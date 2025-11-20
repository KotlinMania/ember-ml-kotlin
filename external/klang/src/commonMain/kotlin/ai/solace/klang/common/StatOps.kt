package ai.solace.klang.common

import kotlin.math.abs
import kotlin.random.Random

/**
 * StatOps: Statistical operations on Float arrays.
 *
 * Provides efficient statistical computations commonly needed in numerical
 * computing, machine learning, and data analysis. All operations support
 * offset-based array slicing for working with subarrays without copying.
 *
 * ## Use Cases
 *
 * - **Machine Learning**: Feature normalization (mean/variance)
 * - **Data Analysis**: Descriptive statistics
 * - **Signal Processing**: Statistical measures of signals
 * - **Quality Control**: Deviation metrics
 * - **Random Initialization**: Uniform random values in ranges
 *
 * ## Operations
 *
 * | Operation | Formula | Complexity |
 * |-----------|---------|------------|
 * | Mean | Σx / n | O(n) |
 * | Variance | Σ(x-μ)² / n | O(n) |
 * | MAD | Σ\|x-μ\| / n | O(n) |
 * | Uniform Random | min + u*(max-min) | O(1) |
 *
 * ## Usage Example
 *
 * ### Basic Statistics
 * ```kotlin
 * val data = floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f, 5.0f)
 *
 * val avg = StatOps.mean(data)                    // 3.0
 * val variance = StatOps.variance(data)            // 2.0
 * val mad = StatOps.meanAbsoluteDeviation(data)   // 1.2
 * ```
 *
 * ### Offset and Length
 * ```kotlin
 * val buffer = FloatArray(100)
 * // ... fill buffer ...
 *
 * // Compute mean of elements [10, 30)
 * val partialMean = StatOps.mean(buffer, offset = 10, length = 20)
 * ```
 *
 * ### ML Feature Normalization
 * ```kotlin
 * fun normalizeFeatures(features: FloatArray): FloatArray {
 *     val mean = StatOps.mean(features)
 *     val variance = StatOps.variance(features, mean = mean)
 *     val stddev = sqrt(variance)
 *
 *     return features.map { (it - mean) / stddev }.toFloatArray()
 * }
 * ```
 *
 * ### Random Initialization
 * ```kotlin
 * val random = Random(42)
 * val weights = FloatArray(1000) {
 *     StatOps.randomUniform(random, -0.1f, 0.1f)
 * }
 * ```
 *
 * ## Performance
 *
 * All operations are single-pass (except when mean must be computed first):
 * - **Mean**: 1 pass through data
 * - **Variance**: 2 passes (1 for mean, 1 for variance) or 1 if mean provided
 * - **MAD**: 2 passes (1 for mean, 1 for MAD) or 1 if mean provided
 *
 * @since 0.1.0
 */
object StatOps {
    /**
     * Compute the arithmetic mean (average) of values.
     *
     * ## Formula
     * ```
     * mean = (Σ values[i]) / length
     * ```
     *
     * ## Example
     * ```kotlin
     * val data = floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f, 5.0f)
     * val avg = StatOps.mean(data)  // 3.0
     * ```
     *
     * @param values Array of Float values
     * @param offset Starting index (default: 0)
     * @param length Number of elements (default: values.size - offset)
     * @return Mean value, or 0.0 if length is 0
     * @throws IllegalArgumentException if offset/length invalid
     */
    fun mean(values: FloatArray, offset: Int = 0, length: Int = values.size - offset): Float {
        require(offset >= 0 && length >= 0 && offset + length <= values.size)
        if (length == 0) return 0.0f
        var sum = 0.0f
        for (i in 0 until length) {
            sum += values[offset + i]
        }
        return sum / length
    }

    /**
     * Compute the variance of values.
     *
     * ## Formula
     * ```
     * variance = Σ(x - mean)² / n
     * ```
     *
     * This is the population variance (divides by n, not n-1).
     *
     * ## Example
     * ```kotlin
     * val data = floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f, 5.0f)
     * val variance = StatOps.variance(data)  // 2.0
     * ```
     *
     * ## Performance Tip
     * If you already computed the mean, pass it to avoid recomputation:
     * ```kotlin
     * val m = StatOps.mean(data)
     * val v = StatOps.variance(data, mean = m)  // Saves one pass
     * ```
     *
     * @param values Array of Float values
     * @param offset Starting index (default: 0)
     * @param length Number of elements (default: values.size - offset)
     * @param mean Pre-computed mean (default: computed automatically)
     * @return Variance, or 0.0 if length is 0
     * @throws IllegalArgumentException if offset/length invalid
     */
    fun variance(values: FloatArray, offset: Int = 0, length: Int = values.size - offset, mean: Float = mean(values, offset, length)): Float {
        require(offset >= 0 && length >= 0 && offset + length <= values.size)
        if (length == 0) return 0.0f
        var acc = 0.0f
        for (i in 0 until length) {
            val delta = values[offset + i] - mean
            acc += delta * delta
        }
        return acc / length
    }

    /**
     * Compute the mean absolute deviation (MAD).
     *
     * ## Formula
     * ```
     * MAD = Σ|x - mean| / n
     * ```
     *
     * MAD measures the average distance from the mean, providing
     * a more robust alternative to standard deviation (less sensitive to outliers).
     *
     * ## Example
     * ```kotlin
     * val data = floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f, 100.0f)
     * val mad = StatOps.meanAbsoluteDeviation(data)
     * // Less affected by outlier (100) than stddev would be
     * ```
     *
     * ## Performance Tip
     * If you already computed the mean, pass it to avoid recomputation:
     * ```kotlin
     * val m = StatOps.mean(data)
     * val mad = StatOps.meanAbsoluteDeviation(data, mean = m)
     * ```
     *
     * @param values Array of Float values
     * @param offset Starting index (default: 0)
     * @param length Number of elements (default: values.size - offset)
     * @param mean Pre-computed mean (default: computed automatically)
     * @return Mean absolute deviation, or 0.0 if length is 0
     * @throws IllegalArgumentException if offset/length invalid
     */
    fun meanAbsoluteDeviation(values: FloatArray, offset: Int = 0, length: Int = values.size - offset, mean: Float = mean(values, offset, length)): Float {
        require(offset >= 0 && length >= 0 && offset + length <= values.size)
        if (length == 0) return 0.0f
        var acc = 0.0f
        for (i in 0 until length) {
            acc += abs(values[offset + i] - mean)
        }
        return acc / length
    }

    /**
     * Generate a random Float uniformly distributed in [min, max].
     *
     * ## Example
     * ```kotlin
     * val random = Random(42)
     *
     * // Random weight in [-0.1, 0.1]
     * val weight = StatOps.randomUniform(random, -0.1f, 0.1f)
     *
     * // Random probability in [0, 1]
     * val prob = StatOps.randomUniform(random, 0.0f, 1.0f)
     * ```
     *
     * ## Use Cases
     * - **ML Initialization**: Weight/bias initialization
     * - **Monte Carlo**: Random sampling
     * - **Simulation**: Random parameters
     * - **Testing**: Generate test data
     *
     * @param random Random number generator
     * @param min Minimum value (inclusive)
     * @param max Maximum value (inclusive)
     * @return Random Float in [min, max]
     * @throws IllegalArgumentException if min > max
     */
    fun randomUniform(random: Random, min: Float, max: Float): Float {
        require(min <= max) { "min must be <= max" }
        if (min == max) return min
        val u = random.nextFloat()
        return min + (max - min) * u
    }
}
