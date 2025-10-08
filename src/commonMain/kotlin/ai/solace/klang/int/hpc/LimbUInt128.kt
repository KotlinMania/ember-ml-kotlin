package ai.solace.klang.int.hpc

import ai.solace.klang.bitwise.SwAR128

/**
 * Immutable 128-bit unsigned integer backed by eight 16-bit limbs stored in an IntArray.
 * Arithmetic delegates to the arithmetic-only SWAR128 helpers to guarantee deterministic
 * behaviour across Kotlin targets.
 */
class LimbUInt128 private constructor(private val limbs: IntArray) : Comparable<LimbUInt128> {

    init {
        require(limbs.size == SwAR128.LIMB_COUNT) {
            "LimbUInt128 requires exactly ${SwAR128.LIMB_COUNT} limbs"
        }
    }

    // -----------------------------------------------------------------------------------------
    // Basic accessors

    fun toIntArray(): IntArray = limbs.copyOf()

    fun toHexString(): String = SwAR128.toBigEndianHex(SwAR128.UInt128(limbs.copyOf()))

    override fun toString(): String = toHexString()

    // -----------------------------------------------------------------------------------------
    // Arithmetic

    operator fun plus(other: LimbUInt128): LimbUInt128 {
        val out = IntArray(SwAR128.LIMB_COUNT)
        val carry = SwAR128.addInto(limbs, other.limbs, out)
        require(carry == 0) { "UInt128 addition overflow" }
        return LimbUInt128(out)
    }

    operator fun minus(other: LimbUInt128): LimbUInt128 {
        val out = IntArray(SwAR128.LIMB_COUNT)
        val borrow = SwAR128.subInto(limbs, other.limbs, out)
        require(borrow == 0) { "UInt128 subtraction underflow" }
        return LimbUInt128(out)
    }

    fun shiftLeft(bits: Int): LimbUInt128 {
        val result = SwAR128.shiftLeft(SwAR128.UInt128(limbs.copyOf()), bits)
        require(result.spill == 0uL) { "Shift left overflowed beyond 128 bits" }
        return LimbUInt128(result.value.copy().limbs)
    }

    fun shiftRight(bits: Int): LimbUInt128 {
        val result = SwAR128.shiftRight(SwAR128.UInt128(limbs.copyOf()), bits)
        return LimbUInt128(result.value.copy().limbs)
    }

    override fun compareTo(other: LimbUInt128): Int = SwAR128.compareUnsigned(
        SwAR128.UInt128(limbs.copyOf()),
        SwAR128.UInt128(other.limbs.copyOf())
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LimbUInt128) return false
        return limbs.contentEquals(other.limbs)
    }

    override fun hashCode(): Int = limbs.contentHashCode()

    // -----------------------------------------------------------------------------------------
    // Companion constructors

    companion object {
        fun zero(): LimbUInt128 = LimbUInt128(IntArray(SwAR128.LIMB_COUNT) { 0 })

        fun one(): LimbUInt128 {
            val arr = IntArray(SwAR128.LIMB_COUNT) { 0 }
            arr[0] = 1
            return LimbUInt128(arr)
        }

        internal fun fromLimbsUnsafe(limbs: IntArray): LimbUInt128 = LimbUInt128(limbs.copyOf())

        fun fromHexString(hex: String): LimbUInt128 = LimbUInt128(
            SwAR128.fromBigEndianHex(hex).limbs.copyOf()
        )

        fun fromULong(value: ULong): LimbUInt128 = LimbUInt128(
            SwAR128.fromULong(value).limbs.copyOf()
        )

        fun fromDecimalString(decimal: String): LimbUInt128 {
            val trimmed = decimal.trim()
            require(trimmed.isNotEmpty()) { "Decimal string must not be empty" }
            require(trimmed[0] != '-') { "UInt128 cannot represent negative values" }

            var accumulator = IntArray(SwAR128.LIMB_COUNT) { 0 }
            val temp = IntArray(SwAR128.LIMB_COUNT)
            val buffer = IntArray(SwAR128.LIMB_COUNT)

            for (ch in trimmed) {
                if (ch !in '0'..'9') {
                    throw IllegalArgumentException("Invalid decimal digit '$ch'")
                }
                val digit = ch - '0'
                val mulCarry = SwAR128.multiplyBySmall(accumulator, 10, temp)
                require(mulCarry == 0) { "Decimal value exceeds 128-bit range" }
                val addCarry = SwAR128.addSmall(temp, digit, buffer)
                require(addCarry == 0) { "Decimal value exceeds 128-bit range" }
                accumulator = buffer.copyOf()
            }
            return LimbUInt128(accumulator)
        }
    }
}
