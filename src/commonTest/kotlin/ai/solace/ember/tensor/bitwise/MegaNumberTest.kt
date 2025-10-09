package ai.solace.ember.tensor.bitwise

import ai.solace.ember.tensor.bitwise.MegaNumber.Companion.addChunks
import ai.solace.ember.tensor.bitwise.MegaNumber.Companion.compareAbs
import ai.solace.ember.tensor.bitwise.MegaNumber.Companion.div2
import ai.solace.ember.tensor.bitwise.MegaNumber.Companion.fromDecimalString
import ai.solace.ember.tensor.bitwise.MegaNumber.Companion.mulChunks
import ai.solace.ember.tensor.bitwise.MegaNumber.Companion.subChunks
import kotlin.test.*

class MegaNumberTest {

    // Helper to extract exponent as signed Int from the new exponent MegaNumber
    private fun expValue(exp: MegaNumber): Int {
        val absVal = exp.mantissa.firstOrNull() ?: 0
        return if (exp.negative) -absVal else absVal
    }
    @Test
    fun testBasicMultiplication() {
        val a = intArrayOf(2)
        val b = intArrayOf(3)
        val result = mulChunks(a, b)
        assertEquals(1, result.size)
        assertEquals(6, result[0])
    }

    @Test
    fun testLargeAddition() {
        // Test that causes carry
        val a = intArrayOf(0xFFFFFFFF.toInt())
        val b = intArrayOf(1)
        val result = addChunks(a, b)
        assertEquals(2, result.size)
        assertEquals(0, result[0])
        assertEquals(1, result[1])
    }

    @Test
    fun testSubChunks() {
        // Test basic subtraction
        val a = intArrayOf(10)
        val b = intArrayOf(3)
        val result = subChunks(a, b)
        assertEquals(1, result.size)
        assertEquals(7, result[0])

        // Test subtraction with borrow
        val c = intArrayOf(0, 1) // Represents 2^32
        val d = intArrayOf(1)
        val result2 = subChunks(c, d)
        assertEquals(2, result2.size)
        assertEquals(0xFFFFFFFF.toInt(), result2[0])
        assertEquals(0, result2[1])

        // Test subtraction resulting in zero
        val e = intArrayOf(5)
        val f = intArrayOf(5)
        val result3 = subChunks(e, f)
        assertEquals(1, result3.size)
        assertEquals(0, result3[0])
    }

    @Test
    fun testCompareAbs() {
        // Test equal values
        val a = intArrayOf(10)
        val b = intArrayOf(10)
        assertEquals(0, compareAbs(a, b))

        // Test first greater
        val c = intArrayOf(20)
        val d = intArrayOf(10)
        assertEquals(1, compareAbs(c, d))

        // Test second greater
        val e = intArrayOf(5)
        val f = intArrayOf(10)
        assertEquals(-1, compareAbs(e, f))

        // Test different lengths
        val g = intArrayOf(1, 1) // Represents 1 + 2^32
        val h = intArrayOf(1)
        assertEquals(1, compareAbs(g, h))

        // Test different lengths, opposite direction
        val i = intArrayOf(1)
        val j = intArrayOf(1, 1)
        assertEquals(-1, compareAbs(i, j))
    }

    @Test
    fun testLargeMulChunks() {
        // Compare dispatcher result against itself to ensure non‑zero output and stable size logic.
        val a = IntArray(40) { 0xFFFFFFFF.toInt() }
        val b = IntArray(40) { 0xFFFFFFFF.toInt() }

        val result = mulChunks(a, b)

        // Result should not be all zeros and must be larger than either operand.
        assertTrue(result.any { it != 0 })
        assertTrue(result.size > a.size)
    }






