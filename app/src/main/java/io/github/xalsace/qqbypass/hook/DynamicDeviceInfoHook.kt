package io.github.xalsace.qqbypass.hook

import io.github.xalsace.qqbypass.config.HookConfig
import io.github.xalsace.qqbypass.utils.HookUtils

/**
 * Dynamic device info collection monitor (Phase 1: observe-only).
 *
 * Logs (not blocks) device info collection for:
 * - TelephonyManager (IMEI/deviceId/phone number)
 * - Settings.Secure (Android ID)
 * - Build fields (serial/fingerprint)
 * - WifiManager (MAC)
 * - Display/Sensors (TuringFD signals)
 */
object DynamicDeviceInfoHook {

    fun hook() {
        val loader = HookConfig.classLoader ?: run {
            HookUtils.log("DynamicDeviceInfoHook: no classloader")
            return
        }

        // TelephonyManager: log deviceId/IMEI reads
        try {
            val tm = HookUtils.findClassOrNull("android.telephony.TelephonyManager", loader)
            if (tm != null) {
                for (m in arrayOf("getDeviceId", "getImei", "getMeid", "getLine1Number", "getSubscriberId", "getSimSerialNumber")) {
                    val methodName = m
                    try {
                        HookUtils.hookMethod(tm, m, { chain ->
                            val result = chain.proceed()
                            HookUtils.log("DeviceInfo: TelephonyManager.$methodName() -> " + mask(result))
                            result
                        })
                    } catch (t: Throwable) {
                        if (HookConfig.VERBOSE_LOGGING) HookUtils.log("Skip TelephonyManager.$methodName")
                    }
                }
            }
        } catch (t: Throwable) {
            HookUtils.log("DeviceInfo TelephonyManager hook failed: " + t.message)
        }

        // Settings.Secure.getString: log ANDROID_ID reads
        try {
            HookUtils.hookAllMethods("android.provider.Settings\$Secure", "getString", { chain ->
                val result = chain.proceed()
                val args = chain.args
                if (args.size >= 2 && "android_id" == args[1]) {
                    HookUtils.log("DeviceInfo: Settings.Secure.ANDROID_ID -> " + mask(result))
                }
                result
            })
        } catch (t: Throwable) {
            HookUtils.log("DeviceInfo Settings.Secure hook failed: " + t.message)
        }

        // Build.SERIAL / Build.getSerial: log
        try {
            HookUtils.hookAllMethods("android.os.Build", "getSerial", { chain ->
                val result = chain.proceed()
                HookUtils.log("DeviceInfo: Build.getSerial() -> " + mask(result))
                result
            })
        } catch (t: Throwable) {
            if (HookConfig.VERBOSE_LOGGING) HookUtils.log("DeviceInfo Build.getSerial hook skipped")
        }

        // WifiInfo.getMacAddress: log
        try {
            HookUtils.hookAllMethods("android.net.wifi.WifiInfo", "getMacAddress", { chain ->
                val result = chain.proceed()
                HookUtils.log("DeviceInfo: WifiInfo.getMacAddress() -> " + mask(result))
                result
            })
        } catch (t: Throwable) {
            if (HookConfig.VERBOSE_LOGGING) HookUtils.log("DeviceInfo WifiInfo hook skipped")
        }
    }

    private fun mask(value: Any?): String {
        if (value == null) return "null"
        val s = value.toString()
        return if (s.length <= 4) "****" else s.substring(0, 2) + "****" + s.substring(s.length - 2)
    }
}
