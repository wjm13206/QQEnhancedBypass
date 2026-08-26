package io.github.xalsace.qqbypass.hooks;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import io.github.xalsace.qqbypass.XposedEntry;
import io.github.xalsace.qqbypass.config.HookConfig;
import io.github.xalsace.qqbypass.utils.HookUtils;

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
        // ProcessBuilder.start() is the UNIVERSAL choke point for command execution:
        // Runtime.exec() and Pandora RuntimeMonitor.exec() both funnel through it
        // (verified in runtime logs - every `type su` probe passes here).
        //
        // EVIDENCE (kick log 22_08-18-33-32): QQ ran `/system/bin/sh -c type su`
        // 130+ times over ~97 min. It was only OBSERVED, never blocked, so `type su`
        // returned the real result (su found) every time -> root reported -> server
        // force-logout. This neutralizes those probes.
        HookUtils.hookMethod(ProcessBuilder.class, "start",
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    ProcessBuilder pb = (ProcessBuilder) param.thisObject;
                    java.util.List<String> command = pb.command();
                    if (command == null || command.isEmpty()) return;

                    String joined = joinCommand(command);

                    if (isRootProbe(joined)) {
                        // Rewrite the command IN PLACE to a harmless no-op that yields
                        // empty stdout + non-zero exit - exactly what an UNROOTED device
                        // returns for `type su` / `which su` (su not found). We let the
                        // real start() proceed with the neutralized command, so there is
                        // no fake Process object and no recursion into this hook.
                        pb.command("/system/bin/sh", "-c", "exit 1");
                        XposedEntry.log("Neutralized ProcessBuilder root probe: " + joined);
                    } else if (isMonitoredCommand(joined)) {
                        // Non-root monitored command: observe only, don't break it.
                        XposedEntry.log("ProcessBuilder command intercepted: " + joined);
                    }
                }
            });
    }

    private static String joinCommand(java.util.List<String> command) {
        StringBuilder sb = new StringBuilder();
        for (String part : command) {
            if (part == null) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(part);
        }
        return sb.toString();
    }

    /**
     * Unambiguous root / root-manager probes that are safe to neutralize.
     * Kept narrow (token-level "su" + known root-manager names) so legitimate
     * shell commands QQ needs are never rewritten.
     */
    private static boolean isRootProbe(String joined) {
        if (joined == null) return false;
        String s = joined.toLowerCase();
        // Known root-manager names + explicit su binary paths (verified present in
        // QQ 9.3.50 dex: /system/bin/su, /system/xbin/su, /sbin/su, /su/bin/su).
        if (s.contains("magisk") || s.contains("supersu") || s.contains("superuser")
                || s.contains("busybox") || s.contains("ksud")
                || s.contains("which su") || s.contains("type su") || s.contains("command -v su")
                || s.contains("/system/bin/su") || s.contains("/system/xbin/su")
                || s.contains("/sbin/su") || s.contains("/su/bin")
                || s.contains("/data/local/su")) {
            return true;
        }
        // standalone "su" token (matches `su`, `type su`, `sh -c su`; not `sudo`, `pull`,
        // and not benign paths that merely contain the substring "su").
        for (String tok : s.split("\\s+")) {
            if (tok.equals("su")) return true;
        }
        return false;
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
