#ifndef NATIVE_BYPASS_HOOK_LIBMSF_H
#define NATIVE_BYPASS_HOOK_LIBMSF_H

namespace hook_libmsf {

/**
 * Hook libMSFKernel.so and libmsfbootV2.so
 * - Signature verification bypass (experimental)
 * - Network communication filtering
 */
void install_hooks();

} // namespace hook_libmsf

#endif // NATIVE_BYPASS_HOOK_LIBMSF_H
