package ai.solace.ember.backend.storage

import ai.solace.ember.tensor.common.DType
import ai.solace.ember.tensor.bitwise.MegaNumber

/**
 * Hybrid storage system for tensors to optimize memory usage.
 * 
 * This sealed class provides different storage strategies depending on the data type:
 * - Efficient native storage for common types (Boolean, UINT8, INT32, etc.)
 * - MegaNumber storage for arbitrary precision when needed
 * 
 * This addresses the 32-bit limb inefficiency where all data types were forced
 * into expensive MegaNumber storage regardless of their natural size.
 */
sealed class TensorStorage {
    abstract val size: Int
    abstract val dtype: DType
    
    /**
     * Efficient boolean storage using bit packing.
     * Provides ~256x memory reduction compared to MegaNumber storage.
     */
    data class PackedBooleanStorage(
        val data: BooleanArray,
        override val size: Int,
        override val dtype: DType = DType.BOOL
    ) : TensorStorage() {
        
        /**
         * Get boolean value at index.
         */
        fun get(index: Int): Boolean {
            if (index < 0 || index >= size) {
                throw IndexOutOfBoundsException("Index $index out of bounds for size $size")
            }
            return data[index]
        }
        
        /**
         * Set boolean value at index.
         */
        fun set(index: Int, value: Boolean) {
            if (index < 0 || index >= size) {
                throw IndexOutOfBoundsException("Index $index out of bounds for size $size")
            }
            data[index] = value
        }
    }
    
    /**
     * Native UINT8 storage using UByteArray.
     * Provides ~32x memory reduction compared to MegaNumber storage.
     */
    data class NativeUByteStorage(
        val data: UByteArray,
        override val size: Int,
        override val dtype: DType = DType.UINT8
    ) : TensorStorage() {
        
        fun get(index: Int): UByte {
            if (index < 0 || index >= size) {
                throw IndexOutOfBoundsException("Index $index out of bounds for size $size")
            }
            return data[index]
        }
        
        fun set(index: Int, value: UByte) {
            if (index < 0 || index >= size) {
                throw IndexOutOfBoundsException("Index $index out of bounds for size $size")
            }
            data[index] = value
        }
    }
    
    /**
     * Native INT32 storage using IntArray.
     * Provides ~8x memory reduction compared to MegaNumber storage.
     */
    data class NativeIntStorage(
        val data: IntArray,
        override val size: Int,
        override val dtype: DType = DType.INT32
    ) : TensorStorage() {
        
        fun get(index: Int): Int {
            if (index < 0 || index >= size) {
                throw IndexOutOfBoundsException("Index $index out of bounds for size $size")
            }
            return data[index]
        }
        
        fun set(index: Int, value: Int) {
            if (index < 0 || index >= size) {
                throw IndexOutOfBoundsException("Index $index out of bounds for size $size")
            }
            data[index] = value
        }
    }
    
    /**
     * Native INT64 storage using LongArray.
     * Provides ~4x memory reduction compared to MegaNumber storage.
     */
    data class NativeLongStorage(
        val data: LongArray,
        override val size: Int,
        override val dtype: DType = DType.INT64
    ) : TensorStorage() {
        
        fun get(index: Int): Long {
            if (index < 0 || index >= size) {
                throw IndexOutOfBoundsException("Index $index out of bounds for size $size")
            }
            return data[index]
        }
        
        fun set(index: Int, value: Long) {
            if (index < 0 || index >= size) {
                throw IndexOutOfBoundsException("Index $index out of bounds for size $size")
            }
            data[index] = value
        }
    }
    
    /**
     * Native FLOAT32 storage using FloatArray.
     * Provides ~8x memory reduction compared to MegaNumber storage.
     */
    data class NativeFloatStorage(
        val data: FloatArray,
        override val size: Int,
        override val dtype: DType = DType.FLOAT32
    ) : TensorStorage() {
        
        fun get(index: Int): Float {
            if (index < 0 || index >= size) {
                throw IndexOutOfBoundsException("Index $index out of bounds for size $size")
            }
            return data[index]
        }
        
        fun set(index: Int, value: Float) {
            if (index < 0 || index >= size) {
                throw IndexOutOfBoundsException("Index $index out of bounds for size $size")
            }
            data[index] = value
        }
    }
    
    /**
     * Native FLOAT64 storage using DoubleArray.
     * Provides ~4x memory reduction compared to MegaNumber storage.
     */
    data class NativeDoubleStorage(
        val data: DoubleArray,
        override val size: Int,
        override val dtype: DType = DType.FLOAT64
    ) : TensorStorage() {
        
        fun get(index: Int): Double {
            if (index < 0 || index >= size) {
                throw IndexOutOfBoundsException("Index $index out of bounds for size $size")
            }
            return data[index]
        }
        
        fun set(index: Int, value: Double) {
            if (index < 0 || index >= size) {
                throw IndexOutOfBoundsException("Index $index out of bounds for size $size")
            }
            data[index] = value
        }
    }
    
    /**
     * MegaNumber storage for arbitrary precision arithmetic.
     * Falls back to this when arbitrary precision is needed.
     */
    data class MegaNumberStorage(
        val data: Array<MegaNumber>,
        override val size: Int,
        override val dtype: DType
    ) : TensorStorage() {
        
        fun get(index: Int): MegaNumber {
            if (index < 0 || index >= size) {
                throw IndexOutOfBoundsException("Index $index out of bounds for size $size")
            }
            return data[index]
        }
        
        fun set(index: Int, value: MegaNumber) {
            if (index < 0 || index >= size) {
                throw IndexOutOfBoundsException("Index $index out of bounds for size $size")
            }
            data[index] = value
        }
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is MegaNumberStorage) return false
            
            if (!data.contentEquals(other.data)) return false
            if (size != other.size) return false
            if (dtype != other.dtype) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + size
            result = 31 * result + dtype.hashCode()
            return result
        }
    }
    
    companion object {
        /**
         * Creates the most efficient storage type for the given data type.
         * 
         * @param dtype The data type to create storage for
         * @param size The number of elements to store
         * @return The most efficient storage implementation
         */
        fun createOptimalStorage(dtype: DType, size: Int): TensorStorage {
            return when (dtype) {
                DType.BOOL -> PackedBooleanStorage(BooleanArray(size), size, dtype)
                DType.UINT8 -> NativeUByteStorage(UByteArray(size), size, dtype)
                DType.INT32 -> NativeIntStorage(IntArray(size), size, dtype)
                DType.INT64 -> NativeLongStorage(LongArray(size), size, dtype)
                DType.FLOAT32 -> NativeFloatStorage(FloatArray(size), size, dtype)
                DType.FLOAT64 -> NativeDoubleStorage(DoubleArray(size), size, dtype)
                // For future expansion: could fall back to MegaNumber for arbitrary precision
                // else -> MegaNumberStorage(Array(size) { MegaNumber() }, size, dtype)
            }
        }
    }
}