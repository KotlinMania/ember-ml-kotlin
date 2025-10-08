@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package ai.solace.klang.kcoro

import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointed

expect class KcoroScheduler
expect class KcoroChannel
expect class KcoroHandle

expect object KcoroInterop {
    val isAvailable: Boolean

    fun createScheduler(workers: Int = 0): KcoroScheduler?
    fun shutdownScheduler(sched: KcoroScheduler?)
    fun drainScheduler(sched: KcoroScheduler?, timeoutMs: Long = -1L): Int

    fun createChannel(kind: Int, elemSize: ULong, capacity: ULong = 0u): KcoroChannel?
    fun destroyChannel(ch: KcoroChannel?)
    fun closeChannel(ch: KcoroChannel?)

    fun send(channel: KcoroChannel?, data: CPointer<CPointed>, timeoutMs: Long = -1L): Int
    fun recv(channel: KcoroChannel?, data: CPointer<CPointed>, timeoutMs: Long = -1L): Int

    fun spawnCoroutine(
        sched: KcoroScheduler?,
        fn: CPointer<CFunction<(COpaquePointer?) -> Unit>>,
        arg: COpaquePointer?,
        stackBytes: ULong = 64uL * 1024uL,
    ): Pair<Int, KcoroHandle?>

    fun spawnTask(
        sched: KcoroScheduler?,
        fn: CPointer<CFunction<(COpaquePointer?) -> Unit>>,
        arg: COpaquePointer?,
    ): Int

    fun yield()
    fun sleepMs(ms: Int)
}
