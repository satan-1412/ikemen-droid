package org.libsdl.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import android.os.Handler;

public class DesktopSystemView extends Dialog {

    public static DesktopSystemView instance;
    
    private void updateUI(final TextView status, final String msg) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            if (status != null) status.setText(msg);
        });
    }

    private Context mContext;
    private SharedPreferences prefs;
    private float density;

    private float mouseX = -1f, mouseY = -1f;
    private Path cursorPath;
    private Paint cursorPaintFill, cursorPaintStroke;

    private FrameLayout rootLayer;
    private FrameLayout desktopBgLayer;
    private FrameLayout desktopIconsLayer;
    private FrameLayout windowsLayer;
    private LinearLayout taskbar; 
    private LinearLayout taskbarAppsLayout;

    public int bgAlpha = 180; 
    public int gridSizeBase = 100;
    public boolean showGrid = false;
    public String customDesktopBg = "";
    public String customWindowBg = "";
    
    public int taskbarAlpha = 230; 
    public int bgMediaVolume = 50; 
    public int winMediaVolume = 50; 
    public int mediaScaleMode = 1; 
    
    public static int savedVideoPositionDesk = 0;
    public static int savedVideoPositionWin = 0;
    
    private MediaPlayer bgMediaPlayer = null;
    private List<MediaPlayer> winMediaPlayers = new ArrayList<>();
    private MediaPlayer currentSndPlayer = null; // SND专属播放器
    
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
    public void dismiss() { 
        super.dismiss(); 
        if (instance == this) instance = null; 
        if (currentSndPlayer != null) { currentSndPlayer.release(); currentSndPlayer = null; }
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
        taskbarAppsLayout.setPadding((int)(15*density), 0, (int)(20*density), 0);
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

    private void setupDesktopIcons() {
        desktopIconsLayer.removeAllViews(); 
        createDesktopIcon("sys_settings", "⚙️", "系统控制台");
        createDesktopIcon("asset_extractor", "🖼️", "SFF查看器"); // 🔥 已换图标和名字
        createDesktopIcon("snd_extractor", "🎵", "SND查看器");  // 🔥 新增SND组件
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
                            else if (id.equals("asset_extractor")) openAppWindow("🖼️ SFF查看器", buildAssetExtractorContent(), null);
                            else if (id.equals("snd_extractor")) openAppWindow("🎵 SND查看器", buildSndExtractorContent(), null);
                            lastClickTime = 0; 
                        } else lastClickTime = clickTime;
                    }
                }
                return true;
            }
        });
    }

    private void showContextMenu(View anchor, String title, Runnable onClose) {
        FrameLayout menuOverlay = new FrameLayout(getContext());
        menuOverlay.setClickable(true);
        menuOverlay.setOnClickListener(v -> rootLayer.removeView(menuOverlay));
        
        LinearLayout menu = new LinearLayout(getContext());
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setBackgroundColor(Color.parseColor("#2D2D30"));
        GradientDrawable border = new GradientDrawable();
        border.setColor(Color.parseColor("#2D2D30"));
        border.setStroke((int)(1*density), Color.parseColor("#3F3F46"));
        menu.setBackground(border);
        menu.setPadding((int)(5*density), (int)(5*density), (int)(5*density), (int)(5*density));
        
        Button btnClose = createButton("❌ 强制关闭", "#E81123");
        btnClose.setOnClickListener(v -> { onClose.run(); rootLayer.removeView(menuOverlay); });
        menu.addView(btnClose);
        
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2);
        int[] loc = new int[2]; anchor.getLocationOnScreen(loc);
        params.leftMargin = loc[0]; 
        params.topMargin = Math.max(0, loc[1] - (int)(60*density));
        
        menuOverlay.addView(menu, params);
        rootLayer.addView(menuOverlay, new FrameLayout.LayoutParams(-1, -1));
        menuOverlay.bringToFront();
    }

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

        final TextView btnMax = new TextView(getContext());
        btnMax.setText(" □ "); applyGlobalFontSettings(btnMax, 1.0f, true); btnMax.setPadding((int)(15*density), (int)(5*density), (int)(15*density), (int)(8*density));
        final boolean[] isMaximized = {false};
        final int[] savedBounds = new int[4]; 
        
        btnMax.setOnClickListener(v -> {
            if (isMaximized[0]) {
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(savedBounds[2], savedBounds[3]);
                windowFrame.setLayoutParams(lp); 
                windowFrame.setX(savedBounds[0]); 
                windowFrame.setY(savedBounds[1]);
                btnMax.setText(" □ "); isMaximized[0] = false;
            } else {
                savedBounds[0] = (int) windowFrame.getX(); savedBounds[1] = (int) windowFrame.getY();
                savedBounds[2] = windowFrame.getWidth(); savedBounds[3] = windowFrame.getHeight();
                
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
                lp.bottomMargin = (int)(50 * density); 
                windowFrame.setLayoutParams(lp); 
                windowFrame.setX(0); 
                windowFrame.setY(0); 
                windowFrame.bringToFront();
                btnMax.setText(" ❐ "); isMaximized[0] = true;
            }
        });
        controls.addView(btnMax);

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
        controls.addView(btnClose); titleBar.addView(controls, new LinearLayout.LayoutParams(-2, -2)); 

        titleBar.setOnTouchListener(new View.OnTouchListener() {
            float dX, dY;
            @Override public boolean onTouch(View v, MotionEvent event) {
                if (isMaximized[0]) return true;
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
        
        String rawName = windowTitle.replace("🎨 检视: ", "").replace("📦 ", "").replace("🎵 检视: ", "").trim();
        final String finalShortName = rawName.length() > 8 ? rawName.substring(0, 8) + ".." : rawName;
        TextView tbText = new TextView(getContext()); tbText.setText("▤ " + finalShortName); applyGlobalFontSettings(tbText, 1.1f, false); taskBtn.addView(tbText);
        
        taskBtn.setOnTouchListener(new View.OnTouchListener() {
            float startX; float initialTranslation; boolean isDragging = false;
            @Override public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN: 
                        startX = event.getRawX(); initialTranslation = v.getTranslationX();
                        isDragging = false; v.setBackgroundColor(Color.parseColor("#44FFFFFF")); return false; 
                    case MotionEvent.ACTION_MOVE: 
                        float dx = event.getRawX() - startX; 
                        if (Math.abs(dx) > 10 * density) { 
                            isDragging = true; v.getParent().requestDisallowInterceptTouchEvent(true); 
                        }
                        if (isDragging) { v.setTranslationX(initialTranslation + dx); v.bringToFront(); }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setBackground(tbBg);
                        if (isDragging) {
                            float currentCenter = v.getX() + v.getTranslationX() + v.getWidth() / 2f;
                            int newIndex = taskbarAppsLayout.getChildCount() - 1;
                            for (int i = 0; i < taskbarAppsLayout.getChildCount(); i++) {
                                View child = taskbarAppsLayout.getChildAt(i);
                                if (child != v && currentCenter < child.getX() + child.getWidth() / 2f) { newIndex = i; break; }
                            }
                            final int targetIndex = newIndex;
                            taskbarAppsLayout.post(() -> {
                                taskbarAppsLayout.removeView(v);
                                v.setTranslationX(0);
                                taskbarAppsLayout.addView(v, targetIndex);
                            });
                            return true;
                        }
                        return false; 
                } return false;
            }
        });

        taskBtn.setOnClickListener(v -> {
            if (windowFrame.getVisibility() == View.VISIBLE) {
                if (windowFrame.getZ() == windowsLayer.getChildCount()) windowFrame.setVisibility(View.GONE);
                else windowFrame.bringToFront();
            } else { windowFrame.setVisibility(View.VISIBLE); windowFrame.bringToFront(); }
        });
        
        taskBtn.setOnLongClickListener(v -> {
            showContextMenu(v, finalShortName, () -> {
                if (onCloseInterceptor != null) onCloseInterceptor.run();
                else { windowsLayer.removeView(windowFrame); taskbarAppsLayout.removeView(taskBtn); }
            });
            return true;
        });
        
        taskbarAppsLayout.addView(taskBtn, tbParams);

        int w = (int) (rootLayer.getWidth() * 0.70f); int h = (int) (rootLayer.getHeight() * 0.80f);
        if (w == 0) w = 800; if (h == 0) h = 600; 
        FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(w, h);
        windowFrame.setLayoutParams(frameParams);
        
        windowFrame.post(() -> {
            if (!isMaximized[0]) {
                windowFrame.setX((rootLayer.getWidth() - windowFrame.getWidth()) / 2f);
                windowFrame.setY((rootLayer.getHeight() - windowFrame.getHeight()) / 2f);
            }
        });
        windowsLayer.addView(windowFrame);
    }

    private void openAppWindow(String windowTitle, View contentView) {
        openAppWindow(windowTitle, contentView, null);
    }

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
        final int b_bgAlpha = bgAlpha; final int b_gridSizeBase = gridSizeBase; final boolean b_showGrid = showGrid;
        final String b_customDesktopBg = customDesktopBg; final String b_customWindowBg = customWindowBg;
        final int b_bgMediaVolume = bgMediaVolume; final int b_winMediaVolume = winMediaVolume; final int b_taskbarAlpha = taskbarAlpha;
        final int b_mediaScaleMode = mediaScaleMode;
        final String b_fontPath = fontPath; final int b_fontColor = fontColor; final float b_fontSize = fontSize;
        final boolean b_fontShadowEnabled = fontShadowEnabled; final int b_fontShadowColor = fontShadowColor;

        Runnable performClose = () -> {
            View win = windowsLayer.findViewWithTag(title); if (win != null) windowsLayer.removeView(win);
            View tbBtn = taskbarAppsLayout.findViewWithTag("tb_" + title); if (tbBtn != null) taskbarAppsLayout.removeView(tbBtn);
        };

        Runnable checkAndPromptClose = () -> {
            boolean changed = (bgAlpha!=b_bgAlpha || gridSizeBase!=b_gridSizeBase || showGrid!=b_showGrid || !customDesktopBg.equals(b_customDesktopBg) || !customWindowBg.equals(b_customWindowBg) || bgMediaVolume!=b_bgMediaVolume || winMediaVolume!=b_winMediaVolume || taskbarAlpha!=b_taskbarAlpha || mediaScaleMode!=b_mediaScaleMode || !fontPath.equals(b_fontPath) || fontColor!=b_fontColor || fontShadowEnabled!=b_fontShadowEnabled);
            if (changed) {
                showWin10SavePrompt(
                    () -> { 
                        prefs.edit().putInt("dt_bgAlpha", bgAlpha).putInt("dt_gridSize", gridSizeBase).putBoolean("dt_showGrid", showGrid).putString("dt_customDeskBg", customDesktopBg).putString("dt_customWinBg", customWindowBg).putInt("dt_bgMediaVol", bgMediaVolume).putInt("dt_winMediaVol", winMediaVolume).putInt("dt_taskbarAlpha", taskbarAlpha).putInt("dt_mediaScale", mediaScaleMode).putString("dt_fontPath", fontPath).putInt("dt_fontColor", fontColor).putFloat("dt_fontSize", fontSize).putBoolean("dt_fontShadow", fontShadowEnabled).putInt("dt_fontShadowC", fontShadowColor).apply();
                        savedVideoPositionDesk = 0; savedVideoPositionWin = 0;
                        reloadTypeface(); refreshDesktopBackground(); setupDesktopIcons();
                        Toast.makeText(getContext(), "✅ 设置已保存！", Toast.LENGTH_SHORT).show(); 
                        performClose.run();
                    },
                    () -> { 
                        bgAlpha = b_bgAlpha; gridSizeBase = b_gridSizeBase; showGrid = b_showGrid; customDesktopBg = b_customDesktopBg; customWindowBg = b_customWindowBg; bgMediaVolume = b_bgMediaVolume; winMediaVolume = b_winMediaVolume; taskbarAlpha = b_taskbarAlpha; mediaScaleMode = b_mediaScaleMode; fontPath = b_fontPath; fontColor = b_fontColor; fontSize = b_fontSize; fontShadowEnabled = b_fontShadowEnabled; fontShadowColor = b_fontShadowColor;
                        if (taskbar != null) taskbar.setBackgroundColor(Color.argb(taskbarAlpha, 17, 17, 17)); updateMediaVolumes(); reloadTypeface(); refreshDesktopBackground(); setupDesktopIcons(); 
                        performClose.run();
                    }
                );
            } else performClose.run();
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

    private void showWin10FilePicker(String winTitle, final int targetType, final TextView labelRef, final View hostViewToRefresh) {
        final Dialog pDialog = new Dialog(getContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
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
                
                Button goRoot = createButton("🏠 回到内部存储根目录", "#0078D7"); goRoot.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); goRoot.setPadding((int)(20*density), (int)(15*density), 0, (int)(15*density));
                goRoot.setOnClickListener(v -> { lastVisitedDir = Environment.getExternalStorageDirectory(); this.run(); }); listLayout.addView(goRoot);

                if (lastVisitedDir.getParentFile() != null) {
                    Button up = createButton("⬆️ 返回上一级文件夹", "#333333"); up.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); up.setPadding((int)(20*density), (int)(15*density), 0, (int)(15*density));
                    up.setOnClickListener(v -> { lastVisitedDir = lastVisitedDir.getParentFile(); this.run(); }); listLayout.addView(up);
                }

                if (targetType == 4) {
                    Button scanDirBtn = createButton("✔️ 扫描并提取本文件夹的 SFF 素材", "#4CAF50"); 
                    scanDirBtn.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); scanDirBtn.setPadding((int)(20*density), (int)(15*density), 0, (int)(15*density));
                    scanDirBtn.setOnClickListener(v -> { startAssetScanner(lastVisitedDir); pDialog.dismiss(); }); 
                    listLayout.addView(scanDirBtn);
                } else if (targetType == 5) { // 🔥 SND专用扫描按钮
                    Button scanDirBtn = createButton("✔️ 扫描并提取本文件夹的 SND 音频", "#FF9800"); 
                    scanDirBtn.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); scanDirBtn.setPadding((int)(20*density), (int)(15*density), 0, (int)(15*density));
                    scanDirBtn.setOnClickListener(v -> { startSndScanner(lastVisitedDir); pDialog.dismiss(); }); 
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
                                    if (absPath.toLowerCase().endsWith(".def") || absPath.toLowerCase().endsWith(".sff")) { startAssetScanner(f); pDialog.dismiss(); } 
                                    else Toast.makeText(getContext(), "❌ 请选择 .def 或 .sff", Toast.LENGTH_SHORT).show();
                                }
                                else if (targetType == 5) {
                                    if (absPath.toLowerCase().endsWith(".snd")) { startSndScanner(f); pDialog.dismiss(); } 
                                    else Toast.makeText(getContext(), "❌ 请选择 .snd 音频包", Toast.LENGTH_SHORT).show();
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
                    TextView empty = new TextView(getContext()); empty.setText("  无权限读取或目录为空..."); applyGlobalFontSettings(empty, 1.0f, false); empty.setPadding(0, (int)(20*density), 0, 0); listLayout.addView(empty);
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
    
    private Button createButton(String text, String colorHex) { 
        Button btn = new Button(getContext()); 
        btn.setText(text); 
        btn.setAllCaps(false); 
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(); 
        bg.setColor(Color.parseColor(colorHex)); 
        bg.setCornerRadius(0); 
        bg.setStroke((int)(1*density), Color.parseColor("#44FFFFFF"));
        btn.setBackground(bg); 
        applyGlobalFontSettings(btn, 1.0f, false); 
        btn.setTextColor(Color.WHITE);
        btn.setPadding((int)(15*density), (int)(8*density), (int)(15*density), (int)(8*density));
        btn.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) v.setAlpha(0.7f);
            else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) v.setAlpha(1.0f);
            return false;
        });
        return btn; 
    }

    @Override public void onBackPressed() { } 

    // ======================================================================================
    // 🎨 核心模块 1：SFF 检视工坊
    // ======================================================================================
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
        
        Button scanBtn = createButton("📂 浏览并选择 SFF 素材文件", "#0078D7");
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

    private static class ValidAsset {
        String name; File sff; Bitmap preview; String version; byte[] actData;
        ValidAsset(String n, File s, Bitmap p, String v, byte[] a) { name=n; sff=s; preview=p; version=v; actData=a; }
    }

    private void startAssetScanner(File targetFile) {
        if (currentGalleryLayout != null) currentGalleryLayout.removeAllViews();
        isAssetScannerRunning = true;
        new Thread(() -> {
            try { runAssetScanner(targetFile, currentGalleryLayout, currentStatusText); } 
            catch (Exception e) { updateUI(currentStatusText, "扫描异常: " + e.getMessage()); } 
            finally { isAssetScannerRunning = false; }
        }).start();
    }

    private void runAssetScanner(File targetFile, final LinearLayout galleryLayout, final TextView statusText) {
        List<File> sffFiles = new ArrayList<>(); List<String> names = new ArrayList<>(); List<byte[]> acts = new ArrayList<>(); 
        updateUI(statusText, "📡 阶段 1/3: 递归扫描目录与协议匹配...");
        findSffTargets(targetFile, sffFiles, names, acts, 0);

        if (!isAssetScannerRunning) return;
        if (sffFiles.isEmpty()) { updateUI(statusText, "⚠️ 未找到有效的 .sff 素材"); return; }

        updateUI(statusText, "⚙️ 阶段 2/3: 深度解析与沙盒预检...");
        final int total = sffFiles.size();
        final List<ValidAsset> validAssets = new ArrayList<>();
        final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

        for (int i = 0; i < total; i++) {
            if (!isAssetScannerRunning) break;
            final File sffFile = sffFiles.get(i); final String name = names.get(i); final byte[] actForThisFile = acts.get(i);
            Bitmap previewBmp = extractPreviewFromSff(sffFile, actForThisFile);
            final String sffVer = sniffSffVersion(sffFile);
            if (previewBmp != null && previewBmp.getWidth() != 300) {
                validAssets.add(new ValidAsset(name, sffFile, previewBmp, sffVer, actForThisFile));
            }
            final int currentCount = i + 1;
            mainHandler.post(() -> statusText.setText(String.format("⚙️ 阶段 2/3: 沙盒预检 %d / %d (已挂载 %d 个有效)", currentCount, total, validAssets.size())));
        }
        
        if (!isAssetScannerRunning) return;
        updateUI(statusText, "🖥️ 阶段 3/3: 预检完毕，正在渲染安全界面...");

        mainHandler.post(() -> {
            LinearLayout currentRow = null; int itemsInRow = 0;
            for (ValidAsset va : validAssets) {
                if (itemsInRow == 0) {
                    currentRow = new LinearLayout(getContext()); currentRow.setOrientation(LinearLayout.HORIZONTAL);
                    galleryLayout.addView(currentRow, new LinearLayout.LayoutParams(-1, -2));
                }
                View card = buildAssetCard(va.name, va.sff, va.preview, va.version, va.actData);
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0, -2, 1f); cardParams.setMargins((int)(5*density), (int)(5*density), (int)(5*density), (int)(5*density));
                currentRow.addView(card, cardParams);
                itemsInRow++; if (itemsInRow >= 3) itemsInRow = 0; 
            }
            statusText.setText("✅ 解析完成! 成功通过沙盒试解，挂载了 " + validAssets.size() + " 个无损资源");
        });
        isAssetScannerRunning = false;
    }

    private List<String> resolvedSffPaths = new ArrayList<>();

    private void findSffTargets(File f, List<File> sffFiles, List<String> names, List<byte[]> acts, int depth) {
        if (depth == 0) resolvedSffPaths.clear(); 
        if (depth > 99 || !isAssetScannerRunning || f == null || !f.exists()) return; 
        
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                Arrays.sort(children, (f1, f2) -> {
                    boolean d1 = f1.getName().toLowerCase().endsWith(".def"); boolean d2 = f2.getName().toLowerCase().endsWith(".def");
                    if (d1 && !d2) return -1; if (!d1 && d2) return 1; return 0;
                });
                for (File child : children) findSffTargets(child, sffFiles, names, acts, depth + 1);
            }
        } else {
            String name = f.getName().toLowerCase();
            if (name.endsWith(".def")) {
                byte[][] extractedAct = new byte[1][]; 
                File sff = parseDefForSffAndAct(f, f.getParentFile(), extractedAct);
                if (sff != null && sff.exists() && !sffFiles.contains(sff)) {
                    sffFiles.add(sff);
                    String parsedName = parseDefForDisplayName(f);
                    names.add(parsedName != null ? parsedName : f.getName().replace(".def", "").replace(".DEF", ""));
                    acts.add(extractedAct[0]); 
                    resolvedSffPaths.add(sff.getAbsolutePath()); 
                }
            } else if (name.endsWith(".sff")) {
                if (!resolvedSffPaths.contains(f.getAbsolutePath()) && !sffFiles.contains(f)) {
                    sffFiles.add(f); names.add(f.getName());
                    byte[] localAct = null;
                    File actFile = new File(f.getParentFile(), f.getName().replace(".sff", ".act").replace(".SFF", ".act"));
                    if (actFile.exists()) {
                        try (java.io.FileInputStream fis = new java.io.FileInputStream(actFile)) {
                            localAct = new byte[768]; fis.read(localAct);
                        } catch (Exception e) { localAct = null; }
                    }
                    acts.add(localAct);
                }
            }
        }
    }

    private String parseDefForDisplayName(File defFile) {
        String[] charsets = {"UTF-8", "Shift_JIS", "GBK", "ISO-8859-1"};
        for (String charset : charsets) {
            try (BufferedReader br = new BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(defFile), charset))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim().toLowerCase();
                    if (line.startsWith("displayname") || line.startsWith("name")) return line.split("=")[1].trim().replace("\"", "");
                }
            } catch (Exception e) {}
        }
        return null;
    }

    private File parseDefForSffAndAct(File defFile, File parentFolder, byte[][] outAct) {
        String[] charsets = {"UTF-8", "Shift_JIS", "GBK", "ISO-8859-1"};
        for (String charset : charsets) {
            try {
                BufferedReader br = new BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(defFile), charset));
                String line; boolean inFilesSection = false; String targetSffName = null;
                while ((line = br.readLine()) != null) {
                    line = line.trim().toLowerCase();
                    if (line.startsWith("[files]")) inFilesSection = true;
                    else if (line.startsWith("[")) inFilesSection = false;
                    else if (inFilesSection && line.startsWith("sff")) {
                        String[] parts = line.split("=");
                        if (parts.length > 1) targetSffName = parts[1].trim().split(";")[0].trim().replace("\\", "/");
                    }
                    else if (inFilesSection && line.startsWith("pal1")) {
                        String[] actParts = line.split("=");
                        if (actParts.length > 1) {
                            String actName = actParts[1].trim().split(";")[0].trim().replace("\\", "/");
                            File actFile = new File(parentFolder, actName);
                            if (actFile.exists()) {
                                try (java.io.FileInputStream fis = new java.io.FileInputStream(actFile)) {
                                    outAct[0] = new byte[768]; fis.read(outAct[0]);
                                } catch (Exception e) {}
                            }
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
                        for (File f : allFiles) { if (f.getName().equalsIgnoreCase(justName)) return f; }
                    }
                }
            } catch (Exception e) { }
        }
        return null;
    }

    private View buildAssetCard(final String name, final File sffFile, Bitmap previewBmp, String sffVersion, final byte[] actData) {
        LinearLayout card = new LinearLayout(getContext()); card.setOrientation(LinearLayout.VERTICAL); card.setGravity(Gravity.CENTER);
        card.setPadding((int)(10*density), (int)(10*density), (int)(10*density), (int)(10*density));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#2D2D30")); bg.setCornerRadius(8f*density); bg.setStroke(1, Color.parseColor("#3F3F46")); card.setBackground(bg);

        ImageView previewView = new ImageView(getContext());
        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams((int)(90*density), (int)(90*density));
        previewView.setLayoutParams(imgParams); previewView.setScaleType(ImageView.ScaleType.FIT_CENTER); previewView.setBackgroundColor(Color.parseColor("#1E1E1E"));
        
        if (previewBmp != null) previewView.setImageBitmap(previewBmp); else previewView.setImageResource(android.R.drawable.ic_menu_gallery); 
        card.addView(previewView);

        TextView nameText = new TextView(getContext()); nameText.setText(name); nameText.setSingleLine(true); nameText.setGravity(Gravity.CENTER); nameText.setPadding(0, (int)(8*density), 0, (int)(2*density)); applyGlobalFontSettings(nameText, 0.9f, false);
        card.addView(nameText);
        
        TextView verText = new TextView(getContext()); verText.setText(sffVersion); verText.setSingleLine(true); verText.setGravity(Gravity.CENTER); verText.setPadding(0, 0, 0, (int)(8*density)); applyGlobalFontSettings(verText, 0.7f, false); verText.setTextColor(Color.GRAY);
        card.addView(verText);

        Button exportBtn = createButton("👁️ 打开查看器", "#0078D7"); exportBtn.setPadding(0, (int)(5*density), 0, (int)(5*density));
        exportBtn.setOnClickListener(v -> {
            if (sffFile != null && sffFile.exists()) showAssetViewerWindow(name, sffFile, actData); 
            else Toast.makeText(getContext(), "资源读取失败", Toast.LENGTH_SHORT).show();
        });
        card.addView(exportBtn); return card;
    }

    private String sniffSffVersion(File sffFile) {
        if (sffFile == null || !sffFile.exists()) return "状态: 文件丢失";
        try {
            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(sffFile, "r"); byte[] signature = new byte[12]; raf.read(signature);
            String sigStr = new String(signature).trim();
            if (!sigStr.equals("Elecbyte")) { raf.close(); return "未知格式"; }
            byte[] verBytes = new byte[4]; raf.seek(12); raf.read(verBytes); raf.close();
            if (verBytes[3] == 2 && verBytes[2] == 0) return "SFF v2.0"; else if (verBytes[3] == 1) return "SFF v1.01"; else return "SFF v" + verBytes[3] + "." + verBytes[2];
        } catch (Exception e) { return "文件异常"; }
    }

    public static class SffFrame {
        public int offset; public int length; public int group; public int item; public int width; public int height;
        public int format; public int colorDepth; public int palIndex; public boolean sharedPal; public Bitmap cachedBmp; public boolean isV2; public int linkedSpriteIndex = -1; 
    }

    private byte[] smartZlibUnwrap(byte[] input) {
        if (input == null || input.length == 0) return input;
        try {
            java.util.zip.Inflater inflater = new java.util.zip.Inflater(); inflater.setInput(input);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(1024);
            byte[] buf = new byte[1024];
            while (!inflater.finished()) {
                int count = inflater.inflate(buf);
                if (count == 0) { if (inflater.needsInput() || inflater.needsDictionary()) break; }
                bos.write(buf, 0, count);
            }
            inflater.end(); byte[] res = bos.toByteArray();
            if (res.length >= 16) return res; 
        } catch (Exception e) {}
        return input; 
    }

    private List<SffFrame> scanSffFrames(File sffFile, byte[] actData, byte[][] outV2Palettes, byte[] outGlobalSharedPalette) {
        List<SffFrame> frameList = new ArrayList<>();
        if (sffFile == null || !sffFile.exists() || sffFile.length() < 128) return frameList;

        for (int i=0; i<256; i++) {
            for (int c=0; c<256; c++) {
                outV2Palettes[i][c*4] = (byte)c; outV2Palettes[i][c*4+1] = (byte)c; outV2Palettes[i][c*4+2] = (byte)c; outV2Palettes[i][c*4+3] = (byte)255;
            }
        }

        if (actData != null && actData.length == 768) {
            System.arraycopy(actData, 0, outGlobalSharedPalette, 0, 768);
            for (int p=0; p<256; p++) {
                for (int c=0; c<256; c++) {
                    outV2Palettes[p][c*4] = actData[c*3]; outV2Palettes[p][c*4+1] = actData[c*3+1]; outV2Palettes[p][c*4+2] = actData[c*3+2]; outV2Palettes[p][c*4+3] = (byte)255;
                }
            }
        }

        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(sffFile, "r")) {
            byte[] sig = new byte[8]; raf.read(sig);
            if (!new String(sig, "US-ASCII").equals("Elecbyte")) return frameList;

            raf.seek(12); byte[] ver = new byte[4]; raf.read(ver);
            boolean isV2 = (ver[3] == 2 && ver[2] == 0); 

            if (isV2) {
                raf.seek(36); int spriteNodeOffset = Integer.reverseBytes(raf.readInt()); raf.seek(40); int numSprites = Integer.reverseBytes(raf.readInt());
                raf.seek(44); int palNodeOffset = Integer.reverseBytes(raf.readInt()); raf.seek(48); int numPalettes = Integer.reverseBytes(raf.readInt());
                raf.seek(52); int ldataOffset = Integer.reverseBytes(raf.readInt()); raf.seek(60); int tdataOffset = Integer.reverseBytes(raf.readInt());

                if (numSprites < 0 || numSprites > 90000) return frameList;

                if (numPalettes > 0) {
                    for(int p=0; p<numPalettes && p<256; p++) {
                        raf.seek(palNodeOffset + p * 16 + 6); short linked = Short.reverseBytes(raf.readShort());
                        int pDataOffset = Integer.reverseBytes(raf.readInt()); int pDataLength = Integer.reverseBytes(raf.readInt());
                        if (linked != 0) {
                            if (pDataOffset >= 0 && pDataOffset < p) System.arraycopy(outV2Palettes[pDataOffset], 0, outV2Palettes[p], 0, 1024);
                        } 
                        else if (pDataLength > 0 && pDataLength <= 4096) {
                            raf.seek(ldataOffset + pDataOffset); byte[] v2palRaw = new byte[pDataLength]; raf.read(v2palRaw);
                            if (pDataLength == 1024) {
                                for(int c=0; c<256; c++) {
                                    outV2Palettes[p][c*4]   = v2palRaw[c*4]; outV2Palettes[p][c*4+1] = v2palRaw[c*4+1];
                                    outV2Palettes[p][c*4+2] = v2palRaw[c*4+2]; outV2Palettes[p][c*4+3] = v2palRaw[c*4+3]; 
                                }
                            } else {
                                byte[] cleanPal = smartZlibUnwrap(v2palRaw); int colorsToRead = Math.min(256, cleanPal.length / 4);
                                for(int c=0; c<colorsToRead; c++) {
                                    outV2Palettes[p][c*4]   = cleanPal[c*4]; outV2Palettes[p][c*4+1] = cleanPal[c*4+1];
                                    outV2Palettes[p][c*4+2] = cleanPal[c*4+2]; outV2Palettes[p][c*4+3] = cleanPal[c*4+3]; 
                                }
                            }
                        }
                    }
                }

                for (int i = 0; i < numSprites; i++) {
                    raf.seek(spriteNodeOffset + i * 28);
                    short group = Short.reverseBytes(raf.readShort()); short item = Short.reverseBytes(raf.readShort());
                    short width = Short.reverseBytes(raf.readShort()); short height = Short.reverseBytes(raf.readShort());
                    raf.skipBytes(4); 
                    
                    short linked = Short.reverseBytes(raf.readShort()); byte format = raf.readByte(); byte depth = raf.readByte(); 
                    int dataOffset = Integer.reverseBytes(raf.readInt()); int dataLength = Integer.reverseBytes(raf.readInt());
                    short palIdx = Short.reverseBytes(raf.readShort()); short flags = Short.reverseBytes(raf.readShort());

                    SffFrame frame = new SffFrame(); frame.isV2 = true; frame.group = group; frame.item = item;
                    frame.width = width; frame.height = height; frame.format = format; frame.colorDepth = depth; frame.palIndex = palIdx;
                    
                    if (linked != 0) { frame.offset = -1; frame.length = 0; frame.linkedSpriteIndex = dataOffset; } 
                    else if (dataLength > 0 && width > 0 && height > 0) { frame.offset = ((flags & 1) != 0 ? tdataOffset : ldataOffset) + dataOffset; frame.length = dataLength; } 
                    else { continue; }
                    frameList.add(frame);
                }
                
                for (SffFrame f : frameList) {
                    if (f.linkedSpriteIndex >= 0) {
                        SffFrame target = f; int guard = 0;
                        while (target.linkedSpriteIndex >= 0 && guard < 100) {
                            if (target.linkedSpriteIndex >= frameList.size()) break;
                            target = frameList.get(target.linkedSpriteIndex); guard++;
                        }
                        f.offset = target.offset; f.length = target.length; f.format = target.format; f.colorDepth = target.colorDepth;
                        if (f.width <= 0) f.width = target.width; if (f.height <= 0) f.height = target.height;
                    }
                }

            } else {
                raf.seek(20); int totalImages = Integer.reverseBytes(raf.readInt()); raf.seek(24); int nextOffset = Integer.reverseBytes(raf.readInt());
                int currentIndex = 0; boolean foundGlobalPal = false;
                int lastValidOffset = -1; int lastValidLength = -1; int lastValidWidth = -1; int lastValidHeight = -1;

                while (nextOffset > 0 && currentIndex < totalImages && currentIndex < 90000) {
                    raf.seek(nextOffset);
                    int nextSub = Integer.reverseBytes(raf.readInt()); int length = Integer.reverseBytes(raf.readInt());
                    short x = Short.reverseBytes(raf.readShort()); short y = Short.reverseBytes(raf.readShort());
                    short group = Short.reverseBytes(raf.readShort()); short item = Short.reverseBytes(raf.readShort());
                    short linked = Short.reverseBytes(raf.readShort()); byte sharedPal = raf.readByte();

                    SffFrame frame = new SffFrame(); frame.isV2 = false; frame.colorDepth = 8; frame.group = group; frame.item = item; frame.sharedPal = (sharedPal != 0);
                    
                    if (linked != 0) {
                        frame.offset = lastValidOffset; frame.length = lastValidLength; frame.width = lastValidWidth; frame.height = lastValidHeight; frameList.add(frame);
                    } else if (length > 128) {
                        raf.seek(nextOffset + 32 + 4);
                        int xmin = Short.reverseBytes(raf.readShort()) & 0xFFFF; int ymin = Short.reverseBytes(raf.readShort()) & 0xFFFF;
                        int xmax = Short.reverseBytes(raf.readShort()) & 0xFFFF; int ymax = Short.reverseBytes(raf.readShort()) & 0xFFFF;
                        
                        frame.width = xmax - xmin + 1; frame.height = ymax - ymin + 1; frame.offset = nextOffset; frame.length = length;
                        frameList.add(frame);

                        lastValidOffset = frame.offset; lastValidLength = frame.length; lastValidWidth = frame.width; lastValidHeight = frame.height;

                        if (!foundGlobalPal && length >= 768) {
                            long palOffset = nextOffset + 32 + length - 768; 
                            if (palOffset > 0 && palOffset <= raf.length()) {
                                long oldPos = raf.getFilePointer(); raf.seek(palOffset - 1);
                                if (raf.readByte() == 0x0C) { raf.read(outGlobalSharedPalette); foundGlobalPal = true; }
                                raf.seek(oldPos);
                            }
                        }
                    }
                    nextOffset = nextSub; currentIndex++;
                }
            }
        } catch (Throwable t) { t.printStackTrace(); }
        return frameList;
    }

    private Bitmap decodeSingleFrame(File sffFile, SffFrame frame, byte[][] v2Palettes, byte[] globalSharedPalette) {
        if (frame.cachedBmp != null) return frame.cachedBmp;
        if (frame.offset < 0 || frame.width <= 0 || frame.height <= 0 || frame.length <= 0) return null; 

        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(sffFile, "r")) {
            byte[] rawData = new byte[frame.length];
            raf.seek(frame.offset + (frame.isV2 ? 0 : 32)); raf.read(rawData);

            if (frame.isV2) {
                if (frame.format >= 10 && frame.format <= 12) {
                    int pngStart = -1;
                    for (int j = 0; j < Math.min(128, rawData.length - 8); j++) {
                        if (rawData[j] == (byte)137 && rawData[j+1] == 80 && rawData[j+2] == 78 && rawData[j+3] == 71) { pngStart = j; break; }
                    }
                    if (pngStart != -1) {
                        byte[] finalPngData = rawData; int finalStart = pngStart; int finalLen = rawData.length - pngStart;
                        if (frame.format == 10 && rawData.length >= pngStart + 33) {
                            boolean hasPlte = false;
                            for (int i = pngStart; i < rawData.length - 4; i++) {
                                if (rawData[i] == 'P' && rawData[i+1] == 'L' && rawData[i+2] == 'T' && rawData[i+3] == 'E') { hasPlte = true; break; }
                            }
                            boolean isIndexed = (rawData[pngStart + 25] == 3);
                            if (isIndexed && !hasPlte) {
                                try {
                                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                                    bos.write(rawData, pngStart, 33); 
                                    bos.write(new byte[]{0, 0, 3, 0}); bos.write(new byte[]{'P', 'L', 'T', 'E'});
                                    byte[] palData = new byte[768]; byte[] targetPal = (frame.palIndex >= 0 && frame.palIndex < 256) ? v2Palettes[frame.palIndex] : v2Palettes[0];
                                    if (targetPal == null) targetPal = new byte[1024];
                                    for (int p = 0; p < 256; p++) { palData[p*3] = targetPal[p*4]; palData[p*3+1] = targetPal[p*4+1]; palData[p*3+2] = targetPal[p*4+2]; }
                                    bos.write(palData);
                                    java.util.zip.CRC32 crc = new java.util.zip.CRC32(); crc.update(new byte[]{'P', 'L', 'T', 'E'}); crc.update(palData);
                                    int crcVal = (int) crc.getValue(); bos.write(new byte[]{(byte)(crcVal>>>24), (byte)(crcVal>>>16), (byte)(crcVal>>>8), (byte)crcVal});
                                    bos.write(new byte[]{0, 0, 0, 1}); bos.write(new byte[]{'t', 'R', 'N', 'S'}); bos.write(new byte[]{0}); 
                                    java.util.zip.CRC32 crcTrns = new java.util.zip.CRC32(); crcTrns.update(new byte[]{'t', 'R', 'N', 'S', 0});
                                    int trnsCrc = (int) crcTrns.getValue(); bos.write(new byte[]{(byte)(trnsCrc>>>24), (byte)(trnsCrc>>>16), (byte)(trnsCrc>>>8), (byte)trnsCrc});
                                    bos.write(rawData, pngStart + 33, rawData.length - pngStart - 33);
                                    finalPngData = bos.toByteArray(); finalStart = 0; finalLen = finalPngData.length;
                                } catch (Exception e) { }
                            }
                        }
                        Bitmap pngBmp = BitmapFactory.decodeByteArray(finalPngData, finalStart, finalLen);
                        if (pngBmp != null) { frame.cachedBmp = pngBmp; return pngBmp; }
                    }
                    return null; 
                }

                byte[] palData = new byte[1024]; byte[] targetPal = (frame.palIndex >= 0 && frame.palIndex < 256) ? v2Palettes[frame.palIndex] : v2Palettes[0];
                if (targetPal != null) System.arraycopy(targetPal, 0, palData, 0, Math.min(targetPal.length, 1024));
                int[] pixels = decodeSffV2C(rawData, frame.format, frame.width, frame.height, frame.colorDepth, palData);
                if (pixels != null && pixels.length > 0) { Bitmap bmp = Bitmap.createBitmap(pixels, frame.width, frame.height, Bitmap.Config.ARGB_8888); frame.cachedBmp = bmp; return bmp; }
            } else {
                byte[] palette = new byte[768];
                if (!frame.sharedPal) {
                    long palOffset = frame.offset + 32 + frame.length - 768; 
                    if (palOffset > 0 && palOffset <= raf.length()) {
                        raf.seek(palOffset - 1); if (raf.readByte() == 0x0C) { raf.read(palette); System.arraycopy(palette, 0, globalSharedPalette, 0, 768); }
                    }
                } else { System.arraycopy(globalSharedPalette, 0, palette, 0, 768); }
                int[] pixels = decodeSffV1C(rawData, frame.width, frame.height, palette);
                if (pixels != null && pixels.length > 0) { Bitmap finalBmp = Bitmap.createBitmap(pixels, frame.width, frame.height, Bitmap.Config.ARGB_8888); frame.cachedBmp = finalBmp; return finalBmp; }
            }
        } catch (Throwable t) { return null; }
        return null;
    }

    private Bitmap extractPreviewFromSff(File sffFile, byte[] actData) {
        byte[][] v2Palettes = new byte[256][1024]; byte[] globalSharedPalette = new byte[768];
        List<SffFrame> frames = scanSffFrames(sffFile, actData, v2Palettes, globalSharedPalette);
        if (frames.isEmpty()) return createTextBitmap(sffFile.getName(), "文件损坏或格式受限");
        
        SffFrame bestFrame = null;
        for (SffFrame f : frames) { 
            if (f.group == 9000 && f.item == 1) { Bitmap bmp = decodeSingleFrame(sffFile, f, v2Palettes, globalSharedPalette); if (bmp != null) return bmp; }
            if (f.group == 9000 && f.item == 0) bestFrame = f; 
        }
        if (bestFrame != null) { Bitmap bmp = decodeSingleFrame(sffFile, bestFrame, v2Palettes, globalSharedPalette); if (bmp != null) return bmp; }
        for (SffFrame f : frames) { Bitmap bmp = decodeSingleFrame(sffFile, f, v2Palettes, globalSharedPalette); if (bmp != null) return bmp; }
        return createTextBitmap(sffFile.getName(), "全帧解析受阻");
    }

    private float spacing(MotionEvent event) { float x = event.getX(0) - event.getX(1); float y = event.getY(0) - event.getY(1); return (float) Math.sqrt(x * x + y * y); }
    private void midPoint(PointF point, MotionEvent event) { float x = event.getX(0) + event.getX(1); float y = event.getY(0) + event.getY(1); point.set(x / 2, y / 2); }

    private void showAssetViewerWindow(String charName, File sffFile, byte[] actData) {
        final String winTitle = "🎨 检视: " + charName;
        final byte[][] scopeV2Palettes = new byte[256][1024]; final byte[] scopeGlobalPalette = new byte[768];
        final List<SffFrame> allFrames = scanSffFrames(sffFile, actData, scopeV2Palettes, scopeGlobalPalette);
        
        LinearLayout root = new LinearLayout(getContext()); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#1E1E1E"));

        List<String> groupListDisplay = new ArrayList<>(); List<Integer> groupList = new ArrayList<>();
        groupListDisplay.add("📂 所有动作帧 (顺序总览)"); groupList.add(-999); 
        for (SffFrame f : allFrames) { if (!groupList.contains(f.group)) { groupList.add(f.group); groupListDisplay.add("📁 动作组: " + f.group); } }
        
        final List<SffFrame> currentGroupFrames = new ArrayList<>();
        final int[] currentFrameIndex = {0}; final boolean[] isPlaying = {false}; 

        LinearLayout topBar = new LinearLayout(getContext()); topBar.setOrientation(LinearLayout.HORIZONTAL); topBar.setGravity(Gravity.CENTER_VERTICAL); topBar.setBackgroundColor(Color.parseColor("#2D2D30")); topBar.setPadding((int)(10*density), (int)(8*density), (int)(10*density), (int)(8*density));
        Spinner groupSpinner = new Spinner(getContext()); ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, groupListDisplay); groupSpinner.setAdapter(adapter); topBar.addView(groupSpinner, new LinearLayout.LayoutParams(0, -2, 1f)); root.addView(topBar);

        TextView infoText = new TextView(getContext()); infoText.setPadding((int)(10*density), (int)(8*density), (int)(10*density), (int)(4*density)); applyGlobalFontSettings(infoText, 0.85f, false); infoText.setTextColor(Color.parseColor("#0078D7")); root.addView(infoText);

        FrameLayout canvasFrame = new FrameLayout(getContext()); LinearLayout.LayoutParams canvasParams = new LinearLayout.LayoutParams(-1, 0, 1f); canvasParams.setMargins((int)(15*density), (int)(10*density), (int)(15*density), (int)(10*density)); canvasFrame.setLayoutParams(canvasParams);
        
        Bitmap bgBmp = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888); Canvas bgCanvas = new Canvas(bgBmp); Paint bgPaint = new Paint(); bgPaint.setColor(Color.parseColor("#181818")); bgCanvas.drawRect(0,0,10,10,bgPaint); bgCanvas.drawRect(10,10,20,20,bgPaint); bgPaint.setColor(Color.parseColor("#252526")); bgCanvas.drawRect(10,0,20,10,bgPaint); bgCanvas.drawRect(0,10,10,20,bgPaint);
        android.graphics.drawable.BitmapDrawable tileBg = new android.graphics.drawable.BitmapDrawable(getContext().getResources(), bgBmp); tileBg.setTileModeXY(android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT); 
        android.graphics.drawable.GradientDrawable canvasBorder = new android.graphics.drawable.GradientDrawable(); canvasBorder.setStroke((int)(1*density), Color.parseColor("#3F3F46")); canvasFrame.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{tileBg, canvasBorder}));

        // 🔥 双指放缩引擎
        final ImageView previewImg = new ImageView(getContext()); previewImg.setScaleType(ImageView.ScaleType.MATRIX); canvasFrame.addView(previewImg, new FrameLayout.LayoutParams(-1, -1)); root.addView(canvasFrame);
        final Matrix imageMatrix = new Matrix(); final Matrix savedMatrix = new Matrix(); final int[] touchMode = {0}; final PointF startPoint = new PointF(); final PointF midPoint = new PointF(); final float[] oldDist = {1f}; final boolean[] isMatrixInitialized = {false};

        canvasFrame.setOnTouchListener((v, event) -> {
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN: savedMatrix.set(imageMatrix); startPoint.set(event.getX(), event.getY()); touchMode[0] = 1; break;
                case MotionEvent.ACTION_POINTER_DOWN: oldDist[0] = spacing(event); if (oldDist[0] > 10f) { savedMatrix.set(imageMatrix); midPoint(midPoint, event); touchMode[0] = 2; } break;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_POINTER_UP: touchMode[0] = 0; break;
                case MotionEvent.ACTION_MOVE:
                    if (touchMode[0] == 1) { imageMatrix.set(savedMatrix); imageMatrix.postTranslate(event.getX() - startPoint.x, event.getY() - startPoint.y); } 
                    else if (touchMode[0] == 2) { float newDist = spacing(event); if (newDist > 10f) { imageMatrix.set(savedMatrix); float scale = newDist / oldDist[0]; imageMatrix.postScale(scale, scale, midPoint.x, midPoint.y); } }
                    break;
            }
            previewImg.setImageMatrix(imageMatrix); return true;
        });

        // 🔥 释放空间的控制区
        LinearLayout controls = new LinearLayout(getContext()); controls.setOrientation(LinearLayout.HORIZONTAL); controls.setGravity(Gravity.CENTER); controls.setPadding((int)(15*density), 0, (int)(15*density), (int)(15*density));
        
        Button btnPrev = createButton("⏪", "#3F3F46"); 
        Button btnPlay = createButton("▶️ 播放", "#0078D7"); 
        Button btnNext = createButton("⏭️", "#3F3F46"); 
        Button btnSpeed = createButton("⚙️ 调速", "#3F3F46"); 
        Button btnExportPng = createButton("💾 导出", "#3F3F46"); 
        
        LinearLayout.LayoutParams btnP = new LinearLayout.LayoutParams(0, -2, 1f); btnP.setMargins((int)(2*density), 0, (int)(2*density), 0);
        
        final int[] currentDelay = {16}; // 默认 60fps
        btnSpeed.setOnClickListener(v -> {
            final Dialog spdDialog = new Dialog(getContext()); spdDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            LinearLayout spdLayout = new LinearLayout(getContext()); spdLayout.setOrientation(LinearLayout.VERTICAL); spdLayout.setBackgroundColor(Color.parseColor("#2D2D30")); spdLayout.setPadding((int)(20*density), (int)(20*density), (int)(20*density), (int)(20*density));
            TextView title = new TextView(getContext()); title.setText("调整播放速度 (FPS)"); applyGlobalFontSettings(title, 1.0f, true); title.setGravity(Gravity.CENTER); spdLayout.addView(title);
            SeekBar speedBar = new SeekBar(getContext()); speedBar.setMax(59); speedBar.setProgress((1000/currentDelay[0])-1);
            speedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar s, int p, boolean b) { currentDelay[0] = 1000 / (p + 1); title.setText("调整播放速度: " + (p+1) + " FPS"); }
                public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
            });
            spdLayout.addView(speedBar, new LinearLayout.LayoutParams((int)(250*density), -2));
            spdDialog.setContentView(spdLayout); spdDialog.show();
        });

        final android.os.Handler uiHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        Runnable updateFrameAction = () -> {
            if (currentGroupFrames.isEmpty()) return;
            if (currentFrameIndex[0] < 0) currentFrameIndex[0] = currentGroupFrames.size() - 1;
            if (currentFrameIndex[0] >= currentGroupFrames.size()) currentFrameIndex[0] = 0;
            final SffFrame targetFrame = currentGroupFrames.get(currentFrameIndex[0]);
            new Thread(() -> {
                final Bitmap bmp = decodeSingleFrame(sffFile, targetFrame, scopeV2Palettes, scopeGlobalPalette);
                uiHandler.post(() -> {
                    if (bmp != null) {
                        previewImg.setImageBitmap(bmp);
                        if (!isMatrixInitialized[0] && canvasFrame.getWidth() > 0) {
                            float scale = Math.min((float)canvasFrame.getWidth() / bmp.getWidth(), (float)canvasFrame.getHeight() / bmp.getHeight());
                            if (scale > 1.5f) scale = 1.5f; float dx = (canvasFrame.getWidth() - bmp.getWidth() * scale) / 2f; float dy = (canvasFrame.getHeight() - bmp.getHeight() * scale) / 2f;
                            imageMatrix.setScale(scale, scale); imageMatrix.postTranslate(dx, dy); previewImg.setImageMatrix(imageMatrix); isMatrixInitialized[0] = true;
                        }
                    }
                    infoText.setText(String.format("帧: %d / %d | 动作: %d | 索引: %d | 尺寸: %dx%d", currentFrameIndex[0] + 1, currentGroupFrames.size(), targetFrame.group, targetFrame.item, targetFrame.width, targetFrame.height));
                });
            }).start();
        };

        canvasFrame.post(updateFrameAction);

        groupSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                int selectedGroup = groupList.get(position); currentGroupFrames.clear();
                if (selectedGroup == -999) currentGroupFrames.addAll(allFrames); else for (SffFrame f : allFrames) { if (f.group == selectedGroup) currentGroupFrames.add(f); }
                currentFrameIndex[0] = 0; updateFrameAction.run();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        Handler playHandler = new Handler();
        Runnable playRunnable = new Runnable() {
            @Override public void run() {
                if (isPlaying[0] && !currentGroupFrames.isEmpty()) {
                    currentFrameIndex[0]++; updateFrameAction.run(); playHandler.postDelayed(this, currentDelay[0]); 
                }
            }
        };

        btnPrev.setOnClickListener(v -> { currentFrameIndex[0]--; updateFrameAction.run(); }); btnNext.setOnClickListener(v -> { currentFrameIndex[0]++; updateFrameAction.run(); });
        btnPlay.setOnClickListener(v -> {
            if (currentGroupFrames.isEmpty()) return; isPlaying[0] = !isPlaying[0];
            btnPlay.setText(isPlaying[0] ? "⏸️ 暂停" : "▶️ 播放"); btnPlay.setBackgroundColor(isPlaying[0] ? Color.parseColor("#E81123") : Color.parseColor("#0078D7"));
            if (isPlaying[0]) playHandler.post(playRunnable); else playHandler.removeCallbacksAndMessages(null);
        });

        btnExportPng.setOnClickListener(v -> {
            if(currentGroupFrames.isEmpty()) return; SffFrame f = currentGroupFrames.get(currentFrameIndex[0]);
            new Thread(() -> {
                Bitmap bmp = decodeSingleFrame(sffFile, f, scopeV2Palettes, scopeGlobalPalette);
                if (bmp != null) {
                    try {
                        File outDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "IkemenExports"); if (!outDir.exists()) outDir.mkdirs();
                        File outFile = new File(outDir, charName + "_G" + f.group + "_I" + f.item + ".png"); java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile);
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, fos); fos.close();
                        uiHandler.post(() -> Toast.makeText(getContext(), "✅ 已导出: " + outFile.getAbsolutePath(), Toast.LENGTH_LONG).show());
                    } catch (Exception e) {}
                }
            }).start();
        });

        controls.addView(btnPrev, btnP); controls.addView(btnPlay, btnP); controls.addView(btnNext, btnP); controls.addView(btnSpeed, btnP); controls.addView(btnExportPng, btnP); 
        root.addView(controls);
        
        openAppWindow(winTitle, root, () -> {
            isPlaying[0] = false; playHandler.removeCallbacksAndMessages(null); 
            for (SffFrame f : allFrames) { if (f.cachedBmp != null) { f.cachedBmp.recycle(); f.cachedBmp = null; } }
            View win = windowsLayer.findViewWithTag(winTitle); if (win != null) windowsLayer.removeView(win);
            View tbBtn = taskbarAppsLayout.findViewWithTag("tb_" + winTitle); if (tbBtn != null) taskbarAppsLayout.removeView(tbBtn);
        });
        if (!groupList.isEmpty()) groupSpinner.setSelection(0);
    }

    // ======================================================================================
    // 🎵 核心模块 2：SND 音频检视工坊
    // ======================================================================================
    private View buildSndExtractorContent() {
        LinearLayout root = new LinearLayout(getContext()); root.setOrientation(LinearLayout.VERTICAL); root.setPadding((int)(15*density), (int)(15*density), (int)(15*density), (int)(15*density));
        LinearLayout topBar = new LinearLayout(getContext()); topBar.setOrientation(LinearLayout.HORIZONTAL); topBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView statusText = new TextView(getContext()); statusText.setText(" 状态: 等待选取 .snd 文件..."); applyGlobalFontSettings(statusText, 1.0f, false);
        Button scanBtn = createButton("📂 浏览并选择 SND 音频文件", "#FF9800"); LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(-2, -2); btnParams.setMargins(0, 0, (int)(15*density), 0);
        topBar.addView(scanBtn, btnParams); topBar.addView(statusText); root.addView(topBar);

        ScrollView scroll = new ScrollView(getContext()); LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, -1); scrollParams.setMargins(0, (int)(15*density), 0, 0); scroll.setLayoutParams(scrollParams);
        final LinearLayout listLayout = new LinearLayout(getContext()); listLayout.setOrientation(LinearLayout.VERTICAL); scroll.addView(listLayout); root.addView(scroll);

        scanBtn.setOnClickListener(v -> {
            currentGalleryLayout = listLayout; currentStatusText = statusText; showWin10FilePicker("选择 .snd 音频包", 5, null, null);
        });
        return root;
    }

    private static class SndNode { int group; int item; int offset; int length; }

    private void startSndScanner(File sndFile) {
        if (currentGalleryLayout != null) currentGalleryLayout.removeAllViews(); currentStatusText.setText("状态: 正在底层解构 SND 链表...");
        new Thread(() -> {
            try {
                byte[] fileData = new byte[(int)sndFile.length()]; FileInputStream fis = new FileInputStream(sndFile); fis.read(fileData); fis.close();
                int[] rawNodes = scanSndC(fileData);
                if (rawNodes == null || rawNodes.length == 0) { updateUI(currentStatusText, "❌ 无法解析，可能非有效 ElecbyteSnd"); return; }
                
                List<SndNode> nodes = new ArrayList<>();
                for (int i = 0; i < rawNodes.length / 4; i++) {
                    SndNode node = new SndNode(); node.group = rawNodes[i*4]; node.item = rawNodes[i*4+1]; node.offset = rawNodes[i*4+2]; node.length = rawNodes[i*4+3];
                    nodes.add(node);
                }
                updateUI(currentStatusText, "✅ 解析成功: " + sndFile.getName());
                
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    showSndViewerWindow(sndFile.getName(), fileData, nodes);
                });
            } catch (Exception e) { updateUI(currentStatusText, "解析异常: " + e.getMessage()); }
        }).start();
    }

    private void showSndViewerWindow(String sndName, final byte[] rawData, List<SndNode> allNodes) {
        final String winTitle = "🎵 检视: " + sndName;
        LinearLayout root = new LinearLayout(getContext()); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#1E1E1E"));

        List<String> groupListDisplay = new ArrayList<>(); List<Integer> groupList = new ArrayList<>();
        groupListDisplay.add("📂 所有音频"); groupList.add(-999);
        for (SndNode n : allNodes) { if (!groupList.contains(n.group)) { groupList.add(n.group); groupListDisplay.add("📁 音频组: " + n.group); } }

        LinearLayout topBar = new LinearLayout(getContext()); topBar.setOrientation(LinearLayout.HORIZONTAL); topBar.setGravity(Gravity.CENTER_VERTICAL); topBar.setBackgroundColor(Color.parseColor("#2D2D30")); topBar.setPadding((int)(10*density), (int)(8*density), (int)(10*density), (int)(8*density));
        Spinner groupSpinner = new Spinner(getContext()); ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, groupListDisplay); groupSpinner.setAdapter(adapter); topBar.addView(groupSpinner, new LinearLayout.LayoutParams(0, -2, 1f)); root.addView(topBar);

        ScrollView scroll = new ScrollView(getContext()); LinearLayout listLayout = new LinearLayout(getContext()); listLayout.setOrientation(LinearLayout.VERTICAL); scroll.addView(listLayout, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1f); scrollParams.setMargins((int)(10*density), (int)(10*density), (int)(10*density), (int)(10*density)); root.addView(scroll, scrollParams);

        Runnable refreshList = () -> {
            listLayout.removeAllViews(); int selectedGroup = groupList.get(groupSpinner.getSelectedItemPosition());
            for (SndNode n : allNodes) {
                if (selectedGroup != -999 && n.group != selectedGroup) continue;
                
                LinearLayout row = new LinearLayout(getContext()); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding((int)(10*density), (int)(10*density), (int)(10*density), (int)(10*density));
                GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#2D2D30")); bg.setCornerRadius(8f*density); row.setBackground(bg);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2); rowParams.setMargins(0, 0, 0, (int)(8*density));
                
                TextView info = new TextView(getContext()); info.setText(String.format("🎵 Group: %d | Item: %d\n📏 大小: %d Bytes", n.group, n.item, n.length)); applyGlobalFontSettings(info, 0.9f, false);
                row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));

                Button btnPlay = createButton("▶️ 试听", "#FF9800"); btnPlay.setPadding((int)(15*density), (int)(8*density), (int)(15*density), (int)(8*density));
                btnPlay.setOnClickListener(v -> {
                    try {
                        if (currentSndPlayer != null) { currentSndPlayer.release(); currentSndPlayer = null; }
                        File tempWav = new File(getContext().getCacheDir(), "ikemen_preview.wav"); FileOutputStream fos = new FileOutputStream(tempWav); fos.write(rawData, n.offset, n.length); fos.close();
                        currentSndPlayer = new MediaPlayer(); currentSndPlayer.setDataSource(tempWav.getAbsolutePath()); currentSndPlayer.prepare(); currentSndPlayer.start();
                    } catch (Exception e) {}
                });
                
                Button btnExport = createButton("💾 导出", "#0078D7"); LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(-2, -2); btnParams.setMargins((int)(10*density), 0, 0, 0);
                btnExport.setOnClickListener(v -> {
                    try {
                        File outDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "IkemenExports"); if (!outDir.exists()) outDir.mkdirs();
                        File outFile = new File(outDir, sndName.replace(".snd", "") + "_G" + n.group + "_I" + n.item + ".wav"); FileOutputStream fos = new FileOutputStream(outFile); fos.write(rawData, n.offset, n.length); fos.close();
                        Toast.makeText(getContext(), "✅ 已导出: " + outFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {}
                });
                
                Button btnReplace = createButton("🔄 替换", "#333333");
                btnReplace.setOnClickListener(v -> Toast.makeText(getContext(), "⚠️ 音频重定位需要重建整条 C++ 链表，当前仅支持无损提取试听！", Toast.LENGTH_LONG).show());

                row.addView(btnPlay); row.addView(btnExport, btnParams); row.addView(btnReplace, btnParams); listLayout.addView(row, rowParams);
            }
        };

        groupSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { refreshList.run(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        
        openAppWindow(winTitle, root, () -> {
            if (currentSndPlayer != null) { currentSndPlayer.release(); currentSndPlayer = null; }
            View win = windowsLayer.findViewWithTag(winTitle); if (win != null) windowsLayer.removeView(win);
            View tbBtn = taskbarAppsLayout.findViewWithTag("tb_" + winTitle); if (tbBtn != null) taskbarAppsLayout.removeView(tbBtn);
        });
    }

    static {
        try { System.loadLibrary("ikemen_sff_codec"); } 
        catch (UnsatisfiedLinkError e) { e.printStackTrace(); }
    }

    public native int[] decodeSffV2C(byte[] data, int format, int width, int height, int colorDepth, byte[] palette);
    public native int[] decodeSffV1C(byte[] data, int width, int height, byte[] palette);
    public native int[] scanSndC(byte[] data); // 🔥 C++ SND 闪电桥接
}
