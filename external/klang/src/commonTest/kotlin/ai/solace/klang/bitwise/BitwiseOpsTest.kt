package ai.solace.klang.bitwise

import ai.solace.klang.mem.GlobalHeap
import ai.solace.klang.mem.KMalloc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class BitwiseOpsTest {
    private fun setup() {
        GlobalHeap.init(1 shl 20)  // 1MB
        KMalloc.init(1 shl 18)      // 256KB
    }
    
    @Test
    fun createMaskBasic() {
        setup()
        
        val mask8 = BitwiseOps.createMask(8)
        assertEquals(0xFF, mask8, "8-bit mask failed")
        
        val mask16 = BitwiseOps.createMask(16)
        assertEquals(0xFFFF, mask16, "16-bit mask failed")
    }
    
    @Test
    fun createMaskZero() {
        setup()
        
        val mask = BitwiseOps.createMask(0)
        assertEquals(0, mask, "Zero mask failed")
    }
    
    @Test
    fun extractBitsLowByte() {
        setup()
        
        val value = 0x12345678
        val lowByte = BitwiseOps.extractBits(value, 8)
        
        assertEquals(0x78, lowByte, "Extract low byte failed")
    }
    
    @Test
    fun extractBitsLowNibble() {
        setup()
        
        val value = 0xABCD
        val lowNibble = BitwiseOps.extractBits(value, 4)
        
        assertEquals(0x0D, lowNibble, "Extract low nibble failed")
    }
    
    @Test
    fun extractBitRangeMiddle() {
        setup()
        
        // Extract bits 8-15 from 0x12345678
        val value = 0x12345678
        val middleByte = BitwiseOps.extractBitRange(value, 8, 8)
        
        assertEquals(0x56, middleByte, "Extract bit range failed")
    }
    
    @Test
    fun extractBitRangeHigh() {
        setup()
        
        // Extract bits 24-31 from 0x12345678
        val value = 0x12345678
        val highByte = BitwiseOps.extractBitRange(value, 24, 8)
        
        assertEquals(0x12, highByte, "Extract high bit range failed")
    }
    
    @Test
    fun combine16BitBasic() {
        setup()
        
        val high = 0x1234
        val low = 0x5678
        val combined = BitwiseOps.combine16Bit(high, low)
        
        assertEquals(0x12345678, combined, "Combine 16-bit failed")
    }
    
    @Test
    fun combine16BitToLong() {
        setup()
        
        val high = 0xABCDL
        val low = 0xEF01L
        val combined = BitwiseOps.combine16BitToLong(high, low)
        
        assertEquals(0xABCDEF01L, combined, "Combine 16-bit to long failed")
    }
    
    @Test
    fun getHigh16Bits() {
        setup()
        
        val value = 0x12345678
        val high = BitwiseOps.getHigh16Bits(value)
        
        assertEquals(0x1234, high, "Get high 16 bits failed")
    }
    
    @Test
    fun getLow16Bits() {
        setup()
        
        val value = 0x12345678
        val low = BitwiseOps.getLow16Bits(value)
        
        assertEquals(0x5678, low, "Get low 16 bits failed")
    }
    
    @Test
    fun byteToUnsignedIntPositive() {
        setup()
        
        val b: Byte = 127
        val unsigned = BitwiseOps.byteToUnsignedInt(b)
        
        assertEquals(127, unsigned, "Byte to unsigned int (positive) failed")
    }
    
    @Test
    fun byteToUnsignedIntNegative() {
        setup()
        
        val b: Byte = -1
        val unsigned = BitwiseOps.byteToUnsignedInt(b)
        
        assertEquals(255, unsigned, "Byte to unsigned int (negative) failed")
    }
    
    @Test
    fun byteToUnsignedIntZero() {
        setup()
        
        val b: Byte = 0
        val unsigned = BitwiseOps.byteToUnsignedInt(b)
        
        assertEquals(0, unsigned, "Byte to unsigned int (zero) failed")
    }
    
    @Test
    fun arithmeticLeftShift() {
        setup()
        
        val value = 1
        val shifted = BitwiseOps.leftShiftArithmetic(value, 8)
        
        assertEquals(256, shifted, "Arithmetic left shift failed")
    }
    
    @Test
    fun arithmeticRightShift() {
        setup()
        
        val value = 256
        val shifted = BitwiseOps.rightShiftArithmetic(value, 8)
        
        assertEquals(1, shifted, "Arithmetic right shift failed")
    }
    
    @Test
    fun isBitSetArithmetic() {
        setup()
        
        val value = 0b10101010
        
        assertTrue(BitwiseOps.isBitSetArithmetic(value, 1), "Bit 1 should be set")
        assertFalse(BitwiseOps.isBitSetArithmetic(value, 0), "Bit 0 should not be set")
        assertTrue(BitwiseOps.isBitSetArithmetic(value, 3), "Bit 3 should be set")
        assertFalse(BitwiseOps.isBitSetArithmetic(value, 2), "Bit 2 should not be set")
    }
    
    @Test
    fun orArithmeticNonOverlapping() {
        setup()
        
        val a = 0x00FF
        val b = 0xFF00
        val result = BitwiseOps.orArithmetic(a, b)
        
        assertEquals(0xFFFF, result, "OR arithmetic (non-overlapping) failed")
    }
    
    @Test
    fun orArithmeticGeneral() {
        setup()
        
        val a = 0b1010
        val b = 0b1100
        val result = BitwiseOps.orArithmeticGeneral(a, b)
        
        // 1010 OR 1100 = 1110 = 14
        assertEquals(0b1110, result, "OR arithmetic (general) failed")
    }
    
    @Test
    fun rotateLeft() {
        setup()
        
        val value = 0x12345678
        val rotated = BitwiseOps.rotateLeft(value, 8)
        
        // Rotating left by 8 should move byte pattern
        val expected = 0x34567812
        assertEquals(expected, rotated, "Rotate left failed")
    }
    
    @Test
    fun rotateRight() {
        setup()
        
        val value = 0x12345678
        val rotated = BitwiseOps.rotateRight(value, 8)
        
        // Rotating right by 8 should move byte pattern
        val expected = 0x78123456
        assertEquals(expected, rotated, "Rotate right failed")
    }
    
    @Test
    fun rotateLeftZero() {
        setup()
        
        val value = 0x12345678
        val rotated = BitwiseOps.rotateLeft(value, 0)
        
        // Rotating by 0 should return normalized value
        assertEquals(value, rotated, "Rotate left by zero failed")
    }
    
    @Test
    fun unsignedRightShiftImproved() {
        setup()
        
        val value = -1  // All bits set
        val shifted = BitwiseOps.urShiftImproved(value, 1)
        
        // Should shift in zero from left
        assertEquals(0x7FFFFFFF, shifted, "Unsigned right shift improved failed")
    }
    
    @Test
    fun unsignedRightShiftImprovedLong() {
        setup()
        
        val value = -1L  // All bits set
        val shifted = BitwiseOps.urShiftImproved(value, 1)
        
        // Should shift in zero from left
        assertEquals(0x7FFFFFFFFFFFFFFFL, shifted, "Unsigned right shift improved (long) failed")
    }
    
    @Test
    fun maskCreationArithmetic() {
        setup()
        
        val mask4 = BitwiseOps.createMaskArithmetic(4)
        assertEquals(0x0F, mask4, "4-bit arithmetic mask failed")
        
        val mask12 = BitwiseOps.createMaskArithmetic(12)
        assertEquals(0xFFF, mask12, "12-bit arithmetic mask failed")
    }
    
    @Test
    fun extractBitsArithmetic() {
        setup()
        
        val value = 0xABCD
        val low8 = BitwiseOps.extractBitsArithmetic(value, 8)
        
        assertEquals(0xCD, low8, "Extract bits arithmetic failed")
    }
    
    @Test
    fun high16BitsArithmetic() {
        setup()
        
        val value = 0x12345678L
        val high = BitwiseOps.getHigh16BitsArithmetic(value)
        
        assertEquals(0x1234, high, "Get high 16 bits arithmetic failed")
    }
    
    @Test
    fun low16BitsArithmetic() {
        setup()
        
        val value = 0x12345678L
        val low = BitwiseOps.getLow16BitsArithmetic(value)
        
        assertEquals(0x5678, low, "Get low 16 bits arithmetic failed")
    }
    
    @Test
    fun combine16BitArithmetic() {
        setup()
        
        val high = 0xABCD
        val low = 0xEF01
        val combined = BitwiseOps.combine16BitArithmetic(high, low)
        
        assertEquals(0xABCDEF01L, combined, "Combine 16-bit arithmetic failed")
    }
    
    @Test
    fun roundTripHighLow16() {
        setup()
        
        val original = 0x12345678
        val high = BitwiseOps.getHigh16Bits(original)
        val low = BitwiseOps.getLow16Bits(original)
        val reconstructed = BitwiseOps.combine16Bit(high, low)
        
        assertEquals(original, reconstructed, "Round-trip high/low 16 bits failed")
    }
    
    @Test
    fun bitRangeExtraction() {
        setup()
        
        // Extract middle 8 bits from 0xABCDEF01
        val value = 0xABCDEF01.toInt()
        val middle = BitwiseOps.extractBitRange(value, 8, 8)
        
        assertEquals(0xEF, middle, "Bit range extraction failed")
    }
    
    @Test
    fun shiftAndMask() {
        setup()
        
        val value = 0xFF00FF00.toInt()
        val shifted = BitwiseOps.rightShiftArithmetic(value, 8)
        // 0xFF00FF00 >> 8 = 0x00FF00FF (or with sign extension: 0xFFFF00FF)
        // Extract low 8 bits should give 0xFF
        val masked = BitwiseOps.extractBits(shifted, 8)
        
        assertEquals(0xFF, masked, "Shift and mask failed")
    }
}
