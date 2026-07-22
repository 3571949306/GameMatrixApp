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
-keep class com.google.gson.* { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

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
# 修复：release 版 BrowserModuleEntryPoint 被剪裁导致模块商店显示"未安装"
-keep interface com.gamecenter.app.core.common.ModuleInterface { *; }
-keep class * implements com.gamecenter.app.core.common.ModuleInterface { *; }
-keep interface com.gamecenter.app.core.common.FeatureModule { *; }
-keep class * implements com.gamecenter.app.core.common.FeatureModule { *; }

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
