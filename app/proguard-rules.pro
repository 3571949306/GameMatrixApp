# ProGuard 混淆规则（GameCenterApp 框架 APK）
# 用于 R8 全模式优化，目标：框架 APK 体积 ≤15MB

# ============ Android 核心组件 ============

-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep public class org.xmlpull.v1.XmlPullParser

# ============ 序列化 ============

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ============ 注解框架 ============

# Dagger/Hilt
-keep class dagger.* { *; }
-keep class * extends dagger.hilt.android.internal.*
-keepclasseswithmembers class * {
    @dagger.inject.* <methods>;
}
-keepclasseswithmembers class * {
    @javax.inject.Inject <methods>;
}
-keepclasseswithmembers class * {
    @javax.inject.Qualifier <methods>;
}

# ============ 网络层 ============

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.* { *; }
-keep interface okhttp3.* { *; }

# Gson
# 保留泛型签名（TypeToken<List<GameConfig>> 反射需要 Signature 属性）
# 保留注解（@SerializedName 字段映射需要 *Annotation* 属性）
# 缺失这两条会导致 Gson 反序列化时类型信息丢失，List<GameConfig> 被反序列化为
# List<LinkedTreeMap>，遍历时触发 ClassCastException（无消息，因 R8 优化裁剪了类型检查详情）
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.* { *; }
-keep class com.google.gson.reflect.** { *; }
-keep class com.google.gson.internal.** { *; }
-keep class com.google.gson.internal.bind.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# Gson 反序列化的数据模型类必须保留字段名（GameConfigLoader 读取 game_configs.json）
-keep class com.gamecenter.app.games.model.** { *; }
-keep class com.gamecenter.app.games.model.enums.** { *; }

# ============ 模块系统 ============

# FlutterEngine 通过反射调用这个生成类的固定 registerWith 方法。R8 可以优化
# 插件实现，但不得重命名或裁剪该反射入口，否则 Release 会静默跳过全部插件注册。
-keep class io.flutter.plugins.GeneratedPluginRegistrant { *; }

# 模块接口（动态加载，不能混淆）
-keep interface com.gamecenter.app.interfaces.IModule { *; }
-keep interface com.gamecenter.app.interfaces.IModuleLoader { *; }
-keep interface com.gamecenter.app.interfaces.IModuleStore { *; }

# 模块加载器
-keep class com.gamecenter.app.moduleloader.ModuleLoaderV2 { *; }
-keep class com.gamecenter.app.moduleloader.ModuleVerifier { *; }

# 模块入口点（通过反射加载，R8 无法静态分析 modules.json 字符串引用，必须保留）
# 修复：release版 BrowserModuleEntryPoint 被剪裁导致模块商店显示"未安装"
-keep interface com.gamecenter.app.core.common.ModuleInterface { *; }
-keep class com.gamecenter.app.core.common.ModuleInterface { *; }
-keep class com.gamecenter.app.core.common.ModuleInterface$* { *; }
-keep class * implements com.gamecenter.app.core.common.ModuleInterface { *; }
-keep interface com.gamecenter.app.core.common.FeatureModule { *; }
-keep class com.gamecenter.app.core.common.FeatureModule { *; }
-keep class com.gamecenter.app.core.common.FeatureModule$* { *; }
-keep class * implements com.gamecenter.app.core.common.FeatureModule { *; }
# 保留 core.common 包下所有数据类和枚举（模块系统核心协议，反射入口众多）
-keep class com.gamecenter.app.core.common.** { *; }

# 关键修复：模块 APK 通过 DexClassLoader 加载，其字节码以原始类名引用 AndroidX 类型。
# 若 R8 把 androidx.fragment.app.Fragment 重命名为 i81，则模块中 createFragment() 返回
# androidx.fragment.app.Fragment 的方法签名与宿主 FeatureModule.createFragment() 返回
# i81 的签名被视为不同方法，触发 AbstractMethodError。
# 因此所有模块接口签名中引用的 AndroidX 类型必须保持原名。
-keep class androidx.fragment.app.Fragment { *; }
-keep class androidx.fragment.app.Fragment$* { *; }
-keep class androidx.fragment.app.FragmentActivity { *; }
-keep class androidx.fragment.app.FragmentManager { *; }
-keep class androidx.fragment.app.FragmentTransaction { *; }
-keep class androidx.fragment.app.FragmentFactory { *; }
-keep class androidx.fragment.app.FragmentContainerView { *; }
-keep class androidx.lifecycle.ViewModel { *; }
-keep class androidx.lifecycle.LiveData { *; }
-keep class androidx.lifecycle.MutableLiveData { *; }
# Android Context 与 Bundle 是系统类不会被混淆，但 ModuleNavigationContribution 等返回
# 类型在模块侧被引用，统一保留 core.common 已覆盖。

