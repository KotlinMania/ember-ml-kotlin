package ai.solace.klang.internal

import ai.solace.klang.internal.runtime.CPointer

/**
 * Typed pointer extensions for [CPointer].
 *
 * Provides Kotlin-idiomatic pointer arithmetic, memory access, and allocation
 * operations that mirror C's pointer semantics while maintaining type safety.
 */

/**
 * Advances a byte pointer by [i] bytes.
 * 
 * @param i The number of bytes to advance.
 * @return A new pointer at offset i bytes.
 */
inline fun CPointer<Byte>.index(i: Int): CPointer<Byte> = CPointer(this.ptr + i)

/**
 * Advances a short pointer by [i] elements (i * 2 bytes).
 * 
 * @param i The number of short elements to advance.
 * @return A new pointer at offset i * sizeof(short) bytes.
 */
inline fun CPointer<Short>.index(i: Int): CPointer<Short> = CPointer(this.ptr + i * 2)

/**
 * Advances an int pointer by [i] elements (i * 4 bytes).
 * 
 * @param i The number of int elements to advance.
 * @return A new pointer at offset i * sizeof(int) bytes.
 */
inline fun CPointer<Int>.index(i: Int): CPointer<Int> = CPointer(this.ptr + i * 4)

/**
 * Advances a long pointer by [i] elements (i * 8 bytes).
 * 
 * @param i The number of long elements to advance.
 * @return A new pointer at offset i * sizeof(long) bytes.
 */
inline fun CPointer<Long>.index(i: Int): CPointer<Long> = CPointer(this.ptr + i * 8)

/**
 * Advances a float pointer by [i] elements (i * 4 bytes).
 * 
 * @param i The number of float elements to advance.
 * @return A new pointer at offset i * sizeof(float) bytes.
 */
inline fun CPointer<Float>.index(i: Int): CPointer<Float> = CPointer(this.ptr + i * 4)

/**
 * Advances a double pointer by [i] elements (i * 8 bytes).
 * 
 * @param i The number of double elements to advance.
 * @return A new pointer at offset i * sizeof(double) bytes.
 */
inline fun CPointer<Double>.index(i: Int): CPointer<Double> = CPointer(this.ptr + i * 8)

/**
 * Loads a byte value from memory at this pointer's address.
 * 
 * @return The byte value stored at this address.
 */
fun CPointer<Byte>.load(): Byte = ai.solace.klang.mem.GlobalHeap.lb(ptr)

/**
 * Stores a byte value to memory at this pointer's address.
 * 
 * @param v The byte value to store.
 */
fun CPointer<Byte>.store(v: Byte) = ai.solace.klang.mem.GlobalHeap.sb(ptr, v)

/**
 * Loads a short value from memory at this pointer's address.
 * 
 * @return The short value stored at this address.
 */
fun CPointer<Short>.load(): Short = ai.solace.klang.mem.GlobalHeap.lh(ptr)

/**
 * Stores a short value to memory at this pointer's address.
 * 
 * @param v The short value to store.
 */
fun CPointer<Short>.store(v: Short) = ai.solace.klang.mem.GlobalHeap.sh(ptr, v)

/**
 * Loads an int value from memory at this pointer's address.
 * 
 * @return The int value stored at this address.
 */
fun CPointer<Int>.load(): Int = ai.solace.klang.mem.GlobalHeap.lw(ptr)

/**
 * Stores an int value to memory at this pointer's address.
 * 
 * @param v The int value to store.
 */
fun CPointer<Int>.store(v: Int) = ai.solace.klang.mem.GlobalHeap.sw(ptr, v)

/**
 * Loads a long value from memory at this pointer's address.
 * 
 * @return The long value stored at this address.
 */
fun CPointer<Long>.load(): Long = ai.solace.klang.mem.GlobalHeap.ld(ptr)

/**
 * Stores a long value to memory at this pointer's address.
 * 
 * @param v The long value to store.
 */
fun CPointer<Long>.store(v: Long) = ai.solace.klang.mem.GlobalHeap.sd(ptr, v)

/**
 * Loads a float value from memory at this pointer's address.
 * 
 * @return The float value stored at this address.
 */
fun CPointer<Float>.load(): Float = ai.solace.klang.mem.GlobalHeap.lwf(ptr)

/**
 * Stores a float value to memory at this pointer's address.
 * 
 * @param v The float value to store.
 */
fun CPointer<Float>.store(v: Float) = ai.solace.klang.mem.GlobalHeap.swf(ptr, v)

/**
 * Loads a double value from memory at this pointer's address.
 * 
 * @return The double value stored at this address.
 */
fun CPointer<Double>.load(): Double = ai.solace.klang.mem.GlobalHeap.ldf(ptr)

/**
 * Stores a double value to memory at this pointer's address.
 * 
 * @param v The double value to store.
 */
fun CPointer<Double>.store(v: Double) = ai.solace.klang.mem.GlobalHeap.sdf(ptr, v)

/**
 * Allocates [n] bytes of uninitialized memory.
 * 
 * @param n The number of bytes to allocate.
 * @return A pointer to the allocated memory, or null on failure.
 */
fun mallocBytes(n: Int): CPointer<Byte> = CPointer(ai.solace.klang.mem.GlobalHeap.malloc(n))

/**
 * Allocates [n] bytes of zero-initialized memory.
 * 
 * @param n The number of bytes to allocate.
 * @return A pointer to the allocated memory, or null on failure.
 */
fun callocBytes(n: Int): CPointer<Byte> = CPointer(ai.solace.klang.mem.GlobalHeap.calloc(n, 1))

