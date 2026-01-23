package tech.bedson.playerworldmanager.managers

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.models.PlayerData
import tech.bedson.playerworldmanager.models.PlayerWorld
import tech.bedson.playerworldmanager.models.WorldInvite
import tech.bedson.playerworldmanager.utils.DebugLogger
import java.io.File
import java.util.UUID
import java.util.logging.Logger

/**
 * Manages JSON persistence for all plugin data.
 *
 * Data is organized as:
 * - plugins/PlayerWorldManager/worlds/ - Individual world JSON files
 * - plugins/PlayerWorldManager/players/ - Individual player data JSON files
 * - plugins/PlayerWorldManager/invites.json - All pending invites
 */
class DataManager(private val plugin: JavaPlugin, dataFolder: File) {

    private val logger: Logger = plugin.logger
    private val debugLogger = DebugLogger(plugin, "DataManager")

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private val worldsFolder = File(dataFolder, "worlds")
    private val playersFolder = File(dataFolder, "players")
    private val invitesFile = File(dataFolder, "invites.json")

    // In-memory caches
    private val worlds = mutableMapOf<UUID, PlayerWorld>()
    private val playerData = mutableMapOf<UUID, PlayerData>()
    private val invites = mutableListOf<WorldInvite>()

    init {
        // Create directories if they don't exist
        worldsFolder.mkdirs()
        playersFolder.mkdirs()
        dataFolder.mkdirs()
    }

    // ========================
    // World Data Management
    // ========================

