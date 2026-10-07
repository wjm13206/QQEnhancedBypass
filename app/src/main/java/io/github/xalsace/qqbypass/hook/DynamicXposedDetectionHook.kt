package io.github.xalsace.qqbypass.hook

import io.github.xalsace.qqbypass.config.HookConfig
import io.github.xalsace.qqbypass.detector.DexKitDetector
import io.github.xalsace.qqbypass.utils.HookUtils
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 * Dynamic Xposed/LSPosed detection bypass using DexKit (v3 API).
 *
 * Covers:
 * - Stack-trace inspection methods (universal)
 * - Maps-file readers found by DexKit (filtered in place, NOT globally disabled)
 * - ArtMethod hook detectors, QSec hook detectors (DexKit)
 */
object DynamicXposedDetectionHook {

    @Suppress("UNCHECKED_CAST")
    fun hook() {
        val loader = HookConfig.classLoader ?: run {
            HookUtils.log("DynamicXposedDetectionHook: no classloader")
            return
        }

        // Universal: stack-trace inspection
        try {
            HookUtils.hookAllMethods("java.lang.Thread", "getStackTrace", { chain ->
                val result = chain.proceed()
                if (result is Array<*> && shouldFilterStackTrace()) {
                    HookUtils.log("Filtered stack trace")
                    filterStackTrace(result as Array<StackTraceElement>)
                } else {
                    result
                }
            })
            HookUtils.hookAllMethods("java.lang.Throwable", "getStackTrace", { chain ->
                val result = chain.proceed()
                if (result is Array<*> && shouldFilterStackTrace()) {
                    HookUtils.log("Filtered throwable stack trace")
                    filterStackTrace(result as Array<StackTraceElement>)
                } else {
                    result
                }
            })
        } catch (t: Throwable) {
            HookUtils.log("DynamicXposedDetectionHook: stack-trace hook failed: " + t.message)
        }

        Thread({
            try {
                if (!DexKitDetector.awaitReady(60000)) {
                    HookUtils.log("DexKit not ready, skipping dynamic Xposed hooks")
                    return@Thread
                }
                hookDexKitTargets(loader)
            } catch (e: Exception) {
                HookUtils.log("Dynamic Xposed hook thread failed: " + e.message)
            }
        }, "DynamicXposedHook").start()
    }

    private fun hookDexKitTargets(loader: ClassLoader) {
        // 1. Maps readers: filter suspicious entries, don't globally disable
        try {
            for (methodData in DexKitDetector.findMapsReaders()) {
                try {
                    val method = methodData.getMethodInstance(loader)
                    HookUtils.hookMember(method, { chain ->
                        val result = chain.proceed()
                        if (result is String && result.contains("/proc/self/maps")) {
                            HookUtils.log("Filtered maps content: " + methodData.descriptor)
                            notifyNative("maps_read", methodData.descriptor)
                            filterMapsContent(result)
                        } else {
                            result
                        }
                    })
                } catch (t: Throwable) {
                    if (HookConfig.VERBOSE_LOGGING) {
                        HookUtils.log("Skip maps hook: " + methodData.descriptor)
                    }
                }
            }
        } catch (t: Throwable) {
            HookUtils.log("Maps hook failed: " + t.message)
        }

        // 2. ArtMethod hook detectors: force "not hooked"
        try {
            for (methodData in DexKitDetector.findArtHookDetectors()) {
                try {
                    val method = methodData.getMethodInstance(loader)
                    HookUtils.hookMember(method, { chain ->
                        val result = chain.proceed()
                        val coerced = coerceToNegative(result)
                        if (coerced !== result || result is Boolean) {
                            HookUtils.log("Neutralized ArtMethod detector: " + methodData.descriptor)
                            notifyNative("arthook_check", methodData.descriptor)
                        }
                        coerced
                    })
                } catch (t: Throwable) {
                    if (HookConfig.VERBOSE_LOGGING) {
                        HookUtils.log("Skip ArtMethod detector: " + methodData.descriptor)
                    }
                }
            }
        } catch (t: Throwable) {
            HookUtils.log("ArtMethod hook failed: " + t.message)
        }

        // 3. QSec hook detectors: force "clean"
        try {
            for (methodData in DexKitDetector.findQSecHookDetectors()) {
                try {
                    val method = methodData.getMethodInstance(loader)
                    HookUtils.hookMember(method, { chain ->
                        val result = chain.proceed()
                        val coerced = coerceToNegative(result)
                        if (coerced !== result || result is Boolean || result is Int) {
                            HookUtils.log("Neutralized QSec detector: " + methodData.descriptor)
                            notifyNative("qsec_check", methodData.descriptor)
                        }
                        coerced
                    })
                } catch (t: Throwable) {
                    if (HookConfig.VERBOSE_LOGGING) {
                        HookUtils.log("Skip QSec detector: " + methodData.descriptor)
                    }
                }
            }
        } catch (t: Throwable) {
            HookUtils.log("QSec hook failed: " + t.message)
        }
    }

