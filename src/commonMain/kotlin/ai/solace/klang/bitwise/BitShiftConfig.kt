package ai.solace.klang.bitwise

/** Global configuration for BitShiftEngine defaults. */
object BitShiftConfig {
    @kotlin.concurrent.Volatile
    var defaultMode: BitShiftMode = BitShiftMode.AUTO
        set(value) {
            field = value
            resolvedModes.clear()
        }

    private val resolvedModes: MutableMap<Int, BitShiftMode> = mutableMapOf()

    fun withMode(mode: BitShiftMode, block: () -> Unit) {
        val prev = defaultMode
        try {
            defaultMode = mode
            block()
        } finally {
            defaultMode = prev
        }
    }

    fun resolveMode(
        bitWidth: Int,
        requested: BitShiftMode = defaultMode,
    ): BitShiftMode {
        return when (requested) {
            BitShiftMode.NATIVE,
            BitShiftMode.ARITHMETIC,
            -> requested

            BitShiftMode.AUTO -> resolvedModes[bitWidth] ?: detectMode(bitWidth).also {
                resolvedModes[bitWidth] = it
            }
        }
    }

    private fun detectMode(bitWidth: Int): BitShiftMode {
        if (bitWidth == 64) {
            return BitShiftMode.NATIVE
        }

        val nativeEngine = BitShiftEngine(BitShiftMode.NATIVE, bitWidth)
        val arithmeticEngine = BitShiftEngine(BitShiftMode.ARITHMETIC, bitWidth)

        val maxValue = if (bitWidth == 64) Long.MAX_VALUE else ((1L shl bitWidth) - 1L)
        val signBit = if (bitWidth > 1) 1L shl (bitWidth - 1) else 0L

        val alternatingLow = generatePattern(bitWidth) { index -> index % 2 == 0 }
        val alternatingHigh = generatePattern(bitWidth) { index -> index % 2 == 1 }

        val testValues = listOf(
            0L,
            1L,
            maxValue,
            maxValue - 1,
            signBit,
            alternatingLow,
            alternatingHigh,
        ).distinct()

        for (value in testValues) {
            for (bits in 0 until bitWidth) {
                val nativeLeft = nativeEngine.leftShift(value, bits)
                val arithmeticLeft = arithmeticEngine.leftShift(value, bits)
                if (nativeLeft.value != arithmeticLeft.value || nativeLeft.carry != arithmeticLeft.carry) {
                    return BitShiftMode.ARITHMETIC
                }

                val nativeRight = nativeEngine.rightShift(value, bits)
                val arithmeticRight = arithmeticEngine.rightShift(value, bits)
                if (nativeRight.value != arithmeticRight.value) {
                    return BitShiftMode.ARITHMETIC
                }

                val nativeUnsignedRight = nativeEngine.unsignedRightShift(value, bits)
                val arithmeticUnsignedRight = arithmeticEngine.unsignedRightShift(value, bits)
                if (nativeUnsignedRight.value != arithmeticUnsignedRight.value) {
                    return BitShiftMode.ARITHMETIC
                }
            }
        }

        return BitShiftMode.NATIVE
    }

    private fun generatePattern(
        bitWidth: Int,
        predicate: (index: Int) -> Boolean,
    ): Long {
        var value = 0L
        for (index in 0 until bitWidth) {
            if (index >= 63) break // avoid shifting into sign bit
            if (predicate(index)) {
                value = value or (1L shl index)
            }
        }
        return value
    }
}
