# 大象游戏中心 1.4.1（版本 600）
# Elephant Game Center 1.4.1 (Build 600)

本次更新修复下载到旧版本安装包导致解析错误的问题。

This update fixes parsing errors caused by downloading an outdated installation package.

- 下载完成后校验安装包内部版本号，防止缓存陈旧导致下载到旧版本。
- 安装前检测降级，避免系统报“解析错误”。
- 同步 GitHub Release 与主更新源版本。
- 附带修复中国象棋新手引导对话框的编译问题。

- Verifies the package’s internal version code after download to prevent stale caches from supplying an older version.
- Detects downgrades before installation to avoid Android “parse error” messages.
- Keeps the GitHub Release and primary update source aligned.
- Also fixes a compilation issue in the Chinese Chess tutorial dialog.

建议所有用户更新。

We recommend that all users update.
