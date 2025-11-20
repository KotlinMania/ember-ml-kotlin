package ai.solace.klang.mem

import ai.solace.klang.bitwise.BitShiftEngine
import ai.solace.klang.bitwise.BitShiftMode

/**
 * CLib: Standard C library string and memory functions for [GlobalHeap].
 *
 * Provides cross-platform implementations of essential C library functions
 * (string.h and stdlib.h) that operate on [GlobalHeap] memory using Int pointers.
 * All functions are deterministic and platform-independent.
 *
 * ## Why CLib?
 *
 * **Use Cases**:
 * - **C interop**: Port C code that uses string.h functions
 * - **Zero-copy strings**: Work with heap strings without Kotlin String allocation
 * - **Binary protocols**: Parse/format binary data with C semantics
 * - **Deterministic behavior**: Cross-platform string operations
 * - **Memory operations**: Low-level memcpy/memset/memcmp without native calls
 *
 * **Advantages**:
 * - Pure Kotlin (no native dependencies)
 * - Cross-platform determinism
 * - Works with heap addresses (Int pointers)
 * - Compatible with C semantics
 * - Zero allocation for most operations
 *
 * ## Pointer Convention
 *
 * All addresses are [Int] byte offsets into [GlobalHeap]:
 * - **Valid pointer**: 0 < addr < GlobalHeap.size
 * - **Null pointer**: 0 (like C's NULL)
 * - **Return 0**: Indicates null/not found (like C)
 *
 * ## String Format
 *
 * C-style null-terminated strings:
 * ```
 * Heap: ['H', 'e', 'l', 'l', 'o', '\0', ...]
 *        ^addr                    ^end
 * ```
 *
 * ## Usage Example
 *
 * ```kotlin
 * KMalloc.init(1024 * 1024)
 *
 * // Create strings in heap
 * val hello = KMalloc.malloc(10)
 * CLib.strcpy(hello, "Hello".toByteArray())
 *
 * val world = KMalloc.malloc(10)
 * CLib.strcpy(world, "World".toByteArray())
 *
 * // String operations
 * val len = CLib.strlen(hello)        // 5
 * val cmp = CLib.strcmp(hello, world) // < 0 (H < W)
 *
 * // Memory operations
 * val pos = CLib.strchr(hello, 'l'.code)  // Finds first 'l'
 * val found = CLib.memchr(hello, 'e'.code, 5)  // Search for 'e'
 * ```
 *
 * ## Function Categories
 *
 * ### String Length
 * - [strlen]: Length of null-terminated string
 * - [strnlen]: Length with maximum limit
 *
 * ### String Comparison
 * - [strcmp]: Compare two strings
 * - [strncmp]: Compare up to n characters
 *
 * ### String Copy
 * - [strcpy]: Copy null-terminated string
 * - [strncpy]: Copy up to n characters (pad with NUL)
 *
 * ### String Search
 * - [strchr]: Find character in string
 * - [memchr]: Find byte in memory region
 *
 * ### Memory Comparison
 * - [memcmp]: Compare two memory regions
 *
 * ## Performance
 *
 * All operations delegate to [FastStringMem] where possible for optimal performance:
 * - **strlen**: O(n) - scans until NUL
 * - **strcmp**: O(min(n, m)) - early exit on difference
 * - **strcpy**: O(n) - copies until NUL
 * - **memcmp**: O(n) - compares byte by byte
 *
 * ## Thread Safety
 *
 * Functions are stateless and thread-safe, but concurrent access
 * to the same heap memory requires external synchronization.
 *
 * ## C Compatibility
 *
 * Behavior matches C99 semantics:
 * ```c
 * // C code
 * char *s1 = "Hello";
 * char *s2 = "World";
 * int len = strlen(s1);
 * int cmp = strcmp(s1, s2);
 * ```
 *
 * ```kotlin
 * // Equivalent KLang code
 * val s1 = createHeapString("Hello")
 * val s2 = createHeapString("World")
 * val len = CLib.strlen(s1)
 * val cmp = CLib.strcmp(s1, s2)
 * ```
 *
 * ## Related Functions
 *
 * Additional string/memory functions:
 * - [GlobalHeap.memset]: Fill memory with byte value
 * - [GlobalHeap.memcpy]: Copy memory region
 * - [FastStringMem]: Optimized low-level implementations
 *
 * @see GlobalHeap For underlying memory operations
 * @see FastStringMem For optimized implementations
 * @see KMalloc For string memory allocation
 * @since 0.1.0
 */
