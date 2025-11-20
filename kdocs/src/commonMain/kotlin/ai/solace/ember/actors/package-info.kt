/**
 * # Actor-Based Architecture for Machine Learning
 *
 * The `ai.solace.ember.actors` package provides the foundation for Ember ML Kotlin's 100% actor-based
 * machine learning platform. This architecture enables non-blocking IO and asynchronous communication
 * over Kotlin channels, making it ideal for high-performance machine learning applications.
 *
 * ## Overview
 *
 * Ember ML Kotlin uses the actor model as its core architectural pattern. In this model:
 * - Each component is an actor
 * - Actors communicate exclusively through message passing
 * - All operations are non-blocking
 * - State is encapsulated within actors
 * - Concurrency is managed through the actor system
 *
 * ## Key Components
 *
 * ### Actor System
 *
 * The actor system provides the runtime environment for actors:
 * - Actor creation and management
 * - Message delivery
 * - Supervision hierarchies
 * - Resource management
 *
 * ```kotlin
 * // Create an actor system
 * val system = ActorSystem()
 *
 * // Create an actor
 * val actor = system.actorOf<TensorActor>()
 *
 * // Send a message to the actor
 * actor.send(ComputeMessage(tensor1, tensor2))
 * ```
 *
 * ### Actor Types
 *
 * Ember ML Kotlin provides several specialized actor types:
 *
 * #### TensorActor
 *
 * Manages tensor operations and state:
 * - Handles tensor creation and manipulation
 * - Performs tensor computations
 * - Manages tensor memory
 *
 * #### ComputeActor
 *
 * Performs computational tasks:
 * - Matrix operations
 * - Neural network forward passes
 * - Gradient computations
 *
 * #### OptimizerActor
 *
 * Manages optimization processes:
 * - Parameter updates
 * - Learning rate scheduling
 * - Gradient processing
 *
 * #### IOActor
 *
 * Handles input/output operations:
 * - Data loading
 * - Model saving/loading
 * - Logging
 *
 * #### SupervisorActor
 *
 * Manages actor lifecycles:
 * - Error handling
 * - Restart strategies
 * - Resource management
 *
 * ## Message Passing
 *
 * All communication between actors happens through message passing over Kotlin channels:
 *
 * ```kotlin
 * // Define a message
 * sealed class TensorMessage
 * data class ComputeMessage(val a: EmberTensor, val b: EmberTensor) : TensorMessage()
 * data class ResultMessage(val result: EmberTensor) : TensorMessage()
 *
 * // Actor implementation
 * class TensorActor : Actor<TensorMessage> {
 *     override suspend fun receive(message: TensorMessage) {
 *         when (message) {
 *             is ComputeMessage -> {
 *                 val result = compute(message.a, message.b)
 *                 sender.send(ResultMessage(result))
 *             }
 *             // Handle other message types
 *         }
 *     }
 *
 *     private suspend fun compute(a: EmberTensor, b: EmberTensor): EmberTensor {
 *         // Perform computation
 *         return a + b
 *     }
 * }
 * ```
 *
 * ## Non-Blocking IO
 *
 * All IO operations in Ember ML Kotlin are non-blocking:
 *
 * ```kotlin
 * // Non-blocking data loading
 * val dataLoader = DataLoaderActor()
 * val dataChannel = dataLoader.loadData("path/to/data")
 *
 * // Process data as it becomes available
 * dataChannel.collect { batch ->
 *     // Process batch
 * }
 * ```
 *
 * ## Asynchronous Training
 *
 * The actor model enables fully asynchronous training:
 *
 * ```kotlin
 * // Create training actors
 * val trainer = system.actorOf<TrainerActor>()
 * val model = system.actorOf<ModelActor>()
 * val optimizer = system.actorOf<OptimizerActor>()
 * val dataLoader = system.actorOf<DataLoaderActor>()
 *
 * // Start training
 * trainer.send(StartTrainingMessage(model, optimizer, dataLoader))
 *
 * // Training progress is reported through messages
 * trainer.messages.collect { message ->
 *     when (message) {
 *         is ProgressMessage -> println("Progress: ${message.progress}%")
 *         is CompletedMessage -> println("Training completed")
 *         is ErrorMessage -> println("Error: ${message.error}")
 *     }
 * }
 * ```
 *
 * ## Benefits of Actor-Based Architecture
 *
 * The actor-based architecture provides several benefits for machine learning applications:
 *
 * 1. **Scalability**: Actors can be distributed across multiple threads, cores, or even machines
 * 2. **Resilience**: Supervisor hierarchies provide fault tolerance and recovery
 * 3. **Concurrency**: The message-passing model simplifies concurrent programming
 * 4. **Modularity**: Actors encapsulate state and behavior, promoting clean design
 * 5. **Responsiveness**: Non-blocking operations ensure the system remains responsive
 *
 * ## Implementation Notes
 *
 * - The actor implementation uses Kotlin coroutines and channels
 * - All actors are designed to work with Kotlin Native and other targets
 * - The architecture is optimized for machine learning workloads
 * - The system is designed to scale from small devices to large clusters
 */
package ai.solace.ember.actors
