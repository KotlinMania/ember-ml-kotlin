#include <assert.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>

typedef enum {
    KC_WAITER_INIT = 0,
    KC_WAITER_ENQUEUED,
    KC_WAITER_CLAIMED,
    KC_WAITER_CANCELLED
} kc_waiter_status_t;

typedef struct {
    kc_waiter_status_t status;
    const char *name;
    bool cancellable;
} kc_waiter_token_t;

typedef enum {
    KC_RV_EMPTY = 0,
    KC_RV_SENDER_READY,
    KC_RV_RECEIVER_READY,
    KC_RV_MATCHED,
    KC_RV_CANCELLED
} kc_rv_state_t;

typedef enum {
    KC_PAYLOAD_NONE = 0,
    KC_PAYLOAD_BYTES,
    KC_PAYLOAD_ZDESC
} kc_payload_kind_t;

typedef struct {
    kc_payload_kind_t kind;
    union {
        const char *bytes;
        struct {
            const void *addr;
            size_t len;
        } zdesc;
    } data;
} kc_payload_t;

typedef struct {
    unsigned matches;
    unsigned cancels;
    unsigned zdesc_matches;
} kc_rv_metrics_t;

typedef struct {
    kc_rv_state_t state;
    kc_waiter_token_t *sender;
    kc_waiter_token_t *receiver;
    kc_payload_t payload;
    kc_rv_metrics_t metrics;
} kc_rv_cell_t;

static void kc_waiter_token_init(kc_waiter_token_t *token, const char *name, bool cancellable) {
    token->status = KC_WAITER_INIT;
    token->name = name;
    token->cancellable = cancellable;
}

static bool kc_waiter_publish(kc_waiter_token_t *token) {
    if (token->status != KC_WAITER_INIT) {
        return false;
    }
    token->status = KC_WAITER_ENQUEUED;
    return true;
}

static bool kc_waiter_claim(kc_waiter_token_t *token) {
    if (token->status != KC_WAITER_ENQUEUED) {
        return false;
    }
    token->status = KC_WAITER_CLAIMED;
    return true;
}

static bool kc_waiter_cancel(kc_waiter_token_t *token) {
    if (!token->cancellable) {
        return false;
    }
    if (token->status != KC_WAITER_ENQUEUED) {
        return false;
    }
    token->status = KC_WAITER_CANCELLED;
    return true;
}

static void kc_payload_reset(kc_payload_t *payload) {
    payload->kind = KC_PAYLOAD_NONE;
    memset(&payload->data, 0, sizeof(payload->data));
}

static void kc_payload_set_bytes(kc_payload_t *payload, const char *bytes) {
    payload->kind = KC_PAYLOAD_BYTES;
    payload->data.bytes = bytes;
}

static void kc_payload_set_zdesc(kc_payload_t *payload, const void *addr, size_t len) {
    payload->kind = KC_PAYLOAD_ZDESC;
    payload->data.zdesc.addr = addr;
    payload->data.zdesc.len = len;
}

static void kc_rv_cell_reset(kc_rv_cell_t *cell) {
    memset(cell, 0, sizeof(*cell));
    cell->state = KC_RV_EMPTY;
    kc_payload_reset(&cell->payload);
}

static void kc_rv_cell_record_match(kc_rv_cell_t *cell) {
    cell->metrics.matches++;
    if (cell->payload.kind == KC_PAYLOAD_ZDESC) {
        cell->metrics.zdesc_matches++;
    }
}

static bool rv_sender_arrive(kc_rv_cell_t *cell, kc_waiter_token_t *sender, kc_payload_t payload) {
    switch (cell->state) {
    case KC_RV_EMPTY:
        if (!kc_waiter_publish(sender)) {
            return false;
        }
        cell->sender = sender;
        cell->payload = payload;
        cell->state = KC_RV_SENDER_READY;
        return true;
    case KC_RV_RECEIVER_READY:
        if (!kc_waiter_claim(cell->receiver)) {
            return false;
        }
        cell->payload = payload;
        cell->state = KC_RV_MATCHED;
        kc_rv_cell_record_match(cell);
        return true;
    default:
        return false;
    }
}

static bool rv_receiver_arrive(kc_rv_cell_t *cell, kc_waiter_token_t *receiver, kc_payload_t *out_payload) {
    switch (cell->state) {
    case KC_RV_EMPTY:
        if (!kc_waiter_publish(receiver)) {
            return false;
        }
        cell->receiver = receiver;
        cell->state = KC_RV_RECEIVER_READY;
        return true;
    case KC_RV_SENDER_READY:
        if (!kc_waiter_claim(cell->sender)) {
            return false;
        }
        if (out_payload) {
            *out_payload = cell->payload;
        }
        cell->state = KC_RV_MATCHED;
        kc_rv_cell_record_match(cell);
        return true;
    default:
        return false;
    }
}

static bool rv_cancel_waiter(kc_rv_cell_t *cell, kc_waiter_token_t *token) {
    if (!kc_waiter_cancel(token)) {
        return false;
    }
    cell->state = KC_RV_CANCELLED;
    cell->metrics.cancels++;
    kc_payload_reset(&cell->payload);
    return true;
}

