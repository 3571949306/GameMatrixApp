# 安全策略

> **当前参考（最后核验：2026-07-27）**  
> 当前安全实现与发布门槛见 [`CURRENT_STATE.md`](CURRENT_STATE.md)。旧 vc595 Flutter 商店发布证据不是当前工作树的自动安全结论；安全相关变更必须以代码、签名产物和真实验证为准。

## 支持版本

| 版本 | 支持状态            |
| ------- | ------------------ |
| 1.4.x   | :white_check_mark: |
| < 1.4.0 | :x:                |

## 报告漏洞

如果你发现 GameMatrixApp 的安全漏洞，请通过 GitHub Security Advisories 私下报告：

https://github.com/3571949306/GameMatrixApp/security/advisories/new

**不要**为安全漏洞开启公开 issue。

我们的目标：
- 在 3 个工作日内确认新报告
- 对高危问题在 14 天内提供修复或缓解方案
- 在发布说明中致谢报告者（除非你希望匿名）

## 密钥管理

本项目对密钥和 API key 使用以下约定：

- API key 和令牌存放在 `local.properties`（已 gitignore）
- 它们在编译期通过 `app/build.gradle` 注入到 `BuildConfig`
- `keystore.properties`（已 gitignore）保存 release 签名配置
- `release-key.jks` keystore **绝不**入库
- 生产部署在适用时使用 Play App Signing

如果不慎提交了密钥，请**立即**轮换密钥，并按 GitHub 指南从 git 历史中清除：
https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository

## Flutter-first 模块商店安全边界

- Flutter 只通过 Pigeon 接收带类型的 catalog/state/result 对象；不能接收私有文件路径，也不能直接操作 APK/DEX、服务、Unity 运行时、签名密钥或模块目录。
- `ModuleCoreFacade` 是唯一业务入口。`ModuleManager` 仍是已安装版本、启用状态、下载、验证、安装和回滚的权威。
- 正式非内置 Catalog V2 记录必须映射到权威 manifest/downloader；否则在队列/进度事件发出之前就以 `package_not_registered` 失败。
- Web/Asset 包使用私有 `staging/current/last_good/quarantine` 目录，并强制路径遍历、条目数、大小和压缩比限制。Web 内容使用虚拟 HTTPS origin，默认禁用 JavaScript 和 bridge。
- `CatalogSignatureVerifier` 从 Release 构建接收 1–3 把已验证的 Ed25519 公钥。stable vc594 使用生产信任 profile；Catalog V8 签名 header 和否定验证路径已上线。私钥材料由 DPAPI 保护，绝不得进入源码、APK、VPS 文件或日志。

## 安全更新历史

### 循环24：Netty 安全升级（commit f978f06）

- **时间**：循环24
- **当时版本**：versionCode=567 / versionName=1.4.1（循环24历史记录；当前版本以 `version.properties` 为准）
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
