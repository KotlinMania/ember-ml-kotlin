package ai.solace.ember.backend

import ai.solace.ember.tensor.common.DType
import kotlin.test.*

/**
 * Test suite for the array manipulation operations implementation.
 * 
 * These tests validate the array manipulation operations added to the backend
 * including stacking, concatenation, repetition, and reshaping operations.
 */
class ArrayManipulationOperationsTest {
    
    private val backend = OptimizedMegaTensorBackend()
    private val arrayOps = ArrayManipulationOperations(backend)
    
    @Test
    fun testVStack() {
        val data1 = doubleArrayOf(1.0, 2.0)
        val data2 = doubleArrayOf(3.0, 4.0)
        val data3 = doubleArrayOf(5.0, 6.0)
        
        val tensor1 = backend.createTensor(data1, intArrayOf(1, 2), DType.FLOAT64) 
            as OptimizedMegaTensorBackend.OptimizedMegaTensor
        val tensor2 = backend.createTensor(data2, intArrayOf(1, 2), DType.FLOAT64) 
            as OptimizedMegaTensorBackend.OptimizedMegaTensor
        val tensor3 = backend.createTensor(data3, intArrayOf(1, 2), DType.FLOAT64) 
            as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        val result = arrayOps.vstack(listOf(tensor1, tensor2, tensor3)) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        assertEquals(DType.FLOAT64, result.dtype)
        assertEquals(6, result.size)
        assertTrue(result.shape.contentEquals(intArrayOf(3, 2)))
        // Result should be [[1,2], [3,4], [5,6]]
    }
    
    @Test
    fun testHStack1D() {
        val data1 = doubleArrayOf(1.0, 2.0)
        val data2 = doubleArrayOf(3.0, 4.0)
        
        val tensor1 = backend.createTensor(data1, intArrayOf(2), DType.FLOAT64) 
            as OptimizedMegaTensorBackend.OptimizedMegaTensor
        val tensor2 = backend.createTensor(data2, intArrayOf(2), DType.FLOAT64) 
            as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        val result = arrayOps.hstack(listOf(tensor1, tensor2)) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        assertEquals(DType.FLOAT64, result.dtype)
        assertEquals(4, result.size)
        assertTrue(result.shape.contentEquals(intArrayOf(4)))
        // Result should be [1,2,3,4]
    }
    
    @Test
    fun testHStack2D() {
        val data1 = doubleArrayOf(1.0, 2.0, 3.0, 4.0) // [[1,2], [3,4]]
        val data2 = doubleArrayOf(5.0, 6.0, 7.0, 8.0) // [[5,6], [7,8]]
        
        val tensor1 = backend.createTensor(data1, intArrayOf(2, 2), DType.FLOAT64) 
            as OptimizedMegaTensorBackend.OptimizedMegaTensor
        val tensor2 = backend.createTensor(data2, intArrayOf(2, 2), DType.FLOAT64) 
            as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        val result = arrayOps.hstack(listOf(tensor1, tensor2)) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        assertEquals(DType.FLOAT64, result.dtype)
        assertEquals(8, result.size)
        assertTrue(result.shape.contentEquals(intArrayOf(2, 4)))
        // Result should be [[1,2,5,6], [3,4,7,8]]
    }
    
    @Test
    fun testConcatenate1D() {
        val data1 = intArrayOf(1, 2, 3)
        val data2 = intArrayOf(4, 5)
        val data3 = intArrayOf(6, 7, 8, 9)
        
        val tensor1 = backend.createTensor(data1, intArrayOf(3), DType.INT32) 
            as OptimizedMegaTensorBackend.OptimizedMegaTensor
        val tensor2 = backend.createTensor(data2, intArrayOf(2), DType.INT32) 
            as OptimizedMegaTensorBackend.OptimizedMegaTensor
        val tensor3 = backend.createTensor(data3, intArrayOf(4), DType.INT32) 
            as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        val result = arrayOps.concatenate(listOf(tensor1, tensor2, tensor3), axis = 0) 
            as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        assertEquals(DType.INT32, result.dtype)
        assertEquals(9, result.size)
        assertTrue(result.shape.contentEquals(intArrayOf(9)))
        // Result should be [1,2,3,4,5,6,7,8,9]
    }
    
    @Test
    fun testRepeatFlattened() {
        val data = intArrayOf(1, 2, 3)
        val tensor = backend.createTensor(data, intArrayOf(3), DType.INT32) 
            as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        val result = arrayOps.repeat(tensor, 3) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        assertEquals(DType.INT32, result.dtype)
        assertEquals(9, result.size)
        assertTrue(result.shape.contentEquals(intArrayOf(9)))
        // Result should be [1,2,3,1,2,3,1,2,3]
    }
    
    @Test
    fun testRepeatAlongAxis() {
        val data = intArrayOf(1, 2, 3)
        val tensor = backend.createTensor(data, intArrayOf(3), DType.INT32) 
            as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        val result = arrayOps.repeat(tensor, 2, axis = 0) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        assertEquals(DType.INT32, result.dtype)
        assertEquals(6, result.size)
        assertTrue(result.shape.contentEquals(intArrayOf(6)))
        // Result should be [1,1,2,2,3,3]
    }
    
    @Test
    fun testRepeatZero() {
        val data = intArrayOf(1, 2, 3)
        val tensor = backend.createTensor(data, intArrayOf(3), DType.INT32) 
            as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        val result = arrayOps.repeat(tensor, 0) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        assertEquals(DType.INT32, result.dtype)
        assertEquals(0, result.size)
        assertTrue(result.shape.contentEquals(intArrayOf(0)))
    }
    
