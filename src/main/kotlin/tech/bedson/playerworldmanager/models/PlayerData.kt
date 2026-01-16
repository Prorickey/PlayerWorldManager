package tech.bedson.playerworldmanager.models

import java.util.UUID

/**
 * Container for all player-specific data.
 */
data class PlayerData(
    val uuid: UUID,
    val username: String,
    var worldLimit: Int = 3,                    // Max worlds this player can create
    val ownedWorlds: MutableList<UUID> = mutableListOf(),  // World IDs owned
    var chatSettings: ChatSettings = ChatSettings(uuid)
)
