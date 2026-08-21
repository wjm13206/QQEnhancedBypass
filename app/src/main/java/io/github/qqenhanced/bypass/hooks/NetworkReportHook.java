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

    // Only pure telemetry/report commands. Login-critical commands
    // (wtlogin.*, turing, DeviceTokenV3) are intentionally NOT blocked -
    // blocking them breaks the QQ login/registration handshake and prevents
    // entry to the main UI.
    private static final String[] BLOCK_COMMANDS = {
        "trpc.o3.report",
        "trpc.o3.mobile_security",
        "trpc.ilive_cdn.report",
        "OidbSvc.0xd79"  // Device report
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
                        if (param.args.length < 1 || !(param.args[0] instanceof String)) return;

                        String command = (String) param.args[0];
                        if (shouldBlock(command)) {
                            // Only override the return value if type-safe. setResult(null)
                            // crashes (uncatchable ClassCastException in proceed()) when the
                            // method returns a primitive.
                            Class<?> returnType = ((java.lang.reflect.Method) param.method).getReturnType();
                            if (returnType.isPrimitive() && returnType != void.class) {
                                // Primitive return - blocking is unsafe, observe only.
                                XposedEntry.log("ChannelProxyExt (not blocked, primitive return "
                                    + returnType.getName() + "): " + command);
                                return;
                            }

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
        // Real path (QQ 9.3.50): com.tencent.mobileqq.msf.core.MsfCore
        HookUtils.hookAllMethods("com.tencent.mobileqq.msf.core.MsfCore", "sendSsoMsg",
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length < 1) return;

                    Object toServiceMsg = param.args[0];
                    Object cmdObj = XposedHelpers.callMethod(toServiceMsg, "getServiceCmd");
                    String command = cmdObj == null ? null : cmdObj.toString();

                    if (shouldBlock(command)) {
                        // Type-safe block: only override the return value if the hooked
                        // method actually returns int/long/void. Setting a wrong-typed
                        // result triggers an uncatchable ClassCastException in Xposed's
                        // proceed() and crashes the process.
                        Class<?> returnType = ((java.lang.reflect.Method) param.method).getReturnType();
                        if (returnType == int.class) {
                            int seq = 0;
                            try {
                                seq = (int) XposedHelpers.callMethod(toServiceMsg, "getRequestSsoSeq");
                            } catch (Throwable ignored) { }
                            param.setResult(seq);
                            XposedEntry.log("Blocked MsfCore: " + command);
                        } else if (returnType == long.class) {
                            long seq = 0L;
                            try {
                                seq = ((Number) XposedHelpers.callMethod(toServiceMsg, "getRequestSsoSeq")).longValue();
                            } catch (Throwable ignored) { }
                            param.setResult(seq);
                            XposedEntry.log("Blocked MsfCore: " + command);
                        } else if (returnType == void.class) {
                            param.setResult(null);
                            XposedEntry.log("Blocked MsfCore: " + command);
                        } else {
                            // Unknown return type - observe only, do not risk a crash.
                            XposedEntry.log("MsfCore (not blocked, return type "
                                + returnType.getName() + "): " + command);
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
