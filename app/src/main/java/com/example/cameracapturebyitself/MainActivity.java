package com.example.cameracapturebyitself;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    File filepath;

    TextureView imageView;
    Button takePic;

    CameraDevice cameraDevice;
    String cameraId;
    Size imageDimensions;
    CaptureRequest.Builder captureRequestBuilder;
    CameraCaptureSession cameraSession;

    Handler backgroundHandler;
    HandlerThread handlerThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        takePic = (Button)findViewById(R.id.takePicture);
        imageView = (TextureView) findViewById(R.id.imageView);

        imageView.setSurfaceTextureListener(surfaceTextureListener);
        long start = System.nanoTime();

        final CameraManager cameraManager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);

        takePic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {

                    //takePicture();
                    //CameraHelper cameraHelper = new CameraHelper(cameraManager,imageView, cameraDevice, cameraId, captureRequestBuilder, cameraSession);
                    //cameraHelper.takePicture(MainActivity.this, cameraManager, getWindowManager());

                    takePicture();
                } catch (Exception e){
                    Toast.makeText(MainActivity.this, e+"", Toast.LENGTH_LONG).show();
                }

            }
        });
        long stop = System.nanoTime();
        Toast.makeText(MainActivity.this, stop-start+"", Toast.LENGTH_LONG);
    }

    TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            try {
                openCamera();
            } catch (Exception e ){
                Toast.makeText(MainActivity.this, e+"", Toast.LENGTH_LONG).show();
            }
        }



        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    CameraDevice.StateCallback stateCallBack = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            cameraDevice = camera;

            try {
                startCameraPreview();
            } catch (Exception e){
                Toast.makeText(MainActivity.this, e+"", Toast.LENGTH_LONG).show();
            }
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    public void takePicture() throws CameraAccessException{
        if (cameraDevice == null){
            return;
        }

        CameraManager manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);

        CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
        Size[] jpegSize = null;

        jpegSize = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);

        int width = 100;
        int height =100;

        if (jpegSize!=null && jpegSize.length>0){
            width = jpegSize[0].getWidth();
            height = jpegSize[0].getHeight();
        }

        ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
        List<Surface> outputSurface = new ArrayList<>(2);
        outputSurface.add(reader.getSurface());

        outputSurface.add(new Surface(imageView.getSurfaceTexture()));

        final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        captureBuilder.addTarget(reader.getSurface());
        captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

        //int rotation = getWindowManager().getDefaultDisplay().getRotation();
        captureBuilder.set(CaptureRequest.CONTROL_MODE, 50);

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "CameraDemo");
       /* if (!mediaStorageDir.exists()){
            if (!mediaStorageDir.mkdirs()){
                Toast.makeText(MainActivity.this, "This directory does not exist", Toast.LENGTH_LONG).show();
                return;
            }
        }
*/

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        //filepath = new File(mediaStorageDir.getPath() + File.separator +
        //        "IMG_"+ timeStamp + ".jpg");
        filepath = getOutputMediaFile();

        ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = null;

                image = reader.acquireLatestImage();
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.capacity()];
                buffer.get(bytes);
                try {
                    save(bytes);
                } catch (Exception e){
                    Toast.makeText(MainActivity.this, e+"", Toast.LENGTH_LONG).show();
                } finally {
                    if (image != null){
                        image.close();
                    }
                }

            }
        };

        reader.setOnImageAvailableListener(readerListener, backgroundHandler);

        final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                super.onCaptureCompleted(session, request, result);
                Toast.makeText(MainActivity.this, "Saved", Toast.LENGTH_LONG).show();
                Toast.makeText(MainActivity.this, filepath+"", Toast.LENGTH_LONG).show();
                try {
                    startCameraPreview();
                } catch (Exception e){
                    Toast.makeText(MainActivity.this, e+"", Toast.LENGTH_LONG).show();
                }



            }
        };


        cameraDevice.createCaptureSession(outputSurface, new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                try {
                    session.capture(captureBuilder.build(), captureListener, backgroundHandler);
                } catch (Exception e){
                    Toast.makeText(MainActivity.this, e+"", Toast.LENGTH_LONG).show();
                }

            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {

            }
        },  backgroundHandler);

    }

    public Bitmap save(byte[] bytes){
        OutputStream outputStream = null;
        Bitmap b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

        try (OutputStream out = new FileOutputStream(filepath)){
            b.compress(Bitmap.CompressFormat.JPEG, 100, out);

        } catch (Exception e){
            Toast.makeText(MainActivity.this, e+"", Toast.LENGTH_LONG).show();
        }

        try {
            outputStream = new FileOutputStream(filepath);
            outputStream.write(bytes);
            outputStream.close();
            Toast.makeText(MainActivity.this, filepath+"", Toast.LENGTH_LONG).show();
        } catch (Exception e){
            Toast.makeText(MainActivity.this, e+"", Toast.LENGTH_LONG).show();
        }
        return b;
    }

    public void openCamera() throws CameraAccessException {
        CameraManager cameraManager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);

        cameraId = cameraManager.getCameraIdList()[0];

        CameraCharacteristics cc = cameraManager.getCameraCharacteristics(cameraId);
        StreamConfigurationMap map = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        imageDimensions = map.getOutputSizes(SurfaceTexture.class)[0];

        try {
            int permissionCheck = this.checkSelfPermission("android.permission.CAMERA");
            //checkPermission("android.permission.CAMERA", null, null);
            cameraManager.openCamera(cameraId, stateCallBack, null);
        } catch (Exception e){
            Toast.makeText(MainActivity.this, e+"", Toast.LENGTH_LONG).show();
            System.out.println(e);
        }
    }


    private void startCameraPreview() throws  CameraAccessException{
        SurfaceTexture texture = imageView.getSurfaceTexture();
        texture.setDefaultBufferSize(imageDimensions.getWidth(), imageDimensions.getHeight());

        Surface surface = new Surface(texture);

        captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

        captureRequestBuilder.addTarget(surface);

        cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(CameraCaptureSession session) {
                if(cameraDevice == null){
                    return;
                }

                cameraSession = session;
                try {
                    updatePreview();
                } catch (Exception e){
                    Toast.makeText(MainActivity.this, e+"", Toast.LENGTH_LONG).show();
                }


            }

            @Override
            public void onConfigureFailed(CameraCaptureSession session) {

            }
        }, null);

    }

    private void updatePreview() throws CameraAccessException{
        if(cameraDevice == null){
            return;
        }

        captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_AUTO);
        cameraSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();

        if(imageView.isAvailable()){
            try {
                openCamera();
            } catch (CameraAccessException e) {
                e.printStackTrace();
                Toast.makeText(MainActivity.this, e+"", Toast.LENGTH_LONG).show();
            }
        } else {
            imageView.setSurfaceTextureListener(surfaceTextureListener);
        }

    }

    private void startBackgroundThread(){
        handlerThread = new HandlerThread("Camera background");
        handlerThread.start();

        backgroundHandler = new Handler((handlerThread.getLooper()));
    }

    private void stopBackgroundThread() throws InterruptedException{
        handlerThread.quitSafely();
        handlerThread.join();

        backgroundHandler = null;
        handlerThread = null;

    }


    @Override
    protected void onPause(){
        try {
            stopBackgroundThread();
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, e+"", Toast.LENGTH_LONG).show();
            System.out.println(e);
        }
        super.onPause();
    }

    private static File getOutputMediaFile(){
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "CameraCaptureApp");
        if (!mediaStorageDir.exists()){
            if (!mediaStorageDir.mkdirs()){
                return null;
            }
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        return new File(mediaStorageDir.getPath() + File.separator +
                "IMG_"+ timeStamp + ".jpg");
    }
}
