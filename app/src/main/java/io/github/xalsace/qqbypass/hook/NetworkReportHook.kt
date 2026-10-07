package io.github.xalsace.qqbypass.hook

import io.github.xalsace.qqbypass.XposedEntry
import io.github.xalsace.qqbypass.config.HookConfig
import io.github.xalsace.qqbypass.detector.DexKitDetector
import io.github.xalsace.qqbypass.utils.HookUtils
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.reflect.Method

/**
 * Network Report Interception Hook
 *
 * Intercepts and blocks security-related reporting to Tencent servers:
 * - trpc.o3.report.* (environment reports)
 * - trpc.o3.mobile_security.* (security checks)
 * - TuringFD risk detection
 * - Wlogin device fingerprint
 */
object NetworkReportHook {

    // Only pure telemetry/report commands. Login-critical commands
    // (wtlogin.*, turing, DeviceTokenV3) are intentionally NOT blocked -
    // blocking them breaks the QQ login/registration handshake and prevents
    // entry to the main UI.
    private val BLOCK_COMMANDS = arrayOf(
        "trpc.o3.report",
        "trpc.o3.mobile_security",
        "trpc.ilive_cdn.report",
        "OidbSvc.0xd79" // Device report
    )

    fun hook() {
        if (!HookConfig.ENABLE_NETWORK_INTERCEPT) return
        val loader = HookConfig.classLoader ?: run {
            HookUtils.log("NetworkReportHook: no classloader")
            return
        }

        XposedEntry.log("Initializing Network Report Hook")

        // Hook 1: ChannelProxyExt.sendMessage (primary intercept point)
        hookChannelProxyExt(loader)

        // Hook 2: MsfCore.sendSsoMsg (secondary intercept point)
        hookMsfCore(loader)

        // Hook 3: TuringFD risk detection
        hookTuringFD()

        // Hook 4: Wlogin device report
        hookWlogin()
    }

    private fun hookChannelProxyExt(loader: ClassLoader) {
        // Try multiple possible method signatures for different QQ versions
        val clazz = HookUtils.findClassOrNull("com.tencent.mobileqq.channel.ChannelProxyExt", loader)
            ?: run {
                if (HookConfig.VERBOSE_LOGGING) XposedEntry.log("ChannelProxyExt not found, skip")
                return
            }
        for (methodName in arrayOf("sendMessage", "sendMessageInner", "send")) {
            for (m in clazz.declaredMethods) {
                if (m.name != methodName) continue
                val returnType = m.returnType
                if (returnType.isPrimitive && returnType != Void.TYPE) {
                    // Primitive return - blocking is unsafe, observe only.
                    // (Old code made this decision at runtime via param.method;
                    // here it is decided once at install time.)
                    val rtName = returnType.name
                    HookUtils.hookMember(m, { chain ->
                        val result = chain.proceed()
                        val cmd = chain.args.firstOrNull() as? String
                        if (cmd != null && shouldBlock(cmd)) {
                            XposedEntry.log("ChannelProxyExt (not blocked, primitive return $rtName): $cmd")
                        }
                        result
                    })
                } else {
                    HookUtils.hookMember(m, { chain ->
                        val args = chain.args
                        if (args.isNotEmpty() && args[0] is String &&
                            shouldBlock(args[0] as String)
                        ) {
                            val command = args[0] as String
                            XposedEntry.log("Blocked ChannelProxyExt: $command")
                            injectFakeResponse(loader, command)
                            null
                        } else {
                            chain.proceed()
                        }
                    })
                }
            }
        }
    }

    private fun injectFakeResponse(loader: ClassLoader, command: String) {
        try {
            val cmCls = Class.forName("com.tencent.mobileqq.channel.ChannelManager", false, loader)
            val channelManager = cmCls.getMethod("getInstance").invoke(null) ?: return
            channelManager.javaClass.getMethod(
                "onNativeReceive",
                String::class.java,
                ByteArray::class.java,
                java.lang.Long.TYPE
            ).invoke(channelManager, command, ByteArray(0), 0L)
        } catch (t: Throwable) {
            // Ignore if injection fails
        }
    }

