<!-- flutter-store-doc-sync: 2026-07-22; historical -->
> Historical snapshot: preserved for context and not current release truth. Flutter-first module store production completion is 100%; current evidence: /docs/flutter-store/MIGRATION_STATUS.md.

# AI Local Gemma Plan

> Scope: downloadable on-device Gemma for Android. Model packages are hosted on the HK update VPS, while prompts run on the phone after the user downloads and enables the model.

## Current State

- HK VPS serves `https://your-server.example.com/ai-models/models.json`.
- The enabled model is `gemma3-1b-it-q4`.
- Model file: `Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task`.
- File size: `554661246` bytes.
- SHA-256: `ddfaf1210d8b4d1b812b5fadb6652999e852c8be6dd9abe353b9213a25262c10`.
- App downloads the model into app-private storage: `Android/data/<package>/files/Documents/ai_models`.
- App integrates MediaPipe LLM Inference through `MediaPipeLocalLlmEngine`.
- `AiTaskRouter` now routes supported local-first tasks to Gemma when the model is downloaded and enabled.
- The default AI task is now chat mode, so local Gemma can be used as a direct on-device assistant.

## Runtime Direction

Use MediaPipe LLM Inference as the first runtime because the hosted package is a `.task` file. Google now recommends LiteRT-LM for long-term work, so keep the runtime behind a small wrapper and avoid spreading MediaPipe APIs across UI code.

Runtime wrapper:

```text
com.GameMatrix.app.ai.local.MediaPipeLocalLlmEngine
```

Routing:

```text
AiFragment
  -> AiModelDownloadManager
  -> AiPreferences localModel=gemma3-1b-it-q4
  -> AiTaskRouter
  -> MediaPipeLocalLlmEngine
  -> LocalAiProcessor/cloud fallback only when Gemma is not selected or not ready
```

## User Agreement And License Flow

Before the first model download, the app must show a Gemma notice and store the accepted notice version.

Required points:

- User must confirm they have read and agree to Google Gemma Terms.
- App must link to `https://ai.google.dev/gemma/terms`.
- App must identify the upstream model source.
- App must disclose that model outputs can be inaccurate, incomplete, biased, or unsuitable for high-risk decisions.
- App must disclose that local inference runs on device and that model download contacts the update server.
- App must disclose the model storage path and that app uninstall/data clearing can remove the model.
- App must forbid unlawful, harmful, infringing, fraudulent, or policy-violating use.

Implemented code:

- `AiLegalNotices.GEMMA_NOTICE_VERSION`
- `AiLegalNotices.buildGemmaDownloadNotice(...)`
- `AiPreferences.acceptGemmaNotice(...)`
- `AiPreferences.hasAcceptedGemmaNotice(...)`

## Device Guardrails

- Minimum SDK remains 24.
- Manifest metadata requires at least 3072 MB RAM.
- Router checks device total RAM before loading Gemma.
- Prompt runs on the existing AI executor, not on the main thread.
- Model is not bundled into the APK.
- SHA-256 is verified after download before finalizing the file.
- If Gemma is missing, disabled, or not selected, existing local-rule/cloud paths remain available.
- If Gemma load fails, the user receives a local inference error instead of a crash.

## Execution Plan

1. Done: host model manifest and `.task` package on HK VPS.
2. Done: add app-private model download and SHA-256 verification.
3. Done: add Gemma notice and accepted-version tracking.
4. Done: add MediaPipe LLM runtime wrapper.
5. Done: route summary, translate, rewrite, Q&A, keywords, classify, and chat tasks to Gemma when enabled.
6. Done: add a visible chat task mode and make it the default AI mode.
7. Next: add delete-model and re-download UI.
8. Next: add streaming output if the UX needs token-by-token rendering.
9. Next: test on a physical 3 GB, 4 GB, and 6 GB Android device.
10. Next: evaluate LiteRT-LM migration once the first MediaPipe build is stable.

## Acceptance Checks

- AI page opens without a model installed.
- User can fetch `models.json`.
- Download is blocked until the Gemma notice is accepted.
- Downloaded file size and SHA-256 match the VPS manifest.
- Download completion sets `localModel=gemma3-1b-it-q4` and keeps local-first enabled.
- Airplane-mode prompt works after download and enablement.
- Low-memory devices show a clear message instead of crashing.
- Release APK remains signed and R8/minify remains enabled.
---

## 2026-05-14 文档同步：文字适配与应用语言

- 新增全局按钮文字适配样式，统一提升 MaterialButton 与平�?Button 的最小高度、内边距和两行显示能力，减少“进入游戏”“发送”等按钮文字被裁切的问题�?- 设置弹窗新增应用语言选项：跟随系统、中文、English；应用启动时会恢复已选择语言�?- AI 任务下拉改为资源字符串，切换 English 后可显示 Chat、Summary、Translate 等英文选项�?- 发布前检查需覆盖中文/英文两种语言、深�?浅色主题、游戏大厅卡片按钮、AI 发送按钮、工具箱小按钮和斗地主操作按钮�?## 2026-05-15 文档同步：Dependabot �?CI 修复

- Dependabot 安全告警已处理：升级 Android Gradle Plugin �?8.13.2、Gradle Wrapper �?8.13、Kotlin �?2.2.21、Hilt �?2.57.2�?- 构建 classpath 已强制解析到安全版本：Netty 4.1.133.Final、BouncyCastle 1.84、commons-compress 1.26.0、jose4j 0.9.6、jdom2 2.0.6.1�?- GitHub Actions 已改为验证型 CI：使�?JDK 21，执�?debug 构建与单元测试，不在云端构建 release 包，避免暴露或依�?release 签名文件�?- CI 命令统一添加 `-PautoBumpVersion=false`，避免自动修�?`version.properties`�?- `.gitignore` �?`data/` 规则已收窄为 `/data/`，防止误忽略 `app/src/main/java/com/GameMatrix/app/ai/data/` 源码�?- 最�?GitHub Actions `CI/CD Pipeline` 已通过；正式签名、R8 混淆�?服务器部�?GitHub Release 发布仍以本机发布流程为准�?
## 2026-05-24 文档同步
- 底部导航切换闪退修复：创�?KeepStateNavigator 自定义导航器，使�?add/show/hide 策略替代 Navigation 默认 replace 策略
- 模块下载修复：ModuleDownloader 全面重写，添加全局异常捕获、降低超时、增加日�?- 内存泄漏全面修复：移�?WeakReference callback、Fragment 回调安全检查、视图引用彻底清�?- 压力测试通过�?0轮快速Tab切换无崩�?

- 2026-05-24 游戏美化+中国象棋提示改进+华容�?中国象棋模块商店上架：四个游戏视觉美化（斗地主径向渐变桌�?五子棋木�?D棋子/华容道深色渐变金色边�?中国象棋木纹角标波浪线）；中国象棋提示改为棋盘可视化（蓝色脉冲光�?箭头指引�?中文棋谱描述；华容道和中国象棋创建独立APK模块（feature/games/klotski、feature/games/chinesechess）v2.0.0上架模块商店