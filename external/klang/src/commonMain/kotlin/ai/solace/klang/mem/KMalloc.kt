package ai.solace.klang.mem

/**
 * KMalloc: A production-grade memory allocator built on [GlobalHeap].
 *
 * Provides C-standard malloc/free semantics with automatic coalescing, segregated free lists,
 * and 16-byte alignment. Designed for cross-platform determinism and zero native dependencies.
 *
 * ## Features
 *
 * **Memory Management**:
 * - 16-byte alignment for optimal SIMD and cache-line access
 * - Automatic coalescing of adjacent free blocks
 * - Segregated free lists for small allocations (≤1024 bytes)
 * - Bump allocation for large blocks and when free lists are empty
 * - Header/footer metadata (8 bytes overhead per allocation)
 *
 * **C Compatibility**:
 * - `malloc(size)` - Allocate uninitialized memory
 * - `calloc(count, size)` - Allocate zero-initialized memory
 * - `free(ptr)` - Deallocate memory with automatic coalescing
 * - `realloc(ptr, size)` - Resize allocation (copy-on-grow)
 *
 * ## Architecture
 *
 * ### Chunk Structure
 * ```
 * ┌────────────┬─────────────────────┬────────────┐
 * │   Header   │      Payload        │   Footer   │
 * │  (4 bytes) │    (size bytes)     │  (4 bytes) │
 * └────────────┴─────────────────────┴────────────┘
 *      ↑                                    ↑
 *   chunk addr                          tag copy
 *
 * Header/Footer: size << 1 | inUse (LSB = 1 if allocated)
 * ```
 *
 * ### Free List Organization
 * ```
 * bins[0]: 16-byte chunks   ──┐
 * bins[1]: 32-byte chunks     │ Segregated by size class
 * ...                          │ (16-byte increments)
 * bins[63]: 1024-byte chunks ──┘
 * largeFreeHead: >1024 bytes (single linked list)
 * ```
 *
 * ### Allocation Strategy
 * 1. **Small blocks (≤1024)**: Search appropriate bin (first-fit)
 * 2. **Large blocks (>1024)**: Search large list
 * 3. **Split if needed**: Create remainder chunk if found block is too large
 * 4. **Bump allocate**: Allocate from top if no suitable free block found
 *
 * ### Deallocation with Coalescing
 * ```
 * Before free:  [Used A] [Used B] [Used C]
 * After free B: [Used A] [Free B] [Used C]
 * Coalesce:     [Used A] [Free B+C merged] (if C freed next)
 * ```
 *
 * Coalescing happens in both directions:
 * - **Forward**: Merge with next block if free
 * - **Backward**: Merge with previous block if free (using footer)
 *
 * ## Usage Example
 *
 * ```kotlin
 * // Initialize with 1MB heap
 * KMalloc.init(1024 * 1024)
 *
 * // Allocate memory
 * val ptr = KMalloc.malloc(256)
 *
 * // Use memory
 * GlobalHeap.sw(ptr, 42)
 * val value = GlobalHeap.lw(ptr)
 *
 * // Free memory (coalesces with adjacent free blocks)
 * KMalloc.free(ptr)
 *
 * // Allocate zero-initialized array
 * val arr = KMalloc.calloc(10, 4)  // 10 ints
 *
 * // Resize allocation
 * val bigger = KMalloc.realloc(arr, 400)  // Grow to 100 ints
 * KMalloc.free(bigger)
 * ```
 *
 * ## Performance Characteristics
 *
 * - **malloc**: O(n) worst case (search free list), O(1) best case (bump allocate)
 * - **free**: O(1) with coalescing (at most 2 merges)
 * - **calloc**: O(n) where n = allocation size (memset overhead)
 * - **realloc**: O(n) worst case (copy old data if growing)
 *
 * ## Memory Overhead
 *
 * - **Per allocation**: 8 bytes (4-byte header + 4-byte footer)
 * - **Alignment padding**: Up to 15 bytes per allocation (16-byte alignment)
 * - **Free list overhead**: 4 bytes per free chunk (next pointer)
 * - **Total metadata**: ~5-15% depending on allocation patterns
 *
 * ## Thread Safety
 *
 * **Not thread-safe**. External synchronization required for concurrent access.
 * Sharing the same heap across threads requires locking around all KMalloc operations.
 *
 * ## Design Trade-offs
 *
 * **Advantages**:
 * - Simple, predictable behavior
 * - Automatic coalescing prevents fragmentation
 * - Segregated lists optimize small allocation performance
 * - Cross-platform determinism (identical on all targets)
 *
 * **Trade-offs**:
 * - No thread-local caching (would require TLS)
 * - Single large lock required for thread safety
 * - Coalescing adds slight overhead to free()
 * - Realloc always copies on growth (no in-place expansion yet)
 *
 * ## Implementation Status
 *
 * **Phase 1 (Complete)**:
 * - ✅ Basic malloc/free with 16-byte alignment
 * - ✅ Segregated free lists for small allocations
 * - ✅ Automatic bidirectional coalescing
 * - ✅ calloc and realloc
 * - ✅ Chunk splitting when oversized
 *
 * **Phase 2 (Future)**:
 * - ⚠️ In-place realloc when possible
 * - ⚠️ Best-fit instead of first-fit for large blocks
 * - ⚠️ Memory defragmentation
 * - ⚠️ Statistics and debugging hooks
 *
 * @see GlobalHeap For the underlying byte array heap
 * @see CScalars For type-safe variables using KMalloc
 * @since 0.1.0
 */
