package tech.bedson.playerworldmanager.managers

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.models.PlayerWorld
import tech.bedson.playerworldmanager.models.WorldInvite
import tech.bedson.playerworldmanager.models.WorldRole
import tech.bedson.playerworldmanager.utils.DebugLogger
import java.util.UUID

/**
 * Manages the invite system with request/accept flow.
 *
 * This manager handles sending invites, accepting/denying them, kicking players,
 * and checking access permissions for player worlds.
 */
class InviteManager(
    private val plugin: JavaPlugin,
    private val dataManager: DataManager,
    private val worldManager: WorldManager
) {
    private val debugLogger = DebugLogger(plugin, "InviteManager")

    /**
     * Send an invite to a player (creates pending invite).
     *
     * @param world The world to invite to
     * @param inviter The player sending the invite (must be owner or manager)
     * @param invitee The player being invited
     * @param role The role to assign to the invitee (default: MEMBER)
     * @return Result with success or error message
     */
    fun sendInvite(world: PlayerWorld, inviter: Player, invitee: Player, role: WorldRole = WorldRole.MEMBER): Result<Unit> {
        debugLogger.debugMethodEntry("sendInvite",
            "worldName" to world.name,
            "worldId" to world.id,
            "inviterName" to inviter.name,
            "inviterUuid" to inviter.uniqueId,
            "inviteeName" to invitee.name,
            "inviteeUuid" to invitee.uniqueId,
            "assignedRole" to role
        )
        plugin.logger.info("[InviteManager] sendInvite: Player '${inviter.name}' attempting to invite '${invitee.name}' to world '${world.name}' with role '$role'")

        // Verify inviter has permission to invite
        val inviterRole = world.getPlayerRole(inviter.uniqueId)
        debugLogger.debug("Verifying inviter has invite permission", "inviterRole" to inviterRole)
        if (inviterRole == null || !inviterRole.canInvite()) {
            plugin.logger.warning("[InviteManager] sendInvite: Player '${inviter.name}' does not have permission to invite in world '${world.name}'")
            debugLogger.debug("Invite rejected - inviter lacks permission")
            debugLogger.debugMethodExit("sendInvite", "failure: no permission")
            return Result.failure(IllegalArgumentException("You don't have permission to invite players to this world"))
        }

        // Managers can only invite as MEMBER or VISITOR, not MANAGER
        if (inviterRole == WorldRole.MANAGER && role == WorldRole.MANAGER) {
            plugin.logger.warning("[InviteManager] sendInvite: Manager '${inviter.name}' attempted to invite as MANAGER")
            debugLogger.debug("Invite rejected - managers cannot invite as manager")
            debugLogger.debugMethodExit("sendInvite", "failure: cannot invite as manager")
            return Result.failure(IllegalArgumentException("Only the owner can invite players as managers"))
        }

        // Check if invitee is already the owner
        if (world.ownerUuid == invitee.uniqueId) {
            plugin.logger.warning("[InviteManager] sendInvite: Cannot invite owner '${invitee.name}' to their own world '${world.name}'")
            debugLogger.debug("Invite rejected - cannot invite self")
            debugLogger.debugMethodExit("sendInvite", "failure: self invite")
            return Result.failure(IllegalArgumentException("You cannot invite yourself to your own world"))
        }

        // Check if invitee already has explicit access
        debugLogger.debug("Checking if invitee already has access", "playerRolesCount" to world.playerRoles.size)
        if (world.hasExplicitAccess(invitee.uniqueId)) {
            plugin.logger.warning("[InviteManager] sendInvite: Player '${invitee.name}' already has access to world '${world.name}'")
            debugLogger.debug("Invite rejected - already has access")
            debugLogger.debugMethodExit("sendInvite", "failure: already has access")
            return Result.failure(IllegalArgumentException("${invitee.name} already has access to this world"))
        }

        // Check if there's already a pending invite
        debugLogger.debug("Checking for existing pending invite")
        val existingInvite = dataManager.getInvite(world.id, invitee.uniqueId)
        if (existingInvite != null) {
            plugin.logger.warning("[InviteManager] sendInvite: Player '${invitee.name}' already has a pending invite to world '${world.name}'")
            debugLogger.debug("Invite rejected - pending invite exists", "existingInviteSentAt" to existingInvite.sentAt)
            debugLogger.debugMethodExit("sendInvite", "failure: pending invite exists")
            return Result.failure(IllegalArgumentException("${invitee.name} already has a pending invite to this world"))
        }

        // Create invite
        debugLogger.debug("Creating new invite with role", "role" to role)
        val invite = WorldInvite(
            worldId = world.id,
            worldName = world.name,
            ownerUuid = inviter.uniqueId,  // The actual inviter, not necessarily the owner
            ownerName = inviter.name,
            inviteeUuid = invitee.uniqueId,
            inviteeName = invitee.name,
            sentAt = System.currentTimeMillis(),
            assignedRole = role
        )
        debugLogger.debugState("newInvite",
            "worldId" to invite.worldId,
            "inviteeUuid" to invite.inviteeUuid,
            "sentAt" to invite.sentAt,
            "assignedRole" to invite.assignedRole
        )

        // Save invite
        dataManager.addInvite(invite)
        plugin.logger.info("[InviteManager] sendInvite: Created and saved invite for '${invitee.name}' to world '${world.name}'")

        // Notify both players
        inviter.sendMessage(
            Component.text("Invite sent to ", NamedTextColor.GREEN)
                .append(Component.text(invitee.name, NamedTextColor.GOLD))
                .append(Component.text(" for world ", NamedTextColor.GREEN))
                .append(Component.text(world.name, NamedTextColor.GOLD))
        )

        val roleText = when (role) {
            WorldRole.MANAGER -> " as a manager"
            WorldRole.MEMBER -> ""
            WorldRole.VISITOR -> " as a visitor"
            else -> ""
        }
        invitee.sendMessage(
            Component.text(inviter.name, NamedTextColor.GOLD)
                .append(Component.text(" has invited you to the world ", NamedTextColor.YELLOW))
                .append(Component.text(world.name, NamedTextColor.GOLD))
                .append(Component.text(roleText, NamedTextColor.YELLOW))
                .append(Component.text("!", NamedTextColor.YELLOW))
        )
        invitee.sendMessage(
            Component.text("Use ", NamedTextColor.YELLOW)
                .append(Component.text("/world invite accept ${world.name}", NamedTextColor.GOLD))
                .append(Component.text(" to accept", NamedTextColor.YELLOW))
        )

        plugin.logger.info("[InviteManager] sendInvite: Successfully sent invite from '${inviter.name}' to '${invitee.name}' for world '${world.name}'")
        debugLogger.debugMethodExit("sendInvite", "success")
        return Result.success(Unit)
    }

    /**
     * Accept a pending invite.
     *
     * @param invite The invite to accept
     * @param player The player accepting (must be the invitee)
     * @return Result with success or error message
     */
    fun acceptInvite(invite: WorldInvite, player: Player): Result<Unit> {
        debugLogger.debugMethodEntry("acceptInvite",
            "playerName" to player.name,
            "playerUuid" to player.uniqueId,
            "worldName" to invite.worldName,
            "worldId" to invite.worldId,
            "ownerName" to invite.ownerName
        )
        plugin.logger.info("[InviteManager] acceptInvite: Player '${player.name}' attempting to accept invite to world '${invite.worldName}'")

        // Verify player is the invitee
        debugLogger.debug("Verifying player is invitee", "inviteInviteeUuid" to invite.inviteeUuid, "playerUuid" to player.uniqueId)
        if (invite.inviteeUuid != player.uniqueId) {
            plugin.logger.warning("[InviteManager] acceptInvite: Player '${player.name}' is not the invitee for this invite (expected UUID: ${invite.inviteeUuid})")
            debugLogger.debug("Accept rejected - not the invitee")
            debugLogger.debugMethodExit("acceptInvite", "failure: not invitee")
            return Result.failure(IllegalArgumentException("This invite is not for you"))
        }

        // Load the world
        debugLogger.debug("Loading world from data manager", "worldId" to invite.worldId)
        val world = dataManager.loadWorld(invite.worldId)
        if (world == null) {
            plugin.logger.warning("[InviteManager] acceptInvite: World '${invite.worldName}' (ID: ${invite.worldId}) no longer exists")
            debugLogger.debug("Accept rejected - world no longer exists")
            debugLogger.debugMethodExit("acceptInvite", "failure: world not found")
            return Result.failure(IllegalStateException("World no longer exists"))
        }
        debugLogger.debug("World loaded successfully", "worldName" to world.name)

        // Add player with their assigned role
        val assignedRole = invite.assignedRole
        debugLogger.debug("Adding player with role", "previousCount" to world.playerRoles.size, "assignedRole" to assignedRole)
        world.setPlayerRole(player.uniqueId, assignedRole)
        dataManager.saveWorld(world)
        plugin.logger.info("[InviteManager] acceptInvite: Added player '${player.name}' to world '${world.name}' with role '$assignedRole'")
        debugLogger.debug("Player added with role", "newCount" to world.playerRoles.size, "role" to assignedRole)

        // Remove the pending invite
        debugLogger.debug("Removing pending invite")
        dataManager.removeInvite(invite)

        // Notify the player
        val roleDescription = when (assignedRole) {
            WorldRole.MANAGER -> " You are a manager."
            WorldRole.VISITOR -> " You are a visitor (spectator mode)."
            else -> ""
        }
        player.sendMessage(
            Component.text("You have accepted the invite to ", NamedTextColor.GREEN)
                .append(Component.text(world.name, NamedTextColor.GOLD))
                .append(Component.text("!$roleDescription", NamedTextColor.GREEN))
        )

        // Notify the owner if online
        val owner = Bukkit.getPlayer(world.ownerUuid)
        if (owner != null) {
            owner.sendMessage(
                Component.text(player.name, NamedTextColor.GOLD)
                    .append(Component.text(" has accepted your invite to ", NamedTextColor.GREEN))
                    .append(Component.text(world.name, NamedTextColor.GOLD))
            )
            plugin.logger.info("[InviteManager] acceptInvite: Notified owner '${owner.name}' that '${player.name}' accepted the invite")
        }

        plugin.logger.info("[InviteManager] acceptInvite: Successfully processed invite acceptance for '${player.name}' to world '${world.name}'")
        debugLogger.debugMethodExit("acceptInvite", "success")
        return Result.success(Unit)
    }

    /**
     * Deny/decline a pending invite.
     *
     * @param invite The invite to deny
     * @param player The player denying (must be the invitee)
     * @return Result with success or error message
     */
    fun denyInvite(invite: WorldInvite, player: Player): Result<Unit> {
        debugLogger.debugMethodEntry("denyInvite",
            "playerName" to player.name,
            "playerUuid" to player.uniqueId,
            "worldName" to invite.worldName,
            "worldId" to invite.worldId
        )
        plugin.logger.info("[InviteManager] denyInvite: Player '${player.name}' attempting to deny invite to world '${invite.worldName}'")

        // Verify player is the invitee
        debugLogger.debug("Verifying player is invitee", "inviteInviteeUuid" to invite.inviteeUuid, "playerUuid" to player.uniqueId)
        if (invite.inviteeUuid != player.uniqueId) {
            plugin.logger.warning("[InviteManager] denyInvite: Player '${player.name}' is not the invitee for this invite (expected UUID: ${invite.inviteeUuid})")
            debugLogger.debug("Deny rejected - not the invitee")
            debugLogger.debugMethodExit("denyInvite", "failure: not invitee")
            return Result.failure(IllegalArgumentException("This invite is not for you"))
        }

        // Remove the pending invite
        debugLogger.debug("Removing pending invite")
        dataManager.removeInvite(invite)
        plugin.logger.info("[InviteManager] denyInvite: Removed pending invite for '${player.name}' to world '${invite.worldName}'")

        // Notify the player
        player.sendMessage(
            Component.text("You have declined the invite to ", NamedTextColor.YELLOW)
                .append(Component.text(invite.worldName, NamedTextColor.GOLD))
        )

        // Notify the owner if online
        val owner = Bukkit.getPlayer(invite.ownerUuid)
        if (owner != null) {
            owner.sendMessage(
                Component.text(player.name, NamedTextColor.GOLD)
                    .append(Component.text(" has declined your invite to ", NamedTextColor.YELLOW))
                    .append(Component.text(invite.worldName, NamedTextColor.GOLD))
            )
            plugin.logger.info("[InviteManager] denyInvite: Notified owner '${owner.name}' that '${player.name}' declined the invite")
        }

        plugin.logger.info("[InviteManager] denyInvite: Successfully processed invite denial for '${player.name}' to world '${invite.worldName}'")
        debugLogger.debugMethodExit("denyInvite", "success")
        return Result.success(Unit)
    }

    /**
     * Cancel a sent invite (by owner).
     *
     * @param invite The invite to cancel
     * @param owner The player canceling (must be the owner)
     * @return Result with success or error message
     */
    fun cancelInvite(invite: WorldInvite, owner: Player): Result<Unit> {
        debugLogger.debugMethodEntry("cancelInvite",
            "ownerName" to owner.name,
            "ownerUuid" to owner.uniqueId,
            "worldName" to invite.worldName,
            "worldId" to invite.worldId,
            "inviteeName" to invite.inviteeName
        )

        // Verify player is the owner
        debugLogger.debug("Verifying player is owner", "inviteOwnerUuid" to invite.ownerUuid, "playerUuid" to owner.uniqueId)
        if (invite.ownerUuid != owner.uniqueId) {
            debugLogger.debug("Cancel rejected - not the owner")
            debugLogger.debugMethodExit("cancelInvite", "failure: not owner")
            return Result.failure(IllegalArgumentException("You didn't send this invite"))
        }

        // Remove the pending invite
        debugLogger.debug("Removing pending invite")
        dataManager.removeInvite(invite)

        // Notify the owner
        owner.sendMessage(
            Component.text("Invite to ", NamedTextColor.YELLOW)
                .append(Component.text(invite.inviteeName, NamedTextColor.GOLD))
                .append(Component.text(" has been cancelled", NamedTextColor.YELLOW))
        )

        // Notify the invitee if online
        val invitee = Bukkit.getPlayer(invite.inviteeUuid)
        invitee?.sendMessage(
            Component.text("The invite to ", NamedTextColor.YELLOW)
                .append(Component.text(invite.worldName, NamedTextColor.GOLD))
                .append(Component.text(" has been cancelled", NamedTextColor.YELLOW))
        )

        debugLogger.debugMethodExit("cancelInvite", "success")
        return Result.success(Unit)
    }

    /**
     * Kick a player from a world (removes their access).
     *
     * @param world The world to kick from
     * @param kicker The player kicking (must be owner or manager)
     * @param playerToKick The UUID of the player to kick
     * @return Result with success or error message
     */
    fun kickPlayer(world: PlayerWorld, kicker: Player, playerToKick: UUID): Result<Unit> {
        debugLogger.debugMethodEntry("kickPlayer",
            "worldName" to world.name,
            "worldId" to world.id,
            "kickerName" to kicker.name,
            "kickerUuid" to kicker.uniqueId,
            "playerToKickUuid" to playerToKick
        )
        plugin.logger.info("[InviteManager] kickPlayer: Player '${kicker.name}' attempting to kick player UUID '$playerToKick' from world '${world.name}'")

        // Verify kicker has permission to kick
        val kickerRole = world.getPlayerRole(kicker.uniqueId)
        debugLogger.debug("Verifying caller has kick permission", "kickerRole" to kickerRole)
        if (kickerRole == null || !kickerRole.canKick()) {
            plugin.logger.warning("[InviteManager] kickPlayer: Player '${kicker.name}' does not have permission to kick in world '${world.name}'")
            debugLogger.debug("Kick rejected - caller lacks permission")
            debugLogger.debugMethodExit("kickPlayer", "failure: no permission")
            return Result.failure(IllegalArgumentException("You don't have permission to kick players from this world"))
        }

        // Check if player is trying to kick themselves
        if (playerToKick == kicker.uniqueId) {
            plugin.logger.warning("[InviteManager] kickPlayer: Player '${kicker.name}' attempted to kick themselves from world '${world.name}'")
            debugLogger.debug("Kick rejected - cannot kick self")
            debugLogger.debugMethodExit("kickPlayer", "failure: self kick")
            return Result.failure(IllegalArgumentException("You cannot kick yourself from the world"))
        }

        // Check if player has access to the world
        val playerRole = world.getExplicitPlayerRole(playerToKick)
        debugLogger.debug("Checking target player's role", "playerRole" to playerRole)
        if (playerRole == null) {
            plugin.logger.warning("[InviteManager] kickPlayer: Player UUID '$playerToKick' does not have access to world '${world.name}'")
            debugLogger.debug("Kick rejected - player has no access")
            debugLogger.debugMethodExit("kickPlayer", "failure: no access")
            return Result.failure(IllegalArgumentException("This player does not have access to this world"))
        }

        // Cannot kick the owner
        if (playerRole == WorldRole.OWNER) {
            plugin.logger.warning("[InviteManager] kickPlayer: Cannot kick owner from world '${world.name}'")
            debugLogger.debug("Kick rejected - cannot kick owner")
            debugLogger.debugMethodExit("kickPlayer", "failure: cannot kick owner")
            return Result.failure(IllegalArgumentException("Cannot kick the world owner"))
        }

        // Managers cannot kick other managers
        if (kickerRole == WorldRole.MANAGER && playerRole == WorldRole.MANAGER) {
            plugin.logger.warning("[InviteManager] kickPlayer: Manager '${kicker.name}' attempted to kick another manager from world '${world.name}'")
            debugLogger.debug("Kick rejected - managers cannot kick other managers")
            debugLogger.debugMethodExit("kickPlayer", "failure: cannot kick manager")
            return Result.failure(IllegalArgumentException("Managers cannot kick other managers"))
        }

        // Get the kicked player's name for messaging
        val kickedPlayerName = Bukkit.getOfflinePlayer(playerToKick).name ?: playerToKick.toString()
        plugin.logger.info("[InviteManager] kickPlayer: Kicking player '$kickedPlayerName' from world '${world.name}'")

        // Remove player's access
        debugLogger.debug("Removing player's access", "previousCount" to world.playerRoles.size)
        world.removePlayer(playerToKick)
        dataManager.saveWorld(world)
        plugin.logger.info("[InviteManager] kickPlayer: Removed player '$kickedPlayerName' from world '${world.name}'")
        debugLogger.debug("Player removed", "newCount" to world.playerRoles.size)

        // If player is currently in the world, teleport them out
        val kickedPlayer = Bukkit.getPlayer(playerToKick)
        if (kickedPlayer != null && kickedPlayer.isOnline) {
            val playerWorld = worldManager.getPlayerWorldFromBukkitWorld(kickedPlayer.world)
            if (playerWorld?.id == world.id) {
                plugin.logger.info("[InviteManager] kickPlayer: Player '$kickedPlayerName' is currently in world '${world.name}', teleporting out")
                // Teleport to default spawn
                val defaultWorld = Bukkit.getWorlds().firstOrNull()
                if (defaultWorld != null) {
                    kickedPlayer.scheduler.run(plugin, { _ ->
                        kickedPlayer.teleportAsync(defaultWorld.spawnLocation).thenAccept {
                            kickedPlayer.sendMessage(
                                Component.text("You have been kicked from ", NamedTextColor.RED)
                                    .append(Component.text(world.name, NamedTextColor.GOLD))
                            )
                            plugin.logger.info("[InviteManager] kickPlayer: Successfully teleported '$kickedPlayerName' out of world '${world.name}'")
                        }
                    }, null)
                } else {
                    plugin.logger.warning("[InviteManager] kickPlayer: No default world found to teleport '$kickedPlayerName' to")
                }
            } else {
                plugin.logger.info("[InviteManager] kickPlayer: Player '$kickedPlayerName' is not currently in world '${world.name}', sending notification only")
                // Not in the world, just notify
                kickedPlayer.sendMessage(
                    Component.text("You have been removed from ", NamedTextColor.YELLOW)
                        .append(Component.text(world.name, NamedTextColor.GOLD))
                )
            }
        } else {
            plugin.logger.info("[InviteManager] kickPlayer: Player '$kickedPlayerName' is offline, no teleport needed")
        }

        // Notify kicker
        kicker.sendMessage(
            Component.text("Kicked ", NamedTextColor.GREEN)
                .append(Component.text(kickedPlayerName, NamedTextColor.GOLD))
                .append(Component.text(" from ", NamedTextColor.GREEN))
                .append(Component.text(world.name, NamedTextColor.GOLD))
        )

        plugin.logger.info("[InviteManager] kickPlayer: Successfully kicked player '$kickedPlayerName' from world '${world.name}'")
        debugLogger.debugMethodExit("kickPlayer", "success")
        return Result.success(Unit)
    }

    /**
     * Get all pending invites for a player.
     *
     * @param playerUuid The player's UUID
     * @return List of pending invites
     */
    fun getPendingInvites(playerUuid: UUID): List<WorldInvite> {
        debugLogger.debugMethodEntry("getPendingInvites", "playerUuid" to playerUuid)
        val invites = dataManager.getInvitesForPlayer(playerUuid)
        debugLogger.debug("Retrieved pending invites", "count" to invites.size)
        debugLogger.debugMethodExit("getPendingInvites", invites.size)
        return invites
    }

    /**
     * Get all pending invites sent for a world.
     *
     * @param worldId The world's UUID
     * @return List of pending invites
     */
    fun getPendingInvitesForWorld(worldId: UUID): List<WorldInvite> {
        debugLogger.debugMethodEntry("getPendingInvitesForWorld", "worldId" to worldId)
        val invites = dataManager.getInvitesForWorld(worldId)
        debugLogger.debug("Retrieved pending invites for world", "count" to invites.size)
        debugLogger.debugMethodExit("getPendingInvitesForWorld", invites.size)
        return invites
    }

    /**
     * Check if player has explicit access to a world (not via public).
     *
     * @param playerUuid The player's UUID
     * @param world The world to check
     * @return True if player has explicit access (owner, manager, member, or visitor role)
     */
    fun hasExplicitAccess(playerUuid: UUID, world: PlayerWorld): Boolean {
        debugLogger.debugMethodEntry("hasExplicitAccess", "playerUuid" to playerUuid, "worldName" to world.name)
        val hasAccess = world.hasExplicitAccess(playerUuid)
        debugLogger.debugMethodExit("hasExplicitAccess", hasAccess)
        return hasAccess
    }

    /**
     * Check if player has access to a world (owner, invited, or via public access).
     *
     * @param playerUuid The player's UUID
     * @param world The world to check
     * @return True if player has access
     */
    fun hasAccess(playerUuid: UUID, world: PlayerWorld): Boolean {
        debugLogger.debugMethodEntry("hasAccess", "playerUuid" to playerUuid, "worldName" to world.name, "worldId" to world.id)
        val role = world.getPlayerRole(playerUuid)
        val hasAccess = role != null
        plugin.logger.info("[InviteManager] hasAccess: Checking access for player UUID '$playerUuid' to world '${world.name}' - Role: $role, Result: $hasAccess")
        debugLogger.debug("Access check result", "role" to role, "hasAccess" to hasAccess)
        debugLogger.debugMethodExit("hasAccess", hasAccess)
        return hasAccess
    }

    /**
     * Get the role of a player in a world.
     *
     * @param playerUuid The player's UUID
     * @param world The world to check
     * @return The player's role, or null if no access
     */
    fun getPlayerRole(playerUuid: UUID, world: PlayerWorld): WorldRole? {
        debugLogger.debugMethodEntry("getPlayerRole", "playerUuid" to playerUuid, "worldName" to world.name)
        val role = world.getPlayerRole(playerUuid)
        debugLogger.debugMethodExit("getPlayerRole", role)
        return role
    }

    /**
     * Get all players with access to a world (excluding owner).
     *
     * @param world The world
     * @return Set of player UUIDs with access
     */
    fun getPlayersWithAccess(world: PlayerWorld): Set<UUID> {
        debugLogger.debugMethodEntry("getPlayersWithAccess", "worldName" to world.name)
        val players = world.playerRoles.keys.toSet()
        debugLogger.debug("Retrieved players with access", "count" to players.size)
        debugLogger.debugMethodExit("getPlayersWithAccess", players.size)
        return players
    }

    /**
     * Get all players with a specific role in a world.
     *
     * @param world The world
     * @param role The role to filter by
     * @return Set of player UUIDs with the specified role
     */
    fun getPlayersWithRole(world: PlayerWorld, role: WorldRole): Set<UUID> {
        debugLogger.debugMethodEntry("getPlayersWithRole", "worldName" to world.name, "role" to role)
        val players = world.getPlayersWithRole(role)
        debugLogger.debug("Retrieved players with role", "count" to players.size)
        debugLogger.debugMethodExit("getPlayersWithRole", players.size)
        return players
    }

    /**
     * Set or change a player's role in a world.
     * Only the owner can change roles.
     *
     * @param world The world
     * @param owner The player making the change (must be owner)
     * @param targetUuid The UUID of the player whose role is being changed
     * @param newRole The new role to assign
     * @return Result with success or error message
     */
    fun setPlayerRole(world: PlayerWorld, owner: Player, targetUuid: UUID, newRole: WorldRole): Result<Unit> {
        debugLogger.debugMethodEntry("setPlayerRole",
            "worldName" to world.name,
            "ownerName" to owner.name,
            "targetUuid" to targetUuid,
            "newRole" to newRole
        )
        plugin.logger.info("[InviteManager] setPlayerRole: Player '${owner.name}' attempting to set role '$newRole' for UUID '$targetUuid' in world '${world.name}'")

        // Verify caller is the owner
        if (world.ownerUuid != owner.uniqueId) {
            plugin.logger.warning("[InviteManager] setPlayerRole: Player '${owner.name}' is not the owner of world '${world.name}'")
            debugLogger.debugMethodExit("setPlayerRole", "failure: not owner")
            return Result.failure(IllegalArgumentException("Only the owner can change player roles"))
        }

        // Cannot change owner role
        if (newRole == WorldRole.OWNER) {
            plugin.logger.warning("[InviteManager] setPlayerRole: Cannot set OWNER role directly")
            debugLogger.debugMethodExit("setPlayerRole", "failure: cannot set owner")
            return Result.failure(IllegalArgumentException("Cannot set OWNER role. Use transfer ownership instead."))
        }

        // Cannot change own role
        if (targetUuid == owner.uniqueId) {
            plugin.logger.warning("[InviteManager] setPlayerRole: Owner attempted to change their own role")
            debugLogger.debugMethodExit("setPlayerRole", "failure: self modification")
            return Result.failure(IllegalArgumentException("You cannot change your own role"))
        }

        // Check if target has access
        if (!world.hasExplicitAccess(targetUuid)) {
            plugin.logger.warning("[InviteManager] setPlayerRole: Target UUID '$targetUuid' does not have access to world '${world.name}'")
            debugLogger.debugMethodExit("setPlayerRole", "failure: no access")
            return Result.failure(IllegalArgumentException("This player does not have access to the world"))
        }

        // Set the role
        val targetName = Bukkit.getOfflinePlayer(targetUuid).name ?: targetUuid.toString()
        val previousRole = world.getPlayerRole(targetUuid)
        world.setPlayerRole(targetUuid, newRole)
        dataManager.saveWorld(world)
        plugin.logger.info("[InviteManager] setPlayerRole: Changed role for '$targetName' from '$previousRole' to '$newRole' in world '${world.name}'")

        // Notify owner
        owner.sendMessage(
            Component.text("Changed ", NamedTextColor.GREEN)
                .append(Component.text(targetName, NamedTextColor.GOLD))
                .append(Component.text("'s role to ", NamedTextColor.GREEN))
                .append(Component.text(newRole.name.lowercase().replaceFirstChar { it.uppercase() }, NamedTextColor.GOLD))
        )

        // Notify target if online
        val targetPlayer = Bukkit.getPlayer(targetUuid)
        if (targetPlayer != null) {
            val roleMessage = when (newRole) {
                WorldRole.MANAGER -> "You are now a manager of "
                WorldRole.MEMBER -> "You are now a member of "
                WorldRole.VISITOR -> "You are now a visitor in "
                else -> "Your role has changed in "
            }
            targetPlayer.sendMessage(
                Component.text(roleMessage, NamedTextColor.YELLOW)
                    .append(Component.text(world.name, NamedTextColor.GOLD))
            )
        }

        debugLogger.debugMethodExit("setPlayerRole", "success")
        return Result.success(Unit)
    }

    /**
     * Toggle a world's public/private visibility.
     * Only the owner can change visibility.
     *
     * @param world The world
     * @param owner The player making the change (must be owner)
     * @return Result with the new visibility state
     */
    fun toggleWorldVisibility(world: PlayerWorld, owner: Player): Result<Boolean> {
        debugLogger.debugMethodEntry("toggleWorldVisibility",
            "worldName" to world.name,
            "ownerName" to owner.name,
            "currentVisibility" to world.isPublic
        )

        // Verify caller is the owner
        if (world.ownerUuid != owner.uniqueId) {
            plugin.logger.warning("[InviteManager] toggleWorldVisibility: Player '${owner.name}' is not the owner of world '${world.name}'")
            debugLogger.debugMethodExit("toggleWorldVisibility", "failure: not owner")
            return Result.failure(IllegalArgumentException("Only the owner can change world visibility"))
        }

        // Toggle visibility
        world.isPublic = !world.isPublic
        dataManager.saveWorld(world)
        plugin.logger.info("[InviteManager] toggleWorldVisibility: World '${world.name}' is now ${if (world.isPublic) "public" else "private"}")

        // Notify owner
        val visibilityText = if (world.isPublic) "public" else "private"
        owner.sendMessage(
            Component.text("World ", NamedTextColor.GREEN)
                .append(Component.text(world.name, NamedTextColor.GOLD))
                .append(Component.text(" is now ", NamedTextColor.GREEN))
                .append(Component.text(visibilityText, NamedTextColor.GOLD))
        )

        debugLogger.debugMethodExit("toggleWorldVisibility", world.isPublic)
        return Result.success(world.isPublic)
    }

    /**
     * Set the role that players get when joining a public world.
     * Only the owner can change this setting.
     *
     * @param world The world
     * @param owner The player making the change (must be owner)
     * @param role The role to assign to public joiners (MEMBER or VISITOR)
     * @return Result with success or error message
     */
    fun setPublicJoinRole(world: PlayerWorld, owner: Player, role: WorldRole): Result<Unit> {
        debugLogger.debugMethodEntry("setPublicJoinRole",
            "worldName" to world.name,
            "ownerName" to owner.name,
            "role" to role
        )

        // Verify caller is the owner
        if (world.ownerUuid != owner.uniqueId) {
            plugin.logger.warning("[InviteManager] setPublicJoinRole: Player '${owner.name}' is not the owner of world '${world.name}'")
            debugLogger.debugMethodExit("setPublicJoinRole", "failure: not owner")
            return Result.failure(IllegalArgumentException("Only the owner can change public join settings"))
        }

        // Only allow MEMBER or VISITOR for public join role
        if (role != WorldRole.MEMBER && role != WorldRole.VISITOR) {
            plugin.logger.warning("[InviteManager] setPublicJoinRole: Invalid role '$role' for public join")
            debugLogger.debugMethodExit("setPublicJoinRole", "failure: invalid role")
            return Result.failure(IllegalArgumentException("Public join role must be Member or Visitor"))
        }

        // Set the role
        world.publicJoinRole = role
        dataManager.saveWorld(world)
        plugin.logger.info("[InviteManager] setPublicJoinRole: World '${world.name}' public join role set to '$role'")

        // Notify owner
        val roleText = if (role == WorldRole.MEMBER) "Member (can play)" else "Visitor (spectator only)"
        owner.sendMessage(
            Component.text("Players joining ", NamedTextColor.GREEN)
                .append(Component.text(world.name, NamedTextColor.GOLD))
                .append(Component.text(" publicly will be: ", NamedTextColor.GREEN))
                .append(Component.text(roleText, NamedTextColor.GOLD))
        )

        debugLogger.debugMethodExit("setPublicJoinRole", "success")
        return Result.success(Unit)
    }

    /**
     * Transfer world ownership.
     *
     * @param world The world to transfer
     * @param currentOwner The current owner (must match world owner)
     * @param newOwnerUuid The UUID of the new owner
     * @return Result with success or error message
     */
    fun transferOwnership(world: PlayerWorld, currentOwner: Player, newOwnerUuid: UUID): Result<Unit> {
        debugLogger.debugMethodEntry("transferOwnership",
            "worldName" to world.name,
            "worldId" to world.id,
            "currentOwnerName" to currentOwner.name,
            "currentOwnerUuid" to currentOwner.uniqueId,
            "newOwnerUuid" to newOwnerUuid
        )
        plugin.logger.info("[InviteManager] transferOwnership: Player '${currentOwner.name}' attempting to transfer ownership of world '${world.name}' to UUID '$newOwnerUuid'")

        // Verify current owner
        debugLogger.debug("Verifying current owner", "worldOwnerUuid" to world.ownerUuid, "callerUuid" to currentOwner.uniqueId)
        if (world.ownerUuid != currentOwner.uniqueId) {
            plugin.logger.warning("[InviteManager] transferOwnership: Player '${currentOwner.name}' is not the owner of world '${world.name}'")
            debugLogger.debug("Transfer rejected - not the current owner")
            debugLogger.debugMethodExit("transferOwnership", "failure: not owner")
            return Result.failure(IllegalArgumentException("You don't own this world"))
        }

        // Check if trying to transfer to self
        if (newOwnerUuid == currentOwner.uniqueId) {
            plugin.logger.warning("[InviteManager] transferOwnership: Player '${currentOwner.name}' attempted to transfer ownership to themselves")
            debugLogger.debug("Transfer rejected - cannot transfer to self")
            debugLogger.debugMethodExit("transferOwnership", "failure: self transfer")
            return Result.failure(IllegalArgumentException("You already own this world"))
        }

        // Check if new owner has access to the world
        debugLogger.debug("Checking if new owner has access", "playerRolesCount" to world.playerRoles.size)
        if (!world.hasExplicitAccess(newOwnerUuid)) {
            plugin.logger.warning("[InviteManager] transferOwnership: New owner UUID '$newOwnerUuid' does not have access to world '${world.name}'")
            debugLogger.debug("Transfer rejected - new owner has no access")
            debugLogger.debugMethodExit("transferOwnership", "failure: no access")
            return Result.failure(IllegalArgumentException("The new owner must have access to the world first"))
        }

        // Get new owner's name
        debugLogger.debug("Looking up new owner by UUID")
        val newOwner = Bukkit.getOfflinePlayer(newOwnerUuid)
        val newOwnerName = newOwner.name
        if (newOwnerName == null) {
            plugin.logger.severe("[InviteManager] transferOwnership: Could not find name for new owner UUID '$newOwnerUuid'")
            debugLogger.debug("Transfer rejected - could not find new owner name")
            debugLogger.debugMethodExit("transferOwnership", "failure: name not found")
            return Result.failure(IllegalStateException("Could not find new owner's name"))
        }
        plugin.logger.info("[InviteManager] transferOwnership: Transferring ownership to player '$newOwnerName'")
        debugLogger.debug("New owner found", "newOwnerName" to newOwnerName)

        // Update world ownership
        val oldOwnerUuid = world.ownerUuid
        val oldOwnerName = world.ownerName
        debugLogger.debug("Preparing ownership transfer",
            "oldOwnerUuid" to oldOwnerUuid,
            "oldOwnerName" to oldOwnerName,
            "newOwnerUuid" to newOwnerUuid,
            "newOwnerName" to newOwnerName
        )

        // Create a new PlayerWorld with updated owner (since ownerUuid and ownerName are val)
        val updatedWorld = world.copy(
            ownerUuid = newOwnerUuid,
            ownerName = newOwnerName
        )
        debugLogger.debug("Created updated world with new owner")

        // Remove new owner from player roles (they're now the owner)
        updatedWorld.playerRoles.remove(newOwnerUuid)
        updatedWorld.invitedPlayers.remove(newOwnerUuid)
        plugin.logger.info("[InviteManager] transferOwnership: Removed new owner '$newOwnerName' from player roles")

        // Add old owner as a manager (they still have management access)
        updatedWorld.playerRoles[oldOwnerUuid] = WorldRole.MANAGER
        updatedWorld.invitedPlayers.add(oldOwnerUuid)
        plugin.logger.info("[InviteManager] transferOwnership: Added old owner '$oldOwnerName' as manager")
        debugLogger.debug("Updated player roles", "count" to updatedWorld.playerRoles.size)

        // Save updated world
        debugLogger.debug("Saving updated world")
        dataManager.saveWorld(updatedWorld)

        // Update old owner's player data
        val oldOwnerData = dataManager.getOrCreatePlayerData(oldOwnerUuid, oldOwnerName)
        oldOwnerData.ownedWorlds.remove(world.id)
        dataManager.savePlayerData(oldOwnerData)
        plugin.logger.info("[InviteManager] transferOwnership: Removed world '${world.name}' from old owner '$oldOwnerName' owned worlds")

        // Update new owner's player data
        val newOwnerData = dataManager.getOrCreatePlayerData(newOwnerUuid, newOwnerName)
        newOwnerData.ownedWorlds.add(world.id)
        dataManager.savePlayerData(newOwnerData)
        plugin.logger.info("[InviteManager] transferOwnership: Added world '${world.name}' to new owner '$newOwnerName' owned worlds")

        // Notify current owner
        currentOwner.sendMessage(
            Component.text("Transferred ownership of ", NamedTextColor.GREEN)
                .append(Component.text(world.name, NamedTextColor.GOLD))
                .append(Component.text(" to ", NamedTextColor.GREEN))
                .append(Component.text(newOwnerName, NamedTextColor.GOLD))
        )

        // Notify new owner if online
        val newOwnerPlayer = Bukkit.getPlayer(newOwnerUuid)
        if (newOwnerPlayer != null) {
            newOwnerPlayer.sendMessage(
                Component.text("You are now the owner of ", NamedTextColor.GREEN)
                    .append(Component.text(world.name, NamedTextColor.GOLD))
                    .append(Component.text("!", NamedTextColor.GREEN))
            )
            plugin.logger.info("[InviteManager] transferOwnership: Notified new owner '$newOwnerName' of ownership transfer")
        }

        plugin.logger.info("[InviteManager] transferOwnership: Successfully transferred ownership of world '${world.name}' from '$oldOwnerName' to '$newOwnerName'")
        debugLogger.debugMethodExit("transferOwnership", "success")
        return Result.success(Unit)
    }
}
