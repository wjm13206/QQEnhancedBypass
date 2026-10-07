package io.github.xalsace.qqbypass.hook

import io.github.xalsace.qqbypass.XposedEntry
import io.github.xalsace.qqbypass.config.HookConfig
import io.github.xalsace.qqbypass.utils.HookUtils
import java.lang.reflect.Modifier

/**
 * QQ 9.3.50 detection/report completion hooks.
 *
 * Targets the real risk-control data sources that the legacy hooks missed
 * (the legacy onExec/process/sendSsoMsg targets either did not exist on 9.3.50
 * or were on the wrong path). Every class/method below was verified to exist by
 * directly parsing QQ 9.3.50's dex class_data (not trusting the written reports,
 * which contained several fabricated method names).
 *
 * Return types were also verified. Returning null is only used where the method
 * returns void (V) or an Object (never a primitive) to avoid the uncatchable
 * ClassCastException class of crash. Object-returning blocks carry an NPE caveat
 * (caller may deref null) and are grouped separately so they can be toggled.
 *
 * Everything is gated by HookConfig.ENABLE_QQ950_PATCH and class-exists checks
 * so a missing class can never crash module init.
 */
object QQ950PatchHook {

    fun hook() {
        if (!HookConfig.ENABLE_QQ950_PATCH) return
        val loader = HookConfig.classLoader ?: run {
            HookUtils.log("[QQ950] no classloader")
            return
        }

        XposedEntry.log("Initializing QQ 9.3.50 detection-point patch")

        // Tier 1: void-return, crash-safe backstops (kick + telemetry + switches)
        hookKickBackstop(loader)
        hookMsfTelemetry()
        hookTuringWrapper(loader)
        hookChannelReport()

        // Tier 2: object-return risk-detection blocks (type-safe, NPE-caveat)
        hookTuringRiskDetect()
        hookTuringDID()
    }

    // ==================== Tier 1: void-return, crash-safe ====================

    /**
     * Kick backstop: NTKickProcessor.b(...) : void is the method that actually
     * clears the login state on a server kick. Blocking it prevents the forced
     * logout. NOTE: if the server has already invalidated the session token this
     * may leave a non-functional session; kept as a backstop, observe behavior.
     */
    private fun hookKickBackstop(loader: ClassLoader) {
        val cls = "com.tencent.mobileqq.kick.NTKickProcessor"
        val clazz = HookUtils.findClassOrNull(cls, loader)
        if (clazz == null) {
            XposedEntry.log("[QQ950] NTKickProcessor not present, skip kick backstop")
            return
        }
        // The kick-clearing method is obfuscated ("b" on 9.3.50) and will be
        // renamed across versions. Locate it by a stable signature instead: a
        // void method whose parameters include a Constants$LogoutReason. The
        // public entry a(AppRuntime, KickedInfo) lacks that param, so this
        // matches only the internal logout worker.
        var hooked = 0
        for (m in clazz.declaredMethods) {
            if (m.returnType != Void.TYPE) continue
            var hasLogoutReason = false
            for (pt in m.parameterTypes) {
                if (pt.name.contains("LogoutReason")) {
                    hasLogoutReason = true
                    break
                }
            }
            if (!hasLogoutReason) continue
            HookUtils.hookMember(m, {
                XposedEntry.log("[QQ950] blocked kick worker (kick suppressed)")
                null // void - crash-safe
            })
            hooked++
            XposedEntry.log("[QQ950] hooked kick worker by signature: " + m.name)
        }
        if (hooked == 0) {
            XposedEntry.log("[QQ950] no kick worker matched signature (void + LogoutReason)")
        }
    }

    /**
     * MSF telemetry: all void, pure reporting. sendAllMsg is intentionally
     * EXCLUDED (name implies message flushing, blocking could break send/recv).
     */
    private fun hookMsfTelemetry() {
        val cls = "com.tencent.mobileqq.msf.core.MsfCore"
        if (!HookUtils.classExists(cls)) return
        val reportMethods = arrayOf(
            "doReportOnInitComplete", "reportMsfCoreInit",
            "tryReportJobAlive", "tryReportLoadCfgTempFile",
            "tryReportMSFAlive", "tryReportSoLoadUseTxlib"
        )
        for (m in reportMethods) {
            HookUtils.hookAllMethods(cls, m, {
                null // all void - crash-safe
            })
        }
    }

