/*
 * Copyright 2018 Google LLC. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.ar.sceneform.samples.hellosceneform;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.RemoteInput;
import android.content.Context;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.collision.Ray;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.rendering.ViewSizer;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.DragGesture;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.ar.sceneform.ux.TransformationSystem;

import org.w3c.dom.Text;

import java.util.Vector;

/**
 * This is an example activity that uses the Sceneform UX package to make common AR tasks easier.
 */
public class HelloSceneformActivity extends AppCompatActivity {
    private static final String TAG = HelloSceneformActivity.class.getSimpleName();
    private static final double MIN_OPENGL_VERSION = 3.0;

    private ArFragment arFragment;
    private ModelRenderable sphereRenderable;
    private ModelRenderable cubeRenderable;
    private ViewRenderable viewRenderable;

    private Button hitButton;
    private TextView distanceView;
    private TextView renderText;
    private ArSceneView arview;
    private Scene scene;
    private boolean check;
    private boolean isSphere;
    private Vector<Pose> hitpoint;
    private Vector<AnchorNode> Node = new Vector<AnchorNode>(2);

    @Override
  @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
  // CompletableFuture requires api level 24
  // FutureReturnValueIgnored is not valid
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (!checkIsSupportedDeviceOrFinish(this)) {
      return;
    }

    ///////initialize step
    setContentView(R.layout.activity_ux);
    arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
    hitButton =  (Button)findViewById(R.id.hitCenterButton);
    distanceView = (TextView)findViewById(R.id.distanceView);
    renderText = (TextView)findViewById(R.id.rendertext);
    arview = arFragment.getArSceneView();
    scene = arview.getScene();
    check = false;
    isSphere = false;
    hitpoint = new Vector<Pose>(2);

    // When you build a Renderable, Sceneform loads its resources in the background while returning
    // a CompletableFuture. Call thenAccept(), handle(), or check isDone() before calling get().

        ViewRenderable.builder().setView(
                this, R.layout.rendertext)
                .build()
                .thenAccept(renderable -> viewRenderable = renderable);

    arFragment.setOnTapArPlaneListener((HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {

        hitpoint.addElement(hitResult.getHitPose());
        Anchor temp = hitResult.createAnchor();
        AnchorNode tempNode = new AnchorNode(temp);
        Node.addElement(tempNode);

        if(!check) {
            check = true;
        }
        else{
            addvertex(hitResult, plane, motionEvent);
            addcube(hitResult, plane, motionEvent);
            Node.clear();
            hitpoint.clear();
            check = false;
        }

    });

  }

  /**
   * Returns false and displays an error message if Sceneform can not run, true if Sceneform can run
   * on this device.
   *
   * <p>Sceneform requires Android N on the device as well as OpenGL 3.0 capabilities.
   *
   * <p>Finishes the activity if Sceneform can not run
   */
  public static boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
    if (Build.VERSION.SDK_INT < VERSION_CODES.N) {
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
    private void addvertex(HitResult hitResult, Plane plane, MotionEvent motionEvent){
        AnchorNode node1 = Node.get(0);
        AnchorNode node2 = Node.get(1);

        node1.setParent(arFragment.getArSceneView().getScene());
        node2.setParent(arFragment.getArSceneView().getScene());

        Vector3 point1, point2;
        point1 = node1.getWorldPosition();
        point2 = node2.getWorldPosition();

        MaterialFactory.makeOpaqueWithColor(this, new Color(android.graphics.Color.BLUE))
                .thenAccept(
                        material -> {
                            sphereRenderable =ShapeFactory.makeSphere(0.008f, new Vector3(0.001f, 0.001f, 0.001f), material);
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
    private void addcube(HitResult hitResult, Plane plane, MotionEvent motionEvent)
    {
        AnchorNode node1 = Node.get(0);
        AnchorNode node2 = Node.get(1);

        node1.setParent(arFragment.getArSceneView().getScene());
        node2.setParent(arFragment.getArSceneView().getScene());

        MaterialFactory.makeOpaqueWithColor(this,  new Color(android.graphics.Color.GREEN))
                .thenAccept(
                        material -> {
                            ModelRenderable cubeRenderable = ShapeFactory.makeCube(new Vector3(0.01f, 0.01f, 0.01f), new Vector3(0.001f, 0.001f, 0.001f), material);
                            Node centernode = new Node();
                            centernode.setParent(node1);
                            centernode.setRenderable(cubeRenderable);
                            centernode.setWorldPosition(Vector3.add(node1.getWorldPosition(), node2.getWorldPosition()).scaled(.5f));
                        }
                );
    }
}
