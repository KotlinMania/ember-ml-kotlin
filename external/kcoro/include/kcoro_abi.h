// SPDX-License-Identifier: BSD-3-Clause
#pragma once
/**
 * @file kcoro_abi.h
 * @brief Version markers for the kcoro public ABI.
 *
 * The ABI is intentionally small and stable. Bumping MAJOR signals breaking
 * changes; MINOR increments for additive changes (new functions/fields that do
 * not disturb existing layouts or semantics). Tools can include this header to
 * assert compatibility at build time.
 *
 * Install guidance
 *   - Always installed. External components may #if on these values.
 */

/**
 * ABI major version: incremented on breaking changes.
 * Consumers may assert KCORO_ABI_MAJOR compatibility at build time to guard
 * against incompatible header/runtime pairs.
 */
#define KCORO_ABI_MAJOR 0
/**
 * ABI minor version: incremented on additive changes.
 * New functions/fields that do not disturb existing layouts bump MINOR only;
 * compatible code should continue to compile and run.
 */
#define KCORO_ABI_MINOR 2
