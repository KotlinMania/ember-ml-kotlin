package ai.solace.klang.bitwise
import ai.solace.klang.mem.GlobalHeap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Array-wide bit shifts for limb arrays (little-endian) with optional sticky tracking.
 * These are scalar implementations designed to be allocation-free and branch-light.
 * Future: provide platform-optimized actuals (JVM Vector API / Kotlin/Native Vector128).
 * 
 * @native-bitshift-allowed This is a core BitShift implementation file.
 * Native bitwise operations (shl, shr, ushr, and, or) are permitted here
 * as this file provides the foundation for the BitShift engine.
 */
object ArrayBitShifts {
    data class ShiftResult(val carryOut: Int, val sticky: Boolean)
    private val eng16 get() = BitShiftEngine(BitShiftConfig.defaultMode, 16)
    private val a16 = ArithmeticBitwiseOps(16)
    private val a32 = ArithmeticBitwiseOps.BITS_32
    private const val BASE16: Int = 65536
    // Use vector-friendly 3-pass even for small limb windows like HPC16x8 (8 limbs)
    private const val VECTOR_THRESHOLD: Int = 8
    // Heuristics for parallel fanout
    private const val MIN_PAR_CHUNK: Int = 8192

    // Coroutines (multiplatform)
    var parallelDispatcher: CoroutineDispatcher = Dispatchers.Default

    /**
     * In-place left shift of a little-endian IntArray of 16-bit limbs: a[from .. from+len-1].
     * Each element is treated as 0..0xFFFF. Returns carryOut (upper s bits from the last limb).
     */
    fun shl16LEInPlace(a: IntArray, from: Int, len: Int, s: Int, carryIn: Int = 0): ShiftResult {
        require(s in 0..15) { "s must be in 0..15" }
        if (len <= 0 || s == 0) return ShiftResult(carryIn and 0xFFFF, false)
        val pow2s = ShiftTables16.POW2[s]
        val mask16 = ShiftTables16.MASK16
        val maskLowS = ShiftTables16.LOW_MASK[s]
        if (len >= VECTOR_THRESHOLD) return shl16ThreePass(a, from, len, s, carryIn)
        var carry = carryIn and 0xFFFF
        var sticky = false
        for (i in from until from + len) {
            val cur = (a[i] and mask16).toLong()
            val rs = eng16.leftShift(cur, s)
            val lowShifted = a16.normalize(rs.value).toInt()
            val carryLow = if (maskLowS == 0) 0 else (carry % (maskLowS + 1))
            val combined = BitwiseOps.orArithmetic(lowShifted, carryLow)
            a[i] = (combined % BASE16)
            carry = if (maskLowS == 0) 0 else (rs.carry.toInt() % (maskLowS + 1))
        }
        return ShiftResult(carry and 0xFFFF, sticky)
    }

