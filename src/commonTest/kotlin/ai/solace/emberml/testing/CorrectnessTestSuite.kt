/**
 * Correctness tests against reference implementations.
 * 
 * This test suite verifies that the Kotlin implementation produces
 * results consistent with the Python reference implementation.
 */
package ai.solace.ember.testing

import ai.solace.ember.tensor.bitwise.MegaBinary
import ai.solace.ember.tensor.bitwise.InterferenceMode
import kotlin.test.*

/**
 * Correctness test data matching Python implementation expected values.
 * 
 * These values are derived from the Python tests in the ember_ml repository
 * to ensure consistency between implementations.
 */
object ReferenceTestData {
    
    /**
     * Test data matching numpy_tests/test_numpy_ops_bitwise.py
     */
    val bitwiseTestCases = mapOf(
        // Basic test values
        "data_a" to "1010",      // 10 in decimal
        "data_b" to "1100",      // 12 in decimal  
        "data_c" to "0011",      // 3 in decimal
        "data_zero" to "0000",   // 0 in decimal
        
        // Expected results for basic operations
        "expected_and" to "1000",     // 10 & 12 = 8
        "expected_or" to "1110",      // 10 | 12 = 14
        "expected_xor" to "0110",     // 10 ^ 12 = 6
        
        // Expected results for multi-wave operations
        "expected_and_all" to "0000", // 10 & 12 & 3 = 0
        "expected_or_all" to "1111",  // 10 | 12 | 3 = 15
        "expected_xor_all" to "0101"  // 10 ^ 12 ^ 3 = 5
    )
    
    /**
     * Shift operation test cases
     */
    val shiftTestCases = mapOf(
        "data" to "1010",           // 10 in decimal
        "left_shift_1" to "10100",  // 10 << 1 = 20
        "left_shift_2" to "101000", // 10 << 2 = 40
        "right_shift_1" to "101",   // 10 >> 1 = 5
        "right_shift_2" to "10"     // 10 >> 2 = 2
    )
    
    /**
     * Pattern generation test cases
     */
    val patternTestCases = mapOf(
        // Duty cycle patterns
        "duty_8_3" to "11100000",     // 8 bits, 3 high
        "duty_10_5" to "1111100000",  // 10 bits, 5 high
        
        // Blocky sine patterns  
        "blocky_8_2" to "11001100",   // 8 bits, half period 2
        "blocky_10_3" to "1110001110" // 10 bits, half period 3
    )
}

/**
 * Correctness tests against reference implementations.
 * 
 * These tests verify that the Kotlin implementation produces results
 * that match the expected behavior from the Python reference implementation.
 */
class CorrectnessTestSuite {

    /**
     * Test basic bitwise operations against reference values
     */
    @Test
    fun testBasicBitwiseOperationsCorrectness() {
        val a = MegaBinary(ReferenceTestData.bitwiseTestCases["data_a"]!!)
        val b = MegaBinary(ReferenceTestData.bitwiseTestCases["data_b"]!!)
        
        // Test AND operation
        val andResult = a.bitwiseAnd(b)
        assertEquals(
            ReferenceTestData.bitwiseTestCases["expected_and"],
            andResult.toBinaryString(),
            "AND operation should match reference implementation"
        )
        
        // Test OR operation
        val orResult = a.bitwiseOr(b)
        assertEquals(
            ReferenceTestData.bitwiseTestCases["expected_or"],
            orResult.toBinaryString(),
            "OR operation should match reference implementation"
        )
        
        // Test XOR operation
        val xorResult = a.bitwiseXor(b)
        assertEquals(
            ReferenceTestData.bitwiseTestCases["expected_xor"],
            xorResult.toBinaryString(),
            "XOR operation should match reference implementation"
        )
    }

