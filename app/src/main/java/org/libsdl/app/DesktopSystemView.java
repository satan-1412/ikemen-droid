package org.libsdl.app;

import android.app.Dialog;
import android.content.Context;
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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Ikemen GO 纯 Java 桌面系统 / Windows 11 风格梦工厂模式
 * 集成全局鼠标指针引擎与双击交互机制 (自适应高分屏)
 */
public class DesktopSystemView extends Dialog {

    private Context mContext;
    
    // === 鼠标引擎相关变量 ===
    private float mouseX = -1f;
    private float mouseY = -1f;
    private Path cursorPath;
    private Paint cursorPaintFill;
    private Paint cursorPaintStroke;
    
    // === 手势与双击识别器 ===
    private GestureDetector gestureDetector;

    public DesktopSystemView(Context context) {
        super(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.mContext = context;
    }

    // 【优化2】将沉浸式全屏移至 onStart，确保每次显示（或从后台切回）时都绝对隐藏导航栏
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

        // 初始化鼠标绘制引擎和双击探测器
        initMouseEngine();

        // 构建纯 Java 布局根节点
        FrameLayout root = new FrameLayout(getContext()) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas); // 先画系统里的所有 UI
                
                // 最后画鼠标，确保鼠标永远在最顶层！
                if (mouseX < 0 && getWidth() > 0) {
                    mouseX = getWidth() / 2f;
                    mouseY = getHeight() / 2f;
                }
                
                if (mouseX >= 0 && mouseY >= 0) {
                    canvas.save();
                    canvas.translate(mouseX, mouseY);
                    canvas.drawPath(cursorPath, cursorPaintFill);   // 画白底
                    canvas.drawPath(cursorPath, cursorPaintStroke); // 画黑边
                    canvas.restore();
                }
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                // 1. 瞬间同步鼠标位置
                mouseX = event.getX();
                mouseY = event.getY();
                invalidate(); // 强制重绘，刷新鼠标位置

                // 2. 将触摸事件喂给手势探测器
                gestureDetector.onTouchEvent(event);

