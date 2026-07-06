# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.4.x   | :white_check_mark: |
| < 1.4.0 | :x:                |

## Reporting a Vulnerability

If you discover a security vulnerability in GameMatrixApp, please report it
privately via GitHub Security Advisories:

https://github.com/3571949306/GameMatrixApp/security/advisories/new

**Do not** open a public issue for security vulnerabilities.

We aim to:
- Acknowledge new reports within 3 business days
- Provide a fix or mitigation within 14 days for high-severity issues
- Credit reporters in the release notes (unless you prefer to remain anonymous)

## Secrets Management

This project uses the following convention for secrets and API keys:

- API keys and tokens live in `local.properties` (gitignored)
- They're injected into `BuildConfig` at compile time via `app/build.gradle`
- `keystore.properties` (gitignored) holds release signing config
- The `release-key.jks` keystore is **never** committed
- Production deploys use Play App Signing where applicable

If you accidentally commit a secret, rotate the secret **immediately** and
follow GitHub's guide to remove the secret from git history:
https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository

## 安全更新历史

### 循环24：Netty 安全升级（commit f978f06）

- **时间**：循环24
- **当前版本**：versionCode=567 / versionName=1.4.1
- **关联 commit**：`f978f06` 循环24 修复 Netty 漏洞
- **改动**：Netty `4.1.134.Final` → `4.1.135.Final`
- **依赖性质**：Netty 是 Gradle / MediaPipe / Robolectric 的传递依赖，仅在构建期/测试期使用，**不进入 APK runtime**。升级是为了消除 Dependabot 告警并保持构建链路合规。

#### 修复的 CVE 列表（共 7 个）

| CVE 编号 | 严重等级 | 受影响组件 | 摘要 |
| --- | --- | --- | --- |
| CVE-2026-50010 | high | netty-handler | 信任管理器绕过主机名验证 |
| CVE-2026-45416 | high | netty-handler | SNI 处理器预分配 16MiB 内存 |
| CVE-2026-44249 | high | netty-handler | IPv6 子网过滤器绕过 |
| CVE-2026-50560 | medium | netty-codec-http2 | HTTP/2 Reset 攻击 |
| CVE-2026-50020 | medium | netty-codec-http | HttpObjectDecoder 跳过控制字符 |
| CVE-2026-48043 | medium | netty-codec-http2 | ByteBuf 引用计数泄漏 |
| CVE-2026-47244 | medium | netty-codec-http2 | MAX_CONCURRENT_STREAMS 未强制 |

### GitHub Dependabot 状态

- **当前状态**：0 open alerts / 7 dismissed
- **已 dismiss 的告警**：7 个 Netty 相关告警，dismiss reason = `fix_started`（已在循环24通过升级到 4.1.135.Final 修复）
- **不要重新打开**：已 dismissed 的告警不要重新 open，详见 `docs/DONT_DO_THIS.md`

### Dependabot 配置

仓库根目录新增 `.github/dependabot.yml`，作用：

- 每周一按 `cron: '0 6 * * 1'` 周期扫描
- 覆盖两个 ecosystem：
  - `gradle` — Gradle 依赖（含 Netty 等传递依赖）
  - `github-actions` — GitHub Actions workflow 依赖
- 早期发现依赖漏洞，避免再次出现 Netty 这种集中爆发场景

### 相关 CI

新增 `.github/workflows/android_ci.yml`，与 Dependabot 配合提供构建期回归保护。


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)
