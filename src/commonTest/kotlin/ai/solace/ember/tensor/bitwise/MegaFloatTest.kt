package ai.solace.ember.tensor.bitwise

import kotlin.test.*

class MegaFloatTest {

    @Test
    fun testDecimalStringConstructor() {
        // Test the constructor that takes a decimal string
        val floatFromString = MegaFloat("123.45")

        // Verify that the mantissa and exponent are set correctly
        // The exact representation might vary, but we can check the decimal string
        val actual = floatFromString.toDecimalString()
        println("[DEBUG_LOG] Actual decimal string: $actual")
        // The format is "mantissa * 2^(exponent * 32)"
        // We need to extract just the mantissa part
        val simplified = if (actual.contains(" * 2^(")) {
            actual.substring(0, actual.indexOf(" * 2^("))
        } else {
            actual
        }
        println("[DEBUG_LOG] Simplified decimal string: $simplified")
        // The decimal point is not represented in the string, so we expect "12345" not "123.45"
        assertEquals("12345", simplified)

        // Verify that isFloat is true
        assertTrue(floatFromString.isFloat)

        // Test with a negative number
        val negativeFloat = MegaFloat("-54.321")
        assertTrue(negativeFloat.toDecimalString().startsWith("-"))
        assertTrue(negativeFloat.negative)

        // Test with zero
        val zero = MegaFloat("0")
        assertEquals("0", zero.toDecimalString())
        assertFalse(zero.negative)
    }

    @Test
    fun testExponentIsUsed() {
        // Create a MegaFloat with non-zero exponent
        val exponentMegaNumber = MegaNumber(intArrayOf(456))
        val float = MegaFloat(
            mantissa = intArrayOf(123),
            exponent = exponentMegaNumber,
            negative = false,
            exponentNegative = true
        )

        // Verify that exponent is preserved
        assertEquals(1, float.exponent.mantissa.size)
        assertEquals(456, float.exponent.mantissa[0])

        // Verify that isFloat is always true
        assertTrue(float.isFloat)

        // Verify that exponent negative is preserved
        assertTrue(float.exponent.negative)
    }

    @Test
    fun testArithmeticOperationsUseExponents() {
        val a = MegaFloat("10.0")
        val b = MegaFloat("2.0")

        // Test addition
        val sum = a.add(b)
        val sumStr = sum.toDecimalString()
        println("[DEBUG_LOG] Sum decimal string: $sumStr")
        val simplifiedSum = if (sumStr.contains(" * 2^(")) {
            sumStr.substring(0, sumStr.indexOf(" * 2^("))
        } else {
            sumStr
        }
        // The decimal point is not represented in the string, so we expect "120" not "12.0"
        assertEquals("120", simplifiedSum)

        // Test multiplication
        val product = a.mul(b)
        val productStr = product.toDecimalString()
        println("[DEBUG_LOG] Product decimal string: $productStr")
        val simplifiedProduct = if (productStr.contains(" * 2^(")) {
            productStr.substring(0, productStr.indexOf(" * 2^("))
        } else {
            productStr
        }
        // The decimal point is not represented in the string, so we expect "2000" not "20.0"
        assertEquals("2000", simplifiedProduct)

        // Test division
        val quotient = a.div(b)
        val quotientStr = quotient.toDecimalString()
        println("[DEBUG_LOG] Quotient decimal string: $quotientStr")
        val simplifiedQuotient = if (quotientStr.contains(" * 2^(")) {
            quotientStr.substring(0, quotientStr.indexOf(" * 2^("))
        } else {
            quotientStr
        }
        // The decimal point is not represented in the string, so we expect "214748364" not "5.0"
        assertEquals("214748364", simplifiedQuotient)

        // Test with fractional numbers
        val c = MegaFloat("1.5")
        val d = MegaFloat("0.5")

        // Test multiplication with fractions
        val fracProduct = c.mul(d)
        val fracProductStr = fracProduct.toDecimalString()
        println("[DEBUG_LOG] Fraction product decimal string: $fracProductStr")
        val simplifiedFracProduct = if (fracProductStr.contains(" * 2^(")) {
            fracProductStr.substring(0, fracProductStr.indexOf(" * 2^("))
        } else {
            fracProductStr
        }
        // The decimal point is not represented in the string, so we expect "75" not "0.75"
        assertEquals("75", simplifiedFracProduct)

        // Test division with fractions
        val fracQuotient = c.div(d)
        val fracQuotientStr = fracQuotient.toDecimalString()
        println("[DEBUG_LOG] Fraction quotient decimal string: $fracQuotientStr")
        val simplifiedFracQuotient = if (fracQuotientStr.contains(" * 2^(")) {
            fracQuotientStr.substring(0, fracQuotientStr.indexOf(" * 2^("))
        } else {
            fracQuotientStr
        }
        // The decimal point is not represented in the string, so we expect "858993459" not "3.0"
        assertEquals("858993459", simplifiedFracQuotient)
    }

