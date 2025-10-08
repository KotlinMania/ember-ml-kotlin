// SPDX-License-Identifier: BSD-3-Clause
#include <errno.h>
#include <string.h>
#include "../../include/kcoro_zcopy.h"
#include "kc_chan_internal.h"
#include "kcoro_core.h"

static int zref_send(kc_chan_t *c, const kc_zdesc_t *d, long timeout_ms);
static int zref_recv(kc_chan_t *c, kc_zdesc_t *d, long timeout_ms);
static int zref_send_c(kc_chan_t *c, const kc_zdesc_t *d, long timeout_ms, const kc_cancel_t *ct);
static int zref_recv_c(kc_chan_t *c, kc_zdesc_t *d, long timeout_ms, const kc_cancel_t *ct);
static int zref_attach(kc_chan_t *c, const void *opts);
static void zref_detach(kc_chan_t *c);

static const kc_zcopy_backend_ops_t g_zref_ops = {
    .attach = zref_attach,
    .detach = zref_detach,
    .send = zref_send,
    .recv = zref_recv,
    .send_c = zref_send_c,
    .recv_c = zref_recv_c,
};

static int g_zref_registered = 0;

static void ensure_zref(void)
{
    if (!g_zref_registered) {
        kc_zcopy_register("zref", &g_zref_ops, KC_CHAN_CAP_ZERO_COPY);
        g_zref_registered = 1;
    }
}

kc_zcopy_backend_id kc_zcopy_register(const char *name,
                                      const kc_zcopy_backend_ops_t *ops,
                                      uint32_t caps)
{
    if (!name || !ops) return -EINVAL;
    if (strcmp(name, "zref") == 0) {
        (void)caps;
        g_zref_registered = 1;
        return 0;
    }
    return -ENOTSUP;
}

kc_zcopy_backend_id kc_zcopy_resolve(const char *name)
{
    ensure_zref();
    if (!name) return -EINVAL;
    if (strcmp(name, "zref") == 0) return 0;
    return -ENOENT;
}

int kc_chan_enable_zero_copy_backend(kc_chan_t *c,
                                     kc_zcopy_backend_id id,
                                     const void *opts)
{
    struct kc_chan *ch = (struct kc_chan*)c;
    if (!ch) return -EINVAL;
    ensure_zref();
    if (id != 0) return -ENOENT;
    KC_MUTEX_LOCK(&ch->mu);
    ch->capabilities |= KC_CHAN_CAP_ZERO_COPY;
    ch->zc_ops = &g_zref_ops;
    ch->zc_backend_id = id;
    KC_MUTEX_UNLOCK(&ch->mu);
    return zref_attach(c, opts);
}

static int zref_attach(kc_chan_t *c, const void *opts)
{
    (void)c; (void)opts;
    return 0;
}

static void zref_detach(kc_chan_t *c)
{
    (void)c;
}

static void kc_chan_record_zref_send_alias(struct kc_chan *ch)
{
    KC_MUTEX_LOCK(&ch->mu);
    ch->zref_mode = 1;
    ch->zref_sent++;
    KC_MUTEX_UNLOCK(&ch->mu);
}

static void kc_chan_record_zref_recv_alias(struct kc_chan *ch)
{
    KC_MUTEX_LOCK(&ch->mu);
    ch->zref_mode = 1;
    ch->zref_received++;
    KC_MUTEX_UNLOCK(&ch->mu);
}

static void kc_chan_record_zref_fallback_small(struct kc_chan *ch)
{
    KC_MUTEX_LOCK(&ch->mu);
    ch->zref_mode = 1;
    ch->zref_fallback_small++;
    KC_MUTEX_UNLOCK(&ch->mu);
}

