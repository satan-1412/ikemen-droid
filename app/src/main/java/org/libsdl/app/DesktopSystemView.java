package org.libsdl.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Ikemen GO 真·PC桌面系统引擎 (Pro版 Win10拟物风 / 媒体矩阵 / 全局字体定制)
 */
public class DesktopSystemView extends Dialog {

    public static DesktopSystemView instance;

    private Context mContext;
    private SharedPreferences prefs;
    private float density;

    // === 全局鼠标与触摸引擎 ===
    private float mouseX = -1f, mouseY = -1f;
    private Path cursorPath;
    private Paint cursorPaintFill, cursorPaintStroke;

    // === 桌面层级容器 ===
    private FrameLayout rootLayer;
    private FrameLayout desktopBgLayer;
    private FrameLayout desktopIconsLayer;
    private FrameLayout windowsLayer;
    private LinearLayout taskbarAppsLayout;

    // === 系统设置参数 ===
    public int bgAlpha = 180; 
    public int gridSizeBase = 100;
    public boolean showGrid = false;
    public int iconShape = 1;
    public String customDesktopBg = "";
    public String customWindowBg = "";

    // === 媒体与视图高阶参数 ===
    public int mediaVolume = 50; 
    public int mediaScaleMode = 1; // 0=拉伸, 1=裁切, 2=居中
    
    // === 字体定制引擎 ===
    public String fontPath = "";
    public Typeface customFont = null;
    public int fontColor = Color.WHITE;
    public float fontSize = 12f;
    public boolean fontShadowEnabled = true;
    public int fontShadowColor = Color.BLACK;

    // 文件选择器中继器
    public int currentPickerTarget = 0; // 1=桌面壁纸, 2=窗口壁纸, 3=字体文件
    public EditText targetInputRef = null;

    public DesktopSystemView(Context context) {
        super(context, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        this.mContext = context;
        this.prefs = context.getSharedPreferences("IkemenDesktopPrefs", Context.MODE_PRIVATE);
        this.density = context.getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onStart() {
        super.onStart();
        Window window = getWindow();
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override
    public void show() {
        super.show();
        instance = this;
    }

    @Override
    public void dismiss() {
        super.dismiss();
        if (instance == this) instance = null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadDesktopSettings();
        initMouseEngine();

        rootLayer = new FrameLayout(getContext()) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                if (showGrid) {
                    Paint gridPaint = new Paint(); gridPaint.setColor(Color.argb(50, 255, 255, 255)); gridPaint.setStrokeWidth(1);
                    float actualGrid = gridSizeBase * density;
                    for (float x = 0; x < getWidth(); x += actualGrid) canvas.drawLine(x, 0, x, getHeight(), gridPaint);
                    for (float y = 0; y < getHeight(); y += actualGrid) canvas.drawLine(0, y, getWidth(), y, gridPaint);
                }
                if (mouseX >= 0 && mouseY >= 0) {
                    canvas.save(); canvas.translate(mouseX, mouseY);
                    canvas.drawPath(cursorPath, cursorPaintFill); canvas.drawPath(cursorPath, cursorPaintStroke);
                    canvas.restore();
                }
            }
            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) { mouseX = -1f; mouseY = -1f; }
                else { mouseX = event.getX(); mouseY = event.getY(); }
                invalidate();
                return super.dispatchTouchEvent(event);
            }
        };
        rootLayer.setClickable(true);

        desktopBgLayer = new FrameLayout(getContext());
        rootLayer.addView(desktopBgLayer, new FrameLayout.LayoutParams(-1, -1));
        refreshDesktopBackground();

        desktopIconsLayer = new FrameLayout(getContext());
        windowsLayer = new FrameLayout(getContext());
        rootLayer.addView(desktopIconsLayer, new FrameLayout.LayoutParams(-1, -1));
        rootLayer.addView(windowsLayer, new FrameLayout.LayoutParams(-1, -1));

        // 任务栏 (Win 10 风格)
        LinearLayout taskbar = new LinearLayout(getContext());
        taskbar.setOrientation(LinearLayout.HORIZONTAL);
        taskbar.setGravity(Gravity.CENTER_VERTICAL);
        taskbar.setPadding((int)(15*density), 0, (int)(15*density), 0);
        taskbar.setBackgroundColor(Color.parseColor("#E6111111")); // 深色几乎不透明
        FrameLayout.LayoutParams taskbarParams = new FrameLayout.LayoutParams(-1, (int)(50*density));
        taskbarParams.gravity = Gravity.BOTTOM;
        rootLayer.addView(taskbar, taskbarParams);

