# 项目AI接手说明

## 1. 当前项目概况

- 项目名称：`GameMatrixApp`
- GitHub 仓库：`https://github.com/3571949306/GameMatrixApp.git`
- 主维护分支：`main`
- Android 构建核心目录：`app/`
- 发布相关脚本目录：`工具/`、`文档/`、`local_private/`

建议 AI 或新维护者接手时，先读这几份文档�?

- `README.md`：项目总览、更新分发架构、文档入�?
- `PROJECT_CONTEXT.md`：维护约束、仓库规则、接手背�?
- `CODE_WIKI.md`：代码结构与模块说明
- `文档/LOCAL_GITHUB_NETWORK.md`：本�?GitHub 访问与代理说�?
- `文档/PUBLISH_GUIDE.md`：发布链路历史说�?

## 2. 当前发布架构

当前项目的安装包分发是两路结构（2026-06-19 起 US VPS 已下线）：

1. 香港 VPS：主更新源
2. GitHub Releases：正式版公开分发及备用更新源

版本策略分两类：

- `beta`：上传到香港 VPS
- `stable/release`：上传到香港 VPS、GitHub Releases

VPS 上的更新目录约定为：

```text
/var/www/update/app/
├── app-beta.apk
├── version-beta.json
├── app-release.apk
└── version-release.json
```

模块市场相关文件�?
/var/www/modules/
├── modules.json              # 模块清单（当前为 29 个模块）
├── modules/                  # 模块包实际服务目�?
�?  ├── feature_game2048_v100.apk
�?  └── game_sudoku_v100.zip

/var/www/update/
└── modules/                  # 兼容备份目录，发布时同步模块�?

本地构建产物默认使用�?

```text
app/build/outputs/apk/release/app-release.apk
app/build/outputs/apk/release/version.json
```

## 3. 如何连接 VPS

### 3.1 配置文件位置

VPS 凭证不进 Git，放在：

```text
local_private/服务器部�?
```

脚本会自动读取：

- `upload_config_hk.json`
- `upload_config_us.json（2026-06-19 已废弃，US VPS 下线）`
- 其他匹配 `upload_config_*.json` 的文�?

每个配置至少应包含这些字段：

```json
{
  "host": "服务器地址",
  "port": 22,
  "user": "root",
  "authMethod": "password �?key",
  "password": "仅本地保�?,
  "identityFile": "可选，私钥路径",
  "remoteDir": "/var/www/update/app",
  "publicBaseUrl": "https://对应公网下载地址",
  "postUploadCommands": []
}
```

### 3.2 实际上传脚本

仓库当前用于连接 VPS 的脚本是�?

```text
工具/upload_to_vps.py
```

它使�?`paramiko` 通过 SSH/SFTP 连接 VPS，默认会�?

1. 读取 `local_private/服务器部�?upload_config_*.json`
2. 上传 `app-release.apk` �?`version.json`
3. 在远端重命名为通道文件�?
4. 清理旧的 `.apk` / `.json` 文件，但保留 `beta` �?`release` 两套当前文件
5. �?`publicBaseUrl` 校验公网可访问�?

### 3.3 常用命令

先安装依赖：

```powershell
python -m pip install paramiko
```

上传测试版到所有已配置 VPS�?

```powershell
python 工具\\upload_to_vps.py --channel beta
```

上传正式版到所有已配置 VPS�?

```powershell
python 工具\\upload_to_vps.py --channel release
```

如果要指定文件路径：

```powershell
python 工具\\upload_to_vps.py `
  --apk app\build\outputs\apk\release\app-release.apk `
  --version app\build\outputs\apk\release\version.json `
  --channel release
```

### 3.4 VPS 上传后的检�?

至少检查这几个地址是否可访问：

- 香港测试版：`https://your-server.example.com/version-beta.json`
- ~~美国测试版~~（已下线）：~~`https://your-server.example.com:1443/version-beta.json`~~
- 香港正式版：`https://your-server.example.com/version-release.json`
- ~~美国正式版~~（已下线）：~~`https://your-server.example.com:1443/version-release.json`~~

美国备用更新源已于 2026-06-19 下线，无需检查。

## 4. 如何上传�?GitHub

### 4.1 远程仓库

当前 `origin` 为：

