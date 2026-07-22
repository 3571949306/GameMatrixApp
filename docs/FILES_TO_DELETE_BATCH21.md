<!-- flutter-store-doc-sync: 2026-07-22 -->
> **Flutter-first production sync:** The Flutter module-store UI and customizable host navigation are live in stable vc595; Android remains authoritative for catalog trust, download, install, rollback, and runtime lifecycle. Production completion: 100%. See `/docs/flutter-store/MIGRATION_STATUS.md`.

# Batch 21 待删除文件清单

按用户规则 22，以下文件原计划在 Batch 21 任务结束之后删除。但编译验证发现 Hilt 模块仍引用，**暂不删除**。

## 暂不删除（编译失败，已恢复）

| 文件路径 | 原计划删除原因 | 实际编译失败原因 | 当前状态 |
|---|---|---|---|
| `app/src/main/kotlin/com/gamecenter/app/modular/ModuleDownloader.kt` | 与 `app/modules/ModuleDownloader.kt` 重复实现；全限定名 grep 无引用 | `ModularModule.kt` 在同包下通过短名 `ModuleDownloader` 引用，Hilt `@Provides provideModuleDownloader(okHttpClient, moduleCacheDir): ModuleDownloader` 导致 KSP 编译失败 | 已用 `git restore` 恢复 |

### 编译失败详细日志（参考）

```
e: [ksp] ModuleProcessingStep was unable to process 'com.gamecenter.app.modular.ModularModule'
    because 'ModuleDownloader' could not be resolved.
  Dependency trace:
>     => element (OBJECT): com.gamecenter.app.modular.ModularModule
>     => element (METHOD): provideModuleDownloader(okhttp3.OkHttpClient,java.io.File)
>     => type (ERROR return type): ModuleDownloader
```

### 引用链（Hilt DI）

`ModularModule.kt` 中以下 4 个 `@Provides` 方法形成引用链：
1. `provideModuleDownloader(okHttpClient, moduleCacheDir): ModuleDownloader`
2. `provideModuleCacheManager(context, moduleDao, downloader: ModuleDownloader): ModuleCacheManager`
3. `provideModuleManager(moduleDao, downloader: ModuleDownloader, cacheManager, moduleLoader): ModuleManager`

## 教训

- **grep 验证不能仅查全限定名**：同包内的短名引用会漏掉。需同时搜 `import com.gamecenter.app.modular.ModuleDownloader` 和 `\\bModuleDownloader\\b`（同包短名）。
- **删除文件前必须先确认无 Hilt/DI 引用**：Hilt `@Provides` 方法的返回类型和参数类型会通过 KSP 在编译期解析，删除会导致 KSP 失败。

## 后续清理方案（不在 Batch 21 范围内）

若未来要彻底删除 `modular/ModuleDownloader.kt`，需要同步：
1. 删除 `ModularModule.kt` 中 `provideModuleDownloader` 方法
2. 重构 `provideModuleCacheManager` 和 `provideModuleManager` 不再依赖 `ModuleDownloader`（或改为依赖主用 `app/modules/ModuleDownloader.kt`）
3. 检查 `ModuleCacheManager.kt` 和 modular 包下的 `ModuleManager.kt` 是否仍被生产代码使用
4. 删除文件后必须重新编译验证

## 保留（虽然也属于 modular 包，但有引用）

| 文件路径 | 保留原因 |
|---|---|
| `app/src/main/kotlin/com/gamecenter/app/modular/ModuleResourceLoader.kt` | 被 `ModuleLoader.kt`、`KlotskiModuleFragment.java`、`ChineseChessModuleFragment.java` 引用 |
| `app/src/main/kotlin/com/gamecenter/app/modular/ModuleInterface.kt` | 类型定义 |
| `app/src/main/kotlin/com/gamecenter/app/modular/ModularModule.kt` | Hilt 模块，提供 DI |
| `app/src/main/kotlin/com/gamecenter/app/modular/ModuleCacheManager.kt` | 被 `ModularModule.kt` 引用 |
| `app/src/main/kotlin/com/gamecenter/app/modular/ModuleManager.kt` | 被 `ModularModule.kt` 引用 |
| `app/src/main/kotlin/com/gamecenter/app/modular/ModuleDao.kt` | Room DAO，被 `ModularModule.kt` 引用 |
| `app/src/main/kotlin/com/gamecenter/app/modular/ModuleDatabase.kt` | Room Database，被 `ModularModule.kt` 引用 |
| `app/src/main/kotlin/com/gamecenter/app/modular/ModuleEntity.kt` | Room Entity |
| `app/src/main/kotlin/com/gamecenter/app/modular/ModuleInfo.kt` | 数据类 |
| `app/src/main/kotlin/com/gamecenter/app/modular/ModuleLoader.kt` | 被 `ModularModule.kt` 引用 |