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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class DynamicGamepadView extends View {
    private static final String PREFS_NAME = "IkemenGamepad_Pro_V4";
    private static final String KEY_LAYOUT_PREFIX = "LayoutSlot_";
    
    public int currentSlot = 0;
    public int joystickMode = 0; // 0=十字, 1=圆盘, 2=街机
    public boolean isVibrationOn = true; // 震动全局开关

    // 摇杆独立属性 (支持拖拽和保存)
    public float joyBaseX = 250, joyBaseY = 700;
    public float joyRadius = 180;
    public int joyAlpha = 200;
    private float joyKnobX = 250, joyKnobY = 700;
    private int joyPointerId = -1;
    private boolean isDraggingJoy = false;

    private final List<VirtualButton> buttons = new ArrayList<>();
    private final Paint paintBtn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintMenu = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final SharedPreferences prefs;
    public boolean isEditMode = false;
    private VirtualButton draggedButton = null;

    private long downTime;
    private float downX, downY;

    private final RectF menuButtonRect = new RectF(20, 20, 250, 110);
    public VirtualButton currentlyEditingButton = null;
    public static DynamicGamepadView instance;

    // 文本颜色预设
    public static final String[] TEXT_COLOR_NAMES = {"白色", "黑色", "红色", "黄色", "蓝色", "绿色"};
    public static final int[] TEXT_COLOR_VALUES = {Color.WHITE, Color.BLACK, Color.RED, Color.YELLOW, Color.BLUE, Color.GREEN};

    public static class VirtualButton {
        public String id;
        public float cx, cy, radius;
        public int color, alpha, textColor;
        public String keyMapStr = "";
        public List<Integer> keyCodes = new ArrayList<>();
        public boolean isPressed = false;
        public String customImageUri = ""; 
        public Bitmap skinBitmap = null;
        public boolean isDirectional = false; 

        public VirtualButton(String id, float cx, float cy, float radius, int color, int alpha, int textColor, String keyMapStr, boolean isDir) {
            this.id = id; this.cx = cx; this.cy = cy;
            this.radius = radius; this.color = color;
            this.alpha = alpha; this.textColor = textColor;
            this.keyMapStr = keyMapStr;
            this.isDirectional = isDir;
            parseKeyCodes();
        }

        public void parseKeyCodes() {
            keyCodes.clear();
            if (keyMapStr == null || keyMapStr.isEmpty()) return;
            String[] parts = keyMapStr.toUpperCase().split("\\+");
            for (String p : parts) {
                int code = mapStringToKeyCode(p.trim());
                if (code != KeyEvent.KEYCODE_UNKNOWN) keyCodes.add(code);
            }
        }

        public void loadSkinFromUri(Context context) {
            if (customImageUri != null && !customImageUri.isEmpty()) {
                try {
                    Uri uri = Uri.parse(customImageUri);
                    InputStream is = context.getContentResolver().openInputStream(uri);
                    Bitmap raw = BitmapFactory.decodeStream(is);
                    // 【修复】每次加载都根据最新的 radius 进行缩放，解决改变大小不生效的问题
                    skinBitmap = Bitmap.createScaledBitmap(raw, (int)(radius*2), (int)(radius*2), true);
                    if (is != null) is.close();
                } catch (Exception e) { 
                    skinBitmap = null; 
                }
            } else { 
                skinBitmap = null; 
            }
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

    // =====================================
    // 渲染引擎 (包含按键与摇杆)
    // =====================================
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        paintMenu.setColor(Color.argb(200, 30, 33, 40));
        paintMenu.setShadowLayer(8f, 0, 4f, Color.BLACK);
        canvas.drawRoundRect(menuButtonRect, 30, 30, paintMenu);
        paintText.setColor(Color.WHITE);
        paintText.setTextSize(40f);
        paintMenu.clearShadowLayer();
        canvas.drawText("⚙ 高级设置", menuButtonRect.centerX(), menuButtonRect.centerY() + 15, paintText);

        if (isEditMode) {
            canvas.drawColor(Color.argb(120, 0, 0, 0));
            paintText.setTextSize(45f);
            canvas.drawText("【编辑模式】拖拽调整位置，轻触按键/摇杆呼出高级设置", getWidth() / 2f, 150, paintText);
        }

        for (VirtualButton btn : buttons) {
            if (joystickMode > 0 && btn.isDirectional) continue;

            int currentAlpha = (btn.isPressed && !isEditMode) ? 255 : btn.alpha;
            
            if (btn.skinBitmap != null) {
                paintBtn.setAlpha(currentAlpha);
                canvas.drawBitmap(btn.skinBitmap, btn.cx - btn.radius, btn.cy - btn.radius, paintBtn);
            } else {
                int baseColor = Color.argb(currentAlpha, Color.red(btn.color), Color.green(btn.color), Color.blue(btn.color));
                int darkColor = Color.argb(currentAlpha, Math.max(0, Color.red(btn.color)-80), Math.max(0, Color.green(btn.color)-80), Math.max(0, Color.blue(btn.color)-80));
                
                RadialGradient gradient = new RadialGradient(
                        btn.cx - btn.radius * 0.3f, btn.cy - btn.radius * 0.3f, 
                        btn.radius * 1.3f, baseColor, darkColor, Shader.TileMode.CLAMP);
                
                paintBtn.setShader(gradient);
                if (currentAlpha > 100) paintBtn.setShadowLayer(15.0f, 0.0f, 10.0f, Color.argb(currentAlpha/2, 0, 0, 0));
                else paintBtn.clearShadowLayer();
                
                canvas.drawCircle(btn.cx, btn.cy, btn.radius, paintBtn);
                paintBtn.clearShadowLayer();
                paintBtn.setShader(null);
            }

            // 【新增】自定义字体颜色渲染
            paintText.setColor(btn.textColor);
            paintText.setAlpha(currentAlpha);
            paintText.setTextSize(btn.radius * 0.55f);
            canvas.drawText(btn.id, btn.cx, btn.cy + (btn.radius * 0.2f), paintText);

            if (isEditMode) {
                paintBtn.setStyle(Paint.Style.STROKE);
                paintBtn.setStrokeWidth(4f);
                paintBtn.setColor(Color.WHITE);
                paintBtn.setAlpha(255);
                canvas.drawCircle(btn.cx, btn.cy, btn.radius + 6, paintBtn);
                paintBtn.setStyle(Paint.Style.FILL);
            }
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (joystickMode > 0) {
            drawJoystick(canvas);
        }
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
            RadialGradient baseGrad = new RadialGradient(joyBaseX, joyBaseY, joyRadius, 
                    Color.parseColor("#444444"), Color.parseColor("#111111"), Shader.TileMode.CLAMP);
            paintBtn.setShader(baseGrad);
            paintBtn.setAlpha((int)(currentAlpha * 0.8f));
            canvas.drawCircle(joyBaseX, joyBaseY, joyRadius, paintBtn);
            paintBtn.setShader(null);
            
            paintBtn.setColor(Color.parseColor("#AAAAAA"));
            paintBtn.setStrokeWidth(25f);
            paintBtn.setStyle(Paint.Style.STROKE);
            paintBtn.setAlpha(currentAlpha);
            canvas.drawLine(joyBaseX, joyBaseY, joyKnobX, joyKnobY, paintBtn);
            paintBtn.setStyle(Paint.Style.FILL);
            
            RadialGradient ballGrad = new RadialGradient(joyKnobX - 15, joyKnobY - 15, joyRadius * 0.5f, 
                    Color.parseColor("#FF5555"), Color.parseColor("#880000"), Shader.TileMode.CLAMP);
            paintBtn.setShader(ballGrad);
            paintBtn.setShadowLayer(15f, 0, 10f, Color.argb(currentAlpha, 0,0,0));
            canvas.drawCircle(joyKnobX, joyKnobY, joyRadius * 0.45f, paintBtn);
            paintBtn.clearShadowLayer();
            paintBtn.setShader(null);
        }

        if (isEditMode) {
            paintBtn.setStyle(Paint.Style.STROKE);
            paintBtn.setStrokeWidth(5f);
            paintBtn.setColor(Color.WHITE);
            paintBtn.setAlpha(255);
            canvas.drawCircle(joyBaseX, joyBaseY, joyRadius + 10, paintBtn);
            paintText.setColor(Color.WHITE);
            paintText.setTextSize(35f);
            canvas.drawText("摇杆控制区", joyBaseX, joyBaseY - joyRadius - 20, paintText);
            paintBtn.setStyle(Paint.Style.FILL);
        }
    }

    // =====================================
    // 触控与物理反馈引擎
    // =====================================
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN && menuButtonRect.contains(event.getX(), event.getY())) {
            showMainMenu();
            return true;
        }

        if (isEditMode) {
            handleEditTouch(event);
            return true;
        }

        if (joystickMode > 0) {
            boolean joyTouched = false;
            for (int i = 0; i < event.getPointerCount(); i++) {
                float px = event.getX(i);
                float py = event.getY(i);
                
                if (px < getWidth() / 2f) {
                    if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                        if (Math.hypot(px - joyBaseX, py - joyBaseY) < joyRadius * 2.5f) {
                            joyPointerId = event.getPointerId(i);
                        }
                    }
                    
                    if (event.getPointerId(i) == joyPointerId) {
                        joyTouched = true;
                        float dx = px - joyBaseX;
                        float dy = py - joyBaseY;
                        float dist = (float) Math.hypot(dx, dy);
                        
                        if (dist > joyRadius) {
                            joyKnobX = joyBaseX + (dx / dist) * joyRadius;
                            joyKnobY = joyBaseY + (dy / dist) * joyRadius;
                        } else {
                            joyKnobX = px;
                            joyKnobY = py;
                        }
                        
                        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
                        if (angle < 0) angle += 360;
                        
                        boolean up = angle > 200 && angle < 340;
                        boolean down = angle > 20 && angle < 160;
                        boolean left = angle > 110 && angle < 250;
                        boolean right = angle < 70 || angle > 290;
                        
                        if (dist < joyRadius * 0.3f) { up = down = left = right = false; }
                        
                        triggerDirection("UP", up);
                        triggerDirection("DOWN", down);
                        triggerDirection("LEFT", left);
                        triggerDirection("RIGHT", right);
                    }
                }
            }
            
            if (!joyTouched || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                joyPointerId = -1;
                joyKnobX = joyBaseX;
                joyKnobY = joyBaseY;
                triggerDirection("UP", false);
                triggerDirection("DOWN", false);
                triggerDirection("LEFT", false);
                triggerDirection("RIGHT", false);
            }
        }

        for (VirtualButton btn : buttons) {
            if (joystickMode > 0 && btn.isDirectional) continue;

            boolean isTouchedNow = false;
            for (int i = 0; i < event.getPointerCount(); i++) {
                if (event.getPointerId(i) == joyPointerId) continue;
                if (Math.hypot(event.getX(i) - btn.cx, event.getY(i) - btn.cy) < btn.radius) {
                    isTouchedNow = true;
                    break;
                }
            }

            if (!btn.isPressed && isTouchedNow) {
                btn.isPressed = true;
                if (isVibrationOn) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); // 震动受控
                for (int code : btn.keyCodes) SDLActivity.onNativeKeyDown(code);
            } else if (btn.isPressed && !isTouchedNow) {
                btn.isPressed = false;
                for (int code : btn.keyCodes) SDLActivity.onNativeKeyUp(code);
            }
        }
        invalidate();
        return true;
    }

    private void triggerDirection(String dirId, boolean pressed) {
        for (VirtualButton btn : buttons) {
            if (btn.id.equals(dirId) && btn.isDirectional) {
                if (pressed && !btn.isPressed) {
                    btn.isPressed = true;
                    for (int code : btn.keyCodes) SDLActivity.onNativeKeyDown(code);
                } else if (!pressed && btn.isPressed) {
                    btn.isPressed = false;
                    for (int code : btn.keyCodes) SDLActivity.onNativeKeyUp(code);
                }
                break;
            }
        }
    }

    // 【修改】加入摇杆的拖动与设置逻辑
    private void handleEditTouch(MotionEvent event) {
        int action = event.getActionMasked();
        float x = event.getX(0);
        float y = event.getY(0);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                downTime = System.currentTimeMillis();
                downX = x; downY = y;
                isDraggingJoy = false;
                draggedButton = null;

                // 优先判定是否点中了摇杆
                if (joystickMode > 0 && Math.hypot(x - joyBaseX, y - joyBaseY) < joyRadius) {
                    isDraggingJoy = true;
                } else {
                    for (int i = buttons.size() - 1; i >= 0; i--) {
                        if (Math.hypot(x - buttons.get(i).cx, y - buttons.get(i).cy) < buttons.get(i).radius * 1.3f) {
                            draggedButton = buttons.get(i);
                            break;
                        }
                    }
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (isDraggingJoy) {
                    joyBaseX = x; joyBaseY = y;
                    joyKnobX = x; joyKnobY = y;
                    invalidate();
                } else if (draggedButton != null) {
                    draggedButton.cx = x; draggedButton.cy = y;
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_UP:
                boolean isTap = System.currentTimeMillis() - downTime < 250 && Math.hypot(x - downX, y - downY) < 20;
                if (isTap) {
                    if (isDraggingJoy) {
                        showJoystickSettingsDialog();
                    } else if (draggedButton != null) {
                        showButtonSettingsDialog(draggedButton);
                    }
                }
                isDraggingJoy = false;
                draggedButton = null;
                break;
        }
    }
    // =====================================
    // 存档、导入导出与序列化逻辑
    // =====================================
    private void loadConfig(int slot) {
        currentSlot = slot;
        String jsonStr = prefs.getString(KEY_LAYOUT_PREFIX + slot, null);
        buttons.clear();
        if (jsonStr != null) {
            try {
                JSONArray array = new JSONArray(jsonStr);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    VirtualButton btn = new VirtualButton(
                            obj.getString("id"),
                            (float) obj.getDouble("cx"), (float) obj.getDouble("cy"), (float) obj.getDouble("radius"),
                            obj.getInt("color"), obj.getInt("alpha"), 
                            obj.optInt("textColor", Color.WHITE), // 兼容老版本，默认白色
                            obj.getString("keyMapStr"),
                            obj.optBoolean("isDirectional", false)
                    );
                    if (obj.has("customImageUri")) {
                        btn.customImageUri = obj.getString("customImageUri");
                        btn.loadSkinFromUri(getContext());
                    }
                    buttons.add(btn);
                }
                joystickMode = prefs.getInt("JoystickMode_" + slot, 0);
                joyBaseX = prefs.getFloat("JoyX_" + slot, 250);
                joyBaseY = prefs.getFloat("JoyY_" + slot, 700);
                joyRadius = prefs.getFloat("JoyR_" + slot, 180);
                joyAlpha = prefs.getInt("JoyA_" + slot, 200);
                isVibrationOn = prefs.getBoolean("Vibration_" + slot, true);
                
                joyKnobX = joyBaseX; joyKnobY = joyBaseY;
                invalidate();
                return;
            } catch (Exception e) { e.printStackTrace(); }
        }
        
        // 首次加载或没存档时，恢复默认
        joystickMode = 0;
        joyBaseX = 250; joyBaseY = 700; joyRadius = 180; joyAlpha = 200;
        isVibrationOn = true;
        loadDefaultLayout();
        invalidate();
    }

    private void saveConfig() {
        try {
            JSONArray array = new JSONArray();
            for (VirtualButton b : buttons) {
                JSONObject obj = new JSONObject();
                obj.put("id", b.id);
                obj.put("cx", b.cx);  obj.put("cy", b.cy);
                obj.put("radius", b.radius);
                obj.put("color", b.color); obj.put("alpha", b.alpha);
                obj.put("textColor", b.textColor);
                obj.put("keyMapStr", b.keyMapStr);
                obj.put("customImageUri", b.customImageUri);
                obj.put("isDirectional", b.isDirectional);
                array.put(obj);
            }
            prefs.edit()
                 .putString(KEY_LAYOUT_PREFIX + currentSlot, array.toString())
                 .putInt("JoystickMode_" + currentSlot, joystickMode)
                 .putFloat("JoyX_" + currentSlot, joyBaseX)
                 .putFloat("JoyY_" + currentSlot, joyBaseY)
                 .putFloat("JoyR_" + currentSlot, joyRadius)
                 .putInt("JoyA_" + currentSlot, joyAlpha)
                 .putBoolean("Vibration_" + currentSlot, isVibrationOn)
                 .apply();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadDefaultLayout() {
        buttons.clear();
        buttons.add(new VirtualButton("UP", 250, 550, 80, Color.DKGRAY, 120, Color.WHITE, "UP", true));
        buttons.add(new VirtualButton("DOWN", 250, 850, 80, Color.DKGRAY, 120, Color.WHITE, "DOWN", true));
        buttons.add(new VirtualButton("LEFT", 100, 700, 80, Color.DKGRAY, 120, Color.WHITE, "LEFT", true));
        buttons.add(new VirtualButton("RIGHT", 400, 700, 80, Color.DKGRAY, 120, Color.WHITE, "RIGHT", true));
        
        buttons.add(new VirtualButton("X", 1600, 600, 90, Color.parseColor("#FFC107"), 150, Color.WHITE, "A", false));
        buttons.add(new VirtualButton("Y", 1800, 500, 90, Color.parseColor("#00BCD4"), 150, Color.WHITE, "S", false));
        buttons.add(new VirtualButton("Z", 2000, 400, 90, Color.parseColor("#9C27B0"), 150, Color.WHITE, "D", false));
        buttons.add(new VirtualButton("A", 1700, 800, 90, Color.parseColor("#F44336"), 150, Color.WHITE, "Z", false));
        buttons.add(new VirtualButton("B", 1900, 700, 90, Color.parseColor("#3F51B5"), 150, Color.WHITE, "X", false));
        buttons.add(new VirtualButton("C", 2100, 600, 90, Color.parseColor("#4CAF50"), 150, Color.WHITE, "C", false));
        
        buttons.add(new VirtualButton("START", 1100, 150, 60, Color.GRAY, 180, Color.WHITE, "RETURN", false));
    }

    // 【新增】导出到外部文件夹
    private void exportLayoutToFile() {
        try {
            File dir = getContext().getExternalFilesDir(null);
            if (dir == null) return;
            
            // 智能寻找递增的文件名: 布局1.json, 布局2.json...
            int index = 1;
            File exportFile;
            do {
                exportFile = new File(dir, "布局" + index + ".json");
                index++;
            } while (exportFile.exists());

            JSONObject root = new JSONObject();
            root.put("joystickMode", joystickMode);
            root.put("joyBaseX", joyBaseX); root.put("joyBaseY", joyBaseY);
            root.put("joyRadius", joyRadius); root.put("joyAlpha", joyAlpha);
            root.put("isVibrationOn", isVibrationOn);
            root.put("buttons", new JSONArray(prefs.getString(KEY_LAYOUT_PREFIX + currentSlot, "[]")));

            FileOutputStream fos = new FileOutputStream(exportFile);
            OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
            writer.write(root.toString(4));
            writer.close();
            fos.close();
            
            Toast.makeText(getContext(), "已导出至: " + exportFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "导出失败", Toast.LENGTH_SHORT).show();
        }
    }

    // =====================================
    // UI 面板渲染与系统弹窗
    // =====================================
    private void showMainMenu() {
        String modeText = isEditMode ? "💾 保存并退出编辑" : "🛠️ 开启按键拖拽编辑";
        String joyText = "🕹️ 摇杆形态: " + (joystickMode==0?"分离十字键":joystickMode==1?"现代白圆盘":"经典街机红杆");
        String vibText = "📳 物理震动: " + (isVibrationOn?"已开启":"已关闭");
        CharSequence[] options = {modeText, "➕ 新建组合键/宏按键", joyText, vibText, "📂 布局存档管理", "🔄 恢复初始默认布局"};

        new AlertDialog.Builder(getContext())
                .setTitle("⚙️ 游戏面板全局设置")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        isEditMode = !isEditMode;
                        if (!isEditMode) saveConfig();
                        invalidate();
                    } else if (which == 1) {
                        VirtualButton newBtn = new VirtualButton("新键", getWidth() / 2f, getHeight() / 2f, 90, Color.RED, 150, Color.WHITE, "Z+X", false);
                        buttons.add(newBtn);
                        isEditMode = true;
                        showButtonSettingsDialog(newBtn);
                    } else if (which == 2) {
                        joystickMode = (joystickMode + 1) % 3;
                        saveConfig();
                        invalidate();
                    } else if (which == 3) {
                        isVibrationOn = !isVibrationOn;
                        saveConfig();
                        Toast.makeText(getContext(), isVibrationOn ? "震动开启" : "震动关闭", Toast.LENGTH_SHORT).show();
                    } else if (which == 4) {
                        showProfileManager();
                    } else if (which == 5) {
                        loadDefaultLayout();
                        saveConfig();
                        invalidate();
                    }
                }).show();
    }

    private void showProfileManager() {
        CharSequence[] options = {"📂 读取 方案 1", "💾 覆盖保存至 方案 1", "📂 读取 方案 2", "💾 覆盖保存至 方案 2", "📤 导出当前布局到游戏目录"};
        new AlertDialog.Builder(getContext())
                .setTitle("布局方案存档与分享")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) loadConfig(1);
                    if (which == 1) { currentSlot = 1; saveConfig(); Toast.makeText(getContext(),"已存入方案1",Toast.LENGTH_SHORT).show();}
                    if (which == 2) loadConfig(2);
                    if (which == 3) { currentSlot = 2; saveConfig(); Toast.makeText(getContext(),"已存入方案2",Toast.LENGTH_SHORT).show();}
                    if (which == 4) { saveConfig(); exportLayoutToFile(); }
                }).show();
    }

    // 【新增】摇杆独立设置面板
    private void showJoystickSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("🕹️ 摇杆独立设置");

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 30, 60, 30);

        layout.addView(createTitle("外观与尺寸:"));
        final SeekBar alphaBar = createColorBar(layout, "摇杆不透明度 (0-255)", joyAlpha);
        final SeekBar sizeBar = createColorBar(layout, "摇杆整体大小", (int)joyRadius);
        sizeBar.setMax(400);

        builder.setView(layout);
        builder.setPositiveButton("💾 保存", (dialog, which) -> {
            joyAlpha = alphaBar.getProgress();
            joyRadius = Math.max(50, sizeBar.getProgress());
            saveConfig();
            invalidate();
        });
        builder.show();
    }

    // 解决输入框白底白字问题的构造器
    private EditText createEditText(String hint, String text) {
        EditText et = new EditText(getContext());
        et.setHint(hint);
        et.setText(text);
        et.setTextColor(Color.BLACK); // 强制黑色，防止瞎眼
        et.setHintTextColor(Color.GRAY);
        return et;
    }

    private void showButtonSettingsDialog(final VirtualButton btn) {
        currentlyEditingButton = btn;
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("🔧 配置按键: " + btn.id);

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 30, 60, 30);
        scroll.addView(layout);

        layout.addView(createTitle("1. 按键屏幕显示名称:"));
        final EditText inputName = createEditText("", btn.id);
        layout.addView(inputName);

        layout.addView(createTitle("2. 字体颜色:"));
        final Spinner textColorSpinner = new Spinner(getContext());
        ArrayAdapter<String> textAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, TEXT_COLOR_NAMES);
        textColorSpinner.setAdapter(textAdapter);
        for (int i=0; i<TEXT_COLOR_VALUES.length; i++) {
            if (btn.textColor == TEXT_COLOR_VALUES[i]) { textColorSpinner.setSelection(i); break; }
        }
        layout.addView(textColorSpinner);

        layout.addView(createTitle("3. 键盘键位映射 (多键用+连接):"));
        final EditText inputKey = createEditText("如: Z, UP, Z+X, ENTER", btn.keyMapStr);
        layout.addView(inputKey);

        layout.addView(createTitle("4. 按键背景色 (RGB色盘):"));
        final View colorPreview = new View(getContext());
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 150);
        previewParams.setMargins(0, 10, 0, 30);
        colorPreview.setLayoutParams(previewParams);
        colorPreview.setBackgroundColor(btn.color);
        layout.addView(colorPreview);

        final int[] rgb = {Color.red(btn.color), Color.green(btn.color), Color.blue(btn.color)};
        SeekBar redBar = createColorBar(layout, "红 (R)", rgb[0]);
        SeekBar greenBar = createColorBar(layout, "绿 (G)", rgb[1]);
        SeekBar blueBar = createColorBar(layout, "蓝 (B)", rgb[2]);

        SeekBar.OnSeekBarChangeListener colorUpdater = new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                rgb[0] = redBar.getProgress(); rgb[1] = greenBar.getProgress(); rgb[2] = blueBar.getProgress();
                colorPreview.setBackgroundColor(Color.rgb(rgb[0], rgb[1], rgb[2]));
            }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        };
        redBar.setOnSeekBarChangeListener(colorUpdater);
        greenBar.setOnSeekBarChangeListener(colorUpdater);
        blueBar.setOnSeekBarChangeListener(colorUpdater);

        layout.addView(createTitle("5. 外观与尺寸:"));
        final SeekBar alphaBar = createColorBar(layout, "不透明度 (0-255)", btn.alpha);
        final SeekBar sizeBar = createColorBar(layout, "按键大小", (int)btn.radius);
        sizeBar.setMax(300);

        layout.addView(createTitle("6. 自定义图片皮肤:"));
        Button btnPickImage = new Button(getContext());
        btnPickImage.setText("🖼️ 从系统相册选择图片");
        btnPickImage.setTextColor(Color.BLACK);
        btnPickImage.setOnClickListener(v -> {
            android.app.Activity activity = (android.app.Activity) getContext();
            ImagePickerFragment fragment = new ImagePickerFragment();
            activity.getFragmentManager().beginTransaction().add(fragment, "image_picker").commitAllowingStateLoss();
        });
        layout.addView(btnPickImage);

        Button btnClearImage = new Button(getContext());
        btnClearImage.setText("❌ 移除图片，恢复纯色");
        btnClearImage.setTextColor(Color.BLACK);
        btnClearImage.setOnClickListener(v -> {
            btn.customImageUri = "";
            btn.skinBitmap = null;
            Toast.makeText(getContext(), "已清除图片皮肤", Toast.LENGTH_SHORT).show();
            invalidate();
        });
        layout.addView(btnClearImage);

        builder.setView(scroll);
        builder.setPositiveButton("💾 保存", (dialog, which) -> {
            btn.id = inputName.getText().toString();
            btn.textColor = TEXT_COLOR_VALUES[textColorSpinner.getSelectedItemPosition()];
            btn.keyMapStr = inputKey.getText().toString().trim().toUpperCase();
            btn.parseKeyCodes();
            btn.color = Color.rgb(rgb[0], rgb[1], rgb[2]);
            btn.alpha = alphaBar.getProgress();
            btn.radius = Math.max(40, sizeBar.getProgress());
            // 修复：每次保存都强制重新缩放皮肤
            btn.loadSkinFromUri(getContext());
            saveConfig();
            invalidate();
        });
        builder.setNegativeButton("🗑️ 删除此键", (dialog, which) -> {
            buttons.remove(btn);
            saveConfig();
            invalidate();
        });
        builder.show();
    }

    private TextView createTitle(String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(16f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(Color.parseColor("#222222")); // 强制深色
        tv.setPadding(0, 40, 0, 10);
        return tv;
    }

    private SeekBar createColorBar(LinearLayout parent, String label, int progress) {
        TextView tv = new TextView(getContext());
        tv.setText(label);
        tv.setTextColor(Color.BLACK); // 强制黑色
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
    // 幽灵 Fragment (无侵入相册调用)
    // =====================================
    @SuppressWarnings("deprecation")
    public static class ImagePickerFragment extends android.app.Fragment {
        @Override
        public void onCreate(android.os.Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            startActivityForResult(intent, 43);
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == 43 && resultCode == android.app.Activity.RESULT_OK && data != null) {
                android.net.Uri uri = data.getData();
                try {
                    getActivity().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception e) { e.printStackTrace(); }
                
                if (DynamicGamepadView.instance != null) {
                    DynamicGamepadView.instance.onImagePicked(uri.toString());
                }
            }
            getFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
        }
    }
}