```text
https://github.com/3571949306/GameMatrixApp.git
```

普通提交命令：

```powershell
git add .
git commit -m "你的提交说明"
git push origin main
```

### 4.2 本机 GitHub 连接方式

当前机器�?GitHub 访问依赖本地代理，只�?GitHub 生效�?

```powershell
git config --global http.https://github.com.proxy http://127.0.0.1:10808
```

检查方式：

```powershell
git config --global --get http.https://github.com.proxy
git ls-remote https://github.com/3571949306/GameMatrixApp.git HEAD
```

如果代理失效，可重新执行�?

```powershell
powershell -ExecutionPolicy Bypass -File 工具\\network\Configure-GitHubProxy.ps1 -Apply
```

清理 GitHub 专用代理�?

```powershell
powershell -ExecutionPolicy Bypass -File 工具\\network\Configure-GitHubProxy.ps1 -Clear
```

### 4.3 上传 GitHub Releases

当前正式�?APK 上传 GitHub Release 的脚本是�?

```text
工具/upload_to_github_release.py
```

它会按以下顺序取 Token�?

1. `--token`
2. 环境变量 `GITHUB_TOKEN`
3. `local_private/github/token.txt`

示例�?

```powershell
python 工具\\upload_to_github_release.py `
  --apk app\build\outputs\apk\release\app-release.apk `
  --version-name 1.3.27 `
  --changelog-file CHANGELOG.md
```

说明�?

- `--version-name` 会作�?GitHub Release �?tag
- 如果�?tag 已存在，脚本会更�?Release 信息并替换同�?APK 资产
- 该脚本主要用于正式版，不建议把测试包长期发到 GitHub Releases

## 5. 如何发布安装�?

### 5.1 本地构建前提

- 使用 Android Studio / Gradle 环境
- 本机 Java 21 可用
- 根目录存�?`keystore.properties` 和签名文�?
- 不要�?`local_private/` 内的私密配置提交�?GitHub

### 5.2 构建命令

生成 Release APK�?

```powershell
.\gradlew.bat assembleRelease -PupdateChannel=beta
```

生成版本元数据：

```powershell
.\gradlew.bat generateVersionJson -PupdateChannel=beta
```

如果发布正式版，�?`beta` 改成 `stable`�?

```powershell
.\gradlew.bat assembleRelease -PupdateChannel=stable
.\gradlew.bat generateVersionJson -PupdateChannel=stable
```

### 5.3 推荐发布方式

#### 方式一：分步执行，最稳妥

测试版：

```powershell
.\gradlew.bat assembleRelease -PupdateChannel=beta
.\gradlew.bat generateVersionJson -PupdateChannel=beta
python 工具\\upload_to_vps.py --channel beta
```

正式版：

```powershell
.\gradlew.bat assembleRelease -PupdateChannel=stable
.\gradlew.bat generateVersionJson -PupdateChannel=stable
python 工具\\upload_to_vps.py --channel release
python 工具\\upload_to_github_release.py `
  --apk app\build\outputs\apk\release\app-release.apk `
  --version-name 版本�?`
  --changelog-file CHANGELOG.md
```

#### 方式二：直接�?Gradle 发布任务

正式版可直接执行�?

```powershell
.\gradlew.bat :app:buildAndUploadToVpsAndGitHub -PupdateChannel=stable
```

这个任务会串联：

1. `assembleRelease`
2. `generateVersionJson`
3. 上传 VPS
4. 上传 GitHub Releases

如果只是构建后上�?VPS�?

```powershell
.\gradlew.bat :app:uploadReleaseToVps -PupdateChannel=beta
```

### 5.4 发版后验�?

建议至少执行�?

```powershell
.\gradlew.bat :app:test -PautoBumpVersion=false
.\gradlew.bat :app:assembleDebug -PautoBumpVersion=false
```

再检查：

- VPS 上的 `version-beta.json` �?`version-release.json`
- GitHub Releases 页面是否已有目标版本
- App 内实际检查更新是否能拿到对应通道

## 6. 接手时的注意事项