    private fun hookMsfCore(loader: ClassLoader) {
        // Real path (QQ 9.3.50): com.tencent.mobileqq.msf.core.MsfCore
        val clazz = HookUtils.findClassOrNull("com.tencent.mobileqq.msf.core.MsfCore", loader)
            ?: run {
                if (HookConfig.VERBOSE_LOGGING) XposedEntry.log("MsfCore not found, skip")
                return
            }
        for (m in clazz.declaredMethods) {
            if (m.name != "sendSsoMsg") continue
            // Type-safe block decided at install time by return type. Setting a
            // wrong-typed result triggers an uncatchable ClassCastException in
            // the framework's proceed() and crashes the process.
            when (m.returnType) {
                Integer.TYPE -> HookUtils.hookMember(m, { chain ->
                    val args = chain.args
                    if (args.isEmpty()) return@hookMember chain.proceed()
                    val command = getServiceCmd(args[0])
                    if (shouldBlock(command)) {
                        var seq = 0
                        try {
                            seq = (args[0].javaClass.getMethod("getRequestSsoSeq")
                                .invoke(args[0]) as? Number)?.toInt() ?: 0
                        } catch (ignored: Throwable) {
                        }
                        XposedEntry.log("Blocked MsfCore: $command")
                        seq
                    } else {
                        chain.proceed()
                    }
                })
                java.lang.Long.TYPE -> HookUtils.hookMember(m, { chain ->
                    val args = chain.args
                    if (args.isEmpty()) return@hookMember chain.proceed()
                    val command = getServiceCmd(args[0])
                    if (shouldBlock(command)) {
                        var seq = 0L
                        try {
                            seq = (args[0].javaClass.getMethod("getRequestSsoSeq")
                                .invoke(args[0]) as? Number)?.toLong() ?: 0L
                        } catch (ignored: Throwable) {
                        }
                        XposedEntry.log("Blocked MsfCore: $command")
                        seq
                    } else {
                        chain.proceed()
                    }
                })
                Void.TYPE -> HookUtils.hookMember(m, { chain ->
                    val args = chain.args
                    if (args.isEmpty()) return@hookMember chain.proceed()
                    val command = getServiceCmd(args[0])
                    if (shouldBlock(command)) {
                        XposedEntry.log("Blocked MsfCore: $command")
                        null
                    } else {
                        chain.proceed()
                    }
                })
                else -> {
                    val rtName = m.returnType.name
                    HookUtils.hookMember(m, { chain ->
                        val result = chain.proceed()
                        val args = chain.args
                        val command = if (args.isNotEmpty()) getServiceCmd(args[0]) else null
                        if (shouldBlock(command)) {
                            XposedEntry.log("MsfCore (not blocked, return type $rtName): $command")
                        }
                        result
                    })
                }
            }
        }
    }

    private fun getServiceCmd(toServiceMsg: Any?): String? {
        if (toServiceMsg == null) return null
        return try {
            val cmdObj = toServiceMsg.javaClass.getMethod("getServiceCmd").invoke(toServiceMsg)
            cmdObj?.toString()
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Block TuringFD's process-enumeration readers (anti-tamper /proc scanners).
     *
     * These live in ProGuard-obfuscated "fruit" classes (Pomegranate / oqKCa on
     * 9.3.50) whose names change every QQ version, so hardcoding them is fragile.
     * Instead we locate them by a stable fingerprint: the format string
     * "/proc/%d/cmdline" they use to read another process's name. Verified to
     * occur in only 2 dex (classes2 + classes18) on 9.3.50, so the match is tight.
     *
     * The old code hooked every method named "a" and setResult(null) on all of
     * them. That was wrong twice over: Blueberry.a doesn't exist (dead hook), and
     * oqKCa.a has an int-returning overload where setResult(null) throws an
     * uncatchable ClassCastException. Here we resolve the exact method via
     * getMethodInstance() and only neutralize String-returning readers (-> "").
     */
    private fun hookTuringFD() {
        val loader = HookConfig.classLoader ?: return
        Thread({
            try {
                // Wait for the single shared bridge; false in a subprocess (gated off).
                if (!DexKitDetector.awaitReady(30000)) {
                    XposedEntry.log("[TuringFD] DexKit not available, skip proc-reader hook")
                    return@Thread
                }
                val bridge = DexKitDetector.getBridge()
                if (bridge == null) {
                    XposedEntry.log("[TuringFD] shared bridge null, skip proc-reader hook")
                    return@Thread
                }

                val procReaders = bridge.findMethod(
                    FindMethod.create()
                        .matcher(
                            MethodMatcher.create()
                                .usingStrings("/proc/%d/cmdline")
                        )
                )
                XposedEntry.log("[TuringFD] found " + procReaders.size + " proc readers by fingerprint")

                for (md in procReaders) {
                    try {
                        val method = md.getMethodInstance(loader)
                        // Only neutralize String-returning readers; skip primitives
                        // (int helper overload) to avoid a ClassCastException crash.
                        if (method !is Method) continue
                        val ret = method.returnType
                        if (ret != String::class.java) {
                            XposedEntry.log(
                                "[TuringFD] skip non-String reader " + md.className + "." + md.methodName +
                                        " (returns " + ret.name + ")"
                            )
                            continue
                        }
                        HookUtils.hookMember(method, {
                            // Empty name = "process not readable"; callers isEmpty-check this.
                            ""
                        })
                        XposedEntry.log(
                            "[TuringFD] neutralized proc reader: " + md.className + "." + md.methodName
                        )
                    } catch (t: Throwable) {
                        XposedEntry.log(
                            "[TuringFD] failed to hook proc reader: " +
                                    t.javaClass.simpleName + ": " + t.message
                        )
                    }
                }
            } catch (t: Throwable) {
                XposedEntry.log(
                    "[TuringFD] proc-reader lookup failed: " + t.javaClass.name + ": " + t.message
                )
            }
            // Shared bridge - do NOT close (owned by DexKitDetector)
        }).start()
    }

    private fun hookWlogin() {
        // Hook Wlogin device fingerprint collection (observe only)
        HookUtils.hookAllMethods("oicq.wlogin_sdk.request.w", "h", { chain ->
            val result = chain.proceed()
            // Modify device fingerprint before sending
            XposedEntry.log("Intercepted Wlogin device fingerprint")
            result
        })
    }

    private fun shouldBlock(command: String?): Boolean {
        if (command == null) return false
        for (blockCmd in BLOCK_COMMANDS) {
            if (command.contains(blockCmd)) return true
        }
        return false
    }
}