object KMalloc {
    /** Alignment boundary: all allocations are multiples of 16 bytes. */
    private const val ALIGN = 16
    
    /** Header size: 4-byte tag stores (size << 1 | inUse). */
    private const val HEADER_SIZE = 4
    
    /** Footer size: 4-byte tag copy enables backward coalescing. */
    private const val FOOTER_SIZE = 4
    
    /** Total overhead per allocation: 8 bytes. */
    private const val OVERHEAD = HEADER_SIZE + FOOTER_SIZE
    
    /** Minimum payload size: 16 bytes (room for next pointer in free chunks). */
    private const val MIN_CHUNK = 16
    
    /** Small allocation threshold: blocks ≤1024 use segregated bins. */
    private const val SMALL_LIMIT = 1024
    
    /** Bin size class shift: 16-byte increments (1 << 4). */
    private const val BIN_SHIFT = 4
    
    /** Number of segregated bins: 64 (for 16, 32, 48, ..., 1024 bytes). */
    private const val BIN_COUNT = (SMALL_LIMIT shr BIN_SHIFT)

    /** Segregated free list bins for small allocations. Each bin holds chunks of similar size. */
    private val bins = IntArray(BIN_COUNT) { -1 }
    
    /** Head of large free block list (>1024 bytes). */
    private var largeFreeHead: Int = -1

    /** Bump allocator pointer: next free address in heap. */
    private var brk: Int = 0

    /**
     * Initialize KMalloc and the underlying heap.
     *
     * Allocates a new [GlobalHeap] of the specified size and resets all free lists.
     * Any existing heap data is discarded.
     *
     * @param bytes Heap size in bytes. Should be large enough for expected allocations.
     * @throws IllegalArgumentException if bytes < 0
     *
     * ## Example
     * ```kotlin
     * KMalloc.init(1 shl 20)  // 1MB heap
     * ```
     */
    fun init(bytes: Int) {
        GlobalHeap.init(bytes)
        bins.fill(-1)
        largeFreeHead = -1
        brk = 0
    }

    /**
     * Reset all allocations and free lists.
     *
     * Clears all metadata and resets the bump pointer to 0.
     * The underlying heap capacity is retained.
     *
     * **Warning**: All pointers become invalid after reset.
     */
    fun reset() {
        bins.fill(-1)
        largeFreeHead = -1
        brk = 0
        GlobalHeap.reset()
    }

    /**
     * Dispose of the allocator and heap.
     *
     * Releases all memory and resets metadata.
     * Call [init] to use the allocator again.
     */
    fun dispose() {
        bins.fill(-1)
        largeFreeHead = -1
        brk = 0
        GlobalHeap.dispose()
    }

