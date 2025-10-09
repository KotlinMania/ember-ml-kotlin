package ai.solace.ember.backend.metal

/**
 * Metal context for managing Metal device and command queue.
 * 
 * This interface provides an abstraction over Metal's native functionality,
 * allowing for platform-specific implementations while maintaining a common API.
 */
interface MetalContext {
    
    /**
     * Creates a Metal buffer with the specified size.
     * 
     * @param size Size of the buffer in bytes
     * @return The created Metal buffer
     */
    fun createBuffer(size: Int): MetalBuffer
    
    /**
     * Creates a Metal buffer from existing data.
     * 
     * @param data The data to copy into the buffer
     * @return The created Metal buffer
     */
    fun createBuffer(data: FloatArray): MetalBuffer
    
    /**
     * Creates a compute pipeline state for the specified kernel function.
     * 
     * @param functionName Name of the Metal kernel function
     * @return The compute pipeline state
     */
    fun createComputePipelineState(functionName: String): MetalComputePipelineState
    
    /**
     * Executes a Metal kernel with the specified parameters.
     * 
     * @param pipelineState The compute pipeline state
     * @param buffers List of input and output buffers
     * @param threadgroupSize Size of each threadgroup
     * @param gridSize Size of the compute grid
     */
    fun executeKernel(
        pipelineState: MetalComputePipelineState,
        buffers: List<MetalBuffer>,
        threadgroupSize: MetalSize,
        gridSize: MetalSize
    )
    
    /**
     * Gets the maximum number of threads per threadgroup.
     */
    fun getMaxThreadsPerThreadgroup(): Int
    
    /**
     * Synchronizes execution (waits for all commands to complete).
     */
    fun synchronize()
    
    companion object {
        /**
         * Creates a Metal context if available on the current platform.
         * 
         * @return MetalContext instance or null if Metal is not available
         */
        fun create(): MetalContext? {
            return try {
                StubMetalContext()
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Represents Metal thread dimensions.
 */
data class MetalSize(
    val width: Int,
    val height: Int = 1,
    val depth: Int = 1
) {
    /**
     * Gets the total number of threads.
     */
    val totalThreads: Int get() = width * height * depth
}

/**
 * Represents a Metal buffer for storing data on GPU.
 */
interface MetalBuffer {
    
    /**
     * Gets the size of the buffer in bytes.
     */
    val size: Int
    
    /**
     * Copies data from the buffer to a FloatArray.
     */
    fun toFloatArray(): FloatArray
    
    /**
     * Copies data from a FloatArray to the buffer.
     */
    fun fromFloatArray(data: FloatArray)
    
    /**
     * Releases the buffer resources.
     */
    fun release()
}

/**
 * Represents a Metal compute pipeline state.
 */
interface MetalComputePipelineState {
    
    /**
     * Gets the maximum number of threads per threadgroup for this pipeline.
     */
    val maxTotalThreadsPerThreadgroup: Int
    
    /**
     * Releases the pipeline state resources.
     */
    fun release()
}

/**
 * Stub implementation of MetalContext for platforms where Metal is not available.
 */
internal class StubMetalContext : MetalContext {
    
    override fun createBuffer(size: Int): MetalBuffer {
        return StubMetalBuffer(size)
    }
    
    override fun createBuffer(data: FloatArray): MetalBuffer {
        return StubMetalBuffer(data.size * 4)
    }
    
    override fun createComputePipelineState(functionName: String): MetalComputePipelineState {
        return StubMetalComputePipelineState()
    }
    
    override fun executeKernel(
        pipelineState: MetalComputePipelineState,
        buffers: List<MetalBuffer>,
        threadgroupSize: MetalSize,
        gridSize: MetalSize
    ) {
        // No-op for stub implementation
    }
    
    override fun getMaxThreadsPerThreadgroup(): Int {
        return 1024
    }
    
    override fun synchronize() {
        // No-op for stub implementation
    }
}

/**
 * Stub implementation of MetalBuffer.
 */
internal class StubMetalBuffer(override val size: Int) : MetalBuffer {
    
    private val data = FloatArray(size / 4)
    
    override fun toFloatArray(): FloatArray {
        return data.copyOf()
    }
    
    override fun fromFloatArray(data: FloatArray) {
        data.copyInto(this.data, 0, 0, minOf(data.size, this.data.size))
    }
    
    override fun release() {
        // No-op for stub implementation
    }
}

/**
 * Stub implementation of MetalComputePipelineState.
 */
internal class StubMetalComputePipelineState : MetalComputePipelineState {
    
    override val maxTotalThreadsPerThreadgroup: Int = 1024
    
    override fun release() {
        // No-op for stub implementation
    }
}