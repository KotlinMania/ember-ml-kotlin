package ai.solace.klang.bitwise

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Float32MathTest {
    @Test
    fun mulMatchesFloatForRepresentativeValues() {
        val vals = floatArrayOf(
            0f, -0f, 1f, -1f, 2f, -2f, 0.5f, -0.5f,
            Float.MIN_VALUE, -Float.MIN_VALUE,
            1.1754944E-38f, // ~min normal
            Float.MAX_VALUE / 2, 3.4028233E38f / 4,
            Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY
        )
        for (a in vals) for (b in vals) {
            val expect = (a * b)
            val got = Float32Math.mul(a, b)
            val bothNaN = expect.isNaN() && got.isNaN()
            assertTrue(bothNaN || expect.toRawBits() == got.toRawBits(),
                "mul mismatch a=$a b=$b expect=${expect.toRawBits().toUInt().toString(16)} got=${got.toRawBits().toUInt().toString(16)}")
        }
    }

    @Test
    fun mulFuzzMatchesFloat() {
        val rnd = Random(1234)
        repeat(10_000) {
            val a = rnd.nextFloat() * 1e10f - 5e9f
            val b = rnd.nextFloat() * 1e10f - 5e9f
            val expect = a * b
            val got = Float32Math.mul(a, b)
            val bothNaN = expect.isNaN() && got.isNaN()
            assertTrue(bothNaN || expect.toRawBits() == got.toRawBits())
        }
    }

    @Test
    fun addPosMatchesFloatOnPositivePairs() {
        val pairs = arrayOf(
            0f to 0f,
            0f to 1f,
            1f to 0f,
            1f to 2f,
            16_000_000f to 3.5f,
            1.1754944E-38f to 1.1754944E-38f,
            12345.125f to 0.5f
        )
        for ((a, b) in pairs) {
            val expect = a + b
            val got = Float32Math.addPos(a, b)
            assertEquals(expect.toRawBits(), got.toRawBits(), "a=$a b=$b")
        }
    }

    @Test
    fun cfloatOperatorsDelegateToBitwise() {
        val a = CFloat32.fromFloat(3.25f)
        val b = CFloat32.fromFloat(1.5f)
        // timesExact is bitwise; compare to normal operator for sanity
        val prod = a.timesExact(b).toFloat()
        assertEquals((3.25f * 1.5f).toRawBits(), prod.toRawBits())

        CFloatTrace.start()
        val r = (a + 2f) * 4f
        val t = CFloatTrace.stop()
        assertEquals(2, t.size)
        val expected = ((a.toFloat() + 2f) * 4f)
        assertEquals(expected.toRawBits(), r.toFloat().toRawBits())
    }
}
