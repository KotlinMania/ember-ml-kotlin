package ai.solace.klang.bitwise

import kotlin.test.Test
import kotlin.test.assertEquals

class Float32ConvertTest {
    private fun bits(x: Float) = x.toRawBits()

    @Test
    fun intToFloat_basic() {
        val cases = listOf(0, 1, -1, 2, -2, 1234567, -7654321, Int.MAX_VALUE, Int.MIN_VALUE)
        for (i in cases) {
            val got = Float32Math.intToFloat(i)
            val ref = i.toFloat()
            assertEquals(bits(ref), bits(got), "i=$i")
        }
    }

    @Test
    fun uintToFloat_basic() {
        val cases = listOf(0u, 1u, 2u, 1234567u, 0xFFFFu, 0x7FFFu, 0xFFFF_FFFFu)
        for (u in cases) {
            val got = Float32Math.uintToFloat(u)
            val ref = u.toFloat()
            assertEquals(bits(ref), bits(got), "u=$u")
        }
    }
}

