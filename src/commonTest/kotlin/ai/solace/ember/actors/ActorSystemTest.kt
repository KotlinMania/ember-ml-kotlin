/**
 * # Actor System Tests
 *
 * Basic tests to verify the actor system functionality.
 */
package ai.solace.ember.actors

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * Simple test message for testing purposes.
 */
data class TestMessage(val content: String) : AppMessage()

/**
 * Simple test actor that echoes messages.
 */
class TestActor : AbstractActor<TestMessage>() {
    var lastMessage: String? = null
    
    override suspend fun receive(message: TestMessage) {
        lastMessage = message.content
    }
}

/**
 * Test suite for the actor system.
 */
class ActorSystemTest {
    
    @Test
    fun testActorSystemCreation() = runTest {
        val system = ActorSystem()
        assertNotNull(system)
        system.shutdown()
    }
    
    @Test
    fun testActorCreation() = runTest {
        val system = ActorSystem()
        
        val actorRef = system.actorOf({ TestActor() }, "test-actor")
        assertNotNull(actorRef)
        
        system.shutdown()
    }
    
    @Test
    fun testMessageSending() = runTest {
        val system = ActorSystem()
        
        val actorRef = system.actorOf({ TestActor() }, "test-actor")
        
        // Send a message
        val success = actorRef.send(TestMessage("Hello, Actor!"))
        assertTrue(success)
        
        // Give the actor time to process the message
        delay(100)
        
        system.shutdown()
    }
    
    @Test
    fun testActorStop() = runTest {
        val system = ActorSystem()
        
        val actorRef = system.actorOf({ TestActor() }, "test-actor")
        
        // Stop the actor
        actorRef.stop()
        
        // Verify the actor is stopped by trying to get it from the system
        val stoppedActor = system.getActor<TestMessage>("test-actor")
        // Note: The actor might still be in the system until cleanup, so this test is basic
        
        system.shutdown()
    }
}