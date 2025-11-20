package ai.solace.limbengine

import ai.solace.ember.backend.klang.LimbEngine
import kotlin.test.Test
import kotlin.test.assertEquals

class LimbEngineShiftFusionTest {

    @Test
    fun `shift left then right fuses`() {
        val engine = LimbEngine(mantissa = intArrayOf(0x1234))
        val result = engine
            .shiftLeft(4)
            .shiftRight(2)
            .flush()
            .mantissaLimbs()[0]

        assertEquals(0x1234 shl 2 and MASK, result)
    }

    @Test
    fun `shift right then left fuses`() {
        val engine = LimbEngine(mantissa = intArrayOf(0xFF00))
        val result = engine
            .shiftRight(4)
            .shiftLeft(1)
            .flush()
            .mantissaLimbs()[0]

        assertEquals(0xFF00 shr 3, result)
    }

    @Test
    fun `bitand merges masks`() {
        val engine = LimbEngine(mantissa = intArrayOf(0xFFFF))
        val value = engine
            .bitAnd(intArrayOf(0x0F0F))
            .bitAnd(intArrayOf(0x00FF))
            .flush()
            .mantissaLimbs()[0]

        assertEquals(0x00, value shr 8)
        assertEquals(0x0F, value and 0xFF)
    }

    @Test
    fun `xor duplicates cancel`() {
        val engine = LimbEngine(mantissa = intArrayOf(0xAAAA))
        val value = engine
            .bitXor(intArrayOf(0x0F0F))
            .bitXor(intArrayOf(0x0F0F))
            .flush()
            .mantissaLimbs()[0]

        assertEquals(0xAAAA, value)
    }

    @Test
    fun `mixed operations flush correctly`() {
        val engine = LimbEngine(mantissa = intArrayOf(0x1234))
        val result = engine
            .shiftLeft(4)
            .bitAnd(intArrayOf(0xFFF0))
            .shiftRight(2)
            .bitOr(intArrayOf(0x0003))
            .flush()
            .mantissaLimbs()[0]

        assertEquals(((0x1234 shl 2) and 0xFFF0) or 0x0003, result)
    }

    companion object {
        private const val MASK: Int = (1 shl 16) - 1
    }
}
