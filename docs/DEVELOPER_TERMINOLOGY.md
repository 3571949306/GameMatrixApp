# 开发文档术语表

> **状态**：当前术语规范  
> **最后核验**：2026-07-27  
> **适用范围**：GameMatrixApp 项目自有开发、架构、发布、测试、模块、服务端、计划、审计和历史 Markdown 文档。

## 使用原则

- 开发者说明正文一律使用中文；必要的英文技术标识符保留原样。
- 代码块、命令、配置键、类名、函数名、字段名、文件路径、URL、API 方法/路径、协议名、版本号、产品/库名称不翻译。
- 表格列名、标题、注释、Mermaid 的说明文字和用户可见文案说明应使用中文；为了语法正确而必须保留的 Mermaid 关键字可保持英文。
- 面向普通用户的 [`../README.md`](../README.md)、[`../RELEASE_NOTES.md`](../RELEASE_NOTES.md) 和 GitHub 问题模板使用中英双语；英文内容应与中文等义。
- 第三方、上游、供应商、许可证、生成报告和虚拟环境文档不属于本规范，保持原文。

## 统一术语

| 英文概念 | 统一中文 | 使用说明 |
|---|---|---|
| module | 模块 | `moduleId`、模块名和代码标识符保留原样 |
| module store | 模块商店 | 用户界面中可使用“模块商店” |
| runtime | 运行时 | `runtimeType` 保留原样 |
| host | 宿主 | 指主应用或宿主进程 |
| fallback | 回退 | 下载源、界面和配置的备选路径 |
| rollback | 回滚 | 更新或安装失败后的恢复路径 |
| release | 发布版 / 发布 | 依语境选择；`release` build type 保留原样 |
| stable | 稳定版 | 发布通道名可保留 `stable` |
| beta | 测试版 | 发布通道名可保留 `beta` |
| working tree | 工作树 | Git 语境 |
| source of truth | 事实来源 / 权威来源 | 配置和实现的判断依据 |
| feature flag | 功能开关 | `BuildConfig` 字段名保留原样 |
| build | 构建 | Gradle task 名保留原样 |
| artifact | 构建产物 | APK、AAB、Catalog 等 |
| deployment | 部署 | 服务端或发布产物上线 |
| catalog | 目录 | `Catalog V2`、`catalog.json` 保留原样 |
| signature verification | 签名验证 | 对 APK、Catalog 或证书的验证 |
| signer certificate | 签名者证书 | X.509 证书场景 |
| integrity | 完整性 | 常与 SHA-256 校验配合 |
| compatibility | 兼容性 | 版本、ABI、宿主或设备兼容 |
| lifecycle | 生命周期 | Android/模块运行时场景 |
| onboarding | 新手引导 | 首次使用路径 |
| privacy disclosure | 隐私说明 | 模块详情/权限说明 |
| local-first | 本地优先 | 产品原则 |
| offline | 离线 | 无网络可用状态 |
| sync | 同步 | 存档或数据同步 |
| conflict resolution | 冲突处理 | 云同步数据冲突 |
| accessibility | 无障碍 | Android 无障碍体验 |
| observability | 可观测性 | 指标、日志、追踪等 |
| smoke test | 冒烟测试 | 最小关键路径验证 |
| regression | 回归 | 回归测试/回归问题 |
| deprecate | 弃用 | `@Deprecated` 保留原样 |

## 必须保持英文的技术名词

以下内容不是中英文混用问题，必须保留原样：

- `ModuleDownloader`、`ModuleLoader`、`ModuleCoreFacade`、`BuildConfig`、`DexClassLoader`、`WebDAV`、`Ed25519`、`SHA-256`、`APK Signature Scheme v2/v3`；
- `GET`、`POST`、`HTTP`、`HTTPS`、`WebSocket`、`TCP`、`JSON`、`YAML`、`XML`、`Gradle`、`Kotlin`、`Java`、`Flutter`、`Compose`；
- 命令行、代码块、包名、类名、文件名、路径、URL、环境变量、配置键、Feature Flag 和 API 路由；
- 外部产品/库名称，例如 Android、GitHub Releases、Cloudflare、Material 3、Room、Hilt、OkHttp。

## 翻译质量要求

- 中文应表达原意，而非逐词替换；避免将代码概念译成不稳定的自造词。
- 历史文档保留发生日期、版本、当时结论和原始证据；只将解释性语言翻为中文。
- 每次迁移后必须检查 Markdown 链接、代码围栏、Mermaid 图、表格列数和行尾格式。
- 新开发文档提交前，应优先引用本术语表；同一概念在同一文档内不得混用多个译法。
