@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package ai.solace.klang.kcoro

import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointed

/** Lightweight stub to keep kcoro-dependent code compiling when the native library is unavailable. */
class KcoroScheduler
class KcoroChannel
class KcoroHandle

object KcoroInterop {
    val isAvailable: Boolean = false

    fun createScheduler(workers: Int = 0): KcoroScheduler? = null
    fun shutdownScheduler(sched: KcoroScheduler?) {}
    fun drainScheduler(sched: KcoroScheduler?, timeoutMs: Long = -1L): Int = -1

    fun createChannel(kind: Int, elemSize: ULong, capacity: ULong = 0u): KcoroChannel? = null
    fun destroyChannel(ch: KcoroChannel?) {}
    fun closeChannel(ch: KcoroChannel?) {}

    fun send(channel: KcoroChannel?, data: CPointer<CPointed>, timeoutMs: Long = -1L): Int = -1
    fun recv(channel: KcoroChannel?, data: CPointer<CPointed>, timeoutMs: Long = -1L): Int = -1

    fun spawnCoroutine(
        sched: KcoroScheduler?,
        fn: CPointer<CFunction<(COpaquePointer?) -> Unit>>,
        arg: COpaquePointer?,
        stackBytes: ULong = 64uL * 1024uL,
    ): Pair<Int, KcoroHandle?> = (-1) to null

    fun spawnTask(
        sched: KcoroScheduler?,
        fn: CPointer<CFunction<(COpaquePointer?) -> Unit>>,
        arg: COpaquePointer?,
    ): Int = -1

    fun yield() {}
    fun sleepMs(ms: Int) {}
}

const val KC_BUFFERED: Int = 1
