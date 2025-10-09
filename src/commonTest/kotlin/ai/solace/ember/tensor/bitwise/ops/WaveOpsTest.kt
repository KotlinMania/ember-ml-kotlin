package ai.solace.ember.tensor.bitwise.ops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertContentEquals

class WaveOpsTest {
    
    @Test
    fun testBinaryWaveInterferenceXor() {
        val wave1 = intArrayOf(0b1100, 0b1010)
        val wave2 = intArrayOf(0b1010, 0b0101)
        val result = binaryWaveInterference(listOf(wave1, wave2), InterferenceMode.XOR)
        assertContentEquals(intArrayOf(0b0110, 0b1111), result)
    }
    
    @Test
    fun testBinaryWaveInterferenceAnd() {
        val wave1 = intArrayOf(0b1100, 0b1010)
        val wave2 = intArrayOf(0b1010, 0b0101)
        val result = binaryWaveInterference(listOf(wave1, wave2), InterferenceMode.AND)
        assertContentEquals(intArrayOf(0b1000, 0b0000), result)
    }
    
    @Test
    fun testBinaryWaveInterferenceOr() {
        val wave1 = intArrayOf(0b1100, 0b1010)
        val wave2 = intArrayOf(0b1010, 0b0101)
        val result = binaryWaveInterference(listOf(wave1, wave2), InterferenceMode.OR)
        assertContentEquals(intArrayOf(0b1110, 0b1111), result)
    }
    
    @Test
    fun testGenerateBlockySin() {
        val result = generateBlockySin(8, 1.0, 1.0, 0.0, 8)
        assertEquals(8, result.size)
        // All values should be within the expected range
        assertTrue(result.all { it in 0..255 })
    }
    
    @Test
    fun testCreateDutyCycle() {
        val result = createDutyCycle(8, 0.5, 4, 1, 0)
        assertContentEquals(intArrayOf(1, 1, 0, 0, 1, 1, 0, 0), result)
        
        val result25 = createDutyCycle(8, 0.25, 4, 1, 0)
        assertContentEquals(intArrayOf(1, 0, 0, 0, 1, 0, 0, 0), result25)
    }
    
    @Test
    fun testPropagate() {
        val wave = intArrayOf(1, 2, 3, 4)
        
        // Shift right by 1 with wrap around
        val shiftedRight = propagate(wave, 1, true)
        assertContentEquals(intArrayOf(4, 1, 2, 3), shiftedRight)
        
        // Shift left by 1 with wrap around  
        val shiftedLeft = propagate(wave, -1, true)
        assertContentEquals(intArrayOf(2, 3, 4, 1), shiftedLeft)
        
        // Shift without wrap around
        val shiftedNoWrap = propagate(wave, 1, false)
        assertContentEquals(intArrayOf(0, 1, 2, 3), shiftedNoWrap)
    }
    
    @Test
    fun testGenerateComplexWave() {
        val frequencies = listOf(1.0, 2.0)
        val amplitudes = listOf(1.0, 0.5)
        val result = generateComplexWave(16, frequencies, amplitudes)
        assertEquals(16, result.size)
        // The result should be the XOR combination of two sine waves
        assertTrue(result.isNotEmpty())
    }
}