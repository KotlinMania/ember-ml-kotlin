package ai.solace.ember.backend.metal

import ai.solace.ember.tensor.common.DType

/**
 * Metal tensor implementation for GPU-accelerated tensor operations.
 * 
 * This class wraps Metal buffers and provides tensor semantics for use with
 * the Metal backend.
 */
class MetalTensor private constructor(
    val buffer: MetalBuffer,
    val shape: IntArray,
    val dtype: DType,
    val context: MetalContext
) {

    /**
     * Gets the number of elements in the tensor.
     */
    val size: Int = shape.fold(1) { acc, dim -> acc * dim }

    /**
     * Gets the number of dimensions.
     */
    val ndim: Int = shape.size

    /**
     * Gets the strides for this tensor (assuming row-major layout).
     */
    val strides: IntArray by lazy {
        val strides = IntArray(shape.size)
        var stride = 1
        for (i in shape.size - 1 downTo 0) {
            strides[i] = stride
            stride *= shape[i]
        }
        strides
    }

    /**
     * Copies the tensor data to a FloatArray.
     */
    fun toFloatArray(): FloatArray {
        return buffer.toFloatArray()
    }

    /**
     * Copies data from a FloatArray to this tensor.
     */
    fun fromFloatArray(data: FloatArray) {
        require(data.size == size) { 
            "Data size (${data.size}) must match tensor size ($size)" 
        }
        buffer.fromFloatArray(data)
    }

    /**
     * Creates a copy of this tensor.
     */
    fun copy(): MetalTensor {
        val newBuffer = context.createBuffer(size * dtype.sizeInBytes)
        val newTensor = MetalTensor(newBuffer, shape.copyOf(), dtype, context)

        // Copy data using Metal operations
        MetalOperations.copy(this, newTensor, context)

        return newTensor
    }

    /**
     * Reshapes the tensor to a new shape.
     */
    fun reshape(newShape: IntArray): MetalTensor {
        val newSize = newShape.fold(1) { acc, dim -> acc * dim }
        require(newSize == size) { 
            "New shape size ($newSize) must match current size ($size)" 
        }
        return MetalTensor(buffer, newShape, dtype, context)
    }

    /**
     * Returns a string representation of the tensor.
     */
    override fun toString(): String {
        return "MetalTensor(shape=${shape.contentToString()}, dtype=$dtype, size=$size)"
    }

    /**
     * Releases the tensor resources.
     */
    fun release() {
        buffer.release()
    }

    companion object {
        /**
         * Creates a Metal tensor from data.
         * 
         * @param data The tensor data (FloatArray, IntArray, etc.)
         * @param shape The tensor shape
         * @param dtype The tensor data type
         * @param context The Metal context
         */
        fun create(data: Any, shape: IntArray, dtype: DType, context: MetalContext): MetalTensor {
            val size = shape.fold(1) { acc, dim -> acc * dim }

            val floatData = when (data) {
                is FloatArray -> data
                is IntArray -> data.map { it.toFloat() }.toFloatArray()
                is DoubleArray -> data.map { it.toFloat() }.toFloatArray()
                is Array<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val flatArray = flattenArray(data as Array<Any>)
                    when (flatArray.firstOrNull()) {
                        is Float -> flatArray.map { (it as Float) }.toFloatArray()
                        is Int -> flatArray.map { (it as Int).toFloat() }.toFloatArray()
                        is Double -> flatArray.map { (it as Double).toFloat() }.toFloatArray()
                        else -> throw IllegalArgumentException("Unsupported data type in array")
                    }
                }
                else -> throw IllegalArgumentException("Unsupported data type: ${data::class}")
            }

            require(floatData.size == size) { 
                "Data size (${floatData.size}) must match tensor size ($size)" 
            }

            val buffer = context.createBuffer(floatData)
            return MetalTensor(buffer, shape, dtype, context)
        }

        /**
         * Creates a Metal tensor with zeros.
         */
        fun zeros(shape: IntArray, dtype: DType, context: MetalContext): MetalTensor {
            val size = shape.fold(1) { acc, dim -> acc * dim }
            val data = FloatArray(size) { 0.0f }
            return create(data, shape, dtype, context)
        }

        /**
         * Creates a Metal tensor with ones.
         */
        fun ones(shape: IntArray, dtype: DType, context: MetalContext): MetalTensor {
            val size = shape.fold(1) { acc, dim -> acc * dim }
            val data = FloatArray(size) { 1.0f }
            return create(data, shape, dtype, context)
        }

        /**
         * Creates a Metal tensor with random values.
         */
        fun random(shape: IntArray, dtype: DType, context: MetalContext): MetalTensor {
            val size = shape.fold(1) { acc, dim -> acc * dim }
            val data = FloatArray(size) { kotlin.random.Random.nextFloat() }
            return create(data, shape, dtype, context)
        }

        /**
         * Flattens a multi-dimensional array to a single-dimensional array.
         */
        private fun flattenArray(array: Array<Any>): Array<Any> {
            val result = mutableListOf<Any>()

            fun flatten(arr: Any) {
                when (arr) {
                    is Array<*> -> arr.forEach { if (it != null) flatten(it) }
                    else -> result.add(arr)
                }
            }

            flatten(array)
            return result.toTypedArray()
        }
    }
}
