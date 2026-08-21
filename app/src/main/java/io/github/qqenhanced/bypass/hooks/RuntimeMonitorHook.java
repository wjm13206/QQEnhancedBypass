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
        // IMPORTANT: These Pandora RuntimeMonitor methods are WRAPPERS that QQ calls
        // to get real data (monitor() returns the Process from Runtime.exec, etc.).
        // They are a privacy-compliance recorder, NOT a hook/root detector, so nulling
        // their return value does not hide anything - it only feeds QQ null/wrong-typed
        // values and risks the same ClassCastException that getStrategyAndReport caused.
        // The real runtime bypass is handled by the native layer + Runtime.exec hook.
        // So we ONLY observe here, never override the return value.
        // Real path (QQ 9.3.50): com.tencent.qmethod.pandoraex.monitor.RuntimeMonitor
        HookUtils.hookAllMethods("com.tencent.qmethod.pandoraex.monitor.RuntimeMonitor", "onExec",
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    // Observe only - do NOT setResult (return type unknown / on data path)
                    if (param.args != null && param.args.length > 0 && param.args[0] != null) {
                        XposedEntry.log("RuntimeMonitor.onExec observed: " + param.args[0]);
                    }
                }
            });

        // Note: shouldMonitor() no longer exists in QQ 9.3.50 - removed.
        // Note: monitor() intentionally NOT overridden - it returns data QQ needs.
    }

    private static void hookCommandProcessors() {
        // Processors are inner classes of RuntimeMonitor (RuntimeMonitor$XxxProcessor).
        // process() returns processed command output that QQ consumes, so we observe
        // only and never override the return value (avoids ClassCastException + breakage).
        // Left intentionally empty: blocking these breaks QQ startup and provides no
        // real detection bypass (native layer already covers command interception).
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
