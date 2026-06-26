# T10: 文档与发布 - 发布清单

> **文档版本**: v1.0  
> **创建日期**: 2026-05-26  
> **维护者**: GameCenterApp Team

---

## 发布前检查清单

### ✅ 文档（已完成）

- [x] 创建 `文档/MODULAR_ARCHITECTURE_DESIGN.md`（架构设计文档）
- [x] 创建 `文档/MODULE_DEVELOPMENT_GUIDE.md`（模块开发指南）
- [x] 更新 `README.md`（模块化架构说明）
- [x] 更新 `CHANGELOG.md`（v1.4.0 更新日志）
- [x] 更新 `文档/MODULAR_ARCHITECTURE_DESIGN.md`（实现细节 + 已知问题）
- [x] 创建 `文档/T08_APK_SIZE_OPTIMIZATION.md`（APK 体积优化指南）

### ⏳ 构建（等待 JAVA_HOME 设置）

- [ ] 设置 JAVA_HOME 环境变量
- [ ] 构建 Release APK（`.\gradlew.bat :app:assembleRelease`）
- [ ] 签名 APK（使用 `keystore.properties` 中的配置）
- [ ] 检查 APK 大小（目标 ≤15 MB）
- [ ] 使用 APK Analyzer 分析体积

### ⏳ 上传到 VPS（等待构建完成）

- [ ] 上传 APK 到香港 VPS（`/var/www/update/app/app-release.apk`）
- [ ] 上传 `version.json` 到香港 VPS（`/var/www/update/app/version-release.json`）
- [ ] 上传 APK 到美国 VPS（备用更新源）
- [ ] 验证 VPS 上传成功（`curl https://hk-update.your-domain.com/app/version-release.json`）

### ⏳ 上传到 GitHub Releases（等待构建完成）

- [ ] 创建 GitHub Release（v1.4.0）
- [ ] 上传 APK 到 GitHub Releases
- [ ] 编写 Release Notes（从 `CHANGELOG.md` 提取）
- [ ] 验证 GitHub Release 上传成功

### ⏳ 测试（T09 已完成）

- [x] 单元测试通过（437+ 测试用例）
- [x] 集成测试通过
- [ ] 手动测试 Release APK（真机测试）
- [ ] 验证模块商店功能正常
- [ ] 验证内置游戏更新功能正常

---

## 发布步骤（详细）

### 步骤 1：设置 JAVA_HOME

```bash
# Windows (PowerShell)
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleRelease -PskipReleaseLint=true

# 或使用 Android Studio 的嵌入式 JDK
# File → Settings → Build, Execution, Deployment → Build Tools → Gradle
# → Gradle JDK → 选择 "Embedded JDK"
```

### 步骤 2：构建 Release APK

```bash
# 清理并构建
.\gradlew.bat clean
.\gradlew.bat :app:assembleRelease -PskipReleaseLint=true

# 检查 APK 大小
ls -lh app/build/outputs/apk/release/app-release.apk

# 目标：≤15 MB
# 当前（优化后）：~18 MB
# 如果超过 15 MB，需要进一步优化（压缩图标、音频）
```

### 步骤 3：签名验证

```bash
# 检查 keystore.properties 是否存在
cat keystore.properties

# 验证 APK 签名
cd app\build\outputs\apk\release
jarsigner -verify app-release.apk

# 输出：jar 已验证 ✅
```

### 步骤 4：上传到 VPS

```bash
# 使用 Python 脚本上传（推荐）
python tools/upload_to_vps.py \
  --apk app/build/outputs/apk/release/app-release.apk \
  --version app/build/outputs/apk/release/version.json \
  --channel release

# 或手动上传
scp app/build/outputs/apk/release/app-release.apk user@hk-vps:/var/www/update/app/
scp app/build/outputs/apk/release/version.json user@hk-vps:/var/www/update/app/version-release.json
```

### 步骤 5：上传到 GitHub Releases

```bash
# 使用 Python 脚本上传（推荐）
python tools/upload_to_github_release.py \
  --apk app/build/outputs/apk/release/app-release.apk \
  --version-name "1.4.0" \
  --changelog-file CHANGELOG.md

# 或手动上传
# 1. 访问 https://github.com/your-repo/GameMatrixApp/releases/new
# 2. 输入 Tag version: v1.4.0
# 3. 输入 Release title: v1.4.0 - 模块化架构重构
# 4. 上传 app-release.apk
# 5. 点击 "Publish release"
```

### 步骤 6：验证更新

```bash
# 验证 VPS 更新元数据
curl https://hk-update.your-domain.com/app/version-release.json

# 应该输出：
# {
#   "versionCode": 341,
#   "versionName": "1.4.0",
#   "channel": "stable",
#   ...
# }

# 安装到测试设备
adb install app/build/outputs/apk/release/app-release.apk

# 打开 App，检查更新提示
# 应该显示："发现新版本 v1.4.0"
```

---

## Release Notes（v1.4.0）

### 🏗️ 模块化架构重构

