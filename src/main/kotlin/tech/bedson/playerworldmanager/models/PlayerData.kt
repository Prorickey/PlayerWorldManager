package tech.bedson.playerworldmanager.models

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
)

/**
 * Container for all player-specific data.
 */
data class PlayerData(
    val uuid: UUID,
    val username: String,
    var worldLimit: Int = 1,                    // Max worlds this player can create
    val ownedWorlds: MutableList<UUID> = mutableListOf(),  // World IDs owned
    var chatSettings: ChatSettings = ChatSettings(uuid),
    val worldLocations: MutableMap<UUID, LocationData> = mutableMapOf(),  // Last location per world (keyed by world UUID)
    var lastWorldId: UUID? = null               // Last plugin world the player was in (for join restoration)
)
