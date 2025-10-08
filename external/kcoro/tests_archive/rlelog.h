// SPDX-License-Identifier: BSD-3-Clause
#pragma once
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

// Start run-length encoding of lines written to stderr.
// Redirects stderr into an internal thread that compresses repeated lines and
// writes a timestamped summary to stdout. If max_lines is >0, limits console
// output to that many lines (per process run).
int rlelog_start(size_t max_lines);

// Stop the RLE thread and restore stderr.
void rlelog_stop(void);

#ifdef __cplusplus
}
#endif

