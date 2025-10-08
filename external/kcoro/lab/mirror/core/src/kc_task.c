/* Removed experimental task system implementation.
 * This file intentionally left as a stub so that any stale build reference
 * produces a clear compile-time error. See src/kcoro/docs/components/developer/MAINTENANCE.md for the
 * forward-looking design notes.
 */

#error "kc_task.c removed: experimental task API purged (see docs/components/developer/MAINTENANCE.md)"

void kc_task_set_context(kc_task_t* task, kc_task_ctx_t* ctx)
{
    if (task) {
        task->ctx = ctx;
        task->state = KC_TASK_RUNNING;
    }
}

/* External API */
kc_task_ctx_t* kc_current_task_ctx(void)
{
    return g_task_ctx;
}

kc_task_t* kc_current_task(void)
{
    return g_task_ctx ? g_task_ctx->current_task : NULL;
}

void kc_task_yield(void)
{
    kc_task_ctx_t* ctx = kc_current_task_ctx();
    if (!ctx || !ctx->current_task) {
        /* Not in task context - fallback to thread yield */
        sched_yield();
        return;
    }
    
    /* TODO: Implement task rescheduling through scheduler */
    /* For now, fallback to thread yield */
    sched_yield();
}

void kc_task_block(void)
{
    kc_task_ctx_t* ctx = kc_current_task_ctx();
    if (!ctx || !ctx->current_task) {
        /* Not in task context - can't block task */
        return;
    }
    
    ctx->current_task->state = KC_TASK_BLOCKED;
    /* TODO: Park task and yield to scheduler */
    /* For now, just yield thread */
    sched_yield();
}

void kc_task_wake(kc_task_t* task)
{
    if (!task) return;
    
    if (task->state == KC_TASK_BLOCKED) {
        task->state = KC_TASK_READY;
        task->wait_object = NULL;
        /* TODO: Add task back to scheduler ready queue */
    }
}

void kc_task_sleep_ms(int ms)
{
    if (ms <= 0) return;
    
    kc_task_ctx_t* ctx = kc_current_task_ctx();
    if (!ctx || !ctx->current_task) {
        /* Not in task context - fallback to thread sleep */
        struct timespec ts = { ms/1000, (ms%1000)*1000000L };
        nanosleep(&ts, NULL);
        return;
    }
    
    /* TODO: Implement timer-based task sleeping */
    /* For now, fallback to thread sleep */
    struct timespec ts = { ms/1000, (ms%1000)*1000000L };
    nanosleep(&ts, NULL);
}

/* Internal task context management */
void kc_task_ctx_init(kc_task_ctx_t* ctx, struct kc_sched* scheduler, int worker_id)
{
    if (ctx) {
        ctx->current_task = NULL;
        ctx->scheduler = scheduler;
        ctx->worker_id = worker_id;
        g_task_ctx = ctx;
    }
}

void kc_task_ctx_set_current(kc_task_ctx_t* ctx, kc_task_t* task)
{
    if (ctx) {
        ctx->current_task = task;
        if (task) {
            task->ctx = ctx;
            task->state = KC_TASK_RUNNING;
        }
    }
}

void kc_task_ctx_clear_current(kc_task_ctx_t* ctx)
{
    if (ctx && ctx->current_task) {
        if (ctx->current_task->state == KC_TASK_RUNNING) {
            ctx->current_task->state = KC_TASK_FINISHED;
        }
        ctx->current_task->ctx = NULL;
        ctx->current_task = NULL;
    }
}
