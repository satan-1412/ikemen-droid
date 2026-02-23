package org.libsdl.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DynamicGamepadView extends View {
    private static final String PREFS_NAME = "IkemenGamepadPrefs_V2";
    private static final String KEY_LAYOUT = "ButtonLayoutData";

    private final List<VirtualButton> buttons = new ArrayList<>();
    private final Paint paintBtn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintMenu = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final SharedPreferences prefs;
    public boolean isEditMode = false;
    private VirtualButton draggedButton = null;

    private long downTime;
    private float downX, downY;

    private final RectF menuButtonRect = new RectF(20, 20, 220, 100);

    // 预设颜色表
    private final String[] colorNames = {"灰色 (Gray)", "红色 (Red)", "蓝色 (Blue)", "绿色 (Green)", "黄色 (Yellow)", "青色 (Cyan)", "紫色 (Magenta)", "黑色 (Black)"};
    private final int[] colorValues = {Color.DKGRAY, Color.RED, Color.BLUE, Color.parseColor("#4CAF50"), Color.parseColor("#FFEB3B"), Color.CYAN, Color.MAGENTA, Color.BLACK};

    public static class VirtualButton {
        public String id;
        public float cx, cy, radius;
        public int color, alpha, sdlKeyCode;
        public boolean isPressed = false;
        public String skinName = ""; // 自定义皮肤图片的文件名
        public Bitmap skinBitmap = null;

        public VirtualButton(String id, float cx, float cy, float radius, int color, int alpha, int sdlKeyCode) {
            this.id = id; this.cx = cx; this.cy = cy;
            this.radius = radius; this.color = color;
            this.alpha = alpha; this.sdlKeyCode = sdlKeyCode;
        }

        public void loadSkin(Context context) {
            if (skinName != null && !skinName.isEmpty()) {
                try {
                    // 默认去手机的 Android/data/包名/files/ 或者 SD卡的根目录寻找
                    File file = new File(context.getExternalFilesDir(null), skinName);
                    if (file.exists()) {
                        Bitmap raw = BitmapFactory.decodeFile(file.getAbsolutePath());
                        skinBitmap = Bitmap.createScaledBitmap(raw, (int)(radius*2), (int)(radius*2), true);
                    }
                } catch (Exception e) {
                    skinBitmap = null;
                }
            } else {
                skinBitmap = null;
            }
        }
    }

    public DynamicGamepadView(Context context) {
        super(context);
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // 开启硬件加速下的阴影支持
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        paintText.setColor(Color.WHITE);
        paintText.setTextAlign(Paint.Align.CENTER);
        loadConfig();
    }

    private void loadConfig() {
        String jsonStr = prefs.getString(KEY_LAYOUT, null);
        buttons.clear();
        if (jsonStr != null) {
            try {
                JSONArray array = new JSONArray(jsonStr);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    VirtualButton btn = new VirtualButton(
                            obj.getString("id"),
                            (float) obj.getDouble("cx"), (float) obj.getDouble("cy"), (float) obj.getDouble("radius"),
                            obj.getInt("color"), obj.getInt("alpha"), obj.getInt("sdlKeyCode")
                    );
                    if (obj.has("skinName")) {
                        btn.skinName = obj.getString("skinName");
                        btn.loadSkin(getContext());
                    }
                    buttons.add(btn);
                }
                return;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        loadDefaultLayout();
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
                obj.put("sdlKeyCode", b.sdlKeyCode);
                obj.put("skinName", b.skinName);
                array.put(obj);
            }
            prefs.edit().putString(KEY_LAYOUT, array.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadDefaultLayout() {
        // 匹配 XML 的默认按键数量与基础功能
        buttons.add(new VirtualButton("UP", 250, 550, 80, Color.DKGRAY, 120, KeyEvent.KEYCODE_W));
        buttons.add(new VirtualButton("DOWN", 250, 850, 80, Color.DKGRAY, 120, KeyEvent.KEYCODE_S));
        buttons.add(new VirtualButton("LEFT", 100, 700, 80, Color.DKGRAY, 120, KeyEvent.KEYCODE_A));
        buttons.add(new VirtualButton("RIGHT", 400, 700, 80, Color.DKGRAY, 120, KeyEvent.KEYCODE_D));
        
        buttons.add(new VirtualButton("X", 1600, 600, 90, Color.parseColor("#FFC107"), 150, KeyEvent.KEYCODE_U));
        buttons.add(new VirtualButton("Y", 1800, 500, 90, Color.parseColor("#00BCD4"), 150, KeyEvent.KEYCODE_I));
        buttons.add(new VirtualButton("Z", 2000, 400, 90, Color.parseColor("#9C27B0"), 150, KeyEvent.KEYCODE_O));
        buttons.add(new VirtualButton("A", 1700, 800, 90, Color.parseColor("#F44336"), 150, KeyEvent.KEYCODE_J));
        buttons.add(new VirtualButton("B", 1900, 700, 90, Color.parseColor("#3F51B5"), 150, KeyEvent.KEYCODE_K));
        buttons.add(new VirtualButton("C", 2100, 600, 90, Color.parseColor("#4CAF50"), 150, KeyEvent.KEYCODE_L));
        
        buttons.add(new VirtualButton("START", 1100, 150, 60, Color.GRAY, 180, KeyEvent.KEYCODE_ENTER));
        buttons.add(new VirtualButton("MENU", 1250, 150, 60, Color.GRAY, 180, KeyEvent.KEYCODE_ESCAPE));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 左上角设置按钮
        paintMenu.setColor(Color.argb(200, 30, 30, 30));
        paintMenu.setShadowLayer(10f, 0, 5f, Color.BLACK);
        canvas.drawRoundRect(menuButtonRect, 20, 20, paintMenu);
        paintText.setTextSize(40f);
        paintMenu.clearShadowLayer();
        canvas.drawText("⚙ 设置", menuButtonRect.centerX(), menuButtonRect.centerY() + 15, paintText);

        if (isEditMode) {
            canvas.drawColor(Color.argb(90, 255, 0, 0));
            paintText.setTextSize(50f);
            canvas.drawText("编辑模式：拖动调整位置，【轻触按键】可独立设置外观与键值", getWidth() / 2f, 150, paintText);
        }

        // 绘制按键
        for (VirtualButton btn : buttons) {
            int currentAlpha = (btn.isPressed && !isEditMode) ? 255 : btn.alpha;
            
            if (btn.skinBitmap != null) {
                // 画皮肤
                paintBtn.setAlpha(currentAlpha);
                canvas.drawBitmap(btn.skinBitmap, btn.cx - btn.radius, btn.cy - btn.radius, paintBtn);
            } else {
                // 画高质感渐变圆
                paintBtn.setAlpha(currentAlpha);
                int darkerColor = darkenColor(btn.color, 0.6f);
                RadialGradient gradient = new RadialGradient(
                        btn.cx - btn.radius * 0.2f, btn.cy - btn.radius * 0.2f, 
                        btn.radius * 1.2f, 
                        btn.color, darkerColor, Shader.TileMode.CLAMP);
                paintBtn.setShader(gradient);
                paintBtn.setShadowLayer(15.0f, 0.0f, 10.0f, Color.argb(150, 0, 0, 0));
                
                canvas.drawCircle(btn.cx, btn.cy, btn.radius, paintBtn);
                paintBtn.clearShadowLayer();
                paintBtn.setShader(null);
            }

            // 画文字
            paintText.setAlpha(currentAlpha);
            paintText.setTextSize(btn.radius * 0.5f);
            canvas.drawText(btn.id, btn.cx, btn.cy + (btn.radius * 0.18f), paintText);

            // 编辑模式下的选中框
            if (isEditMode) {
                paintBtn.setStyle(Paint.Style.STROKE);
                paintBtn.setStrokeWidth(4f);
                paintBtn.setColor(Color.WHITE);
                paintBtn.setAlpha(255);
                canvas.drawCircle(btn.cx, btn.cy, btn.radius + 5, paintBtn);
                paintBtn.setStyle(Paint.Style.FILL);
            }
        }
    }

    // 颜色加深算法，用于生成 3D 质感
    private int darkenColor(int color, float factor) {
        int a = Color.alpha(color);
        int r = Math.max((int)(Color.red(color) * factor), 0);
        int g = Math.max((int)(Color.green(color) * factor), 0);
        int b = Math.max((int)(Color.blue(color) * factor), 0);
        return Color.argb(a, r, g, b);
    }

    private void showMainMenu() {
        String modeText = isEditMode ? "💾 保存并退出编辑" : "🛠️ 开启按键编辑模式";
        CharSequence[] options = {modeText, "➕ 新建一个空白按键", "🔄 恢复默认原始布局"};

        new AlertDialog.Builder(getContext())
                .setTitle("游戏面板设置")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        isEditMode = !isEditMode;
                        if (!isEditMode) saveConfig();
                        invalidate();
                    } else if (which == 1) {
                        VirtualButton newBtn = new VirtualButton("新键", getWidth() / 2f, getHeight() / 2f, 90, colorValues[1], 180, KeyEvent.KEYCODE_UNKNOWN);
                        buttons.add(newBtn);
                        isEditMode = true;
                        showButtonSettingsDialog(newBtn); // 直接弹出设置框
                    } else if (which == 2) {
                        buttons.clear();
                        loadDefaultLayout();
                        saveConfig();
                        invalidate();
                    }
                }).show();
    }

    // 呼出独立按键设置面板
    private void showButtonSettingsDialog(final VirtualButton btn) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("配置按键: " + btn.id);

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        // 1. 按键名称
        final EditText inputName = new EditText(getContext());
        inputName.setHint("按键显示名称 (如 A, 宏)");
        inputName.setText(btn.id);
        layout.addView(createLabel("按键屏幕名称:"));
        layout.addView(inputName);

        // 2. 物理键值映射
        final EditText inputKey = new EditText(getContext());
        inputKey.setHint("输入键盘按键 (如 J, ENTER, SPACE)");
        inputKey.setText(KeyEvent.keyCodeToString(btn.sdlKeyCode).replace("KEYCODE_", ""));
        layout.addView(createLabel("映射键盘键位:"));
        layout.addView(inputKey);

        // 3. 颜色选择器
        final Spinner colorSpinner = new Spinner(getContext());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, colorNames);
        colorSpinner.setAdapter(adapter);
        // 自动定位当前颜色
        for (int i=0; i<colorValues.length; i++) {
            if (colorValues[i] == btn.color) { colorSpinner.setSelection(i); break; }
        }
        layout.addView(createLabel("按键颜色:"));
        layout.addView(colorSpinner);

        // 4. 透明度
        final SeekBar alphaBar = new SeekBar(getContext());
        alphaBar.setMax(255); alphaBar.setProgress(btn.alpha);
        layout.addView(createLabel("不透明度 (0-255):"));
        layout.addView(alphaBar);

        // 5. 大小
        final SeekBar sizeBar = new SeekBar(getContext());
        sizeBar.setMax(300); sizeBar.setProgress((int)btn.radius);
        layout.addView(createLabel("按键尺寸大小:"));
        layout.addView(sizeBar);

        // 6. 自定义皮肤图片名
        final EditText inputSkin = new EditText(getContext());
        inputSkin.setHint("输入图片名(如 a.png)。留空则用纯色");
        inputSkin.setText(btn.skinName);
        layout.addView(createLabel("自定义皮肤(放置在应用数据目录):"));
        layout.addView(inputSkin);

        builder.setView(layout);
        builder.setPositiveButton("保存设置", (dialog, which) -> {
            btn.id = inputName.getText().toString();
            
            // 强大的键值转换逻辑 (将用户输入的字母转为安卓 KEYCODE)
            String keyStr = inputKey.getText().toString().trim().toUpperCase();
            int newCode = KeyEvent.keyCodeFromString("KEYCODE_" + keyStr);
            if (newCode == KeyEvent.KEYCODE_UNKNOWN && keyStr.length() == 1) {
                // 如果用户输入了单个字符，尝试直接映射
                newCode = KeyEvent.keyCodeFromString("KEYCODE_" + keyStr);
            }
            btn.sdlKeyCode = (newCode != KeyEvent.KEYCODE_UNKNOWN) ? newCode : btn.sdlKeyCode;

            btn.color = colorValues[colorSpinner.getSelectedItemPosition()];
            btn.alpha = alphaBar.getProgress();
            btn.radius = Math.max(30, sizeBar.getProgress()); // 防止设为0
            
            btn.skinName = inputSkin.getText().toString().trim();
            btn.loadSkin(getContext()); // 重新加载皮肤
            
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

    private TextView createLabel(String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setPadding(0, 20, 0, 5);
        tv.setTextColor(Color.DKGRAY);
        return tv;
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
                        if (Math.hypot(x - buttons.get(i).cx, y - buttons.get(i).cy) < buttons.get(i).radius * 1.5f) {
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
                    // 智能识别：如果是轻触（时间短，距离近），则打开此按键的专属设置！
                    if (draggedButton != null && System.currentTimeMillis() - downTime < 250 && Math.hypot(x - downX, y - downY) < 20) {
                        showButtonSettingsDialog(draggedButton);
                    }
                    draggedButton = null;
                    break;
            }
            return true;
        }

        // 游玩模式多点触控发送信号
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                for (VirtualButton btn : buttons) {
                    if (!btn.isPressed && Math.hypot(x - btn.cx, y - btn.cy) < btn.radius) {
                        btn.isPressed = true;
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        SDLActivity.onNativeKeyDown(btn.sdlKeyCode);
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
                        SDLActivity.onNativeKeyUp(btn.sdlKeyCode);
                        invalidate();
                    }
                }
                break;
        }
        return true;
    }
}
