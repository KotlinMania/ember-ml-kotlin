package ai.solace.ember.backend.metal

import ai.solace.ember.tensor.common.DType

/**
 * Metal linear algebra operations including SVD implementation.
 * 
 * This object provides high-performance implementations of linear algebra operations
 * using Metal kernels, including the SVD decomposition ported from the MLX implementation.
 */
object MetalLinearAlgebra {
    
    /**
     * Singular Value Decomposition (SVD) using Metal kernels.
     * 
     * Computes the SVD of a matrix A = U * Σ * V^T using the power method
     * for singular vector estimation, implemented with Metal kernels for GPU acceleration.
     * 
     * @param matrix Input matrix tensor (2D)
     * @param fullMatrices If true, compute full U and V matrices
     * @param computeUv If true, compute U and V matrices; if false, only compute singular values
     * @param context Metal context for GPU operations
     * @return Array containing [U, Σ, V^T] tensors
     */
    fun svd(
        matrix: MetalTensor, 
        fullMatrices: Boolean, 
        computeUv: Boolean,
        context: MetalContext
    ): Array<Any> {
        require(matrix.shape.size == 2) { "SVD requires a 2D matrix" }
        
        val m = matrix.shape[0]
        val n = matrix.shape[1]
        val k = minOf(m, n)
        
        // Initialize result tensors
        val singularValues = MetalTensor.zeros(intArrayOf(k), matrix.dtype, context)
        
        val u = if (computeUv) {
            val uCols = if (fullMatrices) m else k
            MetalTensor.zeros(intArrayOf(m, uCols), matrix.dtype, context)
        } else {
            null
        }
        
        val vt = if (computeUv) {
            val vtRows = if (fullMatrices) n else k
            MetalTensor.zeros(intArrayOf(vtRows, n), matrix.dtype, context)
        } else {
            null
        }
        
        // Perform SVD computation using Metal kernels
        computeSvdWithMetal(matrix, singularValues, u, vt, context)
        
        return when {
            computeUv -> arrayOf(u!!, singularValues, vt!!)
            else -> arrayOf(singularValues)
        }
    }
    
    /**
     * Core SVD computation using Metal kernels.
     * 
     * This implements the power method for SVD using Metal kernels, similar to the
     * implementation in mlxtests/metal_kernel_method/svd_metal.py.
     */
    private fun computeSvdWithMetal(
        matrix: MetalTensor,
        singularValues: MetalTensor,
        u: MetalTensor?,
        vt: MetalTensor?,
        context: MetalContext
    ) {
        val m = matrix.shape[0]
        val n = matrix.shape[1]
        val k = minOf(m, n)
        val epsilon = 1e-6f
        
        // Create buffers for SVD computation parameters
        val paramsData = floatArrayOf(m.toFloat(), n.toFloat(), k.toFloat(), epsilon)
        val paramsBuffer = context.createBuffer(paramsData)
        
        // Create workspace buffers for iterative computation
        val workspaceSize = maxOf(m, n) * 4  // Space for temporary vectors
        val workspaceBuffer = context.createBuffer(workspaceSize * 4)  // 4 bytes per float
        
        // Prepare buffers list
        val buffers = mutableListOf(
            matrix.buffer,
            singularValues.buffer,
            paramsBuffer,
            workspaceBuffer
        )
        
        // Add U and V buffers if computing UV
        if (u != null) buffers.add(u.buffer)
        if (vt != null) buffers.add(vt.buffer)
        
        // Execute SVD Metal kernel
        val pipelineState = context.createComputePipelineState("svd_power_method")
        
        // Use a single threadgroup for the iterative power method
        val threadsPerThreadgroup = MetalSize(1)
        val threadgroupsPerGrid = MetalSize(1)
        
        context.executeKernel(pipelineState, buffers, threadsPerThreadgroup, threadgroupsPerGrid)
        context.synchronize()
    }
    
    /**
     * Estimates the dominant singular vector using the power method with Metal kernels.
     * 
     * This is a simplified version that computes only the first singular vector,
     * which can be used as a building block for full SVD.
     */
    fun svd1d(matrix: MetalTensor, epsilon: Float = 1e-6f, context: MetalContext): MetalTensor {
        require(matrix.shape.size == 2) { "svd1d requires a 2D matrix" }
        
        val m = matrix.shape[0]
        val n = matrix.shape[1]
        val k = minOf(m, n)
        
        // Create result vector
        val result = MetalTensor.random(intArrayOf(k, 1), matrix.dtype, context)
        
        // Normalize initial vector
        val norm = MetalOperations.norm(result, context)
        val normalizedResult = divide(result, norm, context)
        
        // Create parameter buffer
        val paramsData = floatArrayOf(m.toFloat(), n.toFloat(), k.toFloat(), epsilon)
        val paramsBuffer = context.createBuffer(paramsData)
        
        // Create workspace for power iteration
        val workspaceBuffer = context.createBuffer(k * 4)  // 4 bytes per float
        
        val buffers = listOf(
            matrix.buffer,
            normalizedResult.buffer,
            paramsBuffer,
            workspaceBuffer
        )
        
        // Execute power method Metal kernel
        val pipelineState = context.createComputePipelineState("svd_1d_power_method")
        val threadsPerThreadgroup = MetalSize(1)
        val threadgroupsPerGrid = MetalSize(1)
        
        context.executeKernel(pipelineState, buffers, threadsPerThreadgroup, threadgroupsPerGrid)
        context.synchronize()
        
        return normalizedResult
    }
    
    /**
     * Helper function to divide a tensor by a scalar.
     */
    private fun divide(tensor: MetalTensor, scalar: Float, context: MetalContext): MetalTensor {
        val result = MetalTensor.zeros(tensor.shape, tensor.dtype, context)
        val scalarBuffer = context.createBuffer(floatArrayOf(scalar))
        
        val pipelineState = context.createComputePipelineState("divide_scalar_float")
        val buffers = listOf(tensor.buffer, scalarBuffer, result.buffer)
        
        val threadsPerThreadgroup = MetalSize(256)
        val threadgroupsPerGrid = MetalSize((tensor.size + 255) / 256)
        
        context.executeKernel(pipelineState, buffers, threadsPerThreadgroup, threadgroupsPerGrid)
        
        return result
    }
    
    /**
     * Computes the QR decomposition using Metal kernels.
     * 
     * This can be used as part of more advanced SVD algorithms.
     */
    fun qr(matrix: MetalTensor, context: MetalContext): Pair<MetalTensor, MetalTensor> {
        require(matrix.shape.size == 2) { "QR decomposition requires a 2D matrix" }
        
        val m = matrix.shape[0]
        val n = matrix.shape[1]
        
        val q = MetalTensor.zeros(intArrayOf(m, n), matrix.dtype, context)
        val r = MetalTensor.zeros(intArrayOf(n, n), matrix.dtype, context)
        
        // Create dimensions buffer
        val dimsData = floatArrayOf(m.toFloat(), n.toFloat())
        val dimsBuffer = context.createBuffer(dimsData)
        
        val buffers = listOf(matrix.buffer, q.buffer, r.buffer, dimsBuffer)
        
        val pipelineState = context.createComputePipelineState("qr_decomposition")
        val threadsPerThreadgroup = MetalSize(16, 16)
        val threadgroupsPerGrid = MetalSize((n + 15) / 16, (m + 15) / 16)
        
        context.executeKernel(pipelineState, buffers, threadsPerThreadgroup, threadgroupsPerGrid)
        context.synchronize()
        
        return Pair(q, r)
    }
}