package ai.solace.ember.backend

import ai.solace.ember.tensor.common.DType
import ai.solace.ember.backend.storage.TensorStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.*

/**
 * Tests for mathematical operations on optimized tensors.
 * 
 * These tests verify that the mathematical functions work correctly
 * with the hybrid storage system and provide the expected results.
 */
class MathematicalOperationsTest {

    private val backend = OptimizedMegaTensorBackend()
    private val mathOps = MathematicalOperations(backend)

    @Test
    fun testSinOperation() {
        val data = doubleArrayOf(0.0, PI/2, PI, 3*PI/2)
        val shape = intArrayOf(4)
        
        val tensor = backend.createTensor(data, shape, DType.FLOAT64)
        val result = mathOps.sin(tensor) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify result properties
        assertEquals(4, result.size)
        assertEquals(DType.FLOAT64, result.dtype)
        assertTrue(result.storage is TensorStorage.NativeDoubleStorage)
        
        // Verify mathematical results (with tolerance for floating point)
        val storage = result.storage as TensorStorage.NativeDoubleStorage
        assertEquals(0.0, storage.get(0), 1e-10)      // sin(0) = 0
        assertEquals(1.0, storage.get(1), 1e-10)      // sin(π/2) = 1
        assertEquals(0.0, storage.get(2), 1e-10)      // sin(π) = 0
        assertEquals(-1.0, storage.get(3), 1e-10)     // sin(3π/2) = -1
    }

    @Test
    fun testCosOperation() {
        val data = doubleArrayOf(0.0, PI/2, PI, 3*PI/2)
        val shape = intArrayOf(4)
        
        val tensor = backend.createTensor(data, shape, DType.FLOAT64)
        val result = mathOps.cos(tensor) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify result properties
        assertEquals(4, result.size)
        assertEquals(DType.FLOAT64, result.dtype)
        assertTrue(result.storage is TensorStorage.NativeDoubleStorage)
        
        // Verify mathematical results (with tolerance for floating point)
        val storage = result.storage as TensorStorage.NativeDoubleStorage
        assertEquals(1.0, storage.get(0), 1e-10)      // cos(0) = 1
        assertEquals(0.0, storage.get(1), 1e-10)      // cos(π/2) = 0
        assertEquals(-1.0, storage.get(2), 1e-10)     // cos(π) = -1
        assertEquals(0.0, storage.get(3), 1e-10)      // cos(3π/2) = 0
    }

    @Test
    fun testExpOperation() {
        val data = doubleArrayOf(0.0, 1.0, 2.0, -1.0)
        val shape = intArrayOf(4)
        
        val tensor = backend.createTensor(data, shape, DType.FLOAT64)
        val result = mathOps.exp(tensor) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify result properties
        assertEquals(4, result.size)
        assertEquals(DType.FLOAT64, result.dtype)
        assertTrue(result.storage is TensorStorage.NativeDoubleStorage)
        
        // Verify mathematical results
        val storage = result.storage as TensorStorage.NativeDoubleStorage
        assertEquals(1.0, storage.get(0), 1e-10)           // exp(0) = 1
        assertEquals(E, storage.get(1), 1e-10)             // exp(1) = e
        assertEquals(E * E, storage.get(2), 1e-10)         // exp(2) = e²
        assertEquals(1.0 / E, storage.get(3), 1e-10)       // exp(-1) = 1/e
    }

    @Test
    fun testLogOperation() {
        val data = doubleArrayOf(1.0, E, E*E, 1.0/E)
        val shape = intArrayOf(4)
        
        val tensor = backend.createTensor(data, shape, DType.FLOAT64)
        val result = mathOps.log(tensor) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify result properties
        assertEquals(4, result.size)
        assertEquals(DType.FLOAT64, result.dtype)
        assertTrue(result.storage is TensorStorage.NativeDoubleStorage)
        
        // Verify mathematical results
        val storage = result.storage as TensorStorage.NativeDoubleStorage
        assertEquals(0.0, storage.get(0), 1e-10)      // ln(1) = 0
        assertEquals(1.0, storage.get(1), 1e-10)      // ln(e) = 1
        assertEquals(2.0, storage.get(2), 1e-10)      // ln(e²) = 2
        assertEquals(-1.0, storage.get(3), 1e-10)     // ln(1/e) = -1
    }

