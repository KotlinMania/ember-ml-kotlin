package ai.solace.klang.internal

import ai.solace.klang.internal.runtime.CPointer

/**
 * C string extension functions for [CPointer]<[Byte]>.
 *
 * Provides Kotlin-idiomatic extensions for C-style null-terminated string operations
 * on byte pointers, bridging the gap between Kotlin's String type and raw memory.
 */

/**
 * Calculates the length of a C string (null-terminated).
 *
 * @return The number of bytes before the null terminator.
 */
fun CPointer<Byte>.strlenz(): Int = ai.solace.klang.mem.CString.strlenz(this.ptr)

/**
 * Reads a C string from memory and converts it to a Kotlin [String].
 *
 * @return The decoded string up to the null terminator.
 */
fun CPointer<Byte>.readCString(): String = ai.solace.klang.mem.CString.read(this.ptr)

/**
 * Writes a Kotlin [String] to memory as a null-terminated C string.
 *
 * @param s The string to write.
 * @return The number of bytes written (excluding the null terminator).
 */
fun CPointer<Byte>.writeCString(s: String): Int = ai.solace.klang.mem.CString.write(this.ptr, s)

/**
 * Duplicates a string by allocating new memory and copying the content.
 *
 * @param s The string to duplicate.
 * @return A new [CPointer] to the allocated memory containing the string copy.
 */
fun strdupCString(s: String): CPointer<Byte> = CPointer(ai.solace.klang.mem.CString.strdup(s))

/**
 * Copies a C string from source to destination.
 *
 * @param src The source string pointer.
 * @return The destination pointer (this).
 */
fun CPointer<Byte>.strcpy(src: CPointer<Byte>): CPointer<Byte> = 
    CPointer(ai.solace.klang.mem.CLib.strcpy(this.ptr, src.ptr))

/**
 * Copies at most [n] bytes from source to destination.
 *
 * @param src The source string pointer.
 * @param n The maximum number of bytes to copy.
 * @return The destination pointer (this).
 */
fun CPointer<Byte>.strncpy(src: CPointer<Byte>, n: Int): CPointer<Byte> = 
    CPointer(ai.solace.klang.mem.CLib.strncpy(this.ptr, src.ptr, n))

/**
 * Compares two C strings lexicographically.
 *
 * @param other The string to compare against.
 * @return Negative if this < other, 0 if equal, positive if this > other.
 */
fun CPointer<Byte>.strcmp(other: CPointer<Byte>): Int = 
    ai.solace.klang.mem.CLib.strcmp(this.ptr, other.ptr)

/**
 * Finds the first occurrence of a character in a C string.
 *
 * @param c The character code to search for.
 * @return Pointer to the found character, or null pointer if not found.
 */
fun CPointer<Byte>.strchr(c: Int): CPointer<Byte> = 
    CPointer(ai.solace.klang.mem.CLib.strchr(this.ptr, c))

/**
 * Finds the first occurrence of a byte in a memory region.
 *
 * @param c The byte value to search for.
 * @param n The number of bytes to search.
 * @return Pointer to the found byte, or null pointer if not found.
 */
fun CPointer<Byte>.memchr(c: Int, n: Int): CPointer<Byte> = 
    CPointer(ai.solace.klang.mem.CLib.memchr(this.ptr, c, n))

/**
 * Compares two memory regions byte-by-byte.
 *
 * @param other The memory region to compare against.
 * @param n The number of bytes to compare.
 * @return Negative if this < other, 0 if equal, positive if this > other.
 */
fun CPointer<Byte>.memcmp(other: CPointer<Byte>, n: Int): Int = 
    ai.solace.klang.mem.CLib.memcmp(this.ptr, other.ptr, n)
