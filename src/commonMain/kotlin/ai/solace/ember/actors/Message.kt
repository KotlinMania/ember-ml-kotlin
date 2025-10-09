/**
 * # Message Types
 *
 * Base message types and interfaces for the actor system.
 * All messages should be immutable and serializable.
 */
package ai.solace.ember.actors

/**
 * Base interface for all actor messages.
 * Messages should be immutable data classes.
 */
interface Message

/**
 * System messages for actor lifecycle management.
 */
sealed class SystemMessage : Message {
    /**
     * Message to start an actor.
     */
    object Start : SystemMessage()
    
    /**
     * Message to stop an actor.
     */
    object Stop : SystemMessage()
    
    /**
     * Message indicating an actor has failed.
     */
    data class Failed(val error: Throwable) : SystemMessage()
    
    /**
     * Message to restart an actor.
     */
    object Restart : SystemMessage()
}

/**
 * Base class for application-specific messages.
 */
abstract class AppMessage : Message

/**
 * Message asking for a response.
 */
interface Request<T : Any> : Message {
    val replyTo: ActorRef<T>?
}

/**
 * Message containing a response.
 */
data class Response<T>(val value: T) : Message