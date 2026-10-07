package io.github.xalsace.qqbypass.hook

import android.os.Debug
import io.github.xalsace.qqbypass.config.HookConfig
import io.github.xalsace.qqbypass.utils.HookUtils

/**
 * Dynamic debug/hook-framework detection bypass.
 *
 * Covers:
 * - Debug.isDebuggerConnected / waitingForDebugger (universal, force false)
 * - DexKit-located debug checkers (force "not debugging")
 * - TracerPid checkers (force "not traced")
 */
object DynamicDebugDetectionHook {

    fun hook() {
        val loader = HookConfig.classLoader ?: run {
            HookUtils.log("DynamicDebugDetectionHook: no classloader")
            return
        }

        // Universal: Debug.isDebuggerConnected -> false
        try {
            HookUtils.hookMethod(Debug::class.java, "isDebuggerConnected", { _ -> false })
        } catch (t: Throwable) {
            HookUtils.log("DynamicDebugDetectionHook: isDebuggerConnected hook failed: " + t.message)
        }

        // Universal: Debug.waitingForDebugger -> block (return immediately)
        try {
            HookUtils.hookMethod(Debug::class.java, "waitingForDebugger", { _ -> null })
        } catch (t: Throwable) {
            if (HookConfig.VERBOSE_LOGGING) HookUtils.log("Skip Debug.waitingForDebugger")
        }

        // Universal: Debug.threadCpuTimeNanos etc. left alone (timing checks are Native-side)

        Thread({
            try {
                if (!io.github.xalsace.qqbypass.detector.DexKitDetector.awaitReady(60000)) {
                    HookUtils.log("DexKit not ready, skipping dynamic debug hooks")
                    return@Thread
                }
                hookDexKitTargets(loader)
            } catch (e: Exception) {
                HookUtils.log("Dynamic debug hook thread failed: " + e.message)
            }
        }, "DynamicDebugHook").start()
    }

    private fun hookDexKitTargets(loader: ClassLoader) {
        // TracerPid checkers report via strings; neutralize boolean/integer results
        try {
            for (methodData in io.github.xalsace.qqbypass.detector.DexKitDetector.findMapsReaders()) {
                val desc = methodData.descriptor
                if (!desc.contains("TracerPid") && !desc.contains("tracer")) continue
                try {
                    val method = methodData.getMethodInstance(loader)
                    HookUtils.hookMember(method, { chain ->
                        val result = chain.proceed()
                        when (result) {
                            is Boolean -> {
                                HookUtils.log("Neutralized TracerPid check: $desc")
                                notifyNative("debug_check", desc)
                                false
                            }
                            is Int -> {
                                HookUtils.log("Neutralized TracerPid check: $desc")
                                notifyNative("debug_check", desc)
                                0
                            }
                            else -> result
                        }
                    })
                } catch (t: Throwable) {
                    if (HookConfig.VERBOSE_LOGGING) HookUtils.log("Skip TracerPid checker: $desc")
                }
            }
        } catch (t: Throwable) {
            HookUtils.log("TracerPid hook failed: " + t.message)
        }
    }

    private fun notifyNative(event: String, data: String) {
        try {
            io.github.xalsace.qqbypass.NativeBypass.notifyDetection(event, data)
        } catch (t: Throwable) {
            // ignore
        }
    }
}
