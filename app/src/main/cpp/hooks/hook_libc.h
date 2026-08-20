#ifndef NATIVE_BYPASS_HOOK_LIBC_H
#define NATIVE_BYPASS_HOOK_LIBC_H

namespace hook_libc {

/**
 * Hook libc functions to intercept file I/O
 * - fopen: redirect /proc/self/maps and /proc/self/status
 * - readlink: hide /proc/self/exe path
 * - access: hide suspicious files
 */
void install_hooks();

} // namespace hook_libc

#endif // NATIVE_BYPASS_HOOK_LIBC_H
