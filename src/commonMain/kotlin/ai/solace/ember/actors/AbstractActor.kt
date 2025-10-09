/**
 * # Abstract Actor Base Class
 *
 * Base implementation for actors in the Ember ML Kotlin actor system.
 * Provides default implementation for message handling and lifecycle management.
 */
package ai.solace.ember.actors

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/**
 * Abstract base class for actors providing common functionality.
 *
 * @param M The message type this actor handles
 * @param capacity The capacity of the actor's mailbox channel
 */
abstract class AbstractActor<M : Any>(
    capacity: Int = Channel.UNLIMITED
) : Actor<M> {

    override val mailbox: Channel<M> = Channel(capacity)
    override val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var job: Job? = null
    private var isRunning = false

    /**
     * Start the actor's message processing loop.
     */
    override suspend fun start() {
        if (isRunning) return

        isRunning = true
        job = scope.launch {
            try {
                @OptIn(DelicateCoroutinesApi::class)
                while (isActive && !mailbox.isClosedForSend) {
                    try {
                        val message = mailbox.receive()
                        receive(message)
                    } catch (e: Exception) {
                        handleError(e)
                    }
                }
            } finally {
                cleanup()
            }
        }
    }

    /**
     * Stop the actor and clean up resources.
     */
    override suspend fun stop() {
        if (!isRunning) return

        isRunning = false
        mailbox.close()
        job?.cancel()
        job?.join()
        scope.cancel()
    }

    /**
     * Send a message to this actor.
     *
     * @param message The message to send
     * @return true if the message was sent successfully, false otherwise
     */
    suspend fun send(message: M): Boolean {
        return try {
            mailbox.send(message)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Handle errors that occur during message processing.
     * Override this method to implement custom error handling.
     *
     * @param error The error that occurred
     */
    protected open suspend fun handleError(error: Exception) {
        // Default error handling - log error if possible
        println("Actor error: ${error.message}")
    }

    /**
     * Cleanup resources when the actor stops.
     * Override this method to implement custom cleanup logic.
     */
    protected open suspend fun cleanup() {
        // Default cleanup - do nothing
    }
}
