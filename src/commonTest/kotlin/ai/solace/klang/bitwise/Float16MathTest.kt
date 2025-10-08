package ai.solace.klang.bitwise

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class Float16MathTest {
    
    @Test
    fun testSpecialValues() {
        // Zero
        assertTrue(Float16Math.isZero(Float16Math.ZERO_BITS))
        assertEquals(0, Float16Math.getSign(Float16Math.ZERO_BITS))
        
        // One
        assertFalse(Float16Math.isZero(Float16Math.ONE_BITS))
        assertEquals(0, Float16Math.getSign(Float16Math.ONE_BITS))
        
        // NaN
        assertTrue(Float16Math.isNaN(Float16Math.NAN_BITS))
        assertFalse(Float16Math.isInf(Float16Math.NAN_BITS))
        
        // Infinity
        assertTrue(Float16Math.isInf(Float16Math.INF_BITS))
        assertFalse(Float16Math.isNaN(Float16Math.INF_BITS))
        assertEquals(0, Float16Math.getSign(Float16Math.INF_BITS))
        
        // Negative infinity
        assertTrue(Float16Math.isInf(Float16Math.NEG_INF_BITS))
        assertEquals(1, Float16Math.getSign(Float16Math.NEG_INF_BITS))
    }
    
    @Test
    fun testFloat32Conversion() {
        // Test conversion: float16 -> float32 -> float16
        val testValues = listOf(
            Float16Math.ZERO_BITS,
            Float16Math.ONE_BITS,
            Float16Math.fromFloat32Bits(2.0f.toRawBits()),
            Float16Math.fromFloat32Bits(0.5f.toRawBits()),
            Float16Math.fromFloat32Bits((-1.0f).toRawBits())
        )
        
        testValues.forEach { original ->
            val f32 = Float16Math.toFloat32Bits(original)
            val backTo16 = Float16Math.fromFloat32Bits(f32)
            assertEquals(original and 0xFFFF, backTo16 and 0xFFFF, 
                "Round trip failed for bits: 0x${original.toString(16)}")
        }
    }
    
    @Test
    fun testAddition() {
        // 1.0 + 1.0 = 2.0
        val one = Float16Math.ONE_BITS
        val result = Float16Math.addBits(one, one)
        val resultFloat = Float.fromBits(Float16Math.toFloat32Bits(result))
        assertEquals(2.0f, resultFloat, 0.01f)
    }
    
    @Test
    fun testSubtraction() {
        // 2.0 - 1.0 = 1.0
        val two = Float16Math.fromFloat32Bits(2.0f.toRawBits())
        val one = Float16Math.ONE_BITS
        val result = Float16Math.subBits(two, one)
        val resultFloat = Float.fromBits(Float16Math.toFloat32Bits(result))
        assertEquals(1.0f, resultFloat, 0.01f)
    }
    
    @Test
    fun testMultiplication() {
        // 2.0 * 3.0 = 6.0
        val two = Float16Math.fromFloat32Bits(2.0f.toRawBits())
        val three = Float16Math.fromFloat32Bits(3.0f.toRawBits())
        val result = Float16Math.mulBits(two, three)
        val resultFloat = Float.fromBits(Float16Math.toFloat32Bits(result))
        assertEquals(6.0f, resultFloat, 0.01f)
    }
    
    @Test
    fun testDivision() {
        // 6.0 / 2.0 = 3.0
        val six = Float16Math.fromFloat32Bits(6.0f.toRawBits())
        val two = Float16Math.fromFloat32Bits(2.0f.toRawBits())
        val result = Float16Math.divBits(six, two)
        val resultFloat = Float.fromBits(Float16Math.toFloat32Bits(result))
        assertEquals(3.0f, resultFloat, 0.01f)
    }
    
    @Test
    fun testNegation() {
        val one = Float16Math.ONE_BITS
        val negOne = Float16Math.negateBits(one)
        
        assertEquals(0, Float16Math.getSign(one))
        assertEquals(1, Float16Math.getSign(negOne))
        
        val negOneFloat = Float.fromBits(Float16Math.toFloat32Bits(negOne))
        assertEquals(-1.0f, negOneFloat, 0.01f)
    }
    
    @Test
    fun testAbsoluteValue() {
        val negOne = Float16Math.negateBits(Float16Math.ONE_BITS)
        val abs = Float16Math.absBits(negOne)
        
        assertEquals(Float16Math.ONE_BITS and 0xFFFF, abs and 0xFFFF)
    }
    
    @Test
    fun testComparison() {
        val one = Float16Math.ONE_BITS
        val two = Float16Math.fromFloat32Bits(2.0f.toRawBits())
        val negOne = Float16Math.negateBits(one)
        
        // 1.0 < 2.0
        assertTrue(Float16Math.compareBits(one, two) < 0)
        
        // 2.0 > 1.0
        assertTrue(Float16Math.compareBits(two, one) > 0)
        
        // 1.0 == 1.0
        assertEquals(0, Float16Math.compareBits(one, one))
        
        // -1.0 < 1.0
        assertTrue(Float16Math.compareBits(negOne, one) < 0)
    }
    
    @Test
    fun testZeroHandling() {
        val posZero = Float16Math.ZERO_BITS
        val negZero = Float16Math.negateBits(posZero)
        
        assertTrue(Float16Math.isZero(posZero))
        assertTrue(Float16Math.isZero(negZero))
        
        // +0 and -0 should compare equal
        assertEquals(0, Float16Math.compareBits(posZero, negZero))
    }
}