object CLib {
    // Use 8-bit shifter for byte operations
    private val shifter = BitShiftEngine(BitShiftMode.NATIVE, 8)
    
    /**
     * Calculate length of null-terminated string.
     *
     * Scans from [s] until a NUL byte (0) is found.
     * Delegates to [FastStringMem.strlen] for optimized performance.
     *
     * @param s Pointer to null-terminated string
     * @return Number of bytes before NUL (0 if s is 0)
     *
     * ## Example
     * ```kotlin
     * val str = createHeapString("Hello")
     * val len = CLib.strlen(str)  // 5
     * ```
     *
     * ## C Equivalent
     * ```c
     * size_t strlen(const char *s);
     * ```
     *
     * ## Complexity
     * O(n) where n is the string length
     */
    fun strlen(s: Int): Int = FastStringMem.strlen(s)
    
    /**
     * Calculate length of string with maximum limit.
     *
     * Scans from [s] for at most [n] bytes until NUL is found.
     * Returns [n] if no NUL found within the limit.
     *
     * @param s Pointer to string (may not be null-terminated)
     * @param n Maximum bytes to scan
     * @return Number of bytes before NUL or [n], whichever is smaller
     *
     * ## Example
     * ```kotlin
     * val str = createHeapString("Hello")
     * val len1 = CLib.strnlen(str, 10)  // 5 (NUL found)
     * val len2 = CLib.strnlen(str, 3)   // 3 (limit reached)
     * ```
     *
     * ## C Equivalent
     * ```c
     * size_t strnlen(const char *s, size_t maxlen);
     * ```
     *
     * ## Complexity
     * O(min(n, strlen(s)))
     */
    fun strnlen(s: Int, n: Int): Int {
        var i = 0
        while (i < n) {
            if (GlobalHeap.lb(s + i).toInt() == 0) break
            i++
        }
        return i
    }

    /**
     * Compare two null-terminated strings lexicographically.
     *
     * Delegates to [FastStringMem.strcmp] for optimized performance.
     *
     * @param a Pointer to first string
     * @param b Pointer to second string
     * @return Negative if a < b, zero if a == b, positive if a > b
     *
     * ## Example
     * ```kotlin
     * val s1 = createHeapString("Apple")
     * val s2 = createHeapString("Banana")
     * val cmp = CLib.strcmp(s1, s2)  // < 0 (Apple < Banana)
     * ```
     *
     * ## C Equivalent
     * ```c
     * int strcmp(const char *s1, const char *s2);
     * ```
     *
     * ## Complexity
     * O(min(len(a), len(b)))
     */
    fun strcmp(a: Int, b: Int): Int = FastStringMem.strcmp(a, b)

    /**
     * Copy null-terminated string from [src] to [dst].
     *
     * Copies bytes including the terminating NUL.
     * **Warning**: No bounds checking - [dst] must have enough space.
     *
     * @param dst Destination pointer (must have space for src + NUL)
     * @param src Source pointer (null-terminated string)
     * @return [dst] (for chaining)
     *
     * ## Example
     * ```kotlin
     * val src = createHeapString("Hello")
     * val dst = KMalloc.malloc(10)
     * CLib.strcpy(dst, src)
     * println(readHeapString(dst))  // "Hello"
     * ```
     *
     * ## C Equivalent
     * ```c
     * char *strcpy(char *dst, const char *src);
     * ```
     *
     * ## Safety
     * Buffer overflow if dst is too small. Use [strncpy] for bounded copy.
     *
     * ## Complexity
     * O(n) where n = strlen(src)
     */
    fun strcpy(dst: Int, src: Int): Int {
        var i = 0
        while (true) {
            val b = GlobalHeap.lbu(src + i)
            GlobalHeap.sb(dst + i, shifter.bitwiseAnd(b.toLong(), 0xFF).toByte())
            if (b == 0) return dst
            i++
        }
    }

    /**
     * Copy at most [n] characters from [src] to [dst].
     *
     * Copies until NUL or [n] bytes, whichever comes first.
     * If [src] is shorter than [n], pads [dst] with NUL bytes to reach [n].
     *
     * @param dst Destination pointer
     * @param src Source pointer
     * @param n Maximum bytes to copy
     * @return [dst] (for chaining)
     *
     * ## Example
     * ```kotlin
     * val src = createHeapString("Hi")
     * val dst = KMalloc.malloc(10)
     * CLib.strncpy(dst, src, 10)  // Copies "Hi\0\0\0\0\0\0\0\0"
     * ```
     *
     * ## C Equivalent
     * ```c
     * char *strncpy(char *dst, const char *src, size_t n);
     * ```
     *
     * ## Note
     * Unlike strcpy, result may NOT be null-terminated if src length >= n.
     *
     * ## Complexity
     * O(n)
     */
    fun strncpy(dst: Int, src: Int, n: Int): Int {
        var i = 0
        while (i < n) {
            val b = GlobalHeap.lbu(src + i)
            GlobalHeap.sb(dst + i, shifter.bitwiseAnd(b.toLong(), 0xFF).toByte())
            i++
            if (b == 0) {
                // pad the rest with NULs
                while (i < n) { GlobalHeap.sb(dst + i, 0); i++ }
                break
            }
        }
        return dst
    }

