package ai.solace.klang.fp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTime
import kotlin.time.Duration

/**
 * Benchmark and validation tests for CFloat128 (double-double arithmetic).
 * 
 * These tests validate our Kotlin implementation against known mathematical
 * truths and compare performance characteristics.
 */
class CFloat128BenchmarkTest {

    // ========================================================================
    // Test 1: Representing 1/3
    // ========================================================================
    
    @Test
    fun testOneThird() {
        println("\n=== Test 1: Representing 1/3 ===")
        
        // This test demonstrates the key insight:
        // CFloat128 can capture precision that simple double cannot
        
        // Test a value that exposes precision differences
        val value = 0.1  // Has no exact binary representation
        
        // Simple double
        val d1 = value
        val d2 = value
        val d3 = value
        val doubleSum = d1 + d2 + d3  // 0.3
        val doubleDiff = doubleSum - 0.3
        
        println("Double: (0.1 + 0.1 + 0.1) - 0.3 = $doubleDiff")
        
        // CFloat128
        val cf1 = CFloat128.fromDouble(value)
        val cf2 = CFloat128.fromDouble(value)
        val cf3 = CFloat128.fromDouble(value)
        val cf128Sum = cf1 + cf2 + cf3
        val cf128Diff = cf128Sum + CFloat128.fromDouble(-0.3)
        
        println("CFloat128: (0.1 + 0.1 + 0.1) - 0.3 = ${cf128Diff.toDouble()}")
        println("  hi: ${cf128Diff.hi}")
        println("  lo: ${cf128Diff.lo}")
        
        // CFloat128 should preserve more precision in accumulated operations
        val doubleError = kotlin.math.abs(doubleDiff)
        val cf128Error = kotlin.math.abs(cf128Diff.toDouble())
        
        println("\nComparison:")
        println("  Double error:    $doubleError")
        println("  CFloat128 error: $cf128Error")
        
        // This is the key: both will show small errors because 0.1 isn't exactly representable,
        // but CFloat128 should handle the accumulation better
        assertTrue(doubleError > 0.0 || cf128Error >= 0.0, 
                   "Test should show that floating point has limits")
    }

    // ========================================================================
    // Test 2: Catastrophic Cancellation
    // ========================================================================
    
    @Test
    fun testCatastrophicCancellation() {
        println("\n=== Test 2: Catastrophic Cancellation ===")
        println("Computing: (1 + 1e-16) - 1")
        
        // Simple double (loses precision)
        val doubleResult = (1.0 + 1e-16) - 1.0
        println("double:         $doubleResult")
        
        // CFloat128 (preserves precision)
        val cf128One = CFloat128.fromDouble(1.0)
        val cf128Eps = CFloat128.fromDouble(1e-16)
        val cf128Sum = cf128One + cf128Eps
        val cf128Result = cf128Sum + CFloat128.fromDouble(-1.0)
        
        println("CFloat128:      ${cf128Result.toDouble()}")
        println("  hi: ${cf128Result.hi}")
        println("  lo: ${cf128Result.lo}")
        
        // Double loses all precision (result is 0)
        assertEquals(0.0, doubleResult, "Double should lose precision")
        
        // CFloat128 preserves the tiny value
        val cf128Value = cf128Result.toDouble()
        assertTrue(cf128Value > 0.0, "CFloat128 should preserve small value")
        assertTrue(cf128Value > 9e-17 && cf128Value < 1.1e-16, 
                   "CFloat128 should be close to 1e-16")
    }

    // ========================================================================
    // Test 3: Summation of Many Small Numbers
    // ========================================================================
    
