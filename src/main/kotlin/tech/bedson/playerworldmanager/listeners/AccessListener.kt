package tech.bedson.playerworldmanager.listeners

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
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
            return
        }

        plugin.logger.info("[AccessListener] PlayerTeleport: Player '${player.name}' teleporting from '${fromWorld.name}' to '${toWorld.name}' (cause: ${event.cause})")

        // Check if destination is a plugin world
        val playerWorld = worldManager.getPlayerWorldFromBukkitWorld(toWorld) ?: run {
            plugin.logger.info("[AccessListener] PlayerTeleport: Destination world '${toWorld.name}' is not a plugin world, allowing teleport")
            return
        }

        plugin.logger.info("[AccessListener] PlayerTeleport: Destination is plugin world '${playerWorld.name}' owned by '${playerWorld.ownerName}'")

        // Admin bypass
        if (player.hasPermission(ADMIN_BYPASS_PERMISSION)) {
            plugin.logger.info("[AccessListener] PlayerTeleport: Player '${player.name}' has admin bypass permission, allowing teleport")
            return
        }

        // Check if player has access
        val hasAccess = inviteManager.hasAccess(player.uniqueId, playerWorld)
        plugin.logger.info("[AccessListener] PlayerTeleport: Access check for '${player.name}' to world '${playerWorld.name}': $hasAccess")

        if (!hasAccess) {
            event.isCancelled = true
            plugin.logger.info("[AccessListener] PlayerTeleport: CANCELLED - Player '${player.name}' denied access to '${playerWorld.name}'")
            player.sendMessage(
                Component.text("You don't have access to this world.", NamedTextColor.RED)
            )
        }
    }

    /**
     * Block portal usage to private world dimensions.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPlayerPortal(event: PlayerPortalEvent) {
        val player = event.player
        val fromWorld = player.world
        val toLocation = event.to ?: run {
            plugin.logger.warning("[AccessListener] PlayerPortal: Player '${player.name}' portal event has no destination")
            return
        }
        val toWorld = toLocation.world ?: run {
            plugin.logger.warning("[AccessListener] PlayerPortal: Player '${player.name}' portal destination has null world")
            return
        }

        plugin.logger.info("[AccessListener] PlayerPortal: Player '${player.name}' using portal from '${fromWorld.name}' to '${toWorld.name}' (cause: ${event.cause})")

        // Check if destination is a plugin world
        val playerWorld = worldManager.getPlayerWorldFromBukkitWorld(toWorld) ?: run {
            plugin.logger.info("[AccessListener] PlayerPortal: Destination world '${toWorld.name}' is not a plugin world, allowing portal")
            return
        }

        plugin.logger.info("[AccessListener] PlayerPortal: Destination is plugin world '${playerWorld.name}' owned by '${playerWorld.ownerName}'")

        // Admin bypass
        if (player.hasPermission(ADMIN_BYPASS_PERMISSION)) {
            plugin.logger.info("[AccessListener] PlayerPortal: Player '${player.name}' has admin bypass permission, allowing portal")
            return
        }

        // Check if player has access
        val hasAccess = inviteManager.hasAccess(player.uniqueId, playerWorld)
        plugin.logger.info("[AccessListener] PlayerPortal: Access check for '${player.name}' to world '${playerWorld.name}': $hasAccess")

        if (!hasAccess) {
            event.isCancelled = true
            plugin.logger.info("[AccessListener] PlayerPortal: CANCELLED - Player '${player.name}' denied portal access to '${playerWorld.name}'")
            player.sendMessage(
                Component.text("You don't have access to this world.", NamedTextColor.RED)
            )
        }
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

        plugin.logger.info("[AccessListener] PlayerChangedWorld: Player '${player.name}' changed from '${fromWorld.name}' to '${currentWorld.name}'")

        // Check if current world is a plugin world
        val playerWorld = worldManager.getPlayerWorldFromBukkitWorld(currentWorld) ?: run {
            plugin.logger.info("[AccessListener] PlayerChangedWorld: Current world '${currentWorld.name}' is not a plugin world, no access check needed")
            return
        }

        plugin.logger.info("[AccessListener] PlayerChangedWorld: Current world is plugin world '${playerWorld.name}' owned by '${playerWorld.ownerName}'")

        // Admin bypass
        if (player.hasPermission(ADMIN_BYPASS_PERMISSION)) {
            plugin.logger.info("[AccessListener] PlayerChangedWorld: Player '${player.name}' has admin bypass permission")
            return
        }

        // Check if player has access
        val hasAccess = inviteManager.hasAccess(player.uniqueId, playerWorld)
        plugin.logger.info("[AccessListener] PlayerChangedWorld: Access check for '${player.name}' to world '${playerWorld.name}': $hasAccess")

        if (!hasAccess) {
            // Teleport player back to default spawn
            val defaultWorld = Bukkit.getWorlds().firstOrNull()
            if (defaultWorld != null) {
                plugin.logger.warning("[AccessListener] PlayerChangedWorld: Player '${player.name}' has no access to '${playerWorld.name}', teleporting to spawn in '${defaultWorld.name}'")
                player.scheduler.run(plugin, { _ ->
                    player.teleportAsync(defaultWorld.spawnLocation).thenAccept {
                        player.sendMessage(
                            Component.text("You don't have access to this world.", NamedTextColor.RED)
                        )
                        plugin.logger.info("[AccessListener] PlayerChangedWorld: Player '${player.name}' successfully teleported to spawn")
                    }
                }, null)
            } else {
                plugin.logger.warning("[AccessListener] PlayerChangedWorld: FAILED - No default world found to teleport '${player.name}' back to")
            }
        }
    }

    /**
     * Block respawn in private world if kicked while offline.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        val respawnWorld = event.respawnLocation.world ?: run {
            plugin.logger.warning("[AccessListener] PlayerRespawn: Player '${player.name}' has null respawn world")
            return
        }

        plugin.logger.info("[AccessListener] PlayerRespawn: Player '${player.name}' respawning in world '${respawnWorld.name}' (isBedSpawn: ${event.isBedSpawn}, isAnchorSpawn: ${event.isAnchorSpawn})")

        // Check if respawn world is a plugin world
        val playerWorld = worldManager.getPlayerWorldFromBukkitWorld(respawnWorld) ?: run {
            plugin.logger.info("[AccessListener] PlayerRespawn: Respawn world '${respawnWorld.name}' is not a plugin world, allowing respawn")
            return
        }

        plugin.logger.info("[AccessListener] PlayerRespawn: Respawn world is plugin world '${playerWorld.name}' owned by '${playerWorld.ownerName}'")

        // Admin bypass
        if (player.hasPermission(ADMIN_BYPASS_PERMISSION)) {
            plugin.logger.info("[AccessListener] PlayerRespawn: Player '${player.name}' has admin bypass permission, allowing respawn")
            return
        }

        // Check if player has access
        val hasAccess = inviteManager.hasAccess(player.uniqueId, playerWorld)
        plugin.logger.info("[AccessListener] PlayerRespawn: Access check for '${player.name}' to world '${playerWorld.name}': $hasAccess")

        if (!hasAccess) {
            // Override respawn location to default world
            val defaultWorld = Bukkit.getWorlds().firstOrNull()
            if (defaultWorld != null) {
                plugin.logger.warning("[AccessListener] PlayerRespawn: Player '${player.name}' has no access to '${playerWorld.name}', overriding respawn to '${defaultWorld.name}'")
                event.respawnLocation = defaultWorld.spawnLocation
                player.sendMessage(
                    Component.text("You no longer have access to that world.", NamedTextColor.RED)
                )
            } else {
                plugin.logger.warning("[AccessListener] PlayerRespawn: FAILED - No default world found to respawn '${player.name}'")
            }
        }
    }
}
