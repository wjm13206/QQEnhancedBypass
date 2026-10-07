package io.github.xalsace.qqbypass.hook

import android.content.pm.PackageManager
import io.github.libxposed.api.XposedInterface
import io.github.xalsace.qqbypass.config.HookConfig
import io.github.xalsace.qqbypass.detector.DexKitDetector
import io.github.xalsace.qqbypass.utils.HookUtils
import java.io.File

/**
 * Dynamic root detection bypass using DexKit (v3 API).
 *
 * Covers:
 * - File.exists() on su paths (universal)
 * - DexKit-located su path checkers, Runtime.exec / ProcessBuilder callers,
 *   package-query methods
 */
object DynamicRootDetectionHook {

    fun hook() {
        val loader = HookConfig.classLoader ?: run {
            HookUtils.log("DynamicRootDetectionHook: no classloader")
            return
        }

        // Universal safety net: File.exists() on su paths
        try {
            HookUtils.hookMethod(File::class.java, "exists", { chain ->
                val file = chain.thisObject as? File
                if (file != null && isSuPath(file.absolutePath)) {
                    HookUtils.log("Blocked root check: File.exists(" + file.absolutePath + ")")
                    notifyNative("root_check", file.absolutePath)
                    false
                } else {
                    chain.proceed()
                }
            })
        } catch (t: Throwable) {
            HookUtils.log("DynamicRootDetectionHook: File.exists hook failed: " + t.message)
        }

        // DexKit-located detectors
        Thread({
            try {
                if (!DexKitDetector.awaitReady(60000)) {
                    HookUtils.log("DexKit not ready, skipping dynamic root hooks")
                    return@Thread
                }
                hookDexKitTargets(loader)
            } catch (e: Exception) {
                HookUtils.log("Dynamic root hook thread failed: " + e.message)
            }
        }, "DynamicRootHook").start()
    }

    private fun hookDexKitTargets(loader: ClassLoader) {
        // 1. su path checkers: force File.exists() -> false
        try {
            for (methodData in DexKitDetector.findSuPathCheckers()) {
                try {
                    val method = methodData.getMethodInstance(loader)
                    HookUtils.hookMember(method, { chain ->
                        val result = chain.proceed()
                        if (result is Boolean && result) {
                            HookUtils.log("Blocked su path check: " + methodData.descriptor)
                            notifyNative("root_check", methodData.descriptor)
                            false
                        } else {
                            result
                        }
                    })
                } catch (t: Throwable) {
                    if (HookConfig.VERBOSE_LOGGING) {
                        HookUtils.log("Skip su checker: " + methodData.descriptor)
                    }
                }
            }
        } catch (t: Throwable) {
            HookUtils.log("su checker hook failed: " + t.message)
        }

        // 2. Runtime.exec callers: log
        hookExecCallers(loader)

        // 3. ProcessBuilder callers: log
        try {
            for (methodData in DexKitDetector.findProcessBuilderCallers()) {
                val className = methodData.className
                try {
                    val method = methodData.getMethodInstance(loader)
                    HookUtils.hookMember(method, { chain ->
                        val result = chain.proceed()
                        HookUtils.log("ProcessBuilder caller invoked: $className")
                        result
                    })
                } catch (t: Throwable) {
                    if (HookConfig.VERBOSE_LOGGING) HookUtils.log("Skip ProcessBuilder caller: $className")
                }
            }
        } catch (t: Throwable) {
            HookUtils.log("ProcessBuilder hook failed: " + t.message)
        }

        // 4. Package-query methods for Magisk/LSPosed: throw NameNotFoundException.
        // NOTE: ExceptionMode.PASSTHROUGH is required here - under the default
        // PROTECTIVE mode a hooker exception is swallowed and execution continues.
        try {
            val hookTargets = ArrayList(DexKitDetector.findSuspiciousPackageQueries())
            for (methodData in hookTargets) {
                try {
                    val method = methodData.getMethodInstance(loader)
                    HookUtils.hookMember(method, { chain ->
                        val args = chain.args
                        if (args.isNotEmpty() && args[0] is String &&
                            isSuspiciousPackage(args[0] as String)
                        ) {
                            HookUtils.log("Blocked package query: " + args[0])
                            notifyNative("package_query", args[0] as String)
                            throw PackageManager.NameNotFoundException(args[0] as String)
                        }
                        chain.proceed()
                    }, XposedInterface.ExceptionMode.PASSTHROUGH)
                } catch (t: Throwable) {
                    if (HookConfig.VERBOSE_LOGGING) {
                        HookUtils.log("Skip package query: " + methodData.descriptor)
                    }
                }
            }
        } catch (t: Throwable) {
            HookUtils.log("Package query hook failed: " + t.message)
        }
    }

    private fun hookExecCallers(loader: ClassLoader) {
        try {
            for (methodData in DexKitDetector.findRuntimeExecCallers()) {
                val className = methodData.className
                try {
                    val method = methodData.getMethodInstance(loader)
                    HookUtils.hookMember(method, { chain ->
                        val result = chain.proceed()
                        HookUtils.log("Runtime.exec caller invoked: $className")
                        result
                    })
                } catch (t: Throwable) {
                    if (HookConfig.VERBOSE_LOGGING) HookUtils.log("Skip exec caller: $className")
                }
            }
        } catch (t: Throwable) {
            HookUtils.log("Runtime.exec hook failed: " + t.message)
        }
    }

    // ---- helpers ----

    private fun isSuPath(path: String?): Boolean {
        if (path == null) return false
        val lower = path.lowercase()
        if (lower.contains("magisk") || lower.contains("xposed") || lower.contains("lsposed") ||
            lower.contains("riru") || lower.contains("zygisk") || lower.contains("substrate") ||
            lower.contains("supersu") || lower.contains("superuser")
        ) {
            return true
        }
        return lower == "/system/bin/su" || lower == "/system/xbin/su" ||
                lower == "/sbin/su" || lower == "/su/bin/su" ||
                lower == "/system/sd/xbin/su" || lower == "/system/bin/failsafe/su" ||
                lower == "/data/local/su" || lower == "/data/local/xbin/su" ||
                lower == "/data/local/bin/su" || lower == "/system/app/Superuser.apk" ||
                lower == "/data/adb/magisk" || lower.endsWith("/su") || lower.endsWith("/busybox")
    }

    private fun isSuspiciousPackage(packageName: String?): Boolean {
        if (packageName == null) return false
        val lower = packageName.lowercase()
        return lower.contains("magisk") || lower.contains("xposed") || lower.contains("lsposed") ||
                lower.contains("edxposed") || lower.contains("taichi") || lower.contains("riru") ||
                lower.contains("zygisk") || lower.contains("supersu") || lower.contains("superuser") ||
                lower.contains("topjohnwu") || lower.contains("substrate") || lower.contains("frida") ||
                lower == "com.topjohnwu.magisk" || lower == "org.meowcat.edxposed.manager" ||
                lower == "org.lsposed.manager" || lower == "de.robv.android.xposed.installer"
    }

    private fun notifyNative(event: String, data: String) {
        try {
            io.github.xalsace.qqbypass.NativeBypass.notifyDetection(event, data)
        } catch (t: Throwable) {
            // Native not loaded, ignore
        }
    }
}
