package io.github.xalsace.qqbypass.hooks;

import android.os.Build;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.List;

import io.github.xalsace.qqbypass.XposedEntry;
import io.github.xalsace.qqbypass.config.HookConfig;
import io.github.xalsace.qqbypass.detector.DexKitDetector;

/**
 * DexKit-based Device Info Consistency Protection
 * Ensures device fingerprint remains stable across hook operations
 */
public class DynamicDeviceInfoHook {

    // Cache real device info to maintain consistency
    private static final String REAL_IMEI = null; // Let system provide real value
    private static final String REAL_ANDROID_ID = null;
    private static final String REAL_SERIAL = null;

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!HookConfig.ENABLE_DEVICE_SPOOF) return;

        XposedEntry.log("Initializing DexKit-based Device Info Protection");

        // Note: Device spoofing is disabled (FAKE_* = null in HookConfig)
        // This module only ensures consistency by monitoring cross-validation attempts

        new Thread(() -> {
            try {
                // Wait for the single shared bridge instead of building our own.
                // Returns false immediately in a subprocess (DexKit gated off).
                if (!DexKitDetector.awaitReady(30000)) {
                    XposedEntry.log("DynamicDevice: DexKit not available, skipping");
                    return;
                }
                hookDeviceInfoReaders(lpparam);

            } catch (Exception e) {
                XposedEntry.log("Dynamic device info hook failed: " + e.getMessage());
            }
        }).start();
    }

    private static void hookDeviceInfoReaders(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedEntry.log("DynamicDevice: Searching for device info readers...");

            // Reuse the shared bridge (owned by DexKitDetector); do NOT close it here.
            DexKitBridge bridge = DexKitDetector.getBridge();
            if (bridge == null) {
                XposedEntry.log("DynamicDevice: shared bridge null, skipping");
                return;
            }

            // Find methods calling TelephonyManager.getDeviceId (IMEI)
            List<MethodData> imeiReaders = bridge.findMethod(
                FindMethod.create()
                    .matcher(MethodMatcher.create()
                        .addInvoke(MethodMatcher.create()
                            .declaredClass("android.telephony.TelephonyManager")
                            .name("getDeviceId"))
                    )
            );

            // Find methods calling Settings.Secure.getString(ANDROID_ID)
            List<MethodData> androidIdReaders = bridge.findMethod(
                FindMethod.create()
                    .matcher(MethodMatcher.create()
                        .addInvoke(MethodMatcher.create()
                            .declaredClass("android.provider.Settings$Secure")
                            .name("getString"))
                        .usingStrings("android_id")
                    )
            );

            // Find methods calling Build.getSerial()
            List<MethodData> serialReaders = bridge.findMethod(
                FindMethod.create()
                    .matcher(MethodMatcher.create()
                        .addInvoke(MethodMatcher.create()
                            .declaredClass("android.os.Build")
                            .name("getSerial"))
                    )
            );

            // Shared bridge - do NOT close (owned by DexKitDetector)

            XposedEntry.log("DynamicDevice: Found " + imeiReaders.size() + " IMEI readers");
            XposedEntry.log("DynamicDevice: Found " + androidIdReaders.size() + " Android ID readers");
            XposedEntry.log("DynamicDevice: Found " + serialReaders.size() + " Serial readers");

            // Hook all discovered readers to monitor cross-validation
            hookDeviceReaderList(lpparam, imeiReaders, "IMEI");
            hookDeviceReaderList(lpparam, androidIdReaders, "AndroidID");
            hookDeviceReaderList(lpparam, serialReaders, "Serial");

        } catch (Throwable e) {
            XposedEntry.log("Failed to hook device readers: "
                + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    private static void hookDeviceReaderList(XC_LoadPackage.LoadPackageParam lpparam,
                                             List<MethodData> readers, String type) {
        for (MethodData methodData : readers) {
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
                        // Monitor but don't modify - just log cross-validation attempts
                        Object result = param.getResult();
                        if (result != null) {
                            XposedEntry.log("Device info read (" + type + "): " + className);
                        }
                    }
                });

                XposedEntry.log("Hooked " + type + " reader: " + className + "." + methodName);

            } catch (Exception e) {
                XposedEntry.log("Failed to hook " + type + " reader: " + e.getMessage());
            }
        }
    }

}