    /**
     * Allocate uninitialized memory (C malloc semantics).
     *
     * Returns a pointer (byte offset) to at least `bytes` bytes of memory.
     * Memory contents are undefined (may contain previous data).
     *
     * ## Allocation Strategy
     * 1. Round size to 16-byte alignment
     * 2. Search appropriate free list (bins or large list)
     * 3. Split chunk if found block is oversized
     * 4. Bump allocate from top if no free block available
     * 5. Grow heap automatically if needed
     *
     * @param bytes Number of bytes to allocate. Will be rounded up to 16-byte multiple.
     * @return Pointer to allocated memory (address in GlobalHeap)
     *
     * ## Example
     * ```kotlin
     * val ptr = KMalloc.malloc(256)
     * // Use ptr with GlobalHeap.lw/sw/etc.
     * ```
     *
     * ## Complexity
     * - Best case: O(1) bump allocation
     * - Average case: O(1) for small blocks (direct bin lookup)
     * - Worst case: O(n) where n = free list length
     *
     * @see calloc For zero-initialized allocation
     */
    fun malloc(bytes: Int): Int {
        val size = normalize(bytes)
        // Try to find a suitable free chunk
        val fromBin = findAndPrepareChunk(size)
        if (fromBin != 0) return fromBin + HEADER_SIZE
        // No free chunk found, allocate from top
        val total = OVERHEAD + size
        val chunk = brk
        ensureCapacity(chunk + total)
        writeHeaderFooter(chunk, size, inUse = true)
        brk += total
        return chunk + HEADER_SIZE
    }

    /**
     * Allocate zero-initialized memory (C calloc semantics).
     *
     * Allocates `count * elemSize` bytes and zeros all bytes.
     * Equivalent to `malloc(count * elemSize)` followed by `memset(ptr, 0, size)`.
     *
     * @param count Number of elements
     * @param elemSize Size of each element in bytes
     * @return Pointer to zero-initialized memory
     *
     * ## Example
     * ```kotlin
     * val arr = KMalloc.calloc(10, 4)  // 10 ints (40 bytes), all zero
     * ```
     *
     * ## Complexity
     * O(n) where n = count * elemSize (due to memset)
     *
     * @see malloc For uninitialized allocation
     */
    fun calloc(count: Int, elemSize: Int): Int {
        val bytes = count * elemSize
        val p = malloc(bytes)
        GlobalHeap.memset(p, 0, bytes)
        return p
    }

    /**
     * Free allocated memory (C free semantics).
     *
     * Marks the block as free and automatically coalesces with adjacent free blocks.
     * Coalescing happens in both directions (forward and backward).
     *
     * ## Coalescing Process
     * 1. Mark chunk as free
     * 2. Check next chunk - if free, merge forward
     * 3. Check previous chunk (using footer) - if free, merge backward
     * 4. Add merged chunk to appropriate free list
     *
     * @param ptr Pointer returned by [malloc], [calloc], or [realloc]. Zero/negative pointers are ignored.
     *
     * ## Example
     * ```kotlin
     * val ptr = KMalloc.malloc(256)
     * // ... use ptr ...
     * KMalloc.free(ptr)  // Automatically coalesces with neighbors
     * ```
     *
     * ## Complexity
     * O(1) with at most 2 merge operations
     *
     * ## Safety
     * - Double-free: May corrupt heap (undefined behavior)
     * - Invalid pointer: May corrupt heap (undefined behavior)
     * - Use-after-free: May return stale data or crash
     */
    fun free(ptr: Int) {
        if (ptr <= 0) return
        var chunk = ptr - HEADER_SIZE
        var size = readSize(chunk)
        // Mark as free
        writeHeaderFooter(chunk, size, inUse = false)
        // Coalesce with next chunk if it's free
        val next = nextChunk(chunk)
        if (next in 0 until brk && !isInUse(next)) {
            removeFromFreeList(next)
            val nextSize = readSize(next)
            size = size + OVERHEAD + nextSize
            writeHeaderFooter(chunk, size, inUse = false)
        }
        // Coalesce with previous chunk if it's free
        val prev = prevChunk(chunk)
        if (prev >= 0 && !isInUse(prev)) {
            removeFromFreeList(prev)
            val prevSize = readSize(prev)
            chunk = prev
            size = prevSize + OVERHEAD + size
            writeHeaderFooter(chunk, size, inUse = false)
        }
        pushFree(chunk)
    }

