/**
 * # Supervision Strategy
 *
 * Defines how actors should be supervised and how failures should be handled.
 * Provides fault tolerance through restart strategies and error escalation.
 */
package ai.solace.ember.actors

/**
 * Supervision strategy for handling actor failures.
 */
sealed class SupervisionStrategy {
    /**
     * Restart the failed actor.
     */
    object Restart : SupervisionStrategy()
    
    /**
     * Stop the failed actor.
     */
    object Stop : SupervisionStrategy()
    
    /**
     * Escalate the failure to the parent supervisor.
     */
    object Escalate : SupervisionStrategy()
    
    /**
     * Resume the actor (ignore the failure).
     */
    object Resume : SupervisionStrategy()
}

/**
 * Supervisor actor that manages child actors and handles their failures.
 */
abstract class SupervisorActor<M : Message>(
    private val strategy: SupervisionStrategy = SupervisionStrategy.Restart,
    capacity: Int = kotlinx.coroutines.channels.Channel.UNLIMITED
) : AbstractActor<M>(capacity) {
    
    private val children = mutableMapOf<String, ActorRef<*>>()
    
    /**
     * Create a child actor under this supervisor.
     */
    suspend fun <CM : Any> createChild(
        factory: () -> Actor<CM>,
        name: String? = null
    ): ActorRef<CM> {
        val childName = name ?: generateChildName()
        
        val child = factory()
        val ref = ActorRefImpl(child)
        children[childName] = ref
        
        // Start the child actor asynchronously
        try {
            child.start()
        } catch (e: Exception) {
            handleChildFailure(childName, e)
        }
        
        return ref
    }
    
    /**
     * Stop a child actor.
     */
    suspend fun stopChild(name: String): Boolean {
        val child = children.remove(name)
        return if (child != null) {
            child.stop()
            true
        } else {
            false
        }
    }
    
    /**
     * Handle child actor failures according to the supervision strategy.
     */
    private suspend fun handleChildFailure(childName: String, error: Exception) {
        when (strategy) {
            SupervisionStrategy.Restart -> {
                println("Restarting child actor: $childName due to: ${error.message}")
                // Note: Restart logic would need the original factory function
                // For now, just remove the failed child
                children.remove(childName)
            }
            SupervisionStrategy.Stop -> {
                println("Stopping child actor: $childName due to: ${error.message}")
                stopChild(childName)
            }
            SupervisionStrategy.Escalate -> {
                println("Escalating failure from child actor: $childName")
                throw error
            }
            SupervisionStrategy.Resume -> {
                println("Resuming child actor: $childName after failure: ${error.message}")
                // Child continues running
            }
        }
    }
    
    /**
     * Stop all child actors when the supervisor stops.
     */
    override suspend fun cleanup() {
        super.cleanup()
        children.values.forEach { it.stop() }
        children.clear()
    }
    
    /**
     * Generate a unique name for a child actor.
     */
    private fun generateChildName(): String {
        return "child-${kotlin.random.Random.nextLong()}-${kotlin.random.Random.nextInt(1000)}"
    }
}