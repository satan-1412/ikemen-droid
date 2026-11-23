package org.libsdl.app;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Movie;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.HashSet;
import java.util.List;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.json.JSONObject;
import android.util.Log;

import api.Api;

public class DesktopSystemView extends Dialog {
    
    // 🔥 1. 全局静态唯一实例，保证桌面的所有窗口和日志状态永驻内存！
    public static DesktopSystemView mSingleton = null;

    // 🔥 2. 拦截系统的销毁指令，改为“隐身”
    @Override
    public void dismiss() {
        this.hide(); // 仅仅隐藏界面，绝不销毁内部的窗口和联机状态！
    }
    
    @Override
    public void cancel() {
        this.hide(); 
    }
    
    @Override
    public void onBackPressed() {
        // 按系统返回键退出桌面模式时，统一走 toggleDesktopMode(false) 确保恢复横屏与游戏
        if (SDLActivity.mSingleton != null) {
            SDLActivity.mSingleton.toggleDesktopMode(false);
        } else {
            this.hide();
        }
    }

    // 留给真正需要彻底清理内存时的后门
    public void forceDestroy() {
        super.dismiss();
        mSingleton = null;
    }

    public static DesktopSystemView instance;
    private boolean isDesktopPortrait = false;
    private TextView orientationToggleBtn = null;

    public void applySavedDesktopOrientation() {
        if (prefs != null) {
            isDesktopPortrait = prefs.getBoolean("dt_portraitMode", false);
        }
        updateOrientationBtnText();
        if (SDLActivity.mSingleton != null) {
            SDLActivity.mSingleton.setDesktopOrientation(isDesktopPortrait);
        }
    }

    private void updateOrientationBtnText() {
        if (orientationToggleBtn != null) {
            orientationToggleBtn.setText(isDesktopPortrait ? L("🖥️ 切横屏") : L("📱 切竖屏"));
        }
    }

    public void onScreenOrientationChanged() {
        if (rootLayer == null) return;
        rootLayer.postDelayed(() -> {
            try {
                if (getContext() == null) return;
                int screenW = getContext().getResources().getDisplayMetrics().widthPixels;
                int screenH = getContext().getResources().getDisplayMetrics().heightPixels;
                if (screenW <= 0 || screenH <= 0) return;

                if (taskbar != null) {
                    int dynamicTaskbarHeight = Math.max((int)(40 * density), screenH / 14);
                    ViewGroup.LayoutParams tbLp = taskbar.getLayoutParams();
                    if (tbLp != null) {
                        tbLp.height = dynamicTaskbarHeight;
                        taskbar.setLayoutParams(tbLp);
                    }
                }
                int tbH = (taskbar != null && taskbar.getHeight() > 0) ? taskbar.getHeight() : Math.max((int)(40 * density), screenH / 14);

                if (windowsLayer != null) {
                    for (int i = 0; i < windowsLayer.getChildCount(); i++) {
                        View w = windowsLayer.getChildAt(i);
                        ViewGroup.LayoutParams lp = w.getLayoutParams();
                        if (lp instanceof FrameLayout.LayoutParams) {
                            FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
                            if (flp.width == FrameLayout.LayoutParams.MATCH_PARENT) {
                                flp.bottomMargin = tbH;
                                w.setLayoutParams(flp);
                                w.setX(0);
                                w.setY(0);
                            } else {
                                int newW = Math.min(flp.width > 0 ? flp.width : (int)(screenW * 0.85f), (int)(screenW * 0.92f));
                                int newH = Math.min(flp.height > 0 ? flp.height : (int)((screenH - tbH) * 0.85f), (int)((screenH - tbH) * 0.90f));
                                flp.width = Math.max((int)(220 * density), newW);
                                flp.height = Math.max((int)(220 * density), newH);
                                w.setLayoutParams(flp);
                                float clampedX = Math.max(0, Math.min(w.getX(), Math.max(0, screenW - flp.width)));
                                float clampedY = Math.max(0, Math.min(w.getY(), Math.max(0, screenH - tbH - flp.height)));
                                w.setX(clampedX);
                                w.setY(clampedY);
                            }
                        }
                    }
                }
                setupDesktopIcons();
                applyImmersiveMode(getWindow());
            } catch (Exception ignored) {}
        }, 120);
    }
    
    private void updateUI(final TextView status, final String msg) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (status != null) status.setText(msg);
        });
    }

    public interface OnFileSelectedListener { void onFileSelected(File file); }

    private Context mContext;
    private SharedPreferences prefs;
    private float baseDensity;
    private float density;
    
    public boolean forceCutoutFullscreen = false;
    public float uiScale = 1.0f;

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
    private MediaPlayer currentSndPlayer = null; 
    
    public String fontPath = "";
    public Typeface customFont = null;
    public int fontColor = Color.WHITE;
    public float fontSize = 12f;
    public boolean fontShadowEnabled = true;
    public int fontShadowColor = Color.BLACK;

    private static File lastVisitedDir = Environment.getExternalStorageDirectory();
    
    // 🗺️ 地图编辑器专属全局状态，解决深度嵌套导致的变量丢失问题
    private String globalDefPath = "";
    private boolean globalIsEditMode = false;
    private String globalSffPath = "";
    public DesktopSystemView(Context context) {
        super(context, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        this.mContext = context;
        this.prefs = context.getSharedPreferences("IkemenDesktopPrefs", Context.MODE_PRIVATE);
        this.baseDensity = context.getResources().getDisplayMetrics().density;
    }

    private void applyImmersiveMode(Window window) {
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WindowManager.LayoutParams layoutParams = window.getAttributes();
                layoutParams.layoutInDisplayCutoutMode = forceCutoutFullscreen ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
                window.setAttributes(layoutParams);
            }
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

        int screenW = getContext().getResources().getDisplayMetrics().widthPixels;
        int screenH = getContext().getResources().getDisplayMetrics().heightPixels;
        int dynamicTaskbarHeight = Math.max((int)(40 * density), screenH / 14); // 动态适配：最高不超过屏幕的 1/14

        taskbar = new LinearLayout(getContext());
        taskbar.setOrientation(LinearLayout.HORIZONTAL);
        taskbar.setGravity(Gravity.CENTER_VERTICAL);
        taskbar.setPadding((int)(8*density), 0, (int)(12*density), 0);
        GradientDrawable taskbarGlass = new GradientDrawable();
        taskbarGlass.setColor(Color.argb(taskbarAlpha, 20, 24, 32));
        taskbarGlass.setStroke((int)(1 * density), Color.parseColor("#2AFFFFFF"));
        taskbar.setBackground(taskbarGlass);
        FrameLayout.LayoutParams taskbarParams = new FrameLayout.LayoutParams(-1, dynamicTaskbarHeight);
        taskbarParams.gravity = Gravity.BOTTOM;
        rootLayer.addView(taskbar, taskbarParams);

        final LinearLayout startBtn = new LinearLayout(getContext());
        startBtn.setOrientation(LinearLayout.HORIZONTAL); startBtn.setGravity(Gravity.CENTER);
        startBtn.setPadding((int)(12*density), (int)(6*density), (int)(14*density), (int)(6*density));
        GradientDrawable startNormalBg = new GradientDrawable();
        startNormalBg.setColor(Color.parseColor("#2200A4EF"));
        startNormalBg.setCornerRadius(8f * density);
        startNormalBg.setStroke((int)(1 * density), Color.parseColor("#5500A4EF"));
        GradientDrawable startPressedBg = new GradientDrawable();
        startPressedBg.setColor(Color.parseColor("#5500A4EF"));
        startPressedBg.setCornerRadius(8f * density);
        startBtn.setBackground(startNormalBg);

        TextView btnIcon = new TextView(getContext()); btnIcon.setText("⊞"); btnIcon.setTextColor(Color.parseColor("#38BDF8")); btnIcon.setTextSize(18f);
        TextView btnText = new TextView(getContext()); btnText.setText(L(" 进入游戏")); applyGlobalFontSettings(btnText, 1.1f, true);
        startBtn.addView(btnIcon); startBtn.addView(btnText);

        startBtn.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) { v.setBackground(startPressedBg); }
            else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.setBackground(startNormalBg);
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
        taskbarAppsLayout.setPadding((int)(12*density), 0, (int)(12*density), 0);
        taskbarScroll.addView(taskbarAppsLayout, new ViewGroup.LayoutParams(-2, -1));
        taskbar.addView(taskbarScroll, new LinearLayout.LayoutParams(0, -1, 1f));

        // 任务栏右侧横竖屏切换按钮（仅影响桌面模式，回游戏强制恢复横屏）
        isDesktopPortrait = prefs.getBoolean("dt_portraitMode", false);
        orientationToggleBtn = new TextView(getContext());
        updateOrientationBtnText();
        applyGlobalFontSettings(orientationToggleBtn, 0.95f, true);
        orientationToggleBtn.setGravity(Gravity.CENTER);
        orientationToggleBtn.setPadding((int)(10*density), (int)(5*density), (int)(10*density), (int)(5*density));
        GradientDrawable oriBg = new GradientDrawable();
        oriBg.setColor(Color.parseColor("#2A38BDF8"));
        oriBg.setCornerRadius(8f * density);
        oriBg.setStroke((int)(1 * density), Color.parseColor("#6638BDF8"));
        orientationToggleBtn.setBackground(oriBg);
        orientationToggleBtn.setOnClickListener(v -> {
            isDesktopPortrait = !isDesktopPortrait;
            prefs.edit().putBoolean("dt_portraitMode", isDesktopPortrait).apply();
            updateOrientationBtnText();
            if (SDLActivity.mSingleton != null) {
                SDLActivity.mSingleton.setDesktopOrientation(isDesktopPortrait);
            }
            Toast.makeText(getContext(), isDesktopPortrait ? L("📱 已切换为桌面竖屏模式 (返回游戏自动恢复横屏)") : L("🖥️ 已切换为桌面横屏模式"), Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams oriParams = new LinearLayout.LayoutParams(-2, -2);
        oriParams.setMargins((int)(6*density), 0, (int)(6*density), 0);
        taskbar.addView(orientationToggleBtn, oriParams);

        // PC 系统托盘实时时钟
        final TextView trayClock = new TextView(getContext());
        applyGlobalFontSettings(trayClock, 0.95f, true);
        trayClock.setTextColor(Color.parseColor("#E2E8F0"));
        trayClock.setGravity(Gravity.CENTER);
        trayClock.setPadding((int)(8*density), (int)(4*density), (int)(8*density), (int)(4*density));
        final java.text.SimpleDateFormat clockFmt = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
        final Runnable clockTick = new Runnable() {
            @Override public void run() {
                if (trayClock.isAttachedToWindow()) {
                    trayClock.setText(clockFmt.format(new java.util.Date()));
                    trayClock.postDelayed(this, 15000);
                }
            }
        };
        trayClock.setText(clockFmt.format(new java.util.Date()));
        trayClock.postDelayed(clockTick, 15000);
        taskbar.addView(trayClock, new LinearLayout.LayoutParams(-2, -2));

        setContentView(rootLayer);
        rootLayer.post(() -> setupDesktopIcons());
    }

    private void initMouseEngine() {
        cursorPaintFill = new Paint(Paint.ANTI_ALIAS_FLAG); cursorPaintFill.setColor(Color.WHITE); cursorPaintFill.setStyle(Paint.Style.FILL);
        cursorPaintStroke = new Paint(Paint.ANTI_ALIAS_FLAG); cursorPaintStroke.setColor(Color.BLACK); cursorPaintStroke.setStyle(Paint.Style.STROKE); cursorPaintStroke.setStrokeWidth(1.5f * density);
        cursorPath = new Path(); cursorPath.moveTo(0, 0); cursorPath.lineTo(0, 35); cursorPath.lineTo(9, 26); cursorPath.lineTo(16, 42); cursorPath.lineTo(22, 38); cursorPath.lineTo(15, 22); cursorPath.lineTo(26, 22); cursorPath.close();
        Matrix scaleMatrix = new Matrix(); scaleMatrix.setScale(density * 0.4f, density * 0.4f); cursorPath.transform(scaleMatrix);
    }

    private void findFilesRecursively(File dir, List<File> resultList, String targetExtension) {
        if (dir == null || !dir.exists() || !dir.canRead()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File f : files) {
            if (f.isDirectory()) { findFilesRecursively(f, resultList, targetExtension); } 
            else if (f.getName().toLowerCase().endsWith(targetExtension)) { resultList.add(f); }
        }
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
        Uri uri = Uri.parse("file://" + uriString); String p = uriString.toLowerCase();
        boolean isVideo = p.endsWith(".mp4") || p.endsWith(".avi") || p.endsWith(".mkv") || p.endsWith(".webm");
        boolean isGif = p.endsWith(".gif");

        FrameLayout mediaContainer = new FrameLayout(mContext); mediaContainer.setAlpha(alpha / 255f);

        if (isVideo) {
            final TextureView tv = new TextureView(mContext); mediaContainer.addView(tv, new FrameLayout.LayoutParams(-1, -1));
            tv.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                private MediaPlayer mp;
                @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
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
                            if (isDesktopBg) { bgMediaPlayer = m; if (savedVideoPositionDesk > 0) m.seekTo(savedVideoPositionDesk); } else { winMediaPlayers.add(m); if (savedVideoPositionWin > 0) m.seekTo(savedVideoPositionWin); }
                            updateMediaVolumes(); m.start();
                        });
                    } catch (Exception e) {}
                }
                @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}
                @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    if (mp != null) { 
                        if (isDesktopBg) { savedVideoPositionDesk = mp.getCurrentPosition(); bgMediaPlayer = null; } else { savedVideoPositionWin = mp.getCurrentPosition(); winMediaPlayers.remove(mp); }
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

    private boolean isGridOccupied(int col, int row, float actualGrid, View excludeView) {
        for (int i = 0; i < desktopIconsLayer.getChildCount(); i++) {
            View child = desktopIconsLayer.getChildAt(i); if (child == excludeView) continue;
            if (Math.round(child.getX() / actualGrid) == col && Math.round(child.getY() / actualGrid) == row) return true;
        } return false;
    }

    private Point findAvailableGrid(float targetX, float targetY, float actualGrid, View excludeView) {
        int maxCol = Math.max(1, (int) (rootLayer.getWidth() / actualGrid));
        int maxRow = Math.max(1, (int) ((rootLayer.getHeight() - 50*density) / actualGrid)); 
        int startCol = Math.max(0, Math.min(maxCol - 1, Math.round(targetX / actualGrid)));
        int startRow = Math.max(0, Math.min(maxRow - 1, Math.round(targetY / actualGrid)));

        if (!isGridOccupied(startCol, startRow, actualGrid, excludeView)) return new Point(startCol, startRow);

        Queue<Point> queue = new LinkedList<>(); HashSet<String> visited = new HashSet<>();
        queue.add(new Point(startCol, startRow)); visited.add(startCol + "," + startRow);
        int[][] dirs = {{0,1}, {1,0}, {0,-1}, {-1,0}, {1,1}, {-1,-1}, {1,-1}, {-1,1}};
        while (!queue.isEmpty()) {
            Point p = queue.poll(); if (!isGridOccupied(p.x, p.y, actualGrid, excludeView)) return p; 
            for (int[] d : dirs) {
                int nc = p.x + d[0]; int nr = p.y + d[1];
                if (nc >= 0 && nc < maxCol && nr >= 0 && nr < maxRow && !visited.contains(nc + "," + nr)) { visited.add(nc + "," + nr); queue.add(new Point(nc, nr)); }
            }
        } return new Point(startCol, startRow); 
    }

    private void setupDesktopIcons() {
        desktopIconsLayer.removeAllViews(); 
        createDesktopIcon("sys_settings", "⚙️", L("系统控制台"));
        createDesktopIcon("asset_extractor", "🖼️", L("SFF查看器")); 
        createDesktopIcon("palette_editor", "🎨", L("ACT色表工坊")); 
        createDesktopIcon("snd_extractor", "🎵", L("SND查看器")); 
        createDesktopIcon("gif_extractor", "🎞️", L("GIF拆解器")); 
        createDesktopIcon("stage_editor", "🗺️", L("地图编辑器")); 
        createDesktopIcon("text_decipher", "🔣", L("乱码解字板")); 
        createDesktopIcon("def_scanner", "🗂️", L("DEF生成器"));
        createDesktopIcon("clsn_editor", "⚔️", L("判定框工坊"));
        createDesktopIcon("ikemen_dict", "📚", L("引擎词典"));
    }

    private void createDesktopIcon(final String id, String iconStr, String name) {
        final LinearLayout iconLayout = new LinearLayout(getContext()); iconLayout.setOrientation(LinearLayout.VERTICAL); iconLayout.setGravity(Gravity.CENTER);
        float actualGrid = gridSizeBase * density; float iconSize = actualGrid - 2f * density; 
        
        TextView iconView = new TextView(getContext()); iconView.setText(iconStr); iconView.setTextSize(26f); iconView.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#44000000")); bg.setCornerRadius(6f*density); 
        iconView.setBackground(bg); iconLayout.addView(iconView, new LinearLayout.LayoutParams((int)(iconSize*0.6f), (int)(iconSize*0.6f)));
        
        TextView nameView = new TextView(getContext()); nameView.setText(name); applyGlobalFontSettings(nameView, 1.0f, false); nameView.setSingleLine(true);
        iconLayout.addView(nameView, new LinearLayout.LayoutParams(-2, -2)); iconLayout.setLayoutParams(new FrameLayout.LayoutParams((int)iconSize, (int)iconSize));
        desktopIconsLayer.addView(iconLayout);

        float savedX = prefs.getFloat("icon_x_" + id, actualGrid * 0.2f); float savedY = prefs.getFloat("icon_y_" + id, actualGrid * 0.2f);
        Point safePoint = findAvailableGrid(savedX, savedY, actualGrid, iconLayout);
        iconLayout.setX(safePoint.x * actualGrid + (actualGrid - iconSize)/2f); iconLayout.setY(safePoint.y * actualGrid + (actualGrid - iconSize)/2f);

        iconLayout.setOnTouchListener(new View.OnTouchListener() {
            private float startRawX, startRawY, offsetX, offsetY; private boolean isDragging = false; private long lastClickTime = 0;
            @Override public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    startRawX = event.getRawX(); startRawY = event.getRawY(); offsetX = view.getX() - mouseX; offsetY = view.getY() - mouseY; isDragging = false; view.setBackgroundColor(Color.parseColor("#44FFFFFF"));
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (!isDragging && (Math.abs(event.getRawX() - startRawX) > 20 * density || Math.abs(event.getRawY() - startRawY) > 20 * density)) { isDragging = true; view.bringToFront(); }
                    if (isDragging) { view.setX(mouseX + offsetX); view.setY(mouseY + offsetY); }
                } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    view.setBackgroundColor(Color.TRANSPARENT);
                    if (isDragging) {
                        Point safeP = findAvailableGrid(view.getX(), view.getY(), actualGrid, view);
                        float finalX = safeP.x * actualGrid + (actualGrid - iconSize)/2f; float finalY = safeP.y * actualGrid + (actualGrid - iconSize)/2f;
                        ObjectAnimator animX = ObjectAnimator.ofFloat(view, "x", view.getX(), finalX); ObjectAnimator animY = ObjectAnimator.ofFloat(view, "y", view.getY(), finalY);
                        animX.setDuration(200); animY.setDuration(200); animX.setInterpolator(new DecelerateInterpolator()); animY.setInterpolator(new DecelerateInterpolator());
                        animX.start(); animY.start(); prefs.edit().putFloat("icon_x_" + id, finalX).putFloat("icon_y_" + id, finalY).apply();
                    } else {
                        long clickTime = System.currentTimeMillis();
                        if (clickTime - lastClickTime < 600) { 
                            if (id.equals("sys_settings")) openSettingsInAppWindow(); 
                            else if (id.equals("asset_extractor")) openAppWindow(L("🖼️ SFF查看器"), buildSffExtractorContent(), null); 
                            else if (id.equals("palette_editor")) openAppWindow(L("🎨 ACT色表工坊"), buildPaletteEditorContent(), null);
                            else if (id.equals("snd_extractor")) openAppWindow(L("🎵 SND查看器"), buildSndExtractorContent(), null); 
                            else if (id.equals("gif_extractor")) openAppWindow(L("🎞️ GIF拆解器"), buildGifExtractorContent(), null);
                            else if (id.equals("stage_editor")) openAppWindow(L("🗺️ 地图编辑器"), buildStageEditorContent(), null);
                            else if (id.equals("text_decipher")) openAppWindow(L("🔣 乱码解字板"), buildDecipherBoardContent(), null);
                            else if (id.equals("def_scanner")) openAppWindow(L("🗂️ DEF生成器"), buildDefScannerContent(), null);
                            else if (id.equals("clsn_editor")) openAppWindow(L("⚔️ 判定框工坊 (打击/防御框)"), buildClsnEditorContent("", ""), null);
                            else if (id.equals("ikemen_dict")) openAppWindow(L("📚 MUGEN/IK 全能词典"), buildDictionaryContent(), null);
                            lastClickTime = 0; 
                        } else lastClickTime = clickTime;
                    }
                } return true;
            }
        });
    }

    private void showContextMenu(View anchor, String title, Runnable onClose) {
        FrameLayout menuOverlay = new FrameLayout(getContext()); menuOverlay.setClickable(true); menuOverlay.setOnClickListener(v -> rootLayer.removeView(menuOverlay));
        LinearLayout menu = new LinearLayout(getContext()); menu.setOrientation(LinearLayout.VERTICAL); menu.setBackgroundColor(Color.parseColor("#2D2D30"));
        GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#2D2D30")); border.setStroke((int)(1*density), Color.parseColor("#3F3F46"));
        menu.setBackground(border); menu.setPadding((int)(5*density), (int)(5*density), (int)(5*density), (int)(5*density));
        
        Button btnClose = createButton(L("❌ 强制关闭"), "#E81123");
        btnClose.setOnClickListener(v -> { onClose.run(); rootLayer.removeView(menuOverlay); }); menu.addView(btnClose);
        
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2);
        int[] loc = new int[2]; anchor.getLocationOnScreen(loc);
        params.leftMargin = loc[0]; params.topMargin = Math.max(0, loc[1] - (int)(60*density));
        menuOverlay.addView(menu, params); rootLayer.addView(menuOverlay, new FrameLayout.LayoutParams(-1, -1)); menuOverlay.bringToFront();
    }

        private String resolveWindowLayoutKey(String windowTitle) {
        if (windowTitle == null) return "default";
        if (windowTitle.contains(L("🎨 检视: ")) || windowTitle.contains("SFF")) return "sff_viewer";
        if (windowTitle.contains(L("🎵 检视: ")) || windowTitle.contains("SND")) return "snd_viewer";
        if (windowTitle.contains(L("⚔️ 判定框")) || windowTitle.contains("Clsn")) return "clsn_editor";
        if (windowTitle.contains(L("地图")) || windowTitle.contains("Stage")) return "stage_editor";
        if (windowTitle.contains(L("色表")) || windowTitle.contains("ACT")) return "palette_editor";
        if (windowTitle.contains(L("词典")) || windowTitle.contains("MUGEN/IK")) return "ikemen_dict";
        if (windowTitle.contains("GIF")) return "gif_viewer";
        return "win_" + Math.abs(windowTitle.hashCode());
    }

    private View findTaggedViewRecursive(View root, String tag) {
        if (root == null) return null;
        if (tag.equals(root.getTag())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View found = findTaggedViewRecursive(vg.getChildAt(i), tag);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void applyFontScaleRecursive(View v, float scaleRatio) {
        if (v == null) return;
        if (v instanceof TextView) {
            TextView tv = (TextView) v;
            Object baseObj = tv.getTag(0x7f0b0001);
            float baseSp;
            if (baseObj instanceof Float) {
                baseSp = (Float) baseObj;
            } else {
                baseSp = tv.getTextSize() / Math.max(1f, getContext().getResources().getDisplayMetrics().scaledDensity);
                tv.setTag(0x7f0b0001, baseSp);
            }
            tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, Math.max(8f, baseSp * scaleRatio));
        } else if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                applyFontScaleRecursive(vg.getChildAt(i), scaleRatio);
            }
        }
    }

    private void applyCustomWindowLayout(String layoutKey, FrameLayout windowFrame, View contentHost, View contentView, boolean isMaximized) {
        String modeTag = isMaximized ? "_full" : "_win";
        String mKey = layoutKey + modeTag;
        String gKey = "global" + modeTag;

        int defW = prefs.getInt("wlay_" + gKey + "_w", isMaximized ? 100 : 78);
        int defH = prefs.getInt("wlay_" + gKey + "_h", isMaximized ? 100 : 84);
        int defPL = prefs.getInt("wlay_" + gKey + "_pl", 4);
        int defPR = prefs.getInt("wlay_" + gKey + "_pr", 4);
        int defPT = prefs.getInt("wlay_" + gKey + "_pt", 4);
        int defPB = prefs.getInt("wlay_" + gKey + "_pb", 4);
        int defPrev = prefs.getInt("wlay_" + gKey + "_prev", 65);
        int defGap = prefs.getInt("wlay_" + gKey + "_gap", 6);
        int defCorner = prefs.getInt("wlay_" + gKey + "_cr", isMaximized ? 0 : 10);
        int defFontPct = prefs.getInt("wlay_" + gKey + "_fs", 100);

        int wPct = prefs.getInt("wlay_" + mKey + "_w", defW);
        int hPct = prefs.getInt("wlay_" + mKey + "_h", defH);
        int padL = prefs.getInt("wlay_" + mKey + "_pl", defPL);
        int padR = prefs.getInt("wlay_" + mKey + "_pr", defPR);
        int padT = prefs.getInt("wlay_" + mKey + "_pt", defPT);
        int padB = prefs.getInt("wlay_" + mKey + "_pb", defPB);
        int prevPct = prefs.getInt("wlay_" + mKey + "_prev", defPrev);
        int gapDp = prefs.getInt("wlay_" + mKey + "_gap", defGap);
        int cornerDp = prefs.getInt("wlay_" + mKey + "_cr", defCorner);
        int fontPct = prefs.getInt("wlay_" + mKey + "_fs", defFontPct);

        int screenW = getContext().getResources().getDisplayMetrics().widthPixels;
        int screenH = getContext().getResources().getDisplayMetrics().heightPixels;
        int tbH = (taskbar != null && taskbar.getHeight() > 0) ? taskbar.getHeight() : Math.max((int)(40 * density), screenH / 14);

        if (windowFrame != null) {
            if (!isMaximized) {
                int w = Math.max((int)(240 * density), (int)(screenW * (wPct / 100f)));
                int h = Math.max((int)(200 * density), (int)((screenH - tbH) * (hPct / 100f)));
                ViewGroup.LayoutParams flp = windowFrame.getLayoutParams();
                if (flp != null) {
                    flp.width = w;
                    flp.height = h;
                    windowFrame.setLayoutParams(flp);
                }
            }
            if (windowFrame.getBackground() instanceof GradientDrawable) {
                ((GradientDrawable) windowFrame.getBackground()).setCornerRadius(cornerDp * density);
            }
        }

        if (contentHost != null) {
            contentHost.setPadding((int)(padL * density), (int)(padT * density), (int)(padR * density), (int)(padB * density));
            // 全屏模式下同样支持自由调整显示区域宽高占比（居中显示）
            if (contentView != null && contentView.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams cvLp = (FrameLayout.LayoutParams) contentView.getLayoutParams();
                if (isMaximized && (wPct < 100 || hPct < 100)) {
                    int availW = Math.max((int)(220 * density), screenW - (int)((padL + padR) * density));
                    int availH = Math.max((int)(180 * density), screenH - tbH - (int)((42 + padT + padB) * density));
                    cvLp.width = Math.max((int)(220 * density), (int)(availW * (wPct / 100f)));
                    cvLp.height = Math.max((int)(180 * density), (int)(availH * (hPct / 100f)));
                    cvLp.gravity = Gravity.CENTER;
                } else {
                    cvLp.width = FrameLayout.LayoutParams.MATCH_PARENT;
                    cvLp.height = FrameLayout.LayoutParams.MATCH_PARENT;
                    cvLp.gravity = Gravity.TOP | Gravity.LEFT;
                }
                contentView.setLayoutParams(cvLp);
            }
        }

        // 动态调整预览区、左右分栏和工具栏间距
        View vLeft = findTaggedViewRecursive(contentView, "layout_left");
        View vCenter = findTaggedViewRecursive(contentView, "layout_center");
        View vRight = findTaggedViewRecursive(contentView, "layout_right");
        View vPreview = findTaggedViewRecursive(contentView, "layout_preview");
        View vToolbar = findTaggedViewRecursive(contentView, "layout_toolbar");

        float centerWeight = Math.max(0.3f, Math.min(0.95f, prevPct / 100f));
        float sideWeight = Math.max(0.05f, 1.0f - centerWeight);
        int gapPx = (int)(gapDp * density);

        if (vCenter != null && vCenter.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams clp = (LinearLayout.LayoutParams) vCenter.getLayoutParams();
            if (vLeft != null && vRight != null) {
                clp.weight = centerWeight * 4.5f;
                clp.setMargins(gapPx, 0, gapPx, 0);
                vCenter.setLayoutParams(clp);
                if (vLeft.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                    LinearLayout.LayoutParams llp = (LinearLayout.LayoutParams) vLeft.getLayoutParams();
                    llp.weight = sideWeight * 2.5f;
                    vLeft.setLayoutParams(llp);
                }
                if (vRight.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                    LinearLayout.LayoutParams rlp = (LinearLayout.LayoutParams) vRight.getLayoutParams();
                    rlp.weight = sideWeight * 2.0f;
                    vRight.setLayoutParams(rlp);
                }
            } else if (vLeft != null) {
                clp.weight = centerWeight * 3.0f;
                clp.setMargins(gapPx, 0, 0, 0);
                vCenter.setLayoutParams(clp);
            }
        }
        if (vPreview != null && vPreview.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams plp = (LinearLayout.LayoutParams) vPreview.getLayoutParams();
            plp.weight = Math.max(1f, centerWeight * 10f);
            plp.setMargins(gapPx, gapPx, gapPx, gapPx);
            vPreview.setLayoutParams(plp);
        }
        if (vToolbar != null && vToolbar.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams tlp = (LinearLayout.LayoutParams) vToolbar.getLayoutParams();
            tlp.setMargins(0, gapPx / 2, 0, 0);
            vToolbar.setLayoutParams(tlp);
        }
        if (contentView != null && fontPct != 100) {
            applyFontScaleRecursive(contentView, fontPct / 100f);
        } else if (contentView != null) {
            applyFontScaleRecursive(contentView, 1.0f);
        }
    }

    private void showWindowLayoutDialog(final String windowTitle, final String layoutKey, final FrameLayout windowFrame, final View contentHost, final View contentView, final boolean[] isMaximized) {
        final boolean curMax = isMaximized[0];
        final String modeTag = curMax ? "_full" : "_win";
        final String mKey = layoutKey + modeTag;
        final String gKey = "global" + modeTag;

        final Dialog d = new Dialog(getContext());
        if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        ScrollView sv = new ScrollView(getContext());
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int)(16*density), (int)(16*density), (int)(16*density), (int)(16*density));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F51E2430"));
        bg.setCornerRadius(12f * density);
        bg.setStroke((int)(1.5f * density), Color.parseColor("#38BDF8"));
        box.setBackground(bg);

        box.addView(createSubTitle(curMax ? L("🖥️ 全屏模式 · 自定义显示区与样式") : L("🪟 窗口模式 · 自定义布局与样式")));
        TextView hint = new TextView(getContext());
        hint.setText(curMax
                ? L("已自动识别为【全屏模式】，可独立调节显示区大小、预览占比、边距、圆角和字号")
                : L("已自动识别为【窗口模式】，可独立调节窗口大小、预览占比、边距、圆角和字号"));
        applyGlobalFontSettings(hint, 0.85f, false);
        hint.setTextColor(Color.parseColor("#94A3B8"));
        box.addView(hint);

        final int[] vals = new int[] {
            prefs.getInt("wlay_" + mKey + "_w", prefs.getInt("wlay_" + gKey + "_w", curMax ? 100 : 78)),
            prefs.getInt("wlay_" + mKey + "_h", prefs.getInt("wlay_" + gKey + "_h", curMax ? 100 : 84)),
            prefs.getInt("wlay_" + mKey + "_prev", prefs.getInt("wlay_" + gKey + "_prev", 65)),
            prefs.getInt("wlay_" + mKey + "_gap", prefs.getInt("wlay_" + gKey + "_gap", 6)),
            prefs.getInt("wlay_" + mKey + "_pl", prefs.getInt("wlay_" + gKey + "_pl", 4)),
            prefs.getInt("wlay_" + mKey + "_pr", prefs.getInt("wlay_" + gKey + "_pr", 4)),
            prefs.getInt("wlay_" + mKey + "_pt", prefs.getInt("wlay_" + gKey + "_pt", 4)),
            prefs.getInt("wlay_" + mKey + "_pb", prefs.getInt("wlay_" + gKey + "_pb", 4)),
            prefs.getInt("wlay_" + mKey + "_cr", prefs.getInt("wlay_" + gKey + "_cr", curMax ? 0 : 10)),
            prefs.getInt("wlay_" + mKey + "_fs", prefs.getInt("wlay_" + gKey + "_fs", 100))
        };
        final String[] labels = new String[] {
            curMax ? L("全屏显示区宽度 (%)") : L("窗口宽度 (%)"),
            curMax ? L("全屏显示区高度 (%)") : L("窗口高度 (%)"),
            L("预览视口比例 (%)"), L("内部区块间距 (dp)"),
            L("左边距 (dp)"), L("右边距 (dp)"), L("上边距 (dp)"), L("下边距 (dp)"),
            L("窗口圆角半径 (dp)"), L("内容字号缩放 (%)")
        };
        final int[] mins = new int[] { 40, 40, 30, 0, 0, 0, 0, 0, 0, 75 };
        final int[] maxs = new int[] { 100, 100, 92, 24, 48, 48, 48, 48, 20, 140 };
        final String[] prefSuffix = new String[] { "_w", "_h", "_prev", "_gap", "_pl", "_pr", "_pt", "_pb", "_cr", "_fs" };

        Runnable liveApply = () -> {
            SharedPreferences.Editor ed = prefs.edit();
            for (int i = 0; i < vals.length; i++) ed.putInt("wlay_" + mKey + prefSuffix[i], vals[i]);
            ed.apply();
            applyCustomWindowLayout(layoutKey, windowFrame, contentHost, contentView, isMaximized[0]);
        };

        for (int i = 0; i < vals.length; i++) {
            final int idx = i;
            TextView lbl = new TextView(getContext());
            lbl.setText(labels[idx] + ": " + vals[idx]);
            applyGlobalFontSettings(lbl, 0.9f, false);
            lbl.setPadding(0, (int)(6*density), 0, (int)(2*density));
            box.addView(lbl);

            SeekBar sb = new SeekBar(getContext());
            sb.setMax(maxs[idx] - mins[idx]);
            sb.setProgress(Math.max(0, Math.min(maxs[idx] - mins[idx], vals[idx] - mins[idx])));
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    vals[idx] = mins[idx] + progress;
                    lbl.setText(labels[idx] + ": " + vals[idx]);
                    if (fromUser) liveApply.run();
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            box.addView(sb, new LinearLayout.LayoutParams((int)(300*density), -2));
        }

        LinearLayout btnRow = new LinearLayout(getContext());
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        Button btnSaveAll = createButton(L("🌐 设为全局默认"), "#0284C7");
        Button btnReset = createButton(L("♻️ 恢复默认"), "#475569");
        Button btnDone = createButton(L("✅ 完成"), "#16A34A");
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, -2, 1f);
        blp.setMargins((int)(3*density), (int)(12*density), (int)(3*density), 0);

        btnSaveAll.setOnClickListener(v -> {
            SharedPreferences.Editor ed = prefs.edit();
            for (int i = 0; i < vals.length; i++) ed.putInt("wlay_" + gKey + prefSuffix[i], vals[i]);
            ed.apply();
            Toast.makeText(getContext(), curMax ? L("✅ 已设为所有工具的全屏默认布局") : L("✅ 已设为所有工具的窗口默认布局"), Toast.LENGTH_SHORT).show();
        });
        btnReset.setOnClickListener(v -> {
            SharedPreferences.Editor ed = prefs.edit();
            for (int i = 0; i < prefSuffix.length; i++) ed.remove("wlay_" + mKey + prefSuffix[i]);
            ed.apply();
            applyCustomWindowLayout(layoutKey, windowFrame, contentHost, contentView, isMaximized[0]);
            Toast.makeText(getContext(), L("♻️ 已恢复默认布局"), Toast.LENGTH_SHORT).show();
            d.dismiss();
        });
        btnDone.setOnClickListener(v -> d.dismiss());

        btnRow.addView(btnSaveAll, blp);
        btnRow.addView(btnReset, blp);
        btnRow.addView(btnDone, blp);
        box.addView(btnRow);

        sv.addView(box);
        d.setContentView(sv);
        d.show();
    }


    private void openAppWindow(String windowTitle, View contentView, final Runnable onCloseInterceptor) {
        View existingWin = windowsLayer.findViewWithTag(windowTitle);
        if (existingWin != null) { existingWin.setVisibility(View.VISIBLE); existingWin.bringToFront(); return; }

        final String layoutKey = resolveWindowLayoutKey(windowTitle);
        final FrameLayout windowFrame = new FrameLayout(getContext()); windowFrame.setTag(windowTitle); windowFrame.setClickable(true);
        View winMediaBg = createMediaBackground(customWindowBg, 255, false);
        if (winMediaBg != null) {
            windowFrame.addView(winMediaBg, new FrameLayout.LayoutParams(-1, -1));
        } else {
            GradientDrawable winBg = new GradientDrawable();
            winBg.setColor(Color.parseColor("#F5181C24"));
            winBg.setCornerRadius(10f * density);
            winBg.setStroke((int)(1.5f * density), Color.parseColor("#3A4356"));
            windowFrame.setBackground(winBg);
        }
        windowFrame.setElevation(25f * density);

        LinearLayout winContainer = new LinearLayout(getContext()); winContainer.setOrientation(LinearLayout.VERTICAL); windowFrame.addView(winContainer, new FrameLayout.LayoutParams(-1, -1));

        final LinearLayout titleBar = new LinearLayout(getContext()); titleBar.setOrientation(LinearLayout.HORIZONTAL); titleBar.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable tbTitleBg = new GradientDrawable();
        tbTitleBg.setColor(Color.parseColor("#F2202632"));
        tbTitleBg.setCornerRadii(new float[]{10f*density, 10f*density, 10f*density, 10f*density, 0, 0, 0, 0});
        titleBar.setBackground(tbTitleBg);
        TextView title = new TextView(getContext()); title.setText("  " + windowTitle); applyGlobalFontSettings(title, 1.12f, true); title.setTextColor(Color.parseColor("#F1F5F9")); titleBar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        // 动态任务栏状态刷新：自动侦测是否有全屏窗口
        Runnable updateTaskbarState = () -> {
            if (taskbar == null) return;
            boolean anyMaximized = false;
            for (int i = 0; i < windowsLayer.getChildCount(); i++) {
                View w = windowsLayer.getChildAt(i);
                if (w.getVisibility() == View.VISIBLE && w.getLayoutParams().width == FrameLayout.LayoutParams.MATCH_PARENT) {
                    anyMaximized = true; break;
                }
            }
            GradientDrawable tbGlass = new GradientDrawable();
            tbGlass.setColor(Color.argb(anyMaximized ? 255 : taskbarAlpha, 20, 24, 32));
            tbGlass.setStroke((int)(1 * density), Color.parseColor("#2AFFFFFF"));
            taskbar.setBackground(tbGlass);
        };

        final FrameLayout contentHost = new FrameLayout(getContext());
        contentHost.addView(contentView, new FrameLayout.LayoutParams(-1, -1));
        final boolean[] isMaximized = {false}; final int[] savedBounds = new int[4];

        LinearLayout controls = new LinearLayout(getContext()); controls.setOrientation(LinearLayout.HORIZONTAL); controls.setGravity(Gravity.CENTER_VERTICAL);
        TextView btnLayout = new TextView(getContext()); btnLayout.setText(" 📐 "); applyGlobalFontSettings(btnLayout, 0.95f, true); btnLayout.setPadding((int)(10*density), (int)(5*density), (int)(10*density), (int)(6*density));
        btnLayout.setOnClickListener(v -> showWindowLayoutDialog(windowTitle, layoutKey, windowFrame, contentHost, contentView, isMaximized));
        controls.addView(btnLayout);

        TextView btnMin = new TextView(getContext()); btnMin.setText(" ─ "); applyGlobalFontSettings(btnMin, 1.0f, true); btnMin.setPadding((int)(12*density), (int)(5*density), (int)(12*density), (int)(8*density));
        btnMin.setOnClickListener(v -> { windowFrame.setVisibility(View.GONE); windowFrame.post(updateTaskbarState); }); controls.addView(btnMin);

        final TextView btnMax = new TextView(getContext()); btnMax.setText(" □ "); applyGlobalFontSettings(btnMax, 1.0f, true); btnMax.setPadding((int)(12*density), (int)(5*density), (int)(12*density), (int)(8*density));
        btnMax.setOnClickListener(v -> {
            if (isMaximized[0]) {
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(savedBounds[2], savedBounds[3]);
                windowFrame.setLayoutParams(lp); windowFrame.setX(savedBounds[0]); windowFrame.setY(savedBounds[1]);
                btnMax.setText(" □ "); isMaximized[0] = false;
            } else {
                savedBounds[0] = (int) windowFrame.getX(); savedBounds[1] = (int) windowFrame.getY(); savedBounds[2] = windowFrame.getWidth(); savedBounds[3] = windowFrame.getHeight();
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
                lp.bottomMargin = taskbar != null ? taskbar.getHeight() : (int)(50 * density);
                windowFrame.setLayoutParams(lp); windowFrame.setX(0); windowFrame.setY(0); windowFrame.bringToFront();
                btnMax.setText(" ❐ "); isMaximized[0] = true;
            }
            applyCustomWindowLayout(layoutKey, windowFrame, contentHost, contentView, isMaximized[0]);
            windowFrame.post(updateTaskbarState);
        }); controls.addView(btnMax);

        final LinearLayout taskBtn = new LinearLayout(getContext()); taskBtn.setTag("tb_" + windowTitle);
        TextView btnClose = new TextView(getContext()); btnClose.setText(" ✕ "); applyGlobalFontSettings(btnClose, 1.0f, true); btnClose.setPadding((int)(12*density), (int)(5*density), (int)(12*density), (int)(5*density));
        btnClose.setOnTouchListener((v, e) -> { if(e.getAction()==MotionEvent.ACTION_DOWN) v.setBackgroundColor(Color.parseColor("#E81123")); else if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL) v.setBackgroundColor(Color.TRANSPARENT); return false; });
        btnClose.setOnClickListener(v -> {
            if (onCloseInterceptor != null) onCloseInterceptor.run();
            else { windowsLayer.removeView(windowFrame); taskbarAppsLayout.removeView(taskBtn); }
            windowFrame.post(updateTaskbarState);
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

        winContainer.addView(titleBar, new LinearLayout.LayoutParams(-1, (int)(38*density)));
        View sep = new View(getContext()); sep.setBackgroundColor(Color.parseColor("#38BDF8")); winContainer.addView(sep, new LinearLayout.LayoutParams(-1, (int)(1.5f*density)));
        winContainer.addView(contentHost, new LinearLayout.LayoutParams(-1, 0, 1f));

        // PC 风格右下角拖拽调窗手柄，任意工具窗口皆可自由拉大缩小
        TextView resizeHandle = new TextView(getContext());
        resizeHandle.setText("◢");
        resizeHandle.setTextColor(Color.parseColor("#8838BDF8"));
        resizeHandle.setTextSize(12f);
        resizeHandle.setGravity(Gravity.BOTTOM | Gravity.RIGHT);
        resizeHandle.setPadding(0, 0, (int)(2*density), (int)(1*density));
        FrameLayout.LayoutParams rhLp = new FrameLayout.LayoutParams((int)(24*density), (int)(24*density), Gravity.BOTTOM | Gravity.RIGHT);
        resizeHandle.setOnTouchListener(new View.OnTouchListener() {
            float startRawX, startRawY; int startW, startH;
            @Override public boolean onTouch(View v, MotionEvent event) {
                if (isMaximized[0]) return true;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startRawX = event.getRawX(); startRawY = event.getRawY();
                        startW = windowFrame.getWidth(); startH = windowFrame.getHeight();
                        windowFrame.bringToFront();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int sW = getContext().getResources().getDisplayMetrics().widthPixels;
                        int sH = getContext().getResources().getDisplayMetrics().heightPixels;
                        int newW = Math.max((int)(240*density), Math.min(sW, startW + (int)(event.getRawX() - startRawX)));
                        int newH = Math.max((int)(200*density), Math.min(sH - (taskbar != null ? taskbar.getHeight() : 0), startH + (int)(event.getRawY() - startRawY)));
                        ViewGroup.LayoutParams lp = windowFrame.getLayoutParams();
                        lp.width = newW; lp.height = newH;
                        windowFrame.setLayoutParams(lp);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        int sW2 = Math.max(1, getContext().getResources().getDisplayMetrics().widthPixels);
                        int sH2 = Math.max(1, getContext().getResources().getDisplayMetrics().heightPixels - (taskbar != null ? taskbar.getHeight() : 0));
                        int pctW = Math.max(40, Math.min(100, Math.round(windowFrame.getWidth() * 100f / sW2)));
                        int pctH = Math.max(40, Math.min(98, Math.round(windowFrame.getHeight() * 100f / sH2)));
                        prefs.edit().putInt("wlay_" + layoutKey + "_win_w", pctW).putInt("wlay_" + layoutKey + "_win_h", pctH).apply();
                        return true;
                }
                return false;
            }
        });
        windowFrame.addView(resizeHandle, rhLp);

        taskBtn.setOrientation(LinearLayout.HORIZONTAL); taskBtn.setGravity(Gravity.CENTER); taskBtn.setPadding((int)(10*density), (int)(5*density), (int)(10*density), (int)(5*density));
        GradientDrawable tbBg = new GradientDrawable(); tbBg.setColor(Color.parseColor("#26FFFFFF")); tbBg.setCornerRadius(6f * density); taskBtn.setBackground(tbBg);
        LinearLayout.LayoutParams tbParams = new LinearLayout.LayoutParams(-2, -1); tbParams.setMargins(0, (int)(4*density), (int)(6*density), (int)(4*density));

        String rawName = windowTitle.replace(L("🎨 检视: "), "").replace(L("📦 "), "").replace(L("🎵 检视: "), "").replace(L("🎞️ GIF拆解: "), "").trim();
        final String finalShortName = rawName.length() > 8 ? rawName.substring(0, 8) + ".." : rawName;
        TextView tbText = new TextView(getContext()); tbText.setText("▤ " + finalShortName); applyGlobalFontSettings(tbText, 1.05f, false); taskBtn.addView(tbText);

        final boolean[] isBtnDragging = {false};
        taskBtn.setOnTouchListener(new View.OnTouchListener() {
            float startX; float initialTranslation;
            @Override public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN: startX = event.getRawX(); initialTranslation = v.getTranslationX(); isBtnDragging[0] = false; v.setBackgroundColor(Color.parseColor("#44FFFFFF")); return false;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - startX;
                        if (Math.abs(dx) > 10 * density) { isBtnDragging[0] = true; v.getParent().requestDisallowInterceptTouchEvent(true); v.cancelLongPress(); }
                        if (isBtnDragging[0]) { v.setTranslationX(initialTranslation + dx); v.bringToFront(); } return true;
                    case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                        v.setBackground(tbBg);
                        if (isBtnDragging[0]) {
                            float currentCenter = v.getX() + v.getTranslationX() + v.getWidth() / 2f; int newIndex = taskbarAppsLayout.getChildCount() - 1;
                            for (int i = 0; i < taskbarAppsLayout.getChildCount(); i++) { View child = taskbarAppsLayout.getChildAt(i); if (child != v && currentCenter < child.getX() + child.getWidth() / 2f) { newIndex = i; break; } }
                            final int targetIndex = newIndex; taskbarAppsLayout.post(() -> { taskbarAppsLayout.removeView(v); v.setTranslationX(0); taskbarAppsLayout.addView(v, targetIndex); });
                            return true;
                        } return false;
                } return false;
            }
        });

        taskBtn.setOnClickListener(v -> {
            if (isBtnDragging[0]) return;
            if (windowFrame.getVisibility() == View.VISIBLE) {
                if (windowsLayer.indexOfChild(windowFrame) == windowsLayer.getChildCount() - 1) { windowFrame.setVisibility(View.GONE); } else { windowFrame.bringToFront(); }
            } else { windowFrame.setVisibility(View.VISIBLE); windowFrame.bringToFront(); }
            windowFrame.post(updateTaskbarState);
        });

        taskBtn.setOnLongClickListener(v -> {
            if (isBtnDragging[0]) return false;
            showContextMenu(v, finalShortName, () -> {
                if (onCloseInterceptor != null) onCloseInterceptor.run();
                else { windowsLayer.removeView(windowFrame); taskbarAppsLayout.removeView(taskBtn); }
                windowFrame.post(updateTaskbarState);
            });
            return true;
        });
        taskbarAppsLayout.addView(taskBtn, tbParams);

        int screenW = getContext().getResources().getDisplayMetrics().widthPixels;
        int screenH = getContext().getResources().getDisplayMetrics().heightPixels;
        int w = (int) (screenW * 0.78f);
        int h = (int) (screenH * 0.84f);

        FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(w, h);
        windowFrame.setLayoutParams(frameParams);
        applyCustomWindowLayout(layoutKey, windowFrame, contentHost, contentView, false);
        windowFrame.post(() -> {
            if (!isMaximized[0]) {
                int tbH2 = (taskbar != null && taskbar.getHeight() > 0) ? taskbar.getHeight() : (int)(44 * density);
                float cx = Math.max(0, (rootLayer.getWidth() - windowFrame.getWidth()) / 2f);
                float cy = Math.max(0, (rootLayer.getHeight() - tbH2 - windowFrame.getHeight()) / 2f);
                windowFrame.setX(cx);
                windowFrame.setY(cy);
            }
        });
        windowsLayer.addView(windowFrame);
    }

    private void loadDesktopSettings() {
        forceCutoutFullscreen = prefs.getBoolean("dt_forceCutout", false);
        uiScale = prefs.getFloat("dt_uiScale", 1.0f);
        this.density = baseDensity * uiScale;

        bgAlpha = prefs.getInt("dt_bgAlpha", 180); gridSizeBase = prefs.getInt("dt_gridSize", 100); showGrid = prefs.getBoolean("dt_showGrid", false);
        customDesktopBg = prefs.getString("dt_customDeskBg", ""); customWindowBg = prefs.getString("dt_customWinBg", "");
        bgMediaVolume = prefs.getInt("dt_bgMediaVol", 50); winMediaVolume = prefs.getInt("dt_winMediaVol", 50); taskbarAlpha = prefs.getInt("dt_taskbarAlpha", 230);
        mediaScaleMode = prefs.getInt("dt_mediaScale", 1); fontPath = prefs.getString("dt_fontPath", ""); fontColor = prefs.getInt("dt_fontColor", Color.WHITE);
        fontSize = prefs.getFloat("dt_fontSize", 12f); fontShadowEnabled = prefs.getBoolean("dt_fontShadow", true); fontShadowColor = prefs.getInt("dt_fontShadowC", Color.BLACK);
        reloadTypeface();
    }

    private void reloadTypeface() {
        if (!fontPath.isEmpty()) { try { customFont = Typeface.createFromFile(fontPath); } catch (Exception e) { customFont = null; } } else customFont = null;
    }

    private void openSettingsInAppWindow() {
        final String title = L("⚙ 系统控制台");
        final int b_bgAlpha = bgAlpha; final int b_gridSizeBase = gridSizeBase; final boolean b_showGrid = showGrid;
        final String b_customDesktopBg = customDesktopBg; final String b_customWindowBg = customWindowBg;
        final int b_bgMediaVolume = bgMediaVolume; final int b_winMediaVolume = winMediaVolume; final int b_taskbarAlpha = taskbarAlpha;
        final int b_mediaScaleMode = mediaScaleMode;
        final String b_fontPath = fontPath; final int b_fontColor = fontColor; final float b_fontSize = fontSize;
        final boolean b_fontShadowEnabled = fontShadowEnabled; final int b_fontShadowColor = fontShadowColor;
        final boolean b_forceCutout = forceCutoutFullscreen; final float b_uiScale = uiScale;

        Runnable performClose = () -> {
            View win = windowsLayer.findViewWithTag(title); if (win != null) windowsLayer.removeView(win);
            View tbBtn = taskbarAppsLayout.findViewWithTag("tb_" + title); if (tbBtn != null) taskbarAppsLayout.removeView(tbBtn);
        };

        Runnable checkAndPromptClose = () -> {
            boolean changed = (bgAlpha!=b_bgAlpha || gridSizeBase!=b_gridSizeBase || showGrid!=b_showGrid || !customDesktopBg.equals(b_customDesktopBg) || !customWindowBg.equals(b_customWindowBg) || bgMediaVolume!=b_bgMediaVolume || winMediaVolume!=b_winMediaVolume || taskbarAlpha!=b_taskbarAlpha || mediaScaleMode!=b_mediaScaleMode || !fontPath.equals(b_fontPath) || fontColor!=b_fontColor || fontShadowEnabled!=b_fontShadowEnabled || forceCutoutFullscreen!=b_forceCutout || uiScale!=b_uiScale);
            if (changed) {
                showWin10SavePrompt(
                    () -> { 
                        prefs.edit().putBoolean("dt_forceCutout", forceCutoutFullscreen).putFloat("dt_uiScale", uiScale).putInt("dt_bgAlpha", bgAlpha).putInt("dt_gridSize", gridSizeBase).putBoolean("dt_showGrid", showGrid).putString("dt_customDeskBg", customDesktopBg).putString("dt_customWinBg", customWindowBg).putInt("dt_bgMediaVol", bgMediaVolume).putInt("dt_winMediaVol", winMediaVolume).putInt("dt_taskbarAlpha", taskbarAlpha).putInt("dt_mediaScale", mediaScaleMode).putString("dt_fontPath", fontPath).putInt("dt_fontColor", fontColor).putFloat("dt_fontSize", fontSize).putBoolean("dt_fontShadow", fontShadowEnabled).putInt("dt_fontShadowC", fontShadowColor).apply();
                        savedVideoPositionDesk = 0; savedVideoPositionWin = 0;
                        reloadTypeface(); refreshDesktopBackground(); setupDesktopIcons(); Toast.makeText(getContext(), L("✅ 设置已保存！"), Toast.LENGTH_SHORT).show(); performClose.run();
                    },
                    () -> { 
                        forceCutoutFullscreen = b_forceCutout; uiScale = b_uiScale;
                        bgAlpha = b_bgAlpha; gridSizeBase = b_gridSizeBase; showGrid = b_showGrid; customDesktopBg = b_customDesktopBg; customWindowBg = b_customWindowBg; bgMediaVolume = b_bgMediaVolume; winMediaVolume = b_winMediaVolume; taskbarAlpha = b_taskbarAlpha; mediaScaleMode = b_mediaScaleMode; fontPath = b_fontPath; fontColor = b_fontColor; fontSize = b_fontSize; fontShadowEnabled = b_fontShadowEnabled; fontShadowColor = b_fontShadowColor;
                        if (taskbar != null) taskbar.setBackgroundColor(Color.argb(taskbarAlpha, 17, 17, 17)); updateMediaVolumes(); reloadTypeface(); refreshDesktopBackground(); setupDesktopIcons(); performClose.run();
                    }
                );
            } else performClose.run();
        };

        View content = buildSettingsContent(performClose); openAppWindow(title, content, checkAndPromptClose);
    }

    private View buildSettingsContent(Runnable closeAction) {
        ScrollView scroll = new ScrollView(getContext()); LinearLayout layout = new LinearLayout(getContext()); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding((int)(20*density), (int)(10*density), (int)(20*density), (int)(20*density));
        
        layout.addView(createTitle(L("🖥️ 桌面基础布局")));
        layout.addView(createSubTitle(L("桌面壁纸不透明度:")));
        SeekBar alphaBar = new SeekBar(getContext()); alphaBar.setMax(255); alphaBar.setProgress(bgAlpha);
        alphaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(SeekBar s, int p, boolean b) { bgAlpha = p; refreshDesktopBackground(); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){} }); layout.addView(alphaBar);
        
        layout.addView(createSubTitle(L("底部任务栏不透明度:")));
        SeekBar tbAlphaBar = new SeekBar(getContext()); tbAlphaBar.setMax(255); tbAlphaBar.setProgress(taskbarAlpha);
        tbAlphaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(SeekBar s, int p, boolean b) { taskbarAlpha = p; if (taskbar != null) taskbar.setBackgroundColor(Color.argb(p, 17, 17, 17)); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){} }); layout.addView(tbAlphaBar);

        layout.addView(createSubTitle(L("桌面网格间距:"))); SeekBar gridBar = new SeekBar(getContext()); gridBar.setMax(250); gridBar.setProgress(gridSizeBase);
        gridBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(SeekBar s, int p, boolean b) { gridSizeBase = Math.max(60, p); rootLayer.invalidate(); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){ setupDesktopIcons(); } }); layout.addView(gridBar);

        Button gridToggle = createButton(showGrid ? L("✔️ 网格辅助线：开启") : L("❌ 网格辅助线：关闭"), "#333333");
        gridToggle.setOnClickListener(v -> { showGrid = !showGrid; gridToggle.setText(showGrid ? L("✔️ 网格辅助线：开启") : L("❌ 网格辅助线：关闭")); rootLayer.invalidate(); }); layout.addView(gridToggle);

        layout.addView(createSubTitle(L("布局与屏幕适配:")));
        Button cutoutToggle = createButton(forceCutoutFullscreen ? L("✔️ 强制全屏 (延伸至刘海/打孔区)") : L("❌ 默认显示 (避开刘海屏黑边)"), "#333333");
        cutoutToggle.setOnClickListener(v -> { forceCutoutFullscreen = !forceCutoutFullscreen; cutoutToggle.setText(forceCutoutFullscreen ? L("✔️ 强制全屏 (延伸至刘海/打孔区)") : L("❌ 默认显示 (避开刘海屏黑边)")); applyImmersiveMode(getWindow()); }); layout.addView(cutoutToggle);

        final TextView uiScaleLabel = createSubTitle(L("全局 UI 缩放比例: ") + String.format("%.2fx", uiScale) + L(" (需重启面板生效)")); layout.addView(uiScaleLabel);
        SeekBar scaleBar = new SeekBar(getContext()); scaleBar.setMax(150); scaleBar.setProgress((int)((uiScale - 0.5f) * 100)); // 支持 0.5x 到 2.0x 动态调缩放
        scaleBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { 
            public void onProgressChanged(SeekBar s, int p, boolean b) { uiScale = 0.5f + (p / 100f); uiScaleLabel.setText(L("全局 UI 缩放比例: ") + String.format("%.2fx", uiScale) + L(" (需重启面板生效)")); } 
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){} 
        }); layout.addView(scaleBar);

        layout.addView(createTitle(L("🅰️ 全局字体定制引擎")));
        final TextView fontLabel = createSubTitle(L("字体状态: ") + (fontPath.isEmpty() ? L("系统默认") : L("已加载外部资源"))); layout.addView(fontLabel);
        Button pickFont = createButton(L("📂 浏览本地选取字体文件 (.ttf/.otf)"), "#444444"); pickFont.setOnClickListener(v -> showWin10FilePicker(L("选择字体文件"), 3, fontLabel, scroll, null)); layout.addView(pickFont);

        layout.addView(createSubTitle(L("字体字号大小:")));
        SeekBar sizeBar = new SeekBar(getContext()); sizeBar.setMax(30); sizeBar.setProgress((int)fontSize);
        sizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(SeekBar s, int p, boolean b) { fontSize = Math.max(8, p); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){} }); layout.addView(sizeBar);

        layout.addView(createSubTitle(L("全局字体颜色代码 (Hex):")));
        final EditText colorInput = createInput(L("如: #FFFFFF"), String.format("#%06X", (0xFFFFFF & fontColor))); layout.addView(colorInput);
        colorInput.addTextChangedListener(new TextWatcher() { public void afterTextChanged(Editable s) { try{ fontColor = Color.parseColor(s.toString()); }catch(Exception e){} } public void beforeTextChanged(CharSequence s, int x, int y, int z) {} public void onTextChanged(CharSequence s, int x, int y, int z) {} });

        Button shadowToggle = createButton(fontShadowEnabled ? L("✔️ 字体投影：开启") : L("❌ 字体投影：关闭"), "#333333");
        shadowToggle.setOnClickListener(v -> { fontShadowEnabled = !fontShadowEnabled; shadowToggle.setText(fontShadowEnabled ? L("✔️ 字体投影：开启") : L("❌ 字体投影：关闭")); }); layout.addView(shadowToggle);

        layout.addView(createTitle(L("🎬 动态媒体矩阵 (优先读取窗口声音)")));
        layout.addView(createSubTitle(L("桌面壁纸视频音量:"))); SeekBar bgVolBar = new SeekBar(getContext()); bgVolBar.setMax(100); bgVolBar.setProgress(bgMediaVolume);
        bgVolBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(SeekBar s, int p, boolean b) { bgMediaVolume = p; updateMediaVolumes(); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){} }); layout.addView(bgVolBar);

        layout.addView(createSubTitle(L("窗口壁纸视频音量:"))); SeekBar winVolBar = new SeekBar(getContext()); winVolBar.setMax(100); winVolBar.setProgress(winMediaVolume);
        winVolBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(SeekBar s, int p, boolean b) { winMediaVolume = p; updateMediaVolumes(); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){} }); layout.addView(winVolBar);

        layout.addView(createSubTitle(L("多媒体渲染模式:"))); Spinner scaleSpinner = new Spinner(getContext());
        ArrayAdapter<String> scaleAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, new String[]{L("📏 强制拉伸填满"), L("✂️ 居中裁切填满"), L("🎯 保持原比例居中")});
        scaleSpinner.setAdapter(scaleAdapter); scaleSpinner.setSelection(mediaScaleMode); layout.addView(scaleSpinner);

        final TextView deskBgLabel = createSubTitle(L("桌面壁纸: ") + (customDesktopBg.isEmpty() ? L("未配置") : L("已应用"))); layout.addView(deskBgLabel);
        Button pickDesk = createButton(L("📂 浏览本地选取桌面动态壁纸"), "#444444"); pickDesk.setOnClickListener(v -> showWin10FilePicker(L("选择桌面动态壁纸"), 1, deskBgLabel, scroll, null)); layout.addView(pickDesk);

        final TextView winBgLabel = createSubTitle(L("窗口壁纸: ") + (customWindowBg.isEmpty() ? L("未配置") : L("已应用"))); layout.addView(winBgLabel);
        Button pickWin = createButton(L("📂 浏览本地选取窗口动态壁纸"), "#444444"); pickWin.setOnClickListener(v -> showWin10FilePicker(L("选择窗口动态壁纸"), 2, winBgLabel, scroll, null)); layout.addView(pickWin);

        Button saveBtn = createButton(L("💾 保存设置并应用"), "#0078D7"); LinearLayout.LayoutParams btnP = new LinearLayout.LayoutParams(-1, -2); btnP.setMargins(0, (int)(30*density), 0, 0); saveBtn.setLayoutParams(btnP);
        saveBtn.setOnClickListener(v -> {
            density = baseDensity * uiScale;
            prefs.edit().putBoolean("dt_forceCutout", forceCutoutFullscreen).putFloat("dt_uiScale", uiScale).putInt("dt_bgAlpha", bgAlpha).putInt("dt_gridSize", gridSizeBase).putBoolean("dt_showGrid", showGrid).putString("dt_customDeskBg", customDesktopBg).putString("dt_customWinBg", customWindowBg).putInt("dt_bgMediaVol", bgMediaVolume).putInt("dt_winMediaVol", winMediaVolume).putInt("dt_taskbarAlpha", taskbarAlpha).putInt("dt_mediaScale", mediaScaleMode).putString("dt_fontPath", fontPath).putInt("dt_fontColor", fontColor).putFloat("dt_fontSize", fontSize).putBoolean("dt_fontShadow", fontShadowEnabled).putInt("dt_fontShadowC", fontShadowColor).apply();
            savedVideoPositionDesk = 0; savedVideoPositionWin = 0; reloadTypeface(); refreshDesktopBackground(); setupDesktopIcons(); Toast.makeText(getContext(), L("✅ 设置已保存！"), Toast.LENGTH_SHORT).show(); closeAction.run();
        }); layout.addView(saveBtn);
        scroll.addView(layout); return scroll;
    }

    private void showWin10SavePrompt(Runnable onSave, Runnable onDiscard) {
        final Dialog pDialog = new Dialog(getContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        pDialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE); applyImmersiveMode(pDialog.getWindow());
        FrameLayout overlay = new FrameLayout(getContext()); overlay.setBackgroundColor(Color.argb(80, 0,0,0));
        LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setBackgroundColor(Color.parseColor("#1E1E1E"));
        GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#1E1E1E")); border.setStroke(2, Color.parseColor("#0078D7")); box.setBackground(border); box.setElevation(50f);
        
        LinearLayout titleBar = new LinearLayout(getContext()); titleBar.setBackgroundColor(Color.parseColor("#2D2D30")); 
        TextView title = new TextView(getContext()); title.setText(L(" ⚠️ 未保存的更改")); applyGlobalFontSettings(title, 1.1f, true); title.setPadding((int)(10*density), (int)(8*density), 0, (int)(8*density)); titleBar.addView(title); box.addView(titleBar);
        View sep = new View(getContext()); sep.setBackgroundColor(Color.parseColor("#0078D7")); box.addView(sep, new LinearLayout.LayoutParams(-1, (int)(2*density)));
        
        TextView msg = new TextView(getContext()); msg.setText(L("检测到设置发生变更，是否保存？")); applyGlobalFontSettings(msg, 1.0f, false); msg.setPadding((int)(20*density), (int)(25*density), (int)(20*density), (int)(25*density)); box.addView(msg);
        
        LinearLayout btnRow = new LinearLayout(getContext()); btnRow.setOrientation(LinearLayout.HORIZONTAL); btnRow.setGravity(Gravity.RIGHT); btnRow.setPadding((int)(10*density), 0, (int)(10*density), (int)(15*density));
        Button bSave = createButton(L("💾 保存"), "#0078D7"); bSave.setOnClickListener(v -> { pDialog.dismiss(); onSave.run(); });
        Button bDiscard = createButton(L("🗑️ 不保存"), "#333333"); bDiscard.setOnClickListener(v -> { pDialog.dismiss(); onDiscard.run(); });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2); bp.setMargins((int)(10*density),0,0,0);
        btnRow.addView(bSave, bp); btnRow.addView(bDiscard, bp); box.addView(btnRow);
        
        FrameLayout.LayoutParams winParams = new FrameLayout.LayoutParams((int)(rootLayer.getWidth()*0.5f), -2); winParams.gravity = Gravity.CENTER; overlay.addView(box, winParams);
        pDialog.setContentView(overlay); pDialog.show(); pDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
    }

    private void showWin10FilePicker(String winTitle, final int targetType, final TextView labelRef, final View hostViewToRefresh, final OnFileSelectedListener listener) {
        final Dialog pDialog = new Dialog(getContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen); pDialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE); applyImmersiveMode(pDialog.getWindow());
        FrameLayout overlay = new FrameLayout(getContext()); overlay.setBackgroundColor(Color.argb(90, 0,0,0));
        LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setBackgroundColor(Color.parseColor("#1E1E1E"));
        GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#1E1E1E")); border.setStroke(2, Color.parseColor("#3F3F46")); box.setBackground(border); box.setClickable(true);
        
        LinearLayout titleBar = new LinearLayout(getContext()); titleBar.setBackgroundColor(Color.parseColor("#2D2D30")); titleBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(getContext()); title.setText(" 📂 " + winTitle); applyGlobalFontSettings(title, 1.1f, true); titleBar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView btnClose = new TextView(getContext()); btnClose.setText(" ✕ "); applyGlobalFontSettings(btnClose, 1.1f, true); btnClose.setPadding((int)(15*density), (int)(8*density), (int)(15*density), (int)(8*density)); btnClose.setOnClickListener(v -> pDialog.dismiss()); titleBar.addView(btnClose); box.addView(titleBar);
        View sep = new View(getContext()); sep.setBackgroundColor(Color.parseColor("#0078D7")); box.addView(sep, new LinearLayout.LayoutParams(-1, (int)(2*density)));
        
        final TextView pathView = new TextView(getContext()); applyGlobalFontSettings(pathView, 0.9f, false); pathView.setPadding((int)(10*density), (int)(10*density), (int)(10*density), (int)(10*density)); pathView.setBackgroundColor(Color.parseColor("#252526")); box.addView(pathView);
        
        ScrollView scroll = new ScrollView(getContext()); LinearLayout listLayout = new LinearLayout(getContext()); listLayout.setOrientation(LinearLayout.VERTICAL); scroll.addView(listLayout);
        box.addView(scroll, new LinearLayout.LayoutParams(-1, -1));

        Runnable refreshList = new Runnable() {
            @Override public void run() {
                listLayout.removeAllViews();
                if (lastVisitedDir == null || !lastVisitedDir.exists()) lastVisitedDir = Environment.getExternalStorageDirectory();
                pathView.setText(L("当前路径: ") + lastVisitedDir.getAbsolutePath());
                
                Button goRoot = createButton(L("🏠 回到内部存储根目录"), "#0078D7"); goRoot.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); goRoot.setPadding((int)(20*density), (int)(15*density), 0, (int)(15*density));
                goRoot.setOnClickListener(v -> { lastVisitedDir = Environment.getExternalStorageDirectory(); this.run(); }); listLayout.addView(goRoot);

                if (lastVisitedDir.getParentFile() != null) {
                    Button up = createButton(L("⬆️ 返回上一级文件夹"), "#333333"); up.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); up.setPadding((int)(20*density), (int)(15*density), 0, (int)(15*density));
                    up.setOnClickListener(v -> { lastVisitedDir = lastVisitedDir.getParentFile(); this.run(); }); listLayout.addView(up);
                }

                if (targetType == 4) {
                    Button scanDirBtn = createButton(L("✔️ 深度扫描并提取本文件夹的 SFF 素材"), "#4CAF50"); scanDirBtn.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); scanDirBtn.setPadding((int)(20*density), (int)(15*density), 0, (int)(15*density));
                    scanDirBtn.setOnClickListener(v -> { if (listener != null) listener.onFileSelected(lastVisitedDir); pDialog.dismiss(); }); listLayout.addView(scanDirBtn);
                } else if (targetType == 5) { 
                    Button scanDirBtn = createButton(L("✔️ 深度扫描并提取本文件夹的 SND 音频"), "#FF9800"); scanDirBtn.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); scanDirBtn.setPadding((int)(20*density), (int)(15*density), 0, (int)(15*density));
                    scanDirBtn.setOnClickListener(v -> { if (listener != null) listener.onFileSelected(lastVisitedDir); pDialog.dismiss(); }); listLayout.addView(scanDirBtn);
                } else if (targetType == 6) {
                    Button scanDirBtn = createButton(L("✔️ 深度扫描并提取本文件夹的 GIF 动图"), "#9C27B0"); scanDirBtn.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); scanDirBtn.setPadding((int)(20*density), (int)(15*density), 0, (int)(15*density));
                    scanDirBtn.setOnClickListener(v -> { if (listener != null) listener.onFileSelected(lastVisitedDir); pDialog.dismiss(); }); listLayout.addView(scanDirBtn);
                } else if (targetType == 7) {
                    Button scanDirBtn = createButton(L("✔️ 深度扫描本文件夹的外部图像素材"), "#4CAF50"); scanDirBtn.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); scanDirBtn.setPadding((int)(20*density), (int)(15*density), 0, (int)(15*density));
                    scanDirBtn.setOnClickListener(v -> { if (listener != null) listener.onFileSelected(lastVisitedDir); pDialog.dismiss(); }); listLayout.addView(scanDirBtn);
                } else if (targetType == 8) {
                    Button scanDirBtn = createButton(L("✔️ 深度扫描本文件夹的外部音频素材"), "#FF9800"); scanDirBtn.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); scanDirBtn.setPadding((int)(20*density), (int)(15*density), 0, (int)(15*density));
                    scanDirBtn.setOnClickListener(v -> { if (listener != null) listener.onFileSelected(lastVisitedDir); pDialog.dismiss(); }); listLayout.addView(scanDirBtn);
                } else if (targetType == 9) {
                    Button scanDirBtn = createButton(L("✔️ 深度扫描并提取本文件夹的 ACT 色表"), "#0078D7"); scanDirBtn.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); scanDirBtn.setPadding((int)(20*density), (int)(15*density), 0, (int)(15*density));
                    scanDirBtn.setOnClickListener(v -> { if (listener != null) listener.onFileSelected(lastVisitedDir); pDialog.dismiss(); }); listLayout.addView(scanDirBtn);
                } else if (targetType == 10) {
                    Button scanDirBtn = createButton(L("✔️ 深度扫描本文件夹的地图工程 (.def)"), "#E81123"); scanDirBtn.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); scanDirBtn.setPadding((int)(20*density), (int)(15*density), 0, (int)(15*density));
                    scanDirBtn.setOnClickListener(v -> { if (listener != null) listener.onFileSelected(lastVisitedDir); pDialog.dismiss(); }); listLayout.addView(scanDirBtn);
                } else if (targetType == 11) {
                    Button scanDirBtn = createButton(L("✔️ 深度扫描本文件夹的 3D 模型 (.gltf/.glb)"), "#0078D7"); scanDirBtn.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); scanDirBtn.setPadding((int)(20*density), (int)(15*density), 0, (int)(15*density));
                    scanDirBtn.setOnClickListener(v -> { if (listener != null) listener.onFileSelected(lastVisitedDir); pDialog.dismiss(); }); listLayout.addView(scanDirBtn);
                } else if (targetType == 13) {
                    Button scanDirBtn = createButton(L("✔️ 扫描此文件夹下的所有 DEF 工程"), "#E81123"); scanDirBtn.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); scanDirBtn.setPadding((int)(20*density), (int)(15*density), 0, (int)(15*density));
                    scanDirBtn.setOnClickListener(v -> { if (listener != null) listener.onFileSelected(lastVisitedDir); pDialog.dismiss(); }); listLayout.addView(scanDirBtn);
                }

                
                File[] files = lastVisitedDir.listFiles();
                if (files != null) {
                    Arrays.sort(files, (f1, f2) -> {
                        if (f1.isDirectory() && !f2.isDirectory()) return -1; if (!f1.isDirectory() && f2.isDirectory()) return 1; return f1.getName().compareToIgnoreCase(f2.getName());
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
                                    if (absPath.toLowerCase().endsWith(".def") || absPath.toLowerCase().endsWith(".sff")) { if(listener != null) listener.onFileSelected(f); pDialog.dismiss(); } 
                                    else Toast.makeText(getContext(), L("❌ 请选择 .def 或 .sff"), Toast.LENGTH_SHORT).show();
                                }
                                else if (targetType == 5) {
                                    if (absPath.toLowerCase().endsWith(".snd")) { if(listener != null) listener.onFileSelected(f); pDialog.dismiss(); } 
                                    else Toast.makeText(getContext(), L("❌ 请选择 .snd 音频包"), Toast.LENGTH_SHORT).show();
                                }
                                else if (targetType == 6) { 
                                    if (absPath.toLowerCase().endsWith(".gif")) { if(listener != null) listener.onFileSelected(f); pDialog.dismiss(); }
                                    else Toast.makeText(getContext(), L("❌ 请选择 .gif 动画文件"), Toast.LENGTH_SHORT).show();
                                }
                                else if (targetType == 7) { 
                                    if (absPath.toLowerCase().endsWith(".png") || absPath.toLowerCase().endsWith(".jpg") || absPath.toLowerCase().endsWith(".jpeg") || absPath.toLowerCase().endsWith(".gif") || absPath.toLowerCase().endsWith(".pcx")) { if(listener != null) listener.onFileSelected(f); pDialog.dismiss(); }
                                    else Toast.makeText(getContext(), L("❌ 请选择图像文件用于替换 (支持 PNG/JPG/GIF/PCX)"), Toast.LENGTH_SHORT).show();
                                }
                                else if (targetType == 8) { 
                                    // 根据 sound.go 和 sound_xm.go 分析
                                    // Ikemen GO 底层全量适配以下格式：
                                    if (absPath.toLowerCase().endsWith(".wav") || absPath.toLowerCase().endsWith(".ogg") || 
                                        absPath.toLowerCase().endsWith(".mp3") || absPath.toLowerCase().endsWith(".flac") || 
                                        absPath.toLowerCase().endsWith(".xm") || absPath.toLowerCase().endsWith(".mod") || 
                                        absPath.toLowerCase().endsWith(".it") || absPath.toLowerCase().endsWith(".s3m") ||
                                        absPath.toLowerCase().endsWith(".mid")) { 
                                        if(listener != null) listener.onFileSelected(f); pDialog.dismiss(); 
                                    }
                                    else Toast.makeText(getContext(), L("❌ 请选择 Ikemen GO 支持的音频格式 (WAV/MP3/OGG/FLAC/XM等)"), Toast.LENGTH_SHORT).show();
                                }
                                else if (targetType == 9) {
                                    if (absPath.toLowerCase().endsWith(".act")) { if(listener != null) listener.onFileSelected(f); pDialog.dismiss(); }
                                    else Toast.makeText(getContext(), L("❌ 请选择 .act 调色板文件"), Toast.LENGTH_SHORT).show();
                                }
                                else if (targetType == 10) {
                                    if (absPath.toLowerCase().endsWith(".def")) {
                                        boolean isStage = false;
                                        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f))) {
                                            String line;
                                            while ((line = br.readLine()) != null) {
                                                String lower = line.toLowerCase().trim();
                                                if (lower.startsWith("[stageinfo]") || lower.startsWith("[bgdef]")) { isStage = true; break; }
                                            }
                                        } catch (Exception ignored) {}
                                        
                                        if (isStage) { if(listener != null) listener.onFileSelected(f); pDialog.dismiss(); }
                                        else { Toast.makeText(getContext(), L("❌ 引擎拦截：这是一个人物包或无效配置，只能选择地图工程！"), Toast.LENGTH_LONG).show(); }
                                    }
                                    else Toast.makeText(getContext(), L("❌ 请选择 .def 地图配置文件"), Toast.LENGTH_SHORT).show();
                                }
                                else if (targetType == 11) {
                                    String lowerPath = absPath.toLowerCase();
                                    if (lowerPath.endsWith(".gltf") || lowerPath.endsWith(".glb") || lowerPath.endsWith(".obj") || lowerPath.endsWith(".fbx") || lowerPath.endsWith(".3ds") || lowerPath.endsWith(".dae") || lowerPath.endsWith(".ply") || lowerPath.endsWith(".stl")) { 
                                        if(listener != null) listener.onFileSelected(f); pDialog.dismiss(); 
                                    } else {
                                        Toast.makeText(getContext(), L("❌ 格式不支持，请选择支持的 3D 格式"), Toast.LENGTH_SHORT).show();
                                    }
                                }
                                else if (targetType == 14) {
                                    if (absPath.toLowerCase().endsWith(".air") || absPath.toLowerCase().endsWith(".txt")) {
                                        if (listener != null) listener.onFileSelected(f);
                                        pDialog.dismiss();
                                    } else {
                                        Toast.makeText(getContext(), L("❌ 请选择 .air 或 .txt 动作文件"), Toast.LENGTH_SHORT).show();
                                    }
                                }
                                else if (targetType == 12 || targetType == 13) {
                                    if(listener != null) listener.onFileSelected(f); pDialog.dismiss(); 
                                }
                                else if (targetType == 1 || targetType == 2) { 
                                    if(targetType == 1) customDesktopBg = absPath; else customWindowBg = absPath;
                                    if(labelRef != null) labelRef.setText(L("壁纸: ") + f.getName()); refreshDesktopBackground(); pDialog.dismiss(); 
                                }
                                if (hostViewToRefresh != null) hostViewToRefresh.invalidate();
                            }
                        });
                        listLayout.addView(btn);
                    }
                } else {
                    TextView empty = new TextView(getContext()); empty.setText(L("  无权限读取或目录为空...")); applyGlobalFontSettings(empty, 1.0f, false); empty.setPadding(0, (int)(20*density), 0, 0); listLayout.addView(empty);
                }
            }
        };
        refreshList.run();
        FrameLayout.LayoutParams winParams = new FrameLayout.LayoutParams((int)(rootLayer.getWidth()*0.6f), (int)(rootLayer.getHeight()*0.75f)); winParams.gravity = Gravity.CENTER; overlay.addView(box, winParams);
        pDialog.setContentView(overlay); pDialog.show(); pDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
    }

    private TextView createTitle(String text) { TextView tv = new TextView(getContext()); tv.setText(text); applyGlobalFontSettings(tv, 1.3f, true); tv.setPadding(0, (int)(25*density), 0, (int)(10*density)); return tv; }
    private TextView createSubTitle(String text) { TextView tv = new TextView(getContext()); tv.setText(text); applyGlobalFontSettings(tv, 1.1f, false); tv.setPadding(0, (int)(15*density), 0, (int)(5*density)); return tv; }
    private EditText createInput(String hint, String text) {
        EditText et = new EditText(getContext()); et.setText(text); applyGlobalFontSettings(et, 1.0f, false); et.setHint(hint); et.setHintTextColor(Color.parseColor("#64748B"));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#1C212B")); bg.setCornerRadius(6f * density); bg.setStroke((int)(1 * density), Color.parseColor("#475569"));
        et.setBackground(bg); et.setPadding((int)(10*density), (int)(8*density), (int)(10*density), (int)(8*density)); return et;
    }
    private Button createButton(String text, String colorHex) {
        Button btn = new Button(getContext()); btn.setText(text); btn.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor(colorHex)); bg.setCornerRadius(7f * density); bg.setStroke((int)(1*density), Color.parseColor("#33FFFFFF")); btn.setBackground(bg);
        applyGlobalFontSettings(btn, 0.98f, false); btn.setTextColor(Color.WHITE); btn.setPadding((int)(13*density), (int)(7*density), (int)(13*density), (int)(7*density));
        btn.setOnTouchListener((v, e) -> { if (e.getAction() == MotionEvent.ACTION_DOWN) v.setAlpha(0.72f); else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) v.setAlpha(1.0f); return false; }); return btn;
    }

    // ======================================================================================
    // 🎨 模块 1：SFF 检视工坊
    // ======================================================================================
    private LinearLayout currentGalleryLayout = null;
    private TextView currentStatusText = null;
    private volatile boolean isAssetScannerRunning = false;

    private View buildSffExtractorContent() {
        LinearLayout root = new LinearLayout(getContext()); root.setOrientation(LinearLayout.VERTICAL); root.setPadding((int)(15*density), (int)(15*density), (int)(15*density), (int)(15*density));
        LinearLayout topBar = new LinearLayout(getContext()); topBar.setOrientation(LinearLayout.VERTICAL);
        Button scanBtn = createButton(L("📂 浏览并选择 SFF 素材文件"), "#0078D7");
        TextView statusText = new TextView(getContext()); statusText.setText(L(" 状态: 等待选取目录或文件...")); applyGlobalFontSettings(statusText, 0.92f, false); statusText.setPadding(0, (int)(6*density), 0, 0);
        topBar.addView(scanBtn, new LinearLayout.LayoutParams(-1, -2)); topBar.addView(statusText); root.addView(topBar);

        ScrollView scroll = new ScrollView(getContext()); LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, -1); scrollParams.setMargins(0, (int)(15*density), 0, 0); scroll.setLayoutParams(scrollParams);
        final LinearLayout galleryLayout = new LinearLayout(getContext()); galleryLayout.setOrientation(LinearLayout.VERTICAL); scroll.addView(galleryLayout); root.addView(scroll);

        scanBtn.setOnClickListener(v -> { if (isAssetScannerRunning) return; currentGalleryLayout = galleryLayout; currentStatusText = statusText; showWin10FilePicker(L("选择目录或 .def/.sff 素材文件"), 4, null, null, file -> startAssetScanner(file)); });
        return root;
    }

    private void startAssetScanner(File targetFile) {
        if (currentGalleryLayout != null) currentGalleryLayout.removeAllViews(); isAssetScannerRunning = true;
        
        new Thread(() -> {
            try { 
                updateUI(currentStatusText, L("📡 阶段 1/3: 正在无限深度检索本地文件..."));
                List<File> targetFiles = new ArrayList<>();
                if (targetFile.isDirectory()) { findFilesRecursively(targetFile, targetFiles, ".sff"); } 
                else { targetFiles.add(targetFile); }

                updateUI(currentStatusText, L("📡 阶段 2/3: 触发底层 Go 引擎获取智能预览..."));
                List<GoEngineBridge.SffInfo> validAssets = new ArrayList<>();
                for (File f : targetFiles) { 
                    List<GoEngineBridge.SffInfo> scanned = GoEngineBridge.scanSff(f.getAbsolutePath());
                    for (GoEngineBridge.SffInfo info : scanned) {
                        try {
                            byte[] pb = Api.getSffPreview(info.filePath);
                            if (pb != null && pb.length > 0) {
                                info.preview = BitmapFactory.decodeByteArray(pb, 0, pb.length);
                            }
                        } catch (Exception e) {}
                        validAssets.add(info);
                    }
                }
                
                if(validAssets == null || validAssets.isEmpty()) { updateUI(currentStatusText, L("⚠️ 未找到有效的 SFF 素材")); return; }
                
                updateUI(currentStatusText, L("🖥️ 阶段 3/3: 预检完毕，正在渲染安全界面..."));
                new Handler(Looper.getMainLooper()).post(() -> {
                    LinearLayout currentRow = null; int itemsInRow = 0;
                    for (GoEngineBridge.SffInfo va : validAssets) {
                        if (itemsInRow == 0) { currentRow = new LinearLayout(getContext()); currentRow.setOrientation(LinearLayout.HORIZONTAL); currentGalleryLayout.addView(currentRow, new LinearLayout.LayoutParams(-1, -2)); }
                        View card = buildAssetCard(va.name, va.filePath, va.preview, va.version);
                        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0, -2, 1f); cardParams.setMargins((int)(5*density), (int)(5*density), (int)(5*density), (int)(5*density));
                        currentRow.addView(card, cardParams); itemsInRow++; if (itemsInRow >= 3) itemsInRow = 0; 
                    }
                    currentStatusText.setText(L("✅ 解析完成! 成功挂载 ") + validAssets.size() + L(" 个无损资源"));
                });
            } catch (Exception e) { updateUI(currentStatusText, L("扫描异常: ") + e.getMessage()); } 
            finally { isAssetScannerRunning = false; }
        }).start();
    }

    private View buildAssetCard(final String name, final String sffPath, Bitmap previewBmp, String sffVersion) {
        LinearLayout card = new LinearLayout(getContext()); card.setOrientation(LinearLayout.VERTICAL); card.setGravity(Gravity.CENTER); card.setPadding((int)(10*density), (int)(10*density), (int)(10*density), (int)(10*density));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#2D2D30")); bg.setCornerRadius(8f*density); bg.setStroke(1, Color.parseColor("#3F3F46")); card.setBackground(bg);
        ImageView previewView = new ImageView(getContext()); previewView.setLayoutParams(new LinearLayout.LayoutParams((int)(90*density), (int)(90*density))); previewView.setScaleType(ImageView.ScaleType.FIT_CENTER); previewView.setBackgroundColor(Color.parseColor("#1E1E1E"));
        if (previewBmp != null) previewView.setImageBitmap(previewBmp); else previewView.setImageResource(android.R.drawable.ic_menu_gallery); 
        card.addView(previewView);
        TextView nameText = new TextView(getContext()); nameText.setText(name); nameText.setSingleLine(true); nameText.setGravity(Gravity.CENTER); nameText.setPadding(0, (int)(8*density), 0, (int)(2*density)); applyGlobalFontSettings(nameText, 0.9f, false); card.addView(nameText);
        TextView verText = new TextView(getContext()); verText.setText(sffVersion); verText.setSingleLine(true); verText.setGravity(Gravity.CENTER); verText.setPadding(0, 0, 0, (int)(8*density)); applyGlobalFontSettings(verText, 0.7f, false); verText.setTextColor(Color.GRAY); card.addView(verText);
        
        Button exportBtn = createButton(L("👁️ 打开查看器"), "#0078D7"); exportBtn.setPadding(0, (int)(5*density), 0, (int)(5*density));
        exportBtn.setOnClickListener(v -> {
            new Thread(() -> {
                try {
                    final List<GoEngineBridge.SffFrame> allFrames = GoEngineBridge.getAllFrames(sffPath);
                    new Handler(Looper.getMainLooper()).post(() -> showAssetViewerWindow(name, sffPath, allFrames));
                } catch (Exception e) {}
            }).start();
        });
        card.addView(exportBtn); return card;
    }

    private void showAssetViewerWindow(String charName, String sffPath, List<GoEngineBridge.SffFrame> allFrames) {
        final String winTitle = L("🎨 检视: ") + charName;
        LinearLayout root = new LinearLayout(getContext()); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#1E1E1E"));

        final List<GoEngineBridge.SffFrame> masterFrames = new ArrayList<>();
        if (allFrames != null) masterFrames.addAll(allFrames);

        final List<Integer> groupList = new ArrayList<>();
        final Runnable rebuildGroupList = () -> {
            groupList.clear();
            groupList.add(-999);
            for (GoEngineBridge.SffFrame f : masterFrames) {
                if (!groupList.contains(f.group)) groupList.add(f.group);
            }
        };
        rebuildGroupList.run();

        final List<GoEngineBridge.SffFrame> currentGroupFrames = new ArrayList<>();
        final int[] currentSelectedGroup = {-999};
        final int[] currentFrameIndex = {0};
        final boolean[] isPlaying = {false};
        final boolean[] showAxisCross = {true};
        final String[] currentActPath = {""};
        final GoEngineBridge.SffFrame[] activeFrameRef = {null};
        final Bitmap[] activeBmpRef = {null};

        TextView infoText = new TextView(getContext()); infoText.setPadding((int)(5*density), (int)(4*density), (int)(5*density), (int)(2*density)); applyGlobalFontSettings(infoText, 0.85f, false); infoText.setTextColor(Color.parseColor("#0078D7")); root.addView(infoText);

        FrameLayout canvasFrame = new FrameLayout(getContext()); canvasFrame.setTag("layout_preview"); LinearLayout.LayoutParams canvasParams = new LinearLayout.LayoutParams(-1, 0, 1f); canvasParams.setMargins((int)(5*density), (int)(5*density), (int)(5*density), (int)(5*density)); canvasFrame.setLayoutParams(canvasParams);
        Bitmap bgBmp = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888); Canvas bgCanvas = new Canvas(bgBmp); Paint bgPaint = new Paint(); bgPaint.setColor(Color.parseColor("#181818")); bgCanvas.drawRect(0,0,10,10,bgPaint); bgCanvas.drawRect(10,10,20,20,bgPaint); bgPaint.setColor(Color.parseColor("#252526")); bgCanvas.drawRect(10,0,20,10,bgPaint); bgCanvas.drawRect(0,10,10,20,bgPaint);
        BitmapDrawable tileBg = new BitmapDrawable(getContext().getResources(), bgBmp); tileBg.setTileModeXY(android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT);
        GradientDrawable canvasBorder = new GradientDrawable(); canvasBorder.setStroke((int)(1*density), Color.parseColor("#3F3F46")); canvasFrame.setBackground(new LayerDrawable(new android.graphics.drawable.Drawable[]{tileBg, canvasBorder}));

        final Matrix imageMatrix = new Matrix(); final Matrix savedMatrix = new Matrix(); final int[] touchMode = {0}; final PointF startPoint = new PointF(); final PointF midPoint = new PointF(); final float[] oldDist = {1f}; final boolean[] isMatrixInitialized = {false};

        final ImageView previewImg = new ImageView(getContext()); previewImg.setScaleType(ImageView.ScaleType.MATRIX); canvasFrame.addView(previewImg, new FrameLayout.LayoutParams(-1, -1));

        // 【新增】：轴心十字线叠加层，实时跟随缩放与平移
        final View axisOverlay = new View(getContext()) {
            private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (!showAxisCross[0] || activeFrameRef[0] == null) return;
                float[] pts = new float[]{ activeFrameRef[0].x, activeFrameRef[0].y };
                imageMatrix.mapPoints(pts);
                axisPaint.setColor(Color.argb(190, 0, 255, 180));
                axisPaint.setStrokeWidth(Math.max(1.5f, density));
                canvas.drawLine(0, pts[1], getWidth(), pts[1], axisPaint);
                canvas.drawLine(pts[0], 0, pts[0], getHeight(), axisPaint);
                axisPaint.setColor(Color.YELLOW);
                canvas.drawCircle(pts[0], pts[1], 3.5f * density, axisPaint);
            }
        };
        canvasFrame.addView(axisOverlay, new FrameLayout.LayoutParams(-1, -1));
        root.addView(canvasFrame);

        canvasFrame.setOnTouchListener((v, event) -> {
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN: savedMatrix.set(imageMatrix); startPoint.set(event.getX(), event.getY()); touchMode[0] = 1; break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (event.getPointerCount() >= 2) {
                        oldDist[0] = (float)Math.sqrt(Math.pow(event.getX(0)-event.getX(1), 2) + Math.pow(event.getY(0)-event.getY(1), 2));
                        if (oldDist[0] > 10f) { savedMatrix.set(imageMatrix); midPoint.set((event.getX(0)+event.getX(1))/2, (event.getY(0)+event.getY(1))/2); touchMode[0] = 2; }
                    }
                    break;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_POINTER_UP: case MotionEvent.ACTION_CANCEL: touchMode[0] = 0; break;
                case MotionEvent.ACTION_MOVE:
                    if (touchMode[0] == 1) { imageMatrix.set(savedMatrix); imageMatrix.postTranslate(event.getX() - startPoint.x, event.getY() - startPoint.y); }
                    else if (touchMode[0] == 2 && event.getPointerCount() >= 2) {
                        float newDist = (float)Math.sqrt(Math.pow(event.getX(0)-event.getX(1), 2) + Math.pow(event.getY(0)-event.getY(1), 2));
                        if (newDist > 10f && oldDist[0] > 10f) { imageMatrix.set(savedMatrix); float scale = newDist / oldDist[0]; imageMatrix.postScale(scale, scale, midPoint.x, midPoint.y); }
                    }
                    break;
            }
            previewImg.setImageMatrix(imageMatrix);
            axisOverlay.invalidate();
            return true;
        });

        LinearLayout bottomToolArea = new LinearLayout(getContext()); bottomToolArea.setTag("layout_toolbar"); bottomToolArea.setOrientation(LinearLayout.VERTICAL);
        HorizontalScrollView controlsScroll = new HorizontalScrollView(getContext()); controlsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout controls = new LinearLayout(getContext()); controls.setOrientation(LinearLayout.HORIZONTAL); controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding((int)(5*density), (int)(5*density), (int)(5*density), (int)(10*density));

        LinearLayout.LayoutParams uniformBtnParams = new LinearLayout.LayoutParams((int)(115*density), -2); uniformBtnParams.setMargins((int)(4*density), 0, (int)(4*density), 0);
        LinearLayout.LayoutParams wideBtnParams = new LinearLayout.LayoutParams((int)(140*density), -2); wideBtnParams.setMargins((int)(4*density), 0, (int)(4*density), 0);

        Button btnDefaultAct = createButton(L("🎨 内置色表"), "#4CAF50");
        Button btnAutoAct = createButton(L("🪄 自动色表"), "#9C27B0");
        Button btnManualAct = createButton(L("🎨 手动色表"), "#9C27B0");
        Button btnGroup = createButton(L("📁 动作编组"), "#1E1E1E");
        Button btnPrev = createButton(L("⏪ 上一帧"), "#3F3F46");
        Button btnPlay = createButton(L("▶️ 播放"), "#0078D7");
        Button btnNext = createButton(L("⏭️ 下一帧"), "#3F3F46");
        Button btnSpeed = createButton(L("⚙️ 调速"), "#3F3F46");
        Button btnAxisToggle = createButton(L("🎯 轴心线: 开"), "#00897B");
        Button btnResetView = createButton(L("🔍 复位视图"), "#3F3F46");
        Button btnOpenClsn = createButton(L("⚔️ 画判定框"), "#D81B60");
        Button btnExportNative = createButton(L("💾 导出帧"), "#3F3F46");
        Button btnBatchExport = createButton(L("📦 批量导出组"), "#0288D1");
        Button btnReplace = createButton(L("🔄 图像替换"), "#4CAF50");
        Button btnAddFrame = createButton(L("➕ 新增帧"), "#2E7D32");
        Button btnOffsetAxis = createButton(L("📐 调整轴心"), "#5C6BC0");
        Button btnDeleteFrame = createButton(L("🗑️ 删除帧"), "#E81123");

        final int[] currentDelay = {16};
        btnSpeed.setOnClickListener(v -> {
            final Dialog spdDialog = new Dialog(getContext()); spdDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            LinearLayout spdLayout = new LinearLayout(getContext()); spdLayout.setOrientation(LinearLayout.VERTICAL); spdLayout.setBackgroundColor(Color.parseColor("#2D2D30")); spdLayout.setPadding((int)(20*density), (int)(20*density), (int)(20*density), (int)(20*density));
            TextView title = new TextView(getContext()); title.setText(L("调整播放速度 (FPS)")); applyGlobalFontSettings(title, 1.0f, true); title.setGravity(Gravity.CENTER); spdLayout.addView(title);
            SeekBar speedBar = new SeekBar(getContext()); speedBar.setMax(59); speedBar.setProgress(Math.max(0, Math.min(59, (1000 / Math.max(1, currentDelay[0])) - 1)));
            speedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(SeekBar s, int p, boolean b) { currentDelay[0] = Math.max(16, 1000 / (p + 1)); title.setText(L("调整播放速度: ") + (p+1) + " FPS"); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){} });
            spdLayout.addView(speedBar, new LinearLayout.LayoutParams((int)(250*density), -2)); spdDialog.setContentView(spdLayout); spdDialog.show();
        });

        final Handler uiHandler = new Handler(Looper.getMainLooper());
        final Handler playHandler = new Handler(Looper.getMainLooper());
        final long[] renderSeq = {0};
        final long[] playGeneration = {0};

        final class FrameUiHelper {
            void apply(GoEngineBridge.SffFrame targetFrame, Bitmap bmp) {
            activeFrameRef[0] = targetFrame;
            activeBmpRef[0] = bmp;
            if (bmp != null && bmp.getWidth() > 0 && bmp.getHeight() > 0) {
                previewImg.setImageBitmap(bmp);
                if (!isMatrixInitialized[0] && canvasFrame.getWidth() > 0 && canvasFrame.getHeight() > 0) {
                    float scale = Math.min((float)canvasFrame.getWidth() / bmp.getWidth(), (float)canvasFrame.getHeight() / bmp.getHeight());
                    if (scale > 1.5f) scale = 1.5f;
                    float dx = (canvasFrame.getWidth() - bmp.getWidth() * scale) / 2f;
                    float dy = (canvasFrame.getHeight() - bmp.getHeight() * scale) / 2f;
                    imageMatrix.setScale(scale, scale); imageMatrix.postTranslate(dx, dy); previewImg.setImageMatrix(imageMatrix); isMatrixInitialized[0] = true;
                }
            } else { previewImg.setImageBitmap(null); }
            axisOverlay.invalidate();
            infoText.setText(String.format(L("帧: ") + "%d / %d | " + L("动作: ") + "%d | " + L("索引: ") + "%d | " + L("尺寸: ") + "%dx%d | " + L("轴心: ") + "%d, %d", currentFrameIndex[0] + 1, currentGroupFrames.size(), targetFrame.group, targetFrame.item, targetFrame.width, targetFrame.height, targetFrame.x, targetFrame.y));
            }
        }
        final FrameUiHelper applyFrameToUi = new FrameUiHelper();

        Runnable updateFrameAction = new Runnable() {
            @Override public void run() {
                if (currentGroupFrames.isEmpty()) {
                    activeFrameRef[0] = null;
                    previewImg.setImageBitmap(null);
                    infoText.setText(L("⚠️ 当前分组无可用帧"));
                    axisOverlay.invalidate();
                    return;
                }
                if (currentFrameIndex[0] < 0) currentFrameIndex[0] = currentGroupFrames.size() - 1;
                if (currentFrameIndex[0] >= currentGroupFrames.size()) currentFrameIndex[0] = 0;
                final GoEngineBridge.SffFrame targetFrame = currentGroupFrames.get(currentFrameIndex[0]);
                final long seq = ++renderSeq[0];
                new Thread(() -> {
                    try {
                        byte[] bmpData = Api.decodeSffFrame(sffPath, targetFrame.group, targetFrame.item, currentActPath[0]);
                        final Bitmap bmp = (bmpData != null && bmpData.length > 0) ? BitmapFactory.decodeByteArray(bmpData, 0, bmpData.length) : null;
                        uiHandler.post(() -> {
                            if (seq != renderSeq[0]) return;
                            applyFrameToUi.apply(targetFrame, bmp);
                        });
                    } catch (Throwable ignored) {}
                }).start();
            }
        };

        Runnable reloadFromDisk = () -> {
            new Thread(() -> {
                List<GoEngineBridge.SffFrame> refreshed = GoEngineBridge.getAllFrames(sffPath);
                uiHandler.post(() -> {
                    masterFrames.clear();
                    if (refreshed != null) masterFrames.addAll(refreshed);
                    rebuildGroupList.run();
                    currentGroupFrames.clear();
                    if (currentSelectedGroup[0] == -999) currentGroupFrames.addAll(masterFrames);
                    else {
                        for (GoEngineBridge.SffFrame fr : masterFrames) {
                            if (fr.group == currentSelectedGroup[0]) currentGroupFrames.add(fr);
                        }
                    }
                    if (currentFrameIndex[0] >= currentGroupFrames.size()) currentFrameIndex[0] = Math.max(0, currentGroupFrames.size() - 1);
                    updateFrameAction.run();
                });
            }).start();
        };

        btnAxisToggle.setOnClickListener(v -> {
            showAxisCross[0] = !showAxisCross[0];
            btnAxisToggle.setText(showAxisCross[0] ? L("🎯 轴心线: 开") : L("🎯 轴心线: 关"));
            axisOverlay.invalidate();
        });

        btnResetView.setOnClickListener(v -> {
            isMatrixInitialized[0] = false;
            updateFrameAction.run();
        });

        btnOpenClsn.setOnClickListener(v -> {
            isPlaying[0] = false; playHandler.removeCallbacksAndMessages(null);
            btnPlay.setText(L("▶️ 播放")); btnPlay.setBackgroundColor(Color.parseColor("#0078D7"));
            openAppWindow(L("⚔️ 判定框工坊 (打击/防御框)"), buildClsnEditorContent(sffPath, currentActPath[0]), null);
        });

        btnDefaultAct.setOnClickListener(v -> {
            currentActPath[0] = "";
            Toast.makeText(getContext(), L("✅ 已恢复内置色表"), Toast.LENGTH_SHORT).show();
            updateFrameAction.run();
        });

        btnAutoAct.setOnClickListener(v -> {
            File parentDir = new File(sffPath).getParentFile();
            File[] actFiles = (parentDir != null) ? parentDir.listFiles((d, name) -> name.toLowerCase().endsWith(".act")) : null;
            if (actFiles != null && actFiles.length > 0) {
                currentActPath[0] = actFiles[0].getAbsolutePath();
                Toast.makeText(getContext(), L("✅ 已自动挂载: ") + actFiles[0].getName(), Toast.LENGTH_SHORT).show();
                updateFrameAction.run();
            } else {
                Toast.makeText(getContext(), L("❌ 当前目录下未找到 .act 文件"), Toast.LENGTH_SHORT).show();
            }
        });

        btnManualAct.setOnClickListener(v -> {
            showWin10FilePicker(L("选择 ACT 调色板"), 9, null, null, selectedFile -> {
                currentActPath[0] = selectedFile.getAbsolutePath();
                Toast.makeText(getContext(), L("✅ 已挂载: ") + selectedFile.getName(), Toast.LENGTH_SHORT).show();
                updateFrameAction.run();
            });
        });

        btnGroup.setOnClickListener(v -> {
            final Dialog groupDialog = new Dialog(getContext());
            groupDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            LinearLayout gLayout = new LinearLayout(getContext()); gLayout.setOrientation(LinearLayout.VERTICAL); gLayout.setBackgroundColor(Color.parseColor("#2D2D30")); gLayout.setPadding((int)(20*density), (int)(20*density), (int)(20*density), (int)(20*density));
            TextView title = new TextView(getContext()); title.setText(L("选择动作编组")); applyGlobalFontSettings(title, 1.1f, true); title.setGravity(Gravity.CENTER); title.setPadding(0, 0, 0, (int)(10*density)); gLayout.addView(title);

            EditText searchInput = createInput(L("🔍 输入动作组号搜索..."), "");
            LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, -2); searchParams.setMargins(0, 0, 0, (int)(15*density)); gLayout.addView(searchInput, searchParams);

            ScrollView gScroll = new ScrollView(getContext());
            final LinearLayout gList = new LinearLayout(getContext()); gList.setOrientation(LinearLayout.VERTICAL);

            for (final int g : groupList) {
                String btnText = (g == -999) ? L("📂 所有动作") : L("📁 动作组 ") + g;
                Button groupBtn = createButton(btnText, "#1E1E1E"); groupBtn.setTag(String.valueOf(g));
                LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, -2); gp.setMargins(0, 0, 0, (int)(5*density));
                groupBtn.setOnClickListener(gv -> {
                    currentSelectedGroup[0] = g;
                    currentGroupFrames.clear();
                    if (g == -999) currentGroupFrames.addAll(masterFrames);
                    else for (GoEngineBridge.SffFrame f : masterFrames) { if (f.group == g) currentGroupFrames.add(f); }
                    currentFrameIndex[0] = 0; updateFrameAction.run();
                    btnGroup.setText(btnText);
                    groupDialog.dismiss();
                });
                gList.addView(groupBtn, gp);
            }

            searchInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    String query = s.toString().trim();
                    for (int i = 0; i < gList.getChildCount(); i++) {
                        View child = gList.getChildAt(i);
                        if (child instanceof Button) {
                            String tag = String.valueOf(child.getTag());
                            String text = ((Button) child).getText().toString();
                            child.setVisibility((query.isEmpty() || text.contains(query) || tag.equals(query)) ? View.VISIBLE : View.GONE);
                        }
                    }
                }
            });

            gScroll.addView(gList);
            gLayout.addView(gScroll, new LinearLayout.LayoutParams((int)(250*density), (int)(300*density)));
            groupDialog.setContentView(gLayout); groupDialog.show();
        });

        btnReplace.setOnClickListener(v -> {
            if (currentGroupFrames.isEmpty() || currentFrameIndex[0] < 0 || currentFrameIndex[0] >= currentGroupFrames.size()) return;
            isPlaying[0] = false; playHandler.removeCallbacksAndMessages(null);
            btnPlay.setText(L("▶️ 播放")); btnPlay.setBackgroundColor(Color.parseColor("#0078D7"));
            final GoEngineBridge.SffFrame f = currentGroupFrames.get(currentFrameIndex[0]);

            showWin10FilePicker(L("选择替换用的图像或所在目录"), 7, null, null, selectedFile -> {
                FileCallback doReplace = finalFile -> {
                    new Thread(() -> {
                        boolean success = Api.replaceSffFrame(sffPath, f.group, f.item, (short)f.x, (short)f.y, finalFile.getAbsolutePath());
                        uiHandler.post(() -> {
                            if (success) {
                                Toast.makeText(getContext(), L("✅ ") + f.group + "-" + f.item + L(" 帧已替换！"), Toast.LENGTH_SHORT).show();
                                reloadFromDisk.run();
                            } else {
                                Toast.makeText(getContext(), L("❌ 替换失败：格式不兼容！SFFv1 只能使用 PCX，SFFv2 禁止使用 PCX！"), Toast.LENGTH_LONG).show();
                            }
                        });
                    }).start();
                };
                if (selectedFile.isDirectory()) showImageGridPicker(selectedFile, doReplace); else doReplace.onFileSelected(selectedFile);
            });
        });

        // 【新增】：向 SFF 注入新增帧 (复用底层 Api.addSffFrame)
        btnAddFrame.setOnClickListener(v -> {
            isPlaying[0] = false; playHandler.removeCallbacksAndMessages(null);
            btnPlay.setText(L("▶️ 播放")); btnPlay.setBackgroundColor(Color.parseColor("#0078D7"));
            showWin10FilePicker(L("选择要新增的图像素材"), 7, null, null, selectedFile -> {
                FileCallback doAdd = finalFile -> {
                    final Dialog d = new Dialog(getContext()); d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                    LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setPadding((int)(20*density), (int)(20*density), (int)(20*density), (int)(20*density));
                    GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#252526")); border.setStroke((int)(2*density), Color.parseColor("#4CAF50")); border.setCornerRadius(12*density); box.setBackground(border);
                    box.addView(createSubTitle(L("➕ 新增 SFF 图像帧参数")));
                    int defG = (activeFrameRef[0] != null) ? activeFrameRef[0].group : 0;
                    int defI = (activeFrameRef[0] != null) ? (activeFrameRef[0].item + 1) : 0;
                    EditText gIn = createInput(L("动作组 (Group)"), String.valueOf(defG)); gIn.setInputType(InputType.TYPE_CLASS_NUMBER); box.addView(gIn);
                    EditText iIn = createInput(L("索引项 (Item)"), String.valueOf(defI)); iIn.setInputType(InputType.TYPE_CLASS_NUMBER); box.addView(iIn);
                    EditText xIn = createInput(L("轴心 X"), "0"); xIn.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED); box.addView(xIn);
                    EditText yIn = createInput(L("轴心 Y"), "0"); yIn.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED); box.addView(yIn);
                    Button bConf = createButton(L("✔️ 确认新增帧"), "#4CAF50");
                    bConf.setOnClickListener(cv -> {
                        int g = 0, it = 0, ax = 0, ay = 0;
                        try { g = Integer.parseInt(gIn.getText().toString().trim()); } catch (Exception ignored) {}
                        try { it = Integer.parseInt(iIn.getText().toString().trim()); } catch (Exception ignored) {}
                        try { ax = Integer.parseInt(xIn.getText().toString().trim()); } catch (Exception ignored) {}
                        try { ay = Integer.parseInt(yIn.getText().toString().trim()); } catch (Exception ignored) {}
                        final int fg = g, fi = it; final short fax = (short)ax, fay = (short)ay;
                        new Thread(() -> {
                            boolean ok = Api.addSffFrame(sffPath, fg, fi, fax, fay, finalFile.getAbsolutePath());
                            uiHandler.post(() -> {
                                if (ok) { Toast.makeText(getContext(), L("✅ 新增帧成功！"), Toast.LENGTH_SHORT).show(); d.dismiss(); reloadFromDisk.run(); }
                                else { Toast.makeText(getContext(), L("❌ 新增失败：可能编号重复或图像格式不兼容"), Toast.LENGTH_LONG).show(); }
                            });
                        }).start();
                    });
                    box.addView(bConf, new LinearLayout.LayoutParams(-1, -2)); d.setContentView(box); d.show();
                };
                if (selectedFile.isDirectory()) showImageGridPicker(selectedFile, doAdd); else doAdd.onFileSelected(selectedFile);
            });
        });

        // 【新增】：调整单帧/整组轴心偏移 (复用底层 Api.offsetSffCoords)
        btnOffsetAxis.setOnClickListener(v -> {
            if (currentGroupFrames.isEmpty() || currentFrameIndex[0] < 0 || currentFrameIndex[0] >= currentGroupFrames.size()) return;
            final GoEngineBridge.SffFrame f = currentGroupFrames.get(currentFrameIndex[0]);
            final Dialog d = new Dialog(getContext()); d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setPadding((int)(20*density), (int)(20*density), (int)(20*density), (int)(20*density));
            GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#252526")); border.setStroke((int)(2*density), Color.parseColor("#5C6BC0")); border.setCornerRadius(12*density); box.setBackground(border);
            box.addView(createSubTitle(L("📐 调整轴心偏移 (当前: ") + f.x + ", " + f.y + ")"));
            EditText dxIn = createInput(L("X 偏移增量 (如 -5 或 10)"), "0"); dxIn.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED); box.addView(dxIn);
            EditText dyIn = createInput(L("Y 偏移增量 (如 -5 或 10)"), "0"); dyIn.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED); box.addView(dyIn);
            Button bSingle = createButton(L("✔️ 仅调整当前帧"), "#0078D7");
            Button bGroup = createButton(L("✔️ 调整当前动作整组"), "#FF9800");
            View.OnClickListener applyOffset = ev -> {
                int dx = 0, dy = 0;
                try { dx = Integer.parseInt(dxIn.getText().toString().trim()); } catch (Exception ignored) {}
                try { dy = Integer.parseInt(dyIn.getText().toString().trim()); } catch (Exception ignored) {}
                final short sdx = (short)dx, sdy = (short)dy;
                final boolean singleOnly = (ev == bSingle);
                new Thread(() -> {
                    boolean ok = Api.offsetSffCoords(sffPath, f.group, f.item, sdx, sdy, singleOnly);
                    uiHandler.post(() -> {
                        if (ok) { Toast.makeText(getContext(), L("✅ 轴心坐标已更新！"), Toast.LENGTH_SHORT).show(); d.dismiss(); reloadFromDisk.run(); }
                        else { Toast.makeText(getContext(), L("❌ 轴心修改失败"), Toast.LENGTH_SHORT).show(); }
                    });
                }).start();
            };
            bSingle.setOnClickListener(applyOffset); bGroup.setOnClickListener(applyOffset);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, (int)(8*density), 0, 0);
            box.addView(bSingle, lp); box.addView(bGroup, lp); d.setContentView(box); d.show();
        });

        // 【新增】：删除当前帧 (带二次确认防误删)
        btnDeleteFrame.setOnClickListener(v -> {
            if (currentGroupFrames.isEmpty() || currentFrameIndex[0] < 0 || currentFrameIndex[0] >= currentGroupFrames.size()) return;
            final GoEngineBridge.SffFrame f = currentGroupFrames.get(currentFrameIndex[0]);
            final Dialog d = new Dialog(getContext()); d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setPadding((int)(20*density), (int)(20*density), (int)(20*density), (int)(20*density));
            GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#252526")); border.setStroke((int)(2*density), Color.parseColor("#E81123")); border.setCornerRadius(12*density); box.setBackground(border);
            box.addView(createSubTitle(L("⚠️ 确认永久删除帧 ") + f.group + "-" + f.item + " ?"));
            Button bDel = createButton(L("🗑️ 确认永久删除"), "#E81123");
            Button bCan = createButton(L("❌ 取消"), "#333333");
            bDel.setOnClickListener(dv -> {
                d.dismiss();
                new Thread(() -> {
                    boolean ok = Api.deleteSffFrame(sffPath, f.group, f.item);
                    uiHandler.post(() -> {
                        if (ok) { Toast.makeText(getContext(), L("✅ 帧已永久删除！"), Toast.LENGTH_SHORT).show(); reloadFromDisk.run(); }
                        else { Toast.makeText(getContext(), L("❌ 删除失败"), Toast.LENGTH_SHORT).show(); }
                    });
                }).start();
            });
            bCan.setOnClickListener(cv -> d.dismiss());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, (int)(8*density), 0, 0);
            box.addView(bDel, lp); box.addView(bCan, lp); d.setContentView(box); d.show();
        });

        // 【新增】：批量导出当前分组所有帧为 PNG
        btnBatchExport.setOnClickListener(v -> {
            if (currentGroupFrames.isEmpty()) return;
            final List<GoEngineBridge.SffFrame> snapshot = new ArrayList<>(currentGroupFrames);
            Toast.makeText(getContext(), L("📦 正在批量导出 ") + snapshot.size() + L(" 张 PNG..."), Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                int okCount = 0;
                String baseName = new File(sffPath).getName().replaceAll("\\.[^.]+$", "");
                File outDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "IkemenExports/" + baseName + "_Batch");
                if (!outDir.exists()) outDir.mkdirs();
                for (GoEngineBridge.SffFrame fr : snapshot) {
                    try {
                        byte[] pngData = Api.decodeSffFrame(sffPath, fr.group, fr.item, currentActPath[0]);
                        if (pngData != null && pngData.length > 0) {
                            File outFile = new File(outDir, baseName + "_G" + fr.group + "_I" + fr.item + ".png");
                            try (FileOutputStream fos = new FileOutputStream(outFile)) { fos.write(pngData); }
                            okCount++;
                        }
                    } catch (Throwable ignored) {}
                }
                final int exported = okCount;
                uiHandler.post(() -> Toast.makeText(getContext(), L("✅ 批量导出完成: ") + exported + " / " + snapshot.size() + "\n" + outDir.getAbsolutePath(), Toast.LENGTH_LONG).show());
            }).start();
        });

        currentGroupFrames.addAll(masterFrames);
        canvasFrame.post(updateFrameAction);

        // 核心修复：使用单线程串行解码播放循环（解码完当前帧并渲染后再进入下一帧），彻底根治大体积 SFF 播放卡住与暂停跳帧问题
        final Runnable startSequentialPlayback = () -> {
            final long myGen = ++playGeneration[0];
            new Thread(() -> {
                while (isPlaying[0] && myGen == playGeneration[0]) {
                    long t0 = System.currentTimeMillis();
                    int total = currentGroupFrames.size();
                    if (total <= 0) break;
                    int idx = currentFrameIndex[0] + 1;
                    if (idx < 0 || idx >= total) idx = 0;
                    final int framePos = idx;
                    final GoEngineBridge.SffFrame tf;
                    try {
                        tf = currentGroupFrames.get(framePos);
                    } catch (Exception e) { break; }

                    Bitmap decoded = null;
                    try {
                        byte[] bmpData = Api.decodeSffFrame(sffPath, tf.group, tf.item, currentActPath[0]);
                        if (bmpData != null && bmpData.length > 0) {
                            decoded = BitmapFactory.decodeByteArray(bmpData, 0, bmpData.length);
                        }
                    } catch (Throwable ignored) {}

                    if (!isPlaying[0] || myGen != playGeneration[0]) break;
                    final Bitmap finalBmp = decoded;
                    final java.util.concurrent.CountDownLatch uiLatch = new java.util.concurrent.CountDownLatch(1);
                    uiHandler.post(() -> {
                        try {
                            if (isPlaying[0] && myGen == playGeneration[0]) {
                                currentFrameIndex[0] = framePos;
                                applyFrameToUi.apply(tf, finalBmp);
                            }
                        } finally {
                            uiLatch.countDown();
                        }
                    });
                    try { uiLatch.await(250, java.util.concurrent.TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) {}

                    long elapsed = System.currentTimeMillis() - t0;
                    long waitMs = Math.max(0L, currentDelay[0] - elapsed);
                    if (waitMs > 0 && isPlaying[0] && myGen == playGeneration[0]) {
                        try { Thread.sleep(waitMs); } catch (InterruptedException ignored) {}
                    }
                }
            }).start();
        };

        btnPrev.setOnClickListener(v -> {
            if (isPlaying[0]) { isPlaying[0] = false; playGeneration[0]++; btnPlay.setText(L("▶️ 播放")); btnPlay.setBackgroundColor(Color.parseColor("#0078D7")); }
            currentFrameIndex[0]--; updateFrameAction.run();
        });
        btnNext.setOnClickListener(v -> {
            if (isPlaying[0]) { isPlaying[0] = false; playGeneration[0]++; btnPlay.setText(L("▶️ 播放")); btnPlay.setBackgroundColor(Color.parseColor("#0078D7")); }
            currentFrameIndex[0]++; updateFrameAction.run();
        });
        btnPlay.setOnClickListener(v -> {
            if (currentGroupFrames.isEmpty()) return;
            isPlaying[0] = !isPlaying[0];
            btnPlay.setText(isPlaying[0] ? L("⏸️ 暂停") : L("▶️ 播放"));
            btnPlay.setBackgroundColor(isPlaying[0] ? Color.parseColor("#E81123") : Color.parseColor("#0078D7"));
            if (isPlaying[0]) {
                startSequentialPlayback.run();
            } else {
                playGeneration[0]++;
                playHandler.removeCallbacksAndMessages(null);
            }
        });

        btnExportNative.setOnClickListener(v -> {
            if (currentGroupFrames.isEmpty() || currentFrameIndex[0] < 0 || currentFrameIndex[0] >= currentGroupFrames.size()) return;
            GoEngineBridge.SffFrame f = currentGroupFrames.get(currentFrameIndex[0]);

            final Dialog formatDialog = new Dialog(getContext());
            formatDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            LinearLayout fLayout = new LinearLayout(getContext()); fLayout.setOrientation(LinearLayout.VERTICAL); fLayout.setBackgroundColor(Color.parseColor("#2D2D30")); fLayout.setPadding((int)(20*density), (int)(20*density), (int)(20*density), (int)(20*density));

            TextView title = new TextView(getContext()); title.setText(L("选择导出格式")); applyGlobalFontSettings(title, 1.1f, true); title.setGravity(Gravity.CENTER); title.setPadding(0, 0, 0, (int)(15*density)); fLayout.addView(title);
            Button btnPcx = createButton(L("💾 导出为 PCX (原生)"), "#0078D7");
            Button btnPng = createButton(L("💾 导出为 PNG (通用)"), "#4CAF50");
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams((int)(200*density), -2); bp.setMargins(0, 0, 0, (int)(10*density));

            View.OnClickListener exportAction = ev -> {
                boolean wantPng = (ev == btnPng);
                formatDialog.dismiss();
                new Thread(() -> {
                    File outDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "IkemenExports");
                    if (!outDir.exists()) outDir.mkdirs();
                    try {
                        String finalPath = "";
                        if (wantPng) {
                            byte[] pngData = Api.decodeSffFrame(sffPath, f.group, f.item, currentActPath[0]);
                            if (pngData != null && pngData.length > 0) {
                                String exportName = new File(sffPath).getName().replaceAll("\\.[^.]+$", "");
                                File outFile = new File(outDir, exportName + "_G" + f.group + "_I" + f.item + ".png");
                                try (FileOutputStream fos = new FileOutputStream(outFile)) { fos.write(pngData); }
                                finalPath = outFile.getAbsolutePath();
                            }
                        } else {
                            finalPath = Api.exportSffFrameNative(sffPath, f.group, f.item, currentActPath[0], outDir.getAbsolutePath());
                        }
                        final String outResult = finalPath;
                        uiHandler.post(() -> {
                            if (outResult != null && !outResult.isEmpty()) Toast.makeText(getContext(), L("✅ 导出成功: ") + outResult, Toast.LENGTH_LONG).show();
                            else Toast.makeText(getContext(), L("❌ 导出失败：解析异常或写入失败"), Toast.LENGTH_LONG).show();
                        });
                    } catch (Exception e) {
                        uiHandler.post(() -> Toast.makeText(getContext(), L("❌ 导出崩溃: ") + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }).start();
            };

            btnPcx.setOnClickListener(exportAction); btnPng.setOnClickListener(exportAction);
            fLayout.addView(btnPcx, bp); fLayout.addView(btnPng, bp);
            formatDialog.setContentView(fLayout); formatDialog.show();
        });

        controls.addView(btnDefaultAct, uniformBtnParams);
        controls.addView(btnAutoAct, uniformBtnParams);
        controls.addView(btnManualAct, uniformBtnParams);
        controls.addView(btnGroup, uniformBtnParams);
        controls.addView(btnPrev, wideBtnParams);
        controls.addView(btnPlay, wideBtnParams);
        controls.addView(btnNext, wideBtnParams);
        controls.addView(btnSpeed, uniformBtnParams);
        controls.addView(btnAxisToggle, uniformBtnParams);
        controls.addView(btnResetView, uniformBtnParams);
        controls.addView(btnOpenClsn, uniformBtnParams);
        controls.addView(btnExportNative, uniformBtnParams);
        controls.addView(btnBatchExport, uniformBtnParams);
        controls.addView(btnReplace, uniformBtnParams);
        controls.addView(btnAddFrame, uniformBtnParams);
        controls.addView(btnOffsetAxis, uniformBtnParams);
        controls.addView(btnDeleteFrame, uniformBtnParams);

        controlsScroll.addView(controls);
        bottomToolArea.addView(controlsScroll);
        root.addView(bottomToolArea);

        openAppWindow(winTitle, root, () -> {
            isPlaying[0] = false; playGeneration[0]++; playHandler.removeCallbacksAndMessages(null);
            View win = windowsLayer.findViewWithTag(winTitle); if (win != null) windowsLayer.removeView(win);
            View tbBtn = taskbarAppsLayout.findViewWithTag("tb_" + winTitle); if (tbBtn != null) taskbarAppsLayout.removeView(tbBtn);
        });
    }

    // ======================================================================================
    // 🎵 模块 2：SND 音频检视工坊
    // ======================================================================================
    private View buildSndExtractorContent() {
        LinearLayout root = new LinearLayout(getContext()); root.setOrientation(LinearLayout.VERTICAL); root.setPadding((int)(15*density), (int)(15*density), (int)(15*density), (int)(15*density));
        LinearLayout topBar = new LinearLayout(getContext()); topBar.setOrientation(LinearLayout.VERTICAL);
        Button scanBtn = createButton(L("📂 浏览并选择 SND 音频文件"), "#FF9800");
        TextView statusText = new TextView(getContext()); statusText.setText(L(" 状态: 等待选取 .snd 文件...")); applyGlobalFontSettings(statusText, 0.92f, false); statusText.setPadding(0, (int)(6*density), 0, 0);
        topBar.addView(scanBtn, new LinearLayout.LayoutParams(-1, -2)); topBar.addView(statusText); root.addView(topBar);

        ScrollView scroll = new ScrollView(getContext()); LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, -1); scrollParams.setMargins(0, (int)(15*density), 0, 0); scroll.setLayoutParams(scrollParams);
        final LinearLayout listLayout = new LinearLayout(getContext()); listLayout.setOrientation(LinearLayout.VERTICAL); scroll.addView(listLayout); root.addView(scroll);

        scanBtn.setOnClickListener(v -> {
            currentGalleryLayout = listLayout; currentStatusText = statusText; showWin10FilePicker(L("选择 .snd 音频包"), 5, null, null, file -> startSndScanner(file));
        });
        return root;
    }

    private void startSndScanner(File sndFile) {
        if (currentGalleryLayout != null) currentGalleryLayout.removeAllViews(); currentStatusText.setText(L("状态: 等待底层 Go 解析 SND..."));
        new Thread(() -> {
            try {
                List<File> validFiles = new ArrayList<>();
                if (sndFile.isDirectory()) { findFilesRecursively(sndFile, validFiles, ".snd"); } else { validFiles.add(sndFile); }
                if (validFiles.isEmpty()) { updateUI(currentStatusText, L("❌ 未找到SND文件")); return; }
                
                new Handler(Looper.getMainLooper()).post(() -> {
                    for(File f : validFiles) {
                        Button btn = createButton(L("🎵 打开: ") + f.getName(), "#FF9800");
                        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2); bp.setMargins(0,0,0,(int)(10*density));
                        btn.setOnClickListener(v -> {
                            Toast.makeText(getContext(), L("加载音频数据..."), Toast.LENGTH_SHORT).show();
                            new Thread(() -> {
                                List<GoEngineBridge.SndNode> nodes = GoEngineBridge.scanSnd(f.getAbsolutePath());
                                new Handler(Looper.getMainLooper()).post(() -> showSndViewerWindow(f.getAbsolutePath(), f.getName(), nodes));
                            }).start();
                        });
                        currentGalleryLayout.addView(btn, bp);
                    }
                    currentStatusText.setText(L("✅ 共发现 ") + validFiles.size() + L(" 个音频包"));
                });
            } catch (Exception e) { updateUI(currentStatusText, L("解析异常: ") + e.getMessage()); }
        }).start();
    }

    private void showSndViewerWindow(String sndPath, String sndName, List<GoEngineBridge.SndNode> allNodes) {
        final String winTitle = L("🎵 检视: ") + sndName;
        LinearLayout root = new LinearLayout(getContext()); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#1E1E1E"));
        final Handler uiHandler = new Handler(Looper.getMainLooper());
        final List<GoEngineBridge.SndNode> safeNodes = (allNodes != null) ? allNodes : new ArrayList<>();

        // 顶部搜索过滤与快捷操作栏（上下两行自适应，窄窗口模式绝不挤压）
        LinearLayout topTools = new LinearLayout(getContext()); topTools.setOrientation(LinearLayout.VERTICAL);
        topTools.setPadding((int)(8*density), (int)(6*density), (int)(8*density), (int)(4*density));
        final EditText searchSnd = createInput(L("🔍 搜索组号或编号 (如 0 或 0-1)..."), "");
        searchSnd.setSingleLine(true);
        topTools.addView(searchSnd, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout topBtnRow = new LinearLayout(getContext()); topBtnRow.setOrientation(LinearLayout.HORIZONTAL); topBtnRow.setGravity(Gravity.CENTER_VERTICAL);
        topBtnRow.setPadding(0, (int)(5*density), 0, 0);
        Button btnStopPlay = createButton(L("⏹️ 停止"), "#E81123");
        Button btnBatchWav = createButton(L("📦 批量导出组"), "#0288D1");
        LinearLayout.LayoutParams topBtnP1 = new LinearLayout.LayoutParams(0, -2, 1f); topBtnP1.setMargins(0, 0, (int)(6*density), 0);
        LinearLayout.LayoutParams topBtnP2 = new LinearLayout.LayoutParams(0, -2, 1.4f);
        topBtnRow.addView(btnStopPlay, topBtnP1);
        topBtnRow.addView(btnBatchWav, topBtnP2);
        topTools.addView(topBtnRow);
        root.addView(topTools);

        ScrollView scroll = new ScrollView(getContext()); scroll.setTag("layout_preview"); LinearLayout listLayout = new LinearLayout(getContext()); listLayout.setOrientation(LinearLayout.VERTICAL); scroll.addView(listLayout, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1f); scrollParams.setMargins((int)(8*density), (int)(4*density), (int)(8*density), (int)(4*density)); root.addView(scroll, scrollParams);

        HorizontalScrollView groupScroll = new HorizontalScrollView(getContext()); groupScroll.setTag("layout_toolbar"); groupScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout groupToolBelt = new LinearLayout(getContext()); groupToolBelt.setOrientation(LinearLayout.HORIZONTAL); groupToolBelt.setPadding((int)(8*density), (int)(8*density), (int)(8*density), (int)(8*density)); groupToolBelt.setBackgroundColor(Color.parseColor("#202632"));

        final int[] currentSelectedGroup = {-999};
        final String[] searchFilter = {""};

        Runnable stopAudioSafely = () -> {
            try {
                if (currentSndPlayer != null) {
                    if (currentSndPlayer.isPlaying()) currentSndPlayer.stop();
                    currentSndPlayer.release();
                    currentSndPlayer = null;
                }
            } catch (Exception ignored) { currentSndPlayer = null; }
        };
        btnStopPlay.setOnClickListener(v -> stopAudioSafely.run());

        final Runnable[] refreshList = {null};
        final Runnable[] rebuildGroupBelt = {null};

        refreshList[0] = () -> {
            listLayout.removeAllViews();
            int selectedGroup = currentSelectedGroup[0];
            String q = searchFilter[0].trim();
            for (final GoEngineBridge.SndNode n : safeNodes) {
                if (selectedGroup != -999 && n.group != selectedGroup) continue;
                if (!q.isEmpty()) {
                    String tag = n.group + "-" + n.item;
                    if (!tag.contains(q) && !String.valueOf(n.group).equals(q) && !String.valueOf(n.item).equals(q)) continue;
                }
                // 响应式双行卡片：窗口模式与全屏模式均清晰舒展，杜绝文字被挤成竖排
                LinearLayout row = new LinearLayout(getContext()); row.setOrientation(LinearLayout.VERTICAL); row.setPadding((int)(10*density), (int)(8*density), (int)(10*density), (int)(8*density));
                GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#232936")); bg.setCornerRadius(8f*density); bg.setStroke((int)(1*density), Color.parseColor("#334155")); row.setBackground(bg);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2); rowParams.setMargins(0, 0, 0, (int)(7*density));
                TextView info = new TextView(getContext()); info.setText(String.format(L("🎵 动作组 (Group): %d | 索引 (Item): %d"), n.group, n.item)); info.setSingleLine(true); info.setEllipsize(android.text.TextUtils.TruncateAt.END); applyGlobalFontSettings(info, 0.92f, true); info.setPadding(0, 0, 0, (int)(6*density)); row.addView(info, new LinearLayout.LayoutParams(-1, -2));

                // 【防卡顿/防闪退】：试听提取改到后台线程
                Button btnPlay = createButton(L("▶️ 试听"), "#FF9800"); btnPlay.setPadding((int)(10*density), (int)(6*density), (int)(10*density), (int)(6*density));
                btnPlay.setOnClickListener(v -> {
                    stopAudioSafely.run();
                    new Thread(() -> {
                        try {
                            byte[] wavData = Api.extractSndAudio(sndPath, n.group, n.item);
                            if (wavData != null && wavData.length > 0) {
                                final File tempWav = new File(getContext().getCacheDir(), "ikemen_preview.wav");
                                try (FileOutputStream fos = new FileOutputStream(tempWav)) { fos.write(wavData); }
                                uiHandler.post(() -> {
                                    try {
                                        stopAudioSafely.run();
                                        currentSndPlayer = new MediaPlayer();
                                        currentSndPlayer.setDataSource(tempWav.getAbsolutePath());
                                        currentSndPlayer.setOnCompletionListener(mp -> stopAudioSafely.run());
                                        currentSndPlayer.prepare();
                                        currentSndPlayer.start();
                                    } catch (Exception e) {
                                        Toast.makeText(getContext(), L("❌ 音频试听失败"), Toast.LENGTH_SHORT).show();
                                    }
                                });
                            } else {
                                uiHandler.post(() -> Toast.makeText(getContext(), L("❌ 无法解码该音频节点"), Toast.LENGTH_SHORT).show());
                            }
                        } catch (Throwable ignored) {}
                    }).start();
                });

                // 【防卡顿】：导出改到后台线程
                Button btnExport = createButton(L("💾 导出"), "#0078D7"); LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(-2, -2); btnParams.setMargins((int)(10*density), 0, 0, 0);
                btnExport.setOnClickListener(v -> {
                    new Thread(() -> {
                        try {
                            byte[] wavData = Api.extractSndAudio(sndPath, n.group, n.item);
                            if (wavData != null && wavData.length > 0) {
                                File outDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "IkemenExports");
                                if (!outDir.exists()) outDir.mkdirs();
                                String base = sndName.replaceAll("(?i)\\.snd$", "");
                                final File outFile = new File(outDir, base + "_G" + n.group + "_I" + n.item + ".wav");
                                try (FileOutputStream fos = new FileOutputStream(outFile)) { fos.write(wavData); }
                                uiHandler.post(() -> Toast.makeText(getContext(), L("✅ 已导出: ") + outFile.getAbsolutePath(), Toast.LENGTH_SHORT).show());
                            }
                        } catch (Exception e) {
                            uiHandler.post(() -> Toast.makeText(getContext(), L("❌ 导出失败"), Toast.LENGTH_SHORT).show());
                        }
                    }).start();
                });

                Button btnReplace = createButton(L("🔄 替换"), "#4CAF50");
                btnReplace.setOnClickListener(v -> {
                    stopAudioSafely.run();
                    showWin10FilePicker(L("选择替换用的音频或所在目录"), 8, null, null, selectedFile -> {
                        FileCallback doReplace = finalFile -> {
                            new Thread(() -> {
                                boolean success = Api.replaceSndAudio(sndPath, n.group, n.item, finalFile.getAbsolutePath());
                                uiHandler.post(() -> {
                                    if (success) { Toast.makeText(getContext(), L("✅ ") + n.group + "-" + n.item + L(" 音频已替换！"), Toast.LENGTH_SHORT).show(); }
                                    else { Toast.makeText(getContext(), L("❌ 音频格式不支持，替换失败"), Toast.LENGTH_LONG).show(); }
                                });
                            }).start();
                        };
                        if (selectedFile.isDirectory()) showAudioListPicker(selectedFile, doReplace); else doReplace.onFileSelected(selectedFile);
                    });
                });

                Button btnDelete = createButton(L("🗑️ 删除"), "#E81123");
                btnDelete.setOnClickListener(v -> {
                    final Dialog cd = new Dialog(getContext()); cd.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                    LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setPadding((int)(20*density), (int)(20*density), (int)(20*density), (int)(20*density));
                    GradientDrawable bdr = new GradientDrawable(); bdr.setColor(Color.parseColor("#252526")); bdr.setStroke((int)(2*density), Color.parseColor("#E81123")); bdr.setCornerRadius(12*density); box.setBackground(bdr);
                    box.addView(createSubTitle(L("⚠️ 确认永久删除音频 ") + n.group + "-" + n.item + " ?"));
                    Button bOk = createButton(L("🗑️ 确认永久删除"), "#E81123");
                    Button bNo = createButton(L("❌ 取消"), "#333333");
                    bOk.setOnClickListener(dv -> {
                        cd.dismiss();
                        stopAudioSafely.run();
                        new Thread(() -> {
                            boolean success = api.Api.deleteSndAudio(sndPath, n.group, n.item);
                            uiHandler.post(() -> {
                                if (success) {
                                    Toast.makeText(getContext(), L("✅ 音频已永久删除！"), Toast.LENGTH_SHORT).show();
                                    new Thread(() -> {
                                        List<GoEngineBridge.SndNode> newNodes = GoEngineBridge.scanSnd(sndPath);
                                        uiHandler.post(() -> { safeNodes.clear(); safeNodes.addAll(newNodes); rebuildGroupBelt[0].run(); refreshList[0].run(); });
                                    }).start();
                                } else { Toast.makeText(getContext(), L("❌ 删除失败"), Toast.LENGTH_SHORT).show(); }
                            });
                        }).start();
                    });
                    bNo.setOnClickListener(cv -> cd.dismiss());
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, (int)(8*density), 0, 0);
                    box.addView(bOk, lp); box.addView(bNo, lp); cd.setContentView(box); cd.show();
                });

                btnExport.setPadding((int)(10*density), (int)(6*density), (int)(10*density), (int)(6*density));
                btnReplace.setPadding((int)(10*density), (int)(6*density), (int)(10*density), (int)(6*density));
                btnDelete.setPadding((int)(10*density), (int)(6*density), (int)(10*density), (int)(6*density));
                HorizontalScrollView hsv = new HorizontalScrollView(getContext()); hsv.setHorizontalScrollBarEnabled(false); hsv.setFillViewport(true);
                LinearLayout btnContainer = new LinearLayout(getContext()); btnContainer.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams eqBp = new LinearLayout.LayoutParams(0, -2, 1f); eqBp.setMargins((int)(3*density), 0, (int)(3*density), 0);
                btnContainer.addView(btnPlay, eqBp); btnContainer.addView(btnExport, eqBp); btnContainer.addView(btnReplace, eqBp); btnContainer.addView(btnDelete, eqBp);
                hsv.addView(btnContainer, new FrameLayout.LayoutParams(-1, -2)); row.addView(hsv, new LinearLayout.LayoutParams(-1, -2)); listLayout.addView(row, rowParams);
            }
        };

        searchSnd.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { searchFilter[0] = s.toString(); refreshList[0].run(); }
        });

        btnBatchWav.setOnClickListener(v -> {
            final int selG = currentSelectedGroup[0];
            final List<GoEngineBridge.SndNode> targets = new ArrayList<>();
            for (GoEngineBridge.SndNode n : safeNodes) {
                if (selG == -999 || n.group == selG) targets.add(n);
            }
            if (targets.isEmpty()) return;
            Toast.makeText(getContext(), L("📦 正在批量导出 ") + targets.size() + L(" 个 WAV 音频..."), Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                int okCount = 0;
                String base = sndName.replaceAll("(?i)\\.snd$", "");
                File outDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "IkemenExports/" + base + "_SND");
                if (!outDir.exists()) outDir.mkdirs();
                for (GoEngineBridge.SndNode n : targets) {
                    try {
                        byte[] wavData = Api.extractSndAudio(sndPath, n.group, n.item);
                        if (wavData != null && wavData.length > 0) {
                            File outFile = new File(outDir, base + "_G" + n.group + "_I" + n.item + ".wav");
                            try (FileOutputStream fos = new FileOutputStream(outFile)) { fos.write(wavData); }
                            okCount++;
                        }
                    } catch (Throwable ignored) {}
                }
                final int exported = okCount;
                uiHandler.post(() -> Toast.makeText(getContext(), L("✅ 批量导出完成: ") + exported + " / " + targets.size() + "\n" + outDir.getAbsolutePath(), Toast.LENGTH_LONG).show());
            }).start();
        });

        Button btnAddSnd = createButton(L("➕ 新增音频"), "#4CAF50");
        LinearLayout.LayoutParams btnAddParams = new LinearLayout.LayoutParams(-2, -2); btnAddParams.setMargins(0, 0, (int)(15*density), 0);
        btnAddSnd.setOnClickListener(v -> {
            showWin10FilePicker(L("选择要强行注入的音频文件"), 8, null, null, selectedFile -> {
                FileCallback doAdd = finalFile -> {
                    final Dialog d = new Dialog(getContext()); d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                    LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setBackgroundColor(Color.parseColor("#252526")); box.setPadding((int)(20*density),(int)(20*density),(int)(20*density),(int)(20*density));
                    GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#252526")); border.setStroke((int)(2*density), Color.parseColor("#0078D7")); border.setCornerRadius(15*density); box.setBackground(border);

                    box.addView(createSubTitle(L("➕ 设定目标音频编号")));
                    EditText gIn = createInput(L("所属组 (Group)"), "0"); gIn.setInputType(InputType.TYPE_CLASS_NUMBER); box.addView(gIn);
                    EditText iIn = createInput(L("索引项 (Item)"), "0"); iIn.setInputType(InputType.TYPE_CLASS_NUMBER); box.addView(iIn);

                    Button bConf = createButton(L("✔️ 执行注入合并"), "#4CAF50");
                    bConf.setOnClickListener(v2 -> {
                        int addG = 0, addI = 0; try { addG = Integer.parseInt(gIn.getText().toString().trim()); addI = Integer.parseInt(iIn.getText().toString().trim()); } catch (Exception ignored) {}
                        final int fg = addG, fi = addI;
                        new Thread(() -> {
                            boolean success = api.Api.addSndAudio(sndPath, fg, fi, finalFile.getAbsolutePath());
                            uiHandler.post(() -> {
                                if (success) {
                                    Toast.makeText(getContext(), L("✅ 音频强行注入成功！"), Toast.LENGTH_SHORT).show(); d.dismiss();
                                    new Thread(() -> {
                                        List<GoEngineBridge.SndNode> newNodes = GoEngineBridge.scanSnd(sndPath);
                                        uiHandler.post(() -> { safeNodes.clear(); safeNodes.addAll(newNodes); rebuildGroupBelt[0].run(); refreshList[0].run(); });
                                    }).start();
                                } else { Toast.makeText(getContext(), L("❌ 注入失败，可能编号重复或格式不可读"), Toast.LENGTH_LONG).show(); }
                            });
                        }).start();
                    });
                    box.addView(bConf, new LinearLayout.LayoutParams(-1,-2)); d.setContentView(box); d.show();
                };
                if (selectedFile.isDirectory()) showAudioListPicker(selectedFile, doAdd); else doAdd.onFileSelected(selectedFile);
            });
        });

        rebuildGroupBelt[0] = () -> {
            groupToolBelt.removeAllViews();
            groupToolBelt.addView(btnAddSnd, btnAddParams);
            List<Integer> groupList = new ArrayList<>();
            groupList.add(-999);
            for (GoEngineBridge.SndNode n : safeNodes) { if (!groupList.contains(n.group)) groupList.add(n.group); }
            for (final int g : groupList) {
                String btnText = (g == -999) ? L("📂 所有音频") : L("📁 音频组 ") + g;
                Button groupBtn = createButton(btnText, "#1E1E1E");
                LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-2, -2); gp.setMargins(0, 0, (int)(5*density), 0);
                groupBtn.setOnClickListener(v -> { currentSelectedGroup[0] = g; refreshList[0].run(); });
                groupToolBelt.addView(groupBtn, gp);
            }
        };
        rebuildGroupBelt[0].run();
        groupScroll.addView(groupToolBelt);
        root.addView(groupScroll);

        refreshList[0].run();

        openAppWindow(winTitle, root, () -> {
            stopAudioSafely.run();
            View win = windowsLayer.findViewWithTag(winTitle); if (win != null) windowsLayer.removeView(win);
            View tbBtn = taskbarAppsLayout.findViewWithTag("tb_" + winTitle); if (tbBtn != null) taskbarAppsLayout.removeView(tbBtn);
        });
    }

    // ======================================================================================
    // 🎞️ 模块 3：GIF 拆解器 (全面支持动态网格预览与全标准控件窗口)
    // ======================================================================================
    private View buildGifExtractorContent() {
        LinearLayout root = new LinearLayout(getContext()); root.setOrientation(LinearLayout.VERTICAL); root.setPadding((int)(15*density), (int)(15*density), (int)(15*density), (int)(15*density));
        LinearLayout topBar = new LinearLayout(getContext()); topBar.setOrientation(LinearLayout.VERTICAL);
        Button scanBtn = createButton(L("📂 浏览并选择 GIF 文件"), "#0078D7");
        TextView statusText = new TextView(getContext()); statusText.setText(L(" 状态: 等待选取 .gif 文件...")); applyGlobalFontSettings(statusText, 0.92f, false); statusText.setPadding(0, (int)(6*density), 0, 0);
        topBar.addView(scanBtn, new LinearLayout.LayoutParams(-1, -2)); topBar.addView(statusText); root.addView(topBar);
        
        ScrollView scroll = new ScrollView(getContext()); 
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, -1); 
        scrollParams.setMargins(0, (int)(15*density), 0, 0); 
        scroll.setLayoutParams(scrollParams);
        final LinearLayout listLayout = new LinearLayout(getContext()); 
        listLayout.setOrientation(LinearLayout.VERTICAL); 
        scroll.addView(listLayout); 
        root.addView(scroll);

        scanBtn.setOnClickListener(v -> {
            currentGalleryLayout = listLayout; 
            currentStatusText = statusText; 
            showWin10FilePicker(L("选择 .gif 文件或所在目录"), 6, null, null, file -> {
                if (file.isDirectory()) {
                    startGifScanner(file);
                } else {
                    openGifViewerWindow(file);
                }
            });
        });
        return root;
    }

    private void startGifScanner(File gifDir) {
        if (currentGalleryLayout != null) currentGalleryLayout.removeAllViews(); 
        currentStatusText.setText(L("状态: 正在检索本地 GIF..."));
        new Thread(() -> {
            try {
                List<File> validFiles = new ArrayList<>();
                findFilesRecursively(gifDir, validFiles, ".gif");
                if (validFiles.isEmpty()) { updateUI(currentStatusText, L("❌ 未找到 GIF 文件")); return; }
                
                new Handler(Looper.getMainLooper()).post(() -> {
                    LinearLayout currentRow = null; int itemsInRow = 0;
                    for(File f : validFiles) {
                        if (itemsInRow == 0) { 
                            currentRow = new LinearLayout(getContext()); 
                            currentRow.setOrientation(LinearLayout.HORIZONTAL); 
                            currentGalleryLayout.addView(currentRow, new LinearLayout.LayoutParams(-1, -2)); 
                        }
                        View card = buildGifCard(f);
                        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0, -2, 1f); 
                        cardParams.setMargins((int)(5*density), (int)(5*density), (int)(5*density), (int)(5*density));
                        currentRow.addView(card, cardParams); 
                        itemsInRow++; 
                        if (itemsInRow >= 3) itemsInRow = 0;
                    }
                    currentStatusText.setText(L("✅ 共发现 ") + validFiles.size() + L(" 个 GIF 动画文件"));
                });
            } catch (Exception e) { updateUI(currentStatusText, L("解析异常: ") + e.getMessage()); }
        }).start();
    }

    // 专属的网格卡片：采用 WebView 搭载原生渲染，不吃 OOM 就能完美预览动态
    private View buildGifCard(File gifFile) {
        LinearLayout card = new LinearLayout(getContext()); 
        card.setOrientation(LinearLayout.VERTICAL); 
        card.setGravity(Gravity.CENTER); 
        card.setPadding((int)(10*density), (int)(10*density), (int)(10*density), (int)(10*density));
        GradientDrawable bg = new GradientDrawable(); 
        bg.setColor(Color.parseColor("#2D2D30")); 
        bg.setCornerRadius(8f*density); 
        bg.setStroke(1, Color.parseColor("#3F3F46")); 
        card.setBackground(bg);
        
        WebView previewView = new WebView(getContext()); 
        previewView.setLayoutParams(new LinearLayout.LayoutParams((int)(90*density), (int)(90*density))); 
        previewView.getSettings().setAllowFileAccess(true);
        previewView.setBackgroundColor(Color.parseColor("#1E1E1E"));
        // 利用 HTML 和底层浏览器内核播放 GIF
        String html = "<html style='margin:0;padding:0;'><body style='margin:0;padding:0;display:flex;justify-content:center;align-items:center;height:100vh;background-color:#1E1E1E;'><img src='file://" + gifFile.getAbsolutePath() + "' style='max-width:100%;max-height:100%;object-fit:contain;' /></body></html>";
        previewView.loadDataWithBaseURL("", html, "text/html", "utf-8", null);
        previewView.setOnTouchListener((v, event) -> true); // 拦截交互，让其充当静态 ImageView 行为
        card.addView(previewView);
        
        TextView nameText = new TextView(getContext()); 
        nameText.setText(gifFile.getName()); 
        nameText.setSingleLine(true); 
        nameText.setGravity(Gravity.CENTER); 
        nameText.setPadding(0, (int)(8*density), 0, (int)(2*density)); 
        applyGlobalFontSettings(nameText, 0.9f, false); 
        card.addView(nameText);
        
        Button exportBtn = createButton(L("👁️ 拆解查看"), "#9C27B0");
        exportBtn.setPadding(0, (int)(5*density), 0, (int)(5*density));
        exportBtn.setOnClickListener(v -> openGifViewerWindow(gifFile));
        card.addView(exportBtn);
        return card;
    }

    private void openGifViewerWindow(File gifFile) {
        final String winTitle = L("🎞️ GIF拆解: ") + gifFile.getName();
        LinearLayout root = new LinearLayout(getContext()); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#1E1E1E"));
        
        int totalFrames = (int) Api.getGifFrameCount(gifFile.getAbsolutePath());
        if (totalFrames <= 0) {
            Toast.makeText(getContext(), L("❌ 无法解析此 GIF，或者该文件已损坏"), Toast.LENGTH_SHORT).show(); return;
        }

        TextView infoText = new TextView(getContext()); infoText.setPadding((int)(10*density), (int)(8*density), (int)(10*density), (int)(4*density)); applyGlobalFontSettings(infoText, 0.85f, false); infoText.setTextColor(Color.parseColor("#0078D7")); root.addView(infoText);
        
        FrameLayout canvasFrame = new FrameLayout(getContext()); LinearLayout.LayoutParams canvasParams = new LinearLayout.LayoutParams(-1, 0, 1f); canvasParams.setMargins((int)(15*density), (int)(10*density), (int)(15*density), (int)(10*density)); canvasFrame.setLayoutParams(canvasParams);
        
        Bitmap bgBmp = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888); Canvas bgCanvas = new Canvas(bgBmp); Paint bgPaint = new Paint(); bgPaint.setColor(Color.parseColor("#181818")); bgCanvas.drawRect(0,0,10,10,bgPaint); bgCanvas.drawRect(10,10,20,20,bgPaint); bgPaint.setColor(Color.parseColor("#252526")); bgCanvas.drawRect(10,0,20,10,bgPaint); bgCanvas.drawRect(0,10,10,20,bgPaint);
        BitmapDrawable tileBg = new BitmapDrawable(getContext().getResources(), bgBmp); tileBg.setTileModeXY(android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT);
        
        GradientDrawable canvasBorder = new GradientDrawable(); canvasBorder.setStroke((int)(1*density), Color.parseColor("#3F3F46")); canvasFrame.setBackground(new LayerDrawable(new android.graphics.drawable.Drawable[]{tileBg, canvasBorder}));
        
        final ImageView previewImg = new ImageView(getContext()); previewImg.setScaleType(ImageView.ScaleType.FIT_CENTER); canvasFrame.addView(previewImg, new FrameLayout.LayoutParams(-1, -1)); root.addView(canvasFrame);
        
        LinearLayout bottomToolArea = new LinearLayout(getContext()); bottomToolArea.setOrientation(LinearLayout.VERTICAL);
        
        SeekBar frameSlider = new SeekBar(getContext()); frameSlider.setMax(totalFrames - 1); frameSlider.setPadding((int)(15*density), (int)(15*density), (int)(15*density), (int)(15*density));
        bottomToolArea.addView(frameSlider);
        
        LinearLayout controls = new LinearLayout(getContext()); controls.setOrientation(LinearLayout.HORIZONTAL); controls.setGravity(Gravity.CENTER); controls.setPadding((int)(15*density), (int)(5*density), (int)(15*density), (int)(15*density));
        
        Button btnPlay = createButton(L("▶️ 播放"), "#0078D7"); Button btnExportCurr = createButton(L("💾 导出当前帧"), "#FF9800"); Button btnExportAll = createButton(L("🚀 导出全部"), "#9C27B0");
        LinearLayout.LayoutParams btnP = new LinearLayout.LayoutParams(0, -2, 1f); btnP.setMargins((int)(2*density), 0, (int)(2*density), 0);
        controls.addView(btnPlay, btnP); controls.addView(btnExportCurr, btnP); controls.addView(btnExportAll, btnP);
        bottomToolArea.addView(controls); root.addView(bottomToolArea);
        
        final int[] currentFrame = {0}; final boolean[] isPlaying = {false};
        final Handler uiHandler = new Handler(Looper.getMainLooper());
        
        Runnable updateFrameAction = () -> {
            new Thread(() -> {
                byte[] bmpData = Api.decodeGifFrame(gifFile.getAbsolutePath(), currentFrame[0]);
                if (bmpData != null && bmpData.length > 0) {
                    final Bitmap bmp = BitmapFactory.decodeByteArray(bmpData, 0, bmpData.length);
                    uiHandler.post(() -> {
                        previewImg.setImageBitmap(bmp);
                        infoText.setText(String.format(L("帧: %d / %d"), currentFrame[0] + 1, totalFrames));
                        frameSlider.setProgress(currentFrame[0]);
                    });
                }
            }).start();
        };

        frameSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { if(fromUser) { currentFrame[0] = progress; updateFrameAction.run(); } }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { isPlaying[0] = false; btnPlay.setText(L("▶️ 播放")); btnPlay.setBackgroundColor(Color.parseColor("#0078D7")); }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        Handler playHandler = new Handler();
        Runnable playRunnable = new Runnable() { @Override public void run() { if (isPlaying[0]) { currentFrame[0] = (currentFrame[0] + 1) % totalFrames; updateFrameAction.run(); playHandler.postDelayed(this, 100); } } };

        btnPlay.setOnClickListener(v -> {
            isPlaying[0] = !isPlaying[0];
            btnPlay.setText(isPlaying[0] ? L("⏸️ 暂停") : L("▶️ 播放")); 
            btnPlay.setBackgroundColor(isPlaying[0] ? Color.parseColor("#E81123") : Color.parseColor("#0078D7"));
            if (isPlaying[0]) playHandler.post(playRunnable); else playHandler.removeCallbacksAndMessages(null);
        });

        btnExportCurr.setOnClickListener(v -> {
            new Thread(() -> {
                byte[] bmpData = Api.decodeGifFrame(gifFile.getAbsolutePath(), currentFrame[0]);
                if (bmpData != null) {
                    try {
                        File outDir = new File(Environment.getExternalStorageDirectory(), "ik_PNG/" + gifFile.getName().replace(".gif", "")); if (!outDir.exists()) outDir.mkdirs();
                        File outFile = new File(outDir, "frame_" + String.format("%04d", currentFrame[0]) + ".png");
                        FileOutputStream fos = new FileOutputStream(outFile); fos.write(bmpData); fos.close();
                        uiHandler.post(() -> Toast.makeText(getContext(), L("✅ 已导出: ") + outFile.getAbsolutePath(), Toast.LENGTH_SHORT).show());
                    } catch (Exception e) {}
                }
            }).start();
        });

        btnExportAll.setOnClickListener(v -> {
            Toast.makeText(getContext(), L("🚀 开始批量无损导出，请稍候..."), Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                File outDir = new File(Environment.getExternalStorageDirectory(), "ik_PNG/" + gifFile.getName().replace(".gif", "")); if (!outDir.exists()) outDir.mkdirs();
                for (int i = 0; i < totalFrames; i++) {
                    byte[] bmpData = Api.decodeGifFrame(gifFile.getAbsolutePath(), i);
                    if (bmpData != null) {
                        try {
                            File outFile = new File(outDir, "frame_" + String.format("%04d", i) + ".png");
                            FileOutputStream fos = new FileOutputStream(outFile); fos.write(bmpData); fos.close();
                        } catch (Exception e) {}
                    }
                }
                uiHandler.post(() -> Toast.makeText(getContext(), L("✅ 批量导出完成！保存至: ") + outDir.getAbsolutePath(), Toast.LENGTH_LONG).show());
            }).start();
        });

        updateFrameAction.run();

        // 统一入口，赋予了它包含 缩小、最大化、关闭 等标准窗口控制栏的功能
        openAppWindow(winTitle, root, () -> {
            isPlaying[0] = false; playHandler.removeCallbacksAndMessages(null);
            View win = windowsLayer.findViewWithTag(winTitle); if (win != null) windowsLayer.removeView(win);
            View tbBtn = taskbarAppsLayout.findViewWithTag("tb_" + winTitle); if (tbBtn != null) taskbarAppsLayout.removeView(tbBtn);
        });
    }

    // ======================================================================================
    // 🎨 模块 4：ACT 色表工坊 (终极完整版：等宽按键、双指缩放、网格挂载、无损调色)
    // ======================================================================================
    private View buildPaletteEditorContent() {
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#1E1E1E"));
        root.setPadding((int)(10*density), (int)(10*density), (int)(10*density), (int)(10*density));

        final String[] currentActPath = {""}; 
        final String[] originalActPath = {""}; 
        final String[] currentSffPath = {""};
        final boolean[] isEditMode = {false}; final byte[][] currentActData = {null}; 
        final boolean[] isRgbaFormat = {false};
        final List<GoEngineBridge.SffFrame> loadedFrames = new ArrayList<>();
        final int[] previewIndex = {0};

        HorizontalScrollView topScroll = new HorizontalScrollView(getContext());
        topScroll.setHorizontalScrollBarEnabled(false);
        topScroll.setFillViewport(true); 

        LinearLayout topBar = new LinearLayout(getContext());
        topBar.setOrientation(LinearLayout.HORIZONTAL); 
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        Button btnLoadAct = createButton(L("📂 挂载色表"), "#0078D7");
        Button btnLoadSff = createButton(L("🖼️ 挂载图像"), "#9C27B0");
        Button btnMode = createButton(L("👁️ 预览模式"), "#4CAF50");
        Button btnExtract = createButton(L("⬇️ 提取内置"), "#FF9800");

        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -2, 1f); 
        bp.setMargins(0, 0, (int)(4*density), 0);
        
        topBar.addView(btnLoadAct, bp); 
        topBar.addView(btnLoadSff, bp);
        topBar.addView(btnMode, bp); 
        topBar.addView(btnExtract, bp);
        
        topScroll.addView(topBar, new FrameLayout.LayoutParams(-1, -2)); 
        root.addView(topScroll);

        LinearLayout mainArea = new LinearLayout(getContext()); 
        mainArea.setOrientation(LinearLayout.HORIZONTAL);
        mainArea.setPadding(0, (int)(15*density), 0, 0);

        ScrollView gridScroll = new ScrollView(getContext());
        LinearLayout gridContainer = new LinearLayout(getContext()); 
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        gridContainer.setBackgroundColor(Color.parseColor("#2D2D30")); 
        gridContainer.setPadding((int)(5*density), (int)(5*density), (int)(5*density), (int)(5*density));
        
        final View[] colorBoxes = new View[256];
        for (int r = 0; r < 16; r++) {
            LinearLayout row = new LinearLayout(getContext()); row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 16; c++) {
                final int idx = r * 16 + c;
                View box = new View(getContext());
                LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams((int)(18*density), (int)(18*density));
                boxParams.setMargins((int)(1*density), (int)(1*density), (int)(1*density), (int)(1*density));
                box.setLayoutParams(boxParams); box.setBackgroundColor(Color.BLACK);
                colorBoxes[idx] = box; row.addView(box);
            }
            gridContainer.addView(row);
        }
        HorizontalScrollView gridHScroll = new HorizontalScrollView(getContext());
        gridHScroll.setHorizontalScrollBarEnabled(false);
        gridHScroll.addView(gridContainer);
        gridScroll.addView(gridHScroll);
        gridScroll.setTag("layout_left");
        mainArea.addView(gridScroll, new LinearLayout.LayoutParams(0, -1, 1f));

        LinearLayout rightArea = new LinearLayout(getContext());
        rightArea.setTag("layout_center");
        rightArea.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, -1, 1f);
        rightParams.setMargins((int)(15*density), 0, 0, 0);
        rightArea.setLayoutParams(rightParams);

        FrameLayout previewFrame = new FrameLayout(getContext());
        previewFrame.setTag("layout_preview");
        Bitmap bgBmp = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888); Canvas bgCanvas = new Canvas(bgBmp); Paint bgPaint = new Paint(); bgPaint.setColor(Color.parseColor("#181818")); bgCanvas.drawRect(0,0,10,10,bgPaint); bgCanvas.drawRect(10,10,20,20,bgPaint); bgPaint.setColor(Color.parseColor("#252526")); bgCanvas.drawRect(10,0,20,10,bgPaint); bgCanvas.drawRect(0,10,10,20,bgPaint);
        BitmapDrawable tileBg = new BitmapDrawable(getContext().getResources(), bgBmp); tileBg.setTileModeXY(android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT); 
        GradientDrawable canvasBorder = new GradientDrawable(); canvasBorder.setStroke((int)(1*density), Color.parseColor("#3F3F46")); 
        previewFrame.setBackground(new LayerDrawable(new android.graphics.drawable.Drawable[]{tileBg, canvasBorder}));

        final ImageView previewImg = new ImageView(getContext()); 
        previewImg.setScaleType(ImageView.ScaleType.MATRIX);
        previewFrame.addView(previewImg, new FrameLayout.LayoutParams(-1, -1)); 
        rightArea.addView(previewFrame, new LinearLayout.LayoutParams(-1, 0, 1f));

        final Matrix imageMatrix = new Matrix(); final Matrix savedMatrix = new Matrix();
        final int[] touchMode = {0}; final PointF startPoint = new PointF(); final PointF midPoint = new PointF();
        final float[] oldDist = {1f}; final boolean[] isMatrixInitialized = {false};

        previewFrame.setOnTouchListener((v, event) -> {
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN: savedMatrix.set(imageMatrix); startPoint.set(event.getX(), event.getY()); touchMode[0] = 1; break;
                case MotionEvent.ACTION_POINTER_DOWN: oldDist[0] = (float)Math.sqrt(Math.pow(event.getX(0)-event.getX(1), 2) + Math.pow(event.getY(0)-event.getY(1), 2)); if (oldDist[0] > 10f) { savedMatrix.set(imageMatrix); midPoint.set((event.getX(0)+event.getX(1))/2, (event.getY(0)+event.getY(1))/2); touchMode[0] = 2; } break;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_POINTER_UP: touchMode[0] = 0; break;
                case MotionEvent.ACTION_MOVE:
                    if (touchMode[0] == 1) { imageMatrix.set(savedMatrix); imageMatrix.postTranslate(event.getX() - startPoint.x, event.getY() - startPoint.y); } 
                    else if (touchMode[0] == 2) { float newDist = (float)Math.sqrt(Math.pow(event.getX(0)-event.getX(1), 2) + Math.pow(event.getY(0)-event.getY(1), 2)); if (newDist > 10f) { imageMatrix.set(savedMatrix); float scale = newDist / oldDist[0]; imageMatrix.postScale(scale, scale, midPoint.x, midPoint.y); } } break;
            } previewImg.setImageMatrix(imageMatrix); return true;
        });

        LinearLayout previewControls = new LinearLayout(getContext()); previewControls.setOrientation(LinearLayout.HORIZONTAL); previewControls.setGravity(Gravity.CENTER); previewControls.setPadding(0, (int)(10*density), 0, 0);
        Button btnPrev = createButton("⏪", "#3F3F46"); Button btnNext = createButton("⏭️", "#3F3F46");
        TextView txtFrameInfo = new TextView(getContext()); txtFrameInfo.setText(L(" 帧预览 ")); applyGlobalFontSettings(txtFrameInfo, 0.9f, false); txtFrameInfo.setPadding((int)(10*density), 0, (int)(10*density), 0);
        previewControls.addView(btnPrev); previewControls.addView(txtFrameInfo); previewControls.addView(btnNext);
        rightArea.addView(previewControls); mainArea.addView(rightArea); root.addView(mainArea, new LinearLayout.LayoutParams(-1, -1));

        Runnable updateSffPreview = () -> {
            if (currentSffPath[0].isEmpty() || currentActPath[0].isEmpty()) return;
            new Thread(() -> {
                try {
                    if (loadedFrames.isEmpty()) {
                        List<GoEngineBridge.SffFrame> frames = GoEngineBridge.getAllFrames(currentSffPath[0]);
                        if (frames != null) loadedFrames.addAll(frames);
                    }
                    if (loadedFrames.isEmpty()) return;
                    if (previewIndex[0] < 0) previewIndex[0] = loadedFrames.size() - 1;
                    if (previewIndex[0] >= loadedFrames.size()) previewIndex[0] = 0;
                    
                    GoEngineBridge.SffFrame f = loadedFrames.get(previewIndex[0]);
                    byte[] bmpData = Api.decodeSffFrame(currentSffPath[0], f.group, f.item, currentActPath[0]);
                    if (bmpData != null && bmpData.length > 0) {
                        Bitmap bmp = BitmapFactory.decodeByteArray(bmpData, 0, bmpData.length);
                        new Handler(Looper.getMainLooper()).post(() -> { 
                            previewImg.setImageBitmap(bmp); 
                            txtFrameInfo.setText(String.format(" G:%d I:%d ", f.group, f.item)); 
                            
                            if (!isMatrixInitialized[0] && previewFrame.getWidth() > 0) {
                                float scale = Math.min((float)previewFrame.getWidth() / bmp.getWidth(), (float)previewFrame.getHeight() / bmp.getHeight());
                                if (scale > 2.5f) scale = 2.5f; 
                                float dx = (previewFrame.getWidth() - bmp.getWidth() * scale) / 2f; 
                                float dy = (previewFrame.getHeight() - bmp.getHeight() * scale) / 2f;
                                imageMatrix.setScale(scale, scale); imageMatrix.postTranslate(dx, dy); 
                                previewImg.setImageMatrix(imageMatrix); isMatrixInitialized[0] = true;
                            }
                        });
                    }
                } catch (Exception e) {}
            }).start();
        };

        btnPrev.setOnClickListener(v -> { previewIndex[0]--; updateSffPreview.run(); }); btnNext.setOnClickListener(v -> { previewIndex[0]++; updateSffPreview.run(); });

        Runnable loadActToGrid = () -> {
            try {
                File f = new File(currentActPath[0]); long size = f.length(); currentActData[0] = new byte[(int)size];
                FileInputStream fis = new FileInputStream(f); fis.read(currentActData[0]); fis.close();
                isRgbaFormat[0] = (size >= 1024);
                for (int i=0; i<256; i++) {
                    int r, g, b;
                    if (isRgbaFormat[0]) { r = currentActData[0][i*4] & 0xFF; g = currentActData[0][i*4+1] & 0xFF; b = currentActData[0][i*4+2] & 0xFF; } 
                    else { r = currentActData[0][i*3] & 0xFF; g = currentActData[0][i*3+1] & 0xFF; b = currentActData[0][i*3+2] & 0xFF; }
                    colorBoxes[i].setBackgroundColor(Color.rgb(r,g,b));
                }
                originalActPath[0] = currentActPath[0]; 
                isEditMode[0] = false;
                btnMode.setText(L("👁️ 预览模式")); btnMode.setBackgroundColor(Color.parseColor("#4CAF50"));
                updateSffPreview.run();
            } catch (Exception e) { Toast.makeText(getContext(), L("❌ 色表加载失败"), Toast.LENGTH_SHORT).show(); }
        };

        for (int i=0; i<256; i++) {
            final int idx = i;
            colorBoxes[i].setOnClickListener(v -> {
                if (!isEditMode[0]) { Toast.makeText(getContext(), L("👁️ 请先点击上方【预览模式】解锁修改"), Toast.LENGTH_SHORT).show(); return; }
                if (currentActData[0] == null) { Toast.makeText(getContext(), L("⚠️ 请先加载色表！"), Toast.LENGTH_SHORT).show(); return; }
                showCleanDraggableRgbDialog(idx, currentActData[0], isRgbaFormat[0], colorBoxes[idx], currentActPath[0], updateSffPreview);
            });
        }

        btnLoadAct.setOnClickListener(v -> showWin10FilePicker(L("选择 .act 色表或目录"), 9, null, null, file -> {
            if (file.isDirectory()) { showActGridPicker(file, selectedAct -> { currentActPath[0] = selectedAct.getAbsolutePath(); loadActToGrid.run(); }); } 
            else { currentActPath[0] = file.getAbsolutePath(); loadActToGrid.run(); }
        }));

        btnLoadSff.setOnClickListener(v -> showWin10FilePicker(L("选择 .sff 图像或目录"), 4, null, null, file -> {
            if (file.isDirectory()) { showSffGridPicker(file, selectedSff -> { currentSffPath[0] = selectedSff.getAbsolutePath(); loadedFrames.clear(); previewIndex[0] = 0; isMatrixInitialized[0] = false; updateSffPreview.run(); }); } 
            else { currentSffPath[0] = file.getAbsolutePath(); loadedFrames.clear(); previewIndex[0] = 0; isMatrixInitialized[0] = false; updateSffPreview.run(); }
        }));

        btnMode.setOnClickListener(v -> {
            if (currentActPath[0].isEmpty()) { Toast.makeText(getContext(), L("⚠️ 请先加载色表"), Toast.LENGTH_SHORT).show(); return; }
            if (!isEditMode[0]) {
                if (!currentActPath[0].equals(originalActPath[0])) {
                    isEditMode[0] = true;
                    btnMode.setText(L("📝 修改模式 (当前: ") + new File(currentActPath[0]).getName() + L(")"));
                    btnMode.setBackgroundColor(Color.parseColor("#E81123"));
                } else {
                    final Dialog prompt = new Dialog(getContext()); prompt.requestWindowFeature(Window.FEATURE_NO_TITLE); prompt.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                    LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setBackgroundColor(Color.parseColor("#252526")); box.setPadding((int)(20*density),(int)(20*density),(int)(20*density),(int)(20*density));
                    GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#252526")); border.setStroke((int)(2*density), Color.parseColor("#0078D7")); border.setCornerRadius(15*density); box.setBackground(border);
                    
                    TextView title = createSubTitle(L("🛡️ 安全修改提示")); title.setTextColor(Color.WHITE); box.addView(title);
                    TextView msg = new TextView(getContext()); msg.setText(L("即将进入修改模式。为了防止人物文件损坏，建议创建一个备份后再进行修改。")); msg.setTextColor(Color.LTGRAY); applyGlobalFontSettings(msg, 0.9f, false); msg.setPadding(0,(int)(10*density),0,(int)(20*density)); box.addView(msg);
                    
                    Button btnBackup = createButton(L("💾 自动创建防毁备份并修改"), "#4CAF50");
                    btnBackup.setOnClickListener(bv -> {
                        try {
                            File orig = new File(originalActPath[0]); File backup = new File(orig.getParent(), orig.getName().replace(".act", "_backup.act"));
                            if (!backup.exists()) copyFileToSandbox(orig, backup);
                            currentActPath[0] = backup.getAbsolutePath(); 
                            isEditMode[0] = true; btnMode.setText(L("📝 修改模式 (已备份)")); btnMode.setBackgroundColor(Color.parseColor("#E81123"));
                            Toast.makeText(getContext(), L("✅ 已切换至备份文件: ") + backup.getName(), Toast.LENGTH_LONG).show();
                        } catch(Exception e){} prompt.dismiss();
                    });
                    
                    Button btnOrig = createButton(L("⚠️ 无视风险，直接修改原文件"), "#FF9800");
                    btnOrig.setOnClickListener(bv -> { isEditMode[0] = true; btnMode.setText(L("📝 修改模式 (修改原文件)")); btnMode.setBackgroundColor(Color.parseColor("#E81123")); prompt.dismiss(); });
                    
                    Button btnCancel = createButton(L("❌ 取消"), "#333333");
                    btnCancel.setOnClickListener(bv -> prompt.dismiss());
                    
                    LinearLayout.LayoutParams promptBp = new LinearLayout.LayoutParams(-1, -2); promptBp.setMargins(0,0,0,(int)(10*density));
                    box.addView(btnBackup, promptBp); box.addView(btnOrig, promptBp); box.addView(btnCancel, promptBp);
                    prompt.setContentView(box); prompt.show();
                }
            } else {
                isEditMode[0] = false;
                btnMode.setText(L("👁️ 预览模式")); btnMode.setBackgroundColor(Color.parseColor("#4CAF50"));
            }
        });

        btnExtract.setOnClickListener(v -> {
            if (currentSffPath[0].isEmpty()) { Toast.makeText(getContext(), L("⚠️ 请先挂载 SFF 文件！"), Toast.LENGTH_SHORT).show(); return; }
            new Thread(() -> {
                File origSff = new File(currentSffPath[0]); File extractAct = new File(origSff.getParent(), origSff.getName().replace(".sff", "_internal.act"));
                boolean success = Api.extractSffPalette(currentSffPath[0], extractAct.getAbsolutePath());
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (success) { 
                        Toast.makeText(getContext(), L("✅ 提取成功: ") + extractAct.getName() + L("，请点击【挂载色表】加载它！"), Toast.LENGTH_LONG).show(); 
                    } 
                    else { 
                        Toast.makeText(getContext(), L("❌ 提取失败：该 SFF 无内置色表"), Toast.LENGTH_LONG).show(); 
                    }
                });
            }).start();
        });

        return root;
    }

    // ==========================================
    // 🧲 纯净版调色窗：无黑框、白底黑字代码框、永不遮挡底部按钮
    // ==========================================
    private void showCleanDraggableRgbDialog(int index, byte[] actData, boolean isRgba, View colorBox, String actPath, Runnable onRealtimeUpdate) {
        final Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE); 
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); 
        dialog.setCanceledOnTouchOutside(false); dialog.setCancelable(false); 

        WindowManager.LayoutParams wmlp = dialog.getWindow().getAttributes();
        wmlp.gravity = Gravity.TOP | Gravity.LEFT; wmlp.x = 50; wmlp.y = 50;
        dialog.getWindow().setAttributes(wmlp);

        LinearLayout root = new LinearLayout(getContext()); root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#252526")); bg.setCornerRadius(15*density); bg.setStroke((int)(1*density), Color.parseColor("#3F3F46"));
        root.setBackground(bg);

        TextView titleBar = new TextView(getContext()); titleBar.setText(L("✋ 调色板 (按住拖动)")); titleBar.setTextColor(Color.WHITE); titleBar.setPadding(20, 20, 20, 20); titleBar.setGravity(Gravity.CENTER);
        GradientDrawable titleBg = new GradientDrawable(); titleBg.setColor(Color.parseColor("#0078D7")); titleBg.setCornerRadii(new float[]{15*density, 15*density, 15*density, 15*density, 0, 0, 0, 0});
        titleBar.setBackground(titleBg); root.addView(titleBar);

        final float[] touchXY = new float[2]; final int[] startXY = new int[2];
        titleBar.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                touchXY[0] = event.getRawX(); touchXY[1] = event.getRawY();
                WindowManager.LayoutParams lp = dialog.getWindow().getAttributes(); startXY[0] = lp.x; startXY[1] = lp.y; return true;
            } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
                lp.x = startXY[0] + (int)(event.getRawX() - touchXY[0]); lp.y = startXY[1] + (int)(event.getRawY() - touchXY[1]);
                dialog.getWindow().setAttributes(lp); return true;
            } return false;
        });

        ScrollView scroll = new ScrollView(getContext()); 
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1f); 
        scroll.setLayoutParams(scrollParams);
        
        LinearLayout layout = new LinearLayout(getContext()); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding((int)(20*density), (int)(10*density), (int)(20*density), (int)(20*density));

        final int origR = isRgba ? (actData[index*4] & 0xFF) : (actData[index*3] & 0xFF);
        final int origG = isRgba ? (actData[index*4+1] & 0xFF) : (actData[index*3+1] & 0xFF);
        final int origB = isRgba ? (actData[index*4+2] & 0xFF) : (actData[index*3+2] & 0xFF);
        final int[] RGB = new int[]{origR, origG, origB};

        View colorPreview = new View(getContext()); LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, (int)(40*density)); cp.setMargins(0, 0, 0, (int)(10*density));
        colorPreview.setLayoutParams(cp); colorPreview.setBackgroundColor(Color.rgb(RGB[0], RGB[1], RGB[2])); layout.addView(colorPreview);

        final boolean[] isUIUpdating = {false}; EditText hexInput = new EditText(getContext());
        Runnable applyColorLive = () -> {
            if (isUIUpdating[0]) return;
            colorBox.setBackgroundColor(Color.rgb(RGB[0], RGB[1], RGB[2])); colorPreview.setBackgroundColor(Color.rgb(RGB[0], RGB[1], RGB[2]));
            isUIUpdating[0] = true; hexInput.setText(String.format("#%02X%02X%02X", RGB[0], RGB[1], RGB[2])); isUIUpdating[0] = false;
            if (isRgba) { actData[index*4]=(byte)RGB[0]; actData[index*4+1]=(byte)RGB[1]; actData[index*4+2]=(byte)RGB[2]; } 
            else { actData[index*3]=(byte)RGB[0]; actData[index*3+1]=(byte)RGB[1]; actData[index*3+2]=(byte)RGB[2]; }
            try { FileOutputStream fos = new FileOutputStream(actPath); fos.write(actData); fos.close(); if (onRealtimeUpdate != null) onRealtimeUpdate.run(); } catch (Exception e) {}
        };

        hexInput.setTextColor(Color.BLACK); hexInput.setBackgroundColor(Color.WHITE); hexInput.setPadding(20,20,20,20);
        hexInput.setText(String.format("#%02X%02X%02X", RGB[0], RGB[1], RGB[2]));
        hexInput.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                if (isUIUpdating[0]) return;
                String hex = s.toString().trim();
                if (!hex.startsWith("#")) hex = "#" + hex;
                if (hex.length() == 4) hex = "#" + hex.charAt(1)+hex.charAt(1) + hex.charAt(2)+hex.charAt(2) + hex.charAt(3)+hex.charAt(3);
                try { int c = Color.parseColor(hex); RGB[0]=Color.red(c); RGB[1]=Color.green(c); RGB[2]=Color.blue(c); applyColorLive.run(); } catch(Exception e){}
            }
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {} public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });
        layout.addView(createSubTitle(L("🎨 代码 (Hex)"))); layout.addView(hexInput);

        LinearLayout modeBar = new LinearLayout(getContext()); modeBar.setOrientation(LinearLayout.HORIZONTAL);
        Button btnModeRgb = createButton(L("RGB滑块"), "#333333"); Button btnModeWheel = createButton(L("纯净色盘"), "#333333"); Button btnModeDpad = createButton(L("十字微调"), "#333333");
        modeBar.addView(btnModeRgb, new LinearLayout.LayoutParams(0, -2, 1)); modeBar.addView(btnModeWheel, new LinearLayout.LayoutParams(0, -2, 1)); modeBar.addView(btnModeDpad, new LinearLayout.LayoutParams(0, -2, 1));
        layout.addView(modeBar);

        FrameLayout modeContainer = new FrameLayout(getContext()); layout.addView(modeContainer);

        LinearLayout viewRgb = new LinearLayout(getContext()); viewRgb.setOrientation(LinearLayout.VERTICAL);
        SeekBar barR = new SeekBar(getContext()); barR.setMax(255); barR.setProgress(RGB[0]);
        SeekBar barG = new SeekBar(getContext()); barG.setMax(255); barG.setProgress(RGB[1]);
        SeekBar barB = new SeekBar(getContext()); barB.setMax(255); barB.setProgress(RGB[2]);
        SeekBar.OnSeekBarChangeListener rgbL = new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean b) { if(b){ if(s==barR) RGB[0]=p; if(s==barG) RGB[1]=p; if(s==barB) RGB[2]=p; applyColorLive.run(); } }
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
        };
        barR.setOnSeekBarChangeListener(rgbL); barG.setOnSeekBarChangeListener(rgbL); barB.setOnSeekBarChangeListener(rgbL);
        viewRgb.addView(createSubTitle(L("🔴 红 (R)"))); viewRgb.addView(barR); viewRgb.addView(createSubTitle(L("🟢 绿 (G)"))); viewRgb.addView(barG); viewRgb.addView(createSubTitle(L("🔵 蓝 (B)"))); viewRgb.addView(barB);

        LinearLayout viewWheel = new LinearLayout(getContext()); viewWheel.setOrientation(LinearLayout.VERTICAL); viewWheel.setGravity(Gravity.CENTER);
        View colorWheel = new View(getContext()) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); android.graphics.Shader hueShader;
            @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w,h,oldw,oldh);
                int[] colors = {Color.RED, Color.MAGENTA, Color.BLUE, Color.CYAN, Color.GREEN, Color.YELLOW, Color.RED};
                hueShader = new android.graphics.SweepGradient(w/2f, h/2f, colors, null);
            }
            @Override protected void onDraw(Canvas c) {
                float cx = getWidth()/2f, cy = getHeight()/2f, radius = Math.min(cx, cy) - 5;
                p.setShader(hueShader); c.drawCircle(cx, cy, radius, p);
                p.setShader(new android.graphics.RadialGradient(cx, cy, radius, Color.WHITE, Color.TRANSPARENT, android.graphics.Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, radius, p);
            }
            @Override public boolean onTouchEvent(MotionEvent event) {
                float dx = event.getX() - getWidth()/2f, dy = event.getY() - getHeight()/2f;
                float angle = (float)(Math.atan2(dy, dx) * 180 / Math.PI + 360) % 360;
                float radius = (float)Math.hypot(dx, dy); float maxR = getWidth()/2f;
                float sat = Math.min(1f, radius / maxR);
                int c = Color.HSVToColor(new float[]{angle, sat, 1f}); 
                RGB[0]=Color.red(c); RGB[1]=Color.green(c); RGB[2]=Color.blue(c); applyColorLive.run(); return true;
            }
        };
        LinearLayout.LayoutParams wheelParams = new LinearLayout.LayoutParams((int)(200*density), (int)(200*density));
        wheelParams.setMargins(0, (int)(15*density), 0, (int)(15*density));
        viewWheel.addView(colorWheel, wheelParams);
        TextView hint = new TextView(getContext()); hint.setText(L("☝️ 滑动提取任意中性色")); hint.setTextColor(Color.GRAY); hint.setGravity(Gravity.CENTER); viewWheel.addView(hint);

        LinearLayout viewDpad = new LinearLayout(getContext()); viewDpad.setOrientation(LinearLayout.VERTICAL); viewDpad.setGravity(Gravity.CENTER);
        viewDpad.setPadding(0, (int)(15*density), 0, 0);
        View.OnClickListener dpadClick = v -> {
            float[] hsv = new float[3]; Color.colorToHSV(Color.rgb(RGB[0],RGB[1],RGB[2]), hsv);
            String tag = (String)v.getTag();
            if(tag.equals("H+")) hsv[0] = (hsv[0] + 5) % 360; else if(tag.equals("H-")) hsv[0] = (hsv[0] - 5 + 360) % 360;
            if(tag.equals("S+")) hsv[1] = Math.min(1f, hsv[1] + 0.05f); else if(tag.equals("S-")) hsv[1] = Math.max(0f, hsv[1] - 0.05f);
            if(tag.equals("V+")) hsv[2] = Math.min(1f, hsv[2] + 0.05f); else if(tag.equals("V-")) hsv[2] = Math.max(0f, hsv[2] - 0.05f);
            int c = Color.HSVToColor(hsv); RGB[0]=Color.red(c); RGB[1]=Color.green(c); RGB[2]=Color.blue(c); applyColorLive.run();
        };
        LinearLayout row1 = new LinearLayout(getContext()); row1.setGravity(Gravity.CENTER); 
        Button btnVplus = createButton(L("🔼 变亮"), "#555555"); btnVplus.setTag("V+"); btnVplus.setOnClickListener(dpadClick); row1.addView(btnVplus);
        LinearLayout row2 = new LinearLayout(getContext()); row2.setGravity(Gravity.CENTER);
        Button btnHminus = createButton(L("◀️ 色偏"), "#555555"); btnHminus.setTag("H-"); btnHminus.setOnClickListener(dpadClick);
        Button btnSplus = createButton(L("⏺️ 加浓"), "#E81123"); btnSplus.setTag("S+"); btnSplus.setOnClickListener(dpadClick);
        Button btnHplus = createButton(L("色偏 ▶️"), "#555555"); btnHplus.setTag("H+"); btnHplus.setOnClickListener(dpadClick);
        row2.addView(btnHminus); row2.addView(btnSplus); row2.addView(btnHplus);
        LinearLayout row3 = new LinearLayout(getContext()); row3.setGravity(Gravity.CENTER);
        Button btnVminus = createButton(L("🔽 变暗"), "#555555"); btnVminus.setTag("V-"); btnVminus.setOnClickListener(dpadClick); row3.addView(btnVminus);
        viewDpad.addView(row1); viewDpad.addView(row2); viewDpad.addView(row3);

        modeContainer.addView(viewRgb); modeContainer.addView(viewWheel); modeContainer.addView(viewDpad);
        viewRgb.setVisibility(View.VISIBLE); viewWheel.setVisibility(View.GONE); viewDpad.setVisibility(View.GONE);

        btnModeRgb.setOnClickListener(v -> { viewRgb.setVisibility(View.VISIBLE); viewWheel.setVisibility(View.GONE); viewDpad.setVisibility(View.GONE); });
        btnModeWheel.setOnClickListener(v -> { viewRgb.setVisibility(View.GONE); viewWheel.setVisibility(View.VISIBLE); viewDpad.setVisibility(View.GONE); });
        btnModeDpad.setOnClickListener(v -> { viewRgb.setVisibility(View.GONE); viewWheel.setVisibility(View.GONE); viewDpad.setVisibility(View.VISIBLE); });

        scroll.addView(layout); 
        root.addView(scroll);

        LinearLayout bottomRow = new LinearLayout(getContext()); bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setBackgroundColor(Color.parseColor("#1E1E1E"));
        bottomRow.setPadding((int)(10*density), (int)(10*density), (int)(10*density), (int)(10*density));

        Button btnCancel = createButton(L("❌ 取消恢复"), "#E81123");
        Button btnSave = createButton(L("💾 确认保存"), "#4CAF50");

        btnCancel.setOnClickListener(v -> {
            if (isRgba) { actData[index*4]=(byte)origR; actData[index*4+1]=(byte)origG; actData[index*4+2]=(byte)origB; } 
            else { actData[index*3]=(byte)origR; actData[index*3+1]=(byte)origG; actData[index*3+2]=(byte)origB; }
            colorBox.setBackgroundColor(Color.rgb(origR, origG, origB));
            try { FileOutputStream fos = new FileOutputStream(actPath); fos.write(actData); fos.close(); if (onRealtimeUpdate != null) onRealtimeUpdate.run(); } catch (Exception e) {}
            dialog.dismiss();
        });

        btnSave.setOnClickListener(v -> dialog.dismiss());

        bottomRow.addView(btnCancel, new LinearLayout.LayoutParams(0, -2, 1f));
        bottomRow.addView(btnSave, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(bottomRow, new LinearLayout.LayoutParams(-1, -2)); 

        dialog.setContentView(root);
        int screenHeight = getContext().getResources().getDisplayMetrics().heightPixels;
        dialog.getWindow().setLayout((int)(340*density), (int)(screenHeight * 0.85f));
        dialog.show();
    }

    // ==========================================
    // 🔎 弹窗型网格选择器：安全防挂掉独立解析机制
    // ==========================================
    // 补齐被误删的文件选择回调接口
    public interface FileCallback {
        void onFileSelected(java.io.File file);
    }

    private void showSffGridPicker(File dir, FileCallback listener) {
        Dialog d = new Dialog(getContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        d.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE); 
        FrameLayout overlay = new FrameLayout(getContext()); overlay.setBackgroundColor(Color.argb(230, 0,0,0));
        LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setPadding((int)(20*density), (int)(20*density), (int)(20*density), (int)(20*density));
        
        TextView title = new TextView(getContext()); title.setText(L("正在极速扫描 SFF 并渲染头像，请稍候...")); title.setTextColor(Color.WHITE); applyGlobalFontSettings(title, 1.2f, true); box.addView(title);
        ScrollView scroll = new ScrollView(getContext()); LinearLayout grid = new LinearLayout(getContext()); grid.setOrientation(LinearLayout.VERTICAL); scroll.addView(grid); box.addView(scroll);
        overlay.addView(box); d.setContentView(overlay); d.show(); d.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);

        class SffGridItem { GoEngineBridge.SffInfo info; Bitmap preview; }

        new Thread(() -> {
            List<File> files = new ArrayList<>();
            class Scanner {
                void scan(File targetDir) {
                    File[] fs = targetDir.listFiles(); if(fs==null) return;
                    for(File f:fs){ if(f.isDirectory() && !f.isHidden()) scan(f); else if(f.getName().toLowerCase().endsWith(".sff")) files.add(f); }
                }
            }
            new Scanner().scan(dir);

            List<SffGridItem> items = new ArrayList<>();
            for(File f : files) {
                List<GoEngineBridge.SffInfo> s = GoEngineBridge.scanSff(f.getAbsolutePath());
                for(GoEngineBridge.SffInfo i : s) { 
                    SffGridItem item = new SffGridItem(); item.info = i;
                    try { 
                        byte[] pb = Api.decodeSffFrame(i.filePath, 9000, 0, ""); 
                        if(pb==null) pb = Api.decodeSffFrame(i.filePath, 0, 0, ""); 
                        if(pb!=null&&pb.length>0) item.preview = BitmapFactory.decodeByteArray(pb,0,pb.length); 
                    } catch(Exception e){} 
                    items.add(item); 
                }
            }
            new Handler(Looper.getMainLooper()).post(() -> {
                title.setText(L("✅ 扫描完成，请点击选择用于挂载的图像:"));
                LinearLayout row = null; int count = 0;
                for(SffGridItem item : items) {
                    if(count == 0) { row = new LinearLayout(getContext()); row.setOrientation(LinearLayout.HORIZONTAL); grid.addView(row); }
                    LinearLayout card = new LinearLayout(getContext()); card.setOrientation(LinearLayout.VERTICAL); card.setGravity(Gravity.CENTER); card.setPadding((int)(10*density),(int)(10*density),(int)(10*density),(int)(10*density));
                    GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#2D2D30")); bg.setCornerRadius(10f); bg.setStroke(2, Color.parseColor("#3F3F46")); card.setBackground(bg);
                    ImageView iv = new ImageView(getContext()); iv.setLayoutParams(new LinearLayout.LayoutParams((int)(90*density), (int)(90*density))); iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    if(item.preview != null) iv.setImageBitmap(item.preview); else iv.setBackgroundColor(Color.DKGRAY); card.addView(iv);
                    TextView tv = new TextView(getContext()); tv.setText(item.info.name); tv.setTextColor(Color.WHITE); tv.setSingleLine(true); applyGlobalFontSettings(tv, 0.9f, false); card.addView(tv);
                    Button btn = createButton(L("✔️ 选择此项"), "#4CAF50"); btn.setOnClickListener(v -> { listener.onFileSelected(new File(item.info.filePath)); d.dismiss(); }); card.addView(btn);
                    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f); cp.setMargins((int)(5*density),(int)(5*density),(int)(5*density),(int)(5*density)); row.addView(card, cp);
                    count++; if(count >= 3) count = 0;
                }
                Button closeBtn = createButton(L("❌ 取消并关闭"), "#E81123"); closeBtn.setOnClickListener(v -> d.dismiss()); grid.addView(closeBtn);
            });
        }).start();
    }

    private void showActGridPicker(File dir, FileCallback listener) {
        Dialog d = new Dialog(getContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        d.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        FrameLayout overlay = new FrameLayout(getContext()); overlay.setBackgroundColor(Color.argb(230, 0,0,0));
        LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setPadding((int)(20*density), (int)(20*density), (int)(20*density), (int)(20*density));
        
        TextView title = new TextView(getContext()); title.setText(L("正在扫描 ACT 色表，请稍候...")); title.setTextColor(Color.WHITE); applyGlobalFontSettings(title, 1.2f, true); box.addView(title);
        ScrollView scroll = new ScrollView(getContext()); LinearLayout grid = new LinearLayout(getContext()); grid.setOrientation(LinearLayout.VERTICAL); scroll.addView(grid); box.addView(scroll);
        overlay.addView(box); d.setContentView(overlay); d.show(); d.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);

        new Thread(() -> {
            List<File> files = new ArrayList<>();
            class Scanner {
                void scan(File targetDir) {
                    File[] fs = targetDir.listFiles(); if(fs==null) return;
                    for(File f:fs){ if(f.isDirectory() && !f.isHidden()) scan(f); else if(f.getName().toLowerCase().endsWith(".act")) files.add(f); }
                }
            }
            new Scanner().scan(dir);
            new Handler(Looper.getMainLooper()).post(() -> {
                title.setText(L("✅ 扫描完成，共找到 ") + files.size() + L(" 个 ACT 色表文件:"));
                for(File f : files) {
                    Button btn = createButton(L("🎨 ") + f.getName() + "\n" + f.getParent(), "#0078D7");
                    btn.setOnClickListener(v -> { listener.onFileSelected(f); d.dismiss(); });
                    LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2); bp.setMargins(0,0,0,(int)(10*density));
                    grid.addView(btn, bp);
                }
                Button closeBtn = createButton(L("❌ 取消并关闭"), "#E81123"); closeBtn.setOnClickListener(v -> d.dismiss()); grid.addView(closeBtn);
            });
        }).start();
    }

    // ==========================================
    // 🔎 弹窗型外部图像网格选择器 (用于SFF替换)
    // ==========================================
    private void showImageGridPicker(File dir, FileCallback listener) {
        Dialog d = new Dialog(getContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        d.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        FrameLayout overlay = new FrameLayout(getContext()); overlay.setBackgroundColor(Color.argb(230, 0,0,0));
        LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setPadding((int)(20*density), (int)(20*density), (int)(20*density), (int)(20*density));
        
        TextView title = new TextView(getContext()); title.setText(L("正在生成图像缩略图，请稍候...")); title.setTextColor(Color.WHITE); applyGlobalFontSettings(title, 1.2f, true); box.addView(title);
        ScrollView scroll = new ScrollView(getContext()); LinearLayout grid = new LinearLayout(getContext()); grid.setOrientation(LinearLayout.VERTICAL); scroll.addView(grid); box.addView(scroll);
        overlay.addView(box); d.setContentView(overlay); d.show(); d.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);

        new Thread(() -> {
            List<File> files = new ArrayList<>();
            class Scanner {
                void scan(File targetDir) {
                    File[] fs = targetDir.listFiles(); if(fs==null) return;
                    for(File f:fs){ 
                        if(f.isDirectory() && !f.isHidden()) scan(f); 
                        else {
                            String n = f.getName().toLowerCase();
                            if(n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".gif") || n.endsWith(".pcx")) files.add(f); 
                        }
                    }
                }
            }
            new Scanner().scan(dir);

            new Handler(Looper.getMainLooper()).post(() -> {
                title.setText(L("✅ 扫描完成，请点击选择用于替换的图像:"));
                LinearLayout row = null; int count = 0;
                for(File f : files) {
                    if(count == 0) { row = new LinearLayout(getContext()); row.setOrientation(LinearLayout.HORIZONTAL); grid.addView(row); }
                    LinearLayout card = new LinearLayout(getContext()); card.setOrientation(LinearLayout.VERTICAL); card.setGravity(Gravity.CENTER); card.setPadding((int)(10*density),(int)(10*density),(int)(10*density),(int)(10*density));
                    GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#2D2D30")); bg.setCornerRadius(10f); bg.setStroke(2, Color.parseColor("#3F3F46")); card.setBackground(bg);
                    ImageView iv = new ImageView(getContext()); iv.setLayoutParams(new LinearLayout.LayoutParams((int)(90*density), (int)(90*density))); iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    
                    iv.setBackgroundColor(Color.parseColor("#1E1E1E"));
                    if (!f.getName().toLowerCase().endsWith(".pcx")) {
                        new Thread(() -> { // 异步极速解码缩略图，防卡顿
                            try { BitmapFactory.Options opt = new BitmapFactory.Options(); opt.inSampleSize = 4; Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath(), opt);
                                if (bmp != null) new Handler(Looper.getMainLooper()).post(() -> iv.setImageBitmap(bmp)); } catch (Exception e){}
                        }).start();
                    }
                    card.addView(iv);
                    TextView tv = new TextView(getContext()); tv.setText(f.getName()); tv.setTextColor(Color.WHITE); tv.setSingleLine(true); applyGlobalFontSettings(tv, 0.9f, false); card.addView(tv);
                    Button btn = createButton(L("✔️ 选择替换"), "#4CAF50"); btn.setOnClickListener(v -> { listener.onFileSelected(f); d.dismiss(); }); card.addView(btn);
                    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f); cp.setMargins((int)(5*density),(int)(5*density),(int)(5*density),(int)(5*density)); row.addView(card, cp);
                    count++; if(count >= 3) count = 0;
                }
                Button closeBtn = createButton(L("❌ 取消并关闭"), "#E81123"); closeBtn.setOnClickListener(v -> d.dismiss()); grid.addView(closeBtn);
            });
        }).start();
    }

    // ==========================================
    // 🔎 弹窗型万能文件列表选择器 (用于 DEF 和 3D 模型)
    // ==========================================
    private void showGenericFileListPicker(File dir, final String[] extensions, String winTitle, String iconHex, FileCallback listener) {
        Dialog d = new Dialog(getContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        d.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        FrameLayout overlay = new FrameLayout(getContext()); overlay.setBackgroundColor(Color.argb(230, 0,0,0));
        LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setPadding((int)(20*density), (int)(20*density), (int)(20*density), (int)(20*density));
        
        TextView title = new TextView(getContext()); title.setText(L("正在极速检索本地文件，请稍候...")); title.setTextColor(Color.WHITE); applyGlobalFontSettings(title, 1.2f, true); box.addView(title);
        ScrollView scroll = new ScrollView(getContext()); LinearLayout listLayout = new LinearLayout(getContext()); listLayout.setOrientation(LinearLayout.VERTICAL); scroll.addView(listLayout); box.addView(scroll);
        overlay.addView(box); d.setContentView(overlay); d.show(); d.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);

        new Thread(() -> {
            List<File> files = new ArrayList<>();
            class Scanner {
                void scan(File targetDir) {
                    File[] fs = targetDir.listFiles(); if(fs==null) return;
                    for(File f:fs){ 
                        if(f.isDirectory() && !f.isHidden()) scan(f); 
                        else {
                            String n = f.getName().toLowerCase();
                            for (String ext : extensions) { if (n.endsWith(ext)) { files.add(f); break; } }
                        }
                    }
                }
            }
            new Scanner().scan(dir);
            new Handler(Looper.getMainLooper()).post(() -> {
                title.setText(L("✅ 扫描完成，共找到 ") + files.size() + L(" 个 ") + winTitle + L(" :"));
                for(File f : files) {
                    Button btn = createButton(L("📄 ") + f.getName() + "\n" + f.getParent(), iconHex);
                    btn.setOnClickListener(v -> { listener.onFileSelected(f); d.dismiss(); });
                    LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2); bp.setMargins(0,0,0,(int)(10*density));
                    listLayout.addView(btn, bp);
                }
                Button closeBtn = createButton(L("❌ 取消并关闭"), "#E81123"); closeBtn.setOnClickListener(v -> d.dismiss()); listLayout.addView(closeBtn);
            });
        }).start();
    }

    // ==========================================
    // 🔎 弹窗型外部音频列表选择器 (带试听)
    // ==========================================
    private void showAudioListPicker(File dir, FileCallback listener) {
        Dialog d = new Dialog(getContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        d.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        FrameLayout overlay = new FrameLayout(getContext()); overlay.setBackgroundColor(Color.argb(230, 0,0,0));
        LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setPadding((int)(20*density), (int)(20*density), (int)(20*density), (int)(20*density));
        
        TextView title = new TextView(getContext()); title.setText(L("正在检索本地音频，请稍候...")); title.setTextColor(Color.WHITE); applyGlobalFontSettings(title, 1.2f, true); box.addView(title);
        ScrollView scroll = new ScrollView(getContext()); LinearLayout listLayout = new LinearLayout(getContext()); listLayout.setOrientation(LinearLayout.VERTICAL); scroll.addView(listLayout); box.addView(scroll);
        overlay.addView(box); d.setContentView(overlay); d.show(); d.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);

        new Thread(() -> {
            List<File> files = new ArrayList<>();
            class Scanner {
                void scan(File targetDir) {
                    File[] fs = targetDir.listFiles(); if(fs==null) return;
                    for(File f:fs){ 
                        if(f.isDirectory() && !f.isHidden()) scan(f); 
                        else {
                            String n = f.getName().toLowerCase();
                            // 根据引擎源码扫描支持的所有格式
                            if(n.endsWith(".wav") || n.endsWith(".ogg") || n.endsWith(".mp3") || n.endsWith(".flac") || n.endsWith(".xm") || n.endsWith(".mod")) files.add(f); 
                        }
                    }
                }
            }
            new Scanner().scan(dir);

            new Handler(Looper.getMainLooper()).post(() -> {
                title.setText(L("✅ 扫描完成，共找到 ") + files.size() + L(" 个音频文件:"));
                for(File f : files) {
                    LinearLayout row = new LinearLayout(getContext()); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding((int)(10*density), (int)(10*density), (int)(10*density), (int)(10*density));
                    GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#2D2D30")); bg.setCornerRadius(8f*density); row.setBackground(bg);
                    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2); rowParams.setMargins(0, 0, 0, (int)(8*density));
                    
                    TextView info = new TextView(getContext()); info.setText(f.getName() + "\n" + f.getParent()); applyGlobalFontSettings(info, 0.9f, false); info.setTextColor(Color.WHITE); row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));

                    Button btnPlay = createButton(L("▶️ 试听"), "#FF9800"); 
                    btnPlay.setOnClickListener(v -> {
                        try {
                            if (currentSndPlayer != null) { currentSndPlayer.release(); currentSndPlayer = null; }
                            currentSndPlayer = new MediaPlayer(); currentSndPlayer.setDataSource(f.getAbsolutePath()); currentSndPlayer.prepare(); currentSndPlayer.start();
                        } catch (Exception e) { Toast.makeText(getContext(), L("播放器不支持试听此编码 (但仍可强行注入)"), Toast.LENGTH_SHORT).show(); }
                    });
                    
                    Button btnSelect = createButton(L("✔️ 选择"), "#4CAF50"); LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(-2, -2); btnParams.setMargins((int)(10*density), 0, 0, 0);
                    btnSelect.setOnClickListener(v -> { 
                        if (currentSndPlayer != null) { currentSndPlayer.release(); currentSndPlayer = null; }
                        listener.onFileSelected(f); d.dismiss(); 
                    });

                    row.addView(btnPlay); row.addView(btnSelect, btnParams); listLayout.addView(row, rowParams);
                }
                Button closeBtn = createButton(L("❌ 取消并关闭"), "#E81123"); 
                closeBtn.setOnClickListener(v -> { if (currentSndPlayer != null) { currentSndPlayer.release(); currentSndPlayer = null; } d.dismiss(); }); 
                listLayout.addView(closeBtn);
            });
        }).start();
    }
    
    // ======================================================================================
    // 🗺️ 模块 5：全能地图编辑器 (防OOM极限引擎, 原理级分辨率分离, 物理导出)
    // ======================================================================================
    private static class StageLayerInfo {
        String name; boolean isVisible = false; boolean manuallyVisible = false; boolean isLocked = false; boolean isGhostGrid = false;
        int group = 0; int item = 0; float startX = 0; float startY = 0; 
        float scaleX = 1.0f; float scaleY = 1.0f; float deltaX = 1.0f; float deltaY = 1.0f; String trans = "none";
        int axisX = 0; int axisY = 0;
        
        int origW = 0; int origH = 0; // 👈 核心参数：永不丢失的真实物理分辨率
        
        int originalGroup = 0; int originalItem = 0;
        String sourcePath = ""; boolean isExternal = false;
        Bitmap cacheBmp = null;
        
        public StageLayerInfo cloneLayer() {
            StageLayerInfo copy = new StageLayerInfo();
            copy.name = this.name + L(" (副本)"); copy.isVisible = this.isVisible; copy.manuallyVisible = this.manuallyVisible; copy.isLocked = this.isLocked;
            copy.group = this.group; copy.item = this.item; copy.startX = this.startX; copy.startY = this.startY;
            copy.scaleX = this.scaleX; copy.scaleY = this.scaleY; copy.deltaX = this.deltaX; copy.deltaY = this.deltaY; copy.trans = this.trans;
            copy.axisX = this.axisX; copy.axisY = this.axisY; copy.origW = this.origW; copy.origH = this.origH;
            copy.originalGroup = this.originalGroup; copy.originalItem = this.originalItem;
            copy.sourcePath = this.sourcePath; copy.isExternal = this.isExternal; copy.cacheBmp = this.cacheBmp; return copy;
        }

        // 🔥 防 OOM 极限引擎：根据你的屏幕自动压缩预览图，但保留真实大小参数给打包用！
        public static Bitmap safeDecode(String path, byte[] data, int[] outSize) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            if (data != null) BitmapFactory.decodeByteArray(data, 0, data.length, opts);
            else BitmapFactory.decodeFile(path, opts);
            outSize[0] = opts.outWidth; outSize[1] = opts.outHeight;
            
            int inSampleSize = 1;
            if (opts.outHeight > 1024 || opts.outWidth > 1024) {
                final int halfH = opts.outHeight / 2; final int halfW = opts.outWidth / 2;
                while ((halfH / inSampleSize) >= 1024 || (halfW / inSampleSize) >= 1024) inSampleSize *= 2;
            }
            opts.inJustDecodeBounds = false; opts.inSampleSize = inSampleSize;
            try {
                if (data != null) return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
                return BitmapFactory.decodeFile(path, opts);
            } catch (OutOfMemoryError e) {
                opts.inSampleSize *= 2; // 如果系统极烂，再缩小一倍自保
                try {
                    if (data != null) return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
                    return BitmapFactory.decodeFile(path, opts);
                } catch(Exception e2) { return null; }
            }
        }
    }

    private interface AutoIncrementer { int[] getNext(int group, int item); }

    private View buildStageEditorContent() {
        LinearLayout root = new LinearLayout(getContext()); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#1E1E1E"));

        globalDefPath = ""; globalSffPath = ""; globalIsEditMode = false;
        final boolean[] is3DMode = {false}; final int[] currentViewMode = {0}; 
        final int[] gridAlpha = {70}; final int[] gridColor = {Color.WHITE}; final int[] bgColor = {Color.parseColor("#000080")};
        final float[] dpadStep = {1.0f};

        final List<StageLayerInfo> layerList = new ArrayList<>();
        final int[] selectedLayerIndex = {0}; 
        final StageLayerInfo[] clipboardLayer = {null};
        
        final Runnable[] refreshLayerListUI = {null}; 
        final Runnable[] updateViewState = {null};
        
        StageLayerInfo ghostGrid = new StageLayerInfo(); ghostGrid.name = L("[系统] 蓝色参考网格"); ghostGrid.isGhostGrid = true; ghostGrid.isLocked = true; ghostGrid.isVisible = true; ghostGrid.manuallyVisible = true;
        layerList.add(ghostGrid);

        AutoIncrementer incrementer = (g, i) -> {
            int curI = i;
            while(true) {
                boolean match = false;
                for(StageLayerInfo l : layerList) { if(!l.isGhostGrid && l.group == g && l.item == curI) { match = true; break; } }
                if(!match) return new int[]{g, curI}; curI++;
            }
        };

        int padS = (int)(4 * density); int padM = (int)(8 * density);

        LinearLayout topBar = new LinearLayout(getContext()); topBar.setOrientation(LinearLayout.HORIZONTAL); topBar.setGravity(Gravity.CENTER_VERTICAL); topBar.setPadding(padS, padS, padS, padS); topBar.setBackgroundColor(Color.parseColor("#252526"));
        Button btnScan = createButton(L("📂 工程"), "#0078D7"); Button btnSave = createButton(L("💾 打包"), "#4CAF50"); 
        Button btnMode2D = createButton(L("📐 2D 地图模式"), "#9C27B0"); Button btnMode3D = createButton(L("🧊 3D 全屏查看器"), "#333333");
        LinearLayout.LayoutParams topBp = new LinearLayout.LayoutParams(0, -2, 1f); topBp.setMargins(0, 0, padS, 0);
        topBar.addView(btnScan, topBp); topBar.addView(btnSave, topBp); topBar.addView(btnMode2D, topBp); topBar.addView(btnMode3D, topBp);
        root.addView(topBar);

        LinearLayout viewSwitchBar = new LinearLayout(getContext()); viewSwitchBar.setOrientation(LinearLayout.HORIZONTAL); viewSwitchBar.setGravity(Gravity.CENTER); viewSwitchBar.setPadding(0, padS, 0, padS); viewSwitchBar.setBackgroundColor(Color.parseColor("#1E1E1E"));
        Button btnViewSff = createButton(L("🖼️ 2D SFF 贴图视口"), "#4CAF50"); Button btnViewDef = createButton(L("📝 DEF 代码编辑器"), "#333333");
        viewSwitchBar.addView(btnViewSff, topBp); viewSwitchBar.addView(btnViewDef, topBp);
        root.addView(viewSwitchBar);

        LinearLayout mainArea = new LinearLayout(getContext()); mainArea.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout leftPanel = new LinearLayout(getContext()); leftPanel.setOrientation(LinearLayout.VERTICAL); leftPanel.setBackgroundColor(Color.parseColor("#2D2D30")); leftPanel.setPadding(padS, padS, padS, padS);
        
        final boolean[] isLayerMode = {false};

        LinearLayout titleRow = new LinearLayout(getContext()); titleRow.setOrientation(LinearLayout.HORIZONTAL); titleRow.setGravity(Gravity.CENTER_VERTICAL);
        Button btnModeAction = createButton(L("🎬 动作组"), "#0078D7"); btnModeAction.setPadding((int)(8*density), (int)(5*density), (int)(8*density), (int)(5*density));
        Button btnModeLayer = createButton(L("📑 图层组"), "#333333"); btnModeLayer.setPadding((int)(8*density), (int)(5*density), (int)(8*density), (int)(5*density));
        Button btnSettings = createButton(L("⚙️ 设置"), "#3F3F46"); btnSettings.setPadding((int)(8*density), (int)(5*density), (int)(8*density), (int)(5*density));
        
        btnModeAction.setOnClickListener(v -> { 
            isLayerMode[0] = false; 
            btnModeAction.setBackgroundColor(Color.parseColor("#0078D7")); 
            btnModeLayer.setBackgroundColor(Color.parseColor("#333333")); 
            if(refreshLayerListUI[0] != null) refreshLayerListUI[0].run(); 
            Toast.makeText(getContext(), L("✅ 动作编组模式 (显示全景, 整体移动, 独立编号)"), Toast.LENGTH_SHORT).show(); 
        });
        btnModeLayer.setOnClickListener(v -> { 
            isLayerMode[0] = true; 
            btnModeAction.setBackgroundColor(Color.parseColor("#333333")); 
            btnModeLayer.setBackgroundColor(Color.parseColor("#0078D7")); 
            if(refreshLayerListUI[0] != null) refreshLayerListUI[0].run(); 
            Toast.makeText(getContext(), L("✅ 图层分组模式 (仅显当前编号, 独立移动, 同编号叠加)"), Toast.LENGTH_SHORT).show(); 
        });

        titleRow.addView(btnModeAction, new LinearLayout.LayoutParams(0, -2, 1f)); titleRow.addView(btnModeLayer, new LinearLayout.LayoutParams(0, -2, 1f)); titleRow.addView(btnSettings, new LinearLayout.LayoutParams(-2, -2));
        leftPanel.addView(titleRow);

        LinearLayout psToolsRow = new LinearLayout(getContext()); psToolsRow.setOrientation(LinearLayout.HORIZONTAL); psToolsRow.setPadding(0, padS, 0, padS);
        Button btnNewLayer = createButton("➕", "#4CAF50"); Button btnCopyLayer = createButton("📄", "#0078D7"); Button btnPasteLayer = createButton("📋", "#FF9800"); Button btnDelLayer = createButton("🗑️", "#E81123"); Button btnBatchDel = createButton("☑️", "#E81123");
        LinearLayout.LayoutParams toolBp = new LinearLayout.LayoutParams(0, -2, 1f); toolBp.setMargins((int)(1*density), 0, (int)(1*density), 0);
        psToolsRow.addView(btnNewLayer, toolBp); psToolsRow.addView(btnCopyLayer, toolBp); psToolsRow.addView(btnPasteLayer, toolBp); psToolsRow.addView(btnDelLayer, toolBp); psToolsRow.addView(btnBatchDel, toolBp);
        leftPanel.addView(psToolsRow);
        ScrollView layerScroll = new ScrollView(getContext()); final LinearLayout layerListLayout = new LinearLayout(getContext()); layerListLayout.setOrientation(LinearLayout.VERTICAL); layerScroll.addView(layerListLayout);
        leftPanel.addView(layerScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button btnImportMenu = createButton(L("📥 导入素材"), "#FF9800"); leftPanel.addView(btnImportMenu, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout centerContainer = new FrameLayout(getContext());
        LinearLayout sffCenterPanel = new LinearLayout(getContext()); sffCenterPanel.setOrientation(LinearLayout.VERTICAL);
        final Matrix imageMatrix = new Matrix(); 
        
        final FrameLayout viewportFrame = new FrameLayout(getContext()) {
            Paint gridPaint = new Paint(); Paint axisPaint = new Paint(); { axisPaint.setColor(Color.parseColor("#FF0000")); axisPaint.setStrokeWidth(2 * density); setWillNotDraw(false); }
            @Override protected void dispatchDraw(Canvas canvas) {
                if (layerList.get(0).isVisible) {
                    canvas.drawColor(bgColor[0]); gridPaint.setColor(gridColor[0]); gridPaint.setAlpha(gridAlpha[0]); gridPaint.setStrokeWidth(1);
                    float[] values = new float[9]; imageMatrix.getValues(values);
                    float transX = values[Matrix.MTRANS_X]; float transY = values[Matrix.MTRANS_Y]; float scale = values[Matrix.MSCALE_X];
                    float gridSize = 20 * scale; 
                    if (gridSize > 4 && gridAlpha[0] > 0) { 
                        float startX = transX % gridSize; if (startX < 0) startX += gridSize;
                        for (float x = startX; x < getWidth(); x += gridSize) canvas.drawLine(x, 0, x, getHeight(), gridPaint);
                        float startY = transY % gridSize; if (startY < 0) startY += gridSize;
                        for (float y = startY; y < getHeight(); y += gridSize) canvas.drawLine(0, y, getWidth(), y, gridPaint);
                    }
                    canvas.drawLine(transX, 0, transX, getHeight(), axisPaint); canvas.drawLine(0, transY, getWidth(), transY, axisPaint); 
                } else { canvas.drawColor(Color.parseColor("#0A0A0A")); }
                
                for (int i = 1; i < layerList.size(); i++) {
                    StageLayerInfo layer = layerList.get(i);
                    if (layer.isVisible && layer.cacheBmp != null) {
                        canvas.save(); canvas.concat(imageMatrix); canvas.translate(layer.startX - layer.axisX, layer.startY - layer.axisY); 
                        float uiScaleX = layer.origW > 0 ? (float)layer.origW / layer.cacheBmp.getWidth() : 1.0f;
                        float uiScaleY = layer.origH > 0 ? (float)layer.origH / layer.cacheBmp.getHeight() : 1.0f;
                        canvas.scale(layer.scaleX * uiScaleX, layer.scaleY * uiScaleY); canvas.drawBitmap(layer.cacheBmp, 0, 0, null); canvas.restore();
                    }
                }
                super.dispatchDraw(canvas); 
            }
        };
        sffCenterPanel.addView(viewportFrame, new LinearLayout.LayoutParams(-1, 0, 1f));

        // 🌟 修复: 只有在这里绑定事件，btnImportMenu 和 viewportFrame 才算真正存活
        btnNewLayer.setOnClickListener(v -> btnImportMenu.performClick());

        btnCopyLayer.setOnClickListener(v -> {
            if (selectedLayerIndex[0] > 0 && selectedLayerIndex[0] < layerList.size()) {
                clipboardLayer[0] = layerList.get(selectedLayerIndex[0]).cloneLayer();
                Toast.makeText(getContext(), L("✅ 已复制图层: ") + clipboardLayer[0].name, Toast.LENGTH_SHORT).show();
            } else { Toast.makeText(getContext(), L("❌ 底板不可复制"), Toast.LENGTH_SHORT).show(); }
        });

        btnPasteLayer.setOnClickListener(v -> {
            if (clipboardLayer[0] != null) {
                StageLayerInfo newLayer = clipboardLayer[0].cloneLayer();
                int[] gi = incrementer.getNext(newLayer.group, newLayer.item);
                newLayer.group = gi[0]; newLayer.item = gi[1];
                layerList.add(newLayer);
                refreshLayerListUI[0].run();
                Toast.makeText(getContext(), L("✅ 粘贴成功"), Toast.LENGTH_SHORT).show();
            } else { Toast.makeText(getContext(), L("❌ 剪贴板为空"), Toast.LENGTH_SHORT).show(); }
        });

        btnDelLayer.setOnClickListener(v -> {
            if (selectedLayerIndex[0] > 0 && selectedLayerIndex[0] < layerList.size()) {
                layerList.remove(selectedLayerIndex[0]);
                selectedLayerIndex[0] = 0;
                refreshLayerListUI[0].run(); viewportFrame.invalidate();
                Toast.makeText(getContext(), L("✅ 选中图层已删除"), Toast.LENGTH_SHORT).show();
            } else { Toast.makeText(getContext(), L("❌ 请先选中一个要删除的图层"), Toast.LENGTH_SHORT).show(); }
        });

        btnBatchDel.setOnClickListener(v -> {
            if (layerList.size() <= 1) { Toast.makeText(getContext(), L("当前没有可删除的图层"), Toast.LENGTH_SHORT).show(); return; }
            final Dialog d = new Dialog(getContext()); d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setBackgroundColor(Color.parseColor("#252526")); box.setPadding(padM, padM, padM, padM);
            GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#252526")); border.setStroke((int)(2*density), Color.parseColor("#0078D7")); border.setCornerRadius(15*density); box.setBackground(border);
            box.addView(createSubTitle(L("☑️ 勾选要批量删除的图层")));
            
            ScrollView sv = new ScrollView(getContext()); LinearLayout list = new LinearLayout(getContext()); list.setOrientation(LinearLayout.VERTICAL);
            List<android.widget.CheckBox> checks = new ArrayList<>();
            for (int i = 1; i < layerList.size(); i++) {
                android.widget.CheckBox cb = new android.widget.CheckBox(getContext());
                cb.setText(String.format(" G:%d I:%d - %s", layerList.get(i).group, layerList.get(i).item, layerList.get(i).name));
                cb.setTextColor(Color.WHITE); cb.setTag(i); checks.add(cb); list.addView(cb);
            }
            sv.addView(list); box.addView(sv, new LinearLayout.LayoutParams(-1, (int)(250*density)));

            Button bConf = createButton(L("🗑️ 确认粉碎选中图层"), "#E81123");
            bConf.setOnClickListener(v2 -> {
                List<StageLayerInfo> toRemove = new ArrayList<>();
                for (android.widget.CheckBox cb : checks) { if (cb.isChecked()) toRemove.add(layerList.get((int)cb.getTag())); }
                layerList.removeAll(toRemove); selectedLayerIndex[0] = 0;
                refreshLayerListUI[0].run(); viewportFrame.invalidate(); d.dismiss();
            });
            Button bCan = createButton(L("❌ 取消"), "#333333"); bCan.setOnClickListener(v2 -> d.dismiss());
            
            LinearLayout btnRow = new LinearLayout(getContext()); btnRow.setOrientation(LinearLayout.HORIZONTAL);
            btnRow.addView(bConf, new LinearLayout.LayoutParams(0, -2, 1f)); btnRow.addView(bCan, new LinearLayout.LayoutParams(0, -2, 1f));
            box.addView(btnRow, new LinearLayout.LayoutParams(-1, -2)); d.setContentView(box); d.show();
        });

        final Matrix savedMatrix = new Matrix(); final int[] touchMode = {0}; final PointF startPoint = new PointF(); final PointF midPoint = new PointF(); final float[] oldDist = {1f}; 
        viewportFrame.setOnTouchListener((vFrameTouch, event) -> {
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN: savedMatrix.set(imageMatrix); startPoint.set(event.getX(), event.getY()); touchMode[0] = 1; break;
                case MotionEvent.ACTION_POINTER_DOWN: oldDist[0] = (float)Math.sqrt(Math.pow(event.getX(0)-event.getX(1), 2) + Math.pow(event.getY(0)-event.getY(1), 2)); if (oldDist[0] > 10f) { savedMatrix.set(imageMatrix); midPoint.set((event.getX(0)+event.getX(1))/2, (event.getY(0)+event.getY(1))/2); touchMode[0] = 2; } break;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_POINTER_UP: touchMode[0] = 0; break;
                case MotionEvent.ACTION_MOVE:
                    if (touchMode[0] == 1) { imageMatrix.set(savedMatrix); imageMatrix.postTranslate(event.getX() - startPoint.x, event.getY() - startPoint.y); } 
                    else if (touchMode[0] == 2) { float newDist = (float)Math.sqrt(Math.pow(event.getX(0)-event.getX(1), 2) + Math.pow(event.getY(0)-event.getY(1), 2)); if (newDist > 10f) { imageMatrix.set(savedMatrix); float scale = newDist / oldDist[0]; imageMatrix.postScale(scale, scale, midPoint.x, midPoint.y); } } break;
            } viewportFrame.invalidate(); return true;
        });
        viewportFrame.post(() -> { imageMatrix.postTranslate(viewportFrame.getWidth() / 2f, viewportFrame.getHeight() * 0.8f); viewportFrame.invalidate(); });

        LinearLayout dpadAreaContainer = new LinearLayout(getContext()); dpadAreaContainer.setOrientation(LinearLayout.VERTICAL); dpadAreaContainer.setBackgroundColor(Color.parseColor("#1E1E1E")); dpadAreaContainer.setPadding(padS, padS, padS, padS);
        LinearLayout dpadRow1 = new LinearLayout(getContext()); dpadRow1.setOrientation(LinearLayout.HORIZONTAL); dpadRow1.setGravity(Gravity.CENTER); dpadRow1.setPadding(0, 0, 0, padS);
        Button btnModeToggle = createButton(L("🎬 动作"), "#9C27B0"); Button btnEditGI = createButton(L("⚙️ 属性"), "#3F3F46"); 
        TextView txtGI = new TextView(getContext()); txtGI.setText("  [0,0]  "); txtGI.setTextColor(Color.parseColor("#0078D7")); applyGlobalFontSettings(txtGI, 1.1f, true);
        TextView txtRes = new TextView(getContext()); txtRes.setText(L(" 📐 0x0 ")); txtRes.setTextColor(Color.parseColor("#4CAF50")); applyGlobalFontSettings(txtRes, 1.0f, false);
        Button btnStep = createButton(L("👣 步长: 1"), "#FF9800");

        btnModeToggle.setOnClickListener(clickMode -> {
            isLayerMode[0] = !isLayerMode[0]; 
            btnModeToggle.setText(isLayerMode[0] ? L("📑 图层") : L("🎬 动作")); 
            btnModeToggle.setBackgroundColor(Color.parseColor(isLayerMode[0] ? "#0078D7" : "#9C27B0")); 
            Toast.makeText(getContext(), isLayerMode[0] ? L("✅ 已切换至【图层模式】(方向键仅移动单层)") : L("✅ 已切换至【动作编组模式】(方向键移动同组所有帧)"), Toast.LENGTH_SHORT).show();
            if(selectedLayerIndex[0] > 0) { 
                StageLayerInfo info = layerList.get(selectedLayerIndex[0]); 
                txtGI.setText(String.format(isLayerMode[0] ? L(" 图层%d|G%d ") : " [%d,%d] ", info.item, info.group, info.group, info.item)); 
            }
        });

        dpadRow1.addView(btnModeToggle); dpadRow1.addView(btnEditGI); dpadRow1.addView(txtGI); dpadRow1.addView(txtRes); dpadRow1.addView(btnStep);
        
        LinearLayout dpadRow2 = new LinearLayout(getContext()); dpadRow2.setOrientation(LinearLayout.HORIZONTAL); dpadRow2.setGravity(Gravity.CENTER);
        Button btnLeft = createButton("◀", "#333333"); Button btnUp = createButton("▲", "#333333"); Button btnDown = createButton("▼", "#333333"); Button btnRight = createButton("▶", "#333333");
        TextView txtCoord = new TextView(getContext()); txtCoord.setText("  X:0  Y:0  "); txtCoord.setTextColor(Color.WHITE); applyGlobalFontSettings(txtCoord, 1.0f, true);
        dpadRow2.addView(btnLeft); dpadRow2.addView(btnUp); dpadRow2.addView(btnDown); dpadRow2.addView(btnRight); dpadRow2.addView(txtCoord);
        HorizontalScrollView dpadScroll1 = new HorizontalScrollView(getContext()); dpadScroll1.setHorizontalScrollBarEnabled(false); dpadScroll1.addView(dpadRow1);
        HorizontalScrollView dpadScroll2 = new HorizontalScrollView(getContext()); dpadScroll2.setHorizontalScrollBarEnabled(false); dpadScroll2.addView(dpadRow2);
        dpadAreaContainer.addView(dpadScroll1); dpadAreaContainer.addView(dpadScroll2); sffCenterPanel.addView(dpadAreaContainer, new LinearLayout.LayoutParams(-1, -2));

        btnStep.setOnClickListener(clickStep -> {
            final Dialog stepDialog = new Dialog(getContext()); stepDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            FrameLayout fl = new FrameLayout(getContext()); ScrollView sv = new ScrollView(getContext()); LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setBackgroundColor(Color.parseColor("#252526")); box.setPadding(padM, padM, padM, padM);
            GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#252526")); border.setStroke((int)(2*density), Color.parseColor("#0078D7")); border.setCornerRadius(15*density); box.setBackground(border);
            box.addView(createSubTitle(L("👣 设置方向键移动步长"))); 
            EditText stepInput = createInput(L("如: 1 或 10"), String.valueOf((int)dpadStep[0])); 
            stepInput.setInputType(InputType.TYPE_CLASS_NUMBER); box.addView(stepInput);
            LinearLayout btnRow = new LinearLayout(getContext()); btnRow.setOrientation(LinearLayout.HORIZONTAL);
            Button bSave = createButton(L("✔️ 保存"), "#4CAF50"); 
            bSave.setOnClickListener(clickSaveStep -> { 
                try { 
                    dpadStep[0] = Float.parseFloat(stepInput.getText().toString()); 
                    btnStep.setText(L("👣 步长: ") + (int)dpadStep[0]); 
                } catch(Exception e){} stepDialog.dismiss(); 
            });
            Button bReset = createButton(L("🔄 恢复默认"), "#0078D7"); 
            bReset.setOnClickListener(clickResetStep -> { 
                dpadStep[0] = 1.0f; 
                btnStep.setText(L("👣 步长: 1")); 
                stepDialog.dismiss(); 
            });
            Button bCancel = createButton(L("❌ 取消"), "#333333"); bCancel.setOnClickListener(clickCancelStep -> stepDialog.dismiss());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f); lp.setMargins((int)(2*density), padM, (int)(2*density), 0); btnRow.addView(bSave, lp); btnRow.addView(bReset, lp); btnRow.addView(bCancel, lp); box.addView(btnRow); sv.addView(box); fl.addView(sv, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER)); stepDialog.setContentView(fl); stepDialog.show();
        });

        View.OnClickListener dpadListener = clickPad -> {
            if (selectedLayerIndex[0] == 0) { 
                Toast.makeText(getContext(), L("不能移动网格底板"), Toast.LENGTH_SHORT).show(); return; 
            }
            StageLayerInfo curLayer = layerList.get(selectedLayerIndex[0]); float step = dpadStep[0]; float dx = 0, dy = 0;
            if (clickPad == btnLeft) dx = -step; else if (clickPad == btnRight) dx = step; else if (clickPad == btnUp) dy = -step; else if (clickPad == btnDown) dy = step;
            if (isLayerMode[0]) { curLayer.startX += dx; curLayer.startY += dy; } else {
                int targetGroup = curLayer.group; int targetItem = curLayer.item;
                for (StageLayerInfo l : layerList) { if (!l.isGhostGrid && l.group == targetGroup && l.item == targetItem) { l.startX += dx; l.startY += dy; } }
            }
            txtCoord.setText(String.format(" X:%.0f Y:%.0f ", curLayer.startX, curLayer.startY)); viewportFrame.invalidate(); 
        };
        btnLeft.setOnClickListener(dpadListener); btnRight.setOnClickListener(dpadListener); btnUp.setOnClickListener(dpadListener); btnDown.setOnClickListener(dpadListener);

        ScrollView defEditorPanel = new ScrollView(getContext()); defEditorPanel.setBackgroundColor(Color.parseColor("#1E1E1E"));
        EditText defCodeInput = new EditText(getContext()); defCodeInput.setBackgroundColor(Color.TRANSPARENT); defCodeInput.setTextColor(Color.parseColor("#D4D4D4")); defCodeInput.setGravity(Gravity.TOP | Gravity.LEFT); defCodeInput.setTypeface(Typeface.MONOSPACE); applyGlobalFontSettings(defCodeInput, 1.0f, false);
        defEditorPanel.addView(defCodeInput, new FrameLayout.LayoutParams(-1, -2)); defEditorPanel.setVisibility(View.GONE); 

        centerContainer.addView(sffCenterPanel, new FrameLayout.LayoutParams(-1, -1));
        centerContainer.addView(defEditorPanel, new FrameLayout.LayoutParams(-1, -1));

        ScrollView rightScroll = new ScrollView(getContext()); rightScroll.setBackgroundColor(Color.parseColor("#252526"));
        LinearLayout rightPanel = new LinearLayout(getContext()); rightPanel.setOrientation(LinearLayout.VERTICAL); rightPanel.setPadding(padS, padS, padS, padS);
        
        LinearLayout panel2D = new LinearLayout(getContext()); panel2D.setOrientation(LinearLayout.VERTICAL);
        panel2D.addView(createSubTitle(L("📐 2D 图层属性"))); 
        panel2D.addView(createSubTitle(L("Scale X,Y (缩放):"))); 
        EditText scale2D = createInput(L("1.0, 1.0"), "1.0, 1.0"); panel2D.addView(scale2D);
        
        panel2D.addView(createSubTitle(L("Delta X,Y (视差):"))); 
        EditText delta2D = createInput(L("1.0, 1.0"), "1.0, 1.0"); panel2D.addView(delta2D);
        
        panel2D.addView(createSubTitle(L("Trans (透明混合):"))); 
        EditText trans2D = createInput(L("add/sub/none"), "none"); panel2D.addView(trans2D);
        
        Button btnApply2D = createButton(L("✔️ 应用 2D 参数"), "#4CAF50");
        btnApply2D.setOnClickListener(clickApp2D -> {
            if (selectedLayerIndex[0] > 0) {
                StageLayerInfo layer = layerList.get(selectedLayerIndex[0]);
                try {
                    String[] sc = scale2D.getText().toString().split(","); 
                    layer.scaleX = Float.parseFloat(sc[0].trim()); 
                    layer.scaleY = Float.parseFloat(sc[1].trim());
                    
                    String[] dl = delta2D.getText().toString().split(","); 
                    layer.deltaX = Float.parseFloat(dl[0].trim()); 
                    layer.deltaY = Float.parseFloat(dl[1].trim());
                    
                    layer.trans = trans2D.getText().toString().trim();
                    
                    txtRes.setText(String.format(L(" 📐 %dx%d "), (int)(layer.origW * layer.scaleX), (int)(layer.origH * layer.scaleY)));
                    viewportFrame.invalidate(); 
                    Toast.makeText(getContext(), L("✅ 2D 参数已应用到当前图层"), Toast.LENGTH_SHORT).show();
                } catch(Exception e){}
            }
        });
        panel2D.addView(btnApply2D); rightPanel.addView(panel2D); rightScroll.addView(rightPanel);

        leftPanel.setTag("layout_left"); centerContainer.setTag("layout_center"); rightScroll.setTag("layout_right"); viewportFrame.setTag("layout_preview"); dpadAreaContainer.setTag("layout_toolbar");
        mainArea.addView(leftPanel, new LinearLayout.LayoutParams(0, -1, 1.2f)); mainArea.addView(centerContainer, new LinearLayout.LayoutParams(0, -1, 2.5f)); mainArea.addView(rightScroll, new LinearLayout.LayoutParams(0, -1, 1f));
        root.addView(mainArea, new LinearLayout.LayoutParams(-1, -1));

        refreshLayerListUI[0] = new Runnable() {
            @Override public void run() {
                layerListLayout.removeAllViews();
                int activeG = -999; int activeI = -999;
                if (selectedLayerIndex[0] > 0 && selectedLayerIndex[0] < layerList.size()) { activeG = layerList.get(selectedLayerIndex[0]).group; activeI = layerList.get(selectedLayerIndex[0]).item; }
                java.util.HashSet<String> seenContainers = new java.util.HashSet<>();

                for (int i = 0; i < layerList.size(); i++) { 
                    final int idx = i; final StageLayerInfo info = layerList.get(i);
                    if (!info.isGhostGrid && !isLayerMode[0]) { String key = info.group + "_" + info.item; if (seenContainers.contains(key)) continue; seenContainers.add(key); } 
                    else if (!info.isGhostGrid && isLayerMode[0] && (info.group != activeG || info.item != activeI)) continue;

                    LinearLayout row = new LinearLayout(getContext()); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setBackgroundColor(selectedLayerIndex[0] == idx ? Color.parseColor("#0078D7") : Color.parseColor("#3F3F46"));
                    row.setPadding((int)(5*density), (int)(8*density), (int)(5*density), (int)(8*density));
                    
                    Button btnVis = createButton(info.isVisible ? "👁️" : "❌", "#333333"); btnVis.setPadding(0,(int)(5*density),0,(int)(5*density));
                    btnVis.setOnClickListener(clickVisLyr -> { info.isVisible = !info.isVisible; info.manuallyVisible = info.isVisible; this.run(); viewportFrame.invalidate(); });
                    Button bLock = createButton(info.isLocked ? "🔒" : "🔓", "#333333"); bLock.setPadding(0,(int)(5*density),0,(int)(5*density));
                    bLock.setOnClickListener(clickLockLyr -> { info.isLocked = !info.isLocked; this.run(); });
                    
                    TextView tName = new TextView(getContext()); 
                    if (info.isGhostGrid) tName.setText(" " + info.name);
                    else if (!isLayerMode[0]) { 
                        int subCount = 0; 
                        for (StageLayerInfo l : layerList) { 
                            if (!l.isGhostGrid && l.group == info.group && l.item == info.item) subCount++; 
                        } 
                        // 包装“动作”标签与“层”单位
                        tName.setText(String.format(L(" 🎬 动作 [%d,%d] (%d层)"), info.group, info.item, subCount)); 
                    } 
                    else tName.setText(" 📄 " + info.name);
                    
                    tName.setTextColor(info.isGhostGrid ? Color.parseColor("#8888FF") : Color.WHITE); applyGlobalFontSettings(tName, 0.9f, false);
                    row.addView(btnVis, new LinearLayout.LayoutParams((int)(40*density), -2)); row.addView(bLock, new LinearLayout.LayoutParams((int)(40*density), -2)); row.addView(tName, new LinearLayout.LayoutParams(0, -2, 1f));
                    
                    row.setOnClickListener(clickRowLyr -> { 
                        selectedLayerIndex[0] = idx; 
                        txtCoord.setText(String.format(L(" X:%.0f Y:%.0f "), info.startX, info.startY));
                        
                        if(!info.isGhostGrid) { 
                            // 处理模式切换下的 GI 文字显示
                            txtGI.setText(String.format(isLayerMode[0] ? L(" 图层%d|G%d ") : " [%d,%d] ", info.item, info.group)); 
                            txtRes.setText(String.format(L(" 📐 %dx%d "), (int)(info.origW * info.scaleX), (int)(info.origH * info.scaleY))); 
                            scale2D.setText(info.scaleX + ", " + info.scaleY); 
                            delta2D.setText(info.deltaX + ", " + info.deltaY); 
                            trans2D.setText(info.trans); 
                        } else { 
                            txtGI.setText(L(" [N/A] ")); 
                            txtRes.setText(L(" 📐 N/A ")); 
                        }
                        
                        for (int j = 1; j < layerList.size(); j++) { 
                            StageLayerInfo l = layerList.get(j); 
                            if (j == idx) l.isVisible = true; 
                            else l.isVisible = l.manuallyVisible; 
                        }
                        
                        // 异步解码逻辑：如果当前没有缓存图，则根据 sourcePath 去 SFF 中抓取
                        if (!info.isGhostGrid && info.cacheBmp == null && !info.sourcePath.isEmpty()) {
                            new Thread(() -> { 
                                Bitmap bmp = null; int[] sizeInfo = new int[2];
                                if (!info.isExternal && info.sourcePath.toLowerCase().endsWith(".sff")) { 
                                    byte[] bmpData = Api.decodeSffFrame(info.sourcePath, info.originalGroup, info.originalItem, ""); 
                                    bmp = StageLayerInfo.safeDecode(null, bmpData, sizeInfo); 
                                } else {
                                    bmp = StageLayerInfo.safeDecode(info.sourcePath, null, sizeInfo);
                                }
                                
                                if (bmp != null) { 
                                    info.origW = sizeInfo[0]; 
                                    info.origH = sizeInfo[1]; 
                                    final Bitmap fbm = bmp; 
                                    new Handler(Looper.getMainLooper()).post(() -> { 
                                        info.cacheBmp = fbm; 
                                        viewportFrame.invalidate(); 
                                        // 仅当用户依然选中该图层时更新尺寸 UI
                                        if (selectedLayerIndex[0] == idx) {
                                            txtRes.setText(String.format(L(" 📐 %dx%d "), (int)(info.origW * info.scaleX), (int)(info.origH * info.scaleY))); 
                                        }
                                    }); 
                                } 
                            }).start();
                        }
                        this.run(); viewportFrame.invalidate();
                    });
                    layerListLayout.addView(row, 0, new LinearLayout.LayoutParams(-1, -2));
                }
            }
        };

        updateViewState[0] = new Runnable() {
            @Override public void run() {
                sffCenterPanel.setVisibility(View.GONE); defEditorPanel.setVisibility(View.GONE);
                btnViewSff.setBackgroundColor(Color.parseColor("#333333")); btnViewDef.setBackgroundColor(Color.parseColor("#333333"));
                if (currentViewMode[0] == 0) { sffCenterPanel.setVisibility(View.VISIBLE); btnViewSff.setBackgroundColor(Color.parseColor("#4CAF50")); } 
                else if (currentViewMode[0] == 1) { defEditorPanel.setVisibility(View.VISIBLE); btnViewDef.setBackgroundColor(Color.parseColor("#FF9800")); } 
            }
        };

        btnViewSff.setOnClickListener(clickVMode1 -> { currentViewMode[0] = 0; updateViewState[0].run(); }); 
        btnViewDef.setOnClickListener(clickVMode2 -> { currentViewMode[0] = 1; updateViewState[0].run(); }); 
        btnMode2D.setOnClickListener(clickM2 -> { is3DMode[0] = false; btnMode2D.setBackgroundColor(Color.parseColor("#9C27B0")); btnMode3D.setBackgroundColor(Color.parseColor("#333333")); });

        btnEditGI.setOnClickListener(clickEGI -> {
            if (selectedLayerIndex[0] == 0) { Toast.makeText(getContext(), L("底板不可修改"), Toast.LENGTH_SHORT).show(); return; }
            StageLayerInfo curLayer = layerList.get(selectedLayerIndex[0]);
            final Dialog d = new Dialog(getContext()); d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            FrameLayout fl = new FrameLayout(getContext()); ScrollView sv = new ScrollView(getContext()); LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setBackgroundColor(Color.parseColor("#252526")); box.setPadding(padM,padM,padM,padM);
            GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#252526")); border.setStroke((int)(2*density), Color.parseColor("#0078D7")); border.setCornerRadius(15*density); box.setBackground(border);
            box.addView(createSubTitle(isLayerMode[0] ? L("⚙️ 调整图层属性") : L("⚙️ 调整动作编组"))); 
            box.addView(createSubTitle(L("图层名称:"))); 
            EditText nameIn = createInput("", curLayer.name); box.addView(nameIn);
            
            box.addView(createSubTitle(isLayerMode[0] ? L("所属动作组 (默认不可动):") : L("Group 动作组号:"))); 
            EditText grpIn = createInput("", String.valueOf(curLayer.group)); grpIn.setInputType(InputType.TYPE_CLASS_NUMBER); box.addView(grpIn);
            
            box.addView(createSubTitle(isLayerMode[0] ? L("图层层深编号 (Item):") : L("Item 帧编号:"))); 
            EditText itmIn = createInput("", String.valueOf(curLayer.item)); itmIn.setInputType(InputType.TYPE_CLASS_NUMBER); box.addView(itmIn);
            
            Button bSave = createButton(L("✔️ 保存并智能排位"), "#4CAF50");
            bSave.setOnClickListener(clickSGI -> {
                try {
                    curLayer.name = nameIn.getText().toString().trim(); 
                    int ng = Integer.parseInt(grpIn.getText().toString().trim()); 
                    int ni = Integer.parseInt(itmIn.getText().toString().trim());
                    
                    StageLayerInfo conflict = null; 
                    for (StageLayerInfo l : layerList) { 
                        if (l != curLayer && !l.isGhostGrid && l.group == ng && l.item == ni) { 
                            conflict = l; break; 
                        } 
                    }
                    
                    if (conflict != null) { 
                        int og = curLayer.group; int oi = curLayer.item; 
                        curLayer.group = ng; curLayer.item = ni; 
                        conflict.group = og; conflict.item = oi; 
                        Toast.makeText(getContext(), L("🔄 检测到层深/帧号被占用，已互相交换"), Toast.LENGTH_LONG).show(); 
                    } 
                    else { 
                        curLayer.group = ng; curLayer.item = ni; 
                        Toast.makeText(getContext(), L("✅ 属性已更新"), Toast.LENGTH_SHORT).show(); 
                    }
                    
                    if (layerList.size() > 1) { 
                        StageLayerInfo ghost = layerList.remove(0); 
                        java.util.Collections.sort(layerList, new java.util.Comparator<StageLayerInfo>() { 
                            public int compare(StageLayerInfo a, StageLayerInfo b) { 
                                if (a.group != b.group) return Integer.compare(a.group, b.group); 
                                return Integer.compare(a.item, b.item); 
                            } 
                        }); 
                        layerList.add(0, ghost); 
                        selectedLayerIndex[0] = layerList.indexOf(curLayer); 
                    }
                    
                    txtGI.setText(String.format(isLayerMode[0] ? L(" 图层%d|G%d ") : " [%d,%d] ", curLayer.item, curLayer.group)); 
                    refreshLayerListUI[0].run(); d.dismiss();
                } catch(Exception e){}
            });
            
            Button bCancel = createButton(L("❌ 取消"), "#333333"); bCancel.setOnClickListener(clickCGI -> d.dismiss());
            LinearLayout btnRow = new LinearLayout(getContext()); btnRow.setOrientation(LinearLayout.HORIZONTAL); 
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f); 
            lp.setMargins((int)(2*density), (int)(10*density), (int)(2*density), 0); 
            btnRow.addView(bSave, lp); btnRow.addView(bCancel, lp); box.addView(btnRow); sv.addView(box); 
            fl.addView(sv, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER)); d.setContentView(fl); d.show();
        });

        Runnable openImageImporter = new Runnable() {
            @Override public void run() {
                showWin10FilePicker(L("选择 图片或GIF 目录"), 7, null, null, fileImg -> {
                    FileCallback processImage = imgFile44 -> {
                        String lowerPath = imgFile44.getAbsolutePath().toLowerCase();
                        if (lowerPath.endsWith(".gif")) {
                            final Dialog dGif = new Dialog(getContext()); 
                            dGif.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); 
                            FrameLayout flGif = new FrameLayout(getContext()); ScrollView svGif = new ScrollView(getContext()); 
                            LinearLayout boxGif = new LinearLayout(getContext()); boxGif.setOrientation(LinearLayout.VERTICAL); 
                            boxGif.setBackgroundColor(Color.parseColor("#252526")); boxGif.setPadding(padM,padM,padM,padM); 
                            GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#252526")); 
                            border.setStroke((int)(2*density), Color.parseColor("#0078D7")); border.setCornerRadius(15*density); 
                            boxGif.setBackground(border); 
                            
                            boxGif.addView(createSubTitle(L("🎞️ 检测到 GIF 动图"))); 
                            TextView hint = new TextView(getContext()); 
                            hint.setText(L("由于 GIF 有多帧，请选择导入策略：")); 
                            hint.setTextColor(Color.LTGRAY); boxGif.addView(hint);
                            
                            long count = Api.getGifFrameCount(imgFile44.getAbsolutePath()); 
                            int totalFrames = (int)count; 
                            if(totalFrames <= 0) { 
                                Toast.makeText(getContext(), L("❌ GIF 解析失败"), Toast.LENGTH_SHORT).show(); 
                                dGif.dismiss(); return; 
                            }
                            ImageView previewGif = new ImageView(getContext()); previewGif.setLayoutParams(new LinearLayout.LayoutParams(-1, (int)(150*density))); boxGif.addView(previewGif);
                            final int[] currentFrame = {0}; final Bitmap[] currentBmp = {null}; final int[] curSize = new int[2];
                            Runnable updatePreviewGif = () -> { new Thread(() -> { byte[] b = Api.decodeGifFrame(imgFile44.getAbsolutePath(), currentFrame[0]); if(b != null) { currentBmp[0] = StageLayerInfo.safeDecode(null, b, curSize); new Handler(Looper.getMainLooper()).post(() -> previewGif.setImageBitmap(currentBmp[0])); } }).start(); };
                            TextView frameInfo = new TextView(getContext()); frameInfo.setTextColor(Color.WHITE); frameInfo.setGravity(Gravity.CENTER); boxGif.addView(frameInfo);
                            SeekBar slider = new SeekBar(getContext()); slider.setMax(totalFrames - 1); slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(SeekBar s, int p, boolean b) { if(b) { currentFrame[0] = p; frameInfo.setText(L("当前预览: 第 ") + (p+1) + L(" / ") + totalFrames + L(" 帧")); updatePreviewGif.run(); } } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){} }); boxGif.addView(slider); frameInfo.setText(L("当前预览: 第 1 / ") + totalFrames + L(" 帧")); updatePreviewGif.run();
                    
                    boxGif.addView(createSubTitle(L("导入名称前缀:"))); EditText nameInput = createInput("", imgFile44.getName().replace(".gif","")); boxGif.addView(nameInput);
                    String defG2 = "0"; String defI2 = "0"; if (selectedLayerIndex[0] > 0 && selectedLayerIndex[0] < layerList.size()) { defG2 = String.valueOf(layerList.get(selectedLayerIndex[0]).group); defI2 = String.valueOf(layerList.get(selectedLayerIndex[0]).item); }
                    
                    boxGif.addView(createSubTitle(L("所属 Group 编号:"))); EditText groupInput = createInput("", isLayerMode[0] ? defG2 : "0"); groupInput.setInputType(InputType.TYPE_CLASS_NUMBER); boxGif.addView(groupInput);
                    boxGif.addView(createSubTitle(L("所属 Item 编号:"))); EditText itemInput = createInput("", isLayerMode[0] ? defI2 : "0"); itemInput.setInputType(InputType.TYPE_CLASS_NUMBER); if (isLayerMode[0]) boxGif.addView(itemInput);
                    
                    Button btnSingle = createButton(isLayerMode[0] ? L("🎯 并入当前动作图层") : L("🎯 导入为新动作"), "#4CAF50");
                    btnSingle.setOnClickListener(clickSingleGif -> { StageLayerInfo layer = new StageLayerInfo(); layer.name = nameInput.getText().toString() + L(" [帧") + currentFrame[0] + L("]"); int g = 0; try { g = Integer.parseInt(groupInput.getText().toString()); } catch(Exception e){} if (isLayerMode[0]) { int i = 0; try { i = Integer.parseInt(itemInput.getText().toString()); } catch(Exception e){} layer.group = g; layer.item = i; } else { int[] gi = incrementer.getNext(g, currentFrame[0]); layer.group = gi[0]; layer.item = gi[1]; } layer.origW = curSize[0]; layer.origH = curSize[1]; try { File tmpF = new File(getContext().getCacheDir(), "gif_ext_" + System.currentTimeMillis() + ".png"); FileOutputStream fos = new FileOutputStream(tmpF); currentBmp[0].compress(Bitmap.CompressFormat.PNG, 100, fos); fos.close(); layer.sourcePath = tmpF.getAbsolutePath(); layer.isExternal = true; } catch(Exception e){} layer.cacheBmp = currentBmp[0]; layer.isVisible = false; layer.manuallyVisible = false; layerList.add(layer); refreshLayerListUI[0].run(); dGif.dismiss(); Toast.makeText(getContext(), L("✅ 已添加单帧图层"), Toast.LENGTH_SHORT).show(); });
                    
                    Button btnAll = createButton(L("🚀 瞬间拆解所有帧追加"), "#9C27B0");
                    btnAll.setOnClickListener(clickAllGif -> { Toast.makeText(getContext(), L("正在提取全部帧序列..."), Toast.LENGTH_SHORT).show(); new Thread(() -> { int grp = 0; try { grp = Integer.parseInt(groupInput.getText().toString()); } catch(Exception e){} final int fGrp = grp; final String baseName = nameInput.getText().toString(); for (int i=0; i<totalFrames; i++) { byte[] b = Api.decodeGifFrame(imgFile44.getAbsolutePath(), i); if(b != null) { int[] sz = new int[2]; Bitmap bmp = StageLayerInfo.safeDecode(null, b, sz); StageLayerInfo layer = new StageLayerInfo(); layer.name = baseName + L(" [帧") + i + L("]"); int[] gi = incrementer.getNext(fGrp, i); layer.group = gi[0]; layer.item = gi[1]; layer.origW = sz[0]; layer.origH = sz[1]; try { File tmpF = new File(getContext().getCacheDir(), "gif_ext_" + System.currentTimeMillis() + "_" + i + ".png"); FileOutputStream fos = new FileOutputStream(tmpF); bmp.compress(Bitmap.CompressFormat.PNG, 100, fos); fos.close(); layer.sourcePath = tmpF.getAbsolutePath(); layer.isExternal = true; } catch(Exception e){} layer.cacheBmp = bmp; layer.isVisible = false; layer.manuallyVisible = false; layerList.add(layer); } } new Handler(Looper.getMainLooper()).post(() -> { refreshLayerListUI[0].run(); dGif.dismiss(); Toast.makeText(getContext(), L("✅ GIF 全部拆解并生成追加图层！"), Toast.LENGTH_LONG).show(); }); }).start(); });
                    
                    Button btnCancel = createButton(L("❌ 取消"), "#333333"); btnCancel.setOnClickListener(clickCanGif -> dGif.dismiss()); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, (int)(10*density), 0, 0); boxGif.addView(btnSingle, lp); boxGif.addView(btnAll, lp); boxGif.addView(btnCancel, lp); svGif.addView(boxGif); flGif.addView(svGif, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER)); dGif.setContentView(flGif); dGif.show();
                        } else {
                            final Dialog dImg = new Dialog(getContext()); dImg.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); FrameLayout flImg = new FrameLayout(getContext()); ScrollView svImg = new ScrollView(getContext()); LinearLayout boxImg = new LinearLayout(getContext()); boxImg.setOrientation(LinearLayout.VERTICAL); boxImg.setBackgroundColor(Color.parseColor("#252526")); boxImg.setPadding(padM,padM,padM,padM); GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#252526")); border.setStroke((int)(2*density), Color.parseColor("#0078D7")); border.setCornerRadius(15*density); boxImg.setBackground(border); boxImg.addView(createSubTitle(L("🖼️ 导入单张静态图像"))); ImageView previewImgView = new ImageView(getContext()); previewImgView.setLayoutParams(new LinearLayout.LayoutParams(-1, (int)(150*density))); int[] sizeInfo = new int[2]; Bitmap bmp = StageLayerInfo.safeDecode(imgFile44.getAbsolutePath(), null, sizeInfo); previewImgView.setImageBitmap(bmp); boxImg.addView(previewImgView); boxImg.addView(createSubTitle(L("自定义名称:"))); EditText nameInput = createInput("", imgFile44.getName()); boxImg.addView(nameInput); String defG = "0"; String defI = "0"; if (selectedLayerIndex[0] > 0 && selectedLayerIndex[0] < layerList.size()) { defG = String.valueOf(layerList.get(selectedLayerIndex[0]).group); defI = String.valueOf(layerList.get(selectedLayerIndex[0]).item); } boxImg.addView(createSubTitle(L("设定 Group 编号:"))); EditText groupInput = createInput("", isLayerMode[0] ? defG : "0"); groupInput.setInputType(InputType.TYPE_CLASS_NUMBER); boxImg.addView(groupInput); boxImg.addView(createSubTitle(L("设定 Item 编号:"))); EditText itemInput = createInput("", isLayerMode[0] ? defI : "0"); itemInput.setInputType(InputType.TYPE_CLASS_NUMBER); boxImg.addView(itemInput); LinearLayout btnRow = new LinearLayout(getContext()); btnRow.setOrientation(LinearLayout.HORIZONTAL); Button btnAdd = createButton(isLayerMode[0] ? L("✔️ 追加为当前图层") : L("✔️ 独立追加新动作"), "#4CAF50");
                    
                    btnAdd.setOnClickListener(clickAddImg -> { StageLayerInfo layer = new StageLayerInfo(); layer.name = nameInput.getText().toString(); int g = 0; try { g = Integer.parseInt(groupInput.getText().toString()); } catch(Exception e){} int i = 0; try { i = Integer.parseInt(itemInput.getText().toString()); } catch(Exception e){} if (isLayerMode[0]) { layer.group = g; layer.item = i; } else { int[] gi = incrementer.getNext(g, i); layer.group = gi[0]; layer.item = gi[1]; } layer.origW = sizeInfo[0]; layer.origH = sizeInfo[1]; layer.sourcePath = imgFile44.getAbsolutePath(); layer.cacheBmp = bmp; layer.isVisible = false; layer.manuallyVisible = false; layer.isExternal = true; layerList.add(layer); refreshLayerListUI[0].run(); dImg.dismiss(); Toast.makeText(getContext(), L("✅ 已追加新图层"), Toast.LENGTH_SHORT).show(); });
                    
                    Button btnCancel = createButton(L("❌ 取消"), "#333333"); btnCancel.setOnClickListener(clickCanImg -> dImg.dismiss()); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f); lp.setMargins((int)(2*density), (int)(10*density), (int)(2*density), 0); btnRow.addView(btnAdd, lp); btnRow.addView(btnCancel, lp); boxImg.addView(btnRow); svImg.addView(boxImg); flImg.addView(svImg, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER)); dImg.setContentView(flImg); dImg.show();
                        }
                    };
                    if (fileImg.isDirectory()) showGenericFileListPicker(fileImg, new String[]{".png", ".jpg", ".jpeg", ".gif", ".pcx"}, L("外部图像素材"), "#4CAF50", processImage);
                    else processImage.onFileSelected(fileImg);
                });
            }
        };

btnImportMenu.setOnClickListener(clickImpMenu -> {
            final Dialog iDialog = new Dialog(getContext()); iDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); 
            FrameLayout flMenu = new FrameLayout(getContext()); ScrollView svMenu = new ScrollView(getContext()); 
            LinearLayout iBox = new LinearLayout(getContext()); iBox.setOrientation(LinearLayout.VERTICAL); iBox.setBackgroundColor(Color.parseColor("#252526")); iBox.setPadding(padM,padM,padM,padM); 
            GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#252526")); border.setStroke((int)(2*density), Color.parseColor("#0078D7")); border.setCornerRadius(15*density); iBox.setBackground(border); 
            iBox.addView(createSubTitle(L("📥 导入素材到场景"))); 
            
            Button iSff = createButton(L("🖼️ 追加 SFF 官方图集包"), "#FF9800");
            iSff.setOnClickListener(clickImpSff -> { iDialog.dismiss(); showWin10FilePicker(L("选择 SFF 文件或目录"), 4, null, null, fileSff -> { FileCallback extractAction = finalFileSff -> { Toast.makeText(getContext(), L("正在提取 SFF 并追加图层..."), Toast.LENGTH_SHORT).show(); String currentPath = finalFileSff.getAbsolutePath(); new Thread(() -> { List<GoEngineBridge.SffFrame> frames = GoEngineBridge.getAllFrames(currentPath); new Handler(Looper.getMainLooper()).post(() -> { for (GoEngineBridge.SffFrame f : frames) { StageLayerInfo layer = new StageLayerInfo(); layer.name = L("Sprite [") + f.group + ", " + f.item + "]"; int[] gi = incrementer.getNext(f.group, f.item); layer.group = gi[0]; layer.item = gi[1]; layer.originalGroup = f.group; layer.originalItem = f.item; layer.origW = f.width; layer.origH = f.height; layer.axisX = f.x; layer.axisY = f.y; layer.sourcePath = currentPath; layer.isVisible = false; layer.manuallyVisible = false; layer.isExternal = false; layerList.add(layer); } refreshLayerListUI[0].run(); Toast.makeText(getContext(), L("✅ SFF 解析完成！追加提取了 ") + frames.size() + L(" 个图层"), Toast.LENGTH_LONG).show(); }); }).start(); }; if (fileSff.isDirectory()) showSffGridPicker(fileSff, extractAction); else extractAction.onFileSelected(fileSff); }); });
            iBox.addView(iSff, new LinearLayout.LayoutParams(-1, -2)); 
            
            Button iImg = createButton(L("🖼️ 追加 单张图片 / GIF 动画"), "#4CAF50"); 
            LinearLayout.LayoutParams lpImg = new LinearLayout.LayoutParams(-1, -2); lpImg.setMargins(0,(int)(10*density),0,0); 
            iImg.setOnClickListener(clickImpImg -> { iDialog.dismiss(); openImageImporter.run(); }); 
            iBox.addView(iImg, lpImg); 

            // 🌟 永远允许导入 3D 模型，导入后进入 3D 全屏沙盘即可布置
            Button iMod = createButton(L("🧊 导入 3D 模型 (.glb / .gltf)"), "#0078D7");
            iMod.setOnClickListener(clickImpMod -> { 
                iDialog.dismiss(); 
                showWin10FilePicker(L("选择 3D 模型"), 11, null, null, fileMod -> {
                    FileCallback onSelected = fMod -> {
                        Toast.makeText(getContext(), L("✅ 3D模型已添加入列！请进入【🧊 3D 全屏工作室】进行摆放"), Toast.LENGTH_LONG).show();
                    };
                    if(fileMod.isDirectory()) showGenericFileListPicker(fileMod, new String[]{".gltf", ".glb"}, L("3D模型"), "#0078D7", onSelected); else onSelected.onFileSelected(fileMod);
                }); 
            });
            iBox.addView(iMod, lpImg);

            Button iCancel = createButton(L("❌ 取消"), "#333333"); 
            iCancel.setOnClickListener(clickImpCan -> iDialog.dismiss()); 
            iBox.addView(iCancel, lpImg); 
            
            svMenu.addView(iBox); flMenu.addView(svMenu, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER)); iDialog.setContentView(flMenu); iDialog.show();
        });

        btnSettings.setOnClickListener(clickSet -> {
            final Dialog setDialog = new Dialog(getContext()); setDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); FrameLayout flSet = new FrameLayout(getContext()); ScrollView svSet = new ScrollView(getContext()); LinearLayout setBox = new LinearLayout(getContext()); setBox.setOrientation(LinearLayout.VERTICAL); setBox.setBackgroundColor(Color.parseColor("#252526")); setBox.setPadding(padM,padM,padM,padM); GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#252526")); border.setStroke((int)(2*density), Color.parseColor("#0078D7")); border.setCornerRadius(15*density); setBox.setBackground(border); 
            setBox.addView(createTitle(L("⚙️ 视口偏好设置"))); 
            setBox.addView(createSubTitle(L("背景底色代码 (Hex):"))); 
            EditText bgInput = createInput(L("如: #000080"), String.format("#%06X", (0xFFFFFF & bgColor[0]))); setBox.addView(bgInput); 
            setBox.addView(createSubTitle(L("网格线颜色代码 (Hex):"))); 
            EditText gridColorInput = createInput(L("如: #FFFFFF"), String.format("#%06X", (0xFFFFFF & gridColor[0]))); setBox.addView(gridColorInput); 
            setBox.addView(createSubTitle(L("网格透明度 (0-255):"))); 
            SeekBar alphaBar = new SeekBar(getContext()); alphaBar.setMax(255); alphaBar.setProgress(gridAlpha[0]); alphaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(SeekBar s, int p, boolean b) { gridAlpha[0] = p; viewportFrame.invalidate(); } public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {} }); setBox.addView(alphaBar); 
            Button btnApply = createButton(L("✔️ 应用设置"), "#4CAF50"); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0,(int)(15*density),0,0); btnApply.setOnClickListener(clickAppSet -> { try { bgColor[0] = Color.parseColor(bgInput.getText().toString()); gridColor[0] = Color.parseColor(gridColorInput.getText().toString()); } catch (Exception e){} viewportFrame.invalidate(); setDialog.dismiss(); }); setBox.addView(btnApply, lp); svSet.addView(setBox); flSet.addView(svSet, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER)); setDialog.setContentView(flSet); setDialog.show();
        });

        btnScan.setOnClickListener(clickScan -> {
            final Dialog prompt = new Dialog(getContext()); prompt.requestWindowFeature(Window.FEATURE_NO_TITLE); prompt.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); FrameLayout flScan = new FrameLayout(getContext()); ScrollView svScan = new ScrollView(getContext()); LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setBackgroundColor(Color.parseColor("#252526")); box.setPadding(padM,padM,padM,padM); GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#252526")); border.setStroke((int)(2*density), Color.parseColor("#0078D7")); border.setCornerRadius(15*density); box.setBackground(border); 
            box.addView(createSubTitle(L("📂 工程读取与新建"))); 
            Button btnNew = createButton(L("📄 新建空白地图"), "#4CAF50"); 
            btnNew.setOnClickListener(clickNewScan -> { globalDefPath = ""; globalIsEditMode = false; layerList.clear(); layerList.add(ghostGrid); refreshLayerListUI[0].run(); defCodeInput.setText("[Info]\nname = \"NewStage\"\n\n[BGdef]\nspr = stages/NewStage.sff\ndebugbg = 0"); Toast.makeText(getContext(), L("已建立新工程"), Toast.LENGTH_SHORT).show(); prompt.dismiss(); }); 
            Button btnLoad = createButton(L("📂 读取现有 .def 地图"), "#0078D7");
            btnLoad.setOnClickListener(clickLoadScan -> { prompt.dismiss(); showWin10FilePicker(L("选择 .def 地图工程"), 10, null, null, selectedFileScan -> { FileCallback loadDefAction = finalDef36 -> { final Dialog safePrompt = new Dialog(getContext()); safePrompt.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); FrameLayout flSafe = new FrameLayout(getContext()); ScrollView svSafe = new ScrollView(getContext()); LinearLayout safeBox = new LinearLayout(getContext()); safeBox.setOrientation(LinearLayout.VERTICAL); safeBox.setBackgroundColor(Color.parseColor("#252526")); safeBox.setPadding(padM,padM,padM,padM); safeBox.setBackground(border); 
            safeBox.addView(createSubTitle(L("🛡️ 安全加载选项"))); 
            Runnable performLoad = () -> { try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(globalDefPath))) { StringBuilder sb = new StringBuilder(); String line; while ((line = br.readLine()) != null) { sb.append(line).append("\n"); } defCodeInput.setText(sb.toString()); } catch (Exception e) {} File targetDef = new File(globalDefPath); File sffFile = new File(targetDef.getParent(), targetDef.getName().replace(".def", ".sff")); if (!sffFile.exists()) sffFile = new File(targetDef.getParent(), targetDef.getName().replace(".def", ".SFF")); if (sffFile.exists()) { globalSffPath = sffFile.getAbsolutePath(); new Thread(() -> { List<GoEngineBridge.SffFrame> frames = GoEngineBridge.getAllFrames(globalSffPath); new Handler(Looper.getMainLooper()).post(() -> { layerList.clear(); layerList.add(ghostGrid); for (GoEngineBridge.SffFrame f : frames) { StageLayerInfo layer = new StageLayerInfo(); layer.name = L("Sprite [") + f.group + ", " + f.item + "]"; layer.group = f.group; layer.item = f.item; layer.originalGroup = f.group; layer.originalItem = f.item; layer.origW = f.width; layer.origH = f.height; layer.axisX = f.x; layer.axisY = f.y; layer.sourcePath = globalSffPath; layer.isExternal=false; layer.isVisible=false; layer.manuallyVisible=false; layerList.add(layer); } refreshLayerListUI[0].run(); Toast.makeText(getContext(), L("✅ 已载入 ") + frames.size() + L(" 个素材图层与关联模型"), Toast.LENGTH_LONG).show(); }); }).start(); } }; 
            Button btnBackup = createButton(L("💾 自动防毁备份并读取"), "#4CAF50"); 
            btnBackup.setOnClickListener(clickBackSafe -> { try { File backup = new File(finalDef36.getParent(), finalDef36.getName().replace(".def", "_backup.def")); if (!backup.exists()) copyFileToSandbox(finalDef36, backup); globalDefPath = backup.getAbsolutePath(); globalIsEditMode = true; Toast.makeText(getContext(), L("✅ 已切换至备份工程: ") + backup.getName(), Toast.LENGTH_SHORT).show(); performLoad.run(); } catch(Exception e){} safePrompt.dismiss(); }); 
            Button btnOrig = createButton(L("⚠️ 无视风险直接读取"), "#FF9800"); 
            btnOrig.setOnClickListener(clickOrigSafe -> { globalDefPath = finalDef36.getAbsolutePath(); globalIsEditMode = true; performLoad.run(); safePrompt.dismiss(); }); 
            Button btnCancel = createButton(L("❌ 取消"), "#333333"); btnCancel.setOnClickListener(clickCanSafe -> safePrompt.dismiss()); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0,0,0,(int)(10*density)); safeBox.addView(btnBackup, lp); safeBox.addView(btnOrig, lp); safeBox.addView(btnCancel, lp); svSafe.addView(safeBox); flSafe.addView(svSafe, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER)); safePrompt.setContentView(flSafe); safePrompt.show(); }; if (selectedFileScan.isDirectory()) showGenericFileListPicker(selectedFileScan, new String[]{".def"}, L("地图工程"), "#E81123", loadDefAction); else loadDefAction.onFileSelected(selectedFileScan); }); }); Button btnCancelMain = createButton(L("❌ 取消"), "#333333"); btnCancelMain.setOnClickListener(clickCanScan -> prompt.dismiss()); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0,0,0,(int)(10*density)); box.addView(btnNew, lp); box.addView(btnLoad, lp); box.addView(btnCancelMain, lp); svScan.addView(box); flScan.addView(svScan, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER)); prompt.setContentView(flScan); prompt.show();
        });

        btnSave.setOnClickListener(clickSaveMain -> {
            final Dialog exportDialog = new Dialog(getContext()); exportDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); FrameLayout flExp = new FrameLayout(getContext()); ScrollView svExp = new ScrollView(getContext()); LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setBackgroundColor(Color.parseColor("#252526")); box.setPadding(padM,padM,padM,padM); GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#252526")); border.setStroke((int)(2*density), Color.parseColor("#0078D7")); border.setCornerRadius(15*density); box.setBackground(border); box.addView(createSubTitle(L("💾 执行打包导出 (仅限 2D)"))); 
            String defaultName = "NewStage"; 
            if (globalIsEditMode && !globalDefPath.isEmpty()) defaultName = new File(globalDefPath).getName().replace(".def", "");
            
            box.addView(createSubTitle(L("地图导出前缀名:"))); 
            EditText nameInput = createInput(L("(默认追加递增防重名)"), defaultName); 
            box.addView(nameInput);

            Button bConfirm = createButton(L("✔️ 确认保存并分离图层导出"), "#4CAF50");
            bConfirm.setOnClickListener(clickConfSave -> {
                exportDialog.dismiss(); 
                Toast.makeText(getContext(), L("📦 引擎正在原生多图层分离导出中..."), Toast.LENGTH_SHORT).show();
                new Thread(() -> {
                    try {
                        String baseName = nameInput.getText().toString().trim(); if (baseName.isEmpty()) baseName = "NewStage"; File rootExportDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "IkemenExports"); File tempDir = new File(rootExportDir, baseName); int counter = 1; while (tempDir.exists()) { tempDir = new File(rootExportDir, baseName + "_" + counter); counter++; } tempDir.mkdirs(); final File finalExportDir = tempDir; 
                        
                        String rawDef = defCodeInput.getText().toString(); 
                        String cleanedDef = rawDef.replaceAll("(?i)\\[BG\\s+.*?\\][\\s\\S]*?(?=\\[|$)", ""); 
                        cleanedDef = cleanedDef.replaceAll("(?i)\\[BG\\][\\s\\S]*?(?=\\[|$)", ""); 
                        
                        StringBuilder defBuilder = new StringBuilder(cleanedDef);
                        for (StageLayerInfo layer : layerList) {
                            if (layer.isGhostGrid) continue;
                            defBuilder.append("\n[BG ").append(layer.name).append("]\n");
                            defBuilder.append("type = normal\n");
                            defBuilder.append("spriteno = ").append(layer.group).append(", ").append(layer.item).append("\n");
                            defBuilder.append("start = ").append((int)layer.startX).append(", ").append((int)layer.startY).append("\n");
                            defBuilder.append("delta = ").append(layer.deltaX).append(", ").append(layer.deltaY).append("\n");
                            defBuilder.append("mask = 1\n");
                            if (layer.scaleX != 1.0f || layer.scaleY != 1.0f) {
                                defBuilder.append("scalestart = ").append(layer.scaleX).append(", ").append(layer.scaleY).append("\n");
                            }
                            if (layer.trans != null && !layer.trans.trim().equals("none")) {
                                defBuilder.append("trans = ").append(layer.trans).append("\n");
                            }
                        }                      
                        File defFile = new File(finalExportDir, baseName + ".def"); 
                        FileOutputStream defOut = new FileOutputStream(defFile); 
                        defOut.write(defBuilder.toString().getBytes("UTF-8")); 
                        defOut.close();
                        
                        File finalSffFile = new File(finalExportDir, baseName + ".sff"); 
                        if (!globalSffPath.isEmpty()) { 
                            copyFileToSandbox(new File(globalSffPath), finalSffFile); 
                            if (finalSffFile.length() > 0) {
                                List<GoEngineBridge.SffFrame> origFrames = GoEngineBridge.getAllFrames(finalSffFile.getAbsolutePath());
                                for (GoEngineBridge.SffFrame f : origFrames) {
                                    boolean keepAsIs = false;
                                    for (StageLayerInfo l : layerList) {
                                        // 仅保留完全未做编号修改的原始图层
                                        if (!l.isGhostGrid && !l.isExternal && l.sourcePath.equals(globalSffPath)) {
                                            if (l.originalGroup == f.group && l.originalItem == f.item) {
                                                if (l.group == f.group && l.item == f.item && Math.abs(l.scaleX) == 1.0f && Math.abs(l.scaleY) == 1.0f) { keepAsIs = true; }
                                                break;
                                            }
                                        }
                                    }
                                    if (!keepAsIs) { Api.deleteSffFrame(finalSffFile.getAbsolutePath(), f.group, f.item); }
                                }
                            }
                        } else { 
                            finalSffFile.createNewFile(); 
                        } 
                        
                        for (StageLayerInfo layer : layerList) { 
                            if (layer.isGhostGrid) continue;
                            
                            // 校验该图层是否已安全且毫无改动地保存在文件中，增加缩放判定
                            boolean isNativeAndUnchanged = (!globalSffPath.isEmpty() && !layer.isExternal && layer.sourcePath.equals(globalSffPath) && layer.group == layer.originalGroup && layer.item == layer.originalItem && Math.abs(layer.scaleX) == 1.0f && Math.abs(layer.scaleY) == 1.0f);
                            
                            if (!isNativeAndUnchanged) {
                                File tmpPng = null;
                                Bitmap layerBmp = null;
                                
                                // 提取图像的原始像素数据以供手术
                                if (layer.isExternal && layer.sourcePath != null && !layer.sourcePath.isEmpty()) {
                                    layerBmp = BitmapFactory.decodeFile(layer.sourcePath);
                                } else if (!layer.isExternal && layer.sourcePath != null && !layer.sourcePath.isEmpty()) {
                                    // 从其他 SFF 导入的图片，或是修改了原本编号的图层：将其解包并重构
                                    byte[] bmpData = Api.decodeSffFrame(layer.sourcePath, layer.originalGroup, layer.originalItem, "");
                                    if (bmpData != null && bmpData.length > 0) {
                                        layerBmp = BitmapFactory.decodeByteArray(bmpData, 0, bmpData.length);
                                    }
                                }

                                if (layerBmp != null) {
                                    Bitmap finalBmp = layerBmp;
                                    int finalAxisX = layer.axisX;
                                    int finalAxisY = layer.axisY;

                                    float absScaleX = Math.abs(layer.scaleX);
                                    float absScaleY = Math.abs(layer.scaleY);

                                    // 如果存在缩放，直接从底层物理修改照片的宽、高、及轴心的映射定位！
                                    if (absScaleX != 1.0f || absScaleY != 1.0f) {
                                        int newW = (int)(layerBmp.getWidth() * absScaleX);
                                        int newH = (int)(layerBmp.getHeight() * absScaleY);
                                        if (newW <= 0) newW = 1; if (newH <= 0) newH = 1;
                                        
                                        Matrix m = new Matrix();
                                        m.postScale(absScaleX, absScaleY);
                                        finalBmp = Bitmap.createBitmap(layerBmp, 0, 0, layerBmp.getWidth(), layerBmp.getHeight(), m, true);
                                        
                                        // 图像放大后，中心点也要同步放大跟上
                                        finalAxisX = (int)(layer.axisX * absScaleX);
                                        finalAxisY = (int)(layer.axisY * absScaleY);
                                    }

                                    tmpPng = new File(getContext().getCacheDir(), "tmp_export_" + System.currentTimeMillis() + "_" + layer.group + "_" + layer.item + ".png");
                                    FileOutputStream fos = new FileOutputStream(tmpPng);
                                    finalBmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
                                    fos.close();
                                    
                                    if (finalBmp != layerBmp) finalBmp.recycle();
                                    layerBmp.recycle();
                                    
                                    // 写入 SFF 包内
                                    if (tmpPng != null && tmpPng.exists()) {
                                        Api.addSffFrame(finalSffFile.getAbsolutePath(), layer.group, layer.item, (short)finalAxisX, (short)finalAxisY, tmpPng.getAbsolutePath());
                                    }
                                }
                            }
                        }
                        
                        new Handler(Looper.getMainLooper()).post(() -> { Toast.makeText(getContext(), L("✅ 2D 原生多图层分离导出成功！\n文件在:\n") + finalExportDir.getAbsolutePath(), Toast.LENGTH_LONG).show(); });
                    } catch (Throwable t) { t.printStackTrace(); }
                }).start();
            });
            Button bCancel = createButton(L("❌ 取消"), "#333333"); bCancel.setOnClickListener(clickCanSave -> exportDialog.dismiss()); LinearLayout btnRow = new LinearLayout(getContext()); btnRow.setOrientation(LinearLayout.HORIZONTAL); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f); lp.setMargins((int)(2*density), (int)(10*density), (int)(2*density), 0); btnRow.addView(bConfirm, lp); btnRow.addView(bCancel, lp); box.addView(btnRow); svExp.addView(box); flExp.addView(svExp, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER)); exportDialog.setContentView(flExp); exportDialog.show();
        });

                // 🚀 终极全自由 3D 全屏沉浸工作台 (天空盒 + 自定义光源 + 全格式支持 + Draco压缩)
        btnMode3D.setText(L("3D 地图查看器")); // 修改按钮文字为查看器
        btnMode3D.setOnClickListener(clickM3 -> {
            final Dialog studioDialog = new Dialog(getContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
            studioDialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            applyImmersiveMode(studioDialog.getWindow());
            
            FrameLayout studioRoot = new FrameLayout(getContext());
            final WebView modelWebView = new WebView(getContext()); 
            modelWebView.getSettings().setJavaScriptEnabled(true); 
            modelWebView.getSettings().setAllowFileAccess(true); 
            modelWebView.getSettings().setAllowFileAccessFromFileURLs(true); 
            modelWebView.getSettings().setAllowUniversalAccessFromFileURLs(true); 
            modelWebView.getSettings().setDomStorageEnabled(true);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                modelWebView.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            }
            modelWebView.setWebChromeClient(new android.webkit.WebChromeClient()); 
            modelWebView.setBackgroundColor(Color.parseColor("#121212"));
            
            modelWebView.addJavascriptInterface(new Object() {
                @android.webkit.JavascriptInterface public void closeStudio() {
                    new Handler(Looper.getMainLooper()).post(() -> studioDialog.dismiss());
                }
                
                @android.webkit.JavascriptInterface public void triggerImport() {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        showWin10FilePicker(L("载入 3D 场景/地图"), 11, null, null, fileMod -> {
                            FileCallback processModel = fMod -> {
                                modelWebView.evaluateJavascript("javascript:loadExternalModel('file://" + fMod.getAbsolutePath() + "');", null);
                                Toast.makeText(getContext(), L("✅ 地图载入中，正在渲染高画质光影..."), Toast.LENGTH_SHORT).show();
                            };
                            if (fileMod.isDirectory()) showGenericFileListPicker(fileMod, new String[]{".gltf", ".glb", ".obj", ".fbx", ".3ds", ".dae", ".ply", ".stl"}, L("3D模型"), "#0078D7", processModel); else processModel.onFileSelected(fileMod);
                        });
                    });
                }
            }, "StudioBridge");

            studioRoot.addView(modelWebView, new FrameLayout.LayoutParams(-1, -1));
            studioDialog.setContentView(studioRoot);
            studioDialog.show();
            studioDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='utf-8'><style>");
            html.append("body,html{margin:0;padding:0;width:100%;height:100%;background-color:#000;overflow:hidden;touch-action:none;user-select:none;font-family:sans-serif;}");
            html.append(".ui-btn{padding:12px 20px; color:white; border:none; border-radius:8px; font-weight:bold; font-size:14px; cursor:pointer; box-shadow:0 4px 6px rgba(0,0,0,0.3); transition:0.2s;}");
            html.append(".ui-btn:active{transform:scale(0.95);}");
            html.append(".err-log{position:absolute; bottom:10px; left:10px; color:#ff4444; z-index:9999; font-size:12px; pointer-events:none;}");
            html.append("</style>");
            
            html.append("<script>window.onerror = function(msg, url, line) { var e = document.createElement('div'); e.className = 'err-log'; e.innerText = '" + L("JS报错: ") + "' + msg + ' (" + L("行 ") + "'+line+')'; document.body.appendChild(e); };</script>");

            html.append("<script src=\"js/three.min.js\"></script>");
            html.append("<script src=\"js/GLTFLoader.js\"></script>");
            html.append("<script src=\"js/OBJLoader.js\"></script>");
            html.append("<script src=\"js/FBXLoader.js\"></script>");
            html.append("<script src=\"js/TDSLoader.js\"></script>");
            html.append("<script src=\"js/ColladaLoader.js\"></script>");
            html.append("<script src=\"js/STLLoader.js\"></script>");
            html.append("<script src=\"js/PLYLoader.js\"></script>");
            html.append("<script src=\"js/nipplejs.min.js\"></script>");
            html.append("<script src=\"js/DRACOLoader.js\"></script>");
            html.append("<script src=\"js/fflate.min.js\"></script>");
            
            html.append("</head><body>");

            // 纯净的 UI 悬浮层
            html.append("<button class='ui-btn' onclick='StudioBridge.triggerImport()' style='position:absolute; top:20px; left:20px; background:rgba(0,120,215,0.8); z-index:1000;'>" + L("📂 载入 3D 地图") + "</button>");
            html.append("<button class='ui-btn' id='btnFilter' onclick='toggleFilter()' style='position:absolute; top:20px; left:180px; background:rgba(156,39,176,0.8); z-index:1000;'>" + L("🎨 画质滤镜: 游戏原画") + "</button>");
            html.append("<button class='ui-btn' onclick='StudioBridge.closeStudio()' style='position:absolute; top:20px; right:20px; background:rgba(232,17,35,0.8); z-index:1000;'>" + L("❌ 退出查看") + "</button>");

            html.append("<script>");
            // 核心渲染器设定：画质拉满
            html.append("var scene = new THREE.Scene(); scene.background = new THREE.Color(0x87CEEB); scene.fog = new THREE.FogExp2(0x87CEEB, 0.002);");
            html.append("var clock = new THREE.Clock(); window.mixers = [];");
            html.append("var camera = new THREE.PerspectiveCamera(65, window.innerWidth/window.innerHeight, 0.1, 20000); camera.position.set(0, 15, 30);");
            html.append("var renderer = new THREE.WebGLRenderer({antialias:true, alpha:false, powerPreference:'high-performance'});");
            html.append("renderer.setSize(window.innerWidth, window.innerHeight); renderer.setPixelRatio(window.devicePixelRatio);");
            html.append("renderer.outputEncoding = THREE.sRGBEncoding; renderer.toneMapping = THREE.ACESFilmicToneMapping; renderer.toneMappingExposure = 1.2;"); // 电影级色彩映射
            html.append("renderer.shadowMap.enabled = true; renderer.shadowMap.type = THREE.PCFSoftShadowMap;");
            html.append("document.body.appendChild(renderer.domElement);");
            
            // 极致光影环境
            html.append("var hemiLight = new THREE.HemisphereLight(0xffffff, 0x444444, 0.6); hemiLight.position.set(0, 500, 0); scene.add(hemiLight);");
            html.append("var dirLight = new THREE.DirectionalLight(0xffffff, 2.0); dirLight.position.set(200, 500, 300); dirLight.castShadow = true;");
            html.append("dirLight.shadow.mapSize.width = 4096; dirLight.shadow.mapSize.height = 4096;"); // 4K 极限阴影
            html.append("dirLight.shadow.camera.near = 1; dirLight.shadow.camera.far = 2000;");
            html.append("dirLight.shadow.camera.left = -500; dirLight.shadow.camera.right = 500; dirLight.shadow.camera.top = 500; dirLight.shadow.camera.bottom = -500;");
            html.append("dirLight.shadow.bias = -0.0005; scene.add(dirLight);");

           // 滤镜系统
            html.append("var filters = [");
            html.append("  {n: '" + L("游戏原画") + "', c: 'none'},");
            html.append("  {n: '" + L("电影大片") + "', c: 'saturate(1.2) contrast(1.1) brightness(0.9) hue-rotate(-5deg)'},");
            html.append("  {n: '" + L("鲜艳动漫") + "', c: 'saturate(1.6) contrast(1.2)'},");
            html.append("  {n: '" + L("赛博朋克") + "', c: 'saturate(2.0) contrast(1.3) hue-rotate(45deg)'},");
            html.append("  {n: '" + L("末日废土") + "', c: 'sepia(0.6) saturate(0.8) contrast(1.2) hue-rotate(-20deg)'},");
            html.append("  {n: '" + L("黑白纪实") + "', c: 'grayscale(1) contrast(1.3)'}");
            html.append("];");
            html.append("var fIdx = 0; window.toggleFilter = function() { " +
                        "fIdx = (fIdx + 1) % filters.length; " +
                        "renderer.domElement.style.filter = filters[fIdx].c; " +
                        "document.getElementById('btnFilter').innerText = '" + L("🎨 画质滤镜: ") + "' + filters[fIdx].n; " +
                        "};");

            // 自由视角与移动控制
            html.append("var euler = new THREE.Euler(0, 0, 0, 'YXZ'); var isLooking = false; var lastTouchX = 0, lastTouchY = 0;");
            html.append("var moveSpeed = 150; var lookSpeed = 0.005;");
            
            html.append("renderer.domElement.addEventListener('pointerdown', function(e) { if(e.clientX < window.innerWidth * 0.4) return; isLooking = true; lastTouchX = e.clientX; lastTouchY = e.clientY; });");
            html.append("renderer.domElement.addEventListener('pointermove', function(e) { if(!isLooking) return; var dx = e.clientX - lastTouchX; var dy = e.clientY - lastTouchY; euler.setFromQuaternion(camera.quaternion); euler.y -= dx * lookSpeed; euler.x -= dy * lookSpeed; euler.x = Math.max(-Math.PI/2, Math.min(Math.PI/2, euler.x)); camera.quaternion.setFromEuler(euler); lastTouchX = e.clientX; lastTouchY = e.clientY; });");
            html.append("renderer.domElement.addEventListener('pointerup', function() { isLooking = false; });");
            html.append("renderer.domElement.addEventListener('pointerleave', function() { isLooking = false; });");

            html.append("var joyZone = document.createElement('div'); joyZone.id = 'joyZone'; joyZone.style.cssText = 'position:absolute; bottom:40px; left:40px; width:150px; height:150px; z-index:999; border-radius:50%; background:rgba(255,255,255,0.05); touch-action:none;'; document.body.appendChild(joyZone);");
            html.append("if(typeof nipplejs !== 'undefined') { var manager = nipplejs.create({ zone: joyZone, mode: 'static', position: {left:'50%', top:'50%'}, color: '#0078D7' }); var moveVec = new THREE.Vector3(0,0,0); manager.on('move', function(evt, data) { var f = Math.min(data.force, 2.0); moveVec.x = Math.cos(data.angle.radian)*f; moveVec.z = -Math.sin(data.angle.radian)*f; }); manager.on('end', function() { moveVec.set(0,0,0); }); }");

            // 万能地图加载器
            html.append("var gltfLoader = new THREE.GLTFLoader();");
            html.append("if(typeof THREE.DRACOLoader !== 'undefined') { var dracoLoader = new THREE.DRACOLoader(); dracoLoader.setDecoderPath('js/'); dracoLoader.setDecoderConfig({type: 'js'}); dracoLoader.setWorkerLimit(0); gltfLoader.setDRACOLoader(dracoLoader); }");
            html.append("var objLoader = typeof THREE.OBJLoader !== 'undefined' ? new THREE.OBJLoader() : null;");
            html.append("var fbxLoader = typeof THREE.FBXLoader !== 'undefined' ? new THREE.FBXLoader() : null;");
            html.append("var tdsLoader = typeof THREE.TDSLoader !== 'undefined' ? new THREE.TDSLoader() : null;");
            html.append("var daeLoader = typeof THREE.ColladaLoader !== 'undefined' ? new THREE.ColladaLoader() : null;");
            
            html.append("window.loadExternalModel = function(url) {");
            html.append("    var ext = url.split('.').pop().toLowerCase(); var basePath = url.substring(0, url.lastIndexOf('/') + 1); if(tdsLoader) tdsLoader.setResourcePath(basePath);"); 
            html.append("    var onLoaded = function(obj) {");
            html.append("        try {");
            html.append("            var toRemove = []; scene.traverse(function(c){ if(c.userData.isMap) toRemove.push(c); }); toRemove.forEach(function(c){ scene.remove(c); });"); // 自动清理上一张地图
            html.append("            var model = obj.scene || obj;");
            html.append("            if(model.isBufferGeometry) { var mat = new THREE.MeshStandardMaterial({color:0xcccccc, side:THREE.DoubleSide}); model = new THREE.Mesh(model, mat); }");
            html.append("            model.userData.isMap = true;");
            html.append("            model.traverse(function(n){ if(n.isMesh) { n.castShadow = true; n.receiveShadow = true; if(n.material) { var mats = Array.isArray(n.material) ? n.material : [n.material]; mats.forEach(function(m){ m.side = THREE.DoubleSide; m.needsUpdate=true; }); } } });");
            html.append("            var anims = obj.animations || []; if(anims.length > 0) { var mixer = new THREE.AnimationMixer(model); window.mixers.push(mixer); anims.forEach(function(a){ mixer.clipAction(a).play(); }); }");
            html.append("            scene.add(model);");
            html.append("        } catch(ex) { alert('" + L("渲染错误: ") + "' + ex.message); }");
            html.append("    };");
            html.append("    try {");
            html.append("        if((ext==='gltf'||ext==='glb') && gltfLoader) gltfLoader.load(url, onLoaded, null, function(err){ alert('" + L("加载失败: ") + "'+err); });");
            html.append("        else if(ext==='obj' && objLoader) objLoader.load(url, onLoaded);");
            html.append("        else if(ext==='fbx' && fbxLoader) fbxLoader.load(url, onLoaded, null, function(err){ alert('" + L("FBX错误: ") + "'+err); });");
            html.append("        else if(ext==='3ds' && tdsLoader) tdsLoader.load(url, onLoaded);");
            html.append("        else if(ext==='dae' && daeLoader) daeLoader.load(url, function(c){ onLoaded(c.scene); });");
            html.append("        else alert('" + L("不支持的格式: ") + "' + ext);");
            html.append("    } catch(e) { alert('" + L("加载异常: ") + "' + e.message); }");
            html.append("};");

            html.append("function animate() { requestAnimationFrame(animate); var dt = clock.getDelta(); window.mixers.forEach(function(m){m.update(dt);}); if(typeof moveVec !== 'undefined' && moveVec.lengthSq() > 0) { camera.translateX(moveVec.x * moveSpeed * dt); camera.translateZ(moveVec.z * moveSpeed * dt); } renderer.render(scene, camera); } animate();");
            html.append("window.addEventListener('resize', function(){ if(typeof camera !== 'undefined'){ camera.aspect = window.innerWidth / window.innerHeight; camera.updateProjectionMatrix(); renderer.setSize(window.innerWidth, window.innerHeight); }});");
            
            html.append("</script></body></html>");
           
            modelWebView.loadDataWithBaseURL("file:///android_asset/", html.toString(), "text/html", "utf-8", null);
        });

        updateViewState[0].run(); refreshLayerListUI[0].run(); return root;
    }

    // 🌉 Go 引擎底层抽象桥接

    // ======================================================================================
    public static class GoEngineBridge {
        public static class SffInfo { public String name; public String filePath; public Bitmap preview; public String version; }
        public static class SffFrame { public int group; public int item; public int width; public int height; public int x; public int y; } 
        public static class SndNode { public int group; public int item; }

        public static List<SffInfo> scanSff(String targetPath) {
            List<SffInfo> list = new ArrayList<>();
            try {
                String jsonStr = Api.scanSff(targetPath); 
                if(jsonStr == null || jsonStr.trim().isEmpty() || jsonStr.equals("[]")) return list;
                org.json.JSONArray array = new org.json.JSONArray(jsonStr);
                for (int i = 0; i < array.length(); i++) {
                    org.json.JSONObject obj = array.getJSONObject(i);
                    SffInfo info = new SffInfo();
                    info.name = obj.getString("name"); info.filePath = obj.getString("filePath"); info.version = obj.getString("version");
                    list.add(info);
                }
            } catch (Exception e) {} return list;
        }

        public static List<SffFrame> getAllFrames(String sffPath) {
            List<SffFrame> list = new ArrayList<>();
            try {
                String jsonStr = Api.getAllFrames(sffPath);
                if(jsonStr == null || jsonStr.trim().isEmpty() || jsonStr.equals("[]")) return list;
                org.json.JSONArray array = new org.json.JSONArray(jsonStr);
                for (int i = 0; i < array.length(); i++) {
                    org.json.JSONObject obj = array.getJSONObject(i);
                    SffFrame f = new SffFrame();
                    f.group = obj.getInt("group"); f.item = obj.getInt("item"); f.width = obj.getInt("width"); f.height = obj.getInt("height");
                    f.x = obj.optInt("x", 0); f.y = obj.optInt("y", 0);
                    list.add(f);
                }
            } catch (Exception e) {} return list;
        }

        public static List<SndNode> scanSnd(String targetPath) {
            List<SndNode> list = new ArrayList<>();
            try {
                String jsonStr = Api.scanSnd(targetPath);
                if(jsonStr == null || jsonStr.trim().isEmpty() || jsonStr.equals("[]")) return list;
                org.json.JSONArray array = new org.json.JSONArray(jsonStr);
                for (int i = 0; i < array.length(); i++) {
                    org.json.JSONObject obj = array.getJSONObject(i);
                    SndNode n = new SndNode(); n.group = obj.getInt("group"); n.item = obj.getInt("item"); list.add(n);
                }
            } catch (Exception e) {} return list;
        }
    }
    
    private void copyFileToSandbox(File src, File dst) throws Exception {
        try (InputStream in = new FileInputStream(src); FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192]; int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        }
    }

    
// ================= 【机制注入】模型贴图与极致解析系统 =================
    // 1. 极致优化：FBX模型解析预处理器 (解决ASCII格式FBX加载报错/黑模问题)
    public String processModelDataSafely(String filePath) {
        try {
            java.io.File file = new java.io.File(filePath);
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            byte[] header = new byte[20];
            fis.read(header);
            fis.close();
            String headerStr = new String(header);
            
            // 智能嗅探：识别 ASCII FBX 并安全编码为纯文本流，强制底层解析器不按二进制读取，避免崩溃
            if (filePath.toLowerCase().endsWith(".fbx") && headerStr.startsWith("; FBX")) {
                return "data:text/plain;base64," + android.util.Base64.encodeToString(readFully(file), android.util.Base64.NO_WRAP);
            }
            // 正常二进制模型(GLB/Binary FBX等)走原生加载，确保全格式兼容
            return "data:application/octet-stream;base64," + android.util.Base64.encodeToString(readFully(file), android.util.Base64.NO_WRAP);
        } catch (Exception e) { return ""; }
    }
    
    private byte[] readFully(java.io.File file) throws Exception {
        java.io.FileInputStream in = new java.io.FileInputStream(file);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; // 扩充缓冲池提升大模型读取效率
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        in.close(); return out.toByteArray();
    }

    // 2. 贴图系统：指定文件夹 -> 自动识别图片 -> 缩略图网格预览 -> 点击自由应用
    public void showCustomTextureGallery(Context context, WebView webView, java.io.File targetFolder) {
        Dialog dialog = new Dialog(context);
        dialog.setTitle(L("选择模型贴图 (点击预览图即可应用)"));
        
        android.widget.LinearLayout rootLayout = new android.widget.LinearLayout(context);
        rootLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        rootLayout.setPadding(30, 30, 30, 30);
        
        android.widget.ScrollView scrollView = new android.widget.ScrollView(context);
        android.widget.GridLayout imageGrid = new android.widget.GridLayout(context);
        imageGrid.setColumnCount(3); // 启用网格预览，一行3图
        scrollView.addView(imageGrid);
        
        if(targetFolder != null && targetFolder.exists() && targetFolder.isDirectory()) {
            for(java.io.File img : targetFolder.listFiles()) {
                String name = img.getName().toLowerCase();
                // 仅安全识别图片，绝不允许出现直接加载覆盖的情况
                if(name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                    android.widget.ImageView previewNode = new android.widget.ImageView(context);
                    
                    // 缩放降采样生成预览图，防止多张高清贴图导致内存溢出(OOM)闪退
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 2; 
                    previewNode.setImageBitmap(BitmapFactory.decodeFile(img.getAbsolutePath(), options));
                    
                    android.widget.GridLayout.LayoutParams params = new android.widget.GridLayout.LayoutParams();
                    params.width = 240; params.height = 240; params.setMargins(15, 15, 15, 15);
                    previewNode.setLayoutParams(params);
                    previewNode.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                    
                    // 核心要求保障：必须在自由点击预览图后，才执行贴图转换动作
                    previewNode.setOnClickListener(iv -> {
                        try {
                            String b64 = "data:image/png;base64," + android.util.Base64.encodeToString(readFully(img), android.util.Base64.NO_WRAP);
                            // 跨端注入：驱动前端执行贴图覆盖逻辑
                            webView.evaluateJavascript("javascript:if(typeof applyCustomTexture === 'function') applyCustomTexture('" + b64 + "');", null);
                            android.widget.Toast.makeText(context, L("贴图已应用: ") + img.getName(), android.widget.Toast.LENGTH_SHORT).show();
                            dialog.dismiss(); // 应用完毕后自动关闭面板
                        } catch(Exception e){}
                    });
                    imageGrid.addView(previewNode);
                }
            }
        } else {
            android.widget.Toast.makeText(context, L("文件夹内未识别到图片"), android.widget.Toast.LENGTH_SHORT).show();
        }
        rootLayout.addView(scrollView);
        dialog.setContentView(rootLayout);
        dialog.show();
    }

    // 3. UI排版修改：动态注入并缩小贴图按钮体积
    public void injectTextureButton(Context context, ViewGroup parentLayout, WebView webView, java.io.File selectedWorkDir) {
        android.widget.Button btnTexture = new android.widget.Button(context);
        btnTexture.setText(L("贴图"));
        btnTexture.setTextSize(12f); // 缩减字体大小
        
        // 【核心修改】大幅缩小按钮宽度，为原有的操作面板腾出排版空间
        android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
            160, // 设定极小固定宽度缩小体积
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(10, 0, 10, 0);
        btnTexture.setLayoutParams(params);
        
        // 绑定手动选择的目录触发预览弹窗逻辑
        btnTexture.setOnClickListener(v -> {
            showCustomTextureGallery(context, webView, selectedWorkDir); 
        });
        
        parentLayout.addView(btnTexture, 0); // 将缩小后的贴图键插入布局首位
    }

    // ======================================================================================
    // 🔣 模块 6：万能乱码解字板 (支持 22 种全球主流编码矩阵)
    // ======================================================================================
    private View buildDecipherBoardContent() {
        LinearLayout root = new LinearLayout(getContext()); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#1E1E1E"));
        root.setPadding((int)(15*density), (int)(15*density), (int)(15*density), (int)(15*density));

        Button btnPickFile = createButton(L("📂 选择乱码文本文件 (.def/.cns/.cmd/.st 等)"), "#0078D7");
        root.addView(btnPickFile);

        ScrollView scroll = new ScrollView(getContext()); LinearLayout contentArea = new LinearLayout(getContext()); contentArea.setOrientation(LinearLayout.VERTICAL); scroll.addView(contentArea);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));

        btnPickFile.setOnClickListener(v -> {
            showWin10FilePicker(L("选择需要修复内容的文本文件"), 12, null, null, file -> {
                if(file.isDirectory()) { Toast.makeText(getContext(), L("❌ 请选择单个文件！"), Toast.LENGTH_SHORT).show(); return; }
                contentArea.removeAllViews();
                contentArea.addView(createSubTitle(L("🎯 目标文件: ") + file.getName()));
                
                final byte[][] rawBytes = {null};
                try {
                    FileInputStream fis = new FileInputStream(file);
                    rawBytes[0] = new byte[(int)file.length()]; fis.read(rawBytes[0]); fis.close();
                } catch (Exception e) { Toast.makeText(getContext(), L("文件读取失败!"), Toast.LENGTH_SHORT).show(); return; }

                // --- 22 种全球编码矩阵 (按热度排序) ---
                String[] charsets = {
                    L("UTF-8 (国际通用标准)"), L("Shift_JIS (日本作者首选)"), L("GBK (简体中文标准)"), L("Big5 (繁体中文-港台)"), L("Windows-1252 (西欧/英语)"),
                    L("EUC-KR (韩语作者)"), L("Windows-1251 (俄语/东欧)"), L("UTF-16LE (Unicode双字节)"), L("UTF-16BE (Unicode大端)"), L("EUC-JP (旧版日文系统)"),
                    L("ISO-8859-1 (西欧拉丁语)"), L("ISO-8859-2 (中欧语系)"), L("ISO-8859-5 (西里尔语系)"), L("Windows-1250 (中欧/波兰)"), L("Windows-1253 (希腊语)"),
                    L("Windows-1254 (土耳其语)"), L("Windows-1255 (希伯来语)"), L("Windows-1256 (阿拉伯语)"), L("Windows-1257 (波罗的海语)"), L("Windows-1258 (越南语)"),
                    L("KOI8-R (俄语网络标准)"), L("GB18030 (超全中文兼容)")
                };
                String[] charsetsKeys = {
                    "UTF-8", "Shift_JIS", "GBK", "Big5", "Windows-1252", "EUC-KR", "Windows-1251", "UTF-16LE", "UTF-16BE", "EUC-JP",
                    "ISO-8859-1", "ISO-8859-2", "ISO-8859-5", "Windows-1250", "Windows-1253", "Windows-1254", "Windows-1255", "Windows-1256", "Windows-1257", "Windows-1258",
                    "KOI8-R", "GB18030"
                };
                String[] charsetsIntro = {
                    L("💡 现代软件标准：全球通用，Ikemen 原生支持。"),
                    L("💡 日本作者首选：解决 90% 日系人物包乱码问题。"),
                    L("💡 简体中文：国内早期作者或系统默认编码。"),
                    L("💡 繁体中文：港澳台地区作者制作素材常用。"),
                    L("💡 西欧语系：包含英法德意西等欧美作者常用。"),
                    L("💡 韩语：韩国作者制作素材时的默认编码。"),
                    L("💡 俄语/西里尔：俄罗斯、乌克兰等作者常用。"),
                    L("💡 Unicode：某些编辑器导出的特定双字节文本。"),
                    L("💡 Unicode(BE)：常见于某些旧版大型引擎配置。"),
                    L("💡 旧版日文：较老的日本 UNIX/Linux 系统常用。"),
                    L("💡 传统西欧：最早的拉丁字母标准。"),
                    L("💡 中欧语系：波兰、捷克、匈牙利语常用。"),
                    L("💡 斯拉夫语：保加利亚、白俄罗斯等语系。"),
                    L("💡 中欧 Windows：Windows 系统下的中欧编码。"),
                    L("💡 希腊语：修复希腊地区作者素材乱码。"),
                    L("💡 土耳其语：修复土耳其地区作者素材。"),
                    L("💡 希伯来语：中东地区特定作者常用。"),
                    L("💡 阿拉伯语：中东及北非地区作者常用。"),
                    L("💡 波罗的海：爱沙尼亚、拉脱维亚地区常用。"),
                    L("💡 越南语：越南作者制作素材时的专用编码。"),
                    L("💡 旧版俄语：早期俄罗斯互联网常用标准。"),
                    L("💡 全能中文：覆盖所有少数民族字符的强力中文集。")
                };

                contentArea.addView(createSubTitle(L("⚙️ 请选择匹配的解码方案:")));
                Spinner charsetSpinner = new Spinner(getContext());
                ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, charsets);
                charsetSpinner.setAdapter(adapter); contentArea.addView(charsetSpinner);

                TextView txtIntro = new TextView(getContext()); txtIntro.setPadding((int)(10*density), (int)(10*density), (int)(10*density), (int)(10*density));
                txtIntro.setBackgroundColor(Color.parseColor("#2D2D30")); applyGlobalFontSettings(txtIntro, 0.85f, false); txtIntro.setTextColor(Color.parseColor("#FFD700"));
                contentArea.addView(txtIntro);

                EditText previewText = createInput("", ""); previewText.setGravity(Gravity.TOP | Gravity.LEFT);
                previewText.setMinimumHeight((int)(300*density)); contentArea.addView(previewText);

                charsetSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                        txtIntro.setText(charsetsIntro[pos]);
                        try {
                            int length = Math.min(rawBytes[0].length, 100 * 1024); // 预览 100KB
                            String decoded = new String(rawBytes[0], 0, length, charsetsKeys[pos]);
                            previewText.setText(decoded);
                        } catch (Exception e) { previewText.setText(L("❌ 该编码不匹配或无法解码")); }
                    }
                    @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
                });

                Button btnSave = createButton(L("💾 破解成功！另存为 UTF-8 无乱码文本"), "#4CAF50");
                LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2); sp.setMargins(0, (int)(15*density), 0, (int)(15*density));
                btnSave.setOnClickListener(saveBtn -> {
                    try {
                        String finalEncoding = charsetsKeys[charsetSpinner.getSelectedItemPosition()];
                        String fullDecoded = new String(rawBytes[0], finalEncoding);
                        File outFile = new File(file.getParent(), file.getName().replaceAll("\\.[^.]+$", "") + "_UTF8" + file.getName().substring(file.getName().lastIndexOf(".")));
                        FileOutputStream fos = new FileOutputStream(outFile); fos.write(fullDecoded.getBytes("UTF-8")); fos.close();
                        Toast.makeText(getContext(), L("✅ 另存成功！") + outFile.getName(), Toast.LENGTH_LONG).show();
                    } catch (Exception e) { Toast.makeText(getContext(), L("❌ 保存失败"), Toast.LENGTH_SHORT).show(); }
                });
                contentArea.addView(btnSave, sp);
            });
        });
        return root;
    }


    // ======================================================================================
    // 🗂️ 模块 7：DEF 扫描与 Select.def 生成器
    // ======================================================================================
    private View buildDefScannerContent() {
        LinearLayout root = new LinearLayout(getContext()); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#1E1E1E"));
        root.setPadding((int)(15*density), (int)(15*density), (int)(15*density), (int)(15*density));

        LinearLayout topBar = new LinearLayout(getContext()); topBar.setOrientation(LinearLayout.VERTICAL);
        Button btnScan = createButton(L("📂 选择根目录进行全盘扫描 (Chars/Stages)"), "#0078D7");
        TextView txtStatus = new TextView(getContext()); txtStatus.setText(L("  等待扫描...")); applyGlobalFontSettings(txtStatus, 0.92f, false); txtStatus.setTextColor(Color.WHITE); txtStatus.setPadding(0, (int)(6*density), 0, 0);
        topBar.addView(btnScan, new LinearLayout.LayoutParams(-1, -2)); topBar.addView(txtStatus); root.addView(topBar);

        ScrollView scroll = new ScrollView(getContext()); LinearLayout contentArea = new LinearLayout(getContext()); contentArea.setOrientation(LinearLayout.VERTICAL); scroll.addView(contentArea);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));

        btnScan.setOnClickListener(v -> {
            showWin10FilePicker(L("选择你要扫描的根目录"), 13, null, null, dir -> {
                if (dir == null || !dir.isDirectory()) { Toast.makeText(getContext(), L("❌ 请选择文件夹！"), Toast.LENGTH_SHORT).show(); return; }
                contentArea.removeAllViews(); txtStatus.setText(L("  🚀 正在深度扫描全文，请稍候..."));

                final List<String> stageResults = new ArrayList<>();
                final List<String> charNormalResults = new ArrayList<>();
                final List<String> charRiskResults = new ArrayList<>();

                new Thread(() -> {
                    List<File> allDefs = new ArrayList<>();
                    class DefScanner {
                        void scan(File targetDir) {
                            File[] fs = targetDir.listFiles(); if (fs == null) return;
                            for (File f : fs) { if (f.isDirectory() && !f.isHidden()) scan(f); else if (f.getName().toLowerCase().endsWith(".def")) allDefs.add(f); }
                        }
                    }
                    new DefScanner().scan(dir);

                    for (File defF : allDefs) {
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(defF), "UTF-8"))) {
                            String line; boolean isStage = false; boolean isChar = false;
                            while ((line = br.readLine()) != null) {
                                int commentIdx = line.indexOf(';');
                                if (commentIdx >= 0) line = line.substring(0, commentIdx);
                                String lower = line.toLowerCase().trim();
                                if (lower.isEmpty()) continue;
                                if (lower.startsWith("[stageinfo]") || lower.startsWith("[bgdef]")) { isStage = true; break; }
                                if (lower.startsWith("[files]") || lower.matches("^cmd\\s*=.*") || lower.matches("^cns\\s*=.*")) { isChar = true; }
                            }

                            File parentF = defF.getParentFile();
                            String folderName = (parentF != null) ? parentF.getName() : "";
                            String rawName = defF.getName();
                            int dotIdx = rawName.lastIndexOf(".");
                            String defNameNoExt = (dotIdx > 0) ? rawName.substring(0, dotIdx) : rawName;

                            if (isStage) {
                                stageResults.add(folderName + "/" + rawName);
                            } else if (isChar) {
                                if (folderName.equalsIgnoreCase(defNameNoExt)) {
                                    charNormalResults.add(defNameNoExt);
                                } else {
                                    charRiskResults.add(folderName + "/" + rawName);
                                }
                            }
                        } catch (Exception ignored) {}
                    }

                    new Handler(Looper.getMainLooper()).post(() -> {
                        txtStatus.setText(L("  ✅ 扫描完毕！DEF: ") + allDefs.size() + L(" | 地图: ") + stageResults.size() + L(" | 正常角色: ") + charNormalResults.size() + L(" | 风险角色: ") + charRiskResults.size());

                        final StringBuilder finalExportText = new StringBuilder();
                        finalExportText.append("; ==================================\n");
                        finalExportText.append("; 🗺️ 地图列表 (Stages)\n");
                        finalExportText.append("; ==================================\n");
                        contentArea.addView(createTitle(L("🗺️ 地图列表 (Stages) - ") + stageResults.size()));
                        for (String s : stageResults) {
                            TextView tv = new TextView(getContext()); tv.setText(s); tv.setTextColor(Color.parseColor("#4CAF50")); applyGlobalFontSettings(tv, 0.9f, false); contentArea.addView(tv);
                            finalExportText.append(s).append("\n");
                        }

                        finalExportText.append("\n; ==================================\n");
                        finalExportText.append("; 🥷 角色列表 - 正常 (Characters)\n");
                        finalExportText.append("; ==================================\n");
                        contentArea.addView(createTitle(L("🥷 角色列表 (正常匹配) - ") + charNormalResults.size()));
                        for (String s : charNormalResults) {
                            TextView tv = new TextView(getContext()); tv.setText(s); tv.setTextColor(Color.parseColor("#0078D7")); applyGlobalFontSettings(tv, 0.9f, false); contentArea.addView(tv);
                            finalExportText.append(s).append("\n");
                        }

                        finalExportText.append("\n; ==================================\n");
                        finalExportText.append("; ⚠️ 角色列表 - 风险类 (文件夹与 DEF 不一致，已转为相对路径格式)\n");
                        finalExportText.append("; ==================================\n");
                        contentArea.addView(createTitle(L("⚠️ 角色列表 (风险类/完整路径) - ") + charRiskResults.size()));
                        for (String s : charRiskResults) {
                            TextView tv = new TextView(getContext()); tv.setText(s); tv.setTextColor(Color.parseColor("#FF9800")); applyGlobalFontSettings(tv, 0.9f, false); contentArea.addView(tv);
                            finalExportText.append(s).append("\n");
                        }

                        LinearLayout actionRow = new LinearLayout(getContext()); actionRow.setOrientation(LinearLayout.HORIZONTAL);
                        Button btnCopy = createButton(L("📋 一键复制结果"), "#0078D7");
                        btnCopy.setOnClickListener(cpBtn -> {
                            try {
                                android.content.ClipboardManager cm = (android.content.ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                                if (cm != null) {
                                    cm.setPrimaryClip(android.content.ClipData.newPlainText("select_def", finalExportText.toString()));
                                    Toast.makeText(getContext(), L("✅ 已复制到剪贴板，可直接粘贴到 select.def！"), Toast.LENGTH_SHORT).show();
                                }
                            } catch (Exception e) { Toast.makeText(getContext(), L("❌ 复制失败"), Toast.LENGTH_SHORT).show(); }
                        });

                        Button btnExport = createButton(L("💾 导出并保存为 txt 文件 (供 select.def 使用)"), "#FF9800");
                        btnExport.setOnClickListener(expBtn -> {
                            final Dialog d = new Dialog(getContext()); d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                            LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setBackgroundColor(Color.parseColor("#252526")); box.setPadding((int)(20*density),(int)(20*density),(int)(20*density),(int)(20*density));
                            GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#252526")); border.setStroke((int)(2*density), Color.parseColor("#0078D7")); border.setCornerRadius(15*density); box.setBackground(border);

                            box.addView(createSubTitle(L("自定义导出文件名 (默认保存在 Download 目录):")));
                            EditText nameInput = createInput("", "select_export.txt"); box.addView(nameInput);

                            Button bConfirm = createButton(L("✔️ 确认导出"), "#4CAF50");
                            bConfirm.setOnClickListener(confBtn -> {
                                try {
                                    File outDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                                    String fname = nameInput.getText().toString().trim();
                                    if (fname.isEmpty()) fname = "select_export.txt";
                                    File outFile = new File(outDir, fname);
                                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                                        fos.write(finalExportText.toString().getBytes("UTF-8"));
                                    }
                                    Toast.makeText(getContext(), L("✅ 成功导出到:\n") + outFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                                    d.dismiss();
                                } catch (Exception e) { Toast.makeText(getContext(), L("❌ 导出失败"), Toast.LENGTH_SHORT).show(); }
                            });
                            box.addView(bConfirm, new LinearLayout.LayoutParams(-1, -2)); d.setContentView(box); d.show();
                        });

                        LinearLayout.LayoutParams lpCopy = new LinearLayout.LayoutParams(0, -2, 1f); lpCopy.setMargins(0, (int)(20*density), (int)(8*density), (int)(20*density));
                        LinearLayout.LayoutParams lpExp = new LinearLayout.LayoutParams(0, -2, 1.5f); lpExp.setMargins(0, (int)(20*density), 0, (int)(20*density));
                        actionRow.addView(btnCopy, lpCopy);
                        actionRow.addView(btnExport, lpExp);
                        contentArea.addView(actionRow);
                    });
                }).start();
            });
        });
        return root;
    }

    // ======================================================================================
    // ⚔️ 模块 8：判定框工坊 (AIR 打击框 Clsn1 / 防御框 Clsn2 可视化绘制与代码生成)
    // ======================================================================================
    private static class ClsnBox {
        int type; // 1 = Clsn1 打击框 (红), 2 = Clsn2 防御框 (蓝)
        int x1, y1, x2, y2;
        int strokeId;
        ClsnBox(int type, int x1, int y1, int x2, int y2) {
            this(type, x1, y1, x2, y2, 0);
        }
        ClsnBox(int type, int x1, int y1, int x2, int y2, int strokeId) {
            this.type = type;
            this.x1 = Math.min(x1, x2);
            this.y1 = Math.min(y1, y2);
            this.x2 = Math.max(x1, x2);
            this.y2 = Math.max(y1, y2);
            this.strokeId = strokeId;
        }
    }

    // 将任意闭合多边形/手绘轮廓通过水平扫描线分解为合法的 MUGEN Clsn 矩形集合
    private List<ClsnBox> decomposePolygonToClsnBoxes(List<Point> poly, int boxType, int slices, int strokeId) {
        List<ClsnBox> out = new ArrayList<>();
        if (poly == null || poly.size() < 3) return out;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (Point p : poly) {
            if (p.y < minY) minY = p.y;
            if (p.y > maxY) maxY = p.y;
        }
        int totalH = maxY - minY;
        if (totalH < 2) return out;
        int bandCount = Math.max(2, Math.min(slices, totalH));
        double bandH = (double) totalH / bandCount;

        for (int b = 0; b < bandCount; b++) {
            int yTop = (int) Math.round(minY + b * bandH);
            int yBot = (int) Math.round(minY + (b + 1) * bandH);
            if (yBot <= yTop) continue;
            double sampleY = (yTop + yBot) * 0.5;

            List<Double> xs = new ArrayList<>();
            int n = poly.size();
            for (int i = 0; i < n; i++) {
                Point p1 = poly.get(i);
                Point p2 = poly.get((i + 1) % n);
                if ((p1.y <= sampleY && p2.y > sampleY) || (p2.y <= sampleY && p1.y > sampleY)) {
                    double t = (sampleY - p1.y) / (double)(p2.y - p1.y);
                    xs.add(p1.x + t * (p2.x - p1.x));
                }
            }
            java.util.Collections.sort(xs);
            for (int i = 0; i + 1 < xs.size(); i += 2) {
                int lx = (int) Math.round(xs.get(i));
                int rx = (int) Math.round(xs.get(i + 1));
                if (rx - lx >= 2) {
                    if (!out.isEmpty()) {
                        ClsnBox prev = out.get(out.size() - 1);
                        if (prev.y2 == yTop && Math.abs(prev.x1 - lx) <= 2 && Math.abs(prev.x2 - rx) <= 2) {
                            prev.x1 = Math.min(prev.x1, lx);
                            prev.x2 = Math.max(prev.x2, rx);
                            prev.y2 = yBot;
                            continue;
                        }
                    }
                    out.add(new ClsnBox(boxType, lx, yTop, rx, yBot, strokeId));
                }
            }
        }
        return out;
    }

    // 扫描透明 SFF 图像非透明像素，生成整图外接框或异形轮廓多段框
    private List<ClsnBox> detectSpriteBoundsToClsn(Bitmap bmp, GoEngineBridge.SffFrame frame, int boxType, boolean contourMode, int slices, int strokeId) {
        List<ClsnBox> out = new ArrayList<>();
        if (bmp == null || bmp.isRecycled() || frame == null) return out;
        int w = bmp.getWidth(), h = bmp.getHeight();
        if (w <= 0 || h <= 0) return out;
        int[] pixels = new int[w * h];
        bmp.getPixels(pixels, 0, w, 0, 0, w, h);

        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            int rowOff = y * w;
            for (int x = 0; x < w; x++) {
                if (Color.alpha(pixels[rowOff + x]) > 10) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < minX || maxY < minY) return out;

        if (!contourMode) {
            out.add(new ClsnBox(boxType, minX - frame.x, minY - frame.y, maxX + 1 - frame.x, maxY + 1 - frame.y, strokeId));
            return out;
        }

        int spanH = maxY - minY + 1;
        int bandCount = Math.max(2, Math.min(slices, spanH));
        double bandH = (double) spanH / bandCount;
        for (int b = 0; b < bandCount; b++) {
            int y0 = minY + (int) Math.floor(b * bandH);
            int y1 = minY + (int) Math.ceil((b + 1) * bandH) - 1;
            if (y1 > maxY) y1 = maxY;
            if (y1 < y0) continue;
            int bMinX = w, bMaxX = -1;
            for (int y = y0; y <= y1; y++) {
                int rowOff = y * w;
                for (int x = minX; x <= maxX; x++) {
                    if (Color.alpha(pixels[rowOff + x]) > 10) {
                        if (x < bMinX) bMinX = x;
                        if (x > bMaxX) bMaxX = x;
                    }
                }
            }
            if (bMaxX >= bMinX) {
                int mx1 = bMinX - frame.x;
                int my1 = y0 - frame.y;
                int mx2 = bMaxX + 1 - frame.x;
                int my2 = y1 + 1 - frame.y;
                if (!out.isEmpty()) {
                    ClsnBox prev = out.get(out.size() - 1);
                    if (prev.y2 >= my1 - 1 && Math.abs(prev.x1 - mx1) <= 3 && Math.abs(prev.x2 - mx2) <= 3) {
                        prev.x1 = Math.min(prev.x1, mx1);
                        prev.x2 = Math.max(prev.x2, mx2);
                        prev.y2 = my2;
                        continue;
                    }
                }
                out.add(new ClsnBox(boxType, mx1, my1, mx2, my2, strokeId));
            }
        }
        return out;
    }

    private View buildClsnEditorContent(String initSffPath, String initActPath) {
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#141820"));
        final Handler uiHandler = new Handler(Looper.getMainLooper());

        final String[] sffPathRef = { initSffPath != null ? initSffPath : "" };
        final String[] actPathRef = { initActPath != null ? initActPath : "" };
        final List<GoEngineBridge.SffFrame> allFrames = new ArrayList<>();
        final List<GoEngineBridge.SffFrame> groupFrames = new ArrayList<>();
        final int[] frameIdx = {0};
        final Bitmap[] curBmp = {null};
        final GoEngineBridge.SffFrame[] curFrame = {null};

        final java.util.Map<String, List<ClsnBox>> frameBoxesMap = new java.util.LinkedHashMap<>();
        // 工具模式: 0=移动缩放画布, 1=绘制打击框(Clsn1), 2=绘制防御框(Clsn2)
        final int[] editMode = {1};
        // 绘制形状: 0=矩形, 1=椭圆, 2=多边形点连, 3=手绘自动闭合, 4=点删单框
        final int[] shapeMode = {0};
        final int[] shapeSlices = {8};
        final int[] strokeSeq = {1};
        final boolean[] showClsn1 = {true};
        final boolean[] showClsn2 = {true};
        final float[] canvasScale = {2.0f};
        final float[] originX = {0f};
        final float[] originY = {0f};
        final boolean[] originInited = {false};

        final List<Point> activePolyPts = new ArrayList<>();
        final List<Point> freehandPts = new ArrayList<>();

        final TextView statusBar = new TextView(getContext());
        statusBar.setText(L("⚔️ 支持矩形/椭圆/多边形/手绘自动闭合，以及 SFF 透明像素自动识别防御框"));
        applyGlobalFontSettings(statusBar, 0.85f, false);
        statusBar.setTextColor(Color.parseColor("#7DD3FC"));
        statusBar.setPadding((int)(8*density), (int)(5*density), (int)(8*density), (int)(5*density));
        root.addView(statusBar);

        final View clsnCanvas = new View(getContext()) {
            private final Paint gridPaint = new Paint();
            private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint fill1 = new Paint();
            private final Paint stroke1 = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint fill2 = new Paint();
            private final Paint stroke2 = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint dashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint bmpPaint = new Paint();
            private boolean isDrawing = false;
            private int dragStartX, dragStartY, dragCurX, dragCurY;
            private float panStartX, panStartY, savedOriginX, savedOriginY;
            private float pinchDist = 0f, savedScale = 2f;
            private int pointerMode = 0;

            {
                gridPaint.setColor(Color.argb(32, 255, 255, 255));
                gridPaint.setStrokeWidth(1f);
                axisPaint.setColor(Color.argb(200, 255, 235, 59));
                axisPaint.setStrokeWidth(Math.max(1.5f, density));
                fill1.setColor(Color.argb(78, 244, 67, 54)); fill1.setStyle(Paint.Style.FILL);
                stroke1.setColor(Color.parseColor("#FF5252")); stroke1.setStyle(Paint.Style.STROKE); stroke1.setStrokeWidth(Math.max(2f, 1.5f * density));
                fill2.setColor(Color.argb(78, 33, 150, 243)); fill2.setStyle(Paint.Style.FILL);
                stroke2.setColor(Color.parseColor("#38BDF8")); stroke2.setStyle(Paint.Style.STROKE); stroke2.setStrokeWidth(Math.max(2f, 1.5f * density));
                dashPaint.setColor(Color.parseColor("#FDE047"));
                dashPaint.setStyle(Paint.Style.STROKE);
                dashPaint.setStrokeWidth(Math.max(2f, 1.5f * density));
                dashPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{8f * density, 6f * density}, 0));
                labelPaint.setColor(Color.WHITE); labelPaint.setTextSize(10f * density); labelPaint.setFakeBoldText(true);
                bmpPaint.setFilterBitmap(false);
            }

            private String currentKey() {
                if (curFrame[0] == null) return "";
                return curFrame[0].group + "," + curFrame[0].item;
            }

            private int screenToMugenX(float sx) {
                float sc = Math.max(0.1f, canvasScale[0]);
                return Math.round((sx - originX[0]) / sc);
            }

            private int screenToMugenY(float sy) {
                float sc = Math.max(0.1f, canvasScale[0]);
                return Math.round((sy - originY[0]) / sc);
            }

            private float mugenToScreenX(int mx) { return originX[0] + mx * Math.max(0.1f, canvasScale[0]); }
            private float mugenToScreenY(int my) { return originY[0] + my * Math.max(0.1f, canvasScale[0]); }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                canvas.drawColor(Color.parseColor("#161A23"));
                if (!originInited[0] && getWidth() > 0 && getHeight() > 0) {
                    originX[0] = getWidth() * 0.5f;
                    originY[0] = getHeight() * 0.72f;
                    originInited[0] = true;
                }
                float sc = Math.max(0.1f, canvasScale[0]);
                float step = 16f * sc;
                if (step >= 8f) {
                    for (float x = originX[0] % step; x < getWidth(); x += step) canvas.drawLine(x, 0, x, getHeight(), gridPaint);
                    for (float y = originY[0] % step; y < getHeight(); y += step) canvas.drawLine(0, y, getWidth(), y, gridPaint);
                }

                canvas.drawLine(0, originY[0], getWidth(), originY[0], axisPaint);
                canvas.drawLine(originX[0], 0, originX[0], getHeight(), axisPaint);

                if (curBmp[0] != null && !curBmp[0].isRecycled() && curFrame[0] != null) {
                    float left = originX[0] - curFrame[0].x * sc;
                    float top = originY[0] - curFrame[0].y * sc;
                    android.graphics.RectF dst = new android.graphics.RectF(left, top, left + curBmp[0].getWidth() * sc, top + curBmp[0].getHeight() * sc);
                    canvas.drawBitmap(curBmp[0], null, dst, bmpPaint);
                }

                String key = currentKey();
                List<ClsnBox> boxes = frameBoxesMap.get(key);
                if (boxes != null) {
                    int c1Idx = 0, c2Idx = 0;
                    for (ClsnBox b : boxes) {
                        if (b.type == 1 && !showClsn1[0]) continue;
                        if (b.type == 2 && !showClsn2[0]) continue;
                        float l = mugenToScreenX(b.x1), t = mugenToScreenY(b.y1);
                        float r = mugenToScreenX(b.x2), bt = mugenToScreenY(b.y2);
                        canvas.drawRect(l, t, r, bt, b.type == 1 ? fill1 : fill2);
                        canvas.drawRect(l, t, r, bt, b.type == 1 ? stroke1 : stroke2);
                        String tag = (b.type == 1) ? ("C1[" + (c1Idx++) + "]") : ("C2[" + (c2Idx++) + "]");
                        canvas.drawText(tag, l + 2f * density, t + 11f * density, labelPaint);
                    }
                }

                Paint activeFill = (editMode[0] == 1) ? fill1 : fill2;
                Paint activeStroke = (editMode[0] == 1) ? stroke1 : stroke2;

                // 矩形 / 椭圆 实时预览
                if (isDrawing && (shapeMode[0] == 0 || shapeMode[0] == 1)) {
                    int rx1 = Math.min(dragStartX, dragCurX), ry1 = Math.min(dragStartY, dragCurY);
                    int rx2 = Math.max(dragStartX, dragCurX), ry2 = Math.max(dragStartY, dragCurY);
                    float l = mugenToScreenX(rx1), t = mugenToScreenY(ry1);
                    float r = mugenToScreenX(rx2), bt = mugenToScreenY(ry2);
                    if (shapeMode[0] == 0) {
                        canvas.drawRect(l, t, r, bt, activeFill);
                        canvas.drawRect(l, t, r, bt, activeStroke);
                    } else {
                        android.graphics.RectF oval = new android.graphics.RectF(l, t, r, bt);
                        canvas.drawOval(oval, activeFill);
                        canvas.drawOval(oval, activeStroke);
                    }
                    canvas.drawText(rx1 + "," + ry1 + " ~ " + rx2 + "," + ry2, l + 4f * density, Math.max(14f * density, t - 4f * density), labelPaint);
                }

                // 多边形顶点预览 + 自动闭合虚线
                if (shapeMode[0] == 2 && !activePolyPts.isEmpty()) {
                    Path pPath = new Path();
                    for (int i = 0; i < activePolyPts.size(); i++) {
                        float sx = mugenToScreenX(activePolyPts.get(i).x);
                        float sy = mugenToScreenY(activePolyPts.get(i).y);
                        if (i == 0) pPath.moveTo(sx, sy); else pPath.lineTo(sx, sy);
                        canvas.drawCircle(sx, sy, 4.5f * density, i == 0 ? dashPaint : activeStroke);
                    }
                    canvas.drawPath(pPath, activeStroke);
                    if (activePolyPts.size() >= 2) {
                        Point first = activePolyPts.get(0);
                        Point last = activePolyPts.get(activePolyPts.size() - 1);
                        canvas.drawLine(mugenToScreenX(last.x), mugenToScreenY(last.y), mugenToScreenX(first.x), mugenToScreenY(first.y), dashPaint);
                    }
                }

                // 手绘路径预览 + 剩余区域自动闭合虚线
                if (isDrawing && shapeMode[0] == 3 && freehandPts.size() >= 2) {
                    Path fPath = new Path();
                    for (int i = 0; i < freehandPts.size(); i++) {
                        float sx = mugenToScreenX(freehandPts.get(i).x);
                        float sy = mugenToScreenY(freehandPts.get(i).y);
                        if (i == 0) fPath.moveTo(sx, sy); else fPath.lineTo(sx, sy);
                    }
                    canvas.drawPath(fPath, activeStroke);
                    Point first = freehandPts.get(0);
                    Point last = freehandPts.get(freehandPts.size() - 1);
                    canvas.drawLine(mugenToScreenX(last.x), mugenToScreenY(last.y), mugenToScreenX(first.x), mugenToScreenY(first.y), dashPaint);
                }
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                int action = event.getActionMasked();
                if (editMode[0] == 0) {
                    switch (action) {
                        case MotionEvent.ACTION_DOWN:
                            panStartX = event.getX(); panStartY = event.getY();
                            savedOriginX = originX[0]; savedOriginY = originY[0];
                            pointerMode = 1;
                            break;
                        case MotionEvent.ACTION_POINTER_DOWN:
                            if (event.getPointerCount() >= 2) {
                                pinchDist = (float) Math.hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1));
                                savedScale = canvasScale[0];
                                pointerMode = 2;
                            }
                            break;
                        case MotionEvent.ACTION_MOVE:
                            if (pointerMode == 1) {
                                originX[0] = savedOriginX + (event.getX() - panStartX);
                                originY[0] = savedOriginY + (event.getY() - panStartY);
                                invalidate();
                            } else if (pointerMode == 2 && event.getPointerCount() >= 2 && pinchDist > 10f) {
                                float nd = (float) Math.hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1));
                                canvasScale[0] = Math.max(0.3f, Math.min(10f, savedScale * (nd / pinchDist)));
                                invalidate();
                            }
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_POINTER_UP:
                        case MotionEvent.ACTION_CANCEL:
                            pointerMode = 0;
                            break;
                    }
                    return true;
                }

                if (curFrame[0] == null) return true;
                int mx = screenToMugenX(event.getX());
                int my = screenToMugenY(event.getY());

                // 点删单框模式
                if (shapeMode[0] == 4) {
                    if (action == MotionEvent.ACTION_DOWN) {
                        String key = currentKey();
                        List<ClsnBox> list = frameBoxesMap.get(key);
                        if (list != null) {
                            for (int i = list.size() - 1; i >= 0; i--) {
                                ClsnBox b = list.get(i);
                                if (mx >= b.x1 && mx <= b.x2 && my >= b.y1 && my <= b.y2) {
                                    list.remove(i);
                                    statusBar.setText(L("🎯 已删除点击的判定框"));
                                    invalidate();
                                    return true;
                                }
                            }
                        }
                    }
                    return true;
                }

                // 多边形点连模式
                if (shapeMode[0] == 2) {
                    if (action == MotionEvent.ACTION_DOWN) {
                        if (activePolyPts.size() >= 3) {
                            Point p0 = activePolyPts.get(0);
                            float distPx = (float) Math.hypot(mugenToScreenX(p0.x) - event.getX(), mugenToScreenY(p0.y) - event.getY());
                            if (distPx <= 18f * density) {
                                int sid = strokeSeq[0]++;
                                List<ClsnBox> generated = decomposePolygonToClsnBoxes(activePolyPts, editMode[0], shapeSlices[0], sid);
                                activePolyPts.clear();
                                if (!generated.isEmpty()) {
                                    String key = currentKey();
                                    List<ClsnBox> list = frameBoxesMap.get(key);
                                    if (list == null) { list = new ArrayList<>(); frameBoxesMap.put(key, list); }
                                    list.addAll(generated);
                                    statusBar.setText((editMode[0] == 1 ? L("🟥 多边形打击框已闭合，生成 ") : L("🟦 多边形防御框已闭合，生成 ")) + generated.size() + L(" 个分段框"));
                                }
                                invalidate();
                                return true;
                            }
                        }
                        activePolyPts.add(new Point(mx, my));
                        statusBar.setText(L("🔷 多边形已点选 ") + activePolyPts.size() + L(" 个顶点 (点回起点或按【✅ 闭合】生成)"));
                        invalidate();
                    }
                    return true;
                }

                // 手绘自动闭合模式
                if (shapeMode[0] == 3) {
                    switch (action) {
                        case MotionEvent.ACTION_DOWN:
                            freehandPts.clear();
                            freehandPts.add(new Point(mx, my));
                            isDrawing = true;
                            invalidate();
                            break;
                        case MotionEvent.ACTION_MOVE:
                            if (isDrawing && !freehandPts.isEmpty()) {
                                Point last = freehandPts.get(freehandPts.size() - 1);
                                if (Math.abs(mx - last.x) + Math.abs(my - last.y) >= 2) {
                                    freehandPts.add(new Point(mx, my));
                                    invalidate();
                                }
                            }
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            if (isDrawing) {
                                isDrawing = false;
                                if (freehandPts.size() >= 3) {
                                    int sid = strokeSeq[0]++;
                                    List<ClsnBox> generated = decomposePolygonToClsnBoxes(freehandPts, editMode[0], shapeSlices[0], sid);
                                    if (!generated.isEmpty()) {
                                        String key = currentKey();
                                        List<ClsnBox> list = frameBoxesMap.get(key);
                                        if (list == null) { list = new ArrayList<>(); frameBoxesMap.put(key, list); }
                                        list.addAll(generated);
                                        statusBar.setText((editMode[0] == 1 ? L("✏️ 手绘打击区已自动闭合，生成 ") : L("✏️ 手绘防御区已自动闭合，生成 ")) + generated.size() + L(" 个分段框"));
                                    }
                                }
                                freehandPts.clear();
                                invalidate();
                            }
                            break;
                    }
                    return true;
                }

                // 矩形 (0) 与 椭圆 (1) 拖拽模式
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        dragStartX = mx; dragStartY = my;
                        dragCurX = mx; dragCurY = my;
                        isDrawing = true;
                        invalidate();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (isDrawing) {
                            dragCurX = mx; dragCurY = my;
                            invalidate();
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (isDrawing) {
                            isDrawing = false;
                            if (Math.abs(dragCurX - dragStartX) >= 2 && Math.abs(dragCurY - dragStartY) >= 2) {
                                String key = currentKey();
                                List<ClsnBox> list = frameBoxesMap.get(key);
                                if (list == null) { list = new ArrayList<>(); frameBoxesMap.put(key, list); }
                                int sid = strokeSeq[0]++;
                                if (shapeMode[0] == 0) {
                                    ClsnBox nb = new ClsnBox(editMode[0], dragStartX, dragStartY, dragCurX, dragCurY, sid);
                                    list.add(nb);
                                    statusBar.setText((nb.type == 1 ? L("🟥 已添加打击框 Clsn1: ") : L("🟦 已添加防御框 Clsn2: ")) + nb.x1 + ", " + nb.y1 + ", " + nb.x2 + ", " + nb.y2);
                                } else {
                                    int rx1 = Math.min(dragStartX, dragCurX), ry1 = Math.min(dragStartY, dragCurY);
                                    int rx2 = Math.max(dragStartX, dragCurX), ry2 = Math.max(dragStartY, dragCurY);
                                    double cx = (rx1 + rx2) * 0.5, cy = (ry1 + ry2) * 0.5;
                                    double a = Math.max(1.0, (rx2 - rx1) * 0.5), b = Math.max(1.0, (ry2 - ry1) * 0.5);
                                    List<Point> ovalPoly = new ArrayList<>();
                                    int segs = 28;
                                    for (int i = 0; i < segs; i++) {
                                        double ang = 2.0 * Math.PI * i / segs;
                                        ovalPoly.add(new Point((int) Math.round(cx + a * Math.cos(ang)), (int) Math.round(cy + b * Math.sin(ang))));
                                    }
                                    List<ClsnBox> generated = decomposePolygonToClsnBoxes(ovalPoly, editMode[0], shapeSlices[0], sid);
                                    list.addAll(generated);
                                    statusBar.setText((editMode[0] == 1 ? L("⭕ 椭圆打击区已生成 ") : L("⭕ 椭圆防御区已生成 ")) + generated.size() + L(" 个分段框"));
                                }
                            }
                            invalidate();
                        }
                        break;
                }
                return true;
            }
        };
        clsnCanvas.setTag("layout_preview");
        LinearLayout.LayoutParams canvasLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        canvasLp.setMargins((int)(6*density), 0, (int)(6*density), (int)(6*density));
        root.addView(clsnCanvas, canvasLp);

        Runnable renderCurrentFrame = () -> {
            activePolyPts.clear();
            freehandPts.clear();
            if (groupFrames.isEmpty()) {
                curFrame[0] = null; curBmp[0] = null;
                clsnCanvas.invalidate();
                return;
            }
            if (frameIdx[0] < 0) frameIdx[0] = groupFrames.size() - 1;
            if (frameIdx[0] >= groupFrames.size()) frameIdx[0] = 0;
            final GoEngineBridge.SffFrame tf = groupFrames.get(frameIdx[0]);
            new Thread(() -> {
                try {
                    byte[] data = Api.decodeSffFrame(sffPathRef[0], tf.group, tf.item, actPathRef[0]);
                    final Bitmap bmp = (data != null && data.length > 0) ? BitmapFactory.decodeByteArray(data, 0, data.length) : null;
                    uiHandler.post(() -> {
                        curFrame[0] = tf;
                        curBmp[0] = bmp;
                        String key = tf.group + "," + tf.item;
                        List<ClsnBox> list = frameBoxesMap.get(key);
                        int c1 = 0, c2 = 0;
                        if (list != null) { for (ClsnBox b : list) { if (b.type == 1) c1++; else c2++; } }
                        statusBar.setText(L("帧: ") + (frameIdx[0] + 1) + "/" + groupFrames.size() + " | [" + tf.group + "," + tf.item + "] | " + L("打击框:") + c1 + " | " + L("防御框:") + c2);
                        clsnCanvas.invalidate();
                    });
                } catch (Throwable ignored) {}
            }).start();
        };

        Runnable loadSffAction = () -> {
            if (sffPathRef[0].isEmpty()) return;
            statusBar.setText(L("⏳ 正在加载 SFF 帧序列..."));
            new Thread(() -> {
                List<GoEngineBridge.SffFrame> loaded = GoEngineBridge.getAllFrames(sffPathRef[0]);
                uiHandler.post(() -> {
                    allFrames.clear(); groupFrames.clear();
                    if (loaded != null) { allFrames.addAll(loaded); groupFrames.addAll(loaded); }
                    frameIdx[0] = 0;
                    if (groupFrames.isEmpty()) statusBar.setText(L("❌ 未能从 SFF 读取到有效帧"));
                    else renderCurrentFrame.run();
                });
            }).start();
        };

        HorizontalScrollView barScroll = new HorizontalScrollView(getContext()); barScroll.setHorizontalScrollBarEnabled(false);
        barScroll.setTag("layout_toolbar");
        LinearLayout bar = new LinearLayout(getContext()); bar.setOrientation(LinearLayout.HORIZONTAL); bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding((int)(6*density), (int)(4*density), (int)(6*density), (int)(8*density));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2); bp.setMargins((int)(4*density), 0, (int)(4*density), 0);

        Button btnLoadSff = createButton(L("📂 加载 SFF"), "#0078D7");
        Button btnLoadAct = createButton(L("🎨 色表"), "#9C27B0");
        Button btnLoadAir = createButton(L("📄 导入 AIR"), "#5C6BC0");
        Button btnModePan = createButton(L("🖐️ 移动画布"), "#334155");
        Button btnModeAtk = createButton(L("🟥 打击框(Clsn1)"), "#D32F2F");
        Button btnModeDef = createButton(L("🟦 防御框(Clsn2)"), "#1976D2");
        Button btnShapeMode = createButton(L("⬜ 形状: 矩形"), "#0F766E");
        Button btnClosePoly = createButton(L("✅ 闭合多边形"), "#059669");
        Button btnAutoDetect = createButton(L("🪄 识别图片边界"), "#0284C7");
        Button btnPrecision = createButton(L("⚙️ 异形精度: 8"), "#475569");
        Button btnUndo = createButton(L("↩️ 撤销"), "#455A64");
        Button btnClear = createButton(L("🗑️ 清空帧"), "#616161");
        Button btnPrev = createButton(L("⏪ 上一帧"), "#334155");
        Button btnNext = createButton(L("⏭️ 下一帧"), "#334155");
        Button btnGroup = createButton(L("📁 选组"), "#334155");
        Button btnToggleC1 = createButton(L("👁️ 打击:开"), "#B71C1C");
        Button btnToggleC2 = createButton(L("👁️ 防御:开"), "#0D47A1");
        Button btnExportAir = createButton(L("📋 生成/导出 AIR"), "#2E7D32");

        btnClosePoly.setVisibility(View.GONE);

        Runnable updateModeHighlight = () -> {
            btnModePan.setBackgroundColor(editMode[0] == 0 ? Color.parseColor("#0284C7") : Color.parseColor("#334155"));
            btnModeAtk.setBackgroundColor(editMode[0] == 1 ? Color.parseColor("#EF4444") : Color.parseColor("#5D2020"));
            btnModeDef.setBackgroundColor(editMode[0] == 2 ? Color.parseColor("#0EA5E9") : Color.parseColor("#1A3A5C"));
            btnClosePoly.setVisibility((editMode[0] != 0 && shapeMode[0] == 2) ? View.VISIBLE : View.GONE);
        };
        updateModeHighlight.run();

        btnModePan.setOnClickListener(v -> { editMode[0] = 0; updateModeHighlight.run(); });
        btnModeAtk.setOnClickListener(v -> { editMode[0] = 1; updateModeHighlight.run(); });
        btnModeDef.setOnClickListener(v -> { editMode[0] = 2; updateModeHighlight.run(); });

        // 5 种绘制方式选择：矩形 / 椭圆 / 多边形 / 手绘自动闭合 / 点删单框
        btnShapeMode.setOnClickListener(v -> {
            final Dialog sd = new Dialog(getContext());
            if (sd.getWindow() != null) sd.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            LinearLayout sl = new LinearLayout(getContext()); sl.setOrientation(LinearLayout.VERTICAL); sl.setPadding((int)(16*density), (int)(16*density), (int)(16*density), (int)(16*density));
            GradientDrawable sbg = new GradientDrawable(); sbg.setColor(Color.parseColor("#F51E2430")); sbg.setCornerRadius(12f * density); sbg.setStroke((int)(1.5f * density), Color.parseColor("#38BDF8")); sl.setBackground(sbg);
            sl.addView(createSubTitle(L("✏️ 选择判定框绘制方式 (攻防通用)")));
            final String[] names = new String[] {
                L("⬜ 矩形拖拽 (标准)"),
                L("⭕ 椭圆/圆形 (异形拟合)"),
                L("🔷 多边形点连 (点选顶点后闭合)"),
                L("✏️ 手绘自由圈选 (松手自动连接剩余区域)"),
                L("🎯 点击删除单个框")
            };
            final String[] shortLabels = new String[] {
                L("⬜ 形状: 矩形"), L("⭕ 形状: 椭圆"), L("🔷 形状: 多边形"), L("✏️ 形状: 手绘闭合"), L("🎯 模式: 点删框")
            };
            for (int i = 0; i < names.length; i++) {
                final int m = i;
                Button mb = createButton(names[m], shapeMode[0] == m ? "#0284C7" : "#2D3548");
                LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams((int)(280*density), -2);
                mlp.setMargins(0, (int)(6*density), 0, 0);
                mb.setOnClickListener(mv -> {
                    shapeMode[0] = m;
                    activePolyPts.clear();
                    freehandPts.clear();
                    btnShapeMode.setText(shortLabels[m]);
                    if (editMode[0] == 0) editMode[0] = 2;
                    updateModeHighlight.run();
                    clsnCanvas.invalidate();
                    sd.dismiss();
                });
                sl.addView(mb, mlp);
            }
            sd.setContentView(sl); sd.show();
        });

        btnClosePoly.setOnClickListener(v -> {
            if (curFrame[0] == null || activePolyPts.size() < 3) {
                Toast.makeText(getContext(), L("⚠️ 请至少在画布上点选 3 个顶点"), Toast.LENGTH_SHORT).show();
                return;
            }
            int sid = strokeSeq[0]++;
            int bType = (editMode[0] == 1) ? 1 : 2;
            List<ClsnBox> generated = decomposePolygonToClsnBoxes(activePolyPts, bType, shapeSlices[0], sid);
            activePolyPts.clear();
            if (!generated.isEmpty()) {
                String key = curFrame[0].group + "," + curFrame[0].item;
                List<ClsnBox> list = frameBoxesMap.get(key);
                if (list == null) { list = new ArrayList<>(); frameBoxesMap.put(key, list); }
                list.addAll(generated);
                statusBar.setText((bType == 1 ? L("🟥 多边形打击框已闭合，生成 ") : L("🟦 多边形防御框已闭合，生成 ")) + generated.size() + L(" 个分段框"));
            }
            clsnCanvas.invalidate();
        });

        // 🪄 自动识别透明 SFF 图片像素边界（支持整图外接框 + 异形轮廓贴合）
        btnAutoDetect.setOnClickListener(v -> {
            if (curFrame[0] == null || curBmp[0] == null || curBmp[0].isRecycled()) {
                Toast.makeText(getContext(), L("⚠️ 请先加载 SFF 角色帧"), Toast.LENGTH_SHORT).show();
                return;
            }
            final Dialog ad = new Dialog(getContext());
            if (ad.getWindow() != null) ad.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            LinearLayout al = new LinearLayout(getContext()); al.setOrientation(LinearLayout.VERTICAL); al.setPadding((int)(16*density), (int)(16*density), (int)(16*density), (int)(16*density));
            GradientDrawable abg = new GradientDrawable(); abg.setColor(Color.parseColor("#F51E2430")); abg.setCornerRadius(12f * density); abg.setStroke((int)(1.5f * density), Color.parseColor("#38BDF8")); al.setBackground(abg);
            al.addView(createSubTitle(L("🪄 自动识别 SFF 非透明像素边界")));
            TextView tip = new TextView(getContext());
            tip.setText(L("自动跳过透明背景区域，精准贴合人物实际像素轮廓"));
            applyGlobalFontSettings(tip, 0.85f, false); tip.setTextColor(Color.parseColor("#94A3B8"));
            al.addView(tip);

            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams((int)(290*density), -2);
            alp.setMargins(0, (int)(8*density), 0, 0);

            Button bFullDef = createButton(L("🟦 整图外接防御框 (1个精准框)"), "#0284C7");
            Button bContourDef = createButton(L("🧬 异形轮廓贴合防御框 (多段贴边)"), "#0F766E");
            Button bFullAtk = createButton(L("🟥 整图外接打击框 (特效/飞行道具)"), "#DC2626");
            Button bContourAtk = createButton(L("🔥 异形轮廓贴合打击框 (多段贴边)"), "#EA580C");

            View.OnClickListener runDetect = dv -> {
                int targetType = (dv == bFullAtk || dv == bContourAtk) ? 1 : 2;
                boolean contour = (dv == bContourDef || dv == bContourAtk);
                int sid = strokeSeq[0]++;
                List<ClsnBox> detected = detectSpriteBoundsToClsn(curBmp[0], curFrame[0], targetType, contour, shapeSlices[0], sid);
                if (detected.isEmpty()) {
                    Toast.makeText(getContext(), L("❌ 当前帧未检测到非透明像素"), Toast.LENGTH_SHORT).show();
                } else {
                    String key = curFrame[0].group + "," + curFrame[0].item;
                    List<ClsnBox> list = frameBoxesMap.get(key);
                    if (list == null) { list = new ArrayList<>(); frameBoxesMap.put(key, list); }
                    list.addAll(detected);
                    statusBar.setText(L("🪄 自动识别成功，已生成 ") + detected.size() + (targetType == 1 ? L(" 个打击框") : L(" 个防御框")));
                    clsnCanvas.invalidate();
                }
                ad.dismiss();
            };

            bFullDef.setOnClickListener(runDetect); bContourDef.setOnClickListener(runDetect);
            bFullAtk.setOnClickListener(runDetect); bContourAtk.setOnClickListener(runDetect);
            al.addView(bFullDef, alp); al.addView(bContourDef, alp); al.addView(bFullAtk, alp); al.addView(bContourAtk, alp);
            ad.setContentView(al); ad.show();
        });

        // 异形分段精度调节 (3 ~ 16 段)
        btnPrecision.setOnClickListener(v -> {
            final Dialog pd = new Dialog(getContext());
            if (pd.getWindow() != null) pd.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            LinearLayout pl = new LinearLayout(getContext()); pl.setOrientation(LinearLayout.VERTICAL); pl.setPadding((int)(18*density), (int)(18*density), (int)(18*density), (int)(18*density));
            GradientDrawable pbg = new GradientDrawable(); pbg.setColor(Color.parseColor("#F51E2430")); pbg.setCornerRadius(12f * density); pbg.setStroke((int)(1.5f * density), Color.parseColor("#38BDF8")); pl.setBackground(pbg);
            TextView pt = new TextView(getContext()); pt.setText(L("异形拟合精度 (分段数): ") + shapeSlices[0]); applyGlobalFontSettings(pt, 1.0f, true); pl.addView(pt);
            SeekBar sb = new SeekBar(getContext()); sb.setMax(13); sb.setProgress(Math.max(0, Math.min(13, shapeSlices[0] - 3)));
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    shapeSlices[0] = 3 + progress;
                    pt.setText(L("异形拟合精度 (分段数): ") + shapeSlices[0]);
                    btnPrecision.setText(L("⚙️ 异形精度: ") + shapeSlices[0]);
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            pl.addView(sb, new LinearLayout.LayoutParams((int)(260*density), -2));
            pd.setContentView(pl); pd.show();
        });

        btnLoadSff.setOnClickListener(v -> showWin10FilePicker(L("选择 SFF 人物文件"), 4, null, null, f -> {
            if (f.isDirectory()) showSffGridPicker(f, picked -> { sffPathRef[0] = picked.getAbsolutePath(); loadSffAction.run(); });
            else { sffPathRef[0] = f.getAbsolutePath(); loadSffAction.run(); }
        }));

        btnLoadAct.setOnClickListener(v -> showWin10FilePicker(L("选择 ACT 调色板"), 9, null, null, f -> {
            actPathRef[0] = f.getAbsolutePath(); renderCurrentFrame.run();
        }));

        // 解析已有 .air 文件中的 Clsn1 / Clsn2 并映射到对应帧
        btnLoadAir.setOnClickListener(v -> showWin10FilePicker(L("选择要导入的 .air 动作文件"), 14, null, null, f -> {
            new Thread(() -> {
                int importedCount = 0;
                try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
                    List<ClsnBox> pendingBoxes = new ArrayList<>();
                    List<ClsnBox> defaultBoxes = new ArrayList<>();
                    boolean inDefault = false;
                    String line;
                    while ((line = br.readLine()) != null) {
                        int cIdx = line.indexOf(';');
                        if (cIdx >= 0) line = line.substring(0, cIdx);
                        String t = line.trim();
                        if (t.isEmpty()) continue;
                        String low = t.toLowerCase();
                        if (low.startsWith("[begin action")) {
                            pendingBoxes.clear(); defaultBoxes.clear(); inDefault = false;
                        } else if (low.startsWith("clsn1default:") || low.startsWith("clsn2default:")) {
                            inDefault = true;
                        } else if (low.startsWith("clsn1:") || low.startsWith("clsn2:")) {
                            inDefault = false;
                        } else if (low.startsWith("clsn1[") || low.startsWith("clsn2[")) {
                            int eq = t.indexOf('=');
                            if (eq > 0) {
                                String[] nums = t.substring(eq + 1).split(",");
                                if (nums.length >= 4) {
                                    int bType = low.startsWith("clsn1[") ? 1 : 2;
                                    ClsnBox b = new ClsnBox(bType, Integer.parseInt(nums[0].trim()), Integer.parseInt(nums[1].trim()), Integer.parseInt(nums[2].trim()), Integer.parseInt(nums[3].trim()));
                                    if (inDefault) defaultBoxes.add(b); else pendingBoxes.add(b);
                                }
                            }
                        } else if (t.matches("^-?\\d+\\s*,\\s*-?\\d+\\s*,.*")) {
                            String[] parts = t.split(",");
                            if (parts.length >= 2) {
                                String key = parts[0].trim() + "," + parts[1].trim();
                                List<ClsnBox> use = !pendingBoxes.isEmpty() ? pendingBoxes : defaultBoxes;
                                if (!use.isEmpty()) {
                                    frameBoxesMap.put(key, new ArrayList<>(use));
                                    importedCount += use.size();
                                }
                                pendingBoxes.clear();
                            }
                        }
                    }
                } catch (Exception ignored) {}
                final int total = importedCount;
                uiHandler.post(() -> {
                    Toast.makeText(getContext(), L("✅ AIR 导入完成，共解析判定框: ") + total, Toast.LENGTH_SHORT).show();
                    clsnCanvas.invalidate();
                });
            }).start();
        }));

        // 按笔画整组撤销（异形/手绘/自动识别一笔撤完）
        btnUndo.setOnClickListener(v -> {
            if (!activePolyPts.isEmpty()) {
                activePolyPts.remove(activePolyPts.size() - 1);
                clsnCanvas.invalidate();
                return;
            }
            if (curFrame[0] == null) return;
            String key = curFrame[0].group + "," + curFrame[0].item;
            List<ClsnBox> list = frameBoxesMap.get(key);
            if (list != null && !list.isEmpty()) {
                int lastStroke = list.get(list.size() - 1).strokeId;
                if (lastStroke > 0) {
                    while (!list.isEmpty() && list.get(list.size() - 1).strokeId == lastStroke) {
                        list.remove(list.size() - 1);
                    }
                } else {
                    list.remove(list.size() - 1);
                }
                clsnCanvas.invalidate();
                renderCurrentFrame.run();
            }
        });

        btnClear.setOnClickListener(v -> {
            activePolyPts.clear();
            freehandPts.clear();
            if (curFrame[0] == null) return;
            frameBoxesMap.remove(curFrame[0].group + "," + curFrame[0].item);
            clsnCanvas.invalidate();
            renderCurrentFrame.run();
        });

        btnPrev.setOnClickListener(v -> { frameIdx[0]--; renderCurrentFrame.run(); });
        btnNext.setOnClickListener(v -> { frameIdx[0]++; renderCurrentFrame.run(); });

        btnGroup.setOnClickListener(v -> {
            if (allFrames.isEmpty()) return;
            List<Integer> gs = new ArrayList<>(); gs.add(-999);
            for (GoEngineBridge.SffFrame fr : allFrames) { if (!gs.contains(fr.group)) gs.add(fr.group); }
            final Dialog gd = new Dialog(getContext()); gd.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            LinearLayout gl = new LinearLayout(getContext()); gl.setOrientation(LinearLayout.VERTICAL); gl.setBackgroundColor(Color.parseColor("#2D2D30")); gl.setPadding((int)(16*density), (int)(16*density), (int)(16*density), (int)(16*density));
            ScrollView sv = new ScrollView(getContext()); LinearLayout list = new LinearLayout(getContext()); list.setOrientation(LinearLayout.VERTICAL);
            for (final int g : gs) {
                Button b = createButton(g == -999 ? L("📂 所有动作") : L("📁 动作组 ") + g, "#1E1E1E");
                b.setOnClickListener(gv -> {
                    groupFrames.clear();
                    if (g == -999) groupFrames.addAll(allFrames);
                    else for (GoEngineBridge.SffFrame fr : allFrames) { if (fr.group == g) groupFrames.add(fr); }
                    frameIdx[0] = 0; renderCurrentFrame.run(); gd.dismiss();
                });
                list.addView(b, new LinearLayout.LayoutParams(-1, -2));
            }
            sv.addView(list); gl.addView(sv, new LinearLayout.LayoutParams((int)(220*density), (int)(280*density)));
            gd.setContentView(gl); gd.show();
        });

        btnToggleC1.setOnClickListener(v -> {
            showClsn1[0] = !showClsn1[0];
            btnToggleC1.setText(showClsn1[0] ? L("👁️ 打击:开") : L("👁️ 打击:关"));
            clsnCanvas.invalidate();
        });
        btnToggleC2.setOnClickListener(v -> {
            showClsn2[0] = !showClsn2[0];
            btnToggleC2.setText(showClsn2[0] ? L("👁️ 防御:开") : L("👁️ 防御:关"));
            clsnCanvas.invalidate();
        });

        btnExportAir.setOnClickListener(v -> {
            StringBuilder sb = new StringBuilder();
            int actionNum = (curFrame[0] != null) ? curFrame[0].group : 0;
            sb.append("[Begin Action ").append(actionNum).append("]\n");
            boolean hasAny = false;
            for (java.util.Map.Entry<String, List<ClsnBox>> entry : frameBoxesMap.entrySet()) {
                List<ClsnBox> boxes = entry.getValue();
                if (boxes == null || boxes.isEmpty()) continue;
                hasAny = true;
                List<ClsnBox> c1 = new ArrayList<>(), c2 = new ArrayList<>();
                for (ClsnBox b : boxes) { if (b.type == 1) c1.add(b); else c2.add(b); }
                if (!c1.isEmpty()) {
                    sb.append("Clsn1: ").append(c1.size()).append("\n");
                    for (int i = 0; i < c1.size(); i++) {
                        ClsnBox b = c1.get(i);
                        sb.append("  Clsn1[").append(i).append("] = ").append(b.x1).append(", ").append(b.y1).append(", ").append(b.x2).append(", ").append(b.y2).append("\n");
                    }
                }
                if (!c2.isEmpty()) {
                    sb.append("Clsn2: ").append(c2.size()).append("\n");
                    for (int i = 0; i < c2.size(); i++) {
                        ClsnBox b = c2.get(i);
                        sb.append("  Clsn2[").append(i).append("] = ").append(b.x1).append(", ").append(b.y1).append(", ").append(b.x2).append(", ").append(b.y2).append("\n");
                    }
                }
                sb.append(entry.getKey()).append(", 0, 0, 5\n\n");
            }
            if (!hasAny) {
                Toast.makeText(getContext(), L("⚠️ 当前尚未绘制任何打击框或防御框"), Toast.LENGTH_SHORT).show();
                return;
            }
            final String airCode = sb.toString();
            final Dialog d = new Dialog(getContext()); d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setPadding((int)(16*density), (int)(16*density), (int)(16*density), (int)(16*density));
            GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#252526")); border.setStroke((int)(2*density), Color.parseColor("#4CAF50")); border.setCornerRadius(12*density); box.setBackground(border);
            box.addView(createSubTitle(L("📋 生成的 AIR 判定框代码:")));
            EditText codeView = createInput("", airCode); codeView.setSingleLine(false); codeView.setMinLines(6); codeView.setMaxLines(12);
            box.addView(codeView, new LinearLayout.LayoutParams((int)(320*density), -2));
            LinearLayout row = new LinearLayout(getContext()); row.setOrientation(LinearLayout.HORIZONTAL);
            Button bCopy = createButton(L("📋 复制代码"), "#0078D7");
            Button bSave = createButton(L("💾 导出 .air"), "#4CAF50");
            bCopy.setOnClickListener(cv -> {
                try {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("air_clsn", codeView.getText().toString()));
                        Toast.makeText(getContext(), L("✅ AIR 判定框代码已复制到剪贴板！"), Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception ignored) {}
            });
            bSave.setOnClickListener(sv -> {
                try {
                    File outDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "IkemenExports");
                    if (!outDir.exists()) outDir.mkdirs();
                    File outFile = new File(outDir, "clsn_action_" + actionNum + ".air");
                    try (FileOutputStream fos = new FileOutputStream(outFile)) { fos.write(codeView.getText().toString().getBytes("UTF-8")); }
                    Toast.makeText(getContext(), L("✅ 已导出 AIR 文件: ") + outFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                    d.dismiss();
                } catch (Exception e) { Toast.makeText(getContext(), L("❌ 导出失败"), Toast.LENGTH_SHORT).show(); }
            });
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -2, 1f); rp.setMargins((int)(4*density), (int)(10*density), (int)(4*density), 0);
            row.addView(bCopy, rp); row.addView(bSave, rp);
            box.addView(row); d.setContentView(box); d.show();
        });

        bar.addView(btnLoadSff, bp); bar.addView(btnLoadAct, bp); bar.addView(btnLoadAir, bp);
        bar.addView(btnModePan, bp); bar.addView(btnModeAtk, bp); bar.addView(btnModeDef, bp);
        bar.addView(btnShapeMode, bp); bar.addView(btnClosePoly, bp); bar.addView(btnAutoDetect, bp); bar.addView(btnPrecision, bp);
        bar.addView(btnUndo, bp); bar.addView(btnClear, bp);
        bar.addView(btnPrev, bp); bar.addView(btnNext, bp); bar.addView(btnGroup, bp);
        bar.addView(btnToggleC1, bp); bar.addView(btnToggleC2, bp);
        bar.addView(btnExportAir, bp);
        barScroll.addView(bar);
        root.addView(barScroll);

        if (!sffPathRef[0].isEmpty()) {
            root.post(loadSffAction);
        }
        return root;
    }

    // ======================================================================================
    // 📚 模块 9：MUGEN (Win/1.0/1.1) & Ikemen GO 全能词典百科（支持官方 GitHub 实时同步）
    // ======================================================================================
    private static class DictEntry {
        String title;
        String category; // sctrl, trigger, zss_lua, format, compat, official
        String versions; // Win / 1.0 / 1.1 / IK-GO
        String summary;
        String detail;
        String example;
        boolean isOfficialSync;
        DictEntry(String title, String category, String versions, String summary, String detail, String example, boolean isOfficialSync) {
            this.title = title; this.category = category; this.versions = versions;
            this.summary = summary; this.detail = detail; this.example = example;
            this.isOfficialSync = isOfficialSync;
        }
    }

    private String httpGetWithFallback(String rawUrl) {
        String[] candidates = new String[] { rawUrl, "https://ghfast.top/" + rawUrl };
        for (String urlStr : candidates) {
            java.net.HttpURLConnection conn = null;
            try {
                java.net.URL url = new java.net.URL(urlStr);
                conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(7000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("User-Agent", "IkemenDroid-Dict/1.0");
                int code = conn.getResponseCode();
                if (code == 200) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line).append("\n");
                    }
                    if (sb.length() > 0) return sb.toString();
                }
            } catch (Throwable ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return null;
    }

    private List<DictEntry> parseMarkdownToDictEntries(String pageTitle, String mdContent) {
        List<DictEntry> out = new ArrayList<>();
        if (mdContent == null || mdContent.trim().isEmpty()) return out;
        String[] lines = mdContent.split("\n");
        String curHeading = pageTitle;
        StringBuilder curBody = new StringBuilder();
        StringBuilder curCode = new StringBuilder();
        boolean inCode = false;

        for (String raw : lines) {
            String t = raw.trim();
            if (t.startsWith("```")) {
                inCode = !inCode;
                continue;
            }
            if (!inCode && (t.startsWith("## ") || t.startsWith("### "))) {
                if (curBody.length() > 0 || curCode.length() > 0) {
                    String bodyStr = curBody.toString().trim();
                    String sum = bodyStr.replace("\n", " ");
                    if (sum.length() > 90) sum = sum.substring(0, 90) + "...";
                    out.add(new DictEntry(curHeading, "official", "IK-GO", sum.isEmpty() ? pageTitle : sum, bodyStr, curCode.toString().trim(), true));
                }
                curHeading = pageTitle + " · " + t.replaceAll("^#+\\s*", "").trim();
                curBody.setLength(0);
                curCode.setLength(0);
            } else {
                if (inCode) curCode.append(raw).append("\n");
                else if (!t.isEmpty()) curBody.append(t).append("\n");
            }
        }
        if (curBody.length() > 0 || curCode.length() > 0) {
            String bodyStr = curBody.toString().trim();
            String sum = bodyStr.replace("\n", " ");
            if (sum.length() > 90) sum = sum.substring(0, 90) + "...";
            out.add(new DictEntry(curHeading, "official", "IK-GO", sum.isEmpty() ? pageTitle : sum, bodyStr, curCode.toString().trim(), true));
        }
        return out;
    }

    private List<DictEntry> buildBuiltinDictionaryEntries() {
        List<DictEntry> list = new ArrayList<>();
        // --- 🎮 核心控制器 (SCTRL: Win / 1.0 / 1.1 / IK-GO) ---
        list.add(new DictEntry("HitDef", "sctrl", "Win/1.0/1.1/IK-GO",
                "定义攻击判定参数（伤害、硬直、击飞、特效、防御属性等）",
                "最核心的攻击控制器。常用参数：attr(S/C/A, NA/SA/HA/NP/SP/HP/NT/ST/HT)、hitflag(MAFD)、guardflag(MA/L/H)、damage(命中,防损)、pausetime、sparkno、hitsound、ground.type、ground.slidetime、ground.hittime、ground.velocity、air.velocity、fall、envshake。\nIkemen GO 扩展：支持 dizzypoints、guardpoints、redlife、score、hitonce、redirectid 等。",
                "[State 200, HitDef]\ntype = HitDef\ntrigger1 = AnimElem = 3\nattr = S, NA\ndamage = 25, 0\nanimtype = Light\nguardflag = MA\nhitflag = MAF\npausetime = 8, 8\nground.type = High\nground.slidetime = 10\nground.hittime = 12\nground.velocity = -4\nair.velocity = -2.5, -3.5", false));
        list.add(new DictEntry("StateTypeSet", "sctrl", "Win/1.0/1.1/IK-GO",
                "动态修改角色当前状态类型(S/C/A/L)、攻击状态(I/A/H)与物理属性(S/C/A/N)",
                "用于在中途切换站/蹲/空/倒地状态、结束攻击判定状态(movetype=I)避免对手一直防御，或切换重力物理。",
                "[State 200, EndAttack]\ntype = StateTypeSet\ntrigger1 = AnimElemTime(4) >= 0\nmovetype = I", false));
        list.add(new DictEntry("ChangeState", "sctrl", "Win/1.0/1.1/IK-GO",
                "跳转到指定状态号 (Statedef)，并可选修改控制权 ctrl 与动画 anim",
                "动作收招或连段取消的核心控制器。value 为目标状态号；ctrl = 1 恢复玩家操控；anim 可在切换同时指定动画。",
                "[State 200, End]\ntype = ChangeState\ntrigger1 = AnimTime = 0\nvalue = 0\nctrl = 1", false));
        list.add(new DictEntry("ChangeAnim / ChangeAnim2", "sctrl", "Win/1.0/1.1/IK-GO",
                "切换自身动画 (ChangeAnim) 或给被投技抓中的对手播放自己的自定义动画 (ChangeAnim2)",
                "value 指定 AIR 动作号，elem 指定从第几帧开始播放。ChangeAnim2 专用于自定义受击状态(Custom State)里调用攻击方的 AIR 动画。",
                "[State 810, Anim]\ntype = ChangeAnim2\ntrigger1 = Time = 0\nvalue = 820", false));
        list.add(new DictEntry("VelSet / VelAdd / VelMul", "sctrl", "Win/1.0/1.1/IK-GO",
                "设置、累加或按比例缩放角色 X/Y 速度（X 正值为面向前方，Y 负值为向上）",
                "VelSet 直接覆写速度；VelAdd 每帧叠加（常用于自定义重力）；VelMul 按倍率减速（如地面摩擦 x = 0.85）。",
                "[State 1000, DashVel]\ntype = VelSet\ntrigger1 = Time = 0\nx = 8.5\ny = -4.0", false));
        list.add(new DictEntry("PosSet / PosAdd / PosFreeze", "sctrl", "Win/1.0/1.1/IK-GO",
                "设置绝对坐标、相对偏移坐标，或冻结当前帧移动",
                "PosAdd 的 x 会自动按角色朝向翻转；PosSet 的 y = 0 代表地面。PosFreeze 常用于顿帧或空中滞空。",
                "[State 1000, Step]\ntype = PosAdd\ntrigger1 = AnimElem = 2\nx = 12", false));
        list.add(new DictEntry("Helper", "sctrl", "Win/1.0/1.1/IK-GO",
                "生成独立实体（飞行道具、分身、援护、系统控制器）",
                "可拥有独立状态机、变量和判定框。常用参数：helpertype(normal/player)、name、ID、stateno、pos、postype、ownpal、supermovetime、pausemovetime。\nIkemen GO 扩展：支持 remapPal、keyctrl 增强与 Helper(id, index) 精确索引。",
                "[State 1000, Fireball]\ntype = Helper\ntrigger1 = AnimElem = 4\nhelpertype = normal\nname = \"Fireball\"\nID = 1005\nstateno = 1005\npos = 40, -60\npostype = p1\nownpal = 1", false));
        list.add(new DictEntry("Explod / ModifyExplod / RemoveExplod", "sctrl", "Win/1.0/1.1/IK-GO",
                "生成、实时修改或移除纯视觉特效（火花、残影、UI 贴图）",
                "常用参数：anim、ID、pos、postype、bindtime、removetime、scale、sprpriority、ontop、trans(add/add1/sub/addalpha)。\nMUGEN 1.1+ & IK-GO 支持 angle/xangle/yangle 旋转与 space(stage/screen)。",
                "[State 1000, FX]\ntype = Explod\ntrigger1 = AnimElem = 2\nanim = 7000\nID = 7000\npos = 30, -50\npostype = p1\nsprpriority = 3\nscale = 0.5, 0.5\nremovetime = -2", false));
        list.add(new DictEntry("Projectile", "sctrl", "Win/1.0/1.1/IK-GO",
                "发射内置简易飞行道具（自带命中与相杀逻辑）",
                "适合简单波动拳；复杂轨迹/多段判定推荐用 Helper。常用参数：ProjID、projanim、projhitanim、projremanim、projscale、offset、velocity、remvelocity。",
                "[State 1000, Proj]\ntype = Projectile\ntrigger1 = AnimElem = 4\nProjID = 1000\nprojanim = 1005\nprojhitanim = 1006\noffset = 45, -60\nvelocity = 6, 0\nattr = S, SP\ndamage = 55, 8", false));
        list.add(new DictEntry("NotHitBy / HitBy", "sctrl", "Win/1.0/1.1/IK-GO",
                "设置无敌属性（免疫哪些攻击）或仅可被哪些攻击命中",
                "value 格式如 SCA（全无敌）、,AA（免打击）、,AT（免投技）、,AP（免飞行道具）。time 为持续帧数；value2 可叠加第二套规则。",
                "[State 1100, Invincible]\ntype = NotHitBy\ntrigger1 = Time = 0\nvalue = SCA\ntime = 12", false));
        list.add(new DictEntry("HitOverride", "sctrl", "Win/1.0/1.1/IK-GO",
                "霸体/当身核心：被指定属性攻击命中时强制转入自定义状态",
                "attr 指定拦截的攻击属性，stateno 指定受击后跳转的状态号，slot 为槽位(0~7)，time 为持续帧数。",
                "[State 1200, Armor]\ntype = HitOverride\ntrigger1 = 1\nattr = SCA, AA, AP\nstateno = 1210\nslot = 0\ntime = 1", false));
        list.add(new DictEntry("SuperPause / Pause", "sctrl", "Win/1.0/1.1/IK-GO",
                "超杀暗屏定格 (SuperPause) 或全局时间暂停 (Pause)",
                "SuperPause 支持 time、anim、sound、pos、movetime、darken、p2defmul、poweradd、unhittable。",
                "[State 3000, SuperPause]\ntype = SuperPause\ntrigger1 = Time = 0\ntime = 40\nmovetime = 40\nanim = -1\ndarken = 1\npoweradd = -1000", false));
        list.add(new DictEntry("VarSet / VarAdd / ParentVarSet / VarRangeSet", "sctrl", "Win/1.0/1.1/IK-GO",
                "读写整数变量 var(0~59)、浮点变量 fvar(0~39) 与系统变量 sysvar(0~4)",
                "Win/1.0/1.1 角色最多 60 个整数 var 与 40 个浮点 fvar；Ikemen GO 推荐使用无限命名变量 MapSet/MapAdd 替代硬编码编号。",
                "[State 200, SetVar]\ntype = VarSet\ntrigger1 = Time = 0\nv = 10\nvalue = 1", false));
        list.add(new DictEntry("MapSet / MapAdd / RootMapSet / ParentMapSet", "sctrl", "IK-GO",
                "【IK专属】读写具名键值对变量 Map（无数量上限，彻底告别 var 编号冲突）",
                "Ikemen GO 革命性特性！直接用字符串名字存数值，如 map(combo_cnt)，支持自身 MapSet、父级 ParentMapSet、本体 RootMapSet、全队 TeamMapSet。",
                "[State 200, IKMap]\ntype = MapAdd\ntrigger1 = MoveHit = 1\nmap = \"custom_gauge\"\nvalue = 10", false));
        list.add(new DictEntry("AssertSpecial", "sctrl", "Win/1.0/1.1/IK-GO",
                "开启单帧特殊引擎标志（如不可防御、隐藏血条、禁止站防/蹲防、禁止转身等）",
                "常用 flag：unguardable、nostandguard、nocrouchguard、noairguard、noautoturn、invisible、nobardisplay、noshadow、intro、roundnotover、timerfreeze。\nIK-GO 新增：globalnoshadow、nomusic、noKO、noko_slow 等。",
                "[State 3000, NoBar]\ntype = AssertSpecial\ntrigger1 = 1\nflag = nobardisplay\nflag2 = noBG", false));
        list.add(new DictEntry("TargetBind / TargetState / TargetLifeAdd", "sctrl", "Win/1.0/1.1/IK-GO",
                "投技核心三件套：绑定对手坐标、强制对手进入自定义状态、增减对手血量",
                "需在 HitDef 命中并捕获 Target 后生效。TargetState 强制对手执行我方 CNS 中的状态；TargetBind 将对手固定在相对坐标 pos。",
                "[State 810, Bind]\ntype = TargetBind\ntrigger1 = AnimElem = 1\npos = 45, -60\ntime = 10", false));
        list.add(new DictEntry("PalFX / BGPalFX / AllPalFX / RemapPal", "sctrl", "Win/1.0/1.1/IK-GO",
                "角色/背景/全屏滤镜染色、闪烁、反色，以及 1.1+/IK 实时换色表",
                "PalFX 支持 add(r,g,b)、mul(r,g,b)、sinadd、invertall、color(0=灰度~256=原色)。RemapPal (1.0+) 可动态把色表组 source 映射到 dest。",
                "[State 3000, Flash]\ntype = PalFX\ntrigger1 = Time = 0\ntime = 15\nadd = 120, 40, 0\nsinadd = 60, 60, 60, 6", false));
        list.add(new DictEntry("Zoom / Camera", "sctrl", "1.1/IK-GO",
                "动态镜头缩放 (Zoom) 与 Ikemen GO 高级镜头视角控制 (Camera)",
                "MUGEN 1.1 与 Ikemen GO 支持在招式中动态拉近/拉远镜头（scale、pos、lag、stagebound、camerabound）。IK-GO 额外提供 Camera 控制器。",
                "[State 3000, SuperZoom]\ntype = Zoom\ntrigger1 = Time < 30\nscale = 1.25\npos = Pos X, Pos Y - 60\nlag = 0.85", false));
        list.add(new DictEntry("Text / LifebarAction / Dialogue / PlayBgm", "sctrl", "IK-GO",
                "【IK专属】屏幕任意位置绘制文字、触发血条演出、剧情对话与动态切换 BGM",
                "Text 控制器支持 font、text、pos、layerno、scale、removetime 及 <color:RRGGBB> 行内变色；PlayBgm 可在战斗中随时切换音乐与音量；Dialogue 用于角色对话系统。",
                "[State 3000, CutInText]\ntype = Text\ntrigger1 = Time = 0\ntext = \"<color:FF4444>CRITICAL ART!</color>\"\nfont = 1, 0, 0\npos = 160, 60\nremovetime = 60", false));
        list.add(new DictEntry("LoadFile / SaveFile / PrintToConsole / RedirectID", "sctrl", "IK-GO",
                "【IK专属】读写本地存档数据、控制台打印调试，以及跨实体 RedirectID 参数",
                "Ikemen GO 中几乎所有控制器都支持通用参数 redirectid = <PlayerID>，无需切换自定义状态即可直接对指定 ID 实体执行控制器！",
                "[State -2, Debug]\ntype = PrintToConsole\ntrigger1 = Time = 1\ntext = \"HP=%d, Power=%d\"\nparams = Life, Power", false));

        // --- ⚡ 触发器 (Trigger: Win / 1.0 / 1.1 / IK-GO) ---
        list.add(new DictEntry("Time / StateTime / AnimTime / AnimElem / AnimElemTime", "trigger", "Win/1.0/1.1/IK-GO",
                "状态计时与动画帧数判定核心触发器",
                "Time：进入当前状态后的帧数（从0开始）。\nAnimTime：距离当前动画播完还剩多少帧（负数，最后一帧结束时为 0）。\nAnimElem = N：当前动画正好切换到第 N 帧时为真。\nAnimElemTime(N)：距离第 N 帧开始过了多少帧。",
                "trigger1 = AnimElem = 3\ntrigger2 = AnimTime = 0", false));
        list.add(new DictEntry("StateNo / PrevStateNo / StateType / MoveType / Physics", "trigger", "Win/1.0/1.1/IK-GO",
                "读取当前状态号、上一个状态号、站蹲空状态、攻防状态与物理状态",
                "StateType: S(站) C(蹲) A(空) L(倒地)。MoveType: I(待机) A(攻击) H(受击)。常用于取消条件判断。",
                "trigger1 = StateType != A && MoveType = I && Ctrl", false));
        list.add(new DictEntry("MoveContact / MoveHit / MoveGuarded / MoveReversed", "trigger", "Win/1.0/1.1/IK-GO",
                "检测当前状态的攻击是否接触、命中、被防御或被当身（并记录已过帧数）",
                "命中当帧返回 1，之后每帧递增。取消连招通常写 MoveContact = [1, 10] 或 MoveHit。",
                "trigger1 = Command = \"Special\" && MoveContact", false));
        list.add(new DictEntry("P2BodyDist X/Y & P2Dist X/Y", "trigger", "Win/1.0/1.1/IK-GO",
                "获取与对手之间的距离（BodyDist 算宽度边缘，Dist 算轴心原点）",
                "AI 判断出招距离首选 P2BodyDist X（0 代表贴身，负数代表重叠）。Y 负值表示对手在上方。",
                "trigger1 = P2BodyDist X = [0, 65] && P2BodyDist Y = [-50, 0]", false));
        list.add(new DictEntry("AILevel", "trigger", "1.0/1.1/IK-GO",
                "官方内置 AI 难度等级 (0=玩家手操, 1~8=电脑难度)",
                "注意兼容差异：WinMUGEN 没有 AILevel 触发器（老角色用 var(59) 开关）；MUGEN 1.0/1.1 与 Ikemen GO 原生支持 AILevel，写 AI 时可用 AILevel > 0 并按 random < AILevel * 100 控制反应概率。",
                "trigger1 = AILevel > 0 && Random < (AILevel * 80)", false));
        list.add(new DictEntry("Command / SelfCommand", "trigger", "Win/1.0/1.1/IK-GO",
                "检测 .cmd 中定义的搓招指令是否触发",
                "Command = \"QCF_x\" 检测按键指令。Ikemen GO 新增 SelfCommand，即使处于被对手控制的自定义状态中也能读取自身按键。",
                "trigger1 = Command = \"hadouken\" && Power >= 1000", false));
        list.add(new DictEntry("NumHelper / NumExplod / NumProjID / NumTarget", "trigger", "Win/1.0/1.1/IK-GO",
                "查询当前场上存活的指定 ID 的 Helper、特效、飞行道具或捕获目标数量",
                "常用于限制同屏飞行道具数量（如 NumHelper(1005) = 0）或确认投技目标存在。",
                "trigger1 = NumHelper(1005) = 0 && NumProjID(1000) = 0", false));
        list.add(new DictEntry("GetHitVar(...)", "trigger", "Win/1.0/1.1/IK-GO",
                "读取受击时的各种参数（伤害、硬直、击飞速度、连段数、是否倒地等）",
                "常用字段：damage、hitcount、fallcount、hittime、slidetime、xvel、yvel、fall、hitshaketime、isbound、chainid。",
                "trigger1 = GetHitVar(hitcount) >= 3", false));
        list.add(new DictEntry("Map(name) / IsAsserted(flag) / GameMode / StageConst", "trigger", "IK-GO",
                "【IK专属】读取 Map 变量、检测 AssertSpecial 标志、当前游戏模式与场景常量",
                "Ikemen GO 新增大量信息查询触发器：map(key)、isAsserted(unguardable)、gameMode = \"training\"、hitDefVar(...)、explodVar(...)、clsnVar(...)、moveHitVar(...)、palfxVar(...)。",
                "trigger1 = map(custom_gauge) >= 100 && !isAsserted(nobardisplay)", false));
        list.add(new DictEntry("重定向: Root / Parent / Target / Helper(id, idx) / PlayerID(id)", "trigger", "Win/1.0/1.1/IK-GO",
                "跨实体读取触发器数据（如读取本体血量或对手坐标）",
                "语法：Root, Life、Parent, Var(1)、Target, StateNo、EnemyNear, Pos X。\nIkemen GO 增强：Helper(id, index) 支持第 2 参数指定同 ID 第几个 Helper，彻底解决多同类 Helper 定位难题。",
                "trigger1 = Root, Alive && Helper(1005, 0), Pos X > 0", false));

        // --- 🧬 ZSS & Lua (Ikemen GO 核心脚本) ---
        list.add(new DictEntry("ZSS 语法基础 (Zero State Script)", "zss_lua", "IK-GO",
                "Ikemen GO 现代化角色脚本语言，支持 if/else、switch、for、局部变量与函数",
                "相比传统 CNS，ZSS 写法更接近 Go/C：无需重复写 trigger1/triggerall，支持代码块嵌套、ignoreHitPause {} 块、let 局部变量、switch/case、自定义 [Function Name(arg)] 与 call Name()。",
                "[Statedef 200; type: S; movetype: A; physics: S; anim: 200; ctrl: 0;]\nif time = 0 {\n    playSnd{value: 0, 0}\n}\nif animElem = 3 {\n    hitDef{attr: S, NA; damage: 25, 0; guardflag: MA;}\n}\nif animTime = 0 {\n    changeState{value: 0; ctrl: 1;}\n}", false));
        list.add(new DictEntry("ZSS [Function] 自定义函数与循环", "zss_lua", "IK-GO",
                "在 ZSS 中定义可复用的函数、for 循环与局部变量 let",
                "可用 [Function MyHelper(x, y) ret] 定义函数，内部用 let 声明局部临时变量，彻底省去占用角色 var(0~59)！",
                "[Function SpawnSparks(cnt)]\nfor i = 0; $cnt - 1; 1 {\n    explod{anim: 7000; pos: $i * 10, -40;}\n}", false));
        list.add(new DictEntry("Ikemen GO Lua 模块与钩子 (hook.add)", "zss_lua", "IK-GO",
                "使用 Lua 编写自定义菜单、商店、剧情模式、HUD 与全局逻辑",
                "Ikemen GO 支持 external/mods/*.lua 模块化扩展，通过 hook.add(\"loop\", \"my_id\", fn) 注入每帧逻辑，常用 API 包括 textImgNew/textImgDraw、animNew/animDraw、sndPlay、playBGM、commandGetState、main.f_menu 等。",
                "hook.add(\"loop\", \"my_hud\", function()\n    -- 每帧自定义绘制逻辑\nend)", false));
        list.add(new DictEntry("行内文字变色与图标标签 (<color:RRGGBB>)", "zss_lua", "IK-GO",
                "Ikemen GO v1.0+ 字体 DEF 与界面文本支持行内富文本染色和按键图标",
                "Screenpack、Storyboard、Lua、Lifebar、Text 与 LifebarAction 控制器全部支持 <color:RRGGBB>文本</color> 变色以及手柄按键提示图标。",
                "text = \"按下 <color:38BDF8>A+B</color> 发动 <color:FF5252>觉醒技</color>\"", false));

        // --- 📁 文件格式 (DEF / AIR / SFF / SND / 3D) ---
        list.add(new DictEntry("角色配置文件 (.def)", "format", "Win/1.0/1.1/IK-GO",
                "定义角色名、作者、引擎版本、CMD/CNS/SFF/AIR/SND/色表路径",
                "[Info] 中 mugenversion 填 04,14,2001(Win) / 1.0 / 1.1；Ikemen GO 专属特性需加 ikemenVersion = 1.0。\n[Files] 指定 cmd、cns、st、stcommon、sprite、anim、sound 与 pal1~pal12。",
                "[Info]\nname = \"Kyo\"\ndisplayname = \"Kyo Kusanagi\"\nmugenversion = 1.1\nikemenVersion = 1.0\nlocalcoord = 320, 240", false));
        list.add(new DictEntry("动画与判定框文件 (.air)", "format", "Win/1.0/1.1/IK-GO",
                "定义动作帧序列、持续时间、翻转、透明混合以及 Clsn1(打击框)/Clsn2(防御框)",
                "每帧格式：Group, Item, OffsetX, OffsetY, Ticks [, Flip(H/V/VH) [, Trans(A/A1/S/ASxDx)]]。\nClsn1 为红框(攻击)，Clsn2 为蓝框(受击)；Clsn2Default 会应用到该动作后续所有未单独定义 Clsn2 的帧。",
                "[Begin Action 200]\nClsn2Default: 1\n  Clsn2[0] = -15, -90, 15, 0\n200,0, 0,0, 3\nClsn1: 1\n  Clsn1[0] = 10, -75, 55, -55\n200,1, 0,0, 4", false));
        list.add(new DictEntry("指令文件 (.cmd)", "format", "Win/1.0/1.1/IK-GO",
                "定义搓招指令序列、按键缓冲时间与 [Statedef -1] 出招触发",
                "符号说明：~松开、/按住、$方向泛指(如 $D 包含下/左下/右下)、+同时按、>严格顺序。time 控制搓招窗口帧数，buffer.time 控制预输入缓冲。",
                "[Command]\nname = \"QCF_x\"\ncommand = ~D, DF, F, x\ntime = 15", false));
        list.add(new DictEntry("常驻状态 (-1 / -2 / -3) 与公共状态 (0~5999)", "format", "Win/1.0/1.1/IK-GO",
                "理解 MUGEN/IK 状态机每帧执行顺序与系统保留状态号",
                "-1：指令出招（仅本体执行，被抓入自定义状态时不执行）。\n-2：全局常驻（即使被对手控制也每帧执行，适合血条/变量维护）。\n-3：仅本体在非自定义状态时执行（Helper 不执行 -3）。\n常用公共状态：0站立、10~12蹲、20走、40~52跳、100跑、120~155防、5000~5150受击倒地、5900开局。",
                "[Statedef -2]\n; 每帧必然执行的全局检测", false));
        list.add(new DictEntry("图像包 (.sff v1 vs v2) 与音频包 (.snd)", "format", "Win/1.0/1.1/IK-GO",
                "SFFv1(256色PCX) 与 SFFv2(PNG/32位真彩) 区别，以及 SND 音频格式支持",
                "SFFv1 仅支持 8-bit 索引色 PCX（依赖外部 .act 换色）；SFFv2 (1.0+/1.1/IK) 支持 RLE/LZ5/PNG8/PNG24/PNG32 真彩带透明通道，内置多套色表。\nIkemen GO 的 SND 除 WAV 外，还原生支持 OGG、MP3、FLAC 及 XM/MOD/IT 等音轨格式。",
                "; SFF 9000,0 = 小头像(25x25)\n; SFF 9000,1 = 大头像(120x140)", false));
        list.add(new DictEntry("地图与 3D 场景 (.def / .gltf / .glb)", "format", "Win/1.0/1.1/IK-GO",
                "2D 视差图层地图配置与 Ikemen GO 专属 3D 模型场景",
                "2D 地图包含 [Camera]、[PlayerInfo]、[Bound]、[StageInfo]、[BGdef] 与多个 [BG] 图层（delta 控制视差速度）。\nIkemen GO 支持 [Model] 直接加载 .gltf/.glb 3D 场景与动画骨骼。",
                "[BG Sky]\ntype = normal\nspriteno = 0, 0\nstart = 0, 0\ndelta = 0.2, 0.2", false));

        // --- 🔄 版本兼容差异 (WinMUGEN / 1.0 / 1.1 / IK-GO) ---
        list.add(new DictEntry("WinMUGEN → 1.0 → 1.1 → Ikemen GO 核心差异速查", "compat", "Win/1.0/1.1/IK-GO",
                "移植老角色/修 Bug 必看的跨版本兼容对照表",
                "1. AI 检测：Win 无 AILevel（靠 var(59) 伪指令）；1.0+/IK 原生支持 AILevel。\n2. 分辨率与缩放：Win 固定 320x240；1.0+ 引入 localcoord；1.1+ 支持 Camera Zoom 与 32位 PNG SFFv2。\n3. 语法严格度：Win 容忍多余逗号/缺参数；Ikemen GO 解析更严格，报错会明确提示行号。\n4. 联机与脚本：仅 Ikemen GO 原生支持网络联机(含实验性回滚)、ZSS、Lua 模块、Map 变量、3D 地图与 MP4 过场。",
                "; 跨版本兼容小技巧：\n; 在 .def 中声明 mugenversion = 1.1\n; IK 专属角色加 ikemenVersion = 1.0", false));
        list.add(new DictEntry("常见报错与崩溃排查指南", "compat", "Win/1.0/1.1/IK-GO",
                "快速定位 SFF 闪退、乱码、状态死循环、Helper 溢出等高频问题",
                "1. 日文/韩文路径或角色名乱码：用桌面【🔣 乱码解字板】按 Shift_JIS/EUC-KR 转存为 UTF-8。\n2. 人物无限卡死：检查 ChangeState 是否缺 AnimTime=0 或触发条件永真导致同帧死循环。\n3. 图像替换失败：SFFv1 只能替换 PCX，SFFv2 禁止 PCX 必须用 PNG。\n4. 选人界面不显示：检查 select.def 路径，可用【🗂️ DEF生成器】一键扫描。",
                "; 建议调试时在游戏内按 Ctrl+D 查看实时状态号与变量", false));

        return list;
    }

    private View buildDictionaryContent() {
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#141820"));
        final Handler uiHandler = new Handler(Looper.getMainLooper());
        final File cacheDir = new File(getContext().getFilesDir(), "ikemen_wiki_cache");
        if (!cacheDir.exists()) cacheDir.mkdirs();

        final List<DictEntry> allEntries = new ArrayList<>();
        allEntries.addAll(buildBuiltinDictionaryEntries());

        final java.util.Set<String> favSet = new HashSet<>(prefs.getStringSet("dict_favs", new HashSet<>()));
        final String[] activeCat = {"all"};
        final String[] activeVer = {"all"};
        final String[] queryText = {""};

        // 顶部搜索与官方同步栏
        LinearLayout headerArea = new LinearLayout(getContext());
        headerArea.setOrientation(LinearLayout.VERTICAL);
        headerArea.setPadding((int)(8*density), (int)(8*density), (int)(8*density), (int)(4*density));

        LinearLayout searchRow = new LinearLayout(getContext());
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        final EditText searchInput = createInput(L("🔍 搜控制器/触发器/ZSS/Lua/参数/中文说明..."), "");
        searchInput.setSingleLine(true);
        Button btnClearQ = createButton("✕", "#334155");
        btnClearQ.setPadding((int)(10*density), (int)(6*density), (int)(10*density), (int)(6*density));
        Button btnSync = createButton(L("🔄 同步官方仓库"), "#0284C7");
        btnSync.setPadding((int)(10*density), (int)(6*density), (int)(10*density), (int)(6*density));
        LinearLayout.LayoutParams sBtnP = new LinearLayout.LayoutParams(-2, -2); sBtnP.setMargins((int)(5*density), 0, 0, 0);
        searchRow.addView(searchInput, new LinearLayout.LayoutParams(0, -2, 1f));
        searchRow.addView(btnClearQ, sBtnP);
        searchRow.addView(btnSync, sBtnP);
        headerArea.addView(searchRow);

        final TextView syncStatus = new TextView(getContext());
        String lastSyncTime = prefs.getString("dict_last_sync", "");
        syncStatus.setText(lastSyncTime.isEmpty()
                ? L("📚 已内置 Win/1.0/1.1/IK-GO 离线词库，点右上角可实时同步官方 GitHub 仓库全部最新 Wiki")
                : L("✅ 上次官方同步: ") + lastSyncTime);
        applyGlobalFontSettings(syncStatus, 0.82f, false);
        syncStatus.setTextColor(Color.parseColor("#94A3B8"));
        syncStatus.setPadding(0, (int)(4*density), 0, (int)(4*density));
        headerArea.addView(syncStatus);

        // 版本筛选胶囊
        HorizontalScrollView verScroll = new HorizontalScrollView(getContext()); verScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout verRow = new LinearLayout(getContext()); verRow.setOrientation(LinearLayout.HORIZONTAL);
        final String[] verKeys = {"all", "Win", "1.0", "1.1", "IK-GO"};
        final String[] verNames = {L("全部版本"), "WinMUGEN", "MUGEN 1.0", "MUGEN 1.1", "Ikemen GO"};
        final Button[] verBtns = new Button[verKeys.length];

        // 分类标签栏
        HorizontalScrollView catScroll = new HorizontalScrollView(getContext()); catScroll.setHorizontalScrollBarEnabled(false);
        catScroll.setTag("layout_toolbar");
        LinearLayout catRow = new LinearLayout(getContext()); catRow.setOrientation(LinearLayout.HORIZONTAL);
        final String[] catKeys = {"all", "fav", "sctrl", "trigger", "zss_lua", "format", "compat", "official"};
        final String[] catNames = {L("🔥 全部"), L("⭐ 收藏"), L("🎮 控制器"), L("⚡ 触发器"), L("🧬 ZSS/Lua"), L("📁 文件格式"), L("🔄 版本差异"), L("🌐 官方同步")};
        final Button[] catBtns = new Button[catKeys.length];

        ScrollView listScroll = new ScrollView(getContext());
        listScroll.setTag("layout_preview");
        final LinearLayout listContainer = new LinearLayout(getContext());
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listScroll.addView(listContainer, new FrameLayout.LayoutParams(-1, -2));

        final Runnable[] refreshDictUI = {null};

        // 详情阅读弹窗
        final java.util.List<DictEntry> dummyRef = allEntries;
        final Runnable updateFilterColors = () -> {
            for (int i = 0; i < verKeys.length; i++) {
                verBtns[i].setBackgroundColor(activeVer[0].equals(verKeys[i]) ? Color.parseColor("#0EA5E9") : Color.parseColor("#252D3D"));
            }
            for (int i = 0; i < catKeys.length; i++) {
                catBtns[i].setBackgroundColor(activeCat[0].equals(catKeys[i]) ? Color.parseColor("#6366F1") : Color.parseColor("#252D3D"));
            }
        };

        LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(-2, -2);
        pillLp.setMargins(0, (int)(3*density), (int)(5*density), (int)(3*density));
        for (int i = 0; i < verKeys.length; i++) {
            final int idx = i;
            verBtns[i] = createButton(verNames[i], "#252D3D");
            verBtns[i].setPadding((int)(10*density), (int)(4*density), (int)(10*density), (int)(4*density));
            verBtns[i].setOnClickListener(v -> { activeVer[0] = verKeys[idx]; updateFilterColors.run(); refreshDictUI[0].run(); });
            verRow.addView(verBtns[i], pillLp);
        }
        verScroll.addView(verRow);
        headerArea.addView(verScroll);

        for (int i = 0; i < catKeys.length; i++) {
            final int idx = i;
            catBtns[i] = createButton(catNames[i], "#252D3D");
            catBtns[i].setPadding((int)(10*density), (int)(5*density), (int)(10*density), (int)(5*density));
            catBtns[i].setOnClickListener(v -> { activeCat[0] = catKeys[idx]; updateFilterColors.run(); refreshDictUI[0].run(); });
            catRow.addView(catBtns[i], pillLp);
        }
        catScroll.addView(catRow);
        headerArea.addView(catScroll);

        root.addView(headerArea);
        LinearLayout.LayoutParams lsLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lsLp.setMargins((int)(8*density), (int)(4*density), (int)(8*density), (int)(8*density));
        root.addView(listScroll, lsLp);

        // 读取本地已缓存的官方 Wiki Markdown
        Runnable loadCachedOfficialDocs = () -> {
            List<DictEntry> toKeep = new ArrayList<>();
            for (DictEntry e : allEntries) { if (!e.isOfficialSync) toKeep.add(e); }
            allEntries.clear();
            allEntries.addAll(toKeep);
            File[] cached = cacheDir.listFiles();
            if (cached != null) {
                Arrays.sort(cached, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                for (File cf : cached) {
                    if (!cf.getName().endsWith(".md") && !cf.getName().endsWith(".def") && !cf.getName().endsWith(".txt")) continue;
                    try {
                        StringBuilder sb = new StringBuilder();
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(cf), "UTF-8"))) {
                            String line;
                            while ((line = br.readLine()) != null) sb.append(line).append("\n");
                        }
                        String pageName = cf.getName().replaceAll("\\.(md|def|txt)$", "").replace("-", " ");
                        allEntries.addAll(parseMarkdownToDictEntries(pageName, sb.toString()));
                    } catch (Exception ignored) {}
                }
            }
        };
        loadCachedOfficialDocs.run();

        refreshDictUI[0] = () -> {
            listContainer.removeAllViews();
            String q = queryText[0].trim().toLowerCase();
            int matchCount = 0;
            for (final DictEntry item : allEntries) {
                if (activeCat[0].equals("fav")) {
                    if (!favSet.contains(item.title)) continue;
                } else if (!activeCat[0].equals("all") && !activeCat[0].equals(item.category)) {
                    continue;
                }
                if (!activeVer[0].equals("all") && !item.versions.contains(activeVer[0])) continue;
                if (!q.isEmpty()) {
                    boolean hit = item.title.toLowerCase().contains(q)
                            || item.summary.toLowerCase().contains(q)
                            || item.detail.toLowerCase().contains(q)
                            || item.example.toLowerCase().contains(q)
                            || item.versions.toLowerCase().contains(q);
                    if (!hit) continue;
                }
                matchCount++;
                if (matchCount > 250) break; // 防止一次渲染过多卡片卡顿

                LinearLayout card = new LinearLayout(getContext());
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding((int)(11*density), (int)(9*density), (int)(11*density), (int)(9*density));
                GradientDrawable cbg = new GradientDrawable();
                cbg.setColor(Color.parseColor("#1E2430"));
                cbg.setCornerRadius(8f * density);
                cbg.setStroke((int)(1*density), Color.parseColor(item.isOfficialSync ? "#0369A1" : "#334155"));
                card.setBackground(cbg);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
                clp.setMargins(0, 0, 0, (int)(7*density));

                LinearLayout titleRow = new LinearLayout(getContext());
                titleRow.setOrientation(LinearLayout.HORIZONTAL);
                titleRow.setGravity(Gravity.CENTER_VERTICAL);
                TextView tvTitle = new TextView(getContext());
                tvTitle.setText((favSet.contains(item.title) ? "⭐ " : "") + item.title);
                applyGlobalFontSettings(tvTitle, 1.0f, true);
                tvTitle.setTextColor(Color.parseColor("#F8FAFC"));
                titleRow.addView(tvTitle, new LinearLayout.LayoutParams(0, -2, 1f));

                TextView tvVer = new TextView(getContext());
                tvVer.setText(" " + item.versions + " ");
                applyGlobalFontSettings(tvVer, 0.75f, true);
                tvVer.setTextColor(Color.parseColor("#38BDF8"));
                GradientDrawable vbg = new GradientDrawable();
                vbg.setColor(Color.parseColor("#1E3A5F"));
                vbg.setCornerRadius(5f * density);
                tvVer.setBackground(vbg);
                tvVer.setPadding((int)(5*density), (int)(2*density), (int)(5*density), (int)(2*density));
                titleRow.addView(tvVer);
                card.addView(titleRow);

                TextView tvSum = new TextView(getContext());
                tvSum.setText(item.summary);
                applyGlobalFontSettings(tvSum, 0.85f, false);
                tvSum.setTextColor(Color.parseColor("#CBD5E1"));
                tvSum.setPadding(0, (int)(4*density), 0, 0);
                tvSum.setMaxLines(2);
                tvSum.setEllipsize(android.text.TextUtils.TruncateAt.END);
                card.addView(tvSum);

                card.setOnClickListener(v -> {
                    final Dialog dd = new Dialog(getContext());
                    if (dd.getWindow() != null) dd.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                    ScrollView dsv = new ScrollView(getContext());
                    LinearLayout dbox = new LinearLayout(getContext());
                    dbox.setOrientation(LinearLayout.VERTICAL);
                    dbox.setPadding((int)(16*density), (int)(16*density), (int)(16*density), (int)(16*density));
                    GradientDrawable dbg = new GradientDrawable();
                    dbg.setColor(Color.parseColor("#F8171C26"));
                    dbg.setCornerRadius(12f * density);
                    dbg.setStroke((int)(1.5f * density), Color.parseColor("#38BDF8"));
                    dbox.setBackground(dbg);

                    TextView dt = new TextView(getContext()); dt.setText(item.title); applyGlobalFontSettings(dt, 1.18f, true); dt.setTextColor(Color.WHITE); dbox.addView(dt);
                    TextView dv = new TextView(getContext()); dv.setText(L("适用引擎版本: ") + item.versions + (item.isOfficialSync ? L("  |  来源: 官方 GitHub 仓库") : L("  |  来源: 内置全量词库")));
                    applyGlobalFontSettings(dv, 0.82f, false); dv.setTextColor(Color.parseColor("#38BDF8")); dv.setPadding(0, (int)(4*density), 0, (int)(10*density)); dbox.addView(dv);

                    TextView dbody = new TextView(getContext()); dbody.setText(item.detail.isEmpty() ? item.summary : item.detail);
                    applyGlobalFontSettings(dbody, 0.94f, false); dbody.setTextColor(Color.parseColor("#E2E8F0")); dbody.setTextIsSelectable(true);
                    dbox.addView(dbody);

                    if (item.example != null && !item.example.isEmpty()) {
                        dbox.addView(createSubTitle(L("💻 语法 / 代码示例:")));
                        TextView codeTv = new TextView(getContext());
                        codeTv.setText(item.example);
                        codeTv.setTypeface(Typeface.MONOSPACE);
                        codeTv.setTextColor(Color.parseColor("#86EFAC"));
                        codeTv.setTextSize(11.5f);
                        codeTv.setTextIsSelectable(true);
                        codeTv.setPadding((int)(10*density), (int)(8*density), (int)(10*density), (int)(8*density));
                        GradientDrawable codeBg = new GradientDrawable(); codeBg.setColor(Color.parseColor("#0B0F17")); codeBg.setCornerRadius(6f*density); codeBg.setStroke(1, Color.parseColor("#334155"));
                        codeTv.setBackground(codeBg);
                        dbox.addView(codeTv, new LinearLayout.LayoutParams(-1, -2));
                    }

                    LinearLayout actRow = new LinearLayout(getContext()); actRow.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams arp = new LinearLayout.LayoutParams(0, -2, 1f); arp.setMargins((int)(3*density), (int)(12*density), (int)(3*density), 0);
                    Button bFav = createButton(favSet.contains(item.title) ? L("★ 取消收藏") : L("⭐ 收藏"), "#B45309");
                    bFav.setOnClickListener(fv -> {
                        if (favSet.contains(item.title)) favSet.remove(item.title); else favSet.add(item.title);
                        prefs.edit().putStringSet("dict_favs", new HashSet<>(favSet)).apply();
                        bFav.setText(favSet.contains(item.title) ? L("★ 取消收藏") : L("⭐ 收藏"));
                        refreshDictUI[0].run();
                    });
                    actRow.addView(bFav, arp);

                    if (item.example != null && !item.example.isEmpty()) {
                        Button bCopy = createButton(L("📋 复制代码"), "#0284C7");
                        bCopy.setOnClickListener(cv -> {
                            try {
                                android.content.ClipboardManager cm = (android.content.ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                                if (cm != null) {
                                    cm.setPrimaryClip(android.content.ClipData.newPlainText("ik_dict", item.example));
                                    Toast.makeText(getContext(), L("✅ 代码示例已复制到剪贴板"), Toast.LENGTH_SHORT).show();
                                }
                            } catch (Exception ignored) {}
                        });
                        actRow.addView(bCopy, arp);
                    }
                    Button bClose = createButton(L("关闭"), "#475569"); bClose.setOnClickListener(cv -> dd.dismiss());
                    actRow.addView(bClose, arp);
                    dbox.addView(actRow);

                    dsv.addView(dbox);
                    int sw = getContext().getResources().getDisplayMetrics().widthPixels;
                    dd.setContentView(dsv, new ViewGroup.LayoutParams((int)(sw * 0.88f), -2));
                    dd.show();
                });

                listContainer.addView(card, clp);
            }
            if (matchCount == 0) {
                TextView empty = new TextView(getContext());
                empty.setText(L("未找到匹配词条，可换个关键词或点击右上角【🔄 同步官方仓库】获取全部最新资料"));
                applyGlobalFontSettings(empty, 0.92f, false);
                empty.setTextColor(Color.parseColor("#94A3B8"));
                empty.setPadding((int)(10*density), (int)(20*density), (int)(10*density), (int)(10*density));
                listContainer.addView(empty);
            }
        };

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { queryText[0] = s.toString(); refreshDictUI[0].run(); }
        });
        btnClearQ.setOnClickListener(v -> { searchInput.setText(""); queryText[0] = ""; refreshDictUI[0].run(); });

        // 🔄 实时同步官方 GitHub 仓库 (ikemen-engine/Ikemen-GO Wiki + system.base.def)
        btnSync.setOnClickListener(v -> {
            btnSync.setEnabled(false);
            syncStatus.setText(L("⏳ 正在连接 GitHub 官方仓库读取文档目录 (_Sidebar.md)..."));
            new Thread(() -> {
                java.util.LinkedHashSet<String> wikiPages = new java.util.LinkedHashSet<>();
                // 核心官方 Wiki 必同步清单
                String[] defaults = new String[] {
                    "Home", "State-controllers-(new)", "State-controllers-(changed)",
                    "Triggers-(new)", "Triggers-(changed)", "Lua",
                    "Character-features", "Stage-features", "Screenpack-features",
                    "Miscellaneous-info", "Command-line-arguments"
                };
                for (String p : defaults) wikiPages.add(p);

                // 动态解析 _Sidebar.md 自动发现官方仓库新增的所有 Wiki 页面
                String sidebarMd = httpGetWithFallback("[https://raw.githubusercontent.com/wiki/ikemen-engine/Ikemen-GO/_Sidebar.md](https://raw.githubusercontent.com/wiki/ikemen-engine/Ikemen-GO/_Sidebar.md)");
                if (sidebarMd != null) {
                    java.util.regex.Matcher m1 = java.util.regex.Pattern.compile("\\[\\[([^\\]|]+)(?:\\|([^\\]]+))?\\]\\]").matcher(sidebarMd);
                    while (m1.find()) {
                        String slug = (m1.group(2) != null ? m1.group(2) : m1.group(1)).trim().replace(" ", "-");
                        if (!slug.isEmpty() && !slug.startsWith("http")) wikiPages.add(slug);
                    }
                    java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("/wiki/([A-Za-z0-9_\\-\\(\\)%]+)").matcher(sidebarMd);
                    while (m2.find()) {
                        String slug = m2.group(1).trim();
                        if (!slug.isEmpty()) wikiPages.add(slug);
                    }
                }

                int total = wikiPages.size() + 1;
                int done = 0, ok = 0;
                for (String slug : wikiPages) {
                    done++;
                    final int curD = done; final String curSlug = slug;
                    uiHandler.post(() -> syncStatus.setText(L("⏳ 正在同步官方 Wiki (") + curD + "/" + total + "): " + curSlug));
                    String md = httpGetWithFallback("[https://raw.githubusercontent.com/wiki/ikemen-engine/Ikemen-GO/](https://raw.githubusercontent.com/wiki/ikemen-engine/Ikemen-GO/)" + slug + ".md");
                    if (md != null && md.length() > 30) {
                        try {
                            String safeName = slug.replaceAll("[^A-Za-z0-9_\\-\\(\\)]", "_") + ".md";
                            try (FileOutputStream fos = new FileOutputStream(new File(cacheDir, safeName))) {
                                fos.write(md.getBytes("UTF-8"));
                            }
                            ok++;
                        } catch (Exception ignored) {}
                    }
                }

                // 同步官方 system.base.def 参数参考注释
                final int sysStep = total;
                uiHandler.post(() -> syncStatus.setText(L("⏳ 正在同步官方配置参数 (") + sysStep + "/" + total + "): system.base.def"));
                String sysDef = httpGetWithFallback("[https://raw.githubusercontent.com/ikemen-engine/Ikemen-GO/main/data/system.base.def](https://raw.githubusercontent.com/ikemen-engine/Ikemen-GO/main/data/system.base.def)");
                if (sysDef == null) sysDef = httpGetWithFallback("[https://raw.githubusercontent.com/ikemen-engine/Ikemen-GO/develop/data/system.base.def](https://raw.githubusercontent.com/ikemen-engine/Ikemen-GO/develop/data/system.base.def)");
                if (sysDef != null && sysDef.length() > 100) {
                    try (FileOutputStream fos = new FileOutputStream(new File(cacheDir, "Official_system_base_def.txt"))) {
                        fos.write(("## system.base.def 官方完整参数参考\n```ini\n" + sysDef + "\n```").getBytes("UTF-8"));
                    } catch (Exception ignored) {}
                    ok++;
                }

                final int okCount = ok;
                final String nowStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(new java.util.Date());
                uiHandler.post(() -> {
                    btnSync.setEnabled(true);
                    if (okCount > 0) {
                        prefs.edit().putString("dict_last_sync", nowStr + " (" + okCount + " docs)").apply();
                        loadCachedOfficialDocs.run();
                        syncStatus.setText(L("✅ 官方仓库同步完成！已更新 ") + okCount + L(" 份文档，共收录 ") + allEntries.size() + L(" 条词条"));
                        Toast.makeText(getContext(), L("✅ 官方资料同步成功！离线也可秒搜"), Toast.LENGTH_LONG).show();
                        refreshDictUI[0].run();
                    } else {
                        syncStatus.setText(L("❌ 同步失败：请检查网络连接后重试（内置离线词库仍可正常使用）"));
                        Toast.makeText(getContext(), L("❌ 无法连接 GitHub，请检查网络"), Toast.LENGTH_SHORT).show();
                    }
                });
            }).start();
        });

        updateFilterColors.run();
        refreshDictUI[0].run();
        return root;
    }

// ================= 【机制注入】多语言补丁快捷助手 =================
    // 自动连接到 DynamicGamepadView 的翻译引擎
    private static String L(String text) {
        return DynamicGamepadView.L(text);
    }
} // 类的结尾
