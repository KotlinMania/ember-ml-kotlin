package ai.solace.klang.internal.symbols

import ai.solace.klang.internal.runtime.CPointer

/**
 * C standard library function symbols backed by [GlobalHeap].
 *
 * Provides familiar C library function names (strlen, strcpy, memcpy, etc.) that operate
 * on [CPointer] wrappers instead of raw integer addresses. These functions delegate to
 * the heap-backed implementations in [ai.solace.klang.mem.CLib] and [ai.solace.klang.mem.GlobalHeap].
 *
 * ## Purpose
 *
 * Enables C-like code patterns in Kotlin while maintaining type safety through [CPointer].
 * Particularly useful for:
 * - Porting C code to Kotlin
 * - Implementing C-compatible APIs
 * - Working with binary protocols
 * - String manipulation on heap memory
 *
 * ## Usage Example
 *
 * ```kotlin
 * val src = strdupCString("Hello")
 * val dst = mallocBytes(32)
 * strcpy(dst, src)
 * val len = strlen(dst)  // Returns 5
 * ```
 *
 * @see ai.solace.klang.mem.CLib For the underlying implementations
 * @see CPointer For the pointer wrapper type
 */

/**
 * Calculates the length of a null-terminated C string.
 *
 * @param s Pointer to the null-terminated string.
 * @return The number of bytes before the null terminator.
 */
fun strlen(s: CPointer<Byte>): Int = ai.solace.klang.mem.CLib.strlen(s.ptr)

/**
 * Calculates the length of a C string, up to a maximum.
 *
 * @param s Pointer to the null-terminated string.
 * @param n Maximum number of bytes to examine.
 * @return The number of bytes before null or n, whichever is smaller.
 */
fun strnlen(s: CPointer<Byte>, n: Int): Int = ai.solace.klang.mem.CLib.strnlen(s.ptr, n)

/**
 * Compares two null-terminated C strings lexicographically.
 *
 * @param a First string pointer.
 * @param b Second string pointer.
 * @return Negative if a < b, 0 if equal, positive if a > b.
 */
fun strcmp(a: CPointer<Byte>, b: CPointer<Byte>): Int = ai.solace.klang.mem.CLib.strcmp(a.ptr, b.ptr)

/**
 * Compares up to n bytes of two C strings lexicographically.
 *
 * @param a First string pointer.
 * @param b Second string pointer.
 * @param n Maximum number of bytes to compare.
 * @return Negative if a < b, 0 if equal, positive if a > b.
 */
fun strncmp(a: CPointer<Byte>, b: CPointer<Byte>, n: Int): Int = ai.solace.klang.mem.CLib.strncmp(a.ptr, b.ptr, n)

/**
 * Copies a null-terminated string from source to destination.
 *
 * @param dst Destination pointer (must have sufficient space).
 * @param src Source string pointer.
 * @return The destination pointer.
 */
fun strcpy(dst: CPointer<Byte>, src: CPointer<Byte>): CPointer<Byte> = CPointer(ai.solace.klang.mem.CLib.strcpy(dst.ptr, src.ptr))

/**
 * Copies up to n bytes from source to destination.
 *
 * @param dst Destination pointer.
 * @param src Source string pointer.
 * @param n Maximum number of bytes to copy.
 * @return The destination pointer.
 */
fun strncpy(dst: CPointer<Byte>, src: CPointer<Byte>, n: Int): CPointer<Byte> = CPointer(ai.solace.klang.mem.CLib.strncpy(dst.ptr, src.ptr, n))

/**
 * Searches for the first occurrence of a byte in a memory region.
 *
 * @param addr Base address to search.
 * @param c Byte value to find.
 * @param n Number of bytes to search.
 * @return Pointer to the found byte, or null pointer if not found.
 */
fun memchr(addr: CPointer<Byte>, c: Int, n: Int): CPointer<Byte> = CPointer(ai.solace.klang.mem.CLib.memchr(addr.ptr, c, n))

/**
 * Searches for the first occurrence of a character in a C string.
 *
 * @param addr String pointer.
 * @param c Character code to find.
 * @return Pointer to the found character, or null pointer if not found.
 */
fun strchr(addr: CPointer<Byte>, c: Int): CPointer<Byte> = CPointer(ai.solace.klang.mem.CLib.strchr(addr.ptr, c))

/**
 * Compares two memory regions byte-by-byte.
 *
 * @param a First memory region.
 * @param b Second memory region.
 * @param n Number of bytes to compare.
 * @return Negative if a < b, 0 if equal, positive if a > b.
 */
fun memcmp(a: CPointer<Byte>, b: CPointer<Byte>, n: Int): Int = ai.solace.klang.mem.CLib.memcmp(a.ptr, b.ptr, n)

/**
 * Copies n bytes from source to destination (regions must not overlap).
 *
 * @param dst Destination pointer.
 * @param src Source pointer.
 * @param n Number of bytes to copy.
 * @return The destination pointer.
 */
fun memcpy(dst: CPointer<Byte>, src: CPointer<Byte>, n: Int): CPointer<Byte> {
    ai.solace.klang.mem.GlobalHeap.memcpy(dst.ptr, src.ptr, n)
    return dst
}
/**
 * Copies n bytes from source to destination, handling overlapping regions.
 *
 * @param dst Destination pointer.
 * @param src Source pointer.
 * @param n Number of bytes to copy.
 * @return The destination pointer.
 */
fun memmove(dst: CPointer<Byte>, src: CPointer<Byte>, n: Int): CPointer<Byte> {
    ai.solace.klang.mem.GlobalHeap.memmove(dst.ptr, src.ptr, n)
    return dst
}
/**
 * Fills n bytes of memory with a constant byte value.
 *
 * @param dst Destination pointer.
 * @param c Byte value to fill (masked to 0-255).
 * @param n Number of bytes to fill.
 * @return The destination pointer.
 */
fun memset(dst: CPointer<Byte>, c: Int, n: Int): CPointer<Byte> {
    ai.solace.klang.mem.GlobalHeap.memset(dst.ptr, c, n)
    return dst
}
