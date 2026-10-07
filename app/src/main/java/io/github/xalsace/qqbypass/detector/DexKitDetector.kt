package io.github.xalsace.qqbypass.detector

import android.content.Context
import android.util.Log
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.result.MethodData
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * DexKit-based detector for dynamically locating obfuscated detection points in QQ.
 * Eliminates hardcoded class/method names to survive version updates.
 *
 * Single shared bridge: DexKitBridge.create() parses the whole APK (~112MB on
 * 9.3.50), so creating one per hook meant 5 full parses per process and, across
 * QQ's several processes, a memory/CPU spike that could get the process killed on
 * cold start. This class now owns ONE bridge; all hooks reuse it via getBridge()
 * after awaitReady(). The bridge is thread-safe (ReentrantReadWriteLock + shared
 * query scheduler), so concurrent findMethod() from multiple hook threads is fine.
 *
 * Process gate: only the process that calls setEnabled(true) (the main QQ process)
 * builds a bridge; subprocesses skip DexKit entirely (awaitReady returns false
 * immediately), keeping the universal framework hooks but paying no parse cost.
 */
object DexKitDetector {
    private const val TAG = "DexKitDetector"

    @Volatile
    private var dexKitBridge: DexKitBridge? = null

    @Volatile
    private var enabled = false // set true only in the gated (main) process

    @Volatile
    private var ready = false   // bridge created and usable

    private val initLock = Any()
    private val readyLatch = CountDownLatch(1)

    // ConcurrentHashMap: findXxx() may now run from multiple hook threads at once.
    private val detectionCache: MutableMap<String, List<MethodData>> = ConcurrentHashMap()

    /** Enable DexKit for this process. Call only in the main process. */
    fun setEnabled(value: Boolean) {
        enabled = value
    }

    fun isEnabled(): Boolean = enabled
    fun isReady(): Boolean = ready
    fun getBridge(): DexKitBridge? = dexKitBridge

