// SPDX-License-Identifier: BSD-3-Clause
/*
 * Distributed Channel Example - Kotlin-like patterns in C
 * 
 * This demonstrates kcoro's distributed channels that work like Kotlin coroutines:
 * - channel.send() / channel.receive() 
 * - channel.trySend() / channel.tryReceive()
 * - Different channel types (RENDEZVOUS, BUFFERED, CONFLATED, UNLIMITED)
 * - Cross-process communication via IPC
 */
#define _POSIX_C_SOURCE 200112L
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/wait.h>
#include <string.h>
#include <signal.h>
#include <stdarg.h>

#include "kcoro.h"
#include "kcoro_port.h"
#include "kcoro_proto.h"
#include "kcoro_ipc_posix.h"
#include "kcoro_ipc_chan.h"
#include "kcoro_ipc_server.h"

/* Message structure for our example */
struct Message {
    int id;
    char text[32];
};

static int g_debug = 0;
static void dbg(const char *fmt, ...)
{
    if (!g_debug) return;
    va_list ap; va_start(ap, fmt);
    fprintf(stderr, "[dbg] ");
    vfprintf(stderr, fmt, ap);
    fprintf(stderr, "\n");
    va_end(ap);
}

static void enable_debug(void)
{
    g_debug = 1;
    setvbuf(stdout, NULL, _IOLBF, 0);
    setvbuf(stderr, NULL, _IOLBF, 0);
    setenv("KCORO_DEBUG", "1", 1);
}

/* Producer - sends messages to channel (like Kotlin producer coroutine) */
static const char *KC_SOCK = "/tmp/kcoro_example.sock";
static const char *KC_CHAN_FILE = "/tmp/kcoro_example.chan";

static int write_chan_info(uint32_t id, int kind, size_t elem_sz)
{
    FILE *f = fopen(KC_CHAN_FILE, "w");
    if (!f) { perror("fopen chan file"); return -1; }
    fprintf(f, "%u %d %zu\n", id, kind, elem_sz);
    fclose(f);
    return 0;
}

static int read_chan_info(uint32_t *id, int *kind, size_t *elem_sz)
{
    FILE *f = fopen(KC_CHAN_FILE, "r");
    if (!f) return -1;
    unsigned tmp_id = 0; int tmp_kind = 0; size_t tmp_sz = 0;
    if (fscanf(f, "%u %d %zu", &tmp_id, &tmp_kind, &tmp_sz) != 3) { fclose(f); return -1; }
    fclose(f);
    if (id) *id = tmp_id;
    if (kind) *kind = tmp_kind;
    if (elem_sz) *elem_sz = tmp_sz;
    return 0;
}

