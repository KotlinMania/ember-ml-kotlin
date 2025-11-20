package ai.solace.klang.bitwise

import ai.solace.klang.mem.GlobalHeap
import ai.solace.klang.mem.KMalloc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class Float64MathTest {
    private fun setup() {
        GlobalHeap.init(1 shl 20)  // 1MB
        KMalloc.init(1 shl 18)      // 256KB
    }
    
    @Test
    fun additionBasic() {
        setup()
        
        val aBits = 10.0.toRawBits()
        val bBits = 20.0.toRawBits()
        val result = Double.fromBits(Float64Math.addBits(aBits, bBits))
        
        assertEquals(30.0, result, 1e-10, "Basic addition failed")
    }
    
    @Test
    fun subtractionBasic() {
        setup()
        
        val aBits = 30.0.toRawBits()
        val bBits = 10.0.toRawBits()
        val result = Double.fromBits(Float64Math.subBits(aBits, bBits))
        
        assertEquals(20.0, result, 1e-10, "Basic subtraction failed")
    }
    
    @Test
    fun multiplicationBasic() {
        setup()
        
        val aBits = 2.0.toRawBits()
        val bBits = 3.0.toRawBits()
        val result = Double.fromBits(Float64Math.mulBits(aBits, bBits))
        
        assertEquals(6.0, result, 1e-10, "Basic multiplication failed")
    }
    
    @Test
    fun divisionBasic() {
        setup()
        
        val aBits = 20.0.toRawBits()
        val bBits = 4.0.toRawBits()
        val result = Double.fromBits(Float64Math.divBits(aBits, bBits))
        
        assertEquals(5.0, result, 1e-10, "Basic division failed")
    }
    
    @Test
    fun negationOperation() {
        setup()
        
        val bits = 42.5.toRawBits()
        val negated = Double.fromBits(Float64Math.negateBits(bits))
        
        assertEquals(-42.5, negated, 1e-10, "Negation failed")
    }
    
    @Test
    fun absoluteValuePositive() {
        setup()
        
        val bits = 42.5.toRawBits()
        val abs = Double.fromBits(Float64Math.absBits(bits))
        
        assertEquals(42.5, abs, 1e-10, "Absolute value of positive failed")
    }
    
    @Test
    fun absoluteValueNegative() {
        setup()
        
        val bits = (-42.5).toRawBits()
        val abs = Double.fromBits(Float64Math.absBits(bits))
        
        assertEquals(42.5, abs, 1e-10, "Absolute value of negative failed")
    }
    
    @Test
    fun isZeroCheck() {
        setup()
        
        assertTrue(Float64Math.isZero(0.0.toRawBits()), "Zero check failed")
        assertTrue(Float64Math.isZero((-0.0).toRawBits()), "Negative zero check failed")
        assertFalse(Float64Math.isZero(1e-300.toRawBits()), "Tiny value incorrectly identified as zero")
    }
    
    @Test
    fun isNaNCheck() {
        setup()
        
        val nan = Double.NaN.toRawBits()
        assertTrue(Float64Math.isNaN(nan), "NaN check failed")
        
        val notNan = 42.0.toRawBits()
        assertFalse(Float64Math.isNaN(notNan), "Non-NaN incorrectly identified as NaN")
    }
    
    @Test
    fun isInfCheck() {
        setup()
        
        val posInf = Double.POSITIVE_INFINITY.toRawBits()
        assertTrue(Float64Math.isInf(posInf), "Positive infinity check failed")
        
        val negInf = Double.NEGATIVE_INFINITY.toRawBits()
        assertTrue(Float64Math.isInf(negInf), "Negative infinity check failed")
        
        val notInf = 42.0.toRawBits()
        assertFalse(Float64Math.isInf(notInf), "Non-infinity incorrectly identified as infinity")
    }
    
    @Test
    fun getSignPositive() {
        setup()
        
        val sign = Float64Math.getSign(42.0.toRawBits())
        assertEquals(0, sign, "Positive sign check failed")
    }
    
    @Test
    fun getSignNegative() {
        setup()
        
        val sign = Float64Math.getSign((-42.0).toRawBits())
        assertEquals(1, sign, "Negative sign check failed")
    }
    
    @Test
    fun comparisonEqual() {
        setup()
        
        val a = 42.0.toRawBits()
        val b = 42.0.toRawBits()
        
        assertEquals(0, Float64Math.compareBits(a, b), "Equal comparison failed")
    }
    
    @Test
    fun comparisonLess() {
        setup()
        
        val a = 10.0.toRawBits()
        val b = 20.0.toRawBits()
        
        assertTrue(Float64Math.compareBits(a, b) < 0, "Less than comparison failed")
    }
    
    @Test
    fun comparisonGreater() {
        setup()
        
        val a = 20.0.toRawBits()
        val b = 10.0.toRawBits()
        
        assertTrue(Float64Math.compareBits(a, b) > 0, "Greater than comparison failed")
    }
    
    @Test
    fun comparisonWithZeros() {
        setup()
        
        val posZero = 0.0.toRawBits()
        val negZero = (-0.0).toRawBits()
        
        assertEquals(0, Float64Math.compareBits(posZero, negZero), "Comparison of +0 and -0 failed")
    }
    
    @Test
    fun float32ToFloat64Conversion() {
        setup()
        
        val f32 = 42.5f
        val f64Bits = Float64Math.fromFloat32Bits(f32.toRawBits())
        val result = Double.fromBits(f64Bits)
        
        assertEquals(42.5, result, 1e-6, "Float32 to Float64 conversion failed")
    }
    
    @Test
    fun float32ToFloat64Zero() {
        setup()
        
        val f32 = 0.0f
        val f64Bits = Float64Math.fromFloat32Bits(f32.toRawBits())
        val result = Double.fromBits(f64Bits)
        
        assertEquals(0.0, result, "Float32 zero to Float64 conversion failed")
    }
    
    @Test
    fun float64ToFloat32Conversion() {
        setup()
        
        val f64 = 42.5
        val f32Bits = Float64Math.toFloat32Bits(f64.toRawBits())
        val result = Float.fromBits(f32Bits)
        
        assertEquals(42.5f, result, 1e-5f, "Float64 to Float32 conversion failed")
    }
    
    @Test
    fun float64ToFloat32Zero() {
        setup()
        
        val f64 = 0.0
        val f32Bits = Float64Math.toFloat32Bits(f64.toRawBits())
        val result = Float.fromBits(f32Bits)
        
        assertEquals(0.0f, result, "Float64 zero to Float32 conversion failed")
    }
    
    @Test
    fun specialValueConstants() {
        setup()
        
        assertEquals(0.0, Double.fromBits(Float64Math.ZERO_BITS), "ZERO_BITS constant failed")
        assertEquals(1.0, Double.fromBits(Float64Math.ONE_BITS), 1e-10, "ONE_BITS constant failed")
        assertTrue(Double.fromBits(Float64Math.NAN_BITS).isNaN(), "NAN_BITS constant failed")
        assertTrue(Double.fromBits(Float64Math.INF_BITS).isInfinite(), "INF_BITS constant failed")
        assertTrue(Double.fromBits(Float64Math.NEG_INF_BITS).isInfinite(), "NEG_INF_BITS constant failed")
    }
    
    @Test
    fun conversionRoundTrip() {
        setup()
        
        // Float32 -> Float64 -> Float32
        val original = 123.456f
        val f64Bits = Float64Math.fromFloat32Bits(original.toRawBits())
        val f32Bits = Float64Math.toFloat32Bits(f64Bits)
        val result = Float.fromBits(f32Bits)
        
        assertEquals(original, result, 1e-5f, "Conversion round-trip failed")
    }
    
    @Test
    fun negativeNumberOperations() {
        setup()
        
        val neg1 = (-10.0).toRawBits()
        val neg2 = (-5.0).toRawBits()
        
        val sum = Double.fromBits(Float64Math.addBits(neg1, neg2))
        assertEquals(-15.0, sum, 1e-10, "Negative addition failed")
        
        val prod = Double.fromBits(Float64Math.mulBits(neg1, neg2))
        assertEquals(50.0, prod, 1e-10, "Negative multiplication failed")
    }
    
    @Test
    fun smallNumberOperations() {
        setup()
        
        val tiny = 1e-100.toRawBits()
        val sum = Float64Math.addBits(tiny, tiny)
        
        assertEquals(2e-100, Double.fromBits(sum), 1e-110, "Small number addition failed")
    }
    
    @Test
    fun largeNumberOperations() {
        setup()
        
        val large = 1e100.toRawBits()
        val two = 2.0.toRawBits()
        val result = Float64Math.mulBits(large, two)
        
        assertEquals(2e100, Double.fromBits(result), 1e90, "Large number multiplication failed")
    }
    
    @Test
    fun chainedOperations() {
        setup()
        
        // (10 + 20) * 2 = 60
        val ten = 10.0.toRawBits()
        val twenty = 20.0.toRawBits()
        val two = 2.0.toRawBits()
        
        val sum = Float64Math.addBits(ten, twenty)
        val result = Float64Math.mulBits(sum, two)
        
        assertEquals(60.0, Double.fromBits(result), 1e-10, "Chained operations failed")
    }
    
    @Test
    fun signPreservation() {
        setup()
        
        val neg = (-42.0).toRawBits()
        val doubled = Float64Math.negateBits(neg)
        
        assertEquals(42.0, Double.fromBits(doubled), 1e-10, "Sign preservation (double negation) failed")
    }
}
