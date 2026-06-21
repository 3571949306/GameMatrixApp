@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo GameMatrixApp 自动化测试运行器
echo ========================================
echo.

REM 设置参数
set "DEVICE=%1"
set "SUITE=%2"
set "APK=%3"

if "%DEVICE%"=="" set "DEVICE=emulator-5554"
if "%SUITE%"=="" set "SUITE=smoke"
if "%APK%"=="" set "APK=D:\Developmment\GameMatrixApp\app\build\outputs\apk\debug\app-arm64-v8a-debug.apk"

echo 设备: %DEVICE%
echo 套件: %SUITE%
echo APK:  %APK%
echo.

REM 检查ADB
where adb >nul 2>nul
if errorlevel 1 (
    echo [错误] ADB未找到，请安装Android SDK
    pause
    exit /b 1
)

REM 检查设备连接
echo [1/3] 检查设备连接...
adb -s %DEVICE% get-state >nul 2>&1
if errorlevel 1 (
    echo [错误] 设备 %DEVICE% 未连接
    pause
    exit /b 1
)
echo [成功] 设备已连接
echo.

REM 检查APK
echo [2/3] 检查APK...
if not exist "%APK%" (
    echo [错误] APK不存在: %APK%
    pause
    exit /b 1
)
echo [成功] APK存在
echo.

REM 运行测试
echo [3/3] 开始运行测试套件: %SUITE%
echo.

cd /d "%~dp0"
python adb_test_framework.py --device %DEVICE% --suite %SUITE% --apk "%APK%"

set TEST_EXIT_CODE=%errorlevel%

echo.
echo ========================================
if %TEST_EXIT_CODE% equ 0 (
    echo [成功] 所有测试通过！
) else (
    echo [失败] 有测试失败，退出码: %TEST_EXIT_CODE%
)
echo ========================================
echo.

pause
exit /b %TEST_EXIT_CODE%
