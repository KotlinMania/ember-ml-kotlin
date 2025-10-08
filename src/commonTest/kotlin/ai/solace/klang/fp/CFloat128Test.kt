package ai.solace.klang.fp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.abs

class CFloat128Test {
    
    @Test
    fun testBasicCreation() {
        val zero = CFloat128.ZERO
        assertEquals(0.0, zero.toDouble())
        
        val one = CFloat128.ONE
        assertEquals(1.0, one.toDouble())
    }
    
    @Test
    fun testAddition() {
        val a = CFloat128.fromDouble(1.5)
        val b = CFloat128.fromDouble(2.5)
        val sum = a + b
        
        assertEquals(4.0, sum.toDouble(), 1e-15)
    }
    
    @Test
    fun testSubtraction() {
        val a = CFloat128.fromDouble(5.5)
        val b = CFloat128.fromDouble(2.5)
        val diff = a - b
        
        assertEquals(3.0, diff.toDouble(), 1e-15)
    }
    
    @Test
    fun testMultiplication() {
        val a = CFloat128.fromDouble(2.5)
        val b = CFloat128.fromDouble(4.0)
        val prod = a * b
        
        assertEquals(10.0, prod.toDouble(), 1e-15)
    }
    
    @Test
    fun testMultiplicationByScalar() {
        val a = CFloat128.fromDouble(3.0)
        val prod = a * 2.0
        
        assertEquals(6.0, prod.toDouble(), 1e-15)
    }
    
    @Test
    fun testNegation() {
        val a = CFloat128.fromDouble(5.0)
        val neg = -a
        
        assertEquals(-5.0, neg.toDouble())
    }
    
    @Test
    fun testExtendedPrecision() {
        // Test that we get better precision than Double alone
        // This is a case where double-double shines
        
        val a = CFloat128.fromDouble(1.0)
        val b = CFloat128.fromDouble(1e-20)
        
        val sum = a + b
        
        // With regular Double, 1.0 + 1e-20 might lose precision
        // With CFloat128, we should maintain both parts
        assertTrue(sum.hi == 1.0)
        assertTrue(abs(sum.lo - 1e-20) < 1e-30)
    }
    
    @Test
    fun testFusedMultiplySubtract() {
        val a = CFloat128.fromDouble(2.0)
        val b = CFloat128.fromDouble(3.0)
        val c = CFloat128.fromDouble(1.0)
        val d = CFloat128.fromDouble(5.0)
        
        // (2*3) - (1*5) = 6 - 5 = 1
        val result = CFloat128.fms(a, b, c, d)
        
        assertEquals(1.0, result.toDouble(), 1e-15)
    }
    
    @Test
    fun testConversionFromFloat() {
        val f = 3.14159f
        val dd = CFloat128.fromFloat(f)
        
        assertEquals(f.toDouble(), dd.toDouble(), 1e-6)
    }
    
    @Test
    fun testConversionFromCFloat16() {
        val f16 = CFloat16.fromFloat(2.5f)
        val f128 = CFloat128.fromCFloat16(f16)
        
        assertEquals(2.5, f128.toDouble(), 0.01)
    }
    
    @Test
    fun testConversionFromCFloat64() {
        val f64 = CFloat64.fromDouble(1.23456789)
        val f128 = CFloat128.fromCFloat64(f64)
        
        assertEquals(1.23456789, f128.toDouble(), 1e-15)
    }
}
