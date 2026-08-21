#include <jni.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/types.h>
#include "../utils/log.h"
#include "bytehook.h"

// libturingmfa 关键函数的原始指针
static ssize_t (*original_send)(int, const void*, size_t, int) = nullptr;
static ssize_t (*original_sendto)(int, const void*, size_t, int, const struct sockaddr*, socklen_t) = nullptr;

/**
 * Hook send - 拦截 turingmfa 的网络上报
 */
static ssize_t hooked_send(int sockfd, const void* buf, size_t len, int flags) {
    // 检查是否包含风控特征字符串
    if (buf && len > 0) {
        const char* data = (const char*)buf;

        // 检测风控上报特征（根据实际抓包调整）
        if (memmem(data, len, "turingmfa", 9) != nullptr ||
            memmem(data, len, "risk_report", 11) != nullptr) {
            LOGI("Blocked turingmfa send (%zu bytes)", len);
            return len; // 假装发送成功
        }
    }

    return original_send(sockfd, buf, len, flags);
}

/**
 * Hook sendto - 拦截 UDP 风控上报
 */
static ssize_t hooked_sendto(int sockfd, const void* buf, size_t len, int flags,
                             const struct sockaddr* dest_addr, socklen_t addrlen) {
    if (buf && len > 0) {
        const char* data = (const char*)buf;

        if (memmem(data, len, "turingmfa", 9) != nullptr ||
            memmem(data, len, "risk_report", 11) != nullptr) {
            LOGI("Blocked turingmfa sendto (%zu bytes)", len);
            return len;
        }
    }

    return original_sendto(sockfd, buf, len, flags, dest_addr, addrlen);
}

/**
 * 安装 libturingmfa 的 hook
 */
void install_turingmfa_hooks() {
    LOGI("Installing libturingmfa hooks (ByteHook)...");

    // Hook send
    bytehook_stub_t stub = bytehook_hook_single(
        "libturingmfa.so",
        nullptr,
        "send",
        (void*)hooked_send,
        nullptr,
        (void**)&original_send
    );
    if (stub == nullptr) {
        LOGE("Failed to hook libturingmfa send");
    }

    // Hook sendto
    stub = bytehook_hook_single(
        "libturingmfa.so",
        nullptr,
        "sendto",
        (void*)hooked_sendto,
        nullptr,
        (void**)&original_sendto
    );
    if (stub == nullptr) {
        LOGE("Failed to hook libturingmfa sendto");
    }

    LOGI("libturingmfa hooks installed");
}
