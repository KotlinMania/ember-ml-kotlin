package ai.solace.klang.bitwise

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Float32Float64ConvertTest {
    private fun eqBitsOrBothNaN(a: Float, b: Float): Boolean =
        a.toRawBits() == b.toRawBits() || (a.isNaN() && b.isNaN())

    @Test
    fun floatToDouble_basic() {
        val cases = floatArrayOf(0f, -0f, 1f, -1f, 123.5f, -9876.25f, Float.MIN_VALUE, Float.MAX_VALUE, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
        for (f in cases) {
            val got = Float32Math.floatToDouble(f)
            val ref = f.toDouble()
            if (ref.isNaN()) {
                assertTrue(got.isNaN())
            } else {
                assertEquals(ref.toRawBits(), got.toRawBits(), "f=$f")
            }
        }
    }

    @Test
    fun doubleToFloat_basic() {
        val cases = doubleArrayOf(0.0, -0.0, 1.0, -1.0, 123.5, -9876.25, Double.MIN_VALUE, Double.MAX_VALUE, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)
        for (d in cases) {
            val got = Float32Math.doubleToFloat(d)
            val ref = d.toFloat()
            assertTrue(eqBitsOrBothNaN(ref, got), "d=$d got=${got.toRawBits()} ref=${ref.toRawBits()}")
        }
    }

    @Test
    fun randomF32F64RoundTrip() {
        val rnd = Random(3)
        repeat(200) {
            val f = Float.fromBits(rnd.nextInt())
            val d = Float32Math.floatToDouble(f)
            val f2 = Float32Math.doubleToFloat(d)
            // round-trip may change if f was NaN (payload canonicalization) or if d overflowed; compare to host
            val ref = d.toFloat()
            assertTrue(eqBitsOrBothNaN(ref, f2), "f=${f.toRawBits()} d=${d.toRawBits()} f2=${f2.toRawBits()} ref=${ref.toRawBits()}")
        }
    }
}