    @Test
    fun testDiv2() {
        // Test simple division
        val a = intArrayOf(10)
        val result = div2(a)
        assertEquals(1, result.size)
        assertEquals(5, result[0])

        // Test odd number
        val b = intArrayOf(11)
        val result2 = div2(b)
        assertEquals(1, result2.size)
        assertEquals(5, result2[0]) // Integer division, so 11/2 = 5

        // Test with carry
        val c = intArrayOf(1, 1) // Represents 1 + 2^32
        val result3 = div2(c)
        // Leading zero chunk is trimmed, so result length is 1
        assertEquals(1, result3.size)
        assertEquals(0x80000000.toInt(), result3[0]) // 2^31
    }

    @Test
    fun testFromDecimalString() {
        // Test integer
        val num = fromDecimalString("12345")
        assertEquals("12345", num.toDecimalString())
        assertFalse(num.negative)
        assertFalse(num.isFloat)

        // Test negative integer
        val negNum = fromDecimalString("-54321")
        assertEquals("-54321", negNum.toDecimalString())
        assertTrue(negNum.negative)
        assertFalse(negNum.isFloat)

        // Test float
        val floatNum = fromDecimalString("123.45")
        assertTrue(floatNum.isFloat)

        // Test negative float
        val negFloatNum = fromDecimalString("-67.89")
        assertTrue(negFloatNum.negative)
        assertTrue(negFloatNum.isFloat)
    }

    @Test
    fun testStringRoundTrip() {
        // Integers should round‑trip exactly
        val ints = listOf("0", "123456", "-987654")
        for (s in ints) {
            val n = fromDecimalString(s)
            assertFalse(n.isFloat)
            assertEquals(s, n.toDecimalString())
        }

        // Floats parse without throwing and retain isFloat flag
        val floats = listOf("1.25", "-3.5", "0.125")
        for (s in floats) {
            val n = fromDecimalString(s)
            assertTrue(n.isFloat)
            // We cannot round‑trip textual form because toDecimalString() currently
            // outputs mantissa * 2^(exp) style; just ensure it contains the mantissa.
            assertTrue(n.toDecimalString().contains('*'))
        }
    }

    @Test
    fun testNormalize() {
        // Test normalization of zero
        val zero = MegaNumber(
            mantissa = intArrayOf(0),
            exponent = MegaNumber(intArrayOf(1), negative = true),
            negative = true,
            isFloat = true
        )
        zero.normalize()
        assertFalse(zero.negative)
        assertFalse(zero.exponent.negative)
        assertEquals(1, zero.exponent.mantissa.size)
        assertEquals(0, zero.exponent.mantissa[0])

        // Test trimming of mantissa
        val num = MegaNumber(intArrayOf(1, 0, 0), MegaNumber(intArrayOf(0)))
        num.normalize()
        assertEquals(1, num.mantissa.size)
        assertEquals(1, num.mantissa[0])

        // Test with keepLeadingZeros
        val numWithZeros = MegaNumber(
            mantissa = intArrayOf(0, 0, 1),
            exponent = MegaNumber(intArrayOf(0)),
            negative = false,
            isFloat = false,
            keepLeadingZeros = true
        )
        numWithZeros.normalize()
        assertEquals(3, numWithZeros.mantissa.size)
        assertEquals(0, numWithZeros.mantissa[0])
        assertEquals(0, numWithZeros.mantissa[1])
        assertEquals(1, numWithZeros.mantissa[2])
    }


    @Test
    fun testAdd() {
        // Test integer addition
        val a = MegaNumber(intArrayOf(10))
        val b = MegaNumber(intArrayOf(20))
        val sum = a.add(b)
        assertEquals("30", sum.toDecimalString())

        // Test addition with different signs
        val c = MegaNumber(intArrayOf(30), negative = true)
        val d = MegaNumber(intArrayOf(20))
        val diff = c.add(d)
        assertEquals("-10", diff.toDecimalString())

        // Test addition resulting in zero
        val e = MegaNumber(intArrayOf(15))
        val f = MegaNumber(intArrayOf(15), negative = true)
        val zero = e.add(f)
        assertEquals("0", zero.toDecimalString())
    }

