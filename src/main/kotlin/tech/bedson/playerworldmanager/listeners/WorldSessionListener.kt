package tech.bedson.playerworldmanager.listeners

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.managers.DataManager
import tech.bedson.playerworldmanager.managers.WorldManager
import tech.bedson.playerworldmanager.models.LocationData

/**
 * Handles world session persistence - saving player location on quit
 * and restoring them to their last world on join.
 */
class WorldSessionListener(
    private val plugin: JavaPlugin,
    private val worldManager: WorldManager,
    private val dataManager: DataManager
) : Listener {

    /**
     * Save player's current location and world when they quit.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val currentWorld = player.world

        plugin.logger.info("[WorldSessionListener] PlayerQuit: Player '${player.name}' quitting from world '${currentWorld.name}'")

        // Check if player is in a plugin world
        val playerWorld = worldManager.getPlayerWorldFromBukkitWorld(currentWorld)
        if (playerWorld == null) {
            plugin.logger.info("[WorldSessionListener] PlayerQuit: Player '${player.name}' is not in a plugin world, skipping location save")
            return
        }

        // Get or create player data
        val playerData = dataManager.getOrCreatePlayerData(player.uniqueId, player.name)

        // Save current location
        val location = player.location
        val locationData = LocationData(
            worldName = currentWorld.name,
            x = location.x,
            y = location.y,
            z = location.z,
            yaw = location.yaw,
            pitch = location.pitch
        )
        playerData.worldLocations[playerWorld.id] = locationData
        plugin.logger.info("[WorldSessionListener] PlayerQuit: Saved location for '${player.name}' in world '${playerWorld.name}' dimension '${currentWorld.name}' at (${location.x}, ${location.y}, ${location.z})")

        // Save last world ID for restoration on join
        playerData.lastWorldId = playerWorld.id
        plugin.logger.info("[WorldSessionListener] PlayerQuit: Set lastWorldId for '${player.name}' to '${playerWorld.name}' (${playerWorld.id})")

        // Persist to disk
        dataManager.savePlayerData(playerData)
        plugin.logger.info("[WorldSessionListener] PlayerQuit: Player data saved for '${player.name}'")
    }

    /**
     * Restore player to their last world when they join.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        plugin.logger.info("[WorldSessionListener] PlayerJoin: Player '${player.name}' joining, checking for last world")

        // Load player data
        val playerData = dataManager.loadPlayerData(player.uniqueId)
        if (playerData == null) {
            plugin.logger.info("[WorldSessionListener] PlayerJoin: No player data for '${player.name}', skipping world restoration")
            return
        }

        val lastWorldId = playerData.lastWorldId
        if (lastWorldId == null) {
            plugin.logger.info("[WorldSessionListener] PlayerJoin: No lastWorldId for '${player.name}', skipping world restoration")
            return
        }

        // Get the last world
        val lastWorld = dataManager.loadWorld(lastWorldId)
        if (lastWorld == null) {
            plugin.logger.info("[WorldSessionListener] PlayerJoin: Last world (ID: $lastWorldId) not found for '${player.name}', skipping restoration")
            return
        }

        plugin.logger.info("[WorldSessionListener] PlayerJoin: Restoring '${player.name}' to last world '${lastWorld.name}'")

        // Schedule teleport after player is fully joined (on their entity scheduler)
        player.scheduler.run(plugin, { _ ->
            worldManager.teleportToWorld(player, lastWorld).thenAccept { success ->
                if (success) {
                    plugin.logger.info("[WorldSessionListener] PlayerJoin: Successfully restored '${player.name}' to world '${lastWorld.name}'")
                } else {
                    plugin.logger.warning("[WorldSessionListener] PlayerJoin: Failed to restore '${player.name}' to world '${lastWorld.name}'")
                }
            }
        }, null)
    }
}
