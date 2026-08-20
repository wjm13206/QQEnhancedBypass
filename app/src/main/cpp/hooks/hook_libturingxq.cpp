#include "hook_libturingxq.h"
#include "../utils/log.h"
#include <bytehook.h>
#include <string.h>
#include <sys/socket.h>

namespace hook_libturingxq {

/**
 * libturingxq.so 高度混淆，仅导出 JNI_OnLoad。
 * 策略：拦截网络发送函数，阻止风控数据上报。
 */

static bool contains_risk(const void* buf, size_t len) {
    if (!buf || len == 0) return false;
    const char* keywords[] = {
        "riskCheckWup", "DeviceTokenV3", "turingRiskDetect", nullptr
    };
    for (int i = 0; keywords[i]; i++) {
        if (memmem(buf, len, keywords[i], strlen(keywords[i]))) {
            LOGI("Blocked risk report: %s", keywords[i]);
            return true;
        }
    }
    return false;
}

// send 代理
static ssize_t fake_send(int sockfd, const void* buf, size_t len, int flags) {
    BYTEHOOK_STACK_SCOPE();

    if (contains_risk(buf, len)) {
        return (ssize_t)len; // 假装发送成功
    }
    return BYTEHOOK_CALL_PREV(fake_send, sockfd, buf, len, flags);
}

// sendto 代理
static ssize_t fake_sendto(int sockfd, const void* buf, size_t len, int flags,
                           const struct sockaddr* dest_addr, socklen_t addrlen) {
    BYTEHOOK_STACK_SCOPE();

    if (contains_risk(buf, len)) {
        return (ssize_t)len;
    }
    return BYTEHOOK_CALL_PREV(fake_sendto, sockfd, buf, len, flags, dest_addr, addrlen);
}

void install_hooks() {
    LOGI("Installing libturingxq hooks (ByteHook)...");

    bytehook_hook_all(nullptr, "send", (void*)fake_send, nullptr, nullptr);
    bytehook_hook_all(nullptr, "sendto", (void*)fake_sendto, nullptr, nullptr);

    LOGI("libturingxq hooks installed");
}

} // namespace hook_libturingxq
