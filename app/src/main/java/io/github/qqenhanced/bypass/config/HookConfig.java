package io.github.qqenhanced.bypass.config;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Central configuration for hook modules
 */
public class HookConfig {

    public static XC_LoadPackage.LoadPackageParam loadPackageParam;
    public static ClassLoader classLoader;

    // Feature toggles
    public static boolean ENABLE_NATIVE_HOOKS = true;        // Native layer hooks (70% coverage)
    public static boolean ENABLE_ROOT_BYPASS = true;
    public static boolean ENABLE_XPOSED_BYPASS = true;
    public static boolean ENABLE_DEBUG_BYPASS = true;
    public static boolean ENABLE_DEVICE_SPOOF = true;
    public static boolean ENABLE_NETWORK_INTERCEPT = true;
    public static boolean ENABLE_RUNTIME_BYPASS = true;

    // Verbose logging
    public static boolean VERBOSE_LOGGING = true;

    // Fake device info (used when spoofing is enabled)
    public static String FAKE_IMEI = null; // null = use real device info
    public static String FAKE_ANDROID_ID = null;
    public static String FAKE_SERIAL = null;

    public static void init(XC_LoadPackage.LoadPackageParam lpparam) {
        loadPackageParam = lpparam;
        classLoader = lpparam.classLoader;
    }

    public static ClassLoader getClassLoader() {
        return classLoader;
    }
}
