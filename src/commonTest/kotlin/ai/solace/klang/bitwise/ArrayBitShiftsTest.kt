package ai.solace.klang.bitwise

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArrayBitShiftsTest {
    @Test
    fun shl16_basic() {
        val a = intArrayOf(0x0001, 0x0000, 0x0000)
        val res = ArrayBitShifts.shl16LEInPlace(a, 0, 3, 1)
        // [0x0002, 0x0000, 0x0000], carryOut=0
        assertEquals(0, res.carryOut)
        assertEquals(0x0002, a[0])
    }

    @Test
    fun rsh16_sticky() {
        val a = intArrayOf(0x0003, 0x8000)
        val res = ArrayBitShifts.rsh16LEInPlace(a, 0, 2, 1)
        // Value 0x8000_0003 >> 1 = 0x4000_0001 -> limbs [0x0001, 0x4000]
        assertTrue(res.sticky)
        assertEquals(1, res.carryOut)
        assertEquals(0x0001, a[0])
        assertEquals(0x4000, a[1])
    }
}