    /**
     * Compare at most [n] characters from two strings.
     *
     * Stops at NUL, difference, or after [n] bytes.
     *
     * @param a Pointer to first string
     * @param b Pointer to second string
     * @param n Maximum bytes to compare
     * @return Negative if a < b, zero if a == b, positive if a > b
     *
     * ## Example
     * ```kotlin
     * val s1 = createHeapString("Hello")
     * val s2 = createHeapString("Help")
     * val cmp = CLib.strncmp(s1, s2, 3)  // 0 ("Hel" == "Hel")
     * ```
     *
     * ## C Equivalent
     * ```c
     * int strncmp(const char *s1, const char *s2, size_t n);
     * ```
     *
     * ## Complexity
     * O(min(n, min(len(a), len(b))))
     */
    fun strncmp(a: Int, b: Int, n: Int): Int {
        var i = 0
        if (n <= 0) return 0
        while (i < n) {
            val ca = GlobalHeap.lbu(a + i)
            val cb = GlobalHeap.lbu(b + i)
            if (ca != cb || ca == 0) return ca - cb
            i++
        }
        return 0
    }

    /**
     * Search for byte [c] in first [n] bytes of memory.
     *
     * Delegates to [FastStringMem.memchr] for optimized performance.
     *
     * @param addr Pointer to memory region
     * @param c Byte value to search for (0-255)
     * @param n Number of bytes to search
     * @return Pointer to first occurrence of [c], or 0 if not found
     *
     * ## Example
     * ```kotlin
     * val data = createHeapBytes(byteArrayOf(1, 2, 3, 4, 5))
     * val pos = CLib.memchr(data, 3, 5)  // Points to the '3'
     * ```
     *
     * ## C Equivalent
     * ```c
     * void *memchr(const void *s, int c, size_t n);
     * ```
     *
     * ## Complexity
     * O(n)
     */
    fun memchr(addr: Int, c: Int, n: Int): Int = FastStringMem.memchr(addr, c, n)

    /**
     * Find first occurrence of character [c] in null-terminated string.
     *
     * @param addr Pointer to null-terminated string
     * @param c Character code to search for (0-255)
     * @return Pointer to first occurrence of [c] (or NUL if c is 0), or 0 if not found
     *
     * ## Example
     * ```kotlin
     * val str = createHeapString("Hello")
     * val pos = CLib.strchr(str, 'l'.code)  // Points to first 'l'
     * ```
     *
     * ## C Equivalent
     * ```c
     * char *strchr(const char *s, int c);
     * ```
     *
     * ## Note
     * If [c] is 0, returns pointer to the terminating NUL.
     *
     * ## Complexity
     * O(n) where n = strlen(addr)
     */
    fun strchr(addr: Int, c: Int): Int {
        val needle = shifter.bitwiseAnd(c.toLong(), 0xFF).toInt()
        var i = 0
        while (true) {
            val b = GlobalHeap.lbu(addr + i)
            if (b == needle) return addr + i
            if (b == 0) return 0
            i++
        }
    }

    /**
     * Compare two memory regions lexicographically.
     *
     * Delegates to [FastStringMem.memcmp] for optimized performance.
     *
     * @param a Pointer to first memory region
     * @param b Pointer to second memory region
     * @param n Number of bytes to compare
     * @return Negative if a < b, zero if a == b, positive if a > b
     *
     * ## Example
     * ```kotlin
     * val data1 = createHeapBytes(byteArrayOf(1, 2, 3))
     * val data2 = createHeapBytes(byteArrayOf(1, 2, 4))
     * val cmp = CLib.memcmp(data1, data2, 3)  // < 0
     * ```
     *
     * ## C Equivalent
     * ```c
     * int memcmp(const void *s1, const void *s2, size_t n);
     * ```
     *
     * ## Complexity
     * O(n)
     */
    fun memcmp(a: Int, b: Int, n: Int): Int = FastStringMem.memcmp(a, b, n)
}
