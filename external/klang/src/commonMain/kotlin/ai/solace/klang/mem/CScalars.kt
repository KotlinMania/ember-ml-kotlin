package ai.solace.klang.mem

import ai.solace.klang.fp.CDouble

/**
 * CVar: Base interface for C-style scalar variables stored in heap memory.
 *
 * All CVar implementations represent a single value stored at a fixed heap address.
 * Operations mutate the heap directly (zero-copy) rather than creating new objects.
 *
 * @property addr Heap address where the value is stored
 * @since 0.1.0
 */
interface CVar { val addr: Int }

/**
 * CByteVar: C-style `char` variable (8-bit signed integer).
 *
 * Represents a single byte stored in heap memory.
 * Provides direct load/store operations on [GlobalHeap].
 *
 * ## Usage Example
 * ```kotlin
 * KStack.withFrame {
 *     val c = CAutos.byte(65)  // 'A'
 *     println(c.value)  // 65
 *     c.value = 66      // 'B'
 *     println(c.value)  // 66
 * }
 * ```
 *
 * @property addr Heap address of the byte
 * @property value The byte value (mutable, directly accesses heap)
 * @see GlobalHeap.lb Load byte
 * @see GlobalHeap.sb Store byte
 */
class CByteVar(override val addr: Int) : CVar {
    /**
     * The byte value stored at [addr].
     *
     * **Get**: Loads from heap via [GlobalHeap.lb]
     * **Set**: Stores to heap via [GlobalHeap.sb]
     */
    var value: Byte
        get() = GlobalHeap.lb(addr)
        set(v) = GlobalHeap.sb(addr, v)
}

/**
 * CShortVar: C-style `short` variable (16-bit signed integer).
 *
 * Represents a 16-bit integer stored in heap memory.
 * Provides direct load/store operations on [GlobalHeap].
 *
 * ## Usage Example
 * ```kotlin
 * val s = CAutos.short(1000)
 * s.value += 500  // 1500
 * ```
 *
 * @property addr Heap address of the short (2-byte aligned recommended)
 * @property value The short value (mutable, directly accesses heap)
 * @see GlobalHeap.lh Load halfword
 * @see GlobalHeap.sh Store halfword
 */
class CShortVar(override val addr: Int) : CVar {
    /**
     * The 16-bit value stored at [addr].
     *
     * **Get**: Loads from heap via [GlobalHeap.lh]
     * **Set**: Stores to heap via [GlobalHeap.sh]
     */
    var value: Short
        get() = GlobalHeap.lh(addr)
        set(v) = GlobalHeap.sh(addr, v)
}

/**
 * CIntVar: C-style `int` variable (32-bit signed integer).
 *
 * Represents a 32-bit integer stored in heap memory with additional
 * compound assignment operations for efficiency.
 *
 * ## Usage Example
 * ```kotlin
 * val counter = CAutos.int(0)
 * counter.value = 10
 * counter.addAssign(5)   // counter.value == 15
 * counter.subAssign(3)   // counter.value == 12
 * ```
 *
 * @property addr Heap address of the int (4-byte aligned recommended)
 * @property value The int value (mutable, directly accesses heap)
 * @see GlobalHeap.lw Load word
 * @see GlobalHeap.sw Store word
 */
class CIntVar(override val addr: Int) : CVar {
    /**
     * The 32-bit value stored at [addr].
     *
     * **Get**: Loads from heap via [GlobalHeap.lw]
     * **Set**: Stores to heap via [GlobalHeap.sw]
     */
    var value: Int
        get() = GlobalHeap.lw(addr)
        set(v) = GlobalHeap.sw(addr, v)

    /**
     * In-place addition (equivalent to C's `+=`).
     *
     * Loads current value, adds, stores result.
     *
     * @param x Value to add
     */
    fun addAssign(x: Int) { GlobalHeap.sw(addr, GlobalHeap.lw(addr) + x) }
    
    /**
     * In-place subtraction (equivalent to C's `-=`).
     *
     * Loads current value, subtracts, stores result.
     *
     * @param x Value to subtract
     */
    fun subAssign(x: Int) { GlobalHeap.sw(addr, GlobalHeap.lw(addr) - x) }
}

/**
 * CLongVar: C-style `long` variable (64-bit signed integer).
 *
 * Represents a 64-bit integer stored in heap memory.
 * Provides direct load/store operations on [GlobalHeap].
 *
 * ## Usage Example
 * ```kotlin
 * val bigNumber = CAutos.long(1_000_000_000_000L)
 * bigNumber.value *= 2  // 2 trillion
 * ```
 *
 * @property addr Heap address of the long (8-byte aligned recommended)
 * @property value The long value (mutable, directly accesses heap)
 * @see GlobalHeap.ld Load doubleword
 * @see GlobalHeap.sd Store doubleword
 */
