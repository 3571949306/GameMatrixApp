#!/bin/bash

# GameMatrixApp 优化版本构建脚本
# 版本: vc660
# 日期: 2026-08-16

set -e

echo "🚀 GameMatrixApp 优化构建流程"
echo "================================"
echo ""

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# 检查环境
echo "📋 检查构建环境..."
if [ ! -f "gradlew" ]; then
    echo -e "${RED}❌ 错误: 找不到 gradlew，请在项目根目录执行${NC}"
    exit 1
fi

# 显示当前版本
echo -e "${GREEN}📝 当前版本:${NC}"
grep "versionCode\|versionName" version.properties

echo ""
echo "================================"
echo "开始构建流程"
echo "================================"
echo ""

# 步骤 1: 清理
echo -e "${YELLOW}🧹 步骤 1/4: 清理旧构建...${NC}"
./gradlew clean
echo -e "${GREEN}✅ 清理完成${NC}"
echo ""

# 步骤 2: 构建 Debug APK（快速验证）
echo -e "${YELLOW}🔨 步骤 2/4: 构建 Debug APK (验证)...${NC}"
./gradlew :app:assembleDebug

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Debug 构建成功${NC}"
    DEBUG_APK="app/build/outputs/apk/debug/app-debug.apk"
    if [ -f "$DEBUG_APK" ]; then
        echo "📦 Debug APK: $DEBUG_APK"
        ls -lh "$DEBUG_APK"
    fi
else
    echo -e "${RED}❌ Debug 构建失败${NC}"
    exit 1
fi
echo ""

# 步骤 3: 构建 Release APK
echo -e "${YELLOW}🔨 步骤 3/4: 构建 Release APK...${NC}"
echo "   配置: stable channel, ARM64-only"
echo "   预计耗时: 30-45 分钟"
echo ""

./gradlew :app:assembleRelease \
  -PupdateChannel=stable \
  -Ptarget-platform=android-arm64 \
  -PskipReleaseLint=true

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Release 构建成功${NC}"
    RELEASE_APK="app/build/outputs/apk/release/app-release.apk"
    
    if [ -f "$RELEASE_APK" ]; then
        echo ""
        echo "================================"
        echo "🎉 构建完成！"
        echo "================================"
        echo ""
        echo "📦 Release APK 信息:"
        ls -lh "$RELEASE_APK"
        echo ""
        
        # APK 大小检查
        APK_SIZE=$(stat -f%z "$RELEASE_APK" 2>/dev/null || stat -c%s "$RELEASE_APK" 2>/dev/null)
        APK_SIZE_MB=$((APK_SIZE / 1024 / 1024))
        echo "📏 APK 大小: ${APK_SIZE_MB} MB"
        
        if [ $APK_SIZE_MB -gt 120 ]; then
            echo -e "${YELLOW}⚠️  警告: APK 超过 120MB 限制${NC}"
        else
            echo -e "${GREEN}✅ APK 大小符合要求${NC}"
        fi
        echo ""
        
        # 步骤 4: 验证
        echo -e "${YELLOW}🔍 步骤 4/4: 验证 APK...${NC}"
        
        # 检查签名（如果有 jarsigner）
        if command -v jarsigner &> /dev/null; then
            echo "检查 APK 签名..."
            jarsigner -verify "$RELEASE_APK" && echo -e "${GREEN}✅ 签名有效${NC}" || echo -e "${YELLOW}⚠️  签名验证失败${NC}"
        fi
        
        echo ""
        echo "================================"
        echo "✅ 所有步骤完成！"
        echo "================================"
        echo ""
        echo "📦 产物位置:"
        echo "   Debug:   $DEBUG_APK"
        echo "   Release: $RELEASE_APK"
        echo ""
        echo "🚀 下一步操作:"
        echo "   1. 安装测试: adb install -r $RELEASE_APK"
        echo "   2. 启动应用: adb shell am start -n com.gamecenter.app/.MainActivity"
        echo "   3. 查看日志: adb logcat | grep GameMatrix"
        echo ""
        
    else
        echo -e "${RED}❌ 找不到 Release APK${NC}"
        exit 1
    fi
else
    echo -e "${RED}❌ Release 构建失败${NC}"
    echo "请检查构建日志: build.log"
    exit 1
fi
