package ai.solace.klang.bitwise

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class Float64MathTest {
    
    @Test
    fun testSpecialValues() {
        // Zero
        assertTrue(Float64Math.isZero(Float64Math.ZERO_BITS))
        assertEquals(0, Float64Math.getSign(Float64Math.ZERO_BITS))
        
        // One
        assertFalse(Float64Math.isZero(Float64Math.ONE_BITS))
        assertEquals(0, Float64Math.getSign(Float64Math.ONE_BITS))
        
        // NaN
        assertTrue(Float64Math.isNaN(Float64Math.NAN_BITS))
        assertFalse(Float64Math.isInf(Float64Math.NAN_BITS))
        
        // Infinity
        assertTrue(Float64Math.isInf(Float64Math.INF_BITS))
        assertFalse(Float64Math.isNaN(Float64Math.INF_BITS))
        assertEquals(0, Float64Math.getSign(Float64Math.INF_BITS))
        
        // Negative infinity
        assertTrue(Float64Math.isInf(Float64Math.NEG_INF_BITS))
        assertEquals(1, Float64Math.getSign(Float64Math.NEG_INF_BITS))
    }
    
    @Test
    fun testArithmetic() {
        val one = Float64Math.ONE_BITS
        val two = 2.0.toRawBits()
        
        // Addition
        val sum = Float64Math.addBits(one, one)
        assertEquals(two, sum)
        
        // Subtraction
        val diff = Float64Math.subBits(two, one)
        assertEquals(one, diff)
        
        // Multiplication
        val prod = Float64Math.mulBits(two, 3.0.toRawBits())
        assertEquals(6.0.toRawBits(), prod)
        
        // Division
        val quot = Float64Math.divBits(6.0.toRawBits(), two)
        assertEquals(3.0.toRawBits(), quot)
    }
    
    @Test
    fun testFloat32Conversions() {
        val f32Values = floatArrayOf(1.0f, 2.5f, -3.14159f)
        
        for (f32 in f32Values) {
            val f64bits = Float64Math.fromFloat32Bits(f32.toRawBits())
            val f32back = Float64Math.toFloat32Bits(f64bits)
            
            assertEquals(f32.toRawBits(), f32back,
                "Round trip failed for $f32")
        }
    }
    
    @Test
    fun testSignOperations() {
        val one = Float64Math.ONE_BITS
        val negOne = Float64Math.negateBits(one)
        
        assertEquals(0, Float64Math.getSign(one))
        assertEquals(1, Float64Math.getSign(negOne))
        
        val negOneDouble = Double.fromBits(negOne)
        assertEquals(-1.0, negOneDouble)
    }
    
    @Test
    fun testAbsoluteValue() {
        val negOne = Float64Math.negateBits(Float64Math.ONE_BITS)
        val abs = Float64Math.absBits(negOne)
        
        assertEquals(Float64Math.ONE_BITS, abs)
    }
    
    @Test
    fun testComparison() {
        val one = Float64Math.ONE_BITS
        val two = 2.0.toRawBits()
        val negOne = Float64Math.negateBits(one)
        
        assertTrue(Float64Math.compareBits(one, two) < 0)
        assertTrue(Float64Math.compareBits(two, one) > 0)
        assertEquals(0, Float64Math.compareBits(one, one))
        assertTrue(Float64Math.compareBits(negOne, one) < 0)
    }
    
    @Test
    fun testZeroHandling() {
        val posZero = Float64Math.ZERO_BITS
        val negZero = Float64Math.negateBits(posZero)
        
        assertTrue(Float64Math.isZero(posZero))
        assertTrue(Float64Math.isZero(negZero))
        
        // +0 and -0 should compare equal
        assertEquals(0, Float64Math.compareBits(posZero, negZero))
    }
}
