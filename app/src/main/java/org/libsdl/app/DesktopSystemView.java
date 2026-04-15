package org.libsdl.app;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import android.widget.ImageView;
import android.net.Uri;

import java.io.File;
import java.util.Arrays;

/**
 * Ikemen GO 真·PC桌面系统引擎 (多窗口 / 媒体壁纸 / 透明映射)
 */
public class DesktopSystemView extends Dialog {

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
    public int bgAlpha = 180; // 默认半透明，露出游戏画面
    public int gridSizeBase = 100;
    public boolean showGrid = false;
    public int iconShape = 1; 
    public String customDesktopBg = "";
    public String customWindowBg = "";

    public DesktopSystemView(Context context) {
        // 【关键修复1】使用 Translucent 主题，彻底解决黑屏，透出底层游戏画面！
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadDesktopSettings();
        initMouseEngine();

        rootLayer = new FrameLayout(getContext()) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                // 【新增】可视化网格线渲染
                if (showGrid) {
                    Paint gridPaint = new Paint(); gridPaint.setColor(Color.argb(40, 255, 255, 255)); gridPaint.setStrokeWidth(1);
                    float actualGrid = gridSizeBase * density;
                    for (float x = 0; x < getWidth(); x += actualGrid) canvas.drawLine(x, 0, x, getHeight(), gridPaint);
                    for (float y = 0; y < getHeight(); y += actualGrid) canvas.drawLine(0, y, getWidth(), y, gridPaint);
                }
                // 鼠标永远最顶层
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
        // 【关键修复2】让根布局可点击，强制拦截滑动事件，解决空白处鼠标不跟手问题
        rootLayer.setClickable(true);

        // 1. 桌面壁纸层 (支持媒体渲染)
        desktopBgLayer = new FrameLayout(getContext());
        rootLayer.addView(desktopBgLayer, new FrameLayout.LayoutParams(-1, -1));
        refreshDesktopBackground();

        // 2. 层级划分
        desktopIconsLayer = new FrameLayout(getContext());
        windowsLayer = new FrameLayout(getContext());
        rootLayer.addView(desktopIconsLayer, new FrameLayout.LayoutParams(-1, -1));
        rootLayer.addView(windowsLayer, new FrameLayout.LayoutParams(-1, -1));

        // 3. 底部任务栏
        LinearLayout taskbar = new LinearLayout(getContext());
        taskbar.setOrientation(LinearLayout.HORIZONTAL);
        taskbar.setGravity(Gravity.CENTER_VERTICAL);
        taskbar.setPadding((int)(15*density), 0, (int)(15*density), 0);
        taskbar.setBackgroundColor(Color.parseColor("#D9111111")); // Win11 毛玻璃黑
        FrameLayout.LayoutParams taskbarParams = new FrameLayout.LayoutParams(-1, (int)(55*density));
        taskbarParams.gravity = Gravity.BOTTOM;
        rootLayer.addView(taskbar, taskbarParams);

        // 4. "进入游戏" 按钮
        final LinearLayout startBtn = new LinearLayout(getContext());
        startBtn.setOrientation(LinearLayout.HORIZONTAL); startBtn.setGravity(Gravity.CENTER);
        startBtn.setPadding((int)(15*density), (int)(8*density), (int)(15*density), (int)(8*density));
        final GradientDrawable btnBg = new GradientDrawable(); btnBg.setColor(Color.parseColor("#22FFFFFF")); btnBg.setCornerRadius(8f*density);
        startBtn.setBackground(btnBg);
        
        TextView btnIcon = new TextView(getContext()); btnIcon.setText("⊞"); btnIcon.setTextColor(Color.parseColor("#00A4EF")); btnIcon.setTextSize(20f);
        TextView btnText = new TextView(getContext()); btnText.setText(" 进入游戏"); btnText.setTextColor(Color.WHITE); btnText.setTextSize(16f); btnText.setTypeface(null, Typeface.BOLD);
        startBtn.addView(btnIcon); startBtn.addView(btnText);
        
        startBtn.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) { v.setScaleX(0.92f); v.setScaleY(0.92f); btnBg.setColor(Color.parseColor("#55FFFFFF")); }
            else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.setScaleX(1.0f); v.setScaleY(1.0f); btnBg.setColor(Color.parseColor("#22FFFFFF")); 
                if (event.getAction() == MotionEvent.ACTION_UP) { hide(); if (SDLActivity.mSingleton != null) SDLActivity.mSingleton.toggleDesktopMode(false); }
            }
            return true;
        });
        taskbar.addView(startBtn);

        // 5. 任务栏多窗口图标承载区
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
    // 媒体解析引擎 (支持 图片/GIF/视频 自动识别)
    // ==========================================
    private View createMediaBackground(String path, int alpha) {
        if (path == null || path.trim().isEmpty()) return null;
        File file = new File(path); if (!file.exists()) return null;
        String p = path.toLowerCase();
        View mediaView = null;
        
        if (p.endsWith(".mp4") || p.endsWith(".avi") || p.endsWith(".mkv") || p.endsWith(".webm")) {
            VideoView vv = new VideoView(mContext); vv.setVideoPath(path);
            vv.setOnPreparedListener(mp -> { mp.setLooping(true); mp.setVolume(0f, 0f); mp.start(); });
            mediaView = vv;
        } else if (p.endsWith(".gif")) {
            WebView wv = new WebView(mContext);
            wv.loadDataWithBaseURL("", "<html style='margin:0;padding:0;'><body style='margin:0;padding:0;background-color:transparent;'><img src='file://" + path + "' style='width:100%;height:100%;object-fit:cover;' /></body></html>", "text/html", "utf-8", null);
            wv.setBackgroundColor(Color.TRANSPARENT);
            mediaView = wv;
        } else {
            ImageView iv = new ImageView(mContext); iv.setImageURI(Uri.parse("file://" + path)); iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            mediaView = iv;
        }
        mediaView.setAlpha(alpha / 255f);
        return mediaView;
    }

    private void refreshDesktopBackground() {
        desktopBgLayer.removeAllViews();
        View media = createMediaBackground(customDesktopBg, bgAlpha);
        if (media != null) {
            desktopBgLayer.addView(media, new FrameLayout.LayoutParams(-1, -1));
        } else {
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.parseColor("#003366"), Color.parseColor("#005A9E")});
            bg.setAlpha(bgAlpha); desktopBgLayer.setBackground(bg);
        }
    }

    // ==========================================
    // 桌面图标与网格系统
    // ==========================================
    private void setupDesktopIcons() {
        desktopIconsLayer.removeAllViews();
        createDesktopIcon("sys_settings", "⚙️", "系统设置");
    }

    private void createDesktopIcon(final String id, String iconStr, String name) {
        final LinearLayout iconLayout = new LinearLayout(getContext());
        iconLayout.setOrientation(LinearLayout.VERTICAL); iconLayout.setGravity(Gravity.CENTER);
        
        float actualGrid = gridSizeBase * density;
        float savedX = prefs.getFloat("icon_x_" + id, actualGrid * 0.2f);
        float savedY = prefs.getFloat("icon_y_" + id, actualGrid * 0.2f);

        TextView iconView = new TextView(getContext()); iconView.setText(iconStr); iconView.setTextSize(26f); iconView.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#44000000")); 
        if (iconShape == 1) bg.setCornerRadius(10f*density); else if (iconShape == 2) bg.setCornerRadius(50f*density); else bg.setColor(Color.TRANSPARENT); 
        iconView.setBackground(bg);
        
        iconLayout.addView(iconView, new LinearLayout.LayoutParams((int)(actualGrid*0.6f), (int)(actualGrid*0.6f)));
        TextView nameView = new TextView(getContext()); nameView.setText(name); nameView.setTextColor(Color.WHITE); nameView.setTextSize(11f); nameView.setShadowLayer(3f, 1f, 1f, Color.BLACK); nameView.setSingleLine(true);
        iconLayout.addView(nameView, new LinearLayout.LayoutParams(-2, -2));

        iconLayout.setLayoutParams(new FrameLayout.LayoutParams(-2, -2));
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
                        float finalX = Math.round(view.getX() / actualGrid) * actualGrid;
                        float finalY = Math.round(view.getY() / actualGrid) * actualGrid;
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
        if (id.equals("sys_settings")) openAppWindow("系统设置 (System Settings)", buildSettingsContent());
    }

    // ==========================================
    // 真·PC窗口管理系统 (多开/最小化/任务栏)
    // ==========================================
    private void openAppWindow(String windowTitle, View contentView) {
        // 防止重复打开，若已打开则直接前置
        View existingWin = windowsLayer.findViewWithTag(windowTitle);
        if (existingWin != null) {
            existingWin.setVisibility(View.VISIBLE); existingWin.bringToFront(); return;
        }

        // 1. 窗口主框架
        final FrameLayout windowFrame = new FrameLayout(getContext());
        windowFrame.setTag(windowTitle); windowFrame.setClickable(true); 
        
        // 渲染自定义窗口背景
        View winMediaBg = createMediaBackground(customWindowBg, 255);
        if (winMediaBg != null) {
            windowFrame.addView(winMediaBg, new FrameLayout.LayoutParams(-1, -1));
        } else {
            GradientDrawable winBg = new GradientDrawable(); winBg.setColor(Color.parseColor("#E61A1A1A")); winBg.setCornerRadius(10f*density); winBg.setStroke(1, Color.parseColor("#444444"));
            windowFrame.setBackground(winBg);
        }
        windowFrame.setElevation(20f * density); // 真实的物理悬浮阴影

        LinearLayout winContainer = new LinearLayout(getContext());
        winContainer.setOrientation(LinearLayout.VERTICAL);
        windowFrame.addView(winContainer, new FrameLayout.LayoutParams(-1, -1));

        // 2. 标题栏设计
        final LinearLayout titleBar = new LinearLayout(getContext());
        titleBar.setOrientation(LinearLayout.HORIZONTAL); titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setBackgroundColor(Color.parseColor("#99000000")); // 半透明标题栏
        
        TextView title = new TextView(getContext()); title.setText("  " + windowTitle); title.setTextColor(Color.WHITE); title.setTextSize(14f);
        titleBar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        // 控制按钮 [ _ ] [ X ]
        LinearLayout controls = new LinearLayout(getContext()); controls.setOrientation(LinearLayout.HORIZONTAL);
        
        // 最小化按钮
        TextView btnMin = new TextView(getContext()); btnMin.setText(" _ "); btnMin.setTextColor(Color.WHITE); btnMin.setTextSize(16f); btnMin.setPadding((int)(15*density), (int)(5*density), (int)(15*density), (int)(10*density));
        btnMin.setOnClickListener(v -> windowFrame.setVisibility(View.GONE));
        controls.addView(btnMin);

        // 关闭按钮
        TextView btnClose = new TextView(getContext()); btnClose.setText(" ✕ "); btnClose.setTextColor(Color.WHITE); btnClose.setTextSize(16f); btnClose.setPadding((int)(15*density), (int)(5*density), (int)(15*density), (int)(5*density));
        controls.addView(btnClose);
        titleBar.addView(controls);

        // 拖拽窗口
        titleBar.setOnTouchListener(new View.OnTouchListener() {
            float dX, dY;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) { dX = windowFrame.getX() - mouseX; dY = windowFrame.getY() - mouseY; windowFrame.bringToFront(); } 
                else if (event.getAction() == MotionEvent.ACTION_MOVE) { windowFrame.setX(mouseX + dX); windowFrame.setY(mouseY + dY); }
                return true;
            }
        });

        winContainer.addView(titleBar, new LinearLayout.LayoutParams(-1, (int)(40*density)));
        View sep = new View(getContext()); sep.setBackgroundColor(Color.parseColor("#0078D7"));
        winContainer.addView(sep, new LinearLayout.LayoutParams(-1, (int)(2*density)));
        winContainer.addView(contentView, new LinearLayout.LayoutParams(-1, -1));

        // 3. 动态添加到任务栏
        final LinearLayout taskBtn = new LinearLayout(getContext());
        taskBtn.setOrientation(LinearLayout.HORIZONTAL); taskBtn.setGravity(Gravity.CENTER);
        taskBtn.setPadding((int)(10*density), (int)(5*density), (int)(10*density), (int)(5*density));
        GradientDrawable tbBg = new GradientDrawable(); tbBg.setColor(Color.parseColor("#33FFFFFF")); tbBg.setCornerRadius(5f*density);
        taskBtn.setBackground(tbBg);
        LinearLayout.LayoutParams tbParams = new LinearLayout.LayoutParams(-2, -1); tbParams.setMargins(0,0,(int)(10*density),0);
        
        TextView tbText = new TextView(getContext()); tbText.setText("🗔 " + windowTitle.split(" ")[0]); tbText.setTextColor(Color.WHITE); tbText.setTextSize(12f);
        taskBtn.addView(tbText);
        
        // 任务栏控制显示/隐藏
        taskBtn.setOnClickListener(v -> {
            if (windowFrame.getVisibility() == View.VISIBLE) windowFrame.setVisibility(View.GONE);
            else { windowFrame.setVisibility(View.VISIBLE); windowFrame.bringToFront(); }
        });
        taskbarAppsLayout.addView(taskBtn, tbParams);

        // 关闭逻辑
        btnClose.setOnClickListener(v -> {
            windowsLayer.removeView(windowFrame);
            taskbarAppsLayout.removeView(taskBtn);
        });

        // 居中弹出
        int w = (int) (rootLayer.getWidth() * 0.65f); int h = (int) (rootLayer.getHeight() * 0.75f);
        FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(w, h); frameParams.gravity = Gravity.CENTER;
        windowsLayer.addView(windowFrame, frameParams);
    }

    // ==========================================
    // 系统设置应用
    // ==========================================
    private void loadDesktopSettings() {
        bgAlpha = prefs.getInt("dt_bgAlpha", 180);
        gridSizeBase = prefs.getInt("dt_gridSize", 100);
        showGrid = prefs.getBoolean("dt_showGrid", false);
        iconShape = prefs.getInt("dt_iconShape", 1);
        customDesktopBg = prefs.getString("dt_customDeskBg", "");
        customWindowBg = prefs.getString("dt_customWinBg", "");
    }

    private View buildSettingsContent() {
        ScrollView scroll = new ScrollView(getContext());
        LinearLayout layout = new LinearLayout(getContext()); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding((int)(20*density), (int)(10*density), (int)(20*density), (int)(20*density));

        // 1. 透明度
        layout.addView(createTitle("桌面背景不透明度 (拉到0完全显示底层游戏):"));
        SeekBar alphaBar = new SeekBar(getContext()); alphaBar.setMax(255); alphaBar.setProgress(bgAlpha);
        alphaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean b) { bgAlpha = p; refreshDesktopBackground(); }
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
        });
        layout.addView(alphaBar);

        // 2. 网格调整与可见性
        layout.addView(createTitle("桌面网格间距 (支持自适应):"));
        SeekBar gridBar = new SeekBar(getContext()); gridBar.setMax(250); gridBar.setProgress(gridSizeBase);
        gridBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean b) { gridSizeBase = Math.max(60, p); rootLayer.invalidate(); }
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){ setupDesktopIcons(); }
        });
        layout.addView(gridBar);

        Button gridToggle = new Button(getContext()); gridToggle.setText(showGrid ? "✔️ 网格辅助线：开启" : "❌ 网格辅助线：关闭");
        gridToggle.setOnClickListener(v -> { showGrid = !showGrid; gridToggle.setText(showGrid ? "✔️ 网格辅助线：开启" : "❌ 网格辅助线：关闭"); rootLayer.invalidate(); });
        layout.addView(gridToggle);

        // 3. 媒体壁纸引擎设置
        layout.addView(createTitle("🖼️ 桌面动态壁纸路径 (.mp4/.gif/.jpg)"));
        EditText deskBgInput = new EditText(getContext()); deskBgInput.setText(customDesktopBg); deskBgInput.setTextColor(Color.WHITE); deskBgInput.setHint("如: /sdcard/bg.mp4");
        layout.addView(deskBgInput);
        Button pickDesk = new Button(getContext()); pickDesk.setText("浏览存储卡查找"); pickDesk.setOnClickListener(v -> showMiniFileBrowser(deskBgInput));
        layout.addView(pickDesk);

        layout.addView(createTitle("🖼️ 窗口动态壁纸路径 (.mp4/.gif/.jpg)"));
        EditText winBgInput = new EditText(getContext()); winBgInput.setText(customWindowBg); winBgInput.setTextColor(Color.WHITE); winBgInput.setHint("如: /sdcard/win.gif");
        layout.addView(winBgInput);
        Button pickWin = new Button(getContext()); pickWin.setText("浏览存储卡查找"); pickWin.setOnClickListener(v -> showMiniFileBrowser(winBgInput));
        layout.addView(pickWin);

        // 4. 保存设置
        Button saveBtn = new Button(getContext()); saveBtn.setText("💾 保存并立刻生效"); saveBtn.setTextColor(Color.WHITE); saveBtn.setBackgroundColor(Color.parseColor("#1976D2"));
        LinearLayout.LayoutParams btnP = new LinearLayout.LayoutParams(-1, -2); btnP.setMargins(0, (int)(20*density), 0, 0); saveBtn.setLayoutParams(btnP);
        saveBtn.setOnClickListener(v -> {
            customDesktopBg = deskBgInput.getText().toString().trim();
            customWindowBg = winBgInput.getText().toString().trim();
            prefs.edit().putInt("dt_bgAlpha", bgAlpha).putInt("dt_gridSize", gridSizeBase).putBoolean("dt_showGrid", showGrid).putString("dt_customDeskBg", customDesktopBg).putString("dt_customWinBg", customWindowBg).apply();
            refreshDesktopBackground();
            Toast.makeText(getContext(), "设置已更新，多媒体引擎已重载", Toast.LENGTH_SHORT).show();
        });
        layout.addView(saveBtn);

        scroll.addView(layout);
        return scroll;
    }

    private TextView createTitle(String text) {
        TextView tv = new TextView(getContext()); tv.setText(text); tv.setTextColor(Color.parseColor("#00A4EF"));
        tv.setTextSize(13f); tv.setPadding(0, (int)(15*density), 0, (int)(5*density)); tv.setTypeface(null, Typeface.BOLD);
        return tv;
    }

    // ==========================================
    // 微型纯 Java 文件浏览器引擎
    // ==========================================
    private void showMiniFileBrowser(final EditText targetInput) {
        final Dialog fd = new Dialog(getContext(), android.R.style.Theme_DeviceDefault_Dialog);
        fd.requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        final LinearLayout root = new LinearLayout(getContext()); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#222222")); root.setPadding(30,30,30,30);
        final TextView pathView = new TextView(getContext()); pathView.setTextColor(Color.YELLOW); pathView.setTextSize(14f); pathView.setPadding(0,0,0,20);
        final ScrollView scroll = new ScrollView(getContext()); final LinearLayout list = new LinearLayout(getContext()); list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list); root.addView(pathView); root.addView(scroll);
        
        final File[] currentDir = {Environment.getExternalStorageDirectory()};
        
        Runnable refreshList = new Runnable() {
            @Override
            public void run() {
                list.removeAllViews(); pathView.setText("当前目录: " + currentDir[0].getAbsolutePath());
                if (currentDir[0].getParentFile() != null) {
                    Button up = new Button(getContext()); up.setText("📁 返回上一级"); up.setTextColor(Color.LTGRAY);
                    up.setOnClickListener(v -> { currentDir[0] = currentDir[0].getParentFile(); this.run(); }); list.addView(up);
                }
                File[] files = currentDir[0].listFiles();
                if (files != null) {
                    Arrays.sort(files, (f1, f2) -> { if (f1.isDirectory() && !f2.isDirectory()) return -1; if (!f1.isDirectory() && f2.isDirectory()) return 1; return f1.getName().compareToIgnoreCase(f2.getName()); });
                    for (File f : files) {
                        Button btn = new Button(getContext()); btn.setAllCaps(false);
                        btn.setText(f.isDirectory() ? "📁 " + f.getName() : "📄 " + f.getName()); btn.setTextColor(Color.WHITE);
                        btn.setOnClickListener(v -> {
                            if (f.isDirectory()) { currentDir[0] = f; this.run(); }
                            else { targetInput.setText(f.getAbsolutePath()); fd.dismiss(); }
                        });
                        list.addView(btn);
                    }
                }
            }
        };
        refreshList.run(); fd.setContentView(root); fd.show();
    }

    @Override
    public void onBackPressed() { } // 屏蔽返回键
}
