/**
 * # Actor System
 *
 * The actor system manages actor lifecycles, supervision, and message routing.
 * It provides the runtime environment for all actors in the system.
 */
package ai.solace.ember.actors

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.reflect.KClass

/**
 * Actor reference for sending messages to actors.
 */
interface ActorRef<M : Any> {
    /**
     * Send a message to the actor.
     */
    suspend fun send(message: M): Boolean
    
    /**
     * Stop the actor.
     */
    suspend fun stop()
}

/**
 * Internal actor reference implementation.
 */
internal class ActorRefImpl<M : Any>(
    private val actor: Actor<M>
) : ActorRef<M> {
    
    override suspend fun send(message: M): Boolean {
        return if (actor is AbstractActor<M>) {
            actor.send(message)
        } else {
            try {
                actor.mailbox.send(message)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
    
    override suspend fun stop() {
        actor.stop()
    }
}

/**
 * Actor system for managing actors and their lifecycles.
 */
class ActorSystem {
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val actors = mutableMapOf<String, ActorRef<*>>()
    
    /**
     * Create and start an actor.
     *
     * @param factory Factory function to create the actor
     * @param name Optional name for the actor (generated if not provided)
     * @return Actor reference for sending messages
     */
    suspend fun <M : Any> actorOf(
        factory: () -> Actor<M>,
        name: String? = null
    ): ActorRef<M> {
        val actor = factory()
        val actorName = name ?: generateActorName()
        
        val ref = ActorRefImpl(actor)
        actors[actorName] = ref
        
        // Start the actor in the system's scope
        scope.launch {
            actor.start()
        }
        
        return ref
    }
    
    /**
     * Get an actor reference by name.
     *
     * @param name The name of the actor
     * @return Actor reference if found, null otherwise
     */
    @Suppress("UNCHECKED_CAST")
    fun <M : Any> getActor(name: String): ActorRef<M>? {
        return actors[name] as? ActorRef<M>
    }
    
    /**
     * Stop an actor by name.
     *
     * @param name The name of the actor to stop
     * @return true if the actor was found and stopped, false otherwise
     */
    suspend fun stopActor(name: String): Boolean {
        val actor = actors.remove(name)
        return if (actor != null) {
            actor.stop()
            true
        } else {
            false
        }
    }
    
    /**
     * Shutdown the entire actor system.
     * This will stop all actors and cancel the system scope.
     */
    suspend fun shutdown() {
        // Stop all actors
        actors.values.forEach { it.stop() }
        actors.clear()
        
        // Cancel the system scope
        scope.cancel()
        scope.coroutineContext[Job]?.join()
    }
    
    /**
     * Generate a unique actor name.
     */
    private fun generateActorName(): String {
        return "actor-${kotlin.random.Random.nextLong()}-${kotlin.random.Random.nextInt(1000)}"
    }
}