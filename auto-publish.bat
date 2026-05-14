@echo off
REM ============================================================
REM GameCenterApp 自动发布到所有更新源
REM 功能：编译 APK 并上传到 HK VPS + US VPS + GitHub Releases
REM ============================================================

echo.
echo ============================================================
echo   GameCenterApp 自动发布工具
echo ============================================================
echo.

set CHANNEL=%1
if "%CHANNEL%"=="" set CHANNEL=beta
set GITHUB_TOKEN=%2

echo [信息] 发布渠道：%CHANNEL%
echo.

REM 步骤 1: 编译 Release APK
echo [1/4] 编译 Release APK...
call gradlew.bat assembleRelease -PupdateChannel=%CHANNEL% -x lintVitalAnalyzeRelease --console=plain
if errorlevel 1 (
    echo [错误] 编译失败！
    exit /b 1
)
echo.

REM 步骤 2: 生成 version.json
echo [2/4] 生成版本元数据...
call gradlew.bat generateVersionJson -PupdateChannel=%CHANNEL% --console=plain
if errorlevel 1 (
    echo [错误] 生成 version.json 失败！
    exit /b 1
)
echo.

for /f "usebackq delims=" %%v in (`powershell -NoProfile -Command "(Get-Content 'app\build\outputs\apk\release\version.json' -Raw | ConvertFrom-Json).versionName"`) do set VERSION_NAME=%%v
set GITHUB_VERSION=%VERSION_NAME%
if /I "%CHANNEL%"=="beta" set GITHUB_VERSION=%VERSION_NAME%-beta

REM 步骤 3: 上传到 VPS (HK + US)
echo [3/4] 上传到 VPS 服务器...
python tools\upload_to_vps.py ^
    --apk app\build\outputs\apk\release\app-release.apk ^
    --version app\build\outputs\apk\release\version.json ^
    --channel %CHANNEL% --skip-verify
if errorlevel 1 (
    echo [警告] VPS 上传失败
)
echo.

REM 步骤 4: 上传到 GitHub Releases
if "%GITHUB_TOKEN%"=="" (
    echo [警告] 未提供 GitHub Token，跳过 GitHub Releases 上传
) else (
    echo [4/4] 上传到 GitHub Releases...
    python tools\upload_to_github_release.py ^
        --apk app\build\outputs\apk\release\app-release.apk ^
        --version-name "%GITHUB_VERSION%"
    if errorlevel 1 (
        echo [警告] GitHub Releases 上传失败
    )
)
echo.

echo ============================================================
echo   发布完成！
echo ============================================================
echo.
echo 已上传到以下更新源：
echo   1. 香港 VPS: https://hk-update.tcp0053.shop
echo   2. 美国 VPS: https://tcp0053.shop:1443
echo   3. GitHub Releases: https://github.com/3571949306/GameCenterApp/releases
echo.

pause
