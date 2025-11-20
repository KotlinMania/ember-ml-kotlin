package ai.solace.klang.bitwise

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for BitShiftEngine helper functions (byte/bit manipulation utilities).
 *
 * Tests cover:
 * - extractByte/replaceByte
 * - isBitSet/setBit/clearBit/toggleBit
 * - popCount
 * - signExtend/zeroExtend
 * - composeBytes/decomposeBytes
 * - Both ARITHMETIC and NATIVE modes
 */
class BitShiftEngineHelperTest {

    @Test
    fun testExtractByte_Native() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
        val value = 0x12345678L
        
        assertEquals(0x78L, engine.extractByte(value, 0))
        assertEquals(0x56L, engine.extractByte(value, 1))
        assertEquals(0x34L, engine.extractByte(value, 2))
        assertEquals(0x12L, engine.extractByte(value, 3))
    }
    
    @Test
    fun testExtractByte_Arithmetic() {
        val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
        val value = 0x12345678L
        
        assertEquals(0x78L, engine.extractByte(value, 0))
        assertEquals(0x56L, engine.extractByte(value, 1))
        assertEquals(0x34L, engine.extractByte(value, 2))
        assertEquals(0x12L, engine.extractByte(value, 3))
    }
    
    @Test
    fun testExtractByte_16Bit() {
        val engineNative = BitShiftEngine(BitShiftMode.NATIVE, 16)
        val engineArith = BitShiftEngine(BitShiftMode.ARITHMETIC, 16)
        val value = 0xABCDL
        
        assertEquals(0xCDL, engineNative.extractByte(value, 0))
        assertEquals(0xABL, engineNative.extractByte(value, 1))
        
        assertEquals(0xCDL, engineArith.extractByte(value, 0))
        assertEquals(0xABL, engineArith.extractByte(value, 1))
    }
    
    @Test
    fun testReplaceByte_Native() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
        val original = 0x12345678L
        
        assertEquals(0x123456AAL, engine.replaceByte(original, 0, 0xAAL))
        assertEquals(0x1234AA78L, engine.replaceByte(original, 1, 0xAAL))
        assertEquals(0x12AA5678L, engine.replaceByte(original, 2, 0xAAL))
        assertEquals(0xAA345678L, engine.replaceByte(original, 3, 0xAAL))
    }
    
    @Test
    fun testReplaceByte_Arithmetic() {
        val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
        val original = 0x12345678L
        
        assertEquals(0x123456AAL, engine.replaceByte(original, 0, 0xAAL))
        assertEquals(0x1234AA78L, engine.replaceByte(original, 1, 0xAAL))
        assertEquals(0x12AA5678L, engine.replaceByte(original, 2, 0xAAL))
        assertEquals(0xAA345678L, engine.replaceByte(original, 3, 0xAAL))
    }
    
    @Test
    fun testIsBitSet_Native() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 8)
        val value = 0b10101010L
        
        assertFalse(engine.isBitSet(value, 0))
        assertTrue(engine.isBitSet(value, 1))
        assertFalse(engine.isBitSet(value, 2))
        assertTrue(engine.isBitSet(value, 3))
        assertFalse(engine.isBitSet(value, 4))
        assertTrue(engine.isBitSet(value, 5))
        assertFalse(engine.isBitSet(value, 6))
        assertTrue(engine.isBitSet(value, 7))
    }
    
    @Test
    fun testIsBitSet_Arithmetic() {
        val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 8)
        val value = 0b10101010L
        
        assertFalse(engine.isBitSet(value, 0))
        assertTrue(engine.isBitSet(value, 1))
        assertFalse(engine.isBitSet(value, 2))
        assertTrue(engine.isBitSet(value, 3))
        assertFalse(engine.isBitSet(value, 4))
        assertTrue(engine.isBitSet(value, 5))
        assertFalse(engine.isBitSet(value, 6))
        assertTrue(engine.isBitSet(value, 7))
    }
    
    @Test
    fun testSetBit_Native() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 8)
        
        assertEquals(0b00001000L, engine.setBit(0b00000000L, 3))
        assertEquals(0b00001010L, engine.setBit(0b00001010L, 3))
        assertEquals(0b10000000L, engine.setBit(0b00000000L, 7))
    }
    
    @Test
    fun testSetBit_Arithmetic() {
        val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 8)
        
        assertEquals(0b00001000L, engine.setBit(0b00000000L, 3))
        assertEquals(0b00001010L, engine.setBit(0b00001010L, 3))
        assertEquals(0b10000000L, engine.setBit(0b00000000L, 7))
    }
    
    @Test
    fun testClearBit_Native() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 8)
        
        assertEquals(0b11110111L, engine.clearBit(0b11111111L, 3))
        assertEquals(0b11110101L, engine.clearBit(0b11110101L, 3))
        assertEquals(0b01111111L, engine.clearBit(0b11111111L, 7))
    }
    
    @Test
    fun testClearBit_Arithmetic() {
        val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 8)
        
        assertEquals(0b11110111L, engine.clearBit(0b11111111L, 3))
        assertEquals(0b11110101L, engine.clearBit(0b11110101L, 3))
        assertEquals(0b01111111L, engine.clearBit(0b11111111L, 7))
    }
    
    @Test
    fun testToggleBit_Native() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 8)
        
        assertEquals(0b10101011L, engine.toggleBit(0b10101010L, 0))
        assertEquals(0b10101000L, engine.toggleBit(0b10101010L, 1))
        assertEquals(0b00101010L, engine.toggleBit(0b10101010L, 7))
    }
    
    @Test
    fun testToggleBit_Arithmetic() {
        val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 8)
        
        assertEquals(0b10101011L, engine.toggleBit(0b10101010L, 0))
        assertEquals(0b10101000L, engine.toggleBit(0b10101010L, 1))
        assertEquals(0b00101010L, engine.toggleBit(0b10101010L, 7))
    }
    
    @Test
    fun testPopCount_Native() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 8)
        
        assertEquals(0, engine.popCount(0b00000000L))
        assertEquals(4, engine.popCount(0b10101010L))
        assertEquals(8, engine.popCount(0b11111111L))
        assertEquals(1, engine.popCount(0b10000000L))
    }
    
    @Test
    fun testPopCount_Arithmetic() {
        val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 8)
        
        assertEquals(0, engine.popCount(0b00000000L))
        assertEquals(4, engine.popCount(0b10101010L))
        assertEquals(8, engine.popCount(0b11111111L))
        assertEquals(1, engine.popCount(0b10000000L))
    }
    
    @Test
    fun testSignExtend_8to32_Native() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
        
        // Positive 8-bit value
        assertEquals(0x0000007FL, engine.signExtend(0x7FL, 8))
        
        // Negative 8-bit value (sign bit set)
        assertEquals(0xFFFFFFFFL, engine.signExtend(0xFFL, 8))
        assertEquals(0xFFFFFF80L, engine.signExtend(0x80L, 8))
    }
    
    @Test
    fun testSignExtend_8to32_Arithmetic() {
        val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
        
        // Positive 8-bit value
        assertEquals(0x0000007FL, engine.signExtend(0x7FL, 8))
        
        // Negative 8-bit value (sign bit set)
        assertEquals(0xFFFFFFFFL, engine.signExtend(0xFFL, 8))
        assertEquals(0xFFFFFF80L, engine.signExtend(0x80L, 8))
    }
    
    @Test
    fun testSignExtend_16to32() {
        val engineNative = BitShiftEngine(BitShiftMode.NATIVE, 32)
        val engineArith = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
        
        // Positive 16-bit value
        assertEquals(0x00007FFFL, engineNative.signExtend(0x7FFFL, 16))
        assertEquals(0x00007FFFL, engineArith.signExtend(0x7FFFL, 16))
        
        // Negative 16-bit value
        assertEquals(0xFFFF8000L, engineNative.signExtend(0x8000L, 16))
        assertEquals(0xFFFF8000L, engineArith.signExtend(0x8000L, 16))
        assertEquals(0xFFFFFFFFL, engineNative.signExtend(0xFFFFL, 16))
        assertEquals(0xFFFFFFFFL, engineArith.signExtend(0xFFFFL, 16))
    }
    
    @Test
    fun testZeroExtend_8to32_Native() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
        
        assertEquals(0x0000007FL, engine.zeroExtend(0x7FL, 8))
        assertEquals(0x000000FFL, engine.zeroExtend(0xFFL, 8))
        assertEquals(0x00000080L, engine.zeroExtend(0x80L, 8))
    }
    
    @Test
    fun testZeroExtend_8to32_Arithmetic() {
        val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
        
        assertEquals(0x0000007FL, engine.zeroExtend(0x7FL, 8))
        assertEquals(0x000000FFL, engine.zeroExtend(0xFFL, 8))
        assertEquals(0x00000080L, engine.zeroExtend(0x80L, 8))
    }
    
    @Test
    fun testComposeBytes_Native() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
        
        val bytes = longArrayOf(0x78L, 0x56L, 0x34L, 0x12L)
        assertEquals(0x12345678L, engine.composeBytes(bytes))
        
        val bytes2 = longArrayOf(0xFFL, 0x00L)
        val engine16 = BitShiftEngine(BitShiftMode.NATIVE, 16)
        assertEquals(0x00FFL, engine16.composeBytes(bytes2))
    }
    
    @Test
    fun testComposeBytes_Arithmetic() {
        val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
        
        val bytes = longArrayOf(0x78L, 0x56L, 0x34L, 0x12L)
        assertEquals(0x12345678L, engine.composeBytes(bytes))
        
        val bytes2 = longArrayOf(0xFFL, 0x00L)
        val engine16 = BitShiftEngine(BitShiftMode.ARITHMETIC, 16)
        assertEquals(0x00FFL, engine16.composeBytes(bytes2))
    }
    
    @Test
    fun testDecomposeBytes_Native() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
        
        val bytes = engine.decomposeBytes(0x12345678L, 4)
        assertEquals(4, bytes.size)
        assertEquals(0x78L, bytes[0])
        assertEquals(0x56L, bytes[1])
        assertEquals(0x34L, bytes[2])
        assertEquals(0x12L, bytes[3])
    }
    
    @Test
    fun testDecomposeBytes_Arithmetic() {
        val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
        
        val bytes = engine.decomposeBytes(0x12345678L, 4)
        assertEquals(4, bytes.size)
        assertEquals(0x78L, bytes[0])
        assertEquals(0x56L, bytes[1])
        assertEquals(0x34L, bytes[2])
        assertEquals(0x12L, bytes[3])
    }
    
    @Test
    fun testBitwiseOperations_Native() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 8)
        
        assertEquals(0b10100000L, engine.bitwiseAnd(0b11110000L, 0b10101010L))
        assertEquals(0b11111010L, engine.bitwiseOr(0b11110000L, 0b10101010L))
        assertEquals(0b01011010L, engine.bitwiseXor(0b11110000L, 0b10101010L))
        assertEquals(0b00001111L, engine.bitwiseNot(0b11110000L))
    }
    
    @Test
    fun testBitwiseOperations_Arithmetic() {
        val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 8)
        
        assertEquals(0b10100000L, engine.bitwiseAnd(0b11110000L, 0b10101010L))
        assertEquals(0b11111010L, engine.bitwiseOr(0b11110000L, 0b10101010L))
        assertEquals(0b01011010L, engine.bitwiseXor(0b11110000L, 0b10101010L))
        assertEquals(0b00001111L, engine.bitwiseNot(0b11110000L))
    }
    
    @Test
    fun testGetMask_Native() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
        
        assertEquals(0xFFL, engine.getMask(8))
        assertEquals(0xFFFFL, engine.getMask(16))
        assertEquals(0xFFFFFFFFL, engine.getMask(32))
        assertEquals(0x1L, engine.getMask(1))
        assertEquals(0x7FL, engine.getMask(7))
    }
    
    @Test
    fun testGetMask_Arithmetic() {
        val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
        
        assertEquals(0xFFL, engine.getMask(8))
        assertEquals(0xFFFFL, engine.getMask(16))
        assertEquals(0xFFFFFFFFL, engine.getMask(32))
        assertEquals(0x1L, engine.getMask(1))
        assertEquals(0x7FL, engine.getMask(7))
    }
    
    @Test
    fun testGetMask_AllBitWidths() {
        val modes = listOf(BitShiftMode.NATIVE, BitShiftMode.ARITHMETIC)
        val bitWidths = listOf(8, 16, 32)
        
        for (mode in modes) {
            for (width in bitWidths) {
                val engine = BitShiftEngine(mode, width)
                
                // Test that full-width mask works
                val fullMask = engine.getMask(width)
                assertEquals(width, engine.popCount(fullMask))
                
                // Test that partial masks work
                for (bits in 1 until width) {
                    val mask = engine.getMask(bits)
                    assertEquals(bits, engine.popCount(mask))
                }
            }
        }
    }
    
    @Test
    fun testComposeDecomposeRoundtrip_Native() {
        val engine = BitShiftEngine(BitShiftMode.NATIVE, 32)
        val original = 0xDEADBEEFL
        
        val decomposed = engine.decomposeBytes(original, 4)
        val recomposed = engine.composeBytes(decomposed)
        
        assertEquals(original, recomposed)
    }
    
    @Test
    fun testComposeDecomposeRoundtrip_Arithmetic() {
        val engine = BitShiftEngine(BitShiftMode.ARITHMETIC, 32)
        val original = 0xDEADBEEFL
        
        val decomposed = engine.decomposeBytes(original, 4)
        val recomposed = engine.composeBytes(decomposed)
        
        assertEquals(original, recomposed)
    }
    
    @Test
    fun testEdgeCases_AllZeros() {
        val engineNative = BitShiftEngine(BitShiftMode.NATIVE, 8)
        val engineArith = BitShiftEngine(BitShiftMode.ARITHMETIC, 8)
        
        assertEquals(0, engineNative.popCount(0L))
        assertEquals(0, engineArith.popCount(0L))
        
        assertFalse(engineNative.isBitSet(0L, 0))
        assertFalse(engineArith.isBitSet(0L, 0))
    }
    
    @Test
    fun testEdgeCases_AllOnes() {
        val engineNative = BitShiftEngine(BitShiftMode.NATIVE, 8)
        val engineArith = BitShiftEngine(BitShiftMode.ARITHMETIC, 8)
        
        assertEquals(8, engineNative.popCount(0xFFL))
        assertEquals(8, engineArith.popCount(0xFFL))
        
        assertTrue(engineNative.isBitSet(0xFFL, 7))
        assertTrue(engineArith.isBitSet(0xFFL, 7))
    }
}
