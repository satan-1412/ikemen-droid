package org.libsdl.app;

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
    public int joyAlpha = 200;
    private float joyKnobX = 250, joyKnobY = 700;
    private int joyPointerId = -1;
    private boolean isDraggingJoy = false;
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
        
        // 【新增】存储宏指令序列的列表
        public List<List<Integer>> macroSteps = new ArrayList<>();

        public VirtualButton(String id, float cx, float cy, float radius, int color, int alpha, int textColor, int shape, String keyMapStr, boolean isDir) {
            this.id = id; this.cx = cx; this.cy = cy;
            this.radius = radius; this.color = color;
            this.alpha = alpha; this.textColor = textColor;
            this.shape = shape; this.keyMapStr = keyMapStr;
            this.isDirectional = isDir;
            parseKeyCodes();
        }

        public void parseKeyCodes() {
            keyCodes.clear();
            macroSteps.clear();
            if (keyMapStr == null || keyMapStr.isEmpty()) return;
            
            // 【新增】按逗号拆分宏步骤，比如 "DOWN,RIGHT,Z"
            String[] steps = keyMapStr.toUpperCase().split(",");
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
            // 兼容非宏的普通按键
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
    }

    private static int mapStringToKeyCode(String k) {
        if (k.equals("UP")) return KeyEvent.KEYCODE_DPAD_UP;
        if (k.equals("DOWN")) return KeyEvent.KEYCODE_DPAD_DOWN;
        if (k.equals("LEFT")) return KeyEvent.KEYCODE_DPAD_LEFT;
        if (k.equals("RIGHT")) return KeyEvent.KEYCODE_DPAD_RIGHT;
        if (k.equals("ENTER") || k.equals("RETURN")) return KeyEvent.KEYCODE_ENTER;
        if (k.equals("SPACE")) return KeyEvent.KEYCODE_SPACE;
        if (k.equals("ESC") || k.equals("ESCAPE")) return KeyEvent.KEYCODE_ESCAPE;
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
        if (currentlyEditingButton != null) {
            currentlyEditingButton.customImageUri = uriStr;
            currentlyEditingButton.loadSkinFromUri(getContext());
            saveConfig();
            invalidate();
            Toast.makeText(getContext(), "皮肤应用成功！", Toast.LENGTH_SHORT).show();
        }
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

            int currentAlpha = (btn.isPressed && !isEditMode) ? 255 : btn.alpha;
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
                paintBtn.setStyle(Paint.Style.STROKE);
                paintBtn.setStrokeWidth(4f);
                paintBtn.setColor(Color.WHITE);
                paintBtn.setAlpha(255);
                if (btn.shape == SHAPE_CIRCLE) canvas.drawCircle(btn.cx, btn.cy, btn.radius + 6, paintBtn);
                else canvas.drawRoundRect(btn.cx - btn.radius - 6, btn.cy - btn.radius - 6, btn.cx + btn.radius + 6, btn.cy + btn.radius + 6, btn.radius*0.3f, btn.radius*0.3f, paintBtn);
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

        if (joystickMode == 1) {
            paintBtn.setColor(Color.WHITE);
            paintBtn.setAlpha((int)(currentAlpha * 0.3f));
            canvas.drawCircle(joyBaseX, joyBaseY, joyRadius, paintBtn);
            paintBtn.setAlpha(currentAlpha);
            canvas.drawCircle(joyKnobX, joyKnobY, joyRadius * 0.35f, paintBtn);
        } else if (joystickMode == 2) {
            RadialGradient baseGrad = new RadialGradient(joyBaseX, joyBaseY, joyRadius, Color.parseColor("#444444"), Color.parseColor("#111111"), Shader.TileMode.CLAMP);
            paintBtn.setShader(baseGrad); paintBtn.setAlpha((int)(currentAlpha * 0.8f));
            canvas.drawCircle(joyBaseX, joyBaseY, joyRadius, paintBtn);
            paintBtn.setShader(null);
            
            paintBtn.setColor(Color.parseColor("#AAAAAA")); paintBtn.setStrokeWidth(25f);
            paintBtn.setStyle(Paint.Style.STROKE); paintBtn.setAlpha(currentAlpha);
            canvas.drawLine(joyBaseX, joyBaseY, joyKnobX, joyKnobY, paintBtn);
            paintBtn.setStyle(Paint.Style.FILL);
            
            RadialGradient ballGrad = new RadialGradient(joyKnobX - 15, joyKnobY - 15, joyRadius * 0.5f, Color.parseColor("#FF5555"), Color.parseColor("#880000"), Shader.TileMode.CLAMP);
            paintBtn.setShader(ballGrad); paintBtn.setShadowLayer(15f, 0, 10f, Color.argb(currentAlpha, 0,0,0));
            canvas.drawCircle(joyKnobX, joyKnobY, joyRadius * 0.45f, paintBtn);
            paintBtn.clearShadowLayer(); paintBtn.setShader(null);
        }

        if (isEditMode) {
            paintBtn.setStyle(Paint.Style.STROKE); paintBtn.setStrokeWidth(5f); paintBtn.setColor(Color.WHITE); paintBtn.setAlpha(255);
            canvas.drawCircle(joyBaseX, joyBaseY, joyRadius + 10, paintBtn);
            paintText.setColor(Color.WHITE); paintText.setTextSize(35f); paintText.setShadowLayer(3f,0,0,Color.BLACK);
            canvas.drawText("摇杆控制区", joyBaseX, joyBaseY - joyRadius - 20, paintText);
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
                        if (Math.hypot(px - joyBaseX, py - joyBaseY) < joyRadius * 1.5f) joyPointerId = event.getPointerId(i);
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
                
                // 【绝杀Bug代码】如果这个手指当前正在抬起（离开屏幕），直接无视它！确保能瞬间触发按键松开
                if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) && i == actionIndex) {
                    continue;
                }

                float px = event.getX(i), py = event.getY(i);
                if (btn.shape == SHAPE_CIRCLE) {
                    if (Math.hypot(px - btn.cx, py - btn.cy) < btn.radius) isTouchedNow = true;
                } else {
                    if (px > btn.cx - btn.radius && px < btn.cx + btn.radius && py > btn.cy - btn.radius && py < btn.cy + btn.radius) isTouchedNow = true;
                }
            }
            
            if (!btn.isPressed && isTouchedNow) {
                btn.isPressed = true;
                if (isVibrationOn) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                for (int code : btn.keyCodes) SDLActivity.onNativeKeyDown(code);
            } else if (btn.isPressed && !isTouchedNow) {
                btn.isPressed = false;
                for (int code : btn.keyCodes) SDLActivity.onNativeKeyUp(code);
            }
        }
        invalidate(); return true;
    }
    

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
        String joyText = "🕹️ 摇杆形态: " + (joystickMode==0?"分离十字键":joystickMode==1?"现代白圆盘":"经典街机红杆");
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
                    else if (which == 3) { joystickMode = (joystickMode + 1) % 3; saveConfig(); invalidate(); } 
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
    private void showJoystickSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle("🕹️ 摇杆独立设置");
        
        ScrollView scroll = new ScrollView(getContext());
        scroll.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        
        LinearLayout layout = new LinearLayout(getContext()); 
        layout.setOrientation(LinearLayout.VERTICAL); 
        layout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        layout.setPadding(60, 30, 60, 30);
        
        layout.addView(DynamicGamepadView.this.createTitle("外观与尺寸:"));
        final SeekBar alphaBar = DynamicGamepadView.this.createColorBar(layout, "摇杆不透明度 (0-255)", joyAlpha);
        final SeekBar sizeBar = DynamicGamepadView.this.createColorBar(layout, "摇杆整体大小", (int)joyRadius); 
        sizeBar.setMax(400);
        
        scroll.addView(layout);
        builder.setView(scroll);
        builder.setPositiveButton("💾 保存", (dialog, which) -> { 
            joyAlpha = alphaBar.getProgress(); 
            joyRadius = Math.max(50, sizeBar.getProgress()); 
            saveConfig(); invalidate(); 
        });
        builder.show();
    }

    private void showButtonSettingsDialog(final VirtualButton btn) {
        currentlyEditingButton = btn;
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle("🔧 配置按键: " + btn.id);

        ScrollView scroll = new ScrollView(getContext());
        scroll.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        
        LinearLayout layout = new LinearLayout(getContext()); 
        layout.setOrientation(LinearLayout.VERTICAL); 
        layout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        layout.setPadding(60, 30, 60, 30); 

        layout.addView(DynamicGamepadView.this.createTitle("1. 按键屏幕显示名称:"));
        final EditText inputName = DynamicGamepadView.this.createEditText("", btn.id); layout.addView(inputName);

        layout.addView(DynamicGamepadView.this.createTitle("2. 按键字体颜色:"));
        final Spinner textColorSpinner = new Spinner(getContext());
        ArrayAdapter<String> textAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, TEXT_COLOR_NAMES);
        textColorSpinner.setAdapter(textAdapter);
        for (int i=0; i<TEXT_COLOR_VALUES.length; i++) { if (btn.textColor == TEXT_COLOR_VALUES[i]) { textColorSpinner.setSelection(i); break; } }
        layout.addView(textColorSpinner);

        layout.addView(DynamicGamepadView.this.createTitle("3. 按键物理形状:"));
        final Spinner shapeSpinner = new Spinner(getContext());
        ArrayAdapter<String> shapeAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, SHAPE_NAMES);
        shapeSpinner.setAdapter(shapeAdapter); shapeSpinner.setSelection(btn.shape); layout.addView(shapeSpinner);

        layout.addView(DynamicGamepadView.this.createTitle("4. 键位映射 (组合键用+, 宏用,分隔):"));
        final EditText inputKey = DynamicGamepadView.this.createEditText("如宏: DOWN,RIGHT,Z", btn.keyMapStr); layout.addView(inputKey);

        layout.addView(DynamicGamepadView.this.createTitle("5. 背景色 (HEX代码/RGB):"));
        final EditText hexInput = DynamicGamepadView.this.createEditText("如: #FF0000", String.format("#%06X", (0xFFFFFF & btn.color))); 
        layout.addView(hexInput);
        
        final View colorPreview = new View(getContext());
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 150);
        previewParams.setMargins(0, 10, 0, 30); colorPreview.setLayoutParams(previewParams); colorPreview.setBackgroundColor(btn.color);
        layout.addView(colorPreview);

        final int[] rgb = {Color.red(btn.color), Color.green(btn.color), Color.blue(btn.color)};
        SeekBar redBar = DynamicGamepadView.this.createColorBar(layout, "红 (R)", rgb[0]); 
        SeekBar greenBar = DynamicGamepadView.this.createColorBar(layout, "绿 (G)", rgb[1]); 
        SeekBar blueBar = DynamicGamepadView.this.createColorBar(layout, "蓝 (B)", rgb[2]);
        SeekBar.OnSeekBarChangeListener colorUpdater = new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                rgb[0] = redBar.getProgress(); rgb[1] = greenBar.getProgress(); rgb[2] = blueBar.getProgress(); colorPreview.setBackgroundColor(Color.rgb(rgb[0], rgb[1], rgb[2]));
            }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        };
        redBar.setOnSeekBarChangeListener(colorUpdater); greenBar.setOnSeekBarChangeListener(colorUpdater); blueBar.setOnSeekBarChangeListener(colorUpdater);

        layout.addView(DynamicGamepadView.this.createTitle("6. 外观与尺寸:"));
        final SeekBar alphaBar = DynamicGamepadView.this.createColorBar(layout, "不透明度 (0-255)", btn.alpha); 
        final SeekBar sizeBar = DynamicGamepadView.this.createColorBar(layout, "按键大小", (int)btn.radius); sizeBar.setMax(300);

        layout.addView(DynamicGamepadView.this.createTitle("7. 自定义图片皮肤:"));
        Button btnPickImage = new Button(getContext()); btnPickImage.setText("🖼️ 从系统相册选择图片"); btnPickImage.setTextColor(Color.WHITE);
        btnPickImage.setOnClickListener(v -> {
            android.app.Activity activity = (android.app.Activity) getContext();
            FileActionFragment fragment = new FileActionFragment();
            android.os.Bundle args = new android.os.Bundle();
            args.putInt("action_type", 0);
            fragment.setArguments(args);
            activity.getFragmentManager().beginTransaction().add(fragment, "file_action").commitAllowingStateLoss();
        });
        layout.addView(btnPickImage);

        Button btnClearImage = new Button(getContext()); btnClearImage.setText("❌ 移除图片，恢复纯色/形状"); btnClearImage.setTextColor(Color.WHITE);
        btnClearImage.setOnClickListener(v -> { btn.customImageUri = ""; btn.skinBitmap = null; Toast.makeText(getContext(), "已清除图片皮肤", Toast.LENGTH_SHORT).show(); invalidate(); });
        layout.addView(btnClearImage);

        scroll.addView(layout);
        builder.setView(scroll);

                builder.setPositiveButton("💾 保存", (dialog, which) -> {
            btn.id = inputName.getText().toString(); 
            btn.textColor = TEXT_COLOR_VALUES[textColorSpinner.getSelectedItemPosition()];
            btn.shape = shapeSpinner.getSelectedItemPosition(); 
            btn.keyMapStr = inputKey.getText().toString().trim().toUpperCase(); 
            btn.parseKeyCodes();
            try { btn.color = Color.parseColor(hexInput.getText().toString().trim()); } 
            catch (Exception e) { btn.color = Color.rgb(redBar.getProgress(), greenBar.getProgress(), blueBar.getProgress()); }
            btn.alpha = alphaBar.getProgress(); 
            // 【修复点】：强制转换为 float
            btn.radius = Math.max(40f, (float)sizeBar.getProgress());
            btn.loadSkinFromUri(getContext()); 
            saveConfig(); 
            invalidate();
        });
        
        builder.setNegativeButton("🗑️ 删除此键", (dialog, which) -> {
            new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("⚠️ 确认删除")
                .setMessage("确定要彻底删除按键 [" + btn.id + "] 吗？")
                .setPositiveButton("确定", (d, w) -> { buttons.remove(btn); saveConfig(); invalidate(); })
                .setNegativeButton("取消", null)
                .show();
        });
        builder.show();
    }
        
        // =====================================
    // 补回被误删的 UI 绘制辅助方法
    // =====================================

    private EditText createEditText(String hint, String text) {
        EditText et = new EditText(getContext());
        et.setHint(hint);
        et.setText(text);
        et.setTextColor(Color.BLACK); // 输入框内必须黑字
        et.setHintTextColor(Color.GRAY);
        et.setBackgroundColor(Color.WHITE); // 强制白色背景，黑白分明防瞎眼
        et.setPadding(20, 20, 20, 20);
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
                obj.put("id", btn.id);
                obj.put("cx", btn.cx);
                obj.put("cy", btn.cy);
                obj.put("radius", btn.radius);
                obj.put("color", btn.color);
                obj.put("alpha", btn.alpha);
                obj.put("textColor", btn.textColor);
                obj.put("shape", btn.shape);
                obj.put("keyMap", btn.keyMapStr);
                obj.put("isDir", btn.isDirectional);
                obj.put("skin", btn.customImageUri);
                array.put(obj);
            }
            editor.putString(KEY_LAYOUT_PREFIX + currentSlot, array.toString());
            editor.putInt("JoystickMode_" + currentSlot, joystickMode);
            editor.putFloat("JoyX_" + currentSlot, joyBaseX);
            editor.putFloat("JoyY_" + currentSlot, joyBaseY);
            editor.putFloat("JoyR_" + currentSlot, joyRadius);
            editor.putInt("JoyA_" + currentSlot, joyAlpha);
            editor.putBoolean("Vibration_" + currentSlot, isVibrationOn);
            // 保存菜单位置与缩放
            editor.putFloat("MenuX", menuX);
            editor.putFloat("MenuY", menuY);
            editor.putFloat("MenuScale", menuScale);
            editor.putInt("MenuAlpha", menuAlpha);
            editor.apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 从存档中读取布局配置
     */
    public void loadConfig(int slot) {
        this.currentSlot = slot;
        String json = prefs.getString(KEY_LAYOUT_PREFIX + slot, null);
        
        // 如果存档不存在，则加载默认布局
        if (json == null || json.isEmpty()) {
            loadDefaultLayout();
            return;
        }

        try {
            buttons.clear();
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                VirtualButton btn = new VirtualButton(
                    o.getString("id"), 
                    (float)o.getDouble("cx"), 
                    (float)o.getDouble("cy"),
                    (float)o.getDouble("radius"), 
                    o.getInt("color"), 
                    o.getInt("alpha"),
                    o.getInt("textColor"), 
                    o.getInt("shape"), 
                    o.getString("keyMap"),
                    o.optBoolean("isDir", false)
                );
                btn.customImageUri = o.optString("skin", "");
                btn.loadSkinFromUri(getContext());
                buttons.add(btn);
            }
            
            // 恢复摇杆与系统设置
            joystickMode = prefs.getInt("JoystickMode_" + slot, 0);
            joyBaseX = prefs.getFloat("JoyX_" + slot, 250);
            joyBaseY = prefs.getFloat("JoyY_" + slot, 700);
            joyRadius = prefs.getFloat("JoyR_" + slot, 180);
            joyAlpha = prefs.getInt("JoyA_" + slot, 200);
            isVibrationOn = prefs.getBoolean("Vibration_" + slot, true);
            
            menuX = prefs.getFloat("MenuX", 20);
            menuY = prefs.getFloat("MenuY", 20);
            menuScale = prefs.getFloat("MenuScale", 1.0f);
            menuAlpha = prefs.getInt("MenuAlpha", 220);
            
            invalidate();
        } catch (Exception e) {
            loadDefaultLayout();
        }
    }

    /**
     * 初始化默认按键布局 (第一次运行或恢复出厂时触发)
     */
    private void loadDefaultLayout() {
        buttons.clear();
        // 方向键组 (Mugen 默认映射)
        buttons.add(new VirtualButton("UP", 250, 550, 80, Color.GRAY, 150, Color.WHITE, SHAPE_CIRCLE, "UP", true));
        buttons.add(new VirtualButton("DOWN", 250, 850, 80, Color.GRAY, 150, Color.WHITE, SHAPE_CIRCLE, "DOWN", true));
        buttons.add(new VirtualButton("LEFT", 100, 700, 80, Color.GRAY, 150, Color.WHITE, SHAPE_CIRCLE, "LEFT", true));
        buttons.add(new VirtualButton("RIGHT", 400, 700, 80, Color.GRAY, 150, Color.WHITE, SHAPE_CIRCLE, "RIGHT", true));
        
        // 动作键组 (A, B, C, X, Y, Z)
        float rx = 1600, ry = 700; 
        buttons.add(new VirtualButton("A", rx, ry, 90, Color.parseColor("#4CAF50"), 180, Color.WHITE, SHAPE_CIRCLE, "A", false));
        buttons.add(new VirtualButton("B", rx + 200, ry - 50, 90, Color.parseColor("#F44336"), 180, Color.WHITE, SHAPE_CIRCLE, "B", false));
        buttons.add(new VirtualButton("C", rx + 400, ry - 100, 90, Color.parseColor("#2196F3"), 180, Color.WHITE, SHAPE_CIRCLE, "C", false));
        buttons.add(new VirtualButton("X", rx, ry - 200, 90, Color.parseColor("#8BC34A"), 180, Color.WHITE, SHAPE_CIRCLE, "X", false));
        buttons.add(new VirtualButton("Y", rx + 200, ry - 250, 90, Color.parseColor("#E91E63"), 180, Color.WHITE, SHAPE_CIRCLE, "Y", false));
        buttons.add(new VirtualButton("Z", rx + 400, ry - 300, 90, Color.parseColor("#03A9F4"), 180, Color.WHITE, SHAPE_CIRCLE, "Z", false));
        
        // 系统键
        buttons.add(new VirtualButton("START", 1000, 950, 70, Color.DKGRAY, 150, Color.WHITE, SHAPE_SQUARE, "RETURN", false));
    }
    // =====================================
    // 幽灵 Fragment：接管全部系统文件与相册请求
    // =====================================
    @SuppressWarnings("deprecation")
    public static class FileActionFragment extends android.app.Fragment {
        @Override
        public void onCreate(android.os.Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            int type = getArguments() != null ? getArguments().getInt("action_type", 0) : 0;
            if (type == 1) { // 导出
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, "ikemen_layout.json");
                startActivityForResult(intent, 44);
            } else if (type == 2) { // 导入
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, 45);
            } else { // 选图片
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
                } else if (requestCode == 44) { // 导出数据写入
                    try {
                        String jsonData = getArguments().getString("export_data");
                        JSONObject root = new JSONObject();
                        root.put("joystickMode", DynamicGamepadView.instance.joystickMode);
                        root.put("joyBaseX", DynamicGamepadView.instance.joyBaseX); root.put("joyBaseY", DynamicGamepadView.instance.joyBaseY);
                        root.put("joyRadius", DynamicGamepadView.instance.joyRadius); root.put("joyAlpha", DynamicGamepadView.instance.joyAlpha);
                        root.put("isVibrationOn", DynamicGamepadView.instance.isVibrationOn);
                        root.put("buttons", new JSONArray(jsonData));
                        
                        java.io.OutputStream os = getActivity().getContentResolver().openOutputStream(uri);
                        os.write(root.toString(4).getBytes(StandardCharsets.UTF_8));
                        os.close();
                        Toast.makeText(getActivity(), "✅ 导出成功！", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) { Toast.makeText(getActivity(), "❌ 导出失败", Toast.LENGTH_SHORT).show(); }
                } else if (requestCode == 45 && DynamicGamepadView.instance != null) { // 导入数据读取
                                       try {
                        java.io.InputStream is = getActivity().getContentResolver().openInputStream(uri);
                        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                        StringBuilder sb = new StringBuilder(); String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                        reader.close(); is.close();
                        
                        JSONObject root = new JSONObject(sb.toString());
                        JSONArray btnArray = root.getJSONArray("buttons");
                        
                        // 【核心修复：在导入时，立刻把所有摇杆设置强行写入 SharedPreferences 存档】
                        SharedPreferences.Editor editor = DynamicGamepadView.instance.getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
                        editor.putString(KEY_LAYOUT_PREFIX + DynamicGamepadView.instance.currentSlot, btnArray.toString());
                        editor.putInt("JoystickMode_" + DynamicGamepadView.instance.currentSlot, root.optInt("joystickMode", 0));
                        editor.putFloat("JoyX_" + DynamicGamepadView.instance.currentSlot, (float) root.optDouble("joyBaseX", 250));
                        editor.putFloat("JoyY_" + DynamicGamepadView.instance.currentSlot, (float) root.optDouble("joyBaseY", 700));
                        editor.putFloat("JoyR_" + DynamicGamepadView.instance.currentSlot, (float) root.optDouble("joyRadius", 180));
                        editor.putInt("JoyA_" + DynamicGamepadView.instance.currentSlot, root.optInt("joyAlpha", 200));
                        editor.putBoolean("Vibration_" + DynamicGamepadView.instance.currentSlot, root.optBoolean("isVibrationOn", true));
                        editor.apply(); // 必须先应用保存
                        
                        // 然后再调用读取，摇杆就会瞬间飞到新存档的位置了！
                        DynamicGamepadView.instance.loadConfig(DynamicGamepadView.instance.currentSlot);
                        Toast.makeText(getActivity(), "✅ 布局导入成功！", Toast.LENGTH_LONG).show();
                    } catch (Exception e) { Toast.makeText(getActivity(), "❌ 导入失败，文件可能已损坏", Toast.LENGTH_SHORT).show(); }
                }
            }
            getFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
        }
    }
}
    
