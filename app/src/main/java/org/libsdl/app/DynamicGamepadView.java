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
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
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
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class DynamicGamepadView extends View {
    private static final String PREFS_NAME = "IkemenGamepad_Pro_V3";
    private static final String KEY_LAYOUT_PREFIX = "LayoutSlot_";
    
    private int currentSlot = 0;
    // 摇杆模式：0=四键分离(默认), 1=现代圆盘摇杆, 2=经典街机摇杆
    public int joystickMode = 0; 

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
    // 记录正在编辑的按钮，用于接收相册返回的图片
    public VirtualButton currentlyEditingButton = null;

    public static class VirtualButton {
        public String id;
        public float cx, cy, radius;
        public int color, alpha;
        public String keyMapStr = "";
        public List<Integer> keyCodes = new ArrayList<>();
        public boolean isPressed = false;
        public String customImageUri = ""; // 升级：使用 Android 原生 Uri 存储相册图片路径
        public Bitmap skinBitmap = null;
        public boolean isDirectional = false; // 标识是否属于方向键（用于摇杆模式切换）

        public VirtualButton(String id, float cx, float cy, float radius, int color, int alpha, String keyMapStr, boolean isDir) {
            this.id = id; this.cx = cx; this.cy = cy;
            this.radius = radius; this.color = color;
            this.alpha = alpha; this.keyMapStr = keyMapStr;
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
        paintText.setColor(Color.WHITE);
        paintText.setTextAlign(Paint.Align.CENTER);
        paintText.setTypeface(Typeface.DEFAULT_BOLD);
        loadConfig(currentSlot);
    }

    // ========== 核心渲染层 (彻底修复 Alpha 穿透问题) ==========
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 绘制现代化菜单入口
        paintMenu.setColor(Color.argb(200, 30, 33, 40));
        paintMenu.setShadowLayer(8f, 0, 4f, Color.BLACK);
        canvas.drawRoundRect(menuButtonRect, 30, 30, paintMenu);
        paintText.setTextSize(40f);
        paintText.setAlpha(255);
        paintMenu.clearShadowLayer();
        canvas.drawText("⚙ 高级设置", menuButtonRect.centerX(), menuButtonRect.centerY() + 15, paintText);

        if (isEditMode) {
            canvas.drawColor(Color.argb(120, 0, 0, 0));
            paintText.setTextSize(45f);
            canvas.drawText("【布局编辑】拖拽调整，轻触按键高级设置", getWidth() / 2f, 150, paintText);
        }

        for (VirtualButton btn : buttons) {
            // 如果开启了现代摇杆模式，且该按键是方向键，则由新的渲染逻辑接管（下半部分实现）
            if (joystickMode > 0 && btn.isDirectional) continue;

            int currentAlpha = (btn.isPressed && !isEditMode) ? 255 : btn.alpha;
            
            if (btn.skinBitmap != null) {
                // 修复：图片也完美支持透明度调整
                paintBtn.setAlpha(currentAlpha);
                canvas.drawBitmap(btn.skinBitmap, btn.cx - btn.radius, btn.cy - btn.radius, paintBtn);
            } else {
                // 修复：将透明度直接注入到 RGB 色值中，解决 RadialGradient 忽略 setAlpha 的 Bug
                int baseColor = Color.argb(currentAlpha, Color.red(btn.color), Color.green(btn.color), Color.blue(btn.color));
                int darkColor = Color.argb(currentAlpha, Math.max(0, Color.red(btn.color)-80), Math.max(0, Color.green(btn.color)-80), Math.max(0, Color.blue(btn.color)-80));
                
                RadialGradient gradient = new RadialGradient(
                        btn.cx - btn.radius * 0.3f, btn.cy - btn.radius * 0.3f, 
                        btn.radius * 1.3f, baseColor, darkColor, Shader.TileMode.CLAMP);
                
                paintBtn.setShader(gradient);
                // 只有不透明度较高时才绘制阴影，防止阴影显得脏
                if (currentAlpha > 100) paintBtn.setShadowLayer(15.0f, 0.0f, 10.0f, Color.argb(currentAlpha/2, 0, 0, 0));
                else paintBtn.clearShadowLayer();
                
                canvas.drawCircle(btn.cx, btn.cy, btn.radius, paintBtn);
                paintBtn.clearShadowLayer();
                paintBtn.setShader(null);
            }

            // 绘制键位名称
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

    // ========== 核心物理反馈层 (彻底修复滑动断触Bug) ==========
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN && menuButtonRect.contains(event.getX(), event.getY())) {
            showMainMenu();
            return true;
        }

        if (isEditMode) {
            handleEditTouch(event); // 编辑模式逻辑抽出
            return true;
        }

        // 游玩模式：全局指针扫描算法，完美解决滑动按键卡死问题
        for (VirtualButton btn : buttons) {
            boolean isTouchedNow = false;
            // 扫描当前屏幕上所有的手指
            for (int i = 0; i < event.getPointerCount(); i++) {
                if (Math.hypot(event.getX(i) - btn.cx, event.getY(i) - btn.cy) < btn.radius) {
                    isTouchedNow = true;
                    break;
                }
            }

            // 状态变更：手指刚刚滑入或按下
            if (!btn.isPressed && isTouchedNow) {
                btn.isPressed = true;
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                for (int code : btn.keyCodes) SDLActivity.onNativeKeyDown(code);
            } 
            // 状态变更：手指刚刚滑出或抬起
            else if (btn.isPressed && !isTouchedNow) {
                btn.isPressed = false;
                for (int code : btn.keyCodes) SDLActivity.onNativeKeyUp(code);
            }
        }
        invalidate();
        return true;
    }

    private void handleEditTouch(MotionEvent event) {
        int action = event.getActionMasked();
        float x = event.getX(0);
        float y = event.getY(0);

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
                    showButtonSettingsDialog(draggedButton); // 呼出大厂UI设置
                }
                draggedButton = null;
                break;
        }
    }
    // ==========================================
    // 下半部分：摇杆渲染、大厂 UI、相册选择与触控引擎 (幽灵Fragment无侵入版)
    // ==========================================

    public static DynamicGamepadView instance;

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        instance = this; // 暴露单例供幽灵 Fragment 传回图片
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (instance == this) instance = null;
    }

    // 接收相册选好的图片 Uri
    public void onImagePicked(String uriStr) {
        if (currentlyEditingButton != null) {
            currentlyEditingButton.customImageUri = uriStr;
            currentlyEditingButton.loadSkinFromUri(getContext());
            saveConfig();
            invalidate();
            Toast.makeText(getContext(), "图片皮肤应用成功！", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------
    // 【黑魔法】幽灵 Fragment：用于在 View 内部直接调用相册并接收回调
    // 完全免去了修改 SDLActivity.java 的麻烦！
    // ---------------------------------------------------------
    @SuppressWarnings("deprecation")
    public static class ImagePickerFragment extends android.app.Fragment {
        @Override
        public void onCreate(android.os.Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            // 启动系统原生文件/相册选择器
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
                    // 申请持久化权限，防止重启游戏后图片无法读取
                    getActivity().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception e) { e.printStackTrace(); }
                
                if (DynamicGamepadView.instance != null) {
                    DynamicGamepadView.instance.onImagePicked(uri.toString());
                }
            }
            // 选完图片后，这个幽灵 Fragment 就自动销毁，不留痕迹
            getFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
        }
    }
    // ---------------------------------------------------------

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
                            obj.getInt("color"), obj.getInt("alpha"), obj.getString("keyMapStr"),
                            obj.optBoolean("isDirectional", false)
                    );
                    if (obj.has("customImageUri")) {
                        btn.customImageUri = obj.getString("customImageUri");
                        btn.loadSkinFromUri(getContext());
                    }
                    buttons.add(btn);
                }
                joystickMode = prefs.getInt("JoystickMode_" + slot, 0);
                invalidate();
                return;
            } catch (Exception e) { e.printStackTrace(); }
        }
        joystickMode = 0;
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
                obj.put("customImageUri", b.customImageUri);
                obj.put("isDirectional", b.isDirectional);
                array.put(obj);
            }
            prefs.edit()
                 .putString(KEY_LAYOUT_PREFIX + currentSlot, array.toString())
                 .putInt("JoystickMode_" + currentSlot, joystickMode)
                 .apply();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadDefaultLayout() {
        buttons.clear();
        // 匹配 config.ini 的 P1 键位，方向键 isDirectional = true
        buttons.add(new VirtualButton("UP", 250, 550, 80, Color.DKGRAY, 120, "UP", true));
        buttons.add(new VirtualButton("DOWN", 250, 850, 80, Color.DKGRAY, 120, "DOWN", true));
        buttons.add(new VirtualButton("LEFT", 100, 700, 80, Color.DKGRAY, 120, "LEFT", true));
        buttons.add(new VirtualButton("RIGHT", 400, 700, 80, Color.DKGRAY, 120, "RIGHT", true));
        
        buttons.add(new VirtualButton("X", 1600, 600, 90, Color.parseColor("#FFC107"), 150, "A", false));
        buttons.add(new VirtualButton("Y", 1800, 500, 90, Color.parseColor("#00BCD4"), 150, "S", false));
        buttons.add(new VirtualButton("Z", 2000, 400, 90, Color.parseColor("#9C27B0"), 150, "D", false));
        buttons.add(new VirtualButton("A", 1700, 800, 90, Color.parseColor("#F44336"), 150, "Z", false));
        buttons.add(new VirtualButton("B", 1900, 700, 90, Color.parseColor("#3F51B5"), 150, "X", false));
        buttons.add(new VirtualButton("C", 2100, 600, 90, Color.parseColor("#4CAF50"), 150, "C", false));
        
        buttons.add(new VirtualButton("START", 1100, 150, 60, Color.GRAY, 180, "RETURN", false));
    }

    // ========== 摇杆渲染引擎 ==========
    private float joyBaseX = 250, joyBaseY = 700;
    private float joyKnobX = 250, joyKnobY = 700;
    private float joyRadius = 180;
    private int joyPointerId = -1;

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (joystickMode > 0 && !isEditMode) {
            drawJoystick(canvas);
        } else if (joystickMode > 0 && isEditMode) {
            paintBtn.setColor(Color.WHITE);
            paintBtn.setStyle(Paint.Style.STROKE);
            paintBtn.setStrokeWidth(5f);
            paintBtn.setAlpha(150);
            canvas.drawCircle(joyBaseX, joyBaseY, joyRadius, paintBtn);
            paintText.setTextSize(35f);
            canvas.drawText("摇杆控制区", joyBaseX, joyBaseY, paintText);
            paintBtn.setStyle(Paint.Style.FILL);
        }
    }

    private void drawJoystick(Canvas canvas) {
        if (joystickMode == 1) {
            // 现代白色磨砂圆盘
            paintBtn.setColor(Color.WHITE);
            paintBtn.setAlpha(60);
            canvas.drawCircle(joyBaseX, joyBaseY, joyRadius, paintBtn);
            paintBtn.setAlpha(200);
            canvas.drawCircle(joyKnobX, joyKnobY, joyRadius * 0.35f, paintBtn);
        } else if (joystickMode == 2) {
            // 经典街机红色摇杆
            RadialGradient baseGrad = new RadialGradient(joyBaseX, joyBaseY, joyRadius, 
                    Color.parseColor("#444444"), Color.parseColor("#111111"), Shader.TileMode.CLAMP);
            paintBtn.setShader(baseGrad);
            paintBtn.setAlpha(220);
            canvas.drawCircle(joyBaseX, joyBaseY, joyRadius, paintBtn);
            paintBtn.setShader(null);
            
            paintBtn.setColor(Color.parseColor("#AAAAAA"));
            paintBtn.setStrokeWidth(25f);
            paintBtn.setStyle(Paint.Style.STROKE);
            canvas.drawLine(joyBaseX, joyBaseY, joyKnobX, joyKnobY, paintBtn);
            paintBtn.setStyle(Paint.Style.FILL);
            
            RadialGradient ballGrad = new RadialGradient(joyKnobX - 15, joyKnobY - 15, joyRadius * 0.5f, 
                    Color.parseColor("#FF5555"), Color.parseColor("#880000"), Shader.TileMode.CLAMP);
            paintBtn.setShader(ballGrad);
            paintBtn.setShadowLayer(15f, 0, 10f, Color.BLACK);
            canvas.drawCircle(joyKnobX, joyKnobY, joyRadius * 0.45f, paintBtn);
            paintBtn.clearShadowLayer();
            paintBtn.setShader(null);
        }
    }

    // ========== 现代化高级设置 UI ==========
    private void showMainMenu() {
        String modeText = isEditMode ? "💾 保存并退出编辑" : "🛠️ 开启按键拖拽编辑";
        String joyText = "🕹️ 摇杆模式: " + (joystickMode==0?"独立十字键":joystickMode==1?"现代漂浮圆盘":"经典街机摇杆");
        CharSequence[] options = {modeText, "➕ 新建组合键/宏按键", joyText, "📂 布局存档管理", "🔄 恢复初始默认布局"};

        new AlertDialog.Builder(getContext(), android.R.style.Theme_Device_Default_Dialog_Alert)
                .setTitle("⚙️ 游戏面板全局设置")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        isEditMode = !isEditMode;
                        if (!isEditMode) saveConfig();
                        invalidate();
                    } else if (which == 1) {
                        VirtualButton newBtn = new VirtualButton("新键", getWidth() / 2f, getHeight() / 2f, 90, Color.RED, 150, "Z+X", false);
                        buttons.add(newBtn);
                        isEditMode = true;
                        showButtonSettingsDialog(newBtn);
                    } else if (which == 2) {
                        joystickMode = (joystickMode + 1) % 3;
                        saveConfig();
                        invalidate();
                    } else if (which == 3) {
                        showProfileManager();
                    } else if (which == 4) {
                        loadDefaultLayout();
                        saveConfig();
                        invalidate();
                    }
                }).show();
    }

    private void showProfileManager() {
        CharSequence[] options = {"📂 读取 方案 1", "💾 覆盖保存至 方案 1", "📂 读取 方案 2", "💾 覆盖保存至 方案 2"};
        new AlertDialog.Builder(getContext(), android.R.style.Theme_Device_Default_Dialog_Alert)
                .setTitle("布局方案存档 (适配不同人物)")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) loadConfig(1);
                    if (which == 1) { currentSlot = 1; saveConfig(); Toast.makeText(getContext(),"已存入方案1",Toast.LENGTH_SHORT).show();}
                    if (which == 2) loadConfig(2);
                    if (which == 3) { currentSlot = 2; saveConfig(); Toast.makeText(getContext(),"已存入方案2",Toast.LENGTH_SHORT).show();}
                }).show();
    }

    private void showButtonSettingsDialog(final VirtualButton btn) {
        currentlyEditingButton = btn;
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), android.R.style.Theme_Device_Default_Light_Dialog_Alert);
        builder.setTitle("🔧 配置按键: " + btn.id);

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 30, 60, 30);
        scroll.addView(layout);

        layout.addView(createTitle("1. 按键屏幕显示名称:"));
        final EditText inputName = new EditText(getContext());
        inputName.setText(btn.id);
        layout.addView(inputName);

        layout.addView(createTitle("2. 键盘键位映射 (多键用+连接):"));
        final EditText inputKey = new EditText(getContext());
        inputKey.setHint("如: Z, UP, Z+X, ENTER");
        inputKey.setText(btn.keyMapStr);
        layout.addView(inputKey);

        layout.addView(createTitle("3. 颜色调节 (RGB色盘):"));
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

        layout.addView(createTitle("4. 外观与尺寸:"));
        final SeekBar alphaBar = createColorBar(layout, "不透明度 (0-255)", btn.alpha);
        final SeekBar sizeBar = createColorBar(layout, "按键大小", (int)btn.radius);
        sizeBar.setMax(300);

        // 核心修改：使用幽灵 Fragment 调用相册
        layout.addView(createTitle("5. 自定义图片皮肤:"));
        Button btnPickImage = new Button(getContext());
        btnPickImage.setText("🖼️ 从系统相册选择图片");
        btnPickImage.setOnClickListener(v -> {
            android.app.Activity activity = (android.app.Activity) getContext();
            ImagePickerFragment fragment = new ImagePickerFragment();
            activity.getFragmentManager().beginTransaction().add(fragment, "image_picker").commitAllowingStateLoss();
        });
        layout.addView(btnPickImage);

        Button btnClearImage = new Button(getContext());
        btnClearImage.setText("❌ 移除图片，恢复纯色");
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
            btn.keyMapStr = inputKey.getText().toString().trim().toUpperCase();
            btn.parseKeyCodes();
            btn.color = Color.rgb(rgb[0], rgb[1], rgb[2]);
            btn.alpha = alphaBar.getProgress();
            btn.radius = Math.max(40, sizeBar.getProgress());
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
        tv.setTextColor(Color.parseColor("#222222"));
        tv.setPadding(0, 40, 0, 10);
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

    // ========== 核心物理触控层 (完美解决滑动断触与摇杆八向) ==========
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

        // --- 摇杆控制处理 (模式1和2) ---
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

        // --- 全局按键防断触扫描算法 ---
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
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
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

    private void handleEditTouch(MotionEvent event) {
        int action = event.getActionMasked();
        float x = event.getX(0);
        float y = event.getY(0);

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
    }
}
