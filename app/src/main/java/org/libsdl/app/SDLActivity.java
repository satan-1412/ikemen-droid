package org.libsdl.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.UiModeManager;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.ikemen_engine.ikemen_go.R;

import java.io.File;
import java.util.Hashtable;
import java.util.Locale;

/**
    SDL Activity
*/
public class SDLActivity extends Activity implements View.OnSystemUiVisibilityChangeListener {
    static {
//        System.setProperty("SDL_HIDAPI_IGNORE_DEVICES", "1");
        System.loadLibrary("main"); // match the Go shared lib name, usually libmain.so
    }
    public static native void nativeOnSDLReady(String path);
    private static final String TAG = "SDL";
    private static final int SDL_MAJOR_VERSION = 2;
    private static final int SDL_MINOR_VERSION = 32;
    private static final int SDL_MICRO_VERSION = 10;

    public static boolean mIsResumedCalled, mHasFocus;
    public static final boolean mHasMultiWindow = (Build.VERSION.SDK_INT >= 24  /* Android 7.0 (N) */);
    public static boolean mHasPromptedFolderThisSession = false; 
    public static boolean mIsPickerActive = false; // 【终极修复】防止 onResume 触发双重弹窗的绝对物理锁


    // Cursor types
    private static final int SDL_SYSTEM_CURSOR_ARROW = 0;
    private static final int SDL_SYSTEM_CURSOR_IBEAM = 1;
    private static final int SDL_SYSTEM_CURSOR_WAIT = 2;
    private static final int SDL_SYSTEM_CURSOR_CROSSHAIR = 3;
    private static final int SDL_SYSTEM_CURSOR_WAITARROW = 4;
    private static final int SDL_SYSTEM_CURSOR_SIZENWSE = 5;
    private static final int SDL_SYSTEM_CURSOR_SIZENESW = 6;
    private static final int SDL_SYSTEM_CURSOR_SIZEWE = 7;
    private static final int SDL_SYSTEM_CURSOR_SIZENS = 8;
    private static final int SDL_SYSTEM_CURSOR_SIZEALL = 9;
    private static final int SDL_SYSTEM_CURSOR_NO = 10;
    private static final int SDL_SYSTEM_CURSOR_HAND = 11;

    protected static final int SDL_ORIENTATION_UNKNOWN = 0;
    protected static final int SDL_ORIENTATION_LANDSCAPE = 1;
    protected static final int SDL_ORIENTATION_LANDSCAPE_FLIPPED = 2;
    protected static final int SDL_ORIENTATION_PORTRAIT = 3;
    protected static final int SDL_ORIENTATION_PORTRAIT_FLIPPED = 4;

    protected static int mCurrentOrientation;
    protected static Locale mCurrentLocale;

    // Handle the state of the native layer
    public enum NativeState {
           INIT, RESUMED, PAUSED
    }

    public static NativeState mNextNativeState;
    public static NativeState mCurrentNativeState;

    /** If shared libraries (e.g. SDL or the native application) could not be loaded. */
    public static boolean mBrokenLibraries = true;

    // Main components
    protected static SDLActivity mSingleton;
    protected static SDLSurface mSurface;
    protected static DummyEdit mTextEdit;
    protected static boolean mScreenKeyboardShown;
    protected static ViewGroup mLayout;
    protected static SDLClipboardHandler mClipboardHandler;
    protected static Hashtable<Integer, PointerIcon> mCursors;
    protected static int mLastCursorID;
    protected static SDLGenericMotionListener_API12 mMotionListener;
    protected static HIDDeviceManager mHIDDeviceManager;

    // This is what SDL runs in. It invokes SDL_main(), eventually
    protected static Thread mSDLThread;

    protected static SDLGenericMotionListener_API12 getMotionListener() {
        if (mMotionListener == null) {
            if (Build.VERSION.SDK_INT >= 26 /* Android 8.0 (O) */) {
                mMotionListener = new SDLGenericMotionListener_API26();
            } else if (Build.VERSION.SDK_INT >= 24 /* Android 7.0 (N) */) {
                mMotionListener = new SDLGenericMotionListener_API24();
            } else {
                mMotionListener = new SDLGenericMotionListener_API12();
            }
        }

        return mMotionListener;
    }

    protected String getMainSharedObject() {
        String library;
        String[] libraries = SDLActivity.mSingleton.getLibraries();
        if (libraries.length > 0) {
            library = "lib" + libraries[libraries.length - 1] + ".so";
        } else {
            library = "libmain.so";
        }
        return getContext().getApplicationInfo().nativeLibraryDir + "/" + library;
    }

    protected String getMainFunction() {
        return "SDL_main";
    }

    protected String[] getLibraries() {
        return new String[] {
            "SDL2",
            "main"
        };
    }

    public void loadLibraries() {
       for (String lib : getLibraries()) {
          SDL.loadLibrary(lib, this);
       }
    }

    protected String[] getArguments() {
        return new String[0];
    }

    public static void initialize() {
        mSingleton = null;
        mSurface = null;
        mTextEdit = null;
        mLayout = null;
        mClipboardHandler = null;
        mCursors = new Hashtable<Integer, PointerIcon>();
        mLastCursorID = 0;
        mSDLThread = null;
        mIsResumedCalled = false;
        mHasFocus = true;
        mNextNativeState = NativeState.INIT;
        mCurrentNativeState = NativeState.INIT;
    }

    protected SDLSurface createSDLSurface(Context context) {
        return new SDLSurface(context);
    }
    private static final int FOLDER_PICKER_CODE = 42;
    public static final int FOLDER_PICKER_PRESET_CODE = 46; // 【新增】：预设文件夹专属的选择码
    private SharedPreferences mSharedPrefs;
    private static String mBasePath;
    private Button mSDButton;
    private ControllerOverlay mControllerOverlay;
    private final Runnable hideRunnable = new Runnable() {
    @Override
    public void run() {
            if (DynamicGamepadView.instance != null && DynamicGamepadView.instance.isEditMode) {
                mUIHandler.postDelayed(this, 2000);
                return;
            }
           
            if (mLayout != null) mLayout.setVisibility(View.INVISIBLE);
        }
    };
    
    private Handler mUIHandler = new Handler(android.os.Looper.getMainLooper());

    private final String[] UPDATE_FILE_CHECK_DIRS = new String[] {
        "external/script"
    };

    private final boolean USE_FOLDER_SELECT = true;

    // Setup
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            // Stop Go from using SIGURG (the Android 14 crasher)
            android.system.Os.setenv("GODEBUG", "asyncpreemptoff=1", true);

            // [性能与内存优化] 动态识别处理器架构
            String arch = System.getProperty("os.arch");
            boolean is64Bit = arch != null && (arch.contains("aarch64") || arch.contains("arm64"));

            if (is64Bit) {
                // 【64位狂暴模式】：8MB 安全栈空间防止 OOM。GOGC=200 降低回收频率，让游戏无限制占用大内存换取极致性能！
                System.setProperty("SDL_THREAD_STACK_SIZE", "8388608"); 
                android.system.Os.setenv("GOGC", "200", true);
                // 解除软限制，警告 Go 引擎可以使用更多系统内存
                android.system.Os.setenv("GOMEMLIMIT", "2048MiB", true); 
                Log.i(TAG, "IkemenGO: Running in 64-bit Extreme Performance Mode");
            } else {
                // 【32位维稳模式】：2MB 极简栈空间，GOGC=80 频繁回收内存，确保老旧安卓 5.0 机器绝对不会崩溃！
                System.setProperty("SDL_THREAD_STACK_SIZE", "2097152");
                android.system.Os.setenv("GOGC", "80", true);
                Log.i(TAG, "IkemenGO: Running in 32-bit Maximum Stability Mode");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to set Go/SDL environment: " + e.getMessage());
        }

