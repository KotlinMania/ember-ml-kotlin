/**
 * # Neural Network (nn) Module
 *
 * The `ai.solace.ember.nn` module provides neural network components for building and training
 * machine learning models in Ember ML Kotlin. It includes a variety of layers, activation functions,
 * and utilities for constructing complex neural network architectures.
 *
 * ## Activations
 *
 * The `ai.solace.ember.nn.activations` package provides activation functions for neural networks.
 *
 * ```kotlin
 * import ai.solace.ember.nn.activations.*
 *
 * // ReLU activation
 * val relu = ReLU()
 * val output = relu(input)
 *
 * // Sigmoid activation
 * val sigmoid = Sigmoid()
 * val output = sigmoid(input)
 *
 * // Tanh activation
 * val tanh = Tanh()
 * val output = tanh(input)
 *
 * // Leaky ReLU activation
 * val leakyRelu = LeakyReLU(alpha = 0.01)
 * val output = leakyRelu(input)
 *
 * // ELU activation
 * val elu = ELU(alpha = 1.0)
 * val output = elu(input)
 *
 * // SELU activation
 * val selu = SELU()
 * val output = selu(input)
 *
 * // Softmax activation
 * val softmax = Softmax(axis = -1)
 * val output = softmax(input)
 *
 * // Softplus activation
 * val softplus = Softplus()
 * val output = softplus(input)
 *
 * // Swish activation
 * val swish = Swish()
 * val output = swish(input)
 * ```
 *
 * ## Containers
 *
 * The `ai.solace.ember.nn.containers` package provides container modules for organizing neural network components.
 *
 * ```kotlin
 * import ai.solace.ember.nn.containers.*
 * import ai.solace.ember.nn.modules.*
 * import ai.solace.ember.nn.activations.*
 *
 * // Sequential container
 * val model = Sequential(
 *     Dense(inputDim = 784, outputDim = 128),
 *     ReLU(),
 *     Dense(inputDim = 128, outputDim = 64),
 *     ReLU(),
 *     Dense(inputDim = 64, outputDim = 10),
 *     Softmax()
 * )
 *
 * // Functional API
 * val input = Input(shape = intArrayOf(784))
 * val hidden1 = Dense(outputDim = 128)(input)
 * val act1 = ReLU()(hidden1)
 * val hidden2 = Dense(outputDim = 64)(act1)
 * val act2 = ReLU()(hidden2)
 * val output = Dense(outputDim = 10)(act2)
 * val model = Model(inputs = input, outputs = output)
 * ```
 *
 * ## Features
 *
 * The `ai.solace.ember.nn.features` package provides feature extraction and processing components.
 *
 * ```kotlin
 * import ai.solace.ember.nn.features.*
 *
 * // Terabyte feature extractor
 * val extractor = TerabyteFeatureExtractor()
 * val features = extractor.extract(data)
 *
 * // Temporal stride processor
 * val processor = TemporalStrideProcessor()
 * val processed = processor.process(data)
 *
 * // Generic feature engineer
 * val engineer = GenericFeatureEngineer()
 * val engineered = engineer.engineer(data)
 *
 * // Generic type detector
 * val detector = GenericTypeDetector()
 * val types = detector.detect(data)
 * ```
 *
 * ## Modules
 *
 * The `ai.solace.ember.nn.modules` package provides neural network modules.
 *
 * ```kotlin
 * import ai.solace.ember.nn.modules.*
 *
 * // Dense layer
 * val dense = Dense(inputDim = 784, outputDim = 128)
 * val output = dense(input)
 *
 * // Dropout layer
 * val dropout = Dropout(rate = 0.5)
 * val output = dropout(input)
 *
 * // BatchNormalization layer
 * val batchNorm = BatchNormalization()
 * val output = batchNorm(input)
 *
 * // Conv1D layer
 * val conv1d = Conv1D(
 *     filters = 32,
 *     kernelSize = 3,
 *     strides = 1,
 *     padding = "same",
 *     activation = "relu"
 * )
 * val output = conv1d(input)
 *
 * // Conv2D layer
 * val conv2d = Conv2D(
 *     filters = 32,
 *     kernelSize = intArrayOf(3, 3),
 *     strides = intArrayOf(1, 1),
 *     padding = "same",
 *     activation = "relu"
 * )
 * val output = conv2d(input)
 *
 * // MaxPooling1D layer
 * val maxPool1d = MaxPooling1D(poolSize = 2, strides = 2)
 * val output = maxPool1d(input)
 *
 * // MaxPooling2D layer
 * val maxPool2d = MaxPooling2D(poolSize = intArrayOf(2, 2), strides = intArrayOf(2, 2))
 * val output = maxPool2d(input)
 * ```
 *
 * ## RNN Modules
 *
 * The `ai.solace.ember.nn.modules.rnn` package provides recurrent neural network modules.
 *
 * ```kotlin
 * import ai.solace.ember.nn.modules.rnn.*
 *
 * // SimpleRNN layer
 * val rnn = SimpleRNN(units = 128, returnSequences = true)
 * val output = rnn(input)
 *
 * // LSTM layer
 * val lstm = LSTM(units = 128, returnSequences = true)
 * val output = lstm(input)
 *
 * // GRU layer
 * val gru = GRU(units = 128, returnSequences = true)
 * val output = gru(input)
 *
 * // Bidirectional wrapper
 * val bidirectional = Bidirectional(LSTM(units = 128))
 * val output = bidirectional(input)
 *
 * // LTC (Liquid Time Constant) layer
 * val ltc = LTC(units = 128, returnSequences = true)
 * val output = ltc(input)
 *
 * // CfC (Closed-form Continuous-time) layer
 * val cfc = CfC(units = 128, returnSequences = true)
 * val output = cfc(input)
 * ```
 *
 * ## Quantum Modules
 *
 * The `ai.solace.ember.nn.modules.rnn.quantum` package provides quantum neural network modules.
 *
 * ```kotlin
 * import ai.solace.ember.nn.modules.rnn.quantum.*
 *
 * // QuantumRNN layer
 * val qrnn = QuantumRNN(units = 128, returnSequences = true)
 * val output = qrnn(input)
 *
 * // QuantumLSTM layer
 * val qlstm = QuantumLSTM(units = 128, returnSequences = true)
 * val output = qlstm(input)
 *
 * // QuantumGRU layer
 * val qgru = QuantumGRU(units = 128, returnSequences = true)
 * val output = qgru(input)
 * ```
 *
 * ## Wiring
 *
 * The `ai.solace.ember.nn.modules.wiring` package provides network connectivity patterns.
 *
 * ```kotlin
 * import ai.solace.ember.nn.modules.wiring.*
 *
 * // NCP (Neural Circuit Policy) wiring
 * val wiring = NCPWiring(
 *     inputSize = 10,
 *     hiddenSize = 64,
 *     outputSize = 5,
 *     motorSensoryWeights = 0.2f,
 *     sensoryMotorWeights = 0.3f,
 *     hiddenHiddenWeights = 0.1f
 * )
 *
 * // Custom wiring
 * val wiring = CustomWiring(
 *     inputSize = 10,
 *     hiddenSize = 64,
 *     outputSize = 5,
 *     sparsity = 0.8f
 * )
 * ```
 */
package ai.solace.ember.nn
