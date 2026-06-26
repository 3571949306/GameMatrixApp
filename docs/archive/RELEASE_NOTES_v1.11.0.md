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
| `app/build/outputs/apk/release/app-release.apk` | 正式版 APK（已签名、已混淆，约 15.6 MB） |
| `app/build/outputs/apk/debug/app-debug.apk` | 调试版 APK（未混淆，带 LeakCanary） |

### 版本元数据
| 文件路径 | 说明 |
|---------|------|
| `app/src/main/assets/version.json` | 版本信息（应用内置检查更新使用） |
| `app/build/outputs/apk/release/version.json` | 生成的版本文件（VPS 上传使用） |

### 发布脚本
| 文件路径 | 说明 |
|---------|------|
| `工具/upload_to_vps.py` | 上传 APK 和 version.json 到香港 VPS |
| `工具/upload_to_github_release.py` | 上传 APK 到 GitHub Releases |
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
python 工具/upload_to_vps.py --apk app\build\outputs\apk\release\app-release.apk --version app\build\outputs\apk\release\version.json

# 4. 上传到 GitHub Releases
python 工具/upload_to_github_release.py --apk app\build\outputs\apk\release\app-release.apk --version-name 1.11.0
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
---

## 2026-05-14 文档同步：文字适配与应用语言

- 新增全局按钮文字适配样式，统一提升 MaterialButton 与平台 Button 的最小高度、内边距和两行显示能力，减少“进入游戏”“发送”等按钮文字被裁切的问题。
- 设置弹窗新增应用语言选项：跟随系统、中文、English；应用启动时会恢复已选择语言。
- AI 任务下拉改为资源字符串，切换 English 后可显示 Chat、Summary、Translate 等英文选项。
- 发布前检查需覆盖中文/英文两种语言、深色/浅色主题、游戏大厅卡片按钮、AI 发送按钮、工具箱小按钮和斗地主操作按钮。
## 2026-05-15 文档同步：Dependabot 与 CI 修复

- Dependabot 安全告警已处理：升级 Android Gradle Plugin 到 8.13.2、Gradle Wrapper 到 8.13、Kotlin 到 2.2.21、Hilt 到 2.57.2。
- 构建 classpath 已强制解析到安全版本：Netty 4.1.133.Final、BouncyCastle 1.84、commons-compress 1.26.0、jose4j 0.9.6、jdom2 2.0.6.1。
- GitHub Actions 已改为验证型 CI：使用 JDK 21，执行 debug 构建与单元测试，不在云端构建 release 包，避免暴露或依赖 release 签名文件。
- CI 命令统一添加 `-PautoBumpVersion=false`，避免自动修改 `version.properties`。
- `.gitignore` 的 `data/` 规则已收窄为 `/data/`，防止误忽略 `app/src/main/java/com/gamecenter/app/ai/data/` 源码。
- 最新 GitHub Actions `CI/CD Pipeline` 已通过；正式签名、R8 混淆和 服务器部署/GitHub Release 发布仍以本机发布流程为准。

## 2026-05-24 文档同步
- 底部导航切换闪退修复：创建 KeepStateNavigator 自定义导航器，使用 add/show/hide 策略替代 Navigation 默认 replace 策略
- 模块下载修复：ModuleDownloader 全面重写，添加全局异常捕获、降低超时、增加日志
- 内存泄漏全面修复：移除 WeakReference callback、Fragment 回调安全检查、视图引用彻底清理
- 压力测试通过：40轮快速Tab切换无崩溃

- 2026-05-24 游戏美化+中国象棋提示改进+华容道&中国象棋模块商店上架：四个游戏视觉美化（斗地主径向渐变桌面/五子棋木纹3D棋子/华容道深色渐变金色边框/中国象棋木纹角标波浪线）；中国象棋提示改为棋盘可视化（蓝色脉冲光环+箭头指引）+中文棋谱描述；华容道和中国象棋创建独立APK模块（feature/games/klotski、feature/games/chinesechess）v2.0.0上架模块商店


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)
