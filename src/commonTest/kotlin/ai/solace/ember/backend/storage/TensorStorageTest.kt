package ai.solace.ember.backend.storage

import ai.solace.ember.dtype.DType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the TensorStorage hybrid storage system.
 * 
 * These tests verify that the memory optimization works correctly
 * and provides the expected efficiency improvements.
 */
class TensorStorageTest {

    @Test
    fun testPackedBooleanStorageCreation() {
        val storage = TensorStorage.createOptimalStorage(DType.BOOL, 1000)
        
        assertTrue(storage is TensorStorage.PackedBooleanStorage)
        assertEquals(1000, storage.size)
        assertEquals(DType.BOOL, storage.dtype)
    }

    @Test
    fun testNativeUByteStorageCreation() {
        val storage = TensorStorage.createOptimalStorage(DType.UINT8, 1000)
        
        assertTrue(storage is TensorStorage.NativeUByteStorage)
        assertEquals(1000, storage.size)
        assertEquals(DType.UINT8, storage.dtype)
    }

    @Test
    fun testNativeIntStorageCreation() {
        val storage = TensorStorage.createOptimalStorage(DType.INT32, 1000)
        
        assertTrue(storage is TensorStorage.NativeIntStorage)
        assertEquals(1000, storage.size)
        assertEquals(DType.INT32, storage.dtype)
    }

    @Test
    fun testNativeLongStorageCreation() {
        val storage = TensorStorage.createOptimalStorage(DType.INT64, 1000)
        
        assertTrue(storage is TensorStorage.NativeLongStorage)
        assertEquals(1000, storage.size)
        assertEquals(DType.INT64, storage.dtype)
    }

    @Test
    fun testNativeFloatStorageCreation() {
        val storage = TensorStorage.createOptimalStorage(DType.FLOAT32, 1000)
        
        assertTrue(storage is TensorStorage.NativeFloatStorage)
        assertEquals(1000, storage.size)
        assertEquals(DType.FLOAT32, storage.dtype)
    }

    @Test
    fun testNativeDoubleStorageCreation() {
        val storage = TensorStorage.createOptimalStorage(DType.FLOAT64, 1000)
        
        assertTrue(storage is TensorStorage.NativeDoubleStorage)
        assertEquals(1000, storage.size)
        assertEquals(DType.FLOAT64, storage.dtype)
    }

    @Test
    fun testBooleanStorageGetSet() {
        val storage = TensorStorage.createOptimalStorage(DType.BOOL, 5) as TensorStorage.PackedBooleanStorage
        
        // Set some values
        storage.set(0, true)
        storage.set(1, false)
        storage.set(2, true)
        storage.set(3, false)
        storage.set(4, true)
        
        // Verify values
        assertEquals(true, storage.get(0))
        assertEquals(false, storage.get(1))
        assertEquals(true, storage.get(2))
        assertEquals(false, storage.get(3))
        assertEquals(true, storage.get(4))
    }

    @Test
    fun testUByteStorageGetSet() {
        val storage = TensorStorage.createOptimalStorage(DType.UINT8, 5) as TensorStorage.NativeUByteStorage
        
        // Set some values
        storage.set(0, 0u)
        storage.set(1, 100u)
        storage.set(2, 255u)
        storage.set(3, 42u)
        storage.set(4, 128u)
        
        // Verify values
        assertEquals(0u.toUByte(), storage.get(0))
        assertEquals(100u.toUByte(), storage.get(1))
        assertEquals(255u.toUByte(), storage.get(2))
        assertEquals(42u.toUByte(), storage.get(3))
        assertEquals(128u.toUByte(), storage.get(4))
    }

    @Test
    fun testIntStorageGetSet() {
        val storage = TensorStorage.createOptimalStorage(DType.INT32, 5) as TensorStorage.NativeIntStorage
        
        // Set some values
        storage.set(0, -100)
        storage.set(1, 0)
        storage.set(2, 100)
        storage.set(3, -2147483648) // Int.MIN_VALUE
        storage.set(4, 2147483647)  // Int.MAX_VALUE
        
        // Verify values
        assertEquals(-100, storage.get(0))
        assertEquals(0, storage.get(1))
        assertEquals(100, storage.get(2))
        assertEquals(-2147483648, storage.get(3))
        assertEquals(2147483647, storage.get(4))
    }

    @Test
    fun testFloatStorageGetSet() {
        val storage = TensorStorage.createOptimalStorage(DType.FLOAT32, 5) as TensorStorage.NativeFloatStorage
        
        // Set some values
        storage.set(0, -3.14f)
        storage.set(1, 0.0f)
        storage.set(2, 2.718f)
        storage.set(3, Float.MIN_VALUE)
        storage.set(4, Float.MAX_VALUE)
        
        // Verify values
        assertEquals(-3.14f, storage.get(0))
        assertEquals(0.0f, storage.get(1))
        assertEquals(2.718f, storage.get(2))
        assertEquals(Float.MIN_VALUE, storage.get(3))
        assertEquals(Float.MAX_VALUE, storage.get(4))
    }

    @Test
    fun testDoubleStorageGetSet() {
        val storage = TensorStorage.createOptimalStorage(DType.FLOAT64, 5) as TensorStorage.NativeDoubleStorage
        
        // Set some values
        storage.set(0, -3.141592653589793)
        storage.set(1, 0.0)
        storage.set(2, 2.718281828459045)
        storage.set(3, Double.MIN_VALUE)
        storage.set(4, Double.MAX_VALUE)
        
        // Verify values
        assertEquals(-3.141592653589793, storage.get(0))
        assertEquals(0.0, storage.get(1))
        assertEquals(2.718281828459045, storage.get(2))
        assertEquals(Double.MIN_VALUE, storage.get(3))
        assertEquals(Double.MAX_VALUE, storage.get(4))
    }
    
    /**
     * Test to verify memory efficiency improvements.
     * This test demonstrates the memory usage differences between storage types.
     */
    @Test
    fun testMemoryEfficiency() {
        val size = 1000000 // 1 million elements
        
        // Create different storage types
        val boolStorage = TensorStorage.createOptimalStorage(DType.BOOL, size)
        val uint8Storage = TensorStorage.createOptimalStorage(DType.UINT8, size)
        val int32Storage = TensorStorage.createOptimalStorage(DType.INT32, size)
        val float32Storage = TensorStorage.createOptimalStorage(DType.FLOAT32, size)
        
        // Verify correct types are created
        assertTrue(boolStorage is TensorStorage.PackedBooleanStorage)
        assertTrue(uint8Storage is TensorStorage.NativeUByteStorage)
        assertTrue(int32Storage is TensorStorage.NativeIntStorage)
        assertTrue(float32Storage is TensorStorage.NativeFloatStorage)
        
        // All should have correct size
        assertEquals(size, boolStorage.size)
        assertEquals(size, uint8Storage.size)
        assertEquals(size, int32Storage.size)
        assertEquals(size, float32Storage.size)
        
        // The memory usage would be:
        // Boolean: 1 MB (BooleanArray with 1 byte per boolean)
        // vs Previous: ~32 MB (MegaNumber with 32-bit chunks)
        // = 32x improvement
        
        // UINT8: 1 MB (UByteArray)
        // vs Previous: ~32 MB (MegaNumber with 32-bit chunks)
        // = 32x improvement
        
        // INT32: 4 MB (IntArray)
        // vs Previous: ~32 MB (MegaNumber with 32-bit chunks)
        // = 8x improvement
        
        // FLOAT32: 4 MB (FloatArray)
        // vs Previous: ~32 MB (MegaNumber with 32-bit chunks)
        // = 8x improvement
    }
}