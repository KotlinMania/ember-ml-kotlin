package ai.solace.klang.int.hpc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HPC16xTests {
    @Test
    fun addSubRoundtrip64() {
        val a = HPC16x4.fromULong(0xFFFF_FFFFuL)
        val b = HPC16x4.fromULong(0x1234_5678uL)
        val (sum, carry) = a.add(b)
        val (back, borrow) = sum.sub(b)
        assertEquals(0u, borrow.toUInt())
        assertEquals(a.toULong(), back.toULong())
        val aU = 0xFFFF_FFFFuL
        val bU = 0x1234_5678uL
        val sumU = aU + bU
        // Carry here means carry out of 64 bits (not 32).
        val expectedCarry = if (sumU > ULong.MAX_VALUE) 1u else 0u
        assertEquals(expectedCarry, carry.toUInt())
    }

    @Test
    fun mul64x64To128_basic() {
        val a = HPC16x4.fromULong(0x0000_0001_FFFFuL)
        val b = HPC16x4.fromULong(0x0000_0001_0001uL)
        val prod = HPC16x8.mul64x64To128(a, b)
        // Convert back via high/low halves to check a few limbs
        // Expected lower 32 bits: (0x1FFFF * 0x10001) & 0xFFFF_FFFF
        val low32 = ((0x1FFFFuL * 0x10001uL) and 0xFFFF_FFFFuL).toUInt()
        val low16 = low32 and 0xFFFFu
        val next16 = (low32 shr 16) and 0xFFFFu
        assertEquals(low16.toUShort(), prod.limb(0))
        assertEquals(next16.toUShort(), prod.limb(1))
        assertTrue(prod.compareTo(prod.copy()) == 0)
    }
}
