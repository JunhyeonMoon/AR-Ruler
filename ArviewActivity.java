package com.example.paranocs.ar_ruler;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.media.Image;
import android.os.Build;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.DpToMetersViewSizer;
import com.google.ar.sceneform.rendering.FixedWidthViewSizer;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.PlaneRenderer;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.rendering.ViewSizer;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.w3c.dom.Text;

import java.nio.ByteBuffer;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;

import static org.opencv.core.CvType.CV_8UC1;
import static org.opencv.imgproc.Imgproc.cvtColor;

public class ArviewActivity extends AppCompatActivity {
    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("opencv_java3");
        System.loadLibrary("native-lib");
    }

    private static String TAG = MainActivity.class.getName();
    private static final double MIN_OPENGL_VERSION = 3.0;

    private ArFragment arFragment;
    private Image image;
    private Vector<AnchorNode> twoNode = new Vector<AnchorNode>(2);
    private Vector<AnchorNode> touchNode = new Vector<AnchorNode>(1000);
    private Vector<AnchorNode> drgNode = new Vector<AnchorNode>(2);
    private boolean isTwo = false;
    private Button opencvBtn;
    private Button deleteBtn;
    private Button drgBtn;
    private ViewRenderable viewRenderable;
    private ModelRenderable sphereRenderable;
    private boolean tflag = false;

    private byte[] imageData = null;
    private Mat matInput;


    //for touch event
    private long downTime = SystemClock.uptimeMillis();
    private long eventTime = SystemClock.uptimeMillis() + 100;
    private float x = 0.0f;
    private float y = 0.0f;
    private int metaState = 0;
    private MotionEvent motionEvent;
    private boolean isFirstTouch = true;
    private boolean isDrg = false;

    public native float[] ConvertRGBtoGray(long matAddrInput);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!checkIsSupportedDeviceOrFinish(this)) {
            return;
        }
        //Initialize step
        setContentView(R.layout.activity_arview);
        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);

        opencvBtn = findViewById(R.id.openCVbtn);
        deleteBtn = findViewById(R.id.deletebtn);
        drgBtn = findViewById(R.id.dragbtn);

        ArSceneView arSceneView = arFragment.getArSceneView();

        deleteBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteAllLine();
                Toast.makeText(ArviewActivity.this, "모든 선을 지웠습니다", Toast.LENGTH_SHORT).show();
            }
        });

        opencvBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (image != null)
                    image.close(); //release before image

                deleteAllLine();

                try {//get camera image
                    image = arSceneView.getArFrame().acquireCameraImage();
                } catch (NotYetAvailableException e) {
                    e.printStackTrace();
                }

                /*********** Image to RGB *************/
                byte[] nv21;

                ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
                ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
                ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

                int ySize = yBuffer.remaining();
                int uSize = uBuffer.remaining();
                int vSize = vBuffer.remaining();

                nv21 = new byte[ySize + uSize + vSize];

                //U and V are swapped
                yBuffer.get(nv21, 0, ySize);
                vBuffer.get(nv21, ySize, vSize);
                uBuffer.get(nv21, ySize + vSize, uSize);

                Mat mRGB = getYUV2Mat(nv21);
                /*************************************/

                Log.d(TAG, "matsize : " + mRGB.size().toString());
                Log.d(TAG, "width : " + Integer.toString(image.getWidth()));
                Log.d(TAG, "height : " + Integer.toString(image.getHeight()));

                // Use OPENCV to detect object
                float rec[] = new float[8];
                rec = ConvertRGBtoGray(mRGB.getNativeObjAddr());

                Log.d(TAG, "rec length : " + Integer.toString(rec.length));
                for (int i = 0; i < rec.length; i++) {
                    Log.d(TAG, "rec coordinate : " + Float.toString(rec[i]));
                }

                if (rec[0] == -1.f) { // no detected object
                    Toast.makeText(ArviewActivity.this, "물체 인식에 실패했습니다", Toast.LENGTH_SHORT).show();

                } else { // detect successful
                    Toast.makeText(ArviewActivity.this, "물체를 인식했습니다", Toast.LENGTH_SHORT).show();
                    Vector<vec2> pos = new Vector<vec2>(4);
                    pos.add(new vec2(rec[0], rec[1]));
                    pos.add(new vec2(rec[2], rec[3]));
                    pos.add(new vec2(rec[4], rec[5]));
                    pos.add(new vec2(rec[6], rec[7]));

                    if (isFirstTouch) {
                        x = (float) arSceneView.getWidth() / 2;
                        y = (float) arSceneView.getHeight() / 2;
                        motionEvent = MotionEvent.obtain(
                                downTime,
                                eventTime,
                                MotionEvent.ACTION_DOWN, //Touch event catch only UP action
                                x,
                                y, metaState
                        );
                        arFragment.getArSceneView().dispatchTouchEvent(motionEvent);

                        isFirstTouch = false;
                    }


                    for (int i = 0; i < 4; i++) {
                        for (int j = 0; j < 2; j++) {
                            if (i + j < 4) {
                                y = pos.get(j + i).x; // 세로로 찍으면 xy 바뀜 (640X480 -> 480X640)
                                // arcore는 1080 * 1920 이므로 비율을 곱한다
                                y *= (float) arSceneView.getHeight() / (float) image.getWidth();
                                x = pos.get(j + i).y;
                                x *= (float) arSceneView.getWidth() / (float) image.getHeight();
                                x = (float) arSceneView.getWidth() - x;

                                //Log.d(TAG, "x, y : " + Float.toString(x) + " " + Float.toString(y));
                                motionEvent = MotionEvent.obtain(
                                        downTime,
                                        eventTime,
                                        MotionEvent.ACTION_UP, //Touch event catch only UP action
                                        x,
                                        y, metaState
                                );
                                arFragment.getArSceneView().dispatchTouchEvent(motionEvent);
                            } else {
                                y = pos.get(0).x;
                                y *= (float) arSceneView.getHeight() / (float) image.getWidth();
                                x = pos.get(0).y;
                                x *= (float) arSceneView.getWidth() / (float) image.getHeight();
                                x = (float) arSceneView.getWidth() - x;
                                //Log.d(TAG, "x, y : " + Float.toString(x) + " " + Float.toString(y));
                                motionEvent = MotionEvent.obtain(
                                        downTime,
                                        eventTime,
                                        MotionEvent.ACTION_UP,  //누른 걸 땠을 때
                                        x,
                                        y, metaState
                                );
                                arFragment.getArSceneView().dispatchTouchEvent(motionEvent);
                            }

                        }
                    }
                }
            }
        });

        drgBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!isDrg){//start drag
                    if (isFirstTouch) {
                        float x = (float) arSceneView.getWidth() / 2;
                        float y = (float) arSceneView.getHeight() / 2;
                        motionEvent = MotionEvent.obtain(
                                downTime,
                                eventTime,
                                MotionEvent.ACTION_UP, //action_down - 처음 눌렀을 때
                                x,
                                y, metaState
                        );
                        arFragment.getArSceneView().dispatchTouchEvent(motionEvent);
                        isFirstTouch = false;
                    }

                    isDrg = true;
                    tflag = true;
                    float x = (float) arSceneView.getWidth() / 2;
                    float y = (float) arSceneView.getHeight() / 2;
                    motionEvent = MotionEvent.obtain(
                            downTime,
                            eventTime,
                            MotionEvent.ACTION_UP, //Touch event catch only UP action
                            x,
                            y, metaState
                    );
                    arFragment.getArSceneView().dispatchTouchEvent(motionEvent);
                    tflag = false;
                    drgNode.clear();
                    isDrg = false;
                }
            }
        });

        arFragment.setOnTapArPlaneListener((HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
            //Log.d(TAG, "Touch event occur!!");
            //Log.d(TAG, "motion Event : " + Integer.toString(motionEvent.getAction()));
            Log.d(TAG, "motion Event (x, y): " + Float.toString(motionEvent.getX()) + " " + Float.toString(motionEvent.getY()));
            //Log.d(TAG, "arview size = " + Integer.toString(arFragment.getArSceneView().getWidth()) + " " + Integer.toString(arFragment.getArSceneView().getHeight()));

            Anchor tmp = hitResult.createAnchor();
            AnchorNode tmpNode = new AnchorNode(tmp);
            twoNode.addElement(tmpNode);
            touchNode.addElement(tmpNode);

            if(isDrg){
                if(drgNode.size() == 0) {
                    drgNode.add(tmpNode);
                }else{
                    drgNode.add(tmpNode);
                    addLineBetweenDrg(hitResult, plane, motionEvent);
                    addvertex(hitResult, plane, motionEvent);
                    drgNode.remove(1);
                }
            }else{
                if (!isTwo) {//no node yet
                    isTwo = true;
                } else {
                    //render line
                    float length = addLineBetweenHits(hitResult, plane, motionEvent);
                    addvertex(hitResult, plane, motionEvent);
                    twoNode.clear();
                    isTwo = false;
                }
            }
        });

    }

    // 안드로이드 버전을 확인한다.
    public static boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.e(TAG, "Sceneform requires Android N or later");
            Toast.makeText(activity, "Sceneform requires Android N or later", Toast.LENGTH_LONG).show();
            activity.finish();
            return false;
        }
        String openGlVersionString =
                ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
                        .getDeviceConfigurationInfo()
                        .getGlEsVersion();
        if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
            Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later");
            Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                    .show();
            activity.finish();
            return false;
        }
        return true;
    }
    //touch로 만든 node에 VERTEX 표시
    private void addvertex(HitResult hitResult, Plane plane, MotionEvent motionEvent){
        AnchorNode node1 = twoNode.get(0);
        AnchorNode node2 = twoNode.get(1);

        node1.setParent(arFragment.getArSceneView().getScene());
        Vector3 point1, point2;
        point1 = node1.getWorldPosition();
        point2 = node2.getWorldPosition();

        MaterialFactory.makeOpaqueWithColor(this, new Color(android.graphics.Color.BLUE))
                .thenAccept(
                        material -> {
                            sphereRenderable =ShapeFactory.makeSphere(0.008f, new Vector3(0.001f, 0.001f, 0.001f), material);
                            sphereRenderable.setShadowCaster(false);
                            TransformableNode node3 = new TransformableNode(arFragment.getTransformationSystem());
                            node3.setParent(node1);
                            node3.setRenderable(sphereRenderable);
                            node3.setWorldPosition(point1);
                            TransformableNode node4 = new TransformableNode(arFragment.getTransformationSystem());
                            node4.setParent(node2);
                            node4.setRenderable(sphereRenderable);
                            node4.setWorldPosition(point2);
                        }
                        );
    }
    private float addLineBetweenHits(HitResult hitResult, Plane plane, MotionEvent motionEvent) {
        AnchorNode node1 = twoNode.get(0);
        AnchorNode node2 = twoNode.get(1);

        node1.setParent(arFragment.getArSceneView().getScene());
        node2.setParent(arFragment.getArSceneView().getScene());
        Vector3 point1, point2;
        point1 = node1.getWorldPosition();
        point2 = node2.getWorldPosition();

        final Vector3 difference = Vector3.subtract(point1, point2);
        final Vector3 directionFromTopToBottom = difference.normalized();
        final Quaternion rotationFromAToB = Quaternion.lookRotation(directionFromTopToBottom, Vector3.up());
        Color color = new Color();
        color.set(1, 0, 0);
        MaterialFactory.makeOpaqueWithColor(getApplicationContext(), color)
                .thenAccept(
                        material -> {
                            ModelRenderable model = ShapeFactory.makeCube(
                                    new Vector3(.001f, .001f, difference.length()),
                                    Vector3.zero(), material);
                            model.setShadowCaster(false);
                            Node node = new Node();
                            node.setParent(node1);
                            node.setRenderable(model);
                            node.setWorldPosition(Vector3.add(point1, point2).scaled(.5f));
                            node.setWorldRotation(rotationFromAToB);
                        }
                );

        float dis = difference.length();
        dis = dis * 100.f;
        int dis_i = (int)dis;
        String dis_s = Integer.toString(dis_i);

        ViewRenderable.builder()
                .setSizer(new DpToMetersViewSizer((int)(-400/point1.y)))
                .setView(this, R.layout.view_length)
                .setVerticalAlignment(ViewRenderable.VerticalAlignment.BOTTOM)
                .setHorizontalAlignment(ViewRenderable.HorizontalAlignment.LEFT)
                .build()
                .thenAccept(renderable -> {
                    viewRenderable = renderable;
                    TextView textView = (TextView)viewRenderable.getView();
                    textView.setText(dis_s+"cm");
                    viewRenderable.setShadowCaster(false);
                    //viewRenderable.setSizer(
                    Node node = new Node();
                    node.setParent(node1);
                    node.setRenderable(viewRenderable);
                    node.setWorldPosition(Vector3.add(point1, point2).scaled(.5f));
                })
                .exceptionally(
                        throwable -> {
                            //
                            Log.d(TAG, "addLineBetweenHits: failed to build viewrenderable");
                            return null;
                        }
                );
        return difference.length();
    }

    private float addLineBetweenDrg(HitResult hitResult, Plane plane, MotionEvent motionEvent){
        AnchorNode node1 = drgNode.get(0);
        AnchorNode node2 = drgNode.get(1);

        node1.setParent(arFragment.getArSceneView().getScene());
        Vector3 point1, point2;
        point1 = node1.getWorldPosition();
        point2 = node2.getWorldPosition();

        final Vector3 difference = Vector3.subtract(point1, point2);
        final Vector3 directionFromTopToBottom = difference.normalized();
        final Quaternion rotationFromAToB = Quaternion.lookRotation(directionFromTopToBottom, Vector3.up());
        Color color = new Color();
        color.set(1, 0, 0);
        MaterialFactory.makeOpaqueWithColor(getApplicationContext(), color)
                .thenAccept(
                        material -> {
                            ModelRenderable model = ShapeFactory.makeCube(
                                    new Vector3(.001f, .001f, difference.length()),
                                    Vector3.zero(), material);
                            model.setShadowCaster(false);

                            Node node = new Node();
                            node.setParent(node1);
                            node.setRenderable(model);
                            node.setWorldPosition(Vector3.add(point1, point2).scaled(.5f));
                            node.setWorldRotation(rotationFromAToB);
                        }
                );

        float dis = difference.length();
        dis = dis * 100.f;
        int dis_i = (int)dis;
        String dis_s = Integer.toString(dis_i);

        ViewRenderable.builder()
                .setSizer(new DpToMetersViewSizer((int)(-400/point1.y)))
                .setView(this, R.layout.view_length)
                .setVerticalAlignment(ViewRenderable.VerticalAlignment.BOTTOM)
                .setHorizontalAlignment(ViewRenderable.HorizontalAlignment.LEFT)
                .build()
                .thenAccept(renderable -> {
                    viewRenderable = renderable;
                    TextView textView = (TextView)viewRenderable.getView();
                    textView.setText(dis_s+"cm");
                    viewRenderable.setShadowCaster(false);
                    //viewRenderable.setSizer(
                    Node node = new Node();
                    node.setParent(node1);
                    node.setRenderable(viewRenderable);
                    node.setWorldPosition(Vector3.add(point1, point2).scaled(.5f));
                })
                .exceptionally(
                        throwable -> {
                            //
                            Log.d(TAG, "addLineBetweenDrg: failed to build viewrenderable");
                            return null;
                        }
                );
        return difference.length();
    }

    public Mat getYUV2Mat(byte[] data) {
        Mat mYuv = new Mat(image.getHeight() + image.getHeight() / 2, image.getWidth(), CV_8UC1);
        mYuv.put(0, 0, data);
        Mat mRGB = new Mat();
        cvtColor(mYuv, mRGB, Imgproc.COLOR_YUV2RGB_NV21, 3);
        return mRGB;
    }

    //touchnode 기준 detach 후 clear
    private void deleteAllLine() {
        if (touchNode.size() > 0) {
            for (int i = 0; i < touchNode.size(); i++) {
                touchNode.get(i).getAnchor().detach();
            }
            touchNode.clear();
        } else {
            //Toast.makeText(this, "지울 선이 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }


    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */


}
