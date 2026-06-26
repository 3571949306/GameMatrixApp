# 漏洞修复任务清单

- `[x]` 1. 修复 ModuleDownloader.kt 中的路径穿越漏洞
  - 修改 `getModuleFile`，使用安全的命名规则（如 `moduleId.apk`）。
- `[x]` 2. 修复 ModuleDownloader.kt 中的不安全网络传输
  - 在下载循环中拦截并丢弃非 `https://` 的下载链接。
- `[x]` 3. 修复 ModuleVerifier.java 中的签名伪造漏洞
  - 添加受信任的签名哈希白名单。
  - 修改 `verifySignature` 提取 APK 签名并进行 SHA-256 哈希比对。
- `[x]` 4. 运行安全机制相关单元测试并验证构建通过。


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)
