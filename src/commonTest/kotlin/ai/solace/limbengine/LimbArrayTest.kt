package ai.solace.limbengine

import ai.solace.ember.backend.klang.LimbArray
import ai.solace.ember.backend.klang.LimbEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LimbArrayTest {

    @Test
    fun `flush applies queued operations to all elements`() {
        val first = LimbEngine(mantissa = intArrayOf(0x1234))
        val second = LimbEngine(mantissa = intArrayOf(0x00FF))
        val array = LimbArray.of(first, second)

        array
            .shiftLeft(4)
            .bitAnd(intArrayOf(0x0FF0))
            .shiftRight(1)
            .flush()

        val results = array.toList().map { it.mantissaLimbs()[0] }
        assertEquals(((0x1234 shl 3) and 0x0FF0) and MASK, results[0])
        assertEquals(((0x00FF shl 3) and 0x0FF0) and MASK, results[1])
    }

    @Test
    fun `auto flush threshold triggers without explicit flush`() {
        val array = LimbArray.of(
            LimbEngine(mantissa = intArrayOf(0x0001)),
            autoFlushThreshold = 1,
        )

        array.shiftLeft(1) // should flush immediately

        val value = array[0].mantissaLimbs()[0]
        assertEquals(0x0002, value)
    }

    @Test
    fun `read barrier on get triggers flush`() {
        val array = LimbArray.of(
            LimbEngine(mantissa = intArrayOf(0x00F0)),
            autoFlushThreshold = Int.MAX_VALUE,
        )

        array.shiftLeft(2).bitXor(intArrayOf(0x000F))

        // Accessing an element should materialize queued ops
        val value = array[0].mantissaLimbs()[0]
        assertEquals(((0x00F0 shl 2) xor 0x000F) and MASK, value)
    }

    companion object {
        private const val MASK = (1 shl 16) - 1
    }

    @Test
    fun `autogate off uses DAG semantics`() {
        val first = LimbEngine(mantissa = intArrayOf(0x1234))
        val arr = LimbArray.of(first, autoFlushThreshold = Int.MAX_VALUE, dagEnabled = true, autoGate = false)

        arr
            .shiftLeft(4)
            .bitAnd(intArrayOf(0xFFF0))
            .shiftRight(2)
            .bitOr(intArrayOf(0x0003))
            .flush()

        val v = arr[0].mantissaLimbs()[0]
        val fused = (((0x1234 shl 2) and 0xFFF0) or 0x0003) and MASK
        assertEquals(fused, v)
    }

    @Test
    fun `autogate on yields either fused or sequential result`() {
        val first = LimbEngine(mantissa = intArrayOf(0x1234))
        val arr = LimbArray.of(first, autoFlushThreshold = Int.MAX_VALUE, dagEnabled = true, autoGate = true)

        arr
            .shiftLeft(4)
            .bitAnd(intArrayOf(0xFFF0))
            .shiftRight(2)
            .bitOr(intArrayOf(0x0003))
            .flush()

        val v = arr[0].mantissaLimbs()[0]
        val fused = (((0x1234 shl 2) and 0xFFF0) or 0x0003) and MASK
        val seq1 = (0x1234 shl 4) and MASK
        val seq2 = (seq1 and 0xFFF0) and MASK
        val seq3 = (seq2 ushr 2) and MASK
        val sequential = (seq3 or 0x0003) and MASK
        kotlin.test.assertTrue(v == fused || v == sequential)
    }
}