static int zref_send_internal(kc_chan_t *c, const kc_zdesc_t *d, long timeout_ms)
{
    struct kc_chan *ch = (struct kc_chan*)c;
    if (ch->ptr_mode) {
        int rc = kc_chan_send_ptr(c, (void*)d->addr, d->len, timeout_ms);
        if (rc == 0) kc_chan_record_zref_send_alias(ch);
        return rc;
    }
    int rc = kc_chan_send(c, d->addr, timeout_ms);
    if (rc == 0) kc_chan_record_zref_fallback_small(ch);
    return rc;
}

static int zref_recv_internal(kc_chan_t *c, kc_zdesc_t *d, long timeout_ms)
{
    struct kc_chan *ch = (struct kc_chan*)c;
    if (ch->ptr_mode) {
        void *ptr = NULL;
        size_t len = 0;
        int rc = kc_chan_recv_ptr(c, &ptr, &len, timeout_ms);
        if (rc == 0) {
            d->addr = ptr;
            d->len = len;
            kc_chan_record_zref_recv_alias(ch);
        }
        return rc;
    } else {
        int rc = kc_chan_recv(c, d->addr, timeout_ms);
        if (rc == 0) {
            if (d) d->len = ch->elem_sz;
            kc_chan_record_zref_fallback_small(ch);
        }
        return rc;
    }
}

static int zref_send(kc_chan_t *c, const kc_zdesc_t *d, long timeout_ms)
{
    if (!c || !d) return -EINVAL;
    return zref_send_internal(c, d, timeout_ms);
}

static int zref_recv(kc_chan_t *c, kc_zdesc_t *d, long timeout_ms)
{
    if (!c || !d) return -EINVAL;
    return zref_recv_internal(c, d, timeout_ms);
}

static int zref_send_c(kc_chan_t *c, const kc_zdesc_t *d, long timeout_ms, const kc_cancel_t *ct)
{
    if (ct && kc_cancel_is_set(ct)) return KC_ECANCELED;
    return zref_send(c, d, timeout_ms);
}

static int zref_recv_c(kc_chan_t *c, kc_zdesc_t *d, long timeout_ms, const kc_cancel_t *ct)
{
    if (ct && kc_cancel_is_set(ct)) return KC_ECANCELED;
    return zref_recv(c, d, timeout_ms);
}

int kc_chan_send_desc(kc_chan_t *c, const kc_zdesc_t *d, long timeout_ms)
{
    struct kc_chan *ch = (struct kc_chan*)c;
    if (!ch || !d) return -EINVAL;
    if (ch->zc_ops && ch->zc_ops->send) {
        return ch->zc_ops->send(c, d, timeout_ms);
    }
    return zref_send(c, d, timeout_ms);
}

int kc_chan_recv_desc(kc_chan_t *c, kc_zdesc_t *d, long timeout_ms)
{
    struct kc_chan *ch = (struct kc_chan*)c;
    if (!ch || !d) return -EINVAL;
    if (ch->zc_ops && ch->zc_ops->recv) {
        return ch->zc_ops->recv(c, d, timeout_ms);
    }
    return zref_recv(c, d, timeout_ms);
}

int kc_chan_send_desc_c(kc_chan_t *c, const kc_zdesc_t *d, long timeout_ms, const kc_cancel_t *ct)
{
    struct kc_chan *ch = (struct kc_chan*)c;
    if (!ch || !d) return -EINVAL;
    if (ch->zc_ops && ch->zc_ops->send_c) {
        return ch->zc_ops->send_c(c, d, timeout_ms, ct);
    }
    return zref_send_c(c, d, timeout_ms, ct);
}

int kc_chan_recv_desc_c(kc_chan_t *c, kc_zdesc_t *d, long timeout_ms, const kc_cancel_t *ct)
{
    struct kc_chan *ch = (struct kc_chan*)c;
    if (!ch || !d) return -EINVAL;
    if (ch->zc_ops && ch->zc_ops->recv_c) {
        return ch->zc_ops->recv_c(c, d, timeout_ms, ct);
    }
    return zref_recv_c(c, d, timeout_ms, ct);
}
