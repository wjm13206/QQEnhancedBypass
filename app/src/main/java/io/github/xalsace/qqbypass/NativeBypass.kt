package io.github.xalsace.qqbypass

/**
 * JNI bridge for native hooks
 *
 * This class loads the native library and provides interface
 * to initialize and check native hook status.
 */
object NativeBypass {

    private var libraryLoaded = false

    init {
        try {
            // 先初始化 ByteHook 的 AAR（会加载 libbytehook.so）
            try {
                com.bytedance.android.bytehook.ByteHook.init()
                XposedEntry.log("ByteHook AAR initialized")
            } catch (t: Throwable) {
                XposedEntry.logWarn("ByteHook.init() failed, native hooks may not work: ${t.message}")
            }

            System.loadLibrary("native-bypass")
            libraryLoaded = true
            XposedEntry.log("Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            XposedEntry.logError("Failed to load native library", e)
            libraryLoaded = false
        }
    }

    /**
     * Check if native library is loaded
     */
    fun isLibraryLoaded(): Boolean = libraryLoaded

    /**
     * Initialize native hooks
     * This should be called early in QQ's lifecycle
     */
    fun initNativeHooks() {
        if (!libraryLoaded) {
            XposedEntry.logError(
                "Native library not loaded, cannot init hooks",
                IllegalStateException("native-bypass not loaded")
            )
            return
        }

        try {
            initNativeHooksNative()
            XposedEntry.log("Native hooks initialization started")
        } catch (t: Throwable) {
            XposedEntry.logError("Failed to init native hooks", t)
        }
    }

    /**
     * Check if native hooks are installed
     */
    fun isHooksInstalled(): Boolean {
        if (!libraryLoaded) {
            return false
        }

        return try {
            isHooksInstalledNative()
        } catch (t: Throwable) {
            XposedEntry.logError("Failed to check hook status", t)
            false
        }
    }

    /**
     * Get hook status message
     */
    fun getHookStatus(): String {
        if (!libraryLoaded) {
            return "Native library not loaded"
        }

        return try {
            getHookStatusNative()
        } catch (t: Throwable) {
            "Error getting hook status: ${t.message}"
        }
    }

    /**
     * Notify Native layer about Java detection event
     * This enables Java → Native communication
     */
    fun notifyDetection(event: String, data: String) {
        if (!libraryLoaded) {
            return
        }

        try {
            notifyDetectionNative(event, data)
        } catch (t: Throwable) {
            XposedEntry.logWarn("Failed to notify native layer: ${t.message}")
        }
    }

    /**
     * Check if specific Native hook is active
     */
    fun isHookActive(hookName: String): Boolean {
        if (!libraryLoaded) {
            return false
        }

        return try {
            isHookActiveNative(hookName)
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * Get detailed hook statistics
     */
    fun getHookStatistics(): String {
        if (!libraryLoaded) {
            return "Native layer unavailable"
        }

        return try {
            getHookStatisticsNative()
        } catch (t: Throwable) {
            "Error: ${t.message}"
        }
    }

    // Native methods (kept static so existing JNI symbols still match)
    @JvmStatic
    private external fun initNativeHooksNative()

    @JvmStatic
    private external fun isHooksInstalledNative(): Boolean

    @JvmStatic
    private external fun getHookStatusNative(): String

    @JvmStatic
    private external fun notifyDetectionNative(event: String, data: String)

    @JvmStatic
    private external fun isHookActiveNative(hookName: String): Boolean

    @JvmStatic
    private external fun getHookStatisticsNative(): String
}
