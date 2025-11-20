package ai.solace.klang.fp

import ai.solace.klang.mem.GlobalHeap
import ai.solace.klang.mem.KMalloc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class CDoubleTest {
    private fun setup() {
        GlobalHeap.init(1 shl 20)  // 1MB
        KMalloc.init(1 shl 18)      // 256KB
    }
    
    @Test
    fun basicArithmetic() {
        setup()
        val a = CDouble.fromDouble(10.0)
        val b = CDouble.fromDouble(20.0)
        
        val sum = a + b
        assertEquals(30.0, sum.toDouble(), 1e-10, "Addition failed")
        
        val diff = b - a
        assertEquals(10.0, diff.toDouble(), 1e-10, "Subtraction failed")
        
        val prod = a * b
        assertEquals(200.0, prod.toDouble(), 1e-10, "Multiplication failed")
        
        val quot = b / a
        assertEquals(2.0, quot.toDouble(), 1e-10, "Division failed")
    }
    
    @Test
    fun unaryMinus() {
        setup()
        val a = CDouble.fromDouble(42.5)
        val negA = -a
        assertEquals(-42.5, negA.toDouble(), 1e-10)
        
        val negZero = -CDouble.ZERO
        assertEquals(-0.0, negZero.toDouble())
    }
    
    @Test
    fun comparison() {
        setup()
        val small = CDouble.fromDouble(10.0)
        val large = CDouble.fromDouble(20.0)
        val equal = CDouble.fromDouble(10.0)
        
        assertTrue(small < large, "Less than comparison failed")
        assertTrue(large > small, "Greater than comparison failed")
        assertTrue(small <= equal, "Less than or equal failed")
        assertTrue(small >= equal, "Greater than or equal failed")
        assertEquals(0, small.compareTo(equal), "Equal comparison failed")
        assertTrue(small.compareTo(large) < 0, "compareTo less failed")
        assertTrue(large.compareTo(small) > 0, "compareTo greater failed")
    }
    
    @Test
    fun specialValues() {
        setup()
        
        // NaN
        val nan = CDouble.NaN
        assertTrue(nan.toDouble().isNaN(), "NaN check failed")
        
        // Infinity
        val posInf = CDouble.POSITIVE_INFINITY
        assertTrue(posInf.toDouble().isInfinite() && posInf.toDouble() > 0, "Positive infinity failed")
        
        val negInf = CDouble.NEGATIVE_INFINITY
        assertTrue(negInf.toDouble().isInfinite() && negInf.toDouble() < 0, "Negative infinity failed")
        
        // Zero
        val zero = CDouble.ZERO
        assertEquals(0.0, zero.toDouble(), "Zero check failed")
        
        // One
        val one = CDouble.ONE
        assertEquals(1.0, one.toDouble(), "One check failed")
    }
    
    @Test
    fun bitRepresentation() {
        setup()
        
        // Test that bits round-trip correctly
        val original = CDouble.fromDouble(123.456)
        val bits = original.toBits()
        val restored = CDouble.fromBits(bits)
        
        assertEquals(original.toDouble(), restored.toDouble(), 1e-10, "Bit round-trip failed")
        assertEquals(original.toBits(), restored.toBits(), "Bit comparison failed")
    }
    
    @Test
    fun conversionFromFloat() {
        setup()
        
        val f = 12.5f
        val cd = CDouble.fromFloat(f)
        assertEquals(f.toDouble(), cd.toDouble(), 1e-6, "Float conversion failed")
    }
    
    @Test
    fun conversionToFloat() {
        setup()
        
        val cd = CDouble.fromDouble(12.5)
        val f = cd.toFloat()
        assertEquals(12.5f, f, 1e-6f, "To float conversion failed")
    }
    
    @Test
    fun conversionFromInt() {
        setup()
        
        val i = 42
        val cd = CDouble.fromInt(i)
        assertEquals(42.0, cd.toDouble(), 1e-10, "Int conversion failed")
    }
    
    @Test
    fun conversionFromLong() {
        setup()
        
        val l = 1234567890L
        val cd = CDouble.fromLong(l)
        assertEquals(1234567890.0, cd.toDouble(), 1.0, "Long conversion failed")
    }
    
    @Test
    fun equality() {
        setup()
        
        val a = CDouble.fromDouble(42.0)
        val b = CDouble.fromDouble(42.0)
        val c = CDouble.fromDouble(43.0)
        
        assertEquals(a, b, "Equality failed")
        assertFalse(a == c, "Inequality check failed")
        assertEquals(a.hashCode(), b.hashCode(), "Hash code consistency failed")
    }
    
    @Test
    fun arithmeticWithZero() {
        setup()
        
        val a = CDouble.fromDouble(10.0)
        val zero = CDouble.ZERO
        
        val sum = a + zero
        assertEquals(10.0, sum.toDouble(), 1e-10)
        
        val diff = a - zero
        assertEquals(10.0, diff.toDouble(), 1e-10)
        
        val prod = a * zero
        assertEquals(0.0, prod.toDouble(), 1e-10)
    }
    
    @Test
    fun arithmeticWithOne() {
        setup()
        
        val a = CDouble.fromDouble(42.0)
        val one = CDouble.ONE
        
        val prod = a * one
        assertEquals(42.0, prod.toDouble(), 1e-10)
        
        val quot = a / one
        assertEquals(42.0, quot.toDouble(), 1e-10)
    }
    
    @Test
    fun smallNumbers() {
        setup()
        
        val tiny = CDouble.fromDouble(1e-100)
        val sum = tiny + tiny
        assertEquals(2e-100, sum.toDouble(), 1e-110, "Small number arithmetic failed")
    }
    
    @Test
    fun largeNumbers() {
        setup()
        
        val large = CDouble.fromDouble(1e100)
        val sum = large + large
        assertEquals(2e100, sum.toDouble(), 1e90, "Large number arithmetic failed")
    }
    
    @Test
    fun negativeNumbers() {
        setup()
        
        val neg = CDouble.fromDouble(-42.0)
        val pos = CDouble.fromDouble(10.0)
        
        val sum = neg + pos
        assertEquals(-32.0, sum.toDouble(), 1e-10)
        
        val prod = neg * pos
        assertEquals(-420.0, prod.toDouble(), 1e-10)
    }
    
    @Test
    fun stringRepresentation() {
        setup()
        
        val a = CDouble.fromDouble(42.5)
        val str = a.toString()
        assertTrue(str.contains("42"), "String representation should contain value")
    }
}
