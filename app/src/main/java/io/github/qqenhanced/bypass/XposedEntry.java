package io.github.qqenhanced.bypass;

import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import io.github.qqenhanced.bypass.config.HookConfig;
import io.github.qqenhanced.bypass.detector.DexKitDetector;
import io.github.qqenhanced.bypass.hooks.*;

/**
 * QQ Enhanced Bypass - Comprehensive Detection Bypass Module
 *
 * This module provides multi-layered protection against QQ's environment detection:
 * - Java Layer: Root/Xposed/Debug detection bypass (30%)
 * - Native Layer: libc/libfekit/libturingxq hooks (70%)
 * - Network reporting interception
 * - Device info spoofing with cross-validation protection
 */
public class XposedEntry implements IXposedHookLoadPackage {

    private static final String TAG = "QQEnhancedBypass";
    private static final String TARGET_PACKAGE = "com.tencent.mobileqq";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        log("=== QQ Enhanced Bypass v2.0 ===");
        log("Package: " + lpparam.packageName);
        log("Process: " + lpparam.processName);

        // Initialize configuration
        HookConfig.init(lpparam);

        // === DexKit Dynamic Detection (Background Thread) ===
        // Scan for obfuscated detection points to avoid version-specific hardcoding
        new Thread(() -> {
            try {
                log("=== DexKit: Starting dynamic scan ===");
                Thread.sleep(3000); // Wait for app context

                android.content.Context context = (android.content.Context)
                    de.robv.android.xposed.XposedHelpers.callStaticMethod(
                        de.robv.android.xposed.XposedHelpers.findClass(
                            "android.app.ActivityThread", lpparam.classLoader),
                        "currentApplication");

                if (context != null) {
                    DexKitDetector.init(context);

                    // Scan all detection points
                    log("DexKit: Scanning Runtime.exec callers...");
                    int runtimeExec = DexKitDetector.findRuntimeExecCallers().size();

                    log("DexKit: Scanning ProcessBuilder callers...");
                    int processBuilder = DexKitDetector.findProcessBuilderCallers().size();

                    log("DexKit: Scanning su path checkers...");
                    int suPath = DexKitDetector.findSuPathCheckers().size();

                    log("DexKit: Scanning package queries...");
                    int packageQuery = DexKitDetector.findSuspiciousPackageQueries().size();

                    log("DexKit: Scanning ArtMethod hook detectors...");
                    int artHook = DexKitDetector.findArtHookDetectors().size();

                    log("DexKit: Scanning /proc/self/maps readers...");
                    int mapsReader = DexKitDetector.findMapsReaders().size();

                    log("DexKit: Scanning wtlogin error handlers...");
                    int wtlogin = DexKitDetector.findWtloginErrorHandlers().size();

                    log("DexKit: Scanning QSec hook detectors...");
                    int qsec = DexKitDetector.findQSecHookDetectors().size();

                    log("=== DexKit Scan Complete ===");
                    log("  Runtime.exec callers: " + runtimeExec);
                    log("  ProcessBuilder callers: " + processBuilder);
                    log("  Su path checkers: " + suPath);
                    log("  Package queries: " + packageQuery);
                    log("  ArtMethod detectors: " + artHook);
                    log("  Maps readers: " + mapsReader);
                    log("  Wtlogin handlers: " + wtlogin);
                    log("  QSec detectors: " + qsec);

                    // Keep DexKit alive for runtime queries
                    // DexKitDetector.release(); // Don't release yet - hooks may need it
                }
            } catch (Exception e) {
                log("DexKit scan failed: " + e.getMessage());
            }
        }).start();

        try {
            // === Initialize Unified Hook Coordinator ===
            // This coordinates Java and Native layers seamlessly
            UnifiedHookCoordinator.initialize(lpparam);

            // === Java Layer Hooks (30% detection coverage) ===

            // Layer 1: Network reporting interception (highest priority)
            NetworkReportHook.hook(lpparam);

            // Layer 2: Root detection bypass (DexKit-based dynamic version)
            DynamicRootDetectionHook.hook(lpparam);

            // Layer 3: Xposed/Hook framework detection mitigation
            XposedDetectionHook.hook(lpparam);

            // Layer 4: Device info consistency protection
            DeviceInfoHook.hook(lpparam);

            // Layer 5: Debug/Emulator detection bypass
            DebugDetectionHook.hook(lpparam);

            // Layer 6: Runtime monitoring bypass (DexKit-based dynamic version)
            DynamicRuntimeMonitorHook.hook(lpparam);

            // Layer 7: QQ 9.3.50 detection-point completion (real risk/kick targets)
            QQ950PatchHook.hook(lpparam);

            // === Report Status ===
            log("=== Java Hooks Initialized ===");
            log("Initial Protection Level: " + UnifiedHookCoordinator.getProtectionLevel());
            // Native 层在后台线程异步安装，稍后报告最终等级
            UnifiedHookCoordinator.reportFinalProtectionLevelAsync();

        } catch (Throwable t) {
            log("Error during hook initialization: " + t.getMessage());
            t.printStackTrace();
        }
    }

    /**
     * Initialize native layer hooks (deprecated - now handled by UnifiedHookCoordinator)
     */
    @Deprecated
    private void initNativeHooks() {
        // This method is kept for compatibility but functionality moved to UnifiedHookCoordinator
    }

    public static void log(String message) {
        Log.i(TAG, message);
    }

    public static void logError(String message, Throwable t) {
        Log.e(TAG, message, t);
    }
}
