package ai.solace.klang.fp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Bit-exact validation of CFloat128 against C reference implementation.
 * 
 * This validates that our Kotlin CFloat128 (double-double) produces identical
 * bit patterns to the C double-double implementation in float128_bitcompare.c
 */
class CFloat128BitValidationTest {
    
    // Helper to convert Double to hex string for debugging
    private fun Double.toHexString(): String {
        val bits = this.toRawBits()
        return "0x${bits.toULong().toString(16).uppercase().padStart(16, '0')}"
    }
    
    // Helper to print double-double as bit patterns
    private fun printDD(label: String, dd: CFloat128) {
        println("$label:")
        println("  hi: ${dd.hi.toHexString()} (${dd.hi})")
        println("  lo: ${dd.lo.toHexString()} (${dd.lo})")
    }
    
    @Test
    fun testBasicValuesBitPatterns() {
        println("\n=== Test: Basic Values Bit Patterns ===")
        
        // From C reference output
        val tests = mapOf(
            "Zero" to (0.0 to Pair(0x0000000000000000L, 0x0000000000000000L)),
            "One" to (1.0 to Pair(0x3FF0000000000000L, 0x0000000000000000L)),
            "Minus One" to (-1.0 to Pair(-0x4010000000000000L, 0x0000000000000000L))  // -1.0 bit pattern
        )
        
        for ((name, pair) in tests) {
            val (value, expected) = pair
            val (expectedHi, expectedLo) = expected
            
            val cf128 = CFloat128.fromDouble(value)
            
            println("\n$name:")
            println("  Expected hi: 0x${expectedHi.toULong().toString(16).uppercase().padStart(16, '0')}")
            println("  Actual hi:   ${cf128.hi.toHexString()}")
            println("  Expected lo: 0x${expectedLo.toULong().toString(16).uppercase().padStart(16, '0')}")
            println("  Actual lo:   ${cf128.lo.toHexString()}")
            
            assertEquals(expectedHi, cf128.hi.toRawBits(), "$name: hi bits mismatch")
            assertEquals(expectedLo, cf128.lo.toRawBits(), "$name: lo bits mismatch")
        }
    }
    
    @Test
    fun testCriticalAccumulationTest() {
        println("\n=== Test: Critical Accumulation (0.1 + 0.1 + 0.1) - 0.3 ===")
        println("This tests that CFloat128 matches C's 2× precision improvement")
        
        // Simple double (as baseline)
        val d1 = 0.1
        val d2 = 0.1
        val d3 = 0.3
        val doubleResult = (d1 + d1 + d1) - d3
        val doubleError = kotlin.math.abs(doubleResult)
        
        println("\nDouble precision:")
        println("  Result: $doubleResult")
        println("  Error:  $doubleError")
        
        // CFloat128
        val cf1 = CFloat128.fromDouble(0.1)
        val cf2 = CFloat128.fromDouble(0.1)
        val cf3 = CFloat128.fromDouble(0.1)
        val cfTarget = CFloat128.fromDouble(0.3)
        
        val cfSum = cf1 + cf2 + cf3
        val cfResult = cfSum + CFloat128(-cfTarget.hi, -cfTarget.lo)
        val cfError = kotlin.math.abs(cfResult.toDouble())
        
        println("\nCFloat128 precision:")
        printDD("  (0.1+0.1+0.1)", cfSum)
        printDD("  Result", cfResult)
        println("  Error:  $cfError")
        
        // From C reference: double error = 5.551115e-17, dd error = 2.775558e-17
        // CFloat128 should be approximately 2× better than double
        println("\nComparison:")
        println("  Double error:    $doubleError")
        println("  CFloat128 error: $cfError")
        println("  Ratio:           ${doubleError / cfError}x")
        
        // Verify CFloat128 is more precise
        assertTrue(cfError < doubleError, 
                   "CFloat128 should be more precise than double")
        
        // Should be approximately 2× better (allow some tolerance)
        val ratio = doubleError / cfError
        assertTrue(ratio > 1.5 && ratio < 2.5,
                   "CFloat128 should be ~2× more precise (got ${ratio}×)")
    }
    
