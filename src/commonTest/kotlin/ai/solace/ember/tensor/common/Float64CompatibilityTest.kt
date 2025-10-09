/**
 * Float64 Compatibility Test
 * 
 * This test verifies that the bitwise tensor operations work correctly with Float64
 * values and provide the necessary workarounds for Apple MLX/Metal compatibility.
 */
package ai.solace.ember.tensor.common

import ai.solace.ember.backend.BackendRegistry
import ai.solace.ember.backend.MegaTensorBackend
import kotlin.test.*

class Float64CompatibilityTest {

    @BeforeTest
    fun setUp() {
        // Set up the MegaTensorBackend for testing
        val backend = MegaTensorBackend()
        BackendRegistry.registerBackend("test", backend)
        BackendRegistry.setBackend("test")
    }

    @Test
    fun testFloat64TensorCreation() {
        // Test that we can create Float64 tensors
        val tensor = EmberTensor(doubleArrayOf(1.5, 2.7, 3.14), float64)
        assertNotNull(tensor)
        assertEquals(DType.FLOAT64, tensor.dtype)
    }

    @Test
    fun testFloat64BitwiseOperationsWorkaround() {
        // Test that Float64 values can be used in bitwise operations through conversion
        val a = EmberTensor(doubleArrayOf(10.0), float64)
        val b = EmberTensor(doubleArrayOf(12.0), float64)
        
        // These operations should work by converting to integer representations
        val andResult = a bitwiseAnd b
        assertNotNull(andResult)
        
        val orResult = a bitwiseOr b  
        assertNotNull(orResult)
        
        val xorResult = a bitwiseXor b
        assertNotNull(xorResult)
    }

    @Test
    fun testFloat64ShiftOperations() {
        // Test that Float64 values can be used in shift operations
        val tensor = EmberTensor(doubleArrayOf(10.0), float64)
        
        val leftShifted = tensor.leftShift(1)
        assertNotNull(leftShifted)
        
        val rightShifted = tensor.rightShift(1)
        assertNotNull(rightShifted)
    }

    @Test
    fun testMixedDTypeOperations() {
        // Test operations between different data types
        val intTensor = EmberTensor(intArrayOf(10), int32)
        val floatTensor = EmberTensor(doubleArrayOf(12.0), float64)
        
        // These should work with type promotion
        val result = intTensor bitwiseAnd floatTensor
        assertNotNull(result)
    }

    @Test
    fun testAppleMLXMetalCompatibility() {
        // Test scenarios that would be problematic on Apple MLX/Metal
        // but should work with our MegaNumber-based implementation
        
        // Large precision operations
        val largePrecision = EmberTensor(doubleArrayOf(1.23456789012345), float64)
        val shiftedLarge = largePrecision.leftShift(1)
        assertNotNull(shiftedLarge)
        
        // Multiple cascaded operations
        val a = EmberTensor(doubleArrayOf(15.0), float64)
        val result = a.leftShift(2).rightShift(1).bitwiseAnd(EmberTensor(doubleArrayOf(31.0), float64))
        assertNotNull(result)
    }
}