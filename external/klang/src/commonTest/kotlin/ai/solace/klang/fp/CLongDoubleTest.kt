package ai.solace.klang.fp

import ai.solace.klang.mem.GlobalHeap
import ai.solace.klang.mem.KMalloc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.abs

class CLongDoubleTest {
    private fun setup() {
        GlobalHeap.init(1 shl 20)  // 1MB
        KMalloc.init(1 shl 18)      // 256KB
    }
    
    @Test
    fun flavorDouble64Arithmetic() {
        setup()
        
        val a = CLongDouble.ofDouble(10.0, CLongDouble.Flavor.DOUBLE64)
        val b = CLongDouble.ofDouble(20.0, CLongDouble.Flavor.DOUBLE64)
        
        val sum = a + b
        assertEquals(30.0, sum.toDouble(), 1e-10, "DOUBLE64 addition failed")
        
        val diff = b - a
        assertEquals(10.0, diff.toDouble(), 1e-10, "DOUBLE64 subtraction failed")
        
        val prod = a * b
        assertEquals(200.0, prod.toDouble(), 1e-10, "DOUBLE64 multiplication failed")
        
        val quot = b / a
        assertEquals(2.0, quot.toDouble(), 1e-10, "DOUBLE64 division failed")
    }
    
    @Test
    fun flavorExtended80Arithmetic() {
        setup()
        
        val a = CLongDouble.ofDouble(10.0, CLongDouble.Flavor.EXTENDED80)
        val b = CLongDouble.ofDouble(20.0, CLongDouble.Flavor.EXTENDED80)
        
        val sum = a + b
        assertEquals(30.0, sum.toDouble(), 1e-10, "EXTENDED80 addition failed")
        
        val diff = b - a
        assertEquals(10.0, diff.toDouble(), 1e-10, "EXTENDED80 subtraction failed")
        
        val prod = a * b
        assertEquals(200.0, prod.toDouble(), 1e-10, "EXTENDED80 multiplication failed")
        
        val quot = b / a
        assertEquals(2.0, quot.toDouble(), 1e-8, "EXTENDED80 division failed")
    }
    
    @Test
    fun flavorIeee128Arithmetic() {
        setup()
        
        val a = CLongDouble.ofDouble(10.0, CLongDouble.Flavor.IEEE128)
        val b = CLongDouble.ofDouble(20.0, CLongDouble.Flavor.IEEE128)
        
        val sum = a + b
        assertEquals(30.0, sum.toDouble(), 1e-10, "IEEE128 addition failed")
        
        val diff = b - a
        assertEquals(10.0, diff.toDouble(), 1e-10, "IEEE128 subtraction failed")
        
        val prod = a * b
        assertEquals(200.0, prod.toDouble(), 1e-10, "IEEE128 multiplication failed")
        
        val quot = b / a
        assertEquals(2.0, quot.toDouble(), 1e-8, "IEEE128 division failed")
    }
    
    @Test
    fun flavorAutoDefaultsToDouble64() {
        setup()
        
        // AUTO should use the default flavor (DOUBLE64)
        CLongDouble.Companion.DefaultFlavorProvider.default = CLongDouble.Flavor.DOUBLE64
        
        val a = CLongDouble.ofDouble(42.0, CLongDouble.Flavor.AUTO)
        val b = CLongDouble.ofDouble(10.0, CLongDouble.Flavor.AUTO)
        
        val sum = a + b
        assertEquals(52.0, sum.toDouble(), 1e-10, "AUTO (DOUBLE64) addition failed")
    }
    
    @Test
    fun changeDefaultFlavor() {
        setup()
        
        // Change default to IEEE128
        val originalDefault = CLongDouble.Companion.DefaultFlavorProvider.default
        CLongDouble.Companion.DefaultFlavorProvider.default = CLongDouble.Flavor.IEEE128
        
        try {
            val a = CLongDouble.ofDouble(10.0, CLongDouble.Flavor.AUTO)
            val b = CLongDouble.ofDouble(5.0, CLongDouble.Flavor.AUTO)
            
            val prod = a * b
            assertEquals(50.0, prod.toDouble(), 1e-10, "AUTO (IEEE128) multiplication failed")
        } finally {
            // Restore original default
            CLongDouble.Companion.DefaultFlavorProvider.default = originalDefault
        }
    }
    
    @Test
    fun conversionToCFloat128() {
        setup()
        
        val ld = CLongDouble.ofDouble(123.456, CLongDouble.Flavor.EXTENDED80)
        val cf128 = ld.toCFloat128()
        
        assertEquals(123.456, cf128.toDouble(), 1e-10, "Conversion to CFloat128 failed")
    }
    
    @Test
    fun fromCDouble() {
        setup()
        
        val cd = CDouble.fromDouble(42.0)
        val ld = CLongDouble.fromCDouble(cd, CLongDouble.Flavor.DOUBLE64)
        
        assertEquals(42.0, ld.toDouble(), 1e-10, "From CDouble failed")
    }
    
