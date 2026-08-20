#include "hook_libmsf.h"
#include "../utils/log.h"
#include <bytehook.h>

namespace hook_libmsf {

/**
 * libMSFKernel.so / libmsfbootV2.so 负责签名校验（阻断型）。
 *
 * 警告：签名校验绕过风险极高，且校验函数符号被剥离、逻辑在 native 内部完成，
 * 单纯的 PLT hook 无法安全绕过。这里默认不做任何 hook，避免导致 QQ 启动崩溃。
 *
 * 如需研究签名绕过，应使用 IDA 定位 IsSignatureValid 的实际偏移后做 inline hook，
 * 那超出了 ByteHook（PLT hook）的能力范围。
 */

void install_hooks() {
    LOGI("libMSF hooks skipped (signature bypass disabled for stability)");
}

} // namespace hook_libmsf
