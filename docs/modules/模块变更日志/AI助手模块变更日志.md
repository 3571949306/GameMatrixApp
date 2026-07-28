<!-- flutter-store release-evidence: 2026-07-22 -->
> **Flutter-first 发布证据快照：** 下文任何关于 vc595 / 已签名 Catalog V8 的陈述均记录 2026-07-22 的发布证据。它不是当前工作树或生产状态的声明；实时事实请查阅项目根目录的 `docs/CURRENT_STATE.md` 与当前实现。

# AI助手模块变更日志

## 模块信息

| 属性 | 值 |
|------|-----|
| 模块ID | ai |
| 模块类型 | nav（导航模块） |
| 入口类 | `AiModuleEntryPoint` |
| 入口方式 | 底部导航"AI"Tab → AiFragment |
| 模块商店分类 | ai |
| 是否内置 | 是（builtIn=true, isBaseFramework=true） |
| 当前版本 | v1.1.0 |
| 代码位置 | `module-store/feature/tools/ai/src/main/java/com/gamecenter/app/ai/ui/AiFragment.java` |

## 功能概述

- 智能AI对话助手，支持多轮对话、历史记录和能力调用
- 支持 7 种 AI 任务类型
- 默认 DeepSeek API，可选阿里云通义、硅基流动、智谱AI、零一万物、OpenAI（全部 OpenAI 兼容接口）
- 支持 Gemma 本地推理（MediaPipe LLM Inference）
- 历史搜索、收藏、导出和常用模板

## AI提供商

| 提供商 | 接口类型 | 说明 |
|--------|----------|------|
| DeepSeek | OpenAI兼容 | 默认提供商 |
| 阿里云通义 | OpenAI兼容 | 可选 |
| 硅基流动 | OpenAI兼容 | 可选 |
| 智谱AI | OpenAI兼容 | 可选 |
| 零一万物 | OpenAI兼容 | 可选 |
| OpenAI | OpenAI原生 | 可选 |
| Gemma本地 | MediaPipe LLM | 下载后本地优先路由 |

---

## v1.1.0 — 2026-06-25

### 模块迁移
- AI 模块从 `:app` 主包迁移至 `module-store/feature/tools/ai/` 作为独立 APK 动态模块
- 代码位置由 `app/src/main/java/com/gamecenter/app/ai/` 迁移至 `module-store/feature/tools/ai/src/main/java/com/gamecenter/app/ai/`
- 入口类 `AiModuleEntryPoint` 迁移至 `module-store/feature/tools/ai/src/main/java/com/gamecenter/app/modules/`
- 沿用 `compileOnly` 依赖宿主接口，运行时由主 APK ClassLoader 提供
- 数据库（AiMessageDao / AiMessageEntity）随之迁移至模块工程
- 模块商店分发与预装接入

---

## v1.0.0 — 2026-05-24

### 稳定性修复
- AiFragment.onDestroyView 更彻底的视图引用清理（etInput、etSearch、btnSend 等全部置null）

### 初始功能
- 多轮对话，支持上下文连续对话
- 7 种 AI 任务类型路由
- 多 AI 提供商支持（DeepSeek/通义/硅基流动/智谱/零一万物/OpenAI）
- Gemma 本地推理接入（MediaPipe LLM Inference）
- 下载前 Gemma Notice 确认
- 启用后本地优先路由
- 历史搜索、收藏、导出
- 常用模板管理
- 底部导航栏动态显示：安装AI助手模块后自动出现"AI"Tab


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)