# 模块加载全面保护：模块 APK 字节码以原始类名引用 AndroidX 公共 API。
# R8 full 模式会因"宿主未直接引用"而裁剪/重命名这些类，导致模块运行时
# NoClassDefFoundError 或 AbstractMethodError。以下包必须保持原名：
-keep class androidx.activity.** { *; }
-keep class androidx.fragment.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.core.** { *; }
-keep class androidx.savedstate.** { *; }
-keep class androidx.annotation.** { *; }
-keep class androidx.collection.** { *; }
-keep class androidx.arch.core.** { *; }

# Kotlin 标准库类型必须保持原名：模块 APK 的字节码以原始类名引用 Kotlin 类型
# （如 kotlin.Lazy、kotlin.jvm.functions.Function0、kotlin.reflect.KClass）。
# R8 full 模式会把这些类型重命名为短名（如 tl1、nw0、gf1），导致模块侧调用
# androidx.fragment.app.FragmentViewModelLazyKt.createViewModelLazy 时因方法签名
# 不匹配触发 NoSuchMethodError（典型表现：wrongbook 模块打开即崩溃）。
-keep class kotlin.** { *; }
-keep class kotlin.jvm.** { *; }
-keep class kotlin.reflect.** { *; }
# Kotlin 协程：ViewModelKt.getViewModelScope() 返回 kotlinx.coroutines.CoroutineScope
# R8 若重命名 CoroutineScope，会导致方法签名不匹配触发 NoSuchMethodError
# （典型表现：wrongbook ViewModel 初始化即崩溃）
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.coroutines.**$* { *; }
-keep class androidx.recyclerview.** { *; }
-keep class androidx.viewpager.** { *; }
-keep class androidx.viewpager2.** { *; }
-keep class androidx.appcompat.** { *; }
-keep class androidx.transition.** { *; }
-keep class androidx.cardview.** { *; }
-keep class androidx.constraintlayout.** { *; }
-keep class androidx.coordinatorlayout.** { *; }
-keep class androidx.recyclerview.widget.** { *; }
-keep class androidx.swiperefreshlayout.** { *; }
-keep class androidx.drawerlayout.** { *; }
-keep class androidx.slidingpanelayout.** { *; }
-keep class androidx.customview.** { *; }
-keep class androidx.vectordrawable.** { *; }
-keep class androidx.interpolator.** { *; }
-keep class androidx.loader.** { *; }
-keep class androidx.localbroadcastmanager.** { *; }
-keep class androidx.documentfile.** { *; }
-keep class androidx.exifinterface.** { *; }
-keep class androidx.preference.** { *; }
-keep class androidx.palette.** { *; }
-keep class androidx.room.** { *; }
-keep class androidx.work.** { *; }
-keep class androidx.paging.** { *; }
-keep class androidx.sqlite.** { *; }
-keep class androidx.databinding.** { *; }

# Material Components：模块 APK 引用 ChipGroup / Chip 等组件及其内部接口。
# R8 会裁剪未直接引用的内部接口（如 OnCheckedStateChangeListener），导致 NoClassDefFoundError。
-keep class com.google.android.material.** { *; }
-keep class com.google.android.material.**$* { *; }

