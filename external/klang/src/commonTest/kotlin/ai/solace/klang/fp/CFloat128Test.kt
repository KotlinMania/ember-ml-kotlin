package ai.solace.klang.fp

import ai.solace.klang.mem.GlobalHeap
import ai.solace.klang.mem.KMalloc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.abs

class CFloat128Test {
    private fun setup() {
        GlobalHeap.init(1 shl 20)  // 1MB
        KMalloc.init(1 shl 18)      // 256KB
    }
    
    @Test
    fun basicArithmetic() {
        setup()
        
        val a = CFloat128.fromDouble(10.0)
        val b = CFloat128.fromDouble(20.0)
        
        val sum = a + b
        assertEquals(30.0, sum.toDouble(), 1e-10, "Addition failed")
        
        val diff = b - a
        assertEquals(10.0, diff.toDouble(), 1e-10, "Subtraction failed")
        
        val prod = a * b
        assertEquals(200.0, prod.toDouble(), 1e-10, "Multiplication failed")
        
        // NEW: Division test
        val quot = b / a
        assertEquals(2.0, quot.toDouble(), 1e-10, "Division failed")
    }
    
    @Test
    fun divisionBasic() {
        setup()
        
        val a = CFloat128.fromDouble(20.0)
        val b = CFloat128.fromDouble(4.0)
        
        val result = a / b
        assertEquals(5.0, result.toDouble(), 1e-10, "Basic division failed")
    }
    
    @Test
    fun divisionByScalar() {
        setup()
        
        val a = CFloat128.fromDouble(100.0)
        val result = a / 4.0
        
        assertEquals(25.0, result.toDouble(), 1e-10, "Division by scalar failed")
    }
    
    @Test
    fun divisionByOne() {
        setup()
        
        val a = CFloat128.fromDouble(42.5)
        val result = a / CFloat128.ONE
        
        assertEquals(42.5, result.toDouble(), 1e-10, "Division by one failed")
    }
    
    @Test
    fun divisionResultingInOne() {
        setup()
        
        val a = CFloat128.fromDouble(7.0)
        val result = a / a
        
        assertEquals(1.0, result.toDouble(), 1e-10, "Division resulting in one failed")
    }
    
    @Test
    fun divisionHighPrecision() {
        setup()
        
        // Test 1/3 with high precision
        val one = CFloat128.ONE
        val three = CFloat128.fromDouble(3.0)
        
        val third = one / three
        
        // Multiply back should give close to 1.0
        val reconstructed = third * three
        
        val error = kotlin.math.abs(reconstructed.toDouble() - 1.0)
        assertTrue(error < 1e-15, "High precision division failed, error: $error")
    }
    
    @Test
    fun divisionChained() {
        setup()
        
        // (100 / 4) / 5 = 5
        val a = CFloat128.fromDouble(100.0)
        val result = (a / 4.0) / 5.0
        
        assertEquals(5.0, result.toDouble(), 1e-10, "Chained division failed")
    }
    
    @Test
    fun divisionWithNegative() {
        setup()
        
        val a = CFloat128.fromDouble(-20.0)
        val b = CFloat128.fromDouble(4.0)
        
        val result = a / b
        assertEquals(-5.0, result.toDouble(), 1e-10, "Negative division failed")
    }
    
    @Test
    fun divisionSmallNumbers() {
        setup()
        
        val a = CFloat128.fromDouble(1e-10)
        val b = CFloat128.fromDouble(1e-5)
        
        val result = a / b
        assertEquals(1e-5, result.toDouble(), 1e-15, "Small number division failed")
    }
    
    @Test
    fun divisionLargeNumbers() {
        setup()
        
        val a = CFloat128.fromDouble(1e100)
        val b = CFloat128.fromDouble(1e50)
        
        val result = a / b
        assertEquals(1e50, result.toDouble(), 1e40, "Large number division failed")
    }
    
    @Test
    fun divisionAccuracy() {
        setup()
        
        // Test that division maintains double-double precision
        // Computing 1/7 and multiplying back should be very close to 1
        val one = CFloat128.ONE
        val seven = CFloat128.fromDouble(7.0)
        
        val seventh = one / seven
        val reconstructed = seventh * seven
        
        val error = kotlin.math.abs(reconstructed.toDouble() - 1.0)
        assertTrue(error < 1e-14, "Division accuracy test failed, error: $error")
    }
    
    @Test
    fun arithmeticWithScalars() {
        setup()
        
        val a = CFloat128.fromDouble(10.0)
        
        val sum = a + 5.0
        assertEquals(15.0, sum.toDouble(), 1e-10, "Scalar addition failed")
        
        val prod = a * 3.0
        assertEquals(30.0, prod.toDouble(), 1e-10, "Scalar multiplication failed")
    }
    
    @Test
    fun unaryMinus() {
        setup()
        
        val a = CFloat128.fromDouble(42.5)
        val negA = -a
        assertEquals(-42.5, negA.toDouble(), 1e-10, "Unary minus failed")
        
        val doubleNeg = -(-a)
        assertEquals(42.5, doubleNeg.toDouble(), 1e-10, "Double negation failed")
    }
    
    @Test
    fun highPrecisionAddition() {
        setup()
        
        // Test that demonstrates extended precision
        // 1 + 1e-16 - 1 should retain the 1e-16 in double-double
        val one = CFloat128.ONE
        val tiny = CFloat128.fromDouble(1e-16)
        
        val sum = one + tiny
        val result = sum - one
        
        // In regular double, this would lose precision
        // In double-double, we should retain it
        val error = abs(result.toDouble() - 1e-16)
        assertTrue(error < 1e-25, "High precision addition failed, error: $error")
    }
    
