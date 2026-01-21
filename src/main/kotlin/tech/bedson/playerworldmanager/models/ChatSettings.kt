package tech.bedson.playerworldmanager.models

import java.util.UUID

/**
 * Represents a player's chat preferences.
 */
data class ChatSettings(
    val playerUuid: UUID,
    var chatMode: ChatMode = ChatMode.GLOBAL
)

/**
 * Enum representing different chat modes.
 */
enum class ChatMode {
    GLOBAL,  // Chat visible to all players
    WORLD    // Chat visible only to players in the same world
}