    @Test
    fun testSqrtOperation() {
        val data = doubleArrayOf(0.0, 1.0, 4.0, 9.0, 16.0)
        val shape = intArrayOf(5)
        
        val tensor = backend.createTensor(data, shape, DType.FLOAT64)
        val result = mathOps.sqrt(tensor) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify result properties
        assertEquals(5, result.size)
        assertEquals(DType.FLOAT64, result.dtype)
        assertTrue(result.storage is TensorStorage.NativeDoubleStorage)
        
        // Verify mathematical results
        val storage = result.storage as TensorStorage.NativeDoubleStorage
        assertEquals(0.0, storage.get(0), 1e-10)      // sqrt(0) = 0
        assertEquals(1.0, storage.get(1), 1e-10)      // sqrt(1) = 1
        assertEquals(2.0, storage.get(2), 1e-10)      // sqrt(4) = 2
        assertEquals(3.0, storage.get(3), 1e-10)      // sqrt(9) = 3
        assertEquals(4.0, storage.get(4), 1e-10)      // sqrt(16) = 4
    }

    @Test
    fun testPowOperation() {
        val data = doubleArrayOf(2.0, 3.0, 4.0, 5.0)
        val shape = intArrayOf(4)
        val exponent = 3.0
        
        val tensor = backend.createTensor(data, shape, DType.FLOAT64)
        val result = mathOps.pow(tensor, exponent) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify result properties
        assertEquals(4, result.size)
        assertEquals(DType.FLOAT64, result.dtype)
        assertTrue(result.storage is TensorStorage.NativeDoubleStorage)
        
        // Verify mathematical results
        val storage = result.storage as TensorStorage.NativeDoubleStorage
        assertEquals(8.0, storage.get(0), 1e-10)      // 2³ = 8
        assertEquals(27.0, storage.get(1), 1e-10)     // 3³ = 27
        assertEquals(64.0, storage.get(2), 1e-10)     // 4³ = 64
        assertEquals(125.0, storage.get(3), 1e-10)    // 5³ = 125
    }

    @Test
    fun testAbsOperation() {
        val data = intArrayOf(-5, -3, 0, 3, 5)
        val shape = intArrayOf(5)
        
        val tensor = backend.createTensor(data, shape, DType.INT32)
        val result = mathOps.abs(tensor) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify result properties
        assertEquals(5, result.size)
        assertEquals(DType.FLOAT64, result.dtype) // Math operations promote to double
        assertTrue(result.storage is TensorStorage.NativeDoubleStorage)
        
        // Verify mathematical results
        val storage = result.storage as TensorStorage.NativeDoubleStorage
        assertEquals(5.0, storage.get(0), 1e-10)      // abs(-5) = 5
        assertEquals(3.0, storage.get(1), 1e-10)      // abs(-3) = 3
        assertEquals(0.0, storage.get(2), 1e-10)      // abs(0) = 0
        assertEquals(3.0, storage.get(3), 1e-10)      // abs(3) = 3
        assertEquals(5.0, storage.get(4), 1e-10)      // abs(5) = 5
    }

    @Test
    fun testGreaterThanComparison() {
        val data1 = intArrayOf(1, 3, 5, 7)
        val data2 = intArrayOf(2, 3, 4, 8)
        val shape = intArrayOf(4)
        
        val tensor1 = backend.createTensor(data1, shape, DType.INT32)
        val tensor2 = backend.createTensor(data2, shape, DType.INT32)
        
        val result = mathOps.greaterThan(tensor1, tensor2) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify result properties
        assertEquals(4, result.size)
        assertEquals(DType.BOOL, result.dtype)
        assertTrue(result.storage is TensorStorage.PackedBooleanStorage)
        
        // Verify comparison results
        val storage = result.storage as TensorStorage.PackedBooleanStorage
        assertEquals(false, storage.get(0))    // 1 > 2 = false
        assertEquals(false, storage.get(1))    // 3 > 3 = false
        assertEquals(true, storage.get(2))     // 5 > 4 = true
        assertEquals(false, storage.get(3))    // 7 > 8 = false
    }