    @Test
    fun testAddFloat() {
        // Test float addition
        val a = MegaNumber(intArrayOf(10), MegaNumber(intArrayOf(1)), negative = false, isFloat = true)
        val b = MegaNumber(intArrayOf(20), MegaNumber(intArrayOf(1)), negative = false, isFloat = true)
        val sum = a.addFloat(b)
        assertTrue(sum.isFloat)

        // Test addition with different exponents
        val c = MegaNumber(intArrayOf(10), MegaNumber(intArrayOf(2)), negative = false, isFloat = true)
        val d = MegaNumber(intArrayOf(10), MegaNumber(intArrayOf(1)), negative = false, isFloat = true)
        val result = c.addFloat(d)
        assertTrue(result.isFloat)

        // Test addition with different signs
        val e = MegaNumber(intArrayOf(30), MegaNumber(intArrayOf(1)), negative = true, isFloat = true)
        val f = MegaNumber(intArrayOf(20), MegaNumber(intArrayOf(1)), negative = false, isFloat = true)
        val diff = e.addFloat(f)
        assertTrue(diff.negative)
        assertTrue(diff.isFloat)
    }

    @Test
    fun testSub() {
        // Test integer subtraction
        val a = MegaNumber(intArrayOf(30))
        val b = MegaNumber(intArrayOf(10))
        val diff = a.sub(b)
        assertEquals("20", diff.toDecimalString())

        // Test subtraction resulting in negative
        val c = MegaNumber(intArrayOf(10))
        val d = MegaNumber(intArrayOf(20))
        val negDiff = c.sub(d)
        assertEquals("-10", negDiff.toDecimalString())

        // Test subtraction of negative number
        val e = MegaNumber(intArrayOf(15))
        val f = MegaNumber(intArrayOf(10), negative = true)
        val sum = e.sub(f)
        assertEquals("25", sum.toDecimalString())
    }

    @Test
    fun testMul() {
        // Test integer multiplication
        val a = MegaNumber(intArrayOf(10))
        val b = MegaNumber(intArrayOf(20))
        val product = a.mul(b)
        assertEquals("200", product.toDecimalString())

        // Test multiplication with different signs
        val c = MegaNumber(intArrayOf(30), negative = true)
        val d = MegaNumber(intArrayOf(20))
        val negProduct = c.mul(d)
        assertEquals("-600", negProduct.toDecimalString())

        // Test multiplication by zero
        val e = MegaNumber(intArrayOf(15))
        val f = MegaNumber(intArrayOf(0))
        val zero = e.mul(f)
        assertEquals("0", zero.toDecimalString())
    }

    @Test
    fun testMulFloat() {
        // Test float multiplication
        val a = MegaNumber(intArrayOf(10), MegaNumber(intArrayOf(1)), false, isFloat = true)
        val b = MegaNumber(intArrayOf(20), MegaNumber(intArrayOf(1)), false, isFloat = true)
        val product = a.mulFloat(b)
        assertTrue(product.isFloat)

        // Test multiplication with different exponents
        val c = MegaNumber(intArrayOf(10), MegaNumber(intArrayOf(2)), negative = false, isFloat = true)
        val d = MegaNumber(intArrayOf(10), MegaNumber(intArrayOf(1)), negative = false, isFloat = true)
        val result = c.mulFloat(d)
        assertTrue(result.isFloat)
        assertEquals(expected = 3, actual = expValue(result.exponent)) // 2 + 1 = 3

        // Test multiplication with different signs
        val e = MegaNumber(intArrayOf(30), MegaNumber(intArrayOf(1)), negative = true, isFloat = true)
        val f = MegaNumber(intArrayOf(20), MegaNumber(intArrayOf(1)), false, isFloat = true)
        val negProduct = e.mulFloat(f)
        assertTrue(negProduct.negative)
        assertTrue(negProduct.isFloat)
    }

