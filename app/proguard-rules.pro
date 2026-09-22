# ==============================================================================
# KuoteX Messenger ProGuard & R8 Optimization and Security Rules
# ==============================================================================

# Preserve line numbers and source files for clean crash traces
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# ------------------------------------------------------------------------------
# 1. Koin Dependency Injection
# ------------------------------------------------------------------------------
-keep class org.koin.** { *; }
-keep interface org.koin.** { *; }
-keepclassmembers class * {
    @org.koin.core.annotation.* *;
}
-keep class * extends org.koin.core.module.Module { *; }
-dontwarn org.koin.**

# ------------------------------------------------------------------------------
# 2. Firebase & Google Identity Services
# ------------------------------------------------------------------------------
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class com.google.android.libraries.identity.** { *; }
-keep class androidx.credentials.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firebase & App Models (Prevent R8 stripping getters/setters and fields)
-keep class com.example.data.** { *; }
-keep class com.example.auth.** { *; }
-keep class com.example.ui.UserAccount { *; }
-keep class com.example.ui.Chat { *; }
-keep class com.example.ui.Message { *; }
-keep class com.example.ui.Contact { *; }
-keep class com.example.ui.Draft { *; }
-keep class com.example.ui.GroupMember { *; }
-keep class com.example.data.RegisteredUserRole { *; }
-keep class com.example.data.CustomBotEntity { *; }

# ------------------------------------------------------------------------------
# 3. Security, Encryption & Database (SQLCipher, Tink, Signal Protocol)
# ------------------------------------------------------------------------------
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**

-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

-keep class androidx.security.crypto.** { *; }
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.**

-keep class com.example.crypto.** { *; }
-keep class org.whispersystems.** { *; }
-dontwarn org.whispersystems.**

# ------------------------------------------------------------------------------
# 4. WebRTC, Media3 & Real-Time Telephony
# ------------------------------------------------------------------------------
-keep class org.webrtc.** { *; }
-keep class com.example.webrtc.** { *; }
-dontwarn org.webrtc.**

-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ------------------------------------------------------------------------------
# 5. Networking & Serialization (OkHttp, Retrofit, Moshi, Coroutines)
# ------------------------------------------------------------------------------
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class retrofit2.** { *; }

-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ------------------------------------------------------------------------------
# 6. LeakCanary (Debug Build Isolation)
# ------------------------------------------------------------------------------
-dontwarn com.squareup.leakcanary.**
-keep class com.squareup.leakcanary.** { *; }

