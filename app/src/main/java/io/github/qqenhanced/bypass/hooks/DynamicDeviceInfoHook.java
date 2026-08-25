package io.github.qqenhanced.bypass.hooks;

import android.os.Build;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.List;

import io.github.qqenhanced.bypass.XposedEntry;
import io.github.qqenhanced.bypass.config.HookConfig;
import io.github.qqenhanced.bypass.detector.DexKitDetector;

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
                Thread.sleep(5000); // Wait for DexKit scan

                hookDeviceInfoReaders(lpparam);

            } catch (Exception e) {
                XposedEntry.log("Dynamic device info hook failed: " + e.getMessage());
            }
        }).start();
    }

    private static void hookDeviceInfoReaders(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedEntry.log("DynamicDevice: Searching for device info readers...");

            // Use DexKit to find methods reading device identifiers
            android.content.Context context = (android.content.Context)
                XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader),
                    "currentApplication");

            if (context == null) {
                XposedEntry.log("DynamicDevice: Context not ready, skipping");
                return;
            }

            String apkPath = context.getApplicationInfo().sourceDir;
            DexKitBridge bridge = DexKitBridge.create(apkPath);

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

            bridge.close();

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
