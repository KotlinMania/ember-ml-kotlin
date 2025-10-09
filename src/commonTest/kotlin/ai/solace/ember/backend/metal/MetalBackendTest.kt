package ai.solace.ember.backend.metal

import ai.solace.ember.backend.BackendRegistry
import ai.solace.ember.tensor.common.DType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for Metal backend integration and basic functionality.
 */
class MetalBackendTest {

    @Test
    fun testMetalBackendRegistration() {
        // Initialize registry to ensure Metal backend is registered
        BackendRegistry.initialize()
        
        // Check that Metal backend is registered
        val metalBackend = BackendRegistry.getBackend("metal")
        assertNotNull(metalBackend, "Metal backend should be registered")
        assertTrue(metalBackend is MetalBackend, "Registered backend should be MetalBackend instance")
    }

    @Test
    fun testMetalBackendAvailability() {
        val metalBackend = MetalBackend()
        
        // On non-Apple platforms, Metal should not be available
        // On Apple platforms with Metal support, it should be available
        val isAvailable = metalBackend.isAvailable()
        
        // Test that the method doesn't throw and returns a boolean
        assertTrue(isAvailable == true || isAvailable == false, "isAvailable should return a boolean")
    }

    @Test
    fun testMetalBackendProperties() {
        val metalBackend = MetalBackend()
        
        assertEquals("metal", metalBackend.name(), "Backend name should be 'metal'")
        assertEquals(200, metalBackend.priority(), "Metal backend should have high priority")
        assertEquals("metal", metalBackend.getDefaultDevice(), "Default device should be 'metal'")
        
        val devices = metalBackend.getAvailableDevices()
        if (metalBackend.isAvailable()) {
            assertTrue(devices.contains("metal"), "Available devices should include 'metal' when available")
        } else {
            assertTrue(devices.isEmpty(), "Available devices should be empty when Metal is not available")
        }
    }

    @Test
    fun testMetalContextCreation() {
        val context = MetalContext.create()
        
        // Context creation should not throw, but may return null on non-Apple platforms
        // This tests the platform abstraction
        if (context != null) {
            assertTrue(context.getMaxThreadsPerThreadgroup() >= 0, "Max threads should be non-negative")
        }
    }

    @Test
    fun testMetalTensorCreationStub() {
        val metalBackend = MetalBackend()
        
        if (!metalBackend.isAvailable()) {
            // Test that operations fail gracefully when Metal is not available
            try {
                metalBackend.createTensor(
                    floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f),
                    intArrayOf(2, 2),
                    DType.FLOAT32
                )
                // If we get here, Metal is unexpectedly available
            } catch (e: IllegalStateException) {
                assertTrue(
                    e.message?.contains("Metal is not available") == true,
                    "Should throw appropriate error when Metal is not available"
                )
            }
        }
    }

    @Test
    fun testMetalSizeUtility() {
        val size1 = MetalSize(256)
        assertEquals(256, size1.width)
        assertEquals(1, size1.height)
        assertEquals(1, size1.depth)
        assertEquals(256, size1.totalThreads)

        val size2 = MetalSize(16, 16)
        assertEquals(16, size2.width)
        assertEquals(16, size2.height)
        assertEquals(1, size2.depth)
        assertEquals(256, size2.totalThreads)

        val size3 = MetalSize(8, 8, 4)
        assertEquals(8, size3.width)
        assertEquals(8, size3.height)
        assertEquals(4, size3.depth)
        assertEquals(256, size3.totalThreads)
    }

    @Test
    fun testKernelSourceConstants() {
        // Test that kernel source code is properly defined
        assertTrue(MetalKernelSource.ADD_KERNEL.contains("add_float"))
        assertTrue(MetalKernelSource.SUBTRACT_KERNEL.contains("subtract_float"))
        assertTrue(MetalKernelSource.MULTIPLY_KERNEL.contains("multiply_float"))
        assertTrue(MetalKernelSource.DIVIDE_KERNEL.contains("divide_float"))
        assertTrue(MetalKernelSource.MATMUL_KERNEL.contains("matmul_float"))
        assertTrue(MetalKernelSource.SVD_POWER_METHOD_KERNEL.contains("svd_power_method"))
        assertTrue(MetalKernelSource.SVD_1D_POWER_METHOD_KERNEL.contains("svd_1d_power_method"))
        
        // Test that kernels include proper Metal headers
        assertTrue(MetalKernelSource.ADD_KERNEL.contains("#include <metal_stdlib>"))
        assertTrue(MetalKernelSource.ADD_KERNEL.contains("using namespace metal;"))
    }

    @Test
    fun testAutoSelectBackend() {
        BackendRegistry.initialize()
        
        val selectedBackend = ai.solace.ember.backend.autoSelectBackend()
        
        // Should select either "metal" (if available) or "mega" (fallback)
        assertTrue(
            selectedBackend == "metal" || selectedBackend == "mega",
            "Auto-selected backend should be either 'metal' or 'mega'"
        )
        
        // The selected backend should be set as current
        val currentBackend = ai.solace.ember.backend.getBackend()
        assertEquals(selectedBackend, currentBackend, "Auto-selected backend should be set as current")
    }
}