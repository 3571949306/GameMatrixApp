<!-- flutter-store-doc-sync: 2026-07-22; historical -->
> Historical snapshot: preserved for context and not current release truth. Flutter-first module store production completion is 100%; current facts: /docs/CURRENT_STATE.md.

# AI User Agreement And Local Model Notice

> This document is an engineering checklist for the in-app AI notice. It is not a substitute for legal review, but it defines the user-facing safeguards the app must implement before enabling local Gemma downloads.

## Required User-Facing Terms

The app must present these points before the first Gemma model download:

- The model is provided under Google Gemma Terms.
- The user should read the terms before downloading.
- The app must provide a direct link to `https://ai.google.dev/gemma/terms`.
- The model source is `https://huggingface.co/litert-community/Gemma3-1B-IT`.
- AI output can be wrong, incomplete, biased, outdated, or unsuitable for the user's situation.
- AI output must not be used as the sole basis for medical, legal, financial, security, emergency, or other high-risk decisions.
- Users are responsible for how they use generated content.
- Users must not use the model for illegal, harmful, infringing, fraudulent, abusive, or policy-violating purposes.
- Local inference runs on the device; cloud inference sends prompts to the selected provider.
- Downloading a model contacts the update server and transfers a large model file.
- The model is stored in the app-private external files directory and may be removed when the app is uninstalled or app data is cleared.

## Implemented Controls

| Control | Implementation |
| --- | --- |
| Gemma notice text | `AiLegalNotices.buildGemmaDownloadNotice()` |
| Notice versioning | `AiLegalNotices.GEMMA_NOTICE_VERSION` |
| Acceptance storage | `AiPreferences.acceptGemmaNotice()` |
| Acceptance check | `AiPreferences.hasAcceptedGemmaNotice()` |
| Download gate | `AiFragment.confirmGemmaNoticeThenDownload()` |
| Model integrity | `AiModelDownloadManager` SHA-256 verification |
| App-private storage | `Android/data/<package>/files/Documents/ai_models` |
| Local runtime wrapper | `MediaPipeLocalLlmEngine` |
| Local routing | `AiTaskRouter.tryLocalLlm()` |

## Recommended App Copy

Use concise text in dialogs and keep the full detail in a scrollable screen if the product adds a dedicated Terms page later.

Short dialog title:

```text
Gemma 模型条款与本地 AI 说明
```

Primary action:

```text
同意并下载
```

Cancel action:

```text
取消
```

## Release Checklist

- Confirm the model manifest includes `licenseUrl`.
- Confirm the model manifest includes SHA-256.
- Confirm `enabled=true` only after the model is hosted and license access is approved.
- Confirm APK build does not embed the model file.
- Confirm R8 mapping is generated for release builds.
- Confirm signed release APK passes `apksigner verify`.
- Confirm the in-app notice appears before first download on a fresh install.
- Confirm users can use the app without downloading Gemma.
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
- `.gitignore` 的 `data/` 规则已收窄为 `/data/`，防止误忽略 `app/src/main/java/com/GameMatrix/app/ai/data/` 源码。
- 最新 GitHub Actions `CI/CD Pipeline` 已通过；正式签名、R8 混淆和 服务器部署/GitHub Release 发布仍以本机发布流程为准。

## 2026-05-24 文档同步
- 底部导航切换闪退修复：创建 KeepStateNavigator 自定义导航器，使用 add/show/hide 策略替代 Navigation 默认 replace 策略
- 模块下载修复：ModuleDownloader 全面重写，添加全局异常捕获、降低超时、增加日志
- 内存泄漏全面修复：移除 WeakReference callback、Fragment 回调安全检查、视图引用彻底清理
- 压力测试通过：40轮快速Tab切换无崩溃

- 2026-05-24 游戏美化+中国象棋提示改进+华容道&中国象棋模块商店上架：四个游戏视觉美化（斗地主径向渐变桌面/五子棋木纹3D棋子/华容道深色渐变金色边框/中国象棋木纹角标波浪线）；中国象棋提示改为棋盘可视化（蓝色脉冲光环+箭头指引）+中文棋谱描述；华容道和中国象棋创建独立APK模块（feature/games/klotski、feature/games/chinesechess）v2.0.0上架模块商店


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)