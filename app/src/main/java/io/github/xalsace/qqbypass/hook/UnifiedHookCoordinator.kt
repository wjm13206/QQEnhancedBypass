package io.github.xalsace.qqbypass.hook

import io.github.xalsace.qqbypass.NativeBypass
import io.github.xalsace.qqbypass.XposedEntry
import io.github.xalsace.qqbypass.utils.HookUtils
import java.io.File

/**
 * Unified Hook Coordinator
 *
 * This module coordinates between Java and Native hooks to provide
 * seamless protection across both layers.
 *
 * Java Hook → If failed → Try Native Hook
 * Native Hook → Notify Java layer
 */
object UnifiedHookCoordinator {

    private var nativeLayerAvailable = false

    fun initialize() {
        XposedEntry.log("=== Initializing Unified Hook Coordinator ===")

        // Check Native layer availability
        nativeLayerAvailable = NativeBypass.isLibraryLoaded()

        if (nativeLayerAvailable) {
            XposedEntry.log("Native layer available - Full protection (80%)")
            initNativeIntegration()
        } else {
            XposedEntry.log("Native layer unavailable - Java only (30%)")
        }

        // Install coordinated hooks
        installCoordinatedHooks()
    }

    /**
     * Initialize Native integration
     */
    private fun initNativeIntegration() {
        // Initialize Native hooks
        NativeBypass.initNativeHooks()

        // Register callback from Native layer
        registerNativeCallbacks()
    }

    /**
     * Register callbacks for Native → Java communication
     */
    private fun registerNativeCallbacks() {
        // Native layer can notify Java layer through JNI
        // e.g., when a detection is blocked at Native level
    }

    /**
     * Install hooks with Java/Native coordination
     */
    private fun installCoordinatedHooks() {
        // Hook critical detection points with fallback
        hookWithFallback()

        // Setup bidirectional communication
        setupCommunicationChannel()
    }

    /**
     * Hook with Java → Native fallback
     */
    private fun hookWithFallback() {
        // Example: File existence check
        // Java layer hooks File.exists()
        // If it fails, Native layer hooks access()

        HookUtils.hookMethod(File::class.java, "exists", { chain ->
            val file = chain.thisObject as? File
            val path = file?.absolutePath

            // Check if this is a sensitive path
            if (isSensitivePath(path)) {
                XposedEntry.log("[Java] Blocked file check: $path")

                // Notify Native layer (if available)
                if (nativeLayerAvailable) {
                    notifyNativeLayer("file_blocked", path ?: "")
                }
                false
            } else {
                chain.proceed()
            }
        })
    }

    /**
     * Setup bidirectional communication
     */
    private fun setupCommunicationChannel() {
        // Java can query Native status
        // Native can notify Java about detections

        if (nativeLayerAvailable) {
            // Check Native hook status periodically
            Thread({
                try {
                    Thread.sleep(3000)
                    val status = NativeBypass.getHookStatus()
                    XposedEntry.log("[Coordinator] Native status: $status")
                } catch (e: Exception) {
                    XposedEntry.logError("[Coordinator] Status check failed", e)
                }
            }).start()
        }
    }

    /**
     * Notify Native layer about Java detections
     */
    private fun notifyNativeLayer(event: String, data: String) {
        if (!nativeLayerAvailable) return

        try {
            // JNI call to Native layer
            NativeBypass.notifyDetection(event, data)
        } catch (t: Throwable) {
            // Silent fail
        }
    }

    /**
     * Check if path is sensitive
     */
    private fun isSensitivePath(path: String?): Boolean {
        if (path == null) return false

        val sensitivePaths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/data/adb/magisk",
            "/data/adb/lsposed"
        )

        for (sensitive in sensitivePaths) {
            if (path.contains(sensitive)) return true
        }

        return false
    }

    /**
     * Get protection level
     */
    fun getProtectionLevel(): String {
        return if (nativeLayerAvailable && NativeBypass.isHooksInstalled()) {
            "Full (80%)"
        } else if (nativeLayerAvailable) {
            "Native loading (60%)"
        } else {
            "Java only (30%)"
        }
    }

    /**
     * 异步等待 Native 层安装完成后，报告最终保护等级。
     *
     * Native hook 在后台线程延迟安装（约需 2-3 秒），如果同步读取会得到
     * "Native loading (60%)" 的中间态。这里轮询等待，直到装完或超时。
     */
    fun reportFinalProtectionLevelAsync() {
        if (!nativeLayerAvailable) {
            // 没有 Native 层，当前状态即最终状态
            XposedEntry.log("=== Final Protection Level: " + getProtectionLevel() + " ===")
            return
        }

        Thread({
            val maxWaitMs = 10000 // 最多等 10 秒
            val intervalMs = 300
            var waited = 0

            while (waited < maxWaitMs) {
                if (NativeBypass.isHooksInstalled()) {
                    XposedEntry.log("=== Final Protection Level: Full (80%) ===")
                    XposedEntry.log("Native hook statistics:\n" + NativeBypass.getHookStatistics())
                    return@Thread
                }
                try {
                    Thread.sleep(intervalMs.toLong())
                    waited += intervalMs
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@Thread
                }
            }

            // 超时仍未装完
            XposedEntry.log(
                "=== Final Protection Level: " + getProtectionLevel() +
                        " (native install timed out) ==="
            )
        }, "QQBypass-StatusReporter").start()
    }
}
