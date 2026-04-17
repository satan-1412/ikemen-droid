# 📱 I.K.E.M.E.N-Droid-Pro (Enhanced Edition)

![Build Status](https://img.shields.io/badge/Build-Success-brightgreen)
![Platform](https://img.shields.io/badge/Platform-Android%205.0+-3DDC84?logo=android)
![Arch](https://img.shields.io/badge/Arch-32bit%20%2F%2064bit-blue)
![Roadmap](https://img.shields.io/badge/Roadmap-Desktop%20%26%20FF%20Lite-orange)
![License](https://img.shields.io/badge/License-MIT-green)

> **EN:** A professional-grade, high-performance reconstruction of the Ikemen-Go Android engine. Born from the frustration of mobile limitations, this Pro version aims to bridge the gap between PC-level customizability and mobile portability.
>
> **CN:** 这是一个专业级、高性能的 Ikemen-Go 安卓引擎重构版本。诞生于对移动端诸多限制的痛点，本 Pro 版致力于打破系统壁垒，让安卓端拥有媲美 PC 级的自定义自由度与流畅体验。

---

## 🥊 Upstream vs Pro: 解决痛点与技术跨越

The original Android port provided a solid foundation, but left many creators and players frustrated. Here is how **Ikemen-Droid-Pro** solves these legacy issues:
原版安卓端提供了优秀的基础框架，但在实际游玩与修改中存在诸多痛点。以下是本 **Pro 版** 带来的核心变革：

| Feature (特性) | Original Upstream (官方原版痛点) | Ikemen-Droid-Pro (核心优化) |
| :--- | :--- | :--- |
| **Input Latency<br>(输入延迟)** | 🛑 Rigid XML UI (`ControllerOverlay`), prone to touch delays and hard to customize. <br>*(老旧的 XML 布局，触控存在粘滞感，且难以调整按键位置。)* | 🚀 **Dynamic Java View:** Direct keyboard simulation via custom Java architecture. Zero-delay inputs. <br>*(弃用 XML，采用动态 Java 视图直接模拟键盘指令，实现电竞级零延迟。)* |
| **Controllers<br>(手柄适配)** | 🛑 Relies heavily on third-party screen mapping apps. <br>*(极度依赖第三方屏幕映射软件，体验割裂。)* | 🚀 **Native Gamepad:** Built-in logic for physical HID controllers and gamepads. <br>*(原生集成物理手柄与控制器识别逻辑，即插即玩。)* |
| **Modpacks<br>(大型整合包)** | 🛑 Limited file reading scope; crashes easily on massive complete builds. <br>*(文件读取限制多，加载数百个角色的大型整合包极易闪退。)* | 🚀 **Advanced I/O:** Optimized `AssetExtractor` for direct and stable loading of massive external modpacks. <br>*(优化文件 IO 与解包逻辑，稳定直读外置超大整合包。)* |
| **Architecture<br>(架构兼容)** | 🛑 Primarily targets modern 64-bit devices. <br>*(偏向于现代高版本 64位 设备，旧机型易被淘汰。)* | 🚀 **Universal Arch:** Full support for both **32-bit (armeabi-v7a)** and **64-bit (arm64-v8a)**, Android 5.0+. <br>*(兼顾情怀，完美支持 32位 与 64位 架构，最低支持安卓 5.0。)* |

---

## 🚀 Future Roadmap: Desktop Mode & Built-in Tools (开发路线图)

We are not just making a game player; we are building an engine workstation for Android. 
我们不仅仅是在做一个游戏播放器，更是在打造一个移动端的引擎工作站。

* 🖥️ **Desktop System UI (桌面模式视图) - [WIP / 预研中]**
  * *EN:* A revolutionary desktop-style UI running inside the engine, designed to manage files, tweak settings, and browse characters without leaving the app.
  * *CN:* 引擎内嵌的革命性桌面风格 UI，让你无需切出应用即可管理文件、调整配置。
* 🛠️ **Fighter Factory Lite (简易版 FF 调试工具) - [Concept / 概念预告]**
  * *EN:* Attempting to recreate a simplified version of the legendary "Fighter Factory" directly on Android. Expect basic tools for editing `.cns`, checking hitboxes, and managing sprite palettes on the go.
  * *CN:* **重磅预告：** 尝试在移动端复刻一个简易版的“梦工厂” (Fighter Factory)。未来你将有望直接在手机上修改 `.cns` 代码、查看 Hitbox 碰撞框以及调试基础素材！

---

## 🛠️ Build Instructions / 构建指南

### Requirements / 环境
* Android Studio
* Android NDK r27d

### Steps / 编译步骤
1.  **[EN]** Build the core engine using `build/build.sh android` on Linux/macOS (set `ANDROID_NDK_HOME`).
    **[CN]** 在 Linux/macOS 环境下运行 `build/build.sh android` 编译核心（需提前配置 `ANDROID_NDK_HOME`）。
2.  **[EN]** Place all generated lib `.so` files into `src/main/jniLibs/arm64-v8a/` (and `armeabi-v7a/` for 32-bit).
    **[CN]** 将生成的 `.so` 库文件分类放入 `src/main/jniLibs/arm64-v8a/` 与 `armeabi-v7a/` 目录中。
3.  **[EN]** Put game assets in `src/main/assets` and generate a matching `manifest.txt`.
    **[CN]** 将整合包资源放入 `src/main/assets` 并生成对应的 `manifest.txt` 文件。
4.  **[EN]** Run `./gradlew clean assembleDebug` via terminal.
    **[CN]** 执行 `./gradlew clean assembleDebug` 开始打包。
5.  **[EN]** Output APK will be located at `app/build/outputs/apk/debug/`.
    **[CN]** 获取你专属的 Pro 版 APK 位于 `app/build/outputs/apk/debug/`。

---

## ⚖️ License & Credits / 版权与致谢

This fork respects and retains the core open-source logic from the upstream repository. 
本项目严格遵守并保留上游原版仓库的开源协议与核心代码版权。

**Original I.K.E.M.E.N-Go specific logic copyrights:**
* (C) 2026 Jesuszilla & Sohil876

**Pro Version Revisions & Additions:**
* Modifications to Input Architecture (Java Overhaul), File IO, and UI logic are maintained by this fork.

*Special thanks to the original Android port translators (Lasombra Demon, MotorRoach, Vans, dionednd) and the entire Ikemen-Engine community.*
