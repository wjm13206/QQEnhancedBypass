package io.github.qqenhanced.bypass.hooks;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import io.github.qqenhanced.bypass.XposedEntry;
import io.github.qqenhanced.bypass.config.HookConfig;
import io.github.qqenhanced.bypass.utils.HookUtils;

/**
 * Runtime Monitor Bypass
 *
 * Handles Pandora RuntimeMonitor which monitors:
 * - Runtime.exec() calls for suspicious commands (ip, pm, getprop, sh)
 * - Command outputs via ProcessBuilder
 * - System command execution patterns
 *
 * Also handles various command processors:
 * - IPProcessor
 * - PackageManagerProcessor
 * - PropProcessor
 */
public class RuntimeMonitorHook {

    private static final String[] MONITORED_COMMANDS = {
        "ip", "pm", "getprop", "sh", "/system/bin/sh",
        "ps", "cat", "mount", "which", "su"
    };

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!HookConfig.ENABLE_RUNTIME_BYPASS) return;

        XposedEntry.log("Initializing Runtime Monitor Bypass");

        // Hook 1: Pandora RuntimeMonitor
        hookPandoraRuntimeMonitor();

        // Hook 2: Command processors
        hookCommandProcessors();

        // Hook 3: ProcessBuilder monitoring
        hookProcessBuilder();
    }

    private static void hookPandoraRuntimeMonitor() {
        // CORRECTED for QQ 9.3.50 (verified by parsing the dex class_data):
        // RuntimeMonitor has NO monitor()/onExec(). Its real methods are:
        //   exec (x6 overloads), execute, isInspect.
        // The previous monitor/onExec hooks matched ZERO methods (silent no-op:
        // XposedBridge.hookAllMethods logs success even when it hooks nothing).
        //
        // exec() internally delegates to Runtime.exec, which we ALREADY hook at the
        // framework level (and safely fake output there). So we do NOT override exec's
        // return value here (it returns a Process - wrong-typed setResult would crash,
        // same class of bug as getStrategyAndReport). We OBSERVE only, to surface what
        // commands actually flow through Pandora's monitor - key diagnostics for the
        // delayed risk-control detection.
        HookUtils.hookAllMethods("com.tencent.qmethod.pandoraex.monitor.RuntimeMonitor", "exec",
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    logCommandArgs("RuntimeMonitor.exec", param.args);
                }
            });

        HookUtils.hookAllMethods("com.tencent.qmethod.pandoraex.monitor.RuntimeMonitor", "execute",
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    logCommandArgs("RuntimeMonitor.execute", param.args);
                }
            });
    }

    private static void hookCommandProcessors() {
        // CORRECTED for QQ 9.3.50: the three RuntimeMonitor$XxxProcessor classes have
        // NO process() method. They implement RuntimeProcessor with transform() + getType().
        // The previous .process hooks were silent no-ops (0 methods matched).
        // transform() returns processed command output QQ consumes, so OBSERVE only
        // (no setResult -> no ClassCastException / breakage).
        String[] processors = {
            "com.tencent.qmethod.pandoraex.monitor.RuntimeMonitor$IPProcessor",
            "com.tencent.qmethod.pandoraex.monitor.RuntimeMonitor$PackageManagerProcessor",
            "com.tencent.qmethod.pandoraex.monitor.RuntimeMonitor$PropProcessor"
        };
        for (String processor : processors) {
            final String shortName = processor.substring(processor.lastIndexOf('$') + 1);
            HookUtils.hookAllMethods(processor, "transform",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        logCommandArgs(shortName + ".transform", param.args);
                    }
                });
        }
    }

    private static void logCommandArgs(String tag, Object[] args) {
        if (args == null) return;
        for (Object arg : args) {
            if (arg == null) continue;
            String s;
            if (arg instanceof String[]) {
                s = java.util.Arrays.toString((String[]) arg);
            } else {
                s = arg.toString();
            }
            if (isMonitoredCommand(s)) {
                XposedEntry.log(tag + " observed monitored command: " + s);
            }
        }
    }

    private static void hookProcessBuilder() {
        // Hook ProcessBuilder.start() to monitor command execution
        HookUtils.hookMethod(ProcessBuilder.class, "start",
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    ProcessBuilder pb = (ProcessBuilder) param.thisObject;
                    java.util.List<String> command = pb.command();

                    if (command != null && !command.isEmpty()) {
                        String cmd = command.get(0);

                        // Log monitored commands
                        if (isMonitoredCommand(cmd)) {
                            XposedEntry.log("ProcessBuilder command intercepted: " + cmd);
                            // We don't block it, just log - blocking might break functionality
                        }
                    }
                }
            });
    }

    private static boolean isMonitoredCommand(String command) {
        if (command == null) return false;

        for (String monitored : MONITORED_COMMANDS) {
            if (command.contains(monitored)) {
                return true;
            }
        }
        return false;
    }
}