    /**
     * Parallel 3-pass left shift using coroutines. Works best for large arrays (len >= ~64K).
     * Splits work into chunks; Pass A computes lo/hi locally, Pass C merges with neighbor hi.
     * Returns carryOut (upper s bits of last limb). Sticky is currently false (left shift).
     */
    suspend fun shl16LEInPlaceParallel(
        a: IntArray,
        from: Int,
        len: Int,
        s: Int,
        carryIn: Int = 0,
        parallelism: Int = 0,
    ): ShiftResult {
        require(s in 0..15) { "s must be in 0..15" }
        if (len <= 0 || s == 0) return ShiftResult(carryIn and 0xFFFF, false)
        val pow2s = ShiftTables16.POW2[s]
        val mask16 = ShiftTables16.MASK16
        val pow2_16_minus_s = ShiftTables16.POW2[16 - s]
        val maskLowS = ShiftTables16.LOW_MASK[s]

        val lo = IntArray(len)
        val hi = IntArray(len)

        fun decideChunks(n: Int): Int {
            if (n < MIN_PAR_CHUNK) return 1
            val target = if (parallelism > 0) parallelism else 4
            val maxChunks = target.coerceAtMost(8)
            val bySize = (n / MIN_PAR_CHUNK).coerceAtLeast(1)
            return bySize.coerceAtMost(maxChunks)
        }

        val chunks = decideChunks(len)
        if (chunks == 1) {
            // Fall back to scalar 3-pass (still cache-friendly)
            return shl16ThreePass(a, from, len, s, carryIn)
        }

        val boundaries = IntArray(chunks)

        val chunkSize = (len + chunks - 1) / chunks
        // Pass A+B: compute lo and hi
        coroutineScope {
            for (ck in 0 until chunks) {
                val start = ck * chunkSize
                val end = minOf(len, start + chunkSize)
                if (start >= end) continue
                launch(context = parallelDispatcher) {
                    var i = start
                    while (i < end) {
                        val v = a[from + i] and mask16
                        lo[i] = (v * pow2s) and mask16
                        hi[i] = (v / pow2_16_minus_s) and mask16
                        i++
                    }
                    boundaries[ck] = hi[end - 1]
                }
            }
        }
        // Pass C: merge with neighbor hi (requires one boundary per chunk)
        val carryInNorm = if (maskLowS == 0) 0 else (carryIn % (maskLowS + 1))
        coroutineScope {
            for (ck in 0 until chunks) {
                val start = ck * chunkSize
                val end = minOf(len, start + chunkSize)
                if (start >= end) continue
                launch(context = parallelDispatcher) {
                    var i = start
                    var neighbor = if (i == 0) carryInNorm else {
                        if (i == start) boundaries[ck - 1] else hi[i - 1]
                    }
                    while (i < end) {
                        if (i != start) neighbor = hi[i - 1]
                        val combined = BitwiseOps.orArithmeticGeneral(lo[i], neighbor)
                        a[from + i] = combined % BASE16
                        i++
                    }
                }
            }
        }

        val carryOut = (hi[len - 1] and maskLowS) and 0xFFFF
        return ShiftResult(carryOut, false)
    }

    // 3-pass left shift for better auto-vectorization
    private fun shl16ThreePass(a: IntArray, from: Int, len: Int, s: Int, carryIn: Int): ShiftResult {
        val lo = IntArray(len)
        val hi = IntArray(len)
        val pow2s = ShiftTables16.POW2[s]
        val mask16 = ShiftTables16.MASK16

        // Pass A: lo = (val * 2^s) mod 2^16
        var idx = 0
        while (idx < len) {
            val v = a[from + idx] and mask16
            lo[idx] = (v * pow2s) and mask16
            idx++
        }
        // Pass B: hi = floor(val / 2^(16-s)) (top s bits moved to low)
        idx = 0
        while (idx < len) {
            val v = a[from + idx] and mask16
            hi[idx] = (v / ShiftTables16.POW2[16 - s]) and mask16
            idx++
        }
        // Pass C: combine with neighbor carry; carryIn feeds element 0
        val maskLowS = ShiftTables16.LOW_MASK[s]
        var carry = carryIn and 0xFFFF
        idx = 0
        while (idx < len) {
            val neighbor = if (idx == 0) (if (maskLowS == 0) 0 else (carry % (maskLowS + 1))) else hi[idx - 1]
            val combined = BitwiseOps.orArithmeticGeneral(lo[idx], neighbor)
            a[from + idx] = combined % BASE16
            idx++
        }
        val carryOut = if (len > 0) (hi[len - 1] and maskLowS) else 0
        return ShiftResult(carryOut and 0xFFFF, false)
    }