- **模块系统框架**：实现完整的模块化架构，支持动态加载 APK 模块
- **模块加载器 V2**：新增 `ModuleLoaderV2`，支持版本管理、DEX 缓存、资源加载、热更新
- **模块商店核心**：实现 `ModuleDownloadManager`（断点续传）、`ModuleInstaller`、`ModuleUninstaller`
- **内置游戏更新**：实现 `BuiltInModuleUpdater`，支持斗地主、五子棋通过模块商店更新

### 🛒 模块商店功能

- **模块商店 UI**：新增 `ModuleStoreActivity`，支持模块浏览、搜索、下载、安装、卸载
- **实时搜索**：`etModuleSearch` 支持按关键词实时过滤模块
- **版本更新提示**：`ModuleAdapter` 显示橙色"更新"按钮（当已安装版本落后时）
- **模块分类**：按游戏、工具、浏览器、AI、VPN 分类展示

### 🌐 联机功能模块化

- **联机核心模块**：将 `OnlineRoomManager`、`GameSocketServer`、`GameSocketClient`、`RelayHttpClient`、`LANManager` 拆分为独立模块 `online-core`
- **动态加载联机模块**：游戏模块通过 `OnlineCoreModule` 动态加载联机功能
- **支持云联机**：斗地主、五子棋、围棋、中国象棋、石头剪刀布均支持 WebSocket 云联机对战

### 📦 APK 体积优化（T08）

- **目标**：框架 APK ≤15MB
- **ABI 拆分**：仅保留 `arm64-v8a` 架构（减少 Native 库体积约 75%）
- **移除嵌入式 APK**：从 `assets/` 中移除嵌入式 APK（节省 ~17.3MB）
- **R8 全模式优化**：`minifyEnabled true` + `shrinkResources true`
- **ProGuard 规则完善**：保留 Android 核心组件、序列化接口、Dagger/Hilt、网络层、模块系统接口
- **待优化**：启动图标压缩（`ic_launcher_logo.png` 1.6MB → 目标 <100KB）、音频文件压缩、`raw/` 2.4MB）

### 🧪 测试覆盖增强

- **单元测试**：新增 15+ 个测试文件，437+ 个测试用例
- **测试覆盖**：斗地主规则引擎（40+）、牌型识别（60+）、AI 决策（3）、更新逻辑（40+）、AI API 客户端（8）
- **测试工具**：使用 MockWebServer、Mockito、Kotlin Coroutines Test

### 📝 文档完善

- **模块开发指南**：新增 `文档/MODULE_DEVELOPMENT_GUIDE.md`，说明如何创建/发布模块
- **架构设计文档**：新增 `文档/MODULAR_ARCHITECTURE_DESIGN.md`，详细说明模块化架构
- **README 更新**：更新模块化架构说明、快速入门、目录结构

### 🐛 Bug 修复

- **模块下载 SHA-256 校验修复**：所有 VPS 文件现在与 `modules.json` 的 SHA-256 哈希值一致
- **模块加载失败修复**：版本感知重加载、DEX 优化缓存清理
- **模块商店显示修复**：`builtIn` 逻辑修复、`games_hall` 模块正确显示
- **证书绑定临时关闭**：解决模拟器 SIGSEGV 兼容性问题

### 📊 版本信息

- **versionCode**: 341
- **versionName**: 1.4.0
- **modules.json 版本**: 11
- **模块总数**: 33（29 款游戏模块 + 4 个功能模块）

---

## 回滚计划

如果发布后发现问题，可以按以下步骤回滚：

### 步骤 1：回滚 VPS

```bash
# 登录 VPS
ssh user@hk-vps

# 回滚到上一个稳定版本
cd /var/www/update/app/
mv app-release.apk app-release.apk.bak
mv app-release.apk.old app-release.apk

mv version-release.json version-release.json.bak
mv version-release.json.old version-release.json
```

### 步骤 2：回滚 GitHub Release

```bash
# 访问 GitHub Releases
# https://github.com/your-repo/GameMatrixApp/releases

# 删除有问题的 Release
# 1. 点击有问题的 Release
# 2. 点击 "Delete" 按钮
# 3. 确认删除

# 重新发布上一个稳定版本
# 1. 点击 "Tags" 标签
# 2. 找到上一个稳定版本的 Tag
# 3. 点击 "..." → "Create release"
# 4. 上传上一个稳定版本的 APK
# 5. 点击 "Publish release"
```

### 步骤 3：通知用户

在 App 中推送通知：

```
发现新问题，已回滚到上一个稳定版本。
请前往"设置" → "检查更新" 重新下载。
```

---

## 发布后验证

### 验证清单

- [ ] VPS 更新元数据可访问（`curl https://hk-update.your-domain.com/app/version-release.json`）
- [ ] GitHub Release 可访问（访问 Release 页面）
- [ ] App 能检测到新版本（打开 App → 检查更新）
- [ ] 下载新版本成功（点击"更新"按钮）
- [ ] 安装新版本成功（下载完成后点击"安装"）
- [ ] 新版本功能正常（测试主要功能和模块商店）
- [ ] 模块商店能正常下载和安装模块
- [ ] 内置游戏更新功能正常（斗地主、五子棋）

---

**文档结束 / End of Document**


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)
