package ai.solace.ember.backend.storage

import ai.solace.ember.tensor.common.DType

/**
 * Hybrid storage system for tensors to optimize memory usage.
 *
 * This sealed class provides native storage strategies sized to the dtype:
 * packed booleans, native byte/int/long/float/double arrays. Numeric
 * precision above 64 bits is delegated to the Klang fixed-precision types
 * (`io.github.kotlinmania.klang.fp.CFloat{16,32,64}`, `CLongDouble`,
 * `klang.int.C_Int128`, `C_UInt128`) at the call site, not modeled as a
 * storage variant here.
 */
sealed class TensorStorage {
    abstract val size: Int
    abstract val dtype: DType

    /**
     * Efficient boolean storage using bit packing.
     */
    data class PackedBooleanStorage(
        val data: BooleanArray,
        override val size: Int,
        override val dtype: DType = DType.BOOL,
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
     * Native bit-packed storage.
     */
    data class NativeUByteStorage(
        val data: UByteArray,
        override val size: Int,
        override val dtype: DType = DType.UINT8,
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
     * Native primitive-array storage.
     */
    data class NativeIntStorage(
        val data: IntArray,
        override val size: Int,
        override val dtype: DType = DType.INT32,
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
     * Native primitive-array storage.
     */
    data class NativeLongStorage(
        val data: LongArray,
        override val size: Int,
        override val dtype: DType = DType.INT64,
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
     * Native primitive-array storage.
     */
    data class NativeFloatStorage(
        val data: FloatArray,
        override val size: Int,
        override val dtype: DType = DType.FLOAT32,
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
     * Native primitive-array storage.
     */
    data class NativeDoubleStorage(
        val data: DoubleArray,
        override val size: Int,
        override val dtype: DType = DType.FLOAT64,
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

    companion object {
        /**
         * Creates the most efficient storage type for the given data type.
         *
         * @param dtype The data type to create storage for
         * @param size The number of elements to store
         * @return The most efficient storage implementation
         */
        fun createOptimalStorage(dtype: DType, size: Int): TensorStorage =
            when (dtype) {
                DType.BOOL -> PackedBooleanStorage(BooleanArray(size), size, dtype)
                DType.UINT8 -> NativeUByteStorage(UByteArray(size), size, dtype)
                DType.INT32 -> NativeIntStorage(IntArray(size), size, dtype)
                DType.INT64 -> NativeLongStorage(LongArray(size), size, dtype)
                DType.FLOAT32 -> NativeFloatStorage(FloatArray(size), size, dtype)
                DType.FLOAT64 -> NativeDoubleStorage(DoubleArray(size), size, dtype)
            }
    }
}