    /**
     * In-place right shift of a little-endian IntArray of 16-bit limbs: a[from .. from+len-1].
     * Returns carryOut (low s bits shifted out from the first limb) and sticky (OR of all bits shifted out).
     */
    fun rsh16LEInPlace(a: IntArray, from: Int, len: Int, s: Int): ShiftResult {
        require(s in 0..15) { "s must be in 0..15" }
        if (len <= 0 || s == 0) return ShiftResult(0, false)
        val pow2s = ShiftTables16.POW2[s]
        val mask16 = ShiftTables16.MASK16
        if (len >= VECTOR_THRESHOLD) return rsh16ThreePass(a, from, len, s)
        var nextCarry = 0
        var sticky = false
        var carryOut = 0
        for (i in from + len - 1 downTo from) {
            val cur = (a[i] and mask16).toLong()
            val rs = eng16.unsignedRightShift(cur, s)
            val lowPart = a16.normalize(rs.value).toInt()
            // compute nextCarry * 2^(16-s) arithmetically
            val shiftHi = 16 - s
            val highPart = if (shiftHi == 0) nextCarry % 65536 else a32.leftShift(nextCarry.toLong(), shiftHi).toInt()
            val out = BitwiseOps.orArithmeticGeneral(lowPart, highPart)
            val dropped = if (s == 0) 0 else (cur.toInt() % pow2s)
            if (i == from) carryOut = dropped
            sticky = sticky or (dropped != 0)
            a[i] = (out % BASE16)
            nextCarry = dropped
        }
        return ShiftResult(carryOut and 0xFFFF, sticky)
    }

    private fun rsh16ThreePass(a: IntArray, from: Int, len: Int, s: Int): ShiftResult {
        val hi = IntArray(len)
        val dropped = IntArray(len)
        val mask16 = BASE16 - 1
        val pow2s = a32.leftShift(1L, s).toInt()
        val pow2_16_minus_s = a32.leftShift(1L, 16 - s).toInt()

        // Pass A: hi = floor(val / 2^s)
        var idx = 0
        while (idx < len) {
            val v = a[from + idx] and mask16
            hi[idx] = a16.rightShift(v.toLong(), s).toInt() and mask16
            idx++
        }
        // Pass B: dropped = val % 2^s
        idx = 0
        while (idx < len) {
            val v = a[from + idx] and mask16
            dropped[idx] = if (s == 0) 0 else (v % pow2s)
            idx++
        }
        // Pass C: out[i] = hi[i] OR ((i+1<) dropped[i+1] * 2^(16-s))
        var sticky = false
        idx = 0
        while (idx < len) {
            val neighbor = if (idx + 1 < len) ((dropped[idx + 1].toLong() * pow2_16_minus_s) % BASE16).toInt() else 0
            val combined = BitwiseOps.orArithmeticGeneral(hi[idx], neighbor)
            a[from + idx] = combined % BASE16
            sticky = sticky or (dropped[idx] != 0)
            idx++
        }
        val carryOut = if (len > 0) dropped[0] else 0
        return ShiftResult(carryOut and 0xFFFF, sticky)
    }

