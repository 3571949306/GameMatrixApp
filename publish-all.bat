@echo off
REM ============================================================
REM GameCenterApp 一键发布脚本
REM 功能：编译打包并上传到所有更新源（HK VPS + US VPS + GitHub Releases）
REM ============================================================

echo.
echo ============================================================
echo   GameCenterApp 一键发布工具
echo ============================================================
echo.

REM 设置参数
set CHANNEL=%1
if "%CHANNEL%"=="" set CHANNEL=beta

set SKIP_VERIFY=%2
if "%SKIP_VERIFY%"=="" set SKIP_VERIFY=

echo [信息] 发布渠道：%CHANNEL%
echo.

REM 步骤 1: 清理旧的构建文件
echo [1/5] 清理旧的构建文件...
call gradlew clean
if errorlevel 1 (
    echo [错误] 清理失败！
    exit /b 1
)
echo.

REM 步骤 2: 编译 Release APK
echo [2/5] 编译 Release APK...
call gradlew assembleRelease -PupdateChannel=%CHANNEL%
if errorlevel 1 (
    echo [错误] 编译失败！
    exit /b 1
)
echo.

REM 步骤 3: 生成 version.json
echo [3/5] 生成版本元数据...
call gradlew generateVersionJson -PupdateChannel=%CHANNEL%
if errorlevel 1 (
    echo [错误] 生成 version.json 失败！
    exit /b 1
)
echo.

REM 步骤 4: 上传到 VPS（HK + US）
echo [4/5] 上传到 VPS 服务器...
python tools\upload_to_vps.py ^
    --apk app\build\outputs\apk\release\app-release-unsigned.apk ^
    --version app\build\outputs\version.json ^
    --channel %CHANNEL% %SKIP_VERIFY%
if errorlevel 1 (
    echo [警告] VPS 上传失败，继续尝试 GitHub Releases...
)
echo.

REM 步骤 5: 上传到 GitHub Releases
echo [5/5] 上传到 GitHub Releases...
python tools\upload_to_github_release.py ^
    app\build\outputs\apk\release\app-release-unsigned.apk ^
    "v1.11.0"
if errorlevel 1 (
    echo [警告] GitHub Releases 上传失败
)
echo.

REM 完成
echo ============================================================
echo   发布完成！
echo ============================================================
echo.
echo 已上传到以下更新源：
echo   1. 香港 VPS: https://hk-update.tcp0053.shop
echo   2. 美国 VPS: https://tcp0053.shop:1443
echo   3. GitHub Releases: https://github.com/3571949306/GameCenterApp/releases
echo.
echo 版本号：%CHANNEL%
echo.

pause
