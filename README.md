# I.K.E.M.E.N-Go for Android

## Instructions for building (debug)
增加了自定义布局系统
### Requirements: Android Studio, Android NDK r27d.

1. Build the main I.K.E.M.E.N-Go engine for Android using Android NDK r27d. You will need to point the environment variable `ANDROID_NDK_HOME` to your NDK location before running `build/build.sh android` on Linux or macOS (builds from Windows currently not supported).
2. Place all the lib .so files from the `lib/` folder and the `build/libmain.so` into `src/main/jniLibs/arm64-v8a/` folder.
3. Place all the engine assets in `src/main/assets` exactly as defined in `src/main/assets/manifest.txt`. You may wish to generate your own manifest.txt for your own game files, but a default is included in this repository.
4. Open a terminal to the root of this project in Android Studio.
5. Run `./gradlew clean assembleDebug`
6. The APK is now in `app/build/outputs/apk/debug/app-debug.apk`.
7. (Optional) Install to your Android device by running the command `adb install -r app/build/outputs/apk/app-debug.apk`.

### LICENSE NOTICE
I.K.E.M.E.N-Go specific logic copyrights:
(C) 2026 Jesuszilla
(C) 2026 Sohil876

SDLActivity was modified and AssetExtractor, ControllerOverlay, and JoystickOverlay and related XML files were added to accomplish this purpose.

Special thanks:
* Lasombra Demon - Spanish translations
* MotorRoach - Portuguese translations
* Vans - Chinese & Japanese translation feedback
* dionednd - Filipino & Meranaw translations

The original license is included in LICENSE.txt
