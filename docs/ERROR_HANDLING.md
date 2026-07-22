<!-- flutter-store-doc-sync: 2026-07-22 -->
> **Flutter-first production sync:** The Flutter module-store UI and customizable host navigation are live in stable vc595; Android remains authoritative for catalog trust, download, install, rollback, and runtime lifecycle. Production completion: 100%. See `/docs/flutter-store/MIGRATION_STATUS.md`.

# 错误处理 & Result 类型 (Phase 2.5)

## 三种 Result 类型

| 类型 | 位置 | 用途 | 何时用 |
|---|---|---|---|
| `kotlin.Result<T>` | 标准库 | 一般 try-catch 包结果 | 已有代码用了, 不主动引入新用法 |
| `AppResult<T>` | `:core:common/util/` | 通用三态 (Success/Error/Loading) | UI 状态、流式数据、业务操作 |
| `NetworkResult<T>` | `:core:common/util/` | 网络三态 (Success/Failure/Loading) | 网络 API 调用, 自带 AppError |

## AppError (10 种错误)

`AppError` 是密封类, 10 个子类型 + 工厂方法:

| 错误码 | 错误类型 | 何时抛 |
|---|---|---|
| -1 | NetworkDisconnected | 没网 |
| -2 | Timeout | SocketTimeoutException |
| -3 | DnsResolution | UnknownHostException |
| -4 | ServerError | HTTP 5xx |
| -5 | ClientError | HTTP 4xx |
| -6 | Unknown | 其他 |
| -7 | IoError | IOException |
| -8 | SslError | SSL 握手失败 |
| -9 | Cancelled | 请求被取消 |
| -10 | BusinessError | 业务自定义错误 |

## 使用约定 (Phase 2.5 统一)

### 网络层 (Retrofit / OkHttp)
返回 `NetworkResult<T>`, 错误用 `AppError`:

```kotlin
suspend fun fetchUser(): NetworkResult<User> {
    return NetworkResult.of {
        api.getUser()
    }
    // 自动包成 NetworkResult.Success 或 NetworkResult.Failure(AppError)
}
```

### UI / ViewModel
用 `AppResult<T>`, 因为 UI 不需要 network-specific 错误分类:

```kotlin
val state: StateFlow<AppResult<List<Game>>> = _state

// 在 Compose / View 里
state.collectAsState().also { result ->
    when (result) {
        is AppResult.Loading -> LoadingSpinner()
        is AppResult.Success -> GameList(result.data)
        is AppResult.Error -> ErrorView(result.message)
    }
}
```

### 业务操作 (不涉及网络)
也用 `AppResult<T>`:

```kotlin
suspend fun saveGame(game: Game): AppResult<Unit> = AppResult.of {
    database.gameDao().insert(game)
}
```

## 工厂方法

```kotlin
// 自动捕获异常包成 Failure
val r1: NetworkResult<User> = NetworkResult.of { api.getUser() }

// 结果可能为 null 的场景
val r2: NetworkResult<User> = NetworkResult.ofNullable { 
    cache.getUser()  // 可能返回 null
}

// 从 Exception 构造 AppError
val err: AppError = AppError.fromException(SocketTimeoutException())

// 从 HTTP code 构造
val err2: AppError = AppError.fromHttpCode(404, "Not Found")
```

## 链式处理

```kotlin
fetchUser()
    .map { it.name }              // Success(User) -> Success(String)
    .onSuccess { name -> log(name) }
    .onFailure { err -> log(err.message) }
    .getOrNull()                   // String? or null
```

## 错误分类 (UI 用)

```kotlin
when (val err = result.getErrorOrNull()) {
    is AppError.NetworkDisconnected -> showToast("请检查网络")
    is AppError.Timeout -> showRetryButton()
    is AppError.ServerError -> showServerErrorToast(err.httpCode)
    is AppError.BusinessError -> showBusinessMessage(err.message)
    else -> showGenericError()
}
```

## 别再用 try-catch + boolean

❌ 老式 (Phase 2.5 起禁):
```kotlin
fun fetchUser(): User? {
    return try {
        api.getUser()
    } catch (e: Exception) {
        Log.e(TAG, "fetch failed", e)
        null  // 调用方不知道是网络错还是没数据
    }
}
```

✅ 新式 (统一用 Result):
```kotlin
suspend fun fetchUser(): NetworkResult<User> {
    return NetworkResult.of { api.getUser() }
    // 调用方用 onSuccess / onFailure 显式处理
}
```

## CI 守门

Phase 2.5+ 在 detekt 加自定义规则（Phase 2.5+ 写）:

```yaml
# config/detekt/detekt.yml
style:
  ForbiddenMethod:
    methods:
      - value: 'java.lang.Throwable.printStackTrace'
        reason: '错误必须走 AppError, 不能裸打 stack'
```

## 参考

- 项目内 `AppError.kt` / `NetworkResult.kt` / `AppResult.kt` 完整源码
- https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-result.html


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)