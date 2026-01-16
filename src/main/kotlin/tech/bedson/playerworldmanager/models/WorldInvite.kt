package tech.bedson.playerworldmanager.models

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
)
