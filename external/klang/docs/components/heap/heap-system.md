GlobalHeap — Single-Heap Memory Model
====================================

Concept
- GlobalHeap is a growable ByteArray used as the program’s memory. Pointers are Int byte offsets into this array.
- All typed IO is little‑endian and consistent across Kotlin targets.

Key APIs (GlobalArrayHeap.kt)
- init(bytes): allocate backing ByteArray; used/reset/dispose manage lifetime.
- ensureCapacity(min): grows the heap preserving contents.
- malloc/calloc/free: simple bump allocator; KMalloc builds on top for general purpose.
- lb/sb, lh/sh, lw/sw, ld/sd: 8/16/32/64‑bit loads/stores, little‑endian.
- lwf/swf, ldf/sdf: Float/Double via raw bits.
- memcpy/memmove/memset: high‑throughput loops from FastMem.

Determinism
- All numeric reads/writes use explicit byte assembly; no platform endianness leaks.

Pointers
- CPointer<T> stores an Int address. Extension helpers provide index/load/store functions that delegate to GlobalHeap.

