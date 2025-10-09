package ai.solace.ember.tensor.bitwise

import kotlin.test.*

class MegaIntegerTest {

    @Test
    fun testDecimalStringConstructor() {
        // Test the constructor that takes a decimal string
        val intFromString = MegaInteger.fromValue("12345")

        // Verify that the mantissa is correct
        assertEquals("12345", intFromString.toDecimalString())

        // Verify that the sign is correct
        assertFalse(intFromString.negative)

        // Test with a negative number
        val negativeInt = MegaInteger.fromValue("-54321")
        assertEquals("-54321", negativeInt.toDecimalString())
        assertTrue(negativeInt.negative)

        // Test with zero
        val zero = MegaInteger.fromValue("0")
        assertEquals("0", zero.toDecimalString())
        assertFalse(zero.negative)
    }

    @Test
    fun testExponentIsAlwaysZero() {
        // Create a MegaInteger with non-zero exponent
        val integer = MegaInteger(
            mantissa = intArrayOf(123),
            negative = false,
            keepLeadingZeros = false
        )

        // Verify that exponent is always zero
        assertEquals(1, integer.exponent.mantissa.size)
        assertEquals(0, integer.exponent.mantissa[0])

        // Verify that isFloat is always false
        assertFalse(integer.isFloat)
    }

    @Test
    fun testArithmeticOperations() {
        val a = MegaInteger.fromValue("100")
        val b = MegaInteger.fromValue("50")

        // Test addition
        val sum = a.add(b)
        val sumStr = sum.toDecimalString()
        println("[DEBUG_LOG] Sum decimal string: $sumStr")
        assertEquals("150", sumStr)

        // Test subtraction
        val diff = a.sub(b)
        val diffStr = diff.toDecimalString()
        println("[DEBUG_LOG] Difference decimal string: $diffStr")
        assertEquals("50", diffStr)

        // Test multiplication
        val product = a.mul(b)
        val productStr = product.toDecimalString()
        println("[DEBUG_LOG] Product decimal string: $productStr")
        assertEquals("5000", productStr)

        // Test division
        val quotient = a.div(b)
        val quotientStr = quotient.toDecimalString()
        println("[DEBUG_LOG] Quotient decimal string: $quotientStr")
        assertEquals("2", quotientStr)
    }

    @Test
    fun testModuloOperation() {
        val a = MegaInteger.fromValue("100")
        val b = MegaInteger.fromValue("30")

        // Test modulo
        val remainder = a.mod(b)
        val remainderStr = remainder.toDecimalString()
        println("[DEBUG_LOG] Remainder decimal string: $remainderStr")
        assertEquals("10", remainderStr)

        // Test with zero remainder
        val c = MegaInteger.fromValue("100")
        val d = MegaInteger.fromValue("25")
        val zeroRemainder = c.mod(d)
        assertEquals("0", zeroRemainder.toDecimalString())
    }

    @Test
    fun testPowerOperation() {
        val base = MegaInteger.fromValue("2")
        val exponent = MegaInteger.fromValue("10")

        // Test power
        val result = base.pow(exponent)
        val resultStr = result.toDecimalString()
        println("[DEBUG_LOG] Power result decimal string: $resultStr")
        assertEquals("1024", resultStr)

        // Test with exponent 0
        val zeroExp = MegaInteger.fromValue("0")
        val oneResult = base.pow(zeroExp)
        assertEquals("1", oneResult.toDecimalString())

        // Test with exponent 1
        val oneExp = MegaInteger.fromValue("1")
        val baseResult = base.pow(oneExp)
        assertEquals("2", baseResult.toDecimalString())
    }

    @Test
    fun testSqrtOperation() {
        val a = MegaInteger.fromValue("100")

        // Test square root
        val sqrt = a.sqrt() as MegaInteger
        val sqrtStr = sqrt.toDecimalString()
        println("[DEBUG_LOG] Square root decimal string: $sqrtStr")
        assertEquals("10", sqrtStr)

        // Test with non-perfect square
        val b = MegaInteger.fromValue("10")
        val sqrtB = b.sqrt() as MegaInteger
        assertEquals("3", sqrtB.toDecimalString()) // Integer sqrt of 10 is 3
    }

    @Test
    fun testConversionToMegaFloat() {
        val integer = MegaInteger.fromValue("12345")

        // Convert to MegaFloat
        val float = MegaFloat(integer)

        // Verify that the mantissa is preserved
        assertEquals(integer.mantissa.size, float.mantissa.size)
        for (i in integer.mantissa.indices) {
            assertEquals(integer.mantissa[i], float.mantissa[i])
        }

        // Verify that the sign is preserved
        assertEquals(integer.negative, float.negative)

        // Verify that isFloat is true for MegaFloat
        assertTrue(float.isFloat)

        // Verify that exponent is zero
        assertEquals(1, float.exponent.mantissa.size)
        assertEquals(0, float.exponent.mantissa[0])
    }

    @Test
    fun testFromValueMethod() {
        // Test fromValue with Int
        val fromInt = MegaInteger.fromValue(42)
        assertEquals("42", fromInt.toDecimalString())

        // Test fromValue with negative Int
        val fromNegativeInt = MegaInteger.fromValue(-123)
        assertEquals("-123", fromNegativeInt.toDecimalString())

        // Test fromValue with String
        val fromString = MegaInteger.fromValue("9876")
        assertEquals("9876", fromString.toDecimalString())

        // Test fromValue with MegaNumber
        val megaNumber = MegaNumber.fromDecimalString("5555")
        val fromMegaNumber = MegaInteger.fromValue(megaNumber)
        assertEquals("5555", fromMegaNumber.toDecimalString())
    }
}
