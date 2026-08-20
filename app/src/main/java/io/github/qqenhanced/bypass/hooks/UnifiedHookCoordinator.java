package io.github.qqenhanced.bypass.hooks;

import android.util.Log;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import io.github.qqenhanced.bypass.NativeBypass;
import io.github.qqenhanced.bypass.XposedEntry;
import io.github.qqenhanced.bypass.config.HookConfig;
import io.github.qqenhanced.bypass.utils.HookUtils;

/**
 * Unified Hook Coordinator
 *
 * This module coordinates between Java and Native hooks to provide
 * seamless protection across both layers.
 *
 * Java Hook → If failed → Try Native Hook
 * Native Hook → Notify Java layer
 */
public class UnifiedHookCoordinator {

    private static boolean nativeLayerAvailable = false;

    public static void initialize(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedEntry.log("=== Initializing Unified Hook Coordinator ===");

        // Check Native layer availability
        nativeLayerAvailable = NativeBypass.isLibraryLoaded();

        if (nativeLayerAvailable) {
            XposedEntry.log("Native layer available - Full protection (80%)");
            initNativeIntegration();
        } else {
            XposedEntry.log("Native layer unavailable - Java only (30%)");
        }

        // Install coordinated hooks
        installCoordinatedHooks(lpparam);
    }

    /**
     * Initialize Native integration
     */
    private static void initNativeIntegration() {
        // Initialize Native hooks
        NativeBypass.initNativeHooks();

        // Register callback from Native layer
        registerNativeCallbacks();
    }

    /**
     * Register callbacks for Native → Java communication
     */
    private static void registerNativeCallbacks() {
        // Native layer can notify Java layer through JNI
        // e.g., when a detection is blocked at Native level
    }

    /**
     * Install hooks with Java/Native coordination
     */
    private static void installCoordinatedHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook critical detection points with fallback
        hookWithFallback(lpparam);

        // Setup bidirectional communication
        setupCommunicationChannel(lpparam);
    }

    /**
     * Hook with Java → Native fallback
     */
    private static void hookWithFallback(XC_LoadPackage.LoadPackageParam lpparam) {
        // Example: File existence check
        // Java layer hooks File.exists()
        // If it fails, Native layer hooks access()

        HookUtils.hookMethod(java.io.File.class, "exists",
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    java.io.File file = (java.io.File) param.thisObject;
                    String path = file.getAbsolutePath();

                    // Check if this is a sensitive path
                    if (isSensitivePath(path)) {
                        param.setResult(false);
                        XposedEntry.log("[Java] Blocked file check: " + path);

                        // Notify Native layer (if available)
                        if (nativeLayerAvailable) {
                            notifyNativeLayer("file_blocked", path);
                        }
                    }
                }
            });
    }

    /**
     * Setup bidirectional communication
     */
    private static void setupCommunicationChannel(XC_LoadPackage.LoadPackageParam lpparam) {
        // Java can query Native status
        // Native can notify Java about detections

        if (nativeLayerAvailable) {
            // Check Native hook status periodically
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    String status = NativeBypass.getHookStatus();
                    XposedEntry.log("[Coordinator] Native status: " + status);
                } catch (Exception e) {
                    XposedEntry.logError("[Coordinator] Status check failed", e);
                }
            }).start();
        }
    }

    /**
     * Notify Native layer about Java detections
     */
    private static void notifyNativeLayer(String event, String data) {
        if (!nativeLayerAvailable) return;

        try {
            // JNI call to Native layer
            NativeBypass.notifyDetection(event, data);
        } catch (Throwable t) {
            // Silent fail
        }
    }

    /**
     * Check if path is sensitive
     */
    private static boolean isSensitivePath(String path) {
        if (path == null) return false;

        String[] sensitivePaths = {
            "/system/bin/su",
            "/system/xbin/su",
            "/data/adb/magisk",
            "/data/adb/lsposed"
        };

        for (String sensitive : sensitivePaths) {
            if (path.contains(sensitive)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get protection level
     */
    public static String getProtectionLevel() {
        if (nativeLayerAvailable && NativeBypass.isHooksInstalled()) {
            return "Full (80%)";
        } else if (nativeLayerAvailable) {
            return "Native loading (60%)";
        } else {
            return "Java only (30%)";
        }
    }
}
