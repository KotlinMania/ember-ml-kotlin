package ai.solace.klang.int

import ai.solace.klang.bitwise.ArithmeticBitwiseOps
import ai.solace.klang.bitwise.BitShiftEngine
import ai.solace.klang.bitwise.BitShiftMode

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

    /**
     * Normalize limbs in an IntArray by ensuring each limb is in range [0, 65535].
     * Handles negative values by adding LIMB_BASE.
     */
    private fun normalizeLimbs(limbs: IntArray) {
        for (i in limbs.indices) {
            var limb = limbs[i]
            limb %= LIMB_BASE
            if (limb < 0) limb += LIMB_BASE
            limbs[i] = limb
        }
    }
    
    /**
     * Write ULong to heap as 128-bit value (zero-extended).
     * @param addr Heap address to write to (16 bytes)
     * @param value ULong value to write
     */
    fun writeULongToHeap(addr: Int, value: ULong) {
        var remainder = value
        for (i in 0 until LIMB_COUNT) {
            val limb = (remainder % LIMB_BASE_UL).toInt() and LIMB_MASK
            writeLimb(addr + i * 2, limb)
            remainder /= LIMB_BASE_UL
        }
    }
    
    /**
     * Convert heap-based 128-bit value to hexadecimal string.
     * @param addr Heap address of 8 limbs (16 bytes)
     * @return Big-endian hex string (e.g., "fedcba9876543210")
     */
    fun toBigEndianHexHeap(addr: Int): String {
        return buildString(LIMB_COUNT * 4) {
            for (i in LIMB_COUNT - 1 downTo 0) {
                val limb = readLimb(addr + i * 2) and LIMB_MASK
                append(limb.toString(16).padStart(4, '0'))
            }
        }.trimStart('0').ifEmpty { "0" }
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

    // -----------------------------------------------------------------------------------------
    // Heap-native operations (zero-copy)
    
    /**
     * Read a 16-bit limb from heap at given address.
     * Assumes little-endian storage: low byte at addr, high byte at addr+1.
     */
    private fun readLimb(addr: Int): Int {
        val ops16 = ArithmeticBitwiseOps.BITS_16
        val lo = ai.solace.klang.mem.GlobalHeap.lbu(addr)
        val hi = ai.solace.klang.mem.GlobalHeap.lbu(addr + 1)
        val shifted = ops16.leftShift(hi.toLong(), 8)
        return ops16.or(lo.toLong(), shifted).toInt()
    }
    
    /**
     * Write a 16-bit limb to heap at given address.
     * Assumes little-endian storage: low byte at addr, high byte at addr+1.
     */
    private fun writeLimb(addr: Int, value: Int) {
        val ops16 = ArithmeticBitwiseOps.BITS_16
        val ops8 = ArithmeticBitwiseOps.BITS_8
        val normalized = ops16.and(value.toLong(), LIMB_MASK.toLong())
        val lowByte = ops8.and(normalized, 0xFFL)
        val highByte = ops8.and(ops16.rightShift(normalized, 8), 0xFFL)
        ai.solace.klang.mem.GlobalHeap.sb(addr, lowByte.toByte())
        ai.solace.klang.mem.GlobalHeap.sb(addr + 1, highByte.toByte())
    }
    
    /**
     * Add two 128-bit integers stored in heap, write result to dest.
     * @param aAddr address of first operand (8 limbs = 16 bytes)
     * @param bAddr address of second operand (8 limbs = 16 bytes)
     * @param destAddr address for result (8 limbs = 16 bytes)
     * @return carry out (0 or 1)
     */
    fun addHeap(aAddr: Int, bAddr: Int, destAddr: Int): Int {
        var carry = 0uL
        var offset = 0
        for (i in 0 until LIMB_COUNT) {
            val aLimb = readLimb(aAddr + offset)
            val bLimb = readLimb(bAddr + offset)
            val sum = aLimb.toULong() + bLimb.toULong() + carry
            writeLimb(destAddr + offset, (sum % LIMB_BASE_UL).toInt())
            carry = sum / LIMB_BASE_UL
            offset += 2
        }
        return carry.toInt()
    }
    
    /**
     * Subtract two 128-bit integers stored in heap, write result to dest.
     * @param aAddr address of first operand (8 limbs = 16 bytes)
     * @param bAddr address of second operand (8 limbs = 16 bytes)
     * @param destAddr address for result (8 limbs = 16 bytes)
     * @return borrow out (0 or 1)
     */
    fun subHeap(aAddr: Int, bAddr: Int, destAddr: Int): Int {
        var borrow = 0L
        var offset = 0
        for (i in 0 until LIMB_COUNT) {
            val aLimb = readLimb(aAddr + offset)
            val bLimb = readLimb(bAddr + offset)
            var diff = aLimb.toLong() - bLimb.toLong() - borrow
            if (diff < 0) {
                diff += LIMB_BASE.toLong()
                borrow = 1
            } else {
                borrow = 0
            }
            writeLimb(destAddr + offset, (diff % LIMB_BASE.toLong()).toInt())
            offset += 2
        }
        return borrow.toInt()
    }
    
    /**
     * Compare two 128-bit integers stored in heap.
     * @return -1 if a < b, 0 if a == b, 1 if a > b
     */
    fun compareHeap(aAddr: Int, bAddr: Int): Int {
        var offset = (LIMB_COUNT - 1) * 2
        for (i in LIMB_COUNT - 1 downTo 0) {
            val aLimb = readLimb(aAddr + offset) and LIMB_MASK
            val bLimb = readLimb(bAddr + offset) and LIMB_MASK
            if (aLimb != bLimb) return if (aLimb > bLimb) 1 else -1
            offset -= 2
        }
        return 0
    }
    
    /**
     * Shift left a 128-bit integer in heap by specified bits.
     * @param srcAddr source address (8 limbs = 16 bytes)
     * @param destAddr destination address (8 limbs = 16 bytes)
     * @param bits number of bits to shift
     * @return spill (bits shifted out beyond 128 bits)
     */
    fun shiftLeftHeap(srcAddr: Int, destAddr: Int, bits: Int): ULong {
        require(bits >= 0)
        
        if (bits == 0) {
            // Copy src to dest
            ai.solace.klang.mem.GlobalHeap.memcpy(destAddr, srcAddr, LIMB_COUNT * 2)
            return 0uL
        }
        
        if (bits >= LIMB_COUNT * LIMB_BITS) {
            // Everything shifts out, result is zero
            ai.solace.klang.mem.GlobalHeap.memset(destAddr, 0, LIMB_COUNT * 2)
            return accumulateSpillHeap(srcAddr)
        }
        
        val wordShift = bits / LIMB_BITS
        val bitShift = bits % LIMB_BITS
        
        // First, do word shift
        var spill = 0uL
        
        // Accumulate limbs that will be shifted out (from MSB side)
        for (i in LIMB_COUNT - wordShift until LIMB_COUNT) {
            val ops16 = ArithmeticBitwiseOps.BITS_16
            val limbValue = ops16.and(readLimb(srcAddr + i * 2).toLong(), LIMB_MASK.toLong()).toULong()
            val shiftAmount = (i - (LIMB_COUNT - wordShift)) * LIMB_BITS
            // Compute 2^shiftAmount as power-of-2 multiplication
            var multiplier = 1uL
            repeat(shiftAmount) { multiplier *= 2uL }
            spill += limbValue * multiplier
        }
        
        // Shift limbs upward
        for (i in LIMB_COUNT - 1 downTo wordShift) {
            val limb = readLimb(srcAddr + (i - wordShift) * 2)
            writeLimb(destAddr + i * 2, limb)
        }
        
        // Lower limbs become zero
        for (i in 0 until wordShift) {
            writeLimb(destAddr + i * 2, 0)
        }
        
        if (bitShift == 0) {
            return spill
        }
        
        // Now do bit shift within limbs
        var carry = 0uL
        for (i in 0 until LIMB_COUNT) {
            val ops16 = ArithmeticBitwiseOps.BITS_16
            val limb = readLimb(destAddr + i * 2)
            val masked = ops16.and(limb.toLong(), LIMB_MASK.toLong()).toULong()
            // Compute 2^bitShift as power-of-2 multiplication
            var multiplier = 1uL
            repeat(bitShift) { multiplier *= 2uL }
            val raw = masked * multiplier
            val combined = raw + carry
            writeLimb(destAddr + i * 2, (combined % LIMB_BASE_UL).toInt())
            carry = combined / LIMB_BASE_UL
        }
        
        // Carry from the MSB limb is spill (bits shifted out beyond 128 bits)
        spill += carry
        return spill
    }
    
    /**
     * Shift right a 128-bit integer in heap by specified bits.
     * @param srcAddr source address (8 limbs = 16 bytes)
     * @param destAddr destination address (8 limbs = 16 bytes)
     * @param bits number of bits to shift
     * @return spill (bits shifted out)
     */
    fun shiftRightHeap(srcAddr: Int, destAddr: Int, bits: Int): ULong {
        require(bits >= 0)
        
        if (bits == 0) {
            ai.solace.klang.mem.GlobalHeap.memcpy(destAddr, srcAddr, LIMB_COUNT * 2)
            return 0uL
        }
        
        if (bits >= LIMB_COUNT * LIMB_BITS) {
            ai.solace.klang.mem.GlobalHeap.memset(destAddr, 0, LIMB_COUNT * 2)
            return accumulateSpillHeap(srcAddr)
        }
        
        val wordShift = bits / LIMB_BITS
        val bitShift = bits % LIMB_BITS
        
        var spill = 0uL
        
        // Accumulate limbs that will be shifted out (from LSB side)
        for (i in 0 until wordShift) {
            spill += (readLimb(srcAddr + i * 2) and LIMB_MASK).toULong() * powerOf2(i * LIMB_BITS)
        }
        
        // Shift limbs downward
        for (i in 0 until LIMB_COUNT - wordShift) {
            val limb = readLimb(srcAddr + (i + wordShift) * 2)
            writeLimb(destAddr + i * 2, limb)
        }
        
        // Upper limbs become zero
        for (i in LIMB_COUNT - wordShift until LIMB_COUNT) {
            writeLimb(destAddr + i * 2, 0)
        }
        
        if (bitShift == 0) {
            return spill
        }
        
        // Now do bit shift within limbs
        var carry = 0uL
        for (i in LIMB_COUNT - 1 downTo 0) {
            val limb = readLimb(destAddr + i * 2)
            val raw = (limb and LIMB_MASK).toULong() + carry * LIMB_BASE_UL
            writeLimb(destAddr + i * 2, (raw / powerOf2(bitShift)).toInt())
            carry = raw % powerOf2(bitShift)
        }
        
        // Carry represents bits shifted out from LSB
        spill += carry
        return spill
    }
    
    /**
     * Compute 2^n without using bit shifts (for cross-platform consistency).
     * @param n exponent (0-63)
     * @return 2^n as ULong
     */
    private fun powerOf2(n: Int): ULong {
        require(n in 0..63) { "powerOf2: exponent must be in range 0-63" }
        var result = 1uL
        repeat(n) {
            result *= 2uL
        }
        return result
    }
    
    /**
     * Accumulate all limbs of a heap-based 128-bit integer into a ULong.
     * Used for spill calculation.
     */
    private fun accumulateSpillHeap(addr: Int): ULong {
        var total = 0uL
        var factor = 1uL
        for (i in 0 until LIMB_COUNT) {
            total += (readLimb(addr + i * 2) and LIMB_MASK).toULong() * factor
            factor *= LIMB_BASE_UL
        }
        return total
    }
    
    /**
     * Write zero to a 128-bit integer in heap.
     */
    fun zeroHeap(addr: Int) {
        ai.solace.klang.mem.GlobalHeap.memset(addr, 0, LIMB_COUNT * 2)
    }
}
