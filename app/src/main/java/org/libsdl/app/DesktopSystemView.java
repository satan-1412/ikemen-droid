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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Ikemen GO 纯 Java 桌面系统 / Windows 11 风格梦工厂模式
 */
public class DesktopSystemView extends Dialog {

    public DesktopSystemView(Context context) {
        super(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. 强制沉浸式全屏
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

        FrameLayout root = new FrameLayout(getContext());
        
        // 2. Windows 11 风格背景
        GradientDrawable winBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#003366"), Color.parseColor("#005A9E"), Color.parseColor("#0078D7")}
        );
        root.setBackground(winBg);

        // 3. 绘制 Win11 徽标水印
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

        // 4. 运行状态窗口
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
        root.addView(centerWindow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        // 5. 底部任务栏
        LinearLayout taskbar = new LinearLayout(getContext());
        taskbar.setOrientation(LinearLayout.HORIZONTAL);
        taskbar.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        taskbar.setPadding(40, 0, 40, 0);
        taskbar.setBackgroundColor(Color.parseColor("#E61E1E1E")); 
        root.addView(taskbar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120, Gravity.BOTTOM));

        // 6. 拟态开始按钮 (恢复游戏)
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
        
        // --- 核心修复：点击监听 ---
        startBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setScaleX(0.92f); v.setScaleY(0.92f); // 点击缩放反馈
                        btnBg.setColor(Color.parseColor("#55FFFFFF"));
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setScaleX(1.0f); v.setScaleY(1.0f);
                        btnBg.setColor(Color.parseColor("#33FFFFFF"));
                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            // 使用单例直接触发，绕过 Context 包装问题
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
    
    @Override
    public void onBackPressed() {
        // 屏蔽返回键，强制点击开始按钮
    }
}