        Log.v(TAG, "Device: " + Build.DEVICE);
        Log.v(TAG, "Model: " + Build.MODEL);
        Log.v(TAG, "onCreate()");
        super.onCreate(savedInstanceState);

        mSharedPrefs = this.getSharedPreferences(getString(R.string.prefs_key), MODE_PRIVATE);

        try {
            Thread.currentThread().setName("SDLActivity");
        } catch (Exception e) {
            Log.v(TAG, "modify thread properties failed " + e.toString());
        }

        // Load shared libraries
        String errorMsgBrokenLib = "";
        try {
            loadLibraries();
            mBrokenLibraries = false; /* success */
        } catch(UnsatisfiedLinkError e) {
            System.err.println(e.getMessage());
            mBrokenLibraries = true;
            errorMsgBrokenLib = e.getMessage();
        } catch(Exception e) {
            System.err.println(e.getMessage());
            mBrokenLibraries = true;
            errorMsgBrokenLib = e.getMessage();
        }

        if (!mBrokenLibraries) {
            String expected_version = String.valueOf(SDL_MAJOR_VERSION) + "." +
                                      String.valueOf(SDL_MINOR_VERSION) + "." +
                                      String.valueOf(SDL_MICRO_VERSION);
            String version = nativeGetVersion();
            if (!version.equals(expected_version)) {
                mBrokenLibraries = true;
                errorMsgBrokenLib = "SDL C/Java version mismatch (expected " + expected_version + ", got " + version + ")";
            }
        }