class CLongVar(override val addr: Int) : CVar {
    /**
     * The 64-bit value stored at [addr].
     *
     * **Get**: Loads from heap via [GlobalHeap.ld]
     * **Set**: Stores to heap via [GlobalHeap.sd]
     */
    var value: Long
        get() = GlobalHeap.ld(addr)
        set(v) = GlobalHeap.sd(addr, v)
}

/**
 * CFloatVar: C-style `float` variable (32-bit IEEE-754).
 *
 * Represents a single-precision float stored in heap memory.
 * Provides direct load/store operations on [GlobalHeap].
 *
 * ## Usage Example
 * ```kotlin
 * val temperature = CAutos.float(98.6f)
 * temperature.value += 1.0f  // 99.6
 * ```
 *
 * @property addr Heap address of the float (4-byte aligned recommended)
 * @property value The float value (mutable, directly accesses heap)
 * @see GlobalHeap.lwf Load word as float
 * @see GlobalHeap.swf Store float as word
 */
class CFloatVar(override val addr: Int) : CVar {
    /**
     * The 32-bit float value stored at [addr].
     *
     * **Get**: Loads from heap via [GlobalHeap.lwf]
     * **Set**: Stores to heap via [GlobalHeap.swf]
     */
    var value: Float
        get() = GlobalHeap.lwf(addr)
        set(v) = GlobalHeap.swf(addr, v)
}

/**
 * CDoubleVar: C-style `double` variable (64-bit IEEE-754).
 *
 * Represents a double-precision float stored in heap memory.
 * Provides access both as native [Double] and as [CDouble] for
 * cross-platform deterministic arithmetic.
 *
 * ## Usage Example
 * ```kotlin
 * val pi = CAutos.double(3.14159265359)
 * pi.value *= 2.0  // 6.28...
 *
 * // Access as CDouble for deterministic math
 * val cd = pi.cdouble
 * val squared = cd * cd
 * pi.cdouble = squared
 * ```
 *
 * @property addr Heap address of the double (8-byte aligned recommended)
 * @property value The double value (mutable, directly accesses heap)
 * @property cdouble Access as [CDouble] for deterministic operations
 * @see GlobalHeap.ldf Load doubleword as double
 * @see GlobalHeap.sdf Store double as doubleword
 * @see CDouble For cross-platform deterministic arithmetic
 */
class CDoubleVar(override val addr: Int) : CVar {
    /**
     * The 64-bit double value stored at [addr].
     *
     * **Get**: Loads from heap via [GlobalHeap.ldf]
     * **Set**: Stores to heap via [GlobalHeap.sdf]
     */
    var value: Double
        get() = GlobalHeap.ldf(addr)
        set(v) = GlobalHeap.sdf(addr, v)

    /**
     * Access value as [CDouble] for deterministic arithmetic.
     *
     * **Get**: Loads bits from heap and wraps in CDouble
     * **Set**: Unwraps CDouble bits and stores to heap
     *
     * ## Example
     * ```kotlin
     * val x = CAutos.double(1.0)
     * val y = CAutos.double(2.0)
     * val sumCD = x.cdouble + y.cdouble  // Deterministic addition
     * x.cdouble = sumCD
     * ```
     */
    var cdouble: CDouble
        get() = CDouble.fromBits(GlobalHeap.ld(addr))
        set(v) = GlobalHeap.sd(addr, v.toBits())
}

