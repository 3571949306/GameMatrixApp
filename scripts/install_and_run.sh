#!/bin/bash
# ====================================================================
# GameMatrix App 一键构建 + 安装 + 启动脚本（Linux/macOS/Git Bash）
# 用法: ./scripts/install_and_run.sh [package_activity]
# 示例: ./scripts/install_and_run.sh                  (默认启动主界面)
#       ./scripts/install_and_run.sh chinesechess      (启动中国象棋)
# ====================================================================

# set -e  # 移除：允许部分步骤失败继续

# =============== 配置 ===============
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
SERIAL="emulator-5554"
PKG="com.gamecenter.app"
DEFAULT_ACTIVITY=".SplashActivity"
SPECIFIC_LAUNCH="${1:-}"
GRADLE_OPTS="-PautoBumpVersion=false --no-daemon --console=plain"

echo "============================================================"
echo " GameMatrixApp 一键构建 + 安装 + 启动"
echo " 项目: $PROJECT_DIR"
echo " 目标: $SERIAL"
echo " APK:  $APK_PATH"
echo "============================================================"

# =============== 0. 检查 emulator ===============
echo ""
echo "[0/4] 检查 emulator 连接状态..."
if ! adb devices | grep -qE "${SERIAL}.*device$"; then
    echo "[错误] 未发现已连接设备 $SERIAL"
    echo "当前设备列表:"
    adb devices
    exit 1
fi
echo "[OK] $SERIAL 已连接"

# =============== 1. 构建 Debug APK ===============
echo ""
echo "[1/4] 构建 Debug APK..."
cd "$PROJECT_DIR"
./gradlew.bat :app:assembleDebug $GRADLE_OPTS 2>&1 | tail -20
echo "[OK] 构建成功"

# =============== 2. 检查 APK ===============
echo ""
echo "[2/4] 检查 APK 产物..."
if [ ! -f "$APK_PATH" ]; then
    echo "[错误] APK 未生成: $APK_PATH"
    exit 1
fi
APK_SIZE=$(stat -c '%s' "$APK_PATH" 2>/dev/null || stat -f '%z' "$APK_PATH")
echo "[OK] APK 大小: $APK_SIZE bytes"

# =============== 3. 安装到 emulator ===============
echo ""
echo "[3/4] 安装到 $SERIAL..."
adb -s $SERIAL install -r "$APK_PATH"
echo "[OK] 安装成功"

# =============== 4. 启动应用 ===============
echo ""
echo "[4/5] 启动应用..."
if [ -z "$SPECIFIC_LAUNCH" ]; then
    # 默认启动 MainActivity（始终可访问）
    LAUNCH_ACT="$DEFAULT_ACTIVITY"
    adb -s $SERIAL shell am start -n "${PKG}/${LAUNCH_ACT}" 2>/dev/null
    echo "[OK] 已启动主 Activity"
else
    # 尝试启动指定游戏的 Activity（可能因 exported=false 被拒绝）
    case "$SPECIFIC_LAUNCH" in
        chinesechess) CLASS_NAME="ChineseChessActivity"; PKG_DIR="games.chinesechess" ;;
        game2048)     CLASS_NAME="Game2048Activity";     PKG_DIR="games.game2048" ;;
        doudizhu)     CLASS_NAME="DouDiZhuActivity";     PKG_DIR="games.doudizhu" ;;
        gomoku)       CLASS_NAME="GomokuActivity";       PKG_DIR="games.gomoku" ;;
        klotski)      CLASS_NAME="KlotskiActivity";      PKG_DIR="games.klotski" ;;
        snake)        CLASS_NAME="SnakeActivity";        PKG_DIR="games.snake" ;;
        tetris)       CLASS_NAME="TetrisActivity";       PKG_DIR="games.tetris" ;;
        *)            CLASS_NAME="${SPECIFIC_LAUNCH^}Activity"; PKG_DIR="games.${SPECIFIC_LAUNCH}" ;;
    esac

    FULL_ACT=".${PKG_DIR}.${CLASS_NAME}"
    START_OUTPUT=$(adb -s $SERIAL shell am start -n "${PKG}/${FULL_ACT}" 2>&1)
    if echo "$START_OUTPUT" | grep -qE "Permission Denial|not exported"; then
        echo "[提示] $FULL_ACT 因 exported=false 无法直接启动（正常 Android 安全机制）"
        echo "[回退] 启动主 Activity，请从主界面点击进入游戏"
        adb -s $SERIAL shell am start -n "${PKG}/${DEFAULT_ACTIVITY}" 2>/dev/null
        echo "[OK] 主 Activity 已启动，请手动点击「$SPECIFIC_LAUNCH」"
    else
        echo "[OK] $FULL_ACT 已启动"
    fi
fi

# =============== 5. 截图保存 ===============
echo ""
echo "[5/5] 截图保存到 $PROJECT_DIR/.emulator-logs/last_screenshot.png..."
mkdir -p "$PROJECT_DIR/.emulator-logs"
adb -s $SERIAL exec-out screencap -p > "$PROJECT_DIR/.emulator-logs/last_screenshot.png" 2>/dev/null
echo "[OK] 截图已保存"

echo ""
echo "============================================================"
echo " 全部完成！APK 已安装并启动。"
echo " - 日志: adb -s $SERIAL logcat | grep $PKG"
echo " - 截图: $PROJECT_DIR/.emulator-logs/last_screenshot.png"
echo ""
echo " 常用命令："
echo "   查看所有游戏:  $PKG/.MainActivity  （启动后从主界面点击）"
echo "   实时日志:     adb -s $SERIAL logcat *:I | grep $PKG"
echo "   卸载应用:     adb -s $SERIAL uninstall $PKG"
echo "============================================================"