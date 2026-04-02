package org.libsdl.app;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;

/**
    SDLSurface. This is what we draw on, so we need to know when it's created
    in order to do anything useful.

    Because of this, that's where we set up the SDL thread
*/
public class SDLSurface extends SurfaceView implements SurfaceHolder.Callback,
    View.OnKeyListener, View.OnTouchListener, SensorEventListener  {

    // Sensors
    protected SensorManager mSensorManager;
    protected Display mDisplay;

    // Keep track of the surface size to normalize touch events
    protected float mWidth, mHeight;

    // Is SurfaceView ready for rendering
    public boolean mIsSurfaceReady;

    // Startup
    public SDLSurface(Context context) {
        super(context);
        getHolder().addCallback(this);

        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        setOnKeyListener(this);
        setOnTouchListener(this);

        mDisplay = ((WindowManager)context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        mSensorManager = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);

        setOnGenericMotionListener(SDLActivity.getMotionListener());

        // Some arbitrary defaults to avoid a potential division by zero
        mWidth = 1.0f;
        mHeight = 1.0f;

        mIsSurfaceReady = false;
    }

    public void handlePause() {
        enableSensor(Sensor.TYPE_ACCELEROMETER, false);
    }

    public void handleResume() {
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        setOnKeyListener(this);
        setOnTouchListener(this);
        enableSensor(Sensor.TYPE_ACCELEROMETER, true);
    }

    public Surface getNativeSurface() {
        return getHolder().getSurface();
    }

    // Called when we have a valid drawing surface
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.v("SDL", "surfaceCreated()");
        SDLActivity.onNativeSurfaceCreated();
    }

    // Called when we lose the surface
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.v("SDL", "surfaceDestroyed()");

        // Transition to pause, if needed
        SDLActivity.mNextNativeState = SDLActivity.NativeState.PAUSED;
        SDLActivity.handleNativeState();

        mIsSurfaceReady = false;
        SDLActivity.onNativeSurfaceDestroyed();
    }

    // Called when the surface is resized
    @Override
    public void surfaceChanged(SurfaceHolder holder,
                               int format, int width, int height) {
        Log.v("SDL", "surfaceChanged() - [Pro Optimized Version]");

        if (SDLActivity.mSingleton == null) {
            return;
        }

        mWidth = width;
        mHeight = height;
        int nDeviceWidth = width;
        int nDeviceHeight = height;
        
        // ==============================================================
        // 1. [性能分流] 32位求稳 (防爆显存黑屏)，64位求极致画质
        // ==============================================================
        boolean is64Bit = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            String[] abis = Build.SUPPORTED_64_BIT_ABIS;
            is64Bit = (abis != null && abis.length > 0);
        }
        
        if (is64Bit) {
            // 64位现代设备：使用最高色彩精度 RGBA_8888，完美呈现复杂滤镜
            holder.setFormat(PixelFormat.RGBA_8888); 
            Log.v("SDL", "64-bit Architecture Detected: Using RGBA_8888");
        } else {
            // 32位老旧设备：强制降级为 RGB_565，极大降低显存带宽压力，防止滤镜黑屏
            holder.setFormat(PixelFormat.RGB_565);
            Log.v("SDL", "32-bit Architecture Detected: Fallback to RGB_565");
        }

        // ==============================================================
        // 2. [极限流畅] 自动侦测并解锁手机硬件允许的最高刷新率
        // ==============================================================
        float maxRefreshRate = 60.0f;
        try {
            if (Build.VERSION.SDK_INT >= 17 /* Android 4.2 */) {
                DisplayMetrics realMetrics = new DisplayMetrics();
                mDisplay.getRealMetrics(realMetrics);
                nDeviceWidth = realMetrics.widthPixels;
                nDeviceHeight = realMetrics.heightPixels;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.view.Window window = ((android.app.Activity)getContext()).getWindow();
                WindowManager.LayoutParams params = window.getAttributes();
                Display.Mode[] modes = mDisplay.getSupportedModes();
                Display.Mode bestMode = modes[0];
                for (Display.Mode mode : modes) {
                    if (mode.getRefreshRate() > bestMode.getRefreshRate()) {
                        bestMode = mode;
                    }
                }
                params.preferredDisplayModeId = bestMode.getModeId();
                window.setAttributes(params);
                maxRefreshRate = bestMode.getRefreshRate();
                Log.v("SDL", "Unlocked Max Refresh Rate: " + maxRefreshRate + " Hz");
            } else {
                maxRefreshRate = mDisplay.getRefreshRate();
            }
        } catch(Exception ignored) {}

        synchronized(SDLActivity.getContext()) {
            // In case we're waiting on a size change after going fullscreen, send a notification.
            SDLActivity.getContext().notifyAll();
        }

        Log.v("SDL", "Window size: " + width + "x" + height);
        Log.v("SDL", "Device size: " + nDeviceWidth + "x" + nDeviceHeight);
        
        // 传递刷新率和分辨率给底层引擎
        SDLActivity.nativeSetScreenResolution(width, height, nDeviceWidth, nDeviceHeight, maxRefreshRate);
        SDLActivity.onNativeResize();

        // ==============================================================
        // 3. [终极修复] 强制引擎挂载，绝不静默跳过 (彻底干掉滤镜黑屏)
        // ==============================================================
        /* If the surface has been previously destroyed by onNativeSurfaceDestroyed, recreate it here */
        SDLActivity.onNativeSurfaceChanged();

        /* Surface is ready */
        mIsSurfaceReady = true;

        SDLActivity.mNextNativeState = SDLActivity.NativeState.RESUMED;
        SDLActivity.handleNativeState();
    }

    // Key events
    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return SDLActivity.handleKeyEvent(v, keyCode, event, null);
    }

    // Touch events
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        /* Ref: http://developer.android.com/training/gestures/multi.html */
        int touchDevId = event.getDeviceId();
        final int pointerCount = event.getPointerCount();
        int action = event.getActionMasked();
        int pointerFingerId;
        int i = -1;
        float x,y,p;

        if (touchDevId < 0) {
            touchDevId -= 1;
        }

        if (event.getSource() == InputDevice.SOURCE_MOUSE || event.getSource() == (InputDevice.SOURCE_MOUSE | InputDevice.SOURCE_TOUCHSCREEN)) {
            int mouseButton = 1;
            try {
                Object object = event.getClass().getMethod("getButtonState").invoke(event);
                if (object != null) {
                    mouseButton = (Integer) object;
                }
            } catch(Exception ignored) {}

            SDLGenericMotionListener_API12 motionListener = SDLActivity.getMotionListener();
            x = motionListener.getEventX(event);
            y = motionListener.getEventY(event);

            SDLActivity.onNativeMouse(mouseButton, action, x, y, motionListener.inRelativeMode());
        } else {
            switch(action) {
                case MotionEvent.ACTION_MOVE:
                    // ==============================================================
                    // 4. [电竞级防断触] 抓取硬件缓冲区里的“历史微操”轨迹
                    // ==============================================================
                    int historySize = event.getHistorySize();
                    for (int h = 0; h < historySize; h++) {
                        for (i = 0; i < pointerCount; i++) {
                            pointerFingerId = event.getPointerId(i);
                            x = event.getHistoricalX(i, h) / mWidth;
                            y = event.getHistoricalY(i, h) / mHeight;
                            p = event.getHistoricalPressure(i, h);
                            if (p > 1.0f) p = 1.0f;
                            SDLActivity.onNativeTouch(touchDevId, pointerFingerId, action, x, y, p);
                        }
                    }
                    // 处理当前最新坐标点
                    for (i = 0; i < pointerCount; i++) {
                        pointerFingerId = event.getPointerId(i);
                        x = event.getX(i) / mWidth;
                        y = event.getY(i) / mHeight;
                        p = event.getPressure(i);
                        if (p > 1.0f) {
                            p = 1.0f;
                        }
                        SDLActivity.onNativeTouch(touchDevId, pointerFingerId, action, x, y, p);
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_DOWN:
                    i = 0;
                    /* fallthrough */
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (i == -1) {
                        i = event.getActionIndex();
                    }

                    pointerFingerId = event.getPointerId(i);
                    x = event.getX(i) / mWidth;
                    y = event.getY(i) / mHeight;
                    p = event.getPressure(i);
                    if (p > 1.0f) {
                        p = 1.0f;
                    }
                    SDLActivity.onNativeTouch(touchDevId, pointerFingerId, action, x, y, p);
                    break;

                case MotionEvent.ACTION_CANCEL:
                    for (i = 0; i < pointerCount; i++) {
                        pointerFingerId = event.getPointerId(i);
                        x = event.getX(i) / mWidth;
                        y = event.getY(i) / mHeight;
                        p = event.getPressure(i);
                        if (p > 1.0f) {
                            p = 1.0f;
                        }
                        SDLActivity.onNativeTouch(touchDevId, pointerFingerId, MotionEvent.ACTION_UP, x, y, p);
                    }
                    break;

                default:
                    break;
            }
        }

        return true;
   }

    // Sensor events
    public void enableSensor(int sensortype, boolean enabled) {
        if (enabled) {
            mSensorManager.registerListener(this,
                            mSensorManager.getDefaultSensor(sensortype),
                            SensorManager.SENSOR_DELAY_GAME, null);
        } else {
            mSensorManager.unregisterListener(this,
                            mSensorManager.getDefaultSensor(sensortype));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // TODO
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

            int newOrientation;
            float x, y;
            switch (mDisplay.getRotation()) {
                case Surface.ROTATION_90:
                    x = -event.values[1];
                    y = event.values[0];
                    newOrientation = SDLActivity.SDL_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_270:
                    x = event.values[1];
                    y = -event.values[0];
                    newOrientation = SDLActivity.SDL_ORIENTATION_LANDSCAPE_FLIPPED;
                    break;
                case Surface.ROTATION_180:
                    x = -event.values[0];
                    y = -event.values[1];
                    newOrientation = SDLActivity.SDL_ORIENTATION_PORTRAIT_FLIPPED;
                    break;
                case Surface.ROTATION_0:
                default:
                    x = event.values[0];
                    y = event.values[1];
                    newOrientation = SDLActivity.SDL_ORIENTATION_PORTRAIT;
                    break;
            }

            if (newOrientation != SDLActivity.mCurrentOrientation) {
                SDLActivity.mCurrentOrientation = newOrientation;
                SDLActivity.onNativeOrientationChanged(newOrientation);
            }

            SDLActivity.onNativeAccel(-x / SensorManager.GRAVITY_EARTH,
                                      y / SensorManager.GRAVITY_EARTH,
                                      event.values[2] / SensorManager.GRAVITY_EARTH);
        }
    }

    // Captured pointer events for API 26.
    public boolean onCapturedPointerEvent(MotionEvent event)
    {
        int action = event.getActionMasked();

        float x, y;
        switch (action) {
            case MotionEvent.ACTION_SCROLL:
                x = event.getAxisValue(MotionEvent.AXIS_HSCROLL, 0);
                y = event.getAxisValue(MotionEvent.AXIS_VSCROLL, 0);
                SDLActivity.onNativeMouse(0, action, x, y, false);
                return true;

            case MotionEvent.ACTION_HOVER_MOVE:
            case MotionEvent.ACTION_MOVE:
                x = event.getX(0);
                y = event.getY(0);
                SDLActivity.onNativeMouse(0, action, x, y, true);
                return true;

            case MotionEvent.ACTION_BUTTON_PRESS:
            case MotionEvent.ACTION_BUTTON_RELEASE:

                // Change our action value to what SDL's code expects.
                if (action == MotionEvent.ACTION_BUTTON_PRESS) {
                    action = MotionEvent.ACTION_DOWN;
                } else { /* MotionEvent.ACTION_BUTTON_RELEASE */
                    action = MotionEvent.ACTION_UP;
                }

                x = event.getX(0);
                y = event.getY(0);
                int button = event.getButtonState();

                SDLActivity.onNativeMouse(button, action, x, y, true);
                return true;
        }

        return false;
    }
}
