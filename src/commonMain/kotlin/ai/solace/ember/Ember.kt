package ai.solace.ember

import ai.solace.ember.dtype.DType
import ai.solace.ember.scalar.Scalar
import ai.solace.ember.tensor.EmberTensor
import ai.solace.klang.fp.CFloat16
import ai.solace.klang.fp.CFloat32
import ai.solace.klang.fp.CDouble

/**
 * Main Ember ML API - MLX-style interface for tensor operations.
 * 
 * This is the primary entry point for creating and manipulating tensors with
 * KLang-backed cross-platform determinism.
 * 
 * Example usage:
 * ```kotlin
 * val x = Ember.array(listOf(1.0f, 2.0f, 3.0f))
 * val y = Ember.zeros(intArrayOf(3, 3), dtype = Ember.float32)
 * val z = Ember.sin(x) + y
 * ```
 */
object Ember {
    
    // ============================================
    // DType namespace for easy access
    // ============================================
    
    /**
     * Floating point types.
     */
    val float16: DType get() = DType.Float16
    val float32: DType get() = DType.Float32
    val float64: DType get() = DType.Float64
    val float128: DType get() = DType.Float128
    val bfloat16: DType get() = DType.BFloat16
    
    /**
     * Integer types.
     */
    val int8: DType get() = DType.Int8
    val int16: DType get() = DType.Int16
    val int32: DType get() = DType.Int32
    val int64: DType get() = DType.Int64
    
    val uint8: DType get() = DType.UInt8
    val uint16: DType get() = DType.UInt16
    val uint32: DType get() = DType.UInt32
    val uint64: DType get() = DType.UInt64
    
    /**
     * Boolean type.
     */
    val bool: DType get() = DType.Bool
    
    /**
     * Complex types.
     */
    val complex64: DType get() = DType.Complex64
    val complex128: DType get() = DType.Complex128
    
    // ============================================
    // Tensor creation functions
    // ============================================
    
    /**
     * Create a tensor from a scalar value.
     */
    fun array(value: Number, dtype: DType = DType.Float32): EmberTensor {
        val scalar = Scalar.fromValue(value, dtype)
        return EmberTensor.fromScalar(scalar)
    }
    
    /**
     * Create a tensor from a 1D list.
     */
    fun array(values: List<Number>, dtype: DType = DType.Float32): EmberTensor {
        return EmberTensor.fromList(values, dtype)
    }
    
    /**
     * Create a tensor from a 2D list (matrix).
     */
    fun array(values: List<List<Number>>, dtype: DType = DType.Float32): EmberTensor {
        return EmberTensor.fromList2D(values, dtype)
    }
    
    /**
     * Create a tensor filled with zeros.
     */
    fun zeros(shape: IntArray, dtype: DType = DType.Float32): EmberTensor {
        return EmberTensor.zeros(shape, dtype)
    }
    
    /**
     * Create a tensor filled with ones.
     */
    fun ones(shape: IntArray, dtype: DType = DType.Float32): EmberTensor {
        return EmberTensor.ones(shape, dtype)
    }
    
    /**
     * Create a tensor filled with a constant value.
     */
    fun full(shape: IntArray, value: Number, dtype: DType = DType.Float32): EmberTensor {
        return EmberTensor.full(shape, value, dtype)
    }
    
    /**
     * Create an identity matrix.
     */
    fun eye(n: Int, dtype: DType = DType.Float32): EmberTensor {
        return EmberTensor.eye(n, dtype)
    }
    
    /**
     * Create a tensor with evenly spaced values in a range.
     */
    fun arange(start: Number, stop: Number, step: Number = 1, dtype: DType = DType.Float32): EmberTensor {
        return EmberTensor.arange(start, stop, step, dtype)
    }
    
    /**
     * Create a tensor with linearly spaced values.
     */
    fun linspace(start: Number, stop: Number, num: Int = 50, dtype: DType = DType.Float32): EmberTensor {
        return EmberTensor.linspace(start, stop, num, dtype)
    }
    
    // ============================================
    // Mathematical operations
    // ============================================
    
    /**
     * Element-wise sine.
     */
    fun sin(x: EmberTensor): EmberTensor = x.sin()
    
    /**
     * Element-wise cosine.
     */
    fun cos(x: EmberTensor): EmberTensor = x.cos()
    
    /**
     * Element-wise tangent.
     */
    fun tan(x: EmberTensor): EmberTensor = x.tan()
    
    /**
     * Element-wise exponential.
     */
    fun exp(x: EmberTensor): EmberTensor = x.exp()
    
    /**
     * Element-wise natural logarithm.
     */
    fun log(x: EmberTensor): EmberTensor = x.log()
    
    /**
     * Element-wise square root.
     */
    fun sqrt(x: EmberTensor): EmberTensor = x.sqrt()
    
    /**
     * Element-wise absolute value.
     */
    fun abs(x: EmberTensor): EmberTensor = x.abs()
    
    /**
     * Element-wise square.
     */
    fun square(x: EmberTensor): EmberTensor = x.square()
    
    /**
     * Element-wise power.
     */
    fun power(x: EmberTensor, exponent: Number): EmberTensor = x.power(exponent)
    
    // ============================================
    // Reduction operations
    // ============================================
    
    /**
     * Sum of all elements.
     */
    fun sum(x: EmberTensor, axis: Int? = null, keepDims: Boolean = false): EmberTensor {
        return x.sum(axis, keepDims)
    }
    
    /**
     * Mean of all elements.
     */
    fun mean(x: EmberTensor, axis: Int? = null, keepDims: Boolean = false): EmberTensor {
        return x.mean(axis, keepDims)
    }
    
    /**
     * Maximum element.
     */
    fun max(x: EmberTensor, axis: Int? = null, keepDims: Boolean = false): EmberTensor {
        return x.max(axis, keepDims)
    }
    
    /**
     * Minimum element.
     */
    fun min(x: EmberTensor, axis: Int? = null, keepDims: Boolean = false): EmberTensor {
        return x.min(axis, keepDims)
    }
    
    // ============================================
    // Shape operations
    // ============================================
    
    /**
     * Reshape a tensor to a new shape.
     */
    fun reshape(x: EmberTensor, newShape: IntArray): EmberTensor {
        return x.reshape(newShape)
    }
    
    /**
     * Transpose a tensor.
     */
    fun transpose(x: EmberTensor, axes: IntArray? = null): EmberTensor {
        return x.transpose(axes)
    }
    
    /**
     * Matrix multiplication.
     */
    fun matmul(a: EmberTensor, b: EmberTensor): EmberTensor {
        return a.matmul(b)
    }
    
    // ============================================
    // Utility functions
    // ============================================
    
    /**
     * Get the shape of a tensor.
     */
    fun shape(x: EmberTensor): IntArray = x.shape
    
    /**
     * Get the number of dimensions.
     */
    fun ndim(x: EmberTensor): Int = x.ndim
    
    /**
     * Get the total number of elements.
     */
    fun size(x: EmberTensor): Int = x.size
    
    /**
     * Get the data type.
     */
    fun dtype(x: EmberTensor): DType = x.dtype
}

/**
 * Type alias for convenience (MLX-style).
 */
typealias Tensor = EmberTensor
