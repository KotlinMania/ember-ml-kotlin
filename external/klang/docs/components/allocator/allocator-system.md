KMalloc — Heap Allocator
========================

Goals
- Pure Kotlin malloc/calloc/realloc/free on top of GlobalHeap.
- 16‑byte alignment; fast small allocations; coalescing on free; splitting on allocation.

Layout
- Header (4 bytes): tag = (size<<1) | inUse.
- Footer (4 bytes): duplicate tag. Allows constant‑time prev‑chunk via footer.
- Payload: user data. For free chunks, first 4 bytes hold next pointer in free list.

Data structures
- Size classes ≤ 1024 bytes (16‑byte steps): array of free‑list heads.
- Large free list: singly‑linked via payload[0..4). Head stored in KMalloc.
- Top pointer brk: next address for bump allocation.

Operations
- malloc(n): normalize to ≥16 and align 16, search bins→large list (first‑fit) with splitting; else bump brk.
- calloc(c, s): malloc(c*s), then memset to 0.
- free(p): mark free, coalesce next/prev if free, then push into the appropriate list.
- realloc(p, n): if shrink → maybe split tail; if grow → alloc new, memcpy, free old.

Notes
- removeFromFreeList carefully unlinks from either a bin or large list.
- Coalescing uses header/footer to find neighbors without scanning.
- Never read user payload when chunk is free except the embedded next pointer.

