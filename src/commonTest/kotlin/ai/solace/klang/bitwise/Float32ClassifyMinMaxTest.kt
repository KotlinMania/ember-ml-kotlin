package ai.solace.klang.bitwise

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Float32ClassifyMinMaxTest {
    @Test
    fun classifyBasics() {
        val nan = Float.fromBits(0x7FC00001.toInt())
        val snan = Float.fromBits(0x7FA00001.toInt())
        val pinf = Float.POSITIVE_INFINITY
        val ninf = Float.NEGATIVE_INFINITY
        val pz = 0.0f
        val nz = -0.0f
        val sub = Float.fromBits(1) // smallest subnormal
        val norm = 1.0f

        assertTrue(Float32Math.isNaNBits(nan.toRawBits()))
        assertTrue(Float32Math.isSignalingNaNBits(snan.toRawBits()))
        assertTrue(Float32Math.isInfBits(pinf.toRawBits()))
        assertTrue(Float32Math.isInfBits(ninf.toRawBits()))
        assertTrue(Float32Math.isZeroBits(pz.toRawBits()))
        assertTrue(Float32Math.isZeroBits(nz.toRawBits()))
        assertTrue(Float32Math.isSubnormalBits(sub.toRawBits()))
        assertTrue(Float32Math.isNormalBits(norm.toRawBits()))
    }

    @Test
    fun fminFmaxNaNAndZeros() {
        val nan = Float.fromBits(0x7FC00001.toInt())
        val pz = 0.0f
        val nz = -0.0f
        val one = 1.0f

        // One NaN -> return the other arg
        assertEquals(one.toRawBits(), Float32Math.fmin(nan, one).toRawBits())
        assertEquals(one.toRawBits(), Float32Math.fmax(one, nan).toRawBits())

        // Both zeros: fmin -> -0; fmax -> +0
        assertEquals((-0.0f).toRawBits(), Float32Math.fmin(pz, nz).toRawBits())
        assertEquals(0.0f.toRawBits(), Float32Math.fmax(pz, nz).toRawBits())
    }

    @Test
    fun copysignBehavior() {
        val a = 3.25f
        val b = -1.0f
        val res = Float32Math.copysign(a, b)
        assertEquals((-3.25f).toRawBits(), res.toRawBits())
    }
}
