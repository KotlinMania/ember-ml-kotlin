package ai.solace.ember.backend

import ai.solace.ember.tensor.common.DType
import ai.solace.ember.backend.storage.TensorStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.*

/**
 * Tests for tensor creation utilities.
 * 
 * These tests verify that tensor creation functions work correctly
 * and create tensors with the expected properties and values.
 */
class TensorCreationUtilitiesTest {

    private val backend = OptimizedMegaTensorBackend()
    private val tensorUtils = TensorCreationUtilities(backend)

    @Test
    fun testZerosCreation() {
        val shape = intArrayOf(2, 3)
        val tensor = tensorUtils.zeros(shape, DType.FLOAT32) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify tensor properties
        assertEquals(6, tensor.size)
        assertEquals(DType.FLOAT32, tensor.dtype)
        assertTrue(tensor.shape.contentEquals(shape))
        assertTrue(tensor.storage is TensorStorage.NativeFloatStorage)
        
        // Verify all values are zero
        val storage = tensor.storage as TensorStorage.NativeFloatStorage
        for (i in 0 until tensor.size) {
            assertEquals(0.0f, storage.get(i))
        }
    }

    @Test
    fun testZerosCreationBoolean() {
        val shape = intArrayOf(4)
        val tensor = tensorUtils.zeros(shape, DType.BOOL) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify tensor properties
        assertEquals(4, tensor.size)
        assertEquals(DType.BOOL, tensor.dtype)
        assertTrue(tensor.storage is TensorStorage.PackedBooleanStorage)
        
        // Verify all values are false (zero for boolean)
        val storage = tensor.storage as TensorStorage.PackedBooleanStorage
        for (i in 0 until tensor.size) {
            assertEquals(false, storage.get(i))
        }
    }

    @Test
    fun testOnesCreation() {
        val shape = intArrayOf(3, 2)
        val tensor = tensorUtils.ones(shape, DType.INT32) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify tensor properties
        assertEquals(6, tensor.size)
        assertEquals(DType.INT32, tensor.dtype)
        assertTrue(tensor.shape.contentEquals(shape))
        assertTrue(tensor.storage is TensorStorage.NativeIntStorage)
        
        // Verify all values are one
        val storage = tensor.storage as TensorStorage.NativeIntStorage
        for (i in 0 until tensor.size) {
            assertEquals(1, storage.get(i))
        }
    }

    @Test
    fun testOnesCreationBoolean() {
        val shape = intArrayOf(3)
        val tensor = tensorUtils.ones(shape, DType.BOOL) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify tensor properties
        assertEquals(3, tensor.size)
        assertEquals(DType.BOOL, tensor.dtype)
        assertTrue(tensor.storage is TensorStorage.PackedBooleanStorage)
        
        // Verify all values are true (one for boolean)
        val storage = tensor.storage as TensorStorage.PackedBooleanStorage
        for (i in 0 until tensor.size) {
            assertEquals(true, storage.get(i))
        }
    }

    @Test
    fun testFullCreation() {
        val shape = intArrayOf(2, 2)
        val fillValue = 42.0
        val tensor = tensorUtils.full(shape, fillValue, DType.FLOAT64) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify tensor properties
        assertEquals(4, tensor.size)
        assertEquals(DType.FLOAT64, tensor.dtype)
        assertTrue(tensor.shape.contentEquals(shape))
        assertTrue(tensor.storage is TensorStorage.NativeDoubleStorage)
        
        // Verify all values are the fill value
        val storage = tensor.storage as TensorStorage.NativeDoubleStorage
        for (i in 0 until tensor.size) {
            assertEquals(fillValue, storage.get(i))
        }
    }

    @Test
    fun testArangeCreation() {
        val tensor = tensorUtils.arange(0.0, 10.0, 2.0, DType.FLOAT64) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify tensor properties
        assertEquals(5, tensor.size) // [0, 2, 4, 6, 8]
        assertEquals(DType.FLOAT64, tensor.dtype)
        assertTrue(tensor.shape.contentEquals(intArrayOf(5)))
        assertTrue(tensor.storage is TensorStorage.NativeDoubleStorage)
        
        // Verify values
        val storage = tensor.storage as TensorStorage.NativeDoubleStorage
        assertEquals(0.0, storage.get(0))
        assertEquals(2.0, storage.get(1))
        assertEquals(4.0, storage.get(2))
        assertEquals(6.0, storage.get(3))
        assertEquals(8.0, storage.get(4))
    }

