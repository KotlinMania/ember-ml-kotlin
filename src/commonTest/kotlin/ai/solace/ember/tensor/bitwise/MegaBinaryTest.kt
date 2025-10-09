package ai.solace.ember.tensor.bitwise

import kotlin.test.*

class MegaBinaryTest {

    @Test
    fun testStringConstructor() {
        // Test the constructor that takes a binary string
        val binary = MegaBinary("1010")

        // Verify that the binary string is correct
        assertEquals("1010", binary.toBinaryString())

        // Test with 0b prefix
        val binaryWithPrefix = MegaBinary("0b1100")
        assertEquals("1100", binaryWithPrefix.toBinaryString())

        // Test with empty string (should default to "0")
        val emptyBinary = MegaBinary("")
        assertEquals("0", emptyBinary.toBinaryString())

        // Test with zero
        val zeroBinary = MegaBinary("0")
        assertEquals("0", zeroBinary.toBinaryString())

        // Test with leading zeros
        val leadingZeros = MegaBinary("00101", keepLeadingZeros = true)
        assertEquals("00101", leadingZeros.toBinaryString())

        // Test without leading zeros
        val noLeadingZeros = MegaBinary("00101", keepLeadingZeros = false)
        assertEquals("101", noLeadingZeros.toBinaryString())
    }

    @Test
    fun testCopyConstructor() {
        // Create a binary number
        val original = MegaBinary("1010")

        // Create a copy
        val copy = MegaBinary(original)

        // Verify that they have the same value
        assertEquals(original.toBinaryString(), copy.toBinaryString())

        // Verify that they are separate objects
        original.setBit(MegaBinary("0"), true)
        assertNotEquals(original.toBinaryString(), copy.toBinaryString())
    }

    @Test
    fun testByteArrayConstructor() {
        // Create a binary number from a byte array
        val bytes = byteArrayOf(0b00001010.toByte())
        val binary = MegaBinary(bytes)

        // Verify that the binary string is correct
        assertEquals("00001010", binary.toBinaryString())

        // Test with multiple bytes
        val multiBytes = byteArrayOf(0b00001010.toByte(), 0b00001111.toByte())
        val multiBinary = MegaBinary(multiBytes)
        assertEquals("0000101000001111", multiBinary.toBinaryString())
    }

    @Test
    fun testIntArrayConstructor() {
        // Create a binary number from an int array
        val ints = intArrayOf(10) // 1010 in binary
        val binary = MegaBinary(ints)

        // Verify that the binary string is correct
        assertEquals("1010", binary.toBinaryString())
    }

    @Test
    fun testBitwiseOperations() {
        val a = MegaBinary("1010")
        val b = MegaBinary("1100")

        // Test AND
        val and = a.bitwiseAnd(b)
        assertEquals("1000", and.toBinaryString())

        // Test OR
        val or = a.bitwiseOr(b)
        assertEquals("1110", or.toBinaryString())

        // Test XOR
        val xor = a.bitwiseXor(b)
        assertEquals("110", xor.toBinaryString())

        // Test NOT
        val not = a.bitwiseNot()
        // The result will depend on the internal representation size
        // For a 4-bit number, NOT 1010 would be 0101
        assertTrue(not.toBinaryString().endsWith("0101"))
    }

    @Test
    fun testShiftOperations() {
        val a = MegaBinary("1010")

        // Test left shift
        val leftShift = a.shiftLeft(MegaBinary("1"))
        assertEquals("10100", leftShift.toBinaryString())

        // Test right shift
        val rightShift = a.shiftRight(MegaBinary("1"))
        assertEquals("101", rightShift.toBinaryString())

        // Test larger shifts
        val largeLeftShift = a.shiftLeft(MegaBinary("11"))
        assertEquals("1010000", largeLeftShift.toBinaryString())

        val largeRightShift = a.shiftRight(MegaBinary("10"))
        assertEquals("10", largeRightShift.toBinaryString())

        // Test shift by zero
        val zeroShift = a.shiftLeft(MegaBinary("0"))
        assertEquals("1010", zeroShift.toBinaryString())
    }

    @Test
    fun testBitManipulation() {
        val a = MegaBinary("1010")

        // Debug output
        println("[DEBUG_LOG] Binary string: ${a.toBinaryString()}")
        println("[DEBUG_LOG] Reversed binary string: ${a.toBinaryString().reversed()}")
        println("[DEBUG_LOG] Bit at position 0: ${a.getBit(MegaBinary("0"))}")
        println("[DEBUG_LOG] Bit at position 1: ${a.getBit(MegaBinary("1"))}")
        println("[DEBUG_LOG] Bit at position 2: ${a.getBit(MegaBinary("10"))}")
        println("[DEBUG_LOG] Bit at position 3: ${a.getBit(MegaBinary("11"))}")

        // Test getBit
        assertTrue(a.getBit(MegaBinary("1")))
        assertFalse(a.getBit(MegaBinary("0")))
        assertTrue(a.getBit(MegaBinary("11")))
        assertFalse(a.getBit(MegaBinary("10")))

        // Test setBit
        val b = MegaBinary("1010")
        b.setBit(MegaBinary("0"), true)
        assertEquals("1011", b.toBinaryString())

        b.setBit(MegaBinary("10"), true)
        assertEquals("1111", b.toBinaryString())

        b.setBit(MegaBinary("11"), false)
        assertEquals("0111", b.toBinaryString())
    }

