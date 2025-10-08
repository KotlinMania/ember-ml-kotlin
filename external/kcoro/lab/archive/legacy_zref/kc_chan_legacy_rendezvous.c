// SPDX-License-Identifier: BSD-3-Clause
// Legacy rendezvous zref code moved from kc_chan.c (now unified under kc_zcopy.c).
// This file is archived for reference only and is not built.

/* NOTE: The active implementation lives in kcoro/user/src/kc_zcopy.c.
 * The old inlined rendezvous helper logic (wait + publish + park) was removed
 * from kc_chan.c to avoid duplicate paths and reduce lifecycle complexity. */

/* Intentionally left minimal to avoid drift. Refer to git history for the
 * exact removed blocks (kc_chan_zref_wait_send, kc_chan_send_zref, etc.). */
