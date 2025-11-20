/**
 * # Utilities Module
 *
 * The `ai.solace.ember.utils` module provides utility functions and classes for working with
 * Ember ML Kotlin. These utilities include mathematical helpers, metrics, visualization tools,
 * and other common functionality.
 *
 * ## Math Utilities
 *
 * The `ai.solace.ember.utils.math` package provides mathematical utility functions.
 *
 * ```kotlin
 * import ai.solace.ember.utils.math.*
 *
 * // Calculate the factorial of a number
 * val result = factorial(5)  // 120
 *
 * // Calculate the binomial coefficient
 * val result = binomial(10, 3)  // 120
 *
 * // Calculate the sigmoid function
 * val result = sigmoid(2.0)  // 0.8807970779778823
 *
 * // Calculate the softmax function
 * val result = softmax(listOf(1.0, 2.0, 3.0))  // [0.09003057, 0.24472847, 0.66524096]
 *
 * // Calculate the log-sum-exp
 * val result = logSumExp(listOf(1.0, 2.0, 3.0))  // 3.407605964995589
 *
 * // Calculate the entropy
 * val result = entropy(listOf(0.1, 0.4, 0.5))  // 0.9433483923290392
 *
 * // Calculate the cross-entropy
 * val result = crossEntropy(listOf(0.1, 0.4, 0.5), listOf(0.2, 0.3, 0.5))  // 1.0296530140645737
 *
 * // Calculate the KL divergence
 * val result = klDivergence(listOf(0.1, 0.4, 0.5), listOf(0.2, 0.3, 0.5))  // 0.08630462173553453
 * ```
 *
 * ## Metrics
 *
 * The `ai.solace.ember.utils.metrics` package provides evaluation metrics for machine learning models.
 *
 * ```kotlin
 * import ai.solace.ember.utils.metrics.*
 *
 * // Calculate the accuracy
 * val accuracy = accuracy(yTrue, yPred)
 *
 * // Calculate the precision
 * val precision = precision(yTrue, yPred)
 *
 * // Calculate the recall
 * val recall = recall(yTrue, yPred)
 *
 * // Calculate the F1 score
 * val f1 = f1Score(yTrue, yPred)
 *
 * // Calculate the area under the ROC curve
 * val auc = auc(yTrue, yPred)
 *
 * // Calculate the mean squared error
 * val mse = meanSquaredError(yTrue, yPred)
 *
 * // Calculate the mean absolute error
 * val mae = meanAbsoluteError(yTrue, yPred)
 *
 * // Calculate the root mean squared error
 * val rmse = rootMeanSquaredError(yTrue, yPred)
 *
 * // Calculate the R-squared
 * val r2 = rSquared(yTrue, yPred)
 * ```
 *
 * ## Visualization
 *
 * The `ai.solace.ember.utils.visualization` package provides tools for visualizing data and models.
 *
 * ```kotlin
 * import ai.solace.ember.utils.visualization.*
 *
 * // Create a line plot
 * val plot = linePlot(x, y, title = "Line Plot", xLabel = "X", yLabel = "Y")
 * plot.show()
 *
 * // Create a scatter plot
 * val plot = scatterPlot(x, y, title = "Scatter Plot", xLabel = "X", yLabel = "Y")
 * plot.show()
 *
 * // Create a bar plot
 * val plot = barPlot(categories, values, title = "Bar Plot", xLabel = "Categories", yLabel = "Values")
 * plot.show()
 *
 * // Create a histogram
 * val plot = histogram(data, bins = 10, title = "Histogram", xLabel = "Value", yLabel = "Frequency")
 * plot.show()
 *
 * // Create a heatmap
 * val plot = heatmap(matrix, title = "Heatmap", xLabel = "X", yLabel = "Y")
 * plot.show()
 *
 * // Create a confusion matrix
 * val plot = confusionMatrix(yTrue, yPred, labels = listOf("Class 0", "Class 1", "Class 2"))
 * plot.show()
 *
 * // Create a ROC curve
 * val plot = rocCurve(yTrue, yPred)
 * plot.show()
 *
 * // Create a precision-recall curve
 * val plot = precisionRecallCurve(yTrue, yPred)
 * plot.show()
 * ```
 *
 * ## File Utilities
 *
 * The `ai.solace.ember.utils.io` package provides utilities for file I/O operations.
 *
 * ```kotlin
 * import ai.solace.ember.utils.io.*
 *
 * // Save a model to a file
 * saveModel(model, "model.bin")
 *
 * // Load a model from a file
 * val model = loadModel("model.bin")
 *
 * // Save data to a CSV file
 * saveToCsv(data, "data.csv")
 *
 * // Load data from a CSV file
 * val data = loadFromCsv("data.csv")
 *
 * // Save data to a JSON file
 * saveToJson(data, "data.json")
 *
 * // Load data from a JSON file
 * val data = loadFromJson("data.json")
 * ```
 *
 * ## Random Utilities
 *
 * The `ai.solace.ember.utils.random` package provides utilities for random number generation.
 *
 * ```kotlin
 * import ai.solace.ember.utils.random.*
 *
 * // Set the random seed
 * setSeed(42)
 *
 * // Generate a random integer
 * val randomInt = randomInt(0, 10)
 *
 * // Generate a random float
 * val randomFloat = randomFloat(0.0f, 1.0f)
 *
 * // Generate a random double
 * val randomDouble = randomDouble(0.0, 1.0)
 *
 * // Generate a random boolean
 * val randomBool = randomBoolean()
 *
 * // Generate a random element from a list
 * val randomElement = randomChoice(listOf(1, 2, 3, 4, 5))
 *
 * // Generate a random sample from a list
 * val randomSample = randomSample(listOf(1, 2, 3, 4, 5), 3)
 *
 * // Shuffle a list
 * val shuffledList = shuffle(listOf(1, 2, 3, 4, 5))
 * ```
 *
 * ## String Utilities
 *
 * The `ai.solace.ember.utils.string` package provides utilities for string manipulation.
 *
 * ```kotlin
 * import ai.solace.ember.utils.string.*
 *
 * // Tokenize a string
 * val tokens = tokenize("Hello, world!")  // ["Hello", ",", "world", "!"]
 *
 * // Join tokens into a string
 * val string = join(listOf("Hello", "world"), " ")  // "Hello world"
 *
 * // Pad a string
 * val paddedString = pad("Hello", 10, padChar = ' ', padLeft = true)  // "     Hello"
 *
 * // Truncate a string
 * val truncatedString = truncate("Hello, world!", 5)  // "Hello..."
 * ```
 */
package ai.solace.ember.utils
