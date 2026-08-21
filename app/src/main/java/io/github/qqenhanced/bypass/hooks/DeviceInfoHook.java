package io.github.qqenhanced.bypass.hooks;

import android.os.Build;
import android.telephony.TelephonyManager;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import io.github.qqenhanced.bypass.XposedEntry;
import io.github.qqenhanced.bypass.config.HookConfig;
import io.github.qqenhanced.bypass.utils.HookUtils;

/**
 * Device Info Hook - Consistency Protection
 *
 * Handles Pandora's multi-path cross-validation:
 * - Ensures consistent values across different API calls
 * - Prevents detection of single-API hooks
 * - Maintains device fingerprint consistency
 *
 * Pandora DeviceInfoMonitor uses 6+ different methods to get the same info:
 * - TelephonyManager.getImei()
 * - TelephonyManager.getDeviceId()
 * - Build.SERIAL (field)
 * - Build.getSerial() (method)
 * - SystemProperties.get()
 * - SubscriptionManager APIs
 */
public class DeviceInfoHook {

    private static String cachedImei = null;
    private static String cachedDeviceId = null;
    private static String cachedSerial = null;
    private static String cachedAndroidId = null;

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!HookConfig.ENABLE_DEVICE_SPOOF) return;

        XposedEntry.log("Initializing Device Info Hook");

        // Initialize cached values from config or real device
        initCachedValues();

        // Hook all device ID retrieval methods with consistent values
        hookTelephonyManager();
        hookBuildSerial();
        hookSystemProperties();
        hookAndroidId();

        // Hook Pandora monitoring framework
        hookPandoraMonitor();
    }

    private static void initCachedValues() {
        // Use configured fake values or null to pass through real values
        cachedImei = HookConfig.FAKE_IMEI;
        cachedDeviceId = HookConfig.FAKE_IMEI; // Same as IMEI for consistency
        cachedSerial = HookConfig.FAKE_SERIAL;
        cachedAndroidId = HookConfig.FAKE_ANDROID_ID;
    }

    private static void hookTelephonyManager() {
        // Hook all IMEI/DeviceId retrieval methods
        String[] methods = {"getImei", "getDeviceId", "getMeid"};

        for (String method : methods) {
            HookUtils.hookAllMethods("android.telephony.TelephonyManager", method,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (cachedImei != null) {
                            param.setResult(cachedImei);
                        }
                        // If null, let real value pass through for consistency
                    }
                });
        }

        // Hook getSimSerialNumber
        HookUtils.hookAllMethods("android.telephony.TelephonyManager", "getSimSerialNumber",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (cachedSerial != null) {
                        param.setResult(cachedSerial);
                    }
                }
            });
    }

    private static void hookBuildSerial() {
        // Hook Build.SERIAL field access (difficult to hook directly)
        // Hook Build.getSerial() method instead
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            HookUtils.hookMethod(Build.class, "getSerial",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (cachedSerial != null) {
                            param.setResult(cachedSerial);
                        }
                    }
                });
        }
    }

    private static void hookSystemProperties() {
        // Hook SystemProperties.get() to return consistent values
        HookUtils.hookAllMethods("android.os.SystemProperties", "get",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length > 0) {
                        String key = (String) param.args[0];

                        // Override specific properties
                        if ("ro.boot.serialno".equals(key) && cachedSerial != null) {
                            param.setResult(cachedSerial);
                        } else if ("gsm.serial".equals(key) && cachedSerial != null) {
                            param.setResult(cachedSerial);
                        }
                    }
                }
            });
    }

    private static void hookAndroidId() {
        // Hook Settings.Secure.getString for android_id
        HookUtils.hookAllMethods("android.provider.Settings.Secure", "getString",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length >= 2) {
                        String key = (String) param.args[1];
                        if ("android_id".equals(key) && cachedAndroidId != null) {
                            param.setResult(cachedAndroidId);
                        }
                    }
                }
            });
    }

    private static void hookPandoraMonitor() {
        // Hook Pandora's DeviceInfoMonitor to prevent cross-validation
        HookUtils.hookAllMethods("com.tencent.qmethod.monitor.core.DeviceInfoMonitor", "getImei",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (cachedImei != null) {
                        param.setResult(cachedImei);
                    }
                }
            });

        // Hook MonitorReporter to block anomaly reports
        HookUtils.hookAllMethods("com.tencent.qmethod.monitor.core.MonitorReporter", "report",
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    // Block all monitoring reports
                    param.setResult(null);
                }
            });

        HookUtils.hookAllMethods("com.tencent.qmethod.monitor.core.MonitorReporter", "getStrategyAndReport",
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    // Return a safe strategy that doesn't trigger checks
                    param.setResult(0);
                }
            });
    }
}
