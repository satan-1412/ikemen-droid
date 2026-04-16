LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

# 🚨 核心替换：将废弃的 main 模板替换为我们的真实 SFF 解析引擎
LOCAL_MODULE := ikemen_sff_codec

# 🚨 核心替换：指向咱们的 C++ 性能怪兽文件
LOCAL_SRC_FILES := sff_codec.cpp

# 补充基础运行库，剥离不需要源码编译的 SDL2（它已经是预编译好的了）
LOCAL_LDLIBS := -lGLESv1_CM -lGLESv2 -lOpenSLES -llog -landroid -lEGL -lz

# =========================================================
# [极客优化核心区] 基于不同 CPU 架构的定制化编译参数
# （已完美保留你的所有极限优化参数，直接作用于 SFF 解析！）
# =========================================================

# 1. 全局基础优化（所有架构共享）：
# -fvisibility=hidden: 隐藏不需要导出的 JNI 符号，减小 .so 库体积，大幅加快冷启动加载速度
# -fno-strict-aliasing: 兼容老旧 C 语言代码的指针转换规则，防止编译器过度优化导致的玄学闪退
# -fdata-sections -ffunction-sections: 配合链接器剔除无用的死代码
# -fomit-frame-pointer: 释放帧指针寄存器，为底层计算提供额外的可用寄存器，全局提升执行效率
LOCAL_CFLAGS := -fvisibility=hidden -fno-strict-aliasing -fdata-sections -ffunction-sections -fomit-frame-pointer

# 2. 架构分流：获取当前正在编译的 CPU 架构
ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
    # -----------------------------------------------------
    # 【64位 (arm64-v8a)】：激进性能模式 (渲染与运算上限极高)
    # -----------------------------------------------------
    # -O3: 开启编译器最高等级优化，牺牲编译时间换取最高运行帧率
    # -ffast-math: 允许牺牲肉眼不可见的浮点精度，换取 OpenGL 矩阵计算和碰撞检测性能的巨大提升
    # -funroll-loops: 循环展开，减少底层数据拷贝时的分支跳转开销
    # -flto: 开启链接期全局优化 (Link-Time Optimization)，极限压缩体积并优化跨文件函数调用
    # -ftree-vectorize: 开启 SIMD 向量化，让 CPU 单指令处理多组数据
    LOCAL_CFLAGS += -O3 -ffast-math -funroll-loops -flto -ftree-vectorize
    
    # 链接器激进优化：回收未使用的代码段，并在链接期配合 LTO 进行深度性能压榨
    LOCAL_LDFLAGS += -Wl,--gc-sections -Wl,-O3 -flto
else ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
    # -----------------------------------------------------
    # 【32位 (armeabi-v7a)】：极度求稳模式 (兼顾老设备与省电)
    # -----------------------------------------------------
    # -Os: 偏向优化应用体积。较小体积能提高老旧设备 CPU 的指令缓存命中率，减少卡顿和发热。
    LOCAL_CFLAGS += -Os
    
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
