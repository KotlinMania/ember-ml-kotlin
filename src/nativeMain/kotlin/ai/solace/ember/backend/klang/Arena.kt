package ai.solace.ember.backend.klang

import ai.solace.klang.mem.GlobalHeap
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Native-only bump allocator. Not thread-safe; pair with [HeapActor] for shared use.
 */
class Arena(
    val basePtr: Int,
    val capacityBytes: Int,
    private val alignment: Int = 16
) {
    private val offset = atomic(0)

    fun alloc(bytes: Int): Int {
        require(bytes >= 0)
        val need = align(max(1, bytes))
        while (true) {
            val cur = offset.value
            val next = cur + need
            if (next > capacityBytes) throw OutOfMemoryError("arena exhausted: $cur + $need > $capacityBytes")
            if (offset.compareAndSet(cur, next)) return basePtr + cur
        }
    }

    fun reset() { offset.value = 0 }

    private fun align(n: Int): Int = (n + (alignment - 1)) and alignment.inv().plus(1)
}

/**
 * Serialize access to a shared [Arena] when multiple coroutines/threads need it.
 */
class HeapActor private constructor(
    private val arena: Arena,
    private val scope: CoroutineScope
) {
    private val mailbox = Channel<Msg>(Channel.UNLIMITED)

    private sealed interface Msg
    private data class Alloc(val bytes: Int, val reply: Channel<Int>) : Msg
    private data class Write(val addr: Int, val data: IntArray, val reply: Channel<Unit>) : Msg
    private data class Read(val addr: Int, val count: Int, val reply: Channel<IntArray>) : Msg
    private data class Reset(val reply: Channel<Unit>) : Msg

    init {
        scope.launch {
            for (m in mailbox) {
                when (m) {
                    is Alloc -> {
                        m.reply.send(arena.alloc(m.bytes)); m.reply.close()
                    }
                    is Write -> {
                        var p = m.addr
                        m.data.forEach { w -> GlobalHeap.sw(p, w); p += 4 }
                        m.reply.send(Unit); m.reply.close()
                    }
                    is Read -> {
                        val out = IntArray(m.count)
                        var p = m.addr
                        for (i in 0 until m.count) { out[i] = GlobalHeap.lw(p); p += 4 }
                        m.reply.send(out); m.reply.close()
                    }
                    is Reset -> {
                        arena.reset()
                        m.reply.send(Unit); m.reply.close()
                    }
                }
            }
        }
    }

    suspend fun alloc(bytes: Int): Int {
        val reply = Channel<Int>(1)
        mailbox.send(Alloc(bytes, reply))
        return reply.receive()
    }

    suspend fun writeInts(addr: Int, data: IntArray) {
        val reply = Channel<Unit>(1)
        mailbox.send(Write(addr, data, reply))
        reply.receive()
    }

    suspend fun readInts(addr: Int, count: Int): IntArray {
        val reply = Channel<IntArray>(1)
        mailbox.send(Read(addr, count, reply))
        return reply.receive()
    }

    suspend fun reset() {
        val reply = Channel<Unit>(1)
        mailbox.send(Reset(reply))
        reply.receive()
    }

    companion object {
        fun start(
            arena: Arena,
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        ): HeapActor = HeapActor(arena, scope)
    }
}