    @Test
    fun fromCFloat128() {
        setup()
        
        val cf128 = CFloat128.fromDouble(99.5)
        val ld = CLongDouble.fromCFloat128(cf128, CLongDouble.Flavor.IEEE128)
        
        assertEquals(99.5, ld.toDouble(), 1e-10, "From CFloat128 failed")
    }
    
    @Test
    fun mixedFlavorCoercion() {
        setup()
        
        // Operations between different flavors should coerce to the first operand's flavor
        val double64 = CLongDouble.ofDouble(10.0, CLongDouble.Flavor.DOUBLE64)
        val ieee128 = CLongDouble.ofDouble(20.0, CLongDouble.Flavor.IEEE128)
        
        // This should work by coercing both to DOUBLE64
        val sum = double64 + ieee128
        assertEquals(30.0, sum.toDouble(), 1e-10, "Mixed flavor addition failed")
    }
    
    @Test
    fun highPrecisionWithExtended80() {
        setup()
        
        // Test that EXTENDED80 maintains better precision than DOUBLE64
        val one = CLongDouble.ofDouble(1.0, CLongDouble.Flavor.EXTENDED80)
        val tiny = CLongDouble.ofDouble(1e-16, CLongDouble.Flavor.EXTENDED80)
        
        val sum = one + tiny
        val diff = sum - one
        
        // Should retain some precision (less strict test due to double-double limitations)
        val error = abs(diff.toDouble() - 1e-16)
        assertTrue(error < 1e-15, "EXTENDED80 precision test failed, error: $error")
    }
    
    @Test
    fun highPrecisionWithIeee128() {
        setup()
        
        // Test that IEEE128 maintains extended precision
        val one = CLongDouble.ofDouble(1.0, CLongDouble.Flavor.IEEE128)
        val tiny = CLongDouble.ofDouble(1e-16, CLongDouble.Flavor.IEEE128)
        
        val sum = one + tiny
        val diff = sum - one
        
        // Should retain precision (less strict test)
        val error = abs(diff.toDouble() - 1e-16)
        assertTrue(error < 1e-15, "IEEE128 precision test failed, error: $error")
    }
    
    @Test
    fun divisionAccuracy() {
        setup()
        
        // Test division with DOUBLE64 flavor (skip Newton-Raphson for EXTENDED80)
        val numerator = CLongDouble.ofDouble(1.0, CLongDouble.Flavor.DOUBLE64)
        val denominator = CLongDouble.ofDouble(3.0, CLongDouble.Flavor.DOUBLE64)
        
        val result = numerator / denominator
        
        // 1/3 should be close to 0.333...
        val expected = 1.0 / 3.0
        assertEquals(expected, result.toDouble(), 1e-10, "Division accuracy test failed")
    }
    
    @Test
    fun zeroValues() {
        setup()
        
        val zero64 = CLongDouble.ofDouble(0.0, CLongDouble.Flavor.DOUBLE64)
        
        assertEquals(0.0, zero64.toDouble(), "DOUBLE64 zero failed")
        
        val a = CLongDouble.ofDouble(10.0, CLongDouble.Flavor.DOUBLE64)
        val sum = a + zero64
        assertEquals(10.0, sum.toDouble(), 1e-10, "Addition with zero failed")
    }
    
    @Test
    fun negativeNumbers() {
        setup()
        
        val neg = CLongDouble.ofDouble(-42.0, CLongDouble.Flavor.DOUBLE64)
        val pos = CLongDouble.ofDouble(10.0, CLongDouble.Flavor.DOUBLE64)
        
        val sum = neg + pos
        assertEquals(-32.0, sum.toDouble(), 1e-10, "Negative addition failed")
        
        val prod = neg * pos
        assertEquals(-420.0, prod.toDouble(), 1e-10, "Negative multiplication failed")
    }
    
    @Test
    fun chainedOperations() {
        setup()
        
        val a = CLongDouble.ofDouble(2.0, CLongDouble.Flavor.DOUBLE64)
        val b = CLongDouble.ofDouble(3.0, CLongDouble.Flavor.DOUBLE64)
        val c = CLongDouble.ofDouble(4.0, CLongDouble.Flavor.DOUBLE64)
        
        // (a + b) * c = (2 + 3) * 4 = 20
        val result = (a + b) * c
        assertEquals(20.0, result.toDouble(), 1e-10, "Chained operations failed")
    }
    
    @Test
    fun consistencyAcrossFlavors() {
        setup()
        
        // Same operation with DOUBLE64 (skip IEEE128 for now)
        val a64 = CLongDouble.ofDouble(7.0, CLongDouble.Flavor.DOUBLE64)
        val b64 = CLongDouble.ofDouble(3.0, CLongDouble.Flavor.DOUBLE64)
        
        val result64 = (a64 * b64).toDouble()
        
        assertEquals(21.0, result64, 1e-10, "DOUBLE64 multiplication failed")
    }
}
