// SPDX-License-Identifier: BSD-3-Clause
/*
 * Distributed Channel Operations over IPC
 * 
 * This implements Kotlin-like distributed channels using the kcoro IPC layer.
 * It bridges local channel operations (kc_chan_*) with remote IPC commands,
 * enabling cross-process channel communication.
 */
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <arpa/inet.h>

#include "../include/kcoro_ipc_posix.h"
#include "../../../include/kcoro.h"
#include "../../../proto/kcoro_proto.h"

/* Distributed channel handle */
typedef struct kc_ipc_chan {
    kc_ipc_conn_t *conn;    /* IPC connection */
    uint32_t chan_id;       /* Remote channel ID */
    int kind;               /* Channel kind (local copy) */
    size_t elem_sz;         /* Element size (local copy) */
} kc_ipc_chan_t;

/* Create a distributed channel */
int kc_ipc_chan_make(kc_ipc_conn_t *conn, int kind, size_t elem_sz, 
                     size_t capacity, kc_ipc_chan_t **out)
{
    if (!conn || !out) return -EINVAL;
    if (elem_sz == 0 || elem_sz > 0xFFFFu) return -EMSGSIZE; /* TLV element length is uint16_t */
    
    /* Send CHAN_MAKE command */
    uint8_t buf[64];
    uint8_t *cur = buf, *end = buf + sizeof(buf);
    
    if (kc_tlv_put_u32(&cur, end, KCORO_ATTR_KIND, (uint32_t)kind) != 0) return -EMSGSIZE;
    if (kc_tlv_put_u32(&cur, end, KCORO_ATTR_ELEM_SIZE, (uint32_t)elem_sz) != 0) return -EMSGSIZE;
    if (kc_tlv_put_u32(&cur, end, KCORO_ATTR_CAPACITY, (uint32_t)capacity) != 0) return -EMSGSIZE;
    
    int rc = kc_ipc_send(conn, KCORO_CMD_CHAN_MAKE, buf, (size_t)(cur - buf));
    if (rc != 0) return rc;
    
    /* Receive response with channel ID */
    uint16_t cmd;
    uint8_t *payload = NULL;
    size_t len = 0;
    rc = kc_ipc_recv(conn, &cmd, &payload, &len);
    if (rc != 0) return rc;
    
    if (cmd != KCORO_CMD_CHAN_MAKE) {
        free(payload);
        return -EPROTO;
    }
    
    /* Parse channel ID from response */
    uint32_t chan_id = 0;
    size_t off = 0;
    while (off + 4 <= len) {
        uint16_t t, l;
        memcpy(&t, payload + off, 2);
        memcpy(&l, payload + off + 2, 2);
        t = ntohs(t);
        l = ntohs(l);
        off += 4;
        
        if (off + l > len) break;
        if (t == KCORO_ATTR_CHAN_ID && l == 4) {
            uint32_t v;
            memcpy(&v, payload + off, 4);
            chan_id = ntohl(v);
        }
        off += l;
    }
    free(payload);
    
    if (chan_id == 0) return -EPROTO;
    
    /* Create local handle */
    kc_ipc_chan_t *ich = malloc(sizeof(*ich));
    if (!ich) return -ENOMEM;
    
    ich->conn = conn;
    ich->chan_id = chan_id;
    ich->kind = kind;
    ich->elem_sz = elem_sz;
    
    *out = ich;
    return 0;
}

/* Open handle to an existing distributed channel (by ID) */
int kc_ipc_chan_open(kc_ipc_conn_t *conn, uint32_t chan_id, int kind,
                     size_t elem_sz, kc_ipc_chan_t **out)
{
    if (!conn || !out || elem_sz == 0 || chan_id == 0) return -EINVAL;
    kc_ipc_chan_t *ich = malloc(sizeof(*ich));
    if (!ich) return -ENOMEM;
    ich->conn = conn;
    ich->chan_id = chan_id;
    ich->kind = kind;
    ich->elem_sz = elem_sz;
    *out = ich;
    return 0;
}

