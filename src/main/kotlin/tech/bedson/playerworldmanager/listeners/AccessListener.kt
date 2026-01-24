package tech.bedson.playerworldmanager.listeners

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerPortalEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.managers.InviteManager
import tech.bedson.playerworldmanager.managers.WorldManager
import tech.bedson.playerworldmanager.models.WorldRole
import tech.bedson.playerworldmanager.utils.DebugLogger

/**
 * Event listener that enforces access control for player worlds.
 *
 * This listener prevents unauthorized players from accessing private worlds
 * through teleportation, portals, or other means.
 */
class AccessListener(
    private val plugin: JavaPlugin,
    private val worldManager: WorldManager,
    private val inviteManager: InviteManager
) : Listener {

    private val debugLogger = DebugLogger(plugin, "AccessListener")

    companion object {
        private const val ADMIN_BYPASS_PERMISSION = "playerworldmanager.admin.bypass"
    }

    /**
     * Block teleportation to private worlds if not invited.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        val player = event.player
        val fromWorld = event.from.world
        val toWorld = event.to.world ?: run {
            plugin.logger.warning("[AccessListener] PlayerTeleport: Player '${player.name}' teleport to null world")
            debugLogger.debug("Teleport destination world is null",
                "player" to player.name,
                "playerUuid" to player.uniqueId,
                "fromWorld" to fromWorld?.name
            )
            return
        }

        debugLogger.debugMethodEntry("onPlayerTeleport",
            "player" to player.name,
            "playerUuid" to player.uniqueId,
            "fromWorld" to fromWorld?.name,
            "toWorld" to toWorld.name,
            "cause" to event.cause,
            "isCancelled" to event.isCancelled
        )

        // Check if destination is a plugin world
        val playerWorld = worldManager.getPlayerWorldFromBukkitWorld(toWorld)
        val isPluginWorld = playerWorld != null
        debugLogger.debug("Checking if destination is plugin world",
            "toWorld" to toWorld.name,
            "isPluginWorld" to isPluginWorld
        )

        if (playerWorld == null) {
            debugLogger.debugMethodExit("onPlayerTeleport", "allowed" to true)
            return
        }

        debugLogger.debug("Plugin world details",
            "worldName" to playerWorld.name,
            "worldId" to playerWorld.id,
            "ownerUuid" to playerWorld.ownerUuid,
            "ownerName" to playerWorld.ownerName,
            "isEnabled" to playerWorld.isEnabled
        )

        // Admin bypass
        val hasAdminBypass = player.hasPermission(ADMIN_BYPASS_PERMISSION)
        debugLogger.debug("Checking admin bypass permission",
            "player" to player.name,
            "permission" to ADMIN_BYPASS_PERMISSION,
            "hasPermission" to hasAdminBypass
        )

        if (hasAdminBypass) {
            debugLogger.debugMethodExit("onPlayerTeleport", "allowed" to true)
            return
        }

        // Check if player has access
        val hasAccess = inviteManager.hasAccess(player.uniqueId, playerWorld)
        debugLogger.debug("Access check result",
            "player" to player.name,
            "playerUuid" to player.uniqueId,
            "worldName" to playerWorld.name,
            "hasAccess" to hasAccess
        )

        if (!hasAccess) {
            event.isCancelled = true
            debugLogger.debug("Teleport CANCELLED - access denied",
                "player" to player.name,
                "worldName" to playerWorld.name,
                "reason" to "no access"
            )
            player.sendMessage(
                Component.text("You don't have access to this world.", NamedTextColor.RED)
            )
        } else {
            // Handle visitor spectator mode
            val role = inviteManager.getPlayerRole(player.uniqueId, playerWorld)
            debugLogger.debug("Checking player role for teleport",
                "player" to player.name,
                "role" to role,
                "isSpectatorOnly" to (role?.isSpectatorOnly() == true)
            )

            if (role?.isSpectatorOnly() == true) {
                // Schedule gamemode change after teleport completes
                player.scheduler.run(plugin, { _ ->
                    if (player.gameMode != GameMode.SPECTATOR) {
                        player.gameMode = GameMode.SPECTATOR
                        player.sendMessage(
                            Component.text("You are visiting this world as a spectator.", NamedTextColor.YELLOW)
                        )
                        debugLogger.debug("Set player to spectator mode as visitor",
                            "player" to player.name,
                            "worldName" to playerWorld.name
                        )
                    }
                }, null)
            }
        }

        debugLogger.debugMethodExit("onPlayerTeleport", "cancelled" to event.isCancelled)
    }

    /**
     * Block portal usage to private world dimensions.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPlayerPortal(event: PlayerPortalEvent) {
        val player = event.player
        val fromWorld = player.world
        val toLocation = event.to ?: run {
            debugLogger.debug("Portal destination location is null",
                "player" to player.name,
                "playerUuid" to player.uniqueId,
                "fromWorld" to fromWorld.name,
                "cause" to event.cause
            )
            return
        }
        val toWorld = toLocation.world ?: run {
            debugLogger.debug("Portal destination world is null",
                "player" to player.name,
                "playerUuid" to player.uniqueId,
                "fromWorld" to fromWorld.name,
                "toLocation" to toLocation
            )
            return
        }

        debugLogger.debugMethodEntry("onPlayerPortal",
            "player" to player.name,
            "playerUuid" to player.uniqueId,
            "fromWorld" to fromWorld.name,
            "toWorld" to toWorld.name,
            "cause" to event.cause,
            "isCancelled" to event.isCancelled
        )

        // Check if destination is a plugin world
        val playerWorld = worldManager.getPlayerWorldFromBukkitWorld(toWorld)
        val isPluginWorld = playerWorld != null
        debugLogger.debug("Checking if destination is plugin world",
            "toWorld" to toWorld.name,
            "isPluginWorld" to isPluginWorld
        )

        if (playerWorld == null) {
            debugLogger.debugMethodExit("onPlayerPortal", "allowed" to true)
            return
        }
        debugLogger.debug("Plugin world details",
            "worldName" to playerWorld.name,
            "worldId" to playerWorld.id,
            "ownerUuid" to playerWorld.ownerUuid,
            "ownerName" to playerWorld.ownerName,
            "isEnabled" to playerWorld.isEnabled
        )

        // Admin bypass
        val hasAdminBypass = player.hasPermission(ADMIN_BYPASS_PERMISSION)
        debugLogger.debug("Checking admin bypass permission",
            "player" to player.name,
            "permission" to ADMIN_BYPASS_PERMISSION,
            "hasPermission" to hasAdminBypass
        )

        if (hasAdminBypass) {
            debugLogger.debugMethodExit("onPlayerPortal", "allowed" to true)
            return
        }

        // Check if player has access
        val hasAccess = inviteManager.hasAccess(player.uniqueId, playerWorld)
        debugLogger.debug("Access check result",
            "player" to player.name,
            "playerUuid" to player.uniqueId,
            "worldName" to playerWorld.name,
            "hasAccess" to hasAccess
        )

        if (!hasAccess) {
            event.isCancelled = true
            debugLogger.debug("Portal CANCELLED - access denied",
                "player" to player.name,
                "worldName" to playerWorld.name,
                "reason" to "no access"
            )
            player.sendMessage(
                Component.text("You don't have access to this world.", NamedTextColor.RED)
            )
        }

        debugLogger.debugMethodExit("onPlayerPortal", "cancelled" to event.isCancelled)
    }

    /**
     * Handle player changing worlds (for edge cases).
     * This is a failsafe in case a player somehow gets into a world they shouldn't be in.
     */
    @EventHandler
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        val player = event.player
        val fromWorld = event.from
        val currentWorld = player.world

        debugLogger.debugMethodEntry("onPlayerChangedWorld",
            "player" to player.name,
            "playerUuid" to player.uniqueId,
            "fromWorld" to fromWorld.name,
            "currentWorld" to currentWorld.name
        )

        // Check if current world is a plugin world
        val playerWorld = worldManager.getPlayerWorldFromBukkitWorld(currentWorld)
        val isPluginWorld = playerWorld != null
        debugLogger.debug("Checking if current world is plugin world",
            "currentWorld" to currentWorld.name,
            "isPluginWorld" to isPluginWorld
        )

        if (playerWorld == null) {
            debugLogger.debugMethodExit("onPlayerChangedWorld", "accessCheckSkipped" to true)
            return
        }
        debugLogger.debug("Plugin world details",
            "worldName" to playerWorld.name,
            "worldId" to playerWorld.id,
            "ownerUuid" to playerWorld.ownerUuid,
            "ownerName" to playerWorld.ownerName,
            "isEnabled" to playerWorld.isEnabled
        )

        // Admin bypass
        val hasAdminBypass = player.hasPermission(ADMIN_BYPASS_PERMISSION)
        debugLogger.debug("Checking admin bypass permission",
            "player" to player.name,
            "permission" to ADMIN_BYPASS_PERMISSION,
            "hasPermission" to hasAdminBypass
        )

        if (hasAdminBypass) {
            debugLogger.debugMethodExit("onPlayerChangedWorld", "allowed" to true)
            return
        }

        // Check if player has access
        val hasAccess = inviteManager.hasAccess(player.uniqueId, playerWorld)
        debugLogger.debug("Access check result",
            "player" to player.name,
            "playerUuid" to player.uniqueId,
            "worldName" to playerWorld.name,
            "hasAccess" to hasAccess
        )

        if (!hasAccess) {
            // Teleport player back to default spawn
            val defaultWorld = Bukkit.getWorlds().firstOrNull()
            debugLogger.debug("No access - initiating emergency teleport",
                "player" to player.name,
                "currentWorld" to playerWorld.name,
                "defaultWorldAvailable" to (defaultWorld != null),
                "defaultWorld" to defaultWorld?.name
            )

            if (defaultWorld != null) {
                plugin.logger.warning("[AccessListener] Player '${player.name}' has no access to '${playerWorld.name}', teleporting to spawn")
                player.scheduler.run(plugin, { _ ->
                    debugLogger.debug("Executing async teleport to default world spawn",
                        "player" to player.name,
                        "targetLocation" to defaultWorld.spawnLocation
                    )
                    player.teleportAsync(defaultWorld.spawnLocation).thenAccept { success ->
                        player.sendMessage(
                            Component.text("You don't have access to this world.", NamedTextColor.RED)
                        )
                        debugLogger.debug("Emergency teleport completed",
                            "player" to player.name,
                            "success" to success
                        )
                    }
                }, null)
            } else {
                plugin.logger.warning("[AccessListener] No default world found to teleport '${player.name}' back to")
                debugLogger.debug("Emergency teleport FAILED - no default world",
                    "player" to player.name,
                    "reason" to "no default world available"
                )
            }
        } else {
            // Handle visitor spectator mode
            val role = inviteManager.getPlayerRole(player.uniqueId, playerWorld)
            debugLogger.debug("Checking player role for gamemode",
                "player" to player.name,
                "role" to role,
                "isSpectatorOnly" to (role?.isSpectatorOnly() == true)
            )

            if (role?.isSpectatorOnly() == true) {
                // Visitor - force spectator mode
                player.scheduler.run(plugin, { _ ->
                    if (player.gameMode != GameMode.SPECTATOR) {
                        player.gameMode = GameMode.SPECTATOR
                        player.sendMessage(
                            Component.text("You are visiting this world as a spectator.", NamedTextColor.YELLOW)
                        )
                        debugLogger.debug("Set visitor to spectator mode",
                            "player" to player.name,
                            "worldName" to playerWorld.name
                        )
                    }
                }, null)
            }
        }

        debugLogger.debugMethodExit("onPlayerChangedWorld", "hasAccess" to hasAccess)
    }

    /**
     * Block respawn in private world if kicked while offline.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        val respawnWorld = event.respawnLocation.world ?: run {
            debugLogger.debug("Respawn world is null",
                "player" to player.name,
                "playerUuid" to player.uniqueId,
                "respawnLocation" to event.respawnLocation
            )
            return
        }

        debugLogger.debugMethodEntry("onPlayerRespawn",
            "player" to player.name,
            "playerUuid" to player.uniqueId,
            "respawnWorld" to respawnWorld.name,
            "isBedSpawn" to event.isBedSpawn,
            "isAnchorSpawn" to event.isAnchorSpawn,
            "respawnLocation" to event.respawnLocation
        )

        // Check if respawn world is a plugin world
        val playerWorld = worldManager.getPlayerWorldFromBukkitWorld(respawnWorld)
        val isPluginWorld = playerWorld != null
        debugLogger.debug("Checking if respawn world is plugin world",
            "respawnWorld" to respawnWorld.name,
            "isPluginWorld" to isPluginWorld
        )

        if (playerWorld == null) {
            debugLogger.debugMethodExit("onPlayerRespawn", "allowed" to true)
            return
        }
        debugLogger.debug("Plugin world details",
            "worldName" to playerWorld.name,
            "worldId" to playerWorld.id,
            "ownerUuid" to playerWorld.ownerUuid,
            "ownerName" to playerWorld.ownerName,
            "isEnabled" to playerWorld.isEnabled
        )

        // Admin bypass
        val hasAdminBypass = player.hasPermission(ADMIN_BYPASS_PERMISSION)
        debugLogger.debug("Checking admin bypass permission",
            "player" to player.name,
            "permission" to ADMIN_BYPASS_PERMISSION,
            "hasPermission" to hasAdminBypass
        )

        if (hasAdminBypass) {
            debugLogger.debugMethodExit("onPlayerRespawn", "allowed" to true)
            return
        }

        // Check if player has access
        val hasAccess = inviteManager.hasAccess(player.uniqueId, playerWorld)
        debugLogger.debug("Access check result",
            "player" to player.name,
            "playerUuid" to player.uniqueId,
            "worldName" to playerWorld.name,
            "hasAccess" to hasAccess
        )

        if (!hasAccess) {
            // Override respawn location to default world
            val defaultWorld = Bukkit.getWorlds().firstOrNull()
            debugLogger.debug("No access - overriding respawn location",
                "player" to player.name,
                "originalWorld" to playerWorld.name,
                "defaultWorldAvailable" to (defaultWorld != null),
                "defaultWorld" to defaultWorld?.name
            )

            if (defaultWorld != null) {
                val originalLocation = event.respawnLocation
                event.respawnLocation = defaultWorld.spawnLocation
                debugLogger.debug("Respawn location overridden",
                    "player" to player.name,
                    "originalLocation" to originalLocation,
                    "newLocation" to event.respawnLocation
                )
                player.sendMessage(
                    Component.text("You no longer have access to that world.", NamedTextColor.RED)
                )
            } else {
                plugin.logger.warning("[AccessListener] No default world found to respawn '${player.name}'")
                debugLogger.debug("Respawn override FAILED - no default world",
                    "player" to player.name,
                    "reason" to "no default world available"
                )
            }
        }

        debugLogger.debugMethodExit("onPlayerRespawn", "hasAccess" to hasAccess)
    }
}
