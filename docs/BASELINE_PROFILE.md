<!-- flutter-store-doc-sync: 2026-07-22 -->
> **Flutter-first production sync:** The Flutter module-store UI and customizable host navigation are live in stable vc595; Android remains authoritative for catalog trust, download, install, rollback, and runtime lifecycle. Production completion: 100%. See `/docs/flutter-store/MIGRATION_STATUS.md`.

# Baseline Profile (启动加速)

## 是什么

Baseline Profile 是一组 ART 编译器在**安装期**就能预先 AOT 编译的 class/method 列表。
效果：
- **冷启动加速 20-30%**（第一次启动无 JIT 等待）
- 类加载更快（DEX 优化提前）
- 减少冷启动卡顿

## 项目当前状态（Phase 2.1）

✅ 启用了 `androidx.profileinstaller:1.4.1`
✅ 写了手工 `app/baseline-prof.txt`（覆盖 7 个核心 module 的启动热路径）
✅ R8 minify 识别 profile（自动打包进 APK 的 `assets/dexopt/baseline.prof`）

## 文件位置

- `app/baseline-prof.txt` — 手工维护的规则
- `:benchmark` module（待 Phase 2.1+ 创建）— MacroBenchmark 自动生成

## 手工规则覆盖范围

当前覆盖的启动热路径（手工基线）：

| 路径 | 规则数 | 用途 |
|---|---|---|
| Application / MainActivity | 4 | 启动入口 |
| Hilt 注入 | 2 | DI 初始化 |
| Room / SQLite | 3 | 数据库 |
| OkHttp / Okio | 3 | 网络 |
| Module 加载 | 5 | 核心业务 |
| Material / UI | 3 | UI 渲染 |
| Gson / 工具 | 2 | JSON |
| 业务类 (core:common/network/update/security) | 4 | 启动时实例化 |
| GameRegistry / Router | 2 | 路由 |

## 如何改进（Phase 2.1+）

### 1. 跑 MacroBenchmark 自动生成

创建 `:benchmark` module：

```kotlin
// benchmark/src/androidTest/java/.../BaselineProfileGenerator.kt
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "com.gamecenter.app",
        maxIterations = 5,
        stableIterations = 3
    ) {
        // 启动 app, 走主流程
        pressHome()
        startActivityAndWait()
        device.findObject(By.text("Gomoku")).click()
        Thread.sleep(2000)
        pressBack()
    }
}
```

跑：`./gradlew :benchmark:pixel6Api31BenchmarkAndroidTest`

### 2. 把生成的 profile 合到 baseline-prof.txt

MacroBenchmark 会输出到 `benchmark/build/outputs/managed_device_files/.../baseline-prof.txt`，
复制到 `app/baseline-prof.txt` 覆盖手工基线。

### 3. 测量效果

用 MacroBenchmark 的 startup test：

```kotlin
@Test
fun startup() = benchmarkRule.measureRepeated(
    packageName = "com.gamecenter.app",
    metrics = listOf(StartupTimingMetric()),
    iterations = 5,
    startupMode = StartupMode.COLD
) {
    pressHome()
    startActivityAndWait()
}
```

对比 baseline 优化前/后启动时间。

## 注意事项

- 每次发版前重新生成（业务代码变了，启动路径就变了）
- 规则数控制：1K-3K 条是健康的，超过 5K 说明过度优化
- 跟 R8 minify 配合用：profileinstaller 把 profile 装到 APK assets，
  R8 读 profile 决定哪些 class AOT 编译

## 参考

- https://developer.android.com/topic/performance/baselineprofiles
- https://android-developers.googleblog.com/2022/01/baseline-profiles-OTA.html


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)