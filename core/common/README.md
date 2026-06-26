# :core:common Module

通用工具类 + 错误处理 + Result 类型。

## 职责

- 全项目共享的工具类（字符串、数字、集合）
- 错误处理统一（AppError + NetworkResult + AppResult）
- 共享常量

## 关键类

| 类 | 作用 |
|---|---|
| `AppError` | 10 种错误类型 + 工厂方法 |
| `NetworkResult<T>` | 网络操作三态 (Success/Failure/Loading) |
| `AppResult<T>` | 通用三态 (Success/Error/Loading) |
| 各种 `*Utils` | 字符串、日期、加密、JSON 等 |

## 用法

### 错误处理

```kotlin
// 网络层
suspend fun fetchUser(): NetworkResult<User> = NetworkResult.of {
    api.getUser()  // 异常自动转 AppError
}

// 业务层
suspend fun saveGame(game: Game): AppResult<Unit> = AppResult.of {
    database.gameDao().insert(game)
}
```

### AppError 子类型

```kotlin
sealed class AppError(val code: Int, val message: String, val cause: Throwable?) {
    class NetworkDisconnected(...) : AppError(-1, ...)
    class Timeout(...)              : AppError(-2, ...)
    class DnsResolution(...)         : AppError(-3, ...)
    class ServerError(val httpCode: Int, ...) : AppError(-4, ...)
    class ClientError(val httpCode: Int, ...) : AppError(-5, ...)
    class Unknown(...)               : AppError(-6, ...)
    class IoError(...)               : AppError(-7, ...)
    class SslError(...)              : AppError(-8, ...)
    class Cancelled(...)             : AppError(-9, ...)
    class BusinessError(message: String, ...) : AppError(-10, ...)
}
```

## 不要做

- ❌ 不要在 feature module 里再 new 一份 Result 类型
- ❌ 不要用 boolean 表示成功失败（旧式）
- ❌ 不要在 catch 块里裸 `printStackTrace()`

## 参考

- 详细规范: `docs/ERROR_HANDLING.md`


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)
