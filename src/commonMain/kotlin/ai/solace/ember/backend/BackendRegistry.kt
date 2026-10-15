package ai.solace.ember.backend

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
    fun getBackend(name: String): Backend? = backends[name]

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
        ensureBackendsInitialized()
        return currentBackend ?: throw IllegalStateException("No backend is set")
    }

    /**
     * Gets a list of available backend names.
     *
     * @return A list of available backend names.
     */
    fun getAvailableBackends(): List<String> {
        ensureBackendsInitialized()
        return backends.keys.toList()
    }

    private var initialized = false

    internal fun ensureBackendsInitialized() {
        if (initialized) return
        initialized = true
        initializePlatformBackends()
    }
}

/**
 * Platform-specific backend registration. Each target registers the concrete
 * backends it ships (e.g. macOS registers Metal and a default CPU backend) and, when
 * appropriate, sets a default current backend.
 */
expect fun initializePlatformBackends()

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
    BackendRegistry.ensureBackendsInitialized()
    return BackendRegistry.setBackend(name)
}

/**
 * Automatically selects the best backend based on the available hardware.
 *
 * @return The name of the selected backend.
 */
expect fun autoSelectBackend(): String

/**
 * Checks if a backend is available.
 *
 * @param name The name of the backend to check.
 * @return True if the backend is available, false otherwise.
 */
fun isBackendAvailable(name: String): Boolean {
    BackendRegistry.ensureBackendsInitialized()
    return BackendRegistry.getBackend(name) != null
}

/**
 * Gets a list of available backend names.
 *
 * @return A list of available backend names.
 */
fun getAvailableBackends(): List<String> = BackendRegistry.getAvailableBackends()
