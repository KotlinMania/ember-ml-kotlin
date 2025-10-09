package ai.solace.ember.tensor.bitwise

/**
 * MegaInteger class for integer-specific math operations.
 * Inherits from MegaNumber but forces is_float=false.
 */
class MegaInteger : MegaNumber, PowerOperations {
    /**
     * Initialize a MegaInteger object.
     *
     * @param mantissa IntArray of limbs (32-bit chunks)
     * @param negative Sign flag
     * @param keepLeadingZeros Whether to keep leading zeros
     */
    constructor(
        mantissa: IntArray = intArrayOf(0),
        negative: Boolean = false,
        keepLeadingZeros: Boolean = false
    ) : super(
        mantissa = mantissa,
        exponent = MegaNumber(intArrayOf(0)),
        negative = negative,
        isFloat = false,
        keepLeadingZeros = keepLeadingZeros
    )

    /**
     * Initialize a MegaInteger from another MegaNumber.
     * If the input is a float, it will be truncated to an integer.
     *
     * @param other Another MegaNumber object
     */
    constructor(other: MegaNumber) : super(
        mantissa = other.mantissa.copyOf(),
        exponent = MegaNumber(intArrayOf(0)),
        negative = other.negative,
        isFloat = false,
        keepLeadingZeros = other.keepLeadingZeros
    )

    /**
     * String representation.
     *
     * @return String representation
     */
    override fun toString(): String {
        return "<MegaInteger ${toDecimalString()}>"
    }

    /**
     * Add two MegaIntegers.
     *
     * @param other Another MegaInteger
     * @return Sum as a MegaInteger
     */
    fun add(other: MegaInteger): MegaInteger {
        val result = super.add(other)
        return MegaInteger(result)
    }

    /**
     * Subtract two MegaIntegers.
     *
     * @param other Another MegaInteger
     * @return Difference as a MegaInteger
     */
    fun sub(other: MegaInteger): MegaInteger {
        val result = super.sub(other)
        return MegaInteger(result)
    }

    /**
     * Multiply two MegaIntegers.
     *
     * @param other Another MegaInteger
     * @return Product as a MegaInteger
     */
    fun mul(other: MegaInteger): MegaInteger {
        val result = super.mul(other)
        return MegaInteger(result)
    }

    /**
     * Divide two MegaIntegers.
     *
     * @param other Another MegaInteger
     * @return Quotient as a MegaInteger
     */
    fun div(other: MegaInteger): MegaInteger {
        val result = super.divide(other)
        return MegaInteger(result)
    }

    /**
     * Compute the modulo of this integer with another.
     *
     * @param other The MegaInteger to modulo with
     * @return A new MegaInteger representing the result
     */
    fun mod(other: MegaInteger): MegaInteger {
        // Calculate quotient using divide
        val quotient = this.div(other)

        // Calculate remainder: this - (quotient * other)
        val product = quotient.mul(other)
        val remainder = this.sub(product)

        return remainder
    }

    /**
     * Compute the square root of this MegaInteger.
     *
     * @return The square root as a MegaInteger (truncated)
     */
    override fun sqrt(): MegaNumber {
        val result = super.sqrt()
        return MegaInteger(result)
    }

    /**
     * Raise this MegaInteger to the power of another MegaNumber.
     *
     * @param exponent The exponent as a MegaNumber
     * @return The result as a MegaNumber
     */
    override fun pow(exponent: MegaNumber): MegaNumber {
        return pow(MegaInteger(exponent))
    }

    /**
     * Raise this MegaInteger to the power of a MegaFloat.
     *
     * @param exponent The exponent as a MegaFloat
     * @return The result as a MegaNumber
     */
    override fun pow(exponent: MegaFloat): MegaNumber {
        // For float exponents, the result might be a float
        return super.pow(exponent)
    }

    /**
     * Compute the power of this MegaInteger to another MegaInteger.
     *
     * @param exponent The exponent as a MegaInteger
     * @return The result as a MegaInteger
     */
    override fun pow(exponent: MegaInteger): MegaInteger {
        if (exponent.negative) {
            throw IllegalArgumentException("Negative exponents not supported for integers")
        }

        // Convert exponent to integer for simplicity
        val expInt = chunksToInt(exponent.mantissa)

        // Base cases
        if (expInt == 0) return MegaInteger(intArrayOf(1))
        if (expInt == 1) return MegaInteger(this)

        // Use repeated squaring algorithm
        var result = MegaInteger(intArrayOf(1))
        var base = MegaInteger(this)
        var exp = expInt

        while (exp > 0) {
            if (exp and 1 == 1) {
                result = result.mul(base)
            }
            base = base.mul(base)
            exp = exp shr 1
        }

        return result
    }

    companion object {
        /**
         * Create a MegaInteger from various types.
         *
         * @param value Value to convert (String, Int)
         * @return MegaInteger representation
         */
        fun fromValue(value: Any): MegaInteger {
            return when (value) {
                is Int -> {
                    val negative = value < 0
                    val absValue = if (negative) -value else value
                    MegaInteger(
                        mantissa = intArrayOf(absValue),
                        negative = negative
                    )
                }
                is String -> {
                    val num = fromDecimalString(value)
                    MegaInteger(num)
                }
                is MegaNumber -> MegaInteger(value)
                else -> throw IllegalArgumentException("Unsupported value type: ${value::class}")
            }
        }
    }
}
