# 网络层架构 (Phase 2.3)

> **当前技术参考（最后核验：2026-07-27）**  
> 本文记录网络层结构。vc595 Flutter 商店描述是历史发布证据；当前联网能力、数据流和发布门槛请结合 [`CURRENT_STATE.md`](CURRENT_STATE.md) 与当前实现核验。

## 当前状态

✅ OkHttpClientProvider 集中管理 OkHttp 实例（单例 + Hilt 注入）
✅ 集成 4 个 interceptor:
- `HeaderInterceptor` - 统一 User-Agent / X-Client-Version 等 header
- `NetworkLoggingInterceptor` - 调试日志
- `RetryInterceptor` - 网络抖动重试（线性退避，最多 3 次）
- `RequestDeduplicationInterceptor` - 短时间重复请求去重
✅ WebSocketClient 独立配置（无读取超时，无去重）
✅ 50MB HTTP 缓存

## 已知问题（Phase 2.3+ 待办）

⚠️ **项目里有 10 个地方自己 new OkHttpClient.Builder()**，没走 OkHttpClientProvider：

| 文件 | 用途 |
|---|---|
| `app/.../DouDiZhuRoomHelper.java` | 斗地主 WebSocket |
| `core/modulestore/ModuleDownloadManager.java` | 模块下载 |
| `core/network/RelayHttpClient.java` | 联机 Relay（应该走 provider） |
| `core/online/RelayClient.java` | 联机中继（应该走 provider） |
| `core/security/SecureOkHttpFactory.kt` | 证书固定客户端（特殊，保留独立） |
| `module-store/feature/games/games/GameSocketClient.java` | 游戏 WebSocket |
| `module-store/feature/games/games/WebSocketHostHelper.java` | WebSocket host |
| `module-store/feature/games/games/tts/TtsActivity.java` | TTS API（已 P0 修复） |
| `module-store/feature/games/games/tts/TtsFragment.java` | TTS API（已 P0 修复） |
| `module-store/feature/tools/ai/AiApiClient.java` | AI API |

## 迁移路径（推荐）

### Step 1: 注入代替 new

```java
// 改前
public class MyClass {
    private final OkHttpClient client = new OkHttpClient.Builder().build();
}

// 改后
public class MyClass {
    @Inject OkHttpClientProvider provider;
    private OkHttpClient getClient() { return provider.getHttpClient(); }
}
```

### Step 2: 例外

- `SecureOkHttpFactory` - 证书固定 + 单独 OkHttp 客户端（OK，保留独立）
- WebSocket 场景 - 用 `provider.getWebSocketClient()`
- 客户端配置需要不同的（短超时、特殊 header 等）- 用 `provider.getHttpClient().newBuilder().build()` 复用连接池/拦截器

### Step 3: 验证

```bash
# grep 找剩余 OkHttpClient.Builder()
git grep "OkHttpClient\.Builder" -- '*.java' '*.kt'
```

## 新增的 Interceptor

### `HeaderInterceptor`

统一管理:
- `User-Agent: GameMatrixApp/1.4.1 (Android)`
- `X-Client-Platform: android`
- `X-Client-Version: 1.4.1`

调用方已设的 header 不会被覆盖（优先级：调用方 > interceptor）。

### `NetworkLoggingInterceptor`

输出格式：
```
D/NetLog: --> POST https://api.example.com/v1/chat
D/NetLog: <-- 200 OK (412ms, 1024 bytes)
```

Debug build 开 verbose，Release build 关掉（生产不留日志）。

## CI 监控

Phase 2.3+ 加 detekt 自定义规则，禁止在 `:app` / `:core` / `:module-store` / `feature` 里直接 `new OkHttpClient.Builder()`（除了 `SecureOkHttpFactory` 这种白名单）：

```yaml
# Phase 2.3+ 写到 config/detekt/detekt.yml
naming:
  # 让 OkHttpClient.Builder 引用报 warning, 引导走 Provider
```

## 循环24：Netty 安全修复

### 背景

- **当时版本**：versionCode=567 / versionName=1.4.1（循环24历史记录；当前版本以 `version.properties` 为准）
- **关联 commit**：`f978f06` 循环24 修复 Netty 漏洞
- **改动**：Netty `4.1.134.Final` → `4.1.135.Final`

### 依赖性质

Netty 在本项目里是**构建期/测试期传递依赖**，**不进入 APK runtime**：

- 来自 Gradle 插件、MediaPipe、Robolectric 等链路
- 不在 `dependencies` 里直接声明，也没有打包进 `app-debug.apk` / `app-release.apk`
- 升级目的：消除 GitHub Dependabot 告警、保持构建链路合规

### 修复的 CVE 列表（共 7 个）

| CVE 编号 | 严重等级 | 受影响组件 | 摘要 |
| --- | --- | --- | --- |
| CVE-2026-50010 | high | netty-handler | 信任管理器绕过主机名验证 |
| CVE-2026-45416 | high | netty-handler | SNI 处理器预分配 16MiB 内存 |
| CVE-2026-44249 | high | netty-handler | IPv6 子网过滤器绕过 |
| CVE-2026-50560 | medium | netty-codec-http2 | HTTP/2 Reset 攻击 |
| CVE-2026-50020 | medium | netty-codec-http | HttpObjectDecoder 跳过控制字符 |
| CVE-2026-48043 | medium | netty-codec-http2 | ByteBuf 引用计数泄漏 |
| CVE-2026-47244 | medium | netty-codec-http2 | MAX_CONCURRENT_STREAMS 未强制 |

### Dependabot 状态

- 0 open alerts / 7 dismissed
- 7 个 Netty 告警已 dismiss，reason = `fix_started`
- 配置文件 `.github/dependabot.yml` 已加入仓库（每周一扫描 gradle / github-actions）

### 约束

- **不要降级 Netty** 到 4.1.134.Final 及以下（会重新触发 7 个 Dependabot 告警）
- 详见 `docs/DONT_DO_THIS.md` 与 `docs/SECURITY.md`

## 参考

- https://square.github.io/okhttp/features/interceptors/
- https://developer.android.com/topic/architecture/domain-layer


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)