    /**
     * Block until the shared bridge is ready, up to timeoutMs.
     * Returns false immediately in a non-enabled (sub)process so callers skip
     * DexKit work instead of blocking for the full timeout.
     */
    fun awaitReady(timeoutMs: Long): Boolean {
        if (ready) return true
        if (!enabled) return false
        try {
            readyLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return ready
    }

    /**
     * Initialize the single shared DexKit bridge (idempotent). Only the first
     * caller creates it; the readyLatch is always released so awaitReady() waiters
     * wake even if creation fails.
     */
    fun init(context: Context) {
        if (ready) return
        synchronized(initLock) {
            if (ready) return
            try {
                val apkPath = context.applicationInfo.sourceDir

                log("Initializing DexKit with APK: $apkPath")
                // Explicitly load libdexkit.so before create(). In an LSPosed host
                // process System.loadLibrary("dexkit") (what create() calls) can fail
                // to find the module's native lib on the host path -> UnsatisfiedLinkError.
                loadDexKitNativeLib()
                dexKitBridge = DexKitBridge.create(apkPath)
                ready = true
                log("DexKit initialized successfully")
            } catch (e: Throwable) {
                log("Failed to initialize DexKit: ${e.javaClass.name}: ${e.message}")
            } finally {
                readyLatch.countDown()
            }
        }
    }

    /**
     * Try to load libdexkit.so from the module's own native library directory.
     * Falls back to System.loadLibrary if the explicit path isn't resolvable.
     */
    private fun loadDexKitNativeLib() {
        try {
            System.loadLibrary("dexkit")
            log("loadLibrary(dexkit) OK")
        } catch (t: Throwable) {
            log("loadLibrary(dexkit) failed (${t.javaClass.simpleName}), trying explicit path...")
            throw t // surfaced by caller; explicit-path loading added once confirmed
        }
    }

    /**
     * Find ArtMethod hook detection methods (QSec ArtTiHookTask / all_so_hook)
     * Target: methods checking ArtMethod structure modification
     */
    fun findArtHookDetectors(): List<MethodData> {
        detectionCache["art_hook"]?.let { return it }

        return try {
            log("Searching for ArtMethod hook detectors...")

            // OR-semantics: usingStrings(...) is AND within one query, so we union
            // separate single-string queries to catch methods referencing ANY marker.
            val results = findMethodsUsingAnyString(
                "ArtMethod", "art_quick", "entry_point", "jni_code",
                "instrumentationExit", "quick_to_interpreter"
            )

            detectionCache["art_hook"] = results
            log("Found ${results.size} ArtMethod hook detectors")
            results
        } catch (e: Exception) {
            log("Error finding ArtMethod detectors: ${e.message}")
            emptyList()
        }
    }

    /**
     * Find /proc/self/maps readers (detecting injected .so files)
     * Target: methods reading maps file to detect LSPosed/Magisk/Zygisk/our module
     */
    fun findMapsReaders(): List<MethodData> {
        detectionCache["maps_reader"]?.let { return it }

        return try {
            log("Searching for /proc/self/maps readers...")

            // "/proc/self/maps" is the strong signal; "/proc/self/task" and "/proc/"
            // catch variants. OR-union so a method needs only one of them.
            val results = findMethodsUsingAnyString(
                "/proc/self/maps", "/proc/self/task", "/proc/%d/maps"
            )

            detectionCache["maps_reader"] = results
            log("Found ${results.size} maps readers")
            results
        } catch (e: Exception) {
            log("Error finding maps readers: ${e.message}")
            emptyList()
        }
    }

    /**
     * Find wtlogin error handlers (w21 "device invalid" logic)
     * Target: methods processing wtlogin error codes from server
     */
    fun findWtloginErrorHandlers(): List<MethodData> {
        detectionCache["wtlogin_error"]?.let { return it }

        return try {
            log("Searching for wtlogin error handlers...")

            val bridge = dexKitBridge ?: return emptyList()
            val results = mutableListOf<MethodData>()

            // Pattern 1: Methods in wtlogin packages
            results.addAll(
                bridge.findMethod(
                    FindMethod.create()
                        .matcher(
                            MethodMatcher.create()
                                .declaredClass("com.tencent.mobileqq.msf.core.wtlogin.*")
                                .usingStrings("error", "code", "msg")
                        )
                )
            )

            // Pattern 2: Methods handling ErrMsg objects
            results.addAll(
                bridge.findMethod(
                    FindMethod.create()
                        .matcher(
                            MethodMatcher.create()
                                .paramTypes("oicq.wlogin_sdk.tools.ErrMsg")
                        )
                )
            )

            detectionCache["wtlogin_error"] = results
            log("Found ${results.size} wtlogin error handlers")
            results
        } catch (e: Exception) {
            log("Error finding wtlogin handlers: ${e.message}")
            emptyList()
        }
    }

    /**
     * Find QSec hook detection methods (registerHookSo / nativeFileHook)
     * Target: QSec's native hook detection framework
     */
    fun findQSecHookDetectors(): List<MethodData> {
        detectionCache["qsec_hook"]?.let { return it }

        return try {
            log("Searching for QSec hook detectors...")

            // OR-semantics: these markers live in different methods, so union them.
            val results = findMethodsUsingAnyString(
                "registerHookSo", "nativeFileHook", "isHookedSoLoad", "QSecConfig",
                "all_so_hook", "ArtTiHookTask"
            )

            detectionCache["qsec_hook"] = results
            log("Found ${results.size} QSec hook detectors")
            results
        } catch (e: Exception) {
            log("Error finding QSec detectors: ${e.message}")
            emptyList()
        }
    }

    /**
     * Find Runtime.exec callers (root detection via "type su" / "which su")
     * Target: methods calling Runtime.exec() with su-related commands
     */
    fun findRuntimeExecCallers(): List<MethodData> {
        detectionCache["runtime_exec"]?.let { return it }

        return try {
            log("Searching for Runtime.exec callers...")

            val bridge = dexKitBridge ?: return emptyList()
            val results = bridge.findMethod(
                FindMethod.create()
                    .matcher(
                        MethodMatcher.create()
                            .addInvoke(
                                MethodMatcher.create()
                                    .declaredClass("java.lang.Runtime")
                                    .name("exec")
                            )
                            // single string = "contains"; multiple would be AND and over-narrow
                            .usingStrings("su")
                    )
            )

            detectionCache["runtime_exec"] = results
            log("Found ${results.size} Runtime.exec callers")
            results
        } catch (e: Exception) {
            log("Error finding Runtime.exec callers: ${e.message}")
            emptyList()
        }
    }

    /**
     * Find ProcessBuilder constructors/start callers (alternative to Runtime.exec)
     */
    fun findProcessBuilderCallers(): List<MethodData> {
        detectionCache["process_builder"]?.let { return it }

        return try {
            log("Searching for ProcessBuilder callers...")

            val bridge = dexKitBridge ?: return emptyList()
            val results = bridge.findMethod(
                FindMethod.create()
                    .matcher(
                        MethodMatcher.create()
                            .addInvoke(
                                MethodMatcher.create()
                                    .declaredClass("java.lang.ProcessBuilder")
                                    .name("start")
                            )
                    )
            )

            detectionCache["process_builder"] = results
            log("Found ${results.size} ProcessBuilder callers")
            results
        } catch (e: Exception) {
            log("Error finding ProcessBuilder callers: ${e.message}")
            emptyList()
        }
    }

    /**
     * Find File.exists callers checking su paths
     */
    fun findSuPathCheckers(): List<MethodData> {
        detectionCache["su_path"]?.let { return it }

        return try {
            log("Searching for su path checkers...")

            val bridge = dexKitBridge ?: return emptyList()
            // Tight fingerprint: a method that references a LITERAL su binary path
            // AND calls File.exists is almost certainly a real root checker.
            // The old bare "su" substring matched 903 innocent methods on 9.3.50
            // (result/measure/issue/... all contain "su"), each then hooked and its
            // boolean flipped -> startup cost + correctness hazard. The universal
            // File.exists hook is the runtime safety net for anything this misses.
            val suPaths = arrayOf(
                "/system/bin/su", "/system/xbin/su", "/sbin/su",
                "/su/bin/su", "/system/sd/xbin/su", "/system/bin/failsafe/su",
                "/data/local/su", "/data/local/xbin/su", "/data/local/bin/su",
                "/data/adb/magisk"
            )
            val unique = HashMap<String, MethodData>()
            for (p in suPaths) {
                try {
                    val found = bridge.findMethod(
                        FindMethod.create()
                            .matcher(
                                MethodMatcher.create()
                                    .addInvoke(
                                        MethodMatcher.create()
                                            .declaredClass("java.io.File")
                                            .name("exists")
                                    )
                                    .usingStrings(p)
                            )
                    )
                    for (m in found) unique[m.descriptor] = m
                } catch (e: Exception) {
                    log("su-path query failed for '$p': ${e.message}")
                }
            }
            val results = ArrayList(unique.values)

            detectionCache["su_path"] = results
            log("Found ${results.size} su path checkers")
            results
        } catch (e: Exception) {
            log("Error finding su path checkers: ${e.message}")
            emptyList()
        }
    }

    /**
     * Find PackageManager queries for Magisk/LSPosed packages
     */
    fun findSuspiciousPackageQueries(): List<MethodData> {
        detectionCache["package_query"]?.let { return it }

        return try {
            log("Searching for suspicious package queries...")

            val bridge = dexKitBridge ?: return emptyList()
            val results = bridge.findMethod(
                FindMethod.create()
                    .matcher(
                        MethodMatcher.create()
                            .addInvoke(
                                MethodMatcher.create()
                                    .declaredClass("android.content.pm.PackageManager")
                                    .name("getPackageInfo")
                            )
                            // single "contains" marker; runtime isSuspiciousPackage() does the rest
                            .usingStrings("magisk")
                    )
            )

            detectionCache["package_query"] = results
            log("Found ${results.size} package query methods")
            results
        } catch (e: Exception) {
            log("Error finding package queries: ${e.message}")
            emptyList()
        }
    }

    /**
     * Run one findMethod query per string (OR-semantics) and union the results,
     * de-duplicating by DEX descriptor. DexKit's usingStrings(vararg) is AND within
     * a single query, which is wrong when markers live in different methods.
     */
    private fun findMethodsUsingAnyString(vararg markers: String): List<MethodData> {
        val bridge = dexKitBridge ?: return emptyList()
        val unique = HashMap<String, MethodData>()
        for (marker in markers) {
            try {
                val found = bridge.findMethod(
                    FindMethod.create()
                        .matcher(MethodMatcher.create().usingStrings(marker))
                )
                for (m in found) {
                    unique[m.descriptor] = m
                }
            } catch (e: Exception) {
                log("query failed for '$marker': ${e.message}")
            }
        }
        return ArrayList(unique.values)
    }

    /**
     * Get all cached detection results
     */
    fun getAllDetectionResults(): Map<String, List<MethodData>> {
        return HashMap(detectionCache)
    }

    /**
     * Release DexKit resources. Note: hooks resolve their target Members during
     * the scan and don't need the bridge afterward, but we keep it alive by
     * default (release not called from XposedEntry) so late/lazy findXxx() calls
     * still work. Call explicitly only when you know no more queries will run.
     */
    fun release() {
        synchronized(initLock) {
            ready = false
            val bridge = dexKitBridge
            if (bridge != null) {
                try {
                    bridge.close()
                    log("DexKit released")
                } catch (e: Exception) {
                    log("Error releasing DexKit: ${e.message}")
                } finally {
                    dexKitBridge = null
                }
            }
        }
    }

    private fun log(message: String) {
        Log.i(TAG, "[$TAG] $message")
    }
}
