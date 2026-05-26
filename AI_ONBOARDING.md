?# GameMatrixApp AI 代理完整接手指南

> 本文档是 AI 代理接手�?*唯一入口文档**，包�?VPS 连接、发布流程、GitHub 操作、本地构建、模块市场等所有关键信息�?
## 2026-05-24 模块市场调整

- 新安装包的游戏大厅只默认提供 **五子�?* �?**斗地�?* 两个基础游戏�?- 其它游戏统一进入模块市场，用户下载对应游戏模块后才会注册到大厅，包括 2048、数独、推箱子、管道连接、扫雷、围棋、贪吃蛇、俄罗斯方块、Brotato、打砖块、打地鼠、配对消除�?1 点、跳棋、Flappy Bird、白块、飞机大战、石头剪刀布、反应力测试、井字棋、记忆翻牌、猜数字、骰子�?- 模块清单维护位置：`deploy/modules.json`；App 离线兜底清单：`app/src/main/assets/modules.json`，两者需要保持一致�?- 每个扩展游戏的下载包放在 `deploy/modules/`，清单中�?`fileSize` �?`sha256` 必须与实际文件一致。除五子棋和斗地主外，不要再把游戏条目标记为 `builtIn=true`�?- 当前实现中，�?2048 的扩展游戏下载包是安装授�?资源占位包，下载完成后由�?App 中的模块注册逻辑启用对应入口�?048 使用独立 `feature:games:game2048` APK 模块并通过 `DynamicGameActivity` 加载�?> 读完本文档后，AI 代理应能独立完成日常开发、构建、发布全流程�?
---

## 1. 项目基本信息

| 项目 | �?|
|------|-----|
| 项目名称 | GameMatrixApp |
| GitHub 仓库 | `https://github.com/3571949306/GameMatrixApp.git` |
| 主维护分�?| `main` |
| 当前版本 | `v1.4.0` (versionCode=294) |
| 本地 Java 路径 | `C:\Program Files\Android\Android Studio\jbr` |
| 工作目录 | `d:\kaifa\GameCenterApp` |
| 操作系统 | Windows (PowerShell) |
| 代理端口 | `127.0.0.1:10808` (v2rayN/xray，仅 GitHub) |

---

## 2. 必读文档索引

| 文档 | 用�?|
|------|------|
| `README.md` | 项目总览、功能介�?|
| `PROJECT_CONTEXT.md` | 代码结构、开发规范、上下文信息 |
| `CODE_WIKI.md` | 详细代码架构说明 |
| `AI_CONTEXT.md` | AI 助手模块上下�?|
| `CHANGELOG.md` | 版本更新历史 |
| `项目改进建议�?md` | 改进记录和待办事�?|
| `game_center_app_ai_roadmap.md` | AI 开发路线图 |
| `文档/PUBLISH_GUIDE.md` | 发布系统历史说明 |
| `文档/LOCAL_GITHUB_NETWORK.md` | GitHub 网络和代理配�?|

---

## 3. 连接 VPS

### 3.1 VPS 列表

| VPS | 地址 | 用�?| 公网域名 |
|-----|------|------|---------|
| 香港 VPS | `149.104.29.181` | 主更新源 | `https://hk-update.tcp0053.shop` |
| 美国 VPS | 备用 | 备用更新�?| `https://tcp0053.shop:1443` |

### 3.2 VPS 凭证

凭证存储在本地私有目录（不进 Git）：
```
local_private/服务器部署/upload_config_hk.json
local_private/服务器部署/upload_config_us.json
```

配置格式示例�?```json
{
  "host": "服务器IP",
  "port": 22,
  "user": "root",
  "authMethod": "password",
  "password": "密码",
  "remoteDir": "/var/www/update/app",
  "publicBaseUrl": "https://对应公网域名",
  "postUploadCommands": []
}
```

### 3.3 SSH 连接方式

```powershell
ssh -o StrictHostKeyChecking=no root@149.104.29.181
```

SCP 传输文件�?```powershell
scp -o StrictHostKeyChecking=no "本地文件路径" root@149.104.29.181:/远程路径
```

### 3.4 VPS 目录结构

