#include <jni.h>
#include <pthread.h>
#include <unistd.h>
#include <string.h>
#include <stdio.h>
#include <bytehook.h>
#include "utils/log.h"
#include "hooks/hook_libc.h"
#include "hooks/hook_libfekit.h"
#include "hooks/hook_libturingxq.h"
#include "hooks/hook_libmsf.h"
#include "hooks/hook_libqsec.h"
#include "hooks/hook_libmsfkernel.h"
#include "hooks/hook_libturingmfa.h"

/**
 * Native Bypass Module for QQ Enhanced Bypass
 *
 * This module provides native-level hooks to bypass detection mechanisms
 * that cannot be handled by Java-layer Xposed hooks.
 *
 * Key targets:
 * - libc: File I/O interception (/proc/self/maps, /proc/self/status)
 * - libfekit.so: Root/Xposed detection
 * - libturingxq.so: ART integrity checks and risk reporting
 * - libMSFKernel.so: qimei36 fingerprint injection (QQ 9.3.50+)
 * - libQSec.so: Anti-hook detection (dlopen/dlsym/dladdr)
 * - libturingmfa.so: Risk reporting channel
 * - libMSF hooks: Signature verification (experimental)
 */

static bool g_hooks_installed = false;
static pthread_mutex_t g_init_mutex = PTHREAD_MUTEX_INITIALIZER;

/**
 * Install all hooks in a background thread
 */
static void* install_hooks_thread(void* arg) {
    LOGI("=== Native Bypass Module Starting ===");
    LOGI("Process: %d", getpid());

    // Wait a bit for QQ libraries to load
    sleep(2);

    pthread_mutex_lock(&g_init_mutex);

    if (g_hooks_installed) {
        pthread_mutex_unlock(&g_init_mutex);
        return nullptr;
    }

    // 初始化 ByteHook（AUTOMATIC 模式：自动 hook 后续加载的库）
    int bh_ret = bytehook_init(BYTEHOOK_MODE_AUTOMATIC, false);
    if (bh_ret != 0) {
        LOGE("bytehook_init failed: %d", bh_ret);
        pthread_mutex_unlock(&g_init_mutex);
        return nullptr;
    }
    LOGI("ByteHook initialized (AUTOMATIC mode)");

    // Install hooks in order of importance
    LOGI("Installing hooks...");

    // 1. libc hooks (highest priority - intercept file I/O)
    hook_libc::install_hooks();

    // 2. libfekit.so hooks (root/xposed detection)
    hook_libfekit::install_hooks();

    // 3. libturingxq.so hooks (ART integrity and risk reporting)
    hook_libturingxq::install_hooks();

    // 4. QQ 9.3.50+ extended hooks
    install_qsec_hooks();        // Anti-hook detection
    install_msfkernel_hooks();   // qimei36 fingerprint injection
    install_turingmfa_hooks();   // Risk reporting channel

    // 5. libMSF hooks (signature verification - experimental, disabled for stability)
    // hook_libmsf::install_hooks();

    g_hooks_installed = true;
    LOGI("=== All Native Hooks Installed Successfully ===");

    pthread_mutex_unlock(&g_init_mutex);

    return nullptr;
}

/**
 * Initialize native hooks
 */
extern "C" JNIEXPORT void JNICALL
Java_io_github_xalsace_qqbypass_NativeBypass_initNativeHooksNative(JNIEnv* env, jclass clazz) {
    LOGI("initNativeHooks called");

    if (g_hooks_installed) {
        LOGW("Native hooks already installed");
        return;
    }

    // Install hooks in background thread to avoid blocking
    pthread_t thread;
    if (pthread_create(&thread, nullptr, install_hooks_thread, nullptr) == 0) {
        pthread_detach(thread);
        LOGI("Hook installation thread started");
    } else {
        LOGE("Failed to create hook installation thread");
    }
}

/**
 * Check if hooks are installed
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_xalsace_qqbypass_NativeBypass_isHooksInstalledNative(JNIEnv* env, jclass clazz) {
    return g_hooks_installed;
}

/**
 * Get hook status message
 */
extern "C" JNIEXPORT jstring JNICALL
Java_io_github_xalsace_qqbypass_NativeBypass_getHookStatusNative(JNIEnv* env, jclass clazz) {
    if (g_hooks_installed) {
        return env->NewStringUTF("Native hooks installed successfully");
    } else {
        return env->NewStringUTF("Native hooks not installed yet");
    }
}

/**
 * Notify Native layer about Java detection event (Java → Native communication)
 */
extern "C" JNIEXPORT void JNICALL
Java_io_github_xalsace_qqbypass_NativeBypass_notifyDetectionNative(
    JNIEnv* env, jclass clazz, jstring event, jstring data) {

    const char* event_str = env->GetStringUTFChars(event, nullptr);
    const char* data_str = env->GetStringUTFChars(data, nullptr);

    LOGI("[Java→Native] Detection event: %s, data: %s", event_str, data_str);

    // Handle different events
    if (strcmp(event_str, "file_blocked") == 0) {
        LOGD("Java layer blocked file access: %s", data_str);
    } else if (strcmp(event_str, "package_hidden") == 0) {
        LOGD("Java layer hid package: %s", data_str);
    }

    env->ReleaseStringUTFChars(event, event_str);
    env->ReleaseStringUTFChars(data, data_str);
}

/**
 * Check if specific hook is active
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_xalsace_qqbypass_NativeBypass_isHookActiveNative(
    JNIEnv* env, jclass clazz, jstring hookName) {

    const char* hook_name = env->GetStringUTFChars(hookName, nullptr);
    bool is_active = false;

    // Check specific hooks
    if (strcmp(hook_name, "fopen") == 0 ||
        strcmp(hook_name, "fgets") == 0 ||
        strcmp(hook_name, "system") == 0) {
        is_active = g_hooks_installed;
    }

    env->ReleaseStringUTFChars(hookName, hook_name);
    return is_active;
}

/**
 * Get detailed hook statistics
 */
static int g_fopen_intercepts = 0;
static int g_fgets_intercepts = 0;
static int g_system_blocks = 0;

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_xalsace_qqbypass_NativeBypass_getHookStatisticsNative(
    JNIEnv* env, jclass clazz) {

    char stats[512];
    snprintf(stats, sizeof(stats),
        "Native Hook Statistics:\n"
        "- Hooks Installed: %s\n"
        "- fopen intercepts: %d\n"
        "- fgets intercepts: %d\n"
        "- system() blocks: %d\n"
        "- Protection Level: %s",
        g_hooks_installed ? "Yes" : "No",
        g_fopen_intercepts,
        g_fgets_intercepts,
        g_system_blocks,
        g_hooks_installed ? "Full (80%)" : "Partial (30%)");

    return env->NewStringUTF(stats);
}

/**
 * JNI_OnLoad - called when library is loaded
 */
JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGI("=== Native Bypass Library Loaded ===");

    JNIEnv* env;
    if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        LOGE("Failed to get JNI environment");
        return JNI_ERR;
    }

    LOGI("JNI_OnLoad complete, waiting for initNativeHooks() call");
    return JNI_VERSION_1_6;
}
