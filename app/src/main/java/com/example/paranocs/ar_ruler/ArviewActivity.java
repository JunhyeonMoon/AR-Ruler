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
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;

import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;
import java.util.Vector;

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
    private boolean isTwo = false;
    private TextView distanceText;
    private Button opencvBtn;
    private Button deleteBtn;
    private ViewRenderable lengthViewRenderable;

    private byte[] imageData = null;
    private Mat matInput;


    //for touch event
    private long downTime = SystemClock.uptimeMillis();
    private long eventTime = SystemClock.uptimeMillis() + 100;
    private float x = 0.0f;
    private float y = 0.0f;
    private int metaState = 0;
    private MotionEvent motionEvent;
    private boolean firstTouch = true;
    private boolean renderLength = false;

    public native float[] ConvertRGBtoGray(long matAddrInput);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!checkIsSupportedDeviceOrFinish(this)) {
            return;
        }

        setContentView(R.layout.activity_arview);
        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
        distanceText = findViewById(R.id.distanceText);
        opencvBtn = findViewById(R.id.openCVbtn);
        deleteBtn = findViewById(R.id.deletebtn);

        ArSceneView arSceneView = arFragment.getArSceneView();


        ViewRenderable.builder()
                .setView(this, R.layout.view_length)
                .build()
                .thenAccept(renderable -> lengthViewRenderable = renderable);

        deleteBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteAllLine();
                Toast.makeText(ArviewActivity.this, "모든 선을 지웠습니다", Toast.LENGTH_SHORT).show();
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
            if (!isTwo) {
                isTwo = true;
            } else {
                //render line
                addLineBetweenHits(hitResult, plane, motionEvent);

                //render Length



                twoNode.clear();
                isTwo = false;
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

                    if(firstTouch){
                        x = (float)arSceneView.getWidth()/2;
                        y = (float)arSceneView.getHeight()/2;
                        motionEvent = MotionEvent.obtain(
                                downTime,
                                eventTime,
                                MotionEvent.ACTION_DOWN, //Touch event catch only UP action
                                x,
                                y, metaState
                        );
                        arFragment.getArSceneView().dispatchTouchEvent(motionEvent);

                        firstTouch = false;
                    }


                    for (int i = 0; i < 4; i++) {
                        for(int j = 0; j < 2; j++){
                            if(i+j < 4){
                                y = pos.get(j+i).x; // 세로로 찍으면 xy 바뀜 (640X480 -> 480X640)
                                // arcore는 1080 * 1920 이므로 비율을 곱한다
                                y *= (float)arSceneView.getHeight()/(float)image.getWidth();
                                x = pos.get(j+i).y;
                                x *= (float)arSceneView.getWidth()/(float)image.getHeight();
                                x = (float)arSceneView.getWidth() - x;

                                //Log.d(TAG, "x, y : " + Float.toString(x) + " " + Float.toString(y));
                                motionEvent = MotionEvent.obtain(
                                        downTime,
                                        eventTime,
                                        MotionEvent.ACTION_UP, //Touch event catch only UP action
                                        x,
                                        y, metaState
                                );
                                arFragment.getArSceneView().dispatchTouchEvent(motionEvent);
                            }else{
                                y = pos.get(0).x;
                                y *= (float)arSceneView.getHeight()/(float)image.getWidth();
                                x = pos.get(0).y;
                                x *= (float)arSceneView.getWidth()/(float)image.getHeight();
                                x = (float)arSceneView.getWidth() - x;
                                //Log.d(TAG, "x, y : " + Float.toString(x) + " " + Float.toString(y));
                                motionEvent = MotionEvent.obtain(
                                        downTime,
                                        eventTime,
                                        MotionEvent.ACTION_UP,  //Touch event catch only UP action
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

    private void addLineBetweenHits(HitResult hitResult, Plane plane, MotionEvent motionEvent) {
        AnchorNode node1 = twoNode.get(0);
        AnchorNode node2 = twoNode.get(1);

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

                            Node node = new Node();
                            node.setParent(node1);
                            node.setRenderable(model);
                            node.setWorldPosition(Vector3.add(point1, point2).scaled(.5f));
                            node.setWorldRotation(rotationFromAToB);
                        }
                );


        float dis = difference.length();
        String dis_s = Float.toString(dis);
        distanceText.setText("distance : " + dis_s + "m");
    }

    public Mat getYUV2Mat(byte[] data) {
        Mat mYuv = new Mat(image.getHeight() + image.getHeight() / 2, image.getWidth(), CV_8UC1);
        mYuv.put(0, 0, data);
        Mat mRGB = new Mat();
        cvtColor(mYuv, mRGB, Imgproc.COLOR_YUV2RGB_NV21, 3);
        return mRGB;
    }

    private void deleteAllLine(){
        if(touchNode.size() > 0){
            for(int i = 0; i < touchNode.size(); i++){
                touchNode.get(i).getAnchor().detach();
            }
            touchNode.clear();
        }else{
            //Toast.makeText(this, "지울 선이 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }


    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */




}