- `local_private/` 是本地私有配置区，不要提交�?
- GitHub 网络问题先看 `文档/LOCAL_GITHUB_NETWORK.md`，不要直接改全局网络方案�?
- 日常测试包优先走 `beta` 通道，不要误发正式版�?GitHub Releases�?
- 正式版发布前确认 `CHANGELOG.md`、`version.properties`、`version.json` 一致�?
- 美国 VPS 已于 2026-06-19 下线，不再使用。
- 如果上传脚本或发布任务异常，优先检�?Python 依赖、GitHub Token、本地代理、VPS 配置文件路径是否正确�?
- 模块市场系统（ModuleStoreActivity + ModuleManager）是新增的核心功能，浏览�?工具�?AI助手和非经典游戏已改为市场下载模块，初始安装包不再自带。修改导航栏或游戏注册逻辑时需注意动态注册机制�?

## 7. 一句话接手结论

这个项目当前已经具备完整的本地构建、香港 VPS 上传、GitHub Releases 发布链路（2026-06-19 起 US VPS 已下线）。AI 接手时，优先复用 `工具/upload_to_vps.py`、`工具/upload_to_github_release.py` �?`app/build.gradle` 里的发布任务，不要重新发明一套发布流程�?

## 8. 模块市场架构说明

- 模块市场入口位于游戏大厅左上角版本号下方�?
- 初始安装包仅内置五子棋和斗地主�?
- 模块市场默认打开“游戏”分类，不再提供“全部”分�?Tab，也不显示“全部”游戏子分类按钮�?
- 模块市场右上角有两个操作按钮：刷新当前列表、进入已下载模块列表；最右侧为已下载列表按钮�?
- 已下载模块列表需要按全部、游戏、浏览器、工具箱、AI助手、VPN 分类展示�?
- 模块市场卡片对已下载模块显示“打开/卸载”两级按钮，可直接快速卸载�?
- 商店下载的游戏安装成功后必须动态注册回游戏大厅，用户返回大厅即可看到并打开�?
- 游戏大厅初始只展示五子棋和斗地主；`GamesFragment` 恢复时需要调�?`registerInstalledGameModules()` 同步已安装商店游戏�?
- 浏览器、工具箱、AI助手改为独立市场模块，初始安装包不再自带�?
- 底部导航栏动态化：默认仅保留"游戏"Tab，安装对应导航模块后自动出现浏览�?工具�?AI Tab�?
- 非内置游戏通过市场下载安装后注册到 `GameRegistry`，并出现在游戏大厅�?
- VPS更新服务器已新增/modules.json�?modules/路由�?
- 模块包已发布�?VPS 实际服务目录 `/var/www/modules/modules/`，并同步�?`/var/www/update/modules/` 作为兼容备份�?
- ModuleManifest新增builtIn字段：代码在主APK中的模块标记builtIn=true，显�?启用"按钮而非"下载"，无需下载dex文件�?

### 8.1 核心�?
| �?| 语言 | 职责 |
|----|------|------|
| ModuleManager | Kotlin | 模块下载/安装/注册 |
| ModuleManifest | Kotlin | 模块清单数据模型 |
| ModuleStoreActivity | Kotlin | 模块市场页面 |
| ModuleAdapter | Kotlin | 模块列表适配�?|
| InstalledModulesActivity | Kotlin | 已下载模块列表页�?|
| InstalledModuleAdapter | Kotlin | 已下载模块列表适配�?|

### 8.2 模块市场架构
- 模块市场顶部分类 Tab：游戏、浏览器、工具箱、AI助手、VPN
- 模块市场不提供“全部”分�?Tab，游戏分类内也不显示“全部”子分类按钮�?
- 标题栏右侧有“刷新”和“已下载模块”按钮，Toolbar 菜单必须显式绑定点击监听�?
- ModuleManifest新增storeCategory和isBaseFramework字段
- 浏览�?工具�?AI拆分为基础框架+扩展功能

### 8.3 模块类型
| type | 说明 | 效果 |
|------|------|------|
| nav | 导航模块 | 安装后底部导航栏自动出现对应Tab |
| game | 游戏模块 | 安装后注册到 GameRegistry，并回流显示到游戏大�?|
| builtIn=true | 内置模块 | 代码在主APK中，显示"启用"按钮，无需下载 |

### 8.4 模块分类规则
| storeCategory | 说明 | 示例 |
|---------------|------|------|
| game | 游戏模块 | 2048、数独、扫雷等 |
| browser | 浏览器模�?| 浏览器基础框架 |
| tools | 工具箱模�?| 工具箱框�?|
| ai | AI助手模块 | AI基础框架 |
| vpn | VPN模块 | VPN基础服务 |

