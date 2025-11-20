Floating Types — C Parity
=========================

CDouble
- Value class over raw Long bits; exact IEEE‑754 binary64 behavior; uses Float64Math for bitwise ops.

CLongDouble (intent wrapper)
- Flavor enum: AUTO, DOUBLE64, EXTENDED80, IEEE128.
- DOUBLE64 delegates to CDouble; EXTENDED80/IEEE128 use CFloat128 core with rounding hooks (TODO) to avoid exceeding host precision.

CFloat128
- Double‑double placeholder with limb conversions; used for research and for long‑double emulation where needed.

Principle
- For “double”, we are always IEEE‑754 binary64 regardless of host quirks.
- For “long double”, intent drives behavior; future AUTO profile can probe host ABI.

