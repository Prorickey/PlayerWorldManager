package tech.bedson.playerworldmanager.models

import java.util.UUID

/**
 * Represents a player's chat preferences.
 */
data class ChatSettings(
    val playerUuid: UUID,
    var chatMode: ChatMode = ChatMode.GLOBAL
) {
    /**
     * Returns a debug-friendly string representation.
     */
    fun toDebugString(): String {
        return "ChatSettings(player=$playerUuid, mode=$chatMode)"
    }

    /**
     * Returns a compact debug string for logging.
     */
    fun toCompactDebugString(): String {
        return "ChatSettings[$chatMode]"
    }
}

/**
 * Enum representing different chat modes.
 */
enum class ChatMode {
    GLOBAL,  // Chat visible to all players
    WORLD    // Chat visible only to players in the same world
}
