package tech.bedson.playerworldmanager.models

/**
 * Enum representing the different roles a player can have in a world.
 *
 * Roles define what actions a player can perform within a world:
 * - OWNER: Full control including deletion, transfer, and all management
 * - MANAGER: Can invite/kick players and manage settings, but cannot delete or transfer
 * - MEMBER: Basic access to the world with no management permissions
 * - VISITOR: Can view the world in spectator mode only
 */
enum class WorldRole {
    /**
     * Full owner of the world.
     * Can perform all actions including:
     * - Delete the world
     * - Transfer ownership
     * - Invite/kick players
     * - Manage world settings
     * - Promote/demote players
     * - Toggle world visibility (public/private)
     */
    OWNER,

    /**
     * Privileged role with management permissions.
     * Can perform:
     * - Invite players
     * - Kick players (except owner and other managers)
     * - Manage world settings (time lock, weather lock, game mode)
     * Cannot:
     * - Delete the world
     * - Transfer ownership
     * - Kick or demote other managers
     * - Promote players to manager
     * - Change world visibility
     */
    MANAGER,

    /**
     * Basic member with world access.
     * Can:
     * - Enter and play in the world normally
     * Cannot:
     * - Invite or kick other players
     * - Manage world settings
     */
    MEMBER,

    /**
     * Visitor with limited access.
     * Can:
     * - Enter the world in spectator mode only
     * Cannot:
     * - Interact with the world
     * - Build or break blocks
     * - Invite or kick other players
     * - Manage world settings
     */
    VISITOR;

    /**
     * Check if this role can invite players.
     */
    fun canInvite(): Boolean = this == OWNER || this == MANAGER

    /**
     * Check if this role can kick players.
     */
    fun canKick(): Boolean = this == OWNER || this == MANAGER

    /**
     * Check if this role can manage world settings.
     */
    fun canManageSettings(): Boolean = this == OWNER || this == MANAGER

    /**
     * Check if this role can delete the world.
     */
    fun canDelete(): Boolean = this == OWNER

    /**
     * Check if this role can transfer ownership.
     */
    fun canTransfer(): Boolean = this == OWNER

    /**
     * Check if this role can promote/demote other players.
     */
    fun canManageRoles(): Boolean = this == OWNER

    /**
     * Check if this role can change world visibility (public/private).
     */
    fun canChangeVisibility(): Boolean = this == OWNER

    /**
     * Check if this role allows normal gameplay (non-spectator).
     */
    fun canPlay(): Boolean = this == OWNER || this == MANAGER || this == MEMBER

    /**
     * Check if this role is spectator-only.
     */
    fun isSpectatorOnly(): Boolean = this == VISITOR

    /**
     * Check if this role has higher or equal privilege than another role.
     */
    fun isAtLeast(other: WorldRole): Boolean = this.ordinal <= other.ordinal

    /**
     * Check if this role has higher privilege than another role.
     */
    fun isHigherThan(other: WorldRole): Boolean = this.ordinal < other.ordinal

    companion object {
        /**
         * Get a user-friendly display name for the role.
         */
        fun WorldRole.displayName(): String = when (this) {
            OWNER -> "Owner"
            MANAGER -> "Manager"
            MEMBER -> "Member"
            VISITOR -> "Visitor"
        }
    }
}