    /**
     * Test multi-wave interference operations against reference values
     */
    @Test
    fun testMultiWaveInterferenceCorrectness() {
        val a = MegaBinary(ReferenceTestData.bitwiseTestCases["data_a"]!!)
        val b = MegaBinary(ReferenceTestData.bitwiseTestCases["data_b"]!!)
        val c = MegaBinary(ReferenceTestData.bitwiseTestCases["data_c"]!!)
        val waves = listOf(a, b, c)
        
        // Test XOR interference
        val xorResult = MegaBinary.interfere(waves, InterferenceMode.XOR)
        assertEquals(
            ReferenceTestData.bitwiseTestCases["expected_xor_all"],
            xorResult.toBinaryString(),
            "XOR interference should match reference implementation"
        )
        
        // Test AND interference
        val andResult = MegaBinary.interfere(waves, InterferenceMode.AND)
        assertEquals(
            ReferenceTestData.bitwiseTestCases["expected_and_all"],
            andResult.toBinaryString(),
            "AND interference should match reference implementation"
        )
        
        // Test OR interference
        val orResult = MegaBinary.interfere(waves, InterferenceMode.OR)
        assertEquals(
            ReferenceTestData.bitwiseTestCases["expected_or_all"],
            orResult.toBinaryString(),
            "OR interference should match reference implementation"
        )
    }

    /**
     * Test shift operations against reference values
     */
    @Test
    fun testShiftOperationsCorrectness() {
        val data = MegaBinary(ReferenceTestData.shiftTestCases["data"]!!)
        
        // Test left shifts
        val leftShift1 = data.shiftLeft(MegaBinary("1"))
        assertEquals(
            ReferenceTestData.shiftTestCases["left_shift_1"],
            leftShift1.toBinaryString(),
            "Left shift by 1 should match reference"
        )
        
        val leftShift2 = data.shiftLeft(MegaBinary("10"))
        assertEquals(
            ReferenceTestData.shiftTestCases["left_shift_2"],
            leftShift2.toBinaryString(),
            "Left shift by 2 should match reference"
        )
        
        // Test right shifts
        val rightShift1 = data.shiftRight(MegaBinary("1"))
        assertEquals(
            ReferenceTestData.shiftTestCases["right_shift_1"],
            rightShift1.toBinaryString(),
            "Right shift by 1 should match reference"
        )
        
        val rightShift2 = data.shiftRight(MegaBinary("10"))
        assertEquals(
            ReferenceTestData.shiftTestCases["right_shift_2"],
            rightShift2.toBinaryString(),
            "Right shift by 2 should match reference"
        )
    }

    /**
     * Test pattern generation against reference values
     */
    @Test
    fun testPatternGenerationCorrectness() {
        // Test duty cycle generation
        val duty8_3 = MegaBinary.createDutyCycle(MegaBinary("1000"), MegaBinary("11"))
        assertEquals(
            ReferenceTestData.patternTestCases["duty_8_3"],
            duty8_3.toBinaryString(),
            "8-bit duty cycle with 3 high bits should match reference"
        )
        
        val duty10_5 = MegaBinary.createDutyCycle(MegaBinary("1010"), MegaBinary("101"))
        assertEquals(
            ReferenceTestData.patternTestCases["duty_10_5"],
            duty10_5.toBinaryString(),
            "10-bit duty cycle with 5 high bits should match reference"
        )
        
        // Test blocky sine generation
        val blocky8_2 = MegaBinary.generateBlockySin(MegaBinary("1000"), MegaBinary("10"))
        assertEquals(
            ReferenceTestData.patternTestCases["blocky_8_2"],
            blocky8_2.toBinaryString(),
            "8-bit blocky sine with half period 2 should match reference"
        )
        
        val blocky10_3 = MegaBinary.generateBlockySin(MegaBinary("1010"), MegaBinary("11"))
        assertEquals(
            ReferenceTestData.patternTestCases["blocky_10_3"],
            blocky10_3.toBinaryString(),
            "10-bit blocky sine with half period 3 should match reference"
        )
    }

