// SPDX-License-Identifier: BSD-3-Clause
#ifndef KCORO_DESC_H
#define KCORO_DESC_H

#include <stddef.h>
#include <stdint.h>

#include "kcoro_token_kernel.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef uint64_t kc_desc_id;

enum {
    KC_DESC_FLAG_ALIAS = 1 << 0,  // Non-owning pointer
};

int kc_desc_global_init(void);
void kc_desc_global_shutdown(void);

kc_desc_id kc_desc_make_alias(void *ptr, size_t len);
kc_desc_id kc_desc_make_copy(const void *src, size_t len);
void kc_desc_retain(kc_desc_id id);
void kc_desc_release(kc_desc_id id);
int kc_desc_payload(kc_desc_id id, kc_payload *out_payload);

#ifdef __cplusplus
}
#endif

#endif /* KCORO_DESC_H */
