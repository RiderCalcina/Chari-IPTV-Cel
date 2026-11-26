# Reglas ProGuard / R8 para Chari-IPTV_Cel (Compose + Media3 + FFmpeg + Hilt + Room + OkHttp + Cast)

# Media3 / ExoPlayer y extensiones cargadas por reflexión (Jellyfin FFmpeg Decoder)
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keep class org.jellyfin.media3.** { *; }
-keepclassmembers class * extends androidx.media3.exoplayer.BaseRenderer {
    public <init>(...);
}
-keepclassmembers class * implements androidx.media3.datasource.DataSource$Factory {
    public <init>(...);
}
-keepclassmembers class * extends androidx.media3.exoplayer.source.MediaSource$Factory {
    public <init>(...);
}
-dontwarn androidx.media3.**
-dontwarn org.jellyfin.media3.**

# OkHttp / Okio / Conscrypt / BouncyCastle opcionales
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**

# Coil SVG
-keep class com.caverock.androidsvg.** { *; }

# Room Entities y DAOs
-keep class com.example.chariiptv.data.local.** { *; }

# Modelos de Dominio y Estado
-keep class com.example.chariiptv.domain.** { *; }

# Inyección Dagger / Hilt y Calificadores
-keep class com.example.chariiptv.di.** { *; }

# Google Cast OptionsProvider
-keep class com.example.chariiptv.cast.** { *; }

# Optimización de rendimiento para producción: eliminar logs de depuración (Log.v / Log.d)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
