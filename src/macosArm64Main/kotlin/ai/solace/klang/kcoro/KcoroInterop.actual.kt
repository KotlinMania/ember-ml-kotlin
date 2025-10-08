@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package ai.solace.klang.kcoro

import ai.solace.kcoro.*
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value

actual class KcoroScheduler internal constructor(internal val ptr: CPointer<kc_sched_t>)
actual class KcoroChannel internal constructor(internal val ptr: CPointer<kc_chan_t>)
actual class KcoroHandle internal constructor(internal val ptr: CPointer<kcoro_t>)

private fun KcoroScheduler?.raw(): CPointer<kc_sched_t>? = this?.ptr
private fun KcoroChannel?.raw(): CPointer<kc_chan_t>? = this?.ptr
private fun KcoroHandle?.raw(): CPointer<kcoro_t>? = this?.ptr

actual object KcoroInterop {
    actual val isAvailable: Boolean = true

    actual fun createScheduler(workers: Int): KcoroScheduler? = memScoped {
        val opts = alloc<kc_sched_opts_t>().apply {
            this.workers = workers
            queue_capacity = 0
            inject_q_cap = 0
        }
        kc_sched_init(opts.ptr)?.let { KcoroScheduler(it) }
    }

    actual fun shutdownScheduler(sched: KcoroScheduler?) {
        sched.raw()?.let { kc_sched_shutdown(it) }
    }

    actual fun drainScheduler(sched: KcoroScheduler?, timeoutMs: Long): Int {
        val raw = sched.raw() ?: return -95
        return kc_sched_drain(raw, timeoutMs)
    }

    actual fun createChannel(kind: Int, elemSize: ULong, capacity: ULong): KcoroChannel? = memScoped {
        val out = alloc<CPointerVar<kc_chan_t>>()
        val rc = kc_chan_make(out.ptr, kind, elemSize, capacity)
        require(rc == 0) { "kc_chan_make failed rc=$rc" }
        out.value?.let { KcoroChannel(it) }
    }

    actual fun destroyChannel(ch: KcoroChannel?) {
        ch.raw()?.let { kc_chan_destroy(it) }
    }

    actual fun closeChannel(ch: KcoroChannel?) {
        ch.raw()?.let { kc_chan_close(it) }
    }

    actual fun send(channel: KcoroChannel?, data: CPointer<CPointed>, timeoutMs: Long): Int {
        val ptr = channel.raw() ?: return -95
        return kc_chan_send(ptr, data, timeoutMs)
    }

    actual fun recv(channel: KcoroChannel?, data: CPointer<CPointed>, timeoutMs: Long): Int {
        val ptr = channel.raw() ?: return -95
        return kc_chan_recv(ptr, data, timeoutMs)
    }

    actual fun spawnCoroutine(
        sched: KcoroScheduler?,
        fn: CPointer<CFunction<(COpaquePointer?) -> Unit>>,
        arg: COpaquePointer?,
        stackBytes: ULong,
    ): Pair<Int, KcoroHandle?> = memScoped {
        val out = alloc<CPointerVar<kcoro_t>>()
        val rc = kc_spawn_co(sched.raw(), fn, arg, stackBytes, out.ptr)
        rc to out.value?.let { KcoroHandle(it) }
    }

    actual fun spawnTask(
        sched: KcoroScheduler?,
        fn: CPointer<CFunction<(COpaquePointer?) -> Unit>>,
        arg: COpaquePointer?,
    ): Int = kc_spawn(sched.raw(), fn, arg)

    actual fun yield() = kc_yield()
    actual fun sleepMs(ms: Int) = kc_sleep_ms(ms)
}
