package ai.solace.klang.bitwise

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Float32SqrtTest {
    private fun bits(x: Float) = x.toRawBits()

    private fun refSqrt(f: Float): Float = sqrt(f.toDouble()).toFloat()

    @Test
    fun zeros() {
        val pz = 0.0f
        val nz = -0.0f
        val gotP = Float.fromBits(Float32Math.sqrtBits(pz.toRawBits()))
        val gotN = Float.fromBits(Float32Math.sqrtBits(nz.toRawBits()))
        assertEquals(bits(refSqrt(pz)), bits(gotP))
        assertEquals(bits(refSqrt(nz)), bits(gotN))
    }

    @Test
    fun normals() {
        val cases = floatArrayOf(1.0f, 2.0f, 4.0f, 9.0f, 0.5f, 0.25f, 123.456f)
        for (x in cases) {
            val got = Float.fromBits(Float32Math.sqrtBits(x.toRawBits()))
            val ref = refSqrt(x)
            assertEquals(bits(ref), bits(got), "x=$x")
        }
    }

    @Test
    fun subnormals() {
        val xs = intArrayOf(0x00000001, 0x00000002, 0x00000100, 0x0000FFFF)
        for (b in xs) {
            val x = Float.fromBits(b)
            val got = Float.fromBits(Float32Math.sqrtBits(b))
            val ref = refSqrt(x)
            assertEquals(bits(ref), bits(got), "subnormal=$b")
        }
    }

    @Test
    fun infAndNaN() {
        val pinf = Float.POSITIVE_INFINITY
        val ninf = Float.NEGATIVE_INFINITY
        val nan = Float.NaN

        val gotInf = Float.fromBits(Float32Math.sqrtBits(pinf.toRawBits()))
        assertEquals(pinf.toRawBits(), gotInf.toRawBits())

        val gotNInf = Float.fromBits(Float32Math.sqrtBits(ninf.toRawBits()))
        assertTrue(gotNInf.isNaN())

        val gotNaN = Float.fromBits(Float32Math.sqrtBits(nan.toRawBits()))
        assertTrue(gotNaN.isNaN())
    }

    @Test
    fun negatives() {
        val xs = floatArrayOf(-1.0f, -2.0f, -123.4f)
        for (x in xs) {
            val got = Float.fromBits(Float32Math.sqrtBits(x.toRawBits()))
            assertTrue(got.isNaN())
        }
    }
}