### 8.5 游戏模块打开流程

1. `ModuleStoreActivity` 默认加载 `storeCategory=game` 的模块列表�?
2. 用户点击“下载”后，模块包保存�?App 私有目录并标记已安装�?
3. `ModuleManager` 标记安装成功后调�?`registerInstalledGameModules()`，将游戏动态注册到 `GameRegistry`�?
4. 用户返回游戏大厅时，`GamesFragment.onResume()` 重新同步模块游戏并刷新分类与列表�?
5. 用户可从模块市场直接打开，也可从游戏大厅打开已安装的模块游戏�?
6. 用户卸载模块后，`ModuleManager.uninstallModule()` 需要同步移除下载文件、安装标记和 `GameRegistry` 动态注册项�?

## 9. 模块市场开发约束（AI代理必读�?

> ⚠️ 以下规则是因历史bug总结的约束，AI代理在修改模块市场相关代码时必须遵守�?

1. **禁止创建指向不存在文件的downloadUrl**：modules.json中的downloadUrl必须指向VPS上实际存在的文件。如果模块代码在主APK中，必须设置`builtIn: true`并将downloadUrl留空�?
2. **builtIn模块不需要dex文件**：当`builtIn=true`时，ModuleAdapter显示"启用"按钮而非"下载"，ModuleManager直接标记已安装。不要为内置模块编译dex文件�?
3. **新增模块时先确认代码位置**：如果模块的Activity/Fragment代码在主APK的源码目录中，该模块必须标记为`builtIn=true`。只有代码完全独立于主APK（通过DexClassLoader动态加载）的模块才能设置`builtIn=false`并提供downloadUrl�?
4. **上传modules.json前验�?*：每次修改deploy/modules.json后，必须同时�?a) 上传到VPS�?var/www/update/modules.json�?b) 如果有非builtIn模块，确保对应dex文件已上传到VPS�?var/www/update/modules/目录�?
5. **fileSize和sha256必须真实**：非builtIn模块的fileSize和sha256必须与VPS上实际dex文件一致，不能留空或填0。builtIn模块的fileSize�?、sha256留空�?

- 2026-05-24 游戏美化+中国象棋提示改进+华容�?中国象棋模块商店上架：四个游戏视觉美化（斗地主径向渐变桌�?五子棋木�?D棋子/华容道深色渐变金色边�?中国象棋木纹角标波浪线）；中国象棋提示改为棋盘可视化（蓝色脉冲光�?箭头指引�?中文棋谱描述；华容道和中国象棋创建独立APK模块（feature/games/klotski、feature/games/chinesechess）v2.0.0上架模块商店

## 10. 2026-05-27 架构改造说明（SOP全流程交付）

> 本章节描�?2026-05-25 ~ 2026-05-27 期间，通过软件团队 SOP 流程完成的系统架构改造。详细设计见 `文档/架构设计-系统改�?v1.md`，改造总结�?`文档/架构改造总结-2026-05-27.md`�?

### 10.1 改造目标（8条需求）

| # | 需�?| 完成状�?|
|---|------|----------|
| 1 | 初始APK为框架仅，游戏改为模块商店下�?| �?|
| 2 | 商店下载完整游戏APK（非解锁密钥�?| �?|
| 3 | 斗地�?五子棋内置但支持更新 | �?|
| 4 | 联机功能做成独立公共模块（AAR库） | �?|
| 5 | 对标大型团队（代码规�?CI/CD/测试覆盖�?40%�?| �?基础框架已建�?|
| 6 | JDK/SDK支持本地调用或重新下�?| �?|
| 7 | VPS连接方法记录在项目中 | �?|
| 8 | 完成内容更新MD文件 | �?|

### 10.2 RePlugin 桥接层（关键改造）

**背景**：原方案依赖 `com.qihoo360.replugin:replugin-host-lib:2.3.4`，但 jcenter 已关闭，Maven Central 无此 JAR�?

**实际方案**：创建桥接类 `com.qihoo360.replugin.RePlugin.java`，静态方法委托给 `ModuleLoaderV2`（DexClassLoader 实现）�?

