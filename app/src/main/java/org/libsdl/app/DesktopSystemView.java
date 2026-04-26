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
        
        taskBtn.setOnTouchListener(new View.OnTouchListener() {
            float startX; float initialTranslation; boolean isDragging = false;
            @Override public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN: startX = event.getRawX(); initialTranslation = v.getTranslationX(); isDragging = false; v.setBackgroundColor(Color.parseColor("#44FFFFFF")); return false; 
                    case MotionEvent.ACTION_MOVE: 
                        float dx = event.getRawX() - startX; if (Math.abs(dx) > 10 * density) { isDragging = true; v.getParent().requestDisallowInterceptTouchEvent(true); }
                        if (isDragging) { v.setTranslationX(initialTranslation + dx); v.bringToFront(); } return true;
                    case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                        v.setBackground(tbBg);
                        if (isDragging) {
                            float currentCenter = v.getX() + v.getTranslationX() + v.getWidth() / 2f; int newIndex = taskbarAppsLayout.getChildCount() - 1;
                            for (int i = 0; i < taskbarAppsLayout.getChildCount(); i++) { View child = taskbarAppsLayout.getChildAt(i); if (child != v && currentCenter < child.getX() + child.getWidth() / 2f) { newIndex = i; break; } }
                            final int targetIndex = newIndex; taskbarAppsLayout.post(() -> { taskbarAppsLayout.removeView(v); v.setTranslationX(0); taskbarAppsLayout.addView(v, targetIndex); });
                            return true;
                        } return false; 
                } return false;
            }
        });

        taskBtn.setOnClickListener(v -> {
            if (windowFrame.getVisibility() == View.VISIBLE) {
                if (windowFrame.getZ() == windowsLayer.getChildCount()) windowFrame.setVisibility(View.GONE); else windowFrame.bringToFront();
            } else { windowFrame.setVisibility(View.VISIBLE); windowFrame.bringToFront(); }
        });
        
        taskBtn.setOnLongClickListener(v -> { showContextMenu(v, finalShortName, () -> { if (onCloseInterceptor != null) onCloseInterceptor.run(); else { windowsLayer.removeView(windowFrame); taskbarAppsLayout.removeView(taskBtn); } }); return true; });
        taskbarAppsLayout.addView(taskBtn, tbParams);

        int w = (int) (rootLayer.getWidth() * 0.70f); int h = (int) (rootLayer.getHeight() * 0.80f); if (w == 0) w = 800; if (h == 0) h = 600; 
        FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(w, h); windowFrame.setLayoutParams(frameParams);
        windowFrame.post(() -> { if (!isMaximized[0]) { windowFrame.setX((rootLayer.getWidth() - windowFrame.getWidth()) / 2f); windowFrame.setY((rootLayer.getHeight() - windowFrame.getHeight()) / 2f); } }); windowsLayer.addView(windowFrame);
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
                                    if (absPath.toLowerCase().endsWith(".gltf") || absPath.toLowerCase().endsWith(".glb")) { if(listener != null) listener.onFileSelected(f); pDialog.dismiss(); }
                                    else Toast.makeText(getContext(), "❌ 请选择 .gltf 或 .glb 模型文件", Toast.LENGTH_SHORT).show();
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
                        boolean success = Api.replaceSffFrame(sffPath, f.group, f.item, finalFile.getAbsolutePath());
                        uiHandler.post(() -> {
                            if (success) { Toast.makeText(getContext(), "✅ " + f.group + "-" + f.item + " 帧已替换！", Toast.LENGTH_SHORT).show(); updateFrameAction.run(); } 
                            else { Toast.makeText(getContext(), "❌ 替换失败：格式不兼容！SFFv1 只能使用 PCX，SFFv2 禁止使用 PCX！", Toast.LENGTH_LONG).show(); }
                        });
                    }).start();
                };
                // 如果选的是文件夹，打开网格图像扫描器；如果是文件，直接替换
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
    // 🗺️ 模块 5：全能地图编辑器 (三重工作流: SFF图层 / DEF代码 / 3D节点)
    // ======================================================================================
    // 图层数据结构定义
    private static class StageLayerInfo {
        String name; boolean isVisible = true; boolean isLocked = false; boolean isGhostGrid = false;
        int group = 0; int item = 0; float startX = 0; float startY = 0; float deltaX = 1; float deltaY = 1;
        public StageLayerInfo cloneLayer() {
            StageLayerInfo copy = new StageLayerInfo();
            copy.name = this.name + " (副本)"; copy.isVisible = this.isVisible; copy.isLocked = this.isLocked;
            copy.group = this.group; copy.item = this.item; copy.startX = this.startX; copy.startY = this.startY;
            return copy;
        }
    }

    private View buildStageEditorContent() {
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#1E1E1E"));

        final String[] currentDefPath = {""};
        final boolean[] is3DMode = {false};
        final int[] currentViewMode = {0}; // 0: SFF图层, 1: DEF代码, 2: 3D模型
        
        final int[] gridAlpha = {70}; final int[] gridColor = {Color.WHITE}; final int[] bgColor = {Color.parseColor("#000080")};

        // --- 初始化 PS 级图层管理器 ---
        final List<StageLayerInfo> layerList = new ArrayList<>();
        final int[] selectedLayerIndex = {0}; final StageLayerInfo[] clipboardLayer = {null}; 
        StageLayerInfo ghostGrid = new StageLayerInfo(); ghostGrid.name = "[系统] 蓝色基准参考网格"; ghostGrid.isGhostGrid = true; ghostGrid.isLocked = true;
        layerList.add(ghostGrid);

        // ================== 1. 顶部总控制栏 ==================
        LinearLayout topBar = new LinearLayout(getContext()); topBar.setOrientation(LinearLayout.HORIZONTAL); topBar.setGravity(Gravity.CENTER_VERTICAL); topBar.setPadding((int)(10*density), (int)(10*density), (int)(10*density), (int)(10*density)); topBar.setBackgroundColor(Color.parseColor("#252526"));
        Button btnScan = createButton("📂 工程", "#0078D7"); Button btnSave = createButton("💾 打包", "#4CAF50"); 
        Button btnMode2D = createButton("📐 2D 地图模式", "#9C27B0"); Button btnMode3D = createButton("🧊 3D 地图模式", "#333333");
        
        LinearLayout.LayoutParams topBp = new LinearLayout.LayoutParams(0, -2, 1f); topBp.setMargins(0, 0, (int)(5*density), 0);
        topBar.addView(btnScan, topBp); topBar.addView(btnSave, topBp); topBar.addView(btnMode2D, topBp); topBar.addView(btnMode3D, topBp);
        root.addView(topBar);

        // ================== 2. 视口切换工作条 ==================
        LinearLayout viewSwitchBar = new LinearLayout(getContext()); viewSwitchBar.setOrientation(LinearLayout.HORIZONTAL); viewSwitchBar.setGravity(Gravity.CENTER); viewSwitchBar.setPadding(0, (int)(5*density), 0, (int)(5*density)); viewSwitchBar.setBackgroundColor(Color.parseColor("#1E1E1E"));
        Button btnViewSff = createButton("🖼️ 2D SFF 贴图视口", "#4CAF50"); 
        Button btnViewDef = createButton("📝 DEF 代码编辑器", "#333333"); 
        Button btnViewModel = createButton("🧊 真 3D 模型视口", "#333333"); btnViewModel.setVisibility(View.GONE);
        viewSwitchBar.addView(btnViewSff, topBp); viewSwitchBar.addView(btnViewDef, topBp); viewSwitchBar.addView(btnViewModel, topBp);
        root.addView(viewSwitchBar);

        LinearLayout mainArea = new LinearLayout(getContext()); mainArea.setOrientation(LinearLayout.HORIZONTAL);
        
        // ================== 3. 左侧大纲与工具栏 ==================
        LinearLayout leftPanel = new LinearLayout(getContext()); leftPanel.setOrientation(LinearLayout.VERTICAL); leftPanel.setBackgroundColor(Color.parseColor("#2D2D30")); leftPanel.setPadding((int)(5*density), (int)(5*density), (int)(5*density), (int)(5*density));
        
        LinearLayout titleRow = new LinearLayout(getContext()); titleRow.setOrientation(LinearLayout.HORIZONTAL); titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView layerTitle = createSubTitle("📑 元素大纲"); layerTitle.setTextColor(Color.WHITE);
        Button btnSettings = createButton("⚙️ 设置", "#3F3F46"); 
        titleRow.addView(layerTitle, new LinearLayout.LayoutParams(0, -2, 1f)); titleRow.addView(btnSettings, new LinearLayout.LayoutParams(-2, -2));
        leftPanel.addView(titleRow);

        // 3.1 SFF 专属工具栏
        LinearLayout psToolsRow = new LinearLayout(getContext()); psToolsRow.setOrientation(LinearLayout.HORIZONTAL); psToolsRow.setPadding(0, (int)(5*density), 0, (int)(5*density));
        Button btnNewLayer = createButton("➕", "#4CAF50"); Button btnCopyLayer = createButton("📄", "#0078D7"); Button btnPasteLayer = createButton("📋", "#FF9800"); Button btnDelLayer = createButton("🗑️", "#E81123");
        LinearLayout.LayoutParams toolBp = new LinearLayout.LayoutParams(0, -2, 1f); toolBp.setMargins((int)(2*density), 0, (int)(2*density), 0);
        psToolsRow.addView(btnNewLayer, toolBp); psToolsRow.addView(btnCopyLayer, toolBp); psToolsRow.addView(btnPasteLayer, toolBp); psToolsRow.addView(btnDelLayer, toolBp);
        leftPanel.addView(psToolsRow);

        // 3.2 SFF 图层列表 (ScrollView)
        ScrollView layerScroll = new ScrollView(getContext());
        final LinearLayout layerListLayout = new LinearLayout(getContext()); layerListLayout.setOrientation(LinearLayout.VERTICAL);
        layerScroll.addView(layerListLayout);

        // 3.3 3D 节点列表 (ScrollView)
        ScrollView modelNodeScroll = new ScrollView(getContext()); modelNodeScroll.setVisibility(View.GONE);
        LinearLayout modelNodeListLayout = new LinearLayout(getContext()); modelNodeListLayout.setOrientation(LinearLayout.VERTICAL);
        // 模拟 3D 节点数据
        Button dummyNode1 = createButton("🧊 Scene Root (变换矩阵)", "#3F3F46"); dummyNode1.setGravity(Gravity.LEFT); modelNodeListLayout.addView(dummyNode1, new LinearLayout.LayoutParams(-1,-2));
        Button dummyNode2 = createButton("   📦 Mesh: Arena_Floor", "#1E1E1E"); dummyNode2.setGravity(Gravity.LEFT); modelNodeListLayout.addView(dummyNode2, new LinearLayout.LayoutParams(-1,-2));
        modelNodeScroll.addView(modelNodeListLayout);

        // 放入同一个容器，占据剩余空间
        FrameLayout leftScrollContainer = new FrameLayout(getContext());
        leftScrollContainer.addView(layerScroll, new FrameLayout.LayoutParams(-1, -1));
        leftScrollContainer.addView(modelNodeScroll, new FrameLayout.LayoutParams(-1, -1));
        leftPanel.addView(leftScrollContainer, new LinearLayout.LayoutParams(-1, 0, 1f));

        // 3.4 统一的折叠导入菜单
        Button btnImportMenu = createButton("📥 导入素材", "#FF9800");
        leftPanel.addView(btnImportMenu, new LinearLayout.LayoutParams(-1, -2));

        // ================== 4. 中心工作区 ==================
        FrameLayout centerContainer = new FrameLayout(getContext());
        
        // 4.1 SFF 视口面板
        LinearLayout sffCenterPanel = new LinearLayout(getContext()); sffCenterPanel.setOrientation(LinearLayout.VERTICAL);
        final Matrix imageMatrix = new Matrix(); 
        final FrameLayout viewportFrame = new FrameLayout(getContext()) {
            Paint gridPaint = new Paint(); Paint axisPaint = new Paint();
            { axisPaint.setColor(Color.parseColor("#FF0000")); axisPaint.setStrokeWidth(2 * density); setWillNotDraw(false); }
            @Override protected void dispatchDraw(Canvas canvas) {
                if (layerList.get(0).isVisible) {
                    canvas.drawColor(bgColor[0]); 
                    gridPaint.setColor(gridColor[0]); gridPaint.setAlpha(gridAlpha[0]); gridPaint.setStrokeWidth(1);
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
                super.dispatchDraw(canvas); 
            }
        };
        final ImageView previewImg = new ImageView(getContext()); previewImg.setScaleType(ImageView.ScaleType.MATRIX);
        viewportFrame.addView(previewImg, new FrameLayout.LayoutParams(-1, -1)); 
        sffCenterPanel.addView(viewportFrame, new LinearLayout.LayoutParams(-1, 0, 1f));

        final Matrix savedMatrix = new Matrix(); final int[] touchMode = {0}; final PointF startPoint = new PointF(); final PointF midPoint = new PointF(); final float[] oldDist = {1f}; 
        viewportFrame.setOnTouchListener((v, event) -> {
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN: savedMatrix.set(imageMatrix); startPoint.set(event.getX(), event.getY()); touchMode[0] = 1; break;
                case MotionEvent.ACTION_POINTER_DOWN: oldDist[0] = (float)Math.sqrt(Math.pow(event.getX(0)-event.getX(1), 2) + Math.pow(event.getY(0)-event.getY(1), 2)); if (oldDist[0] > 10f) { savedMatrix.set(imageMatrix); midPoint.set((event.getX(0)+event.getX(1))/2, (event.getY(0)+event.getY(1))/2); touchMode[0] = 2; } break;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_POINTER_UP: touchMode[0] = 0; break;
                case MotionEvent.ACTION_MOVE:
                    if (touchMode[0] == 1) { imageMatrix.set(savedMatrix); imageMatrix.postTranslate(event.getX() - startPoint.x, event.getY() - startPoint.y); } 
                    else if (touchMode[0] == 2) { float newDist = (float)Math.sqrt(Math.pow(event.getX(0)-event.getX(1), 2) + Math.pow(event.getY(0)-event.getY(1), 2)); if (newDist > 10f) { imageMatrix.set(savedMatrix); float scale = newDist / oldDist[0]; imageMatrix.postScale(scale, scale, midPoint.x, midPoint.y); } } break;
            } previewImg.setImageMatrix(imageMatrix); viewportFrame.invalidate(); return true;
        });
        viewportFrame.post(() -> { imageMatrix.postTranslate(viewportFrame.getWidth() / 2f, viewportFrame.getHeight() * 0.8f); previewImg.setImageMatrix(imageMatrix); viewportFrame.invalidate(); });

        LinearLayout dpadArea = new LinearLayout(getContext()); dpadArea.setOrientation(LinearLayout.HORIZONTAL); dpadArea.setGravity(Gravity.CENTER); dpadArea.setPadding(0, (int)(10*density), 0, (int)(10*density)); dpadArea.setBackgroundColor(Color.parseColor("#1E1E1E"));
        Button btnLeft = createButton("◀", "#333333"); Button btnUp = createButton("▲", "#333333"); Button btnDown = createButton("▼", "#333333"); Button btnRight = createButton("▶", "#333333");
        TextView txtCoord = new TextView(getContext()); txtCoord.setText("  X: 0   Y: 0  "); txtCoord.setTextColor(Color.WHITE); applyGlobalFontSettings(txtCoord, 1.0f, true);
        dpadArea.addView(btnLeft); dpadArea.addView(btnUp); dpadArea.addView(btnDown); dpadArea.addView(btnRight); dpadArea.addView(txtCoord);
        sffCenterPanel.addView(dpadArea, new LinearLayout.LayoutParams(-1, -2));

        // 4.2 DEF 代码编辑区
        ScrollView defEditorPanel = new ScrollView(getContext()); defEditorPanel.setBackgroundColor(Color.parseColor("#1E1E1E"));
        EditText defCodeInput = new EditText(getContext()); defCodeInput.setBackgroundColor(Color.TRANSPARENT); defCodeInput.setTextColor(Color.parseColor("#D4D4D4")); defCodeInput.setGravity(Gravity.TOP | Gravity.LEFT); defCodeInput.setTypeface(Typeface.MONOSPACE); applyGlobalFontSettings(defCodeInput, 1.0f, false);
        defEditorPanel.addView(defCodeInput, new FrameLayout.LayoutParams(-1, -2)); defEditorPanel.setVisibility(View.GONE); 

        // 4.3 3D 模型视口区
        LinearLayout modelCenterPanel = new LinearLayout(getContext()); modelCenterPanel.setOrientation(LinearLayout.VERTICAL); modelCenterPanel.setVisibility(View.GONE);
        FrameLayout modelViewportFrame = new FrameLayout(getContext()); modelViewportFrame.setBackgroundColor(Color.parseColor("#121212"));
        TextView modelHint = new TextView(getContext()); modelHint.setText("🧊 3D 模型实时渲染与节点变换区\n(支持双指旋转缩放)"); modelHint.setTextColor(Color.GRAY); applyGlobalFontSettings(modelHint, 1.3f, true); modelHint.setGravity(Gravity.CENTER);
        modelViewportFrame.addView(modelHint, new FrameLayout.LayoutParams(-1, -1));
        modelCenterPanel.addView(modelViewportFrame, new LinearLayout.LayoutParams(-1, 0, 1f));

        centerContainer.addView(sffCenterPanel, new FrameLayout.LayoutParams(-1, -1));
        centerContainer.addView(defEditorPanel, new FrameLayout.LayoutParams(-1, -1));
        centerContainer.addView(modelCenterPanel, new FrameLayout.LayoutParams(-1, -1));

        // ================== 5. 右侧属性面板 ==================
        ScrollView rightScroll = new ScrollView(getContext()); rightScroll.setBackgroundColor(Color.parseColor("#252526"));
        LinearLayout rightPanel = new LinearLayout(getContext()); rightPanel.setOrientation(LinearLayout.VERTICAL); rightPanel.setPadding((int)(10*density), (int)(10*density), (int)(10*density), (int)(10*density));

        LinearLayout panel2D = new LinearLayout(getContext()); panel2D.setOrientation(LinearLayout.VERTICAL);
        panel2D.addView(createSubTitle("📐 2D 尺寸与视差混合")); panel2D.addView(createInput("缩放比例 (默认1.0)", "1.0")); panel2D.addView(createInput("X轴滚动视差 (Delta)", "1.0")); panel2D.addView(createInput("Y轴滚动视差 (Delta)", "1.0")); panel2D.addView(createInput("透明混合模式 (Trans)", "none"));
        rightPanel.addView(panel2D);

        LinearLayout panel3D = new LinearLayout(getContext()); panel3D.setOrientation(LinearLayout.VERTICAL);
        panel3D.addView(createSubTitle("🧊 3D 空间与光照")); panel3D.addView(createInput("全局位移 (Offset X,Y,Z)", "0,0,0")); panel3D.addView(createInput("全局缩放 (Scale X,Y,Z)", "1,1,1")); panel3D.addView(createInput("环境光强度 (Intensity)", "1.0"));
        panel3D.setVisibility(View.GONE); rightPanel.addView(panel3D);

        rightScroll.addView(rightPanel);

        // 组装主界面
        mainArea.addView(leftPanel, new LinearLayout.LayoutParams(0, -1, 1.3f)); mainArea.addView(centerContainer, new LinearLayout.LayoutParams(0, -1, 2f)); mainArea.addView(rightScroll, new LinearLayout.LayoutParams(0, -1, 1f));
        root.addView(mainArea, new LinearLayout.LayoutParams(-1, -1));

        // ================== 核心功能与状态机绑定 ==================

        // 刷新 SFF 图层列表 UI
        Runnable refreshLayerListUI = new Runnable() {
            @Override public void run() {
                layerListLayout.removeAllViews();
                for (int i = layerList.size() - 1; i >= 0; i--) {
                    final int idx = i; final StageLayerInfo info = layerList.get(i);
                    LinearLayout row = new LinearLayout(getContext()); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setBackgroundColor(selectedLayerIndex[0] == idx ? Color.parseColor("#0078D7") : Color.parseColor("#3F3F46"));
                    row.setPadding((int)(5*density), (int)(8*density), (int)(5*density), (int)(8*density));
                    
                    Button btnVis = createButton(info.isVisible ? "👁️" : "❌", "#333333"); btnVis.setPadding(0,(int)(5*density),0,(int)(5*density));
                    btnVis.setOnClickListener(v -> { info.isVisible = !info.isVisible; this.run(); viewportFrame.invalidate(); });
                    Button bLock = createButton(info.isLocked ? "🔒" : "🔓", "#333333"); bLock.setPadding(0,(int)(5*density),0,(int)(5*density));
                    bLock.setOnClickListener(v -> { info.isLocked = !info.isLocked; this.run(); });
                    TextView tName = new TextView(getContext()); tName.setText(" " + info.name); tName.setTextColor(info.isGhostGrid ? Color.parseColor("#8888FF") : Color.WHITE); applyGlobalFontSettings(tName, 0.9f, false);
                    
                    row.addView(btnVis, new LinearLayout.LayoutParams((int)(40*density), -2)); row.addView(bLock, new LinearLayout.LayoutParams((int)(40*density), -2)); row.addView(tName, new LinearLayout.LayoutParams(0, -2, 1f));
                    row.setOnClickListener(v -> { selectedLayerIndex[0] = idx; this.run(); });
                    layerListLayout.addView(row, new LinearLayout.LayoutParams(-1, -2));
                }
            }
        };

        // 视口状态切换引擎
        Runnable updateViewState = () -> {
            sffCenterPanel.setVisibility(View.GONE); defEditorPanel.setVisibility(View.GONE); modelCenterPanel.setVisibility(View.GONE);
            layerScroll.setVisibility(View.GONE); modelNodeScroll.setVisibility(View.GONE); psToolsRow.setVisibility(View.GONE);
            btnViewSff.setBackgroundColor(Color.parseColor("#333333")); btnViewDef.setBackgroundColor(Color.parseColor("#333333")); btnViewModel.setBackgroundColor(Color.parseColor("#333333"));
            
            if (currentViewMode[0] == 0) { // SFF 图层视口
                sffCenterPanel.setVisibility(View.VISIBLE); layerScroll.setVisibility(View.VISIBLE); psToolsRow.setVisibility(View.VISIBLE);
                btnViewSff.setBackgroundColor(Color.parseColor("#4CAF50"));
            } else if (currentViewMode[0] == 1) { // DEF 代码编辑器
                defEditorPanel.setVisibility(View.VISIBLE); layerScroll.setVisibility(View.VISIBLE); // 代码模式下保留左侧图层大纲作参考
                btnViewDef.setBackgroundColor(Color.parseColor("#FF9800"));
            } else if (currentViewMode[0] == 2) { // 3D 模型视口
                modelCenterPanel.setVisibility(View.VISIBLE); modelNodeScroll.setVisibility(View.VISIBLE); // 切换为 3D 节点大纲
                btnViewModel.setBackgroundColor(Color.parseColor("#0078D7"));
            }
        };

        btnViewSff.setOnClickListener(v -> { currentViewMode[0] = 0; updateViewState.run(); });
        btnViewDef.setOnClickListener(v -> { currentViewMode[0] = 1; updateViewState.run(); });
        btnViewModel.setOnClickListener(v -> { currentViewMode[0] = 2; updateViewState.run(); });

        // 2D/3D 全局模式切换
        btnMode2D.setOnClickListener(v -> { 
            is3DMode[0] = false; btnMode2D.setBackgroundColor(Color.parseColor("#9C27B0")); btnMode3D.setBackgroundColor(Color.parseColor("#333333")); 
            btnViewModel.setVisibility(View.GONE); panel2D.setVisibility(View.VISIBLE); panel3D.setVisibility(View.GONE);
            if(currentViewMode[0] == 2) { currentViewMode[0] = 0; updateViewState.run(); } // 如果在模型视口，强行切回 SFF
        });
        btnMode3D.setOnClickListener(v -> { 
            is3DMode[0] = true; btnMode3D.setBackgroundColor(Color.parseColor("#0078D7")); btnMode2D.setBackgroundColor(Color.parseColor("#333333")); 
            btnViewModel.setVisibility(View.VISIBLE); panel2D.setVisibility(View.VISIBLE); panel3D.setVisibility(View.VISIBLE); 
        });

        // 动态导入菜单弹窗
        btnImportMenu.setOnClickListener(v -> {
            final Dialog iDialog = new Dialog(getContext()); iDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            LinearLayout iBox = new LinearLayout(getContext()); iBox.setOrientation(LinearLayout.VERTICAL); iBox.setBackgroundColor(Color.parseColor("#252526")); iBox.setPadding((int)(20*density),(int)(20*density),(int)(20*density),(int)(20*density));
            GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#252526")); border.setStroke((int)(2*density), Color.parseColor("#0078D7")); border.setCornerRadius(15*density); iBox.setBackground(border);
            
            iBox.addView(createSubTitle("📥 导入素材到地图"));
            
            Button iSff = createButton("🖼️ 导入 SFF / GIF 图像图层", "#FF9800");
            iSff.setOnClickListener(bv -> { iDialog.dismiss(); showWin10FilePicker("选择图像素材 (自动拆解 GIF)", 7, null, null, file -> Toast.makeText(getContext(), "准备装载 SFF: " + file.getName(), Toast.LENGTH_SHORT).show()); });
            iBox.addView(iSff, new LinearLayout.LayoutParams(-1, -2));

            if (is3DMode[0]) {
                Button iMod = createButton("🧊 导入真 3D 模型 (.gltf/.glb)", "#0078D7");
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0,(int)(10*density),0,0);
                iMod.setOnClickListener(bv -> { 
                    iDialog.dismiss(); 
                    showWin10FilePicker("选择 3D 模型工程", 11, null, null, file -> {
                        FileCallback onSelected = f -> Toast.makeText(getContext(), "已挂载 3D 模型: " + f.getName(), Toast.LENGTH_SHORT).show();
                        if(file.isDirectory()) showGenericFileListPicker(file, new String[]{".gltf", ".glb"}, "3D模型", "#0078D7", onSelected); else onSelected.onFileSelected(file);
                    }); 
                });
                iBox.addView(iMod, lp);
            }
            
            Button iCancel = createButton("❌ 取消", "#333333");
            LinearLayout.LayoutParams lpC = new LinearLayout.LayoutParams(-1, -2); lpC.setMargins(0,(int)(10*density),0,0);
            iCancel.setOnClickListener(bv -> iDialog.dismiss());
            iBox.addView(iCancel, lpC);
            
            iDialog.setContentView(iBox); iDialog.show();
        });

        // 视口偏好设置
        btnSettings.setOnClickListener(v -> {
            final Dialog setDialog = new Dialog(getContext()); setDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            LinearLayout setBox = new LinearLayout(getContext()); setBox.setOrientation(LinearLayout.VERTICAL); setBox.setBackgroundColor(Color.parseColor("#252526")); setBox.setPadding((int)(20*density),(int)(20*density),(int)(20*density),(int)(20*density));
            GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#252526")); border.setStroke((int)(2*density), Color.parseColor("#0078D7")); border.setCornerRadius(15*density); setBox.setBackground(border);
            
            setBox.addView(createTitle("⚙️ 视口偏好设置"));
            setBox.addView(createSubTitle("背景底色代码 (Hex):")); EditText bgInput = createInput("如: #000080", String.format("#%06X", (0xFFFFFF & bgColor[0]))); setBox.addView(bgInput);
            setBox.addView(createSubTitle("网格线颜色代码 (Hex):")); EditText gridColorInput = createInput("如: #FFFFFF", String.format("#%06X", (0xFFFFFF & gridColor[0]))); setBox.addView(gridColorInput);
            setBox.addView(createSubTitle("网格透明度 (0-255):"));
            SeekBar alphaBar = new SeekBar(getContext()); alphaBar.setMax(255); alphaBar.setProgress(gridAlpha[0]);
            alphaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(SeekBar s, int p, boolean b) { gridAlpha[0] = p; viewportFrame.invalidate(); } public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {} }); setBox.addView(alphaBar);

            Button btnApply = createButton("✔️ 应用设置", "#4CAF50"); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0,(int)(15*density),0,0);
            btnApply.setOnClickListener(bv -> { try { bgColor[0] = Color.parseColor(bgInput.getText().toString()); gridColor[0] = Color.parseColor(gridColorInput.getText().toString()); } catch (Exception e){} viewportFrame.invalidate(); setDialog.dismiss(); });
            setBox.addView(btnApply, lp); setDialog.setContentView(setBox); setDialog.show();
        });

        // PS 工具栏逻辑
        btnNewLayer.setOnClickListener(v -> { StageLayerInfo newL = new StageLayerInfo(); newL.name = "[BG] 新建图层 " + layerList.size(); layerList.add(newL); selectedLayerIndex[0] = layerList.size() - 1; refreshLayerListUI.run(); });
        btnCopyLayer.setOnClickListener(v -> { if (selectedLayerIndex[0] == 0) { Toast.makeText(getContext(), "❌ 无法复制幽灵网格", Toast.LENGTH_SHORT).show(); return; } clipboardLayer[0] = layerList.get(selectedLayerIndex[0]).cloneLayer(); Toast.makeText(getContext(), "✅ 已复制图层", Toast.LENGTH_SHORT).show(); });
        btnPasteLayer.setOnClickListener(v -> { if (clipboardLayer[0] != null) { layerList.add(clipboardLayer[0].cloneLayer()); selectedLayerIndex[0] = layerList.size() - 1; refreshLayerListUI.run(); } else Toast.makeText(getContext(), "❌ 剪贴板为空", Toast.LENGTH_SHORT).show(); });
        btnDelLayer.setOnClickListener(v -> { int targetIdx = selectedLayerIndex[0]; if (targetIdx == 0) { Toast.makeText(getContext(), "🛡️ 引擎保护：幽灵网格底板不可被删除，只能隐藏！", Toast.LENGTH_SHORT).show(); return; } layerList.remove(targetIdx); selectedLayerIndex[0] = Math.max(0, targetIdx - 1); refreshLayerListUI.run(); });

        // 工程状态机
        btnScan.setOnClickListener(v -> {
            final Dialog prompt = new Dialog(getContext()); prompt.requestWindowFeature(Window.FEATURE_NO_TITLE); prompt.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            LinearLayout box = new LinearLayout(getContext()); box.setOrientation(LinearLayout.VERTICAL); box.setBackgroundColor(Color.parseColor("#252526")); box.setPadding((int)(20*density),(int)(20*density),(int)(20*density),(int)(20*density));
            GradientDrawable border = new GradientDrawable(); border.setColor(Color.parseColor("#252526")); border.setStroke((int)(2*density), Color.parseColor("#0078D7")); border.setCornerRadius(15*density); box.setBackground(border);
            
            box.addView(createSubTitle("📂 工程管理"));
            Button btnNew = createButton("📄 新建空白地图", "#4CAF50");
            btnNew.setOnClickListener(bv -> { currentDefPath[0] = ""; isEditMode[0] = false; layerList.clear(); layerList.add(ghostGrid); refreshLayerListUI.run(); defCodeInput.setText("[Info]\nname = \"NewStage\"\n\n[BGdef]\nspr = stages/NewStage.sff\ndebugbg = 0"); Toast.makeText(getContext(), "已建立新工程", Toast.LENGTH_SHORT).show(); prompt.dismiss(); });
            
            Button btnLoad = createButton("📂 读取现有 .def 地图", "#0078D7");
            btnLoad.setOnClickListener(bv -> {
                prompt.dismiss();
                showWin10FilePicker("选择 .def 地图工程", 10, null, null, selectedFile -> {
                    FileCallback loadDefAction = finalDef -> {
                        final Dialog safePrompt = new Dialog(getContext()); safePrompt.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                        LinearLayout safeBox = new LinearLayout(getContext()); safeBox.setOrientation(LinearLayout.VERTICAL); safeBox.setBackgroundColor(Color.parseColor("#252526")); safeBox.setPadding((int)(20*density),(int)(20*density),(int)(20*density),(int)(20*density)); safeBox.setBackground(border);
                        safeBox.addView(createSubTitle("🛡️ 安全修改提示"));
                        
                        Runnable performLoad = () -> {
                            try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(currentDefPath[0]))) {
                                StringBuilder sb = new StringBuilder(); String line; while ((line = br.readLine()) != null) sb.append(line).append("\n");
                                defCodeInput.setText(sb.toString());
                                StageLayerInfo loadedLayer = new StageLayerInfo(); loadedLayer.name = "[BG] 从代码载入"; layerList.add(loadedLayer); refreshLayerListUI.run();
                            } catch (Exception e) {}
                            
                            File targetDef = new File(currentDefPath[0]); File sffFile = new File(targetDef.getParent(), targetDef.getName().replace(".def", ".sff"));
                            if (!sffFile.exists()) sffFile = new File(targetDef.getParent(), targetDef.getName().replace(".def", ".SFF"));
                            if (sffFile.exists()) {
                                final String finalSffPath = sffFile.getAbsolutePath();
                                new Thread(() -> { byte[] pb = Api.getSffPreview(finalSffPath); if (pb != null && pb.length > 0) { Bitmap bmp = BitmapFactory.decodeByteArray(pb, 0, pb.length); new Handler(Looper.getMainLooper()).post(() -> { previewImg.setImageBitmap(bmp); Toast.makeText(getContext(), "✅ 已自动挂载同名 SFF 图像", Toast.LENGTH_SHORT).show(); }); } }).start();
                            }
                        };

                        Button btnBackup = createButton("💾 自动创建防毁备份并读取", "#4CAF50");
                        btnBackup.setOnClickListener(sv -> { try { File backup = new File(finalDef.getParent(), finalDef.getName().replace(".def", "_backup.def")); if (!backup.exists()) copyFileToSandbox(finalDef, backup); currentDefPath[0] = backup.getAbsolutePath(); isEditMode[0] = true; Toast.makeText(getContext(), "✅ 已切换至备份工程: " + backup.getName(), Toast.LENGTH_SHORT).show(); performLoad.run(); } catch(Exception e){} safePrompt.dismiss(); });
                        Button btnOrig = createButton("⚠️ 直接读取并修改原文件", "#FF9800");
                        btnOrig.setOnClickListener(sv -> { currentDefPath[0] = finalDef.getAbsolutePath(); isEditMode[0] = true; performLoad.run(); safePrompt.dismiss(); });
                        Button btnCancel = createButton("❌ 取消", "#333333"); btnCancel.setOnClickListener(sv -> safePrompt.dismiss());
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0,0,0,(int)(10*density)); safeBox.addView(btnBackup, lp); safeBox.addView(btnOrig, lp); safeBox.addView(btnCancel, lp); safePrompt.setContentView(safeBox); safePrompt.show();
                    };
                    if (selectedFile.isDirectory()) showGenericFileListPicker(selectedFile, new String[]{".def"}, "地图工程", "#E81123", loadDefAction); else loadDefAction.onFileSelected(selectedFile);
                });
            });
            Button btnCancelMain = createButton("❌ 取消", "#333333"); btnCancelMain.setOnClickListener(bv -> prompt.dismiss()); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0,0,0,(int)(10*density)); box.addView(btnNew, lp); box.addView(btnLoad, lp); box.addView(btnCancelMain, lp); prompt.setContentView(box); prompt.show();
        });

        // 打包导出
        btnSave.setOnClickListener(v -> {
            Toast.makeText(getContext(), "正在执行底层编译打包...", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                try {
                    StringBuilder jsonBGs = new StringBuilder();
                    for (int i = 1; i < layerList.size(); i++) {
                        StageLayerInfo info = layerList.get(i);
                        jsonBGs.append("    {\"_name\": \"").append(info.name).append("\", \"type\": \"normal\", \"spriteno\": \"").append(info.group).append(",").append(info.item).append("\", \"start\": \"").append(info.startX).append(",").append(info.startY).append("\"}");
                        if (i < layerList.size() - 1) jsonBGs.append(",\n");
                    }
                    String stageJson = "{\n  \"Info\": {\"name\": \"MyMobileStage\", \"displayname\": \"Mobile Stage\", \"author\": \"IkemenEditor\"},\n  \"Camera\": {\"startx\": 0, \"starty\": 0, \"boundleft\": -150, \"boundright\": 150},\n  \"BGs\": [\n" + jsonBGs.toString() + "\n  ]\n}";
                    
                    File exportDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "IkemenExports");
                    if (!exportDir.exists()) exportDir.mkdirs();
                    String resultPath = Api.exportStageDef(exportDir.getAbsolutePath(), stageJson);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (resultPath != null && !resultPath.isEmpty()) { Toast.makeText(getContext(), "✅ 地图已成功打包至：\n" + resultPath, Toast.LENGTH_LONG).show(); } 
                        else { Toast.makeText(getContext(), "❌ 编译失败：底层拦截或 JSON 语法错误", Toast.LENGTH_LONG).show(); }
                    });
                } catch (Throwable t) {}
            }).start();
        });

        updateViewState.run(); // 初始化视口状态
        refreshLayerListUI.run();
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
}
