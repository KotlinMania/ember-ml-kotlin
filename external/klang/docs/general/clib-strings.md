CLib, CString, and Fast Paths
=============================

CLib (mem/CLib.kt)
- strlen/strnlen/strcmp/strncmp/strcpy/strncpy/strchr/memchr/memcmp implemented over GlobalHeap.
- Semantics mirror libc (e.g., memcpy has UB on overlap; memmove is overlap‑safe).

CString (mem/CString.kt)
- read/write/strlenz/strdup helpers for heap‑resident NUL‑terminated strings.

FastMem and FastStringMem
- musl‑style word‑at‑a‑time loops for memset/memcpy/memmove and string ops.
- Detect zero bytes with classic (x - 0x0101...) & ~x & 0x8080... technique.
- All loads/stores are assembled little‑endian to be portable across targets.

Guideline
- Prefer these routines over ad‑hoc loops; they’re tested and tuned for this heap.

