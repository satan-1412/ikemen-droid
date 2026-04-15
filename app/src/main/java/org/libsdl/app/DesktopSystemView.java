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
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Ikemen GO 纯 Java 桌面系统 / Windows 11 风格梦工厂模式
 * 集成：自适应鼠标引擎、真·PC窗口管理系统、网格化桌面、极速拖拽引擎
 */
public class DesktopSystemView extends Dialog {

    private Context mContext;
    private SharedPreferences prefs;

    // === 全局鼠标与触摸引擎 ===
    private float mouseX = -1f;
    private float mouseY = -1f;
    private Path cursorPath;
    private Paint cursorPaintFill;
    private Paint cursorPaintStroke;
    
    // === 桌面层级容器 ===
    private FrameLayout rootLayer;         // 根画板
    private FrameLayout desktopIconsLayer; // 图标层
    private FrameLayout windowsLayer;      // 浮动窗口层
    private GradientDrawable desktopBgDrawable;
    private View winLogo;
    
    // === 系统设置参数 ===
    public int bgAlpha = 255;
    public int gridSize = 160;
    public int iconShape = 1; // 0=隐藏, 1=圆角, 2=圆形

    public DesktopSystemView(Context context) {
        super(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.mContext = context;
        this.prefs = context.getSharedPreferences("IkemenDesktopPrefs", Context.MODE_PRIVATE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Window window = getWindow();
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        loadDesktopSettings();
        initMouseEngine();

        // 1. 构建根画板 (全局拦截与最高层鼠标绘制)
        rootLayer = new FrameLayout(getContext()) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                // 鼠标永远画在最顶层
                if (mouseX >= 0 && mouseY >= 0) {
                    canvas.save();
                    canvas.translate(mouseX, mouseY);
                    canvas.drawPath(cursorPath, cursorPaintFill);
                    canvas.drawPath(cursorPath, cursorPaintStroke);
                    canvas.restore();
                }
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    mouseX = -1f; // 抬手瞬间隐藏鼠标
                    mouseY = -1f;
                } else {
                    mouseX = event.getX(); // 精确同步根坐标
                    mouseY = event.getY();
                }
                invalidate(); 
                return super.dispatchTouchEvent(event);
            }
        };

