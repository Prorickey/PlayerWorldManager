package tech.bedson.playerworldmanager.managers

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.models.PlayerWorld
import tech.bedson.playerworldmanager.models.WorldInvite
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
     * @param inviter The player sending the invite (must be owner)
     * @param invitee The player being invited
     * @return Result with success or error message
     */
    fun sendInvite(world: PlayerWorld, inviter: Player, invitee: Player): Result<Unit> {
        debugLogger.debugMethodEntry("sendInvite",
            "worldName" to world.name,
            "worldId" to world.id,
            "inviterName" to inviter.name,
            "inviterUuid" to inviter.uniqueId,
            "inviteeName" to invitee.name,
            "inviteeUuid" to invitee.uniqueId
        )
        plugin.logger.info("[InviteManager] sendInvite: Player '${inviter.name}' attempting to invite '${invitee.name}' to world '${world.name}'")

        // Verify inviter is the owner
        debugLogger.debug("Verifying inviter is owner", "worldOwnerUuid" to world.ownerUuid, "inviterUuid" to inviter.uniqueId)
        if (world.ownerUuid != inviter.uniqueId) {
            plugin.logger.warning("[InviteManager] sendInvite: Player '${inviter.name}' is not the owner of world '${world.name}'")
            debugLogger.debug("Invite rejected - inviter is not owner")
            debugLogger.debugMethodExit("sendInvite", "failure: not owner")
            return Result.failure(IllegalArgumentException("You don't own this world"))
        }

        // Check if invitee is already the owner
        if (world.ownerUuid == invitee.uniqueId) {
            plugin.logger.warning("[InviteManager] sendInvite: Cannot invite owner '${invitee.name}' to their own world '${world.name}'")
            debugLogger.debug("Invite rejected - cannot invite self")
            debugLogger.debugMethodExit("sendInvite", "failure: self invite")
            return Result.failure(IllegalArgumentException("You cannot invite yourself to your own world"))
        }

        // Check if invitee is already invited
        debugLogger.debug("Checking if invitee is already invited", "invitedPlayersCount" to world.invitedPlayers.size)
        if (world.invitedPlayers.contains(invitee.uniqueId)) {
            plugin.logger.warning("[InviteManager] sendInvite: Player '${invitee.name}' is already invited to world '${world.name}'")
            debugLogger.debug("Invite rejected - already invited")
            debugLogger.debugMethodExit("sendInvite", "failure: already invited")
            return Result.failure(IllegalArgumentException("${invitee.name} is already invited to this world"))
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
        debugLogger.debug("Creating new invite")
        val invite = WorldInvite(
            worldId = world.id,
            worldName = world.name,
            ownerUuid = world.ownerUuid,
            ownerName = world.ownerName,
            inviteeUuid = invitee.uniqueId,
            inviteeName = invitee.name,
            sentAt = System.currentTimeMillis()
        )
        debugLogger.debugState("newInvite",
            "worldId" to invite.worldId,
            "inviteeUuid" to invite.inviteeUuid,
            "sentAt" to invite.sentAt
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

        invitee.sendMessage(
            Component.text(inviter.name, NamedTextColor.GOLD)
                .append(Component.text(" has invited you to their world ", NamedTextColor.YELLOW))
                .append(Component.text(world.name, NamedTextColor.GOLD))
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

        // Add player to invited players
        debugLogger.debug("Adding player to invited players list", "previousCount" to world.invitedPlayers.size)
        world.invitedPlayers.add(player.uniqueId)
        dataManager.saveWorld(world)
        plugin.logger.info("[InviteManager] acceptInvite: Added player '${player.name}' to invited players list for world '${world.name}'")
        debugLogger.debug("Player added to invited list", "newCount" to world.invitedPlayers.size)

        // Remove the pending invite
        debugLogger.debug("Removing pending invite")
        dataManager.removeInvite(invite)

        // Notify the player
        player.sendMessage(
            Component.text("You have accepted the invite to ", NamedTextColor.GREEN)
                .append(Component.text(world.name, NamedTextColor.GOLD))
                .append(Component.text("!", NamedTextColor.GREEN))
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
     * Kick an invited player from a world (removes their access).
     *
     * @param world The world to kick from
     * @param owner The player kicking (must be owner)
     * @param playerToKick The UUID of the player to kick
     * @return Result with success or error message
     */
    fun kickPlayer(world: PlayerWorld, owner: Player, playerToKick: UUID): Result<Unit> {
        debugLogger.debugMethodEntry("kickPlayer",
            "worldName" to world.name,
            "worldId" to world.id,
            "ownerName" to owner.name,
            "ownerUuid" to owner.uniqueId,
            "playerToKickUuid" to playerToKick
        )
        plugin.logger.info("[InviteManager] kickPlayer: Owner '${owner.name}' attempting to kick player UUID '$playerToKick' from world '${world.name}'")

        // Verify owner
        debugLogger.debug("Verifying caller is owner", "worldOwnerUuid" to world.ownerUuid, "callerUuid" to owner.uniqueId)
        if (world.ownerUuid != owner.uniqueId) {
            plugin.logger.warning("[InviteManager] kickPlayer: Player '${owner.name}' is not the owner of world '${world.name}'")
            debugLogger.debug("Kick rejected - caller is not owner")
            debugLogger.debugMethodExit("kickPlayer", "failure: not owner")
            return Result.failure(IllegalArgumentException("You don't own this world"))
        }

        // Check if player is the owner
        if (playerToKick == owner.uniqueId) {
            plugin.logger.warning("[InviteManager] kickPlayer: Owner '${owner.name}' attempted to kick themselves from world '${world.name}'")
            debugLogger.debug("Kick rejected - cannot kick self")
            debugLogger.debugMethodExit("kickPlayer", "failure: self kick")
            return Result.failure(IllegalArgumentException("You cannot kick yourself from your own world"))
        }

        // Check if player is invited
        debugLogger.debug("Checking if player is in invited list", "invitedPlayersCount" to world.invitedPlayers.size)
        if (!world.invitedPlayers.contains(playerToKick)) {
            plugin.logger.warning("[InviteManager] kickPlayer: Player UUID '$playerToKick' is not invited to world '${world.name}'")
            debugLogger.debug("Kick rejected - player not invited")
            debugLogger.debugMethodExit("kickPlayer", "failure: not invited")
            return Result.failure(IllegalArgumentException("This player is not invited to your world"))
        }

        // Get the kicked player's name for messaging
        val kickedPlayerName = Bukkit.getOfflinePlayer(playerToKick).name ?: playerToKick.toString()
        plugin.logger.info("[InviteManager] kickPlayer: Kicking player '$kickedPlayerName' from world '${world.name}'")

        // Remove from invited players
        debugLogger.debug("Removing player from invited list", "previousCount" to world.invitedPlayers.size)
        world.invitedPlayers.remove(playerToKick)
        dataManager.saveWorld(world)
        plugin.logger.info("[InviteManager] kickPlayer: Removed player '$kickedPlayerName' from invited players list for world '${world.name}'")
        debugLogger.debug("Player removed from invited list", "newCount" to world.invitedPlayers.size)

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

        // Notify owner
        owner.sendMessage(
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
     * Check if player is invited to a world.
     *
     * @param playerUuid The player's UUID
     * @param world The world to check
     * @return True if player is in the invited players list
     */
    fun isInvited(playerUuid: UUID, world: PlayerWorld): Boolean {
        debugLogger.debugMethodEntry("isInvited", "playerUuid" to playerUuid, "worldName" to world.name)
        val isInvited = world.invitedPlayers.contains(playerUuid)
        debugLogger.debugMethodExit("isInvited", isInvited)
        return isInvited
    }

    /**
     * Check if player has access to a world (owner OR invited).
     *
     * @param playerUuid The player's UUID
     * @param world The world to check
     * @return True if player is owner or invited
     */
    fun hasAccess(playerUuid: UUID, world: PlayerWorld): Boolean {
        debugLogger.debugMethodEntry("hasAccess", "playerUuid" to playerUuid, "worldName" to world.name, "worldId" to world.id)
        val isOwner = world.ownerUuid == playerUuid
        val isInvited = world.invitedPlayers.contains(playerUuid)
        val hasAccess = isOwner || isInvited
        plugin.logger.info("[InviteManager] hasAccess: Checking access for player UUID '$playerUuid' to world '${world.name}' - Owner: $isOwner, Invited: $isInvited, Result: $hasAccess")
        debugLogger.debug("Access check result", "isOwner" to isOwner, "isInvited" to isInvited, "hasAccess" to hasAccess)
        debugLogger.debugMethodExit("hasAccess", hasAccess)
        return hasAccess
    }

    /**
     * Get all players invited to a world.
     *
     * @param world The world
     * @return Set of invited player UUIDs
     */
    fun getInvitedPlayers(world: PlayerWorld): Set<UUID> {
        debugLogger.debugMethodEntry("getInvitedPlayers", "worldName" to world.name)
        val players = world.invitedPlayers.toSet()
        debugLogger.debug("Retrieved invited players", "count" to players.size)
        debugLogger.debugMethodExit("getInvitedPlayers", players.size)
        return players
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

        // Check if new owner is invited
        debugLogger.debug("Checking if new owner is invited", "invitedPlayersCount" to world.invitedPlayers.size)
        if (!world.invitedPlayers.contains(newOwnerUuid)) {
            plugin.logger.warning("[InviteManager] transferOwnership: New owner UUID '$newOwnerUuid' is not invited to world '${world.name}'")
            debugLogger.debug("Transfer rejected - new owner not invited")
            debugLogger.debugMethodExit("transferOwnership", "failure: not invited")
            return Result.failure(IllegalArgumentException("The new owner must be invited to the world first"))
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

        // Remove new owner from invited players
        updatedWorld.invitedPlayers.remove(newOwnerUuid)
        plugin.logger.info("[InviteManager] transferOwnership: Removed new owner '$newOwnerName' from invited players list")

        // Add old owner to invited players
        updatedWorld.invitedPlayers.add(oldOwnerUuid)
        plugin.logger.info("[InviteManager] transferOwnership: Added old owner '$oldOwnerName' to invited players list")
        debugLogger.debug("Updated invited players list", "count" to updatedWorld.invitedPlayers.size)

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