    /**
     * Save a PlayerWorld to disk.
     */
    fun saveWorld(world: PlayerWorld) {
        debugLogger.debugMethodEntry("saveWorld",
            "worldName" to world.name,
            "worldId" to world.id,
            "ownerName" to world.ownerName
        )
        logger.info("[DataManager] saveWorld: Saving world '${world.name}' (ID: ${world.id}, Owner: ${world.ownerName})")
        worlds[world.id] = world
        debugLogger.debug("Added world to cache", "cacheSize" to worlds.size)
        val file = File(worldsFolder, "${world.id}.json")
        debugLogger.debug("Writing to file", "filePath" to file.absolutePath)
        try {
            val json = gson.toJson(world)
            debugLogger.debug("Serialized world to JSON", "jsonLength" to json.length)
            file.writeText(json)
            logger.info("[DataManager] saveWorld: Successfully saved world '${world.name}' to ${file.path}")
            debugLogger.debugMethodExit("saveWorld", "success")
        } catch (e: Exception) {
            logger.severe("[DataManager] saveWorld: Failed to save world ${world.name} (${world.id}): ${e.message}")
            debugLogger.debug("Failed to save world",
                "exceptionType" to e.javaClass.simpleName,
                "exceptionMessage" to e.message
            )
            debugLogger.debugMethodExit("saveWorld", "failure: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Load a PlayerWorld from disk by ID.
     */
    fun loadWorld(worldId: UUID): PlayerWorld? {
        debugLogger.debugMethodEntry("loadWorld", "worldId" to worldId)
        logger.info("[DataManager] loadWorld: Loading world by ID: $worldId")
        // Check cache first
        worlds[worldId]?.let {
            logger.info("[DataManager] loadWorld: Found world '${it.name}' in cache")
            debugLogger.debug("Cache hit", "worldName" to it.name, "worldId" to it.id)
            debugLogger.debugMethodExit("loadWorld", it.name)
            return it
        }
        debugLogger.debug("Cache miss, loading from disk")

        // Load from disk
        val file = File(worldsFolder, "$worldId.json")
        debugLogger.debug("Checking file existence", "filePath" to file.absolutePath, "exists" to file.exists())
        if (!file.exists()) {
            logger.info("[DataManager] loadWorld: World file not found for ID: $worldId")
            debugLogger.debugMethodExit("loadWorld", null)
            return null
        }

        return try {
            val json = file.readText()
            debugLogger.debug("Read file", "jsonLength" to json.length)
            val world = gson.fromJson(json, PlayerWorld::class.java)
            worlds[worldId] = world
            debugLogger.debug("Deserialized and cached world", "worldName" to world.name)
            logger.info("[DataManager] loadWorld: Successfully loaded world '${world.name}' from disk")
            debugLogger.debugMethodExit("loadWorld", world.name)
            world
        } catch (e: Exception) {
            logger.severe("[DataManager] loadWorld: Failed to load world $worldId: ${e.message}")
            debugLogger.debug("Failed to load world",
                "exceptionType" to e.javaClass.simpleName,
                "exceptionMessage" to e.message
            )
            debugLogger.debugMethodExit("loadWorld", null)
            e.printStackTrace()
            null
        }
    }

    /**
     * Load a PlayerWorld from disk by name.
     */
    fun loadWorldByName(name: String): PlayerWorld? {
        debugLogger.debugMethodEntry("loadWorldByName", "name" to name)
        logger.info("[DataManager] loadWorldByName: Searching for world with name: $name")
        // Check cache first
        debugLogger.debug("Checking cache", "cacheSize" to worlds.size)
        worlds.values.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let {
            logger.info("[DataManager] loadWorldByName: Found world '$name' in cache (ID: ${it.id})")
            debugLogger.debug("Cache hit", "worldId" to it.id)
            debugLogger.debugMethodExit("loadWorldByName", it.name)
            return it
        }
        debugLogger.debug("Cache miss, searching disk files")

        logger.info("[DataManager] loadWorldByName: World not in cache, searching disk files")
        // Load all worlds from disk if not in cache
        val files = worldsFolder.listFiles()
        debugLogger.debug("Scanning world folder", "fileCount" to (files?.size ?: 0))
        files?.forEach { file ->
            if (file.extension == "json") {
                try {
                    debugLogger.debug("Reading file", "fileName" to file.name)
                    val world = gson.fromJson(file.readText(), PlayerWorld::class.java)
                    worlds[world.id] = world
                    if (world.name.equals(name, ignoreCase = true)) {
                        logger.info("[DataManager] loadWorldByName: Found world '$name' on disk (ID: ${world.id})")
                        debugLogger.debug("Found matching world", "worldId" to world.id)
                        debugLogger.debugMethodExit("loadWorldByName", world.name)
                        return world
                    }
                } catch (e: Exception) {
                    logger.warning("[DataManager] loadWorldByName: Failed to load world file ${file.name}: ${e.message}")
                    debugLogger.debug("Failed to parse file", "fileName" to file.name, "error" to e.message)
                }
            }
        }

        logger.info("[DataManager] loadWorldByName: World '$name' not found")
        debugLogger.debugMethodExit("loadWorldByName", null)
        return null
    }

    /**
     * Delete a PlayerWorld from disk.
     */
    fun deleteWorld(worldId: UUID) {
        debugLogger.debugMethodEntry("deleteWorld", "worldId" to worldId)
        logger.info("[DataManager] deleteWorld: Deleting world ID: $worldId")
        val world = worlds.remove(worldId)
        if (world != null) {
            logger.info("[DataManager] deleteWorld: Removed world '${world.name}' from cache")
            debugLogger.debug("Removed from cache", "worldName" to world.name, "newCacheSize" to worlds.size)
        } else {
            debugLogger.debug("World was not in cache", "worldId" to worldId)
        }
        val file = File(worldsFolder, "$worldId.json")
        debugLogger.debug("Checking file for deletion", "filePath" to file.absolutePath, "exists" to file.exists())
        if (file.exists()) {
            file.delete()
            logger.info("[DataManager] deleteWorld: Deleted world file: ${file.path}")
            debugLogger.debug("File deleted successfully")
        } else {
            logger.info("[DataManager] deleteWorld: World file not found for deletion: $worldId")
            debugLogger.debug("File did not exist")
        }
        debugLogger.debugMethodExit("deleteWorld")
    }

    /**
     * Remove a world and clean up all related data.
     * Used when a world's files are missing and it needs to be purged from the database.
     */
    fun cleanupOrphanedWorld(worldId: UUID) {
        debugLogger.debugMethodEntry("cleanupOrphanedWorld", "worldId" to worldId)
        logger.info("[DataManager] cleanupOrphanedWorld: Removing orphaned world ID: $worldId")

        // Remove from worlds map
        val world = worlds.remove(worldId)
        debugLogger.debug("Removed from worlds map", "found" to (world != null), "worldName" to world?.name)
        if (world != null) {
            // Delete the world file
            val worldFile = File(worldsFolder, "$worldId.json")
            debugLogger.debug("Checking world file", "filePath" to worldFile.absolutePath, "exists" to worldFile.exists())
            if (worldFile.exists()) {
                worldFile.delete()
                logger.info("[DataManager] cleanupOrphanedWorld: Deleted world file for '${world.name}'")
                debugLogger.debug("World file deleted")
            }

            // Remove from owner's player data
            debugLogger.debug("Loading owner player data", "ownerUuid" to world.ownerUuid)
            val playerData = loadPlayerData(world.ownerUuid)
            if (playerData != null) {
                val removed = playerData.ownedWorlds.remove(worldId)
                debugLogger.debug("Removed from owner's ownedWorlds", "removed" to removed, "remainingWorlds" to playerData.ownedWorlds.size)
                savePlayerData(playerData)
                logger.info("[DataManager] cleanupOrphanedWorld: Removed world from owner's data")
            }

            // Remove any invites for this world
            val invitesToRemove = invites.filter { it.worldId == worldId }
            debugLogger.debug("Found invites to remove", "count" to invitesToRemove.size)
            invites.removeAll(invitesToRemove)
            if (invitesToRemove.isNotEmpty()) {
                saveInvites()
                logger.info("[DataManager] cleanupOrphanedWorld: Removed ${invitesToRemove.size} invites")
            }
            debugLogger.debug("Orphaned world cleanup complete", "worldName" to world.name)
        }
        debugLogger.debugMethodExit("cleanupOrphanedWorld")
    }

    /**
     * Get all loaded worlds.
     */
    fun getAllWorlds(): List<PlayerWorld> {
        debugLogger.debugMethodEntry("getAllWorlds")
        val worldList = worlds.values.toList()
        debugLogger.debug("Retrieved all worlds", "count" to worldList.size)
        debugLogger.debugMethodExit("getAllWorlds", worldList.size)
        return worldList
    }

    /**
     * Get worlds owned by a specific player.
     */
    fun getWorldsByOwner(ownerUuid: UUID): List<PlayerWorld> {
        debugLogger.debugMethodEntry("getWorldsByOwner", "ownerUuid" to ownerUuid)
        val ownedWorlds = worlds.values.filter { it.ownerUuid == ownerUuid }
        debugLogger.debug("Retrieved worlds by owner", "count" to ownedWorlds.size, "worldNames" to ownedWorlds.map { it.name })
        debugLogger.debugMethodExit("getWorldsByOwner", ownedWorlds.size)
        return ownedWorlds
    }

    // ========================
    // Player Data Management
    // ========================

    /**
     * Save PlayerData to disk.
     */
    fun savePlayerData(data: PlayerData) {
        debugLogger.debugMethodEntry("savePlayerData",
            "username" to data.username,
            "uuid" to data.uuid,
            "ownedWorldsCount" to data.ownedWorlds.size,
            "worldStatesCount" to data.worldStates.size
        )
        logger.info("[DataManager] savePlayerData: Saving player data for '${data.username}' (UUID: ${data.uuid})")
        playerData[data.uuid] = data
        debugLogger.debug("Added player data to cache", "cacheSize" to playerData.size)
        val file = File(playersFolder, "${data.uuid}.json")
        debugLogger.debug("Writing to file", "filePath" to file.absolutePath)
        try {
            val json = gson.toJson(data)
            debugLogger.debug("Serialized player data to JSON", "jsonLength" to json.length)
            file.writeText(json)
            logger.info("[DataManager] savePlayerData: Successfully saved player data for '${data.username}'")
            debugLogger.debugMethodExit("savePlayerData", "success")
        } catch (e: Exception) {
            logger.severe("[DataManager] savePlayerData: Failed to save player data for ${data.username} (${data.uuid}): ${e.message}")
            debugLogger.debug("Failed to save player data",
                "exceptionType" to e.javaClass.simpleName,
                "exceptionMessage" to e.message
            )
            debugLogger.debugMethodExit("savePlayerData", "failure: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Load PlayerData from disk.
     */
    fun loadPlayerData(playerUuid: UUID): PlayerData? {
        debugLogger.debugMethodEntry("loadPlayerData", "playerUuid" to playerUuid)
        logger.info("[DataManager] loadPlayerData: Loading player data for UUID: $playerUuid")
        // Check cache first
        playerData[playerUuid]?.let {
            logger.info("[DataManager] loadPlayerData: Found player '${it.username}' in cache")
            debugLogger.debug("Cache hit", "username" to it.username)
            debugLogger.debugMethodExit("loadPlayerData", it.username)
            return it
        }
        debugLogger.debug("Cache miss, loading from disk")

        // Load from disk
        val file = File(playersFolder, "$playerUuid.json")
        debugLogger.debug("Checking file existence", "filePath" to file.absolutePath, "exists" to file.exists())
        if (!file.exists()) {
            logger.info("[DataManager] loadPlayerData: Player data file not found for UUID: $playerUuid")
            debugLogger.debugMethodExit("loadPlayerData", null)
            return null
        }

        return try {
            val json = file.readText()
            debugLogger.debug("Read file", "jsonLength" to json.length)
            val data = gson.fromJson(json, PlayerData::class.java)
            playerData[playerUuid] = data
            debugLogger.debug("Deserialized and cached player data", "username" to data.username)
            logger.info("[DataManager] loadPlayerData: Successfully loaded player data for '${data.username}' from disk")
            debugLogger.debugMethodExit("loadPlayerData", data.username)
            data
        } catch (e: Exception) {
            logger.severe("[DataManager] loadPlayerData: Failed to load player data for $playerUuid: ${e.message}")
            debugLogger.debug("Failed to load player data",
                "exceptionType" to e.javaClass.simpleName,
                "exceptionMessage" to e.message
            )
            debugLogger.debugMethodExit("loadPlayerData", null)
            e.printStackTrace()
            null
        }
    }

    /**
     * Get or create PlayerData for a player.
     */
    fun getOrCreatePlayerData(playerUuid: UUID, username: String): PlayerData {
        debugLogger.debugMethodEntry("getOrCreatePlayerData", "playerUuid" to playerUuid, "username" to username)
        logger.info("[DataManager] getOrCreatePlayerData: Getting or creating player data for '$username' (UUID: $playerUuid)")
        val existingData = loadPlayerData(playerUuid)
        if (existingData != null) {
            debugLogger.debug("Found existing player data", "username" to existingData.username)
            debugLogger.debugMethodExit("getOrCreatePlayerData", "existing: ${existingData.username}")
            return existingData
        }
        debugLogger.debug("Creating new player data", "username" to username)
        val newData = PlayerData(playerUuid, username)
        logger.info("[DataManager] getOrCreatePlayerData: Creating new player data for '$username'")
        savePlayerData(newData)
        debugLogger.debugMethodExit("getOrCreatePlayerData", "created: $username")
        return newData
    }

    /**
     * Get all loaded player data.
     */
    fun getAllPlayerData(): List<PlayerData> {
        debugLogger.debugMethodEntry("getAllPlayerData")
        val allData = playerData.values.toList()
        debugLogger.debug("Retrieved all player data", "count" to allData.size)
        debugLogger.debugMethodExit("getAllPlayerData", allData.size)
        return allData
    }

    // ========================
    // Invite Management
    // ========================

    /**
     * Save all invites to disk.
     */
    fun saveInvites() {
        debugLogger.debugMethodEntry("saveInvites", "inviteCount" to invites.size)
        logger.info("[DataManager] saveInvites: Saving ${invites.size} invites to disk")
        debugLogger.debug("Writing to file", "filePath" to invitesFile.absolutePath)
        try {
            val json = gson.toJson(invites)
            debugLogger.debug("Serialized invites to JSON", "jsonLength" to json.length)
            invitesFile.writeText(json)
            logger.info("[DataManager] saveInvites: Successfully saved invites to ${invitesFile.path}")
            debugLogger.debugMethodExit("saveInvites", "success")
        } catch (e: Exception) {
            logger.severe("[DataManager] saveInvites: Failed to save invites: ${e.message}")
            debugLogger.debug("Failed to save invites",
                "exceptionType" to e.javaClass.simpleName,
                "exceptionMessage" to e.message
            )
            debugLogger.debugMethodExit("saveInvites", "failure: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Load all invites from disk.
     */
    fun loadInvites() {
        debugLogger.debugMethodEntry("loadInvites")
        logger.info("[DataManager] loadInvites: Loading invites from disk")
        debugLogger.debug("Checking file existence", "filePath" to invitesFile.absolutePath, "exists" to invitesFile.exists())
        if (!invitesFile.exists()) {
            logger.info("[DataManager] loadInvites: Invites file does not exist, skipping")
            debugLogger.debugMethodExit("loadInvites", "skipped - no file")
            return
        }

        try {
            val json = invitesFile.readText()
            debugLogger.debug("Read file", "jsonLength" to json.length)
            val loadedInvites = gson.fromJson(
                json,
                Array<WorldInvite>::class.java
            )
            invites.clear()
            invites.addAll(loadedInvites)
            debugLogger.debug("Loaded invites into memory", "count" to invites.size)
            logger.info("[DataManager] loadInvites: Successfully loaded ${invites.size} invites")
            debugLogger.debugMethodExit("loadInvites", invites.size)
        } catch (e: Exception) {
            logger.severe("[DataManager] loadInvites: Failed to load invites: ${e.message}")
            debugLogger.debug("Failed to load invites",
                "exceptionType" to e.javaClass.simpleName,
                "exceptionMessage" to e.message
            )
            debugLogger.debugMethodExit("loadInvites", "failure: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Add an invite.
     */
    fun addInvite(invite: WorldInvite) {
        debugLogger.debugMethodEntry("addInvite",
            "inviteeName" to invite.inviteeName,
            "worldName" to invite.worldName,
            "worldId" to invite.worldId
        )
        logger.info("[DataManager] addInvite: Adding invite for '${invite.inviteeName}' to world '${invite.worldName}' (World ID: ${invite.worldId})")
        invites.add(invite)
        debugLogger.debug("Added invite to list", "newInviteCount" to invites.size)
        saveInvites()
        debugLogger.debugMethodExit("addInvite", "success")
    }

    /**
     * Remove an invite.
     */
    fun removeInvite(invite: WorldInvite) {
        debugLogger.debugMethodEntry("removeInvite",
            "inviteeName" to invite.inviteeName,
            "worldName" to invite.worldName,
            "worldId" to invite.worldId
        )
        logger.info("[DataManager] removeInvite: Removing invite for '${invite.inviteeName}' to world '${invite.worldName}' (World ID: ${invite.worldId})")
        val removed = invites.remove(invite)
        debugLogger.debug("Removed invite from list", "wasRemoved" to removed, "newInviteCount" to invites.size)
        saveInvites()
        debugLogger.debugMethodExit("removeInvite", removed)
    }

    /**
     * Get all invites for a specific player.
     */
    fun getInvitesForPlayer(playerUuid: UUID): List<WorldInvite> {
        debugLogger.debugMethodEntry("getInvitesForPlayer", "playerUuid" to playerUuid)
        val playerInvites = invites.filter { it.inviteeUuid == playerUuid }
        debugLogger.debug("Filtered invites for player",
            "totalInvites" to invites.size,
            "matchingInvites" to playerInvites.size
        )
        debugLogger.debugMethodExit("getInvitesForPlayer", playerInvites.size)
        return playerInvites
    }

    /**
     * Get all invites for a specific world.
     */
    fun getInvitesForWorld(worldId: UUID): List<WorldInvite> {
        debugLogger.debugMethodEntry("getInvitesForWorld", "worldId" to worldId)
        val worldInvites = invites.filter { it.worldId == worldId }
        debugLogger.debug("Filtered invites for world",
            "totalInvites" to invites.size,
            "matchingInvites" to worldInvites.size
        )
        debugLogger.debugMethodExit("getInvitesForWorld", worldInvites.size)
        return worldInvites
    }

    /**
     * Get a specific invite.
     */
    fun getInvite(worldId: UUID, inviteeUuid: UUID): WorldInvite? {
        debugLogger.debugMethodEntry("getInvite", "worldId" to worldId, "inviteeUuid" to inviteeUuid)
        val invite = invites.firstOrNull { it.worldId == worldId && it.inviteeUuid == inviteeUuid }
        debugLogger.debug("Invite lookup result", "found" to (invite != null))
        debugLogger.debugMethodExit("getInvite", invite != null)
        return invite
    }

    /**
     * Get all invites.
     */
    fun getAllInvites(): List<WorldInvite> {
        debugLogger.debugMethodEntry("getAllInvites")
        val allInvites = invites.toList()
        debugLogger.debug("Retrieved all invites", "count" to allInvites.size)
        debugLogger.debugMethodExit("getAllInvites", allInvites.size)
        return allInvites
    }

    // ========================
    // Bulk Operations
    // ========================

    /**
     * Load all data from disk.
     */
    fun loadAll() {
        debugLogger.debugMethodEntry("loadAll")
        logger.info("[DataManager] loadAll: Starting to load all data from disk...")
        debugLogger.debug("Starting bulk data load",
            "worldsFolderPath" to worldsFolder.absolutePath,
            "playersFolderPath" to playersFolder.absolutePath
        )

        // Load all worlds
        val worldFiles = worldsFolder.listFiles()?.filter { it.extension == "json" } ?: emptyList()
        logger.info("[DataManager] loadAll: Found ${worldFiles.size} world files to load")
        debugLogger.debug("Found world files", "count" to worldFiles.size)
        var worldSuccessCount = 0
        var worldFailCount = 0
        worldFiles.forEach { file ->
            try {
                debugLogger.debug("Loading world file", "fileName" to file.name)
                val world = gson.fromJson(file.readText(), PlayerWorld::class.java)
                worlds[world.id] = world
                worldSuccessCount++
                logger.info("[DataManager] loadAll: Loaded world '${world.name}' (Owner: ${world.ownerName})")
            } catch (e: Exception) {
                worldFailCount++
                logger.warning("[DataManager] loadAll: Failed to load world file ${file.name}: ${e.message}")
                debugLogger.debug("Failed to load world file",
                    "fileName" to file.name,
                    "exceptionType" to e.javaClass.simpleName,
                    "exceptionMessage" to e.message
                )
            }
        }
        logger.info("[DataManager] loadAll: Successfully loaded ${worlds.size} worlds")
        debugLogger.debug("World loading complete", "successCount" to worldSuccessCount, "failCount" to worldFailCount)

        // Load all player data
        val playerFiles = playersFolder.listFiles()?.filter { it.extension == "json" } ?: emptyList()
        logger.info("[DataManager] loadAll: Found ${playerFiles.size} player data files to load")
        debugLogger.debug("Found player data files", "count" to playerFiles.size)
        var playerSuccessCount = 0
        var playerFailCount = 0
        playerFiles.forEach { file ->
            try {
                debugLogger.debug("Loading player data file", "fileName" to file.name)
                val data = gson.fromJson(file.readText(), PlayerData::class.java)
                playerData[data.uuid] = data
                playerSuccessCount++
                logger.info("[DataManager] loadAll: Loaded player data for '${data.username}'")
            } catch (e: Exception) {
                playerFailCount++
                logger.warning("[DataManager] loadAll: Failed to load player data file ${file.name}: ${e.message}")
                debugLogger.debug("Failed to load player data file",
                    "fileName" to file.name,
                    "exceptionType" to e.javaClass.simpleName,
                    "exceptionMessage" to e.message
                )
            }
        }
        logger.info("[DataManager] loadAll: Successfully loaded ${playerData.size} player data records")
        debugLogger.debug("Player data loading complete", "successCount" to playerSuccessCount, "failCount" to playerFailCount)

        // Load invites
        debugLogger.debug("Loading invites")
        loadInvites()

        logger.info("[DataManager] loadAll: Data loading complete - ${worlds.size} worlds, ${playerData.size} players, ${invites.size} invites")
        debugLogger.debugState("loadAll complete",
            "worldsCount" to worlds.size,
            "playerDataCount" to playerData.size,
            "invitesCount" to invites.size
        )
        debugLogger.debugMethodExit("loadAll", "success")
    }

    /**
     * Save all data to disk.
     */
    fun saveAll() {
        debugLogger.debugMethodEntry("saveAll")
        logger.info("[DataManager] saveAll: Starting to save all data to disk...")
        logger.info("[DataManager] saveAll: Saving ${worlds.size} worlds, ${playerData.size} players, ${invites.size} invites")
        debugLogger.debug("Starting bulk data save",
            "worldsCount" to worlds.size,
            "playerDataCount" to playerData.size,
            "invitesCount" to invites.size
        )

        // Save all worlds
        debugLogger.debug("Saving all worlds")
        worlds.values.forEach { saveWorld(it) }
        debugLogger.debug("All worlds saved")

        // Save all player data
        debugLogger.debug("Saving all player data")
        playerData.values.forEach { savePlayerData(it) }
        debugLogger.debug("All player data saved")

        // Save invites
        debugLogger.debug("Saving invites")
        saveInvites()

        logger.info("[DataManager] saveAll: All data saved successfully")
        debugLogger.debugMethodExit("saveAll", "success")
    }
}
