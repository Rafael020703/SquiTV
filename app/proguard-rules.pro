# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\rafas\AppData\Local\Android\Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.

# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep rules here:

# Retrofit
-keepattributes Signature, InnerClasses, AnnotationDefault
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Hilt / Dagger
-keep class com.google.dagger.** { *; }
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent
-keep class * extends dagger.hilt.internal.GeneratedComponentManager
-keep class * extends dagger.hilt.internal.ComponentManager
-keep @dagger.hilt.EntryPoint class *
-keep @dagger.hilt.InstallIn class *
-keep @dagger.hilt.android.lifecycle.HiltViewModel class *

# Kotlin Serialization
-keepattributes *Annotation*, EnclosingMethod, Signature
-keepclassmembernames class kotlinx.serialization.json.** {
    *** serializer(...);
}
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    *** Companion;
}
-keepclassmembers class ** {
    *** $serializer;
}
-keepclasseswithmembers class ** {
    *** $serializer;
}



# Media3
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.session.** { *; }
-dontwarn androidx.media3.**

# Application Domain and Data Models
-keep class rsv.squitv.data.model.** { *; }
-keep class rsv.squitv.data.local.entities.** { *; }
-keep class rsv.squitv.domain.model.** { *; }
-keep class rsv.squitv.core.domain.state.** { *; }
-keep class rsv.squitv.core.navigation.Route** { *; }

# AppFunctions
-keep class androidx.appfunctions.** { *; }
-keep @androidx.appfunctions.AppFunction class * { *; }

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# General
-keepattributes SourceFile, LineNumberTable
-keepattributes *Annotation*
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}
