package org.libsdl.app;

import android.text.Editable;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Environment;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.media.MediaPlayer;
import android.view.TextureView;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.ViewGroup;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

        public class DynamicGamepadView extends View {
    // ================= UI 尺寸比例与遮罩图控制变量 =================
    public String menuSkinUri = "";
    public Bitmap menuSkinBitmap = null;
    public float menuWidth = 230; 
    public float menuHeight = 90;
    public float dialogWidthRatio = 0.8f;  
    public float dialogHeightRatio = 0.8f; 
    public boolean isOverlayVisible = true; 
    public boolean overlayMirror1 = false;  
    public boolean overlayMirror2 = false;  
    public android.graphics.Movie overlayMovie1 = null; 
    public android.graphics.Movie overlayMovie2 = null;
    public long movieStart1 = 0;
    public long movieStart2 = 0;
    // 👇 新增：菜单按钮的高阶属性 👇
    public boolean isMenuLocked = false; // 【补回被误删的变量】是否锁定拖拽
    public boolean isDynamicScaleEnabled = false; // 是否开启跨设备动态键位适配
    public int menuColor = Color.parseColor("#333333");
    public int menuTextColor = Color.WHITE;
    public int menuTextSizeFactor = 100;
    public int menuShape = 1; // 1 = 矩形, 0 = 圆形
    public String menuPressedSkinUri = "";
    public Bitmap menuPressedSkinBitmap = null;
    public int menuPressedEffectColor = 0;
    public int menuPressedEffectAlpha = 150;
        public String menuButtonName = L("⚙ 高级设置"); // 【新增】菜单按钮自定义名字
    public static boolean alwaysAskFolder = true; // 【新增】每次启动选择目录开关 (全局生效)
    public static boolean isIntegrationModeEnabled = false; // 【新增】整合包兼容模式开关
    
    // ================= 新增：预设文件夹管理系统变量 =================
    public static class FolderPreset {
        public String name;
        public String uri;
        public int color;
        public String motifPath; // 【新增专属主题字段】
        public FolderPreset(String n, String u, int c, String m) { this.name=n; this.uri=u; this.color=c; this.motifPath=m; }
        public JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject(); o.put("name", name); o.put("uri", uri); o.put("color", color); 
            o.put("motifPath", motifPath != null ? motifPath : ""); return o;
        }
        public static FolderPreset fromJson(JSONObject o) {
            return new FolderPreset(o.optString("name",""), o.optString("uri",""), o.optInt("color", Color.WHITE), o.optString("motifPath",""));
        }
    }
    public List<FolderPreset> folderPresets = new ArrayList<>(); // 文件夹预设列表


    // ================= 全局弹窗 UI 自定义系统变量 (被误删的变量补回) =================
    public int dialogBgColor = Color.parseColor("#222222"); 
    public int dialogBgAlpha = 230; 
    public int dialogTextColor = Color.WHITE; 
    public float dialogTextSize = 14f; 
    public String dialogBgImageUri = ""; 
    public Bitmap dialogBgBitmap = null; 

    private static final String PREFS_NAME = "IkemenGamepad_Pro_V5";    
    
    private static final String KEY_LAYOUT_PREFIX = "LayoutSlot_";
    public int currentSlot = 0;
    public int joystickMode = 0; // 0=十字, 1=圆盘, 2=街机
    public boolean isVibrationOn = true;
    public int vibrationIntensity = 30; // 震动强度 (建议0-100，即震动毫秒数)
        public boolean isAutoHideEnabled = true; // 自动隐藏开关
    public int autoHideSeconds = 5;          // 自动隐藏延迟时间（秒）
    
    // 【新增】全局按压反馈变量
    public boolean isGlobalFeedbackEnabled = true; 
    public int globalFeedbackScaleInt = 85; // 85代表缩小至85%，115代表放大至115%，100代表不变
    // ================= 新增：物理手柄系统核心变量 =================
    public float gamepadDeadzone = 0.2f;      // 摇杆死区/灵敏度 (0.01~1.0)
    public int gamepadUIMode = 1;             // 手柄联动模式: 0=无反应, 1=屏幕按键同步发光, 2=按下手柄时自动隐藏虚拟按键
    public boolean isGamepadVibrationOn = true; // 手柄硬件震动开关
    // 手柄测试与绑定模式的状态锁
    public boolean isGamepadBindingMode = false;
    public VirtualButton currentBindingTargetButton = null;
    public android.app.Dialog currentBindingDialog = null;
    public android.widget.TextView testFeedbackText = null;




       public float joyBaseX = 250, joyBaseY = 700;
    public float joyRadius = 180;
    public float joyHitboxRadius = 270; // 【新增】摇杆独立触摸判定范围
    public int joyAlpha = 200;
    public int joyColor = Color.parseColor("#FF5555"); // 原有行
    public boolean isJoyLocked = false; // 【新增】摇杆位置锁定状态

    private float joyKnobX = 250, joyKnobY = 700;
    private int joyPointerId = -1;
    private boolean isDraggingJoy = false;
    private final Paint dashPaint = new Paint();
    private Paint.FontMetrics textFontMetrics; // 缓存字体属性
    // 【修改】拆分为摇杆外框和摇杆中心两层皮肤
    public String joySkinBaseUri = "";
    public Bitmap joySkinBaseBitmap = null;
    public String joySkinKnobUri = "";
    public Bitmap joySkinKnobBitmap = null;
    public int imagePickerTarget = 0; // 0=无, 1=摇杆外框, 2=摇杆中心, 3=普通按键

    public float menuX = 20, menuY = 20;
   
        public float menuScale = 1.0f;
    public int menuAlpha = 220;
    private boolean isDraggingMenu = false; 
    
    // 👇 新增：菜单按钮长按判定变量 👇
    private boolean isMenuDown = false;
    private float menuDownX, menuDownY;
    private Runnable menuLongPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (isMenuDown) {
                isMenuDown = false; // 标记已被消费，防止抬手时触发短按
                showMenuSettingsDialog(); // 触发长按，打开外观设置
                triggerVibrate(50); // 给个震动反馈提示用户长按成功
            }
        }
    };
    
    private final List<VirtualButton> buttons = new ArrayList<>();
    private final Paint paintBtn = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintMenu = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tempRect = new RectF(); // 用于绘制圆角矩形

    private final SharedPreferences prefs;
    public boolean isEditMode = false;
        public boolean isGridSnapMode = false; // 是否开启网格吸附
            public boolean pendingDefaultLayout = false; // 【新增】延迟加载标记
            public boolean pendingResolutionScale = false; // 【新增】等待动态分辨率拉伸标记
            private int loadedSavedWidth = 0; // 【新增】存档记录的屏幕宽
            private int loadedSavedHeight = 0; // 【新增】存档记录的屏幕高
            // 【新增】遮罩图功能相关变量
    public int overlayMode = 0; // 0=关闭, 1=单图, 2=双图
        // ================= 新增：按键风格系统变量 =================
    public int currentStyleIndex = 0;
    public List<GamepadStyle> styleList = new ArrayList<>();
    public static final int JOYSTICK_MODE_STYLE = 4; // 新增模式：跟随风格

    // 按键风格实体类
    public static class GamepadStyle {
        public String styleName;
        public String joyBaseUri = "";
        public String joyKnobUri = "";
        public String btnNormalUri = "";
        public String btnSquareUri = ""; // 【新增】方形按键专属皮肤
        public String btnPressedUri = ""; // 全局默认按下特效
        public int globalBtnColor = Color.GRAY;
        public int globalPressedColor = Color.WHITE;
        public int globalPressedAlpha = 150;
        
        public GamepadStyle(String name) { this.styleName = name; }
        
        public JSONObject toJson() throws Exception {
            JSONObject obj = new JSONObject();
            obj.put("name", styleName); obj.put("joyBaseUri", joyBaseUri);
            obj.put("joyKnobUri", joyKnobUri); obj.put("btnNormalUri", btnNormalUri);
            obj.put("btnSquareUri", btnSquareUri); // 【新增】
            obj.put("btnPressedUri", btnPressedUri); obj.put("btnColor", globalBtnColor);
            obj.put("pressedColor", globalPressedColor); obj.put("pressedAlpha", globalPressedAlpha);
            return obj;
        }
        
        public static GamepadStyle fromJson(JSONObject obj) {
            GamepadStyle style = new GamepadStyle(obj.optString("name", L("未命名风格")));
            style.joyBaseUri = obj.optString("joyBaseUri", "");
            style.joyKnobUri = obj.optString("joyKnobUri", "");
            style.btnNormalUri = obj.optString("btnNormalUri", "");
            style.btnSquareUri = obj.optString("btnSquareUri", ""); // 【新增】
            style.btnPressedUri = obj.optString("btnPressedUri", "");
            style.globalBtnColor = obj.optInt("btnColor", Color.GRAY);
            style.globalPressedColor = obj.optInt("pressedColor", Color.WHITE);
            style.globalPressedAlpha = obj.optInt("pressedAlpha", 150);
            return style;
        }
    }


    public String overlayUri1 = "";
    public Bitmap overlayBmp1 = null;
    public float overlayX1 = 0, overlayY1 = 0, overlayScaleX1 = 1.0f, overlayScaleY1 = 1.0f;
    public float overlayCurvature1 = 0f; // 【新增】图1的边缘弯曲度
    private Bitmap movieBuffer1 = null;  // 【新增】图1的GIF缓冲
    
    public String overlayUri2 = "";
    public Bitmap overlayBmp2 = null;
    public float overlayX2 = 0, overlayY2 = 0, overlayScaleX2 = 1.0f, overlayScaleY2 = 1.0f;
    public float overlayCurvature2 = 0f; // 【新增】图2的边缘弯曲度
    private Bitmap movieBuffer2 = null;  // 【新增】图2的GIF缓冲
    
    public float overlayRotation1 = 0f; // 遮罩图1旋转角度
    public float overlayRotation2 = 0f; // 遮罩图2旋转角度

    // 强制全屏时隐藏的标志位，供外部（如SDLActivity）检测到游戏全屏状态时修改
    public boolean isFullscreenHideOverlay = false; 

        public int gridSize = 50; // 【优化2】自定义网格大小
    // ====== 【新增：网格与背景自定义变量】 ======
    public int gridLineColor = Color.WHITE; // 网格线颜色
    public int gridLineAlpha = 30;          // 网格线透明度
    public int gridBgColor = Color.argb(100, 255, 0, 0); // 编辑模式背景色 (默认半透红)
    // =========================================
    private VirtualButton draggedButton = null;
    public VirtualButton copiedButton = null; // 【优化1】用于复制的按键
    // 用于处理按键长按复制的定时任务
    private Runnable btnLongPressRunnable = null;
    
    // 记录上一次点击空白处的时间戳，用于判断双击粘贴
    private long lastEmptyTapTime = 0;
    private long downTime;
    private float downX, downY;

    private final RectF menuButtonRect = new RectF(20, 20, 250, 110);
    public VirtualButton currentlyEditingButton = null;
    public static DynamicGamepadView instance;

    public static final String[] TEXT_COLOR_NAMES = {"⬜", "⬛", "🟥", "🟨", "🟦", "🟩"};
    public static final int[] TEXT_COLOR_VALUES = {Color.WHITE, Color.BLACK, Color.RED, Color.YELLOW, Color.BLUE, Color.GREEN};

    public static final String[] SHAPE_NAMES = {"⭕", "🔲"};
    public static final int SHAPE_CIRCLE = 0;
    public static final int SHAPE_SQUARE = 1;

        public static class VirtualButton {
        public String id;
        public float cx, cy, radius;
        public int color, alpha, textColor, shape;
        public String keyMapStr = "";
        public List<Integer> keyCodes = new ArrayList<>();
        public boolean isPressed = false;
        public String customImageUri = ""; 
        public Bitmap skinBitmap = null;
        public boolean isDirectional = false; 
        public float hitboxRadius; // 触摸判定范围
        public boolean isLocked = false; // 【优化1】单个按键位置锁定
        // 【优化】全局静态线程池，复用线程防崩溃
        private static final java.util.concurrent.ExecutorService threadPool = java.util.concurrent.Executors.newCachedThreadPool();

        public volatile boolean isMacroPlaying = false; 
        public List<List<Integer>> macroSteps = new ArrayList<>(); 
                
        public long pressTimestamp = 0; // 【新增】记录精准按下时间戳，用于防吃键
         public int boundGamepadKeyCode = 0; // 【新增】记录该虚拟键绑定的物理手柄键值 (0为无)
        public boolean isTurbo = false; // 【新增】是否开启连发
        public int turboInterval = 40; 
        private volatile boolean turboRunning = false; // 【新增】连发线程控制锁
        
        // 【新增】每个按键的独立高阶属性
        public int textSizeFactor = 100;       // 字体大小百分比
        public boolean useCustomVib = false;   // 是否使用独立震动
        public int customVib = 30;             // 独立震动强度
        public boolean useCustomFeed = false;  // 是否使用独立形变反馈
        public int customFeedScale = 85;       // 独立形变比例
        

        
        // --- 新增：按下特效专属参数 (移到方法外面来) ---
        public String customPressedUri = ""; // 独立的按下图片
        public Bitmap pressedSkinBitmap = null;
        public int pressedEffectColor = 0; // 0代表使用默认，非0代表自定义高亮颜色
        public int pressedEffectAlpha = 150;

        // 【修复】把两个合并成了一个
        public VirtualButton(String id, float cx, float cy, float radius, int color, int alpha, int textColor, int shape, String keyMapStr, boolean isDir) {
            this.id = id; this.cx = cx; this.cy = cy;
            this.radius = radius; this.color = color;
            this.alpha = alpha; this.textColor = textColor;
            this.shape = shape; this.keyMapStr = keyMapStr;
            this.isDirectional = isDir;
                        this.hitboxRadius = radius * 1.5f; // 默认触摸范围比视觉大1.5倍
            parseKeyCodes();
            this.displayLines = this.id.split("\n"); // 【优化】初始化时缓存多行文本
        }
        
        public String[] displayLines; // 【优化】文本缓存变量
            
        

                public void parseKeyCodes() {
            keyCodes.clear();
            macroSteps.clear();
            if (keyMapStr == null || keyMapStr.isEmpty()) return;
            
            // 【修复 4】将宏的步骤分隔符从逗号改为斜杠 /
            String[] steps = keyMapStr.toUpperCase().split("/");
            for (String step : steps) {
                List<Integer> currentStepCodes = new ArrayList<>();
                String[] parts = step.split("\\+");
                for (String p : parts) {
                    int code = mapStringToKeyCode(p.trim());
                    if (code != KeyEvent.KEYCODE_UNKNOWN) {
                        currentStepCodes.add(code);
                    }
                }
                macroSteps.add(currentStepCodes);
            }
            if (!macroSteps.isEmpty()) keyCodes.addAll(macroSteps.get(0));
        }
        

                public void loadSkinFromUri(Context context) {
            // 加载常态皮肤
            if (customImageUri != null && !customImageUri.isEmpty()) {
                try {
                    InputStream is = context.getContentResolver().openInputStream(Uri.parse(customImageUri));
                    skinBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeStream(is), (int)(radius*2), (int)(radius*2), true);
                    if (is != null) is.close();
                } catch (Exception e) { skinBitmap = null; }
            } else { skinBitmap = null; }
            
            // 加载按下态皮肤
            if (customPressedUri != null && !customPressedUri.isEmpty()) {
                try {
                    InputStream is = context.getContentResolver().openInputStream(Uri.parse(customPressedUri));
                    pressedSkinBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeStream(is), (int)(radius*2), (int)(radius*2), true);
                    if (is != null) is.close();
                } catch (Exception e) { pressedSkinBitmap = null; }
            } else { pressedSkinBitmap = null; }
        }
        

         public void executeMacro() {
            if (macroSteps.size() <= 1 || isMacroPlaying) return;
            isMacroPlaying = true;
            threadPool.execute(() -> {        
                try {
                    for (List<Integer> stepCodes : macroSteps) {
                        for (int code : stepCodes) SDLActivity.onNativeKeyDown(code);
                        // 【优化】：将 60ms 缩短为 30ms（约两帧），让按压足够快且引擎能稳定识别
                        Thread.sleep(30); 
                        for (int code : stepCodes) SDLActivity.onNativeKeyUp(code);
                        // 【优化】：将 50ms 缩短为 20ms（约一帧），让招式衔接如丝般顺滑
                        Thread.sleep(20); 
                    }
                } catch (InterruptedException e) { }
                isMacroPlaying = false;
            });
        }
                        

        public void startTurbo() {
            if (turboRunning || macroSteps.isEmpty()) return;
            turboRunning = true;
            threadPool.execute(() -> {      
                while (turboRunning) {
                    try {
                        for (int code : macroSteps.get(0)) SDLActivity.onNativeKeyDown(code);
                        Thread.sleep(turboInterval); 
                        for (int code : macroSteps.get(0)) SDLActivity.onNativeKeyUp(code);
                        Thread.sleep(turboInterval);                       
                    } catch (InterruptedException e) { break; }
                }
            });
        }
                

        // 【新增】停止连发
        public void stopTurbo() {
            turboRunning = false;
        }

    } // <====== 兄弟，大括号必须放在这里！！！它必须把所有 VirtualButton 相关的变量和方法全部包在里面！
    
    private static int mapStringToKeyCode(String k) {            
        if (k.equals("UP")) return KeyEvent.KEYCODE_DPAD_UP;
        if (k.equals("DOWN")) return KeyEvent.KEYCODE_DPAD_DOWN;
        if (k.equals("LEFT")) return KeyEvent.KEYCODE_DPAD_LEFT;
        if (k.equals("RIGHT")) return KeyEvent.KEYCODE_DPAD_RIGHT;
        if (k.equals("ENTER") || k.equals("RETURN")) return KeyEvent.KEYCODE_ENTER;
        if (k.equals("SPACE")) return KeyEvent.KEYCODE_SPACE;
        if (k.equals("ESC") || k.equals("ESCAPE")) return KeyEvent.KEYCODE_ESCAPE;
        // 【新增】全面兼容 PC 游戏常用的控制键
        if (k.equals("CTRL")) return KeyEvent.KEYCODE_CTRL_LEFT;
        if (k.equals("SHIFT")) return KeyEvent.KEYCODE_SHIFT_LEFT;
        if (k.equals("ALT")) return KeyEvent.KEYCODE_ALT_LEFT;
        if (k.equals("TAB")) return KeyEvent.KEYCODE_TAB;
        if (k.length() == 1) {
            char c = k.charAt(0);
            if (c >= 'A' && c <= 'Z') return KeyEvent.KEYCODE_A + (c - 'A');
            if (c >= '0' && c <= '9') return KeyEvent.KEYCODE_0 + (c - '0');
        }
        return KeyEvent.keyCodeFromString("KEYCODE_" + k);
    }

    public DynamicGamepadView(Context context) {
        super(context);
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadLanguagePack(context); // 【新增】初始化时立即加载语言补丁
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        paintText.setTypeface(Typeface.DEFAULT_BOLD);
        loadConfig(currentSlot);
        // 在构造函数内添加：
        dashPaint.setStyle(Paint.Style.STROKE);
        dashPaint.setStrokeWidth(3f);
        dashPaint.setColor(Color.YELLOW);
        dashPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{10f, 10f}, 0));

    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        instance = this; 
    }

       @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (instance == this) instance = null;
        // 【新增安全措施】：View 销毁时，强制停止所有连发和宏线程，防止后台崩溃
        for (VirtualButton btn : buttons) {
            btn.stopTurbo();
            btn.isMacroPlaying = false;
        }
    }
    
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (pendingDefaultLayout && w > 0 && h > 0) {
            loadDefaultLayout();
            saveConfig();
            invalidate();
        } else if (pendingResolutionScale && w > 0 && h > 0) {
            applyDynamicResolutionScale(w, h);
            pendingResolutionScale = false;
            saveConfig();
            invalidate();
        } else if (w > 0 && h > 0 && loadedSavedWidth > 0 && loadedSavedHeight > 0) {
            // 【终极修复】防止全屏隐藏导航栏引起的微小像素波动被漏判，只要发生变动立刻平移适应
            if (w != loadedSavedWidth || h != loadedSavedHeight) {
                applyDynamicResolutionScale(w, h);
                saveConfig();
                invalidate();
            }
        }
    }

    // 【终极方案：加入开关控制的高度等比动态适配】
    private void applyDynamicResolutionScale(int currentW, int currentH) {
        if (loadedSavedWidth <= 0 || loadedSavedHeight <= 0) return;
        if (loadedSavedWidth == currentW && loadedSavedHeight == currentH) return;

        // 如果关闭了动态适配，仅执行最基础的坐标边界检查，绝对不动大小和位置 (原版逻辑)
        if (!isDynamicScaleEnabled) {
            loadedSavedWidth = currentW;
            loadedSavedHeight = currentH;
            return; 
        }

        // 【动态适配逻辑】：以高度比例作为主缩放系数，解决“本地太小”的问题
        float scaleFactor = (float) currentH / loadedSavedHeight;
        // X轴比例用于坐标映射，确保位置不偏离
        float scaleX = (float) currentW / loadedSavedWidth;

        for (VirtualButton btn : buttons) {
            btn.radius *= scaleFactor;
            btn.hitboxRadius *= scaleFactor;
            btn.cx *= scaleX;
            btn.cy *= scaleFactor;
        }

        joyRadius *= scaleFactor;
        joyHitboxRadius *= scaleFactor;
        joyBaseX *= scaleX;
        joyBaseY *= scaleFactor;
        joyKnobX = joyBaseX; 
        joyKnobY = joyBaseY;

        menuX *= scaleX;
        menuY *= scaleFactor;
        menuScale *= scaleFactor;

        // 缩放后强制重新渲染皮肤
        try {
            if(joySkinBaseUri != null && !joySkinBaseUri.isEmpty()) {
                java.io.InputStream is1 = getContext().getContentResolver().openInputStream(Uri.parse(joySkinBaseUri));
                if (joySkinBaseBitmap != null && !joySkinBaseBitmap.isRecycled()) joySkinBaseBitmap.recycle();
                joySkinBaseBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeStream(is1), (int)(joyRadius*2), (int)(joyRadius*2), true);
                if(is1!=null) is1.close();
            }
            if(joySkinKnobUri != null && !joySkinKnobUri.isEmpty()) {
                java.io.InputStream is2 = getContext().getContentResolver().openInputStream(Uri.parse(joySkinKnobUri));
                if (joySkinKnobBitmap != null && !joySkinKnobBitmap.isRecycled()) joySkinKnobBitmap.recycle();
                joySkinKnobBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeStream(is2), (int)(joyRadius*2), (int)(joyRadius*2), true);
                if(is2!=null) is2.close();
            }
            for (VirtualButton btn : buttons) {
                btn.loadSkinFromUri(getContext());
            }
        } catch (Exception e) {}
        
        loadedSavedWidth = currentW; 
        loadedSavedHeight = currentH;
    }

    // 【新增】将外部图片转存到APP私有目录的通用方法
    private String saveImageToLocal(Bitmap bitmap, String fileName) {
        try {
            File dir = new File(getContext().getFilesDir(), "ikemen_skins");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, fileName);
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();
            return android.net.Uri.fromFile(file).toString(); // 返回绝对路径的URI格式
        } catch (Exception e) {
            return "";
        }
    }

        // 自动生成视频里的“街机风格”图片并存入沙盒，返回URI
        // ================= 新增：超级按键风格生成矩阵 (12套预设) =================
    private void generateVideoArcadeStyle() {
        styleList.clear();

        // 1. 系统原生风格 (占位符，触发代码内置渐变渲染)
        GamepadStyle style1 = new GamepadStyle(L("01. 原生渐变引擎 (System Default)"));
        style1.joyBaseUri = ""; style1.joyKnobUri = ""; style1.btnNormalUri = ""; style1.btnPressedUri = "";
        style1.globalPressedColor = 0; 
        styleList.add(style1);

        int size = 400; // 统一生成高清 400x400 贴图
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // 核心数据矩阵：{风格名称, 底盘底色, 底盘边框, 摇杆底色, 摇杆边框, 按键底色, 按键边框, 按下特效高亮色}
String[][] themes = {
            {L("02. 经典街机 (Retro Arcade)"), "#0C141E", "#90CAF9", "#D32F2F", "#B71C1C", "#1A2B42", "#90CAF9", "#4CAF50"},
            {L("03. 赛博朋克霓虹 (Cyberpunk)"), "#110022", "#00FFFF", "#FF007F", "#FF00FF", "#110022", "#00FFFF", "#FF00FF"},
            {L("04. 暗物质黑武士 (Dark Matter)"), "#111111", "#333333", "#444444", "#111111", "#1A1A1A", "#333333", "#FFFFFF"},
            {L("05. 皇家奢华黑金 (Luxury Gold)"), "#1A1813", "#D4AF37", "#C5B358", "#8A793D", "#26241D", "#D4AF37", "#FFDF00"},
            {L("06. SFC 经典主机 (SNES Classic)"), "#D3D3D3", "#A9A9A9", "#4A4E69", "#2F3241", "#D3D3D3", "#A9A9A9", "#7B68EE"},
            {L("07. 生化毒液 (Toxic Acid)"), "#0F1A0F", "#39FF14", "#2E8B57", "#00FF00", "#142214", "#39FF14", "#ADFF2F"},
            {L("08. 猩红之月 (Blood Moon)"), "#1A0505", "#DC143C", "#8B0000", "#660000", "#240A0A", "#DC143C", "#FF0000"},
            {L("09. 深海幽蓝 (Ocean Depth)"), "#001F3F", "#00BFFF", "#0074D9", "#00008B", "#001A33", "#00BFFF", "#1E90FF"},
            {L("10. 极简拟物白 (White Glass)"), "#F5F5F5", "#E0E0E0", "#FFFFFF", "#CCCCCC", "#FAFAFA", "#E0E0E0", "#87CEEB"},
            {L("11. 紫晶矿石 (Royal Amethyst)"), "#20102B", "#9932CC", "#8A2BE2", "#4B0082", "#2A1538", "#9932CC", "#DDA0DD"},
            {L("12. 熔岩火山核心 (Magma Core)"), "#2B0F0E", "#FF4500", "#FF8C00", "#8B0000", "#361311", "#FF4500", "#FFFF00"}
        };

        // 批量自动绘制并存入沙盒
        for (String[] t : themes) {
            String name = t[0];
            int baseFill = Color.parseColor(t[1]), baseStroke = Color.parseColor(t[2]);
            int knobFill = Color.parseColor(t[3]), knobStroke = Color.parseColor(t[4]);
            int btnFill = Color.parseColor(t[5]), btnStroke = Color.parseColor(t[6]);
            int pressColor = Color.parseColor(t[7]);

            // 1. 动态画：摇杆底盘
            Bitmap baseBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas cBase = new Canvas(baseBmp);
            p.setStyle(Paint.Style.FILL); p.setColor(baseFill); cBase.drawCircle(size/2f, size/2f, size/2f - 8, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(12f); p.setColor(baseStroke); cBase.drawCircle(size/2f, size/2f, size/2f - 8, p);
            String baseUri = saveImageToLocal(baseBmp, "style_base_" + name.substring(0,2) + ".png");

            // 2. 动态画：摇杆帽
            Bitmap knobBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas cKnob = new Canvas(knobBmp);
            p.setStyle(Paint.Style.FILL); p.setColor(knobFill); cKnob.drawCircle(size/2f, size/2f, size/2.5f, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(8f); p.setColor(knobStroke); cKnob.drawCircle(size/2f, size/2f, size/2.5f, p);
            String knobUri = saveImageToLocal(knobBmp, "style_knob_" + name.substring(0,2) + ".png");

            // 3. 动态画：圆形动作按键
            Bitmap btnBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas cBtn = new Canvas(btnBmp);
            p.setStyle(Paint.Style.FILL); p.setColor(btnFill); cBtn.drawCircle(size/2f, size/2f, size/2f - 10, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(14f); p.setColor(btnStroke); cBtn.drawCircle(size/2f, size/2f, size/2f - 10, p);
            String btnUri = saveImageToLocal(btnBmp, "style_btn_ci_" + name.substring(0,2) + ".png");

            // 3.5 动态画：方形动作按键 【彻底解决方形匹配】
            Bitmap btnSqBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas cBtnSq = new Canvas(btnSqBmp);
            RectF sqRect = new RectF(10, 10, size - 10, size - 10);
            p.setStyle(Paint.Style.FILL); p.setColor(btnFill); cBtnSq.drawRoundRect(sqRect, size*0.2f, size*0.2f, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(14f); p.setColor(btnStroke); cBtnSq.drawRoundRect(sqRect, size*0.2f, size*0.2f, p);
            String btnSqUri = saveImageToLocal(btnSqBmp, "style_btn_sq_" + name.substring(0,2) + ".png");

            // 4. 组装并写入数据库
            GamepadStyle style = new GamepadStyle(name);
            style.joyBaseUri = baseUri;
            style.joyKnobUri = knobUri;
            style.btnNormalUri = btnUri;
            style.btnSquareUri = btnSqUri; // 【新增】绑定方形皮肤
            style.globalBtnColor = btnFill; // 修复恢复默认风格失效的Bug
            style.globalPressedColor = pressColor; 
            styleList.add(style);
        }
    }
    

    // ================= 新增：安全图片采样压缩算法 (防大图闪退OOM) =================
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    // ================= 新增：安全读取动图字节流 =================
    private byte[] readBytes(InputStream is) throws Exception {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        int nRead; byte[] data = new byte[16384];
        while ((nRead = is.read(data, 0, data.length)) != -1) { buffer.write(data, 0, nRead); }
        return buffer.toByteArray();
    }

    public void onImagePicked(String uriStr) {
        try {
            Uri uri = Uri.parse(uriStr);
            String mimeType = getContext().getContentResolver().getType(uri);
            boolean isVideo = (mimeType != null && mimeType.startsWith("video/")) 
                           || uriStr.toLowerCase().endsWith(".webm") 
                           || uriStr.toLowerCase().endsWith(".mp4");

            // ==== 处理视频遮罩 (Target 4/5) ====
            if ((imagePickerTarget == 4 || imagePickerTarget == 5) && isVideo) {
                android.util.Log.i("GamepadView", "检测到视频遮罩，启动异步硬件解码...");
                if (imagePickerTarget == 4) {
                    overlayUri1 = uriStr; overlayMovie1 = null; overlayBmp1 = null;
                    if (overlayMode < 1) overlayMode = 1;
                } else {
                    overlayUri2 = uriStr; overlayMovie2 = null; overlayBmp2 = null;
                    if (overlayMode < 2) overlayMode = 2;
                }
                
                ViewGroup parentLayout = (ViewGroup) this.getParent(); 
                if (parentLayout != null) {
                    TextureView videoView = new TextureView(getContext());
                    videoView.setOpaque(false); // 允许透明
                    android.widget.RelativeLayout.LayoutParams params = new android.widget.RelativeLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                    
                    videoView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                        private MediaPlayer mediaPlayer;
                        @Override
                        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                            try {
                                mediaPlayer = new MediaPlayer();
                                mediaPlayer.setDataSource(getContext(), uri);
                                mediaPlayer.setSurface(new Surface(surfaceTexture));
                                mediaPlayer.setLooping(true);
                                mediaPlayer.setVolume(0f, 0f);
                                // 【核心修复】必须使用异步加载，否则大视频会直接卡死主线程导致闪退！
                                mediaPlayer.setOnPreparedListener(mp -> mp.start());
                                mediaPlayer.prepareAsync(); 
                            } catch (Exception e) {}
                        }
                        @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int w, int h) {}
                        @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                            if (mediaPlayer != null) mediaPlayer.release(); return true;
                        }
                        @Override public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {}
                    });
                    // 【核心修复】将视频插在索引 0（所有UI的底层，但由于游戏画面处于底层Activity，所以刚刚好叠在中间）
                    parentLayout.addView(videoView, 0, params); 
                }
                imagePickerTarget = 0; saveConfig(); invalidate();
                Toast.makeText(getContext(), L("视频遮罩准备完毕！"), Toast.LENGTH_SHORT).show();
                return;
            }

            // ==== 处理动图遮罩 (Target 4/5) ====
            if (imagePickerTarget == 4 || imagePickerTarget == 5) {
                InputStream isGif = getContext().getContentResolver().openInputStream(uri);
                byte[] bytes = readBytes(isGif); isGif.close();
                
                File dir = new File(getContext().getFilesDir(), "ikemen_skins");
                if (!dir.exists()) dir.mkdirs();
                File file = new File(dir, "overlay_" + System.currentTimeMillis() + ".gif");
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(bytes); fos.flush(); fos.close();
                String localGifUri = android.net.Uri.fromFile(file).toString();
                
                android.graphics.Movie movie = android.graphics.Movie.decodeByteArray(bytes, 0, bytes.length);
                if (imagePickerTarget == 4) {
                    overlayUri1 = localGifUri;
                    if (movie != null && movie.duration() > 0) { overlayMovie1 = movie; overlayBmp1 = null; movieStart1 = 0; }
                    else { overlayMovie1 = null; overlayBmp1 = BitmapFactory.decodeByteArray(bytes, 0, bytes.length); }
                    if (overlayMode < 1) overlayMode = 1;
                } else {
                    overlayUri2 = localGifUri;
                    if (movie != null && movie.duration() > 0) { overlayMovie2 = movie; overlayBmp2 = null; movieStart2 = 0; }
                    else { overlayMovie2 = null; overlayBmp2 = BitmapFactory.decodeByteArray(bytes, 0, bytes.length); }
                    if (overlayMode < 2) overlayMode = 2;
                }
                imagePickerTarget = 0; saveConfig(); invalidate();
                Toast.makeText(getContext(), L("遮罩应用成功！"), Toast.LENGTH_SHORT).show();
                return; 
            }

            // ==== 处理普通按键/摇杆/背景图 (防OOM终极方案) ====
            Bitmap raw = null;
            if (isVideo) {
                // 【核心修复】如果给按键选了视频，自动抽第一帧当图片用，不闪退
                android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
                try {
                    retriever.setDataSource(getContext(), uri);
                    raw = retriever.getFrameAtTime(0);
                } catch (Exception e) {} finally { try { retriever.release(); } catch(Exception e){} }
            } else {
                // 【核心修复】大图降采样压缩，加载 4K 图绝对不闪退
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                InputStream isBounds = getContext().getContentResolver().openInputStream(uri);
                BitmapFactory.decodeStream(isBounds, null, options);
                if(isBounds != null) isBounds.close();
                
                options.inSampleSize = calculateInSampleSize(options, 800, 800); // 限制最大读取分辨率到 800x800
                options.inJustDecodeBounds = false;
                
                InputStream isFull = getContext().getContentResolver().openInputStream(uri);
                raw = BitmapFactory.decodeStream(isFull, null, options);
                if(isFull != null) isFull.close();
            }

            if (raw == null) {
                Toast.makeText(getContext(), L("❌ 无法解析该文件，请尝试其他格式"), Toast.LENGTH_SHORT).show();
                return;
            }
            
String localUriStr = saveImageToLocal(raw, "skin_" + System.currentTimeMillis() + ".png");
            final String finalUriStr = localUriStr.isEmpty() ? uriStr : localUriStr; 
            
            if (imagePickerTarget == 1) { 
                if (joySkinBaseBitmap != null && !joySkinBaseBitmap.isRecycled()) joySkinBaseBitmap.recycle();
                joySkinBaseUri = finalUriStr; 
                joySkinBaseBitmap = Bitmap.createScaledBitmap(raw, (int)(joyRadius*2), (int)(joyRadius*2), true);
                Toast.makeText(getContext(), L("摇杆外框皮肤应用成功！"), Toast.LENGTH_SHORT).show();
            } else if (imagePickerTarget == 2) { 
                if (joySkinKnobBitmap != null && !joySkinKnobBitmap.isRecycled()) joySkinKnobBitmap.recycle();
                joySkinKnobUri = finalUriStr; 
                joySkinKnobBitmap = Bitmap.createScaledBitmap(raw, (int)(joyRadius*2), (int)(joyRadius*2), true);
                Toast.makeText(getContext(), L("摇杆中心皮肤应用成功！"), Toast.LENGTH_SHORT).show();
            } else if (imagePickerTarget == 3 && currentlyEditingButton != null) { 
                if (currentlyEditingButton.skinBitmap != null && !currentlyEditingButton.skinBitmap.isRecycled()) currentlyEditingButton.skinBitmap.recycle();
                currentlyEditingButton.customImageUri = finalUriStr; 
                currentlyEditingButton.skinBitmap = Bitmap.createScaledBitmap(raw, (int)(currentlyEditingButton.radius*2), (int)(currentlyEditingButton.radius*2), true);
                Toast.makeText(getContext(), L("按键皮肤应用成功！"), Toast.LENGTH_SHORT).show();
            } else if (imagePickerTarget == 6 && currentlyEditingButton != null) { 
                if (currentlyEditingButton.pressedSkinBitmap != null && !currentlyEditingButton.pressedSkinBitmap.isRecycled()) currentlyEditingButton.pressedSkinBitmap.recycle();
                currentlyEditingButton.customPressedUri = finalUriStr; 
                currentlyEditingButton.pressedSkinBitmap = Bitmap.createScaledBitmap(raw, (int)(currentlyEditingButton.radius*2), (int)(currentlyEditingButton.radius*2), true);
                Toast.makeText(getContext(), L("按下状态皮肤应用成功！"), Toast.LENGTH_SHORT).show();
            } else if (imagePickerTarget == 7) { 
                if (dialogBgBitmap != null && !dialogBgBitmap.isRecycled()) dialogBgBitmap.recycle();
                dialogBgImageUri = finalUriStr; 
                dialogBgBitmap = Bitmap.createScaledBitmap(raw, 800, 800, true); 
                Toast.makeText(getContext(), L("弹窗背景图应用成功！"), Toast.LENGTH_SHORT).show();
            } else if (imagePickerTarget == 8) {
                if (menuSkinBitmap != null && !menuSkinBitmap.isRecycled()) menuSkinBitmap.recycle();
                menuSkinUri = finalUriStr;
                menuSkinBitmap = Bitmap.createScaledBitmap(raw, (int)menuWidth, (int)menuHeight, true);
                Toast.makeText(getContext(), L("菜单按钮图片应用成功！"), Toast.LENGTH_SHORT).show();
            } else if (imagePickerTarget == 9) {
                if (menuPressedSkinBitmap != null && !menuPressedSkinBitmap.isRecycled()) menuPressedSkinBitmap.recycle();
                menuPressedSkinUri = finalUriStr;
                menuPressedSkinBitmap = Bitmap.createScaledBitmap(raw, (int)menuWidth, (int)menuHeight, true);
                Toast.makeText(getContext(), L("菜单按钮按下状态皮肤应用成功！"), Toast.LENGTH_SHORT).show();
            }
            
            if (raw != null && !raw.isRecycled()) raw.recycle(); 
            saveConfig();
            invalidate();
        } catch (Exception e) {
             Toast.makeText(getContext(), L("文件处理失败: ") + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        imagePickerTarget = 0;
    }

                   
               
            
    // 【补上这里缺失的收尾代码 👆】

        // =====================================
    // 渲染引擎
    // =====================================
    
    // 【新增】专门用于弯曲“遮罩图片本身”的算法（绝对不是全屏滤镜）
        private float[] generateOverlayMesh(float width, float height, float curvature) {
        int MESH = 20;
        float[] verts = new float[(MESH + 1) * (MESH + 1) * 2];
        // 降低弯曲系数，防止拖动过度导致变形太生硬
        float k = (curvature / 100f) * 0.15f; 
        int index = 0;
        for (int y = 0; y <= MESH; y++) {
            float fy = y / (float) MESH;
            float ny = fy * 2 - 1; 
            for (int x = 0; x <= MESH; x++) {
                float fx = x / (float) MESH;
                float nx = fx * 2 - 1;
                
                // 【核心修复】：放弃鱼眼哈哈镜，改用 1D 水平透视弯曲
                float dx = nx * (1 + k * nx * nx) / (1 + k);
                float dy = ny; // 绝对不碰 Y 轴，保证图片上下边缘平直，人物比例正常
                
                verts[index++] = (dx + 1) / 2f * width;
                verts[index++] = (dy + 1) / 2f * height;
            }
        }
        return verts;
    }
    

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 绘制遮罩图 (支持动态 GIF、独立长宽拉伸、图片边缘弯曲)
        if ((!isFullscreenHideOverlay || isEditMode) && overlayMode > 0 && isOverlayVisible) {
            paintBtn.setAlpha(255);
            // ==== 渲染图 1 ====
            if (overlayMode >= 1) {
                float rawW = overlayMovie1 != null ? overlayMovie1.width() : (overlayBmp1 != null ? overlayBmp1.getWidth() : 0);
                float rawH = overlayMovie1 != null ? overlayMovie1.height() : (overlayBmp1 != null ? overlayBmp1.getHeight() : 0);
                if (rawW > 0 && rawH > 0) {
                    canvas.save();
                    float finalW = rawW * overlayScaleX1;
                    float finalH = rawH * overlayScaleY1;
                    float cx = overlayX1 + finalW / 2f;
                    float cy = overlayY1 + finalH / 2f;
                    
                    canvas.rotate(overlayRotation1, cx, cy);
                    canvas.scale(overlayMirror1 ? -1 : 1, 1, cx, cy);
                    
                    tempRect.set(overlayX1, overlayY1, overlayX1 + finalW, overlayY1 + finalH);
                    
                    if (overlayMovie1 != null) {
                        long now = android.os.SystemClock.uptimeMillis();
                        if (movieStart1 == 0) movieStart1 = now;
                        int dur = overlayMovie1.duration(); if (dur == 0) dur = 1000;
                        overlayMovie1.setTime((int)((now - movieStart1) % dur));
                        invalidate();
                    }

                    if (overlayCurvature1 > 0) { // 启用图片弯曲
                        float[] mesh = generateOverlayMesh(finalW, finalH, overlayCurvature1);
                        canvas.translate(overlayX1, overlayY1);
                        if (overlayMovie1 != null) {
                            if (movieBuffer1 == null || movieBuffer1.getWidth() != (int)rawW || movieBuffer1.getHeight() != (int)rawH) {
                                if (movieBuffer1 != null) movieBuffer1.recycle();
                                movieBuffer1 = Bitmap.createBitmap((int)rawW, (int)rawH, Bitmap.Config.ARGB_8888);
                            }
                            movieBuffer1.eraseColor(Color.TRANSPARENT);
                            Canvas mc = new Canvas(movieBuffer1);
                            overlayMovie1.draw(mc, 0, 0);
                            canvas.drawBitmapMesh(movieBuffer1, 20, 20, mesh, 0, null, 0, paintBtn);
                        } else if (overlayBmp1 != null) {
                            Bitmap scaled = Bitmap.createScaledBitmap(overlayBmp1, (int)finalW, (int)finalH, true);
                            canvas.drawBitmapMesh(scaled, 20, 20, mesh, 0, null, 0, paintBtn);
                            if (scaled != overlayBmp1) scaled.recycle();
                        }
                        canvas.translate(-overlayX1, -overlayY1);
                    } else { // 常规拉宽拉长
                        if (overlayMovie1 != null) {
                            canvas.translate(overlayX1, overlayY1);
                            canvas.scale(overlayScaleX1, overlayScaleY1);
                            overlayMovie1.draw(canvas, 0, 0);
                            canvas.scale(1/overlayScaleX1, 1/overlayScaleY1);
                            canvas.translate(-overlayX1, -overlayY1);
                        } else if (overlayBmp1 != null) {
                            canvas.drawBitmap(overlayBmp1, null, tempRect, paintBtn);
                        }
                    }
                    if (isEditMode) { paintBtn.setStyle(Paint.Style.STROKE); paintBtn.setColor(Color.GREEN); paintBtn.setStrokeWidth(5f); canvas.drawRect(tempRect, paintBtn); paintBtn.setStyle(Paint.Style.FILL); }
                    canvas.restore();
                }
            }
            // ==== 渲染图 2 ====
            if (overlayMode == 2) {
                float rawW = overlayMovie2 != null ? overlayMovie2.width() : (overlayBmp2 != null ? overlayBmp2.getWidth() : 0);
                float rawH = overlayMovie2 != null ? overlayMovie2.height() : (overlayBmp2 != null ? overlayBmp2.getHeight() : 0);
                if (rawW > 0 && rawH > 0) {
                    canvas.save();
                    float finalW = rawW * overlayScaleX2;
                    float finalH = rawH * overlayScaleY2;
                    float cx = overlayX2 + finalW / 2f;
                    float cy = overlayY2 + finalH / 2f;
                    
                    canvas.rotate(overlayRotation2, cx, cy);
                    canvas.scale(overlayMirror2 ? -1 : 1, 1, cx, cy);
                    
                    tempRect.set(overlayX2, overlayY2, overlayX2 + finalW, overlayY2 + finalH);
                    
                    if (overlayMovie2 != null) {
                        long now = android.os.SystemClock.uptimeMillis();
                        if (movieStart2 == 0) movieStart2 = now;
                        int dur = overlayMovie2.duration(); if (dur == 0) dur = 1000;
                        overlayMovie2.setTime((int)((now - movieStart2) % dur));
                        invalidate();
                    }

                    if (overlayCurvature2 > 0) {
                        float[] mesh = generateOverlayMesh(finalW, finalH, overlayCurvature2);
                        canvas.translate(overlayX2, overlayY2);
                        if (overlayMovie2 != null) {
                            if (movieBuffer2 == null || movieBuffer2.getWidth() != (int)rawW || movieBuffer2.getHeight() != (int)rawH) {
                                if (movieBuffer2 != null) movieBuffer2.recycle();
                                movieBuffer2 = Bitmap.createBitmap((int)rawW, (int)rawH, Bitmap.Config.ARGB_8888);
                            }
                            movieBuffer2.eraseColor(Color.TRANSPARENT);
                            Canvas mc = new Canvas(movieBuffer2);
                            overlayMovie2.draw(mc, 0, 0);
                            canvas.drawBitmapMesh(movieBuffer2, 20, 20, mesh, 0, null, 0, paintBtn);
                        } else if (overlayBmp2 != null) {
                            Bitmap scaled = Bitmap.createScaledBitmap(overlayBmp2, (int)finalW, (int)finalH, true);
                            canvas.drawBitmapMesh(scaled, 20, 20, mesh, 0, null, 0, paintBtn);
                            if (scaled != overlayBmp2) scaled.recycle();
                        }
                        canvas.translate(-overlayX2, -overlayY2);
                    } else {
                        if (overlayMovie2 != null) {
                            canvas.translate(overlayX2, overlayY2);
                            canvas.scale(overlayScaleX2, overlayScaleY2);
                            overlayMovie2.draw(canvas, 0, 0);
                            canvas.scale(1/overlayScaleX2, 1/overlayScaleY2);
                            canvas.translate(-overlayX2, -overlayY2);
                        } else if (overlayBmp2 != null) {
                            canvas.drawBitmap(overlayBmp2, null, tempRect, paintBtn);
                        }
                    }
                    if (isEditMode) { paintBtn.setStyle(Paint.Style.STROKE); paintBtn.setColor(Color.BLUE); paintBtn.setStrokeWidth(5f); canvas.drawRect(tempRect, paintBtn); paintBtn.setStyle(Paint.Style.FILL); }
                    canvas.restore();
                }
            }
        }
        
        // 动态计算菜单按键的位置和缩放
        float mw = menuWidth * menuScale;
        float mh = menuHeight * menuScale;
        menuButtonRect.set(menuX, menuY, menuX + mw, menuY + mh);

        boolean isMenuPreviewPress = isMenuDown;
        int currentMenuAlpha = menuAlpha;
        int drawMenuColor = (isMenuPreviewPress && menuPressedEffectColor != 0) ? menuPressedEffectColor : menuColor;
        int drawMenuAlpha = (isMenuPreviewPress && menuPressedEffectColor != 0) ? menuPressedEffectAlpha : currentMenuAlpha;

        Bitmap currentMenuSkin = menuSkinBitmap;
        if (isMenuPreviewPress && menuPressedSkinBitmap != null) {
            currentMenuSkin = menuPressedSkinBitmap;
        }

        if (currentMenuSkin != null && !currentMenuSkin.isRecycled()) {
            paintBtn.setAlpha(currentMenuAlpha);
            canvas.drawBitmap(currentMenuSkin, null, menuButtonRect, paintBtn);
            
            if (isMenuPreviewPress && menuPressedSkinBitmap == null && menuPressedEffectColor != 0) {
                paintBtn.setColor(menuPressedEffectColor);
                paintBtn.setAlpha(menuPressedEffectAlpha);
                if (menuShape == SHAPE_CIRCLE) canvas.drawCircle(menuButtonRect.centerX(), menuButtonRect.centerY(), Math.min(mw, mh) / 2f, paintBtn);
                else canvas.drawRoundRect(menuButtonRect, 20 * menuScale, 20 * menuScale, paintBtn);
            }
        } else {
            int baseColor = Color.argb(drawMenuAlpha, Color.red(drawMenuColor), Color.green(drawMenuColor), Color.blue(drawMenuColor));
            int darkColor = Color.argb(drawMenuAlpha, Math.max(0, Color.red(drawMenuColor)-80), Math.max(0, Color.green(drawMenuColor)-80), Math.max(0, Color.blue(drawMenuColor)-80));
            
            float drawRadius = Math.max(mw, mh) / 2f;
            RadialGradient gradient = new RadialGradient(menuButtonRect.centerX() - drawRadius * 0.3f, menuButtonRect.centerY() - drawRadius * 0.3f, drawRadius * 1.3f, baseColor, darkColor, Shader.TileMode.CLAMP);
            paintMenu.setShader(gradient);
            
            if (isMenuPreviewPress) {
                paintMenu.setShadowLayer(25.0f, 0.0f, 0.0f, drawMenuColor);
            } else if (currentMenuAlpha > 80) {
                paintMenu.setShadowLayer(8f * menuScale, 0, 4f * menuScale, Color.argb(currentMenuAlpha/2, 0, 0, 0));
            } else { paintMenu.clearShadowLayer(); }

            if (menuShape == SHAPE_CIRCLE) {
                canvas.drawCircle(menuButtonRect.centerX(), menuButtonRect.centerY(), Math.min(mw, mh) / 2f, paintMenu);
            } else {
                canvas.drawRoundRect(menuButtonRect, 20 * menuScale, 20 * menuScale, paintMenu);
            }
            paintMenu.clearShadowLayer(); paintMenu.setShader(null);
        }
        
        paintText.setColor(menuTextColor);
        paintText.setAlpha(currentMenuAlpha);
        paintText.setTextSize(38f * menuScale * (menuTextSizeFactor / 100f));
        // 【修复：文本阴影透明度动态跟随菜单整体透明度，解决幽灵文字残留问题】
        int menuBaseShadowColor = (menuTextColor == Color.BLACK) ? Color.WHITE : Color.BLACK;
        paintText.setShadowLayer(3f, 1f, 1f, Color.argb(currentMenuAlpha, Color.red(menuBaseShadowColor), Color.green(menuBaseShadowColor), Color.blue(menuBaseShadowColor)));
                paintText.setTextAlign(Paint.Align.CENTER);
        float textOffset = (paintText.descent() - paintText.ascent()) / 2 - paintText.descent();
        // 【修改】如果自定义名字不为空，才绘制文字
        if (menuButtonName != null && !menuButtonName.trim().isEmpty()) {
            canvas.drawText(menuButtonName, menuButtonRect.centerX(), menuButtonRect.centerY() + textOffset, paintText);
        }
        paintText.clearShadowLayer();


        if (isEditMode) {
            paintBtn.setStyle(Paint.Style.STROKE); 
            paintBtn.setColor(isMenuLocked ? Color.RED : Color.GREEN); // 红色代表已锁定，绿色代表可拖动
            paintBtn.setStrokeWidth(5f); 
            if (menuShape == SHAPE_CIRCLE) {
                canvas.drawCircle(menuButtonRect.centerX(), menuButtonRect.centerY(), Math.min(mw, mh) / 2f, paintBtn);
            } else {
                canvas.drawRect(menuButtonRect, paintBtn); 
            }
            paintBtn.setStyle(Paint.Style.FILL);
        }
        
        
        if (isEditMode) {
            canvas.drawColor(gridBgColor); // 【修改：使用自定义背景色】
            if (isGridSnapMode) {
                paintBtn.setColor(gridLineColor); // 【修改：使用自定义网格线颜色】
                paintBtn.setAlpha(gridLineAlpha); // 【修改：使用自定义网格线透明度】
                paintBtn.setStrokeWidth(1f);
                for (int i = 0; i < getWidth(); i += gridSize) canvas.drawLine(i, 0, i, getHeight(), paintBtn);
                for (int i = 0; i < getHeight(); i += gridSize) canvas.drawLine(0, i, getWidth(), i, paintBtn);
            }
                        paintText.setTextSize(Math.max(20f, getHeight() * 0.05f)); // 【修复】动态字体大小
            paintText.setShadowLayer(5f, 2f, 2f, Color.BLACK);
            canvas.drawText(L("【编辑模式】拖动调整，轻触设置"), getWidth() / 2f, getHeight() * 0.12f, paintText); // 【修复】动态高度位置          
        }

              // 核心按键绘制逻辑
        for (VirtualButton btn : buttons) {
            if (joystickMode > 0 && btn.isDirectional) continue;

                        int idleAlpha = (int)(btn.alpha * 0.6f); 
            // 【修复】：去掉 Math.max 限制，让编辑模式完全反映真实透明度
            // 【关键修复：彻底解决隐藏后按下闪烁的问题】按下状态绝对不能写死 255，必须跟随用户设定的最大透明度 btn.alpha
            int currentAlpha = isEditMode ? btn.alpha : (btn.isPressed ? btn.alpha : idleAlpha);
            
            // 【超级新增】：允许在编辑模式下，对当前正编辑的按键进行“按下态”强行预览
            boolean isPreviewPress = btn.isPressed && (!isEditMode || btn == currentlyEditingButton);
            
            // 【优化】结合全局与独立的缩放或放大反馈
            float currentScale = 1.0f;
            if (isPreviewPress) {
                if (btn.useCustomFeed) {
                    currentScale = btn.customFeedScale / 100f;
                } else if (isGlobalFeedbackEnabled) {
                    currentScale = globalFeedbackScaleInt / 100f;
                }
            }
            float drawRadius = btn.radius * currentScale;
            tempRect.set(btn.cx - drawRadius, btn.cy - drawRadius, btn.cx + drawRadius, btn.cy + drawRadius);
            
            // ==== 新增：按键皮肤与按下特效渲染逻辑 ====
            Bitmap currentSkin = btn.skinBitmap;
            if (isPreviewPress && btn.pressedSkinBitmap != null) {
                currentSkin = btn.pressedSkinBitmap;
            }

            if (currentSkin != null) {
                paintBtn.setAlpha(currentAlpha);
                canvas.drawBitmap(currentSkin, null, tempRect, paintBtn);
                
                // 自定义皮肤的纯色按下泛光补充 (只有当玩家设置了特效颜色时才画)
                if (isPreviewPress && btn.pressedSkinBitmap == null && btn.pressedEffectColor != 0) {
                    paintBtn.setColor(btn.pressedEffectColor);
                    paintBtn.setAlpha(btn.pressedEffectAlpha);
                    if (btn.shape == SHAPE_CIRCLE) canvas.drawCircle(btn.cx, btn.cy, btn.radius, paintBtn);
                    else canvas.drawRoundRect(tempRect, btn.radius*0.3f, btn.radius*0.3f, paintBtn);
                }
            } else {
            
                // 原版渐变渲染
                int drawColor = (isPreviewPress && btn.pressedEffectColor != 0) ? btn.pressedEffectColor : btn.color;
                int drawAlpha = (isPreviewPress && btn.pressedEffectColor != 0) ? btn.pressedEffectAlpha : currentAlpha;
                
                int baseColor = Color.argb(drawAlpha, Color.red(drawColor), Color.green(drawColor), Color.blue(drawColor));
                int darkColor = Color.argb(drawAlpha, Math.max(0, Color.red(drawColor)-80), Math.max(0, Color.green(drawColor)-80), Math.max(0, Color.blue(drawColor)-80));
                
                // 【修改1：渐变圆心和半径】
                RadialGradient gradient = new RadialGradient(btn.cx - drawRadius * 0.3f, btn.cy - drawRadius * 0.3f, drawRadius * 1.3f, baseColor, darkColor, Shader.TileMode.CLAMP);
                paintBtn.setShader(gradient);
                
                if (isPreviewPress) {
                    paintBtn.setShadowLayer(25.0f, 0.0f, 0.0f, drawColor);
                } else if (currentAlpha > 80) {
                    paintBtn.setShadowLayer(10.0f, 0.0f, 5.0f, Color.argb(currentAlpha/2, 0, 0, 0));
                } else { paintBtn.clearShadowLayer(); }          
                
                // 【修改2和3：画圆和画圆角的半径】
                if (btn.shape == SHAPE_CIRCLE) canvas.drawCircle(btn.cx, btn.cy, drawRadius, paintBtn);
                else canvas.drawRoundRect(tempRect, drawRadius*0.3f, drawRadius*0.3f, paintBtn);
                
                paintBtn.clearShadowLayer(); paintBtn.setShader(null);                
            }
        

                      // 绘制文字
            paintText.setColor(btn.textColor);
            paintText.setAlpha(currentAlpha);
            // 如果有多行，适当缩小一点字体，防止文字超出按键边缘
            boolean isMultiLine = btn.id.contains("\n");
            // 【修改】在此基础上增加玩家定制的字体大小百分比系数 (textSizeFactor)
            paintText.setTextSize(drawRadius * (isMultiLine ? 0.45f : 0.6f) * (btn.textSizeFactor / 100f));           
            paintText.setTextAlign(Paint.Align.CENTER); 
            // 【修复：动态解析 ARGB，让阴影的透明度与按键本身的透明度 (currentAlpha) 强行同步】
            int btnBaseShadowColor = (btn.textColor == Color.BLACK) ? Color.WHITE : Color.BLACK;
            paintText.setShadowLayer(3f, 1f, 1f, Color.argb(currentAlpha, Color.red(btnBaseShadowColor), Color.green(btnBaseShadowColor), Color.blue(btnBaseShadowColor)));
            
            // 【优化】单行直接绘制，多行拆分并重新计算 Y 轴居中偏移
            textOffset = (paintText.descent() - paintText.ascent()) / 2 - paintText.descent();
            
            if (!isMultiLine) {
                canvas.drawText(btn.id, btn.cx, btn.cy + textOffset, paintText);
            } else {
                String[] lines = btn.displayLines; // 【优化】直接读取缓存，杜绝内存垃圾
                float lineHeight = paintText.descent() - paintText.ascent();
                // 计算多行文本的总偏移，使其整体垂直居中
                float startY = btn.cy + textOffset - (lines.length - 1) * lineHeight / 2f;
                for (int i = 0; i < lines.length; i++) {
                    canvas.drawText(lines[i], btn.cx, startY + (i * lineHeight), paintText);
                }
            }
            paintText.clearShadowLayer();
            
            // 编辑模式的外框与判定范围
            if (isEditMode) {
                paintBtn.setStyle(Paint.Style.STROKE); paintBtn.setStrokeWidth(4f); paintBtn.setColor(Color.WHITE); paintBtn.setAlpha(255);
                if (btn.shape == SHAPE_CIRCLE) canvas.drawCircle(btn.cx, btn.cy, btn.radius + 6, paintBtn);
                else canvas.drawRoundRect(btn.cx - btn.radius - 6, btn.cy - btn.radius - 6, btn.cx + btn.radius + 6, btn.cy + btn.radius + 6, btn.radius*0.3f, btn.radius*0.3f, paintBtn);
                
                // 【修正】直接使用类头部的 dashPaint，不要再 new Paint()
                if (btn.shape == SHAPE_CIRCLE) canvas.drawCircle(btn.cx, btn.cy, btn.hitboxRadius, dashPaint);
                else canvas.drawRoundRect(btn.cx - btn.hitboxRadius, btn.cy - btn.hitboxRadius, btn.cx + btn.hitboxRadius, btn.cy + btn.hitboxRadius, btn.radius*0.3f, btn.radius*0.3f, dashPaint);
                paintBtn.setStyle(Paint.Style.FILL);
            }
        } 
    }
   

        @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (joystickMode > 0) drawJoystick(canvas);
    }

    private void drawJoystick(Canvas canvas) {
        // 【修复】：让摇杆真实反映透明度
        int currentAlpha = joyAlpha;
                            
        
        // ========= 1. 绘制底盘 =========
        if (joystickMode == JOYSTICK_MODE_STYLE && joySkinBaseBitmap != null) {
            paintBtn.setAlpha(currentAlpha);
            tempRect.set(joyBaseX - joyRadius, joyBaseY - joyRadius, joyBaseX + joyRadius, joyBaseY + joyRadius);
            canvas.drawBitmap(joySkinBaseBitmap, null, tempRect, paintBtn);
            
            float dxA = joyKnobX - joyBaseX, dyA = joyKnobY - joyBaseY;
            float distA = (float) Math.hypot(dxA, dyA);
            float activeAngle = -1;
            if (distA > joyRadius * 0.2f && !isEditMode) { 
                activeAngle = (float) Math.toDegrees(Math.atan2(dyA, dxA));
                if (activeAngle < 0) activeAngle += 360;
            }
            for (int i = 0; i < 8; i++) {
                float targetAngle = i * 45;
                boolean isActive = false;
                if (activeAngle != -1) {
                    float diff = Math.abs(activeAngle - targetAngle);
                    if (diff > 180) diff = 360 - diff;
                    if (diff <= 22.5f) isActive = true;
                }
                paintBtn.setColor(isActive ? Color.WHITE : Color.argb(40, 255, 255, 255));
                if (isActive) paintBtn.setShadowLayer(12f, 0, 0, Color.WHITE);
                else paintBtn.clearShadowLayer();
                canvas.save();
                canvas.rotate(targetAngle, joyBaseX, joyBaseY);
                android.graphics.Path path = new android.graphics.Path();
                path.moveTo(joyBaseX + joyRadius * 0.8f, joyBaseY); 
                path.lineTo(joyBaseX + joyRadius * 0.65f, joyBaseY - 12); 
                path.lineTo(joyBaseX + joyRadius * 0.65f, joyBaseY + 12); 
                path.close();
                canvas.drawPath(path, paintBtn);
                canvas.restore();
            }
            paintBtn.clearShadowLayer();
            
        } else if (joystickMode == 1) { 
            paintBtn.setColor(joyColor); paintBtn.setAlpha((int)(currentAlpha * 0.3f));
            canvas.drawCircle(joyBaseX, joyBaseY, joyRadius, paintBtn);
        } else if (joystickMode == 2 || (joystickMode == JOYSTICK_MODE_STYLE && joySkinBaseBitmap == null)) { 
            RadialGradient baseGrad = new RadialGradient(joyBaseX, joyBaseY, joyRadius, Color.parseColor("#333333"), Color.parseColor("#080808"), Shader.TileMode.CLAMP);
            paintBtn.setShader(baseGrad); paintBtn.setAlpha((int)(currentAlpha * 0.9f));
            canvas.drawCircle(joyBaseX, joyBaseY, joyRadius, paintBtn);
            paintBtn.setShader(null);
        } else if (joystickMode == 3) { 
            paintBtn.setColor(Color.DKGRAY); paintBtn.setAlpha((int)(currentAlpha * 0.5f));
            paintText.setColor(Color.WHITE); paintText.setAlpha(currentAlpha); paintText.setTextSize(joyRadius * 0.35f);
            paintText.setTextAlign(Paint.Align.CENTER);
            float textOffset = (paintText.descent() - paintText.ascent()) / 2 - paintText.descent();
            String[] dirs = {"➡", "↘", "⬇", "↙", "⬅", "↖", "⬆", "↗"}; 
            for (int i = 0; i < 8; i++) {
                float angle = (float) Math.toRadians(i * 45);
                float bx = joyBaseX + (float) Math.cos(angle) * joyRadius * 0.8f;
                float by = joyBaseY + (float) Math.sin(angle) * joyRadius * 0.8f;
                canvas.drawCircle(bx, by, joyRadius * 0.28f, paintBtn);
                canvas.drawText(dirs[i], bx, by + textOffset, paintText);
            }
        }

        // ========= 1.5 绘制8向基准线 =========
        if (joystickMode == 1 || joystickMode == 2 || (joystickMode == JOYSTICK_MODE_STYLE && joySkinBaseBitmap == null)) {
            paintBtn.setColor(Color.WHITE); paintBtn.setStrokeWidth(4f); paintBtn.setAlpha((int)(joyAlpha * 0.4f));
            for (int i = 0; i < 8; i++) {
                float angle = (float) Math.toRadians(i * 45);
                float startX = joyBaseX + (float) Math.cos(angle) * (joyRadius * 0.6f);
                float startY = joyBaseY + (float) Math.sin(angle) * (joyRadius * 0.6f);
                float endX = joyBaseX + (float) Math.cos(angle) * joyRadius;
                float endY = joyBaseY + (float) Math.sin(angle) * joyRadius;
                canvas.drawLine(startX, startY, endX, endY, paintBtn);
            }
        }

        // ========= 2. 绘制摇杆动态拉伸白线 =========
        float dx = joyKnobX - joyBaseX;
        float dy = joyKnobY - joyBaseY;
        float dist = (float) Math.hypot(dx, dy);

        if (dist > joyRadius * 0.2f && !isEditMode) {
            if (joystickMode == 1 || joystickMode == 2 || (joystickMode == JOYSTICK_MODE_STYLE && joySkinKnobBitmap == null)) {
                paintBtn.setColor(Color.WHITE);
                paintBtn.setStrokeWidth(8f);
                paintBtn.setAlpha(200);
                float edgeX = joyBaseX + (dx / dist) * joyRadius;
                float edgeY = joyBaseY + (dy / dist) * joyRadius;
                paintBtn.setShadowLayer(15f, 0, 0, joyColor); 
                canvas.drawLine(joyBaseX, joyBaseY, edgeX, edgeY, paintBtn);
                canvas.drawCircle(edgeX, edgeY, joyRadius * 0.12f, paintBtn);
                paintBtn.clearShadowLayer();
            }
        }

        // ========= 3. 绘制摇杆帽 =========
        if (joystickMode != 3) { 
            if (joystickMode == JOYSTICK_MODE_STYLE && joySkinKnobBitmap != null) {
                paintBtn.setAlpha(currentAlpha);
                float knobRad = joyRadius * 0.5f; 
                tempRect.set(joyKnobX - knobRad, joyKnobY - knobRad, joyKnobX + knobRad, joyKnobY + knobRad);
                canvas.drawBitmap(joySkinKnobBitmap, null, tempRect, paintBtn);
            } else if (joystickMode == 1) {
                paintBtn.setColor(joyColor); paintBtn.setAlpha(currentAlpha);
                canvas.drawCircle(joyKnobX, joyKnobY, joyRadius * 0.35f, paintBtn);
        } else if (joystickMode == 2 || (joystickMode == JOYSTICK_MODE_STYLE && joySkinKnobBitmap == null)) { 
                    } else if (joystickMode == 2 || (joystickMode == JOYSTICK_MODE_STYLE && joySkinKnobBitmap == null)) { 
            paintBtn.setColor(Color.parseColor("#AAAAAA")); paintBtn.setStrokeWidth(25f);
            paintBtn.setStyle(Paint.Style.STROKE); paintBtn.setAlpha(currentAlpha);
            canvas.drawLine(joyBaseX, joyBaseY, joyKnobX, joyKnobY, paintBtn); 
            paintBtn.setStyle(Paint.Style.FILL); // 👈 删掉多余的，只留这一句即可
            
            
            int darkColor = Color.rgb(Math.max(0, Color.red(joyColor)-100), Math.max(0, Color.green(joyColor)-100), Math.max(0, Color.blue(joyColor)-100));
                RadialGradient ballGrad = new RadialGradient(joyKnobX - 15, joyKnobY - 15, joyRadius * 0.5f, joyColor, darkColor, Shader.TileMode.CLAMP);
                paintBtn.setShader(ballGrad); paintBtn.setShadowLayer(15f, 0, 10f, Color.argb(currentAlpha, 0,0,0));
                canvas.drawCircle(joyKnobX, joyKnobY, joyRadius * 0.45f, paintBtn);
                paintBtn.clearShadowLayer(); paintBtn.setShader(null);
            }
        } else if (joystickMode == 3 && joyPointerId != -1) {
            paintBtn.setColor(joyColor); paintBtn.setAlpha((int)(currentAlpha * 0.6f));
            canvas.drawCircle(joyKnobX, joyKnobY, joyRadius * 0.25f, paintBtn);
        }

        // ========= 4. 编辑模式提示 =========
        if (isEditMode) {
            paintBtn.setStyle(Paint.Style.STROKE); paintBtn.setStrokeWidth(5f); 
            // 【修改】如果被锁定了外框就变红，没锁定就是白色
            paintBtn.setColor(isJoyLocked ? Color.RED : Color.WHITE); 
            paintBtn.setAlpha(255);
            canvas.drawCircle(joyBaseX, joyBaseY, joyRadius + 10, paintBtn); 
            canvas.drawCircle(joyBaseX, joyBaseY, joyHitboxRadius, dashPaint);
            paintText.setColor(Color.WHITE); 
            paintText.setTextSize(Math.max(16f, joyRadius * 0.25f)); // 【修复】跟随摇杆比例缩放
            paintText.setShadowLayer(3f,0,0,Color.BLACK);
            canvas.drawText(L("摇杆控制区"), joyBaseX, joyBaseY - joyHitboxRadius - 10, paintText);            
            paintBtn.setStyle(Paint.Style.FILL); paintText.clearShadowLayer();
        }
    }
                        
                
    
        // 原有的无参调用默认走全局强度
    private void triggerVibrate() {
        triggerVibrate(vibrationIntensity);
    }
    
    // 【终极修复】：缓存 Vibrator 实例。绝对不能在触控高频事件中重复获取 SystemService，否则必导致UI线程卡死、断触！
    private android.os.Vibrator cachedVibrator = null;

    // 【新增】支持接收独立强度参数的底层引擎
    private void triggerVibrate(int intensity) {
        if (!isVibrationOn || intensity <= 0) return;
        try {
            if (cachedVibrator == null) {
                cachedVibrator = (android.os.Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (cachedVibrator != null && cachedVibrator.hasVibrator()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    cachedVibrator.vibrate(android.os.VibrationEffect.createOneShot(intensity, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    cachedVibrator.vibrate(intensity);
                }
            }
        } catch (Exception e) {}
    }
    
    
    public boolean onPhysicalGamepadKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        boolean isDown = event.getAction() == KeyEvent.ACTION_DOWN;

// 1. 拦截：手柄绑定模式 (核心：生成或绑定虚拟按键)
        if (isGamepadBindingMode && isDown) {
            isGamepadBindingMode = false;
            if (currentBindingDialog != null) currentBindingDialog.dismiss();

            if (currentBindingTargetButton != null) {
                // 模式A：绑定到现有的预设屏幕按键
                currentBindingTargetButton.boundGamepadKeyCode = keyCode;
                Toast.makeText(getContext(), L("绑定成功！[") + currentBindingTargetButton.id + L("] 已绑定手柄键值: ") + keyCode, Toast.LENGTH_SHORT).show();
            } else {
                // 模式B：按下手柄，直接在屏幕中央无中生有出一个新按键
                float scale = Math.max(0.5f, getHeight() / 1080f);
                VirtualButton newBtn = new VirtualButton(L("待绑定"), getWidth() / 2f, getHeight() / 2f, 90 * scale, Color.parseColor("#9C27B0"), 200, Color.WHITE, SHAPE_CIRCLE, "", false);
                newBtn.boundGamepadKeyCode = keyCode;
                buttons.add(newBtn);
                isEditMode = true; // 强制进入编辑模式，方便玩家待会儿直接拖走它
                showButtonSettingsDialog(newBtn); // 瞬间弹出设置面板
                Toast.makeText(getContext(), L("已捕捉手柄键值: ") + keyCode + L("，请配置功能并拖动位置"), Toast.LENGTH_LONG).show();
            }
            saveConfig();
            return true;
        }

        // 2. 拦截：测试仪模式
        if (testFeedbackText != null) {
            if (isDown) {
                testFeedbackText.setText(L("当前按下键值: ") + keyCode + L("\n尝试触发手柄震动..."));
                triggerHardwareGamepadVibration(event.getDevice());
            } else { testFeedbackText.setText(L("手柄已连接，等待按键...")); }
            return true;
        }

        // 3. 正常游戏模式 (直接扫描屏幕上的所有按键)
        boolean handled = false;
        for (VirtualButton btn : buttons) {
            // 优先检查玩家自定义绑定的物理键
            boolean match = (btn.boundGamepadKeyCode == keyCode);
            
            // 兜底逻辑：如果这个虚拟按键还没被自定义绑定过，我们给标准手柄一个默认支持
            if (!match && btn.boundGamepadKeyCode == 0) {
                if (keyCode == KeyEvent.KEYCODE_BUTTON_A && btn.id.equals("A")) match = true;
                else if (keyCode == KeyEvent.KEYCODE_BUTTON_B && btn.id.equals("B")) match = true;
                else if (keyCode == KeyEvent.KEYCODE_BUTTON_X && btn.id.equals("X")) match = true;
                else if (keyCode == KeyEvent.KEYCODE_BUTTON_Y && btn.id.equals("Y")) match = true;
                else if ((keyCode == KeyEvent.KEYCODE_BUTTON_R1 || keyCode == KeyEvent.KEYCODE_BUTTON_C) && btn.id.equals("C")) match = true;
                else if ((keyCode == KeyEvent.KEYCODE_BUTTON_L1 || keyCode == KeyEvent.KEYCODE_BUTTON_Z) && btn.id.equals("Z")) match = true;
                else if (keyCode == KeyEvent.KEYCODE_BUTTON_START && btn.id.equals("START")) match = true;
                else if ((keyCode == KeyEvent.KEYCODE_BUTTON_SELECT || keyCode == KeyEvent.KEYCODE_BUTTON_THUMBL) && btn.id.equals("ESC")) match = true;
                else if (keyCode == KeyEvent.KEYCODE_DPAD_UP && btn.id.equals("UP")) match = true;
                else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && btn.id.equals("DOWN")) match = true;
                else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && btn.id.equals("LEFT")) match = true;
                else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && btn.id.equals("RIGHT")) match = true;
            }

            if (match) {
                handled = true;
                if (isDown && !btn.isPressed) {
                    btn.isPressed = true;
                    if (gamepadUIMode == 1) invalidate();
                    if (gamepadUIMode == 2 && isLayoutVisible()) hideLayoutTemporarily();
                    if (isGamepadVibrationOn) triggerHardwareGamepadVibration(event.getDevice());
                    for (int c : btn.keyCodes) SDLActivity.onNativeKeyDown(c);
                } else if (!isDown && btn.isPressed) {
                    btn.isPressed = false;
                    if (gamepadUIMode == 1) invalidate();
                    for (int c : btn.keyCodes) SDLActivity.onNativeKeyUp(c);
                }
            }
        }
        return handled;
    }

        public boolean onPhysicalGamepadMotionEvent(MotionEvent event) {
        if (testFeedbackText != null) return true; // 测试模式屏蔽摇杆
        
        float x = event.getAxisValue(MotionEvent.AXIS_X);
        float y = event.getAxisValue(MotionEvent.AXIS_Y);
        
        boolean up = y < -gamepadDeadzone;
        boolean down = y > gamepadDeadzone;
        boolean left = x < -gamepadDeadzone;
        boolean right = x > gamepadDeadzone;

        // 1. 映射物理摇杆到虚拟十字键状态 (发送 SDL 信号 + 方向键发光)
        triggerDirection("UP", up);
        triggerDirection("DOWN", down);
        triggerDirection("LEFT", left);
        triggerDirection("RIGHT", right);
        
        // 2. 【新增】同步屏幕上的摇杆球视觉位置
        if (joystickMode > 0) {
            float dist = (float) Math.hypot(x, y);
            if (dist > 1.0f) dist = 1.0f; // 限制最大半径比例
            if (dist > gamepadDeadzone) {
                float maxDist = joyRadius * 0.75f;
                joyKnobX = joyBaseX + x * maxDist;
                joyKnobY = joyBaseY + y * maxDist;
            } else {
                joyKnobX = joyBaseX;
                joyKnobY = joyBaseY;
            }
        }
        
        // 3. UI 联动与刷新
        if (gamepadUIMode == 1) invalidate(); // 屏幕按键/摇杆球同步刷新
        if (gamepadUIMode == 2 && (up || down || left || right) && isLayoutVisible()) {
            hideLayoutTemporarily();
        }
        return true;
    }


    private void triggerHardwareGamepadVibration(android.view.InputDevice device) {
        if (device != null && device.getVibrator().hasVibrator()) {
            try { device.getVibrator().vibrate(100); } catch(Exception e){}
        }
    }
    private boolean isLayoutVisible() { return getVisibility() == View.VISIBLE; }
    private void hideLayoutTemporarily() {
        if (getContext() instanceof SDLActivity) ((SDLActivity)getContext()).cancelAutoHide();
        setVisibility(View.INVISIBLE); // 隐藏面板本身
    }

            
    // =====================================
    // 触控引擎
    // =====================================
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex(); 

        // 如果是编辑模式，直接进入拖拽逻辑
        if (isEditMode) { 
            handleEditTouch(event); 
            return true; 
        }

        // 👇 替换为：菜单按钮长短按智能识别引擎 👇
        if (action == MotionEvent.ACTION_DOWN && menuButtonRect.contains(event.getX(actionIndex), event.getY(actionIndex))) {

            isMenuDown = true;
            menuDownX = event.getX(actionIndex);
            menuDownY = event.getY(actionIndex);
            postDelayed(menuLongPressRunnable, 500); // 500毫秒触发长按
            invalidate(); // 触发按下特效渲染
            return true; 
        }

        if (action == MotionEvent.ACTION_MOVE && isMenuDown) {
            // 如果手指滑动距离超过20像素，取消长按判定，防止拖拽误触
            if (Math.hypot(event.getX(actionIndex) - menuDownX, event.getY(actionIndex) - menuDownY) > 20) {
                isMenuDown = false;
                removeCallbacks(menuLongPressRunnable);
                invalidate(); // 取消按下特效
            }
        }

        if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) && isMenuDown) {
            isMenuDown = false;
            removeCallbacks(menuLongPressRunnable); // 抬手时如果在500ms内，取消长按倒计时
            invalidate(); // 取消按下特效
            // 触发短按，打开主菜单
            showMainMenu(); 
            return true;
        }
    

        // --- 摇杆逻辑完美增强版 ---
        if (joystickMode > 0) {
            boolean joyTouched = false;
            
            // 【新增】：精准处理抬起事件，防止手指离开后 actionIndex 残留导致摇杆被幽灵锁定
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
                if (event.getPointerId(actionIndex) == joyPointerId) {
                    joyPointerId = -1;
                    joyKnobX = joyBaseX; joyKnobY = joyBaseY;
                    triggerDirection("UP", false); triggerDirection("DOWN", false); triggerDirection("LEFT", false); triggerDirection("RIGHT", false);
                }
            }

            for (int i = 0; i < event.getPointerCount(); i++) {
                float px = event.getX(i), py = event.getY(i);
                int pointerId = event.getPointerId(i);
                
                // 跳过刚刚抬起的这根手指，彻底避免将其误判为正在触摸
                if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) && i == actionIndex) continue;

                if (px < getWidth() / 2f) {
                    // 【核心修复】：加上 `&& i == actionIndex`，只对“刚刚按下的那根特定手指”做摇杆捕捉判定，绝不干扰其他手指
                    if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) && i == actionIndex) {
                        if (Math.hypot(px - joyBaseX, py - joyBaseY) < joyHitboxRadius) {
                            joyPointerId = pointerId;
                        }
                    }
                    
                    if (pointerId == joyPointerId) {
                        joyTouched = true;
                        float dx = px - joyBaseX, dy = py - joyBaseY;
                        float dist = (float) Math.hypot(dx, dy);
                        
                        // 限制摇杆帽只能在底座半径的 75% 范围内活动
                        float maxDist = joyRadius * 0.75f; 
                        if (dist > maxDist) { 
                            joyKnobX = joyBaseX + (dx / dist) * maxDist; 
                            joyKnobY = joyBaseY + (dy / dist) * maxDist; 
                        } else { 
                            joyKnobX = px; joyKnobY = py; 
                        }
                        
                        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
                        if (angle < 0) angle += 360;
                        boolean up = angle > 200 && angle < 340, down = angle > 20 && angle < 160;
                        boolean left = angle > 110 && angle < 250, right = angle < 70 || angle > 290;
                        // 触发阈值同步缩小
                        if (dist < joyRadius * 0.2f) up = down = left = right = false;
                        triggerDirection("UP", up); triggerDirection("DOWN", down); triggerDirection("LEFT", left); triggerDirection("RIGHT", right);
                    }
                }
            }
            
            // 安全兜底清空
            if (!joyTouched && joyPointerId != -1) {
                joyPointerId = -1; joyKnobX = joyBaseX; joyKnobY = joyBaseY;
                triggerDirection("UP", false); triggerDirection("DOWN", false); triggerDirection("LEFT", false); triggerDirection("RIGHT", false);
            }
        }        

        // --- 【核心修复】全局防卡键扫描 ---
                for (VirtualButton btn : buttons) {
            if (joystickMode > 0 && btn.isDirectional) continue;
            boolean isTouchedNow = false;
            for (int i = 0; i < event.getPointerCount(); i++) {
                if (event.getPointerId(i) == joyPointerId) continue;
                if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) && i == actionIndex) continue;

                float px = event.getX(i), py = event.getY(i);
                // 【修改】使用 hitboxRadius 替代 radius 进行碰撞检测，扩大触摸范围
                    if (btn.shape == SHAPE_CIRCLE) {
                    float dx = px - btn.cx;
                    float dy = py - btn.cy;
                    if ((dx * dx + dy * dy) < (btn.hitboxRadius * btn.hitboxRadius)) isTouchedNow = true;
                } else {
                
                    if (px > btn.cx - btn.hitboxRadius && px < btn.cx + btn.hitboxRadius && py > btn.cy - btn.hitboxRadius && py < btn.cy + btn.hitboxRadius) isTouchedNow = true;
                }
            }
            
                       // 【修改】区分触发：连发、宏、普通单点
                        if (!btn.isPressed && isTouchedNow) {
                btn.isPressed = true;
                btn.pressTimestamp = System.currentTimeMillis(); // 瞬间打卡记录时间
                // 【应用独立震动】
                triggerVibrate(btn.useCustomVib ? btn.customVib : vibrationIntensity);
            
                
                if (btn.isTurbo) {
                    btn.startTurbo(); // 触发独立连发引擎
                } else if (btn.macroSteps.size() > 1) {
                    btn.executeMacro(); // 触发一键连招
                } else if (!btn.macroSteps.isEmpty()) {
                    for (int code : btn.macroSteps.get(0)) SDLActivity.onNativeKeyDown(code); // 瞬间触发同按组合键
                }
            } else if (btn.isPressed && !isTouchedNow) {
                btn.isPressed = false;
                
                if (btn.isTurbo) {
                    btn.stopTurbo(); // 松开即停止连发
                } else if (btn.macroSteps.size() <= 1 && !btn.macroSteps.isEmpty()) {
                    long pressDuration = System.currentTimeMillis() - btn.pressTimestamp;
                    final List<Integer> codes = btn.macroSteps.get(0);
                    
                    if (pressDuration < 50) {
                        // 如果按压时间不足50毫秒(不到3帧)，利用View的线程延迟松开操作，保证底层引擎一定能抓到动作
                        postDelayed(() -> {
                            for (int code : codes) SDLActivity.onNativeKeyUp(code);
                        }, 50 - pressDuration);
                    } else {
                        for (int code : codes) SDLActivity.onNativeKeyUp(code);
                    }
                }
            }
        }    
        invalidate();   // <--- 【补上这行】刷新屏幕
        return true;    // <--- 【补上这行】结束触控事件
    }                   // <--- 【补上这个大括号】把 onTouchEvent 关上

    // 【只保留这一个完整的就行了！】
        // 替换原有的 triggerDirection 方法
    private void triggerDirection(String dirId, boolean pressed) {
        for (VirtualButton btn : buttons) {
            if (btn.id.equals(dirId) && btn.isDirectional) {
                if (pressed && !btn.isPressed) { 
                    btn.isPressed = true; 
                    triggerVibrate(); // 【新增】摇杆拨动触发震动
                    for (int c : btn.keyCodes) SDLActivity.onNativeKeyDown(c); 
                } 
                else if (!pressed && btn.isPressed) { 
                    btn.isPressed = false; 
                    for (int c : btn.keyCodes) SDLActivity.onNativeKeyUp(c); 
                }
                break;
            }
        }
    }
    
        

        private void handleEditTouch(MotionEvent event) {
        int action = event.getActionMasked();
        float x = event.getX(0), y = event.getY(0);
        
                float targetX = isGridSnapMode ? Math.round(x / gridSize) * gridSize : x;
        float targetY = isGridSnapMode ? Math.round(y / gridSize) * gridSize : y;

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                downTime = System.currentTimeMillis(); downX = x; downY = y;
                isDraggingJoy = false; draggedButton = null; isDraggingMenu = false;
                
                if (menuButtonRect.contains(x, y)) {
                    isDraggingMenu = !isMenuLocked; 
                    isMenuDown = true; 
                    postDelayed(menuLongPressRunnable, 500); 
                    invalidate(); 
                                } else if (joystickMode > 0 && Math.hypot(x - joyBaseX, y - joyBaseY) < joyRadius) { 
                    // 只有在未锁定时，才允许标记为“正在拖拽摇杆”
                    isDraggingJoy = !isJoyLocked; 
                } else {
                    for (int i = buttons.size() - 1; i >= 0; i--) {
                        if (Math.hypot(x - buttons.get(i).cx, y - buttons.get(i).cy) < buttons.get(i).radius * 1.3f) {
                            draggedButton = buttons.get(i); break;
                        }
                    }
                    // 【优化1】长按复制与双击粘贴引擎
                    if (draggedButton != null) {
                        btnLongPressRunnable = () -> {
                            copiedButton = draggedButton;
                            triggerVibrate(50);
                            Toast.makeText(getContext(), L("已复制按键 [") + copiedButton.id + L("]，双击空白处粘贴"), Toast.LENGTH_SHORT).show();
                        };
                        postDelayed(btnLongPressRunnable, 600);
                    } else {
                        if (copiedButton != null && System.currentTimeMillis() - lastEmptyTapTime < 300) {
                            VirtualButton newBtn = new VirtualButton(copiedButton.id + L("_副本"), targetX, targetY, copiedButton.radius, copiedButton.color, copiedButton.alpha, copiedButton.textColor, copiedButton.shape, copiedButton.keyMapStr, copiedButton.isDirectional);
                            newBtn.hitboxRadius = copiedButton.hitboxRadius; newBtn.isTurbo = copiedButton.isTurbo; newBtn.turboInterval = copiedButton.turboInterval;
                            newBtn.customImageUri = copiedButton.customImageUri; newBtn.customPressedUri = copiedButton.customPressedUri;
                            newBtn.pressedEffectColor = copiedButton.pressedEffectColor; newBtn.pressedEffectAlpha = copiedButton.pressedEffectAlpha;
                            newBtn.textSizeFactor = copiedButton.textSizeFactor; newBtn.useCustomVib = copiedButton.useCustomVib; newBtn.customVib = copiedButton.customVib;
                            newBtn.useCustomFeed = copiedButton.useCustomFeed; newBtn.customFeedScale = copiedButton.customFeedScale;
                            newBtn.loadSkinFromUri(getContext());
                            buttons.add(newBtn); saveConfig(); invalidate(); triggerVibrate(30);
                        }
                        lastEmptyTapTime = System.currentTimeMillis();
                    }
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (isDraggingMenu) { 
                    if (isMenuDown && Math.hypot(x - downX, y - downY) > 20) {
                        isMenuDown = false; removeCallbacks(menuLongPressRunnable); invalidate();
                    }
                    menuX = targetX - (menuButtonRect.width() / 2f); menuY = targetY - (menuButtonRect.height() / 2f); invalidate(); 
                } else if (isDraggingJoy) { 
                    joyBaseX = targetX; joyBaseY = targetY; joyKnobX = targetX; joyKnobY = targetY; invalidate(); 
                } else if (draggedButton != null && !draggedButton.isLocked) { 
                    if (Math.hypot(x - downX, y - downY) > 20 && btnLongPressRunnable != null) {
                        removeCallbacks(btnLongPressRunnable); btnLongPressRunnable = null;
                    }
                    draggedButton.cx = targetX; draggedButton.cy = targetY; invalidate(); 
                }
                break;

            case MotionEvent.ACTION_UP:
                if (isMenuDown) { 
                    isMenuDown = false; removeCallbacks(menuLongPressRunnable); invalidate(); 
                    if (System.currentTimeMillis() - downTime < 250 && Math.hypot(x - downX, y - downY) < 20) { showMainMenu(); }
                } else if (System.currentTimeMillis() - downTime < 250 && Math.hypot(x - downX, y - downY) < 20) {
                    if (btnLongPressRunnable != null) { removeCallbacks(btnLongPressRunnable); btnLongPressRunnable = null; }
                    // 【修复】直接通过坐标判断是否点中了摇杆区域，无视锁定状态，强行打开设置
                    if (joystickMode > 0 && Math.hypot(downX - joyBaseX, downY - joyBaseY) < joyRadius) {
                        DynamicGamepadView.this.showJoystickSettingsDialog();
                    } else if (draggedButton != null) {
                        DynamicGamepadView.this.showButtonSettingsDialog(draggedButton);
                    }
                }
                if (btnLongPressRunnable != null) { removeCallbacks(btnLongPressRunnable); btnLongPressRunnable = null; }
                isDraggingJoy = false; draggedButton = null; isDraggingMenu = false;
                break;
        }
    }
    
                        
                    
                
            
    // =====================================
    // 存档、导入导出与序列化逻辑 (包含二次确认)
    // =====================================
        // 【修改】调用系统自带的文件选择器导出 JSON
    private void exportLayoutToFile() {
        android.app.Activity activity = (android.app.Activity) getContext();
        FileActionFragment fragment = new FileActionFragment();
        android.os.Bundle args = new android.os.Bundle();
        args.putInt("action_type", 1); // 1 代表导出
        args.putString("export_data", prefs.getString(KEY_LAYOUT_PREFIX + currentSlot, "[]"));
        fragment.setArguments(args);
        activity.getFragmentManager().beginTransaction().add(fragment, "file_action").commitAllowingStateLoss();
    }

    // 【修改】调用系统自带的文件选择器导入 JSON
    private void importLayoutFromFile() {
        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(L("⚠️ 覆盖警告"))
            .setMessage(L("即将从手机存储中选择布局文件。\n导入成功后将【永久覆盖】你当前的按键布局，确定继续吗？"))
            .setPositiveButton(L("选文件并覆盖"), (d, w) -> {
                android.app.Activity activity = (android.app.Activity) getContext();
                FileActionFragment fragment = new FileActionFragment();
                android.os.Bundle args = new android.os.Bundle();
                args.putInt("action_type", 2); // 2 代表导入
                fragment.setArguments(args);
                activity.getFragmentManager().beginTransaction().add(fragment, "file_action").commitAllowingStateLoss();
            })
            .setNegativeButton(L("取消"), null).show();
    }
    
    private void exportAllData() {
        android.app.Activity activity = (android.app.Activity) getContext();
        FileActionFragment fragment = new FileActionFragment();
        android.os.Bundle args = new android.os.Bundle();
        args.putInt("action_type", 1); 
        
        try {
            JSONObject root = new JSONObject();
            // 保存之前的布局
            root.put("layout", new JSONArray(prefs.getString(KEY_LAYOUT_PREFIX + currentSlot, "[]")));
            // 保存风格列表
            JSONArray styleArr = new JSONArray();
            for(GamepadStyle s : styleList) styleArr.put(s.toJson());
            root.put("styles", styleArr);
            args.putString("export_data", root.toString());
        } catch(Exception e) {}
        
        fragment.setArguments(args);
        activity.getFragmentManager().beginTransaction().add(fragment, "file_action").commitAllowingStateLoss();
    }

    private void showMenuSettingsDialog() {
        imagePickerTarget = 0;
        final android.app.Dialog dialog = new android.app.Dialog(getContext(), android.R.style.Theme_DeviceDefault_Dialog);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        
        LinearLayout rootLayout = new LinearLayout(getContext());
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackground(getCustomDialogBackground());

        TextView dragHandle = new TextView(getContext());
        dragHandle.setText(L("✋ 拖拽此处 | ⚙️ 菜单外观高阶设定"));
        android.graphics.drawable.GradientDrawable titleBg = new android.graphics.drawable.GradientDrawable();
        titleBg.setColor(Color.argb(50, 0, 0, 0));
        titleBg.setCornerRadii(new float[]{35f, 35f, 35f, 35f, 0f, 0f, 0f, 0f});
        dragHandle.setBackground(titleBg);
        dragHandle.setTextColor(dialogTextColor);
        dragHandle.setPadding(40, 30, 40, 30);
        dragHandle.setTextSize(dialogTextSize + 2f);
        dragHandle.setTypeface(null, Typeface.BOLD);
        rootLayout.addView(dragHandle);

        ScrollView scroll = new ScrollView(getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int trueScreenH = Math.min(DynamicGamepadView.this.getWidth(), DynamicGamepadView.this.getHeight());
                int maxHeight = (int) (trueScreenH * dialogHeightRatio) - 120;
                if (maxHeight < 200) { maxHeight = 200; }
                int customHeightSpec = View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST);
                super.onMeasure(widthMeasureSpec, customHeightSpec);
            }
        };

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 50);

        layout.addView(createTitle(L("0. 菜单自定义文字 (留空则不显示文字):")));
        final EditText nameInput = createEditText(L("例如: ⚙ 高级设置"), menuButtonName);
        layout.addView(nameInput);


        layout.addView(createTitle(L("1. 菜单位置锁定:")));
        final Button lockBtn = new Button(getContext());

        lockBtn.setText(isMenuLocked ? L("🔒 拖拽位置锁定：已开启") : L("🔓 拖拽位置锁定：已关闭"));
        lockBtn.setTextColor(Color.WHITE);
        lockBtn.setBackgroundColor(isMenuLocked ? Color.parseColor("#D32F2F") : Color.parseColor("#4CAF50"));
        lockBtn.setOnClickListener(v -> {
            isMenuLocked = !isMenuLocked;
            lockBtn.setText(isMenuLocked ? L("🔒 拖拽位置锁定：已开启") : L("🔓 拖拽位置锁定：已关闭"));
            lockBtn.setBackgroundColor(isMenuLocked ? Color.parseColor("#D32F2F") : Color.parseColor("#4CAF50"));
            invalidate(); // 立刻预览红/绿框
        });
        layout.addView(lockBtn);

        layout.addView(createTitle(L("2. 按键样式与文字颜色:")));
        final Spinner textColorSpinner = new Spinner(getContext());
        ArrayAdapter<String> textAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, TEXT_COLOR_NAMES);
        textColorSpinner.setAdapter(textAdapter);
        for (int i=0; i<TEXT_COLOR_VALUES.length; i++) { if (menuTextColor == TEXT_COLOR_VALUES[i]) { textColorSpinner.setSelection(i); break; } }
        layout.addView(textColorSpinner);

        final Spinner shapeSpinner = new Spinner(getContext());
        ArrayAdapter<String> shapeAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, SHAPE_NAMES);
        shapeSpinner.setAdapter(shapeAdapter); shapeSpinner.setSelection(menuShape); layout.addView(shapeSpinner);

        layout.addView(createTitle(L("3. 菜单背景颜色 (纯色引擎):")));
        final EditText hexInput = createEditText(L("颜色代码如: #333333"), String.format("#%06X", (0xFFFFFF & menuColor))); 
        layout.addView(hexInput);

        final View colorPreview = new View(getContext());
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 60);
        previewParams.setMargins(0, 10, 0, 30); colorPreview.setLayoutParams(previewParams); 
        final android.graphics.drawable.GradientDrawable previewBg = new android.graphics.drawable.GradientDrawable();
        previewBg.setCornerRadius(20f); previewBg.setColor(menuColor); colorPreview.setBackground(previewBg);
        layout.addView(colorPreview);

        final int[] rgb = {Color.red(menuColor), Color.green(menuColor), Color.blue(menuColor)};
        final SeekBar redBar = createColorBar(layout, L("🔴 红色分量 (R)"), rgb[0]); 
        final SeekBar greenBar = createColorBar(layout, L("🟢 绿色分量 (G)"), rgb[1]); 
        final SeekBar blueBar = createColorBar(layout, L("🔵 蓝色分量 (B)"), rgb[2]);

        SeekBar.OnSeekBarChangeListener colorUpdater = new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                rgb[0] = redBar.getProgress(); rgb[1] = greenBar.getProgress(); rgb[2] = blueBar.getProgress(); 
                int newColor = Color.rgb(rgb[0], rgb[1], rgb[2]);
                previewBg.setColor(newColor); menuColor = newColor; invalidate();                 
                if(fromUser) hexInput.setText(String.format("#%06X", (0xFFFFFF & newColor)));
            }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        };
        redBar.setOnSeekBarChangeListener(colorUpdater); greenBar.setOnSeekBarChangeListener(colorUpdater); blueBar.setOnSeekBarChangeListener(colorUpdater);

        layout.addView(createTitle(L("4. 尺寸与隐藏参数:")));
        final SeekBar wBar = createColorBar(layout, L("基础宽度"), (int)menuWidth); wBar.setMax(500);
        final SeekBar hBar = createColorBar(layout, L("基础高度"), (int)menuHeight); hBar.setMax(300);
        final SeekBar sBar = createColorBar(layout, L("整体缩放 (%)"), (int)(menuScale * 100)); sBar.setMax(300);
        final SeekBar aBar = createColorBar(layout, L("可见透明度 (0-255)"), menuAlpha); 
        final SeekBar txtSizeBar = createColorBar(layout, L("🅰️ 字体大小百分比"), menuTextSizeFactor); txtSizeBar.setMax(300);

        SeekBar.OnSeekBarChangeListener sizeUpdater = new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) {
                    if(s==wBar) menuWidth=Math.max(50, p); else if(s==hBar) menuHeight=Math.max(50, p); 
                    else if(s==sBar) menuScale=Math.max(10, p)/100f; else if(s==aBar) menuAlpha=p; 
                    else if(s==txtSizeBar) menuTextSizeFactor=Math.max(10, p);
                    invalidate();
                }
            }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        };
        wBar.setOnSeekBarChangeListener(sizeUpdater); hBar.setOnSeekBarChangeListener(sizeUpdater); sBar.setOnSeekBarChangeListener(sizeUpdater); aBar.setOnSeekBarChangeListener(sizeUpdater); txtSizeBar.setOnSeekBarChangeListener(sizeUpdater);

        layout.addView(createTitle(L("5. 自定义常态图片皮肤:")));
        LinearLayout skinLayout = new LinearLayout(getContext()); skinLayout.setOrientation(LinearLayout.HORIZONTAL);
        Button pickImg = new Button(getContext()); pickImg.setText(L("🖼️ 选择皮肤")); pickImg.setTextColor(Color.WHITE); pickImg.setBackgroundColor(Color.parseColor("#4CAF50"));
        pickImg.setOnClickListener(v -> {
            imagePickerTarget = 8; android.app.Activity activity = (android.app.Activity) getContext(); FileActionFragment fragment = new FileActionFragment();
            android.os.Bundle args = new android.os.Bundle(); args.putInt("action_type", 0); fragment.setArguments(args); activity.getFragmentManager().beginTransaction().add(fragment, "file_action").commitAllowingStateLoss();
        }); skinLayout.addView(pickImg);
        
        Button clearImg = new Button(getContext()); clearImg.setText(L("❌ 移除皮肤")); clearImg.setTextColor(Color.WHITE); clearImg.setBackgroundColor(Color.parseColor("#F44336"));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); cp.setMargins(20, 0, 0, 0); clearImg.setLayoutParams(cp);
        clearImg.setOnClickListener(v -> { menuSkinUri = ""; menuSkinBitmap = null; Toast.makeText(getContext(), L("已恢复默认材质"), Toast.LENGTH_SHORT).show(); invalidate(); });
        skinLayout.addView(clearImg); layout.addView(skinLayout);
        // ================= 按下特效引擎 =================
        layout.addView(createTitle(L("6. 按下状态特效 (独立颜色与皮肤):")));
        final EditText hexInputP = createEditText(L("颜色如: #4CAF50 (填 #000000 为无)"), String.format("#%06X", (0xFFFFFF & menuPressedEffectColor))); layout.addView(hexInputP);
        final View colorPreviewP = new View(getContext()); colorPreviewP.setLayoutParams(previewParams); 
        final android.graphics.drawable.GradientDrawable previewBgP = new android.graphics.drawable.GradientDrawable();
        previewBgP.setCornerRadius(20f); previewBgP.setColor(menuPressedEffectColor == 0 ? Color.parseColor("#333333") : menuPressedEffectColor); colorPreviewP.setBackground(previewBgP); layout.addView(colorPreviewP);

        final SeekBar alphaBarP = createColorBar(layout, L("按下特效不透明度"), menuPressedEffectAlpha); 
        final int[] rgbP = {Color.red(menuPressedEffectColor), Color.green(menuPressedEffectColor), Color.blue(menuPressedEffectColor)};
        final SeekBar rBarP = createColorBar(layout, L("🔴 按下红 (R)"), rgbP[0]); final SeekBar gBarP = createColorBar(layout, L("🟢 按下绿 (G)"), rgbP[1]); final SeekBar bBarP = createColorBar(layout, L("🔵 按下蓝 (B)"), rgbP[2]);

        SeekBar.OnSeekBarChangeListener colorUpdaterP = new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                rgbP[0] = rBarP.getProgress(); rgbP[1] = gBarP.getProgress(); rgbP[2] = bBarP.getProgress(); 
                int newColor = Color.rgb(rgbP[0], rgbP[1], rgbP[2]);
                menuPressedEffectColor = newColor; invalidate();
                previewBgP.setColor(newColor == 0 ? Color.parseColor("#333333") : newColor); 
                if(fromUser) hexInputP.setText(String.format("#%06X", (0xFFFFFF & newColor)));
            }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        };
        rBarP.setOnSeekBarChangeListener(colorUpdaterP); gBarP.setOnSeekBarChangeListener(colorUpdaterP); bBarP.setOnSeekBarChangeListener(colorUpdaterP);
        alphaBarP.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) { if (fromUser) { menuPressedEffectAlpha = p; invalidate(); } }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        });

        LinearLayout skinLayoutP = new LinearLayout(getContext()); skinLayoutP.setOrientation(LinearLayout.HORIZONTAL);
        Button pickImgP = new Button(getContext()); pickImgP.setText(L("🖼️ 按下皮肤")); pickImgP.setTextColor(Color.WHITE); pickImgP.setBackgroundColor(Color.parseColor("#4CAF50"));
        pickImgP.setOnClickListener(v -> {
            imagePickerTarget = 9; android.app.Activity activity = (android.app.Activity) getContext(); FileActionFragment fragment = new FileActionFragment();
            android.os.Bundle args = new android.os.Bundle(); args.putInt("action_type", 0); fragment.setArguments(args); activity.getFragmentManager().beginTransaction().add(fragment, "file_action").commitAllowingStateLoss();
        }); skinLayoutP.addView(pickImgP);
        Button clearImgP = new Button(getContext()); clearImgP.setText(L("❌ 移除按下皮肤")); clearImgP.setTextColor(Color.WHITE); clearImgP.setBackgroundColor(Color.parseColor("#F44336")); clearImgP.setLayoutParams(cp);
        clearImgP.setOnClickListener(v -> { menuPressedSkinUri = ""; menuPressedSkinBitmap = null; Toast.makeText(getContext(), L("已恢复无"), Toast.LENGTH_SHORT).show(); invalidate(); });
        skinLayoutP.addView(clearImgP); layout.addView(skinLayoutP);

        // 底部按钮区域
        LinearLayout bottomButtons = new LinearLayout(getContext()); bottomButtons.setOrientation(LinearLayout.HORIZONTAL); bottomButtons.setPadding(0, 50, 0, 0);
        Button deleteBtn = new Button(getContext()); deleteBtn.setText(L("🔄 恢复默认")); deleteBtn.setTextColor(Color.WHITE); deleteBtn.setBackgroundColor(Color.parseColor("#D32F2F"));
        deleteBtn.setOnClickListener(v -> { 
            isMenuLocked = false; menuColor = Color.parseColor("#333333"); menuTextColor = Color.WHITE; menuTextSizeFactor = 100; menuShape = 1;
            menuWidth = 230; menuHeight = 90; menuScale = 1.0f; menuAlpha = 220;
            menuSkinUri = ""; menuSkinBitmap = null; menuPressedSkinUri = ""; menuPressedSkinBitmap = null;
            menuPressedEffectColor = 0; menuPressedEffectAlpha = 150;
            saveConfig(); invalidate(); dialog.dismiss(); showMenuSettingsDialog(); // 重新加载界面
        }); bottomButtons.addView(deleteBtn);
        
        Button saveBtn = new Button(getContext()); saveBtn.setText(L("💾 保存修改并退出")); saveBtn.setTextColor(Color.WHITE); saveBtn.setBackgroundColor(Color.parseColor("#1976D2"));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); saveParams.setMargins(20, 0, 0, 0); saveBtn.setLayoutParams(saveParams);
        
        saveBtn.setOnClickListener(v -> {
            menuButtonName = nameInput.getText().toString(); // 获取新名字
            menuTextColor = TEXT_COLOR_VALUES[textColorSpinner.getSelectedItemPosition()];
            menuShape = shapeSpinner.getSelectedItemPosition(); 
            saveConfig(); invalidate(); dialog.dismiss();
        });
        bottomButtons.addView(saveBtn); layout.addView(bottomButtons);

        scroll.addView(layout);
        rootLayout.addView(scroll);
        dialog.setContentView(rootLayout);
        setupMovableDialog(dialog, dragHandle); 
        dialog.show();
    }
            
    

    // =====================================
    // UI 面板渲染与系统弹窗
    // =====================================
        private void showMainMenu() {
        final android.app.Dialog dialog = new android.app.Dialog(getContext(), android.R.style.Theme_DeviceDefault_Dialog);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        
        LinearLayout rootLayout = new LinearLayout(getContext());
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackground(getCustomDialogBackground()); // 统一应用自定义背景

        TextView dragHandle = new TextView(getContext());
        dragHandle.setText(L("✋ 拖拽此处 | ⚙️ 游戏面板全局设置"));
                        android.graphics.drawable.GradientDrawable titleBg = new android.graphics.drawable.GradientDrawable();
        titleBg.setColor(Color.argb(50, 0, 0, 0)); // 【修复1】改用半透明遮罩，完美融合下方自定义背景色
        titleBg.setCornerRadii(new float[]{35f, 35f, 35f, 35f, 0f, 0f, 0f, 0f});
        dragHandle.setBackground(titleBg); 
        dragHandle.setTextColor(dialogTextColor); // 【修复2】文字颜色跟随全局
        dragHandle.setPadding(40, 30, 40, 30); 
        dragHandle.setTextSize(dialogTextSize + 2f); // 【修复2】文字大小跟随全局(标题略大2号)
        dragHandle.setTypeface(null, Typeface.BOLD);                
        rootLayout.addView(dragHandle);

                        ScrollView scroll = new ScrollView(getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                // 【核心救命修复】：必须指明是 DynamicGamepadView.this，去拿全屏的真实宽高！
                // 绝不能让 ScrollView 拿自己还没算出来的高度 (0) 互为因果地死循环！
                int trueScreenH = Math.min(DynamicGamepadView.this.getWidth(), DynamicGamepadView.this.getHeight());
                
                // 按比例截取，留出 120px 给顶部的拖拽条
                int maxHeight = (int) (trueScreenH * dialogHeightRatio) - 120; 
                
                // 终极安全锁：防止在极端瞬间（比如横竖屏刚切换还没渲染完）高度变成负数导致崩溃
                if (maxHeight < 200) {
                    maxHeight = 200; 
                }
                
                int customHeightSpec = View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST);
                super.onMeasure(widthMeasureSpec, customHeightSpec);
            }
        };
        
        
        LinearLayout layout = new LinearLayout(getContext()); 
        layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(40, 20, 40, 40);

        layout.addView(createMenuButton(isEditMode ? L("💾 保存并退出编辑") : L("🛠️ 开启按键编辑"), v -> { isEditMode = !isEditMode; if (!isEditMode) saveConfig(); invalidate(); dialog.dismiss(); }));
        layout.addView(createMenuButton(L("➕ 新建组合键/宏"), v -> { 
            float scale = Math.max(0.5f, getHeight() / 1080f);
            VirtualButton newBtn = new VirtualButton(L("新键"), getWidth() / 2f, getHeight() / 2f, 90 * scale, Color.RED, 150, Color.WHITE, SHAPE_CIRCLE, "Z+X", false);                                             
            buttons.add(newBtn); isEditMode = true; showButtonSettingsDialog(newBtn); dialog.dismiss();
        }));
        layout.addView(createMenuButton(isGridSnapMode ? L("🧲 网格吸附：已开启") : L("🧲 网格吸附：已关闭"), v -> { isGridSnapMode = !isGridSnapMode; Toast.makeText(getContext(), isGridSnapMode ? L("已开启网格吸附") : L("已开启自由拖动"), Toast.LENGTH_SHORT).show(); dialog.dismiss(); showMainMenu(); }));
        if (isGridSnapMode) {
            // 1. 网格大小滑条 (加入 invalidate 实现实时预览)
            final SeekBar gridBar = createColorBar(layout, L("📐 网格尺寸步长 (拖动实时预览)"), gridSize);
            gridBar.setMax(200);
            gridBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar s, int p, boolean fromUser) { 
                    if (fromUser) { gridSize = Math.max(10, p); invalidate(); } // 【修改：实时刷新】
                }
                public void onStartTrackingTouch(SeekBar s) {} 
                public void onStopTrackingTouch(SeekBar s) { saveConfig(); }
            });

            // 2. 网格线透明度滑条
            final SeekBar lineAlphaBar = createColorBar(layout, L("👁️ 网格线不透明度 (0-255)"), gridLineAlpha);
            lineAlphaBar.setMax(255);
            lineAlphaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar s, int p, boolean fromUser) { 
                    if (fromUser) { gridLineAlpha = p; invalidate(); } 
                }
                public void onStartTrackingTouch(SeekBar s) {} 
                public void onStopTrackingTouch(SeekBar s) { saveConfig(); }
            });

            // 3. 网格线颜色下拉框
            layout.addView(createTitle(L("🎨 网格线颜色:")));
            final Spinner lineColorSpinner = new Spinner(getContext());
            ArrayAdapter<String> colorAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, TEXT_COLOR_NAMES);
            lineColorSpinner.setAdapter(colorAdapter);
            for (int i=0; i<TEXT_COLOR_VALUES.length; i++) { if (gridLineColor == TEXT_COLOR_VALUES[i]) { lineColorSpinner.setSelection(i); break; } }
            lineColorSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    gridLineColor = TEXT_COLOR_VALUES[position]; invalidate(); saveConfig();
                }
                public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
            layout.addView(lineColorSpinner);

            // 4. 背景色透明度滑条
            int currentBgAlpha = Color.alpha(gridBgColor);
            final SeekBar bgAlphaBar = createColorBar(layout, L("🌫️ 编辑背景不透明度 (0-255)"), currentBgAlpha);
            bgAlphaBar.setMax(255);
            bgAlphaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar s, int p, boolean fromUser) { 
                    if (fromUser) { 
                        gridBgColor = Color.argb(p, Color.red(gridBgColor), Color.green(gridBgColor), Color.blue(gridBgColor));
                        invalidate(); 
                    } 
                }
                public void onStartTrackingTouch(SeekBar s) {} 
                public void onStopTrackingTouch(SeekBar s) { saveConfig(); }
            });

            // 5. 背景颜色下拉框
            layout.addView(createTitle(L("🖌️ 编辑模式背景色:")));
            final Spinner bgColorSpinner = new Spinner(getContext());
            bgColorSpinner.setAdapter(colorAdapter);
            int rgbOnly = Color.rgb(Color.red(gridBgColor), Color.green(gridBgColor), Color.blue(gridBgColor));
            for (int i=0; i<TEXT_COLOR_VALUES.length; i++) { if (rgbOnly == TEXT_COLOR_VALUES[i]) { bgColorSpinner.setSelection(i); break; } }
            bgColorSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    int selectedColor = TEXT_COLOR_VALUES[position];
                    gridBgColor = Color.argb(Color.alpha(gridBgColor), Color.red(selectedColor), Color.green(selectedColor), Color.blue(selectedColor));
                    invalidate(); saveConfig();
                }
                public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
            layout.addView(bgColorSpinner);
        }

        layout.addView(createMenuButton(L("🕹️ 切换摇杆形态"), v -> { joystickMode = (joystickMode + 1) % 5; if (joystickMode == JOYSTICK_MODE_STYLE) refreshJoystickStyle(); saveConfig(); invalidate(); dialog.dismiss(); showMainMenu(); }));
        layout.addView(createMenuButton(L("⚙️ 全局设置配置区"), v -> { showVibrationSettingsDialog(); dialog.dismiss(); }));
        layout.addView(createMenuButton(L("📂 布局存档与导入导出"), v -> { showProfileManager(); dialog.dismiss(); }));
        layout.addView(createMenuButton(L("🔄 恢复初始默认布局"), v -> { loadDefaultLayout(); saveConfig(); invalidate(); dialog.dismiss(); }));
        
        // 👇 这里是新增的动态缩放开关 👇
        layout.addView(createMenuButton(isDynamicScaleEnabled ? L("📱 跨设备动态缩放适配: [已开启]") : L("📱 跨设备动态缩放适配: [已关闭(原版)]"), v -> {
            isDynamicScaleEnabled = !isDynamicScaleEnabled;
            saveConfig();
            Toast.makeText(getContext(), isDynamicScaleEnabled ? L("已开启: 自动缩放适配不同分辨率屏幕") : L("已关闭: 恢复原版像素1:1映射"), Toast.LENGTH_SHORT).show();
            dialog.dismiss(); showMainMenu();
        }));
        layout.addView(createMenuButton(L("🖼️ 屏幕遮罩详细设置"), v -> { showOverlaySettingsDialog(); dialog.dismiss(); }));
        layout.addView(createMenuButton(isOverlayVisible ? L("👁️ 隐藏遮罩图 (当前:显示)") : L("👁️ 显示遮罩图 (当前:隐藏)"), v -> { isOverlayVisible = !isOverlayVisible; invalidate(); dialog.dismiss(); showMainMenu(); }));
        layout.addView(createMenuButton(L("📁 重新选择游戏数据目录"), v -> { if (getContext() instanceof SDLActivity) ((SDLActivity) getContext()).checkAndPickFolder(); dialog.dismiss(); }));
        
        layout.addView(createMenuButton(alwaysAskFolder ? L("📂 每次启动强制重选目录: [已开启]") : L("📂 每次启动强制重选目录: [已关闭]"), v -> {
            alwaysAskFolder = !alwaysAskFolder;
            saveConfig();
            Toast.makeText(getContext(), alwaysAskFolder ? L("已开启: 每次启动/更新都会提示选择目录") : L("已关闭: 启动时将直接进入上次的目录"), Toast.LENGTH_SHORT).show();
            dialog.dismiss(); showMainMenu();
        }));
        
        // 【新增：主程序快速启动列表】
        layout.addView(createMenuButton(L("🗂️ 主程序列表管理系统"), v -> { showFolderPresetManagerDialog(); dialog.dismiss(); }));

        // 【新增：整合包兼容模式 UI 开关】
        layout.addView(createMenuButton(isIntegrationModeEnabled ? L("📦 整合包兼容直读模式: [已开启]") : L("📦 整合包兼容直读模式: [已关闭]"), v -> {
            isIntegrationModeEnabled = !isIntegrationModeEnabled;
            saveConfig();
            if (isIntegrationModeEnabled) {
                new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(L("📦 兼容模式已开启"))
                    .setMessage(L("此模式专为第三方整合包设计。\n\n引擎将自动扫描文件夹内是否有【任意.exe文件】或【data/system.def】。\n如果检测到，将直接读取整合包数据，绝对不会释放并覆盖官方脚本！"))
                    .setPositiveButton(L("我知道了"), null).show();
            } else {
                Toast.makeText(getContext(), L("已关闭：将恢复原版官方文件校验机制"), Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss(); showMainMenu();
        }));

        layout.addView(createMenuButton(L("⏱️ 面板自动隐藏设置"), v -> { showAutoHideSettingsDialog(); dialog.dismiss(); }));
        layout.addView(createMenuButton(L("🎨 按键风格管理系统"), v -> { showStyleManagerDialog(); dialog.dismiss(); }));
        layout.addView(createMenuButton(L("🎮 物理手柄与外设专区"), v -> { showGamepadSettingsDialog(); dialog.dismiss(); }));
        layout.addView(createMenuButton(L("🪟 自定义设置弹窗 UI外观"), v -> { showDialogCustomizationSettings(); dialog.dismiss(); }));
        // 【新增：多语言补丁导入入口】
        layout.addView(createMenuButton(L("🌐 导入本地化语言补丁 (.json)"), v -> { 
            android.app.Activity activity = (android.app.Activity) getContext();
            FileActionFragment fragment = new FileActionFragment();
            android.os.Bundle args = new android.os.Bundle();
            args.putInt("action_type", 3); // 3 代表导入语言包
            fragment.setArguments(args);
            activity.getFragmentManager().beginTransaction().add(fragment, "file_action").commitAllowingStateLoss();
            dialog.dismiss(); 
        }));
        // 【新增：桌面系统模式入口】
        layout.addView(createMenuButton(L("💻 进入桌面/梦工厂模式"), v -> { 
            if (getContext() instanceof SDLActivity) {
                ((SDLActivity) getContext()).toggleDesktopMode(true);
            }
            dialog.dismiss(); 
        }));


        scroll.addView(layout);
        rootLayout.addView(scroll);
        dialog.setContentView(rootLayout);
        setupMovableDialog(dialog, dragHandle);
        dialog.show();
    }

    private Button createMenuButton(String text, View.OnClickListener listener) {
        Button btn = new Button(getContext());
        btn.setText(text);
        btn.setTextColor(dialogTextColor);
        btn.setBackgroundColor(Color.parseColor("#33ffffff")); // 半透明底色
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 10, 0, 10);
        btn.setLayoutParams(params);
        btn.setOnClickListener(listener);
        return btn;
    }
        
    
        // 【修改】：独立抽出同步摇杆皮肤的方法，供多处调用
    public void refreshJoystickStyle() {
        if (joystickMode == JOYSTICK_MODE_STYLE && currentStyleIndex < styleList.size()) {
            GamepadStyle style = styleList.get(currentStyleIndex);
            // 如果连风格里都没配摇杆皮肤，也清空
            joySkinBaseUri = style.joyBaseUri != null ? style.joyBaseUri : ""; 
            joySkinKnobUri = style.joyKnobUri != null ? style.joyKnobUri : "";
            try {
                if(!joySkinBaseUri.isEmpty()) joySkinBaseBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getContext().getContentResolver().openInputStream(Uri.parse(joySkinBaseUri))), (int)(joyRadius*2), (int)(joyRadius*2), true); else joySkinBaseBitmap = null;
                if(!joySkinKnobUri.isEmpty()) joySkinKnobBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getContext().getContentResolver().openInputStream(Uri.parse(joySkinKnobUri))), (int)(joyRadius*2), (int)(joyRadius*2), true); else joySkinKnobBitmap = null;
            } catch (Exception e) { joySkinBaseBitmap = null; joySkinKnobBitmap = null; }
        }
    }

    private void showStyleManagerDialog() {
        if (styleList.isEmpty()) generateVideoArcadeStyle(); 
        

                CharSequence[] options = {
            L("🎨 1. 选择并应用现有风格"), 
            L("💾 2. 提取当前面板保存为新风格"), 
            L("✏️ 3. 重命名选择的风格"), 
            L("🗑️ 4. 删除当前选择的风格")
        };
        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(L("按键风格系统 (Style System)"))
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    String[] styleNames = new String[styleList.size()];
                    for(int i=0; i<styleList.size(); i++) styleNames[i] = styleList.get(i).styleName;
                    new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle(L("应用风格 (将替换所有按键)"))
                        .setSingleChoiceItems(styleNames, currentStyleIndex, (d, w) -> currentStyleIndex = w)
                        .setPositiveButton(L("确定应用"), (d, w) -> {
                            GamepadStyle style = styleList.get(currentStyleIndex);
                            joystickMode = JOYSTICK_MODE_STYLE;
                            refreshJoystickStyle(); 
                            for (VirtualButton b : buttons) {
                                if (!b.isDirectional) {
                                    b.color = style.globalBtnColor; // 同步底色
                                    if (b.shape == SHAPE_CIRCLE) {
                                        b.customImageUri = style.btnNormalUri != null ? style.btnNormalUri : ""; 
                                    } else {
                                        // 【彻底修复】：如果是方形，绝不借用圆图！有方形专图就用，没有就直接置空（让系统画出纯色渐变方块）
                                        b.customImageUri = (style.btnSquareUri != null && !style.btnSquareUri.isEmpty()) ? style.btnSquareUri : ""; 
                                    }
                                    b.customPressedUri = style.btnPressedUri != null ? style.btnPressedUri : "";
                                    b.pressedEffectColor = style.globalPressedColor; 
                                    b.pressedEffectAlpha = style.globalPressedAlpha;
                                    b.loadSkinFromUri(getContext());
                                }
                            }
                            saveConfig(); invalidate(); Toast.makeText(getContext(), L("已应用风格: ") + style.styleName, Toast.LENGTH_SHORT).show();
                        }).setNegativeButton(L("取消"), null).show();
                } else if (which == 1) {
                    final EditText input = createEditText(L("给新风格命名"), L("我的自定义风格"));
                    new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle(L("提取并保存风格"))
                        .setMessage(L("将自动提取当前屏幕上的按键外观，打包为新风格。"))
                        .setView(input)
                        .setPositiveButton(L("保存"), (d, w) -> {
                            GamepadStyle newStyle = new GamepadStyle(input.getText().toString());
                            newStyle.joyBaseUri = joySkinBaseUri; newStyle.joyKnobUri = joySkinKnobUri;
                            for (VirtualButton b : buttons) { 
                                if (!b.isDirectional) {
                                    if (b.shape == SHAPE_CIRCLE) newStyle.btnNormalUri = b.customImageUri; 
                                    else newStyle.btnSquareUri = b.customImageUri;
                                    newStyle.btnPressedUri = b.customPressedUri;
                                    newStyle.globalPressedColor = b.pressedEffectColor; newStyle.globalPressedAlpha = b.pressedEffectAlpha;
                                }
                            }
                            // 【彻底修复】：取消互相借用！方形键如果没有方形图片，就应该为空，这样底层才会画出完美的方形框。
                            
                            styleList.add(newStyle); currentStyleIndex = styleList.size() - 1;

                            saveConfig(); Toast.makeText(getContext(), L("新风格保存成功！"), Toast.LENGTH_SHORT).show();
                        }).setNegativeButton(L("取消"), null).show();

                // 👇这里就是你漏掉的重命名逻辑👇
                } else if (which == 2) {
                    final EditText input = createEditText(L("新风格名称"), styleList.get(currentStyleIndex).styleName);
                    new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle(L("重命名风格")).setView(input)
                        .setPositiveButton(L("保存"), (d, w) -> {
                            styleList.get(currentStyleIndex).styleName = input.getText().toString();
                            saveConfig(); Toast.makeText(getContext(), L("已重命名"), Toast.LENGTH_SHORT).show();
                        }).show();
                // 👇删除逻辑变成了 which == 3👇
                } else if (which == 3) {
                    if (currentStyleIndex <= 1) { Toast.makeText(getContext(), L("系统默认风格不可删除！"), Toast.LENGTH_SHORT).show(); return; }
                    styleList.remove(currentStyleIndex); currentStyleIndex = 0; saveConfig();
                    Toast.makeText(getContext(), L("风格已删除"), Toast.LENGTH_SHORT).show();
                }
            }).show();
        }

        // ================= 新增：图片 Base64 自动封包与解包引擎 =================
    public String embedImageToBase64(String uriStr) {
        if (uriStr == null || uriStr.isEmpty()) return "";
        if (uriStr.startsWith("base64:")) return uriStr;
        // 如果是系统预设风格图（名字带 style_），不转Base64，因为接收方手机里一定自带，节省体积
        if (uriStr.contains("style_base_") || uriStr.contains("style_knob_") || uriStr.contains("style_btn_") || uriStr.contains("style_sq_")) return uriStr;
        try {
            java.io.InputStream is = getContext().getContentResolver().openInputStream(Uri.parse(uriStr));
            android.graphics.Bitmap bm = BitmapFactory.decodeStream(is);
            is.close();
            if (bm == null) return uriStr;
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            bm.compress(android.graphics.Bitmap.CompressFormat.PNG, 80, baos); 
            byte[] b = baos.toByteArray();
            bm.recycle();
            return "base64:" + android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP);
        } catch (Exception e) { return uriStr; }
    }

    public String extractBase64ToImage(String dataStr) {
        if (dataStr == null || dataStr.isEmpty()) return "";
        if (!dataStr.startsWith("base64:")) return dataStr; 
        try {
            String b64 = dataStr.substring(7);
            byte[] decodedBytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
            android.graphics.Bitmap bm = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
            return saveImageToLocal(bm, "imported_skin_" + System.currentTimeMillis() + ".png");
        } catch (Exception e) { return ""; }
    }

    private void showProfileManager() {
        CharSequence[] options = {
            L("📤 导出: [全部布局、位置与所有皮肤]"), 
            L("📤 仅导出: [按键布局定位与基本映射]"), 
            L("📤 仅导出: [所有皮肤外观与样式库]"), 
            L("📥 导入: 从文件中读取配置")
        };
        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(L("数据导出与导入 (完美跨设备分享)"))
                .setItems(options, (dialog, which) -> {
                    android.app.Activity activity = (android.app.Activity) getContext();
                    FileActionFragment fragment = new FileActionFragment();
                    android.os.Bundle args = new android.os.Bundle();
                    
                    if (which <= 2) { 
                        args.putInt("action_type", 1); 
                        try {
                            JSONObject root = new JSONObject();
                            root.put("exportMode", which); // 打上标记，防混乱
                            JSONArray rawLayout = new JSONArray(prefs.getString(KEY_LAYOUT_PREFIX + currentSlot, "[]"));
                            
                            if (which == 0 || which == 1) { // 【仅布局】或【全部】
                                JSONArray exportLayout = new JSONArray();
                                for (int i = 0; i < rawLayout.length(); i++) {
                                    JSONObject btn = new JSONObject(rawLayout.getJSONObject(i).toString());
                                    if (which == 1) { 
                                        // 【仅布局】：无情剥离所有外观数据，只保留位置和映射逻辑
                                        btn.remove("skin"); btn.remove("pressedSkin");
                                        btn.remove("color"); btn.remove("pressedColor");
                                        btn.remove("alpha"); btn.remove("pressedAlpha");
                                        btn.remove("textColor"); btn.remove("shape");
                                    } else {
                                        // 【全部导出】：检测并封包自定义图为 Base64
                                        btn.put("skin", embedImageToBase64(btn.optString("skin")));
                                        btn.put("pressedSkin", embedImageToBase64(btn.optString("pressedSkin")));
                                    }
                                    exportLayout.put(btn);
                                }
                                root.put("layout", exportLayout);
                                root.put("joystickMode", joystickMode);
                                root.put("joyBaseX", joyBaseX); root.put("joyBaseY", joyBaseY);
                                root.put("joyRadius", joyRadius); root.put("joyHitboxRadius", joyHitboxRadius);
                                root.put("isJoyLocked", isJoyLocked); root.put("isMenuLocked", isMenuLocked);
                                root.put("menuX", menuX); root.put("menuY", menuY);
                                root.put("menuWidth", menuWidth); root.put("menuHeight", menuHeight);
                                root.put("menuScale", menuScale);
                                root.put("savedScreenWidth", loadedSavedWidth > 0 ? loadedSavedWidth : getWidth());
                                root.put("savedScreenHeight", loadedSavedHeight > 0 ? loadedSavedHeight : getHeight());
                                root.put("isVibrationOn", isVibrationOn); root.put("vibrationIntensity", vibrationIntensity);
                            }
                            
                            if (which == 0 || which == 2) { // 【仅风格】或【全部】
                                JSONArray styleArr = new JSONArray();
                                for(GamepadStyle s : styleList) {
                                    JSONObject sj = s.toJson();
                                    sj.put("joyBaseUri", embedImageToBase64(sj.optString("joyBaseUri")));
                                    sj.put("joyKnobUri", embedImageToBase64(sj.optString("joyKnobUri")));
                                    sj.put("btnNormalUri", embedImageToBase64(sj.optString("btnNormalUri")));
                                    sj.put("btnSquareUri", embedImageToBase64(sj.optString("btnSquareUri")));
                                    sj.put("btnPressedUri", embedImageToBase64(sj.optString("btnPressedUri")));
                                    styleArr.put(sj);
                                }
                                root.put("styles", styleArr);
                                
                                // 【核心机制】：提取当前所有按键独占的外观数据映射关系表
                                JSONArray btnStyles = new JSONArray();
                                for (int i = 0; i < rawLayout.length(); i++) {
                                    JSONObject btn = rawLayout.getJSONObject(i);
                                    JSONObject bs = new JSONObject();
                                    bs.put("id", btn.optString("id", ""));
                                    bs.put("keyMap", btn.optString("keyMap", ""));
                                    bs.put("skin", embedImageToBase64(btn.optString("skin", "")));
                                    bs.put("pressedSkin", embedImageToBase64(btn.optString("pressedSkin", "")));
                                    bs.put("color", btn.optInt("color", Color.GRAY));
                                    bs.put("pressedColor", btn.optInt("pressedColor", 0));
                                    bs.put("alpha", btn.optInt("alpha", 150));
                                    bs.put("pressedAlpha", btn.optInt("pressedAlpha", 150));
                                    bs.put("textColor", btn.optInt("textColor", Color.WHITE));
                                    bs.put("shape", btn.optInt("shape", SHAPE_CIRCLE));
                                    btnStyles.put(bs);
                                }
                                root.put("buttonStyles", btnStyles);
                                
                                root.put("joyAlpha", joyAlpha); root.put("joyColor", joyColor);
                                root.put("joySkinBase", embedImageToBase64(joySkinBaseUri)); 
                                root.put("joySkinKnob", embedImageToBase64(joySkinKnobUri));
                                root.put("menuAlpha", menuAlpha); root.put("currentStyleIndex", currentStyleIndex);
                            }
                            args.putString("export_data", root.toString());
                        } catch(Exception e) {}
                    } else if (which == 3) { 
                        args.putInt("action_type", 2); 
                    }
                    fragment.setArguments(args);
                    activity.getFragmentManager().beginTransaction().add(fragment, "file_action").commitAllowingStateLoss();
                }).show();
    }
    
    // ================= 新增：预设文件夹管理系统完整 UI =================
    private void showFolderPresetManagerDialog() {
        final android.app.Dialog dialog = new android.app.Dialog(getContext(), android.R.style.Theme_DeviceDefault_Dialog);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        
        LinearLayout rootLayout = new LinearLayout(getContext());
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackground(getCustomDialogBackground());

        TextView dragHandle = new TextView(getContext());
        dragHandle.setText(L("✋ 拖拽此处 | 🗂️ 主程序列表管理"));
        android.graphics.drawable.GradientDrawable titleBg = new android.graphics.drawable.GradientDrawable();
        titleBg.setColor(Color.argb(50, 0, 0, 0)); titleBg.setCornerRadii(new float[]{35f, 35f, 35f, 35f, 0f, 0f, 0f, 0f});
        dragHandle.setBackground(titleBg); dragHandle.setTextColor(dialogTextColor);
        dragHandle.setPadding(40, 30, 40, 30); dragHandle.setTextSize(dialogTextSize + 2f);
        dragHandle.setTypeface(null, Typeface.BOLD); rootLayout.addView(dragHandle);

        ScrollView scroll = new ScrollView(getContext()) {
            @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int trueScreenH = Math.min(DynamicGamepadView.this.getWidth(), DynamicGamepadView.this.getHeight());
                int maxHeight = (int) (trueScreenH * dialogHeightRatio) - 120; 
                if (maxHeight < 200) { maxHeight = 200; }
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST));
            }
        };

        LinearLayout layout = new LinearLayout(getContext()); 
        layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(50, 20, 50, 50);

        // --- 1. 大大的加号按键区 ---
        Button addBtn = new Button(getContext());
        addBtn.setText(L("➕ 添加一个新的主程序路径"));
        addBtn.setTextColor(Color.WHITE); addBtn.setTextSize(18f);
        addBtn.setBackgroundColor(Color.parseColor("#4CAF50"));
        addBtn.setPadding(0, 40, 0, 40);
        addBtn.setOnClickListener(v -> {
            if (getContext() instanceof SDLActivity) {
                dialog.dismiss();
                ((SDLActivity) getContext()).pickFolderForPreset(); // 调用系统层选取
            }
        });
        layout.addView(addBtn);
        
        layout.addView(createTitle(" ")); // 空行

        // --- 2. 遍历渲染现有文件夹卡片 ---
        for (int i = 0; i < folderPresets.size(); i++) {
            final int index = i;
            final FolderPreset preset = folderPresets.get(i);
            
            LinearLayout card = new LinearLayout(getContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(30, 30, 30, 30);
            android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
            cardBg.setColor(Color.argb(80, 0, 0, 0)); cardBg.setCornerRadius(20f);
            cardBg.setStroke(3, preset.color); // 使用自定义的字体颜色作为卡片边框高亮
            card.setBackground(cardBg);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMargins(0, 20, 0, 20); card.setLayoutParams(cp);
            
            // 名字和URI
            TextView nameTv = new TextView(getContext());
            nameTv.setText("📁 " + preset.name);
            nameTv.setTextColor(preset.color); nameTv.setTextSize(dialogTextSize + 4f); nameTv.setTypeface(null, Typeface.BOLD);
            card.addView(nameTv);
            TextView uriTv = new TextView(getContext());
            uriTv.setText(preset.uri); uriTv.setTextColor(Color.GRAY); uriTv.setTextSize(dialogTextSize - 2f); uriTv.setPadding(0,0,0,5);
            card.addView(uriTv);
            if (preset.motifPath != null && !preset.motifPath.isEmpty()) {
                TextView motifTv = new TextView(getContext());
                motifTv.setText(L("🎯 专属UI主题: ") + preset.motifPath); motifTv.setTextColor(Color.parseColor("#4CAF50")); motifTv.setTextSize(dialogTextSize - 3f); motifTv.setPadding(0,0,0,20);
                card.addView(motifTv);
            } else { uriTv.setPadding(0,0,0,20); }
            
            // 按钮操作区 (水平排列)
            LinearLayout btnRow = new LinearLayout(getContext()); btnRow.setOrientation(LinearLayout.HORIZONTAL);
            
            // 【启动按钮】带有确认提示
            Button launchBtn = new Button(getContext()); launchBtn.setText(L("🚀 读取此文件夹")); launchBtn.setTextColor(Color.WHITE); launchBtn.setBackgroundColor(Color.parseColor("#2196F3"));
            LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f); lp1.setMargins(0,0,10,0); launchBtn.setLayoutParams(lp1);
            launchBtn.setOnClickListener(v -> {
                new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(L("🔄 即将重新加载数据"))
                    .setMessage(L("确定要无缝切换到【") + preset.name + L("】并重新加载游戏吗？\n\n⚠️ 警告：如果您读取的是第三方整合包或非官方引擎，请务必确保已在设置中开启【📦 整合包兼容直读模式】，否则可能导致原版素材被无损注入覆盖或数据异常！"))
                    .setPositiveButton(L("立刻加载"), (d, w) -> {
                        if (getContext() instanceof SDLActivity) {
                            dialog.dismiss();
                            ((SDLActivity) getContext()).saveAndRestartWithPresetUri(preset.uri, preset.motifPath);
                        }
                    }).setNegativeButton(L("取消"), null).show();
            });
            btnRow.addView(launchBtn);
            
            // 【编辑按钮】
            Button editBtn = new Button(getContext()); editBtn.setText(L("✏️ 编辑")); editBtn.setTextColor(Color.WHITE); editBtn.setBackgroundColor(Color.parseColor("#FF9800"));
            LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f); lp2.setMargins(0,0,10,0); editBtn.setLayoutParams(lp2);
            editBtn.setOnClickListener(v -> {
                dialog.dismiss();
                showEditFolderPresetDialog(preset);
            });
            btnRow.addView(editBtn);

            // 【删除按钮】带有确认提示
            Button delBtn = new Button(getContext()); delBtn.setText(L("🗑️")); delBtn.setTextColor(Color.WHITE); delBtn.setBackgroundColor(Color.parseColor("#F44336"));
            LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); delBtn.setLayoutParams(lp3);
            delBtn.setOnClickListener(v -> {
                new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(L("移除预设")).setMessage(L("确定要从列表中移除【") + preset.name + L("】吗？\n(仅移除快捷方式，不会删除手机里的真实文件)"))
                    .setPositiveButton(L("确定移除"), (d, w) -> {
                        folderPresets.remove(index); saveConfig(); dialog.dismiss(); showFolderPresetManagerDialog();
                    }).setNegativeButton(L("取消"), null).show();
            });
            btnRow.addView(delBtn);

            card.addView(btnRow);
            layout.addView(card);
        }

        Button exitBtn = new Button(getContext()); exitBtn.setText(L("关闭面板"));
        LinearLayout.LayoutParams exParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); exParams.setMargins(0, 40, 0, 0); exitBtn.setLayoutParams(exParams);
        exitBtn.setOnClickListener(v -> dialog.dismiss()); layout.addView(exitBtn);

        scroll.addView(layout); rootLayout.addView(scroll);
        dialog.setContentView(rootLayout); setupMovableDialog(dialog, dragHandle); dialog.show();
    }
    
    // 编辑单个文件夹预设属性（名字和颜色）
    private void showEditFolderPresetDialog(final FolderPreset preset) {
        final android.app.Dialog dialog = new android.app.Dialog(getContext(), android.R.style.Theme_DeviceDefault_Dialog);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout rootLayout = new LinearLayout(getContext()); rootLayout.setOrientation(LinearLayout.VERTICAL); rootLayout.setBackground(getCustomDialogBackground());
        TextView dragHandle = new TextView(getContext()); dragHandle.setText(L("✋ 拖拽此处 | ✏️ 编辑主程序预设"));
        android.graphics.drawable.GradientDrawable titleBg = new android.graphics.drawable.GradientDrawable();
        titleBg.setColor(Color.argb(50, 0, 0, 0)); titleBg.setCornerRadii(new float[]{35f, 35f, 35f, 35f, 0f, 0f, 0f, 0f}); dragHandle.setBackground(titleBg); dragHandle.setTextColor(dialogTextColor);
        dragHandle.setPadding(40, 30, 40, 30); dragHandle.setTextSize(dialogTextSize + 2f); dragHandle.setTypeface(null, Typeface.BOLD); rootLayout.addView(dragHandle);

        LinearLayout layout = new LinearLayout(getContext()); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(50, 50, 50, 50);
        
        layout.addView(createTitle(L("自定义显示名称:")));
        final EditText nameInput = createEditText(L("给它起个好记的名字"), preset.name); layout.addView(nameInput);
        
        layout.addView(createTitle(L("字体高亮颜色:")));
        final Spinner colorSpinner = new Spinner(getContext());
        ArrayAdapter<String> colorAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, TEXT_COLOR_NAMES);
        colorSpinner.setAdapter(colorAdapter);
        for (int i=0; i<TEXT_COLOR_VALUES.length; i++) { if (preset.color == TEXT_COLOR_VALUES[i]) { colorSpinner.setSelection(i); break; } }
        layout.addView(colorSpinner);
        
        Button saveBtn = new Button(getContext()); saveBtn.setText(L("💾 保存设置")); saveBtn.setTextColor(Color.WHITE); saveBtn.setBackgroundColor(Color.parseColor("#1976D2"));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); btnParams.setMargins(0, 40, 0, 0); saveBtn.setLayoutParams(btnParams);
        saveBtn.setOnClickListener(v -> {
            preset.name = nameInput.getText().toString();
            preset.color = TEXT_COLOR_VALUES[colorSpinner.getSelectedItemPosition()];
            saveConfig(); dialog.dismiss(); showFolderPresetManagerDialog(); // 重新打开列表
        });
        layout.addView(saveBtn);
        
        rootLayout.addView(layout); dialog.setContentView(rootLayout); setupMovableDialog(dialog, dragHandle); dialog.show();
    }
    // 【注意】这里需要配合在 SDLActivity 里写一个 pickFolderForPreset 的底层方法来实现系统文件夹选择，如果 SDL 那边还没写，我会帮你处理。

    // 动态生成弹窗背景（支持纯色+透明度，或图片+透明度+圆角）
    private android.graphics.drawable.Drawable getCustomDialogBackground() {
        if (dialogBgBitmap != null && !dialogBgBitmap.isRecycled()) {
            return new android.graphics.drawable.Drawable() {
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                {
                    android.graphics.BitmapShader shader = new android.graphics.BitmapShader(dialogBgBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    paint.setShader(shader);
                    paint.setAlpha(dialogBgAlpha);
                    dimPaint.setColor(Color.argb((int)(dialogBgAlpha * 0.4f), 0, 0, 0)); 
                }
                @Override public void draw(Canvas canvas) {
                    RectF rect = new RectF(getBounds());
                    canvas.drawRoundRect(rect, 35f, 35f, paint);
                    canvas.drawRoundRect(rect, 35f, 35f, dimPaint);
                }
                @Override public void setAlpha(int alpha) {}
                @Override public void setColorFilter(android.graphics.ColorFilter colorFilter) {}
                @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
            };
        } else {
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(Color.argb(dialogBgAlpha, Color.red(dialogBgColor), Color.green(dialogBgColor), Color.blue(dialogBgColor)));
            bg.setCornerRadius(35f);
            return bg;
        }
    }


        // 【新增】遮罩图控制面板
    private void showOverlaySettingsDialog() {
        final android.app.Dialog dialog = new android.app.Dialog(getContext(), android.R.style.Theme_DeviceDefault_Dialog);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 60, 60, 60);
        layout.setBackground(getCustomDialogBackground());
        

                                      ScrollView scroll = new ScrollView(getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                // 【核心救命修复】：必须指明是 DynamicGamepadView.this，去拿全屏的真实宽高！
                // 绝不能让 ScrollView 拿自己还没算出来的高度 (0) 互为因果地死循环！
                int trueScreenH = Math.min(DynamicGamepadView.this.getWidth(), DynamicGamepadView.this.getHeight());
                
                // 按比例截取，留出 120px 给顶部的拖拽条
                int maxHeight = (int) (trueScreenH * dialogHeightRatio) - 120; 
                
                // 终极安全锁：防止在极端瞬间（比如横竖屏刚切换还没渲染完）高度变成负数导致崩溃
                if (maxHeight < 200) {
                    maxHeight = 200; 
                }
                
                int customHeightSpec = View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST);
                super.onMeasure(widthMeasureSpec, customHeightSpec);
            }
        };
                               
                       
                
    
        
        LinearLayout contentLayout = new LinearLayout(getContext());
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        
        contentLayout.addView(createTitle(L("🖼️ 遮罩图配置面板")));

        // 强制全屏隐藏开关
        final Button toggleHideBtn = new Button(getContext());
        toggleHideBtn.setText(isFullscreenHideOverlay ? L("【开启】游戏强制全屏时隐藏遮罩") : L("【关闭】全屏不影响遮罩"));
        toggleHideBtn.setTextColor(Color.WHITE);
        toggleHideBtn.setBackgroundColor(isFullscreenHideOverlay ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));
        toggleHideBtn.setOnClickListener(v -> {
            isFullscreenHideOverlay = !isFullscreenHideOverlay;
            toggleHideBtn.setText(isFullscreenHideOverlay ? L("【开启】游戏强制全屏时隐藏遮罩") : L("【关闭】全屏不影响遮罩"));
            toggleHideBtn.setBackgroundColor(isFullscreenHideOverlay ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));
        });
        contentLayout.addView(toggleHideBtn);

        // 模式选择
        contentLayout.addView(createTitle(L("模式选择：")));
        final Spinner modeSpinner = new Spinner(getContext());
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, new String[]{L("关闭遮罩"), L("开启一张遮罩图"), L("开启两张遮罩图")});
        modeSpinner.setAdapter(modeAdapter);
        modeSpinner.setSelection(overlayMode);
        modeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                overlayMode = position;
                invalidate(); 
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        contentLayout.addView(modeSpinner);

        // ==== 第一张图控件 ====
        contentLayout.addView(createTitle(L("--- 遮罩图 1 (绿框) ---")));
        LinearLayout btnLayout1 = new LinearLayout(getContext()); btnLayout1.setOrientation(LinearLayout.HORIZONTAL);
        Button mirrorBmp1 = new Button(getContext()); mirrorBmp1.setText(overlayMirror1 ? L("↔️ 镜像: [开]") : L("↔️ 镜像: [关]"));
        mirrorBmp1.setOnClickListener(v -> { overlayMirror1 = !overlayMirror1; mirrorBmp1.setText(overlayMirror1 ? L("↔️ 镜像: [开]") : L("↔️ 镜像: [关]")); invalidate(); });
        btnLayout1.addView(mirrorBmp1);
        Button pickBmp1 = new Button(getContext()); pickBmp1.setText(L("选择图片")); pickBmp1.setOnClickListener(v -> { imagePickerTarget = 4; pickImage(); });
        Button clearBmp1 = new Button(getContext()); clearBmp1.setText(L("清除图片")); clearBmp1.setOnClickListener(v -> { overlayUri1 = ""; overlayBmp1 = null; overlayMovie1 = null; if(movieBuffer1!=null){movieBuffer1.recycle(); movieBuffer1=null;} invalidate(); });
        btnLayout1.addView(pickBmp1); btnLayout1.addView(clearBmp1); contentLayout.addView(btnLayout1);

        final SeekBar xBar1 = createColorBar(contentLayout, L("X 轴位置"), (int)overlayX1); xBar1.setMax(3000);
        final SeekBar yBar1 = createColorBar(contentLayout, L("Y 轴位置"), (int)overlayY1); yBar1.setMax(2000);
        final SeekBar sxBar1 = createColorBar(contentLayout, L("↔️ 独立宽度拉宽 (%)"), (int)(overlayScaleX1 * 100)); sxBar1.setMax(500);
        final SeekBar syBar1 = createColorBar(contentLayout, L("↕️ 独立高度拉长 (%)"), (int)(overlayScaleY1 * 100)); syBar1.setMax(500);
        final SeekBar rBar1 = createColorBar(contentLayout, L("🔄 旋转角度 (°)"), (int)overlayRotation1); rBar1.setMax(360);
        final SeekBar cBar1 = createColorBar(contentLayout, L("📺 遮罩图边缘弯曲度"), (int)overlayCurvature1); cBar1.setMax(100);
                
        SeekBar.OnSeekBarChangeListener valUpdater1 = new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if(s==xBar1) overlayX1 = p; else if(s==yBar1) overlayY1 = p; else if(s==sxBar1) overlayScaleX1 = p / 100f; else if(s==syBar1) overlayScaleY1 = p / 100f; else if(s==rBar1) overlayRotation1 = p; else if(s==cBar1) overlayCurvature1 = p;
                invalidate();
            }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        };
        xBar1.setOnSeekBarChangeListener(valUpdater1); yBar1.setOnSeekBarChangeListener(valUpdater1); sxBar1.setOnSeekBarChangeListener(valUpdater1); syBar1.setOnSeekBarChangeListener(valUpdater1); rBar1.setOnSeekBarChangeListener(valUpdater1); cBar1.setOnSeekBarChangeListener(valUpdater1);

