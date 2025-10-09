package ai.solace.ember.actors

import ai.solace.ember.tensor.common.EmberTensor
import ai.solace.ember.tensor.common.EmberShape
import kotlinx.coroutines.channels.SendChannel

/**
 * Base class for all tensor-related messages.
 */
sealed class TensorMessage

/**
 * Request to add two tensors.
 */
data class AddTensorMessage(
    val a: EmberTensor,
    val b: EmberTensor,
    val responseChannel: SendChannel<TensorResponse>? = null
) : TensorMessage()

/**
 * Request to multiply two tensors.
 */
data class MultiplyTensorMessage(
    val a: EmberTensor,
    val b: EmberTensor,
    val responseChannel: SendChannel<TensorResponse>? = null
) : TensorMessage()

/**
 * Request to perform matrix multiplication.
 */
data class MatmulTensorMessage(
    val a: EmberTensor,
    val b: EmberTensor,
    val responseChannel: SendChannel<TensorResponse>? = null
) : TensorMessage()

/**
 * Request to reshape a tensor.
 */
data class ReshapeTensorMessage(
    val tensor: EmberTensor,
    val newShape: EmberShape,
    val responseChannel: SendChannel<TensorResponse>? = null
) : TensorMessage()

/**
 * Base class for tensor responses.
 */
sealed class TensorResponse

data class TensorResultMessage(val result: EmberTensor) : TensorResponse()
data class TensorErrorMessage(val error: Throwable) : TensorResponse()

/**
 * Actor that handles tensor operations in a non-blocking coroutine-safe way.
 */
class TensorActor : AbstractActor<TensorMessage>() {

    override suspend fun receive(message: TensorMessage) {
        when (message) {
            is AddTensorMessage -> {
                try {
                    val result = message.a + message.b
                    message.responseChannel?.send(TensorResultMessage(result))
                } catch (e: Exception) {
                    message.responseChannel?.send(TensorErrorMessage(e))
                }
            }
            is MultiplyTensorMessage -> {
                try {
                    val result = message.a * message.b
                    message.responseChannel?.send(TensorResultMessage(result))
                } catch (e: Exception) {
                    message.responseChannel?.send(TensorErrorMessage(e))
                }
            }
            is MatmulTensorMessage -> {
                try {
                    val result = message.a.matmul(message.b)
                    message.responseChannel?.send(TensorResultMessage(result))
                } catch (e: Exception) {
                    message.responseChannel?.send(TensorErrorMessage(e))
                }
            }
            is ReshapeTensorMessage -> {
                try {
                    val reshaped = message.tensor.reshape(message.newShape)
                    val result = if (reshaped is EmberTensor) reshaped 
                        else throw IllegalStateException("Reshape operation did not return EmberTensor")
                    message.responseChannel?.send(TensorResultMessage(result))
                } catch (e: Exception) {
                    message.responseChannel?.send(TensorErrorMessage(e))
                }
            }
        }
    }

    // Remove the handle function since we're handling each case directly
}