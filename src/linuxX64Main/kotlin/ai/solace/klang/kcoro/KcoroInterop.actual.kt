@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package ai.solace.klang.kcoro

import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointed

actual class KcoroScheduler internal constructor()
actual class KcoroChannel internal constructor()
actual class KcoroHandle internal constructor()

actual object KcoroInterop {
    actual val isAvailable: Boolean = false

    actual fun createScheduler(workers: Int): KcoroScheduler? = null

    actual fun shutdownScheduler(sched: KcoroScheduler?) {}

    actual fun drainScheduler(sched: KcoroScheduler?, timeoutMs: Long): Int = -95

    actual fun createChannel(kind: Int, elemSize: ULong, capacity: ULong): KcoroChannel? = null

    actual fun destroyChannel(ch: KcoroChannel?) {}

    actual fun closeChannel(ch: KcoroChannel?) {}

    actual fun send(channel: KcoroChannel?, data: CPointer<CPointed>, timeoutMs: Long): Int = -95

    actual fun recv(channel: KcoroChannel?, data: CPointer<CPointed>, timeoutMs: Long): Int = -95

    actual fun spawnCoroutine(
        sched: KcoroScheduler?,
        fn: CPointer<CFunction<(COpaquePointer?) -> Unit>>,
        arg: COpaquePointer?,
        stackBytes: ULong,
    ): Pair<Int, KcoroHandle?> = -95 to null

    actual fun spawnTask(
        sched: KcoroScheduler?,
        fn: CPointer<CFunction<(COpaquePointer?) -> Unit>>,
        arg: COpaquePointer?,
    ): Int = -95

    actual fun yield() {}
    actual fun sleepMs(ms: Int) {}
}
