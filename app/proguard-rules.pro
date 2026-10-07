# Keep Xposed entry point
-keep class io.github.xalsace.qqbypass.XposedEntry {
    public *;
}

# Keep all hook modules
-keep class io.github.xalsace.qqbypass.hook.** {
    public *;
}

# libxposed API 102 官方混淆规则：保留模块入口并重写 java_init.list
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
public <init>();
}
