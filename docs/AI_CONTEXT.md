# AI 上下文文档 - GameMatrix App

> 本文档合并了原 `AI_CONTEXT.md`、`PROJECT_CONTEXT.md`、`AI_ONBOARDING.md`、`项目AI接手说明.md` 四个文档，供 AI 编程助手和新开发者阅读。

**最后更新**: 2026-06-25  
**当前版本**: versionCode=500, versionName=1.4.1

---

## 1. 项目概览

| 项目 | 说明 |
|------|------|
| 名称 | GameMatrixApp（夹层） |
| 类型 | Android 游戏中心应用 |
| 包名 | `com.gamecenter.app` |
| 语言 | Java 17 (60%) + Kotlin 2.0.21 (40%) |
| 构建 | Gradle 8.13 + AGP 8.13.2 |
| SDK | minSdk 24, targetSdk 35, compileSdk 35 |
| DI | Hilt 2.57.2 |
| 数据库 | Room 2.7.1 |
| 网络 | OkHttp 4.12.0 |
| GitHub | `https://github.com/3571949306/GameMatrixApp.git` (main 分支) |

### 核心功能
- 28 款内置经典游戏（经典/益智/休闲三大类）
- 模块商店：按需下载扩展游戏、工具箱、浏览器、AI 助手、VPN
- 单机 AI 对战 + 局域网联机 + 云联机
- 自动更新（香港 VPS + GitHub Releases 双源）

---

## 2. 当前状态（2026-06-25）

### 构建状态
```
✅ Debug/Release 构建正常
✅ R8 混淆已启用
✅ ABI splits: arm64-v8a
✅ Lint 严格模式（abortOnError true）
✅ 证书绑定: Release 启用
```

### 适配状态
```
✅ Android 11-17 全版本适配
✅ SplashScreen API (Android 12+)
✅ POST_NOTIFICATIONS (Android 13+)
✅ registerReceiver export 标志 (Android 14+)
✅ Edge-to-Edge (Android 15+)
✅ 屏幕方向锁定 (Android 16+)
```

### 测试状态
```
✅ 145 个 UI 自动化测试用例全部通过
✅ 14 个单元测试文件
✅ 47 个 Android 测试文件
```

---

## 3. 服务器架构

### 3.1 VPS 列表

| VPS | 用途 | 域名前缀 |
|-----|------|----------|
| 香港 VPS | 主更新源 + WebSocket Relay + HTTP Relay | `hk-*` |
| ~~美国 VPS~~ | ~~备用~~（2026-06-19 已下线） | ~~无~~ |

### 3.2 主 VPS 服务

| 服务 | 域名 | 用途 |
|------|------|------|
| APK 更新 | `hk-update.<DOMAIN>` | 应用更新分发 |
| WebSocket | `hk-ws.<DOMAIN>` | 云联机 Relay |
| HTTP Relay | `hk-relay.<DOMAIN>` | 云联机 HTTP |

### 3.3 更新分发架构

```
优先级 1: 香港 VPS (连接 2s/读取 3s)
    ↓ (失败或速度 <30KB/s)
优先级 2: GitHub Releases (连接 5s/读取 15s)
```

---

## 4. 代码结构

```
GameMatrixApp/
├── app/                          # 主应用
│   ├── src/main/java/            # Java 源码（112 个文件）
│   ├── src/main/kotlin/          # Kotlin 源码（18 个文件）
│   ├── src/main/res/             # 资源文件
│   ├── src/main/assets/          # modules.json, game_configs.json
│   ├── src/test/                 # 单元测试（14 个文件）
│   ├── src/androidTest/          # UI 自动化测试（47 个文件）
│   ├── build.gradle              # 应用构建配置
│   └── proguard-rules.pro        # R8 混淆规则
├── core/                         # 核心模块
│   ├── common/                   # 通用基础模块
│   ├── network/                  # 网络模块
│   ├── update/                   # 更新模块
│   ├── security/                 # 安全模块（证书绑定）
│   ├── module-host/              # 模块宿主框架
│   ├── moduleloader/             # 模块加载引擎
│   ├── modulestore/              # 模块商店
│   └── online/                   # 联机公共模块
├── module-store/feature/         # 功能模块
│   ├── games/                    # 游戏模块
│   └── tools/                    # 工具模块（browser, tools, ai, vpn）
├── docs/                         # 文档
├── scripts/                      # 构建脚本
├── tools/                        # 工具脚本
├── version.properties            # 版本配置
├── local.properties              # 本地配置（不提交 Git）
└── keystore.properties           # 签名配置（不提交 Git）
```

---

## 5. 关键文件索引

### 5.1 应用入口

| 文件 | 职责 |
|------|------|
| `App.java` | Application 入口，全局初始化 |
| `MainActivity.java` | 主界面，底部导航 + 更新检查 |
| `SplashActivity.java` | 启动页（已适配 SplashScreen API） |
| `SettingsManager.java` | SharedPreferences 封装 |
| `PermissionHelper.java` | 权限管理（含 POST_NOTIFICATIONS） |

### 5.2 游戏系统

| 文件 | 职责 |
|------|------|
| `GameRegistry.java` | 游戏注册中心（静态 + @GameEntry + 动态） |
| `BaseGameActivity.java` | 游戏基类（屏幕方向锁定） |
| `games/gomoku/` | 五子棋（UI/AI/音效/状态保存） |
| `games/doudizhu/` | 斗地主（单机 + 三模联机） |
| `games/chinesechess/` | 中国象棋 |
| `games/go/` | 围棋 |

### 5.3 模块系统