                // 3. 放行事件，保证按钮可点击
                return super.dispatchTouchEvent(event);
            }
        };
        
        // Windows 11 风格深蓝背景渐变
        GradientDrawable winBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#003366"), Color.parseColor("#005A9E"), Color.parseColor("#0078D7")}
        );
        root.setBackground(winBg);

        // 纯代码手绘巨型 Windows 11 徽标 (背景水印)
        View winLogo = new View(getContext()) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override
            protected void onDraw(Canvas canvas) {
                p.setColor(Color.WHITE);
                p.setAlpha(25); 
                float cx = getWidth() / 2f;
                float cy = getHeight() / 2f - 50;
                float size = Math.min(getWidth(), getHeight()) * 0.35f; 
                float gap = size * 0.04f;
                float rectSize = (size - gap) / 2f;
                float corner = rectSize * 0.08f; 
                
                canvas.drawRoundRect(cx - rectSize - gap/2, cy - rectSize - gap/2, cx - gap/2, cy - gap/2, corner, corner, p);
                canvas.drawRoundRect(cx + gap/2, cy - rectSize - gap/2, cx + rectSize + gap/2, cy - gap/2, corner, corner, p);
                canvas.drawRoundRect(cx - rectSize - gap/2, cy + gap/2, cx - gap/2, cy + rectSize + gap/2, corner, corner, p);
                canvas.drawRoundRect(cx + gap/2, cy + gap/2, cx + rectSize + gap/2, cy + rectSize + gap/2, corner, corner, p);
            }
        };
        root.addView(winLogo, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 屏幕中心的运行状态窗口
        LinearLayout centerWindow = new LinearLayout(getContext());
        centerWindow.setOrientation(LinearLayout.VERTICAL);
        centerWindow.setGravity(Gravity.CENTER);
        
        TextView titleText = new TextView(getContext());
        titleText.setText("系统已挂起 (System Suspended)");
        titleText.setTextColor(Color.WHITE);
        titleText.setTextSize(28f);
        titleText.setTypeface(null, Typeface.BOLD);
        titleText.setShadowLayer(4f, 0, 4f, Color.BLACK);
        
        TextView subText = new TextView(getContext());
        subText.setText("桌面交互引擎已启动，支持虚拟鼠标指针。\n任务栏单击生效，桌面工具需双击打开。");
        subText.setTextColor(Color.parseColor("#DDDDDD"));
        subText.setTextSize(16f);
        subText.setGravity(Gravity.CENTER);
        subText.setPadding(0, 20, 0, 0);
        
        centerWindow.addView(titleText);
        centerWindow.addView(subText);
        
        FrameLayout.LayoutParams centerParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        centerParams.gravity = Gravity.CENTER;
        root.addView(centerWindow, centerParams);

        // 底部毛玻璃任务栏
        LinearLayout taskbar = new LinearLayout(getContext());
        taskbar.setOrientation(LinearLayout.HORIZONTAL);
        taskbar.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        taskbar.setPadding(40, 0, 40, 0);
        taskbar.setBackgroundColor(Color.parseColor("#E61E1E1E"));

        FrameLayout.LayoutParams taskbarParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120);
        taskbarParams.gravity = Gravity.BOTTOM;
        root.addView(taskbar, taskbarParams);

        // 开始菜单按钮 (用于恢复游戏，单键触发)
        final LinearLayout startBtn = new LinearLayout(getContext());
        startBtn.setOrientation(LinearLayout.HORIZONTAL);
        startBtn.setGravity(Gravity.CENTER);
        startBtn.setPadding(50, 15, 50, 15);
        final GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.parseColor("#33FFFFFF")); 
        btnBg.setCornerRadius(12f);
        startBtn.setBackground(btnBg);
        
        TextView btnIcon = new TextView(getContext());
        btnIcon.setText("⊞"); 
        btnIcon.setTextColor(Color.parseColor("#00A4EF")); 
        btnIcon.setTextSize(22f);
        
        TextView btnText = new TextView(getContext());
        btnText.setText(" 恢复游戏");
        btnText.setTextColor(Color.WHITE);
        btnText.setTextSize(18f);
        btnText.setTypeface(null, Typeface.BOLD);
        
        startBtn.addView(btnIcon);
        startBtn.addView(btnText);
        
        // 点击极速反馈
        startBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setScaleX(0.92f); v.setScaleY(0.92f); 
                        btnBg.setColor(Color.parseColor("#55FFFFFF")); 
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setScaleX(1.0f); v.setScaleY(1.0f);
                        btnBg.setColor(Color.parseColor("#33FFFFFF")); 
                        
                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            DesktopSystemView.this.hide(); 
                            if (SDLActivity.mSingleton != null) {
                                SDLActivity.mSingleton.toggleDesktopMode(false);
                            }
                        }
                        break;
                }
                return true; 
            }
        });
        taskbar.addView(startBtn);

        setContentView(root);
    }
    
    /**
     * 初始化虚拟鼠标绘制参数与双击探测器
     */
    private void initMouseEngine() {
        // 【优化1】获取当前手机的屏幕像素密度，用于完美自适应缩放
        float density = mContext.getResources().getDisplayMetrics().density;

        cursorPaintFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        cursorPaintFill.setColor(Color.WHITE);
        cursorPaintFill.setStyle(Paint.Style.FILL);

        cursorPaintStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        cursorPaintStroke.setColor(Color.BLACK);
        cursorPaintStroke.setStyle(Paint.Style.STROKE);
        cursorPaintStroke.setStrokeWidth(2.0f * density); // 描边跟随分辨率缩放

        // 纯代码手绘 PC 鼠标箭头 Path
        cursorPath = new Path();
        cursorPath.moveTo(0, 0);          
        cursorPath.lineTo(0, 45);         
        cursorPath.lineTo(12, 34);        
        cursorPath.lineTo(20, 52);        
        cursorPath.lineTo(28, 48);        
        cursorPath.lineTo(19, 30);        
        cursorPath.lineTo(32, 30);        
        cursorPath.close();               

        // 【优化1核心】对鼠标进行等比例缩放矩阵变换，保证在 2K 屏上依然大小合适
        Matrix scaleMatrix = new Matrix();
        scaleMatrix.setScale(density * 0.7f, density * 0.7f);
        cursorPath.transform(scaleMatrix);

        // 初始化双击机制
        gestureDetector = new GestureDetector(mContext, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                // 【优化3】更安全的屏幕绝对高度获取方式
                int screenHeight = mContext.getResources().getDisplayMetrics().heightPixels;
                
                // 判断双击点是否在任务栏上方 (保留 120px 的底部任务栏安全区)
                if (e.getRawY() < screenHeight - 120) {
                    Toast.makeText(mContext, "🛠️ 桌面预留接口：检测到双击！\n(未来双击这里的图标即可打开内置工具)", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });
    }
    
    // 拦截物理返回键，强制玩家点击左下角菜单返回，提升 PC 沉浸感
    @Override
    public void onBackPressed() {
        // 屏蔽物理返回键
    }
}