        // "进入游戏" 按钮
        final LinearLayout startBtn = new LinearLayout(getContext());
        startBtn.setOrientation(LinearLayout.HORIZONTAL); startBtn.setGravity(Gravity.CENTER);
        startBtn.setPadding((int)(15*density), (int)(8*density), (int)(15*density), (int)(8*density));
        
        TextView btnIcon = new TextView(getContext()); btnIcon.setText("⊞"); btnIcon.setTextColor(Color.parseColor("#00A4EF")); btnIcon.setTextSize(20f);
        TextView btnText = new TextView(getContext()); btnText.setText(" 进入游戏"); btnText.setTextColor(Color.WHITE); btnText.setTextSize(16f); btnText.setTypeface(null, Typeface.BOLD);
        startBtn.addView(btnIcon); startBtn.addView(btnText);
        
        startBtn.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) { v.setBackgroundColor(Color.parseColor("#33FFFFFF")); }
            else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.setBackgroundColor(Color.TRANSPARENT);
                if (event.getAction() == MotionEvent.ACTION_UP) { hide(); if (SDLActivity.mSingleton != null) SDLActivity.mSingleton.toggleDesktopMode(false); }
            }
            return true;
        });
        taskbar.addView(startBtn);

        HorizontalScrollView taskbarScroll = new HorizontalScrollView(getContext());
        taskbarScroll.setHorizontalScrollBarEnabled(false);
        taskbarAppsLayout = new LinearLayout(getContext());
        taskbarAppsLayout.setOrientation(LinearLayout.HORIZONTAL);
        taskbarAppsLayout.setGravity(Gravity.CENTER_VERTICAL);
        taskbarAppsLayout.setPadding((int)(15*density), 0, 0, 0);
        taskbarScroll.addView(taskbarAppsLayout, new ViewGroup.LayoutParams(-2, -1));
        taskbar.addView(taskbarScroll, new LinearLayout.LayoutParams(0, -1, 1f));

        setContentView(rootLayer);
        setupDesktopIcons();
    }

    private void initMouseEngine() {
        cursorPaintFill = new Paint(Paint.ANTI_ALIAS_FLAG); cursorPaintFill.setColor(Color.WHITE); cursorPaintFill.setStyle(Paint.Style.FILL);
        cursorPaintStroke = new Paint(Paint.ANTI_ALIAS_FLAG); cursorPaintStroke.setColor(Color.BLACK); cursorPaintStroke.setStyle(Paint.Style.STROKE); cursorPaintStroke.setStrokeWidth(1.5f * density);
        cursorPath = new Path();
        cursorPath.moveTo(0, 0); cursorPath.lineTo(0, 35); cursorPath.lineTo(9, 26); cursorPath.lineTo(16, 42);
        cursorPath.lineTo(22, 38); cursorPath.lineTo(15, 22); cursorPath.lineTo(26, 22); cursorPath.close();
        Matrix scaleMatrix = new Matrix(); scaleMatrix.setScale(density * 0.4f, density * 0.4f); cursorPath.transform(scaleMatrix);
    }

    // ==========================================
    // 媒体解析引擎 (TextureView 独立解码，防BGM打断)
    // ==========================================
    private View createMediaBackground(String path, int alpha) {
        if (path == null || path.trim().isEmpty()) return null;
        File file = new File(path); if (!file.exists()) return null;
        String p = path.toLowerCase();
        
        FrameLayout mediaContainer = new FrameLayout(mContext);
        mediaContainer.setAlpha(alpha / 255f);

        if (p.endsWith(".mp4") || p.endsWith(".avi") || p.endsWith(".mkv") || p.endsWith(".webm")) {
            final TextureView tv = new TextureView(mContext);
            mediaContainer.addView(tv, new FrameLayout.LayoutParams(-1, -1)); // 初始填充
            tv.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                private MediaPlayer mp;
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    try {
                        mp = new MediaPlayer();
                        mp.setDataSource(mContext, Uri.parse("file://" + path));
                        mp.setSurface(new Surface(surface));
                        mp.setLooping(true);
                        // 【核心修复】分离音轨，且不申请系统焦点
                        float vol = mediaVolume / 100f;
                        mp.setVolume(vol, vol);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build());
                        }
                        mp.prepareAsync();
                        mp.setOnPreparedListener(m -> {
                            int vw = m.getVideoWidth();
                            int vh = m.getVideoHeight();
                            int pw = mediaContainer.getWidth();
                            int ph = mediaContainer.getHeight();
                            if (pw > 0 && ph > 0 && vw > 0 && vh > 0) {
                                FrameLayout.LayoutParams lp;
                                if (mediaScaleMode == 2) { // 居中 (Center)
                                    lp = new FrameLayout.LayoutParams(vw, vh, Gravity.CENTER);
                                } else if (mediaScaleMode == 1) { // 裁切 (Crop)
                                    float scale = Math.max((float)pw/vw, (float)ph/vh);
                                    lp = new FrameLayout.LayoutParams((int)(vw*scale), (int)(vh*scale), Gravity.CENTER);
                                } else { // 拉伸 (Stretch)
                                    lp = new FrameLayout.LayoutParams(-1, -1);
                                }
                                tv.setLayoutParams(lp);
                            }
                            m.start();
                        });
                    } catch (Exception e) {}
                }
                @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}
                @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    if (mp != null) { mp.release(); mp = null; } return true;
                }
                @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
            });
            return mediaContainer;
        } else if (p.endsWith(".gif")) {
            WebView wv = new WebView(mContext);
            wv.loadDataWithBaseURL("", "<html style='margin:0;padding:0;'><body style='margin:0;padding:0;background-color:transparent;display:flex;justify-content:center;align-items:center;height:100vh;'><img src='file://" + path + "' style='width:100%;height:100%;object-fit:" + (mediaScaleMode==0?"fill":(mediaScaleMode==1?"cover":"contain")) + ";' /></body></html>", "text/html", "utf-8", null);
            wv.setBackgroundColor(Color.TRANSPARENT);
            wv.setAlpha(alpha / 255f);
            return wv;
        } else {
            ImageView iv = new ImageView(mContext); iv.setImageURI(Uri.parse("file://" + path)); 
            if (mediaScaleMode == 2) iv.setScaleType(ImageView.ScaleType.CENTER);
            else if (mediaScaleMode == 1) iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            else iv.setScaleType(ImageView.ScaleType.FIT_XY);
            iv.setAlpha(alpha / 255f);
            return iv;
        }
    }

    private void refreshDesktopBackground() {
        desktopBgLayer.removeAllViews();
        View media = createMediaBackground(customDesktopBg, bgAlpha);
        if (media != null) {
            desktopBgLayer.addView(media, new FrameLayout.LayoutParams(-1, -1));
        } else {
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.parseColor("#1B1B1B"), Color.parseColor("#2D2D30")});
            bg.setAlpha(bgAlpha); desktopBgLayer.setBackground(bg);
        }
    }

    // ==========================================
    // 桌面图标与网格系统
    // ==========================================
    private void setupDesktopIcons() {
        desktopIconsLayer.removeAllViews();
        createDesktopIcon("sys_settings", "⚙️", "系统控制台");
    }

    private void createDesktopIcon(final String id, String iconStr, String name) {
        final LinearLayout iconLayout = new LinearLayout(getContext());
        iconLayout.setOrientation(LinearLayout.VERTICAL); iconLayout.setGravity(Gravity.CENTER);
        
        float actualGrid = gridSizeBase * density;
        // 【核心修复】网格比图标容器大2个像素，精准对齐
        float iconSize = actualGrid - 2f * density; 
        
        float savedX = prefs.getFloat("icon_x_" + id, actualGrid * 0.2f);
        float savedY = prefs.getFloat("icon_y_" + id, actualGrid * 0.2f);

        TextView iconView = new TextView(getContext()); iconView.setText(iconStr); iconView.setTextSize(26f); iconView.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#44000000"));
        if (iconShape == 1) bg.setCornerRadius(6f*density); else if (iconShape == 2) bg.setCornerRadius(50f*density); else bg.setColor(Color.TRANSPARENT);
        iconView.setBackground(bg);
        
        iconLayout.addView(iconView, new LinearLayout.LayoutParams((int)(iconSize*0.6f), (int)(iconSize*0.6f)));
        TextView nameView = new TextView(getContext()); nameView.setText(name); 
        applyCustomFontSettings(nameView); // 应用字体
        nameView.setSingleLine(true);
        iconLayout.addView(nameView, new LinearLayout.LayoutParams(-2, -2));

        iconLayout.setLayoutParams(new FrameLayout.LayoutParams((int)iconSize, (int)iconSize));
        iconLayout.setX(savedX); iconLayout.setY(savedY);
        desktopIconsLayer.addView(iconLayout);

        iconLayout.setOnTouchListener(new View.OnTouchListener() {
            private float offsetX, offsetY;
            private boolean isDragging = false;
            private long lastClickTime = 0;
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    offsetX = view.getX() - mouseX; offsetY = view.getY() - mouseY; isDragging = false;
                    view.setBackgroundColor(Color.parseColor("#44FFFFFF"));
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (!isDragging && (Math.abs(mouseX - (view.getX() - offsetX)) > 10 || Math.abs(mouseY - (view.getY() - offsetY)) > 10)) {
                        isDragging = true; view.bringToFront();
                    }
                    if (isDragging) { view.setX(mouseX + offsetX); view.setY(mouseY + offsetY); }
                } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    view.setBackgroundColor(Color.TRANSPARENT);
                    if (isDragging) {
                        // 【精准吸附引擎】
                        float finalX = Math.round(view.getX() / actualGrid) * actualGrid + (actualGrid - iconSize)/2f;
                        float finalY = Math.round(view.getY() / actualGrid) * actualGrid + (actualGrid - iconSize)/2f;
                        view.setX(finalX); view.setY(finalY);
                        prefs.edit().putFloat("icon_x_" + id, finalX).putFloat("icon_y_" + id, finalY).apply();
                    } else {
                        long clickTime = System.currentTimeMillis();
                        if (clickTime - lastClickTime < 350) { handleIconDoubleTap(id); lastClickTime = 0; }
                        else lastClickTime = clickTime;
                    }
                }
                return true;
            }
        });
    }

    private void handleIconDoubleTap(String id) {
        if (id.equals("sys_settings")) openAppWindow("系统控制台", buildSettingsContent());
    }

    // ==========================================
    // 真·PC窗口管理系统 (Win 10 拟物风)
    // ==========================================
    private void openAppWindow(String windowTitle, View contentView) {
        View existingWin = windowsLayer.findViewWithTag(windowTitle);
        if (existingWin != null) {
            existingWin.setVisibility(View.VISIBLE); existingWin.bringToFront(); return;
        }

        final FrameLayout windowFrame = new FrameLayout(getContext());
        windowFrame.setTag(windowTitle); windowFrame.setClickable(true);
        
        View winMediaBg = createMediaBackground(customWindowBg, 255);
        if (winMediaBg != null) {
            windowFrame.addView(winMediaBg, new FrameLayout.LayoutParams(-1, -1));
        } else {
            GradientDrawable winBg = new GradientDrawable(); winBg.setColor(Color.parseColor("#FA1E1E1E")); 
            winBg.setStroke(2, Color.parseColor("#3F3F46")); // Win 10 细边框
            windowFrame.setBackground(winBg);
        }
        windowFrame.setElevation(25f * density);

        LinearLayout winContainer = new LinearLayout(getContext());
        winContainer.setOrientation(LinearLayout.VERTICAL);
        windowFrame.addView(winContainer, new FrameLayout.LayoutParams(-1, -1));

        // 标题栏
        final LinearLayout titleBar = new LinearLayout(getContext());
        titleBar.setOrientation(LinearLayout.HORIZONTAL); titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setBackgroundColor(Color.parseColor("#F22D2D30")); // Win 10 Dark Title
        
        TextView title = new TextView(getContext()); title.setText("  " + windowTitle);
        applyCustomFontSettings(title); title.setTextSize(14f);
        titleBar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout controls = new LinearLayout(getContext()); controls.setOrientation(LinearLayout.HORIZONTAL);
        
        TextView btnMin = new TextView(getContext()); btnMin.setText(" ─ "); btnMin.setTextColor(Color.WHITE); btnMin.setTextSize(14f); btnMin.setPadding((int)(15*density), (int)(5*density), (int)(15*density), (int)(8*density));
        btnMin.setOnClickListener(v -> windowFrame.setVisibility(View.GONE));
        controls.addView(btnMin);

        TextView btnClose = new TextView(getContext()); btnClose.setText(" ✕ "); btnClose.setTextColor(Color.WHITE); btnClose.setTextSize(14f); btnClose.setPadding((int)(15*density), (int)(5*density), (int)(15*density), (int)(5*density));
        btnClose.setOnTouchListener((v, e) -> {
            if(e.getAction()==MotionEvent.ACTION_DOWN) v.setBackgroundColor(Color.parseColor("#E81123")); // Win10 经典红
            else if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL) v.setBackgroundColor(Color.TRANSPARENT);
            return false;
        });
        controls.addView(btnClose);
        titleBar.addView(controls);

        titleBar.setOnTouchListener(new View.OnTouchListener() {
            float dX, dY;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) { dX = windowFrame.getX() - mouseX; dY = windowFrame.getY() - mouseY; windowFrame.bringToFront(); }
                else if (event.getAction() == MotionEvent.ACTION_MOVE) { windowFrame.setX(mouseX + dX); windowFrame.setY(mouseY + dY); }
                return true;
            }
        });

        winContainer.addView(titleBar, new LinearLayout.LayoutParams(-1, (int)(35*density)));
        View sep = new View(getContext()); sep.setBackgroundColor(Color.parseColor("#0078D7"));
        winContainer.addView(sep, new LinearLayout.LayoutParams(-1, (int)(1.5f*density)));
        winContainer.addView(contentView, new LinearLayout.LayoutParams(-1, -1));

        // 【核心修复】动态任务栏与滑动重排
        final LinearLayout taskBtn = new LinearLayout(getContext());
        taskBtn.setOrientation(LinearLayout.HORIZONTAL); taskBtn.setGravity(Gravity.CENTER);
        taskBtn.setPadding((int)(10*density), (int)(5*density), (int)(10*density), (int)(5*density));
        GradientDrawable tbBg = new GradientDrawable(); tbBg.setColor(Color.parseColor("#22FFFFFF")); 
        taskBtn.setBackground(tbBg);
        LinearLayout.LayoutParams tbParams = new LinearLayout.LayoutParams(-2, -1); tbParams.setMargins(0,0,(int)(5*density),0);
        
        TextView tbText = new TextView(getContext()); tbText.setText("▤ " + windowTitle.split(" ")[0]); // 现代化符号
        applyCustomFontSettings(tbText); tbText.setTextSize(13f);
        taskBtn.addView(tbText);
        
        taskBtn.setOnTouchListener(new View.OnTouchListener() {
            float startX, originalX;
            boolean isDragging = false;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX(); originalX = v.getX(); isDragging = false;
                        v.setBackgroundColor(Color.parseColor("#44FFFFFF")); v.bringToFront();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - startX;
                        if (Math.abs(dx) > 10) isDragging = true;
                        if (isDragging) v.setX(originalX + dx);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setBackground(tbBg);
                        if (isDragging) {
                            int newIndex = -1;
                            float currentCenter = v.getX() + v.getWidth()/2f;
                            for (int i=0; i<taskbarAppsLayout.getChildCount(); i++) {
                                View child = taskbarAppsLayout.getChildAt(i);
                                if (child != v && currentCenter < child.getX() + child.getWidth()/2f) {
                                    newIndex = i; break;
                                }
                            }
                            taskbarAppsLayout.removeView(v);
                            if (newIndex == -1) taskbarAppsLayout.addView(v, tbParams);
                            else taskbarAppsLayout.addView(v, newIndex, tbParams);
                            for (int i=0; i<taskbarAppsLayout.getChildCount(); i++) taskbarAppsLayout.getChildAt(i).setTranslationX(0); // 归位
                        } else {
                            if (windowFrame.getVisibility() == View.VISIBLE) windowFrame.setVisibility(View.GONE);
                            else { windowFrame.setVisibility(View.VISIBLE); windowFrame.bringToFront(); }
                        }
                        return true;
                }
                return false;
            }
        });
        taskbarAppsLayout.addView(taskBtn, tbParams);

        btnClose.setOnClickListener(v -> { windowsLayer.removeView(windowFrame); taskbarAppsLayout.removeView(taskBtn); });

        int w = (int) (rootLayer.getWidth() * 0.70f); int h = (int) (rootLayer.getHeight() * 0.80f);
        FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(w, h); frameParams.gravity = Gravity.CENTER;
        windowsLayer.addView(windowFrame, frameParams);
    }

    // ==========================================
    // 字体辅助与系统设置引擎
    // ==========================================
    private void applyCustomFontSettings(TextView tv) {
        if (customFont != null) tv.setTypeface(customFont);
        tv.setTextColor(fontColor);
        tv.setTextSize(fontSize);
        if (fontShadowEnabled) {
            tv.setShadowLayer(4f, 2f, 2f, fontShadowColor);
        } else {
            // 【编译错误修复点】：使用设为透明的方式来清除阴影，完美兼容所有 Android 版本
            tv.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT); 
        }
    }

    private void loadDesktopSettings() {
        bgAlpha = prefs.getInt("dt_bgAlpha", 180);
        gridSizeBase = prefs.getInt("dt_gridSize", 100);
        showGrid = prefs.getBoolean("dt_showGrid", false);
        customDesktopBg = prefs.getString("dt_customDeskBg", "");
        customWindowBg = prefs.getString("dt_customWinBg", "");
        
        mediaVolume = prefs.getInt("dt_mediaVol", 50);
        mediaScaleMode = prefs.getInt("dt_mediaScale", 1);
        
        fontPath = prefs.getString("dt_fontPath", "");
        if (!fontPath.isEmpty()) {
            try { customFont = Typeface.createFromFile(fontPath); } catch (Exception e) { customFont = null; }
        } else { customFont = null; }
        fontColor = prefs.getInt("dt_fontColor", Color.WHITE);
        fontSize = prefs.getFloat("dt_fontSize", 12f);
        fontShadowEnabled = prefs.getBoolean("dt_fontShadow", true);
        fontShadowColor = prefs.getInt("dt_fontShadowC", Color.BLACK);
    }

    private View buildSettingsContent() {
        ScrollView scroll = new ScrollView(getContext());
        LinearLayout layout = new LinearLayout(getContext()); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding((int)(20*density), (int)(10*density), (int)(20*density), (int)(20*density));

        // --- 1. 桌面基础设置 ---
        layout.addView(createTitle("🖥️ 桌面基础布局"));
        layout.addView(createSubTitle("桌面背景不透明度 (拉到0完全显示底层游戏):"));
        SeekBar alphaBar = new SeekBar(getContext()); alphaBar.setMax(255); alphaBar.setProgress(bgAlpha);
        alphaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean b) { bgAlpha = p; refreshDesktopBackground(); }
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
        });
        layout.addView(alphaBar);

        layout.addView(createSubTitle("桌面网格间距 (系统自动对齐减去边距):"));
        SeekBar gridBar = new SeekBar(getContext()); gridBar.setMax(250); gridBar.setProgress(gridSizeBase);
        gridBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean b) { gridSizeBase = Math.max(60, p); rootLayer.invalidate(); }
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){ setupDesktopIcons(); }
        });
        layout.addView(gridBar);

        Button gridToggle = new Button(getContext()); gridToggle.setText(showGrid ? "✔️ 网格辅助线：开启" : "❌ 网格辅助线：关闭");
        gridToggle.setTextColor(Color.WHITE); gridToggle.setBackgroundColor(Color.parseColor("#333333"));
        gridToggle.setOnClickListener(v -> { showGrid = !showGrid; gridToggle.setText(showGrid ? "✔️ 网格辅助线：开启" : "❌ 网格辅助线：关闭"); rootLayer.invalidate(); });
        layout.addView(gridToggle);

        // --- 2. 字体与颜色定制引擎 ---
        layout.addView(createTitle("🅰️ 字体定制引擎"));
        
        layout.addView(createSubTitle("自选本地字体库 (TTF/OTF):"));
        final EditText fontInput = createInput("选定的字体路径", fontPath); layout.addView(fontInput);
        Button pickFont = new Button(getContext()); pickFont.setText("选择自定义字体文件");
        pickFont.setOnClickListener(v -> { currentPickerTarget = 3; targetInputRef = fontInput; launchSystemFilePicker("*/*"); });
        layout.addView(pickFont);

        layout.addView(createSubTitle("字体字号大小:"));
        SeekBar sizeBar = new SeekBar(getContext()); sizeBar.setMax(30); sizeBar.setProgress((int)fontSize);
        sizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean b) { fontSize = Math.max(8, p); }
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
        });
        layout.addView(sizeBar);

        layout.addView(createSubTitle("全局字体颜色代码 (Hex):"));
        final EditText colorInput = createInput("如: #FFFFFF", String.format("#%06X", (0xFFFFFF & fontColor))); layout.addView(colorInput);
        colorInput.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) { try{ fontColor = Color.parseColor(s.toString()); }catch(Exception e){} }
            public void beforeTextChanged(CharSequence s, int x, int y, int z) {} public void onTextChanged(CharSequence s, int x, int y, int z) {}
        });

        Button shadowToggle = new Button(getContext()); shadowToggle.setText(fontShadowEnabled ? "✔️ 字体投影：开启" : "❌ 字体投影：关闭");
        shadowToggle.setTextColor(Color.WHITE); shadowToggle.setBackgroundColor(Color.parseColor("#333333"));
        shadowToggle.setOnClickListener(v -> { fontShadowEnabled = !fontShadowEnabled; shadowToggle.setText(fontShadowEnabled ? "✔️ 字体投影：开启" : "❌ 字体投影：关闭"); });
        layout.addView(shadowToggle);

        layout.addView(createSubTitle("投影颜色代码 (Hex):"));
        final EditText shadowColorInput = createInput("如: #000000", String.format("#%06X", (0xFFFFFF & fontShadowColor))); layout.addView(shadowColorInput);
        shadowColorInput.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) { try{ fontShadowColor = Color.parseColor(s.toString()); }catch(Exception e){} }
            public void beforeTextChanged(CharSequence s, int x, int y, int z) {} public void onTextChanged(CharSequence s, int x, int y, int z) {}
        });

        // --- 3. 媒体引擎设置 ---
        layout.addView(createTitle("🎬 动态媒体矩阵 (支持任何格式图片/GIF/视频)"));
        
        layout.addView(createSubTitle("多媒体渲染模式:"));
        Spinner scaleSpinner = new Spinner(getContext());
        ArrayAdapter<String> scaleAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, new String[]{"📏 强制拉伸填满", "✂️ 居中裁切填满", "🎯 保持原比例居中"});
        scaleSpinner.setAdapter(scaleAdapter); scaleSpinner.setSelection(mediaScaleMode);
        layout.addView(scaleSpinner);

        layout.addView(createSubTitle("后台视频独立音量 (静音绝不影响BGM):"));
        SeekBar volBar = new SeekBar(getContext()); volBar.setMax(100); volBar.setProgress(mediaVolume);
        volBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean b) { mediaVolume = p; }
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
        });
        layout.addView(volBar);

        layout.addView(createSubTitle("🖼️ 桌面动态壁纸路径:"));
        EditText deskBgInput = createInput("选择文件获取绝对路径", customDesktopBg); layout.addView(deskBgInput);
        Button pickDesk = new Button(getContext()); pickDesk.setText("调用系统安全选择器获取资源"); 
        pickDesk.setOnClickListener(v -> { currentPickerTarget = 1; targetInputRef = deskBgInput; launchSystemFilePicker("*/*"); });
        layout.addView(pickDesk);

        layout.addView(createSubTitle("🪟 窗口动态壁纸路径:"));
        EditText winBgInput = createInput("选择文件获取绝对路径", customWindowBg); layout.addView(winBgInput);
        Button pickWin = new Button(getContext()); pickWin.setText("调用系统安全选择器获取资源"); 
        pickWin.setOnClickListener(v -> { currentPickerTarget = 2; targetInputRef = winBgInput; launchSystemFilePicker("*/*"); });
        layout.addView(pickWin);

        // --- 保存与退出 ---
        Button saveBtn = new Button(getContext()); saveBtn.setText("💾 保存设置并重载引擎"); saveBtn.setTextColor(Color.WHITE); saveBtn.setBackgroundColor(Color.parseColor("#0078D7"));
        LinearLayout.LayoutParams btnP = new LinearLayout.LayoutParams(-1, -2); btnP.setMargins(0, (int)(30*density), 0, 0); saveBtn.setLayoutParams(btnP);
        saveBtn.setOnClickListener(v -> {
            customDesktopBg = deskBgInput.getText().toString().trim();
            customWindowBg = winBgInput.getText().toString().trim();
            fontPath = fontInput.getText().toString().trim();
            mediaScaleMode = scaleSpinner.getSelectedItemPosition();
            
            prefs.edit().putInt("dt_bgAlpha", bgAlpha).putInt("dt_gridSize", gridSizeBase).putBoolean("dt_showGrid", showGrid)
                 .putString("dt_customDeskBg", customDesktopBg).putString("dt_customWinBg", customWindowBg)
                 .putInt("dt_mediaVol", mediaVolume).putInt("dt_mediaScale", mediaScaleMode)
                 .putString("dt_fontPath", fontPath).putInt("dt_fontColor", fontColor).putFloat("dt_fontSize", fontSize)
                 .putBoolean("dt_fontShadow", fontShadowEnabled).putInt("dt_fontShadowC", fontShadowColor).apply();
            
            loadDesktopSettings(); // 重新加载字库
            refreshDesktopBackground();
            setupDesktopIcons();
            Toast.makeText(getContext(), "✅ 系统设置已全面刷新", Toast.LENGTH_SHORT).show();
        });
        layout.addView(saveBtn);
        
        // --- 恢复默认出口 ---
        Button resetBtn = new Button(getContext()); resetBtn.setText("🔄 恢复默认桌面设置"); resetBtn.setTextColor(Color.WHITE); resetBtn.setBackgroundColor(Color.parseColor("#E81123"));
        LinearLayout.LayoutParams rBtnP = new LinearLayout.LayoutParams(-1, -2); rBtnP.setMargins(0, (int)(15*density), 0, (int)(20*density)); resetBtn.setLayoutParams(rBtnP);
        resetBtn.setOnClickListener(v -> {
            prefs.edit().clear().apply();
            loadDesktopSettings(); refreshDesktopBackground(); setupDesktopIcons();
            Toast.makeText(getContext(), "已清除所有桌面定制参数", Toast.LENGTH_SHORT).show();
            hide(); // 关闭设置窗
        });
        layout.addView(resetBtn);

        scroll.addView(layout);
        return scroll;
    }

    private TextView createTitle(String text) {
        TextView tv = new TextView(getContext()); tv.setText(text); tv.setTextColor(Color.parseColor("#00A4EF"));
        tv.setTextSize(16f); tv.setPadding(0, (int)(25*density), 0, (int)(10*density)); tv.setTypeface(null, Typeface.BOLD);
        return tv;
    }
    
    private TextView createSubTitle(String text) {
        TextView tv = new TextView(getContext()); tv.setText(text); tv.setTextColor(Color.LTGRAY);
        tv.setTextSize(13f); tv.setPadding(0, (int)(15*density), 0, (int)(5*density));
        return tv;
    }
    
    private EditText createInput(String hint, String text) {
        EditText et = new EditText(getContext()); et.setText(text); et.setTextColor(Color.WHITE); et.setHint(hint);
        et.setHintTextColor(Color.DKGRAY); et.setTextSize(14f);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#252526")); bg.setStroke(1, Color.GRAY); et.setBackground(bg);
        et.setPadding((int)(10*density), (int)(10*density), (int)(10*density), (int)(10*density));
        return et;
    }

    // ==========================================
    // 现代化文件选择器交互核心 (对接系统 SAF)
    // ==========================================
    private void launchSystemFilePicker(String mimeType) {
        if (getContext() instanceof Activity) {
            DesktopFileActionFragment fragment = new DesktopFileActionFragment();
            Bundle args = new Bundle(); args.putString("mime_type", mimeType);
            fragment.setArguments(args);
            ((Activity) getContext()).getFragmentManager().beginTransaction().add(fragment, "dt_file_action").commitAllowingStateLoss();
        }
    }

    public void onFilePickedSafely(Uri uri) {
        try { mContext.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception e) {}
        
        // 若为字体文件，执行安全沙盒拷贝提取绝对路径供 Typeface 使用
        if (currentPickerTarget == 3) {
            try {
                InputStream is = mContext.getContentResolver().openInputStream(uri);
                File fontDir = new File(mContext.getFilesDir(), "ikemen_fonts");
                if (!fontDir.exists()) fontDir.mkdirs();
                File destFile = new File(fontDir, "custom_font_" + System.currentTimeMillis() + ".ttf");
                FileOutputStream fos = new FileOutputStream(destFile);
                byte[] buffer = new byte[8192]; int read;
                while ((read = is.read(buffer)) != -1) fos.write(buffer, 0, read);
                fos.flush(); fos.close(); is.close();
                if (targetInputRef != null) targetInputRef.setText(destFile.getAbsolutePath());
                Toast.makeText(mContext, "字体已安全导入沙盒！", Toast.LENGTH_SHORT).show();
            } catch (Exception e) { Toast.makeText(mContext, "字体导入失败", Toast.LENGTH_SHORT).show(); }
        } else {
            if (targetInputRef != null) targetInputRef.setText(uri.toString());
        }
        currentPickerTarget = 0; targetInputRef = null;
    }

    public static class DesktopFileActionFragment extends android.app.Fragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            String mime = getArguments() != null ? getArguments().getString("mime_type", "*/*") : "*/*";
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(mime);
            startActivityForResult(intent, 2026); // 使用安全码
        }
        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            if (requestCode == 2026 && resultCode == Activity.RESULT_OK && data != null) {
                if (DesktopSystemView.instance != null) DesktopSystemView.instance.onFilePickedSafely(data.getData());
            }
            getFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
        }
    }

    @Override
    public void onBackPressed() { } 
}
