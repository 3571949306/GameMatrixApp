@echo off
rem Change to project directory
cd /d d:\kaifa\GameCenterApp

rem Set JDK path
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "PATH=%JAVA_HOME%\bin;%PATH%"

rem Set Android SDK path for adb
set "ANDROID_SDK_ROOT=C:\Users\tcw\AppData\Local\Android\Sdk"
set "PATH=%ANDROID_SDK_ROOT%\platform-tools;%PATH%"

rem Build debug APK
call gradlew.bat assembleDebug
if errorlevel 1 (
    echo [ERROR] Gradle build failed
    exit /b 1
)

rem Install APK to emulator (assumes emulator is running and accessible)
adb install -r app\build\outputs\apk\debug\app-debug.apk
if errorlevel 1 (
    echo [ERROR] ADB install failed
    exit /b 1
)

rem Launch Game2048 via DynamicGameActivity
adb shell am start -n com.gamecenter.app/.DynamicGameActivity -e gameId 2048
if errorlevel 1 (
    echo [ERROR] ADB launch failed
    exit /b 1
)

echo [SUCCESS] Build, install, and launch completed.
