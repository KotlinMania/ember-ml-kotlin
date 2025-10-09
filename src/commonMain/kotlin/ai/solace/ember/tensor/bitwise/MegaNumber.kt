/**
 * Kotlin Native implementation of MegaNumber, the foundation for arbitrary precision arithmetic.
 *
 * This class provides a chunk-based big integer (or float) with arbitrary precision arithmetic,
 * using Int arrays with 32-bit values.
 */
package ai.solace.ember.tensor.bitwise

/**
 * Constants used by MegaNumber implementation
 */
object MegaNumberConstants {
    const val GLOBAL_CHUNK_SIZE: Int = 32 // Optimum size for O(n) complexity drop in certain operations
    const val MASK: Long = 0xFFFFFFFFL // Use Long for proper unsigned handling

    // Thresholds for picking naive vs. Karatsuba vs. Toom-3
    const val MUL_THRESHOLD_KARATSUBA = 64 // Increased to use standard multiplication for smaller numbers
    const val MUL_THRESHOLD_TOOM = 128 // Increased to use Karatsuba for medium-sized numbers
    // Maximum precision in bits (limited to avoid excessive memory usage)
    var maxPrecisionBits: Int = 1024   // −1 means “unlimited”
}

/**
 * A chunk-based big integer (or float) with arbitrary precision arithmetic,
 * using IntArray with 32-bit values.
 *
 * @property mantissa     IntArray of limbs (32-bit chunks)
 * @property exponent     `MegaNumber` representing the binary‑exponent; its `negative`
 *                        flag encodes whether the overall exponent is positive
 *                        or negative, and its `mantissa` holds the magnitude in
 *                        32‑bit limbs.
 * @property negative     Sign flag
 * @property isFloat      Float flag
 * @property keepLeadingZeros Whether to keep leading zeros
 */
