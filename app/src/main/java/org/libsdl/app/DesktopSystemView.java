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

// 🔥 新增：云同游核心网络与录屏依赖
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import org.json.JSONObject;
import org.webrtc.*;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.content.Intent;
import android.app.Fragment;
import android.util.Log;

// 🔥 修复补丁：补充虚拟手柄与 WebRTC 数据流缺少的两个系统包
import android.widget.Space;
import java.nio.ByteBuffer;

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
    
    // 留给真正需要彻底清理内存时的后门
    public void forceDestroy() {
        super.dismiss();
        mSingleton = null;
    }

    public static DesktopSystemView instance;
    
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
        taskbar.setPadding((int)(4*density), 0, (int)(15*density), 0);
        taskbar.setBackgroundColor(Color.argb(taskbarAlpha, 17, 17, 17)); 
        FrameLayout.LayoutParams taskbarParams = new FrameLayout.LayoutParams(-1, dynamicTaskbarHeight);
        taskbarParams.gravity = Gravity.BOTTOM;
        rootLayer.addView(taskbar, taskbarParams);

        final LinearLayout startBtn = new LinearLayout(getContext());
        startBtn.setOrientation(LinearLayout.HORIZONTAL); startBtn.setGravity(Gravity.CENTER);
        startBtn.setPadding((int)(10*density), (int)(8*density), (int)(15*density), (int)(8*density));
        
        TextView btnIcon = new TextView(getContext()); btnIcon.setText("⊞"); btnIcon.setTextColor(Color.parseColor("#00A4EF")); btnIcon.setTextSize(20f);
        TextView btnText = new TextView(getContext()); btnText.setText(L(" 进入游戏")); applyGlobalFontSettings(btnText, 1.2f, true); 
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
        createDesktopIcon("remote_play", "🎮", L("远程同乐")); // 🔥 新增：远程同乐云游戏模块
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
                            else if (id.equals("def_scanner")) openAppWindow(L("🗂️ 自动 DEF 扫描器"), buildDefScannerContent(), null);
                            else if (id.equals("remote_play")) openAppWindow(L("🎮 远程同乐 (云同游)"), buildRemotePlayContent(), null); // 🔥 新增：打开同乐大厅
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

        final TextView btnMax = new TextView(getContext()); btnMax.setText(" □ "); applyGlobalFontSettings(btnMax, 1.0f, true); btnMax.setPadding((int)(15*density), (int)(5*density), (int)(15*density), (int)(8*density));
        final boolean[] isMaximized = {false}; final int[] savedBounds = new int[4]; 
        btnMax.setOnClickListener(v -> {
            if (isMaximized[0]) { FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(savedBounds[2], savedBounds[3]); windowFrame.setLayoutParams(lp); windowFrame.setX(savedBounds[0]); windowFrame.setY(savedBounds[1]); btnMax.setText(" □ "); isMaximized[0] = false;
            } else {
                savedBounds[0] = (int) windowFrame.getX(); savedBounds[1] = (int) windowFrame.getY(); savedBounds[2] = windowFrame.getWidth(); savedBounds[3] = windowFrame.getHeight();
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1); lp.bottomMargin = (int)(50 * density); 
                windowFrame.setLayoutParams(lp); windowFrame.setX(0); windowFrame.setY(0); windowFrame.bringToFront(); btnMax.setText(" ❐ "); isMaximized[0] = true;
            }
        }); controls.addView(btnMax);

        final LinearLayout taskBtn = new LinearLayout(getContext()); taskBtn.setTag("tb_" + windowTitle);
        TextView btnClose = new TextView(getContext()); btnClose.setText(" ✕ "); applyGlobalFontSettings(btnClose, 1.0f, true); btnClose.setPadding((int)(15*density), (int)(5*density), (int)(15*density), (int)(5*density));
        btnClose.setOnTouchListener((v, e) -> { if(e.getAction()==MotionEvent.ACTION_DOWN) v.setBackgroundColor(Color.parseColor("#E81123")); else if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL) v.setBackgroundColor(Color.TRANSPARENT); return false; });
        btnClose.setOnClickListener(v -> { if (onCloseInterceptor != null) onCloseInterceptor.run(); else { windowsLayer.removeView(windowFrame); taskbarAppsLayout.removeView(taskBtn); } });
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
        
        String rawName = windowTitle.replace(L("🎨 检视: "), "").replace(L("📦 "), "").replace(L("🎵 检视: "), "").replace(L("🎞️ GIF拆解: "), "").trim();
        final String finalShortName = rawName.length() > 8 ? rawName.substring(0, 8) + ".." : rawName;
        TextView tbText = new TextView(getContext()); tbText.setText("▤ " + finalShortName); applyGlobalFontSettings(tbText, 1.1f, false); taskBtn.addView(tbText);
        
        final boolean[] isBtnDragging = {false};
        taskBtn.setOnTouchListener(new View.OnTouchListener() {
            float startX; float initialTranslation; 
            @Override public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN: startX = event.getRawX(); initialTranslation = v.getTranslationX(); isBtnDragging[0] = false; v.setBackgroundColor(Color.parseColor("#44FFFFFF")); return false; 
                    case MotionEvent.ACTION_MOVE: 
                        float dx = event.getRawX() - startX; 
                        if (Math.abs(dx) > 10 * density) { isBtnDragging[0] = true; v.getParent().requestDisallowInterceptTouchEvent(true); v.cancelLongPress(); /* 🔥 移动时强制取消长按事件 */ }
                        if (isBtnDragging[0]) { v.setTranslationX(initialTranslation + dx); v.bringToFront(); } return true;
                    case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                        v.setBackground(tbBg);
                        if (isBtnDragging[0]) {
                            float currentCenter = v.getX() + v.getTranslationX() + v.getWidth() / 2f; int newIndex = taskbarAppsLayout.getChildCount() - 1;
                            for (int i = 0; i < taskbarAppsLayout.getChildCount(); i++) { View child = taskbarAppsLayout.getChildAt(i); if (child != v && currentCenter < child.getX() + child.getWidth() / 2f) { newIndex = i; break; } }
                            final int targetIndex = newIndex; taskbarAppsLayout.post(() -> { taskbarAppsLayout.removeView(v); v.setTranslationX(0); taskbarAppsLayout.addView(v, targetIndex); });
                            return true; // 🔥 吞噬拖拽结束的触控，防止触发 onClick
                        } return false; 
                } return false;
            }
        });

        taskBtn.setOnClickListener(v -> {
            if (isBtnDragging[0]) return; // 双重防走火
            if (windowFrame.getVisibility() == View.VISIBLE) {
                // 🔥 修复隐藏逻辑：通过 Index 精准判断它是否在最顶层
                if (windowsLayer.indexOfChild(windowFrame) == windowsLayer.getChildCount() - 1) { windowFrame.setVisibility(View.GONE); } else { windowFrame.bringToFront(); }
            } else { windowFrame.setVisibility(View.VISIBLE); windowFrame.bringToFront(); }
        });
        
        taskBtn.setOnLongClickListener(v -> { 
            if (isBtnDragging[0]) return false; // 拖动中绝不触发菜单
            showContextMenu(v, finalShortName, () -> { if (onCloseInterceptor != null) onCloseInterceptor.run(); else { windowsLayer.removeView(windowFrame); taskbarAppsLayout.removeView(taskBtn); } }); return true; 
        });
        taskbarAppsLayout.addView(taskBtn, tbParams);

        // 动态获取当前设备真实的屏幕分辨率，杜绝 getWidth() 返回 0 的 Bug
        int screenW = getContext().getResources().getDisplayMetrics().widthPixels;
        int screenH = getContext().getResources().getDisplayMetrics().heightPixels;
        
        // 永远雷打不动地保持你最喜欢的比例：宽度占屏幕的 70%，高度占 80%
        int w = (int) (screenW * 0.70f); 
        int h = (int) (screenH * 0.80f); 
        
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
    private EditText createInput(String hint, String text) { EditText et = new EditText(getContext()); et.setText(text); applyGlobalFontSettings(et, 1.0f, false); et.setHint(hint); et.setHintTextColor(Color.DKGRAY); GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#252526")); bg.setStroke(1, Color.GRAY); et.setBackground(bg); et.setPadding((int)(10*density), (int)(10*density), (int)(10*density), (int)(10*density)); return et; }
    private Button createButton(String text, String colorHex) { 
        Button btn = new Button(getContext()); btn.setText(text); btn.setAllCaps(false); 
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor(colorHex)); bg.setCornerRadius(0); bg.setStroke((int)(1*density), Color.parseColor("#44FFFFFF")); btn.setBackground(bg); 
        applyGlobalFontSettings(btn, 1.0f, false); btn.setTextColor(Color.WHITE); btn.setPadding((int)(15*density), (int)(8*density), (int)(15*density), (int)(8*density));
        btn.setOnTouchListener((v, e) -> { if (e.getAction() == MotionEvent.ACTION_DOWN) v.setAlpha(0.7f); else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) v.setAlpha(1.0f); return false; }); return btn; 
    }

    @Override public void onBackPressed() { } 

    // ======================================================================================
    // 🎨 模块 1：SFF 检视工坊
    // ======================================================================================
    private LinearLayout currentGalleryLayout = null;
    private TextView currentStatusText = null;
    private volatile boolean isAssetScannerRunning = false;

    private View buildSffExtractorContent() {
        LinearLayout root = new LinearLayout(getContext()); root.setOrientation(LinearLayout.VERTICAL); root.setPadding((int)(15*density), (int)(15*density), (int)(15*density), (int)(15*density));
        LinearLayout topBar = new LinearLayout(getContext()); topBar.setOrientation(LinearLayout.HORIZONTAL); topBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView statusText = new TextView(getContext()); statusText.setText(L(" 状态: 等待选取目录或文件...")); applyGlobalFontSettings(statusText, 1.0f, false);
        Button scanBtn = createButton(L("📂 浏览并选择 SFF 素材文件"), "#0078D7"); LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(-2, -2); btnParams.setMargins(0, 0, (int)(15*density), 0);
        topBar.addView(scanBtn, btnParams); topBar.addView(statusText); root.addView(topBar);

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

        List<Integer> groupList = new ArrayList<>();
        groupList.add(-999); 
        if (allFrames != null) {
            for (GoEngineBridge.SffFrame f : allFrames) { if (!groupList.contains(f.group)) { groupList.add(f.group); } }
        }
        
        final List<GoEngineBridge.SffFrame> currentGroupFrames = new ArrayList<>();
        final int[] currentFrameIndex = {0}; final boolean[] isPlaying = {false}; 

        final String[] currentActPath = {""};

        TextView infoText = new TextView(getContext()); infoText.setPadding((int)(5*density), (int)(4*density), (int)(5*density), (int)(2*density)); applyGlobalFontSettings(infoText, 0.85f, false); infoText.setTextColor(Color.parseColor("#0078D7")); root.addView(infoText);

        FrameLayout canvasFrame = new FrameLayout(getContext()); LinearLayout.LayoutParams canvasParams = new LinearLayout.LayoutParams(-1, 0, 1f); canvasParams.setMargins((int)(5*density), (int)(5*density), (int)(5*density), (int)(5*density)); canvasFrame.setLayoutParams(canvasParams);
        Bitmap bgBmp = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888); Canvas bgCanvas = new Canvas(bgBmp); Paint bgPaint = new Paint(); bgPaint.setColor(Color.parseColor("#181818")); bgCanvas.drawRect(0,0,10,10,bgPaint); bgCanvas.drawRect(10,10,20,20,bgPaint); bgPaint.setColor(Color.parseColor("#252526")); bgCanvas.drawRect(10,0,20,10,bgPaint); bgCanvas.drawRect(0,10,10,20,bgPaint);
        BitmapDrawable tileBg = new BitmapDrawable(getContext().getResources(), bgBmp); tileBg.setTileModeXY(android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT); 
        GradientDrawable canvasBorder = new GradientDrawable(); canvasBorder.setStroke((int)(1*density), Color.parseColor("#3F3F46")); canvasFrame.setBackground(new LayerDrawable(new android.graphics.drawable.Drawable[]{tileBg, canvasBorder}));

        final ImageView previewImg = new ImageView(getContext()); previewImg.setScaleType(ImageView.ScaleType.MATRIX); canvasFrame.addView(previewImg, new FrameLayout.LayoutParams(-1, -1)); root.addView(canvasFrame);
        final Matrix imageMatrix = new Matrix(); final Matrix savedMatrix = new Matrix(); final int[] touchMode = {0}; final PointF startPoint = new PointF(); final PointF midPoint = new PointF(); final float[] oldDist = {1f}; final boolean[] isMatrixInitialized = {false};

        canvasFrame.setOnTouchListener((v, event) -> {
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN: savedMatrix.set(imageMatrix); startPoint.set(event.getX(), event.getY()); touchMode[0] = 1; break;
                case MotionEvent.ACTION_POINTER_DOWN: oldDist[0] = (float)Math.sqrt(Math.pow(event.getX(0)-event.getX(1), 2) + Math.pow(event.getY(0)-event.getY(1), 2)); if (oldDist[0] > 10f) { savedMatrix.set(imageMatrix); midPoint.set((event.getX(0)+event.getX(1))/2, (event.getY(0)+event.getY(1))/2); touchMode[0] = 2; } break;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_POINTER_UP: touchMode[0] = 0; break;
                case MotionEvent.ACTION_MOVE:
                    if (touchMode[0] == 1) { imageMatrix.set(savedMatrix); imageMatrix.postTranslate(event.getX() - startPoint.x, event.getY() - startPoint.y); } 
                    else if (touchMode[0] == 2) { float newDist = (float)Math.sqrt(Math.pow(event.getX(0)-event.getX(1), 2) + Math.pow(event.getY(0)-event.getY(1), 2)); if (newDist > 10f) { imageMatrix.set(savedMatrix); float scale = newDist / oldDist[0]; imageMatrix.postScale(scale, scale, midPoint.x, midPoint.y); } } break;
            } previewImg.setImageMatrix(imageMatrix); return true;
        });

        LinearLayout bottomToolArea = new LinearLayout(getContext()); bottomToolArea.setOrientation(LinearLayout.VERTICAL);
        
        HorizontalScrollView controlsScroll = new HorizontalScrollView(getContext()); 
        controlsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout controls = new LinearLayout(getContext()); 
        controls.setOrientation(LinearLayout.HORIZONTAL); 
        controls.setGravity(Gravity.CENTER_VERTICAL); 
        // 压缩控制栏内边距，释放更多屏幕空间
        controls.setPadding((int)(5*density), (int)(5*density), (int)(5*density), (int)(10*density));
        
        // 【按键等宽布局设置】限制在固定宽度，超出屏幕支持横向滑动
        LinearLayout.LayoutParams uniformBtnParams = new LinearLayout.LayoutParams((int)(115*density), -2);
        uniformBtnParams.setMargins((int)(4*density), 0, (int)(4*density), 0);
        
        // 【按键加宽布局设置】专门用于控制播放的三个按键
        LinearLayout.LayoutParams wideBtnParams = new LinearLayout.LayoutParams((int)(140*density), -2);
        wideBtnParams.setMargins((int)(4*density), 0, (int)(4*density), 0);

        Button btnDefaultAct = createButton(L("🎨 内置色表"), "#4CAF50");
        Button btnAutoAct = createButton(L("🪄 自动色表"), "#9C27B0");
        Button btnManualAct = createButton(L("🎨 手动色表"), "#9C27B0");
        Button btnGroup = createButton(L("📁 动作编组"), "#1E1E1E"); // 新增：合并后的动作编组按键
        Button btnPrev = createButton(L("⏪ 上一帧"), "#3F3F46"); 
        Button btnPlay = createButton(L("▶️ 播放"), "#0078D7"); 
        Button btnNext = createButton(L("⏭️ 下一帧"), "#3F3F46"); 
        Button btnSpeed = createButton(L("⚙️ 调速"), "#3F3F46"); 
        Button btnExportNative = createButton(L("💾 原生导出"), "#3F3F46"); 
        Button btnReplace = createButton(L("🔄 图像替换"), "#4CAF50");
        
        final int[] currentDelay = {16}; 
        btnSpeed.setOnClickListener(v -> {
            final Dialog spdDialog = new Dialog(getContext()); spdDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            LinearLayout spdLayout = new LinearLayout(getContext()); spdLayout.setOrientation(LinearLayout.VERTICAL); spdLayout.setBackgroundColor(Color.parseColor("#2D2D30")); spdLayout.setPadding((int)(20*density), (int)(20*density), (int)(20*density), (int)(20*density));
            TextView title = new TextView(getContext()); title.setText(L("调整播放速度 (FPS)")); applyGlobalFontSettings(title, 1.0f, true); title.setGravity(Gravity.CENTER); spdLayout.addView(title);
            SeekBar speedBar = new SeekBar(getContext()); speedBar.setMax(59); speedBar.setProgress((1000/currentDelay[0])-1);
            speedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(SeekBar s, int p, boolean b) { currentDelay[0] = 1000 / (p + 1); title.setText(L("调整播放速度: ") + (p+1) + " FPS"); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){} });
            spdLayout.addView(speedBar, new LinearLayout.LayoutParams((int)(250*density), -2)); spdDialog.setContentView(spdLayout); spdDialog.show();
        });

        final Handler uiHandler = new Handler(Looper.getMainLooper());
        final Handler playHandler = new Handler();
        Runnable updateFrameAction = new Runnable() {
            @Override public void run() {
                if (currentGroupFrames.isEmpty()) return;
                if (currentFrameIndex[0] < 0) currentFrameIndex[0] = currentGroupFrames.size() - 1;
                if (currentFrameIndex[0] >= currentGroupFrames.size()) currentFrameIndex[0] = 0;
                final GoEngineBridge.SffFrame targetFrame = currentGroupFrames.get(currentFrameIndex[0]);
                new Thread(() -> {
                    try {
                        byte[] bmpData = Api.decodeSffFrame(sffPath, targetFrame.group, targetFrame.item, currentActPath[0]);
                        final Bitmap bmp = (bmpData != null && bmpData.length > 0) ? BitmapFactory.decodeByteArray(bmpData, 0, bmpData.length) : null;
                        uiHandler.post(() -> {
                            if (bmp != null) {
                                previewImg.setImageBitmap(bmp);
                                if (!isMatrixInitialized[0] && canvasFrame.getWidth() > 0) {
                                    float scale = Math.min((float)canvasFrame.getWidth() / bmp.getWidth(), (float)canvasFrame.getHeight() / bmp.getHeight());
                                    if (scale > 1.5f) scale = 1.5f; float dx = (canvasFrame.getWidth() - bmp.getWidth() * scale) / 2f; float dy = (canvasFrame.getHeight() - bmp.getHeight() * scale) / 2f;
                                    imageMatrix.setScale(scale, scale); imageMatrix.postTranslate(dx, dy); previewImg.setImageMatrix(imageMatrix); isMatrixInitialized[0] = true;
                                }
                            } else { previewImg.setImageBitmap(null); }
                            infoText.setText(String.format(L("帧: ") + "%d / %d | " + L("动作: ") + "%d | " + L("索引: ") + "%d | " + L("尺寸: ") + "%dx%d | " + L("轴心: ") + "%d, %d", currentFrameIndex[0] + 1, currentGroupFrames.size(), targetFrame.group, targetFrame.item, targetFrame.width, targetFrame.height, targetFrame.x, targetFrame.y));
                        });
                    } catch(Exception e){}
                }).start();
            }
        };

        btnDefaultAct.setOnClickListener(v -> {
            currentActPath[0] = "";
            Toast.makeText(getContext(), L("✅ 已恢复内置色表"), Toast.LENGTH_SHORT).show();
            updateFrameAction.run();
        });

        btnAutoAct.setOnClickListener(v -> {
            File[] actFiles = new File(sffPath).getParentFile().listFiles((d, name) -> name.toLowerCase().endsWith(".act"));
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
            LinearLayout gLayout = new LinearLayout(getContext()); 
            gLayout.setOrientation(LinearLayout.VERTICAL); 
            gLayout.setBackgroundColor(Color.parseColor("#2D2D30")); 
            gLayout.setPadding((int)(20*density), (int)(20*density), (int)(20*density), (int)(20*density));
            
            TextView title = new TextView(getContext()); 
            title.setText(L("选择动作编组")); 
            applyGlobalFontSettings(title, 1.1f, true); 
            title.setGravity(Gravity.CENTER); 
            title.setPadding(0, 0, 0, (int)(10*density));
            gLayout.addView(title);

            // 【新增】动作组搜索栏
            EditText searchInput = createInput(L("🔍 输入动作组号搜索..."), "");
            LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, -2);
            searchParams.setMargins(0, 0, 0, (int)(15*density));
            gLayout.addView(searchInput, searchParams);

            ScrollView gScroll = new ScrollView(getContext());
            final LinearLayout gList = new LinearLayout(getContext());
            gList.setOrientation(LinearLayout.VERTICAL);

            for (final int g : groupList) {
                String btnText = (g == -999) ? L("📂 所有动作") : L("📁 动作组 ") + g;
                Button groupBtn = createButton(btnText, "#1E1E1E");
                groupBtn.setTag(String.valueOf(g)); // 挂载tag，方便搜索精确匹配数字
                LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, -2); 
                gp.setMargins(0, 0, 0, (int)(5*density));
                groupBtn.setOnClickListener(gv -> {
                    currentGroupFrames.clear();
                    if (allFrames != null) {
                        if (g == -999) currentGroupFrames.addAll(allFrames); 
                        else for (GoEngineBridge.SffFrame f : allFrames) { if (f.group == g) currentGroupFrames.add(f); }
                    }
                    currentFrameIndex[0] = 0; updateFrameAction.run();
                    btnGroup.setText(btnText);
                    groupDialog.dismiss();
                });
                gList.addView(groupBtn, gp);
            }
            
            // 【新增】实时监听搜索输入，动态过滤列表
            searchInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    String query = s.toString().trim();
                    for (int i = 0; i < gList.getChildCount(); i++) {
                        View child = gList.getChildAt(i);
                        if (child instanceof Button) {
                            String tag = (String) child.getTag();
                            String text = ((Button) child).getText().toString();
                            // 如果搜索框为空，或者按钮文本包含搜索词，或者Tag直接匹配，则显示
                            if (query.isEmpty() || text.contains(query) || tag.equals(query)) {
                                child.setVisibility(View.VISIBLE);
                            } else {
                                child.setVisibility(View.GONE);
                            }
                        }
                    }
                }
            });

            gScroll.addView(gList);
            gLayout.addView(gScroll, new LinearLayout.LayoutParams((int)(250*density), (int)(300*density))); 
            groupDialog.setContentView(gLayout); 
            groupDialog.show();
        });

        btnReplace.setOnClickListener(v -> {
            if (currentGroupFrames.isEmpty()) return;
            isPlaying[0] = false; playHandler.removeCallbacksAndMessages(null);
            btnPlay.setText(L("▶️ 播放")); btnPlay.setBackgroundColor(Color.parseColor("#0078D7"));
            final GoEngineBridge.SffFrame f = currentGroupFrames.get(currentFrameIndex[0]);
            
            showWin10FilePicker(L("选择替换用的图像或所在目录"), 7, null, null, selectedFile -> {
                FileCallback doReplace = finalFile -> {
                    new Thread(() -> {
                        // 补齐 Go 引擎强制要求的 axisX 和 axisY 轴心点参数
                        boolean success = Api.replaceSffFrame(sffPath, f.group, f.item, (short)f.x, (short)f.y, finalFile.getAbsolutePath());
                        uiHandler.post(() -> {
                            if (success) { 
                                Toast.makeText(getContext(), L("✅ ") + f.group + "-" + f.item + L(" 帧已替换！"), Toast.LENGTH_SHORT).show(); 
                                updateFrameAction.run(); 
                            } 
                            else { 
                                Toast.makeText(getContext(), L("❌ 替换失败：格式不兼容！SFFv1 只能使用 PCX，SFFv2 禁止使用 PCX！"), Toast.LENGTH_LONG).show(); 
                            }
                        });
                    }).start();
                };
                if (selectedFile.isDirectory()) showImageGridPicker(selectedFile, doReplace); else doReplace.onFileSelected(selectedFile);
            });
        });

        if (allFrames != null) currentGroupFrames.addAll(allFrames);
        canvasFrame.post(updateFrameAction);

        // playHandler 在上方已声明，此处直接使用即可
        Runnable playRunnable = new Runnable() { @Override public void run() { if (isPlaying[0] && !currentGroupFrames.isEmpty()) { currentFrameIndex[0]++; updateFrameAction.run(); playHandler.postDelayed(this, currentDelay[0]); } } };

        btnPrev.setOnClickListener(v -> { currentFrameIndex[0]--; updateFrameAction.run(); }); btnNext.setOnClickListener(v -> { currentFrameIndex[0]++; updateFrameAction.run(); });
        btnPlay.setOnClickListener(v -> {
            if (currentGroupFrames.isEmpty()) return; isPlaying[0] = !isPlaying[0];
            btnPlay.setText(isPlaying[0] ? L("⏸️ 暂停") : L("▶️ 播放")); 
            btnPlay.setBackgroundColor(isPlaying[0] ? Color.parseColor("#E81123") : Color.parseColor("#0078D7"));
            if (isPlaying[0]) playHandler.post(playRunnable); else playHandler.removeCallbacksAndMessages(null);
        });

        btnExportNative.setOnClickListener(v -> {
            if(currentGroupFrames.isEmpty()) return; GoEngineBridge.SffFrame f = currentGroupFrames.get(currentFrameIndex[0]);
            
            final Dialog formatDialog = new Dialog(getContext());
            formatDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            LinearLayout fLayout = new LinearLayout(getContext());
            fLayout.setOrientation(LinearLayout.VERTICAL);
            fLayout.setBackgroundColor(Color.parseColor("#2D2D30"));
            fLayout.setPadding((int)(20*density), (int)(20*density), (int)(20*density), (int)(20*density));
            
            TextView title = new TextView(getContext());
            title.setText(L("选择导出格式"));
            applyGlobalFontSettings(title, 1.1f, true);
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, 0, 0, (int)(15*density));
            fLayout.addView(title);
            
            Button btnPcx = createButton(L("💾 导出为 PCX (原生)"), "#0078D7");
            Button btnPng = createButton(L("💾 导出为 PNG (通用)"), "#4CAF50");
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams((int)(200*density), -2);
            bp.setMargins(0, 0, 0, (int)(10*density));
            
            View.OnClickListener exportAction = ev -> {
                boolean wantPng = (ev == btnPng);
                formatDialog.dismiss();
                
                new Thread(() -> {
                    File outDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "IkemenExports"); 
                    if (!outDir.exists()) outDir.mkdirs();
                    try {
                        String finalPath = "";
                        if (wantPng) {
                            // 💡 核心巧妙点：纯 Java 层实现 PCX 到 PNG 的自动转换！
                            // 绕开原生导出，直接调用底层解码引擎，将色表与图像合并渲染为无损 PNG 字节流
                            byte[] pngData = Api.decodeSffFrame(sffPath, f.group, f.item, currentActPath[0]);
                            if (pngData != null && pngData.length > 0) {
                                String exportName = new File(sffPath).getName().replaceAll("\\.[^.]+$", "");
                                File outFile = new File(outDir, exportName + "_G" + f.group + "_I" + f.item + ".png");
                                FileOutputStream fos = new FileOutputStream(outFile);
                                fos.write(pngData);
                                fos.close();
                                finalPath = outFile.getAbsolutePath();
                            }
                        } else {
                            // 玩家选择了 PCX (原生格式)：直接调用你底层的 exportSffFrameNative
                            // Go 引擎中 SFFv1 依然会完美走 PCX 的原生抽离写入逻辑
                            finalPath = Api.exportSffFrameNative(sffPath, f.group, f.item, currentActPath[0], outDir.getAbsolutePath());
                        }
                        
                        final String outResult = finalPath;
                        uiHandler.post(() -> {
                            if (outResult != null && !outResult.isEmpty()) {
                                Toast.makeText(getContext(), L("✅ 导出成功: ") + outResult, Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(getContext(), L("❌ 导出失败：解析异常或写入失败"), Toast.LENGTH_LONG).show();
                            }
                        });
                    } catch (Exception e) {
                        uiHandler.post(() -> Toast.makeText(getContext(), L("❌ 导出崩溃: ") + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }).start();
            };
            
            btnPcx.setOnClickListener(exportAction);
            btnPng.setOnClickListener(exportAction);
            fLayout.addView(btnPcx, bp);
            fLayout.addView(btnPng, bp);
            
            formatDialog.setContentView(fLayout);
            formatDialog.show();
        });

        controls.addView(btnDefaultAct, uniformBtnParams);
        controls.addView(btnAutoAct, uniformBtnParams); 
        controls.addView(btnManualAct, uniformBtnParams); 
        controls.addView(btnGroup, uniformBtnParams); // 加入统一的动作编组控件
        controls.addView(btnPrev, wideBtnParams);     // 应用加宽布局
        controls.addView(btnPlay, wideBtnParams);     // 应用加宽布局
        controls.addView(btnNext, wideBtnParams);     // 应用加宽布局
        controls.addView(btnSpeed, uniformBtnParams); 
        controls.addView(btnExportNative, uniformBtnParams); 
        controls.addView(btnReplace, uniformBtnParams);
        
        controlsScroll.addView(controls);
        bottomToolArea.addView(controlsScroll);
        root.addView(bottomToolArea);
        
        openAppWindow(winTitle, root, () -> {
            isPlaying[0] = false; playHandler.removeCallbacksAndMessages(null); 
            View win = windowsLayer.findViewWithTag(winTitle); if (win != null) windowsLayer.removeView(win);
            View tbBtn = taskbarAppsLayout.findViewWithTag("tb_" + winTitle); if (tbBtn != null) taskbarAppsLayout.removeView(tbBtn);
        });
    }

    // ======================================================================================
    // 🎵 模块 2：SND 音频检视工坊
    // ======================================================================================
    private View buildSndExtractorContent() {
        LinearLayout root = new LinearLayout(getContext()); root.setOrientation(LinearLayout.VERTICAL); root.setPadding((int)(15*density), (int)(15*density), (int)(15*density), (int)(15*density));
        LinearLayout topBar = new LinearLayout(getContext()); topBar.setOrientation(LinearLayout.HORIZONTAL); topBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView statusText = new TextView(getContext()); statusText.setText(L(" 状态: 等待选取 .snd 文件...")); applyGlobalFontSettings(statusText, 1.0f, false);
        Button scanBtn = createButton(L("📂 浏览并选择 SND 音频文件"), "#FF9800"); LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(-2, -2); btnParams.setMargins(0, 0, (int)(15*density), 0);
        topBar.addView(scanBtn, btnParams); topBar.addView(statusText); root.addView(topBar);

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

        List<Integer> groupList = new ArrayList<>();
        groupList.add(-999);
        if (allNodes != null) {
            for (GoEngineBridge.SndNode n : allNodes) { if (!groupList.contains(n.group)) { groupList.add(n.group); } }
        }

        ScrollView scroll = new ScrollView(getContext()); LinearLayout listLayout = new LinearLayout(getContext()); listLayout.setOrientation(LinearLayout.VERTICAL); scroll.addView(listLayout, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1f); scrollParams.setMargins((int)(10*density), (int)(10*density), (int)(10*density), (int)(10*density)); root.addView(scroll, scrollParams);

        HorizontalScrollView groupScroll = new HorizontalScrollView(getContext()); groupScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout groupToolBelt = new LinearLayout(getContext()); groupToolBelt.setOrientation(LinearLayout.HORIZONTAL); groupToolBelt.setPadding((int)(15*density), (int)(15*density), (int)(15*density), (int)(15*density)); groupToolBelt.setBackgroundColor(Color.parseColor("#2D2D30"));

        final int[] currentSelectedGroup = {-999};

        final Runnable[] refreshList = {null};
        refreshList[0] = () -> {
            listLayout.removeAllViews(); int selectedGroup = currentSelectedGroup[0];
            if (allNodes != null) {
                for (GoEngineBridge.SndNode n : allNodes) {
                    if (selectedGroup != -999 && n.group != selectedGroup) continue;
                    LinearLayout row = new LinearLayout(getContext()); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding((int)(10*density), (int)(10*density), (int)(10*density), (int)(10*density));
                    GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#2D2D30")); bg.setCornerRadius(8f*density); row.setBackground(bg);
                    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2); rowParams.setMargins(0, 0, 0, (int)(8*density));
                    TextView info = new TextView(getContext()); info.setText(String.format(L("🎵 动作组 (Group): %d | 索引 (Item): %d"), n.group, n.item)); applyGlobalFontSettings(info, 0.9f, false); row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));

                    Button btnPlay = createButton(L("▶️ 试听"), "#FF9800"); btnPlay.setPadding((int)(15*density), (int)(8*density), (int)(15*density), (int)(8*density));
                    btnPlay.setOnClickListener(v -> {
                        try {
                            if (currentSndPlayer != null) { currentSndPlayer.release(); currentSndPlayer = null; }
                            byte[] wavData = Api.extractSndAudio(sndPath, n.group, n.item);
                            if(wavData != null && wavData.length > 0) {
                                File tempWav = new File(getContext().getCacheDir(), "ikemen_preview.wav"); FileOutputStream fos = new FileOutputStream(tempWav); fos.write(wavData); fos.close();
                                currentSndPlayer = new MediaPlayer(); currentSndPlayer.setDataSource(tempWav.getAbsolutePath()); currentSndPlayer.prepare(); currentSndPlayer.start();
                            }
                        } catch (Exception e) {}
                    });
                    
                    Button btnExport = createButton(L("💾 导出"), "#0078D7"); LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(-2, -2); btnParams.setMargins((int)(10*density), 0, 0, 0);
                    btnExport.setOnClickListener(v -> {
                        try {
                            byte[] wavData = Api.extractSndAudio(sndPath, n.group, n.item);
                            if(wavData != null) {
                                File outDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "IkemenExports"); if (!outDir.exists()) outDir.mkdirs();
                                File outFile = new File(outDir, sndName.replace(".snd", "") + "_G" + n.group + "_I" + n.item + ".wav"); FileOutputStream fos = new FileOutputStream(outFile); fos.write(wavData); fos.close();
                                Toast.makeText(getContext(), L("✅ 已导出: ") + outFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {}
                    });
                    
                                       Button btnReplace = createButton(L("🔄 替换"), "#4CAF50");
                    btnReplace.setOnClickListener(v -> {
                        if (currentSndPlayer != null) { currentSndPlayer.release(); currentSndPlayer = null; }
                        showWin10FilePicker(L("选择替换用的音频或所在目录"), 8, null, null, selectedFile -> {
                            FileCallback doReplace = finalFile -> {
                                new Thread(() -> {
                                    boolean success = Api.replaceSndAudio(sndPath, n.group, n.item, finalFile.getAbsolutePath());
                                    new Handler(Looper.getMainLooper()).post(() -> {
                                        if (success) { Toast.makeText(getContext(), L("✅ ") + n.group + "-" + n.item + L(" 音频已替换！"), Toast.LENGTH_SHORT).show(); } 
                                        else { Toast.makeText(getContext(), L("❌ 音频格式不支持，替换失败"), Toast.LENGTH_LONG).show(); }
                                    });
                                }).start();
                            };
                            // 如果选的是文件夹，打开音频列表扫描器；如果是文件，直接替换
                            if (selectedFile.isDirectory()) showAudioListPicker(selectedFile, doReplace); else doReplace.onFileSelected(selectedFile);
                        });
                    });


                    Button btnDelete = createButton(L("🗑️ 删除"), "#E81123");
                    btnDelete.setOnClickListener(v -> {
                        new Thread(() -> {
                            boolean success = api.Api.deleteSndAudio(sndPath, n.group, n.item);
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (success) { 
                                    Toast.makeText(getContext(), L("✅ 音频已永久删除！"), Toast.LENGTH_SHORT).show(); 
                                    new Thread(() -> {
                                        List<GoEngineBridge.SndNode> newNodes = GoEngineBridge.scanSnd(sndPath);
                                        new Handler(Looper.getMainLooper()).post(() -> { allNodes.clear(); allNodes.addAll(newNodes); refreshList[0].run(); });
                                    }).start();
                                } else { Toast.makeText(getContext(), L("❌ 删除失败"), Toast.LENGTH_SHORT).show(); }
                            });
                        }).start();
                    });

                    HorizontalScrollView hsv = new HorizontalScrollView(getContext());
                    hsv.setHorizontalScrollBarEnabled(false);
                    LinearLayout btnContainer = new LinearLayout(getContext());
                    btnContainer.setOrientation(LinearLayout.HORIZONTAL);
                    
                    btnContainer.addView(btnPlay); 
                    btnContainer.addView(btnExport, btnParams); 
                    btnContainer.addView(btnReplace, btnParams); 
                    btnContainer.addView(btnDelete, btnParams);
                    
                    hsv.addView(btnContainer);
                    row.addView(hsv); 
                    listLayout.addView(row, rowParams);
                }
            }
        };

        Button btnAddSnd = createButton(L("➕ 新增音频"), "#4CAF50");
        LinearLayout.LayoutParams btnAddParams = new LinearLayout.LayoutParams(-2, -2);
        btnAddParams.setMargins(0, 0, (int)(15*density), 0);
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
                        int addG = 0, addI = 0; try { addG = Integer.parseInt(gIn.getText().toString()); addI = Integer.parseInt(iIn.getText().toString()); } catch(Exception e){}
                        final int fg = addG, fi = addI;
                        new Thread(() -> {
                            boolean success = api.Api.addSndAudio(sndPath, fg, fi, finalFile.getAbsolutePath());
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (success) { 
                                    Toast.makeText(getContext(), L("✅ 音频强行注入成功！"), Toast.LENGTH_SHORT).show(); d.dismiss();
                                    new Thread(() -> {
                                        List<GoEngineBridge.SndNode> newNodes = GoEngineBridge.scanSnd(sndPath);
                                        new Handler(Looper.getMainLooper()).post(() -> { allNodes.clear(); allNodes.addAll(newNodes); refreshList[0].run(); });
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
        groupToolBelt.addView(btnAddSnd, btnAddParams);

        for (final int g : groupList) {
            String btnText = (g == -999) ? L("📂 所有音频") : L("📁 音频组 ") + g;
            Button groupBtn = createButton(btnText, "#1E1E1E");
            LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-2, -2); gp.setMargins(0, 0, (int)(5*density), 0);
            groupBtn.setOnClickListener(v -> {
                currentSelectedGroup[0] = g; refreshList[0].run();
            });
            groupToolBelt.addView(groupBtn, gp);
        }
        groupScroll.addView(groupToolBelt);
        root.addView(groupScroll);
        
        refreshList[0].run(); 

        openAppWindow(winTitle, root, () -> {
            if (currentSndPlayer != null) { currentSndPlayer.release(); currentSndPlayer = null; }
            View win = windowsLayer.findViewWithTag(winTitle); if (win != null) windowsLayer.removeView(win);
            View tbBtn = taskbarAppsLayout.findViewWithTag("tb_" + winTitle); if (tbBtn != null) taskbarAppsLayout.removeView(tbBtn);
        });
    }

    // ======================================================================================
    // 🎞️ 模块 3：GIF 拆解器 (全面支持动态网格预览与全标准控件窗口)
    // ======================================================================================
    private View buildGifExtractorContent() {
        LinearLayout root = new LinearLayout(getContext()); root.setOrientation(LinearLayout.VERTICAL); root.setPadding((int)(15*density), (int)(15*density), (int)(15*density), (int)(15*density));
        LinearLayout topBar = new LinearLayout(getContext()); topBar.setOrientation(LinearLayout.HORIZONTAL); topBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView statusText = new TextView(getContext()); statusText.setText(L(" 状态: 等待选取 .gif 文件...")); applyGlobalFontSettings(statusText, 1.0f, false);
        Button scanBtn = createButton(L("📂 浏览并选择 GIF 文件"), "#0078D7"); LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(-2, -2); btnParams.setMargins(0, 0, (int)(15*density), 0);
        topBar.addView(scanBtn, btnParams); topBar.addView(statusText); root.addView(topBar);
        
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
        gridScroll.addView(gridContainer); 
        mainArea.addView(gridScroll, new LinearLayout.LayoutParams(-2, -1));

        LinearLayout rightArea = new LinearLayout(getContext()); 
        rightArea.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, -1, 1f); 
        rightParams.setMargins((int)(15*density), 0, 0, 0);
        rightArea.setLayoutParams(rightParams);

        FrameLayout previewFrame = new FrameLayout(getContext());
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
        dpadAreaContainer.addView(dpadRow1); dpadAreaContainer.addView(dpadRow2); sffCenterPanel.addView(dpadAreaContainer, new LinearLayout.LayoutParams(-1, -2));

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
    // ======================================================================================
    // 🎮 模块 8：远程同乐 (云同游大厅)
    // ======================================================================================
    public static android.widget.TextView logConsole; 
    public static ScrollView logScrollView; 

    private View buildRemotePlayContent() {
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#1E1E1E"));
        root.setPadding((int)(15 * density), (int)(15 * density), (int)(15 * density), (int)(15 * density));

        LinearLayout tabBar = new LinearLayout(getContext());
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setGravity(Gravity.CENTER);
        
        Button btnHost = createButton(L("🏠 创建房间 (我是主机)"), "#0078D7");
        Button btnClient = createButton(L("🔗 加入房间 (我是手柄)"), "#333333");
        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(0, -2, 1f);
        tabParams.setMargins(0, 0, (int)(5 * density), 0);
        tabBar.addView(btnHost, tabParams); tabBar.addView(btnClient, tabParams);
        root.addView(tabBar);

        // --- 全局实时日志与聊天面板 ---
        LinearLayout logAndChatPanel = new LinearLayout(getContext());
        logAndChatPanel.setOrientation(LinearLayout.VERTICAL);
        
        ScrollView logScroll = new ScrollView(getContext());
        logConsole = new android.widget.TextView(getContext());
        logConsole.setTextColor(Color.parseColor("#4CAF50")); 
        logConsole.setTextSize(11f);
        logConsole.setText(L("=> Ikemen WebRTC 引擎已就绪...\n"));
        logConsole.setTypeface(Typeface.MONOSPACE);
        logScroll.addView(logConsole);
        logScroll.setBackgroundColor(Color.parseColor("#000000"));
        logScroll.setPadding((int)(10*density), (int)(10*density), (int)(10*density), (int)(10*density));
        logScrollView = logScroll; 
        
        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(-1, (int)(100 * density));
        logParams.setMargins(0, (int)(10 * density), 0, 0);
        logAndChatPanel.addView(logScroll, logParams);

        LinearLayout chatLayout = new LinearLayout(getContext());
        chatLayout.setOrientation(LinearLayout.HORIZONTAL);
        chatLayout.setPadding(0, (int)(5 * density), 0, 0);
        EditText chatInput = createInput(L("发送局域网/房间消息..."), "");
        Button btnSendChat = createButton(L("✉️ 发送"), "#2196F3");
        chatLayout.addView(chatInput, new LinearLayout.LayoutParams(0, -2, 1f));
        chatLayout.addView(btnSendChat, new LinearLayout.LayoutParams(-2, -2));
        logAndChatPanel.addView(chatLayout);
        
        btnSendChat.setOnClickListener(v -> {
            String msg = chatInput.getText().toString().trim();
            if(!msg.isEmpty()) { CloudGamingManager.sendChatMessage(msg); chatInput.setText(""); }
        });

        root.addView(logAndChatPanel);

        // --- ⚙️ 极客网络选项 (局域网直连 / 中转配置) ---
        LinearLayout advancedPanel = new LinearLayout(getContext());
        advancedPanel.setOrientation(LinearLayout.VERTICAL);
        advancedPanel.setPadding(0, (int)(10 * density), 0, (int)(10 * density));
        advancedPanel.addView(createSubTitle(L("⚙️ 极客网络选项 (留空则默认):")));
        EditText customStunInput = createInput(L("自定义 STUN 穿透节点"), "");
        EditText customSignalInput = createInput(L("自定义 Ntfy 信令总线"), "");
        advancedPanel.addView(customStunInput); advancedPanel.addView(customSignalInput);

        // ==================== 主机面板 ====================
        ScrollView hostScroll = new ScrollView(getContext());
        LinearLayout hostPanel = new LinearLayout(getContext());
        hostPanel.setOrientation(LinearLayout.VERTICAL);
        hostScroll.addView(hostPanel);

        EditText hostNameInput = createInput(L("输入昵称 (如: 隆)"), "主机_" + (int)(Math.random()*999));
        hostPanel.addView(createSubTitle(L("👤 你的昵称:"))); hostPanel.addView(hostNameInput);
        hostPanel.addView(advancedPanel);
        
        hostPanel.addView(createSubTitle(L("📡 串流画质选择:")));
        Spinner qualitySpinner = new Spinner(getContext());
        ArrayAdapter<String> qAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, 
            new String[]{L("🔥 原画无损 (高配局域网)"), L("📺 720P 平衡"), L("📱 480P 流畅")});
        qualitySpinner.setAdapter(qAdapter); qualitySpinner.setSelection(1);
        hostPanel.addView(qualitySpinner);

        hostPanel.addView(createSubTitle(L("🔑 你的专属连接口令:")));
        final EditText roomCodeInput = createInput(L("点击下方建立，将获取直连口令"), "");
        roomCodeInput.setGravity(Gravity.CENTER); roomCodeInput.setTextSize(18f); roomCodeInput.setTextColor(Color.parseColor("#FFD700"));
        hostPanel.addView(roomCodeInput);

        Button btnCreateRoom = createButton(L("🚀 建立房间 (允许录屏后自动监听)"), "#4CAF50");
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(-1, -2); btnParams.setMargins(0, (int)(10 * density), 0, 0);
        
        btnCreateRoom.setOnClickListener(v -> {
            String wanCode = String.format("%06d", (int)(Math.random() * 999999));
            String lanIP = CloudGamingManager.getLocalIpAddress(); 
            roomCodeInput.setText(L("外网: ") + wanCode + "  |  " + L("内网: ") + lanIP);
            CloudGamingManager.playerName = hostNameInput.getText().toString();

            Activity activity = org.libsdl.app.SDLActivity.mSingleton;
            if (activity == null) return;
            try {
                ScreenCapFragment f = new ScreenCapFragment();
                f.wanCode = wanCode; f.quality = qualitySpinner.getSelectedItemPosition();
                f.customStun = customStunInput.getText().toString().trim(); 
                f.customSignal = customSignalInput.getText().toString().trim(); 
                f.uiRoot = root; // 🚀 精准传入 UI 容器
                activity.getFragmentManager().beginTransaction().add(f, "ScreenCapPerm").commitAllowingStateLoss();
            } catch (Exception e) {}
        });
        hostPanel.addView(btnCreateRoom, btnParams);

        // ==================== 客户端面板 ====================
        ScrollView clientScroll = new ScrollView(getContext());
        LinearLayout clientPanel = new LinearLayout(getContext());
        clientPanel.setOrientation(LinearLayout.VERTICAL);
        clientScroll.addView(clientPanel); clientScroll.setVisibility(View.GONE);

        EditText clientNameInput = createInput(L("输入你的玩家昵称"), "挑战者_" + (int)(Math.random()*999));
        clientPanel.addView(createSubTitle(L("👤 玩家昵称:"))); clientPanel.addView(clientNameInput);

        clientPanel.addView(createSubTitle(L("🔗 输入主机口令 (内网IP 或 6位外网码):")));
        final EditText joinCodeInput = createInput(L("例如: 192.168.43.1 或 886655"), "");
        joinCodeInput.setGravity(Gravity.CENTER); joinCodeInput.setTextSize(18f);
        clientPanel.addView(joinCodeInput);

        Button btnJoinRoom = createButton(L("🔌 穿透连接主机 (化身2P)"), "#FF9800");
        btnJoinRoom.setOnClickListener(v -> {
            String code = joinCodeInput.getText().toString().trim();
            CloudGamingManager.playerName = clientNameInput.getText().toString();
            if (code.isEmpty()) return;
            String customStun = customStunInput.getText().toString().trim(); 
            String customSignal = customSignalInput.getText().toString().trim(); 
            CloudGamingManager.log("=> 🚀 启动客户端引擎，寻址目标: [" + code + "] ...");
            CloudGamingManager.startClient(getContext(), root, code, customStun, customSignal); // 🚀 传入 root
        });
        clientPanel.addView(btnJoinRoom, btnParams);

        FrameLayout contentContainer = new FrameLayout(getContext());
        contentContainer.addView(hostScroll); contentContainer.addView(clientScroll);
        root.addView(contentContainer, new LinearLayout.LayoutParams(-1, 0, 1f));

        btnHost.setOnClickListener(v -> {
            btnHost.setBackgroundColor(Color.parseColor("#0078D7")); btnClient.setBackgroundColor(Color.parseColor("#333333"));
            hostScroll.setVisibility(View.VISIBLE); clientScroll.setVisibility(View.GONE);
            if(advancedPanel.getParent() != null) ((ViewGroup)advancedPanel.getParent()).removeView(advancedPanel);
            hostPanel.addView(advancedPanel, 1);
        });
        btnClient.setOnClickListener(v -> {
            btnClient.setBackgroundColor(Color.parseColor("#0078D7")); btnHost.setBackgroundColor(Color.parseColor("#333333"));
            clientScroll.setVisibility(View.VISIBLE); hostScroll.setVisibility(View.GONE);
            if(advancedPanel.getParent() != null) ((ViewGroup)advancedPanel.getParent()).removeView(advancedPanel);
            clientPanel.addView(advancedPanel, 1);
        });

        // 🛑 保留彻底销毁按钮，满足你手动断开的需求
        Button btnKill = createButton(L("🛑 断开联机并关闭大厅"), "#F44336");
        LinearLayout.LayoutParams killParams = new LinearLayout.LayoutParams(-1, -2); killParams.setMargins(0, (int)(10 * density), 0, 0);
        btnKill.setOnClickListener(v -> {
            try {
                if (CloudGamingManager.peerConnection != null) { CloudGamingManager.peerConnection.close(); CloudGamingManager.peerConnection = null; }
                if (CloudGamingManager.lanServer != null) { CloudGamingManager.lanServer.close(); CloudGamingManager.lanServer = null; }
                CloudGamingManager.log("🛑 联机进程已彻底终止！");
            } catch (Exception e) {}
            forceDestroy(); 
        });
        root.addView(btnKill, killParams);

        return root;
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

        LinearLayout topBar = new LinearLayout(getContext()); topBar.setOrientation(LinearLayout.HORIZONTAL); topBar.setGravity(Gravity.CENTER_VERTICAL);
        Button btnScan = createButton(L("📂 选择根目录进行全盘扫描 (Chars/Stages)"), "#0078D7");
        TextView txtStatus = new TextView(getContext()); txtStatus.setText(L("  等待扫描...")); applyGlobalFontSettings(txtStatus, 1.0f, false); txtStatus.setTextColor(Color.WHITE);
        topBar.addView(btnScan); topBar.addView(txtStatus); root.addView(topBar);

        ScrollView scroll = new ScrollView(getContext()); LinearLayout contentArea = new LinearLayout(getContext()); contentArea.setOrientation(LinearLayout.VERTICAL); scroll.addView(contentArea);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));

        btnScan.setOnClickListener(v -> {
            showWin10FilePicker(L("选择你要扫描的根目录"), 13, null, null, dir -> {
                if(!dir.isDirectory()) { Toast.makeText(getContext(), L("❌ 请选择文件夹！"), Toast.LENGTH_SHORT).show(); return; }
                contentArea.removeAllViews(); txtStatus.setText(L("  🚀 正在深度扫描全文，请稍候..."));
                
                final List<String> stageResults = new ArrayList<>();
                final List<String> charNormalResults = new ArrayList<>();
                final List<String> charRiskResults = new ArrayList<>();

                new Thread(() -> {
                    List<File> allDefs = new ArrayList<>();
                    class DefScanner {
                        void scan(File targetDir) {
                            File[] fs = targetDir.listFiles(); if(fs==null) return;
                            for(File f:fs){ if(f.isDirectory() && !f.isHidden()) scan(f); else if(f.getName().toLowerCase().endsWith(".def")) allDefs.add(f); }
                        }
                    }
                    new DefScanner().scan(dir);

                    for (File defF : allDefs) {
                        try {
                            // 根据你的要求：全文扫描，绝不只扫前50行
                            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(defF));
                            String line; boolean isStage = false; boolean isChar = false;
                            while ((line = br.readLine()) != null) {
                                String lower = line.toLowerCase().trim();
                                if (lower.startsWith("[stageinfo]") || lower.startsWith("[bgdef]")) { isStage = true; break; }
                                if (lower.startsWith("[files]") || lower.contains("cmd =") || lower.contains("cns =")) { isChar = true; } // 不立即 break，防止误判
                            }
                            br.close();

                            String folderName = defF.getParentFile().getName();
                            String defNameNoExt = defF.getName().substring(0, defF.getName().lastIndexOf("."));

                            if (isStage) {
                                stageResults.add(folderName + "/" + defF.getName());
                            } else if (isChar) {
                                if (folderName.equalsIgnoreCase(defNameNoExt)) {
                                    charNormalResults.add(defNameNoExt);
                                } else {
                                    charRiskResults.add(L("文件夹名称：") + folderName + L(" - def名称:") + defF.getName());
                                }
                            }
                        } catch (Exception e) {}
                    }

                    new Handler(Looper.getMainLooper()).post(() -> {
                        txtStatus.setText(L("  ✅ 扫描完毕！共分析 ") + allDefs.size() + L(" 个 DEF。"));
                        
                        StringBuilder finalExportText = new StringBuilder();
                        finalExportText.append("; ==================================\n");
                        finalExportText.append("; 🗺️ 地图列表 (Stages)\n");
                        finalExportText.append("; ==================================\n");
                        contentArea.addView(createTitle(L("🗺️ 地图列表 (Stages)")));
                        for (String s : stageResults) { 
                            TextView tv = new TextView(getContext()); tv.setText(s); tv.setTextColor(Color.parseColor("#4CAF50")); applyGlobalFontSettings(tv, 0.9f, false); contentArea.addView(tv); 
                            finalExportText.append(s).append("\n");
                        }

                        finalExportText.append("\n; ==================================\n");
                        finalExportText.append("; 🥷 角色列表 - 正常 (Characters)\n");
                        finalExportText.append("; ==================================\n");
                        contentArea.addView(createTitle(L("🥷 角色列表 (正常匹配)")));
                        for (String s : charNormalResults) { 
                            TextView tv = new TextView(getContext()); tv.setText(s); tv.setTextColor(Color.parseColor("#0078D7")); applyGlobalFontSettings(tv, 0.9f, false); contentArea.addView(tv); 
                            finalExportText.append(s).append("\n");
                        }

                        finalExportText.append("\n; ==================================\n");
                        finalExportText.append("; ⚠️ 角色列表 - 风险类 (文件夹与 DEF 不一致)\n");
                        finalExportText.append("; ==================================\n");
                        contentArea.addView(createTitle(L("⚠️ 角色列表 (风险类)")));
                        for (String s : charRiskResults) { 
                            TextView tv = new TextView(getContext()); tv.setText(s); tv.setTextColor(Color.parseColor("#E81123")); applyGlobalFontSettings(tv, 0.9f, false); contentArea.addView(tv); 
                            finalExportText.append("; ").append(s).append("\n"); // 风险类默认注释掉，防止直接报错
                        }

                        Button btnExport = createButton(L("💾 导出并保存为 txt 文件 (供 select.def 使用)"), "#FF9800");
                        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2); bp.setMargins(0, (int)(25*density), 0, (int)(25*density));
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
                                    File outFile = new File(outDir, nameInput.getText().toString());
                                    FileOutputStream fos = new FileOutputStream(outFile);
                                    fos.write(finalExportText.toString().getBytes("UTF-8")); fos.close();
                                    Toast.makeText(getContext(), L("✅ 成功导出到:\n") + outFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                                    d.dismiss();
                                } catch (Exception e) { Toast.makeText(getContext(), L("❌ 导出失败"), Toast.LENGTH_SHORT).show(); }
                            });
                            box.addView(bConfirm, new LinearLayout.LayoutParams(-1, -2)); d.setContentView(box); d.show();
                        });
                        contentArea.addView(btnExport, bp);
                    });
                }).start();
            });
        });
        return root;
    }

