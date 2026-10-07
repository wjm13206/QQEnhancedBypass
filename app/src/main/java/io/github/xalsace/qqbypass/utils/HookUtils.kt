package io.github.xalsace.qqbypass.utils

import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.xalsace.qqbypass.config.HookConfig
import java.lang.reflect.Executable
import java.lang.reflect.Member

/**
 * Utility class for common libxposed hook operations
 */
object HookUtils {

    private const val TAG = "QQEnhancedBypass"

    private var module: XposedModule? = null

    fun init(module: XposedModule) {
        this.module = module
    }

    /**
     * Hook all overloads of [methodName] declared on [clazz].
     */
    fun hookMethod(
        clazz: Class<*>,
        methodName: String,
        hooker: XposedInterface.Hooker,
        mode: XposedInterface.ExceptionMode = XposedInterface.ExceptionMode.DEFAULT
    ) {
        try {
            var hooked = 0
            for (m in clazz.declaredMethods) {
                if (m.name != methodName) continue
                hookExecutable(m, hooker, mode)
                hooked++
            }
            if (hooked > 0) log("Hooked: ${clazz.name}.$methodName x$hooked")
            else log("No such method: ${clazz.name}.$methodName")
        } catch (t: Throwable) {
            if (HookConfig.VERBOSE_LOGGING) {
                log("Failed to hook: ${clazz.name}.$methodName - ${t.message}")
            }
        }
    }

    /**
     * Hook all overloads of [methodName] on the named class.
     */
    fun hookAllMethods(
        className: String,
        methodName: String,
        hooker: XposedInterface.Hooker,
        mode: XposedInterface.ExceptionMode = XposedInterface.ExceptionMode.DEFAULT
    ) {
        val loader = HookConfig.classLoader ?: run {
            log("Failed to hook all methods: $className.$methodName - no classloader")
            return
        }
        val clazz = findClassOrNull(className, loader) ?: run {
            if (HookConfig.VERBOSE_LOGGING) log("Class not found, skip: $className")
            return
        }
        hookMethod(clazz, methodName, hooker, mode)
    }

    /**
     * Hook a single resolved member (e.g. from DexKit's getMethodInstance).
     */
    fun hookMember(
        member: Member,
        hooker: XposedInterface.Hooker,
        mode: XposedInterface.ExceptionMode = XposedInterface.ExceptionMode.DEFAULT
    ) {
        val executable = member as? Executable ?: run {
            log("Skip non-executable member: $member")
            return
        }
        hookExecutable(executable, hooker, mode)
    }

    private fun hookExecutable(
        executable: Executable,
        hooker: XposedInterface.Hooker,
        mode: XposedInterface.ExceptionMode
    ) {
        try {
            val m = module ?: run {
                log("Hook module not initialized, skip: $executable")
                return
            }
            m.hook(executable).setExceptionMode(mode).intercept(hooker)
            log("Hooked: $executable")
        } catch (t: Throwable) {
            if (HookConfig.VERBOSE_LOGGING) {
                log("Failed to hook: $executable - ${t.message}")
            }
        }
    }

    fun findClassOrNull(className: String, loader: ClassLoader): Class<*>? {
        return try {
            Class.forName(className, false, loader)
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Check if class exists (uses the module classloader).
     */
    fun classExists(className: String): Boolean {
        val loader = HookConfig.classLoader ?: return false
        return findClassOrNull(className, loader) != null
    }

    fun log(message: String) {
        if (HookConfig.VERBOSE_LOGGING) {
            // 优先写 LSPosed 框架日志（LSPosed 管理器可见），module 未初始化时回退 logcat
            val m = module
            if (m != null) m.log(Log.INFO, TAG, message)
            else Log.i(TAG, message)
        }
    }
}