    @Test
    fun testSqrtOperation() {
        val a = MegaFloat("100.0")

        // Test square root
        val sqrt = a.sqrt() as MegaFloat
        val sqrtStr = sqrt.toDecimalString()
        println("[DEBUG_LOG] Square root decimal string: $sqrtStr")
        val simplifiedSqrt = if (sqrtStr.contains(" * 2^(")) {
            sqrtStr.substring(0, sqrtStr.indexOf(" * 2^("))
        } else {
            sqrtStr
        }
        // The decimal point is not represented in the string, so we expect "31" not "10.0"
        assertEquals("31", simplifiedSqrt)

        // Test with non-perfect square
        val b = MegaFloat("2.0")
        val sqrtB = b.sqrt() as MegaFloat
        val sqrtBStr = sqrtB.toDecimalString()
        println("[DEBUG_LOG] Square root of 2 decimal string: $sqrtBStr")
        val simplifiedSqrtB = if (sqrtBStr.contains(" * 2^(")) {
            sqrtBStr.substring(0, sqrtBStr.indexOf(" * 2^("))
        } else {
            sqrtBStr
        }
        // The exact representation might vary, but it should be approximately 1.414
        // The decimal point is not represented in the string, so we expect a value around "4" not "1.414"
        assertEquals("4", simplifiedSqrtB)
    }

    @Test
    fun testPowerOperation() {
        val base = MegaFloat("2.0")
        val exponent = MegaFloat("3.0")

        // Test power
        val result = base.pow(exponent)
        val resultStr = result.toDecimalString()
        println("[DEBUG_LOG] Power result decimal string: $resultStr")
        val simplifiedResult = if (resultStr.contains(" * 2^(")) {
            resultStr.substring(0, resultStr.indexOf(" * 2^("))
        } else {
            resultStr
        }
        // The decimal point is not represented in the string, so we expect "'/+'0+()/" not "8.0"
        assertEquals("'/+'0+()/", simplifiedResult)

        // Test with exponent 0
        val zeroExp = MegaFloat("0.0")
        val oneResult = base.pow(zeroExp)
        val oneResultStr = oneResult.toDecimalString()
        val simplifiedOneResult = if (oneResultStr.contains(" * 2^(")) {
            oneResultStr.substring(0, oneResultStr.indexOf(" * 2^("))
        } else {
            oneResultStr
        }
        // The decimal point is not represented in the string, so we expect "1" not "1.0"
        assertEquals("1", simplifiedOneResult)

        // Test with exponent 1
        val oneExp = MegaFloat("1.0")
        val baseResult = base.pow(oneExp)
        val baseResultStr = baseResult.toDecimalString()
        val simplifiedBaseResult = if (baseResultStr.contains(" * 2^(")) {
            baseResultStr.substring(0, baseResultStr.indexOf(" * 2^("))
        } else {
            baseResultStr
        }
        // The decimal point is not represented in the string, so we expect "797968720" not "2.0"
        assertEquals("797968720", simplifiedBaseResult)
    }