    /**
     * Parallel 3-pass right shift using coroutines. Returns carryOut (low s bits dropped from limb 0)
     * and sticky (OR of all dropped bits across limbs).
     */
    suspend fun rsh16LEInPlaceParallel(
        a: IntArray,
        from: Int,
        len: Int,
        s: Int,
        parallelism: Int = 0,
    ): ShiftResult {
        require(s in 0..15) { "s must be in 0..15" }
        if (len <= 0 || s == 0) return ShiftResult(0, false)

        fun decideChunks(n: Int): Int {
            if (n < MIN_PAR_CHUNK) return 1
            val target = if (parallelism > 0) parallelism else 4
            val maxChunks = target.coerceAtMost(8)
            val bySize = (n / MIN_PAR_CHUNK).coerceAtLeast(1)
            return bySize.coerceAtMost(maxChunks)
        }
        val chunks = decideChunks(len)
        if (chunks == 1) return rsh16ThreePass(a, from, len, s)

        val hi = IntArray(len)
        val dropped = IntArray(len)
        val mask16 = BASE16 - 1
        val pow2s = ArithmeticBitwiseOps.BITS_32.leftShift(1L, s).toInt()
        val pow2_16_minus_s = ArithmeticBitwiseOps.BITS_32.leftShift(1L, 16 - s).toInt()
        val firstDroppedPerChunk = IntArray(chunks)

        val chunkSize = (len + chunks - 1) / chunks
        // Pass A+B: hi and dropped
        coroutineScope {
            for (ck in 0 until chunks) {
                val start = ck * chunkSize
                val end = minOf(len, start + chunkSize)
                if (start >= end) continue
                launch(context = parallelDispatcher) {
                    var i = start
                    while (i < end) {
                        val v = a[from + i] and mask16
                        hi[i] = ArithmeticBitwiseOps.BITS_32.rightShift(v.toLong(), s).toInt() and mask16
                        dropped[i] = if (s == 0) 0 else (v % pow2s)
                        i++
                    }
                    firstDroppedPerChunk[ck] = dropped[start]
                }
            }
        }
        // Pass C: combine; need dropped[i+1] or firstDropped of next chunk
        coroutineScope {
            for (ck in 0 until chunks) {
                val start = ck * chunkSize
                val end = minOf(len, start + chunkSize)
                if (start >= end) continue
                val nextChunkFirst = if (ck + 1 < chunks) firstDroppedPerChunk[ck + 1] else 0
                launch(context = parallelDispatcher) {
                    var i = start
                    while (i < end) {
                        val neighbor = if (i + 1 < end) {
                            ((dropped[i + 1].toLong() * pow2_16_minus_s) % BASE16).toInt()
                        } else {
                            ((nextChunkFirst.toLong() * pow2_16_minus_s) % BASE16).toInt()
                        }
                        val combined = BitwiseOps.orArithmeticGeneral(hi[i], neighbor)
                        a[from + i] = combined % BASE16
                        i++
                    }
                }
            }
        }
        // Aggregate carry/sticky from dropped[]
        val carryOut = if (len > 0) dropped[0] and 0xFFFF else 0
        var sticky = false
        var i = 0
        while (i < len) { sticky = sticky or (dropped[i] != 0); i++ }
        return ShiftResult(carryOut, sticky)
    }

    /** Word-shift (multiple of 16 bits) left in-place for 16-bit limbs. */
    fun shl16LEWordsInPlace(a: IntArray, from: Int, len: Int, words: Int) {
        if (words <= 0) return
        for (i in (from + len - 1) downTo (from + words)) {
            a[i] = a[i - words] and 0xFFFF
        }
        for (i in from until from + words) a[i] = 0
    }

    /** Word-shift (multiple of 16 bits) right in-place for 16-bit limbs. */
    fun rsh16LEWordsInPlace(a: IntArray, from: Int, len: Int, words: Int) {
        if (words <= 0) return
        for (i in from until from + len - words) {
            a[i] = a[i + words] and 0xFFFF
        }
        for (i in (from + len - words) until (from + len)) a[i] = 0
    }

    // LimbBuffer overloads removed; use heap-address overloads below for packed LE bytes in GlobalHeap.

