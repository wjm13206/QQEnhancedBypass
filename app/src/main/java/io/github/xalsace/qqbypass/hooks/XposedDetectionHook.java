package io.github.xalsace.qqbypass.hooks;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import io.github.xalsace.qqbypass.XposedEntry;
import io.github.xalsace.qqbypass.config.HookConfig;
import io.github.xalsace.qqbypass.utils.HookUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Xposed/LSPosed/Hook Framework Detection Bypass
 *
 * Mitigates detection of:
 * - LSPosed/Xposed installer package
 * - Xposed framework artifacts
 * - /proc/self/maps scanning for hook libraries
 * - Cydia Substrate detection
 * - ART method hook detection (limited effectiveness)
 *
 * Note: Native-layer detection (libfekit.so, libturingxq.so) requires native hooks
 * This module can only handle Java-layer checks and file I/O interception
 */
public class XposedDetectionHook {

    private static final String[] XPOSED_PACKAGES = {
        "de.robv.android.xposed.installer",
        "org.meowcat.edxposed.manager",
        "io.github.lsposed.manager",
        "com.topjohnwu.magisk"
    };

    private static final String[] HOOK_LIBRARY_PATTERNS = {
        "lsposed",
        "xposed",
        "libriru",
        "zygisk",
        "edxposed",
        "libsubstrate",
        "libAndroidCydia",
        "libDalvikLoader"
    };

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!HookConfig.ENABLE_XPOSED_BYPASS) return;

        XposedEntry.log("Initializing Xposed Detection Bypass");

        // Hook 1: PackageManager to hide Xposed packages
        hookPackageManagerForXposed();

        // Hook 2: File I/O to filter /proc/self/maps
        hookProcMapsReading();

        // Hook 3: Stack trace filtering
        hookStackTrace();

        // Hook 4: ClassLoader detection
        hookClassLoaderCheck();
    }

    private static void hookPackageManagerForXposed() {
        // Hide Xposed-related packages from PackageManager queries
        HookUtils.hookAllMethods("android.app.ApplicationPackageManager", "getPackageInfo",
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length > 0) {
                        String packageName = (String) param.args[0];
                        if (isXposedPackage(packageName)) {
                            XposedEntry.log("Blocked PackageManager query for: " + packageName);
                            throw new android.content.pm.PackageManager.NameNotFoundException();
                        }
                    }
                }
            });

        HookUtils.hookAllMethods("android.app.ApplicationPackageManager", "getApplicationInfo",
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length > 0) {
                        String packageName = (String) param.args[0];
                        if (isXposedPackage(packageName)) {
                            throw new android.content.pm.PackageManager.NameNotFoundException();
                        }
                    }
                }
            });

        HookUtils.hookAllMethods("android.app.ApplicationPackageManager", "getInstalledPackages",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.getResult() != null) {
                        @SuppressWarnings("unchecked")
                        List<Object> packages = (List<Object>) param.getResult();
                        List<Object> filtered = new ArrayList<>();

                        for (Object pkg : packages) {
                            String pkgName = (String) XposedHelpers.getObjectField(pkg, "packageName");
                            if (!isXposedPackage(pkgName)) {
                                filtered.add(pkg);
                            }
                        }
                        param.setResult(filtered);
                    }
                }
            });
    }

    private static void hookProcMapsReading() {
        // Hook file reading to filter /proc/self/maps content
        HookUtils.hookMethod(FileReader.class, "read", char[].class,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    // This is complex - would need to track file paths and filter content
                    // Simplified implementation: just log the attempt
                }
            });

        // Hook BufferedReader.readLine for /proc/self/maps
        HookUtils.hookMethod(BufferedReader.class, "readLine",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String line = (String) param.getResult();
                    if (line != null && containsHookLibrary(line)) {
                        // Try to filter out hook library references
                        // This is tricky because we need context about which file is being read
                        Object reader = param.thisObject;
                        // Check if this BufferedReader is reading from /proc/self/maps
                        try {
                            Object lock = XposedHelpers.getObjectField(reader, "lock");
                            if (lock instanceof FileReader) {
                                // We can't easily get the file path, so this is limited
                                // Return null to skip this line
                                param.setResult(null);
                                XposedEntry.log("Filtered hook library from maps: " + line.substring(0, Math.min(50, line.length())));
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            });

        // Hook File.exists for LSPosed artifacts
        HookUtils.hookMethod(File.class, "exists",
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    File file = (File) param.thisObject;
                    String path = file.getAbsolutePath();

                    if (isXposedArtifact(path)) {
                        param.setResult(false);
                        XposedEntry.log("Hidden Xposed artifact: " + path);
                    }
                }
            });
    }

    private static void hookStackTrace() {
        // Filter Xposed from stack traces
        HookUtils.hookMethod(Throwable.class, "getStackTrace",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    StackTraceElement[] original = (StackTraceElement[]) param.getResult();
                    if (original == null) return;

                    List<StackTraceElement> filtered = new ArrayList<>();
                    for (StackTraceElement element : original) {
                        String className = element.getClassName();
                        if (!className.contains("xposed") &&
                            !className.contains("lsposed") &&
                            !className.contains("edxposed")) {
                            filtered.add(element);
                        }
                    }

                    if (filtered.size() != original.length) {
                        param.setResult(filtered.toArray(new StackTraceElement[0]));
                    }
                }
            });
    }

    private static void hookClassLoaderCheck() {
        // Some apps check the ClassLoader chain for Xposed
        HookUtils.hookMethod(ClassLoader.class, "toString",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String result = (String) param.getResult();
                    if (result != null && (result.contains("xposed") || result.contains("lsposed"))) {
                        // Sanitize the string
                        result = result.replaceAll("(?i)xposed", "dalvik")
                                       .replaceAll("(?i)lsposed", "dalvik");
                        param.setResult(result);
                    }
                }
            });
    }

    private static boolean isXposedPackage(String packageName) {
        if (packageName == null) return false;
        for (String xposedPkg : XPOSED_PACKAGES) {
            if (packageName.equals(xposedPkg)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsHookLibrary(String line) {
        if (line == null) return false;
        String lowerLine = line.toLowerCase();
        for (String pattern : HOOK_LIBRARY_PATTERNS) {
            if (lowerLine.contains(pattern.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isXposedArtifact(String path) {
        return path != null && (
            path.contains("/data/user_de/0/org.lsposed.manager") ||
            path.contains("/data/user/0/org.lsposed.manager") ||
            path.contains("/data/data/de.robv.android.xposed.installer") ||
            path.contains("XposedBridge") ||
            path.contains("/system/framework/XposedBridge")
        );
    }
}
