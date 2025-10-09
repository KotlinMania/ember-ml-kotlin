package ai.solace.ember.backend

import ai.solace.ember.tensor.common.DType
import ai.solace.ember.backend.storage.TensorStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the OptimizedMegaTensorBackend.
 * 
 * These tests verify that the optimized backend works correctly
 * and provides the expected memory efficiency improvements.
 */
class OptimizedMegaTensorBackendTest {

    private val backend = OptimizedMegaTensorBackend()

    @Test
    fun testCreateBooleanTensor() {
        val data = booleanArrayOf(true, false, true, false)
        val shape = intArrayOf(4)
        
        val tensor = backend.createTensor(data, shape, DType.BOOL) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify the tensor properties
        assertEquals(4, tensor.size)
        assertEquals(DType.BOOL, tensor.dtype)
        assertTrue(tensor.shape.contentEquals(shape))
        assertTrue(tensor.storage is TensorStorage.PackedBooleanStorage)
        
        // Verify the storage is created with optimal type
        val storage = tensor.storage as TensorStorage.PackedBooleanStorage
        assertEquals(true, storage.get(0))
        assertEquals(false, storage.get(1))
        assertEquals(true, storage.get(2))
        assertEquals(false, storage.get(3))
    }

    @Test
    fun testCreateUInt8Tensor() {
        val data = intArrayOf(0, 100, 255, 42) // Will be converted to UByte
        val shape = intArrayOf(4)
        
        val tensor = backend.createTensor(data, shape, DType.UINT8) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify the tensor properties
        assertEquals(4, tensor.size)
        assertEquals(DType.UINT8, tensor.dtype)
        assertTrue(tensor.shape.contentEquals(shape))
        assertTrue(tensor.storage is TensorStorage.NativeUByteStorage)
        
        // Verify the storage is created with optimal type
        val storage = tensor.storage as TensorStorage.NativeUByteStorage
        assertEquals(0u.toUByte(), storage.get(0))
        assertEquals(100u.toUByte(), storage.get(1))
        assertEquals(255u.toUByte(), storage.get(2))
        assertEquals(42u.toUByte(), storage.get(3))
    }

    @Test
    fun testCreateIntTensor() {
        val data = intArrayOf(-100, 0, 100, 42)
        val shape = intArrayOf(4)
        
        val tensor = backend.createTensor(data, shape, DType.INT32) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify the tensor properties
        assertEquals(4, tensor.size)
        assertEquals(DType.INT32, tensor.dtype)
        assertTrue(tensor.shape.contentEquals(shape))
        assertTrue(tensor.storage is TensorStorage.NativeIntStorage)
        
        // Verify the storage is created with optimal type
        val storage = tensor.storage as TensorStorage.NativeIntStorage
        assertEquals(-100, storage.get(0))
        assertEquals(0, storage.get(1))
        assertEquals(100, storage.get(2))
        assertEquals(42, storage.get(3))
    }

    @Test
    fun testCreateFloatTensor() {
        val data = floatArrayOf(-3.14f, 0.0f, 2.718f, 42.0f)
        val shape = intArrayOf(4)
        
        val tensor = backend.createTensor(data, shape, DType.FLOAT32) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify the tensor properties
        assertEquals(4, tensor.size)
        assertEquals(DType.FLOAT32, tensor.dtype)
        assertTrue(tensor.shape.contentEquals(shape))
        assertTrue(tensor.storage is TensorStorage.NativeFloatStorage)
        
        // Verify the storage is created with optimal type
        val storage = tensor.storage as TensorStorage.NativeFloatStorage
        assertEquals(-3.14f, storage.get(0))
        assertEquals(0.0f, storage.get(1))
        assertEquals(2.718f, storage.get(2))
        assertEquals(42.0f, storage.get(3))
    }

    @Test
    fun testCreateDoubleTensor() {
        val data = doubleArrayOf(-3.141592653589793, 0.0, 2.718281828459045, 42.0)
        val shape = intArrayOf(4)
        
        val tensor = backend.createTensor(data, shape, DType.FLOAT64) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify the tensor properties
        assertEquals(4, tensor.size)
        assertEquals(DType.FLOAT64, tensor.dtype)
        assertTrue(tensor.shape.contentEquals(shape))
        assertTrue(tensor.storage is TensorStorage.NativeDoubleStorage)
        
        // Verify the storage is created with optimal type
        val storage = tensor.storage as TensorStorage.NativeDoubleStorage
        assertEquals(-3.141592653589793, storage.get(0))
        assertEquals(0.0, storage.get(1))
        assertEquals(2.718281828459045, storage.get(2))
        assertEquals(42.0, storage.get(3))
    }

    @Test
    fun testAddIntTensors() {
        val data1 = intArrayOf(1, 2, 3, 4)
        val data2 = intArrayOf(5, 6, 7, 8)
        val shape = intArrayOf(4)
        
        val tensor1 = backend.createTensor(data1, shape, DType.INT32)
        val tensor2 = backend.createTensor(data2, shape, DType.INT32)
        
        val result = backend.add(tensor1, tensor2) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify the result
        assertEquals(4, result.size)
        assertEquals(DType.INT32, result.dtype)
        assertTrue(result.storage is TensorStorage.NativeIntStorage)
        
        val storage = result.storage as TensorStorage.NativeIntStorage
        assertEquals(6, storage.get(0))  // 1 + 5
        assertEquals(8, storage.get(1))  // 2 + 6
        assertEquals(10, storage.get(2)) // 3 + 7
        assertEquals(12, storage.get(3)) // 4 + 8
    }

