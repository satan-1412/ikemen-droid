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
    public float dialogWidthRatio = 0.8f;  
    public float dialogHeightRatio = 0.8f; 
    public boolean isOverlayVisible = true; 
    public boolean overlayMirror1 = false;  
    public boolean overlayMirror2 = false;  
    public android.graphics.Movie overlayMovie1 = null; 
    public android.graphics.Movie overlayMovie2 = null;
    public long movieStart1 = 0;
    public long movieStart2 = 0;

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
    // 【新增】UI 模式切换开关：false 为经典模式，true 为复古街机模式
    public boolean useRetroUIMode = false; 



       public float joyBaseX = 250, joyBaseY = 700;
    public float joyRadius = 180;
    public float joyHitboxRadius = 270; // 【新增】摇杆独立触摸判定范围
    public int joyAlpha = 200;
    public int joyColor = Color.parseColor("#FF5555"); // 【修改】默认摇杆中心为红色
    
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

    private final List<VirtualButton> buttons = new ArrayList<>();
    private final Paint paintBtn = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintMenu = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tempRect = new RectF(); // 用于绘制圆角矩形

    private final SharedPreferences prefs;
    public boolean isEditMode = false;
        public boolean isGridSnapMode = false; // 是否开启网格吸附
            public boolean pendingDefaultLayout = false; // 【新增】延迟加载标记
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
        public String btnPressedUri = ""; // 全局默认按下特效
        public int globalBtnColor = Color.GRAY;
        public int globalPressedColor = Color.WHITE;
        public int globalPressedAlpha = 150;
        
        public GamepadStyle(String name) { this.styleName = name; }
        
        public JSONObject toJson() throws Exception {
            JSONObject obj = new JSONObject();
            obj.put("name", styleName); obj.put("joyBaseUri", joyBaseUri);
            obj.put("joyKnobUri", joyKnobUri); obj.put("btnNormalUri", btnNormalUri);
            obj.put("btnPressedUri", btnPressedUri); obj.put("btnColor", globalBtnColor);
            obj.put("pressedColor", globalPressedColor); obj.put("pressedAlpha", globalPressedAlpha);
            return obj;
        }
        
        public static GamepadStyle fromJson(JSONObject obj) {
            GamepadStyle style = new GamepadStyle(obj.optString("name", "未命名风格"));
            style.joyBaseUri = obj.optString("joyBaseUri", "");
            style.joyKnobUri = obj.optString("joyKnobUri", "");
            style.btnNormalUri = obj.optString("btnNormalUri", "");
            style.btnPressedUri = obj.optString("btnPressedUri", "");
            style.globalBtnColor = obj.optInt("btnColor", Color.GRAY);
            style.globalPressedColor = obj.optInt("pressedColor", Color.WHITE);
            style.globalPressedAlpha = obj.optInt("pressedAlpha", 150);
            return style;
        }
    }

    public String overlayUri1 = "";
    public Bitmap overlayBmp1 = null;
    public float overlayX1 = 0, overlayY1 = 0, overlayScale1 = 1.0f;
    
    public String overlayUri2 = "";
    public Bitmap overlayBmp2 = null;
    public float overlayX2 = 0, overlayY2 = 0, overlayScale2 = 1.0f;
    
    public float overlayRotation1 = 0f; // 遮罩图1旋转角度
    public float overlayRotation2 = 0f; // 遮罩图2旋转角度

    // 强制全屏时隐藏的标志位，供外部（如SDLActivity）检测到游戏全屏状态时修改
    public boolean isFullscreenHideOverlay = false; 

    private static final int GRID_SIZE = 50; // 网格大小，50像素一个格子，你可以自己调
    private VirtualButton draggedButton = null;
    private long downTime;
    private float downX, downY;

    private final RectF menuButtonRect = new RectF(20, 20, 250, 110);
    public VirtualButton currentlyEditingButton = null;
    public static DynamicGamepadView instance;

    public static final String[] TEXT_COLOR_NAMES = {"白色", "黑色", "红色", "黄色", "蓝色", "绿色"};
    public static final int[] TEXT_COLOR_VALUES = {Color.WHITE, Color.BLACK, Color.RED, Color.YELLOW, Color.BLUE, Color.GREEN};

    public static final String[] SHAPE_NAMES = {"⭕ 圆形 (Circle)", "🔲 圆角矩形 (Rounded Square)"};
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
                public volatile boolean isMacroPlaying = false; 
        public List<List<Integer>> macroSteps = new ArrayList<>(); 
        public long pressTimestamp = 0; // 【新增】记录精准按下时间戳，用于防吃键
        public boolean isTurbo = false; // 【新增】是否开启连发
        public int turboInterval = 40; 
        private volatile boolean turboRunning = false; // 【新增】连发线程控制锁

        
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
        }
        

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
        

                // 【修复】确保这个方法在类的大括号里面
        public void executeMacro() {
            if (macroSteps.size() <= 1 || isMacroPlaying) return;
            isMacroPlaying = true;
            new Thread(() -> {
                try {
                    for (List<Integer> stepCodes : macroSteps) {
                        for (int code : stepCodes) SDLActivity.onNativeKeyDown(code);
                        Thread.sleep(60); 
                        for (int code : stepCodes) SDLActivity.onNativeKeyUp(code);
                        Thread.sleep(50); 
                    }
                } catch (InterruptedException e) { }
                isMacroPlaying = false;
            }).start();
        }
       
        // 【新增】启动连发线程
        public void startTurbo() {
            if (turboRunning || macroSteps.isEmpty()) return;
            turboRunning = true;
            new Thread(() -> {
                while (turboRunning) {
                    try {
                        for (int code : macroSteps.get(0)) SDLActivity.onNativeKeyDown(code);
                        Thread.sleep(turboInterval); // 【优化】自定义按下持续时间
                        for (int code : macroSteps.get(0)) SDLActivity.onNativeKeyUp(code);
                        Thread.sleep(turboInterval); // 【优化】自定义松开间隔时间                       
                    } catch (InterruptedException e) { break; }
                }
            }).start();
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
        // 当 View 真正获取到游戏引擎赋予的逻辑分辨率时，执行延迟的排版
        if (pendingDefaultLayout && w > 0 && h > 0) {
            loadDefaultLayout();
            saveConfig();
            invalidate();
        }
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
        GamepadStyle style1 = new GamepadStyle("01. 原生渐变引擎 (System Default)");
        style1.joyBaseUri = ""; style1.joyKnobUri = ""; style1.btnNormalUri = ""; style1.btnPressedUri = "";
        style1.globalPressedColor = 0; 
        styleList.add(style1);

        int size = 400; // 统一生成高清 400x400 贴图
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // 核心数据矩阵：{风格名称, 底盘底色, 底盘边框, 摇杆底色, 摇杆边框, 按键底色, 按键边框, 按下特效高亮色}
        String[][] themes = {
            {"02. 经典街机 (Retro Arcade)", "#0C141E", "#90CAF9", "#D32F2F", "#B71C1C", "#1A2B42", "#90CAF9", "#4CAF50"},
            {"03. 赛博朋克霓虹 (Cyberpunk)", "#110022", "#00FFFF", "#FF007F", "#FF00FF", "#110022", "#00FFFF", "#FF00FF"},
            {"04. 暗物质黑武士 (Dark Matter)", "#111111", "#333333", "#444444", "#111111", "#1A1A1A", "#333333", "#FFFFFF"},
            {"05. 皇家奢华黑金 (Luxury Gold)", "#1A1813", "#D4AF37", "#C5B358", "#8A793D", "#26241D", "#D4AF37", "#FFDF00"},
            {"06. SFC 经典主机 (SNES Classic)", "#D3D3D3", "#A9A9A9", "#4A4E69", "#2F3241", "#D3D3D3", "#A9A9A9", "#7B68EE"},
            {"07. 生化毒液 (Toxic Acid)", "#0F1A0F", "#39FF14", "#2E8B57", "#00FF00", "#142214", "#39FF14", "#ADFF2F"},
            {"08. 猩红之月 (Blood Moon)", "#1A0505", "#DC143C", "#8B0000", "#660000", "#240A0A", "#DC143C", "#FF0000"},
            {"09. 深海幽蓝 (Ocean Depth)", "#001F3F", "#00BFFF", "#0074D9", "#00008B", "#001A33", "#00BFFF", "#1E90FF"},
            {"10. 极简拟物白 (White Glass)", "#F5F5F5", "#E0E0E0", "#FFFFFF", "#CCCCCC", "#FAFAFA", "#E0E0E0", "#87CEEB"},
            {"11. 紫晶矿石 (Royal Amethyst)", "#20102B", "#9932CC", "#8A2BE2", "#4B0082", "#2A1538", "#9932CC", "#DDA0DD"},
            {"12. 熔岩火山核心 (Magma Core)", "#2B0F0E", "#FF4500", "#FF8C00", "#8B0000", "#361311", "#FF4500", "#FFFF00"}
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

            // 3. 动态画：动作按键
            Bitmap btnBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas cBtn = new Canvas(btnBmp);
            p.setStyle(Paint.Style.FILL); p.setColor(btnFill); cBtn.drawCircle(size/2f, size/2f, size/2f - 10, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(14f); p.setColor(btnStroke); cBtn.drawCircle(size/2f, size/2f, size/2f - 10, p);
            String btnUri = saveImageToLocal(btnBmp, "style_btn_" + name.substring(0,2) + ".png");

            // 4. 组装并写入数据库
            GamepadStyle style = new GamepadStyle(name);
            style.joyBaseUri = baseUri;
            style.joyKnobUri = knobUri;
            style.btnNormalUri = btnUri;
            style.globalPressedColor = pressColor; // 绑上专用的按下发光色
            styleList.add(style);
        }
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

            // ================= 终极修复：GIF 灭霸拦截器 =================
            // 绝对不能让 GIF 图走下面的 Bitmap.compress 逻辑，否则直接被拍扁成单帧死图！
            if (imagePickerTarget == 4 || imagePickerTarget == 5) {
                InputStream isGif = getContext().getContentResolver().openInputStream(uri);
                byte[] bytes = readBytes(isGif); isGif.close();
                
                // 将原汁原味的字节流存入沙盒，保住 GIF 的动图命脉
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
                    Toast.makeText(getContext(), "遮罩图1应用成功！(动态帧已保留)", Toast.LENGTH_SHORT).show();
                } else {
                    overlayUri2 = localGifUri;
                    if (movie != null && movie.duration() > 0) { overlayMovie2 = movie; overlayBmp2 = null; movieStart2 = 0; }
                    else { overlayMovie2 = null; overlayBmp2 = BitmapFactory.decodeByteArray(bytes, 0, bytes.length); }
                    if (overlayMode < 2) overlayMode = 2;
                    Toast.makeText(getContext(), "遮罩图2应用成功！(动态帧已保留)", Toast.LENGTH_SHORT).show();
                }
                imagePickerTarget = 0; saveConfig(); invalidate();
                return; // 直接拦截返回，不走下面的 PNG 压缩逻辑！
            }
            // =========================================================

            // 下面是普通按键/背景皮肤的逻辑 (安全地转存为 PNG 格式)
            InputStream is = getContext().getContentResolver().openInputStream(uri);
            Bitmap raw = BitmapFactory.decodeStream(is);
            
            String localUriStr = saveImageToLocal(raw, "skin_" + System.currentTimeMillis() + ".png");
            final String finalUriStr = localUriStr.isEmpty() ? uriStr : localUriStr; 
            
            if (imagePickerTarget == 1) { 
                if (joySkinBaseBitmap != null && !joySkinBaseBitmap.isRecycled()) joySkinBaseBitmap.recycle();
                joySkinBaseUri = finalUriStr; 
                joySkinBaseBitmap = Bitmap.createScaledBitmap(raw, (int)(joyRadius*2), (int)(joyRadius*2), true);
                Toast.makeText(getContext(), "摇杆外框皮肤应用成功！", Toast.LENGTH_SHORT).show();
            } else if (imagePickerTarget == 2) { 
                if (joySkinKnobBitmap != null && !joySkinKnobBitmap.isRecycled()) joySkinKnobBitmap.recycle();
                joySkinKnobUri = finalUriStr; 
                joySkinKnobBitmap = Bitmap.createScaledBitmap(raw, (int)(joyRadius*2), (int)(joyRadius*2), true);
                Toast.makeText(getContext(), "摇杆中心皮肤应用成功！", Toast.LENGTH_SHORT).show();
            } else if (imagePickerTarget == 3 && currentlyEditingButton != null) { 
                if (currentlyEditingButton.skinBitmap != null && !currentlyEditingButton.skinBitmap.isRecycled()) currentlyEditingButton.skinBitmap.recycle();
                currentlyEditingButton.customImageUri = finalUriStr; 
                currentlyEditingButton.skinBitmap = Bitmap.createScaledBitmap(raw, (int)(currentlyEditingButton.radius*2), (int)(currentlyEditingButton.radius*2), true);
                Toast.makeText(getContext(), "按键皮肤应用成功！", Toast.LENGTH_SHORT).show();
            } else if (imagePickerTarget == 6 && currentlyEditingButton != null) { 
                if (currentlyEditingButton.pressedSkinBitmap != null && !currentlyEditingButton.pressedSkinBitmap.isRecycled()) currentlyEditingButton.pressedSkinBitmap.recycle();
                currentlyEditingButton.customPressedUri = finalUriStr; 
                currentlyEditingButton.pressedSkinBitmap = Bitmap.createScaledBitmap(raw, (int)(currentlyEditingButton.radius*2), (int)(currentlyEditingButton.radius*2), true);
                Toast.makeText(getContext(), "按下状态皮肤应用成功！", Toast.LENGTH_SHORT).show();
            } else if (imagePickerTarget == 7) { 
                if (dialogBgBitmap != null && !dialogBgBitmap.isRecycled()) dialogBgBitmap.recycle();
                dialogBgImageUri = finalUriStr; 
                dialogBgBitmap = Bitmap.createScaledBitmap(raw, 800, 800, true); 
                Toast.makeText(getContext(), "弹窗背景图应用成功！", Toast.LENGTH_SHORT).show();
            }
            
            if (raw != null && !raw.isRecycled()) raw.recycle(); 
            if (is != null) is.close();
            
            saveConfig();
            invalidate();
        } catch (Exception e) {}
        imagePickerTarget = 0;
    }
                   
               
            
    // 【补上这里缺失的收尾代码 👆】

    // =====================================
    // 渲染引擎
    // =====================================
             @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

                // 绘制遮罩图
                // 绘制遮罩图 (支持动态 GIF、镜像翻转、任意角度旋转)
        if ((!isFullscreenHideOverlay || isEditMode) && overlayMode > 0 && isOverlayVisible) {
            paintBtn.setAlpha(255);
            // ==== 渲染图 1 ====
            if (overlayMode >= 1) {
                canvas.save();
                if (overlayMovie1 != null) {
                    long now = android.os.SystemClock.uptimeMillis();
                    if (movieStart1 == 0) movieStart1 = now;
                    int dur = overlayMovie1.duration(); if (dur == 0) dur = 1000;
                    overlayMovie1.setTime((int)((now - movieStart1) % dur));
                    
                    float cx = overlayX1 + overlayMovie1.width() * overlayScale1 / 2f;
                    float cy = overlayY1 + overlayMovie1.height() * overlayScale1 / 2f;
                    canvas.rotate(overlayRotation1, cx, cy); // 需求3: 角度旋转
                    canvas.scale(overlayMirror1 ? -overlayScale1 : overlayScale1, overlayScale1, cx, cy); // 需求2: 镜像与缩放
                    canvas.translate(cx - overlayMovie1.width()/2f, cy - overlayMovie1.height()/2f);
                    overlayMovie1.draw(canvas, 0, 0); // 需求1: 动图渲染
                    invalidate(); // 强制不断重绘以播放动画
                } else if (overlayBmp1 != null) {
                    tempRect.set(overlayX1, overlayY1, overlayX1 + overlayBmp1.getWidth() * overlayScale1, overlayY1 + overlayBmp1.getHeight() * overlayScale1);
                    canvas.rotate(overlayRotation1, tempRect.centerX(), tempRect.centerY()); // 需求3: 角度旋转
                    canvas.scale(overlayMirror1 ? -1 : 1, 1, tempRect.centerX(), tempRect.centerY()); // 需求2: 镜像翻转
                    canvas.drawBitmap(overlayBmp1, null, tempRect, paintBtn);
                    if (isEditMode) { paintBtn.setStyle(Paint.Style.STROKE); paintBtn.setColor(Color.GREEN); paintBtn.setStrokeWidth(5f); canvas.drawRect(tempRect, paintBtn); paintBtn.setStyle(Paint.Style.FILL); }
                }
                canvas.restore();
            }
            // ==== 渲染图 2 ====
            if (overlayMode == 2) {
                canvas.save();
                if (overlayMovie2 != null) {
                    long now = android.os.SystemClock.uptimeMillis();
                    if (movieStart2 == 0) movieStart2 = now;
                    int dur = overlayMovie2.duration(); if (dur == 0) dur = 1000;
                    overlayMovie2.setTime((int)((now - movieStart2) % dur));
                    
                    float cx = overlayX2 + overlayMovie2.width() * overlayScale2 / 2f;
                    float cy = overlayY2 + overlayMovie2.height() * overlayScale2 / 2f;
                    canvas.rotate(overlayRotation2, cx, cy); 
                    canvas.scale(overlayMirror2 ? -overlayScale2 : overlayScale2, overlayScale2, cx, cy); 
                    canvas.translate(cx - overlayMovie2.width()/2f, cy - overlayMovie2.height()/2f);
                    overlayMovie2.draw(canvas, 0, 0); 
                    invalidate(); 
                } else if (overlayBmp2 != null) {
                    tempRect.set(overlayX2, overlayY2, overlayX2 + overlayBmp2.getWidth() * overlayScale2, overlayY2 + overlayBmp2.getHeight() * overlayScale2);
                    canvas.rotate(overlayRotation2, tempRect.centerX(), tempRect.centerY());
                    canvas.scale(overlayMirror2 ? -1 : 1, 1, tempRect.centerX(), tempRect.centerY());
                    canvas.drawBitmap(overlayBmp2, null, tempRect, paintBtn);
                    if (isEditMode) { paintBtn.setStyle(Paint.Style.STROKE); paintBtn.setColor(Color.BLUE); paintBtn.setStrokeWidth(5f); canvas.drawRect(tempRect, paintBtn); paintBtn.setStyle(Paint.Style.FILL); }
                }
                canvas.restore();
            }
        }
        
        

        // 动态计算菜单按键的位置和缩放
        float mw = 230 * menuScale;
        float mh = 90 * menuScale;
        menuButtonRect.set(menuX, menuY, menuX + mw, menuY + mh);

        paintMenu.setColor(Color.argb(menuAlpha, 20, 20, 25));
        paintMenu.setShadowLayer(8f * menuScale, 0, 4f * menuScale, Color.BLACK);
        canvas.drawRoundRect(menuButtonRect, 20 * menuScale, 20 * menuScale, paintMenu);
        paintText.setColor(Color.WHITE);
        paintText.setAlpha(menuAlpha);
        paintText.setTextSize(38f * menuScale);
        paintText.setShadowLayer(0,0,0,Color.TRANSPARENT);
        paintMenu.clearShadowLayer();
        canvas.drawText("⚙ 高级设置", menuButtonRect.centerX(), menuButtonRect.centerY() + (12 * menuScale), paintText);
        
        if (isEditMode) {
            canvas.drawColor(Color.argb(100, 255, 50, 50));
            if (isGridSnapMode) {
                paintBtn.setColor(Color.WHITE);
                paintBtn.setAlpha(30); 
                paintBtn.setStrokeWidth(1f);
                for (int i = 0; i < getWidth(); i += GRID_SIZE) canvas.drawLine(i, 0, i, getHeight(), paintBtn);
                for (int i = 0; i < getHeight(); i += GRID_SIZE) canvas.drawLine(0, i, getWidth(), i, paintBtn);
            }
                        paintText.setTextSize(Math.max(20f, getHeight() * 0.05f)); // 【修复】动态字体大小
            paintText.setShadowLayer(5f, 2f, 2f, Color.BLACK);
            canvas.drawText("【编辑模式】拖动调整，轻触设置", getWidth() / 2f, getHeight() * 0.12f, paintText); // 【修复】动态高度位置          
        }

              // 核心按键绘制逻辑
        for (VirtualButton btn : buttons) {
            if (joystickMode > 0 && btn.isDirectional) continue;

                        int idleAlpha = (int)(btn.alpha * 0.6f); 
            // 【修复】：去掉 Math.max 限制，让编辑模式完全反映真实透明度
            int currentAlpha = isEditMode ? btn.alpha : (btn.isPressed ? 255 : idleAlpha);            
            tempRect.set(btn.cx - btn.radius, btn.cy - btn.radius, btn.cx + btn.radius, btn.cy + btn.radius);
            
                        // ==== 新增：按键皮肤与按下特效渲染逻辑 ====
            Bitmap currentSkin = btn.skinBitmap;
            if (btn.isPressed && !isEditMode && btn.pressedSkinBitmap != null) {
                currentSkin = btn.pressedSkinBitmap;
            }

            if (currentSkin != null) {
                paintBtn.setAlpha(currentAlpha);
                canvas.drawBitmap(currentSkin, null, tempRect, paintBtn);
                
                // 自定义皮肤的纯色按下泛光补充 (只有当玩家设置了特效颜色时才画)
                if (btn.isPressed && !isEditMode && btn.pressedSkinBitmap == null && btn.pressedEffectColor != 0) {
                    paintBtn.setColor(btn.pressedEffectColor);
                    paintBtn.setAlpha(btn.pressedEffectAlpha);
                    if (btn.shape == SHAPE_CIRCLE) canvas.drawCircle(btn.cx, btn.cy, btn.radius, paintBtn);
                    else canvas.drawRoundRect(tempRect, btn.radius*0.3f, btn.radius*0.3f, paintBtn);
                }
            } else {
            
                // 原版渐变渲染
                int drawColor = (btn.isPressed && !isEditMode && btn.pressedEffectColor != 0) ? btn.pressedEffectColor : btn.color;
                int drawAlpha = (btn.isPressed && !isEditMode && btn.pressedEffectColor != 0) ? btn.pressedEffectAlpha : currentAlpha;
                
                int baseColor = Color.argb(drawAlpha, Color.red(drawColor), Color.green(drawColor), Color.blue(drawColor));
                int darkColor = Color.argb(drawAlpha, Math.max(0, Color.red(drawColor)-80), Math.max(0, Color.green(drawColor)-80), Math.max(0, Color.blue(drawColor)-80));
                
                RadialGradient gradient = new RadialGradient(btn.cx - btn.radius * 0.3f, btn.cy - btn.radius * 0.3f, btn.radius * 1.3f, baseColor, darkColor, Shader.TileMode.CLAMP);
                paintBtn.setShader(gradient);
                
                if (btn.isPressed && !isEditMode) {
                    paintBtn.setShadowLayer(25.0f, 0.0f, 0.0f, drawColor);
                } else if (currentAlpha > 80) {
                    paintBtn.setShadowLayer(10.0f, 0.0f, 5.0f, Color.argb(currentAlpha/2, 0, 0, 0));
                } else { paintBtn.clearShadowLayer(); }
                
                if (btn.shape == SHAPE_CIRCLE) canvas.drawCircle(btn.cx, btn.cy, btn.radius, paintBtn);
                else canvas.drawRoundRect(tempRect, btn.radius*0.3f, btn.radius*0.3f, paintBtn);
                
                paintBtn.clearShadowLayer(); paintBtn.setShader(null);
            }
        

                      // 绘制文字
            paintText.setColor(btn.textColor);
            paintText.setAlpha(currentAlpha);
            // 如果有多行，适当缩小一点字体，防止文字超出按键边缘
            boolean isMultiLine = btn.id.contains("\n");
            paintText.setTextSize(btn.radius * (isMultiLine ? 0.45f : 0.6f));
            paintText.setTextAlign(Paint.Align.CENTER); 
            paintText.setShadowLayer(3f, 1f, 1f, (btn.textColor == Color.BLACK) ? Color.WHITE : Color.BLACK);
            
            // 【优化】单行直接绘制，多行拆分并重新计算 Y 轴居中偏移
            float textOffset = (paintText.descent() - paintText.ascent()) / 2 - paintText.descent();
            
            if (!isMultiLine) {
                canvas.drawText(btn.id, btn.cx, btn.cy + textOffset, paintText);
            } else {
                String[] lines = btn.id.split("\n");
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
                paintBtn.setColor(Color.parseColor("#AAAAAA")); paintBtn.setStrokeWidth(25f);
                paintBtn.setStyle(Paint.Style.STROKE); paintBtn.setAlpha(currentAlpha);
                canvas.drawLine(joyBaseX, joyBaseY, joyKnobX, joyKnobY, paintBtn); 
                paintBtn.setStyle(Paint.Style.FILL);
                
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
            paintBtn.setStyle(Paint.Style.STROKE); paintBtn.setStrokeWidth(5f); paintBtn.setColor(Color.WHITE); paintBtn.setAlpha(255);
            canvas.drawCircle(joyBaseX, joyBaseY, joyRadius + 10, paintBtn); 
            canvas.drawCircle(joyBaseX, joyBaseY, joyHitboxRadius, dashPaint);
            paintText.setColor(Color.WHITE); 
            paintText.setTextSize(Math.max(16f, joyRadius * 0.25f)); // 【修复】跟随摇杆比例缩放
            paintText.setShadowLayer(3f,0,0,Color.BLACK);
            canvas.drawText("摇杆控制区", joyBaseX, joyBaseY - joyHitboxRadius - 10, paintText);            
            paintBtn.setStyle(Paint.Style.FILL); paintText.clearShadowLayer();
        }
    }
                        
                
    
    private void triggerVibrate() {
        if (!isVibrationOn || vibrationIntensity <= 0) return;
        try {
            android.os.Vibrator v = (android.os.Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    v.vibrate(android.os.VibrationEffect.createOneShot(vibrationIntensity, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(vibrationIntensity);
                }
            }
        } catch (Exception e) {}
    }

            
    
    // =====================================
    // 触控引擎
    // =====================================
        @Override
public boolean onTouchEvent(MotionEvent event) {
    int action = event.getActionMasked();
    int actionIndex = event.getActionIndex(); 

    // 【核心保命代码】：不论在什么模式，只要点到左上角菜单区域，立刻弹出主菜单
    // 删掉之前的 if (!isEditMode) 判断，让它变成“强行弹出”
        // 【修改后的核心保命代码】
    if (action == MotionEvent.ACTION_DOWN && menuButtonRect.contains(event.getX(actionIndex), event.getY(actionIndex))) {
        if (useRetroUIMode) {
            showRetroMainMenu(); // 走向全新的街机风主菜单
        } else {
            showMainMenu();      // 保持原汁原味的旧版 UI
        }
        return true; 
    }

    // 如果不是点菜单，才进入编辑模式的拖拽逻辑
    if (isEditMode) { 
        handleEditTouch(event); 
        return true; 
    }
    

        

        // --- 摇杆逻辑保持不变 ---
        if (joystickMode > 0) {
            boolean joyTouched = false;
            for (int i = 0; i < event.getPointerCount(); i++) {
                float px = event.getX(i), py = event.getY(i);
                if (px < getWidth() / 2f) {
                    if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                        if (Math.hypot(px - joyBaseX, py - joyBaseY) < joyHitboxRadius) joyPointerId = event.getPointerId(i);
                    }
                                        if (event.getPointerId(i) == joyPointerId) {
                        joyTouched = true;
                        float dx = px - joyBaseX, dy = py - joyBaseY;
                        float dist = (float) Math.hypot(dx, dy);
                        
                        // 【核心修改：限制摇杆帽只能在底座半径的 75% 范围内活动，且优化了误触范围】
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
            if (!joyTouched || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
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
                    if (Math.hypot(px - btn.cx, py - btn.cy) < btn.hitboxRadius) isTouchedNow = true;
                } else {
                    if (px > btn.cx - btn.hitboxRadius && px < btn.cx + btn.hitboxRadius && py > btn.cy - btn.hitboxRadius && py < btn.cy + btn.hitboxRadius) isTouchedNow = true;
                }
            }
            
                       // 【修改】区分触发：连发、宏、普通单点
            if (!btn.isPressed && isTouchedNow) {
                btn.isPressed = true;
                btn.pressTimestamp = System.currentTimeMillis(); // 瞬间打卡记录时间
                triggerVibrate();
                
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
        
        // 自动计算网格坐标
        float targetX = isGridSnapMode ? Math.round(x / GRID_SIZE) * GRID_SIZE : x;
        float targetY = isGridSnapMode ? Math.round(y / GRID_SIZE) * GRID_SIZE : y;

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                downTime = System.currentTimeMillis(); downX = x; downY = y;
                isDraggingJoy = false; draggedButton = null;
                
                // 排除菜单，只判断摇杆和按键
                if (joystickMode > 0 && Math.hypot(x - joyBaseX, y - joyBaseY) < joyRadius) { 
                    isDraggingJoy = true; 
                } else {
                    for (int i = buttons.size() - 1; i >= 0; i--) {
                        if (Math.hypot(x - buttons.get(i).cx, y - buttons.get(i).cy) < buttons.get(i).radius * 1.3f) {
                            draggedButton = buttons.get(i); break;
                        }
                    }
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (isDraggingJoy) { 
                    joyBaseX = targetX; joyBaseY = targetY; joyKnobX = targetX; joyKnobY = targetY; 
                    invalidate(); 
                } else if (draggedButton != null) { 
                    draggedButton.cx = targetX; draggedButton.cy = targetY; 
                    invalidate(); 
                }
                break;

            case MotionEvent.ACTION_UP:
                // 轻触判定：弹出设置弹窗
                if (System.currentTimeMillis() - downTime < 250 && Math.hypot(x - downX, y - downY) < 20) {
                    if (isDraggingJoy) {
                        DynamicGamepadView.this.showJoystickSettingsDialog();
                    } else if (draggedButton != null) {
                        DynamicGamepadView.this.showButtonSettingsDialog(draggedButton);
                    }
                }
                isDraggingJoy = false; draggedButton = null;
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
            .setTitle("⚠️ 覆盖警告")
            .setMessage("即将从手机存储中选择布局文件。\n导入成功后将【永久覆盖】你当前的按键布局，确定继续吗？")
            .setPositiveButton("选文件并覆盖", (d, w) -> {
                android.app.Activity activity = (android.app.Activity) getContext();
                FileActionFragment fragment = new FileActionFragment();
                android.os.Bundle args = new android.os.Bundle();
                args.putInt("action_type", 2); // 2 代表导入
                fragment.setArguments(args);
                activity.getFragmentManager().beginTransaction().add(fragment, "file_action").commitAllowingStateLoss();
            })
            .setNegativeButton("取消", null).show();
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
        dragHandle.setText("✋ 拖拽此处 | ⚙️ 游戏面板全局设置");
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
                // 【核心修复】：彻底抛弃不可靠的 DisplayMetrics，统一使用 View 当前真实渲染的长短边！
                int trueScreenH = Math.min(getWidth(), getHeight());
                
                // 【精细控制】：按当前真实高度比例截取，留出 120px 给顶部的拖拽条
                int maxHeight = (int) (trueScreenH * dialogHeightRatio) - 120; 
                
                int customHeightSpec = View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST);
                super.onMeasure(widthMeasureSpec, customHeightSpec);
            }
        };
        
        
        LinearLayout layout = new LinearLayout(getContext()); 
        layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(40, 20, 40, 40);

        layout.addView(createMenuButton(isEditMode ? "💾 保存并退出编辑" : "🛠️ 开启按键编辑", v -> { isEditMode = !isEditMode; if (!isEditMode) saveConfig(); invalidate(); dialog.dismiss(); }));
        layout.addView(createMenuButton("➕ 新建组合键/宏", v -> { 
            float scale = Math.max(0.5f, getHeight() / 1080f);
            VirtualButton newBtn = new VirtualButton("新键", getWidth() / 2f, getHeight() / 2f, 90 * scale, Color.RED, 150, Color.WHITE, SHAPE_CIRCLE, "Z+X", false);                                             
            buttons.add(newBtn); isEditMode = true; showButtonSettingsDialog(newBtn); dialog.dismiss();
        }));
        layout.addView(createMenuButton(isGridSnapMode ? "🧲 网格吸附：已开启" : "🧲 网格吸附：已关闭", v -> { isGridSnapMode = !isGridSnapMode; Toast.makeText(getContext(), isGridSnapMode ? "已开启网格吸附" : "已开启自由拖动", Toast.LENGTH_SHORT).show(); dialog.dismiss(); showMainMenu(); }));
        layout.addView(createMenuButton("🕹️ 切换摇杆形态", v -> { joystickMode = (joystickMode + 1) % 5; if (joystickMode == JOYSTICK_MODE_STYLE) refreshJoystickStyle(); saveConfig(); invalidate(); dialog.dismiss(); showMainMenu(); }));
        layout.addView(createMenuButton("📳 物理震动开关与强度", v -> { showVibrationSettingsDialog(); dialog.dismiss(); }));
        layout.addView(createMenuButton("📂 布局存档与导入导出", v -> { showProfileManager(); dialog.dismiss(); }));
        layout.addView(createMenuButton("🔄 恢复初始默认布局", v -> { loadDefaultLayout(); saveConfig(); invalidate(); dialog.dismiss(); }));
        layout.addView(createMenuButton("🖼️ 屏幕遮罩详细设置", v -> { showOverlaySettingsDialog(); dialog.dismiss(); }));
        layout.addView(createMenuButton(isOverlayVisible ? "👁️ 隐藏遮罩图 (当前:显示)" : "👁️ 显示遮罩图 (当前:隐藏)", v -> { isOverlayVisible = !isOverlayVisible; invalidate(); dialog.dismiss(); showMainMenu(); }));
        layout.addView(createMenuButton("📁 重新选择游戏数据目录", v -> { if (getContext() instanceof SDLActivity) ((SDLActivity) getContext()).checkAndPickFolder(); dialog.dismiss(); }));
        layout.addView(createMenuButton("⏱️ 面板自动隐藏设置", v -> { showAutoHideSettingsDialog(); dialog.dismiss(); }));
        layout.addView(createMenuButton("🎨 按键风格管理系统", v -> { showStyleManagerDialog(); dialog.dismiss(); }));
        layout.addView(createMenuButton("🕹️ 切换至专业街机面板", v -> { useRetroUIMode = true; saveConfig(); dialog.dismiss(); showRetroMainMenu(); }));
        layout.addView(createMenuButton("🪟 自定义设置弹窗 UI外观", v -> { showDialogCustomizationSettings(); dialog.dismiss(); }));

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
        
        CharSequence[] options = {"🎨 1. 选择并应用现有风格", "💾 2. 提取当前面板保存为新风格", "🗑️ 3. 删除当前选择的风格"};
        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("按键风格系统 (Style System)")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    String[] styleNames = new String[styleList.size()];
                    for(int i=0; i<styleList.size(); i++) styleNames[i] = styleList.get(i).styleName;
                    new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("应用风格 (将替换所有按键)")
                        .setSingleChoiceItems(styleNames, currentStyleIndex, (d, w) -> currentStyleIndex = w)
                        .setPositiveButton("确定应用", (d, w) -> {
                            GamepadStyle style = styleList.get(currentStyleIndex);
                            // 【修复 1】：每次切换风格，强制把摇杆模式改为跟随风格
                            joystickMode = JOYSTICK_MODE_STYLE;
                            refreshJoystickStyle(); 
                            for (VirtualButton b : buttons) {
                                if (!b.isDirectional) {
                                    // 【修复 2】：防空指针，如果没有皮肤图片强制赋值空字符串，覆盖掉以前残留的旧图片
                                    b.customImageUri = style.btnNormalUri != null ? style.btnNormalUri : ""; 
                                    b.customPressedUri = style.btnPressedUri != null ? style.btnPressedUri : "";
                                    b.pressedEffectColor = style.globalPressedColor; 
                                    b.pressedEffectAlpha = style.globalPressedAlpha;
                                    // 重新加载一次皮肤（如果为空就会自动清除 Bitmap）
                                    b.loadSkinFromUri(getContext());
                                }
                            }
                            saveConfig(); invalidate(); Toast.makeText(getContext(), "已应用风格: " + style.styleName, Toast.LENGTH_SHORT).show();
                        }).setNegativeButton("取消", null).show();
                } else if (which == 1) {
                    final EditText input = createEditText("给新风格命名", "我的自定义风格");
                    new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("提取并保存风格")
                        .setMessage("将自动提取当前1号位按键和摇杆的外观，打包为新风格。")
                        .setView(input)
                        .setPositiveButton("保存", (d, w) -> {
                            GamepadStyle newStyle = new GamepadStyle(input.getText().toString());
                            newStyle.joyBaseUri = joySkinBaseUri; newStyle.joyKnobUri = joySkinKnobUri;
                            for (VirtualButton b : buttons) { // 找第一个不是方向键的键作为风格模板
                                if (!b.isDirectional) {
                                    newStyle.btnNormalUri = b.customImageUri; newStyle.btnPressedUri = b.customPressedUri;
                                    newStyle.globalPressedColor = b.pressedEffectColor; newStyle.globalPressedAlpha = b.pressedEffectAlpha;
                                    break;
                                }
                            }
                            styleList.add(newStyle); currentStyleIndex = styleList.size() - 1;
                            saveConfig(); Toast.makeText(getContext(), "新风格保存成功！", Toast.LENGTH_SHORT).show();
                        }).setNegativeButton("取消", null).show();
                } else if (which == 2) {
                    if (currentStyleIndex <= 1) { Toast.makeText(getContext(), "系统默认风格不可删除！", Toast.LENGTH_SHORT).show(); return; }
                    styleList.remove(currentStyleIndex); currentStyleIndex = 0; saveConfig();
                    Toast.makeText(getContext(), "风格已删除", Toast.LENGTH_SHORT).show();
                }
            }).show();
    }
    


        private void showProfileManager() {
        CharSequence[] options = {"📤 导出: [按键布局] + [所有风格]", "📤 仅导出: [按键布局]", "📤 仅导出: [所有风格库]", "📥 导入: 从文件中读取配置"};
        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("数据导出与导入 (JSON)")
                .setItems(options, (dialog, which) -> {
                    android.app.Activity activity = (android.app.Activity) getContext();
                    FileActionFragment fragment = new FileActionFragment();
                    android.os.Bundle args = new android.os.Bundle();
                    
                    if (which <= 2) { // 导出选项
                        args.putInt("action_type", 1); 
                        try {
                            JSONObject root = new JSONObject();
                            if (which == 0 || which == 1) { // 包含布局
                                root.put("layout", new JSONArray(prefs.getString(KEY_LAYOUT_PREFIX + currentSlot, "[]")));
                            }
                            if (which == 0 || which == 2) { // 包含风格
                                JSONArray styleArr = new JSONArray();
                                for(GamepadStyle s : styleList) styleArr.put(s.toJson());
                                root.put("styles", styleArr);
                            }
                            args.putString("export_data", root.toString());
                        } catch(Exception e) {}
                    } else if (which == 3) { // 导入选项
                        args.putInt("action_type", 2); 
                    }
                    fragment.setArguments(args);
                    activity.getFragmentManager().beginTransaction().add(fragment, "file_action").commitAllowingStateLoss();
                }).show();
    }
    
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
                // 【核心修复】：彻底抛弃不可靠的 DisplayMetrics，统一使用 View 当前真实渲染的长短边！
                int trueScreenH = Math.min(getWidth(), getHeight());
                
                // 【精细控制】：按当前真实高度比例截取，留出 120px 给顶部的拖拽条
                int maxHeight = (int) (trueScreenH * dialogHeightRatio) - 120; 
                
                int customHeightSpec = View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST);
                super.onMeasure(widthMeasureSpec, customHeightSpec);
            }
        };
                       
                
    
        
        LinearLayout contentLayout = new LinearLayout(getContext());
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        
        contentLayout.addView(createTitle("🖼️ 遮罩图配置面板"));

        // 强制全屏隐藏开关
        final Button toggleHideBtn = new Button(getContext());
        toggleHideBtn.setText(isFullscreenHideOverlay ? "【开启】游戏强制全屏时隐藏遮罩" : "【关闭】全屏不影响遮罩");
        toggleHideBtn.setTextColor(Color.WHITE);
        toggleHideBtn.setBackgroundColor(isFullscreenHideOverlay ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));
        toggleHideBtn.setOnClickListener(v -> {
            isFullscreenHideOverlay = !isFullscreenHideOverlay;
            toggleHideBtn.setText(isFullscreenHideOverlay ? "【开启】游戏强制全屏时隐藏遮罩" : "【关闭】全屏不影响遮罩");
            toggleHideBtn.setBackgroundColor(isFullscreenHideOverlay ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));
        });
        contentLayout.addView(toggleHideBtn);

        // 模式选择
        contentLayout.addView(createTitle("模式选择："));
        final Spinner modeSpinner = new Spinner(getContext());
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, new String[]{"关闭遮罩", "开启一张遮罩图", "开启两张遮罩图"});
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
        contentLayout.addView(createTitle("--- 遮罩图 1 (绿框) ---"));
        LinearLayout btnLayout1 = new LinearLayout(getContext()); btnLayout1.setOrientation(LinearLayout.HORIZONTAL);
                Button mirrorBmp1 = new Button(getContext()); mirrorBmp1.setText(overlayMirror1 ? "↔️ 镜像: [开]" : "↔️ 镜像: [关]");
        mirrorBmp1.setOnClickListener(v -> { overlayMirror1 = !overlayMirror1; mirrorBmp1.setText(overlayMirror1 ? "↔️ 镜像: [开]" : "↔️ 镜像: [关]"); invalidate(); });
        btnLayout1.addView(mirrorBmp1);
        Button pickBmp1 = new Button(getContext()); pickBmp1.setText("选择图片"); pickBmp1.setOnClickListener(v -> { imagePickerTarget = 4; pickImage(); });
        Button clearBmp1 = new Button(getContext()); clearBmp1.setText("清除图片"); clearBmp1.setOnClickListener(v -> { overlayUri1 = ""; overlayBmp1 = null; overlayMovie1 = null; invalidate(); });
        btnLayout1.addView(pickBmp1); btnLayout1.addView(clearBmp1); contentLayout.addView(btnLayout1);

        // 【修复：先声明全部滑动条，系统才能认识它们】
        final SeekBar xBar1 = createColorBar(contentLayout, "X 轴位置", (int)overlayX1); xBar1.setMax(3000);
        final SeekBar yBar1 = createColorBar(contentLayout, "Y 轴位置", (int)overlayY1); yBar1.setMax(2000);
        final SeekBar sBar1 = createColorBar(contentLayout, "缩放比例 (%)", (int)(overlayScale1 * 100)); sBar1.setMax(500);
        final SeekBar rBar1 = createColorBar(contentLayout, "旋转角度 (°)", (int)overlayRotation1); rBar1.setMax(360);
                
        // 【修复：声明完滑动条后，再写监听器，并只写一次】
        SeekBar.OnSeekBarChangeListener valUpdater1 = new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if(s==xBar1) overlayX1 = p; else if(s==yBar1) overlayY1 = p; else if(s==sBar1) overlayScale1 = p / 100f; else if(s==rBar1) overlayRotation1 = p;
                invalidate();
            }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        };
        xBar1.setOnSeekBarChangeListener(valUpdater1); yBar1.setOnSeekBarChangeListener(valUpdater1); sBar1.setOnSeekBarChangeListener(valUpdater1); rBar1.setOnSeekBarChangeListener(valUpdater1);

        // ==== 第二张图控件 ====
        contentLayout.addView(createTitle("--- 遮罩图 2 (蓝框) ---"));
        LinearLayout btnLayout2 = new LinearLayout(getContext()); btnLayout2.setOrientation(LinearLayout.HORIZONTAL);
                Button mirrorBmp2 = new Button(getContext()); mirrorBmp2.setText(overlayMirror2 ? "↔️ 镜像: [开]" : "↔️ 镜像: [关]");
        mirrorBmp2.setOnClickListener(v -> { overlayMirror2 = !overlayMirror2; mirrorBmp2.setText(overlayMirror2 ? "↔️ 镜像: [开]" : "↔️ 镜像: [关]"); invalidate(); });
        btnLayout2.addView(mirrorBmp2);
        Button pickBmp2 = new Button(getContext()); pickBmp2.setText("选择图片"); pickBmp2.setOnClickListener(v -> { imagePickerTarget = 5; pickImage(); });
        Button clearBmp2 = new Button(getContext()); clearBmp2.setText("清除图片"); clearBmp2.setOnClickListener(v -> { overlayUri2 = ""; overlayBmp2 = null; overlayMovie2 = null; invalidate(); });        
        btnLayout2.addView(pickBmp2); btnLayout2.addView(clearBmp2); contentLayout.addView(btnLayout2);

        // 【修复：同理，先声明滑动条】
        final SeekBar xBar2 = createColorBar(contentLayout, "X 轴位置", (int)overlayX2); xBar2.setMax(3000);
        final SeekBar yBar2 = createColorBar(contentLayout, "Y 轴位置", (int)overlayY2); yBar2.setMax(2000);
        final SeekBar sBar2 = createColorBar(contentLayout, "缩放比例 (%)", (int)(overlayScale2 * 100)); sBar2.setMax(500);
        final SeekBar rBar2 = createColorBar(contentLayout, "旋转角度 (°)", (int)overlayRotation2); rBar2.setMax(360);
        
        // 【修复：最后绑定唯一正确的监听器】
        SeekBar.OnSeekBarChangeListener valUpdater2 = new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if(s==xBar2) overlayX2 = p; else if(s==yBar2) overlayY2 = p; else if(s==sBar2) overlayScale2 = p / 100f; else if(s==rBar2) overlayRotation2 = p;
                invalidate();
            }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        };
        xBar2.setOnSeekBarChangeListener(valUpdater2); yBar2.setOnSeekBarChangeListener(valUpdater2); sBar2.setOnSeekBarChangeListener(valUpdater2); rBar2.setOnSeekBarChangeListener(valUpdater2);

               

        Button saveBtn = new Button(getContext());
        saveBtn.setText("💾 保存并关闭");
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
        final android.app.Dialog dialog = new android.app.Dialog(getContext(), android.R.style.Theme_DeviceDefault_Dialog);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 60, 60, 60);
                layout.setBackground(getCustomDialogBackground());

        layout.addView(createTitle("📳 震动功能控制"));

        // 震动总开关
        final Button toggleBtn = new Button(getContext());
        toggleBtn.setText(isVibrationOn ? "当前状态：已开启 (点击关闭)" : "当前状态：已关闭 (点击开启)");
        toggleBtn.setTextColor(Color.WHITE);
        toggleBtn.setBackgroundColor(isVibrationOn ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));
        toggleBtn.setOnClickListener(v -> {
            isVibrationOn = !isVibrationOn;
            toggleBtn.setText(isVibrationOn ? "当前状态：已开启 (点击关闭)" : "当前状态：已关闭 (点击开启)");
            toggleBtn.setBackgroundColor(isVibrationOn ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));
            if (isVibrationOn) triggerVibrate();
        });
        layout.addView(toggleBtn);

        // 震动强度滑动条 (复用刚才修改好的自带输入框的进度条)
        final SeekBar intensityBar = createColorBar(layout, "震动时长 / 毫秒 (拖动测试)", vibrationIntensity);
        intensityBar.setMax(100); 
        intensityBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
    public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                vibrationIntensity = Math.max(1, p); // 防止设置为0
                if ((fromUser || s.hasFocus()) && isVibrationOn) {
                    triggerVibrate();
                }
            }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        });

        Button saveBtn = new Button(getContext());
        saveBtn.setText("💾 保存并关闭");
        saveBtn.setTextColor(Color.WHITE);
        saveBtn.setBackgroundColor(Color.parseColor("#1976D2"));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(0, 40, 0, 0); saveBtn.setLayoutParams(btnParams);
        saveBtn.setOnClickListener(v -> { saveConfig(); dialog.dismiss(); });
        layout.addView(saveBtn);

        dialog.setContentView(layout);
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
        dragHandle.setText("✋ 拖拽窗口 | 🪟 全局弹窗 UI 实验室");
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
                // 【核心修复】：彻底抛弃不可靠的 DisplayMetrics，统一使用 View 当前真实渲染的长短边！
                int trueScreenH = Math.min(getWidth(), getHeight());
                
                // 【精细控制】：按当前真实高度比例截取，留出 120px 给顶部的拖拽条
                int maxHeight = (int) (trueScreenH * dialogHeightRatio) - 120; 
                
                int customHeightSpec = View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST);
                super.onMeasure(widthMeasureSpec, customHeightSpec);
            }
        };
        
        
        LinearLayout layout = new LinearLayout(getContext()); 
        layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(50, 20, 50, 50);

        layout.addView(createTitle("0. 窗口全局缩放比例"));
        final SeekBar widthBar = createColorBar(layout, "↔️ 窗口宽度百分比", (int)(dialogWidthRatio * 100)); widthBar.setMax(100);
        final SeekBar heightBar = createColorBar(layout, "↕️ 窗口高度百分比", (int)(dialogHeightRatio * 100)); heightBar.setMax(100);
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
        layout.addView(createTitle("1. 全局字体大小与透明度"));
        final SeekBar sizeBar = createColorBar(layout, "字体缩放大小", (int)dialogTextSize); sizeBar.setMax(30);
        final SeekBar alphaBar = createColorBar(layout, "背景不透明度 (0为全透)", dialogBgAlpha); 
        
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
        layout.addView(createTitle("2. 背景纯色与文字颜色"));
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

        final EditText hexInput = createEditText("背景颜色代码: #222222", String.format("#%06X", (0xFFFFFF & dialogBgColor))); layout.addView(hexInput);
        final View colorPreview = new View(getContext());
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 60);
        previewParams.setMargins(0, 10, 0, 30); colorPreview.setLayoutParams(previewParams); 
        final android.graphics.drawable.GradientDrawable previewBg = new android.graphics.drawable.GradientDrawable();
        previewBg.setCornerRadius(20f); previewBg.setColor(dialogBgColor); colorPreview.setBackground(previewBg); layout.addView(colorPreview);

        final int[] rgb = {Color.red(dialogBgColor), Color.green(dialogBgColor), Color.blue(dialogBgColor)};
        final SeekBar rBar = createColorBar(layout, "🔴 红", rgb[0]); 
        final SeekBar gBar = createColorBar(layout, "🟢 绿", rgb[1]); 
        final SeekBar bBar = createColorBar(layout, "🔵 蓝", rgb[2]);

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
        layout.addView(createTitle("3. 注入自定义背景图"));
        LinearLayout imgLayout = new LinearLayout(getContext()); imgLayout.setOrientation(LinearLayout.HORIZONTAL);
        Button pickImgBtn = new Button(getContext()); pickImgBtn.setText("🖼️ 选择图片"); pickImgBtn.setTextColor(Color.WHITE); pickImgBtn.setBackgroundColor(Color.parseColor("#4CAF50"));
        pickImgBtn.setOnClickListener(v -> {
            imagePickerTarget = 7; 
            android.app.Activity activity = (android.app.Activity) getContext(); FileActionFragment fragment = new FileActionFragment();
            android.os.Bundle args = new android.os.Bundle(); args.putInt("action_type", 0); fragment.setArguments(args); 
            activity.getFragmentManager().beginTransaction().add(fragment, "file_action").commitAllowingStateLoss();
        }); imgLayout.addView(pickImgBtn);
        
        Button clearImgBtn = new Button(getContext()); clearImgBtn.setText("❌ 清除图片"); clearImgBtn.setTextColor(Color.WHITE); clearImgBtn.setBackgroundColor(Color.parseColor("#F44336"));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); cp.setMargins(20, 0, 0, 0); clearImgBtn.setLayoutParams(cp);
        clearImgBtn.setOnClickListener(v -> { dialogBgImageUri = ""; dialogBgBitmap = null; rootLayout.setBackground(getCustomDialogBackground()); Toast.makeText(getContext(), "已清除", Toast.LENGTH_SHORT).show(); });
        imgLayout.addView(clearImgBtn); layout.addView(imgLayout);

        // --- 4. 底部三按钮 ---
        LinearLayout bottomButtons = new LinearLayout(getContext()); bottomButtons.setOrientation(LinearLayout.HORIZONTAL); bottomButtons.setPadding(0, 50, 0, 0);
        Button defaultBtn = new Button(getContext()); defaultBtn.setText("🔄 默认"); defaultBtn.setTextColor(Color.WHITE); defaultBtn.setBackgroundColor(Color.parseColor("#9E9E9E"));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); btnParams.setMargins(5, 0, 5, 0); defaultBtn.setLayoutParams(btnParams);
                defaultBtn.setOnClickListener(v -> {
            dialogBgColor = Color.parseColor("#222222"); dialogBgAlpha = 230; dialogTextColor = Color.WHITE; dialogTextSize = 14f; dialogBgImageUri = ""; dialogBgBitmap = null;
            dialogWidthRatio = 0.8f; dialogHeightRatio = 0.8f; // 【补充修复：强制重置窗口比例】
            saveConfig(); dialog.dismiss(); showDialogCustomizationSettings();
        }); 
        bottomButtons.addView(defaultBtn);
        

        Button cancelBtn = new Button(getContext()); cancelBtn.setText("❌ 取消"); cancelBtn.setTextColor(Color.WHITE); cancelBtn.setBackgroundColor(Color.parseColor("#F44336"));
        cancelBtn.setLayoutParams(btnParams);
        cancelBtn.setOnClickListener(v -> {
            dialogBgColor = backupColor; dialogBgAlpha = backupAlpha; dialogTextColor = backupTextColor; dialogTextSize = backupTextSize; dialogBgImageUri = backupUri; dialogBgBitmap = backupBmp;
            dialog.dismiss();
        }); bottomButtons.addView(cancelBtn);

        Button saveBtn = new Button(getContext()); saveBtn.setText("💾 保存"); saveBtn.setTextColor(Color.WHITE); saveBtn.setBackgroundColor(Color.parseColor("#1976D2"));
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
        
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 60, 60, 60);
                layout.setBackground(getCustomDialogBackground());

        layout.addView(createTitle("⏱️ 面板自动隐藏设置"));

        // 隐藏总开关
        final Button toggleBtn = new Button(getContext());
        toggleBtn.setText(isAutoHideEnabled ? "当前状态：已开启 (点击关闭)" : "当前状态：已关闭 (面板长亮)");
        toggleBtn.setTextColor(Color.WHITE);
        toggleBtn.setBackgroundColor(isAutoHideEnabled ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));
        toggleBtn.setOnClickListener(v -> {
            isAutoHideEnabled = !isAutoHideEnabled;
            toggleBtn.setText(isAutoHideEnabled ? "当前状态：已开启 (点击关闭)" : "当前状态：已关闭 (面板长亮)");
            toggleBtn.setBackgroundColor(isAutoHideEnabled ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));
            // 若关闭隐藏，立刻通知 Activity 移除现有的隐藏任务
            if (!isAutoHideEnabled && getContext() instanceof SDLActivity) {
                ((SDLActivity) getContext()).cancelAutoHide();
            }
        });
        layout.addView(toggleBtn);

        // 延迟时间滑动条
        final SeekBar timeBar = createColorBar(layout, "无操作自动隐藏延迟 / 秒", autoHideSeconds);
        timeBar.setMax(60); 
        timeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                autoHideSeconds = Math.max(1, p); // 防止设置为0
            }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        });

        Button saveBtn = new Button(getContext());
        saveBtn.setText("💾 保存并关闭");
        saveBtn.setTextColor(Color.WHITE);
        saveBtn.setBackgroundColor(Color.parseColor("#1976D2"));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(0, 40, 0, 0); saveBtn.setLayoutParams(btnParams);
        saveBtn.setOnClickListener(v -> { saveConfig(); dialog.dismiss(); });
        layout.addView(saveBtn);

        dialog.setContentView(layout);
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
        dragHandle.setText("✋ 按住此处拖拽窗口 | 🕹️ 摇杆配置");
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
                // 【核心修复】：彻底抛弃不可靠的 DisplayMetrics，统一使用 View 当前真实渲染的长短边！
                int trueScreenH = Math.min(getWidth(), getHeight());
                
                // 【精细控制】：按当前真实高度比例截取，留出 120px 给顶部的拖拽条
                int maxHeight = (int) (trueScreenH * dialogHeightRatio) - 120; 
                
                int customHeightSpec = View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST);
                super.onMeasure(widthMeasureSpec, customHeightSpec);
            }
        };
                        
                
        
        LinearLayout layout = new LinearLayout(getContext()); 
        layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(50, 20, 50, 50);

        layout.addView(createTitle("1. 摇杆中心球颜色 (双向同步):"));
        final EditText hexInput = createEditText("颜色代码如: #FF5555", String.format("#%06X", (0xFFFFFF & joyColor))); 
        layout.addView(hexInput);
        
        final View colorPreview = new View(getContext());
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 60);
        previewParams.setMargins(0, 10, 0, 30); colorPreview.setLayoutParams(previewParams); 
        final android.graphics.drawable.GradientDrawable previewBg = new android.graphics.drawable.GradientDrawable();
        previewBg.setCornerRadius(20f); previewBg.setColor(joyColor); colorPreview.setBackground(previewBg);
        layout.addView(colorPreview);

        final int[] rgb = {Color.red(joyColor), Color.green(joyColor), Color.blue(joyColor)};
        final SeekBar redBar = createColorBar(layout, "🔴 红色分量 (R)", rgb[0]); 
        final SeekBar greenBar = createColorBar(layout, "🟢 绿色分量 (G)", rgb[1]); 
        final SeekBar blueBar = createColorBar(layout, "🔵 蓝色分量 (B)", rgb[2]);

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

        layout.addView(createTitle("2. 尺寸与判定范围:"));
        final SeekBar alphaBar = createColorBar(layout, "不透明度 (0-255)", joyAlpha); 
        final SeekBar sizeBar = createColorBar(layout, "摇杆视觉大小", (int)joyRadius); sizeBar.setMax(400);
        final SeekBar hitboxBar = createColorBar(layout, "触摸判定半径 (黄色虚线)", (int)joyHitboxRadius); hitboxBar.setMax(500);
        
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

        layout.addView(createTitle("3. 自定义双层皮肤:"));
        // 外框皮肤按钮
        LinearLayout baseLayout = new LinearLayout(getContext()); baseLayout.setOrientation(LinearLayout.HORIZONTAL);
        Button btnPickBase = new Button(getContext()); btnPickBase.setText("🖼️ 外框皮肤"); btnPickBase.setTextColor(Color.WHITE); btnPickBase.setBackgroundColor(Color.parseColor("#4CAF50"));
        btnPickBase.setOnClickListener(v -> {
            imagePickerTarget = 1; 
            android.app.Activity activity = (android.app.Activity) getContext(); FileActionFragment fragment = new FileActionFragment();
            android.os.Bundle args = new android.os.Bundle(); args.putInt("action_type", 0); fragment.setArguments(args); 
            activity.getFragmentManager().beginTransaction().add(fragment, "file_action").commitAllowingStateLoss();
        }); baseLayout.addView(btnPickBase);
        Button btnClearBase = new Button(getContext()); btnClearBase.setText("❌ 清除"); btnClearBase.setTextColor(Color.WHITE); btnClearBase.setBackgroundColor(Color.parseColor("#F44336"));
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); p1.setMargins(20, 0, 0, 0); btnClearBase.setLayoutParams(p1);
        btnClearBase.setOnClickListener(v -> { joySkinBaseUri = ""; joySkinBaseBitmap = null; Toast.makeText(getContext(), "已清除", Toast.LENGTH_SHORT).show(); invalidate(); });
        baseLayout.addView(btnClearBase); layout.addView(baseLayout);
        
        // 中心皮肤按钮
        LinearLayout knobLayout = new LinearLayout(getContext()); knobLayout.setOrientation(LinearLayout.HORIZONTAL); knobLayout.setPadding(0, 20, 0, 0);
        Button btnPickKnob = new Button(getContext()); btnPickKnob.setText("🖼️ 中心皮肤"); btnPickKnob.setTextColor(Color.WHITE); btnPickKnob.setBackgroundColor(Color.parseColor("#4CAF50"));
        btnPickKnob.setOnClickListener(v -> {
            imagePickerTarget = 2; 
            android.app.Activity activity = (android.app.Activity) getContext(); FileActionFragment fragment = new FileActionFragment();
            android.os.Bundle args = new android.os.Bundle(); args.putInt("action_type", 0); fragment.setArguments(args); 
            activity.getFragmentManager().beginTransaction().add(fragment, "file_action").commitAllowingStateLoss();
        }); knobLayout.addView(btnPickKnob);
        Button btnClearKnob = new Button(getContext()); btnClearKnob.setText("❌ 清除"); btnClearKnob.setTextColor(Color.WHITE); btnClearKnob.setBackgroundColor(Color.parseColor("#F44336"));
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); p2.setMargins(20, 0, 0, 0); btnClearKnob.setLayoutParams(p2);
        btnClearKnob.setOnClickListener(v -> { joySkinKnobUri = ""; joySkinKnobBitmap = null; Toast.makeText(getContext(), "已清除", Toast.LENGTH_SHORT).show(); invalidate(); });
        knobLayout.addView(btnClearKnob); layout.addView(knobLayout);

        LinearLayout bottomButtons = new LinearLayout(getContext()); bottomButtons.setOrientation(LinearLayout.HORIZONTAL); bottomButtons.setPadding(0, 50, 0, 0);
        Button deleteBtn = new Button(getContext()); deleteBtn.setText("🔄 恢复默认"); deleteBtn.setTextColor(Color.WHITE); deleteBtn.setBackgroundColor(Color.parseColor("#D32F2F"));
        deleteBtn.setOnClickListener(v -> { 
            joyAlpha = 200; joyRadius = 180; joyHitboxRadius = 270; joyColor = Color.parseColor("#FF5555"); joySkinBaseUri = ""; joySkinKnobUri = ""; joySkinBaseBitmap = null; joySkinKnobBitmap = null;
            saveConfig(); invalidate(); dialog.dismiss(); 
        }); bottomButtons.addView(deleteBtn);
        
        Button saveBtn = new Button(getContext()); saveBtn.setText("💾 保存退出"); saveBtn.setTextColor(Color.WHITE); saveBtn.setBackgroundColor(Color.parseColor("#1976D2"));
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
        dragHandle.setText("✋ 按住此处拖拽窗口 | 🔧 配置: " + btn.id);
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
                // 【核心修复】：彻底抛弃不可靠的 DisplayMetrics，统一使用 View 当前真实渲染的长短边！
                int trueScreenH = Math.min(getWidth(), getHeight());
                
                // 【精细控制】：按当前真实高度比例截取，留出 120px 给顶部的拖拽条
                int maxHeight = (int) (trueScreenH * dialogHeightRatio) - 120; 
                
                int customHeightSpec = View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST);
                super.onMeasure(widthMeasureSpec, customHeightSpec);
            }
        };
                        
                
        
        LinearLayout layout = new LinearLayout(getContext()); 
        layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(50, 20, 50, 50);

        layout.addView(createTitle("1. 按键名称与映射:"));
        final EditText inputName = createEditText("显示名称 (如: A)", btn.id); layout.addView(inputName);
        final EditText inputKey = createEditText("映射键值 (如: DOWN/A)", btn.keyMapStr); layout.addView(inputKey);

        layout.addView(createTitle("2. 按键样式:"));
        final Spinner textColorSpinner = new Spinner(getContext());
        ArrayAdapter<String> textAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, TEXT_COLOR_NAMES);
        textColorSpinner.setAdapter(textAdapter);
        for (int i=0; i<TEXT_COLOR_VALUES.length; i++) { if (btn.textColor == TEXT_COLOR_VALUES[i]) { textColorSpinner.setSelection(i); break; } }
        layout.addView(textColorSpinner);

        final Spinner shapeSpinner = new Spinner(getContext());
        ArrayAdapter<String> shapeAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, SHAPE_NAMES);
        shapeSpinner.setAdapter(shapeAdapter); shapeSpinner.setSelection(btn.shape); layout.addView(shapeSpinner);

        layout.addView(createTitle("3. 按键颜色 (代码与滑块双向同步):"));
        final EditText hexInput = createEditText("颜色代码如: #FF0000", String.format("#%06X", (0xFFFFFF & btn.color))); 
        layout.addView(hexInput);

        final View colorPreview = new View(getContext());
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 60);
        previewParams.setMargins(0, 10, 0, 30); colorPreview.setLayoutParams(previewParams); 
        final android.graphics.drawable.GradientDrawable previewBg = new android.graphics.drawable.GradientDrawable();
        previewBg.setCornerRadius(20f); previewBg.setColor(btn.color); colorPreview.setBackground(previewBg);
        layout.addView(colorPreview);

        final int[] rgb = {Color.red(btn.color), Color.green(btn.color), Color.blue(btn.color)};
        final SeekBar redBar = createColorBar(layout, "🔴 红色分量 (R)", rgb[0]); 
        final SeekBar greenBar = createColorBar(layout, "🟢 绿色分量 (G)", rgb[1]); 
        final SeekBar blueBar = createColorBar(layout, "🔵 蓝色分量 (B)", rgb[2]);

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

        layout.addView(createTitle("4. 尺寸与隐藏参数:"));
        final SeekBar alphaBar = createColorBar(layout, "可见透明度 (拉到0为隐藏)", btn.alpha); 
        final SeekBar sizeBar = createColorBar(layout, "视觉大小", (int)btn.radius); sizeBar.setMax(300);
        final SeekBar hitboxBar = createColorBar(layout, "触摸判定范围 (黄线圈)", (int)btn.hitboxRadius); hitboxBar.setMax(400);

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

        layout.addView(createTitle("5. 自定义图片皮肤:"));
        LinearLayout skinLayout = new LinearLayout(getContext()); skinLayout.setOrientation(LinearLayout.HORIZONTAL);
                Button btnPickImage = new Button(getContext()); btnPickImage.setText("🖼️ 选择皮肤"); btnPickImage.setTextColor(Color.WHITE); btnPickImage.setBackgroundColor(Color.parseColor("#4CAF50"));
        btnPickImage.setOnClickListener(v -> {
            // 【新增这一句】，告诉回调函数这是普通按键在选图片
            imagePickerTarget = 3; currentlyEditingButton = btn; 
            android.app.Activity activity = (android.app.Activity) getContext(); FileActionFragment fragment = new FileActionFragment();
            android.os.Bundle args = new android.os.Bundle(); args.putInt("action_type", 0);
            fragment.setArguments(args); activity.getFragmentManager().beginTransaction().add(fragment, "file_action").commitAllowingStateLoss();
        }); skinLayout.addView(btnPickImage);
        
        
        Button btnClearImage = new Button(getContext()); btnClearImage.setText("❌ 移除皮肤"); btnClearImage.setTextColor(Color.WHITE); btnClearImage.setBackgroundColor(Color.parseColor("#F44336"));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); btnParams.setMargins(20, 0, 0, 0); btnClearImage.setLayoutParams(btnParams);
        btnClearImage.setOnClickListener(v -> { btn.customImageUri = ""; btn.skinBitmap = null; Toast.makeText(getContext(), "已恢复默认材质", Toast.LENGTH_SHORT).show(); invalidate(); });
                skinLayout.addView(btnClearImage); layout.addView(skinLayout);
                
                        layout.addView(createTitle("7. 连发功能 (Turbo):"));
        final Button turboBtn = new Button(getContext());
        turboBtn.setText(btn.isTurbo ? "🔥 连发状态：已开启" : "⚪ 连发状态：已关闭");
        turboBtn.setTextColor(Color.WHITE);
        turboBtn.setBackgroundColor(btn.isTurbo ? Color.parseColor("#FF5722") : Color.parseColor("#555555"));
        turboBtn.setOnClickListener(v -> {
            btn.isTurbo = !btn.isTurbo;
            turboBtn.setText(btn.isTurbo ? "🔥 连发状态：已开启" : "⚪ 连发状态：已关闭");
            turboBtn.setBackgroundColor(btn.isTurbo ? Color.parseColor("#FF5722") : Color.parseColor("#555555"));
        });
        layout.addView(turboBtn);

        final SeekBar turboIntervalBar = createColorBar(layout, "⏱️ 连发触发间隔 (毫秒)", btn.turboInterval);
        turboIntervalBar.setMax(500);
        turboIntervalBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) btn.turboInterval = Math.max(10, p); // 最小间隔10ms防卡死
            }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        });
                
            


        // ================= 【新增：按下状态的UI调节控制】 =================
                // ================= 【修改：增加按下特效的实时颜色预览】 =================
        layout.addView(createTitle("6. 按下状态特效 (独立颜色与皮肤):"));
        final EditText hexInputP = createEditText("颜色如: #4CAF50 (填 #000000 变回普通渐变)", String.format("#%06X", (0xFFFFFF & btn.pressedEffectColor))); 
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

        final SeekBar alphaBarP = createColorBar(layout, "按下特效不透明度", btn.pressedEffectAlpha); 
        

        final int[] rgbP = {Color.red(btn.pressedEffectColor), Color.green(btn.pressedEffectColor), Color.blue(btn.pressedEffectColor)};
        final SeekBar rBarP = createColorBar(layout, "🔴 按下红 (R)", rgbP[0]); 
        final SeekBar gBarP = createColorBar(layout, "🟢 按下绿 (G)", rgbP[1]); 
        final SeekBar bBarP = createColorBar(layout, "🔵 按下蓝 (B)", rgbP[2]);

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
        Button btnPickImageP = new Button(getContext()); btnPickImageP.setText("🖼️ 按下皮肤"); btnPickImageP.setTextColor(Color.WHITE); btnPickImageP.setBackgroundColor(Color.parseColor("#4CAF50"));
        btnPickImageP.setOnClickListener(v -> {
            imagePickerTarget = 6; currentlyEditingButton = btn; 
            android.app.Activity activity = (android.app.Activity) getContext(); FileActionFragment fragment = new FileActionFragment();
            android.os.Bundle args = new android.os.Bundle(); args.putInt("action_type", 0);
            fragment.setArguments(args); activity.getFragmentManager().beginTransaction().add(fragment, "file_action").commitAllowingStateLoss();
        }); skinLayoutP.addView(btnPickImageP);
        
        Button btnClearImageP = new Button(getContext()); btnClearImageP.setText("❌ 移除按下皮肤"); btnClearImageP.setTextColor(Color.WHITE); btnClearImageP.setBackgroundColor(Color.parseColor("#F44336"));
        LinearLayout.LayoutParams btnParamsP = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); btnParamsP.setMargins(20, 0, 0, 0); btnClearImageP.setLayoutParams(btnParamsP);
        btnClearImageP.setOnClickListener(v -> { btn.customPressedUri = ""; btn.pressedSkinBitmap = null; Toast.makeText(getContext(), "已恢复无皮肤状态", Toast.LENGTH_SHORT).show(); invalidate(); });
        skinLayoutP.addView(btnClearImageP); layout.addView(skinLayoutP);
        // =========================================================================

        LinearLayout bottomButtons = new LinearLayout(getContext()); bottomButtons.setOrientation(LinearLayout.HORIZONTAL); bottomButtons.setPadding(0, 50, 0, 0);
        Button deleteBtn = new Button(getContext()); deleteBtn.setText("🗑️ 删除按键"); deleteBtn.setTextColor(Color.WHITE); deleteBtn.setBackgroundColor(Color.parseColor("#D32F2F"));
                deleteBtn.setOnClickListener(v -> { 
            btn.stopTurbo(); btn.isMacroPlaying = false; // 修复：先斩断后台独立线程
            buttons.remove(btn); 
            saveConfig(); invalidate(); dialog.dismiss(); 
        });
        
        bottomButtons.addView(deleteBtn);
        
        Button saveBtn = new Button(getContext()); saveBtn.setText("💾 保存修改并退出"); saveBtn.setTextColor(Color.WHITE); saveBtn.setBackgroundColor(Color.parseColor("#1976D2"));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); saveParams.setMargins(20, 0, 0, 0); saveBtn.setLayoutParams(saveParams);
        saveBtn.setOnClickListener(v -> {
            btn.id = inputName.getText().toString(); btn.textColor = TEXT_COLOR_VALUES[textColorSpinner.getSelectedItemPosition()];
            btn.shape = shapeSpinner.getSelectedItemPosition(); btn.keyMapStr = inputKey.getText().toString().trim().toUpperCase(); 
            btn.parseKeyCodes(); saveConfig(); invalidate(); dialog.dismiss();
        });
        bottomButtons.addView(saveBtn); layout.addView(bottomButtons);

        scroll.addView(layout); rootLayout.addView(scroll);
        dialog.setContentView(rootLayout); setupMovableDialog(dialog, dragHandle); dialog.show();
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
        final SeekBar sb = new SeekBar(getContext()) {
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
        sb.setPadding(0, 20, 0, 30);
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
                // 【新增：保存按下状态特效】
                obj.put("pressedSkin", btn.customPressedUri);
                obj.put("pressedColor", btn.pressedEffectColor);
                obj.put("pressedAlpha", btn.pressedEffectAlpha);
                obj.put("isTurbo", btn.isTurbo);
                obj.put("turboInterval", btn.turboInterval);
                array.put(obj);
            }
            editor.putString(KEY_LAYOUT_PREFIX + currentSlot, array.toString());
            editor.putInt("JoystickMode_" + currentSlot, joystickMode);
            editor.putFloat("JoyX_" + currentSlot, joyBaseX);
            editor.putFloat("JoyY_" + currentSlot, joyBaseY);
            editor.putFloat("JoyR_" + currentSlot, joyRadius);
            editor.putFloat("JoyHitR_" + currentSlot, joyHitboxRadius);
            editor.putInt("JoyA_" + currentSlot, joyAlpha);
            editor.putInt("JoyColor_" + currentSlot, joyColor);
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
            editor.putFloat("OverlayScale1_" + currentSlot, overlayScale1);
            editor.putString("OverlayUri2_" + currentSlot, overlayUri2);
            editor.putFloat("OverlayX2_" + currentSlot, overlayX2);
            editor.putFloat("OverlayY2_" + currentSlot, overlayY2);
            editor.putFloat("OverlayScale2_" + currentSlot, overlayScale2);
            editor.putFloat("OverlayRot1_" + currentSlot, overlayRotation1);
            editor.putFloat("OverlayRot2_" + currentSlot, overlayRotation2);
            editor.putBoolean("FS_HideOverlay_" + currentSlot, isFullscreenHideOverlay);
            editor.putBoolean("AutoHide_" + currentSlot, isAutoHideEnabled);
