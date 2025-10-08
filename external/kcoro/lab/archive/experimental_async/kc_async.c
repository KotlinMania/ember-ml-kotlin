// SPDX-License-Identifier: BSD-3-Clause
/*
 * Archived experimental async/await implementation.
 * See README.md in this directory for details.
 */
#ifdef KC_SCHED
#ifdef KC_ASYNC_EXPERIMENTAL

#ifndef _GNU_SOURCE
#define _GNU_SOURCE 1
#endif
#define _POSIX_C_SOURCE 200809L

#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <time.h>

#include "kcoro_async.h"
#include "kcoro_task.h"
#include "kcoro.h"

int kc_chan_send_async(kc_chan_t* ch, const void* data)
{
    if (!ch || !data) return -EINVAL;
    /* experimental task API removed */
    if (!current_task) return kc_chan_send(ch, data, -1);
    int result = kc_chan_send(ch, data, 0);
    if (result == 0) return 0;
    if (result != -EAGAIN) return result;
    sched_yield();
    return kc_chan_send(ch, data, 0);
}

int kc_chan_recv_async(kc_chan_t* ch, void* data)
{
    if (!ch || !data) return -EINVAL;
    /* experimental task API removed */
    if (!current_task) return kc_chan_recv(ch, data, -1);
    int result = kc_chan_recv(ch, data, 0);
    if (result == 0) return 0;
    if (result != -EAGAIN) return result;
    sched_yield();
    return kc_chan_recv(ch, data, 0);
}

int kc_chan_try_send_async(kc_chan_t* ch, const void* data)
{
    if (!ch || !data) return -EINVAL;
    return kc_chan_send(ch, data, 0);
}

int kc_chan_try_recv_async(kc_chan_t* ch, void* data)
{
    if (!ch || !data) return -EINVAL;
    return kc_chan_recv(ch, data, 0);
}

int kc_chan_send_timeout_async(kc_chan_t* ch, const void* data, int timeout_ms)
{
    if (!ch || !data) return -EINVAL;
    /* experimental task API removed */
    if (!current_task) return kc_chan_send(ch, data, timeout_ms);
    int result = kc_chan_send(ch, data, 0);
    if (result == 0) return 0;
    if (result != -EAGAIN) return result;
    if (timeout_ms > 0) { struct timespec ts = { timeout_ms/1000, (timeout_ms%1000)*1000000L }; nanosleep(&ts, NULL); } else sched_yield();
    return kc_chan_send(ch, data, 0);
}

int kc_chan_recv_timeout_async(kc_chan_t* ch, void* data, int timeout_ms)
{
    if (!ch || !data) return -EINVAL;
    /* experimental task API removed */
    if (!current_task) return kc_chan_recv(ch, data, timeout_ms);
    int result = kc_chan_recv(ch, data, 0);
    if (result == 0) return 0;
    if (result != -EAGAIN) return result;
    if (timeout_ms > 0) { struct timespec ts = { timeout_ms/1000, (timeout_ms%1000)*1000000L }; nanosleep(&ts, NULL); } else sched_yield();
    return kc_chan_recv(ch, data, 0);
}

#endif /* KC_ASYNC_EXPERIMENTAL */
#endif /* KC_SCHED */

