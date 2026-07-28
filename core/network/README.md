# :core:network 模块

网络层核心：所有 HTTP / WebSocket 通信的中转站。

## 职责

- 集中管理 OkHttp 客户端（单例 + Hilt 注入）。
- 提供统一拦截器链（header / logging / retry / dedup）。
- 暴露给所有功能模块使用。

## 关键类

| 类 | 作用 |
|---|---|
| `OkHttpClientProvider` | 单例 OkHttp 客户端（HTTP + WebSocket） |
| `HeaderInterceptor` | 统一 `User-Agent` / `X-Client` 头 |
| `NetworkLoggingInterceptor` | 调试日志 |
| `RequestDeduplicationInterceptor` | 短时间重复请求去重 |
| `RetryInterceptor`（内嵌） | 网络抖动重试，线性退避 |
| `RelayHttpClient` | 联机 Relay API 专用客户端 |
| `RemoteP2PUtil` | P2P 远程通信工具 |
| `NetworkErrorHandler` | 错误码 → 用户消息转换 |

## 用法

### 注入使用

```kotlin
class MyService @Inject constructor(
    private val httpClientProvider: OkHttpClientProvider
) {
    fun doRequest() {
        val client = httpClientProvider.getHttpClient()
        val request = Request.Builder().url("https://...").build()
        client.newCall(request).enqueue(...)
    }
}
```

### 不用注入（动态模块场景）

动态加载的模块以 `compileOnly` 依赖，无法用 Hilt 注入。
**Phase 2.3+** 应改用 `getInstance(context)`（已 `@Deprecated`，等待迁移）。

```kotlin
// 临时
val provider = OkHttpClientProvider.getInstance(context)
val client = provider.httpClient
```

## 配置项（`BuildConfig`）

- `RELAY_URL`：联机 Relay 服务器 URL。
- `WS_URL`：WebSocket 服务器 URL。

这两个值从 `local.properties` 读取：

```properties
relay.url=https://your-server.example.com/api/ddz-relay
ws.url=wss://your-server.example.com/ws
```

## 不要做

- ❌ 不要在模块内自行 `new OkHttpClient.Builder()`（`SecureOkHttpFactory` 除外）。
- ❌ 不要在新代码中使用 `getInstance(context)`（已 `@Deprecated`）。
- ❌ 不要忽略 `HeaderInterceptor` 添加的 header 优先级（调用方设置的优先）。

## 参考

- 详细架构：[`docs/NETWORK_LAYER.md`](../../docs/NETWORK_LAYER.md)
- 10 个散落的 `OkHttpClient.Builder` 待迁移点见 `NETWORK_LAYER.md`。

---

[返回文档索引](../../docs/DOCUMENTATION_INDEX.md)
