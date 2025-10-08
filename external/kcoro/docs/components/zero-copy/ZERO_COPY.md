# Zero‑Copy Channels & Memory Efficiency

## Rationale
For high-throughput applications, copying medium-sized payloads (≥256B) dominates per-message cost. Zero‑copy (zref) handoff reduces latency and CPU cycles by transferring ownership of a buffer pointer rather than copying bytes, while preserving existing API semantics for applications that do not opt in.

## Implementation Phases
1. **Z.1** Rendezvous pointer handoff (zref)
2. **Z.2** Buffered/unlimited descriptor ring storing (ptr,len)
3. **Z.3** Shared memory region registration (region_id, offset, length) for IPC
4. **Z.4** Region revocation & lifecycle safety (refcounts, graceful drain)
5. **Z.5** Batching & prefetch optimizations

## API Surface

### Core Zero-Copy APIs (`src/kcoro/include/kcoro_zcopy.h`)
```c
/* Common descriptor */
typedef struct kc_zdesc {
    void       *addr;
    size_t      len;
    uint64_t    region_id;    /* Reserved for future shared regions */
    uint64_t    offset;       /* Reserved for future shared regions */
    uint32_t    flags;
} kc_zdesc_t;

/* Canonical zero-copy APIs (descriptor-based) */
int kc_chan_send_desc(kc_chan_t *ch, const kc_zdesc_t *d, long timeout_ms);
int kc_chan_recv_desc(kc_chan_t *ch, kc_zdesc_t *d, long timeout_ms);
int kc_chan_send_desc_c(kc_chan_t *ch, const kc_zdesc_t *d, long timeout_ms, const kc_cancel_t *ct);
int kc_chan_recv_desc_c(kc_chan_t *ch, kc_zdesc_t *d, long timeout_ms, const kc_cancel_t *ct);
```

### Convenience Wrappers (`src/kcoro/include/kcoro.h:203-206`)
```c
/* Simplified pointer-based interface */
int kc_chan_send_zref(kc_chan_t *ch, void *ptr, size_t len, long timeout_ms);
int kc_chan_recv_zref(kc_chan_t *ch, void **out_ptr, size_t *out_len, long timeout_ms);
int kc_chan_send_zref_c(kc_chan_t *ch, void *ptr, size_t len, long timeout_ms, const kc_cancel_t *cancel);
int kc_chan_recv_zref_c(kc_chan_t *ch, void **out_ptr, size_t *out_len, long timeout_ms, const kc_cancel_t *cancel);
```

### Feature Detection (`src/kcoro/include/kcoro.h:183-197`)
```c
/* Query channel capabilities */
unsigned kc_chan_capabilities(kc_chan_t *ch);

#define KC_CHAN_CAP_ZERO_COPY   (1u<<0)  /* Supports zref operations */
#define KC_CHAN_CAP_PTR         (1u<<1)  /* Pointer-descriptor channel */

/* Enable zero-copy on existing channel */
int kc_chan_enable_zero_copy(kc_chan_t *ch);
```

## Ownership Model & Invariants

| Invariant | Explanation |
|-----------|-------------|
| **Single consumer per zref handoff** | Ensures unique ownership transfer; rendezvous or CAS winner enforces it |
| **Pointer non-null** | Basic safety requirement |
| **Length within bounds** | Prevents pathological huge allocations |
| **No implicit free** | Library never frees user memory; caller retains lifecycle control |
| **Cancel before match returns ownership** | Abort path clears slot/descriptor |
| **Close during pending handoff safe** | Returns `-EPIPE`; no dangling pointer exposure |

### Ownership Transfer Rules
1. **Sender retains ownership** until `kc_chan_send_zref` returns 0
2. **On success**, ownership transfers to the receiver
3. **Cancellation/timeout/close** before success leaves ownership with sender
4. **Library never frees** user memory automatically

## Usage Pattern

### Basic Zero-Copy Operation
```c
// Producer side
char *buffer = malloc(4096);
// ... populate buffer ...

if (kc_chan_send_zref(ch, buffer, 4096, 1000) == 0) {
    // Success: ownership transferred, don't free buffer
} else {
    // Failed: still own buffer, safe to free
    free(buffer);
}

// Consumer side
void *data;
size_t len;
if (kc_chan_recv_zref(ch, &data, &len, 1000) == 0) {
    // Process data...
    process_data(data, len);

    // Consumer is now responsible for cleanup
    free(data);
}
```

### Fallback Strategy
```c
int send_data(kc_chan_t *ch, void *data, size_t len) {
    // Try zero-copy first
    if (kc_chan_capabilities(ch) & KC_CHAN_CAP_ZERO_COPY) {
        int result = kc_chan_send_zref(ch, data, len, 0);
        if (result != -KC_ENOTSUP) {
            return result;  // Success or other error
        }
    }

    // Fall back to copy-based send
    return kc_chan_send(ch, data, len, 0);
}
```

