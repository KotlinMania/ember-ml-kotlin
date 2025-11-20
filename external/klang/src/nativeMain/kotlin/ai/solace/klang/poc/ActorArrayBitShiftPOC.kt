package ai.solace.klang.poc

import ai.solace.klang.bitwise.ArrayBitShifts
import ai.solace.klang.bitwise.BitShiftConfig
import ai.solace.klang.bitwise.BitShiftMode
import ai.solace.klang.int.SwAR128
import ai.solace.klang.mem.GlobalHeap
import ai.solace.klang.mem.KMalloc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Heap-only actor pipeline for shifting 16-bit little-endian limbs in place.
 * This is the canonical POC; all other variants were removed.
 */
private const val LIMB_BITS = 16
private const val LIMB_MASK = 0xFFFF

object ActorArrayBitShiftPOC {

    data class ShiftTask(
        val sequence: Int,
        val offset: Int, // limb offset
        val length: Int, // limb count
        val carryIn: Int,
    ) { var base: Int = 0 }

    data class ShiftSegment(
        val sequence: Int,
        val offset: Int,
        val carryOut: Int,
    )

    fun shiftLeftWithActors(
        input: IntArray,
        bits: Int,
        chunkSize: Int = 512,
        workerCount: Int = 2,
    ): IntArray = runBlocking {
        require(bits >= 0) { "Shift count must be non-negative" }
        require(chunkSize > 0) { "Chunk size must be positive" }
        require(chunkSize % SwAR128.LIMB_COUNT == 0) { "Chunk size must be a multiple of ${SwAR128.LIMB_COUNT}" }

        if (bits == 0 || input.isEmpty()) return@runBlocking input.copyOf()

        val wordShift = bits / LIMB_BITS
        val bitShift = bits % LIMB_BITS

        val limbCount = input.size + wordShift + 1
        val base = KMalloc.malloc(limbCount * 2)
        // write input into heap at wordShift
        var i = 0
        while (i < input.size) {
            val v = input[i] and 0xFFFF
            val off = base + (wordShift + i) * 2
            GlobalHeap.sb(off, (v and 0xFF).toByte())
            GlobalHeap.sb(off + 1, ((v ushr 8) and 0xFF).toByte())
            i++
        }
        // zero pre/post
        var z = 0
        while (z < wordShift) { GlobalHeap.sh(base + z * 2, 0); z++ }
        GlobalHeap.sh(base + (limbCount - 1) * 2, 0)

        if (bitShift == 0) {
            val out = readTrimmed(base, limbCount)
            KMalloc.free(base)
            return@runBlocking out
        }

        val chunkCount = (limbCount + chunkSize - 1) / chunkSize
        val carryChannel = Channel<Int>(capacity = 1)
        val taskChannel = Channel<ShiftTask>(capacity = workerCount)
        val segmentChannel = Channel<ShiftSegment>(capacity = workerCount)

        carryChannel.send(0)

        val scope = CoroutineScope(Dispatchers.Default + Job())

        val producer = scope.launch {
            for (sequence in 0 until chunkCount) {
                val carryIn = carryChannel.receive()
                val offset = sequence * chunkSize
                val len = kotlin.math.min(chunkSize, limbCount - offset)
                taskChannel.send(ShiftTask(sequence, offset, len, carryIn).also { it.base = base })
            }
            taskChannel.close()
        }

        repeat(workerCount) {
            scope.launch {
                for (task in taskChannel) {
                    val res = ArrayBitShifts.shl16LEInPlace(task.base, task.offset, task.length, bitShift, task.carryIn)
                    segmentChannel.send(ShiftSegment(task.sequence, task.offset, res.carryOut))
                }
            }
        }

        val consumer = scope.launch {
            val buffer = HashMap<Int, ShiftSegment>()
            var expected = 0
            repeat(chunkCount) {
                val segment = segmentChannel.receive()
                buffer[segment.sequence] = segment
                while (true) {
                    val next = buffer.remove(expected) ?: break
                    if (expected + 1 < chunkCount) {
                        carryChannel.send(next.carryOut)
                    } else {
                        GlobalHeap.sh(base + (limbCount - 1) * 2, (next.carryOut and 0xFFFF).toShort())
                    }
                    expected++
                }
            }
            segmentChannel.close()
            carryChannel.close()
        }

        producer.join()
        consumer.join()
        scope.coroutineContext[Job]?.cancel()

        val out = readTrimmed(base, limbCount)
        KMalloc.free(base)
        out
    }
}

private fun readTrimmed(base: Int, limbCount: Int): IntArray {
    val data = IntArray(limbCount)
    var i = 0
    while (i < limbCount) {
        val off = base + i * 2
        data[i] = GlobalHeap.lbu(off) or (GlobalHeap.lbu(off + 1) shl 8)
        i++
    }
    return trim(data)
}

private fun trim(limbs: IntArray): IntArray {
    var last = limbs.size - 1
    while (last > 0 && limbs[last] == 0) last--
    return limbs.copyOf(last + 1)
}

// Minimal native entry point that exercises the heap-only actor path.
fun main() {
    BitShiftConfig.defaultMode = BitShiftMode.NATIVE
    val sample = intArrayOf(0x1234, 0xABCD, 0x00FF, 0xC0DE)
    val res = ActorArrayBitShiftPOC.shiftLeftWithActors(sample, bits = 37, chunkSize = 16, workerCount = 2)
    println("POC heap-actor shift ok: ${res.isNotEmpty()}")
}

