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

       public float joyBaseX = 250, joyBaseY = 700;
    public float joyRadius = 180;
    public float joyHitboxRadius = 270; // 【新增】摇杆独立触摸判定范围
    public int joyAlpha = 200;
    public int joyColor = Color.parseColor("#FF5555"); // 【修改】默认摇杆中心为红色
    
    private float joyKnobX = 250, joyKnobY = 700;
    private int joyPointerId = -1;
    private boolean isDraggingJoy = false;
    
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
        public boolean isMacroPlaying = false; // 宏状态标记
        public List<List<Integer>> macroSteps = new ArrayList<>(); // 存储宏指令序列的列表

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
            if (customImageUri != null && !customImageUri.isEmpty()) {
                try {
                    Uri uri = Uri.parse(customImageUri);
                    InputStream is = context.getContentResolver().openInputStream(uri);
                    Bitmap raw = BitmapFactory.decodeStream(is);
                    skinBitmap = Bitmap.createScaledBitmap(raw, (int)(radius*2), (int)(radius*2), true);
                    if (is != null) is.close();
                } catch (Exception e) { skinBitmap = null; }
            } else { skinBitmap = null; }
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
    
            public void onImagePicked(String uriStr) {
        try {
            Uri uri = Uri.parse(uriStr);
            InputStream is = getContext().getContentResolver().openInputStream(uri);
            Bitmap raw = BitmapFactory.decodeStream(is);
            
            if (imagePickerTarget == 1) { // 摇杆外框
                joySkinBaseUri = uriStr;
                joySkinBaseBitmap = Bitmap.createScaledBitmap(raw, (int)(joyRadius*2), (int)(joyRadius*2), true);
                Toast.makeText(getContext(), "摇杆外框皮肤应用成功！", Toast.LENGTH_SHORT).show();
            } else if (imagePickerTarget == 2) { // 摇杆中心
                joySkinKnobUri = uriStr;
                joySkinKnobBitmap = Bitmap.createScaledBitmap(raw, (int)(joyRadius*2), (int)(joyRadius*2), true);
                Toast.makeText(getContext(), "摇杆中心皮肤应用成功！", Toast.LENGTH_SHORT).show();
            } else if (imagePickerTarget == 3 && currentlyEditingButton != null) { // 普通按键
                currentlyEditingButton.customImageUri = uriStr;
                currentlyEditingButton.skinBitmap = Bitmap.createScaledBitmap(raw, (int)(currentlyEditingButton.radius*2), (int)(currentlyEditingButton.radius*2), true);
                Toast.makeText(getContext(), "按键皮肤应用成功！", Toast.LENGTH_SHORT).show();
            }
            if (is != null) is.close();
            saveConfig();
            invalidate();
        } catch (Exception e) {}
        imagePickerTarget = 0;
    }
        
    

    // =====================================
    // 渲染引擎
    // =====================================
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

                // 【新增】动态计算菜单按键的位置和缩放
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
            
            // 【新增：如果是网格模式，画出辅助线】
            if (isGridSnapMode) {
                paintBtn.setColor(Color.WHITE);
                paintBtn.setAlpha(30); // 半透明白线
                paintBtn.setStrokeWidth(1f);
                for (int i = 0; i < getWidth(); i += GRID_SIZE) {
                    canvas.drawLine(i, 0, i, getHeight(), paintBtn);
                }
                for (int i = 0; i < getHeight(); i += GRID_SIZE) {
                    canvas.drawLine(0, i, getWidth(), i, paintBtn);
                }
            }
            
            paintText.setTextSize(40f);
            paintText.setShadowLayer(5f, 2f, 2f, Color.BLACK);
            canvas.drawText("【编辑模式】拖动调整，轻触设置", getWidth() / 2f, 100, paintText);
        }
        

        for (VirtualButton btn : buttons) {
            if (joystickMode > 0 && btn.isDirectional) continue;

            int currentAlpha = isEditMode ? Math.max(120, btn.alpha) : (btn.isPressed ? 255 : btn.alpha);
            tempRect.set(btn.cx - btn.radius, btn.cy - btn.radius, btn.cx + btn.radius, btn.cy + btn.radius);
            
            if (btn.skinBitmap != null) {
                paintBtn.setAlpha(currentAlpha);
                if (btn.shape == SHAPE_CIRCLE) {
                    // 圆形图片裁剪效果由于性能原因，一般直接画方图。如需严格圆角，建议图片自带透明。
                    canvas.drawBitmap(btn.skinBitmap, null, tempRect, paintBtn);
                } else {
                    canvas.drawBitmap(btn.skinBitmap, null, tempRect, paintBtn);
                }
            } else {
                int baseColor = Color.argb(currentAlpha, Color.red(btn.color), Color.green(btn.color), Color.blue(btn.color));
                int darkColor = Color.argb(currentAlpha, Math.max(0, Color.red(btn.color)-80), Math.max(0, Color.green(btn.color)-80), Math.max(0, Color.blue(btn.color)-80));
                
                RadialGradient gradient = new RadialGradient(
                        btn.cx - btn.radius * 0.3f, btn.cy - btn.radius * 0.3f, 
                        btn.radius * 1.3f, baseColor, darkColor, Shader.TileMode.CLAMP);
                
                paintBtn.setShader(gradient);
                if (currentAlpha > 80) paintBtn.setShadowLayer(10.0f, 0.0f, 5.0f, Color.argb(currentAlpha/2, 0, 0, 0));
                else paintBtn.clearShadowLayer();
                
                if (btn.shape == SHAPE_CIRCLE) {
                    canvas.drawCircle(btn.cx, btn.cy, btn.radius, paintBtn);
                } else {
                    canvas.drawRoundRect(tempRect, btn.radius*0.3f, btn.radius*0.3f, paintBtn);
                }
                paintBtn.clearShadowLayer();
                paintBtn.setShader(null);
            }

                        paintText.setColor(btn.textColor);
            paintText.setAlpha(currentAlpha);
            paintText.setTextSize(btn.radius * 0.6f);
            paintText.setTextAlign(Paint.Align.CENTER); // 【修复】确保居中对齐
            paintText.setShadowLayer(3f, 1f, 1f, (btn.textColor == Color.BLACK) ? Color.WHITE : Color.BLACK);
            
            // 【修复】计算字体的物理中轴线，实现绝对垂直居中
            Paint.FontMetrics fm = paintText.getFontMetrics();
            float textOffset = (fm.descent - fm.ascent) / 2 - fm.descent;
            canvas.drawText(btn.id, btn.cx, btn.cy + textOffset, paintText); 
            
            paintText.clearShadowLayer();
            

                        if (isEditMode) {
                // 原来的白色外框
                paintBtn.setStyle(Paint.Style.STROKE);
                paintBtn.setStrokeWidth(4f);
                paintBtn.setColor(Color.WHITE);
                paintBtn.setAlpha(255);
                if (btn.shape == SHAPE_CIRCLE) canvas.drawCircle(btn.cx, btn.cy, btn.radius + 6, paintBtn);
                else canvas.drawRoundRect(btn.cx - btn.radius - 6, btn.cy - btn.radius - 6, btn.cx + btn.radius + 6, btn.cy + btn.radius + 6, btn.radius*0.3f, btn.radius*0.3f, paintBtn);
                
                // 【修复】这才是绘制黄色虚线判定圈的正确位置！
                Paint dashPaint = new Paint();
                dashPaint.setStyle(Paint.Style.STROKE);
                dashPaint.setStrokeWidth(3f);
                dashPaint.setColor(Color.YELLOW);
                dashPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{10f, 10f}, 0));
                
                if (btn.shape == SHAPE_CIRCLE) {
                    canvas.drawCircle(btn.cx, btn.cy, btn.hitboxRadius, dashPaint);
                } else {
                    canvas.drawRoundRect(btn.cx - btn.hitboxRadius, btn.cy - btn.hitboxRadius, 
                                         btn.cx + btn.hitboxRadius, btn.cy + btn.hitboxRadius, btn.radius*0.3f, btn.radius*0.3f, dashPaint);
                }
                paintBtn.setStyle(Paint.Style.FILL);
            }
        } // 循环结束
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
        } else if (joystickMode == 1) { // 现代纯色圆盘
            paintBtn.setColor(joyColor); paintBtn.setAlpha((int)(currentAlpha * 0.3f));
            canvas.drawCircle(joyBaseX, joyBaseY, joyRadius, paintBtn);
        } else if (joystickMode == 2) { // 【修改】经典街机红杆：底盘固定为深黑灰色
            RadialGradient baseGrad = new RadialGradient(joyBaseX, joyBaseY, joyRadius, Color.parseColor("#333333"), Color.parseColor("#080808"), Shader.TileMode.CLAMP);
            paintBtn.setShader(baseGrad); paintBtn.setAlpha((int)(currentAlpha * 0.9f));
            canvas.drawCircle(joyBaseX, joyBaseY, joyRadius, paintBtn);
            paintBtn.setShader(null);
        } else if (joystickMode == 3) { // 【修改】纯正的8向分离按键
            paintBtn.setColor(Color.DKGRAY); paintBtn.setAlpha((int)(currentAlpha * 0.5f));
            paintText.setColor(Color.WHITE); paintText.setAlpha(currentAlpha); paintText.setTextSize(joyRadius * 0.35f);
            paintText.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics fm = paintText.getFontMetrics();
            float textOffset = (fm.descent - fm.ascent) / 2 - fm.descent;
            
            // 8个方向的标识符
            String[] dirs = {"➡", "↘", "⬇", "↙", "⬅", "↖", "⬆", "↗"}; 
            for (int i = 0; i < 8; i++) {
                float angle = (float) Math.toRadians(i * 45);
                float bx = joyBaseX + (float) Math.cos(angle) * joyRadius * 0.8f;
                float by = joyBaseY + (float) Math.sin(angle) * joyRadius * 0.8f;
                // 画8个独立的圆形底座
                canvas.drawCircle(bx, by, joyRadius * 0.28f, paintBtn);
                // 画方向箭头
                canvas.drawText(dirs[i], bx, by + textOffset, paintText);
            }
        }

        // 仅模式1和2画8向指示白线
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

        // ========= 2. 绘制摇杆帽 =========
        if (joystickMode != 3) { // 模式3是8个分离按键，不需要摇杆帽
            if (joySkinKnobBitmap != null) {
                paintBtn.setAlpha(currentAlpha);
                float knobRad = joyRadius * 0.5f; 
                tempRect.set(joyKnobX - knobRad, joyKnobY - knobRad, joyKnobX + knobRad, joyKnobY + knobRad);
                canvas.drawBitmap(joySkinKnobBitmap, null, tempRect, paintBtn);
            } else if (joystickMode == 1) { // 纯色圆盘中心
                paintBtn.setColor(joyColor); paintBtn.setAlpha(currentAlpha);
                canvas.drawCircle(joyKnobX, joyKnobY, joyRadius * 0.35f, paintBtn);
            } else if (joystickMode == 2) { // 街机摇杆球 (使用自定义颜色，默认红色)
                paintBtn.setColor(Color.parseColor("#AAAAAA")); paintBtn.setStrokeWidth(25f);
                paintBtn.setStyle(Paint.Style.STROKE); paintBtn.setAlpha(currentAlpha);
                canvas.drawLine(joyBaseX, joyBaseY, joyKnobX, joyKnobY, paintBtn); // 画金属杆
                paintBtn.setStyle(Paint.Style.FILL);
                
                int darkColor = Color.rgb(Math.max(0, Color.red(joyColor)-100), Math.max(0, Color.green(joyColor)-100), Math.max(0, Color.blue(joyColor)-100));
                RadialGradient ballGrad = new RadialGradient(joyKnobX - 15, joyKnobY - 15, joyRadius * 0.5f, joyColor, darkColor, Shader.TileMode.CLAMP);
                paintBtn.setShader(ballGrad); paintBtn.setShadowLayer(15f, 0, 10f, Color.argb(currentAlpha, 0,0,0));
                canvas.drawCircle(joyKnobX, joyKnobY, joyRadius * 0.45f, paintBtn);
                paintBtn.clearShadowLayer(); paintBtn.setShader(null);
            }
        } else if (joystickMode == 3 && joyPointerId != -1) {
            // 模式3按下时，在手指位置画个发光的触点反馈
            paintBtn.setColor(joyColor); paintBtn.setAlpha((int)(currentAlpha * 0.6f));
            canvas.drawCircle(joyKnobX, joyKnobY, joyRadius * 0.25f, paintBtn);
        }

        // ========= 3. 编辑模式提示 =========
        if (isEditMode) {
            paintBtn.setStyle(Paint.Style.STROKE); paintBtn.setStrokeWidth(5f); paintBtn.setColor(Color.WHITE); paintBtn.setAlpha(255);
            canvas.drawCircle(joyBaseX, joyBaseY, joyRadius + 10, paintBtn); // 视觉范围白圈
            
            // 【修改】画出隐藏的触摸判定范围 (黄色虚线)
            Paint dashPaint = new Paint();
            dashPaint.setStyle(Paint.Style.STROKE); dashPaint.setStrokeWidth(3f); dashPaint.setColor(Color.YELLOW);
            dashPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{10f, 10f}, 0));
            canvas.drawCircle(joyBaseX, joyBaseY, joyHitboxRadius, dashPaint);
            
            paintText.setColor(Color.WHITE); paintText.setTextSize(35f); paintText.setShadowLayer(3f,0,0,Color.BLACK);
            canvas.drawText("摇杆控制区", joyBaseX, joyBaseY - joyHitboxRadius - 20, paintText);
            paintBtn.setStyle(Paint.Style.FILL); paintText.clearShadowLayer();
        }
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
                if (isVibrationOn) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                
                if (btn.macroSteps.size() > 1) {
                    btn.executeMacro(); // 触发一键连招
                } else if (!btn.macroSteps.isEmpty()) {
                    for (int code : btn.macroSteps.get(0)) SDLActivity.onNativeKeyDown(code); // 瞬间触发同按组合键
                }
                       } else if (btn.isPressed && !isTouchedNow) {
                btn.isPressed = false;
                // 只有普通键/组合键需要在这里松开，宏已经在子线程自己松开了
                if (btn.macroSteps.size() <= 1 && !btn.macroSteps.isEmpty()) {
                    for (int code : btn.macroSteps.get(0)) SDLActivity.onNativeKeyUp(code);
                }
            }
        }
        invalidate();   // <--- 【补上这行】刷新屏幕
        return true;    // <--- 【补上这行】结束触控事件
    }                   // <--- 【补上这个大括号】把 onTouchEvent 关上

    // 【只保留这一个完整的就行了！】
    private void triggerDirection(String dirId, boolean pressed) {
        for (VirtualButton btn : buttons) {
            if (btn.id.equals(dirId) && btn.isDirectional) {
                if (pressed && !btn.isPressed) { btn.isPressed = true; for (int c : btn.keyCodes) SDLActivity.onNativeKeyDown(c); } 
                else if (!pressed && btn.isPressed) { btn.isPressed = false; for (int c : btn.keyCodes) SDLActivity.onNativeKeyUp(c); }
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

    // =====================================
    // UI 面板渲染与系统弹窗
    // =====================================
    private void showMainMenu() {
                String modeText = isEditMode ? "💾 保存并退出编辑" : "🛠️ 开启按键编辑";
        // 【新增】网格模式状态文本
        String gridText = isGridSnapMode ? "🧲 网格吸附：已开启" : "🧲 网格吸附：已关闭 (自由拖动)";
        String joyText = "🕹️ 摇杆形态: " + (joystickMode==0?"分离十字键":joystickMode==1?"现代白圆盘":joystickMode==2?"经典红杆":"8向十字盘");
        String vibText = "📳 物理震动: " + (isVibrationOn?"已开启":"已关闭");
        
        // 【修改】把网格选项加进数组
        CharSequence[] options = {modeText, "➕ 新建组合键/宏", gridText, joyText, vibText, "📂 布局存档管理 / 导入导出", "🔄 恢复初始默认布局"};

        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("⚙️ 游戏面板全局设置")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) { isEditMode = !isEditMode; if (!isEditMode) saveConfig(); invalidate(); } 
                    else if (which == 1) {
                        VirtualButton newBtn = new VirtualButton("新键", getWidth() / 2f, getHeight() / 2f, 90, Color.RED, 150, Color.WHITE, SHAPE_CIRCLE, "Z+X", false);
                        buttons.add(newBtn); isEditMode = true; showButtonSettingsDialog(newBtn);
                    } 
                    else if (which == 2) { 
                        // 【新增】切换网格模式
                        isGridSnapMode = !isGridSnapMode; 
                        Toast.makeText(getContext(), isGridSnapMode ? "已开启网格吸附" : "已开启自由拖动", Toast.LENGTH_SHORT).show();
                    } 
                    // 后面的序号依次加 1
                    else if (which == 3) { joystickMode = (joystickMode + 1) % 4; saveConfig(); invalidate(); } 
                    else if (which == 4) { isVibrationOn = !isVibrationOn; saveConfig(); Toast.makeText(getContext(), isVibrationOn ? "震动开启" : "震动关闭", Toast.LENGTH_SHORT).show(); } 
                    else if (which == 5) { showProfileManager(); } 
                    else if (which == 6) {
                        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setTitle("⚠️ 警告").setMessage("确定要清空所有自定义修改，恢复为原版默认按键布局吗？")
                            .setPositiveButton("确定恢复", (d, w) -> { loadDefaultLayout(); saveConfig(); invalidate(); })
                            .setNegativeButton("取消", null).show();
                    }
                }).show();   
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
        currentlyEditingButton = btn; isEditingJoystickSkin = false;
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

    private SeekBar createColorBar(LinearLayout parent, String label, int progress) {
        TextView tv = new TextView(getContext());
        tv.setText(label);
        tv.setTextColor(Color.WHITE); // 滑动条上的文字强制纯白
        tv.setPadding(0, 10, 0, 0);
        SeekBar sb = new SeekBar(getContext());
        sb.setMax(255);
        sb.setProgress(progress);
        sb.setPadding(0, 10, 0, 20);
        parent.addView(tv);
        parent.addView(sb);
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
                obj.put("skin", btn.customImageUri); obj.put("hitboxRadius", btn.hitboxRadius);
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
            editor.putFloat("MenuX", menuX); editor.putFloat("MenuY", menuY);
            editor.putFloat("MenuScale", menuScale); editor.putInt("MenuAlpha", menuAlpha);
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
                btn.customImageUri = o.optString("skin", ""); btn.loadSkinFromUri(getContext());
                buttons.add(btn);
            }
            joystickMode = prefs.getInt("JoystickMode_" + slot, 0);
            joyBaseX = prefs.getFloat("JoyX_" + slot, 250); joyBaseY = prefs.getFloat("JoyY_" + slot, 700);
            joyRadius = prefs.getFloat("JoyR_" + slot, 180); 
            joyHitboxRadius = prefs.getFloat("JoyHitR_" + slot, 270);
            joyAlpha = prefs.getInt("JoyA_" + slot, 200);
            joyColor = prefs.getInt("JoyColor_" + slot, Color.parseColor("#FF5555"));
            isVibrationOn = prefs.getBoolean("Vibration_" + slot, true);
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
            menuScale = prefs.getFloat("MenuScale", 1.0f); menuAlpha = prefs.getInt("MenuAlpha", 220);
            invalidate();
        } catch (Exception e) { loadDefaultLayout(); }
    }

        private void loadDefaultLayout() {
        buttons.clear();
        // 【核心修复】恢复默认时，把摇杆彻底重置到初始状态
        joystickMode = 0;
        isVibrationOn = true; // 补上恢复默认震动
        imagePickerTarget = 0; // 补上清空选图状态
        joyBaseX = 250; joyBaseY = 700; joyKnobX = 250; joyKnobY = 700;
        joyRadius = 180; joyHitboxRadius = 270; joyAlpha = 200; joyColor = Color.parseColor("#FF5555"); 
        joySkinBaseUri = ""; joySkinKnobUri = ""; joySkinBaseBitmap = null; joySkinKnobBitmap = null;
    
        
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
        buttons.add(new VirtualButton("START", 1000, 950, 70, Color.DKGRAY, 150, Color.WHITE, SHAPE_SQUARE, "RETURN", false));
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
                        JSONObject root = new JSONObject();
                        root.put("joystickMode", DynamicGamepadView.instance.joystickMode);
                        root.put("joyBaseX", DynamicGamepadView.instance.joyBaseX); 
                        root.put("joyBaseY", DynamicGamepadView.instance.joyBaseY);
                        root.put("joyRadius", DynamicGamepadView.instance.joyRadius);
                        root.put("joyHitboxRadius", DynamicGamepadView.instance.joyHitboxRadius);
                        root.put("joyAlpha", DynamicGamepadView.instance.joyAlpha);
                        root.put("joyColor", DynamicGamepadView.instance.joyColor);
                        root.put("isVibrationOn", DynamicGamepadView.instance.isVibrationOn);
                        root.put("buttons", new JSONArray(DynamicGamepadView.instance.getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_LAYOUT_PREFIX + DynamicGamepadView.instance.currentSlot, "[]")));
                        root.put("joySkinBase", DynamicGamepadView.instance.joySkinBaseUri);
                        root.put("joySkinKnob", DynamicGamepadView.instance.joySkinKnobUri);

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
                        JSONArray btnArray = root.getJSONArray("buttons");
                        
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
                        editor.putString("JoySkinBase_" + DynamicGamepadView.instance.currentSlot, root.optString("joySkinBase", ""));
                        editor.putString("JoySkinKnob_" + DynamicGamepadView.instance.currentSlot, root.optString("joySkinKnob", ""));
                        editor.apply(); 
                        
                        DynamicGamepadView.instance.loadConfig(DynamicGamepadView.instance.currentSlot);
                        Toast.makeText(getActivity(), "✅ 布局导入成功！", Toast.LENGTH_LONG).show();
                    } catch (Exception e) { Toast.makeText(getActivity(), "❌ 导入失败，文件可能已损坏", Toast.LENGTH_SHORT).show(); }
                }
            }
            getFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
        }
    }
}
    
