# GameMatrixApp 当前状态

> **状态**：当前参考
> **最后核验**：2026-07-27（真机端到端验证）
> **事实来源优先级**：[`version.properties`](../version.properties)、当前 Gradle/Catalog/源代码、当前工作树；文档治理规则见 [`DOCUMENTATION_GOVERNANCE.md`](DOCUMENTATION_GOVERNANCE.md)。

本文只记录已核验的现状。路线图、审计和发布快照中的历史判断请保留其原始日期，不要将其视为实时状态。

## 版本与发布语义

| 项目 | 当前值 | 说明 |
|---|---:|---|
| 工作版本 | `1.4.1 / vc645` | `version.properties` 中的当前开发版本；不等于已发布版本 |
| 最近稳定版 | `1.4.1 / vc600` | `lastStableVersion*` 字段；面向已安装用户的稳定基线 |
| 最低 SDK | Android 7.0 / API 24 | 当前 Android 构建约束 |
| 目标 SDK | Android 15 / API 35 | 当前 Android 构建约束 |
| 编译 SDK | API 36 | 当前 Android 构建约束 |

发布文档不得把工作版本称为“已发布”，除非已验证签名产物、公开下载源和发布记录。

## 产品定位

GameMatrixApp 是一个**本地优先、用户可控制的 Android 通用模块平台**。游戏是发现与长期使用的核心入口；工具、浏览器、AI、错题本、网络能力和后续内容模块通过统一的安装、权限、隐私、更新和卸载体验共存。

平台不以“功能越多越好”为目标。新模块应明确回答：它帮助用户完成什么任务、是否可离线、会访问哪些数据与权限、如何安装/更新/卸载、以及它如何与既有能力连续协作。

完整的发展与体验策略见 [`PRODUCT_DIRECTION_AND_UX.md`](PRODUCT_DIRECTION_AND_UX.md)。

## 初始安装包预算

初始安装包的长期产品目标为**约 200 MB**。当前 release APK 约 **96.6 MB**（2026-07-27 测量，含 vpn-release.apk；调整后预计 ~97.3 MB），有约 100 MB 余量；决策方向是“有余量可预装更多高价值能力”，而非继续压缩。当前 assets/modules/ 内打包的 APK：`browser`（0.8 MB）+ `wrongbook`（5.86 MB）+ `tools`（0.66 MB）+ `chinesechess`（0.63 MB），共约 7.95 MB；3 个内置代码模块（`games_hall` / `browser` / `breakout`）；28 个 APK 可下载（`vpn`/`ai`/`tts_voice` 及其余游戏按需下载）。**注**：`wrongbook`/`tools`/`chinesechess` 在 `modules.json` 中 `builtIn=false`，APK 虽打包到 assets 但运行时不自动安装——`chinesechess` 通过游戏大厅加载路径可用，`wrongbook`/`tools` 需从模块商店下载（真机验证已确认下载链路正常）。

首包应优先覆盖高频、离线、首次打开即可完成价值的核心体验；低频、体积特别大、权限敏感或更新节奏快的能力仍适合按需下载。模块化决策必须同时评估用户价值、离线可用性、首启成功率、下载成本、设备存储和维护风险。

### 200 MB 首包分配建议

| 能力 | 建议 | 理由 |
|---|---|---|
| 宿主、游戏大厅、动态导航、隐私/数据控制 | 预装 | 平台启动、发现和控制面的必要能力 |
| 浏览器 | 继续预装 | 高频入口、具备离线缓存价值，现有内置体积约 0.8 MB |
| 错题本 | 继续预装 | 学习意图的首启闭环，现有内置体积约 5.86 MB |
| 基础工具箱 | 已预装（2026-07-27） | 体积小（0.66 MB）、离线价值高，覆盖"创作"意图首次打开即有可用能力 |
| 代表性离线游戏 | 已预装 chinesechess（2026-07-27） | 经典棋类、5级AI、纯离线，覆盖"玩"意图；体积 0.63 MB |
| 云端 AI、TTS | 按需下载 | 依赖网络、模型/费用/隐私披露，更新节奏快 |
| VPN | 按需下载 | 权限与网络敏感；`vpn-release.apk` 已构建并签名（2026-07-27），但不预装 |
| 其余游戏与专业工具 | 按需下载 | 控制维护面和更新风险，保留用户卸载选择 |

