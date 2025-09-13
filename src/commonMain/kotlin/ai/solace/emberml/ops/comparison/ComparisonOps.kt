package ai.solace.emberml.ops.comparison

import ai.solace.emberml.tensor.common.EmberTensor

/**
 * Comparison operations interface.
 *
 * This interface defines the abstract interface for comparison operations
 * that can be implemented by different backends.
 */
interface ComparisonOps {
    
    // === Element-wise Comparison Operations ===
    
    /**
     * Element-wise equality comparison.
     * 
     * @param x First input tensor
     * @param y Second input tensor
     * @return Boolean tensor indicating element-wise equality
     */
    fun equal(x: EmberTensor, y: EmberTensor): EmberTensor
    
    /**
     * Element-wise inequality comparison.
     * 
     * @param x First input tensor
     * @param y Second input tensor
     * @return Boolean tensor indicating element-wise inequality
     */
    fun notEqual(x: EmberTensor, y: EmberTensor): EmberTensor
    
    /**
     * Element-wise less-than comparison.
     * 
     * @param x First input tensor
     * @param y Second input tensor
     * @return Boolean tensor indicating element-wise less-than
     */
    fun less(x: EmberTensor, y: EmberTensor): EmberTensor
    
    /**
     * Element-wise less-than-or-equal comparison.
     * 
     * @param x First input tensor
     * @param y Second input tensor
     * @return Boolean tensor indicating element-wise less-than-or-equal
     */
    fun lessEqual(x: EmberTensor, y: EmberTensor): EmberTensor
    
    /**
     * Element-wise greater-than comparison.
     * 
     * @param x First input tensor
     * @param y Second input tensor
     * @return Boolean tensor indicating element-wise greater-than
     */
    fun greater(x: EmberTensor, y: EmberTensor): EmberTensor
    
    /**
     * Element-wise greater-than-or-equal comparison.
     * 
     * @param x First input tensor
     * @param y Second input tensor
     * @return Boolean tensor indicating element-wise greater-than-or-equal
     */
    fun greaterEqual(x: EmberTensor, y: EmberTensor): EmberTensor
    
    // === Logical Operations ===
    
    /**
     * Element-wise logical AND.
     * 
     * @param x First input tensor
     * @param y Second input tensor
     * @return Boolean tensor indicating element-wise logical AND
     */
    fun logicalAnd(x: EmberTensor, y: EmberTensor): EmberTensor
    
    /**
     * Element-wise logical OR.
     * 
     * @param x First input tensor
     * @param y Second input tensor
     * @return Boolean tensor indicating element-wise logical OR
     */
    fun logicalOr(x: EmberTensor, y: EmberTensor): EmberTensor
    
    /**
     * Element-wise logical NOT.
     * 
     * @param x Input tensor
     * @return Boolean tensor indicating element-wise logical NOT
     */
    fun logicalNot(x: EmberTensor): EmberTensor
    
    /**
     * Element-wise logical XOR.
     * 
     * @param x First input tensor
     * @param y Second input tensor
     * @return Boolean tensor indicating element-wise logical XOR
     */
    fun logicalXor(x: EmberTensor, y: EmberTensor): EmberTensor
    
    // === Tolerance-based Comparison ===
    
    /**
     * Returns whether all elements are close.
     * 
     * @param x First input tensor
     * @param y Second input tensor
     * @param rtol Relative tolerance parameter
     * @param atol Absolute tolerance parameter
     * @return True if all elements are close within tolerance
     */
    fun allClose(x: EmberTensor, y: EmberTensor, rtol: Double = 1e-5, atol: Double = 1e-8): Boolean
    
    /**
     * Returns whether each element is close.
     * 
     * @param x First input tensor
     * @param y Second input tensor
     * @param rtol Relative tolerance parameter
     * @param atol Absolute tolerance parameter
     * @return Boolean tensor indicating element-wise closeness within tolerance
     */
    fun isClose(x: EmberTensor, y: EmberTensor, rtol: Double = 1e-5, atol: Double = 1e-8): EmberTensor
    
    // === Aggregate Boolean Operations ===
    
    /**
     * Test whether all elements evaluate to True.
     * 
     * @param x Input tensor
     * @param axis Axis or axes along which to perform the test
     * @param keepDims Whether to keep the reduced dimensions
     * @return True if all elements evaluate to True
     */
    fun all(x: EmberTensor, axis: IntArray? = null, keepDims: Boolean = false): EmberTensor
    
    /**
     * Test whether any elements evaluate to True.
     * 
     * @param x Input tensor
     * @param axis Axis or axes along which to perform the test
     * @param keepDims Whether to keep the reduced dimensions
     * @return True if any elements evaluate to True
     */
    fun any(x: EmberTensor, axis: IntArray? = null, keepDims: Boolean = false): EmberTensor
    
    // === Conditional Selection ===
    
    /**
     * Return elements chosen from x or y depending on condition.
     * 
     * @param condition Boolean tensor indicating which elements to choose
     * @param x First choice tensor
     * @param y Second choice tensor
     * @return Tensor with elements chosen from x where condition is true, y otherwise
     */
    fun where(condition: EmberTensor, x: EmberTensor, y: EmberTensor): EmberTensor
    
    // === Special Value Testing ===
    
    /**
     * Test element-wise for NaN.
     * 
     * @param x Input tensor
     * @return Boolean tensor indicating element-wise NaN values
     */
    fun isNan(x: EmberTensor): EmberTensor
    
    /**
     * Test element-wise for positive or negative infinity.
     * 
     * @param x Input tensor
     * @return Boolean tensor indicating element-wise infinite values
     */
    fun isInf(x: EmberTensor): EmberTensor
    
    /**
     * Test element-wise for finite values (not infinity and not NaN).
     * 
     * @param x Input tensor
     * @return Boolean tensor indicating element-wise finite values
     */
    fun isFinite(x: EmberTensor): EmberTensor
}