static const char *payload_desc(const kc_payload_t *payload, char *buf, size_t buf_sz) {
    switch (payload->kind) {
    case KC_PAYLOAD_BYTES:
        return payload->data.bytes ? payload->data.bytes : "(null)";
    case KC_PAYLOAD_ZDESC:
        snprintf(buf, buf_sz, "zdesc[%zu]", payload->data.zdesc.len);
        return buf;
    default:
        return "-";
    }
}

static void dump_cell(const char *label, const kc_rv_cell_t *cell) {
    static const char *state_names[] = {
        "EMPTY",
        "SENDER_READY",
        "RECEIVER_READY",
        "MATCHED",
        "CANCELLED"
    };
    char payload_buf[64];
    printf("%-22s state=%-13s sender=%-12s receiver=%-12s payload=%-12s | matches=%u cancels=%u zdesc_matches=%u\n",
           label,
           state_names[cell->state],
           cell->sender ? cell->sender->name : "-",
           cell->receiver ? cell->receiver->name : "-",
           payload_desc(&cell->payload, payload_buf, sizeof(payload_buf)),
           cell->metrics.matches,
           cell->metrics.cancels,
           cell->metrics.zdesc_matches);
}

static void scenario_sender_first(void) {
    kc_rv_cell_t cell;
    kc_waiter_token_t sender, receiver;
    kc_rv_cell_reset(&cell);
    kc_waiter_token_init(&sender, "sender", false);
    kc_waiter_token_init(&receiver, "receiver", true);

    kc_payload_t payload;
    kc_payload_set_bytes(&payload, "payload-A");
    bool ok = rv_sender_arrive(&cell, &sender, payload);
    assert(ok);
    dump_cell("after sender arrives", &cell);

    kc_payload_t received = {0};
    ok = rv_receiver_arrive(&cell, &receiver, &received);
    assert(ok);
    assert(received.kind == KC_PAYLOAD_BYTES);
    assert(received.data.bytes && strcmp(received.data.bytes, "payload-A") == 0);
    dump_cell("after receiver matches", &cell);

    assert(sender.status == KC_WAITER_CLAIMED);
    assert(receiver.status == KC_WAITER_INIT);
    assert(cell.metrics.matches == 1);
    assert(cell.metrics.cancels == 0);
}

static void scenario_receiver_timeout(void) {
    kc_rv_cell_t cell;
    kc_waiter_token_t receiver;
    kc_rv_cell_reset(&cell);
    kc_waiter_token_init(&receiver, "receiver", true);

    bool ok = rv_receiver_arrive(&cell, &receiver, NULL);
    assert(ok);
    dump_cell("receiver enqueued", &cell);

    ok = rv_cancel_waiter(&cell, &receiver);
    assert(ok);
    assert(receiver.status == KC_WAITER_CANCELLED);
    dump_cell("receiver cancelled", &cell);
    assert(cell.metrics.cancels == 1);
}

static void scenario_sender_matches_waiting_receiver(void) {
    kc_rv_cell_t cell;
    kc_waiter_token_t sender, receiver;
    kc_rv_cell_reset(&cell);
    kc_waiter_token_init(&sender, "sender", false);
    kc_waiter_token_init(&receiver, "receiver", true);

    bool ok = rv_receiver_arrive(&cell, &receiver, NULL);
    assert(ok);
    assert(receiver.status == KC_WAITER_ENQUEUED);

    kc_payload_t payload;
    kc_payload_set_bytes(&payload, "payload-B");
    bool sender_ok = rv_sender_arrive(&cell, &sender, payload);
    assert(sender_ok);
    assert(cell.state == KC_RV_MATCHED);
    assert(sender.status == KC_WAITER_INIT);
    assert(receiver.status == KC_WAITER_CLAIMED);
    dump_cell("matched via sender arrival", &cell);
    assert(cell.metrics.matches == 1);
}

static void scenario_zero_copy_match(void) {
    kc_rv_cell_t cell;
    kc_waiter_token_t sender, receiver;
    kc_rv_cell_reset(&cell);
    kc_waiter_token_init(&sender, "sender_z", false);
    kc_waiter_token_init(&receiver, "receiver_z", true);

    bool ok = rv_receiver_arrive(&cell, &receiver, NULL);
    assert(ok);

    char buffer[] = "hello-zero-copy";
    kc_payload_t payload;
    kc_payload_set_zdesc(&payload, buffer, strlen(buffer));
    ok = rv_sender_arrive(&cell, &sender, payload);
    assert(ok);
    dump_cell("zdesc match", &cell);
    assert(cell.metrics.matches == 1);
    assert(cell.metrics.zdesc_matches == 1);
}

int main(void) {
    scenario_sender_first();
    scenario_receiver_timeout();
    scenario_sender_matches_waiting_receiver();
    scenario_zero_copy_match();
    puts("All rendezvous token lab scenarios passed.");
    return 0;
}
