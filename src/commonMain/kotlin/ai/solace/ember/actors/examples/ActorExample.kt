/**
 * # Actor System Example
 *
 * Simple example demonstrating the actor system in action.
 * This shows how to create actors, send messages, and handle responses.
 */
package ai.solace.ember.actors.examples

import ai.solace.ember.actors.*
import kotlinx.coroutines.*

/**
 * Example message types for computation
 */
sealed class ComputeMessage : AppMessage() {
    data class Add(val a: Double, val b: Double, val replyTo: ActorRef<ComputeResponse>) : ComputeMessage()
    data class Multiply(val a: Double, val b: Double, val replyTo: ActorRef<ComputeResponse>) : ComputeMessage()
}

/**
 * Response messages
 */
sealed class ComputeResponse : AppMessage() {
    data class Result(val value: Double) : ComputeResponse()
    data class Error(val message: String) : ComputeResponse()
}

/**
 * Simple calculator actor
 */
class CalculatorActor : AbstractActor<ComputeMessage>() {
    
    override suspend fun receive(message: ComputeMessage) {
        when (message) {
            is ComputeMessage.Add -> {
                val result = message.a + message.b
                message.replyTo.send(ComputeResponse.Result(result))
            }
            is ComputeMessage.Multiply -> {
                val result = message.a * message.b
                message.replyTo.send(ComputeResponse.Result(result))
            }
        }
    }
}

/**
 * Actor that collects results
 */
class CollectorActor : AbstractActor<ComputeResponse>() {
    private val results = mutableListOf<Double>()
    
    override suspend fun receive(message: ComputeResponse) {
        when (message) {
            is ComputeResponse.Result -> {
                results.add(message.value)
                println("Collected result: ${message.value}")
            }
            is ComputeResponse.Error -> {
                println("Error: ${message.message}")
            }
        }
    }
    
    fun getResults(): List<Double> = results.toList()
}

/**
 * Example usage of the actor system
 */
suspend fun actorSystemExample() {
    val system = ActorSystem()
    
    try {
        // Create actors
        val calculator = system.actorOf({ CalculatorActor() }, "calculator")
        val collector = system.actorOf({ CollectorActor() }, "collector")
        
        // Send some computation requests
        calculator.send(ComputeMessage.Add(10.0, 5.0, collector))
        calculator.send(ComputeMessage.Multiply(3.0, 4.0, collector))
        calculator.send(ComputeMessage.Add(1.0, 1.0, collector))
        
        // Wait a bit for processing
        delay(500)
        
        println("Actor system example completed")
        
    } finally {
        system.shutdown()
    }
}