**预算约束**：200 MB 是上限方向而不是填满目标。近期先把约 96.6 MB 基线提升到 **120–150 MB 的目标带**，仅加入完成 Release 签名、Catalog 一致性和真机启动验证的高价值模块；保留至少 50 MB 余量给资源增长、ABI 差异和后续高价值能力。最终预装清单应结合“玩 / 学习 / 浏览 / 创作 / 连接”五类首次意图各自至少一个无需下载的可完成入口。

## 已核验的实现能力

| 能力 | 当前状态 | 依据 |
|---|---|---|
| 游戏中心 | 已实现；当前工作树继续完善排行榜、回放、难度建议、收藏/分组和分享卡等体验 | `app/src/main/java/com/gamecenter/app/games/`、`GamesFragment.java` |
| 模块商店 | 已实现；Android 负责目录信任、下载、安装、回滚和 Runtime 生命周期，Flutter Add-to-App 负责商店 UI/交互 | `app/src/main/java/com/gamecenter/app/modules/`、`flutter_module/` |
| 动态导航 | 已实现并默认开启；用户可排序/隐藏底部导航，游戏大厅始终保留 | `MainActivity.kt`、`BottomNavigationManager`、`app/build.gradle` |
| 模块下载与完整性 | 已实现 HTTPS、重试、多源 URL、SHA-256 与 APK 签名检查；**真机已核验**（2026-07-27，5 模块全链路通过） | `ModuleDownloader.kt`、`ModuleLoader.kt` |
| 模块签名者验证 | 已实现并在下载/加载路径调用；使用 `apksig` 校验 v2/v3 签名并对比内置 X.509 证书 | `core/security/.../ModuleSignatureVerifier.kt` |
| 事务安装/回滚 | 已实现并默认开启；**真机已核验**（2026-07-27，`ai`/`tts_voice`/`tools`/`vpn` 从事务安装路径成功） | `ENABLE_TRANSACTIONAL_INSTALL`、模块 Runtime 安装路径 |
| 浏览器 | 已实现多标签、阅读模式、隐私保护、离线缓存、翻译等能力；**真机已核验**（2026-07-27，builtIn 打开正常） | `app/src/main/java/com/gamecenter/app/browser/` |
| 工具、AI、错题本、TTS、VPN | 通过模块/宿主能力提供；**真机已核验**（2026-07-27，5 模块打开均无 `FATAL EXCEPTION`） | `module-store/feature/tools/` |
| 云同步 | 当前工作树有 WebDAV 存档同步；支持上传、下载、时间戳冲突返回和可选自动同步 | `app/src/main/kotlin/com/gamecenter/app/cloudsync/CloudSyncManager.kt` |
| 主题与设计 Token | 基础已落地：主色、明暗色 token、间距、圆角、阴影；旧界面仍需分批统一 | `app/src/main/res/values/{colors,color_tokens,dimens_tokens,shape_tokens,elevation_tokens}.xml` |

“已实现”表示当前代码存在相应路径；它不自动等于对外发布或已完成真机全链路验收。标记“真机已核验”的项目已在真机上完成端到端验证。

## 构建、Catalog 与功能开关

- `settings.gradle` 当前包含 `:app`、`macrobenchmark`、`core` 子模块、模块商店功能模块与 Flutter Add-to-App 集成。
- 两个本地目录均含 34 个模块记录：`catalog.json` 为 `catalogVersion=9/version=28`，`modules.json` 为 `catalogVersion=10/version=31`（2026-07-27 真机验证使用版本）。2026-07-27 字段级核验确认 `tools` 和 `vpn` 的顶层 `fileSize` / `sha256` 与嵌套 `package` 元数据已收敛一致；`ai`/`wrongbook`/`tts_voice` 的 `sha256`/`fileSize` 已与远程服务器实际 APK 匹配（真机下载校验通过）。运行时权威目录、兼容策略和发布产物必须以当前代码与发布验证为准，不能手工假定两个文件等价。
- `app/build.gradle` 的 `defaultConfig` 当前声明 90 个布尔 BuildConfig 开关；多数默认开启。部分开关的最终值来自构建参数或 build type，例如 Flutter 商店和 Catalog 签名。文档中的“默认值”必须区分源码回退值、构建参数值和生产发布值。

## 当前发布门槛

以下项目必须用具体产物和环境核验，不能仅依据代码或历史报告宣布完成：

