# ==============================================================================
# Cinematic Camera App - ProGuard / R8 Optimization Rules
# ==============================================================================

# 1. Preserve C++ JNI Native Bridge classes and native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.cinematic.camera.engine.** { *; }
-keepclassmembers class com.cinematic.camera.engine.DollyZoomNativeBridge {
    private native <methods>;
    public <methods>;
}

-keepclassmembers class com.cinematic.camera.engine.RackFocusNativeBridge {
    private native <methods>;
    public <methods>;
}

# 2. Preserve UI Take records and Enums from obfuscation
-keep class com.cinematic.camera.ui.CinematicTake { *; }
-keep class com.cinematic.camera.ui.AppShotState { *; }
-keep class com.cinematic.camera.ui.FocusPullMode { *; }
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 3. CameraX & Camera2 Interop
-keep class androidx.camera.camera2.interop.** { *; }
-dontwarn androidx.camera.camera2.interop.**

# 4. Media Metadata & Codecs
-keepclassmembers class android.media.MediaMetadataRetriever { *; }
-keepclassmembers class android.media.MediaPlayer { *; }