        // 2. 背景与水印
        desktopBgDrawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#003366"), Color.parseColor("#005A9E"), Color.parseColor("#0078D7")}
        );
        desktopBgDrawable.setAlpha(bgAlpha);
        rootLayer.setBackground(desktopBgDrawable);

        winLogo = new View(getContext()) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override
            protected void onDraw(Canvas canvas) {
                p.setColor(Color.WHITE); p.setAlpha((int)(25 * (bgAlpha/255f))); 
                float cx = getWidth() / 2f; float cy = getHeight() / 2f - 50;
                float size = Math.min(getWidth(), getHeight()) * 0.35f; float gap = size * 0.04f; float rectSize = (size - gap) / 2f; float corner = rectSize * 0.08f; 
                canvas.drawRoundRect(cx - rectSize - gap/2, cy - rectSize - gap/2, cx - gap/2, cy - gap/2, corner, corner, p);
                canvas.drawRoundRect(cx + gap/2, cy - rectSize - gap/2, cx + rectSize + gap/2, cy - gap/2, corner, corner, p);
                canvas.drawRoundRect(cx - rectSize - gap/2, cy + gap/2, cx - gap/2, cy + rectSize + gap/2, corner, corner, p);
                canvas.drawRoundRect(cx + gap/2, cy + gap/2, cx + rectSize + gap/2, cy + rectSize + gap/2, corner, corner, p);
            }
        };
        rootLayer.addView(winLogo, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 3. 层级划分：图标层 与 窗口层 (保证窗口永远压在图标上方)
        desktopIconsLayer = new FrameLayout(getContext());
        windowsLayer = new FrameLayout(getContext());
        rootLayer.addView(desktopIconsLayer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rootLayer.addView(windowsLayer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 4. 底部任务栏
        LinearLayout taskbar = new LinearLayout(getContext());
        taskbar.setOrientation(LinearLayout.HORIZONTAL);
        taskbar.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        taskbar.setPadding(40, 0, 40, 0);
        taskbar.setBackgroundColor(Color.parseColor("#E61E1E1E"));
        FrameLayout.LayoutParams taskbarParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120);
        taskbarParams.gravity = Gravity.BOTTOM;
        rootLayer.addView(taskbar, taskbarParams);

        // 5. 开始菜单按钮 (单键极速恢复游戏)
        final LinearLayout startBtn = new LinearLayout(getContext());
        startBtn.setOrientation(LinearLayout.HORIZONTAL);
        startBtn.setGravity(Gravity.CENTER);
        startBtn.setPadding(50, 15, 50, 15);
        final GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.parseColor("#33FFFFFF")); btnBg.setCornerRadius(12f);
        startBtn.setBackground(btnBg);
        
        TextView btnIcon = new TextView(getContext()); btnIcon.setText("⊞"); btnIcon.setTextColor(Color.parseColor("#00A4EF")); btnIcon.setTextSize(22f);
        TextView btnText = new TextView(getContext()); btnText.setText(" 恢复游戏"); btnText.setTextColor(Color.WHITE); btnText.setTextSize(18f); btnText.setTypeface(null, Typeface.BOLD);
        startBtn.addView(btnIcon); startBtn.addView(btnText);
        
        startBtn.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.setScaleX(0.92f); v.setScaleY(0.92f); btnBg.setColor(Color.parseColor("#55FFFFFF")); 
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setScaleX(1.0f); v.setScaleY(1.0f); btnBg.setColor(Color.parseColor("#33FFFFFF")); 
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        DesktopSystemView.this.hide(); 
                        if (SDLActivity.mSingleton != null) SDLActivity.mSingleton.toggleDesktopMode(false);
                    }
                    break;
            }
            return true;
        });
        taskbar.addView(startBtn);

        setContentView(rootLayer);

        // 6. 初始化桌面
        setupDesktopIcons();
    }

    private void initMouseEngine() {
        float density = mContext.getResources().getDisplayMetrics().density;
        cursorPaintFill = new Paint(Paint.ANTI_ALIAS_FLAG); cursorPaintFill.setColor(Color.WHITE); cursorPaintFill.setStyle(Paint.Style.FILL);
        cursorPaintStroke = new Paint(Paint.ANTI_ALIAS_FLAG); cursorPaintStroke.setColor(Color.BLACK); cursorPaintStroke.setStyle(Paint.Style.STROKE); cursorPaintStroke.setStrokeWidth(1.5f * density); 

        cursorPath = new Path();
        cursorPath.moveTo(0, 0); cursorPath.lineTo(0, 40); cursorPath.lineTo(10, 30); cursorPath.lineTo(18, 48);        
        cursorPath.lineTo(25, 44); cursorPath.lineTo(17, 26); cursorPath.lineTo(30, 26); cursorPath.close();               

        Matrix scaleMatrix = new Matrix();
        scaleMatrix.setScale(density * 0.35f, density * 0.35f);
        cursorPath.transform(scaleMatrix);
    }

    // ==========================================
    // 桌面图标系统与拖拽双击引擎
    // ==========================================
    private void setupDesktopIcons() {
        desktopIconsLayer.removeAllViews();
        createDesktopIcon("sys_settings", "⚙️", "系统设置");
        // 未来可以加：createDesktopIcon("mod_tool", "📂", "资源管理器");
    }

    private void createDesktopIcon(final String id, String iconStr, String name) {
        final LinearLayout iconLayout = new LinearLayout(getContext());
        iconLayout.setOrientation(LinearLayout.VERTICAL);
        iconLayout.setGravity(Gravity.CENTER);
        iconLayout.setPadding(10, 10, 10, 10);
        
        float savedX = prefs.getFloat("icon_x_" + id, gridSize * 0.2f);
        float savedY = prefs.getFloat("icon_y_" + id, gridSize * 0.2f);

        TextView iconView = new TextView(getContext());
        iconView.setText(iconStr); iconView.setTextSize(32f); iconView.setGravity(Gravity.CENTER);
        
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#44000000")); 
        if (iconShape == 1) bg.setCornerRadius(20f); else if (iconShape == 2) bg.setCornerRadius(100f); else bg.setColor(Color.TRANSPARENT); 
        iconView.setBackground(bg);
        
        int iconSize = (int)(gridSize * 0.7f);
        iconLayout.addView(iconView, new LinearLayout.LayoutParams(iconSize, iconSize));

        TextView nameView = new TextView(getContext());
        nameView.setText(name); nameView.setTextColor(Color.WHITE); nameView.setTextSize(12f); nameView.setGravity(Gravity.CENTER); nameView.setShadowLayer(3f, 1f, 1f, Color.BLACK);
        iconLayout.addView(nameView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        iconLayout.setLayoutParams(params);
        iconLayout.setX(savedX); iconLayout.setY(savedY);
        desktopIconsLayer.addView(iconLayout);

        // --- 核弹级拖拽与双击引擎 ---
        iconLayout.setOnTouchListener(new View.OnTouchListener() {
            private float offsetX, offsetY;
            private float startX, startY;
            private boolean isDragging = false;
            private long lastClickTime = 0;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                // 全部采用全局 mouseX/Y，绝对不会偏移
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        offsetX = view.getX() - mouseX;
                        offsetY = view.getY() - mouseY;
                        startX = mouseX;
                        startY = mouseY;
                        isDragging = false;
                        view.setBackgroundColor(Color.parseColor("#44FFFFFF")); // 选中高亮
                        break;
                        
                    case MotionEvent.ACTION_MOVE:
                        if (!isDragging && (Math.abs(mouseX - startX) > 10 || Math.abs(mouseY - startY) > 10)) {
                            isDragging = true;
                            view.bringToFront(); // 拖拽时置于图标层最上方
                        }
                        if (isDragging) {
                            view.setX(mouseX + offsetX);
                            view.setY(mouseY + offsetY);
                        }
                        break;
                        
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.setBackgroundColor(Color.TRANSPARENT);
                        
                        if (isDragging) {
                            // 网格自动吸附
                            float finalX = Math.round(view.getX() / gridSize) * gridSize;
                            float finalY = Math.round(view.getY() / gridSize) * gridSize;
                            view.setX(finalX); view.setY(finalY);
                            prefs.edit().putFloat("icon_x_" + id, finalX).putFloat("icon_y_" + id, finalY).apply();
                        } else {
                            // 极速双击判定
                            long clickTime = System.currentTimeMillis();
                            if (clickTime - lastClickTime < 350) { 
                                handleIconDoubleTap(id);
                                lastClickTime = 0; 
                            } else {
                                lastClickTime = clickTime;
                            }
                        }
                        break;
                }
                return true; 
            }
        });
    }

    private void handleIconDoubleTap(String id) {
        if (id.equals("sys_settings")) {
            openAppWindow("系统设置 (System Settings)", buildSettingsContent());
        }
    }

    // ==========================================
    // 真·PC窗口管理系统 (Window Manager)
    // ==========================================
    /**
     * 在桌面动态生成一个可拖拽的 PC 风格独立窗口
     */
    private void openAppWindow(String windowTitle, View contentView) {
        // 防止重复打开同一个窗口
        if (windowsLayer.findViewWithTag(windowTitle) != null) {
            windowsLayer.findViewWithTag(windowTitle).bringToFront();
            return;
        }

        // 1. 窗口主框架 (Win11 黑暗模式质感)
        final LinearLayout windowFrame = new LinearLayout(getContext());
        windowFrame.setTag(windowTitle);
        windowFrame.setOrientation(LinearLayout.VERTICAL);
        windowFrame.setClickable(true); // 拦截触摸，防止点到背后的图标
        
        GradientDrawable winBg = new GradientDrawable();
        winBg.setColor(Color.parseColor("#1C1C1C")); // 深灰底色
        winBg.setCornerRadius(16f);
        winBg.setStroke(2, Color.parseColor("#333333")); // 边框
        windowFrame.setBackground(winBg);
        windowFrame.setElevation(40f); // 产生极其逼真的物理阴影

        // 2. 窗口标题栏
        final LinearLayout titleBar = new LinearLayout(getContext());
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setBackgroundColor(Color.TRANSPARENT);
        titleBar.setPadding(30, 0, 0, 0);
        
        TextView title = new TextView(getContext());
        title.setText(windowTitle);
        title.setTextColor(Color.WHITE);
        title.setTextSize(14f);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleBar.addView(title, titleParams);

        // 3. 标题栏控制按钮 (─ □ ✕)
        LinearLayout controls = new LinearLayout(getContext());
        controls.setOrientation(LinearLayout.HORIZONTAL);
        
        TextView btnClose = new TextView(getContext());
        btnClose.setText("✕"); btnClose.setTextColor(Color.WHITE); btnClose.setTextSize(18f);
        btnClose.setPadding(30, 15, 30, 15);
        btnClose.setOnClickListener(v -> windowsLayer.removeView(windowFrame)); // 关闭窗口逻辑
        // 鼠标悬停变红效果
        btnClose.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) v.setBackgroundColor(Color.parseColor("#E81123"));
            else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) v.setBackgroundColor(Color.TRANSPARENT);
            return false;
        });
        controls.addView(btnClose);
        titleBar.addView(controls);

        // 4. 赋予标题栏拖拽整个窗口的能力
        titleBar.setOnTouchListener(new View.OnTouchListener() {
            float dX, dY;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    dX = windowFrame.getX() - mouseX;
                    dY = windowFrame.getY() - mouseY;
                    windowFrame.bringToFront(); // 点击标题栏，窗口置顶
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    windowFrame.setX(mouseX + dX);
                    windowFrame.setY(mouseY + dY);
                }
                return true;
            }
        });

        // 5. 组合窗口元素
        windowFrame.addView(titleBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 100));
        
        View separator = new View(getContext()); separator.setBackgroundColor(Color.parseColor("#333333"));
        windowFrame.addView(separator, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2));
        
        windowFrame.addView(contentView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 6. 计算自适应大小并将其添加到窗口层
        int w = (int) (rootLayer.getWidth() * 0.65f); // 占屏幕 65% 宽
        int h = (int) (rootLayer.getHeight() * 0.75f); // 占屏幕 75% 高
        FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(w, h);
        frameParams.gravity = Gravity.CENTER; // 默认居中弹出
        
        windowsLayer.addView(windowFrame, frameParams);
        windowFrame.bringToFront();
    }

    // ==========================================
    // 内部应用：系统设置的内容视图构建
    // ==========================================
    private void loadDesktopSettings() {
        bgAlpha = prefs.getInt("dt_bgAlpha", 255);
        gridSize = prefs.getInt("dt_gridSize", 160);
        iconShape = prefs.getInt("dt_iconShape", 1);
    }

    private View buildSettingsContent() {
        ScrollView scroll = new ScrollView(getContext());
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 40);

        // 1. 透明度滑块
        layout.addView(createSettingTitle("背景壁纸不透明度 (0-255):"));
        SeekBar alphaBar = new SeekBar(getContext());
        alphaBar.setMax(255); alphaBar.setProgress(bgAlpha);
        alphaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean b) { 
                bgAlpha = p; desktopBgDrawable.setAlpha(bgAlpha); winLogo.invalidate(); 
            }
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
        });
        layout.addView(alphaBar);

        // 2. 网格滑块
        layout.addView(createSettingTitle("桌面网格大小与图标间距:"));
        SeekBar gridBar = new SeekBar(getContext());
        gridBar.setMax(300); gridBar.setProgress(gridSize);
        gridBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean b) { gridSize = Math.max(80, p); }
            public void onStartTrackingTouch(SeekBar s){} 
            public void onStopTrackingTouch(SeekBar s){ setupDesktopIcons(); }
        });
        layout.addView(gridBar);

        // 3. 形状切换
        layout.addView(createSettingTitle("图标底框形状:"));
        Button shapeBtn = new Button(getContext());
        shapeBtn.setText(iconShape == 0 ? "当前：透明隐藏" : (iconShape == 1 ? "当前：圆角矩形" : "当前：纯圆形"));
        shapeBtn.setOnClickListener(v -> {
            iconShape = (iconShape + 1) % 3;
            shapeBtn.setText(iconShape == 0 ? "当前：透明隐藏" : (iconShape == 1 ? "当前：圆角矩形" : "当前：纯圆形"));
            setupDesktopIcons();
        });
        layout.addView(shapeBtn);

        // 4. 恢复默认与保存
        layout.addView(createSettingTitle(""));
        Button resetBtn = new Button(getContext());
        resetBtn.setText("🔄 恢复桌面默认布局"); resetBtn.setTextColor(Color.WHITE); resetBtn.setBackgroundColor(Color.parseColor("#D32F2F"));
        resetBtn.setOnClickListener(v -> {
            prefs.edit().remove("icon_x_sys_settings").remove("icon_y_sys_settings").apply();
            gridSize = 160; iconShape = 1; bgAlpha = 255; desktopBgDrawable.setAlpha(255);
            setupDesktopIcons();
            Toast.makeText(getContext(), "布局已恢复", Toast.LENGTH_SHORT).show();
        });
        layout.addView(resetBtn);

        Button saveBtn = new Button(getContext());
        saveBtn.setText("💾 保存设置"); saveBtn.setTextColor(Color.WHITE); saveBtn.setBackgroundColor(Color.parseColor("#1976D2"));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(0, 40, 0, 0); saveBtn.setLayoutParams(btnParams);
        saveBtn.setOnClickListener(v -> {
            prefs.edit().putInt("dt_bgAlpha", bgAlpha).putInt("dt_gridSize", gridSize).putInt("dt_iconShape", iconShape).apply();
            windowsLayer.removeView(windowsLayer.findViewWithTag("系统设置 (System Settings)")); // 点击保存后自动关闭本窗口
        });
        layout.addView(saveBtn);

        scroll.addView(layout);
        return scroll;
    }

    private TextView createSettingTitle(String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text); tv.setTextColor(Color.parseColor("#AAAAAA"));
        tv.setTextSize(14f); tv.setPadding(0, 30, 0, 10);
        return tv;
    }

    @Override
    public void onBackPressed() {
        // 屏蔽物理返回键
    }
}
