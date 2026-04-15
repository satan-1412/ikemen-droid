package org.libsdl.app;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Arrays;

/**
 * Ikemen GO 纯血 PC 桌面操作系统引擎 (Win11 亚克力质感)
 */
public class DesktopSystemView extends Dialog {

    private Context mContext;
    private SharedPreferences prefs;
    private float density;

    // === 全局鼠标与网格系统 ===
    private float mouseX = -1f, mouseY = -1f;
    private Path cursorPath;
    private Paint cursorPaintFill, cursorPaintStroke;
    
    // === 核心图层 ===
    private FrameLayout rootLayer, desktopBgLayer, desktopIconsLayer, windowsLayer;
    private LinearLayout taskbarAppsLayout; 
    
    // === 个性化设置中心 ===
    public int bgAlpha = 180, gridSizeBase = 120, iconShape = 1;
    public boolean showGrid = false;
    public String deskBgPath = "", winBgPath = "";
    public int deskVol = 0, winVol = 0;
    public int deskScaleMode = 1, winScaleMode = 1; // 0=居中 1=裁切 2=拉伸
    
    // === 字体定制引擎 ===
    public String fontPath = "";
    public int fontSize = 12;
    public String fontColor = "#FFFFFF";
    public boolean fontShadow = true;
    public String shadowColor = "#000000";
    private Typeface customTypeface = Typeface.DEFAULT;

    public DesktopSystemView(Context context) {
        super(context, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        this.mContext = context;
        this.prefs = context.getSharedPreferences("IkemenWin11Prefs", Context.MODE_PRIVATE);
        this.density = context.getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onStart() {
        super.onStart();
        Window w = getWindow();
        if (w != null) {
            w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            w.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadSettings();
        initMouseEngine();

        rootLayer = new FrameLayout(getContext()) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                // 【核心修复】网格精准渲染
                float gridPx = gridSizeBase * density;
                if (showGrid) {
                    Paint p = new Paint(); p.setColor(Color.argb(50, 255, 255, 255)); p.setStrokeWidth(1);
                    for (float x = 0; x <= getWidth(); x += gridPx) canvas.drawLine(x, 0, x, getHeight(), p);
                    for (float y = 0; y <= getHeight(); y += gridPx) canvas.drawLine(0, y, getWidth(), y, p);
                }
                if (mouseX >= 0 && mouseY >= 0) {
                    canvas.save(); canvas.translate(mouseX, mouseY);
                    canvas.drawPath(cursorPath, cursorPaintFill); canvas.drawPath(cursorPath, cursorPaintStroke);
                    canvas.restore();
                }
            }
            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                int a = event.getActionMasked();
                if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) { mouseX = -1; mouseY = -1; } 
                else { mouseX = event.getX(); mouseY = event.getY(); }
                invalidate(); return super.dispatchTouchEvent(event);
            }
        };
        rootLayer.setClickable(true);

        desktopBgLayer = new FrameLayout(getContext());
        desktopIconsLayer = new FrameLayout(getContext());
        windowsLayer = new FrameLayout(getContext());
        rootLayer.addView(desktopBgLayer, new FrameLayout.LayoutParams(-1, -1));
        rootLayer.addView(desktopIconsLayer, new FrameLayout.LayoutParams(-1, -1));
        rootLayer.addView(windowsLayer, new FrameLayout.LayoutParams(-1, -1));
        refreshDesktopBackground();

        // 现代任务栏
        LinearLayout taskbar = new LinearLayout(getContext());
        taskbar.setOrientation(LinearLayout.HORIZONTAL); taskbar.setGravity(Gravity.CENTER_VERTICAL);
        taskbar.setBackgroundColor(Color.parseColor("#E6111111"));
        FrameLayout.LayoutParams tParams = new FrameLayout.LayoutParams(-1, (int)(55*density)); tParams.gravity = Gravity.BOTTOM;
        rootLayer.addView(taskbar, tParams);

        // “进入游戏”按钮
        LinearLayout startBtn = new LinearLayout(getContext()); startBtn.setGravity(Gravity.CENTER);
        startBtn.setPadding((int)(15*density), 0, (int)(20*density), 0);
        TextView icon = new TextView(getContext()); icon.setText("⊞"); icon.setTextColor(Color.parseColor("#00A4EF")); icon.setTextSize(22f);
        TextView text = new TextView(getContext()); text.setText(" 进入游戏"); text.setTextColor(Color.WHITE); text.setTextSize(16f); text.setTypeface(null, Typeface.BOLD);
        startBtn.addView(icon); startBtn.addView(text);
        
