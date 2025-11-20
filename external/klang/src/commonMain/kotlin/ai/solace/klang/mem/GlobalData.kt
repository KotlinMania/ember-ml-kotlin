package ai.solace.klang.mem

/**
 * GlobalData: simple DATA/BSS placement over GlobalHeap.
 *
 * - Symbols live for the whole program; addresses are Int byte offsets into GlobalHeap.
 * - DATA: initialized bytes copied at definition time.
 * - BSS: zero-initialized region of given size.
 * - Alignment: power-of-two; returns an aligned address. For align > KMalloc's
 *   native alignment (16 bytes), we over-allocate and return an aligned pointer
 *   inside the block. Original base is retained for disposal.
 */
object GlobalData {
    private val symbols = LinkedHashMap<String, Int>()
    private val bases = ArrayList<Int>()
    private var initialized = false

    fun init() { initialized = true }

    fun dispose() {
        for (b in bases) KMalloc.free(b)
        symbols.clear(); bases.clear(); initialized = false
    }

    fun get(name: String): Int = symbols[name]
        ?: error("Global symbol not defined: $name")

    fun isDefined(name: String): Boolean = symbols.containsKey(name)

    fun defineBss(name: String, size: Int, align: Int = 16): Int {
        ensureInit(); require(name.isNotBlank()); require(size >= 0); checkAlign(align)
        check(!symbols.containsKey(name)) { "Duplicate global: $name" }
        val (base, aligned) = allocAligned(size, align, zero = true)
        symbols[name] = aligned
        bases += base
        return aligned
    }

    fun defineData(name: String, initBytes: ByteArray, align: Int = 16): Int {
        ensureInit(); require(name.isNotBlank()); checkAlign(align)
        check(!symbols.containsKey(name)) { "Duplicate global: $name" }
        val n = initBytes.size
        val (base, aligned) = allocAligned(n, align, zero = false)
        var p = aligned
        for (b in initBytes) GlobalHeap.sb(p++, b)
        symbols[name] = aligned
        bases += base
        return aligned
    }

    // Convenience constructors ------------------------------------------------
    fun defineI32(name: String, value: Int = 0, align: Int = 4): Int {
        val addr = defineBss(name, 4, align)
        GlobalHeap.sw(addr, value); return addr
    }

    fun defineI64(name: String, value: Long = 0L, align: Int = 8): Int {
        val addr = defineBss(name, 8, align)
        GlobalHeap.sd(addr, value); return addr
    }

    fun defineF64(name: String, value: Double = 0.0, align: Int = 8): Int {
        val addr = defineBss(name, 8, align)
        GlobalHeap.sdf(addr, value); return addr
    }

    // Internals ---------------------------------------------------------------
    private fun allocAligned(size: Int, align: Int, zero: Boolean): Pair<Int, Int> {
        // Over-allocate to ensure we can return an aligned address inside the block.
        val total = size + (align - 1)
        val base = if (zero) KMalloc.calloc(total, 1) else KMalloc.malloc(total)
        val aligned = alignUp(base, align)
        if (zero && aligned != base) {
            // zero any leading slack to keep BSS semantics
            GlobalHeap.memset(base, 0, aligned - base)
        }
        return base to aligned
    }

    private fun alignUp(x: Int, align: Int): Int = (x + (align - 1)) and (align - 1).inv()
    private fun checkAlign(align: Int) { require(align > 0 && (align and (align - 1)) == 0) }
    private fun ensureInit() { check(initialized) { "GlobalData not initialized" } }
}

