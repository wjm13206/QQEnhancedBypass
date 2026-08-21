#ifndef HOOK_LIBMSFKERNEL_H
#define HOOK_LIBMSFKERNEL_H

/**
 * Install hooks for libMSFKernel.so
 *
 * Intercepts device fingerprint injection:
 * - MSFKernelBridge.native_setQimei36 (qimei36 device fingerprint)
 */
void install_msfkernel_hooks();

#endif // HOOK_LIBMSFKERNEL_H
