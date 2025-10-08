package ai.solace.klang.bitwise

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Float32MathAddExactTest {
    @Test
    fun largePlusSmallTieToEven() {
        val a = 16_000_000f
        val b = 3.5f
        val expect = a + b
        val got = Float32Math.add(a, b)
        assertEquals(expect.toRawBits(), got.toRawBits(), "tie-to-even add failed: a=$a b=$b expect=$expect got=$got")
    }

    @Test
    fun signedZeroRules() {
        val pz = 0.0f
        val nz = -0.0f
        // +0 + -0 == +0
        val got = Float32Math.add(pz, nz)
        assertEquals(0, got.toRawBits(), "+0 + -0 should be +0")
    }

    @Test
    fun infinities() {
        val inf = Float.POSITIVE_INFINITY
        val ninf = Float.NEGATIVE_INFINITY
        // inf + 1 == inf
        assertTrue(Float32Math.add(inf, 1f).isInfinite())
        // inf + -inf == NaN
        assertTrue(Float32Math.add(inf, ninf).isNaN())
    }

    @Test
    fun nanPropagation() {
        val qnan = Float.fromBits(0x7FC00001.toInt())
        val res = Float32Math.add(qnan, 1f)
        assertTrue(res.isNaN())
    }
}
