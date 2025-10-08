package ai.solace.klang.bitwise

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CFloat32Test {
    @Test
    fun basicArithmeticMatchesFloat() {
        val a = CFloat32.fromFloat(1.5f)
        val b = CFloat32.fromFloat(2.75f)

        assertEquals(4.25f, (a + b).toFloat())
        assertEquals(-1.25f, (a - b).toFloat())
        assertEquals(4.125f, (a * b).toFloat())
        assertEquals(0.54545456f, (a / b).toFloat())
    }

    @Test
    fun preservesSinglePrecisionRounding() {
        val large = CFloat32.fromFloat(16_000_000f)
        val small = CFloat32.fromFloat(3.5f)

        val expected = 16_000_000f + 3.5f
        val rounded = (large + small).toFloat()
        assertEquals(expected, rounded)
    }

    @Test
    fun fmaBehavesLikeCFloat() {
        val acc = CFloat32.fromFloat(1.0f)
        val multiplier = CFloat32.fromFloat(0.2f)
        val addend = CFloat32.fromFloat(0.1f)

        val result = acc.fma(multiplier, addend)
        assertTrue(result.toFloat() > 1.0f && result.toFloat() < 1.1f)
    }

    @Test
    fun traceCapturesOperations() {
        val (value, trace) = CFloatTrace.withTracing {
            val a = CFloat32.fromFloat(2f)
            val b = a + 3f
            b * 4f
        }
        assertEquals(2, trace.size)
        assertEquals("plusF", trace[0].op)
        assertEquals("timesF", trace[1].op)
        assertEquals(20f, value.toFloat())
    }
}
