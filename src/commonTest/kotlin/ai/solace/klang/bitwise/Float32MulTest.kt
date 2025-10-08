package ai.solace.klang.bitwise

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Float32MulTest {
    private fun bits(x: Float) = x.toRawBits()

    @Test
    fun basicProducts() {
        val cases = listOf(
            4.0f to 2.0f,
            -9.0f to 3.0f,
            1.5f to 2.0f,
            -1.5f to -2.0f,
            7.25f to -0.5f,
        )
        for ((a,b) in cases) {
            val got = CFloat32.fromFloat(a) * CFloat32.fromFloat(b)
            assertEquals(bits(a*b), got.toFloat().toRawBits(), "a=$a b=$b")
        }
    }

    @Test
    fun zerosAndSigns() {
        val pz = 0.0f
        val nz = -0.0f
        val a = 5.0f
        assertEquals(bits(pz), (CFloat32.fromFloat(pz) * CFloat32.fromFloat(a)).toFloat().toRawBits())
        assertEquals(bits(nz), (CFloat32.fromFloat(nz) * CFloat32.fromFloat(a)).toFloat().toRawBits())
        assertEquals(bits(nz), (CFloat32.fromFloat(a) * CFloat32.fromFloat(nz)).toFloat().toRawBits())
    }

    @Test
    fun infAndNaN() {
        val inf = Float.POSITIVE_INFINITY
        val ninf = Float.NEGATIVE_INFINITY
        val nan = Float.NaN
        val one = 1.0f
        // Inf * x => Inf
        assertEquals(bits(inf), (CFloat32.fromFloat(inf) * CFloat32.fromFloat(one)).toFloat().toRawBits())
        assertEquals(bits(ninf), (CFloat32.fromFloat(ninf) * CFloat32.fromFloat(one)).toFloat().toRawBits())
        // 0 * Inf => NaN
        val z = 0.0f
        assertTrue((CFloat32.fromFloat(z) * CFloat32.fromFloat(inf)).toFloat().isNaN())
        assertTrue((CFloat32.fromFloat(inf) * CFloat32.fromFloat(z)).toFloat().isNaN())
        // NaN propagates
        assertTrue((CFloat32.fromFloat(nan) * CFloat32.fromFloat(one)).toFloat().isNaN())
        assertTrue((CFloat32.fromFloat(one) * CFloat32.fromFloat(nan)).toFloat().isNaN())
    }

    @Test
    fun subnormals() {
        // Smallest subnormal * 2 should still be subnormal or zero depending on rounding
        val a = Float.fromBits(0x00000001)
        val b = Float.fromBits(0x00000002)
        val got = (CFloat32.fromFloat(a) * CFloat32.fromFloat(b)).toFloat()
        assertEquals((a*b).toRawBits(), got.toRawBits())
    }

    @Test
    fun smallFuzz() {
        val rnd = Random(1)
        repeat(200) {
            var a = Float.fromBits(rnd.nextInt())
            var b = Float.fromBits(rnd.nextInt())
            // constrain to finite values to avoid huge Inf cascades in fuzz
            if (!a.isFinite()) a = 0.123f
            if (!b.isFinite()) b = -0.987f
            val got = (CFloat32.fromFloat(a) * CFloat32.fromFloat(b)).toFloat()
            val ref = a * b
            // Allow raw bit compare; if both NaN, consider ok
            val ok = (got.toRawBits() == ref.toRawBits()) || (got.isNaN() && ref.isNaN())
            assertTrue(ok, "a=${a.toRawBits()} b=${b.toRawBits()} got=${got.toRawBits()} ref=${ref.toRawBits()}")
        }
    }
}

