# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Moshi
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn sun.misc.**

# Retrofit / OkHttp
-dontwarn javax.annotation.**
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes Signature
-keepattributes Exceptions

# Data models & API responses
-keep class com.example.data.api.model.** { *; }
-keep class com.example.data.local.** { *; }

# Security Key Store and Dynamic Signer
-keep class com.example.data.api.security.** { *; }
-keepclassmembers class com.example.data.api.security.SecureKeyStore { *; }
-keepclassmembers class com.example.data.api.security.DeviceSecurityHelper { *; }

