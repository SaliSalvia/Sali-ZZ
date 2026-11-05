# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class com.salizzai.app.**$$serializer { *; }
-keepclassmembers class com.salizzai.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.salizzai.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Room
-keep class androidx.room.** { *; }
-keep class com.salizzai.app.data.db.** { *; }
-keep class com.salizzai.app.data.db.entity.** { *; }

# Markwon
-keep class io.noties.markwon.** { *; }
-keep class org.commonmark.** { *; }

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# Credential Manager + Google Identity
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn com.google.android.libraries.identity.googleid.**

# EncryptedSharedPreferences
-keep class androidx.security.crypto.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep app data classes (used by Room + serialization)
-keep class com.salizzai.app.data.model.** { *; }

# Kotlin metadata — keep reflection working for KSP-generated Room code
-keep class kotlin.Metadata { *; }

# R8 full mode safety: keep generic signatures
-keepattributes Signature
-keepattributes EnclosingMethod
-keepattributes SourceFile,LineNumberTable

# Crash-friendly: keep enum values used in serialization
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
