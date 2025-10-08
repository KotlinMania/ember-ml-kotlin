package ai.solace.klang.bitwise

/**
 * SwAR128 - arithmetic-only SIMD-Within-A-Register helpers for unsigned 128-bit integers.
 *
 * Internal representation: eight 16-bit limbs (little-endian).  The struct uses math-only
 * routines for add/sub/shift to keep behaviour deterministic across Kotlin targets.
 */
object SwAR128 {
    const val LIMB_BITS = 16
    const val LIMB_COUNT = 8
    private const val LIMB_BASE = 1 shl LIMB_BITS
    private val LIMB_MASK = LIMB_BASE - 1
    private val LIMB_BASE_UL = LIMB_BASE.toULong()

    data class UInt128(val limbs: IntArray) {
        init {
            require(limbs.size == LIMB_COUNT) { "UInt128 requires exactly $LIMB_COUNT limbs" }
            normalizeLimbs(limbs)
        }

        fun copy(): UInt128 = UInt128(limbs.copyOf())
    }

    data class AddResult(val value: UInt128, val carryOut: Int)
    data class SubResult(val value: UInt128, val borrowOut: Int)
    data class ShiftResult(val value: UInt128, val spill: ULong)

    // -----------------------------------------------------------------------------------------
    // Construction

    fun zero(): UInt128 = UInt128(IntArray(LIMB_COUNT) { 0 })

    fun fromULong(value: ULong): UInt128 {
        var remainder = value
        val limbs = IntArray(LIMB_COUNT) { index ->
            val limb = (remainder % LIMB_BASE_UL).toInt() and LIMB_MASK
            remainder /= LIMB_BASE_UL
            limb
        }
        return UInt128(limbs)
    }

    private fun normalizeLimbs(limbs: IntArray) {
        for (i in limbs.indices) {
            var limb = limbs[i]
            limb %= LIMB_BASE
            if (limb < 0) limb += LIMB_BASE
            limbs[i] = limb
        }
    }

    fun fromBigEndianHex(hex: String): UInt128 {
        val clean = hex.removePrefix("0x").padStart(LIMB_COUNT * 4, '0')
        val limbs = IntArray(LIMB_COUNT)
        var limbIndex = 0
        var pos = clean.length
        while (limbIndex < LIMB_COUNT && pos > 0) {
            val start = (pos - 4).coerceAtLeast(0)
            val chunk = clean.substring(start, pos)
            limbs[limbIndex] = chunk.toUInt(16).toInt() and LIMB_MASK
            limbIndex++
            pos = start
        }
        return UInt128(limbs)
    }

    // -----------------------------------------------------------------------------------------
    // Basic arithmetic

    fun add(a: UInt128, b: UInt128): AddResult {
        val out = IntArray(LIMB_COUNT)
        var carry = 0uL
        for (i in 0 until LIMB_COUNT) {
            val sum = a.limbs[i].toULong() + b.limbs[i].toULong() + carry
            out[i] = (sum % LIMB_BASE_UL).toInt()
            carry = sum / LIMB_BASE_UL
        }
        normalizeLimbs(out)
        return AddResult(UInt128(out), carry.toInt())
    }

    fun sub(a: UInt128, b: UInt128): SubResult {
        val out = IntArray(LIMB_COUNT)
        var borrow = 0L
        for (i in 0 until LIMB_COUNT) {
            var diff = a.limbs[i].toLong() - b.limbs[i].toLong() - borrow
            if (diff < 0) {
                diff += LIMB_BASE.toLong()
                borrow = 1
            } else {
                borrow = 0
            }
            out[i] = (diff % LIMB_BASE.toLong()).toInt()
        }
        normalizeLimbs(out)
        return SubResult(UInt128(out), borrow.toInt())
    }

    fun increment(a: UInt128): AddResult = add(a, one())
    fun decrement(a: UInt128): SubResult = sub(a, one())

    fun compareUnsigned(a: UInt128, b: UInt128): Int {
        for (i in LIMB_COUNT - 1 downTo 0) {
            val ai = a.limbs[i] and LIMB_MASK
            val bi = b.limbs[i] and LIMB_MASK
            if (ai != bi) return if (ai > bi) 1 else -1
        }
        return 0
    }

    fun one(): UInt128 {
        val limbs = IntArray(LIMB_COUNT) { 0 }
        limbs[0] = 1
        return UInt128(limbs)
    }

    // -----------------------------------------------------------------------------------------
    // Shifts

    fun shiftLeft(value: UInt128, bits: Int): ShiftResult {
        require(bits >= 0)
        if (bits == 0) return ShiftResult(value.copy(), 0uL)
        if (bits >= LIMB_COUNT * LIMB_BITS) return ShiftResult(zero(), accumulateSpill(value))

        val wordShift = bits / LIMB_BITS
        val bitShift = bits % LIMB_BITS

        val shifted = IntArray(LIMB_COUNT)
        var spill = 0uL

        // Word shift moves limbs upwards
        var i = LIMB_COUNT - 1
        while (i >= wordShift) {
            shifted[i] = value.limbs[i - wordShift]
            i--
        }

        while (i >= 0) {
            spill += (value.limbs[i] and LIMB_MASK).toULong() shl (i * LIMB_BITS)
            shifted[i] = 0
            i--
        }

        if (bitShift == 0) {
            return ShiftResult(UInt128(shifted), spill)
        }

        var carry = 0uL
        for (index in 0 until LIMB_COUNT) {
            val raw = (shifted[index] and LIMB_MASK).toULong() shl bitShift
            val combined = raw + carry
            shifted[index] = (combined % LIMB_BASE_UL).toInt()
            carry = combined / LIMB_BASE_UL
        }

        spill += carry shl (LIMB_COUNT * LIMB_BITS)
        normalizeLimbs(shifted)
        return ShiftResult(UInt128(shifted), spill)
    }

