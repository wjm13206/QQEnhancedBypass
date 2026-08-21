#ifndef HOOK_LIBTURINGMFA_H
#define HOOK_LIBTURINGMFA_H

/**
 * Install hooks for libturingmfa.so
 *
 * Blocks risk reporting channel:
 * - send/sendto network functions
 */
void install_turingmfa_hooks();

#endif // HOOK_LIBTURINGMFA_H
