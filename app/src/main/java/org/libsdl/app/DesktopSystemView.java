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

import api.Api;

public class DesktopSystemView extends Dialog {

    public static DesktopSystemView instance;
    
    private void updateUI(final TextView status, final String msg) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (status != null) status.setText(msg);
        });
    }

    public interface OnFileSelectedListener { void onFileSelected(File file); }

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
        TextView btnText = new TextView(getContext()); btnText.setText(" 进入游戏"); applyGlobalFontSettings(btnText, 1.2f, true); 
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
        createDesktopIcon("sys_settings", "⚙️", "系统控制台");
        createDesktopIcon("asset_extractor", "🖼️", "SFF查看器"); 
        createDesktopIcon("palette_editor", "🎨", "ACT色表工坊"); 
        createDesktopIcon("snd_extractor", "🎵", "SND查看器"); 
        createDesktopIcon("gif_extractor", "🎞️", "GIF拆解器"); 
        createDesktopIcon("stage_editor", "🗺️", "地图编辑器"); 
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
                            else if (id.equals("asset_extractor")) openAppWindow("🖼️ SFF查看器", buildSffExtractorContent(), null); 
                            else if (id.equals("palette_editor")) openAppWindow("🎨 ACT色表工坊", buildPaletteEditorContent(), null);
                            else if (id.equals("snd_extractor")) openAppWindow("🎵 SND查看器", buildSndExtractorContent(), null); 
                            else if (id.equals("gif_extractor")) openAppWindow("🎞️ GIF拆解器", buildGifExtractorContent(), null);
                            else if (id.equals("stage_editor")) openAppWindow("🗺️ 地图编辑器", buildStageEditorContent(), null);
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
        
        Button btnClose = createButton("❌ 强制关闭", "#E81123");
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
        
        String rawName = windowTitle.replace("🎨 检视: ", "").replace("📦 ", "").replace("🎵 检视: ", "").replace("🎞️ GIF拆解: ", "").trim();
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
                        reloadTypeface(); refreshDesktopBackground(); setupDesktopIcons(); Toast.makeText(getContext(), "✅ 设置已保存！", Toast.LENGTH_SHORT).show(); performClose.run();
                    },
                    () -> { 
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
        
        layout.addView(createTitle("🖥️ 桌面基础布局"));
        layout.addView(createSubTitle("桌面壁纸不透明度:"));
        SeekBar alphaBar = new SeekBar(getContext()); alphaBar.setMax(255); alphaBar.setProgress(bgAlpha);
        alphaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(SeekBar s, int p, boolean b) { bgAlpha = p; refreshDesktopBackground(); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){} }); layout.addView(alphaBar);
        
        layout.addView(createSubTitle("底部任务栏不透明度:"));
        SeekBar tbAlphaBar = new SeekBar(getContext()); tbAlphaBar.setMax(255); tbAlphaBar.setProgress(taskbarAlpha);
        tbAlphaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(SeekBar s, int p, boolean b) { taskbarAlpha = p; if (taskbar != null) taskbar.setBackgroundColor(Color.argb(p, 17, 17, 17)); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){} }); layout.addView(tbAlphaBar);

        layout.addView(createSubTitle("桌面网格间距:")); SeekBar gridBar = new SeekBar(getContext()); gridBar.setMax(250); gridBar.setProgress(gridSizeBase);
        gridBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(SeekBar s, int p, boolean b) { gridSizeBase = Math.max(60, p); rootLayer.invalidate(); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){ setupDesktopIcons(); } }); layout.addView(gridBar);

        Button gridToggle = createButton(showGrid ? "✔️ 网格辅助线：开启" : "❌ 网格辅助线：关闭", "#333333");
        gridToggle.setOnClickListener(v -> { showGrid = !showGrid; gridToggle.setText(showGrid ? "✔️ 网格辅助线：开启" : "❌ 网格辅助线：关闭"); rootLayer.invalidate(); }); layout.addView(gridToggle);

        layout.addView(createTitle("🅰️ 全局字体定制引擎"));
        final TextView fontLabel = createSubTitle("字体状态: " + (fontPath.isEmpty()?"系统默认":"已加载外部资源")); layout.addView(fontLabel);
        Button pickFont = createButton("📂 浏览本地选取字体文件 (.ttf/.otf)", "#444444"); pickFont.setOnClickListener(v -> showWin10FilePicker("选择字体文件", 3, fontLabel, scroll, null)); layout.addView(pickFont);

        layout.addView(createSubTitle("字体字号大小:"));
        SeekBar sizeBar = new SeekBar(getContext()); sizeBar.setMax(30); sizeBar.setProgress((int)fontSize);
        sizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(SeekBar s, int p, boolean b) { fontSize = Math.max(8, p); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){} }); layout.addView(sizeBar);

        layout.addView(createSubTitle("全局字体颜色代码 (Hex):"));
        final EditText colorInput = createInput("如: #FFFFFF", String.format("#%06X", (0xFFFFFF & fontColor))); layout.addView(colorInput);
        colorInput.addTextChangedListener(new TextWatcher() { public void afterTextChanged(Editable s) { try{ fontColor = Color.parseColor(s.toString()); }catch(Exception e){} } public void beforeTextChanged(CharSequence s, int x, int y, int z) {} public void onTextChanged(CharSequence s, int x, int y, int z) {} });

        Button shadowToggle = createButton(fontShadowEnabled ? "✔️ 字体投影：开启" : "❌ 字体投影：关闭", "#333333");
        shadowToggle.setOnClickListener(v -> { fontShadowEnabled = !fontShadowEnabled; shadowToggle.setText(fontShadowEnabled ? "✔️ 字体投影：开启" : "❌ 字体投影：关闭"); }); layout.addView(shadowToggle);

        layout.addView(createTitle("🎬 动态媒体矩阵 (优先读取窗口声音)"));
        layout.addView(createSubTitle("桌面壁纸视频音量:")); SeekBar bgVolBar = new SeekBar(getContext()); bgVolBar.setMax(100); bgVolBar.setProgress(bgMediaVolume);
        bgVolBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(SeekBar s, int p, boolean b) { bgMediaVolume = p; updateMediaVolumes(); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){} }); layout.addView(bgVolBar);

        layout.addView(createSubTitle("窗口壁纸视频音量:")); SeekBar winVolBar = new SeekBar(getContext()); winVolBar.setMax(100); winVolBar.setProgress(winMediaVolume);
        winVolBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(SeekBar s, int p, boolean b) { winMediaVolume = p; updateMediaVolumes(); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){} }); layout.addView(winVolBar);

        layout.addView(createSubTitle("多媒体渲染模式:")); Spinner scaleSpinner = new Spinner(getContext());
        ArrayAdapter<String> scaleAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, new String[]{"📏 强制拉伸填满", "✂️ 居中裁切填满", "🎯 保持原比例居中"});
        scaleSpinner.setAdapter(scaleAdapter); scaleSpinner.setSelection(mediaScaleMode); layout.addView(scaleSpinner);

        final TextView deskBgLabel = createSubTitle("桌面壁纸: " + (customDesktopBg.isEmpty()?"未配置":"已应用")); layout.addView(deskBgLabel);
        Button pickDesk = createButton("📂 浏览本地选取桌面动态壁纸", "#444444"); pickDesk.setOnClickListener(v -> showWin10FilePicker("选择桌面动态壁纸", 1, deskBgLabel, scroll, null)); layout.addView(pickDesk);

        final TextView winBgLabel = createSubTitle("窗口壁纸: " + (customWindowBg.isEmpty()?"未配置":"已应用")); layout.addView(winBgLabel);
        Button pickWin = createButton("📂 浏览本地选取窗口动态壁纸", "#444444"); pickWin.setOnClickListener(v -> showWin10FilePicker("选择窗口动态壁纸", 2, winBgLabel, scroll, null)); layout.addView(pickWin);

        Button saveBtn = createButton("💾 保存设置并应用", "#0078D7"); LinearLayout.LayoutParams btnP = new LinearLayout.LayoutParams(-1, -2); btnP.setMargins(0, (int)(30*density), 0, 0); saveBtn.setLayoutParams(btnP);
        saveBtn.setOnClickListener(v -> {
            prefs.edit().putInt("dt_bgAlpha", bgAlpha).putInt("dt_gridSize", gridSizeBase).putBoolean("dt_showGrid", showGrid).putString("dt_customDeskBg", customDesktopBg).putString("dt_customWinBg", customWindowBg).putInt("dt_bgMediaVol", bgMediaVolume).putInt("dt_winMediaVol", winMediaVolume).putInt("dt_taskbarAlpha", taskbarAlpha).putInt("dt_mediaScale", mediaScaleMode).putString("dt_fontPath", fontPath).putInt("dt_fontColor", fontColor).putFloat("dt_fontSize", fontSize).putBoolean("dt_fontShadow", fontShadowEnabled).putInt("dt_fontShadowC", fontShadowColor).apply();
            savedVideoPositionDesk = 0; savedVideoPositionWin = 0; reloadTypeface(); refreshDesktopBackground(); setupDesktopIcons(); Toast.makeText(getContext(), "✅ 设置已保存！", Toast.LENGTH_SHORT).show(); closeAction.run();
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
        TextView title = new TextView(getContext()); title.setText(" ⚠️ 未保存的更改"); applyGlobalFontSettings(title, 1.1f, true); title.setPadding((int)(10*density), (int)(8*density), 0, (int)(8*density)); titleBar.addView(title); box.addView(titleBar);
        View sep = new View(getContext()); sep.setBackgroundColor(Color.parseColor("#0078D7")); box.addView(sep, new LinearLayout.LayoutParams(-1, (int)(2*density)));
        
        TextView msg = new TextView(getContext()); msg.setText("检测到设置发生变更，是否保存？"); applyGlobalFontSettings(msg, 1.0f, false); msg.setPadding((int)(20*density), (int)(25*density), (int)(20*density), (int)(25*density)); box.addView(msg);
        
        LinearLayout btnRow = new LinearLayout(getContext()); btnRow.setOrientation(LinearLayout.HORIZONTAL); btnRow.setGravity(Gravity.RIGHT); btnRow.setPadding((int)(10*density), 0, (int)(10*density), (int)(15*density));
        Button bSave = createButton("💾 保存", "#0078D7"); bSave.setOnClickListener(v -> { pDialog.dismiss(); onSave.run(); });
        Button bDiscard = createButton("🗑️ 不保存", "#333333"); bDiscard.setOnClickListener(v -> { pDialog.dismiss(); onDiscard.run(); });
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
                pathView.setText("当前路径: " + lastVisitedDir.getAbsolutePath());
                
                Button goRoot = createButton("🏠 回到内部存储根目录", "#0078D7"); goRoot.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); goRoot.setPadding((int)(20*density), (int)(15*density), 0, (int)(15*density));
                goRoot.setOnClickListener(v -> { lastVisitedDir = Environment.getExternalStorageDirectory(); this.run(); }); listLayout.addView(goRoot);

                if (lastVisitedDir.getParentFile() != null) {
                    Button up = createButton("⬆️ 返回上一级文件夹", "#333333"); up.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); up.setPadding((int)(20*density), (int)(15*density), 0, (int)(15*density));
                    up.setOnClickListener(v -> { lastVisitedDir = lastVisitedDir.getParentFile(); this.run(); }); listLayout.addView(up);
                }

                if (targetType == 4) {
                    Button scanDirBtn = createButton("✔️ 深度扫描并提取本文件夹的 SFF 素材", "#4CAF50"); scanDirBtn.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); scanDirBtn.setPadding((int)(20*density), (int)(15*density), 0, (int)(15*density));
                    scanDirBtn.setOnClickListener(v -> { if (listener != null) listener.onFileSelected(lastVisitedDir); pDialog.dismiss(); }); listLayout.addView(scanDirBtn);
                } else if (targetType == 5) { 
                    Button scanDirBtn = createButton("✔️ 深度扫描并提取本文件夹的 SND 音频", "#FF9800"); scanDirBtn.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); scanDirBtn.setPadding((int)(20*density), (int)(15*density), 0, (int)(15*density));
                    scanDirBtn.setOnClickListener(v -> { if (listener != null) listener.onFileSelected(lastVisitedDir); pDialog.dismiss(); }); listLayout.addView(scanDirBtn);
                } else if (targetType == 6) {
                    Button scanDirBtn = createButton("✔️ 深度扫描并提取本文件夹的 GIF 动图", "#9C27B0"); scanDirBtn.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); scanDirBtn.setPadding((int)(20*density), (int)(15*density), 0, (int)(15*density));
                    scanDirBtn.setOnClickListener(v -> { if (listener != null) listener.onFileSelected(lastVisitedDir); pDialog.dismiss(); }); listLayout.addView(scanDirBtn);
                } else if (targetType == 7) {
                    Button scanDirBtn = createButton("✔️ 深度扫描本文件夹的外部图像素材", "#4CAF50"); scanDirBtn.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); scanDirBtn.setPadding((int)(20*density), (int)(15*density), 0, (int)(15*density));
                    scanDirBtn.setOnClickListener(v -> { if (listener != null) listener.onFileSelected(lastVisitedDir); pDialog.dismiss(); }); listLayout.addView(scanDirBtn);
                } else if (targetType == 8) {
                    Button scanDirBtn = createButton("✔️ 深度扫描本文件夹的外部音频素材", "#FF9800"); scanDirBtn.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); scanDirBtn.setPadding((int)(20*density), (int)(15*density), 0, (int)(15*density));
                    scanDirBtn.setOnClickListener(v -> { if (listener != null) listener.onFileSelected(lastVisitedDir); pDialog.dismiss(); }); listLayout.addView(scanDirBtn);
                } else if (targetType == 9) {
                    Button scanDirBtn = createButton("✔️ 深度扫描并提取本文件夹的 ACT 色表", "#0078D7"); scanDirBtn.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); scanDirBtn.setPadding((int)(20*density), (int)(15*density), 0, (int)(15*density));
                    scanDirBtn.setOnClickListener(v -> { if (listener != null) listener.onFileSelected(lastVisitedDir); pDialog.dismiss(); }); listLayout.addView(scanDirBtn);
                } else if (targetType == 10) {
                    Button scanDirBtn = createButton("✔️ 深度扫描本文件夹的地图工程 (.def)", "#E81123"); scanDirBtn.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); scanDirBtn.setPadding((int)(20*density), (int)(15*density), 0, (int)(15*density));
                    scanDirBtn.setOnClickListener(v -> { if (listener != null) listener.onFileSelected(lastVisitedDir); pDialog.dismiss(); }); listLayout.addView(scanDirBtn);
                } else if (targetType == 11) {
                    Button scanDirBtn = createButton("✔️ 深度扫描本文件夹的 3D 模型 (.gltf/.glb)", "#0078D7"); scanDirBtn.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL); scanDirBtn.setPadding((int)(20*density), (int)(15*density), 0, (int)(15*density));
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
                                    else Toast.makeText(getContext(), "❌ 请选择 .def 或 .sff", Toast.LENGTH_SHORT).show();
                                }
                                else if (targetType == 5) {
                                    if (absPath.toLowerCase().endsWith(".snd")) { if(listener != null) listener.onFileSelected(f); pDialog.dismiss(); } 
                                    else Toast.makeText(getContext(), "❌ 请选择 .snd 音频包", Toast.LENGTH_SHORT).show();
                                }
                                else if (targetType == 6) { 
                                    if (absPath.toLowerCase().endsWith(".gif")) { if(listener != null) listener.onFileSelected(f); pDialog.dismiss(); }
                                    else Toast.makeText(getContext(), "❌ 请选择 .gif 动画文件", Toast.LENGTH_SHORT).show();
                                }
                                else if (targetType == 7) { 
                                    if (absPath.toLowerCase().endsWith(".png") || absPath.toLowerCase().endsWith(".jpg") || absPath.toLowerCase().endsWith(".jpeg") || absPath.toLowerCase().endsWith(".gif") || absPath.toLowerCase().endsWith(".pcx")) { if(listener != null) listener.onFileSelected(f); pDialog.dismiss(); }
                                    else Toast.makeText(getContext(), "❌ 请选择图像文件用于替换 (支持 PNG/JPG/GIF/PCX)", Toast.LENGTH_SHORT).show();
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
                                    else Toast.makeText(getContext(), "❌ 请选择 Ikemen GO 支持的音频格式 (WAV/MP3/OGG/FLAC/XM等)", Toast.LENGTH_SHORT).show();
                                }
                                else if (targetType == 9) {
                                    if (absPath.toLowerCase().endsWith(".act")) { if(listener != null) listener.onFileSelected(f); pDialog.dismiss(); }
                                    else Toast.makeText(getContext(), "❌ 请选择 .act 调色板文件", Toast.LENGTH_SHORT).show();
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
                                        else { Toast.makeText(getContext(), "❌ 引擎拦截：这是一个人物包或无效配置，只能选择地图工程！", Toast.LENGTH_LONG).show(); }
                                    }
                                    else Toast.makeText(getContext(), "❌ 请选择 .def 地图配置文件", Toast.LENGTH_SHORT).show();
                                }
                                else if (targetType == 11) {
                                    String lowerPath = absPath.toLowerCase();
                                    if (lowerPath.endsWith(".gltf") || lowerPath.endsWith(".glb") || lowerPath.endsWith(".obj") || lowerPath.endsWith(".fbx") || lowerPath.endsWith(".3ds") || lowerPath.endsWith(".dae") || lowerPath.endsWith(".ply") || lowerPath.endsWith(".stl")) { 
                                        if(listener != null) listener.onFileSelected(f); pDialog.dismiss(); 
                                    } else {
                                        Toast.makeText(getContext(), "❌ 格式不支持，请选择支持的 3D 格式", Toast.LENGTH_SHORT).show();
                                    }
                                }
                                else if (targetType == 1 || targetType == 2) { 
                                    if(targetType == 1) customDesktopBg = absPath; else customWindowBg = absPath;
                                    if(labelRef != null) labelRef.setText("壁纸: " + f.getName()); refreshDesktopBackground(); pDialog.dismiss(); 
                                }
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
        TextView statusText = new TextView(getContext()); statusText.setText(" 状态: 等待选取目录或文件..."); applyGlobalFontSettings(statusText, 1.0f, false);
        Button scanBtn = createButton("📂 浏览并选择 SFF 素材文件", "#0078D7"); LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(-2, -2); btnParams.setMargins(0, 0, (int)(15*density), 0);
        topBar.addView(scanBtn, btnParams); topBar.addView(statusText); root.addView(topBar);

        ScrollView scroll = new ScrollView(getContext()); LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, -1); scrollParams.setMargins(0, (int)(15*density), 0, 0); scroll.setLayoutParams(scrollParams);
        final LinearLayout galleryLayout = new LinearLayout(getContext()); galleryLayout.setOrientation(LinearLayout.VERTICAL); scroll.addView(galleryLayout); root.addView(scroll);

        scanBtn.setOnClickListener(v -> { if (isAssetScannerRunning) return; currentGalleryLayout = galleryLayout; currentStatusText = statusText; showWin10FilePicker("选择目录或 .def/.sff 素材文件", 4, null, null, file -> startAssetScanner(file)); });
        return root;
    }

    private void startAssetScanner(File targetFile) {
        if (currentGalleryLayout != null) currentGalleryLayout.removeAllViews(); isAssetScannerRunning = true;
        
        new Thread(() -> {
            try { 
                updateUI(currentStatusText, "📡 阶段 1/3: 正在无限深度检索本地文件...");
                List<File> targetFiles = new ArrayList<>();
                if (targetFile.isDirectory()) { findFilesRecursively(targetFile, targetFiles, ".sff"); } 
                else { targetFiles.add(targetFile); }

                updateUI(currentStatusText, "📡 阶段 2/3: 触发底层 Go 引擎获取智能预览...");
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
                
                if(validAssets == null || validAssets.isEmpty()) { updateUI(currentStatusText, "⚠️ 未找到有效的 SFF 素材"); return; }
                
                updateUI(currentStatusText, "🖥️ 阶段 3/3: 预检完毕，正在渲染安全界面...");
                new Handler(Looper.getMainLooper()).post(() -> {
                    LinearLayout currentRow = null; int itemsInRow = 0;
                    for (GoEngineBridge.SffInfo va : validAssets) {
                        if (itemsInRow == 0) { currentRow = new LinearLayout(getContext()); currentRow.setOrientation(LinearLayout.HORIZONTAL); currentGalleryLayout.addView(currentRow, new LinearLayout.LayoutParams(-1, -2)); }
                        View card = buildAssetCard(va.name, va.filePath, va.preview, va.version);
                        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0, -2, 1f); cardParams.setMargins((int)(5*density), (int)(5*density), (int)(5*density), (int)(5*density));
                        currentRow.addView(card, cardParams); itemsInRow++; if (itemsInRow >= 3) itemsInRow = 0; 
                    }
                    currentStatusText.setText("✅ 解析完成! 成功挂载 " + validAssets.size() + " 个无损资源");
                });
            } catch (Exception e) { updateUI(currentStatusText, "扫描异常: " + e.getMessage()); } 
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
        
        Button exportBtn = createButton("👁️ 打开查看器", "#0078D7"); exportBtn.setPadding(0, (int)(5*density), 0, (int)(5*density));
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
        final String winTitle = "🎨 检视: " + charName;
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

        Button btnDefaultAct = createButton("🎨 内置色表", "#4CAF50");
        Button btnAutoAct = createButton("🪄 自动色表", "#9C27B0");
        Button btnManualAct = createButton("🎨 手动色表", "#9C27B0");
        Button btnGroup = createButton("📁 动作编组", "#1E1E1E"); // 新增：合并后的动作编组按键
        Button btnPrev = createButton("⏪ 上一帧", "#3F3F46"); 
        Button btnPlay = createButton("▶️ 播放", "#0078D7"); 
        Button btnNext = createButton("⏭️ 下一帧", "#3F3F46"); 
        Button btnSpeed = createButton("⚙️ 调速", "#3F3F46"); 
        Button btnExportNative = createButton("💾 原生导出", "#3F3F46"); 
        Button btnReplace = createButton("🔄 图像替换", "#4CAF50");
        
        final int[] currentDelay = {16}; 
        btnSpeed.setOnClickListener(v -> {
            final Dialog spdDialog = new Dialog(getContext()); spdDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            LinearLayout spdLayout = new LinearLayout(getContext()); spdLayout.setOrientation(LinearLayout.VERTICAL); spdLayout.setBackgroundColor(Color.parseColor("#2D2D30")); spdLayout.setPadding((int)(20*density), (int)(20*density), (int)(20*density), (int)(20*density));
            TextView title = new TextView(getContext()); title.setText("调整播放速度 (FPS)"); applyGlobalFontSettings(title, 1.0f, true); title.setGravity(Gravity.CENTER); spdLayout.addView(title);
            SeekBar speedBar = new SeekBar(getContext()); speedBar.setMax(59); speedBar.setProgress((1000/currentDelay[0])-1);
            speedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(SeekBar s, int p, boolean b) { currentDelay[0] = 1000 / (p + 1); title.setText("调整播放速度: " + (p+1) + " FPS"); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){} });
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
                            infoText.setText(String.format("帧: %d / %d | 动作: %d | 索引: %d | 尺寸: %dx%d | 轴心: %d, %d", currentFrameIndex[0] + 1, currentGroupFrames.size(), targetFrame.group, targetFrame.item, targetFrame.width, targetFrame.height, targetFrame.x, targetFrame.y));
                        });
                    } catch(Exception e){}
                }).start();
            }
        };

        btnDefaultAct.setOnClickListener(v -> {
            currentActPath[0] = "";
            Toast.makeText(getContext(), "✅ 已恢复内置色表", Toast.LENGTH_SHORT).show();
            updateFrameAction.run();
        });

        btnAutoAct.setOnClickListener(v -> {
            File[] actFiles = new File(sffPath).getParentFile().listFiles((d, name) -> name.toLowerCase().endsWith(".act"));
            if (actFiles != null && actFiles.length > 0) {
                currentActPath[0] = actFiles[0].getAbsolutePath();
                Toast.makeText(getContext(), "✅ 已自动挂载: " + actFiles[0].getName(), Toast.LENGTH_SHORT).show();
                updateFrameAction.run();
            } else {
                Toast.makeText(getContext(), "❌ 当前目录下未找到 .act 文件", Toast.LENGTH_SHORT).show();
            }
        });

        btnManualAct.setOnClickListener(v -> {
            showWin10FilePicker("选择 ACT 调色板", 9, null, null, selectedFile -> {
                currentActPath[0] = selectedFile.getAbsolutePath();
                Toast.makeText(getContext(), "✅ 已挂载: " + selectedFile.getName(), Toast.LENGTH_SHORT).show();
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
            title.setText("选择动作编组"); 
            applyGlobalFontSettings(title, 1.1f, true); 
            title.setGravity(Gravity.CENTER); 
            title.setPadding(0, 0, 0, (int)(10*density));
            gLayout.addView(title);

            // 【新增】动作组搜索栏
            EditText searchInput = createInput("🔍 输入动作组号搜索...", "");
            LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, -2);
            searchParams.setMargins(0, 0, 0, (int)(15*density));
            gLayout.addView(searchInput, searchParams);

            ScrollView gScroll = new ScrollView(getContext());
            final LinearLayout gList = new LinearLayout(getContext());
            gList.setOrientation(LinearLayout.VERTICAL);

            for (final int g : groupList) {
                String btnText = (g == -999) ? "📂 所有动作" : "📁 动作组 " + g;
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
            btnPlay.setText("▶️ 播放"); btnPlay.setBackgroundColor(Color.parseColor("#0078D7"));
            final GoEngineBridge.SffFrame f = currentGroupFrames.get(currentFrameIndex[0]);
            
            showWin10FilePicker("选择替换用的图像或所在目录", 7, null, null, selectedFile -> {
                FileCallback doReplace = finalFile -> {
                    new Thread(() -> {
                        // 补齐 Go 引擎强制要求的 axisX 和 axisY 轴心点参数
                        boolean success = Api.replaceSffFrame(sffPath, f.group, f.item, (short)f.x, (short)f.y, finalFile.getAbsolutePath());
                        uiHandler.post(() -> {
                            if (success) { Toast.makeText(getContext(), "✅ " + f.group + "-" + f.item + " 帧已替换！", Toast.LENGTH_SHORT).show(); updateFrameAction.run(); } 
                            else { Toast.makeText(getContext(), "❌ 替换失败：格式不兼容！SFFv1 只能使用 PCX，SFFv2 禁止使用 PCX！", Toast.LENGTH_LONG).show(); }
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
            btnPlay.setText(isPlaying[0] ? "⏸️ 暂停" : "▶️ 播放"); btnPlay.setBackgroundColor(isPlaying[0] ? Color.parseColor("#E81123") : Color.parseColor("#0078D7"));
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
            title.setText("选择导出格式");
            applyGlobalFontSettings(title, 1.1f, true);
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, 0, 0, (int)(15*density));
            fLayout.addView(title);
            
            Button btnPcx = createButton("💾 导出为 PCX (原生)", "#0078D7");
            Button btnPng = createButton("💾 导出为 PNG (通用)", "#4CAF50");
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
                                Toast.makeText(getContext(), "✅ 导出成功: " + outResult, Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(getContext(), "❌ 导出失败：解析异常或写入失败", Toast.LENGTH_LONG).show();
                            }
                        });
                    } catch (Exception e) {
                        uiHandler.post(() -> Toast.makeText(getContext(), "❌ 导出崩溃: " + e.getMessage(), Toast.LENGTH_LONG).show());
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
        TextView statusText = new TextView(getContext()); statusText.setText(" 状态: 等待选取 .snd 文件..."); applyGlobalFontSettings(statusText, 1.0f, false);
        Button scanBtn = createButton("📂 浏览并选择 SND 音频文件", "#FF9800"); LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(-2, -2); btnParams.setMargins(0, 0, (int)(15*density), 0);
        topBar.addView(scanBtn, btnParams); topBar.addView(statusText); root.addView(topBar);

        ScrollView scroll = new ScrollView(getContext()); LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, -1); scrollParams.setMargins(0, (int)(15*density), 0, 0); scroll.setLayoutParams(scrollParams);
        final LinearLayout listLayout = new LinearLayout(getContext()); listLayout.setOrientation(LinearLayout.VERTICAL); scroll.addView(listLayout); root.addView(scroll);

        scanBtn.setOnClickListener(v -> {
            currentGalleryLayout = listLayout; currentStatusText = statusText; showWin10FilePicker("选择 .snd 音频包", 5, null, null, file -> startSndScanner(file));
        });
        return root;
    }

    private void startSndScanner(File sndFile) {
        if (currentGalleryLayout != null) currentGalleryLayout.removeAllViews(); currentStatusText.setText("状态: 等待底层 Go 解析 SND...");
        new Thread(() -> {
            try {
                List<File> validFiles = new ArrayList<>();
                if (sndFile.isDirectory()) { findFilesRecursively(sndFile, validFiles, ".snd"); } else { validFiles.add(sndFile); }
                if (validFiles.isEmpty()) { updateUI(currentStatusText, "❌ 未找到SND文件"); return; }
                
                new Handler(Looper.getMainLooper()).post(() -> {
                    for(File f : validFiles) {
                        Button btn = createButton("🎵 打开: " + f.getName(), "#FF9800");
                        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2); bp.setMargins(0,0,0,(int)(10*density));
                        btn.setOnClickListener(v -> {
                            Toast.makeText(getContext(), "加载音频数据...", Toast.LENGTH_SHORT).show();
                            new Thread(() -> {
                                List<GoEngineBridge.SndNode> nodes = GoEngineBridge.scanSnd(f.getAbsolutePath());
                                new Handler(Looper.getMainLooper()).post(() -> showSndViewerWindow(f.getAbsolutePath(), f.getName(), nodes));
                            }).start();
                        });
                        currentGalleryLayout.addView(btn, bp);
                    }
                    currentStatusText.setText("✅ 共发现 " + validFiles.size() + " 个音频包");
                });
            } catch (Exception e) { updateUI(currentStatusText, "解析异常: " + e.getMessage()); }
        }).start();
    }

    private void showSndViewerWindow(String sndPath, String sndName, List<GoEngineBridge.SndNode> allNodes) {
        final String winTitle = "🎵 检视: " + sndName;
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

        Runnable refreshList = () -> {
            listLayout.removeAllViews(); int selectedGroup = currentSelectedGroup[0];
            if (allNodes != null) {
                for (GoEngineBridge.SndNode n : allNodes) {
                    if (selectedGroup != -999 && n.group != selectedGroup) continue;
                    LinearLayout row = new LinearLayout(getContext()); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding((int)(10*density), (int)(10*density), (int)(10*density), (int)(10*density));
                    GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#2D2D30")); bg.setCornerRadius(8f*density); row.setBackground(bg);
                    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2); rowParams.setMargins(0, 0, 0, (int)(8*density));
                    TextView info = new TextView(getContext()); info.setText(String.format("🎵 Group: %d | Item: %d", n.group, n.item)); applyGlobalFontSettings(info, 0.9f, false); row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));

                    Button btnPlay = createButton("▶️ 试听", "#FF9800"); btnPlay.setPadding((int)(15*density), (int)(8*density), (int)(15*density), (int)(8*density));
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
                    
                    Button btnExport = createButton("💾 导出", "#0078D7"); LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(-2, -2); btnParams.setMargins((int)(10*density), 0, 0, 0);
                    btnExport.setOnClickListener(v -> {
                        try {
                            byte[] wavData = Api.extractSndAudio(sndPath, n.group, n.item);
                            if(wavData != null) {
                                File outDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "IkemenExports"); if (!outDir.exists()) outDir.mkdirs();
                                File outFile = new File(outDir, sndName.replace(".snd", "") + "_G" + n.group + "_I" + n.item + ".wav"); FileOutputStream fos = new FileOutputStream(outFile); fos.write(wavData); fos.close();
                                Toast.makeText(getContext(), "✅ 已导出: " + outFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {}
                    });
                    
                                       Button btnReplace = createButton("🔄 替换", "#4CAF50");
                    btnReplace.setOnClickListener(v -> {
                        if (currentSndPlayer != null) { currentSndPlayer.release(); currentSndPlayer = null; }
                        showWin10FilePicker("选择替换用的音频或所在目录", 8, null, null, selectedFile -> {
                            FileCallback doReplace = finalFile -> {
                                new Thread(() -> {
                                    boolean success = Api.replaceSndAudio(sndPath, n.group, n.item, finalFile.getAbsolutePath());
                                    new Handler(Looper.getMainLooper()).post(() -> {
                                        if (success) { Toast.makeText(getContext(), "✅ " + n.group + "-" + n.item + " 音频已替换！", Toast.LENGTH_SHORT).show(); } 
                                        else { Toast.makeText(getContext(), "❌ 音频格式不支持，替换失败", Toast.LENGTH_LONG).show(); }
                                    });
                                }).start();
                            };
                            // 如果选的是文件夹，打开音频列表扫描器；如果是文件，直接替换
                            if (selectedFile.isDirectory()) showAudioListPicker(selectedFile, doReplace); else doReplace.onFileSelected(selectedFile);
                        });
                    });


                    row.addView(btnPlay); row.addView(btnExport, btnParams); row.addView(btnReplace, btnParams); listLayout.addView(row, rowParams);
                }
            }
        };

        for (final int g : groupList) {
            String btnText = (g == -999) ? "📂 所有音频" : "📁 音频组 " + g;
            Button groupBtn = createButton(btnText, "#1E1E1E");
            LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-2, -2); gp.setMargins(0, 0, (int)(5*density), 0);
            groupBtn.setOnClickListener(v -> {
                currentSelectedGroup[0] = g; refreshList.run();
            });
            groupToolBelt.addView(groupBtn, gp);
        }
        groupScroll.addView(groupToolBelt);
        root.addView(groupScroll);
        
        refreshList.run(); 

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
        TextView statusText = new TextView(getContext()); statusText.setText(" 状态: 等待选取 .gif 文件..."); applyGlobalFontSettings(statusText, 1.0f, false);
        Button scanBtn = createButton("📂 浏览并选择 GIF 文件", "#0078D7"); LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(-2, -2); btnParams.setMargins(0, 0, (int)(15*density), 0);
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
            showWin10FilePicker("选择 .gif 文件或所在目录", 6, null, null, file -> {
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
        currentStatusText.setText("状态: 正在检索本地 GIF...");
        new Thread(() -> {
            try {
                List<File> validFiles = new ArrayList<>();
                findFilesRecursively(gifDir, validFiles, ".gif");
                if (validFiles.isEmpty()) { updateUI(currentStatusText, "❌ 未找到 GIF 文件"); return; }
                
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
                    currentStatusText.setText("✅ 共发现 " + validFiles.size() + " 个 GIF 动画文件");
                });
            } catch (Exception e) { updateUI(currentStatusText, "解析异常: " + e.getMessage()); }
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
        
        Button exportBtn = createButton("👁️ 拆解查看", "#9C27B0"); 
        exportBtn.setPadding(0, (int)(5*density), 0, (int)(5*density));
        exportBtn.setOnClickListener(v -> openGifViewerWindow(gifFile));
        card.addView(exportBtn); 
        
        return card;
    }

    private void openGifViewerWindow(File gifFile) {
        final String winTitle = "🎞️ GIF拆解: " + gifFile.getName();
        LinearLayout root = new LinearLayout(getContext()); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#1E1E1E"));

        int totalFrames = (int) Api.getGifFrameCount(gifFile.getAbsolutePath());
        if (totalFrames <= 0) {
            Toast.makeText(getContext(), "❌ 无法解析此 GIF，或者该文件已损坏", Toast.LENGTH_SHORT).show(); return;
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
        
        Button btnPlay = createButton("▶️ 播放", "#0078D7"); Button btnExportCurr = createButton("💾 导出当前帧", "#FF9800"); Button btnExportAll = createButton("🚀 导出全部", "#9C27B0");
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
                        infoText.setText(String.format("帧: %d / %d", currentFrame[0] + 1, totalFrames));
                        frameSlider.setProgress(currentFrame[0]);
                    });
                }
            }).start();
        };

        frameSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { if(fromUser) { currentFrame[0] = progress; updateFrameAction.run(); } }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { isPlaying[0] = false; btnPlay.setText("▶️ 播放"); btnPlay.setBackgroundColor(Color.parseColor("#0078D7")); }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        Handler playHandler = new Handler();
        Runnable playRunnable = new Runnable() { @Override public void run() { if (isPlaying[0]) { currentFrame[0] = (currentFrame[0] + 1) % totalFrames; updateFrameAction.run(); playHandler.postDelayed(this, 100); } } };

        btnPlay.setOnClickListener(v -> {
            isPlaying[0] = !isPlaying[0];
            btnPlay.setText(isPlaying[0] ? "⏸️ 暂停" : "▶️ 播放"); btnPlay.setBackgroundColor(isPlaying[0] ? Color.parseColor("#E81123") : Color.parseColor("#0078D7"));
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
                        uiHandler.post(() -> Toast.makeText(getContext(), "✅ 已导出: " + outFile.getAbsolutePath(), Toast.LENGTH_SHORT).show());
                    } catch (Exception e) {}
                }
            }).start();
        });

        btnExportAll.setOnClickListener(v -> {
            Toast.makeText(getContext(), "🚀 开始批量无损导出，请稍候...", Toast.LENGTH_SHORT).show();
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
                uiHandler.post(() -> Toast.makeText(getContext(), "✅ 批量导出完成！保存至: " + outDir.getAbsolutePath(), Toast.LENGTH_LONG).show());
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

        Button btnLoadAct = createButton("📂 挂载色表", "#0078D7");
        Button btnLoadSff = createButton("🖼️ 挂载图像", "#9C27B0");
        Button btnMode = createButton("👁️ 预览模式", "#4CAF50");
        Button btnExtract = createButton("⬇️ 提取内置", "#FF9800");

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
        TextView txtFrameInfo = new TextView(getContext()); txtFrameInfo.setText(" 帧预览 "); applyGlobalFontSettings(txtFrameInfo, 0.9f, false); txtFrameInfo.setPadding((int)(10*density), 0, (int)(10*density), 0);
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
                btnMode.setText("👁️ 预览模式"); btnMode.setBackgroundColor(Color.parseColor("#4CAF50"));
                updateSffPreview.run();
            } catch (Exception e) { Toast.makeText(getContext(), "❌ 色表加载失败", Toast.LENGTH_SHORT).show(); }
        };

        for (int i=0; i<256; i++) {
            final int idx = i;
            colorBoxes[i].setOnClickListener(v -> {
                if (!isEditMode[0]) { Toast.makeText(getContext(), "👁️ 请先点击上方【预览模式】解锁修改", Toast.LENGTH_SHORT).show(); return; }
                if (currentActData[0] == null) { Toast.makeText(getContext(), "⚠️ 请先加载色表！", Toast.LENGTH_SHORT).show(); return; }
                showCleanDraggableRgbDialog(idx, currentActData[0], isRgbaFormat[0], colorBoxes[idx], currentActPath[0], updateSffPreview);
            });
        }

        btnLoadAct.setOnClickListener(v -> showWin10FilePicker("选择 .act 色表或目录", 9, null, null, file -> {
            if (file.isDirectory()) { showActGridPicker(file, selectedAct -> { currentActPath[0] = selectedAct.getAbsolutePath(); loadActToGrid.run(); }); } 
            else { currentActPath[0] = file.getAbsolutePath(); loadActToGrid.run(); }
        }));

        btnLoadSff.setOnClickListener(v -> showWin10FilePicker("选择 .sff 图像或目录", 4, null, null, file -> {
            if (file.isDirectory()) { showSffGridPicker(file, selectedSff -> { currentSffPath[0] = selectedSff.getAbsolutePath(); loadedFrames.clear(); previewIndex[0] = 0; isMatrixInitialized[0] = false; updateSffPreview.run(); }); } 
            else { currentSffPath[0] = file.getAbsolutePath(); loadedFrames.clear(); previewIndex[0] = 0; isMatrixInitialized[0] = false; updateSffPreview.run(); }
        }));

        btnMode.setOnClickListener(v -> {
            if (currentActPath[0].isEmpty()) { Toast.makeText(getContext(), "⚠️ 请先加载色表", Toast.LENGTH_SHORT).show(); return; }
            if (!isEditMode[0]) {
                if (!currentActPath[0].equals(originalActPath[0])) {
                    isEditMode[0] = true;
                    btnMode.setText("📝 修改模式 (当前: " + new File(currentActPath[0]).getName() + ")");
                    btnMode.setBackgroundColor(Color.parseColor("#E81123"));
                } else {
                    final Dialog prompt = new Dialog(getContext()); prompt.requestWindowFeature(Window.FEATURE_NO_TITLE); prompt.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                    LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setBackgroundColor(Color.parseColor("#252526")); box.setPadding((int)(20*density),(int)(20*density),(int)(20*density),(int)(20*density));
                    GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#252526")); border.setStroke((int)(2*density), Color.parseColor("#0078D7")); border.setCornerRadius(15*density); box.setBackground(border);
                    
                    TextView title = createSubTitle("🛡️ 安全修改提示"); title.setTextColor(Color.WHITE); box.addView(title);
                    TextView msg = new TextView(getContext()); msg.setText("即将进入修改模式。为了防止人物文件损坏，建议创建一个备份后再进行修改。"); msg.setTextColor(Color.LTGRAY); applyGlobalFontSettings(msg, 0.9f, false); msg.setPadding(0,(int)(10*density),0,(int)(20*density)); box.addView(msg);
                    
                    Button btnBackup = createButton("💾 自动创建防毁备份并修改", "#4CAF50");
                    btnBackup.setOnClickListener(bv -> {
                        try {
                            File orig = new File(originalActPath[0]); File backup = new File(orig.getParent(), orig.getName().replace(".act", "_backup.act"));
                            if (!backup.exists()) copyFileToSandbox(orig, backup);
                            currentActPath[0] = backup.getAbsolutePath(); 
                            isEditMode[0] = true; btnMode.setText("📝 修改模式 (已备份)"); btnMode.setBackgroundColor(Color.parseColor("#E81123"));
                            Toast.makeText(getContext(), "✅ 已切换至备份文件: " + backup.getName(), Toast.LENGTH_LONG).show();
                        } catch(Exception e){} prompt.dismiss();
                    });
                    
                    Button btnOrig = createButton("⚠️ 无视风险，直接修改原文件", "#FF9800");
                    btnOrig.setOnClickListener(bv -> { isEditMode[0] = true; btnMode.setText("📝 修改模式 (修改原文件)"); btnMode.setBackgroundColor(Color.parseColor("#E81123")); prompt.dismiss(); });
                    
                    Button btnCancel = createButton("❌ 取消", "#333333");
                    btnCancel.setOnClickListener(bv -> prompt.dismiss());
                    
                    LinearLayout.LayoutParams promptBp = new LinearLayout.LayoutParams(-1, -2); promptBp.setMargins(0,0,0,(int)(10*density));
                    box.addView(btnBackup, promptBp); box.addView(btnOrig, promptBp); box.addView(btnCancel, promptBp);
                    prompt.setContentView(box); prompt.show();
                }
            } else {
                isEditMode[0] = false;
                btnMode.setText("👁️ 预览模式"); btnMode.setBackgroundColor(Color.parseColor("#4CAF50"));
            }
        });

        btnExtract.setOnClickListener(v -> {
            if (currentSffPath[0].isEmpty()) { Toast.makeText(getContext(), "⚠️ 请先挂载 SFF 文件！", Toast.LENGTH_SHORT).show(); return; }
            new Thread(() -> {
                File origSff = new File(currentSffPath[0]); File extractAct = new File(origSff.getParent(), origSff.getName().replace(".sff", "_internal.act"));
                boolean success = Api.extractSffPalette(currentSffPath[0], extractAct.getAbsolutePath());
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (success) { Toast.makeText(getContext(), "✅ 提取成功: " + extractAct.getName() + "，请点击【挂载色表】加载它！", Toast.LENGTH_LONG).show(); } 
                    else { Toast.makeText(getContext(), "❌ 提取失败：该 SFF 无内置色表", Toast.LENGTH_LONG).show(); }
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

        TextView titleBar = new TextView(getContext()); titleBar.setText("✋ 调色板 (按住拖动)"); titleBar.setTextColor(Color.WHITE); titleBar.setPadding(20, 20, 20, 20); titleBar.setGravity(Gravity.CENTER);
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
        layout.addView(createSubTitle("🎨 代码 (Hex)")); layout.addView(hexInput);

        LinearLayout modeBar = new LinearLayout(getContext()); modeBar.setOrientation(LinearLayout.HORIZONTAL);
        Button btnModeRgb = createButton("RGB滑块", "#333333"); Button btnModeWheel = createButton("纯净色盘", "#333333"); Button btnModeDpad = createButton("十字微调", "#333333");
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
        viewRgb.addView(createSubTitle("🔴 红 (R)")); viewRgb.addView(barR); viewRgb.addView(createSubTitle("🟢 绿 (G)")); viewRgb.addView(barG); viewRgb.addView(createSubTitle("🔵 蓝 (B)")); viewRgb.addView(barB);

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
        TextView hint = new TextView(getContext()); hint.setText("☝️ 滑动提取任意中性色"); hint.setTextColor(Color.GRAY); hint.setGravity(Gravity.CENTER); viewWheel.addView(hint);

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
        Button btnVplus = createButton("🔼 变亮", "#555555"); btnVplus.setTag("V+"); btnVplus.setOnClickListener(dpadClick); row1.addView(btnVplus);
        LinearLayout row2 = new LinearLayout(getContext()); row2.setGravity(Gravity.CENTER);
        Button btnHminus = createButton("◀️ 色偏", "#555555"); btnHminus.setTag("H-"); btnHminus.setOnClickListener(dpadClick);
        Button btnSplus = createButton("⏺️ 加浓", "#E81123"); btnSplus.setTag("S+"); btnSplus.setOnClickListener(dpadClick);
        Button btnHplus = createButton("色偏 ▶️", "#555555"); btnHplus.setTag("H+"); btnHplus.setOnClickListener(dpadClick);
        row2.addView(btnHminus); row2.addView(btnSplus); row2.addView(btnHplus);
        LinearLayout row3 = new LinearLayout(getContext()); row3.setGravity(Gravity.CENTER);
        Button btnVminus = createButton("🔽 变暗", "#555555"); btnVminus.setTag("V-"); btnVminus.setOnClickListener(dpadClick); row3.addView(btnVminus);
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

        Button btnCancel = createButton("❌ 取消恢复", "#E81123");
        Button btnSave = createButton("💾 确认保存", "#4CAF50");

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
        
        TextView title = new TextView(getContext()); title.setText("正在极速扫描 SFF 并渲染头像，请稍候..."); title.setTextColor(Color.WHITE); applyGlobalFontSettings(title, 1.2f, true); box.addView(title);
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
                title.setText("✅ 扫描完成，请点击选择用于挂载的图像:");
                LinearLayout row = null; int count = 0;
                for(SffGridItem item : items) {
                    if(count == 0) { row = new LinearLayout(getContext()); row.setOrientation(LinearLayout.HORIZONTAL); grid.addView(row); }
                    LinearLayout card = new LinearLayout(getContext()); card.setOrientation(LinearLayout.VERTICAL); card.setGravity(Gravity.CENTER); card.setPadding((int)(10*density),(int)(10*density),(int)(10*density),(int)(10*density));
                    GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#2D2D30")); bg.setCornerRadius(10f); bg.setStroke(2, Color.parseColor("#3F3F46")); card.setBackground(bg);
                    ImageView iv = new ImageView(getContext()); iv.setLayoutParams(new LinearLayout.LayoutParams((int)(90*density), (int)(90*density))); iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    if(item.preview != null) iv.setImageBitmap(item.preview); else iv.setBackgroundColor(Color.DKGRAY); card.addView(iv);
                    TextView tv = new TextView(getContext()); tv.setText(item.info.name); tv.setTextColor(Color.WHITE); tv.setSingleLine(true); applyGlobalFontSettings(tv, 0.9f, false); card.addView(tv);
                    Button btn = createButton("✔️ 选择此项", "#4CAF50"); btn.setOnClickListener(v -> { listener.onFileSelected(new File(item.info.filePath)); d.dismiss(); }); card.addView(btn);
                    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f); cp.setMargins((int)(5*density),(int)(5*density),(int)(5*density),(int)(5*density)); row.addView(card, cp);
                    count++; if(count >= 3) count = 0;
                }
                Button closeBtn = createButton("❌ 取消并关闭", "#E81123"); closeBtn.setOnClickListener(v -> d.dismiss()); grid.addView(closeBtn);
            });
        }).start();
    }

    private void showActGridPicker(File dir, FileCallback listener) {
        Dialog d = new Dialog(getContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        d.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        FrameLayout overlay = new FrameLayout(getContext()); overlay.setBackgroundColor(Color.argb(230, 0,0,0));
        LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setPadding((int)(20*density), (int)(20*density), (int)(20*density), (int)(20*density));
        
        TextView title = new TextView(getContext()); title.setText("正在扫描 ACT 色表，请稍候..."); title.setTextColor(Color.WHITE); applyGlobalFontSettings(title, 1.2f, true); box.addView(title);
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
                title.setText("✅ 扫描完成，共找到 " + files.size() + " 个 ACT 色表文件:");
                for(File f : files) {
                    Button btn = createButton("🎨 " + f.getName() + "\n" + f.getParent(), "#0078D7");
                    btn.setOnClickListener(v -> { listener.onFileSelected(f); d.dismiss(); });
                    LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2); bp.setMargins(0,0,0,(int)(10*density));
                    grid.addView(btn, bp);
                }
                Button closeBtn = createButton("❌ 取消并关闭", "#E81123"); closeBtn.setOnClickListener(v -> d.dismiss()); grid.addView(closeBtn);
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
        
        TextView title = new TextView(getContext()); title.setText("正在生成图像缩略图，请稍候..."); title.setTextColor(Color.WHITE); applyGlobalFontSettings(title, 1.2f, true); box.addView(title);
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
                title.setText("✅ 扫描完成，请点击选择用于替换的图像:");
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
                    Button btn = createButton("✔️ 选择替换", "#4CAF50"); btn.setOnClickListener(v -> { listener.onFileSelected(f); d.dismiss(); }); card.addView(btn);
                    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f); cp.setMargins((int)(5*density),(int)(5*density),(int)(5*density),(int)(5*density)); row.addView(card, cp);
                    count++; if(count >= 3) count = 0;
                }
                Button closeBtn = createButton("❌ 取消并关闭", "#E81123"); closeBtn.setOnClickListener(v -> d.dismiss()); grid.addView(closeBtn);
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
        
        TextView title = new TextView(getContext()); title.setText("正在极速检索本地文件，请稍候..."); title.setTextColor(Color.WHITE); applyGlobalFontSettings(title, 1.2f, true); box.addView(title);
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
                title.setText("✅ 扫描完成，共找到 " + files.size() + " 个 " + winTitle + " :");
                for(File f : files) {
                    Button btn = createButton("📄 " + f.getName() + "\n" + f.getParent(), iconHex);
                    btn.setOnClickListener(v -> { listener.onFileSelected(f); d.dismiss(); });
                    LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2); bp.setMargins(0,0,0,(int)(10*density));
                    listLayout.addView(btn, bp);
                }
                Button closeBtn = createButton("❌ 取消并关闭", "#E81123"); closeBtn.setOnClickListener(v -> d.dismiss()); listLayout.addView(closeBtn);
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
        
        TextView title = new TextView(getContext()); title.setText("正在检索本地音频，请稍候..."); title.setTextColor(Color.WHITE); applyGlobalFontSettings(title, 1.2f, true); box.addView(title);
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
                title.setText("✅ 扫描完成，共找到 " + files.size() + " 个音频文件:");
                for(File f : files) {
                    LinearLayout row = new LinearLayout(getContext()); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding((int)(10*density), (int)(10*density), (int)(10*density), (int)(10*density));
                    GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#2D2D30")); bg.setCornerRadius(8f*density); row.setBackground(bg);
                    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2); rowParams.setMargins(0, 0, 0, (int)(8*density));
                    
                    TextView info = new TextView(getContext()); info.setText(f.getName() + "\n" + f.getParent()); applyGlobalFontSettings(info, 0.9f, false); info.setTextColor(Color.WHITE); row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));

                    Button btnPlay = createButton("▶️ 试听", "#FF9800"); 
                    btnPlay.setOnClickListener(v -> {
                        try {
                            if (currentSndPlayer != null) { currentSndPlayer.release(); currentSndPlayer = null; }
                            currentSndPlayer = new MediaPlayer(); currentSndPlayer.setDataSource(f.getAbsolutePath()); currentSndPlayer.prepare(); currentSndPlayer.start();
                        } catch (Exception e) { Toast.makeText(getContext(), "播放器不支持试听此编码 (但仍可强行注入)", Toast.LENGTH_SHORT).show(); }
                    });
                    
                    Button btnSelect = createButton("✔️ 选择", "#4CAF50"); LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(-2, -2); btnParams.setMargins((int)(10*density), 0, 0, 0);
                    btnSelect.setOnClickListener(v -> { 
                        if (currentSndPlayer != null) { currentSndPlayer.release(); currentSndPlayer = null; }
                        listener.onFileSelected(f); d.dismiss(); 
                    });

                    row.addView(btnPlay); row.addView(btnSelect, btnParams); listLayout.addView(row, rowParams);
                }
                Button closeBtn = createButton("❌ 取消并关闭", "#E81123"); 
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
            copy.name = this.name + " (副本)"; copy.isVisible = this.isVisible; copy.manuallyVisible = this.manuallyVisible; copy.isLocked = this.isLocked;
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
    
    private static class StageModelInfo { 
        String name; String path; boolean isVisible = true;
        float offsetX = 0; float offsetY = 0; float offsetZ = 0;
        float scaleX = 1; float scaleY = 1; float scaleZ = 1;
    }

    private interface AutoIncrementer { int[] getNext(int group, int item); }

    private View buildStageEditorContent() {
        LinearLayout root = new LinearLayout(getContext()); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#1E1E1E"));

        globalDefPath = ""; globalSffPath = ""; globalIsEditMode = false;
        final boolean[] is3DMode = {false}; final int[] currentViewMode = {0}; 
        final int[] gridAlpha = {70}; final int[] gridColor = {Color.WHITE}; final int[] bgColor = {Color.parseColor("#000080")};
        final float[] dpadStep = {1.0f};

        final List<StageLayerInfo> layerList = new ArrayList<>();
        final List<StageModelInfo> modelList = new ArrayList<>();
        final int[] selectedLayerIndex = {0}; 
        final StageLayerInfo[] clipboardLayer = {null};
        
        final Runnable[] refreshLayerListUI = {null}; final Runnable[] refreshModelListUI = {null};
        final Runnable[] updateViewState = {null};
        
        StageLayerInfo ghostGrid = new StageLayerInfo(); ghostGrid.name = "[系统] 蓝色参考网格"; ghostGrid.isGhostGrid = true; ghostGrid.isLocked = true; ghostGrid.isVisible = true; ghostGrid.manuallyVisible = true;
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
        Button btnScan = createButton("📂 工程", "#0078D7"); Button btnSave = createButton("💾 打包", "#4CAF50"); 
        Button btnMode2D = createButton("📐 2D 地图模式", "#9C27B0"); Button btnMode3D = createButton("🧊 3D 全屏工作室", "#333333");
        LinearLayout.LayoutParams topBp = new LinearLayout.LayoutParams(0, -2, 1f); topBp.setMargins(0, 0, padS, 0);
        topBar.addView(btnScan, topBp); topBar.addView(btnSave, topBp); topBar.addView(btnMode2D, topBp); topBar.addView(btnMode3D, topBp);
        root.addView(topBar);

        LinearLayout viewSwitchBar = new LinearLayout(getContext()); viewSwitchBar.setOrientation(LinearLayout.HORIZONTAL); viewSwitchBar.setGravity(Gravity.CENTER); viewSwitchBar.setPadding(0, padS, 0, padS); viewSwitchBar.setBackgroundColor(Color.parseColor("#1E1E1E"));
        Button btnViewSff = createButton("🖼️ 2D SFF 贴图视口", "#4CAF50"); Button btnViewDef = createButton("📝 DEF 代码编辑器", "#333333");
        viewSwitchBar.addView(btnViewSff, topBp); viewSwitchBar.addView(btnViewDef, topBp);
        root.addView(viewSwitchBar);

        LinearLayout mainArea = new LinearLayout(getContext()); mainArea.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout leftPanel = new LinearLayout(getContext()); leftPanel.setOrientation(LinearLayout.VERTICAL); leftPanel.setBackgroundColor(Color.parseColor("#2D2D30")); leftPanel.setPadding(padS, padS, padS, padS);
        
        final boolean[] isLayerMode = {false};

        LinearLayout titleRow = new LinearLayout(getContext()); titleRow.setOrientation(LinearLayout.HORIZONTAL); titleRow.setGravity(Gravity.CENTER_VERTICAL);
        Button btnModeAction = createButton("🎬 动作组", "#0078D7"); btnModeAction.setPadding((int)(8*density), (int)(5*density), (int)(8*density), (int)(5*density));
        Button btnModeLayer = createButton("📑 图层组", "#333333"); btnModeLayer.setPadding((int)(8*density), (int)(5*density), (int)(8*density), (int)(5*density));
        Button btnSettings = createButton("⚙️ 设置", "#3F3F46"); btnSettings.setPadding((int)(8*density), (int)(5*density), (int)(8*density), (int)(5*density));
        
        btnModeAction.setOnClickListener(v -> { isLayerMode[0] = false; btnModeAction.setBackgroundColor(Color.parseColor("#0078D7")); btnModeLayer.setBackgroundColor(Color.parseColor("#333333")); if(refreshLayerListUI[0] != null) refreshLayerListUI[0].run(); Toast.makeText(getContext(), "✅ 动作编组模式 (显示全景, 整体移动, 独立编号)", Toast.LENGTH_SHORT).show(); });
        btnModeLayer.setOnClickListener(v -> { isLayerMode[0] = true; btnModeAction.setBackgroundColor(Color.parseColor("#333333")); btnModeLayer.setBackgroundColor(Color.parseColor("#0078D7")); if(refreshLayerListUI[0] != null) refreshLayerListUI[0].run(); Toast.makeText(getContext(), "✅ 图层分组模式 (仅显当前编号, 独立移动, 同编号叠加)", Toast.LENGTH_SHORT).show(); });

        titleRow.addView(btnModeAction, new LinearLayout.LayoutParams(0, -2, 1f)); titleRow.addView(btnModeLayer, new LinearLayout.LayoutParams(0, -2, 1f)); titleRow.addView(btnSettings, new LinearLayout.LayoutParams(-2, -2));
        leftPanel.addView(titleRow);

        LinearLayout psToolsRow = new LinearLayout(getContext()); psToolsRow.setOrientation(LinearLayout.HORIZONTAL); psToolsRow.setPadding(0, padS, 0, padS);
        Button btnNewLayer = createButton("➕", "#4CAF50"); Button btnCopyLayer = createButton("📄", "#0078D7"); Button btnPasteLayer = createButton("📋", "#FF9800"); Button btnDelLayer = createButton("🗑️", "#E81123");
        LinearLayout.LayoutParams toolBp = new LinearLayout.LayoutParams(0, -2, 1f); toolBp.setMargins((int)(2*density), 0, (int)(2*density), 0);
        psToolsRow.addView(btnNewLayer, toolBp); psToolsRow.addView(btnCopyLayer, toolBp); psToolsRow.addView(btnPasteLayer, toolBp); psToolsRow.addView(btnDelLayer, toolBp);
        leftPanel.addView(psToolsRow);

        ScrollView layerScroll = new ScrollView(getContext()); final LinearLayout layerListLayout = new LinearLayout(getContext()); layerListLayout.setOrientation(LinearLayout.VERTICAL); layerScroll.addView(layerListLayout);
        leftPanel.addView(layerScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button btnImportMenu = createButton("📥 导入素材", "#FF9800"); leftPanel.addView(btnImportMenu, new LinearLayout.LayoutParams(-1, -2));

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
        Button btnModeToggle = createButton("🎬 动作", "#9C27B0"); Button btnEditGI = createButton("⚙️ 属性", "#3F3F46"); 
        TextView txtGI = new TextView(getContext()); txtGI.setText("  [0,0]  "); txtGI.setTextColor(Color.parseColor("#0078D7")); applyGlobalFontSettings(txtGI, 1.1f, true);
        TextView txtRes = new TextView(getContext()); txtRes.setText(" 📐 0x0 "); txtRes.setTextColor(Color.parseColor("#4CAF50")); applyGlobalFontSettings(txtRes, 1.0f, false);
        Button btnStep = createButton("👣 步长: 1", "#FF9800");

        btnModeToggle.setOnClickListener(clickMode -> {
            isLayerMode[0] = !isLayerMode[0]; btnModeToggle.setText(isLayerMode[0] ? "📑 图层" : "🎬 动作"); btnModeToggle.setBackgroundColor(Color.parseColor(isLayerMode[0] ? "#0078D7" : "#9C27B0")); Toast.makeText(getContext(), isLayerMode[0] ? "✅ 已切换至【图层模式】(方向键仅移动单层)" : "✅ 已切换至【动作编组模式】(方向键移动同组所有帧)", Toast.LENGTH_SHORT).show();
            if(selectedLayerIndex[0] > 0) { StageLayerInfo info = layerList.get(selectedLayerIndex[0]); txtGI.setText(String.format(isLayerMode[0] ? " 图层%d|G%d " : " [%d,%d] ", info.item, info.group, info.group, info.item)); }
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
            box.addView(createSubTitle("👣 设置方向键移动步长")); EditText stepInput = createInput("如: 1 或 10", String.valueOf((int)dpadStep[0])); stepInput.setInputType(InputType.TYPE_CLASS_NUMBER); box.addView(stepInput);
            LinearLayout btnRow = new LinearLayout(getContext()); btnRow.setOrientation(LinearLayout.HORIZONTAL);
            Button bSave = createButton("✔️ 保存", "#4CAF50"); bSave.setOnClickListener(clickSaveStep -> { try { dpadStep[0] = Float.parseFloat(stepInput.getText().toString()); btnStep.setText("👣 步长: " + (int)dpadStep[0]); } catch(Exception e){} stepDialog.dismiss(); });
            Button bReset = createButton("🔄 恢复默认", "#0078D7"); bReset.setOnClickListener(clickResetStep -> { dpadStep[0] = 1.0f; btnStep.setText("👣 步长: 1"); stepDialog.dismiss(); });
            Button bCancel = createButton("❌ 取消", "#333333"); bCancel.setOnClickListener(clickCancelStep -> stepDialog.dismiss());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f); lp.setMargins((int)(2*density), padM, (int)(2*density), 0); btnRow.addView(bSave, lp); btnRow.addView(bReset, lp); btnRow.addView(bCancel, lp); box.addView(btnRow); sv.addView(box); fl.addView(sv, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER)); stepDialog.setContentView(fl); stepDialog.show();
        });

        View.OnClickListener dpadListener = clickPad -> {
            if (selectedLayerIndex[0] == 0) { Toast.makeText(getContext(), "不能移动网格底板", Toast.LENGTH_SHORT).show(); return; }
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
        panel2D.addView(createSubTitle("📐 2D 图层属性")); 
        panel2D.addView(createSubTitle("Scale X,Y (缩放):")); EditText scale2D = createInput("1.0, 1.0", "1.0, 1.0"); panel2D.addView(scale2D);
        panel2D.addView(createSubTitle("Delta X,Y (视差):")); EditText delta2D = createInput("1.0, 1.0", "1.0, 1.0"); panel2D.addView(delta2D);
        panel2D.addView(createSubTitle("Trans (透明混合):")); EditText trans2D = createInput("add/sub/none", "none"); panel2D.addView(trans2D);
        Button btnApply2D = createButton("✔️ 应用 2D 参数", "#4CAF50");
        btnApply2D.setOnClickListener(clickApp2D -> {
            if (selectedLayerIndex[0] > 0) {
                StageLayerInfo layer = layerList.get(selectedLayerIndex[0]);
                try {
                    String[] sc = scale2D.getText().toString().split(","); layer.scaleX = Float.parseFloat(sc[0].trim()); layer.scaleY = Float.parseFloat(sc[1].trim());
                    String[] dl = delta2D.getText().toString().split(","); layer.deltaX = Float.parseFloat(dl[0].trim()); layer.deltaY = Float.parseFloat(dl[1].trim());
                    layer.trans = trans2D.getText().toString().trim();
                    viewportFrame.invalidate(); Toast.makeText(getContext(), "✅ 2D 参数已应用到当前图层", Toast.LENGTH_SHORT).show();
                } catch(Exception e){}
            }
        });
        panel2D.addView(btnApply2D); rightPanel.addView(panel2D); rightScroll.addView(rightPanel);

        mainArea.addView(leftPanel, new LinearLayout.LayoutParams(0, -1, 1.2f)); mainArea.addView(centerContainer, new LinearLayout.LayoutParams(0, -1, 2.5f)); mainArea.addView(rightScroll, new LinearLayout.LayoutParams(0, -1, 1f));
        root.addView(mainArea, new LinearLayout.LayoutParams(-1, -1));

        refreshModelListUI[0] = new Runnable() { @Override public void run() {} }; // 旧3D逻辑占位防崩溃

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
                    else if (!isLayerMode[0]) { int subCount = 0; for (StageLayerInfo l : layerList) { if (!l.isGhostGrid && l.group == info.group && l.item == info.item) subCount++; } tName.setText(String.format(" 🎬 动作 [%d,%d] (%d层)", info.group, info.item, subCount)); } 
                    else tName.setText(" 📄 " + info.name);
                    
                    tName.setTextColor(info.isGhostGrid ? Color.parseColor("#8888FF") : Color.WHITE); applyGlobalFontSettings(tName, 0.9f, false);
                    row.addView(btnVis, new LinearLayout.LayoutParams((int)(40*density), -2)); row.addView(bLock, new LinearLayout.LayoutParams((int)(40*density), -2)); row.addView(tName, new LinearLayout.LayoutParams(0, -2, 1f));
                    
                    row.setOnClickListener(clickRowLyr -> { 
                        selectedLayerIndex[0] = idx; txtCoord.setText(String.format(" X:%.0f Y:%.0f ", info.startX, info.startY));
                        if(!info.isGhostGrid) { txtGI.setText(String.format(isLayerMode[0] ? " 图层%d|G%d " : " [%d,%d] ", info.item, info.group)); txtRes.setText(String.format(" 📐 %dx%d ", info.origW, info.origH)); scale2D.setText(info.scaleX + ", " + info.scaleY); delta2D.setText(info.deltaX + ", " + info.deltaY); trans2D.setText(info.trans); } else { txtGI.setText(" [N/A] "); txtRes.setText(" 📐 N/A "); }
                        for (int j = 1; j < layerList.size(); j++) { StageLayerInfo l = layerList.get(j); if (j == idx) l.isVisible = true; else l.isVisible = l.manuallyVisible; }
                        if (!info.isGhostGrid && info.cacheBmp == null && !info.sourcePath.isEmpty()) {
                            new Thread(() -> { 
                                Bitmap bmp = null; int[] sizeInfo = new int[2];
                                if (!info.isExternal && info.sourcePath.toLowerCase().endsWith(".sff")) { byte[] bmpData = Api.decodeSffFrame(info.sourcePath, info.originalGroup, info.originalItem, ""); bmp = StageLayerInfo.safeDecode(null, bmpData, sizeInfo); } else bmp = StageLayerInfo.safeDecode(info.sourcePath, null, sizeInfo);
                                if (bmp != null) { info.origW = sizeInfo[0]; info.origH = sizeInfo[1]; final Bitmap fbm = bmp; new Handler(Looper.getMainLooper()).post(() -> { info.cacheBmp = fbm; viewportFrame.invalidate(); if (selectedLayerIndex[0] == idx) txtRes.setText(String.format(" 📐 %dx%d ", info.origW, info.origH)); }); } 
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
            if (selectedLayerIndex[0] == 0) { Toast.makeText(getContext(), "底板不可修改", Toast.LENGTH_SHORT).show(); return; }
            StageLayerInfo curLayer = layerList.get(selectedLayerIndex[0]);
            final Dialog d = new Dialog(getContext()); d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            FrameLayout fl = new FrameLayout(getContext()); ScrollView sv = new ScrollView(getContext()); LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setBackgroundColor(Color.parseColor("#252526")); box.setPadding(padM,padM,padM,padM);
            GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#252526")); border.setStroke((int)(2*density), Color.parseColor("#0078D7")); border.setCornerRadius(15*density); box.setBackground(border);
            box.addView(createSubTitle(isLayerMode[0] ? "⚙️ 调整图层属性" : "⚙️ 调整动作编组")); box.addView(createSubTitle("图层名称:")); EditText nameIn = createInput("", curLayer.name); box.addView(nameIn);
            box.addView(createSubTitle(isLayerMode[0] ? "所属动作组 (默认不可动):" : "Group 动作组号:")); EditText grpIn = createInput("", String.valueOf(curLayer.group)); grpIn.setInputType(InputType.TYPE_CLASS_NUMBER); box.addView(grpIn);
            box.addView(createSubTitle(isLayerMode[0] ? "图层层深编号 (Item):" : "Item 帧编号:")); EditText itmIn = createInput("", String.valueOf(curLayer.item)); itmIn.setInputType(InputType.TYPE_CLASS_NUMBER); box.addView(itmIn);
            Button bSave = createButton("✔️ 保存并智能排位", "#4CAF50");
            bSave.setOnClickListener(clickSGI -> {
                try {
                    curLayer.name = nameIn.getText().toString().trim(); int ng = Integer.parseInt(grpIn.getText().toString().trim()); int ni = Integer.parseInt(itmIn.getText().toString().trim());
                    StageLayerInfo conflict = null; for (StageLayerInfo l : layerList) { if (l != curLayer && !l.isGhostGrid && l.group == ng && l.item == ni) { conflict = l; break; } }
                    if (conflict != null) { int og = curLayer.group; int oi = curLayer.item; curLayer.group = ng; curLayer.item = ni; conflict.group = og; conflict.item = oi; Toast.makeText(getContext(), "🔄 检测到层深/帧号被占用，已互相交换", Toast.LENGTH_LONG).show(); } 
                    else { curLayer.group = ng; curLayer.item = ni; Toast.makeText(getContext(), "✅ 属性已更新", Toast.LENGTH_SHORT).show(); }
                    if (layerList.size() > 1) { StageLayerInfo ghost = layerList.remove(0); java.util.Collections.sort(layerList, new java.util.Comparator<StageLayerInfo>() { public int compare(StageLayerInfo a, StageLayerInfo b) { if (a.group != b.group) return Integer.compare(a.group, b.group); return Integer.compare(a.item, b.item); } }); layerList.add(0, ghost); selectedLayerIndex[0] = layerList.indexOf(curLayer); }
                    txtGI.setText(String.format(isLayerMode[0] ? " 图层%d|G%d " : " [%d,%d] ", curLayer.item, curLayer.group)); refreshLayerListUI[0].run(); d.dismiss();
                } catch(Exception e){}
            });
            Button bCancel = createButton("❌ 取消", "#333333"); bCancel.setOnClickListener(clickCGI -> d.dismiss());
            LinearLayout btnRow = new LinearLayout(getContext()); btnRow.setOrientation(LinearLayout.HORIZONTAL); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f); lp.setMargins((int)(2*density), (int)(10*density), (int)(2*density), 0); btnRow.addView(bSave, lp); btnRow.addView(bCancel, lp); box.addView(btnRow); sv.addView(box); fl.addView(sv, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER)); d.setContentView(fl); d.show();
        });

        Runnable openImageImporter = new Runnable() {
            @Override public void run() {
                showWin10FilePicker("选择 图片或GIF 目录", 7, null, null, fileImg -> {
                    FileCallback processImage = imgFile44 -> {
                        String lowerPath = imgFile44.getAbsolutePath().toLowerCase();
                        if (lowerPath.endsWith(".gif")) {
                            final Dialog dGif = new Dialog(getContext()); dGif.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); FrameLayout flGif = new FrameLayout(getContext()); ScrollView svGif = new ScrollView(getContext()); LinearLayout boxGif = new LinearLayout(getContext()); boxGif.setOrientation(LinearLayout.VERTICAL); boxGif.setBackgroundColor(Color.parseColor("#252526")); boxGif.setPadding(padM,padM,padM,padM); GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#252526")); border.setStroke((int)(2*density), Color.parseColor("#0078D7")); border.setCornerRadius(15*density); boxGif.setBackground(border); boxGif.addView(createSubTitle("🎞️ 检测到 GIF 动图")); TextView hint = new TextView(getContext()); hint.setText("由于 GIF 有多帧，请选择导入策略："); hint.setTextColor(Color.LTGRAY); boxGif.addView(hint);
                            long count = Api.getGifFrameCount(imgFile44.getAbsolutePath()); int totalFrames = (int)count; if(totalFrames <= 0) { Toast.makeText(getContext(), "❌ GIF 解析失败", Toast.LENGTH_SHORT).show(); dGif.dismiss(); return; }
                            ImageView previewGif = new ImageView(getContext()); previewGif.setLayoutParams(new LinearLayout.LayoutParams(-1, (int)(150*density))); boxGif.addView(previewGif);
                            final int[] currentFrame = {0}; final Bitmap[] currentBmp = {null}; final int[] curSize = new int[2];
                            Runnable updatePreviewGif = () -> { new Thread(() -> { byte[] b = Api.decodeGifFrame(imgFile44.getAbsolutePath(), currentFrame[0]); if(b != null) { currentBmp[0] = StageLayerInfo.safeDecode(null, b, curSize); new Handler(Looper.getMainLooper()).post(() -> previewGif.setImageBitmap(currentBmp[0])); } }).start(); };
                            TextView frameInfo = new TextView(getContext()); frameInfo.setTextColor(Color.WHITE); frameInfo.setGravity(Gravity.CENTER); boxGif.addView(frameInfo);
                            SeekBar slider = new SeekBar(getContext()); slider.setMax(totalFrames - 1); slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(SeekBar s, int p, boolean b) { if(b) { currentFrame[0] = p; frameInfo.setText("当前预览: 第 " + (p+1) + " / " + totalFrames + " 帧"); updatePreviewGif.run(); } } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){} }); boxGif.addView(slider); frameInfo.setText("当前预览: 第 1 / " + totalFrames + " 帧"); updatePreviewGif.run();
                            boxGif.addView(createSubTitle("导入名称前缀:")); EditText nameInput = createInput("", imgFile44.getName().replace(".gif","")); boxGif.addView(nameInput);
                            String defG2 = "0"; String defI2 = "0"; if (selectedLayerIndex[0] > 0 && selectedLayerIndex[0] < layerList.size()) { defG2 = String.valueOf(layerList.get(selectedLayerIndex[0]).group); defI2 = String.valueOf(layerList.get(selectedLayerIndex[0]).item); }
                            boxGif.addView(createSubTitle("所属 Group 编号:")); EditText groupInput = createInput("", isLayerMode[0] ? defG2 : "0"); groupInput.setInputType(InputType.TYPE_CLASS_NUMBER); boxGif.addView(groupInput);
                            boxGif.addView(createSubTitle("所属 Item 编号:")); EditText itemInput = createInput("", isLayerMode[0] ? defI2 : "0"); itemInput.setInputType(InputType.TYPE_CLASS_NUMBER); if (isLayerMode[0]) boxGif.addView(itemInput);
                            Button btnSingle = createButton(isLayerMode[0] ? "🎯 并入当前动作图层" : "🎯 导入为新动作", "#4CAF50");
                            btnSingle.setOnClickListener(clickSingleGif -> { StageLayerInfo layer = new StageLayerInfo(); layer.name = nameInput.getText().toString() + " [帧" + currentFrame[0] + "]"; int g = 0; try { g = Integer.parseInt(groupInput.getText().toString()); } catch(Exception e){} if (isLayerMode[0]) { int i = 0; try { i = Integer.parseInt(itemInput.getText().toString()); } catch(Exception e){} layer.group = g; layer.item = i; } else { int[] gi = incrementer.getNext(g, currentFrame[0]); layer.group = gi[0]; layer.item = gi[1]; } layer.origW = curSize[0]; layer.origH = curSize[1]; try { File tmpF = new File(getContext().getCacheDir(), "gif_ext_" + System.currentTimeMillis() + ".png"); FileOutputStream fos = new FileOutputStream(tmpF); currentBmp[0].compress(Bitmap.CompressFormat.PNG, 100, fos); fos.close(); layer.sourcePath = tmpF.getAbsolutePath(); layer.isExternal = true; } catch(Exception e){} layer.cacheBmp = currentBmp[0]; layer.isVisible = false; layer.manuallyVisible = false; layerList.add(layer); refreshLayerListUI[0].run(); dGif.dismiss(); Toast.makeText(getContext(), "✅ 已添加单帧图层", Toast.LENGTH_SHORT).show(); });
                            Button btnAll = createButton("🚀 瞬间拆解所有帧追加", "#9C27B0");
                            btnAll.setOnClickListener(clickAllGif -> { Toast.makeText(getContext(), "正在提取全部帧序列...", Toast.LENGTH_SHORT).show(); new Thread(() -> { int grp = 0; try { grp = Integer.parseInt(groupInput.getText().toString()); } catch(Exception e){} final int fGrp = grp; final String baseName = nameInput.getText().toString(); for (int i=0; i<totalFrames; i++) { byte[] b = Api.decodeGifFrame(imgFile44.getAbsolutePath(), i); if(b != null) { int[] sz = new int[2]; Bitmap bmp = StageLayerInfo.safeDecode(null, b, sz); StageLayerInfo layer = new StageLayerInfo(); layer.name = baseName + " [帧" + i + "]"; int[] gi = incrementer.getNext(fGrp, i); layer.group = gi[0]; layer.item = gi[1]; layer.origW = sz[0]; layer.origH = sz[1]; try { File tmpF = new File(getContext().getCacheDir(), "gif_ext_" + System.currentTimeMillis() + "_" + i + ".png"); FileOutputStream fos = new FileOutputStream(tmpF); bmp.compress(Bitmap.CompressFormat.PNG, 100, fos); fos.close(); layer.sourcePath = tmpF.getAbsolutePath(); layer.isExternal = true; } catch(Exception e){} layer.cacheBmp = bmp; layer.isVisible = false; layer.manuallyVisible = false; layerList.add(layer); } } new Handler(Looper.getMainLooper()).post(() -> { refreshLayerListUI[0].run(); dGif.dismiss(); Toast.makeText(getContext(), "✅ GIF 全部拆解并生成追加图层！", Toast.LENGTH_LONG).show(); }); }).start(); });
                            Button btnCancel = createButton("❌ 取消", "#333333"); btnCancel.setOnClickListener(clickCanGif -> dGif.dismiss()); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, (int)(10*density), 0, 0); boxGif.addView(btnSingle, lp); boxGif.addView(btnAll, lp); boxGif.addView(btnCancel, lp); svGif.addView(boxGif); flGif.addView(svGif, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER)); dGif.setContentView(flGif); dGif.show();
                        } else {
                            final Dialog dImg = new Dialog(getContext()); dImg.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); FrameLayout flImg = new FrameLayout(getContext()); ScrollView svImg = new ScrollView(getContext()); LinearLayout boxImg = new LinearLayout(getContext()); boxImg.setOrientation(LinearLayout.VERTICAL); boxImg.setBackgroundColor(Color.parseColor("#252526")); boxImg.setPadding(padM,padM,padM,padM); GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#252526")); border.setStroke((int)(2*density), Color.parseColor("#0078D7")); border.setCornerRadius(15*density); boxImg.setBackground(border); boxImg.addView(createSubTitle("🖼️ 导入单张静态图像")); ImageView previewImgView = new ImageView(getContext()); previewImgView.setLayoutParams(new LinearLayout.LayoutParams(-1, (int)(150*density))); int[] sizeInfo = new int[2]; Bitmap bmp = StageLayerInfo.safeDecode(imgFile44.getAbsolutePath(), null, sizeInfo); previewImgView.setImageBitmap(bmp); boxImg.addView(previewImgView); boxImg.addView(createSubTitle("自定义名称:")); EditText nameInput = createInput("", imgFile44.getName()); boxImg.addView(nameInput); String defG = "0"; String defI = "0"; if (selectedLayerIndex[0] > 0 && selectedLayerIndex[0] < layerList.size()) { defG = String.valueOf(layerList.get(selectedLayerIndex[0]).group); defI = String.valueOf(layerList.get(selectedLayerIndex[0]).item); } boxImg.addView(createSubTitle("设定 Group 编号:")); EditText groupInput = createInput("", isLayerMode[0] ? defG : "0"); groupInput.setInputType(InputType.TYPE_CLASS_NUMBER); boxImg.addView(groupInput); boxImg.addView(createSubTitle("设定 Item 编号:")); EditText itemInput = createInput("", isLayerMode[0] ? defI : "0"); itemInput.setInputType(InputType.TYPE_CLASS_NUMBER); boxImg.addView(itemInput); LinearLayout btnRow = new LinearLayout(getContext()); btnRow.setOrientation(LinearLayout.HORIZONTAL); Button btnAdd = createButton(isLayerMode[0] ? "✔️ 追加为当前图层" : "✔️ 独立追加新动作", "#4CAF50");
                            btnAdd.setOnClickListener(clickAddImg -> { StageLayerInfo layer = new StageLayerInfo(); layer.name = nameInput.getText().toString(); int g = 0; try { g = Integer.parseInt(groupInput.getText().toString()); } catch(Exception e){} int i = 0; try { i = Integer.parseInt(itemInput.getText().toString()); } catch(Exception e){} if (isLayerMode[0]) { layer.group = g; layer.item = i; } else { int[] gi = incrementer.getNext(g, i); layer.group = gi[0]; layer.item = gi[1]; } layer.origW = sizeInfo[0]; layer.origH = sizeInfo[1]; layer.sourcePath = imgFile44.getAbsolutePath(); layer.cacheBmp = bmp; layer.isVisible = false; layer.manuallyVisible = false; layer.isExternal = true; layerList.add(layer); refreshLayerListUI[0].run(); dImg.dismiss(); Toast.makeText(getContext(), "✅ 已追加新图层", Toast.LENGTH_SHORT).show(); });
                            Button btnCancel = createButton("❌ 取消", "#333333"); btnCancel.setOnClickListener(clickCanImg -> dImg.dismiss()); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f); lp.setMargins((int)(2*density), (int)(10*density), (int)(2*density), 0); btnRow.addView(btnAdd, lp); btnRow.addView(btnCancel, lp); boxImg.addView(btnRow); svImg.addView(boxImg); flImg.addView(svImg, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER)); dImg.setContentView(flImg); dImg.show();
                        }
                    };
                    if (fileImg.isDirectory()) showGenericFileListPicker(fileImg, new String[]{".png", ".jpg", ".jpeg", ".gif", ".pcx"}, "外部图像素材", "#4CAF50", processImage);
                    else processImage.onFileSelected(fileImg);
                });
            }
        };