/**
 * CAutos: Factory for automatic (stack) storage variables.
 *
 * Creates C-style variables on [KStack] with automatic lifetime management.
 * Variables are freed when their stack frame is popped.
 *
 * ## Automatic Storage
 *
 * Analogous to C's automatic variables:
 * ```c
 * void foo() {
 *     int x = 42;      // Automatic storage
 *     double y = 3.14; // Freed at function exit
 * }
 * ```
 *
 * Kotlin equivalent:
 * ```kotlin
 * fun foo() {
 *     KStack.withFrame {
 *         val x = CAutos.int(42)
 *         val y = CAutos.double(3.14)
 *         // Use x, y...
 *     }  // Automatically freed
 * }
 * ```
 *
 * ## Usage Example
 *
 * ```kotlin
 * KStack.init(1024 * 1024)
 *
 * KStack.withFrame {
 *     val counter = CAutos.int(0)
 *     val sum = CAutos.double(0.0)
 *
 *     for (i in 0..100) {
 *         counter.addAssign(1)
 *         sum.value += i.toDouble()
 *     }
 *
 *     println("Counter: ${counter.value}")
 *     println("Sum: ${sum.value}")
 * }  // counter and sum freed automatically
 * ```
 *
 * ## Performance
 *
 * - **Allocation**: O(1) via [KStack.alloca]
 * - **Deallocation**: O(1) via frame pop (bulk free)
 * - **Access**: Direct heap access (no indirection)
 *
 * ## Alignment
 *
 * Each type has appropriate default alignment:
 * - byte: 1-byte (no alignment)
 * - short: 2-byte aligned
 * - int/float: 4-byte aligned
 * - long/double: 8-byte aligned
 *
 * @see KStack For stack management
 * @see CGlobals For static/global variables
 * @see CHeapVars For heap-allocated variables
 * @since 0.1.0
 */
object CAutos {
    /**
     * Allocate a byte on the stack.
     *
     * @param init Initial value (default: 0)
     * @param align Alignment (default: 1)
     * @return CByteVar pointing to stack memory
     */
    fun byte(init: Byte = 0, align: Int = 1): CByteVar {
        val p = KStack.alloca(1, align)
        GlobalHeap.sb(p, init)
        return CByteVar(p)
    }
    
    /**
     * Allocate a short on the stack.
     *
     * @param init Initial value (default: 0)
     * @param align Alignment (default: 2)
     * @return CShortVar pointing to stack memory
     */
    fun short(init: Short = 0, align: Int = 2): CShortVar {
        val p = KStack.alloca(2, align)
        GlobalHeap.sh(p, init)
        return CShortVar(p)
    }
    
    /**
     * Allocate an int on the stack.
     *
     * @param init Initial value (default: 0)
     * @param align Alignment (default: 4)
     * @return CIntVar pointing to stack memory
     */
    fun int(init: Int = 0, align: Int = 4): CIntVar {
        val p = KStack.alloca(4, align)
        GlobalHeap.sw(p, init)
        return CIntVar(p)
    }
    
    /**
     * Allocate a long on the stack.
     *
     * @param init Initial value (default: 0)
     * @param align Alignment (default: 8)
     * @return CLongVar pointing to stack memory
     */
    fun long(init: Long = 0L, align: Int = 8): CLongVar {
        val p = KStack.alloca(8, align)
        GlobalHeap.sd(p, init)
        return CLongVar(p)
    }
    
    /**
     * Allocate a float on the stack.
     *
     * @param init Initial value (default: 0.0f)
     * @param align Alignment (default: 4)
     * @return CFloatVar pointing to stack memory
     */
    fun float(init: Float = 0f, align: Int = 4): CFloatVar {
        val p = KStack.alloca(4, align)
        GlobalHeap.swf(p, init)
        return CFloatVar(p)
    }
    
    /**
     * Allocate a double on the stack.
     *
     * @param init Initial value (default: 0.0)
     * @param align Alignment (default: 8)
     * @return CDoubleVar pointing to stack memory
     */
    fun double(init: Double = 0.0, align: Int = 8): CDoubleVar {
        val p = KStack.alloca(8, align)
        GlobalHeap.sdf(p, init)
        return CDoubleVar(p)
    }
}

/**
 * CGlobals: Factory for global/static storage variables.
 *
 * Creates C-style variables in [GlobalData] (DATA/BSS segments) with
 * static lifetime and named access.
 *
 * ## Static Storage
 *
 * Analogous to C's global/static variables:
 * ```c
 * int globalCounter = 0;        // Global
 * static double configValue;    // Static
 * ```
 *
 * Kotlin equivalent:
 * ```kotlin
 * val globalCounter = CGlobals.int("counter", 0)
 * val configValue = CGlobals.double("config", 3.14)
 * ```
 *
 * ## Usage Example
 *
 * ```kotlin
 * GlobalData.init(1024)
 *
 * val counter = CGlobals.int("appCounter", 0)
 * val version = CGlobals.double("appVersion", 1.0)
 *
 * fun incrementCounter() {
 *     counter.value++
 * }
 *
 * fun getVersion(): Double = version.value
 * ```
 *
 * ## Named Access
 *
 * Variables can be retrieved by name:
 * ```kotlin
 * val counter = CGlobals.int("hits", 0)
 * // Later...
 * val addr = GlobalData.getAddress("hits")
 * val sameCounter = CIntVar(addr)
 * ```
 *
 * ## Performance
 *
 * - **Define**: O(1) amortized (hash table)
 * - **Access**: Direct heap access (no lookup)
 * - **Lifetime**: Static (never freed)
 *
 * @see GlobalData For underlying DATA/BSS management
 * @see CAutos For stack variables
 * @see CHeapVars For heap variables
 * @since 0.1.0
 */