# R 类与资源：模块 APK 以 com.gamecenter.app.R.layout.* / .id.* 形式引用宿主资源。
# R8 会重命名或裁剪"未直接引用"的 R 类，导致 NoClassDefFoundError: R$layout。
-keep class com.gamecenter.app.R { *; }
-keep class com.gamecenter.app.R$* { *; }
-keep class com.gamecenter.app.R$id { *; }
-keep class com.gamecenter.app.R$layout { *; }
-keep class com.gamecenter.app.R$string { *; }
-keep class com.gamecenter.app.R$drawable { *; }
-keep class com.gamecenter.app.R$color { *; }
-keep class com.gamecenter.app.R$style { *; }
-keep class com.gamecenter.app.R$dimen { *; }
-keep class com.gamecenter.app.R$mipmap { *; }
-keep class com.gamecenter.app.R$anim { *; }
-keep class com.gamecenter.app.R$attr { *; }
-keep class com.gamecenter.app.R$menu { *; }
-keep class com.gamecenter.app.R$raw { *; }
-keep class com.gamecenter.app.R$array { *; }
-keep class com.gamecenter.app.R$integer { *; }
-keep class com.gamecenter.app.R$bool { *; }
-keep class com.gamecenter.app.R$styleable { *; }
-keep class com.gamecenter.app.R$plurals { *; }
-keep class com.gamecenter.app.R$font { *; }
-keep class com.gamecenter.app.R$navigation { *; }
-keep class com.gamecenter.app.R$xml { *; }

# 宿主工具类：模块 APK 直接调用宿主类（如 SettingsManager.isDarkMode）。
# 这些类的公共静态方法必须保持原名，否则模块运行时 NoSuchMethodError。
-keep class com.gamecenter.app.SettingsManager { *; }
-keep class com.gamecenter.app.SettingsManager$* { *; }
-keep class com.gamecenter.app.BuildConfig { *; }
-keep class com.gamecenter.app.BuildConfig$* { *; }
-keep class com.gamecenter.app.ColorSchemeManager { *; }
-keep class com.gamecenter.app.ColorSchemeManager$* { *; }
# 2026-08-23 模块分层回归修复：模块化游戏 APK（chinesechess 等）直接调用宿主
# 游戏工具类。R8 混淆重命名后模块侧 ClassNotFoundException / NoSuchMethodError
# （表现：中国象棋启动即崩溃退出）。
-keep class com.gamecenter.app.games.GameUsageStore { *; }
-keep class com.gamecenter.app.games.GameUsageStore$* { *; }
-keep class com.gamecenter.app.games.GameTutorialHelper { *; }
-keep class com.gamecenter.app.games.GameTutorialHelper$* { *; }

# 宿主视图与工具类：模块 APK 直接引用宿主自定义 View / 工具类。
-keep class com.gamecenter.app.views.** { *; }
-keep class com.gamecenter.app.utils.** { *; }
-keep class com.gamecenter.app.network.** { *; }
-keep class com.gamecenter.app.moduleloader.** { *; }

# 模块系统 API：模块 APK 通过反射或直接调用访问 ModuleManager / ModuleLoader。
-keep class com.gamecenter.app.modules.ModuleManager { *; }
-keep class com.gamecenter.app.modules.ModuleManager$* { *; }
-keep class com.gamecenter.app.modules.ModuleLoader { *; }
-keep class com.gamecenter.app.modules.ModuleLoader$* { *; }
-keep class com.gamecenter.app.modules.ModuleVerifier { *; }

# 统一加载器真源（core:module-host）：经 facade 委托与 DexClassLoader 反射加载，
# R8 混淆会破坏类名/签名，必须整体保留。
-keep class com.gamecenter.app.core.modulehost.** { *; }

# modular 包：模块 APK 通过 ModuleManager.getModuleResources() 等方法返回的类型
# 被 wrongbook 等模块引用。R8 若重命名 ModuleResourceLoader$ModuleResources 等类型，
# 会导致方法签名不匹配触发 NoSuchMethodError（典型表现：wrongbook 打开即崩溃）。
-keep class com.gamecenter.app.modular.** { *; }
-keep class com.gamecenter.app.modular.**$* { *; }

# UI 共享组件：模块 APK 调用 ConsentDialog 等共享 UI。
-keep class com.gamecenter.app.ui.** { *; }
-keep class com.gamecenter.app.ui.ConsentDialog { *; }
-keep class com.gamecenter.app.ui.ConsentDialog$* { *; }

