package ai.solace.emberml.ops.math

import ai.solace.emberml.tensor.common.EmberTensor

/**
 * Mathematical operations interface.
 *
 * This interface defines the abstract interface for mathematical operations
 * that can be implemented by different backends.
 */
interface MathOps {
    
    // === Basic Arithmetic Operations ===
    
    /**
     * Element-wise addition of tensors.
     * 
     * @param x First input tensor
     * @param y Second input tensor
     * @return Element-wise sum of x and y
     */
    fun add(x: EmberTensor, y: EmberTensor): EmberTensor
    
    /**
     * Element-wise subtraction of tensors.
     * 
     * @param x First input tensor  
     * @param y Second input tensor
     * @return Element-wise difference of x and y
     */
    fun subtract(x: EmberTensor, y: EmberTensor): EmberTensor
    
    /**
     * Element-wise multiplication of tensors.
     * 
     * @param x First input tensor
     * @param y Second input tensor
     * @return Element-wise product of x and y
     */
    fun multiply(x: EmberTensor, y: EmberTensor): EmberTensor
    
    /**
     * Element-wise division of tensors.
     * 
     * @param x First input tensor
     * @param y Second input tensor
     * @return Element-wise division of x by y
     */
    fun divide(x: EmberTensor, y: EmberTensor): EmberTensor
    
    /**
     * Element-wise floor division of tensors.
     * 
     * @param x First input tensor
     * @param y Second input tensor
     * @return Element-wise floor division of x by y
     */
    fun floorDivide(x: EmberTensor, y: EmberTensor): EmberTensor
    
    /**
     * Element-wise remainder of division.
     * 
     * @param x First input tensor
     * @param y Second input tensor
     * @return Element-wise remainder of x divided by y
     */
    fun mod(x: EmberTensor, y: EmberTensor): EmberTensor
    
    /**
     * Element-wise power function.
     * 
     * @param x Base tensor
     * @param y Exponent tensor
     * @return Element-wise power of x raised to y
     */
    fun pow(x: EmberTensor, y: EmberTensor): EmberTensor
    
    // === Matrix Operations ===
    
    /**
     * Dot product of tensors.
     * 
     * @param x First input tensor
     * @param y Second input tensor
     * @return Dot product of x and y
     */
    fun dot(x: EmberTensor, y: EmberTensor): EmberTensor
    
    /**
     * Matrix multiplication of tensors.
     * 
     * @param x First input tensor
     * @param y Second input tensor
     * @return Matrix multiplication of x and y
     */
    fun matmul(x: EmberTensor, y: EmberTensor): EmberTensor
    
    // === Exponential and Logarithmic Functions ===
    
    /**
     * Element-wise exponential of tensor.
     * 
     * @param x Input tensor
     * @return Element-wise exponential of x
     */
    fun exp(x: EmberTensor): EmberTensor
    
    /**
     * Element-wise natural logarithm of tensor.
     * 
     * @param x Input tensor
     * @return Element-wise natural logarithm of x
     */
    fun log(x: EmberTensor): EmberTensor
    
    /**
     * Element-wise base-10 logarithm of tensor.
     * 
     * @param x Input tensor
     * @return Element-wise base-10 logarithm of x
     */
    fun log10(x: EmberTensor): EmberTensor
    
    /**
     * Element-wise base-2 logarithm of tensor.
     * 
     * @param x Input tensor
     * @return Element-wise base-2 logarithm of x
     */
    fun log2(x: EmberTensor): EmberTensor
    
    // === Power and Root Functions ===
    
    /**
     * Element-wise square root of tensor.
     * 
     * @param x Input tensor
     * @return Element-wise square root of x
     */
    fun sqrt(x: EmberTensor): EmberTensor
    
    /**
     * Element-wise square of tensor.
     * 
     * @param x Input tensor
     * @return Element-wise square of x
     */
    fun square(x: EmberTensor): EmberTensor
    
    // === Sign and Absolute Value Functions ===
    
    /**
     * Element-wise absolute value of tensor.
     * 
     * @param x Input tensor
     * @return Element-wise absolute value of x
     */
    fun abs(x: EmberTensor): EmberTensor
    
    /**
     * Element-wise negation of tensor.
     * 
     * @param x Input tensor
     * @return Element-wise negation of x
     */
    fun negative(x: EmberTensor): EmberTensor
    
    /**
     * Element-wise sign of tensor.
     * 
     * @param x Input tensor
     * @return Element-wise sign of x
     */
    fun sign(x: EmberTensor): EmberTensor
    
    // === Clipping and Rounding Functions ===
    
    /**
     * Element-wise clipping of tensor values.
     * 
     * @param x Input tensor
     * @param minVal Minimum value
     * @param maxVal Maximum value
     * @return Element-wise clipped values of x
     */
    fun clip(x: EmberTensor, minVal: Double, maxVal: Double): EmberTensor
    
    /**
     * Element-wise floor of tensor.
     * 
     * @param x Input tensor
     * @return Element-wise floor of x
     */
    fun floor(x: EmberTensor): EmberTensor
    
    /**
     * Element-wise ceiling of tensor.
     * 
     * @param x Input tensor
     * @return Element-wise ceiling of x
     */
    fun ceil(x: EmberTensor): EmberTensor
    
    // === Trigonometric Functions ===
    
    /**
     * Element-wise sine of tensor.
     * 
     * @param x Input tensor
     * @return Element-wise sine of x
     */
    fun sin(x: EmberTensor): EmberTensor
    
    /**
     * Element-wise cosine of tensor.
     * 
     * @param x Input tensor
     * @return Element-wise cosine of x
     */
    fun cos(x: EmberTensor): EmberTensor
    
    /**
     * Element-wise tangent of tensor.
     * 
     * @param x Input tensor
     * @return Element-wise tangent of x
     */
    fun tan(x: EmberTensor): EmberTensor
    
    /**
     * Element-wise hyperbolic sine of tensor.
     * 
     * @param x Input tensor
     * @return Element-wise hyperbolic sine of x
     */
    fun sinh(x: EmberTensor): EmberTensor
    
    /**
     * Element-wise hyperbolic cosine of tensor.
     * 
     * @param x Input tensor
     * @return Element-wise hyperbolic cosine of x
     */
    fun cosh(x: EmberTensor): EmberTensor
    
    /**
     * Element-wise hyperbolic tangent of tensor.
     * 
     * @param x Input tensor
     * @return Element-wise hyperbolic tangent of x
     */
    fun tanh(x: EmberTensor): EmberTensor
    
    // === Gradient Operations ===
    
    /**
     * Compute gradient of a tensor.
     * 
     * @param x Input tensor
     * @param axis Axis along which to compute the gradient
     * @return Gradient of x
     */
    fun gradient(x: EmberTensor, axis: Int? = null): EmberTensor
}