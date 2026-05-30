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
-keep public class android.*
-keep public class androidx.**

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

# 模块接口（动态加载，不能混淆）
-keep interface com.gamecenter.app.interfaces.IModule { *; }
-keep interface com.gamecenter.app.interfaces.IModuleLoader { *; }
-keep interface com.gamecenter.app.interfaces.IModuleStore { *; }

# 模块加载器
-keep class com.gamecenter.app.moduleloader.ModuleLoaderV2 { *; }
-keep class com.gamecenter.app.moduleloader.ModuleVerifier { *; }

# ============ 数据模型 ============

# Parcelable 实现
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
-keep class * implements android.os.Parcelable$Creator {
    public * createFromParcel(android.os.Parcel);
    public *[] newArray(int);
}

# ModuleInfo、ModuleVersion 等
-keep class com.gamecenter.app.models.* { *; }

# ============ WebSockets ============

# WebSocket 相关（okhttp3 WebSocketListener）
-keep class okhttp3.WebSocketListener { *; }
-keep class com.gamecenter.app.online.GameSocketClient* { *; }

# ============ 资源收缩白名单 ============

# 保留所有 Activity 的布局引用
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