```
/var/www/update/
├── app/                          # APK 安装�?�?  ├── app-beta.apk              # 测试�?�?  ├── version-beta.json         # 测试版元数据
�?  ├── app-stable.apk            # 正式�?�?  ├── version-stable.json       # 正式版元数据
�?  └── version-release.json      # 正式版元数据（同步）
├── modules.json                  # 模块清单�?2个模块）
└── modules/                      # 模块 dex 文件目录
    └── minesweeper-v1.dex        # 示例：扫雷模�?```

### 3.5 上传 APK �?VPS

```powershell
# 安装依赖
python -m pip install paramiko

# 上传测试�?python 工具\\upload_to_vps.py --channel beta

# 上传正式�?python 工具\\upload_to_vps.py --channel release
```

### 3.6 上传后验�?
确认以下 URL 可访问：
- `https://hk-update.tcp0053.shop/version-beta.json`
- `https://hk-update.tcp0053.shop/version-release.json`
- `https://hk-update.tcp0053.shop/modules.json`

---

## 4. 连接 GitHub

### 4.1 远程仓库

```
origin: https://github.com/3571949306/GameMatrixApp.git
分支: main
```

### 4.2 GitHub 代理配置（必须）

本机访问 GitHub 需要通过本地代理 `127.0.0.1:10808`。Git 已配�?GitHub-only 代理�?
```powershell
# 验证代理是否生效
git config --global --get http.https://github.com.proxy

# 如果没有，重新配�?git config --global http.https://github.com.proxy http://127.0.0.1:10808

# 测试连接
git ls-remote https://github.com/3571949306/GameMatrixApp.git HEAD
```

### 4.3 重新检测代�?
如果代理失效�?```powershell
powershell -ExecutionPolicy Bypass -File 工具\\network\Configure-GitHubProxy.ps1 -Apply
```

### 4.4 清除代理

```powershell
powershell -ExecutionPolicy Bypass -File 工具\\network\Configure-GitHubProxy.ps1 -Clear
```

### 4.5 Gradle 也需要代理（临时�?
Git 代理不影�?Gradle 下载依赖。如�?Gradle 需要走代理�?```powershell
$env:GRADLE_OPTS='-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=10808 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=10808'
```

### 4.6 基本 Git 操作

```powershell
git add .
git commit -m "提交说明"
git push origin main
```

### 4.7 上传 GitHub Releases

```powershell
python 工具\\upload_to_github_release.py `
  --apk app\build\outputs\apk\release\app-release.apk `
  --version-name 1.4.0 `
  --changelog-file CHANGELOG.md