        if (mBrokenLibraries) {
            mSingleton = this;
            AlertDialog.Builder dlgAlert  = new AlertDialog.Builder(this);
            dlgAlert.setMessage("An error occurred while trying to start the application. Please try again and/or reinstall."
                  + System.getProperty("line.separator")
                  + System.getProperty("line.separator")
                  + "Error: " + errorMsgBrokenLib);
            dlgAlert.setTitle("SDL Error");
            dlgAlert.setPositiveButton("Exit",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog,int id) {
                        SDLActivity.mSingleton.finish();
                    }
                });
           dlgAlert.setCancelable(false);
           dlgAlert.create().show();

           return;
        }

        // Set up JNI
        SDL.setupJNI();

        // Initialize state
        SDL.initialize();

        mSingleton = this;
        SDL.setContext(this);

        mClipboardHandler = new SDLClipboardHandler();

        mHIDDeviceManager = HIDDeviceManager.acquire(this);

        mSurface = createSDLSurface(this);
        mSurface.setZOrderOnTop(false);
        mSurface.setZOrderMediaOverlay(true);

        FrameLayout rootLayout = new FrameLayout(this);
        rootLayout.addView(mSurface, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        mLayout = new RelativeLayout(this);
        mLayout.setClickable(false);
        mLayout.setFocusable(false);
        rootLayout.addView(mLayout, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        mSDButton = new Button(this);
        mSDButton.setText(R.string.game_folder_str);
        mSDButton.setAlpha(0.5f);

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);

        if (USE_FOLDER_SELECT) {
            mSDButton.setOnClickListener(v -> checkAndPickFolder());
        }
        
        mCurrentOrientation = SDLActivity.getCurrentOrientation();
        SDLActivity.onNativeOrientationChanged(mCurrentOrientation);

        try {
            if (Build.VERSION.SDK_INT < 24 /* Android 7.0 (N) */) {
                mCurrentLocale = getContext().getResources().getConfiguration().locale;
            } else {
                mCurrentLocale = getContext().getResources().getConfiguration().getLocales().get(0);
            }
        } catch(Exception ignored) {
        }

        mSDButton.setZ(100f); 

        DynamicGamepadView dynamicGamepad = new DynamicGamepadView(this);
        android.widget.RelativeLayout.LayoutParams lp = new android.widget.RelativeLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
                android.view.ViewGroup.LayoutParams.MATCH_PARENT);
        mLayout.addView(dynamicGamepad, lp);
        
        if (dynamicGamepad.isAutoHideEnabled) {
            mUIHandler.postDelayed(hideRunnable, dynamicGamepad.autoHideSeconds * 1000L);
        }

        setContentView(rootLayout);

        setWindowStyle(false);

        View dv = getWindow().getDecorView();
        dv.setOnSystemUiVisibilityChangeListener(this);

        Intent intent = getIntent();
        if (intent != null && intent.getData() != null) {
            String filename = intent.getData().getPath();
            if (filename != null) {
                Log.v(TAG, "Got filename: " + filename);
                SDLActivity.onNativeDropFile(filename);
            }
        }

        if (USE_FOLDER_SELECT) {
            mBasePath = mSharedPrefs.getString(getString(R.string.game_folder_key), "");
            boolean alwaysAsk = this.getSharedPreferences("IkemenGamepad_Pro_V5", Context.MODE_PRIVATE).getBoolean("AlwaysAskFolder", true);
            
            // 【完美闭环】：如果是玩家刚才在菜单里点“重选目录”引发的重启，强制跳过开局询问
            boolean skipNextAsk = mSharedPrefs.getBoolean("SkipNextAsk", false);
            if (skipNextAsk) {
                alwaysAsk = false;
                mSharedPrefs.edit().putBoolean("SkipNextAsk", false).commit(); // 阅后即焚
            }

            // 【究极修复】：只有在游戏底层引擎尚未启动且本回合未弹窗时，才清空路径强制重选
            if (alwaysAsk && mSDLThread == null && !mHasPromptedFolderThisSession) {
                mBasePath = ""; 
                mHasPromptedFolderThisSession = true; 
            }
        } else {




            mBasePath = getExternalFilesDir(null).getAbsolutePath();
        }

        if (mBasePath.isEmpty()) {
            checkAndPickFolder();
        } else {
            setupContent();
        }
    }

    // === 把方法放在这里 ===
    public void cancelAutoHide() {
        if (mUIHandler != null) {
            mUIHandler.removeCallbacks(hideRunnable);
        }
        if (mLayout != null) {
            mLayout.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        int action = ev.getAction();
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            if (mLayout != null) {
                ViewGroup root = (ViewGroup) mLayout.getParent();
                int lastChildIndex = root.getChildCount() - 1;

                if (mLayout.getVisibility() != View.VISIBLE || root.getChildAt(lastChildIndex) != mLayout) {
                    mLayout.setVisibility(View.VISIBLE);
                    mLayout.bringToFront();
                    root.requestLayout();
                }
            }
            
            mUIHandler.removeCallbacks(hideRunnable);
            
            if (DynamicGamepadView.instance != null) {
                if (DynamicGamepadView.instance.isAutoHideEnabled) {
                    mUIHandler.postDelayed(hideRunnable, DynamicGamepadView.instance.autoHideSeconds * 1000L);
                }
            } else {
                mUIHandler.postDelayed(hideRunnable, 5000);
            }
        }
        
        return super.dispatchTouchEvent(ev);
    }

    // ================= 【新增：预设文件夹专属的拾取与重启逻辑】 =================
    public void pickFolderForPreset() {
        if (mIsPickerActive) return; 
        mIsPickerActive = true;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, FOLDER_PICKER_PRESET_CODE);
    }

    public void saveAndRestartWithPresetUri(String uri) {
        // 1. 保存玩家选择的预设路径为当前主路径
        mSharedPrefs.edit().putString(getString(R.string.game_folder_key), uri).commit();
        // 2. 写入免打扰标记，告诉下一次启动“是我主动重启的，直接进游戏别弹窗”
        mSharedPrefs.edit().putBoolean("SkipNextAsk", true).commit();
        
        // 3. 暴力杀进程并原地复活 (加入极其微小的延迟，确保系统有时间处理启动指令，实现真正的无缝重载)
        Intent intent = getBaseContext().getPackageManager().getLaunchIntentForPackage(getBaseContext().getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                startActivity(intent);
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(0);
                }, 50);
            }, 100);
        }
    }
    // =========================================================================

    public void checkAndPickFolder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(Uri.parse(String.format("package:%s", getPackageName())));
                    startActivity(intent);
                } catch (Exception e) {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                }
            } else {
                openDirectoryPicker();
            }
        } else {
            openDirectoryPicker();
        }
    }

    public String getFullPathFromTreeUri(Uri treeUri) {
        if (treeUri == null) return null;
        String treeId = DocumentsContract.getTreeDocumentId(treeUri);
        String[] split = treeId.split(":");
        String type = split[0];
        String path = (split.length > 1) ? split[1] : "";

        if ("primary".equalsIgnoreCase(type)) {
            return Environment.getExternalStorageDirectory() + "/" + path;
        } else {
            File[] externalDirs = getExternalFilesDirs(null);
            for (File f : externalDirs) {
                if (f != null) {
                    String absPath = f.getAbsolutePath();
                    if (absPath.contains(type)) {
                        return absPath.split("/Android/")[0] + "/" + path;
                    }
                }
            }
        }
        return null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        // === 【新增：拦截并处理预设文件夹的选择结果】 ===
        if (requestCode == FOLDER_PICKER_PRESET_CODE) {
            mIsPickerActive = false;
            if (resultCode == RESULT_OK && data != null) {
                Uri treeUri = data.getData();
                getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                String selectedPath = getFullPathFromTreeUri(treeUri);
                if (selectedPath != null && !selectedPath.isEmpty()) {
                    if (org.libsdl.app.DynamicGamepadView.instance != null) {
                        String name = "新预设文件夹";
                        try {
                            String[] parts = selectedPath.split("/");
                            name = parts[parts.length - 1]; // 自动把最后一级目录名当做预设的默认名字
                        } catch (Exception e) {}
                        
                        // 生成预设对象并保存到游戏面板的存档中
                        org.libsdl.app.DynamicGamepadView.FolderPreset preset = new org.libsdl.app.DynamicGamepadView.FolderPreset(name, selectedPath, android.graphics.Color.WHITE);
                        org.libsdl.app.DynamicGamepadView.instance.folderPresets.add(preset);
                        org.libsdl.app.DynamicGamepadView.instance.saveConfig();
                        
                        Toast.makeText(this, "✅ 成功添加预设: " + name, Toast.LENGTH_SHORT).show();
                    }
                }
            }
            return; // 处理完毕，直接结束，不走原来的逻辑
        }
        // =================================================

        if (requestCode == FOLDER_PICKER_CODE) {
            mIsPickerActive = false; // 解除防连弹物理锁
            
            // 【究极防死循环判定】：不要管路径空不空，直接看底层引擎有没有在跑！没跑就是开局，绝对不准重启！
            boolean isFreshRun = (mSDLThread == null);
            
            if (resultCode == RESULT_OK && data != null) {
                Uri treeUri = data.getData();
                getContentResolver().takePersistableUriPermission(treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                String selectedPath = getFullPathFromTreeUri(treeUri);
                if (selectedPath != null && !selectedPath.isEmpty()) {
                    mBasePath = selectedPath;
                } else {
                    mBasePath = getExternalFilesDir(null).getAbsolutePath();
                }
            } else {
                if (mBasePath == null || mBasePath.isEmpty()) {
                    mBasePath = getExternalFilesDir(null).getAbsolutePath();
                }
            }
            
            mSharedPrefs.edit().putString(getString(R.string.game_folder_key), mBasePath).commit();

            if (isFreshRun) {
                // 如果是冷启动，选完目录直接放行进入游戏！
                setupContent(); 
            } else {
                // 【完美闭环】：写入免打扰标记，告诉下一次启动“这是我主动重启的，别再弹框烦我”
                mSharedPrefs.edit().putBoolean("SkipNextAsk", true).commit();

                // 只有游戏已经在运行（玩家在菜单里点了“重选目录”），此时才必须杀进程重启App
                Intent intent = getBaseContext().getPackageManager().getLaunchIntentForPackage(getBaseContext().getPackageName());
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                android.os.Process.killProcess(android.os.Process.myPid());
                Runtime.getRuntime().exit(0);
            }
        }
    }



    public void openDirectoryPicker() {
        if (mIsPickerActive) return; 
        mIsPickerActive = true;

        if (Build.VERSION.SDK_INT < 24) {
            Log.i(TAG, "检测到旧版安卓(API < 24)，自动指定根目录 IkemenGO 文件夹");
            mIsPickerActive = false; 
            File defaultGameDir = new File(Environment.getExternalStorageDirectory(), "IkemenGO");
            if (!defaultGameDir.exists()) {
                defaultGameDir.mkdirs();
            }
            mBasePath = defaultGameDir.getAbsolutePath();
            // 【修复你代码里的中文逗号报错】
            mSharedPrefs.edit().putString(getString(R.string.game_folder_key), mBasePath).commit();
            setupContent();
        } else {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(intent, FOLDER_PICKER_CODE);
        }
    }


    private boolean filesNeedUpdate(File baseDir, String[] dirsToCheck) {
        try {
            for (String checkDir : dirsToCheck) {
                String[] files = getAssets().list(checkDir);
                if (files == null || files.length == 0) return false;

                for (String fileName : files) {
                    String relativePath = checkDir + "/" + fileName;
                    File localFile = new File(baseDir, relativePath);

                    if (!localFile.exists()) {
                        return true; 
                    }
                }
            }
        } catch (java.io.IOException e) {
            Log.e("AssetExtractor", "Error checking scripts", e);
            return true;
        }
        return false;
    }

    private void setupContent() {
        File baseDir = new File(mBasePath);

        new Thread(() -> {
            boolean updateRequired = filesNeedUpdate(baseDir, UPDATE_FILE_CHECK_DIRS);

            if (!updateRequired) {
                Log.i("SDLActivity", "Scripts match APK. Skipping extraction.");
                runOnUiThread(this::onSDLReady);
            } else {
                Log.i("SDLActivity", "Specified files are outdated or missing. Beginning extraction...");

                runOnUiThread(() -> {
                    ProgressDialog progress = new ProgressDialog(this);
                    progress.setTitle(R.string.updating_files_title);
                    progress.setMessage(getString(R.string.updating_files_msg));
                    progress.setCancelable(false);
                    progress.show();

                    new Thread(() -> {
                        try {
                            AssetExtractor.extractAll(getAssets(), baseDir);
                            runOnUiThread(() -> {
                                progress.dismiss();
                                onSDLReady();
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> {
                                progress.dismiss();
                                new AlertDialog.Builder(this)
                                        .setTitle(R.string.update_error_title)
                                        .setMessage(e.getMessage())
                                        .setPositiveButton(R.string.exit_str, (d, w) -> finish())
                                        .show();
                            });
                        }
                    }).start();
                });
            }
        }).start();
    }

    private void onSDLReady() {
        mBasePath = mSharedPrefs.getString(getString(R.string.game_folder_key), "");

        if (mBasePath.isEmpty()) {
            mBasePath = getExternalFilesDir(null).getAbsolutePath();
        }
        SDLActivity.nativeOnSDLReady(mBasePath);
    }

    protected void pauseNativeThread() {
        mNextNativeState = NativeState.PAUSED;
        mIsResumedCalled = false;

        if (SDLActivity.mBrokenLibraries) {
            return;
        }

        SDLActivity.handleNativeState();
    }

    protected void resumeNativeThread() {
        mNextNativeState = NativeState.RESUMED;
        mIsResumedCalled = true;

        if (SDLActivity.mBrokenLibraries) {
           return;
        }

        SDLActivity.handleNativeState();
    }

    @Override
    protected void onPause() {
        Log.v(TAG, "onPause()");
        super.onPause();

        if (mHIDDeviceManager != null) {
            mHIDDeviceManager.setFrozen(true);
        }
        if (!mHasMultiWindow) {
            pauseNativeThread();
        }
    }

    @Override
    protected void onResume() {
        Log.v(TAG, "onResume()");
        super.onResume();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                if (mBasePath == null || mBasePath.isEmpty()) {
                    openDirectoryPicker();
                }
            }
        }

        if (mHIDDeviceManager != null) {
            mHIDDeviceManager.setFrozen(false);
        }
        if (!mHasMultiWindow) {
            resumeNativeThread();
        }
    }

    @Override
    protected void onStop() {
        Log.v(TAG, "onStop()");
        super.onStop();
        if (mHasMultiWindow) {
            pauseNativeThread();
        }
    }

    @Override
    protected void onStart() {
        Log.v(TAG, "onStart()");
        super.onStart();
        if (mHasMultiWindow) {
            resumeNativeThread();
        }
    }

    public static int getCurrentOrientation() {
        int result = SDL_ORIENTATION_UNKNOWN;

        Activity activity = (Activity)getContext();
        if (activity == null) {
            return result;
        }
        Display display = activity.getWindowManager().getDefaultDisplay();

        switch (display.getRotation()) {
            case Surface.ROTATION_0:
                result = SDL_ORIENTATION_PORTRAIT;
                break;
            case Surface.ROTATION_90:
                result = SDL_ORIENTATION_LANDSCAPE;
                break;
            case Surface.ROTATION_180:
                result = SDL_ORIENTATION_PORTRAIT_FLIPPED;
                break;
            case Surface.ROTATION_270:
                result = SDL_ORIENTATION_LANDSCAPE_FLIPPED;
                break;
        }

        return result;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        Log.v(TAG, "onWindowFocusChanged(): " + hasFocus);

        if (SDLActivity.mBrokenLibraries) {
           return;
        }

        mHasFocus = hasFocus;
        if (hasFocus) {
           mNextNativeState = NativeState.RESUMED;
           SDLActivity.getMotionListener().reclaimRelativeMouseModeIfNeeded();

           SDLActivity.handleNativeState();
           nativeFocusChanged(true);

        } else {
           nativeFocusChanged(false);
           if (!mHasMultiWindow) {
               mNextNativeState = NativeState.PAUSED;
               SDLActivity.handleNativeState();
           }
        }
    }

    @Override
    public void onLowMemory() {
        Log.v(TAG, "onLowMemory()");
        super.onLowMemory();

        if (SDLActivity.mBrokenLibraries) {
           return;
        }

        SDLActivity.nativeLowMemory();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.v(TAG, "onConfigurationChanged()");
        super.onConfigurationChanged(newConfig);

        if (SDLActivity.mBrokenLibraries) {
           return;
        }

        if (mCurrentLocale == null || !mCurrentLocale.equals(newConfig.locale)) {
            mCurrentLocale = newConfig.locale;
            SDLActivity.onNativeLocaleChanged();
        }
    }

    @Override
    protected void onDestroy() {
        Log.v(TAG, "onDestroy()");

        if (mHIDDeviceManager != null) {
            HIDDeviceManager.release(mHIDDeviceManager);
            mHIDDeviceManager = null;
        }

        SDLAudioManager.release(this);

        if (SDLActivity.mBrokenLibraries) {
           super.onDestroy();
           return;
        }

        if (SDLActivity.mSDLThread != null) {
            SDLActivity.nativeSendQuit();
            try {
                SDLActivity.mSDLThread.join();
            } catch(Exception e) {
                Log.v(TAG, "Problem stopping SDLThread: " + e);
            }
        }

        SDLActivity.nativeQuit();
        super.onDestroy();
        System.exit(0);
    }

    @Override
    public void onBackPressed() {
        boolean trapBack = SDLActivity.nativeGetHintBoolean("SDL_ANDROID_TRAP_BACK_BUTTON", false);
        if (trapBack) {
            return;
        }
        if (!isFinishing()) {
            super.onBackPressed();
        }
    }

    public static void manualBackButton() {
        mSingleton.pressBackButton();
    }

    public void pressBackButton() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!SDLActivity.this.isFinishing()) {
                    SDLActivity.this.superOnBackPressed();
                }
            }
        });
    }

    public void superOnBackPressed() {
        super.onBackPressed();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
    
            // [新增逻辑]：最高优先级拦截物理手柄与摇杆按键，交由自定义键位系统接管
        if (org.libsdl.app.DynamicGamepadView.instance != null) {
            int source = event.getSource();
            // 判断来源是否为物理手柄或摇杆
            if ((source & android.view.InputDevice.SOURCE_GAMEPAD) == android.view.InputDevice.SOURCE_GAMEPAD ||
                (source & android.view.InputDevice.SOURCE_JOYSTICK) == android.view.InputDevice.SOURCE_JOYSTICK) {
                
                // 将事件抛给动态手柄面板
                boolean handled = org.libsdl.app.DynamicGamepadView.instance.onPhysicalGamepadKeyEvent(event);
                // 如果自定义面板接管了该按键，直接返回 true 拦截，禁止原版 SDL 处理
                if (handled) {
                    return true;
                }
            }
        }

        if (SDLActivity.mBrokenLibraries) {
           return false;
        }

        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_CAMERA ||
            keyCode == KeyEvent.KEYCODE_ZOOM_IN || 
            keyCode == KeyEvent.KEYCODE_ZOOM_OUT 
            ) {
            return false;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        // [新增逻辑]：最高优先级拦截物理摇杆的轴向移动与线性扳机，交由自定义键位系统接管
        if (org.libsdl.app.DynamicGamepadView.instance != null) {
            int source = event.getSource();
            // 判断来源是否为物理摇杆或手柄
            if ((source & android.view.InputDevice.SOURCE_JOYSTICK) == android.view.InputDevice.SOURCE_JOYSTICK ||
                (source & android.view.InputDevice.SOURCE_GAMEPAD) == android.view.InputDevice.SOURCE_GAMEPAD) {
                
                // 将事件抛给动态手柄面板
                boolean handled = org.libsdl.app.DynamicGamepadView.instance.onPhysicalGamepadMotionEvent(event);
                // 如果自定义面板接管了该动作，直接返回 true 销毁事件，禁止原版 SDL 处理
                if (handled) {
                    return true;
                }
            }
        }
        
        // 如果自定义面板没有接管，或者没有外设接入，则放行给原版逻辑
        return super.dispatchGenericMotionEvent(event);
    }

    public static void handleNativeState() {
        if (mNextNativeState == mCurrentNativeState) {
            return;
        }

        if (mNextNativeState == NativeState.INIT) {
            mCurrentNativeState = mNextNativeState;
            return;
        }

        if (mNextNativeState == NativeState.PAUSED) {
            if (mSDLThread != null) {
                nativePause();
            }
            if (mSurface != null) {
                mSurface.handlePause();
            }
            mCurrentNativeState = mNextNativeState;
            return;
        }

        if (mNextNativeState == NativeState.RESUMED) {
            if (mSurface.mIsSurfaceReady && mHasFocus && mIsResumedCalled) {
                if (mSDLThread == null) {
                    mSDLThread = new Thread(new SDLMain(), "SDLThread");
                    mSurface.enableSensor(Sensor.TYPE_ACCELEROMETER, true);
                    mSDLThread.start();
                } else {
                    nativeResume();
                }
                mSurface.handleResume();
                mCurrentNativeState = mNextNativeState;
            }
        }
    }

    static final int COMMAND_CHANGE_TITLE = 1;
    static final int COMMAND_CHANGE_WINDOW_STYLE = 2;
    static final int COMMAND_TEXTEDIT_HIDE = 3;
    static final int COMMAND_SET_KEEP_SCREEN_ON = 5;

    protected static final int COMMAND_USER = 0x8000;

    protected static boolean mFullscreenModeActive;

    protected boolean onUnhandledMessage(int command, Object param) {
        return false;
    }

    protected static class SDLCommandHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Context context = SDL.getContext();
            if (context == null) {
                Log.e(TAG, "error handling message, getContext() returned null");
                return;
            }
            switch (msg.arg1) {
            case COMMAND_CHANGE_TITLE:
                if (context instanceof Activity) {
                    ((Activity) context).setTitle((String)msg.obj);
                } else {
                    Log.e(TAG, "error handling message, getContext() returned no Activity");
                }
                break;
            case COMMAND_CHANGE_WINDOW_STYLE:
                if (Build.VERSION.SDK_INT >= 19 /* Android 4.4 (KITKAT) */) {
                    if (context instanceof Activity) {
                        Window window = ((Activity) context).getWindow();
                        if (window != null) {
                            if ((msg.obj instanceof Integer) && ((Integer) msg.obj != 0)) {
                                int flags = View.SYSTEM_UI_FLAG_FULLSCREEN |
                                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.INVISIBLE;
                                window.getDecorView().setSystemUiVisibility(flags);
                                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                                window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
                                SDLActivity.mFullscreenModeActive = true;
                            } else {
                                int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_VISIBLE;
                                window.getDecorView().setSystemUiVisibility(flags);
                                window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
                                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                                SDLActivity.mFullscreenModeActive = false;
                            }
                            if (Build.VERSION.SDK_INT >= 28 /* Android 9 (Pie) */) {
                                window.getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                            }
                        }
                    } else {
                        Log.e(TAG, "error handling message, getContext() returned no Activity");
                    }
                }
                break;
            case COMMAND_TEXTEDIT_HIDE:
                if (mTextEdit != null) {
                    mTextEdit.setLayoutParams(new RelativeLayout.LayoutParams(0, 0));
                    InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(mTextEdit.getWindowToken(), 0);
                    mScreenKeyboardShown = false;
                    mSurface.requestFocus();
                }
                break;
            case COMMAND_SET_KEEP_SCREEN_ON:
            {
                if (context instanceof Activity) {
                    Window window = ((Activity) context).getWindow();
                    if (window != null) {
                        if ((msg.obj instanceof Integer) && ((Integer) msg.obj != 0)) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        }
                    }
                }
                break;
            }
            default:
                if ((context instanceof SDLActivity) && !((SDLActivity) context).onUnhandledMessage(msg.arg1, msg.obj)) {
                    Log.e(TAG, "error handling message, command is " + msg.arg1);
                }
            }
        }
    }

    Handler commandHandler = new SDLCommandHandler();

    boolean sendCommand(int command, Object data) {
        Message msg = commandHandler.obtainMessage();
        msg.arg1 = command;
        msg.obj = data;
        boolean result = commandHandler.sendMessage(msg);

        if (Build.VERSION.SDK_INT >= 19 /* Android 4.4 (KITKAT) */) {
            if (command == COMMAND_CHANGE_WINDOW_STYLE) {
                boolean bShouldWait = false;
                if (data instanceof Integer) {
                    Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
                    DisplayMetrics realMetrics = new DisplayMetrics();
                    display.getRealMetrics(realMetrics);

                    boolean bFullscreenLayout = ((realMetrics.widthPixels == mSurface.getWidth()) &&
                            (realMetrics.heightPixels == mSurface.getHeight()));

                    if ((Integer) data == 1) {
                        bShouldWait = !bFullscreenLayout;
                    } else {
                        bShouldWait = bFullscreenLayout;
                    }
                }

                if (bShouldWait && (SDLActivity.getContext() != null)) {
                    synchronized (SDLActivity.getContext()) {
                        try {
                            SDLActivity.getContext().wait(500);
                        } catch (InterruptedException ie) {
                            ie.printStackTrace();
                        }
                    }
                }
            }
        }

        return result;
    }

    public static native String nativeGetVersion();
    public static native int nativeSetupJNI();
    public static native int nativeRunMain(String library, String function, Object arguments);
    public static native void nativeLowMemory();
    public static native void nativeSendQuit();
    public static native void nativeQuit();
    public static native void nativePause();
    public static native void nativeResume();
    public static native void nativeFocusChanged(boolean hasFocus);
    public static native void onNativeDropFile(String filename);
    public static native void nativeSetScreenResolution(int surfaceWidth, int surfaceHeight, int deviceWidth, int deviceHeight, float rate);
    public static native void onNativeResize();
    public static native void onNativeKeyDown(int keycode);
    public static native void onNativeKeyUp(int keycode);
    public static native boolean onNativeSoftReturnKey();
    public static native void onNativeKeyboardFocusLost();
    public static native void onNativeMouse(int button, int action, float x, float y, boolean relative);
    public static native void onNativeTouch(int touchDevId, int pointerFingerId,
                                            int action, float x,
                                            float y, float p);
    public static native void onNativeAccel(float x, float y, float z);
    public static native void onNativeClipboardChanged();
    public static native void onNativeSurfaceCreated();
    public static native void onNativeSurfaceChanged();
    public static native void onNativeSurfaceDestroyed();
    public static native String nativeGetHint(String name);
    public static native boolean nativeGetHintBoolean(String name, boolean default_value);
    public static native void nativeSetenv(String name, String value);
    public static native void onNativeOrientationChanged(int orientation);
    public static native void nativeAddTouch(int touchId, String name);
    public static native void nativePermissionResult(int requestCode, boolean result);
    public static native void onNativeLocaleChanged();

    public static boolean setActivityTitle(String title) {
        return mSingleton.sendCommand(COMMAND_CHANGE_TITLE, title);
    }

    public static void setWindowStyle(boolean fullscreen) {
        mSingleton.sendCommand(COMMAND_CHANGE_WINDOW_STYLE, fullscreen ? 1 : 0);
    }

    public static void setOrientation(int w, int h, boolean resizable, String hint)
    {
        if (mSingleton != null) {
            mSingleton.setOrientationBis(w, h, resizable, hint);
        }
    }

    public void setOrientationBis(int w, int h, boolean resizable, String hint)
    {
        int orientation_landscape = -1;
        int orientation_portrait = -1;

        if (hint.contains("LandscapeRight") && hint.contains("LandscapeLeft")) {
            orientation_landscape = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
        } else if (hint.contains("LandscapeLeft")) {
            orientation_landscape = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        } else if (hint.contains("LandscapeRight")) {
            orientation_landscape = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
        }

        boolean contains_Portrait = hint.contains("Portrait ") || hint.endsWith("Portrait");

        if (contains_Portrait && hint.contains("PortraitUpsideDown")) {
            orientation_portrait = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
        } else if (contains_Portrait) {
            orientation_portrait = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        } else if (hint.contains("PortraitUpsideDown")) {
            orientation_portrait = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
        }

        boolean is_landscape_allowed = (orientation_landscape != -1);
        boolean is_portrait_allowed = (orientation_portrait != -1);
        int req;

        if (!is_portrait_allowed && !is_landscape_allowed) {
            if (resizable) {
                req = ActivityInfo.SCREEN_ORIENTATION_FULL_USER;
            } else {
                req = (w > h ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            }
        } else {
            if (resizable) {
                if (is_portrait_allowed && is_landscape_allowed) {
                    req = ActivityInfo.SCREEN_ORIENTATION_FULL_USER;
                } else {
                    req = (is_landscape_allowed ? orientation_landscape : orientation_portrait);
                }
            } else {
                if (is_portrait_allowed && is_landscape_allowed) {
                    req = (w > h ? orientation_landscape : orientation_portrait);
                } else {
                    req = (is_landscape_allowed ? orientation_landscape : orientation_portrait);
                }
            }
        }

        Log.v(TAG, "setOrientation() requestedOrientation=" + req + " width=" + w +" height="+ h +" resizable=" + resizable + " hint=" + hint);
        mSingleton.setRequestedOrientation(req);
    }

    public static void minimizeWindow() {
        if (mSingleton == null) {
            return;
        }
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mSingleton.startActivity(startMain);
    }

    public static boolean shouldMinimizeOnFocusLoss() {
        return false;
    }

    public static boolean isScreenKeyboardShown()
    {
        if (mTextEdit == null) {
            return false;
        }
        if (!mScreenKeyboardShown) {
            return false;
        }
        InputMethodManager imm = (InputMethodManager) SDL.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        return imm.isAcceptingText();
    }

    public static boolean supportsRelativeMouse()
    {
        if (Build.VERSION.SDK_INT < 27 /* Android 8.1 (O_MR1) */ && isDeXMode()) {
            return false;
        }
        return SDLActivity.getMotionListener().supportsRelativeMouse();
    }

    public static boolean setRelativeMouseEnabled(boolean enabled)
    {
        if (enabled && !supportsRelativeMouse()) {
            return false;
        }
        return SDLActivity.getMotionListener().setRelativeMouseEnabled(enabled);
    }

    public static boolean sendMessage(int command, int param) {
        if (mSingleton == null) {
            return false;
        }
        return mSingleton.sendCommand(command, param);
    }

    public static Context getContext() {
        return SDL.getContext();
    }

    public static boolean isAndroidTV() {
        UiModeManager uiModeManager = (UiModeManager) getContext().getSystemService(UI_MODE_SERVICE);
        if (uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true;
        }
        if (Build.MANUFACTURER.equals("MINIX") && Build.MODEL.equals("NEO-U1")) {
            return true;
        }
        if (Build.MANUFACTURER.equals("Amlogic") && Build.MODEL.equals("X96-W")) {
            return true;
        }
        return Build.MANUFACTURER.equals("Amlogic") && Build.MODEL.startsWith("TV");
    }

    public static double getDiagonal()
    {
        DisplayMetrics metrics = new DisplayMetrics();
        Activity activity = (Activity)getContext();
        if (activity == null) {
            return 0.0;
        }
        activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);

        double dWidthInches = metrics.widthPixels / (double)metrics.xdpi;
        double dHeightInches = metrics.heightPixels / (double)metrics.ydpi;

        return Math.sqrt((dWidthInches * dWidthInches) + (dHeightInches * dHeightInches));
    }

    public static boolean isTablet() {
        return (getDiagonal() >= 7.0);
    }

    public static boolean isChromebook() {
        if (getContext() == null) {
            return false;
        }
        return getContext().getPackageManager().hasSystemFeature("org.chromium.arc.device_management");
    }

    public static boolean isDeXMode() {
        if (Build.VERSION.SDK_INT < 24 /* Android 7.0 (N) */) {
            return false;
        }
        try {
            final Configuration config = getContext().getResources().getConfiguration();
            final Class<?> configClass = config.getClass();
            return configClass.getField("SEM_DESKTOP_MODE_ENABLED").getInt(configClass)
                    == configClass.getField("semDesktopModeEnabled").getInt(config);
        } catch(Exception ignored) {
            return false;
        }
    }

    public static DisplayMetrics getDisplayDPI() {
        return getContext().getResources().getDisplayMetrics();
    }

    public static boolean getManifestEnvironmentVariables() {
        try {
            if (getContext() == null) {
                return false;
            }

            ApplicationInfo applicationInfo = getContext().getPackageManager().getApplicationInfo(getContext().getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = applicationInfo.metaData;
            if (bundle == null) {
                return false;
            }
            String prefix = "SDL_ENV.";
            final int trimLength = prefix.length();
            for (String key : bundle.keySet()) {
                if (key.startsWith(prefix)) {
                    String name = key.substring(trimLength);
                    String value = bundle.get(key).toString();
                    nativeSetenv(name, value);
                }
            }
            return true;
        } catch (Exception e) {
           Log.v(TAG, "exception " + e.toString());
        }
        return false;
    }

    public static View getContentView() {
        return mLayout;
    }

    static class ShowTextInputTask implements Runnable {
        static final int HEIGHT_PADDING = 15;
        public int x, y, w, h;

        public ShowTextInputTask(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;

            if (this.w <= 0) {
                this.w = 1;
            }
            if (this.h + HEIGHT_PADDING <= 0) {
                this.h = 1 - HEIGHT_PADDING;
            }
        }

        @Override
        public void run() {
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(w, h + HEIGHT_PADDING);
            params.leftMargin = x;
            params.topMargin = y;

            if (mTextEdit == null) {
                mTextEdit = new DummyEdit(SDL.getContext());
                mLayout.addView(mTextEdit, params);
            } else {
                mTextEdit.setLayoutParams(params);
            }

            mTextEdit.setVisibility(View.VISIBLE);
            mTextEdit.requestFocus();

            InputMethodManager imm = (InputMethodManager) SDL.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(mTextEdit, 0);

            mScreenKeyboardShown = true;
        }
    }

    public static boolean showTextInput(int x, int y, int w, int h) {
        return mSingleton.commandHandler.post(new ShowTextInputTask(x, y, w, h));
    }

    public static boolean isTextInputEvent(KeyEvent event) {
        if (event.isCtrlPressed()) {
            return false;
        }
        return event.isPrintingKey() || event.getKeyCode() == KeyEvent.KEYCODE_SPACE;
    }

    public static boolean handleKeyEvent(View v, int keyCode, KeyEvent event, InputConnection ic) {
        int deviceId = event.getDeviceId();
        int source = event.getSource();

        if (source == InputDevice.SOURCE_UNKNOWN) {
            InputDevice device = InputDevice.getDevice(deviceId);
            if (device != null) {
                source = device.getSources();
            }
        }

        if (SDLControllerManager.isDeviceSDLJoystick(deviceId)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (SDLControllerManager.onNativePadDown(deviceId, keyCode) == 0) {
                    return true;
                }
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                if (SDLControllerManager.onNativePadUp(deviceId, keyCode) == 0) {
                    return true;
                }
            }
        }

        if ((source & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE) {
            if ((keyCode == KeyEvent.KEYCODE_BACK) || (keyCode == KeyEvent.KEYCODE_FORWARD)) {
                switch (event.getAction()) {
                case KeyEvent.ACTION_DOWN:
                case KeyEvent.ACTION_UP:
                    return true;
                }
            }
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (isTextInputEvent(event)) {
                if (ic != null) {
                    ic.commitText(String.valueOf((char) event.getUnicodeChar()), 1);
                } else {
                    SDLInputConnection.nativeCommitText(String.valueOf((char) event.getUnicodeChar()), 1);
                }
            }
            onNativeKeyDown(keyCode);
            return true;
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            onNativeKeyUp(keyCode);
            return true;
        }

        return false;
    }

    public static Surface getNativeSurface() {
        if (SDLActivity.mSurface == null) {
            return null;
        }
        return SDLActivity.mSurface.getNativeSurface();
    }

    public static void initTouch() {
        int[] ids = InputDevice.getDeviceIds();

        for (int id : ids) {
            InputDevice device = InputDevice.getDevice(id);
            if (device != null && ((device.getSources() & InputDevice.SOURCE_TOUCHSCREEN) == InputDevice.SOURCE_TOUCHSCREEN
                    || device.isVirtual())) {

                int touchDevId = device.getId();
                if (touchDevId < 0) {
                    touchDevId -= 1;
                }
                nativeAddTouch(touchDevId, device.getName());
            }
        }
    }

    protected final int[] messageboxSelection = new int[1];

    public int messageboxShowMessageBox(
            final int flags,
            final String title,
            final String message,
            final int[] buttonFlags,
            final int[] buttonIds,
            final String[] buttonTexts,
            final int[] colors) {

        messageboxSelection[0] = -1;

        if ((buttonFlags.length != buttonIds.length) && (buttonIds.length != buttonTexts.length)) {
            return -1;
        }

        final Bundle args = new Bundle();
        args.putInt("flags", flags);
        args.putString("title", title);
        args.putString("message", message);
        args.putIntArray("buttonFlags", buttonFlags);
        args.putIntArray("buttonIds", buttonIds);
        args.putStringArray("buttonTexts", buttonTexts);
        args.putIntArray("colors", colors);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                messageboxCreateAndShow(args);
            }
        });

        synchronized (messageboxSelection) {
            try {
                messageboxSelection.wait();
            } catch (InterruptedException ex) {
                ex.printStackTrace();
                return -1;
            }
        }

        return messageboxSelection[0];
    }

    protected void messageboxCreateAndShow(Bundle args) {
        int[] colors = args.getIntArray("colors");
        int backgroundColor;
        int textColor;
        int buttonBorderColor;
        int buttonBackgroundColor;
        int buttonSelectedColor;
        if (colors != null) {
            int i = -1;
            backgroundColor = colors[++i];
            textColor = colors[++i];
            buttonBorderColor = colors[++i];
            buttonBackgroundColor = colors[++i];
            buttonSelectedColor = colors[++i];
        } else {
            backgroundColor = Color.TRANSPARENT;
            textColor = Color.TRANSPARENT;
            buttonBorderColor = Color.TRANSPARENT;
            buttonBackgroundColor = Color.TRANSPARENT;
            buttonSelectedColor = Color.TRANSPARENT;
        }

        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        dialog.setTitle(args.getString("title"));
        dialog.setCancelable(false);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface unused) {
                synchronized (messageboxSelection) {
                    messageboxSelection.notify();
                }
            }
        });

        TextView message = new TextView(this);
        message.setGravity(Gravity.CENTER);
        message.setText(args.getString("message"));
        if (textColor != Color.TRANSPARENT) {
            message.setTextColor(textColor);
        }

        int[] buttonFlags = args.getIntArray("buttonFlags");
        int[] buttonIds = args.getIntArray("buttonIds");
        String[] buttonTexts = args.getStringArray("buttonTexts");

        final SparseArray<Button> mapping = new SparseArray<Button>();

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        for (int i = 0; i < buttonTexts.length; ++i) {
            Button button = new Button(this);
            final int id = buttonIds[i];
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    messageboxSelection[0] = id;
                    dialog.dismiss();
                }
            });
            if (buttonFlags[i] != 0) {
                if ((buttonFlags[i] & 0x00000001) != 0) {
                    mapping.put(KeyEvent.KEYCODE_ENTER, button);
                }
                if ((buttonFlags[i] & 0x00000002) != 0) {
                    mapping.put(KeyEvent.KEYCODE_ESCAPE, button); 
                }
            }
            button.setText(buttonTexts[i]);
            if (textColor != Color.TRANSPARENT) {
                button.setTextColor(textColor);
            }
            if (buttonBackgroundColor != Color.TRANSPARENT) {
                Drawable drawable = button.getBackground();
                if (drawable == null) {
                    button.setBackgroundColor(buttonBackgroundColor);
                } else {
                    drawable.setColorFilter(buttonBackgroundColor, PorterDuff.Mode.MULTIPLY);
                }
            }
            buttons.addView(button);
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(message);
        content.addView(buttons);
        if (backgroundColor != Color.TRANSPARENT) {
            content.setBackgroundColor(backgroundColor);
        }

        dialog.setView(content);
        dialog.setOnKeyListener(new Dialog.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface d, int keyCode, KeyEvent event) {
                Button button = mapping.get(keyCode);
                if (button != null) {
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        button.performClick();
                    }
                    return true; 
                }
                return false;
            }
        });

        dialog.show();
    }

    private final Runnable rehideSystemUi = new Runnable() {
        @Override
        public void run() {
            if (Build.VERSION.SDK_INT >= 19 /* Android 4.4 (KITKAT) */) {
                int flags = View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.INVISIBLE;

                SDLActivity.this.getWindow().getDecorView().setSystemUiVisibility(flags);
            }
        }
    };

    public void onSystemUiVisibilityChange(int visibility) {
        if (SDLActivity.mFullscreenModeActive && ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0 || (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0)) {
            Handler handler = getWindow().getDecorView().getHandler();
            if (handler != null) {
                handler.removeCallbacks(rehideSystemUi); 
                handler.postDelayed(rehideSystemUi, 2000);
            }
        }
    }

    public static boolean clipboardHasText() {
        return mClipboardHandler.clipboardHasText();
    }

    public static String clipboardGetText() {
        return mClipboardHandler.clipboardGetText();
    }

    public static void clipboardSetText(String string) {
        mClipboardHandler.clipboardSetText(string);
    }

    public static int createCustomCursor(int[] colors, int width, int height, int hotSpotX, int hotSpotY) {
        Bitmap bitmap = Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888);
        ++mLastCursorID;

        if (Build.VERSION.SDK_INT >= 24 /* Android 7.0 (N) */) {
            try {
                mCursors.put(mLastCursorID, PointerIcon.create(bitmap, hotSpotX, hotSpotY));
            } catch (Exception e) {
                return 0;
            }
        } else {
            return 0;
        }
        return mLastCursorID;
    }

    public static void destroyCustomCursor(int cursorID) {
        if (Build.VERSION.SDK_INT >= 24 /* Android 7.0 (N) */) {
            try {
                mCursors.remove(cursorID);
            } catch (Exception e) {
            }
        }
        return;
    }

    public static boolean setCustomCursor(int cursorID) {
        if (Build.VERSION.SDK_INT >= 24 /* Android 7.0 (N) */) {
            try {
                mSurface.setPointerIcon(mCursors.get(cursorID));
            } catch (Exception e) {
                return false;
            }
        } else {
            return false;
        }
        return true;
    }

    public static boolean setSystemCursor(int cursorID) {
        int cursor_type = 0; 
        switch (cursorID) {
        case SDL_SYSTEM_CURSOR_ARROW:
            cursor_type = 1000; 
            break;
        case SDL_SYSTEM_CURSOR_IBEAM:
            cursor_type = 1008; 
            break;
        case SDL_SYSTEM_CURSOR_WAIT:
            cursor_type = 1004; 
            break;
        case SDL_SYSTEM_CURSOR_CROSSHAIR:
            cursor_type = 1007; 
            break;
        case SDL_SYSTEM_CURSOR_WAITARROW:
            cursor_type = 1004; 
            break;
        case SDL_SYSTEM_CURSOR_SIZENWSE:
            cursor_type = 1017; 
            break;
        case SDL_SYSTEM_CURSOR_SIZENESW:
            cursor_type = 1016; 
            break;
        case SDL_SYSTEM_CURSOR_SIZEWE:
            cursor_type = 1014; 
            break;
        case SDL_SYSTEM_CURSOR_SIZENS:
            cursor_type = 1015; 
            break;
        case SDL_SYSTEM_CURSOR_SIZEALL:
            cursor_type = 1020; 
            break;
        case SDL_SYSTEM_CURSOR_NO:
            cursor_type = 1012; 
            break;
        case SDL_SYSTEM_CURSOR_HAND:
            cursor_type = 1002; 
            break;
        }
        if (Build.VERSION.SDK_INT >= 24 /* Android 7.0 (N) */) {
            try {
                mSurface.setPointerIcon(PointerIcon.getSystemIcon(SDL.getContext(), cursor_type));
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    public static void requestPermission(String permission, int requestCode) {
        if (Build.VERSION.SDK_INT < 23 /* Android 6.0 (M) */) {
            nativePermissionResult(requestCode, true);
            return;
        }

        Activity activity = (Activity)getContext();
        if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{permission}, requestCode);
        } else {
            nativePermissionResult(requestCode, true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        boolean result = (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED);
        nativePermissionResult(requestCode, result);
    }

    public static int openURL(String url)
    {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse(url));

            int flags = Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
            if (Build.VERSION.SDK_INT >= 21 /* Android 5.0 (LOLLIPOP) */) {
                flags |= Intent.FLAG_ACTIVITY_NEW_DOCUMENT;
            } else {
                flags |= Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET;
            }
            i.addFlags(flags);

            mSingleton.startActivity(i);
        } catch (Exception ex) {
            return -1;
        }
        return 0;
    }

    public static int showToast(String message, int duration, int gravity, int xOffset, int yOffset)
    {
        if(null == mSingleton) {
            return - 1;
        }

        try
        {
            class OneShotTask implements Runnable {
                String mMessage;
                int mDuration;
                int mGravity;
                int mXOffset;
                int mYOffset;

                OneShotTask(String message, int duration, int gravity, int xOffset, int yOffset) {
                    mMessage  = message;
                    mDuration = duration;
                    mGravity  = gravity;
                    mXOffset  = xOffset;
                    mYOffset  = yOffset;
                }

                public void run() {
                    try
                    {
                        Toast toast = Toast.makeText(mSingleton, mMessage, mDuration);
                        if (mGravity >= 0) {
                            toast.setGravity(mGravity, mXOffset, mYOffset);
                        }
                        toast.show();
                    } catch(Exception ex) {
                        Log.e(TAG, ex.getMessage());
                    }
                }
            }
            mSingleton.runOnUiThread(new OneShotTask(message, duration, gravity, xOffset, yOffset));
        } catch(Exception ex) {
            return -1;
        }
        return 0;
    }
}

