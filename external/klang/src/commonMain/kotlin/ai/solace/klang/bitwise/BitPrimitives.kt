package ai.solace.klang.bitwise

/**
 * @native-bitshift-allowed This is a core BitShift implementation file.
 * Native bitwise operations (shl, shr, ushr, and, or) are permitted here
 * as this file provides the foundation for the BitShift engine.
 */

/**
 * Low-level helpers that mirror common C bit-manipulation intrinsics. These
 * operations are written once here so ports can rely on a single, consistent
 * implementation rather than sprinkling ad-hoc shifts throughout the codebase.
 */
object BitPrimitives {
    private val shift32 get() = BitShiftEngine(BitShiftConfig.defaultMode, 32)
    private val shift64 get() = BitShiftEngine(BitShiftConfig.defaultMode, 64)
    private val arith32 = ArithmeticBitwiseOps.BITS_32

    // ---- Leading/trailing zeros & population count ----

    fun clz32(x: Int): Int = x.countLeadingZeroBits()
    fun clz64(x: Long): Int = x.countLeadingZeroBits()

    fun ctz32(x: Int): Int = if (x == 0) 32 else x.countTrailingZeroBits()
    fun ctz64(x: Long): Int = if (x == 0L) 64 else x.countTrailingZeroBits()

    fun popcount32(x: Int): Int = x.countOneBits()
    fun popcount64(x: Long): Int = x.countOneBits()

    // ---- Rotations ----

    fun rotl32(value: Int, distance: Int): Int {
        val sh = ((distance % 32) + 32) % 32
        if (sh == 0) return value
        val left = shift32.leftShift(value.toLong(), sh).value.toInt()
        val right = shift32.unsignedRightShift(value.toLong(), 32 - sh).value.toInt()
        return arith32.or(left.toLong(), right.toLong()).toInt()
    }

    fun rotr32(value: Int, distance: Int): Int {
        val sh = ((distance % 32) + 32) % 32
        if (sh == 0) return value
        val right = shift32.unsignedRightShift(value.toLong(), sh).value.toInt()
        val left = shift32.leftShift(value.toLong(), 32 - sh).value.toInt()
        return arith32.or(left.toLong(), right.toLong()).toInt()
    }

    fun rotl64(value: Long, distance: Int): Long {
        val sh = ((distance % 64) + 64) % 64
        if (sh == 0) return value
        val left = shift64.leftShift(value, sh).value
        val right = shift64.unsignedRightShift(value, 64 - sh).value
        return (left or right)
    }

    fun rotr64(value: Long, distance: Int): Long {
        val sh = ((distance % 64) + 64) % 64
        if (sh == 0) return value
        val right = shift64.unsignedRightShift(value, sh).value
        val left = shift64.leftShift(value, 64 - sh).value
        return (left or right)
    }

    // ---- Bit-field extraction / insertion ----

    fun bitFieldExtract32(value: Int, offset: Int, width: Int): Int {
        if (width <= 0) return 0
        if (offset !in 0..<32) return 0
        val w = minOf(width, 32 - offset)
        val fieldMask = if (w == 32) -1 else (1 shl w) - 1
        val shifted = shift32.unsignedRightShift(value.toLong(), offset).value.toInt()
        return if (w == 32) shifted else shifted and fieldMask
    }

    fun bitFieldInsert32(base: Int, insert: Int, offset: Int, width: Int): Int {
        if (width <= 0) return base
        if (offset !in 0..<32) return base
        val w = minOf(width, 32 - offset)
        val fieldMask = if (w == 32) -1 else (1 shl w) - 1
        val mask = if (w == 32) -1 else fieldMask shl offset
        val cleared = base and mask.inv()
        val writeVal = if (w == 32) insert else (insert and fieldMask) shl offset
        return cleared or (writeVal and mask)
    }

    fun bitFieldExtract64(value: Long, offset: Int, width: Int): Long {
        if (width <= 0) return 0
        if (offset !in 0..<64) return 0
        val w = minOf(width, 64 - offset)
        val mask = if (w == 64) -1L else (1L shl w) - 1L
        val shifted = shift64.unsignedRightShift(value, offset).value
        return shifted and mask
    }

    fun bitFieldInsert64(base: Long, insert: Long, offset: Int, width: Int): Long {
        if (width <= 0) return base
        if (offset !in 0..<64) return base
        val w = minOf(width, 64 - offset)
        val fieldMask = if (w == 64) -1L else (1L shl w) - 1L
        val mask = if (w == 64) -1L else fieldMask shl offset
        val cleared = base and mask.inv()
        val toWrite = if (w == 64) insert else (insert and fieldMask) shl offset
        return cleared or (toWrite and mask)
    }
}
