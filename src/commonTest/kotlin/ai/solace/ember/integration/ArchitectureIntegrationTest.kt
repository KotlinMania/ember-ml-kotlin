package ai.solace.ember.integration

import ai.solace.ember.nn.layers.Dense
import ai.solace.ember.nn.activations.ReLU
import ai.solace.ember.tensor.common.EmberTensor
import ai.solace.ember.tensor.common.EmberShape
import ai.solace.ember.tensor.common.DType
import ai.solace.ember.backend.BackendRegistry
import kotlin.test.*

/**
 * Integration tests for the architecture components.
 */
class ArchitectureIntegrationTest {

    @BeforeTest
    fun setup() {
        BackendRegistry.initialize()
    }

    @Test
    fun testTensorBroadcasting() {
        val shape1 = EmberShape.of(2, 3)
        val shape2 = EmberShape.of(3)

        assertTrue(shape1.isBroadcastableWith(shape2))

        val broadcastedShape = shape1.broadcastWith(shape2)
        assertEquals(EmberShape.of(2, 3), broadcastedShape)
    }

    @Test
    fun testShapeUtilities() {
        val shape = EmberShape.of(2, 3, 4)
        assertEquals(3, shape.size)
        assertEquals(24, shape.totalSize())
        assertEquals(2, shape[0])
        assertEquals(3, shape[1])
        assertEquals(4, shape[2])
    }

    @Test
    fun testNeuralNetworkLayerCreation() {
        // Create a dense layer
        val dense = Dense(inputSize = 3, outputSize = 2)

        // Check that parameters exist
        val params = dense.parameters()
        assertTrue(params.containsKey("weight"))
        assertTrue(params.containsKey("bias"))

        val weights = params["weight"]!!
        assertEquals(EmberShape.of(3, 2), weights.shape)

        val bias = params["bias"]!!
        assertEquals(EmberShape.of(2), bias.shape)
    }

    @Test
    fun testActivationFunctions() {
        // Test that activation functions can be created
        val relu = ReLU()
        val params = relu.parameters()
        assertTrue(params.isEmpty()) // Activations should have no parameters

        // Test training mode toggle
        assertTrue(relu.training) // Should start in training mode
        relu.eval()
        assertFalse(relu.training)
        relu.train()
        assertTrue(relu.training)
    }

    @Test
    fun testMetalBackendRegistration() {
        // Test that backend registry knows about Metal backend
        val availableBackends = BackendRegistry.getAvailableBackends()

        // Metal backend should now be in the list since we're registering it in initialize()
        assertTrue(availableBackends.contains("metal"))

        // We should also have the mega backend
        assertTrue(availableBackends.contains("mega"))

        // Test current backend
        val currentBackend = BackendRegistry.getCurrentBackend()
        assertNotNull(currentBackend)
    }

    @Test
    fun testBroadcastingEdgeCases() {
        // Test various broadcasting scenarios
        val shape1 = EmberShape.of(1)
        val shape2 = EmberShape.of(5)
        assertTrue(shape1.isBroadcastableWith(shape2))
        assertEquals(EmberShape.of(5), shape1.broadcastWith(shape2))

        val shape3 = EmberShape.of(3, 1)
        val shape4 = EmberShape.of(1, 4)
        assertTrue(shape3.isBroadcastableWith(shape4))
        assertEquals(EmberShape.of(3, 4), shape3.broadcastWith(shape4))

        // Test incompatible shapes
        val shape5 = EmberShape.of(3)
        val shape6 = EmberShape.of(4)
        assertFalse(shape5.isBroadcastableWith(shape6))

        assertFailsWith<IllegalArgumentException> {
            shape5.broadcastWith(shape6)
        }
    }
}
