package ai.solace.klang.bitwise

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class SwARTest {
    @Test
    fun testU8ParityRandom() {
        val rnd = Random(42)
        repeat(10_000) {
            val a = rnd.nextInt()
            val b = rnd.nextInt()
            assertEquals(SwAR.refAvgU8Trunc(a, b), SwAR.avgU8Trunc(a, b))
            assertEquals(SwAR.refAvgU8Round(a, b), SwAR.avgU8Round(a, b))
            assertEquals(SwAR.refAvgU8Trunc(a, b), SwAR.avgU8TruncArith(a, b))
            assertEquals(SwAR.refAvgU8Round(a, b), SwAR.avgU8RoundArith(a, b))
            assertEquals(SwAR.refAvgU8Trunc(a, b), SwAR.avgU8TruncLutArith(a, b))
            assertEquals(SwAR.refAvgU8Round(a, b), SwAR.avgU8RoundLutArith(a, b))
        }
    }

    @Test
    fun testU16ParityRandom() {
        val rnd = Random(99)
        repeat(10_000) {
            val a = rnd.nextInt()
            val b = rnd.nextInt()
            assertEquals(SwAR.refAvgU16Trunc(a, b), SwAR.avgU16Trunc(a, b))
            assertEquals(SwAR.refAvgU16Round(a, b), SwAR.avgU16Round(a, b))
            assertEquals(SwAR.refAvgU16Trunc(a, b), SwAR.avgU16TruncArith(a, b))
            assertEquals(SwAR.refAvgU16Round(a, b), SwAR.avgU16RoundArith(a, b))
        }
    }

    @Test
    fun testEdgeCases() {
        // u8 extremes per lane
        val zero = 0x00000000
        val ones = 0x01010101
        val ff   = 0xFFFFFFFF.toInt()

        assertEquals(SwAR.refAvgU8Trunc(zero, ff), SwAR.avgU8Trunc(zero, ff))
        assertEquals(SwAR.refAvgU8Round(zero, ff), SwAR.avgU8Round(zero, ff))
        assertEquals(SwAR.refAvgU8Trunc(ones, ff), SwAR.avgU8Trunc(ones, ff))
        assertEquals(SwAR.refAvgU8Round(ones, ff), SwAR.avgU8Round(ones, ff))

        // u16 extremes per lane
        val z16 = 0x0000_0000
        val f16 = 0xFFFF_FFFF.toInt()
        assertEquals(SwAR.refAvgU16Trunc(z16, f16), SwAR.avgU16Trunc(z16, f16))
        assertEquals(SwAR.refAvgU16Round(z16, f16), SwAR.avgU16Round(z16, f16))
    }
}
