# Compose 迁移指南 (Phase 2.4)

> **当前技术参考（最后核验：2026-07-27）**  
> 本文记录渐进式 Compose 迁移原则。vc595 Flutter 商店发布结果属于历史证据；当前页面、模块和 Runtime 选择应以 [`CURRENT_STATE.md`](CURRENT_STATE.md) 与实际构建配置为准。

## 与 Flutter-first 商店的关系

- Flutter-first 只迁移模块商店 UI/交互，不等于全 App Flutter 重写。
- Compose 路线仍适用于 Android 宿主页面和需要宿主资源/生命周期的动态模块；Flutter 商店不会替代这些业务页面。
- 新模块先按 `runtimeType`/`deliveryType` 选择 Flutter、Web、Asset、Android、Native Service 或 Unity，再决定 Android Runtime 内部是否使用 Compose。
- 不要把现有 Compose 模块搬入 Flutter 以追求表面统一；只有已编译进宿主且具备稳定 Pigeon/route 合同的页面才可选择 Flutter Runtime。

## 现状

- ✅ `xiaomi-mimo-tts-android` 已经是 Compose-only 项目 (Android 端的姊妹项目)
- ✅ `module-store/feature/games/games/tts/` Android module 已用 Compose (build.gradle.kts line 47-50)
- ⚠️ 13 个 Android module 中只有 1 个 (TTS) 用 Compose, 其他 12 个还是 View 系统

## 样板已就位

- `module-store/feature/games/games/hall/src/main/java/.../HallScreen.kt` — 游戏大厅 Composable 样板
  - `HallScreen` (Scaffold + TopAppBar + Grid)
  - `HallGrid` (LazyVerticalGrid 自适应)
  - `GameCard` (Material 3 Card)
  - `HallViewModel` (StateFlow 暴露数据)
  - `@Preview` 函数 (Android Studio 直接预览)

## 给 feature module 加 Compose 的步骤

### 1. 改 build.gradle

```groovy
android {
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"  // 跟 Kotlin 2.0.21 兼容
    }
}
```

### 2. 加依赖

```groovy
dependencies {
    // Compose BOM — 统一管理所有 Compose 库版本
    val composeBom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(composeBom)
    implementation "androidx.compose.ui:ui"
    implementation "androidx.compose.ui:ui-graphics"
    implementation "androidx.compose.ui:ui-tooling-preview"
    implementation "androidx.compose.material3:material3"
    implementation "androidx.activity:activity-compose:1.9.0"
    implementation "androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7"
    implementation "androidx.hilt:hilt-navigation-compose:1.2.0"

    // 调试用
    debugImplementation "androidx.compose.ui:ui-tooling"
}
```

### 3. 写 Composable

参考 `hall/HallScreen.kt` 的模式:
- 顶层 `Scaffold` 提供 `padding`
- `collectAsState()` 接 ViewModel 的 StateFlow
- `items()` + `key =` 保证 LazyList 性能
- `@Preview` 让 IDE 实时预览

### 4. Activity 改 setContent

```kotlin
// 改前 (View 系统)
class HallActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hall)
        // findViewById / RecyclerView.Adapter 配一堆
    }
}

// 改后 (Compose)
class HallActivity : ComponentActivity() {
    @Inject lateinit var viewModel: HallViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                HallScreen(viewModel = viewModel, onGameClick = { id -> /* 跳游戏 */ })
            }
        }
    }
}
```

## 迁移优先级

按"业务稳定性 + 重构价值"排:

| 模块 | 价值 | 难度 | 建议时机 |
|---|---|---|---|
| `hall` (游戏大厅) | 高 - 频繁打开 | 中 | Phase 2.4+ 第一步 |
| `tools` (工具箱) | 中 | 低 | Phase 2.4+ 第二步 |
| `browser` | 中 | 高 (WebView 集成) | Phase 3+ |
| `ai` | 中 | 高 (AI 状态复杂) | Phase 3+ |
| `tts` 已 Compose | - | - | (不需要) |
| `doudizhu` 等游戏 | 低 (重业务逻辑) | 很高 | Phase 4+ 单独规划 |

## 注意事项

### Compose 与 ViewBinding 兼容

`buildFeatures.viewBinding = true` 可以跟 `compose = true` 共存:
- 旧 Activity 继续用 ViewBinding
- 新 Activity / Fragment 用 Compose
- 同一项目里可以渐进迁移

### Compose 与 KSP 兼容

Hilt + Compose + KSP 工作流:

```kotlin
@HiltViewModel
class HallViewModel @Inject constructor(
    private val gameRepo: GameRepository
) : ViewModel()
```

记得加 `androidx.hilt:hilt-navigation-compose` 才能在 Compose 里 `hiltViewModel()`。

### Compose 在动态模块 (DexClassLoader) 里

如果 feature module 是动态加载的（像 hall 这种），Compose runtime 必须在宿主 APK 里。
- 主 module 装 Compose
- 动态 module 用 `compileOnly "androidx.compose.ui:ui"` 防止 dex 冲突
- 实际加载时 ClassLoader 会从 host 找到 Compose 类

参考：`xiaomi-mimo-tts-android` 已经验证这条路径 OK。

## 参考

- https://developer.android.com/jetpack/compose
- https://developer.android.com/jetpack/compose/migration
- 项目内 TTS module 的 `TtsActivity.kt` 是生产代码样板


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)