**关键文件**�?
- `app/src/main/java/com/qihoo360/replugin/RePlugin.java` �?桥接�?
- `app/src/main/java/com/gamecenter/app/App.java` �?�?`attachBaseContext()` �?`onCreate()` 中调�?RePlugin 初始�?

**已知风险**：非官方 RePlugin 实现，Android 15+ 兼容性未验证（TD-03，计�?v1.3 修复）�?

### 10.3 模块商店增强

- 支持完整 APK 模块下载安装（非 dex 解锁�?
- 新增 `ModuleDownloadManager.kt` �?模块下载管理�?
- 新增 `ModuleVersionChecker.kt` �?版本检查器
- 内置游戏（斗地主/五子棋）支持版本号判断更�?

**文件变更**�?
- 修改 `app/src/main/java/com/gamecenter/app/App.java` �?添加 RePlugin 初始�?
- 修改 `app/build.gradle` �?添加 `fileTree(dir: 'libs', include: ['*.jar'])`
- 修改 `app/src/main/res/values/styles.xml` �?添加 Material3 兼容样式
- 修改 `app/src/main/res/layout/item_module.xml` �?修复 Material3 样式引用

### 10.4 联机公共模块（AAR 库）

**架构决策**：`:core:online` = Android Library (AAR)，可被任�?App 通过 `implementation` 依赖�?

**新增文件**（`core/online/`）：
- `OnlineManager.java` �?联机管理器（公共接口�?
- `RelayClient.java` �?WebSocket Relay 客户�?
- `RoomManager.java` �?房间管理�?
- `MessageProtocol.java` �?消息协议

### 10.5 开发环境管�?

- 新增 `config/environment.properties` �?开发环境配置文�?
- JAVA_HOME 配置：使�?Windows 路径格式 `C:/Program Files/Android/Android Studio/jbr`（Git Bash Unix 路径 Gradle 无法识别�?

### 10.6 测试框架

**测试结果**�?
- �?单元测试�?2/22 通过 (100%)
- ⚠️ 仪表化测试：0/53 执行（网络代理问题，TD-02�?
- 📊 测试覆盖率：29.3%（目�?> 40%，待 TD-01/02 修复后达标）

**新增测试文件**（`app/src/androidTest/java/com/gamecenter/app/`）：
- `T01RePluginInitTest.kt` �?8 个测试用例（RePlugin 初始化）
- `T02ModuleStoreTest.kt` �?11 个测试用例（模块商店�?
- `T03OnlineModuleTest.kt` �?18 个测试用例（联机模块�?
- `T04BuiltInGameUpdateTest.kt` �?16 个测试用例（内置游戏更新�?
- `EmulatorTestBase.kt` �?测试基类（UI 交互辅助方法�?

### 10.7 已知技术债务

| 编号 | 描述 | 严重�?| 计划版本 |
|------|------|---------|--------------|
| TD-01 | `ModuleLifecycleManagerTest` �?`ModuleDependencyGraphTest` �?`@Ignore` 跳过 | �?| v1.2（Robolectric 重构�?|
| TD-02 | `connectedAndroidTest` 因网络代理问题无法执�?| �?| v1.2（配置离线依赖） |
| TD-03 | RePlugin 桥接层非官方 JAR，Android 15+ 兼容性未验证 | �?| v1.3（兼容性测试） |
| TD-04 | `CircularDependencyException` 内部类未实现 | �?| v1.2（补充实现） |
| TD-05 | 模块依赖自动下载功能未实�?| �?| v1.4（P2 需求） |

### 10.8 下一步行动计�?

**v1.2（立即执行）**�?
1. 修复网络配置，使 `connectedAndroidTest` 可以执行（TD-02�?
2. 使用 Robolectric 重构 `ModuleLifecycleManagerTest` �?`ModuleDependencyGraphTest`（TD-01�?
3. 补充 `CircularDependencyException` 内部类实现（TD-04�?

**v1.3（后续优化）**�?
1. Android 15+ RePlugin 兼容性测试（TD-03�?
2. 性能基准测试（启动时间、内存占用、帧率）
3. 模块依赖自动下载功能实现（TD-05�?

### 10.9 关键文件速查

