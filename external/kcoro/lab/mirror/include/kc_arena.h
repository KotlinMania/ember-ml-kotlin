// SPDX-License-Identifier: BSD-3-Clause
#ifndef KC_ARENA_H
#define KC_ARENA_H

#include <stddef.h>
#include <errno.h>

#ifdef __cplusplus
extern "C" {
#endif

#define KC_ARENA_MAX 4

int kc_arena_create(unsigned arena_id, size_t total_bytes);
int kc_arena_destroy(unsigned arena_id);
void *kc_arena_alloc(unsigned arena_id, size_t len);
void kc_arena_free(unsigned arena_id, void *ptr, size_t len);

#ifdef __cplusplus
}
#endif

#endif /* KC_ARENA_H */
