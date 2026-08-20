#include "hook_libfekit.h"
#include "../utils/log.h"
#include <bytehook.h>
#include <stdio.h>
#include <string.h>

namespace hook_libfekit {

/**
 * libfekit.so 的检测函数符号被剥离，无法直接 hook。
 * 策略：拦截它依赖的 libc 系统调用（命令执行、字符串匹配、符号解析）。
 */

// system 代理：阻止 root 检测命令
static int fake_system(const char* command) {
    BYTEHOOK_STACK_SCOPE();

    if (command && (strstr(command, "magisk") || strstr(command, "su") ||
                    strstr(command, "mount") || strstr(command, "/proc/mounts"))) {
        LOGI("Blocked system command: %s", command);
        return 0;
    }
    return BYTEHOOK_CALL_PREV(fake_system, command);
}

// popen 代理：阻止管道命令检测
static FILE* fake_popen(const char* command, const char* type) {
    BYTEHOOK_STACK_SCOPE();

    if (command && (strstr(command, "magisk") || strstr(command, "su") ||
                    strstr(command, "grep") || strstr(command, "/proc/mounts"))) {
        LOGI("Blocked popen command: %s", command);
        return tmpfile();
    }
    return BYTEHOOK_CALL_PREV(fake_popen, command, type);
}

// strstr 代理：仅拦截 libfekit.so 对敏感关键字的匹配
static char* fake_strstr(const char* haystack, const char* needle) {
    BYTEHOOK_STACK_SCOPE();

    char* result = BYTEHOOK_CALL_PREV(fake_strstr, haystack, needle);
    if (result && needle) {
        const char* sensitive[] = {
            "lsposed", "xposed", "riru", "zygisk", "magisk", "frida", "KSU", nullptr
        };
        for (int i = 0; sensitive[i]; i++) {
            if (strcmp(needle, sensitive[i]) == 0) {
                LOGD("Blocked strstr match: %s", needle);
                return nullptr;
            }
        }
    }
    return result;
}

void install_hooks() {
    LOGI("Installing libfekit hooks (ByteHook)...");

    // 命令执行类：全局拦截（调用频率低，安全）
    bytehook_hook_all(nullptr, "system", (void*)fake_system, nullptr, nullptr);
    bytehook_hook_all(nullptr, "popen", (void*)fake_popen, nullptr, nullptr);

    // 字符串匹配：仅对 libfekit.so 作为调用方拦截，避免全局性能损耗
    bytehook_hook_single("libfekit.so", nullptr, "strstr", (void*)fake_strstr, nullptr, nullptr);

    LOGI("libfekit hooks installed");
}

} // namespace hook_libfekit
