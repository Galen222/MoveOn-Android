# ------------------------------------------------------------
# Reglas R8 / ProGuard para MoveOn
# ------------------------------------------------------------

-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

-keep @androidx.annotation.Keep class * { *; }
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <fields>;
    @androidx.annotation.Keep <methods>;
}

-keep interface com.proyecto.moveon.data.remote.retrofit.** { *; }
-keepclasseswithmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Entity class * { *; }

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
