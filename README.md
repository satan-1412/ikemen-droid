# 📱 I.K.E.M.E.N-Droid-Pro

![Platform](https://img.shields.io/badge/Platform-Android%205.0+-3DDC84?logo=android)
![Arch](https://img.shields.io/badge/Arch-32bit%20%2F%2064bit-blue)
![License](https://img.shields.io/badge/License-MIT-green)

*(Scroll down for the Chinese version / 中文版请向下滚动)*

---

##  English Version

A heavily modified and enhanced fork of the original Ikemen-Go Android port. We have completely rewritten the UI and input systems from scratch, significantly outperforming the original version in terms of mobile experience and performance.

### ✨ Original vs. Pro Version (Advantages)

* **Input & Control System:**
  * *Original:* Uses a legacy, static XML-based (`ControllerOverlay`) layout resulting in high touch latency and stiff controls.
  * *Pro Version:* **Completely Original Java UI Architecture**. Implemented a custom `DynamicGamepadView` that directly simulates underlying keyboard inputs. This provides zero-delay, smooth touch responsiveness, and a vastly superior control feel.
* **External Controller Support:**
  * *Original:* Lacks proper native support for external gamepads.
  * *Pro Version:* **Native Gamepad Integration**. Added comprehensive, native mapping logic for external physical HID controllers and gamepads.
* **Large Modpack Compatibility:**
  * *Original:* Prone to crashing and instability when loading large custom MUGEN/Ikemen builds due to unoptimized file reading.
  * *Pro Version:* **Optimized File I/O**. Completely reworked the `AssetExtractor` and file path logic, ensuring highly stable loading of massive custom builds and modpacks directly from external storage.
* **Broad Architecture Support:** Maintains comprehensive support for both **32-bit (armeabi-v7a)** and **64-bit (arm64-v8a)**, keeping older devices alive with a minimum requirement of Android 5.0.

### 🚀 Development Roadmap

* **[WIP] Desktop System View:** Currently working on a built-in desktop-style UI layout to make file management and character selection easier within the app.
* **[Planned] Lightweight Tool (ff):** Planning to recreate a simple, lightweight mobile tool for basic parameter tweaking and engine debugging.

### 📦 Instructions for building (debug)

**Requirements:** Android Studio, Android NDK r27d.

1. Build the main I.K.E.M.E.N-Go engine for Android using Android NDK r27d. Point the environment variable `ANDROID_NDK_HOME` to your NDK location before running `build/build.sh android` on Linux or macOS (Windows builds currently not supported).
2. Place all the lib `.so` files from the `lib/` folder and `build/libmain.so` into `src/main/jniLibs/arm64-v8a/` (and `armeabi-v7a/` for 32-bit).
3. Place game assets in `src/main/assets` exactly as defined in `src/main/assets/manifest.txt`. 
4. Open a terminal to the root of this project in Android Studio.
5. Run `./gradlew clean assembleDebug`
6. The output APK will be in `app/build/outputs/apk/debug/app-debug.apk`.

### ⚖️ LICENSE NOTICE & Credits

This project is a modified fork of I.K.E.M.E.N-Go.

* **Engine Core Logic:** (C) 2026 Jesuszilla, (C) 2026 Sohil876.
* **New UI & Input System:** This version features a **completely original UI architecture** and input mapping system developed specifically for this Pro fork, entirely replacing and deprecating the legacy Android port components.

The original license is included in `LICENSE.txt`.

<br><br>

---
---

##  中文版

本项目基于原版 Ikemen-Go 安卓端进行了深度重构与增强。我们从零开始彻底重写了 UI 与输入系统，在移动端操作体验、外设兼容性以及大型整合包的支持上全面超越原版。

### ✨ 原版与 Pro 版优势对比

* **按键与输入系统：**
  * *原版：* 采用老旧的静态 XML (`ControllerOverlay`) 布局，触控延迟高，手感僵硬。
  * *Pro 版：* **完全自主开发的 Java UI 架构**。引入了自定义的 `DynamicGamepadView` 视图，通过底层直接模拟键盘输入，实现了零延迟、极致顺滑的触控反馈，大幅提升了格斗游戏的手感。
* **外设控制支持：**
  * *原版：* 缺乏完善的原生手柄支持。
  * *Pro 版：* **原生手柄支持**。完美接入并重写了外部物理手柄（HID 控制器）的底层映射逻辑，即插即用。
* **大型整合包兼容性：**
  * *原版：* 在读取大型完整整合包时，极易因内存溢出或 I/O 效率低下导致闪退。
  * *Pro 版：* **极致优化的 I/O 逻辑**。重构了 `AssetExtractor` 与文件路径读取逻辑，现已支持从外置存储极其稳定地直读超大型游戏整合包。
* **兼顾老旧机型：** 在完美支持 **64位 (arm64-v8a)** 的同时，保留了对 **32位 (armeabi-v7a)** 架构的支持，系统最低要求仅为 Android 5.0。

### 🚀 开发计划

* **[开发中] 引擎桌面模式：** 正在尝试为引擎编写一个内嵌的桌面风格 UI 视图，旨在方便玩家直接在应用内管理文件和选择内容。
* **[计划中] 轻量级调试工具 (ff)：** 打算在后期尝试复刻一个简易、轻型的手机端工具，方便进行一些基础的参数修改和日常调试。

### 📦 编译与构建指南

**环境要求：** Android Studio，Android NDK r27d。

1. 在 Linux 或 macOS 下，使用 Android NDK r27d 编译核心引擎。运行 `build/build.sh android` 前需配置好 `ANDROID_NDK_HOME` 环境变量（暂不支持 Windows 构建）。
2. 将生成的 `.so` 库文件分类放入 `src/main/jniLibs/arm64-v8a/` 与 `armeabi-v7a/` 目录中。
3. 按照 `src/main/assets/manifest.txt` 中的定义，将游戏资源文件放入 `src/main/assets`。
4. 在 Android Studio 的终端中定位到项目根目录。
5. 执行打包命令：`./gradlew clean assembleDebug`
6. 编译生成的 APK 位于：`app/build/outputs/apk/debug/app-debug.apk`。

### ⚖️ 版权与致谢声明

本项目为 I.K.E.M.E.N-Go 的修改分支：

* **引擎核心逻辑版权：** (C) 2026 Jesuszilla, (C) 2026 Sohil876。
* **全新 UI 与输入系统：** 本项目采用了**完全自主开发的 UI 架构**与输入映射系统，已彻底弃用并替换了原版安卓移植的所有相关前端组件。

原始核心引擎许可协议详见 `LICENSE.txt`。
