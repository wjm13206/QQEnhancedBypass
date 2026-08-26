package io.github.xalsace.qqbypass.hooks;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import io.github.xalsace.qqbypass.XposedEntry;
import io.github.xalsace.qqbypass.config.HookConfig;
import io.github.xalsace.qqbypass.utils.HookUtils;

import java.io.BufferedReader;
import java.io.FileReader;

/**
 * Debug & Emulator Detection Bypass
 *
 * Handles multiple detection methods:
 * - TracerPid check in /proc/self/status (5+ implementations)
 * - ro.debuggable property
 * - ro.secure property
 * - ApplicationInfo.FLAG_DEBUGGABLE
 * - Emulator detection (ro.kernel.qemu, goldfish, etc.)
 */
public class DebugDetectionHook {

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!HookConfig.ENABLE_DEBUG_BYPASS) return;

        XposedEntry.log("Initializing Debug Detection Bypass");

        // Hook 1: TracerPid checks
        hookTracerPidChecks();

        // Hook 2: System properties
        hookDebugProperties();

        // Hook 3: ApplicationInfo flags
        hookApplicationInfo();

        // Hook 4: Emulator detection
        hookEmulatorDetection();

        // Hook 5: TuringFD debug checks
        hookTuringDebugChecks();
    }

    private static void hookTracerPidChecks() {
        // Hook file reading for /proc/self/status
        HookUtils.hookMethod(BufferedReader.class, "readLine",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String line = (String) param.getResult();
                    if (line != null && line.startsWith("TracerPid:")) {
                        // Always return TracerPid: 0 (not being traced)
                        param.setResult("TracerPid:\t0");
                        XposedEntry.log("Spoofed TracerPid to 0");
                    }
                }
            });

        // Hook TuringFD specific TracerPid checks
        String[] turingClasses = {
            "com.tencent.tfd.sdk.wxa.Blueberry",
            "com.tencent.turingcam.oqKCa",
            "com.tencent.turingfd.sdk.xq.Pomegranate"
        };

        for (String className : turingClasses) {
            HookUtils.hookAllMethods(className, "b",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        // These methods check TracerPid
                        param.setResult(0); // Return 0 (not debugging)
                    }
                });
        }
    }

    private static void hookDebugProperties() {
        // Hook SystemProperties.get for debug-related properties
        HookUtils.hookAllMethods("android.os.SystemProperties", "get",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length > 0) {
                        String key = (String) param.args[0];

                        if ("ro.debuggable".equals(key)) {
                            param.setResult("0");
                        } else if ("ro.secure".equals(key)) {
                            param.setResult("1");
                        }
                    }
                }
            });

        HookUtils.hookAllMethods("android.os.SystemProperties", "getInt",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length > 0) {
                        String key = (String) param.args[0];

                        if ("ro.debuggable".equals(key)) {
                            param.setResult(0);
                        } else if ("ro.secure".equals(key)) {
                            param.setResult(1);
                        }
                    }
                }
            });

        HookUtils.hookAllMethods("android.os.SystemProperties", "getBoolean",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length > 0) {
                        String key = (String) param.args[0];

                        if ("ro.debuggable".equals(key)) {
                            param.setResult(false);
                        } else if ("ro.secure".equals(key)) {
                            param.setResult(true);
                        }
                    }
                }
            });
    }

    private static void hookApplicationInfo() {
        // Hook ApplicationInfo.flags to remove FLAG_DEBUGGABLE
        HookUtils.hookAllMethods("android.app.ApplicationPackageManager", "getApplicationInfo",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object appInfo = param.getResult();
                    if (appInfo != null) {
                        android.content.pm.ApplicationInfo info = (android.content.pm.ApplicationInfo) appInfo;
                        // Remove FLAG_DEBUGGABLE flag (0x02)
                        info.flags &= ~android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE;
                    }
                }
            });
    }

    private static void hookEmulatorDetection() {
        // Hook emulator-related properties
        HookUtils.hookAllMethods("android.os.SystemProperties", "get",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length > 0) {
                        String key = (String) param.args[0];

                        // Emulator detection properties
                        if ("ro.kernel.qemu".equals(key)) {
                            param.setResult("0");
                        } else if ("ro.product.device".equals(key)) {
                            String result = (String) param.getResult();
                            if ("goldfish".equals(result) || "vbox86".equals(result)) {
                                param.setResult("unknown"); // Spoof to non-emulator value
                            }
                        }
                    }
                }
            });
    }

    private static void hookTuringDebugChecks() {
        // TuringFD checks are primarily in native code (libfekit.so, libturingxq.so)
        // and are handled by the native bypass layer
    }
}