object CGlobals {
    /**
     * Define a global int variable.
     *
     * @param name Unique identifier for the variable
     * @param init Initial value (default: 0)
     * @param align Alignment (default: 4)
     * @return CIntVar pointing to global memory
     */
    fun int(name: String, init: Int = 0, align: Int = 4): CIntVar = CIntVar(GlobalData.defineI32(name, init, align))
    
    /**
     * Define a global long variable.
     *
     * @param name Unique identifier for the variable
     * @param init Initial value (default: 0)
     * @param align Alignment (default: 8)
     * @return CLongVar pointing to global memory
     */
    fun long(name: String, init: Long = 0, align: Int = 8): CLongVar = CLongVar(GlobalData.defineI64(name, init, align))
    
    /**
     * Define a global double variable.
     *
     * @param name Unique identifier for the variable
     * @param init Initial value (default: 0.0)
     * @param align Alignment (default: 8)
     * @return CDoubleVar pointing to global memory
     */
    fun double(name: String, init: Double = 0.0, align: Int = 8): CDoubleVar = CDoubleVar(GlobalData.defineF64(name, init, align))
}

/**
 * CHeapVars: Factory for heap-allocated variables.
 *
 * Creates C-style variables on [KMalloc] heap with manual lifetime management.
 * Variables must be explicitly freed via [free] to avoid memory leaks.
 *
 * ## Heap Storage
 *
 * Analogous to C's malloc'd variables:
 * ```c
 * int* p = (int*)malloc(sizeof(int));
 * *p = 42;
 * free(p);
 * ```
 *
 * Kotlin equivalent:
 * ```kotlin
 * val p = CHeapVars.int(42)
 * p.value += 10
 * CHeapVars.free(p)
 * ```
 *
 * ## Usage Example
 *
 * ```kotlin
 * KMalloc.init(1024 * 1024)
 *
 * val data = CHeapVars.double(3.14)
 * try {
 *     data.value *= 2
 *     println(data.value)  // 6.28
 * } finally {
 *     CHeapVars.free(data)  // Important!
 * }
 * ```
 *
 * ## When to Use
 *
 * Use heap variables when:
 * - Lifetime exceeds stack frame
 * - Data outlives function scope
 * - Dynamic allocation is required
 *
 * Prefer CAutos (stack) when possible for automatic cleanup.
 *
 * ## Performance
 *
 * - **Allocation**: O(n) avg via [KMalloc.malloc]
 * - **Deallocation**: O(1) via [KMalloc.free]
 * - **Overhead**: 8 bytes (KMalloc metadata)
 *
 * @see KMalloc For heap management
 * @see CAutos For stack variables (RAII-style)
 * @see CGlobals For static variables
 * @since 0.1.0
 */
object CHeapVars {
    /**
     * Allocate an int on the heap.
     *
     * @param init Initial value (default: 0)
     * @param align Alignment (default: 4, reserved for future)
     * @return CIntVar pointing to heap memory
     *
     * **Important**: Must call [free] to avoid memory leak.
     */
    fun int(init: Int = 0, align: Int = 4): CIntVar {
        val p = KMalloc.malloc(4)
        // align>=4 guaranteed by KMalloc default alignment; align parameter reserved for future aligned_alloc
        GlobalHeap.sw(p, init); return CIntVar(p)
    }
    
    /**
     * Allocate a double on the heap.
     *
     * @param init Initial value (default: 0.0)
     * @return CDoubleVar pointing to heap memory
     *
     * **Important**: Must call [free] to avoid memory leak.
     */
    fun double(init: Double = 0.0): CDoubleVar {
        val p = KMalloc.malloc(8)
        GlobalHeap.sdf(p, init); return CDoubleVar(p)
    }
    
    /**
     * Free a heap-allocated variable.
     *
     * Returns the memory to [KMalloc]. The variable becomes invalid after this call.
     *
     * @param v Variable to free (any CVar from CHeapVars)
     *
     * ## Example
     * ```kotlin
     * val x = CHeapVars.int(42)
     * // ... use x ...
     * CHeapVars.free(x)
     * // x is now invalid
     * ```
     */
    fun free(v: CVar) { KMalloc.free(v.addr) }
}
