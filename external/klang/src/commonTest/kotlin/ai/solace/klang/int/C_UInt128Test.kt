package ai.solace.klang.int

import ai.solace.klang.mem.KMalloc
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Test suite for C_UInt128: C-compatible unsigned 128-bit integer type.
 *
 * Tests zero-copy heap operations, arithmetic, comparisons, and edge cases.
 */
class C_UInt128Test {
    
    @BeforeTest
    fun setup() {
        KMalloc.init(1024 * 1024)
    }

    @Test
    fun testZeroAndOne() {
        val zero = C_UInt128.zero()
        val one = C_UInt128.one()
        
        assertEquals("0x0", zero.toHexString())
        assertEquals("0x1", one.toHexString())
        assertTrue(zero < one)
    }

    @Test
    fun testFromULong() {
        val x = C_UInt128.fromULong(255uL)
        assertEquals("0xff", x.toHexString())
        
        val y = C_UInt128.fromULong(ULong.MAX_VALUE)
        assertEquals("0xffffffffffffffff", y.toHexString())
    }

    @Test
    fun testAddition() {
        val a = C_UInt128.fromULong(100uL)
        val b = C_UInt128.fromULong(200uL)
        val sum = a + b
        
        assertEquals("0x12c", sum.toHexString())  // 300 in hex
    }

    @Test
    fun testSubtraction() {
        val a = C_UInt128.fromULong(500uL)
        val b = C_UInt128.fromULong(200uL)
        val diff = a - b
        
        assertEquals("0x12c", diff.toHexString())  // 300 in hex
    }

    @Test
    fun testSubtractionUnderflow() {
        val a = C_UInt128.fromULong(100uL)
        val b = C_UInt128.fromULong(200uL)
        
        assertFailsWith<IllegalArgumentException> {
            a - b  // Should throw
        }
    }

    @Test
    fun testShiftLeft() {
        val one = C_UInt128.one()
        val shifted = one.shiftLeft(8)
        
        assertEquals("0x100", shifted.toHexString())  // 256 in hex
        
        val one2 = C_UInt128.one()
        val shifted64 = one2.shiftLeft(64)
        assertEquals("0x10000000000000000", shifted64.toHexString())
    }

    @Test
    fun testShiftRight() {
        val x = C_UInt128.fromULong(256uL)
        val shifted = x.shiftRight(8)
        
        assertEquals("0x1", shifted.toHexString())
    }

    @Test
    fun testComparison() {
        val a = C_UInt128.fromULong(100uL)
        val b = C_UInt128.fromULong(200uL)
        val c = C_UInt128.fromULong(100uL)
        
        assertTrue(a < b)
        assertTrue(b > a)
        assertEquals(0, a.compareTo(c))
    }

    @Test
    fun testEquality() {
        val a = C_UInt128.fromULong(12345uL)
        val b = C_UInt128.fromULong(12345uL)
        val c = C_UInt128.fromULong(54321uL)
        
        assertEquals(a, b)
        assertTrue(a != c)
    }

    @Test
    fun test64BitOverflow() {
        val max64 = C_UInt128.fromULong(ULong.MAX_VALUE)
        val one = C_UInt128.one()
        val overflow = max64 + one
        
        // Should be 2^64
        assertEquals("0x10000000000000000", overflow.toHexString())
    }

    @Test
    fun testAdditionOverflow() {
        // Create maximum 128-bit value (all bits set)
        val max128a = C_UInt128.fromULong(ULong.MAX_VALUE)
        val max128b = max128a.shiftLeft(64)  // Top 64 bits set
        val max128c = C_UInt128.fromULong(ULong.MAX_VALUE)
        val max128 = max128b + max128c  // All 128 bits set: 2^128 - 1
        
        val one = C_UInt128.one()
        
        // Adding 1 to max value should overflow
        assertFailsWith<IllegalArgumentException> {
            max128 + one
        }
    }
}
