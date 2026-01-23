package tech.bedson.playerworldmanager.models

import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.utils.DebugLogger
import java.util.UUID

/**
 * Represents a pending world invite that requires acceptance.
 */
data class WorldInvite(
    val worldId: UUID,           // Which world
    val worldName: String,       // World name for display
    val ownerUuid: UUID,         // Who sent the invite
    val ownerName: String,       // Owner name for display
    val inviteeUuid: UUID,       // Who received the invite
    val inviteeName: String,     // Invitee name for display
    val sentAt: Long             // Timestamp
) {
    companion object {
        /**
         * Create a WorldInvite with debug logging.
         */
        fun createWithLogging(
            plugin: JavaPlugin,
            worldId: UUID,
            worldName: String,
            ownerUuid: UUID,
            ownerName: String,
            inviteeUuid: UUID,
            inviteeName: String,
            sentAt: Long
        ): WorldInvite {
            val debugLogger = DebugLogger(plugin, "WorldInvite")
            debugLogger.debug("Creating WorldInvite",
                "worldId" to worldId,
                "worldName" to worldName,
                "owner" to ownerName,
                "invitee" to inviteeName
            )
            return WorldInvite(worldId, worldName, ownerUuid, ownerName, inviteeUuid, inviteeName, sentAt)
        }
    }

    /**
     * Returns a debug-friendly string representation.
     */
    fun toDebugString(): String {
        return "WorldInvite(worldId=$worldId, world=$worldName, " +
                "from=$ownerName/$ownerUuid, to=$inviteeName/$inviteeUuid, sentAt=$sentAt)"
    }

    /**
     * Returns a compact debug string for logging.
     */
    fun toCompactDebugString(): String {
        return "WorldInvite[$worldName: $ownerName -> $inviteeName]"
    }
}
