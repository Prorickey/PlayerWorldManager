package tech.bedson.playerworldmanager.managers

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import tech.bedson.playerworldmanager.models.PlayerData
import tech.bedson.playerworldmanager.models.PlayerWorld
import tech.bedson.playerworldmanager.models.WorldInvite
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
class DataManager(dataFolder: File, private val logger: Logger) {

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
        logger.info("[DataManager] saveWorld: Saving world '${world.name}' (ID: ${world.id}, Owner: ${world.ownerName})")
        worlds[world.id] = world
        val file = File(worldsFolder, "${world.id}.json")
        try {
            file.writeText(gson.toJson(world))
            logger.info("[DataManager] saveWorld: Successfully saved world '${world.name}' to ${file.path}")
        } catch (e: Exception) {
            logger.severe("[DataManager] saveWorld: Failed to save world ${world.name} (${world.id}): ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Load a PlayerWorld from disk by ID.
     */
    fun loadWorld(worldId: UUID): PlayerWorld? {
        logger.info("[DataManager] loadWorld: Loading world by ID: $worldId")
        // Check cache first
        worlds[worldId]?.let {
            logger.info("[DataManager] loadWorld: Found world '${it.name}' in cache")
            return it
        }

        // Load from disk
        val file = File(worldsFolder, "$worldId.json")
        if (!file.exists()) {
            logger.info("[DataManager] loadWorld: World file not found for ID: $worldId")
            return null
        }

        return try {
            val world = gson.fromJson(file.readText(), PlayerWorld::class.java)
            worlds[worldId] = world
            logger.info("[DataManager] loadWorld: Successfully loaded world '${world.name}' from disk")
            world
        } catch (e: Exception) {
            logger.severe("[DataManager] loadWorld: Failed to load world $worldId: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Load a PlayerWorld from disk by name.
     */
    fun loadWorldByName(name: String): PlayerWorld? {
        logger.info("[DataManager] loadWorldByName: Searching for world with name: $name")
        // Check cache first
        worlds.values.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let {
            logger.info("[DataManager] loadWorldByName: Found world '$name' in cache (ID: ${it.id})")
            return it
        }

        logger.info("[DataManager] loadWorldByName: World not in cache, searching disk files")
        // Load all worlds from disk if not in cache
        worldsFolder.listFiles()?.forEach { file ->
            if (file.extension == "json") {
                try {
                    val world = gson.fromJson(file.readText(), PlayerWorld::class.java)
                    worlds[world.id] = world
                    if (world.name.equals(name, ignoreCase = true)) {
                        logger.info("[DataManager] loadWorldByName: Found world '$name' on disk (ID: ${world.id})")
                        return world
                    }
                } catch (e: Exception) {
                    logger.warning("[DataManager] loadWorldByName: Failed to load world file ${file.name}: ${e.message}")
                }
            }
        }

        logger.info("[DataManager] loadWorldByName: World '$name' not found")
        return null
    }

    /**
     * Delete a PlayerWorld from disk.
     */
    fun deleteWorld(worldId: UUID) {
        logger.info("[DataManager] deleteWorld: Deleting world ID: $worldId")
        val world = worlds.remove(worldId)
        if (world != null) {
            logger.info("[DataManager] deleteWorld: Removed world '${world.name}' from cache")
        }
        val file = File(worldsFolder, "$worldId.json")
        if (file.exists()) {
            file.delete()
            logger.info("[DataManager] deleteWorld: Deleted world file: ${file.path}")
        } else {
            logger.info("[DataManager] deleteWorld: World file not found for deletion: $worldId")
        }
    }

    /**
     * Get all loaded worlds.
     */
    fun getAllWorlds(): List<PlayerWorld> {
        return worlds.values.toList()
    }

    /**
     * Get worlds owned by a specific player.
     */
    fun getWorldsByOwner(ownerUuid: UUID): List<PlayerWorld> {
        return worlds.values.filter { it.ownerUuid == ownerUuid }
    }

    // ========================
    // Player Data Management
    // ========================

    /**
     * Save PlayerData to disk.
     */
    fun savePlayerData(data: PlayerData) {
        logger.info("[DataManager] savePlayerData: Saving player data for '${data.username}' (UUID: ${data.uuid})")
        playerData[data.uuid] = data
        val file = File(playersFolder, "${data.uuid}.json")
        try {
            file.writeText(gson.toJson(data))
            logger.info("[DataManager] savePlayerData: Successfully saved player data for '${data.username}'")
        } catch (e: Exception) {
            logger.severe("[DataManager] savePlayerData: Failed to save player data for ${data.username} (${data.uuid}): ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Load PlayerData from disk.
     */
    fun loadPlayerData(playerUuid: UUID): PlayerData? {
        logger.info("[DataManager] loadPlayerData: Loading player data for UUID: $playerUuid")
        // Check cache first
        playerData[playerUuid]?.let {
            logger.info("[DataManager] loadPlayerData: Found player '${it.username}' in cache")
            return it
        }

        // Load from disk
        val file = File(playersFolder, "$playerUuid.json")
        if (!file.exists()) {
            logger.info("[DataManager] loadPlayerData: Player data file not found for UUID: $playerUuid")
            return null
        }

        return try {
            val data = gson.fromJson(file.readText(), PlayerData::class.java)
            playerData[playerUuid] = data
            logger.info("[DataManager] loadPlayerData: Successfully loaded player data for '${data.username}' from disk")
            data
        } catch (e: Exception) {
            logger.severe("[DataManager] loadPlayerData: Failed to load player data for $playerUuid: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Get or create PlayerData for a player.
     */
    fun getOrCreatePlayerData(playerUuid: UUID, username: String): PlayerData {
        logger.info("[DataManager] getOrCreatePlayerData: Getting or creating player data for '$username' (UUID: $playerUuid)")
        return loadPlayerData(playerUuid) ?: PlayerData(playerUuid, username).also {
            logger.info("[DataManager] getOrCreatePlayerData: Creating new player data for '$username'")
            savePlayerData(it)
        }
    }

    /**
     * Get all loaded player data.
     */
    fun getAllPlayerData(): List<PlayerData> {
        return playerData.values.toList()
    }

    // ========================
    // Invite Management
    // ========================

    /**
     * Save all invites to disk.
     */
    fun saveInvites() {
        logger.info("[DataManager] saveInvites: Saving ${invites.size} invites to disk")
        try {
            invitesFile.writeText(gson.toJson(invites))
            logger.info("[DataManager] saveInvites: Successfully saved invites to ${invitesFile.path}")
        } catch (e: Exception) {
            logger.severe("[DataManager] saveInvites: Failed to save invites: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Load all invites from disk.
     */
    fun loadInvites() {
        logger.info("[DataManager] loadInvites: Loading invites from disk")
        if (!invitesFile.exists()) {
            logger.info("[DataManager] loadInvites: Invites file does not exist, skipping")
            return
        }

        try {
            val loadedInvites = gson.fromJson(
                invitesFile.readText(),
                Array<WorldInvite>::class.java
            )
            invites.clear()
            invites.addAll(loadedInvites)
            logger.info("[DataManager] loadInvites: Successfully loaded ${invites.size} invites")
        } catch (e: Exception) {
            logger.severe("[DataManager] loadInvites: Failed to load invites: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Add an invite.
     */
    fun addInvite(invite: WorldInvite) {
        logger.info("[DataManager] addInvite: Adding invite for '${invite.inviteeName}' to world '${invite.worldName}' (World ID: ${invite.worldId})")
        invites.add(invite)
        saveInvites()
    }

    /**
     * Remove an invite.
     */
    fun removeInvite(invite: WorldInvite) {
        logger.info("[DataManager] removeInvite: Removing invite for '${invite.inviteeName}' to world '${invite.worldName}' (World ID: ${invite.worldId})")
        invites.remove(invite)
        saveInvites()
    }

    /**
     * Get all invites for a specific player.
     */
    fun getInvitesForPlayer(playerUuid: UUID): List<WorldInvite> {
        return invites.filter { it.inviteeUuid == playerUuid }
    }

    /**
     * Get all invites for a specific world.
     */
    fun getInvitesForWorld(worldId: UUID): List<WorldInvite> {
        return invites.filter { it.worldId == worldId }
    }

    /**
     * Get a specific invite.
     */
    fun getInvite(worldId: UUID, inviteeUuid: UUID): WorldInvite? {
        return invites.firstOrNull { it.worldId == worldId && it.inviteeUuid == inviteeUuid }
    }

    /**
     * Get all invites.
     */
    fun getAllInvites(): List<WorldInvite> {
        return invites.toList()
    }

    // ========================
    // Bulk Operations
    // ========================

    /**
     * Load all data from disk.
     */
    fun loadAll() {
        logger.info("[DataManager] loadAll: Starting to load all data from disk...")

        // Load all worlds
        val worldFiles = worldsFolder.listFiles()?.filter { it.extension == "json" } ?: emptyList()
        logger.info("[DataManager] loadAll: Found ${worldFiles.size} world files to load")
        worldFiles.forEach { file ->
            try {
                val world = gson.fromJson(file.readText(), PlayerWorld::class.java)
                worlds[world.id] = world
                logger.info("[DataManager] loadAll: Loaded world '${world.name}' (Owner: ${world.ownerName})")
            } catch (e: Exception) {
                logger.warning("[DataManager] loadAll: Failed to load world file ${file.name}: ${e.message}")
            }
        }
        logger.info("[DataManager] loadAll: Successfully loaded ${worlds.size} worlds")

        // Load all player data
        val playerFiles = playersFolder.listFiles()?.filter { it.extension == "json" } ?: emptyList()
        logger.info("[DataManager] loadAll: Found ${playerFiles.size} player data files to load")
        playerFiles.forEach { file ->
            try {
                val data = gson.fromJson(file.readText(), PlayerData::class.java)
                playerData[data.uuid] = data
                logger.info("[DataManager] loadAll: Loaded player data for '${data.username}'")
            } catch (e: Exception) {
                logger.warning("[DataManager] loadAll: Failed to load player data file ${file.name}: ${e.message}")
            }
        }
        logger.info("[DataManager] loadAll: Successfully loaded ${playerData.size} player data records")

        // Load invites
        loadInvites()
        logger.info("[DataManager] loadAll: Data loading complete - ${worlds.size} worlds, ${playerData.size} players, ${invites.size} invites")
    }

    /**
     * Save all data to disk.
     */
    fun saveAll() {
        logger.info("[DataManager] saveAll: Starting to save all data to disk...")
        logger.info("[DataManager] saveAll: Saving ${worlds.size} worlds, ${playerData.size} players, ${invites.size} invites")

        // Save all worlds
        worlds.values.forEach { saveWorld(it) }

        // Save all player data
        playerData.values.forEach { savePlayerData(it) }

        // Save invites
        saveInvites()

        logger.info("[DataManager] saveAll: All data saved successfully")
    }
}