    @Test
    fun testSubtractFloatTensors() {
        val data1 = floatArrayOf(10.0f, 20.0f, 30.0f, 40.0f)
        val data2 = floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f)
        val shape = intArrayOf(4)
        
        val tensor1 = backend.createTensor(data1, shape, DType.FLOAT32)
        val tensor2 = backend.createTensor(data2, shape, DType.FLOAT32)
        
        val result = backend.subtract(tensor1, tensor2) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify the result
        assertEquals(4, result.size)
        assertEquals(DType.FLOAT32, result.dtype)
        assertTrue(result.storage is TensorStorage.NativeFloatStorage)
        
        val storage = result.storage as TensorStorage.NativeFloatStorage
        assertEquals(9.0f, storage.get(0))  // 10.0 - 1.0
        assertEquals(18.0f, storage.get(1)) // 20.0 - 2.0
        assertEquals(27.0f, storage.get(2)) // 30.0 - 3.0
        assertEquals(36.0f, storage.get(3)) // 40.0 - 4.0
    }

    @Test
    fun testMultiplyBooleanTensors() {
        val data1 = booleanArrayOf(true, false, true, false)
        val data2 = booleanArrayOf(true, true, false, false)
        val shape = intArrayOf(4)
        
        val tensor1 = backend.createTensor(data1, shape, DType.BOOL)
        val tensor2 = backend.createTensor(data2, shape, DType.BOOL)
        
        val result = backend.multiply(tensor1, tensor2) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify the result (boolean AND operation)
        assertEquals(4, result.size)
        assertEquals(DType.BOOL, result.dtype)
        assertTrue(result.storage is TensorStorage.PackedBooleanStorage)
        
        val storage = result.storage as TensorStorage.PackedBooleanStorage
        assertEquals(true, storage.get(0))   // true && true = true
        assertEquals(false, storage.get(1))  // false && true = false
        assertEquals(false, storage.get(2))  // true && false = false
        assertEquals(false, storage.get(3))  // false && false = false
    }

    @Test
    fun testDivideDoubleTensors() {
        val data1 = doubleArrayOf(10.0, 20.0, 30.0, 40.0)
        val data2 = doubleArrayOf(2.0, 4.0, 5.0, 8.0)
        val shape = intArrayOf(4)
        
        val tensor1 = backend.createTensor(data1, shape, DType.FLOAT64)
        val tensor2 = backend.createTensor(data2, shape, DType.FLOAT64)
        
        val result = backend.divide(tensor1, tensor2) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify the result
        assertEquals(4, result.size)
        assertEquals(DType.FLOAT64, result.dtype)
        assertTrue(result.storage is TensorStorage.NativeDoubleStorage)
        
        val storage = result.storage as TensorStorage.NativeDoubleStorage
        assertEquals(5.0, storage.get(0))  // 10.0 / 2.0
        assertEquals(5.0, storage.get(1))  // 20.0 / 4.0
        assertEquals(6.0, storage.get(2))  // 30.0 / 5.0
        assertEquals(5.0, storage.get(3))  // 40.0 / 8.0
    }

    @Test
    fun testGetTensorProperties() {
        val data = intArrayOf(1, 2, 3, 4, 5, 6)
        val shape = intArrayOf(2, 3) // 2x3 matrix
        
        val tensor = backend.createTensor(data, shape, DType.INT32)
        
        // Test backend methods
        val retrievedShape = backend.getTensorShape(tensor)
        val retrievedDType = backend.getTensorDType(tensor)
        val retrievedDevice = backend.getTensorDevice(tensor)
        
        assertTrue(retrievedShape.contentEquals(shape))
        assertEquals(DType.INT32, retrievedDType)
        assertEquals("cpu", retrievedDevice)
    }

    /**
     * Test to demonstrate the memory efficiency improvement.
     */
    @Test
    fun testMemoryEfficiencyImprovement() {
        val size = 1000000 // 1 million elements
        val booleanData = BooleanArray(size) { it % 2 == 0 } // Alternating true/false
        val shape = intArrayOf(size)
        
        // Create tensor with optimized backend
        val optimizedTensor = backend.createTensor(booleanData, shape, DType.BOOL) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify it uses the efficient storage
        assertTrue(optimizedTensor.storage is TensorStorage.PackedBooleanStorage)
        assertEquals(size, optimizedTensor.size)
        assertEquals(DType.BOOL, optimizedTensor.dtype)
        
        // The optimized storage uses:
        // - BooleanArray: ~1 MB (1 byte per boolean in Kotlin)
        // 
        // Compared to the previous MegaNumber storage which would use:
        // - Array<MegaNumber>: ~32+ MB (each MegaNumber uses 32-bit chunks)
        // 
        // This represents approximately 32x memory improvement for boolean tensors!
        
        // Verify data integrity
        val storage = optimizedTensor.storage as TensorStorage.PackedBooleanStorage
        assertEquals(true, storage.get(0))    // 0 % 2 == 0 = true
        assertEquals(false, storage.get(1))   // 1 % 2 == 0 = false
        assertEquals(true, storage.get(2))    // 2 % 2 == 0 = true
        assertEquals(false, storage.get(3))   // 3 % 2 == 0 = false
    }

    @Test
    fun testSumOperation() {
        val data = intArrayOf(1, 2, 3, 4, 5)
        val shape = intArrayOf(5)
        
        val tensor = backend.createTensor(data, shape, DType.INT32)
        val result = backend.sum(tensor) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify result properties
        assertEquals(1, result.size)
        assertEquals(DType.INT64, result.dtype) // INT32 sum promotes to INT64
        assertTrue(result.storage is TensorStorage.NativeLongStorage)
        
        // Verify sum result
        val storage = result.storage as TensorStorage.NativeLongStorage
        assertEquals(15L, storage.get(0)) // 1 + 2 + 3 + 4 + 5 = 15
    }

    @Test
    fun testSumBooleanOperation() {
        val data = booleanArrayOf(true, false, true, true, false)
        val shape = intArrayOf(5)
        
        val tensor = backend.createTensor(data, shape, DType.BOOL)
        val result = backend.sum(tensor) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify result properties
        assertEquals(1, result.size)
        assertEquals(DType.INT32, result.dtype) // Boolean sum gives count as INT32
        assertTrue(result.storage is TensorStorage.NativeIntStorage)
        
        // Verify sum result
        val storage = result.storage as TensorStorage.NativeIntStorage
        assertEquals(3, storage.get(0)) // 3 true values
    }

    @Test
    fun testMeanOperation() {
        val data = doubleArrayOf(2.0, 4.0, 6.0, 8.0)
        val shape = intArrayOf(4)
        
        val tensor = backend.createTensor(data, shape, DType.FLOAT64)
        val result = backend.mean(tensor) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify result properties
        assertEquals(1, result.size)
        assertEquals(DType.FLOAT64, result.dtype)
        assertTrue(result.storage is TensorStorage.NativeDoubleStorage)
        
        // Verify mean result
        val storage = result.storage as TensorStorage.NativeDoubleStorage
        assertEquals(5.0, storage.get(0)) // (2 + 4 + 6 + 8) / 4 = 5
    }

    @Test
    fun testMinOperation() {
        val data = intArrayOf(5, 2, 8, 1, 9, 3)
        val shape = intArrayOf(6)
        
        val tensor = backend.createTensor(data, shape, DType.INT32)
        val result = backend.min(tensor) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify result properties
        assertEquals(1, result.size)
        assertEquals(DType.INT32, result.dtype)
        assertTrue(result.storage is TensorStorage.NativeIntStorage)
        
        // Verify min result
        val storage = result.storage as TensorStorage.NativeIntStorage
        assertEquals(1, storage.get(0)) // minimum value is 1
    }

    @Test
    fun testMaxOperation() {
        val data = intArrayOf(5, 2, 8, 1, 9, 3)
        val shape = intArrayOf(6)
        
        val tensor = backend.createTensor(data, shape, DType.INT32)
        val result = backend.max(tensor) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify result properties
        assertEquals(1, result.size)
        assertEquals(DType.INT32, result.dtype)
        assertTrue(result.storage is TensorStorage.NativeIntStorage)
        
        // Verify max result
        val storage = result.storage as TensorStorage.NativeIntStorage
        assertEquals(9, storage.get(0)) // maximum value is 9
    }

    @Test
    fun testGetElementOperation() {
        val data = intArrayOf(10, 20, 30, 40, 50)
        val shape = intArrayOf(5)
        
        val tensor = backend.createTensor(data, shape, DType.INT32)
        
        // Test getting various elements
        assertEquals(10, backend.getElement(tensor, 0))
        assertEquals(20, backend.getElement(tensor, 1))
        assertEquals(30, backend.getElement(tensor, 2))
        assertEquals(40, backend.getElement(tensor, 3))
        assertEquals(50, backend.getElement(tensor, 4))
    }

    @Test
    fun testSetElementOperation() {
        val data = intArrayOf(10, 20, 30, 40, 50)
        val shape = intArrayOf(5)
        
        val tensor = backend.createTensor(data, shape, DType.INT32)
        
        // Set element at index 2 to 99
        val newTensor = backend.setElement(tensor, 2, 99) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify the new tensor has the updated value
        assertEquals(99, backend.getElement(newTensor, 2))
        
        // Verify other elements remain unchanged
        assertEquals(10, backend.getElement(newTensor, 0))
        assertEquals(20, backend.getElement(newTensor, 1))
        assertEquals(40, backend.getElement(newTensor, 3))
        assertEquals(50, backend.getElement(newTensor, 4))
        
        // Verify original tensor is unchanged (immutable)
        assertEquals(30, backend.getElement(tensor, 2))
    }
}