    @Test
    fun catastrophicCancellation() {
        setup()
        
        // Test (1 + 1e-15) - 1
        // Regular double would have issues
        val one = CFloat128.ONE
        val epsilon = CFloat128.fromDouble(1e-15)
        
        val sum = one + epsilon
        val diff = sum - one
        
        // Should be very close to 1e-15
        assertEquals(1e-15, diff.toDouble(), 1e-20, "Catastrophic cancellation not handled")
    }
    
    @Test
    fun accurateSum() {
        setup()
        
        // Sum of many small numbers
        // Regular double accumulation would lose precision
        var sum = CFloat128.ZERO
        val small = CFloat128.fromDouble(1e-8)
        
        // Add 1e-8 a thousand times = 1e-5
        for (i in 0 until 1000) {
            sum = sum + small
        }
        
        assertEquals(1e-5, sum.toDouble(), 1e-13, "Accurate summation failed")
    }
    
    @Test
    fun fusedMultiplySubtract() {
        setup()
        
        val a = CFloat128.fromDouble(3.0)
        val b = CFloat128.fromDouble(4.0)
        val c = CFloat128.fromDouble(2.0)
        val d = CFloat128.fromDouble(5.0)
        
        // (a*b) - (c*d) = 12 - 10 = 2
        val result = CFloat128.fms(a, b, c, d)
        assertEquals(2.0, result.toDouble(), 1e-10, "FMS failed")
    }
    
    @Test
    fun specialValues() {
        setup()
        
        val zero = CFloat128.ZERO
        assertEquals(0.0, zero.toDouble(), "Zero failed")
        
        val one = CFloat128.ONE
        assertEquals(1.0, one.toDouble(), "One failed")
    }
    
    @Test
    fun conversionFromDouble() {
        setup()
        
        val d = 123.456
        val cf = CFloat128.fromDouble(d)
        assertEquals(d, cf.toDouble(), 1e-10, "From double conversion failed")
    }
    
    @Test
    fun conversionFromFloat() {
        setup()
        
        val f = 12.5f
        val cf = CFloat128.fromFloat(f)
        assertEquals(f.toDouble(), cf.toDouble(), 1e-6, "From float conversion failed")
    }
    
    @Test
    fun conversionFromCDouble() {
        setup()
        
        val cd = CDouble.fromDouble(42.0)
        val cf = CFloat128.fromCDouble(cd)
        assertEquals(42.0, cf.toDouble(), 1e-10, "From CDouble conversion failed")
    }
    
    @Test
    fun conversionFromCFloat16() {
        setup()
        
        val cf16 = CFloat16.fromFloat(12.5f)
        val cf128 = CFloat128.fromCFloat16(cf16)
        assertEquals(12.5, cf128.toDouble(), 1e-2, "From CFloat16 conversion failed")
    }
    
    @Test
    fun extensionFunctions() {
        setup()
        
        val f = 10.5f
        val d = 20.5
        
        val cf1 = f.toCFloat128()
        assertEquals(10.5, cf1.toDouble(), 1e-6, "Float extension failed")
        
        val cf2 = d.toCFloat128()
        assertEquals(20.5, cf2.toDouble(), 1e-10, "Double extension failed")
    }
    
    @Test
    fun multiplicationAccuracy() {
        setup()
        
        // Test multiplication of numbers that might lose precision
        val a = CFloat128.fromDouble(1.0 + 1e-10)
        val b = CFloat128.fromDouble(1.0 + 1e-10)
        
        val prod = a * b
        // (1 + e)^2 ≈ 1 + 2e + e^2
        // For e = 1e-10: 1 + 2e-10 + 1e-20
        val expected = 1.0 + 2e-10 + 1e-20
        
        assertEquals(expected, prod.toDouble(), 1e-18, "Multiplication accuracy failed")
    }
    
    @Test
    fun chainedOperations() {
        setup()
        
        val a = CFloat128.fromDouble(1.0)
        val b = CFloat128.fromDouble(2.0)
        val c = CFloat128.fromDouble(3.0)
        
        // (a + b) * c - a
        val result = (a + b) * c - a
        // (1 + 2) * 3 - 1 = 9 - 1 = 8
        assertEquals(8.0, result.toDouble(), 1e-10, "Chained operations failed")
    }
    
    @Test
    fun verySmallNumbers() {
        setup()
        
        val tiny = CFloat128.fromDouble(1e-200)
        val sum = tiny + tiny
        assertEquals(2e-200, sum.toDouble(), 1e-210, "Very small numbers failed")
    }
    
    @Test
    fun hiLoComponents() {
        setup()
        
        val cf = CFloat128(1.0, 1e-16)
        
        assertEquals(1.0, cf.hi, "Hi component failed")
        assertEquals(1e-16, cf.lo, 1e-26, "Lo component failed")
        
        // Total should be sum of hi and lo
        assertEquals(1.0 + 1e-16, cf.toDouble(), 1e-20, "Hi+Lo sum failed")
    }
    
    @Test
    fun precisionDemonstration() {
        setup()
        
        // Compute 1/3 with extended precision
        // Regular double: 0.333...
        // We can't do division yet, but we can test what we have
        
        val third = CFloat128.fromDouble(1.0 / 3.0)
        val tripled = third * 3.0
        
        // Should be very close to 1.0
        val error = abs(tripled.toDouble() - 1.0)
        assertTrue(error < 1e-14, "Precision demonstration failed, error: $error")
    }
}
