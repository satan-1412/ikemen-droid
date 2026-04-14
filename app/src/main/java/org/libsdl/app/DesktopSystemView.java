package org.libsdl.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Ikemen GO 纯 Java 桌面系统 / Windows 11 风格梦工厂模式
 */
public class DesktopSystemView extends FrameLayout {
    private Context mContext;

    public DesktopSystemView(Context context) {
        super(context);
        this.mContext = context;
        initPureJavaUI();
    }

    private void initPureJavaUI() {
        // 1. 彻底拦截触摸，防止底层游戏误触
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);

        // 2. Windows 11 风格的深邃蓝背景渐变
        GradientDrawable winBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#003366"), Color.parseColor("#005A9E"), Color.parseColor("#0078D7")}
        );
        setBackground(winBg);

        // 3. 纯 Java 绘制巨型 Windows 11 徽标 (背景水印)
        View winLogo = new View(mContext) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override
            protected void onDraw(Canvas canvas) {
                p.setColor(Color.WHITE);
                p.setAlpha(20); // 极度透明，作为背景水印
                float cx = getWidth() / 2f;
                float cy = getHeight() / 2f - 50;
                float size = Math.min(getWidth(), getHeight()) * 0.4f; // 占据屏幕 40%
                float gap = size * 0.04f;
                float rectSize = (size - gap) / 2f;
                float corner = rectSize * 0.08f; // Win11 风格的轻微圆角
                
                // 画四个方块
                canvas.drawRoundRect(cx - rectSize - gap/2, cy - rectSize - gap/2, cx - gap/2, cy - gap/2, corner, corner, p);
                canvas.drawRoundRect(cx + gap/2, cy - rectSize - gap/2, cx + rectSize + gap/2, cy - gap/2, corner, corner, p);
                canvas.drawRoundRect(cx - rectSize - gap/2, cy + gap/2, cx - gap/2, cy + rectSize + gap/2, corner, corner, p);
                canvas.drawRoundRect(cx + gap/2, cy + gap/2, cx + rectSize + gap/2, cy + rectSize + gap/2, corner, corner, p);
            }
        };
        addView(winLogo, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 4. 屏幕中心的信息弹窗 (仿 PC 窗口)
        LinearLayout centerWindow = new LinearLayout(mContext);
        centerWindow.setOrientation(LinearLayout.VERTICAL);
        centerWindow.setGravity(Gravity.CENTER);
        
        TextView titleText = new TextView(mContext);
        titleText.setText("系统已挂起 (System Suspended)");
        titleText.setTextColor(Color.WHITE);
        titleText.setTextSize(28f);
        titleText.setTypeface(null, Typeface.BOLD);
        titleText.setShadowLayer(4f, 0, 4f, Color.BLACK);
        
        TextView subText = new TextView(mContext);
        subText.setText("梦工厂工作台环境已加载。\n底部点击 [恢复游戏] 即可瞬间返回。");
        subText.setTextColor(Color.parseColor("#DDDDDD"));
        subText.setTextSize(16f);
        subText.setGravity(Gravity.CENTER);
        subText.setPadding(0, 20, 0, 0);
        
        centerWindow.addView(titleText);
        centerWindow.addView(subText);
        
        LayoutParams centerParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        centerParams.gravity = Gravity.CENTER;
        addView(centerWindow, centerParams);

        // 5. 底部任务栏 (毛玻璃黑)
        LinearLayout taskbar = new LinearLayout(mContext);
        taskbar.setOrientation(LinearLayout.HORIZONTAL);
        taskbar.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        taskbar.setPadding(40, 0, 40, 0);
        taskbar.setBackgroundColor(Color.parseColor("#E61E1E1E")); // 90% 不透明度的深灰色

        LayoutParams taskbarParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120);
        taskbarParams.gravity = Gravity.BOTTOM;
        addView(taskbar, taskbarParams);

        // 6. 任务栏 Start 按钮 (恢复游戏)
        LinearLayout startBtn = new LinearLayout(mContext);
        startBtn.setOrientation(LinearLayout.HORIZONTAL);
        startBtn.setGravity(Gravity.CENTER);
        startBtn.setPadding(50, 15, 50, 15);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.parseColor("#33FFFFFF")); // 半透明悬浮感
        btnBg.setCornerRadius(10f);
        startBtn.setBackground(btnBg);
        
        TextView btnIcon = new TextView(mContext);
        btnIcon.setText("⊞"); // 电脑徽标字符
        btnIcon.setTextColor(Color.parseColor("#00A4EF")); // 微软蓝
        btnIcon.setTextSize(22f);
        
        TextView btnText = new TextView(mContext);
        btnText.setText(" 恢复游戏");
        btnText.setTextColor(Color.WHITE);
        btnText.setTextSize(18f);
        btnText.setTypeface(null, Typeface.BOLD);
        
        startBtn.addView(btnIcon);
        startBtn.addView(btnText);
        
        startBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mContext instanceof SDLActivity) {
                    ((SDLActivity) mContext).toggleDesktopMode(false);
                }
            }
        });
        taskbar.addView(startBtn);
    }

    // 唤醒时的强制全屏与沉浸式锁定
    public void onOpen() {
        bringToFront();
        requestFocus();
        // 强制隐藏状态栏和导航栏，真正铺满全屏
        setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);
        return true; // 吃掉所有触控，绝不漏给底层画布
    }
}