    @Test
    fun testTile1D() {
        val data = intArrayOf(1, 2)
        val tensor = backend.createTensor(data, intArrayOf(2), DType.INT32) 
            as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        val result = arrayOps.tile(tensor, intArrayOf(3)) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        assertEquals(DType.INT32, result.dtype)
        assertEquals(6, result.size)
        assertTrue(result.shape.contentEquals(intArrayOf(6)))
        // Result should be [1,2,1,2,1,2]
    }
    
    @Test
    fun testTileZeroReps() {
        val data = intArrayOf(1, 2, 3)
        val tensor = backend.createTensor(data, intArrayOf(3), DType.INT32) 
            as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        val result = arrayOps.tile(tensor, intArrayOf(0)) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        assertEquals(DType.INT32, result.dtype)
        assertEquals(0, result.size)
        assertTrue(result.shape.contentEquals(intArrayOf(0)))
    }
    
    @Test
    fun testErrorHandling() {
        // Test empty tensor list
        assertFailsWith<IllegalArgumentException> {
            arrayOps.vstack(emptyList())
        }
        
        assertFailsWith<IllegalArgumentException> {
            arrayOps.hstack(emptyList())
        }
        
        assertFailsWith<IllegalArgumentException> {
            arrayOps.concatenate(emptyList())
        }
        
        // Test incompatible shapes for vstack
        val tensor1 = backend.createTensor(doubleArrayOf(1.0, 2.0), intArrayOf(1, 2), DType.FLOAT64)
        val tensor2 = backend.createTensor(doubleArrayOf(3.0, 4.0, 5.0), intArrayOf(1, 3), DType.FLOAT64)
        
        assertFailsWith<IllegalArgumentException> {
            arrayOps.vstack(listOf(tensor1, tensor2))
        }
        
        // Test incompatible types
        val intTensor = backend.createTensor(intArrayOf(1, 2), intArrayOf(1, 2), DType.INT32)
        val floatTensor = backend.createTensor(doubleArrayOf(3.0, 4.0), intArrayOf(1, 2), DType.FLOAT64)
        
        assertFailsWith<IllegalArgumentException> {
            arrayOps.vstack(listOf(intTensor, floatTensor))
        }
        
        // Test negative repeats
        val tensor = backend.createTensor(intArrayOf(1, 2), intArrayOf(2), DType.INT32)
        
        assertFailsWith<IllegalArgumentException> {
            arrayOps.repeat(tensor, -1)
        }
        
        // Test negative tile reps
        assertFailsWith<IllegalArgumentException> {
            arrayOps.tile(tensor, intArrayOf(-1))
        }
        
        // Test invalid axis
        assertFailsWith<IllegalArgumentException> {
            arrayOps.repeat(tensor, 2, axis = 5)
        }
    }
    
    @Test
    fun testUnsupportedOperations() {
        val tensor = backend.createTensor(doubleArrayOf(1.0, 2.0, 3.0, 4.0), intArrayOf(2, 2), DType.FLOAT64)
        
        // Test unsupported multi-dimensional concatenate
        assertFailsWith<UnsupportedOperationException> {
            arrayOps.concatenate(listOf(tensor, tensor), axis = 1)
        }
        
        // Test unsupported multi-dimensional repeat
        assertFailsWith<UnsupportedOperationException> {
            arrayOps.repeat(tensor, 2, axis = 0)
        }
        
        // Test unsupported multi-dimensional tile
        assertFailsWith<UnsupportedOperationException> {
            arrayOps.tile(tensor, intArrayOf(2, 2))
        }
    }
    
    @Test
    fun testDifferentDataTypes() {
        // Test with boolean arrays
        val boolData1 = booleanArrayOf(true, false)
        val boolData2 = booleanArrayOf(false, true)
        
        val boolTensor1 = backend.createTensor(boolData1, intArrayOf(2), DType.BOOL)
        val boolTensor2 = backend.createTensor(boolData2, intArrayOf(2), DType.BOOL)
        
        val result = arrayOps.concatenate(listOf(boolTensor1, boolTensor2)) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        
        assertEquals(DType.BOOL, result.dtype)
        assertEquals(4, result.size)
        assertTrue(result.shape.contentEquals(intArrayOf(4)))
        
        // Test with mixed numeric types (should fail)
        val floatTensor = backend.createTensor(floatArrayOf(1.0f, 2.0f), intArrayOf(2), DType.FLOAT32)
        val intTensor = backend.createTensor(intArrayOf(3, 4), intArrayOf(2), DType.INT32)
        
        assertFailsWith<IllegalArgumentException> {
            arrayOps.concatenate(listOf(floatTensor, intTensor))
        }
    }
    
    @Test
    fun testEmptyAndSingleElementOperations() {
        // Test with single tensor in stack operations
        val singleData = doubleArrayOf(1.0, 2.0)
        val singleTensor = backend.createTensor(singleData, intArrayOf(1, 2), DType.FLOAT64)
        
        val vstackResult = arrayOps.vstack(listOf(singleTensor)) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        assertEquals(2, vstackResult.size)
        assertTrue(vstackResult.shape.contentEquals(intArrayOf(1, 2)))
        
        val hstackResult = arrayOps.hstack(listOf(singleTensor)) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        assertEquals(2, hstackResult.size)
        assertTrue(hstackResult.shape.contentEquals(intArrayOf(1, 2)))
        
        // Test repeat with single element
        val scalarData = doubleArrayOf(5.0)
        val scalarTensor = backend.createTensor(scalarData, intArrayOf(1), DType.FLOAT64)
        
        val repeatResult = arrayOps.repeat(scalarTensor, 4) as OptimizedMegaTensorBackend.OptimizedMegaTensor
        assertEquals(4, repeatResult.size)
        assertTrue(repeatResult.shape.contentEquals(intArrayOf(4)))
    }
}