    @Test
    fun testPropagate() {
        val a = MegaBinary("1010")

        // Test propagate (which is just a left shift)
        val propagated = a.propagate(MegaBinary("10"))
        assertEquals("101000", propagated.toBinaryString())
    }

    @Test
    fun testConversionMethods() {
        val a = MegaBinary("1010")

        // Test toBits (LSB first)
        val bits = a.toBits()
        assertEquals(listOf(0, 1, 0, 1), bits)

        // Test toBitsBigEndian (MSB first)
        val bitsBE = a.toBitsBigEndian()
        assertEquals(listOf(1, 0, 1, 0), bitsBE)

        // Test toBytes
        val bytes = a.toBytes()
        assertEquals(1, bytes.size)
        assertEquals(10, bytes[0].toInt())
    }

    @Test
    fun testUtilityMethods() {
        // Test isZero
        val zero = MegaBinary("0")
        assertTrue(zero.isZero())

        val nonZero = MegaBinary("1010")
        assertFalse(nonZero.isZero())

        // Test copy
        val original = MegaBinary("1010")
        val copy = original.copy()
        assertEquals(original.toBinaryString(), copy.toBinaryString())

        // Verify that they are separate objects
        original.setBit(MegaBinary("0"), true)
        assertNotEquals(original.toBinaryString(), copy.toBinaryString())
    }

    @Test
    fun testToString() {
        val a = MegaBinary("1010")
        assertEquals("<MegaBinary 1010>", a.toString())
    }

    @Test
    fun testInterfere() {
        val a = MegaBinary("1010")
        val b = MegaBinary("1100")
        val c = MegaBinary("0101")

        // Test XOR interference
        val xorResult = MegaBinary.interfere(listOf(a, b, c), InterferenceMode.XOR)
        assertEquals("11", xorResult.toBinaryString())

        // Test AND interference
        val andResult = MegaBinary.interfere(listOf(a, b, c), InterferenceMode.AND)
        assertEquals("0000", andResult.toBinaryString())

        // Test OR interference
        val orResult = MegaBinary.interfere(listOf(a, b, c), InterferenceMode.OR)
        assertEquals("1111", orResult.toBinaryString())

        // Test with single wave
        val singleResult = MegaBinary.interfere(listOf(a), InterferenceMode.XOR)
        assertEquals("1010", singleResult.toBinaryString())

        // Test with empty list
        assertFailsWith<IllegalArgumentException> {
            MegaBinary.interfere(emptyList(), InterferenceMode.XOR)
        }
    }

    @Test
    fun testGenerateBlockySin() {
        // Test with length 8 and half period 2
        val sin = MegaBinary.generateBlockySin(MegaBinary("1000"), MegaBinary("10"))
        assertEquals("11001100", sin.toBinaryString())

        // Test with length 10 and half period 3
        val sin2 = MegaBinary.generateBlockySin(MegaBinary("1010"), MegaBinary("11"))
        assertEquals("1110001110", sin2.toBinaryString())

        // Test with invalid half period
        assertFailsWith<IllegalArgumentException> {
            MegaBinary.generateBlockySin(MegaBinary("1000"), MegaBinary("0"))
        }

        // Test with invalid length
        val zeroLength = MegaBinary.generateBlockySin(MegaBinary("0"), MegaBinary("10"))
        assertEquals("0", zeroLength.toBinaryString())
    }

    @Test
    fun testCreateDutyCycle() {
        // Test with length 8 and duty cycle 3
        val duty = MegaBinary.createDutyCycle(MegaBinary("1000"), MegaBinary("11"))
        assertEquals("11100000", duty.toBinaryString())

        // Test with length 10 and duty cycle 5
        val duty2 = MegaBinary.createDutyCycle(MegaBinary("1010"), MegaBinary("101"))
        assertEquals("1111100000", duty2.toBinaryString())

        // Test with invalid duty cycle (negative)
        assertFailsWith<IllegalArgumentException> {
            MegaBinary.createDutyCycle(MegaBinary("1000"), MegaBinary("-1"))
        }

        // Test with invalid duty cycle (too large)
        assertFailsWith<IllegalArgumentException> {
            MegaBinary.createDutyCycle(MegaBinary("1000"), MegaBinary("1001"))
        }

        // Test with invalid length
        val zeroLength = MegaBinary.createDutyCycle(MegaBinary("0"), MegaBinary("0"))
        assertEquals("0", zeroLength.toBinaryString())
    }

    @Test
    fun testInheritedArithmeticOperations() {
        val a = MegaBinary("1010") // 10 in decimal
        val b = MegaBinary("0101") // 5 in decimal

        // Test addition
        val sum = a.add(b)
        assertEquals("15", sum.toDecimalString()) // 15 in decimal

        // Test subtraction
        val diff = a.sub(b)
        assertEquals("5", diff.toDecimalString()) // 5 in decimal

        // Test multiplication
        val product = a.mul(b)
        assertEquals("50", product.toDecimalString()) // 50 in decimal

        // Test division
        val quotient = a.divide(b)
        assertEquals("2", quotient.toDecimalString()) // 2 in decimal

        // Test square root
        val sqrt = a.sqrt()
        assertEquals("3", sqrt.toDecimalString()) // 3 in decimal (integer sqrt of 10)
    }

    @Test
    fun testInvalidBinaryString() {
        // Test with invalid binary string (contains non-binary characters)
        assertFailsWith<IllegalArgumentException> {
            MegaBinary("1012")
        }
    }
}