editor.putInt("AutoHideSec_" + currentSlot, autoHideSeconds);
            editor.putBoolean("RetroUI_" + currentSlot, useRetroUIMode);
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
                btn.customImageUri = o.optString("skin", ""); 
                // 【新增：读取按下状态特效】
                btn.customPressedUri = o.optString("pressedSkin", "");
                btn.pressedEffectColor = o.optInt("pressedColor", 0);
                btn.pressedEffectAlpha = o.optInt("pressedAlpha", 150);
                btn.isTurbo = o.optBoolean("isTurbo", false);
                btn.turboInterval = o.optInt("turboInterval", 40);
                 
                btn.loadSkinFromUri(getContext());                
                buttons.add(btn);
            }
            joystickMode = prefs.getInt("JoystickMode_" + slot, 0);
            joyBaseX = prefs.getFloat("JoyX_" + slot, 250); joyBaseY = prefs.getFloat("JoyY_" + slot, 700);
            joyRadius = prefs.getFloat("JoyR_" + slot, 180); 
            joyHitboxRadius = prefs.getFloat("JoyHitR_" + slot, 270);
            joyAlpha = prefs.getInt("JoyA_" + slot, 200);
            joyColor = prefs.getInt("JoyColor_" + slot, Color.parseColor("#FF5555"));
            isVibrationOn = prefs.getBoolean("Vibration_" + slot, true);
            vibrationIntensity = prefs.getInt("VibIntensity_" + slot, 30);
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
            overlayScale1 = prefs.getFloat("OverlayScale1_" + slot, 1.0f);
            overlayUri2 = prefs.getString("OverlayUri2_" + slot, "");
            overlayX2 = prefs.getFloat("OverlayX2_" + slot, 0); overlayY2 = prefs.getFloat("OverlayY2_" + slot, 0);
            overlayScale2 = prefs.getFloat("OverlayScale2_" + slot, 1.0f);
            overlayRotation1 = prefs.getFloat("OverlayRot1_" + slot, 0f);
            overlayRotation2 = prefs.getFloat("OverlayRot2_" + slot, 0f);
            isFullscreenHideOverlay = prefs.getBoolean("FS_HideOverlay_" + slot, false);
            isAutoHideEnabled = prefs.getBoolean("AutoHide_" + slot, true);
