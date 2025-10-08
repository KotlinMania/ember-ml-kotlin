package ai.solace.klang.bitwise

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Float32DivTest {
    @Test
    fun basicDivisionMatchesFloat() {
        val cases = arrayOf(
            4.0f to 2.0f,
            -9.0f to 3.0f,
            1.0f to 3.0f,
            1.0f to 256.0f,
            16777216f to 3.0f, // big / small
        )
        for ((a, b) in cases) {
            val expect = a / b
            val got = CFloat32.fromFloat(a) / CFloat32.fromFloat(b)
            val bothNaN = expect.isNaN() && got.toFloat().isNaN()
            assertTrue(bothNaN || expect.toRawBits() == got.toFloat().toRawBits(), "a=$a b=$b")
        }
    }

    @Test
    fun divByZeroAndInfinities() {
        val inf = Float.POSITIVE_INFINITY
        val ninf = Float.NEGATIVE_INFINITY
        val zero = 0.0f
        val one = 1.0f
        // 1/0 = +inf
        assertTrue(((CFloat32.fromFloat(one) / CFloat32.fromFloat(zero)).toFloat()).isInfinite())
        // 0/0 = NaN
        assertTrue(((CFloat32.fromFloat(zero) / CFloat32.fromFloat(zero)).toFloat()).isNaN())
        // inf/inf = NaN
        assertTrue(((CFloat32.fromFloat(inf) / CFloat32.fromFloat(inf)).toFloat()).isNaN())
        // inf/1 = inf
        assertTrue(((CFloat32.fromFloat(inf) / CFloat32.fromFloat(one)).toFloat()).isInfinite())
        // 1/inf = 0
        assertEquals(0.0f.toRawBits(), (CFloat32.fromFloat(one)/CFloat32.fromFloat(inf)).toFloat().toRawBits())
        // signs
        assertEquals(Float.NEGATIVE_INFINITY.toRawBits(), (CFloat32.fromFloat(one)/CFloat32.fromFloat(-0.0f)).toFloat().toRawBits())
        assertEquals(Float.POSITIVE_INFINITY.toRawBits(), (CFloat32.fromFloat(-one)/CFloat32.fromFloat(-0.0f)).toFloat().toRawBits())
    }
}

