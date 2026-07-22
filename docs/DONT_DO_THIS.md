<!-- flutter-store-doc-sync: 2026-07-22 -->
> **Flutter-first production sync:** The Flutter module-store UI and customizable host navigation are live in stable vc595; Android remains authoritative for catalog trust, download, install, rollback, and runtime lifecycle. Production completion: 100%. See `/docs/flutter-store/MIGRATION_STATUS.md`.

# 写给 AI / 协作者：不要做

下面这些事**不要**做：

## ❌ 不要把 kapt 加回来

`kapt` 已经在 Phase 1.3 全部移除。Hilt / Room 都走 KSP。
- 加 `id 'org.jetbrains.kotlin.kapt'` 会让 R8 / KSP 冲突
- `kapt '...'` 依赖会被忽略或报错
- 解决: 用 `ksp '...'` 替换

## ❌ 不要 commit / push

用户明确说"先不提交到 github，只本地进行"（2026-06-02 17:06 确认）。等用户明确说"提交"再动。

## ❌ 不要撤销 / 重置 MiMo API key

用户说这个 API 是免费的（2026-06-02 15:38），无财务影响。撤销由用户自己决定。

## ❌ 不要修改 keystore.properties / local.properties 内容

这两个文件是 gitignored，但**值**有讲究:
- `keystore.properties` 的 storePassword = `android` / keyPassword = `android` 是用户用的
- `local.properties` 的 mimo.api.key 是用户从 MiMo 控制台拿的
- 改这两个文件值会让 build 出来的 APK 行为不同

## ❌ 不要替换 `release-key.jks`

这个 keystore 是 Phase 1.3 由 AI 生成的，密码固定 `android`/`android`。
- 删了它后所有用这个 keystore 签的 release 包都覆盖不上去
- 重新生成一个 = 旧包跟新包签名不一致 = 用户升级 APK 会失败（必须卸载重装）
- 改之前问用户

## ❌ 不要 `git filter-repo` 清历史

涉及 force-push，跟协作者状态冲突。等用户明确说"清历史"再做。

## ❌ 不要改 `version.properties` 自动 bump

`app:assembleDebug` 会自动跑 `bumpVersion` task 把 versionCode + 1。改了用户要追责。
- 跑 `./gradlew :app:assembleDebug -PautoBumpVersion=false` 关掉自动 bump
- 或直接改 `version.properties` 文件

## ❌ 不要 push 任何 release 包到 GitHub Releases

发布流程是用户的本机手动跑的（`buildAndUploadToVpsAndGitHub`），不要替用户发。

## ❌ 不要切到 Compose 强迁整屏

Phase 2.4 给了样板（`hall/HallScreen.kt`），真整屏迁是 Phase 3+ 的事：
- 改前必须先跟用户对齐
- 不要为追求"全 Compose" 而重写还在跑业务的 View 系统 Activity

## ❌ 不要把“模块商店 Flutter 化”扩大成“全 App Flutter 重写”

- 当前 Flutter 化只覆盖模块商店 UI/交互层。
- 宿主首页、棋类游戏、人机 AI、动态模块业务页面继续使用现有 Android/Compose/View 实现。
- 没有明确产品需求和迁移计划时，不要因为技术统一而迁移这些页面。

## ❌ 不要让 Flutter 绕过 Android 权威链

- Flutter 不得直接下载 APK/ZIP、访问模块私有目录、加载 DEX、启动服务或控制 Unity 生命周期。
- 不得另建安装数据库、目录缓存、签名策略或模拟下载进度。
- 所有动作必须通过 Pigeon 和 `ModuleCoreFacade`，正式 V2 包必须映射到 `ModuleManager` 权威清单。

## ❌ 不要删除旧商店或默认开启 Flutter 商店

- `ModuleStoreActivity` 是故障回退路径，生产门禁完成前必须保留。
- `ENABLE_FLUTTER_MODULE_STORE` 的源码默认 false 是回退策略，不代表生产未启用；stable vc595 已用生产参数启用。Android 11–15、签名 Catalog V8、正式多 Runtime 包和生产灰度均已完成，不得继续写成“占位公钥”“待线上签名”或“Android 11/12/14 待测”。
- 不要把 Android 13 Debug 冒烟通过写成已完成正式发布。

## ❌ 不要把 lint abortOnError 改回 true

Phase 1.5 故意调成 false 配合新 baseline。改回 true 老项目立刻挂。

## ❌ 不要把 detekt ignoreFailures 改回 false

Phase 1.1 故意配 true（baseline 模式）。改回 false 老项目立刻挂。

## ❌ 不要给 `:app` 直接加 `id 'org.jetbrains.kotlin.jvm'`

`:app` 是 Android module，用 `id 'org.jetbrains.kotlin.android'`。JVM plugin 会跟 AGP 冲突。

## ❌ 不要给 detekt 加 `allRules = true`

只跑 `detekt.yml` 配的规则 + 内置默认。`allRules` 会启用几十条未审过的规则，老项目立刻挂。

## ❌ 不要假设我 (AI) 知道你的机器配置

- JDK 路径: `C:\Users\Administrator\.jdks\ms-17.0.19`
- Android SDK: `C:\Users\Administrator\AppData\Local\Android\Sdk`
- 实际 build 前要查环境, 不要写死

## ❌ 不要把已迁移的 Kotlin 文件改回 Java

循环23 已完成宿主 Kotlin 迁移，以下文件**不要改回 Java**：

- `app/src/main/kotlin/com/gamecenter/app/App.kt`
- `app/src/main/kotlin/com/gamecenter/app/MainActivity.kt`
- `app/src/main/kotlin/com/gamecenter/app/GameRegistry.kt`

原因：

- 迁移是单向的，回退会丢掉 Kotlin 的空安全、协程、Hilt 集成等收益
- 回退会破坏现有 Gradle 配置（Kotlin plugin 已配置）和依赖关系
- 如果要改这些文件的行为，继续用 Kotlin 编辑，不要换语言

## ❌ 不要降级 Netty 到 4.1.134.Final 及以下

循环24（commit `f978f06`）已把 Netty 从 `4.1.134.Final` 升级到 `4.1.135.Final`，修复 7 个 CVE：

- CVE-2026-50010 / CVE-2026-45416 / CVE-2026-44249（high）
- CVE-2026-50560 / CVE-2026-50020 / CVE-2026-48043 / CVE-2026-47244（medium）

降级会：

- 重新触发 7 个 GitHub Dependabot 告警
- 让构建链路重新引入已知漏洞
- 详见 `docs/SECURITY.md` 和 `docs/NETWORK_LAYER.md`

注意：Netty 是 Gradle / MediaPipe / Robolectric 的传递依赖，不进入 APK runtime，但仍需保持版本合规。

## ❌ 不要重新打开已 dismissed 的 GitHub Dependabot 告警

循环24 已 dismiss 7 个 Netty 相关 Dependabot 告警，dismiss reason = `fix_started`（已通过升级到 4.1.135.Final 修复）。

- 当前状态：0 open alerts / 7 dismissed
- 不要把这些告警 reopen
- 如果 Dependabot 又报新告警，那是新问题，按新流程处理（先看是否影响 APK runtime，再决定修复或 dismiss）
- 详见 `docs/SECURITY.md`


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)