1. **发布签名同源性**：已用 `keytool` 核验 `release_signer.cer` 与 `gamecenter.jks`（alias `gamecenter`）SHA256 一致（`D0:58:A1:8F:9E:89:A2:9B:53:39:ED:A2:7E:CE:3F:F9:F7:8E:0D:BE:FE:60:5D:55:1E:77:45:F7:24:D2:ED:DC`，2026-07-27）；`apksigner` 抽样确认宿主、chinesechess、gomoku、wrongbook、**vpn**、**tools** Release APK 使用同一证书（2026-07-27 新增 vpn/tools 验证）。`vpn-release.apk` 已构建并用 `gamecenter.jks` 签名（fileSize=640752, sha256=`a76aa05a...d31af01c`）。**已核验**（2026-07-27）。
2. **Catalog 一致性与信任**：`modules.json` 与 `catalog.json` 中 `tools` 和 `vpn` 条目的顶层 `fileSize`/`sha256` 与嵌套 `package` 元数据已于 2026-07-27 收敛一致。`tools`：fileSize=692923, sha256=`67d1eda6...f9865566`；`vpn`：fileSize=640752, sha256=`a76aa05a...d31af01c`。远程目录的版本、签名、缓存和兼容回退行为应与本地 Catalog/客户端策略一致。**已核验**（2026-07-27）。
3. **真实用户路径**：下载 → SHA-256 → APK signer → 安装 → 打开 → 更新 → 失败回滚。**已核验**（2026-07-27，真机 f0363bc0，宿主 v1.4.1/vc646）：5 个可下载模块（`ai`/`wrongbook`/`tts_voice`/`tools`/`vpn`）全部完成 下载 → SHA-256 校验 → APK 签名校验 → 事务安装 → 打开 链路，logcat 无 `FATAL EXCEPTION`/`NoSuchMethodError`/`NoClassDefFoundError`。具体证据：
   - `wrongbook`（学习与整理，5.9 MB）：已预装，打开显示 错题/看板/复习/设置 tab，空状态正常。
   - `ai`（文本与创作，1.1 MB）：从远程下载安装，打开显示本地规则引擎、5 类快捷任务、输入区。
   - `tts_voice`（文本与创作，625.7 KB）：从远程下载安装，打开显示语音合成实验室（模型选择/文本输入/合成/试听/保存）。
   - `tools`（设备与网络，676.7 KB）：从远程下载安装，打开显示网络体检/报告导出/DNS 查询。
   - `vpn`（设备与网络，625.7 KB）：从远程下载安装，打开显示配置界面与 FAB。
   - 预装模块首次打开成功率（#6.3，5 类意图）：玩（`chinesechess`/`breakout`）✓、学（`wrongbook`）✓、浏览（`browser` builtIn）✓、创作（`tools`/`ai`/`tts_voice`）✓、连接（`vpn`）✓。首次打开成功率 100%。
   - 已修复的阻塞问题：R8 full 模式混淆导致 `wrongbook` `NoSuchMethodError`（`createViewModelLazy`/`getViewModelScope`）→ ProGuard 规则保留 `kotlin.**`/`kotlinx.coroutines.**`；`ai` `NoClassDefFoundError: MasterKeys` → `security-crypto` 改为 `implementation`；`tts_voice` `NullPointerException` in `dp()` → `ctx` 在 `onCreateView` 初始化。
4. **新模块接入**：必须声明交付方式、运行时、权限、隐私数据流、导航贡献、更新/卸载语义及回滚策略。

## 用户关键路径

1. **首次使用**：用户选择“玩、学习、浏览、创作、连接”等意图；平台只推荐对应的起始模块和导航，而非一次展示全部功能。
2. **发现与安装**：模块详情需在安装前说明体积、权限、离线能力、数据去向、兼容性、更新和卸载影响。
3. **日常使用**：最近使用、收藏、挑战、回放、统计和难度建议应帮助用户继续自己的任务，而不是制造无意义通知或压力。
4. **隐私控制**：任何云端 AI/OCR/同步调用都应先告知数据去向、用途、费用/网络要求，并允许取消或改用本地路径。
5. **同步与恢复**：用户需要清楚看到同步配置、最近结果、冲突原因、可选处理和本地备份恢复入口。

## 文档状态说明

- `vc595`、Catalog V8、Flutter-first 发布和旧测试矩阵等内容是**已记录的发布证据**，不是当前全局状态的唯一来源。
- 2026-07-19 前后的路线图以 vc567/vc587 为基线，其中“A1/S1/A2 尚未开始”等结论已被后续实现部分或全部超越；保留路线图用于追溯，但实时判断请回到本页与实现。
- 当前工作树存在并行开发中的变更。除非任务明确验证了受影响 APK 和真机路径，不应将工作树功能标记为稳定发布。
