<!-- flutter-store-doc-sync: 2026-07-22; historical -->
> Historical snapshot: preserved for context and not current release truth. Flutter-first module store production completion is 100%; current facts: /docs/CURRENT_STATE.md.

# GameMatrixApp 代码质量修复实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 修复项目评审中发现的 12 个代码质量和安全问题

**Architecture:** 按优先级分 3 个批次并行执行，每批次内的任务相互独立

**Tech Stack:** Java, Kotlin, Android, OkHttp, ProGuard

---

## 批次 1：高优先级安全修复（3 个并行任务）

### Task 1: 修复 RecoveryDownloader SSL 漏洞

**Files:**
- Modify: `app/src/main/java/com/gamecenter/app/recovery/RecoveryDownloader.kt:109-118`

**Problem:** 使用 TrustAllX509TrustManager 禁用 SSL 验证，易受 MITM 攻击

**Solution:** 移除 trust-all 实现，使用系统默认 SSL 验证链

- [ ] **Step 1:** 删除 `TrustAllX509TrustManager` 对象（lines 195-199）
- [ ] **Step 2:** 移除 `downloadFromUrl()` 中的 SSL 绕过代码（lines 109-118），改为使用系统默认 SSL
- [ ] **Step 3:** 验证编译通过

### Task 2: 启用证书固定

**Files:**
- Modify: `app/src/main/java/com/gamecenter/app/App.java` — 确保 release 构建调用 `setHosts(host, true)`
- Modify: `app/src/main/res/xml/network_security_config.xml:11-16` — 取消注释并配置 pin-set

**Problem:** 证书固定默认禁用，network_security_config 的 pin-set 被注释

**Solution:** 在 App 初始化中根据 BuildConfig.DEBUG 控制 pinning 开关

### Task 3: 修复 VPN 转发线程静默吞异常

**Files:**
- Modify: `app/src/main/java/com/gamecenter/app/vpn/service/VpnServiceProxy.kt:106,113`

**Problem:** VPN 转发线程空 catch 块，连接失败不可见

**Solution:** 添加日志记录和错误状态通知

---

## 批次 2：中优先级代码修复（5 个并行任务）

### Task 4: 修复游戏 Activity Handler 泄漏

**Files:**
- Modify: `app/src/main/java/com/gamecenter/app/games/chinesechess/ChineseChessActivity.java:843-853`
- Modify: `app/src/main/java/com/gamecenter/app/games/gomoku/GomokuActivity.java:685-693`

**Problem:** onDestroy() 未清理 mainHandler 回调

**Solution:** 添加 `mainHandler.removeCallbacksAndMessages(null)`

### Task 5: 清理空 catch 块

**Files:**
- Modify: 多个游戏 Activity 和 VPN 协议模块中的空 catch 块

**Problem:** 20+ 处 `catch (Exception ignored) {}`

**Solution:** 添加 `Log.w()` 日志记录

### Task 6: 修复 ProGuard 过宽 keep 规则

**Files:**
- Modify: `app/proguard-rules.pro:14-15,80,135,140,145,150,155,170-180`

**Problem:** `-keep public class android.*` 和 `-keep public class androidx.**` 保留整个框架

**Solution:** 移除不必要的 keep 规则，删除重复的 Serializable 规则

### Task 7: 优化 RelayHttpClient 复用 OkHttpClient

**Files:**
- Modify: `core/network/src/main/java/com/gamecenter/app/network/RelayHttpClient.java:219-256`

**Problem:** post() 每次 new OkHttpClient，浪费连接池

**Solution:** 使用 OkHttpClientProvider 共享实例 + newBuilder() 适配超时

### Task 8: 删除 RePluginModuleLoader 死代码

**Files:**
- Delete: `app/src/main/kotlin/com/gamecenter/app/modules/RePluginModuleLoader.kt`

**Problem:** 4 个 TODO 标记的未完成集成，代码路径不可达

**Solution:** 删除文件，清理引用

---

## 批次 3：低优先级改进（4 个并行任务）

### Task 9: 提取硬编码 DNS 和 URL 到配置

**Files:**
- Modify: `app/src/main/java/com/gamecenter/app/vpn/service/VpnServiceProxy.kt` — DNS
- Modify: `module-store/feature/tools/tools/src/main/java/com/gamecenter/app/tools/NetworkDiagHelper.java` — DNS
- Modify: `module-store/feature/tools/tools/src/main/java/com/gamecenter/app/tools/PingToolBinder.java` — DNS

### Task 10: 硬编码 URL 提取到 BuildConfig

**Files:**
- Modify: `module-store/feature/games/games/tts/src/main/java/com/gamecenter/app/tts/TtsActivity.java`
- Modify: `module-store/feature/games/games/tts/src/main/java/com/gamecenter/app/tts/TtsFragment.java`

### Task 11: 提取 magic numbers 为命名常量

**Files:**
- Modify: `app/src/main/java/com/gamecenter/app/vpn/service/VpnServiceProxy.kt` — buffer size
- Modify: `app/src/main/java/com/gamecenter/app/modules/ModuleDownloader.kt` — 1024, 200, 206

### Task 12: VpnServiceProxy TUN 流资源泄漏修复

**Files:**
- Modify: `app/src/main/java/com/gamecenter/app/vpn/service/VpnServiceProxy.kt` — onDestroy 中关闭流

---

## 执行顺序

```
批次 1 (安全): Task 1, 2, 3 ──→ 并行
批次 2 (代码): Task 4, 5, 6, 7, 8 ──→ 并行（批次 1 完成后）
批次 3 (改进): Task 9, 10, 11, 12 ──→ 并行（批次 2 完成后）
```