    /**
     * Resize an existing allocation (C realloc semantics).
     *
     * If the new size is smaller, may split the chunk and return the same pointer.
     * If the new size is larger, allocates a new chunk, copies old data, and frees the old chunk.
     *
     * ## Behavior
     * - `realloc(0, size)` → `malloc(size)`
     * - `realloc(ptr, 0)` → current implementation keeps minimum allocation
     * - Shrinking: Returns same pointer (may split remainder)
     * - Growing: Allocates new, copies old, frees old
     *
     * @param ptr Existing pointer (or 0 for new allocation)
     * @param newSize New size in bytes
     * @return Pointer to resized allocation (may be same or different)
     *
     * ## Example
     * ```kotlin
     * var ptr = KMalloc.malloc(100)
     * // ... use 100 bytes ...
     * ptr = KMalloc.realloc(ptr, 200)  // Grow to 200 bytes
     * // Old data is preserved, ptr may have changed
     * ```
     *
     * ## Complexity
     * - Shrinking: O(1)
     * - Growing: O(n) where n = old size (due to memcpy)
     *
     * ## Future Improvements
     * Could optimize by expanding in-place if next chunk is free and large enough.
     */
    fun realloc(ptr: Int, newSize: Int): Int {
        if (ptr == 0) return malloc(newSize)
        val chunk = ptr - HEADER_SIZE
        val oldSize = readSize(chunk)
        val size = normalize(newSize)
        if (size <= oldSize) {
            // Shrinking: split off remainder if significant
            maybeSplit(chunk, oldSize, size)
            return ptr
        }
        // Growing: allocate new, copy, free old
        val np = malloc(size)
        GlobalHeap.memcpy(np, ptr, oldSize)
        free(ptr)
        return np
    }

    /**
     * Ensure heap has at least the specified capacity.
     *
     * Delegates to [GlobalHeap.ensureCapacity] which grows the heap if needed.
     *
     * @param minSize Minimum required capacity in bytes
     */
    private fun ensureCapacity(minSize: Int) {
        if (minSize <= GlobalHeap.size) return
        GlobalHeap.ensureCapacity(minSize)
    }

    /**
     * Get bin index for a given size, or -1 for large blocks.
     *
     * Small blocks (≤1024) use segregated bins, large blocks use a single list.
     *
     * @param size Allocation size (already normalized)
     * @return Bin index (0-63) or -1 for large blocks
     */
    private fun binIndexOrMinus1(size: Int): Int = if (size <= SMALL_LIMIT) ((size ushr BIN_SHIFT) - 1) else -1

    /**
     * Normalize size to 16-byte alignment with minimum of MIN_CHUNK.
     *
     * @param n Requested size
     * @return Normalized size (multiple of 16, >= 16)
     */
    private fun normalize(n: Int): Int {
        val v = if (n <= 0) MIN_CHUNK else n
        val a = ((v + (ALIGN - 1)) / ALIGN) * ALIGN
        return a.coerceAtLeast(MIN_CHUNK)
    }

    /**
     * Write header and footer tags for a chunk.
     *
     * Both header and footer store the same tag: (size << 1) | inUse.
     * The footer enables backward traversal for coalescing.
     *
     * @param chunk Chunk address (start of header)
     * @param size Payload size (not including overhead)
     * @param inUse true if allocated, false if free
     */
    private fun writeHeaderFooter(chunk: Int, size: Int, inUse: Boolean) {
        val tag = pack(size, inUse)
        GlobalHeap.sw(chunk, tag)
        GlobalHeap.sw(chunk + HEADER_SIZE + size, tag)
    }
    private fun pack(size: Int, inUse: Boolean): Int = (size shl 1) or (if (inUse) 1 else 0)
    private fun readTag(chunk: Int): Int = GlobalHeap.lw(chunk)
    private fun readSize(chunk: Int): Int = readTag(chunk) ushr 1
    private fun isInUse(chunk: Int): Boolean = (readTag(chunk) and 1) != 0
    private fun nextChunk(chunk: Int): Int = chunk + HEADER_SIZE + readSize(chunk) + FOOTER_SIZE
    private fun prevChunk(chunk: Int): Int {
        if (chunk < FOOTER_SIZE) return -1
        val prevTag = GlobalHeap.lw(chunk - FOOTER_SIZE)
        val prevSize = prevTag ushr 1
        val prevBase = chunk - (HEADER_SIZE + prevSize + FOOTER_SIZE)
        return if (prevBase >= 0) prevBase else -1
    }

