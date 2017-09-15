package net.ossrs.yasea.demo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.github.faucamp.simplertmp.RtmpHandler;
import com.seu.magicfilter.utils.MagicFilterType;
import com.seu.magicfilter.utils.OpenGLUtils;

import net.ossrs.yasea.SrsCameraView2;
import net.ossrs.yasea.SrsEncodeHandler;
import net.ossrs.yasea.SrsPublisher;
import net.ossrs.yasea.SrsRecordHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Random;

public class MainActivity extends AppCompatActivity implements RtmpHandler.RtmpListener,
        SrsRecordHandler.SrsRecordListener, SrsEncodeHandler.SrsEncodeListener {

    private static final String TAG = "Yasea";

    private Button btnPublish;
    private Button btnSwitchCamera;
    private Button btnRecord;
    private Button btnSwitchEncoder;

    private SharedPreferences sp;
    private String rtmpUrl = "rtmp://live-api-a.facebook.com:80/rtmp/1625387180815455?ds=1&s_l=1&a=ATjtf4L0DoRM2uSV";
    private String recPath = Environment.getExternalStorageDirectory().getPath() + "/test.mp4";

    private SrsPublisher mPublisher;

    private Surface mSurface;

    private SurfaceView mSurfaceView;

    private SurfaceTexture mSurfaceTexture;
    private int mOESTextureId = OpenGLUtils.NO_TEXTURE;
    private ImageView mImageView;

    private int mScreenDensity;
    private DisplayMetrics metrics;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        // response screen rotation event
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);

        // restore data.
//        sp = getSharedPreferences("Yasea", MODE_PRIVATE);
//        rtmpUrl = sp.getString("rtmpUrl", rtmpUrl);

        // initialize url.
        final EditText efu = (EditText) findViewById(R.id.url);
        efu.setText(rtmpUrl);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;
        mMediaProjectionManager = (MediaProjectionManager)
                getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        btnPublish = (Button) findViewById(R.id.publish);
        btnSwitchCamera = (Button) findViewById(R.id.swCam);
        btnRecord = (Button) findViewById(R.id.record);
        btnSwitchEncoder = (Button) findViewById(R.id.swEnc);
        mImageView=(ImageView) findViewById(R.id.imageView2);

        mSurfaceView = (SurfaceView) findViewById(R.id.glsurfaceview_camera);

        mPublisher = new SrsPublisher(mSurfaceView);
        mPublisher.setEncodeHandler(new SrsEncodeHandler(this));
        mPublisher.setRtmpHandler(new RtmpHandler(this));
        mPublisher.setRecordHandler(new SrsRecordHandler(this));
        mPublisher.setPreviewResolution(640, 360);
        mPublisher.setOutputResolution(360, 640);
        mPublisher.setVideoHDMode();
