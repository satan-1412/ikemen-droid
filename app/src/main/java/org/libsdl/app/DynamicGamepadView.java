package org.ikemen_engine.ikemen_go; // 确保包名一致

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class DynamicGamepadView extends View {
    private static final String PREFS_NAME = "IkemenGamepadPrefs";
    private static final String KEY_LAYOUT = "ButtonLayoutData";

    private final List<VirtualButton> buttons = new ArrayList<>();
    private final Paint paintBtn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintMenu = new Paint(Paint.ANTI_ALIAS_FLAG);
    
    private final SharedPreferences prefs;
    public boolean isEditMode = false;
    private VirtualButton draggedButton = null;

    // 左上角设置按钮的区域
    private final RectF menuButtonRect = new RectF(20, 20, 200, 100);

    public static class VirtualButton {
        public String id;
        public float cx, cy, radius;
        public int color, alpha, sdlKeyCode;
        public boolean isPressed = false;

        public VirtualButton(String id, float cx, float cy, float radius, int color, int alpha, int sdlKeyCode) {
            this.id = id; this.cx = cx; this.cy = cy;
            this.radius = radius; this.color = color;
            this.alpha = alpha; this.sdlKeyCode = sdlKeyCode;
        }
    }

    public DynamicGamepadView(Context context) {
        super(context);
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
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
                    buttons.add(new VirtualButton(
                            obj.getString("id"),
                            (float) obj.getDouble("cx"),
                            (float) obj.getDouble("cy"),
                            (float) obj.getDouble("radius"),
                            obj.getInt("color"),
                            obj.getInt("alpha"),
                            obj.getInt("sdlKeyCode")
                    ));
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
                array.put(obj);
            }
            prefs.edit().putString(KEY_LAYOUT, array.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadDefaultLayout() {
        // 默认按键
        buttons.add(new VirtualButton("UP", 300, 550, 90, Color.DKGRAY, 120, KeyEvent.KEYCODE_W));
        buttons.add(new VirtualButton("DOWN", 300, 850, 90, Color.DKGRAY, 120, KeyEvent.KEYCODE_S));
        buttons.add(new VirtualButton("LEFT", 150, 700, 90, Color.DKGRAY, 120, KeyEvent.KEYCODE_A));
        buttons.add(new VirtualButton("RIGHT", 450, 700, 90, Color.DKGRAY, 120, KeyEvent.KEYCODE_D));
        
        buttons.add(new VirtualButton("X", 1600, 600, 100, Color.YELLOW, 150, KeyEvent.KEYCODE_U));
        buttons.add(new VirtualButton("Y", 1800, 500, 100, Color.CYAN, 150, KeyEvent.KEYCODE_I));
        buttons.add(new VirtualButton("Z", 2000, 400, 100, Color.MAGENTA, 150, KeyEvent.KEYCODE_O));
        buttons.add(new VirtualButton("A", 1700, 800, 100, Color.RED, 150, KeyEvent.KEYCODE_J));
        buttons.add(new VirtualButton("B", 1900, 700, 100, Color.BLUE, 150, KeyEvent.KEYCODE_K));
        buttons.add(new VirtualButton("C", 2100, 600, 100, Color.GREEN, 150, KeyEvent.KEYCODE_L));
        
        buttons.add(new VirtualButton("START", 1100, 150, 70, Color.GRAY, 180, KeyEvent.KEYCODE_ENTER));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // --- 绘制左上角设置按钮 ---
        paintMenu.setColor(Color.argb(180, 50, 50, 50));
        canvas.drawRoundRect(menuButtonRect, 20, 20, paintMenu);
        paintText.setTextSize(40f);
        canvas.drawText("⚙ 设置", menuButtonRect.centerX(), menuButtonRect.centerY() + 15, paintText);

        if (isEditMode) {
            canvas.drawColor(Color.argb(80, 255, 0, 0)); // 红色全屏遮罩提醒
            paintText.setTextSize(60f);
            canvas.drawText("编辑模式：请随意拖动按键，完成后点击左上角保存", getWidth() / 2f, 150, paintText);
        }

        // --- 绘制所有虚拟按键 ---
        for (int i = 0; i < buttons.size(); i++) {
            VirtualButton btn = buttons.get(i);
            paintBtn.setColor(btn.color);
            paintBtn.setAlpha(btn.isPressed && !isEditMode ? 255 : btn.alpha);
            
            canvas.drawCircle(btn.cx, btn.cy, btn.radius, paintBtn);
            
            paintText.setTextSize(btn.radius * 0.6f);
            canvas.drawText(btn.id, btn.cx, btn.cy + (btn.radius * 0.2f), paintText);
            
            if (isEditMode) {
                paintBtn.setStyle(Paint.Style.STROKE);
                paintBtn.setStrokeWidth(5f);
                paintBtn.setColor(Color.WHITE);
                canvas.drawCircle(btn.cx, btn.cy, btn.radius, paintBtn);
                paintBtn.setStyle(Paint.Style.FILL);
            }
        }
    }

    // 弹出设置菜单
    private void showSettingsMenu() {
        String modeText = isEditMode ? "💾 保存并退出编辑" : "🛠️ 开启按键位置编辑";
        CharSequence[] options = {modeText, "➕ 添加新按键 (M宏键)", "🔄 恢复默认布局"};

        new AlertDialog.Builder(getContext())
                .setTitle("虚拟面板设置")
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            isEditMode = !isEditMode;
                            if (!isEditMode) {
                                saveConfig(); // 退出时自动保存
                                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                            }
                            invalidate();
                        } else if (which == 1) {
                            // 动态添加一个新按键，默认出现在屏幕中间，映射到键盘的 M 键
                            buttons.add(new VirtualButton("M宏", getWidth() / 2f, getHeight() / 2f, 90, Color.parseColor("#FF9800"), 180, KeyEvent.KEYCODE_M));
                            isEditMode = true; // 自动开启编辑模式方便拖拽
                            invalidate();
                        } else if (which == 2) {
                            buttons.clear();
                            loadDefaultLayout();
                            saveConfig();
                            invalidate();
                        }
                    }
                })
                .setCancelable(true)
                .show();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int pointerIndex = event.getActionIndex();
        float x = event.getX(pointerIndex);
        float y = event.getY(pointerIndex);

        // === 拦截左上角设置按钮的点击 ===
        if (action == MotionEvent.ACTION_DOWN && menuButtonRect.contains(x, y)) {
            showSettingsMenu();
            return true;
        }

        if (isEditMode) {
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    for (int i = buttons.size() - 1; i >= 0; i--) {
                        VirtualButton btn = buttons.get(i);
                        if (Math.hypot(x - btn.cx, y - btn.cy) < btn.radius * 1.5f) {
                            draggedButton = btn;
                            break;
                        }
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (draggedButton != null) {
                        draggedButton.cx = Math.max(draggedButton.radius, Math.min(x, getWidth() - draggedButton.radius));
                        draggedButton.cy = Math.max(draggedButton.radius, Math.min(y, getHeight() - draggedButton.radius));
                        invalidate();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    draggedButton = null;
                    break;
            }
            return true;
        }

        // === 正常游玩模式 ===
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                for (int i = 0; i < buttons.size(); i++) {
                    VirtualButton btn = buttons.get(i);
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
                for (int i = 0; i < buttons.size(); i++) {
                    VirtualButton btn = buttons.get(i);
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