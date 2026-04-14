package org.libsdl.app;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import org.ikemen_engine.ikemen_go.R;

/**
 * 独立的桌面/功能系统
 * 模仿 Win 界面，用于内置工具和高级设置
 */
public class DesktopSystemView extends FrameLayout {
    private Context mContext;
    private View mTaskBar; // 底部任务栏
    private View mDesktopIcons; // 桌面图标容器

    public DesktopSystemView(Context context) {
        super(context);
        this.mContext = context;
        initView();
    }

    private void initView() {
        // 设置背景（可以弄个 Win 经典的蓝屏或壁纸）
        setBackgroundColor(Color.parseColor("#0078D7")); 

        // 1. 加载桌面布局 (建议创建一个对应的 XML: desktop_mode_layout.xml)
        // 布局内应包含：任务栏、开始菜单、快捷方式区
        // LayoutInflater.from(mContext).inflate(R.layout.desktop_mode_layout, this, true);

        // 2. 框架级按钮：回到游戏
        // Button btnReturn = findViewById(R.id.btn_return_game);
        // btnReturn.setOnClickListener(v -> ((SDLActivity)mContext).toggleDesktopMode(false));

        // 3. 框架级按钮：彻底关闭桌面模式 (如果需要区别于回到游戏)
        // Button btnClose = findViewById(R.id.btn_close_desktop);
    }

    public void onOpen() {
        // 这里处理打开时的逻辑，比如刷新工具列表
    }

    // 这里以后可以添加 addApp(Tool tool) 方法，动态向桌面添加工具图标
}