static int producer_process(void)
{
    printf("[Producer] Starting...\n");
    
    /* Connect to kcoro server */
    kc_ipc_conn_t *conn = NULL;
    if (kc_ipc_connect(KC_SOCK, &conn) != 0) {
        perror("connect");
        return 1;
    }
    
    /* Handshake */
    uint32_t maj, min;
    if (kc_ipc_hs_cli(conn, &maj, &min) != 0) {
        fprintf(stderr, "[Producer] Handshake failed\n");
        kc_ipc_conn_close(conn);
        return 1;
    }
    printf("[Producer] Connected to server ABI %u.%u\n", maj, min);
    
    /* Create a distributed buffered channel - like Kotlin Channel<Message>(capacity = 4) */
    kc_ipc_chan_t *channel = NULL;
    int rc = kc_ipc_chan_make(conn, KC_BUFFERED, sizeof(struct Message), 4, &channel);
    if (rc != 0) {
        fprintf(stderr, "[Producer] Failed to create channel: %d\n", rc);
        kc_ipc_conn_close(conn);
        return 1;
    }
    printf("[Producer] Created distributed channel\n");
    
    /* Share channel info (ID/kind/elem_sz) with consumer via a temp file */
    uint32_t cid = kc_ipc_chan_id(channel);
    if (write_chan_info(cid, KC_BUFFERED, sizeof(struct Message)) != 0) {
        fprintf(stderr, "[Producer] Failed to write channel info\n");
    }
    printf("[Producer] Channel id=%u\n", cid);
    
    /* Send messages - like Kotlin: repeat(10) { channel.send(Message(it, "data$it")) } */
    for (int i = 0; i < 10; i++) {
        struct Message msg = { .id = i };
        snprintf(msg.text, sizeof(msg.text), "data%d", i);
        
        printf("[Producer] Sending message %d: %s\n", msg.id, msg.text);
        dbg("producer send id=%d", i);
        
        /* Suspending send - will block if channel buffer is full */
        rc = kc_ipc_chan_send(channel, &msg, -1); /* timeout = -1 means infinite */
        if (rc != 0) {
            fprintf(stderr, "[Producer] Send failed: %d\n", rc);
            break;
        }
        
        sleep(1); /* 1s delay for demo */
    }
    
    /* Try non-blocking send - like Kotlin channel.trySend() */
    struct Message overflow_msg = { .id = 999 };
    strcpy(overflow_msg.text, "overflow");
    
    rc = kc_ipc_chan_try_send(channel, &overflow_msg);
    if (rc == KC_EAGAIN) {
        printf("[Producer] Channel full, trySend() returned EAGAIN as expected\n");
    } else {
        printf("[Producer] trySend() succeeded: %d\n", rc);
    }
    
    /* Close channel - like Kotlin channel.close() */
    kc_ipc_chan_close(channel);
    printf("[Producer] Closed channel\n");
    
    /* Cleanup */
    kc_ipc_chan_destroy(channel);
    kc_ipc_conn_close(conn);
    printf("[Producer] Done\n");
    
    return 0;
}

/* Consumer - receives messages from channel (like Kotlin consumer coroutine) */
static int consumer_process(void)
{
    printf("[Consumer] Starting...\n");
    sleep(1); /* Give producer time to create channel */
    
    /* Connect to same kcoro server */
    kc_ipc_conn_t *conn = NULL;
    if (kc_ipc_connect(KC_SOCK, &conn) != 0) {
        perror("connect");
        return 1;
    }
    
    /* Handshake */
    uint32_t maj, min;
    if (kc_ipc_hs_cli(conn, &maj, &min) != 0) {
        fprintf(stderr, "[Consumer] Handshake failed\n");
        kc_ipc_conn_close(conn);
        return 1;
    }
    printf("[Consumer] Connected to server ABI %u.%u\n", maj, min);
    
    /* Read shared channel info */
    printf("[Consumer] Waiting for channel info...\n");
    for (int tries = 0; tries < 50; ++tries) {
        uint32_t id=0; int kind=0; size_t es=0;
        if (read_chan_info(&id, &kind, &es) == 0) {
            kc_ipc_chan_t *ich = NULL;
            if (kc_ipc_chan_open(conn, id, kind, es, &ich) == 0) {
                printf("[Consumer] Attached to channel id=%u\n", id);
                /* Simulate consuming messages - like Kotlin: for (msg in channel) { ... } */
                printf("[Consumer] Starting to consume messages...\n");
                for (int i = 0; i < 10; i++) {
                    struct Message msg;
                    printf("[Consumer] Waiting for message...\n");
                    int rc = kc_ipc_chan_recv(ich, &msg, -1);
                    if (rc == 0) {
                        printf("[Consumer] Received message %d: %s\n", msg.id, msg.text);
                    } else if (rc == KC_EPIPE) {
                        printf("[Consumer] Channel closed\n");
                        break;
                    } else {
                        fprintf(stderr, "[Consumer] Receive failed: %d\n", rc);
                        break;
                    }
                    sleep(1);
                }
                /* Try non-blocking receive */
                struct Message msg;
                int rc = kc_ipc_chan_try_recv(ich, &msg);
                if (rc == KC_EAGAIN) {
                    printf("[Consumer] No message available, tryReceive() returned EAGAIN\n");
                } else if (rc == 0) {
                    printf("[Consumer] tryReceive() got message %d: %s\n", msg.id, msg.text);
                }
                kc_ipc_chan_destroy(ich);
                kc_ipc_conn_close(conn);
                printf("[Consumer] Done\n");
                return 0;
            }
        }
        struct timespec ts; ts.tv_sec = 0; ts.tv_nsec = 100000000L; nanosleep(&ts, NULL);
    }
    fprintf(stderr, "[Consumer] Failed to read channel info\n");
    kc_ipc_conn_close(conn);
    return 1;
    
    /* Simulate consuming messages - like Kotlin: for (msg in channel) { ... } */
    printf("[Consumer] Starting to consume messages...\n");
    
    for (int i = 0; i < 10; i++) {
        struct Message msg;
        
        /* Suspending receive - like Kotlin channel.receive() */
        printf("[Consumer] Waiting for message...\n");
        int rc = kc_ipc_chan_recv(NULL, &msg, -1); /* Note: Need channel sharing mechanism */
        if (rc == 0) {
            printf("[Consumer] Received message %d: %s\n", msg.id, msg.text);
        } else if (rc == KC_EPIPE) {
            printf("[Consumer] Channel closed\n");
            break;
        } else {
            fprintf(stderr, "[Consumer] Receive failed: %d\n", rc);
            break;
        }
        
        /* Simulate processing time */
        sleep(1); /* 1s processing time */
    }
    
    /* Try non-blocking receive - like Kotlin channel.tryReceive() */
    struct Message msg;
    int rc = kc_ipc_chan_try_recv(NULL, &msg); /* Note: Need channel sharing */
    if (rc == KC_EAGAIN) {
        printf("[Consumer] No message available, tryReceive() returned EAGAIN\n");
    } else if (rc == 0) {
        printf("[Consumer] tryReceive() got message %d: %s\n", msg.id, msg.text);
    }
    
    kc_ipc_conn_close(conn);
    printf("[Consumer] Done\n");
    
    return 0;
}

