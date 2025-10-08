package ai.solace.klang.common

import kotlin.math.abs
import kotlin.random.Random

object StatOps {
    fun mean(values: FloatArray, offset: Int = 0, length: Int = values.size - offset): Float {
        require(offset >= 0 && length >= 0 && offset + length <= values.size)
        if (length == 0) return 0.0f
        var sum = 0.0f
        for (i in 0 until length) {
            sum += values[offset + i]
        }
        return sum / length
    }

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

    fun meanAbsoluteDeviation(values: FloatArray, offset: Int = 0, length: Int = values.size - offset, mean: Float = mean(values, offset, length)): Float {
        require(offset >= 0 && length >= 0 && offset + length <= values.size)
        if (length == 0) return 0.0f
        var acc = 0.0f
        for (i in 0 until length) {
            acc += abs(values[offset + i] - mean)
        }
        return acc / length
    }

    fun randomUniform(random: Random, min: Float, max: Float): Float {
        require(min <= max) { "min must be <= max" }
        if (min == max) return min
        val u = random.nextFloat()
        return min + (max - min) * u
    }
}