    @Test
    fun testArangeCreationNegativeStep() {
        val tensor = tensorUtils.arange(10.0, 0.0, -2.0, DType.FLOAT64) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify tensor properties
        assertEquals(5, tensor.size) // [10, 8, 6, 4, 2]
        assertEquals(DType.FLOAT64, tensor.dtype)
        
        // Verify values
        val storage = tensor.storage as TensorStorage.NativeDoubleStorage
        assertEquals(10.0, storage.get(0))
        assertEquals(8.0, storage.get(1))
        assertEquals(6.0, storage.get(2))
        assertEquals(4.0, storage.get(3))
        assertEquals(2.0, storage.get(4))
    }

    @Test
    fun testArangeEmptyRange() {
        val tensor = tensorUtils.arange(5.0, 5.0, 1.0, DType.FLOAT64) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Should create empty tensor
        assertEquals(0, tensor.size)
        assertTrue(tensor.shape.contentEquals(intArrayOf(0)))
    }

    @Test
    fun testLinspaceCreation() {
        val tensor = tensorUtils.linspace(0.0, 10.0, 5, DType.FLOAT64) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify tensor properties
        assertEquals(5, tensor.size)
        assertEquals(DType.FLOAT64, tensor.dtype)
        assertTrue(tensor.shape.contentEquals(intArrayOf(5)))
        
        // Verify values [0, 2.5, 5, 7.5, 10]
        val storage = tensor.storage as TensorStorage.NativeDoubleStorage
        assertEquals(0.0, storage.get(0))
        assertEquals(2.5, storage.get(1))
        assertEquals(5.0, storage.get(2))
        assertEquals(7.5, storage.get(3))
        assertEquals(10.0, storage.get(4))
    }

    @Test
    fun testLinspaceOneElement() {
        val tensor = tensorUtils.linspace(5.0, 10.0, 1, DType.FLOAT64) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Should contain only the start value
        assertEquals(1, tensor.size)
        val storage = tensor.storage as TensorStorage.NativeDoubleStorage
        assertEquals(5.0, storage.get(0))
    }

    @Test
    fun testEyeCreation() {
        val tensor = tensorUtils.eye(3, DType.FLOAT32) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify tensor properties
        assertEquals(9, tensor.size) // 3x3 matrix
        assertEquals(DType.FLOAT32, tensor.dtype)
        assertTrue(tensor.shape.contentEquals(intArrayOf(3, 3)))
        assertTrue(tensor.storage is TensorStorage.NativeFloatStorage)
        
        // Verify identity matrix pattern
        val storage = tensor.storage as TensorStorage.NativeFloatStorage
        val expected = floatArrayOf(
            1.0f, 0.0f, 0.0f,  // Row 0
            0.0f, 1.0f, 0.0f,  // Row 1
            0.0f, 0.0f, 1.0f   // Row 2
        )
        
        for (i in 0 until 9) {
            assertEquals(expected[i], storage.get(i))
        }
    }

    @Test
    fun testRandomUniformCreation() {
        val shape = intArrayOf(100) // Use enough samples for statistical testing
        val low = 0.0
        val high = 1.0
        val tensor = tensorUtils.randomUniform(shape, low, high, DType.FLOAT64) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify tensor properties
        assertEquals(100, tensor.size)
        assertEquals(DType.FLOAT64, tensor.dtype)
        assertTrue(tensor.shape.contentEquals(shape))
        assertTrue(tensor.storage is TensorStorage.NativeDoubleStorage)
        
        // Verify all values are in range and not all the same
        val storage = tensor.storage as TensorStorage.NativeDoubleStorage
        var allSame = true
        val firstValue = storage.get(0)
        
        for (i in 0 until tensor.size) {
            val value = storage.get(i)
            assertTrue(value >= low, "Value $value should be >= $low")
            assertTrue(value < high, "Value $value should be < $high")
            if (value != firstValue) allSame = false
        }
        
        // Should not be all the same value (very low probability)
        assertTrue(!allSame, "Random values should not all be the same")
    }

