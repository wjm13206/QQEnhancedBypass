#ifndef NATIVE_BYPASS_HOOK_LIBFEKIT_H
#define NATIVE_BYPASS_HOOK_LIBFEKIT_H

namespace hook_libfekit {

/**
 * Hook libfekit.so detection functions
 * - Root detection (Magisk/KernelSU checks)
 * - Maps scanning
 * - CheckJNI integrity verification
 * - Package manager queries
 */
void install_hooks();

} // namespace hook_libfekit

#endif // NATIVE_BYPASS_HOOK_LIBFEKIT_H
