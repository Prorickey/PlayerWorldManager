package tech.bedson.playerworldmanager.utils

import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Logger

/**
 * A wrapper around Bukkit's logger that adds debug logging capabilities.
 * Debug messages are only logged when debug mode is enabled in config.yml.
 *
 * Usage:
 * ```
 * val debugLogger = DebugLogger(plugin, "WorldManager")
 * debugLogger.debug("Processing world creation: name=$name, type=$type, owner=$ownerUuid")
 * debugLogger.info("World created successfully")
 * ```
 */
class DebugLogger(
    private val plugin: JavaPlugin,
    private val tag: String
) {
    companion object {
        // Cache debug state to avoid repeated config lookups
        private var debugEnabled: Boolean? = null
        private var lastConfigCheck: Long = 0
        private const val CONFIG_CACHE_MS = 5000L // Re-check config every 5 seconds

        /**
         * Force refresh of debug state from config.
         * Call this when config is reloaded.
         */
        fun refreshDebugState() {
            debugEnabled = null
            lastConfigCheck = 0
        }
    }

    private val bukkitLogger: Logger = plugin.logger

    /**
     * Check if debug mode is enabled in config.
     * Caches the result for performance.
     */
    private fun isDebugEnabled(): Boolean {
        val now = System.currentTimeMillis()
        if (debugEnabled == null || now - lastConfigCheck > CONFIG_CACHE_MS) {
            debugEnabled = plugin.config.getBoolean("debug", false)
            lastConfigCheck = now
        }
        return debugEnabled!!
    }

    /**
     * Log a debug message with the tag prefix.
     * Only logs when debug mode is enabled in config.yml.
     *
     * @param message The debug message to log
     */
    fun debug(message: String) {
        if (isDebugEnabled()) {
            bukkitLogger.info("[DEBUG][$tag] $message")
        }
    }

    /**
     * Log a debug message with additional context data.
     * Automatically formats key-value pairs for easy reading.
     *
     * @param message The debug message
     * @param context Key-value pairs of contextual data
     */
    fun debug(message: String, vararg context: Pair<String, Any?>) {
        if (isDebugEnabled()) {
            val contextStr = context.joinToString(", ") { (k, v) -> "$k=$v" }
            bukkitLogger.info("[DEBUG][$tag] $message | $contextStr")
        }
    }

    /**
     * Log method entry with parameters.
     *
     * @param methodName The method being entered
     * @param params Method parameters as key-value pairs
     */
    fun debugMethodEntry(methodName: String, vararg params: Pair<String, Any?>) {
        if (isDebugEnabled()) {
            val paramStr = if (params.isNotEmpty()) {
                params.joinToString(", ") { (k, v) -> "$k=$v" }
            } else {
                "no params"
            }
            bukkitLogger.info("[DEBUG][$tag] >>> $methodName($paramStr)")
        }
    }

    /**
     * Log method exit with return value.
     *
     * @param methodName The method being exited
     * @param returnValue The return value (optional)
     */
    fun debugMethodExit(methodName: String, returnValue: Any? = null) {
        if (isDebugEnabled()) {
            val returnStr = returnValue?.let { " -> $it" } ?: ""
            bukkitLogger.info("[DEBUG][$tag] <<< $methodName$returnStr")
        }
    }

    /**
     * Log state information for an object.
     *
     * @param objectName Name of the object/class
     * @param state Key-value pairs representing current state
     */
    fun debugState(objectName: String, vararg state: Pair<String, Any?>) {
        if (isDebugEnabled()) {
            val stateStr = state.joinToString(", ") { (k, v) -> "$k=$v" }
            bukkitLogger.info("[DEBUG][$tag] STATE $objectName: $stateStr")
        }
    }

    /**
     * Standard info log (always logged regardless of debug mode).
     */
    fun info(message: String) {
        bukkitLogger.info("[$tag] $message")
    }

    /**
     * Standard warning log (always logged regardless of debug mode).
     */
    fun warning(message: String) {
        bukkitLogger.warning("[$tag] $message")
    }

    /**
     * Standard severe/error log (always logged regardless of debug mode).
     */
    fun severe(message: String) {
        bukkitLogger.severe("[$tag] $message")
    }

    /**
     * Log an exception with debug context.
     * Always logs regardless of debug mode, but includes extra detail when debug is on.
     */
    fun error(message: String, exception: Throwable? = null) {
        bukkitLogger.severe("[$tag] $message")
        if (exception != null) {
            bukkitLogger.severe("[$tag] Exception: ${exception.message}")
            if (isDebugEnabled()) {
                exception.stackTrace.take(10).forEach { frame ->
                    bukkitLogger.severe("[$tag]   at $frame")
                }
            }
        }
    }
}
