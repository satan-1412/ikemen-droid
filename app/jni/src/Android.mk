LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := main

SDL_PATH := ../SDL

LOCAL_C_INCLUDES := $(LOCAL_PATH)/$(SDL_PATH)/include

# Add your application source files here...
LOCAL_SRC_FILES := YourSourceHere.c

LOCAL_SHARED_LIBRARIES := SDL2

# 补充了原版中的底层库，并增加了 EGL 以保证更高版本 OpenGL 的上下文兼容性
LOCAL_LDLIBS := -lGLESv1_CM -lGLESv2 -lOpenSLES -llog -landroid -lEGL

# =========================================================
# [极客优化核心区] 基于不同 CPU 架构的定制化编译参数
# =========================================================

# 1. 全局基础优化（所有架构共享）：
# -fvisibility=hidden: 隐藏不需要导出的 JNI 符号，减小 .so 库体积，大幅加快冷启动加载速度
# -fno-strict-aliasing: 兼容老旧 C 语言代码的指针转换规则，防止编译器过度优化导致的玄学闪退
# -fdata-sections -ffunction-sections: 配合链接器剔除无用的死代码
LOCAL_CFLAGS := -fvisibility=hidden -fno-strict-aliasing -fdata-sections -ffunction-sections

# 2. 架构分流：获取当前正在编译的 CPU 架构
ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
    # -----------------------------------------------------
    # 【64位 (arm64-v8a)】：激进性能模式 (渲染与运算上限极高)
    # -----------------------------------------------------
    # -O3: 开启编译器最高等级优化，牺牲编译时间换取最高运行帧率
    # -ffast-math: 允许编译器牺牲极小（肉眼不可见）的浮点精度，换取 OpenGL 渲染矩阵计算和物理运算性能的巨大提升
    # -funroll-loops: 循环展开，减少底层数据拷贝时的分支跳转开销
    LOCAL_CFLAGS += -O3 -ffast-math -funroll-loops
    
    # 链接器激进优化：回收未使用的代码段，并在链接期进行深度性能压榨
    LOCAL_LDFLAGS += -Wl,--gc-sections -Wl,-O3
else ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
    # -----------------------------------------------------
    # 【32位 (armeabi-v7a)】：极度求稳模式 (兼顾老设备与省电)
    # -----------------------------------------------------
    # -Os: 偏向优化应用体积。较小的体积能提高老旧设备 CPU 的指令缓存 (I-Cache) 命中率，反而能减少卡顿和发热
    # -mfpu=neon: 强制启用 NEON 硬件矢量加速指令集，加速音频流 (OpenSLES) 和纹理解码
    # -mfloat-abi=softfp: 保证底层硬件浮点运算与各种魔改老旧 Android 系统的完美兼容，绝不崩溃
    LOCAL_CFLAGS += -Os -mfpu=neon -mfloat-abi=softfp
    
    # 链接器求稳修复：Wl,--fix-cortex-a8 用于修复早期 32 位处理器的硬件级指令死锁 BUG
    LOCAL_LDFLAGS += -Wl,--gc-sections -Wl,--fix-cortex-a8
else
    # -----------------------------------------------------
    # 【其他架构 (如 x86_64 模拟器环境)】：平衡模式
    # -----------------------------------------------------
    LOCAL_CFLAGS += -O2
    LOCAL_LDFLAGS += -Wl,--gc-sections
endif

include $(BUILD_SHARED_LIBRARY)