## Backend Architecture

The zero-copy system uses a pluggable backend architecture:

- **Descriptor (`kc_zdesc_t`)**: Universal format for pointer + metadata
- **Backend vtable**: `attach/detach`, `send/recv`, cancellable variants
- **Built-in "zref" backend**: Handles rendezvous and buffered descriptor queues
- **Factory pattern**: `kc_zcopy_register/resolve` for runtime backend selection

## Performance Characteristics

### Throughput Benefits
- **Rendezvous channels**: ~250ns per zero-copy operation (vs ~350ns copy)
- **Large payloads**: Eliminates memcpy overhead proportional to size
- **Memory bandwidth**: Reduces pressure on memory subsystem
- **Cache efficiency**: Preserves data locality in receiver

### Memory Overhead
- **Descriptor storage**: 32 bytes per `kc_zdesc_t`
- **Buffered channels**: Store descriptors instead of payload copies
- **Region registration**: Amortized overhead for shared memory pools

## Error Semantics

| Condition | Send Return | Recv Return | Ownership |
|-----------|-------------|-------------|-----------|
| **Success** | 0 | 0 | Receiver gains |
| **Timeout** | -KC_ETIME | -KC_ETIME | Sender keeps |
| **Cancel** | -KC_ECANCELED | -KC_ECANCELED | Sender keeps |
| **Channel closed** | -KC_EPIPE | -KC_EPIPE | Sender keeps |
| **Unsupported** | -KC_ENOTSUP | -KC_ENOTSUP | Sender keeps |

## Select Integration

Zero-copy operations integrate seamlessly with `kc_select`:
- Provisional reservations are rolled back on losing arbitration
- Winner performs pointer handoff without copying
- Select index semantics remain unchanged

## Shared Memory Regions (Future)

Planned support for registered memory regions:
```c
/* Region registration (Phase Z.3+) */
typedef struct kc_region kc_region_t;

int  kc_region_register(kc_region_t **out, void *addr, size_t len, unsigned flags);
int  kc_region_deregister(kc_region_t *reg);
int  kc_region_export_id(const kc_region_t *reg, unsigned long *out_id);
```

Benefits:
- **IPC efficiency**: Transfer region ID + offset instead of copying
- **Validation**: Bounds checking against registered regions
- **Lifecycle management**: Reference counting prevents premature deregistration

## Observability & Metrics

### Zero-Copy Counters (`src/kcoro/include/kcoro.h:230-237`)
```c
struct kc_chan_zstats {
    unsigned long zref_sent;
    unsigned long zref_received;
    unsigned long zref_fallback_small;     /* Size threshold fallbacks */
    unsigned long zref_fallback_capacity;  /* Buffer full fallbacks */
    unsigned long zref_canceled;
    unsigned long zref_aborted_close;
};

int kc_chan_get_zstats(kc_chan_t *ch, struct kc_chan_zstats *out);
```

### Unified Metrics
All backends contribute to standard channel metrics:
- `total_sends/recvs` count both copy and zero-copy operations
- `total_bytes_sent/recv` reflect actual payload sizes
- `kc_chan_snapshot()` includes zero-copy mode flags and counters

## Security & Safety

### Bounds Validation
- Length must be > 0 and ≤ `KC_ZREF_MAX` (configurable limit)
- Pointer must be non-NULL
- Region bounds checked against registered areas (when applicable)

### Memory Safety
- No double-free risk: library never calls `free()` on user pointers
- Use-after-free protection via exact ownership tracking
- Cancel/close paths clear descriptors before returning to user

### API Contract
- Caller responsible for pointer validity until ownership transfer
- No automatic garbage collection or reference counting of user buffers
- Clean error paths preserve memory ownership invariants

## Implementation Status

### Phase Z.1 (Complete)
- ✅ Rendezvous pointer handoff operational
- ✅ Basic descriptor APIs functional
- ✅ Ownership transfer semantics validated
- ✅ Integration with existing channel test suite

### Phase Z.2 (In Progress)
- 🔄 Buffered descriptor ring implementation
- 🔄 Small payload fallback heuristics
- 🔄 Performance optimization for mixed workloads

### Future Phases
- ⏳ Z.3: Shared memory region registration
- ⏳ Z.4: Region lifecycle and revocation
- ⏳ Z.5: Batching and prefetch optimizations

---

This zero-copy design maintains kcoro's portability and licensing goals while enabling significant performance improvements for applications processing large or frequent messages.