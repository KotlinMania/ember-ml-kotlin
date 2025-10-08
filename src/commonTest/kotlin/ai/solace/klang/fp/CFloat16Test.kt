package ai.solace.klang.fp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CFloat16Test {
    
    @Test
    fun testBasicCreation() {
        val f = CFloat16.fromFloat(1.0f)
        assertEquals(1.0f, f.toFloat(), 0.001f)
    }
    
    @Test
    fun testZero() {
        val zero = CFloat16.ZERO
        assertEquals(0.0f, zero.toFloat(), 0.001f)
    }
    
    @Test
    fun testOne() {
        val one = CFloat16.ONE
        assertEquals(1.0f, one.toFloat(), 0.001f)
    }
    
    @Test
    fun testAddition() {
        val a = CFloat16.fromFloat(2.5f)
        val b = CFloat16.fromFloat(3.5f)
        val sum = a + b
        
        assertEquals(6.0f, sum.toFloat(), 0.01f)
    }
    
    @Test
    fun testSubtraction() {
        val a = CFloat16.fromFloat(5.0f)
        val b = CFloat16.fromFloat(3.0f)
        val diff = a - b
        
        assertEquals(2.0f, diff.toFloat(), 0.01f)
    }
    
    @Test
    fun testMultiplication() {
        val a = CFloat16.fromFloat(2.0f)
        val b = CFloat16.fromFloat(3.0f)
        val product = a * b
        
        assertEquals(6.0f, product.toFloat(), 0.01f)
    }
    
    @Test
    fun testDivision() {
        val a = CFloat16.fromFloat(6.0f)
        val b = CFloat16.fromFloat(2.0f)
        val quotient = a / b
        
        assertEquals(3.0f, quotient.toFloat(), 0.01f)
    }
    
    @Test
    fun testNegation() {
        val a = CFloat16.fromFloat(5.0f)
        val negated = -a
        
        assertEquals(-5.0f, negated.toFloat(), 0.01f)
    }
    
    @Test
    fun testSpecialValues() {
        // NaN
        assertTrue(CFloat16.NaN.toFloat().isNaN())
        
        // Infinity
        assertEquals(Float.POSITIVE_INFINITY, CFloat16.POSITIVE_INFINITY.toFloat())
        assertEquals(Float.NEGATIVE_INFINITY, CFloat16.NEGATIVE_INFINITY.toFloat())
    }
    
    @Test
    fun testRoundTrip() {
        val original = 3.14f
        val f16 = CFloat16.fromFloat(original)
        val back = f16.toFloat()
        
        // Float16 has limited precision, so allow some error
        assertEquals(original, back, 0.01f)
    }
}
