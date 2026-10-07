package io.github.xalsace.qqbypass.hook

import io.github.xalsace.qqbypass.config.HookConfig
import io.github.xalsace.qqbypass.detector.DexKitDetector
import io.github.xalsace.qqbypass.utils.HookUtils

/**
 * Dynamic runtime monitoring: watches Runtime.exec / ProcessBuilder usage
 * and neutralizes shell commands commonly used for root/framework probing.
 */
object DynamicRuntimeMonitorHook {

    fun hook() {
        val loader = HookConfig.classLoader ?: run {
            HookUtils.log("DynamicRuntimeMonitorHook: no classloader")
            return
        }

        // Universal: ProcessBuilder.start - sanitize suspicious commands
        try {
            HookUtils.hookAllMethods("java.lang.ProcessBuilder", "start", { chain ->
                val pb = chain.thisObject as? ProcessBuilder
                if (pb != null && isSuspiciousCommand(pb.command())) {
                    HookUtils.log("Blocked suspicious ProcessBuilder command: " + pb.command())
                    notifyNative("exec_block", pb.command().toString())
                    // Replace with a harmless no-op command, then proceed
                    pb.command("true")
                }
                chain.proceed()
            })
        } catch (t: Throwable) {
            HookUtils.log("DynamicRuntimeMonitorHook: ProcessBuilder hook failed: " + t.message)
        }

        // Universal: Runtime.exec - log + neutralize suspicious commands
        try {
            HookUtils.hookAllMethods("java.lang.Runtime", "exec", { chain ->
                val args = chain.args
                val cmd = args.firstOrNull()
                if (cmd != null && isSuspiciousCommand(cmd)) {
                    HookUtils.log("Blocked suspicious Runtime.exec: $cmd")
                    notifyNative("exec_block", cmd.toString())
                    throw java.io.IOException("Permission denied")
                }
                chain.proceed()
            }, io.github.libxposed.api.XposedInterface.ExceptionMode.PASSTHROUGH)
        } catch (t: Throwable) {
            HookUtils.log("DynamicRuntimeMonitorHook: Runtime.exec hook failed: " + t.message)
        }

        // DexKit-located exec callers: observe
        Thread({
            try {
                if (!DexKitDetector.awaitReady(60000)) return@Thread
                try {
                    for (methodData in DexKitDetector.findRuntimeExecCallers()) {
                        try {
                            val method = methodData.getMethodInstance(loader)
                            HookUtils.hookMember(method, { chain ->
                                val result = chain.proceed()
                                HookUtils.log("Runtime.exec caller: " + methodData.descriptor)
                                result
                            })
                        } catch (t: Throwable) {
                            if (HookConfig.VERBOSE_LOGGING) {
                                HookUtils.log("Skip exec caller: " + methodData.descriptor)
                            }
                        }
                    }
                } catch (t: Throwable) {
                    HookUtils.log("Exec caller hook failed: " + t.message)
                }
            } catch (e: Exception) {
                HookUtils.log("Dynamic runtime hook thread failed: " + e.message)
            }
        }, "DynamicRuntimeHook").start()
    }

    private fun isSuspiciousCommand(cmd: Any?): Boolean {
        if (cmd == null) return false
        val text = when (cmd) {
            is String -> cmd
            is Array<*> -> cmd.joinToString(" ")
            is List<*> -> cmd.joinToString(" ")
            else -> cmd.toString()
        }.lowercase()
        return text.contains(" su") || text == "su" || text.endsWith("/su") ||
                text.contains("magisk") || text.contains("which su") || text.contains("type su") ||
                text.contains("getprop") && text.contains("ro.debuggable") ||
                text.contains("/proc/self/maps") && text.contains("xposed")
    }

    private fun notifyNative(event: String, data: String) {
        try {
            io.github.xalsace.qqbypass.NativeBypass.notifyDetection(event, data)
        } catch (t: Throwable) {
            // ignore
        }
    }
}
