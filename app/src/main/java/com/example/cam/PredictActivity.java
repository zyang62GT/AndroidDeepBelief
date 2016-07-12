package com.example.cam;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.jetpac.deepbelief.DeepBelief;
import com.jetpac.deepbelief.DeepBelief.JPCNNLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;


public class PredictActivity extends Activity {

    private static final String TAG = "CamTestActivity";
    Preview preview;
    Button buttonClick;
    TextView labelsView;
    Camera camera;
    String fileName;
    Activity act;
    Context ctx;


    //Pointer varaibles for jpcnn api
    Pointer networkHandle = null;
    Pointer trainerHandle = null;
    Pointer[] predictor = new Pointer[10];

    //Limit values for predictions
    int kPosPreT = 50;
    int kNegPreT = 50;
    int kElePerPre = 4096;

    float kMinSecBetPings = .05f;

    int posPreC;
    int negPreC;

    float[] preVal = new float[10];
    String labelsText = "";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ctx = this;
        act = this;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_learn);

        preview = new Preview(this, (SurfaceView)findViewById(R.id.surfaceView));
        preview.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
        ((FrameLayout) findViewById(R.id.preview)).addView(preview);
        preview.setKeepScreenOn(true);

        labelsView = (TextView) findViewById(R.id.labelsView);
        labelsView.setText("");

        //initDeepBelief();
    }

    @Override
    protected void onResume() {
        super.onResume();
        //      preview.camera = Camera.open();
        camera = Camera.open();
        camera.startPreview();
        preview.setCamera(camera);
        camera.setPreviewCallback(previewCallback);
    }

    @Override
    protected void onPause() {
        if(camera != null) {
            camera.stopPreview();
            preview.setCamera(null);
            camera.release();
            camera = null;
        }
        super.onPause();
    }

    private void resetCam() {
        camera.startPreview();
        preview.setCamera(camera);
    }

    PreviewCallback previewCallback = new PreviewCallback() {
        public void onPreviewFrame(byte[] data, Camera camera) {
            Size previewSize = camera.getParameters().getPreviewSize();
            YuvImage yuvimage=new YuvImage(data, ImageFormat.NV21, previewSize.width, previewSize.height, null);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            yuvimage.compressToJpeg(new Rect(0, 0, previewSize.width, previewSize.height), 80, baos);
            byte[] jdata = baos.toByteArray();
            Bitmap bmp = BitmapFactory.decodeByteArray(jdata, 0, jdata.length);
            classifyBitmap(bmp);
        }
    };

    void initDeepBelief() {
        AssetManager am = ctx.getAssets();
        String baseFileName = "jetpac.ntwk";
        String dataDir = ctx.getFilesDir().getAbsolutePath();
        String networkFile = dataDir + "/" + baseFileName;
        copyAsset(am, baseFileName, networkFile);
        networkHandle = JPCNNLibrary.INSTANCE.jpcnn_create_network(networkFile);

        //Bitmap lenaBitmap = getBitmapFromAsset("lena.png");
        //classifyBitmap(lenaBitmap);
    }

    private class PredictionLabel implements Comparable<PredictionLabel> {
        public String name;
        public float predictionValue;
        public PredictionLabel(String inName, float inPredictionValue) {
            this.name = inName;
            this.predictionValue = inPredictionValue;
        }
        public int compareTo(PredictionLabel anotherInstance) {
            final float diff = (this.predictionValue - anotherInstance.predictionValue);
            if (diff < 0.0f) {
                return 1;
            } else if (diff > 0.0f) {
                return -1;
            } else {
                return 0;
            }
        }
    };

    void classifyBitmap(Bitmap bitmap) {
        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();
        final int pixelCount = (width * height);
        final int bytesPerPixel = 4;
        final int byteCount = (pixelCount * bytesPerPixel);
        ByteBuffer buffer = ByteBuffer.allocate(byteCount);
        bitmap.copyPixelsToBuffer(buffer);
        byte[] pixels = buffer.array();
        Pointer imageHandle = JPCNNLibrary.INSTANCE.jpcnn_create_image_buffer_from_uint8_data(pixels, width, height, 4, (4 * width), 0, 0);

        PointerByReference predictionsValuesRef = new PointerByReference();
        IntByReference predictionsLengthRef = new IntByReference();
        PointerByReference predictionsNamesRef = new PointerByReference();
        IntByReference predictionsNamesLengthRef = new IntByReference();
        long startT = System.currentTimeMillis();
        JPCNNLibrary.INSTANCE.jpcnn_classify_image(
                networkHandle,
                imageHandle,
                2, //SAMPLE FLAGS: 0 = DEFAULT(CENTERED), 1 = MULTISAMPLE, 2 = RANDOM_SAMPLE
                -2, //LAYEROFFSET
                predictionsValuesRef,
                predictionsLengthRef,
                predictionsNamesRef,
                predictionsNamesLengthRef);
        long stopT = System.currentTimeMillis();
        float duration = (float)(stopT-startT) / 1000.0f;
        System.err.println("jpcnn_classify_image() took " + duration + " seconds.");

        JPCNNLibrary.INSTANCE.jpcnn_destroy_image_buffer(imageHandle);

        Pointer predictionsValuesPointer = predictionsValuesRef.getValue();
        final int predictionsLength = predictionsLengthRef.getValue();
        //Pointer predictionsNamesPointer = predictionsNamesRef.getValue();
        //final int predictionsNamesLength = predictionsNamesLengthRef.getValue();

        System.err.println(String.format("predictionsLength = %d", predictionsLength));

        float[] predictionsValues = predictionsValuesPointer.getFloatArray(0, predictionsLength);
        predictionHandler(predictionsValuesPointer, predictionsLength);
        printLabels();


    }


    private static boolean copyAsset(AssetManager assetManager,
                                     String fromAssetPath, String toPath) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = assetManager.open(fromAssetPath);
            new File(toPath).createNewFile();
            out = new FileOutputStream(toPath);
            copyFile(in, out);
            in.close();
            in = null;
            out.flush();
            out.close();
            out = null;
            return true;
        } catch(Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void process(){

    }

    public void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);

        // Transfer bytes from in to out
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }

    private static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1){
            out.write(buffer, 0, read);
        }
    }



    public void listFiles(){
        String path = Environment.getExternalStorageDirectory().toString()+"/Documents/newFile";
        Log.d("Files", "Path: " + path);
        File f = new File(path);
        File file[] = f.listFiles();
        Log.d("Files", "Size: "+ file.length);
        for (int i=0; i < file.length; i++)
        {
            if(i>=10) break;
            Log.d("Files", "FileName:" + file[i].getName());
            predictor[i] = JPCNNLibrary.INSTANCE.jpcnn_load_predictor(path + '/' + file[i].getName().toString());
            //preVal[i] = JPCNNLibrary.INSTANCE.jpcnn_predict(predictor[i],predictions,predictionsLength);
        }



    }

    public void predictionHandler (Pointer predictions,int predictionsLength){
        for (int i=0; i < 10; i++)
        {
            if(predictor[i]==null) break;
            preVal[i] = JPCNNLibrary.INSTANCE.jpcnn_predict(predictor[i], predictions, predictionsLength);
        }
    }

    public void printLabels(){
        String path = Environment.getExternalStorageDirectory().toString()+"/Documents/newFile";
        Log.d("Files", "Path: " + path);
        File f = new File(path);
        File file[] = f.listFiles();
        Log.d("Files", "Size: "+ file.length);
        PredictionLabel label;
        for (int i=0; i < file.length; i++)
        {
            if(i>=10) break;
            label = new PredictionLabel(file[i].getName().toString(),preVal[i]);
            labelsText += String.format("%s - %.2f\n", label.name, label.predictionValue);
        }
        labelsView.setText(labelsText);
    }





}
