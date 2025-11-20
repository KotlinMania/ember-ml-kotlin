package ai.solace.klang.common

import ai.solace.klang.mem.GlobalHeap

/**
 * StructLayout: C-style struct/union layout computation with natural alignment.
 *
 * Computes memory layouts for structures and unions following C ABI conventions:
 * - Natural alignment (each field aligned to its size)
 * - Little-endian byte order
 * - Padding inserted to maintain alignment
 * - Total size rounded up to structure alignment
 *
 * ## Why StructLayout?
 *
 * **Use Cases**:
 * - **FFI**: Interop with C libraries and native code
 * - **Binary Protocols**: Network protocols, file formats
 * - **Memory Management**: Efficient packing of heterogeneous data
 * - **Serialization**: Binary serialization with padding awareness
 * - **GPU Buffers**: Shader uniform buffers (std140/std430 layouts)
 *
 * ## Alignment Rules
 *
 * Natural alignment matches C compiler behavior:
 * ```
 * Type      Size  Alignment
 * --------- ----  ---------
 * char      1     1
 * short     2     2
 * int       4     4
 * long      8     8
 * float     4     4
 * double    8     8
 * pointer   4/8   4/8 (platform-dependent)
 * ```
 *
 * ## Usage Example
 *
 * ### Struct Layout
 * ```kotlin
 * // C equivalent:
 * // struct Point {
 * //     char  id;      // offset 0, size 1
 * //     short x;       // offset 2, size 2 (padding at offset 1)
 * //     int   y;       // offset 4, size 4
 * // };  // total size: 8 bytes
 *
 * val layout = StructLayout.layoutStruct(listOf(
 *     StructLayout.Field(size = 1, align = 1),  // char id
 *     StructLayout.Field(size = 2, align = 2),  // short x
 *     StructLayout.Field(size = 4, align = 4)   // int y
 * ))
 *
 * println(layout.offsets.toList())  // [0, 2, 4]
 * println(layout.size)              // 8
 * println(layout.align)             // 4 (max field alignment)
 * ```
 *
 * ### Union Layout
 * ```kotlin
 * // C equivalent:
 * // union Data {
 * //     int   i;       // offset 0, size 4
 * //     float f;       // offset 0, size 4
 * //     char  c[8];    // offset 0, size 8
 * // };  // total size: 8 bytes (max of all fields)
 *
 * val layout = StructLayout.layoutUnion(listOf(
 *     StructLayout.Field(size = 4, align = 4),  // int
 *     StructLayout.Field(size = 4, align = 4),  // float
 *     StructLayout.Field(size = 8, align = 1)   // char[8]
 * ))
 *
 * println(layout.offsets.toList())  // [0, 0, 0] (all overlay)
 * println(layout.size)              // 8 (max size)
 * println(layout.align)             // 4 (max alignment)
 * ```
 *
 * ### Allocate and Access
 * ```kotlin
 * // Define struct layout
 * val layout = StructLayout.layoutStruct(listOf(
 *     StructLayout.Field(1, 1),  // byte
 *     StructLayout.Field(4, 4)   // int
 * ))
 *
 * // Allocate on heap
 * val addr = StructLayout.alloc(layout)
 *
 * // Access fields
 * GlobalHeap.sb(addr + layout.offsets[0], 42)        // Set byte
 * GlobalHeap.sw(addr + layout.offsets[1], 1000)      // Set int
 *
 * val byte = GlobalHeap.lb(addr + layout.offsets[0]) // Read byte
 * val int = GlobalHeap.lw(addr + layout.offsets[1])  // Read int
 * ```
 *
 * ## Padding Example
 *
 * ```
 * struct Example {
 *     char  a;      // offset 0, size 1
 *     // [padding: 1 byte]
 *     short b;      // offset 2, size 2
 *     // [padding: 0 bytes]
 *     int   c;      // offset 4, size 4
 *     char  d;      // offset 8, size 1
 *     // [padding: 3 bytes to align to 4]
 * };  // total: 12 bytes
 * ```
 *
 * ## Struct vs Union
 *
 * | Property | Struct | Union |
 * |----------|--------|-------|
 * | Field offsets | Sequential (with padding) | All zero (overlay) |
 * | Total size | Sum + padding | Max field size + padding |
 * | Alignment | Max field alignment | Max field alignment |
 * | Use case | Independent fields | Alternative representations |
 *
 * ## Performance
 *
 * Layout computation is O(n) where n is the number of fields.
 * Results should be cached and reused for repeated allocations.
 *
 * @since 0.1.0
 */
object StructLayout {
    /**
     * Field: Describes a single field in a struct or union.
     *
     * @property size Field size in bytes
     * @property align Field alignment requirement in bytes
     */
    data class Field(val size: Int, val align: Int)
    
    /**
     * Layout: Computed memory layout for a struct or union.
     *
     * @property offsets Array of byte offsets for each field
     * @property size Total size in bytes (including padding)
     * @property align Overall alignment requirement in bytes
     */
    data class Layout(val offsets: IntArray, val size: Int, val align: Int)

    /**
     * Compute natural-aligned struct layout.
     *
     * Fields are laid out sequentially with padding inserted
     * to satisfy each field's alignment requirement.
     *
     * ## Algorithm
     * ```
     * 1. For each field:
     *    a. Round current offset up to field's alignment
     *    b. Place field at aligned offset
     *    c. Advance offset by field size
     * 2. Round total size up to struct's alignment
     * ```
     *
     * @param fields List of field descriptors
     * @return Layout with offsets, total size, and alignment
     * @throws IllegalArgumentException if fields is empty
     */
    fun layoutStruct(fields: List<Field>): Layout {
        require(fields.isNotEmpty())
        val offsets = IntArray(fields.size)
        var offset = 0
        var maxAlign = 1
        for ((i, f) in fields.withIndex()) {
            val a = f.align.coerceAtLeast(1)
            maxAlign = maxOf(maxAlign, a)
            val aligned = ((offset + (a - 1)) / a) * a
            offsets[i] = aligned
            offset = aligned + f.size
        }
        val total = ((offset + (maxAlign - 1)) / maxAlign) * maxAlign
        return Layout(offsets, total, maxAlign)
    }

    /**
     * Compute union layout (all fields at offset 0).
     *
     * All fields overlay at the same address. Total size is
     * the maximum field size, rounded up to max alignment.
     *
     * ## Algorithm
     * ```
     * 1. Find max field size
     * 2. Find max field alignment
     * 3. Round size up to alignment
     * 4. All offsets are 0
     * ```
     *
     * @param fields List of field descriptors
     * @return Layout with all offsets zero, max size, max alignment
     * @throws IllegalArgumentException if fields is empty
     */
    fun layoutUnion(fields: List<Field>): Layout {
        require(fields.isNotEmpty())
        var size = 0
        var align = 1
        for (f in fields) { size = maxOf(size, f.size); align = maxOf(align, f.align) }
        val total = ((size + (align - 1)) / align) * align
        return Layout(IntArray(fields.size) { 0 }, total, align)
    }

    /**
     * Allocate memory for a struct/union on the global heap.
     *
     * Allocates [layout.size] bytes on [GlobalHeap] and returns
     * the base address. Caller is responsible for initialization.
     *
     * ## Example
     * ```kotlin
     * val layout = layoutStruct(...)
     * val addr = alloc(layout)
     * // Use addr + layout.offsets[i] to access fields
     * ```
     *
     * @param layout Layout descriptor with size
     * @return Base address of allocated memory
     * @see GlobalHeap.malloc
     */
    fun alloc(layout: Layout): Int = GlobalHeap.malloc(layout.size)
}
