package com.archrinto.robocovid;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.freedesktop.gstreamer.GStreamer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Skeleton of an Android Things activity.
 * <p>
 * Android Things peripheral APIs are accessible through the PeripheralManager
 * For example, the snippet below will open a GPIO pin and set it to HIGH:
 * <p>
 * PeripheralManager manager = PeripheralManager.getInstance();
 * try {
 * Gpio gpio = manager.openGpio("BCM6");
 * gpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
 * gpio.setValue(true);
 * } catch (IOException e) {
 * Log.e(TAG, "Unable to access GPIO");
 * }
 * <p>
 * You can find additional examples on GitHub: https://github.com/androidthings
 */
public class RemoteActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private final static String TAG = MainActivity.class.getSimpleName();

    private Button mBtnUp;
    private Button mBtnDown;
    private Button mBtnLeft;
    private Button mBtnRight;
    private Button mBtnTurnRight;
    private Button mBtnTurnLeft;
    private Button mBtnVideoCall;
    private Button mBtnBell;
    private Button mBtnHorn;
    private SurfaceView mSurfaceVideo;
    private PointMsg pointMsg;
    private float linearSpeed = 130;
    private float angularSpeed = 150;
    private int servoSpeed = 2;
    private String robotIP;

    private final static String SP_CAM_WIDTH = "cam_width";
    private final static String SP_CAM_HEIGHT = "cam_height";
    private final static String SP_DEST_IP = "dest_ip";
    private final static String SP_DEST_PORT = "dest_port";

    private final static int DEFAULT_FRAME_RATE = 15;
    private final static int DEFAULT_BIT_RATE = 500000;
    private int CAMERA_ID = 1;

    private Camera camera;
    private SurfaceHolder previewHolder;
    private byte[] previewBuffer;
    private boolean isStreaming = false;
    private AvcEncoder encoder;
    private DatagramSocket udpSocket;
    private InetAddress address;

    private int video_port = 6000;
    private int audio_port = 6001;
    private ArrayList<byte[]> encDataList = new ArrayList<byte[]>();
    private ArrayList<Integer> encDataLengthList = new ArrayList<Integer>();
    public byte[] audio_buffer;

    private AudioRecord recorder;
    private int sampleRate = 8000;      //How much will be ideal?
    private int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_MONO;
    private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private boolean isAudioStreaming = true;

    private Camera.PreviewCallback mFrameCallback;

    private native void nativeInit();     // Initialize native code, build pipeline, etc
    private native void nativeFinalize(); // Destroy pipeline and shutdown native code
    private native void nativePlay();     // Set pipeline to PLAYING
    private native void nativePause();    // Set pipeline to PAUSED
    private static native boolean nativeClassInit(); // Initialize native class: cache Method IDs for callbacks
    private native void nativeSurfaceInit(Object surface);
    private native void nativeSurfaceFinalize();
    public long native_custom_data;      // Native code will use this to keep private data

    private boolean is_playing_desired;   // Whether the user asked to go to PLAYING
    private boolean is_video_call;

    Runnable senderRun = new Runnable() {
        @Override
        public void run()
        {
            while (isStreaming)
            {
                boolean empty = false;
                byte[] encData = null;

                synchronized(encDataList)
                {
                    if (encDataList.size() == 0)
                    {
                        empty = true;
                    }
                    else
                        encData = encDataList.remove(0);
                }
                if (empty)
                {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    continue;
                }
                try
                {
                    DatagramPacket packet = new DatagramPacket(encData, encData.length, address, video_port);
                    udpSocket.send(packet);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
            //TODO:
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


        Intent intent = getIntent();
        robotIP = intent.getExtras().getString("robot_ip");

        Toast.makeText(getApplicationContext(), robotIP,
                Toast.LENGTH_SHORT).show();

        pointMsg = new PointMsg();

        mBtnUp = (Button) findViewById(R.id.btnUp);
        mBtnDown = (Button) findViewById(R.id.btnDown);
        mBtnLeft = (Button) findViewById(R.id.btnLeft);
        mBtnRight = (Button) findViewById(R.id.btnRight);
        mBtnTurnLeft = (Button) findViewById(R.id.btnTurnLeft);
        mBtnTurnRight = (Button) findViewById(R.id.btnTurnRight);
        mBtnBell = (Button) findViewById(R.id.btnBell);
        mBtnHorn = (Button) findViewById(R.id.btnHorn);
        mBtnVideoCall = (Button) findViewById(R.id.btnVideoCall);

        mBtnBell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendBell();
            }
        });

        mBtnHorn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendHorn();
            }
        });

        mBtnUp.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_UP){
                    Toast.makeText(getApplicationContext(), "Stop!",
                            Toast.LENGTH_SHORT).show();

                    pointMsg.y = 0;
                    sendCommandMove();

                    return true;
                } else if(event.getAction() == MotionEvent.ACTION_DOWN){
                    Toast.makeText(getApplicationContext(), "Forward!",
                            Toast.LENGTH_SHORT).show();

                    pointMsg.y = linearSpeed;
                    sendCommandMove();

                    return true;
                }

                return false;
            }
        });
        mBtnDown.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_UP){
                    Toast.makeText(getApplicationContext(), "Stop!",
                            Toast.LENGTH_SHORT).show();

                    pointMsg.y = 0;
                    sendCommandMove();

                    return true;
                } else if(event.getAction() == MotionEvent.ACTION_DOWN){
                    Toast.makeText(getApplicationContext(), "Backward!",
                            Toast.LENGTH_SHORT).show();

                    pointMsg.y = -linearSpeed;
                    sendCommandMove();

                    return true;
                }

                return false;
            }
        });
        mBtnLeft.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_UP){
                    Toast.makeText(getApplicationContext(), "Stop!",
                            Toast.LENGTH_SHORT).show();

                    pointMsg.x = 0;
                    sendCommandMove();

                    return true;
                } else if(event.getAction() == MotionEvent.ACTION_DOWN){
                    Toast.makeText(getApplicationContext(), "Left!",
                            Toast.LENGTH_SHORT).show();

                    pointMsg.x = linearSpeed;
                    sendCommandMove();

                    return true;
                }

                return false;
            }
        });
        mBtnRight.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    Toast.makeText(getApplicationContext(), "Stop!",
                            Toast.LENGTH_SHORT).show();

                    pointMsg.x = 0;
                    sendCommandMove();

                    return true;
                } else if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    Toast.makeText(getApplicationContext(), "Right!",
                            Toast.LENGTH_SHORT).show();

                    pointMsg.x = -linearSpeed;
                    sendCommandMove();

                    return true;
                }

                return false;
            }
        });
        mBtnTurnRight.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    Toast.makeText(getApplicationContext(), "Stop!",
                            Toast.LENGTH_SHORT).show();

                    pointMsg.z = 0;
                    sendCommandMove();

                    return true;
                } else if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    Toast.makeText(getApplicationContext(), "Turn Right!",
                            Toast.LENGTH_SHORT).show();

                    pointMsg.z = -angularSpeed;
                    sendCommandMove();

                    return true;
                }

                return false;
            }
        });
        mBtnTurnLeft.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    Toast.makeText(getApplicationContext(), "Stop!",
                            Toast.LENGTH_SHORT).show();

                    pointMsg.z = 0;
                    sendCommandMove();

                    return true;
                } else if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    Toast.makeText(getApplicationContext(), "Turn Left!",
                            Toast.LENGTH_SHORT).show();

                    pointMsg.z = angularSpeed;
                    sendCommandMove();

                    return true;
                }

                return false;
            }
        });

        mBtnVideoCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startVideoCall();
            }
        });

        try {
            GStreamer.init(this);
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        SurfaceView sv = (SurfaceView) this.findViewById(R.id.surface_video);
        SurfaceHolder sh = sv.getHolder();
        sh.addCallback(this);

        if (savedInstanceState != null) {
            is_playing_desired = savedInstanceState.getBoolean("playing");
            Log.i ("GStreamer", "Activity created. Saved state is playing:" + is_playing_desired);
        } else {
            is_playing_desired = false;
            Log.i ("GStreamer", "Activity created. There is no saved state, playing: false");
        }

        nativeInit();

        is_video_call = false;

        initCameraHandler();
    }

    void initCameraHandler() {
        SurfaceView svCameraPreview = (SurfaceView) this.findViewById(R.id.svCameraPeviewMe);

        this.previewHolder = svCameraPreview.getHolder();
        this.previewHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder)
            {
                startCamera();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
            {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder)
            {
                stopCamera();
            }
        });

        mFrameCallback = new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera)
            {
                camera.addCallbackBuffer(previewBuffer);

                if (isStreaming)
                {
                    if (encDataLengthList.size() > 100)
                    {
                        Log.e(TAG, "OUT OF BUFFER");
                        return;
                    }

                    byte[] encData = encoder.offerEncoder(data);
                    if (encData.length > 0)
                    {
                        synchronized(encDataList)
                        {
                            encDataList.add(encData);
                        }
                    }
                }
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();
        is_playing_desired = true;
        sendConnectionStart();
        nativePlay();
    }

    @Override
    protected void onPause() {
        if (is_video_call) {
            this.stopStream();
            this.stopAudioStream();

            if (encoder != null)
                encoder.close();
        }

        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        sendConnectionEnd();
    }

    protected void onSaveInstanceState (Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.d("GStreamer", "Saving state, playing:" + is_playing_desired);
        outState.putBoolean("playing", is_playing_desired);
    }

    public void sendCommandMove() {
        String msg = "{\"cmd\": \"move\", \"x\":"+ pointMsg.x + ",\"y\":" + pointMsg.y +  ",\"z\":"+ pointMsg.z + "}";
        sendMessage(msg);
    }

    public void sendConnectionStart() {
        String msg = "{\"cmd\": \"connect\"}";
        sendMessage(msg);
    }

    public void sendConnectionEnd() {
        String msg = "{\"cmd\": \"disconnect\"}";
        sendMessage(msg);
    }

    public void sendBell() {
        String msg = "{\"cmd\": \"bell\"}";
        sendMessage(msg);
    }


    public void sendHorn() {
        String msg = "{\"cmd\": \"horn\"}";
        sendMessage(msg);
    }

    public void sendMessage(String msg) {
        MessageSender messageSender = new MessageSender(robotIP);
        messageSender.execute(msg);
    }

    protected void onDestroy() {
        sendConnectionEnd();
        nativeFinalize();
        super.onDestroy();
    }

    // Called from native code. This sets the content of the TextView from the UI thread.
    private void setMessage(final String message) {
        final TextView tv = (TextView) this.findViewById(R.id.textview_message);
        runOnUiThread (new Runnable() {
            public void run() {
                tv.setText(message);
            }
        });
    }

    // Called from native code. Native code calls this once it has created its pipeline and
    // the main loop is running, so it is ready to accept commands.
    private void onGStreamerInitialized () {
        Log.i ("GStreamer", "Gst initialized. Restoring state, playing:" + is_playing_desired);
        // Restore previous playing state
        if (is_playing_desired) {
            nativePlay();
        } else {
            nativePause();
        }
    }

    static {
        System.loadLibrary("gstreamer_android");
        System.loadLibrary("tutorial-3");
        nativeClassInit();
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d("GStreamer", "Surface changed to format " + format + " width "
                + width + " height " + height);
        nativeSurfaceInit (holder.getSurface());
    }

    public void surfaceCreated(SurfaceHolder holder) {
        Log.d("GStreamer", "Surface created: " + holder.getSurface());
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d("GStreamer", "Surface destroyed");
        nativeSurfaceFinalize ();
    }

    public void startVideoCall() {
      if (!is_video_call) {
          startStream(robotIP, video_port);
          startAudioStreaming(robotIP, audio_port);
          is_video_call = true;
          mBtnVideoCall.setText("Stop Video Call");

          String msg = "{\"cmd\": \"start_video_call\"}";
          sendMessage(msg);
      } else {
          String msg = "{\"cmd\": \"stop_video_call\"}";
          sendMessage(msg);
          is_video_call = false;

          stopVideoCall();
          mBtnVideoCall.setText("Video Call");
      }
    }

    private void startStream(String ip, int port)
    {
        SharedPreferences sp = this.getPreferences(Context.MODE_PRIVATE);
        int width = sp.getInt(SP_CAM_WIDTH, 0);
        int height = sp.getInt(SP_CAM_HEIGHT, 0);

        this.encoder = new AvcEncoder();
        this.encoder.init(width, height, DEFAULT_FRAME_RATE, DEFAULT_BIT_RATE);

        try
        {
            this.udpSocket = new DatagramSocket();
            this.address = InetAddress.getByName(ip);
            this.video_port = port;
        }
        catch (SocketException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return;
        }
        catch (UnknownHostException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return;
        }
        sp.edit().putString(SP_DEST_IP, ip).commit();
        sp.edit().putInt(SP_DEST_PORT, port).commit();

        this.isStreaming = true;
        Thread thrd = new Thread(senderRun);
        thrd.start();
    }

    private void stopStream()
    {
        this.isStreaming = false;

        if (this.encoder != null)
            this.encoder.close();
        this.encoder = null;
    }

    private void startCamera()
    {
        SharedPreferences sp = this.getPreferences(Context.MODE_PRIVATE);
        int width = sp.getInt(SP_CAM_WIDTH, 0);
        int height = sp.getInt(SP_CAM_HEIGHT, 0);
        if (width == 0)
        {
            Camera tmpCam = Camera.open(CAMERA_ID);
            Camera.Parameters params = tmpCam.getParameters();
            final List<Camera.Size> prevSizes = params.getSupportedPreviewSizes();
            int i = prevSizes.size()-1;
            width = prevSizes.get(i).width;
            height = prevSizes.get(i).height;
            sp.edit().putInt(SP_CAM_WIDTH, width).commit();
            sp.edit().putInt(SP_CAM_HEIGHT, height).commit();
            tmpCam.release();
            tmpCam = null;
        }

        this.previewHolder.setFixedSize(width, height);

        int stride = (int) Math.ceil(width/16.0f) * 16;
        int cStride = (int) Math.ceil(width/32.0f)  * 16;
        final int frameSize = stride * height;
        final int qFrameSize = cStride * height / 2;

        this.previewBuffer = new byte[frameSize + qFrameSize * 2];

        try
        {
            camera = Camera.open(CAMERA_ID);
            camera.setPreviewDisplay(this.previewHolder);
            Camera.Parameters params = camera.getParameters();
            params.setPreviewSize(width, height);
            params.setPreviewFormat(ImageFormat.YV12);
            camera.setParameters(params);
            camera.addCallbackBuffer(previewBuffer);
            camera.setPreviewCallbackWithBuffer(mFrameCallback);
            camera.startPreview();
        }
        catch (IOException e)
        {
            //TODO:
        }
        catch (RuntimeException e)
        {
            //TODO:
        }
    }

    public void startAudioStreaming(String ip, int port) {
        isAudioStreaming = true;
        Thread streamThread = new Thread(new Runnable() {

            @Override
            public void run() {
                try {

                    int minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
                    DatagramSocket socket = new DatagramSocket();
                    Log.d("VS", "Socket Created");

                    byte[] buffer = new byte[minBufSize];

                    Log.d("VS","Buffer created of size " + minBufSize);
                    DatagramPacket packet;

                    final InetAddress destination = InetAddress.getByName(ip);
                    Log.d("VS", "Address retrieved");

                    recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,sampleRate,channelConfig,audioFormat,minBufSize);
                    Log.d("VS", "Recorder initialized");

                    recorder.startRecording();

                    while(isAudioStreaming == true) {

                        //reading data from MIC into buffer
                        minBufSize = recorder.read(buffer, 0, buffer.length);

                        //putting buffer in the packet
                        packet = new DatagramPacket (buffer,buffer.length,destination,port);

                        socket.send(packet);
                    }

                } catch(UnknownHostException e) {
                    Log.e("VS", "UnknownHostException");
                } catch (IOException e) {
                    Log.e("VS", "IOException");
                }


            }

        });
        streamThread.start();
    }

    private void stopCamera()
    {
        if (camera != null)
        {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    public void stopAudioStream() {
        isAudioStreaming = false;
        recorder.release();
    }

    public void stopVideoCall() {
        stopStream();
        stopAudioStream();
    }

}