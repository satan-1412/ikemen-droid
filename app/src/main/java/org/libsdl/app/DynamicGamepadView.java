package org.libsdl.app;

import android.app.AlertDialog;
import android.content.Context;
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
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DynamicGamepadView extends View {
    private static final String PREFS_NAME = "IkemenGamepad_Pro";
    private static final String KEY_LAYOUT_PREFIX = "LayoutSlot_";
    
    private int currentSlot = 0; // 当前使用的存档槽位(0为默认自动保存)

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

    public static class VirtualButton {
        public String id;
        public float cx, cy, radius;
        public int color, alpha;
        public String keyMapStr = ""; // 保存用户输入的字符串，如 "A", "Z+X", "UP"
        public List<Integer> keyCodes = new ArrayList<>(); // 解析后的底层组合键
        public boolean isPressed = false;
        public String skinName = "";
        public Bitmap skinBitmap = null;

        public VirtualButton(String id, float cx, float cy, float radius, int color, int alpha, String keyMapStr) {
            this.id = id; this.cx = cx; this.cy = cy;
            this.radius = radius; this.color = color;
            this.alpha = alpha; this.keyMapStr = keyMapStr;
            parseKeyCodes();
        }

        // 核心：支持 "+" 号组合键解析
        public void parseKeyCodes() {
            keyCodes.clear();
            if (keyMapStr == null || keyMapStr.isEmpty()) return;
            String[] parts = keyMapStr.toUpperCase().split("\\+");
            for (String p : parts) {
                int code = mapStringToKeyCode(p.trim());
                if (code != KeyEvent.KEYCODE_UNKNOWN) {
                    keyCodes.add(code);
                }
            }
        }

        public void loadSkin(Context context) {
            if (skinName != null && !skinName.isEmpty()) {
                try {
                    File file = new File(context.getExternalFilesDir(null), skinName);
                    if (file.exists()) {
                        Bitmap raw = BitmapFactory.decodeFile(file.getAbsolutePath());
                        skinBitmap = Bitmap.createScaledBitmap(raw, (int)(radius*2), (int)(radius*2), true);
                    } else { skinBitmap = null; }
                } catch (Exception e) { skinBitmap = null; }
            } else { skinBitmap = null; }
        }
    }

    // 将用户输入的字母或单词映射到安卓系统按键
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
        paintText.setColor(Color.WHITE);
        paintText.setTextAlign(Paint.Align.CENTER);
        paintText.setTypeface(Typeface.DEFAULT_BOLD);
        loadConfig(currentSlot);
    }

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
                            obj.getInt("color"), obj.getInt("alpha"), obj.getString("keyMapStr")
                    );
                    if (obj.has("skinName")) {
                        btn.skinName = obj.getString("skinName");
                        btn.loadSkin(getContext());
                    }
                    buttons.add(btn);
                }
                invalidate();
                return;
            } catch (Exception e) { e.printStackTrace(); }
        }
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
                obj.put("keyMapStr", b.keyMapStr);
                obj.put("skinName", b.skinName);
                array.put(obj);
            }
            prefs.edit().putString(KEY_LAYOUT_PREFIX + currentSlot, array.toString()).apply();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadDefaultLayout() {
        buttons.clear();
        // 严格按照 config.ini 中 P1 的按键进行默认映射
        buttons.add(new VirtualButton("UP", 250, 550, 80, Color.DKGRAY, 120, "UP"));
        buttons.add(new VirtualButton("DOWN", 250, 850, 80, Color.DKGRAY, 120, "DOWN"));
        buttons.add(new VirtualButton("LEFT", 100, 700, 80, Color.DKGRAY, 120, "LEFT"));
        buttons.add(new VirtualButton("RIGHT", 400, 700, 80, Color.DKGRAY, 120, "RIGHT"));
        
        buttons.add(new VirtualButton("X", 1600, 600, 90, Color.parseColor("#FFC107"), 150, "A"));
        buttons.add(new VirtualButton("Y", 1800, 500, 90, Color.parseColor("#00BCD4"), 150, "S"));
        buttons.add(new VirtualButton("Z", 2000, 400, 90, Color.parseColor("#9C27B0"), 150, "D"));
        buttons.add(new VirtualButton("A", 1700, 800, 90, Color.parseColor("#F44336"), 150, "Z"));
        buttons.add(new VirtualButton("B", 1900, 700, 90, Color.parseColor("#3F51B5"), 150, "X"));
        buttons.add(new VirtualButton("C", 2100, 600, 90, Color.parseColor("#4CAF50"), 150, "C"));
        
        buttons.add(new VirtualButton("START", 1100, 150, 60, Color.GRAY, 180, "RETURN"));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        paintMenu.setColor(Color.argb(220, 40, 40, 40));
        paintMenu.setShadowLayer(8f, 0, 4f, Color.BLACK);
        canvas.drawRoundRect(menuButtonRect, 25, 25, paintMenu);
        paintText.setTextSize(45f);
        paintMenu.clearShadowLayer();
        canvas.drawText("⚙ 面板设置", menuButtonRect.centerX(), menuButtonRect.centerY() + 15, paintText);

        if (isEditMode) {
            canvas.drawColor(Color.argb(100, 0, 0, 0));
            paintText.setTextSize(50f);
            canvas.drawText("【编辑模式】拖拽调整位置，轻触按键呼出高级设置", getWidth() / 2f, 150, paintText);
        }

        for (VirtualButton btn : buttons) {
            int currentAlpha = (btn.isPressed && !isEditMode) ? 255 : btn.alpha;
            paintBtn.setAlpha(currentAlpha);
            
            if (btn.skinBitmap != null) {
                canvas.drawBitmap(btn.skinBitmap, btn.cx - btn.radius, btn.cy - btn.radius, paintBtn);
            } else {
                int darkerColor = darkenColor(btn.color, 0.5f);
                RadialGradient gradient = new RadialGradient(
                        btn.cx - btn.radius * 0.3f, btn.cy - btn.radius * 0.3f, 
                        btn.radius * 1.3f, 
                        btn.color, darkerColor, Shader.TileMode.CLAMP);
                paintBtn.setShader(gradient);
                paintBtn.setShadowLayer(15.0f, 0.0f, 10.0f, Color.argb(180, 0, 0, 0));
                canvas.drawCircle(btn.cx, btn.cy, btn.radius, paintBtn);
                paintBtn.clearShadowLayer();
                paintBtn.setShader(null);
            }

            paintText.setAlpha(currentAlpha);
            paintText.setTextSize(btn.radius * 0.55f);
            canvas.drawText(btn.id, btn.cx, btn.cy + (btn.radius * 0.2f), paintText);

            if (isEditMode) {
                paintBtn.setStyle(Paint.Style.STROKE);
                paintBtn.setStrokeWidth(5f);
                paintBtn.setColor(Color.WHITE);
                paintBtn.setAlpha(255);
                canvas.drawCircle(btn.cx, btn.cy, btn.radius + 8, paintBtn);
                paintBtn.setStyle(Paint.Style.FILL);
            }
        }
    }

    private int darkenColor(int color, float factor) {
        int a = Color.alpha(color);
        int r = Math.max((int)(Color.red(color) * factor), 0);
        int g = Math.max((int)(Color.green(color) * factor), 0);
        int b = Math.max((int)(Color.blue(color) * factor), 0);
        return Color.argb(a, r, g, b);
    }

    private void showMainMenu() {
        String modeText = isEditMode ? "💾 保存并退出编辑" : "🛠️ 开启按键编辑模式";
        CharSequence[] options = {modeText, "➕ 新建组合键/宏按键", "📂 布局方案管理", "🔄 恢复默认原始布局"};

        new AlertDialog.Builder(getContext())
                .setTitle("虚拟面板全局设置")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        isEditMode = !isEditMode;
                        if (!isEditMode) saveConfig();
                        invalidate();
                    } else if (which == 1) {
                        VirtualButton newBtn = new VirtualButton("大招", getWidth() / 2f, getHeight() / 2f, 100, Color.RED, 180, "Z+X");
                        buttons.add(newBtn);
                        isEditMode = true;
                        showButtonSettingsDialog(newBtn);
                    } else if (which == 2) {
                        showProfileManager();
                    } else if (which == 3) {
                        loadDefaultLayout();
                        saveConfig();
                        invalidate();
                    }
                }).show();
    }

    private void showProfileManager() {
        CharSequence[] options = {"读取 方案 1", "保存至 方案 1", "读取 方案 2", "保存至 方案 2"};
        new AlertDialog.Builder(getContext())
                .setTitle("布局方案管理")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) loadConfig(1);
                    if (which == 1) { currentSlot = 1; saveConfig(); }
                    if (which == 2) loadConfig(2);
                    if (which == 3) { currentSlot = 2; saveConfig(); }
                }).show();
    }

    // 高级单键设置面板（带取色器）
    private void showButtonSettingsDialog(final VirtualButton btn) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("按键独立设置");

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 30, 60, 30);
        scroll.addView(layout);

        // 1. 名字
        layout.addView(createTitle("按键显示名称:"));
        final EditText inputName = new EditText(getContext());
        inputName.setText(btn.id);
        layout.addView(inputName);

        // 2. 宏绑定
        layout.addView(createTitle("按键映射 (组合键用 + 隔开):"));
        final EditText inputKey = new EditText(getContext());
        inputKey.setHint("如: UP, Z, Z+X, ENTER");
        inputKey.setText(btn.keyMapStr);
        layout.addView(inputKey);

        // 3. 颜色调节 (RGB)
        layout.addView(createTitle("自定义颜色 (RGB):"));
        final View colorPreview = new View(getContext());
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120);
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
            public void onStartTrackingTouch(SeekBar seekBar) {} public void onStopTrackingTouch(SeekBar seekBar) {}
        };
        redBar.setOnSeekBarChangeListener(colorUpdater);
        greenBar.setOnSeekBarChangeListener(colorUpdater);
        blueBar.setOnSeekBarChangeListener(colorUpdater);

        // 4. 透明度与大小
        layout.addView(createTitle("外观与尺寸:"));
        final SeekBar alphaBar = createColorBar(layout, "不透明度 (0-255)", btn.alpha);
        final SeekBar sizeBar = createColorBar(layout, "按键大小", (int)btn.radius);
        sizeBar.setMax(300);

        // 5. 皮肤
        layout.addView(createTitle("自定义皮肤文件 (放于应用目录下):"));
        final EditText inputSkin = new EditText(getContext());
        inputSkin.setHint("如: btn_punch.png (留空恢复纯色)");
        inputSkin.setText(btn.skinName);
        layout.addView(inputSkin);

        builder.setView(scroll);
        builder.setPositiveButton("保存设置", (dialog, which) -> {
            btn.id = inputName.getText().toString();
            btn.keyMapStr = inputKey.getText().toString().trim().toUpperCase();
            btn.parseKeyCodes();
            btn.color = Color.rgb(rgb[0], rgb[1], rgb[2]);
            btn.alpha = alphaBar.getProgress();
            btn.radius = Math.max(40, sizeBar.getProgress());
            btn.skinName = inputSkin.getText().toString().trim();
            btn.loadSkin(getContext());
            saveConfig();
            invalidate();
        });
        builder.setNegativeButton("删除此按键", (dialog, which) -> {
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
        tv.setTextColor(Color.parseColor("#333333"));
        tv.setPadding(0, 30, 0, 10);
        return tv;
    }

    private SeekBar createColorBar(LinearLayout parent, String label, int progress) {
        TextView tv = new TextView(getContext());
        tv.setText(label);
        tv.setPadding(0, 10, 0, 0);
        SeekBar sb = new SeekBar(getContext());
        sb.setMax(255);
        sb.setProgress(progress);
        sb.setPadding(0, 10, 0, 20);
        parent.addView(tv);
        parent.addView(sb);
        return sb;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int pointerIndex = event.getActionIndex();
        float x = event.getX(pointerIndex);
        float y = event.getY(pointerIndex);

        if (action == MotionEvent.ACTION_DOWN && menuButtonRect.contains(x, y)) {
            showMainMenu();
            return true;
        }

        if (isEditMode) {
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    downTime = System.currentTimeMillis();
                    downX = x; downY = y;
                    for (int i = buttons.size() - 1; i >= 0; i--) {
                        if (Math.hypot(x - buttons.get(i).cx, y - buttons.get(i).cy) < buttons.get(i).radius * 1.3f) {
                            draggedButton = buttons.get(i);
                            break;
                        }
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (draggedButton != null) {
                        draggedButton.cx = x; draggedButton.cy = y;
                        invalidate();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (draggedButton != null && System.currentTimeMillis() - downTime < 250 && Math.hypot(x - downX, y - downY) < 20) {
                        showButtonSettingsDialog(draggedButton);
                    }
                    draggedButton = null;
                    break;
            }
            return true;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                for (VirtualButton btn : buttons) {
                    if (!btn.isPressed && Math.hypot(x - btn.cx, y - btn.cy) < btn.radius) {
                        btn.isPressed = true;
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        for (int code : btn.keyCodes) SDLActivity.onNativeKeyDown(code);
                        invalidate();
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                for (VirtualButton btn : buttons) {
                    if (btn.isPressed && Math.hypot(x - btn.cx, y - btn.cy) < btn.radius) {
                        btn.isPressed = false;
                        for (int code : btn.keyCodes) SDLActivity.onNativeKeyUp(code);
                        invalidate();
                    }
                }
                break;
        }
        return true;
    }
}
