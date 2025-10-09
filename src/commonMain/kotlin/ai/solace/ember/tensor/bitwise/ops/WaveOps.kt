package ai.solace.ember.tensor.bitwise.ops

import kotlin.math.*

/**
 * Binary wave operations for Ember ML Kotlin.
 * 
 * This module provides Kotlin implementations of operations specific to
 * binary wave processing, such as interference and propagation.
 */

/**
 * Interference modes for binary wave operations.
 */
enum class InterferenceMode {
    XOR,
    AND,
    OR
}

/**
 * Apply wave interference between multiple binary patterns element-wise.
 *
 * @param waves List of input arrays (must be integer type).
 *              All arrays must have the same size.
 * @param mode Interference type ('XOR', 'AND', or 'OR'). Defaults to 'XOR'.
 * @return Array representing the interference pattern.
 */
fun binaryWaveInterference(waves: List<IntArray>, mode: InterferenceMode = InterferenceMode.XOR): IntArray {
    require(waves.isNotEmpty()) { "Input list 'waves' cannot be empty." }
    
    val firstWave = waves[0]
    val result = firstWave.copyOf()
    
    for (i in 1 until waves.size) {
        val wave = waves[i]
        require(wave.size == firstWave.size) { "All waves must have the same size." }
        
        for (j in result.indices) {
            result[j] = when (mode) {
                InterferenceMode.XOR -> bitwiseXor(result[j], wave[j])
                InterferenceMode.AND -> bitwiseAnd(result[j], wave[j])
                InterferenceMode.OR -> bitwiseOr(result[j], wave[j])
            }
        }
    }
    
    return result
}

/**
 * Apply wave interference between multiple binary patterns element-wise (Long arrays).
 */
fun binaryWaveInterference(waves: List<LongArray>, mode: InterferenceMode = InterferenceMode.XOR): LongArray {
    require(waves.isNotEmpty()) { "Input list 'waves' cannot be empty." }
    
    val firstWave = waves[0]
    val result = firstWave.copyOf()
    
    for (i in 1 until waves.size) {
        val wave = waves[i]
        require(wave.size == firstWave.size) { "All waves must have the same size." }
        
        for (j in result.indices) {
            result[j] = when (mode) {
                InterferenceMode.XOR -> bitwiseXor(result[j], wave[j])
                InterferenceMode.AND -> bitwiseAnd(result[j], wave[j])
                InterferenceMode.OR -> bitwiseOr(result[j], wave[j])
            }
        }
    }
    
    return result
}

/**
 * Generate a blocky sine wave pattern using binary representation.
 *
 * @param length Length of the output array.
 * @param frequency Frequency of the sine wave (cycles per length).
 * @param amplitude Maximum amplitude of the wave (affects bit patterns).
 * @param phase Phase offset in radians.
 * @param bitWidth Number of bits to use for the pattern (default: 32).
 * @return IntArray representing the blocky sine wave pattern.
 */
fun generateBlockySin(
    length: Int,
    frequency: Double = 1.0,
    amplitude: Double = 1.0,
    phase: Double = 0.0,
    bitWidth: Int = 32
): IntArray {
    require(length > 0) { "Length must be positive" }
    require(bitWidth in 1..32) { "Bit width must be between 1 and 32" }
    
    val result = IntArray(length)
    val maxValue = (1 shl bitWidth) - 1
    
    for (i in 0 until length) {
        val t = i.toDouble() / length
        val sinValue = sin(2.0 * PI * frequency * t + phase)
        
        // Convert sine wave to blocky binary pattern
        val normalizedValue = (sinValue * amplitude + 1.0) / 2.0 // Normalize to [0, 1]
        val intValue = (normalizedValue * maxValue).toInt()
        
        // Create blocky pattern by quantizing to fewer levels
        val levels = 8 // Number of discrete levels
        val quantized = ((intValue / (maxValue.toDouble() / levels)).toInt() * (maxValue / levels))
        
        result[i] = quantized.coerceIn(0, maxValue)
    }
    
    return result
}

/**
 * Create a binary pattern with a specified duty cycle.
 *
 * @param length Length of the output array.
 * @param dutyCycle Duty cycle as a percentage (0.0 to 1.0).
 * @param period Period of the pattern in array elements.
 * @param highValue Value to use for the 'high' state (default: -1 for all bits set).
 * @param lowValue Value to use for the 'low' state (default: 0).
 * @return IntArray representing the duty cycle pattern.
 */
fun createDutyCycle(
    length: Int,
    dutyCycle: Double,
    period: Int,
    highValue: Int = -1, // All bits set
    lowValue: Int = 0
): IntArray {
    require(length > 0) { "Length must be positive" }
    require(dutyCycle in 0.0..1.0) { "Duty cycle must be between 0.0 and 1.0" }
    require(period > 0) { "Period must be positive" }
    
    val result = IntArray(length)
    val highDuration = (period * dutyCycle).toInt()
    
    for (i in 0 until length) {
        val positionInPeriod = i % period
        result[i] = if (positionInPeriod < highDuration) highValue else lowValue
    }
    
    return result
}

/**
 * Propagate a wave by shifting it.
 *
 * @param wave Input wave pattern.
 * @param shift Number of positions to shift (positive for right, negative for left).
 * @param wrapAround Whether to wrap around the array boundaries.
 * @return Array with the propagated (shifted) wave.
 */
fun propagate(wave: IntArray, shift: Int, wrapAround: Boolean = true): IntArray {
    val result = IntArray(wave.size)
    
    for (i in wave.indices) {
        val sourceIndex = if (wrapAround) {
            ((i - shift) % wave.size + wave.size) % wave.size
        } else {
            i - shift
        }
        
        result[i] = if (sourceIndex in wave.indices) {
            wave[sourceIndex]
        } else {
            0 // Fill with zeros if not wrapping
        }
    }
    
    return result
}

/**
 * Propagate a wave by shifting it (Long array version).
 */
fun propagate(wave: LongArray, shift: Int, wrapAround: Boolean = true): LongArray {
    val result = LongArray(wave.size)
    
    for (i in wave.indices) {
        val sourceIndex = if (wrapAround) {
            ((i - shift) % wave.size + wave.size) % wave.size
        } else {
            i - shift
        }
        
        result[i] = if (sourceIndex in wave.indices) {
            wave[sourceIndex]
        } else {
            0L // Fill with zeros if not wrapping
        }
    }
    
    return result
}

/**
 * Generate a complex wave pattern by combining multiple frequencies.
 *
 * @param length Length of the output array.
 * @param frequencies List of frequencies to combine.
 * @param amplitudes List of amplitudes for each frequency (must match frequencies size).
 * @param phases List of phase offsets for each frequency (optional).
 * @param mode Interference mode for combining frequencies.
 * @return IntArray representing the complex wave pattern.
 */
fun generateComplexWave(
    length: Int,
    frequencies: List<Double>,
    amplitudes: List<Double>,
    phases: List<Double> = frequencies.map { 0.0 },
    mode: InterferenceMode = InterferenceMode.XOR
): IntArray {
    require(frequencies.size == amplitudes.size) { "Frequencies and amplitudes must have the same size" }
    require(frequencies.size == phases.size) { "Frequencies and phases must have the same size" }
    
    val waves = frequencies.mapIndexed { index, frequency ->
        generateBlockySin(length, frequency, amplitudes[index], phases[index])
    }
    
    return binaryWaveInterference(waves, mode)
}