useRetroUIMode = prefs.getBoolean("RetroUI_" + slot, false);
autoHideSeconds = prefs.getInt("AutoHideSec_" + slot, 5);
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
            
            // 【新增】：强制重绘摇杆底盘的颜色梯度，避免切换配置时颜色不同步
            if (joystickMode == 2) {
                RadialGradient baseGrad = new RadialGradient(joyBaseX, joyBaseY, joyRadius, Color.parseColor("#333333"), Color.parseColor("#080808"), Shader.TileMode.CLAMP);
                paintBtn.setShader(baseGrad);
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
        overlayX1 = 0; overlayY1 = 0; overlayScale1 = 1.0f; overlayX2 = 0; overlayY2 = 0; overlayScale2 = 1.0f;        
        isFullscreenHideOverlay = false;
        isAutoHideEnabled = true;
        autoHideSeconds = 5;

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
            } else { 
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
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
                        // 【修复】：优先读取 exportAllData 传过来的全量 JSON 数据
                        String exportData = getArguments() != null ? getArguments().getString("export_data", "") : "";
                        JSONObject root;
                        if (!exportData.isEmpty()) {
                            root = new JSONObject(exportData);
                        } else {
                            // 保底旧版单发布局的逻辑
                            root = new JSONObject();
                            root.put("joystickMode", DynamicGamepadView.instance.joystickMode);
                            root.put("joyBaseX", DynamicGamepadView.instance.joyBaseX); 
                            root.put("joyBaseY", DynamicGamepadView.instance.joyBaseY);
                            root.put("joyRadius", DynamicGamepadView.instance.joyRadius);
                            root.put("joyHitboxRadius", DynamicGamepadView.instance.joyHitboxRadius);
                            root.put("joyAlpha", DynamicGamepadView.instance.joyAlpha);
                            root.put("joyColor", DynamicGamepadView.instance.joyColor);
                            root.put("isVibrationOn", DynamicGamepadView.instance.isVibrationOn);
                            root.put("vibrationIntensity", DynamicGamepadView.instance.vibrationIntensity);
                            root.put("buttons", new JSONArray(DynamicGamepadView.instance.getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_LAYOUT_PREFIX + DynamicGamepadView.instance.currentSlot, "[]")));
                            root.put("joySkinBase", DynamicGamepadView.instance.joySkinBaseUri);
                            root.put("joySkinKnob", DynamicGamepadView.instance.joySkinKnobUri);
                            root.put("overlayMode", DynamicGamepadView.instance.overlayMode);
                            root.put("overlayUri1", DynamicGamepadView.instance.overlayUri1);
                            root.put("overlayX1", DynamicGamepadView.instance.overlayX1);
                            root.put("overlayY1", DynamicGamepadView.instance.overlayY1);
                            root.put("overlayScale1", DynamicGamepadView.instance.overlayScale1);
                            root.put("overlayUri2", DynamicGamepadView.instance.overlayUri2);
                            root.put("overlayX2", DynamicGamepadView.instance.overlayX2);
                            root.put("overlayY2", DynamicGamepadView.instance.overlayY2);
                            root.put("overlayScale2", DynamicGamepadView.instance.overlayScale2);
                            root.put("overlayRotation1", DynamicGamepadView.instance.overlayRotation1);
                            root.put("overlayRotation2", DynamicGamepadView.instance.overlayRotation2);
                            root.put("isFullscreenHideOverlay", DynamicGamepadView.instance.isFullscreenHideOverlay);
                            root.put("isAutoHideEnabled", DynamicGamepadView.instance.isAutoHideEnabled);
                            root.put("autoHideSeconds", DynamicGamepadView.instance.autoHideSeconds);
                        }

                        java.io.OutputStream os = getActivity().getContentResolver().openOutputStream(uri);
                        os.write(root.toString(4).getBytes(StandardCharsets.UTF_8));
                        os.close();
                        Toast.makeText(getActivity(), "✅ 导出成功！", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) { Toast.makeText(getActivity(), "❌ 导出失败", Toast.LENGTH_SHORT).show(); }                
                                               } else if (requestCode == 45 && DynamicGamepadView.instance != null) { 
                    // 【修复1】提前获取安全的 Context
                    final Context safeContext = getActivity() != null ? getActivity() : DynamicGamepadView.instance.getContext();
                    
                    try {
                        java.io.InputStream is = safeContext.getContentResolver().openInputStream(uri);
                        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                        StringBuilder sb = new StringBuilder(); String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                        reader.close(); is.close();
                        
                        String fileContent = sb.toString().trim();
                        final JSONObject root;
                        boolean hasLayout = false;
                        boolean hasStyles = false;
                        
                        // 【三合一完美兼容逻辑：新版JSON、旧版JSON、经典XML】
                        if (fileContent.startsWith("[")) {
                            // 1. 兼容旧版：纯按键数组 JSON
                            root = new JSONObject();
                            root.put("buttons", new JSONArray(fileContent));
                            hasLayout = true;
                        } else if (fileContent.startsWith("{")) {
                            // 2. 兼容新版：包含 layout 和 styles 的完整 JSON
                            root = new JSONObject(fileContent);
                            hasLayout = root.has("layout") || root.has("buttons");
                            hasStyles = root.has("styles");
                        } else if (fileContent.contains("org.libsdl.app") || fileContent.contains("JoystickOverlay") || fileContent.contains("virtual_controller")) {
                            // 3. 【新增修复】兼容极早期经典文件：virtual_controller.xml (编译后的二进制或纯文本)
                            // 核心思路：既然用户导入了经典文件，我们直接调用最新的动态自适应引擎，为其生成完美适配当前屏幕比例的经典六键+摇杆键位。
                            DynamicGamepadView.instance.post(() -> {
                                DynamicGamepadView.instance.loadDefaultLayout();
                                DynamicGamepadView.instance.saveConfig();
                                DynamicGamepadView.instance.invalidate();
                                Toast.makeText(safeContext, "✅ 识别到经典 XML 布局！已自动转换为全屏自适应 Pro 布局。", Toast.LENGTH_LONG).show();
                            });
                            getFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
                            return;
                        } else {
                            Toast.makeText(safeContext, "❌ 无效的文件格式", Toast.LENGTH_SHORT).show(); 
                            getFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
                            return;
                        }

                        if (!hasLayout && !hasStyles) {
                            Toast.makeText(safeContext, "❌ 文件内找不到有效的布局数据", Toast.LENGTH_SHORT).show(); 
                            getFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
                            return;
                        }

                        // 【核心修复：创建 final 副本供 Lambda 内部使用】
                        final boolean finalHasLayout = hasLayout;
                        final boolean finalHasStyles = hasStyles;

                        // 弹窗让用户选择具体要导入什么
                        new AlertDialog.Builder(safeContext, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setTitle("发现数据，请选择要导入的内容：")
                            .setItems(new CharSequence[]{"📥 导入全部 (布局与风格)", "📥 仅导入按键布局", "📥 仅导入风格库"}, (dialog, which) -> {
                                try {
                                    SharedPreferences.Editor editor = DynamicGamepadView.instance.getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
                                    
                                    // 处理布局导入
                                    if ((which == 0 || which == 1) && finalHasLayout) {
                                        JSONArray btnArray = root.has("layout") ? root.getJSONArray("layout") : root.getJSONArray("buttons");
                                        editor.putString(KEY_LAYOUT_PREFIX + DynamicGamepadView.instance.currentSlot, btnArray.toString());
                                        editor.putInt("JoystickMode_" + DynamicGamepadView.instance.currentSlot, root.optInt("joystickMode", 0));
                                        editor.putFloat("JoyX_" + DynamicGamepadView.instance.currentSlot, (float) root.optDouble("joyBaseX", 250));
                                        editor.putFloat("JoyY_" + DynamicGamepadView.instance.currentSlot, (float) root.optDouble("joyBaseY", 700));
                                        editor.putFloat("JoyR_" + DynamicGamepadView.instance.currentSlot, (float) root.optDouble("joyRadius", 180));
                                        editor.putFloat("JoyHitR_" + DynamicGamepadView.instance.currentSlot, (float) root.optDouble("joyHitboxRadius", 270));
                                        editor.putInt("JoyA_" + DynamicGamepadView.instance.currentSlot, root.optInt("joyAlpha", 200));
                                        editor.putInt("JoyColor_" + DynamicGamepadView.instance.currentSlot, root.optInt("joyColor", Color.parseColor("#FF5555"))); 
                                        editor.putBoolean("Vibration_" + DynamicGamepadView.instance.currentSlot, root.optBoolean("isVibrationOn", true));                        
                                        editor.putInt("VibIntensity_" + DynamicGamepadView.instance.currentSlot, root.optInt("vibrationIntensity", 30));
                                        
                                        editor.putString("JoySkinBase_" + DynamicGamepadView.instance.currentSlot, root.optString("joySkinBase", ""));
                                        editor.putString("JoySkinKnob_" + DynamicGamepadView.instance.currentSlot, root.optString("joySkinKnob", ""));                       
                                    }
                                    
                                    // 处理风格库导入
                                    if ((which == 0 || which == 2) && finalHasStyles) {
                                        JSONArray styleArr = root.getJSONArray("styles");
                                        DynamicGamepadView.instance.styleList.clear();
                                        DynamicGamepadView.instance.styleList.add(new GamepadStyle("纯色渐变风格 (默认1)"));
                                        for (int i = 0; i < styleArr.length(); i++) {
                                            GamepadStyle importedStyle = GamepadStyle.fromJson(styleArr.getJSONObject(i));
                                            if (!importedStyle.styleName.contains("纯色渐变")) {
                                                DynamicGamepadView.instance.styleList.add(importedStyle);
                                            }
                                        }
                                    }
                                    // 【修复3】使用 commit() 强制同步阻塞写入，确保数据落地后再继续
                                    editor.commit(); 
                                    DynamicGamepadView.instance.loadConfig(DynamicGamepadView.instance.currentSlot);
                                    Toast.makeText(safeContext, "✅ 数据导入成功！", Toast.LENGTH_LONG).show();
                                } catch (Exception e) { Toast.makeText(safeContext, "❌ 应用失败", Toast.LENGTH_SHORT).show(); }
                            }).show();
                        
                            
                    } catch (Exception e) { Toast.makeText(safeContext, "❌ 文件读取失败，可能已损坏", Toast.LENGTH_SHORT).show(); }
                }
            }
                                
                    
            getFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
        }
   }     
    // =====================================
    // 新增：专业复古街机风 UI 系统 (Pro Arcade UI)
    // =====================================
    private void showRetroMainMenu() {
        final android.app.Dialog dialog = new android.app.Dialog(getContext(), android.R.style.Theme_DeviceDefault_Dialog);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        
        // 1. 核心底板：CRT 监视器深黑底色 + 绝对直角边框 (完全摒弃系统圆角)
        LinearLayout rootLayout = new LinearLayout(getContext());
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(10, 10, 10, 10);
        android.graphics.drawable.GradientDrawable crtBg = new android.graphics.drawable.GradientDrawable();
        crtBg.setColor(Color.parseColor("#0A0A0A")); // 极深的 CRT 黑
        crtBg.setStroke(6, Color.parseColor("#555555")); // 工业感灰色粗外框
        crtBg.setCornerRadius(0f); // 绝对直角！
        rootLayout.setBackground(crtBg);

        // 顶栏拖拽条
        TextView header = new TextView(getContext());
        header.setText("IKEMEN GO CONTROL PANEL (PRO)");
        header.setTextColor(Color.parseColor("#FFCC00")); // 街机投币黄
        header.setTextSize(18f);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setGravity(android.view.Gravity.CENTER);
        header.setPadding(20, 30, 20, 30);
        header.setBackgroundColor(Color.parseColor("#1A1A1A"));
        rootLayout.addView(header);

          ScrollView scroll = new ScrollView(getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                // 【核心修复】：彻底抛弃不可靠的 DisplayMetrics，统一使用 View 当前真实渲染的长短边！
                int trueScreenH = Math.min(getWidth(), getHeight());
                
                // 【精细控制】：按当前真实高度比例截取，留出 120px 给顶部的拖拽条
                int maxHeight = (int) (trueScreenH * dialogHeightRatio) - 120; 
                
                int customHeightSpec = View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST);
                super.onMeasure(widthMeasureSpec, customHeightSpec);
            }
        };
                        
                
        
        LinearLayout contentLayout = new LinearLayout(getContext());
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(30, 20, 30, 40);

        // ================= ZONE 1: 操作面板构建区 (红色警示主题) =================
        contentLayout.addView(createRetroZoneTitle("ZONE 1 : LAYOUT & EDIT", "#FF0033"));
        LinearLayout zone1 = createRetroZoneContainer("#FF0033");
        
        Button btnEdit = createRetroButton(isEditMode ? "💾 保存并退出编辑模式" : "🛠️ 开启全局按键编辑", isEditMode ? "#4CAF50" : "#FF0033");
        btnEdit.setOnClickListener(v -> { isEditMode = !isEditMode; if (!isEditMode) saveConfig(); invalidate(); dialog.dismiss(); });
        zone1.addView(btnEdit);
        
        Button btnNewBtn = createRetroButton("➕ 新建按键 / 宏映射", "#555555");
        btnNewBtn.setOnClickListener(v -> { 
            float scale = Math.max(0.5f, getHeight() / 1080f);
VirtualButton newBtn = new VirtualButton("新键", getWidth() / 2f, getHeight() / 2f, 90 * scale, Color.RED, 150, Color.WHITE, SHAPE_CIRCLE, "Z+X", false);             
            buttons.add(newBtn); isEditMode = true; 
            // 注意：这里暂时调用经典面板，下一步我们再重构按键设置面板
            showButtonSettingsDialog(newBtn); dialog.dismiss(); 
        });
        zone1.addView(btnNewBtn);

        Button btnGrid = createRetroButton(isGridSnapMode ? "🧲 网格吸附：[ON]" : "🧲 网格吸附：[OFF]", "#555555");
        btnGrid.setOnClickListener(v -> { isGridSnapMode = !isGridSnapMode; btnGrid.setText(isGridSnapMode ? "🧲 网格吸附：[ON]" : "🧲 网格吸附：[OFF]"); });
        zone1.addView(btnGrid);
        contentLayout.addView(zone1);

        // ================= ZONE 2: 视觉与机台美化 (霓虹蓝主题) =================
        contentLayout.addView(createRetroZoneTitle("ZONE 2 : VISUAL & STYLE", "#00FFFF"));
        LinearLayout zone2 = createRetroZoneContainer("#00FFFF");
        
        Button btnStyle = createRetroButton("🎨 风格管理系统 (Style System)", "#555555");
        btnStyle.setOnClickListener(v -> { showStyleManagerDialog(); dialog.dismiss(); });
        zone2.addView(btnStyle);
        
        String joyText = "🕹️ 摇杆形态: " + (joystickMode==0?"分离十字键":joystickMode==1?"现代白圆盘":joystickMode==2?"经典红杆":joystickMode==3?"十字型八键":"跟随当前风格");
        Button btnJoy = createRetroButton(joyText, "#555555");
        btnJoy.setOnClickListener(v -> { joystickMode = (joystickMode + 1) % 5; if (joystickMode == JOYSTICK_MODE_STYLE) refreshJoystickStyle(); saveConfig(); invalidate(); dialog.dismiss(); showRetroMainMenu(); });
        zone2.addView(btnJoy);

        Button btnOverlay = createRetroButton("🖼️ 屏幕遮罩引擎配置", "#555555");
        btnOverlay.setOnClickListener(v -> { showOverlaySettingsDialog(); dialog.dismiss(); });
        zone2.addView(btnOverlay);
        contentLayout.addView(zone2);

        // ================= ZONE 3: 硬件与系统控制 (工业黄主题) =================
        contentLayout.addView(createRetroZoneTitle("ZONE 3 : HARDWARE & SYS", "#FFCC00"));
        LinearLayout zone3 = createRetroZoneContainer("#FFCC00");
        
        Button btnVib = createRetroButton("📳 物理震动与强度调配", "#555555");
        btnVib.setOnClickListener(v -> { showVibrationSettingsDialog(); dialog.dismiss(); });
        zone3.addView(btnVib);

        Button btnAuto = createRetroButton("⏱️ 自动隐藏延迟控制", "#555555");
        btnAuto.setOnClickListener(v -> { showAutoHideSettingsDialog(); dialog.dismiss(); });
        zone3.addView(btnAuto);

        Button btnDir = createRetroButton("📁 重新挂载游戏数据目录", "#555555");
        btnDir.setOnClickListener(v -> { 
            if (getContext() instanceof SDLActivity) ((SDLActivity) getContext()).checkAndPickFolder();
            dialog.dismiss();
        });
        zone3.addView(btnDir);
        contentLayout.addView(zone3);

        // ================= ZONE 4: 记忆卡与插槽 (电路板绿主题) =================
        contentLayout.addView(createRetroZoneTitle("ZONE 4 : MEMORY CARD", "#4CAF50"));
        LinearLayout zone4 = createRetroZoneContainer("#4CAF50");
        
        Button btnFile = createRetroButton("📂 布局存档与导入导出", "#555555");
        btnFile.setOnClickListener(v -> { showProfileManager(); dialog.dismiss(); });
        zone4.addView(btnFile);

        Button btnReset = createRetroButton("⚠️ 格式化：恢复出厂布局", "#8B0000"); // 深红色警告
        btnReset.setOnClickListener(v -> {
            new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("⚠️ SYSTEM WARNING").setMessage("确定抹除所有自定义数据，恢复出厂设置吗？")
                .setPositiveButton("EXECUTE (执行)", (d, w) -> { loadDefaultLayout(); saveConfig(); invalidate(); dialog.dismiss(); })
                .setNegativeButton("CANCEL (取消)", null).show();
        });
        zone4.addView(btnReset);
        contentLayout.addView(zone4);

        // ================= 底部：退回经典模式 =================
        Button btnSwitchBack = createRetroButton("↩ 退回经典 UI 模式", "#333333");
        btnSwitchBack.setOnClickListener(v -> { useRetroUIMode = false; saveConfig(); dialog.dismiss(); showMainMenu(); });
        LinearLayout.LayoutParams bottomParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bottomParams.setMargins(0, 40, 0, 0);
        btnSwitchBack.setLayoutParams(bottomParams);
        contentLayout.addView(btnSwitchBack);

        scroll.addView(contentLayout);
        rootLayout.addView(scroll);
        
        dialog.setContentView(rootLayout);
        setupMovableDialog(dialog, header); // 复用你的拖拽逻辑
        dialog.show();
    }

    // --- 以下是生成复古 UI 组件的辅助工具方法 ---

    private TextView createRetroZoneTitle(String text, String hexColor) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextColor(Color.parseColor(hexColor));
        tv.setTextSize(14f);
        tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD); // 暂时使用自带等宽字体模拟像素感
        tv.setPadding(10, 30, 0, 10);
        return tv;
    }

        private LinearLayout createRetroZoneContainer(String borderColorHex) {
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.parseColor("#121212")); // 略浅一点的黑底
        bg.setStroke(2, Color.parseColor(borderColorHex)); // 细线霓虹边框
        bg.setCornerRadius(0f); // 绝对直角
        
        layout.setBackground(bg);  // <---- 【补上这一句就完美了】
        
        return layout;
    }
    

    private Button createRetroButton(String text, String bgColorHex) {
        Button btn = new Button(getContext());
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(13f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.parseColor(bgColorHex));
        bg.setCornerRadius(0f); // 绝对直角
        bg.setStroke(2, Color.BLACK); // 黑色内嵌描边增加立体感
        btn.setBackground(bg);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 10, 0, 10);
        btn.setLayoutParams(params);
        return btn;
    }
}    
