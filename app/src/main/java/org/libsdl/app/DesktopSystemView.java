package org.libsdl.app;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Ikemen GO 纯 Java 桌面系统 / Windows 11 风格梦工厂模式
 * 采用 Dialog 降维打击：彻底解决 UI 刷新卡死与全屏遮挡问题
 */
public class DesktopSystemView extends Dialog {

    public DesktopSystemView(Context context) {
        // 使用全屏且无标题栏的纯黑主题，剥夺底部导航栏空间
        super(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. 强制沉浸式全屏，彻底隐藏所有系统导航栏和小白条
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

        // 2. 构建纯 Java 布局根节点
        FrameLayout root = new FrameLayout(getContext());
        
        // 3. Windows 11 风格深蓝背景渐变
        GradientDrawable winBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#003366"), Color.parseColor("#005A9E"), Color.parseColor("#0078D7")}
        );
        root.setBackground(winBg);

        // 4. 纯代码手绘巨型 Windows 11 徽标 (背景水印)
        View winLogo = new View(getContext()) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override
            protected void onDraw(Canvas canvas) {
                p.setColor(Color.WHITE);
                p.setAlpha(25); // 极度透明作为背景水印
                float cx = getWidth() / 2f;
                float cy = getHeight() / 2f - 50;
                float size = Math.min(getWidth(), getHeight()) * 0.35f; 
                float gap = size * 0.04f;
                float rectSize = (size - gap) / 2f;
                float corner = rectSize * 0.08f; 
                
                // 画出经典的四个蓝色方块（这里被填充为半透明白色）
                canvas.drawRoundRect(cx - rectSize - gap/2, cy - rectSize - gap/2, cx - gap/2, cy - gap/2, corner, corner, p);
                canvas.drawRoundRect(cx + gap/2, cy - rectSize - gap/2, cx + rectSize + gap/2, cy - gap/2, corner, corner, p);
                canvas.drawRoundRect(cx - rectSize - gap/2, cy + gap/2, cx - gap/2, cy + rectSize + gap/2, corner, corner, p);
                canvas.drawRoundRect(cx + gap/2, cy + gap/2, cx + rectSize + gap/2, cy + rectSize + gap/2, corner, corner, p);
            }
        };
        root.addView(winLogo, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 5. 屏幕中心的运行状态窗口
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
        subText.setText("梦工厂高级工作台环境已加载。\n引擎图形与音频流已被强制阻断。");
        subText.setTextColor(Color.parseColor("#DDDDDD"));
        subText.setTextSize(16f);
        subText.setGravity(Gravity.CENTER);
        subText.setPadding(0, 20, 0, 0);
        
        centerWindow.addView(titleText);
        centerWindow.addView(subText);
        
        FrameLayout.LayoutParams centerParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        centerParams.gravity = Gravity.CENTER;
        root.addView(centerWindow, centerParams);

        // 6. 底部毛玻璃任务栏
        LinearLayout taskbar = new LinearLayout(getContext());
        taskbar.setOrientation(LinearLayout.HORIZONTAL);
        taskbar.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        taskbar.setPadding(40, 0, 40, 0);
        taskbar.setBackgroundColor(Color.parseColor("#E61E1E1E")); // 90% 不透明黑

        FrameLayout.LayoutParams taskbarParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120);
        taskbarParams.gravity = Gravity.BOTTOM;
        root.addView(taskbar, taskbarParams);

        // 7. 开始菜单按钮 (用于恢复游戏)
        LinearLayout startBtn = new LinearLayout(getContext());
        startBtn.setOrientation(LinearLayout.HORIZONTAL);
        startBtn.setGravity(Gravity.CENTER);
        startBtn.setPadding(50, 15, 50, 15);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.parseColor("#33FFFFFF")); // 悬浮质感
        btnBg.setCornerRadius(10f);
        startBtn.setBackground(btnBg);
        
        TextView btnIcon = new TextView(getContext());
        btnIcon.setText("⊞"); // PC开始菜单徽标
        btnIcon.setTextColor(Color.parseColor("#00A4EF")); // 微软蓝
        btnIcon.setTextSize(22f);
        
        TextView btnText = new TextView(getContext());
        btnText.setText(" 恢复游戏");
        btnText.setTextColor(Color.WHITE);
        btnText.setTextSize(18f);
        btnText.setTypeface(null, Typeface.BOLD);
        
        startBtn.addView(btnIcon);
        startBtn.addView(btnText);
        
        // 点击返回游戏逻辑
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getContext() instanceof SDLActivity) {
                    ((SDLActivity) getContext()).toggleDesktopMode(false);
                }
            }
        });
        taskbar.addView(startBtn);

        setContentView(root);
    }
    
    // 拦截返回键，强制玩家点击左下角菜单返回，提升 PC 沉浸感
    @Override
    public void onBackPressed() {
        // 什么都不做
    }
}
