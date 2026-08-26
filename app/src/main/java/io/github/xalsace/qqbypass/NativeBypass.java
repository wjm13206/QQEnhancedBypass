package io.github.xalsace.qqbypass;

import android.util.Log;

/**
 * JNI bridge for native hooks
 *
 * This class loads the native library and provides interface
 * to initialize and check native hook status.
 */
public class NativeBypass {

    private static final String TAG = "NativeBypass";
    private static boolean libraryLoaded = false;

    static {
        try {
            // 先初始化 ByteHook 的 AAR（会加载 libbytehook.so）
            try {
                com.bytedance.android.bytehook.ByteHook.init();
                Log.i(TAG, "ByteHook AAR initialized");
            } catch (Throwable t) {
                Log.w(TAG, "ByteHook.init() failed, native hooks may not work: " + t.getMessage());
            }

            System.loadLibrary("native-bypass");
            libraryLoaded = true;
            Log.i(TAG, "Native library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native library", e);
            libraryLoaded = false;
        }
    }

    /**
     * Check if native library is loaded
     */
    public static boolean isLibraryLoaded() {
        return libraryLoaded;
    }

    /**
     * Initialize native hooks
     * This should be called early in QQ's lifecycle
     */
    public static void initNativeHooks() {
        if (!libraryLoaded) {
            Log.e(TAG, "Native library not loaded, cannot init hooks");
            return;
        }

        try {
            initNativeHooksNative();
            Log.i(TAG, "Native hooks initialization started");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to init native hooks", t);
        }
    }

    /**
     * Check if native hooks are installed
     */
    public static boolean isHooksInstalled() {
        if (!libraryLoaded) {
            return false;
        }

        try {
            return isHooksInstalledNative();
        } catch (Throwable t) {
            Log.e(TAG, "Failed to check hook status", t);
            return false;
        }
    }

    /**
     * Get hook status message
     */
    public static String getHookStatus() {
        if (!libraryLoaded) {
            return "Native library not loaded";
        }

        try {
            return getHookStatusNative();
        } catch (Throwable t) {
            return "Error getting hook status: " + t.getMessage();
        }
    }

    /**
     * Notify Native layer about Java detection event
     * This enables Java → Native communication
     */
    public static void notifyDetection(String event, String data) {
        if (!libraryLoaded) {
            return;
        }

        try {
            notifyDetectionNative(event, data);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to notify native layer: " + t.getMessage());
        }
    }

    /**
     * Check if specific Native hook is active
     */
    public static boolean isHookActive(String hookName) {
        if (!libraryLoaded) {
            return false;
        }

        try {
            return isHookActiveNative(hookName);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Get detailed hook statistics
     */
    public static String getHookStatistics() {
        if (!libraryLoaded) {
            return "Native layer unavailable";
        }

        try {
            return getHookStatisticsNative();
        } catch (Throwable t) {
            return "Error: " + t.getMessage();
        }
    }

    // Native methods
    private static native void initNativeHooksNative();
    private static native boolean isHooksInstalledNative();
    private static native String getHookStatusNative();
    private static native void notifyDetectionNative(String event, String data);
    private static native boolean isHookActiveNative(String hookName);
    private static native String getHookStatisticsNative();
}