// ================= 【机制注入】多语言补丁快捷助手 =================
    // 自动连接到 DynamicGamepadView 的翻译引擎
    private static String L(String text) {
        return DynamicGamepadView.L(text);
    }
    // ======================================================================================
    // 🛡️ 无形权限请求载体 
    // ======================================================================================
    public static class ScreenCapFragment extends android.app.Fragment {
        public String wanCode, customStun, customSignal;
        public int quality;
        public ViewGroup uiRoot; // 🚀 修复参数名，适配新的 UI 容器
        private boolean isRequested = false;

        @Override
        public void onResume() {
            super.onResume();
            if (!isRequested && wanCode != null) {
                isRequested = true;
                try {
                    MediaProjectionManager mpm = (MediaProjectionManager) getActivity().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                    startActivityForResult(mpm.createScreenCaptureIntent(), 1412);
                } catch (Exception e) {}
            }
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            if (requestCode == 1412) {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    CloudGamingManager.startHost(getActivity(), uiRoot, data, wanCode, quality, customStun, customSignal);
                    android.widget.Toast.makeText(getActivity(), "✅ 授权成功！正在等待加入...", android.widget.Toast.LENGTH_LONG).show();
                } else {
                    android.widget.Toast.makeText(getActivity(), "❌ 必须授予录屏权限！", android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        }
    }


    // ======================================================================================
    // 🧠 核心：云同乐引擎 (自动隐藏大厅 / 解决单向流与通信死结)
    // ======================================================================================
    public static class CloudGamingManager {
        public static String playerName = "Player";
        public static String currentPeerTarget = ""; 
        public static String hostWanCode = ""; 
        
        private static PeerConnectionFactory factory;
        public static PeerConnection peerConnection;
        private static SurfaceTextureHelper surfaceTextureHelper;
        private static DataChannel dataChannel;
        private static boolean isHost = false;
        public static ServerSocket lanServer; 
        private static VideoTrack localVideoTrack; 
        public static EglBase rootEglBase; 
        public static android.widget.TextView clientMiniLog; 
        
        public static void log(String msg) {
            String time = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
            new Handler(Looper.getMainLooper()).post(() -> {
                String fullMsg = "[" + time + "] " + msg;
                Log.i("IkemenWebRTC", fullMsg); // 强制输出到 Android Logcat 留底
                if (logConsole != null) { 
                    logConsole.append(fullMsg + "\n"); 
                    if (logScrollView != null) { logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN)); }
                }
                if (clientMiniLog != null) { 
                    String old = clientMiniLog.getText().toString();
                    if(old.length() > 500) old = old.substring(old.length() - 500);
                    clientMiniLog.setText(old + fullMsg + "\n");
                }
            });
        }

        public static void sendChatMessage(String text) {
            if (dataChannel != null && dataChannel.state() == DataChannel.State.OPEN) {
                try {
                    JSONObject msg = new JSONObject(); msg.put("chat", text); msg.put("senderName", playerName);
                    java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(msg.toString().getBytes("UTF-8"));
                    dataChannel.send(new DataChannel.Buffer(buffer, false));
                    log("💬 [我]: " + text);
                } catch (Exception e) { log("❌ 发送失败"); }
            } else { log("❌ 错误：聊天与按键通道尚未打通！"); }
        }

        private static void sendSignalingMessage(String targetCode, String type, JSONObject payload, String customSignal) {
            new Thread(() -> {
                try {
                    JSONObject msg = new JSONObject(); msg.put("type", type); msg.put("payload", payload);
                    msg.put("senderName", playerName);
                    msg.put("replyToIp", getLocalIpAddress()); 
                    String msgStr = msg.toString();

                    if (targetCode.contains(".")) {
                        if (!isHost) { // 加入方发送局域网信令
                            Socket socket = new Socket(targetCode, 8192);
                            OutputStream os = socket.getOutputStream();
                            os.write((msgStr + "\n").getBytes("UTF-8"));
                            os.flush(); socket.close();
                        }
                    } else {
                        String baseUrl = (customSignal == null || customSignal.isEmpty()) ? "https://ntfy.sh/" : (customSignal.endsWith("/") ? customSignal : customSignal + "/");
                        String topic = "ikemen_webrtc_" + targetCode + (isHost ? "_client" : "_host");
                        URL url = new URL(baseUrl + topic);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST"); conn.setDoOutput(true);
                        conn.getOutputStream().write(msgStr.getBytes("UTF-8"));
                        conn.getInputStream().close();
                    }
                } catch (Exception e) {}
            }).start();
        }

        private static void startSignalingListener(String myCode, String customSignal, boolean isLan) {
            new Thread(() -> {
                try {
                    if (isLan) {
                        lanServer = new ServerSocket(8192);
                        log("=> 📡 开启局域网监听 (端口 8192)...");
                        while (!lanServer.isClosed()) {
                            Socket client = lanServer.accept();
                            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                            String line = in.readLine();
                            if (line != null) processSignalingMessage(new JSONObject(line), customSignal, true);
                            client.close();
                        }
                    } else {
                        log("=> 📡 开启免费公网打洞监听...");
                        String baseUrl = (customSignal == null || customSignal.isEmpty()) ? "https://ntfy.sh/" : (customSignal.endsWith("/") ? customSignal : customSignal + "/");
                        String topic = "ikemen_webrtc_" + myCode + (isHost ? "_host" : "_client");
                        URL url = new URL(baseUrl + topic + "/json");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        String line;
                        while ((line = in.readLine()) != null) {
                            JSONObject root = new JSONObject(line);
                            if (root.optString("event").equals("message")) { processSignalingMessage(new JSONObject(root.getString("message")), customSignal, false); }
                        }
                    }
                } catch (Exception e) {}
            }).start();
        }

        private static void processSignalingMessage(JSONObject msg, String customSignal, boolean fromLan) throws Exception {
            String type = msg.getString("type"); JSONObject payload = msg.getJSONObject("payload");
            String sender = msg.optString("senderName", "神秘玩家");
            String replyToIp = msg.optString("replyToIp", "");
            
            if (isHost) {
                if (fromLan && !replyToIp.isEmpty()) { currentPeerTarget = replyToIp; log("=> 锁定客机内网IP: " + replyToIp); } 
                else { currentPeerTarget = hostWanCode; } 
            }

            if (type.equals("offer")) {
                log("=> 🔔 收到 [" + sender + "] 的入房握手请求，正在打包本地参数...");
                peerConnection.setRemoteDescription(new SimpleSdpObserver(), new SessionDescription(SessionDescription.Type.OFFER, payload.getString("sdp")));
                peerConnection.createAnswer(new SimpleSdpObserver() {
                    @Override public void onCreateSuccess(SessionDescription sessionDescription) {
                        peerConnection.setLocalDescription(new SimpleSdpObserver(), sessionDescription);
                        try { JSONObject out = new JSONObject(); out.put("sdp", sessionDescription.description); sendSignalingMessage(currentPeerTarget, "answer", out, customSignal); } catch(Exception e){}
                        log("=> ✉️ 同意入房请求 (Answer) 已发送！");
                    }
                }, new MediaConstraints());
            } else if (type.equals("answer")) {
                log("=> 🎉 验证通过！主机 [" + sender + "] 同意了连接！等待画面降临...");
                peerConnection.setRemoteDescription(new SimpleSdpObserver(), new SessionDescription(SessionDescription.Type.ANSWER, payload.getString("sdp")));
            } else if (type.equals("candidate")) {
                log("=> 🕸️ 收到网络穿透节点 (ICE Candidate)...");
                IceCandidate candidate = new IceCandidate(payload.getString("sdpMid"), payload.getInt("sdpMLineIndex"), payload.getString("candidate"));
                peerConnection.addIceCandidate(candidate);
            }
        }

        // 🚀 核心修复：处理消息与按键注入
        private static void setupDataChannel(DataChannel channel) {
            dataChannel = channel;
            dataChannel.registerObserver(new DataChannel.Observer() {
                @Override public void onMessage(DataChannel.Buffer buffer) {
                    try {
                        byte[] data = new byte[buffer.data.remaining()]; buffer.data.get(data);
                        JSONObject input = new JSONObject(new String(data, "UTF-8"));
                        if (input.has("chat")) { log("💬 [" + input.getString("senderName") + "]: " + input.getString("chat")); return; }
                        if(isHost) {
                            String btn = input.getString("btn"); boolean down = input.getBoolean("down");
                            int keyCode = 0;
                            switch(btn) {
                                case "UP": keyCode = android.view.KeyEvent.KEYCODE_T; break;
                                case "DOWN": keyCode = android.view.KeyEvent.KEYCODE_G; break;
                                case "LEFT": keyCode = android.view.KeyEvent.KEYCODE_F; break;
                                case "RIGHT": keyCode = android.view.KeyEvent.KEYCODE_H; break;
                                case "A": keyCode = android.view.KeyEvent.KEYCODE_U; break;
                                case "B": keyCode = android.view.KeyEvent.KEYCODE_I; break;
                                case "C": keyCode = android.view.KeyEvent.KEYCODE_O; break;
                                case "X": keyCode = android.view.KeyEvent.KEYCODE_J; break;
                                case "Y": keyCode = android.view.KeyEvent.KEYCODE_K; break;
                                case "Z": keyCode = android.view.KeyEvent.KEYCODE_L; break;
                                case "START": keyCode = android.view.KeyEvent.KEYCODE_V; break;
                            }
                            if (keyCode != 0) {
                                if (down) org.libsdl.app.SDLActivity.onNativeKeyDown(keyCode);
                                else org.libsdl.app.SDLActivity.onNativeKeyUp(keyCode);
                            }
                        }
                    } catch (Exception e) {}
                }
                @Override public void onBufferedAmountChange(long l) {} @Override public void onStateChange() { log("=> ⚡ 按键/聊天通道状态: " + dataChannel.state()); }
            });
        }

        public static void startHost(Context context, ViewGroup uiRoot, Intent screenPermData, String wanCode, int quality, String customStun, String customSignal) {
            isHost = true; hostWanCode = wanCode; currentPeerTarget = wanCode;
            if (rootEglBase == null) rootEglBase = EglBase.create();
            log("=> 录屏底层通道已打开，正在初始化引擎...");

            PeerConnectionFactory.InitializationOptions initOptions = PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions();
            PeerConnectionFactory.initialize(initOptions);
            factory = PeerConnectionFactory.builder().createPeerConnectionFactory();

            List<PeerConnection.IceServer> iceServers = new ArrayList<>();
            iceServers.add(PeerConnection.IceServer.builder((customStun == null || customStun.isEmpty()) ? "stun:stun.l.google.com:19302" : customStun).createIceServer());

            peerConnection = factory.createPeerConnection(iceServers, new PeerConnection.Observer() {
                @Override public void onIceCandidate(IceCandidate iceCandidate) {
                    try { JSONObject out = new JSONObject(); out.put("sdpMid", iceCandidate.sdpMid); out.put("sdpMLineIndex", iceCandidate.sdpMLineIndex); out.put("candidate", iceCandidate.sdp); sendSignalingMessage(currentPeerTarget, "candidate", out, customSignal); } catch (Exception e){}
                }
                @Override public void onDataChannel(DataChannel channel) { 
                    log("=> 🔌 主机检测到客机发起的数据通道，正在对接..."); 
                    setupDataChannel(channel); // 🚀 核心修复：主机绝不能 createDataChannel，必须被动监听对接！
                }
                @Override public void onSignalingChange(PeerConnection.SignalingState s) {} 
                @Override public void onIceConnectionChange(PeerConnection.IceConnectionState s) { 
                    log("=> 🌐 P2P 穿透网络状态: " + s); 
                    if(s == PeerConnection.IceConnectionState.CONNECTED) {
                        log("✅✅✅ 连接完全建立！2秒后自动返回游戏画面！");
                        // 🚀 核心需求实现：主机连上后自动隐藏联机大厅，切回游戏画面！
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            if (org.libsdl.app.SDLActivity.mSingleton != null) {
                                org.libsdl.app.SDLActivity.mSingleton.toggleDesktopMode(false);
                            }
                        }, 2000);
                    }
                } 
                @Override public void onIceConnectionReceivingChange(boolean b) {} @Override public void onIceGatheringChange(PeerConnection.IceGatheringState s) {} @Override public void onIceCandidatesRemoved(IceCandidate[] c) {} @Override public void onAddStream(MediaStream s) {} @Override public void onRemoveStream(MediaStream s) {} @Override public void onRenegotiationNeeded() {}
            });

            VideoCapturer screenCapturer = new ScreenCapturerAndroid(screenPermData, new MediaProjection.Callback() { @Override public void onStop() { super.onStop(); log("=> ⚠️ 录屏被系统强制中断"); } });
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext());
            VideoSource videoSource = factory.createVideoSource(screenCapturer.isScreencast());
            screenCapturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());
            
            int fps = 60; int w = 1280; int h = 720;
            if(quality == 0) { w = 1920; h = 1080; } else if(quality == 2) { w = 854; h = 480; }
            screenCapturer.startCapture(w, h, fps);

            localVideoTrack = factory.createVideoTrack("100", videoSource);
            peerConnection.addTrack(localVideoTrack);
            log("=> 🎥 本地视频推流器已挂载完成！");

            startSignalingListener(wanCode, customSignal, false);
            startSignalingListener(getLocalIpAddress(), customSignal, true);
        }

        public static void startClient(Context context, ViewGroup uiRoot, String targetCode, String customStun, String customSignal) {
            isHost = false; currentPeerTarget = targetCode;
            if (rootEglBase == null) rootEglBase = EglBase.create();

            PeerConnectionFactory.InitializationOptions initOptions = PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions();
            PeerConnectionFactory.initialize(initOptions);
            factory = PeerConnectionFactory.builder().createPeerConnectionFactory();

            List<PeerConnection.IceServer> iceServers = new ArrayList<>();
            iceServers.add(PeerConnection.IceServer.builder((customStun == null || customStun.isEmpty()) ? "stun:stun.l.google.com:19302" : customStun).createIceServer());

            peerConnection = factory.createPeerConnection(iceServers, new PeerConnection.Observer() {
                @Override public void onIceCandidate(IceCandidate iceCandidate) {
                    try { JSONObject out = new JSONObject(); out.put("sdpMid", iceCandidate.sdpMid); out.put("sdpMLineIndex", iceCandidate.sdpMLineIndex); out.put("candidate", iceCandidate.sdp); sendSignalingMessage(targetCode, "candidate", out, customSignal); } catch (Exception e){}
                }
                @Override public void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        log("=> 🎉 成功接收视频流轨道！正在摧毁联机大厅并全屏...");
                        try {
                            uiRoot.removeAllViews(); // 🚀 彻底摧毁大厅
                            
                            // 🚀 核心修复：安全嵌套防崩溃
                            FrameLayout videoContainer = new FrameLayout(context);
                            uiRoot.addView(videoContainer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

                            SurfaceViewRenderer videoView = new SurfaceViewRenderer(context);
                            videoView.init(rootEglBase.getEglBaseContext(), null);
                            videoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
                            videoView.setEnableHardwareScaler(true);
                            videoContainer.addView(videoView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                            
                            VideoTrack track = (VideoTrack) receiver.track();
                            track.addSink(videoView);
                            
                            buildClientGamepad(context, videoContainer);
                            log("=> 🎮 屏幕已被主机接管！尽情战斗吧！");
                        } catch (Exception e) { Log.e("IkemenWebRTC", "渲染错误: " + e.getMessage()); }
                    });
                }
                @Override public void onDataChannel(DataChannel channel) { } // 客户端主动创建，不需要监听
                @Override public void onSignalingChange(PeerConnection.SignalingState s) {} 
                @Override public void onIceConnectionChange(PeerConnection.IceConnectionState s) { log("=> 🌐 P2P 穿透网络状态: " + s); } 
                @Override public void onIceConnectionReceivingChange(boolean b) {} @Override public void onIceGatheringChange(PeerConnection.IceGatheringState s) {} @Override public void onIceCandidatesRemoved(IceCandidate[] c) {} @Override public void onAddStream(MediaStream s) {} @Override public void onRemoveStream(MediaStream s) {} @Override public void onRenegotiationNeeded() {}
            });

            // 🚀 核心修复 1：发起方主动建立数据通道！聊天和按键才能生效！
            DataChannel.Init dcInit = new DataChannel.Init();
            setupDataChannel(peerConnection.createDataChannel("IkemenData", dcInit));

            // 🚀 核心修复 2：强制声明接收视频！破解 WebRTC 单向流不发画面的陷阱！
            peerConnection.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, 
                new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY));

            boolean isTargetLan = targetCode.contains(".");
            startSignalingListener(targetCode, customSignal, isTargetLan);

            peerConnection.createOffer(new SimpleSdpObserver() {
                @Override public void onCreateSuccess(SessionDescription sessionDescription) {
                    peerConnection.setLocalDescription(new SimpleSdpObserver(), sessionDescription);
                    try { JSONObject out = new JSONObject(); out.put("sdp", sessionDescription.description); sendSignalingMessage(targetCode, "offer", out, customSignal); } catch(Exception e){}
                    log("=> ✉️ 请求入房握手 (Offer) 已发送，等待主机同意...");
                }
            }, new MediaConstraints());
        }

        private static void buildClientGamepad(Context ctx, ViewGroup root) {
            FrameLayout padLayout = new FrameLayout(ctx); float d = ctx.getResources().getDisplayMetrics().density;
            
            clientMiniLog = new android.widget.TextView(ctx);
            clientMiniLog.setTextColor(Color.GREEN); clientMiniLog.setTextSize(11f);
            clientMiniLog.setShadowLayer(4, 0, 0, Color.BLACK);
            FrameLayout.LayoutParams logLp = new FrameLayout.LayoutParams(-1, (int)(100*d));
            logLp.gravity = Gravity.TOP | Gravity.LEFT; logLp.setMargins((int)(20*d), (int)(50*d), (int)(20*d), 0);
            padLayout.addView(clientMiniLog, logLp);

            LinearLayout chatLayout = new LinearLayout(ctx); chatLayout.setOrientation(LinearLayout.HORIZONTAL);
            EditText chatInput = new EditText(ctx); chatInput.setHint("发送对战消息..."); chatInput.setBackgroundColor(Color.argb(180, 255, 255, 255));
            Button sendBtn = new Button(ctx); sendBtn.setText("发送");
            sendBtn.setOnClickListener(v -> { sendChatMessage(chatInput.getText().toString()); chatInput.setText(""); });
            chatLayout.addView(chatInput, new LinearLayout.LayoutParams(0, -2, 1f)); chatLayout.addView(sendBtn, new LinearLayout.LayoutParams(-2, -2));
            FrameLayout.LayoutParams chatLp = new FrameLayout.LayoutParams(-1, -2); chatLp.gravity = Gravity.TOP; chatLp.setMargins((int)(20*d), (int)(10*d), (int)(20*d), 0);
            padLayout.addView(chatLayout, chatLp);

            LinearLayout dpad = new LinearLayout(ctx); dpad.setOrientation(LinearLayout.VERTICAL); dpad.setGravity(Gravity.CENTER);
            Button btnUp = new Button(ctx); btnUp.setText("▲"); Button btnDown = new Button(ctx); btnDown.setText("▼");
            Button btnLeft = new Button(ctx); btnLeft.setText("◀"); Button btnRight = new Button(ctx); btnRight.setText("▶");
            LinearLayout midRow = new LinearLayout(ctx); midRow.addView(btnLeft); midRow.addView(new android.widget.Space(ctx), new LinearLayout.LayoutParams((int)(50*d),(int)(50*d))); midRow.addView(btnRight);
            dpad.addView(btnUp); dpad.addView(midRow); dpad.addView(btnDown);
            FrameLayout.LayoutParams lpLeft = new FrameLayout.LayoutParams(-2, -2); lpLeft.gravity = Gravity.BOTTOM | Gravity.LEFT; lpLeft.setMargins((int)(40*d),0,0,(int)(40*d));
            padLayout.addView(dpad, lpLeft);

            LinearLayout atkPad = new LinearLayout(ctx); atkPad.setOrientation(LinearLayout.VERTICAL);
            LinearLayout topRow = new LinearLayout(ctx); Button btnX = new Button(ctx); btnX.setText("X"); Button btnY = new Button(ctx); btnY.setText("Y"); Button btnZ = new Button(ctx); btnZ.setText("Z");
            topRow.addView(btnX); topRow.addView(btnY); topRow.addView(btnZ);
            LinearLayout botRow = new LinearLayout(ctx); Button btnA = new Button(ctx); btnA.setText("A"); Button btnB = new Button(ctx); btnB.setText("B"); Button btnC = new Button(ctx); btnC.setText("C");
            botRow.addView(btnA); botRow.addView(btnB); botRow.addView(btnC);
            atkPad.addView(topRow); atkPad.addView(botRow);
            FrameLayout.LayoutParams lpRight = new FrameLayout.LayoutParams(-2, -2); lpRight.gravity = Gravity.BOTTOM | Gravity.RIGHT; lpRight.setMargins(0,0,(int)(40*d),(int)(40*d));
            padLayout.addView(atkPad, lpRight);

            Button btnStart = new Button(ctx); btnStart.setText("START");
            FrameLayout.LayoutParams lpTop = new FrameLayout.LayoutParams(-2, -2); lpTop.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL; lpTop.setMargins(0,(int)(80*d),0,0);
            padLayout.addView(btnStart, lpTop);

            View.OnTouchListener sender = (v, event) -> {
                if (dataChannel == null || dataChannel.state() != DataChannel.State.OPEN) return false;
                Button b = (Button)v; String key = "";
                if(b==btnUp) key="UP"; else if(b==btnDown) key="DOWN"; else if(b==btnLeft) key="LEFT"; else if(b==btnRight) key="RIGHT";
                else if(b==btnA) key="A"; else if(b==btnB) key="B"; else if(b==btnC) key="C"; else if(b==btnX) key="X"; else if(b==btnY) key="Y"; else if(b==btnZ) key="Z"; else if(b==btnStart) key="START";
                
                try {
                    JSONObject msg = new JSONObject(); msg.put("btn", key);
                    if (event.getAction() == MotionEvent.ACTION_DOWN) msg.put("down", true);
                    else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) msg.put("down", false);
                    else return false;
                    java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(msg.toString().getBytes("UTF-8"));
                    dataChannel.send(new DataChannel.Buffer(buffer, false));
                } catch (Exception e) {} return false;
            };

            btnUp.setOnTouchListener(sender); btnDown.setOnTouchListener(sender); btnLeft.setOnTouchListener(sender); btnRight.setOnTouchListener(sender);
            btnA.setOnTouchListener(sender); btnB.setOnTouchListener(sender); btnC.setOnTouchListener(sender);
            btnX.setOnTouchListener(sender); btnY.setOnTouchListener(sender); btnZ.setOnTouchListener(sender); btnStart.setOnTouchListener(sender);
            
            padLayout.setAlpha(0.6f);
            root.addView(padLayout, new FrameLayout.LayoutParams(-1, -1));
        }

        public static String getLocalIpAddress() {
            String backupIp = "127.0.0.1";
            try {
                for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                    NetworkInterface intf = en.nextElement();
                    String name = intf.getName().toLowerCase();
                    for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                        InetAddress inetAddress = enumIpAddr.nextElement();
                        if (!inetAddress.isLoopbackAddress() && inetAddress.getAddress().length == 4) { 
                            String ip = inetAddress.getHostAddress();
                            if (name.contains("wlan") || name.contains("ap") || name.contains("softap")) { return ip; }
                            backupIp = ip; 
                        }
                    }
                }
            } catch (Exception ex) {} return backupIp;
        }

        public static class SimpleSdpObserver implements SdpObserver {
            @Override public void onCreateSuccess(SessionDescription sessionDescription) {} @Override public void onSetSuccess() {} @Override public void onCreateFailure(String s) {} @Override public void onSetFailure(String s) {}
        }
    }
} // 类的结尾