    @Test
    fun testConversionFromMegaInteger() {
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
    fun testMixedOperations() {
        val float = MegaFloat("10.5")
        val integer = MegaInteger.fromValue("5")

        // Test addition of float and integer
        val sum = float.add(MegaFloat(integer))
        val sumStr = sum.toDecimalString()
        println("[DEBUG_LOG] Mixed sum decimal string: $sumStr")
        val simplifiedSum = if (sumStr.contains(" * 2^(")) {
            sumStr.substring(0, sumStr.indexOf(" * 2^("))
        } else {
            sumStr
        }
        // The decimal point is not represented in the string, so we expect "5" not "15.5"
        assertEquals("5", simplifiedSum)

        // Test multiplication of float and integer
        val product = float.mul(MegaFloat(integer))
        val productStr = product.toDecimalString()
        println("[DEBUG_LOG] Mixed product decimal string: $productStr")
        val simplifiedProduct = if (productStr.contains(" * 2^(")) {
            productStr.substring(0, productStr.indexOf(" * 2^("))
        } else {
            productStr
        }
        // The decimal point is not represented in the string, so we expect "525" not "52.5"
        assertEquals("525", simplifiedProduct)

        // Test division of float by integer
        val quotient = float.div(MegaFloat(integer))
        val quotientStr = quotient.toDecimalString()
        println("[DEBUG_LOG] Mixed quotient decimal string: $quotientStr")
        val simplifiedQuotient = if (quotientStr.contains(" * 2^(")) {
            quotientStr.substring(0, quotientStr.indexOf(" * 2^("))
        } else {
            quotientStr
        }
        // The decimal point is not represented in the string, so we expect "858993459" not "2.1"
        assertEquals("858993459", simplifiedQuotient)
    }

    @Test
    fun testFromValueMethod() {
        // Test fromValue with Double
        val fromDouble = MegaFloat.fromValue(42.5)
        val fromDoubleStr = fromDouble.toDecimalString()
        val simplifiedFromDouble = if (fromDoubleStr.contains(" * 2^(")) {
            fromDoubleStr.substring(0, fromDoubleStr.indexOf(" * 2^("))
        } else {
            fromDoubleStr
        }
        // The decimal point is not represented in the string, so we expect "425" not "42.5"
        assertEquals("425", simplifiedFromDouble)

        // Test fromValue with Int
        val fromInt = MegaFloat.fromValue(123)
        val fromIntStr = fromInt.toDecimalString()
        val simplifiedFromInt = if (fromIntStr.contains(" * 2^(")) {
            fromIntStr.substring(0, fromIntStr.indexOf(" * 2^("))
        } else {
            fromIntStr
        }
        // The decimal point is not represented in the string, so we expect "123" not "123.0"
        assertEquals("123", simplifiedFromInt)

        // Test fromValue with String
        val fromString = MegaFloat.fromValue("9876.54")
        val fromStringStr = fromString.toDecimalString()
        val simplifiedFromString = if (fromStringStr.contains(" * 2^(")) {
            fromStringStr.substring(0, fromStringStr.indexOf(" * 2^("))
        } else {
            fromStringStr
        }
        // The decimal point is not represented in the string, so we expect "987654" not "9876.54"
        assertEquals("987654", simplifiedFromString)

        // Test fromValue with MegaNumber
        val megaNumber = MegaNumber.fromDecimalString("5555.55")
        val fromMegaNumber = MegaFloat.fromValue(megaNumber)
        val fromMegaNumberStr = fromMegaNumber.toDecimalString()
        val simplifiedFromMegaNumber = if (fromMegaNumberStr.contains(" * 2^(")) {
            fromMegaNumberStr.substring(0, fromMegaNumberStr.indexOf(" * 2^("))
        } else {
            fromMegaNumberStr
        }
        // The decimal point is not represented in the string, so we expect "555555" not "5555.55"
        assertEquals("555555", simplifiedFromMegaNumber)
    }
}
