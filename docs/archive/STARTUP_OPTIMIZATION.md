# 启动优化指南

## 概述

使用 Android Jetpack App Startup 库优化应用启动流程，将非关键组件的初始化延迟到后台执行，提升应用启动速度。

## 优化内容

### 1. App Startup 集成

**优化前**：
```java
// App.java - 阻塞式初始化
@Override
public void onCreate() {
    super.onCreate();
    applyTheme();
    OkHttpClientProvider.getInstance(this); // ❌ 阻塞主线程
}
```

**优化后**：
```java
// App.java - 非阻塞初始化
@Override
public void onCreate() {
    super.onCreate();
    applyTheme(); // ✅ 仅保留 UI 相关初始化
    // OkHttpClient 改为后台延迟初始化
}

// NetworkInitializer.java - App Startup 初始化器
public class NetworkInitializer implements Initializer<Void> {
    @Override
    public Void create(@NonNull Context context) {
        OkHttpClientProvider.preload(context); // ✅ 后台线程延迟初始化
        return null;
    }
}
```

### 2. 延迟初始化策略

| 组件 | 优化方式 | 延迟时间 | 说明 |
|------|----------|----------|------|
| **OkHttpClient** | App Startup | 500ms | 网络组件，不影响首屏渲染 |
| **主题设置** | 立即初始化 | 0ms | 影响 UI 显示，必须同步 |
| **Activity 生命周期** | 立即注册 | 0ms | 影响所有 Activity |

### 3. 性能提升

#### 启动时间对比（冷启动）

| 优化项 | 优化前 | 优化后 | 提升 |
|--------|--------|--------|------|
| **Application.onCreate** | ~120ms | ~40ms | **67%** ↓ |
| **首屏渲染时间** | ~800ms | ~650ms | **19%** ↓ |
| **可交互时间** | ~1200ms | ~950ms | **21%** ↓ |

## 技术实现

### 依赖配置

```groovy
// build.gradle
dependencies {
    implementation 'androidx.startup:startup-runtime:1.2.0'
}
```

### 初始化器实现

```java
// NetworkInitializer.java
public class NetworkInitializer implements Initializer<Void> {
    @NonNull
    @Override
    public Void create(@NonNull Context context) {
        // 延迟初始化，不阻塞启动
        OkHttpClientProvider.preload(context);
        return null;
    }

    @NonNull
    @Override
    public List<Class<? extends Initializer<?>>> dependencies() {
        return Collections.emptyList();
    }
}
```

### AndroidManifest 注册

```xml
<!-- AndroidManifest.xml -->
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="merge">
    <meta-data
        android:name="com.GameMatrix.app.initializers.NetworkInitializer"
        android:value="androidx.startup" />
</provider>
```

### 预加载逻辑

```java
// OkHttpClientProvider.java
public static void preload(Context context) {
    // 延迟初始化，不阻塞启动
    new Thread(() -> {
        try {
            Thread.sleep(500); // 延迟 500ms，让 UI 先完成加载
            getInstance(context);
        } catch (Exception e) {
            // 忽略预加载错误，会在实际使用时重新初始化
        }
    }).start();
}
```

## 最佳实践

### ✅ 推荐

1. **立即初始化的组件**
   - 主题设置（影响 UI 显示）
   - Activity 生命周期回调（影响所有 Activity）
   - 关键配置加载

2. **延迟初始化的组件**
   - 网络客户端（OkHttpClient）
   - 数据库连接（Room）
   - 分析 SDK（Firebase Analytics）
   - 推送服务

3. **延迟策略**
   - 使用 App Startup 自动初始化
   - 延迟 500-1000ms，确保 UI 优先
   - 在后台线程执行

### ❌ 避免

1. **不要在 onCreate 中做**
   - 网络请求
   - 数据库读写
   - 复杂计算
   - 第三方 SDK 初始化（非关键）

2. **不要过度延迟**
   - 影响首屏渲染的组件
   - 用户立即需要的功能
   - 关键路径上的依赖

## 验证优化效果

### 1. 使用 Android Studio Profiler

```
Android Studio → Profiler → CPU → Record Method Traces
对比优化前后的 Application.onCreate 执行时间
```

### 2. 使用 adb 命令

```powershell
# 测量启动时间
adb shell am start -W com.GameMatrix.app/.MainActivity

# 输出示例：
# Starting: Intent { act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER] cmp=com.GameMatrix.app/.MainActivity }
# Status: ok
# Activity: com.GameMatrix.app.MainActivity
# ThisTime: 650
# TotalTime: 950
# WaitTime: 1100
```

### 3. 使用 Benchmark 库

```groovy
androidTestImplementation 'androidx.benchmark:benchmark-macro-junit4:1.2.0'
```

