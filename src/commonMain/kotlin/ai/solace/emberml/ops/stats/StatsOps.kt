package ai.solace.emberml.ops.stats

import ai.solace.emberml.tensor.common.EmberTensor

/**
 * Statistical operations interface.
 *
 * This interface defines the abstract interface for statistical operations
 * that can be implemented by different backends.
 */
interface StatsOps {
    
    // === Descriptive Statistics ===
    
    /**
     * Compute the mean of a tensor along specified axes.
     * 
     * @param x Input tensor
     * @param axis Axis or axes along which to compute the mean
     * @param keepDims Whether to keep the reduced dimensions
     * @return Mean of the tensor
     */
    fun mean(x: EmberTensor, axis: IntArray? = null, keepDims: Boolean = false): EmberTensor
    
    /**
     * Compute the variance along the specified axis.
     * 
     * @param x Input tensor
     * @param axis Axis or axes along which to compute the variance
     * @param keepDims Whether to keep the reduced dimensions
     * @param ddof Delta degrees of freedom
     * @return Variance of the tensor
     */
    fun variance(x: EmberTensor, axis: IntArray? = null, keepDims: Boolean = false, ddof: Int = 0): EmberTensor
    
    /**
     * Compute the median along the specified axis.
     * 
     * @param x Input tensor
     * @param axis Axis or axes along which to compute the median
     * @param keepDims Whether to keep the reduced dimensions
     * @return Median of the tensor
     */
    fun median(x: EmberTensor, axis: IntArray? = null, keepDims: Boolean = false): EmberTensor
    
    /**
     * Compute the standard deviation along the specified axis.
     * 
     * @param x Input tensor
     * @param axis Axis or axes along which to compute the standard deviation
     * @param keepDims Whether to keep the reduced dimensions
     * @param ddof Delta degrees of freedom
     * @return Standard deviation of the tensor
     */
    fun standardDeviation(x: EmberTensor, axis: IntArray? = null, keepDims: Boolean = false, ddof: Int = 0): EmberTensor
    
    /**
     * Compute the q-th percentile along the specified axis.
     * 
     * @param x Input tensor
     * @param axis Axis or axes along which to compute the percentile
     * @param keepDims Whether to keep the reduced dimensions
     * @return q-th percentile of the tensor
     */
    fun percentile(x: EmberTensor, q: Double, axis: IntArray? = null, keepDims: Boolean = false): EmberTensor
    
    /**
     * Compute the maximum value along the specified axis.
     * 
     * @param x Input tensor
     * @param axis Axis or axes along which to compute the maximum
     * @param keepDims Whether to keep the reduced dimensions
     * @return Maximum value of the tensor
     */
    fun max(x: EmberTensor, axis: IntArray? = null, keepDims: Boolean = false): EmberTensor
    
    /**
     * Compute the minimum value along the specified axis.
     * 
     * @param x Input tensor
     * @param axis Axis or axes along which to compute the minimum
     * @param keepDims Whether to keep the reduced dimensions
     * @return Minimum value of the tensor
     */
    fun min(x: EmberTensor, axis: IntArray? = null, keepDims: Boolean = false): EmberTensor
    
    /**
     * Compute the sum along the specified axis.
     * 
     * @param x Input tensor
     * @param axis Axis or axes along which to compute the sum
     * @param keepDims Whether to keep the reduced dimensions
     * @return Sum of the tensor
     */
    fun sum(x: EmberTensor, axis: IntArray? = null, keepDims: Boolean = false): EmberTensor
    
    /**
     * Compute the cumulative sum along the specified axis.
     * 
     * @param x Input tensor
     * @param axis Axis along which to compute the cumulative sum
     * @return Cumulative sum of the tensor
     */
    fun cumSum(x: EmberTensor, axis: Int? = null): EmberTensor
    
    /**
     * Returns the indices of the maximum values along an axis.
     * 
     * @param x Input tensor
     * @param axis Axis along which to compute the argmax
     * @param keepDims Whether to keep the reduced dimensions
     * @return Indices of the maximum values
     */
    fun argMax(x: EmberTensor, axis: Int? = null, keepDims: Boolean = false): EmberTensor
    
    /**
     * Sort a tensor along the specified axis.
     * 
     * @param x Input tensor
     * @param axis Axis along which to sort
     * @param descending Whether to sort in descending order
     * @return Sorted tensor
     */
    fun sort(x: EmberTensor, axis: Int = -1, descending: Boolean = false): EmberTensor
    
    /**
     * Returns the indices that would sort a tensor along the specified axis.
     * 
     * @param x Input tensor
     * @param axis Axis along which to sort
     * @param descending Whether to sort in descending order
     * @return Indices that would sort the tensor
     */
    fun argSort(x: EmberTensor, axis: Int = -1, descending: Boolean = false): EmberTensor

    // === Probability Distributions ===
    
    /**
     * Compute the value of the Gaussian (normal distribution) function.
     *
     * Formula: (1 / (sigma * sqrt(2 * pi))) * exp(-0.5 * ((x - mu) / sigma)^2)
     *
     * @param inputValue The input value(s)
     * @param mu The mean (center) of the distribution. Defaults to 0.0
     * @param sigma The standard deviation (spread) of the distribution. Defaults to 1.0
     * @return The Gaussian function evaluated at the input value(s)
     */
    fun gaussian(inputValue: EmberTensor, mu: Double = 0.0, sigma: Double = 1.0): EmberTensor
}