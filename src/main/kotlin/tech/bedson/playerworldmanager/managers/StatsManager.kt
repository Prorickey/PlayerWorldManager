package tech.bedson.playerworldmanager.managers

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.models.PlayerStatistics
import tech.bedson.playerworldmanager.models.WorldStatistics
import tech.bedson.playerworldmanager.utils.DebugLogger
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/**
 * Manages world statistics persistence and tracking.
 *
 * Statistics are stored in:
 * - plugins/PlayerWorldManager/stats/ - Individual world statistics JSON files
 */
class StatsManager(
    private val plugin: JavaPlugin,
    private val dataManager: DataManager,
    private val worldManager: WorldManager
) {
    private val logger: Logger = plugin.logger
    private val debugLogger = DebugLogger(plugin, "StatsManager")
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private val statsFolder = File(plugin.dataFolder, "stats")

    // In-memory cache of world statistics (thread-safe for Folia's multi-threaded environment)
    private val worldStats = ConcurrentHashMap<UUID, WorldStatistics>()

    init {
        statsFolder.mkdirs()
    }

    // ========================
    // Statistics Retrieval
    // ========================

    /**
     * Get statistics for a world, creating if it doesn't exist.
     */
    fun getWorldStats(worldId: UUID): WorldStatistics {
        debugLogger.debugMethodEntry("getWorldStats", "worldId" to worldId)

        // Check cache first
        worldStats[worldId]?.let {
            debugLogger.debug("Cache hit", "worldId" to worldId)
            debugLogger.debugMethodExit("getWorldStats", "cached")
            return it
        }

        // Try loading from disk
        val stats = loadWorldStats(worldId) ?: WorldStatistics(worldId)
        worldStats[worldId] = stats
        debugLogger.debug("Stats loaded/created", "worldId" to worldId, "isNew" to (stats.blocksPlaced == 0L))
        debugLogger.debugMethodExit("getWorldStats", stats.toDebugString())
        return stats
    }

    /**
     * Get statistics for a world by its Bukkit world.
     */
    fun getWorldStatsByBukkitWorld(bukkitWorld: org.bukkit.World): WorldStatistics? {
        debugLogger.debugMethodEntry("getWorldStatsByBukkitWorld", "worldName" to bukkitWorld.name)

        val playerWorld = worldManager.getPlayerWorldFromBukkitWorld(bukkitWorld)
        if (playerWorld == null) {
            debugLogger.debug("No PlayerWorld found for Bukkit world", "worldName" to bukkitWorld.name)
            debugLogger.debugMethodExit("getWorldStatsByBukkitWorld", null)
            return null
        }

        val stats = getWorldStats(playerWorld.id)
        debugLogger.debugMethodExit("getWorldStatsByBukkitWorld", stats.toDebugString())
        return stats
    }

    /**
     * Get the PlayerWorld ID from a Bukkit world, if it's a plugin world.
     */
    fun getWorldIdFromBukkitWorld(bukkitWorld: org.bukkit.World): UUID? {
        return worldManager.getPlayerWorldFromBukkitWorld(bukkitWorld)?.id
    }

    /**
     * Get player statistics within a specific world.
     */
    fun getPlayerStats(worldId: UUID, playerUuid: UUID): PlayerStatistics {
        debugLogger.debugMethodEntry("getPlayerStats", "worldId" to worldId, "playerUuid" to playerUuid)
        val worldStats = getWorldStats(worldId)
        val playerStats = worldStats.getOrCreatePlayerStats(playerUuid)
        debugLogger.debugMethodExit("getPlayerStats", playerStats.toDebugString())
        return playerStats
    }

    // ========================
    // Statistics Updates
    // ========================

    /**
     * Record a block placed in a world.
     */
    fun recordBlockPlaced(worldId: UUID, playerUuid: UUID) {
        debugLogger.debugMethodEntry("recordBlockPlaced", "worldId" to worldId, "playerUuid" to playerUuid)
        val stats = getWorldStats(worldId)
        stats.blocksPlaced++
        stats.getOrCreatePlayerStats(playerUuid).blocksPlaced++
        debugLogger.debug("Block placed recorded", "totalBlocksPlaced" to stats.blocksPlaced)
        debugLogger.debugMethodExit("recordBlockPlaced")
    }

    /**
     * Record a block broken in a world.
     */
    fun recordBlockBroken(worldId: UUID, playerUuid: UUID) {
        debugLogger.debugMethodEntry("recordBlockBroken", "worldId" to worldId, "playerUuid" to playerUuid)
        val stats = getWorldStats(worldId)
        stats.blocksBroken++
        stats.getOrCreatePlayerStats(playerUuid).blocksBroken++
        debugLogger.debug("Block broken recorded", "totalBlocksBroken" to stats.blocksBroken)
        debugLogger.debugMethodExit("recordBlockBroken")
    }

    /**
     * Record a mob killed in a world.
     */
    fun recordMobKilled(worldId: UUID, playerUuid: UUID) {
        debugLogger.debugMethodEntry("recordMobKilled", "worldId" to worldId, "playerUuid" to playerUuid)
        val stats = getWorldStats(worldId)
        stats.mobsKilled++
        stats.getOrCreatePlayerStats(playerUuid).mobsKilled++
        debugLogger.debug("Mob killed recorded", "totalMobsKilled" to stats.mobsKilled)
        debugLogger.debugMethodExit("recordMobKilled")
    }

    /**
     * Record an animal killed in a world.
     */
    fun recordAnimalKilled(worldId: UUID, playerUuid: UUID) {
        debugLogger.debugMethodEntry("recordAnimalKilled", "worldId" to worldId, "playerUuid" to playerUuid)
        val stats = getWorldStats(worldId)
        stats.animalsKilled++
        stats.getOrCreatePlayerStats(playerUuid).animalsKilled++
        debugLogger.debug("Animal killed recorded", "totalAnimalsKilled" to stats.animalsKilled)
        debugLogger.debugMethodExit("recordAnimalKilled")
    }

    /**
     * Record a player kill in a world.
     */
    fun recordPlayerKill(worldId: UUID, killerUuid: UUID) {
        debugLogger.debugMethodEntry("recordPlayerKill", "worldId" to worldId, "killerUuid" to killerUuid)
        val stats = getWorldStats(worldId)
        stats.playerKills++
        stats.getOrCreatePlayerStats(killerUuid).playerKills++
        debugLogger.debug("Player kill recorded", "totalPlayerKills" to stats.playerKills)
        debugLogger.debugMethodExit("recordPlayerKill")
    }

    /**
     * Record a player death in a world.
     */
    fun recordPlayerDeath(worldId: UUID, playerUuid: UUID) {
        debugLogger.debugMethodEntry("recordPlayerDeath", "worldId" to worldId, "playerUuid" to playerUuid)
        val stats = getWorldStats(worldId)
        stats.playerDeaths++
        stats.getOrCreatePlayerStats(playerUuid).deaths++
        debugLogger.debug("Player death recorded", "totalPlayerDeaths" to stats.playerDeaths)
        debugLogger.debugMethodExit("recordPlayerDeath")
    }

    /**
     * Record an item crafted in a world.
     */
    fun recordItemCrafted(worldId: UUID, playerUuid: UUID, amount: Int = 1) {
        debugLogger.debugMethodEntry("recordItemCrafted", "worldId" to worldId, "playerUuid" to playerUuid, "amount" to amount)
        val stats = getWorldStats(worldId)
        stats.itemsCrafted += amount
        stats.getOrCreatePlayerStats(playerUuid).itemsCrafted += amount
        debugLogger.debug("Item crafted recorded", "totalItemsCrafted" to stats.itemsCrafted)
        debugLogger.debugMethodExit("recordItemCrafted")
    }

    /**
     * Record play time for a player in a world.
     */
    fun recordPlayTime(worldId: UUID, playerUuid: UUID, durationMs: Long) {
        debugLogger.debugMethodEntry("recordPlayTime", "worldId" to worldId, "playerUuid" to playerUuid, "durationMs" to durationMs)
        val stats = getWorldStats(worldId)
        stats.timePlayed += durationMs
        stats.getOrCreatePlayerStats(playerUuid).timePlayed += durationMs
        debugLogger.debug("Play time recorded", "totalTimePlayed" to stats.timePlayed)
        debugLogger.debugMethodExit("recordPlayTime")
    }

    /**
     * Start tracking session time for a player.
     */
    fun startPlayerSession(worldId: UUID, playerUuid: UUID) {
        debugLogger.debugMethodEntry("startPlayerSession", "worldId" to worldId, "playerUuid" to playerUuid)
        val playerStats = getPlayerStats(worldId, playerUuid)
        playerStats.lastJoinTime = System.currentTimeMillis()
        debugLogger.debug("Session started", "lastJoinTime" to playerStats.lastJoinTime)
        debugLogger.debugMethodExit("startPlayerSession")
    }

    /**
     * End tracking session time for a player and record the duration.
     */
    fun endPlayerSession(worldId: UUID, playerUuid: UUID) {
        debugLogger.debugMethodEntry("endPlayerSession", "worldId" to worldId, "playerUuid" to playerUuid)
        val playerStats = getPlayerStats(worldId, playerUuid)
        if (playerStats.lastJoinTime > 0) {
            val duration = System.currentTimeMillis() - playerStats.lastJoinTime
            recordPlayTime(worldId, playerUuid, duration)
            playerStats.lastJoinTime = 0
            debugLogger.debug("Session ended", "sessionDuration" to duration)
        }
        debugLogger.debugMethodExit("endPlayerSession")
    }

    // ========================
    // Persistence
    // ========================

    /**
     * Load world statistics from disk.
     */
    private fun loadWorldStats(worldId: UUID): WorldStatistics? {
        debugLogger.debugMethodEntry("loadWorldStats", "worldId" to worldId)
        val file = File(statsFolder, "$worldId.json")
        if (!file.exists()) {
            debugLogger.debug("Stats file not found", "worldId" to worldId)
            debugLogger.debugMethodExit("loadWorldStats", null)
            return null
        }

        return try {
            val json = file.readText()
            val stats = gson.fromJson(json, WorldStatistics::class.java)
            debugLogger.debug("Stats loaded from disk", "worldId" to worldId)
            debugLogger.debugMethodExit("loadWorldStats", "success")
            stats
        } catch (e: Exception) {
            logger.warning("[StatsManager] Failed to load stats for world $worldId: ${e.message}")
            debugLogger.debug("Failed to load stats", "error" to e.message)
            debugLogger.debugMethodExit("loadWorldStats", "error")
            null
        }
    }

    /**
     * Save world statistics to disk.
     */
    fun saveWorldStats(worldId: UUID) {
        debugLogger.debugMethodEntry("saveWorldStats", "worldId" to worldId)
        val stats = worldStats[worldId] ?: return
        val file = File(statsFolder, "$worldId.json")
        try {
            file.writeText(gson.toJson(stats))
            debugLogger.debug("Stats saved to disk", "worldId" to worldId)
            debugLogger.debugMethodExit("saveWorldStats", "success")
        } catch (e: Exception) {
            logger.severe("[StatsManager] Failed to save stats for world $worldId: ${e.message}")
            debugLogger.debug("Failed to save stats", "error" to e.message)
            debugLogger.debugMethodExit("saveWorldStats", "error")
        }
    }

    /**
     * Save all statistics to disk.
     */
    fun saveAll() {
        debugLogger.debugMethodEntry("saveAll")
        logger.info("[StatsManager] Saving all statistics (${worldStats.size} worlds)...")
        worldStats.keys.forEach { worldId ->
            saveWorldStats(worldId)
        }
        logger.info("[StatsManager] Statistics saved successfully")
        debugLogger.debugMethodExit("saveAll")
    }

    /**
     * Load all statistics from disk.
     */
    fun loadAll() {
        debugLogger.debugMethodEntry("loadAll")
        logger.info("[StatsManager] Loading all statistics from disk...")

        val files = statsFolder.listFiles()?.filter { it.extension == "json" } ?: emptyList()
        var successCount = 0
        var failCount = 0

        files.forEach { file ->
            try {
                val worldId = UUID.fromString(file.nameWithoutExtension)
                val stats = gson.fromJson(file.readText(), WorldStatistics::class.java)
                worldStats[worldId] = stats
                successCount++
            } catch (e: Exception) {
                logger.warning("[StatsManager] Failed to load stats file ${file.name}: ${e.message}")
                failCount++
            }
        }

        logger.info("[StatsManager] Loaded $successCount statistics files ($failCount failed)")
        debugLogger.debug("Statistics loaded", "successCount" to successCount, "failCount" to failCount)
        debugLogger.debugMethodExit("loadAll")
    }

    /**
     * Delete statistics for a world.
     */
    fun deleteWorldStats(worldId: UUID) {
        debugLogger.debugMethodEntry("deleteWorldStats", "worldId" to worldId)
        worldStats.remove(worldId)
        val file = File(statsFolder, "$worldId.json")
        if (file.exists()) {
            file.delete()
            logger.info("[StatsManager] Deleted statistics for world $worldId")
        }
        debugLogger.debugMethodExit("deleteWorldStats")
    }

    /**
     * Get all cached world statistics.
     */
    fun getAllWorldStats(): Map<UUID, WorldStatistics> {
        return worldStats.toMap()
    }
}
