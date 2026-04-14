package org.libsdl.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * Ikemen GO 纯 Java 桌面系统/梦工厂模式 UI 层
 */
public class DesktopSystemView extends RelativeLayout {
    private Context mContext;

    public DesktopSystemView(Context context) {
        super(context);
        this.mContext = context;
        initPureJavaUI();
    }

    private void initPureJavaUI() {
        // 1. 设定全屏背景色（经典 Win 蓝屏色，彻底遮挡游戏画面）
        setBackgroundColor(Color.parseColor("#0078D7"));
        setClickable(true); // 拦截底层的触摸事件，防止点到游戏里
        setFocusable(true);

        // 2. 创建底部任务栏 (Taskbar)
        LinearLayout taskbar = new LinearLayout(mContext);
        taskbar.setId(View.generateViewId()); // 动态生成 ID
        taskbar.setOrientation(LinearLayout.HORIZONTAL);
        taskbar.setBackgroundColor(Color.parseColor("#1E1E1E")); // 深灰色任务栏
        taskbar.setGravity(Gravity.CENTER_VERTICAL);
        taskbar.setPadding(30, 0, 30, 0);

        // 设定任务栏的高度和位置（锚定在底部）
        RelativeLayout.LayoutParams taskbarParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 120); // 任务栏高度 120px
        taskbarParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        taskbar.setLayoutParams(taskbarParams);

        // 3. 任务栏上的 "返回游戏" 按钮
        Button btnReturn = new Button(mContext);
        btnReturn.setText("⬅️ 返回游戏 (Resume)");
        btnReturn.setTextColor(Color.WHITE);
        btnReturn.setBackgroundColor(Color.parseColor("#D32F2F")); // 醒目的红色
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

        // 将任务栏加入主视图
        addView(taskbar);

        // 4. 创建桌面核心区域 (用于后续放置图标、浮窗或调试工具)
        FrameLayout desktopArea = new FrameLayout(mContext);
        RelativeLayout.LayoutParams desktopParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        desktopParams.addRule(RelativeLayout.ABOVE, taskbar.getId()); // 严格置于任务栏上方
        desktopArea.setLayoutParams(desktopParams);
        
        // 5. 桌面中心的提示文字
        TextView hintText = new TextView(mContext);
        hintText.setText("💻 Ikemen GO 桌面/梦工厂模式已激活\n\n底层游戏引擎已挂起。\n后续可在此区域添加纯 Java 编写的内存修改器或工具窗。");
        hintText.setTextColor(Color.WHITE);
        hintText.setTextSize(22f);
        hintText.setTypeface(null, Typeface.BOLD);
        hintText.setGravity(Gravity.CENTER);
        
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.gravity = Gravity.CENTER;
        desktopArea.addView(hintText, hintParams);

        // 将桌面区域加入主视图
        addView(desktopArea);
    }

    public void onOpen() {
        // 每次进入桌面模式时触发，确保图层在最顶端
        bringToFront();
    }
}
