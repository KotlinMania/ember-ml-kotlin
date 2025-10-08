package ai.solace.klang.bitwise

// Symmetric operator support when Float is on the left-hand side

operator fun Float.plus(rhs: CFloat32): CFloat32 =
    CFloat32.fromBits(Float32Math.addBits(this.toRawBits(), rhs.toBits()))

operator fun Float.minus(rhs: CFloat32): CFloat32 =
    CFloat32.fromBits(Float32Math.subBits(this.toRawBits(), rhs.toBits()))

operator fun Float.times(rhs: CFloat32): CFloat32 =
    CFloat32.fromBits(Float32Math.mulBits(this.toRawBits(), rhs.toBits()))

operator fun Float.div(rhs: CFloat32): CFloat32 =
    CFloat32.fromBits(Float32Math.divBits(this.toRawBits(), rhs.toBits()))
