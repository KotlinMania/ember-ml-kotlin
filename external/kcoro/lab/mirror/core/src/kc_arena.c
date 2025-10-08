// SPDX-License-Identifier: BSD-3-Clause
#include "kc_arena.h"

#include <stdlib.h>
#include <errno.h>
#include <pthread.h>

typedef struct kc_arena {
    int active;
    pthread_mutex_t mu;
    size_t bytes_allocated;
} kc_arena;

static kc_arena g_arenas[KC_ARENA_MAX] = {0};
static pthread_mutex_t g_mu = PTHREAD_MUTEX_INITIALIZER;

int kc_arena_create(unsigned arena_id, size_t total_bytes)
{
    (void)total_bytes;
    if (arena_id >= KC_ARENA_MAX) return -EINVAL;
    pthread_mutex_lock(&g_mu);
    kc_arena *arena = &g_arenas[arena_id];
    if (arena->active) {
        pthread_mutex_unlock(&g_mu);
        return -EEXIST;
    }
    arena->active = 1;
    pthread_mutex_init(&arena->mu, NULL);
    arena->bytes_allocated = 0;
    pthread_mutex_unlock(&g_mu);
    return 0;
}

int kc_arena_destroy(unsigned arena_id)
{
    if (arena_id >= KC_ARENA_MAX) return -EINVAL;
    pthread_mutex_lock(&g_mu);
    kc_arena *arena = &g_arenas[arena_id];
    if (!arena->active) {
        pthread_mutex_unlock(&g_mu);
        return -ENOENT;
    }
    pthread_mutex_lock(&arena->mu);
    arena->active = 0;
    arena->bytes_allocated = 0;
    pthread_mutex_unlock(&arena->mu);
    pthread_mutex_destroy(&arena->mu);
    pthread_mutex_unlock(&g_mu);
    return 0;
}

static kc_arena *kc_arena_get(unsigned arena_id)
{
    if (arena_id >= KC_ARENA_MAX) return NULL;
    kc_arena *arena = &g_arenas[arena_id];
    if (!arena->active) return NULL;
    return arena;
}

void *kc_arena_alloc(unsigned arena_id, size_t len)
{
    kc_arena *arena = kc_arena_get(arena_id);
    if (!arena) return NULL;
    void *ptr = NULL;
    if (len) {
        ptr = malloc(len);
        if (!ptr) return NULL;
        pthread_mutex_lock(&arena->mu);
        arena->bytes_allocated += len;
        pthread_mutex_unlock(&arena->mu);
    }
    return ptr;
}

void kc_arena_free(unsigned arena_id, void *ptr, size_t len)
{
    if (!ptr) return;
    kc_arena *arena = kc_arena_get(arena_id);
    if (arena) {
        pthread_mutex_lock(&arena->mu);
        if (arena->bytes_allocated >= len) arena->bytes_allocated -= len;
        pthread_mutex_unlock(&arena->mu);
    }
    free(ptr);
}
