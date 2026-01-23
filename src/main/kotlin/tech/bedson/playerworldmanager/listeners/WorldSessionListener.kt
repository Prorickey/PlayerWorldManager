package tech.bedson.playerworldmanager.listeners

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.managers.DataManager
import tech.bedson.playerworldmanager.managers.WorldManager
import tech.bedson.playerworldmanager.managers.WorldStateManager
import tech.bedson.playerworldmanager.utils.DebugLogger

/**
 * Handles world session persistence - saving player location on quit
 * and restoring them to their last world on join.
 */
class WorldSessionListener(
    private val plugin: JavaPlugin,
    private val worldManager: WorldManager,
    private val worldStateManager: WorldStateManager,
    private val dataManager: DataManager
) : Listener {

    private val debugLogger = DebugLogger(plugin, "WorldSessionListener")

    /**
     * Save player's current location and world when they quit.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val currentWorld = player.world
        val currentWorldName = currentWorld.name

        debugLogger.debugMethodEntry("onPlayerQuit",
            "player" to player.name,
            "playerUuid" to player.uniqueId,
            "currentWorld" to currentWorldName,
            "location" to player.location
        )

        plugin.logger.info("[WorldSessionListener] PlayerQuit: Player '${player.name}' quitting from world '$currentWorldName'")

        // Save player state (location, inventory, gamemode, etc.)
        debugLogger.debug("Saving player state",
            "player" to player.name,
            "worldName" to currentWorldName,
            "location" to player.location,
            "gameMode" to player.gameMode
        )
        worldStateManager.savePlayerState(player, currentWorldName)

        // Get or create player data
        debugLogger.debug("Getting or creating player data",
            "player" to player.name,
            "playerUuid" to player.uniqueId
        )
        val playerData = dataManager.getOrCreatePlayerData(player.uniqueId, player.name)
        debugLogger.debug("Player data retrieved",
            "player" to player.name,
            "existingLastWorldId" to playerData.lastWorldId
        )

        // Save last world ID for restoration on join (only for plugin worlds)
        val playerWorld = worldManager.getPlayerWorldFromBukkitWorld(currentWorld)
        val isPluginWorld = playerWorld != null
        debugLogger.debug("Checking if current world is plugin world",
            "currentWorld" to currentWorldName,
            "isPluginWorld" to isPluginWorld,
            "pluginWorldName" to playerWorld?.name,
            "pluginWorldId" to playerWorld?.id
        )

        if (playerWorld != null) {
            val previousLastWorldId = playerData.lastWorldId
            playerData.lastWorldId = playerWorld.id
            debugLogger.debug("Updated lastWorldId",
                "player" to player.name,
                "previousLastWorldId" to previousLastWorldId,
                "newLastWorldId" to playerWorld.id,
                "pluginWorldName" to playerWorld.name
            )
            plugin.logger.info("[WorldSessionListener] PlayerQuit: Set lastWorldId for '${player.name}' to '${playerWorld.name}' (${playerWorld.id})")
        } else {
            debugLogger.debug("Not a plugin world - lastWorldId not updated",
                "player" to player.name,
                "currentWorld" to currentWorldName
            )
        }

        // Persist to disk
        debugLogger.debug("Persisting player data to disk",
            "player" to player.name,
            "playerUuid" to player.uniqueId,
            "lastWorldId" to playerData.lastWorldId
        )
        dataManager.savePlayerData(playerData)
        plugin.logger.info("[WorldSessionListener] PlayerQuit: Player data saved for '${player.name}'")

        debugLogger.debugMethodExit("onPlayerQuit", "savedLastWorldId" to playerData.lastWorldId)
    }

    /**
     * Restore player to their last world when they join.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        debugLogger.debugMethodEntry("onPlayerJoin",
            "player" to player.name,
            "playerUuid" to player.uniqueId,
            "currentWorld" to player.world.name,
            "hasPlayedBefore" to player.hasPlayedBefore()
        )

        plugin.logger.info("[WorldSessionListener] PlayerJoin: Player '${player.name}' joining, checking for last world")

        // Load player data
        debugLogger.debug("Loading player data",
            "player" to player.name,
            "playerUuid" to player.uniqueId
        )
        val playerData = dataManager.loadPlayerData(player.uniqueId)
        if (playerData == null) {
            debugLogger.debug("No player data found - skipping restoration",
                "player" to player.name,
                "playerUuid" to player.uniqueId
            )
            plugin.logger.info("[WorldSessionListener] PlayerJoin: No player data for '${player.name}', skipping world restoration")
            debugLogger.debugMethodExit("onPlayerJoin", "restored" to false)
            return
        }

        debugLogger.debug("Player data loaded",
            "player" to player.name,
            "lastWorldId" to playerData.lastWorldId,
            "username" to playerData.username
        )

        val lastWorldId = playerData.lastWorldId
        if (lastWorldId == null) {
            debugLogger.debug("No lastWorldId in player data - skipping restoration",
                "player" to player.name
            )
            plugin.logger.info("[WorldSessionListener] PlayerJoin: No lastWorldId for '${player.name}', skipping world restoration")
            debugLogger.debugMethodExit("onPlayerJoin", "restored" to false)
            return
        }

        // Get the last world
        debugLogger.debug("Loading last world by ID",
            "lastWorldId" to lastWorldId
        )
        val lastWorld = dataManager.loadWorld(lastWorldId)
        if (lastWorld == null) {
            debugLogger.debug("Last world not found in data - skipping restoration",
                "player" to player.name,
                "lastWorldId" to lastWorldId
            )
            plugin.logger.info("[WorldSessionListener] PlayerJoin: Last world (ID: $lastWorldId) not found for '${player.name}', skipping restoration")
            debugLogger.debugMethodExit("onPlayerJoin", "restored" to false)
            return
        }

        debugLogger.debug("Last world found - scheduling teleport",
            "player" to player.name,
            "lastWorldId" to lastWorldId,
            "lastWorldName" to lastWorld.name,
            "lastWorldOwner" to lastWorld.ownerName
        )
        plugin.logger.info("[WorldSessionListener] PlayerJoin: Restoring '${player.name}' to last world '${lastWorld.name}'")

        // Schedule teleport after player is fully joined (on their entity scheduler)
        debugLogger.debug("Scheduling teleport on entity scheduler",
            "player" to player.name,
            "targetWorld" to lastWorld.name
        )
        player.scheduler.run(plugin, { _ ->
            debugLogger.debug("Entity scheduler task executing - initiating teleport",
                "player" to player.name,
                "targetWorld" to lastWorld.name
            )
            worldManager.teleportToWorld(player, lastWorld).thenAccept { success ->
                if (success) {
                    plugin.logger.info("[WorldSessionListener] PlayerJoin: Successfully restored '${player.name}' to world '${lastWorld.name}'")
                    debugLogger.debug("Teleport completed successfully",
                        "player" to player.name,
                        "targetWorld" to lastWorld.name,
                        "success" to true
                    )
                } else {
                    plugin.logger.warning("[WorldSessionListener] PlayerJoin: Failed to restore '${player.name}' to world '${lastWorld.name}'")
                    debugLogger.debug("Teleport failed",
                        "player" to player.name,
                        "targetWorld" to lastWorld.name,
                        "success" to false
                    )
                }
            }
        }, null)

        debugLogger.debugMethodExit("onPlayerJoin", "teleportScheduled" to true)
    }
}
