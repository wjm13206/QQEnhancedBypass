#ifndef HOOK_LIBQSEC_H
#define HOOK_LIBQSEC_H

/**
 * Install hooks for libQSec.so
 *
 * Blocks anti-hook detection mechanisms:
 * - dlopen/dlsym/dladdr scanning for hook frameworks
 * - sigaction timer-based self-checks (future)
 */
void install_qsec_hooks();

#endif // HOOK_LIBQSEC_H
