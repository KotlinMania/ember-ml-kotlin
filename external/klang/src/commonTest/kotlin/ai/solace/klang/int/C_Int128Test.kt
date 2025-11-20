package ai.solace.klang.int

import ai.solace.klang.mem.KMalloc
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test suite for C_Int128: C-compatible signed 128-bit integer type.
 *
 * Tests two's complement arithmetic, sign handling, and edge cases.
 */
class C_Int128Test {
    
    @BeforeTest
    fun setup() {
        KMalloc.init(1024 * 1024)
    }

    @Test
    fun testZeroAndOne() {
        val zero = C_Int128.zero()
        val one = C_Int128.one()
        
        assertEquals("0x0", zero.toHexString())
        assertEquals("0x1", one.toHexString())
        assertFalse(zero.isNegative())
        assertFalse(one.isNegative())
    }

    @Test
    fun testPositiveValues() {
        val pos = C_Int128.fromLong(12345L)
        
        assertEquals("0x3039", pos.toHexString())
        assertFalse(pos.isNegative())
    }

    @Test
    fun testNegativeValues() {
        val neg = C_Int128.fromLong(-100L)
        
        assertTrue(neg.isNegative())
    }

    @Test
    fun testNegation() {
        val pos = C_Int128.fromLong(100L)
        val neg = pos.negate()
        
        assertTrue(neg.isNegative())
        
        val posAgain = neg.negate()
        assertFalse(posAgain.isNegative())
        assertEquals(pos, posAgain)
    }

    @Test
    fun testAdditionPositive() {
        val a = C_Int128.fromLong(100L)
        val b = C_Int128.fromLong(200L)
        val sum = a + b
        
        assertEquals("0x12c", sum.toHexString())  // 300 in hex
        assertFalse(sum.isNegative())
    }

    @Test
    fun testAdditionNegative() {
        val a = C_Int128.fromLong(-100L)
        val b = C_Int128.fromLong(200L)
        val sum = a + b
        
        assertEquals("0x64", sum.toHexString())  // 100 in hex
        assertFalse(sum.isNegative())
    }

    @Test
    fun testSubtraction() {
        val a = C_Int128.fromLong(500L)
        val b = C_Int128.fromLong(200L)
        val diff = a - b
        
        assertEquals("0x12c", diff.toHexString())  // 300 in hex
    }

    @Test
    fun testSubtractionToNegative() {
        val a = C_Int128.fromLong(100L)
        val b = C_Int128.fromLong(200L)
        val diff = a - b
        
        assertTrue(diff.isNegative())
    }

    @Test
    fun testAbsoluteValue() {
        val neg = C_Int128.fromLong(-12345L)
        val abs = neg.abs()
        
        assertEquals("0x3039", abs.toHexString())
        assertFalse(abs.isNegative())
    }

    @Test
    fun testComparison() {
        val neg = C_Int128.fromLong(-100L)
        val zero = C_Int128.zero()
        val pos = C_Int128.fromLong(100L)
        
        assertTrue(neg < zero)
        assertTrue(zero < pos)
        assertTrue(neg < pos)
    }

    @Test
    fun testEquality() {
        val a = C_Int128.fromLong(-12345L)
        val b = C_Int128.fromLong(-12345L)
        val c = C_Int128.fromLong(12345L)
        
        assertEquals(a, b)
        assertTrue(a != c)
    }

    @Test
    fun testShiftLeft() {
        val one = C_Int128.one()
        val shifted = one.shiftLeft(8)
        
        assertEquals("0x100", shifted.toHexString())
    }

    @Test
    fun testArithmeticShiftRight() {
        val neg = C_Int128.fromLong(-256L)
        val shifted = neg.shiftRight(2)
        
        // Should preserve sign
        assertTrue(shifted.isNegative())
    }
}
