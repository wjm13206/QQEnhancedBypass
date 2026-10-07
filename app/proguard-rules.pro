# Keep Xposed entry point
-keep class io.github.xalsace.qqbypass.XposedEntry {
    public *;
}

# Keep all hook modules
-keep class io.github.xalsace.qqbypass.hook.** {
    public *;
}

# Keep JNI bridge: C++ 按类名+方法名查找（Java_io_github_xalsace_qqbypass_NativeBypass_*），
# AGP 9.1 起 R8 默认 repackaging 会移动包名，必须保留
-keep class io.github.xalsace.qqbypass.NativeBypass {
    *;
}

# libxposed API 102 官方混淆规则：保留模块入口并重写 java_init.list
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
public <init>();
}