    @Test
    fun testLessThanComparison() {
        val data1 = intArrayOf(1, 3, 5, 7)
        val data2 = intArrayOf(2, 3, 4, 8)
        val shape = intArrayOf(4)
        
        val tensor1 = backend.createTensor(data1, shape, DType.INT32)
        val tensor2 = backend.createTensor(data2, shape, DType.INT32)
        
        val result = mathOps.lessThan(tensor1, tensor2) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify result properties
        assertEquals(4, result.size)
        assertEquals(DType.BOOL, result.dtype)
        assertTrue(result.storage is TensorStorage.PackedBooleanStorage)
        
        // Verify comparison results
        val storage = result.storage as TensorStorage.PackedBooleanStorage
        assertEquals(true, storage.get(0))     // 1 < 2 = true
        assertEquals(false, storage.get(1))    // 3 < 3 = false
        assertEquals(false, storage.get(2))    // 5 < 4 = false
        assertEquals(true, storage.get(3))     // 7 < 8 = true
    }

    @Test
    fun testEqualComparison() {
        val data1 = intArrayOf(1, 3, 5, 7)
        val data2 = intArrayOf(2, 3, 4, 7)
        val shape = intArrayOf(4)
        
        val tensor1 = backend.createTensor(data1, shape, DType.INT32)
        val tensor2 = backend.createTensor(data2, shape, DType.INT32)
        
        val result = mathOps.equal(tensor1, tensor2) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Verify result properties
        assertEquals(4, result.size)
        assertEquals(DType.BOOL, result.dtype)
        assertTrue(result.storage is TensorStorage.PackedBooleanStorage)
        
        // Verify comparison results
        val storage = result.storage as TensorStorage.PackedBooleanStorage
        assertEquals(false, storage.get(0))    // 1 == 2 = false
        assertEquals(true, storage.get(1))     // 3 == 3 = true
        assertEquals(false, storage.get(2))    // 5 == 4 = false
        assertEquals(true, storage.get(3))     // 7 == 7 = true
    }

    @Test
    fun testMathOperationsWithDifferentDataTypes() {
        // Test that integer types get promoted to double for math operations
        val intData = intArrayOf(0, 1, 2, 3)
        val floatData = floatArrayOf(0f, 1f, 2f, 3f)
        val shape = intArrayOf(4)
        
        val intTensor = backend.createTensor(intData, shape, DType.INT32)
        val floatTensor = backend.createTensor(floatData, shape, DType.FLOAT32)
        
        val intResult = mathOps.sin(intTensor) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        val floatResult = mathOps.sin(floatTensor) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Integer tensor should be promoted to FLOAT64
        assertEquals(DType.FLOAT64, intResult.dtype)
        assertTrue(intResult.storage is TensorStorage.NativeDoubleStorage)
        
        // Float tensor should maintain FLOAT32
        assertEquals(DType.FLOAT32, floatResult.dtype)
        assertTrue(floatResult.storage is TensorStorage.NativeFloatStorage)
    }
    
    @Test
    fun testMathOperationsWithBooleanTensor() {
        val data = booleanArrayOf(true, false, true, false)
        val shape = intArrayOf(4)
        
        val tensor = backend.createTensor(data, shape, DType.BOOL)
        val result = mathOps.abs(tensor) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        // Boolean tensor should be promoted to FLOAT64
        assertEquals(DType.FLOAT64, result.dtype)
        assertTrue(result.storage is TensorStorage.NativeDoubleStorage)
        
        // Verify boolean conversion results
        val storage = result.storage as TensorStorage.NativeDoubleStorage
        assertEquals(1.0, storage.get(0), 1e-10)      // abs(true) = 1
        assertEquals(0.0, storage.get(1), 1e-10)      // abs(false) = 0
        assertEquals(1.0, storage.get(2), 1e-10)      // abs(true) = 1
        assertEquals(0.0, storage.get(3), 1e-10)      // abs(false) = 0
    }
}