@echo off
REM ====================================================================
REM GameMatrix App 一键构建 + 安装 + 启动脚本（Windows）
REM 用法: scripts\install_and_run.bat [package_activity]
REM 示例: scripts\install_and_run.bat                  (默认启动主界面)
REM       scripts\install_and_run.bat chinesechess      (启动中国象棋)
REM ====================================================================
setlocal enabledelayedexpansion

REM =============== 配置 ===============
set "PROJECT_DIR=%~dp0.."
set "APK_PATH=%PROJECT_DIR%\app\build\outputs\apk\debug\app-debug.apk"
set "SERIAL=emulator-5554"
set "PKG=com.gamecenter.app"
set "DEFAULT_ACTIVITY=.SplashActivity"
set "SPECIFIC_LAUNCH=%~1"
set "GRADLE_OPTS=-PautoBumpVersion=false --no-daemon --console=plain"

REM =============== 颜色支持（可选）===============
echo ============================================================
echo  GameMatrixApp 一键构建 + 安装 + 启动
echo  项目: %PROJECT_DIR%
echo  目标: %SERIAL%
echo  APK:  %APK_PATH%
echo ============================================================

REM =============== 0. 检查 emulator ===============
echo.
echo [0/4] 检查 emulator 连接状态...
adb devices | findstr /R "%SERIAL%.*device" >nul
if errorlevel 1 (
    echo [错误] 未发现已连接设备 %SERIAL%
    echo 当前设备列表:
    adb devices
    exit /b 1
)
echo [OK] %SERIAL% 已连接

REM =============== 1. 构建 Debug APK ===============
echo.
echo [1/4] 构建 Debug APK...
cd /d "%PROJECT_DIR%"
call gradlew.bat :app:assembleDebug %GRADLE_OPTS%
if errorlevel 1 (
    echo [错误] 构建失败
    exit /b 1
)
echo [OK] 构建成功

REM =============== 2. 检查 APK ===============
echo.
echo [2/4] 检查 APK 产物...
if not exist "%APK_PATH%" (
    echo [错误] APK 未生成: %APK_PATH%
    exit /b 1
)
for %%A in ("%APK_PATH%") do echo [OK] APK 大小: %%~zA bytes

REM =============== 3. 安装到 emulator ===============
echo.
echo [3/4] 安装到 %SERIAL%...
adb -s %SERIAL% install -r "%APK_PATH%"
if errorlevel 1 (
    echo [错误] 安装失败
    exit /b 1
)
echo [OK] 安装成功

REM =============== 4. 启动应用 ===============
echo.
echo [4/4] 启动应用...
if "%SPECIFIC_LAUNCH%"=="" (
    REM 默认启动 SplashActivity（LAUNCHER intent-filter 始终可访问）
    set "LAUNCH_ACT=%DEFAULT_ACTIVITY%"
) else (
    REM 包名 → Activity 类名映射表（与 sh 版本同步）
    if /I "%SPECIFIC_LAUNCH%"=="chinesechess" set "CLASS_NAME=ChineseChessActivity"
    if /I "%SPECIFIC_LAUNCH%"=="game2048" set "CLASS_NAME=Game2048Activity"
    if /I "%SPECIFIC_LAUNCH%"=="doudizhu" set "CLASS_NAME=DouDiZhuActivity"
    if /I "%SPECIFIC_LAUNCH%"=="gomoku" set "CLASS_NAME=GomokuActivity"
    if /I "%SPECIFIC_LAUNCH%"=="klotski" set "CLASS_NAME=KlotskiActivity"
    if /I "%SPECIFIC_LAUNCH%"=="snake" set "CLASS_NAME=SnakeActivity"
    if /I "%SPECIFIC_LAUNCH%"=="tetris" set "CLASS_NAME=TetrisActivity"
    if not defined CLASS_NAME (
        REM 未知包名：使用首字母大写兜底
        for /f "tokens=*" %%A in ('powershell -NoProfile -Command "$a='%SPECIFIC_LAUNCH%'; (Get-Culture).TextInfo.ToTitleCase($a) + 'Activity'"') do set "CLASS_NAME=%%A"
    )
    set "LAUNCH_ACT=.games.%SPECIFIC_LAUNCH%.%CLASS_NAME%"
)

adb -s %SERIAL% shell am start -n "%PKG%/%LAUNCH_ACT%" 2>nul
if errorlevel 1 (
    echo [提示] %LAUNCH_ACT% 启动失败，尝试启动主 Activity...
    adb -s %SERIAL% shell am start -n "%PKG%/%DEFAULT_ACTIVITY%"
)
echo [OK] 已发送启动 Intent

REM =============== 5. 截图保存 ===============
echo.
echo [5/5] 截图保存到 %PROJECT_DIR%\.emulator-logs\last_screenshot.png...
if not exist "%PROJECT_DIR%\.emulator-logs" mkdir "%PROJECT_DIR%\.emulator-logs"
adb -s %SERIAL% exec-out screencap -p > "%PROJECT_DIR%\.emulator-logs\last_screenshot.png" 2>nul
if not errorlevel 1 echo [OK] 截图已保存

echo.
echo ============================================================
echo  全部完成！APK 已安装并启动。
echo  - 日志: adb -s %SERIAL% logcat | findstr %PKG%
echo  - 截图: %PROJECT_DIR%\.emulator-logs\last_screenshot.png
echo ============================================================

endlocal