| 文件 | 职责 |
|------|------|
| `ModuleManager.kt` | 模块管理器（下载/安装/卸载） |
| `ModuleLoaderV2.java` | 模块加载引擎（DexClassLoader） |
| `ModuleDownloader.kt` | 模块下载器（多源/SHA-256 校验） |
| `ModuleResourceLoader.kt` | 模块资源加载（反射私有 API） |
| `ModuleStoreActivity.kt` | 模块商店界面 |

### 5.4 更新系统

| 文件 | 职责 |
|------|------|
| `UpdateChecker.java` | 更新检查器（多源自动切换） |
| `OptimizedUpdateManager` | 缓存、重试、MD5 预检查 |
| `UpdateInstaller.java` | APK 安装器（FileProvider URI） |
| `UpdateViewModel.kt` | 更新 ViewModel（@HiltViewModel） |

### 5.5 安全模块

| 文件 | 职责 |
|------|------|
| `SecureOkHttpFactory.kt` | 证书绑定开关（Release 启用） |
| `SSLHelper.java` | SSL 信任管理 |

### 5.6 联机模块

| 文件 | 职责 |
|------|------|
| `GameSocketServer.java` | 房主权威服务器 |
| `GameSocketClient.java` | 客户端连接管理 |
| `RelayHttpClient.java` | HTTP Relay + WebSocket |
| `OnlineRoomManager.java` | 联机房间管理器 |

---

## 6. 游戏列表（28 款内置）

### 经典类（8 款）
五子棋、中国象棋、围棋、斗地主、21点、跳棋、骰子、石头剪刀布

### 益智类（10 款）
2048、数独、华容道、推箱子、管道、扫雷、消消乐、记忆翻牌、打砖块、拼图

### 休闲类（9 款）
俄罗斯方块、贪吃蛇、Flappy Bird、Brotato、飞机大战、反应测试、猜数字、井字棋、打地鼠

### 特殊
斗地主支持三模联机（TCP + HTTP Relay + WebSocket）

---

## 7. 构建与开发

### 7.1 构建前提
- JDK 17（Android Studio 自带 JBR）
- Android SDK 35
- `keystore.properties`（Release 构建需要）

### 7.2 常用命令
```bash
# Debug 构建
.\gradlew.bat :app:assembleDebug

# Release 构建
.\gradlew.bat :app:assembleRelease

# 单元测试
.\gradlew.bat :app:test

# Lint 检查
.\gradlew.bat :app:lintDebug

# UI 自动化测试
adb shell am instrument -w -r -e class com.gamecenter.app.tests.* com.gamecenter.app.test/androidx.test.runner.AndroidJUnitRunner

# 清理构建
.\gradlew.bat clean
```

### 7.3 Git 操作
```bash
# 代理配置（GitHub 需要）
git config --global http.https://github.com.proxy http://127.0.0.1:10808

# 基本操作
git add .
git commit -m "说明"
git push origin main
```

### 7.4 发布流程
```bash
# 上传到 VPS
python tools/upload_to_vps.py --channel release

# 上传到 GitHub Releases
python tools/upload_to_github_release.py --apk app/build/outputs/apk/release/app-release.apk --version-name 1.4.1
```

---

## 8. 配置文件说明

### 8.1 version.properties
```properties
versionCode=500          # 内部版本号（每次构建自动递增）
versionName=1.4.1        # 展示版本号
lastStableVersionCode=465
lastStableVersionName=1.4.0
betaNoticeVersionGap=3
```

### 8.2 local.properties（不提交 Git）
```properties
server.url=https://hk-update.<DOMAIN>
ws.url=wss://hk-ws.<DOMAIN>/ddz-ws
relay.url=https://hk-relay.<DOMAIN>/api/ddz-relay
feedback.url=https://<DOMAIN>/api/feedback
```

### 8.3 BuildConfig 字段
- `SERVER_URL`: 主更新源 URL
- `MODULES_URL`: 模块清单 URL
- `DOWNLOAD_BASE_URL`: 模块下载基础 URL
- `TEST_MODE`: 测试模式开关（Debug 默认 true）

---

## 9. 注意事项

### 9.1 不要做的事
- 不要删除斗地主包（主入口，GameRegistry 引用依赖）
- 不要将 LeakCanary 改为默认启用（蓝叠模拟器崩溃）
- 不要禁用 R8 混淆（已修复 BreakoutActivity$1 问题）
- 不要恢复美国 VPS 配置（已下线）
- 不要关闭 ABI splits（除非需要模拟器测试）
- 不要将 `abortOnError` 改回 false

### 9.2 向后兼容
- `UPDATE_SOURCE_VPS_US` 常量保留用于旧用户自动回退
- 旧用户选择美国 VPS 时自动显示为"自动"
- 模块加载失败时返回 null，调用方需处理

### 9.3 模拟器注意事项
- ABI splits 启用后 x86_64 模拟器无法安装（需临时改回 `enable false`）
- LeakCanary 默认关闭，需通过 `-PleakCanary=true` 显式启用
- Debug 包默认开启 TEST_MODE，跳过权限弹窗

---

## 10. 文档索引

| 文档 | 用途 |
|------|------|
| `README.md` | 项目总览、功能介绍 |
| `CHANGELOG.md` | 版本更新历史 |
| `CODE_WIKI.md` | 详细代码架构说明 |
| `修改记录.md` | 16 轮修复循环的完整变更历史 |
| `docs/PROJECT_STATUS.md` | 项目当前状态总览（合并自 7 个审计文档） |
| `docs/DOCUMENTATION_INDEX.md` | 文档统一索引 |
| `docs/PUBLISH_GUIDE.md` | 发布指南 |
| `docs/SECURITY.md` | 安全文档 |
| `docs/NETWORK_LAYER.md` | 网络层文档 |

---

[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)
