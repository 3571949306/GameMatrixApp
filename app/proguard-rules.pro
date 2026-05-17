# ============ 基础设置 ============
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-verbose
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

# ============ Android 基础保持 ============
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep public class * extends android.view.View
-keep class androidx.fragment.app.** { *; }
-keep class androidx.appcompat.app.** { *; }

# ============ WebView JavaScript 接口 ============
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ============ Android 通用保持 ============
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes SourceFile,LineNumberTable

# ============ 反射和序列化 ============
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ============ 枚举 ============
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============ 游戏中心主包 ============
-keep class com.gamecenter.app.model.** { *; }
-keep class com.gamecenter.app.games.doudizhu.model.** { *; }
-keep class com.gamecenter.app.games.doudizhu.network.** { *; }
-keep class com.gamecenter.app.games.doudizhu.utils.** { *; }
-keepclassmembers class com.gamecenter.app.** {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class com.gamecenter.app.R$* {
    public static <fields>;
}

# ============ 第三方库保持 ============

# OkHttp 自带 ProGuard 规则，无需手动 keep

# Glide 自带 ProGuard 规则，仅需保留自定义模块
-keep public class * implements com.bumptech.glide.module.GlideModule

# ZXing
-keep class com.google.zxing.** { *; }

# ============ 系统 API 调用 ============
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ============ Native 方法 ============
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.auto.value.AutoValue$Builder
-dontwarn com.google.auto.value.AutoValue
-dontwarn com.google.mediapipe.framework.image.BitmapExtractor
-dontwarn com.google.mediapipe.framework.image.ByteBufferExtractor
-dontwarn com.google.mediapipe.framework.image.MPImage
-dontwarn com.google.mediapipe.framework.image.MPImageProperties
-dontwarn com.google.mediapipe.framework.image.MediaImageExtractor
-dontwarn com.google.protobuf.Internal$ProtoMethodMayReturnNull
-dontwarn com.google.protobuf.Internal$ProtoNonnullApi
-dontwarn com.google.protobuf.ProtoField
-dontwarn com.google.protobuf.ProtoPresenceBits
-dontwarn com.google.protobuf.ProtoPresenceCheckedField
