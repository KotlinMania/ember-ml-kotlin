package ai.solace.ember.tensor.bitwise

/**
 * MegaFloat class for float-specific math operations.
 * Inherits from MegaNumber but forces is_float=true.
 */
class MegaFloat : MegaNumber, PowerOperations {
    /**
     * Initialize a MegaFloat object.
     *
     * @param mantissa IntArray of limbs (32-bit chunks)
     * @param exponent MegaNumber representing the binary-exponent
     * @param negative Sign flag
     * @param exponentNegative Whether the exponent is negative
     * @param keepLeadingZeros Whether to keep leading zeros
     */
    constructor(
        mantissa: IntArray = intArrayOf(0),
        exponent: MegaNumber = MegaNumber(intArrayOf(0)),
        negative: Boolean = false,
        exponentNegative: Boolean = false,
        keepLeadingZeros: Boolean = false
    ) : super(
        mantissa = mantissa,
        exponent = if (exponentNegative) {
            MegaNumber(exponent.mantissa.copyOf(), negative = true)
        } else {
            MegaNumber(exponent.mantissa.copyOf(), negative = false)
        },
        negative = negative,
        isFloat = true,
        keepLeadingZeros = keepLeadingZeros
    )

    /**
     * Initialize a MegaFloat from a string.
     *
     * @param value String representation of a float
     */
    constructor(value: String) : super(
        mantissa = intArrayOf(0),
        exponent = MegaNumber(intArrayOf(0)),
        negative = false,
        isFloat = true,
        keepLeadingZeros = false
    ) {
        val temp = fromDecimalString(value)
        this.mantissa = temp.mantissa.copyOf()
        this.exponent = temp.exponent
        this.negative = temp.negative
        this.isFloat = true
    }

    /**
     * Initialize a MegaFloat from another MegaNumber.
     *
     * @param other Another MegaNumber object
     */
    constructor(other: MegaNumber) : super(
        mantissa = other.mantissa.copyOf(),
        exponent = other.exponent,
        negative = other.negative,
        isFloat = true,
        keepLeadingZeros = other.keepLeadingZeros
    )

    /**
     * String representation.
     *
     * @return String representation
     */
    override fun toString(): String {
        return "<MegaFloat ${toDecimalString()}>"
    }

    /**
     * Add two MegaFloats.
     *
     * @param other Another MegaFloat
     * @return Sum as a MegaFloat
     */
    fun add(other: MegaFloat): MegaFloat {
        val result = super.addFloat(other)
        return MegaFloat(result)
    }

    /**
     * Subtract two MegaFloats.
     *
     * @param other Another MegaFloat
     * @return Difference as a MegaFloat
     */
    fun sub(other: MegaFloat): MegaFloat {
        val result = super.sub(other)
        return MegaFloat(result)
    }

    /**
     * Multiply two MegaFloats.
     *
     * @param other Another MegaFloat
     * @return Product as a MegaFloat
     */
    fun mul(other: MegaFloat): MegaFloat {
        val result = super.mulFloat(other)
        return MegaFloat(result)
    }

    /**
     * Divide two MegaFloats.
     *
     * @param other Another MegaFloat
     * @return Quotient as a MegaFloat
     */
    fun div(other: MegaFloat): MegaFloat {
        val result = super.divide(other)
        return MegaFloat(result)
    }

    /**
     * Compute the square root of this MegaFloat.
     *
     * @return The square root as a MegaFloat
     */
    override fun sqrt(): MegaNumber {
        val result = super.sqrt()
        return MegaFloat(result)
    }

    /**
     * Raise this MegaFloat to the power of another MegaNumber.
     *
     * @param exponent The exponent as a MegaNumber
     * @return The result as a MegaNumber
     */
    override fun pow(exponent: MegaNumber): MegaNumber {
        return pow(MegaFloat(exponent))
    }

    /**
     * Raise this MegaFloat to the power of a MegaInteger.
     *
     * @param exponent The exponent as a MegaInteger
     * @return The result as a MegaFloat
     */
    override fun pow(exponent: MegaInteger): MegaFloat {
        return pow(MegaFloat(exponent))
    }

    /**
     * Raise this MegaFloat to the power of another MegaFloat.
     *
     * @param exponent The exponent as a MegaFloat
     * @return The result as a MegaFloat
     */
    override fun pow(exponent: MegaFloat): MegaFloat {
        if (exponent.negative) {
            throw IllegalArgumentException("Negative exponents not supported yet")
        }

        // Convert exponent to integer for simplicity
        val expInt = chunksToInt(exponent.mantissa)

        // Base cases
        if (expInt == 0) return MegaFloat(intArrayOf(1))
        if (expInt == 1) return MegaFloat(this)

        // Use repeated squaring algorithm
        var result = MegaFloat(intArrayOf(1))
        var base = MegaFloat(this)
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
         * Create a MegaFloat from various types.
         *
         * @param value Value to convert (String, Double, Int)
         * @return MegaFloat representation
         */
        fun fromValue(value: Any): MegaFloat {
            return when (value) {
                is Double -> MegaFloat(value.toString())
                is Int -> MegaFloat(value.toString())
                is String -> MegaFloat(value)
                is MegaNumber -> MegaFloat(value)
                else -> throw IllegalArgumentException("Unsupported value type: ${value::class}")
            }
        }
    }
}
