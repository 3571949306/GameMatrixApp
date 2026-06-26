# :core:moduleloader Module

动态模块加载引擎（DexClassLoader + 验证 + 热更新）。

## 职责

- 加载外部模块 APK（DexClassLoader 机制）
- 校验模块签名（SHA-256）
- 热更新：版本号不同时自动 unload + reload
- 缓存优化 DEX

## 关键类

| 类 | 作用 |
|---|---|
| `ModuleLoader` (V2) | 主加载器 |
| `ModuleVerifier` | 签名/SHA-256 校验 |
| `ModuleResourceLoader` | 加载模块内资源（防止本地动态资源崩溃） |

## 加载流程

```
modules.json (VPS)
  ↓
ModuleDownloadManager (下载 APK 到 cache)
  ↓
ModuleVerifier.verify() (SHA-256 校验)
  ↓
ModuleLoader.loadModule() (DexClassLoader 装载)
  ↓
ModuleResourceLoader.injectResources() (资源反射重载)
  ↓
IModuleEntry.onLoad() (模块启动)
```

## 安全模型

- ✅ 模块必须跟宿主同签名（`signingConfig` 校验）
- ✅ SHA-256 校验模块 APK（防篡改）
- ⚠️ 证书固定（`SecureOkHttpFactory`）只在网络层用，模块本身走的是 APK signature verification

## 不要做

- ❌ 不要跳过 ModuleVerifier 直接 load (生产环境会出安全事故)
- ❌ 不要在主线程跑 loadModule (DexClassLoader 慢, 会卡 UI)
- ❌ 不要假设模块有 Manifest 声明的 Activity (动态加载的 Activity 走 Fragment 承载)

## 参考

- Phase 1 设计笔记见 `docs/项目改进建议书.md`
- 模块商店 metadata 格式: `modules.json` schema (VPS 同步)


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)