# 模块导航贡献协议（ModuleRegistry 通过反射注册，R8 full 模式会移除接口）
# 修复：release 版 MainActivity 崩溃 NoClassDefFoundError: ModuleNavigationContribution
-keep interface com.gamecenter.app.core.common.ModuleNavigationContribution { *; }
-keep class com.gamecenter.app.core.common.ModuleNavigationContribution { *; }
-keep class com.gamecenter.app.core.common.ModuleNavigationContribution$* { *; }
-keep class * implements com.gamecenter.app.core.common.ModuleNavigationContribution { *; }
-keep class com.gamecenter.app.core.common.NavigationSlot { *; }
-keep class com.gamecenter.app.core.common.NavigationSlot$* { *; }
# 模块注册表与导航贡献条目（ModuleRegistry.NavigationContributionEntry 是反射入口）
-keep class com.gamecenter.app.core.common.ModuleRegistry { *; }
-keep class com.gamecenter.app.core.common.ModuleRegistry$* { *; }

# 所有 *ModuleEntryPoint 命名的类（双保险，覆盖 modules.json 中 entryClass 字段引用）
-keep class com.gamecenter.app.**.*ModuleEntryPoint { *; }
-keep class com.gamecenter.app.**.*ModuleEntryPoint$* { *; }

# ============ 数据模型 ============

# Parcelable 实现
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
-keep class * implements android.os.Parcelable$Creator {
    public * createFromParcel(android.os.Parcel);
    public *[] newArray(int);
}

# ModuleInfo、ModuleVersion 等 — 按需添加具体模型类
# -keep class com.gamecenter.app.models.ModuleInfo { *; }
# -keep class com.gamecenter.app.models.ModuleVersion { *; }

# ============ WebSockets ============

# WebSocket 相关（okhttp3 WebSocketListener）
-keep class okhttp3.WebSocketListener { *; }
-keep class com.gamecenter.app.online.GameSocketClient* { *; }

# ============ 资源收缩白名单 ============

# ============ 游戏模块 ============

# 保留游戏模块中的匿名内部类和Lambda类（避免重复类问题）
-keep class com.gamecenter.app.games.breakout.BreakoutActivity$* { *; }
-keep class com.gamecenter.app.games.**$* { *; }

# 保留所有Activity的布局引用
-keepclassmembers class * extends android.app.Activity {
    public void *(android.view.View);
}

# 保留所有 XML 中引用的 View 类
-keepclasseswithmembers class * {
    <init>(android.content.Context, android.util.AttributeSet);
    <init>(android.content.Context, android.util.AttributeSet, int);
}

# ============ 调试保留 ============

# 保留已混淆后的类名映射（用于崩溃报告）
-printmapping build/outputs/mapping/release/mapping.txt
-printseeds build/outputs/mapping/release/seeds.txt

# ============ 优化选项 ============

# 允许访问修改的 Jar（提高优化效果）
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''

# 移除日志（Release 版）
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

# ============ 房间模块 ============

# 保留房间模块中的 WebSocket 和消息类
-keep class com.gamecenter.app.room.*Socket* { *; }
-keep class com.gamecenter.app.room.*Message* { *; }

# ============ 枚举类 ============

# 保留枚举类
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# R8 missing classes
-dontwarn javax.lang.model.element.Modifier

# ============ Compose (Phase 1 试点) ============
# Compose runtime 已自带 keep 规则（androidx.compose.runtime.R8KeepRules），
# 但 material3/ui-tooling 的部分反射 API 需要额外保留以防 R8 full 模式裁剪。

# 保留 Compose Modifier 链与 Composable 函数（R8 full 模式下 lambda 内联可能导致反射失效）
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.material.icons.** { *; }
-keep class androidx.activity.compose.** { *; }

# Compose lambdas — 保留合成方法签名，防止 R8 误裁剪 @Composable 闭包
-keepclassmembers class ** {
    @androidx.compose.runtime.Composable <methods>;
}

# ============ 模块热更改造（2026-08-29）：动态模块运行时依赖的宿主类 ============
# 模块 APK 经 compileOnly app-classes.jar 编译，运行时按父加载器解析宿主类；
# 以下宿主类被模块源码直接引用但此前无 keep 覆盖，release R8 改名后会导致
# 模块内 NoClassDefFoundError（仅 release 暴露）。
# SaveManager：10 个模块引用（dice/game2048/guess/klotski/match/memory/reaction/snake/tiles/whack）
-keep class com.gamecenter.app.SaveManager { *; }
# AppExecutors：ai 模块反射调用（AiTaskRouter Class.forName，失败会降级但失去线程池能力）
-keep class com.gamecenter.app.core.threading.AppExecutors { *; }

