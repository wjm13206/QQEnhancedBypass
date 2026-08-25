package io.github.qqenhanced.bypass.hooks;

import android.os.Debug;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Member;
import java.util.List;

import io.github.qqenhanced.bypass.XposedEntry;
import io.github.qqenhanced.bypass.config.HookConfig;
import io.github.qqenhanced.bypass.detector.DexKitDetector;
import io.github.qqenhanced.bypass.utils.HookUtils;

/**
 * DexKit-based Debug/Emulator Detection Bypass
 * Dynamically locates and neutralizes debugging and emulator checks
 */
public class DynamicDebugDetectionHook {

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!HookConfig.ENABLE_DEBUG_BYPASS) return;

        XposedEntry.log("Initializing DexKit-based Debug Detection Bypass");

        // Universal hooks: Android debug APIs
        hookDebugAPIs();

        // Dynamic hooks: DexKit-discovered debug/emulator checkers
        new Thread(() -> {
            try {
                // Wait for the single shared bridge; false in a subprocess (gated off).
                if (!DexKitDetector.awaitReady(30000)) {
                    XposedEntry.log("DynamicDebug: DexKit not available, skipping");
                    return;
                }
                hookDebugCheckers(lpparam);
                hookEmulatorCheckers(lpparam);

            } catch (Exception e) {
                XposedEntry.log("Dynamic debug detection hook failed: " + e.getMessage());
            }
        }).start();
    }

    private static void hookDebugAPIs() {
        // Debug.isDebuggerConnected()
        HookUtils.hookMethod(Debug.class, "isDebuggerConnected",
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(false);
                }
            });

        // ApplicationInfo.FLAG_DEBUGGABLE check is handled at native layer
    }

    private static void hookDebugCheckers(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedEntry.log("DynamicDebug: Searching for debug checkers...");

            // Reuse the shared bridge (owned by DexKitDetector); do NOT close it here.
            DexKitBridge bridge = DexKitDetector.getBridge();
            if (bridge == null) {
                XposedEntry.log("DynamicDebug: shared bridge null, skipping");
                return;
            }

            // Find methods calling Debug.isDebuggerConnected()
            List<MethodData> debugCheckers = bridge.findMethod(
                FindMethod.create()
                    .matcher(MethodMatcher.create()
                        .addInvoke(MethodMatcher.create()
                            .declaredClass("android.os.Debug")
                            .name("isDebuggerConnected"))
                    )
            );

            // Find methods reading TracerPid from /proc/self/status.
            // usingStrings(vararg) is AND, so keep to the single strongest marker.
            List<MethodData> tracerPidReaders = bridge.findMethod(
                FindMethod.create()
                    .matcher(MethodMatcher.create()
                        .usingStrings("TracerPid")
                    )
            );

            // Shared bridge - do NOT close (owned by DexKitDetector)

            XposedEntry.log("DynamicDebug: Found " + debugCheckers.size() + " debug checkers");
            XposedEntry.log("DynamicDebug: Found " + tracerPidReaders.size() + " TracerPid readers");

            hookCheckerList(lpparam, debugCheckers, "Debug");
            hookCheckerList(lpparam, tracerPidReaders, "TracerPid");

        } catch (Throwable e) {
            XposedEntry.log("Failed to hook debug checkers: "
                + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    private static void hookEmulatorCheckers(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedEntry.log("DynamicDebug: Searching for emulator checkers...");

            // Reuse the shared bridge (owned by DexKitDetector); do NOT close it here.
            DexKitBridge bridge = DexKitDetector.getBridge();
            if (bridge == null) {
                XposedEntry.log("DynamicDebug: shared bridge null, skipping");
                return;
            }

            // Find methods checking emulator markers
            List<MethodData> emulatorCheckers = bridge.findMethod(
                FindMethod.create()
                    .matcher(MethodMatcher.create()
                        .usingStrings("goldfish", "ranchu", "qemu", "vbox", "genymotion")
                    )
            );

            // Shared bridge - do NOT close (owned by DexKitDetector)

            XposedEntry.log("DynamicDebug: Found " + emulatorCheckers.size() + " emulator checkers");

            hookCheckerList(lpparam, emulatorCheckers, "Emulator");

        } catch (Exception e) {
            XposedEntry.log("Failed to hook emulator checkers: " + e.getMessage());
        }
    }

    private static void hookCheckerList(XC_LoadPackage.LoadPackageParam lpparam,
                                        List<MethodData> checkers, String type) {
        for (MethodData methodData : checkers) {
            try {
                String className = methodData.getClassName();
                String methodName = methodData.getMethodName();

                Member method;
                try {
                    method = methodData.getMethodInstance(lpparam.classLoader);
                } catch (Throwable t) {
                    continue;
                }

                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object result = param.getResult();

                        // Neutralize positive detection results
                        if (result instanceof Boolean && (Boolean) result) {
                            param.setResult(false);
                            XposedEntry.log(type + " checker neutralized: " + className);
                        }

                        // Neutralize non-zero detection codes
                        if (result instanceof Integer && (Integer) result != 0) {
                            param.setResult(0);
                            XposedEntry.log(type + " detection code zeroed: " + className);
                        }

                        // Clear detection result strings
                        if (result instanceof String) {
                            String str = (String) result;
                            if (isSuspiciousString(str)) {
                                param.setResult("");
                                XposedEntry.log(type + " result string cleared: " + className);
                            }
                        }
                    }
                });

                XposedEntry.log("Hooked " + type + " checker: " + className + "." + methodName);

            } catch (Exception e) {
                XposedEntry.log("Failed to hook " + type + " checker: " + e.getMessage());
            }
        }
    }

    private static boolean isSuspiciousString(String str) {
        if (str == null || str.isEmpty()) return false;
        String lower = str.toLowerCase();

        return lower.contains("debug") || lower.contains("tracer")
            || lower.contains("goldfish") || lower.contains("qemu")
            || lower.contains("emulator") || lower.contains("genymotion");
    }
}
