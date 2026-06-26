# GameMatrixApp 安全审查报告与漏洞修复计划

经过对项目的详细安全审查，我们发现在核心框架的**动态模块加载**和**网络下载**部分存在高危及严重级别的安全漏洞。这些漏洞可能导致设备被恶意接管（任意代码执行）、私有数据被覆写以及中间人网络攻击。

建议立即对以下漏洞进行修复。

## 🔴 发现的严重漏洞

### 1. 任意文件覆写 / 路径穿越 (Path Traversal) - 【高危】
- **位置**：`ModuleDownloader.kt -> getModuleFile()`
- **问题**：构建模块存储路径时，直接拼接了不受信任的网络响应数据 `manifest.fileName`：`return File(dir, manifest.fileName)`。
- **利用方式**：恶意服务端或中间人可以将 `fileName` 伪造为 `../../../shared_prefs/auth.xml`，导致 `OkHttp` 下载恶意内容时覆盖应用内部关键数据，甚至劫持 SharedPreferences 等关键配置。

### 2. 签名校验绕过 (Arbitrary Code Execution) - 【严重】
- **位置**：`ModuleVerifier.java -> verifySignature()`
- **问题**：代码中通过 `PackageManager.GET_SIGNATURES` 获取签名后，仅简单判断了 `signatures.length == 0`，没有比对签名的具体哈希值。
- **利用方式**：只要 APK 有签名（哪怕是黑客随意生成的随机证书），`ModuleVerifier` 就会认为"签名验证通过"，配合 `DexClassLoader`，这直接导致**任意恶意代码执行**。

### 3. 未强制加密传输 (Insecure Network Protocol) - 【中危】
- **位置**：`ModuleDownloader.kt -> doDownload()`
- **问题**：在获取 `manifest.getAllDownloadUrls()` 时，未强制校验 URL 的协议。
- **利用方式**：即使配置了 `SecureOkHttpFactory` 的证书固定，如果不法分子通过 DNS 劫持等方式强制返回 `http://` 链接，流量依旧会以明文传输，导致应用下载到被篡改的恶意模块。

---

## 🛠 Proposed Changes

为彻底根除上述安全隐患，我提议进行如下代码改造：

### 核心安全模块

#### [MODIFY] [ModuleDownloader.kt](file:///d:/Developmment/GameMatrixApp/app/src/main/java/com/gamecenter/app/modules/ModuleDownloader.kt)
- **修复路径穿越**：废弃直接使用 `manifest.fileName` 作为文件名的做法。强制通过模块 ID 重新构建安全文件名（例如：`"${manifest.id}_v${manifest.version}.apk"`）。
- **强制 HTTPS**：在遍历并验证 `urls` 集合时，明确过滤掉所有非 HTTPS 前缀的链接。一旦检测到明文 HTTP，强制丢弃。

#### [MODIFY] [ModuleVerifier.java](file:///d:/Developmment/GameMatrixApp/core/moduleloader/src/main/java/com/gamecenter/app/moduleloader/ModuleVerifier.java)
- **严格证书校验**：引入全局可信证书公钥的 SHA-256 指纹白名单机制。
- 对每个模块的签名进行哈希摘要运算，并要求提取出的签名公钥指纹必须与白名单中（例如官方开发者签名）匹配，否则直接判定为非法模块。

## User Review Required

> [!WARNING]
> 修复 `ModuleVerifier.java` 中的签名验证需要配置一组**可信证书指纹（Trusted Certificate Fingerprint）**。
> 目前计划预留一套默认的签名指纹或抛出明显的签名校验配置入口，您后续在线上发布时需要把开发者证书（Keystore）的指纹填入代码中，这会使得任何用其他证书编译的插件模块全部失效。
>
> 针对路径穿越，我们统一使用 `{ModuleID}.apk` 作为本地文件名，不再信任服务端的 fileName。请确认此规则是否满足您的分发需求。

## Verification Plan

### Automated Tests
- 新增单元测试：拦截带有 `../` 的恶意文件名路径，确保安全防御生效。
- 新增单元测试：伪造假证书签名的 APK 送入 `ModuleVerifier`，断言其正确抛出 `VERIFY_ERROR_SIGNATURE_FAILED`。

请您评估上述风险与修复计划，若确认无误，请批准执行。


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)