    @Test
    fun testMultiplicationPrecision() {
        println("\n=== Test: Multiplication Precision ===")
        
        // One Third squared - this exposes precision in multiplication
        val oneThird = 1.0 / 3.0
        
        // Double
        val dResult = oneThird * oneThird
        println("Double: (1/3)² = $dResult")
        println("  Bits: ${dResult.toHexString()}")
        
        // CFloat128
        val cfOneThird = CFloat128.fromDouble(oneThird)
        val cfResult = cfOneThird * cfOneThird
        
        println("\nCFloat128: (1/3)² =")
        printDD("  Result", cfResult)
        
        // From C reference: hi = 0x3FBC71C71C71C71C, lo = 0xBCB71C71C71C71C7 (negative!)
        // The lo component captures error that double loses
        
        // Key test: lo should be non-zero (capturing extra precision)
        assertTrue(cfResult.lo != 0.0, 
                   "CFloat128 multiplication should capture extra precision in lo")
        
        // From C: lo ≈ -6.167906e-18 (negative because of rounding direction)
        println("\nPrecision check:")
        println("  lo component: ${cfResult.lo} (${cfResult.lo.toHexString()})")
        println("  Expected: ~-6e-18")
        
        val expectedLo = -6.167906e-18
        val loError = kotlin.math.abs(cfResult.lo - expectedLo)
        assertTrue(loError < 1e-20,
                   "CFloat128 multiplication lo component should match C reference")
    }
    
    @Test
    fun testAdditionPrecisionCapture() {
        println("\n=== Test: Addition Precision Capture ===")
        
        // Test that CFloat128 captures precision during addition
        val small = 1e-16
        val large = 1.0
        
        // Double: precision loss
        val dResult = large + small
        val dRecovered = dResult - large
        
        println("Double:")
        println("  (1.0 + 1e-16) - 1.0 = $dRecovered")
        println("  Lost: ${kotlin.math.abs(dRecovered - small)}")
        
        // CFloat128: should preserve precision
        val cfLarge = CFloat128.fromDouble(large)
        val cfSmall = CFloat128.fromDouble(small)
        val cfSum = cfLarge + cfSmall
        val cfRecovered = cfSum + CFloat128(-large, 0.0)
        
        println("\nCFloat128:")
        printDD("  (1.0 + 1e-16)", cfSum)
        printDD("  Result", cfRecovered)
        
        val recovered = cfRecovered.toDouble()
        println("  Recovered: $recovered")
        println("  Lost: ${kotlin.math.abs(recovered - small)}")
        
        // CFloat128 should preserve more precision
        val dLoss = kotlin.math.abs(dRecovered - small)
        val cfLoss = kotlin.math.abs(recovered - small)
        
        assertTrue(cfLoss < dLoss,
                   "CFloat128 should lose less precision than double")
    }
    
    @Test
    fun testPiBitPattern() {
        println("\n=== Test: Pi Bit Pattern ===")
        
        // From C reference: Pi = 0x400921FB54442D18
        val expectedPi = 0x400921FB54442D18L
        
        val cfPi = CFloat128.fromDouble(kotlin.math.PI)
        
        println("Pi:")
        println("  Expected: 0x${expectedPi.toULong().toString(16).uppercase().padStart(16, '0')}")
        println("  Actual:   ${cfPi.hi.toHexString()}")
        
        assertEquals(expectedPi, cfPi.hi.toRawBits(), "Pi hi bits mismatch")
        assertEquals(0L, cfPi.lo.toRawBits(), "Pi lo should be zero (exact in double)")
    }
    
    @Test
    fun testEBitPattern() {
        println("\n=== Test: E Bit Pattern ===")
        
        // From C reference: E = 0x4005BF0A8B145769
        val expectedE = 0x4005BF0A8B145769L
        
        val cfE = CFloat128.fromDouble(kotlin.math.E)
        
        println("E:")
        println("  Expected: 0x${expectedE.toULong().toString(16).uppercase().padStart(16, '0')}")
        println("  Actual:   ${cfE.hi.toHexString()}")
        
        assertEquals(expectedE, cfE.hi.toRawBits(), "E hi bits mismatch")
        assertEquals(0L, cfE.lo.toRawBits(), "E lo should be zero (exact in double)")
    }
    
    @Test
    fun testSummationConvergence() {
        println("\n=== Test: Summation Convergence ===")
        println("Sum 1e-8 repeatedly and compare precision")
        
        val n = 10_000_000
        val small = 1e-8
        val expected = n * small
        
        // Double
        var dSum = 0.0
        repeat(n) {
            dSum += small
        }
        val dError = kotlin.math.abs(dSum - expected)
        
        // CFloat128
        var cfSum = CFloat128.ZERO
        val cfSmall = CFloat128.fromDouble(small)
        repeat(n) {
            cfSum = cfSum + cfSmall
        }
        val cfError = kotlin.math.abs(cfSum.toDouble() - expected)
        
        println("\nResults:")
        println("  Expected:      $expected")
        println("  Double:        $dSum (error: $dError)")
        println("  CFloat128:     ${cfSum.toDouble()} (error: $cfError)")
        println("  Improvement:   ${dError / cfError}×")
        
        // CFloat128 should have lower error
        assertTrue(cfError <= dError,
                   "CFloat128 should have equal or better precision for summation")
    }
}
