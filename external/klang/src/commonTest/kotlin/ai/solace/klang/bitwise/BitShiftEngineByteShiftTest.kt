package ai.solace.klang.bitwise

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for BitShiftEngine byte-level shifting operations.
 *
 * Validates that byteShiftLeft and byteShiftRight work correctly for both
 * NATIVE and ARITHMETIC modes across different bit widths.
 */
class BitShiftEngineByteShiftTest {
    
    @Test
    fun testByteShiftLeft_8bit_native() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 8)
        
        // Shift 0x12 left by 0 bytes (no change)
        assertEquals(0x12L, engine.byteShiftLeft(0x12, 0).value)
        
        // Shift left by 1 byte would overflow 8-bit value
        val result = engine.byteShiftLeft(0x12, 1)
        assertEquals(0L, result.value)
        assertEquals(true, result.overflow)
    }
    
    @Test
    fun testByteShiftLeft_16bit_native() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 16)
        
        // Shift 0x12 left by 1 byte (8 bits)
        assertEquals(0x1200L, engine.byteShiftLeft(0x12, 1).value)
        
        // Shift 0x1234 left by 1 byte
        val result = engine.byteShiftLeft(0x1234, 1)
        assertEquals(0x3400L, result.value)
        assertEquals(true, result.overflow)
        
        // Shift left by 2 bytes causes complete overflow
        val result2 = engine.byteShiftLeft(0x1234, 2)
        assertEquals(0L, result2.value)
        assertEquals(true, result2.overflow)
    }
    
    @Test
    fun testByteShiftLeft_32bit_native() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
        
        // Shift 0x12345678 left by 1 byte
        assertEquals(0x34567800L, engine.byteShiftLeft(0x12345678, 1).value)
        
        // Shift left by 2 bytes
        assertEquals(0x56780000L, engine.byteShiftLeft(0x12345678, 2).value)
        
        // Shift left by 3 bytes
        assertEquals(0x78000000L, engine.byteShiftLeft(0x12345678, 3).value)
        
        // Shift left by 4 bytes causes overflow
        val result = engine.byteShiftLeft(0x12345678, 4)
        assertEquals(0L, result.value)
        assertEquals(true, result.overflow)
    }
    
    @Test
    fun testByteShiftLeft_64bit_native() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 64)
        
        // Use simple values for testing
        val val1 = 0x0000000012345678L
        
        // Shift left by 1 byte
        val result1 = engine.byteShiftLeft(val1, 1)
        assertEquals(0x0000001234567800L, result1.value)
        
        // Shift left by 2 bytes
        val result2 = engine.byteShiftLeft(val1, 2)
        assertEquals(0x0000123456780000L, result2.value)
    }
    
    @Test
    fun testByteShiftRight_8bit_native() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 8)
        
        // Shift 0x12 right by 0 bytes (no change)
        assertEquals(0x12L, engine.byteShiftRight(0x12, 0).value)
        
        // Shift right by 1 byte results in 0
        assertEquals(0L, engine.byteShiftRight(0x12, 1).value)
    }
    
    @Test
    fun testByteShiftRight_16bit_native() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 16)
        
        // Shift 0x1234 right by 1 byte (8 bits)
        assertEquals(0x0012L, engine.byteShiftRight(0x1234, 1).value)
        
        // Shift right by 2 bytes results in 0
        assertEquals(0L, engine.byteShiftRight(0x1234, 2).value)
    }
    
    @Test
    fun testByteShiftRight_32bit_native() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
        
        // Shift 0x12345678 right by 1 byte
        assertEquals(0x00123456L, engine.byteShiftRight(0x12345678, 1).value)
        
        // Shift right by 2 bytes
        assertEquals(0x00001234L, engine.byteShiftRight(0x12345678, 2).value)
        
        // Shift right by 3 bytes
        assertEquals(0x00000012L, engine.byteShiftRight(0x12345678, 3).value)
        
        // Shift right by 4 bytes results in 0
        assertEquals(0L, engine.byteShiftRight(0x12345678, 4).value)
    }
    
    @Test
    fun testByteShiftRight_64bit_native() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 64)
        
        // Use values within signed long range for reliable testing
        val val1 = 0x0123456789ABCDEFL
        
        // Shift right by 1 byte
        assertEquals(0x000123456789ABCDL, engine.byteShiftRight(val1, 1).value)
        
        // Shift right by 4 bytes
        assertEquals(0x0000000001234567L, engine.byteShiftRight(val1, 4).value)
        
        // Shift right by 7 bytes
        assertEquals(0x0000000000000001L, engine.byteShiftRight(val1, 7).value)
    }
    
    @Test
    fun testByteShiftLeft_arithmetic_mode() {
        val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
        
        // Same results as NATIVE mode
        assertEquals(0x34567800L, engine.byteShiftLeft(0x12345678, 1).value)
        assertEquals(0x56780000L, engine.byteShiftLeft(0x12345678, 2).value)
        assertEquals(0x78000000L, engine.byteShiftLeft(0x12345678, 3).value)
    }
    
    @Test
    fun testByteShiftRight_arithmetic_mode() {
        val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
        
        // Same results as NATIVE mode
        assertEquals(0x00123456L, engine.byteShiftRight(0x12345678, 1).value)
        assertEquals(0x00001234L, engine.byteShiftRight(0x12345678, 2).value)
        assertEquals(0x00000012L, engine.byteShiftRight(0x12345678, 3).value)
    }
    
    @Test
    fun testComposeBytesHelper() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 16)
        
        // Compose bytes into 16-bit value
        assertEquals(0x1234L, engine.composeBytes(0x34, 0x12))
        assertEquals(0xABCDL, engine.composeBytes(0xCD, 0xAB))
        assertEquals(0x00FFL, engine.composeBytes(0xFF, 0x00))
        assertEquals(0xFF00L, engine.composeBytes(0x00, 0xFF))
    }
    
    @Test
    fun testDecomposeBytesHelper() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 16)
        
        // Decompose 16-bit value into bytes
        val (low1, high1) = engine.decomposeBytes(0x1234)
        assertEquals(0x34.toByte(), low1)
        assertEquals(0x12.toByte(), high1)
        
        val (low2, high2) = engine.decomposeBytes(0xABCD)
        assertEquals(0xCD.toByte(), low2)
        assertEquals(0xAB.toByte(), high2)
    }
    
    @Test
    fun testByteShiftRoundTrip() {
        val engine32 = BitShiftEngine(BitShiftMode.NATIVE, 32)
        val original = 0x12345678L
        
        // Shift left then right should give us back part of the original
        val shifted = engine32.byteShiftLeft(original, 1).value
        val back = engine32.byteShiftRight(shifted, 1).value
        assertEquals(0x00345678L, back) // Lost the high byte
    }
    
    @Test
    fun testByteShiftBoundaryConditions() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
        
        // Negative byte count
        val result1 = engine.byteShiftLeft(0x12345678, -1)
        assertEquals(0L, result1.value)
        assertEquals(true, result1.overflow)
        
        // Out of range byte count
        val result2 = engine.byteShiftLeft(0x12345678, 5)
        assertEquals(0L, result2.value)
        assertEquals(true, result2.overflow)
        
        // Zero shift
        assertEquals(0x12345678L, engine.byteShiftLeft(0x12345678, 0).value)
        assertEquals(0x12345678L, engine.byteShiftRight(0x12345678, 0).value)
    }
    
    @Test
    fun testByteShiftPreservesMode() {
        // Ensure ARITHMETIC mode propagates through byte shifts
        val arithmetic = BitShiftEngine(BitShiftMode.ARITHMETIC, 16)
        val result = arithmetic.byteShiftLeft(0x1234, 1)
        // Result should be computed using arithmetic operations
        assertEquals(0x3400L, result.value)
        
        val native = BitShiftEngine(BitShiftMode.NATIVE, 16)
        val result2 = native.byteShiftLeft(0x1234, 1)
        // Both should produce same result
        assertEquals(result.value, result2.value)
    }
}
