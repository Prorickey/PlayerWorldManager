package tech.bedson.playerworldmanager.models

import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.utils.DebugLogger
import java.util.UUID

/**
 * Stores a player's last known location in a specific world.
 */
data class LocationData(
    val worldName: String,      // Bukkit world name (not PlayerWorld name)
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float
) {
    /**
     * Returns a debug-friendly string representation.
     */
    fun toDebugString(): String {
        return "LocationData(world=$worldName, x=%.2f, y=%.2f, z=%.2f, yaw=%.1f, pitch=%.1f)".format(x, y, z, yaw, pitch)
    }

    /**
     * Returns a compact coordinate string for logging.
     */
    fun toCompactString(): String {
        return "$worldName(%.1f, %.1f, %.1f)".format(x, y, z)
    }
}

/**
 * Container for all player-specific data.
 */
data class PlayerData(
    val uuid: UUID,
    val username: String,
    var worldLimit: Int = 1,                    // Max worlds this player can create
    val ownedWorlds: MutableList<UUID> = mutableListOf(),  // World IDs owned
    var chatSettings: ChatSettings = ChatSettings(uuid),
    val worldLocations: MutableMap<String, LocationData> = mutableMapOf(),  // Last location per Bukkit world (keyed by world name)
    val worldStates: MutableMap<String, PlayerWorldState> = mutableMapOf(), // Full player state per Bukkit world (inventory, health, etc.)
    var lastWorldId: UUID? = null               // Last plugin world the player was in (for join restoration)
) {
    companion object {
        /**
         * Create a PlayerData with debug logging.
         */
        fun createWithLogging(
            plugin: JavaPlugin,
            uuid: UUID,
            username: String,
            worldLimit: Int = 1
        ): PlayerData {
            val debugLogger = DebugLogger(plugin, "PlayerData")
            debugLogger.debug("Creating PlayerData",
                "uuid" to uuid,
                "username" to username,
                "worldLimit" to worldLimit
            )
            return PlayerData(uuid, username, worldLimit)
        }
    }

    /**
     * Returns a debug-friendly string representation.
     */
    fun toDebugString(): String {
        return "PlayerData(uuid=$uuid, username=$username, worldLimit=$worldLimit, " +
                "ownedWorlds=${ownedWorlds.size}, chatMode=${chatSettings.chatMode}, " +
                "locations=${worldLocations.size}, states=${worldStates.size}, " +
                "lastWorldId=$lastWorldId)"
    }

    /**
     * Returns a compact debug string for logging.
     */
    fun toCompactDebugString(): String {
        return "PlayerData[$username: ${ownedWorlds.size} worlds, limit=$worldLimit]"
    }

    /**
     * Returns a summary of player data for debugging.
     */
    fun toDataSummary(): String {
        return buildString {
            append("Player: $username ($uuid)\n")
            append("  Worlds: ${ownedWorlds.size}/$worldLimit owned")
            if (ownedWorlds.isNotEmpty()) {
                append(" [${ownedWorlds.joinToString(", ") { it.toString().take(8) }}]")
            }
            append("\n")
            append("  Chat: ${chatSettings.chatMode}\n")
            append("  Locations: ${worldLocations.keys.joinToString(", ").ifEmpty { "none" }}\n")
            append("  States: ${worldStates.keys.joinToString(", ").ifEmpty { "none" }}\n")
            append("  Last World: ${lastWorldId?.toString()?.take(8) ?: "none"}")
        }
    }
}