class SDLMain implements Runnable {
    @Override
    public void run() {
        String library = SDLActivity.mSingleton.getMainSharedObject();
        String function = SDLActivity.mSingleton.getMainFunction();
        String[] arguments = SDLActivity.mSingleton.getArguments();

        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DISPLAY);
        } catch (Exception e) {
            Log.v("SDL", "modify thread properties failed " + e.toString());
        }

        Log.v("SDL", "Running main function " + function + " from library " + library);

        SDLActivity.nativeRunMain(library, function, arguments);

        Log.v("SDL", "Finished main function");

        if (SDLActivity.mSingleton != null && !SDLActivity.mSingleton.isFinishing()) {
            SDLActivity.mSDLThread = null;
            SDLActivity.mSingleton.finish();
        } 

    }
}

class DummyEdit extends View implements View.OnKeyListener {
    InputConnection ic;

    public DummyEdit(Context context) {
        super(context);
        setFocusableInTouchMode(true);
        setFocusable(true);
        setOnKeyListener(this);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return SDLActivity.handleKeyEvent(v, keyCode, event, ic);
    }

    @Override
    public boolean onKeyPreIme (int keyCode, KeyEvent event) {
        if (event.getAction()==KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
            if (SDLActivity.mTextEdit != null && SDLActivity.mTextEdit.getVisibility() == View.VISIBLE) {
                SDLActivity.onNativeKeyboardFocusLost();
            }
        }
        return super.onKeyPreIme(keyCode, event);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        ic = new SDLInputConnection(this, true);

        outAttrs.inputType = InputType.TYPE_CLASS_TEXT |
                             InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI |
                              EditorInfo.IME_FLAG_NO_FULLSCREEN /* API 11 */;

        return ic;
    }
}

