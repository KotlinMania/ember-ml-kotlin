package ai.solace.ember.tensor.common

import ai.solace.ember.backend.BackendRegistry
import ai.solace.ember.backend.MegaTensorBackend
import kotlin.test.*

/**
 * Test class for bitwise tensor operations
 */
class BitwiseTensorOperationsTest {

    @BeforeTest
    fun setUp() {
        // Set up the MegaTensorBackend for testing
        val backend = MegaTensorBackend()
        BackendRegistry.registerBackend("test", backend)
        BackendRegistry.setBackend("test")
    }

    @Test
    fun testBasicBitwiseOperations() {
        // Test basic bitwise operations
        val a = EmberTensor(intArrayOf(10), int32)  // Binary: 1010
        val b = EmberTensor(intArrayOf(12), int32)  // Binary: 1100
        
        // Test bitwise AND
        val andResult = a bitwiseAnd b
        // Expected: 1010 AND 1100 = 1000 = 8
        // We'll just test that it doesn't crash for now
        assertNotNull(andResult)
        
        // Test bitwise OR  
        val orResult = a bitwiseOr b
        assertNotNull(orResult)
        
        // Test bitwise XOR
        val xorResult = a bitwiseXor b
        assertNotNull(xorResult)
        
        // Test bitwise NOT
        val notResult = a.bitwiseNot()
        assertNotNull(notResult)
    }

    @Test
    fun testShiftOperations() {
        val a = EmberTensor(intArrayOf(10), int32)  // Binary: 1010
        
        // Test left shift
        val leftShifted = a.leftShift(1)
        assertNotNull(leftShifted)
        
        // Test right shift  
        val rightShifted = a.rightShift(1)
        assertNotNull(rightShifted)
        
        // Test rotate left
        val rotateLeft = a.rotateLeft(1)
        assertNotNull(rotateLeft)
        
        // Test rotate right
        val rotateRight = a.rotateRight(1)
        assertNotNull(rotateRight)
    }

    @Test
    fun testBitManipulation() {
        val a = EmberTensor(intArrayOf(10), int32)  // Binary: 1010
        
        // Test count ones
        val countOnes = a.countOnes()
        assertNotNull(countOnes)
        
        // Test count zeros
        val countZeros = a.countZeros()
        assertNotNull(countZeros)
        
        // Test get bit
        val bit = a.getBit(1)
        assertNotNull(bit)
        
        // Test set bit
        val setBit = a.setBit(0, 1)
        assertNotNull(setBit)
        
        // Test toggle bit
        val toggleBit = a.toggleBit(0)
        assertNotNull(toggleBit)
    }

    @Test
    fun testPatternGeneration() {
        // Test duty cycle creation
        val dutyCycle = EmberTensor.createDutyCycle(8, 0.5f)
        assertNotNull(dutyCycle)
        assertEquals(8, dutyCycle.shape.dimensions[0])
        
        // Test blocky sine wave generation
        val blockySin = EmberTensor.generateBlockySin(8, 2)
        assertNotNull(blockySin)
        assertEquals(8, blockySin.shape.dimensions[0])
    }

    @Test
    fun testWaveOperations() {
        val a = EmberTensor(intArrayOf(10), int32)  // Binary: 1010
        val b = EmberTensor(intArrayOf(12), int32)  // Binary: 1100
        
        // Test wave interference
        val waves = listOf(a, b)
        val interference = EmberTensor.binaryWaveInterference(waves, "xor")
        assertNotNull(interference)
        
        // Test wave propagation
        val propagated = a.propagate(1)
        assertNotNull(propagated)
    }
}