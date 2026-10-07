package io.github.xalsace.qqbypass

import android.content.Context
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.xalsace.qqbypass.config.HookConfig
import io.github.xalsace.qqbypass.detector.DexKitDetector
import io.github.xalsace.qqbypass.hook.DynamicDebugDetectionHook
import io.github.xalsace.qqbypass.hook.DynamicDeviceInfoHook
import io.github.xalsace.qqbypass.hook.DynamicRootDetectionHook
import io.github.xalsace.qqbypass.hook.DynamicRuntimeMonitorHook
import io.github.xalsace.qqbypass.hook.DynamicXposedDetectionHook
import io.github.xalsace.qqbypass.hook.NetworkReportHook
import io.github.xalsace.qqbypass.hook.QQ950PatchHook
import io.github.xalsace.qqbypass.hook.UnifiedHookCoordinator
import io.github.xalsace.qqbypass.utils.HookUtils

/**
 * QQ Enhanced Bypass - Comprehensive Detection Bypass Module
 *
 * This module provides multi-layered protection against QQ's environment detection:
 * - Java Layer: Root/Xposed/Debug detection bypass (30%)
 * - Native Layer: libc/libfekit/libturingxq hooks (70%)
 * - Network reporting interception
 * - Device info spoofing with cross-validation protection
 */
class XposedEntry : XposedModule() {

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        // PackageReadyParam has no process name; remember it here for the
        // main-process gate in onPackageReady.
        hostProcessName = param.processName
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (param.packageName != TARGET_PACKAGE) return

        log("=== QQ Enhanced Bypass v2.0 ===")
        log("Package: " + param.packageName)

        // Initialize configuration
        HookConfig.init(param.classLoader)
        HookUtils.init(this)

        // Process gate: DexKitBridge.create() parses the whole ~112MB APK. QQ runs
        // several processes (main, :MSF, ...); parsing in every one caused a
        // memory/CPU spike that could get the process killed on cold start (the
        // "1-2s flash-exit"). Only the main process runs DexKit; subprocesses keep
        // the cheap universal framework hooks. main process: processName == packageName.
        val isMainProcess = param.packageName == hostProcessName
        installHooks(isMainProcess)
    }

    /**
     * 102-only API: 热重载是 libxposed 102 新增能力（101 无此回调）。
     * 返回 true 允许框架在模块更新后热替换，不重启 QQ。
     */
    override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean = true

    /**
     * 102-only API: 运行在新版本代码中。旧 hooks 已由框架接管，
     * 这里先解绑再用新 classLoader 重装全部 hooks。
     * 注意：Native 层重复安装依赖 ByteHook/C++ 侧幂等，若出现异常请重启 QQ。
     */
    override fun onHotReloaded(param: XposedModuleInterface.HotReloadedParam) {
        log("=== Hot reload: reinstalling hooks ===")
        for (handle in param.oldHookHandles) {
            try {
                handle.unhook()
            } catch (t: Throwable) {
                // ignore - best effort unhook of previous generation
            }
        }
        val loader = javaClass.classLoader ?: run {
            log("Hot reload: no classloader, hooks NOT reinstalled")
            return
        }
        HookConfig.init(loader)
        HookUtils.init(this)
        installHooks(hostProcessName == TARGET_PACKAGE)
    }

    private fun installHooks(isMainProcess: Boolean) {
        DexKitDetector.setEnabled(isMainProcess)
        log("DexKit enabled for this process: $isMainProcess")

        // === DexKit Dynamic Detection (Background Thread) ===
        // Scan for obfuscated detection points to avoid version-specific hardcoding
        if (isMainProcess) {
            Thread({
                try {
                    log("=== DexKit: Starting dynamic scan ===")
                    Thread.sleep(3000) // Wait for app context

                    val context = Class.forName("android.app.ActivityThread")
                        .getDeclaredMethod("currentApplication")
                        .apply { isAccessible = true }
                        .invoke(null) as? Context

                    if (context != null) {
                        DexKitDetector.init(context)

                        // Scan all detection points
                        log("DexKit: Scanning Runtime.exec callers...")
                        val runtimeExec = DexKitDetector.findRuntimeExecCallers().size

                        log("DexKit: Scanning ProcessBuilder callers...")
                        val processBuilder = DexKitDetector.findProcessBuilderCallers().size

                        log("DexKit: Scanning su path checkers...")
                        val suPath = DexKitDetector.findSuPathCheckers().size

                        log("DexKit: Scanning package queries...")
                        val packageQuery = DexKitDetector.findSuspiciousPackageQueries().size

                        log("DexKit: Scanning ArtMethod hook detectors...")
                        val artHook = DexKitDetector.findArtHookDetectors().size

                        log("DexKit: Scanning /proc/self/maps readers...")
                        val mapsReader = DexKitDetector.findMapsReaders().size

                        log("DexKit: Scanning wtlogin error handlers...")
                        val wtlogin = DexKitDetector.findWtloginErrorHandlers().size

                        log("DexKit: Scanning QSec hook detectors...")
                        val qsec = DexKitDetector.findQSecHookDetectors().size

                        log("=== DexKit Scan Complete ===")
                        log("  Runtime.exec callers: $runtimeExec")
                        log("  ProcessBuilder callers: $processBuilder")
                        log("  Su path checkers: $suPath")
                        log("  Package queries: $packageQuery")
                        log("  ArtMethod detectors: $artHook")
                        log("  Maps readers: $mapsReader")
                        log("  Wtlogin handlers: $wtlogin")
                        log("  QSec detectors: $qsec")

                        // Keep DexKit alive for runtime queries
                        // DexKitDetector.release() // Don't release yet - hooks may need it
                    }
                } catch (e: Exception) {
                    log("DexKit scan failed: " + e.message)
                }
            }).start()
        }

        try {
            // === Initialize Unified Hook Coordinator ===
            // This coordinates Java and Native layers seamlessly
            UnifiedHookCoordinator.initialize()

            // === Java Layer Hooks (30% detection coverage) ===

            // Layer 1: Network reporting interception (highest priority)
            NetworkReportHook.hook()

            // Layer 2: Root detection bypass (DexKit-based dynamic version)
            DynamicRootDetectionHook.hook()

            // Layer 3: Xposed/Hook framework detection mitigation (DexKit-based dynamic version)
            DynamicXposedDetectionHook.hook()

            // Layer 4: Device info consistency protection (DexKit-based dynamic version)
            DynamicDeviceInfoHook.hook()

            // Layer 5: Debug/Emulator detection bypass (DexKit-based dynamic version)
            DynamicDebugDetectionHook.hook()

            // Layer 6: Runtime monitoring bypass (DexKit-based dynamic version)
            DynamicRuntimeMonitorHook.hook()

            // Layer 7: QQ 9.3.50 detection-point completion (real risk/kick targets)
            QQ950PatchHook.hook()

            // === Report Status ===
            log("=== Java Hooks Initialized ===")
            log("Initial Protection Level: " + UnifiedHookCoordinator.getProtectionLevel())
            // Native 层在后台线程异步安装，稍后报告最终等级
            UnifiedHookCoordinator.reportFinalProtectionLevelAsync()
        } catch (t: Throwable) {
            log("Error during hook initialization: " + t.message)
            t.printStackTrace()
        }
    }

    companion object {
        private const val TAG = "QQEnhancedBypass"
        private const val TARGET_PACKAGE = "com.tencent.mobileqq"

        @Volatile
        private var hostProcessName: String? = null

        fun log(message: String) {
            Log.i(TAG, message)
        }

        fun logError(message: String, t: Throwable) {
            Log.e(TAG, message, t)
        }
    }
}
