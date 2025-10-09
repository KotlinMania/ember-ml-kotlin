package ai.solace.ember.tensor.ops.creation

import ai.solace.ember.tensor.common.DType
import ai.solace.ember.tensor.common.EmberShape
import ai.solace.ember.tensor.common.EmberTensor
import ai.solace.ember.tensor.common.float32

/**
 * Creates a tensor filled with zeros.
 *
 * @param shape The shape of the tensor.
 * @param dtype The data type of the tensor.
 * @param device The device where the tensor is stored.
 * @param requiresGrad Whether the tensor requires gradients.
 * @return A tensor filled with zeros.
 */
fun zeros(
    shape: IntArray,
    dtype: DType = float32,
    device: String = "cpu",
    requiresGrad: Boolean = false
): EmberTensor {
    // This would delegate to the backend implementation
    // For now, we'll create a list of zeros and create a tensor from it
    val size = shape.fold(1) { acc, dim -> acc * dim }
    val data = List(size) { 0 }
    return EmberTensor(data, dtype, device, requiresGrad)
}

/**
 * Creates a tensor filled with ones.
 *
 * @param shape The shape of the tensor.
 * @param dtype The data type of the tensor.
 * @param device The device where the tensor is stored.
 * @param requiresGrad Whether the tensor requires gradients.
 * @return A tensor filled with ones.
 */
fun ones(
    shape: IntArray,
    dtype: DType = float32,
    device: String = "cpu",
    requiresGrad: Boolean = false
): EmberTensor {
    // This would delegate to the backend implementation
    // For now, we'll create a list of ones and create a tensor from it
    val size = shape.fold(1) { acc, dim -> acc * dim }
    val data = List(size) { 1 }
    return EmberTensor(data, dtype, device, requiresGrad)
}

/**
 * Creates a tensor with values from a specified range.
 *
 * @param start The start of the range.
 * @param end The end of the range (exclusive).
 * @param step The step size.
 * @param dtype The data type of the tensor.
 * @param device The device where the tensor is stored.
 * @param requiresGrad Whether the tensor requires gradients.
 * @return A tensor with values from the specified range.
 */
fun arange(
    start: Int,
    end: Int,
    step: Int = 1,
    dtype: DType = float32,
    device: String = "cpu",
    requiresGrad: Boolean = false
): EmberTensor {
    // This would delegate to the backend implementation
    // For now, we'll create a list of values and create a tensor from it
    val data = (start until end step step).toList()
    return EmberTensor(data, dtype, device, requiresGrad)
}

/**
 * Creates a tensor with the specified value.
 *
 * @param value The value to fill the tensor with.
 * @param shape The shape of the tensor.
 * @param dtype The data type of the tensor.
 * @param device The device where the tensor is stored.
 * @param requiresGrad Whether the tensor requires gradients.
 * @return A tensor filled with the specified value.
 */
fun full(
    value: Number,
    shape: IntArray,
    dtype: DType = float32,
    device: String = "cpu",
    requiresGrad: Boolean = false
): EmberTensor {
    // This would delegate to the backend implementation
    // For now, we'll create a list of values and create a tensor from it
    val size = shape.fold(1) { acc, dim -> acc * dim }
    val data = List(size) { value }
    return EmberTensor(data, dtype, device, requiresGrad)
}

/**
 * Creates an identity matrix.
 *
 * @param n The size of the matrix.
 * @param dtype The data type of the tensor.
 * @param device The device where the tensor is stored.
 * @param requiresGrad Whether the tensor requires gradients.
 * @return An identity matrix.
 */
fun eye(
    n: Int,
    dtype: DType = float32,
    device: String = "cpu",
    requiresGrad: Boolean = false
): EmberTensor {
    // This would delegate to the backend implementation
    // For now, we'll create a 2D list representing an identity matrix
    val data = List(n) { i ->
        List(n) { j ->
            if (i == j) 1 else 0
        }
    }
    return EmberTensor(data, dtype, device, requiresGrad)
}
