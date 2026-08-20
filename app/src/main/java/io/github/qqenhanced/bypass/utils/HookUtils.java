package io.github.qqenhanced.bypass.utils;

import android.util.Log;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import io.github.qqenhanced.bypass.config.HookConfig;

import java.lang.reflect.Method;

/**
 * Utility class for common Xposed hook operations
 */
public class HookUtils {

    private static final String TAG = "QQEnhancedBypass";

    /**
     * Safe hook with error handling
     */
    public static void hookMethod(String className, String methodName, Object... parameterTypesAndCallback) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, HookConfig.getClassLoader());
            XposedHelpers.findAndHookMethod(clazz, methodName, parameterTypesAndCallback);
            log("Hooked: " + className + "." + methodName);
        } catch (Throwable t) {
            if (HookConfig.VERBOSE_LOGGING) {
                log("Failed to hook: " + className + "." + methodName + " - " + t.getMessage());
            }
        }
    }

    /**
     * Hook method with class object
     */
    public static void hookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        try {
            XposedHelpers.findAndHookMethod(clazz, methodName, parameterTypesAndCallback);
            log("Hooked: " + clazz.getName() + "." + methodName);
        } catch (Throwable t) {
            if (HookConfig.VERBOSE_LOGGING) {
                log("Failed to hook: " + clazz.getName() + "." + methodName + " - " + t.getMessage());
            }
        }
    }

    /**
     * Hook all methods with specific name
     */
    public static void hookAllMethods(String className, String methodName, XC_MethodHook callback) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, HookConfig.getClassLoader());
            XposedBridge.hookAllMethods(clazz, methodName, callback);
            log("Hooked all methods: " + className + "." + methodName);
        } catch (Throwable t) {
            if (HookConfig.VERBOSE_LOGGING) {
                log("Failed to hook all methods: " + className + "." + methodName + " - " + t.getMessage());
            }
        }
    }

    /**
     * Hook constructor
     */
    public static void hookConstructor(String className, Object... parameterTypesAndCallback) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, HookConfig.getClassLoader());
            XposedHelpers.findAndHookConstructor(clazz, parameterTypesAndCallback);
            log("Hooked constructor: " + className);
        } catch (Throwable t) {
            if (HookConfig.VERBOSE_LOGGING) {
                log("Failed to hook constructor: " + className + " - " + t.getMessage());
            }
        }
    }

    /**
     * Check if class exists
     */
    public static boolean classExists(String className) {
        try {
            XposedHelpers.findClass(className, HookConfig.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void log(String message) {
        if (HookConfig.VERBOSE_LOGGING) {
            Log.i(TAG, message);
        }
    }
}