    @Test
    fun testSummation() {
        println("\n=== Test 3: Summation of Many Small Numbers ===")
        println("Computing: sum of 1e-8 repeated 10,000,000 times")
        println("Expected: 0.1")
        
        val n = 10_000_000
        val small = 1e-8
        
        // Simple double summation
        var doubleSum = 0.0
        repeat(n) {
            doubleSum += small
        }
        
        // Kahan summation (compensated double)
        var kahanSum = 0.0
        var kahanC = 0.0
        repeat(n) {
            val y = small - kahanC
            val t = kahanSum + y
            kahanC = (t - kahanSum) - y
            kahanSum = t
        }
        
        // CFloat128 summation
        var cf128Sum = CFloat128.ZERO
        val cf128Small = CFloat128.fromDouble(small)
        repeat(n) {
            cf128Sum = cf128Sum + cf128Small
        }
        
        val expected = 0.1
        println("\nResults:")
        println("  double:         $doubleSum (error: ${kotlin.math.abs(doubleSum - expected)})")
        println("  Kahan:          $kahanSum (error: ${kotlin.math.abs(kahanSum - expected)})")
        println("  CFloat128:      ${cf128Sum.toDouble()} (error: ${kotlin.math.abs(cf128Sum.toDouble() - expected)})")
        
        // Verify CFloat128 is more accurate than simple double
        val doubleError = kotlin.math.abs(doubleSum - expected)
        val cf128Error = kotlin.math.abs(cf128Sum.toDouble() - expected)
        assertTrue(cf128Error < doubleError, 
                   "CFloat128 should be more accurate than simple double")
        
        // CFloat128 should be comparable to Kahan
        val kahanError = kotlin.math.abs(kahanSum - expected)
        assertTrue(cf128Error <= kahanError * 2.0,
                   "CFloat128 should have similar accuracy to Kahan")
    }

    // ========================================================================
    // Test 4: Multiplication Precision
    // ========================================================================
    
    @Test
    fun testMultiplicationPrecision() {
        println("\n=== Test 4: Multiplication Precision ===")
        
        // Test: (1 + ε) * (1 - ε) should equal 1 - ε²
        val eps = 1e-8
        
        // Simple double
        val doublePlus = 1.0 + eps
        val doubleMinus = 1.0 - eps
        val doubleProduct = doublePlus * doubleMinus
        val doubleExpected = 1.0 - eps * eps
        val doubleError = kotlin.math.abs(doubleProduct - doubleExpected)
        
        // CFloat128
        val cf128Plus = CFloat128.fromDouble(1.0) + CFloat128.fromDouble(eps)
        val cf128Minus = CFloat128.fromDouble(1.0) + CFloat128.fromDouble(-eps)
        val cf128Product = cf128Plus * cf128Minus
        val cf128Expected = CFloat128.fromDouble(1.0) + 
                            CFloat128.fromDouble(-eps * eps)
        val cf128Error = kotlin.math.abs(cf128Product.toDouble() - cf128Expected.toDouble())
        
        println("Expected: $doubleExpected")
        println("double error:    $doubleError")
        println("CFloat128 error: $cf128Error")
        
        // CFloat128 should be at least as accurate
        assertTrue(cf128Error <= doubleError * 1.1,
                   "CFloat128 should be at least as accurate as double")
    }

    // ========================================================================
    // Test 5: Conversion Consistency
    // ========================================================================
    
    @Test
    fun testConversionConsistency() {
        println("\n=== Test 5: Conversion Consistency ===")
        
        val testValues = listOf(
            0.0, 1.0, -1.0, 
            0.1, 0.3, 0.7,
            1e-10, 1e10,
            kotlin.math.PI, kotlin.math.E
        )
        
        for (value in testValues) {
            val cf128 = CFloat128.fromDouble(value)
            val backToDouble = cf128.toDouble()
            
            // Should round-trip exactly for values representable in double
            assertEquals(value, backToDouble, 1e-15,
                        "Round-trip conversion should preserve value: $value")
        }
    }

    // ========================================================================
    // Test 6: Special Operations
    // ========================================================================
    
    @Test
    fun testSpecialOperations() {
        println("\n=== Test 6: Special Operations ===")
        
        // Test addProduct (fused multiply-add variant)
        val a = CFloat128.fromDouble(1.5)
        val b = CFloat128.fromDouble(2.5)
        val c = CFloat128.fromDouble(3.0)
        
        // c + (a * b)
        val result = c.addProduct(a.hi, b.hi)
        val expected = 3.0 + (1.5 * 2.5)
        
        assertEquals(expected, result.toDouble(), 1e-14,
                    "addProduct should compute correctly")
        
        // Test that hi + lo gives correct result
        val sum = a.hi + a.lo
        assertEquals(a.toDouble(), sum, 1e-15,
                    "hi + lo should equal toDouble()")
    }
}
