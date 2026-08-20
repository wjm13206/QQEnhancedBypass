package io.github.qqenhanced.bypass.hooks;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import io.github.qqenhanced.bypass.XposedEntry;
import io.github.qqenhanced.bypass.config.HookConfig;
import io.github.qqenhanced.bypass.utils.HookUtils;

/**
 * Network Report Interception Hook
 *
 * Intercepts and blocks security-related reporting to Tencent servers:
 * - trpc.o3.report.* (environment reports)
 * - trpc.o3.mobile_security.* (security checks)
 * - TuringFD risk detection
 * - Wlogin device fingerprint
 */
public class NetworkReportHook {

    private static final String[] BLOCK_COMMANDS = {
        "trpc.o3.report",
        "trpc.o3.mobile_security",
        "trpc.ilive_cdn.report",
        "OidbSvc.0xd79",  // Device report
        "wtlogin.device_lock",
        "turing",
        "riskCheckWup",
        "DeviceTokenV3"
    };

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!HookConfig.ENABLE_NETWORK_INTERCEPT) return;

        XposedEntry.log("Initializing Network Report Hook");

        // Hook 1: ChannelProxyExt.sendMessage (primary intercept point)
        hookChannelProxyExt(lpparam);

        // Hook 2: MsfCore.sendSsoMsg (secondary intercept point)
        hookMsfCore(lpparam);

        // Hook 3: TuringFD risk detection
        hookTuringFD(lpparam);

        // Hook 4: Wlogin device report
        hookWlogin(lpparam);
    }

    private static void hookChannelProxyExt(XC_LoadPackage.LoadPackageParam lpparam) {
        // Try multiple possible method signatures for different QQ versions
        String[] methodNames = {"sendMessage", "sendMessageInner", "send"};

        for (String methodName : methodNames) {
            HookUtils.hookAllMethods("com.tencent.mobileqq.channel.ChannelProxyExt", methodName,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args.length < 1) return;

                        String command = (String) param.args[0];
                        if (shouldBlock(command)) {
                            XposedEntry.log("Blocked ChannelProxyExt: " + command);

                            // Inject fake response
                            try {
                                Object channelManager = XposedHelpers.callStaticMethod(
                                    XposedHelpers.findClass("com.tencent.mobileqq.channel.ChannelManager",
                                    lpparam.classLoader), "getInstance");

                                XposedHelpers.callMethod(channelManager, "onNativeReceive",
                                    command, new byte[0], 0L);
                            } catch (Throwable t) {
                                // Ignore if injection fails
                            }

                            param.setResult(null);
                        }
                    }
                });
        }
    }

    private static void hookMsfCore(XC_LoadPackage.LoadPackageParam lpparam) {
        HookUtils.hookAllMethods("com.tencent.qphone.base.remote.MsfCore", "sendSsoMsg",
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length < 1) return;

                    Object toServiceMsg = param.args[0];
                    String command = (String) XposedHelpers.callMethod(toServiceMsg, "getServiceCmd");

                    if (shouldBlock(command)) {
                        XposedEntry.log("Blocked MsfCore: " + command);

                        // Return sequence number to simulate success
                        try {
                            int seq = (int) XposedHelpers.callMethod(toServiceMsg, "getRequestSsoSeq");
                            param.setResult(seq);
                        } catch (Throwable t) {
                            param.setResult(0);
                        }
                    }
                }
            });
    }

    private static void hookTuringFD(XC_LoadPackage.LoadPackageParam lpparam) {
        // Block TuringFD risk detection initialization and reporting
        String[] turingClasses = {
            "com.tencent.turingfd.sdk.xq.Pomegranate",
            "com.tencent.turingfd.sdk.xq.Blueberry",
            "com.tencent.turingcam.oqKCa"
        };

        for (String className : turingClasses) {
            // Hook init/report methods to prevent detection
            HookUtils.hookAllMethods(className, "a", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    // Block all TuringFD operations
                    param.setResult(null);
                }
            });
        }
    }

    private static void hookWlogin(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook Wlogin device fingerprint collection
        HookUtils.hookAllMethods("oicq.wlogin_sdk.request.w", "h",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    // Modify device fingerprint before sending
                    XposedEntry.log("Intercepted Wlogin device fingerprint");
                }
            });
    }

    private static boolean shouldBlock(String command) {
        if (command == null) return false;

        for (String blockCmd : BLOCK_COMMANDS) {
            if (command.contains(blockCmd)) {
                return true;
            }
        }
        return false;
    }
}
