package ai.solace.ember.tensor

import ai.solace.ember.dtype.DType
import ai.solace.ember.scalar.Scalar
import ai.solace.klang.fp.CFloat16
import ai.solace.klang.fp.CFloat32
import ai.solace.klang.fp.CDouble

/**
 * EmberTensor - the core tensor class for Ember ML.
 * 
 * Built on KLang for cross-platform determinism, with an MLX-inspired API.
 * Everything is a tensor (scalars are 0-dimensional tensors).
 * 
 * Example usage:
 * ```kotlin
 * val x = EmberTensor.fromList(listOf(1.0f, 2.0f, 3.0f))
 * val y = EmberTensor.zeros(intArrayOf(3))
 * val z = x + y
 * ```
 */
class EmberTensor private constructor(
    val shape: IntArray,
    val dtype: DType,
    private val storage: TensorStorage
) {
    
    /**
     * Number of dimensions.
     */
    val ndim: Int get() = shape.size
    
    /**
     * Total number of elements.
     */
    val size: Int get() = if (shape.isEmpty()) 1 else shape.reduce { a, b -> a * b }
    
    /**
     * Check if this is a scalar (0-dimensional tensor).
     */
    val isScalar: Boolean get() = shape.isEmpty()
    
    // ============================================
    // Element-wise arithmetic operations
    // ============================================
    
    operator fun plus(other: EmberTensor): EmberTensor {
        requireCompatibleShapes(this, other, "addition")
        return elementWiseBinaryOp(this, other) { a, b -> a + b }
    }
    
    operator fun plus(scalar: Number): EmberTensor {
        return elementWiseScalarOp(this, scalar) { a, s -> a + s }
    }
    
    operator fun minus(other: EmberTensor): EmberTensor {
        requireCompatibleShapes(this, other, "subtraction")
        return elementWiseBinaryOp(this, other) { a, b -> a - b }
    }
    
    operator fun minus(scalar: Number): EmberTensor {
        return elementWiseScalarOp(this, scalar) { a, s -> a - s }
    }
    
    operator fun times(other: EmberTensor): EmberTensor {
        requireCompatibleShapes(this, other, "multiplication")
        return elementWiseBinaryOp(this, other) { a, b -> a * b }
    }
    
    operator fun times(scalar: Number): EmberTensor {
        return elementWiseScalarOp(this, scalar) { a, s -> a * s }
    }
    
    operator fun div(other: EmberTensor): EmberTensor {
        requireCompatibleShapes(this, other, "division")
        return elementWiseBinaryOp(this, other) { a, b -> a / b }
    }
    
    operator fun div(scalar: Number): EmberTensor {
        return elementWiseScalarOp(this, scalar) { a, s -> a / s }
    }
    
    operator fun unaryMinus(): EmberTensor {
        return elementWiseUnaryOp(this) { -it }
    }
    
    // ============================================
    // Mathematical operations
    // ============================================
    
    fun sin(): EmberTensor = elementWiseUnaryOp(this) { kotlin.math.sin(it.toDouble()).toFloat() }
    fun cos(): EmberTensor = elementWiseUnaryOp(this) { kotlin.math.cos(it.toDouble()).toFloat() }
    fun tan(): EmberTensor = elementWiseUnaryOp(this) { kotlin.math.tan(it.toDouble()).toFloat() }
    fun exp(): EmberTensor = elementWiseUnaryOp(this) { kotlin.math.exp(it.toDouble()).toFloat() }
    fun log(): EmberTensor = elementWiseUnaryOp(this) { kotlin.math.ln(it.toDouble()).toFloat() }
    fun sqrt(): EmberTensor = elementWiseUnaryOp(this) { kotlin.math.sqrt(it.toDouble()).toFloat() }
    fun abs(): EmberTensor = elementWiseUnaryOp(this) { kotlin.math.abs(it) }
    fun square(): EmberTensor = elementWiseBinaryOp(this, this) { a, _ -> a * a }
    fun power(exponent: Number): EmberTensor {
        val exp = exponent.toDouble()
        return elementWiseUnaryOp(this) { kotlin.math.pow(it.toDouble(), exp).toFloat() }
    }
    
    // ============================================
    // Reduction operations
    // ============================================
    
    fun sum(axis: Int? = null, keepDims: Boolean = false): EmberTensor {
        return if (axis == null) {
            // Sum all elements
            val result = storage.toFloatArray().sum()
            fromScalar(Scalar.fromValue(result, dtype))
        } else {
            reduceAlongAxis(this, axis, keepDims) { values -> values.sum() }
        }
    }
    
    fun mean(axis: Int? = null, keepDims: Boolean = false): EmberTensor {
        return if (axis == null) {
            val result = storage.toFloatArray().average().toFloat()
            fromScalar(Scalar.fromValue(result, dtype))
        } else {
            reduceAlongAxis(this, axis, keepDims) { values -> values.average().toFloat() }
        }
    }
    
    fun max(axis: Int? = null, keepDims: Boolean = false): EmberTensor {
        return if (axis == null) {
            val result = storage.toFloatArray().maxOrNull() ?: 0f
            fromScalar(Scalar.fromValue(result, dtype))
        } else {
            reduceAlongAxis(this, axis, keepDims) { values -> values.maxOrNull() ?: 0f }
        }
    }
    
    fun min(axis: Int? = null, keepDims: Boolean = false): EmberTensor {
        return if (axis == null) {
            val result = storage.toFloatArray().minOrNull() ?: 0f
            fromScalar(Scalar.fromValue(result, dtype))
        } else {
            reduceAlongAxis(this, axis, keepDims) { values -> values.minOrNull() ?: 0f }
        }
    }
    
    // ============================================
    // Shape operations
    // ============================================
    
    fun reshape(newShape: IntArray): EmberTensor {
        val newSize = if (newShape.isEmpty()) 1 else newShape.reduce { a, b -> a * b }
        require(newSize == size) { 
            "Cannot reshape tensor of size $size to shape ${newShape.contentToString()}"
        }
        return EmberTensor(newShape, dtype, storage)
    }
    
    fun transpose(axes: IntArray? = null): EmberTensor {
        if (ndim <= 1) return this
        
        val permutation = axes ?: IntArray(ndim) { ndim - 1 - it }
        require(permutation.size == ndim) { 
            "Transpose axes must match number of dimensions" 
        }
        
        // For now, implement simple 2D transpose
        if (ndim == 2 && permutation.contentEquals(intArrayOf(1, 0))) {
            return transpose2D()
        }
        
        // General case would need more complex logic
        TODO("General transpose not yet implemented")
    }
    
    private fun transpose2D(): EmberTensor {
        require(ndim == 2) { "2D transpose requires 2D tensor" }
        val rows = shape[0]
        val cols = shape[1]
        val data = storage.toFloatArray()
        val transposed = FloatArray(size)
        
        for (i in 0 until rows) {
            for (j in 0 until cols) {
                transposed[j * rows + i] = data[i * cols + j]
            }
        }
        
        return fromFloatArray(transposed, intArrayOf(cols, rows), dtype)
    }
    
    fun matmul(other: EmberTensor): EmberTensor {
        require(ndim == 2 && other.ndim == 2) { 
            "Matrix multiplication requires 2D tensors" 
        }
        require(shape[1] == other.shape[0]) { 
            "Incompatible shapes for matrix multiplication: ${shape.contentToString()} and ${other.shape.contentToString()}" 
        }
        
        val m = shape[0]
        val n = shape[1]
        val p = other.shape[1]
        
        val a = storage.toFloatArray()
        val b = other.storage.toFloatArray()
        val result = FloatArray(m * p)
        
        for (i in 0 until m) {
            for (j in 0 until p) {
                var sum = 0f
                for (k in 0 until n) {
                    sum += a[i * n + k] * b[k * p + j]
                }
                result[i * p + j] = sum
            }
        }
        
        return fromFloatArray(result, intArrayOf(m, p), dtype)
    }
    
    // ============================================
    // Data access
    // ============================================
    
    fun toFloatArray(): FloatArray = storage.toFloatArray()
    
    fun toScalar(): Scalar {
        require(isScalar) { "toScalar() requires a 0-dimensional tensor" }
        val value = storage.toFloatArray()[0]
        return Scalar.fromValue(value, dtype)
    }
    
    override fun toString(): String {
        val dataPreview = if (size <= 10) {
            storage.toFloatArray().contentToString()
        } else {
            val preview = storage.toFloatArray().take(5).joinToString(", ")
            "[$preview, ... (${size - 5} more)]"
        }
        return "EmberTensor(shape=${shape.contentToString()}, dtype=$dtype, data=$dataPreview)"
    }
    
    companion object {
        
        // ============================================
        // Creation from scalars and lists
        // ============================================
        
        fun fromScalar(scalar: Scalar): EmberTensor {
            val data = floatArrayOf(scalar.toFloat())
            val storage = TensorStorage.fromFloatArray(data, scalar.dtype)
            return EmberTensor(intArrayOf(), scalar.dtype, storage)
        }
        
        fun fromList(values: List<Number>, dtype: DType = DType.Float32): EmberTensor {
            val data = FloatArray(values.size) { values[it].toFloat() }
            val storage = TensorStorage.fromFloatArray(data, dtype)
            return EmberTensor(intArrayOf(values.size), dtype, storage)
        }
        
        fun fromList2D(values: List<List<Number>>, dtype: DType = DType.Float32): EmberTensor {
            val rows = values.size
            require(rows > 0) { "Cannot create tensor from empty list" }
            val cols = values[0].size
            require(values.all { it.size == cols }) { "All rows must have the same length" }
            
            val data = FloatArray(rows * cols) { i ->
                values[i / cols][i % cols].toFloat()
            }
            val storage = TensorStorage.fromFloatArray(data, dtype)
            return EmberTensor(intArrayOf(rows, cols), dtype, storage)
        }
        
        fun fromFloatArray(data: FloatArray, shape: IntArray, dtype: DType = DType.Float32): EmberTensor {
            val storage = TensorStorage.fromFloatArray(data, dtype)
            return EmberTensor(shape, dtype, storage)
        }
        
        // ============================================
        // Factory functions
        // ============================================
        
        fun zeros(shape: IntArray, dtype: DType = DType.Float32): EmberTensor {
            val size = if (shape.isEmpty()) 1 else shape.reduce { a, b -> a * b }
            val data = FloatArray(size) { 0f }
            val storage = TensorStorage.fromFloatArray(data, dtype)
            return EmberTensor(shape, dtype, storage)
        }
        
        fun ones(shape: IntArray, dtype: DType = DType.Float32): EmberTensor {
            val size = if (shape.isEmpty()) 1 else shape.reduce { a, b -> a * b }
            val data = FloatArray(size) { 1f }
            val storage = TensorStorage.fromFloatArray(data, dtype)
            return EmberTensor(shape, dtype, storage)
        }
        
        fun full(shape: IntArray, value: Number, dtype: DType = DType.Float32): EmberTensor {
            val size = if (shape.isEmpty()) 1 else shape.reduce { a, b -> a * b }
            val data = FloatArray(size) { value.toFloat() }
            val storage = TensorStorage.fromFloatArray(data, dtype)
            return EmberTensor(shape, dtype, storage)
        }
        
        fun eye(n: Int, dtype: DType = DType.Float32): EmberTensor {
            val data = FloatArray(n * n) { i ->
                if (i / n == i % n) 1f else 0f
            }
            val storage = TensorStorage.fromFloatArray(data, dtype)
            return EmberTensor(intArrayOf(n, n), dtype, storage)
        }
        
        fun arange(start: Number, stop: Number, step: Number = 1, dtype: DType = DType.Float32): EmberTensor {
            val startF = start.toFloat()
            val stopF = stop.toFloat()
            val stepF = step.toFloat()
            
            val count = ((stopF - startF) / stepF).toInt()
            val data = FloatArray(count) { i -> startF + i * stepF }
            val storage = TensorStorage.fromFloatArray(data, dtype)
            return EmberTensor(intArrayOf(count), dtype, storage)
        }
        
        fun linspace(start: Number, stop: Number, num: Int = 50, dtype: DType = DType.Float32): EmberTensor {
            require(num > 0) { "Number of samples must be positive" }
            val startF = start.toFloat()
            val stopF = stop.toFloat()
            val step = if (num > 1) (stopF - startF) / (num - 1) else 0f
            
            val data = FloatArray(num) { i -> startF + i * step }
            val storage = TensorStorage.fromFloatArray(data, dtype)
            return EmberTensor(intArrayOf(num), dtype, storage)
        }
        
        // ============================================
        // Helper functions for operations
        // ============================================
        
        private fun requireCompatibleShapes(a: EmberTensor, b: EmberTensor, operation: String) {
            if (!a.shape.contentEquals(b.shape)) {
                // For now, require exact shape match (no broadcasting)
                throw IllegalArgumentException(
                    "Incompatible shapes for $operation: ${a.shape.contentToString()} and ${b.shape.contentToString()}"
                )
            }
        }
        
        private fun elementWiseBinaryOp(
            a: EmberTensor, 
            b: EmberTensor, 
            op: (Float, Float) -> Float
        ): EmberTensor {
            val aData = a.storage.toFloatArray()
            val bData = b.storage.toFloatArray()
            val result = FloatArray(aData.size) { i -> op(aData[i], bData[i]) }
            return fromFloatArray(result, a.shape, a.dtype)
        }
        
        private fun elementWiseScalarOp(
            tensor: EmberTensor, 
            scalar: Number, 
            op: (Float, Float) -> Float
        ): EmberTensor {
            val data = tensor.storage.toFloatArray()
            val scalarF = scalar.toFloat()
            val result = FloatArray(data.size) { i -> op(data[i], scalarF) }
            return fromFloatArray(result, tensor.shape, tensor.dtype)
        }
        
        private fun elementWiseUnaryOp(
            tensor: EmberTensor, 
            op: (Float) -> Float
        ): EmberTensor {
            val data = tensor.storage.toFloatArray()
            val result = FloatArray(data.size) { i -> op(data[i]) }
            return fromFloatArray(result, tensor.shape, tensor.dtype)
        }
        
        private fun reduceAlongAxis(
            tensor: EmberTensor,
            axis: Int,
            keepDims: Boolean,
            reduceOp: (FloatArray) -> Float
        ): EmberTensor {
            require(axis in 0 until tensor.ndim) { "Invalid axis $axis for ${tensor.ndim}D tensor" }
            
            // Simplified implementation for common cases
            if (tensor.ndim == 1) {
                val result = reduceOp(tensor.storage.toFloatArray())
                return if (keepDims) {
                    fromFloatArray(floatArrayOf(result), intArrayOf(1), tensor.dtype)
                } else {
                    fromScalar(Scalar.fromValue(result, tensor.dtype))
                }
            }
            
            // For 2D tensors
            if (tensor.ndim == 2) {
                val data = tensor.storage.toFloatArray()
                val rows = tensor.shape[0]
                val cols = tensor.shape[1]
                
                return if (axis == 0) {
                    // Reduce along rows (result has shape [cols])
                    val result = FloatArray(cols) { j ->
                        val column = FloatArray(rows) { i -> data[i * cols + j] }
                        reduceOp(column)
                    }
                    val newShape = if (keepDims) intArrayOf(1, cols) else intArrayOf(cols)
                    fromFloatArray(result, newShape, tensor.dtype)
                } else {
                    // Reduce along columns (result has shape [rows])
                    val result = FloatArray(rows) { i ->
                        val row = FloatArray(cols) { j -> data[i * cols + j] }
                        reduceOp(row)
                    }
                    val newShape = if (keepDims) intArrayOf(rows, 1) else intArrayOf(rows)
                    fromFloatArray(result, newShape, tensor.dtype)
                }
            }
            
            TODO("Reduction along axis not yet implemented for tensors with ndim > 2")
        }
    }
}

/**
 * Simple tensor storage interface.
 * 
 * For now, this is a wrapper around FloatArray. In the future, this will be
 * backed by KLang types (CFloat16, CFloat32, CFloat64) for true cross-platform
 * determinism.
 */
private class TensorStorage private constructor(
    private val data: FloatArray,
    private val dtype: DType
) {
    fun toFloatArray(): FloatArray = data.copyOf()
    
    companion object {
        fun fromFloatArray(data: FloatArray, dtype: DType): TensorStorage {
            return TensorStorage(data, dtype)
        }
    }
}