    /**
     * Test bit manipulation operations for correctness
     */
    @Test
    fun testBitManipulationCorrectness() {
        val data = MegaBinary("1010") // Binary: 1010, Decimal: 10
        
        // Test getBit - these should match Python implementation behavior
        assertTrue(data.getBit(MegaBinary("1")), "Bit 1 should be set (reference)")
        assertFalse(data.getBit(MegaBinary("0")), "Bit 0 should not be set (reference)")
        assertTrue(data.getBit(MegaBinary("11")), "Bit 3 should be set (reference)")
        assertFalse(data.getBit(MegaBinary("10")), "Bit 2 should not be set (reference)")
        
        // Test setBit consistency
        val mutableData = MegaBinary("1010")
        mutableData.setBit(MegaBinary("0"), true)
        assertTrue(mutableData.getBit(MegaBinary("0")), "Set bit should be retrievable")
        
        mutableData.setBit(MegaBinary("1"), false)
        assertFalse(mutableData.getBit(MegaBinary("1")), "Cleared bit should not be set")
    }

    /**
     * Test numerical consistency with reference implementation
     */
    @Test
    fun testNumericalConsistency() {
        val testCases = listOf(
            "0000" to 0,
            "0001" to 1,
            "1010" to 10,
            "1100" to 12,
            "1111" to 15
        )
        
        testCases.forEach { (binary, decimal) ->
            val megaBinary = MegaBinary(binary)
            assertEquals(
                decimal.toString(),
                megaBinary.toDecimalString(),
                "Binary $binary should convert to decimal $decimal"
            )
        }
    }

    /**
     * Test edge cases for correctness
     */
    @Test
    fun testEdgeCasesCorrectness() {
        // Test zero values
        val zero = MegaBinary("0")
        assertEquals("0", zero.toBinaryString(), "Zero should remain zero")
        assertEquals("0", zero.toDecimalString(), "Zero should convert to decimal 0")
        
        // Test single bit values
        val one = MegaBinary("1")
        assertEquals("1", one.toBinaryString(), "One should remain one")
        assertEquals("1", one.toDecimalString(), "One should convert to decimal 1")
        
        // Test operations with zero
        val data = MegaBinary("1010")
        val andWithZero = data.bitwiseAnd(zero)
        assertEquals("0", andWithZero.toBinaryString(), "AND with zero should be zero")
        
        val orWithZero = data.bitwiseOr(zero)
        assertEquals("1010", orWithZero.toBinaryString(), "OR with zero should preserve original")
    }

    /**
     * Test consistency across different input formats
     */
    @Test
    fun testInputFormatConsistency() {
        // Test that different input formats produce consistent results
        val binary1 = MegaBinary("1010")
        val binary2 = MegaBinary("0b1010")
        
        assertEquals(
            binary1.toBinaryString(),
            binary2.toBinaryString(),
            "Different input formats should produce same result"
        )
        
        assertEquals(
            binary1.toDecimalString(),
            binary2.toDecimalString(),
            "Different input formats should have same decimal value"
        )
    }

    /**
     * Test operation commutativity and associativity where applicable
     */
    @Test
    fun testMathematicalProperties() {
        val a = MegaBinary("1010")
        val b = MegaBinary("1100")
        val c = MegaBinary("0011")
        
        // Test commutativity: a OP b = b OP a
        assertEquals(
            a.bitwiseAnd(b).toBinaryString(),
            b.bitwiseAnd(a).toBinaryString(),
            "AND should be commutative"
        )
        
        assertEquals(
            a.bitwiseOr(b).toBinaryString(),
            b.bitwiseOr(a).toBinaryString(),
            "OR should be commutative"
        )
        
        assertEquals(
            a.bitwiseXor(b).toBinaryString(),
            b.bitwiseXor(a).toBinaryString(),
            "XOR should be commutative"
        )
        
        // Test associativity: (a OP b) OP c = a OP (b OP c)
        val leftAssocAnd = a.bitwiseAnd(b).bitwiseAnd(c)
        val rightAssocAnd = a.bitwiseAnd(b.bitwiseAnd(c))
        assertEquals(
            leftAssocAnd.toBinaryString(),
            rightAssocAnd.toBinaryString(),
            "AND should be associative"
        )
    }
}