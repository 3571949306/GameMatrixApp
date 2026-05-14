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