/* Send to distributed channel (Kotlin channel.send() equivalent) */
int kc_ipc_chan_send(kc_ipc_chan_t *ich, const void *msg, long timeout_ms)
{
    if (!ich || !msg) return -EINVAL;
    if (ich->elem_sz > 0xFFFFu) return -EMSGSIZE;
    
    /* Prepare message with channel ID, element data, and timeout */
    size_t total_len = 4 + 2 + 4 + 2 + ich->elem_sz + 4 + 2 + 4; // TLV overhead
    uint8_t *buf = malloc(total_len);
    if (!buf) return -ENOMEM;
    
    uint8_t *cur = buf, *end = buf + total_len;
    
    /* Pack TLVs */
    if (kc_tlv_put_u32(&cur, end, KCORO_ATTR_CHAN_ID, ich->chan_id) != 0 ||
        kc_tlv_put_u32(&cur, end, KCORO_ATTR_TIMEOUT_MS, (uint32_t)timeout_ms) != 0) {
        free(buf);
        return -EMSGSIZE;
    }
    
    /* Add element data TLV manually */
    if ((size_t)(end - cur) < 4 + ich->elem_sz) {
        free(buf);
        return -EMSGSIZE;
    }
    
    uint16_t t = htons(KCORO_ATTR_ELEMENT);
    uint16_t l = htons((uint16_t)ich->elem_sz);
    memcpy(cur, &t, 2);
    memcpy(cur + 2, &l, 2);
    memcpy(cur + 4, msg, ich->elem_sz);
    cur += 4 + ich->elem_sz;
    
    int rc = kc_ipc_send(ich->conn, KCORO_CMD_CHAN_SEND, buf, (size_t)(cur - buf));
    free(buf);
    if (rc != 0) return rc;
    
    /* Receive result */
    uint16_t cmd;
    uint8_t *payload = NULL;
    size_t len = 0;
    rc = kc_ipc_recv(ich->conn, &cmd, &payload, &len);
    if (rc != 0) return rc;
    
    if (cmd != KCORO_CMD_CHAN_SEND) {
        free(payload);
        return -EPROTO;
    }
    
    /* Parse result code */
    int result = 0;
    size_t off = 0;
    while (off + 4 <= len) {
        uint16_t t, l;
        memcpy(&t, payload + off, 2);
        memcpy(&l, payload + off + 2, 2);
        t = ntohs(t);
        l = ntohs(l);
        off += 4;
        
        if (off + l > len) break;
        if (t == KCORO_ATTR_RESULT && l == 4) {
            uint32_t v;
            memcpy(&v, payload + off, 4);
            result = (int)ntohl(v);
        }
        off += l;
    }
    free(payload);
    
    return result;
}

/* Receive from distributed channel (Kotlin channel.receive() equivalent) */
int kc_ipc_chan_recv(kc_ipc_chan_t *ich, void *out, long timeout_ms)
{
    if (!ich || !out) return -EINVAL;
    if (ich->elem_sz > 0xFFFFu) return -EMSGSIZE;
    
    /* Send CHAN_RECV command */
    uint8_t buf[32];
    uint8_t *cur = buf, *end = buf + sizeof(buf);
    
    if (kc_tlv_put_u32(&cur, end, KCORO_ATTR_CHAN_ID, ich->chan_id) != 0 ||
        kc_tlv_put_u32(&cur, end, KCORO_ATTR_TIMEOUT_MS, (uint32_t)timeout_ms) != 0) {
        return -EMSGSIZE;
    }
    
    int rc = kc_ipc_send(ich->conn, KCORO_CMD_CHAN_RECV, buf, (size_t)(cur - buf));
    if (rc != 0) return rc;
    
    /* Receive response */
    uint16_t cmd;
    uint8_t *payload = NULL;
    size_t len = 0;
    rc = kc_ipc_recv(ich->conn, &cmd, &payload, &len);
    if (rc != 0) return rc;
    
    if (cmd != KCORO_CMD_CHAN_RECV) {
        free(payload);
        return -EPROTO;
    }
    
    /* Parse result and element data */
    int result = -EPROTO;
    size_t off = 0;
    while (off + 4 <= len) {
        uint16_t t, l;
        memcpy(&t, payload + off, 2);
        memcpy(&l, payload + off + 2, 2);
        t = ntohs(t);
        l = ntohs(l);
        off += 4;
        
        if (off + l > len) break;
        
        if (t == KCORO_ATTR_RESULT && l == 4) {
            uint32_t v;
            memcpy(&v, payload + off, 4);
            result = (int)ntohl(v);
        } else if (t == KCORO_ATTR_ELEMENT && l == ich->elem_sz) {
            memcpy(out, payload + off, ich->elem_sz);
        }
        off += l;
    }
    free(payload);
    
    return result;
}

/* Non-blocking send (Kotlin channel.trySend() equivalent) */
int kc_ipc_chan_try_send(kc_ipc_chan_t *ich, const void *msg)
{
    return kc_ipc_chan_send(ich, msg, 0); /* timeout = 0 for non-blocking */
}

/* Non-blocking receive (Kotlin channel.tryReceive() equivalent) */
int kc_ipc_chan_try_recv(kc_ipc_chan_t *ich, void *out)
{
    return kc_ipc_chan_recv(ich, out, 0); /* timeout = 0 for non-blocking */
}

/* Close distributed channel */
int kc_ipc_chan_close(kc_ipc_chan_t *ich)
{
    if (!ich) return -EINVAL;
    
    uint8_t buf[16];
    uint8_t *cur = buf, *end = buf + sizeof(buf);
    
    if (kc_tlv_put_u32(&cur, end, KCORO_ATTR_CHAN_ID, ich->chan_id) != 0) {
        return -EMSGSIZE;
    }
    
    return kc_ipc_send(ich->conn, KCORO_CMD_CHAN_CLOSE, buf, (size_t)(cur - buf));
}

/* Destroy distributed channel handle */
void kc_ipc_chan_destroy(kc_ipc_chan_t *ich)
{
    if (!ich) return;
    
    /* Send destroy command (best effort) */
    uint8_t buf[16];
    uint8_t *cur = buf, *end = buf + sizeof(buf);
    
    if (kc_tlv_put_u32(&cur, end, KCORO_ATTR_CHAN_ID, ich->chan_id) == 0) {
        kc_ipc_send(ich->conn, KCORO_CMD_CHAN_DESTROY, buf, (size_t)(cur - buf));
    }
    
    free(ich);
}

/* Accessors */
uint32_t kc_ipc_chan_id(kc_ipc_chan_t *ich)
{
    return ich ? ich->chan_id : 0;
}
