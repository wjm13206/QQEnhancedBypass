package io.github.qqenhanced.bypass.hooks;

import android.os.Debug;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Member;
import java.util.List;

import io.github.qqenhanced.bypass.XposedEntry;
import io.github.qqenhanced.bypass.config.HookConfig;
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
                Thread.sleep(5000); // Wait for DexKit scan

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

            android.content.Context context = (android.content.Context)
                XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader),
                    "currentApplication");

            if (context == null) {
                XposedEntry.log("DynamicDebug: Context not ready, skipping");
                return;
            }

            String apkPath = context.getApplicationInfo().sourceDir;
            DexKitBridge bridge = DexKitBridge.create(apkPath);

            // Find methods calling Debug.isDebuggerConnected()
            List<MethodData> debugCheckers = bridge.findMethod(
                FindMethod.create()
                    .matcher(MethodMatcher.create()
                        .addInvoke(MethodMatcher.create()
                            .declaredClass("android.os.Debug")
                            .name("isDebuggerConnected"))
                    )
            );

            // Find methods reading TracerPid from /proc/self/status
            List<MethodData> tracerPidReaders = bridge.findMethod(
                FindMethod.create()
                    .matcher(MethodMatcher.create()
                        .usingStrings("TracerPid", "/proc/self/status", "/proc/")
                    )
            );

            bridge.close();

            XposedEntry.log("DynamicDebug: Found " + debugCheckers.size() + " debug checkers");
            XposedEntry.log("DynamicDebug: Found " + tracerPidReaders.size() + " TracerPid readers");

            hookCheckerList(lpparam, debugCheckers, "Debug");
            hookCheckerList(lpparam, tracerPidReaders, "TracerPid");

        } catch (Exception e) {
            XposedEntry.log("Failed to hook debug checkers: " + e.getMessage());
        }
    }

    private static void hookEmulatorCheckers(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedEntry.log("DynamicDebug: Searching for emulator checkers...");

            android.content.Context context = (android.content.Context)
                XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader),
                    "currentApplication");

            if (context == null) return;

            String apkPath = context.getApplicationInfo().sourceDir;
            DexKitBridge bridge = DexKitBridge.create(apkPath);

            // Find methods checking emulator markers
            List<MethodData> emulatorCheckers = bridge.findMethod(
                FindMethod.create()
                    .matcher(MethodMatcher.create()
                        .usingStrings("goldfish", "ranchu", "qemu", "vbox", "genymotion")
                    )
            );

            bridge.close();

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
