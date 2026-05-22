# ============ 基础设置 ============
-optimizationpasses 5
-dontusemixedcaseclassnames
-verbose
-keepattributes Signature,Exceptions,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# ============ Android 基础保持 ============
-keep public class * extends android.app.Activity { public <init>(); }
-keep public class * extends android.app.Application { public <init>(); }
-keep public class * extends android.app.Service { public <init>(); }
-keep public class * extends android.content.BroadcastReceiver { public <init>(); }
-keep public class * extends android.content.ContentProvider { public <init>(); }
-keep public class * extends android.app.backup.BackupAgentHelper { public <init>(); }
-keep public class * extends android.preference.Preference { public <init>(); }
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    protected void onMeasure(int, int);
    protected void onDraw(android.graphics.Canvas);
    public void onClick(android.view.View);
}

# ============ AndroidX ============
-keep class androidx.fragment.app.** { *; }
-keep class androidx.appcompat.app.** { *; }

# ============ WebView ============
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

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
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============ R 类 ============
-keepclassmembers class com.gamecenter.app.R$* { public static <fields>; }
-keepclassmembers class com.gamecenter.app.core.**.R$* { public static <fields>; }

# ============ 主包保留（WebView JS 接口） ============
-keep class com.gamecenter.app.** {
    @android.webkit.JavascriptInterface <methods>;
}

# ============ Room ============
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.paging.**

# ============ Hilt ============
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep,allowobfuscation,allowshrinking class * extends dagger.hilt.android.internal.managers.ApplicationComponentManager { *; }

# ============ Coroutines ============
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-keepclassmembers class kotlin.coroutines.SafeContinuation { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# ============ OkHttp / Okio ============
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ============ Gson ============
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.stream.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation,allowshrinking class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# ============ Glide (KSP) ============
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder { *** rewind(); }

# ============ MediaPipe ============
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.auto.value.AutoValue$Builder
-dontwarn com.google.auto.value.AutoValue
-dontwarn com.google.protobuf.Internal$*
-dontwarn com.google.mediapipe.framework.image.BitmapExtractor
-dontwarn com.google.mediapipe.framework.image.ByteBufferExtractor
-dontwarn com.google.mediapipe.framework.image.MPImage
-dontwarn com.google.mediapipe.framework.image.MPImageProperties
-dontwarn com.google.mediapipe.framework.image.MediaImageExtractor
-dontwarn com.google.protobuf.ProtoField
-dontwarn com.google.protobuf.ProtoPresenceBits
-dontwarn com.google.protobuf.ProtoPresenceCheckedField

# ============ ZXing ============
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# ============ Native ============
-keepclasseswithmembernames class * {
    native <methods>;
}

# ============ 其他 ============
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