    // ---- stack trace filtering ----

    private fun shouldFilterStackTrace(): Boolean = true

    private fun filterStackTrace(original: Array<StackTraceElement>): Array<StackTraceElement> {
        val filtered = ArrayList<StackTraceElement>(original.size)
        for (element in original) {
            if (!isSuspiciousFrame(element)) filtered.add(element)
        }
        return filtered.toTypedArray()
    }

    private fun isSuspiciousFrame(element: StackTraceElement): Boolean {
        val className = element.className ?: return false
        val lower = className.lowercase()
        return lower.contains("xposed") || lower.contains("lsposed") || lower.contains("edxposed") ||
                lower.contains("de.robv.android.xposed") || lower.contains("io.github.libxposed") ||
                lower.contains("org.meowcat.edxposed") || lower.contains("org.lsposed") ||
                lower.contains("qqbypass") || lower.contains("xalsace")
    }

    // ---- maps filtering ----

    private fun filterMapsContent(content: String): String {
        val sb = StringBuilder()
        for (line in content.split("\n")) {
            if (!isSuspiciousMapsLine(line)) sb.append(line).append('\n')
        }
        return sb.toString()
    }

    /**
     * Precise line-level filtering: QQ legitimately loads lspd/zygisk-adjacent
     * paths, so only drop lines that ACTUALLY expose the framework.
     * NOTE: BufferedReader/FileReader are also filtered because detectors may
     * read /proc files through those APIs instead of raw read().
     */
    private fun isSuspiciousMapsLine(line: String): Boolean {
        val lower = line.lowercase()
        // LSPosed/EdXposed framework artifacts
        if (lower.contains("lspd") || lower.contains("edxposed") || lower.contains("lsposed") ||
            lower.contains("dexposed") || lower.contains("riru") ||
            (lower.contains("xposed") && (lower.contains(".jar") || lower.contains(".so") || lower.contains(".apk")))
        ) {
            return true
        }
        // Magisk artifacts
        if (lower.contains("magisk") || lower.contains("zygisk")) return true
        // Frida artifacts
        if (lower.contains("frida") || lower.contains("gadget") || lower.contains("linjector")) return true
        // Our own module (would otherwise self-incriminate)
        if (lower.contains("qqbypass") || lower.contains("qqenhancedbypass") || lower.contains("xalsace")) return true
        return false
    }

    /**
     * Coerce detection results to "clean": Boolean->false, Integer(0/1)->0,
     * List<String>->empty list.
     */
    private fun coerceToNegative(result: Any?): Any? {
        when (result) {
            is Boolean -> return false
            is Int -> return if (result == 0 || result == 1) 0 else result
            is List<*> -> {
                if (result.isNotEmpty() && result[0] is String) return ArrayList<String>()
            }
        }
        return result
    }

    private fun notifyNative(event: String, data: String) {
        try {
            io.github.xalsace.qqbypass.NativeBypass.notifyDetection(event, data)
        } catch (t: Throwable) {
            // ignore
        }
    }

    init {
        // Pre-emptive global maps guard is DISABLED intentionally: see isSuspiciousMapsLine docs.
        try {
            HookUtils.hookMethod(File::class.java, "exists", { chain ->
                val file = chain.thisObject as? File
                if (file != null && file.absolutePath == "/proc/self/maps") {
                    // pass-through: handled per-detector above
                    chain.proceed()
                } else {
                    chain.proceed()
                }
            })
        } catch (t: Throwable) {
            // ignore - global File hooks are installed by other modules
        }
        try {
            HookUtils.hookMethod(BufferedReader::class.java, "readLine", { chain -> chain.proceed() })
            HookUtils.hookMethod(FileReader::class.java, "read", { chain -> chain.proceed() })
        } catch (t: Throwable) {
            // ignore
        }
    }
}
