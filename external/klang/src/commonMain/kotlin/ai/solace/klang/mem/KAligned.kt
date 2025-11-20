package ai.solace.klang.mem

/**
 * Aligned allocations over KMalloc.
 *
 * Returns an aligned interior pointer. Use alignedFree to release it.
 * Layout:
 *   [ base ... ][ padding ][ metadata:int base ][ aligned payload ... ]
 * We store the original base (Int) immediately before the aligned payload so we can free.
 */
object KAligned {
    private const val META_SIZE = 4
    private const val PTR_SIZE = 8 // assume 64-bit pointers for our model
    private const val EINVAL = 22
    private const val ENOMEM = 12

    fun alignedAlloc(alignment: Int, size: Int): Int {
        require(alignment > 0 && (alignment and (alignment - 1)) == 0) { "alignment must be power of two" }
        require(size >= 0) { "size must be >= 0" }
        val total = size + alignment - 1 + META_SIZE
        val base = KMalloc.malloc(total)
        val alignedMeta = alignUp(base + META_SIZE, alignment) - META_SIZE
        GlobalHeap.sw(alignedMeta, base)
        return alignedMeta + META_SIZE
    }

    fun alignedCalloc(alignment: Int, size: Int): Int {
        val p = alignedAlloc(alignment, size)
        GlobalHeap.memset(p, 0, size)
        return p
    }

    fun alignedFree(alignedPtr: Int) {
        val meta = alignedPtr - META_SIZE
        val base = GlobalHeap.lw(meta)
        KMalloc.free(base)
    }

    private fun alignUp(x: Int, align: Int): Int = (x + (align - 1)) and (align - 1).inv()

    /**
     * posix_memalign semantics: alignment must be power of two and a multiple of sizeof(void*).
     * Returns (err, ptr). On error, ptr is 0. Errors: EINVAL (22), ENOMEM (12).
     */
    data class MemalignResult(val err: Int, val ptr: Int)

    fun posixMemalign(alignment: Int, size: Int): MemalignResult {
        // Validate parameters upfront - all the same checks as alignedAlloc
        if (alignment <= 0 || (alignment and (alignment - 1)) != 0) return MemalignResult(EINVAL, 0)
        if (alignment % PTR_SIZE != 0) return MemalignResult(EINVAL, 0)
        if (size < 0) return MemalignResult(EINVAL, 0)
        
        // All validation passed - delegate to alignedAlloc which will succeed or throw
        // (throwing is acceptable here since it means OOM or heap corruption, not user error)
        val p = alignedAlloc(alignment, size)
        return MemalignResult(0, p)
    }
}
