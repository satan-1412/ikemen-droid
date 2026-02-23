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

    private final List<VirtualButton> buttons = new ArrayList<>();
    private final Paint paintBtn = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintMenu = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tempRect = new RectF(); // 用于绘制圆角矩形

    private final SharedPreferences prefs;
    public boolean isEditMode = false;
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

        paintMenu.setColor(Color.argb(220, 20, 20, 25));
        paintMenu.setShadowLayer(8f, 0, 4f, Color.BLACK);
        canvas.drawRoundRect(menuButtonRect, 20, 20, paintMenu);
        paintText.setColor(Color.WHITE);
        paintText.setTextSize(38f);
        paintText.setShadowLayer(0,0,0,Color.TRANSPARENT);
        paintMenu.clearShadowLayer();
        canvas.drawText("⚙ 高级设置", menuButtonRect.centerX(), menuButtonRect.centerY() + 12, paintText);

        if (isEditMode) {
            canvas.drawColor(Color.argb(100, 255, 50, 50));
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
            // 增加文字描边，防止在浅色背景下看不清
            paintText.setShadowLayer(3f, 1f, 1f, (btn.textColor == Color.BLACK) ? Color.WHITE : Color.BLACK);
            canvas.drawText(btn.id, btn.cx, btn.cy + (btn.radius * 0.22f), paintText);
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

        if (action == MotionEvent.ACTION_DOWN && menuButtonRect.contains(event.getX(), event.getY())) {
            showMainMenu();
            return true;
        }

        if (isEditMode) { handleEditTouch(event); return true; }

        if (joystickMode > 0) {
            boolean joyTouched = false;
            for (int i = 0; i < event.getPointerCount(); i++) {
                float px = event.getX(i), py = event.getY(i);
                if (px < getWidth() / 2f) {
                    if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                        if (Math.hypot(px - joyBaseX, py - joyBaseY) < joyRadius * 2.5f) joyPointerId = event.getPointerId(i);
                    }
                    if (event.getPointerId(i) == joyPointerId) {
                        joyTouched = true;
                        float dx = px - joyBaseX, dy = py - joyBaseY;
                        float dist = (float) Math.hypot(dx, dy);
                        if (dist > joyRadius) { joyKnobX = joyBaseX + (dx / dist) * joyRadius; joyKnobY = joyBaseY + (dy / dist) * joyRadius; } 
                        else { joyKnobX = px; joyKnobY = py; }
                        
                        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
                        if (angle < 0) angle += 360;
                        boolean up = angle > 200 && angle < 340, down = angle > 20 && angle < 160;
                        boolean left = angle > 110 && angle < 250, right = angle < 70 || angle > 290;
                        if (dist < joyRadius * 0.3f) up = down = left = right = false;
                        triggerDirection("UP", up); triggerDirection("DOWN", down); triggerDirection("LEFT", left); triggerDirection("RIGHT", right);
                    }
                }
            }
            if (!joyTouched || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                joyPointerId = -1; joyKnobX = joyBaseX; joyKnobY = joyBaseY;
                triggerDirection("UP", false); triggerDirection("DOWN", false); triggerDirection("LEFT", false); triggerDirection("RIGHT", false);
            }
        }

        for (VirtualButton btn : buttons) {
            if (joystickMode > 0 && btn.isDirectional) continue;
            boolean isTouchedNow = false;
            for (int i = 0; i < event.getPointerCount(); i++) {
                if (event.getPointerId(i) == joyPointerId) continue;
                // 形状碰撞检测
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
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                downTime = System.currentTimeMillis(); downX = x; downY = y;
                isDraggingJoy = false; draggedButton = null;
                if (joystickMode > 0 && Math.hypot(x - joyBaseX, y - joyBaseY) < joyRadius) { isDraggingJoy = true; } 
                else {
                    for (int i = buttons.size() - 1; i >= 0; i--) {
                        if (Math.hypot(x - buttons.get(i).cx, y - buttons.get(i).cy) < buttons.get(i).radius * 1.3f) {
                            draggedButton = buttons.get(i); break;
                        }
                    }
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (isDraggingJoy) { joyBaseX = x; joyBaseY = y; joyKnobX = x; joyKnobY = y; invalidate(); } 
                else if (draggedButton != null) { draggedButton.cx = x; draggedButton.cy = y; invalidate(); }
                break;
            case MotionEvent.ACTION_UP:
                if (System.currentTimeMillis() - downTime < 250 && Math.hypot(x - downX, y - downY) < 20) {
                    if (isDraggingJoy) showJoystickSettingsDialog(); else if (draggedButton != null) showButtonSettingsDialog(draggedButton);
                }
                isDraggingJoy = false; draggedButton = null;
                break;
        }
    }
    // =====================================
    // 存档、导入导出与序列化逻辑 (包含二次确认)
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
                            obj.optInt("textColor", Color.WHITE), 
                            obj.optInt("shape", SHAPE_CIRCLE), // 读取形状，兼容旧存档默认圆形
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
                obj.put("shape", b.shape); // 保存形状
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
        buttons.add(new VirtualButton("UP", 250, 550, 80, Color.DKGRAY, 120, Color.WHITE, SHAPE_CIRCLE, "UP", true));
        buttons.add(new VirtualButton("DOWN", 250, 850, 80, Color.DKGRAY, 120, Color.WHITE, SHAPE_CIRCLE, "DOWN", true));
        buttons.add(new VirtualButton("LEFT", 100, 700, 80, Color.DKGRAY, 120, Color.WHITE, SHAPE_CIRCLE, "LEFT", true));
        buttons.add(new VirtualButton("RIGHT", 400, 700, 80, Color.DKGRAY, 120, Color.WHITE, SHAPE_CIRCLE, "RIGHT", true));
        
        buttons.add(new VirtualButton("X", 1600, 600, 90, Color.parseColor("#FFC107"), 150, Color.WHITE, SHAPE_CIRCLE, "A", false));
        buttons.add(new VirtualButton("Y", 1800, 500, 90, Color.parseColor("#00BCD4"), 150, Color.WHITE, SHAPE_CIRCLE, "S", false));
        buttons.add(new VirtualButton("Z", 2000, 400, 90, Color.parseColor("#9C27B0"), 150, Color.WHITE, SHAPE_CIRCLE, "D", false));
        buttons.add(new VirtualButton("A", 1700, 800, 90, Color.parseColor("#F44336"), 150, Color.WHITE, SHAPE_CIRCLE, "Z", false));
        buttons.add(new VirtualButton("B", 1900, 700, 90, Color.parseColor("#3F51B5"), 150, Color.WHITE, SHAPE_CIRCLE, "X", false));
        buttons.add(new VirtualButton("C", 2100, 600, 90, Color.parseColor("#4CAF50"), 150, Color.WHITE, SHAPE_CIRCLE, "C", false));
        
        buttons.add(new VirtualButton("START", 1100, 150, 60, Color.GRAY, 180, Color.WHITE, SHAPE_CIRCLE, "RETURN", false));
    }

    // 【新增】带二次确认的导出功能
    private void exportLayoutToFile() {
        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("📤 导出确认")
            .setMessage("确定要将当前布局导出为 JSON 文件吗？\n文件将保存在游戏数据目录的 [布局] 文件夹下，方便分享给其他玩家。")
            .setPositiveButton("确定导出", (dialog, which) -> {
                try {
                    File dir = new File(getContext().getExternalFilesDir(null), "布局");
                    if (!dir.exists()) dir.mkdirs();
                    
                    int index = 1;
                    File exportFile;
                    do {
                        exportFile = new File(dir, "布局方案_" + index + ".json");
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
                    writer.close(); fos.close();
                    
                    Toast.makeText(getContext(), "✅ 成功导出至:\n" + exportFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(getContext(), "❌ 导出失败", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    // 【新增】一键导入外部布局功能
    private void importLayoutFromFile() {
        File dir = new File(getContext().getExternalFilesDir(null), "布局");
        if (!dir.exists() || dir.listFiles() == null || dir.listFiles().length == 0) {
            Toast.makeText(getContext(), "❌ 未在游戏目录找到 [布局] 文件夹或里面没有 JSON 文件", Toast.LENGTH_LONG).show();
            return;
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            Toast.makeText(getContext(), "❌ [布局] 文件夹内没有合法的 .json 文件", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] fileNames = new String[files.length];
        for (int i = 0; i < files.length; i++) fileNames[i] = files[i].getName();

        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("📥 选择要导入的布局方案")
            .setItems(fileNames, (dialog, which) -> {
                File selectedFile = files[which];
                // 二次确认覆盖
                new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("⚠️ 覆盖警告")
                    .setMessage("导入操作将【永久覆盖】你当前正在使用的布局，确定要导入 [" + selectedFile.getName() + "] 吗？")
                    .setPositiveButton("确定覆盖", (d, w) -> {
                        try {
                            FileInputStream fis = new FileInputStream(selectedFile);
                            InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
                            BufferedReader reader = new BufferedReader(isr);
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) sb.append(line);
                            reader.close(); isr.close(); fis.close();

                            JSONObject root = new JSONObject(sb.toString());
                            joystickMode = root.optInt("joystickMode", 0);
                            joyBaseX = (float) root.optDouble("joyBaseX", 250);
                            joyBaseY = (float) root.optDouble("joyBaseY", 700);
                            joyRadius = (float) root.optDouble("joyRadius", 180);
                            joyAlpha = root.optInt("joyAlpha", 200);
                            isVibrationOn = root.optBoolean("isVibrationOn", true);
                            joyKnobX = joyBaseX; joyKnobY = joyBaseY;

                            JSONArray btnArray = root.getJSONArray("buttons");
                            prefs.edit().putString(KEY_LAYOUT_PREFIX + currentSlot, btnArray.toString()).apply();
                            
                            loadConfig(currentSlot); // 重新加载上屏
                            Toast.makeText(getContext(), "✅ 导入成功！", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            e.printStackTrace();
                            Toast.makeText(getContext(), "❌ 导入失败，文件可能已损坏", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            })
            .show();
    }

    // =====================================
    // UI 面板渲染与系统弹窗
    // =====================================
    private void showMainMenu() {
        String modeText = isEditMode ? "💾 保存并退出编辑" : "🛠️ 开启按键拖拽编辑";
        String joyText = "🕹️ 摇杆形态: " + (joystickMode==0?"分离十字键":joystickMode==1?"现代白圆盘":"经典街机红杆");
        String vibText = "📳 物理震动: " + (isVibrationOn?"已开启":"已关闭");
        CharSequence[] options = {modeText, "➕ 新建组合键/宏", joyText, vibText, "📂 布局存档管理 / 导入导出", "🔄 恢复初始默认布局"};

        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("⚙️ 游戏面板全局设置")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        isEditMode = !isEditMode;
                        if (!isEditMode) saveConfig();
                        invalidate();
                    } else if (which == 1) {
                        VirtualButton newBtn = new VirtualButton("新键", getWidth() / 2f, getHeight() / 2f, 90, Color.RED, 150, Color.WHITE, SHAPE_CIRCLE, "Z+X", false);
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
                        // 恢复默认的防手滑确认
                        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setTitle("⚠️ 警告")
                            .setMessage("确定要清空所有自定义修改，恢复为原版默认按键布局吗？")
                            .setPositiveButton("确定恢复", (d, w) -> {
                                loadDefaultLayout();
                                saveConfig();
                                invalidate();
                            })
                            .setNegativeButton("取消", null).show();
                    }
                }).show();
    }

    private void showProfileManager() {
        CharSequence[] options = {"📂 读取云端方案 1", "💾 覆盖保存至方案 1", "📂 读取云端方案 2", "💾 覆盖保存至方案 2", "📤 导出布局到本地文件夹", "📥 从本地文件夹导入布局"};
        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("布局方案存档与分享")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setTitle("⚠️ 读取确认").setMessage("确定读取方案 1？当前未保存的修改将会丢失。")
                            .setPositiveButton("确定", (d, w) -> loadConfig(1)).setNegativeButton("取消", null).show();
                    }
                    if (which == 1) {
                        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setTitle("⚠️ 覆盖确认").setMessage("确定将当前的按键排布覆盖保存至方案 1？")
                            .setPositiveButton("确定", (d, w) -> { currentSlot = 1; saveConfig(); Toast.makeText(getContext(),"✅ 已存入方案1",Toast.LENGTH_SHORT).show();}).setNegativeButton("取消", null).show();
                    }
                    if (which == 2) {
                        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setTitle("⚠️ 读取确认").setMessage("确定读取方案 2？当前未保存的修改将会丢失。")
                            .setPositiveButton("确定", (d, w) -> loadConfig(2)).setNegativeButton("取消", null).show();
                    }
                    if (which == 3) {
                        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setTitle("⚠️ 覆盖确认").setMessage("确定将当前的按键排布覆盖保存至方案 2？")
                            .setPositiveButton("确定", (d, w) -> { currentSlot = 2; saveConfig(); Toast.makeText(getContext(),"✅ 已存入方案2",Toast.LENGTH_SHORT).show();}).setNegativeButton("取消", null).show();
                    }
                    if (which == 4) { saveConfig(); exportLayoutToFile(); }
                    if (which == 5) { importLayoutFromFile(); }
                }).show();
    }

    private void showJoystickSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert);
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

    // 针对暗黑模式优化的文本输入框（白字）
    private EditText createEditText(String hint, String text) {
        EditText et = new EditText(getContext());
        et.setHint(hint);
        et.setText(text);
        et.setTextColor(Color.WHITE); // 确保在暗黑弹窗下文字可见
        et.setHintTextColor(Color.GRAY);
        return et;
    }

    private void showButtonSettingsDialog(final VirtualButton btn) {
        currentlyEditingButton = btn;
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle("🔧 配置按键: " + btn.id);

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 30, 60, 30);
        scroll.addView(layout);

        layout.addView(createTitle("1. 按键屏幕显示名称:"));
        final EditText inputName = createEditText("", btn.id);
        layout.addView(inputName);

        layout.addView(createTitle("2. 按键字体颜色:"));
        final Spinner textColorSpinner = new Spinner(getContext());
        ArrayAdapter<String> textAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, TEXT_COLOR_NAMES);
        textColorSpinner.setAdapter(textAdapter);
        for (int i=0; i<TEXT_COLOR_VALUES.length; i++) {
            if (btn.textColor == TEXT_COLOR_VALUES[i]) { textColorSpinner.setSelection(i); break; }
        }
        layout.addView(textColorSpinner);

        // 【新增】按键形状选择
        layout.addView(createTitle("3. 按键物理形状:"));
        final Spinner shapeSpinner = new Spinner(getContext());
        ArrayAdapter<String> shapeAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, SHAPE_NAMES);
        shapeSpinner.setAdapter(shapeAdapter);
        shapeSpinner.setSelection(btn.shape);
        layout.addView(shapeSpinner);

        layout.addView(createTitle("4. 键盘键位映射 (多键用+连接):"));
        final EditText inputKey = createEditText("如: Z, UP, Z+X, ENTER", btn.keyMapStr);
        layout.addView(inputKey);

        layout.addView(createTitle("5. 按键背景色 (RGB色盘):"));
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

        layout.addView(createTitle("6. 外观与尺寸:"));
        final SeekBar alphaBar = createColorBar(layout, "不透明度 (0-255)", btn.alpha);
        final SeekBar sizeBar = createColorBar(layout, "按键大小", (int)btn.radius);
        sizeBar.setMax(300);

        layout.addView(createTitle("7. 自定义图片皮肤:"));
        Button btnPickImage = new Button(getContext());
        btnPickImage.setText("🖼️ 从系统相册选择图片");
        btnPickImage.setTextColor(Color.WHITE); // 暗黑模式下白字
        btnPickImage.setOnClickListener(v -> {
            android.app.Activity activity = (android.app.Activity) getContext();
            ImagePickerFragment fragment = new ImagePickerFragment();
            activity.getFragmentManager().beginTransaction().add(fragment, "image_picker").commitAllowingStateLoss();
        });
        layout.addView(btnPickImage);

        Button btnClearImage = new Button(getContext());
        btnClearImage.setText("❌ 移除图片，恢复纯色/形状");
        btnClearImage.setTextColor(Color.WHITE);
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
            btn.shape = shapeSpinner.getSelectedItemPosition();
            btn.keyMapStr = inputKey.getText().toString().trim().toUpperCase();
            btn.parseKeyCodes();
            btn.color = Color.rgb(rgb[0], rgb[1], rgb[2]);
            btn.alpha = alphaBar.getProgress();
            btn.radius = Math.max(40, sizeBar.getProgress());
            btn.loadSkinFromUri(getContext());
            saveConfig();
            invalidate();
        });
        builder.setNegativeButton("🗑️ 删除此键", (dialog, which) -> {
            // 删除时也加上二次确认
            new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("⚠️ 确认删除")
                .setMessage("确定要彻底删除按键 [" + btn.id + "] 吗？此操作不可逆。")
                .setPositiveButton("确定删除", (d, w) -> {
                    buttons.remove(btn);
                    saveConfig();
                    invalidate();
                })
                .setNegativeButton("取消", null).show();
        });
        builder.show();
    }

    // 针对暗黑模式和高级审美的 UI 组件
    private TextView createTitle(String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(16f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(Color.WHITE); // 修复小字体黑字看不清的问题
        tv.setPadding(0, 40, 0, 10);
        return tv;
    }

    private SeekBar createColorBar(LinearLayout parent, String label, int progress) {
        TextView tv = new TextView(getContext());
        tv.setText(label);
        tv.setTextColor(Color.WHITE); // 修复滑块上方字体看不清的问题
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
                    // 获取持久化读取权限，防止游戏重启后图片丢失
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
