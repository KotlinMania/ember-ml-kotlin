package ai.solace.ember.tensor.common

/**
 * Represents the shape of a tensor.
 * A shape is a list of dimensions that define the size of the tensor along each axis.
 *
 * @property dimensions The dimensions of the tensor.
 */
class EmberShape(val dimensions: IntArray) {
    /**
     * The number of dimensions in the shape.
     */
    val size: Int
        get() = dimensions.size

    /**
     * Gets the dimension at the specified index.
     *
     * @param index The index of the dimension to get.
     * @return The dimension at the specified index.
     */
    operator fun get(index: Int): Int = dimensions[index]

    /**
     * Returns a string representation of the shape.
     *
     * @return A string representation of the shape.
     */
    override fun toString(): String = dimensions.contentToString()

    /**
     * Checks if this shape is equal to another object.
     *
     * @param other The object to compare with.
     * @return True if the shapes are equal, false otherwise.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmberShape) return false
        return dimensions.contentEquals(other.dimensions)
    }

    /**
     * Returns a hash code for this shape.
     *
     * @return A hash code for this shape.
     */
    override fun hashCode(): Int = dimensions.contentHashCode()

    /**
     * Calculates the total number of elements in the tensor.
     *
     * @return The total number of elements.
     */
    fun totalSize(): Int = dimensions.fold(1) { acc, dim -> acc * dim }

    /**
     * Checks if this shape is compatible for broadcasting with another shape.
     *
     * @param other The other shape to check compatibility with.
     * @return True if the shapes are compatible for broadcasting, false otherwise.
     */
    fun isBroadcastableWith(other: EmberShape): Boolean {
        val thisReversed = dimensions.reversedArray()
        val otherReversed = other.dimensions.reversedArray()
        val maxLength = maxOf(thisReversed.size, otherReversed.size)
        
        for (i in 0 until maxLength) {
            val thisDim = if (i < thisReversed.size) thisReversed[i] else 1
            val otherDim = if (i < otherReversed.size) otherReversed[i] else 1
            
            if (thisDim != otherDim && thisDim != 1 && otherDim != 1) {
                return false
            }
        }
        return true
    }

    /**
     * Broadcasts this shape with another shape.
     *
     * @param other The other shape to broadcast with.
     * @return The broadcasted shape.
     * @throws IllegalArgumentException if the shapes are not compatible for broadcasting.
     */
    fun broadcastWith(other: EmberShape): EmberShape {
        if (!isBroadcastableWith(other)) {
            throw IllegalArgumentException("Shapes $this and $other are not compatible for broadcasting")
        }
        
        val thisReversed = dimensions.reversedArray()
        val otherReversed = other.dimensions.reversedArray()
        val maxLength = maxOf(thisReversed.size, otherReversed.size)
        val result = IntArray(maxLength)
        
        for (i in 0 until maxLength) {
            val thisDim = if (i < thisReversed.size) thisReversed[i] else 1
            val otherDim = if (i < otherReversed.size) otherReversed[i] else 1
            result[i] = maxOf(thisDim, otherDim)
        }
        
        return EmberShape(result.reversedArray())
    }

    companion object {
        /**
         * Creates a shape from a list of dimensions.
         *
         * @param dims The dimensions of the tensor.
         * @return A new shape with the specified dimensions.
         */
        fun of(vararg dims: Int): EmberShape = EmberShape(dims)

        /**
         * Broadcasts multiple shapes together.
         *
         * @param shapes The shapes to broadcast.
         * @return The broadcasted shape.
         * @throws IllegalArgumentException if any shapes are not compatible for broadcasting.
         */
        fun broadcast(vararg shapes: EmberShape): EmberShape {
            return shapes.reduce { acc, shape -> acc.broadcastWith(shape) }
        }
    }
}