//        mPublisher.startCamera();
        mSurface = mSurfaceView.getHolder().getSurface();

        btnPublish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btnPublish.getText().toString().contentEquals("publish")) {
//                    rtmpUrl = efu.getText().toString();
//                    SharedPreferences.Editor editor = sp.edit();
//                    editor.putString("rtmpUrl", rtmpUrl);
//                    editor.apply();

                    mPublisher.startPublish(rtmpUrl);
//                    mPublisher.startCamera();

                    if (mVirtualDisplay == null) {
                        startScreenCapture();
                    } else {
                        stopScreenCapture();
                    }

                    if (btnSwitchEncoder.getText().toString().contentEquals("soft encoder")) {
                        Toast.makeText(getApplicationContext(), "Use hard encoder", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getApplicationContext(), "Use soft encoder", Toast.LENGTH_SHORT).show();
                    }
                    btnPublish.setText("stop");
                    btnSwitchEncoder.setEnabled(false);
                } else if (btnPublish.getText().toString().contentEquals("stop")) {
                    mPublisher.stopPublish();
                    mPublisher.stopRecord();
                    btnPublish.setText("publish");
                    btnRecord.setText("record");
                    btnSwitchEncoder.setEnabled(true);
                }
            }
        });

        btnSwitchCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                mPublisher.switchCameraFace((mPublisher.getCamraId() + 1) % Camera.getNumberOfCameras());
            }
        });

        btnRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btnRecord.getText().toString().contentEquals("record")) {
                    if (mPublisher.startRecord(recPath)) {
                        btnRecord.setText("pause");
                    }
                } else if (btnRecord.getText().toString().contentEquals("pause")) {
                    mPublisher.pauseRecord();
                    btnRecord.setText("resume");
                } else if (btnRecord.getText().toString().contentEquals("resume")) {
                    mPublisher.resumeRecord();
                    btnRecord.setText("pause");
                }
            }
        });

        btnSwitchEncoder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btnSwitchEncoder.getText().toString().contentEquals("soft encoder")) {
                    mPublisher.switchToSoftEncoder();
                    btnSwitchEncoder.setText("hard encoder");
                } else if (btnSwitchEncoder.getText().toString().contentEquals("hard encoder")) {
                    mPublisher.switchToHardEncoder();
                    btnSwitchEncoder.setText("soft encoder");
                }
            }
        });
    }

    private static final int REQUEST_MEDIA_PROJECTION = 1;
    private int mResultCode;
    private Intent mResultData;
    private MediaProjection mMediaProjection;
    private MediaProjectionManager mMediaProjectionManager;
    private VirtualDisplay mVirtualDisplay;
    private ImageReader mImageReader;
    int mWidth, mHeight;

    private void startScreenCapture() {
        if (mSurface == null) {
            return;
        }
        if (mMediaProjection != null) {
            setUpVirtualDisplay();
        } else if (mResultCode != 0 && mResultData != null) {
            setUpMediaProjection();
            setUpVirtualDisplay();
        } else {
            Log.e(TAG, "Requesting confirmation");
            // This initiates a prompt dialog for the user to confirm screen projection.
            startActivityForResult(
                    mMediaProjectionManager.createScreenCaptureIntent(),
                    REQUEST_MEDIA_PROJECTION);
        }
    }

    private void stopScreenCapture() {
        if (mVirtualDisplay == null) {
            return;
        }
        mVirtualDisplay.release();
        mVirtualDisplay = null;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode != Activity.RESULT_OK) {
                Log.e(TAG, "User cancelled");
                Toast.makeText(this, "cancelled", Toast.LENGTH_SHORT).show();
                return;
            }

            Log.e(TAG, "Starting screen capture");
            mResultCode = resultCode;
            mResultData = data;
            setUpMediaProjection();
            setUpVirtualDisplay();
        }
    }

    private void setUpVirtualDisplay() {
        Log.e(TAG, "Setting up a VirtualDisplay: " +
                mSurfaceView.getWidth() + "x" + mSurfaceView.getHeight() +
                " (" + mScreenDensity + ")");

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        mWidth = mSurfaceView.getWidth();
        mHeight = mSurfaceView.getHeight();
        mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 8);

        mVirtualDisplay = mMediaProjection.createVirtualDisplay("ScreenCapture",
                mSurfaceView.getWidth(), mSurfaceView.getHeight(), mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mImageReader.getSurface(), null, null);

        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener(){
            @Override
            public void onImageAvailable(ImageReader imageReader) {
                Image image = null;
                Bitmap bitmap=null;

                ByteArrayOutputStream jpegByteOutStream = new ByteArrayOutputStream();
                try {
                    image = mImageReader.acquireLatestImage();
                    // Grab the image that the reader prepared us with
                    if (image != null) {
//                        bitmap =getScreenshot(image);
                        ByteBuffer bufferDes=getBufferFromImage(image);

                        bufferDes.rewind();
                        mPublisher.onGetRgbaFrame(bufferDes.array(), mWidth, mHeight);
                        //Log.v(TAG, "Sent a single image!");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    //TODO: Clean up stopping so we dont have to see this error
                    //Log.d(TAG,"FUCK! Image error!");
                } finally {
                    if (jpegByteOutStream != null) {
                        try {
                            jpegByteOutStream.close();
                        } catch (IOException ioe) {
                            ioe.printStackTrace();
                        }
                    }
                    if (bitmap != null) {
                        bitmap.recycle();
                    }
                    if (image != null) {
                        image.close();
                    }
                }
            }
        }, null);

    }

    private Bitmap getScreenshot(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * mWidth;

        Bitmap bitmap = Bitmap.createBitmap(mWidth + rowPadding / pixelStride, mHeight, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        image.close();

        return bitmap;
    }

    private ByteBuffer getBufferFromBitmap(Bitmap bitmap){
        int bytes=bitmap.getByteCount();
        ByteBuffer bufferDes = ByteBuffer.allocate(bytes); //Create a new buffer
        bitmap.copyPixelsToBuffer(bufferDes); //Move the byte data to the buffer
        byte[] array = bufferDes.array(); //Get the underlying array containing the data.
        return bufferDes;
    }

    private ByteBuffer getBufferFromImage(Image image){
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        buffer.rewind();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * mWidth;
        int capacity = mWidth * pixelStride * mHeight;
        byte[][] data = new byte[mHeight][mWidth * pixelStride];
        ByteBuffer bufferDes = ByteBuffer.allocate(capacity);
        for (int i = 0; i < mHeight; i++) {
            buffer.position(i * rowStride);
            buffer.get(data[i], 0, mWidth * pixelStride);
        }
        for (int j=0;j<mHeight;j++){
            bufferDes.put(data[mHeight-j-1]);
        }
        return bufferDes;
    }

    private ByteBuffer getBufferFromImage_2(Image image){
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();

        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * mWidth;
        int capacity = mWidth  * mHeight * pixelStride;
        ByteBuffer bufferDes = ByteBuffer.allocate(capacity);
        byte[] data=new byte[pixelStride];
        for (int i = 0; i <mHeight; i++) {
            buffer.position(i * rowStride);
            for (int j = 0; j < mWidth; j++) {
                buffer.position(j * pixelStride);
                buffer.get(data, 0, pixelStride);
                byte a = data[0];
                data[0] = data[3];
                data[3] = a;
                bufferDes.put(data);
            }
        }
        return  bufferDes;
    }

    private IntBuffer convertToIntBuffer(ByteBuffer bufferDes){
        IntBuffer bufferInt =bufferDes.asIntBuffer();
        int[] arr=new int[bufferInt.remaining()];
        bufferInt.get(arr);
        return bufferInt;
    }


    private void setUpMediaProjection() {
        mMediaProjection = mMediaProjectionManager.getMediaProjection(mResultCode, mResultData);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        } else {
//            switch (id) {
//                case R.id.cool_filter:
//                    mPublisher.switchCameraFilter(MagicFilterType.COOL);
//                    break;
//                case R.id.beauty_filter:
//                    mPublisher.switchCameraFilter(MagicFilterType.BEAUTY);
//                    break;
//                case R.id.early_bird_filter:
//                    mPublisher.switchCameraFilter(MagicFilterType.EARLYBIRD);
//                    break;
//                case R.id.evergreen_filter:
//                    mPublisher.switchCameraFilter(MagicFilterType.EVERGREEN);
//                    break;
//                case R.id.n1977_filter:
//                    mPublisher.switchCameraFilter(MagicFilterType.N1977);
//                    break;
//                case R.id.nostalgia_filter:
//                    mPublisher.switchCameraFilter(MagicFilterType.NOSTALGIA);
//                    break;
//                case R.id.romance_filter:
//                    mPublisher.switchCameraFilter(MagicFilterType.ROMANCE);
//                    break;
//                case R.id.sunrise_filter:
//                    mPublisher.switchCameraFilter(MagicFilterType.SUNRISE);
//                    break;
//                case R.id.sunset_filter:
//                    mPublisher.switchCameraFilter(MagicFilterType.SUNSET);
//                    break;
//                case R.id.tender_filter:
//                    mPublisher.switchCameraFilter(MagicFilterType.TENDER);
//                    break;
//                case R.id.toast_filter:
//                    mPublisher.switchCameraFilter(MagicFilterType.TOASTER2);
//                    break;
//                case R.id.valencia_filter:
//                    mPublisher.switchCameraFilter(MagicFilterType.VALENCIA);
//                    break;
//                case R.id.walden_filter:
//                    mPublisher.switchCameraFilter(MagicFilterType.WALDEN);
//                    break;
//                case R.id.warm_filter:
//                    mPublisher.switchCameraFilter(MagicFilterType.WARM);
//                    break;
//                case R.id.original_filter:
//                default:
//                    mPublisher.switchCameraFilter(MagicFilterType.NONE);
//                    break;
//            }
        }
        setTitle(item.getTitle());

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        final Button btn = (Button) findViewById(R.id.publish);
        btn.setEnabled(true);
        mPublisher.resumeRecord();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPublisher.pauseRecord();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPublisher.stopPublish();
        mPublisher.stopRecord();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mPublisher.stopEncode();
        mPublisher.stopRecord();
        btnRecord.setText("record");
        mPublisher.setScreenOrientation(newConfig.orientation);
        if (btnPublish.getText().toString().contentEquals("stop")) {
            mPublisher.startEncode();
        }
//        mPublisher.startCamera();
    }


    private void handleException(Exception e) {
        try {
            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            mPublisher.stopPublish();
            mPublisher.stopRecord();
            btnPublish.setText("publish");
            btnRecord.setText("record");
            btnSwitchEncoder.setEnabled(true);
        } catch (Exception e1) {
            //
        }
    }

    // Implementation of SrsRtmpListener.

    @Override
    public void onRtmpConnecting(String msg) {
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRtmpConnected(String msg) {
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRtmpVideoStreaming() {
    }

    @Override
    public void onRtmpAudioStreaming() {
    }

    @Override
    public void onRtmpStopped() {
        Toast.makeText(getApplicationContext(), "Stopped", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRtmpDisconnected() {
        Toast.makeText(getApplicationContext(), "Disconnected", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRtmpVideoFpsChanged(double fps) {
        Log.i(TAG, String.format("Output Fps: %f", fps));
    }

    @Override
    public void onRtmpVideoBitrateChanged(double bitrate) {
        int rate = (int) bitrate;
        if (rate / 1000 > 0) {
            Log.i(TAG, String.format("Video bitrate: %f kbps", bitrate / 1000));
        } else {
            Log.i(TAG, String.format("Video bitrate: %d bps", rate));
        }
    }

    @Override
    public void onRtmpAudioBitrateChanged(double bitrate) {
        int rate = (int) bitrate;
        if (rate / 1000 > 0) {
            Log.i(TAG, String.format("Audio bitrate: %f kbps", bitrate / 1000));
        } else {
            Log.i(TAG, String.format("Audio bitrate: %d bps", rate));
        }
    }

    @Override
    public void onRtmpSocketException(SocketException e) {
        handleException(e);
    }

    @Override
    public void onRtmpIOException(IOException e) {
        handleException(e);
    }

    @Override
    public void onRtmpIllegalArgumentException(IllegalArgumentException e) {
        handleException(e);
    }

    @Override
    public void onRtmpIllegalStateException(IllegalStateException e) {
        handleException(e);
    }

    // Implementation of SrsRecordHandler.

    @Override
    public void onRecordPause() {
        Toast.makeText(getApplicationContext(), "Record paused", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRecordResume() {
        Toast.makeText(getApplicationContext(), "Record resumed", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRecordStarted(String msg) {
        Toast.makeText(getApplicationContext(), "Recording file: " + msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRecordFinished(String msg) {
        Toast.makeText(getApplicationContext(), "MP4 file saved: " + msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRecordIOException(IOException e) {
        handleException(e);
    }

    @Override
    public void onRecordIllegalArgumentException(IllegalArgumentException e) {
        handleException(e);
    }

    // Implementation of SrsEncodeHandler.

    @Override
    public void onNetworkWeak() {
        Toast.makeText(getApplicationContext(), "Network weak", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onNetworkResume() {
        Toast.makeText(getApplicationContext(), "Network resume", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onEncodeIllegalArgumentException(IllegalArgumentException e) {
        handleException(e);
    }


}
