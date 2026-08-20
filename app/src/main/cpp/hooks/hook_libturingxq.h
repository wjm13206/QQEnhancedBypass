#ifndef NATIVE_BYPASS_HOOK_LIBTURINGXQ_H
#define NATIVE_BYPASS_HOOK_LIBTURINGXQ_H

namespace hook_libturingxq {

/**
 * Hook libturingxq.so (Tencent TuringFD security SDK)
 * - ART method integrity checks
 * - Risk detection and reporting
 */
void install_hooks();

} // namespace hook_libturingxq

#endif // NATIVE_BYPASS_HOOK_LIBTURINGXQ_H
