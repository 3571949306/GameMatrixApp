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


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)
