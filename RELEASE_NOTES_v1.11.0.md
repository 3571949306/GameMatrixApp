# 夹层游戏中心 v1.11.0 正式版发布说明

## 版本信息
- **版本号**: 217 (1.11.0)
- **发布日期**: 2026-05-11
- **类型**: Stable（正式版）
- **更新渠道**: Beta（默认）/ Stable

## 📦 发布文件清单

### APK 文件
| 文件路径 | 说明 |
|---------|------|
| `app/build/outputs/apk/release/app-release-unsigned.apk` | 正式版 APK（已混淆，约 15.6 MB） |
| `app/build/outputs/apk/debug/app-debug.apk` | 调试版 APK（未混淆，带 LeakCanary） |

### 版本元数据
| 文件路径 | 说明 |
|---------|------|
| `app/src/main/assets/version.json` | 版本信息（应用内置检查更新使用） |
| `app/build/outputs/version.json` | 生成的版本文件（VPS 上传使用） |

### 发布脚本
| 文件路径 | 说明 |
|---------|------|
| `tools/upload_to_vps.py` | 上传 APK 和 version.json 到香港 VPS |
| `tools/upload_to_github_release.py` | 上传 APK 到 GitHub Releases |
| `build-and-upload-all.bat` | 一键构建并发布到 VPS 和 GitHub |

## 🚀 发布步骤

### 方法一：一键发布（推荐）
```bash
# 在项目根目录执行
.\build-and-upload-all.bat
```

### 方法二：分步发布
```bash
# 1. 构建正式版
.\gradlew clean assembleRelease

# 2. 生成版本元数据
.\gradlew generateVersionJson

# 3. 上传到 VPS（需要配置 VPS 信息）
python tools/upload_to_vps.py --apk app\build\outputs\apk\release\app-release-unsigned.apk --version-json app\build\outputs\version.json

# 4. 上传到 GitHub Releases
python tools/upload_to_github_release.py app\build\outputs\apk\release\app-release-unsigned.apk "v1.11.0"
```

## ✨ 本次更新

### 新增
- **Lint 严格模式**：Release 构建启用 abortOnError、warningsAsErrors，确保代码质量
- **统一网络错误处理器**：NetworkErrorHandler 集中管理所有网络异常，支持错误码分类、智能重试、网络状态检查
- **国际化支持**：中英文双语，values-en/strings.xml 英文资源，根据系统语言自动切换
- **内存泄漏检测**：Debug 版集成 LeakCanary 2.14，自动检测 Activity/Fragment 泄漏
- **autoBumpVersion 开关**：通过 `-PautoBumpVersion=false` 可关闭版本号自动递增
- **GitHub Actions CI/CD**：.github/workflows/ci.yml 实现自动构建、测试、上传产物

### 优化
- 网络错误提示统一为友好的中文/英文 Toast 消息（10+ 种错误类型）
- 版本号递增可通过构建参数灵活控制
- 资源文件按语言分离，支持多语言扩展
- ProGuard 规则完善，确保所有游戏类和第三方库不被混淆

### 技术改进
- 新增 `utils.NetworkErrorHandler` - 网络错误统一处理
- 新增 `utils.I18nHelper` - 国际化辅助工具
- 新增 `debugImplementation leakcanary-android:2.14`
- Lint 配置：abortOnError=true, warningsAsErrors=true, checkReleaseBuilds=true

## 📋 系统要求
- Android 6.0+ (API 23)
- 存储空间：至少 50 MB
- 网络连接：用于更新检查和联机游戏

## ⚠️ 注意事项
1. 首次启动会弹出权限使用说明对话框
2. 如需更新，请确保网络畅通
3. 联机游戏需要稳定的网络连接

## 🔧 构建命令参考

```bash
# 构建调试版
.\gradlew assembleDebug

# 构建正式版
.\gradlew assembleRelease

# 构建并关闭版本号递增
.\gradlew assembleDebug -PautoBumpVersion=false

# 运行单元测试
.\gradlew test

# 清理构建缓存
.\gradlew clean
```

## 📊 版本对比

| 指标 | v1.10.3 | v1.11.0 | 改进 |
|------|---------|---------|------|
| APK 大小（Release） | 15.58 MB | 15.6 MB | +0.02 MB（国际化资源） |
| 单元测试用例 | 96 | 96 | 保持 |
| Lint 检查 | 关闭 | 严格模式 | 代码质量提升 |
| 内存泄漏检测 | 无 | LeakCanary | 自动检测 |
| CI/CD | 本地脚本 | GitHub Actions | 自动化 |
| 网络错误处理 | 分散 | 统一 | 用户体验提升 |
| 国际化 | 中文 | 中英文 | 用户群扩大 |