    /**
     * Turing switches: TuringWrapper.b()/c() : void. a() returns AtomicBoolean
     * (state), left untouched to avoid NPE.
     */
    private fun hookTuringWrapper(loader: ClassLoader) {
        val cls = "com.tencent.mobileqq.dt.model.TuringWrapper"
        val clazz = HookUtils.findClassOrNull(cls, loader) ?: return
        // b()/c() are the obfuscated turing-cache writers: both static, void, no
        // args. a() returns AtomicBoolean (state getter) and is left untouched.
        // Match by signature so a version rename of b/c still resolves them.
        var hooked = 0
        for (m in clazz.declaredMethods) {
            if (!Modifier.isStatic(m.modifiers)) continue
            if (m.returnType != Void.TYPE) continue
            if (m.parameterTypes.isNotEmpty()) continue
            HookUtils.hookMember(m, {
                null // void - crash-safe
            })
            hooked++
            XposedEntry.log("[QQ950] hooked TuringWrapper switch by signature: " + m.name)
        }
        if (hooked == 0) {
            XposedEntry.log("[QQ950] no TuringWrapper switch matched (static void no-arg)")
        }
    }

    /**
     * ChannelManager.checkMethod() : void - the Java-side entry that arms the
     * report channel (initReport is private native, cannot be hooked directly).
     */
    private fun hookChannelReport() {
        val cls = "com.tencent.mobileqq.channel.ChannelManager"
        if (!HookUtils.classExists(cls)) return
        HookUtils.hookAllMethods(cls, "checkMethod", {
            XposedEntry.log("[QQ950] blocked ChannelManager.checkMethod")
            null // void - crash-safe
        })
    }

    // ============ Tier 2: object-return risk detection (NPE-caveat) ============
    // These return Objects, so returning null is TYPE-safe (no ClassCastException),
    // but a caller that dereferences the null result without a guard could NPE.
    // Turing SDKs generally null-check risk responses defensively. Gated so they
    // can be disabled independently if an NPE appears in logs.

    /**
     * TuringRiskService.reqRiskDetectV2(...) : RiskDetectResp - the core risk
     * detection call whose result feeds the server's cheat/anomaly judgment.
     * Both the wxa and xq SDK variants, 3 overloads each.
     */
    private fun hookTuringRiskDetect() {
        if (!HookConfig.ENABLE_QQ950_RISK_BLOCK) return
        val classes = arrayOf(
            "com.tencent.tfd.sdk.wxa.TuringRiskService",
            "com.tencent.turingfd.sdk.xq.TuringRiskService"
        )
        for (cls in classes) {
            if (!HookUtils.classExists(cls)) continue
            HookUtils.hookAllMethods(cls, "reqRiskDetectV2", {
                XposedEntry.log("[QQ950] blocked reqRiskDetectV2 @ $cls")
                null // RiskDetectResp (Object) - type-safe
            })
        }
    }

    /**
     * TuringIDService.getTuringDID/getTuringDIDCached : ITuringDID (Object);
     * getTuringDIDAsync : void. The Turing device id is a fingerprint source.
     */
    private fun hookTuringDID() {
        if (!HookConfig.ENABLE_QQ950_RISK_BLOCK) return
        val classes = arrayOf(
            "com.tencent.tfd.sdk.wxa.TuringIDService",
            "com.tencent.turingfd.sdk.xq.TuringIDService"
        )
        for (cls in classes) {
            if (!HookUtils.classExists(cls)) continue
            for (m in arrayOf("getTuringDID", "getTuringDIDAsync", "getTuringDIDCached")) {
                HookUtils.hookAllMethods(cls, m, {
                    null // Object or void - type-safe
                })
            }
        }
    }
}