open class MegaNumber : BasicArithmeticOperations, FloatSpecificOperations, 
                   AdvancedMathOperations, BitManipulationOperations,
                   ChunkOperations, ConversionOperations, PowerOperations {
    var mantissa: IntArray
    var exponent: MegaNumber
    var negative: Boolean
    var isFloat: Boolean
    val keepLeadingZeros: Boolean

    // Implementation classes
    private val arithmeticCalculator: BasicArithmeticOperations
    private val floatOperations: FloatSpecificOperations
    private val advancedMathOperations: AdvancedMathOperations
    private val conversionOperations: ConversionOperations
    private val powerOperations: PowerOperations

    constructor(
        mantissa: IntArray = intArrayOf(0),
        exponent: MegaNumber = ZERO_EXPONENT,
        negative: Boolean = false,
        isFloat: Boolean = false,
        keepLeadingZeros: Boolean = false
    ) {
        this.mantissa = mantissa
        this.exponent = exponent
        this.negative = negative
        this.isFloat = isFloat
        this.keepLeadingZeros = keepLeadingZeros

        // Initialize implementation classes
        this.arithmeticCalculator = DefaultArithmeticCalculator(this)
        this.floatOperations = DefaultFloatOperations(this)
        this.advancedMathOperations = DefaultAdvancedMathOperations(this)
        this.conversionOperations = DefaultConversionOperations(this)
        this.powerOperations = DefaultPowerOperations(this)

        normalize()
    }

    /** Private no‑arg constructor used only to build the ZERO_EXPONENT sentinel. */
    private constructor() {
        mantissa = intArrayOf(0)
        exponent = this     // self‑reference so no further construction
        negative = false
        isFloat = false
        keepLeadingZeros = false

        // Initialize implementation classes with null values since this is only used for ZERO_EXPONENT
        arithmeticCalculator = DefaultArithmeticCalculator(this)
        floatOperations = DefaultFloatOperations(this)
        advancedMathOperations = DefaultAdvancedMathOperations(this)
        conversionOperations = DefaultConversionOperations(this)
        powerOperations = DefaultPowerOperations(this)
    }

    companion object {
        /**
         * If the supplied limb‑array encodes an exact power‑of‑two, returns the
         * number of **bit** positions to shift (i.e. log2(value)).
         * Otherwise returns −1.
         *
         * Runs in O(n) over the limb count and uses bit‑twiddling to verify
         * that exactly one bit is set across the entire array.
         */
        private fun powerOfTwoShift(chunks: IntArray): Int {
            var shift = 0
            var seenNonZero = false
            for (i in 0 until chunks.size) {
                val limb = chunks[i]
                if (limb == 0) {
                    shift += MegaNumberConstants.GLOBAL_CHUNK_SIZE
                    continue
                }
                /* limb must itself be a power‑of‑two **and** this must be the
                   only non‑zero limb encountered so far                       */
                if ((limb and (limb - 1)) != 0 || seenNonZero) return -1
                shift += limb.countTrailingZeroBits()
                seenNonZero = true
            }
            return if (seenNonZero) shift else -1
        }
        /** Shared “0‑exponent” instance to break default‑parameter recursion. */
        private val ZERO_EXPONENT = MegaNumber()
        /**
         * Right shift chunk-limbs by 1 bit => integer //2.
         */
        internal fun div2(limbs: IntArray): IntArray {
            if (limbs.size == 1 && limbs[0] == 0) return intArrayOf(0)

            val result = IntArray(limbs.size)
            var carry = 0          // LSB carried from higher limb

            for (i in limbs.indices.reversed()) {
                val cur = limbs[i].toLong() and 0xFFFFFFFFL
                result[i] = ((cur ushr 1) or ((carry.toLong() and 1) shl (MegaNumberConstants.GLOBAL_CHUNK_SIZE - 1))).toInt()
                carry = (cur and 1L).toInt()   // next iteration uses this bit
            }

            // Trim trailing zero limbs
            var last = result.size - 1
            while (last > 0 && result[last] == 0) last--
            return result.copyOf(last + 1)
        }

        /**
         * Add two chunk-limb arrays => sum-limb array
         */
        internal fun addChunks(a: IntArray, b: IntArray): IntArray {
            val maxLen = maxOf(a.size, b.size)
            val out = IntArray(maxLen + 1)
            var carry = 0L

            for (i in 0 until maxLen) {
                val av = if (i < a.size) a[i].toLong() and MegaNumberConstants.MASK else 0L
                val bv = if (i < b.size) b[i].toLong() and MegaNumberConstants.MASK else 0L
                val s = av + bv + carry
                out[i] = (s and MegaNumberConstants.MASK).toInt()
                carry = s ushr MegaNumberConstants.GLOBAL_CHUNK_SIZE
            }

            if (carry != 0L) {
                out[maxLen] = carry.toInt()
                return out
            }

            // Trim trailing zeros
            var lastNonZero = out.size - 1
            while (lastNonZero > 0 && out[lastNonZero] == 0) {
                lastNonZero--
            }

            return if (lastNonZero == out.size - 1) out else out.copyOf(lastNonZero + 1)
        }

        /**
         * Subtract B from A (assuming A >= B), returning chunk-limb array
         */
        internal fun subChunks(a: IntArray, b: IntArray, preserveSize: Boolean = true): IntArray {
            // Same implementation as above until the trimming part
            val out = IntArray(a.size)
            var borrow = 0L

            for (i in 0 until a.size) {
                val av = a[i].toLong() and MegaNumberConstants.MASK
                val bv = if (i < b.size) b[i].toLong() and MegaNumberConstants.MASK else 0L
                var diff = av - bv - borrow

                if (diff < 0) {
                    diff += MegaNumberConstants.MASK + 1
                    borrow = 1L
                } else {
                    borrow = 0L
                }

                out[i] = (diff and MegaNumberConstants.MASK).toInt()
            }

            if (preserveSize) {
                return out
            }

            // Find last non-zero element
            var lastNonZero = out.size - 1
            while (lastNonZero > 0 && out[lastNonZero] == 0) {
                lastNonZero--
            }

            return out.copyOf(lastNonZero + 1)
        }

        /**
         * Compare absolute magnitude of A vs. B.
         * Returns 1 if |A| > |B|, -1 if |A| < |B|, 0 if equal.
         */
        internal fun compareAbs(a: IntArray, b: IntArray): Int {
            // Quick length comparison
            if (a.size != b.size) return a.size.compareTo(b.size)

            // Same length – check limbs from most‑significant down
            for (i in a.indices.reversed()) {
                val cmp = a[i].compareTo(b[i])
                if (cmp != 0) return cmp
            }
            return 0
        }

        /**
         * Multiply two chunk-limb arrays => product-limb array using standard algorithm
         */
        private fun mulChunksStandard(a: IntArray, b: IntArray): IntArray {
            if (a.size == 1 && a[0] == 0) return intArrayOf(0)
            if (b.size == 1 && b[0] == 0) return intArrayOf(0)

            val la = a.size
            val lb = b.size
            val out = IntArray(la + lb)

            for (i in 0 until la) {
                val av = a[i].toLong() and MegaNumberConstants.MASK
                var carry = 0L

                for (j in 0 until lb) {
                    val bv = b[j].toLong() and MegaNumberConstants.MASK
                    val existing = out[i + j].toLong() and MegaNumberConstants.MASK
                    val prod = av * bv + existing + carry
                    out[i + j] = (prod and MegaNumberConstants.MASK).toInt()
                    carry = prod ushr MegaNumberConstants.GLOBAL_CHUNK_SIZE
                }

                if (carry != 0L) {
                    out[i + lb] = carry.toInt()
                }
            }

            // Trim trailing zeros
            var lastNonZero = out.size - 1
            while (lastNonZero > 0 && out[lastNonZero] == 0) {
                lastNonZero--
            }

            return out.copyOf(lastNonZero + 1)
        }

        /**
         * Adaptive dispatcher that chooses the best multiplication algorithm
         * (naïve, Karatsuba, or Toom‑3) based on operand length.
         * Callers should use *only this* function for chunk‑level multiplication.
         */
        internal fun mulChunks(a: IntArray, b: IntArray): IntArray {
            val n = maxOf(a.size, b.size)
            return if (n < MegaNumberConstants.MUL_THRESHOLD_KARATSUBA) {
                mulChunksStandard(a, b)
            } else if (n < MegaNumberConstants.MUL_THRESHOLD_TOOM) {
                karatsubaMulChunks(a, b)
            } else {
                toom3MulChunks(a, b)
            }
        }

        /** Multiply a chunk‑array by a small Int (0 ≤ k ≤ 8). */
        private fun mulBySmall(a: IntArray, k: Int): IntArray {
            if (k == 0) return intArrayOf(0)
            if (k == 1) return a.copyOf()
            val out = IntArray(a.size + 1)
            var carry = 0L
            for (i in 0 until a.size) {
                val prod = (a[i].toLong() and MegaNumberConstants.MASK) * k + carry
                out[i] = (prod and MegaNumberConstants.MASK).toInt()
                carry = prod ushr MegaNumberConstants.GLOBAL_CHUNK_SIZE
            }
            if (carry != 0L) out[a.size] = carry.toInt()
            // trim
            var last = out.size - 1
            while (last > 0 && out[last] == 0) last--
            return out.copyOf(last + 1)
        }

        /**
         * Implements Karatsuba multiplication for large numbers.
         */
        private fun karatsubaMulChunks(a: IntArray, b: IntArray): IntArray {
            val n = maxOf(a.size, b.size)
            if (n <= 32) {
                return mulChunksStandard(a, b) // Use standard multiplication for small sizes
            }

            val m = n / 2

            val aLow = a.copyOf(minOf(m, a.size))
            val aHigh = if (m < a.size) a.copyOfRange(m, a.size) else intArrayOf(0)
            val bLow = b.copyOf(minOf(m, b.size))
            val bHigh = if (m < b.size) b.copyOfRange(m, b.size) else intArrayOf(0)

            val z0 = karatsubaMulChunks(aLow, bLow)
            val z2 = karatsubaMulChunks(aHigh, bHigh)

            // Perform (A_low + A_high) * (B_low + B_high)
            val aSum = addChunks(aLow, aHigh)
            val bSum = addChunks(bLow, bHigh)
            val z1Full = karatsubaMulChunks(aSum, bSum)

            // Compute z1 = z1_full - z0 - z2
            val z1Intermediate = subChunks(z1Full, z0)
            val z1 = subChunks(z1Intermediate, z2)

            // Combine results: z2 * BASE^(2*m) + z1 * BASE^m + z0
            val z2Shifted = shiftLeft(z2, 2 * m)
            val z1Shifted = shiftLeft(z1, m)
            val intermediate = addChunks(z2Shifted, z1Shifted)
            return addChunks(intermediate, z0)
        }

        /**
         * Toom‑3 (aka Toom‑Cook 3‑way) multiplication for very large operands.
         * Splits each number into three equal‑length parts of k limbs.
         */
        private fun toom3MulChunks(a: IntArray, b: IntArray): IntArray {
            val n = maxOf(a.size, b.size)
            val k = (n + 2) / 3                    // ceil(n/3)

            // Split operands (low → high)
            fun slice(src: IntArray, from: Int, to: Int): IntArray =
                if (from >= src.size) intArrayOf(0)
                else src.copyOfRange(from, minOf(to, src.size))

            val a0 = slice(a, 0,           k)
            val a1 = slice(a, k,           2 * k)
            val a2 = slice(a, 2 * k,       3 * k)
            val b0 = slice(b, 0,           k)
            val b1 = slice(b, k,           2 * k)
            val b2 = slice(b, 2 * k,       3 * k)

            /* Evaluate at x = 0,  1, −1,  2,  ∞ */
            val p0   = mulChunksStandard(a0, b0)                // x = 0
            val pinf = mulChunksStandard(a2, b2)                // x = ∞

            val ap1  = addChunks(addChunks(a0, a1), a2)         // a(1)
            val bp1  = addChunks(addChunks(b0, b1), b2)
            val p1   = mulChunksStandard(ap1, bp1)              // x = 1

            val am1  = addChunks(subChunks(a0, a1, false), a2)  // a(−1) = a0 − a1 + a2
            val bm1  = addChunks(subChunks(b0, b1, false), b2)
            val pm1  = mulChunksStandard(am1, bm1)              // x = −1

            // a(2) = a0 + 2a1 + 4a2
            val a2x = addChunks(addChunks(a0, mulBySmall(a1, 2)), mulBySmall(a2, 4))
            val b2x = addChunks(addChunks(b0, mulBySmall(b1, 2)), mulBySmall(b2, 4))
            val p2   = mulChunksStandard(a2x, b2x)              // x = 2

            /* -------- Interpolation (derivation from standard Toom‑3) ------ */
            val c0 = p0
            val c4 = pinf

            // c1 = (p1 − pm1) / 2
            val t1 = if (compareAbs(p1, pm1) >= 0)
                        subChunks(p1, pm1, false) else subChunks(pm1, p1, false)
            val c1 = div2(t1)

            // c2 = pm1 − c0 − c4
            val c2tmp = subChunks(pm1, addChunks(c0, c4), false)
            val c2 = c2tmp

            // c3 = (p2 − p1 − 4*c4) / 2
            val p2MinusP1 = if (compareAbs(p2, p1) >= 0)
                                subChunks(p2, p1, false) else subChunks(p1, p2, false)
            val fourC4 = mulBySmall(c4, 4)
            val t3 = if (compareAbs(p2MinusP1, fourC4) >= 0)
                         subChunks(p2MinusP1, fourC4, false) else subChunks(fourC4, p2MinusP1, false)
            val c3 = div2(t3)

            /* -------- Recomposition:  result = Σ ci * BASE^{i*k} ---------- */
            fun shlChunks(x: IntArray, m: Int) = shiftLeft(x, m * k)

            var result = c0
            result = addChunks(result, shlChunks(c1, 1))
            result = addChunks(result, shlChunks(c2, 2))
            result = addChunks(result, shlChunks(c3, 3))
            result = addChunks(result, shlChunks(c4, 4))

            return result
        }

        /**
         * Shifts the limbs left by `shift` chunks (equivalent to multiplying by BASE^shift).
         */
        private fun shiftLeft(limbs: IntArray, shift: Int): IntArray {
            if (shift <= 0) return limbs.copyOf()
            val result = IntArray(limbs.size + shift)
            limbs.copyInto(result, shift)
            return result
        }






        /**
         * Interpret a chunk-array as a *small* non‑negative integer and return
         * it as a Kotlin `Int`.  We only support up to 31 bits because many
         * call‑sites (shift counts, loop indices, etc.) expect a value safely
         * representable in an `Int`.​
         *
         * Legacy code attempted to accumulate all limbs with `shl` operations
         * on an `Int`, which wrapped once the shift reached ≥ 32 bits and
         * produced wildly incorrect results (e.g. `0b11` became
         * `0b110000…000`).  By restricting the conversion to the least‑
         * significant limb and explicitly checking that all higher limbs are
         * zero we avoid silent overflow while still supporting every place
         * where `chunksToInt` is legitimately used.
         */
        internal fun chunksToInt(limbs: IntArray): Int {
            if (limbs.isEmpty()) return 0

            // Reject values that require more than 31 bits.
            if (limbs.size > 1 && limbs.drop(1).any { it != 0 }) {
                throw IllegalArgumentException(
                    "Value ${limbs.joinToString()} exceeds 31‑bit range and cannot be converted to Int"
                )
            }

            // Mask to 31 bits to avoid sign‑extension if bit 31 is set.
            return limbs[0] and 0x7FFFFFFF
        }


        /**
         * Convert decimal string => chunk-limb array
         */
        private fun decimalStringToChunks(dec: String): IntArray {
            if (dec.isEmpty()) return intArrayOf(0)
            if (dec == "0") return intArrayOf(0)

            var limbs = intArrayOf(0)
            for (ch in dec) {
                if (ch < '0' || ch > '9') {
                    throw IllegalArgumentException("Invalid decimal digit in $dec")
                }
                val digit : Int = (ch - '0')
                // Multiply limbs by 10, then add digit
                limbs = addChunks(mulChunks(limbs, intArrayOf(10)), intArrayOf(digit))
            }
            return limbs
        }

        /**
         * Create from decimal string, e.g. "123.456"
         */
        fun fromDecimalString(s: String): MegaNumber {
            // Basic parse
            var negative = false
            var raw = s.trim()
            if (raw.startsWith("-")) {
                negative = true
                raw = raw.substring(1).trim()
            }
            if (raw.isEmpty()) return MegaNumber()

            // Check float or int
            val parts = raw.split(".")
            if (parts.size == 1) {
                // Integer
                val mant = decimalStringToChunks(parts[0])
                return MegaNumber(
                    mantissa = mant,
                    exponent = MegaNumber(intArrayOf(0)),
                    negative = negative,
                    isFloat = false
                )
            } else {
                // Float
                val intPart = parts[0]
                val fracPart = parts[1]

                // Combine them as integer => do repeated multiply/add
                val fullNumStr = intPart + fracPart
                val mant = decimalStringToChunks(fullNumStr)
                // Approximate exponent using length of fraction => treat fraction as 2^some shift
                // E.g., log2(10) * fracLen
                val fracLen = fracPart.length
                val shiftBits = kotlin.math.ceil(fracLen * kotlin.math.log2(10.0)).toInt()
                val expChunks = intArrayOf(shiftBits)

                return MegaNumber(
                    mantissa = mant,
                    exponent = MegaNumber(expChunks, negative = true),
                    negative = negative,
                    isFloat = true
                )
            }
        }

    }

    /**
     * Check if a MegaNumber exceeds the maximum precision limit.
     * Throws an exception if the limit is exceeded.
     *
     * @param num The MegaNumber to check
     * @throws IllegalStateException if the precision limit is exceeded
     */
    internal fun checkPrecisionLimit(num: MegaNumber) {
        val maxBits = MegaNumberConstants.maxPrecisionBits
        if (maxBits > 0) {
            val totalBits = num.mantissa.size * MegaNumberConstants.GLOBAL_CHUNK_SIZE
            if (totalBits > maxBits) {
                throw IllegalStateException("Precision limit exceeded: $totalBits bits > $maxBits bits")
            }
        }
    }

    /**
     * Remove trailing zeros, handle zero sign, etc.
     */
    open fun normalize() {
        if (!keepLeadingZeros) {
            // Trim mantissa
            var lastNonZero = mantissa.size - 1
            while (lastNonZero > 0 && mantissa[lastNonZero] == 0) {
                lastNonZero--
            }
            if (lastNonZero < mantissa.size - 1) {
                mantissa = mantissa.copyOf(lastNonZero + 1)
            }

            // Trim exponent mantissa if this number is a float
            if (isFloat) {
                lastNonZero = exponent.mantissa.size - 1
                while (lastNonZero > 0 && exponent.mantissa[lastNonZero] == 0) {
                    lastNonZero--
                }
                if (lastNonZero < exponent.mantissa.size - 1) {
                    exponent.mantissa = exponent.mantissa.copyOf(lastNonZero + 1)
                }
            }

            // If zero => unify sign
            if (mantissa.size == 1 && mantissa[0] == 0) {
                negative = false
                exponent = ZERO_EXPONENT        // avoid recursive construction
            }
        } else {
            // keepLeadingZeros = true: do not trim mantissa/exponent, but unify sign if all zero
            if (mantissa.all { it == 0 }) {
                negative = false
            }
        }
    }

    /**
     * Implement chunk-based right shift.
     *
     * @param shiftBits Number of bits to shift; must be >= 0.
     */
    internal fun shiftRight(limbs: IntArray, shiftBits: Int): IntArray {
        // Shift count must be non-negative.
        if (shiftBits < 0) {
            throw IllegalArgumentException("shiftBits must be non-negative")
        }
        if (shiftBits == 0) {
            // No shift – return a copy so callers can freely mutate the result
            return limbs.copyOf()
        }

        val chunkShift = shiftBits / MegaNumberConstants.GLOBAL_CHUNK_SIZE
        val bitShift   = shiftBits % MegaNumberConstants.GLOBAL_CHUNK_SIZE

        if (chunkShift >= limbs.size) {
            return intArrayOf(0)
        }

        // Create result array
        val resultSize = limbs.size - chunkShift
        val result     = IntArray(resultSize)

        if (bitShift == 0) {
            // Just copy the chunks
            for (i in 0 until resultSize) {
                result[i] = limbs[i + chunkShift]
            }
        } else {
            // Shift with carry
            for (i in 0 until resultSize) {
                val currentChunk = limbs[i + chunkShift].toLong() and 0xFFFFFFFFL
                val nextChunk    = if (i + chunkShift + 1 < limbs.size) {
                    limbs[i + chunkShift + 1].toLong() and 0xFFFFFFFFL
                } else {
                    0L
                }

                // Take upper bits from next chunk and lower bits from current
                result[i] = (((nextChunk shl (MegaNumberConstants.GLOBAL_CHUNK_SIZE - bitShift)) or
                        (currentChunk ushr bitShift)) and 0xFFFFFFFFL).toInt()
            }
        }

        // Zero-fill: discard the least-significant `bitShift` bits that were shifted out.
        if (bitShift != 0) {
            val mask = -1 shl bitShift      // e.g. bitShift=16 → 0xFFFF0000
            result[0] = result[0] and mask  // clear those lower bits
        }

        // Trim trailing zeros
        var lastNonZero = result.size - 1
        while (lastNonZero > 0 && result[lastNonZero] == 0) {
            lastNonZero--
        }

        return if (lastNonZero < result.size - 1) {
            result.copyOf(lastNonZero + 1)
        } else {
            result
        }
    }

    /**
     * Divide chunk-limb arrays => (quotient, remainder), integer division
     */
    internal fun chunkDivide(a: IntArray, b: IntArray): Pair<IntArray, IntArray> {
        // B must not be zero
        if (b.size == 1 && b[0] == 0) {
            throw ArithmeticException("Division by zero")
        }
        val c = compareAbs(a, b)
        if (c < 0) return Pair(intArrayOf(0), a.copyOf()) // A<B => Q=0, R=A
        if (c == 0) return Pair(intArrayOf(1), intArrayOf(0)) // A=B => Q=1, R=0

        val q = IntArray(a.size)
        var r = intArrayOf(0)

        // We do a standard chunk-based short division
        for (i in a.indices.reversed()) {
            // shift R left by one chunk
            r = IntArray(r.size + 1).also {
                r.copyInto(it, 1)
                it[0] = a[i]
            }

            // binary search in [0..BASE-1] for the best q
            var low = 0
            var high = Int.MAX_VALUE // Approximation for BASE-1
            var guess = 0

            while (low <= high) {
                val mid = (low + high) shr 1
                val mm = mulChunks(b, intArrayOf(mid))
                val cmpv = compareAbs(mm, r)
                if (cmpv <= 0) {
                    guess = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            if (guess != 0) {
                val mm = mulChunks(b, intArrayOf(guess))
                r = subChunks(r, mm)
            }
            q[i] = guess
        }

        // Trim q
        var lastNonZero = q.size - 1
        while (lastNonZero > 0 && q[lastNonZero] == 0) {
            lastNonZero--
        }
        return Pair(q.copyOf(lastNonZero + 1), r)
    }

    /**
     * Divmod by 10 (decimal conversion helper).
     */
    internal fun divMod10(a: IntArray): Pair<IntArray, Int> {
        var remainder = 0
        val out = IntArray(a.size)

        for (i in a.indices.reversed()) {
            // Shift the remainder left by GLOBAL_CHUNK_SIZE bits and add the current limb
            val cur = (remainder shl MegaNumberConstants.GLOBAL_CHUNK_SIZE) + a[i]

            // Compute the quotient digit and the new remainder (dividing by 10)
            val qd = cur / 10
            remainder = cur % 10

            // Assign the quotient digit to the output array, ensuring it fits within the chunk mask
            out[i] = qd and MegaNumberConstants.MASK.toInt()
        }

        // Trim any unnecessary trailing zeros from the output array
        var lastNonZero = out.size - 1
        while (lastNonZero > 0 && out[lastNonZero] == 0) {
            lastNonZero--
        }

        return Pair(out.copyOf(lastNonZero + 1), remainder)
    }

    /**
     * Convert chunk-limbs to decimal string
     */
    internal fun chunkToDecimal(limbs: IntArray): String {
        // quick check for zero
        if (limbs.size == 1 && limbs[0] == 0) {
            return "0"
        }
        var temp = limbs.copyOf()
        val digits = mutableListOf<Char>()
        while (!(temp.size == 1 && temp[0] == 0)) {
            val (q, r) = divMod10(temp)
            temp = q
            digits.add('0' + r)
        }
        return digits.reversed().joinToString("")
    }

    /** Treat `this` (a MegaNumber exponent) as a signed Int */
    internal fun MegaNumber.expAsInt(): Int {
        val absVal = chunksToInt(this.mantissa)
        return if (this.negative) -absVal else absVal
    }

    /**
     * Return a decimal-string representation. (Integer-only if exponent=0.)
     */
    override fun toDecimalString(): String {
        return conversionOperations.toDecimalString()
    }

    /**
     * Add two MegaNumbers. If either is float, handle float addition
     */
    override fun add(other: MegaNumber): MegaNumber {
        return arithmeticCalculator.add(other)
    }

    /**
     * Float addition using chunk-based arithmetic
     */
    override fun addFloat(other: MegaNumber): MegaNumber {
        return floatOperations.addFloat(other)
    }

    /**
     * Subtract two MegaNumbers. a - b = a + (-b)
     */
    override fun sub(other: MegaNumber): MegaNumber {
        return arithmeticCalculator.sub(other)
    }

    /**
     * Multiply two MegaNumbers. If either is float, delegate to float multiply
     */
    override fun mul(other: MegaNumber): MegaNumber {
        return arithmeticCalculator.mul(other)
    }

    /**
     * Float multiplication using chunk-based arithmetic
     */
    override fun mulFloat(other: MegaNumber): MegaNumber {
        return floatOperations.mulFloat(other)
    }

    /** Integer division branch, used when *both* numbers are plain integers */
    internal fun divideInteger(other: MegaNumber): MegaNumber {
        /* --- fast path for single‑chunk integers --------------------------- */
        if (!this.isFloat && !other.isFloat &&
            this.mantissa.size == 1 && other.mantissa.size == 1 &&
            this.exponent.mantissa.size == 1 && this.exponent.mantissa[0] == 0 &&
            other.exponent.mantissa.size == 1 && other.exponent.mantissa[0] == 0
        ) {
            val lhs = this.mantissa[0].toUInt()
            val rhs = other.mantissa[0].toUInt()
            require(rhs != 0u) { "Division by zero" }
            val neg = this.negative xor other.negative
            val q = (lhs / rhs).toInt()
            return MegaNumber(intArrayOf(q), MegaNumber(intArrayOf(0)), neg, false)
        }
        /* --- long‑form integer division ------------------------------------ */
        if (other.mantissa.size == 1 && other.mantissa[0] == 0) {
            throw ArithmeticException("Division by zero")
        }
        // ---- fast path when divisor is an exact power‑of‑two --------------
        val sign = (this.negative != other.negative)
        val powShift = powerOfTwoShift(other.mantissa)
        if (powShift >= 0) {
            val (qChunks, _) = divideBy2ToThePower(this.mantissa, powShift)
            return MegaNumber(qChunks, MegaNumber(intArrayOf(0)), sign, false)
        }
        val cmp = compareAbs(this.mantissa, other.mantissa)
        return when {
            cmp < 0 -> MegaNumber(intArrayOf(0))
            cmp == 0 -> MegaNumber(intArrayOf(1), MegaNumber(intArrayOf(0)), sign, false)
            else -> {
                val (q, _) = chunkDivide(this.mantissa, other.mantissa)
                MegaNumber(q, MegaNumber(intArrayOf(0)), sign, false)
            }
        }
    }

    /** Float division branch used when either operand is float */
    internal fun divideFloat(other: MegaNumber): MegaNumber {
        // Divide mantissas
        val (quotientMant, _) = chunkDivide(this.mantissa, other.mantissa)
        // Subtract exponents
        val diffExp = this.exponent.expAsInt() - other.exponent.expAsInt()
        val newExponent = MegaNumber(intArrayOf(kotlin.math.abs(diffExp)), negative = diffExp < 0)
        // Determine sign
        val newNegative = (this.negative != other.negative)
        val out = MegaNumber(
            mantissa = quotientMant,
            exponent = newExponent,
            negative = newNegative,
            isFloat = true
        )
        out.normalize()
        return out
    }

    /**
     * Divide two MegaNumbers. If either is float, delegate to float division
     */
    override fun divide(other: MegaNumber): MegaNumber {
        return arithmeticCalculator.divide(other)
    }

    /**
     * Compute the square root of this MegaNumber.
     * For integer values, returns the integer square root.
     * For float values, returns a float approximation.
     *
     * @return The square root as a MegaNumber
     * @throws IllegalArgumentException if this MegaNumber is negative
     */
    override fun sqrt(): MegaNumber {
        return advancedMathOperations.sqrt()
    }

    /**
     * Raise this MegaNumber to the power of another MegaNumber.
     *
     * @param exponent The exponent as a MegaNumber
     * @return The result as a MegaNumber
     */
    override fun pow(exponent: MegaNumber): MegaNumber {
        return powerOperations.pow(exponent)
    }

    /**
     * Raise this MegaNumber to the power of a MegaFloat.
     *
     * @param exponent The exponent as a MegaFloat
     * @return The result as a MegaNumber
     */
    open fun pow(exponent: MegaFloat): MegaNumber {
        return pow(exponent as MegaNumber)
    }

    /**
     * Raise this MegaNumber to the power of a MegaInteger.
     *
     * @param exponent The exponent as a MegaInteger
     * @return The result as a MegaNumber
     */
    open fun pow(exponent: MegaInteger): MegaNumber {
        return pow(exponent as MegaNumber)
    }

    /**
     * Compute the square root for float values.
     *
     * @return The square root as a MegaNumber with float representation
     */
    internal fun floatSqrt(): MegaNumber {
        // Get the exponent as an integer
        val totalExp = this.exponent.expAsInt()

        // Check if exponent is odd
        val remainder = totalExp and 1          // 1 = odd, 0 = even

        // Make a working copy of mantissa
        var workMantissa = mantissa.copyOf()
        var adjustedExp = totalExp

        // If exponent is odd, adjust mantissa and exponent
        if (remainder != 0) {
            if (totalExp > 0) {
                // Double the mantissa (shift left by 1 bit)
                var carry = 0
                val result = IntArray(workMantissa.size + 1)
                for (i in workMantissa.indices) {
                    val doubled = (workMantissa[i] shl 1) + carry
                    result[i] = doubled and MegaNumberConstants.MASK.toInt()
                    carry = doubled shr MegaNumberConstants.GLOBAL_CHUNK_SIZE
                }
                if (carry != 0) {
                    result[workMantissa.size] = carry
                }
                workMantissa = result
                adjustedExp = adjustedExp - 1
            } else {
                // Halve the mantissa (shift right by 1 bit)
                val result = IntArray(workMantissa.size)
                var carry = 0
                for (i in workMantissa.indices.reversed()) {
                    val value = workMantissa[i]
                    result[i] = (value shr 1) or (carry shl (MegaNumberConstants.GLOBAL_CHUNK_SIZE - 1))
                    carry = value and 1
                }
                workMantissa = result
                adjustedExp = adjustedExp + 1
            }
        }

        // Half of exponent
        val halfExp = adjustedExp / 2

        // Do integer sqrt on workMantissa
        var low = intArrayOf(0)
        var high = workMantissa.copyOf()
        var sqrtMantissa: IntArray

        while (true) {
            // mid = (low + high) / 2
            val sumLH = addChunks(low, high)
            val mid = div2(sumLH)

            // Check if we've converged
            val cLo = compareAbs(mid, low)
            val cHi = compareAbs(mid, high)
            if (cLo == 0 || cHi == 0) {
                sqrtMantissa = mid
                break
            }

            // mid^2
            val midSqr = mulChunks(mid, mid)

            // Compare mid^2 with workMantissa
            val cCmp = compareAbs(midSqr, workMantissa)
            if (cCmp == 0) {
                sqrtMantissa = mid
                break
            } else if (cCmp < 0) {
                low = mid
            } else {
                high = mid
            }
        }

        // Create the result with half the exponent
        val newExponent = MegaNumber(intArrayOf(kotlin.math.abs(halfExp)), negative = halfExp < 0)

        val out = MegaNumber(
            mantissa = sqrtMantissa,
            exponent = newExponent,
            negative = false,
            isFloat = true
        )
        out.normalize()
        checkPrecisionLimit(out)
        return out
    }

    /**
     * Divide chunks by 2^bits, returning quotient and remainder.
     * This uses IntArray for proper 32-bit chunk operations.
     *
     * @param chunks The chunks to divide
     * @param bits The power of 2 to divide by
     * @return Pair of (quotient, remainder)
     */
    internal fun divideBy2ToThePower(chunks: IntArray, bits: Int): Pair<IntArray, IntArray> {
        if (bits <= 0) {
            return Pair(chunks.copyOf(), intArrayOf(0))
        }

        // Use the chunks directly since they are already IntArray
        val intChunks = chunks.copyOf()

        // Calculate whole chunk shifts and bit shifts within chunks
        val chunkShift = bits / MegaNumberConstants.GLOBAL_CHUNK_SIZE
        val bitShift = bits % MegaNumberConstants.GLOBAL_CHUNK_SIZE

        // Handle chunk-level right shift
        val quotientInt = if (chunkShift >= intChunks.size) {
            IntArray(1) { 0 }
        } else {
            IntArray(intChunks.size - chunkShift) { i ->
                if (i + chunkShift < intChunks.size) {
                    intChunks[i + chunkShift]
                } else {
                    0
                }
            }
        }

        // Handle bit-level right shift within chunks
        val finalQuotient = if (bitShift > 0) {
            IntArray(quotientInt.size) { i ->
                val current = quotientInt[i]
                val carry = if (i + 1 < quotientInt.size) {
                    (quotientInt[i + 1] shl (MegaNumberConstants.GLOBAL_CHUNK_SIZE - bitShift)) and MegaNumberConstants.MASK.toInt()
                } else {
                    0
                }
                ((current shr bitShift) or carry)
            }
        } else {
            quotientInt
        }

        // Convert back to IntArray
        val quotient = IntArray(finalQuotient.size) { finalQuotient[it] }

        // Calculate remainder: original - (quotient << bits)
        val quotientShifted = multiplyBy2ToThePower(quotient, bits)
        val remainder = if (compareAbs(chunks, quotientShifted) >= 0) {
            subChunks(chunks, quotientShifted)
        } else {
            intArrayOf(0)
        }

        return Pair(quotient, remainder)
    }

    /**
     * Multiply chunks by 2^bits using IntArray for proper 32-bit operations.
     */
    internal fun multiplyBy2ToThePower(chunks: IntArray, bits: Int): IntArray {
        if (bits <= 0) {
            return chunks.copyOf()
        }

        // Use the chunks directly since they are already IntArray
        val intChunks = chunks.copyOf()

        // Calculate whole chunk shifts and bit shifts within chunks
        val chunkShift = bits / MegaNumberConstants.GLOBAL_CHUNK_SIZE
        val bitShift = bits % MegaNumberConstants.GLOBAL_CHUNK_SIZE

        // Handle chunk-level left shift (add zero chunks at the beginning)
        val expandedSize = intChunks.size + chunkShift + if (bitShift > 0) 1 else 0
        val shiftedInt = IntArray(expandedSize) { i ->
            if (i < chunkShift) {
                0
            } else if (i - chunkShift < intChunks.size) {
                intChunks[i - chunkShift]
            } else {
                0
            }
        }

        // Handle bit-level left shift within chunks
        val finalResult = if (bitShift > 0) {
            var carry = 0
            IntArray(shiftedInt.size) { i ->
                val current = shiftedInt[i]
                val shifted = (current shl bitShift) or carry
                carry = (shifted shr MegaNumberConstants.GLOBAL_CHUNK_SIZE) and MegaNumberConstants.MASK.toInt()
                (shifted and MegaNumberConstants.MASK.toInt())
            }
        } else {
            shiftedInt
        }

        // Convert back to IntArray and trim trailing zeros
        val result = IntArray(finalResult.size) { finalResult[it] }
        var lastNonZero = result.size - 1
        while (lastNonZero > 0 && result[lastNonZero] == 0) {
            lastNonZero--
        }
        return result.copyOf(lastNonZero + 1)
    }

}
