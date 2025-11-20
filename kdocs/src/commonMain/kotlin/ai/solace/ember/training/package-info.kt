/**
 * # Training Module
 *
 * The `ai.solace.ember.training` module provides utilities for training machine learning models
 * in Ember ML Kotlin. It includes optimizers, learning rate schedulers, callbacks, and other
 * components for efficient model training.
 *
 * ## Optimizers
 *
 * The `ai.solace.ember.training.optimizers` package provides optimization algorithms for training
 * neural networks.
 *
 * ```kotlin
 * import ai.solace.ember.training.optimizers.*
 *
 * // Stochastic Gradient Descent (SGD) optimizer
 * val sgd = SGD(learningRate = 0.01, momentum = 0.9, nesterov = true)
 *
 * // Adam optimizer
 * val adam = Adam(learningRate = 0.001, beta1 = 0.9, beta2 = 0.999, epsilon = 1e-7)
 *
 * // RMSprop optimizer
 * val rmsprop = RMSprop(learningRate = 0.001, rho = 0.9, epsilon = 1e-7)
 *
 * // Adagrad optimizer
 * val adagrad = Adagrad(learningRate = 0.01, epsilon = 1e-7)
 *
 * // Adadelta optimizer
 * val adadelta = Adadelta(learningRate = 1.0, rho = 0.95, epsilon = 1e-7)
 *
 * // Adamax optimizer
 * val adamax = Adamax(learningRate = 0.002, beta1 = 0.9, beta2 = 0.999, epsilon = 1e-7)
 *
 * // Nadam optimizer
 * val nadam = Nadam(learningRate = 0.002, beta1 = 0.9, beta2 = 0.999, epsilon = 1e-7)
 *
 * // FTRL optimizer
 * val ftrl = FTRL(learningRate = 0.001, learningRatePower = -0.5, initialAccumulatorValue = 0.1)
 * ```
 *
 * ## Learning Rate Schedulers
 *
 * The `ai.solace.ember.training.schedulers` package provides learning rate schedulers for
 * adjusting the learning rate during training.
 *
 * ```kotlin
 * import ai.solace.ember.training.schedulers.*
 *
 * // Step decay scheduler
 * val stepDecay = StepDecay(initialLearningRate = 0.1, dropFactor = 0.5, dropEvery = 10)
 *
 * // Exponential decay scheduler
 * val expDecay = ExponentialDecay(initialLearningRate = 0.1, decayRate = 0.96, decaySteps = 100)
 *
 * // Polynomial decay scheduler
 * val polyDecay = PolynomialDecay(initialLearningRate = 0.1, endLearningRate = 0.0001, power = 1.0, decaySteps = 100)
 *
 * // Cosine decay scheduler
 * val cosineDecay = CosineDecay(initialLearningRate = 0.1, decaySteps = 100)
 *
 * // Cosine decay with restarts scheduler
 * val cosineDecayRestarts = CosineDecayWithRestarts(initialLearningRate = 0.1, firstDecaySteps = 100, t_mul = 2.0, m_mul = 1.0)
 *
 * // One cycle scheduler
 * val oneCycle = OneCycle(maxLearningRate = 0.1, steps = 100, divFactor = 25.0, finalDivFactor = 10000.0)
 *
 * // Reduce on plateau scheduler
 * val reduceOnPlateau = ReduceOnPlateau(factor = 0.1, patience = 10, minLearningRate = 0.0001)
 * ```
 *
 * ## Callbacks
 *
 * The `ai.solace.ember.training.callbacks` package provides callbacks for customizing the
 * training process.
 *
 * ```kotlin
 * import ai.solace.ember.training.callbacks.*
 *
 * // Model checkpoint callback
 * val checkpoint = ModelCheckpoint(
 *     filepath = "model_{epoch:02d}_{val_loss:.2f}.bin",
 *     monitor = "val_loss",
 *     saveFrequency = "epoch",
 *     saveFormat = "binary"
 * )
 *
 * // Early stopping callback
 * val earlyStopping = EarlyStopping(
 *     monitor = "val_loss",
 *     minDelta = 0.001,
 *     patience = 10,
 *     mode = "min"
 * )
 *
 * // Learning rate scheduler callback
 * val lrScheduler = LearningRateSchedulerCallback(scheduler = stepDecay)
 *
 * // Tensor board callback
 * val tensorBoard = TensorBoardCallback(logDir = "logs")
 *
 * // CSV logger callback
 * val csvLogger = CSVLogger(filename = "training.csv", separator = ",", append = false)
 *
 * // Lambda callback
 * val lambda = LambdaCallback(
 *     onEpochBegin = { epoch, logs -> println("Epoch $epoch started") },
 *     onEpochEnd = { epoch, logs -> println("Epoch $epoch ended with loss ${logs["loss"]}") }
 * )
 * ```
 *
 * ## Training Loop
 *
 * The `ai.solace.ember.training` package provides utilities for creating and customizing
 * training loops.
 *
 * ```kotlin
 * import ai.solace.ember.training.*
 * import ai.solace.ember.nn.containers.Sequential
 * import ai.solace.ember.nn.modules.Dense
 * import ai.solace.ember.nn.activations.ReLU
 * import ai.solace.ember.nn.activations.Softmax
 * import ai.solace.ember.training.optimizers.Adam
 * import ai.solace.ember.training.callbacks.ModelCheckpoint
 * import ai.solace.ember.training.callbacks.EarlyStopping
 *
 * // Create a model
 * val model = Sequential(
 *     Dense(inputDim = 784, outputDim = 128),
 *     ReLU(),
 *     Dense(inputDim = 128, outputDim = 64),
 *     ReLU(),
 *     Dense(inputDim = 64, outputDim = 10),
 *     Softmax()
 * )
 *
 * // Compile the model
 * model.compile(
 *     optimizer = Adam(learningRate = 0.001),
 *     loss = "categoricalCrossentropy",
 *     metrics = listOf("accuracy")
 * )
 *
 * // Train the model
 * model.fit(
 *     x = trainData,
 *     y = trainLabels,
 *     batchSize = 32,
 *     epochs = 10,
 *     validation = Pair(valData, valLabels),
 *     callbacks = listOf(
 *         ModelCheckpoint(filepath = "model_{epoch:02d}_{val_loss:.2f}.bin"),
 *         EarlyStopping(monitor = "val_loss", patience = 3)
 *     )
 * )
 *
 * // Evaluate the model
 * val evaluation = model.evaluate(testData, testLabels, batchSize = 32)
 * println("Test loss: ${evaluation["loss"]}")
 * println("Test accuracy: ${evaluation["accuracy"]}")
 *
 * // Make predictions
 * val predictions = model.predict(newData, batchSize = 32)
 * ```
 *
 * ## Custom Training Loops
 *
 * The `ai.solace.ember.training` package also supports custom training loops for more
 * fine-grained control over the training process.
 *
 * ```kotlin
 * import ai.solace.ember.training.*
 * import ai.solace.ember.tensor.common.EmberTensor
 * import ai.solace.ember.ops.math.*
 * import ai.solace.ember.training.optimizers.Adam
 *
 * // Create a model and optimizer
 * val model = createModel()
 * val optimizer = Adam(learningRate = 0.001)
 *
 * // Custom training loop
 * for (epoch in 0 until 10) {
 *     var epochLoss = 0.0
 *     var batchCount = 0
 *
 *     // Iterate over batches
 *     for ((batchX, batchY) in dataLoader) {
 *         // Forward pass
 *         val predictions = model(batchX)
 *         val loss = categoricalCrossentropy(batchY, predictions)
 *
 *         // Backward pass
 *         val gradients = gradients(loss, model.parameters)
 *
 *         // Update weights
 *         optimizer.applyGradients(gradients, model.parameters)
 *
 *         epochLoss += loss.value
 *         batchCount++
 *     }
 *
 *     // Print epoch results
 *     println("Epoch ${epoch + 1}/10, Loss: ${epochLoss / batchCount}")
 * }
 * ```
 */
package ai.solace.ember.training
