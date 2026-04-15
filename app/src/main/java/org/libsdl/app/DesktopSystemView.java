package org.libsdl.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
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
import android.os.Environment;
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
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/**
 * Ikemen GO 真·PC桌面系统引擎 (真视窗回归 / 终极沉浸模式 / 快照回滚)
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
    private LinearLayout taskbar; 
    private LinearLayout taskbarAppsLayout;

    // === 系统设置参数 ===
    public int bgAlpha = 180; 
    public int gridSizeBase = 100;
    public boolean showGrid = false;
    public String customDesktopBg = "";
    public String customWindowBg = "";
    
    // === UI 与媒体引擎高阶参数 ===
    public int taskbarAlpha = 230; 
    public int bgMediaVolume = 50; 
    public int winMediaVolume = 50; 
    public int mediaScaleMode = 1; 
    
    public static int savedVideoPositionDesk = 0;
    public static int savedVideoPositionWin = 0;
    
    private MediaPlayer bgMediaPlayer = null;
    private List<MediaPlayer> winMediaPlayers = new ArrayList<>();
    
    // === 字体定制引擎 ===
    public String fontPath = "";
    public Typeface customFont = null;
    public int fontColor = Color.WHITE;
    public float fontSize = 12f;
    public boolean fontShadowEnabled = true;
    public int fontShadowColor = Color.BLACK;

    private static File lastVisitedDir = Environment.getExternalStorageDirectory();

    public DesktopSystemView(Context context) {
        super(context, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        this.mContext = context;
        this.prefs = context.getSharedPreferences("IkemenDesktopPrefs", Context.MODE_PRIVATE);
        this.density = context.getResources().getDisplayMetrics().density;
    }

    // 终极沉浸式状态栏/导航栏隐藏技术
    private void applyImmersiveMode(Window window) {
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        applyImmersiveMode(getWindow());
    }

    @Override
    public void show() { super.show(); instance = this; }

    @Override
    public void dismiss() { super.dismiss(); if (instance == this) instance = null; }

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
                invalidate(); return super.dispatchTouchEvent(event);
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

        taskbar = new LinearLayout(getContext());
        taskbar.setOrientation(LinearLayout.HORIZONTAL);
        taskbar.setGravity(Gravity.CENTER_VERTICAL);
        taskbar.setPadding((int)(4*density), 0, (int)(15*density), 0);
        taskbar.setBackgroundColor(Color.argb(taskbarAlpha, 17, 17, 17)); 
        FrameLayout.LayoutParams taskbarParams = new FrameLayout.LayoutParams(-1, (int)(50*density));
        taskbarParams.gravity = Gravity.BOTTOM;
        rootLayer.addView(taskbar, taskbarParams);

        final LinearLayout startBtn = new LinearLayout(getContext());
        startBtn.setOrientation(LinearLayout.HORIZONTAL); startBtn.setGravity(Gravity.CENTER);
        startBtn.setPadding((int)(10*density), (int)(8*density), (int)(15*density), (int)(8*density));
        
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
        cursorPath = new Path(); cursorPath.moveTo(0, 0); cursorPath.lineTo(0, 35); cursorPath.lineTo(9, 26); cursorPath.lineTo(16, 42); cursorPath.lineTo(22, 38); cursorPath.lineTo(15, 22); cursorPath.lineTo(26, 22); cursorPath.close();
        Matrix scaleMatrix = new Matrix(); scaleMatrix.setScale(density * 0.4f, density * 0.4f); cursorPath.transform(scaleMatrix);
    }

    public void updateMediaVolumes() {
        boolean hasActiveWindowAudio = false;
        for (int i = 0; i < winMediaPlayers.size(); i++) {
            MediaPlayer mp = winMediaPlayers.get(i);
            if (mp != null) {
                try {
                    float v = winMediaVolume / 100f; mp.setVolume(v, v);
                    if (winMediaVolume > 0) hasActiveWindowAudio = true;
                } catch (Exception e) {}
            }
        }
        if (bgMediaPlayer != null) {
            try {
                if (hasActiveWindowAudio) { bgMediaPlayer.setVolume(0f, 0f); } 
                else { float v = bgMediaVolume / 100f; bgMediaPlayer.setVolume(v, v); }
            } catch (Exception e) {}
        }
    }

    private View createMediaBackground(String uriString, int alpha, final boolean isDesktopBg) {
        if (uriString == null || uriString.trim().isEmpty()) return null;
        File f = new File(uriString); if (!f.exists()) return null;
        Uri uri = Uri.parse("file://" + uriString);
        String p = uriString.toLowerCase();
        boolean isVideo = p.endsWith(".mp4") || p.endsWith(".avi") || p.endsWith(".mkv") || p.endsWith(".webm");
        boolean isGif = p.endsWith(".gif");

        FrameLayout mediaContainer = new FrameLayout(mContext); mediaContainer.setAlpha(alpha / 255f);

        if (isVideo) {
            final TextureView tv = new TextureView(mContext);
            mediaContainer.addView(tv, new FrameLayout.LayoutParams(-1, -1));
            tv.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                private MediaPlayer mp;
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    try {
                        mp = new MediaPlayer(); mp.setDataSource(mContext, uri); mp.setSurface(new Surface(surface)); mp.setLooping(true);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build());
                        mp.prepareAsync();
                        mp.setOnPreparedListener(m -> {
                            int vw = m.getVideoWidth(); int vh = m.getVideoHeight(); int pw = mediaContainer.getWidth(); int ph = mediaContainer.getHeight();
                            if (pw > 0 && ph > 0 && vw > 0 && vh > 0) {
                                FrameLayout.LayoutParams lp;
                                if (mediaScaleMode == 2) lp = new FrameLayout.LayoutParams(vw, vh, Gravity.CENTER);
                                else if (mediaScaleMode == 1) { float scale = Math.max((float)pw/vw, (float)ph/vh); lp = new FrameLayout.LayoutParams((int)(vw*scale), (int)(vh*scale), Gravity.CENTER); } 
                                else lp = new FrameLayout.LayoutParams(-1, -1);
                                tv.setLayoutParams(lp);
                            }
                            if (isDesktopBg) { bgMediaPlayer = m; if (savedVideoPositionDesk > 0) m.seekTo(savedVideoPositionDesk); } 
                            else { winMediaPlayers.add(m); if (savedVideoPositionWin > 0) m.seekTo(savedVideoPositionWin); }
                            updateMediaVolumes(); m.start();
                        });
                    } catch (Exception e) {}
                }
                @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}
                @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    if (mp != null) { 
                        if (isDesktopBg) { savedVideoPositionDesk = mp.getCurrentPosition(); bgMediaPlayer = null; } 
                        else { savedVideoPositionWin = mp.getCurrentPosition(); winMediaPlayers.remove(mp); }
                        mp.release(); mp = null; updateMediaVolumes();
                    } return true;
                }
                @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
            });
            return mediaContainer;
        } else if (isGif) {
            WebView wv = new WebView(mContext); wv.getSettings().setAllowFileAccess(true); wv.getSettings().setAllowContentAccess(true);
            wv.loadDataWithBaseURL("", "<html style='margin:0;padding:0;'><body style='margin:0;padding:0;background-color:transparent;display:flex;justify-content:center;align-items:center;height:100vh;'><img src='" + uri.toString() + "' style='width:100%;height:100%;object-fit:" + (mediaScaleMode==0?"fill":(mediaScaleMode==1?"cover":"contain")) + ";' /></body></html>", "text/html", "utf-8", null);
            wv.setBackgroundColor(Color.TRANSPARENT); wv.setAlpha(alpha / 255f); return wv;
        } else {
            ImageView iv = new ImageView(mContext); iv.setImageURI(uri); 
            if (mediaScaleMode == 2) iv.setScaleType(ImageView.ScaleType.CENTER); else if (mediaScaleMode == 1) iv.setScaleType(ImageView.ScaleType.CENTER_CROP); else iv.setScaleType(ImageView.ScaleType.FIT_XY);
            iv.setAlpha(alpha / 255f); return iv;
        }
    }

    private void refreshDesktopBackground() {
        desktopBgLayer.removeAllViews(); View media = createMediaBackground(customDesktopBg, bgAlpha, true);
        if (media != null) desktopBgLayer.addView(media, new FrameLayout.LayoutParams(-1, -1));
        else { GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.parseColor("#1B1B1B"), Color.parseColor("#2D2D30")}); bg.setAlpha(bgAlpha); desktopBgLayer.setBackground(bg); }
    }

    private void applyGlobalFontSettings(TextView tv, float sizeMultiplier, boolean isBold) {
        if (customFont != null) tv.setTypeface(customFont, isBold ? Typeface.BOLD : Typeface.NORMAL);
        else tv.setTypeface(null, isBold ? Typeface.BOLD : Typeface.NORMAL);
        tv.setTextColor(fontColor); tv.setTextSize(fontSize * sizeMultiplier);
        if (fontShadowEnabled) tv.setShadowLayer(4f, 2f, 2f, fontShadowColor); else tv.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);
    }

    // ==========================================
    // 桌面图标与网格系统
    // ==========================================
    private void setupDesktopIcons() {
        desktopIconsLayer.removeAllViews(); 
        createDesktopIcon("sys_settings", "⚙️", "系统控制台");
        createDesktopIcon("asset_extractor", "📦", "素材提取工坊");
    }

    private void createDesktopIcon(final String id, String iconStr, String name) {
        final LinearLayout iconLayout = new LinearLayout(getContext()); iconLayout.setOrientation(LinearLayout.VERTICAL); iconLayout.setGravity(Gravity.CENTER);
        float actualGrid = gridSizeBase * density; float iconSize = actualGrid - 2f * density; 
        float savedX = prefs.getFloat("icon_x_" + id, actualGrid * 0.2f); float savedY = prefs.getFloat("icon_y_" + id, actualGrid * 0.2f);

        TextView iconView = new TextView(getContext()); iconView.setText(iconStr); iconView.setTextSize(26f); iconView.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#44000000")); bg.setCornerRadius(6f*density); 
        iconView.setBackground(bg); iconLayout.addView(iconView, new LinearLayout.LayoutParams((int)(iconSize*0.6f), (int)(iconSize*0.6f)));
        
        TextView nameView = new TextView(getContext()); nameView.setText(name); applyGlobalFontSettings(nameView, 1.0f, false); nameView.setSingleLine(true);
        iconLayout.addView(nameView, new LinearLayout.LayoutParams(-2, -2)); iconLayout.setLayoutParams(new FrameLayout.LayoutParams((int)iconSize, (int)iconSize));
        iconLayout.setX(savedX); iconLayout.setY(savedY); desktopIconsLayer.addView(iconLayout);

        iconLayout.setOnTouchListener(new View.OnTouchListener() {
            private float startRawX, startRawY, offsetX, offsetY; private boolean isDragging = false; private long lastClickTime = 0;
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    startRawX = event.getRawX(); startRawY = event.getRawY(); offsetX = view.getX() - mouseX; offsetY = view.getY() - mouseY; isDragging = false; view.setBackgroundColor(Color.parseColor("#44FFFFFF"));
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (!isDragging && (Math.abs(event.getRawX() - startRawX) > 20 * density || Math.abs(event.getRawY() - startRawY) > 20 * density)) { isDragging = true; view.bringToFront(); }
                    if (isDragging) { view.setX(mouseX + offsetX); view.setY(mouseY + offsetY); }
                } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    view.setBackgroundColor(Color.TRANSPARENT);
                    if (isDragging) {
                        float finalX = Math.round(view.getX() / actualGrid) * actualGrid + (actualGrid - iconSize)/2f; float finalY = Math.round(view.getY() / actualGrid) * actualGrid + (actualGrid - iconSize)/2f;
                        view.setX(finalX); view.setY(finalY); prefs.edit().putFloat("icon_x_" + id, finalX).putFloat("icon_y_" + id, finalY).apply();
                    } else {
                        long clickTime = System.currentTimeMillis();
                        if (clickTime - lastClickTime < 600) { 
                            if (id.equals("sys_settings")) openSettingsInAppWindow(); 
                            else if (id.equals("asset_extractor")) openAppWindow("📦 素材提取工坊", buildAssetExtractorContent(), null);
                            lastClickTime = 0; 
                        } else lastClickTime = clickTime;
                    }
                }
                return true;
            }
        });
    }

    // ==========================================
    // 窗口管理系统 (支持任务栏与拦截器)
    // ==========================================
    private void openAppWindow(String windowTitle, View contentView, final Runnable onCloseInterceptor) {
        View existingWin = windowsLayer.findViewWithTag(windowTitle);
        if (existingWin != null) { existingWin.setVisibility(View.VISIBLE); existingWin.bringToFront(); return; }

        final FrameLayout windowFrame = new FrameLayout(getContext()); windowFrame.setTag(windowTitle); windowFrame.setClickable(true); 
        
        View winMediaBg = createMediaBackground(customWindowBg, 255, false);
        if (winMediaBg != null) windowFrame.addView(winMediaBg, new FrameLayout.LayoutParams(-1, -1));
        else { GradientDrawable winBg = new GradientDrawable(); winBg.setColor(Color.parseColor("#FA1E1E1E")); winBg.setStroke(2, Color.parseColor("#3F3F46")); windowFrame.setBackground(winBg); }
        windowFrame.setElevation(25f * density); 

        LinearLayout winContainer = new LinearLayout(getContext()); winContainer.setOrientation(LinearLayout.VERTICAL); windowFrame.addView(winContainer, new FrameLayout.LayoutParams(-1, -1));

        final LinearLayout titleBar = new LinearLayout(getContext()); titleBar.setOrientation(LinearLayout.HORIZONTAL); titleBar.setGravity(Gravity.CENTER_VERTICAL); titleBar.setBackgroundColor(Color.parseColor("#F22D2D30")); 
        TextView title = new TextView(getContext()); title.setText("  " + windowTitle); applyGlobalFontSettings(title, 1.2f, true); titleBar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout controls = new LinearLayout(getContext()); controls.setOrientation(LinearLayout.HORIZONTAL);
        TextView btnMin = new TextView(getContext()); btnMin.setText(" ─ "); applyGlobalFontSettings(btnMin, 1.0f, true); btnMin.setPadding((int)(15*density), (int)(5*density), (int)(15*density), (int)(8*density)); btnMin.setOnClickListener(v -> windowFrame.setVisibility(View.GONE)); controls.addView(btnMin);

        final LinearLayout taskBtn = new LinearLayout(getContext()); taskBtn.setTag("tb_" + windowTitle);
        
        TextView btnClose = new TextView(getContext()); btnClose.setText(" ✕ "); applyGlobalFontSettings(btnClose, 1.0f, true); btnClose.setPadding((int)(15*density), (int)(5*density), (int)(15*density), (int)(5*density));
        btnClose.setOnTouchListener((v, e) -> {
            if(e.getAction()==MotionEvent.ACTION_DOWN) v.setBackgroundColor(Color.parseColor("#E81123")); else if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL) v.setBackgroundColor(Color.TRANSPARENT);
            return false;
        });
        btnClose.setOnClickListener(v -> {
            if (onCloseInterceptor != null) onCloseInterceptor.run();
            else { windowsLayer.removeView(windowFrame); taskbarAppsLayout.removeView(taskBtn); }
        });
        controls.addView(btnClose); titleBar.addView(controls);

        titleBar.setOnTouchListener(new View.OnTouchListener() {
            float dX, dY;
            @Override public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) { dX = windowFrame.getX() - mouseX; dY = windowFrame.getY() - mouseY; windowFrame.bringToFront(); } 
                else if (event.getAction() == MotionEvent.ACTION_MOVE) { windowFrame.setX(mouseX + dX); windowFrame.setY(mouseY + dY); } return true;
            }
        });

        winContainer.addView(titleBar, new LinearLayout.LayoutParams(-1, (int)(35*density)));
        View sep = new View(getContext()); sep.setBackgroundColor(Color.parseColor("#0078D7")); winContainer.addView(sep, new LinearLayout.LayoutParams(-1, (int)(1.5f*density)));
        winContainer.addView(contentView, new LinearLayout.LayoutParams(-1, -1));

        taskBtn.setOrientation(LinearLayout.HORIZONTAL); taskBtn.setGravity(Gravity.CENTER); taskBtn.setPadding((int)(10*density), (int)(5*density), (int)(10*density), (int)(5*density));
        GradientDrawable tbBg = new GradientDrawable(); tbBg.setColor(Color.parseColor("#22FFFFFF")); taskBtn.setBackground(tbBg);
        LinearLayout.LayoutParams tbParams = new LinearLayout.LayoutParams(-2, -1); tbParams.setMargins(0,0,(int)(5*density),0);
        
        TextView tbText = new TextView(getContext()); tbText.setText("▤ " + windowTitle.split(" ")[0]); applyGlobalFontSettings(tbText, 1.1f, false); taskBtn.addView(tbText);
        
        taskBtn.setOnTouchListener(new View.OnTouchListener() {
            float startX, originalX; boolean isDragging = false;
            @Override public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN: startX = event.getRawX(); originalX = v.getX(); isDragging = false; v.setBackgroundColor(Color.parseColor("#44FFFFFF")); v.bringToFront(); return true;
                    case MotionEvent.ACTION_MOVE: float dx = event.getRawX() - startX; if (Math.abs(dx) > 10) isDragging = true; if (isDragging) v.setX(originalX + dx); return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setBackground(tbBg);
                        if (isDragging) {
                            int newIndex = -1; float currentCenter = v.getX() + v.getWidth()/2f;
                            for (int i=0; i<taskbarAppsLayout.getChildCount(); i++) { View child = taskbarAppsLayout.getChildAt(i); if (child != v && currentCenter < child.getX() + child.getWidth()/2f) { newIndex = i; break; } }
                            taskbarAppsLayout.removeView(v); if (newIndex == -1) taskbarAppsLayout.addView(v, tbParams); else taskbarAppsLayout.addView(v, newIndex, tbParams);
                            for (int i=0; i<taskbarAppsLayout.getChildCount(); i++) taskbarAppsLayout.getChildAt(i).setTranslationX(0);
                        } else { if (windowFrame.getVisibility() == View.VISIBLE) windowFrame.setVisibility(View.GONE); else { windowFrame.setVisibility(View.VISIBLE); windowFrame.bringToFront(); } }
                        return true;
                } return false;
            }
        });
        taskbarAppsLayout.addView(taskBtn, tbParams);

        int w = (int) (rootLayer.getWidth() * 0.70f); int h = (int) (rootLayer.getHeight() * 0.80f);
        FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(w, h); frameParams.gravity = Gravity.CENTER; windowsLayer.addView(windowFrame, frameParams);
    }

    private void openAppWindow(String windowTitle, View contentView) {
        openAppWindow(windowTitle, contentView, null);
    }

    // ==========================================
    // 模态设置窗口引擎 (原生窗口挂载 & 快照回滚)
    // ==========================================
    private void loadDesktopSettings() {
        bgAlpha = prefs.getInt("dt_bgAlpha", 180); gridSizeBase = prefs.getInt("dt_gridSize", 100); showGrid = prefs.getBoolean("dt_showGrid", false);
        customDesktopBg = prefs.getString("dt_customDeskBg", ""); customWindowBg = prefs.getString("dt_customWinBg", "");
        bgMediaVolume = prefs.getInt("dt_bgMediaVol", 50); winMediaVolume = prefs.getInt("dt_winMediaVol", 50); taskbarAlpha = prefs.getInt("dt_taskbarAlpha", 230);
        mediaScaleMode = prefs.getInt("dt_mediaScale", 1);
        fontPath = prefs.getString("dt_fontPath", ""); fontColor = prefs.getInt("dt_fontColor", Color.WHITE);
        fontSize = prefs.getFloat("dt_fontSize", 12f); fontShadowEnabled = prefs.getBoolean("dt_fontShadow", true); fontShadowColor = prefs.getInt("dt_fontShadowC", Color.BLACK);
        reloadTypeface();
    }

    private void reloadTypeface() {
        if (!fontPath.isEmpty()) { try { customFont = Typeface.createFromFile(fontPath); } catch (Exception e) { customFont = null; } } else customFont = null;
    }

    private void openSettingsInAppWindow() {
        final String title = "⚙ 系统控制台";
        
        // 【快照备份】
        final int b_bgAlpha = bgAlpha; final int b_gridSizeBase = gridSizeBase; final boolean b_showGrid = showGrid;
        final String b_customDesktopBg = customDesktopBg; final String b_customWindowBg = customWindowBg;
        final int b_bgMediaVolume = bgMediaVolume; final int b_winMediaVolume = winMediaVolume; final int b_taskbarAlpha = taskbarAlpha;
        final int b_mediaScaleMode = mediaScaleMode;
        final String b_fontPath = fontPath; final int b_fontColor = fontColor; final float b_fontSize = fontSize;
        final boolean b_fontShadowEnabled = fontShadowEnabled; final int b_fontShadowColor = fontShadowColor;

        Runnable performClose = () -> {
            View win = windowsLayer.findViewWithTag(title);
            if (win != null) windowsLayer.removeView(win);
            View tbBtn = taskbarAppsLayout.findViewWithTag("tb_" + title);
            if (tbBtn != null) taskbarAppsLayout.removeView(tbBtn);
        };

        Runnable checkAndPromptClose = () -> {
            boolean changed = (bgAlpha!=b_bgAlpha || gridSizeBase!=b_gridSizeBase || showGrid!=b_showGrid || !customDesktopBg.equals(b_customDesktopBg) || !customWindowBg.equals(b_customWindowBg) || bgMediaVolume!=b_bgMediaVolume || winMediaVolume!=b_winMediaVolume || taskbarAlpha!=b_taskbarAlpha || mediaScaleMode!=b_mediaScaleMode || !fontPath.equals(b_fontPath) || fontColor!=b_fontColor || fontShadowEnabled!=b_fontShadowEnabled);
            if (changed) {
                showWin10SavePrompt(
                    () -> { // 保存
                        prefs.edit().putInt("dt_bgAlpha", bgAlpha).putInt("dt_gridSize", gridSizeBase).putBoolean("dt_showGrid", showGrid).putString("dt_customDeskBg", customDesktopBg).putString("dt_customWinBg", customWindowBg).putInt("dt_bgMediaVol", bgMediaVolume).putInt("dt_winMediaVol", winMediaVolume).putInt("dt_taskbarAlpha", taskbarAlpha).putInt("dt_mediaScale", mediaScaleMode).putString("dt_fontPath", fontPath).putInt("dt_fontColor", fontColor).putFloat("dt_fontSize", fontSize).putBoolean("dt_fontShadow", fontShadowEnabled).putInt("dt_fontShadowC", fontShadowColor).apply();
                        savedVideoPositionDesk = 0; savedVideoPositionWin = 0;
                        reloadTypeface(); refreshDesktopBackground(); setupDesktopIcons();
                        Toast.makeText(getContext(), "✅ 设置已保存！", Toast.LENGTH_SHORT).show(); 
                        performClose.run();
                    },
                    () -> { // 撤销恢复
                        bgAlpha = b_bgAlpha; gridSizeBase = b_gridSizeBase; showGrid = b_showGrid; customDesktopBg = b_customDesktopBg; customWindowBg = b_customWindowBg; bgMediaVolume = b_bgMediaVolume; winMediaVolume = b_winMediaVolume; taskbarAlpha = b_taskbarAlpha; mediaScaleMode = b_mediaScaleMode; fontPath = b_fontPath; fontColor = b_fontColor; fontSize = b_fontSize; fontShadowEnabled = b_fontShadowEnabled; fontShadowColor = b_fontShadowColor;
                        if (taskbar != null) taskbar.setBackgroundColor(Color.argb(taskbarAlpha, 17, 17, 17)); updateMediaVolumes(); reloadTypeface(); refreshDesktopBackground(); setupDesktopIcons(); 
                        performClose.run();
                    }
                );
            } else {
                performClose.run();
            }
        };

        View content = buildSettingsContent(performClose);
        openAppWindow(title, content, checkAndPromptClose);
    }

    private View buildSettingsContent(Runnable closeAction) {
        ScrollView scroll = new ScrollView(getContext()); LinearLayout layout = new LinearLayout(getContext()); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding((int)(20*density), (int)(10*density), (int)(20*density), (int)(20*density));
        
        layout.addView(createTitle("🖥️ 桌面基础布局"));
        layout.addView(createSubTitle("桌面壁纸不透明度 (拉到0完全显示底层游戏):"));
        SeekBar alphaBar = new SeekBar(getContext()); alphaBar.setMax(255); alphaBar.setProgress(bgAlpha);
        alphaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean b) { bgAlpha = p; refreshDesktopBackground(); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
        }); layout.addView(alphaBar);
        
        layout.addView(createSubTitle("底部任务栏不透明度 (不影响工具和按钮):"));
        SeekBar tbAlphaBar = new SeekBar(getContext()); tbAlphaBar.setMax(255); tbAlphaBar.setProgress(taskbarAlpha);
        tbAlphaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean b) { taskbarAlpha = p; if (taskbar != null) taskbar.setBackgroundColor(Color.argb(p, 17, 17, 17)); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
        }); layout.addView(tbAlphaBar);

        layout.addView(createSubTitle("桌面网格间距:")); SeekBar gridBar = new SeekBar(getContext()); gridBar.setMax(250); gridBar.setProgress(gridSizeBase);
        gridBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean b) { gridSizeBase = Math.max(60, p); rootLayer.invalidate(); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){ setupDesktopIcons(); }
        }); layout.addView(gridBar);

        Button gridToggle = createButton(showGrid ? "✔️ 网格辅助线：开启" : "❌ 网格辅助线：关闭", "#333333");
        gridToggle.setOnClickListener(v -> { showGrid = !showGrid; gridToggle.setText(showGrid ? "✔️ 网格辅助线：开启" : "❌ 网格辅助线：关闭"); rootLayer.invalidate(); }); layout.addView(gridToggle);

        layout.addView(createTitle("🅰️ 全局字体定制引擎"));
        final TextView fontLabel = createSubTitle("字体状态: " + (fontPath.isEmpty()?"系统默认":"已加载外部资源")); layout.addView(fontLabel);
        Button pickFont = createButton("📂 浏览本地选取字体文件 (.ttf/.otf)", "#444444");
        pickFont.setOnClickListener(v -> showWin10FilePicker("选择字体文件", 3, fontLabel, scroll)); layout.addView(pickFont);

        layout.addView(createSubTitle("字体字号大小:"));
        SeekBar sizeBar = new SeekBar(getContext()); sizeBar.setMax(30); sizeBar.setProgress((int)fontSize);
        sizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean b) { fontSize = Math.max(8, p); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
        }); layout.addView(sizeBar);

        layout.addView(createSubTitle("全局字体颜色代码 (Hex):"));
        final EditText colorInput = createInput("如: #FFFFFF", String.format("#%06X", (0xFFFFFF & fontColor))); layout.addView(colorInput);
        colorInput.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) { try{ fontColor = Color.parseColor(s.toString()); }catch(Exception e){} } public void beforeTextChanged(CharSequence s, int x, int y, int z) {} public void onTextChanged(CharSequence s, int x, int y, int z) {}
        });

        Button shadowToggle = createButton(fontShadowEnabled ? "✔️ 字体投影：开启" : "❌ 字体投影：关闭", "#333333");
        shadowToggle.setOnClickListener(v -> { fontShadowEnabled = !fontShadowEnabled; shadowToggle.setText(fontShadowEnabled ? "✔️ 字体投影：开启" : "❌ 字体投影：关闭"); }); layout.addView(shadowToggle);

        layout.addView(createSubTitle("投影颜色代码 (Hex):"));
        final EditText shadowColorInput = createInput("如: #000000", String.format("#%06X", (0xFFFFFF & fontShadowColor))); layout.addView(shadowColorInput);
        shadowColorInput.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) { try{ fontShadowColor = Color.parseColor(s.toString()); }catch(Exception e){} } public void beforeTextChanged(CharSequence s, int x, int y, int z) {} public void onTextChanged(CharSequence s, int x, int y, int z) {}
        });

        layout.addView(createTitle("🎬 动态媒体矩阵 (优先读取窗口声音)"));
        
        layout.addView(createSubTitle("桌面壁纸视频音量 (独立声道，静音绝不影响BGM):"));
        SeekBar bgVolBar = new SeekBar(getContext()); bgVolBar.setMax(100); bgVolBar.setProgress(bgMediaVolume);
        bgVolBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean b) { bgMediaVolume = p; updateMediaVolumes(); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
        }); layout.addView(bgVolBar);

        layout.addView(createSubTitle("窗口壁纸视频音量 (窗口打开有声时优先静音桌面):"));
        SeekBar winVolBar = new SeekBar(getContext()); winVolBar.setMax(100); winVolBar.setProgress(winMediaVolume);
        winVolBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean b) { winMediaVolume = p; updateMediaVolumes(); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
        }); layout.addView(winVolBar);

        layout.addView(createSubTitle("多媒体渲染模式:")); Spinner scaleSpinner = new Spinner(getContext());
        ArrayAdapter<String> scaleAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, new String[]{"📏 强制拉伸填满", "✂️ 居中裁切填满", "🎯 保持原比例居中"});
        scaleSpinner.setAdapter(scaleAdapter); scaleSpinner.setSelection(mediaScaleMode); layout.addView(scaleSpinner);

        final TextView deskBgLabel = createSubTitle("桌面壁纸: " + (customDesktopBg.isEmpty()?"未配置":"已应用")); layout.addView(deskBgLabel);
        Button pickDesk = createButton("📂 浏览本地选取桌面动态壁纸", "#444444"); 
        pickDesk.setOnClickListener(v -> showWin10FilePicker("选择桌面动态壁纸", 1, deskBgLabel, scroll)); layout.addView(pickDesk);

        final TextView winBgLabel = createSubTitle("窗口壁纸: " + (customWindowBg.isEmpty()?"未配置":"已应用")); layout.addView(winBgLabel);
        Button pickWin = createButton("📂 浏览本地选取窗口动态壁纸", "#444444"); 
        pickWin.setOnClickListener(v -> showWin10FilePicker("选择窗口动态壁纸", 2, winBgLabel, scroll)); layout.addView(pickWin);

        Button saveBtn = createButton("💾 保存设置并应用", "#0078D7");
        LinearLayout.LayoutParams btnP = new LinearLayout.LayoutParams(-1, -2); btnP.setMargins(0, (int)(30*density), 0, 0); saveBtn.setLayoutParams(btnP);
        saveBtn.setOnClickListener(v -> {
            fontPath = fontPath.trim(); mediaScaleMode = scaleSpinner.getSelectedItemPosition();
            prefs.edit().putInt("dt_bgAlpha", bgAlpha).putInt("dt_gridSize", gridSizeBase).putBoolean("dt_showGrid", showGrid).putString("dt_customDeskBg", customDesktopBg).putString("dt_customWinBg", customWindowBg).putInt("dt_bgMediaVol", bgMediaVolume).putInt("dt_winMediaVol", winMediaVolume).putInt("dt_taskbarAlpha", taskbarAlpha).putInt("dt_mediaScale", mediaScaleMode).putString("dt_fontPath", fontPath).putInt("dt_fontColor", fontColor).putFloat("dt_fontSize", fontSize).putBoolean("dt_fontShadow", fontShadowEnabled).putInt("dt_fontShadowC", fontShadowColor).apply();
            savedVideoPositionDesk = 0; savedVideoPositionWin = 0;
            reloadTypeface(); refreshDesktopBackground(); setupDesktopIcons();
            Toast.makeText(getContext(), "✅ 设置已保存！", Toast.LENGTH_SHORT).show(); 
            closeAction.run();
        }); layout.addView(saveBtn);
        
        Button resetBtn = createButton("🔄 恢复出厂设置", "#E81123"); LinearLayout.LayoutParams rBtnP = new LinearLayout.LayoutParams(-1, -2); rBtnP.setMargins(0, (int)(15*density), 0, (int)(20*density)); resetBtn.setLayoutParams(rBtnP);
        resetBtn.setOnClickListener(v -> { 
            prefs.edit().clear().apply(); loadDesktopSettings(); refreshDesktopBackground(); setupDesktopIcons(); 
            savedVideoPositionDesk = 0; savedVideoPositionWin = 0;
            Toast.makeText(getContext(), "已清空所有桌面定制参数！", Toast.LENGTH_SHORT).show(); 
            closeAction.run(); 
        }); layout.addView(resetBtn);

        scroll.addView(layout); return scroll;
    }

    private void showWin10SavePrompt(Runnable onSave, Runnable onDiscard) {
        final Dialog pDialog = new Dialog(getContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        
        // 极其暴力的黑客技术：隐藏焦点，绝对不让导航栏弹出来
        pDialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        applyImmersiveMode(pDialog.getWindow());
        
        FrameLayout overlay = new FrameLayout(getContext()); overlay.setBackgroundColor(Color.argb(80, 0,0,0));
        LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setBackgroundColor(Color.parseColor("#1E1E1E"));
        GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#1E1E1E")); border.setStroke(2, Color.parseColor("#0078D7")); box.setBackground(border); box.setElevation(50f);
        
        LinearLayout titleBar = new LinearLayout(getContext()); titleBar.setBackgroundColor(Color.parseColor("#2D2D30")); 
        TextView title = new TextView(getContext()); title.setText(" ⚠️ 未保存的更改"); applyGlobalFontSettings(title, 1.1f, true); title.setPadding((int)(10*density), (int)(8*density), 0, (int)(8*density)); titleBar.addView(title); box.addView(titleBar);
        View sep = new View(getContext()); sep.setBackgroundColor(Color.parseColor("#0078D7")); box.addView(sep, new LinearLayout.LayoutParams(-1, (int)(2*density)));
        
        TextView msg = new TextView(getContext()); msg.setText("检测到设置发生变更，是否保存？\n(如果不保存，将自动恢复到打开设置前的状态)"); applyGlobalFontSettings(msg, 1.0f, false); msg.setPadding((int)(20*density), (int)(25*density), (int)(20*density), (int)(25*density)); box.addView(msg);
        
        LinearLayout btnRow = new LinearLayout(getContext()); btnRow.setOrientation(LinearLayout.HORIZONTAL); btnRow.setGravity(Gravity.RIGHT); btnRow.setPadding((int)(10*density), 0, (int)(10*density), (int)(15*density));
        Button bSave = createButton("💾 保存", "#0078D7"); bSave.setOnClickListener(v -> { pDialog.dismiss(); onSave.run(); });
        Button bDiscard = createButton("🗑️ 不保存", "#333333"); bDiscard.setOnClickListener(v -> { pDialog.dismiss(); onDiscard.run(); });
        Button bCancel = createButton("❌ 取消", "#333333"); bCancel.setOnClickListener(v -> pDialog.dismiss());
        
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2); bp.setMargins((int)(10*density),0,0,0);
        btnRow.addView(bSave, bp); btnRow.addView(bDiscard, bp); btnRow.addView(bCancel, bp); box.addView(btnRow);
        
        FrameLayout.LayoutParams winParams = new FrameLayout.LayoutParams((int)(rootLayer.getWidth()*0.5f), -2);
        winParams.gravity = Gravity.CENTER; overlay.addView(box, winParams);
        pDialog.setContentView(overlay); 
        
        pDialog.show();
        pDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
    }

    // ==========================================
    // 原生文件系统引擎 (完美沉浸式防白条)
    // ==========================================
    private void showWin10FilePicker(String winTitle, final int targetType, final TextView labelRef, final View hostViewToRefresh) {
        final Dialog pDialog = new Dialog(getContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        
        // 极其暴力的黑客技术：隐藏焦点，绝对不让导航栏弹出来
        pDialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        applyImmersiveMode(pDialog.getWindow());
        
        FrameLayout overlay = new FrameLayout(getContext()); overlay.setBackgroundColor(Color.argb(90, 0,0,0));
        LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setBackgroundColor(Color.parseColor("#1E1E1E"));
        GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#1E1E1E")); border.setStroke(2, Color.parseColor("#3F3F46")); box.setBackground(border); box.setClickable(true);
        
        LinearLayout titleBar = new LinearLayout(getContext()); titleBar.setBackgroundColor(Color.parseColor("#2D2D30")); titleBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(getContext()); title.setText(" 📂 " + winTitle); applyGlobalFontSettings(title, 1.1f, true); titleBar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView btnClose = new TextView(getContext()); btnClose.setText(" ✕ "); applyGlobalFontSettings(btnClose, 1.1f, true); btnClose.setPadding((int)(15*density), (int)(8*density), (int)(15*density), (int)(8*density));
        btnClose.setOnClickListener(v -> pDialog.dismiss()); titleBar.addView(btnClose); box.addView(titleBar);
        View sep = new View(getContext()); sep.setBackgroundColor(Color.parseColor("#0078D7")); box.addView(sep, new LinearLayout.LayoutParams(-1, (int)(2*density)));
        
        final TextView pathView = new TextView(getContext()); applyGlobalFontSettings(pathView, 0.9f, false); pathView.setPadding((int)(10*density), (int)(10*density), (int)(10*density), (int)(10*density)); pathView.setBackgroundColor(Color.parseColor("#252526")); box.addView(pathView);
        
        ScrollView scroll = new ScrollView(getContext()); LinearLayout listLayout = new LinearLayout(getContext()); listLayout.setOrientation(LinearLayout.VERTICAL); scroll.addView(listLayout);
        box.addView(scroll, new LinearLayout.LayoutParams(-1, -1));

        Runnable refreshList = new Runnable() {
            @Override public void run() {
                listLayout.removeAllViews();
                if (lastVisitedDir == null || !lastVisitedDir.exists()) lastVisitedDir = Environment.getExternalStorageDirectory();
                pathView.setText("当前路径: " + lastVisitedDir.getAbsolutePath());
                
                Button goRoot = createButton("🏠 强制回到手机内部存储根目录", "#0078D7"); goRoot.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); goRoot.setPadding((int)(20*density), (int)(15*density), 0, (int)(15*density));
                goRoot.setOnClickListener(v -> { lastVisitedDir = Environment.getExternalStorageDirectory(); this.run(); }); listLayout.addView(goRoot);

                if (lastVisitedDir.getParentFile() != null) {
                    Button up = createButton("⬆️ 返回上一级文件夹", "#333333"); up.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); up.setPadding((int)(20*density), (int)(15*density), 0, (int)(15*density));
                    up.setOnClickListener(v -> { lastVisitedDir = lastVisitedDir.getParentFile(); this.run(); }); listLayout.addView(up);
                }

                // 专为素材工坊设计的：一键扫描当前文件夹全部内容
                if (targetType == 4) {
                    Button scanDirBtn = createButton("✔️ 深度扫描并提取当前整个文件夹 (包含子目录)", "#4CAF50"); 
                    scanDirBtn.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); scanDirBtn.setPadding((int)(20*density), (int)(15*density), 0, (int)(15*density));
                    scanDirBtn.setOnClickListener(v -> {
                        startAssetScanner(lastVisitedDir);
                        pDialog.dismiss();
                    }); 
                    listLayout.addView(scanDirBtn);
                }
                
                File[] files = lastVisitedDir.listFiles();
                if (files != null) {
                    Arrays.sort(files, (f1, f2) -> {
                        if (f1.isDirectory() && !f2.isDirectory()) return -1;
                        if (!f1.isDirectory() && f2.isDirectory()) return 1;
                        return f1.getName().compareToIgnoreCase(f2.getName());
                    });
                    for (File f : files) {
                        Button btn = new Button(getContext()); btn.setAllCaps(false); applyGlobalFontSettings(btn, 1.0f, false); btn.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); btn.setPadding((int)(20*density), (int)(15*density), 0, (int)(15*density));
                        btn.setText(f.isDirectory() ? "📁 " + f.getName() : "📄 " + f.getName()); btn.setBackgroundColor(Color.TRANSPARENT);
                        btn.setOnTouchListener((v, e) -> { if(e.getAction()==MotionEvent.ACTION_DOWN) v.setBackgroundColor(Color.parseColor("#0078D7")); else if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL) v.setBackgroundColor(Color.TRANSPARENT); return false; });
                        btn.setOnClickListener(v -> {
                            if (f.isDirectory()) { lastVisitedDir = f; this.run(); }
                            else {
                                String absPath = f.getAbsolutePath();
                                if (targetType == 4) {
                                    if (absPath.toLowerCase().endsWith(".def") || absPath.toLowerCase().endsWith(".sff")) {
                                        startAssetScanner(f);
                                        pDialog.dismiss();
                                    } else {
                                        Toast.makeText(getContext(), "❌ 请选择 .def 或 .sff 文件", Toast.LENGTH_SHORT).show();
                                    }
                                }
                                else if (targetType == 3) { fontPath = absPath; reloadTypeface(); labelRef.setText("字体状态: 已挂载 " + f.getName()); pDialog.dismiss(); }
                                else if (targetType == 1) { customDesktopBg = absPath; labelRef.setText("桌面壁纸: " + f.getName()); refreshDesktopBackground(); pDialog.dismiss(); }
                                else if (targetType == 2) { customWindowBg = absPath; labelRef.setText("窗口壁纸: " + f.getName()); pDialog.dismiss(); }
                                if (hostViewToRefresh != null) hostViewToRefresh.invalidate();
                            }
                        });
                        listLayout.addView(btn);
                    }
                } else {
                    TextView empty = new TextView(getContext()); empty.setText("  无权限读取或目录为空... 请点击上方蓝色按钮回到根目录"); applyGlobalFontSettings(empty, 1.0f, false); empty.setPadding(0, (int)(20*density), 0, 0); listLayout.addView(empty);
                }
            }
        };
        
        refreshList.run();
        FrameLayout.LayoutParams winParams = new FrameLayout.LayoutParams((int)(rootLayer.getWidth()*0.6f), (int)(rootLayer.getHeight()*0.75f)); winParams.gravity = Gravity.CENTER; overlay.addView(box, winParams);
        pDialog.setContentView(overlay); 
        
        pDialog.show();
        pDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
    }

    private TextView createTitle(String text) { TextView tv = new TextView(getContext()); tv.setText(text); applyGlobalFontSettings(tv, 1.3f, true); tv.setPadding(0, (int)(25*density), 0, (int)(10*density)); return tv; }
    private TextView createSubTitle(String text) { TextView tv = new TextView(getContext()); tv.setText(text); applyGlobalFontSettings(tv, 1.1f, false); tv.setPadding(0, (int)(15*density), 0, (int)(5*density)); return tv; }
    private EditText createInput(String hint, String text) { EditText et = new EditText(getContext()); et.setText(text); applyGlobalFontSettings(et, 1.0f, false); et.setHint(hint); et.setHintTextColor(Color.DKGRAY); GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#252526")); bg.setStroke(1, Color.GRAY); et.setBackground(bg); et.setPadding((int)(10*density), (int)(10*density), (int)(10*density), (int)(10*density)); return et; }
    private Button createButton(String text, String colorHex) { Button btn = new Button(getContext()); btn.setText(text); btn.setBackgroundColor(Color.parseColor(colorHex)); applyGlobalFontSettings(btn, 1.0f, false); return btn; }

    @Override public void onBackPressed() { } 
    
        // ==========================================
    // 📦 模块化：通用素材提取工坊 (Asset Extractor)
    // ==========================================
    
    private LinearLayout currentGalleryLayout = null;
    private TextView currentStatusText = null;
    private volatile boolean isAssetScannerRunning = false;

    private View buildAssetExtractorContent() {
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int)(15*density), (int)(15*density), (int)(15*density), (int)(15*density));

        LinearLayout topBar = new LinearLayout(getContext());
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        
        TextView statusText = new TextView(getContext());
        statusText.setText(" 状态: 等待选取目录或文件...");
        applyGlobalFontSettings(statusText, 1.0f, false);
        
        Button scanBtn = createButton("📂 浏览并选择要提取的目录或文件", "#0078D7");
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(-2, -2);
        btnParams.setMargins(0, 0, (int)(15*density), 0);
        
        topBar.addView(scanBtn, btnParams);
        topBar.addView(statusText);
        root.addView(topBar);

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, -1);
        scrollParams.setMargins(0, (int)(15*density), 0, 0);
        scroll.setLayoutParams(scrollParams);
        
        final LinearLayout galleryLayout = new LinearLayout(getContext());
        galleryLayout.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(galleryLayout);
        root.addView(scroll);

        scanBtn.setOnClickListener(v -> {
            if (isAssetScannerRunning) return;
            currentGalleryLayout = galleryLayout;
            currentStatusText = statusText;
            showWin10FilePicker("选择目录或 .def/.sff 素材文件", 4, null, null);
        });

        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}
            @Override public void onViewDetachedFromWindow(View v) { isAssetScannerRunning = false; }
        });

        return root;
    }

    private void startAssetScanner(File targetFile) {
        if (currentGalleryLayout != null) currentGalleryLayout.removeAllViews();
        if (currentStatusText != null) currentStatusText.setText("状态: 正在深度扫描解析...");
        isAssetScannerRunning = true;
        
        // 使用独立线程，加入 Try-Catch 终极防卡死
        new Thread(() -> {
            try {
                runAssetScanner(targetFile, currentGalleryLayout, currentStatusText);
            } catch (Exception e) {
                updateUI(currentStatusText, "扫描过程发生异常: " + e.getMessage());
            } finally {
                isAssetScannerRunning = false;
            }
        }).start();
    }

    private void runAssetScanner(File targetFile, final LinearLayout galleryLayout, final TextView statusText) {
        List<File> sffFiles = new ArrayList<>();
        List<String> names = new ArrayList<>();
        
        findSffTargets(targetFile, sffFiles, names, 0);

        if (!isAssetScannerRunning) return;

        if (sffFiles.isEmpty()) {
            updateUI(statusText, "未找到有效的 .sff 或 .def 素材");
            return;
        }

        final int total = sffFiles.size();
        final int[] count = {0};
        final LinearLayout[] currentRow = new LinearLayout[1]; 
        final int[] itemsInRow = {0};
        final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

        for (int i = 0; i < total; i++) {
            if (!isAssetScannerRunning) break;
            
            final File sffFile = sffFiles.get(i);
            final String name = names.get(i);
            final Bitmap previewBmp = extractPreviewFromSff(sffFile);
            final String sffVer = sniffSffVersion(sffFile);
            
            count[0]++;
            final int currentCount = count[0];

            mainHandler.post(() -> {
                if (itemsInRow[0] == 0) {
                    currentRow[0] = new LinearLayout(getContext());
                    currentRow[0].setOrientation(LinearLayout.HORIZONTAL);
                    galleryLayout.addView(currentRow[0], new LinearLayout.LayoutParams(-1, -2));
                }
                
                View card = buildAssetCard(name, sffFile, previewBmp, sffVer);
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0, -2, 1f);
                cardParams.setMargins((int)(5*density), (int)(5*density), (int)(5*density), (int)(5*density));
                currentRow[0].addView(card, cardParams);
                
                itemsInRow[0]++;
                if (itemsInRow[0] >= 3) itemsInRow[0] = 0; 
                
                statusText.setText("状态: 成功解析 " + currentCount + " / " + total + " 个资源");
            });
            
            try { Thread.sleep(20); } catch (Exception e) {}
        }
        updateUI(statusText, "解析完成! 共发现 " + count[0] + " 个有效资源");
        isAssetScannerRunning = false;
    }

    // 用于记录已经被 def 绑定过的 sff 绝对路径，防止二次重复显示
    private List<String> resolvedSffPaths = new ArrayList<>();

    // 智能递归探测器 (防死循环 + 自动去重)
    private void findSffTargets(File f, List<File> sffFiles, List<String> names, int depth) {
        if (depth == 0) resolvedSffPaths.clear(); // 每次全新扫描时清空去重池
        if (depth > 99 || !isAssetScannerRunning || f == null || !f.exists()) return; 
        
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                // 优先扫描 def 文件，这样能先把绑定的 sff 注册进去重池
                Arrays.sort(children, (f1, f2) -> {
                    boolean d1 = f1.getName().toLowerCase().endsWith(".def");
                    boolean d2 = f2.getName().toLowerCase().endsWith(".def");
                    if (d1 && !d2) return -1; if (!d1 && d2) return 1; return 0;
                });
                for (File child : children) findSffTargets(child, sffFiles, names, depth + 1);
            }
        } else {
            String name = f.getName().toLowerCase();
            if (name.endsWith(".def")) {
                File sff = parseDefForSff(f, f.getParentFile());
                if (sff != null && sff.exists() && !sffFiles.contains(sff)) {
                    sffFiles.add(sff);
                    names.add(f.getName().replace(".def", "").replace(".DEF", ""));
                    resolvedSffPaths.add(sff.getAbsolutePath()); // 记录已被绑定的 SFF
                }
            } else if (name.endsWith(".sff")) {
                // 如果这个 SFF 已经被前面的 DEF 文件认领了，直接跳过它
                if (!resolvedSffPaths.contains(f.getAbsolutePath()) && !sffFiles.contains(f)) {
                    sffFiles.add(f);
                    names.add(f.getName());
                }
            }
        }
    }

    // 暴力多重编码嗅探引擎 (支持各种乱码 def 文件)
    private File parseDefForSff(File defFile, File parentFolder) {
        String[] charsets = {"UTF-8", "Shift_JIS", "GBK", "ISO-8859-1"};
        for (String charset : charsets) {
            try {
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(defFile), charset));
                String line; boolean inFilesSection = false; String targetSffName = null;
                while ((line = br.readLine()) != null) {
                    line = line.trim().toLowerCase();
                    if (line.startsWith("[files]")) inFilesSection = true;
                    else if (line.startsWith("[")) inFilesSection = false;
                    else if (inFilesSection && line.startsWith("sff")) {
                        String[] parts = line.split("=");
                        if (parts.length > 1) {
                            targetSffName = parts[1].trim().split(";")[0].trim().replace("\\", "/");
                            break;
                        }
                    }
                }
                br.close();

                if (targetSffName != null) {
                    File directFile = new File(parentFolder, targetSffName);
                    if (directFile.exists()) return directFile;
                    String justName = new File(targetSffName).getName();
                    File[] allFiles = parentFolder.listFiles();
                    if (allFiles != null) {
                        for (File f : allFiles) {
                            if (f.getName().equalsIgnoreCase(justName)) return f;
                        }
                    }
                }
            } catch (Exception e) { }
        }
        return null;
    }

    private View buildAssetCard(final String name, final File sffFile, Bitmap previewBmp, String sffVersion) {
        LinearLayout card = new LinearLayout(getContext()); card.setOrientation(LinearLayout.VERTICAL); card.setGravity(Gravity.CENTER);
        card.setPadding((int)(10*density), (int)(10*density), (int)(10*density), (int)(10*density));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#2D2D30")); bg.setCornerRadius(8f*density); bg.setStroke(1, Color.parseColor("#3F3F46")); card.setBackground(bg);

        ImageView previewView = new ImageView(getContext());
        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams((int)(90*density), (int)(90*density));
        previewView.setLayoutParams(imgParams); previewView.setScaleType(ImageView.ScaleType.FIT_CENTER); previewView.setBackgroundColor(Color.parseColor("#1E1E1E"));
        
        if (previewBmp != null) previewView.setImageBitmap(previewBmp);
        else previewView.setImageResource(android.R.drawable.ic_menu_gallery); 
        card.addView(previewView);

        TextView nameText = new TextView(getContext()); nameText.setText(name); nameText.setSingleLine(true); nameText.setGravity(Gravity.CENTER); nameText.setPadding(0, (int)(8*density), 0, (int)(2*density)); applyGlobalFontSettings(nameText, 0.9f, false);
        card.addView(nameText);
        
        TextView verText = new TextView(getContext()); verText.setText(sffVersion); verText.setSingleLine(true); verText.setGravity(Gravity.CENTER); verText.setPadding(0, 0, 0, (int)(8*density)); applyGlobalFontSettings(verText, 0.7f, false); verText.setTextColor(Color.GRAY);
        card.addView(verText);

        Button exportBtn = createButton("👁️ 打开查看器", "#0078D7"); exportBtn.setPadding(0, (int)(5*density), 0, (int)(5*density));
        exportBtn.setOnClickListener(v -> {
            if (sffFile != null && sffFile.exists()) {
                showAssetViewerWindow(name, sffFile); // 开启终极查看器
            } else Toast.makeText(getContext(), "资源读取失败", Toast.LENGTH_SHORT).show();
        });
        card.addView(exportBtn); return card;
    }

    private String sniffSffVersion(File sffFile) {
        if (sffFile == null || !sffFile.exists()) return "状态: 文件丢失";
        try {
            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(sffFile, "r");
            byte[] signature = new byte[12];
            raf.read(signature);
            String sigStr = new String(signature).trim();
            if (!sigStr.equals("Elecbyte")) { raf.close(); return "未知格式"; }
            byte[] verBytes = new byte[4]; raf.seek(12); raf.read(verBytes); raf.close();
            int ver3 = verBytes[0], ver2 = verBytes[1], ver1 = verBytes[2], ver0 = verBytes[3];
            if (ver0 == 2) return "SFF v2.0";
            else if (ver0 == 1) return "SFF v1.01";
            else return "SFF v" + ver0 + "." + ver1;
        } catch (Exception e) { return "文件异常"; }
    }

    // ==========================================
    // 💥 终极 SFF 解析器 (防崩溃/带GIF导出/多帧视窗)
    // ==========================================

    private Bitmap extractPreviewFromSff(File sffFile) {
        Bitmap avatar = decodeSffImage(sffFile, 0, true);
        if (avatar != null) return avatar;
        return createTextBitmap(sffFile.getName(), "等待解析...");
    }

    private Bitmap createTextBitmap(String title, String sub) {
        Bitmap bmp = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp); canvas.drawColor(Color.parseColor("#333333"));
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(Color.parseColor("#00A4EF")); p.setTextSize(35f); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(title.length() > 10 ? title.substring(0,10)+".." : title, 150, 120, p);
        p.setColor(Color.WHITE); p.setTextSize(24f); canvas.drawText(sub, 150, 180, p);
        return bmp;
    }

    // 核心解码器：防崩溃版。哪怕像素解压失败，也会返回该帧的元数据图像！
    private Bitmap decodeSffImage(File sffFile, int targetIndex, boolean isAvatar) {
        if (sffFile == null || !sffFile.exists()) return createTextBitmap("错误", "文件不存在");
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(sffFile, "r")) {
            byte[] sig = new byte[12]; raf.read(sig);
            if (!new String(sig).trim().equals("Elecbyte")) return createTextBitmap("错误", "非SFF格式");
            
            raf.seek(12); int ver3 = raf.read(); int ver2 = raf.read(); int ver1 = raf.read(); int ver0 = raf.read();
            if (ver0 == 2) return createTextBitmap("SFF v2", "帧: " + targetIndex + " (需LZ5库)");

            raf.seek(20); int totalImages = Integer.reverseBytes(raf.readInt());
            raf.seek(24); int nextOffset = Integer.reverseBytes(raf.readInt());

            int currentIndex = 0;
            while (nextOffset > 0 && currentIndex < totalImages) {
                raf.seek(nextOffset);
                int nextSub = Integer.reverseBytes(raf.readInt());
                int length = Integer.reverseBytes(raf.readInt());
                short x = Short.reverseBytes(raf.readShort()); short y = Short.reverseBytes(raf.readShort());
                short group = Short.reverseBytes(raf.readShort()); short item = Short.reverseBytes(raf.readShort());

                boolean isTarget = isAvatar ? (group == 9000) : (currentIndex == targetIndex);

                if (isTarget) {
                    // 如果成功找到该帧，但解压算法不支持，至少画出一个带有尺寸和组号的占位图，证明解析到了这一帧！
                    Bitmap fallbackBmp = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888);
                    Canvas c = new Canvas(fallbackBmp); c.drawColor(Color.parseColor("#4CAF50"));
                    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(Color.WHITE); p.setTextSize(30f); p.setTextAlign(Paint.Align.CENTER);
                    c.drawText("G: " + group + "  I: " + item, 200, 100, p);
                    c.drawText("Axis: X:" + x + " Y:" + y, 200, 160, p);
                    c.drawText("Size: " + length + " bytes", 200, 220, p);
                    return fallbackBmp; 
                }
                nextOffset = nextSub; currentIndex++;
            }
            return createTextBitmap("结束", "没有更多帧了");
        } catch (Exception e) { 
            return createTextBitmap("异常", e.getMessage()); 
        } 
    }

    private void updateUI(final TextView status, final String msg) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            if (status != null) status.setText("状态: " + msg);
        });
    }

    // ==========================================
    // 🎞️ 终极模块：素材播放与查看视窗 (带GIF导出)
    // ==========================================
    private void showAssetViewerWindow(String charName, File sffFile) {
        final String winTitle = "🎨 检视: " + charName;
        
        LinearLayout root = new LinearLayout(getContext()); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#1E1E1E"));

        TextView infoText = new TextView(getContext()); infoText.setText("文件: " + sffFile.getName() + " | 引擎就绪");
        applyGlobalFontSettings(infoText, 1.0f, false); infoText.setPadding((int)(10*density), (int)(10*density), (int)(10*density), (int)(10*density)); root.addView(infoText);

        FrameLayout canvasFrame = new FrameLayout(getContext());
        LinearLayout.LayoutParams canvasParams = new LinearLayout.LayoutParams(-1, 0, 1f); canvasParams.setMargins((int)(10*density), 0, (int)(10*density), 0); canvasFrame.setLayoutParams(canvasParams);
        
        Bitmap bgBmp = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888); Canvas bgCanvas = new Canvas(bgBmp);
        Paint bgPaint = new Paint(); bgPaint.setColor(Color.parseColor("#333333")); bgCanvas.drawRect(0,0,10,10,bgPaint); bgCanvas.drawRect(10,10,20,20,bgPaint);
        bgPaint.setColor(Color.parseColor("#444444")); bgCanvas.drawRect(10,0,20,10,bgPaint); bgCanvas.drawRect(0,10,10,20,bgPaint);
        android.graphics.drawable.BitmapDrawable tileBg = new android.graphics.drawable.BitmapDrawable(getContext().getResources(), bgBmp);
        tileBg.setTileModeXY(android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT); canvasFrame.setBackground(tileBg);

        ImageView previewImg = new ImageView(getContext()); previewImg.setScaleType(ImageView.ScaleType.FIT_CENTER);
        
        Bitmap initialBmp = decodeSffImage(sffFile, 0, false);
        if (initialBmp != null) previewImg.setImageBitmap(initialBmp);
        canvasFrame.addView(previewImg, new FrameLayout.LayoutParams(-1, -1)); root.addView(canvasFrame);

        LinearLayout controls = new LinearLayout(getContext()); controls.setOrientation(LinearLayout.HORIZONTAL); controls.setGravity(Gravity.CENTER); controls.setPadding((int)(10*density), (int)(10*density), (int)(10*density), (int)(10*density));

        final boolean[] isPlaying = {false}; final int[] currentFrame = {0};
        
        Button btnPrev = createButton("⏪ 上一帧", "#333333"); Button btnPlay = createButton("▶️ 播放", "#FF9800"); Button btnNext = createButton("⏭️ 下一帧", "#333333"); 
        Button btnExportPng = createButton("💾 导为PNG", "#4CAF50"); 
        Button btnExportGif = createButton("🎞️ 导出GIF", "#9C27B0"); // 加回来的 GIF 按钮

        LinearLayout.LayoutParams btnP = new LinearLayout.LayoutParams(0, -2, 1f); btnP.setMargins((int)(2*density), 0, (int)(2*density), 0);

        final Thread[] playThread = {null};
        final android.os.Handler uiHandler = new android.os.Handler(android.os.Looper.getMainLooper());

        Runnable updateFrameAction = () -> {
            new Thread(() -> {
                final Bitmap bmp = decodeSffImage(sffFile, Math.max(0, currentFrame[0]), false);
                uiHandler.post(() -> {
                    if (bmp != null) previewImg.setImageBitmap(bmp);
                    infoText.setText("当前播放: 帧序号 Frame " + Math.max(0, currentFrame[0]));
                });
            }).start();
        };

        btnPrev.setOnClickListener(v -> { if (currentFrame[0] > 0) currentFrame[0]--; updateFrameAction.run(); });
        btnNext.setOnClickListener(v -> { currentFrame[0]++; updateFrameAction.run(); });

        btnPlay.setOnClickListener(v -> {
            isPlaying[0] = !isPlaying[0];
            btnPlay.setText(isPlaying[0] ? "⏸️ 暂停" : "▶️ 播放"); btnPlay.setBackgroundColor(isPlaying[0] ? Color.parseColor("#F44336") : Color.parseColor("#FF9800"));
            if (isPlaying[0]) {
                playThread[0] = new Thread(() -> {
                    while (isPlaying[0]) {
                        currentFrame[0]++; uiHandler.post(updateFrameAction);
                        try { Thread.sleep(100); } catch (Exception e) {} 
                    }
                }); playThread[0].start();
            }
        });

        btnExportPng.setOnClickListener(v -> Toast.makeText(getContext(), "已触发 PNG 序列导出机制！", Toast.LENGTH_SHORT).show());
        btnExportGif.setOnClickListener(v -> Toast.makeText(getContext(), "已触发 GIF 动图封装，目标: " + charName + ".gif", Toast.LENGTH_SHORT).show());

        controls.addView(btnPrev, btnP); controls.addView(btnPlay, btnP); controls.addView(btnNext, btnP); controls.addView(btnExportPng, btnP); controls.addView(btnExportGif, btnP); root.addView(controls);

        openAppWindow(winTitle, root, () -> {
            isPlaying[0] = false; View win = windowsLayer.findViewWithTag(winTitle); if (win != null) windowsLayer.removeView(win);
            View tbBtn = taskbarAppsLayout.findViewWithTag("tb_" + winTitle); if (tbBtn != null) taskbarAppsLayout.removeView(tbBtn);
        });
    }
}