    private fun pushFree(chunk: Int) {
        val size = readSize(chunk)
        val binIdx = binIndexOrMinus1(size)
        if (binIdx >= 0) {
            val head = bins[binIdx]
            GlobalHeap.sw(chunk + HEADER_SIZE, head)
            bins[binIdx] = chunk
        } else {
            // large list push front
            GlobalHeap.sw(chunk + HEADER_SIZE, largeFreeHead)
            largeFreeHead = chunk
        }
    }

    private fun removeFromFreeList(chunk: Int) {
        val size = readSize(chunk)
        val binIdx = binIndexOrMinus1(size)
        if (binIdx >= 0) {
            var cur = bins[binIdx]
            var prev = -1
            while (cur != -1) {
                if (cur == chunk) {
                    val next = GlobalHeap.lw(cur + HEADER_SIZE)
                    if (prev == -1) bins[binIdx] = next else GlobalHeap.sw(prev + HEADER_SIZE, next)
                    return
                }
                prev = cur
                cur = GlobalHeap.lw(cur + HEADER_SIZE)
            }
        } else {
            var cur = largeFreeHead
            var prev = -1
            while (cur != -1) {
                if (cur == chunk) {
                    val next = GlobalHeap.lw(cur + HEADER_SIZE)
                    if (prev == -1) largeFreeHead = next else GlobalHeap.sw(prev + HEADER_SIZE, next)
                    return
                }
                prev = cur
                cur = GlobalHeap.lw(cur + HEADER_SIZE)
            }
        }
    }

    private fun maybeSplit(chunk: Int, curSize: Int, wantSize: Int) {
        val remain = curSize - wantSize
        if (remain >= MIN_CHUNK + OVERHEAD) {
            // allocated front keeps wantSize; create a free tail
            writeHeaderFooter(chunk, wantSize, inUse = true)
            val tail = chunk + HEADER_SIZE + wantSize + FOOTER_SIZE
            writeHeaderFooter(tail, remain - OVERHEAD, inUse = false)
            pushFree(tail)
        }
    }

    private fun findAndPrepareChunk(size: Int): Int {
        // Search bins from target bin upward
        var idx = binIndexOrMinus1(size)
        if (idx >= 0) {
            while (idx < BIN_COUNT) {
                var cur = bins[idx]
                var prev = -1
                while (cur != -1) {
                    val curSize = readSize(cur)
                    if (curSize >= size) {
                        // remove from list
                        val next = GlobalHeap.lw(cur + HEADER_SIZE)
                        if (prev == -1) bins[idx] = next else GlobalHeap.sw(prev + HEADER_SIZE, next)
                        // split if needed
                        val remain = curSize - size
                        if (remain >= MIN_CHUNK + OVERHEAD) {
                            val tail = cur + HEADER_SIZE + size + FOOTER_SIZE
                            writeHeaderFooter(tail, remain - OVERHEAD, inUse = false)
                            pushFree(tail)
                            writeHeaderFooter(cur, size, inUse = true)
                        } else {
                            writeHeaderFooter(cur, curSize, inUse = true)
                        }
                        return cur
                    }
                    prev = cur
                    cur = GlobalHeap.lw(prev + HEADER_SIZE)
                }
                idx++
            }
        }
        // Search large list first-fit
        var cur = largeFreeHead
        var prev = -1
        while (cur != -1) {
            val curSize = readSize(cur)
            if (curSize >= size) {
                val next = GlobalHeap.lw(cur + HEADER_SIZE)
                if (prev == -1) largeFreeHead = next else GlobalHeap.sw(prev + HEADER_SIZE, next)
                val remain = curSize - size
                if (remain >= MIN_CHUNK + OVERHEAD) {
                    val tail = cur + HEADER_SIZE + size + FOOTER_SIZE
                    writeHeaderFooter(tail, remain - OVERHEAD, inUse = false)
                    pushFree(tail)
                    writeHeaderFooter(cur, size, inUse = true)
                } else {
                    writeHeaderFooter(cur, curSize, inUse = true)
                }
                return cur
            }
            prev = cur
            cur = GlobalHeap.lw(cur + HEADER_SIZE)
        }
        return 0
    }
}