// ==== 第二张图控件 ====
        contentLayout.addView(createTitle(L("--- 遮罩图 2 (蓝框) ---")));
        LinearLayout btnLayout2 = new LinearLayout(getContext()); btnLayout2.setOrientation(LinearLayout.HORIZONTAL);
        Button mirrorBmp2 = new Button(getContext()); mirrorBmp2.setText(overlayMirror2 ? L("↔️ 镜像: [开]") : L("↔️ 镜像: [关]"));
        mirrorBmp2.setOnClickListener(v -> { overlayMirror2 = !overlayMirror2; mirrorBmp2.setText(overlayMirror2 ? L("↔️ 镜像: [开]") : L("↔️ 镜像: [关]")); invalidate(); });
        btnLayout2.addView(mirrorBmp2);
        Button pickBmp2 = new Button(getContext()); pickBmp2.setText(L("选择图片")); pickBmp2.setOnClickListener(v -> { imagePickerTarget = 5; pickImage(); });
        Button clearBmp2 = new Button(getContext()); clearBmp2.setText(L("清除图片")); clearBmp2.setOnClickListener(v -> { overlayUri2 = ""; overlayBmp2 = null; overlayMovie2 = null; if(movieBuffer2!=null){movieBuffer2.recycle(); movieBuffer2=null;} invalidate(); });        
        btnLayout2.addView(pickBmp2); btnLayout2.addView(clearBmp2); contentLayout.addView(btnLayout2);

        final SeekBar xBar2 = createColorBar(contentLayout, L("X 轴位置"), (int)overlayX2); xBar2.setMax(3000);
        final SeekBar yBar2 = createColorBar(contentLayout, L("Y 轴位置"), (int)overlayY2); yBar2.setMax(2000);
        final SeekBar sxBar2 = createColorBar(contentLayout, L("↔️ 独立宽度拉宽 (%)"), (int)(overlayScaleX2 * 100)); sxBar2.setMax(500);
        final SeekBar syBar2 = createColorBar(contentLayout, L("↕️ 独立高度拉长 (%)"), (int)(overlayScaleY2 * 100)); syBar2.setMax(500);
        final SeekBar rBar2 = createColorBar(contentLayout, L("🔄 旋转角度 (°)"), (int)overlayRotation2); rBar2.setMax(360);
        final SeekBar cBar2 = createColorBar(contentLayout, L("📺 遮罩图边缘弯曲度"), (int)overlayCurvature2); cBar2.setMax(100);
        
        SeekBar.OnSeekBarChangeListener valUpdater2 = new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if(s==xBar2) overlayX2 = p; else if(s==yBar2) overlayY2 = p; else if(s==sxBar2) overlayScaleX2 = p / 100f; else if(s==syBar2) overlayScaleY2 = p / 100f; else if(s==rBar2) overlayRotation2 = p; else if(s==cBar2) overlayCurvature2 = p;
                invalidate();
            }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        };
        xBar2.setOnSeekBarChangeListener(valUpdater2); yBar2.setOnSeekBarChangeListener(valUpdater2); sxBar2.setOnSeekBarChangeListener(valUpdater2); syBar2.setOnSeekBarChangeListener(valUpdater2); rBar2.setOnSeekBarChangeListener(valUpdater2); cBar2.setOnSeekBarChangeListener(valUpdater2);
                        

                

        Button saveBtn = new Button(getContext());
        saveBtn.setText(L("💾 保存并关闭"));
        saveBtn.setTextColor(Color.WHITE);
        saveBtn.setBackgroundColor(Color.parseColor("#1976D2"));
        saveBtn.setOnClickListener(v -> { overlayMode = modeSpinner.getSelectedItemPosition(); saveConfig(); invalidate(); dialog.dismiss(); });
        contentLayout.addView(saveBtn);

        scroll.addView(contentLayout);
        layout.addView(scroll);
        dialog.setContentView(layout);
        dialog.show();
    }
    

    private void pickImage() {
        android.app.Activity activity = (android.app.Activity) getContext();
        FileActionFragment fragment = new FileActionFragment();
        android.os.Bundle args = new android.os.Bundle(); args.putInt("action_type", 0);
        fragment.setArguments(args);
        activity.getFragmentManager().beginTransaction().add(fragment, "file_action").commitAllowingStateLoss();
    }

    private void showVibrationSettingsDialog() {
        // 【关键实现】使用临时变量进行缓存预览，未点击保存则自动丢弃
        final boolean[] tempVibOn = {isVibrationOn};
        final int[] tempVibInt = {vibrationIntensity};
        final boolean[] tempFeedOn = {isGlobalFeedbackEnabled};
        final int[] tempFeedScale = {globalFeedbackScaleInt};
        final int[] tempGlobalAlpha = {joyAlpha}; // 初始值参考摇杆透明度
        int initialPressedAlpha = 150;
        if (!buttons.isEmpty()) initialPressedAlpha = buttons.get(0).pressedEffectAlpha;
        final int[] tempGlobalPressedAlpha = {initialPressedAlpha}; // 获取首个按键的按下透明度作参考

        final android.app.Dialog dialog = new android.app.Dialog(getContext(), android.R.style.Theme_DeviceDefault_Dialog);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        
        LinearLayout rootLayout = new LinearLayout(getContext());
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackground(getCustomDialogBackground());

        ScrollView scroll = new ScrollView(getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int trueScreenH = Math.min(DynamicGamepadView.this.getWidth(), DynamicGamepadView.this.getHeight());
                int maxHeight = (int) (trueScreenH * dialogHeightRatio); 
                if (maxHeight < 200) { maxHeight = 200; }
                int customHeightSpec = View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST);
                super.onMeasure(widthMeasureSpec, customHeightSpec);
            }
        };

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 60, 60, 60);

        // ================= 1. 全局透明度统一调整 =================
        layout.addView(createTitle(L("👁️ 全局不透明度统一调整")));
        final SeekBar alphaBar = createColorBar(layout, L("拖动统一修改所有按键 (0为全透, 255不透明)"), tempGlobalAlpha[0]);
        alphaBar.setMax(255);
        alphaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                tempGlobalAlpha[0] = p;
                if (fromUser) {
                    joyAlpha = p; // 实时预览：摇杆
                    menuAlpha = p; // 实时预览：菜单
                    for (VirtualButton b : buttons) b.alpha = p; // 实时预览：所有普通按键
                    invalidate(); 
                }
            }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        });

        // 【新增：全局按下特效透明度滑块】
        final SeekBar pressedAlphaBar = createColorBar(layout, L("拖动统一修改所有按键【按下时的特效透明度】"), tempGlobalPressedAlpha[0]);
        pressedAlphaBar.setMax(255);
        pressedAlphaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                tempGlobalPressedAlpha[0] = p;
                if (fromUser) {
                    for (VirtualButton b : buttons) {
                        b.pressedEffectAlpha = p; 
                        // 为了能看见效果，强行让第一个按键保持按下状态进行实时预览
                        if (buttons.indexOf(b) == 0) b.isPressed = true;
                    }
                    invalidate(); 
                }
            }
            public void onStartTrackingTouch(SeekBar s) { } 
            public void onStopTrackingTouch(SeekBar s) {
                for (VirtualButton b : buttons) b.isPressed = false; // 松开滑块时恢复原状
                invalidate();
            }
        });

        layout.addView(createTitle("")); // 占位空行

        // ================= 2. 震动设置 =================
        layout.addView(createTitle(L("📳 全局震动控制")));
        final Button toggleVibBtn = new Button(getContext());
        toggleVibBtn.setText(tempVibOn[0] ? L("全局震动：已开启") : L("全局震动：已关闭"));
        toggleVibBtn.setTextColor(Color.WHITE);
        toggleVibBtn.setBackgroundColor(tempVibOn[0] ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));
        toggleVibBtn.setOnClickListener(v -> {
            tempVibOn[0] = !tempVibOn[0];
            toggleVibBtn.setText(tempVibOn[0] ? L("全局震动：已开启") : L("全局震动：已关闭"));
            toggleVibBtn.setBackgroundColor(tempVibOn[0] ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));
            if (tempVibOn[0]) triggerVibrate(tempVibInt[0]); 
        });
        layout.addView(toggleVibBtn);

        final SeekBar intensityBar = createColorBar(layout, L("震动时长 / 毫秒 (拖动测试)"), tempVibInt[0]);
        intensityBar.setMax(100); 
        intensityBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                tempVibInt[0] = Math.max(1, p);
                if ((fromUser || s.hasFocus()) && tempVibOn[0]) triggerVibrate(tempVibInt[0]);
            }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        });

        layout.addView(createTitle("")); // 占位空行

        // ================= 3. 形变反馈设置 =================
        layout.addView(createTitle(L("🗜️ 按压视觉反馈控制")));
        final Button toggleFeedBtn = new Button(getContext());
        toggleFeedBtn.setText(tempFeedOn[0] ? L("按压视觉反馈：已开启") : L("按压视觉反馈：已关闭") );
        toggleFeedBtn.setTextColor(Color.WHITE);
        toggleFeedBtn.setBackgroundColor(tempFeedOn[0] ? Color.parseColor("#2196F3") : Color.parseColor("#555555"));
        toggleFeedBtn.setOnClickListener(v -> {
            tempFeedOn[0] = !tempFeedOn[0];
            toggleFeedBtn.setText(tempFeedOn[0] ? L("按压视觉反馈：已开启") : L("按压视觉反馈：已关闭"));
            toggleFeedBtn.setBackgroundColor(tempFeedOn[0] ? Color.parseColor("#2196F3") : Color.parseColor("#555555"));
        });
        layout.addView(toggleFeedBtn);

        final SeekBar scaleBar = createColorBar(layout, L("反馈幅度 (100不变, <100缩小, >100放大)"), tempFeedScale[0]);
        scaleBar.setMax(200); 
        scaleBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                tempFeedScale[0] = Math.max(10, p);
            }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        });

        // ================= 4. 保存 =================
        Button saveBtn = new Button(getContext());
        saveBtn.setText(L("💾 保存并覆盖所有按键"));
        saveBtn.setTextColor(Color.WHITE);
        saveBtn.setBackgroundColor(Color.parseColor("#D32F2F"));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(0, 40, 0, 0); saveBtn.setLayoutParams(btnParams);
        saveBtn.setOnClickListener(v -> { 
            isVibrationOn = tempVibOn[0];
            vibrationIntensity = tempVibInt[0];
            isGlobalFeedbackEnabled = tempFeedOn[0];
            globalFeedbackScaleInt = tempFeedScale[0];
            joyAlpha = tempGlobalAlpha[0];
            menuAlpha = tempGlobalAlpha[0];
            
            for (VirtualButton b : buttons) {
                b.useCustomVib = false;
                b.useCustomFeed = false;
                b.alpha = tempGlobalAlpha[0]; // 永久写入所有按键
                b.pressedEffectAlpha = tempGlobalPressedAlpha[0]; // 【新增：永久写入所有按键按下透明度】
            }
            saveConfig(); invalidate(); dialog.dismiss(); 
        });
        layout.addView(saveBtn);

        scroll.addView(layout);
        rootLayout.addView(scroll);
        dialog.setContentView(rootLayout);
        dialog.show();
    }
            
        
    

    private void showDialogCustomizationSettings() {
        // 备份当前数据，用于点击“取消”时回滚
        final int backupColor = dialogBgColor;
        final int backupAlpha = dialogBgAlpha;
        final int backupTextColor = dialogTextColor;
        final float backupTextSize = dialogTextSize;
        final String backupUri = dialogBgImageUri;
        final Bitmap backupBmp = dialogBgBitmap;

        final android.app.Dialog dialog = new android.app.Dialog(getContext(), android.R.style.Theme_DeviceDefault_Dialog);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        
        final LinearLayout rootLayout = new LinearLayout(getContext());
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackground(getCustomDialogBackground()); // 应用自定义背景

        final TextView dragHandle = new TextView(getContext());
        dragHandle.setText(L("✋ 拖拽窗口 | 🪟 全局弹窗 UI 实验室"));
                android.graphics.drawable.GradientDrawable titleBg = new android.graphics.drawable.GradientDrawable();
        titleBg.setColor(Color.argb(50, 0, 0, 0)); // 【修复1】改用半透明遮罩，完美融合下方自定义背景色
        titleBg.setCornerRadii(new float[]{35f, 35f, 35f, 35f, 0f, 0f, 0f, 0f});
        dragHandle.setBackground(titleBg); 
        dragHandle.setTextColor(dialogTextColor); // 【修复2】文字颜色跟随全局
        dragHandle.setPadding(40, 30, 40, 30); 
        dragHandle.setTextSize(dialogTextSize + 2f); // 【修复2】文字大小跟随全局(标题略大2号)
        dragHandle.setTypeface(null, Typeface.BOLD);        
        rootLayout.addView(dragHandle);

                                ScrollView scroll = new ScrollView(getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                // 【核心救命修复】：必须指明是 DynamicGamepadView.this，去拿全屏的真实宽高！
                // 绝不能让 ScrollView 拿自己还没算出来的高度 (0) 互为因果地死循环！
                int trueScreenH = Math.min(DynamicGamepadView.this.getWidth(), DynamicGamepadView.this.getHeight());
                
                // 按比例截取，留出 120px 给顶部的拖拽条
                int maxHeight = (int) (trueScreenH * dialogHeightRatio) - 120; 
                
                // 终极安全锁：防止在极端瞬间（比如横竖屏刚切换还没渲染完）高度变成负数导致崩溃
                if (maxHeight < 200) {
                    maxHeight = 200; 
                }
                
                int customHeightSpec = View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST);
                super.onMeasure(widthMeasureSpec, customHeightSpec);
            }
        };
                        
                
        
        
        LinearLayout layout = new LinearLayout(getContext()); 
        layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(50, 20, 50, 50);

        layout.addView(createTitle(L("0. 窗口全局缩放比例")));
        final SeekBar widthBar = createColorBar(layout, L("↔️ 窗口宽度百分比"), (int)(dialogWidthRatio * 100)); widthBar.setMax(100);
        final SeekBar heightBar = createColorBar(layout, L("↕️ 窗口高度百分比"), (int)(dialogHeightRatio * 100)); heightBar.setMax(100);
                        // 找到 showDialogCustomizationSettings 里面的 ratioUpdater，替换为：
                SeekBar.OnSeekBarChangeListener ratioUpdater = new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) {
                    if (s == widthBar) dialogWidthRatio = Math.max(0.4f, p / 100f);
                    else if (s == heightBar) dialogHeightRatio = Math.max(0.4f, p / 100f);
                    
                    android.view.Window window = dialog.getWindow();
                    if (window != null) {
                        // 【同步修复】：继续用真实 View 的长宽
                        int trueScreenW = Math.max(getWidth(), getHeight());
                        int targetW = (int)(trueScreenW * dialogWidthRatio);
                        
                        window.setLayout(targetW, ViewGroup.LayoutParams.WRAP_CONTENT);
                        dragHandle.setMinimumWidth(targetW); 
                        
                        // 【强制同步到底层 View】
                        View rootView = window.findViewById(android.R.id.content);
                        if (rootView != null && rootView instanceof ViewGroup && ((ViewGroup)rootView).getChildCount() > 0) {
                            View realRoot = ((ViewGroup)rootView).getChildAt(0);
                            ViewGroup.LayoutParams lp = realRoot.getLayoutParams();
                            if(lp != null) {
                                lp.width = targetW;
                                realRoot.setLayoutParams(lp);
                            }
                            realRoot.setMinimumWidth(targetW);
                        }
                    }
                    scroll.requestLayout(); 
                }
            }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        };
        
                
        widthBar.setOnSeekBarChangeListener(ratioUpdater);
        heightBar.setOnSeekBarChangeListener(ratioUpdater);

        // --- 1. 文字样式设置 ---
        layout.addView(createTitle(L("1. 全局字体大小与透明度")));
        final SeekBar sizeBar = createColorBar(layout, L("字体缩放大小"), (int)dialogTextSize); sizeBar.setMax(30);
        final SeekBar alphaBar = createColorBar(layout, L("背景不透明度 (0为全透)"), dialogBgAlpha);
        
                sizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if(fromUser) { 
                    dialogTextSize = Math.max(10f, p); 
                    dragHandle.setTextSize(dialogTextSize + 2f); // 标题单独大2号
                    refreshRealtimeUI(rootLayout); // 【新增：瞬间刷新整个弹窗的字体】
                }
            }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        });
        

        alphaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if(fromUser) { dialogBgAlpha = p; rootLayout.setBackground(getCustomDialogBackground()); } // 实时预览
            }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        });

        // --- 2. 颜色控制面板 ---
        layout.addView(createTitle(L("2. 背景纯色与文字颜色")));
        final Spinner textColorSpinner = new Spinner(getContext());
        ArrayAdapter<String> textAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, TEXT_COLOR_NAMES);
        textColorSpinner.setAdapter(textAdapter);
        for (int i=0; i<TEXT_COLOR_VALUES.length; i++) { if (dialogTextColor == TEXT_COLOR_VALUES[i]) { textColorSpinner.setSelection(i); break; } }
                textColorSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                dialogTextColor = TEXT_COLOR_VALUES[position]; 
                dragHandle.setTextColor(dialogTextColor); 
                refreshRealtimeUI(rootLayout); // 【新增：瞬间刷新整个弹窗的文字颜色】
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        
        layout.addView(textColorSpinner);

        final EditText hexInput = createEditText(L("背景颜色代码: #222222"), String.format("#%06X", (0xFFFFFF & dialogBgColor))); layout.addView(hexInput);
        final View colorPreview = new View(getContext());
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 60);
        previewParams.setMargins(0, 10, 0, 30); colorPreview.setLayoutParams(previewParams); 
        final android.graphics.drawable.GradientDrawable previewBg = new android.graphics.drawable.GradientDrawable();
        previewBg.setCornerRadius(20f); previewBg.setColor(dialogBgColor); colorPreview.setBackground(previewBg); layout.addView(colorPreview);

        final int[] rgb = {Color.red(dialogBgColor), Color.green(dialogBgColor), Color.blue(dialogBgColor)};
        final SeekBar rBar = createColorBar(layout, L("🔴 红"), rgb[0]); 
        final SeekBar gBar = createColorBar(layout, L("🟢 绿"), rgb[1]); 
        final SeekBar bBar = createColorBar(layout, L("🔵 蓝"), rgb[2]);

        SeekBar.OnSeekBarChangeListener colorUpdater = new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                rgb[0] = rBar.getProgress(); rgb[1] = gBar.getProgress(); rgb[2] = bBar.getProgress(); 
                dialogBgColor = Color.rgb(rgb[0], rgb[1], rgb[2]);
                previewBg.setColor(dialogBgColor); rootLayout.setBackground(getCustomDialogBackground());
                if(fromUser) hexInput.setText(String.format("#%06X", (0xFFFFFF & dialogBgColor)));
            }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        };
        rBar.setOnSeekBarChangeListener(colorUpdater); gBar.setOnSeekBarChangeListener(colorUpdater); bBar.setOnSeekBarChangeListener(colorUpdater);

        // --- 3. 自定义背景图 ---
        layout.addView(createTitle(L("3. 注入自定义背景图")));
        LinearLayout imgLayout = new LinearLayout(getContext()); imgLayout.setOrientation(LinearLayout.HORIZONTAL);
        Button pickImgBtn = new Button(getContext()); pickImgBtn.setText(L("🖼️ 选择图片")); pickImgBtn.setTextColor(Color.WHITE); pickImgBtn.setBackgroundColor(Color.parseColor("#4CAF50"));
        pickImgBtn.setOnClickListener(v -> {
            imagePickerTarget = 7; 
            android.app.Activity activity = (android.app.Activity) getContext(); FileActionFragment fragment = new FileActionFragment();
            android.os.Bundle args = new android.os.Bundle(); args.putInt("action_type", 0); fragment.setArguments(args); 
            activity.getFragmentManager().beginTransaction().add(fragment, "file_action").commitAllowingStateLoss();
        }); imgLayout.addView(pickImgBtn);
        
        Button clearImgBtn = new Button(getContext()); clearImgBtn.setText(L("❌ 清除图片")); clearImgBtn.setTextColor(Color.WHITE); clearImgBtn.setBackgroundColor(Color.parseColor("#F44336"));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); cp.setMargins(20, 0, 0, 0); clearImgBtn.setLayoutParams(cp);
        clearImgBtn.setOnClickListener(v -> { dialogBgImageUri = ""; dialogBgBitmap = null; rootLayout.setBackground(getCustomDialogBackground()); Toast.makeText(getContext(), L("已清除"), Toast.LENGTH_SHORT).show(); });
        imgLayout.addView(clearImgBtn); layout.addView(imgLayout);

        // --- 4. 底部三按钮 ---
        LinearLayout bottomButtons = new LinearLayout(getContext()); bottomButtons.setOrientation(LinearLayout.HORIZONTAL); bottomButtons.setPadding(0, 50, 0, 0);
        Button defaultBtn = new Button(getContext()); defaultBtn.setText(L("🔄 默认")); defaultBtn.setTextColor(Color.WHITE); defaultBtn.setBackgroundColor(Color.parseColor("#9E9E9E"));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); btnParams.setMargins(5, 0, 5, 0); defaultBtn.setLayoutParams(btnParams);
                defaultBtn.setOnClickListener(v -> {
            dialogBgColor = Color.parseColor("#222222"); dialogBgAlpha = 230; dialogTextColor = Color.WHITE; dialogTextSize = 14f; dialogBgImageUri = ""; dialogBgBitmap = null;
            dialogWidthRatio = 0.8f; dialogHeightRatio = 0.8f; // 【补充修复：强制重置窗口比例】
            saveConfig(); dialog.dismiss(); showDialogCustomizationSettings();
        }); 
        bottomButtons.addView(defaultBtn);
        

        Button cancelBtn = new Button(getContext()); cancelBtn.setText(L("❌ 取消")); cancelBtn.setTextColor(Color.WHITE); cancelBtn.setBackgroundColor(Color.parseColor("#F44336"));
        cancelBtn.setLayoutParams(btnParams);
        cancelBtn.setOnClickListener(v -> {
            dialogBgColor = backupColor; dialogBgAlpha = backupAlpha; dialogTextColor = backupTextColor; dialogTextSize = backupTextSize; dialogBgImageUri = backupUri; dialogBgBitmap = backupBmp;
            dialog.dismiss();
        }); bottomButtons.addView(cancelBtn);

        Button saveBtn = new Button(getContext()); saveBtn.setText(L("💾 保存")); saveBtn.setTextColor(Color.WHITE); saveBtn.setBackgroundColor(Color.parseColor("#1976D2"));
        saveBtn.setLayoutParams(btnParams); saveBtn.setOnClickListener(v -> { saveConfig(); dialog.dismiss(); });
        bottomButtons.addView(saveBtn); layout.addView(bottomButtons);

        scroll.addView(layout); rootLayout.addView(scroll);
        dialog.setContentView(rootLayout); setupMovableDialog(dialog, dragHandle); 
        rootLayout.getViewTreeObserver().addOnWindowFocusChangeListener(hasFocus -> { if(hasFocus) rootLayout.setBackground(getCustomDialogBackground()); });
        
        dialog.show();
    }

        private void showAutoHideSettingsDialog() {
        final android.app.Dialog dialog = new android.app.Dialog(getContext(), android.R.style.Theme_DeviceDefault_Dialog);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        
        // 1. 【核心升级】外层背景容器
        LinearLayout rootLayout = new LinearLayout(getContext());
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackground(getCustomDialogBackground());

        // 2. 【核心升级】动态滑动视图
        ScrollView scroll = new ScrollView(getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int trueScreenH = Math.min(DynamicGamepadView.this.getWidth(), DynamicGamepadView.this.getHeight());
                int maxHeight = (int) (trueScreenH * dialogHeightRatio); 
                if (maxHeight < 200) { maxHeight = 200; }
                int customHeightSpec = View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST);
                super.onMeasure(widthMeasureSpec, customHeightSpec);
            }
        };

        // 3. 实际内容容器
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 60, 60, 60);

        layout.addView(createTitle(L("⏱️ 面板自动隐藏设置")));

        // 隐藏总开关
        final Button toggleBtn = new Button(getContext());
        toggleBtn.setText(isAutoHideEnabled ? L("当前状态：已开启 (点击关闭)") : L("当前状态：已关闭 (面板长亮)"));
        toggleBtn.setTextColor(Color.WHITE);
        toggleBtn.setBackgroundColor(isAutoHideEnabled ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));
        toggleBtn.setOnClickListener(v -> {
            isAutoHideEnabled = !isAutoHideEnabled;
            toggleBtn.setText(isAutoHideEnabled ? L("当前状态：已开启 (点击关闭)") : L("当前状态：已关闭 (面板长亮)"));
            toggleBtn.setBackgroundColor(isAutoHideEnabled ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));
            if (!isAutoHideEnabled && getContext() instanceof SDLActivity) {
                ((SDLActivity) getContext()).cancelAutoHide();
            }
        });
        layout.addView(toggleBtn);

        // 延迟时间滑动条
        final SeekBar timeBar = createColorBar(layout, L("无操作自动隐藏延迟 / 秒"), autoHideSeconds);
        timeBar.setMax(60); 
        timeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                autoHideSeconds = Math.max(1, p); 
            }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        });

        Button saveBtn = new Button(getContext());
        saveBtn.setText(L("💾 保存并关闭"));
        saveBtn.setTextColor(Color.WHITE);
        saveBtn.setBackgroundColor(Color.parseColor("#1976D2"));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(0, 40, 0, 0); saveBtn.setLayoutParams(btnParams);
        saveBtn.setOnClickListener(v -> { saveConfig(); dialog.dismiss(); });
        layout.addView(saveBtn);

        // 4. 【核心升级】装载视图
        scroll.addView(layout);
        rootLayout.addView(scroll);
        dialog.setContentView(rootLayout);
        dialog.show();
    }
    
                    // =====================================
    // 各类独立设置弹窗
    // =====================================
        // 【新增工具方法】用于让设置对话框变成可移动、半透明的悬浮窗
        // 找到原来的 setupMovableDialog 方法，将其替换为以下代码：
        private void setupMovableDialog(android.app.Dialog dialog, View dragHandle) {
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            // 1. 彻底清除系统弹窗自带的各类内边距和背景黑盒限制
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); 
            window.getDecorView().setPadding(0, 0, 0, 0);
            window.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);        
            window.setDimAmount(0f); 

            // 2. 【核心修复】放弃不可靠的 DisplayMetrics，直接拿当前 View 真实渲染的长宽尺寸！
            final int trueScreenW = Math.max(getWidth(), getHeight());
            final int targetWidth = (int) (trueScreenW * dialogWidthRatio);

            // 3. 强制把 Window 设定为我们算好的精确像素宽度
            window.setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            final android.view.WindowManager.LayoutParams params = window.getAttributes();
            params.x = 50; params.y = 50; 
            params.width = targetWidth; 
            window.setAttributes(params);

            // 4. 【暴力破解系统自动折行】强制让根布局(rootLayout)的宽度撑满 targetWidth
            View rootView = window.findViewById(android.R.id.content);
            if (rootView != null && rootView instanceof ViewGroup) {
                ViewGroup contentParent = (ViewGroup) rootView;
                if (contentParent.getChildCount() > 0) {
                    View realRoot = contentParent.getChildAt(0);
                    ViewGroup.LayoutParams lp = realRoot.getLayoutParams();
                    if (lp == null) {
                        lp = new ViewGroup.LayoutParams(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
                    } else {
                        lp.width = targetWidth;
                    }
                    realRoot.setLayoutParams(lp);
                    realRoot.setMinimumWidth(targetWidth); 
                }
            }

            // 5. 【防御文字成列】给拖拽条上“免死金牌”，绝对不允许文字换行，强行撑开宽度！
            if (dragHandle instanceof TextView) {
                ((TextView) dragHandle).setSingleLine(true);
                ((TextView) dragHandle).setEllipsize(android.text.TextUtils.TruncateAt.END);
            }
            dragHandle.setMinimumWidth(targetWidth);

            // 6. 拖拽逻辑保持不变
            dragHandle.setOnTouchListener(new View.OnTouchListener() {
                float dX, dY;
                @Override
                public boolean onTouch(View view, MotionEvent event) {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            dX = event.getRawX() - params.x; dY = event.getRawY() - params.y; return true;
                        case MotionEvent.ACTION_MOVE:
                            params.x = (int) (event.getRawX() - dX); params.y = (int) (event.getRawY() - dY);
                            window.setAttributes(params); return true;
                    }
                    return false;
                }
            });
        }
    }
    
    

    private void showJoystickSettingsDialog() {
        imagePickerTarget = 0;
        final android.app.Dialog dialog = new android.app.Dialog(getContext(), android.R.style.Theme_DeviceDefault_Dialog);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        
        LinearLayout rootLayout = new LinearLayout(getContext());
        rootLayout.setOrientation(LinearLayout.VERTICAL);
                rootLayout.setBackground(getCustomDialogBackground());
        

        TextView dragHandle = new TextView(getContext());
        dragHandle.setText(L("✋ 按住此处拖拽窗口 | 🕹️ 摇杆配置"));
                android.graphics.drawable.GradientDrawable titleBg = new android.graphics.drawable.GradientDrawable();
        titleBg.setColor(Color.argb(50, 0, 0, 0)); // 【修复1】改用半透明遮罩，完美融合下方自定义背景色
        titleBg.setCornerRadii(new float[]{35f, 35f, 35f, 35f, 0f, 0f, 0f, 0f});
        dragHandle.setBackground(titleBg); 
        dragHandle.setTextColor(dialogTextColor); // 【修复2】文字颜色跟随全局
        dragHandle.setPadding(40, 30, 40, 30); 
        dragHandle.setTextSize(dialogTextSize + 2f); // 【修复2】文字大小跟随全局(标题略大2号)
        dragHandle.setTypeface(null, Typeface.BOLD);        
        rootLayout.addView(dragHandle);

                        ScrollView scroll = new ScrollView(getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                // 【核心救命修复】：必须指明是 DynamicGamepadView.this，去拿全屏的真实宽高！
                // 绝不能让 ScrollView 拿自己还没算出来的高度 (0) 互为因果地死循环！
                int trueScreenH = Math.min(DynamicGamepadView.this.getWidth(), DynamicGamepadView.this.getHeight());
                
                // 按比例截取，留出 120px 给顶部的拖拽条
                int maxHeight = (int) (trueScreenH * dialogHeightRatio) - 120; 
                
                // 终极安全锁：防止在极端瞬间（比如横竖屏刚切换还没渲染完）高度变成负数导致崩溃
                if (maxHeight < 200) {
                    maxHeight = 200; 
                }
                
                int customHeightSpec = View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST);
                super.onMeasure(widthMeasureSpec, customHeightSpec);
            }
        };
                
        
                        
                
        
        LinearLayout layout = new LinearLayout(getContext()); 
        layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(50, 20, 50, 50);
               // 【新增：摇杆位置锁定控制区】
        layout.addView(createTitle(L("0. 摇杆位置锁定设置:")));
        final Button lockBtn = new Button(getContext());
        lockBtn.setText(isJoyLocked ? L("🔒 摇杆位置：已锁定") : L("🔓 摇杆位置：未锁定"));
        lockBtn.setTextColor(Color.WHITE);
        lockBtn.setBackgroundColor(isJoyLocked ? Color.parseColor("#D32F2F") : Color.parseColor("#4CAF50"));
        lockBtn.setOnClickListener(v -> {
            isJoyLocked = !isJoyLocked;
            lockBtn.setText(isJoyLocked ? L("🔒 摇杆位置：已锁定") : L("🔓 摇杆位置：未锁定"));
            lockBtn.setBackgroundColor(isJoyLocked ? Color.parseColor("#D32F2F") : Color.parseColor("#4CAF50"));
            invalidate(); // 刷新编辑模式下的外框颜色
        });
        layout.addView(lockBtn);
        layout.addView(createTitle(L("1. 摇杆中心球颜色 (双向同步):")));
        final EditText hexInput = createEditText(L("颜色代码如: #FF5555"), String.format("#%06X", (0xFFFFFF & joyColor))); 
        layout.addView(hexInput);
        
        final View colorPreview = new View(getContext());
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 60);
        previewParams.setMargins(0, 10, 0, 30); colorPreview.setLayoutParams(previewParams); 
        final android.graphics.drawable.GradientDrawable previewBg = new android.graphics.drawable.GradientDrawable();
        previewBg.setCornerRadius(20f); previewBg.setColor(joyColor); colorPreview.setBackground(previewBg);
        layout.addView(colorPreview);

        final int[] rgb = {Color.red(joyColor), Color.green(joyColor), Color.blue(joyColor)};
        final SeekBar redBar = createColorBar(layout, L("🔴 红色分量 (R)"), rgb[0]); 
        final SeekBar greenBar = createColorBar(layout, L("🟢 绿色分量 (G)"), rgb[1]); 
        final SeekBar blueBar = createColorBar(layout, L("🔵 蓝色分量 (B)"), rgb[2]);

        hexInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                if (hexInput.hasFocus()) { 
                    try {
                        String hex = s.toString().trim();
                        if (!hex.startsWith("#")) hex = "#" + hex;
                        if (hex.length() == 7 || hex.length() == 9) {
                            int c = Color.parseColor(hex);
                            redBar.setProgress(Color.red(c)); greenBar.setProgress(Color.green(c)); blueBar.setProgress(Color.blue(c));
                        }
                    } catch (Exception e) {}
                }
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        SeekBar.OnSeekBarChangeListener colorUpdater = new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                rgb[0] = redBar.getProgress(); rgb[1] = greenBar.getProgress(); rgb[2] = blueBar.getProgress(); 
                joyColor = Color.rgb(rgb[0], rgb[1], rgb[2]);
                previewBg.setColor(joyColor); invalidate();                 
                if(fromUser) hexInput.setText(String.format("#%06X", (0xFFFFFF & joyColor))); 
            }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        };
        redBar.setOnSeekBarChangeListener(colorUpdater); greenBar.setOnSeekBarChangeListener(colorUpdater); blueBar.setOnSeekBarChangeListener(colorUpdater);

        layout.addView(createTitle(L("2. 尺寸与判定范围:")));
        final SeekBar alphaBar = createColorBar(layout, L("不透明度 (0-255)"), joyAlpha); 
        final SeekBar sizeBar = createColorBar(layout, L("摇杆视觉大小"), (int)joyRadius); sizeBar.setMax(400);
        final SeekBar hitboxBar = createColorBar(layout, L("触摸判定半径 (黄色虚线)"), (int)joyHitboxRadius); hitboxBar.setMax(500);
        
        SeekBar.OnSeekBarChangeListener sizeUpdater = new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) {
                    if (s == alphaBar) joyAlpha = p;
                    else if (s == sizeBar) joyRadius = Math.max(50f, p);
                    else if (s == hitboxBar) joyHitboxRadius = Math.max(joyRadius, p);
                    invalidate();
                }
            }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        };
        alphaBar.setOnSeekBarChangeListener(sizeUpdater); sizeBar.setOnSeekBarChangeListener(sizeUpdater); hitboxBar.setOnSeekBarChangeListener(sizeUpdater);

        layout.addView(createTitle(L("3. 自定义双层皮肤:")));
        // 外框皮肤按钮
        LinearLayout baseLayout = new LinearLayout(getContext()); baseLayout.setOrientation(LinearLayout.HORIZONTAL);
        Button btnPickBase = new Button(getContext()); btnPickBase.setText(L("🖼️ 外框皮肤")); btnPickBase.setTextColor(Color.WHITE); btnPickBase.setBackgroundColor(Color.parseColor("#4CAF50"));
        btnPickBase.setOnClickListener(v -> {
            imagePickerTarget = 1; 
            android.app.Activity activity = (android.app.Activity) getContext(); FileActionFragment fragment = new FileActionFragment();
            android.os.Bundle args = new android.os.Bundle(); args.putInt("action_type", 0); fragment.setArguments(args); 
            activity.getFragmentManager().beginTransaction().add(fragment, "file_action").commitAllowingStateLoss();
        }); baseLayout.addView(btnPickBase);
        Button btnClearBase = new Button(getContext()); btnClearBase.setText(L("❌ 清除")); btnClearBase.setTextColor(Color.WHITE); btnClearBase.setBackgroundColor(Color.parseColor("#F44336"));
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); p1.setMargins(20, 0, 0, 0); btnClearBase.setLayoutParams(p1);
        btnClearBase.setOnClickListener(v -> { joySkinBaseUri = ""; joySkinBaseBitmap = null; Toast.makeText(getContext(), L("已清除"), Toast.LENGTH_SHORT).show(); invalidate(); });
        baseLayout.addView(btnClearBase); layout.addView(baseLayout);
        
        // 中心皮肤按钮
        LinearLayout knobLayout = new LinearLayout(getContext()); knobLayout.setOrientation(LinearLayout.HORIZONTAL); knobLayout.setPadding(0, 20, 0, 0);
        Button btnPickKnob = new Button(getContext()); btnPickKnob.setText(L("🖼️ 中心皮肤")); btnPickKnob.setTextColor(Color.WHITE); btnPickKnob.setBackgroundColor(Color.parseColor("#4CAF50"));
        btnPickKnob.setOnClickListener(v -> {
            imagePickerTarget = 2; 
            android.app.Activity activity = (android.app.Activity) getContext(); FileActionFragment fragment = new FileActionFragment();
            android.os.Bundle args = new android.os.Bundle(); args.putInt("action_type", 0); fragment.setArguments(args); 
            activity.getFragmentManager().beginTransaction().add(fragment, "file_action").commitAllowingStateLoss();
        }); knobLayout.addView(btnPickKnob);
        Button btnClearKnob = new Button(getContext()); btnClearKnob.setText(L("❌ 清除")); btnClearKnob.setTextColor(Color.WHITE); btnClearKnob.setBackgroundColor(Color.parseColor("#F44336"));
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); p2.setMargins(20, 0, 0, 0); btnClearKnob.setLayoutParams(p2);
        btnClearKnob.setOnClickListener(v -> { joySkinKnobUri = ""; joySkinKnobBitmap = null; Toast.makeText(getContext(), L("已清除"), Toast.LENGTH_SHORT).show(); invalidate(); });
        knobLayout.addView(btnClearKnob); layout.addView(knobLayout);

        LinearLayout bottomButtons = new LinearLayout(getContext()); bottomButtons.setOrientation(LinearLayout.HORIZONTAL); bottomButtons.setPadding(0, 50, 0, 0);
        Button deleteBtn = new Button(getContext()); deleteBtn.setText(L("🔄 恢复默认")); deleteBtn.setTextColor(Color.WHITE); deleteBtn.setBackgroundColor(Color.parseColor("#D32F2F"));
        deleteBtn.setOnClickListener(v -> { 
            joyAlpha = 200; joyRadius = 180; joyHitboxRadius = 270; joyColor = Color.parseColor("#FF5555"); joySkinBaseUri = ""; joySkinKnobUri = ""; joySkinBaseBitmap = null; joySkinKnobBitmap = null;
            saveConfig(); invalidate(); dialog.dismiss(); 
        }); bottomButtons.addView(deleteBtn);
        
        Button saveBtn = new Button(getContext()); saveBtn.setText(L("💾 保存退出")); saveBtn.setTextColor(Color.WHITE); saveBtn.setBackgroundColor(Color.parseColor("#1976D2"));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); saveParams.setMargins(20, 0, 0, 0); saveBtn.setLayoutParams(saveParams);
        saveBtn.setOnClickListener(v -> { saveConfig(); invalidate(); dialog.dismiss(); });
        bottomButtons.addView(saveBtn); layout.addView(bottomButtons);

        scroll.addView(layout); rootLayout.addView(scroll);
        dialog.setContentView(rootLayout); setupMovableDialog(dialog, dragHandle); dialog.show();
    }
            

    private void showButtonSettingsDialog(final VirtualButton btn) {
        currentlyEditingButton = btn; imagePickerTarget = 0; // 【修正】改用新的标记变量
        final android.app.Dialog dialog = new android.app.Dialog(getContext(), android.R.style.Theme_DeviceDefault_Dialog);
    
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        LinearLayout rootLayout = new LinearLayout(getContext());
        rootLayout.setOrientation(LinearLayout.VERTICAL);       rootLayout.setBackground(getCustomDialogBackground());
        

        TextView dragHandle = new TextView(getContext());
        dragHandle.setText(L("✋ 按住此处拖拽窗口 | 🔧 配置: ") + btn.id);
               android.graphics.drawable.GradientDrawable titleBg = new android.graphics.drawable.GradientDrawable();
        titleBg.setColor(Color.argb(50, 0, 0, 0)); // 【修复1】改用半透明遮罩，完美融合下方自定义背景色
        titleBg.setCornerRadii(new float[]{35f, 35f, 35f, 35f, 0f, 0f, 0f, 0f});
        dragHandle.setBackground(titleBg); 
        dragHandle.setTextColor(dialogTextColor); // 【修复2】文字颜色跟随全局
        dragHandle.setPadding(40, 30, 40, 30); 
        dragHandle.setTextSize(dialogTextSize + 2f); // 【修复2】文字大小跟随全局(标题略大2号)
        dragHandle.setTypeface(null, Typeface.BOLD);       
        rootLayout.addView(dragHandle);

                                        ScrollView scroll = new ScrollView(getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                // 【核心救命修复】：必须指明是 DynamicGamepadView.this，去拿全屏的真实宽高！
                // 绝不能让 ScrollView 拿自己还没算出来的高度 (0) 互为因果地死循环！
                int trueScreenH = Math.min(DynamicGamepadView.this.getWidth(), DynamicGamepadView.this.getHeight());
                
                // 按比例截取，留出 120px 给顶部的拖拽条
                int maxHeight = (int) (trueScreenH * dialogHeightRatio) - 120; 
                
                // 终极安全锁：防止在极端瞬间（比如横竖屏刚切换还没渲染完）高度变成负数导致崩溃
                if (maxHeight < 200) {
                    maxHeight = 200; 
                }
                
                int customHeightSpec = View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST);
                super.onMeasure(widthMeasureSpec, customHeightSpec);
            }
        };
                                
                        
                
        
        LinearLayout layout = new LinearLayout(getContext()); 
        layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(50, 20, 50, 50);

        layout.addView(createTitle(L("0. 按键位置锁定:")));
        Button lockBtn = new Button(getContext());
        lockBtn.setText(btn.isLocked ? L("🔒 位置已锁定 (不可拖动)") : L("🔓 位置未锁定 (可拖动)"));
        lockBtn.setTextColor(Color.WHITE); lockBtn.setBackgroundColor(btn.isLocked ? Color.parseColor("#D32F2F") : Color.parseColor("#4CAF50"));
        lockBtn.setOnClickListener(v -> { btn.isLocked = !btn.isLocked; lockBtn.setText(btn.isLocked ? L("🔒 位置已锁定 (不可拖动)") : L("🔓 位置未锁定 (可拖动)")); lockBtn.setBackgroundColor(btn.isLocked ? Color.parseColor("#D32F2F") : Color.parseColor("#4CAF50")); invalidate(); });
        layout.addView(lockBtn);

        layout.addView(createTitle(L("1. 按键名称与映射:")));
        
        // 【新增】显示绑定的物理手柄键值
        TextView padInfo = new TextView(getContext());
        padInfo.setText(L("🎮 已绑定物理手柄键值: ") + (btn.boundGamepadKeyCode != 0 ? btn.boundGamepadKeyCode : L("未绑定")));
        padInfo.setTextColor(Color.parseColor("#FF9800")); padInfo.setTextSize(dialogTextSize); padInfo.setPadding(0,0,0,10);
        layout.addView(padInfo);

        final EditText inputName = createEditText(L("显示名称 (如: A)"), btn.id); layout.addView(inputName);
        final EditText inputKey = createEditText(L("映射引擎键值 (如: DOWN/A)"), btn.keyMapStr); layout.addView(inputKey);

        // 【新增】如果是系统基础预设按键，则强制锁定底层键值映射，不允许修改，只能改外观
        boolean isPreset = btn.id.equals("A") || btn.id.equals("B") || btn.id.equals("C") || btn.id.equals("X") || btn.id.equals("Y") || btn.id.equals("Z") || btn.id.equals("ESC") || btn.id.equals("START") || btn.id.equals("UP") || btn.id.equals("DOWN") || btn.id.equals("LEFT") || btn.id.equals("RIGHT");
        if (isPreset) {
            inputKey.setEnabled(false);
            inputKey.setBackgroundColor(Color.parseColor("#555555")); // 变暗暗示锁定
            inputKey.setHint(L("核心预设按键，映射键值已锁定"));
        }


        layout.addView(createTitle(L("2. 按键样式:")));
        final Spinner textColorSpinner = new Spinner(getContext());
        ArrayAdapter<String> textAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, TEXT_COLOR_NAMES);
        textColorSpinner.setAdapter(textAdapter);
        for (int i=0; i<TEXT_COLOR_VALUES.length; i++) { if (btn.textColor == TEXT_COLOR_VALUES[i]) { textColorSpinner.setSelection(i); break; } }
        layout.addView(textColorSpinner);

        final Spinner shapeSpinner = new Spinner(getContext());
        ArrayAdapter<String> shapeAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, SHAPE_NAMES);
        shapeSpinner.setAdapter(shapeAdapter); shapeSpinner.setSelection(btn.shape); layout.addView(shapeSpinner);

        layout.addView(createTitle(L("3. 按键颜色 (代码与滑块双向同步):")));
        final EditText hexInput = createEditText(L("颜色代码如: #FF0000"), String.format("#%06X", (0xFFFFFF & btn.color))); 
        layout.addView(hexInput);

        final View colorPreview = new View(getContext());
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 60);
        previewParams.setMargins(0, 10, 0, 30); colorPreview.setLayoutParams(previewParams); 
        final android.graphics.drawable.GradientDrawable previewBg = new android.graphics.drawable.GradientDrawable();
        previewBg.setCornerRadius(20f); previewBg.setColor(btn.color); colorPreview.setBackground(previewBg);
        layout.addView(colorPreview);

        final int[] rgb = {Color.red(btn.color), Color.green(btn.color), Color.blue(btn.color)};
        final SeekBar redBar = createColorBar(layout, L("🔴 红色分量 (R)"), rgb[0]); 
        final SeekBar greenBar = createColorBar(layout, L("🟢 绿色分量 (G)"), rgb[1]); 
        final SeekBar blueBar = createColorBar(layout, L("🔵 蓝色分量 (B)"), rgb[2]);

        hexInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                if (hexInput.hasFocus()) {
                    try {
                        String hex = s.toString().trim();
                        if (!hex.startsWith("#")) hex = "#" + hex;
                        if (hex.length() == 7 || hex.length() == 9) {
                            int c = Color.parseColor(hex);
                            redBar.setProgress(Color.red(c)); greenBar.setProgress(Color.green(c)); blueBar.setProgress(Color.blue(c));
                        }
                    } catch (Exception e) {}
                }
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });
        
        SeekBar.OnSeekBarChangeListener colorUpdater = new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                rgb[0] = redBar.getProgress(); rgb[1] = greenBar.getProgress(); rgb[2] = blueBar.getProgress(); 
                int newColor = Color.rgb(rgb[0], rgb[1], rgb[2]);
                previewBg.setColor(newColor); 
                btn.color = newColor;         
                invalidate();                 
                if(fromUser) hexInput.setText(String.format("#%06X", (0xFFFFFF & newColor)));
            }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        };
        redBar.setOnSeekBarChangeListener(colorUpdater); greenBar.setOnSeekBarChangeListener(colorUpdater); blueBar.setOnSeekBarChangeListener(colorUpdater);

        layout.addView(createTitle(L("4. 尺寸与隐藏参数:")));
        final SeekBar alphaBar = createColorBar(layout, L("可见透明度 (拉到0为隐藏)"), btn.alpha); 
        final SeekBar sizeBar = createColorBar(layout, L("视觉大小"), (int)btn.radius); sizeBar.setMax(300);
        final SeekBar hitboxBar = createColorBar(layout, L("边缘触控灵敏度/范围 (黄线圈)"), (int)btn.hitboxRadius); hitboxBar.setMax(600); // 【优化4】重命名并扩大上限

        SeekBar.OnSeekBarChangeListener sizeUpdater = new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) {
                    if (s == alphaBar) btn.alpha = p;
                    else if (s == sizeBar) btn.radius = Math.max(40f, p);
                    else if (s == hitboxBar) btn.hitboxRadius = Math.max(btn.radius, p);
                    invalidate();
                }
            }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        };
        alphaBar.setOnSeekBarChangeListener(sizeUpdater); sizeBar.setOnSeekBarChangeListener(sizeUpdater); hitboxBar.setOnSeekBarChangeListener(sizeUpdater);

        layout.addView(createTitle(L("5. 自定义图片皮肤:")));
        LinearLayout skinLayout = new LinearLayout(getContext()); skinLayout.setOrientation(LinearLayout.HORIZONTAL);
                Button btnPickImage = new Button(getContext()); btnPickImage.setText(L("🖼️ 选择皮肤")); btnPickImage.setTextColor(Color.WHITE); btnPickImage.setBackgroundColor(Color.parseColor("#4CAF50"));
        btnPickImage.setOnClickListener(v -> {
            // 【新增这一句】，告诉回调函数这是普通按键在选图片
            imagePickerTarget = 3; currentlyEditingButton = btn; 
            android.app.Activity activity = (android.app.Activity) getContext(); FileActionFragment fragment = new FileActionFragment();
            android.os.Bundle args = new android.os.Bundle(); args.putInt("action_type", 0);
            fragment.setArguments(args); activity.getFragmentManager().beginTransaction().add(fragment, "file_action").commitAllowingStateLoss();
        }); skinLayout.addView(btnPickImage);
        
        
        Button btnClearImage = new Button(getContext()); btnClearImage.setText(L("❌ 移除皮肤")); btnClearImage.setTextColor(Color.WHITE); btnClearImage.setBackgroundColor(Color.parseColor("#F44336"));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); btnParams.setMargins(20, 0, 0, 0); btnClearImage.setLayoutParams(btnParams);
        btnClearImage.setOnClickListener(v -> { btn.customImageUri = ""; btn.skinBitmap = null; Toast.makeText(getContext(), L("已恢复默认材质"), Toast.LENGTH_SHORT).show(); invalidate(); });
                skinLayout.addView(btnClearImage); layout.addView(skinLayout);
                
                        layout.addView(createTitle(L("7. 连发功能 (Turbo):")));
        final Button turboBtn = new Button(getContext());
        turboBtn.setText(btn.isTurbo ? L("🔥 连发状态：已开启") : L("⚪ 连发状态：已关闭"));
        turboBtn.setTextColor(Color.WHITE);
        turboBtn.setBackgroundColor(btn.isTurbo ? Color.parseColor("#FF5722") : Color.parseColor("#555555"));
        turboBtn.setOnClickListener(v -> {
            btn.isTurbo = !btn.isTurbo;
            turboBtn.setText(btn.isTurbo ? L("🔥 连发状态：已开启") : L("⚪ 连发状态：已关闭"));
            turboBtn.setBackgroundColor(btn.isTurbo ? Color.parseColor("#FF5722") : Color.parseColor("#555555"));
        });
        layout.addView(turboBtn);

        final SeekBar turboIntervalBar = createColorBar(layout, L("⏱️ 连发触发间隔 (毫秒)"), btn.turboInterval);
        turboIntervalBar.setMax(500);
        turboIntervalBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) btn.turboInterval = Math.max(10, p); // 最小间隔10ms防卡死
            }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        });
        
        // ================= 【新增：独立高阶属性区】 =================
        layout.addView(createTitle(L("8. 独立高级控制 (文字/震动/反馈):")));
        
        final SeekBar txtSizeBar = createColorBar(layout, L("🅰️ 字体大小百分比 (默认 100%)"), btn.textSizeFactor);
        txtSizeBar.setMax(300);
        
        // 【新增】：拖动滑块实时刷新字体大小
        txtSizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) { btn.textSizeFactor = Math.max(10, p); invalidate(); }
            }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        });

        // 独立震动开关与滑块
        final Button useVibBtn = new Button(getContext());
        useVibBtn.setText(btn.useCustomVib ? L("📳 此键震动：独立配置 (点击跟随全局)") : L("📳 此键震动：跟随全局配置 (点击独立)"));
        useVibBtn.setTextColor(Color.WHITE);
        useVibBtn.setBackgroundColor(btn.useCustomVib ? Color.parseColor("#4CAF50") : Color.parseColor("#555555"));
        
        final SeekBar customVibBar = createColorBar(layout, L("  └─ 独立震动时长 (毫秒)"), btn.customVib);
        customVibBar.setMax(100);
        customVibBar.setVisibility(btn.useCustomVib ? View.VISIBLE : View.GONE); 
        
        // 【新增】：拖动震动条时，实时触发独立震动预览
        customVibBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser || s.hasFocus()) {
                    btn.customVib = Math.max(1, p);
                    if (btn.useCustomVib) triggerVibrate(btn.customVib);
                }
            }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        });

        useVibBtn.setOnClickListener(v -> {
            btn.useCustomVib = !btn.useCustomVib;
            useVibBtn.setText(btn.useCustomVib ? L("📳 此键震动：独立配置 (点击跟随全局)") : L("📳 此键震动：跟随全局配置 (点击独立)"));
            useVibBtn.setBackgroundColor(btn.useCustomVib ? Color.parseColor("#4CAF50") : Color.parseColor("#555555"));
            customVibBar.setVisibility(btn.useCustomVib ? View.VISIBLE : View.GONE);
            if (btn.useCustomVib) triggerVibrate(btn.customVib); // 开启瞬间给个测试震动
        });
        layout.addView(useVibBtn);

        // 独立反馈开关与滑块
        final Button useFeedBtn = new Button(getContext());
        useFeedBtn.setText(btn.useCustomFeed ? L("🗜️ 按压形变：独立配置 (点击跟随全局)") : L("🗜️ 按压形变：跟随全局配置 (点击独立)"));
        useFeedBtn.setTextColor(Color.WHITE);
        useFeedBtn.setBackgroundColor(btn.useCustomFeed ? Color.parseColor("#2196F3") : Color.parseColor("#555555"));
        
        final SeekBar customFeedBar = createColorBar(layout, L("  └─ 独立形变比例 (按住拖动看画布预览)"), btn.customFeedScale);
        customFeedBar.setMax(200);
        customFeedBar.setVisibility(btn.useCustomFeed ? View.VISIBLE : View.GONE);

        // 【新增】：按下反馈比例实时预览引擎
        customFeedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) { btn.customFeedScale = Math.max(10, p); invalidate(); }
            }
            public void onStartTrackingTouch(SeekBar s) { 
                btn.isPressed = true; // 按住滑块时，强行让画布进入“按下态”以预览反馈大小与发光特效
                invalidate(); 
            } 
            public void onStopTrackingTouch(SeekBar s) { 
                btn.isPressed = false; // 松开滑块时恢复静态
                invalidate(); 
            }
        });

        useFeedBtn.setOnClickListener(v -> {
            btn.useCustomFeed = !btn.useCustomFeed;
            useFeedBtn.setText(btn.useCustomFeed ? L("🗜️ 按压形变：独立配置 (点击跟随全局)") : L("🗜️ 按压形变：跟随全局配置 (点击独立)"));
            useFeedBtn.setBackgroundColor(btn.useCustomFeed ? Color.parseColor("#2196F3") : Color.parseColor("#555555"));
            customFeedBar.setVisibility(btn.useCustomFeed ? View.VISIBLE : View.GONE);
        });
        layout.addView(useFeedBtn);        

            


        // ================= 【新增：按下状态的UI调节控制】 =================
                // ================= 【修改：增加按下特效的实时颜色预览】 =================
        layout.addView(createTitle(L("6. 按下状态特效 (独立颜色与皮肤):")));
        final EditText hexInputP = createEditText(L("颜色如: #4CAF50 (填 #000000 变回普通渐变)"), String.format("#%06X", (0xFFFFFF & btn.pressedEffectColor))); 
        layout.addView(hexInputP);
        
        final View colorPreviewP = new View(getContext());
        LinearLayout.LayoutParams previewParamsP = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 60);
        previewParamsP.setMargins(0, 10, 0, 30); colorPreviewP.setLayoutParams(previewParamsP); 
        final android.graphics.drawable.GradientDrawable previewBgP = new android.graphics.drawable.GradientDrawable();
        previewBgP.setCornerRadius(20f); 
        // 0代表没开启颜色特效，用深灰色暗示未启用
        previewBgP.setColor(btn.pressedEffectColor == 0 ? Color.parseColor("#333333") : btn.pressedEffectColor); 
        colorPreviewP.setBackground(previewBgP);
        layout.addView(colorPreviewP);

        final SeekBar alphaBarP = createColorBar(layout, L("按下特效不透明度"), btn.pressedEffectAlpha); 
        

        final int[] rgbP = {Color.red(btn.pressedEffectColor), Color.green(btn.pressedEffectColor), Color.blue(btn.pressedEffectColor)};
        final SeekBar rBarP = createColorBar(layout, L("🔴 按下红 (R)"), rgbP[0]); 
        final SeekBar gBarP = createColorBar(layout, L("🟢 按下绿 (G)"), rgbP[1]); 
        final SeekBar bBarP = createColorBar(layout, L("🔵 按下蓝 (B)"), rgbP[2]);

        hexInputP.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                if (hexInputP.hasFocus()) {
                    try {
                        String hex = s.toString().trim();
                        if (!hex.startsWith("#")) hex = "#" + hex;
                        if (hex.length() == 7 || hex.length() == 9) {
                            btn.pressedEffectColor = Color.parseColor(hex); invalidate();
                            rBarP.setProgress(Color.red(btn.pressedEffectColor)); gBarP.setProgress(Color.green(btn.pressedEffectColor)); bBarP.setProgress(Color.blue(btn.pressedEffectColor));
                        }
                    } catch (Exception e) {}
                }
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

                SeekBar.OnSeekBarChangeListener colorUpdaterP = new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                rgbP[0] = rBarP.getProgress(); rgbP[1] = gBarP.getProgress(); rgbP[2] = bBarP.getProgress(); 
                int newColor = Color.rgb(rgbP[0], rgbP[1], rgbP[2]);
                btn.pressedEffectColor = newColor; invalidate();
                previewBgP.setColor(newColor == 0 ? Color.parseColor("#333333") : newColor); // 同步刷新预览块
                if(fromUser) hexInputP.setText(String.format("#%06X", (0xFFFFFF & newColor)));
            }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        };
        rBarP.setOnSeekBarChangeListener(colorUpdaterP); gBarP.setOnSeekBarChangeListener(colorUpdaterP); bBarP.setOnSeekBarChangeListener(colorUpdaterP);
        
        alphaBarP.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) { btn.pressedEffectAlpha = p; invalidate(); }
            }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        });

        LinearLayout skinLayoutP = new LinearLayout(getContext()); skinLayoutP.setOrientation(LinearLayout.HORIZONTAL);
        Button btnPickImageP = new Button(getContext()); btnPickImageP.setText(L("🖼️ 按下皮肤")); btnPickImageP.setTextColor(Color.WHITE); btnPickImageP.setBackgroundColor(Color.parseColor("#4CAF50"));
        btnPickImageP.setOnClickListener(v -> {
            imagePickerTarget = 6; currentlyEditingButton = btn; 
            android.app.Activity activity = (android.app.Activity) getContext(); FileActionFragment fragment = new FileActionFragment();
            android.os.Bundle args = new android.os.Bundle(); args.putInt("action_type", 0);
            fragment.setArguments(args); activity.getFragmentManager().beginTransaction().add(fragment, "file_action").commitAllowingStateLoss();
        }); skinLayoutP.addView(btnPickImageP);
        
        Button btnClearImageP = new Button(getContext()); btnClearImageP.setText(L("❌ 移除按下皮肤")); btnClearImageP.setTextColor(Color.WHITE); btnClearImageP.setBackgroundColor(Color.parseColor("#F44336"));
        LinearLayout.LayoutParams btnParamsP = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); btnParamsP.setMargins(20, 0, 0, 0); btnClearImageP.setLayoutParams(btnParamsP);
        btnClearImageP.setOnClickListener(v -> { btn.customPressedUri = ""; btn.pressedSkinBitmap = null; Toast.makeText(getContext(), L("已恢复无皮肤状态"), Toast.LENGTH_SHORT).show(); invalidate(); });
        skinLayoutP.addView(btnClearImageP); layout.addView(skinLayoutP);
        // =========================================================================

        LinearLayout bottomButtons = new LinearLayout(getContext()); bottomButtons.setOrientation(LinearLayout.HORIZONTAL); bottomButtons.setPadding(0, 50, 0, 0);
        Button deleteBtn = new Button(getContext()); deleteBtn.setText(L("🗑️ 删除按键")); deleteBtn.setTextColor(Color.WHITE); deleteBtn.setBackgroundColor(Color.parseColor("#D32F2F"));
                deleteBtn.setOnClickListener(v -> { 
            btn.stopTurbo(); btn.isMacroPlaying = false; // 修复：先斩断后台独立线程
            buttons.remove(btn); 
            saveConfig(); invalidate(); dialog.dismiss(); 
        });
        
        bottomButtons.addView(deleteBtn);
        
        Button saveBtn = new Button(getContext()); saveBtn.setText(L("💾 保存修改并退出")); saveBtn.setTextColor(Color.WHITE); saveBtn.setBackgroundColor(Color.parseColor("#1976D2"));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); saveParams.setMargins(20, 0, 0, 0); saveBtn.setLayoutParams(saveParams);
        
        // ================= 【新增：第4点要求 - 高级拦截防误触保护】 =================
        // 1. 获取进入设置前的全部原始数据，用来做“取消恢复”的回滚准备
        final String origName = btn.id;
        final String origKey = btn.keyMapStr;
        final int origColor = btn.color;
        final int origAlpha = btn.alpha;
        final float origRadius = btn.radius;
        final float origHitbox = btn.hitboxRadius;
        final String origSkin = btn.customImageUri;
        final String origPressedSkin = btn.customPressedUri;
        final int origPressedColor = btn.pressedEffectColor;
        final int origPressedAlpha = btn.pressedEffectAlpha;
        final boolean origTurbo = btn.isTurbo;
        final int origTurboInterval = btn.turboInterval;
        final int origSizeFactor = btn.textSizeFactor;
        final boolean origUseVib = btn.useCustomVib;
        final int origVib = btn.customVib;
        final boolean origUseFeed = btn.useCustomFeed;
        final int origFeedScale = btn.customFeedScale;
        final int origShape = btn.shape;
        final boolean origLocked = btn.isLocked; // 【修复备份：备份此按键的初始锁定状态】

        // 2. 将普通的“点击保存”标记起来，防止它也触发警告
        final boolean[] isNormalSave = {false};
        
        saveBtn.setOnClickListener(v -> {
            isNormalSave[0] = true; // 标记为正常保存操作
            
            btn.id = inputName.getText().toString(); 
            btn.displayLines = btn.id.split("\n"); 
            btn.textColor = TEXT_COLOR_VALUES[textColorSpinner.getSelectedItemPosition()];
            int oldShape = btn.shape; btn.shape = shapeSpinner.getSelectedItemPosition(); 
            if (oldShape != btn.shape && joystickMode == JOYSTICK_MODE_STYLE && currentStyleIndex < styleList.size()) {
                 GamepadStyle currentTheme = styleList.get(currentStyleIndex);
                 if (btn.shape == SHAPE_CIRCLE) btn.customImageUri = currentTheme.btnNormalUri;
                 else btn.customImageUri = (currentTheme.btnSquareUri != null && !currentTheme.btnSquareUri.isEmpty()) ? currentTheme.btnSquareUri : "";
                 btn.loadSkinFromUri(getContext());
            }
            btn.keyMapStr = inputKey.getText().toString().trim().toUpperCase(); btn.parseKeyCodes(); 
            btn.textSizeFactor = Math.max(10, txtSizeBar.getProgress()); btn.customVib = Math.max(0, customVibBar.getProgress()); btn.customFeedScale = Math.max(10, customFeedBar.getProgress());
            saveConfig(); invalidate(); dialog.dismiss();
        });

        bottomButtons.addView(saveBtn); layout.addView(bottomButtons);

        scroll.addView(layout); rootLayout.addView(scroll);
        dialog.setContentView(rootLayout); setupMovableDialog(dialog, dragHandle); 
        
        // 3. 开启弹窗的“外部点击取消”拦截系统
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnCancelListener(d -> {
            if (!isNormalSave[0]) {
                // 如果是用户点击了外部区域（或返回键），拦截关闭并弹出警告
                new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(L("⚠️ 未保存的更改"))
                    .setMessage(L("您点击了设置窗口外部，是否要保存刚刚做出的修改？"))
                    .setPositiveButton(L("💾 马上保存"), (dialogInterface, i) -> {
                        saveBtn.performClick(); // 直接触发上面写好的保存按钮
                    })
                    .setNeutralButton(L("🔙 返回继续修改"), (dialogInterface, i) -> {
                        dialog.show(); // 拦截操作，重新把刚刚关掉的弹窗显示回来
                    })
                    .setNegativeButton(L("🗑️ 不保存(复原)"), (dialogInterface, i) -> {
                        // 触发灾难恢复：把上面备份的初始数据强行覆盖回来
                        btn.id = origName; btn.displayLines = btn.id.split("\n");
                        btn.keyMapStr = origKey; btn.parseKeyCodes();
                        btn.color = origColor; btn.alpha = origAlpha;
                        btn.radius = origRadius; btn.hitboxRadius = origHitbox;
                        btn.customImageUri = origSkin; btn.customPressedUri = origPressedSkin;
                        btn.pressedEffectColor = origPressedColor; btn.pressedEffectAlpha = origPressedAlpha;
                        btn.isTurbo = origTurbo; btn.turboInterval = origTurboInterval;
                        btn.textSizeFactor = origSizeFactor; btn.useCustomVib = origUseVib; btn.customVib = origVib;
                        btn.useCustomFeed = origUseFeed; btn.customFeedScale = origFeedScale; btn.shape = origShape;
                        btn.isLocked = origLocked; // 【灾难恢复时精准还原锁定状态】
                        btn.loadSkinFromUri(getContext());
                        invalidate(); // 画布还原
                    }).show();
            }
        });
        
        dialog.show();
    }        
    
        // =====================================
    // 补回被误删的 UI 绘制辅助方法
    // =====================================

            private EditText createEditText(String hint, String text) {
        EditText et = new EditText(getContext());
        et.setHint(hint);
        et.setText(text);
        et.setTextColor(Color.BLACK); 
        et.setHintTextColor(Color.GRAY);
        et.setTextSize(dialogTextSize);
        
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(15f);
        bg.setStroke(3, Color.parseColor("#999999"));
        et.setBackground(bg);
        
        et.setPadding(20, 15, 20, 15); // 【修复】大幅缩小巨型内边距
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 5, 0, 10); // 【修复】缩小外边距
        et.setLayoutParams(params);
        return et;
    }
        
    
          private TextView createTitle(String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(dialogTextSize); // 标题稍微比基础字体小一点或者你自己定
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(dialogTextColor); // 动态应用颜色
        tv.setPadding(0, 15, 0, 5);
        return tv;
    }
      
    

        // 替换原代码中底部的 createColorBar 方法
    private SeekBar createColorBar(LinearLayout parent, String label, int progress) {
        // 1. 创建横向容器包裹标题和输入框
        LinearLayout headerLayout = new LinearLayout(getContext());
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);
        
        TextView tv = new TextView(getContext());
        tv.setText(label);
        tv.setTextColor(dialogTextColor); // 【修复】跟随全局颜色
        tv.setTextSize(dialogTextSize);   // 【修复】跟随全局字体大小
        tv.setPadding(0, 10, 0, 0);
        LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tv.setLayoutParams(tvParams);
        
        final EditText input = new EditText(getContext());
        input.setText(String.valueOf(progress));
        input.setTextColor(Color.BLACK);
        input.setTextSize(dialogTextSize); // 【修复】跟随全局字体大小
        
        input.setPadding(20, 10, 20, 10);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setGravity(android.view.Gravity.CENTER);
        
        // UI美化：加个白底圆角边框
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(15f);
        input.setBackground(bg);
        
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(180, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputParams.setMargins(10, 10, 0, 0);
        input.setLayoutParams(inputParams);
        
        headerLayout.addView(tv);
        headerLayout.addView(input);
        parent.addView(headerLayout);
        
               // 3. 使用匿名内部类重写 SeekBar 的事件，实现“隐形”双向绑定
        final SeekBar sb = new SeekBar(getContext(), null, android.R.attr.seekBarStyle) {        
            private OnSeekBarChangeListener extListener;
            
            @Override
            public void setOnSeekBarChangeListener(OnSeekBarChangeListener l) {
                // 拦截外部原本要挂载的监听器
                this.extListener = l;
            }
            
            {
                // 自己内部先处理一遍数据同步
                super.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int p, boolean fromUser) {
                        // 只要不是用户正在手动改数字，就让数字跟着滑块变
                        if (!input.hasFocus()) {
                            input.setText(String.valueOf(p));
                        }
                        
                        if (extListener != null) {
                            // 核心判定：不管是拖动滑块(fromUser)，还是直接输入数字(input.hasFocus)，都算作有效修改
                            boolean isUserAction = fromUser || input.hasFocus();
                            extListener.onProgressChanged(seekBar, p, isUserAction);
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        if (extListener != null) extListener.onStartTrackingTouch(seekBar);
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        if (extListener != null) extListener.onStopTrackingTouch(seekBar);
                    }
                });
            }
        };
        
        sb.setMax(255);
        sb.setProgress(progress);
        sb.setPadding(30, 20, 30, 30);        
                // 【修复老旧机型滑动条消失的 Bug】：强制指定宽度占满父容器
        LinearLayout.LayoutParams sbParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        sb.setLayoutParams(sbParams);

        parent.addView(sb);
        
        // 4. 监听输入框变化，反向驱动滑块
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                if (input.hasFocus()) {
                    try {
                        int val = Integer.parseInt(s.toString());
                        // 如果输入的数值大于滑块当前的上限（比如 300 强制填了 500），自动扩容防止卡死
                        if (val > sb.getMax()) {
                            sb.setMax(val); 
                        }
                        sb.setProgress(val);
                    } catch (NumberFormatException e) {}
                }
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });
        
        return sb;
    }
      // ================= 修复：UI 实时预览刷新引擎 (解决标题栏覆盖问题) =================
    private void refreshRealtimeUI(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof TextView) {
                TextView tv = (TextView) child;
                if (!(tv instanceof Button) && !(tv instanceof EditText)) {
                    tv.setTextColor(dialogTextColor); // 更新颜色
                }
                // 智能判断：如果是顶部的拖拽标题栏(包含✋)，维持大2号字体；否则更新为全局常规字体
                if (tv.getText().toString().contains("✋")) {
                    tv.setTextSize(dialogTextSize + 2f);
                } else {
                    tv.setTextSize(dialogTextSize); 
                }
            }
            if (child instanceof ViewGroup) {
                refreshRealtimeUI((ViewGroup) child); // 递归遍历
            }
        }
    }



    // =====================================
    // 核心持久化逻辑 (补全以下方法以修复编译错误)
    // =====================================

    /**
     * 保存当前布局方案到 SharedPreferences
     */
        public void saveConfig() {
        try {
            SharedPreferences.Editor editor = prefs.edit();
            JSONArray array = new JSONArray();
            for (VirtualButton btn : buttons) {
                JSONObject obj = new JSONObject();
                obj.put("id", btn.id); obj.put("cx", btn.cx); obj.put("cy", btn.cy);
                obj.put("radius", btn.radius); obj.put("color", btn.color); obj.put("alpha", btn.alpha);
                obj.put("textColor", btn.textColor); obj.put("shape", btn.shape);
                obj.put("keyMap", btn.keyMapStr); obj.put("isDir", btn.isDirectional);
                obj.put("skin", btn.customImageUri); 
                obj.put("hitboxRadius", btn.hitboxRadius);
                obj.put("isLocked", btn.isLocked);
                // 【新增：保存按下状态特效】
                obj.put("pressedSkin", btn.customPressedUri);
                obj.put("pressedColor", btn.pressedEffectColor);
                obj.put("pressedAlpha", btn.pressedEffectAlpha);
                obj.put("boundPadCode", btn.boundGamepadKeyCode);
                obj.put("isTurbo", btn.isTurbo);
                obj.put("turboInterval", btn.turboInterval);
                // 【新增：保存独立高阶设置】
                obj.put("textSizeFactor", btn.textSizeFactor);
                obj.put("useCustomVib", btn.useCustomVib);
                obj.put("customVib", btn.customVib);
                obj.put("useCustomFeed", btn.useCustomFeed);
                obj.put("customFeedScale", btn.customFeedScale);
                array.put(obj);
            }
            editor.putString(KEY_LAYOUT_PREFIX + currentSlot, array.toString());
            // 【新增：保存全局反馈设置】
            editor.putBoolean("GlobalFeedbackOn_" + currentSlot, isGlobalFeedbackEnabled);
            editor.putInt("GlobalFeedbackScale_" + currentSlot, globalFeedbackScaleInt);
                
            editor.putInt("JoystickMode_" + currentSlot, joystickMode);
            editor.putFloat("JoyX_" + currentSlot, joyBaseX);
            editor.putFloat("JoyY_" + currentSlot, joyBaseY);
            editor.putFloat("JoyR_" + currentSlot, joyRadius);
            editor.putFloat("JoyHitR_" + currentSlot, joyHitboxRadius);
            editor.putInt("JoyA_" + currentSlot, joyAlpha);
            editor.putInt("JoyColor_" + currentSlot, joyColor);
            editor.putBoolean("JoyLocked_" + currentSlot, isJoyLocked);
            editor.putString("JoySkinBase_" + currentSlot, joySkinBaseUri);
            editor.putString("JoySkinKnob_" + currentSlot, joySkinKnobUri);
            editor.putBoolean("Vibration_" + currentSlot, isVibrationOn);
            editor.putInt("VibIntensity_" + currentSlot, vibrationIntensity);
            editor.putFloat("MenuX", menuX); editor.putFloat("MenuY", menuY);
            editor.putFloat("MenuScale", menuScale); editor.putInt("MenuAlpha", menuAlpha);
            editor.putInt("OverlayMode_" + currentSlot, overlayMode);
            editor.putString("OverlayUri1_" + currentSlot, overlayUri1);
            editor.putFloat("OverlayX1_" + currentSlot, overlayX1);
            editor.putFloat("OverlayY1_" + currentSlot, overlayY1);
                        editor.putFloat("OverlayScaleX1_" + currentSlot, overlayScaleX1);
            editor.putFloat("OverlayScaleY1_" + currentSlot, overlayScaleY1);
            editor.putFloat("OverlayCurv1_" + currentSlot, overlayCurvature1);
            editor.putString("OverlayUri2_" + currentSlot, overlayUri2);
            editor.putFloat("OverlayX2_" + currentSlot, overlayX2);
            editor.putFloat("OverlayY2_" + currentSlot, overlayY2);
            editor.putFloat("OverlayScaleX2_" + currentSlot, overlayScaleX2);
            editor.putFloat("OverlayScaleY2_" + currentSlot, overlayScaleY2);
            editor.putFloat("OverlayCurv2_" + currentSlot, overlayCurvature2);           
            editor.putFloat("OverlayRot1_" + currentSlot, overlayRotation1);
            editor.putFloat("OverlayRot2_" + currentSlot, overlayRotation2);
            editor.putBoolean("FS_HideOverlay_" + currentSlot, isFullscreenHideOverlay);
            editor.putBoolean("AutoHide_" + currentSlot, isAutoHideEnabled);
editor.putInt("AutoHideSec_" + currentSlot, autoHideSeconds);
            editor.putFloat("PadDeadzone_" + currentSlot, gamepadDeadzone);
            editor.putInt("PadUIMode_" + currentSlot, gamepadUIMode);
             editor.putBoolean("PadVib_" + currentSlot, isGamepadVibrationOn);
            editor.putInt("GridSize_" + currentSlot, gridSize);
            // 【新增：保存网格与背景配置】
            editor.putInt("GridLineColor_" + currentSlot, gridLineColor);
            editor.putInt("GridLineAlpha_" + currentSlot, gridLineAlpha);
            editor.putInt("GridBgColor_" + currentSlot, gridBgColor);
            
            editor.putInt("CurrentStyleIndex_" + currentSlot, currentStyleIndex); // 【记忆修复：保存当前选中的风格】
            editor.putInt("DlgBgC_" + currentSlot, dialogBgColor);
            editor.putInt("DlgBgA_" + currentSlot, dialogBgAlpha);
            editor.putInt("DlgTxtC_" + currentSlot, dialogTextColor);
            editor.putFloat("DlgTxtS_" + currentSlot, dialogTextSize);
            editor.putString("DlgBgUri_" + currentSlot, dialogBgImageUri);
            editor.putFloat("DlgWidth_" + currentSlot, dialogWidthRatio); 
            editor.putFloat("DlgHeight_" + currentSlot, dialogHeightRatio); 
            editor.putBoolean("OverlayVis_" + currentSlot, isOverlayVisible);
            editor.putBoolean("OverlayMir1_" + currentSlot, overlayMirror1); 
            editor.putBoolean("OverlayMir2_" + currentSlot, overlayMirror2);
            editor.putFloat("MenuWidth_" + currentSlot, menuWidth);
            editor.putFloat("MenuHeight_" + currentSlot, menuHeight);
            editor.putString("MenuSkin_" + currentSlot, menuSkinUri);
            editor.putBoolean("MenuLocked_" + currentSlot, isMenuLocked);
            editor.putInt("MenuColor_" + currentSlot, menuColor);
            editor.putInt("MenuTextColor_" + currentSlot, menuTextColor);
            editor.putInt("MenuTextSizeFactor_" + currentSlot, menuTextSizeFactor);
            editor.putInt("MenuShape_" + currentSlot, menuShape);
             editor.putString("MenuPressedSkin_" + currentSlot, menuPressedSkinUri);
            editor.putInt("MenuPressedColor_" + currentSlot, menuPressedEffectColor);
            editor.putInt("MenuPressedAlpha_" + currentSlot, menuPressedEffectAlpha);
            editor.putString("MenuButtonName_" + currentSlot, menuButtonName);
            editor.putBoolean("AlwaysAskFolder", alwaysAskFolder); // 全局保存
            editor.putBoolean("IntegrationMode", isIntegrationModeEnabled); // 全局保存：整合包兼容模式
            editor.putBoolean("DynamicScaleEnabled_" + currentSlot, isDynamicScaleEnabled);

            
            // 【新增：保存文件夹预设列表】
            JSONArray folderArr = new JSONArray();
            for(FolderPreset f : folderPresets) folderArr.put(f.toJson());
            editor.putString("FolderPresets", folderArr.toString());

            JSONArray styleArr = new JSONArray();
            for(GamepadStyle s : styleList) styleArr.put(s.toJson());
            editor.putString("StyleList_" + currentSlot, styleArr.toString());

            // 【新增：动态分辨率适配 - 记录保存时的屏幕真实宽高】
            editor.putInt("SavedScreenWidth_" + currentSlot, getWidth());
            editor.putInt("SavedScreenHeight_" + currentSlot, getHeight());

            editor.apply();
        } catch (Exception e) {}
    }

    public void loadConfig(int slot) {
        this.currentSlot = slot;
        String json = prefs.getString(KEY_LAYOUT_PREFIX + slot, null);
        if (json == null || json.isEmpty()) { loadDefaultLayout(); return; }
        try {
            buttons.clear();
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                VirtualButton btn = new VirtualButton(o.optString("id", "Btn"), (float)o.optDouble("cx", 500), (float)o.optDouble("cy", 500),
                    (float)o.optDouble("radius", 80), o.optInt("color", Color.GRAY), o.optInt("alpha", 150), o.optInt("textColor", Color.WHITE), 
                    o.optInt("shape", SHAPE_CIRCLE), o.optString("keyMap", ""), o.optBoolean("isDir", false));
               btn.hitboxRadius = (float)o.optDouble("hitboxRadius", btn.radius * 1.5f); 
                btn.isLocked = o.optBoolean("isLocked", false);
                btn.customImageUri = o.optString("skin", ""); 
                // 【新增：读取按下状态特效】
                btn.customPressedUri = o.optString("pressedSkin", "");
                btn.pressedEffectColor = o.optInt("pressedColor", 0);
                btn.pressedEffectAlpha = o.optInt("pressedAlpha", 150);
                btn.boundGamepadKeyCode = o.optInt("boundPadCode", 0);
                btn.isTurbo = o.optBoolean("isTurbo", false);
                btn.turboInterval = o.optInt("turboInterval", 40);
                // 【新增：读取独立高阶设置】
                btn.textSizeFactor = o.optInt("textSizeFactor", 100);
                btn.useCustomVib = o.optBoolean("useCustomVib", false);
                btn.customVib = o.optInt("customVib", 30);
                btn.useCustomFeed = o.optBoolean("useCustomFeed", false);
                btn.customFeedScale = o.optInt("customFeedScale", 85);
                 
                btn.loadSkinFromUri(getContext());                
                buttons.add(btn);              
            }
            joystickMode = prefs.getInt("JoystickMode_" + slot, 0);
            joyBaseX = prefs.getFloat("JoyX_" + slot, 250); joyBaseY = prefs.getFloat("JoyY_" + slot, 700);
            joyRadius = prefs.getFloat("JoyR_" + slot, 180); 
            joyHitboxRadius = prefs.getFloat("JoyHitR_" + slot, 270);
            joyAlpha = prefs.getInt("JoyA_" + slot, 200);
            joyColor = prefs.getInt("JoyColor_" + slot, Color.parseColor("#FF5555"));
            isJoyLocked = prefs.getBoolean("JoyLocked_" + slot, false);           isVibrationOn = prefs.getBoolean("Vibration_" + slot, true);
            vibrationIntensity = prefs.getInt("VibIntensity_" + slot, 30);
            // 【新增：读取全局反馈设置】
            isGlobalFeedbackEnabled = prefs.getBoolean("GlobalFeedbackOn_" + slot, true);
            globalFeedbackScaleInt = prefs.getInt("GlobalFeedbackScale_" + slot, 85);         
            joySkinBaseUri = prefs.getString("JoySkinBase_" + slot, "");
            joySkinKnobUri = prefs.getString("JoySkinKnob_" + slot, "");
            
            joyKnobX = joyBaseX; joyKnobY = joyBaseY;
            
            if (!joySkinBaseUri.isEmpty() || !joySkinKnobUri.isEmpty()) {
                try {
                    if(!joySkinBaseUri.isEmpty()) {
                        InputStream is1 = getContext().getContentResolver().openInputStream(Uri.parse(joySkinBaseUri));
                        joySkinBaseBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeStream(is1), (int)(joyRadius*2), (int)(joyRadius*2), true);
                        if(is1!=null) is1.close();
                    } else { joySkinBaseBitmap = null; }
                    
                    if(!joySkinKnobUri.isEmpty()) {
                        InputStream is2 = getContext().getContentResolver().openInputStream(Uri.parse(joySkinKnobUri));
                        joySkinKnobBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeStream(is2), (int)(joyRadius*2), (int)(joyRadius*2), true);
                        if(is2!=null) is2.close();
                    } else { joySkinKnobBitmap = null; }
                } catch (Exception e) { joySkinBaseBitmap = null; joySkinKnobBitmap = null; }
            } else { joySkinBaseBitmap = null; joySkinKnobBitmap = null; }

            menuX = prefs.getFloat("MenuX", 20); menuY = prefs.getFloat("MenuY", 20);
                        overlayMode = prefs.getInt("OverlayMode_" + slot, 0);
            overlayUri1 = prefs.getString("OverlayUri1_" + slot, "");
            overlayX1 = prefs.getFloat("OverlayX1_" + slot, 0); overlayY1 = prefs.getFloat("OverlayY1_" + slot, 0);
                        float oldScale1 = prefs.getFloat("OverlayScale1_" + slot, 1.0f);
            overlayScaleX1 = prefs.getFloat("OverlayScaleX1_" + slot, oldScale1);
            overlayScaleY1 = prefs.getFloat("OverlayScaleY1_" + slot, oldScale1);
            overlayCurvature1 = prefs.getFloat("OverlayCurv1_" + slot, 0f);
            
            overlayUri2 = prefs.getString("OverlayUri2_" + slot, "");
            overlayX2 = prefs.getFloat("OverlayX2_" + slot, 0); overlayY2 = prefs.getFloat("OverlayY2_" + slot, 0);
            
            float oldScale2 = prefs.getFloat("OverlayScale2_" + slot, 1.0f);
            overlayScaleX2 = prefs.getFloat("OverlayScaleX2_" + slot, oldScale2);
            overlayScaleY2 = prefs.getFloat("OverlayScaleY2_" + slot, oldScale2);
            overlayCurvature2 = prefs.getFloat("OverlayCurv2_" + slot, 0f);
            overlayRotation1 = prefs.getFloat("OverlayRot1_" + slot, 0f);
            overlayRotation2 = prefs.getFloat("OverlayRot2_" + slot, 0f);
            isFullscreenHideOverlay = prefs.getBoolean("FS_HideOverlay_" + slot, false);
            isAutoHideEnabled = prefs.getBoolean("AutoHide_" + slot, true);
            gamepadDeadzone = prefs.getFloat("PadDeadzone_" + slot, 0.2f);
            gamepadUIMode = prefs.getInt("PadUIMode_" + slot, 1);
            isGamepadVibrationOn = prefs.getBoolean("PadVib_" + slot, true);           
            gridSize = prefs.getInt("GridSize_" + slot, 50);
            // 【新增：读取网格与背景配置】
            gridLineColor = prefs.getInt("GridLineColor_" + slot, Color.WHITE);
            gridLineAlpha = prefs.getInt("GridLineAlpha_" + slot, 30);
            gridBgColor = prefs.getInt("GridBgColor_" + slot, Color.argb(100, 255, 0, 0));
            
            autoHideSeconds = prefs.getInt("AutoHideSec_" + slot, 5);
            currentStyleIndex = prefs.getInt("CurrentStyleIndex_" + slot, 0); // 【记忆修复：读取刚才选中的风格】
            // 读取自定义弹窗 UI 设置
            dialogBgColor = prefs.getInt("DlgBgC_" + slot, Color.parseColor("#222222"));
            dialogBgAlpha = prefs.getInt("DlgBgA_" + slot, 230);
            dialogTextColor = prefs.getInt("DlgTxtC_" + slot, Color.WHITE);
            dialogTextSize = prefs.getFloat("DlgTxtS_" + slot, 14f);
            dialogBgImageUri = prefs.getString("DlgBgUri_" + slot, "");
                        dialogWidthRatio = prefs.getFloat("DlgWidth_" + slot, 0.8f); 
            dialogHeightRatio = prefs.getFloat("DlgHeight_" + slot, 0.8f); 
            isOverlayVisible = prefs.getBoolean("OverlayVis_" + slot, true); 
            overlayMirror1 = prefs.getBoolean("OverlayMir1_" + slot, false); 
            overlayMirror2 = prefs.getBoolean("OverlayMir2_" + slot, false);
            if(!dialogBgImageUri.isEmpty()){
                try { 
                    InputStream dIs = getContext().getContentResolver().openInputStream(Uri.parse(dialogBgImageUri));
                    dialogBgBitmap = BitmapFactory.decodeStream(dIs);
                    if(dIs != null) dIs.close();
                } catch(Exception e) { dialogBgBitmap = null; }
            } else { dialogBgBitmap = null; }

            
                                    // ================= 彻底修复：存读档时的 GIF 动静双重识别引擎 =================
            try { 
                if (!overlayUri1.isEmpty()) { 
                    InputStream is = getContext().getContentResolver().openInputStream(Uri.parse(overlayUri1));
                    byte[] bytes = readBytes(is); is.close();
                    android.graphics.Movie m = android.graphics.Movie.decodeByteArray(bytes, 0, bytes.length);
                    if (m != null && m.duration() > 0) { overlayMovie1 = m; overlayBmp1 = null; } 
                    else { overlayMovie1 = null; overlayBmp1 = BitmapFactory.decodeByteArray(bytes, 0, bytes.length); }
                } else { overlayMovie1 = null; overlayBmp1 = null; }
            } catch(Exception e) { overlayMovie1 = null; overlayBmp1 = null; }

            try { 
                if (!overlayUri2.isEmpty()) { 
                    InputStream is = getContext().getContentResolver().openInputStream(Uri.parse(overlayUri2));
                    byte[] bytes = readBytes(is); is.close();
                    android.graphics.Movie m = android.graphics.Movie.decodeByteArray(bytes, 0, bytes.length);
                    if (m != null && m.duration() > 0) { overlayMovie2 = m; overlayBmp2 = null; } 
                    else { overlayMovie2 = null; overlayBmp2 = BitmapFactory.decodeByteArray(bytes, 0, bytes.length); }
                } else { overlayMovie2 = null; overlayBmp2 = null; }
            } catch(Exception e) { overlayMovie2 = null; overlayBmp2 = null; }
                        

            menuScale = prefs.getFloat("MenuScale", 1.0f); menuAlpha = prefs.getInt("MenuAlpha", 220);
            
            // 【👇补充这里👇】
            menuWidth = prefs.getFloat("MenuWidth_" + slot, 230);
            menuHeight = prefs.getFloat("MenuHeight_" + slot, 90);
            menuSkinUri = prefs.getString("MenuSkin_" + slot, ""); // <--- 改为 getString 读取
            isMenuLocked = prefs.getBoolean("MenuLocked_" + slot, false);
            menuColor = prefs.getInt("MenuColor_" + slot, Color.parseColor("#333333"));
            menuTextColor = prefs.getInt("MenuTextColor_" + slot, Color.WHITE);
            menuTextSizeFactor = prefs.getInt("MenuTextSizeFactor_" + slot, 100);
            menuShape = prefs.getInt("MenuShape_" + slot, 1);
            menuPressedSkinUri = prefs.getString("MenuPressedSkin_" + slot, "");
            menuPressedEffectColor = prefs.getInt("MenuPressedColor_" + slot, 0);
            menuPressedEffectAlpha = prefs.getInt("MenuPressedAlpha_" + slot, 150);
            menuButtonName = prefs.getString("MenuButtonName_" + slot, L("⚙ 高级设置"));
            alwaysAskFolder = prefs.getBoolean("AlwaysAskFolder", true);
            isIntegrationModeEnabled = prefs.getBoolean("IntegrationMode", false); // 读取整合包兼容模式
            isDynamicScaleEnabled = prefs.getBoolean("DynamicScaleEnabled_" + slot, false);

            
            // 【新增：读取文件夹预设列表】
            String folderJson = prefs.getString("FolderPresets", "[]");
            folderPresets.clear();
            if (!folderJson.isEmpty() && !folderJson.equals("[]")) {
                try {
                    JSONArray fa = new JSONArray(folderJson);
                    for (int i=0; i<fa.length(); i++) folderPresets.add(FolderPreset.fromJson(fa.getJSONObject(i)));
                } catch (Exception e){}
            }

            if(!menuPressedSkinUri.isEmpty()) { 
                try {
                    InputStream mIs = getContext().getContentResolver().openInputStream(Uri.parse(menuPressedSkinUri));
                    menuPressedSkinBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeStream(mIs), (int)menuWidth, (int)menuHeight, true);
                    if(mIs != null) mIs.close();
                } catch(Exception e) { menuPressedSkinBitmap = null; }
            } else { menuPressedSkinBitmap = null; }

            if(!menuSkinUri.isEmpty()) { 
                try {
                    InputStream mIs = getContext().getContentResolver().openInputStream(Uri.parse(menuSkinUri));
                    menuSkinBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeStream(mIs), (int)menuWidth, (int)menuHeight, true);
                    if(mIs != null) mIs.close();
                } catch(Exception e) { menuSkinBitmap = null; }
            } else { menuSkinBitmap = null; }

             String styleJson = prefs.getString("StyleList_" + slot, "");
            if (!styleJson.isEmpty()) {
                JSONArray styleArr = new JSONArray(styleJson);
                styleList.clear();
                for (int i=0; i<styleArr.length(); i++) styleList.add(GamepadStyle.fromJson(styleArr.getJSONObject(i)));
                
                // 【无损抢救】：自动检测旧版本。如果发现旧版系统预设缺失方形贴图，静默重置12个系统预设，完美保留玩家自定义风格！
                if (styleList.size() > 1 && (styleList.get(1).btnSquareUri == null || styleList.get(1).btnSquareUri.isEmpty())) {
                    List<GamepadStyle> userStyles = new ArrayList<>();
                    for (int i = 0; i < styleList.size(); i++) {
                        if (i >= 12 || !styleList.get(i).styleName.matches("^[0-1][0-9]\\..*")) userStyles.add(styleList.get(i));
                    }
                    generateVideoArcadeStyle(); // 重置完美的系统风格
                    styleList.addAll(userStyles); // 无损把玩家的心血加回尾部
                }
            } else {
                generateVideoArcadeStyle();
            }

            // 【开局全局纠偏】：无论玩家存的是什么神仙旧布局，开机瞬间强行把错误穿成“圆形衣服”的“方形按键”扒下来！
            // 【修复】：只纠正外观图片，绝对不碰玩家自定义保存下来的透明度和颜色，解决重启丢失配置 Bug！
            if (joystickMode == JOYSTICK_MODE_STYLE && currentStyleIndex < styleList.size()) {
                GamepadStyle currentTheme = styleList.get(currentStyleIndex);
                for (VirtualButton b : buttons) {
                    if (!b.isDirectional) {
                        // 移除对 b.color 和 b.pressedEffectAlpha 的强行覆盖，保留玩家自定义数值
                        if (b.shape == SHAPE_CIRCLE) {
                            b.customImageUri = currentTheme.btnNormalUri != null ? currentTheme.btnNormalUri : "";
                        } else {
                            b.customImageUri = (currentTheme.btnSquareUri != null && !currentTheme.btnSquareUri.isEmpty()) ? currentTheme.btnSquareUri : "";
                        }
                        // 只有当按键的按下特效颜色等于默认 0 时，才补全主题皮肤；如果玩家改过，保留玩家的！
                        if (b.pressedEffectColor == 0) {
                             b.customPressedUri = currentTheme.btnPressedUri != null ? currentTheme.btnPressedUri : "";
                        }
                        b.loadSkinFromUri(getContext());
                    }
                }
            }

            // 【新增】：强制重绘摇杆底盘的颜色梯度，避免切换配置时颜色不同步
            if (joystickMode == 2) {
                RadialGradient baseGrad = new RadialGradient(joyBaseX, joyBaseY, joyRadius, Color.parseColor("#333333"), Color.parseColor("#080808"), Shader.TileMode.CLAMP);
                paintBtn.setShader(baseGrad);
            }

            // 【读取旧存档的分辨率记录，触发动态拉伸（旧版本没这数据的不会被拉伸，保障兼容性）】
            loadedSavedWidth = prefs.getInt("SavedScreenWidth_" + slot, 0);
            loadedSavedHeight = prefs.getInt("SavedScreenHeight_" + slot, 0);
            if (loadedSavedWidth > 0 && loadedSavedHeight > 0) {
                int currentW = getWidth();
                int currentH = getHeight();
                if (currentW > 0 && currentH > 0 && (currentW != loadedSavedWidth || currentH != loadedSavedHeight)) {
                    applyDynamicResolutionScale(currentW, currentH);
                } else if (currentW == 0 || currentH == 0) {
                    pendingResolutionScale = true; // 引擎生命周期原因此时拿不到长宽，交接给 onSizeChanged 处理
                }
            }
            
            invalidate();
        } catch (Exception e) { loadDefaultLayout(); }
    }


        
             private void loadDefaultLayout() {
        int sw = getWidth();
        int sh = getHeight();
        
        // 拦截尺寸为 0 的初始化阶段，交给 onSizeChanged 处理
        if (sw == 0 || sh == 0) {
            pendingDefaultLayout = true;
            return;
        }
        pendingDefaultLayout = false;
        
        buttons.clear();
        joystickMode = 0;
        isVibrationOn = true; 
        vibrationIntensity = 30;
        imagePickerTarget = 0; 
        
        // 【终极修复：引入 1080p 基准缩放比例】
        // 无论屏幕是 720p 还是 2K，都以高度作为缩放基准，确保按键始终保持正圆且相对距离完美
        float scale = Math.min(sw, sh) / 1080f;
        
        // 【新增】：强制同步菜单按钮的大小和位置，使其跟随屏幕自适应
        menuScale = scale; 
        menuX = 30 * scale; 
        menuY = 30 * scale;

        
        // 左侧摇杆与方向键：紧贴左下角
        joyBaseX = 250 * scale; 
        joyBaseY = sh - 380 * scale; 
        joyKnobX = joyBaseX; 
        joyKnobY = joyBaseY;
        
        joyRadius = 180 * scale; 
        joyHitboxRadius = joyRadius * 1.5f; 
        joyAlpha = 200; 
        joyColor = Color.parseColor("#FF5555"); 
        joySkinBaseUri = ""; joySkinKnobUri = ""; joySkinBaseBitmap = null; joySkinKnobBitmap = null;
        
                overlayMode = 0; overlayUri1 = ""; overlayUri2 = ""; overlayBmp1 = null; overlayBmp2 = null; overlayMovie1 = null; overlayMovie2 = null;
        overlayX1 = 0; overlayY1 = 0; overlayScaleX1 = 1.0f; overlayScaleY1 = 1.0f; overlayCurvature1 = 0f;
        overlayX2 = 0; overlayY2 = 0; overlayScaleX2 = 1.0f; overlayScaleY2 = 1.0f; overlayCurvature2 = 0f;     
        isFullscreenHideOverlay = false;
        isAutoHideEnabled = true;
        autoHideSeconds = 5;
        // 【新增：恢复网格默认值】
        gridSize = 50;
        gridLineColor = Color.WHITE;
        gridLineAlpha = 30;
        gridBgColor = Color.argb(100, 255, 0, 0);

        float btnRadius = 90 * scale; 
        float dirRadius = 80 * scale;  
        float dPadOffset = 150 * scale;

        // 十字方向键围绕摇杆基准
        buttons.add(new VirtualButton("UP", joyBaseX, joyBaseY - dPadOffset, dirRadius, Color.GRAY, 150, Color.WHITE, SHAPE_CIRCLE, "UP", true));
        buttons.add(new VirtualButton("DOWN", joyBaseX, joyBaseY + dPadOffset, dirRadius, Color.GRAY, 150, Color.WHITE, SHAPE_CIRCLE, "DOWN", true));
        buttons.add(new VirtualButton("LEFT", joyBaseX - dPadOffset, joyBaseY, dirRadius, Color.GRAY, 150, Color.WHITE, SHAPE_CIRCLE, "LEFT", true));
        buttons.add(new VirtualButton("RIGHT", joyBaseX + dPadOffset, joyBaseY, dirRadius, Color.GRAY, 150, Color.WHITE, SHAPE_CIRCLE, "RIGHT", true));
        
        // 【终极修复：右侧动作键反向锚定右边界】
        // rx 基准点设为屏幕宽度向左缩进，确保最右侧的 C/Z 键离屏幕边缘始终有安全边距
        float rx = sw - 650 * scale; 
        float ry = sh - 380 * scale; 
        
        float ox = 200 * scale; // 水平间距
        float oy = 50 * scale;  // 斜向落差
        float spacingY = 200 * scale; // 上下排间距
        
        buttons.add(new VirtualButton("A", rx, ry, btnRadius, Color.parseColor("#4CAF50"), 180, Color.WHITE, SHAPE_CIRCLE, "A", false));
        buttons.add(new VirtualButton("B", rx + ox, ry - oy, btnRadius, Color.parseColor("#F44336"), 180, Color.WHITE, SHAPE_CIRCLE, "B", false));
        buttons.add(new VirtualButton("C", rx + ox * 2, ry - oy * 2, btnRadius, Color.parseColor("#2196F3"), 180, Color.WHITE, SHAPE_CIRCLE, "C", false));
        buttons.add(new VirtualButton("X", rx, ry - spacingY, btnRadius, Color.parseColor("#8BC34A"), 180, Color.WHITE, SHAPE_CIRCLE, "X", false));
        buttons.add(new VirtualButton("Y", rx + ox, ry - oy - spacingY, btnRadius, Color.parseColor("#E91E63"), 180, Color.WHITE, SHAPE_CIRCLE, "Y", false));
        buttons.add(new VirtualButton("Z", rx + ox * 2, ry - oy * 2 - spacingY, btnRadius, Color.parseColor("#03A9F4"), 180, Color.WHITE, SHAPE_CIRCLE, "Z", false));
        
        // 居中系统按键 (ESC / START)锚定底部中点
        float sysBtnRadius = 70 * scale;
        float sysBaseY = sh - 130 * scale;
        buttons.add(new VirtualButton("ESC", sw / 2f - 150 * scale, sysBaseY, sysBtnRadius, Color.DKGRAY, 150, Color.WHITE, SHAPE_SQUARE, "ESC", false));
        buttons.add(new VirtualButton("START", sw / 2f + 150 * scale, sysBaseY, sysBtnRadius, Color.DKGRAY, 150, Color.WHITE, SHAPE_SQUARE, "RETURN", false));
    }
               
            
        
        
    @SuppressWarnings("deprecation")
    public static class FileActionFragment extends android.app.Fragment {
        @Override
        public void onCreate(android.os.Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            int type = getArguments() != null ? getArguments().getInt("action_type", 0) : 0;
            if (type == 1) { 
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, "ikemen_layout.json");
                startActivityForResult(intent, 44);
            } else if (type == 2) { 
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, 45);
            } else if (type == 3) { // 【新增】语言补丁专属请求
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                startActivityForResult(intent, 46);
            } else { 
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*"); // 【优化5】放开限制，允许选择任何视频或GIF
                startActivityForResult(intent, 43);
            }
        }

@Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (resultCode == android.app.Activity.RESULT_OK && data != null && data.getData() != null) {
                android.net.Uri uri = data.getData();
                if (requestCode == 43 && DynamicGamepadView.instance != null) {
                    try { getActivity().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception e) {}
                    DynamicGamepadView.instance.onImagePicked(uri.toString());
                } else if (requestCode == 44) { 
                    try {
                        String exportData = getArguments() != null ? getArguments().getString("export_data", "") : "";
                        if (exportData.isEmpty()) { Toast.makeText(getActivity(), L("❌ 无数据可导出"), Toast.LENGTH_SHORT).show(); return; }
                        java.io.OutputStream os = getActivity().getContentResolver().openOutputStream(uri);
                        os.write(exportData.getBytes(StandardCharsets.UTF_8));
                        os.close();
                        Toast.makeText(getActivity(), L("✅ 导出成功！"), Toast.LENGTH_SHORT).show();
                    } catch (Exception e) { Toast.makeText(getActivity(), L("❌ 导出失败"), Toast.LENGTH_SHORT).show(); }                
                } else if (requestCode == 46 && DynamicGamepadView.instance != null) { 
                    // 【新增】处理语言补丁的解析与覆写
                    final Context safeContext = getActivity() != null ? getActivity() : DynamicGamepadView.instance.getContext();
                    try {
                        java.io.InputStream is = safeContext.getContentResolver().openInputStream(uri);
                        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                        StringBuilder sb = new StringBuilder(); String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                        reader.close(); is.close();
                        
                        // 校验 JSON 格式并永久保存
                        JSONObject newLangPack = new JSONObject(sb.toString().trim());
                        SharedPreferences.Editor editor = safeContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
                        editor.putString("LanguagePatch", newLangPack.toString());
                        editor.commit();
                        
                        Toast.makeText(safeContext, L("✅ 补丁导入成功！即将强制重启生效..."), Toast.LENGTH_LONG).show();
                        
                        // 延迟 1.5 秒后直接杀死进程，迫使玩家重进游戏
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            System.exit(0);
                        }, 1500);
                        
                    } catch (Exception e) {
                        Toast.makeText(safeContext, L("❌ 语言补丁读取失败，请检查 JSON 格式是否正确"), Toast.LENGTH_SHORT).show();
                    }
                    getFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
                    return;
                } else if (requestCode == 45 && DynamicGamepadView.instance != null) { 
                    final Context safeContext = getActivity() != null ? getActivity() : DynamicGamepadView.instance.getContext();
                    try {
                        java.io.InputStream is = safeContext.getContentResolver().openInputStream(uri);
                        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                        StringBuilder sb = new StringBuilder(); String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                        reader.close(); is.close();
                        
                        String fileContent = sb.toString().trim();
                        final JSONObject root;
                        
                        if (fileContent.startsWith("[")) {
                            root = new JSONObject(); root.put("buttons", new JSONArray(fileContent));
                        } else if (fileContent.startsWith("{")) {
                            if (!fileContent.endsWith("}")) {
                                int lastBracket = fileContent.lastIndexOf("}");
                                if (lastBracket != -1) fileContent = fileContent.substring(0, lastBracket + 1);
                            }
                            root = new JSONObject(fileContent);
                        } else {
                            DynamicGamepadView.instance.post(() -> {
                                DynamicGamepadView.instance.loadDefaultLayout();
                                DynamicGamepadView.instance.saveConfig();
                                DynamicGamepadView.instance.invalidate();
                                Toast.makeText(safeContext, L("✅ 已自动转换为全屏自适应 Pro 布局"), Toast.LENGTH_LONG).show();
                            });
                            getFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
                            return;
                        }

                        new AlertDialog.Builder(safeContext, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setTitle(L("发现数据，请选择要导入的内容："))
                            .setItems(new CharSequence[]{L("📥 导入全部 (布局、位置与所有风格外观)"), L("📥 仅导入按键布局 (保留当前系统里的外观)"), L("📥 仅导入外观风格库与皮肤图片")}, (dialog, which) -> {
                                try {
                                    DynamicGamepadView v = DynamicGamepadView.instance;
                                    SharedPreferences.Editor editor = safeContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
                                    
                                    JSONArray incomingLayout = root.has("layout") ? root.optJSONArray("layout") : root.optJSONArray("buttons");
                                    if (incomingLayout == null) incomingLayout = new JSONArray();

                                    if (which == 0) { // 【全部解包导入】
                                        JSONArray finalLayout = new JSONArray();
                                        for (int i = 0; i < incomingLayout.length(); i++) {
                                            JSONObject btn = new JSONObject(incomingLayout.getJSONObject(i).toString());
                                            btn.put("skin", v.extractBase64ToImage(btn.optString("skin", "")));
                                            btn.put("pressedSkin", v.extractBase64ToImage(btn.optString("pressedSkin", "")));
                                            finalLayout.put(btn);
                                        }
                                        editor.putString(KEY_LAYOUT_PREFIX + v.currentSlot, finalLayout.toString());
                                        editor.putInt("JoystickMode_" + v.currentSlot, root.optInt("joystickMode", v.joystickMode));
                                        editor.putFloat("JoyX_" + v.currentSlot, (float) root.optDouble("joyBaseX", v.joyBaseX));
                                        editor.putFloat("JoyY_" + v.currentSlot, (float) root.optDouble("joyBaseY", v.joyBaseY));
                                        editor.putFloat("JoyR_" + v.currentSlot, (float) root.optDouble("joyRadius", v.joyRadius));
                                        editor.putFloat("JoyHitR_" + v.currentSlot, (float) root.optDouble("joyHitboxRadius", v.joyHitboxRadius));
                                        editor.putBoolean("JoyLocked_" + v.currentSlot, root.optBoolean("isJoyLocked", v.isJoyLocked));
                                        editor.putInt("JoyA_" + v.currentSlot, root.optInt("joyAlpha", v.joyAlpha));
                                        editor.putInt("JoyColor_" + v.currentSlot, root.optInt("joyColor", v.joyColor)); 
                                        editor.putString("JoySkinBase_" + v.currentSlot, v.extractBase64ToImage(root.optString("joySkinBase", "")));     
                                        editor.putString("JoySkinKnob_" + v.currentSlot, v.extractBase64ToImage(root.optString("joySkinKnob", "")));     
                                        editor.putBoolean("MenuLocked_" + v.currentSlot, root.optBoolean("isMenuLocked", v.isMenuLocked));
                                        editor.putFloat("MenuX", (float) root.optDouble("menuX", v.menuX));
                                        editor.putFloat("MenuY", (float) root.optDouble("menuY", v.menuY));
                                        editor.putFloat("MenuScale", (float) root.optDouble("menuScale", v.menuScale));
                                        editor.putInt("MenuAlpha", root.optInt("menuAlpha", v.menuAlpha));
                                        
                                        if (root.has("savedScreenWidth")) {
                                            editor.putInt("SavedScreenWidth_" + v.currentSlot, root.optInt("savedScreenWidth"));
                                            editor.putInt("SavedScreenHeight_" + v.currentSlot, root.optInt("savedScreenHeight"));
                                        }
                                        
                                        if (root.has("styles")) {
                                            JSONArray styles = root.getJSONArray("styles");
                                            JSONArray parsedStyles = new JSONArray();
                                            for (int i = 0; i < styles.length(); i++) {
                                                JSONObject s = styles.getJSONObject(i);
                                                s.put("joyBaseUri", v.extractBase64ToImage(s.optString("joyBaseUri", "")));
                                                s.put("joyKnobUri", v.extractBase64ToImage(s.optString("joyKnobUri", "")));
                                                s.put("btnNormalUri", v.extractBase64ToImage(s.optString("btnNormalUri", "")));
                                                s.put("btnSquareUri", v.extractBase64ToImage(s.optString("btnSquareUri", "")));
                                                s.put("btnPressedUri", v.extractBase64ToImage(s.optString("btnPressedUri", "")));
                                                parsedStyles.put(s);
                                            }
                                            editor.putString("StyleList_" + v.currentSlot, parsedStyles.toString());
                                            editor.putInt("CurrentStyleIndex_" + v.currentSlot, root.optInt("currentStyleIndex", v.currentStyleIndex));
                                        }
                                    } 
                                    else if (which == 1) { // 【仅布局 - 智能穿衣引擎】
                                        JSONArray finalLayout = new JSONArray();
                                        for (int i = 0; i < incomingLayout.length(); i++) {
                                            JSONObject incBtn = new JSONObject(incomingLayout.getJSONObject(i).toString());
                                            // 自动继承当前系统中同 ID 或对应键值的皮肤
                                            for (DynamicGamepadView.VirtualButton currBtn : v.buttons) {
                                                if (currBtn.id.equals(incBtn.optString("id")) || currBtn.keyMapStr.equals(incBtn.optString("keyMap"))) {
                                                    incBtn.put("skin", currBtn.customImageUri);
                                                    incBtn.put("pressedSkin", currBtn.customPressedUri);
                                                    incBtn.put("color", currBtn.color);
                                                    incBtn.put("pressedColor", currBtn.pressedEffectColor);
                                                    incBtn.put("alpha", currBtn.alpha);
                                                    incBtn.put("pressedAlpha", currBtn.pressedEffectAlpha);
                                                    incBtn.put("shape", currBtn.shape);
                                                    incBtn.put("textColor", currBtn.textColor);
                                                    break;
                                                }
                                            }
                                            finalLayout.put(incBtn);
                                        }
                                        editor.putString(KEY_LAYOUT_PREFIX + v.currentSlot, finalLayout.toString());
                                        editor.putFloat("JoyX_" + v.currentSlot, (float) root.optDouble("joyBaseX", v.joyBaseX));
                                        editor.putFloat("JoyY_" + v.currentSlot, (float) root.optDouble("joyBaseY", v.joyBaseY));
                                        editor.putFloat("JoyR_" + v.currentSlot, (float) root.optDouble("joyRadius", v.joyRadius));
                                    }
                                    else if (which == 2) { // 【仅风格 - 精准覆盖皮肤引擎】
                                        if (root.has("styles")) {
                                            JSONArray styles = root.getJSONArray("styles");
                                            JSONArray parsedStyles = new JSONArray();
                                            for (int i = 0; i < styles.length(); i++) {
                                                JSONObject s = styles.getJSONObject(i);
                                                s.put("joyBaseUri", v.extractBase64ToImage(s.optString("joyBaseUri", "")));
                                                s.put("joyKnobUri", v.extractBase64ToImage(s.optString("joyKnobUri", "")));
                                                s.put("btnNormalUri", v.extractBase64ToImage(s.optString("btnNormalUri", "")));
                                                s.put("btnSquareUri", v.extractBase64ToImage(s.optString("btnSquareUri", "")));
                                                s.put("btnPressedUri", v.extractBase64ToImage(s.optString("btnPressedUri", "")));
                                                parsedStyles.put(s);
                                            }
                                            editor.putString("StyleList_" + v.currentSlot, parsedStyles.toString());
                                            editor.putInt("CurrentStyleIndex_" + v.currentSlot, root.optInt("currentStyleIndex", v.currentStyleIndex));
                                        }
                                        
                                        // 提取导入包中的映射库，如果是旧版文件则直接从布局里硬抽映射
                                        JSONArray btnStyles = root.optJSONArray("buttonStyles");
                                        if (btnStyles == null) btnStyles = incomingLayout; 
                                        
                                        JSONArray currentLayoutArray = new JSONArray(safeContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_LAYOUT_PREFIX + v.currentSlot, "[]"));
                                        JSONArray finalLayout = new JSONArray();
                                        
                                        for (int i = 0; i < currentLayoutArray.length(); i++) {
                                            JSONObject currBtn = currentLayoutArray.getJSONObject(i);
                                            for (int j = 0; j < btnStyles.length(); j++) {
                                                JSONObject incStyle = btnStyles.getJSONObject(j);
                                                if (currBtn.optString("id").equals(incStyle.optString("id")) || currBtn.optString("keyMap").equals(incStyle.optString("keyMap"))) {
                                                    currBtn.put("skin", v.extractBase64ToImage(incStyle.optString("skin", "")));
                                                    currBtn.put("pressedSkin", v.extractBase64ToImage(incStyle.optString("pressedSkin", "")));
                                                    currBtn.put("color", incStyle.optInt("color", currBtn.optInt("color")));
                                                    currBtn.put("pressedColor", incStyle.optInt("pressedColor", currBtn.optInt("pressedColor")));
                                                    currBtn.put("alpha", incStyle.optInt("alpha", currBtn.optInt("alpha")));
                                                    currBtn.put("pressedAlpha", incStyle.optInt("pressedAlpha", currBtn.optInt("pressedAlpha")));
                                                    currBtn.put("textColor", incStyle.optInt("textColor", currBtn.optInt("textColor")));
                                                    currBtn.put("shape", incStyle.optInt("shape", currBtn.optInt("shape")));
                                                    break;
                                                }
                                            }
                                            finalLayout.put(currBtn);
                                        }
                                        editor.putString(KEY_LAYOUT_PREFIX + v.currentSlot, finalLayout.toString());
                                        editor.putInt("JoyAlpha_" + v.currentSlot, root.optInt("joyAlpha", v.joyAlpha));
                                        editor.putInt("JoyColor_" + v.currentSlot, root.optInt("joyColor", v.joyColor)); 
                                        editor.putString("JoySkinBase_" + v.currentSlot, v.extractBase64ToImage(root.optString("joySkinBase", "")));     
                                        editor.putString("JoySkinKnob_" + v.currentSlot, v.extractBase64ToImage(root.optString("joySkinKnob", "")));     
                                    }

                                    editor.commit(); 
                                    v.post(() -> v.loadConfig(v.currentSlot));
                                    Toast.makeText(safeContext, L("✅ 数据完美导入成功！"), Toast.LENGTH_LONG).show();
                                } catch (Exception e) { Toast.makeText(safeContext, L("❌ 应用失败: ") + e.getMessage(), Toast.LENGTH_SHORT).show(); }
                            }).show();
                            
                    } catch (Exception e) { Toast.makeText(safeContext, L("❌ 文件读取失败，可能已损坏"), Toast.LENGTH_SHORT).show(); }
                }
            }
            getFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
        }
   }     
    // =====================================
    // 物理手柄控制面板系统
    // =====================================
    private void showGamepadSettingsDialog() {
        final android.app.Dialog dialog = new android.app.Dialog(getContext(), android.R.style.Theme_DeviceDefault_Dialog);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        
        LinearLayout rootLayout = new LinearLayout(getContext());
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackground(getCustomDialogBackground());

        TextView dragHandle = new TextView(getContext());
        dragHandle.setText(L("✋ 拖拽此处 | 🎮 物理手柄外设专区"));
        android.graphics.drawable.GradientDrawable titleBg = new android.graphics.drawable.GradientDrawable();
        titleBg.setColor(Color.argb(50, 0, 0, 0));
        titleBg.setCornerRadii(new float[]{35f, 35f, 35f, 35f, 0f, 0f, 0f, 0f});
        dragHandle.setBackground(titleBg); dragHandle.setTextColor(dialogTextColor);
        dragHandle.setPadding(40, 30, 40, 30); dragHandle.setTextSize(dialogTextSize + 2f);
        dragHandle.setTypeface(null, Typeface.BOLD); rootLayout.addView(dragHandle);

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout layout = new LinearLayout(getContext()); 
        layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(50, 20, 50, 50);

        // 1. 硬件连接测试仪
        layout.addView(createTitle(L("1. 手柄通讯与硬件检测")));
        Button testBtn = new Button(getContext()); testBtn.setText(L("🕹️ 打开手柄信号检测仪"));
        testBtn.setTextColor(Color.WHITE); testBtn.setBackgroundColor(Color.parseColor("#9C27B0"));
        testBtn.setOnClickListener(v -> showGamepadTestDialog());
        layout.addView(testBtn);

        // 2. 灵敏度与视觉联动
        layout.addView(createTitle(L("2. 摇杆灵敏度与 UI 联动")));
        final SeekBar deadzoneBar = createColorBar(layout, L("摇杆死区/灵敏度 (%)"), (int)(gamepadDeadzone * 100));
        deadzoneBar.setMax(100);
        deadzoneBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) { if(fromUser) gamepadDeadzone = p / 100f; }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        });

        final Spinner uiModeSpinner = new Spinner(getContext());
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, 
            new String[]{L("0 - 屏幕按键无反应"), L("1 - 手柄按压时屏幕按键同步发光"), L("2 - 操作手柄时自动隐藏整个屏幕UI")});
        uiModeSpinner.setAdapter(modeAdapter); uiModeSpinner.setSelection(gamepadUIMode);
        layout.addView(uiModeSpinner);

        // 3. 外设物理震动
        layout.addView(createTitle(L("3. 外设硬件震动 (依赖系统驱动)")));
        final Button vibBtn = new Button(getContext());
        vibBtn.setText(isGamepadVibrationOn ? L("📳 外设实体震动：已开启") : L("📳 外设实体震动：已关闭"));
        vibBtn.setTextColor(Color.WHITE);
        vibBtn.setBackgroundColor(isGamepadVibrationOn ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));
        vibBtn.setOnClickListener(v -> {
            isGamepadVibrationOn = !isGamepadVibrationOn;
            vibBtn.setText(isGamepadVibrationOn ? L("📳 外设实体震动：已开启") : L("📳 外设实体震动：已关闭"));
            vibBtn.setBackgroundColor(isGamepadVibrationOn ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));
        });
        layout.addView(vibBtn);

        // 4. 自定义改键
        layout.addView(createTitle(L("4. 外设自定义映射 (改键)")));
        Button bindBtn = new Button(getContext()); bindBtn.setText(L("🛠️ 进入手柄自定义映射模式"));
        bindBtn.setTextColor(Color.WHITE); bindBtn.setBackgroundColor(Color.parseColor("#FF9800"));
        bindBtn.setOnClickListener(v -> { dialog.dismiss(); showGamepadBindingManager(); });
        layout.addView(bindBtn);

        // 底部保存按钮
        Button saveBtn = new Button(getContext()); saveBtn.setText(L("💾 保存设置"));
        saveBtn.setTextColor(Color.WHITE); saveBtn.setBackgroundColor(Color.parseColor("#1976D2"));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); p.setMargins(0, 40, 0, 0); saveBtn.setLayoutParams(p);
        saveBtn.setOnClickListener(v -> { gamepadUIMode = uiModeSpinner.getSelectedItemPosition(); saveConfig(); dialog.dismiss(); });
        layout.addView(saveBtn);

        scroll.addView(layout); rootLayout.addView(scroll);
        dialog.setContentView(rootLayout); setupMovableDialog(dialog, dragHandle); dialog.show();
    }

    private void showGamepadTestDialog() {
        final android.app.Dialog dialog = new android.app.Dialog(getContext(), android.R.style.Theme_DeviceDefault_Dialog);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        
        LinearLayout layout = new LinearLayout(getContext()); layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#222222")); layout.setPadding(60, 60, 60, 60);
        
        TextView title = new TextView(getContext()); title.setText(L("📡 手柄信号接收测试仪"));
        title.setTextColor(Color.GREEN); title.setTextSize(20f); title.setGravity(android.view.Gravity.CENTER);
        layout.addView(title);

        testFeedbackText = new TextView(getContext());
        testFeedbackText.setText(L("请随意按下手柄任意按键...\n(测试期间会拦截所有引擎指令)"));
        testFeedbackText.setTextColor(Color.WHITE); testFeedbackText.setTextSize(16f); testFeedbackText.setPadding(0, 40, 0, 40);
        layout.addView(testFeedbackText);

        Button closeBtn = new Button(getContext()); closeBtn.setText(L("结束测试"));
        closeBtn.setOnClickListener(v -> { testFeedbackText = null; dialog.dismiss(); });
        layout.addView(closeBtn);
        
        dialog.setContentView(layout); 
        dialog.setOnDismissListener(d -> testFeedbackText = null);
        dialog.show();
    }

    private void showGamepadBindingManager() {
        final android.app.Dialog dialog = new android.app.Dialog(getContext(), android.R.style.Theme_DeviceDefault_Dialog);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        
        LinearLayout rootLayout = new LinearLayout(getContext()); rootLayout.setOrientation(LinearLayout.VERTICAL); rootLayout.setBackground(getCustomDialogBackground());
        TextView header = new TextView(getContext()); header.setText(L("✋ 拖拽此处 | 🛠️ 手柄映射管理器"));
        header.setTextColor(dialogTextColor); header.setPadding(40, 30, 40, 30); header.setTextSize(dialogTextSize + 2f); header.setTypeface(null, Typeface.BOLD); rootLayout.addView(header);

        ScrollView scroll = new ScrollView(getContext()); LinearLayout layout = new LinearLayout(getContext()); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(40, 20, 40, 40);
        
        // ===== 【核心功能】：按下手柄新建按键 =====
        layout.addView(createTitle(L("【进阶】手柄直连创建自定义按键:")));
        Button btnNewFromPad = new Button(getContext());
        btnNewFromPad.setText(L("➕ 监听手柄按键 -> 在屏幕上新建绑定按键"));
        btnNewFromPad.setTextColor(Color.WHITE); btnNewFromPad.setBackgroundColor(Color.parseColor("#9C27B0"));
        btnNewFromPad.setPadding(0, 30, 0, 30);
        btnNewFromPad.setOnClickListener(v -> {
            isGamepadBindingMode = true; currentBindingTargetButton = null; // null 代表这是新建模式
            currentBindingDialog = new android.app.Dialog(getContext());
            TextView tv = new TextView(getContext()); tv.setText(L("请按下外设手柄上想要使用的按键...\n(按下后将在屏幕中心生成一个新按键并打开设置)"));
            tv.setTextColor(Color.WHITE); tv.setTextSize(18f); tv.setPadding(60,60,60,60);
            currentBindingDialog.setContentView(tv); currentBindingDialog.show();
        });
        layout.addView(btnNewFromPad);

        // ===== 【基础功能】：绑定已有预设 =====
        layout.addView(createTitle(L("【基础】绑定到屏幕上已存在的按键:")));
        for (VirtualButton btn : buttons) {
            if (btn.isDirectional) continue; // 过滤掉方向键，摇杆有独立的物理支持
            Button targetBtn = new Button(getContext());
            String bindInfo = (btn.boundGamepadKeyCode != 0) ? L(" (已绑手柄键值:") + btn.boundGamepadKeyCode + L(")") : L(" (未绑定手柄)");
            targetBtn.setText(L("绑定 -> [") + btn.id + L("]") + bindInfo);
            targetBtn.setTextColor(Color.WHITE); targetBtn.setBackgroundColor(Color.parseColor("#33ffffff"));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(0, 5, 0, 5); targetBtn.setLayoutParams(lp);
            
            targetBtn.setOnClickListener(v -> {
                isGamepadBindingMode = true; currentBindingTargetButton = btn; // 记录要绑定到哪个按键
                currentBindingDialog = new android.app.Dialog(getContext());
                TextView tv = new TextView(getContext()); tv.setText(L("请按下外设手柄上想要绑定到 [") + btn.id + L("] 的按键..."));
                tv.setTextColor(Color.WHITE); tv.setTextSize(18f); tv.setPadding(60,60,60,60);
                currentBindingDialog.setContentView(tv); currentBindingDialog.show();
            });
            layout.addView(targetBtn);
        }
        
        Button clearBtn = new Button(getContext()); clearBtn.setText(L("❌ 清除所有手柄绑定 (恢复默认)"));
        clearBtn.setTextColor(Color.WHITE); clearBtn.setBackgroundColor(Color.parseColor("#D32F2F"));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); cp.setMargins(0, 40, 0, 0); clearBtn.setLayoutParams(cp);
        clearBtn.setOnClickListener(v -> { for(VirtualButton b : buttons) b.boundGamepadKeyCode = 0; saveConfig(); dialog.dismiss(); showGamepadBindingManager(); });
        layout.addView(clearBtn);

        Button exitBtn = new Button(getContext()); exitBtn.setText(L("退出管理器")); exitBtn.setOnClickListener(v -> dialog.dismiss());
        layout.addView(exitBtn);

        scroll.addView(layout); rootLayout.addView(scroll);
        dialog.setContentView(rootLayout); setupMovableDialog(dialog, header); dialog.show();
    }
    
// ================= 新增：多语言补丁引擎 =================
    public static JSONObject languagePack = new JSONObject();

    public static void loadLanguagePack(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = p.getString("LanguagePatch", "{}");
        try {
            languagePack = new JSONObject(json);
        } catch (Exception e) {
            languagePack = new JSONObject();
        }
    }

    public static String L(String text) {
        // 核心翻译逻辑：如果 JSON 里有这个中文的翻译，就输出翻译；如果没有，就原样输出中文
        if (languagePack != null && languagePack.has(text)) {
            return languagePack.optString(text, text);
        }
        return text;
    }

} // <==== 注意：确保代码的最后以这个大括号结尾！
