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
 * Ikemen GO 真·PC桌面系统引擎 (媒体记忆 / 全局UI覆盖 / 纯正系统文件选择器)
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

    // === 媒体引擎高阶参数 ===
    public int mediaVolume = 50; 
    public int mediaScaleMode = 1; // 0=拉伸, 1=裁切, 2=居中
    
    // 【记忆修复】视频播放进度断点记忆
    public static int savedVideoPositionDesk = 0;
    public static int savedVideoPositionWin = 0;
    
    // === 字体定制引擎 ===
    public String fontPath = "";
    public Typeface customFont = null;
    public int fontColor = Color.WHITE;
    public float fontSize = 12f;
    public boolean fontShadowEnabled = true;
    public int fontShadowColor = Color.BLACK;

    // 文件选择器中继器 (不再使用路径输入框，直接用标签显示状态)
    public int currentPickerTarget = 0; 
    public TextView targetLabelRef = null;

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

        LinearLayout taskbar = new LinearLayout(getContext());
        taskbar.setOrientation(LinearLayout.HORIZONTAL);
        taskbar.setGravity(Gravity.CENTER_VERTICAL);
        taskbar.setPadding((int)(15*density), 0, (int)(15*density), 0);
        taskbar.setBackgroundColor(Color.parseColor("#E6111111")); 
        FrameLayout.LayoutParams taskbarParams = new FrameLayout.LayoutParams(-1, (int)(50*density));
        taskbarParams.gravity = Gravity.BOTTOM;
        rootLayer.addView(taskbar, taskbarParams);

        // “进入游戏”按钮固定在左侧铁壁
        final LinearLayout startBtn = new LinearLayout(getContext());
        startBtn.setOrientation(LinearLayout.HORIZONTAL); startBtn.setGravity(Gravity.CENTER);
        startBtn.setPadding((int)(15*density), (int)(8*density), (int)(15*density), (int)(8*density));
        
        TextView btnIcon = new TextView(getContext()); btnIcon.setText("⊞"); btnIcon.setTextColor(Color.parseColor("#00A4EF")); btnIcon.setTextSize(20f);
        TextView btnText = new TextView(getContext()); btnText.setText(" 进入游戏"); 
        applyGlobalFontSettings(btnText, 1.2f, true); 
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

        // 右侧自由拖动排序的任务栏应用区
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
    // 媒体解析与记忆引擎
    // ==========================================
    private View createMediaBackground(String uriString, int alpha, final boolean isDesktopBg) {
        if (uriString == null || uriString.trim().isEmpty()) return null;
        Uri uri; String mimeType = null;
        
        if (uriString.startsWith("content://")) {
            uri = Uri.parse(uriString);
            try { mimeType = mContext.getContentResolver().getType(uri); } catch(Exception e){}
        } else {
            File f = new File(uriString); if (!f.exists()) return null;
            uri = Uri.parse("file://" + uriString);
        }
        
        String p = uriString.toLowerCase();
        boolean isVideo = (mimeType != null && mimeType.startsWith("video/")) || p.endsWith(".mp4") || p.endsWith(".avi") || p.endsWith(".mkv") || p.endsWith(".webm");
        boolean isGif = (mimeType != null && mimeType.equals("image/gif")) || p.endsWith(".gif");

        FrameLayout mediaContainer = new FrameLayout(mContext);
        mediaContainer.setAlpha(alpha / 255f);

        if (isVideo) {
            final TextureView tv = new TextureView(mContext);
            mediaContainer.addView(tv, new FrameLayout.LayoutParams(-1, -1));
            tv.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                private MediaPlayer mp;
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    try {
                        mp = new MediaPlayer();
                        mp.setDataSource(mContext, uri);
                        mp.setSurface(new Surface(surface));
                        mp.setLooping(true);
                        
                        // 独立音量控制，绝不打断后台游戏BGM
                        float vol = mediaVolume / 100f;
                        mp.setVolume(vol, vol);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build());
                        }
                        
                        mp.prepareAsync();
                        mp.setOnPreparedListener(m -> {
                            int vw = m.getVideoWidth(); int vh = m.getVideoHeight();
                            int pw = mediaContainer.getWidth(); int ph = mediaContainer.getHeight();
                            if (pw > 0 && ph > 0 && vw > 0 && vh > 0) {
                                FrameLayout.LayoutParams lp;
                                if (mediaScaleMode == 2) { 
                                    // 居中 (Center)：保持原大小放在正中间
                                    lp = new FrameLayout.LayoutParams(vw, vh, Gravity.CENTER);
                                } else if (mediaScaleMode == 1) { 
                                    // 裁切 (Crop)：等比放大填满全屏，多余部分裁掉
                                    float scale = Math.max((float)pw/vw, (float)ph/vh);
                                    lp = new FrameLayout.LayoutParams((int)(vw*scale), (int)(vh*scale), Gravity.CENTER);
                                } else { 
                                    // 拉伸 (Stretch)：强行改变比例塞满窗口
                                    lp = new FrameLayout.LayoutParams(-1, -1);
                                }
                                tv.setLayoutParams(lp);
                            }
                            // 【记忆恢复】判断断点并精确续播
                            if (isDesktopBg && savedVideoPositionDesk > 0) m.seekTo(savedVideoPositionDesk);
                            else if (!isDesktopBg && savedVideoPositionWin > 0) m.seekTo(savedVideoPositionWin);
                            m.start();
                        });
                    } catch (Exception e) {}
                }
                @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}
                @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    if (mp != null) { 
                        // 【记忆记录】销毁时瞬间记录上一毫秒的进度
                        if (isDesktopBg) savedVideoPositionDesk = mp.getCurrentPosition();
                        else savedVideoPositionWin = mp.getCurrentPosition();
                        mp.release(); mp = null; 
                    } 
                    return true;
                }
                @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
            });
            return mediaContainer;
        } else if (isGif) {
            WebView wv = new WebView(mContext);
            wv.getSettings().setAllowFileAccess(true); wv.getSettings().setAllowContentAccess(true);
            wv.loadDataWithBaseURL("", "<html style='margin:0;padding:0;'><body style='margin:0;padding:0;background-color:transparent;display:flex;justify-content:center;align-items:center;height:100vh;'><img src='" + uri.toString() + "' style='width:100%;height:100%;object-fit:" + (mediaScaleMode==0?"fill":(mediaScaleMode==1?"cover":"none")) + ";' /></body></html>", "text/html", "utf-8", null);
            wv.setBackgroundColor(Color.TRANSPARENT); wv.setAlpha(alpha / 255f);
            return wv;
        } else {
            ImageView iv = new ImageView(mContext); iv.setImageURI(uri); 
            if (mediaScaleMode == 2) iv.setScaleType(ImageView.ScaleType.CENTER);
            else if (mediaScaleMode == 1) iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            else iv.setScaleType(ImageView.ScaleType.FIT_XY);
            iv.setAlpha(alpha / 255f); return iv;
        }
    }

    private void refreshDesktopBackground() {
        desktopBgLayer.removeAllViews();
        View media = createMediaBackground(customDesktopBg, bgAlpha, true); // true = 桌面
        if (media != null) {
            desktopBgLayer.addView(media, new FrameLayout.LayoutParams(-1, -1));
        } else {
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.parseColor("#1B1B1B"), Color.parseColor("#2D2D30")});
            bg.setAlpha(bgAlpha); desktopBgLayer.setBackground(bg);
        }
    }

    // ==========================================
    // 全局字体与颜色覆盖引擎
    // ==========================================
    private void applyGlobalFontSettings(TextView tv, float sizeMultiplier, boolean isBold) {
        if (customFont != null) {
            tv.setTypeface(customFont, isBold ? Typeface.BOLD : Typeface.NORMAL);
        } else {
            tv.setTypeface(null, isBold ? Typeface.BOLD : Typeface.NORMAL);
        }
        tv.setTextColor(fontColor);
        tv.setTextSize(fontSize * sizeMultiplier);
        if (fontShadowEnabled) {
            tv.setShadowLayer(4f, 2f, 2f, fontShadowColor);
        } else {
            tv.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT); 
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
        // 网格比功能范围大2个像素点，完美居中对齐
        float iconSize = actualGrid - 2f * density; 
        
        float savedX = prefs.getFloat("icon_x_" + id, actualGrid * 0.2f);
        float savedY = prefs.getFloat("icon_y_" + id, actualGrid * 0.2f);

        TextView iconView = new TextView(getContext()); iconView.setText(iconStr); iconView.setTextSize(26f); iconView.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#44000000"));
        if (iconShape == 1) bg.setCornerRadius(6f*density); else if (iconShape == 2) bg.setCornerRadius(50f*density); else bg.setColor(Color.TRANSPARENT);
        iconView.setBackground(bg);
        
        iconLayout.addView(iconView, new LinearLayout.LayoutParams((int)(iconSize*0.6f), (int)(iconSize*0.6f)));
        TextView nameView = new TextView(getContext()); nameView.setText(name); 
        applyGlobalFontSettings(nameView, 1.0f, false); // 图标名字应用全局字体
        nameView.setSingleLine(true);
        iconLayout.addView(nameView, new LinearLayout.LayoutParams(-2, -2));

        iconLayout.setLayoutParams(new FrameLayout.LayoutParams((int)iconSize, (int)iconSize));
        iconLayout.setX(savedX); iconLayout.setY(savedY);
        desktopIconsLayer.addView(iconLayout);

        iconLayout.setOnTouchListener(new View.OnTouchListener() {
            private float startRawX, startRawY;
            private float offsetX, offsetY;
            private boolean isDragging = false;
            private long lastClickTime = 0;
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    startRawX = event.getRawX(); startRawY = event.getRawY();
                    offsetX = view.getX() - mouseX; offsetY = view.getY() - mouseY; isDragging = false;
                    view.setBackgroundColor(Color.parseColor("#44FFFFFF"));
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    // 防手抖优化：增加滑动死区判定
                    if (!isDragging && (Math.abs(event.getRawX() - startRawX) > 20 * density || Math.abs(event.getRawY() - startRawY) > 20 * density)) {
                        isDragging = true; view.bringToFront();
                    }
                    if (isDragging) { view.setX(mouseX + offsetX); view.setY(mouseY + offsetY); }
                } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    view.setBackgroundColor(Color.TRANSPARENT);
                    if (isDragging) {
                        float finalX = Math.round(view.getX() / actualGrid) * actualGrid + (actualGrid - iconSize)/2f;
                        float finalY = Math.round(view.getY() / actualGrid) * actualGrid + (actualGrid - iconSize)/2f;
                        view.setX(finalX); view.setY(finalY);
                        prefs.edit().putFloat("icon_x_" + id, finalX).putFloat("icon_y_" + id, finalY).apply();
                    } else {
                        long clickTime = System.currentTimeMillis();
                        // 双击容错判定延长到 600ms，绝不漏判
                        if (clickTime - lastClickTime < 600) { handleIconDoubleTap(id); lastClickTime = 0; }
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
    // 窗口管理系统 (含现代符号与防穿模拖动)
    // ==========================================
    private void openAppWindow(String windowTitle, View contentView) {
        View existingWin = windowsLayer.findViewWithTag(windowTitle);
        if (existingWin != null) { existingWin.setVisibility(View.VISIBLE); existingWin.bringToFront(); return; }

        final FrameLayout windowFrame = new FrameLayout(getContext());
        windowFrame.setTag(windowTitle); windowFrame.setClickable(true);
        
        View winMediaBg = createMediaBackground(customWindowBg, 255, false); // false = 窗口
        if (winMediaBg != null) windowFrame.addView(winMediaBg, new FrameLayout.LayoutParams(-1, -1));
        else {
            GradientDrawable winBg = new GradientDrawable(); winBg.setColor(Color.parseColor("#FA1E1E1E")); 
            winBg.setStroke(2, Color.parseColor("#3F3F46")); windowFrame.setBackground(winBg);
        }
        windowFrame.setElevation(25f * density);

        LinearLayout winContainer = new LinearLayout(getContext());
        winContainer.setOrientation(LinearLayout.VERTICAL);
        windowFrame.addView(winContainer, new FrameLayout.LayoutParams(-1, -1));

        final LinearLayout titleBar = new LinearLayout(getContext());
        titleBar.setOrientation(LinearLayout.HORIZONTAL); titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setBackgroundColor(Color.parseColor("#F22D2D30")); 
        
        TextView title = new TextView(getContext()); title.setText("  " + windowTitle);
        applyGlobalFontSettings(title, 1.2f, true); 
        titleBar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout controls = new LinearLayout(getContext()); controls.setOrientation(LinearLayout.HORIZONTAL);
        
        TextView btnMin = new TextView(getContext()); btnMin.setText(" ─ "); btnMin.setPadding((int)(15*density), (int)(5*density), (int)(15*density), (int)(8*density));
        applyGlobalFontSettings(btnMin, 1.0f, true); btnMin.setOnClickListener(v -> windowFrame.setVisibility(View.GONE)); controls.addView(btnMin);

        TextView btnClose = new TextView(getContext()); btnClose.setText(" ✕ "); btnClose.setPadding((int)(15*density), (int)(5*density), (int)(15*density), (int)(5*density));
        applyGlobalFontSettings(btnClose, 1.0f, true);
        btnClose.setOnTouchListener((v, e) -> {
            if(e.getAction()==MotionEvent.ACTION_DOWN) v.setBackgroundColor(Color.parseColor("#E81123")); 
            else if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL) v.setBackgroundColor(Color.TRANSPARENT);
            return false;
        });
        controls.addView(btnClose); titleBar.addView(controls);

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

        // 任务栏按钮拖动：因为放进了 HorizontalScrollView 内部，天然拥有左边界约束，绝不穿模覆盖“进入游戏”
        final LinearLayout taskBtn = new LinearLayout(getContext());
        taskBtn.setOrientation(LinearLayout.HORIZONTAL); taskBtn.setGravity(Gravity.CENTER);
        taskBtn.setPadding((int)(10*density), (int)(5*density), (int)(10*density), (int)(5*density));
        GradientDrawable tbBg = new GradientDrawable(); tbBg.setColor(Color.parseColor("#22FFFFFF")); 
        taskBtn.setBackground(tbBg);
        LinearLayout.LayoutParams tbParams = new LinearLayout.LayoutParams(-2, -1); tbParams.setMargins(0,0,(int)(5*density),0);
        
        TextView tbText = new TextView(getContext()); 
        tbText.setText("▤ " + windowTitle.split(" ")[0]); // 修复不显示的乱码符号，改用现代符号
        applyGlobalFontSettings(tbText, 1.1f, false); 
        taskBtn.addView(tbText);
        
        taskBtn.setOnTouchListener(new View.OnTouchListener() {
            float startX, originalX; boolean isDragging = false;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX(); originalX = v.getX(); isDragging = false;
                        v.setBackgroundColor(Color.parseColor("#44FFFFFF")); v.bringToFront(); return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - startX; if (Math.abs(dx) > 10) isDragging = true;
                        if (isDragging) v.setX(originalX + dx); return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setBackground(tbBg);
                        if (isDragging) {
                            int newIndex = -1; float currentCenter = v.getX() + v.getWidth()/2f;
                            for (int i=0; i<taskbarAppsLayout.getChildCount(); i++) {
                                View child = taskbarAppsLayout.getChildAt(i);
                                if (child != v && currentCenter < child.getX() + child.getWidth()/2f) { newIndex = i; break; }
                            }
                            taskbarAppsLayout.removeView(v);
                            if (newIndex == -1) taskbarAppsLayout.addView(v, tbParams); else taskbarAppsLayout.addView(v, newIndex, tbParams);
                            for (int i=0; i<taskbarAppsLayout.getChildCount(); i++) taskbarAppsLayout.getChildAt(i).setTranslationX(0); // 重置视觉偏移
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
    // 现代化设置面板 UI 生成器
    // ==========================================
    private void loadDesktopSettings() {
        bgAlpha = prefs.getInt("dt_bgAlpha", 180);
        gridSizeBase = prefs.getInt("dt_gridSize", 100);
        showGrid = prefs.getBoolean("dt_showGrid", false);
        customDesktopBg = prefs.getString("dt_customDeskBg", "");
        customWindowBg = prefs.getString("dt_customWinBg", "");
        mediaVolume = prefs.getInt("dt_mediaVol", 50);
        mediaScaleMode = prefs.getInt("dt_mediaScale", 1);
        
        fontPath = prefs.getString("dt_fontPath", "");
        if (!fontPath.isEmpty()) { try { customFont = Typeface.createFromFile(fontPath); } catch (Exception e) { customFont = null; } } 
        else { customFont = null; }
        
        fontColor = prefs.getInt("dt_fontColor", Color.WHITE);
        fontSize = prefs.getFloat("dt_fontSize", 12f);
        fontShadowEnabled = prefs.getBoolean("dt_fontShadow", true);
        fontShadowColor = prefs.getInt("dt_fontShadowC", Color.BLACK);
    }

    private View buildSettingsContent() {
        ScrollView scroll = new ScrollView(getContext());
        LinearLayout layout = new LinearLayout(getContext()); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding((int)(20*density), (int)(10*density), (int)(20*density), (int)(20*density));

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

        Button gridToggle = createButton(showGrid ? "✔️ 网格辅助线：开启" : "❌ 网格辅助线：关闭", "#333333");
        gridToggle.setOnClickListener(v -> { showGrid = !showGrid; gridToggle.setText(showGrid ? "✔️ 网格辅助线：开启" : "❌ 网格辅助线：关闭"); rootLayer.invalidate(); });
        layout.addView(gridToggle);

        layout.addView(createTitle("🅰️ 字体定制引擎 (全局覆盖UI)"));
        final TextView fontLabel = createSubTitle("当前字体状态: " + (fontPath.isEmpty()?"未选择 (默认)":"已加载自定义资源")); layout.addView(fontLabel);
        Button pickFont = createButton("调用系统选择器获取字体文件", "#444444");
        pickFont.setOnClickListener(v -> { currentPickerTarget = 3; targetLabelRef = fontLabel; launchSystemFilePicker("*/*"); });
        layout.addView(pickFont);

        layout.addView(createSubTitle("字体字号大小调节:"));
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

        Button shadowToggle = createButton(fontShadowEnabled ? "✔️ 字体投影：开启" : "❌ 字体投影：关闭", "#333333");
        shadowToggle.setOnClickListener(v -> { fontShadowEnabled = !fontShadowEnabled; shadowToggle.setText(fontShadowEnabled ? "✔️ 字体投影：开启" : "❌ 字体投影：关闭"); });
        layout.addView(shadowToggle);

        layout.addView(createSubTitle("投影颜色代码 (Hex):"));
        final EditText shadowColorInput = createInput("如: #000000", String.format("#%06X", (0xFFFFFF & fontShadowColor))); layout.addView(shadowColorInput);
        shadowColorInput.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) { try{ fontShadowColor = Color.parseColor(s.toString()); }catch(Exception e){} }
            public void beforeTextChanged(CharSequence s, int x, int y, int z) {} public void onTextChanged(CharSequence s, int x, int y, int z) {}
        });

        // 多媒体矩阵设置区
        layout.addView(createTitle("🎬 动态媒体矩阵 (支持任何格式图片和GIF和各种视频)"));
        
        layout.addView(createSubTitle("多媒体渲染模式:"));
        Spinner scaleSpinner = new Spinner(getContext());
        ArrayAdapter<String> scaleAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, new String[]{"📏 强制拉伸填满 (Stretch)", "✂️ 居中裁切填满 (Crop)", "🎯 保持原比例居中 (Center)"});
        scaleSpinner.setAdapter(scaleAdapter); scaleSpinner.setSelection(mediaScaleMode);
        layout.addView(scaleSpinner);

        layout.addView(createSubTitle("视频独立音量控制 (绝不影响游戏BGM):"));
        SeekBar volBar = new SeekBar(getContext()); volBar.setMax(100); volBar.setProgress(mediaVolume);
        volBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean b) { mediaVolume = p; }
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
        });
        layout.addView(volBar);

        final TextView deskBgLabel = createSubTitle("桌面壁纸状态: " + (customDesktopBg.isEmpty()?"未配置":"已安全加载底层资源")); layout.addView(deskBgLabel);
        Button pickDesk = createButton("调用系统底层接口获取资源", "#444444"); 
        pickDesk.setOnClickListener(v -> { currentPickerTarget = 1; targetLabelRef = deskBgLabel; launchSystemFilePicker("*/*"); });
        layout.addView(pickDesk);

        final TextView winBgLabel = createSubTitle("窗口壁纸状态: " + (customWindowBg.isEmpty()?"未配置":"已安全加载底层资源")); layout.addView(winBgLabel);
        Button pickWin = createButton("调用系统底层接口获取资源", "#444444"); 
        pickWin.setOnClickListener(v -> { currentPickerTarget = 2; targetLabelRef = winBgLabel; launchSystemFilePicker("*/*"); });
        layout.addView(pickWin);

        Button saveBtn = createButton("💾 保存设置并重载引擎", "#0078D7");
        LinearLayout.LayoutParams btnP = new LinearLayout.LayoutParams(-1, -2); btnP.setMargins(0, (int)(30*density), 0, 0); saveBtn.setLayoutParams(btnP);
        saveBtn.setOnClickListener(v -> {
            fontPath = fontPath.trim(); mediaScaleMode = scaleSpinner.getSelectedItemPosition();
            prefs.edit().putInt("dt_bgAlpha", bgAlpha).putInt("dt_gridSize", gridSizeBase).putBoolean("dt_showGrid", showGrid)
                 .putString("dt_customDeskBg", customDesktopBg).putString("dt_customWinBg", customWindowBg)
                 .putInt("dt_mediaVol", mediaVolume).putInt("dt_mediaScale", mediaScaleMode)
                 .putString("dt_fontPath", fontPath).putInt("dt_fontColor", fontColor).putFloat("dt_fontSize", fontSize)
                 .putBoolean("dt_fontShadow", fontShadowEnabled).putInt("dt_fontShadowC", fontShadowColor).apply();
            
            // 重置断点记忆，因为资源或模式改变了
            savedVideoPositionDesk = 0; savedVideoPositionWin = 0;
            
            loadDesktopSettings(); refreshDesktopBackground(); setupDesktopIcons();
            Toast.makeText(getContext(), "✅ 系统设置已全面刷新！", Toast.LENGTH_SHORT).show();
            hide(); 
        });
        layout.addView(saveBtn);
        
        // 【添加找回的恢复默认按钮】
        Button resetBtn = createButton("🔄 恢复初始默认状态", "#E81123");
        LinearLayout.LayoutParams rBtnP = new LinearLayout.LayoutParams(-1, -2); rBtnP.setMargins(0, (int)(15*density), 0, (int)(20*density)); resetBtn.setLayoutParams(rBtnP);
        resetBtn.setOnClickListener(v -> {
            prefs.edit().clear().apply(); loadDesktopSettings(); refreshDesktopBackground(); setupDesktopIcons();
            savedVideoPositionDesk = 0; savedVideoPositionWin = 0;
            Toast.makeText(getContext(), "已清空所有桌面定制参数！", Toast.LENGTH_SHORT).show(); hide();
        });
        layout.addView(resetBtn);

        scroll.addView(layout); return scroll;
    }

    private TextView createTitle(String text) {
        TextView tv = new TextView(getContext()); tv.setText(text);
        applyGlobalFontSettings(tv, 1.3f, true); 
        tv.setPadding(0, (int)(25*density), 0, (int)(10*density)); return tv;
    }
    
    private TextView createSubTitle(String text) {
        TextView tv = new TextView(getContext()); tv.setText(text);
        applyGlobalFontSettings(tv, 1.1f, false);
        tv.setPadding(0, (int)(15*density), 0, (int)(5*density)); return tv;
    }

    private EditText createInput(String hint, String text) {
        EditText et = new EditText(getContext()); et.setText(text); 
        applyGlobalFontSettings(et, 1.0f, false);
        et.setHint(hint); et.setHintTextColor(Color.DKGRAY);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#252526")); bg.setStroke(1, Color.GRAY); et.setBackground(bg);
        et.setPadding((int)(10*density), (int)(10*density), (int)(10*density), (int)(10*density)); return et;
    }
    
    private Button createButton(String text, String colorHex) {
        Button btn = new Button(getContext()); btn.setText(text); 
        btn.setBackgroundColor(Color.parseColor(colorHex));
        applyGlobalFontSettings(btn, 1.0f, false);
        return btn;
    }

    // ==========================================
    // 纯正文件选取引擎 (直接使用系统底层 URI，无视安卓版本限制)
    // ==========================================
    private void launchSystemFilePicker(String mimeType) {
        if (getContext() instanceof Activity) {
            DesktopFileActionFragment fragment = new DesktopFileActionFragment();
            Bundle args = new Bundle(); args.putString("mime_type", mimeType); // 不限制任何格式
            fragment.setArguments(args);
            ((Activity) getContext()).getFragmentManager().beginTransaction().add(fragment, "dt_file_action").commitAllowingStateLoss();
        }
    }

    public void onFilePickedSafely(Uri uri) {
        try { mContext.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception e) {}
        
        // 字体特例：底层 Typeface 无法直接解析 content:// 协议，故在选定瞬间进行无感知的沙盒拷贝提取
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
                fontPath = destFile.getAbsolutePath();
                if (targetLabelRef != null) targetLabelRef.setText("当前字体状态: 已成功加载自定义资源");
                Toast.makeText(mContext, "字体已安全导入沙盒引擎！", Toast.LENGTH_SHORT).show();
            } catch (Exception e) { Toast.makeText(mContext, "字体解析失败", Toast.LENGTH_SHORT).show(); }
        } else {
            // 图片和视频：安全存储 URI，由 MediaPlayer 和 ImageView 底层直接读流渲染
            String uriStr = uri.toString();
            if (currentPickerTarget == 1) {
                customDesktopBg = uriStr;
                if (targetLabelRef != null) targetLabelRef.setText("桌面壁纸状态: 已安全绑定该多媒体资源");
            } else if (currentPickerTarget == 2) {
                customWindowBg = uriStr;
                if (targetLabelRef != null) targetLabelRef.setText("窗口壁纸状态: 已安全绑定该多媒体资源");
            }
        }
        currentPickerTarget = 0; targetLabelRef = null;
    }

    public static class DesktopFileActionFragment extends android.app.Fragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            String mime = getArguments() != null ? getArguments().getString("mime_type", "*/*") : "*/*";
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(mime); // 这里传入 */*，不对玩家选什么文件做任何限制
            startActivityForResult(intent, 2026); 
        }
        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            if (requestCode == 2026 && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                if (DesktopSystemView.instance != null) DesktopSystemView.instance.onFilePickedSafely(data.getData());
            }
            getFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
        }
    }

    @Override
    public void onBackPressed() { } 
}
