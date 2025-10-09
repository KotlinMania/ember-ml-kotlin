/**
 * # Operations (ops) Module
 *
 * The `ai.solace.ember.ops` module provides the primary, backend-agnostic interface for fundamental operations
 * in Ember ML Kotlin. Through a dynamic dispatch system, it exposes functions implemented by the currently active
 * backend, ensuring a consistent API regardless of the underlying computation library.
 *
 * ## Important Notes
 *
 * - **Input Handling:** The `ops` functions accept various tensor-like inputs (native backend tensors, `EmberTensor`,
 *   `Parameter`, arrays, Kotlin lists/scalars). Backend implementations automatically handle input conversion and
 *   object unwrapping to access the native tensor data needed for computation.
 *
 * - **Return Types:** Functions within the `ops` module return **native backend tensors** wrapped in `EmberTensor`
 *   instances for a consistent user experience.
 *
 * ## Core Mathematical Operations
 *
 * ```kotlin
 * import ai.solace.ember.ops.math.*
 *
 * // Element-wise addition of tensors
 * val result = add(x, y)
 *
 * // Element-wise subtraction of tensors
 * val result = subtract(x, y)
 *
 * // Element-wise multiplication of tensors
 * val result = multiply(x, y)
 *
 * // Element-wise division of tensors
 * val result = divide(x, y)
 *
 * // Element-wise floor division of tensors
 * val result = floorDivide(x, y)
 *
 * // Element-wise remainder of division
 * val result = mod(x, y)
 *
 * // Dot product of tensors
 * val result = dot(x, y)
 *
 * // Matrix multiplication of tensors
 * val result = matmul(x, y)
 *
 * // Element-wise exponential of tensor
 * val result = exp(x)
 *
 * // Element-wise natural logarithm of tensor
 * val result = log(x)
 *
 * // Element-wise base-10 logarithm of tensor
 * val result = log10(x)
 *
 * // Element-wise base-2 logarithm of tensor
 * val result = log2(x)
 *
 * // Element-wise power function
 * val result = pow(x, y)
 *
 * // Element-wise square root of tensor
 * val result = sqrt(x)
 *
 * // Element-wise square of tensor
 * val result = square(x)
 *
 * // Element-wise absolute value of tensor
 * val result = abs(x)
 *
 * // Element-wise negation of tensor
 * val result = negative(x)
 *
 * // Element-wise sign of tensor
 * val result = sign(x)
 *
 * // Element-wise clipping of tensor values
 * val result = clip(x, minVal, maxVal)
 * ```
 *
 * ## Trigonometric Functions
 *
 * ```kotlin
 * import ai.solace.ember.ops.math.*
 *
 * // Element-wise sine of tensor
 * val result = sin(x)
 *
 * // Element-wise cosine of tensor
 * val result = cos(x)
 *
 * // Element-wise tangent of tensor
 * val result = tan(x)
 *
 * // Element-wise hyperbolic sine of tensor
 * val result = sinh(x)
 *
 * // Element-wise hyperbolic cosine of tensor
 * val result = cosh(x)
 *
 * // Element-wise hyperbolic tangent of tensor
 * val result = tanh(x)
 * ```
 *
 * ## Comparison Operations
 *
 * ```kotlin
 * import ai.solace.ember.ops.comparison.*
 *
 * // Element-wise equality comparison
 * val result = equal(x, y)
 *
 * // Element-wise inequality comparison
 * val result = notEqual(x, y)
 *
 * // Element-wise less-than comparison
 * val result = less(x, y)
 *
 * // Element-wise less-than-or-equal comparison
 * val result = lessEqual(x, y)
 *
 * // Element-wise greater-than comparison
 * val result = greater(x, y)
 *
 * // Element-wise greater-than-or-equal comparison
 * val result = greaterEqual(x, y)
 *
 * // Element-wise logical AND
 * val result = logicalAnd(x, y)
 *
 * // Element-wise logical OR
 * val result = logicalOr(x, y)
 *
 * // Element-wise logical NOT
 * val result = logicalNot(x)
 *
 * // Element-wise logical XOR
 * val result = logicalXor(x, y)
 *
 * // Returns whether all elements are close
 * val result = allClose(x, y, rtol = 1e-5, atol = 1e-8)
 *
 * // Returns whether each element is close
 * val result = isClose(x, y, rtol = 1e-5, atol = 1e-8)
 *
 * // Test whether all elements evaluate to True
 * val result = all(x, axis = null, keepDims = false)
 *
 * // Test whether any elements evaluate to True
 * val result = any(x, axis = null, keepDims = false)
 *
 * // Return elements chosen from x or y depending on condition
 * val result = where(condition, x, y)
 *
 * // Test element-wise for NaN
 * val result = isNan(x)
 * ```
 *
 * ## Device Operations
 *
 * ```kotlin
 * import ai.solace.ember.ops.device.*
 *
 * // Move tensor to the specified device
 * val result = toDevice(x, device)
 *
 * // Get the device of a tensor
 * val device = getDevice(x)
 *
 * // Get a list of available devices
 * val devices = getAvailableDevices()
 *
 * // Get memory usage for the specified device
 * val memoryUsage = memoryUsage(device)
 *
 * // Get detailed memory information for the specified device
 * val memoryInfo = memoryInfo(device)
 *
 * // Synchronize computation on the specified device (backend-dependent)
 * synchronize(device)
 *
 * // Set the default device for the current backend
 * setDefaultDevice(device)
 *
 * // Get the default device for the current backend
 * val defaultDevice = getDefaultDevice()
 *
 * // Check if a specific device (e.g., 'cuda', 'mps') is available
 * val isAvailable = isAvailable(deviceName)
 * ```
 *
 * ## I/O Operations
 *
 * ```kotlin
 * import ai.solace.ember.ops.io.*
 *
 * // Save object to file (backend-specific serialization)
 * save(obj, path)
 *
 * // Load object from file (backend-specific serialization)
 * val obj = load(path)
 * ```
 *
 * ## Loss Operations
 *
 * ```kotlin
 * import ai.solace.ember.ops.loss.*
 *
 * // Mean squared error loss
 * val loss = mse(yTrue, yPred)
 *
 * // Mean absolute error loss
 * val loss = meanAbsoluteError(yTrue, yPred)
 *
 * // Binary crossentropy loss
 * val loss = binaryCrossentropy(yTrue, yPred, fromLogits = false)
 *
 * // Categorical crossentropy loss
 * val loss = categoricalCrossentropy(yTrue, yPred, fromLogits = false)
 *
 * // Sparse categorical crossentropy loss
 * val loss = sparseCategoricalCrossentropy(yTrue, yPred, fromLogits = false)
 *
 * // Huber loss
 * val loss = huberLoss(yTrue, yPred, delta = 1.0)
 *
 * // Logarithm of the hyperbolic cosine loss
 * val loss = logCoshLoss(yTrue, yPred)
 * ```
 *
 * ## Vector & FFT Operations
 *
 * ```kotlin
 * import ai.solace.ember.ops.vector.*
 * import ai.solace.ember.ops.fft.*
 *
 * // Normalize a vector or matrix
 * val result = normalizeVector(x, axis)
 *
 * // Compute energy stability of a vector
 * val result = computeEnergyStability(x, axis)
 *
 * // Compute interference strength between vectors
 * val result = computeInterferenceStrength(x, y)
 *
 * // Compute phase coherence between vectors
 * val result = computePhaseCoherence(x, y)
 *
 * // Compute partial interference between vectors
 * val result = partialInterference(x, y, mask)
 *
 * // Compute Euclidean distance between vectors
 * val result = euclideanDistance(x, y)
 *
 * // Compute cosine similarity between vectors
 * val result = cosineSimilarity(x, y)
 *
 * // Apply exponential decay to a vector
 * val result = exponentialDecay(x, rate = 0.1)
 *
 * // Compute the one-dimensional discrete Fourier Transform
 * val result = fft(x, n, axis = -1)
 *
 * // Compute the one-dimensional inverse discrete Fourier Transform
 * val result = ifft(x, n, axis = -1)
 *
 * // Compute the two-dimensional discrete Fourier Transform
 * val result = fft2(x, s, axes = intArrayOf(-2, -1))
 *
 * // Compute the two-dimensional inverse discrete Fourier Transform
 * val result = ifft2(x, s, axes = intArrayOf(-2, -1))
 * ```
 *
 * ## Backend Management
 *
 * ```kotlin
 * import ai.solace.ember.backend.*
 *
 * // Get the name of the current active backend
 * val backend = getBackend()
 *
 * // Set the active backend (e.g., 'js', 'native')
 * setBackend(backendName)
 *
 * // Automatically select and set the best backend based on hardware
 * autoSelectBackend()
 * ```
 *
 * ## Notes
 *
 * - All operations are backend-agnostic and work with any backend set via `setBackend`.
 * - The operations follow a consistent API across different backends.
 * - Most operations support broadcasting, similar to NumPy and other array libraries.
 * - For tensor creation and manipulation, use the `ai.solace.ember.tensor` module.
 * - For statistical operations (e.g., mean, std, var), use the `ai.solace.ember.ops.stats` module.
 * - For linear algebra operations (e.g., svd, inv, det), use the `ai.solace.ember.ops.linearalg` module.
 * - For activation functions, use the `ai.solace.ember.nn.activations` module.
 * - For feature extraction operations/factories, use the `ai.solace.ember.nn.features` module.
 */
package ai.solace.ember.ops