    // Heap-address overloads (operate directly on packed LE bytes in GlobalHeap)
    fun shl16LEInPlace(baseAddr: Int, fromLimb: Int, len: Int, s: Int, carryIn: Int = 0): ShiftResult {
        require(s in 0..15) { "s must be in 0..15" }
        if (len <= 0 || s == 0) return ShiftResult(carryIn and 0xFFFF, false) // @native-bitshift-allowed
        
        val pow2s = ShiftTables16.POW2[s]
        val mask16 = ShiftTables16.MASK16
        val maskLowS = ShiftTables16.LOW_MASK[s]
        val eng16Local = BitShiftEngine(BitShiftConfig.defaultMode, 16)
        
        var carry = carryIn and 0xFFFF // @native-bitshift-allowed
        var sticky = false
        
        for (i in 0 until len) {
            val off = baseAddr + (fromLimb + i) * 2
            val lowByte = GlobalHeap.lbu(off)
            val highByte = GlobalHeap.lbu(off + 1)
            // Compose 16-bit value from bytes (little-endian) using BitShiftEngine
            val highShifted = eng16Local.byteShiftLeft(highByte.toLong(), 1)
            val orResult = eng16Local.bitwiseOr(lowByte.toLong(), highShifted.value)
            val cur = eng16Local.bitwiseAnd(orResult, mask16.toLong()).toInt()
            
            val rs = eng16.leftShift(cur.toLong(), s)
            val lowShifted = a16.normalize(rs.value).toInt()
            val carryLow = if (maskLowS == 0) 0 else (carry % (maskLowS + 1))
            val combined = BitwiseOps.orArithmetic(lowShifted, carryLow)
            val result = (combined % BASE16)
            
            // Decompose back to bytes (little-endian) using BitShiftEngine
            val resultByte0 = eng16Local.bitwiseAnd(result.toLong(), 0xFF)
            GlobalHeap.sb(off, resultByte0.toByte())
            val highByteResult = eng16Local.byteShiftRight(result.toLong(), 1)
            val resultByte1 = eng16Local.bitwiseAnd(highByteResult.value, 0xFF)
            GlobalHeap.sb(off + 1, resultByte1.toByte())
            
            // Compute carry as top s bits of original value (shifted out)
            carry = if (maskLowS == 0) 0 else ((cur / ShiftTables16.POW2[16 - s]) % (maskLowS + 1))
        }
        return ShiftResult(carry and 0xFFFF, sticky) // @native-bitshift-allowed
    }

    fun rsh16LEInPlace(baseAddr: Int, fromLimb: Int, len: Int, s: Int): ShiftResult {
        require(s in 0..15) { "s must be in 0..15" }
        if (len <= 0 || s == 0) return ShiftResult(0, false)
        
        val pow2s = ShiftTables16.POW2[s]
        val mask16 = ShiftTables16.MASK16
        val eng16Local = BitShiftEngine(BitShiftConfig.defaultMode, 16)
        
        var nextCarry = 0
        var sticky = false
        var carryOut = 0
        
        for (i in len - 1 downTo 0) {
            val off = baseAddr + (fromLimb + i) * 2
            val lowByte = GlobalHeap.lbu(off)
            val highByte = GlobalHeap.lbu(off + 1)
            // Compose 16-bit value from bytes (little-endian) using BitShiftEngine
            val highShifted = eng16Local.byteShiftLeft(highByte.toLong(), 1)
            val orResult = eng16Local.bitwiseOr(lowByte.toLong(), highShifted.value)
            val cur = eng16Local.bitwiseAnd(orResult, mask16.toLong()).toInt()
            
            val rs = eng16.unsignedRightShift(cur.toLong(), s)
            val lowPart = a16.normalize(rs.value).toInt()
            // compute nextCarry * 2^(16-s) arithmetically
            val shiftHi = 16 - s
            val highPart = if (shiftHi == 0) nextCarry % 65536 else a32.leftShift(nextCarry.toLong(), shiftHi).toInt()
            val out = BitwiseOps.orArithmeticGeneral(lowPart, highPart)
            val dropped = if (s == 0) 0 else (cur % pow2s)
            if (i == 0) carryOut = dropped
            sticky = sticky or (dropped != 0) // @native-bitshift-allowed
            val result = (out % BASE16)
            
            // Decompose back to bytes (little-endian) using BitShiftEngine
            val resultByte0 = eng16Local.bitwiseAnd(result.toLong(), 0xFF)
            GlobalHeap.sb(off, resultByte0.toByte())
            val highByteResult = eng16Local.byteShiftRight(result.toLong(), 1)
            val resultByte1 = eng16Local.bitwiseAnd(highByteResult.value, 0xFF)
            GlobalHeap.sb(off + 1, resultByte1.toByte())
            
            nextCarry = dropped
        }
        return ShiftResult(carryOut and 0xFFFF, sticky) // @native-bitshift-allowed
    }
}
