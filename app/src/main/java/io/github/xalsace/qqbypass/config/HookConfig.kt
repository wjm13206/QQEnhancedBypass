package io.github.xalsace.qqbypass.config

/**
 * Central configuration for hook modules
 */
object HookConfig {

    var classLoader: ClassLoader? = null

    // Feature toggles
    var ENABLE_NATIVE_HOOKS = true        // Native layer hooks (70% coverage)
    var ENABLE_ROOT_BYPASS = true
    var ENABLE_XPOSED_BYPASS = true
    var ENABLE_DEBUG_BYPASS = true
    var ENABLE_DEVICE_SPOOF = true
    var ENABLE_NETWORK_INTERCEPT = true
    var ENABLE_RUNTIME_BYPASS = true
    var ENABLE_QQ950_PATCH = true        // QQ 9.3.50 detection-point completion
    var ENABLE_QQ950_RISK_BLOCK = true   // Tier 2: object-return risk blocks (NPE-caveat; disable if NPE appears)

    // Verbose logging
    var VERBOSE_LOGGING = true

    // Fake device info (used when spoofing is enabled)
    var FAKE_IMEI: String? = null // null = use real device info
    var FAKE_ANDROID_ID: String? = null
    var FAKE_SERIAL: String? = null

    fun init(loader: ClassLoader) {
        classLoader = loader
    }
}