    fun shiftRight(value: UInt128, bits: Int): ShiftResult {
        require(bits >= 0)
        if (bits == 0) return ShiftResult(value.copy(), 0uL)
        if (bits >= LIMB_COUNT * LIMB_BITS) return ShiftResult(zero(), accumulateSpill(value))

        val wordShift = bits / LIMB_BITS
        val bitShift = bits % LIMB_BITS

        val shifted = IntArray(LIMB_COUNT)
        var spill = 0uL

        for (i in 0 until LIMB_COUNT - wordShift) {
            shifted[i] = value.limbs[i + wordShift]
        }
        for (i in LIMB_COUNT - wordShift until LIMB_COUNT) {
            spill += (value.limbs[i] and LIMB_MASK).toULong() shl (i * LIMB_BITS)
        }

        if (bitShift == 0) {
            return ShiftResult(UInt128(shifted), spill)
        }

        var carry = 0uL
        for (index in LIMB_COUNT - 1 downTo 0) {
            val raw = (shifted[index] and LIMB_MASK).toULong() + (carry shl LIMB_BITS)
            shifted[index] = (raw shr bitShift).toInt()
            carry = raw and ((1uL shl bitShift) - 1uL)
        }

        spill += carry shl (LIMB_COUNT * LIMB_BITS - bitShift)
        normalizeLimbs(shifted)
        return ShiftResult(UInt128(shifted), spill)
    }

    // -----------------------------------------------------------------------------------------
    // Utilities

    fun toBigEndianHex(value: UInt128): String {
        return buildString(LIMB_COUNT * 4) {
            for (i in LIMB_COUNT - 1 downTo 0) {
                append((value.limbs[i] and LIMB_MASK).toString(16).padStart(4, '0'))
            }
        }.trimStart('0').ifEmpty { "0" }
    }

    fun accumulateSpill(value: UInt128): ULong {
        var total = 0uL
        var factor = 1uL
        for (i in 0 until LIMB_COUNT) {
            total += (value.limbs[i] and LIMB_MASK).toULong() * factor
            factor *= LIMB_BASE_UL
        }
        return total
    }

    // -----------------------------------------------------------------------------------------
    // General IntArray helpers (for larger limb arrays)

    fun addInto(a: IntArray, b: IntArray, dest: IntArray = IntArray(a.size)): Int {
        require(a.size == b.size) { "Mismatched limb counts" }
        require(dest.size == a.size)
        var carry = 0uL
        for (i in 0 until a.size) {
            val sum = (a[i] and LIMB_MASK).toULong() + (b[i] and LIMB_MASK).toULong() + carry
            dest[i] = (sum % LIMB_BASE_UL).toInt()
            carry = sum / LIMB_BASE_UL
        }
        normalizeLimbs(dest)
        return carry.toInt()
    }

    fun subInto(a: IntArray, b: IntArray, dest: IntArray = IntArray(a.size)): Int {
        require(a.size == b.size)
        require(dest.size == a.size)
        var borrow = 0L
        for (i in 0 until a.size) {
            var diff = (a[i] and LIMB_MASK) - (b[i] and LIMB_MASK) - borrow
            if (diff < 0) {
                diff += LIMB_BASE
                borrow = 1
            } else {
                borrow = 0
            }
            dest[i] = (diff % LIMB_BASE).toInt()
        }
        normalizeLimbs(dest)
        return borrow.toInt()
    }

    fun multiplyBySmall(limbs: IntArray, factor: Int, dest: IntArray = IntArray(limbs.size)): Int {
        require(factor >= 0)
        require(dest.size == limbs.size)
        var carry = 0uL
        for (i in 0 until limbs.size) {
            val product = (limbs[i] and LIMB_MASK).toULong() * factor.toULong() + carry
            dest[i] = (product % LIMB_BASE_UL).toInt()
            carry = product / LIMB_BASE_UL
        }
        normalizeLimbs(dest)
        return carry.toInt()
    }

    fun addSmall(limbs: IntArray, addend: Int, dest: IntArray = IntArray(limbs.size)): Int {
        require(addend in 0 until LIMB_BASE)
        require(dest.size == limbs.size)
        var carry = addend.toULong()
        for (i in 0 until limbs.size) {
            val sum = (limbs[i] and LIMB_MASK).toULong() + carry
            dest[i] = (sum % LIMB_BASE_UL).toInt()
            carry = sum / LIMB_BASE_UL
            if (carry == 0uL) {
                for (j in i + 1 until limbs.size) dest[j] = limbs[j] and LIMB_MASK
                normalizeLimbs(dest)
                return 0
            }
        }
        normalizeLimbs(dest)
        return carry.toInt()
    }
}