/* Simple server that handles channel operations */
/* Simple threaded server that handles channel operations */
struct worker_arg { kc_ipc_server_ctx_t *ctx; kc_ipc_conn_t *conn; };

static void *server_worker(void *arg)
{
    struct worker_arg *wa = (struct worker_arg*)arg;
    kc_ipc_conn_t *conn = wa->conn; kc_ipc_server_ctx_t *ctx = wa->ctx;
    free(wa);
    uint32_t maj=0,min=0;
    (void)kc_ipc_hs_srv(conn, &maj, &min); /* best effort */
    for (;;) {
        uint16_t cmd=0; uint8_t *pl=NULL; size_t len=0;
        int rc = kc_ipc_recv(conn, &cmd, &pl, &len);
        if (rc != 0) { dbg("server recv rc=%d -> exit", rc); break; }
        dbg("server got cmd=%u len=%zu", cmd, len);
        if (cmd == KCORO_CMD_GET_INFO) {
            uint8_t buf[64]; uint8_t *cur=buf, *end=buf+sizeof(buf);
            kc_tlv_put_u32(&cur, end, KCORO_ATTR_ABI_MAJOR, KCORO_PROTO_ABI_MAJOR);
            kc_tlv_put_u32(&cur, end, KCORO_ATTR_ABI_MINOR, KCORO_PROTO_ABI_MINOR);
            kc_tlv_put_u32(&cur, end, KCORO_ATTR_CAPS, 0);
            kc_ipc_send(conn, KCORO_CMD_GET_INFO, buf, (size_t)(cur-buf));
        } else if (cmd == KCORO_CMD_GET_STATS) {
            uint8_t buf[64]; uint8_t *cur=buf, *end=buf+sizeof(buf);
            kc_tlv_put_u32(&cur, end, KCORO_ATTR_SEND_OPS, 0);
            kc_tlv_put_u32(&cur, end, KCORO_ATTR_RECV_OPS, 0);
            kc_ipc_send(conn, KCORO_CMD_GET_STATS, buf, (size_t)(cur-buf));
        } else {
            int hr = kc_ipc_handle_command(ctx, conn, cmd, pl, len);
            dbg("server handled cmd=%u rc=%d", cmd, hr);
        }
        free(pl);
    }
    kc_ipc_conn_close(conn);
    return NULL;
}

