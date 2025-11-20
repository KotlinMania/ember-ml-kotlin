package ai.solace.klang.bitwise

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for type-preserving shift operations in BitShiftEngine.
 *
 * These tests verify that shift operations correctly maintain type information
 * when working with Byte, Short, and Int types, which is essential for
 * ByteArray and heap operations.
 */
class BitShiftEngineTypePreservingTest {
    
    // ========================================================================
    // Byte Type Tests (8-bit)
    // ========================================================================
    
    @Test
    fun testLeftShiftByte_Native() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 8)
        
        // Basic shift
        assertEquals(0x24.toByte(), engine.leftShiftByte(0x12, 1))
        assertEquals(0x48.toByte(), engine.leftShiftByte(0x12, 2))
        assertEquals(0x20.toByte(), engine.leftShiftByte(0x12, 4))
        
        // Overflow wrap
        assertEquals(0xE0.toByte(), engine.leftShiftByte(0xF0, 1).toUByte().toByte())
        assertEquals(0x00.toByte(), engine.leftShiftByte(0xFF, 8))
        
        // Zero shift
        assertEquals(0x42.toByte(), engine.leftShiftByte(0x42, 0))
    }
    
    @Test
    fun testLeftShiftByte_Arithmetic() {
        val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 8)
        
        // Basic shift
        assertEquals(0x24.toByte(), engine.leftShiftByte(0x12, 1))
        assertEquals(0x48.toByte(), engine.leftShiftByte(0x12, 2))
        assertEquals(0x20.toByte(), engine.leftShiftByte(0x12, 4))
        
        // Overflow wrap
        assertEquals(0xE0.toByte(), engine.leftShiftByte(0xF0, 1).toUByte().toByte())
        assertEquals(0x00.toByte(), engine.leftShiftByte(0xFF, 8))
        
        // Zero shift
        assertEquals(0x42.toByte(), engine.leftShiftByte(0x42, 0))
    }
    
    @Test
    fun testRightShiftByte_Native() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 8)
        
        // Basic shift
        assertEquals(0x09.toByte(), engine.rightShiftByte(0x12, 1))
        assertEquals(0x04.toByte(), engine.rightShiftByte(0x12, 2))
        assertEquals(0x01.toByte(), engine.rightShiftByte(0x12, 4))
        
        // Shift to zero
        assertEquals(0x00.toByte(), engine.rightShiftByte(0x12, 8))
        
        // Zero shift
        assertEquals(0x42.toByte(), engine.rightShiftByte(0x42, 0))
    }
    
    @Test
    fun testRightShiftByte_Arithmetic() {
        val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 8)
        
        // Basic shift
        assertEquals(0x09.toByte(), engine.rightShiftByte(0x12, 1))
        assertEquals(0x04.toByte(), engine.rightShiftByte(0x12, 2))
        assertEquals(0x01.toByte(), engine.rightShiftByte(0x12, 4))
        
        // Shift to zero
        assertEquals(0x00.toByte(), engine.rightShiftByte(0x12, 8))
        
        // Zero shift
        assertEquals(0x42.toByte(), engine.rightShiftByte(0x42, 0))
    }
    
    @Test
    fun testUnsignedRightShiftByte_Native() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 8)
        
        // Basic shift
        assertEquals(0x42.toByte(), engine.unsignedRightShiftByte(0x84, 1))
        assertEquals(0x21.toByte(), engine.unsignedRightShiftByte(0x84, 2))
        
        // High bit set
        assertEquals(0x7F.toByte(), engine.unsignedRightShiftByte(0xFF, 1))
        assertEquals(0x3F.toByte(), engine.unsignedRightShiftByte(0xFF, 2))
        
        // Zero shift
        assertEquals(0x84.toByte(), engine.unsignedRightShiftByte(0x84, 0))
    }
    
    @Test
    fun testUnsignedRightShiftByte_Arithmetic() {
        val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 8)
        
        // Basic shift
        assertEquals(0x42.toByte(), engine.unsignedRightShiftByte(0x84, 1))
        assertEquals(0x21.toByte(), engine.unsignedRightShiftByte(0x84, 2))
        
        // High bit set
        assertEquals(0x7F.toByte(), engine.unsignedRightShiftByte(0xFF, 1))
        assertEquals(0x3F.toByte(), engine.unsignedRightShiftByte(0xFF, 2))
        
        // Zero shift
        assertEquals(0x84.toByte(), engine.unsignedRightShiftByte(0x84, 0))
    }
    
    // ========================================================================
    // Short Type Tests (16-bit)
    // ========================================================================
    
    @Test
    fun testLeftShiftShort_Native() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 16)
        
        // Basic shift
        assertEquals(0x2468.toShort(), engine.leftShiftShort(0x1234, 1))
        assertEquals(0x48D0.toShort(), engine.leftShiftShort(0x1234, 2))
        assertEquals(0x2340.toShort(), engine.leftShiftShort(0x1234, 4))
        
        // Overflow wrap
        assertEquals(0xE000.toShort(), engine.leftShiftShort(0xF000, 1).toUShort().toShort())
        assertEquals(0x0000.toShort(), engine.leftShiftShort(0xFFFF, 16))
        
        // Zero shift
        assertEquals(0x4242.toShort(), engine.leftShiftShort(0x4242, 0))
    }
    
    @Test
    fun testLeftShiftShort_Arithmetic() {
        val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 16)
        
        // Basic shift
        assertEquals(0x2468.toShort(), engine.leftShiftShort(0x1234, 1))
        assertEquals(0x48D0.toShort(), engine.leftShiftShort(0x1234, 2))
        assertEquals(0x2340.toShort(), engine.leftShiftShort(0x1234, 4))
        
        // Overflow wrap
        assertEquals(0xE000.toShort(), engine.leftShiftShort(0xF000, 1).toUShort().toShort())
        assertEquals(0x0000.toShort(), engine.leftShiftShort(0xFFFF, 16))
        
        // Zero shift
        assertEquals(0x4242.toShort(), engine.leftShiftShort(0x4242, 0))
    }
    
    @Test
    fun testRightShiftShort_Native() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 16)
        
        // Basic shift
        assertEquals(0x091A.toShort(), engine.rightShiftShort(0x1234, 1))
        assertEquals(0x048D.toShort(), engine.rightShiftShort(0x1234, 2))
        assertEquals(0x0123.toShort(), engine.rightShiftShort(0x1234, 4))
        
        // Shift to zero
        assertEquals(0x0000.toShort(), engine.rightShiftShort(0x1234, 16))
        
        // Zero shift
        assertEquals(0x4242.toShort(), engine.rightShiftShort(0x4242, 0))
    }
    
    @Test
    fun testRightShiftShort_Arithmetic() {
        val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 16)
        
        // Basic shift
        assertEquals(0x091A.toShort(), engine.rightShiftShort(0x1234, 1))
        assertEquals(0x048D.toShort(), engine.rightShiftShort(0x1234, 2))
        assertEquals(0x0123.toShort(), engine.rightShiftShort(0x1234, 4))
        
        // Shift to zero
        assertEquals(0x0000.toShort(), engine.rightShiftShort(0x1234, 16))
        
        // Zero shift
        assertEquals(0x4242.toShort(), engine.rightShiftShort(0x4242, 0))
    }
    
    @Test
    fun testUnsignedRightShiftShort_Native() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 16)
        
        // Basic shift
        assertEquals(0x4210.toShort(), engine.unsignedRightShiftShort(0x8421, 1))
        assertEquals(0x2108.toShort(), engine.unsignedRightShiftShort(0x8421, 2))
        assertEquals(0x0842.toShort(), engine.unsignedRightShiftShort(0x8421, 4))
        
        // High bit set
        assertEquals(0x7FFF.toShort(), engine.unsignedRightShiftShort(0xFFFF, 1))
        assertEquals(0x3FFF.toShort(), engine.unsignedRightShiftShort(0xFFFF, 2))
        
        // Zero shift
        assertEquals(0x8421.toShort(), engine.unsignedRightShiftShort(0x8421, 0))
    }
    
    @Test
    fun testUnsignedRightShiftShort_Arithmetic() {
        val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 16)
        
        // Basic shift
        assertEquals(0x4210.toShort(), engine.unsignedRightShiftShort(0x8421, 1))
        assertEquals(0x2108.toShort(), engine.unsignedRightShiftShort(0x8421, 2))
        assertEquals(0x0842.toShort(), engine.unsignedRightShiftShort(0x8421, 4))
        
        // High bit set
        assertEquals(0x7FFF.toShort(), engine.unsignedRightShiftShort(0xFFFF, 1))
        assertEquals(0x3FFF.toShort(), engine.unsignedRightShiftShort(0xFFFF, 2))
        
        // Zero shift
        assertEquals(0x8421.toShort(), engine.unsignedRightShiftShort(0x8421, 0))
    }
    
    // ========================================================================
    // Int Type Tests (32-bit)
    // ========================================================================
    
    @Test
    fun testLeftShiftInt_Native() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
        
        // Basic shift
        assertEquals(0x2468ACF0.toInt(), engine.leftShiftInt(0x12345678, 1))
        assertEquals(0x48D159E0.toInt(), engine.leftShiftInt(0x12345678, 2))
        assertEquals(0x23456780.toInt(), engine.leftShiftInt(0x12345678, 4))
        
        // Overflow wrap
        assertEquals(0xE0000000.toInt(), engine.leftShiftInt(0xF0000000.toInt(), 1))
        assertEquals(0x00000000, engine.leftShiftInt(0xFFFFFFFF.toInt(), 32))
        
        // Zero shift
        assertEquals(0x42424242, engine.leftShiftInt(0x42424242, 0))
    }
    
    @Test
    fun testLeftShiftInt_Arithmetic() {
        val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
        
        // Basic shift
        assertEquals(0x2468ACF0.toInt(), engine.leftShiftInt(0x12345678, 1))
        assertEquals(0x48D159E0.toInt(), engine.leftShiftInt(0x12345678, 2))
        assertEquals(0x23456780.toInt(), engine.leftShiftInt(0x12345678, 4))
        
        // Overflow wrap
        assertEquals(0xE0000000.toInt(), engine.leftShiftInt(0xF0000000.toInt(), 1))
        assertEquals(0x00000000, engine.leftShiftInt(0xFFFFFFFF.toInt(), 32))
        
        // Zero shift
        assertEquals(0x42424242, engine.leftShiftInt(0x42424242, 0))
    }
    
    @Test
    fun testRightShiftInt_Native() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
        
        // Basic shift
        assertEquals(0x091A2B3C, engine.rightShiftInt(0x12345678, 1))
        assertEquals(0x048D159E, engine.rightShiftInt(0x12345678, 2))
        assertEquals(0x01234567, engine.rightShiftInt(0x12345678, 4))
        
        // Shift to zero
        assertEquals(0x00000000, engine.rightShiftInt(0x12345678, 32))
        
        // Zero shift
        assertEquals(0x42424242, engine.rightShiftInt(0x42424242, 0))
    }
    
    @Test
    fun testRightShiftInt_Arithmetic() {
        val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
        
        // Basic shift
        assertEquals(0x091A2B3C, engine.rightShiftInt(0x12345678, 1))
        assertEquals(0x048D159E, engine.rightShiftInt(0x12345678, 2))
        assertEquals(0x01234567, engine.rightShiftInt(0x12345678, 4))
        
        // Shift to zero
        assertEquals(0x00000000, engine.rightShiftInt(0x12345678, 32))
        
        // Zero shift
        assertEquals(0x42424242, engine.rightShiftInt(0x42424242, 0))
    }
    
    @Test
    fun testUnsignedRightShiftInt_Native() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
        
        // Basic shift
        assertEquals(0x42108421, engine.unsignedRightShiftInt(0x84210842.toInt(), 1))
        assertEquals(0x21084210, engine.unsignedRightShiftInt(0x84210842.toInt(), 2))
        assertEquals(0x08421084, engine.unsignedRightShiftInt(0x84210842.toInt(), 4))
        
        // High bit set
        assertEquals(0x7FFFFFFF, engine.unsignedRightShiftInt(0xFFFFFFFF.toInt(), 1))
        assertEquals(0x3FFFFFFF, engine.unsignedRightShiftInt(0xFFFFFFFF.toInt(), 2))
        
        // Zero shift
        assertEquals(0x84210842.toInt(), engine.unsignedRightShiftInt(0x84210842.toInt(), 0))
    }
    
    @Test
    fun testUnsignedRightShiftInt_Arithmetic() {
        val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
        
        // Basic shift
        assertEquals(0x42108421, engine.unsignedRightShiftInt(0x84210842.toInt(), 1))
        assertEquals(0x21084210, engine.unsignedRightShiftInt(0x84210842.toInt(), 2))
        assertEquals(0x08421084, engine.unsignedRightShiftInt(0x84210842.toInt(), 4))
        
        // High bit set
        assertEquals(0x7FFFFFFF, engine.unsignedRightShiftInt(0xFFFFFFFF.toInt(), 1))
        assertEquals(0x3FFFFFFF, engine.unsignedRightShiftInt(0xFFFFFFFF.toInt(), 2))
        
        // Zero shift
        assertEquals(0x84210842.toInt(), engine.unsignedRightShiftInt(0x84210842.toInt(), 0))
    }
    
    // ========================================================================
    // ByteArray Integration Tests
    // ========================================================================
    
    @Test
    fun testByteArrayShifting_Native() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 8)
        
        // Simulate shifting bytes in a ByteArray
        val bytes = byteArrayOf(0x12, 0x34, 0x56, 0x78.toByte())
        
        // Shift each byte left by 1
        val shifted = ByteArray(bytes.size) { i ->
            engine.leftShiftByte(bytes[i].toInt() and 0xFF, 1)
        }
        
        assertEquals(0x24.toByte(), shifted[0])
        assertEquals(0x68.toByte(), shifted[1])
        assertEquals(0xAC.toByte(), shifted[2])
        assertEquals(0xF0.toByte(), shifted[3].toUByte().toByte())
    }
    
    @Test
    fun testByteArrayShifting_Arithmetic() {
        val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 8)
        
        // Simulate shifting bytes in a ByteArray
        val bytes = byteArrayOf(0x12, 0x34, 0x56, 0x78.toByte())
        
        // Shift each byte right by 2
        val shifted = ByteArray(bytes.size) { i ->
            engine.rightShiftByte(bytes[i].toInt() and 0xFF, 2)
        }
        
        assertEquals(0x04.toByte(), shifted[0])
        assertEquals(0x0D.toByte(), shifted[1])
        assertEquals(0x15.toByte(), shifted[2])
        assertEquals(0x1E.toByte(), shifted[3])
    }
    
    // ========================================================================
    // Cross-Mode Consistency Tests
    // ========================================================================
    
    @Test
    fun testByteCrossMode_Consistency() {
        val nativeEngine = BitShiftEngine(BitShiftMode.NATIVE, 8)
        val arithmeticEngine = BitShiftEngine(BitShiftMode.ARITHMETIC, 8)
        
        val testValues = listOf(0x00, 0x01, 0x12, 0x7F, 0x80, 0xAB, 0xFF)
        val testShifts = listOf(0, 1, 2, 4, 7, 8)
        
        for (value in testValues) {
            for (shift in testShifts) {
                val nativeLeft = nativeEngine.leftShiftByte(value, shift)
                val arithmeticLeft = arithmeticEngine.leftShiftByte(value, shift)
                assertEquals(nativeLeft, arithmeticLeft, 
                    "Left shift mismatch for value=0x${value.toString(16)}, shift=$shift")
                
                val nativeRight = nativeEngine.rightShiftByte(value, shift)
                val arithmeticRight = arithmeticEngine.rightShiftByte(value, shift)
                assertEquals(nativeRight, arithmeticRight,
                    "Right shift mismatch for value=0x${value.toString(16)}, shift=$shift")
                
                val nativeURight = nativeEngine.unsignedRightShiftByte(value, shift)
                val arithmeticURight = arithmeticEngine.unsignedRightShiftByte(value, shift)
                assertEquals(nativeURight, arithmeticURight,
                    "Unsigned right shift mismatch for value=0x${value.toString(16)}, shift=$shift")
            }
        }
    }
    
    @Test
    fun testShortCrossMode_Consistency() {
        val nativeEngine = BitShiftEngine(BitShiftMode.NATIVE, 16)
        val arithmeticEngine = BitShiftEngine(BitShiftMode.ARITHMETIC, 16)
        
        val testValues = listOf(0x0000, 0x0001, 0x1234, 0x7FFF, 0x8000, 0xABCD, 0xFFFF)
        val testShifts = listOf(0, 1, 2, 4, 8, 15, 16)
        
        for (value in testValues) {
            for (shift in testShifts) {
                val nativeLeft = nativeEngine.leftShiftShort(value, shift)
                val arithmeticLeft = arithmeticEngine.leftShiftShort(value, shift)
                assertEquals(nativeLeft, arithmeticLeft,
                    "Left shift mismatch for value=0x${value.toString(16)}, shift=$shift")
                
                val nativeRight = nativeEngine.rightShiftShort(value, shift)
                val arithmeticRight = arithmeticEngine.rightShiftShort(value, shift)
                assertEquals(nativeRight, arithmeticRight,
                    "Right shift mismatch for value=0x${value.toString(16)}, shift=$shift")
                
                val nativeURight = nativeEngine.unsignedRightShiftShort(value, shift)
                val arithmeticURight = arithmeticEngine.unsignedRightShiftShort(value, shift)
                assertEquals(nativeURight, arithmeticURight,
                    "Unsigned right shift mismatch for value=0x${value.toString(16)}, shift=$shift")
            }
        }
    }
    
    @Test
    fun testIntCrossMode_Consistency() {
        val nativeEngine = BitShiftEngine(BitShiftMode.NATIVE, 32)
        val arithmeticEngine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
        
        val testValues = listOf(0x00000000, 0x00000001, 0x12345678, 0x7FFFFFFF, 
                                0x80000000.toInt(), 0xABCDEF01.toInt(), 0xFFFFFFFF.toInt())
        val testShifts = listOf(0, 1, 2, 4, 8, 16, 31, 32)
        
        for (value in testValues) {
            for (shift in testShifts) {
                val nativeLeft = nativeEngine.leftShiftInt(value, shift)
                val arithmeticLeft = arithmeticEngine.leftShiftInt(value, shift)
                assertEquals(nativeLeft, arithmeticLeft,
                    "Left shift mismatch for value=0x${value.toString(16)}, shift=$shift")
                
                val nativeRight = nativeEngine.rightShiftInt(value, shift)
                val arithmeticRight = arithmeticEngine.rightShiftInt(value, shift)
                assertEquals(nativeRight, arithmeticRight,
                    "Right shift mismatch for value=0x${value.toString(16)}, shift=$shift")
                
                val nativeURight = nativeEngine.unsignedRightShiftInt(value, shift)
                val arithmeticURight = arithmeticEngine.unsignedRightShiftInt(value, shift)
                assertEquals(nativeURight, arithmeticURight,
                    "Unsigned right shift mismatch for value=0x${value.toString(16)}, shift=$shift")
            }
        }
    }
}
