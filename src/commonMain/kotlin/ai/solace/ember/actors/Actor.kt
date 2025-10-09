/**
 * # Actor Interface
 *
 * Base interface for all actors in the Ember ML Kotlin actor system.
 * Actors communicate exclusively through message passing and encapsulate their own state.
 *
 * @param M The type of messages this actor can receive
 */
package ai.solace.ember.actors

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope

/**
 * Base interface for all actors in the system.
 *
 * @param M The message type this actor handles
 */
interface Actor<M : Any> {
    
    /**
     * The mailbox channel for receiving messages.
     */
    val mailbox: Channel<M>
    
    /**
     * The coroutine scope for this actor.
     */
    val scope: CoroutineScope
    
    /**
     * Handle a received message.
     * This method is called for each message received by the actor.
     *
     * @param message The message to handle
     */
    suspend fun receive(message: M)
    
    /**
     * Start the actor's message processing loop.
     */
    suspend fun start()
    
    /**
     * Stop the actor and clean up resources.
     */
    suspend fun stop()
}