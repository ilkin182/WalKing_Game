# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line numbers for readable release stack traces, but hide the real file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Room ---
# Room's own AAR ships consumer rules for its generated Impl classes; these are a defensive
# backstop so entity/DAO shape survives even if that changes across versions.
-keep class com.example.data.local.entity.** { *; }
-keep interface com.example.data.local.dao.** { *; }
-keep class com.example.data.local.AppDatabase

# --- Domain models crossing Moshi/Firebase/Room boundaries via reflection-adjacent APIs ---
-keep class com.example.domain.model.** { *; }

# --- Firebase Auth / Play Services ---
# Firebase's SDKs discover components via reflection at startup (ComponentDiscoveryService).
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# --- Moshi (codegen) ---
# We use moshi-kotlin-codegen (KSP), so adapters are generated, not reflective; keep their
# generated constructors so R8 doesn't strip what Moshi looks up by name.
-keepclasseswithmembers class * extends com.squareup.moshi.JsonAdapter {
    <init>(...);
}
-keep @com.squareup.moshi.JsonClass class *
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}

# --- Retrofit / OkHttp (standard recommended rules) ---
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# --- H3 (com.uber.h3core) ---
-keep class com.uber.h3core.** { *; }
-dontwarn com.uber.h3core.**

# --- osmdroid ---
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