| 操作 | 文件路径 | 说明 |
|------|----------|------|
| 新增 | `app/src/main/java/com/qihoo360/replugin/RePlugin.java` | RePlugin 桥接�?|
| 新增 | `core/online/src/main/java/com/gamecenter/core/online/OnlineManager.java` | 联机管理�?|
| 新增 | `core/online/src/main/java/com/gamecenter/core/online/RelayClient.java` | WebSocket Relay 客户�?|
| 新增 | `core/online/src/main/java/com/gamecenter/core/online/RoomManager.java` | 房间管理�?|
| 新增 | `core/online/src/main/java/com/gamecenter/core/online/MessageProtocol.java` | 消息协议 |
| 新增 | `app/src/main/java/com/gamecenter/app/modules/ModuleDownloadManager.kt` | 模块下载管理�?|
| 新增 | `app/src/main/java/com/gamecenter/app/modules/ModuleVersionChecker.kt` | 版本检查器 |
| 新增 | `config/environment.properties` | 开发环境配�?|
| 新增 | `app/src/androidTest/java/com/gamecenter/app/T01RePluginInitTest.kt` | T01 测试用例 |
| 新增 | `app/src/androidTest/java/com/gamecenter/app/T02ModuleStoreTest.kt` | T02 测试用例 |
| 新增 | `app/src/androidTest/java/com/gamecenter/app/T03OnlineModuleTest.kt` | T03 测试用例 |
| 新增 | `app/src/androidTest/java/com/gamecenter/app/T04BuiltInGameUpdateTest.kt` | T04 测试用例 |
| 新增 | `app/src/androidTest/java/com/gamecenter/app/EmulatorTestBase.kt` | 测试基类 |
| 修改 | `app/src/main/java/com/gamecenter/app/App.java` | RePlugin 初始化调�?|
| 修改 | `app/build.gradle` | 添加 libs/*.jar 依赖 |
| 修改 | `app/src/main/res/values/styles.xml` | Material3 兼容样式 |
| 修改 | `app/src/main/res/layout/item_module.xml` | 修复 Material3 样式引用 |

> 完整文件清单�?`文档/架构设计-系统改�?v1.md` 第十一章�?

### 10.10 构建和运�?

**构建命令**（Windows PowerShell）：
```powershell
$env:JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
.\gradlew.bat assembleDebug
```

**测试命令**�?
```powershell
# 单元测试�?00% 通过�?
.\gradlew.bat testDebugUnitTest

# 仪表化测试（�?TD-02 修复后可用）
.\gradlew.bat connectedDebugAndroidTest
```

**安装到模拟器**�?
```powershell
adb install app\build\outputs\apk\debug\app-debug.apk
```

模拟�?ADB 端口（蓝蝶模拟器国际版）�?554�?556

---

## 11. 2026-05-27 v1.3 性能基准测试

### 11.1 改造目�?

�?GameCenterApp 添加性能监控能力，确保应用在各种设备上都能流畅运行�?

**性能目标**�?
- 冷启动时�?< 2s
- 内存占用 < 200MB
- 帧率 > 55 FPS

### 11.2 性能监控模块 (PerfMonitor)

**新增文件**�?
- `app/src/main/java/com/gamecenter/app/monitor/PerfMonitor.java`

**功能**�?
1. **冷启动时间测�?*
   - 记录启动开始时间（Application.attachBaseContext�?
   - 记录首帧渲染完成时间（onWindowFocusChanged �?ViewTreeObserver�?
   - 计算冷启动时�?= 首帧时间 - 启动时间
   - 目标�? 2s

2. **内存占用监控**
   - 使用 HandlerThread 后台线程，每 5 秒采样一�?
   - 获取 Java Heap（Runtime.totalMemory - Runtime.freeMemory�?
   - 获取 Native Heap（Debug.getNativeHeapAllocatedSize()�?
   - 记录峰值和平均�?
   - 目标�? 200MB

3. **帧率监控**
   - 使用 Choreographer �?FrameCallback 监听每一�?
   - 通过帧时间间隔计�?FPS：FPS = 1_000_000_000 / 平均帧间�?
   - 保存最�?300 帧的时间戳用于计�?
   - 目标�? 55 FPS

4. **性能报告生成**
   - �?stopMonitoring() 中自动生�?JSON 格式报告
   - 报告包含：冷启动时间、内存峰�?平均值、FPS 平均�?最小值、总体评估
   - 保存到文件：`/data/data/com.gamecenter.app/files/perf-report-latest.json`
   - 自动�?Logcat 输出报告摘要

**集成方式**�?
```java
// �?Application.onCreate() �?
PerfMonitor.getInstance().startMonitoring(this);

// 在首帧渲染完成后（如 Activity �?onWindowFocusChanged�?
@Override
public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) {
        PerfMonitor.getInstance().recordFirstFrame(getWindow().getDecorView());
    }
}
```

### 11.3 性能基准测试 (PerfMonitorTest)

**新增文件**�?
- `app/src/test/java/com/gamecenter/app/monitor/PerfMonitorTest.java`

**测试覆盖**�?3 个测试用�?+ 3 个基准测试）�?
1. **功能测试**：单例模式、启�?停止监控、冷启动测量、内存采样、FPS 计算、JSON 报告格式、性能目标判断、边界情况处理、弱引用防泄�?
2. **基准测试**：冷启动时间基准�? 2s）、内存占用基准（< 200MB）、帧率基准（> 55 FPS�?

**运行方式**�?
```bash
# 运行所有测�?
./gradlew test

# 运行 PerfMonitorTest
./gradlew testDebugUnitTest --tests "com.gamecenter.app.monitor.PerfMonitorTest"

# 生成测试报告
./gradlew testDebugUnitTest
```

**测试报告位置**�?
- HTML 报告: `app/build/reports/tests/testDebugUnitTest/index.html`
- XML 报告: `app/build/test-results/testDebugUnitTest/*.xml`

### 11.4 已知技术债务

| ID | 描述 | 影响 | 计划修复版本 |
|----|------|------|--------------|
| TD-01 | Robolectric 重构（已完成�?| 单元测试无法�?JVM 运行 | v1.2 �?|
| TD-02 | Gradle 代理配置（已修复�?| connectedAndroidTest 失败 | v1.2 �?|
| TD-03 | RePlugin 桥接�?Android 15+ 兼容性未验证 | 高版�?Android 可能无法加载插件 | v1.3 ⚠️ |
| TD-04 | CircularDependencyException 补充（已完成�?| 模块依赖循环检测缺少异常类 | v1.2 �?|
| TD-05 | 模块依赖自动下载功能 | 模块无法自动下载依赖的其他模�?| v1.4 |

### 11.5 下一步行动计�?

**v1.3 剩余工作**�?
1. �?性能监控模块已实�?
2. �?性能基准测试已编�?
3. ⚠️ TD-03：RePlugin 桥接�?Android 15+ 兼容性测试（未处理）
4. ⚠️ 测试覆盖率提升：当前 29.3%，目�?> 40%

**v1.4 计划**�?
1. 模块依赖自动下载功能（TD-05�?
2. 测试覆盖率提升到 > 40%
3. connectedAndroidTest 配置（需要内�?Maven 仓库或离线模式）

### 11.6 关键文件速查

| 文件路径 | 说明 |
|---------|------|
| `app/src/main/java/com/gamecenter/app/monitor/PerfMonitor.java` | 性能监控模块（新增） |
| `app/src/test/java/com/gamecenter/app/monitor/PerfMonitorTest.java` | 性能基准测试（新增） |
| `文档/PRD-系统改�?v1.md` | PRD 文档（v1.1�?|
| `文档/架构设计-系统改�?v1.md` | 架构设计文档（v1.2�?|
| `AI_CONTEXT.md` | AI 上下文文档（已更�?v1.3�?|
| `项目AI接手说明.md` | 本项目文件（已更�?v1.3�?|

### 11.7 构建和运�?

**构建命令**（Windows PowerShell）：
```powershell
$env:JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
.\gradlew.bat assembleDebug
```

**测试命令**�?
```powershell
# 单元测试（性能基准测试�?
.\gradlew.bat testDebugUnitTest --tests "com.gamecenter.app.monitor.PerfMonitorTest"

# 仪表化测试（需要模拟器或真机）
.\gradlew.bat connectedDebugAndroidTest
```

**查看性能报告**�?
```powershell
# 从设备拉取性能报告
adb shell "run-as com.gamecenter.app cat /data/data/com.gamecenter.app/files/perf-report-latest.json" > perf-report-latest.json
```