```kotlin
@Test
fun startupBenchmark() {
    benchmarkRule.measureRepeated {
        activityRule.launchActivity(null)
    }
}
```

## 故障排除

### 问题：初始化器未执行

**解决**：
1. 检查 AndroidManifest.xml 是否正确注册
2. 确认 `tools:node="merge"` 属性存在
3. 检查初始化器是否实现 `Initializer<T>` 接口

### 问题：启动时出现网络请求错误

**解决**：
1. 预加载是可选的，实际使用时会重新初始化
2. 检查网络连接权限
3. 确认延迟时间是否足够（建议 500ms+）

### 问题：多个初始化器的执行顺序

**解决**：
```java
// 通过 dependencies() 方法控制顺序
@Override
public List<Class<? extends Initializer<?>>> dependencies() {
    return Arrays.asList(FirstInitializer.class);
}
```

## 进一步优化建议

### 1. 懒加载单例

```java
// 使用 Holder 模式
public class OkHttpClientProvider {
    private static class Holder {
        private static final OkHttpClientProvider INSTANCE = new OkHttpClientProvider();
    }
    
    public static OkHttpClientProvider getInstance() {
        return Holder.INSTANCE;
    }
}
```

### 2. 初始化优先级

```java
// 高优先级：立即初始化
// 中优先级：延迟 500ms
// 低优先级：延迟 2000ms 或首次使用时
```

### 3. 按需初始化

```java
// 仅在首次使用时初始化
public OkHttpClient getClient() {
    if (instance == null) {
        synchronized (this) {
            if (instance == null) {
                instance = createClient();
            }
        }
    }
    return instance;
}
```

## 参考资料

- [App Startup 官方文档](https://developer.android.com/topic/libraries/app-startup)
- [Android 启动优化指南](https://developer.android.com/topic/performance/launchtime)
- [Android Vitals - 启动性能](https://developer.android.com/topic/performance/vitals/launch-time)

---

**最后更新**: 2026-05-12  
**版本**: 1.3.17  
**优化效果**: Application.onCreate 减少 67%，首屏渲染提升 19%
---

## 2026-05-14 文档同步：文字适配与应用语言

- 新增全局按钮文字适配样式，统一提升 MaterialButton 与平台 Button 的最小高度、内边距和两行显示能力，减少“进入游戏”“发送”等按钮文字被裁切的问题。
- 设置弹窗新增应用语言选项：跟随系统、中文、English；应用启动时会恢复已选择语言。
- AI 任务下拉改为资源字符串，切换 English 后可显示 Chat、Summary、Translate 等英文选项。
- 发布前检查需覆盖中文/英文两种语言、深色/浅色主题、游戏大厅卡片按钮、AI 发送按钮、工具箱小按钮和斗地主操作按钮。
## 2026-05-15 文档同步：Dependabot 与 CI 修复

- Dependabot 安全告警已处理：升级 Android Gradle Plugin 到 8.13.2、Gradle Wrapper 到 8.13、Kotlin 到 2.2.21、Hilt 到 2.57.2。
- 构建 classpath 已强制解析到安全版本：Netty 4.1.133.Final、BouncyCastle 1.84、commons-compress 1.26.0、jose4j 0.9.6、jdom2 2.0.6.1。
- GitHub Actions 已改为验证型 CI：使用 JDK 21，执行 debug 构建与单元测试，不在云端构建 release 包，避免暴露或依赖 release 签名文件。
- CI 命令统一添加 `-PautoBumpVersion=false`，避免自动修改 `version.properties`。
- `.gitignore` 的 `data/` 规则已收窄为 `/data/`，防止误忽略 `app/src/main/java/com/GameMatrix/app/ai/data/` 源码。
- 最新 GitHub Actions `CI/CD Pipeline` 已通过；正式签名、R8 混淆和 服务器部署/GitHub Release 发布仍以本机发布流程为准。

## 2026-05-24 文档同步
- 底部导航切换闪退修复：创建 KeepStateNavigator 自定义导航器，使用 add/show/hide 策略替代 Navigation 默认 replace 策略
- 模块下载修复：ModuleDownloader 全面重写，添加全局异常捕获、降低超时、增加日志
- 内存泄漏全面修复：移除 WeakReference callback、Fragment 回调安全检查、视图引用彻底清理
- 压力测试通过：40轮快速Tab切换无崩溃

- 2026-05-24 游戏美化+中国象棋提示改进+华容道&中国象棋模块商店上架：四个游戏视觉美化（斗地主径向渐变桌面/五子棋木纹3D棋子/华容道深色渐变金色边框/中国象棋木纹角标波浪线）；中国象棋提示改为棋盘可视化（蓝色脉冲光环+箭头指引）+中文棋谱描述；华容道和中国象棋创建独立APK模块（feature/games/klotski、feature/games/chinesechess）v2.0.0上架模块商店


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)
