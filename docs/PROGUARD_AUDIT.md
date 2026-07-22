<!-- flutter-store-doc-sync: 2026-07-22 -->
> **Flutter-first production sync:** The Flutter module-store UI and customizable host navigation are live in stable vc595; Android remains authoritative for catalog trust, download, install, rollback, and runtime lifecycle. Production completion: 100%. See `/docs/flutter-store/MIGRATION_STATUS.md`.

# ProGuard / R8 审计 (Phase 3)

## 当前状态

`app/proguard-rules.pro`（3.9 KB）覆盖：
- ✅ Android 核心组件（Activity/Application/Service/BroadcastReceiver）
- ✅ 序列化（Serializable / Parcelable）
- ✅ Dagger/Hilt 注入
- ✅ OkHttp / Okio
- ✅ Gson
- ✅ 模块系统接口（IModule 等不能混淆）
- ✅ 模块加载器（ModuleLoaderV2, ModuleVerifier）
- ✅ 数据模型（com.gamecenter.app.models.*）
- ✅ WebSocket 监听器
- ✅ 资源收缩白名单

**R8 minifyEnabled = true** + **shrinkResources = true** 启用。

## ⚠️ 已知 release build 失败 (Phase 2.1 之后)

R8 报：

```
ERROR: com.gamecenter.app.games.breakout.BreakoutActivity$1 is defined multiple times
```

**根因**：`:app` 同时有两个来源包含 `BreakoutActivity$1`:
1. `module-store/feature/games/games` 的 aar (编译期 implementation)
2. `app/build/intermediates/.../BreakoutActivity$1.class` (asm transform 产物)

这是**预存在**问题（跟 Phase 1.3 KSP 迁移无关），是动态模块加载架构的设计张力：
- 模块要**动态**加载（DexClassLoader）→ 必须在模块里独立编译
- 但 :app 也**静态**依赖同一模块 → 编译时也有副本

## 修复方案 (Phase 3+ 实施)

### 方案 A：分离静态/动态编译产物 (推荐)

```groovy
// :app/build.gradle
dependencies {
    // 静态: 只要游戏注册元数据, 不让模块类重复打进来
    compileOnly project(':module-store:feature:games:games')

    // 动态加载: 模块本身打成 APK 从 VPS 拉
    // (生产环境)
}
```

### 方案 B：R8 multi-pass (短期)

```groovy
// app/proguard-rules.pro 加
-keep class com.gamecenter.app.games.breakout.BreakoutActivity { *; }
-keep class com.gamecenter.app.games.breakout.BreakoutActivity$* { *; }
```

（明确 keep 重复的类，让 R8 跳过）

### 方案 C：拆分模块

把"游戏注册中心"从"游戏实现"分离：
- `:module-store:feature:games:registry` - 只放 GameEntry 注册 (轻量)
- `:module-store:feature:games:games` - 放游戏实现 (重)

`:app` 只依赖 registry, 不依赖 games。

## 临时建议

**短期（Phase 3 不修）**:
- 不打 release 包，发布就用 debug 包 + signing
- 或绕过去: `./gradlew :app:assembleRelease -PskipMinify=true`

**长期（Phase 4+）**: 实施方案 A 或 C。

## ProGuard 规则审计清单

| 模块 | 需要 keep 的类 | 状态 |
|---|---|---|
| `:app` | Application / Activities | ✅ |
| `:core:network` | OkHttp / Interceptors | ✅ |
| `:core:common` | Util classes (gson 序列化) | ✅ |
| `:core:security` | 证书固定 keystore 类 | ⚠️ 可能缺 keep 规则 |
| `:core:moduleloader` | ModuleLoader, ModuleVerifier | ✅ |
| `:core:modulestore` | ModuleDownloadTask, ModuleVerifier | ⚠️ 可能缺 |
| `module-store/feature/games/games` | 各游戏 Activity | ✅ (重复类问题除外) |
| `module-store/feature/tools/ai` | AI Activity | ⚠️ 待审 |
| `module-store/feature/tools/vpn` | VPN service | ⚠️ 待审 |

## 如何审 (Phase 3+)

1. 打 release 包 (绕开 minify): `assembleRelease -PskipMinify`
2. 装到真机测所有功能
3. 打开 R8 full mode report:
   ```
   ./gradlew :app:minifyReleaseWithR8 -- --print-report
   ```
4. 看哪些类被误删/误重命名
5. 针对性补 keep 规则

## 通用 keep 规则模板

```proguard
# 注解框架
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep,allowobfuscation @interface dagger.hilt.android.HiltAndroidApp
-keep,allowobfuscation @interface dagger.hilt.android.AndroidEntryPoint

# Compose runtime (Phase 2.4+)
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# Kotlinx coroutines
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
```

## 参考

- https://developer.android.com/build/shrink-code
- https://r8.googlesource.com/r8
- 项目内 `app/proguard-rules.pro` 完整源码


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)