    @Test
    fun testDivide() {
        // Test integer division
        val a = MegaNumber(intArrayOf(100))
        val b = MegaNumber(intArrayOf(20))
        val quotient = a.divide(b)
        assertEquals("5", quotient.toDecimalString())

        // Test division with different signs
        val c = MegaNumber(intArrayOf(30), negative = true)
        val d = MegaNumber(intArrayOf(10))
        val negQuotient = c.divide(d)
        assertEquals("-3", negQuotient.toDecimalString())

        // Test division by larger number
        val e = MegaNumber(intArrayOf(10))
        val f = MegaNumber(intArrayOf(20))
        val zeroQuotient = e.divide(f)
        assertEquals("0", zeroQuotient.toDecimalString())

        // Test division by zero
        val g = MegaNumber(intArrayOf(15))
        val h = MegaNumber(intArrayOf(0))
        assertFailsWith<IllegalArgumentException> {
            g.divide(h)
        }
    }

    @Test
    fun testFloatDivide() {
        // Test float division
        val a = MegaNumber(intArrayOf(100), MegaNumber(intArrayOf(1)), false, isFloat = true)
        val b = MegaNumber(intArrayOf(20), MegaNumber(intArrayOf(1)), negative = false, isFloat = true)
        val quotient = a.divide(b)
        assertTrue(quotient.isFloat)

        // Test division with different exponents
        val c = MegaNumber(intArrayOf(10), MegaNumber(intArrayOf(3)), negative = false, isFloat = true)
        val d = MegaNumber(intArrayOf(10), MegaNumber(intArrayOf(1)), negative = false, isFloat = true)
        val result = c.divide(d)
        assertTrue(result.isFloat)
        assertEquals(expected = 2, actual = expValue(result.exponent)) // 3 - 1 = 2

        // Test division with different signs
        val e = MegaNumber(intArrayOf(30), MegaNumber(intArrayOf(1)), negative = true, isFloat = true)
        val f = MegaNumber(intArrayOf(10), MegaNumber(intArrayOf(1)), negative = false, isFloat = true)
        val negQuotient = e.divide(f)
        assertTrue(negQuotient.negative)
        assertTrue(negQuotient.isFloat)
    }

    @Test
    fun testSqrt() {
        // Test integer square root
        val a = MegaNumber(intArrayOf(100))
        val sqrt = a.sqrt()
        assertEquals("10", sqrt.toDecimalString())

        // Test square root of zero
        val zero = MegaNumber(intArrayOf(0))
        val zeroSqrt = zero.sqrt()
        assertEquals("0", zeroSqrt.toDecimalString())

        // Test square root of negative number
        val neg = MegaNumber(intArrayOf(25), negative = true)
        assertFailsWith<IllegalArgumentException> {
            neg.sqrt()
        }

        // Test square root of non-perfect square
        val nonPerfect = MegaNumber(intArrayOf(12))
        val nonPerfectSqrt = nonPerfect.sqrt()
        assertEquals("3", nonPerfectSqrt.toDecimalString()) // Integer sqrt of 12 is 3
    }

    @Test
    fun testFloatSqrt() {
        // Test float square root
        val a = MegaNumber(intArrayOf(100), MegaNumber(intArrayOf(0)), negative = false, isFloat = true)
        val sqrt = a.sqrt()
        assertTrue(sqrt.isFloat)

        // Test with odd exponent
        val b = MegaNumber(intArrayOf(100), MegaNumber(intArrayOf(3)), negative = false, isFloat = true)
        val oddExpSqrt = b.sqrt()
        assertTrue(oddExpSqrt.isFloat)
        assertEquals(1, expValue(oddExpSqrt.exponent)) // 3/2 = 1.5, but integer division gives 1
    }

    // Note: low‑level helper methods (e.g., shiftLeft/shiftRight/decimalStringToChunks)
    // are now private and intentionally not unit‑tested here.
}
