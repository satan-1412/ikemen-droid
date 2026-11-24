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
        createDesktopIcon("char_editor", "🥷", L("角色编辑器"));
        createDesktopIcon("ikemen_dict", "📚", L("引擎词典"));
        createDesktopIcon("ai_chat", "🤖", L("AI对话工坊"));
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
                            else if (id.equals("char_editor")) openCharacterEditorWindow();
                            else if (id.equals("ikemen_dict")) openAppWindow(L("📚 MUGEN/IK 全能词典"), buildDictionaryContent(), null);
                            else if (id.equals("ai_chat")) openAppWindow(L("🤖 AI对话工坊"), buildAiChatContent(), null);
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
        if (windowTitle.contains(L("角色编辑器")) || windowTitle.contains("CharEditor")) return "char_editor";
        if (windowTitle.contains(L("AI对话工坊")) || windowTitle.contains("AI")) return "ai_chat";
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
                                else if (targetType == 15) {
                                    if (absPath.toLowerCase().endsWith(".def")) {
                                        boolean isStage = false;
                                        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f))) {
                                            String line;
                                            while ((line = br.readLine()) != null) {
                                                String lower = line.toLowerCase().trim();
                                                if (lower.startsWith("[stageinfo]") || lower.startsWith("[bgdef]")) { isStage = true; break; }
                                            }
                                        } catch (Exception ignored) {}
                                        if (!isStage) {
                                            if (listener != null) listener.onFileSelected(f);
                                            pDialog.dismiss();
                                        } else {
                                            Toast.makeText(getContext(), L("❌ 这是地图配置文件，请选择角色 .def 文件！"), Toast.LENGTH_LONG).show();
                                        }
                                    } else {
                                        Toast.makeText(getContext(), L("❌ 请选择角色 .def 配置文件"), Toast.LENGTH_SHORT).show();
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
        int type; // 1 = Clsn1 打击框 (红), 2 = Clsn2 防御框 (蓝), 3 = 无敌框 (金/NotHitBy)
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
        // 工具模式: 0=移动缩放画布, 1=绘制打击框(Clsn1), 2=绘制防御框(Clsn2), 3=绘制无敌框(NotHitBy)
        final int[] editMode = {1};
        // 绘制形状: 0=矩形, 1=椭圆, 2=多边形点连, 3=手绘自动闭合, 4=点删单框
        final int[] shapeMode = {0};
        final int[] shapeSlices = {8};
        final int[] strokeSeq = {1};
        final boolean[] showClsn1 = {true};
        final boolean[] showClsn2 = {true};
        final boolean[] showInvul = {true};
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
            private final Paint fill3 = new Paint();
            private final Paint stroke3 = new Paint(Paint.ANTI_ALIAS_FLAG);
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
                fill3.setColor(Color.argb(66, 250, 204, 21)); fill3.setStyle(Paint.Style.FILL);
                stroke3.setColor(Color.parseColor("#FACC15")); stroke3.setStyle(Paint.Style.STROKE); stroke3.setStrokeWidth(Math.max(2.2f, 1.8f * density));
                stroke3.setPathEffect(new android.graphics.DashPathEffect(new float[]{7f * density, 5f * density}, 0));
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
                    int c1Idx = 0, c2Idx = 0, invIdx = 0;
                    for (ClsnBox b : boxes) {
                        if (b.type == 1 && !showClsn1[0]) continue;
                        if (b.type == 2 && !showClsn2[0]) continue;
                        if (b.type == 3 && !showInvul[0]) continue;
                        float l = mugenToScreenX(b.x1), t = mugenToScreenY(b.y1);
                        float r = mugenToScreenX(b.x2), bt = mugenToScreenY(b.y2);
                        Paint fP = (b.type == 1) ? fill1 : (b.type == 3 ? fill3 : fill2);
                        Paint sP = (b.type == 1) ? stroke1 : (b.type == 3 ? stroke3 : stroke2);
                        canvas.drawRect(l, t, r, bt, fP);
                        canvas.drawRect(l, t, r, bt, sP);
                        String tag = (b.type == 1) ? ("C1[" + (c1Idx++) + "]") : (b.type == 3 ? ("INV[" + (invIdx++) + "]") : ("C2[" + (c2Idx++) + "]"));
                        canvas.drawText(tag, l + 2f * density, t + 11f * density, labelPaint);
                    }
                }

                Paint activeFill = (editMode[0] == 1) ? fill1 : (editMode[0] == 3 ? fill3 : fill2);
                Paint activeStroke = (editMode[0] == 1) ? stroke1 : (editMode[0] == 3 ? stroke3 : stroke2);

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
                                    String tLbl = (nb.type == 1) ? L("🟥 已添加打击框 Clsn1: ") : (nb.type == 3 ? L("🟨 已添加无敌框 NotHitBy: ") : L("🟦 已添加防御框 Clsn2: "));
                                    statusBar.setText(tLbl + nb.x1 + ", " + nb.y1 + ", " + nb.x2 + ", " + nb.y2);
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
                        int c1 = 0, c2 = 0, c3 = 0;
                        if (list != null) { for (ClsnBox b : list) { if (b.type == 1) c1++; else if (b.type == 3) c3++; else c2++; } }
                        statusBar.setText(L("帧: ") + (frameIdx[0] + 1) + "/" + groupFrames.size() + " | [" + tf.group + "," + tf.item + "] | " + L("打击:") + c1 + " | " + L("防御:") + c2 + " | " + L("无敌:") + c3);
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
        Button btnModeInv = createButton(L("🟨 无敌框(NotHitBy)"), "#B45309");
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
        Button btnToggleInv = createButton(L("👁️ 无敌:开"), "#A16207");
        Button btnExportAir = createButton(L("📋 生成/导出 AIR"), "#2E7D32");

        btnClosePoly.setVisibility(View.GONE);

        Runnable updateModeHighlight = () -> {
            btnModePan.setBackgroundColor(editMode[0] == 0 ? Color.parseColor("#0284C7") : Color.parseColor("#334155"));
            btnModeAtk.setBackgroundColor(editMode[0] == 1 ? Color.parseColor("#EF4444") : Color.parseColor("#5D2020"));
            btnModeDef.setBackgroundColor(editMode[0] == 2 ? Color.parseColor("#0EA5E9") : Color.parseColor("#1A3A5C"));
            btnModeInv.setBackgroundColor(editMode[0] == 3 ? Color.parseColor("#EAB308") : Color.parseColor("#6B4E16"));
            btnClosePoly.setVisibility((editMode[0] != 0 && shapeMode[0] == 2) ? View.VISIBLE : View.GONE);
        };
        updateModeHighlight.run();

        btnModePan.setOnClickListener(v -> { editMode[0] = 0; updateModeHighlight.run(); });
        btnModeAtk.setOnClickListener(v -> { editMode[0] = 1; updateModeHighlight.run(); });
        btnModeDef.setOnClickListener(v -> { editMode[0] = 2; updateModeHighlight.run(); });
        btnModeInv.setOnClickListener(v -> { editMode[0] = 3; updateModeHighlight.run(); });

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
            Button bFullInv = createButton(L("🟨 整图外接无敌框 (标记本帧无敌)"), "#CA8A04");

            View.OnClickListener runDetect = dv -> {
                int targetType = (dv == bFullAtk || dv == bContourAtk) ? 1 : (dv == bFullInv ? 3 : 2);
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
                    String kindTxt = (targetType == 1) ? L(" 个打击框") : (targetType == 3 ? L(" 个无敌框") : L(" 个防御框"));
                    statusBar.setText(L("🪄 自动识别成功，已生成 ") + detected.size() + kindTxt);
                    clsnCanvas.invalidate();
                }
                ad.dismiss();
            };

            bFullDef.setOnClickListener(runDetect); bContourDef.setOnClickListener(runDetect);
            bFullAtk.setOnClickListener(runDetect); bContourAtk.setOnClickListener(runDetect);
            bFullInv.setOnClickListener(runDetect);
            al.addView(bFullDef, alp); al.addView(bContourDef, alp); al.addView(bFullAtk, alp); al.addView(bContourAtk, alp); al.addView(bFullInv, alp);
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
        btnToggleInv.setOnClickListener(v -> {
            showInvul[0] = !showInvul[0];
            btnToggleInv.setText(showInvul[0] ? L("👁️ 无敌:开") : L("👁️ 无敌:关"));
            clsnCanvas.invalidate();
        });

        btnExportAir.setOnClickListener(v -> {
            StringBuilder sb = new StringBuilder();
            StringBuilder invulCns = new StringBuilder();
            int actionNum = (curFrame[0] != null) ? curFrame[0].group : 0;
            sb.append("[Begin Action ").append(actionNum).append("]\n");
            boolean hasAny = false;
            int elemIdx = 0;
            for (java.util.Map.Entry<String, List<ClsnBox>> entry : frameBoxesMap.entrySet()) {
                List<ClsnBox> boxes = entry.getValue();
                if (boxes == null || boxes.isEmpty()) continue;
                hasAny = true;
                elemIdx++;
                List<ClsnBox> c1 = new ArrayList<>(), c2 = new ArrayList<>(), inv = new ArrayList<>();
                for (ClsnBox b : boxes) { if (b.type == 1) c1.add(b); else if (b.type == 3) inv.add(b); else c2.add(b); }
                if (!c1.isEmpty()) {
                    sb.append("Clsn1: ").append(c1.size()).append("\n");
                    for (int i = 0; i < c1.size(); i++) {
                        ClsnBox b = c1.get(i);
                        sb.append("  Clsn1[").append(i).append("] = ").append(b.x1).append(", ").append(b.y1).append(", ").append(b.x2).append(", ").append(b.y2).append("\n");
                    }
                }
                if (!inv.isEmpty()) {
                    sb.append("; [无敌帧 Elem ").append(elemIdx).append("] 已自动移除 Clsn2 受击防御框实现无敌\n");
                    invulCns.append("[State ").append(actionNum).append(", Invul_Elem").append(elemIdx).append("]\ntype = NotHitBy\ntrigger1 = AnimElem = ").append(elemIdx).append("\nvalue = SCA\ntime = 5\n\n");
                } else if (!c2.isEmpty()) {
                    sb.append("Clsn2: ").append(c2.size()).append("\n");
                    for (int i = 0; i < c2.size(); i++) {
                        ClsnBox b = c2.get(i);
                        sb.append("  Clsn2[").append(i).append("] = ").append(b.x1).append(", ").append(b.y1).append(", ").append(b.x2).append(", ").append(b.y2).append("\n");
                    }
                }
                sb.append(entry.getKey()).append(", 0, 0, 5\n\n");
            }
            if (invulCns.length() > 0) {
                sb.append("; === 配套 CNS 无敌帧控制器 (可直接粘贴到对应 Statedef) ===\n").append(invulCns.toString());
            }
            if (!hasAny) {
                Toast.makeText(getContext(), L("⚠️ 当前尚未绘制任何打击框、防御框或无敌框"), Toast.LENGTH_SHORT).show();
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
        bar.addView(btnModePan, bp); bar.addView(btnModeAtk, bp); bar.addView(btnModeDef, bp); bar.addView(btnModeInv, bp);
        bar.addView(btnShapeMode, bp); bar.addView(btnClosePoly, bp); bar.addView(btnAutoDetect, bp); bar.addView(btnPrecision, bp);
        bar.addView(btnUndo, bp); bar.addView(btnClear, bp);
        bar.addView(btnPrev, bp); bar.addView(btnNext, bp); bar.addView(btnGroup, bp);
        bar.addView(btnToggleC1, bp); bar.addView(btnToggleC2, bp); bar.addView(btnToggleInv, bp);
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
        String[] candidates = new String[] { rawUrl, "https://" + "ghfast.top/" + rawUrl };
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

    // 🌐 在线/离线混合中文翻译引擎（专用于将下载的英文官方 Wiki 翻译为简体中文并本地缓存）
    private String translateWikiToChinese(String englishText) {
        if (englishText == null || englishText.trim().isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        String[] lines = englishText.split("\n");
        StringBuilder chunk = new StringBuilder();
        List<String> chunks = new ArrayList<>();
        for (String line : lines) {
            if (chunk.length() + line.length() > 1100 && chunk.length() > 0) {
                chunks.add(chunk.toString());
                chunk.setLength(0);
            }
            chunk.append(line).append("\n");
        }
        if (chunk.length() > 0) chunks.add(chunk.toString());

        boolean anyOnlineOk = false;
        for (String part : chunks) {
            String tr = translateChunkGoogle(part);
            if (tr == null || tr.trim().isEmpty()) tr = translateChunkMyMemory(part);
            if (tr != null && !tr.trim().isEmpty()) {
                anyOnlineOk = true;
                out.append(tr.trim()).append("\n");
            } else {
                out.append(offlineGlossaryTranslate(part)).append("\n");
            }
        }
        String result = out.toString().trim();
        if (!anyOnlineOk) {
            result = L("【离线术语对照译文（联网后点击可获取完整机器翻译）】\n") + result;
        }
        return result;
    }

    private String translateChunkGoogle(String text) {
        java.net.HttpURLConnection conn = null;
        try {
            String q = java.net.URLEncoder.encode(text, "UTF-8");
            String urlStr = "https://" + "translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=zh-CN&dt=t&q=" + q;
            conn = (java.net.HttpURLConnection) new java.net.URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (conn.getResponseCode() == 200) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                    String l; while ((l = br.readLine()) != null) sb.append(l);
                }
                org.json.JSONArray rootArr = new org.json.JSONArray(sb.toString());
                org.json.JSONArray segs = rootArr.getJSONArray(0);
                StringBuilder tr = new StringBuilder();
                for (int i = 0; i < segs.length(); i++) {
                    org.json.JSONArray item = segs.getJSONArray(i);
                    if (!item.isNull(0)) tr.append(item.getString(0));
                }
                return tr.toString();
            }
        } catch (Throwable ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private String translateChunkMyMemory(String text) {
        java.net.HttpURLConnection conn = null;
        try {
            String sub = text.length() > 480 ? text.substring(0, 480) : text;
            String q = java.net.URLEncoder.encode(sub, "UTF-8");
            String urlStr = "https://" + "api.mymemory.translated.net/get?q=" + q + "&langpair=en|zh-CN";
            conn = (java.net.HttpURLConnection) new java.net.URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "IkemenDroid-Dict/1.0");
            if (conn.getResponseCode() == 200) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                    String l; while ((l = br.readLine()) != null) sb.append(l);
                }
                JSONObject obj = new JSONObject(sb.toString());
                JSONObject rd = obj.optJSONObject("responseData");
                if (rd != null) {
                    String t = rd.optString("translatedText", "");
                    if (!t.isEmpty() && !t.contains("MYMEMORY WARNING")) return t;
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private String offlineGlossaryTranslate(String text) {
        String s = text;
        String[][] pairs = new String[][] {
            {"Required parameters:", "【必填参数】："}, {"Optional parameters:", "【可选参数】："},
            {"Return type:", "【返回类型】："}, {"Arguments:", "【参数列表】："},
            {"Example:", "【代码示例】："}, {"Examples:", "【代码示例】："},
            {"Details:", "【详细说明】："}, {"Notes:", "【注意事项】："}, {"Note:", "【注意】："},
            {"Defaults to ", "默认值为 "}, {"(nightly build only)", "（仅 Nightly 开发版）"},
            {"Valid values are", "有效取值包括"}, {"boolean int (1 or 0)", "布尔整数(1或0)"},
            {"Specifies ", "指定"}, {"Returns ", "返回"}, {"Sets ", "设置"}, {"Changes ", "修改"}
        };
        for (String[] p : pairs) s = s.replace(p[0], p[1]);
        return s;
    }

    private List<DictEntry> buildBuiltinDictionaryEntries() {
        List<DictEntry> list = new ArrayList<>();
        addDictSctrlCombat(list);
        addDictSctrlMovement(list);
        addDictSctrlEntities(list);
        addDictSctrlVisualAudio(list);
        addDictSctrlSystem(list);
        addDictIkemenSctrlsDetailed(list);
        addDictMugenTriggersDetailed(list);
        addDictIkemenTriggersDetailed(list);
        addDictZssAndLuaDetailed(list);
        addDictFormatsAndFilesDetailed(list);
        addDictCommonStatesDetailed(list);
        addDictCompatAndGuidesDetailed(list);
        return list;
    }

    private void addDictSctrlCombat(List<DictEntry> list) {
        list.add(new DictEntry("HitDef (攻击判定定义)", "sctrl", "Win/1.0/1.1/1.1b1/IK-GO",
                "定义招式攻击判定全部属性：伤害、防损、顿帧、硬直、击飞、倒地、火花与音效",
                "【功能定位】\n当角色当前帧存在红框(Clsn1)且与对手蓝框(Clsn2)重叠时，引擎按本控制器参数结算命中或防御。\n\n"
                + "【必填参数】\n"
                + "• attr = 姿态, 类别：姿态填 S(站)/C(蹲)/A(空)；类别填 NA/SA/HA(普/必/超打击)、NT/ST/HT(投)、NP/SP/HP(飞道)。\n\n"
                + "【核心可选参数详解】\n"
                + "• hitflag = MAFDP+-：可命中的目标状态（M站蹲、A空、F浮空、D倒地、P飞道；+仅连段中可中、-不可连段中命中）。默认 MAF。\n"
                + "• guardflag = H/L/M/A：可防御方式（H仅站防/中段、L仅蹲防/下段、M站蹲皆可、A空防；留空=不可防御）。\n"
                + "• damage = hit_dmg, guard_dmg：命中伤害与防御磨血伤害（默认 0, 0）。\n"
                + "• animtype = Light/Medium/Hard/Back/Up/DiagUp：对手受击动作强度（决定播放 5000/5001/5002 等受击动画）。\n"
                + "• pausetime = p1_pause, p2_shake：攻方顿帧与守方受击震动帧（默认 0, 0）。\n"
                + "• sparkno = [F]动作号 / guard.sparkno = [F]动作号：命中/防御火花动画（不加S默认调用 fightfx.air，加 S 调用自身 AIR，-1 不显示）。\n"
                + "• sparkxy = x, y：火花偏移坐标（相对对手受击点）。\n"
                + "• hitsound = [S/F]组, 号 / guardsound = [S/F]组, 号：命中/防御音效。\n"
                + "• ground.type = High/Low/Trip/None：地面受击部位（Trip 为扫腿绊倒）。\n"
                + "• ground.slidetime / ground.hittime：地面滑退帧数 / 受击硬直总帧数。\n"
                + "• ground.velocity = vx, vy / air.velocity = vx, vy：命中地面/空中对手赋予的初速度。\n"
                + "• guard.slidetime / guard.hittime / guard.ctrltime / guard.velocity：被防御时的滑退、硬直、恢复控制时间与击退速度。\n"
                + "• fall = 0/1：是否击倒对手；fall.recover = 0/1(能否受身)；fall.recovertime(多久后可受身)；fall.damage(砸地追加伤害)。\n"
                + "• getpower = hit, guard / givepower = hit, guard：攻方增气 / 守方受击增气。\n"
                + "• priority = 等级(1~7), Hit/Miss/Dodge：相杀优先级（数字越大越优先）。\n"
                + "• p1stateno / p2stateno：命中后强切我方/对手状态（投技核心）。\n"
                + "• id / chainid / nochainid：目标标记 ID 与连段互斥控制。\n\n"
                + "【Ikemen GO 专属扩展参数】\n"
                + "• dizzypoints / guardpoints / redlife / score：直接指定眩晕值、破防值、红血量与积分。\n"
                + "• sparkscale / sparkangle / guard.sparkscale / guard.sparkangle：直接缩放与旋转打击火花。\n"
                + "• forcecrouch = 1(强制蹲姿)、keepstate = 1(扣血不切对手状态)、ignorereversaldef = 1(无视当身)、down.recovertime(硬倒地时长)、p2clsncheck(指定撞击 Size/Clsn2 盒)。",
                "[State 200, LightPunch]\ntype = HitDef\ntrigger1 = AnimElem = 3\nattr = S, NA\nhitflag = MAF\nguardflag = MA\ndamage = 28, 0\nanimtype = Light\npausetime = 8, 8\nsparkno = 0\nsparkxy = -10, -76\nhitsound = 5, 0\nguardsound = 6, 0\nground.type = High\nground.slidetime = 10\nground.hittime = 12\nground.velocity = -4.2\nair.velocity = -2.5, -3.5\ngetpower = 40, 15\ngivepower = 20, 10", false));

        list.add(new DictEntry("Projectile (内置飞行道具)", "sctrl", "Win/1.0/1.1/1.1b1/IK-GO",
                "发射引擎托管的飞行道具（自带飞行、命中、消弹与相杀逻辑）",
                "【功能定位】\n适合波动拳、直线气功等常规飞行道具；复杂轨迹/多段独立状态请改用 Helper。\n\n"
                + "【核心专属参数详解】\n"
                + "• ProjID = id：飞行道具编号（供 NumProjID / ProjHit 等触发器检测）。\n"
                + "• projanim = 动作号：飞行动画（必填）。\n"
                + "• projhitanim / projremanim / projcancelanim：命中时 / 消失时 / 相杀时播放的动画（-1 跳过）。\n"
                + "• projscale = sx, sy：缩放比例（默认 1, 1）。\n"
                + "• projremove = 0/1：命中后是否立即销毁（默认 1；设 0 可做穿透激光）。\n"
                + "• projremovetime = 帧数：存活上限帧（-1 直到飞出屏幕）。\n"
                + "• projhits = 段数 / projmisstime = 间隔帧：多段飞行道具总命中段数与每段间隔。\n"
                + "• projpriority = 等级：对波相杀优先级（默认 1，高优先级吞掉低优先级并减 1）。\n"
                + "• offset = x, y / postype：发射点相对偏移（默认 p1）。\n"
                + "• velocity = vx, vy / remvelocity = vx, vy / accel = ax, ay / velmul = mx, my：飞行初速、消散速度、加速度与每帧倍率。\n"
                + "• projedgebound / projstagebound：飞出屏幕/地图边界多少像素后销毁。\n"
                + "• 其余命中参数完全兼容 HitDef（attr、damage、guardflag、pausetime 等）。\n\n"
                + "【Ikemen GO 扩展】\n支持 ModifyProjectile 动态修改、projangle/xangle/yangle、shader.* 着色器，以及有 ikemenVersion 时正式生效 chainID/nochainID。",
                "[State 1000, Hadouken]\ntype = Projectile\ntrigger1 = AnimElem = 4\nProjID = 1000\nprojanim = 1005\nprojhitanim = 1006\nprojremanim = 1006\nprojpriority = 1\noffset = 48, -62\nvelocity = 6.5, 0\nattr = S, SP\ndamage = 60, 10\nguardflag = MA\npausetime = 0, 10\nground.hittime = 16\nground.velocity = -6", false));

        list.add(new DictEntry("NotHitBy (无敌属性设置)", "sctrl", "Win/1.0/1.1/1.1b1/IK-GO",
                "使角色在指定帧数内免疫特定类别的攻击（全无敌/防空/免投/穿波核心）",
                "【功能定位】\n赋予角色无敌时间，常用于升龙拳起手、起身无敌(State 5120)、闪避翻滚与超杀暗屏。\n\n"
                + "【参数详解】\n"
                + "• value = 姿态, 攻击类型：免疫的攻击组合。\n  - 姿态：S(站)、C(蹲)、A(空) 或组合 SCA。\n  - 类别：AA(所有打击)、AT(所有投技)、AP(所有飞道) 或精细组合 NA/SA/HA/NT/ST/HT/NP/SP/HP。\n  - 只写 SCA 表示全无敌；写 ,AT 表示全姿态免疫投技。\n"
                + "• value2 = 姿态, 类型：叠加第二套无敌规则（与 value 取并集，不会互相覆盖）。\n"
                + "• time = 帧数：持续时间（默认 1 帧）。\n\n"
                + "【Ikemen GO 扩展】\n支持 8 个独立槽位（slot = 0~7 + attr 语法）、stack = 1 叠加、clsn.group/clsn.index（仅对指定蓝框无敌）、playerno/playerid（仅对指定对手无敌）。",
                "[State 1100, AntiAirInvul]\ntype = NotHitBy\ntrigger1 = Time = 0\nvalue = , AA\nvalue2 = , AT\ntime = 10", false));

        list.add(new DictEntry("HitBy (受击白名单设置)", "sctrl", "Win/1.0/1.1/1.1b1/IK-GO",
                "限定角色“仅可被”指定属性攻击命中（与 NotHitBy 相反）",
                "【功能定位】\n当需要“只吃投技、免疫其他一切攻击”或“只吃下段攻击”时，用 HitBy 比写多条 NotHitBy 更简洁。\n\n"
                + "【参数详解】\n"
                + "• value = 姿态, 类型：唯一允许命中的攻击属性（例如 value = SCA, NT, ST, HT 代表仅可被投技抓，免疫一切打击与飞道）。\n"
                + "• value2 = 姿态, 类型：第二套白名单槽位。\n"
                + "• time = 帧数：持续帧数（默认 1）。",
                "[State 1050, RollOnlyThrow]\ntype = HitBy\ntrigger1 = Time = 0\nvalue = SCA, NT, ST, HT\ntime = 18", false));

        list.add(new DictEntry("HitOverride (霸体/超级护甲/当身拦截)", "sctrl", "Win/1.0/1.1/1.1b1/IK-GO",
                "被指定属性攻击命中时不进普通受击，而是强行跳转到自定义状态",
                "【功能定位】\n制作霸体(Armor)、抗打击护盾、受击反击技的核心控制器。\n\n"
                + "【参数详解】\n"
                + "• attr = 姿态, 类型：要拦截的攻击属性（如 SCA, AA, AP）。\n"
                + "• stateno = 状态号：被命中后跳转到的自定义状态（通常在目标状态里扣血、给顿帧，然后继续出招）。\n"
                + "• slot = 0~7：使用的拦截槽位编号（默认 0，最多同时挂 8 个不同规则）。\n"
                + "• time = 帧数：生效时长（-1 直到被触发或切换状态）。\n"
                + "• forceair = 0/1：触发拦截时是否强制视为受空中击飞。\n\n"
                + "【Ikemen GO 扩展】\n• keepstate = 1：拦截受击但不打断当前状态（实现一行代码真霸体！）。\n• guardflag / guardflag.not：按上/中/下段过滤拦截。\n• forceguard = 1：拦截成功时视为防御。",
                "[State 1200, TrueArmor]\ntype = HitOverride\ntrigger1 = 1\nattr = SCA, AA, AP\nstateno = 1210\nslot = 0\ntime = 1", false));

        list.add(new DictEntry("ReversalDef (架招/当身反击判定)", "sctrl", "Win/1.0/1.1/1.1b1/IK-GO",
                "用自身攻击红框(Clsn1)主动碰撞对手攻击红框，触发架招当身反制",
                "【功能定位】\n与 HitDef 相反，不打蓝框只打对手的红框(Clsn1)，用于格挡反击/当身技。\n\n"
                + "【参数详解】\n"
                + "• reversal.attr = 姿态, 类型：可反制的攻击属性（必填，如 SCA, NA, SA）。\n"
                + "• pausetime = p1, p2：当身成功后的双方定格顿帧。\n"
                + "• sparkno / hitsound：当身成功火花与音效。\n"
                + "• p1stateno / p2stateno：当身成功后我方与对手跳转的状态号。\n"
                + "• numhits = 0/1：是否计入连击数（默认 1）。\n\n"
                + "【Ikemen GO 扩展】\n支持 reversal.guardflag / reversal.guardflag.not（如只当身下段或上段攻击）与 ModifyReversalDef。",
                "[State 1300, Parry]\ntype = ReversalDef\ntrigger1 = AnimElem = 2\nreversal.attr = SC, NA, SA\npausetime = 14, 14\nsparkno = 40\nhitsound = 6, 0\np1stateno = 1310", false));

        list.add(new DictEntry("TargetBind / TargetState / TargetLifeAdd / TargetVelSet / TargetDrop", "sctrl", "Win/1.0/1.1/1.1b1/IK-GO",
                "投技与派生捕获目标控制体系（坐标锁定、强制状态、扣血、击飞、释放）",
                "【前提条件】必须先通过 HitDef 命中并捕获 Target（NumTarget > 0）。\n\n"
                + "【各控制器参数详解】\n"
                + "• TargetBind：time(持续帧，默认1)、id(指定Target ID，默认-1全部)、pos = x, y（固定对手轴心到我方相对坐标，常在投技每帧更新）。\n"
                + "• TargetState：value = 状态号、id（强制对手进入我方 CNS 中的自定义受击状态；结束时对手状态里必须调用 SelfState 归还）。\n"
                + "• TargetLifeAdd：value = 增减血量(负数扣血)、kill = 0/1(能否致死，默认1)、absolute = 0/1(1无视防御倍率)。\n"
                + "• TargetVelSet / TargetVelAdd：x, y, id（投技扔出对手时赋予抛出速度）。\n"
                + "• TargetFacing：value = 1(同向)/-1(面对面)。\n"
                + "• TargetDrop：excludeID、keepone = 0/1（释放已捕获目标，防后续误控）。\n\n"
                + "【Ikemen GO 扩展】全部支持 index 参数控制多目标之一；新增 TargetAdd / TargetDizzyPointsAdd / TargetGuardPointsAdd / TargetRedLifeAdd / TargetScoreAdd。",
                "[State 810, HoldEnemy]\ntype = TargetBind\ntrigger1 = AnimElem = 1\npos = 42, -55\ntime = 12\n\n[State 810, ThrowDmg]\ntype = TargetLifeAdd\ntrigger1 = AnimElem = 4\nvalue = -110\nkill = 1", false));
    }

    private void addDictSctrlMovement(List<DictEntry> list) {
        list.add(new DictEntry("ChangeState (状态跳转)", "sctrl", "Win/1.0/1.1/1.1b1/IK-GO",
                "将角色从当前 Statedef 切换到目标状态号，并可选重置动画与操控权",
                "【参数详解】\n"
                + "• value = 状态号（必填）：要跳转的目标 Statedef 编号（如 0 站立、11 蹲、50 空中）。\n"
                + "• ctrl = 0/1（可选）：切换同时修改控制权（收招回站立必须写 ctrl = 1，否则卡死不能动）。\n"
                + "• anim = 动作号（可选）：切换同时强制播放指定 AIR 动画（覆盖目标 Statedef 的默认 anim）。\n\n"
                + "【避坑指南】\n切勿在 trigger1 = 1 下跳转到自身状态号，会造成死循环卡死！\n【Ikemen GO 扩展】支持 continue = 1（在负状态执行跳转后不中断该负状态后续代码）。",
                "[State 200, BackToIdle]\ntype = ChangeState\ntrigger1 = AnimTime = 0\nvalue = 0\nctrl = 1", false));

        list.add(new DictEntry("SelfState (回归自身状态机)", "sctrl", "Win/1.0/1.1/1.1b1/IK-GO",
                "在被对手抓入自定义受击状态后，强制返回角色自己的状态表",
                "【功能定位】\n当 A 用 HitDef(p2stateno=820) 或 TargetState 把 B 拖进 A 的状态 820 后，B 在 820 结尾**绝对不能**用 ChangeState 跳 5050（否则会跑到 A 的 5050），必须用 SelfState！\n\n"
                + "【参数详解】\n"
                + "• value = 状态号（必填）：返回自身后的目标状态（如 5050 空中受击下落、5100 砸地、0 站立）。\n"
                + "• ctrl = 0/1（可选）；anim = 动作号（可选）。\n【Ikemen GO 扩展】新增 ReadPlayerID 参数，可跳转到指定玩家 ID 的状态。",
                "[State 820, ReturnToSelf]\ntype = SelfState\ntrigger1 = Pos Y >= 0 && Vel Y > 0\nvalue = 5100", false));

        list.add(new DictEntry("StateTypeSet (动态状态/物理切换)", "sctrl", "Win/1.0/1.1/1.1b1/IK-GO",
                "在状态执行中途动态更改站蹲空姿态、攻击态或物理摩擦/重力类型",
                "【参数详解（均可选，不填保持不变）】\n"
                + "• statetype = S/C/A/L：站立(S)、下蹲(C)、空中(A)、倒地(L)。\n"
                + "• movetype = I/A/H：待机(I)、攻击(A)、受击(H)。**收招后务必把 movetype 设为 I**，否则对手离远了也会一直被触发防御姿势！\n"
                + "• physics = S/C/A/N：地面站姿摩擦(S)、蹲姿摩擦(C)、空中重力(A)、无物理(N)。",
                "[State 1100, EnterAir]\ntype = StateTypeSet\ntrigger1 = AnimElem = 3\nstatetype = A\nphysics = A\n\n[State 1100, EndAtk]\ntype = StateTypeSet\ntrigger1 = AnimElem = 6\nmovetype = I", false));

        list.add(new DictEntry("VelSet / VelAdd / VelMul / Gravity (速度与重力)", "sctrl", "Win/1.0/1.1/1.1b1/IK-GO",
                "精确控制角色 X/Y 速度矢量（设值、累加、摩擦缩放与标准重力）",
                "【坐标约定】X 正值恒为角色面向前方（引擎自动按 Facing 翻转），X 负值为后退；Y 负值为向上起跳，Y 正值为向下坠落。\n\n"
                + "【各控制器参数详解】\n"
                + "• VelSet：x = 浮点值, y = 浮点值（直接覆写当前速度；省略某轴则该轴保持原速）。\n"
                + "• VelAdd：x = 增量, y = 增量（每帧叠加；如自定义重力 y = 0.45）。\n"
                + "• VelMul：x = 倍率, y = 倍率（每帧乘以系数；如地面急停摩擦 x = 0.82）。\n"
                + "• Gravity：无参数，等价于 VelAdd y = Const(movement.yaccel)。",
                "[State 1050, DragonPunchJump]\ntype = VelSet\ntrigger1 = AnimElem = 2\nx = 3.5\ny = -9.2\n\n[State 1050, CustomGrav]\ntype = VelAdd\ntrigger1 = AnimElemTime(2) > 0\ny = 0.48", false));

        list.add(new DictEntry("PosSet / PosAdd / PosFreeze / Offset (坐标与冻结)", "sctrl", "Win/1.0/1.1/1.1b1/IK-GO",
                "直接设定绝对坐标、增量瞬移、冻结位移或仅偏移精灵显示位置",
                "【各控制器参数详解】\n"
                + "• PosSet：x = 坐标, y = 坐标（y = 0 恒为地面；常用于落地防穿地 PosSet y = 0 或瞬移到对手身后）。\n"
                + "• PosAdd：x = 偏移, y = 偏移（x 自动按朝向翻转，适合招式踏步前进）。\n"
                + "• PosFreeze：value = 1（当帧冻结速度带来的位置变化，保留 Vel 值不清空，适合空中停顿/蓄力）。\n"
                + "• Offset：x = 像素, y = 像素（仅偏移画面贴图渲染，**不改变实际坐标与判定框**，适合做角色受击抖动）。",
                "[State 1000, StepFwd]\ntype = PosAdd\ntrigger1 = AnimElem = 2\nx = 18\n\n[State 1000, GroundClamp]\ntype = PosSet\ntrigger1 = Pos Y > 0\ny = 0", false));

        list.add(new DictEntry("Width / PlayerPush / ScreenBound / Turn / CtrlSet", "sctrl", "Win/1.0/1.1/1.1b1/IK-GO",
                "单帧挤身宽/版边宽、穿身开关、镜头绑定、立即转身与操控权开关",
                "【各控制器参数详解】\n"
                + "• Width（单帧生效）：edge = 前版边宽, 后版边宽；player = 前挤身宽, 后挤身宽；value = 前, 后(同时设两者)。用于防长招式穿墙或把对手挤出角落。\n"
                + "• PlayerPush（单帧生效）：value = 0 关闭角色间挤身碰撞（穿身冲刺/瞬移必备）；IK 新增 priority(推挤优先级)与 affectteam(F/B/E)。\n"
                + "• ScreenBound（单帧生效）：value = 0 允许移出屏幕边缘；movecamera = x_flag, y_flag（设 0, 0 让镜头不跟随自己）；IK 新增 stagebound。\n"
                + "• Turn：无参数，立刻翻转角色朝向。\n"
                + "• CtrlSet：value = 0/1 直接开关玩家操控权。",
                "[State 1050, DashThrough]\ntype = PlayerPush\ntrigger1 = Time < 15\nvalue = 0\n\n[State 1050, KeepInStage]\ntype = Width\ntrigger1 = 1\nedge = 20, 20", false));
    }

    private void addDictSctrlEntities(List<DictEntry> list) {
        list.add(new DictEntry("Helper (生成独立实体)", "sctrl", "Win/1.0/1.1/1.1b1/IK-GO",
                "召唤拥有完整独立状态机、判定框与变量的实体（高级飞道/分身/援护/系统控制器）",
                "【核心参数详解】\n"
                + "• helpertype = normal/player（默认 normal；player 类似完整角色可被镜头跟随与推挤）。\n"
                + "• name = \"名称\"：供调试与 HelperName 识别。\n"
                + "• ID = 整数编号：供 NumHelper(ID) / Helper(ID) 检索。\n"
                + "• stateno = 状态号：生成后立即进入的 Statedef（默认 0）。\n"
                + "• pos = x, y / postype = p1/p2/front/back/left/right：生成位置与参考系。\n"
                + "• facing = 1/-1：相对参考系的朝向。\n"
                + "• keyctrl = 0/1：是否允许读取玩家按键并执行 State -1（IK 支持 keyctrl = 1, 2, 3 继承负状态）。\n"
                + "• ownpal = 0/1：设 1 拥有独立调色板（不随本体 PalFX 变色，特效/飞道必开 1！）。\n"
                + "• supermovetime / pausemovetime：在 SuperPause / Pause 期间可继续活动的帧数。\n"
                + "• size.xscale / size.yscale / size.ground.back / size.ground.front / size.height：覆写实体缩放与体积。\n\n"
                + "【Ikemen GO 专属扩展】\n• clsnproxy = 1(判定框代理给父级)、extendsmap = 1(继承父级 Map)、immortal = 1(不死)、inheritjuggle = 1/2(共享浮空与目标)、preserve = 1(跨回合不销毁)、ownprojectile = 1、map.<名称> = 初值。\n【销毁】Helper 状态结束必须调用 type = DestroySelf，否则会残留占用实体槽位！",
                "[State 1000, EnergyBall]\ntype = Helper\ntrigger1 = AnimElem = 4\nhelpertype = normal\nname = \"EnergyBall\"\nID = 1005\nstateno = 1005\npos = 45, -65\npostype = p1\nfacing = 1\nownpal = 1\nsupermovetime = 0\npausemovetime = 0", false));

        list.add(new DictEntry("Explod (生成视觉特效)", "sctrl", "Win/1.0/1.1/1.1b1/IK-GO",
                "在屏幕或场景中生成纯视觉动画特效（火花、斩击、立绘切入、自定义 UI）",
                "【核心参数详解】\n"
                + "• anim = [F]动作号（必填）：加 F 调用 fightfx.air，否则调用自身 AIR。\n"
                + "• ID = 编号：供 NumExplod / ModifyExplod / RemoveExplod 控制。\n"
                + "• pos = x, y / postype = p1/p2/front/back/left/right（1.1+ 推荐搭配 space = stage/screen）。\n"
                + "• bindtime = 帧数：绑定坐标的持续帧（-1 永久跟随，1 仅生成帧）。\n"
                + "• removetime = 帧数：存活帧数（-2 动画播完自动销毁，-1 永久存在需手动 RemoveExplod）。\n"
                + "• vel = vx, vy / accel = ax, ay / random = rx, ry：初速度、加速度与随机散布范围。\n"
                + "• scale = sx, sy / facing = 1/-1 / vfacing = 1/-1：缩放与水平/垂直翻转。\n"
                + "• sprpriority = -5~5 / ontop = 0/1：图层先后（ontop=1 盖在血条之上）。\n"
                + "• ownpal = 0/1：设 1 防止随本体 PalFX 一起变色。\n"
                + "• trans = none/add/add1/sub/addalpha；alpha = src(0~256), dst(0~256)。\n"
                + "• supermovetime / pausemovetime / ignorehitpause / removeongethit。\n\n"
                + "【MUGEN 1.1/1.1b1 & IK-GO 扩展】\n• angle / xangle / yangle(3D旋转)、remappal(色表映射)。\n• IK 独有：interpolation.* 补间动画、palfx.*、afterimage.*、shader.*、syncid(同步角色渲染序)、window(裁剪)、xshear(斜切)、under(置于血条与阴影下)、removeonchangestate。",
                "[State 3000, SuperPortrait]\ntype = Explod\ntrigger1 = Time = 0\nanim = 8500\nID = 8500\npos = 0, 0\npostype = left\nscale = 0.5, 0.5\nbindtime = -1\nremovetime = 40\nsupermovetime = 40\nsprpriority = -2\nownpal = 1", false));

        list.add(new DictEntry("ModifyExplod / RemoveExplod / ExplodBindTime", "sctrl", "Win/1.0/1.1/1.1b1/IK-GO",
                "实时修改已存在特效的属性、绑定时长，或按 ID 销毁特效",
                "【参数详解】\n"
                + "• ModifyExplod：必填 ID = 特效编号，其余参数与 Explod 相同，可实时改 pos、scale、vel、sprpriority、trans、alpha、angle 等。\n  ※ Ikemen GO 扩展：支持 index 参数仅改同 ID 第 N 个特效，支持实时改 anim / animelem / space / under。\n"
                + "• RemoveExplod：ID = 编号（省略则清除自身全部 Explod；IK 支持 index）。\n"
                + "• ExplodBindTime：ID = 编号, time = 新绑定帧数。",
                "[State 3000, FadeOutFX]\ntype = ModifyExplod\ntrigger1 = Time >= 20\nID = 8500\ntrans = addalpha\nalpha = 256 - (Time - 20) * 12, 256", false));
    }

    private void addDictSctrlVisualAudio(List<DictEntry> list) {
        list.add(new DictEntry("ChangeAnim / ChangeAnim2 (动画切换)", "sctrl", "Win/1.0/1.1/1.1b1/IK-GO",
                "切换当前播放的 AIR 动作序列与起始帧（ChangeAnim2 用于自定义受击状态）",
                "【参数详解】\n"
                + "• value = 动作号（必填）：AIR 中的 [Begin Action N]。\n"
                + "• elem = 帧索引（可选，默认 1）：指定从第几帧开始播放。\n"
                + "• ChangeAnim2 专用场景：在投技对手状态(P2StateNo)中，调用攻击方的 AIR 动作编排来驱动对手的 SFF 图像。\n\n"
                + "【Ikemen GO 扩展】\n支持 elemtime(精确到帧内第几 tick)、animplayerno、spriteplayerno、readplayerid 以及 F 前缀调用 fightfx 动画。",
                "[State 1000, LoopAnim]\ntype = ChangeAnim\ntrigger1 = Time = 0\nvalue = 1000\nelem = 1", false));

        list.add(new DictEntry("PalFX / BGPalFX / AllPalFX (调色板与滤镜)", "sctrl", "Win/1.0/1.1/1.1b1/IK-GO",
                "对角色自身(PalFX)、地图背景(BGPalFX)或全屏(AllPalFX)施加染色、闪烁、灰度与反色",
                "【参数详解】\n"
                + "• time = 持续帧数（-1 永久直到下次覆盖，0 立即取消）。\n"
                + "• add = r, g, b（-256~256，默认 0,0,0）：颜色通道加减（如 180,40,0 火焰泛红）。\n"
                + "• mul = r, g, b（0~512，默认 256,256,256）：颜色通道乘法（256=100%原色，128=半暗）。\n"
                + "• sinadd = r, g, b, period：正弦周期闪烁（period 为周期帧数）。\n"
                + "• invertall = 0/1：是否底片反色。\n"
                + "• color = 0~256：饱和度（0 完全黑白灰度，256 正常彩色）。\n\n"
                + "【Ikemen GO 扩展】\n新增 hue(-256~256 色相偏移)、sinmul、sincolor、sinhue、invertblend。",
                "[State 3000, BurnTint]\ntype = PalFX\ntrigger1 = Time = 0\ntime = 25\nadd = 160, 40, -20\nmul = 256, 200, 160\nsinadd = 60, 30, 0, 8\ncolor = 256", false));

        list.add(new DictEntry("RemapPal (动态实时换色表)", "sctrl", "1.0/1.1/1.1b1/IK-GO",
                "【1.0+新增】在战斗中动态将 SFFv2 某色表映射为另一套色表（变身/爆气换色核心）",
                "【功能定位】\nWinMUGEN 不支持！MUGEN 1.0+ 与 Ikemen GO 可在不切换精灵图的情况下瞬间更换整套服装配色。\n\n"
                + "【参数详解】\n"
                + "• source = 组, 号：原始色表索引（通常 1, 1）。\n"
                + "• dest = 组, 号：目标色表索引（如 1, 7；恢复原色时设 dest = 1, 1）。",
                "[State 3000, GoldenForm]\ntype = RemapPal\ntrigger1 = Time = 0\nsource = 1, 1\ndest = 1, 7", false));

        list.add(new DictEntry("AfterImage / AfterImageTime (残影拖尾)", "sctrl", "Win/1.0/1.1/1.1b1/IK-GO",
                "为超必杀、瞬步、EX 技开启可精细调色的残影拖尾效果",
                "【AfterImage 核心参数详解】\n"
                + "• time = 持续帧数（-1 永久，默认 1）。\n"
                + "• length = 缓存帧数（2~60，默认 20；越大残影尾巴越长）。\n"
                + "• timegap = 采样间隔帧（默认 1）；framegap = 显示间隔帧（默认 4）。\n"
                + "• palcolor = 0~256(饱和度)；palinvertall = 0/1(反色)。\n"
                + "• palbright = r, g, b / palcontrast = r, g, b(128=原对比度) / palpostbright = r, g, b。\n"
                + "• paladd = r, g, b / palmul = r, g, b（每个残影逐级叠加衰减，如 palmul = 0.65, 0.65, 0.75）。\n"
                + "• trans = none / add / add1 / sub。\n\n"
                + "【AfterImageTime】time = 0 可在受击或收招时立即终止残影。\n【Ikemen GO 扩展】支持 shader.* 残影着色器与精确 framegap 计数。",
                "[State 1050, EXTrail]\ntype = AfterImage\ntrigger1 = Time = 0\ntime = 25\nlength = 12\npalbright = 40, 20, 0\npalcontrast = 200, 160, 80\npalmul = 0.7, 0.7, 0.7\ntimegap = 1\nframegap = 3\ntrans = add1", false));

        list.add(new DictEntry("AngleDraw / AngleSet / AngleAdd / AngleMul (旋转缩放)", "sctrl", "Win/1.0/1.1/1.1b1/IK-GO",
                "实时旋转并缩放角色精灵绘制（需每帧触发）",
                "【参数详解】\n"
                + "• AngleDraw：value = 角度(逆时针为正，单位度)；scale = sx, sy（MUGEN 1.0+ 支持直接写在 AngleDraw 里）。\n"
                + "• AngleSet / AngleAdd / AngleMul：修改内部角度寄存器（WinMUGEN 需配合使用）。\n\n"
                + "【Ikemen GO 扩展】\n支持 3D 透视倾角 xangle、yangle 以及旋转轴心偏移 pivot = x, y。",
                "[State 1000, SpinKick]\ntype = AngleDraw\ntrigger1 = 1\nvalue = Time * 24\nscale = 1.05, 1.05", false));

        list.add(new DictEntry("PlaySnd / StopSnd / SndPan (音效播放与控制)", "sctrl", "Win/1.0/1.1/1.1b1/IK-GO",
                "播放角色或系统音效、打断指定声道以及动态调节左右立体声声像",
                "【PlaySnd 核心参数详解】\n"
                + "• value = [S/F]组, 号（必填）：不加前缀或加 S 播角色 .snd，加 F 播 fight.snd 公共音效。\n"
                + "• volumescale = 百分比（1.0+ 推荐，100=原音量，200=双倍；旧版用 volume = -255~255）。\n"
                + "• channel = 声道号（-1 自动分配；同声道播放新音会打断旧音，角色语音建议固定 channel = 0 防重叠！）。\n"
                + "• lowpriority = 0/1：设 1 时若该声道正在发声则放弃本次播放。\n"
                + "• freqmul = 倍率（默认 1.0，改音调与播放速度）。\n"
                + "• loop = 0/1：是否循环播放；pan / abspan：相对/绝对左右声道偏移。\n\n"
                + "【StopSnd】channel = 声道号（-1 停全场；Ikemen GO 新增 -2 仅停自身全部声道）。\n【Ikemen GO 扩展】PlaySnd 新增 priority、loopstart/end、startposition、loopcount、stopongethit、stoponchangestate。",
                "[State 200, AttackVoice]\ntype = PlaySnd\ntrigger1 = AnimElem = 1\nvalue = 0, 2\nvolumescale = 100\nchannel = 0", false));

        list.add(new DictEntry("EnvShake / EnvColor (屏幕震动与闪屏)", "sctrl", "Win/1.0/1.1/1.1b1/IK-GO",
                "触发全局画面震动反馈或纯色全屏闪光覆盖",
                "【EnvShake 参数详解】\n"
                + "• time = 持续帧数（必填）。\n"
                + "• freq = 震动频率（0~180，默认 60，越大越急促）。\n"
                + "• ampl = 振幅像素（默认 -4，绝对值越大晃动越剧烈）。\n"
                + "• phase = 初始相位角（默认 90）。\n"
                + "• Ikemen GO 新增：decay（指数衰减，如 1.5 自然淡出）、dir（震动方向角度，90=左右横震）、diradd。\n\n"
                + "【EnvColor 参数详解】\n"
                + "• value = r, g, b（0~255，如 255,255,255 白闪）；time = 持续帧数；under = 0/1（1 绘制在角色背后，遮住地图不遮角色）。",
                "[State 3000, ImpactShake]\ntype = EnvShake\ntrigger1 = AnimElem = 4\ntime = 18\nfreq = 90\nampl = -8\n\n[State 3000, WhiteFlash]\ntype = EnvColor\ntrigger1 = AnimElem = 4\nvalue = 255, 255, 255\ntime = 3\nunder = 1", false));

        list.add(new DictEntry("Zoom (动态镜头缩放)", "sctrl", "1.1/1.1b1/IK-GO",
                "【1.1/1.1b1+新增】在超必杀或特写招式中动态拉近/拉远镜头",
                "【参数详解】\n"
                + "• scale = 缩放倍率（float，>1.0 放大拉近，<1.0 缩小拉远，如 1.35）。\n"
                + "• pos = x, y：缩放焦点（相对屏幕中心，填 Pos X, Pos Y - 60 可紧跟角色胸口）。\n"
                + "• lag = 0.0~1.0：平滑插值阻力（0 瞬切，0.8~0.92 丝滑推进）。\n"
                + "• time = 持续帧数（默认 1，可配合 trigger1 = Time < 40 每帧维持）。\n"
                + "• stagebound = 0/1：是否限制在地图边界内防露黑边（默认 1）。\n"
                + "• camerabound = 0/1：是否限制在当前镜头范围内（默认 1）。\n"
                + "• endlag = 0.0~1.0（Ikemen GO 新增）：缩放结束后的平滑回弹阻力。",
                "[State 3000, SuperZoom]\ntype = Zoom\ntrigger1 = Time < 40\nscale = 1.3\npos = Pos X, Pos Y - 60\nlag = 0.88\nstagebound = 1", false));
    }

    private void addDictSctrlSystem(List<DictEntry> list) {
        list.add(new DictEntry("SuperPause / Pause (超杀暗屏与时间暂停)", "sctrl", "Win/1.0/1.1/1.1b1/IK-GO",
                "触发超必杀专属暗屏定格特效(SuperPause)或纯时间暂停(Pause)",
                "【SuperPause 核心参数详解】\n"
                + "• time = 定格总帧数（默认 30）。\n"
                + "• movetime = 自身可行动帧数（默认 0；设等于 time 可在定格期间演完起手动作）。\n"
                + "• anim = [S/F]动作号（默认 30，-1 关闭内置圆环光效以便自画 Explod）。\n"
                + "• sound = [S/F]组, 号 / pos = x, y：定格音效与内置特效坐标。\n"
                + "• darken = 0/1：是否压暗背景（默认 1；Ikemen GO 新增 brightness = 0~255 精细控制暗度）。\n"
                + "• p2defmul = 浮点倍率：超杀连段防补正系数（设 1.0 取消默认减伤）。\n"
                + "• poweradd = 数值：扣除能量（如 -1000 扣 1 格气）。\n"
                + "• unhittable = 0/1：定格期间自身是否无敌（默认 1）。\n"
                + "• endcmdbuftime = 帧数：定格结束前几帧允许预输入指令。\n"
                + "• pausebg = 0/1：是否同时冻结地图背景动画。",
                "[State 3000, SuperFreeze]\ntype = SuperPause\ntrigger1 = Time = 0\ntime = 42\nmovetime = 42\nanim = -1\ndarken = 1\npoweradd = -1000\np2defmul = 1.0\nunhittable = 1", false));

        list.add(new DictEntry("AssertSpecial (特殊引擎标志开关)", "sctrl", "Win/1.0/1.1/1.1b1/IK-GO",
                "单帧激活引擎级特殊规则（不可防御、隐藏血条、免死、禁转身等）",
                "【语法规则】每帧自动复位，需持续触发。MUGEN 单次最多 flag / flag2 / flag3；Ikemen GO 扩展到 flag~flag8 且支持 enabled = 0 反向关闭。\n\n"
                + "【高频常用 Flag 分类速查】\n"
                + "• 演出/界面：intro(延长开场)、roundnotover(延长胜利)、nobardisplay(隐藏血条)、noBG(隐藏背景)、noFG(隐藏前景)、invisible(隐身)、noshadow / globalnoshadow(隐影)、nomusic(静音BGM)、timerfreeze(冻结计时器)。\n"
                + "• 攻防机制：unguardable(本帧不可防)、nostandguard / nocrouchguard / noairguard(禁站/蹲/空防)、noautoturn(禁止自动转身)、nojugglecheck(无视浮空限制)、noko(免死留1血)、nokosnd / nokoslow(禁KO惨叫/慢放)、nowalk / noairjump。\n"
                + "• Ikemen GO 专属新增：AutoGuard(自动防)、AnimFreeze(定帧)、AnimateHitpause(顿帧继续播动画)、CameraFreeze(锁镜头)、NoCornerPush(免墙角推)、NoGuardKO(免磨血致死)、NoHardcodedKeys(屏蔽底层方向状态)、SizePushOnly(纯体型盒推挤)、RoundFreeze、RoundNotSkip、RunFirst / RunLast。",
                "[State 3000, BossDomain]\ntype = AssertSpecial\ntrigger1 = 1\nflag = nobardisplay\nflag2 = noBG\nflag3 = timerfreeze", false));

        list.add(new DictEntry("VarSet / VarAdd / VarRandom / VarRangeSet / ParentVarSet", "sctrl", "Win/1.0/1.1/1.1b1/IK-GO",
                "读写角色整数变量 var、浮点变量 fvar、系统变量 sysvar 及范围清零",
                "【变量容量】MUGEN Win/1.0/1.1 为 var(0~59)、fvar(0~39)、sysvar(0~4)、sysfvar(0~4)；Ikemen GO 解除上限（支持任意正 int32 编号）并推荐改用命名 Map。\n\n"
                + "【各控制器参数详解】\n"
                + "• VarSet / VarAdd：v = 编号 或 fv = 编号，value = 新值/增量；也可直接简写 var(10) = 1 或 fvar(2) = 1.5。\n"
                + "• VarRandom：v = 编号，range = 最小值, 最大值（含两端，默认 0, 1000）。\n"
                + "• VarRangeSet：value = 整数值 或 fvalue = 浮点值，first = 起始号，last = 结束号（常用于 State 5900 开局重置变量）。\n"
                + "• ParentVarSet / ParentVarAdd（IK 新增 RootVarSet/Add）：Helper 修改父级/本体变量。",
                "[State 5900, ResetVars]\ntype = VarRangeSet\ntrigger1 = RoundNo = 1\nvalue = 0\nfirst = 0\nlast = 50", false));

        list.add(new DictEntry("LifeAdd / LifeSet / PowerAdd / PowerSet / HitAdd / AttackMulSet / DefenceMulSet", "sctrl", "Win/1.0/1.1/1.1b1/IK-GO",
                "直接操控血量、能量气槽、连击计数与动态攻防倍率",
                "【参数详解】\n"
                + "• LifeAdd：value = 增减值、kill = 0/1(0 至少留 1 血)、absolute = 0/1(1 无视防御倍率)。\n"
                + "• LifeSet：value = 目标血量（常用于回满血 LifeSet value = LifeMax）。\n"
                + "• PowerAdd / PowerSet：value = 能量值（常规 1000 = 1 格气）。\n"
                + "• HitAdd：value = 连击数增量（无 HitDef 扣血时同步增加屏幕 Combo 计数）。\n"
                + "• AttackMulSet / DefenceMulSet：value = 倍率（如 1.25 增伤 25%；注意 MUGEN 中 DefenceMulSet 仅在受击态生效且覆盖倒地减伤，Ikemen GO 改为连乘并新增 multype / onhit）。",
                "[State -2, RageMode]\ntype = AttackMulSet\ntrigger1 = Life <= LifeMax * 0.3\nvalue = 1.25", false));

        list.add(new DictEntry("VictoryQuote / DisplayToClipboard / SprPriority / Trans", "sctrl", "Win/1.0/1.1/1.1b1/IK-GO",
                "胜利台词指定(1.0+)、调试剪贴板输出、图层优先级与精灵透明混合",
                "【各控制器参数详解】\n"
                + "• VictoryQuote (1.0+ 专属)：value = 台词编号（对应 CNS [Quotes] 中的 victoryN）。\n"
                + "• DisplayToClipboard / AppendToClipboard：text = \"格式串\"(%d/%f/%v), params = 表达式1, 表达式2...（按 Ctrl+D 在屏幕底部查看实时变量）。\n"
                + "• SprPriority：value = -5~5（数字越大越靠前；IK 新增 layerno = -1/0/1）。\n"
                + "• Trans：trans = none / add / add1 / sub / addalpha；alpha = src(0~256), dst(0~256)（IK 新增 subadd 混合模式）。",
                "[State 180, SpecialQuote]\ntype = VictoryQuote\ntrigger1 = Enemy, Name = \"Ryu\"\nvalue = 2", false));
    }

    private void addDictIkemenSctrlsDetailed(List<DictEntry> list) {
        list.add(new DictEntry("RedirectID (IK 通用跨实体重定向参数)", "sctrl", "IK-GO",
                "【IK官方Wiki】所有新老控制器通用参数：无需自定义状态即可直接操控指定实体",
                "【核心原理】\n在几乎任何状态控制器中加入 RedirectID = <PlayerID>，该控制器就会直接对目标 ID 实体执行！\n\n"
                + "【典型用途】\n• 持续中毒/灼烧扣血而不打断对手当前动作\n• Helper 直接给队友/队长加 Map 或回血\n• 地图 AttachedChar 直接给双方玩家施加环境特效\n• 注意：对非自身执行 ChangeState/SelfState 时，不会中断当前状态后续代码运行。",
                "[State -2, BurnDot]\ntype = LifeAdd\ntrigger1 = P2, Map(burn) > 0 && GameTime % 20 = 0\nvalue = -8\nkill = 0\nRedirectID = P2, ID", false));

        list.add(new DictEntry("MapSet / MapAdd / MapReset (IK 具名变量系统)", "sctrl", "IK-GO",
                "【IK官方Wiki】无数量上限的字符串键名变量（支持数组索引、字符串值与跨队读写）",
                "【参数详解】\n"
                + "• MapSet / MapAdd：必填 map = \"变量名\"（不可含空格），value = 数值（Nightly 支持字符串），可选 index = 整数（把同名 Map 当数组用，index 0 等价于省略）。\n"
                + "• MapReset：清空自身所有 Map（.def [Map] 中预设的键会恢复默认值），可选 exclude ~ exclude8 = \"前缀\" 保留特定前缀的 Map。\n"
                + "• 跨实体版本：ParentMapSet / ParentMapAdd、RootMapSet / RootMapAdd、TeamMapSet / TeamMapAdd。",
                "[State 200, GainHeat]\ntype = MapAdd\ntrigger1 = MoveHit = 1\nmap = \"heat_meter\"\nvalue = 12\n\n[State 5900, Clean]\ntype = MapReset\ntrigger1 = RoundNo = 1\nexclude = \"save_\"", false));

        list.add(new DictEntry("ModifyHitDef / ModifyProjectile / ModifyReversalDef / ModifyPlayer", "sctrl", "IK-GO",
                "【IK官方Wiki】运行时动态修改已激活判定、飞行道具或角色底层属性",
                "【各控制器详解】\n"
                + "• ModifyHitDef / ModifyReversalDef：实时覆写当前激活 HitDef/ReversalDef 的任意参数（如第 5 帧提升 damage 或改 hitflag）。\n"
                + "• ModifyProjectile：ID = 飞道ID(-1全部), index = 序号(-1全部)，可实时改 accel、velocity、teamside、projID、anim。\n"
                + "• ModifyPlayer：直接修改底层角色字段：ailevel、alive、attack、defence、displayname、lifebarname、lifemax、powermax、dizzypointsmax、guardpointsmax、teamside、helpervar.ID、movehit、moveguarded、hitpausetime、supermovetime、unhittabletime。",
                "[State -2, BossPhase2]\ntype = ModifyPlayer\ntrigger1 = Life < LifeMax / 2 && Map(p2) = 0\ndisplayname = \"真·觉醒形态\"\nattack = 135\ndefence = 120", false));

        list.add(new DictEntry("Text / ModifyText / RemoveText / LifebarAction", "sctrl", "IK-GO",
                "【IK官方Wiki】屏幕任意位置绘制格式化文字、打字机字幕与血条侧边提示队列",
                "【Text 核心参数详解】\n"
                + "• text = \"格式串\"（支持 %d/%f/%v/%s 与 <color:RRGGBB>变色</color>）；params = 表达式列表。\n"
                + "• font = [F]字体编号（加 F 用血条字体，否则用角色 .def font0~9，缺失回退调试字体）。\n"
                + "• bank = 色库号；align = 1(左)/0(中)/-1(右)；pos = x, y；scale = sx, sy；angle = 角度；color = r,g,b,a(默认256)。\n"
                + "• layerno = 0/1/2；removetime = 帧数；id = 编号；textdelay = 逐字打字机延迟；velocity/accel/friction；palfx.*；hidewithbars = 1。\n\n"
                + "【LifebarAction 参数】top、time、timemul、anim、spr、snd、text、font.no/bank/align/color、refreshtype(0堆叠/1刷计时/2重新滑入)。",
                "[State 3000, ShowGauge]\ntype = Text\ntrigger1 = Time = 0\nid = 100\ntext = \"<color:FACC15>狂暴值: %d%%</color>\"\nparams = Map(rage)\nfont = F 0\nalign = 0\npos = 160, 48\nremovetime = 90", false));

        list.add(new DictEntry("Dialogue (IK 战斗剧情对话)", "sctrl", "IK-GO",
                "【IK官方Wiki】开局前或决胜局自动弹出带立绘、语音与动作联动的剧情对话框",
                "【参数详解】\n"
                + "• text1, text2, ... = \"对话内容\"（按序播放，需界面 system.def 配置 [Dialogue Info]）。\n"
                + "• hidebars = 1（调用时立即隐藏血条）；force = 1（无视 RoundState 立即弹出）。\n\n"
                + "【内嵌控制标签】\n"
                + "• <p1> / <p2>：切换左/右侧对话框\n"
                + "• <p1face=self,9000,0> / <p2face=enemy,9000,2>：切换头像（填 -1 隐藏）\n"
                + "• <sound=self,10,0,100>：播语音；<anim=self,190> / <state=enemy,5500>：联动角色动作与状态\n"
                + "• <displayname=enemy>：自动替换对方显示名；<wait=60>：停顿帧数。",
                "[State 191, BossTalk]\ntype = Dialogue\ntrigger1 = Enemy, Name = \"Kyo\"\nhidebars = 1\ntext1 = \"<p1><p1face=self,9000,0>好久不见，<displayname=enemy>。<wait=50>\"\ntext2 = \"<p2><p2face=enemy,9000,0>今天就在这里做个了断！<wait=60>\"", false));

        list.add(new DictEntry("PlayBgm / ModifyBgm / LoadFile / SaveFile / PrintToConsole", "sctrl", "IK-GO",
                "【IK官方Wiki】战斗动态切歌、BGM实时变速跳转、二进制本地存档与终端打印",
                "【PlayBgm 参数详解】\n"
                + "• bgm = \"路径\"（支持 mp3/ogg/wav/flac/mid/xm/mod/it/s3m；留空 \"\" 停止音乐）。\n"
                + "• source = \"match\" / \"stagedef\" / \"charparams\" / \"motif\"（恢复对战/地图原曲）。\n"
                + "• volume(默认100)、loop(默认1)、loopstart/loopend(采样点)、startposition、freqmul(速度音调)、loopcount。\n"
                + "• ModifyBgm：实时改正在播放音乐的 volume/freqmul/position/loopstart/loopend（freqmul=0 暂停）。\n\n"
                + "【SaveFile / LoadFile 参数详解】\n"
                + "• savedata = \"var\" / \"fvar\" / \"map\"；path = \"save.gob\"（相对角色目录的 Go gob 二进制存档）；可选 maps / maps.include 过滤键名。\n\n"
                + "【PrintToConsole】text = \"格式串\", params = 表达式...（直接输出到控制台与终端）。",
                "[State 3000, BossBGM]\ntype = PlayBgm\ntrigger1 = Time = 0\nbgm = \"sound/phase2.ogg\"\nvolume = 100\nloop = 1", false));

        list.add(new DictEntry("OverrideClsn / TransformClsn / TransformSprite / RemapSprite / ShaderSet / Storyboard", "sctrl", "IK-GO",
                "【IK官方Wiki】动态改写判定框、透视变形、批量换装预设、自定义着色器与战斗播MP4过场",
                "【参数速查】\n"
                + "• OverrideClsn：group = All/None/Clsn1/Clsn2/Size，index(-1全部)，rect = x1,y1,x2,y2（0,0,0,0移除）。\n"
                + "• TransformClsn：group, index, scale = sx,sy, angle = 度, pivot = px,py。\n"
                + "• TransformSprite：window(裁剪矩形)、xshear(水平斜切)、projection(orthographic/perspective/perspective2)、focallength。\n"
                + "• RemapSprite：reset = 1、preset = \"预设名\"（对应 CNS [RemapPreset 名称]）、或单图 source = g,i / dest = g,i。\n"
                + "• ShaderSet：shader(Nightly为 name) = \"着色器名\"、time(-1永久)、shaderparam.p0~p15(Nightly为 param0~15)、shadertex1/2.spr 或 anim。\n"
                + "• Storyboard：path = \"过场.def\"、id、space = none/screen/stage、pos、scale、bindID、bindtime（支持 MP4 视频超杀演出！）。",
                "[State 3000, CineSuper]\ntype = Storyboard\ntrigger1 = Time = 0\npath = \"super_cutin.def\"\nid = 10\nspace = screen\npos = 0, 0", false));

        list.add(new DictEntry("Camera / GroundLevelOffset / TagIn / TagOut / MatchRestart / ShiftInput / AssertInput / Dizzy & GuardBreak", "sctrl", "IK-GO",
                "【IK官方Wiki】自由镜头、动态平台高度、Tag换人、无刷重载对局、键位反转与眩晕破防",
                "【参数速查】\n"
                + "• Camera [实验]：view = \"Fighting\" / \"Follow\" / \"Free\"，pos.x/y，followid。\n"
                + "• GroundLevelOffset：value = Y偏移（动态抬高地面做平台关卡）。\n"
                + "• TagIn / TagOut：控制队友进出 Standby 待机态，支持 leader、stateno、partner、memberno。\n"
                + "• MatchRestart：p1def~p8def、stagedef、reload = 1,1、resetmatch = 1、preservevars = 1,1。\n"
                + "• AssertInput(flag~flag8 模拟按键) / AssertCommand(name, buffer.time) / ShiftInput(input, output 键位映射/混乱反转)。\n"
                + "• 商业格斗条槽：DizzySet / DizzyPointsAdd / GuardBreakSet / GuardPointsAdd / RedLifeAdd / ScoreAdd / ChangeMovelist。",
                "[State -2, Platform]\ntype = GroundLevelOffset\ntrigger1 = Pos X = [-100, 100] && Pos Y + GroundLevel <= -70\nvalue = -70", false));
    }

    private void addDictMugenTriggersDetailed(List<DictEntry> list) {
        list.add(new DictEntry("Time / AnimTime / AnimElem / AnimElemTime / AnimElemNo / SelfAnimExist", "trigger", "Win/1.0/1.1/1.1b1/IK-GO",
                "状态帧计时、动画剩余时间、关键帧命中与动画存在性检查",
                "• Time (同 StateTime)：进入当前 Statedef 后经过的帧数（首帧为 0）。\n"
                + "• AnimTime：距离当前动画播完剩余帧数（负数；最后一帧结束瞬间严格等于 0，用于收招判断）。\n"
                + "• AnimElem = N：当前动画正好切换到第 N 帧首帧时为 1。\n"
                + "• AnimElemTime(N)：距离第 N 帧开始经过的帧数（未到第 N 帧时为负，到达为 0，之后递增）。\n"
                + "• AnimElemNo(offset)：返回当前向前/后偏移 offset 帧对应的帧编号。\n"
                + "• AnimExist(N) / SelfAnimExist(N)：检查动作 N 是否存在（SelfAnimExist 即使在自定义状态也只查自身 AIR）。",
                "trigger1 = AnimElemTime(3) >= 0 && AnimElemTime(5) < 0\ntrigger2 = AnimTime = 0", false));

        list.add(new DictEntry("StateNo / PrevStateNo / StateType / MoveType / Physics / Ctrl / Alive / Facing", "trigger", "Win/1.0/1.1/1.1b1/IK-GO",
                "角色核心状态机字段读取（状态号、站蹲空倒、攻防待、操控权与朝向）",
                "• StateNo / PrevStateNo：当前与上一个状态号。\n"
                + "• StateType：S(站) / C(蹲) / A(空) / L(倒地)。\n"
                + "• MoveType：I(待机) / A(攻击) / H(受击)。\n"
                + "• Physics：S / C / A / N（IK 新增支持直接返回）。\n"
                + "• Ctrl(0/1 玩家操控权)、Alive(0/1 存活)、Facing(1朝右 / -1朝左)。",
                "trigger1 = StateType != A && MoveType = I && Ctrl && Alive", false));

        list.add(new DictEntry("MoveContact / MoveHit / MoveGuarded / MoveReversed / HitCount / HitPauseTime / InGuardDist", "trigger", "Win/1.0/1.1/1.1b1/IK-GO",
                "招式命中/被防/当身计时、连段计数、顿帧剩余与近身防御区检测",
                "• MoveContact / MoveHit / MoveGuarded / MoveReversed：接触当帧=1，之后每非顿帧+1，未接触=0。取消技推荐写 MoveContact = [1, 12]。\n"
                + "• HitCount / UniqHitCount：当前状态命中总段数 / 独立招数。\n"
                + "• HitPauseTime：当前剩余顿帧数（=0 表示顿帧结束）。\n"
                + "• InGuardDist：对手攻击是否已覆盖自身防御距离（AI 自动防御判断核心）。",
                "trigger1 = Command = \"QCF_x\" && MoveContact && HitPauseTime = 0", false));

        list.add(new DictEntry("Pos / Vel / ScreenPos / P2Dist / P2BodyDist / BackEdgeBodyDist / FrontEdgeBodyDist", "trigger", "Win/1.0/1.1/1.1b1/IK-GO",
                "位置、速度、对手距离与版边墙角距离触发器详解",
                "• Pos X / Y：相对屏幕水平中心与地面(y=0)坐标；Vel X / Y：当前速度。\n"
                + "• ScreenPos X / Y：相对屏幕左上角的像素坐标。\n"
                + "• P2BodyDist X：我方身体边缘到对手身体边缘的距离（贴身=0，重叠<0，AI 判定出招距离首选！）。\n"
                + "• P2Dist X / Y：双方轴心原点距离；ParentDist / RootDist：Helper 到父级/本体距离。\n"
                + "• BackEdgeBodyDist / FrontEdgeBodyDist：角色身体到后/前屏幕边缘距离（<=10 通常代表被压墙角）。",
                "trigger1 = P2BodyDist X = [0, 68] && P2StateType != L", false));

        list.add(new DictEntry("AILevel / Command / Random / PalNo / Life / Power / Const / Const240p~720p", "trigger", "Win/1.0/1.1/1.1b1/IK-GO",
                "AI 难度(1.0+)、搓招检测、随机数、色表号、血气值与分辨率自适应换算",
                "• AILevel（1.0/1.1/1.1b1/IK）：0=人类玩家，1~8=电脑 AI 难度（WinMUGEN 无此触发器）。\n"
                + "• Command = \"指令名\"：检测 .cmd 中定义的搓招是否触发。\n"
                + "• Random：每帧返回 0~999 随机整数。\n"
                + "• PalNo：当前选用色表编号（1~12）。\n"
                + "• Life / LifeMax / Power / PowerMax：当前与最大血量/气槽。\n"
                + "• Const(字段名)：读取 CNS 常量（如 Const(data.life)）。\n"
                + "• Const240p(v) / Const480p(v) / Const720p(v)（1.0+）：将像素值自动换算到角色 localcoord 坐标系。",
                "trigger1 = AILevel > 0 && Random < AILevel * 75 && Power >= 1000", false));

        list.add(new DictEntry("GetHitVar(...) / NumHelper / NumExplod / NumProjID / NumTarget / ProjHit", "trigger", "Win/1.0/1.1/1.1b1/IK-GO",
                "受击参数反查、实体存活数量统计与内置飞行道具命中检测",
                "• GetHitVar(参数)：读取上次受击的 damage、hitcount、fallcount、hittime、slidetime、hitshaketime、xvel、yvel、fall、guarded、isbound、chainid 等（IK 新增 attr/guardflag/hitflag/dizzypoints/guardpoints/redlife/playerid/frame）。\n"
                + "• NumHelper(id) / NumExplod(id) / NumProjID(id) / NumTarget(id)：统计指定 ID 存活数（省略 ID 查全部）。\n"
                + "• IsHelper / IsHelper(id) / ID / PlayerIDExist(id)。\n"
                + "• ProjHit<ID> = 1, < 10 / ProjGuarded<ID> / ProjContact<ID> / ProjHitTime(id)。",
                "trigger1 = !IsHelper && NumHelper(1005) = 0 && GetHitVar(hitcount) < 5", false));

        list.add(new DictEntry("IfElse vs Cond (1.1+) & CameraPos / CameraZoom & 跨实体重定向", "trigger", "Win/1.0/1.1/1.1b1/IK-GO",
                "短路三目 Cond(防除零/空目标报错)、1.1镜头触发器与跨实体链式重定向",
                "• IfElse(条件, A, B)：会**同时计算** A 和 B！若 B 含除零或无目标重定向会报错。\n"
                + "• Cond(条件, A, B)（1.1/1.1b1/IK 专属）：**短路求值**！仅计算命中分支，强烈推荐替代 IfElse。\n"
                + "• CameraPos X / Y、CameraZoom、ScreenWidth / ScreenHeight、LeftEdge / RightEdge / TopEdge / BottomEdge（1.1/1.1b1+）。\n"
                + "• 重定向：Root, / Parent, / Target(id), / Helper(id), / EnemyNear(n), / Partner, / PlayerID(id),。",
                "value = Cond(NumTarget > 0, Target, Pos X, Pos X)", false));
    }

    private void addDictIkemenTriggersDetailed(List<DictEntry> list) {
        list.add(new DictEntry("IK 增强重定向: P2 / Player(n) / PlayerIndex(n) / HelperIndex(n) / StateOwner / Helper(id, idx)", "trigger", "IK-GO",
                "【IK官方Wiki】精准对手锁定、遍历全场实体与多同 ID Helper 精确索引",
                "• P2, <触发器>：自动锁定主对手（忽略已 KO 的 5150 状态，比 EnemyNear 更稳定）。\n"
                + "• Helper(id, index) / Target(id, index)：新增第 2 参数 index(从0起)，彻底解决同 ID 多个 Helper 无法区分的问题！\n"
                + "• Player(n)：指向 1~8 号玩家（配合 PlayerNoExist(n)）。\n"
                + "• PlayerIndex(n) / HelperIndex(n)：按内部列表遍历全部实体（配合 NumPlayer / PlayerIndexExist / HelperIndexExist 与 ZSS for 循环）。\n"
                + "• StateOwner：在自定义受击状态里指向施法者本体。",
                "trigger1 = NumHelper(1005) >= 2 && Helper(1005, 1), MoveType = A", false));

        list.add(new DictEntry("IK 状态/变量查询: Map / IsAsserted / HitDefVar / MoveHitVar / ClsnOverlap / ExplodVar / ProjVar / StageConst", "trigger", "IK-GO",
                "【IK官方Wiki】全面反查引擎底层状态、判定框碰撞、特效/飞道/地图与战斗模式",
                "• Map(name[, idx])：读取具名 Map；IsAsserted(flag)：查 AssertSpecial 标志。\n"
                + "• HitDefVar(param)：查当前 HitDef 的 hitdamage/guardflag/hitflag/pausetime/sparkx/y。\n"
                + "• MoveHitVar(param)：查刚打出攻击的 frame(命中当帧)/playerid/sparkx/y/power/overridden。\n"
                + "• ClsnOverlap(box1, targetID, box2)：原生旋转/缩放判定盒重叠检测；ClsnVar(Clsn2, 0, Top/Bottom/Front/Back)。\n"
                + "• ExplodVar / ProjVar / SpriteVar / AnimElemVar / PalFXVar / ZoomVar / StageBGVar / StageConst / StageVar。\n"
                + "• InputTime(key)(精确蓄力帧数)、Analog(摇杆浮点)、AILevelF(浮点AI)、GameMode、GameVar、FightScreenState、MotifState、Dizzy/GuardBreak/RedLife/Standby/ComboCount/ReceivedDamage/JugglePoints。\n"
                + "• 数学新增：Atan2、Clamp、Lerp、Min、Max、Round、Sign、Deg、Rad、Float、RandomRange。",
                "trigger1 = ClsnOverlap(clsn1, P2, ID, clsn2) && InputTime(B) >= 30", false));
    }

    private void addDictZssAndLuaDetailed(List<DictEntry> list) {
        list.add(new DictEntry("ZSS 完整语法手册 (Zero State Script)", "zss_lua", "IK-GO",
                "Ikemen GO 现代化角色脚本语言：if/else/switch/for、let 局部变量与 [Function] 函数",
                "【核心语法】\n"
                + "1. 状态头：[Statedef 200; type: S; movetype: A; physics: S; anim: 200; ctrl: 0;]\n"
                + "2. 条件块：if cond { ... } else if cond2 { ... } else { ... }，彻底告别重复 trigger1/triggerall。\n"
                + "3. 控制器调用：hitDef{attr: S, NA; damage: 30, 0; guardflag: MA;}（分号分隔参数，支持 ignoreHitPause{} 与 persistent(0){} 块）。\n"
                + "4. 局部变量：let tmp = pos x + 20; 引用时加 $ 前缀($tmp)，不占用 var(0~59)。\n"
                + "5. 循环与分支：for i = 0; 9; 1 { ... }；switch stateNo { case 200, 210: ... default: ... }。\n"
                + "6. 自定义函数：[Function CalcDist(x, y) ret] ... 调用：call CalcDist(10, 20)。",
                "[Statedef 200; type: S; movetype: A; physics: S; anim: 200; ctrl: 0;]\nif time = 0 { playSnd{value: 0, 0} }\nif animElem = 3 {\n    hitDef{attr: S, NA; damage: 28, 0; guardflag: MA; pausetime: 8, 8;}\n}\nif animTime = 0 { changeState{value: 0; ctrl: 1;} }", false));

        list.add(new DictEntry("Ikemen GO Lua 模块与钩子 API 详解", "zss_lua", "IK-GO",
                "通过 external/mods/*.lua 扩展菜单、自定义模式、剧情 HUD 与全局渲染",
                "【挂载方式】将 .lua 脚本放入 external/mods/ 目录即可自动加载。\n\n"
                + "【核心 API 分类】\n"
                + "• 钩子注入：hook.add(\"loop\", \"my_id\", fn)（每帧渲染）、hook.add(\"main.t_itemname\", ...)（注册主菜单入口）。\n"
                + "• 文本渲染：textImgNew()、textImgSetFont / SetText / SetPos / SetScale / SetColor / textImgDraw()（v1.0+ 支持 <color:RRGGBB> 行内变色）。\n"
                + "• 动画与音效：animNew / animUpdate / animDraw、sndPlay(snd, g, i)、playBGM(path)。\n"
                + "• 输入与对战：commandGetState(cmd, 'u'/'d'/'a'...)、launchFight{p1=..., p2=..., stage=...}、config / motif / main 全局表。",
                "local txt = textImgNew()\ntextImgSetFont(txt, motif.font_data[1])\ntextImgSetPos(txt, 160, 24)\nhook.add(\"loop\", \"hud_demo\", function()\n    textImgSetText(txt, \"<color:38BDF8>IKEMEN MOD</color>\")\n    textImgDraw(txt)\nend)", false));
    }

    private void addDictFormatsAndFilesDetailed(List<DictEntry> list) {
        list.add(new DictEntry("角色配置文件 (.def) 逐项参数详解", "format", "Win/1.0/1.1/1.1b1/IK-GO",
                "角色总入口文件：[Info]、[Files]、[Arcade]、[Palette Keymap]、[Map]、[Shaders]",
                "【[Info] 区块】\n"
                + "• name / displayname / author：内部名 / 显示名 / 作者名。\n"
                + "• mugenversion：Win填 04,14,2001；1.0填 1.0；1.1/1.1b1填 1.1。\n"
                + "• ikemenVersion（IK专属）：填 1.0 开启全部现代 IK 机制（不填则按旧 MUGEN 兼容模式运行）。\n"
                + "• localcoord = 宽, 高（1.0+，默认 320, 240；高清角色常用 640, 480 或 1280, 720）。\n"
                + "• pal.defaults = 1,2,3,4：默认色表顺序；IK 新增 lifebarname、portraitscale、fightfx.prefix。\n\n"
                + "【[Files] 区块】\n"
                + "• cmd / cns / st / st0~st9 / stcommon：指令、常量、状态机与公共状态（IK 全部支持 .zss）。\n"
                + "• sprite(.sff) / anim(.air) / sound(.snd) / ai(.ai) / pal1~pal12(.act)。\n"
                + "• IK 专属：movelist / movelist1(暂停出招表)、font0~font9(Text控制器字体)、fx(角色专属CommonFX)。\n"
                + "【IK 新增区块】[Map](默认具名变量)、[Shaders](自定义GLSL/Vulkan着色器)。",
                "[Info]\nname = \"Kyo\"\ndisplayname = \"草薙京\"\nmugenversion = 1.1\nikemenVersion = 1.0\nlocalcoord = 320, 240\n\n[Files]\ncmd = kyo.cmd\ncns = kyo.cns\nst = kyo.cns\nstcommon = common1.cns\nsprite = kyo.sff\nanim = kyo.air\nsound = kyo.snd\nmovelist = movelist.dat", false));

        list.add(new DictEntry("角色常量文件 (.cns) 逐项参数详解", "format", "Win/1.0/1.1/1.1b1/IK-GO",
                "[Data] 血攻防气、[Size] 缩放体积、[Velocity] 速度、[Movement] 重力摩擦与 [Quotes]",
                "【[Data] 核心数值】\n"
                + "• life(血量默认1000)、power(气槽默认3000，1.0+)、attack(攻击倍率默认100)、defence(防御倍率默认100)、fall.defence_up(倒地增防%默认50)、liedown.time(倒地帧默认60)、airjuggle(浮空上限默认15)、sparkno / guard.sparkno、KO.echo、IntPersistIndex / FloatPersistIndex(跨局保留变量起始号)。\n"
                + "• IK 新增：dizzypoints(眩晕上限)、guardpoints(防槽上限)、hitsound.channel / guardsound.channel。\n\n"
                + "【[Size] 体型缩放】\n"
                + "• xscale / yscale(绘制缩放)、ground.back / ground.front(站立挤身宽)、air.back / air.front、height(跳跃越过高度)、attack.dist(触发防御距离)、head.pos / mid.pos(投技锚点)。\n"
                + "• IK 新增：stand/crouch/air/down.sizebox、weight(推挤重量默认100)、pushfactor、depth(Z轴宽)。\n\n"
                + "【[Velocity] & [Movement]】\n"
                + "• walk.fwd / walk.back、run.fwd / run.back、jump.neu / jump.back / jump.fwd、airjump.neu。\n"
                + "• yaccel(重力加速度默认0.44)、stand.friction / crouch.friction(摩擦系数默认0.85)、airjump.num(空中跳次数)。\n"
                + "【[Quotes](1.0+)】victory0~99；【[Constants](IK)】自定义常量；【[RemapPreset 名称](IK)】换图预设。",
                "[Data]\nlife = 1000\npower = 3000\nattack = 100\ndefence = 100\nairjuggle = 15\n\n[Velocity]\nwalk.fwd = 2.6\nwalk.back = -2.2\nrun.fwd = 5.2, 0\njump.neu = 0, -8.8", false));

        list.add(new DictEntry("动画与判定框 (.air) & 指令文件 (.cmd) 语法详解", "format", "Win/1.0/1.1/1.1b1/IK-GO",
                "AIR 帧序列、Clsn1/Clsn2、补间插值，以及 CMD 搓招符号与缓冲区配置",
                "【.air 动画文件】\n"
                + "• 帧行：Group, Item, OffX, OffY, Ticks [, Flip(H/V/VH) [, Trans(A/A1/S/ASxDx) [, ScaleX, ScaleY, Angle(1.1+)]]]。\n"
                + "• Ticks = -1 永久定格最后一帧；Loopstart 标记循环起点。\n"
                + "• Clsn1(红=攻击框)、Clsn2(蓝=防御框)；Clsn1Default/Clsn2Default 为后续帧默认框。\n"
                + "• 1.1+ 支持 Interpolate Offset/Scale/Angle/Blend；IK 新增 Copy Action N 与 SA/SASxDx(SubAdd)。\n\n"
                + "【.cmd 指令文件】\n"
                + "• [Defaults]：command.time(默认15帧)、command.buffer.time(默认1帧)；IK 新增 steptime、autogreater、buffer.hitpause/pauseend/shared。\n"
                + "• 符号：~松开(~30B蓄力30帧松开)、/按住(/30B持续蓄力)、$模糊方向($D含下/左下/右下)、+同时按、>严格无杂键；IK 新增 | 或逻辑(x|y|z)、*方向缓冲(*D)、绝对左右 L/R 与回中 N。\n"
                + "• [Statedef -1]：从上往下判定，超杀/必杀必须排在轻重拳脚前面！",
                "[Command]\nname = \"QCF_P\"\ncommand = ~D, DF, F, x | y | z\ntime = 18\nbuffer.time = 3", false));

        list.add(new DictEntry("图像包 (.sff v1/v2.00/v2.01) & 音频包 (.snd) 规范", "format", "Win/1.0/1.1/1.1b1/IK-GO",
                "SFF 三个版本差异（PCX vs 32位PNG）与 SND/BGM 全格式兼容表",
                "【SFF 图像包三代规范】\n"
                + "• SFFv1 (v1.01, WinMUGEN)：仅支持 8-bit 256 色 PCX，必须依赖外部 .act 文件换色。\n"
                + "• SFFv2.00 (MUGEN 1.0)：支持内置色表块 + RLE8/RLE5/LZ5 压缩，但仍限 8-bit 索引色。\n"
                + "• SFFv2.01 (MUGEN 1.1 / 1.1b1 / Ikemen GO)：原生支持 PNG8 / PNG24 / PNG32 真彩带半透明 Alpha 通道（1.1b1 必须开 OpenGL 渲染模式！）。\n"
                + "• 通用头像图号：9000,0 小头像(25x25)、9000,1 大头像(120x140)。\n\n"
                + "【SND 音频与 BGM 规范】\n"
                + "• MUGEN Win/1.0/1.1：.snd 仅支持未压缩 PCM WAV；BGM 支持 MP3/OGG/MIDI/MOD/XM。\n"
                + "• Ikemen GO：.snd 额外支持 OGG/MP3/FLAC；BGM 支持 FLAC、采样循环点(bgmloopstart/end)、多曲逗号随机、MIDI 音色库(sound/soundfont.sf2)。",
                "; SFFv1 只能替换 PCX | SFFv2 必须使用 PNG\n; SND 11,0 为角色 KO 惨叫保留音效", false));

        list.add(new DictEntry("引擎配置: mugen.cfg (Win/1.0/1.1b1) vs config.ini (IK-GO)", "format", "Win/1.0/1.1/1.1b1/IK-GO",
                "渲染器(OpenGL/Vulkan)、分辨率、1.1b1 4GB补丁、SOCD 与通用扩展配置详解",
                "• WinMUGEN mugen.cfg：[Video Win] Width/Height、Depth(16/32)、DXMode、BlitMode、Stretch。\n"
                + "• MUGEN 1.0 mugen.cfg：[Config] GameWidth/GameHeight(支持720p)、[Video] RenderMode = System/DirectX。\n"
                + "• MUGEN 1.1 / 1.1b1 mugen.cfg：[Video] RenderMode = OpenGL（1.1b1 启用 32位 PNG 与 Zoom 必须设为 OpenGL）、SafeMode = 0、SpriteDecompressionBufferSize；大型整合包需用 4GB Patch 工具突破 32位 2GB 内存限制。\n"
                + "• Ikemen GO save/config.ini：管理 OpenGL 2.1/3.2 / Vulkan 1.3、Rollback 帧延迟、SOCDResolution(0~4)、ButtonAssist(1帧双键宽容)、CommonStates/CommonFx/CommonShaders。",
                "[Video]\nRenderMode = OpenGL\nSafeMode = 0\n\n[Input]\nSOCDResolution = 4\nButtonAssist = 1", false));

        list.add(new DictEntry("界面/选人/血条/过场/字体/地图/出招表/着色器全配置速查", "format", "Win/1.0/1.1/1.1b1/IK-GO",
                "system.def、select.def、fight.def、storyboard.def、Font v2、3D地图、movelist.dat 与 Shader",
                "• system.def (Motif)：标题/选人/VS/胜利画面；IK 支持 menu.itemname.<子菜单>.<模式> 自定义层级，写空值隐藏默认项；支持 [Dialogue Info]、[Hiscore Info]、[Glyphs] 与 Order Select。\n"
                + "• select.def：[Characters] 填 角色, 地图, order=1, music=...（IK 支持 .zip 直读、ai/rounds/hidden/bonus/unlock）；[ExtraStages] 同样支持 .zip；[StoryMode] 剧情弧。\n"
                + "• fight.def：血条/气槽/连击/计时/KO；IK 新增 [Tag Lifebar](pX.red红血)、[StunBar]、[GuardBar]、[Score]、[Action]。\n"
                + "• storyboard.def：片头/结局过场；IK 支持 layerX.video 播 MP4 与战斗中 Storyboard 控制器调用。\n"
                + "• Font v2 (.def, 1.0+)：Type = truetype 可直读 .ttf 中文字体，Type = bitmap 引用 SFFv2；IK 支持 <color:RRGGBB> 行内变色。\n"
                + "• 地图 .def：1.1+ 支持 [Camera] startzoom/zoomout/zoomin；IK 支持 [Model] 加载 .gltf/.glb 3D 场景、AttachedChar1~4、RoundXDef、life.bgmusic、Z轴 [Scaling] 与 9000,1 地图预览图。\n"
                + "• movelist.dat (IK)：<#RRGGBBAA>文字</> 变色 + TAB 对齐 + _QCF/_DSF/^LP/^HP 等内置图标。\n"
                + "• 命令行与联机 (IK)：-p1.ai 8、-s stage0、-fight、-r motif、-debug、-ip、-tcpport 7500、-udpport。",
                "[Characters]\nkfm, stages/stage0.def, order=1, music=sound/kfm.ogg\nchars/boss.zip, stages/boss.def, order=5, ai=8", false));
    }

    private void addDictCommonStatesDetailed(List<DictEntry> list) {
        list.add(new DictEntry("全局常驻状态执行序: -4 / -3 / -2 / -1 / 正常状态 / +1", "common", "Win/1.0/1.1/1.1b1/IK-GO",
                "每帧状态机执行优先级、Pause 穿透规则与 Helper 继承机制全解",
                "每帧严格按以下顺序执行：\n"
                + "1. Statedef -4 [IK专属]：无视 Pause/SuperPause、无视自定义状态、Helper 无论 keyctrl 都必执行！\n"
                + "2. Statedef -3：仅本体在非自定义状态、非暂停时执行（Helper 不执行）。\n"
                + "3. Statedef -2：本体每帧执行（即使被对手抓入自定义状态也跑，暂停时不跑）。\n"
                + "4. Statedef -1：指令出招区（仅本体非自定义状态执行；Helper 需 keyctrl = 1）。\n"
                + "5. 正常状态 (0 ~ N)：当前 StateNo。\n"
                + "6. Statedef +1 [IK专属]：正常状态跑完后在帧尾执行，同样穿透暂停/自定义状态/Helper！\n"
                + "※ Ikemen GO 允许同一负状态在多个文件重复声明（自动追加合并）。",
                "[Statedef -4]\n[State -4, AlwaysRun]\ntype = MapAdd\ntrigger1 = 1\nmap = \"frame_cnt\"\nvalue = 1", false));

        list.add(new DictEntry("common1.cns 标准公共状态号大全 (0 ~ 5999 + IK 保留号)", "common", "Win/1.0/1.1/1.1b1/IK-GO",
                "移动、防御、受击、倒地、受身、KO、开局及 IK 眩晕/破防/换人保留状态号",
                "【基础移动与防御 (0~199)】\n"
                + "• 0 站立 | 10~12 下蹲/蹲/起 | 20 走 | 40 起跳 | 45 二段跳 | 50~52 滞空/下落/落地\n"
                + "• 100 前跑 | 105~106 后撤 | 120 防御准备 | 130~132 站蹲空防 | 140 防结束 | 150~155 受防硬直\n"
                + "• 170 败北 | 175 平局 | 180~189 胜利 | 190~194 开场 | 195 挑衅\n\n"
                + "【受击/倒地/受身/系统 (5000~5999)】\n"
                + "• 5000~5001 站受击 | 5010~5011 蹲受击 | 5020 空受击 | 5030~5050 击飞下落\n"
                + "• 5070~5071 绊倒 | 5080~5081 倒地追击 | 5100~5101 砸地弹起 | 5110 躺地 | 5120 起身无敌\n"
                + "• 5150 KO 阵亡 | 5200~5201 地面受身 | 5210 空中受身 | 5300 眩晕 | 5500 续关 | 5900 开局初始化\n\n"
                + "【Ikemen GO 系统保留状态号 (data/common.const)】\n"
                + "• 6565300~6565310：dizzy.zss 眩晕 | 6565400~6565401：guardbreak.zss 破防 | 6565600~6565621：tag.zss 换人",
                "; 在 State 180 随机分支胜利姿势：\n[State 180, WinBranch]\ntype = ChangeState\ntrigger1 = Time = 0\nvalue = 181 + (Random % 2)", false));
    }

    private void addDictCompatAndGuidesDetailed(List<DictEntry> list) {
        list.add(new DictEntry("全版本差异与移植指南: WinMUGEN → 1.0 → 1.1a4 → 1.1b1 → Ikemen GO", "compat", "Win/1.0/1.1/1.1b1/IK-GO",
                "五大历史版本能力边界、IK底层帧优势差异(+1 hittime)与常见闪退排查",
                "【版本演进】\n"
                + "• WinMUGEN：固定 320x240，仅 SFFv1(PCX)，无 AILevel/VictoryQuote/localcoord，语法极宽松。\n"
                + "• MUGEN 1.0：新增 localcoord、SFFv2.00、Font v2 TTF、AILevel、VictoryQuote、RemapPal、Const240p~720p。\n"
                + "• MUGEN 1.1 / 1.1b1：新增 OpenGL、SFFv2.01(32位PNG)、Zoom 缩放、CameraPos、Cond 短路三目、Explod 旋转补间（1.1b1 需开 RenderMode=OpenGL，大整合包需 4GB Patch）。\n"
                + "• Ikemen GO：64位无限内存、OpenGL/Vulkan、3D `.gltf` 场景、MP4 过场、ZSS/Lua、无限 var & 具名 Map、回滚联机、Tag/Dizzy/GuardBreak/RedLife/Movelist。\n\n"
                + "【IK 与 MUGEN 底层细微差异】\n"
                + "1. 防御与帧优势：无 ikemenVersion 的老角色所有 HitDef 自动 +1 hittime 保持手感一致。\n"
                + "2. 角色处理顺序：MoveType A 玩家 → A Helper → I 玩家 → H 玩家 → I/H Helper，消除 1P/2P 不公。\n"
                + "3. 常见问题：安卓 Linux 严格区分文件名大小写；Win 地图背景错位需把 tilespacing 补全 X,Y 两值；日韩乱码用【🔣 乱码解字板】转 UTF-8。",
                "; 调试快捷键：Ctrl+D 调试栏 | Ctrl+C 判定框 | F1 秒杀 | F4 重开 | Shift+F4 重载", false));
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
                ? L("📚 已内置 Win/1.0/1.1/1.1b1/IK-GO 全中文词库 (") + allEntries.size() + L(" 条)，点右上角可同步官方 GitHub 全部 Wiki")
                : L("✅ 上次官方同步: ") + lastSyncTime);
        applyGlobalFontSettings(syncStatus, 0.82f, false);
        syncStatus.setTextColor(Color.parseColor("#94A3B8"));
        syncStatus.setPadding(0, (int)(4*density), 0, (int)(4*density));
        headerArea.addView(syncStatus);

        // 版本筛选胶囊
        HorizontalScrollView verScroll = new HorizontalScrollView(getContext()); verScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout verRow = new LinearLayout(getContext()); verRow.setOrientation(LinearLayout.HORIZONTAL);
        final String[] verKeys = {"all", "Win", "1.0", "1.1", "1.1b1", "IK-GO"};
        final String[] verNames = {L("全部版本"), "WinMUGEN", "MUGEN 1.0", "MUGEN 1.1", "MUGEN 1.1b1", "Ikemen GO"};
        final Button[] verBtns = new Button[verKeys.length];

        // 分类标签栏
        HorizontalScrollView catScroll = new HorizontalScrollView(getContext()); catScroll.setHorizontalScrollBarEnabled(false);
        catScroll.setTag("layout_toolbar");
        LinearLayout catRow = new LinearLayout(getContext()); catRow.setOrientation(LinearLayout.HORIZONTAL);
        final String[] catKeys = {"all", "fav", "sctrl", "trigger", "zss_lua", "format", "common", "compat", "official"};
        final String[] catNames = {L("🔥 全部"), L("⭐ 收藏"), L("🎮 控制器"), L("⚡ 触发器"), L("🧬 ZSS/Lua"), L("📁 文件配置"), L("🧱 公共状态"), L("🔄 版本兼容"), L("🌐 官方同步")};
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
                if (matchCount > 400) break; // 防止一次渲染过多卡片卡顿

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

                    if (item.isOfficialSync) {
                        final String origText = item.detail.isEmpty() ? item.summary : item.detail;
                        final File zhCacheFile = new File(cacheDir, "zh_" + Math.abs(item.title.hashCode()) + ".txt");
                        final boolean[] showingZh = {false};
                        final String[] cachedZh = {null};
                        if (zhCacheFile.exists()) {
                            try {
                                StringBuilder zsb = new StringBuilder();
                                try (BufferedReader zbr = new BufferedReader(new InputStreamReader(new FileInputStream(zhCacheFile), "UTF-8"))) {
                                    String zl; while ((zl = zbr.readLine()) != null) zsb.append(zl).append("\n");
                                }
                                if (zsb.length() > 0) {
                                    cachedZh[0] = zsb.toString().trim();
                                    dbody.setText(cachedZh[0]);
                                    showingZh[0] = true;
                                }
                            } catch (Exception ignored) {}
                        }
                        Button bTrans = createButton(showingZh[0] ? L("📄 看英文原文") : L("🌐 译成中文"), "#7C3AED");
                        bTrans.setOnClickListener(tv -> {
                            if (showingZh[0]) {
                                dbody.setText(origText);
                                showingZh[0] = false;
                                bTrans.setText(L("🌐 译成中文"));
                            } else if (cachedZh[0] != null) {
                                dbody.setText(cachedZh[0]);
                                showingZh[0] = true;
                                bTrans.setText(L("📄 看英文原文"));
                            } else {
                                bTrans.setEnabled(false);
                                bTrans.setText(L("⏳ 翻译中..."));
                                new Thread(() -> {
                                    final String zh = translateWikiToChinese(origText);
                                    if (zh != null && !zh.isEmpty() && !zh.startsWith(L("【离线术语对照"))) {
                                        try (FileOutputStream fos = new FileOutputStream(zhCacheFile)) {
                                            fos.write(zh.getBytes("UTF-8"));
                                        } catch (Exception ignored) {}
                                        cachedZh[0] = zh;
                                    }
                                    uiHandler.post(() -> {
                                        bTrans.setEnabled(true);
                                        dbody.setText(zh);
                                        showingZh[0] = true;
                                        bTrans.setText(L("📄 看英文原文"));
                                    });
                                }).start();
                            }
                        });
                        actRow.addView(bTrans, arp);
                    }

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

        // 🔄 实时同步官方 GitHub 仓库 (ikemen-engine/Ikemen-GO 全部 Wiki + 官方核心配置/常量/ZSS)
        btnSync.setOnClickListener(v -> {
            btnSync.setEnabled(false);
            syncStatus.setText(L("⏳ 正在连接 GitHub 官方仓库读取文档目录 (_Sidebar.md)..."));
            new Thread(() -> {
                final String WIKI_RAW_BASE = "https://" + "raw.githubusercontent.com/wiki/ikemen-engine/Ikemen-GO/";
                final String REPO_RAW_MAIN = "https://" + "raw.githubusercontent.com/ikemen-engine/Ikemen-GO/main/";
                final String REPO_RAW_DEV  = "https://" + "raw.githubusercontent.com/ikemen-engine/Ikemen-GO/develop/";

                java.util.LinkedHashSet<String> wikiPages = new java.util.LinkedHashSet<>();
                // 核心官方 Wiki 全量必同步清单
                String[] defaults = new String[] {
                    "Home", "State-controllers-(new)", "State-controllers-(changed)",
                    "Triggers-(new)", "Triggers-(changed)", "ZSS", "Lua",
                    "Character-features", "Stage-features", "Background-features",
                    "Screenpack-features", "Storyboard-features", "Lifebar-features",
                    "Font-features", "Miscellaneous-info", "Command-line-arguments"
                };
                for (String p : defaults) wikiPages.add(p);

                // 动态解析 _Sidebar.md 自动发现官方仓库新增的所有 Wiki 页面
                String sidebarMd = httpGetWithFallback(WIKI_RAW_BASE + "_Sidebar.md");
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

                String[][] repoDocs = new String[][] {
                    { "data/system.base.def", "Official_system_base_def.txt", "## system.base.def 官方完整界面参数参考\n```ini\n", "\n```" },
                    { "data/common.const", "Official_common_const.txt", "## data/common.const 官方内置系统常量参考\n```ini\n", "\n```" },
                    { "data/common1.cns.zss", "Official_common1_cns_zss.txt", "## data/common1.cns.zss 官方公共状态源码参考\n```zss\n", "\n```" }
                };

                int total = wikiPages.size() + repoDocs.length;
                int done = 0, ok = 0;
                for (String slug : wikiPages) {
                    done++;
                    final int curD = done; final String curSlug = slug;
                    uiHandler.post(() -> syncStatus.setText(L("⏳ 正在同步官方 Wiki (") + curD + "/" + total + "): " + curSlug));
                    String md = httpGetWithFallback(WIKI_RAW_BASE + slug + ".md");
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

                // 同步官方核心配置/常量/公共状态参考文件
                for (String[] rd : repoDocs) {
                    done++;
                    final int curD = done; final String docPath = rd[0];
                    uiHandler.post(() -> syncStatus.setText(L("⏳ 正在同步官方仓库文件 (") + curD + "/" + total + "): " + docPath));
                    String body = httpGetWithFallback(REPO_RAW_MAIN + docPath);
                    if (body == null) body = httpGetWithFallback(REPO_RAW_DEV + docPath);
                    if (body != null && body.length() > 80) {
                        try (FileOutputStream fos = new FileOutputStream(new File(cacheDir, rd[1]))) {
                            fos.write((rd[2] + body + rd[3]).getBytes("UTF-8"));
                        } catch (Exception ignored) {}
                        ok++;
                    }
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

    // ======================================================================================
    // 🥷 模块 10：角色全能编辑器（基础属性/出招表/CMD解析/全状态技能预览/单技能伤害调整）
    // ======================================================================================
    private interface OnCharStateClick { void onClick(CharStateItem st); }

    private static class CharCmdItem {
        String name;
        String rawCommand;
        String prettyCommand;
        int targetState = -1;
        CharCmdItem(String name, String rawCommand, String prettyCommand) {
            this.name = name; this.rawCommand = rawCommand; this.prettyCommand = prettyCommand;
        }
    }

    private static class CharHitItem {
        File sourceFile;
        int lineIndex; // damage 所在行号（0-indexed）
        String controllerType; // HitDef / Projectile
        int hitDamage;
        int guardDamage;
        int origHitDamage;
        int origGuardDamage;
        String attr = "";
        String guardFlag = "";
        String pauseTime = "";
        boolean dirty = false;
    }

    private static class AirFrameSpec {
        int group;
        int item;
        int offX;
        int offY;
        int ticks;
        List<int[]> clsn1 = new ArrayList<>();
        List<int[]> clsn2 = new ArrayList<>();
    }

    private static class CharStateItem {
        int stateNo;
        String title = "";
        String type = "";
        String moveType = "";
        String physics = "";
        int anim = -1;
        int powerAdd = 0;
        boolean hasInvul = false;
        List<Integer> changeAnimTargets = new ArrayList<>();
        File sourceFile;
        String linkedCmd = "";
        String codePreview = "";
        List<CharHitItem> hits = new ArrayList<>();
    }

    private String readTextAutoCharset(File f) {
        if (f == null || !f.exists() || !f.isFile()) return "";
        try {
            byte[] raw = readFully(f);
            if (raw.length >= 3 && (raw[0] & 0xFF) == 0xEF && (raw[1] & 0xFF) == 0xBB && (raw[2] & 0xFF) == 0xBF) {
                return new String(raw, 3, raw.length - 3, "UTF-8");
            }
            String utf8 = new String(raw, "UTF-8");
            if (!utf8.contains("\uFFFD")) return utf8;
            try {
                String sjis = new String(raw, "Shift_JIS");
                if (!sjis.contains("\uFFFD")) return sjis;
            } catch (Exception ignored) {}
            try { return new String(raw, "GBK"); } catch (Exception ignored) {}
            return utf8;
        } catch (Exception e) {
            return "";
        }
    }

    private File resolveCharSubFile(File baseDir, String relPath) {
        if (baseDir == null || relPath == null) return null;
        String clean = relPath.trim().replace("\"", "").replace("\\", "/");
        if (clean.isEmpty()) return null;
        File direct = new File(baseDir, clean);
        if (direct.exists() && direct.isFile()) return direct;
        // Linux 大小写不敏感容错匹配
        File[] list = baseDir.listFiles();
        if (list != null) {
            String targetName = new File(clean).getName();
            for (File f : list) {
                if (f.isFile() && f.getName().equalsIgnoreCase(targetName)) return f;
            }
        }
        return null;
    }

    private String prettifyCmdSequence(String raw) {
        if (raw == null) return "";
        String[] parts = raw.split(",");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String tok = parts[i].trim();
            if (tok.isEmpty()) continue;
            tok = tok.replace("DF", "↘").replace("DB", "↙").replace("UF", "↗").replace("UB", "↖")
                     .replace("F", "→").replace("B", "←").replace("D", "↓").replace("U", "↑")
                     .replace("~", "松").replace("/", "蓄").replace("$", "").replace(">", "");
            if (sb.length() > 0) sb.append(" ");
            sb.append(tok);
        }
        return sb.toString();
    }

    // 严格按 Ikemen GO .cmd 规范清洗与修正按键序列：方向必须大写(U/D/B/F/UB/UF/DB/DF/L/R/N)，按键必须小写(a/b/c/x/y/z/s/d/w/m)
    private String sanitizeIkCommandSeq(String raw) {
        if (raw == null) return "s, s";
        String s = raw.trim()
                .replace('，', ',').replace('＋', '+').replace('～', '~')
                .replace('／', '/').replace('＄', '$').replace('＞', '>').replace('｜', '|');
        // 全角字母转半角
        StringBuilder asciiSb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0xFF21 && c <= 0xFF3A) asciiSb.append((char) (c - 0xFEE0));
            else if (c >= 0xFF41 && c <= 0xFF5A) asciiSb.append((char) (c - 0xFEE0));
            else if (c >= 0xFF10 && c <= 0xFF19) asciiSb.append((char) (c - 0xFEE0));
            else asciiSb.append(c);
        }
        s = asciiSb.toString().trim();
        if (s.isEmpty()) return "s, s";

        // 判断整个序列里是否带有明确方向键，用于智能区分用户误输的大写 B（是方向后B还是按键b）
        boolean hasExplicitDir = java.util.regex.Pattern.compile("(?<![A-Za-z])(DF|DB|UF|UB|U|D|F)(?![A-Za-z])").matcher(s).find();
        boolean hasUpperBtn = java.util.regex.Pattern.compile("(?<![A-Za-z])[ACXYZS](?![A-Za-z])").matcher(s).find();

        String[] steps = s.split(",");
        StringBuilder outSteps = new StringBuilder();
        for (String stepRaw : steps) {
            String step = stepRaw.trim();
            if (step.isEmpty()) continue;
            // 支持同时按(+)或逻辑或(|)
            String sep = step.contains("+") ? "+" : (step.contains("|") ? "|" : "");
            String[] atoms = sep.isEmpty() ? new String[]{step} : step.split(sep.equals("+") ? "\\+" : "\\|");
            boolean isChord = atoms.length > 1;
            StringBuilder outAtoms = new StringBuilder();
            for (String atomRaw : atoms) {
                String tok = atomRaw.trim();
                if (tok.isEmpty()) continue;
                // 提取前导修饰符（~, /, $, >, *, 以及 ~数字 如 ~30D）
                java.util.regex.Matcher mm = java.util.regex.Pattern.compile("^([~/$>*0-9]*)([A-Za-z]+)$").matcher(tok);
                if (!mm.matches()) continue;
                String prefix = mm.group(1);
                String sym = mm.group(2);
                String symUp = sym.toUpperCase();
                String symLow = sym.toLowerCase();
                String normSym = null;
                if (symUp.equals("DF") || symUp.equals("DB") || symUp.equals("UF") || symUp.equals("UB")) {
                    normSym = symUp;
                } else if (sym.equals("b") || sym.equals("d")) {
                    // 用户明确输入小写 b / d，保留为攻击键
                    normSym = sym;
                } else if (symUp.equals("B")) {
                    // 组合键(如 A+B) 或 与大写按键同行且无其他方向(如 A,B) 时自动转为按键 b，否则保留方向 B
                    normSym = (isChord || (!hasExplicitDir && hasUpperBtn)) ? "b" : "B";
                } else if (symUp.equals("U") || symUp.equals("D") || symUp.equals("F") || symUp.equals("L") || symUp.equals("R") || symUp.equals("N")) {
                    normSym = symUp;
                } else if (symLow.equals("a") || symLow.equals("c") || symLow.equals("x") || symLow.equals("y")
                        || symLow.equals("z") || symLow.equals("s") || symLow.equals("w") || symLow.equals("m")) {
                    normSym = symLow;
                }
                if (normSym == null) continue;
                if (outAtoms.length() > 0) outAtoms.append(sep);
                outAtoms.append(prefix).append(normSym);
            }
            if (outAtoms.length() > 0) {
                if (outSteps.length() > 0) outSteps.append(", ");
                outSteps.append(outAtoms);
            }
        }
        return outSteps.length() > 0 ? outSteps.toString() : "s, s";
    }

    // 生成符合 Ikemen GO 规范的 [Command] 块：对双键同按(如 a+b / x+y)自动补齐 /a, b 与 /b, a 容错并加 buffer.time，彻底解决触屏与键盘双键按不出来的问题
    private String buildIkCommandBlocks(String cmdName, String cleanSeq, int cmdTime) {
        StringBuilder sb = new StringBuilder();
        String compact = cleanSeq.replaceAll("\\s+", "");
        if (!compact.contains(",") && compact.contains("+")) {
            String[] parts = compact.split("\\+");
            sb.append("\n; [自动移植指令] ").append(cmdName).append(" (双键容错模式)\n");
            sb.append("[Command]\nname = \"").append(cmdName).append("\"\ncommand = ").append(compact).append("\ntime = 1\nbuffer.time = 5\n");
            if (parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()
                    && !parts[0].startsWith("/") && !parts[1].startsWith("/")
                    && !parts[0].startsWith("~") && !parts[1].startsWith("~")) {
                sb.append("[Command]\nname = \"").append(cmdName).append("\"\ncommand = /").append(parts[0]).append(", ").append(parts[1]).append("\ntime = 8\nbuffer.time = 5\n");
                sb.append("[Command]\nname = \"").append(cmdName).append("\"\ncommand = /").append(parts[1]).append(", ").append(parts[0]).append("\ntime = 8\nbuffer.time = 5\n");
            }
        } else {
            sb.append("\n; [自动移植指令] ").append(cmdName).append("\n");
            sb.append("[Command]\nname = \"").append(cmdName).append("\"\ncommand = ").append(cleanSeq).append("\ntime = ").append(cmdTime).append("\nbuffer.time = 4\n");
        }
        return sb.toString();
    }

    private void openCharacterEditorWindow() {
        final String winTitle = L("🥷 角色编辑器");
        final boolean[] isDirty = {false};
        final Runnable[] saveAllRef = {null};

        final Runnable closeNow = () -> {
            View win = windowsLayer.findViewWithTag(winTitle); if (win != null) windowsLayer.removeView(win);
            View tbBtn = taskbarAppsLayout.findViewWithTag("tb_" + winTitle); if (tbBtn != null) taskbarAppsLayout.removeView(tbBtn);
        };

        final Runnable closeInterceptor = () -> {
            if (isDirty[0]) {
                showWin10SavePrompt(
                    () -> { if (saveAllRef[0] != null) saveAllRef[0].run(); closeNow.run(); },
                    closeNow
                );
            } else {
                closeNow.run();
            }
        };

        View content = buildCharacterEditorContent(isDirty, saveAllRef);
        openAppWindow(winTitle, content, closeInterceptor);
    }

    private View buildCharacterEditorContent(final boolean[] isDirty, final Runnable[] saveAllRef) {
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#131720"));
        final Handler uiHandler = new Handler(Looper.getMainLooper());

        final File[] defFileRef = {null};
        final File[] cnsFileRef = {null};
        final File[] sffFileRef = {null};
        final File[] sndFileRef = {null};
        final File[] airFileRef = {null};
        final File[] cmdFileRef = {null};
        final File[] movelistFileRef = {null};
        final String[] movelistContentRef = {""};
        final List<File> allStateFiles = new ArrayList<>();
        final java.util.Map<String, String> defInfoMap = new java.util.LinkedHashMap<>();
        final java.util.Map<String, String> defFilesMap = new java.util.LinkedHashMap<>();
        final java.util.Map<String, String[]> cnsStatsMap = new java.util.LinkedHashMap<>();
        final List<CharCmdItem> cmdItems = new ArrayList<>();
        final List<CharStateItem> stateItems = new ArrayList<>();
        final java.util.Map<Integer, List<AirFrameSpec>> airActionFrames = new java.util.HashMap<>();
        final java.util.Map<Integer, String> airActionRawText = new java.util.HashMap<>();
        final List<GoEngineBridge.SffFrame> cachedSffFrames = new ArrayList<>();
        final java.util.Map<String, GoEngineBridge.SffFrame> sffFrameLookup = new java.util.HashMap<>();
        final int[] activeTab = {0};

        // 顶部角色英雄卡片（头像 + 信息 + 核心保存操作）
        LinearLayout heroCard = new LinearLayout(getContext());
        heroCard.setOrientation(LinearLayout.VERTICAL);
        heroCard.setPadding((int)(10*density), (int)(8*density), (int)(10*density), (int)(8*density));
        GradientDrawable heroBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor("#1B2333"), Color.parseColor("#171E2C")});
        heroBg.setStroke((int)(1*density), Color.parseColor("#2D3B55"));
        heroCard.setBackground(heroBg);

        LinearLayout heroTop = new LinearLayout(getContext());
        heroTop.setOrientation(LinearLayout.HORIZONTAL);
        heroTop.setGravity(Gravity.CENTER_VERTICAL);

        final ImageView portraitView = new ImageView(getContext());
        portraitView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        GradientDrawable pBg = new GradientDrawable(); pBg.setColor(Color.parseColor("#0D111A")); pBg.setCornerRadius(8f*density); pBg.setStroke((int)(1.5f*density), Color.parseColor("#38BDF8"));
        portraitView.setBackground(pBg);
        portraitView.setImageResource(android.R.drawable.ic_menu_myplaces);
        heroTop.addView(portraitView, new LinearLayout.LayoutParams((int)(64*density), (int)(64*density)));

        LinearLayout infoCol = new LinearLayout(getContext());
        infoCol.setOrientation(LinearLayout.VERTICAL);
        infoCol.setPadding((int)(10*density), 0, (int)(6*density), 0);
        final TextView tvCharName = new TextView(getContext());
        tvCharName.setText(L("未载入角色 (.def)"));
        applyGlobalFontSettings(tvCharName, 1.12f, true);
        tvCharName.setTextColor(Color.WHITE);
        final TextView tvCharMeta = new TextView(getContext());
        tvCharMeta.setText(L("点击下方【📂 选择角色 .def】自动解析血量/攻防/速度/出招表与全部技能"));
        applyGlobalFontSettings(tvCharMeta, 0.82f, false);
        tvCharMeta.setTextColor(Color.parseColor("#94A3B8"));
        final TextView tvDirtyBadge = new TextView(getContext());
        tvDirtyBadge.setText(L("⚪ 等待载入"));
        applyGlobalFontSettings(tvDirtyBadge, 0.78f, true);
        tvDirtyBadge.setTextColor(Color.parseColor("#38BDF8"));
        infoCol.addView(tvCharName); infoCol.addView(tvCharMeta); infoCol.addView(tvDirtyBadge);
        heroTop.addView(infoCol, new LinearLayout.LayoutParams(0, -2, 1f));
        heroCard.addView(heroTop);

        HorizontalScrollView actScroll = new HorizontalScrollView(getContext()); actScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout actBar = new LinearLayout(getContext()); actBar.setOrientation(LinearLayout.HORIZONTAL);
        actBar.setPadding(0, (int)(7*density), 0, 0);
        Button btnPickDef = createButton(L("📂 选择角色 .def"), "#0284C7");
        Button btnSaveAll = createButton(L("💾 保存生效"), "#16A34A");
        Button btnRevert = createButton(L("↩️ 放弃修改"), "#475569");
        Button btnJumpSff = createButton(L("🖼️ SFF"), "#6366F1");
        Button btnJumpSnd = createButton(L("🎵 SND"), "#D97706");
        Button btnJumpClsn = createButton(L("⚔️ 判定框"), "#DB2777");
        LinearLayout.LayoutParams abp = new LinearLayout.LayoutParams(-2, -2); abp.setMargins(0, 0, (int)(5*density), 0);
        actBar.addView(btnPickDef, abp); actBar.addView(btnSaveAll, abp); actBar.addView(btnRevert, abp);
        actBar.addView(btnJumpSff, abp); actBar.addView(btnJumpSnd, abp); actBar.addView(btnJumpClsn, abp);
        actScroll.addView(actBar);
        heroCard.addView(actScroll);
        root.addView(heroCard);

        HorizontalScrollView tabScroll = new HorizontalScrollView(getContext()); tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setTag("layout_toolbar");
        LinearLayout tabBar = new LinearLayout(getContext()); tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setPadding((int)(8*density), (int)(6*density), (int)(8*density), (int)(4*density));
        final String[] tabTitles = { L("📊 基础属性"), L("🥋 出招/指令"), L("⚔️ 状态与技能"), L("📁 关联文件") };
        final Button[] tabBtns = new Button[tabTitles.length];

        final FrameLayout tabContentHost = new FrameLayout(getContext());
        tabContentHost.setTag("layout_preview");

        final Runnable updateDirtyUI = () -> {
            if (defFileRef[0] == null) {
                tvDirtyBadge.setText(L("⚪ 等待载入"));
                tvDirtyBadge.setTextColor(Color.parseColor("#94A3B8"));
            } else if (isDirty[0]) {
                tvDirtyBadge.setText(L("🟠 有未保存修改（点击【💾 保存生效】才写入文件，不保存不生效）"));
                tvDirtyBadge.setTextColor(Color.parseColor("#FBBF24"));
            } else {
                tvDirtyBadge.setText(L("🟢 已与本地文件同步"));
                tvDirtyBadge.setTextColor(Color.parseColor("#4ADE80"));
            }
        };

        final ScrollView tab0Scroll = new ScrollView(getContext());
        final LinearLayout tab0Box = new LinearLayout(getContext()); tab0Box.setOrientation(LinearLayout.VERTICAL); tab0Box.setPadding((int)(10*density), (int)(6*density), (int)(10*density), (int)(16*density));
        tab0Scroll.addView(tab0Box);

        final LinearLayout tab1Root = new LinearLayout(getContext()); tab1Root.setOrientation(LinearLayout.VERTICAL); tab1Root.setPadding((int)(8*density), (int)(4*density), (int)(8*density), (int)(8*density));
        final LinearLayout tab2Root = new LinearLayout(getContext()); tab2Root.setOrientation(LinearLayout.VERTICAL); tab2Root.setPadding((int)(8*density), (int)(4*density), (int)(8*density), (int)(8*density));

        final ScrollView tab3Scroll = new ScrollView(getContext());
        final LinearLayout tab3Box = new LinearLayout(getContext()); tab3Box.setOrientation(LinearLayout.VERTICAL); tab3Box.setPadding((int)(10*density), (int)(6*density), (int)(10*density), (int)(16*density));
        tab3Scroll.addView(tab3Box);

        tabContentHost.addView(tab0Scroll, new FrameLayout.LayoutParams(-1, -1));
        tabContentHost.addView(tab1Root, new FrameLayout.LayoutParams(-1, -1));
        tabContentHost.addView(tab2Root, new FrameLayout.LayoutParams(-1, -1));
        tabContentHost.addView(tab3Scroll, new FrameLayout.LayoutParams(-1, -1));

        final Runnable switchTab = () -> {
            tab0Scroll.setVisibility(activeTab[0] == 0 ? View.VISIBLE : View.GONE);
            tab1Root.setVisibility(activeTab[0] == 1 ? View.VISIBLE : View.GONE);
            tab2Root.setVisibility(activeTab[0] == 2 ? View.VISIBLE : View.GONE);
            tab3Scroll.setVisibility(activeTab[0] == 3 ? View.VISIBLE : View.GONE);
            for (int i = 0; i < tabBtns.length; i++) {
                tabBtns[i].setBackgroundColor(activeTab[0] == i ? Color.parseColor("#0284C7") : Color.parseColor("#232B3B"));
            }
        };

        LinearLayout.LayoutParams tbp = new LinearLayout.LayoutParams(-2, -2); tbp.setMargins(0, 0, (int)(6*density), 0);
        for (int i = 0; i < tabTitles.length; i++) {
            final int idx = i;
            tabBtns[i] = createButton(tabTitles[i], "#232B3B");
            tabBtns[i].setPadding((int)(12*density), (int)(6*density), (int)(12*density), (int)(6*density));
            tabBtns[i].setOnClickListener(v -> { activeTab[0] = idx; switchTab.run(); });
            tabBar.addView(tabBtns[i], tbp);
        }
        tabScroll.addView(tabBar);
        root.addView(tabScroll);
        root.addView(tabContentHost, new LinearLayout.LayoutParams(-1, 0, 1f));
        switchTab.run();

        final OnCharStateClick[] openStateDetailRef = {null};
        final Runnable[] renderTab0Ref = {null};
        final Runnable[] renderTab1Ref = {null};
        final Runnable[] renderTab2Ref = {null};
        final Runnable[] renderTab3Ref = {null};

        // --- Tab 0：基础属性面板（血量/攻击/防御/气槽/速度/缩放/重力）---
        renderTab0Ref[0] = () -> {
            tab0Box.removeAllViews();
            if (defFileRef[0] == null) {
                TextView empty = new TextView(getContext()); empty.setText(L("请先选择角色 .def 文件")); applyGlobalFontSettings(empty, 0.95f, false); empty.setTextColor(Color.parseColor("#94A3B8")); tab0Box.addView(empty);
                return;
            }
            String[][] groups = new String[][] {
                { "SECTION", L("❤️ 核心战斗数值 ([Data])") },
                { "data|life", L("最大血量 (life)"), "1000" },
                { "data|power", L("最大气槽 (power)"), "3000" },
                { "data|attack", L("攻击倍率 % (attack)"), "100" },
                { "data|defence", L("防御倍率 % (defence)"), "100" },
                { "data|airjuggle", L("浮空连段点数 (airjuggle)"), "15" },
                { "data|liedown.time", L("倒地躺平帧数 (liedown.time)"), "60" },
                { "data|fall.defence_up", L("倒地防补正 % (fall.defence_up)"), "50" },
                { "SECTION", L("🏃 移动与跳跃速度 ([Velocity] & [Movement])") },
                { "velocity|walk.fwd", L("前进走速 (walk.fwd)"), "2.4" },
                { "velocity|walk.back", L("后退走速 (walk.back)"), "-2.2" },
                { "velocity|run.fwd", L("前冲跑速 (run.fwd)"), "4.6, 0" },
                { "velocity|run.back", L("后撤步速 (run.back)"), "-4.5, -3.8" },
                { "velocity|jump.neu", L("垂直起跳速 (jump.neu)"), "0, -8.4" },
                { "velocity|jump.fwd", L("前跳初速 (jump.fwd)"), "2.5" },
                { "velocity|jump.back", L("后跳初速 (jump.back)"), "-2.55" },
                { "movement|yaccel", L("重力加速度 (yaccel)"), "0.44" },
                { "movement|airjump.num", L("空中二段跳次数 (airjump.num)"), "1" },
                { "SECTION", L("📐 体型与缩放 ([Size])") },
                { "size|xscale", L("水平缩放 (xscale)"), "1" },
                { "size|yscale", L("垂直缩放 (yscale)"), "1" },
                { "size|attack.dist", L("默认防御触发距离 (attack.dist)"), "160" }
            };

            for (final String[] rowDef : groups) {
                if ("SECTION".equals(rowDef[0])) {
                    TextView sec = createSubTitle(rowDef[1]);
                    sec.setTextColor(Color.parseColor("#38BDF8"));
                    tab0Box.addView(sec);
                    continue;
                }
                final String mapKey = rowDef[0];
                String[] pair = cnsStatsMap.get(mapKey);
                if (pair == null) {
                    pair = new String[] { rowDef[2], rowDef[2] };
                    cnsStatsMap.put(mapKey, pair);
                }
                final String[] finalPair = pair;

                LinearLayout card = new LinearLayout(getContext());
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setPadding((int)(10*density), (int)(7*density), (int)(10*density), (int)(7*density));
                GradientDrawable cbg = new GradientDrawable();
                cbg.setColor(Color.parseColor("#1C2230")); cbg.setCornerRadius(7f*density); cbg.setStroke(1, Color.parseColor("#2E3A50"));
                card.setBackground(cbg);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2); clp.setMargins(0, 0, 0, (int)(5*density));

                TextView lbl = new TextView(getContext());
                lbl.setText(rowDef[1]);
                applyGlobalFontSettings(lbl, 0.88f, false);
                lbl.setTextColor(Color.parseColor("#E2E8F0"));
                card.addView(lbl, new LinearLayout.LayoutParams(0, -2, 1.3f));

                final EditText valEdit = createInput("", finalPair[1]);
                valEdit.setSingleLine(true);
                valEdit.setGravity(Gravity.CENTER);
                valEdit.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                    @Override public void afterTextChanged(Editable s) {
                        String nv = s.toString().trim();
                        if (!nv.equals(finalPair[1])) {
                            finalPair[1] = nv;
                            isDirty[0] = true;
                            updateDirtyUI.run();
                        }
                    }
                });
                card.addView(valEdit, new LinearLayout.LayoutParams(0, -2, 0.9f));

                if (!finalPair[1].contains(",")) {
                    Button bMinus = createButton("-10%", "#334155");
                    Button bPlus = createButton("+10%", "#0369A1");
                    bMinus.setPadding((int)(6*density), (int)(4*density), (int)(6*density), (int)(4*density));
                    bPlus.setPadding((int)(6*density), (int)(4*density), (int)(6*density), (int)(4*density));
                    View.OnClickListener stepAct = sv -> {
                        try {
                            double cur = Double.parseDouble(valEdit.getText().toString().trim());
                            boolean isInt = !valEdit.getText().toString().contains(".");
                            double mul = (sv == bPlus) ? 1.1 : 0.9;
                            double next = cur * mul;
                            String outStr = isInt ? String.valueOf((int) Math.round(next)) : String.format(java.util.Locale.US, "%.2f", next);
                            valEdit.setText(outStr);
                        } catch (Exception ignored) {}
                    };
                    bMinus.setOnClickListener(stepAct); bPlus.setOnClickListener(stepAct);
                    LinearLayout.LayoutParams mbp = new LinearLayout.LayoutParams(-2, -2); mbp.setMargins((int)(4*density), 0, 0, 0);
                    card.addView(bMinus, mbp); card.addView(bPlus, mbp);
                }

                tab0Box.addView(card, clp);
            }
        };

        // --- Tab 1：出招表（优先 movelist / 同目录出招表，否则自动解析 CMD）---
        final boolean[] showRawMovelist = {true};
        renderTab1Ref[0] = () -> {
            tab1Root.removeAllViews();
            if (defFileRef[0] == null) return;
            boolean hasMovelist = movelistFileRef[0] != null && !movelistContentRef[0].trim().isEmpty();

            LinearLayout modeRow = new LinearLayout(getContext()); modeRow.setOrientation(LinearLayout.HORIZONTAL);
            Button bMv = createButton(hasMovelist ? L("📜 出招表: ") + movelistFileRef[0].getName() : L("📜 未检测到出招表"), hasMovelist && showRawMovelist[0] ? "#0284C7" : "#252D3D");
            Button bCmd = createButton(L("🎮 CMD 指令解析 (") + cmdItems.size() + ")", (!hasMovelist || !showRawMovelist[0]) ? "#0284C7" : "#252D3D");
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, -2, 1f); mp.setMargins((int)(3*density), 0, (int)(3*density), (int)(6*density));
            bMv.setOnClickListener(v -> { if (hasMovelist) { showRawMovelist[0] = true; renderTab1Ref[0].run(); } else Toast.makeText(getContext(), L("该角色未提供独立出招表，已自动展示 CMD 解析"), Toast.LENGTH_SHORT).show(); });
            bCmd.setOnClickListener(v -> { showRawMovelist[0] = false; renderTab1Ref[0].run(); });
            modeRow.addView(bMv, mp); modeRow.addView(bCmd, mp);
            tab1Root.addView(modeRow);

            ScrollView sv = new ScrollView(getContext());
            LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL);
            sv.addView(box, new FrameLayout.LayoutParams(-1, -2));
            tab1Root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

            if (hasMovelist && showRawMovelist[0]) {
                TextView tv = new TextView(getContext());
                tv.setText(movelistContentRef[0]);
                tv.setTypeface(Typeface.MONOSPACE);
                applyGlobalFontSettings(tv, 0.9f, false);
                tv.setTextColor(Color.parseColor("#E2E8F0"));
                tv.setTextIsSelectable(true);
                tv.setPadding((int)(10*density), (int)(10*density), (int)(10*density), (int)(10*density));
                GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#181E2A")); bg.setCornerRadius(8f*density); bg.setStroke(1, Color.parseColor("#334155"));
                tv.setBackground(bg);
                box.addView(tv, new LinearLayout.LayoutParams(-1, -2));
            } else {
                if (cmdItems.isEmpty()) {
                    TextView emp = new TextView(getContext()); emp.setText(L("未在 CMD 中解析到 [Command] 指令")); applyGlobalFontSettings(emp, 0.9f, false); emp.setTextColor(Color.parseColor("#94A3B8")); box.addView(emp);
                } else {
                    for (final CharCmdItem ci : cmdItems) {
                        LinearLayout card = new LinearLayout(getContext()); card.setOrientation(LinearLayout.HORIZONTAL); card.setGravity(Gravity.CENTER_VERTICAL);
                        card.setPadding((int)(10*density), (int)(8*density), (int)(10*density), (int)(8*density));
                        GradientDrawable cbg = new GradientDrawable(); cbg.setColor(Color.parseColor("#1C2230")); cbg.setCornerRadius(7f*density); cbg.setStroke(1, Color.parseColor("#334155"));
                        card.setBackground(cbg);
                        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2); clp.setMargins(0, 0, 0, (int)(5*density));

                        LinearLayout left = new LinearLayout(getContext()); left.setOrientation(LinearLayout.VERTICAL);
                        TextView tName = new TextView(getContext()); tName.setText("🎯 " + ci.name + (ci.targetState >= 0 ? "  →  State " + ci.targetState : ""));
                        applyGlobalFontSettings(tName, 0.92f, true); tName.setTextColor(Color.parseColor("#F8FAFC"));
                        TextView tSeq = new TextView(getContext()); tSeq.setText(L("搓招: ") + ci.prettyCommand + "   (" + ci.rawCommand + ")");
                        applyGlobalFontSettings(tSeq, 0.82f, false); tSeq.setTextColor(Color.parseColor("#38BDF8"));
                        left.addView(tName); left.addView(tSeq);
                        card.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));

                        if (ci.targetState >= 0) {
                            Button bGo = createButton(L("🎬 看招式"), "#0F766E");
                            bGo.setPadding((int)(8*density), (int)(5*density), (int)(8*density), (int)(5*density));
                            bGo.setOnClickListener(gv -> {
                                for (CharStateItem st : stateItems) {
                                    if (st.stateNo == ci.targetState) {
                                        if (openStateDetailRef[0] != null) openStateDetailRef[0].onClick(st);
                                        return;
                                    }
                                }
                                Toast.makeText(getContext(), L("未找到 State ") + ci.targetState + L(" 的定义"), Toast.LENGTH_SHORT).show();
                            });
                            card.addView(bGo);
                        }
                        box.addView(card, clp);
                    }
                }
            }
        };

        // --- Tab 2：全部状态与技能预览 + 伤害调整 ---
        final String[] stateFilterCat = {"all"};
        final String[] stateQuery = {""};
        renderTab2Ref[0] = () -> {
            tab2Root.removeAllViews();
            if (defFileRef[0] == null) return;

            LinearLayout topRow = new LinearLayout(getContext()); topRow.setOrientation(LinearLayout.HORIZONTAL); topRow.setGravity(Gravity.CENTER_VERTICAL);
            final EditText qIn = createInput(L("🔍 搜状态号 / 技能名 / 指令..."), stateQuery[0]);
            qIn.setSingleLine(true);
            Button bGlobalMul = createButton(L("⚡ 全局调伤"), "#D97706");
            bGlobalMul.setPadding((int)(10*density), (int)(6*density), (int)(10*density), (int)(6*density));
            LinearLayout.LayoutParams gbp = new LinearLayout.LayoutParams(-2, -2); gbp.setMargins((int)(5*density), 0, 0, 0);
            topRow.addView(qIn, new LinearLayout.LayoutParams(0, -2, 1f));
            topRow.addView(bGlobalMul, gbp);
            tab2Root.addView(topRow);

            HorizontalScrollView fScroll = new HorizontalScrollView(getContext()); fScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout fRow = new LinearLayout(getContext()); fRow.setOrientation(LinearLayout.HORIZONTAL);
            fRow.setPadding(0, (int)(5*density), 0, (int)(5*density));
            final String[] fKeys = {"all", "atk", "special", "basic"};
            final String[] fNames = {L("全部状态"), L("⚔️ 攻击技(有伤害)"), L("🔥 必杀/超杀(>=1000)"), L("🧱 基础/其他")};
            final Button[] fBtns = new Button[fKeys.length];

            ScrollView sv = new ScrollView(getContext());
            final LinearLayout listBox = new LinearLayout(getContext()); listBox.setOrientation(LinearLayout.VERTICAL);
            sv.addView(listBox, new FrameLayout.LayoutParams(-1, -2));

            final Runnable renderListOnly = () -> {
                listBox.removeAllViews();
                String q = stateQuery[0].trim().toLowerCase();
                int shown = 0;
                for (final CharStateItem st : stateItems) {
                    if ("atk".equals(stateFilterCat[0]) && st.hits.isEmpty()) continue;
                    if ("special".equals(stateFilterCat[0]) && st.stateNo < 1000) continue;
                    if ("basic".equals(stateFilterCat[0]) && (!st.hits.isEmpty() || st.stateNo >= 1000)) continue;
                    if (!q.isEmpty()) {
                        String hay = (st.stateNo + " " + st.title + " " + st.linkedCmd + " " + st.moveType).toLowerCase();
                        if (!hay.contains(q)) continue;
                    }
                    shown++;
                    if (shown > 300) break;

                    LinearLayout card = new LinearLayout(getContext()); card.setOrientation(LinearLayout.VERTICAL);
                    card.setPadding((int)(10*density), (int)(8*density), (int)(10*density), (int)(8*density));
                    boolean hasDirtyHit = false;
                    for (CharHitItem h : st.hits) if (h.dirty) { hasDirtyHit = true; break; }
                    GradientDrawable cbg = new GradientDrawable();
                    cbg.setColor(Color.parseColor("#1B2230")); cbg.setCornerRadius(8f*density);
                    cbg.setStroke((int)(1*density), Color.parseColor(hasDirtyHit ? "#F59E0B" : (!st.hits.isEmpty() ? "#EF4444" : "#334155")));
                    card.setBackground(cbg);
                    LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2); clp.setMargins(0, 0, 0, (int)(6*density));

                    LinearLayout head = new LinearLayout(getContext()); head.setOrientation(LinearLayout.HORIZONTAL); head.setGravity(Gravity.CENTER_VERTICAL);
                    TextView tvNo = new TextView(getContext());
                    tvNo.setText(" State " + st.stateNo + " ");
                    applyGlobalFontSettings(tvNo, 0.8f, true); tvNo.setTextColor(Color.WHITE);
                    GradientDrawable nbg = new GradientDrawable(); nbg.setColor(Color.parseColor(!st.hits.isEmpty() ? "#B91C1C" : "#1E3A5F")); nbg.setCornerRadius(5f*density);
                    tvNo.setBackground(nbg); tvNo.setPadding((int)(5*density), (int)(2*density), (int)(5*density), (int)(2*density));
                    head.addView(tvNo);

                    TextView tvTitle = new TextView(getContext());
                    String displayLabel = !st.title.isEmpty() ? st.title : (!st.linkedCmd.isEmpty() ? st.linkedCmd : L("未命名状态"));
                    tvTitle.setText("  " + displayLabel);
                    applyGlobalFontSettings(tvTitle, 0.94f, true); tvTitle.setTextColor(Color.parseColor("#F8FAFC"));
                    tvTitle.setSingleLine(true); tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    head.addView(tvTitle, new LinearLayout.LayoutParams(0, -2, 1f));

                    if (!st.hits.isEmpty()) {
                        CharHitItem h0 = st.hits.get(0);
                        TextView tvDmg = new TextView(getContext());
                        tvDmg.setText(" 💥" + h0.hitDamage + "/" + h0.guardDamage + (st.hits.size() > 1 ? " (+" + (st.hits.size()-1) + ")" : "") + " ");
                        applyGlobalFontSettings(tvDmg, 0.8f, true); tvDmg.setTextColor(Color.parseColor("#FDE047"));
                        head.addView(tvDmg);
                    }
                    card.addView(head);

                    TextView tvSub = new TextView(getContext());
                    StringBuilder sub = new StringBuilder();
                    sub.append("Type:").append(st.type.isEmpty() ? "-" : st.type)
                       .append(" | Move:").append(st.moveType.isEmpty() ? "-" : st.moveType)
                       .append(" | Anim:").append(st.anim >= 0 ? st.anim : "-");
                    if (!st.linkedCmd.isEmpty()) sub.append(" | 🎯").append(st.linkedCmd);
                    tvSub.setText(sub.toString());
                    applyGlobalFontSettings(tvSub, 0.78f, false); tvSub.setTextColor(Color.parseColor("#94A3B8"));
                    tvSub.setPadding(0, (int)(3*density), 0, 0);
                    card.addView(tvSub);

                    card.setOnClickListener(cv -> {
                        if (openStateDetailRef[0] != null) openStateDetailRef[0].onClick(st);
                    });
                    listBox.addView(card, clp);
                }
                if (shown == 0) {
                    TextView emp = new TextView(getContext()); emp.setText(L("未找到匹配的状态或技能")); applyGlobalFontSettings(emp, 0.9f, false); emp.setTextColor(Color.parseColor("#94A3B8")); listBox.addView(emp);
                }
            };

            LinearLayout.LayoutParams fbp = new LinearLayout.LayoutParams(-2, -2); fbp.setMargins(0, 0, (int)(5*density), 0);
            for (int i = 0; i < fKeys.length; i++) {
                final int idx = i;
                fBtns[i] = createButton(fNames[i], stateFilterCat[0].equals(fKeys[i]) ? "#6366F1" : "#232B3B");
                fBtns[i].setPadding((int)(10*density), (int)(4*density), (int)(10*density), (int)(4*density));
                fBtns[i].setOnClickListener(fv -> {
                    stateFilterCat[0] = fKeys[idx];
                    for (int j = 0; j < fBtns.length; j++) fBtns[j].setBackgroundColor(j == idx ? Color.parseColor("#6366F1") : Color.parseColor("#232B3B"));
                    renderListOnly.run();
                });
                fRow.addView(fBtns[i], fbp);
            }
            fScroll.addView(fRow);
            tab2Root.addView(fScroll);
            tab2Root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

            qIn.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) { stateQuery[0] = s.toString(); renderListOnly.run(); }
            });

            bGlobalMul.setOnClickListener(v -> {
                final Dialog gd = new Dialog(getContext());
                if (gd.getWindow() != null) gd.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                LinearLayout gb = new LinearLayout(getContext()); gb.setOrientation(LinearLayout.VERTICAL); gb.setPadding((int)(16*density), (int)(16*density), (int)(16*density), (int)(16*density));
                GradientDrawable gbg = new GradientDrawable(); gbg.setColor(Color.parseColor("#F51B2230")); gbg.setCornerRadius(12f*density); gbg.setStroke((int)(1.5f*density), Color.parseColor("#F59E0B")); gb.setBackground(gbg);
                gb.addView(createSubTitle(L("⚡ 一键缩放该角色全部技能伤害")));
                final EditText mulIn = createInput(L("输入倍率，如 1.2 或 0.8"), "1.2");
                gb.addView(mulIn);
                LinearLayout row = new LinearLayout(getContext()); row.setOrientation(LinearLayout.HORIZONTAL);
                final float[] presets = {0.5f, 0.8f, 1.2f, 1.5f, 2.0f};
                LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, -2, 1f); pp.setMargins((int)(2*density), (int)(8*density), (int)(2*density), 0);
                for (final float p : presets) {
                    Button pb = createButton(p + "x", "#334155");
                    pb.setPadding((int)(4*density), (int)(5*density), (int)(4*density), (int)(5*density));
                    pb.setOnClickListener(pv -> mulIn.setText(String.valueOf(p)));
                    row.addView(pb, pp);
                }
                gb.addView(row);
                Button bApply = createButton(L("✔️ 应用到全部技能 (需保存生效)"), "#D97706");
                bApply.setOnClickListener(av -> {
                    try {
                        float m = Float.parseFloat(mulIn.getText().toString().trim());
                        if (m <= 0f || m > 100f) return;
                        int cnt = 0;
                        for (CharStateItem st : stateItems) {
                            for (CharHitItem h : st.hits) {
                                h.hitDamage = Math.max(0, Math.round(h.hitDamage * m));
                                h.guardDamage = Math.max(0, Math.round(h.guardDamage * m));
                                h.dirty = true;
                                cnt++;
                            }
                        }
                        if (cnt > 0) {
                            isDirty[0] = true; updateDirtyUI.run(); renderListOnly.run();
                            Toast.makeText(getContext(), L("✅ 已调整 ") + cnt + L(" 处技能伤害（点顶部【💾 保存生效】写入）"), Toast.LENGTH_LONG).show();
                        }
                        gd.dismiss();
                    } catch (Exception e) { Toast.makeText(getContext(), L("❌ 请输入有效数字倍率"), Toast.LENGTH_SHORT).show(); }
                });
                LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, -2); ap.setMargins(0, (int)(12*density), 0, 0);
                gb.addView(bApply, ap);
                gd.setContentView(gb, new ViewGroup.LayoutParams((int)(300*density), -2)); gd.show();
            });

            renderListOnly.run();
        };

        // --- 技能详情 + SFF 动作与判定框(攻/防/无敌)开关预览 + 完整代码与双模式技能复刻 ---
        openStateDetailRef[0] = (final CharStateItem st) -> {
            final Dialog sd = new Dialog(getContext());
            if (sd.getWindow() != null) sd.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            ScrollView dsv = new ScrollView(getContext());
            LinearLayout dbox = new LinearLayout(getContext()); dbox.setOrientation(LinearLayout.VERTICAL);
            dbox.setPadding((int)(14*density), (int)(14*density), (int)(14*density), (int)(14*density));
            GradientDrawable dbg = new GradientDrawable(); dbg.setColor(Color.parseColor("#F8151B26")); dbg.setCornerRadius(12f*density); dbg.setStroke((int)(1.5f*density), Color.parseColor("#38BDF8")); dbox.setBackground(dbg);

            TextView dTitle = new TextView(getContext());
            dTitle.setText("⚔️ State " + st.stateNo + (st.title.isEmpty() ? "" : " · " + st.title));
            applyGlobalFontSettings(dTitle, 1.12f, true); dTitle.setTextColor(Color.WHITE); dbox.addView(dTitle);

            TextView dMeta = new TextView(getContext());
            dMeta.setText("Type=" + st.type + " | MoveType=" + st.moveType + " | Anim=" + st.anim + (st.hasInvul ? " | 🛡️NotHitBy" : "") + (st.linkedCmd.isEmpty() ? "" : " | 🎯 " + st.linkedCmd) + (st.sourceFile != null ? "\n📄 " + st.sourceFile.getName() : ""));
            applyGlobalFontSettings(dMeta, 0.8f, false); dMeta.setTextColor(Color.parseColor("#38BDF8")); dMeta.setPadding(0, (int)(3*density), 0, (int)(8*density));
            dbox.addView(dMeta);

            final boolean[] animPlaying = {false};
            final boolean[] showBoxes = {true};
            final long[] animGen = {0};
            final int[] curAnimIdx = {0};
            final List<AirFrameSpec> framesToPlay = new ArrayList<>();

            // 多级兜底解析动作帧，确保全部状态都能预览动作与图片
            int resolvedAnim = -1;
            String previewSourceNote = "";
            if (st.anim >= 0 && airActionFrames.containsKey(st.anim) && !airActionFrames.get(st.anim).isEmpty()) {
                resolvedAnim = st.anim;
                framesToPlay.addAll(airActionFrames.get(resolvedAnim));
                previewSourceNote = L("AIR动作 ") + resolvedAnim;
            }
            if (framesToPlay.isEmpty() && !st.changeAnimTargets.isEmpty()) {
                for (int ca : st.changeAnimTargets) {
                    if (airActionFrames.containsKey(ca) && !airActionFrames.get(ca).isEmpty()) {
                        resolvedAnim = ca;
                        framesToPlay.addAll(airActionFrames.get(ca));
                        previewSourceNote = L("ChangeAnim 动作 ") + ca;
                        break;
                    }
                }
            }
            if (framesToPlay.isEmpty() && airActionFrames.containsKey(st.stateNo) && !airActionFrames.get(st.stateNo).isEmpty()) {
                resolvedAnim = st.stateNo;
                framesToPlay.addAll(airActionFrames.get(st.stateNo));
                previewSourceNote = L("同号AIR动作 ") + st.stateNo;
            }
            if (framesToPlay.isEmpty() && !cachedSffFrames.isEmpty()) {
                int tryGroup = (st.anim >= 0) ? st.anim : st.stateNo;
                for (GoEngineBridge.SffFrame sf : cachedSffFrames) {
                    if (sf.group == tryGroup) {
                        AirFrameSpec af = new AirFrameSpec(); af.group = sf.group; af.item = sf.item; af.ticks = 6;
                        framesToPlay.add(af);
                        if (framesToPlay.size() >= 30) break;
                    }
                }
                if (!framesToPlay.isEmpty()) {
                    resolvedAnim = tryGroup;
                    previewSourceNote = L("SFF同组兜底 Group ") + tryGroup;
                }
            }
            if (framesToPlay.isEmpty() && airActionFrames.containsKey(0) && !airActionFrames.get(0).isEmpty()) {
                resolvedAnim = 0;
                framesToPlay.addAll(airActionFrames.get(0));
                previewSourceNote = L("默认站姿兜底 AIR 0");
            }
            if (framesToPlay.isEmpty() && !cachedSffFrames.isEmpty()) {
                for (GoEngineBridge.SffFrame sf : cachedSffFrames) {
                    if (sf.group == 0) {
                        AirFrameSpec af = new AirFrameSpec(); af.group = sf.group; af.item = sf.item; af.ticks = 6;
                        framesToPlay.add(af);
                        if (framesToPlay.size() >= 12) break;
                    }
                }
                if (framesToPlay.isEmpty()) {
                    GoEngineBridge.SffFrame sf0 = cachedSffFrames.get(0);
                    AirFrameSpec af = new AirFrameSpec(); af.group = sf0.group; af.item = sf0.item; af.ticks = 6;
                    framesToPlay.add(af);
                }
                previewSourceNote = L("SFF精灵兜底预览");
            }
            final int finalResolvedAnim = resolvedAnim;
            final String finalSourceNote = previewSourceNote;

            if (sffFileRef[0] != null && sffFileRef[0].exists() && !framesToPlay.isEmpty()) {
                final Bitmap[] curBmpRef = {null};
                final AirFrameSpec[] curSpecRef = {framesToPlay.get(0)};

                final View animCanvas = new View(getContext()) {
                    private final Paint pImg = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                    private final Paint pAxis = new Paint(Paint.ANTI_ALIAS_FLAG);
                    private final Paint pAtkFill = new Paint(Paint.ANTI_ALIAS_FLAG);
                    private final Paint pAtkStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
                    private final Paint pDefFill = new Paint(Paint.ANTI_ALIAS_FLAG);
                    private final Paint pDefStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
                    private final Paint pInvFill = new Paint(Paint.ANTI_ALIAS_FLAG);
                    private final Paint pInvStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
                    private final Paint pLbl = new Paint(Paint.ANTI_ALIAS_FLAG);
                    {
                        pAxis.setColor(Color.parseColor("#5594A3B8")); pAxis.setStrokeWidth(1.2f * density);
                        pAtkFill.setColor(Color.parseColor("#44EF4444")); pAtkFill.setStyle(Paint.Style.FILL);
                        pAtkStroke.setColor(Color.parseColor("#FFEF4444")); pAtkStroke.setStyle(Paint.Style.STROKE); pAtkStroke.setStrokeWidth(2f * density);
                        pDefFill.setColor(Color.parseColor("#443B82F6")); pDefFill.setStyle(Paint.Style.FILL);
                        pDefStroke.setColor(Color.parseColor("#FF38BDF8")); pDefStroke.setStyle(Paint.Style.STROKE); pDefStroke.setStrokeWidth(2f * density);
                        pInvFill.setColor(Color.parseColor("#33FACC15")); pInvFill.setStyle(Paint.Style.FILL);
                        pInvStroke.setColor(Color.parseColor("#FFFACC15")); pInvStroke.setStyle(Paint.Style.STROKE); pInvStroke.setStrokeWidth(2.2f * density);
                        pInvStroke.setPathEffect(new android.graphics.DashPathEffect(new float[]{8f*density, 5f*density}, 0));
                        pLbl.setColor(Color.parseColor("#FDE047")); pLbl.setTextSize(10.5f * density); pLbl.setFakeBoldText(true);
                    }
                    @Override protected void onDraw(Canvas canvas) {
                        super.onDraw(canvas);
                        int vw = getWidth(), vh = getHeight();
                        if (vw <= 0 || vh <= 0) return;
                        Bitmap bmp = curBmpRef[0];
                        AirFrameSpec sp = curSpecRef[0];
                        if (bmp == null) return;

                        GoEngineBridge.SffFrame meta = sffFrameLookup.get(sp.group + "," + sp.item);
                        int ax = (meta != null) ? meta.x : (bmp.getWidth() / 2);
                        int ay = (meta != null) ? meta.y : bmp.getHeight();
                        float imgL = -ax + sp.offX;
                        float imgT = -ay + sp.offY;
                        float imgR = imgL + bmp.getWidth();
                        float imgB = imgT + bmp.getHeight();

                        float minX = imgL, maxX = imgR, minY = imgT, maxY = imgB;
                        if (showBoxes[0]) {
                            for (int[] b : sp.clsn1) { minX = Math.min(minX, Math.min(b[0], b[2])); maxX = Math.max(maxX, Math.max(b[0], b[2])); minY = Math.min(minY, Math.min(b[1], b[3])); maxY = Math.max(maxY, Math.max(b[1], b[3])); }
                            for (int[] b : sp.clsn2) { minX = Math.min(minX, Math.min(b[0], b[2])); maxX = Math.max(maxX, Math.max(b[0], b[2])); minY = Math.min(minY, Math.min(b[1], b[3])); maxY = Math.max(maxY, Math.max(b[1], b[3])); }
                        }
                        float bw = Math.max(16f, maxX - minX);
                        float bh = Math.max(16f, maxY - minY);
                        float pad = 12f * density;
                        float scale = Math.min((vw - pad * 2f) / bw, (vh - pad * 2f) / bh);
                        if (scale > 4f) scale = 4f;
                        float ox = (vw - bw * scale) * 0.5f - minX * scale;
                        float oy = (vh - bh * scale) * 0.5f - minY * scale;

                        if (showBoxes[0]) {
                            canvas.drawLine(0, oy, vw, oy, pAxis);
                            canvas.drawLine(ox, 0, ox, vh, pAxis);
                        }

                        android.graphics.RectF dstImg = new android.graphics.RectF(ox + imgL * scale, oy + imgT * scale, ox + imgR * scale, oy + imgB * scale);
                        canvas.drawBitmap(bmp, null, dstImg, pImg);

                        if (showBoxes[0]) {
                            // 蓝色防御框 Clsn2
                            for (int[] b : sp.clsn2) {
                                float l = ox + Math.min(b[0], b[2]) * scale, t = oy + Math.min(b[1], b[3]) * scale;
                                float r = ox + Math.max(b[0], b[2]) * scale, bt = oy + Math.max(b[1], b[3]) * scale;
                                canvas.drawRect(l, t, r, bt, pDefFill);
                                canvas.drawRect(l, t, r, bt, pDefStroke);
                            }
                            // 红色攻击框 Clsn1
                            for (int[] b : sp.clsn1) {
                                float l = ox + Math.min(b[0], b[2]) * scale, t = oy + Math.min(b[1], b[3]) * scale;
                                float r = ox + Math.max(b[0], b[2]) * scale, bt = oy + Math.max(b[1], b[3]) * scale;
                                canvas.drawRect(l, t, r, bt, pAtkFill);
                                canvas.drawRect(l, t, r, bt, pAtkStroke);
                            }
                            // 金色无敌框 (当状态含 NotHitBy 或该帧无防御框 Clsn2 时标识无敌受击区)
                            boolean isFrameInvul = st.hasInvul || sp.clsn2.isEmpty();
                            if (isFrameInvul) {
                                canvas.drawRect(dstImg.left, dstImg.top, dstImg.right, dstImg.bottom, pInvFill);
                                canvas.drawRect(dstImg.left, dstImg.top, dstImg.right, dstImg.bottom, pInvStroke);
                                canvas.drawText(st.hasInvul ? L("🛡️ 无敌态 (NotHitBy)") : L("🛡️ 无防御框 (无敌帧)"), 8f * density, 16f * density, pLbl);
                            }
                        }
                    }
                };
                GradientDrawable abg = new GradientDrawable(); abg.setColor(Color.parseColor("#0B0F17")); abg.setCornerRadius(8f*density); abg.setStroke(1, Color.parseColor("#334155"));
                animCanvas.setBackground(abg);
                dbox.addView(animCanvas, new LinearLayout.LayoutParams(-1, (int)(185*density)));

                final TextView tvFrameStat = new TextView(getContext());
                applyGlobalFontSettings(tvFrameStat, 0.78f, false); tvFrameStat.setTextColor(Color.parseColor("#94A3B8")); tvFrameStat.setGravity(Gravity.CENTER);
                dbox.addView(tvFrameStat);

                Runnable updateFrameStatText = () -> {
                    AirFrameSpec sp = curSpecRef[0];
                    String boxInfo = showBoxes[0]
                        ? (" | 🔴攻:" + sp.clsn1.size() + " 🔵防:" + sp.clsn2.size() + ((st.hasInvul || sp.clsn2.isEmpty()) ? " 🟡无敌" : ""))
                        : L(" | 纯图模式");
                    tvFrameStat.setText(finalSourceNote + " | " + (curAnimIdx[0]+1) + "/" + framesToPlay.size() + " | SFF[" + sp.group + "," + sp.item + "]" + boxInfo);
                };

                Runnable showOneFrame = () -> {
                    if (framesToPlay.isEmpty()) return;
                    if (curAnimIdx[0] < 0) curAnimIdx[0] = framesToPlay.size() - 1;
                    if (curAnimIdx[0] >= framesToPlay.size()) curAnimIdx[0] = 0;
                    final AirFrameSpec fSpec = framesToPlay.get(curAnimIdx[0]);
                    new Thread(() -> {
                        try {
                            byte[] raw = Api.decodeSffFrame(sffFileRef[0].getAbsolutePath(), fSpec.group, fSpec.item, "");
                            final Bitmap bmp = (raw != null && raw.length > 0) ? BitmapFactory.decodeByteArray(raw, 0, raw.length) : null;
                            uiHandler.post(() -> {
                                curSpecRef[0] = fSpec;
                                curBmpRef[0] = bmp;
                                animCanvas.invalidate();
                                updateFrameStatText.run();
                            });
                        } catch (Throwable ignored) {}
                    }).start();
                };
                showOneFrame.run();

                LinearLayout pBar = new LinearLayout(getContext()); pBar.setOrientation(LinearLayout.HORIZONTAL); pBar.setGravity(Gravity.CENTER);
                Button bPrev = createButton("⏪", "#334155");
                Button bPlay = createButton(L("▶️ 播放"), "#0284C7");
                Button bNext = createButton("⏭️", "#334155");
                final Button bToggleBox = createButton(L("🔲 框:开"), "#0F766E");
                LinearLayout.LayoutParams pbp = new LinearLayout.LayoutParams(0, -2, 1f); pbp.setMargins((int)(2*density), (int)(5*density), (int)(2*density), (int)(8*density));
                bPrev.setOnClickListener(v -> { animPlaying[0] = false; animGen[0]++; bPlay.setText(L("▶️ 播放")); curAnimIdx[0]--; showOneFrame.run(); });
                bNext.setOnClickListener(v -> { animPlaying[0] = false; animGen[0]++; bPlay.setText(L("▶️ 播放")); curAnimIdx[0]++; showOneFrame.run(); });
                bToggleBox.setOnClickListener(v -> {
                    showBoxes[0] = !showBoxes[0];
                    bToggleBox.setText(showBoxes[0] ? L("🔲 框:开") : L("🖼️ 纯图"));
                    bToggleBox.setBackgroundColor(Color.parseColor(showBoxes[0] ? "#0F766E" : "#475569"));
                    animCanvas.invalidate();
                    updateFrameStatText.run();
                });
                bPlay.setOnClickListener(v -> {
                    animPlaying[0] = !animPlaying[0];
                    bPlay.setText(animPlaying[0] ? L("⏸️ 暂停") : L("▶️ 播放"));
                    if (animPlaying[0]) {
                        final long g = ++animGen[0];
                        new Thread(() -> {
                            while (animPlaying[0] && g == animGen[0]) {
                                final AirFrameSpec sp = framesToPlay.get(curAnimIdx[0]);
                                long waitMs = Math.max(40, Math.min(400, (sp.ticks <= 0 ? 5 : sp.ticks) * 16L));
                                byte[] raw = null;
                                try { raw = Api.decodeSffFrame(sffFileRef[0].getAbsolutePath(), sp.group, sp.item, ""); } catch (Throwable ignored) {}
                                final Bitmap bmp = (raw != null && raw.length > 0) ? BitmapFactory.decodeByteArray(raw, 0, raw.length) : null;
                                uiHandler.post(() -> {
                                    if (animPlaying[0] && g == animGen[0]) {
                                        curSpecRef[0] = sp;
                                        curBmpRef[0] = bmp;
                                        animCanvas.invalidate();
                                        updateFrameStatText.run();
                                    }
                                });
                                try { Thread.sleep(waitMs); } catch (InterruptedException ignored) {}
                                curAnimIdx[0] = (curAnimIdx[0] + 1) % framesToPlay.size();
                            }
                        }).start();
                    } else {
                        animGen[0]++;
                    }
                });
                pBar.addView(bPrev, pbp); pBar.addView(bPlay, pbp); pBar.addView(bNext, pbp); pBar.addView(bToggleBox, pbp);
                dbox.addView(pBar);
            }

            if (!st.hits.isEmpty()) {
                dbox.addView(createSubTitle(L("💥 技能伤害调整 (修改后需点顶部保存生效):")));
                for (int hi = 0; hi < st.hits.size(); hi++) {
                    final CharHitItem h = st.hits.get(hi);
                    LinearLayout hCard = new LinearLayout(getContext()); hCard.setOrientation(LinearLayout.VERTICAL);
                    hCard.setPadding((int)(8*density), (int)(6*density), (int)(8*density), (int)(6*density));
                    GradientDrawable hbg = new GradientDrawable(); hbg.setColor(Color.parseColor("#1E2636")); hbg.setCornerRadius(6f*density); hbg.setStroke(1, Color.parseColor("#475569"));
                    hCard.setBackground(hbg);
                    LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2); hlp.setMargins(0, 0, 0, (int)(6*density));

                    TextView hInfo = new TextView(getContext());
                    hInfo.setText("#" + (hi+1) + " " + h.controllerType + (h.attr.isEmpty() ? "" : " | attr=" + h.attr) + (h.guardFlag.isEmpty() ? "" : " | guard=" + h.guardFlag));
                    applyGlobalFontSettings(hInfo, 0.82f, true); hInfo.setTextColor(Color.parseColor("#FDE047"));
                    hCard.addView(hInfo);

                    LinearLayout hRow = new LinearLayout(getContext()); hRow.setOrientation(LinearLayout.HORIZONTAL); hRow.setGravity(Gravity.CENTER_VERTICAL);
                    TextView l1 = new TextView(getContext()); l1.setText(L("命中伤:")); applyGlobalFontSettings(l1, 0.82f, false); l1.setTextColor(Color.WHITE);
                    final EditText eHit = createInput("", String.valueOf(h.hitDamage)); eHit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED); eHit.setSingleLine(true);
                    TextView l2 = new TextView(getContext()); l2.setText(L("  磨血:")); applyGlobalFontSettings(l2, 0.82f, false); l2.setTextColor(Color.WHITE);
                    final EditText eGrd = createInput("", String.valueOf(h.guardDamage)); eGrd.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED); eGrd.setSingleLine(true);

                    TextWatcher dmgWatcher = new TextWatcher() {
                        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                        @Override public void afterTextChanged(Editable s) {
                            try {
                                int nh = Integer.parseInt(eHit.getText().toString().trim());
                                int ng = Integer.parseInt(eGrd.getText().toString().trim());
                                if (nh != h.hitDamage || ng != h.guardDamage) {
                                    h.hitDamage = nh; h.guardDamage = ng; h.dirty = true;
                                    isDirty[0] = true; updateDirtyUI.run();
                                }
                            } catch (Exception ignored) {}
                        }
                    };
                    eHit.addTextChangedListener(dmgWatcher); eGrd.addTextChangedListener(dmgWatcher);
                    hRow.addView(l1); hRow.addView(eHit, new LinearLayout.LayoutParams(0, -2, 1f));
                    hRow.addView(l2); hRow.addView(eGrd, new LinearLayout.LayoutParams(0, -2, 1f));
                    hCard.addView(hRow);
                    dbox.addView(hCard, hlp);
                }
            }

            // 组装完整复刻代码（完整 CNS + 关联 AIR + CMD 触发参考）
            final StringBuilder fullBundle = new StringBuilder();
            fullBundle.append("; ================= CNS/ST 状态完整代码 =================\n");
            fullBundle.append(st.codePreview);
            if (finalResolvedAnim >= 0 && airActionRawText.containsKey(finalResolvedAnim)) {
                fullBundle.append("\n\n; ================= AIR 动作完整代码 (Action ").append(finalResolvedAnim).append(") =================\n");
                fullBundle.append(airActionRawText.get(finalResolvedAnim));
            }
            if (!st.linkedCmd.isEmpty()) {
                for (CharCmdItem ci : cmdItems) {
                    if (ci.name.equalsIgnoreCase(st.linkedCmd)) {
                        fullBundle.append("\n\n; ================= CMD 指令触发参考 =================\n")
                                  .append("[Command]\nname = \"").append(ci.name).append("\"\ncommand = ").append(ci.rawCommand).append("\ntime = 15\n")
                                  .append("\n[State -1, ").append(ci.name).append("]\ntype = ChangeState\nvalue = ").append(st.stateNo)
                                  .append("\ntriggerall = command = \"").append(ci.name).append("\"\ntrigger1 = ctrl");
                        break;
                    }
                }
            }
            final String completeSkillCode = fullBundle.toString().trim();

                        // 双模式复刻技能操作区
            dbox.addView(createSubTitle(L("🧬 复刻技能 (一键全自动移植到其他角色并可直接触发):")));
            LinearLayout cloneRow = new LinearLayout(getContext()); cloneRow.setOrientation(LinearLayout.HORIZONTAL);
            Button bCloneCode = createButton(L("📋 仅复制完整代码"), "#2563EB");
            Button bCloneFull = createButton(L("🚀 一键全自动移植到角色(.def)"), "#7C3AED");
            LinearLayout.LayoutParams crp = new LinearLayout.LayoutParams(0, -2, 1f); crp.setMargins((int)(2*density), (int)(3*density), (int)(2*density), (int)(6*density));

            bCloneCode.setOnClickListener(v -> {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("SkillCode", completeSkillCode));
                Toast.makeText(getContext(), L("✅ 已复制完整技能代码 (CNS + AIR + CMD)"), Toast.LENGTH_SHORT).show();
            });

            bCloneFull.setOnClickListener(v -> {
                showWin10FilePicker(L("选择目标角色的 .def (自动写入 SFF/SND/AIR/CNS/CMD)"), 15, null, null, targetDef -> {
                    if (targetDef == null || !targetDef.exists() || !targetDef.getName().toLowerCase().endsWith(".def")) {
                        Toast.makeText(getContext(), L("❌ 请选择有效的目标角色 .def 文件"), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // 解析目标 .def 中的关联文件
                    File tBaseDir = targetDef.getParentFile();
                    java.util.Map<String, String> tFiles = new java.util.LinkedHashMap<>();
                    String tDefTxt = readTextAutoCharset(targetDef);
                    String tSec = "";
                    for (String rl : tDefTxt.split("\n")) {
                        int ci = rl.indexOf(';');
                        String t = (ci >= 0 ? rl.substring(0, ci) : rl).trim();
                        if (t.isEmpty()) continue;
                        if (t.startsWith("[") && t.endsWith("]")) { tSec = t.substring(1, t.length() - 1).trim().toLowerCase(); continue; }
                        int eq = t.indexOf('=');
                        if (eq > 0 && "files".equals(tSec)) {
                            tFiles.put(t.substring(0, eq).trim().toLowerCase(), t.substring(eq + 1).trim().replace("\"", ""));
                        }
                    }
                    final File tSff = resolveCharSubFile(tBaseDir, tFiles.get("sprite"));
                    final File tSnd = resolveCharSubFile(tBaseDir, tFiles.get("sound"));
                    final File tAir = resolveCharSubFile(tBaseDir, tFiles.get("anim"));
                    final File tCmd = resolveCharSubFile(tBaseDir, tFiles.get("cmd"));
                    File tCnsTmp = resolveCharSubFile(tBaseDir, tFiles.get("st"));
                    if (tCnsTmp == null) tCnsTmp = resolveCharSubFile(tBaseDir, tFiles.get("cns"));
                    final File tCns = tCnsTmp;

                    if (tCns == null || tCmd == null) {
                        Toast.makeText(getContext(), L("❌ 目标角色缺少 CNS/ZSS 或 CMD 文件，无法自动注入"), Toast.LENGTH_LONG).show();
                        return;
                    }

                    // 判断源技能是否为 ZSS 语法（Ikemen GO 兼容优先：绝不将 CNS 与 ZSS 混写进同一文件！）
                    final boolean srcIsZss = (st.sourceFile != null && st.sourceFile.getName().toLowerCase().endsWith(".zss"))
                            || java.util.regex.Pattern.compile("(?i)\\[\\s*statedef\\s+-?\\d+\\s*;").matcher(st.codePreview).find();

                    // 扫描目标角色全部状态文件 (st / st0..stN / cns，含 .zss 与 .cns) 已占用的 StateNo
                    java.util.HashSet<Integer> usedStates = new java.util.HashSet<>();
                    java.util.LinkedHashSet<File> tAllStFiles = new java.util.LinkedHashSet<>();
                    tAllStFiles.add(tCns);
                    for (java.util.Map.Entry<String, String> tEn : tFiles.entrySet()) {
                        String tk = tEn.getKey();
                        if (tk.equals("st") || tk.matches("^st\\d+$") || tk.equals("cns")) {
                            File tsf = resolveCharSubFile(tBaseDir, tEn.getValue());
                            if (tsf != null && tsf.exists()) tAllStFiles.add(tsf);
                        }
                    }
                    java.util.regex.Pattern tSdPat = java.util.regex.Pattern.compile("(?i)\\[\\s*statedef\\s+(-?\\d+)");
                    for (File tsf : tAllStFiles) {
                        java.util.regex.Matcher smt = tSdPat.matcher(readTextAutoCharset(tsf));
                        while (smt.find()) { try { usedStates.add(Integer.parseInt(smt.group(1))); } catch (Exception ignored) {} }
                    }
                    String tAirRaw = (tAir != null && tAir.exists()) ? readTextAutoCharset(tAir) : "";
                    java.util.HashSet<Integer> usedAnims = new java.util.HashSet<>();
                    java.util.regex.Matcher amt = java.util.regex.Pattern.compile("(?i)\\[\\s*begin\\s+action\\s+(-?\\d+)").matcher(tAirRaw);
                    while (amt.find()) { try { usedAnims.add(Integer.parseInt(amt.group(1))); } catch (Exception ignored) {} }

                    int recState = st.stateNo;
                    if (recState < 200 || usedStates.contains(recState)) {
                        recState = 3500;
                        while (usedStates.contains(recState)) recState++;
                    }
                    int recAnim = (finalResolvedAnim >= 0) ? finalResolvedAnim : recState;
                    if (usedAnims.contains(recAnim)) {
                        recAnim = recState;
                        while (usedAnims.contains(recAnim)) recAnim++;
                    }

                    String defaultRawCmd = "~D, DF, F, x";
                    if (!st.linkedCmd.isEmpty()) {
                        for (CharCmdItem ci : cmdItems) {
                            if (ci.name.equalsIgnoreCase(st.linkedCmd) && !ci.rawCommand.isEmpty()) {
                                defaultRawCmd = ci.rawCommand; break;
                            }
                        }
                    }

                    // 弹窗让用户确认触发按键与目标编号
                    final Dialog cfgD = new Dialog(getContext());
                    if (cfgD.getWindow() != null) cfgD.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                    ScrollView csv = new ScrollView(getContext());
                    LinearLayout cbox = new LinearLayout(getContext()); cbox.setOrientation(LinearLayout.VERTICAL);
                    cbox.setPadding((int)(14*density), (int)(14*density), (int)(14*density), (int)(14*density));
                    GradientDrawable cbg = new GradientDrawable(); cbg.setColor(Color.parseColor("#F8141A24")); cbg.setCornerRadius(12f*density); cbg.setStroke(2, Color.parseColor("#7C3AED")); cbox.setBackground(cbg);
                    cbox.addView(createSubTitle(L("🚀 一键移植到: ") + targetDef.getName() + (srcIsZss ? " [ZSS]" : " [CNS]")));

                    final EditText eCmdName = createInput(L("CMD 指令名"), "CloneSkill_" + recState);
                    final EditText eCmdKeys = createInput(L("搓招按键 (冲突将自动升级为双击触发)"), defaultRawCmd);
                    final EditText eNewState = createInput(L("写入目标 State 编号"), String.valueOf(recState));
                    eNewState.setInputType(InputType.TYPE_CLASS_NUMBER);
                    final EditText eNewAnim = createInput(L("写入目标 AIR 动作号"), String.valueOf(recAnim));
                    eNewAnim.setInputType(InputType.TYPE_CLASS_NUMBER);

                    cbox.addView(createSubTitle(L("1. 游戏内触发按键 (若按键重复自动转双击触发):")));
                    cbox.addView(eCmdKeys);
                    HorizontalScrollView psc = new HorizontalScrollView(getContext()); psc.setHorizontalScrollBarEnabled(false);
                    LinearLayout prow = new LinearLayout(getContext()); prow.setOrientation(LinearLayout.HORIZONTAL);
                    String[][] presets = {
                        {"↓↘→+x", "~D, DF, F, x"}, {"↓↙←+x", "~D, DB, B, x"},
                        {"x+y", "x+y"}, {"a+b", "a+b"}, {"s(挑衅键)", "s"}, {"s,s(双击挑衅)", "s, s"}
                    };
                    LinearLayout.LayoutParams pbp = new LinearLayout.LayoutParams(-2, -2); pbp.setMargins(0, (int)(4*density), (int)(4*density), (int)(6*density));
                    for (final String[] pr : presets) {
                        Button pb = createButton(pr[0], "#263147");
                        pb.setPadding((int)(8*density), (int)(3*density), (int)(8*density), (int)(3*density));
                        pb.setOnClickListener(pv -> eCmdKeys.setText(pr[1]));
                        prow.addView(pb, pbp);
                    }
                    psc.addView(prow); cbox.addView(psc);

                    cbox.addView(createSubTitle(L("2. 指令名称 (自动写入目标 .cmd):"))); cbox.addView(eCmdName);
                    cbox.addView(createSubTitle(L("3. 目标 State 编号 (已全文件防撞号):"))); cbox.addView(eNewState);
                    cbox.addView(createSubTitle(L("4. 目标 AIR 动作号 (冲突已自动避让):"))); cbox.addView(eNewAnim);

                    Button bExecClone = createButton(L("✅ 开始全自动移植 (IK/ZSS 防闪退模式)"), "#16A34A");
                    LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, -2); ep.setMargins(0, (int)(10*density), 0, 0);
                    final int fDefState = recState, fDefAnim = recAnim;
                    bExecClone.setOnClickListener(ev -> {
                        cfgD.dismiss();
                        final String cmdName = eCmdName.getText().toString().trim().isEmpty() ? ("CloneSkill_" + fDefState) : eCmdName.getText().toString().trim();
                        final String rawInputSeq = eCmdKeys.getText().toString().trim().isEmpty() ? "~D, DF, F, x" : eCmdKeys.getText().toString().trim();
                        int tmpSt = fDefState, tmpAn = fDefAnim;
                        try { tmpSt = Integer.parseInt(eNewState.getText().toString().trim()); } catch (Exception ignored) {}
                        try { tmpAn = Integer.parseInt(eNewAnim.getText().toString().trim()); } catch (Exception ignored) {}
                        final int newStateNo = tmpSt, newAnimNo = tmpAn;

                        Toast.makeText(getContext(), L("⏳ 正在全自动移植精灵、音效与全部代码..."), Toast.LENGTH_SHORT).show();
                        new Thread(() -> {
                            try {
                                File tmpDir = new File(getContext().getCacheDir(), "skill_clone_tmp");
                                if (!tmpDir.exists()) tmpDir.mkdirs();

                                        // ① 全量收集关联动作（主动作 + Explod特效 + HitDef自制火花S + Projectile飞道 + ChangeAnim）并分配不冲突编号
                                String rewrittenCns = st.codePreview.replace("\r\n", "\n");
                                java.util.LinkedHashMap<Integer, Integer> animRemap = new java.util.LinkedHashMap<>();
                                java.util.HashSet<Integer> allocUsedAnims = new java.util.HashSet<>(usedAnims);
                                if (finalResolvedAnim >= 0) {
                                    animRemap.put(finalResolvedAnim, newAnimNo);
                                    allocUsedAnims.add(newAnimNo);
                                }
                                java.util.LinkedHashSet<Integer> extraAnims = new java.util.LinkedHashSet<>();
                                if (srcIsZss) {
                                    java.util.regex.Matcher em1 = java.util.regex.Pattern.compile("(?i)(?:explod|changeanim2?)\\s*\\{[^}]*?(?:anim|value)\\s*:\\s*(\\d+)").matcher(rewrittenCns);
                                    while (em1.find()) { try { extraAnims.add(Integer.parseInt(em1.group(1))); } catch (Exception ignored) {} }
                                    java.util.regex.Matcher em2 = java.util.regex.Pattern.compile("(?i)\\b(?:projanim|projhitanim|projremanim|projcancelanim)\\s*:\\s*(\\d+)").matcher(rewrittenCns);
                                    while (em2.find()) { try { extraAnims.add(Integer.parseInt(em2.group(1))); } catch (Exception ignored) {} }
                                    java.util.regex.Matcher em3 = java.util.regex.Pattern.compile("(?i)\\b(?:sparkno|guard\\.sparkno)\\s*:\\s*S\\s*(\\d+)").matcher(rewrittenCns);
                                    while (em3.find()) { try { extraAnims.add(Integer.parseInt(em3.group(1))); } catch (Exception ignored) {} }
                                } else {
                                    String[] scanL = rewrittenCns.split("\n", -1);
                                    String scType = "";
                                    java.util.regex.Pattern pAnimNum = java.util.regex.Pattern.compile("(?i)^\\s*(anim|value|projanim|projhitanim|projremanim|projcancelanim)\\s*=\\s*(\\d+)\\s*$");
                                    java.util.regex.Pattern pSparkS = java.util.regex.Pattern.compile("(?i)^\\s*(?:sparkno|guard\\.sparkno)\\s*=\\s*S\\s*(\\d+)");
                                    for (String sl : scanL) {
                                        int ci = sl.indexOf(';');
                                        String sc = (ci >= 0 ? sl.substring(0, ci) : sl).trim();
                                        String slow = sc.toLowerCase();
                                        if (slow.startsWith("[state ")) scType = "";
                                        else if (slow.matches("^type\\s*=\\s*[a-z0-9_]+$")) scType = slow.substring(slow.indexOf('=') + 1).trim();
                                        else {
                                            java.util.regex.Matcher ms = pSparkS.matcher(sc);
                                            if (ms.find()) { try { extraAnims.add(Integer.parseInt(ms.group(1))); } catch (Exception ignored) {} }
                                            java.util.regex.Matcher ma = pAnimNum.matcher(sc);
                                            if (ma.find()) {
                                                String kn = ma.group(1).toLowerCase();
                                                boolean okAnim = kn.startsWith("proj")
                                                        || (kn.equals("anim") && (scType.equals("explod") || scType.equals("modifyexplod")))
                                                        || (kn.equals("value") && (scType.equals("changeanim") || scType.equals("changeanim2")));
                                                if (okAnim) { try { extraAnims.add(Integer.parseInt(ma.group(2))); } catch (Exception ignored) {} }
                                            }
                                        }
                                    }
                                }
                                for (int ea : extraAnims) {
                                    if (ea >= 0 && !animRemap.containsKey(ea) && airActionRawText.containsKey(ea)) {
                                        int na = ea;
                                        if (allocUsedAnims.contains(na)) {
                                            na = Math.max(newAnimNo + 1, 7500);
                                            while (allocUsedAnims.contains(na)) na++;
                                        }
                                        allocUsedAnims.add(na);
                                        animRemap.put(ea, na);
                                    }
                                }

                                // ①-B SFF 全量精灵帧注入（主动作 + 全部 Explod/火花/飞道子动作）+ 冲突自动改号 + 源色表兼容
                                java.util.LinkedHashMap<String, int[]> sffRemap = new java.util.LinkedHashMap<>();
                                int sffOk = 0, sffFail = 0, sffRemapped = 0;
                                if (sffFileRef[0] != null && sffFileRef[0].exists() && tSff != null && tSff.exists()) {
                                    String srcActPath = "";
                                    if (defFileRef[0] != null && defFilesMap.containsKey("pal1")) {
                                        File pf = resolveCharSubFile(defFileRef[0].getParentFile(), defFilesMap.get("pal1"));
                                        if (pf != null && pf.exists()) srcActPath = pf.getAbsolutePath();
                                    }
                                    java.util.LinkedHashMap<String, int[]> uniqSff = new java.util.LinkedHashMap<>();
                                    for (AirFrameSpec af : framesToPlay) {
                                        if (af.group >= 0 && af.item >= 0) uniqSff.put(af.group + "," + af.item, new int[]{af.group, af.item});
                                    }
                                    for (int oa : animRemap.keySet()) {
                                        List<AirFrameSpec> subFs = airActionFrames.get(oa);
                                        if (subFs != null) {
                                            for (AirFrameSpec af : subFs) {
                                                if (af.group >= 0 && af.item >= 0) uniqSff.put(af.group + "," + af.item, new int[]{af.group, af.item});
                                            }
                                        }
                                    }
                                    if (!uniqSff.isEmpty()) {
                                        File bak = new File(tSff.getAbsolutePath() + ".bak");
                                        if (!bak.exists()) copyFileToSandbox(tSff, bak);
                                        List<GoEngineBridge.SffFrame> targetFrames = GoEngineBridge.getAllFrames(tSff.getAbsolutePath());
                                        java.util.HashSet<String> occSff = new java.util.HashSet<>();
                                        int maxG = 0;
                                        for (GoEngineBridge.SffFrame tf : targetFrames) {
                                            occSff.add(tf.group + "," + tf.item);
                                            if (tf.group > maxG && tf.group < 9000) maxG = tf.group;
                                        }
                                        for (java.util.Map.Entry<String, int[]> en : uniqSff.entrySet()) {
                                            int og = en.getValue()[0], oi = en.getValue()[1];
                                            int ng = og, ni = oi;
                                            if (occSff.contains(ng + "," + ni)) {
                                                boolean found = false;
                                                for (int tryI = 0; tryI <= 250; tryI++) {
                                                    if (!occSff.contains(ng + "," + tryI)) { ni = tryI; found = true; break; }
                                                }
                                                if (!found) {
                                                    ng = Math.max(maxG + 1, 7000); ni = 0;
                                                    while (occSff.contains(ng + "," + ni)) ni++;
                                                    maxG = ng;
                                                }
                                                sffRemapped++;
                                            }
                                            occSff.add(ng + "," + ni);
                                            sffRemap.put(og + "," + oi, new int[]{ng, ni});
                                            byte[] pngBytes = Api.decodeSffFrame(sffFileRef[0].getAbsolutePath(), og, oi, srcActPath);
                                            if ((pngBytes == null || pngBytes.length == 0) && !srcActPath.isEmpty()) {
                                                pngBytes = Api.decodeSffFrame(sffFileRef[0].getAbsolutePath(), og, oi, "");
                                            }
                                            if (pngBytes != null && pngBytes.length > 0) {
                                                File tmpPng = new File(tmpDir, "f_" + og + "_" + oi + ".png");
                                                try (FileOutputStream fos = new FileOutputStream(tmpPng)) { fos.write(pngBytes); }
                                                GoEngineBridge.SffFrame srcMeta = sffFrameLookup.get(og + "," + oi);
                                                short ax = (srcMeta != null) ? (short) srcMeta.x : 0;
                                                short ay = (srcMeta != null) ? (short) srcMeta.y : 0;
                                                if (Api.addSffFrame(tSff.getAbsolutePath(), ng, ni, ax, ay, tmpPng.getAbsolutePath())) sffOk++;
                                                else sffFail++;
                                                tmpPng.delete();
                                            } else {
                                                sffFail++;
                                            }
                                        }
                                    }
                                }

                                // ② SND 绑定音效提取注入 + 冲突自动改号（同时兼容 CNS 与 ZSS 语法，跳过 F 公共音效）
                                java.util.LinkedHashMap<String, int[]> sndRemap = new java.util.LinkedHashMap<>();
                                int sndOk = 0, sndRemapped = 0;
                                if (sndFileRef[0] != null && sndFileRef[0].exists() && tSnd != null && tSnd.exists()) {
                                    java.util.LinkedHashMap<String, int[]> foundSnds = new java.util.LinkedHashMap<>();
                                    if (srcIsZss) {
                                        java.util.regex.Matcher zm1 = java.util.regex.Pattern.compile("(?i)playsnd\\s*\\{[^}]*?value\\s*:\\s*(?:S\\s*)?(\\d+)\\s*,\\s*(\\d+)").matcher(rewrittenCns);
                                        while (zm1.find()) {
                                            int sg = Integer.parseInt(zm1.group(1)), si = Integer.parseInt(zm1.group(2));
                                            foundSnds.put(sg + "," + si, new int[]{sg, si});
                                        }
                                        java.util.regex.Matcher zm2 = java.util.regex.Pattern.compile("(?i)\\b(?:hitsound|guardsound|sound)\\s*:\\s*(?:S\\s*)?(\\d+)\\s*,\\s*(\\d+)").matcher(rewrittenCns);
                                        while (zm2.find()) {
                                            int sg = Integer.parseInt(zm2.group(1)), si = Integer.parseInt(zm2.group(2));
                                            foundSnds.put(sg + "," + si, new int[]{sg, si});
                                        }
                                    } else {
                                        String[] cLines = rewrittenCns.split("\n", -1);
                                        boolean inPlaySnd = false;
                                        java.util.regex.Pattern sndPairPat = java.util.regex.Pattern.compile("(?i)^\\s*(value|hitsound|guardsound|sound)\\s*=\\s*(?:S\\s*)?(\\d+)\\s*,\\s*(\\d+)");
                                        for (String cl : cLines) {
                                            int ci = cl.indexOf(';');
                                            String cClean = (ci >= 0 ? cl.substring(0, ci) : cl).trim();
                                            String cLow = cClean.toLowerCase();
                                            if (cLow.startsWith("[state ")) inPlaySnd = false;
                                            else if (cLow.matches("^type\\s*=\\s*playsnd$")) inPlaySnd = true;
                                            else {
                                                java.util.regex.Matcher m = sndPairPat.matcher(cClean);
                                                if (m.find()) {
                                                    String kName = m.group(1).toLowerCase();
                                                    if (!kName.equals("value") || inPlaySnd) {
                                                        int sg = Integer.parseInt(m.group(2));
                                                        int si = Integer.parseInt(m.group(3));
                                                        foundSnds.put(sg + "," + si, new int[]{sg, si});
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (!foundSnds.isEmpty()) {
                                        File bak = new File(tSnd.getAbsolutePath() + ".bak");
                                        if (!bak.exists()) copyFileToSandbox(tSnd, bak);
                                        List<GoEngineBridge.SndNode> tNodes = GoEngineBridge.scanSnd(tSnd.getAbsolutePath());
                                        java.util.HashSet<String> occSnd = new java.util.HashSet<>();
                                        int maxSndG = 0;
                                        for (GoEngineBridge.SndNode sn : tNodes) {
                                            occSnd.add(sn.group + "," + sn.item);
                                            if (sn.group > maxSndG && sn.group < 9000) maxSndG = sn.group;
                                        }
                                        for (java.util.Map.Entry<String, int[]> en : foundSnds.entrySet()) {
                                            int sg = en.getValue()[0], si = en.getValue()[1];
                                            int nsg = sg, nsi = si;
                                            if (occSnd.contains(nsg + "," + nsi)) {
                                                boolean found = false;
                                                for (int tryI = 0; tryI <= 250; tryI++) {
                                                    if (!occSnd.contains(nsg + "," + tryI)) { nsi = tryI; found = true; break; }
                                                }
                                                if (!found) {
                                                    nsg = Math.max(maxSndG + 1, 7000); nsi = 0;
                                                    while (occSnd.contains(nsg + "," + nsi)) nsi++;
                                                    maxSndG = nsg;
                                                }
                                                sndRemapped++;
                                            }
                                            occSnd.add(nsg + "," + nsi);
                                            sndRemap.put(sg + "," + si, new int[]{nsg, nsi});
                                            byte[] wavBytes = Api.extractSndAudio(sndFileRef[0].getAbsolutePath(), sg, si);
                                            if (wavBytes != null && wavBytes.length > 0) {
                                                File tmpWav = new File(tmpDir, "s_" + sg + "_" + si + ".wav");
                                                try (FileOutputStream fos = new FileOutputStream(tmpWav)) { fos.write(wavBytes); }
                                                if (Api.addSndAudio(tSnd.getAbsolutePath(), nsg, nsi, tmpWav.getAbsolutePath())) sndOk++;
                                                tmpWav.delete();
                                            }
                                        }
                                    }
                                }

                                // ③ 同步改写状态号 / 全部动作号 (含 Explod/火花/飞道) / 音效调用；严格隔离 CNS 与 ZSS 语法防闪退！
                                StringBuilder cnsOut = new StringBuilder();
                                if (srcIsZss) {
                                    String zBody = rewrittenCns.replaceFirst("(?i)(\\[\\s*statedef\\s+)-?\\d+", "$1" + newStateNo);
                                    for (java.util.Map.Entry<Integer, Integer> am : animRemap.entrySet()) {
                                        int oa = am.getKey(), na = am.getValue();
                                        if (oa != na) {
                                            zBody = zBody.replaceAll("(?i)(\\b(?:anim|projanim|projhitanim|projremanim|projcancelanim)\\s*:\\s*)" + oa + "\\b", "$1" + na);
                                            zBody = zBody.replaceAll("(?i)(changeanim2?\\s*\\{[^}]*?value\\s*:\\s*)" + oa + "\\b", "$1" + na);
                                            zBody = zBody.replaceAll("(?i)(\\b(?:sparkno|guard\\.sparkno)\\s*:\\s*S\\s*)" + oa + "\\b", "$1" + na);
                                        }
                                    }
                                    if (!sndRemap.isEmpty()) {
                                        for (java.util.Map.Entry<String, int[]> rm : sndRemap.entrySet()) {
                                            String[] op = rm.getKey().split(",");
                                            int ng = rm.getValue()[0], ni = rm.getValue()[1];
                                            if (!op[0].equals(String.valueOf(ng)) || !op[1].equals(String.valueOf(ni))) {
                                                zBody = zBody.replaceAll("(?i)(playsnd\\s*\\{[^}]*?value\\s*:\\s*(?:S\\s*)?)" + op[0] + "(\\s*,\\s*)" + op[1] + "\\b", "$1" + ng + "$2" + ni);
                                                zBody = zBody.replaceAll("(?i)(\\b(?:hitsound|guardsound|sound)\\s*:\\s*(?:S\\s*)?)" + op[0] + "(\\s*,\\s*)" + op[1] + "\\b", "$1" + ng + "$2" + ni);
                                            }
                                        }
                                    }
                                    cnsOut.append(zBody);
                                } else {
                                    String[] cnsLines = rewrittenCns.split("\n", -1);
                                    boolean inPs = false, inCa = false, inExp = false, inHead = false;
                                    java.util.regex.Pattern sndRepPat = java.util.regex.Pattern.compile("(?i)^(\\s*(?:value|hitsound|guardsound|sound)\\s*=\\s*(?:S\\s*)?)(\\d+)(\\s*,\\s*)(\\d+)(.*)$");
                                    java.util.regex.Pattern sparkRepPat = java.util.regex.Pattern.compile("(?i)^(\\s*(?:sparkno|guard\\.sparkno)\\s*=\\s*S\\s*)(\\d+)(.*)$");
                                    java.util.regex.Pattern projAnimRepPat = java.util.regex.Pattern.compile("(?i)^(\\s*(?:projanim|projhitanim|projremanim|projcancelanim)\\s*=\\s*)(\\d+)(.*)$");
                                    for (String line : cnsLines) {
                                        int ci = line.indexOf(';');
                                        String codePart = (ci >= 0 ? line.substring(0, ci) : line);
                                        String cmtPart = (ci >= 0 ? line.substring(ci) : "");
                                        String trimmed = codePart.trim();
                                        String low = trimmed.toLowerCase();
                                        if (low.matches("^\\[\\s*statedef\\s+-?\\d+.*")) {
                                            codePart = "[Statedef " + newStateNo + "]";
                                            inHead = true; inPs = false; inCa = false; inExp = false;
                                        } else if (low.startsWith("[state ")) {
                                            inHead = false; inPs = false; inCa = false; inExp = false;
                                        } else if (low.matches("^type\\s*=\\s*playsnd$")) {
                                            inPs = true;
                                        } else if (low.matches("^type\\s*=\\s*changeanim2?$")) {
                                            inCa = true;
                                        } else if (low.matches("^type\\s*=\\s*(?:explod|modifyexplod)$")) {
                                            inExp = true;
                                        } else if (inHead && low.matches("^anim\\s*=\\s*-?\\d+$")) {
                                            codePart = "anim = " + newAnimNo;
                                        } else if (inExp && low.matches("^anim\\s*=\\s*\\d+$")) {
                                            try {
                                                int oldA = Integer.parseInt(trimmed.substring(trimmed.indexOf('=') + 1).trim());
                                                if (animRemap.containsKey(oldA)) codePart = "anim = " + animRemap.get(oldA);
                                            } catch (Exception ignored) {}
                                        } else if (inCa && low.matches("^value\\s*=\\s*-?\\d+$")) {
                                            try {
                                                int vOld = Integer.parseInt(trimmed.substring(trimmed.indexOf('=') + 1).trim());
                                                if (animRemap.containsKey(vOld)) codePart = "value = " + animRemap.get(vOld);
                                            } catch (Exception ignored) {}
                                        } else {
                                            java.util.regex.Matcher smSpark = sparkRepPat.matcher(codePart);
                                            if (smSpark.find()) {
                                                try {
                                                    int oldSp = Integer.parseInt(smSpark.group(2));
                                                    if (animRemap.containsKey(oldSp)) codePart = smSpark.group(1) + animRemap.get(oldSp) + smSpark.group(3);
                                                } catch (Exception ignored) {}
                                            }
                                            java.util.regex.Matcher pmAnim = projAnimRepPat.matcher(codePart);
                                            if (pmAnim.find()) {
                                                try {
                                                    int oldPa = Integer.parseInt(pmAnim.group(2));
                                                    if (animRemap.containsKey(oldPa)) codePart = pmAnim.group(1) + animRemap.get(oldPa) + pmAnim.group(3);
                                                } catch (Exception ignored) {}
                                            }
                                            if (!sndRemap.isEmpty()) {
                                                java.util.regex.Matcher m = sndRepPat.matcher(codePart);
                                                if (m.find()) {
                                                    boolean isVal = m.group(1).trim().toLowerCase().startsWith("value");
                                                    if (!isVal || inPs) {
                                                        int[] mapped = sndRemap.get(m.group(2) + "," + m.group(4));
                                                        if (mapped != null) {
                                                            codePart = m.group(1) + mapped[0] + m.group(3) + mapped[1] + m.group(5);
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        cnsOut.append(codePart).append(cmtPart).append("\n");
                                    }
                                }

                                // 飞行/Helper/子状态防跳空与防卡死保护：检查 ChangeState/HitOverride/p1stateno 是否指向目标角色不存在的状态
                                String cnsBodyStr = cnsOut.toString().trim();
                                int fixedMissingTargets = 0;
                                if (!srcIsZss) {
                                    StringBuilder safeCns = new StringBuilder();
                                    String[] sLines = cnsBodyStr.split("\n", -1);
                                    String curScType = "";
                                    java.util.regex.Pattern pValNum = java.util.regex.Pattern.compile("(?i)^(\\s*(?:value|stateno)\\s*=\\s*)(\\d+)(.*)$");
                                    java.util.regex.Pattern pP1St = java.util.regex.Pattern.compile("(?i)^(\\s*p1stateno\\s*=\\s*)(\\d+)(.*)$");
                                    for (String sl : sLines) {
                                        int ci = sl.indexOf(';');
                                        String cp = (ci >= 0 ? sl.substring(0, ci) : sl);
                                        String cm = (ci >= 0 ? sl.substring(ci) : "");
                                        String tr = cp.trim();
                                        String lw = tr.toLowerCase();
                                        if (lw.startsWith("[state ") || lw.startsWith("[statedef ")) curScType = "";
                                        else if (lw.matches("^type\\s*=\\s*[a-z0-9_]+$")) curScType = lw.substring(lw.indexOf('=') + 1).trim();
                                        else {
                                            java.util.regex.Matcher mp1 = pP1St.matcher(cp);
                                            if (mp1.find()) {
                                                try {
                                                    int tgt = Integer.parseInt(mp1.group(2));
                                                    boolean okTgt = (tgt == newStateNo || usedStates.contains(tgt) || (tgt >= 0 && tgt <= 199) || (tgt >= 5000 && tgt <= 5999));
                                                    if (!okTgt) {
                                                        cp = "; [IK防卡死: 原 p1stateno=" + tgt + " 目标角色不存在已屏蔽] " + tr;
                                                        fixedMissingTargets++;
                                                    }
                                                } catch (Exception ignored) {}
                                            } else if (curScType.equals("changestate") || curScType.equals("hitoverride")) {
                                                java.util.regex.Matcher mvn = pValNum.matcher(cp);
                                                if (mvn.find()) {
                                                    try {
                                                        int tgt = Integer.parseInt(mvn.group(2));
                                                        boolean okTgt = (tgt == newStateNo || usedStates.contains(tgt) || (tgt >= 0 && tgt <= 199) || (tgt >= 5000 && tgt <= 5999));
                                                        if (!okTgt) {
                                                            cp = mvn.group(1) + "0 ; [IK防卡死: 原目标状态 " + tgt + " 不存在，已安全回站立]";
                                                            if (curScType.equals("changestate")) cp += "\nctrl = 1";
                                                            fixedMissingTargets++;
                                                        }
                                                    } catch (Exception ignored) {}
                                                }
                                            }
                                        }
                                        safeCns.append(cp).append(cm).append("\n");
                                    }
                                    cnsBodyStr = safeCns.toString().trim();
                                }

                                // 若源技能是 Helper/飞行道具状态（有 DestroySelf 或无回 0/50 站立/落地 ChangeState），自动补收招兜底防卡死
                                boolean hasDestroySelf = cnsBodyStr.toLowerCase().contains("destroyself");
                                boolean isAirState = "A".equalsIgnoreCase(st.type)
                                        || java.util.regex.Pattern.compile("(?im)^\\s*type\\s*=\\s*A\\b").matcher(cnsBodyStr).find();
                                boolean hasSafeReturn = java.util.regex.Pattern.compile("(?im)^\\s*value\\s*[:=]\\s*(?:0|50)\\b").matcher(cnsBodyStr).find();
                                if (hasDestroySelf || !hasSafeReturn) {
                                    if (srcIsZss) {
                                        cnsBodyStr += "\n# [IK防卡死兜底] 玩家直接触发时自动收招/落地\nif !isHelper && (animTime = 0 || time >= 90 || frontEdgeDist <= -50) {\n\tchangeState{value: ifelse(pos y < 0, 50, 0); ctrl: 1}\n}";
                                    } else {
                                        cnsBodyStr += "\n[State " + newStateNo + ", AutoReturnIdle]\ntype = ChangeState\ntrigger1 = !IsHelper && (AnimTime = 0 || Time >= 90 || FrontEdgeDist <= -50)\nvalue = ifelse(Pos Y < 0, 50, 0)\nctrl = 1";
                                    }
                                }

                                // 决定安全写入目标文件：若目标主状态为 ZSS 而源为 CNS（或反之），自动创建独立侧挂文件并注册到目标 .def 的 [Files] stN！
                                boolean targetMainIsZss = tCns.getName().toLowerCase().endsWith(".zss");
                                File writeStateFile = tCns;
                                String defMountNote = "";
                                if (srcIsZss != targetMainIsZss) {
                                    String sidecarName = srcIsZss ? "ik_cloned_skills.zss" : "ik_cloned_skills.cns";
                                    writeStateFile = new File(tBaseDir, sidecarName);
                                    boolean alreadyMounted = false;
                                    int maxStIdx = 0;
                                    for (java.util.Map.Entry<String, String> tEn : tFiles.entrySet()) {
                                        String tk = tEn.getKey();
                                        if (tEn.getValue().equalsIgnoreCase(sidecarName)) alreadyMounted = true;
                                        if (tk.matches("^st\\d+$")) {
                                            try { int idx = Integer.parseInt(tk.substring(2)); if (idx > maxStIdx) maxStIdx = idx; } catch (Exception ignored) {}
                                        }
                                    }
                                    if (!alreadyMounted) {
                                        int nextStIdx = Math.max(1, maxStIdx + 1);
                                        while (tFiles.containsKey("st" + nextStIdx)) nextStIdx++;
                                        String stKey = "st" + nextStIdx;
                                        File defBak = new File(targetDef.getAbsolutePath() + ".bak");
                                        if (!defBak.exists()) copyFileToSandbox(targetDef, defBak);
                                        String curDefTxt = readTextAutoCharset(targetDef);
                                        java.util.regex.Matcher fm = java.util.regex.Pattern.compile("(?im)^\\s*(\\[\\s*files\\s*\\][^\\r\\n]*\\r?\\n?)").matcher(curDefTxt);
                                        String newDefTxt;
                                        if (fm.find()) {
                                            newDefTxt = curDefTxt.substring(0, fm.end()) + stKey + " = " + sidecarName + " ; [IK自动挂载移植技能]\n" + curDefTxt.substring(fm.end());
                                        } else {
                                            newDefTxt = curDefTxt + "\n[Files]\n" + stKey + " = " + sidecarName + "\n";
                                        }
                                        try (FileOutputStream fos = new FileOutputStream(targetDef)) { fos.write(newDefTxt.getBytes("UTF-8")); }
                                        defMountNote = " (已防冲突独立隔离并在 " + targetDef.getName() + " 自动挂载 " + stKey + " = " + sidecarName + ")";
                                    } else {
                                        defMountNote = " (已写入独立隔离文件，避免污染目标 ZSS/CNS)";
                                    }
                                } else {
                                    File cnsBak = new File(writeStateFile.getAbsolutePath() + ".bak");
                                    if (!cnsBak.exists()) copyFileToSandbox(writeStateFile, cnsBak);
                                }
                                String cmtPrefix = srcIsZss ? "# " : "; ";
                                String finalCnsBlock = "\n\n" + cmtPrefix + "=== [自动移植技能] " + cmdName + " (State " + newStateNo + ") ===\n" + cnsBodyStr + "\n";
                                try (FileOutputStream fos = new FileOutputStream(writeStateFile, true)) { fos.write(finalCnsBlock.getBytes("UTF-8")); }

                                // ④ 同步改写全部 AIR 动作块（主动作 + 全部 Explod/火花/飞道子动作）并追加写入目标 .air
                                int airWrittenCnt = 0;
                                if (!animRemap.isEmpty() && tAir != null && tAir.exists()) {
                                    StringBuilder allAirAppend = new StringBuilder();
                                    for (java.util.Map.Entry<Integer, Integer> am : animRemap.entrySet()) {
                                        int oa = am.getKey(), na = am.getValue();
                                        String airRaw = airActionRawText.containsKey(oa) ? airActionRawText.get(oa).replace("\r\n", "\n") : "";
                                        if (airRaw.isEmpty()) continue;
                                        StringBuilder rewrittenAir = new StringBuilder();
                                        for (String line : airRaw.split("\n", -1)) {
                                            String trimmed = line.trim();
                                            if (trimmed.toLowerCase().startsWith("[begin action")) {
                                                line = "[Begin Action " + na + "]";
                                            } else if (trimmed.matches("^-?\\d+\\s*,\\s*-?\\d+\\s*,.*")) {
                                                String[] ps = trimmed.split(",", 3);
                                                if (ps.length >= 3) {
                                                    int[] mapped = sffRemap.get(ps[0].trim() + "," + ps[1].trim());
                                                    if (mapped != null) line = mapped[0] + ", " + mapped[1] + "," + ps[2];
                                                }
                                            }
                                            rewrittenAir.append(line).append("\n");
                                        }
                                        allAirAppend.append("\n\n; === [自动移植动作] ").append(cmdName).append(" (Action ").append(na).append(") ===\n")
                                                    .append(rewrittenAir.toString().trim()).append("\n");
                                        airWrittenCnt++;
                                    }
                                    if (airWrittenCnt > 0) {
                                        File airBak = new File(tAir.getAbsolutePath() + ".bak");
                                        if (!airBak.exists()) copyFileToSandbox(tAir, airBak);
                                        try (FileOutputStream fos = new FileOutputStream(tAir, true)) { fos.write(allAirAppend.toString().getBytes("UTF-8")); }
                                    }
                                }

                                // ⑤ 目标 .cmd 自动体检自愈（清除空 [Command] 与缺指令孤儿触发器）+ 按键重复自动升级双击 + 同步注入
                                File cmdBak = new File(tCmd.getAbsolutePath() + ".bak");
                                if (!cmdBak.exists()) copyFileToSandbox(tCmd, cmdBak);
                                String cmdOrig = readTextAutoCharset(tCmd).replace("\r\n", "\n");

                                // 5.1 仅从真正的 [Command] 区块收集已有指令名与按键序列
                                java.util.HashSet<String> existingCmdNames = new java.util.HashSet<>();
                                java.util.HashSet<String> existingCmdSeqs = new java.util.HashSet<>();
                                boolean scanInCmdSec = false;
                                for (String cl : cmdOrig.split("\n")) {
                                    int ci = cl.indexOf(';');
                                    String clean = (ci >= 0 ? cl.substring(0, ci) : cl).trim();
                                    if (clean.isEmpty()) continue;
                                    String low = clean.toLowerCase();
                                    if (low.startsWith("[")) {
                                        scanInCmdSec = low.equals("[command]");
                                    } else if (scanInCmdSec) {
                                        int eq = clean.indexOf('=');
                                        if (eq > 0) {
                                            String ck = clean.substring(0, eq).trim().toLowerCase();
                                            String cv = clean.substring(eq + 1).trim();
                                            if (ck.equals("name")) existingCmdNames.add(cv.replace("\"", "").trim().toLowerCase());
                                            else if (ck.equals("command")) existingCmdSeqs.add(cv.replaceAll("\\s+", "").toLowerCase());
                                        }
                                    }
                                }

                                // 5.2 自愈清理：移除无对应 [Command] 的孤儿 [State -1]、空 [Command] 块，并自动修复已有非法大小写 command（如 A,B -> a, b 根治 Invalid symbol 闪退）
                                int healedOrphans = 0;
                                int healedBadCmds = 0;
                                StringBuilder healedCmd = new StringBuilder();
                                String[] origLines = cmdOrig.split("\n", -1);
                                boolean inHealCmdBlock = false;
                                for (int li = 0; li < origLines.length; li++) {
                                    String curL = origLines[li];
                                    String curTrim = curL.trim().toLowerCase();
                                    if (curTrim.equals("[command]")) {
                                        int endBlock = li + 1;
                                        boolean hasName = false;
                                        while (endBlock < origLines.length) {
                                            String nt = origLines[endBlock].trim();
                                            if (nt.startsWith("[")) break;
                                            String nc = (nt.indexOf(';') >= 0 ? nt.substring(0, nt.indexOf(';')) : nt).trim().toLowerCase();
                                            if (nc.startsWith("name") && nc.contains("=")) hasName = true;
                                            endBlock++;
                                        }
                                        if (!hasName) { li = endBlock - 1; inHealCmdBlock = false; continue; }
                                        inHealCmdBlock = true;
                                    } else if (curTrim.startsWith("[state -1")) {
                                        inHealCmdBlock = false;
                                        int endBlock = li + 1;
                                        String refCmd = "";
                                        while (endBlock < origLines.length) {
                                            String nt = origLines[endBlock].trim();
                                            if (nt.startsWith("[")) break;
                                            java.util.regex.Matcher rcm = java.util.regex.Pattern.compile("(?i)command\\s*=\\s*\"([^\"]+)\"").matcher(nt);
                                            if (rcm.find()) refCmd = rcm.group(1).trim();
                                            endBlock++;
                                        }
                                        if (!refCmd.isEmpty() && !existingCmdNames.contains(refCmd.toLowerCase())) {
                                            healedOrphans++;
                                            li = endBlock - 1;
                                            continue;
                                        }
                                    } else if (curTrim.startsWith("[")) {
                                        inHealCmdBlock = false;
                                    } else if (inHealCmdBlock) {
                                        int ci = curL.indexOf(';');
                                        String codeP = (ci >= 0 ? curL.substring(0, ci) : curL);
                                        String cmtP = (ci >= 0 ? curL.substring(ci) : "");
                                        java.util.regex.Matcher cmLine = java.util.regex.Pattern.compile("(?i)^(\\s*command\\s*=\\s*)(.+)$").matcher(codeP);
                                        if (cmLine.find()) {
                                            String oldVal = cmLine.group(2).trim();
                                            // 仅当存在明确非法符号（如大写 A/C/X/Y/Z/S/W/M 或全角字符）时才自动修复，避免误伤原角色正常指令
                                            if (java.util.regex.Pattern.compile("(?<![A-Za-z])[ACXYZSWM](?![A-Za-z])|[，＋～／Ａ-Ｚａ-ｚ]").matcher(oldVal).find()) {
                                                String fixedVal = sanitizeIkCommandSeq(oldVal);
                                                curL = cmLine.group(1) + fixedVal + (cmtP.isEmpty() ? " ; [IK自愈修复非法按键符号]" : cmtP);
                                                healedBadCmds++;
                                            }
                                        }
                                    }
                                    healedCmd.append(curL).append("\n");
                                }
                                cmdOrig = healedCmd.toString();

                                // 5.3 按 Ikemen GO 规范强制标准化按键大小写与符号 + 确保 cmdName 唯一 + 重复按键自动升级双击
                                String finalCmdName = cmdName;
                                int nameSuffix = 2;
                                while (existingCmdNames.contains(finalCmdName.toLowerCase())) {
                                    finalCmdName = cmdName + "_" + (nameSuffix++);
                                }
                                String cleanInputSeq = sanitizeIkCommandSeq(rawInputSeq);
                                String finalCmdSeq = cleanInputSeq;
                                int cmdTime = 18;
                                String cmdConflictNote = "";
                                if (!cleanInputSeq.replaceAll("\\s+", "").equals(rawInputSeq.replaceAll("\\s+", ""))) {
                                    cmdConflictNote = "🔧 已按 Ikemen GO 规范自动修正按键大小写/符号: [" + rawInputSeq + "] → [" + cleanInputSeq + "]";
                                }
                                String normSeq = finalCmdSeq.replaceAll("\\s+", "").toLowerCase();
                                if (existingCmdSeqs.contains(normSeq)) {
                                    String dblSeq = finalCmdSeq + ", " + finalCmdSeq;
                                    if (!existingCmdSeqs.contains(dblSeq.replaceAll("\\s+", "").toLowerCase())) {
                                        finalCmdSeq = dblSeq;
                                        cmdTime = 25;
                                        cmdConflictNote += (cmdConflictNote.isEmpty() ? "" : "\n") + "⚠️ 检测到目标 .cmd 已占用按键 [" + cleanInputSeq + "]，已自动升级为双击触发: " + finalCmdSeq + " (time=25)";
                                    } else {
                                        String triSeq = finalCmdSeq + ", " + finalCmdSeq + ", " + finalCmdSeq;
                                        if (!existingCmdSeqs.contains(triSeq.replaceAll("\\s+", "").toLowerCase())) {
                                            finalCmdSeq = triSeq;
                                            cmdTime = 32;
                                            cmdConflictNote += (cmdConflictNote.isEmpty() ? "" : "\n") + "⚠️ 按键 [" + cleanInputSeq + "] 与双击均被占用，已自动升级为三击触发: " + finalCmdSeq;
                                        } else {
                                            finalCmdSeq = "~D, D, " + finalCmdSeq;
                                            cmdTime = 25;
                                            cmdConflictNote += (cmdConflictNote.isEmpty() ? "" : "\n") + "⚠️ 按键 [" + cleanInputSeq + "] 已被占用，已自动改配组合触发: " + finalCmdSeq;
                                        }
                                    }
                                }

                                String cmdDefBlock = buildIkCommandBlocks(finalCmdName, finalCmdSeq, cmdTime);
                                boolean cmdIsZss = tCmd.getName().toLowerCase().endsWith(".zss");
                                // 若为飞行/空中/Helper技能，放宽空中触发限制(ctrl即可)，否则地面触发(statetype != A && ctrl)
                                String condCns = (isAirState || hasDestroySelf) ? "ctrl" : "statetype != A && ctrl";
                                String condZss = (isAirState || hasDestroySelf) ? "ctrl" : "stateType != A && ctrl";
                                String stateNeg1Block = cmdIsZss
                                        ? ("\n# [自动移植触发器] " + finalCmdName + "\nif command = \"" + finalCmdName + "\" && " + condZss + " {\n\tchangeState{value: " + newStateNo + "}\n}\n")
                                        : ("\n; [自动移植触发器] " + finalCmdName + "\n[State -1, " + finalCmdName + "]\ntype = ChangeState\nvalue = " + newStateNo + "\ntriggerall = command = \"" + finalCmdName + "\"\ntrigger1 = " + condCns + "\n");


                                // 严格行首锚定，绝不误匹配注释中的 ; [Statedef -1]
                                java.util.regex.Matcher neg1M = java.util.regex.Pattern.compile("(?im)^\\s*(\\[\\s*statedef\\s+-1[^\\]]*\\][^\\r\\n]*\\r?\\n?)").matcher(cmdOrig);
                                String newCmdText;
                                if (neg1M.find()) {
                                    int idxStart = neg1M.start();
                                    int idxEnd = neg1M.end();
                                    newCmdText = cmdOrig.substring(0, idxStart) + cmdDefBlock + "\n" + cmdOrig.substring(idxStart, idxEnd) + stateNeg1Block + cmdOrig.substring(idxEnd);
                                } else {
                                    newCmdText = cmdOrig + "\n" + cmdDefBlock + "\n[Statedef -1]\n" + stateNeg1Block;
                                }
                                try (FileOutputStream fos = new FileOutputStream(tCmd)) { fos.write(newCmdText.getBytes("UTF-8")); }

                                StringBuilder report = new StringBuilder();
                                report.append("🎉 全自动移植已完成，进游戏可直接搓招触发！\n");
                                report.append("🎮 游戏内触发方式: ").append(prettifyCmdSequence(finalCmdSeq)).append("  (").append(finalCmdSeq).append(")\n");
                                if (!cmdConflictNote.isEmpty()) report.append(cmdConflictNote).append("\n");
                                if (healedOrphans > 0) report.append("🛠️ 已自动清理目标 .cmd 中 ").append(healedOrphans).append(" 处缺失指令的孤儿触发器（防止闪退）\n");
                                if (healedBadCmds > 0) report.append("🛠️ 已自动修复目标 .cmd 中 ").append(healedBadCmds).append(" 处非法大小写/符号指令（防止 Invalid symbol 闪退）\n");
                                if (fixedMissingTargets > 0) report.append("🛡️ 已自动兜底修复 ").append(fixedMissingTargets).append(" 处不存在的跳转子状态（防止触发后卡死）\n");
                                report.append("\n• 目标角色: ").append(targetDef.getName()).append(" (已自动创建 .bak 备份)\n");
                                report.append("• 状态写入: ").append(writeStateFile.getName()).append(" → [Statedef ").append(newStateNo).append("]").append(defMountNote).append("\n");
                                report.append("• CMD 写入: ").append(tCmd.getName()).append(" → [Command] + [State -1, ").append(finalCmdName).append("]\n");
                                report.append("• AIR 写入: ").append(airWrittenCnt > 0 ? (tAir.getName() + " → 共写入 " + airWrittenCnt + " 个动作块 (含主动作与关联特效/火花)") : "无独立动作").append("\n");
                                report.append("• SFF 注入: 成功 ").append(sffOk).append(" 帧").append(sffFail > 0 ? (" / 失败 " + sffFail + " 帧(请确认目标SFF为v2格式)") : "").append(" (冲突自动改号 ").append(sffRemapped).append(" 帧)\n");
                                report.append("• SND 注入: ").append(sndOk).append(" 条绑定音效 (冲突自动改号 ").append(sndRemapped).append(" 条)\n");


                                if (sffRemapped > 0) {
                                    report.append("\n--- SFF 精灵改号对照 ---\n");
                                    for (java.util.Map.Entry<String, int[]> rm : sffRemap.entrySet()) {
                                        String nk = rm.getValue()[0] + "," + rm.getValue()[1];
                                        if (!rm.getKey().equals(nk)) report.append("  [").append(rm.getKey()).append("] -> [").append(nk).append("]\n");
                                    }
                                }
                                if (sndRemapped > 0) {
                                    report.append("\n--- SND 音效改号对照 ---\n");
                                    for (java.util.Map.Entry<String, int[]> rm : sndRemap.entrySet()) {
                                        String nk = rm.getValue()[0] + "," + rm.getValue()[1];
                                        if (!rm.getKey().equals(nk)) report.append("  [").append(rm.getKey()).append("] -> [").append(nk).append("]\n");
                                    }
                                }

                                final String finalRep = report.toString();
                                uiHandler.post(() -> {
                                    final Dialog rd = new Dialog(getContext());
                                    if (rd.getWindow() != null) rd.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                                    ScrollView rsv = new ScrollView(getContext());
                                    LinearLayout rbox = new LinearLayout(getContext()); rbox.setOrientation(LinearLayout.VERTICAL);
                                    rbox.setPadding((int)(14*density), (int)(14*density), (int)(14*density), (int)(14*density));
                                    GradientDrawable rbg = new GradientDrawable(); rbg.setColor(Color.parseColor("#F8141A24")); rbg.setCornerRadius(12f*density); rbg.setStroke(2, Color.parseColor("#22C55E")); rbox.setBackground(rbg);
                                    rbox.addView(createSubTitle(L("✅ 技能全自动移植成功")));
                                    TextView rtv = new TextView(getContext());
                                    rtv.setText(finalRep); rtv.setTypeface(Typeface.MONOSPACE); rtv.setTextSize(10.8f); rtv.setTextColor(Color.parseColor("#86EFAC")); rtv.setTextIsSelectable(true);
                                    rbox.addView(rtv);
                                    Button bDone = createButton(L("太棒了，关闭"), "#16A34A");
                                    LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2); bp.setMargins(0, (int)(10*density), 0, 0);
                                    bDone.setOnClickListener(cv2 -> rd.dismiss());
                                    rbox.addView(bDone, bp);
                                    rsv.addView(rbox);
                                    int sw2 = getContext().getResources().getDisplayMetrics().widthPixels;
                                    rd.setContentView(rsv, new ViewGroup.LayoutParams((int)(sw2 * 0.88f), -2));
                                    rd.show();
                                });
                            } catch (Exception ex) {
                                uiHandler.post(() -> Toast.makeText(getContext(), L("❌ 自动移植失败: ") + ex.getMessage(), Toast.LENGTH_LONG).show());
                            }
                        }).start();
                    });
                    cbox.addView(bExecClone, ep);
                    csv.addView(cbox);
                    int swCfg = getContext().getResources().getDisplayMetrics().widthPixels;
                    cfgD.setContentView(csv, new ViewGroup.LayoutParams((int)(swCfg * 0.88f), -2));
                    cfgD.show();
                });
            });

            cloneRow.addView(bCloneCode, crp); cloneRow.addView(bCloneFull, crp);
            dbox.addView(cloneRow);

            if (!completeSkillCode.isEmpty()) {
                dbox.addView(createSubTitle(L("📝 完整技能源码 (CNS + AIR + CMD):")));
                TextView codeTv = new TextView(getContext());
                codeTv.setText(completeSkillCode);
                codeTv.setTypeface(Typeface.MONOSPACE);
                codeTv.setTextSize(10.5f);
                codeTv.setTextColor(Color.parseColor("#86EFAC"));
                codeTv.setTextIsSelectable(true);
                codeTv.setPadding((int)(8*density), (int)(8*density), (int)(8*density), (int)(8*density));
                GradientDrawable cbg = new GradientDrawable(); cbg.setColor(Color.parseColor("#0B0F17")); cbg.setCornerRadius(6f*density); cbg.setStroke(1, Color.parseColor("#334155"));
                codeTv.setBackground(cbg);
                dbox.addView(codeTv, new LinearLayout.LayoutParams(-1, -2));
            }

            Button bClose = createButton(L("完成关闭"), "#475569");
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2); clp.setMargins(0, (int)(10*density), 0, 0);
            bClose.setOnClickListener(v -> { animPlaying[0] = false; animGen[0]++; sd.dismiss(); if (renderTab2Ref[0] != null) renderTab2Ref[0].run(); });
            dbox.addView(bClose, clp);

            sd.setOnDismissListener(di -> { animPlaying[0] = false; animGen[0]++; });
            dsv.addView(dbox);
            int sw = getContext().getResources().getDisplayMetrics().widthPixels;
            sd.setContentView(dsv, new ViewGroup.LayoutParams((int)(sw * 0.88f), -2));
            sd.show();
        };

        // --- Tab 3：关联文件总览（支持直接调用 SND/SFF/判定框 工具打开） ---
        renderTab3Ref[0] = () -> {
            tab3Box.removeAllViews();
            if (defFileRef[0] == null) return;
            File baseDir = defFileRef[0].getParentFile();
            tab3Box.addView(createSubTitle(L("📄 DEF 引用文件清单 (点击右侧按钮直达对应工具):")));
            for (java.util.Map.Entry<String, String> en : defFilesMap.entrySet()) {
                final File rf = resolveCharSubFile(baseDir, en.getValue());
                LinearLayout row = new LinearLayout(getContext()); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding((int)(9*density), (int)(7*density), (int)(9*density), (int)(7*density));
                GradientDrawable rbg = new GradientDrawable(); rbg.setColor(Color.parseColor("#1C2230")); rbg.setCornerRadius(6f*density); rbg.setStroke(1, Color.parseColor("#334155"));
                row.setBackground(rbg);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2); rlp.setMargins(0, 0, 0, (int)(5*density));

                TextView tv = new TextView(getContext());
                String sz = (rf != null && rf.exists()) ? (" (" + (rf.length() / 1024) + " KB)") : L(" [缺失]");
                tv.setText(en.getKey() + " = " + en.getValue() + sz);
                applyGlobalFontSettings(tv, 0.85f, false);
                tv.setTextColor(rf != null && rf.exists() ? Color.parseColor("#E2E8F0") : Color.parseColor("#F87171"));
                row.addView(tv, new LinearLayout.LayoutParams(0, -2, 1f));

                if (rf != null && rf.exists()) {
                    String low = rf.getName().toLowerCase();
                    if (low.endsWith(".sff")) {
                        Button bSff = createButton(L("🖼️ SFF查看器"), "#6366F1");
                        bSff.setPadding((int)(8*density), (int)(4*density), (int)(8*density), (int)(4*density));
                        bSff.setOnClickListener(vv -> new Thread(() -> {
                            List<GoEngineBridge.SffFrame> fs = GoEngineBridge.getAllFrames(rf.getAbsolutePath());
                            uiHandler.post(() -> showAssetViewerWindow(rf.getName(), rf.getAbsolutePath(), fs));
                        }).start());
                        row.addView(bSff);
                    } else if (low.endsWith(".snd")) {
                        Button bSnd = createButton(L("🎵 SND预览器"), "#D97706");
                        bSnd.setPadding((int)(8*density), (int)(4*density), (int)(8*density), (int)(4*density));
                        bSnd.setOnClickListener(vv -> new Thread(() -> {
                            List<GoEngineBridge.SndNode> ns = GoEngineBridge.scanSnd(rf.getAbsolutePath());
                            uiHandler.post(() -> showSndViewerWindow(rf.getAbsolutePath(), rf.getName(), ns));
                        }).start());
                        row.addView(bSnd);
                    } else if (low.endsWith(".def") || low.endsWith(".cns") || low.endsWith(".const") || low.endsWith(".cmd") || low.endsWith(".st") || low.endsWith(".zss") || low.endsWith(".air") || low.endsWith(".lua") || low.endsWith(".json") || low.endsWith(".txt") || low.endsWith(".dat")) {
                        if (low.endsWith(".air")) {
                            Button bClsn = createButton(L("⚔️ 判定框"), "#DB2777");
                            bClsn.setPadding((int)(7*density), (int)(4*density), (int)(7*density), (int)(4*density));
                            bClsn.setOnClickListener(vv -> {
                                String sp = (sffFileRef[0] != null && sffFileRef[0].exists()) ? sffFileRef[0].getAbsolutePath() : "";
                                openAppWindow(L("⚔️ 判定框工坊 (打击/防御框)"), buildClsnEditorContent(sp, ""), null);
                            });
                            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2); cp.setMargins(0, 0, (int)(4*density), 0);
                            row.addView(bClsn, cp);
                        }
                        Button bView = createButton(L("👁️ 查看"), "#334155");
                        bView.setPadding((int)(8*density), (int)(4*density), (int)(8*density), (int)(4*density));
                        bView.setOnClickListener(vv -> {
                            final Dialog vd = new Dialog(getContext());
                            if (vd.getWindow() != null) vd.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                            ScrollView vsv = new ScrollView(getContext());
                            LinearLayout vb = new LinearLayout(getContext()); vb.setOrientation(LinearLayout.VERTICAL); vb.setPadding((int)(14*density), (int)(14*density), (int)(14*density), (int)(14*density));
                            GradientDrawable vbg = new GradientDrawable(); vbg.setColor(Color.parseColor("#F8141A24")); vbg.setCornerRadius(10f*density); vbg.setStroke(2, Color.parseColor("#38BDF8")); vb.setBackground(vbg);
                            vb.addView(createSubTitle("📄 " + rf.getName()));
                            String txt = readTextAutoCharset(rf);
                            if (txt.length() > 60000) txt = txt.substring(0, 60000) + "\n... (内容过长已截断预览)";
                            TextView ct = new TextView(getContext()); ct.setText(txt); ct.setTypeface(Typeface.MONOSPACE); ct.setTextSize(10.5f); ct.setTextColor(Color.parseColor("#CBD5E1")); ct.setTextIsSelectable(true);
                            vb.addView(ct);
                            Button bOk = createButton(L("关闭"), "#475569"); bOk.setOnClickListener(okv -> vd.dismiss()); vb.addView(bOk);
                            vsv.addView(vb);
                            int sw = getContext().getResources().getDisplayMetrics().widthPixels;
                            vd.setContentView(vsv, new ViewGroup.LayoutParams((int)(sw * 0.86f), -2)); vd.show();
                        });
                        row.addView(bView);
                    }
                }
                tab3Box.addView(row, rlp);
            }
        };


        // --- 核心解析器：载入角色 .def 及全部关联文件 ---
        final Runnable[] loadCharacterRef = {null};
        loadCharacterRef[0] = () -> {
            final File defFile = defFileRef[0];
            if (defFile == null || !defFile.exists()) return;
            tvCharMeta.setText(L("⏳ 正在深度解析角色 DEF / CNS / CMD / AIR / 出招表..."));
            new Thread(() -> {
                defInfoMap.clear(); defFilesMap.clear(); cnsStatsMap.clear();
                cmdItems.clear(); stateItems.clear(); allStateFiles.clear(); airActionFrames.clear();
                airActionRawText.clear(); cachedSffFrames.clear(); sffFrameLookup.clear();
                cnsFileRef[0] = null; sffFileRef[0] = null; sndFileRef[0] = null; airFileRef[0] = null; cmdFileRef[0] = null;
                movelistFileRef[0] = null; movelistContentRef[0] = "";

                File baseDir = defFile.getParentFile();
                String defText = readTextAutoCharset(defFile);
                String sec = "";
                for (String rawLine : defText.split("\n")) {
                    int ci = rawLine.indexOf(';');
                    String t = (ci >= 0 ? rawLine.substring(0, ci) : rawLine).trim();
                    if (t.isEmpty()) continue;
                    if (t.startsWith("[") && t.endsWith("]")) { sec = t.substring(1, t.length()-1).trim().toLowerCase(); continue; }
                    int eq = t.indexOf('=');
                    if (eq > 0) {
                        String k = t.substring(0, eq).trim().toLowerCase();
                        String v = t.substring(eq + 1).trim().replace("\"", "");
                        if ("info".equals(sec)) defInfoMap.put(k, v);
                        else if ("files".equals(sec)) defFilesMap.put(k, v);
                    }
                }

                cnsFileRef[0] = resolveCharSubFile(baseDir, defFilesMap.get("cns"));
                sffFileRef[0] = resolveCharSubFile(baseDir, defFilesMap.get("sprite"));
                sndFileRef[0] = resolveCharSubFile(baseDir, defFilesMap.get("sound"));
                airFileRef[0] = resolveCharSubFile(baseDir, defFilesMap.get("anim"));
                cmdFileRef[0] = resolveCharSubFile(baseDir, defFilesMap.get("cmd"));

                java.util.LinkedHashSet<File> stSet = new java.util.LinkedHashSet<>();
                if (cnsFileRef[0] != null) stSet.add(cnsFileRef[0]);
                for (java.util.Map.Entry<String, String> en : defFilesMap.entrySet()) {
                    String k = en.getKey();
                    if (k.equals("st") || k.matches("^st\\d+$") || k.equals("cns")) {
                        File sf = resolveCharSubFile(baseDir, en.getValue());
                        if (sf != null) stSet.add(sf);
                    }
                }
                if (cmdFileRef[0] != null) stSet.add(cmdFileRef[0]);
                allStateFiles.addAll(stSet);

                // 优先查 .def movelist* -> 再查同目录出招表文件
                for (String mk : new String[]{"movelist", "movelist0", "movelist1"}) {
                    if (defFilesMap.containsKey(mk)) {
                        File mf = resolveCharSubFile(baseDir, defFilesMap.get(mk));
                        if (mf != null && mf.exists()) { movelistFileRef[0] = mf; break; }
                    }
                }
                if (movelistFileRef[0] == null && baseDir != null) {
                    File[] sibs = baseDir.listFiles();
                    if (sibs != null) {
                        for (File sf : sibs) {
                            if (!sf.isFile()) continue;
                            String ln = sf.getName().toLowerCase();
                            if (ln.contains("movelist") || ln.contains("出招") || ln.contains("技表") || ln.contains("commandlist") || (ln.contains("readme") && ln.endsWith(".txt"))) {
                                movelistFileRef[0] = sf; break;
                            }
                        }
                    }
                }
                if (movelistFileRef[0] != null) {
                    movelistContentRef[0] = readTextAutoCharset(movelistFileRef[0]);
                }

                // 解析 CNS 基础属性
                if (cnsFileRef[0] != null) {
                    String cnsTxt = readTextAutoCharset(cnsFileRef[0]);
                    String cSec = "";
                    for (String rawLine : cnsTxt.split("\n")) {
                        int ci = rawLine.indexOf(';');
                        String t = (ci >= 0 ? rawLine.substring(0, ci) : rawLine).trim();
                        if (t.isEmpty()) continue;
                        if (t.startsWith("[") && t.endsWith("]")) { cSec = t.substring(1, t.length()-1).trim().toLowerCase(); continue; }
                        if ("data".equals(cSec) || "size".equals(cSec) || "velocity".equals(cSec) || "movement".equals(cSec)) {
                            int eq = t.indexOf('=');
                            if (eq > 0) {
                                String k = t.substring(0, eq).trim().toLowerCase();
                                String v = t.substring(eq + 1).trim();
                                cnsStatsMap.put(cSec + "|" + k, new String[]{v, v});
                            }
                        }
                    }
                }

                // 解析 CMD 指令与 Statedef -1 状态映射
                final java.util.Map<Integer, String> stateToCmdName = new java.util.HashMap<>();
                if (cmdFileRef[0] != null) {
                    String cmdTxt = readTextAutoCharset(cmdFileRef[0]);
                    String[] clines = cmdTxt.split("\n");
                    boolean inCmd = false, inNeg1 = false;
                    String curName = "", curSeq = "";
                    int curTarget = -1; String curTrigCmd = "";
                    for (String raw : clines) {
                        int ci = raw.indexOf(';');
                        String t = (ci >= 0 ? raw.substring(0, ci) : raw).trim();
                        if (t.isEmpty()) continue;
                        String low = t.toLowerCase();
                        if (low.startsWith("[command]")) {
                            if (inCmd && !curName.isEmpty() && !curSeq.isEmpty()) cmdItems.add(new CharCmdItem(curName, curSeq, prettifyCmdSequence(curSeq)));
                            inCmd = true; inNeg1 = false; curName = ""; curSeq = "";
                        } else if (low.startsWith("[statedef -1")) {
                            if (inCmd && !curName.isEmpty() && !curSeq.isEmpty()) cmdItems.add(new CharCmdItem(curName, curSeq, prettifyCmdSequence(curSeq)));
                            inCmd = false; inNeg1 = true; curTarget = -1; curTrigCmd = "";
                        } else if (low.startsWith("[state ")) {
                            if (inNeg1 && curTarget >= 0 && !curTrigCmd.isEmpty()) {
                                stateToCmdName.put(curTarget, curTrigCmd);
                                for (CharCmdItem item : cmdItems) { if (item.name.equalsIgnoreCase(curTrigCmd) && item.targetState < 0) item.targetState = curTarget; }
                            }
                            curTarget = -1; curTrigCmd = "";
                        } else if (low.startsWith("[")) {
                            if (inCmd && !curName.isEmpty() && !curSeq.isEmpty()) cmdItems.add(new CharCmdItem(curName, curSeq, prettifyCmdSequence(curSeq)));
                            inCmd = false; inNeg1 = false;
                        } else if (inCmd) {
                            int eq = t.indexOf('=');
                            if (eq > 0) {
                                String k = t.substring(0, eq).trim().toLowerCase();
                                String v = t.substring(eq + 1).trim().replace("\"", "");
                                if ("name".equals(k)) curName = v;
                                else if ("command".equals(k)) curSeq = v;
                            }
                        } else if (inNeg1) {
                            if (low.startsWith("value") && t.contains("=")) {
                                try { curTarget = Integer.parseInt(t.substring(t.indexOf('=') + 1).trim()); } catch (Exception ignored) {}
                            } else if (low.contains("command") && t.contains("\"")) {
                                java.util.regex.Matcher cm = java.util.regex.Pattern.compile("command\\s*=\\s*\"([^\"]+)\"", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(t);
                                if (cm.find()) {
                                    String cn = cm.group(1);
                                    if (!cn.startsWith("hold")) curTrigCmd = cn;
                                }
                            }
                        }
                    }
                    if (inNeg1 && curTarget >= 0 && !curTrigCmd.isEmpty()) {
                        stateToCmdName.put(curTarget, curTrigCmd);
                        for (CharCmdItem item : cmdItems) { if (item.name.equalsIgnoreCase(curTrigCmd) && item.targetState < 0) item.targetState = curTarget; }
                    }
                }

                // 缓存 SFF 帧表（供所有状态兜底预览 + 判定框轴心定位 + 复刻注入）
                if (sffFileRef[0] != null && sffFileRef[0].exists()) {
                    try {
                        List<GoEngineBridge.SffFrame> sfs = GoEngineBridge.getAllFrames(sffFileRef[0].getAbsolutePath());
                        if (sfs != null) {
                            cachedSffFrames.addAll(sfs);
                            for (GoEngineBridge.SffFrame sf : sfs) {
                                sffFrameLookup.put(sf.group + "," + sf.item, sf);
                            }
                        }
                    } catch (Throwable ignored) {}
                }

                // 解析 AIR 完整动作块 + Clsn1/Clsn2/Default 判定框
                if (airFileRef[0] != null) {
                    String airTxt = readTextAutoCharset(airFileRef[0]);
                    int curAct = -1;
                    StringBuilder curAirBlock = new StringBuilder();
                    List<int[]> defClsn1 = new ArrayList<>(), defClsn2 = new ArrayList<>();
                    List<int[]> curClsn1 = null, curClsn2 = null;
                    int readingBoxMode = 0; // 1=defC1, 2=defC2, 3=curC1, 4=curC2
                    for (String raw : airTxt.split("\n")) {
                        int ci = raw.indexOf(';');
                        String t = (ci >= 0 ? raw.substring(0, ci) : raw).trim();
                        String low = t.toLowerCase();
                        if (low.startsWith("[begin action")) {
                            if (curAct >= 0 && curAirBlock.length() > 0) airActionRawText.put(curAct, curAirBlock.toString().trim());
                            curAirBlock.setLength(0);
                            curAirBlock.append(raw).append("\n");
                            defClsn1.clear(); defClsn2.clear(); curClsn1 = null; curClsn2 = null; readingBoxMode = 0;
                            try { curAct = Integer.parseInt(t.replaceAll("[^0-9\\-]", "")); } catch (Exception e) { curAct = -1; }
                            continue;
                        }
                        if (curAct >= 0) curAirBlock.append(raw).append("\n");
                        if (t.isEmpty() || curAct < 0) continue;

                        if (low.startsWith("clsn1default:")) { defClsn1.clear(); readingBoxMode = 1; }
                        else if (low.startsWith("clsn2default:")) { defClsn2.clear(); readingBoxMode = 2; }
                        else if (low.startsWith("clsn1:")) { curClsn1 = new ArrayList<>(); readingBoxMode = 3; }
                        else if (low.startsWith("clsn2:")) { curClsn2 = new ArrayList<>(); readingBoxMode = 4; }
                        else if ((low.startsWith("clsn1[") || low.startsWith("clsn2[")) && t.contains("=")) {
                            String rhs = t.substring(t.indexOf('=') + 1).trim();
                            String[] bs = rhs.split(",");
                            if (bs.length >= 4) {
                                try {
                                    int[] box = new int[]{
                                        Integer.parseInt(bs[0].trim()), Integer.parseInt(bs[1].trim()),
                                        Integer.parseInt(bs[2].trim()), Integer.parseInt(bs[3].trim())
                                    };
                                    if (readingBoxMode == 1) defClsn1.add(box);
                                    else if (readingBoxMode == 2) defClsn2.add(box);
                                    else if (readingBoxMode == 3 && curClsn1 != null) curClsn1.add(box);
                                    else if (readingBoxMode == 4 && curClsn2 != null) curClsn2.add(box);
                                } catch (Exception ignored) {}
                            }
                        } else if (t.matches("^-?\\d+\\s*,\\s*-?\\d+\\s*,.*")) {
                            String[] ps = t.split(",");
                            if (ps.length >= 5) {
                                try {
                                    AirFrameSpec af = new AirFrameSpec();
                                    af.group = Integer.parseInt(ps[0].trim());
                                    af.item = Integer.parseInt(ps[1].trim());
                                    af.offX = Integer.parseInt(ps[2].trim());
                                    af.offY = Integer.parseInt(ps[3].trim());
                                    af.ticks = Integer.parseInt(ps[4].trim());
                                    if (curClsn1 != null) af.clsn1.addAll(curClsn1); else af.clsn1.addAll(defClsn1);
                                    if (curClsn2 != null) af.clsn2.addAll(curClsn2); else af.clsn2.addAll(defClsn2);
                                    List<AirFrameSpec> fl = airActionFrames.get(curAct);
                                    if (fl == null) { fl = new ArrayList<>(); airActionFrames.put(curAct, fl); }
                                    fl.add(af);
                                } catch (Exception ignored) {}
                            }
                            curClsn1 = null; curClsn2 = null; readingBoxMode = 0;
                        }
                    }
                    if (curAct >= 0 && curAirBlock.length() > 0) airActionRawText.put(curAct, curAirBlock.toString().trim());
                }

                 // 额外扫描 ZSS 文件（如 command.zss）中的 [StateDef -1] 出招映射：command = "..." -> changeState{value: N}
                java.util.regex.Pattern zCmdPat = java.util.regex.Pattern.compile("(?i)command\\s*=\\s*\"([^\"]+)\"");
                java.util.regex.Pattern zCsPat = java.util.regex.Pattern.compile("(?i)changestate\\s*\\{[^}]*?value\\s*:\\s*(\\d+)");
                for (File sf : allStateFiles) {
                    if (!sf.getName().toLowerCase().endsWith(".zss")) continue;
                    String zTxt = readTextAutoCharset(sf);
                    String[] zLines = zTxt.split("\n");
                    boolean inZNeg1 = false;
                    String recentCmd = "";
                    for (String zl : zLines) {
                        int hi = zl.indexOf('#');
                        String zc = (hi >= 0 ? zl.substring(0, hi) : zl).trim();
                        if (zc.isEmpty()) continue;
                        String zLow = zc.toLowerCase();
                        if (zLow.matches("^\\[\\s*statedef\\s+-1\\b.*")) { inZNeg1 = true; recentCmd = ""; continue; }
                        else if (zLow.matches("^\\[\\s*statedef\\s+.*") || zLow.startsWith("[function ")) { inZNeg1 = false; recentCmd = ""; continue; }
                        if (inZNeg1) {
                            java.util.regex.Matcher cm = zCmdPat.matcher(zc);
                            if (cm.find()) {
                                String cn = cm.group(1);
                                if (!cn.toLowerCase().startsWith("hold")) recentCmd = cn;
                            }
                            java.util.regex.Matcher csm = zCsPat.matcher(zc);
                            if (csm.find() && !recentCmd.isEmpty()) {
                                try {
                                    int tgt = Integer.parseInt(csm.group(1));
                                    if (!stateToCmdName.containsKey(tgt)) stateToCmdName.put(tgt, recentCmd);
                                    for (CharCmdItem item : cmdItems) { if (item.name.equalsIgnoreCase(recentCmd) && item.targetState < 0) item.targetState = tgt; }
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                }

                // 解析全部状态文件（原生双引擎：CNS 分号注释语法 + Ikemen GO ZSS 井号/花括号语法）
                java.util.regex.Pattern patSd = java.util.regex.Pattern.compile("^\\[\\s*statedef\\s+(-?\\d+)(.*)", java.util.regex.Pattern.CASE_INSENSITIVE);
                java.util.regex.Pattern patZssKv = java.util.regex.Pattern.compile("(?i)\\b(type|movetype|physics|anim|poweradd)\\s*:\\s*([A-Za-z0-9_\\-]+)");
                java.util.regex.Pattern patZssCa = java.util.regex.Pattern.compile("(?i)changeanim2?\\s*\\{[^}]*?value\\s*:\\s*(-?\\d+)");
                for (File sf : allStateFiles) {
                    boolean isZssFile = sf.getName().toLowerCase().endsWith(".zss");
                    String txt = readTextAutoCharset(sf);
                    String[] lines = txt.split("\n");
                    CharStateItem curSt = null;
                    StringBuilder curCode = new StringBuilder();
                    String curCtrlType = "";
                    CharHitItem curHit = null;
                    String lastComment = "";
                    boolean inZssHeader = false;
                    int zssHitBraceDepth = 0;
                    for (int li = 0; li < lines.length; li++) {
                        String raw = lines[li];
                        String trimmed = raw.trim();
                        if (isZssFile) {
                            if (trimmed.startsWith("#")) {
                                String cm = trimmed.replaceAll("^#+", "").trim();
                                if (cm.length() >= 2 && cm.length() <= 44 && !cm.startsWith("=") && !cm.startsWith("-")) lastComment = cm;
                                if (curSt != null) curCode.append(raw).append("\n");
                                continue;
                            }
                            int hi = raw.indexOf('#');
                            String clean = (hi >= 0 ? raw.substring(0, hi) : raw).trim();
                            if (clean.isEmpty()) { if (curSt != null) curCode.append(raw).append("\n"); continue; }
                            String low = clean.toLowerCase();
                            if (low.startsWith("[function ")) {
                                if (curSt != null) { curSt.codePreview = curCode.toString().trim(); stateItems.add(curSt); curSt = null; }
                                inZssHeader = false; curHit = null; zssHitBraceDepth = 0;
                                continue;
                            }
                            java.util.regex.Matcher sm = patSd.matcher(clean);
                            if (sm.find()) {
                                if (curSt != null) { curSt.codePreview = curCode.toString().trim(); stateItems.add(curSt); }
                                curSt = new CharStateItem();
                                curSt.stateNo = Integer.parseInt(sm.group(1));
                                curSt.sourceFile = sf;
                                String tailComment = (hi >= 0) ? raw.substring(hi + 1).trim() : "";
                                curSt.title = !tailComment.isEmpty() ? tailComment : lastComment;
                                if (stateToCmdName.containsKey(curSt.stateNo)) curSt.linkedCmd = stateToCmdName.get(curSt.stateNo);
                                curCode.setLength(0);
                                curCode.append(raw).append("\n");
                                curHit = null; zssHitBraceDepth = 0; lastComment = "";
                                inZssHeader = !clean.contains("]");
                                java.util.regex.Matcher kvm = patZssKv.matcher(clean);
                                while (kvm.find()) {
                                    String kk = kvm.group(1).toLowerCase(), vv = kvm.group(2);
                                    if ("type".equals(kk)) curSt.type = vv.toUpperCase();
                                    else if ("movetype".equals(kk)) curSt.moveType = vv.toUpperCase();
                                    else if ("physics".equals(kk)) curSt.physics = vv.toUpperCase();
                                    else if ("anim".equals(kk)) { try { curSt.anim = Integer.parseInt(vv); } catch (Exception ignored) {} }
                                    else if ("poweradd".equals(kk)) { try { curSt.powerAdd = Integer.parseInt(vv); } catch (Exception ignored) {} }
                                }
                                continue;
                            }
                            if (curSt != null) {
                                curCode.append(raw).append("\n");
                                if (inZssHeader) {
                                    java.util.regex.Matcher kvm = patZssKv.matcher(clean);
                                    while (kvm.find()) {
                                        String kk = kvm.group(1).toLowerCase(), vv = kvm.group(2);
                                        if ("type".equals(kk)) curSt.type = vv.toUpperCase();
                                        else if ("movetype".equals(kk)) curSt.moveType = vv.toUpperCase();
                                        else if ("physics".equals(kk)) curSt.physics = vv.toUpperCase();
                                        else if ("anim".equals(kk)) { try { curSt.anim = Integer.parseInt(vv); } catch (Exception ignored) {} }
                                        else if ("poweradd".equals(kk)) { try { curSt.powerAdd = Integer.parseInt(vv); } catch (Exception ignored) {} }
                                    }
                                    if (clean.contains("]")) inZssHeader = false;
                                    continue;
                                }
                                if (low.contains("nothitby{") || low.matches(".*\\bnothitby\\s*\\{.*")) curSt.hasInvul = true;
                                java.util.regex.Matcher cam = patZssCa.matcher(clean);
                                while (cam.find()) {
                                    try {
                                        int caVal = Integer.parseInt(cam.group(1));
                                        if (!curSt.changeAnimTargets.contains(caVal)) curSt.changeAnimTargets.add(caVal);
                                    } catch (Exception ignored) {}
                                }
                                if (low.matches(".*\\b(hitdef|projectile)\\s*\\{.*")) {
                                    curHit = new CharHitItem();
                                    curHit.sourceFile = sf;
                                    curHit.controllerType = low.contains("projectile") ? "Projectile" : "HitDef";
                                    zssHitBraceDepth = 0;
                                }
                                if (curHit != null) {
                                    for (int ci2 = 0; ci2 < clean.length(); ci2++) {
                                        char ch2 = clean.charAt(ci2);
                                        if (ch2 == '{') zssHitBraceDepth++;
                                        else if (ch2 == '}') zssHitBraceDepth--;
                                    }
                                    java.util.regex.Matcher am = java.util.regex.Pattern.compile("(?i)\\battr\\s*:\\s*([^;}\\n]+)").matcher(clean);
                                    if (am.find()) curHit.attr = am.group(1).trim();
                                    java.util.regex.Matcher gm = java.util.regex.Pattern.compile("(?i)\\bguardflag\\s*:\\s*([^;}\\n]+)").matcher(clean);
                                    if (gm.find()) curHit.guardFlag = gm.group(1).trim();
                                    java.util.regex.Matcher pm = java.util.regex.Pattern.compile("(?i)\\bpausetime\\s*:\\s*([^;}\\n]+)").matcher(clean);
                                    if (pm.find()) curHit.pauseTime = pm.group(1).trim();
                                    java.util.regex.Matcher dm = java.util.regex.Pattern.compile("(?i)\\bdamage\\s*:\\s*(\\d+)(?:\\s*,\\s*(\\d+))?").matcher(clean);
                                    if (dm.find()) {
                                        try {
                                            curHit.hitDamage = Integer.parseInt(dm.group(1).trim());
                                            curHit.guardDamage = dm.group(2) != null ? Integer.parseInt(dm.group(2).trim()) : 0;
                                            curHit.origHitDamage = curHit.hitDamage;
                                            curHit.origGuardDamage = curHit.guardDamage;
                                            curHit.lineIndex = li;
                                            curSt.hits.add(curHit);
                                        } catch (Exception ignored) {}
                                    }
                                    if (zssHitBraceDepth <= 0) curHit = null;
                                }
                            }
                            continue;
                        }
                        // --- 标准 CNS/CMD 语法解析 ---
                        if (trimmed.startsWith(";")) {
                            String cm = trimmed.replaceAll("^;+", "").trim();
                            if (cm.length() >= 2 && cm.length() <= 40 && !cm.startsWith("=")) lastComment = cm;
                            continue;
                        }
                        int ci = raw.indexOf(';');
                        String clean = (ci >= 0 ? raw.substring(0, ci) : raw).trim();
                        if (clean.isEmpty()) continue;
                        String low = clean.toLowerCase();

                        java.util.regex.Matcher sm = patSd.matcher(clean);
                        if (sm.find()) {
                            if (curSt != null) { curSt.codePreview = curCode.toString().trim(); stateItems.add(curSt); }
                            curSt = new CharStateItem();
                            curSt.stateNo = Integer.parseInt(sm.group(1));
                            curSt.sourceFile = sf;
                            String tailComment = (ci >= 0) ? raw.substring(ci + 1).trim() : "";
                            curSt.title = !tailComment.isEmpty() ? tailComment : lastComment;
                            if (stateToCmdName.containsKey(curSt.stateNo)) curSt.linkedCmd = stateToCmdName.get(curSt.stateNo);
                            curCode.setLength(0);
                            curCode.append(raw).append("\n");
                            curCtrlType = ""; curHit = null; lastComment = "";
                            continue;
                        }
                        if (curSt != null) {
                            curCode.append(raw).append("\n");
                            if (low.startsWith("[state ")) {
                                curCtrlType = ""; curHit = null;
                            } else {
                                int eq = clean.indexOf('=');
                                if (eq > 0) {
                                    String k = clean.substring(0, eq).trim().toLowerCase();
                                    String v = clean.substring(eq + 1).trim();
                                    if (curCtrlType.isEmpty()) {
                                        if ("type".equals(k)) {
                                            if (curSt.type.isEmpty() && (v.equalsIgnoreCase("S") || v.equalsIgnoreCase("C") || v.equalsIgnoreCase("A") || v.equalsIgnoreCase("L") || v.equalsIgnoreCase("U"))) {
                                                curSt.type = v.toUpperCase();
                                            } else {
                                                curCtrlType = v.toLowerCase();
                                                if (curCtrlType.equals("nothitby")) curSt.hasInvul = true;
                                                if (curCtrlType.equals("hitdef") || curCtrlType.equals("projectile")) {
                                                    curHit = new CharHitItem();
                                                    curHit.sourceFile = sf;
                                                    curHit.controllerType = curCtrlType.equals("hitdef") ? "HitDef" : "Projectile";
                                                }
                                            }
                                        } else if ("movetype".equals(k)) curSt.moveType = v.toUpperCase();
                                        else if ("physics".equals(k)) curSt.physics = v.toUpperCase();
                                        else if ("anim".equals(k)) { try { curSt.anim = Integer.parseInt(v); } catch (Exception ignored) {} }
                                        else if ("poweradd".equals(k)) { try { curSt.powerAdd = Integer.parseInt(v); } catch (Exception ignored) {} }
                                    } else if ((curCtrlType.equals("changeanim") || curCtrlType.equals("changeanim2")) && "value".equals(k)) {
                                        try {
                                            int caVal = Integer.parseInt(v.replaceAll("[^0-9\\-]", ""));
                                            if (!curSt.changeAnimTargets.contains(caVal)) curSt.changeAnimTargets.add(caVal);
                                        } catch (Exception ignored) {}
                                    } else if (curHit != null) {
                                        if ("attr".equals(k)) curHit.attr = v;
                                        else if ("guardflag".equals(k)) curHit.guardFlag = v;
                                        else if ("pausetime".equals(k)) curHit.pauseTime = v;
                                        else if ("damage".equals(k)) {
                                            String[] ds = v.split(",");
                                            try {
                                                curHit.hitDamage = Integer.parseInt(ds[0].trim());
                                                curHit.guardDamage = ds.length > 1 ? Integer.parseInt(ds[1].trim()) : 0;
                                                curHit.origHitDamage = curHit.hitDamage;
                                                curHit.origGuardDamage = curHit.guardDamage;
                                                curHit.lineIndex = li;
                                                curSt.hits.add(curHit);
                                            } catch (Exception ignored) {}
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (curSt != null) { curSt.codePreview = curCode.toString().trim(); stateItems.add(curSt); }
                }

                java.util.Collections.sort(stateItems, (a, b) -> Integer.compare(a.stateNo, b.stateNo));

                Bitmap portrait = null;
                if (sffFileRef[0] != null && sffFileRef[0].exists()) {
                    try {
                        byte[] pb = Api.decodeSffFrame(sffFileRef[0].getAbsolutePath(), 9000, 1, "");
                        if (pb == null || pb.length == 0) pb = Api.decodeSffFrame(sffFileRef[0].getAbsolutePath(), 9000, 0, "");
                        if (pb == null || pb.length == 0) pb = Api.getSffPreview(sffFileRef[0].getAbsolutePath());
                        if (pb != null && pb.length > 0) portrait = BitmapFactory.decodeByteArray(pb, 0, pb.length);
                    } catch (Throwable ignored) {}
                }
                final Bitmap finalPortrait = portrait;

                uiHandler.post(() -> {
                    isDirty[0] = false;
                    if (finalPortrait != null) portraitView.setImageBitmap(finalPortrait);
                    else portraitView.setImageResource(android.R.drawable.ic_menu_myplaces);
                    String disp = defInfoMap.containsKey("displayname") ? defInfoMap.get("displayname") : defInfoMap.get("name");
                    if (disp == null || disp.isEmpty()) disp = defFile.getName();
                    tvCharName.setText(disp);
                    String author = defInfoMap.containsKey("author") ? defInfoMap.get("author") : "-";
                    String mver = defInfoMap.containsKey("mugenversion") ? defInfoMap.get("mugenversion") : "-";
                    String ikver = defInfoMap.containsKey("ikemenversion") ? " | IK " + defInfoMap.get("ikemenversion") : "";
                    tvCharMeta.setText(L("作者: ") + author + " | MUGEN " + mver + ikver + " | " + L("状态: ") + stateItems.size() + " | " + L("指令: ") + cmdItems.size());
                    updateDirtyUI.run();
                    renderTab0Ref[0].run();
                    renderTab1Ref[0].run();
                    renderTab2Ref[0].run();
                    renderTab3Ref[0].run();
                });
            }).start();
        };

        // --- 核心保存器：仅在点击【💾 保存生效】时写入磁盘（自动创建 .bak 备份） ---
        saveAllRef[0] = () -> {
            if (defFileRef[0] == null) { Toast.makeText(getContext(), L("⚠️ 请先选择角色 .def 文件"), Toast.LENGTH_SHORT).show(); return; }
            if (!isDirty[0]) { Toast.makeText(getContext(), L("当前没有未保存的修改"), Toast.LENGTH_SHORT).show(); return; }
            new Thread(() -> {
                int savedFiles = 0;
                try {
                    if (cnsFileRef[0] != null && cnsFileRef[0].exists()) {
                        boolean anyStatChanged = false;
                        for (String[] p : cnsStatsMap.values()) { if (!p[0].equals(p[1])) { anyStatChanged = true; break; } }
                        if (anyStatChanged) {
                            File bak = new File(cnsFileRef[0].getAbsolutePath() + ".bak");
                            if (!bak.exists()) copyFileToSandbox(cnsFileRef[0], bak);
                            String raw = readTextAutoCharset(cnsFileRef[0]);
                            String[] lines = raw.split("\n", -1);
                            String curSec = "";
                            StringBuilder out = new StringBuilder();
                            for (int i = 0; i < lines.length; i++) {
                                String line = lines[i];
                                int ci = line.indexOf(';');
                                String codePart = ci >= 0 ? line.substring(0, ci) : line;
                                String commentPart = ci >= 0 ? line.substring(ci) : "";
                                String t = codePart.trim();
                                if (t.startsWith("[") && t.endsWith("]")) {
                                    curSec = t.substring(1, t.length()-1).trim().toLowerCase();
                                } else if (t.contains("=")) {
                                    int eq = codePart.indexOf('=');
                                    String k = codePart.substring(0, eq).trim().toLowerCase();
                                    String mk = curSec + "|" + k;
                                    String[] pair = cnsStatsMap.get(mk);
                                    if (pair != null && !pair[0].equals(pair[1])) {
                                        line = codePart.substring(0, eq + 1) + " " + pair[1] + (commentPart.isEmpty() ? "" : " " + commentPart);
                                        pair[0] = pair[1];
                                    }
                                }
                                out.append(line);
                                if (i + 1 < lines.length) out.append("\n");
                            }
                            try (FileOutputStream fos = new FileOutputStream(cnsFileRef[0])) { fos.write(out.toString().getBytes("UTF-8")); }
                            savedFiles++;
                        }
                    }

                    java.util.Map<File, List<CharHitItem>> dirtyByFile = new java.util.LinkedHashMap<>();
                    for (CharStateItem st : stateItems) {
                        for (CharHitItem h : st.hits) {
                            if (h.dirty && h.sourceFile != null && h.sourceFile.exists()) {
                                List<CharHitItem> fl = dirtyByFile.get(h.sourceFile);
                                if (fl == null) { fl = new ArrayList<>(); dirtyByFile.put(h.sourceFile, fl); }
                                fl.add(h);
                            }
                        }
                    }
                    for (java.util.Map.Entry<File, List<CharHitItem>> en : dirtyByFile.entrySet()) {
                        File f = en.getKey();
                        File bak = new File(f.getAbsolutePath() + ".bak");
                        if (!bak.exists()) copyFileToSandbox(f, bak);
                        String raw = readTextAutoCharset(f);
                        String[] lines = raw.split("\n", -1);
                        boolean isZssSave = f.getName().toLowerCase().endsWith(".zss");
                        for (CharHitItem h : en.getValue()) {
                            if (h.lineIndex >= 0 && h.lineIndex < lines.length) {
                                String line = lines[h.lineIndex];
                                if (isZssSave) {
                                    int hi = line.indexOf('#');
                                    String codePart = hi >= 0 ? line.substring(0, hi) : line;
                                    String commentPart = hi >= 0 ? line.substring(hi) : "";
                                    String replaced = codePart.replaceFirst("(?i)(\\bdamage\\s*:\\s*)\\d+(?:\\s*,\\s*\\d+)?", "$1" + h.hitDamage + ", " + h.guardDamage);
                                    if (!replaced.equals(codePart)) {
                                        lines[h.lineIndex] = replaced + commentPart;
                                        h.origHitDamage = h.hitDamage;
                                        h.origGuardDamage = h.guardDamage;
                                        h.dirty = false;
                                    }
                                } else {
                                    int ci = line.indexOf(';');
                                    String codePart = ci >= 0 ? line.substring(0, ci) : line;
                                    String commentPart = ci >= 0 ? line.substring(ci) : "";
                                    int eq = codePart.indexOf('=');
                                    if (eq > 0 && codePart.substring(0, eq).trim().equalsIgnoreCase("damage")) {
                                        lines[h.lineIndex] = codePart.substring(0, eq + 1) + " " + h.hitDamage + ", " + h.guardDamage + (commentPart.isEmpty() ? "" : " " + commentPart);
                                        h.origHitDamage = h.hitDamage;
                                        h.origGuardDamage = h.guardDamage;
                                        h.dirty = false;
                                    }
                                }
                            }
                        }
                        StringBuilder out = new StringBuilder();
                        for (int i = 0; i < lines.length; i++) {
                            out.append(lines[i]);
                            if (i + 1 < lines.length) out.append("\n");
                        }
                        try (FileOutputStream fos = new FileOutputStream(f)) { fos.write(out.toString().getBytes("UTF-8")); }
                        savedFiles++;
                    }
                } catch (Exception e) {
                    uiHandler.post(() -> Toast.makeText(getContext(), L("❌ 保存异常: ") + e.getMessage(), Toast.LENGTH_LONG).show());
                    return;
                }
                final int cnt = savedFiles;
                uiHandler.post(() -> {
                    isDirty[0] = false;
                    updateDirtyUI.run();
                    if (renderTab2Ref[0] != null) renderTab2Ref[0].run();
                    Toast.makeText(getContext(), L("✅ 保存成功！已更新 ") + cnt + L(" 个角色文件（原文件已自动备份 .bak）"), Toast.LENGTH_LONG).show();
                });
            }).start();
        };

        btnPickDef.setOnClickListener(v -> showWin10FilePicker(L("选择角色 .def 配置文件"), 15, null, null, f -> {
            defFileRef[0] = f;
            loadCharacterRef[0].run();
        }));
        btnSaveAll.setOnClickListener(v -> saveAllRef[0].run());
        btnRevert.setOnClickListener(v -> {
            if (defFileRef[0] == null) return;
            loadCharacterRef[0].run();
            Toast.makeText(getContext(), L("↩️ 已放弃所有未保存修改并重新载入"), Toast.LENGTH_SHORT).show();
        });
        btnJumpSff.setOnClickListener(v -> {
            if (sffFileRef[0] == null || !sffFileRef[0].exists()) { Toast.makeText(getContext(), L("⚠️ 未找到该角色的 SFF 文件"), Toast.LENGTH_SHORT).show(); return; }
            new Thread(() -> {
                List<GoEngineBridge.SffFrame> fs = GoEngineBridge.getAllFrames(sffFileRef[0].getAbsolutePath());
                uiHandler.post(() -> showAssetViewerWindow(sffFileRef[0].getName(), sffFileRef[0].getAbsolutePath(), fs));
            }).start();
        });
        btnJumpSnd.setOnClickListener(v -> {
            if (sndFileRef[0] == null || !sndFileRef[0].exists()) { Toast.makeText(getContext(), L("⚠️ 未找到该角色的 SND 文件"), Toast.LENGTH_SHORT).show(); return; }
            new Thread(() -> {
                List<GoEngineBridge.SndNode> ns = GoEngineBridge.scanSnd(sndFileRef[0].getAbsolutePath());
                uiHandler.post(() -> showSndViewerWindow(sndFileRef[0].getAbsolutePath(), sndFileRef[0].getName(), ns));
            }).start();
        });
        btnJumpClsn.setOnClickListener(v -> {
            String sp = (sffFileRef[0] != null && sffFileRef[0].exists()) ? sffFileRef[0].getAbsolutePath() : "";
            openAppWindow(L("⚔️ 判定框工坊 (打击/防御框)"), buildClsnEditorContent(sp, ""), null);
        });

        return root;
    }

    // =========================================================================
    // 【模块十一】：🤖 AI对话工坊 (全协议兼容 + 多Key + 模型拉取 + 多池/备用池自动故障转移)
    // =========================================================================
    private static class AiEndpointConfig {
        String id;
        String name;
        String protocol; // openai, gemini, claude, ollama, cohere, azure
        String baseUrl;
        List<String> keys = new ArrayList<>();
        List<String> models = new ArrayList<>();
        boolean enabled = true;
    }

    private static class AiPoolConfig {
        String id;
        String name;
        boolean isPrimary = false;
        List<AiEndpointConfig> endpoints = new ArrayList<>();
    }

    private static class AiChatMessage {
        String role; // user, assistant
        String content;
        String meta;
        AiChatMessage(String role, String content, String meta) {
            this.role = role; this.content = content; this.meta = meta;
        }
    }

    private static class AiCallResult {
        boolean ok;
        String reply;
        String usedInfo;
        String trace;
        String error;
    }

    private interface AiStreamCallback { void onDelta(String deltaText); }

    private static final String PREF_AI_POOLS = "ikemen_ai_pools_v2";
    private static final String PREF_AI_ACTIVE_POOL = "ikemen_ai_active_pool_v2";
    private static final String PREF_AI_ACTIVE_MODEL = "ikemen_ai_active_model_v2";
    private static final String PREF_AI_SYS_PROMPT = "ikemen_ai_sys_prompt_v1";
    private static final String PREF_AI_TEMP = "ikemen_ai_temp_v1";
    private static final String PREF_AI_MAX_TOKENS = "ikemen_ai_max_tokens_v1";
    private static final String PREF_AI_CTX_TURNS = "ikemen_ai_ctx_turns_v1";
    private static final String PREF_AI_STREAM = "ikemen_ai_stream_v1";

    private List<AiPoolConfig> loadAiPools() {
        List<AiPoolConfig> list = new ArrayList<>();
        String raw = prefs.getString(PREF_AI_POOLS, "");
        if (raw != null && !raw.trim().isEmpty()) {
            try {
                org.json.JSONArray arr = new org.json.JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject po = arr.getJSONObject(i);
                    AiPoolConfig p = new AiPoolConfig();
                    p.id = po.optString("id", "pool_" + i);
                    p.name = po.optString("name", "Pool " + (i + 1));
                    p.isPrimary = po.optBoolean("isPrimary", i == 0);
                    org.json.JSONArray eps = po.optJSONArray("endpoints");
                    if (eps != null) {
                        for (int j = 0; j < eps.length(); j++) {
                            JSONObject eo = eps.getJSONObject(j);
                            AiEndpointConfig ep = new AiEndpointConfig();
                            ep.id = eo.optString("id", "ep_" + j);
                            ep.name = eo.optString("name", "API " + (j + 1));
                            ep.protocol = eo.optString("protocol", "openai");
                            ep.baseUrl = eo.optString("baseUrl", "");
                            ep.enabled = eo.optBoolean("enabled", true);
                            org.json.JSONArray ks = eo.optJSONArray("keys");
                            if (ks != null) for (int k = 0; k < ks.length(); k++) ep.keys.add(ks.optString(k, ""));
                            org.json.JSONArray ms = eo.optJSONArray("models");
                            if (ms != null) for (int m = 0; m < ms.length(); m++) ep.models.add(ms.optString(m, ""));
                            p.endpoints.add(ep);
                        }
                    }
                    list.add(p);
                }
            } catch (Exception ignored) {}
        }
        if (list.isEmpty()) {
            // 默认预填双免费测试节点的主池：优先 LLM7.io (免Key/无单次402限制) + Pollinations 备用，自动故障切换
            AiPoolConfig primary = new AiPoolConfig();
            primary.id = "pool_primary";
            primary.name = L("主池 · 免费测试 (LLM7 + Pollinations)");
            primary.isPrimary = true;

            AiEndpointConfig epLlm7 = new AiEndpointConfig();
            epLlm7.id = "ep_llm7";
            epLlm7.name = "LLM7.io Free (OpenAI)";
            epLlm7.protocol = "openai";
            epLlm7.baseUrl = "https://" + "api.llm7.io/v1";
            epLlm7.keys.add("unused");
            epLlm7.models.add("default");
            epLlm7.models.add("gpt-4o-mini-2024-07-18");
            epLlm7.models.add("mistral-small-2503");
            primary.endpoints.add(epLlm7);

            AiEndpointConfig epFree = new AiEndpointConfig();
            epFree.id = "ep_pollinations";
            epFree.name = "Pollinations Free (OpenAI)";
            epFree.protocol = "openai";
            epFree.baseUrl = "https://" + "text.pollinations.ai/openai";
            epFree.keys.add("pollinations-free-test");
            epFree.models.add("openai");
            epFree.models.add("mistral");
            epFree.models.add("searchgpt");
            primary.endpoints.add(epFree);
            list.add(primary);

            // 预置备用池 1：Google Gemini + OpenRouter 模板（填入自己的 Key 即可自动接管）
            AiPoolConfig backup1 = new AiPoolConfig();
            backup1.id = "pool_backup_1";
            backup1.name = L("备用池1 · Gemini / OpenRouter");
            backup1.isPrimary = false;

            AiEndpointConfig epGem = new AiEndpointConfig();
            epGem.id = "ep_gemini_tpl";
            epGem.name = "Google Gemini (原生)";
            epGem.protocol = "gemini";
            epGem.baseUrl = "https://" + "generativelanguage.googleapis.com";
            epGem.models.add("gemini-2.5-flash");
            epGem.models.add("gemini-2.0-flash");
            epGem.enabled = false;
            backup1.endpoints.add(epGem);

            AiEndpointConfig epOr = new AiEndpointConfig();
            epOr.id = "ep_openrouter_tpl";
            epOr.name = "OpenRouter / DeepSeek (OpenAI兼容)";
            epOr.protocol = "openai";
            epOr.baseUrl = "https://" + "openrouter.ai/api/v1";
            epOr.models.add("deepseek/deepseek-chat-v3-0324:free");
            epOr.models.add("google/gemini-2.0-flash-exp:free");
            epOr.enabled = false;
            backup1.endpoints.add(epOr);
            list.add(backup1);

            saveAiPools(list);
        }
        return list;
    }

    private void saveAiPools(List<AiPoolConfig> pools) {
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (AiPoolConfig p : pools) {
                JSONObject po = new JSONObject();
                po.put("id", p.id);
                po.put("name", p.name);
                po.put("isPrimary", p.isPrimary);
                org.json.JSONArray eps = new org.json.JSONArray();
                for (AiEndpointConfig ep : p.endpoints) {
                    JSONObject eo = new JSONObject();
                    eo.put("id", ep.id);
                    eo.put("name", ep.name);
                    eo.put("protocol", ep.protocol);
                    eo.put("baseUrl", ep.baseUrl);
                    eo.put("enabled", ep.enabled);
                    org.json.JSONArray ks = new org.json.JSONArray();
                    for (String k : ep.keys) if (k != null && !k.trim().isEmpty()) ks.put(k.trim());
                    eo.put("keys", ks);
                    org.json.JSONArray ms = new org.json.JSONArray();
                    for (String m : ep.models) if (m != null && !m.trim().isEmpty()) ms.put(m.trim());
                    eo.put("models", ms);
                    eps.put(eo);
                }
                po.put("endpoints", eps);
                arr.put(po);
            }
            prefs.edit().putString(PREF_AI_POOLS, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private String aiHttpRequest(String urlStr, String method, java.util.Map<String, String> headers, String jsonBody, int[] outCode) throws Exception {
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(urlStr).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(45000);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "IkemenDroid-AIStudio/1.0");
        if (headers != null) {
            for (java.util.Map.Entry<String, String> h : headers.entrySet()) {
                if (h.getValue() != null && !h.getValue().isEmpty()) conn.setRequestProperty(h.getKey(), h.getValue());
            }
        }
        if (jsonBody != null) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            byte[] data = jsonBody.getBytes("UTF-8");
            try (java.io.OutputStream os = conn.getOutputStream()) { os.write(data); }
        }
        int code = conn.getResponseCode();
        if (outCode != null && outCode.length > 0) outCode[0] = code;
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append("\n");
        br.close();
        conn.disconnect();
        return sb.toString().trim();
    }

    private List<String> fetchModelsForEndpoint(AiEndpointConfig ep, String apiKey) throws Exception {
        List<String> out = new ArrayList<>();
        String base = ep.baseUrl == null ? "" : ep.baseUrl.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        java.util.Map<String, String> hdrs = new java.util.HashMap<>();
        int[] code = {0};

        if ("gemini".equalsIgnoreCase(ep.protocol)) {
            String url = base.contains("/v1") ? (base + "/models") : (base + "/v1beta/models");
            if (apiKey != null && !apiKey.isEmpty()) url += (url.contains("?") ? "&key=" : "?key=") + java.net.URLEncoder.encode(apiKey, "UTF-8");
            String resp = aiHttpRequest(url, "GET", hdrs, null, code);
            if (code[0] < 200 || code[0] >= 300) throw new Exception("HTTP " + code[0] + ": " + resp);
            JSONObject jo = new JSONObject(resp);
            org.json.JSONArray ms = jo.optJSONArray("models");
            if (ms != null) {
                for (int i = 0; i < ms.length(); i++) {
                    String n = ms.getJSONObject(i).optString("name", "");
                    if (n.startsWith("models/")) n = n.substring(7);
                    if (!n.isEmpty()) out.add(n);
                }
            }
        } else if ("ollama".equalsIgnoreCase(ep.protocol)) {
            String url = base.endsWith("/api/tags") ? base : (base + "/api/tags");
            if (apiKey != null && !apiKey.isEmpty()) hdrs.put("Authorization", "Bearer " + apiKey);
            String resp = aiHttpRequest(url, "GET", hdrs, null, code);
            if (code[0] < 200 || code[0] >= 300) throw new Exception("HTTP " + code[0] + ": " + resp);
            JSONObject jo = new JSONObject(resp);
            org.json.JSONArray ms = jo.optJSONArray("models");
            if (ms != null) {
                for (int i = 0; i < ms.length(); i++) {
                    String n = ms.getJSONObject(i).optString("name", "");
                    if (!n.isEmpty()) out.add(n);
                }
            }
        } else if ("claude".equalsIgnoreCase(ep.protocol)) {
            String url = base.endsWith("/models") ? base : (base.endsWith("/v1") ? base + "/models" : base + "/v1/models");
            if (apiKey != null && !apiKey.isEmpty()) hdrs.put("x-api-key", apiKey);
            hdrs.put("anthropic-version", "2023-06-01");
            String resp = aiHttpRequest(url, "GET", hdrs, null, code);
            if (code[0] < 200 || code[0] >= 300) throw new Exception("HTTP " + code[0] + ": " + resp);
            JSONObject jo = new JSONObject(resp);
            org.json.JSONArray ds = jo.optJSONArray("data");
            if (ds != null) for (int i = 0; i < ds.length(); i++) { String id = ds.getJSONObject(i).optString("id", ""); if (!id.isEmpty()) out.add(id); }
        } else if ("cohere".equalsIgnoreCase(ep.protocol)) {
            String url = base.endsWith("/models") ? base : (base + "/v1/models");
            if (apiKey != null && !apiKey.isEmpty()) hdrs.put("Authorization", "Bearer " + apiKey);
            String resp = aiHttpRequest(url, "GET", hdrs, null, code);
            if (code[0] < 200 || code[0] >= 300) throw new Exception("HTTP " + code[0] + ": " + resp);
            JSONObject jo = new JSONObject(resp);
            org.json.JSONArray ms = jo.optJSONArray("models");
            if (ms != null) for (int i = 0; i < ms.length(); i++) { String id = ms.getJSONObject(i).optString("name", ""); if (!id.isEmpty()) out.add(id); }
        } else {
            // openai / azure / pollinations / generic
            String url;
            if (base.contains("text.pollinations.ai")) {
                url = "https://" + "text.pollinations.ai/models";
            } else if (base.endsWith("/chat/completions")) {
                url = base.substring(0, base.length() - "/chat/completions".length()) + "/models";
            } else if (base.endsWith("/models")) {
                url = base;
            } else if (base.endsWith("/v1") || base.endsWith("/openai")) {
                url = base + "/models";
            } else {
                url = base + "/v1/models";
            }
            if (apiKey != null && !apiKey.isEmpty() && !"pollinations-free-test".equals(apiKey)) {
                if ("azure".equalsIgnoreCase(ep.protocol)) hdrs.put("api-key", apiKey);
                else hdrs.put("Authorization", "Bearer " + apiKey);
            }
            String resp = aiHttpRequest(url, "GET", hdrs, null, code);
            if (code[0] < 200 || code[0] >= 300) throw new Exception("HTTP " + code[0] + ": " + resp);
            String trim = resp.trim();
            if (trim.startsWith("[")) {
                org.json.JSONArray arr = new org.json.JSONArray(trim);
                for (int i = 0; i < arr.length(); i++) {
                    Object o = arr.get(i);
                    if (o instanceof JSONObject) {
                        String n = ((JSONObject) o).optString("name", ((JSONObject) o).optString("id", ""));
                        if (!n.isEmpty()) out.add(n);
                    } else if (o instanceof String) {
                        out.add((String) o);
                    }
                }
            } else {
                JSONObject jo = new JSONObject(trim);
                org.json.JSONArray ds = jo.optJSONArray("data");
                if (ds != null) {
                    for (int i = 0; i < ds.length(); i++) {
                        String id = ds.getJSONObject(i).optString("id", "");
                        if (!id.isEmpty()) out.add(id);
                    }
                }
            }
        }
        return out;
    }

        private String aiHttpStreamRequest(String urlStr, java.util.Map<String, String> headers, String jsonBody, String proto, AiStreamCallback cb) throws Exception {
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setRequestProperty("Accept", "text/event-stream, application/json");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("User-Agent", "IkemenDroid-AIStudio/1.0");
        if (headers != null) {
            for (java.util.Map.Entry<String, String> h : headers.entrySet()) {
                if (h.getValue() != null && !h.getValue().isEmpty()) conn.setRequestProperty(h.getKey(), h.getValue());
            }
        }
        conn.setDoOutput(true);
        try (java.io.OutputStream os = conn.getOutputStream()) { os.write(jsonBody.getBytes("UTF-8")); }
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) { conn.disconnect(); throw new Exception("HTTP " + code); }
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        if (code < 200 || code >= 300) {
            StringBuilder err = new StringBuilder();
            String l; while ((l = br.readLine()) != null) err.append(l);
            br.close(); conn.disconnect();
            throw new Exception("HTTP " + code + ": " + err.toString());
        }
        StringBuilder fullOut = new StringBuilder();
        StringBuilder rawFallback = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            rawFallback.append(line).append("\n");
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("data:")) trimmed = trimmed.substring(5).trim();
            if (trimmed.equals("[DONE]") || !trimmed.startsWith("{")) continue;
            try {
                JSONObject jo = new JSONObject(trimmed);
                String delta = "";
                if ("gemini".equals(proto)) {
                    org.json.JSONArray cands = jo.optJSONArray("candidates");
                    if (cands != null && cands.length() > 0) {
                        JSONObject cont = cands.getJSONObject(0).optJSONObject("content");
                        if (cont != null) {
                            org.json.JSONArray parts = cont.optJSONArray("parts");
                            if (parts != null) for (int i = 0; i < parts.length(); i++) delta += parts.getJSONObject(i).optString("text", "");
                        }
                    }
                } else if ("claude".equals(proto)) {
                    JSONObject d = jo.optJSONObject("delta");
                    if (d != null) delta = d.optString("text", "");
                } else if ("ollama".equals(proto)) {
                    JSONObject m = jo.optJSONObject("message");
                    if (m != null) delta = m.optString("content", "");
                } else if ("cohere".equals(proto)) {
                    JSONObject d = jo.optJSONObject("delta");
                    if (d != null && d.optJSONObject("message") != null && d.optJSONObject("message").optJSONObject("content") != null) {
                        delta = d.optJSONObject("message").optJSONObject("content").optString("text", "");
                    }
                } else {
                    org.json.JSONArray ch = jo.optJSONArray("choices");
                    if (ch != null && ch.length() > 0) {
                        JSONObject c0 = ch.getJSONObject(0);
                        JSONObject d = c0.optJSONObject("delta");
                        if (d != null) delta = d.optString("content", "");
                        else if (c0.optJSONObject("message") != null) delta = c0.optJSONObject("message").optString("content", "");
                    }
                }
                if (delta != null && !delta.isEmpty()) {
                    fullOut.append(delta);
                    if (cb != null) cb.onDelta(delta);
                }
            } catch (Exception ignored) {}
        }
        br.close();
        conn.disconnect();
        if (fullOut.length() > 0) return fullOut.toString().trim();
        // 若接口忽略 stream=true 返回纯文本，兜底返回
        String rawStr = rawFallback.toString().trim();
        if (!rawStr.isEmpty() && !rawStr.startsWith("{")) {
            if (cb != null) cb.onDelta(rawStr);
            return rawStr;
        }
        throw new Exception("流式接口返回空内容");
    }

    private String callSingleAiEndpoint(AiEndpointConfig ep, String apiKey, String model, List<AiChatMessage> history, String sysPrompt, double temp, int maxTokens, boolean useStream, AiStreamCallback streamCb) throws Exception {
        String base = ep.baseUrl == null ? "" : ep.baseUrl.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        java.util.Map<String, String> hdrs = new java.util.HashMap<>();
        int[] code = {0};
        String proto = ep.protocol == null ? "openai" : ep.protocol.toLowerCase();

        if ("gemini".equals(proto)) {
            String suffix = useStream ? ":streamGenerateContent?alt=sse" : ":generateContent";
            String url = base.contains("/v1")
                ? (base + "/models/" + model + suffix)
                : (base + "/v1beta/models/" + model + suffix);
            if (apiKey != null && !apiKey.isEmpty()) url += (url.contains("?") ? "&key=" : "?key=") + java.net.URLEncoder.encode(apiKey, "UTF-8");
            JSONObject body = new JSONObject();
            if (sysPrompt != null && !sysPrompt.trim().isEmpty()) {
                JSONObject si = new JSONObject();
                org.json.JSONArray sp = new org.json.JSONArray();
                sp.put(new JSONObject().put("text", sysPrompt));
                si.put("parts", sp);
                body.put("system_instruction", si);
            }
            org.json.JSONArray contents = new org.json.JSONArray();
            for (AiChatMessage m : history) {
                JSONObject c = new JSONObject();
                c.put("role", "assistant".equals(m.role) ? "model" : "user");
                c.put("parts", new org.json.JSONArray().put(new JSONObject().put("text", m.content)));
                contents.put(c);
            }
            body.put("contents", contents);
            JSONObject gcfg = new JSONObject();
            gcfg.put("temperature", temp);
            if (maxTokens > 0) gcfg.put("maxOutputTokens", maxTokens);
            body.put("generationConfig", gcfg);

            if (useStream) return aiHttpStreamRequest(url, hdrs, body.toString(), proto, streamCb);
            String resp = aiHttpRequest(url, "POST", hdrs, body.toString(), code);
            if (code[0] < 200 || code[0] >= 300) throw new Exception("HTTP " + code[0] + ": " + resp);
            JSONObject jo = new JSONObject(resp);
            org.json.JSONArray cands = jo.optJSONArray("candidates");
            if (cands != null && cands.length() > 0) {
                JSONObject cont = cands.getJSONObject(0).optJSONObject("content");
                if (cont != null) {
                    org.json.JSONArray parts = cont.optJSONArray("parts");
                    if (parts != null && parts.length() > 0) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < parts.length(); i++) sb.append(parts.getJSONObject(i).optString("text", ""));
                        if (sb.length() > 0) return sb.toString().trim();
                    }
                }
            }
            throw new Exception("Gemini 返回空内容");
        } else if ("claude".equals(proto)) {
            String url = base.endsWith("/messages") ? base : (base.endsWith("/v1") ? base + "/messages" : base + "/v1/messages");
            if (apiKey != null && !apiKey.isEmpty()) hdrs.put("x-api-key", apiKey);
            hdrs.put("anthropic-version", "2023-06-01");
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("max_tokens", maxTokens > 0 ? maxTokens : 2048);
            body.put("temperature", temp);
            if (useStream) body.put("stream", true);
            if (sysPrompt != null && !sysPrompt.trim().isEmpty()) body.put("system", sysPrompt);
            org.json.JSONArray msgs = new org.json.JSONArray();
            for (AiChatMessage m : history) {
                msgs.put(new JSONObject().put("role", "assistant".equals(m.role) ? "assistant" : "user").put("content", m.content));
            }
            body.put("messages", msgs);

            if (useStream) return aiHttpStreamRequest(url, hdrs, body.toString(), proto, streamCb);
            String resp = aiHttpRequest(url, "POST", hdrs, body.toString(), code);
            if (code[0] < 200 || code[0] >= 300) throw new Exception("HTTP " + code[0] + ": " + resp);
            JSONObject jo = new JSONObject(resp);
            org.json.JSONArray carr = jo.optJSONArray("content");
            if (carr != null && carr.length() > 0) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < carr.length(); i++) sb.append(carr.getJSONObject(i).optString("text", ""));
                if (sb.length() > 0) return sb.toString().trim();
            }
            throw new Exception("Claude 返回空内容");
        } else if ("ollama".equals(proto)) {
            String url = base.endsWith("/api/chat") ? base : (base + "/api/chat");
            if (apiKey != null && !apiKey.isEmpty()) hdrs.put("Authorization", "Bearer " + apiKey);
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("stream", useStream);
            org.json.JSONArray msgs = new org.json.JSONArray();
            if (sysPrompt != null && !sysPrompt.trim().isEmpty()) msgs.put(new JSONObject().put("role", "system").put("content", sysPrompt));
            for (AiChatMessage m : history) msgs.put(new JSONObject().put("role", m.role).put("content", m.content));
            body.put("messages", msgs);
            body.put("options", new JSONObject().put("temperature", temp));

            if (useStream) return aiHttpStreamRequest(url, hdrs, body.toString(), proto, streamCb);
            String resp = aiHttpRequest(url, "POST", hdrs, body.toString(), code);
            if (code[0] < 200 || code[0] >= 300) throw new Exception("HTTP " + code[0] + ": " + resp);
            JSONObject jo = new JSONObject(resp);
            JSONObject msg = jo.optJSONObject("message");
            if (msg != null && !msg.optString("content", "").trim().isEmpty()) return msg.optString("content").trim();
            throw new Exception("Ollama 返回空内容");
        } else if ("cohere".equals(proto)) {
            String url = base.endsWith("/chat") ? base : (base + "/v2/chat");
            if (apiKey != null && !apiKey.isEmpty()) hdrs.put("Authorization", "Bearer " + apiKey);
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("temperature", temp);
            if (useStream) body.put("stream", true);
            org.json.JSONArray msgs = new org.json.JSONArray();
            if (sysPrompt != null && !sysPrompt.trim().isEmpty()) msgs.put(new JSONObject().put("role", "system").put("content", sysPrompt));
            for (AiChatMessage m : history) msgs.put(new JSONObject().put("role", m.role).put("content", m.content));
            body.put("messages", msgs);

            if (useStream) return aiHttpStreamRequest(url, hdrs, body.toString(), proto, streamCb);
            String resp = aiHttpRequest(url, "POST", hdrs, body.toString(), code);
            if (code[0] < 200 || code[0] >= 300) throw new Exception("HTTP " + code[0] + ": " + resp);
            JSONObject jo = new JSONObject(resp);
            JSONObject msg = jo.optJSONObject("message");
            if (msg != null) {
                org.json.JSONArray carr = msg.optJSONArray("content");
                if (carr != null && carr.length() > 0) {
                    String txt = carr.getJSONObject(0).optString("text", "").trim();
                    if (!txt.isEmpty()) return txt;
                }
            }
            throw new Exception("Cohere 返回空内容");
        } else {
            // openai & azure & llm7 & pollinations 兼容协议
            String url;
            if ("azure".equals(proto)) {
                url = base.contains("/chat/completions") ? base : (base + "/openai/deployments/" + model + "/chat/completions?api-version=2024-06-01");
                if (apiKey != null && !apiKey.isEmpty()) hdrs.put("api-key", apiKey);
            } else {
                if (base.endsWith("/chat/completions")) url = base;
                else if (base.contains("text.pollinations.ai/openai")) url = base;
                else if (base.endsWith("/v1")) url = base + "/chat/completions";
                else url = base + "/v1/chat/completions";
                if (apiKey != null && !apiKey.isEmpty() && !"pollinations-free-test".equals(apiKey)) {
                    hdrs.put("Authorization", "Bearer " + apiKey);
                }
            }
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("temperature", temp);
            if (useStream) body.put("stream", true);
            if (maxTokens > 0) body.put("max_tokens", maxTokens);
            org.json.JSONArray msgs = new org.json.JSONArray();
            if (sysPrompt != null && !sysPrompt.trim().isEmpty()) msgs.put(new JSONObject().put("role", "system").put("content", sysPrompt));
            for (AiChatMessage m : history) msgs.put(new JSONObject().put("role", m.role).put("content", m.content));
            body.put("messages", msgs);

            if (useStream) return aiHttpStreamRequest(url, hdrs, body.toString(), proto, streamCb);
            String resp = aiHttpRequest(url, "POST", hdrs, body.toString(), code);
            if (code[0] < 200 || code[0] >= 300) throw new Exception("HTTP " + code[0] + ": " + resp);
            String trim = resp.trim();
            if (trim.startsWith("{")) {
                JSONObject jo = new JSONObject(trim);
                org.json.JSONArray choices = jo.optJSONArray("choices");
                if (choices != null && choices.length() > 0) {
                    JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
                    if (msg != null) {
                        String content = msg.optString("content", "").trim();
                        if (!content.isEmpty()) return content;
                    }
                }
            } else if (!trim.isEmpty()) {
                return trim;
            }
            throw new Exception("OpenAI 兼容接口返回空内容");
        }
    }

    private AiCallResult callAiWithFailover(List<AiPoolConfig> pools, String preferredPoolId, String preferredModel, List<AiChatMessage> history, String sysPrompt, double temp, int maxTokens, boolean useStream, AiStreamCallback streamCb) {

        AiCallResult res = new AiCallResult();
        StringBuilder trace = new StringBuilder();

        // 排序池：先当前选中池，再主池，再其余备用池
        List<AiPoolConfig> ordered = new ArrayList<>();
        for (AiPoolConfig p : pools) if (p.id.equals(preferredPoolId)) ordered.add(p);
        for (AiPoolConfig p : pools) if (p.isPrimary && !ordered.contains(p)) ordered.add(p);
        for (AiPoolConfig p : pools) if (!ordered.contains(p)) ordered.add(p);

        for (AiPoolConfig pool : ordered) {
            for (AiEndpointConfig ep : pool.endpoints) {
                if (!ep.enabled || ep.baseUrl == null || ep.baseUrl.trim().isEmpty()) continue;

                // 决定模型尝试顺序：若新 API 支持当前选定模型则优先用它，否则自动换该 API 的其他可用模型
                List<String> modelOrder = new ArrayList<>();
                if (preferredModel != null && !preferredModel.isEmpty() && ep.models.contains(preferredModel)) {
                    modelOrder.add(preferredModel);
                }
                for (String m : ep.models) {
                    if (m != null && !m.trim().isEmpty() && !modelOrder.contains(m.trim())) modelOrder.add(m.trim());
                }
                if (modelOrder.isEmpty()) {
                    if (preferredModel != null && !preferredModel.isEmpty()) modelOrder.add(preferredModel);
                    else modelOrder.add("openai");
                }

                // 决定 Key 尝试顺序（无 Key 时允许匿名测试端点尝试 1 次）
                List<String> keyOrder = new ArrayList<>();
                for (String k : ep.keys) if (k != null && !k.trim().isEmpty()) keyOrder.add(k.trim());
                if (keyOrder.isEmpty()) keyOrder.add("");

                for (int ki = 0; ki < keyOrder.size(); ki++) {
                    String key = keyOrder.get(ki);
                    boolean keyHardFailed = false;
                    for (int mi = 0; mi < modelOrder.size(); mi++) {
                        String tryModel = modelOrder.get(mi);
                        try {
                            String reply = callSingleAiEndpoint(ep, key, tryModel, history, sysPrompt, temp, maxTokens, useStream, streamCb);
                            res.ok = true;
                            res.reply = reply;
                            res.usedInfo = pool.name + " / " + ep.name + " [" + ep.protocol + "] / " + tryModel + (keyOrder.size() > 1 ? " (Key#" + (ki + 1) + ")" : "");
                            res.trace = trace.toString().trim();
                            return res;
                        } catch (Exception e) {
                            String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                            if (errMsg.length() > 90) errMsg = errMsg.substring(0, 90) + "...";
                            trace.append("× ").append(pool.name).append("/").append(ep.name).append("(").append(tryModel).append(", Key#").append(ki + 1).append("): ").append(errMsg).append("\n");
                            // 401/402/403/429 属于 Key 鉴权或额度上限问题，立即切下一个 Key
                            if (errMsg.contains("HTTP 401") || errMsg.contains("HTTP 402") || errMsg.contains("HTTP 403") || errMsg.contains("HTTP 429")) {
                                keyHardFailed = true;
                                break;
                            }
                        }
                    }
                    if (!keyHardFailed) break;
                }
            }
        }
        res.ok = false;
        res.error = L("所有主池与备用池的 API/Key 均调用失败，请检查网络或配置。");
        res.trace = trace.toString().trim();
        return res;
    }

    private View buildAiChatContent() {
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#111622"));
        final Handler uiHandler = new Handler(Looper.getMainLooper());

        final List<AiPoolConfig> pools = loadAiPools();
        final List<AiChatMessage> messages = new ArrayList<>();
        final String[] activePoolId = { prefs.getString(PREF_AI_ACTIVE_POOL, pools.get(0).id) };
        final String[] activeModel = { prefs.getString(PREF_AI_ACTIVE_MODEL, "default") };
        final boolean[] isSending = {false};

        // 顶部工具条（选池 / 选模型 / 池管理 / 对话参数 / 清空）
        HorizontalScrollView topScroll = new HorizontalScrollView(getContext());
        topScroll.setHorizontalScrollBarEnabled(false);
        topScroll.setTag("layout_toolbar");
        LinearLayout topBar = new LinearLayout(getContext());
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding((int)(8*density), (int)(6*density), (int)(8*density), (int)(6*density));
        topBar.setBackgroundColor(Color.parseColor("#1A2233"));

        final Button btnSelectPool = createButton(L("🗂️ 主池"), "#0284C7");
        final Button btnSelectModel = createButton(L("🧠 模型"), "#4F46E5");
        Button btnManagePools = createButton(L("⚙️ API池/Key管理"), "#0F766E");
        Button btnChatParams = createButton(L("🎛️ 参数"), "#334155");
        Button btnClearChat = createButton(L("🧹 清空"), "#7F1D1D");
        LinearLayout.LayoutParams tbp = new LinearLayout.LayoutParams(-2, -2);
        tbp.setMargins(0, 0, (int)(5*density), 0);
        topBar.addView(btnSelectPool, tbp);
        topBar.addView(btnSelectModel, tbp);
        topBar.addView(btnManagePools, tbp);
        topBar.addView(btnChatParams, tbp);
        topBar.addView(btnClearChat, tbp);
        topScroll.addView(topBar);
        root.addView(topScroll);

        final TextView tvStatusStrip = new TextView(getContext());
        applyGlobalFontSettings(tvStatusStrip, 0.78f, false);
        tvStatusStrip.setTextColor(Color.parseColor("#94A3B8"));
        tvStatusStrip.setBackgroundColor(Color.parseColor("#161D2C"));
        tvStatusStrip.setPadding((int)(10*density), (int)(4*density), (int)(10*density), (int)(4*density));
        root.addView(tvStatusStrip);

        final Runnable refreshHeaderUI = () -> {
            AiPoolConfig curPool = pools.isEmpty() ? null : pools.get(0);
            for (AiPoolConfig p : pools) if (p.id.equals(activePoolId[0])) { curPool = p; break; }
            if (curPool != null) {
                activePoolId[0] = curPool.id;
                btnSelectPool.setText("🗂️ " + curPool.name);
            }
            btnSelectModel.setText("🧠 " + activeModel[0]);
            int enabledEps = 0, totalKeys = 0;
            for (AiPoolConfig p : pools) {
                for (AiEndpointConfig ep : p.endpoints) {
                    if (ep.enabled) { enabledEps++; totalKeys += Math.max(1, ep.keys.size()); }
                }
            }
            tvStatusStrip.setText(L("已启用 ") + pools.size() + L(" 个池 | ") + enabledEps + L(" 个节点 | ") + totalKeys + L(" 组Key | 限流/报错自动切Key与备用池"));
        };
        refreshHeaderUI.run();

        // 对话消息列表
        final ScrollView chatScroll = new ScrollView(getContext());
        chatScroll.setTag("layout_preview");
        final LinearLayout chatBox = new LinearLayout(getContext());
        chatBox.setOrientation(LinearLayout.VERTICAL);
        chatBox.setPadding((int)(10*density), (int)(8*density), (int)(10*density), (int)(12*density));
        chatScroll.addView(chatBox, new FrameLayout.LayoutParams(-1, -2));
        root.addView(chatScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        final Runnable renderMessages = () -> {
            chatBox.removeAllViews();
            if (messages.isEmpty()) {
                TextView welcome = new TextView(getContext());
                welcome.setText(L("🤖 欢迎使用 AI 对话工坊！\n• 已默认预置免费测试池 (Pollinations OpenAI 协议)，直接发消息即可测试。\n• 点击【⚙️ API池/Key管理】可添加 GPT / Gemini / Claude / DeepSeek / Ollama / Cohere / Azure 等任意多协议节点，支持多 Key、拉取模型列表与多备用池自动故障转移。"));
                applyGlobalFontSettings(welcome, 0.88f, false);
                welcome.setTextColor(Color.parseColor("#CBD5E1"));
                welcome.setPadding((int)(12*density), (int)(12*density), (int)(12*density), (int)(12*density));
                GradientDrawable wbg = new GradientDrawable();
                wbg.setColor(Color.parseColor("#1A2334")); wbg.setCornerRadius(10f*density); wbg.setStroke(1, Color.parseColor("#334155"));
                welcome.setBackground(wbg);
                chatBox.addView(welcome);
                return;
            }
            for (final AiChatMessage msg : messages) {
                boolean isUser = "user".equals(msg.role);
                LinearLayout row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.VERTICAL);
                row.setGravity(isUser ? Gravity.END : Gravity.START);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
                rlp.setMargins(0, 0, 0, (int)(8*density));

                LinearLayout bubble = new LinearLayout(getContext());
                bubble.setOrientation(LinearLayout.VERTICAL);
                bubble.setPadding((int)(11*density), (int)(8*density), (int)(11*density), (int)(8*density));
                GradientDrawable bbg = new GradientDrawable();
                bbg.setColor(Color.parseColor(isUser ? "#1E3A8A" : "#1C2434"));
                bbg.setCornerRadius(10f*density);
                bbg.setStroke(1, Color.parseColor(isUser ? "#3B82F6" : "#334155"));
                bubble.setBackground(bbg);

                TextView tvBody = new TextView(getContext());
                tvBody.setText(msg.content);
                applyGlobalFontSettings(tvBody, 0.92f, false);
                tvBody.setTextColor(Color.parseColor("#F8FAFC"));
                tvBody.setTextIsSelectable(true);
                bubble.addView(tvBody);

                if (msg.meta != null && !msg.meta.isEmpty()) {
                    TextView tvMeta = new TextView(getContext());
                    tvMeta.setText(msg.meta);
                    applyGlobalFontSettings(tvMeta, 0.72f, false);
                    tvMeta.setTextColor(Color.parseColor("#38BDF8"));
                    tvMeta.setPadding(0, (int)(4*density), 0, 0);
                    bubble.addView(tvMeta);
                }

                if (!isUser) {
                    TextView btnCopy = new TextView(getContext());
                    btnCopy.setText(L("📋 复制回复"));
                    applyGlobalFontSettings(btnCopy, 0.74f, true);
                    btnCopy.setTextColor(Color.parseColor("#94A3B8"));
                    btnCopy.setPadding(0, (int)(4*density), 0, 0);
                    btnCopy.setOnClickListener(cv -> {
                        android.content.ClipboardManager cm = (android.content.ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("AiReply", msg.content));
                        Toast.makeText(getContext(), L("✅ 已复制回复内容"), Toast.LENGTH_SHORT).show();
                    });
                    bubble.addView(btnCopy);
                }
                row.addView(bubble, new LinearLayout.LayoutParams(-2, -2));
                chatBox.addView(row, rlp);
            }
            chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
        };
        renderMessages.run();

        // 快捷提示词条
        HorizontalScrollView chipScroll = new HorizontalScrollView(getContext());
        chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chipBar = new LinearLayout(getContext());
        chipBar.setOrientation(LinearLayout.HORIZONTAL);
        chipBar.setPadding((int)(8*density), (int)(3*density), (int)(8*density), (int)(3*density));
        final EditText inputBox = createInput(L("输入问题（如：帮我写一个带浮空的 HitDef）..."), "");
        inputBox.setMaxLines(4);

        String[] quickPrompts = {
            L("测试连通性"),
            L("帮我写一个带击飞的HitDef"),
            L("解释Ikemen GO的ZSS语法"),
            L("排查CNS状态机报错")
        };
        LinearLayout.LayoutParams qcp = new LinearLayout.LayoutParams(-2, -2);
        qcp.setMargins(0, 0, (int)(5*density), 0);
        for (final String qp : quickPrompts) {
            Button qb = createButton(qp, "#232C3F");
            qb.setPadding((int)(8*density), (int)(3*density), (int)(8*density), (int)(3*density));
            qb.setOnClickListener(v -> inputBox.setText(qp));
            chipBar.addView(qb, qcp);
        }
        chipScroll.addView(chipBar);
        root.addView(chipScroll);

        // 底部输入栏
        LinearLayout inputBar = new LinearLayout(getContext());
        inputBar.setOrientation(LinearLayout.HORIZONTAL);
        inputBar.setGravity(Gravity.CENTER_VERTICAL);
        inputBar.setPadding((int)(8*density), (int)(5*density), (int)(8*density), (int)(8*density));
        inputBar.setBackgroundColor(Color.parseColor("#161D2C"));
        final Button btnSend = createButton(L("🚀 发送"), "#16A34A");
        btnSend.setPadding((int)(14*density), (int)(8*density), (int)(14*density), (int)(8*density));
        inputBar.addView(inputBox, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams sbp = new LinearLayout.LayoutParams(-2, -2); sbp.setMargins((int)(6*density), 0, 0, 0);
        inputBar.addView(btnSend, sbp);
        root.addView(inputBar);

        // 发送逻辑（带多 Key / 多池 / 模型自动回退）
        btnSend.setOnClickListener(v -> {
            if (isSending[0]) return;
            final String q = inputBox.getText().toString().trim();
            if (q.isEmpty()) return;
            inputBox.setText("");
            messages.add(new AiChatMessage("user", q, ""));
            renderMessages.run();
            isSending[0] = true;
            btnSend.setText(L("⏳ 思考中"));
            btnSend.setBackgroundColor(Color.parseColor("#475569"));

            final int ctxTurns = Math.max(1, prefs.getInt(PREF_AI_CTX_TURNS, 8));
            final List<AiChatMessage> ctxSlice = new ArrayList<>();
            int startIdx = Math.max(0, messages.size() - ctxTurns * 2);
            for (int i = startIdx; i < messages.size(); i++) ctxSlice.add(messages.get(i));

            final String sysPrompt = prefs.getString(PREF_AI_SYS_PROMPT, L("你是专业的 MUGEN 与 Ikemen GO 开发助手，请使用清晰简洁的中文回答。"));
            final double temp = prefs.getFloat(PREF_AI_TEMP, 0.7f);
            final int maxTok = prefs.getInt(PREF_AI_MAX_TOKENS, 2048);
            final boolean useStream = prefs.getBoolean(PREF_AI_STREAM, true);

            final AiChatMessage streamingMsg = useStream ? new AiChatMessage("assistant", L("⏳ 正在连接流式输出..."), "") : null;
            if (streamingMsg != null) {
                messages.add(streamingMsg);
                renderMessages.run();
            }
            final StringBuilder liveBuf = new StringBuilder();
            final long[] lastUiTick = {0};

            new Thread(() -> {
                AiStreamCallback cb = useStream ? delta -> {
                    liveBuf.append(delta);
                    long now = System.currentTimeMillis();
                    if (now - lastUiTick[0] >= 80) {
                        lastUiTick[0] = now;
                        final String snap = liveBuf.toString();
                        uiHandler.post(() -> {
                            if (streamingMsg != null) {
                                streamingMsg.content = snap;
                                renderMessages.run();
                            }
                        });
                    }
                } : null;

                final AiCallResult res = callAiWithFailover(pools, activePoolId[0], activeModel[0], ctxSlice, sysPrompt, temp, maxTok, useStream, cb);
                uiHandler.post(() -> {
                    isSending[0] = false;
                    btnSend.setText(L("🚀 发送"));
                    btnSend.setBackgroundColor(Color.parseColor("#16A34A"));
                    String finalContent = res.ok
                        ? res.reply
                        : ("❌ " + res.error + (res.trace != null && !res.trace.isEmpty() ? "\n\n" + res.trace : ""));
                    String finalMeta = res.ok
                        ? ("⚡ " + res.usedInfo + (useStream ? " [Stream]" : "") + (res.trace != null && !res.trace.isEmpty() ? "\n↪️ " + L("故障自动切换记录:\n") + res.trace : ""))
                        : L("全部通道均失败");
                    if (streamingMsg != null) {
                        streamingMsg.content = finalContent;
                        streamingMsg.meta = finalMeta;
                    } else {
                        messages.add(new AiChatMessage("assistant", finalContent, finalMeta));
                    }
                    renderMessages.run();
                });
            }).start();
        });

        btnClearChat.setOnClickListener(v -> {
            messages.clear();
            renderMessages.run();
            Toast.makeText(getContext(), L("🧹 已清空当前对话"), Toast.LENGTH_SHORT).show();
        });

        // 切换当前首选池
        btnSelectPool.setOnClickListener(v -> {
            final Dialog d = new Dialog(getContext());
            if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setPadding((int)(14*density), (int)(14*density), (int)(14*density), (int)(14*density));
            GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#F5171F2E")); bg.setCornerRadius(12f*density); bg.setStroke(2, Color.parseColor("#38BDF8")); box.setBackground(bg);
            box.addView(createSubTitle(L("🗂️ 选择优先使用的 API 池:")));
            for (final AiPoolConfig p : pools) {
                Button pb = createButton((p.id.equals(activePoolId[0]) ? "✅ " : "") + p.name + " (" + p.endpoints.size() + " API)", p.id.equals(activePoolId[0]) ? "#0284C7" : "#243044");
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, (int)(5*density), 0, 0);
                pb.setOnClickListener(pv -> {
                    activePoolId[0] = p.id;
                    prefs.edit().putString(PREF_AI_ACTIVE_POOL, p.id).apply();
                    for (AiEndpointConfig ep : p.endpoints) {
                        if (ep.enabled && !ep.models.isEmpty()) {
                            if (!ep.models.contains(activeModel[0])) {
                                activeModel[0] = ep.models.get(0);
                                prefs.edit().putString(PREF_AI_ACTIVE_MODEL, activeModel[0]).apply();
                            }
                            break;
                        }
                    }
                    refreshHeaderUI.run();
                    d.dismiss();
                });
                box.addView(pb, lp);
            }
            d.setContentView(box, new ViewGroup.LayoutParams((int)(300*density), -2));
            d.show();
        });

        // 切换首选模型（聚合所有已启用池的模型 + 支持手动输入）
        btnSelectModel.setOnClickListener(v -> {
            final Dialog d = new Dialog(getContext());
            if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            ScrollView sv = new ScrollView(getContext());
            LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setPadding((int)(14*density), (int)(14*density), (int)(14*density), (int)(14*density));
            GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#F5171F2E")); bg.setCornerRadius(12f*density); bg.setStroke(2, Color.parseColor("#6366F1")); box.setBackground(bg);
            box.addView(createSubTitle(L("🧠 选择首选模型 (不可用时自动换池内其他模型):")));

            final EditText customIn = createInput(L("手动输入任意模型名..."), activeModel[0]);
            Button bUseCustom = createButton(L("✔️ 使用该模型"), "#4F46E5");
            bUseCustom.setOnClickListener(cv -> {
                String cm = customIn.getText().toString().trim();
                if (!cm.isEmpty()) {
                    activeModel[0] = cm;
                    prefs.edit().putString(PREF_AI_ACTIVE_MODEL, cm).apply();
                    refreshHeaderUI.run();
                }
                d.dismiss();
            });
            box.addView(customIn);
            LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(-1, -2); up.setMargins(0, (int)(5*density), 0, (int)(8*density));
            box.addView(bUseCustom, up);

            java.util.LinkedHashSet<String> allModels = new java.util.LinkedHashSet<>();
            for (AiPoolConfig p : pools) {
                for (AiEndpointConfig ep : p.endpoints) {
                    if (ep.enabled) allModels.addAll(ep.models);
                }
            }
            for (final String m : allModels) {
                Button mb = createButton((m.equals(activeModel[0]) ? "✅ " : "• ") + m, m.equals(activeModel[0]) ? "#4F46E5" : "#243044");
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, (int)(4*density), 0, 0);
                mb.setOnClickListener(mv -> {
                    activeModel[0] = m;
                    prefs.edit().putString(PREF_AI_ACTIVE_MODEL, m).apply();
                    refreshHeaderUI.run();
                    d.dismiss();
                });
                box.addView(mb, lp);
            }
            sv.addView(box);
            d.setContentView(sv, new ViewGroup.LayoutParams((int)(310*density), -2));
            d.show();
        });

        // 对话参数设置弹窗 (System Prompt / Temperature / Max Tokens / 上下文轮数)
        btnChatParams.setOnClickListener(v -> {
            final Dialog d = new Dialog(getContext());
            if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            ScrollView sv = new ScrollView(getContext());
            LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setPadding((int)(14*density), (int)(14*density), (int)(14*density), (int)(14*density));
            GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#F5171F2E")); bg.setCornerRadius(12f*density); bg.setStroke(2, Color.parseColor("#38BDF8")); box.setBackground(bg);
            box.addView(createSubTitle(L("🎛️ AI 对话参数设置")));

            final EditText eSys = createInput(L("系统提示词 (System Prompt)"), prefs.getString(PREF_AI_SYS_PROMPT, L("你是专业的 MUGEN 与 Ikemen GO 开发助手，请使用清晰简洁的中文回答。")));
            eSys.setMinLines(3);
            final EditText eTemp = createInput(L("温度 Temperature (0.0~2.0)"), String.valueOf(prefs.getFloat(PREF_AI_TEMP, 0.7f)));
            final EditText eMaxT = createInput(L("最大输出 Token (如 2048)"), String.valueOf(prefs.getInt(PREF_AI_MAX_TOKENS, 2048)));
            final EditText eCtx = createInput(L("上下文记忆轮数 (如 8)"), String.valueOf(prefs.getInt(PREF_AI_CTX_TURNS, 8)));
            final boolean[] streamFlag = { prefs.getBoolean(PREF_AI_STREAM, true) };
            final Button bStreamToggle = createButton(streamFlag[0] ? L("⚡ 流式输出 (SSE打字机): 开启") : L("⚡ 流式输出 (SSE打字机): 关闭"), streamFlag[0] ? "#0284C7" : "#475569");
            bStreamToggle.setOnClickListener(stv -> {
                streamFlag[0] = !streamFlag[0];
                bStreamToggle.setText(streamFlag[0] ? L("⚡ 流式输出 (SSE打字机): 开启") : L("⚡ 流式输出 (SSE打字机): 关闭"));
                bStreamToggle.setBackgroundColor(Color.parseColor(streamFlag[0] ? "#0284C7" : "#475569"));
            });

            box.addView(createSubTitle(L("系统提示词:"))); box.addView(eSys);
            box.addView(createSubTitle(L("温度 (Temperature):"))); box.addView(eTemp);
            box.addView(createSubTitle(L("最大输出长度 (Max Tokens):"))); box.addView(eMaxT);
            box.addView(createSubTitle(L("上下文轮数:"))); box.addView(eCtx);
            LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(-1, -2); stp.setMargins(0, (int)(8*density), 0, 0);
            box.addView(bStreamToggle, stp);

            Button bSave = createButton(L("💾 保存参数"), "#16A34A");
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2); sp.setMargins(0, (int)(10*density), 0, 0);
            bSave.setOnClickListener(sv2 -> {
                float t = 0.7f; int mt = 2048; int ct = 8;
                try { t = Float.parseFloat(eTemp.getText().toString().trim()); } catch (Exception ignored) {}
                try { mt = Integer.parseInt(eMaxT.getText().toString().trim()); } catch (Exception ignored) {}
                try { ct = Integer.parseInt(eCtx.getText().toString().trim()); } catch (Exception ignored) {}
                prefs.edit()
                     .putString(PREF_AI_SYS_PROMPT, eSys.getText().toString().trim())
                     .putFloat(PREF_AI_TEMP, Math.max(0f, Math.min(2f, t)))
                     .putInt(PREF_AI_MAX_TOKENS, Math.max(64, mt))
                     .putInt(PREF_AI_CTX_TURNS, Math.max(1, Math.min(50, ct)))
                     .putBoolean(PREF_AI_STREAM, streamFlag[0])
                     .apply();
                Toast.makeText(getContext(), L("✅ 对话参数已保存"), Toast.LENGTH_SHORT).show();
                d.dismiss();
            });
            box.addView(bSave, sp);
            sv.addView(box);
            int sw = getContext().getResources().getDisplayMetrics().widthPixels;
            d.setContentView(sv, new ViewGroup.LayoutParams((int)(sw * 0.84f), -2));
            d.show();
        });

        // 完整的 API 池 / 备用池 / 多Key / 多协议 / 拉取模型管理器
        final Runnable[] openPoolManagerRef = {null};
        openPoolManagerRef[0] = () -> {
            final Dialog md = new Dialog(getContext());
            if (md.getWindow() != null) md.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            ScrollView msv = new ScrollView(getContext());
            final LinearLayout mbox = new LinearLayout(getContext()); mbox.setOrientation(LinearLayout.VERTICAL);
            mbox.setPadding((int)(14*density), (int)(14*density), (int)(14*density), (int)(14*density));
            GradientDrawable mbg = new GradientDrawable(); mbg.setColor(Color.parseColor("#F8141A24")); mbg.setCornerRadius(12f*density); mbg.setStroke(2, Color.parseColor("#0F766E")); mbox.setBackground(mbg);

            mbox.addView(createSubTitle(L("⚙️ API 多池与多 Key 管理中心 (支持 OpenAI/Gemini/Claude/Ollama/Cohere/Azure)")));

            LinearLayout topAct = new LinearLayout(getContext()); topAct.setOrientation(LinearLayout.HORIZONTAL);
            Button bAddPool = createButton(L("➕ 新建备用池"), "#0284C7");
            Button bResetDef = createButton(L("🔄 恢复免费测试默认"), "#475569");
            LinearLayout.LayoutParams tap = new LinearLayout.LayoutParams(0, -2, 1f); tap.setMargins((int)(2*density), (int)(4*density), (int)(2*density), (int)(8*density));
            bAddPool.setOnClickListener(v -> {
                AiPoolConfig np = new AiPoolConfig();
                np.id = "pool_" + System.currentTimeMillis();
                np.name = L("备用池 ") + pools.size();
                np.isPrimary = false;
                pools.add(np);
                saveAiPools(pools);
                refreshHeaderUI.run();
                md.dismiss();
                openPoolManagerRef[0].run();
            });
            bResetDef.setOnClickListener(v -> {
                prefs.edit().remove(PREF_AI_POOLS).apply();
                pools.clear();
                pools.addAll(loadAiPools());
                activePoolId[0] = pools.get(0).id;
                activeModel[0] = "default";
                refreshHeaderUI.run();
                md.dismiss();
                openPoolManagerRef[0].run();
            });
            topAct.addView(bAddPool, tap); topAct.addView(bResetDef, tap);
            mbox.addView(topAct);

            for (int pi = 0; pi < pools.size(); pi++) {
                final int poolIdx = pi;
                final AiPoolConfig pool = pools.get(pi);
                LinearLayout pCard = new LinearLayout(getContext()); pCard.setOrientation(LinearLayout.VERTICAL);
                pCard.setPadding((int)(10*density), (int)(8*density), (int)(10*density), (int)(8*density));
                GradientDrawable pbg = new GradientDrawable(); pbg.setColor(Color.parseColor("#1B2333")); pbg.setCornerRadius(8f*density);
                pbg.setStroke(1, Color.parseColor(pool.isPrimary ? "#38BDF8" : "#334155"));
                pCard.setBackground(pbg);
                LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, -2); plp.setMargins(0, 0, 0, (int)(8*density));

                LinearLayout pHead = new LinearLayout(getContext()); pHead.setOrientation(LinearLayout.HORIZONTAL); pHead.setGravity(Gravity.CENTER_VERTICAL);
                TextView pTitle = new TextView(getContext());
                pTitle.setText((pool.isPrimary ? "⭐ " : "📦 ") + pool.name);
                applyGlobalFontSettings(pTitle, 0.94f, true); pTitle.setTextColor(Color.WHITE);
                pHead.addView(pTitle, new LinearLayout.LayoutParams(0, -2, 1f));

                Button bAddEp = createButton(L("+ API节点"), "#16A34A");
                bAddEp.setPadding((int)(7*density), (int)(3*density), (int)(7*density), (int)(3*density));
                Button bDelPool = createButton("🗑️", "#991B1B");
                bDelPool.setPadding((int)(7*density), (int)(3*density), (int)(7*density), (int)(3*density));
                LinearLayout.LayoutParams hbp = new LinearLayout.LayoutParams(-2, -2); hbp.setMargins((int)(4*density), 0, 0, 0);
                pHead.addView(bAddEp, hbp);
                if (pools.size() > 1) pHead.addView(bDelPool, hbp);
                pCard.addView(pHead);

                bDelPool.setOnClickListener(dv -> {
                    pools.remove(poolIdx);
                    saveAiPools(pools);
                    refreshHeaderUI.run();
                    md.dismiss();
                    openPoolManagerRef[0].run();
                });

                // 节点编辑器打开器（新建或修改已有 API 节点）
                final Runnable[] openEpEditorFactory = {null};
                final AiEndpointConfig[] editingEpRef = {null};
                openEpEditorFactory[0] = () -> {
                    final boolean isNew = (editingEpRef[0] == null);
                    final AiEndpointConfig target = isNew ? new AiEndpointConfig() : editingEpRef[0];
                    if (isNew) {
                        target.id = "ep_" + System.currentTimeMillis();
                        target.name = "Custom API";
                        target.protocol = "openai";
                        target.baseUrl = "https://" + "api.openai.com/v1";
                        target.enabled = true;
                    }
                    final Dialog ed = new Dialog(getContext());
                    if (ed.getWindow() != null) ed.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                    ScrollView esv = new ScrollView(getContext());
                    LinearLayout ebox = new LinearLayout(getContext()); ebox.setOrientation(LinearLayout.VERTICAL);
                    ebox.setPadding((int)(14*density), (int)(14*density), (int)(14*density), (int)(14*density));
                    GradientDrawable ebg = new GradientDrawable(); ebg.setColor(Color.parseColor("#F8141A24")); ebg.setCornerRadius(12f*density); ebg.setStroke(2, Color.parseColor("#6366F1")); ebox.setBackground(ebg);
                    ebox.addView(createSubTitle(isNew ? L("➕ 新增 API 节点") : L("✏️ 编辑 API 节点")));

                    final EditText eName = createInput(L("节点名称（如 DeepSeek / Gemini）"), target.name);
                    final String[] protos = {"openai", "gemini", "claude", "ollama", "cohere", "azure"};
                    final String[] protoSel = { target.protocol };
                    HorizontalScrollView psc = new HorizontalScrollView(getContext()); psc.setHorizontalScrollBarEnabled(false);
                    LinearLayout prow = new LinearLayout(getContext()); prow.setOrientation(LinearLayout.HORIZONTAL);
                    final Button[] pbs = new Button[protos.length];
                    LinearLayout.LayoutParams pbp = new LinearLayout.LayoutParams(-2, -2); pbp.setMargins(0, (int)(3*density), (int)(4*density), (int)(5*density));
                    for (int k = 0; k < protos.length; k++) {
                        final int ki = k;
                        pbs[k] = createButton(protos[k], protos[k].equalsIgnoreCase(protoSel[0]) ? "#4F46E5" : "#263147");
                        pbs[k].setPadding((int)(8*density), (int)(4*density), (int)(8*density), (int)(4*density));
                        pbs[k].setOnClickListener(pv -> {
                            protoSel[0] = protos[ki];
                            for (int x = 0; x < pbs.length; x++) pbs[x].setBackgroundColor(Color.parseColor(x == ki ? "#4F46E5" : "#263147"));
                        });
                        prow.addView(pbs[k], pbp);
                    }
                    psc.addView(prow);

                    final EditText eUrl = createInput(L("Base URL（如 ") + "https://" + "api.openai.com/v1）", target.baseUrl);
                    StringBuilder ksInit = new StringBuilder();
                    for (String k : target.keys) { if (ksInit.length() > 0) ksInit.append("\n"); ksInit.append(k); }
                    final EditText eKeys = createInput(L("多个 API Key（每行一个，额度用完自动切下一个）"), ksInit.toString());
                    eKeys.setMinLines(2);

                    StringBuilder msInit = new StringBuilder();
                    for (String m : target.models) { if (msInit.length() > 0) msInit.append(", "); msInit.append(m); }
                    final EditText eModels = createInput(L("模型列表（逗号分隔，可点下方按钮自动拉取）"), msInit.toString());
                    eModels.setMinLines(2);

                    final boolean[] enFlag = { target.enabled };
                    final Button bToggleEn = createButton(enFlag[0] ? L("✅ 状态: 已启用") : L("⏸️ 状态: 已停用"), enFlag[0] ? "#16A34A" : "#475569");
                    bToggleEn.setOnClickListener(ev -> {
                        enFlag[0] = !enFlag[0];
                        bToggleEn.setText(enFlag[0] ? L("✅ 状态: 已启用") : L("⏸️ 状态: 已停用"));
                        bToggleEn.setBackgroundColor(Color.parseColor(enFlag[0] ? "#16A34A" : "#475569"));
                    });

                    Button bFetchModels = createButton(L("🔄 从该 API 自动拉取可用模型列表"), "#0284C7");
                    bFetchModels.setOnClickListener(fv -> {
                        AiEndpointConfig tmp = new AiEndpointConfig();
                        tmp.protocol = protoSel[0];
                        tmp.baseUrl = eUrl.getText().toString().trim();
                        String firstKey = "";
                        for (String kl : eKeys.getText().toString().split("[\n,]")) {
                            if (!kl.trim().isEmpty()) { firstKey = kl.trim(); break; }
                        }
                        final String fk = firstKey;
                        Toast.makeText(getContext(), L("⏳ 正在向接口拉取模型列表..."), Toast.LENGTH_SHORT).show();
                        new Thread(() -> {
                            try {
                                final List<String> fetched = fetchModelsForEndpoint(tmp, fk);
                                uiHandler.post(() -> {
                                    if (fetched.isEmpty()) {
                                        Toast.makeText(getContext(), L("⚠️ 接口返回了空模型列表"), Toast.LENGTH_SHORT).show();
                                    } else {
                                        StringBuilder msb = new StringBuilder();
                                        int cap = Math.min(fetched.size(), 40);
                                        for (int i = 0; i < cap; i++) {
                                            if (i > 0) msb.append(", ");
                                            msb.append(fetched.get(i));
                                        }
                                        eModels.setText(msb.toString());
                                        Toast.makeText(getContext(), L("✅ 成功拉取 ") + fetched.size() + L(" 个模型！"), Toast.LENGTH_SHORT).show();
                                    }
                                });
                            } catch (Exception ex) {
                                uiHandler.post(() -> Toast.makeText(getContext(), L("❌ 拉取失败: ") + ex.getMessage(), Toast.LENGTH_LONG).show());
                            }
                        }).start();
                    });

                    ebox.addView(createSubTitle(L("名称:"))); ebox.addView(eName);
                    ebox.addView(createSubTitle(L("协议格式:"))); ebox.addView(psc);
                    ebox.addView(createSubTitle(L("API 地址 (Base URL):"))); ebox.addView(eUrl);
                    ebox.addView(createSubTitle(L("API Keys (支持多个，每行一个):"))); ebox.addView(eKeys);
                    ebox.addView(createSubTitle(L("支持模型列表:"))); ebox.addView(eModels);
                    LinearLayout.LayoutParams bfp = new LinearLayout.LayoutParams(-1, -2); bfp.setMargins(0, (int)(6*density), 0, 0);
                    ebox.addView(bFetchModels, bfp);
                    ebox.addView(bToggleEn, bfp);

                    LinearLayout actR = new LinearLayout(getContext()); actR.setOrientation(LinearLayout.HORIZONTAL);
                    Button bSaveEp = createButton(L("💾 保存节点"), "#16A34A");
                    Button bCancelEp = createButton(L("取消"), "#475569");
                    LinearLayout.LayoutParams arp = new LinearLayout.LayoutParams(0, -2, 1f); arp.setMargins((int)(3*density), (int)(10*density), (int)(3*density), 0);
                    bSaveEp.setOnClickListener(sv2 -> {
                        target.name = eName.getText().toString().trim();
                        if (target.name.isEmpty()) target.name = protoSel[0].toUpperCase();
                        target.protocol = protoSel[0];
                        target.baseUrl = eUrl.getText().toString().trim();
                        target.enabled = enFlag[0];
                        target.keys.clear();
                        for (String k : eKeys.getText().toString().split("[\n,]")) if (!k.trim().isEmpty()) target.keys.add(k.trim());
                        target.models.clear();
                        for (String m : eModels.getText().toString().split("[\n,]")) if (!m.trim().isEmpty()) target.models.add(m.trim());
                        if (isNew) pool.endpoints.add(target);
                        saveAiPools(pools);
                        refreshHeaderUI.run();
                        ed.dismiss();
                        md.dismiss();
                        openPoolManagerRef[0].run();
                    });
                    bCancelEp.setOnClickListener(cv -> ed.dismiss());
                    actR.addView(bSaveEp, arp); actR.addView(bCancelEp, arp);
                    ebox.addView(actR);

                    esv.addView(ebox);
                    int sw2 = getContext().getResources().getDisplayMetrics().widthPixels;
                    ed.setContentView(esv, new ViewGroup.LayoutParams((int)(sw2 * 0.88f), -2));
                    ed.show();
                };

                bAddEp.setOnClickListener(av -> { editingEpRef[0] = null; openEpEditorFactory[0].run(); });

                for (int ei = 0; ei < pool.endpoints.size(); ei++) {
                    final int epIdx = ei;
                    final AiEndpointConfig ep = pool.endpoints.get(ei);
                    LinearLayout eRow = new LinearLayout(getContext()); eRow.setOrientation(LinearLayout.HORIZONTAL); eRow.setGravity(Gravity.CENTER_VERTICAL);
                    eRow.setPadding((int)(8*density), (int)(6*density), (int)(8*density), (int)(6*density));
                    GradientDrawable ebg = new GradientDrawable(); ebg.setColor(Color.parseColor("#131A26")); ebg.setCornerRadius(6f*density); ebg.setStroke(1, Color.parseColor(ep.enabled ? "#334155" : "#451A1A"));
                    eRow.setBackground(ebg);
                    LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(-1, -2); elp.setMargins(0, (int)(5*density), 0, 0);

                    TextView eTv = new TextView(getContext());
                    eTv.setText((ep.enabled ? "🟢 " : "⚪ ") + ep.name + " [" + ep.protocol + "]\nKey:" + ep.keys.size() + " | 模型:" + ep.models.size());
                    applyGlobalFontSettings(eTv, 0.8f, false); eTv.setTextColor(Color.parseColor("#CBD5E1"));
                    eRow.addView(eTv, new LinearLayout.LayoutParams(0, -2, 1f));

                    Button bEd = createButton(L("编辑"), "#334155"); bEd.setPadding((int)(7*density), (int)(3*density), (int)(7*density), (int)(3*density));
                    Button bRm = createButton("✕", "#7F1D1D"); bRm.setPadding((int)(7*density), (int)(3*density), (int)(7*density), (int)(3*density));
                    bEd.setOnClickListener(ev -> { editingEpRef[0] = ep; openEpEditorFactory[0].run(); });
                    bRm.setOnClickListener(rv -> {
                        pool.endpoints.remove(epIdx);
                        saveAiPools(pools);
                        refreshHeaderUI.run();
                        md.dismiss();
                        openPoolManagerRef[0].run();
                    });
                    eRow.addView(bEd, hbp); eRow.addView(bRm, hbp);
                    pCard.addView(eRow, elp);
                }
                mbox.addView(pCard, plp);
            }

            Button bClose = createButton(L("完成关闭"), "#475569");
            bClose.setOnClickListener(cv -> md.dismiss());
            mbox.addView(bClose, new LinearLayout.LayoutParams(-1, -2));

            msv.addView(mbox);
            int sw = getContext().getResources().getDisplayMetrics().widthPixels;
            md.setContentView(msv, new ViewGroup.LayoutParams((int)(sw * 0.90f), -2));
            md.show();
        };
        btnManagePools.setOnClickListener(v -> openPoolManagerRef[0].run());

        return root;
    }

// ================= 【机制注入】多语言补丁快捷助手 =================
    // 自动连接到 DynamicGamepadView 的翻译引擎
    private static String L(String text) {
        return DynamicGamepadView.L(text);
    }
} // 类的结尾