    @Test
    fun testRandomNormalCreation() {
        val shape = intArrayOf(1000) // Use many samples for statistical testing
        val mean = 5.0
        val std = 2.0
        val tensor = tensorUtils.randomNormal(shape, mean, std, DType.FLOAT64) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify tensor properties
        assertEquals(1000, tensor.size)
        assertEquals(DType.FLOAT64, tensor.dtype)
        assertTrue(tensor.storage is TensorStorage.NativeDoubleStorage)
        
        // Calculate sample mean and standard deviation
        val storage = tensor.storage as TensorStorage.NativeDoubleStorage
        var sum = 0.0
        for (i in 0 until tensor.size) {
            sum += storage.get(i)
        }
        val sampleMean = sum / tensor.size
        
        var sumSquaredDiffs = 0.0
        for (i in 0 until tensor.size) {
            val diff = storage.get(i) - sampleMean
            sumSquaredDiffs += diff * diff
        }
        val sampleStd = sqrt(sumSquaredDiffs / (tensor.size - 1))
        
        // Check that sample statistics are reasonably close to expected values
        // (with generous tolerance due to randomness)
        assertEquals(mean, sampleMean, 0.2) // Mean should be close
        assertEquals(std, sampleStd, 0.3)   // Std should be close
    }

    @Test
    fun testRandomIntCreation() {
        val shape = intArrayOf(50)
        val low = 10
        val high = 20
        val tensor = tensorUtils.randomInt(shape, low, high, DType.INT32) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify tensor properties
        assertEquals(50, tensor.size)
        assertEquals(DType.INT32, tensor.dtype)
        assertTrue(tensor.storage is TensorStorage.NativeIntStorage)
        
        // Verify all values are in range
        val storage = tensor.storage as TensorStorage.NativeIntStorage
        var allSame = true
        val firstValue = storage.get(0)
        
        for (i in 0 until tensor.size) {
            val value = storage.get(i)
            assertTrue(value >= low, "Value $value should be >= $low")
            assertTrue(value < high, "Value $value should be < $high")
            if (value != firstValue) allSame = false
        }
        
        // Should not be all the same value (very low probability)
        assertTrue(!allSame, "Random values should not all be the same")
    }

    @Test
    fun testZerosLike() {
        // Create a reference tensor
        val refData = intArrayOf(1, 2, 3, 4)
        val refShape = intArrayOf(2, 2)
        val refTensor = backend.createTensor(refData, refShape, DType.INT32)
        
        // Create zeros like the reference
        val tensor = tensorUtils.zerosLike(refTensor) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify tensor properties match reference
        assertEquals(4, tensor.size)
        assertEquals(DType.INT32, tensor.dtype)
        assertTrue(tensor.shape.contentEquals(refShape))
        
        // Verify all values are zero
        val storage = tensor.storage as TensorStorage.NativeIntStorage
        for (i in 0 until tensor.size) {
            assertEquals(0, storage.get(i))
        }
    }

    @Test
    fun testOnesLike() {
        // Create a reference tensor
        val refData = doubleArrayOf(3.14, 2.71)
        val refShape = intArrayOf(2)
        val refTensor = backend.createTensor(refData, refShape, DType.FLOAT64)
        
        // Create ones like the reference
        val tensor = tensorUtils.onesLike(refTensor) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify tensor properties match reference
        assertEquals(2, tensor.size)
        assertEquals(DType.FLOAT64, tensor.dtype)
        assertTrue(tensor.shape.contentEquals(refShape))
        
        // Verify all values are one
        val storage = tensor.storage as TensorStorage.NativeDoubleStorage
        for (i in 0 until tensor.size) {
            assertEquals(1.0, storage.get(i))
        }
    }

    @Test
    fun testFullLike() {
        // Create a reference tensor
        val refData = booleanArrayOf(true, false, true)
        val refShape = intArrayOf(3)
        val refTensor = backend.createTensor(refData, refShape, DType.BOOL)
        
        // Create full like the reference with a specific value
        val fillValue = false
        val tensor = tensorUtils.fullLike(refTensor, fillValue) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify tensor properties match reference
        assertEquals(3, tensor.size)
        assertEquals(DType.BOOL, tensor.dtype)
        assertTrue(tensor.shape.contentEquals(refShape))
        
        // Verify all values are the fill value
        val storage = tensor.storage as TensorStorage.PackedBooleanStorage
        for (i in 0 until tensor.size) {
            assertEquals(fillValue, storage.get(i))
        }
    }

    @Test
    fun testTensorCreationWithUByteType() {
        val shape = intArrayOf(4)
        val tensor = tensorUtils.ones(shape, DType.UINT8) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify tensor properties
        assertEquals(4, tensor.size)
        assertEquals(DType.UINT8, tensor.dtype)
        assertTrue(tensor.storage is TensorStorage.NativeUByteStorage)
        
        // Verify all values are one (as UByte)
        val storage = tensor.storage as TensorStorage.NativeUByteStorage
        for (i in 0 until tensor.size) {
            assertEquals(1u.toUByte(), storage.get(i))
        }
    }
}