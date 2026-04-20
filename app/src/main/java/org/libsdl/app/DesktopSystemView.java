package org.libsdl.app;

import android.animation.ObjectAnimator;
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

    private void updateUI(final TextView status, final String msg) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (status != null) status.setText(msg);
        });
    }

    public interface OnFileSelectedListener { void onFileSelected(File file); }

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

        setupTaskbar();
        setContentView(rootLayer);
        rootLayer.post(() -> setupDesktopIcons());
    }

    private void initMouseEngine() {
        cursorPaintFill = new Paint(Paint.ANTI_ALIAS_FLAG); cursorPaintFill.setColor(Color.WHITE); cursorPaintFill.setStyle(Paint.Style.FILL);
        cursorPaintStroke = new Paint(Paint.ANTI_ALIAS_FLAG); cursorPaintStroke.setColor(Color.BLACK); cursorPaintStroke.setStyle(Paint.Style.STROKE); cursorPaintStroke.setStrokeWidth(1.5f * density);
        cursorPath = new Path(); cursorPath.moveTo(0, 0); cursorPath.lineTo(0, 35); cursorPath.lineTo(9, 26); cursorPath.lineTo(16, 42); cursorPath.lineTo(22, 38); cursorPath.lineTo(15, 22); cursorPath.lineTo(26, 22); cursorPath.close();
        Matrix scaleMatrix = new Matrix(); scaleMatrix.setScale(density * 0.4f, density * 0.4f); cursorPath.transform(scaleMatrix);
    }

    private void setupTaskbar() {
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
        
        startBtn.setOnClickListener(v -> {
            hide(); if (SDLActivity.mSingleton != null) SDLActivity.mSingleton.toggleDesktopMode(false);
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
    }

    private void findFilesRecursively(File dir, List<File> resultList, String targetExtension) {
        if (dir == null || !dir.exists() || !dir.canRead()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) findFilesRecursively(f, resultList, targetExtension);
            else if (f.getName().toLowerCase().endsWith(targetExtension)) resultList.add(f);
        }
    }

    private void refreshDesktopBackground() {
        desktopBgLayer.removeAllViews(); View media = createMediaBackground(customDesktopBg, bgAlpha, true);
        if (media != null) desktopBgLayer.addView(media, new FrameLayout.LayoutParams(-1, -1));
        else { GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.parseColor("#1B1B1B"), Color.parseColor("#2D2D30")}); bg.setAlpha(bgAlpha); desktopBgLayer.setBackground(bg); }
    }

    private View createMediaBackground(String uriString, int alpha, final boolean isDesktopBg) {
        if (uriString == null || uriString.trim().isEmpty()) return null;
        File f = new File(uriString); if (!f.exists()) return null;
        Uri uri = Uri.parse("file://" + uriString); String p = uriString.toLowerCase();
        boolean isVideo = p.endsWith(".mp4") || p.endsWith(".avi") || p.endsWith(".mkv") || p.endsWith(".webm");
        if (isVideo) {
            final TextureView tv = new TextureView(mContext);
            tv.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                private MediaPlayer mp;
                @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    try {
                        mp = new MediaPlayer(); mp.setDataSource(mContext, uri); mp.setSurface(new Surface(surface)); mp.setLooping(true);
                        mp.prepareAsync();
                        mp.setOnPreparedListener(m -> {
                            if (isDesktopBg) bgMediaPlayer = m; else winMediaPlayers.add(m);
                            m.start();
                        });
                    } catch (Exception e) {}
                }
                @Override public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) {}
                @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { if(mp!=null) mp.release(); return true; }
                @Override public void onSurfaceTextureUpdated(SurfaceTexture s) {}
            });
            return tv;
        }
        ImageView iv = new ImageView(mContext); iv.setImageURI(uri); iv.setScaleType(ImageView.ScaleType.CENTER_CROP); iv.setAlpha(alpha/255f);
        return iv;
    }

    private void applyGlobalFontSettings(TextView tv, float sizeMultiplier, boolean isBold) {
        if (customFont != null) tv.setTypeface(customFont, isBold ? Typeface.BOLD : Typeface.NORMAL);
        else tv.setTypeface(null, isBold ? Typeface.BOLD : Typeface.NORMAL);
        tv.setTextColor(fontColor); tv.setTextSize(fontSize * sizeMultiplier);
    }

    private void setupDesktopIcons() {
        desktopIconsLayer.removeAllViews(); 
        createDesktopIcon("sys_settings", "⚙️", "系统控制台");
        createDesktopIcon("asset_extractor", "🖼️", "SFF查看器"); 
        createDesktopIcon("snd_extractor", "🎵", "SND查看器"); 
        createDesktopIcon("gif_extractor", "🎞️", "GIF拆解器"); 
    }

    private void createDesktopIcon(final String id, String iconStr, String name) {
        final LinearLayout iconLayout = new LinearLayout(getContext()); iconLayout.setOrientation(LinearLayout.VERTICAL); iconLayout.setGravity(Gravity.CENTER);
        float actualGrid = gridSizeBase * density; float iconSize = actualGrid - 2f * density; 
        TextView iconView = new TextView(getContext()); iconView.setText(iconStr); iconView.setTextSize(26f); iconView.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#44000000")); bg.setCornerRadius(6f*density); 
        iconView.setBackground(bg); iconLayout.addView(iconView, (int)(iconSize*0.6f), (int)(iconSize*0.6f));
        TextView nameView = new TextView(getContext()); nameView.setText(name); applyGlobalFontSettings(nameView, 1.0f, false);
        iconLayout.addView(nameView); desktopIconsLayer.addView(iconLayout, (int)iconSize, (int)iconSize);

        iconLayout.setX(prefs.getFloat("icon_x_" + id, 20*density)); iconLayout.setY(prefs.getFloat("icon_y_" + id, 20*density));
        iconLayout.setOnTouchListener(new View.OnTouchListener() {
            float ox, oy; boolean drag = false; long last = 0;
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) { ox = v.getX() - e.getRawX(); oy = v.getY() - e.getRawY(); drag = false; }
                else if (e.getAction() == MotionEvent.ACTION_MOVE) { if(Math.abs(ox+e.getRawX()-v.getX())>5) { v.setX(ox + e.getRawX()); v.setY(oy + e.getRawY()); drag = true; } }
                else if (e.getAction() == MotionEvent.ACTION_UP) {
                    prefs.edit().putFloat("icon_x_"+id, v.getX()).putFloat("icon_y_"+id, v.getY()).apply();
                    if (!drag && System.currentTimeMillis() - last < 500) {
                        if (id.equals("sys_settings")) openSettingsInAppWindow();
                        else if (id.equals("asset_extractor")) openAppWindow("🖼️ SFF查看器", buildSffExtractorContent(), null);
                        else if (id.equals("snd_extractor")) openAppWindow("🎵 SND查看器", buildSndExtractorContent(), null);
                        else if (id.equals("gif_extractor")) openAppWindow("🎞️ GIF拆解器", buildGifExtractorContent(), null);
                    } last = System.currentTimeMillis();
                } return true;
            }
        });
    }

    private void openAppWindow(String title, View content, Runnable onClosing) {
        if (windowsLayer.findViewWithTag(title) != null) return;
        final FrameLayout win = new FrameLayout(getContext()); win.setTag(title);
        GradientDrawable gd = new GradientDrawable(); gd.setColor(Color.parseColor("#EE1E1E1E")); gd.setStroke(2, Color.parseColor("#3F3F46"));
        win.setBackground(gd); win.setElevation(20 * density);
        LinearLayout main = new LinearLayout(getContext()); main.setOrientation(LinearLayout.VERTICAL); win.addView(main);
        LinearLayout head = new LinearLayout(getContext()); head.setBackgroundColor(Color.parseColor("#2D2D30")); head.setGravity(Gravity.CENTER_VERTICAL);
        TextView tv = new TextView(getContext()); tv.setText(" " + title); applyGlobalFontSettings(tv, 1.1f, true);
        head.addView(tv, new LinearLayout.LayoutParams(0, -1, 1f));
        TextView close = new TextView(getContext()); close.setText(" ✕ "); applyGlobalFontSettings(close, 1.1f, true);
        close.setOnClickListener(v -> { if(onClosing!=null) onClosing.run(); windowsLayer.removeView(win); });
        head.addView(close); main.addView(head, -1, (int)(35*density)); main.addView(content, -1, -1);
        int w = (int)(rootLayer.getWidth()*0.8f), h = (int)(rootLayer.getHeight()*0.8f);
        windowsLayer.addView(win, w, h); win.setX((rootLayer.getWidth()-w)/2f); win.setY((rootLayer.getHeight()-h)/2f);
        head.setOnTouchListener((v, e) -> { if(e.getAction()==MotionEvent.ACTION_MOVE) { win.setX(e.getRawX()-w/2f); win.setY(e.getRawY()-15*density); } return true; });
    }

    private void loadDesktopSettings() {
        bgAlpha = prefs.getInt("dt_bgAlpha", 180); gridSizeBase = prefs.getInt("dt_gridSize", 100);
        customDesktopBg = prefs.getString("dt_customDeskBg", ""); fontColor = prefs.getInt("dt_fontColor", Color.WHITE);
        fontSize = prefs.getFloat("dt_fontSize", 12f);
    }

    private void openSettingsInAppWindow() {
        ScrollView scroll = new ScrollView(getContext()); LinearLayout l = new LinearLayout(getContext()); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(30,30,30,30);
        l.addView(createTitle("🖥️ 桌面布局"));
        SeekBar sb = new SeekBar(getContext()); sb.setMax(255); sb.setProgress(bgAlpha);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean b) { bgAlpha = p; refreshDesktopBackground(); }
            @Override public void onStartTrackingTouch(SeekBar s) {} @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        l.addView(sb);
        Button pick = createButton("📂 选择壁纸文件", "#444444");
        pick.setOnClickListener(v -> showWin10FilePicker("选壁纸", 1, null, null, f -> { customDesktopBg = f.getAbsolutePath(); refreshDesktopBackground(); }));
        l.addView(pick);
        scroll.addView(l); openAppWindow("⚙ 系统控制台", scroll, () -> prefs.edit().putInt("dt_bgAlpha", bgAlpha).putString("dt_customDeskBg", customDesktopBg).apply());
    }

    // ======================================================================================
    // 🎨 模块 1：SFF 检视工坊
    // ======================================================================================
    private View buildSffExtractorContent() {
        LinearLayout root = new LinearLayout(getContext()); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(20,20,20,20);
        final TextView status = new TextView(getContext()); status.setText("📡 准备扫描..."); applyGlobalFontSettings(status, 1.0f, false);
        final LinearLayout gallery = new LinearLayout(getContext()); gallery.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(getContext()); sv.addView(gallery);
        Button scan = createButton("📂 浏览 SFF 文件", "#0078D7");
        scan.setOnClickListener(v -> showWin10FilePicker("选择SFF", 4, null, null, f -> startAssetScanner(f, gallery, status)));
        root.addView(scan); root.addView(status); root.addView(sv, -1, -1);
        return root;
    }

    private void startAssetScanner(File f, LinearLayout container, TextView status) {
        container.removeAllViews(); updateUI(status, "正在分析...");
        new Thread(() -> {
            try {
                String json = Api.scanSff(f.getAbsolutePath());
                org.json.JSONArray arr = new org.json.JSONArray(json);
                new Handler(Looper.getMainLooper()).post(() -> {
                    for(int i=0; i<arr.length(); i++) {
                        try {
                            org.json.JSONObject obj = arr.getJSONObject(i);
                            final String path = obj.getString("filePath"); final String name = obj.getString("name");
                            byte[] pb = Api.getSffPreview(path);
                            if(pb == null) pb = Api.decodeSffFrame(path, 0, 0);
                            Bitmap bmp = (pb != null) ? BitmapFactory.decodeByteArray(pb, 0, pb.length) : null;
                            LinearLayout card = new LinearLayout(getContext()); card.setPadding(10,10,10,10);
                            ImageView iv = new ImageView(getContext()); iv.setImageBitmap(bmp); card.addView(iv, (int)(60*density), (int)(60*density));
                            TextView tv = new TextView(getContext()); tv.setText(" " + name); applyGlobalFontSettings(tv, 1.0f, true); card.addView(tv);
                            card.setOnClickListener(v -> showAssetViewerWindow(name, path)); container.addView(card);
                        } catch (Exception e) {}
                    }
                    status.setText("✅ 扫描完成");
                });
            } catch (Exception e) {}
        }).start();
    }

    private void showAssetViewerWindow(String name, String path) {
        LinearLayout root = new LinearLayout(getContext()); root.setOrientation(LinearLayout.VERTICAL);
        final ImageView preview = new ImageView(getContext()); preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(preview, -1, 0, 1f);

        HorizontalScrollView hsv = new HorizontalScrollView(getContext()); hsv.setHorizontalScrollBarEnabled(false);
        final LinearLayout toolBelt = new LinearLayout(getContext()); toolBelt.setPadding(10,10,10,10);
        hsv.addView(toolBelt); root.addView(hsv, -1, (int)(65*density));

        new Thread(() -> {
            try {
                String fJson = Api.getAllFrames(path); org.json.JSONArray fArr = new org.json.JSONArray(fJson);
                final List<Integer> groups = new ArrayList<>();
                for(int i=0; i<fArr.length(); i++) {
                    int g = fArr.getJSONObject(i).getInt("group");
                    if(!groups.contains(g)) groups.add(g);
                }
                new Handler(Looper.getMainLooper()).post(() -> {
                    for(int g : groups) {
                        Button b = createButton("组:"+g, "#2D2D30");
                        b.setOnClickListener(v -> {
                            byte[] pb = Api.decodeSffFrame(path, g, 0);
                            if(pb!=null) preview.setImageBitmap(BitmapFactory.decodeByteArray(pb,0,pb.length));
                        });
                        toolBelt.addView(b);
                    }
                });
            } catch (Exception e) {}
        }).start();

        Button replace = createButton("🔄 替换当前组第0帧", "#4CAF50");
        replace.setOnClickListener(v -> showWin10FilePicker("选图", 7, null, null, f -> {
            // 此处逻辑需对应实际UI选择的Group，这里演示为组0
            Api.replaceSffFrame(path, 0, 0, f.getAbsolutePath());
            Toast.makeText(getContext(), "替换已提交", Toast.LENGTH_SHORT).show();
        }));
        root.addView(replace); openAppWindow("检视:"+name, root, null);
    }

    // ======================================================================================
    // 🎞️ 模块 3：GIF 拆解器
    // ======================================================================================
    private View buildGifExtractorContent() {
        LinearLayout root = new LinearLayout(getContext()); root.setOrientation(LinearLayout.VERTICAL);
        Button btn = createButton("📂 选取 GIF", "#9C27B0");
        root.addView(btn);
        btn.setOnClickListener(v -> showWin10FilePicker("选GIF", 6, null, null, this::promptGifDisassembler));
        return root;
    }

    private void promptGifDisassembler(File gif) {
        final Dialog d = new Dialog(getContext());
        LinearLayout l = new LinearLayout(getContext()); l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundColor(Color.parseColor("#1E1E1E")); l.setPadding(40,40,40,40);
        final ImageView iv = new ImageView(getContext());
        final SeekBar sb = new SeekBar(getContext());
        final EditText et = new EditText(getContext()); et.setText("30"); et.setTextColor(Color.WHITE);
        Button pre = createButton("👁️ 预览滑条", "#0078D7");
        pre.setOnClickListener(v -> {
            try {
                InputStream is = new BufferedInputStream(new FileInputStream(gif));
                Movie m = Movie.decodeStream(is);
                if(m!=null) {
                    sb.setMax(m.duration());
                    sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                        @Override public void onProgressChanged(SeekBar s, int p, boolean b) {
                            Bitmap bmp = Bitmap.createBitmap(m.width(), m.height(), Bitmap.Config.ARGB_8888);
                            Canvas c = new Canvas(bmp); m.setTime(p); m.draw(c,0,0); iv.setImageBitmap(bmp);
                        }
                        @Override public void onStartTrackingTouch(SeekBar s) {} @Override public void onStopTrackingTouch(SeekBar s) {}
                    });
                }
            } catch (Exception e) {}
        });
        Button exp = createButton("🚀 开始拆解", "#9C27B0");
        exp.setOnClickListener(v -> { startGifDisassemblerExec(gif, Integer.parseInt(et.getText().toString())); d.dismiss(); });
        l.addView(et); l.addView(pre); l.addView(iv, (int)(200*density), (int)(200*density)); l.addView(sb); l.addView(exp);
        d.setContentView(l); d.show();
    }

    private void startGifDisassemblerExec(File gif, int count) {
        new Thread(() -> {
            try {
                InputStream is = new BufferedInputStream(new FileInputStream(gif));
                Movie m = Movie.decodeStream(is);
                File out = new File(Environment.getExternalStorageDirectory(), "ik_PNG/"+gif.getName());
                if(!out.exists()) out.mkdirs();
                int step = m.duration() / count;
                for(int i=0; i<count; i++) {
                    Bitmap b = Bitmap.createBitmap(m.width(), m.height(), Bitmap.Config.ARGB_8888);
                    Canvas c = new Canvas(b); m.setTime(i*step); m.draw(c,0,0);
                    FileOutputStream fos = new FileOutputStream(new File(out, "f_"+i+".png"));
                    b.compress(Bitmap.CompressFormat.PNG, 100, fos); fos.close();
                }
                new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(getContext(), "完成: " + out.getPath(), Toast.LENGTH_LONG).show());
            } catch (Exception e) {}
        }).start();
    }

    private View buildSndExtractorContent() {
        LinearLayout root = new LinearLayout(getContext()); root.setOrientation(LinearLayout.VERTICAL);
        Button b = createButton("📂 选择 SND", "#FF9800");
        root.addView(b);
        b.setOnClickListener(v -> showWin10FilePicker("选SND", 5, null, null, f -> {
            try {
                String json = Api.scanSnd(f.getAbsolutePath()); org.json.JSONArray arr = new org.json.JSONArray(json);
                LinearLayout list = new LinearLayout(getContext()); list.setOrientation(LinearLayout.VERTICAL);
                for(int i=0; i<arr.length(); i++) {
                    final int g = arr.getJSONObject(i).getInt("group"); final int it = arr.getJSONObject(i).getInt("item");
                    Button row = createButton("音:"+g+"-"+it, "#1E1E1E");
                    row.setOnClickListener(vx -> {
                        byte[] wav = Api.extractSndAudio(f.getAbsolutePath(), g, it);
                        if(wav!=null) {
                            try {
                                File tmp = new File(getContext().getCacheDir(), "t.wav"); FileOutputStream fos = new FileOutputStream(tmp); fos.write(wav); fos.close();
                                MediaPlayer mp = new MediaPlayer(); mp.setDataSource(tmp.getAbsolutePath()); mp.prepare(); mp.start();
                            } catch (Exception e) {}
                        }
                    });
                    list.addView(row);
                }
                ScrollView sv = new ScrollScrollView(getContext()); sv.addView(list); openAppWindow("检视SND", sv, null);
            } catch (Exception e) {}
        }));
        return root;
    }

    // ======================================================================================
    // 🛠️ 辅助 UI 组件
    // ======================================================================================
    private TextView createTitle(String t) { TextView tv = new TextView(getContext()); tv.setText(t); applyGlobalFontSettings(tv, 1.3f, true); return tv; }
    private Button createButton(String t, String c) {
        Button b = new Button(getContext()); b.setText(t); b.setAllCaps(false); b.setTextColor(Color.WHITE);
        GradientDrawable gd = new GradientDrawable(); gd.setColor(Color.parseColor(c)); gd.setCornerRadius(4*density);
        b.setBackground(gd); return b;
    }

    private void showWin10FilePicker(String title, int type, TextView label, View host, OnFileSelectedListener listener) {
        final Dialog d = new Dialog(getContext());
        LinearLayout root = new LinearLayout(getContext()); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#1E1E1E"));
        final LinearLayout list = new LinearLayout(getContext()); list.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(getContext()); sv.addView(list);
        
        Runnable refresh = new Runnable() {
            @Override public void run() {
                list.removeAllViews();
                File[] files = lastVisitedDir.listFiles();
                if(lastVisitedDir.getParentFile()!=null) {
                    Button up = createButton(".. 上级目录", "#333333"); up.setOnClickListener(v -> { lastVisitedDir = lastVisitedDir.getParentFile(); run(); });
                    list.addView(up);
                }
                if(files!=null) {
                    for(File f : files) {
                        Button b = createButton((f.isDirectory()?"📁 ":"📄 ")+f.getName(), "#1E1E1E");
                        b.setOnClickListener(v -> { if(f.isDirectory()){ lastVisitedDir=f; run(); } else { listener.onFileSelected(f); d.dismiss(); } });
                        list.addView(b);
                    }
                }
            }
        };
        refresh.run(); root.addView(sv); d.setContentView(root); d.show();
    }

    // 内部类，防止滚动冲突
    private class ScrollScrollView extends ScrollView { public ScrollScrollView(Context c){super(c);} }

    public static class GoEngineBridge {
        public static class SffInfo { public String name, filePath, version; public Bitmap preview; }
        public static class SffFrame { public int group, item, width, height, x, y; }
    }
}
