package org.libsdl.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Ikemen GO 纯 Java 桌面系统/梦工厂模式 UI 层
 */
public class DesktopSystemView extends FrameLayout {
    private Context mContext;

    public DesktopSystemView(Context context) {
        super(context);
        this.mContext = context;
        initPureJavaUI();
    }

    private void initPureJavaUI() {
        // 1. 彻底拦截所有底层的触摸，防止穿透到游戏中
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);

        // 2. Windows 10/11 风格默认壁纸渐变色 (深蓝到浅蓝)
        GradientDrawable winBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#005A9E"), Color.parseColor("#0078D7"), Color.parseColor("#00A4EF")}
        );
        setBackground(winBg);

        // 3. 中心提示文字 (居中)
        TextView hintText = new TextView(mContext);
        hintText.setText("💻 Ikemen GO 桌面/梦工厂模式已激活\n\n系统已全屏铺满，底层游戏完美挂起。");
        hintText.setTextColor(Color.WHITE);
        hintText.setTextSize(22f);
        hintText.setTypeface(null, Typeface.BOLD);
        hintText.setGravity(Gravity.CENTER);
        
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.gravity = Gravity.CENTER;
        addView(hintText, hintParams);

        // 4. 底部任务栏容器 (透明背景，完全与壁纸融为一体)
        LinearLayout taskbar = new LinearLayout(mContext);
        taskbar.setOrientation(LinearLayout.HORIZONTAL);
        taskbar.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        taskbar.setPadding(40, 0, 40, 0);
        taskbar.setBackgroundColor(Color.TRANSPARENT); // 移除之前的黑色，完美融入背景

        FrameLayout.LayoutParams taskbarParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 120);
        taskbarParams.gravity = Gravity.BOTTOM; // 锚定在底部
        addView(taskbar, taskbarParams);

        // 5. "返回游戏" 按钮 (Win 徽标位置)
        Button btnReturn = new Button(mContext);
        btnReturn.setText("⬅️ 返回游戏 (Resume)");
        btnReturn.setTextColor(Color.WHITE);
        btnReturn.setBackgroundColor(Color.parseColor("#D32F2F")); // 保持红色醒目
        btnReturn.setTextSize(16f);
        btnReturn.setPadding(40, 20, 40, 20);
        
        // 绑定关闭逻辑
        btnReturn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mContext instanceof SDLActivity) {
                    ((SDLActivity) mContext).toggleDesktopMode(false);
                }
            }
        });
        taskbar.addView(btnReturn);
    }

    // 每次显示时强制自己跑到最顶层
    public void onOpen() {
        bringToFront();
        requestFocus();
    }

    // 终极物理防御：拦截所有的触摸滑动，绝对不让事件漏给底层的 SDLSurface 导致假死
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);
        return true; 
    }
}