btnImportMenu.setOnClickListener(clickImpMenu -> {
            final Dialog iDialog = new Dialog(getContext()); iDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); 
            FrameLayout flMenu = new FrameLayout(getContext()); ScrollView svMenu = new ScrollView(getContext()); 
            LinearLayout iBox = new LinearLayout(getContext()); iBox.setOrientation(LinearLayout.VERTICAL); iBox.setBackgroundColor(Color.parseColor("#252526")); iBox.setPadding(padM,padM,padM,padM); 
            GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#252526")); border.setStroke((int)(2*density), Color.parseColor("#0078D7")); border.setCornerRadius(15*density); iBox.setBackground(border); 
            iBox.addView(createSubTitle("📥 导入素材到场景")); 
            
            Button iSff = createButton("🖼️ 追加 SFF 官方图集包", "#FF9800");
            iSff.setOnClickListener(clickImpSff -> { iDialog.dismiss(); showWin10FilePicker("选择 SFF 文件或目录", 4, null, null, fileSff -> { FileCallback extractAction = finalFileSff -> { Toast.makeText(getContext(), "正在提取 SFF 并追加图层...", Toast.LENGTH_SHORT).show(); String currentPath = finalFileSff.getAbsolutePath(); new Thread(() -> { List<GoEngineBridge.SffFrame> frames = GoEngineBridge.getAllFrames(currentPath); new Handler(Looper.getMainLooper()).post(() -> { for (GoEngineBridge.SffFrame f : frames) { StageLayerInfo layer = new StageLayerInfo(); layer.name = "Sprite [" + f.group + ", " + f.item + "]"; int[] gi = incrementer.getNext(f.group, f.item); layer.group = gi[0]; layer.item = gi[1]; layer.originalGroup = f.group; layer.originalItem = f.item; layer.origW = f.width; layer.origH = f.height; layer.axisX = f.x; layer.axisY = f.y; layer.sourcePath = currentPath; layer.isVisible = false; layer.manuallyVisible = false; layer.isExternal = false; layerList.add(layer); } refreshLayerListUI[0].run(); Toast.makeText(getContext(), "✅ SFF 解析完成！追加提取了 " + frames.size() + " 个图层", Toast.LENGTH_LONG).show(); }); }).start(); }; if (fileSff.isDirectory()) showSffGridPicker(fileSff, extractAction); else extractAction.onFileSelected(fileSff); }); });
            iBox.addView(iSff, new LinearLayout.LayoutParams(-1, -2)); 
            
            Button iImg = createButton("🖼️ 追加 单张图片 / GIF 动画", "#4CAF50"); 
            LinearLayout.LayoutParams lpImg = new LinearLayout.LayoutParams(-1, -2); lpImg.setMargins(0,(int)(10*density),0,0); 
            iImg.setOnClickListener(clickImpImg -> { iDialog.dismiss(); openImageImporter.run(); }); 
            iBox.addView(iImg, lpImg); 

            // 🌟 永远允许导入 3D 模型，导入后进入 3D 全屏沙盘即可布置
            Button iMod = createButton("🧊 导入 3D 模型 (.glb / .gltf)", "#0078D7");
            iMod.setOnClickListener(clickImpMod -> { 
                iDialog.dismiss(); 
                showWin10FilePicker("选择 3D 模型", 11, null, null, fileMod -> {
                    FileCallback onSelected = fMod -> {
                        StageModelInfo m = new StageModelInfo(); m.name = fMod.getName(); m.path = fMod.getAbsolutePath(); modelList.add(m); refreshModelListUI[0].run();
                        Toast.makeText(getContext(), "✅ 3D模型已添加入列！请进入【🧊 3D 全屏工作室】进行摆放", Toast.LENGTH_LONG).show();
                    };
                    if(fileMod.isDirectory()) showGenericFileListPicker(fileMod, new String[]{".gltf", ".glb"}, "3D模型", "#0078D7", onSelected); else onSelected.onFileSelected(fileMod);
                }); 
            });
            iBox.addView(iMod, lpImg);

            Button iCancel = createButton("❌ 取消", "#333333"); 
            iCancel.setOnClickListener(clickImpCan -> iDialog.dismiss()); 
            iBox.addView(iCancel, lpImg); 
            
            svMenu.addView(iBox); flMenu.addView(svMenu, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER)); iDialog.setContentView(flMenu); iDialog.show();
        });

        btnSettings.setOnClickListener(clickSet -> {
            final Dialog setDialog = new Dialog(getContext()); setDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); FrameLayout flSet = new FrameLayout(getContext()); ScrollView svSet = new ScrollView(getContext()); LinearLayout setBox = new LinearLayout(getContext()); setBox.setOrientation(LinearLayout.VERTICAL); setBox.setBackgroundColor(Color.parseColor("#252526")); setBox.setPadding(padM,padM,padM,padM); GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#252526")); border.setStroke((int)(2*density), Color.parseColor("#0078D7")); border.setCornerRadius(15*density); setBox.setBackground(border); setBox.addView(createTitle("⚙️ 视口偏好设置")); setBox.addView(createSubTitle("背景底色代码 (Hex):")); EditText bgInput = createInput("如: #000080", String.format("#%06X", (0xFFFFFF & bgColor[0]))); setBox.addView(bgInput); setBox.addView(createSubTitle("网格线颜色代码 (Hex):")); EditText gridColorInput = createInput("如: #FFFFFF", String.format("#%06X", (0xFFFFFF & gridColor[0]))); setBox.addView(gridColorInput); setBox.addView(createSubTitle("网格透明度 (0-255):")); SeekBar alphaBar = new SeekBar(getContext()); alphaBar.setMax(255); alphaBar.setProgress(gridAlpha[0]); alphaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(SeekBar s, int p, boolean b) { gridAlpha[0] = p; viewportFrame.invalidate(); } public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {} }); setBox.addView(alphaBar); Button btnApply = createButton("✔️ 应用设置", "#4CAF50"); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0,(int)(15*density),0,0); btnApply.setOnClickListener(clickAppSet -> { try { bgColor[0] = Color.parseColor(bgInput.getText().toString()); gridColor[0] = Color.parseColor(gridColorInput.getText().toString()); } catch (Exception e){} viewportFrame.invalidate(); setDialog.dismiss(); }); setBox.addView(btnApply, lp); svSet.addView(setBox); flSet.addView(svSet, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER)); setDialog.setContentView(flSet); setDialog.show();
        });

        btnScan.setOnClickListener(clickScan -> {
            final Dialog prompt = new Dialog(getContext()); prompt.requestWindowFeature(Window.FEATURE_NO_TITLE); prompt.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); FrameLayout flScan = new FrameLayout(getContext()); ScrollView svScan = new ScrollView(getContext()); LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setBackgroundColor(Color.parseColor("#252526")); box.setPadding(padM,padM,padM,padM); GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#252526")); border.setStroke((int)(2*density), Color.parseColor("#0078D7")); border.setCornerRadius(15*density); box.setBackground(border); box.addView(createSubTitle("📂 工程读取与新建")); Button btnNew = createButton("📄 新建空白地图", "#4CAF50"); btnNew.setOnClickListener(clickNewScan -> { globalDefPath = ""; globalIsEditMode = false; layerList.clear(); layerList.add(ghostGrid); refreshLayerListUI[0].run(); modelList.clear(); if(refreshModelListUI[0]!=null)refreshModelListUI[0].run(); defCodeInput.setText("[Info]\nname = \"NewStage\"\n\n[BGdef]\nspr = stages/NewStage.sff\ndebugbg = 0"); Toast.makeText(getContext(), "已建立新工程", Toast.LENGTH_SHORT).show(); prompt.dismiss(); }); Button btnLoad = createButton("📂 读取现有 .def 地图", "#0078D7");
            btnLoad.setOnClickListener(clickLoadScan -> { prompt.dismiss(); showWin10FilePicker("选择 .def 地图工程", 10, null, null, selectedFileScan -> { FileCallback loadDefAction = finalDef36 -> { final Dialog safePrompt = new Dialog(getContext()); safePrompt.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); FrameLayout flSafe = new FrameLayout(getContext()); ScrollView svSafe = new ScrollView(getContext()); LinearLayout safeBox = new LinearLayout(getContext()); safeBox.setOrientation(LinearLayout.VERTICAL); safeBox.setBackgroundColor(Color.parseColor("#252526")); safeBox.setPadding(padM,padM,padM,padM); safeBox.setBackground(border); safeBox.addView(createSubTitle("🛡️ 安全加载选项")); Runnable performLoad = () -> { try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(globalDefPath))) { StringBuilder sb = new StringBuilder(); String line; modelList.clear(); while ((line = br.readLine()) != null) { sb.append(line).append("\n"); String lowerLine = line.toLowerCase().trim(); if ((lowerLine.contains(".glb") || lowerLine.contains(".gltf")) && line.contains("=")) { try { String mFile = line.substring(line.indexOf("=") + 1).replace("\"", "").trim(); File fM = new File(new File(globalDefPath).getParent(), mFile); if(fM.exists()) { StageModelInfo sm = new StageModelInfo(); sm.name = fM.getName(); sm.path = fM.getAbsolutePath(); modelList.add(sm); } } catch(Exception ignored){} } } defCodeInput.setText(sb.toString()); if(refreshModelListUI[0]!=null)refreshModelListUI[0].run(); } catch (Exception e) {} File targetDef = new File(globalDefPath); File sffFile = new File(targetDef.getParent(), targetDef.getName().replace(".def", ".sff")); if (!sffFile.exists()) sffFile = new File(targetDef.getParent(), targetDef.getName().replace(".def", ".SFF")); if (sffFile.exists()) { globalSffPath = sffFile.getAbsolutePath(); new Thread(() -> { List<GoEngineBridge.SffFrame> frames = GoEngineBridge.getAllFrames(globalSffPath); new Handler(Looper.getMainLooper()).post(() -> { layerList.clear(); layerList.add(ghostGrid); for (GoEngineBridge.SffFrame f : frames) { StageLayerInfo layer = new StageLayerInfo(); layer.name = "Sprite [" + f.group + ", " + f.item + "]"; layer.group = f.group; layer.item = f.item; layer.originalGroup = f.group; layer.originalItem = f.item; layer.origW = f.width; layer.origH = f.height; layer.axisX = f.x; layer.axisY = f.y; layer.sourcePath = globalSffPath; layer.isExternal=false; layer.isVisible=false; layer.manuallyVisible=false; layerList.add(layer); } refreshLayerListUI[0].run(); Toast.makeText(getContext(), "✅ 已载入 " + frames.size() + " 个素材图层与关联模型", Toast.LENGTH_LONG).show(); }); }).start(); } }; Button btnBackup = createButton("💾 自动防毁备份并读取", "#4CAF50"); btnBackup.setOnClickListener(clickBackSafe -> { try { File backup = new File(finalDef36.getParent(), finalDef36.getName().replace(".def", "_backup.def")); if (!backup.exists()) copyFileToSandbox(finalDef36, backup); globalDefPath = backup.getAbsolutePath(); globalIsEditMode = true; Toast.makeText(getContext(), "✅ 已切换至备份工程: " + backup.getName(), Toast.LENGTH_SHORT).show(); performLoad.run(); } catch(Exception e){} safePrompt.dismiss(); }); Button btnOrig = createButton("⚠️ 无视风险直接读取", "#FF9800"); btnOrig.setOnClickListener(clickOrigSafe -> { globalDefPath = finalDef36.getAbsolutePath(); globalIsEditMode = true; performLoad.run(); safePrompt.dismiss(); }); Button btnCancel = createButton("❌ 取消", "#333333"); btnCancel.setOnClickListener(clickCanSafe -> safePrompt.dismiss()); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0,0,0,(int)(10*density)); safeBox.addView(btnBackup, lp); safeBox.addView(btnOrig, lp); safeBox.addView(btnCancel, lp); svSafe.addView(safeBox); flSafe.addView(svSafe, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER)); safePrompt.setContentView(flSafe); safePrompt.show(); }; if (selectedFileScan.isDirectory()) showGenericFileListPicker(selectedFileScan, new String[]{".def"}, "地图工程", "#E81123", loadDefAction); else loadDefAction.onFileSelected(selectedFileScan); }); }); Button btnCancelMain = createButton("❌ 取消", "#333333"); btnCancelMain.setOnClickListener(clickCanScan -> prompt.dismiss()); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0,0,0,(int)(10*density)); box.addView(btnNew, lp); box.addView(btnLoad, lp); box.addView(btnCancelMain, lp); svScan.addView(box); flScan.addView(svScan, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER)); prompt.setContentView(flScan); prompt.show();
        });

        btnSave.setOnClickListener(clickSaveMain -> {
            final Dialog exportDialog = new Dialog(getContext()); exportDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); FrameLayout flExp = new FrameLayout(getContext()); ScrollView svExp = new ScrollView(getContext()); LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setBackgroundColor(Color.parseColor("#252526")); box.setPadding(padM,padM,padM,padM); GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#252526")); border.setStroke((int)(2*density), Color.parseColor("#0078D7")); border.setCornerRadius(15*density); box.setBackground(border); box.addView(createSubTitle("💾 执行打包导出 (仅限 2D)")); String defaultName = "NewStage"; if (globalIsEditMode && !globalDefPath.isEmpty()) defaultName = new File(globalDefPath).getName().replace(".def", ""); box.addView(createSubTitle("地图导出前缀名:")); EditText nameInput = createInput("(默认追加递增防重名)", defaultName); box.addView(nameInput);
            Button bConfirm = createButton("✔️ 确认合并栅格化导出", "#4CAF50");
            bConfirm.setOnClickListener(clickConfSave -> {
                exportDialog.dismiss(); Toast.makeText(getContext(), "📦 引擎正在合并栅格化导出中...", Toast.LENGTH_SHORT).show();
                new Thread(() -> {
                    try {
                        String baseName = nameInput.getText().toString().trim(); if (baseName.isEmpty()) baseName = "NewStage"; File rootExportDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "IkemenExports"); File tempDir = new File(rootExportDir, baseName); int counter = 1; while (tempDir.exists()) { tempDir = new File(rootExportDir, baseName + "_" + counter); counter++; } tempDir.mkdirs(); final File finalExportDir = tempDir; 
                        String rawDef = defCodeInput.getText().toString(); String cleanedDef = rawDef.replaceAll("(?i)\\[BG\\s+.*?\\][\\s\\S]*?(?=\\[|$)", ""); cleanedDef = cleanedDef.replaceAll("(?i)\\[BG\\][\\s\\S]*?(?=\\[|$)", ""); cleanedDef += "\n[BG FlattenedBackground]\ntype = normal\nspriteno = 0, 0\nstart = 0, 0\nmask = 1\n"; File defFile = new File(finalExportDir, baseName + ".def"); FileOutputStream defOut = new FileOutputStream(defFile); defOut.write(cleanedDef.getBytes("UTF-8")); defOut.close();
                        File finalSffFile = new File(finalExportDir, baseName + ".sff"); if (!globalSffPath.isEmpty()) { copyFileToSandbox(new File(globalSffPath), finalSffFile); } else { finalSffFile.createNewFile(); } 
                        if (finalSffFile.length() > 0) { List<GoEngineBridge.SffFrame> origFrames = GoEngineBridge.getAllFrames(finalSffFile.getAbsolutePath()); for (GoEngineBridge.SffFrame f : origFrames) { Api.deleteSffFrame(finalSffFile.getAbsolutePath(), f.group, f.item); } }
                        float minX = 99999, minY = 99999, maxX = -99999, maxY = -99999; boolean hasContent = false;
                        for (StageLayerInfo layer : layerList) { if (layer.isGhostGrid || layer.origW == 0 || (!layer.isVisible && !layer.manuallyVisible)) continue; float left = layer.startX - layer.axisX; float top = layer.startY - layer.axisY; float right = left + layer.origW * Math.abs(layer.scaleX); float bottom = top + layer.origH * Math.abs(layer.scaleY); if (left < minX) minX = left; if (top < minY) minY = top; if (right > maxX) maxX = right; if (bottom > maxY) maxY = bottom; hasContent = true; }
                        if (hasContent) {
                            int outW = (int) Math.ceil(maxX - minX); int outH = (int) Math.ceil(maxY - minY); if (outW <= 0) outW = 1; if (outH <= 0) outH = 1; Bitmap mergedBmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888); Canvas mergedCanvas = new Canvas(mergedBmp);
                            for (int l_idx = 1; l_idx < layerList.size(); l_idx++) {
                                StageLayerInfo layer = layerList.get(l_idx); if (layer.isGhostGrid || layer.origW == 0 || (!layer.isVisible && !layer.manuallyVisible)) continue;
                                try { Bitmap layerFullBmp = null; if (layer.isExternal && layer.sourcePath != null && !layer.sourcePath.isEmpty()) { layerFullBmp = BitmapFactory.decodeFile(layer.sourcePath); } else if (!layer.isExternal && layer.sourcePath != null && layer.sourcePath.toLowerCase().endsWith(".sff")) { byte[] fullData = Api.decodeSffFrame(layer.sourcePath, layer.originalGroup, layer.originalItem, ""); if (fullData != null) layerFullBmp = BitmapFactory.decodeByteArray(fullData, 0, fullData.length); }
                                    if (layerFullBmp != null) { Matrix m = new Matrix(); m.postScale(layer.scaleX, layer.scaleY); float drawX = (layer.startX - layer.axisX) - minX; float drawY = (layer.startY - layer.axisY) - minY; m.postTranslate(drawX, drawY); Paint p = new Paint(); if ("add".equalsIgnoreCase(layer.trans != null ? layer.trans.trim() : "none")) { p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SCREEN)); } mergedCanvas.drawBitmap(layerFullBmp, m, p); layerFullBmp.recycle(); }
                                } catch (OutOfMemoryError e) {}
                            }
                            File tmpPng = new File(getContext().getCacheDir(), "merged_flatten_" + System.currentTimeMillis() + ".png"); FileOutputStream fosPng = new FileOutputStream(tmpPng); mergedBmp.compress(Bitmap.CompressFormat.PNG, 100, fosPng); fosPng.close(); mergedBmp.recycle(); short newAxisX = (short) -minX; short newAxisY = (short) -minY; Api.addSffFrame(finalSffFile.getAbsolutePath(), 0, 0, newAxisX, newAxisY, tmpPng.getAbsolutePath());
                        }
                        new Handler(Looper.getMainLooper()).post(() -> { Toast.makeText(getContext(), "✅ 2D 地图合并栅格化导出成功！\n文件在:\n" + finalExportDir.getAbsolutePath(), Toast.LENGTH_LONG).show(); });
                    } catch (Throwable t) { t.printStackTrace(); }
                }).start();
            });
            Button bCancel = createButton("❌ 取消", "#333333"); bCancel.setOnClickListener(clickCanSave -> exportDialog.dismiss()); LinearLayout btnRow = new LinearLayout(getContext()); btnRow.setOrientation(LinearLayout.HORIZONTAL); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f); lp.setMargins((int)(2*density), (int)(10*density), (int)(2*density), 0); btnRow.addView(bConfirm, lp); btnRow.addView(bCancel, lp); box.addView(btnRow); svExp.addView(box); flExp.addView(svExp, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER)); exportDialog.setContentView(flExp); exportDialog.show();
        });

        // 🚀 终极全自由 3D 全屏沉浸工作台 (天空盒 + 自定义光源 + 全格式支持 + Draco压缩)
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
                    new Handler(Looper.getMainLooper()).post(() -> {
                        new android.app.AlertDialog.Builder(getContext())
                            .setTitle("⚠️ 退出确认")
                            .setMessage("确定要退出 3D 工作室吗？\n如果没有点击【💾 烘焙打包】，当前的场景布置将会丢失！")
                            .setPositiveButton("退出", (dialog, which) -> { studioDialog.dismiss(); is3DMode[0] = false; btnMode2D.performClick(); })
                            .setNegativeButton("取消", null)
                            .show();
                    });
                }
                
                @android.webkit.JavascriptInterface public void triggerImport() {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        // 🔓 支持 8 种主流 3D 格式
                        showWin10FilePicker("导入 3D 模型", 11, null, null, fileMod -> {
                            FileCallback processModel = fMod -> {
                                StageModelInfo m = new StageModelInfo(); m.name = fMod.getName(); m.path = fMod.getAbsolutePath(); modelList.add(m);
                                modelWebView.evaluateJavascript("javascript:loadExternalModel('file://" + m.path + "');", null);
                                Toast.makeText(getContext(), "✅ 模型开始解析并注入准星位置", Toast.LENGTH_SHORT).show();
                            };
                            if (fileMod.isDirectory()) showGenericFileListPicker(fileMod, new String[]{".gltf", ".glb", ".obj", ".fbx", ".3ds", ".dae", ".ply", ".stl"}, "3D模型", "#0078D7", processModel); else processModel.onFileSelected(fileMod);
                        });
                    });
                }

                @android.webkit.JavascriptInterface public void triggerTextureImport() {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        showWin10FilePicker("选择贴图 (支持图片/GIF/视频)", 7, null, null, fileTex -> {
                            FileCallback processTex = fTex -> {
                                modelWebView.evaluateJavascript("javascript:applyTexture('file://" + fTex.getAbsolutePath() + "');", null);
                                Toast.makeText(getContext(), "✅ 贴图/视频已应用至模型", Toast.LENGTH_SHORT).show();
                            };
                            if (fileTex.isDirectory()) showImageGridPicker(fileTex, processTex); else processTex.onFileSelected(fileTex);
                        });
                    });
                }

                @android.webkit.JavascriptInterface public void triggerExportSettings() {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        final Dialog expD = new Dialog(getContext());
                        expD.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                        LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setBackgroundColor(Color.parseColor("#252526")); box.setPadding((int)(20*density), (int)(20*density), (int)(20*density), (int)(20*density));
                        GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#252526")); border.setStroke((int)(2*density), Color.parseColor("#0078D7")); border.setCornerRadius(15*density); box.setBackground(border);

                        box.addView(createSubTitle("💾 场景烘焙 (统一输出 GLB)"));
                        box.addView(createSubTitle("自定义场景名称:"));
                        EditText nameInput = createInput("例如: MyStage_3D", "MyStage_3D"); box.addView(nameInput);

                        box.addView(createSubTitle("保存路径:"));
                        TextView pathTxt = new TextView(getContext());
                        String defaultPath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "IkemenExports").getAbsolutePath();
                        pathTxt.setText(defaultPath); pathTxt.setTextColor(Color.LTGRAY); pathTxt.setPadding(0, (int)(5*density), 0, (int)(10*density)); applyGlobalFontSettings(pathTxt, 0.9f, false); box.addView(pathTxt);

                        Button btnPickDir = createButton("📂 选择其他文件夹", "#3F3F46");
                        btnPickDir.setOnClickListener(v -> showWin10FilePicker("选择导出目录", 10, null, null, dir -> pathTxt.setText(dir.getAbsolutePath())));
                        box.addView(btnPickDir);

                        // 🛡️ 使用全路径避免导包错误
                        android.widget.CheckBox compressCheck = new android.widget.CheckBox(getContext());
                        compressCheck.setText("开启极限体积压缩优化 (合并节点)");
                        compressCheck.setTextColor(Color.WHITE); compressCheck.setChecked(true);
                        box.addView(compressCheck);

                        Button btnConf = createButton("✔️ 确认打包导出", "#4CAF50");
                        btnConf.setOnClickListener(v -> {
                            String n = nameInput.getText().toString().trim(); if(n.isEmpty()) n = "MyStage_3D";
                            String p = pathTxt.getText().toString().replace("\\", "\\\\");
                            modelWebView.evaluateJavascript("javascript:executeGLBExport('" + n + "', '" + p + "', " + compressCheck.isChecked() + ");", null);
                            expD.dismiss();
                            Toast.makeText(getContext(), "📦 正在执行烘焙打包压缩...", Toast.LENGTH_LONG).show();
                        });
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, (int)(15*density), 0, 0); box.addView(btnConf, lp);
                        Button btnCancel = createButton("❌ 取消", "#E81123"); btnCancel.setOnClickListener(v -> expD.dismiss()); box.addView(btnCancel);
                        // 🛡️ 核心修复：套一层 ScrollView，完美支持上下滑动！
                        ScrollView scrollWrap = new ScrollView(getContext());
                        scrollWrap.addView(box);
                        expD.setContentView(scrollWrap); 
                        expD.show();
                    });
                }

                private StringBuilder b64Buf = new StringBuilder();
                @android.webkit.JavascriptInterface public void beginExport() { b64Buf.setLength(0); }
                @android.webkit.JavascriptInterface public void chunkExport(String chunk) { b64Buf.append(chunk); }
                @android.webkit.JavascriptInterface public void endExport(String name, String pathStr) {
                    final String fullB64 = b64Buf.toString();
                    new Thread(() -> {
                        try {
                            byte[] data = android.util.Base64.decode(fullB64, android.util.Base64.DEFAULT);
                            File outDir = new File(pathStr); outDir.mkdirs();
                            File glbFile = new File(outDir, name + ".glb");
                            FileOutputStream fos = new FileOutputStream(glbFile); fos.write(data); fos.close();
                            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(getContext(), "✅ 完美导出! 模型大小: " + (data.length / 1024) + " KB\n保存至: " + glbFile.getAbsolutePath(), Toast.LENGTH_LONG).show());
                        } catch(Exception e) { 
                            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(getContext(), "❌ 写入失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    }).start();
                }
            }, "StudioBridge");

            studioRoot.addView(modelWebView, new FrameLayout.LayoutParams(-1, -1));
            studioDialog.setContentView(studioRoot);
            studioDialog.show();
            studioDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='utf-8'><style>");
            html.append("body,html{margin:0;padding:0;width:100%;height:100%;background-color:#121212;overflow:hidden;touch-action:none;user-select:none;font-family:sans-serif;}");
            html.append(".scrollable-panel { max-height:80vh; overflow-y:auto; overflow-x:hidden; display:flex; flex-direction:column; }");
            html.append(".scrollable-panel::-webkit-scrollbar { width:4px; } .scrollable-panel::-webkit-scrollbar-thumb { background:#888; border-radius:2px; }");
            html.append(".ui-btn{padding:10px; background:#333; color:white; border:none; border-radius:6px; font-weight:bold; width:100%; margin-bottom:5px; flex-shrink:0;}");
            html.append(".ui-btn:active{background:#555;}");
            html.append(".drag-handle{width:100%;text-align:center;color:#aaa;font-size:12px;cursor:move;padding:10px 0;margin-bottom:5px;border-bottom:1px solid #444; flex-shrink:0; touch-action:none;}");
            html.append(".setting-row{display:flex; justify-content:space-between; margin-bottom:8px; color:white; font-size:12px; align-items:center;}");
            html.append(".param-row { display:flex; gap:3px; margin-bottom:5px; align-items:center; color:white; font-size:12px; }");
            html.append(".param-row input { width:35px; background:#222; color:white; border:1px solid #555; text-align:center; font-size:12px; border-radius:3px; }");
            html.append(".err-log { position:absolute; bottom:10px; left:10px; color:red; z-index:9999; font-size:12px; pointer-events:none; }");
            html.append("</style>");
            
            html.append("<script>window.onerror = function(msg, url, line) { var e = document.createElement('div'); e.className = 'err-log'; e.innerText = 'JS报错: ' + msg + ' (行 '+line+')'; document.body.appendChild(e); };</script>");

            html.append("<script src=\"js/three.min.js\"></script>");
            html.append("<script src=\"js/GLTFLoader.js\"></script>");
            html.append("<script src=\"js/OBJLoader.js\"></script>");
            html.append("<script src=\"js/FBXLoader.js\"></script>");
            html.append("<script src=\"js/TDSLoader.js\"></script>");
            html.append("<script src=\"js/ColladaLoader.js\"></script>");
            html.append("<script src=\"js/STLLoader.js\"></script>");
            html.append("<script src=\"js/PLYLoader.js\"></script>");
            html.append("<script src=\"js/TransformControls.js\"></script>");
            html.append("<script src=\"js/GLTFExporter.js\"></script>");
            html.append("<script src=\"js/nipplejs.min.js\"></script>");
            html.append("<script src=\"js/DRACOLoader.js\"></script>"); // 🛡️ 纯离线读取 Draco 解码器
            html.append("<script src=\"js/fflate.min.js\"></script>"); // 🛡️ 引入 FBX 解压模块
            
            html.append("</head><body>");
            
            html.append("<div id='crosshair' style='position:absolute; top:50%; left:50%; transform:translate(-50%, -50%); color:rgba(255,255,255,0.7); font-size:30px; pointer-events:none; z-index:50;'>+</div>");

            html.append("<button id='previewExit' onclick='togglePreviewMode()' style='display:none; position:absolute; top:20px; right:20px; z-index:3000; padding:15px; background:#FF9800; color:white; border-radius:8px; border:none; font-weight:bold;'>❌ 退出预览</button>");

            html.append("<div id='sysGroup' class='scrollable-panel' style='position:absolute; top:20px; left:20px; z-index:1000; background:rgba(20,20,20,0.8); padding:8px; border-radius:10px; width:120px;'>");
            html.append("   <div class='drag-handle' id='sysHandle'>⠿ 拖动 ⠿</div>");
            html.append("   <button class='ui-btn' onclick='StudioBridge.closeStudio()' style='background:#E81123;'>⬅️ 返回 2D</button>");
            html.append("   <button class='ui-btn' onclick='StudioBridge.triggerExportSettings()' style='background:#4CAF50;'>💾 烘焙打包</button>");
            html.append("   <button class='ui-btn' onclick='togglePreviewMode()' style='background:#FF9800;'>👁️ 预览模式</button>");
            html.append("</div>");

            html.append("<div id='rightMenu' class='scrollable-panel' style='position:absolute; top:20px; right:20px; z-index:1000; background:rgba(20,20,20,0.85); padding:8px; border-radius:10px; width:180px;'>");
            html.append("   <div class='drag-handle' id='rightHandle'>⠿ 拖动 ⠿</div>");
            html.append("   <button class='ui-btn' onclick='StudioBridge.triggerImport()' style='background:#0078D7;'>📥 导入模型</button>");
            
            html.append("   <button class='ui-btn' onclick='toggleSub(\"buildSub\")'>➕ 新建几何/环境/光</button>");
            html.append("   <div id='buildSub' style='display:none; padding-left:10px;'>");
            html.append("       <button class='ui-btn' onclick='addGeom(\"box\")'>方块(墙)</button>");
            html.append("       <button class='ui-btn' onclick='addGeom(\"plane\")'>平面(地)</button>");
            html.append("       <button class='ui-btn' onclick='addGeom(\"skydome\")' style='background:#9C27B0'>天空盒(环境背景)</button>");
          html.append("       <button class='ui-btn' onclick='addLight(\"dir\")' style='background:#E6C200; color:black;'>☀️ 平行光(太阳/全局阴影)</button>");
        html.append("       <button class='ui-btn' onclick='addLight(\"point\")' style='background:#E6C200; color:black;'>💡 点光源(范围照亮)</button>");
        html.append("       <button class='ui-btn' onclick='addLight(\"spot\")' style='background:#E6C200; color:black;'>🔦 手电筒(单向射线)</button>");
        html.append("       <button class='ui-btn' onclick='addLight(\"hemi\")' style='background:#E6C200; color:black;'>☁️ 动态环境光(天光)</button>");
            html.append("   </div>");
            
            html.append("   <button class='ui-btn' onclick='toggleSub(\"transSub\")'>🔧 变换轴</button>");
            html.append("   <div id='transSub' style='display:none; padding-left:10px;'>");
            html.append("       <button class='ui-btn' id='m_trans' onclick='setTransMode(\"translate\")' style='background:#0078D7'>↕️ 移动</button>");
            html.append("       <button class='ui-btn' id='m_rot' onclick='setTransMode(\"rotate\")'>🔄 旋转</button>");
            html.append("       <button class='ui-btn' id='m_scale' onclick='setTransMode(\"scale\")'>📐 缩放</button>");
            html.append("   </div>");

            html.append("   <button class='ui-btn' onclick='toggleSub(\"lightSub\")'>⚙️ 系统光与灵敏度</button>");
            html.append("   <div id='lightSub' style='display:none; padding:10px; background:rgba(0,0,0,0.5); border-radius:8px;'>");
            html.append("       <div class='setting-row'><span>环境色</span><input type='color' id='l_ambC' value='#ffffff' onchange='updateSysLights()' style='width:50px;'></div>");
            html.append("       <div class='setting-row'><span>环境强</span><input type='range' id='l_ambI' min='0' max='50' value='20' style='width:70px;' oninput='updateSysLights()'></div>");
            html.append("       <div class='setting-row'><span>太阳色</span><input type='color' id='l_dirC' value='#ffffff' onchange='updateSysLights()' style='width:50px;'></div>");
            html.append("       <div class='setting-row'><span>太阳强</span><input type='range' id='l_dirI' min='0' max='50' value='15' style='width:70px;' oninput='updateSysLights()'></div>");
            html.append("       <div class='setting-row'><span>移速</span><input type='range' id='s_move' min='20' max='300' value='80' style='width:70px;' oninput='updateSettings()'></div>");
            html.append("       <div class='setting-row'><span>视角</span><input type='range' id='s_look' min='1' max='30' value='6' style='width:70px;' oninput='updateSettings()'></div>");
            html.append("   </div>");
            
            html.append("   <div id='objTools' style='display:none; margin-top:10px; border-top:1px solid #555; padding-top:10px;'>");
            html.append("       <div style='color:#ccc; font-size:12px; margin-bottom:5px; text-align:center;'>精确参数 (X Y Z)</div>");
            html.append("       <div class='param-row'><span>移</span><input type='number' id='pX' onchange='applyParams()'><input type='number' id='pY' onchange='applyParams()'><input type='number' id='pZ' onchange='applyParams()'></div>");
            html.append("       <div class='param-row'><span>旋</span><input type='number' id='rX' onchange='applyParams()'><input type='number' id='rY' onchange='applyParams()'><input type='number' id='rZ' onchange='applyParams()'></div>");
            html.append("       <div class='param-row'><span>缩</span><input type='number' id='sX' step='0.1' onchange='applyParams()'><input type='number' id='sY' step='0.1' onchange='applyParams()'><input type='number' id='sZ' step='0.1' onchange='applyParams()'></div>");
            
            // 🎬 加入动态位移与自转输入框 (彻底修复找不到 rvX 导致的崩溃红字)
            html.append("       <div style='color:#4CAF50; font-size:12px; margin:5px 0; text-align:center;'>模型持续位移/自转速 (每帧)</div>");
            html.append("       <div class='param-row'><span>移</span><input type='number' id='vX' step='0.1' onchange='applyParams()'><input type='number' id='vY' step='0.1' onchange='applyParams()'><input type='number' id='vZ' step='0.1' onchange='applyParams()'></div>");
            html.append("       <div class='param-row'><span>转</span><input type='number' id='rvX' step='0.01' onchange='applyParams()'><input type='number' id='rvY' step='0.01' onchange='applyParams()'><input type='number' id='rvZ' step='0.01' onchange='applyParams()'></div>");          
            html.append("       <div id='lightParams' style='display:none; margin-top:5px; border-top:1px dashed #555; padding-top:5px;'>");
            html.append("           <div style='color:#E6C200; font-size:12px; margin-bottom:5px;'>💡 自定义光源参数</div>");
            html.append("           <div class='param-row'><span>颜色</span><input type='color' id='l_col' onchange='applyParams()' style='width:60px; height:20px; padding:0;'></div>");
            html.append("           <div class='param-row'><span>强度</span><input type='number' id='l_int' step='0.1' onchange='applyParams()'></div>");
            html.append("           <div class='param-row'><span>范围</span><input type='number' id='l_dist' step='1' onchange='applyParams()'></div>");
            html.append("       </div>");

            html.append("       <div style='display:flex; gap:5px; margin-top:5px; flex-wrap:wrap;'><button class='ui-btn' onclick='mirrorObj(\"x\")' style='background:#1E88E5; flex:1; min-width:70px;'>↔️ X镜</button><button class='ui-btn' onclick='mirrorObj(\"y\")' style='background:#1E88E5; flex:1; min-width:70px;'>↕️ Y镜</button></div>");
            html.append("       <div style='display:flex; gap:5px; margin-top:5px; flex-wrap:wrap;'><button class='ui-btn' onclick='copyObj()' style='background:#43A047; flex:1; min-width:70px;'>📄 复制</button><button class='ui-btn' onclick='pasteObj()' style='background:#FDD835; color:black; flex:1; min-width:70px;'>📋 粘贴</button></div>");

            html.append("       <button class='ui-btn' onclick='StudioBridge.triggerTextureImport()' style='background:#9C27B0; margin-top:10px;'>🖼️ 替换贴图</button>");
            html.append("       <button class='ui-btn' onclick='deleteSelected()' style='background:#ff4444;'>🗑️ 删除对象</button>");
            html.append("       <button class='ui-btn' onclick='clearSelection()' style='background:#777;'>❌ 取消选中</button>");
            html.append("   </div>");
            html.append("</div>");

            html.append("<div id='fireBtn' style='position:absolute; bottom:60px; right:60px; width:70px; height:70px; border-radius:50%; background:rgba(232,17,35,0.7); border:3px solid rgba(255,255,255,0.6); display:flex; justify-content:center; align-items:center; font-size:28px; z-index:1000; box-shadow:0 0 15px rgba(232,17,35,0.8); touch-action:none;' onpointerdown='fireSelect()'>🎯</div>");

            // 🎬 5按键独立动画控制台 (任意模型选中即显示，包含逐帧调整)
            html.append("<div id='animTools' style='position:absolute; bottom:20px; left:50%; transform:translateX(-50%); z-index:9999; display:none; gap:8px; background:rgba(0,0,0,0.8); padding:10px; border-radius:10px; align-items:center; white-space:nowrap;'>");
            html.append("   <button onclick='switchAnim(-1)' class='ui-btn' style='width:auto; margin:0; padding:8px 12px;'>⏪ 上动作</button>");
            html.append("   <button onclick='stepFrame(-1)' class='ui-btn' style='width:auto; margin:0; padding:8px 12px;'>◀ 上一帧</button>");
            html.append("   <button id='playBtn' onclick='togglePlay()' class='ui-btn' style='background:#0078D7; width:auto; margin:0; padding:8px 20px;'>▶️ 播放</button>");
            html.append("   <button onclick='stepFrame(1)' class='ui-btn' style='width:auto; margin:0; padding:8px 12px;'>下一帧 ▶</button>");
            html.append("   <button onclick='switchAnim(1)' class='ui-btn' style='width:auto; margin:0; padding:8px 12px;'>下动作 ⏭️</button>");
            html.append("</div>");

            html.append("<script>");
            html.append("var scene = new THREE.Scene(); var clock = new THREE.Clock();");
            html.append("var camera = new THREE.PerspectiveCamera(60, window.innerWidth/window.innerHeight, 0.1, 10000); camera.position.set(0, 15, 30);");
            html.append("var renderer = new THREE.WebGLRenderer({antialias:true, alpha:true, powerPreference:'high-performance'}); renderer.setSize(window.innerWidth, window.innerHeight);");
            html.append("renderer.outputEncoding = THREE.sRGBEncoding; renderer.toneMapping = THREE.ACESFilmicToneMapping; renderer.toneMappingExposure = 1.0; renderer.shadowMap.enabled = true; renderer.shadowMap.type = THREE.PCFSoftShadowMap;");
            html.append("document.body.appendChild(renderer.domElement);");
            
            html.append("var ambientLight = new THREE.AmbientLight(0xffffff, 0.6); scene.add(ambientLight);");
            html.append("var dirLight = new THREE.DirectionalLight(0xffffff, 2.0); dirLight.position.set(100, 200, 100); dirLight.castShadow = true;");
            html.append("dirLight.shadow.mapSize.width = 4096; dirLight.shadow.mapSize.height = 4096; dirLight.shadow.camera.near = 0.5; dirLight.shadow.camera.far = 1000; dirLight.shadow.camera.left = -200; dirLight.shadow.camera.right = 200; dirLight.shadow.camera.top = 200; dirLight.shadow.camera.bottom = -200; dirLight.shadow.bias = -0.0005; scene.add(dirLight);");
            html.append("var grid = new THREE.GridHelper(200, 20, 0x0078D7, 0x3F3F46); scene.add(grid);");

            html.append("var euler = new THREE.Euler(0, 0, 0, 'YXZ'); var isLooking = false; var lastTouchX = 0, lastTouchY = 0;");
            html.append("var moveSpeed = 80; var lookSpeed = 0.006; var lockEvents = false; var clipboardObj = null; var isUIEditMode = false;");
            
            html.append("var transformControl = new THREE.TransformControls(camera, renderer.domElement);");
            html.append("transformControl.addEventListener('dragging-changed', function(e) { isLooking = false; });");
            html.append("transformControl.addEventListener('change', function() { updateParamUI(); });");
            html.append("scene.add(transformControl);");

            html.append("renderer.domElement.addEventListener('pointerdown', function(e) { if(isUIEditMode) return; if(lockEvents || e.clientX < window.innerWidth * 0.4 || transformControl.dragging) return; isLooking = true; lastTouchX = e.clientX; lastTouchY = e.clientY; });");
            html.append("renderer.domElement.addEventListener('pointermove', function(e) { if(isUIEditMode) return; if(!isLooking || transformControl.dragging) return; var dx = e.clientX - lastTouchX; var dy = e.clientY - lastTouchY; euler.setFromQuaternion(camera.quaternion); euler.y -= dx * lookSpeed; euler.x -= dy * lookSpeed; euler.x = Math.max(-Math.PI/2, Math.min(Math.PI/2, euler.x)); camera.quaternion.setFromEuler(euler); lastTouchX = e.clientX; lastTouchY = e.clientY; });");
            html.append("renderer.domElement.addEventListener('pointerup', function() { isLooking = false; });");

            // 🛡️ 补齐动画混合器全局声明，彻底解决 mixers 报错和动画按键不显示的问题！
            html.append("var raycaster = new THREE.Raycaster(); var interactables = []; var selectedObj = null; window.mixers = window.mixers || [];");
            html.append("window.fireSelect = function() { if (lockEvents || isUIEditMode) return; raycaster.setFromCamera(new THREE.Vector2(0, 0), camera); var intersects = raycaster.intersectObjects(interactables, true); if(intersects.length > 0) { var obj = intersects[0].object; while(obj.parent && obj.userData.isRoot !== true) { obj = obj.parent; } transformControl.attach(obj); selectedObj = obj; document.getElementById('objTools').style.display='block'; updateParamUI(); checkAnimUI(); } else { clearSelection(); } };");
            html.append("window.clearSelection = function() { transformControl.detach(); selectedObj = null; document.getElementById('objTools').style.display='none'; document.getElementById('animTools').style.display='none'; };");

            html.append("window.updateParamUI = function() { if(!selectedObj) return; document.getElementById('pX').value=selectedObj.position.x.toFixed(2); document.getElementById('pY').value=selectedObj.position.y.toFixed(2); document.getElementById('pZ').value=selectedObj.position.z.toFixed(2); document.getElementById('rX').value=(selectedObj.rotation.x*180/Math.PI).toFixed(1); document.getElementById('rY').value=(selectedObj.rotation.y*180/Math.PI).toFixed(1); document.getElementById('rZ').value=(selectedObj.rotation.z*180/Math.PI).toFixed(1); document.getElementById('sX').value=selectedObj.scale.x.toFixed(2); document.getElementById('sY').value=selectedObj.scale.y.toFixed(2); document.getElementById('sZ').value=selectedObj.scale.z.toFixed(2); document.getElementById('vX').value=selectedObj.userData.velX||0; document.getElementById('vY').value=selectedObj.userData.velY||0; document.getElementById('vZ').value=selectedObj.userData.velZ||0; document.getElementById('rvX').value=selectedObj.userData.rVelX||0; document.getElementById('rvY').value=selectedObj.userData.rVelY||0; document.getElementById('rvZ').value=selectedObj.userData.rVelZ||0; if(selectedObj.userData.isLight){ document.getElementById('lightParams').style.display='block'; var l = selectedObj.children[0]; if(l.color) document.getElementById('l_col').value='#'+l.color.getHexString(); document.getElementById('l_int').value=l.intensity||1; document.getElementById('l_dist').value=l.distance||0; }else{ document.getElementById('lightParams').style.display='none'; } };");
            html.append("window.applyParams = function() { if(!selectedObj) return; selectedObj.position.set(parseFloat(document.getElementById('pX').value)||0, parseFloat(document.getElementById('pY').value)||0, parseFloat(document.getElementById('pZ').value)||0); selectedObj.rotation.set((parseFloat(document.getElementById('rX').value)||0)*Math.PI/180, (parseFloat(document.getElementById('rY').value)||0)*Math.PI/180, (parseFloat(document.getElementById('rZ').value)||0)*Math.PI/180); selectedObj.scale.set(parseFloat(document.getElementById('sX').value)||1, parseFloat(document.getElementById('sY').value)||1, parseFloat(document.getElementById('sZ').value)||1); selectedObj.userData.velX=parseFloat(document.getElementById('vX').value)||0; selectedObj.userData.velY=parseFloat(document.getElementById('vY').value)||0; selectedObj.userData.velZ=parseFloat(document.getElementById('vZ').value)||0; selectedObj.userData.rVelX=parseFloat(document.getElementById('rvX').value)||0; selectedObj.userData.rVelY=parseFloat(document.getElementById('rvY').value)||0; selectedObj.userData.rVelZ=parseFloat(document.getElementById('rvZ').value)||0; if(selectedObj.userData.isLight){ var l = selectedObj.children[0]; if(l.color) l.color.set(document.getElementById('l_col').value); l.intensity=parseFloat(document.getElementById('l_int').value); l.distance=parseFloat(document.getElementById('l_dist').value); } };");

            html.append("window.mirrorObj = function(axis) { if(!selectedObj) return; if(axis==='x') selectedObj.scale.x *= -1; else if(axis==='y') selectedObj.scale.y *= -1; updateParamUI(); };");
            html.append("window.copyObj = function() { if(selectedObj) { var cache = []; selectedObj.traverse(function(c){ cache.push({m:c.userData.mixer, a:c.userData.action}); delete c.userData.mixer; delete c.userData.action; }); clipboardObj = selectedObj.clone(); var i=0; selectedObj.traverse(function(c){ c.userData.mixer=cache[i].m; c.userData.action=cache[i].a; i++; }); alert('已复制该对象'); } };");
            html.append("window.pasteObj = function() { if(clipboardObj) { var nObj = clipboardObj.clone(); nObj.position.x += 5; scene.add(nObj); interactables.push(nObj); alert('粘贴成功'); } };");

            html.append("var texLoader = new THREE.TextureLoader();");
            html.append("window.applyTexture = function(url) { if(!selectedObj) return; var isVid = url.match(/\\.(mp4|webm|mkv)$/i); var tex; if(isVid) { var vid = document.createElement('video'); vid.src=url; vid.loop=true; vid.muted=true; vid.play(); tex = new THREE.VideoTexture(vid); } else { tex = texLoader.load(url); } tex.encoding = THREE.sRGBEncoding; selectedObj.traverse(function(child) { if(child.isMesh) { child.material = new THREE.MeshStandardMaterial({ map: tex, side: THREE.DoubleSide }); child.material.needsUpdate=true; } }); };");

            html.append("var joyZone = document.createElement('div'); joyZone.id = 'joyZone'; joyZone.style.cssText = 'position:absolute; bottom:40px; left:40px; width:120px; height:120px; z-index:999; border-radius:50%; background:rgba(255,255,255,0.08); touch-action:none;'; document.body.appendChild(joyZone);");
            html.append("if(typeof nipplejs !== 'undefined') { var manager = nipplejs.create({ zone: joyZone, mode: 'static', position: {left:'50%', top:'50%'}, color: '#0078D7' }); var moveVec = new THREE.Vector3(0,0,0); manager.on('move', function(evt, data) { var f = Math.min(data.force, 2.0); moveVec.x = Math.cos(data.angle.radian)*f; moveVec.z = -Math.sin(data.angle.radian)*f; }); manager.on('end', function() { moveVec.set(0,0,0); }); }");

            html.append("window.closeStudioSafe = function() { if(confirm('⚠️ 确定要退出 3D 工作室吗？\\n如果没有点击【💾 烘焙打包】，当前的场景布置将会丢失！')) { StudioBridge.closeStudio(); } };");

            html.append("var gltfLoader = new THREE.GLTFLoader();");
            // 🛡️ 离线加载 Draco 并强制禁用 WebWorker，彻底解决安卓本地 file:// 协议拦截多线程的闪退！
            html.append("if(typeof THREE.DRACOLoader !== 'undefined') { var dracoLoader = new THREE.DRACOLoader(); dracoLoader.setDecoderPath('js/'); dracoLoader.setDecoderConfig({type: 'js'}); dracoLoader.setWorkerLimit(0); gltfLoader.setDRACOLoader(dracoLoader); }");
            html.append("var objLoader = typeof THREE.OBJLoader !== 'undefined' ? new THREE.OBJLoader() : null;");
            html.append("var fbxLoader = typeof THREE.FBXLoader !== 'undefined' ? new THREE.FBXLoader() : null;");
            html.append("var tdsLoader = typeof THREE.TDSLoader !== 'undefined' ? new THREE.TDSLoader() : null;");
            html.append("var daeLoader = typeof THREE.ColladaLoader !== 'undefined' ? new THREE.ColladaLoader() : null;");
            html.append("var plyLoader = typeof THREE.PLYLoader !== 'undefined' ? new THREE.PLYLoader() : null;");
            html.append("var stlLoader = typeof THREE.STLLoader !== 'undefined' ? new THREE.STLLoader() : null;");

            html.append("window.loadExternalModel = function(url) {");
            html.append("    var mathPlane = new THREE.Plane(new THREE.Vector3(0, 1, 0), 0); var spawnPos = new THREE.Vector3();");
            html.append("    raycaster.setFromCamera(new THREE.Vector2(0, 0), camera);");
            html.append("    raycaster.ray.intersectPlane(mathPlane, spawnPos); if(!spawnPos) spawnPos = new THREE.Vector3(0,0,0);");
            html.append("    var ext = url.split('.').pop().toLowerCase();");
            html.append("    var basePath = url.substring(0, url.lastIndexOf('/') + 1);"); 
            html.append("    if(tdsLoader) tdsLoader.setResourcePath(basePath);"); 
            html.append("    var onLoaded = function(obj) {");
            html.append("        try {");
            html.append("            var model = obj.scene || obj;");
            html.append("            if(model.isBufferGeometry) { var mat = new THREE.MeshStandardMaterial({color:0xcccccc, side:THREE.DoubleSide}); model = new THREE.Mesh(model, mat); }");
            html.append("            model.userData.isRoot = true; model.position.copy(spawnPos);");
            html.append("            model.traverse(function(n){ if(n.isMesh) { n.castShadow = true; n.receiveShadow = true; if(n.material) { var mats = Array.isArray(n.material) ? n.material : [n.material]; mats.forEach(function(m, i){ if(!m.isMeshStandardMaterial && !m.isMeshPhongMaterial){ var nm = new THREE.MeshStandardMaterial({map:m.map, color:m.color||0xffffff, transparent:m.transparent, opacity:m.opacity, alphaTest:m.alphaTest}); if(Array.isArray(n.material)) n.material[i]=nm; else n.material=nm; m=nm; } m.side = THREE.DoubleSide; if(m.emissive) m.emissive.setHex(0x000000); m.needsUpdate=true; }); } } });");
            html.append("            model.userData.velX = 0; model.userData.velY = 0; model.userData.velZ = 0;"); 
            html.append("            var anims = obj.animations || [];");
            html.append("            if(anims && anims.length > 0) { model.userData.animations = anims; model.userData.animIndex = 0; model.userData.isPlaying = true; var mixer = new THREE.AnimationMixer(model); model.userData.mixer = mixer; mixers.push(mixer); model.userData.action = mixer.clipAction(anims[0]); model.userData.action.play(); }");
            html.append("            scene.add(model); interactables.push(model);");
            html.append("            checkAnimUI();");
            html.append("        } catch(ex) { alert('模型渲染报错: ' + ex.message); }");
            html.append("    };");
            html.append("    try {");
            html.append("        if((ext==='gltf'||ext==='glb') && gltfLoader) gltfLoader.load(url, onLoaded, null, function(err){ alert('加载失败: '+err); });");
            html.append("        else if(ext==='obj' && objLoader) objLoader.load(url, onLoaded);");
            html.append("        else if(ext==='fbx' && fbxLoader) fbxLoader.load(url, onLoaded, null, function(err){ alert('FBX错误(可能缺fflate): '+err); });");
            html.append("        else if(ext==='3ds' && tdsLoader) tdsLoader.load(url, onLoaded);");
            html.append("        else if(ext==='dae' && daeLoader) daeLoader.load(url, function(c){ onLoaded(c.scene); });");
            html.append("        else if(ext==='stl' && stlLoader) stlLoader.load(url, onLoaded);");
            html.append("        else if(ext==='ply' && plyLoader) plyLoader.load(url, onLoaded);");
            html.append("        else alert('未找到该格式的解析器: ' + ext);");
            html.append("    } catch(e) { alert('加载核心异常: ' + e.message); }");
            html.append("};");

            html.append("window.toggleSub = function(id) { var e=document.getElementById(id); e.style.display=(e.style.display==='none'||e.style.display==='')?'block':'none'; };");
            html.append("window.setTransMode = function(m) { transformControl.setMode(m); document.getElementById('m_trans').style.background='#333'; document.getElementById('m_rot').style.background='#333'; document.getElementById('m_scale').style.background='#333'; document.getElementById(m==='translate'?'m_trans':(m==='rotate'?'m_rot':'m_scale')).style.background='#0078D7'; };");
            
            html.append("window.addGeom = function(t) {");
            html.append("    var geo, mat = new THREE.MeshStandardMaterial({color: 0xcccccc}); var mesh;");
            html.append("    if(t==='box') { geo=new THREE.BoxGeometry(10,10,10); mesh=new THREE.Mesh(geo,mat); }");
            html.append("    if(t==='plane') { geo=new THREE.PlaneGeometry(100,100); geo.rotateX(-Math.PI/2); mesh=new THREE.Mesh(geo,mat); }");
            html.append("    if(t==='skydome') { geo=new THREE.SphereGeometry(100, 32, 32); mat=new THREE.MeshBasicMaterial({color: 0x87CEEB, side: THREE.BackSide}); mesh=new THREE.Mesh(geo,mat); mesh.userData.isSkybox = true; }");
            html.append("    if(t!=='skydome') {");
            html.append("       var mathPlane = new THREE.Plane(new THREE.Vector3(0, 1, 0), 0); var spawnPos = new THREE.Vector3();");
            html.append("       raycaster.setFromCamera(new THREE.Vector2(0, 0), camera); raycaster.ray.intersectPlane(mathPlane, spawnPos);");
            html.append("       mesh.position.copy(spawnPos || new THREE.Vector3(0,0,0));");
            html.append("       mesh.castShadow=true; mesh.receiveShadow=true;");
            html.append("    }");
            html.append("    mesh.userData.isRoot=true; scene.add(mesh); interactables.push(mesh);");
            html.append("};");

            html.append("window.checkDefaultLights = function() { var c=0; interactables.forEach(function(o){if(o.userData.isLight) c++;}); if(c>0){ambientLight.visible=false; dirLight.visible=false;}else{ambientLight.visible=true; dirLight.visible=true;} };");
            
            html.append("window.addLight = function(type) {");
            html.append("    var group = new THREE.Group(); var light, gizmo;");
            html.append("    if(type==='dir') {");
            html.append("        light = new THREE.DirectionalLight(0xffffff, 2.0); light.castShadow = true; light.shadow.mapSize.width = 4096; light.shadow.mapSize.height = 4096; light.shadow.camera.left = -200; light.shadow.camera.right = 200; light.shadow.camera.top = 200; light.shadow.camera.bottom = -200; light.shadow.bias = -0.0005; group.add(light);");
            html.append("        gizmo = new THREE.Mesh(new THREE.BoxGeometry(2, 2, 2), new THREE.MeshBasicMaterial({color:0xffffff, wireframe:true}));");
            html.append("    } else if(type==='spot') {");
            html.append("        light = new THREE.SpotLight(0xffffff, 15, 300, Math.PI/6, 0.5, 1); light.position.set(0,0,0); light.target.position.set(0,-1,0); light.castShadow = true; light.shadow.mapSize.width = 2048; light.shadow.mapSize.height = 2048; light.shadow.bias = -0.0005; group.add(light); group.add(light.target);");
            html.append("        gizmo = new THREE.Mesh(new THREE.CylinderGeometry(0.1, 1.5, 2.5, 8), new THREE.MeshBasicMaterial({color:0x555555, wireframe:true})); gizmo.position.y = -1.25;");
            html.append("    } else if(type==='hemi') {");
            html.append("        light = new THREE.HemisphereLight(0x87CEEB, 0x444444, 2); group.add(light);");
            html.append("        gizmo = new THREE.Mesh(new THREE.OctahedronGeometry(2, 0), new THREE.MeshBasicMaterial({color:0x00aaff, wireframe:true}));");
            html.append("    } else {");
            html.append("        light = new THREE.PointLight(0xffffff, 10, 200, 1); light.castShadow = true; light.shadow.mapSize.width = 2048; light.shadow.mapSize.height = 2048; light.shadow.bias = -0.0005; group.add(light);");
            html.append("        gizmo = new THREE.Mesh(new THREE.SphereGeometry(1.5, 8, 8), new THREE.MeshBasicMaterial({color:0xffff00, wireframe:true}));");
            html.append("    }");
            html.append("    group.add(gizmo);");
            html.append("    var mathPlane = new THREE.Plane(new THREE.Vector3(0, 1, 0), 0); var spawnPos = new THREE.Vector3(); raycaster.setFromCamera(new THREE.Vector2(0, 0), camera); raycaster.ray.intersectPlane(mathPlane, spawnPos);");
            html.append("    group.position.copy(spawnPos || new THREE.Vector3(0,0,0)); group.position.y += 15;");
            html.append("    group.userData.isRoot = true; group.userData.isLight = true; group.userData.lType = type || 'point';");
            html.append("    scene.add(group); interactables.push(group); checkDefaultLights();");
            html.append("};");

            html.append("window.deleteSelected = function() { if(selectedObj) { scene.remove(selectedObj); interactables.splice(interactables.indexOf(selectedObj),1); clearSelection(); checkDefaultLights(); }};");
            
            html.append("window.updateSysLights = function() { ambientLight.color.set(document.getElementById('l_ambC').value); ambientLight.intensity=parseFloat(document.getElementById('l_ambI').value)/10; dirLight.color.set(document.getElementById('l_dirC').value); dirLight.intensity=parseFloat(document.getElementById('l_dirI').value)/10; };");
            html.append("window.updateSettings = function() { moveSpeed=parseFloat(document.getElementById('s_move').value); lookSpeed=parseFloat(document.getElementById('s_look').value)/1000; };");
            
            html.append("var isPreview = false; window.togglePreviewMode = function() { isPreview = !isPreview; lockEvents = true; setTimeout(function(){lockEvents=false;}, 500); document.getElementById('sysGroup').style.display = isPreview ? 'none' : 'flex'; document.getElementById('rightMenu').style.display = isPreview ? 'none' : 'flex'; document.getElementById('fireBtn').style.display = isPreview ? 'none' : 'flex'; document.getElementById('crosshair').style.display = isPreview ? 'none' : 'block'; document.getElementById('previewExit').style.display = isPreview ? 'block' : 'none'; grid.visible = !isPreview; transformControl.visible = !isPreview; transformControl.enabled = !isPreview; if(isPreview){clearSelection();} };");

            html.append("function checkAnimUI() { var ui=document.getElementById('animTools'); if(selectedObj) { ui.style.display='flex'; var ud=selectedObj.userData; var p=document.getElementById('playBtn'); p.innerText=ud.isPlaying?'⏸️ 暂停':'▶️ 播放'; p.style.background=ud.isPlaying?'#E81123':'#0078D7'; } else { ui.style.display='none'; } }");
            
            html.append("window.switchAnim = function(dir) { var ud=selectedObj.userData; if(!ud.animations || ud.animations.length===0){ alert('该模型无原生动作'); return; } ud.animIndex=(ud.animIndex+dir+ud.animations.length)%ud.animations.length; ud.mixer.stopAllAction(); ud.action=ud.mixer.clipAction(ud.animations[ud.animIndex]); ud.action.paused = !ud.isPlaying; ud.action.play(); checkAnimUI(); };");
            
            html.append("window.togglePlay = function() { var ud=selectedObj.userData; ud.isPlaying=!ud.isPlaying; if(ud.action) { ud.action.paused = !ud.isPlaying; if(ud.isPlaying) ud.action.play(); } checkAnimUI(); };");
            
            html.append("window.stepFrame = function(dir) { var ud=selectedObj.userData; if(ud.isPlaying){ alert('请先暂停播放再微调帧！'); return; } if(ud.action) { var clip=ud.action.getClip(); ud.action.time += dir * (1.0/30.0); if(ud.action.time < 0) ud.action.time = clip.duration; if(ud.action.time > clip.duration) ud.action.time = 0; ud.mixer.update(0); } };");

            // 🛡️ 动画主循环：使用 Math.sin 生成真正有来有回的移动视差预览效果
            html.append("function animate() { requestAnimationFrame(animate); var dt = clock.getDelta(); var time = clock.elapsedTime; if(typeof mixers!=='undefined') mixers.forEach(function(m){m.update(dt);});");
            html.append("    interactables.forEach(function(obj) { if(obj.userData.isPlaying!==false) { var factor = Math.sin(time); ");
            html.append("    if(obj.userData.velX) obj.position.x += obj.userData.velX * factor; if(obj.userData.velY) obj.position.y += obj.userData.velY * factor; if(obj.userData.velZ) obj.position.z += obj.userData.velZ * factor; ");
            html.append("    if(obj.userData.rVelX) obj.rotation.x += obj.userData.rVelX; if(obj.userData.rVelY) obj.rotation.y += obj.userData.rVelY; if(obj.userData.rVelZ) obj.rotation.z += obj.userData.rVelZ; } });");
            html.append("    if(typeof moveVec!=='undefined' && moveVec.lengthSq()>0) { camera.translateX(moveVec.x*moveSpeed*dt); camera.translateZ(moveVec.z*moveSpeed*dt); } renderer.render(scene, camera); } animate();");

            // 🛡️ 终极物理打包引擎：彻底物理切除 userData 拦截 JSON 死循环溢出，保留所有灯光与有来有回轨道
            html.append("window.executeGLBExport = function(name, path, compress) { try { var exporter = new THREE.GLTFExporter(); clearSelection(); scene.remove(grid); scene.remove(transformControl); ");
            html.append("    var expAnims = []; var hiddenGizmos = []; ");
            html.append("    scene.traverse(function(child) { ");
            html.append("        if((child.type === 'LineSegments' || child.type === 'Line' || (child.material && child.material.wireframe))) { hiddenGizmos.push({obj: child, vis: child.visible}); child.visible = false; } ");
            html.append("        if(child.isMesh && child.material && !child.userData.oldMaterial) { ");
            html.append("            var mats = Array.isArray(child.material) ? child.material : [child.material]; var newMats = []; ");
            html.append("            mats.forEach(function(m){ ");
            html.append("                var ar = ambientLight.visible ? (ambientLight.color.r * ambientLight.intensity) : 0; ");
            html.append("                var ag = ambientLight.visible ? (ambientLight.color.g * ambientLight.intensity) : 0; ");
            html.append("                var ab = ambientLight.visible ? (ambientLight.color.b * ambientLight.intensity) : 0; ");
            html.append("                var newMat = m.clone(); ");
            html.append("                if(!child.userData.isSkybox && newMat.isMeshStandardMaterial) { ");
            html.append("                    var curE = newMat.emissive || new THREE.Color(0,0,0); ");
            html.append("                    newMat.emissive = new THREE.Color(Math.min(1, curE.r + ar * 0.6), Math.min(1, curE.g + ag * 0.6), Math.min(1, curE.b + ab * 0.6)); ");
            html.append("                    newMat.emissiveIntensity = 1.0; ");
            html.append("                } ");
            html.append("                newMats.push(newMat); ");
            html.append("            }); ");
            html.append("            child.userData.oldMaterial = child.material; child.material = Array.isArray(child.material) ? newMats : newMats[0]; ");
            html.append("            if(child.userData.isSkybox) { child.userData.oldScaleX = child.scale.x; child.scale.x *= -1; } ");
            html.append("        } ");
            html.append("        if(compress && child.isMesh && child.geometry && child.scale.x < 1 && child.scale.y < 1 && child.scale.z < 1 && !child.userData.isSkybox) { ");
            html.append("            child.userData.oldGeo = child.geometry; child.userData.oldScale = child.scale.clone(); child.geometry = child.geometry.clone(); child.geometry.applyMatrix4(new THREE.Matrix4().makeScale(child.scale.x, child.scale.y, child.scale.z)); child.scale.set(1,1,1); ");
            html.append("            if(child.geometry.attributes.position){ var pos = child.geometry.attributes.position.array; for(var k=0; k<pos.length; k++) pos[k] = Math.round(pos[k]*1000)/1000; } ");
            html.append("        } ");
            html.append("    }); ");
            html.append("    interactables.forEach(function(o){ ");
            html.append("        if(o.userData.animations) expAnims.push(...o.userData.animations); ");
            html.append("        var vX=o.userData.velX||0, vY=o.userData.velY||0, vZ=o.userData.velZ||0, rX=o.userData.rVelX||0, rY=o.userData.rVelY||0, rZ=o.userData.rVelZ||0; ");
            html.append("        if(vX||vY||vZ||rX||rY||rZ){ ");
            html.append("            var times=[0, 2, 4]; var p = o.position; var dx=vX*120, dy=vY*120, dz=vZ*120; ");
            html.append("            var trackP=new THREE.VectorKeyframeTrack(o.uuid + '.position', times, [p.x,p.y,p.z, p.x+dx,p.y+dy,p.z+dz, p.x,p.y,p.z]); ");
            html.append("            var e = new THREE.Euler(o.rotation.x+rX*10, o.rotation.y+rY*10, o.rotation.z+rZ*10); var q1 = new THREE.Quaternion().setFromEuler(e); ");
            html.append("            var trackQ=new THREE.QuaternionKeyframeTrack(o.uuid + '.quaternion', times, [o.quaternion.x,o.quaternion.y,o.quaternion.z,o.quaternion.w, q1.x,q1.y,q1.z,q1.w, o.quaternion.x,o.quaternion.y,o.quaternion.z,o.quaternion.w]); ");
            html.append("            expAnims.push(new THREE.AnimationClip(o.name+'_Action', 4, [trackP, trackQ])); ");
            html.append("        } ");
            html.append("    }); ");
            html.append("    var pb = document.createElement('div'); pb.id='exp-prog'; pb.style.cssText='position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);background:rgba(0,120,215,0.9);padding:20px 40px;color:white;z-index:9999;border-radius:10px;text-align:center;font-size:18px;font-weight:bold;box-shadow:0 0 20px rgba(0,0,0,0.5);'; document.body.appendChild(pb); pb.innerText='深度光子渲染注入中...'; ");
            html.append("    exporter.parse(scene, function(result) { ");
            html.append("        scene.add(grid); scene.add(transformControl); ");
            html.append("        hiddenGizmos.forEach(function(g){ g.obj.visible = g.vis; }); ");
            html.append("        scene.traverse(function(child) { ");
            html.append("            if(child.isMesh && child.userData.oldMaterial) { child.material = child.userData.oldMaterial; delete child.userData.oldMaterial; } ");
            html.append("            if(child.userData.isSkybox && child.userData.oldScaleX !== undefined) { child.scale.x = child.userData.oldScaleX; delete child.userData.oldScaleX; } ");
            html.append("            if(child.userData.oldGeo) { child.geometry = child.userData.oldGeo; child.scale.copy(child.userData.oldScale); delete child.userData.oldGeo; delete child.userData.oldScale; } ");
            html.append("        }); ");
            html.append("        var blob=new Blob([result], {type:'application/octet-stream'}); var reader=new FileReader(); reader.readAsDataURL(blob); ");
            html.append("        reader.onloadend=function(){ ");
            html.append("            var b64 = reader.result.replace(/^data:.*;base64,/, ''); StudioBridge.beginExport(); var chunk = 500000; var t = b64.length; var i = 0; ");
            html.append("            function nextChunk(){ if(i < t) { StudioBridge.chunkExport(b64.substring(i, i+chunk)); i += chunk; pb.innerText='强制无光材质转换与打包进度: '+Math.min(100, Math.round((i/t)*100))+'%'; setTimeout(nextChunk, 5); } else { document.body.removeChild(pb); StudioBridge.endExport(name, path); } } ");
            html.append("            nextChunk(); ");
            html.append("        }; ");
            html.append("    }, {binary:true, animations:expAnims.length?expAnims:null}); ");
            html.append("} catch(e) { alert('打包拦截异常: '+e.message); scene.add(grid); scene.add(transformControl); if(document.getElementById('exp-prog')) document.body.removeChild(document.getElementById('exp-prog')); } };");


            html.append("window.addEventListener('resize', function(){ if(typeof camera !== 'undefined'){ camera.aspect = window.innerWidth / window.innerHeight; camera.updateProjectionMatrix(); renderer.setSize(window.innerWidth, window.innerHeight); }});");
            html.append("window.makeDraggable = function(handleId, popupId) { var pos1=0, pos2=0, pos3=0, pos4=0; var elmnt = document.getElementById(popupId); var handle = document.getElementById(handleId); if(!handle) return; handle.onpointerdown = function(e) { e.preventDefault(); pos3 = e.clientX; pos4 = e.clientY; document.onpointerup = function() { document.onpointerup = null; document.onpointermove = null; }; document.onpointermove = function(e) { e.preventDefault(); pos1 = pos3 - e.clientX; pos2 = pos4 - e.clientY; pos3 = e.clientX; pos4 = e.clientY; elmnt.style.top = (elmnt.offsetTop - pos2) + 'px'; elmnt.style.left = (elmnt.offsetLeft - pos1) + 'px'; }; }; };");
            html.append("setTimeout(function(){ makeDraggable('sysHandle', 'sysGroup'); makeDraggable('rightHandle', 'rightMenu'); }, 500);");
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

    // 🌉 Android 与 3D WebView 数据通信桥梁
    public class WebAppInterface {
        private Dialog parentDialog;
        public WebAppInterface(Dialog d) { this.parentDialog = d; }
        
        @android.webkit.JavascriptInterface
        public void closeStudio() {
            new Handler(Looper.getMainLooper()).post(() -> {
                new android.app.AlertDialog.Builder(getContext())
                    .setTitle("⚠️ 退出确认")
                    .setMessage("确定要退出 3D 工作室吗？\n如果没有点击【💾 烘焙打包】，当前的场景布置将会丢失！")
                    .setPositiveButton("坚决退出", (dialog, which) -> { if(parentDialog != null) parentDialog.dismiss(); })
                    .setNegativeButton("点错了", null)
                    .show();
            });
        }
        
        @android.webkit.JavascriptInterface
        public void saveGLB(String base64Data, String baseName) {
            new Thread(() -> {
                try {
                    String cleanBase64 = base64Data.replaceFirst("^data:.*;base64,", "");
                    byte[] data = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT);
                    File outDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "IkemenExports");
                    File tempDir = new File(outDir, baseName); tempDir.mkdirs();
                    
                    File glbFile = new File(tempDir, baseName + "_3DStage.glb");
                    FileOutputStream fos = new FileOutputStream(glbFile);
                    fos.write(data); fos.close();
                    
                    File defFile = new File(tempDir, baseName + ".def");
                    FileOutputStream defOut = new FileOutputStream(defFile);
                    defOut.write(("[Info]\nname = \"" + baseName + "\"\n\n[BGdef]\n\n[Model " + baseName + "整合场景]\nfile = " + baseName + "_3DStage.glb\nposition = 0,0,0\nscale = 1,1,1\n").getBytes("UTF-8"));
                    defOut.close();

                    new Handler(Looper.getMainLooper()).post(() -> {
                        Toast.makeText(getContext(), "✅ 3D场景与动画已烘焙导出！\n文件在:\n" + glbFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                        if(parentDialog != null) parentDialog.dismiss(); // 导出成功后自动退出全屏
                    });
                } catch(Exception e) { e.printStackTrace(); }
            }).start();
        }
    }
}