```

Token 优先级：`--token` 参数 �?`GITHUB_TOKEN` 环境变量 �?`local_private/github/token.txt`

---

## 5. 本地构建与发布流�?
### 5.1 构建前提

- Java 21: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'`
- Gradle Wrapper: `.\gradlew.bat`
- 签名配置: `keystore.properties` (根目�?
- 签名文件: `GameMatrix.keystore` (根目�?

### 5.2 编译 Debug（测试用�?
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd d:\kaifa\GameCenterApp
.\gradlew.bat :app:assembleDebug
```

### 5.3 编译 Release（发布用�?
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd d:\kaifa\GameCenterApp
.\gradlew.bat :app:assembleRelease
```

### 5.4 安装到设�?模拟�?
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-工具\\adb.exe" install -r "d:\kaifa\GameCenterApp\app\build\outputs\apk\release\app-release.apk"
```

> 注意：如果模拟器上已�?debug 版（签名不同），需要先卸载�?> ```powershell
> & "$env:LOCALAPPDATA\Android\Sdk\platform-工具\\adb.exe" uninstall com.gamecenter.app
> ```

### 5.5 运行测试

```powershell
.\gradlew.bat :app:test -PautoBumpVersion=false
```

### 5.6 完整发布流程（推荐分步）

**测试版发�?*�?```powershell
# 1. 编译
.\gradlew.bat :app:assembleRelease -PupdateChannel=beta
# 2. 生成版本元数�?.\gradlew.bat :app:generateVersionJson -PupdateChannel=beta
# 3. 上传 VPS
python 工具\\upload_to_vps.py --channel beta
```

**正式版发�?*�?```powershell
# 1. 编译
.\gradlew.bat :app:assembleRelease -PupdateChannel=stable
# 2. 生成版本元数�?.\gradlew.bat :app:generateVersionJson -PupdateChannel=stable
# 3. 上传 VPS
python 工具\\upload_to_vps.py --channel release
# 4. 上传 GitHub Releases
python 工具\\upload_to_github_release.py `
  --apk app\build\outputs\apk\release\app-release.apk `
  --version-name 1.4.0 `
  --changelog-file CHANGELOG.md
```

**一键发布（Gradle 任务�?*�?```powershell
.\gradlew.bat :app:buildAndUploadToVpsAndGitHub -PupdateChannel=stable
```

### 5.7 本地运行完后该干什�?
| 场景 | 下一�?|
|------|--------|
| 代码修改完成 | 运行 `assembleDebug` �?安装到模拟器 �?手动测试功能 |
| 准备发布 | 运行 `assembleRelease` �?上传 VPS �?上传 GitHub Releases |
| 模块市场相关修改 | 修改 `deploy/modules.json` �?上传�?VPS `/var/www/update/modules.json` �?如果有非内置模块，同时上�?dex 文件 |
| 更新文档 | 修改对应 md 文件 �?`git add .` �?`git commit` �?`git push` |

---

## 6. 模块市场系统（核心功能）

### 6.1 架构概览

```
游戏大厅 �?模块市场入口按钮 �?ModuleStoreActivity
                                    �?                          ModuleManager（下�?安装/启用/卸载�?                                    �?                          GameRegistry（动态注册游戏）
                          MainActivity（动态导航栏�?```

### 6.2 模块类型

| type | 说明 | 效果 |
|------|------|------|
| `nav` | 导航模块 | 安装后底部导航栏自动出现对应Tab（浏览器/工具�?AI�?|
| `game` | 游戏模块 | 安装后游戏自动注册到GameRegistry并出现在游戏大厅 |

### 6.3 内置模块 vs 下载模块（⚠�?关键�?
当前所�?22 个模块的**代码都在�?APK �?*，因此全部标记为 `builtIn: true`�?
| 属�?| 内置模块 (builtIn=true) | 下载模块 (builtIn=false) |
|------|------------------------|-------------------------|
| 代码位置 | �?APK 源码目录 | 独立 dex 文件（通过 DexClassLoader 加载�?|
| downloadUrl | **留空** `""` | 必须指向 VPS 上实际存在的 dex 文件 |
| fileName | **留空** `""` | dex 文件名（�?`browser-v1.dex`�?|
| fileSize | `0` | 实际文件大小（字节） |
| sha256 | **留空** `""` | 实际 SHA-256 哈希�?|
| storeCategory | game/browser/工具/ai/vpn | game/browser/工具/ai/vpn |
| isBaseFramework | true/false | true/false |
| 市场按钮文字 | "启用"（蓝色） | "下载" |
| 用户操作 | 点击"启用"直接标记已安�?| 点击下载 dex 文件后安�?|

### 6.4 当前模块清单

| 模块 | 类型 | builtIn | storeCategory | isBaseFramework | 说明 |
|------|------|---------|---------------|-----------------|------|
| browser | nav | true | browser | true | 浏览器基础框架（代码在主APK中） |
| tools | nav | true | tools | true | 工具箱框架（代码在主APK中） |
| ai | nav | true | ai | true | AI基础框架（代码在主APK中） |
| vpn | nav | true | vpn | true | VPN基础服务占位模块 |
| 2048 | game | true | game | false | 益智游戏 |
| sudoku | game | true | game | false | 益智游戏 |
| sokoban | game | true | game | false | 益智游戏 |
| pipeline | game | true | game | false | 益智游戏 |
| klotski | game | true | game | false | 益智游戏 |
| minesweeper | game | true | game | false | 益智游戏 |
| breakout | game | true | game | false | 休闲游戏 |
| whack | game | true | game | false | 休闲游戏 |
| match | game | true | game | false | 休闲游戏 |
| blackjack | game | true | game | false | 休闲游戏 |
| checkers | game | true | game | false | 休闲游戏 |
| flappy | game | true | game | false | 休闲游戏 |
| tiles | game | true | game | false | 休闲游戏 |
| plane | game | true | game | false | 休闲游戏 |
| rock | game | true | game | false | 休闲游戏 |
| reaction | game | true | game | false | 休闲游戏 |
| tic | game | true | game | false | 休闲游戏 |
| memory | game | true | game | false | 休闲游戏 |
| guess | game | true | game | false | 休闲游戏 |
| dice | game | true | game | false | 休闲游戏 |

### 6.5 如何让用户下载新模块

**情况 A：新模块代码在主 APK 中（当前所有模块都是这种情况）**

1. 在主 APK 源码中创建模块的 Activity/Fragment/EntryPoint
2. �?`deploy/modules.json` 中添加模块条目，设置 `"builtIn": true`
3. 清空 `downloadUrl`、`fileName`、`sha256`，`fileSize` �?0
4. 上传 `modules.json` �?VPS�?   ```powershell
   scp -o StrictHostKeyChecking=no "d:\kaifa\GameCenterApp\deploy\modules.json" root@149.104.29.181:/var/www/update/modules.json
   ```
5. 用户在市场看到模块后点击"启用"即可�?*无需下载 dex 文件**

**情况 B：新模块是完全独立的 dex 文件（未来真正模块化时）**

1. 独立编译模块代码�?dex 文件（使�?d8 工具�?2. 计算 dex 文件�?SHA-256 和大�?3. �?`deploy/modules.json` 中添加模块条目，设置 `"builtIn": false`
4. 填写真实�?`downloadUrl`、`fileName`、`fileSize`、`sha256`
5. 上传 `modules.json` �?VPS
6. 上传 dex 文件�?VPS �?`/var/www/update/modules/` 目录
7. 验证 VPS 上的 dex 文件可通过 downloadUrl 访问

### 6.6 内置游戏（GameRegistry 静态注册）

初始安装包仅内置 7 款经典游戏（直接显示在游戏大厅，不需要市场启用）�?- 五子棋、围棋、中国象棋、贪吃蛇、俄罗斯方块、斗地主、Brotato

其余 19 款游戏通过市场"启用"后自动出现在对应分类（益�?休闲）�?
### 6.7 模块市场分类与已安装列表

模块市场顶部有分类Tab：全部、游戏、浏览器、工具箱、AI助手、VPN�?- 点击分类Tab只显示对应storeCategory的模�?- 基础框架模块（isBaseFramework=true）在所属分类内置顶显示
- 标题栏右侧有"已安装模�?按钮，点击打开InstalledModulesActivity
- 已安装列表显示所有已安装模块，支持更�?卸载操作

模块分类规则�?| storeCategory | 说明 | 示例 |
|---------------|------|------|
| game | 游戏模块 | 2048、数独、扫雷等 |
| browser | 浏览器模�?| 浏览器基础框架 |
| tools | 工具箱模�?| 工具箱框�?|
| ai | AI助手模块 | AI基础框架 |
| vpn | VPN模块 | VPN基础服务 |

---

## 7. 模块市场开发约束（⚠️ 必读，禁止违反）

> ⚠️ 以下规则是因历史 bug 总结的约束。违反这些规则会导致模块市场下载失败�?04 错误等严重问题�?
1. **禁止创建指向不存在文件的 downloadUrl**
   - `modules.json` 中的 `downloadUrl` 必须指向 VPS �?*实际存在**的文�?   - 如果模块代码在主 APK 中，必须设置 `"builtIn": true` 并将 `downloadUrl` 留空
   - **错误示例**：`"downloadUrl": "https://.../browser-v1.dex"`（VPS 上没有这个文件）

2. **builtIn 模块不需�?dex 文件**
   - �?`builtIn=true` 时，ModuleAdapter 显示"启用"按钮而非"下载"
   - ModuleManager 直接标记已安装，不走下载流程
   - **不要为内置模块编�?dex 文件**

3. **新增模块时先确认代码位置**
   - 如果模块�?Activity/Fragment 代码�?`app/src/main/java/com/gamecenter/app/` 目录中，该模块必须标记为 `builtIn=true`
   - 只有代码完全独立于主 APK（通过 DexClassLoader 动态加载）的模块才能设�?`builtIn=false`

4. **上传 modules.json 前必须验�?*
   - 每次修改 `deploy/modules.json` 后，必须同时�?     - (a) 上传�?VPS �?`/var/www/update/modules.json`
     - (b) 如果有非 builtIn 模块，确保对�?dex 文件已上传到 VPS �?`/var/www/update/modules/` 目录

5. **fileSize �?sha256 必须真实**
   - �?builtIn 模块�?`fileSize` �?`sha256` 必须�?VPS 上实�?dex 文件一�?   - 不能留空或填 0（否�?SHA-256 校验会通过但文件可能不完整�?   - builtIn 模块�?`fileSize` �?0、`sha256` 留空

---

## 8. 日常开发工作流

### 8.1 修改代码 �?测试 �?提交

```powershell
# 1. 设置 Java 环境
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd d:\kaifa\GameCenterApp

# 2. 编译 Debug 版本
.\gradlew.bat :app:assembleDebug

# 3. 安装到模拟器
& "$env:LOCALAPPDATA\Android\Sdk\platform-工具\\adb.exe" install -r "d:\kaifa\GameCenterApp\app\build\outputs\apk\debug\app-debug.apk"

# 4. 手动测试功能

# 5. 运行单元测试
.\gradlew.bat :app:test -PautoBumpVersion=false

# 6. 提交代码
git add .
git commit -m "描述修改内容"
git push origin main
```

### 8.2 发布新版�?
```powershell
# 1. 确认 version.properties 版本号已更新
# 2. 确认 CHANGELOG.md 已更�?# 3. 编译 Release
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd d:\kaifa\GameCenterApp
.\gradlew.bat :app:assembleRelease

# 4. 上传 VPS
python 工具\\upload_to_vps.py --channel release

# 5. 上传 GitHub Releases
python 工具\\upload_to_github_release.py `
  --apk app\build\outputs\apk\release\app-release.apk `
  --version-name 版本�?`
  --changelog-file CHANGELOG.md

# 6. 验证
#    - https://hk-update.tcp0053.shop/version-release.json
#    - https://github.com/3571949306/GameMatrixApp/releases
```

### 8.3 修改模块市场�?
```powershell
# 1. 修改 deploy/modules.json
# 2. 上传�?VPS
scp -o StrictHostKeyChecking=no "d:\kaifa\GameCenterApp\deploy\modules.json" root@149.104.29.181:/var/www/update/modules.json

# 3. 验证
ssh -o StrictHostKeyChecking=no root@149.104.29.181 "head -c 500 /var/www/update/modules.json"
```

---

## 9. 常见问题排查

### 9.1 Gradle 编译失败

| 错误 | 解决方案 |
|------|---------|
| `No buffer space available` | 检�?xray/v2rayN 是否占用过多 UDP 端口，关�?TUN 模式或重启代�?|
| `lintVitalReportRelease` 失败 | 使用 `-PskipReleaseLint=true` 跳过 lint 检�?|
| 签名配置缺失 | 确认根目录有 `keystore.properties` �?`GameMatrix.keystore` |

### 9.2 GitHub 连接失败

```powershell
# 1. 确认代理进程运行（v2rayN 窗口是否打开�?# 2. 重新配置代理
powershell -ExecutionPolicy Bypass -File 工具\\network\Configure-GitHubProxy.ps1 -Apply
# 3. 测试
git ls-remote https://github.com/3571949306/GameMatrixApp.git HEAD
```

### 9.3 VPS 连接失败

```powershell
# 1. 测试 SSH
ssh -o StrictHostKeyChecking=no -o ConnectTimeout=10 root@149.104.29.181 "echo ok"
# 2. 确认 local_private/服务器部署/upload_config_hk.json 存在且正�?# 3. 确认 paramiko 已安�?python -m pip install paramiko
```

### 9.4 模块市场显示异常

| 问题 | 原因 | 解决 |
|------|------|------|
| 只显示少数模�?| VPS �?modules.json 未更�?| 重新上传 modules.json �?VPS |
| 下载失败 404 | downloadUrl 指向不存在的文件 | 确认模块是否�?builtIn，设置正�?|
| 界面被状态栏遮挡 | 缺少 fitsSystemWindows | 检查布局文件是否设置 `android:fitsSystemWindows="true"` |
| 启用后游戏不出现 | GameRegistry 未注�?| 检�?`registerInstalledGameModules()` 是否调用 |

---

## 10. 项目代码结构速查

```
GameCenterApp/
├── app/src/main/java/com/gamecenter/app/
�?  ├── MainActivity.java              # �?Activity（动态导航栏�?�?  ├── App.java                       # Application 入口
�?  ├── SettingsManager.java           # 全局设置
�?  ├── fragments/
�?  �?  ├── GamesFragment.java         # 游戏大厅（含模块市场入口�?�?  �?  ├── BrowserFragment.java       # 浏览器（模块安装后显示）
�?  �?  ├── ToolsFragment.java         # 工具箱（模块安装后显示）
�?  �?  └── AiFragment.java            # AI助手（模块安装后显示�?�?  ├── games/                         # 所有游戏代�?�?  �?  ├── GameRegistry.java          # 游戏注册中心（三轨制�?�?  �?  ├── gomoku/                    # 五子棋（内置经典�?�?  �?  ├── minesweeper/               # 扫雷（市场启用）
�?  �?  └── ...                        # 其他游戏
�?  ├── modules/                       # 模块市场系统
�?  �?  ├── ModuleManager.kt           # 模块管理�?�?  �?  ├── ModuleManifest.kt          # 模块数据模型（含 builtIn 字段�?�?  �?  ├── ModuleStoreActivity.kt     # 市场页面
�?  �?  ├── ModuleAdapter.kt           # 列表适配�?�?  �?  ├── ModuleDownloader.kt        # 下载�?�?  �?  ├── BrowserModuleEntryPoint.kt # 浏览器入�?�?  �?  ├── ToolsModuleEntryPoint.kt   # 工具箱入�?�?  �?  └── AiModuleEntryPoint.kt      # AI 入口
�?  └── ai/                            # AI 助手模块
├── deploy/
�?  └── modules.json                   # 模块清单�?2个模块）
├── 工具/
�?  ├── upload_to_vps.py               # VPS 上传脚本
�?  ├── upload_to_github_release.py    # GitHub Release 上传脚本
�?  └── network/
�?      └── Configure-GitHubProxy.ps1  # GitHub 代理配置脚本
├── local_private/                     # 本地私有配置（不�?Git�?�?  ├── 服务器部署/
�?  �?  ├── upload_config_hk.json
�?  �?  └── upload_config_us.json
�?  └── github/
�?      └── token.txt
├── version.properties                 # 版本�?└── keystore.properties                # 签名配置
```

---

## 11. 关键文件路径速查

| 用�?| 本地路径 | VPS 路径 |
|------|---------|---------|
| Release APK | `app\build\outputs\apk\release\app-release.apk` | `/var/www/update/app/app-stable.apk` |
| 版本元数�?| `app\build\outputs\apk\release\version.json` | `/var/www/update/app/version-stable.json` |
| 模块清单 | `deploy\modules.json` | `/var/www/update/modules.json` |
| 模块 dex | `app\build\outputs\dex\*.dex` | `/var/www/update/modules/*.dex` |
| VPS 配置 | `local_private\服务器部署\\upload_config_hk.json` | - |
| GitHub Token | `local_private\github\token.txt` | - |

---

## 12. 一句话总结

这个项目当前已具备完整的本地构建、VPS 上传、GitHub Releases 发布链路。AI 代理接手时：
1. **先看本文�?*，了�?服务器部署/GitHub/构建/模块市场全貌
2. **复用现有脚本**（`工具/upload_to_vps.py`、`工具/upload_to_github_release.py`），不要重新发明流程
3. **遵守模块市场约束**（第 7 节），特别是 builtIn 规则和 downloadUrl 验证
4. **文档同步**，每次修改代码后更新对应 md 文件

## 2026-05-24 文档同步
- 底部导航切换闪退修复：创建 KeepStateNavigator 自定义导航器，使用 add/show/hide 策略替代 Navigation 默认 replace 策略
- 模块下载修复：ModuleDownloader 全面重写，添加全局异常捕获、降低超时、增加日志
- 内存泄漏全面修复：移除 WeakReference callback、Fragment 回调安全检查、视图引用彻底清理
- 压力测试通过：40轮快速Tab切换无崩溃


