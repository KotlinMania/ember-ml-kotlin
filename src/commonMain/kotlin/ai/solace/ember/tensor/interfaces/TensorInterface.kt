package ai.solace.ember.tensor.interfaces

import ai.solace.ember.tensor.common.DType
import ai.solace.ember.tensor.common.EmberShape

/**
 * Interface for all tensor implementations.
 * This is the core interface that all tensor implementations must implement.
 */
interface TensorInterface {
    /**
     * The shape of the tensor.
     */
    val shape: EmberShape

    /**
     * The data type of the tensor.
     */
    val dtype: DType

    /**
     * The device where the tensor is stored.
     */
    val device: String

    /**
     * Whether the tensor requires gradients.
     */
    val requiresGrad: Boolean

    /**
     * The number of dimensions in the tensor.
     */
    val ndim: Int
        get() = shape.size

    /**
     * The total number of elements in the tensor.
     */
    val size: Int
        get() = shape.dimensions.fold(1) { acc: Int, dim: Int -> acc * dim }

    /**
     * Casts the tensor to a different data type.
     *
     * @param dtype The target data type.
     * @return A new tensor with the same data but different data type.
     */
    fun cast(dtype: DType): TensorInterface

    /**
     * Reshapes the tensor to a new shape.
     *
     * @param newShape The new shape.
     * @return A new tensor with the same data but different shape.
     */
    fun reshape(newShape: EmberShape): TensorInterface

    /**
     * Transposes the tensor.
     *
     * @param axes The permutation of the dimensions. If null, reverses the dimensions.
     * @return A new tensor with the dimensions permuted.
     */
    fun transpose(axes: IntArray? = null): TensorInterface

    /**
     * Converts the tensor to a string representation.
     *
     * @return A string representation of the tensor.
     */
    override fun toString(): String
}