        GradientDrawable startBg = new GradientDrawable(); startBg.setColor(Color.TRANSPARENT); startBg.setCornerRadius(8f*density);
        startBtn.setBackground(startBg);
        startBtn.setOnTouchListener((v, e) -> {
            if(e.getAction() == MotionEvent.ACTION_DOWN) startBg.setColor(Color.parseColor("#44FFFFFF"));
            else if(e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                startBg.setColor(Color.TRANSPARENT);
                if(e.getAction() == MotionEvent.ACTION_UP) { hide(); if (SDLActivity.mSingleton != null) SDLActivity.mSingleton.toggleDesktopMode(false); }
            }
            return true;
        });
        taskbar.addView(startBtn, new LinearLayout.LayoutParams(-2, -1));

        // 任务栏多窗口承载区
        HorizontalScrollView hScroll = new HorizontalScrollView(getContext()); hScroll.setHorizontalScrollBarEnabled(false);
        taskbarAppsLayout = new LinearLayout(getContext()); taskbarAppsLayout.setOrientation(LinearLayout.HORIZONTAL); taskbarAppsLayout.setGravity(Gravity.CENTER_VERTICAL);
        hScroll.addView(taskbarAppsLayout, new ViewGroup.LayoutParams(-2, -1));
        taskbar.addView(hScroll, new LinearLayout.LayoutParams(0, -1, 1f));