static int server_process(void)
{
    printf("[Server] Starting kcoro server...\n");
    
    /* This would use the server-side handler we created */
    /* For now, just demonstrate the IPC server setup */
    
    kc_ipc_server_t *srv = NULL;
    unlink(KC_SOCK);
    if (kc_ipc_srv_listen(KC_SOCK, &srv) != 0) {
        perror("listen");
        return 1;
    }
    printf("[Server] Listening on %s\n", KC_SOCK);

    kc_ipc_server_ctx_t *ctx = kc_ipc_server_ctx_create();
    if (!ctx) { fprintf(stderr, "ctx create failed\n"); kc_ipc_srv_close(srv); return 1; }

    for (;;) {
        kc_ipc_conn_t *conn = NULL;
        if (kc_ipc_srv_accept(srv, &conn) == 0) {
            printf("[Server] Client connected\n");
            pthread_t th; struct worker_arg *wa = malloc(sizeof(*wa));
            if (!wa) { kc_ipc_conn_close(conn); continue; }
            wa->ctx = ctx; wa->conn = conn;
            if (pthread_create(&th, NULL, server_worker, wa) != 0) {
                kc_ipc_conn_close(conn);
                free(wa);
                continue;
            }
            pthread_detach(th);
        } else {
            /* Brief nap to avoid busy loop */
            struct timespec ts; ts.tv_sec = 0; ts.tv_nsec = 100000000L; nanosleep(&ts, NULL);
        }
        /* Stop when parent kills us */
    }

    kc_ipc_server_ctx_destroy(ctx);
    kc_ipc_srv_close(srv);
    printf("[Server] Shutdown\n");
    
    return 0;
}

int main(int argc, char **argv)
{
    /* Parse args */
    for (int i = 1; i < argc; ++i) {
        if (strcmp(argv[i], "--debug") == 0 || strcmp(argv[i], "-d") == 0) {
            enable_debug();
        } else if (strcmp(argv[i], "-h") == 0 || strcmp(argv[i], "--help") == 0) {
            printf("Usage: %s [--debug]\n", argv[0]);
            return 0;
        } else {
            fprintf(stderr, "Unknown arg: %s\n", argv[i]);
            printf("Usage: %s [--debug]\n", argv[0]);
            return 2;
        }
    }

    printf("=== kcoro Distributed Channels Example ===\n");
    printf("Demonstrating Kotlin-like channel patterns over IPC\n\n");
    
    /* Fork server process */
    pid_t server_pid = fork();
    if (server_pid == 0) {
        return server_process();
    } else if (server_pid < 0) {
        perror("fork server");
        return 1;
    }
    
    sleep(1); /* Give server time to start */
    
    /* Fork producer process */
    pid_t producer_pid = fork();
    if (producer_pid == 0) {
        return producer_process();
    } else if (producer_pid < 0) {
        perror("fork producer");
        kill(server_pid, SIGTERM);
        return 1;
    }
    
    /* Fork consumer process */  
    pid_t consumer_pid = fork();
    if (consumer_pid == 0) {
        return consumer_process();
    } else if (consumer_pid < 0) {
        perror("fork consumer");
        kill(producer_pid, SIGTERM);
        kill(server_pid, SIGTERM);
        return 1;
    }
    
    /* Wait for all processes */
    int status;
    waitpid(producer_pid, &status, 0);
    waitpid(consumer_pid, &status, 0);
    kill(server_pid, SIGTERM);
    waitpid(server_pid, &status, 0);
    unlink(KC_CHAN_FILE);
    unlink(KC_SOCK);
    
    printf("\n=== Example Complete ===\n");
    printf("Note: This demonstrates the API design.\n");
    printf("Full implementation needs:\n");
    printf("  - Channel sharing/discovery mechanism\n");
    printf("  - Complete server command processing\n");
    printf("  - Error handling and cleanup\n");
    printf("  - Select/multiplexing support\n");
    
    return 0;
}
