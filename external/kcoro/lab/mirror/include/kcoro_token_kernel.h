// SPDX-License-Identifier: BSD-3-Clause
#ifndef KCORO_TOKEN_KERNEL_H
#define KCORO_TOKEN_KERNEL_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

struct kc_chan;
struct kcoro;

typedef uint64_t kc_token_id_t;

typedef struct kc_payload {
    void   *ptr;
    size_t  len;
    int     status;   // 0=OK, negative = error (e.g. KC_ECANCELLED)
    uint64_t desc_id; // descriptor backing the payload (0 when not used)
} kc_payload;

typedef struct kc_ticket {
    kc_token_id_t      id;
    struct kc_chan *channel;
} kc_ticket;

int kc_token_kernel_global_init(void);
void kc_token_kernel_global_shutdown(void);

kc_ticket kc_token_kernel_publish_send(struct kc_chan *ch,
                                       void *ptr,
                                       size_t len,
                                       void (*resume_pc)(void));

kc_ticket kc_token_kernel_publish_recv(struct kc_chan *ch,
                                       void (*resume_pc)(void));

void kc_token_kernel_callback(kc_ticket ticket, kc_payload payload);
void kc_token_kernel_cancel(kc_ticket ticket, int reason);

int kc_token_kernel_consume_payload(kc_payload *out_payload);

#ifdef __cplusplus
}
#endif

#endif // KCORO_TOKEN_KERNEL_H
