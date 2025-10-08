package ai.solace.klang.scalar

import ai.solace.klang.bitwise.ArithmeticBitwiseOps
import ai.solace.klang.bitwise.BitShiftConfig
import ai.solace.klang.bitwise.BitShiftMode
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/**
 * EmberScalar – direct Kotlin port of the MegaNumber limb engine.
 *
 * Limbs are stored little-endian with 16-bit chunks packed inside IntArray entries. All arithmetic
 * stays in the limb domain so behaviour is deterministic across Kotlin targets and free from
 * floating, 64-bit, or platform fallbacks.
 */
class EmberScalar constructor(
    mantissa: IntArray = intArrayOf(0),
    exponent: IntArray = intArrayOf(0),
    negative: Boolean = false,
    isFloat: Boolean = false,
    keepLeadingZeros: Boolean = false,
    exponentNegative: Boolean = false
) {
    private var mantissa: IntArray = mantissa.copyOf()
    private var exponent: IntArray = exponent.copyOf()
    private var negative: Boolean = negative
    private var isFloat: Boolean = isFloat
    private var keepLeadingZeros: Boolean = keepLeadingZeros
    private var exponentNegative: Boolean = exponentNegative

    constructor(value: String) : this() {
        assignFrom(fromDecimalString(value))
    }

    constructor(value: EmberScalar) : this(
        mantissa = value.mantissa,
        exponent = value.exponent,
        negative = value.negative,
        isFloat = value.isFloat,
        keepLeadingZeros = value.keepLeadingZeros,
        exponentNegative = value.exponentNegative
    )

    constructor(
        value: IntArray,
        negative: Boolean = false,
        exponent: IntArray = intArrayOf(0),
        isFloat: Boolean = false,
        exponentNegative: Boolean = false,
        keepLeadingZeros: Boolean = false
    ) : this(
        mantissa = value,
        exponent = exponent,
        negative = negative,
        isFloat = isFloat,
        keepLeadingZeros = keepLeadingZeros,
        exponentNegative = exponentNegative
    )

    init {
        normalize()
    }

    private fun assignFrom(other: EmberScalar) {
        mantissa = other.mantissa.copyOf()
        exponent = other.exponent.copyOf()
        negative = other.negative
        isFloat = other.isFloat
        exponentNegative = other.exponentNegative
        keepLeadingZeros = other.keepLeadingZeros
        normalize()
    }

    fun copy(): EmberScalar = EmberScalar(
        mantissa = mantissa,
        exponent = exponent,
        negative = negative,
        isFloat = isFloat,
        keepLeadingZeros = keepLeadingZeros,
        exponentNegative = exponentNegative
    )

    fun mantissaLimbs(): IntArray = mantissa.copyOf()
    fun exponentLimbs(): IntArray = exponent.copyOf()
    fun isNegative(): Boolean = negative
    fun isFloatValue(): Boolean = isFloat
    fun exponentIsNegative(): Boolean = exponentNegative
    fun isZero(): Boolean = mantissa.size == 1 && mantissa[0] == 0

    override fun toString(): String = toDecimalString()

    fun toDecimalString(maxDigits: Int? = null): String {
        if (isZero()) return "0"
        val signStr = if (negative) "-" else ""
        if (!isFloat) {
            var current = mantissa.copyOf()
            val builder = StringBuilder()
            while (!(current.size == 1 && current[0] == 0)) {
                val (quot, rem) = divmodSmall(current, 10)
                builder.append(rem)
                current = trimChunklist(quot)
            }
            val digits = builder.reverse().toString()
            val truncated = when {
                maxDigits != null && digits.length > maxDigits -> digits.takeLast(maxDigits)
                else -> digits
            }
            return signStr + truncated
        }

        val expInt = expAsInt(this)
        return if (exponentNegative) {
            val (intPart, remainder) = divBy2Exp(mantissa, abs(expInt))
            val intStr = chunkToDecimalString(intPart, maxDigits)
            if (chunkListAllZero(remainder)) {
                signStr + intStr
            } else {
                val divisor = shiftLeftBits(intArrayOf(1), abs(expInt))
                val ten = intArrayOf(10)
                val steps = maxDigits ?: DEFAULT_FRACTION_PRECISION
                val fractional = StringBuilder()
                var current = remainder
                repeat(steps) {
                    current = mulChunklists(current, ten)
                    val (digitChunks, newRemainder) = divChunk(current, divisor)
                    val digit = chunklistToInt(digitChunks)
                    fractional.append(digit)
                    current = trimChunklist(newRemainder)
                    if (chunkListAllZero(current)) return@repeat
                }
                val fracStr = fractional.toString().trimEnd('0')
                if (fracStr.isEmpty()) {
                    signStr + intStr
                } else {
                    signStr + intStr + "." + fracStr
                }
            }
        } else {
            val shifted = mulBy2Exp(mantissa, abs(expInt))
            signStr + chunkToDecimalString(shifted, maxDigits)
        }
    }

    fun add(other: EmberScalar): EmberScalar {
        if (isFloat || other.isFloat) {
            val selfFloat = copy().apply { isFloat = true }
            val otherFloat = other.copy().apply { isFloat = true }
            return selfFloat.addFloat(otherFloat)
        }
        val result = EmberScalar()
        when {
            negative == other.negative -> {
                result.mantissa = addChunklists(mantissa, other.mantissa)
                result.negative = negative
            }
            compareAbsChunks(mantissa, other.mantissa) >= 0 -> {
                result.mantissa = subChunklists(mantissa, other.mantissa)
                result.negative = negative
            }
            else -> {
                result.mantissa = subChunklists(other.mantissa, mantissa)
                result.negative = other.negative
            }
        }
        result.normalize()
        return result
    }

    fun sub(other: EmberScalar): EmberScalar {
        val neg = other.copy()
        neg.negative = !other.negative
        if (neg.isZero()) neg.negative = false
        return add(neg)
    }

    fun mul(other: EmberScalar): EmberScalar {
        if (isZero() || other.isZero()) return EmberScalar()
        val sign = negative.xor(other.negative)
        val outMantissa = mulChunklists(mantissa, other.mantissa)
        val result = if (!isFloat && !other.isFloat) {
            EmberScalar(
                mantissa = outMantissa,
                exponent = intArrayOf(0),
                negative = sign
            )
        } else {
            val expSum = expAsInt(this) + expAsInt(other)
            val expNeg = expSum < 0
            val expChunks = intToChunklist(abs(expSum))
            EmberScalar(
                mantissa = outMantissa,
                exponent = expChunks,
                negative = sign,
                isFloat = true,
                exponentNegative = expNeg
            )
        }
        result.normalize()
        return result
    }

    fun div(other: EmberScalar): EmberScalar {
        if (other.isZero()) throw ArithmeticException("division by zero")
        if (compareAbs(other) == 0 && isFloat == other.isFloat && compareAbsChunks(exponent, other.exponent) == 0 && exponentNegative == other.exponentNegative) {
            val sign = negative.xor(other.negative)
            return EmberScalar(if (sign) "-1" else "1")
        }
        if (isZero()) return EmberScalar()
        val sign = negative.xor(other.negative)
        if (!isFloat && !other.isFloat) {
            val cmp = compareAbsChunks(mantissa, other.mantissa)
            if (cmp < 0) return EmberScalar()
            val (quotient, _) = divChunk(mantissa, other.mantissa)
            val result = EmberScalar(
                mantissa = quotient,
                exponent = intArrayOf(0),
                negative = sign
            )
            result.normalize()
            return result
        }
        val expDiff = expAsInt(this) - expAsInt(other)
        val precisionIncrease = max(0, other.mantissa.size * GLOBAL_CHUNK_SIZE)
        val adjustedMantissa = mulBy2Exp(mantissa, precisionIncrease)
        val (quotient, _) = divChunk(adjustedMantissa, other.mantissa)
        val newExp = expDiff + precisionIncrease
        val expNeg = newExp < 0
        val expChunks = intToChunklist(abs(newExp))
        val result = EmberScalar(
            mantissa = quotient,
            exponent = expChunks,
            negative = sign,
            isFloat = true,
            exponentNegative = expNeg
        )
        result.normalize()
        return result
    }

    fun compareAbs(other: EmberScalar): Int {
        return if (isFloat || other.isFloat) {
            val expA = expAsInt(this)
            val expB = expAsInt(other)
            when {
                expA > expB -> 1
                expA < expB -> -1
                else -> compareAbsChunks(mantissa, other.mantissa)
            }
        } else {
            compareAbsChunks(mantissa, other.mantissa)
        }
    }

    private fun addFloat(other: EmberScalar): EmberScalar {
        val expA = expAsInt(this)
        val expB = expAsInt(other)
        val mantA: IntArray
        val mantB: IntArray
        val finalExp: Int
        when {
            expA == expB -> {
                mantA = mantissa.copyOf()
                mantB = other.mantissa.copyOf()
                finalExp = expA
            }
            expA > expB -> {
                mantA = mantissa.copyOf()
                mantB = shiftRightBits(other.mantissa, expA - expB)
                finalExp = expA
            }
            else -> {
                mantA = shiftRightBits(mantissa, expB - expA)
                mantB = other.mantissa.copyOf()
                finalExp = expB
            }
        }
        val outMantissa: IntArray
        val outNegative: Boolean
        if (negative == other.negative) {
            outMantissa = addChunklists(mantA, mantB)
            outNegative = negative
        } else {
            val cmp = compareAbsChunks(mantA, mantB)
            if (cmp == 0) {
                return EmberScalar(isFloat = true)
            }
            if (cmp > 0) {
                outMantissa = subChunklists(mantA, mantB)
                outNegative = negative
            } else {
                outMantissa = subChunklists(mantB, mantA)
                outNegative = other.negative
            }
        }
        val expNeg = finalExp < 0
        val expChunks = intToChunklist(abs(finalExp))
        val result = EmberScalar(
            mantissa = outMantissa,
            exponent = expChunks,
            negative = outNegative,
            isFloat = true,
            exponentNegative = expNeg
        )
        result.normalize()
        return result
    }

    private fun normalize() {
        if (!keepLeadingZeros) {
            mantissa = trimChunklist(mantissa)
            if (isFloat) {
                exponent = trimChunklist(exponent)
            }
            if (mantissa.size == 1 && mantissa[0] == 0) {
                negative = false
                if (isFloat) {
                    exponent = intArrayOf(0)
                    exponentNegative = false
                }
            }
        } else if (chunkListAllZero(mantissa)) {
            negative = false
            exponentNegative = false
        }
    }

    private fun expAsInt(target: EmberScalar): Int {
        val magnitude = chunklistToInt(target.exponent)
        return if (target.exponentNegative) -magnitude else magnitude
    }

    private fun chunkToDecimalString(chunks: IntArray, maxDigits: Int?): String {
        val tmp = EmberScalar(
            mantissa = chunks.copyOf(),
            exponent = intArrayOf(0),
            negative = false,
            isFloat = false
        )
        return tmp.toDecimalString(maxDigits)
    }

    private fun divBy2Exp(limbs: IntArray, bits: Int): Pair<IntArray, IntArray> {
        if (bits <= 0) return trimChunklist(limbs.copyOf()) to intArrayOf(0)
        val quotient = shiftRightBits(limbs, bits)
        val product = mulBy2Exp(quotient, bits)
        val remainder = if (compareAbsChunks(limbs, product) >= 0) {
            subChunklists(limbs, product)
        } else {
            intArrayOf(0)
        }
        return trimChunklist(quotient) to trimChunklist(remainder)
    }

    private fun mulBy2Exp(limbs: IntArray, bits: Int): IntArray {
        if (bits <= 0) return trimChunklist(limbs.copyOf())
        return shiftLeftBits(limbs, bits)
    }

    private fun shiftRightBits(src: IntArray, bits: Int): IntArray {
        if (bits <= 0) return trimChunklist(src.copyOf())
        val trimmed = trimChunklist(src)
        val wordShift = bits / GLOBAL_CHUNK_SIZE
        val bitShift = bits % GLOBAL_CHUNK_SIZE
        if (wordShift >= trimmed.size) return intArrayOf(0)
        val outLen = trimmed.size - wordShift
        val out = IntArray(outLen)
        for (i in out.indices) {
            out[i] = BIT_OPS_16.normalize(trimmed[i + wordShift].toLong()).toInt()
        }
        if (bitShift != 0 && out.isNotEmpty()) {
            val paddedLen = LimbShift16Workspace.alignLength(out.size)
            val workspace = IntArray(paddedLen)
            out.copyInto(
                destination = workspace,
                destinationOffset = 0,
                startIndex = 0,
                endIndex = out.size,
            )
            LimbShift16Workspace.shiftRight(workspace, bitShift)
            workspace.copyInto(
                destination = out,
                destinationOffset = 0,
                startIndex = 0,
                endIndex = out.size,
            )
        }
        return trimChunklist(out)
    }

    private fun shiftLeftBits(src: IntArray, bits: Int): IntArray {
        if (bits <= 0) return trimChunklist(src.copyOf())
        val trimmed = trimChunklist(src)
        val wordShift = bits / GLOBAL_CHUNK_SIZE
        val bitShift = bits % GLOBAL_CHUNK_SIZE
        val out = IntArray(trimmed.size + wordShift + 1)
        for (i in trimmed.indices) {
            out[i + wordShift] = BIT_OPS_16.normalize(trimmed[i].toLong()).toInt()
        }
        if (bitShift != 0) {
            val paddedLen = LimbShift16Workspace.alignLength(out.size)
            val workspace = IntArray(paddedLen)
            out.copyInto(
                destination = workspace,
                destinationOffset = 0,
                startIndex = 0,
                endIndex = out.size,
            )
            val carry = LimbShift16Workspace.shiftLeft(workspace, bitShift)
            workspace.copyInto(
                destination = out,
                destinationOffset = 0,
                startIndex = 0,
                endIndex = out.size,
            )
            if (carry != 0) {
                out[out.size - 1] = BIT_OPS_16.normalize(carry.toLong()).toInt()
            }
        }
        return trimChunklist(out)
    }

    private fun divChunk(a: IntArray, b: IntArray): Pair<IntArray, IntArray> {
        val dividend = trimChunklist(a)
        val divisor = trimChunklist(b)
        if (chunkListAllZero(divisor)) throw ArithmeticException("division by zero")
        val cmp = compareAbsChunks(dividend, divisor)
        if (cmp < 0) return intArrayOf(0) to dividend.copyOf()
        if (divisor.size == 1) {
            val single = BIT_OPS_16.normalize(divisor[0].toLong())
            val result = IntArray(dividend.size)
            var remainder = 0L
            for (i in dividend.size - 1 downTo 0) {
                val cur = remainder * BASE + BIT_OPS_16.normalize(dividend[i].toLong())
                val q = cur / single
                result[i] = BIT_OPS_16.normalize(q).toInt()
                remainder = cur % single
            }
            return trimChunklist(result) to intToChunklist(remainder)
        }
        val dividendBits = bitLength(dividend)
        val divisorBits = bitLength(divisor)
        val shift = max(0, dividendBits - divisorBits)
        var remainder = dividend.copyOf()
        var shiftedDivisor = shiftLeftBits(divisor, shift)
        val quotient = IntArray(shift / GLOBAL_CHUNK_SIZE + 1)
        for (bit in shift downTo 0) {
            if (compareAbsChunks(remainder, shiftedDivisor) >= 0) {
                remainder = subChunklists(remainder, shiftedDivisor)
                setBit(quotient, bit)
            }
            shiftedDivisor = shiftRightBits(shiftedDivisor, 1)
        }
        return trimChunklist(quotient) to trimChunklist(remainder)
    }

    private fun bitLength(limbs: IntArray): Int {
        val trimmed = trimChunklist(limbs)
        if (trimmed.size == 1 && trimmed[0] == 0) return 0
        val lastIndex = trimmed.size - 1
        val highest = BIT_OPS_16.normalize(trimmed[lastIndex].toLong()).toInt()
        val leading = highest.countLeadingZeroBits() - (Int.SIZE_BITS - GLOBAL_CHUNK_SIZE)
        val highBits = GLOBAL_CHUNK_SIZE - leading
        return lastIndex * GLOBAL_CHUNK_SIZE + highBits
    }

    private fun setBit(limbs: IntArray, bitIndex: Int) {
        if (bitIndex < 0) return
        val word = bitIndex / GLOBAL_CHUNK_SIZE
        val bit = bitIndex % GLOBAL_CHUNK_SIZE
        if (word >= limbs.size) throw IndexOutOfBoundsException("Bit index $bitIndex outside quotient size ${limbs.size}")
        val maskVal = BIT_OPS_32.leftShift(1L, bit)
        val updated = BIT_OPS_32.or(limbs[word].toLong(), maskVal)
        limbs[word] = BIT_OPS_16.normalize(updated).toInt()
    }

    private fun mulChunklists(a: IntArray, b: IntArray): IntArray {
        val la = a.size
        val lb = b.size
        val out = IntArray(la + lb)
        for (i in 0 until la) {
            var carry = 0L
            val ai = BIT_OPS_16.normalize(a[i].toLong())
            for (j in 0 until lb) {
                val idx = i + j
                val product = out[idx].toLong() + ai * BIT_OPS_16.normalize(b[j].toLong()) + carry
                val lower = BIT_OPS_16.extractBits(product, GLOBAL_CHUNK_SIZE)
                out[idx] = lower.toInt()
                carry = BIT_OPS_32.rightShift(product, GLOBAL_CHUNK_SIZE)
            }
            var pos = i + lb
            while (carry != 0L) {
                val sum = out[pos].toLong() + carry
                val lower = BIT_OPS_16.extractBits(sum, GLOBAL_CHUNK_SIZE)
                out[pos] = lower.toInt()
                carry = BIT_OPS_32.rightShift(sum, GLOBAL_CHUNK_SIZE)
                pos++
            }
        }
        return trimChunklist(out)
    }

    private fun addChunklists(a: IntArray, b: IntArray): IntArray {
        val maxLen = max(a.size, b.size)
        val out = IntArray(maxLen + 1)
        var carry = 0
        for (i in 0 until maxLen) {
            val av = if (i < a.size) BIT_OPS_16.normalize(a[i].toLong()) else 0
            val bv = if (i < b.size) BIT_OPS_16.normalize(b[i].toLong()) else 0
            val sum = av + bv + carry
            val lower = BIT_OPS_16.extractBits(sum, GLOBAL_CHUNK_SIZE)
            out[i] = lower.toInt()
            carry = BIT_OPS_32.rightShift(sum, GLOBAL_CHUNK_SIZE).toInt()
        }
        if (carry != 0) {
            out[maxLen] = BIT_OPS_16.normalize(carry.toLong()).toInt()
        }
        return trimChunklist(out)
    }

    private fun subChunklists(a: IntArray, b: IntArray): IntArray {
        val out = IntArray(a.size)
        var borrow = 0
        for (i in 0 until a.size) {
            val av = BIT_OPS_16.normalize(a[i].toLong()).toInt()
            val bv = if (i < b.size) BIT_OPS_16.normalize(b[i].toLong()).toInt() else 0
            var diff = av - bv - borrow
            if (diff < 0) {
                diff += BASE
                borrow = 1
            } else {
                borrow = 0
            }
            out[i] = BIT_OPS_16.normalize(diff.toLong()).toInt()
        }
        return trimChunklist(out)
    }

    private fun divmodSmall(src: IntArray, divisor: Int): Pair<IntArray, Int> {
        require(divisor in 1..MASK) { "Divisor must be between 1 and $MASK" }
        val out = IntArray(src.size)
        var remainder = 0L
        for (i in src.size - 1 downTo 0) {
            val cur = remainder * BASE + BIT_OPS_16.normalize(src[i].toLong())
            val q = cur / divisor
            remainder = cur % divisor
            out[i] = BIT_OPS_16.normalize(q).toInt()
        }
        return trimChunklist(out) to remainder.toInt()
    }

    private object LimbShift16Workspace {
        private const val LIMB_BITS = 16
        private const val GROUP_SIZE = 8
        private const val BASE = 1 shl LIMB_BITS
        private val BASE_LONG = BASE.toLong()
        private val POW2 = IntArray(LIMB_BITS + 1).also { table ->
            var value = 1
            for (i in 0..LIMB_BITS) {
                table[i] = value
                if (i < LIMB_BITS) value *= 2
            }
        }
        private val LOW_MASK = IntArray(LIMB_BITS + 1).also { table ->
            table[0] = 0
            for (i in 1..LIMB_BITS) {
                table[i] = POW2[i] - 1
            }
        }
        private val nativePreferred by lazy { BitShiftConfig.resolveMode(LIMB_BITS) == BitShiftMode.NATIVE }

        fun alignLength(length: Int): Int {
            if (length <= 0) return 0
            val groups = (length + GROUP_SIZE - 1) / GROUP_SIZE
            return groups * GROUP_SIZE
        }

        fun shiftLeft(data: IntArray, bitShift: Int, carryIn: Int = 0): Int {
            require(bitShift in 0 until LIMB_BITS) { "bitShift must be in 0 until $LIMB_BITS" }
            if (bitShift == 0 || data.isEmpty()) return normalize(carryIn)
            if (nativePreferred) {
                return shiftLeftNative(data, bitShift, carryIn)
            }
            require(data.size % GROUP_SIZE == 0) { "Data length must be a multiple of $GROUP_SIZE" }

            val pow2 = POW2[bitShift]
            val carryMask = LOW_MASK[bitShift]
            var carry = if (carryMask == 0) 0 else normalize(carryIn) % (carryMask + 1)
            var offset = 0
            while (offset < data.size) {
                carry = shiftLeftWord(data, offset, pow2, carryMask, carry)
                offset += GROUP_SIZE
            }
            return normalize(carry)
        }

        fun shiftRight(data: IntArray, bitShift: Int): Int {
            require(bitShift in 0 until LIMB_BITS) { "bitShift must be in 0 until $LIMB_BITS" }
            if (bitShift == 0 || data.isEmpty()) return 0
            if (nativePreferred) {
                return shiftRightNative(data, bitShift)
            }
            require(data.size % GROUP_SIZE == 0) { "Data length must be a multiple of $GROUP_SIZE" }

            val divisor = POW2[bitShift]
            val merge = POW2[LIMB_BITS - bitShift]
            var carry = 0
            var offset = data.size - GROUP_SIZE
            while (offset >= 0) {
                carry = shiftRightWord(data, offset, divisor, merge, carry)
                offset -= GROUP_SIZE
            }
            return normalize(carry)
        }

        private fun shiftLeftWord(
            data: IntArray,
            start: Int,
            pow2: Int,
            carryMask: Int,
            initialCarry: Int,
        ): Int {
            var carry = if (carryMask == 0) 0 else initialCarry % (carryMask + 1)
            var idx = 0
            while (idx < GROUP_SIZE) {
                val pos = start + idx
                if (pos >= data.size) break
                val value = normalize(data[pos])
                val combined = value.toLong() * pow2.toLong() + carry.toLong()
                data[pos] = normalize(combined % BASE_LONG)
                carry = (combined / BASE_LONG).toInt()
                idx++
            }
            return if (carryMask == 0) 0 else carry % (carryMask + 1)
        }

        private fun shiftRightWord(
            data: IntArray,
            start: Int,
            divisor: Int,
            merge: Int,
            initialCarry: Int,
        ): Int {
            var carry = initialCarry % divisor
            var idx = GROUP_SIZE - 1
            while (idx >= 0) {
                val pos = start + idx
                if (pos >= data.size) {
                    idx--
                    continue
                }
                val value = normalize(data[pos])
                val shifted = value / divisor
                val neighbor = (carry * merge) % BASE
                val combined = shifted + neighbor
                data[pos] = normalize(combined)
                carry = value % divisor
                idx--
            }
            return carry % divisor
        }

        private fun normalize(value: Int): Int {
            var remainder = value % BASE
            if (remainder < 0) remainder += BASE
            return remainder
        }

        private fun normalize(value: Long): Int {
            var remainder = value % BASE_LONG
            if (remainder < 0) remainder += BASE_LONG
            return remainder.toInt()
        }

        private fun shiftLeftNative(data: IntArray, bitShift: Int, carryIn: Int): Int {
            val carryMask = LOW_MASK[bitShift]
            var carry = if (carryMask == 0) 0 else normalize(carryIn) % (carryMask + 1)
            for (index in 0 until data.size) {
                val current = normalize(data[index])
                val combined = (current shl bitShift) + carry
                data[index] = normalize(combined)
                carry = combined ushr LIMB_BITS
            }
            return normalize(carry)
        }

        private fun shiftRightNative(data: IntArray, bitShift: Int): Int {
            val carryMask = LOW_MASK[bitShift]
            val mergeShift = LIMB_BITS - bitShift
            var incoming = 0
            var idx = data.lastIndex
            while (idx >= 0) {
                val current = normalize(data[idx])
                val upper =
                    if (bitShift == 0) {
                        0
                    } else {
                        (incoming and carryMask) shl mergeShift
                    }
                val shifted = (current ushr bitShift) or upper
                data[idx] = normalize(shifted)
                incoming = current and carryMask
                idx--
            }
            return normalize(incoming)
        }
    }

    companion object {
        const val GLOBAL_CHUNK_SIZE = 16
        private val BIT_OPS_16 = ArithmeticBitwiseOps.BITS_16
        private val BIT_OPS_32 = ArithmeticBitwiseOps.BITS_32
        private val BASE = BIT_OPS_32.leftShift(1L, GLOBAL_CHUNK_SIZE).toInt()
        private val MASK = BIT_OPS_16.createMask(GLOBAL_CHUNK_SIZE).toInt()
        private const val DEFAULT_FRACTION_PRECISION = 50
        private const val LOG2_OF_10 = 3.3219280948873626

        fun zero(): EmberScalar = EmberScalar()
        fun one(): EmberScalar = EmberScalar(mantissa = intArrayOf(1))

        fun fromDecimalString(value: String): EmberScalar {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return EmberScalar()
            var negative = false
            var body = trimmed
            if (body.startsWith('-')) {
                negative = true
                body = body.substring(1)
            } else if (body.startsWith('+')) {
                body = body.substring(1)
            }
            body = body.trim()
            if (body.isEmpty()) return EmberScalar()

            var fractionLength = 0
            val pointIndex = body.indexOf('.')
            if (pointIndex >= 0) {
                fractionLength = body.length - pointIndex - 1
                body = body.removeRange(pointIndex, pointIndex + 1)
            }

            val mantissaChunks = parseDecimalDigits(body)
            var exponentChunks = intArrayOf(0)
            var exponentNegative = false
            var isFloat = false

            if (fractionLength > 0) {
                isFloat = true
                exponentNegative = true
                val bitsNeeded = ceil(fractionLength * LOG2_OF_10).toInt()
                exponentChunks = intToChunklist(bitsNeeded)
            }

            val result = EmberScalar(
                mantissa = mantissaChunks,
                exponent = exponentChunks,
                negative = negative && !chunkListAllZero(mantissaChunks),
                isFloat = isFloat,
                exponentNegative = exponentNegative
            )
            result.normalize()
            return result
        }

        fun fromBinaryString(value: String): EmberScalar {
            var body = value.trim()
            if (body.startsWith("0b") || body.startsWith("0B")) body = body.substring(2)
            if (body.isEmpty()) return EmberScalar()
            val limbs = IntArray((body.length + GLOBAL_CHUNK_SIZE - 1) / GLOBAL_CHUNK_SIZE)
            var limbIndex = 0
            var bitOffset = 0
            for (ch in body.reversed()) {
                require(ch == '0' || ch == '1') { "Invalid binary digit '$ch'" }
                if (ch == '1') {
                    val maskVal = BIT_OPS_32.leftShift(1L, bitOffset)
                    val updated = BIT_OPS_32.or(limbs[limbIndex].toLong(), maskVal)
                    limbs[limbIndex] = BIT_OPS_16.normalize(updated).toInt()
                }
                bitOffset++
                if (bitOffset == GLOBAL_CHUNK_SIZE) {
                    bitOffset = 0
                    limbIndex++
                }
            }
            return EmberScalar(mantissa = trimChunklist(limbs))
        }

        private fun parseDecimalDigits(digits: String): IntArray {
            if (digits.isEmpty()) return intArrayOf(0)
            var current = intArrayOf(0)
            val temp = IntArray(digits.length + 4)
            val buffer = IntArray(digits.length + 4)
            for (ch in digits) {
                require(ch in '0'..'9') { "Invalid decimal digit '$ch'" }
                multiplyChunklistBySmall(current, 10, temp)
                addSmall(temp, ch - '0', buffer)
                current = trimChunklist(buffer.copyOf())
            }
            return current
        }

        private fun multiplyChunklistBySmall(src: IntArray, factor: Int, dest: IntArray) {
            dest.fill(0)
            var carry = 0L
            var i = 0
            while (i < src.size) {
                val product = BIT_OPS_16.normalize(src[i].toLong()) * factor + carry
                dest[i] = BIT_OPS_16.extractBits(product, GLOBAL_CHUNK_SIZE).toInt()
                carry = BIT_OPS_32.rightShift(product, GLOBAL_CHUNK_SIZE)
                i++
            }
            if (carry != 0L && i < dest.size) dest[i] = BIT_OPS_16.normalize(carry).toInt()
        }

        private fun addSmall(src: IntArray, addend: Int, dest: IntArray) {
            dest.fill(0)
            var carry = addend.toLong()
            var i = 0
            while (i < src.size) {
                val sum = BIT_OPS_16.normalize(src[i].toLong()) + carry
                dest[i] = BIT_OPS_16.extractBits(sum, GLOBAL_CHUNK_SIZE).toInt()
                carry = BIT_OPS_32.rightShift(sum, GLOBAL_CHUNK_SIZE)
                i++
                if (carry == 0L) {
                    while (i < src.size) {
                        dest[i] = src[i]
                        i++
                    }
                    return
                }
            }
            if (i < dest.size) dest[i] = BIT_OPS_16.normalize(carry).toInt()
        }

        private fun intToChunklist(value: Int): IntArray = intToChunklist(value.toLong())

        private fun intToChunklist(value: Long): IntArray {
            if (value == 0L) return intArrayOf(0)
            var tmp = value
            val out = ArrayList<Int>()
            val baseLong = BASE.toLong()
            while (tmp != 0L) {
                val limb = (tmp % baseLong).toInt()
                out.add(if (limb >= 0) limb else (limb + BASE))
                tmp /= baseLong
            }
            return out.toIntArray()
        }

        private fun chunklistToInt(limbs: IntArray): Int {
            var result = 0L
            val baseLong = BASE.toLong()
            for (i in limbs.size - 1 downTo 0) {
                val limb = BIT_OPS_16.normalize(limbs[i].toLong())
                result = result * baseLong + limb
                if (result > Int.MAX_VALUE) throw IllegalArgumentException("Chunk list too large to fit in Int")
            }
            return result.toInt()
        }

        private fun trimChunklist(limbs: IntArray): IntArray {
            var last = limbs.size - 1
            while (last > 0 && limbs[last] == 0) last--
            return limbs.copyOf(last + 1)
        }

        private fun chunkListAllZero(limbs: IntArray): Boolean = limbs.all { it == 0 }

        private fun compareAbsChunks(a: IntArray, b: IntArray): Int {
            val ta = trimChunklist(a)
            val tb = trimChunklist(b)
            if (ta.size != tb.size) return ta.size.compareTo(tb.size)
            for (i in ta.size - 1 downTo 0) {
                val av = BIT_OPS_16.normalize(ta[i].toLong()).toInt()
                val bv = BIT_OPS_16.normalize(tb[i].toLong()).toInt()
                if (av != bv) return av.compareTo(bv)
            }
            return 0
        }
    }
}