        setContentView(rootLayer);
        setupDesktopIcons();
    }

    private void initMouseEngine() {
        cursorPaintFill = new Paint(Paint.ANTI_ALIAS_FLAG); cursorPaintFill.setColor(Color.WHITE); cursorPaintFill.setStyle(Paint.Style.FILL);
        cursorPaintStroke = new Paint(Paint.ANTI_ALIAS_FLAG); cursorPaintStroke.setColor(Color.BLACK); cursorPaintStroke.setStyle(Paint.Style.STROKE); cursorPaintStroke.setStrokeWidth(1.2f * density); 
        cursorPath = new Path(); cursorPath.moveTo(0,0); cursorPath.lineTo(0,35); cursorPath.lineTo(9,26); cursorPath.lineTo(16,42); cursorPath.lineTo(22,38); cursorPath.lineTo(15,22); cursorPath.lineTo(26,22); cursorPath.close();               
        Matrix sm = new Matrix(); sm.setScale(density * 0.35f, density * 0.35f); cursorPath.transform(sm);
    }

    // ==========================================
    // 万能多媒体渲染引擎 (纯原生防占用设计)
    // ==========================================
    private View createMediaEngine(String path, int alpha, int volPercent, int scaleMode) {
        if (path == null || path.trim().isEmpty() || !new File(path).exists()) return null;
        String p = path.toLowerCase();
        
        if (p.endsWith(".mp4") || p.endsWith(".avi") || p.endsWith(".mkv")) {
            // 原生 TextureView 方案：不获取 AudioFocus，绝不暂停游戏背景音乐！
            TextureView tv = new TextureView(getContext()); tv.setAlpha(alpha / 255f);
            MediaPlayer mp = new MediaPlayer();
            try { mp.setDataSource(path); } catch(Exception e){}
            mp.setLooping(true); float vol = volPercent / 100f; mp.setVolume(vol, vol);
            
            tv.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
                    mp.setSurface(new Surface(st)); try { mp.prepareAsync(); } catch(Exception e){}
                }
                public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) { adjustScale(tv, mp, scaleMode, w, h); }
                public boolean onSurfaceTextureDestroyed(SurfaceTexture st) { mp.release(); return true; }
                public void onSurfaceTextureUpdated(SurfaceTexture st) {}
            });
            mp.setOnPreparedListener(m -> { m.start(); adjustScale(tv, m, scaleMode, tv.getWidth(), tv.getHeight()); });
            mp.setOnVideoSizeChangedListener((m, vw, vh) -> adjustScale(tv, m, scaleMode, tv.getWidth(), tv.getHeight()));
            return tv;
        } else if (p.endsWith(".gif")) {
            WebView wv = new WebView(getContext()); wv.setBackgroundColor(Color.TRANSPARENT); wv.setAlpha(alpha/255f);
            String fit = scaleMode == 0 ? "contain" : (scaleMode == 1 ? "cover" : "fill");
            wv.loadDataWithBaseURL("", "<html style='margin:0;padding:0;'><body style='margin:0;padding:0;background-color:transparent;display:flex;justify-content:center;align-items:center;'><img src='file://" + path + "' style='width:100%;height:100%;object-fit:" + fit + ";' /></body></html>", "text/html", "utf-8", null);
            return wv;
        } else {
            ImageView iv = new ImageView(getContext()); iv.setImageURI(Uri.parse("file://" + path)); iv.setAlpha(alpha/255f);
            iv.setScaleType(scaleMode == 0 ? ImageView.ScaleType.CENTER_INSIDE : (scaleMode == 1 ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_XY));
            return iv;
        }
    }

    private void adjustScale(TextureView tv, MediaPlayer mp, int mode, int vW, int vH) {
        if (vW==0 || vH==0 || mp==null) return;
        int mX = mp.getVideoWidth(), mY = mp.getVideoHeight(); if (mX==0 || mY==0) return;
        Matrix m = new Matrix();
        if (mode != 2) {
            float sX = (float)vW/mX, sY = (float)vH/mY;
            float scale = mode == 1 ? Math.max(sX, sY) : Math.min(sX, sY);
            m.setScale(scale * mX / vW, scale * mY / vH, vW/2f, vH/2f);
        }
        tv.setTransform(m);
    }

    private void refreshDesktopBackground() {
        desktopBgLayer.removeAllViews();
        View media = createMediaEngine(deskBgPath, bgAlpha, deskVol, deskScaleMode);
        if (media != null) desktopBgLayer.addView(media, new FrameLayout.LayoutParams(-1, -1));
        else {
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.parseColor("#002244"), Color.parseColor("#004488")});
            bg.setAlpha(bgAlpha); desktopBgLayer.setBackground(bg);
        }
    }

    // ==========================================
    // 桌面图标系统与精准拖拽
    // ==========================================
    private void setupDesktopIcons() {
        desktopIconsLayer.removeAllViews();
        createDesktopIcon("sys_settings", "⚙️", "系统设置");
    }

    private void createDesktopIcon(final String id, String iconStr, String name) {
        FrameLayout iconContainer = new FrameLayout(getContext());
        float actualGrid = gridSizeBase * density;
        int iconSize = (int)(actualGrid - 2); // 【核心修复】只比网格小2像素，完美贴合
        
        float savedX = prefs.getFloat("icon_x_"+id, 0); float savedY = prefs.getFloat("icon_y_"+id, 0);
        
        LinearLayout inner = new LinearLayout(getContext()); inner.setOrientation(LinearLayout.VERTICAL); inner.setGravity(Gravity.CENTER);
        
        TextView iconView = new TextView(getContext()); iconView.setText(iconStr); iconView.setTextSize(actualGrid * 0.3f); iconView.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#44000000")); 
        if (iconShape == 1) bg.setCornerRadius(10f*density); else if (iconShape == 2) bg.setCornerRadius(actualGrid); else bg.setColor(Color.TRANSPARENT); 
        iconView.setBackground(bg);
        inner.addView(iconView, new LinearLayout.LayoutParams((int)(actualGrid*0.6f), (int)(actualGrid*0.6f)));

        TextView nameView = new TextView(getContext()); nameView.setText(name); nameView.setSingleLine(true); nameView.setGravity(Gravity.CENTER);
        applyFontSettings(nameView); // 应用自定义字体和颜色
        inner.addView(nameView, new LinearLayout.LayoutParams(-2, -2));

        iconContainer.addView(inner, new FrameLayout.LayoutParams(-1, -1));
        iconContainer.setLayoutParams(new FrameLayout.LayoutParams(iconSize, iconSize));
        iconContainer.setX(savedX); iconContainer.setY(savedY);
        desktopIconsLayer.addView(iconContainer);

        iconContainer.setOnTouchListener(new View.OnTouchListener() {
            float ox, oy; boolean isDrag = false; long lastClick = 0;
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) { ox = v.getX()-mouseX; oy = v.getY()-mouseY; isDrag=false; inner.setBackgroundColor(Color.parseColor("#44FFFFFF")); }
                else if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    if (!isDrag && (Math.abs(mouseX-(v.getX()-ox))>10 || Math.abs(mouseY-(v.getY()-oy))>10)) { isDrag=true; v.bringToFront(); }
                    if (isDrag) { v.setX(mouseX+ox); v.setY(mouseY+oy); }
                } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                    inner.setBackgroundColor(Color.TRANSPARENT);
                    if (isDrag) {
                        float fx = Math.round(v.getX() / actualGrid) * actualGrid; float fy = Math.round(v.getY() / actualGrid) * actualGrid;
                        v.setX(fx); v.setY(fy); prefs.edit().putFloat("icon_x_"+id, fx).putFloat("icon_y_"+id, fy).apply();
                    } else {
                        long c = System.currentTimeMillis(); if(c - lastClick < 350) { openSettingsWindow(); lastClick = 0; } else lastClick = c;
                    }
                }
                return true; 
            }
        });
    }

    // ==========================================
    // 现代多窗口与任务栏拖拽管理器
    // ==========================================
    private void openSettingsWindow() {
        String titleStr = "系统个性化设置";
        if (windowsLayer.findViewWithTag(titleStr) != null) { windowsLayer.findViewWithTag(titleStr).setVisibility(View.VISIBLE); windowsLayer.findViewWithTag(titleStr).bringToFront(); return; }

        FrameLayout winFrame = new FrameLayout(getContext()); winFrame.setTag(titleStr); winFrame.setClickable(true);
        
        View mediaBg = createMediaEngine(winBgPath, 255, winVol, winScaleMode);
        if (mediaBg != null) winFrame.addView(mediaBg, new FrameLayout.LayoutParams(-1, -1));
        else { GradientDrawable gb = new GradientDrawable(); gb.setColor(Color.parseColor("#F2202020")); gb.setCornerRadius(10f*density); gb.setStroke(1, Color.parseColor("#444444")); winFrame.setBackground(gb); }
        winFrame.setElevation(25f * density);

        LinearLayout content = new LinearLayout(getContext()); content.setOrientation(LinearLayout.VERTICAL); winFrame.addView(content, new FrameLayout.LayoutParams(-1,-1));

        // 标题栏
        LinearLayout tBar = new LinearLayout(getContext()); tBar.setBackgroundColor(Color.parseColor("#99000000")); tBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(getContext()); title.setText("  " + titleStr); applyFontSettings(title);
        tBar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView btnMin = new TextView(getContext()); btnMin.setText(" ━ "); btnMin.setTextColor(Color.WHITE); btnMin.setPadding(30,10,30,10); btnMin.setOnClickListener(v -> winFrame.setVisibility(View.GONE));
        TextView btnClose = new TextView(getContext()); btnClose.setText(" ✕ "); btnClose.setTextColor(Color.WHITE); btnClose.setPadding(30,10,30,10);
        tBar.addView(btnMin); tBar.addView(btnClose);
        
        tBar.setOnTouchListener(new View.OnTouchListener() { float dx,dy; public boolean onTouch(View v,MotionEvent e){
            if(e.getAction()==MotionEvent.ACTION_DOWN){ dx=winFrame.getX()-mouseX; dy=winFrame.getY()-mouseY; winFrame.bringToFront(); }
            else if(e.getAction()==MotionEvent.ACTION_MOVE){ winFrame.setX(mouseX+dx); winFrame.setY(mouseY+dy); } return true;
        }});
        content.addView(tBar, new LinearLayout.LayoutParams(-1, (int)(40*density)));
        View sep = new View(getContext()); sep.setBackgroundColor(Color.parseColor("#0078D7")); content.addView(sep, new LinearLayout.LayoutParams(-1, 2));
        content.addView(buildSettingsContent(), new LinearLayout.LayoutParams(-1,-1));

        // 任务栏专属图标 (修复显示符并支持左右拖拽)
        LinearLayout tBtn = new LinearLayout(getContext()); tBtn.setGravity(Gravity.CENTER); tBtn.setPadding(20,0,20,0);
        GradientDrawable tbBg = new GradientDrawable(); tbBg.setColor(Color.parseColor("#44FFFFFF")); tbBg.setCornerRadius(5f*density); tBtn.setBackground(tbBg);
        TextView tbTxt = new TextView(getContext()); tbTxt.setText("⚙️ 系统"); applyFontSettings(tbTxt); tBtn.addView(tbTxt);
        LinearLayout.LayoutParams tbParams = new LinearLayout.LayoutParams((int)(120*density), -1); tbParams.setMargins(0, (int)(8*density), (int)(10*density), (int)(8*density));
        
        // 任务栏拖拽算法
        tBtn.setOnTouchListener(new View.OnTouchListener() {
            float sX; int sIdx;
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) { sX = mouseX; sIdx = taskbarAppsLayout.indexOfChild(v); v.setAlpha(0.6f); }
                else if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    float diff = mouseX - sX; v.setTranslationX(diff);
                    int nIdx = sIdx;
                    if (diff > v.getWidth() && sIdx < taskbarAppsLayout.getChildCount()-1) nIdx = sIdx + 1;
                    else if (diff < -v.getWidth() && sIdx > 0) nIdx = sIdx - 1;
                    if (nIdx != sIdx) { taskbarAppsLayout.removeView(v); taskbarAppsLayout.addView(v, nIdx); v.setTranslationX(0); sX = mouseX; sIdx = nIdx; }
                } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                    v.setAlpha(1f); v.setTranslationX(0);
                    if (Math.abs(mouseX - sX) < 10) { if (winFrame.getVisibility() == View.VISIBLE) winFrame.setVisibility(View.GONE); else { winFrame.setVisibility(View.VISIBLE); winFrame.bringToFront(); } }
                } return true;
            }
        });
        taskbarAppsLayout.addView(tBtn, tbParams);
        btnClose.setOnClickListener(v -> { windowsLayer.removeView(winFrame); taskbarAppsLayout.removeView(tBtn); });

        FrameLayout.LayoutParams fParams = new FrameLayout.LayoutParams((int)(rootLayer.getWidth()*0.65f), (int)(rootLayer.getHeight()*0.75f));
        fParams.gravity = Gravity.CENTER; windowsLayer.addView(winFrame, fParams);
    }

    // ==========================================
    // 超级控制面板 (配置全功能参数)
    // ==========================================
    private void loadSettings() {
        bgAlpha = prefs.getInt("dt_bgAlpha", 180); gridSizeBase = prefs.getInt("dt_gridSize", 100); showGrid = prefs.getBoolean("dt_showGrid", false); iconShape = prefs.getInt("dt_iconShape", 1);
        deskBgPath = prefs.getString("dt_dPath", ""); winBgPath = prefs.getString("dt_wPath", "");
        deskVol = prefs.getInt("dt_dVol", 0); winVol = prefs.getInt("dt_wVol", 0);
        deskScaleMode = prefs.getInt("dt_dSM", 1); winScaleMode = prefs.getInt("dt_wSM", 1);
        fontPath = prefs.getString("dt_font", ""); fontSize = prefs.getInt("dt_fSize", 12); fontColor = prefs.getString("dt_fColor", "#FFFFFF");
        fontShadow = prefs.getBoolean("dt_fShadow", true); shadowColor = prefs.getString("dt_sColor", "#000000");
        try { if (!fontPath.isEmpty()) customTypeface = Typeface.createFromFile(fontPath); } catch (Exception e) { customTypeface = Typeface.DEFAULT; }
    }

    private View buildSettingsContent() {
        ScrollView scroll = new ScrollView(getContext()); LinearLayout layout = new LinearLayout(getContext()); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(40,20,40,40);

        layout.addView(cTitle("🎨 桌面网格与外观"));
        SeekBar gBar = new SeekBar(getContext()); gBar.setMax(200); gBar.setProgress(gridSizeBase);
        gBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(SeekBar s,int p,boolean b){ gridSizeBase=Math.max(60,p); rootLayer.invalidate(); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){ setupDesktopIcons(); } });
        layout.addView(gBar);
        Button btnGrid = new Button(getContext()); btnGrid.setText(showGrid ? "网格线: 开启" : "网格线: 隐藏"); btnGrid.setOnClickListener(v -> { showGrid=!showGrid; btnGrid.setText(showGrid?"网格线: 开启":"网格线: 隐藏"); rootLayer.invalidate(); }); layout.addView(btnGrid);

        layout.addView(cTitle("🔤 字体定制引擎"));
        EditText fPathInput = cInput(fontPath, "自定义字体路径 (.ttf/.otf)"); layout.addView(fPathInput);
        Button pickF = new Button(getContext()); pickF.setText("选择字体文件"); pickF.setOnClickListener(v -> showFileBrowser(fPathInput)); layout.addView(pickF);
        
        EditText fColorInput = cInput(fontColor, "字体颜色代码 (如 #FF0000)"); layout.addView(fColorInput);
        Button btnShadow = new Button(getContext()); btnShadow.setText(fontShadow ? "字体阴影: 开启" : "字体阴影: 关闭"); btnShadow.setOnClickListener(v -> { fontShadow=!fontShadow; btnShadow.setText(fontShadow?"字体阴影: 开启":"字体阴影: 关闭"); }); layout.addView(btnShadow);
        EditText sColorInput = cInput(shadowColor, "阴影颜色代码 (如 #000000)"); layout.addView(sColorInput);

        layout.addView(cTitle("🖼️ 桌面背景配置 (支持所有图片/GIF/视频)"));
        EditText dPathInput = cInput(deskBgPath, "桌面多媒体路径"); layout.addView(dPathInput);
        Button pickD = new Button(getContext()); pickD.setText("浏览存储卡选择文件"); pickD.setOnClickListener(v -> showFileBrowser(dPathInput)); layout.addView(pickD);
        
        SeekBar aBar = new SeekBar(getContext()); aBar.setMax(255); aBar.setProgress(bgAlpha);
        aBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){ public void onProgressChanged(SeekBar s,int p,boolean b){ bgAlpha=p; refreshDesktopBackground(); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){} }); layout.addView(aBar);
        layout.addView(cText("视频音量 (0-100)")); SeekBar dvBar = new SeekBar(getContext()); dvBar.setMax(100); dvBar.setProgress(deskVol); dvBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){ public void onProgressChanged(SeekBar s,int p,boolean b){ deskVol=p; } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){} }); layout.addView(dvBar);
        Button dModeBtn = new Button(getContext()); dModeBtn.setText(deskScaleMode==0?"模式: 居中":(deskScaleMode==1?"模式: 裁切全屏":"模式: 强制拉伸")); dModeBtn.setOnClickListener(v->{ deskScaleMode=(deskScaleMode+1)%3; dModeBtn.setText(deskScaleMode==0?"模式: 居中":(deskScaleMode==1?"模式: 裁切全屏":"模式: 强制拉伸")); }); layout.addView(dModeBtn);

        layout.addView(cTitle("🖼️ 窗口背景配置 (支持所有图片/GIF/视频)"));
        EditText wPathInput = cInput(winBgPath, "窗口多媒体路径"); layout.addView(wPathInput);
        Button pickW = new Button(getContext()); pickW.setText("浏览存储卡选择文件"); pickW.setOnClickListener(v -> showFileBrowser(wPathInput)); layout.addView(pickW);
        layout.addView(cText("视频音量 (0-100)")); SeekBar wvBar = new SeekBar(getContext()); wvBar.setMax(100); wvBar.setProgress(winVol); wvBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){ public void onProgressChanged(SeekBar s,int p,boolean b){ winVol=p; } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){} }); layout.addView(wvBar);
        Button wModeBtn = new Button(getContext()); wModeBtn.setText(winScaleMode==0?"模式: 居中":(winScaleMode==1?"模式: 裁切填满":"模式: 强制拉伸")); wModeBtn.setOnClickListener(v->{ winScaleMode=(winScaleMode+1)%3; wModeBtn.setText(winScaleMode==0?"模式: 居中":(winScaleMode==1?"模式: 裁切填满":"模式: 强制拉伸")); }); layout.addView(wModeBtn);

        layout.addView(cTitle(""));
        Button resetBtn = new Button(getContext()); resetBtn.setText("🔄 恢复默认布局与设置"); resetBtn.setTextColor(Color.WHITE); resetBtn.setBackgroundColor(Color.parseColor("#D32F2F"));
        resetBtn.setOnClickListener(v -> { prefs.edit().clear().apply(); loadSettings(); setupDesktopIcons(); refreshDesktopBackground(); Toast.makeText(getContext(),"已重置",Toast.LENGTH_SHORT).show(); }); layout.addView(resetBtn);

        Button saveBtn = new Button(getContext()); saveBtn.setText("💾 保存所有设置"); saveBtn.setTextColor(Color.WHITE); saveBtn.setBackgroundColor(Color.parseColor("#1976D2"));
        saveBtn.setOnClickListener(v -> {
            deskBgPath=dPathInput.getText().toString().trim(); winBgPath=wPathInput.getText().toString().trim(); fontPath=fPathInput.getText().toString().trim(); fontColor=fColorInput.getText().toString().trim(); shadowColor=sColorInput.getText().toString().trim();
            prefs.edit().putInt("dt_bgAlpha",bgAlpha).putInt("dt_gridSize",gridSizeBase).putBoolean("dt_showGrid",showGrid).putString("dt_dPath",deskBgPath).putString("dt_wPath",winBgPath).putInt("dt_dVol",deskVol).putInt("dt_wVol",winVol).putInt("dt_dSM",deskScaleMode).putInt("dt_wSM",winScaleMode).putString("dt_font",fontPath).putString("dt_fColor",fontColor).putBoolean("dt_fShadow",fontShadow).putString("dt_sColor",shadowColor).apply();
            loadSettings(); refreshDesktopBackground(); setupDesktopIcons(); Toast.makeText(getContext(),"保存成功，需重新打开窗口生效",Toast.LENGTH_SHORT).show();
        }); layout.addView(saveBtn);
        scroll.addView(layout); return scroll;
    }

    // 辅助控件创建方法
    private TextView cTitle(String t){ TextView tv=new TextView(getContext()); tv.setText(t); tv.setTextColor(Color.parseColor("#00A4EF")); tv.setTextSize(14f); tv.setPadding(0,40,0,10); tv.setTypeface(null,Typeface.BOLD); return tv; }
    private TextView cText(String t){ TextView tv=new TextView(getContext()); tv.setText(t); tv.setTextColor(Color.LTGRAY); tv.setPadding(0,20,0,0); return tv; }
    private EditText cInput(String val, String hint){ EditText e=new EditText(getContext()); e.setText(val); e.setHint(hint); e.setTextColor(Color.WHITE); e.setHintTextColor(Color.GRAY); return e; }

    // 颜色解析保护
    private int parseColor(String hex, String fallback) { try{ return Color.parseColor(hex); }catch(Exception e){ return Color.parseColor(fallback); } }
    
    // 全局字体应用接口
    private void applyFontSettings(TextView tv) {
        tv.setTypeface(customTypeface); tv.setTextColor(parseColor(fontColor, "#FFFFFF")); tv.setTextSize(fontSize);
        if (fontShadow) tv.setShadowLayer(4f, 1f, 1f, parseColor(shadowColor, "#000000")); else tv.setShadowLayer(0,0,0,0);
    }

    // ==========================================
    // 现代化 Fluent 文件选择器
    // ==========================================
    private void showFileBrowser(final EditText targetInput) {
        final Dialog d = new Dialog(getContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        LinearLayout root = new LinearLayout(getContext()); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#EE1A1A1A")); root.setPadding(40,40,40,40);
        TextView title = new TextView(getContext()); title.setText("请选择任意图片、GIF、视频或字体文件"); title.setTextColor(Color.parseColor("#00A4EF")); title.setTextSize(18f); root.addView(title);
        final TextView pathView = new TextView(getContext()); pathView.setTextColor(Color.WHITE); pathView.setPadding(0,20,0,40); root.addView(pathView);
        ScrollView scroll = new ScrollView(getContext()); LinearLayout list = new LinearLayout(getContext()); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list); root.addView(scroll, new LinearLayout.LayoutParams(-1,-1));
        final File[] curDir = {Environment.getExternalStorageDirectory()};
        Runnable ref = new Runnable() { public void run() {
            list.removeAllViews(); pathView.setText(curDir[0].getAbsolutePath());
            if (curDir[0].getParentFile() != null) { Button up=new Button(getContext()); up.setText("⬅️ 返回上一级"); up.setOnClickListener(v->{ curDir[0]=curDir[0].getParentFile(); this.run(); }); list.addView(up); }
            File[] files = curDir[0].listFiles();
            if (files != null) {
                Arrays.sort(files, (f1,f2)->{ if(f1.isDirectory()&&!f2.isDirectory())return -1; if(!f1.isDirectory()&&f2.isDirectory())return 1; return f1.getName().compareToIgnoreCase(f2.getName());});
                for (File f : files) {
                    Button btn = new Button(getContext()); btn.setAllCaps(false); btn.setText((f.isDirectory()?"📁 ":"📄 ") + f.getName()); btn.setTextColor(Color.WHITE);
                    btn.setBackgroundColor(Color.TRANSPARENT);
                    btn.setOnClickListener(v->{ if(f.isDirectory()){ curDir[0]=f; this.run(); }else{ targetInput.setText(f.getAbsolutePath()); d.dismiss(); } }); list.addView(btn);
                }
            }
        }}; ref.run(); d.setContentView(root); d.show();
    }
    @Override public void onBackPressed() { } 
}
