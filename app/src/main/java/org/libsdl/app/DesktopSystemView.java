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
 * 集成：自适应鼠标引擎、网格化桌面系统、持久化设置、拖拽双击交互
 */
public class DesktopSystemView extends Dialog {

    private Context mContext;
    private SharedPreferences prefs;

    // === 鼠标引擎相关变量 ===
    private float mouseX = -1f;
    private float mouseY = -1f;
    private Path cursorPath;
    private Paint cursorPaintFill;
    private Paint cursorPaintStroke;

    // === 桌面系统设置 ===
    private FrameLayout desktopArea;
    private GradientDrawable desktopBgDrawable;
    private View winLogo;
    
    public int bgAlpha = 255;
    public int gridSize = 120;
    public int iconShape = 1; // 0=隐藏底框, 1=圆角矩形, 2=圆形

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

        // 1. 构建底层画板 (处理鼠标绘制与全局触摸分发)
        FrameLayout root = new FrameLayout(getContext()) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                // 画鼠标：只有在有触摸坐标时才画（实现不触屏时隐藏）
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
                    // 手指抬起或取消，鼠标坐标置为 -1，瞬间隐藏
                    mouseX = -1f;
                    mouseY = -1f;
                } else {
                    // 手指按下或滑动，鼠标紧紧跟随手指
                    mouseX = event.getX();
                    mouseY = event.getY();
                }
                invalidate(); // 强制刷新画面
                return super.dispatchTouchEvent(event);
            }
        };

        // 2. Windows 11 风格背景与水印
        desktopBgDrawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#003366"), Color.parseColor("#005A9E"), Color.parseColor("#0078D7")}
        );
        desktopBgDrawable.setAlpha(bgAlpha);
        root.setBackground(desktopBgDrawable);

        winLogo = new View(getContext()) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override
            protected void onDraw(Canvas canvas) {
                p.setColor(Color.WHITE);
                p.setAlpha((int)(25 * (bgAlpha/255f))); 
                float cx = getWidth() / 2f; float cy = getHeight() / 2f - 50;
                float size = Math.min(getWidth(), getHeight()) * 0.35f; 
                float gap = size * 0.04f; float rectSize = (size - gap) / 2f; float corner = rectSize * 0.08f; 
                canvas.drawRoundRect(cx - rectSize - gap/2, cy - rectSize - gap/2, cx - gap/2, cy - gap/2, corner, corner, p);
                canvas.drawRoundRect(cx + gap/2, cy - rectSize - gap/2, cx + rectSize + gap/2, cy - gap/2, corner, corner, p);
                canvas.drawRoundRect(cx - rectSize - gap/2, cy + gap/2, cx - gap/2, cy + rectSize + gap/2, corner, corner, p);
                canvas.drawRoundRect(cx + gap/2, cy + gap/2, cx + rectSize + gap/2, cy + rectSize + gap/2, corner, corner, p);
            }
        };
        root.addView(winLogo, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 3. 桌面图标承载区 (独立一层，防止被任务栏挡住)
        desktopArea = new FrameLayout(getContext());
        root.addView(desktopArea, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 4. 底部任务栏
        LinearLayout taskbar = new LinearLayout(getContext());
        taskbar.setOrientation(LinearLayout.HORIZONTAL);
        taskbar.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        taskbar.setPadding(40, 0, 40, 0);
        taskbar.setBackgroundColor(Color.parseColor("#E61E1E1E"));
        FrameLayout.LayoutParams taskbarParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120);
        taskbarParams.gravity = Gravity.BOTTOM;
        root.addView(taskbar, taskbarParams);

        // 5. 任务栏 Start 按钮
        final LinearLayout startBtn = new LinearLayout(getContext());
        startBtn.setOrientation(LinearLayout.HORIZONTAL);
        startBtn.setGravity(Gravity.CENTER);
        startBtn.setPadding(50, 15, 50, 15);
        final GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.parseColor("#33FFFFFF")); 
        btnBg.setCornerRadius(12f);
        startBtn.setBackground(btnBg);
        
        TextView btnIcon = new TextView(getContext());
        btnIcon.setText("⊞"); btnIcon.setTextColor(Color.parseColor("#00A4EF")); btnIcon.setTextSize(22f);
        TextView btnText = new TextView(getContext());
        btnText.setText(" 恢复游戏"); btnText.setTextColor(Color.WHITE); btnText.setTextSize(18f); btnText.setTypeface(null, Typeface.BOLD);
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

        setContentView(root);

        // 6. 初始化桌面图标
        setupDesktopIcons();
    }

    /**
     * 鼠标绘制引擎初始化
     */
    private void initMouseEngine() {
        float density = mContext.getResources().getDisplayMetrics().density;

        cursorPaintFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        cursorPaintFill.setColor(Color.WHITE);
        cursorPaintFill.setStyle(Paint.Style.FILL);

        cursorPaintStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        cursorPaintStroke.setColor(Color.BLACK);
        cursorPaintStroke.setStyle(Paint.Style.STROKE);
        cursorPaintStroke.setStrokeWidth(1.5f * density); 

        // PC 鼠标形状
        cursorPath = new Path();
        cursorPath.moveTo(0, 0);          
        cursorPath.lineTo(0, 40);         
        cursorPath.lineTo(10, 30);        
        cursorPath.lineTo(18, 48);        
        cursorPath.lineTo(25, 44);        
        cursorPath.lineTo(17, 26);        
        cursorPath.lineTo(30, 26);        
        cursorPath.close();               

        // 鼠标缩小 50%，更加符合 PC 观感
        Matrix scaleMatrix = new Matrix();
        scaleMatrix.setScale(density * 0.35f, density * 0.35f);
        cursorPath.transform(scaleMatrix);
    }

    /**
     * 加载并创建桌面图标
     */
    private void setupDesktopIcons() {
        desktopArea.removeAllViews();

        // 创建唯一的系统应用：系统设置
        createDesktopIcon("system_settings", "⚙️", "系统设置");
    }

    /**
     * 创建单个桌面图标及拖拽/双击引擎
     */
    private void createDesktopIcon(final String id, String iconStr, String name) {
        final LinearLayout iconLayout = new LinearLayout(getContext());
        iconLayout.setOrientation(LinearLayout.VERTICAL);
        iconLayout.setGravity(Gravity.CENTER);
        iconLayout.setPadding(10, 10, 10, 10);
        
        // 动态读取坐标，如果没有则按网格排布 (默认放到左上角)
        float savedX = prefs.getFloat("icon_x_" + id, gridSize * 0.5f);
        float savedY = prefs.getFloat("icon_y_" + id, gridSize * 0.5f);

        // 绘制图标的形状和底色
        TextView iconView = new TextView(getContext());
        iconView.setText(iconStr);
        iconView.setTextSize(32f);
        iconView.setGravity(Gravity.CENTER);
        
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#44000000")); // 半透明黑底
        if (iconShape == 1) bg.setCornerRadius(20f); // 圆角
        else if (iconShape == 2) bg.setCornerRadius(100f); // 圆形
        else bg.setColor(Color.TRANSPARENT); // 隐藏底框
        
        iconView.setBackground(bg);
        int iconSize = (int)(gridSize * 0.7f); // 图标大小占网格的 70%
        iconLayout.addView(iconView, new LinearLayout.LayoutParams(iconSize, iconSize));

        // 绘制文字
        TextView nameView = new TextView(getContext());
        nameView.setText(name);
        nameView.setTextColor(Color.WHITE);
        nameView.setTextSize(12f);
        nameView.setGravity(Gravity.CENTER);
        nameView.setShadowLayer(3f, 1f, 1f, Color.BLACK); // PC 图标经典阴影
        nameView.setSingleLine(true);
        iconLayout.addView(nameView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 放置到桌面
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        iconLayout.setLayoutParams(params);
        iconLayout.setX(savedX);
        iconLayout.setY(savedY);
        desktopArea.addView(iconLayout);

        // --- 图标的【拖拽】与【双击】集成引擎 ---
        iconLayout.setOnTouchListener(new View.OnTouchListener() {
            private float dX, dY;
            private boolean isDragging = false;
            private long lastClickTime = 0;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dX = view.getX() - event.getRawX();
                        dY = view.getY() - event.getRawY();
                        isDragging = false;
                        view.setBackgroundColor(Color.parseColor("#44FFFFFF")); // 点击高亮
                        break;
                        
                    case MotionEvent.ACTION_MOVE:
                        float newX = event.getRawX() + dX;
                        float newY = event.getRawY() + dY;
                        // 如果移动距离超过 10 像素，判定为拖拽
                        if (Math.abs(newX - view.getX()) > 10 || Math.abs(newY - view.getY()) > 10) {
                            isDragging = true;
                        }
                        view.setX(newX);
                        view.setY(newY);
                        break;
                        
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.setBackgroundColor(Color.TRANSPARENT); // 恢复透明
                        
                        if (isDragging) {
                            // 拖拽结束：执行【网格吸附算法】
                            float finalX = Math.round(view.getX() / gridSize) * gridSize;
                            float finalY = Math.round(view.getY() / gridSize) * gridSize;
                            view.setX(finalX);
                            view.setY(finalY);
                            // 保存坐标
                            prefs.edit().putFloat("icon_x_" + id, finalX).putFloat("icon_y_" + id, finalY).apply();
                        } else {
                            // 未拖拽：执行【双击判定】
                            long clickTime = System.currentTimeMillis();
                            if (clickTime - lastClickTime < 300) { // 300ms 内连点两次
                                handleIconDoubleTap(id);
                                lastClickTime = 0; // 重置
                            } else {
                                lastClickTime = clickTime;
                            }
                        }
                        break;
                }
                return true; // 拦截事件
            }
        });
    }

    /**
     * 处理双击逻辑
     */
    private void handleIconDoubleTap(String id) {
        if (id.equals("system_settings")) {
            showDesktopSettingsWindow();
        }
    }

    // ==========================================
    // 桌面设置系统 (控制台窗口)
    // ==========================================
    private void loadDesktopSettings() {
        bgAlpha = prefs.getInt("dt_bgAlpha", 255);
        gridSize = prefs.getInt("dt_gridSize", 160);
        iconShape = prefs.getInt("dt_iconShape", 1);
    }

    private void saveDesktopSettings() {
        prefs.edit()
            .putInt("dt_bgAlpha", bgAlpha)
            .putInt("dt_gridSize", gridSize)
            .putInt("dt_iconShape", iconShape)
            .apply();
    }

    private void showDesktopSettingsWindow() {
        final Dialog settingsDialog = new Dialog(getContext(), android.R.style.Theme_DeviceDefault_Dialog);
        settingsDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#E6222222")); // 半透明黑窗口
        root.setPadding(40, 40, 40, 40);

        TextView title = new TextView(getContext());
        title.setText("⚙️ 系统个性化设置");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22f);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, 30);
        root.addView(title);

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);

        // 1. 透明度滑块
        layout.addView(createSettingTitle("背景壁纸不透明度 (0-255):"));
        SeekBar alphaBar = new SeekBar(getContext());
        alphaBar.setMax(255); alphaBar.setProgress(bgAlpha);
        alphaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean b) { 
                bgAlpha = p; 
                desktopBgDrawable.setAlpha(bgAlpha); 
                winLogo.invalidate(); 
            }
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
        });
        layout.addView(alphaBar);

        // 2. 网格与间距滑块
        layout.addView(createSettingTitle("桌面网格大小与图标间距:"));
        SeekBar gridBar = new SeekBar(getContext());
        gridBar.setMax(300); gridBar.setProgress(gridSize);
        gridBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean b) { 
                gridSize = Math.max(80, p); // 最小 80，防止挤在一起
            }
            public void onStartTrackingTouch(SeekBar s){} 
            public void onStopTrackingTouch(SeekBar s){
                setupDesktopIcons(); // 重新按新尺寸渲染图标
            }
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

        // 4. 恢复默认布局
        layout.addView(createSettingTitle(""));
        Button resetBtn = new Button(getContext());
        resetBtn.setText("🔄 恢复桌面默认布局");
        resetBtn.setTextColor(Color.WHITE);
        resetBtn.setBackgroundColor(Color.parseColor("#D32F2F"));
        resetBtn.setOnClickListener(v -> {
            prefs.edit().remove("icon_x_system_settings").remove("icon_y_system_settings").apply();
            gridSize = 160; iconShape = 1; bgAlpha = 255;
            desktopBgDrawable.setAlpha(255);
            setupDesktopIcons();
            settingsDialog.dismiss();
            Toast.makeText(getContext(), "已恢复默认布局", Toast.LENGTH_SHORT).show();
        });
        layout.addView(resetBtn);

        // 5. 保存并关闭
        Button saveBtn = new Button(getContext());
        saveBtn.setText("💾 保存设置并关闭");
        saveBtn.setTextColor(Color.WHITE);
        saveBtn.setBackgroundColor(Color.parseColor("#1976D2"));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(0, 40, 0, 0);
        saveBtn.setLayoutParams(btnParams);
        saveBtn.setOnClickListener(v -> {
            saveDesktopSettings();
            settingsDialog.dismiss();
        });
        layout.addView(saveBtn);

        scroll.addView(layout);
        root.addView(scroll);
        settingsDialog.setContentView(root);
        settingsDialog.show();
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
