#include "hook_libc.h"
#include "../utils/log.h"
#include <bytehook.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <dlfcn.h>

namespace hook_libc {

// 直接从 libc 取到的真实函数指针（绕过 PLT，避免在辅助函数里再次触发 hook 造成递归）
static FILE* (*real_fopen)(const char*, const char*) = nullptr;
static char* (*real_fgets)(char*, int, FILE*) = nullptr;

static void ensure_real_funcs() {
    if (!real_fopen) {
        real_fopen = (FILE* (*)(const char*, const char*))dlsym(RTLD_NEXT, "fopen");
    }
    if (!real_fgets) {
        real_fgets = (char* (*)(char*, int, FILE*))dlsym(RTLD_NEXT, "fgets");
    }
}

// 构造过滤掉 hook 特征库的 maps 临时文件
static FILE* build_filtered_maps() {
    ensure_real_funcs();
    if (!real_fopen || !real_fgets) return nullptr;

    FILE* real = real_fopen("/proc/self/maps", "r");
    if (!real) return nullptr;

    FILE* fake = tmpfile();
    if (!fake) {
        fclose(real);
        return nullptr;
    }

    char line[512];
    const char* blacklist[] = {
        "lsposed", "xposed", "riru", "zygisk", "edxposed",
        "frida", "substrate", "libDalvikLoader", "libAndroidCydia", nullptr
    };

    while (real_fgets(line, sizeof(line), real)) {
        bool filter = false;
        for (int i = 0; blacklist[i]; i++) {
            if (strstr(line, blacklist[i])) { filter = true; break; }
        }
        if (!filter) fputs(line, fake);
    }
    fclose(real);
    rewind(fake);
    return fake;
}

// fopen 代理：重定向 /proc/self/maps
static FILE* fake_fopen(const char* path, const char* mode) {
    BYTEHOOK_STACK_SCOPE();

    if (path && strcmp(path, "/proc/self/maps") == 0) {
        LOGI("Intercepted fopen: /proc/self/maps");
        FILE* fake = build_filtered_maps();
        if (fake) return fake;
    }
    return BYTEHOOK_CALL_PREV(fake_fopen, path, mode);
}

// fgets 代理：伪造 TracerPid 为 0
static char* fake_fgets(char* s, int size, FILE* stream) {
    BYTEHOOK_STACK_SCOPE();

    char* result = BYTEHOOK_CALL_PREV(fake_fgets, s, size, stream);
    if (result && s && strncmp(s, "TracerPid:", 10) == 0) {
        snprintf(s, size, "TracerPid:\t0\n");
    }
    return result;
}

// readlink 代理：隐藏进程真实路径
static ssize_t fake_readlink(const char* path, char* buf, size_t bufsiz) {
    BYTEHOOK_STACK_SCOPE();

    if (path && strcmp(path, "/proc/self/exe") == 0) {
        const char* fake_path = "/system/bin/app_process64";
        size_t len = strlen(fake_path);
        if (len > bufsiz) len = bufsiz;
        memcpy(buf, fake_path, len);
        return (ssize_t)len;
    }
    return BYTEHOOK_CALL_PREV(fake_readlink, path, buf, bufsiz);
}

// access 代理：隐藏可疑文件
static int fake_access(const char* pathname, int mode) {
    BYTEHOOK_STACK_SCOPE();

    if (pathname) {
        const char* hidden[] = {
            "/data/local/tmp/frida", "re.frida.server", "/system/xposed",
            "/data/adb/lsposed", "/data/adb/modules/lsposed", "/data/adb/magisk", nullptr
        };
        for (int i = 0; hidden[i]; i++) {
            if (strstr(pathname, hidden[i])) {
                LOGD("Hidden path access: %s", pathname);
                return -1;
            }
        }
    }
    return BYTEHOOK_CALL_PREV(fake_access, pathname, mode);
}

void install_hooks() {
    LOGI("Installing libc hooks (ByteHook)...");
    ensure_real_funcs();

    bytehook_hook_all(nullptr, "fopen", (void*)fake_fopen, nullptr, nullptr);
    bytehook_hook_all(nullptr, "fgets", (void*)fake_fgets, nullptr, nullptr);
    bytehook_hook_all(nullptr, "readlink", (void*)fake_readlink, nullptr, nullptr);
    bytehook_hook_all(nullptr, "access", (void*)fake_access, nullptr, nullptr);

    LOGI("libc hooks installed");
}

} // namespace hook_libc
