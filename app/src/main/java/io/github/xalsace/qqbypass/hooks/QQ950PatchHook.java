package io.github.xalsace.qqbypass.hooks;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import io.github.xalsace.qqbypass.XposedEntry;
import io.github.xalsace.qqbypass.config.HookConfig;
import io.github.xalsace.qqbypass.utils.HookUtils;

/**
 * QQ 9.3.50 detection/report completion hooks.
 *
 * Targets the real risk-control data sources that the legacy hooks missed
 * (the legacy onExec/process/sendSsoMsg targets either did not exist on 9.3.50
 * or were on the wrong path). Every class/method below was verified to exist by
 * directly parsing QQ 9.3.50's dex class_data (not trusting the written reports,
 * which contained several fabricated method names).
 *
 * Return types were also verified. setResult(null) is only used where the method
 * returns void (V) or an Object (never a primitive) to avoid the uncatchable
 * ClassCastException class of crash. Object-returning blocks carry an NPE caveat
 * (caller may deref null) and are grouped separately so they can be toggled.
 *
 * Everything is gated by HookConfig.ENABLE_QQ950_PATCH and classExists() so a
 * missing class can never crash module init.
 */
public class QQ950PatchHook {

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!HookConfig.ENABLE_QQ950_PATCH) return;

        XposedEntry.log("Initializing QQ 9.3.50 detection-point patch");

        // Tier 1: void-return, crash-safe backstops (kick + telemetry + switches)
        hookKickBackstop();
        hookMsfTelemetry();
        hookTuringWrapper();
        hookChannelReport();

        // Tier 2: object-return risk-detection blocks (type-safe, NPE-caveat)
        hookTuringRiskDetect();
        hookTuringDID();
    }

    // ==================== Tier 1: void-return, crash-safe ====================

    /**
     * Kick backstop: NTKickProcessor.b(...) : void is the method that actually
     * clears the login state on a server kick. Blocking it prevents the forced
     * logout. NOTE: if the server has already invalidated the session token this
     * may leave a non-functional session; kept as a backstop, observe behavior.
     */
    private static void hookKickBackstop() {
        final String cls = "com.tencent.mobileqq.kick.NTKickProcessor";
        Class<?> clazz = XposedHelpers.findClassIfExists(cls, HookConfig.getClassLoader());
        if (clazz == null) {
            XposedEntry.log("[QQ950] NTKickProcessor not present, skip kick backstop");
            return;
        }
        // The kick-clearing method is obfuscated ("b" on 9.3.50) and will be
        // renamed across versions. Locate it by a stable signature instead: a
        // void method whose parameters include a Constants$LogoutReason. The
        // public entry a(AppRuntime, KickedInfo) lacks that param, so this
        // matches only the internal logout worker.
        int hooked = 0;
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getReturnType() != void.class) continue;
            boolean hasLogoutReason = false;
            for (Class<?> pt : m.getParameterTypes()) {
                if (pt.getName().contains("LogoutReason")) { hasLogoutReason = true; break; }
            }
            if (!hasLogoutReason) continue;
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                    p.setResult(null); // void - crash-safe
                    XposedEntry.log("[QQ950] blocked kick worker (kick suppressed)");
                }
            });
            hooked++;
            XposedEntry.log("[QQ950] hooked kick worker by signature: " + m.getName());
        }
        if (hooked == 0) {
            XposedEntry.log("[QQ950] no kick worker matched signature (void + LogoutReason)");
        }
    }

    /**
     * MSF telemetry: all void, pure reporting. sendAllMsg is intentionally
     * EXCLUDED (name implies message flushing, blocking could break send/recv).
     */
    private static void hookMsfTelemetry() {
        final String cls = "com.tencent.mobileqq.msf.core.MsfCore";
        if (!HookUtils.classExists(cls)) return;
        String[] reportMethods = {
            "doReportOnInitComplete", "reportMsfCoreInit",
            "tryReportJobAlive", "tryReportLoadCfgTempFile",
            "tryReportMSFAlive", "tryReportSoLoadUseTxlib"
        };
        for (String m : reportMethods) {
            HookUtils.hookAllMethods(cls, m, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                    p.setResult(null); // all void - crash-safe
                }
            });
        }
    }

    /**
     * Turing switches: TuringWrapper.b()/c() : void. a() returns AtomicBoolean
     * (state), left untouched to avoid NPE.
     */
    private static void hookTuringWrapper() {
        final String cls = "com.tencent.mobileqq.dt.model.TuringWrapper";
        Class<?> clazz = XposedHelpers.findClassIfExists(cls, HookConfig.getClassLoader());
        if (clazz == null) return;
        // b()/c() are the obfuscated turing-cache writers: both static, void, no
        // args. a() returns AtomicBoolean (state getter) and is left untouched.
        // Match by signature so a version rename of b/c still resolves them.
        int hooked = 0;
        for (Method m : clazz.getDeclaredMethods()) {
            if (!Modifier.isStatic(m.getModifiers())) continue;
            if (m.getReturnType() != void.class) continue;
            if (m.getParameterTypes().length != 0) continue;
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                    p.setResult(null); // void - crash-safe
                }
            });
            hooked++;
            XposedEntry.log("[QQ950] hooked TuringWrapper switch by signature: " + m.getName());
        }
        if (hooked == 0) {
            XposedEntry.log("[QQ950] no TuringWrapper switch matched (static void no-arg)");
        }
    }

    /**
     * ChannelManager.checkMethod() : void - the Java-side entry that arms the
     * report channel (initReport is private native, cannot be hooked directly).
     */
    private static void hookChannelReport() {
        final String cls = "com.tencent.mobileqq.channel.ChannelManager";
        if (!HookUtils.classExists(cls)) return;
        HookUtils.hookAllMethods(cls, "checkMethod", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                p.setResult(null); // void - crash-safe
                XposedEntry.log("[QQ950] blocked ChannelManager.checkMethod");
            }
        });
    }

    // ============ Tier 2: object-return risk detection (NPE-caveat) ============
    // These return Objects, so setResult(null) is TYPE-safe (no ClassCastException),
    // but a caller that dereferences the null result without a guard could NPE.
    // Turing SDKs generally null-check risk responses defensively. Gated so they
    // can be disabled independently if an NPE appears in logs.

    /**
     * TuringRiskService.reqRiskDetectV2(...) : RiskDetectResp - the core risk
     * detection call whose result feeds the server's cheat/anomaly judgment.
     * Both the wxa and xq SDK variants, 3 overloads each.
     */
    private static void hookTuringRiskDetect() {
        if (!HookConfig.ENABLE_QQ950_RISK_BLOCK) return;
        String[] classes = {
            "com.tencent.tfd.sdk.wxa.TuringRiskService",
            "com.tencent.turingfd.sdk.xq.TuringRiskService"
        };
        for (final String cls : classes) {
            if (!HookUtils.classExists(cls)) continue;
            HookUtils.hookAllMethods(cls, "reqRiskDetectV2", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                    p.setResult(null); // RiskDetectResp (Object) - type-safe
                    XposedEntry.log("[QQ950] blocked reqRiskDetectV2 @ " + cls);
                }
            });
        }
    }

    /**
     * TuringIDService.getTuringDID/getTuringDIDCached : ITuringDID (Object);
     * getTuringDIDAsync : void. The Turing device id is a fingerprint source.
     */
    private static void hookTuringDID() {
        if (!HookConfig.ENABLE_QQ950_RISK_BLOCK) return;
        String[] classes = {
            "com.tencent.tfd.sdk.wxa.TuringIDService",
            "com.tencent.turingfd.sdk.xq.TuringIDService"
        };
        for (final String cls : classes) {
            if (!HookUtils.classExists(cls)) continue;
            for (String m : new String[]{"getTuringDID", "getTuringDIDAsync", "getTuringDIDCached"}) {
                HookUtils.hookAllMethods(cls, m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                        p.setResult(null); // Object or void - type-safe
                    }
                });
            }
        }
    }
}
