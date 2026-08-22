package io.github.qqenhanced.bypass;

import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import io.github.qqenhanced.bypass.config.HookConfig;
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

        try {
            // === Initialize Unified Hook Coordinator ===
            // This coordinates Java and Native layers seamlessly
            UnifiedHookCoordinator.initialize(lpparam);

            // === Java Layer Hooks (30% detection coverage) ===

            // Layer 1: Network reporting interception (highest priority)
            NetworkReportHook.hook(lpparam);

            // Layer 2: Root detection bypass
            RootDetectionHook.hook(lpparam);

            // Layer 3: Xposed/Hook framework detection mitigation
            XposedDetectionHook.hook(lpparam);

            // Layer 4: Device info consistency protection
            DeviceInfoHook.hook(lpparam);

            // Layer 5: Debug/Emulator detection bypass
            DebugDetectionHook.hook(lpparam);

            // Layer 6: Runtime monitoring bypass
            RuntimeMonitorHook.hook(lpparam);

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