class SDLInputConnection extends BaseInputConnection {

    protected EditText mEditText;
    protected String mCommittedText = "";

    public SDLInputConnection(View targetView, boolean fullEditor) {
        super(targetView, fullEditor);
        mEditText = new EditText(SDL.getContext());
    }

    @Override
    public Editable getEditable() {
        return mEditText.getEditableText();
    }

    @Override
    public boolean sendKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
            if (SDLActivity.onNativeSoftReturnKey()) {
                return true;
            }
        }

        return super.sendKeyEvent(event);
    }

    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
        if (!super.commitText(text, newCursorPosition)) {
            return false;
        }
        updateText();
        return true;
    }

    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        if (!super.setComposingText(text, newCursorPosition)) {
            return false;
        }
        updateText();
        return true;
    }

    @Override
    public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        if (Build.VERSION.SDK_INT <= 29 /* Android 10.0 (Q) */) {
            if (beforeLength > 0 && afterLength == 0) {
                while (beforeLength-- > 0) {
                    nativeGenerateScancodeForUnichar('\b');
                }
                return true;
           }
        }

        if (!super.deleteSurroundingText(beforeLength, afterLength)) {
            return false;
        }
        updateText();
        return true;
    }

    protected void updateText() {
        final Editable content = getEditable();
        if (content == null) {
            return;
        }

        String text = content.toString();
        int compareLength = Math.min(text.length(), mCommittedText.length());
        int matchLength, offset;

        for (matchLength = 0; matchLength < compareLength; ) {
            int codePoint = mCommittedText.codePointAt(matchLength);
            if (codePoint != text.codePointAt(matchLength)) {
                break;
            }
            matchLength += Character.charCount(codePoint);
        }
        for (offset = matchLength; offset < mCommittedText.length(); ) {
            int codePoint = mCommittedText.codePointAt(offset);
            nativeGenerateScancodeForUnichar('\b');
            offset += Character.charCount(codePoint);
        }

        if (matchLength < text.length()) {
            String pendingText = text.subSequence(matchLength, text.length()).toString();
            for (offset = 0; offset < pendingText.length(); ) {
                int codePoint = pendingText.codePointAt(offset);
                if (codePoint == '\n') {
                    if (SDLActivity.onNativeSoftReturnKey()) {
                        return;
                    }
                }
                if (codePoint < 128) {
                    nativeGenerateScancodeForUnichar((char)codePoint);
                }
                offset += Character.charCount(codePoint);
            }
            SDLInputConnection.nativeCommitText(pendingText, 0);
        }
        mCommittedText = text;
    }

    public static native void nativeCommitText(String text, int newCursorPosition);

    public static native void nativeGenerateScancodeForUnichar(char c);
}

class SDLClipboardHandler implements
    ClipboardManager.OnPrimaryClipChangedListener {

    protected ClipboardManager mClipMgr;

    SDLClipboardHandler() {
       mClipMgr = (ClipboardManager) SDL.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
       mClipMgr.addPrimaryClipChangedListener(this);
    }

    public boolean clipboardHasText() {
       return mClipMgr.hasPrimaryClip();
    }

    public String clipboardGetText() {
        ClipData clip = mClipMgr.getPrimaryClip();
        if (clip != null) {
            ClipData.Item item = clip.getItemAt(0);
            if (item != null) {
                CharSequence text = item.getText();
                if (text != null) {
                    return text.toString();
                }
            }
        }
        return null;
    }

    public void clipboardSetText(String string) {
       mClipMgr.removePrimaryClipChangedListener(this);
       ClipData clip = ClipData.newPlainText(null, string);
       mClipMgr.setPrimaryClip(clip);
       mClipMgr.addPrimaryClipChangedListener(this);
    }

    @Override
    public void onPrimaryClipChanged() {
        SDLActivity.onNativeClipboardChanged();
    }
}
