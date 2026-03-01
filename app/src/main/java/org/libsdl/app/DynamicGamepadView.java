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
    private static final String PREFS_NAME = "IkemenGamepad_Pro_V5";
    private static final String KEY_LAYOUT_PREFIX = "LayoutSlot_";
    
    public int currentSlot = 0;
    public int joystickMode = 0; // 0=十字, 1=圆盘, 2=街机
    public boolean isVibrationOn = true;
    public int vibrationIntensity = 30; // 震动强度 (建议0-100，即震动毫秒数)
    public boolean isAutoHideEnabled = true; // 自动隐藏开关
    public int autoHideSeconds = 5;          // 自动隐藏延迟时间（秒）


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
                        Thread.sleep(60); // 【修复】把按下时间延长到 60ms，确保游戏帧能抓到
                        for (int code : stepCodes) SDLActivity.onNativeKeyUp(code);
                        Thread.sleep(50); // 【修复】动作间隔延长到 50ms，防止连招粘连
                    }
                } catch (InterruptedException e) { }
                isMacroPlaying = false;
            }).start();
        }
    } // <====== 兄弟，就是少了这一个大括号！！！它是用来把 VirtualButton 这个类关上的！
       
    
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
    private void generateVideoArcadeStyle() {
        int size = 400; // 高清分辨率
        // 1. 画底盘 (深蓝色带白边和8个三角)
        Bitmap baseBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cBase = new Canvas(baseBmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.parseColor("#1A2B42")); cBase.drawCircle(size/2f, size/2f, size/2f - 4, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(6f); p.setColor(Color.WHITE); cBase.drawCircle(size/2f, size/2f, size/2f - 4, p);
        p.setStyle(Paint.Style.FILL);
        for(int i=0; i<8; i++) {
            cBase.save(); cBase.rotate(i*45, size/2f, size/2f);
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(size/2f, 20); path.lineTo(size/2f - 15, 45); path.lineTo(size/2f + 15, 45); path.close();
            cBase.drawPath(path, p); cBase.restore();
        }
        String baseUri = saveImageToLocal(baseBmp, "arcade_base.png");

        // 2. 画摇杆帽 (红球)
        Bitmap knobBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cKnob = new Canvas(knobBmp);
        p.setColor(Color.parseColor("#D32F2F")); cKnob.drawCircle(size/2f, size/2f, size/2.5f, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(4f); p.setColor(Color.parseColor("#B71C1C")); cKnob.drawCircle(size/2f, size/2f, size/2.5f, p);
        String knobUri = saveImageToLocal(knobBmp, "arcade_knob.png");

        // 3. 画普通按键 (深蓝带白边)
        Bitmap btnBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cBtn = new Canvas(btnBmp);
        p.setStyle(Paint.Style.FILL); p.setColor(Color.parseColor("#1A2B42")); cBtn.drawCircle(size/2f, size/2f, size/2f - 6, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(8f); p.setColor(Color.parseColor("#90CAF9")); cBtn.drawCircle(size/2f, size/2f, size/2f - 6, p);
        String btnUri = saveImageToLocal(btnBmp, "arcade_btn.png");

        // 创建风格对象并存入列表
        GamepadStyle style2 = new GamepadStyle("视频街机风格 (默认2)");
        style2.joyBaseUri = baseUri; style2.joyKnobUri = knobUri; style2.btnNormalUri = btnUri;
        style2.globalPressedColor = Color.parseColor("#4CAF50"); // 默认按下变绿泛光
        
        styleList.clear();
        styleList.add(new GamepadStyle("纯色渐变风格 (默认1)")); // 默认1为空，代表走老代码渐变
        styleList.add(style2);
    }

                   public void onImagePicked(String uriStr) {
        try {
            Uri uri = Uri.parse(uriStr);
            InputStream is = getContext().getContentResolver().openInputStream(uri);
            Bitmap raw = BitmapFactory.decodeStream(is);
            
            // 【新增核心】生成私有缓存图，彻底摆脱外部相册依赖
            String localUriStr = saveImageToLocal(raw, "skin_" + System.currentTimeMillis() + ".png");
            final String finalUriStr = localUriStr.isEmpty() ? uriStr : localUriStr; 
            
            if (imagePickerTarget == 1) { 
                if (joySkinBaseBitmap != null && !joySkinBaseBitmap.isRecycled()) joySkinBaseBitmap.recycle();
                joySkinBaseUri = finalUriStr; // 【修改】存最终私有路径
                joySkinBaseBitmap = Bitmap.createScaledBitmap(raw, (int)(joyRadius*2), (int)(joyRadius*2), true);
                Toast.makeText(getContext(), "摇杆外框皮肤应用成功！", Toast.LENGTH_SHORT).show();
            } else if (imagePickerTarget == 2) { 
                if (joySkinKnobBitmap != null && !joySkinKnobBitmap.isRecycled()) joySkinKnobBitmap.recycle();
                joySkinKnobUri = finalUriStr; // 【修改】存最终私有路径
                joySkinKnobBitmap = Bitmap.createScaledBitmap(raw, (int)(joyRadius*2), (int)(joyRadius*2), true);
                Toast.makeText(getContext(), "摇杆中心皮肤应用成功！", Toast.LENGTH_SHORT).show();
            } else if (imagePickerTarget == 3 && currentlyEditingButton != null) { 
                if (currentlyEditingButton.skinBitmap != null && !currentlyEditingButton.skinBitmap.isRecycled()) currentlyEditingButton.skinBitmap.recycle();
                currentlyEditingButton.customImageUri = finalUriStr; // 【修改】存最终私有路径
                currentlyEditingButton.skinBitmap = Bitmap.createScaledBitmap(raw, (int)(currentlyEditingButton.radius*2), (int)(currentlyEditingButton.radius*2), true);
                Toast.makeText(getContext(), "按键皮肤应用成功！", Toast.LENGTH_SHORT).show();
                            // 【新增：按键按下特效皮肤图片读取 (Target 6)】
            } else if (imagePickerTarget == 6 && currentlyEditingButton != null) { 
                if (currentlyEditingButton.pressedSkinBitmap != null && !currentlyEditingButton.pressedSkinBitmap.isRecycled()) currentlyEditingButton.pressedSkinBitmap.recycle();
                currentlyEditingButton.customPressedUri = finalUriStr; 
                currentlyEditingButton.pressedSkinBitmap = Bitmap.createScaledBitmap(raw, (int)(currentlyEditingButton.radius*2), (int)(currentlyEditingButton.radius*2), true);
                Toast.makeText(getContext(), "按下状态皮肤应用成功！", Toast.LENGTH_SHORT).show();

            } else if (imagePickerTarget == 4) { 
                if (overlayBmp1 != null && !overlayBmp1.isRecycled()) overlayBmp1.recycle();
                overlayUri1 = finalUriStr; // 【修改】存最终私有路径
                overlayBmp1 = Bitmap.createBitmap(raw); 
                if (overlayMode < 1) overlayMode = 1; 
                Toast.makeText(getContext(), "遮罩图1应用成功！", Toast.LENGTH_SHORT).show();
            } else if (imagePickerTarget == 5) { 
                if (overlayBmp2 != null && !overlayBmp2.isRecycled()) overlayBmp2.recycle();
                overlayUri2 = finalUriStr; // 【修改】存最终私有路径
                overlayBmp2 = Bitmap.createBitmap(raw);
                if (overlayMode < 2) overlayMode = 2; 
                Toast.makeText(getContext(), "遮罩图2应用成功！", Toast.LENGTH_SHORT).show();
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
        if ((!isFullscreenHideOverlay || isEditMode) && overlayMode > 0) {
            paintBtn.setAlpha(255);
            if (overlayMode >= 1 && overlayBmp1 != null) {
                tempRect.set(overlayX1, overlayY1, overlayX1 + overlayBmp1.getWidth() * overlayScale1, overlayY1 + overlayBmp1.getHeight() * overlayScale1);
                canvas.save(); // 保存画布状态
                canvas.rotate(overlayRotation1, tempRect.centerX(), tempRect.centerY()); // 围绕中心点旋转
                canvas.drawBitmap(overlayBmp1, null, tempRect, paintBtn);
                if (isEditMode) { 
                    paintBtn.setStyle(Paint.Style.STROKE); paintBtn.setColor(Color.GREEN); paintBtn.setStrokeWidth(5f);
                    canvas.drawRect(tempRect, paintBtn); paintBtn.setStyle(Paint.Style.FILL);
                }
                canvas.restore(); // 恢复画布状态
            }
            if (overlayMode == 2 && overlayBmp2 != null) {
                tempRect.set(overlayX2, overlayY2, overlayX2 + overlayBmp2.getWidth() * overlayScale2, overlayY2 + overlayBmp2.getHeight() * overlayScale2);
                canvas.save(); // 保存画布状态
                canvas.rotate(overlayRotation2, tempRect.centerX(), tempRect.centerY()); // 围绕中心点旋转
                canvas.drawBitmap(overlayBmp2, null, tempRect, paintBtn);
                if (isEditMode) {
                    paintBtn.setStyle(Paint.Style.STROKE); paintBtn.setColor(Color.BLUE); paintBtn.setStrokeWidth(5f);
                    canvas.drawRect(tempRect, paintBtn); paintBtn.setStyle(Paint.Style.FILL);
                }
                canvas.restore(); // 恢复画布状态
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
            paintText.setTextSize(40f);
            paintText.setShadowLayer(5f, 2f, 2f, Color.BLACK);
            canvas.drawText("【编辑模式】拖动调整，轻触设置", getWidth() / 2f, 100, paintText);
        }

              // 核心按键绘制逻辑
        for (VirtualButton btn : buttons) {
            if (joystickMode > 0 && btn.isDirectional) continue;

            int idleAlpha = (int)(btn.alpha * 0.6f); 
            int currentAlpha = isEditMode ? Math.max(120, btn.alpha) : (btn.isPressed ? 255 : idleAlpha);
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
        int currentAlpha = isEditMode ? Math.max(100, joyAlpha) : joyAlpha;
        
        // ========= 1. 绘制底盘 =========
        if (joySkinBaseBitmap != null) {
            paintBtn.setAlpha(currentAlpha);
            tempRect.set(joyBaseX - joyRadius, joyBaseY - joyRadius, joyBaseX + joyRadius, joyBaseY + joyRadius);
            canvas.drawBitmap(joySkinBaseBitmap, null, tempRect, paintBtn);
        } else if (joystickMode == 1) { 
            paintBtn.setColor(joyColor); paintBtn.setAlpha((int)(currentAlpha * 0.3f));
            canvas.drawCircle(joyBaseX, joyBaseY, joyRadius, paintBtn);
        } else if (joystickMode == 2) { 
            RadialGradient baseGrad = new RadialGradient(joyBaseX, joyBaseY, joyRadius, Color.parseColor("#333333"), Color.parseColor("#080808"), Shader.TileMode.CLAMP);
            paintBtn.setShader(baseGrad); paintBtn.setAlpha((int)(currentAlpha * 0.9f));
            canvas.drawCircle(joyBaseX, joyBaseY, joyRadius, paintBtn);
            paintBtn.setShader(null);
        } else if (joystickMode == 3) { 
            paintBtn.setColor(Color.DKGRAY); paintBtn.setAlpha((int)(currentAlpha * 0.5f));
            paintText.setColor(Color.WHITE); paintText.setAlpha(currentAlpha); paintText.setTextSize(joyRadius * 0.35f);
            paintText.setTextAlign(Paint.Align.CENTER);
            
                       // 【终极优化】直接读取数值，实现 0 内存分配
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

        if ((joystickMode == 1 || joystickMode == 2) && joySkinBaseBitmap == null) {
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

        // ========= 2. 绘制摇杆动态指示方向 =========
        float dx = joyKnobX - joyBaseX;
        float dy = joyKnobY - joyBaseY;
        float dist = (float) Math.hypot(dx, dy);

        if (dist > joyRadius * 0.2f && !isEditMode && joystickMode != 3) {
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

        // ========= 3. 绘制摇杆帽 =========
        if (joystickMode != 3) { 
            if (joySkinKnobBitmap != null) {
                paintBtn.setAlpha(currentAlpha);
                float knobRad = joyRadius * 0.5f; 
                tempRect.set(joyKnobX - knobRad, joyKnobY - knobRad, joyKnobX + knobRad, joyKnobY + knobRad);
                canvas.drawBitmap(joySkinKnobBitmap, null, tempRect, paintBtn);
            } else if (joystickMode == 1) { 
                paintBtn.setColor(joyColor); paintBtn.setAlpha(currentAlpha);
                canvas.drawCircle(joyKnobX, joyKnobY, joyRadius * 0.35f, paintBtn);
            } else if (joystickMode == 2) { 
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
            
            // 【修正】直接使用类头部的 dashPaint
            canvas.drawCircle(joyBaseX, joyBaseY, joyHitboxRadius, dashPaint);
            
            paintText.setColor(Color.WHITE); paintText.setTextSize(35f); paintText.setShadowLayer(3f,0,0,Color.BLACK);
            canvas.drawText("摇杆控制区", joyBaseX, joyBaseY - joyHitboxRadius - 20, paintText);
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
    if (action == MotionEvent.ACTION_DOWN && menuButtonRect.contains(event.getX(actionIndex), event.getY(actionIndex))) {
        showMainMenu(); // <--- 只要这个在，你就永远能退出编辑模式
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
            
            // 【修改】区分触发：多步的是宏，单步的是普通组合键
            if (!btn.isPressed && isTouchedNow) {
                btn.isPressed = true;
                btn.pressTimestamp = System.currentTimeMillis(); // 【新增】瞬间打卡记录时间
                triggerVibrate();
                
                if (btn.macroSteps.size() > 1) {
                    btn.executeMacro(); // 触发一键连招
                } else if (!btn.macroSteps.isEmpty()) {
                    for (int code : btn.macroSteps.get(0)) SDLActivity.onNativeKeyDown(code); // 瞬间触发同按组合键
                }
            } else if (btn.isPressed && !isTouchedNow) {
                btn.isPressed = false;
                
                // 只有普通键/组合键需要在这里松开，宏已经在子线程自己松开了
                if (btn.macroSteps.size() <= 1 && !btn.macroSteps.isEmpty()) {
                    long pressDuration = System.currentTimeMillis() - btn.pressTimestamp;
                    final List<Integer> codes = btn.macroSteps.get(0);
                    
                    if (pressDuration < 50) {
                        // 【核心修复】如果按压时间不足50毫秒(不到3帧)，利用View的线程延迟松开操作，保证底层引擎一定能抓到动作
                        postDelayed(() -> {
                            for (int code : codes) SDLActivity.onNativeKeyUp(code);
                        }, 50 - pressDuration);
                    } else {
                        // 按压时间正常，立即松开
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
        String modeText = isEditMode ? "💾 保存并退出编辑" : "🛠️ 开启按键编辑";
        String gridText = isGridSnapMode ? "🧲 网格吸附：已开启" : "🧲 网格吸附：已关闭 (自由拖动)";
                String joyText = "🕹️ 摇杆形态: " + (joystickMode==0?"分离十字键":joystickMode==1?"现代白圆盘":joystickMode==2?"经典红杆":joystickMode==3?"风格默认键盘":"[专属] 跟随当前风格");
        String vibText = "📳 物理震动开关与强度设置 (" + (isVibrationOn?"开启":"关闭") + ")";
        
        // 【新增】判断当前遮罩状态，动态显示快捷按钮文本
        String quickOverlayText = isFullscreenHideOverlay ? "👁️ 当前: 隐藏遮罩 (点击恢复显示)" : "👁️ 当前: 显示遮罩 (点击临时隐藏)";
String autoHideText = "⏱️ 按键自动隐藏设置 (" + (isAutoHideEnabled ? autoHideSeconds + "秒" : "已关闭") + ")";
CharSequence[] options = {modeText, "➕ 新建组合键/宏", gridText, joyText, vibText, "📂 布局存档与导入导出", "🔄 恢复初始默认布局", "🖼️ 屏幕遮罩详细设置", quickOverlayText, "📁 重新选择游戏数据目录", autoHideText, "🎨 按键风格管理系统"};
        
        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("⚙️ 游戏面板全局设置")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) { isEditMode = !isEditMode; if (!isEditMode) saveConfig(); invalidate(); } 
                    else if (which == 1) {
                        VirtualButton newBtn = new VirtualButton("新键", getWidth() / 2f, getHeight() / 2f, 90, Color.RED, 150, Color.WHITE, SHAPE_CIRCLE, "Z+X", false);
                        buttons.add(newBtn); isEditMode = true; showButtonSettingsDialog(newBtn);
                    } 
                    else if (which == 2) { 
                        isGridSnapMode = !isGridSnapMode; 
                        Toast.makeText(getContext(), isGridSnapMode ? "已开启网格吸附" : "已开启自由拖动", Toast.LENGTH_SHORT).show();
                    } 
                    else if (which == 3) { joystickMode = (joystickMode + 1) % 5; saveConfig(); invalidate(); } 
                    else if (which == 4) { showVibrationSettingsDialog(); } 
                    else if (which == 5) { showProfileManager(); } 
                    else if (which == 6) {
                        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setTitle("⚠️ 警告").setMessage("确定要清空所有自定义修改，恢复为原版默认按键布局吗？")
                            .setPositiveButton("确定恢复", (d, w) -> { loadDefaultLayout(); saveConfig(); invalidate(); })
                            .setNegativeButton("取消", null).show();
                    }
                    else if (which == 7) { showOverlaySettingsDialog(); }
                                                            else if (which == 8) { 
                        // 【新增】快捷切换遮罩的显示与隐藏状态，用于应对游戏内修改纵横比
                        isFullscreenHideOverlay = !isFullscreenHideOverlay;
                        Toast.makeText(getContext(), isFullscreenHideOverlay ? "已临时隐藏遮罩 (适配全屏)" : "已恢复遮罩显示", Toast.LENGTH_SHORT).show();
                        saveConfig();
                        invalidate();
                    }
                    // === 在这里追加以下代码 ===
                    else if (which == 9) {
                        // 触发主 Activity 中的目录选择器
                        if (getContext() instanceof SDLActivity) {
                            ((SDLActivity) getContext()).checkAndPickFolder();
                        } else {
                            Toast.makeText(getContext(), "无法调用目录选择器：上下文环境异常", Toast.LENGTH_SHORT).show();
                        }
                    }
                                        else if (which == 10) {
                        showAutoHideSettingsDialog();
                    }
                    else if (which == 11) { showStyleManagerDialog(); } // 触发风格管理

                    // ==========================        
                }).show();
    }
    private void showStyleManagerDialog() {
        if (styleList.isEmpty()) generateVideoArcadeStyle(); // 初始化默认风格
        
        String[] styleNames = new String[styleList.size()];
        for(int i=0; i<styleList.size(); i++) styleNames[i] = styleList.get(i).styleName;
        
        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("🎨 风格系统 (选定后将强行替换所有按键)")
            .setSingleChoiceItems(styleNames, currentStyleIndex, (dialog, which) -> currentStyleIndex = which)
            .setPositiveButton("应用该风格", (d, w) -> {
                GamepadStyle style = styleList.get(currentStyleIndex);
                if(joystickMode == JOYSTICK_MODE_STYLE) {
                    joySkinBaseUri = style.joyBaseUri; joySkinKnobUri = style.joyKnobUri;
                    if(!joySkinBaseUri.isEmpty()) joySkinBaseBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getContext().getContentResolver().openInputStream(Uri.parse(joySkinBaseUri))), (int)(joyRadius*2), (int)(joyRadius*2), true); else joySkinBaseBitmap = null;
                    if(!joySkinKnobUri.isEmpty()) joySkinKnobBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeStream(getContext().getContentResolver().openInputStream(Uri.parse(joySkinKnobUri))), (int)(joyRadius*2), (int)(joyRadius*2), true); else joySkinKnobBitmap = null;
                }
                for (VirtualButton b : buttons) {
                    if (!b.isDirectional) { // 只替换非方向键
                        b.customImageUri = style.btnNormalUri; b.customPressedUri = style.btnPressedUri;
                        b.pressedEffectColor = style.globalPressedColor; b.pressedEffectAlpha = style.globalPressedAlpha;
                        b.loadSkinFromUri(getContext());
                    }
                }
                saveConfig(); invalidate(); Toast.makeText(getContext(), "已应用风格: " + style.styleName, Toast.LENGTH_SHORT).show();
            })
            .setNeutralButton("导出全部布局与风格", (d, w) -> {
                // 【调用第六步的导出功能】
                exportAllData();
            })
            .setNegativeButton("取消", null).show();
    }

    private void showProfileManager() {
        CharSequence[] options = {"📂 读取云端方案 1", "💾 覆盖保存至方案 1", "📂 读取云端方案 2", "💾 覆盖保存至方案 2", "📤 系统管理器导出配置", "📥 系统管理器导入配置"};
        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("布局方案存档与分享")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("⚠️ 读取确认").setMessage("确定读取方案 1？未保存修改将丢失。").setPositiveButton("确定", (d, w) -> loadConfig(1)).setNegativeButton("取消", null).show();
                    if (which == 1) new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("⚠️ 覆盖确认").setMessage("确定将当前布局覆盖至方案 1？").setPositiveButton("确定", (d, w) -> { currentSlot = 1; saveConfig(); Toast.makeText(getContext(),"✅ 已存入方案1",Toast.LENGTH_SHORT).show();}).setNegativeButton("取消", null).show();
                    if (which == 2) new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("⚠️ 读取确认").setMessage("确定读取方案 2？未保存修改将丢失。").setPositiveButton("确定", (d, w) -> loadConfig(2)).setNegativeButton("取消", null).show();
                    if (which == 3) new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("⚠️ 覆盖确认").setMessage("确定将当前布局覆盖至方案 2？").setPositiveButton("确定", (d, w) -> { currentSlot = 2; saveConfig(); Toast.makeText(getContext(),"✅ 已存入方案2",Toast.LENGTH_SHORT).show();}).setNegativeButton("取消", null).show();
                    if (which == 4) { saveConfig(); exportLayoutToFile(); }
                    if (which == 5) { importLayoutFromFile(); }
                }).show();
    }

        // 【新增】遮罩图控制面板
    private void showOverlaySettingsDialog() {
        final android.app.Dialog dialog = new android.app.Dialog(getContext(), android.R.style.Theme_DeviceDefault_Dialog);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 60, 60, 60);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.parseColor("#E6222222")); bg.setCornerRadius(35f);
        layout.setBackground(bg);

        ScrollView scroll = new ScrollView(getContext());
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
        Button pickBmp1 = new Button(getContext()); pickBmp1.setText("选择图片"); pickBmp1.setOnClickListener(v -> { imagePickerTarget = 4; pickImage(); });
        Button clearBmp1 = new Button(getContext()); clearBmp1.setText("清除图片"); clearBmp1.setOnClickListener(v -> { overlayUri1 = ""; overlayBmp1 = null; invalidate(); });
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
        Button pickBmp2 = new Button(getContext()); pickBmp2.setText("选择图片"); pickBmp2.setOnClickListener(v -> { imagePickerTarget = 5; pickImage(); });
        Button clearBmp2 = new Button(getContext()); clearBmp2.setText("清除图片"); clearBmp2.setOnClickListener(v -> { overlayUri2 = ""; overlayBmp2 = null; invalidate(); });
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
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.parseColor("#E6222222")); bg.setCornerRadius(35f);
        layout.setBackground(bg);

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

    private void showAutoHideSettingsDialog() {
        final android.app.Dialog dialog = new android.app.Dialog(getContext(), android.R.style.Theme_DeviceDefault_Dialog);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 60, 60, 60);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.parseColor("#E6222222")); bg.setCornerRadius(35f);
        layout.setBackground(bg);

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
    private void setupMovableDialog(android.app.Dialog dialog, View dragHandle) {
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); // 窗体透明
            window.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
            window.setDimAmount(0f); // 取消黑底遮罩
            final android.view.WindowManager.LayoutParams params = window.getAttributes();
            params.x = 50; params.y = 50; // 默认出生在左上角
            window.setAttributes(params);

            // 监听拖拽条的触摸事件来实现窗口移动
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
        android.graphics.drawable.GradientDrawable windowBg = new android.graphics.drawable.GradientDrawable();
        windowBg.setColor(Color.parseColor("#E6222222")); windowBg.setCornerRadius(35f);
        rootLayout.setBackground(windowBg);

        TextView dragHandle = new TextView(getContext());
        dragHandle.setText("✋ 按住此处拖拽窗口 | 🕹️ 摇杆配置");
        android.graphics.drawable.GradientDrawable titleBg = new android.graphics.drawable.GradientDrawable();
        titleBg.setColor(Color.parseColor("#333333")); titleBg.setCornerRadii(new float[]{35f, 35f, 35f, 35f, 0f, 0f, 0f, 0f});
        dragHandle.setBackground(titleBg); dragHandle.setTextColor(Color.WHITE);
        dragHandle.setPadding(40, 30, 40, 30); dragHandle.setTextSize(16f); dragHandle.setTypeface(null, Typeface.BOLD);
        rootLayout.addView(dragHandle);

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout layout = new LinearLayout(getContext()); 
        layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(50, 20, 50, 50);

        layout.addView(createTitle("1. 摇杆中心球颜色 (双向同步):"));
        final EditText hexInput = createEditText("颜色代码如: #FF5555", String.format("#%06X", (0xFFFFFF & joyColor))); 
        layout.addView(hexInput);
        
        final View colorPreview = new View(getContext());
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 150);
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
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable windowBg = new android.graphics.drawable.GradientDrawable();
        windowBg.setColor(Color.parseColor("#E6222222")); windowBg.setCornerRadius(35f);
        rootLayout.setBackground(windowBg);

        TextView dragHandle = new TextView(getContext());
        dragHandle.setText("✋ 按住此处拖拽窗口 | 🔧 配置: " + btn.id);
        android.graphics.drawable.GradientDrawable titleBg = new android.graphics.drawable.GradientDrawable();
        titleBg.setColor(Color.parseColor("#333333")); titleBg.setCornerRadii(new float[]{35f, 35f, 35f, 35f, 0f, 0f, 0f, 0f});
        dragHandle.setBackground(titleBg); dragHandle.setTextColor(Color.WHITE);
        dragHandle.setPadding(40, 30, 40, 30); dragHandle.setTextSize(16f); dragHandle.setTypeface(null, Typeface.BOLD);
        rootLayout.addView(dragHandle);

        ScrollView scroll = new ScrollView(getContext());
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
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 150);
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

        // ================= 【新增：按下状态的UI调节控制】 =================
        layout.addView(createTitle("6. 按下状态特效 (独立颜色与皮肤):"));
        final EditText hexInputP = createEditText("颜色如: #4CAF50 (填 #000000 变回渐变)", String.format("#%06X", (0xFFFFFF & btn.pressedEffectColor))); 
        layout.addView(hexInputP);
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
        deleteBtn.setOnClickListener(v -> { buttons.remove(btn); saveConfig(); invalidate(); dialog.dismiss(); });
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
        
        // 【UI 美化】给输入框加个圆角白底边框
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(15f);
        bg.setStroke(3, Color.parseColor("#999999"));
        et.setBackground(bg);
        
        et.setPadding(30, 30, 30, 30);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 10, 0, 20);
        et.setLayoutParams(params);
        return et;
    }
    
    private TextView createTitle(String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(16f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(Color.WHITE); // 外部标题强制纯白
        tv.setPadding(0, 40, 0, 10);
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
        tv.setTextColor(Color.WHITE); 
        tv.setPadding(0, 10, 0, 0);
        // 让文本占用剩余的左侧空间
        LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tv.setLayoutParams(tvParams);
        
        // 2. 创建数值输入小框
        final EditText input = new EditText(getContext());
        input.setText(String.valueOf(progress));
        input.setTextColor(Color.BLACK);
        input.setTextSize(14f);
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
autoHideSeconds = prefs.getInt("AutoHideSec_" + slot, 5);
            
                        try { if (!overlayUri1.isEmpty()) overlayBmp1 = BitmapFactory.decodeStream(getContext().getContentResolver().openInputStream(Uri.parse(overlayUri1))); else overlayBmp1 = null; } catch(Exception e) { overlayBmp1 = null; }
            try { if (!overlayUri2.isEmpty()) overlayBmp2 = BitmapFactory.decodeStream(getContext().getContentResolver().openInputStream(Uri.parse(overlayUri2))); else overlayBmp2 = null; } catch(Exception e) { overlayBmp2 = null; }

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
        buttons.clear();
        // 【核心修复】恢复默认时，把摇杆彻底重置到初始状态
        joystickMode = 0;
        isVibrationOn = true; // 补上恢复默认震动
        vibrationIntensity = 30;
        imagePickerTarget = 0; // 补上清空选图状态
        joyBaseX = 250; joyBaseY = 700; joyKnobX = 250; joyKnobY = 700;
        joyRadius = 180; joyHitboxRadius = 270; joyAlpha = 200; joyColor = Color.parseColor("#FF5555"); 
        joySkinBaseUri = ""; joySkinKnobUri = ""; joySkinBaseBitmap = null; joySkinKnobBitmap = null;
        overlayMode = 0; overlayUri1 = ""; overlayUri2 = ""; overlayBmp1 = null; overlayBmp2 = null;
        overlayX1 = 0; overlayY1 = 0; overlayScale1 = 1.0f; overlayX2 = 0; overlayY2 = 0; overlayScale2 = 1.0f;
        isFullscreenHideOverlay = false;
        isAutoHideEnabled = true;
autoHideSeconds = 5;
    
        
        buttons.add(new VirtualButton("UP", 250, 550, 80, Color.GRAY, 150, Color.WHITE, SHAPE_CIRCLE, "UP", true));
        buttons.add(new VirtualButton("DOWN", 250, 850, 80, Color.GRAY, 150, Color.WHITE, SHAPE_CIRCLE, "DOWN", true));
        buttons.add(new VirtualButton("LEFT", 100, 700, 80, Color.GRAY, 150, Color.WHITE, SHAPE_CIRCLE, "LEFT", true));
        buttons.add(new VirtualButton("RIGHT", 400, 700, 80, Color.GRAY, 150, Color.WHITE, SHAPE_CIRCLE, "RIGHT", true));
        float rx = 1600, ry = 700; 
        buttons.add(new VirtualButton("A", rx, ry, 90, Color.parseColor("#4CAF50"), 180, Color.WHITE, SHAPE_CIRCLE, "A", false));
        buttons.add(new VirtualButton("B", rx + 200, ry - 50, 90, Color.parseColor("#F44336"), 180, Color.WHITE, SHAPE_CIRCLE, "B", false));
        buttons.add(new VirtualButton("C", rx + 400, ry - 100, 90, Color.parseColor("#2196F3"), 180, Color.WHITE, SHAPE_CIRCLE, "C", false));
        buttons.add(new VirtualButton("X", rx, ry - 200, 90, Color.parseColor("#8BC34A"), 180, Color.WHITE, SHAPE_CIRCLE, "X", false));
        buttons.add(new VirtualButton("Y", rx + 200, ry - 250, 90, Color.parseColor("#E91E63"), 180, Color.WHITE, SHAPE_CIRCLE, "Y", false));
        buttons.add(new VirtualButton("Z", rx + 400, ry - 300, 90, Color.parseColor("#03A9F4"), 180, Color.WHITE, SHAPE_CIRCLE, "Z", false));
        buttons.add(new VirtualButton("ESC", 850, 950, 70, Color.DKGRAY, 150, Color.WHITE, SHAPE_SQUARE, "ESC", false));
        buttons.add(new VirtualButton("START", 1150, 950, 70, Color.DKGRAY, 150, Color.WHITE, SHAPE_SQUARE, "RETURN", false));
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
                    try {
                        java.io.InputStream is = getActivity().getContentResolver().openInputStream(uri);
                        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                        StringBuilder sb = new StringBuilder(); String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                        reader.close(); is.close();
                        
                                                JSONObject root = new JSONObject(sb.toString());
                        
                        // 【兼容逻辑】：如果是用新版 exportAllData 导出的，布局数据叫 layout；如果是旧版导出的，叫 buttons
                        JSONArray btnArray = root.has("layout") ? root.getJSONArray("layout") : root.getJSONArray("buttons");
                        
                        SharedPreferences.Editor editor = DynamicGamepadView.instance.getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
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
                        
                        editor.putInt("OverlayMode_" + DynamicGamepadView.instance.currentSlot, root.optInt("overlayMode", 0));
                        editor.putString("OverlayUri1_" + DynamicGamepadView.instance.currentSlot, root.optString("overlayUri1", ""));
                        editor.putFloat("OverlayX1_" + DynamicGamepadView.instance.currentSlot, (float)root.optDouble("overlayX1", 0));
                        editor.putFloat("OverlayY1_" + DynamicGamepadView.instance.currentSlot, (float)root.optDouble("overlayY1", 0));
                        editor.putFloat("OverlayScale1_" + DynamicGamepadView.instance.currentSlot, (float)root.optDouble("overlayScale1", 1.0f));
                        editor.putString("OverlayUri2_" + DynamicGamepadView.instance.currentSlot, root.optString("overlayUri2", ""));
                        editor.putFloat("OverlayX2_" + DynamicGamepadView.instance.currentSlot, (float)root.optDouble("overlayX2", 0));
                        editor.putFloat("OverlayY2_" + DynamicGamepadView.instance.currentSlot, (float)root.optDouble("overlayY2", 0));
                        editor.putFloat("OverlayScale2_" + DynamicGamepadView.instance.currentSlot, (float)root.optDouble("overlayScale2", 1.0f));
                        editor.putFloat("OverlayRot1_" + DynamicGamepadView.instance.currentSlot, (float)root.optDouble("overlayRotation1", 0f));
                        editor.putFloat("OverlayRot2_" + DynamicGamepadView.instance.currentSlot, (float)root.optDouble("overlayRotation2", 0f));
                        editor.putBoolean("FS_HideOverlay_" + DynamicGamepadView.instance.currentSlot, root.optBoolean("isFullscreenHideOverlay", false));
                        editor.putBoolean("AutoHide_" + DynamicGamepadView.instance.currentSlot, root.optBoolean("isAutoHideEnabled", true));
                        editor.putInt("AutoHideSec_" + DynamicGamepadView.instance.currentSlot, root.optInt("autoHideSeconds", 5));
                        editor.apply(); 

                        // 【新增修复：读取导入文件中的所有自定义风格】
                        if (root.has("styles")) {
                            JSONArray styleArr = root.getJSONArray("styles");
                            DynamicGamepadView.instance.styleList.clear();
                            // 强制保留基础的渐变风格作为第一项兜底
                            DynamicGamepadView.instance.styleList.add(new GamepadStyle("纯色渐变风格 (默认1)"));
                            for (int i = 0; i < styleArr.length(); i++) {
                                GamepadStyle importedStyle = GamepadStyle.fromJson(styleArr.getJSONObject(i));
                                // 避免重复导入基础默认风格
                                if (!importedStyle.styleName.contains("纯色渐变")) {
                                    DynamicGamepadView.instance.styleList.add(importedStyle);
                                }
                            }
                        }
                        
                        DynamicGamepadView.instance.loadConfig(DynamicGamepadView.instance.currentSlot);
                        Toast.makeText(getActivity(), "✅ 布局与风格导入成功！", Toast.LENGTH_LONG).show();
                        
                    } catch (Exception e) { Toast.makeText(getActivity(), "❌ 导入失败，文件可能已损坏", Toast.LENGTH_SHORT).show(); }
                }
            }
            getFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
        }
    }
}
    
