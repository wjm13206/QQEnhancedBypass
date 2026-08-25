package io.github.qqenhanced.bypass.detector;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XposedBridge;

/**
 * DexKit-based detector for dynamically locating obfuscated detection points in QQ
 * Eliminates hardcoded class/method names to survive version updates
 */
public class DexKitDetector {
    private static final String TAG = "DexKitDetector";
    private static DexKitBridge dexKitBridge;
    private static final Map<String, List<MethodData>> detectionCache = new HashMap<>();

    /**
     * Initialize DexKit with target app's APK path
     */
    public static void init(Context context) {
        try {
            ApplicationInfo appInfo = context.getApplicationInfo();
            String apkPath = appInfo.sourceDir;

            XposedBridge.log("[" + TAG + "] Initializing DexKit with APK: " + apkPath);
            // Explicitly load libdexkit.so before create(). In an LSPosed host process
            // System.loadLibrary("dexkit") (what create() calls) often can't find the
            // module's native lib on the host's library path -> UnsatisfiedLinkError.
            loadDexKitNativeLib(context);
            dexKitBridge = DexKitBridge.create(apkPath);
            XposedBridge.log("[" + TAG + "] DexKit initialized successfully");
        } catch (Throwable e) {
            XposedBridge.log("[" + TAG + "] Failed to initialize DexKit: "
                + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    /**
     * Try to load libdexkit.so from the module's own native library directory.
     * Falls back to System.loadLibrary if the explicit path isn't resolvable.
     */
    private static void loadDexKitNativeLib(Context context) {
        try {
            System.loadLibrary("dexkit");
            XposedBridge.log("[" + TAG + "] loadLibrary(dexkit) OK");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] loadLibrary(dexkit) failed ("
                + t.getClass().getSimpleName() + "), trying explicit path...");
            throw t; // surfaced by caller; explicit-path loading added once confirmed
        }
    }

    /**
     * Find ArtMethod hook detection methods (QSec ArtTiHookTask / all_so_hook)
     * Target: methods checking ArtMethod structure modification
     */
    public static List<MethodData> findArtHookDetectors() {
        if (detectionCache.containsKey("art_hook")) {
            return detectionCache.get("art_hook");
        }

        try {
            XposedBridge.log("[" + TAG + "] Searching for ArtMethod hook detectors...");

            // OR-semantics: usingStrings(...) is AND within one query, so we union
            // separate single-string queries to catch methods referencing ANY marker.
            List<MethodData> results = findMethodsUsingAnyString(
                "ArtMethod", "art_quick", "entry_point", "jni_code",
                "instrumentationExit", "quick_to_interpreter");

            detectionCache.put("art_hook", results);
            XposedBridge.log("[" + TAG + "] Found " + results.size() + " ArtMethod hook detectors");
            return results;
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] Error finding ArtMethod detectors: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Find /proc/self/maps readers (detecting injected .so files)
     * Target: methods reading maps file to detect LSPosed/Magisk/Zygisk/our module
     */
    public static List<MethodData> findMapsReaders() {
        if (detectionCache.containsKey("maps_reader")) {
            return detectionCache.get("maps_reader");
        }

        try {
            XposedBridge.log("[" + TAG + "] Searching for /proc/self/maps readers...");

            // "/proc/self/maps" is the strong signal; "/proc/self/task" and "/proc/"
            // catch variants. OR-union so a method needs only one of them.
            List<MethodData> results = findMethodsUsingAnyString(
                "/proc/self/maps", "/proc/self/task", "/proc/%d/maps");

            detectionCache.put("maps_reader", results);
            XposedBridge.log("[" + TAG + "] Found " + results.size() + " maps readers");
            return results;
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] Error finding maps readers: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Find wtlogin error handlers (w21 "device invalid" logic)
     * Target: methods processing wtlogin error codes from server
     */
    public static List<MethodData> findWtloginErrorHandlers() {
        if (detectionCache.containsKey("wtlogin_error")) {
            return detectionCache.get("wtlogin_error");
        }

        try {
            XposedBridge.log("[" + TAG + "] Searching for wtlogin error handlers...");

            List<MethodData> results = new ArrayList<>();

            // Pattern 1: Methods in wtlogin packages
            results.addAll(dexKitBridge.findMethod(
                FindMethod.create()
                    .matcher(MethodMatcher.create()
                        .declaredClass("com.tencent.mobileqq.msf.core.wtlogin.*")
                        .usingStrings("error", "code", "msg")
                    )
            ));

            // Pattern 2: Methods handling ErrMsg objects
            results.addAll(dexKitBridge.findMethod(
                FindMethod.create()
                    .matcher(MethodMatcher.create()
                        .paramTypes("oicq.wlogin_sdk.tools.ErrMsg")
                    )
            ));

            detectionCache.put("wtlogin_error", results);
            XposedBridge.log("[" + TAG + "] Found " + results.size() + " wtlogin error handlers");
            return results;
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] Error finding wtlogin handlers: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Find QSec hook detection methods (registerHookSo / nativeFileHook)
     * Target: QSec's native hook detection framework
     */
    public static List<MethodData> findQSecHookDetectors() {
        if (detectionCache.containsKey("qsec_hook")) {
            return detectionCache.get("qsec_hook");
        }

        try {
            XposedBridge.log("[" + TAG + "] Searching for QSec hook detectors...");

            // OR-semantics: these markers live in different methods, so union them.
            List<MethodData> results = findMethodsUsingAnyString(
                "registerHookSo", "nativeFileHook", "isHookedSoLoad", "QSecConfig",
                "all_so_hook", "ArtTiHookTask");

            detectionCache.put("qsec_hook", results);
            XposedBridge.log("[" + TAG + "] Found " + results.size() + " QSec hook detectors");
            return results;
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] Error finding QSec detectors: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Find Runtime.exec callers (root detection via "type su" / "which su")
     * Target: methods calling Runtime.exec() with su-related commands
     */
    public static List<MethodData> findRuntimeExecCallers() {
        if (detectionCache.containsKey("runtime_exec")) {
            return detectionCache.get("runtime_exec");
        }

        try {
            XposedBridge.log("[" + TAG + "] Searching for Runtime.exec callers...");

            List<MethodData> results = dexKitBridge.findMethod(
                FindMethod.create()
                    .matcher(MethodMatcher.create()
                        .addInvoke(MethodMatcher.create()
                            .declaredClass("java.lang.Runtime")
                            .name("exec"))
                        // single string = "contains"; multiple would be AND and over-narrow
                        .usingStrings("su")
                    )
            );

            detectionCache.put("runtime_exec", results);
            XposedBridge.log("[" + TAG + "] Found " + results.size() + " Runtime.exec callers");
            return results;
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] Error finding Runtime.exec callers: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Find ProcessBuilder constructors/start callers (alternative to Runtime.exec)
     */
    public static List<MethodData> findProcessBuilderCallers() {
        if (detectionCache.containsKey("process_builder")) {
            return detectionCache.get("process_builder");
        }

        try {
            XposedBridge.log("[" + TAG + "] Searching for ProcessBuilder callers...");

            List<MethodData> results = dexKitBridge.findMethod(
                FindMethod.create()
                    .matcher(MethodMatcher.create()
                        .addInvoke(MethodMatcher.create()
                            .declaredClass("java.lang.ProcessBuilder")
                            .name("start"))
                    )
            );

            detectionCache.put("process_builder", results);
            XposedBridge.log("[" + TAG + "] Found " + results.size() + " ProcessBuilder callers");
            return results;
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] Error finding ProcessBuilder callers: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Find File.exists callers checking su paths
     */
    public static List<MethodData> findSuPathCheckers() {
        if (detectionCache.containsKey("su_path")) {
            return detectionCache.get("su_path");
        }

        try {
            XposedBridge.log("[" + TAG + "] Searching for su path checkers...");

            List<MethodData> results = dexKitBridge.findMethod(
                FindMethod.create()
                    .matcher(MethodMatcher.create()
                        .addInvoke(MethodMatcher.create()
                            .declaredClass("java.io.File")
                            .name("exists"))
                        // single string = "contains"; catches "/system/bin/su" etc.
                        .usingStrings("su")
                    )
            );

            detectionCache.put("su_path", results);
            XposedBridge.log("[" + TAG + "] Found " + results.size() + " su path checkers");
            return results;
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] Error finding su path checkers: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Find PackageManager queries for Magisk/LSPosed packages
     */
    public static List<MethodData> findSuspiciousPackageQueries() {
        if (detectionCache.containsKey("package_query")) {
            return detectionCache.get("package_query");
        }

        try {
            XposedBridge.log("[" + TAG + "] Searching for suspicious package queries...");

            List<MethodData> results = dexKitBridge.findMethod(
                FindMethod.create()
                    .matcher(MethodMatcher.create()
                        .addInvoke(MethodMatcher.create()
                            .declaredClass("android.content.pm.PackageManager")
                            .name("getPackageInfo"))
                        // single "contains" marker; runtime isSuspiciousPackage() does the rest
                        .usingStrings("magisk")
                    )
            );

            detectionCache.put("package_query", results);
            XposedBridge.log("[" + TAG + "] Found " + results.size() + " package query methods");
            return results;
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] Error finding package queries: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Run one findMethod query per string (OR-semantics) and union the results,
     * de-duplicating by DEX descriptor. DexKit's usingStrings(vararg) is AND within
     * a single query, which is wrong when markers live in different methods.
     */
    private static List<MethodData> findMethodsUsingAnyString(String... markers) {
        Map<String, MethodData> unique = new HashMap<>();
        for (String marker : markers) {
            try {
                List<MethodData> found = dexKitBridge.findMethod(
                    FindMethod.create()
                        .matcher(MethodMatcher.create().usingStrings(marker))
                );
                for (MethodData m : found) {
                    unique.put(m.getDescriptor(), m);
                }
            } catch (Exception e) {
                XposedBridge.log("[" + TAG + "] query failed for '" + marker + "': " + e.getMessage());
            }
        }
        return new ArrayList<>(unique.values());
    }

    /**
     * Get all cached detection results
     */
    public static Map<String, List<MethodData>> getAllDetectionResults() {
        return new HashMap<>(detectionCache);
    }

    /**
     * Release DexKit resources
     */
    public static void release() {
        if (dexKitBridge != null) {
            try {
                dexKitBridge.close();
                XposedBridge.log("[" + TAG + "] DexKit released");
            } catch (Exception e) {
                XposedBridge.log("[" + TAG + "] Error releasing DexKit: " + e.getMessage());
            }
        }
    }
}
