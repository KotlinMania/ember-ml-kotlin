package ai.solace.ember.backend

import ai.solace.ember.backend.metal.MetalBackend

/**
 * Registry for backend implementations.
 * This class manages the available backends and allows switching between them.
 */
object BackendRegistry {
    // Map of backend names to backend implementations
    private val backends = mutableMapOf<String, Backend>()

    // The current backend
    private var currentBackend: Backend? = null

    /**
     * Registers a backend with the registry.
     *
     * @param name The name of the backend.
     * @param backend The backend implementation.
     */
    fun registerBackend(name: String, backend: Backend) {
        backends[name] = backend
    }

    /**
     * Gets a backend by name.
     *
     * @param name The name of the backend.
     * @return The backend implementation, or null if not found.
     */
    fun getBackend(name: String): Backend? {
        return backends[name]
    }

    /**
     * Sets the current backend.
     *
     * @param name The name of the backend to set as current.
     * @return True if the backend was set, false if the backend was not found.
     */
    fun setBackend(name: String): Boolean {
        val backend = backends[name] ?: return false
        currentBackend = backend
        return true
    }

    /**
     * Gets the current backend.
     *
     * @return The current backend.
     * @throws IllegalStateException if no backend is set.
     */
    fun getCurrentBackend(): Backend {
        return currentBackend ?: throw IllegalStateException("No backend is set")
    }

    /**
     * Gets a list of available backend names.
     *
     * @return A list of available backend names.
     */
    fun getAvailableBackends(): List<String> {
        return backends.keys.toList()
    }

    /**
     * Initializes the registry with the default backends.
     */
    fun initialize() {
        // Register the MegaTensorBackend
        registerBackend("mega", MegaTensorBackend())

        // Register Metal backend
        registerBackend("metal", MetalBackend())

        // Set the MegaTensorBackend as the default if no backend is set
        if (currentBackend == null) {
            setBackend("mega")
        }
    }
}

/**
 * Gets the current backend.
 *
 * @return The name of the current backend.
 * @throws IllegalStateException if no backend is set.
 */
fun getBackend(): String {
    val backend = BackendRegistry.getCurrentBackend()
    return BackendRegistry.getAvailableBackends().find { BackendRegistry.getBackend(it) === backend }
        ?: throw IllegalStateException("Current backend is not registered")
}

/**
 * Sets the backend to use.
 *
 * @param name The name of the backend to use.
 * @return True if the backend was set, false if the backend was not found.
 */
fun setBackend(name: String): Boolean {
    return BackendRegistry.setBackend(name)
}

/**
 * Automatically selects the best backend based on the available hardware.
 *
 * @return The name of the selected backend.
 */
fun autoSelectBackend(): String {
    // Try Metal backend first (highest performance on Apple platforms)
    val metalBackend = BackendRegistry.getBackend("metal") as? MetalBackend
    if (metalBackend?.isAvailable() == true) {
        BackendRegistry.setBackend("metal")
        return "metal"
    }

    // Fall back to MegaTensorBackend
    BackendRegistry.setBackend("mega")
    return "mega"
}

/**
 * Checks if a backend is available.
 *
 * @param name The name of the backend to check.
 * @return True if the backend is available, false otherwise.
 */
fun isBackendAvailable(name: String): Boolean {
    return BackendRegistry.getBackend(name) != null
}

/**
 * Gets a list of available backend names.
 *
 * @return A list of available backend names.
 */
fun getAvailableBackends(): List<String> {
    return BackendRegistry.getAvailableBackends()
}

// Initialize the registry
private val initializeRegistry = BackendRegistry.initialize()
