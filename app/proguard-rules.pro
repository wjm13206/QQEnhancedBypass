# Keep Xposed entry point
-keep class io.github.qqenhanced.bypass.XposedEntry {
    public *;
}

# Keep all hook modules
-keep class io.github.qqenhanced.bypass.hooks.** {
    public *;
}

# Keep methods called via reflection
-keepclassmembers class * {
    @io.github.qqenhanced.bypass.annotation.KeepForHook *;
}

# Don't warn about Xposed API
-dontwarn de.robv.android.xposed.**
