package tech.bedson.playerworldmanager.managers

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.models.PlayerWorld
import tech.bedson.playerworldmanager.utils.DebugLogger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Manages automatic unloading of empty player worlds after a configurable delay.
 *
 * This manager tracks when worlds become empty and schedules them for unloading.
 * The default world is never unloaded, and unloading can be cancelled if a player
 * enters the world before the delay expires.
 */
class WorldUnloadManager(
    private val plugin: JavaPlugin,
    private val worldManager: WorldManager,
    private val dataManager: DataManager
) {
    private val logger = plugin.logger
    private val debugLogger = DebugLogger(plugin, "WorldUnloadManager")

    // Track when worlds became empty (world ID -> timestamp in millis)
    private val emptyWorldTimestamps: ConcurrentHashMap<UUID, Long> = ConcurrentHashMap()

    // Track scheduled unload tasks (world ID -> scheduled task)
    private val scheduledUnloads: ConcurrentHashMap<UUID, ScheduledTask> = ConcurrentHashMap()

    // Track which worlds are currently unloaded (world ID -> true if unloaded)
    private val unloadedWorlds: ConcurrentHashMap<UUID, Boolean> = ConcurrentHashMap()

    // Configuration
    private var unloadDelayMinutes: Long = 5
    private var autoUnloadEnabled: Boolean = true

    /**
     * Initialize the unload manager with configuration values.
     */
    fun initialize() {
        debugLogger.debugMethodEntry("initialize")

        // Load configuration
        unloadDelayMinutes = plugin.config.getLong("worlds.auto-unload.delay-minutes", 5)
        autoUnloadEnabled = unloadDelayMinutes >= 0

        if (!autoUnloadEnabled) {
            logger.info("[WorldUnloadManager] Auto-unload is disabled (delay-minutes = $unloadDelayMinutes)")
            debugLogger.debug("Auto-unload disabled", "delayMinutes" to unloadDelayMinutes)
        } else {
            logger.info("[WorldUnloadManager] Auto-unload enabled with ${unloadDelayMinutes} minute delay")
            debugLogger.debug("Auto-unload enabled", "delayMinutes" to unloadDelayMinutes)
        }

        debugLogger.debugMethodExit("initialize")
    }

    /**
     * Shutdown the unload manager, cancelling all scheduled tasks.
     */
    fun shutdown() {
        debugLogger.debugMethodEntry("shutdown")

        // Cancel all scheduled unload tasks
        scheduledUnloads.values.forEach { task ->
            task.cancel()
        }
        scheduledUnloads.clear()
        emptyWorldTimestamps.clear()

        logger.info("[WorldUnloadManager] Shutdown complete")
        debugLogger.debugMethodExit("shutdown")
    }

    /**
     * Called when a player leaves a world. Checks if the world is now empty
     * and schedules it for unloading if applicable.
     *
     * @param world The PlayerWorld the player left
     * @param leavingPlayer Optional player who is leaving (used when player quits,
     *                      as they're still counted as in world during quit event)
     */
    fun onPlayerLeaveWorld(world: PlayerWorld, leavingPlayer: Player? = null) {
        debugLogger.debugMethodEntry("onPlayerLeaveWorld",
            "worldName" to world.name,
            "worldId" to world.id,
            "leavingPlayer" to leavingPlayer?.name
        )

        if (!autoUnloadEnabled) {
            debugLogger.debug("Auto-unload disabled, skipping", "worldName" to world.name)
            debugLogger.debugMethodExit("onPlayerLeaveWorld", "disabled")
            return
        }

        // Check if this is the default world (never unload it)
        val defaultWorldName = plugin.config.getString("default-world", "world") ?: "world"
        val overworldName = worldManager.getWorldName(world, World.Environment.NORMAL)
        if (overworldName == defaultWorldName) {
            debugLogger.debug("Skipping default world", "worldName" to world.name)
            debugLogger.debugMethodExit("onPlayerLeaveWorld", "default world")
            return
        }

        // Check if this world is now empty
        val bukkitWorld = worldManager.getBukkitWorld(world)
        if (bukkitWorld == null) {
            debugLogger.debug("Bukkit world not found", "worldName" to world.name)
            debugLogger.debugMethodExit("onPlayerLeaveWorld", "world not loaded")
            return
        }

        // Check all dimensions of this world, excluding the leaving player
        val totalPlayers = countPlayersInWorld(world, leavingPlayer)
        debugLogger.debug("Player count check", "worldName" to world.name, "totalPlayers" to totalPlayers)

        if (totalPlayers == 0) {
            // World is now empty, schedule for unloading
            scheduleUnload(world)
        }

        debugLogger.debugMethodExit("onPlayerLeaveWorld")
    }

    /**
     * Called when a player enters a world. Cancels any scheduled unload
     * for this world.
     *
     * @param world The PlayerWorld the player entered
     */
    fun onPlayerEnterWorld(world: PlayerWorld) {
        debugLogger.debugMethodEntry("onPlayerEnterWorld",
            "worldName" to world.name,
            "worldId" to world.id
        )

        // Cancel any scheduled unload for this world
        cancelScheduledUnload(world.id)

        // Clear empty timestamp
        emptyWorldTimestamps.remove(world.id)

        debugLogger.debugMethodExit("onPlayerEnterWorld")
    }

    /**
     * Check if a world is currently unloaded.
     *
     * @param worldId The UUID of the world to check
     * @return True if the world is unloaded
     */
    fun isWorldUnloaded(worldId: UUID): Boolean {
        return unloadedWorlds[worldId] == true
    }

    /**
     * Mark a world as loaded (called after loading an unloaded world).
     *
     * @param worldId The UUID of the world
     */
    fun markWorldLoaded(worldId: UUID) {
        debugLogger.debug("Marking world as loaded", "worldId" to worldId)
        unloadedWorlds.remove(worldId)
    }

    /**
     * Count total players across all dimensions of a world.
     *
     * @param world The PlayerWorld to check
     * @param excludePlayer Optional player to exclude from count (used during quit events)
     * @return Total number of players in all dimensions
     */
    private fun countPlayersInWorld(world: PlayerWorld, excludePlayer: Player? = null): Int {
        var totalPlayers = 0
        for (env in World.Environment.entries) {
            val bukkitWorld = worldManager.getBukkitWorld(world, env)
            if (bukkitWorld != null) {
                val playerCount = bukkitWorld.players.count { player ->
                    excludePlayer == null || player.uniqueId != excludePlayer.uniqueId
                }
                totalPlayers += playerCount
            }
        }
        return totalPlayers
    }

    /**
     * Schedule a world for unloading after the configured delay.
     *
     * @param world The PlayerWorld to schedule for unloading
     */
    private fun scheduleUnload(world: PlayerWorld) {
        debugLogger.debugMethodEntry("scheduleUnload",
            "worldName" to world.name,
            "worldId" to world.id,
            "delayMinutes" to unloadDelayMinutes
        )

        // Check if already scheduled
        if (scheduledUnloads.containsKey(world.id)) {
            debugLogger.debug("Unload already scheduled", "worldName" to world.name)
            debugLogger.debugMethodExit("scheduleUnload", "already scheduled")
            return
        }

        // Record when the world became empty
        emptyWorldTimestamps[world.id] = System.currentTimeMillis()

        // Schedule the unload task
        val task = Bukkit.getAsyncScheduler().runDelayed(plugin, { _ ->
            // Check again if the world is still empty before unloading
            Bukkit.getGlobalRegionScheduler().run(plugin) { _ ->
                checkAndUnloadWorld(world)
            }
        }, unloadDelayMinutes, TimeUnit.MINUTES)

        scheduledUnloads[world.id] = task
        logger.info("[WorldUnloadManager] Scheduled unload for world '${world.name}' in $unloadDelayMinutes minutes")
        debugLogger.debug("Unload scheduled",
            "worldName" to world.name,
            "worldId" to world.id,
            "delayMinutes" to unloadDelayMinutes
        )

        debugLogger.debugMethodExit("scheduleUnload")
    }

    /**
     * Cancel a scheduled unload for a world.
     *
     * @param worldId The UUID of the world
     */
    private fun cancelScheduledUnload(worldId: UUID) {
        debugLogger.debugMethodEntry("cancelScheduledUnload", "worldId" to worldId)

        val task = scheduledUnloads.remove(worldId)
        if (task != null) {
            task.cancel()
            logger.info("[WorldUnloadManager] Cancelled scheduled unload for world ID: $worldId")
            debugLogger.debug("Cancelled scheduled unload", "worldId" to worldId)
        }

        debugLogger.debugMethodExit("cancelScheduledUnload")
    }

    /**
     * Check if a world is still empty and unload it if so.
     * This runs on the global region scheduler.
     *
     * @param world The PlayerWorld to check and potentially unload
     */
    private fun checkAndUnloadWorld(world: PlayerWorld) {
        debugLogger.debugMethodEntry("checkAndUnloadWorld",
            "worldName" to world.name,
            "worldId" to world.id
        )

        // Remove from scheduled unloads
        scheduledUnloads.remove(world.id)

        // Check if world still exists in data
        val currentWorld = dataManager.loadWorld(world.id)
        if (currentWorld == null) {
            debugLogger.debug("World no longer exists in data", "worldName" to world.name)
            debugLogger.debugMethodExit("checkAndUnloadWorld", "world deleted")
            return
        }

        // Check if world is still empty
        val totalPlayers = countPlayersInWorld(currentWorld)
        if (totalPlayers > 0) {
            logger.info("[WorldUnloadManager] World '${world.name}' is no longer empty, skipping unload")
            debugLogger.debug("World no longer empty", "worldName" to world.name, "playerCount" to totalPlayers)
            emptyWorldTimestamps.remove(world.id)
            debugLogger.debugMethodExit("checkAndUnloadWorld", "not empty")
            return
        }

        // Unload the world
        unloadWorld(currentWorld)

        debugLogger.debugMethodExit("checkAndUnloadWorld")
    }

    /**
     * Unload a world and all its dimensions.
     *
     * @param world The PlayerWorld to unload
     */
    private fun unloadWorld(world: PlayerWorld) {
        debugLogger.debugMethodEntry("unloadWorld",
            "worldName" to world.name,
            "worldId" to world.id
        )

        logger.info("[WorldUnloadManager] Unloading empty world '${world.name}'")

        // Unload all dimensions
        var unloadedCount = 0
        for (env in World.Environment.entries) {
            val worldName = worldManager.getWorldName(world, env)
            val bukkitWorld = Bukkit.getWorld(worldName)
            if (bukkitWorld != null) {
                debugLogger.debug("Unloading dimension",
                    "worldName" to worldName,
                    "environment" to env
                )

                // Unload with save
                val success = Bukkit.unloadWorld(bukkitWorld, true)
                if (success) {
                    unloadedCount++
                    debugLogger.debug("Dimension unloaded successfully", "worldName" to worldName)
                } else {
                    logger.warning("[WorldUnloadManager] Failed to unload dimension: $worldName")
                    debugLogger.debug("Dimension unload failed", "worldName" to worldName)
                }
            }
        }

        if (unloadedCount > 0) {
            // Mark world as unloaded
            unloadedWorlds[world.id] = true
            emptyWorldTimestamps.remove(world.id)
            logger.info("[WorldUnloadManager] Successfully unloaded world '${world.name}' ($unloadedCount dimensions)")
            debugLogger.debug("World unloaded successfully",
                "worldName" to world.name,
                "dimensionsUnloaded" to unloadedCount
            )
        }

        debugLogger.debugMethodExit("unloadWorld")
    }

    /**
     * Check all loaded worlds and schedule unloading for any that are empty.
     * This can be called periodically to catch any worlds that became empty
     * without triggering the player leave event.
     */
    fun checkAllWorldsForUnload() {
        debugLogger.debugMethodEntry("checkAllWorldsForUnload")

        if (!autoUnloadEnabled) {
            debugLogger.debug("Auto-unload disabled, skipping check")
            debugLogger.debugMethodExit("checkAllWorldsForUnload", "disabled")
            return
        }

        val defaultWorldName = plugin.config.getString("default-world", "world") ?: "world"
        val allWorlds = dataManager.getAllWorlds()

        for (world in allWorlds) {
            // Skip if already scheduled or unloaded
            if (scheduledUnloads.containsKey(world.id) || unloadedWorlds[world.id] == true) {
                continue
            }

            // Skip default world (never unload)
            val overworldName = worldManager.getWorldName(world, World.Environment.NORMAL)
            if (overworldName == defaultWorldName) {
                continue
            }

            // Check if world is loaded and empty
            val bukkitWorld = worldManager.getBukkitWorld(world)
            if (bukkitWorld != null) {
                val totalPlayers = countPlayersInWorld(world)
                if (totalPlayers == 0) {
                    debugLogger.debug("Found empty world", "worldName" to world.name)
                    scheduleUnload(world)
                }
            }
        }

        debugLogger.debugMethodExit("checkAllWorldsForUnload")
    }
}
