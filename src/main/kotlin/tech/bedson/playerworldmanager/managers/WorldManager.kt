package tech.bedson.playerworldmanager.managers

import com.google.common.collect.ImmutableList
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import com.mojang.serialization.Dynamic
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.NbtException
import net.minecraft.nbt.ReportedNbtException
import net.minecraft.resources.ResourceKey
import net.minecraft.server.WorldLoader
import net.minecraft.server.dedicated.DedicatedServer
import net.minecraft.server.dedicated.DedicatedServerProperties
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.GsonHelper
import net.minecraft.world.Difficulty
import net.minecraft.world.entity.ai.village.VillageSiege
import net.minecraft.world.entity.npc.CatSpawner
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner
import net.minecraft.world.level.CustomSpawner
import net.minecraft.world.level.GameType
import net.minecraft.world.level.LevelSettings
import net.minecraft.world.level.biome.BiomeManager
import net.minecraft.world.level.dimension.LevelStem
import net.minecraft.world.level.levelgen.PatrolSpawner
import net.minecraft.world.level.levelgen.PhantomSpawner
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.storage.LevelStorageSource
import net.minecraft.world.level.storage.PrimaryLevelData
import net.minecraft.world.level.validation.ContentValidationException
import org.bukkit.Bukkit
import org.bukkit.GameRules
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.craftbukkit.CraftServer
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.generator.CraftWorldInfo
import org.bukkit.entity.Player
import org.bukkit.generator.BiomeProvider
import org.bukkit.generator.ChunkGenerator
import org.bukkit.generator.WorldInfo
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.models.*
import tech.bedson.playerworldmanager.utils.DebugLogger
import tech.bedson.playerworldmanager.utils.VoidGenerator
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger

/**
 * Manages player worlds - creation, deletion, loading, unloading, and teleportation.
 *
 * This manager is Folia-compatible and uses:
 * - GlobalRegionScheduler for world operations
 * - Entity schedulers for player operations
 * - Async teleportation for player movement
 */
class WorldManager(
    private val plugin: JavaPlugin,
    private val dataManager: DataManager,
    private val worldStateManager: WorldStateManager
) {

    private val logger: Logger = plugin.logger
    private val debugLogger = DebugLogger(plugin, "WorldManager")

    // Reference to WorldUnloadManager (set after initialization to avoid circular dependency)
    private var worldUnloadManager: WorldUnloadManager? = null

    /**
     * Set the WorldUnloadManager reference.
     * This is called after both managers are created to avoid circular dependency.
     */
    fun setWorldUnloadManager(manager: WorldUnloadManager) {
        this.worldUnloadManager = manager
    }

    // World name validation regex (alphanumeric + underscores)
    private val namePattern = Regex("^[a-zA-Z0-9_]+$")

    // Max world name length
    private val maxNameLength = 32

    // Default world limit per player
    private val defaultWorldLimit = 3

    // File to store worlds pending deletion (on Folia, we can't unload worlds dynamically)
    private val pendingDeletionsFile = File(plugin.dataFolder, "pending-deletions.txt")

    // Track deleted world names in memory (Folia can't unload worlds, so they persist in Bukkit.getWorld())
    // This prevents reusing stale cached worlds when creating new worlds with the same name
    private val deletedWorldNames: MutableSet<String> = mutableSetOf()

    // Track worlds that need to be removed from the database (orphaned entries with missing folders)
    private val worldsToCleanup: MutableList<PlayerWorld> = mutableListOf()

    companion object {
        // World name suffixes for dimensions
        private const val NETHER_SUFFIX = "_nether"
        private const val END_SUFFIX = "_the_end"
    }

    /**
     * Initialize the world manager - load all worlds on startup.
     *
     * IMPORTANT: This runs synchronously on the server thread during onEnable.
     * We cannot use GlobalRegionScheduler here as it would cause a deadlock.
     */
    fun initialize() {
        debugLogger.debugMethodEntry("initialize")
        logger.info("Initializing WorldManager...")

        debugLogger.debug("Processing pending deletions from previous server runs")
        // Process any pending deletions from previous server runs (before loading worlds)
        processPendingDeletions()

        // Load all worlds from data
        val worlds = dataManager.getAllWorlds()
        logger.info("Found ${worlds.size} worlds to load")
        debugLogger.debug("Retrieved worlds from DataManager", "worldCount" to worlds.size)

        // Load each world SYNCHRONOUSLY (we're on server thread, can't use scheduler)
        var successCount = 0
        var failCount = 0
        worlds.forEach { world ->
            debugLogger.debug("Loading world", "name" to world.name, "id" to world.id, "owner" to world.ownerName)
            val success = loadWorldSync(world)
            if (success) {
                logger.info("Loaded world: ${world.name} (owner: ${world.ownerName})")
                successCount++
            } else {
                logger.warning("Failed to load world: ${world.name}")
                failCount++
            }
        }

        debugLogger.debug("World loading complete", "successCount" to successCount, "failCount" to failCount)

        // Process cleanup list for orphaned worlds (folders missing on disk)
        debugLogger.debug("Processing cleanup list for orphaned worlds")
        processCleanupList()

        logger.info("WorldManager initialization complete")
        debugLogger.debugMethodExit("initialize")
    }

    /**
     * Shutdown the world manager - save and unload all worlds.
     */
    fun shutdown() {
        debugLogger.debugMethodEntry("shutdown")
        logger.info("Shutting down WorldManager...")

        val worlds = getLoadedWorlds()
        logger.info("Unloading ${worlds.size} worlds...")
        debugLogger.debug("Preparing to unload worlds", "worldCount" to worlds.size)
        debugLogger.debugState("shutdown", "loadedWorlds" to worlds.map { it.name })

        // Unload all worlds
        val futures = worlds.map { world ->
            debugLogger.debug("Scheduling world unload", "worldName" to world.name, "worldId" to world.id)
            unloadWorld(world)
        }

        debugLogger.debug("Waiting for all unloads to complete", "futureCount" to futures.size)
        // Wait for all unloads to complete
        CompletableFuture.allOf(*futures.toTypedArray()).join()

        logger.info("WorldManager shutdown complete")
        debugLogger.debugMethodExit("shutdown")
    }

    /**
     * Create a new world with all 3 dimensions.
     *
     * @param owner The player creating the world
     * @param name The world name
     * @param worldType The type of world generation
     * @param seed Optional seed for world generation
     * @return CompletableFuture with Result containing PlayerWorld or error message
     */
    fun createWorld(
        owner: Player,
        name: String,
        worldType: WorldType,
        seed: Long? = null
    ): CompletableFuture<Result<PlayerWorld>> {
        debugLogger.debugMethodEntry("createWorld",
            "ownerName" to owner.name,
            "ownerUuid" to owner.uniqueId,
            "name" to name,
            "worldType" to worldType,
            "seed" to seed
        )
        logger.info("[WorldManager] createWorld: Player '${owner.name}' attempting to create world '$name' (Type: $worldType, Seed: ${seed ?: "random"})")
        val future = CompletableFuture<Result<PlayerWorld>>()

        // Validate world name
        debugLogger.debug("Validating world name", "name" to name)
        val validationError = validateWorldName(name)
        if (validationError != null) {
            logger.warning("[WorldManager] createWorld: Validation failed for world '$name': $validationError")
            debugLogger.debug("World name validation failed", "name" to name, "error" to validationError)
            debugLogger.debugMethodExit("createWorld", "failure: $validationError")
            future.complete(Result.failure(IllegalArgumentException(validationError)))
            return future
        }
        debugLogger.debug("World name validation passed", "name" to name)

        // Check if player can create more worlds
        val currentWorldCount = getWorldCount(owner.uniqueId)
        val worldLimit = dataManager.loadPlayerData(owner.uniqueId)?.worldLimit ?: defaultWorldLimit
        debugLogger.debug("Checking world limit", "currentCount" to currentWorldCount, "limit" to worldLimit)
        if (!canCreateWorld(owner)) {
            val limit = dataManager.loadPlayerData(owner.uniqueId)?.worldLimit ?: defaultWorldLimit
            logger.warning("[WorldManager] createWorld: Player '${owner.name}' has reached world limit ($limit worlds)")
            debugLogger.debug("World limit reached", "player" to owner.name, "currentCount" to currentWorldCount, "limit" to limit)
            debugLogger.debugMethodExit("createWorld", "failure: world limit reached")
            future.complete(Result.failure(IllegalStateException("You have reached your world limit ($limit worlds)")))
            return future
        }
        debugLogger.debug("World limit check passed", "currentCount" to currentWorldCount, "limit" to worldLimit)

        // Check if world name already exists for this player
        val existingWorlds = dataManager.getWorldsByOwner(owner.uniqueId)
        debugLogger.debug("Checking for existing world with same name", "existingWorldCount" to existingWorlds.size)
        val existingWorld = existingWorlds.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (existingWorld != null) {
            logger.warning("[WorldManager] createWorld: Player '${owner.name}' already has a world named '$name'")
            debugLogger.debug("Duplicate world name found", "existingWorldId" to existingWorld.id)
            debugLogger.debugMethodExit("createWorld", "failure: duplicate name")
            future.complete(Result.failure(IllegalArgumentException("You already have a world named '$name'")))
            return future
        }
        debugLogger.debug("No duplicate world name found")

        // Create PlayerWorld object
        val worldId = UUID.randomUUID()
        debugLogger.debug("Creating PlayerWorld object", "worldId" to worldId)
        val playerWorld = PlayerWorld(
            id = worldId,
            name = name,
            ownerUuid = owner.uniqueId,
            ownerName = owner.name,
            worldType = worldType,
            seed = seed,
            createdAt = System.currentTimeMillis()
        )
        logger.info("[WorldManager] createWorld: Created PlayerWorld object with ID: ${playerWorld.id}")
        debugLogger.debugState("playerWorld",
            "id" to playerWorld.id,
            "name" to playerWorld.name,
            "ownerUuid" to playerWorld.ownerUuid,
            "worldType" to playerWorld.worldType,
            "seed" to playerWorld.seed,
            "createdAt" to playerWorld.createdAt
        )

        // Create worlds on global region scheduler
        debugLogger.debug("Scheduling world creation on GlobalRegionScheduler")
        Bukkit.getGlobalRegionScheduler().run(plugin) { _ ->
            try {
                debugLogger.debug("GlobalRegionScheduler task started for world creation", "worldName" to playerWorld.name)
                logger.info("[WorldManager] createWorld: Creating overworld dimension for '${playerWorld.name}'")
                // Create overworld
                val overworldName = getWorldName(playerWorld, World.Environment.NORMAL)
                val overworld = createDimension(overworldName, World.Environment.NORMAL, worldType, seed)
                if (overworld == null) {
                    logger.severe("[WorldManager] createWorld: Failed to create overworld for '${playerWorld.name}'")
                    future.complete(Result.failure(RuntimeException("Failed to create overworld")))
                    return@run
                }
                logger.info("[WorldManager] createWorld: Successfully created overworld '$overworldName'")

                // Set spawn location
                val spawnLocation = overworld.spawnLocation
                playerWorld.spawnLocation = spawnLocation.toSimpleLocation()
                logger.info("[WorldManager] createWorld: Set spawn location for '${playerWorld.name}' at ${spawnLocation.x}, ${spawnLocation.y}, ${spawnLocation.z}")

                // Create nether
                logger.info("[WorldManager] createWorld: Creating nether dimension for '${playerWorld.name}'")
                val netherName = getWorldName(playerWorld, World.Environment.NETHER)
                val nether = createDimension(netherName, World.Environment.NETHER, worldType, seed)
                if (nether == null) {
                    logger.warning("[WorldManager] createWorld: Failed to create nether dimension for ${playerWorld.name}")
                } else {
                    logger.info("[WorldManager] createWorld: Successfully created nether '$netherName'")
                }

                // Create end
                logger.info("[WorldManager] createWorld: Creating end dimension for '${playerWorld.name}'")
                val endName = getWorldName(playerWorld, World.Environment.THE_END)
                val end = createDimension(endName, World.Environment.THE_END, worldType, seed)
                if (end == null) {
                    logger.warning("[WorldManager] createWorld: Failed to create end dimension for ${playerWorld.name}")
                } else {
                    logger.info("[WorldManager] createWorld: Successfully created end '$endName'")
                }

                // Save world data
                dataManager.saveWorld(playerWorld)

                // Update player data
                val playerData = dataManager.getOrCreatePlayerData(owner.uniqueId, owner.name)
                playerData.ownedWorlds.add(playerWorld.id)
                dataManager.savePlayerData(playerData)
                logger.info("[WorldManager] createWorld: Added world '${playerWorld.name}' to player '${owner.name}' owned worlds list")

                // Apply world settings
                applyWorldSettings(playerWorld)

                logger.info("[WorldManager] createWorld: Successfully created world '${playerWorld.name}' for ${owner.name}")
                debugLogger.debug("World creation successful",
                    "worldName" to playerWorld.name,
                    "worldId" to playerWorld.id,
                    "owner" to owner.name
                )
                debugLogger.debugMethodExit("createWorld", "success: ${playerWorld.id}")
                future.complete(Result.success(playerWorld))

            } catch (e: Exception) {
                logger.severe("[WorldManager] createWorld: Error creating world '${playerWorld.name}': ${e.message}")
                debugLogger.debug("World creation failed with exception",
                    "worldName" to playerWorld.name,
                    "exceptionType" to e.javaClass.simpleName,
                    "exceptionMessage" to e.message
                )
                debugLogger.debugMethodExit("createWorld", "failure: ${e.message}")
                e.printStackTrace()
                future.complete(Result.failure(e))
            }
        }

        debugLogger.debug("Returning future for async world creation")
        return future
    }

    /**
     * Delete a world and all its dimensions.
     *
     * @param world The world to delete
     * @return CompletableFuture with Result
     */
    fun deleteWorld(world: PlayerWorld): CompletableFuture<Result<Unit>> {
        debugLogger.debugMethodEntry("deleteWorld",
            "worldName" to world.name,
            "worldId" to world.id,
            "ownerName" to world.ownerName,
            "ownerUuid" to world.ownerUuid
        )
        debugLogger.debugState("worldToDelete",
            "name" to world.name,
            "id" to world.id,
            "type" to world.worldType,
            "invitedPlayers" to world.invitedPlayers.size
        )
        logger.info("[WorldManager] deleteWorld: Attempting to delete world '${world.name}' (ID: ${world.id}, Owner: ${world.ownerName})")
        val future = CompletableFuture<Result<Unit>>()

        Bukkit.getGlobalRegionScheduler().run(plugin) { _ ->
            try {
                // Teleport all players in the world to default spawn
                val defaultWorld = Bukkit.getWorlds().firstOrNull()
                if (defaultWorld == null) {
                    logger.severe("[WorldManager] deleteWorld: No default world found for teleporting players")
                    future.complete(Result.failure(RuntimeException("No default world found")))
                    return@run
                }
                logger.info("[WorldManager] deleteWorld: Preparing to teleport players from world '${world.name}' to default spawn")

                val defaultSpawn = defaultWorld.spawnLocation
                val teleportFutures = mutableListOf<CompletableFuture<Boolean>>()

                // Check all dimensions and teleport players
                var playerCount = 0
                for (env in World.Environment.entries) {
                    val bukkitWorld = getBukkitWorld(world, env)
                    if (bukkitWorld != null) {
                        for (player in bukkitWorld.players) {
                            logger.info("[WorldManager] deleteWorld: Teleporting player '${player.name}' from '${world.name}' to default spawn")
                            teleportFutures.add(player.teleportAsync(defaultSpawn))
                            playerCount++
                        }
                    }
                }
                logger.info("[WorldManager] deleteWorld: Found $playerCount player(s) to teleport from world '${world.name}'")

                // Wait for all teleports to complete
                CompletableFuture.allOf(*teleportFutures.toTypedArray()).thenRun {
                    logger.info("[WorldManager] deleteWorld: All players teleported, proceeding with world deletion")
                    Bukkit.getGlobalRegionScheduler().run(plugin) { _ ->
                        try {
                            // On Folia, Bukkit.unloadWorld() throws UnsupportedOperationException
                            // Instead of unloading, we'll mark the world for deletion and delete files
                            // The world will remain loaded until server restart, but files will be deleted
                            logger.info("[WorldManager] deleteWorld: Skipping world unload (not supported on Folia)")
                            logger.info("[WorldManager] deleteWorld: World will remain loaded until server restart")

                            // Mark worlds as pending deletion and track in memory
                            logger.info("[WorldManager] deleteWorld: Marking world '${world.name}' for deletion")
                            val worldNames = mutableListOf<String>()
                            for (env in World.Environment.entries) {
                                val worldName = getWorldName(world, env)
                                worldNames.add(worldName)
                                // Track deleted names in memory to prevent reusing cached Bukkit worlds
                                deletedWorldNames.add(worldName)
                            }
                            addPendingDeletions(worldNames)
                            logger.info("[WorldManager] deleteWorld: Added ${worldNames.size} dimension names to deletedWorldNames set")

                            // Delete world folders from disk immediately
                            // The worlds will stay loaded in memory but files will be deleted
                            logger.info("[WorldManager] deleteWorld: Deleting world folders from disk for '${world.name}'")
                            val serverFolder = plugin.server.worldContainer
                            for (env in World.Environment.entries) {
                                val worldName = getWorldName(world, env)
                                val worldFolder = File(serverFolder, worldName)
                                if (worldFolder.exists()) {
                                    deleteDirectory(worldFolder)
                                    logger.info("[WorldManager] deleteWorld: Deleted world folder: $worldName")
                                } else {
                                    logger.info("[WorldManager] deleteWorld: World folder $worldName does not exist, skipping deletion")
                                }
                            }

                            // Remove from data
                            dataManager.deleteWorld(world.id)

                            // Update player data
                            val playerData = dataManager.loadPlayerData(world.ownerUuid)
                            if (playerData != null) {
                                playerData.ownedWorlds.remove(world.id)
                                dataManager.savePlayerData(playerData)
                                logger.info("[WorldManager] deleteWorld: Removed world '${world.name}' from player '${world.ownerName}' owned worlds list")
                            } else {
                                logger.warning("[WorldManager] deleteWorld: Could not load player data for owner UUID: ${world.ownerUuid}")
                            }

                            logger.info("[WorldManager] deleteWorld: Successfully deleted world '${world.name}' (owner: ${world.ownerName})")
                            logger.info("[WorldManager] deleteWorld: Note: World remains in memory until server restart")
                            debugLogger.debug("World deletion successful", "worldName" to world.name, "worldId" to world.id)
                            debugLogger.debugMethodExit("deleteWorld", "success")
                            future.complete(Result.success(Unit))

                        } catch (e: Exception) {
                            logger.severe("[WorldManager] deleteWorld: Error deleting world '${world.name}': ${e.message}")
                            debugLogger.debug("World deletion failed with exception",
                                "worldName" to world.name,
                                "exceptionType" to e.javaClass.simpleName,
                                "exceptionMessage" to e.message
                            )
                            debugLogger.debugMethodExit("deleteWorld", "failure: ${e.message}")
                            e.printStackTrace()
                            future.complete(Result.failure(e))
                        }
                    }
                }

            } catch (e: Exception) {
                logger.severe("[WorldManager] deleteWorld: Error in initial deletion phase for world '${world.name}': ${e.message}")
                debugLogger.debug("World deletion initial phase failed",
                    "worldName" to world.name,
                    "exceptionType" to e.javaClass.simpleName,
                    "exceptionMessage" to e.message
                )
                debugLogger.debugMethodExit("deleteWorld", "failure: ${e.message}")
                e.printStackTrace()
                future.complete(Result.failure(e))
            }
        }

        debugLogger.debug("Returning future for async world deletion")
        return future
    }

    /**
     * Load a world from disk.
     *
     * @param world The world to load
     * @return CompletableFuture with success status
     */
    fun loadWorld(world: PlayerWorld): CompletableFuture<Boolean> {
        debugLogger.debugMethodEntry("loadWorld",
            "worldName" to world.name,
            "worldId" to world.id,
            "ownerName" to world.ownerName,
            "isEnabled" to world.isEnabled
        )
        logger.info("[WorldManager] loadWorld: Loading world '${world.name}' (ID: ${world.id}, Owner: ${world.ownerName})")
        val future = CompletableFuture<Boolean>()

        if (!world.isEnabled) {
            logger.info("[WorldManager] loadWorld: World '${world.name}' is disabled, skipping load")
            debugLogger.debug("World is disabled, skipping load", "worldName" to world.name)
            debugLogger.debugMethodExit("loadWorld", false)
            future.complete(false)
            return future
        }

        // Check if overworld folder exists BEFORE scheduling async work (to avoid deadlock)
        val overworldName = getWorldName(world, World.Environment.NORMAL)
        val overworldFolder = File(plugin.server.worldContainer, overworldName)
        debugLogger.debug("Checking world folder existence",
            "overworldName" to overworldName,
            "folderPath" to overworldFolder.absolutePath,
            "exists" to overworldFolder.exists()
        )
        if (!overworldFolder.exists()) {
            logger.warning("[WorldManager] loadWorld: World folder '$overworldName' does not exist, skipping world '${world.name}'")
            debugLogger.debug("World folder missing, marking for cleanup", "worldName" to world.name)
            // Mark world as having missing files - will be cleaned up after initialization
            markWorldForCleanup(world)
            debugLogger.debugMethodExit("loadWorld", false)
            future.complete(false)
            return future
        }

        Bukkit.getGlobalRegionScheduler().run(plugin) { _ ->
            try {
                var success = true

                // Load overworld
                logger.info("[WorldManager] loadWorld: Loading overworld dimension '$overworldName'")

                if (Bukkit.getWorld(overworldName) == null) {
                    val overworld = createOrLoadDimension(overworldName, World.Environment.NORMAL, world.worldType, world.seed)
                    if (overworld == null) {
                        logger.warning("[WorldManager] loadWorld: Failed to load overworld: $overworldName")
                        success = false
                    } else {
                        logger.info("[WorldManager] loadWorld: Successfully loaded overworld '$overworldName'")
                    }
                } else {
                    logger.info("[WorldManager] loadWorld: Overworld '$overworldName' already loaded")
                }

                // Load nether (only if overworld loaded successfully)
                val netherName = getWorldName(world, World.Environment.NETHER)
                val netherFolder = File(plugin.server.worldContainer, netherName)
                if (netherFolder.exists()) {
                    logger.info("[WorldManager] loadWorld: Loading nether dimension '$netherName'")
                    if (Bukkit.getWorld(netherName) == null) {
                        val nether = createOrLoadDimension(netherName, World.Environment.NETHER, world.worldType, world.seed)
                        if (nether == null) {
                            logger.warning("[WorldManager] loadWorld: Failed to load nether: $netherName")
                        } else {
                            logger.info("[WorldManager] loadWorld: Successfully loaded nether '$netherName'")
                        }
                    } else {
                        logger.info("[WorldManager] loadWorld: Nether '$netherName' already loaded")
                    }
                } else {
                    logger.info("[WorldManager] loadWorld: Nether folder '$netherName' does not exist, skipping")
                }

                // Load end (only if overworld loaded successfully)
                val endName = getWorldName(world, World.Environment.THE_END)
                val endFolder = File(plugin.server.worldContainer, endName)
                if (endFolder.exists()) {
                    logger.info("[WorldManager] loadWorld: Loading end dimension '$endName'")
                    if (Bukkit.getWorld(endName) == null) {
                        val end = createOrLoadDimension(endName, World.Environment.THE_END, world.worldType, world.seed)
                        if (end == null) {
                            logger.warning("[WorldManager] loadWorld: Failed to load end: $endName")
                        } else {
                            logger.info("[WorldManager] loadWorld: Successfully loaded end '$endName'")
                        }
                    } else {
                        logger.info("[WorldManager] loadWorld: End '$endName' already loaded")
                    }
                } else {
                    logger.info("[WorldManager] loadWorld: End folder '$endName' does not exist, skipping")
                }

                // Apply world settings
                if (success) {
                    logger.info("[WorldManager] loadWorld: Applying world settings for '${world.name}'")
                    applyWorldSettings(world)
                }

                logger.info("[WorldManager] loadWorld: Load complete for world '${world.name}' - Success: $success")
                debugLogger.debug("World load complete", "worldName" to world.name, "success" to success)
                debugLogger.debugMethodExit("loadWorld", success)
                future.complete(success)

            } catch (e: Exception) {
                logger.severe("[WorldManager] loadWorld: Error loading world ${world.name}: ${e.message}")
                debugLogger.debug("World load failed with exception",
                    "worldName" to world.name,
                    "exceptionType" to e.javaClass.simpleName,
                    "exceptionMessage" to e.message
                )
                debugLogger.debugMethodExit("loadWorld", false)
                e.printStackTrace()
                future.complete(false)
            }
        }

        debugLogger.debug("Returning future for async world load")
        return future
    }

    /**
     * Load a world synchronously (for use during initialization on the server thread).
     * This avoids the deadlock that occurs when using GlobalRegionScheduler during onEnable.
     *
     * @param world The world to load
     * @return True if loaded successfully
     */
    private fun loadWorldSync(world: PlayerWorld): Boolean {
        debugLogger.debugMethodEntry("loadWorldSync",
            "worldName" to world.name,
            "worldId" to world.id,
            "ownerName" to world.ownerName,
            "isEnabled" to world.isEnabled,
            "worldType" to world.worldType
        )
        logger.info("[WorldManager] loadWorldSync: Loading world '${world.name}' (ID: ${world.id}, Owner: ${world.ownerName})")

        if (!world.isEnabled) {
            logger.info("[WorldManager] loadWorldSync: World '${world.name}' is disabled, skipping load")
            debugLogger.debug("World is disabled, skipping sync load", "worldName" to world.name)
            debugLogger.debugMethodExit("loadWorldSync", false)
            return false
        }

        // Check if overworld folder exists
        val overworldName = getWorldName(world, World.Environment.NORMAL)
        val overworldFolder = File(plugin.server.worldContainer, overworldName)
        debugLogger.debug("Checking overworld folder for sync load",
            "overworldName" to overworldName,
            "folderPath" to overworldFolder.absolutePath,
            "exists" to overworldFolder.exists()
        )
        if (!overworldFolder.exists()) {
            logger.warning("[WorldManager] loadWorldSync: World folder '$overworldName' does not exist, skipping world '${world.name}'")
            debugLogger.debug("Overworld folder missing, marking for cleanup", "worldName" to world.name)
            markWorldForCleanup(world)
            debugLogger.debugMethodExit("loadWorldSync", false)
            return false
        }

        try {
            var success = true

            // Load overworld
            logger.info("[WorldManager] loadWorldSync: Loading overworld dimension '$overworldName'")
            if (Bukkit.getWorld(overworldName) == null) {
                val overworld = createOrLoadDimension(overworldName, World.Environment.NORMAL, world.worldType, world.seed)
                if (overworld == null) {
                    logger.warning("[WorldManager] loadWorldSync: Failed to load overworld: $overworldName")
                    success = false
                } else {
                    logger.info("[WorldManager] loadWorldSync: Successfully loaded overworld '$overworldName'")
                }
            } else {
                logger.info("[WorldManager] loadWorldSync: Overworld '$overworldName' already loaded")
            }

            // Load nether
            val netherName = getWorldName(world, World.Environment.NETHER)
            val netherFolder = File(plugin.server.worldContainer, netherName)
            if (netherFolder.exists()) {
                logger.info("[WorldManager] loadWorldSync: Loading nether dimension '$netherName'")
                if (Bukkit.getWorld(netherName) == null) {
                    val nether = createOrLoadDimension(netherName, World.Environment.NETHER, world.worldType, world.seed)
                    if (nether == null) {
                        logger.warning("[WorldManager] loadWorldSync: Failed to load nether: $netherName")
                    } else {
                        logger.info("[WorldManager] loadWorldSync: Successfully loaded nether '$netherName'")
                    }
                } else {
                    logger.info("[WorldManager] loadWorldSync: Nether '$netherName' already loaded")
                }
            }

            // Load end
            val endName = getWorldName(world, World.Environment.THE_END)
            val endFolder = File(plugin.server.worldContainer, endName)
            if (endFolder.exists()) {
                logger.info("[WorldManager] loadWorldSync: Loading end dimension '$endName'")
                if (Bukkit.getWorld(endName) == null) {
                    val end = createOrLoadDimension(endName, World.Environment.THE_END, world.worldType, world.seed)
                    if (end == null) {
                        logger.warning("[WorldManager] loadWorldSync: Failed to load end: $endName")
                    } else {
                        logger.info("[WorldManager] loadWorldSync: Successfully loaded end '$endName'")
                    }
                } else {
                    logger.info("[WorldManager] loadWorldSync: End '$endName' already loaded")
                }
            }

            // Apply world settings
            if (success) {
                logger.info("[WorldManager] loadWorldSync: Applying world settings for '${world.name}'")
                applyWorldSettings(world)
            }

            logger.info("[WorldManager] loadWorldSync: Load complete for world '${world.name}' - Success: $success")
            debugLogger.debug("Sync load complete", "worldName" to world.name, "success" to success)
            debugLogger.debugMethodExit("loadWorldSync", success)
            return success

        } catch (e: Exception) {
            logger.severe("[WorldManager] loadWorldSync: Error loading world ${world.name}: ${e.message}")
            debugLogger.debug("Sync load failed with exception",
                "worldName" to world.name,
                "exceptionType" to e.javaClass.simpleName,
                "exceptionMessage" to e.message
            )
            debugLogger.debugMethodExit("loadWorldSync", false)
            e.printStackTrace()
            return false
        }
    }

    /**
     * Unload a world (save and remove from memory).
     *
     * @param world The world to unload
     * @return CompletableFuture with success status
     */
    fun unloadWorld(world: PlayerWorld): CompletableFuture<Boolean> {
        debugLogger.debugMethodEntry("unloadWorld",
            "worldName" to world.name,
            "worldId" to world.id,
            "ownerName" to world.ownerName
        )
        val future = CompletableFuture<Boolean>()

        debugLogger.debug("Scheduling world unload on GlobalRegionScheduler")
        Bukkit.getGlobalRegionScheduler().run(plugin) { _ ->
            try {
                var success = true
                debugLogger.debug("GlobalRegionScheduler task started for world unload", "worldName" to world.name)

                // Unload all dimensions
                for (env in World.Environment.entries) {
                    val worldName = getWorldName(world, env)
                    val bukkitWorld = Bukkit.getWorld(worldName)
                    debugLogger.debug("Checking dimension for unload",
                        "environment" to env,
                        "worldName" to worldName,
                        "isLoaded" to (bukkitWorld != null)
                    )
                    if (bukkitWorld != null) {
                        // Kick players before unloading
                        val defaultWorld = Bukkit.getWorlds().firstOrNull()
                        val playerCount = bukkitWorld.players.size
                        debugLogger.debug("Players in dimension",
                            "worldName" to worldName,
                            "playerCount" to playerCount
                        )
                        if (defaultWorld != null && bukkitWorld.players.isNotEmpty()) {
                            debugLogger.debug("Teleporting players out of dimension",
                                "playerNames" to bukkitWorld.players.map { it.name },
                                "destination" to defaultWorld.name
                            )
                            val teleportFutures = bukkitWorld.players.map { player ->
                                player.teleportAsync(defaultWorld.spawnLocation)
                            }
                            CompletableFuture.allOf(*teleportFutures.toTypedArray()).join()
                            debugLogger.debug("All players teleported from dimension", "worldName" to worldName)
                        }

                        // Unload with save
                        debugLogger.debug("Attempting to unload dimension", "worldName" to worldName, "withSave" to true)
                        if (!Bukkit.unloadWorld(bukkitWorld, true)) {
                            logger.warning("Failed to unload world: $worldName")
                            debugLogger.debug("Dimension unload failed", "worldName" to worldName)
                            success = false
                        } else {
                            debugLogger.debug("Dimension unloaded successfully", "worldName" to worldName)
                        }
                    }
                }

                debugLogger.debug("World unload complete", "worldName" to world.name, "success" to success)
                debugLogger.debugMethodExit("unloadWorld", success)
                future.complete(success)

            } catch (e: Exception) {
                logger.severe("Error unloading world ${world.name}: ${e.message}")
                debugLogger.debug("World unload failed with exception",
                    "worldName" to world.name,
                    "exceptionType" to e.javaClass.simpleName,
                    "exceptionMessage" to e.message
                )
                debugLogger.debugMethodExit("unloadWorld", false)
                e.printStackTrace()
                future.complete(false)
            }
        }

        return future
    }

    /**
     * Teleport player to a world's spawn.
     * If the world is unloaded, it will be loaded first and the player will see a loading message.
     *
     * @param player The player to teleport
     * @param world The world to teleport to
     * @return CompletableFuture with success status
     */
    fun teleportToWorld(player: Player, world: PlayerWorld): CompletableFuture<Boolean> {
        debugLogger.debugMethodEntry("teleportToWorld",
            "playerName" to player.name,
            "playerUuid" to player.uniqueId,
            "worldName" to world.name,
            "worldId" to world.id
        )
        logger.info("[WorldManager] teleportToWorld: Teleporting player '${player.name}' to world '${world.name}'")

        // Check if world is unloaded and needs to be loaded first
        val isUnloaded = worldUnloadManager?.isWorldUnloaded(world.id) == true ||
                         getBukkitWorld(world) == null
        debugLogger.debug("World load status check",
            "worldName" to world.name,
            "isUnloaded" to isUnloaded
        )

        if (isUnloaded) {
            // World needs to be loaded first
            logger.info("[WorldManager] teleportToWorld: World '${world.name}' is unloaded, loading first...")
            player.sendMessage(
                Component.text("Loading world...", NamedTextColor.YELLOW)
            )

            val future = CompletableFuture<Boolean>()

            // Load the world first
            loadWorld(world).thenAccept { loadSuccess ->
                if (loadSuccess) {
                    logger.info("[WorldManager] teleportToWorld: World '${world.name}' loaded successfully, proceeding with teleport")
                    // Mark as loaded in unload manager
                    worldUnloadManager?.markWorldLoaded(world.id)
                    // Now teleport
                    teleportToDimension(player, world, World.Environment.NORMAL).thenAccept { teleportSuccess ->
                        future.complete(teleportSuccess)
                    }
                } else {
                    logger.warning("[WorldManager] teleportToWorld: Failed to load world '${world.name}'")
                    player.scheduler.run(plugin, { _ ->
                        player.sendMessage(
                            Component.text("Failed to load world", NamedTextColor.RED)
                        )
                    }, null)
                    future.complete(false)
                }
            }

            debugLogger.debugMethodExit("teleportToWorld", "loading world first")
            return future
        }

        val result = teleportToDimension(player, world, World.Environment.NORMAL)
        debugLogger.debug("Delegating to teleportToDimension", "environment" to World.Environment.NORMAL)
        return result
    }

    /**
     * Teleport player to the first vanilla (non-plugin) world.
     *
     * @param player The player to teleport
     * @return CompletableFuture with success status
     */
    fun teleportToVanillaWorld(player: Player): CompletableFuture<Boolean> {
        debugLogger.debugMethodEntry("teleportToVanillaWorld",
            "playerName" to player.name,
            "playerUuid" to player.uniqueId,
            "currentWorld" to player.world.name
        )
        logger.info("[WorldManager] teleportToVanillaWorld: Teleporting player '${player.name}' to vanilla world")
        val future = CompletableFuture<Boolean>()

        // Get the first vanilla world
        val allWorlds = Bukkit.getWorlds()
        debugLogger.debug("Finding vanilla world", "totalWorlds" to allWorlds.size)
        val vanillaWorld = allWorlds.firstOrNull { !isPluginWorld(it) }
        if (vanillaWorld == null) {
            logger.warning("[WorldManager] teleportToVanillaWorld: No vanilla world found")
            debugLogger.debug("No vanilla world found")
            debugLogger.debugMethodExit("teleportToVanillaWorld", false)
            future.complete(false)
            return future
        }
        debugLogger.debug("Found vanilla world", "vanillaWorldName" to vanillaWorld.name)

        val vanillaWorldName = vanillaWorld.name
        val currentWorldName = player.world.name

        // Same-world check: if player is already in the vanilla world, do nothing
        if (currentWorldName == vanillaWorldName) {
            logger.info("[WorldManager] teleportToVanillaWorld: Player '${player.name}' is already in vanilla world '$vanillaWorldName', skipping teleport")
            debugLogger.debug("Player already in vanilla world, skipping teleport",
                "playerName" to player.name,
                "worldName" to vanillaWorldName
            )
            debugLogger.debugMethodExit("teleportToVanillaWorld", true)
            future.complete(true)
            return future
        }

        // STEP 1: Save FULL player state for current world BEFORE teleporting
        logger.info("[WorldManager] teleportToVanillaWorld: Saving full state for '${player.name}' in '$currentWorldName'")
        debugLogger.debug("Saving player state before teleport",
            "playerName" to player.name,
            "currentWorld" to currentWorldName
        )
        worldStateManager.savePlayerState(player, currentWorldName)

        // STEP 2: Determine teleport location
        val savedState = worldStateManager.getSavedLocation(player.uniqueId, vanillaWorldName)
        debugLogger.debug("Checking for saved location",
            "targetWorld" to vanillaWorldName,
            "hasSavedState" to (savedState != null)
        )
        val location = if (savedState != null) {
            logger.info("[WorldManager] teleportToVanillaWorld: Using saved location for '${player.name}' in '$vanillaWorldName'")
            debugLogger.debug("Using saved location",
                "x" to savedState.x, "y" to savedState.y, "z" to savedState.z,
                "yaw" to savedState.yaw, "pitch" to savedState.pitch
            )
            Location(vanillaWorld, savedState.x, savedState.y, savedState.z, savedState.yaw, savedState.pitch)
        } else {
            logger.info("[WorldManager] teleportToVanillaWorld: Using world spawn for '$vanillaWorldName' (first visit)")
            val spawn = vanillaWorld.spawnLocation
            debugLogger.debug("Using world spawn (first visit)",
                "x" to spawn.x, "y" to spawn.y, "z" to spawn.z
            )
            spawn
        }

        // Check if this is first time in vanilla world
        val isFirstVisit = !worldStateManager.hasStateForWorld(player.uniqueId, vanillaWorldName)
        debugLogger.debug("First visit check", "isFirstVisit" to isFirstVisit)

        // STEP 3: Teleport async
        debugLogger.debug("Initiating async teleport",
            "destination" to vanillaWorldName,
            "x" to location.x, "y" to location.y, "z" to location.z
        )
        player.teleportAsync(location).thenAccept { success ->
            if (success) {
                logger.info("[WorldManager] teleportToVanillaWorld: Teleport successful for '${player.name}' to '$vanillaWorldName'")
                debugLogger.debug("Async teleport successful", "playerName" to player.name, "destination" to vanillaWorldName)

                // STEP 4: Restore or clear state on entity scheduler (after teleport completes)
                debugLogger.debug("Scheduling state restoration on entity scheduler")
                player.scheduler.run(plugin, { _ ->
                    if (isFirstVisit) {
                        // First time in vanilla world - clear to fresh state
                        logger.info("[WorldManager] teleportToVanillaWorld: First visit to '$vanillaWorldName', clearing player state")
                        debugLogger.debug("First visit - clearing player state", "worldName" to vanillaWorldName)
                        worldStateManager.clearPlayerState(player)
                        // Save the cleared state so future visits restore it (not treated as first visit again)
                        worldStateManager.savePlayerState(player, vanillaWorldName)
                        debugLogger.debug("Saved cleared state for future visits")
                    } else {
                        // Returning to vanilla world - restore saved state
                        logger.info("[WorldManager] teleportToVanillaWorld: Restoring state for '${player.name}' in '$vanillaWorldName'")
                        debugLogger.debug("Returning visit - restoring saved state", "worldName" to vanillaWorldName)
                        worldStateManager.restorePlayerState(player, vanillaWorldName)
                    }
                    debugLogger.debugMethodExit("teleportToVanillaWorld", true)
                }, null)
            } else {
                logger.warning("[WorldManager] teleportToVanillaWorld: Failed to teleport '${player.name}' to '$vanillaWorldName'")
                debugLogger.debug("Async teleport failed", "playerName" to player.name)
                debugLogger.debugMethodExit("teleportToVanillaWorld", false)
            }
            future.complete(success)
        }

        return future
    }

    /**
     * Teleport player to specific dimension of a world.
     *
     * @param player The player to teleport
     * @param world The world to teleport to
     * @param environment The dimension to teleport to
     * @return CompletableFuture with success status
     */
    fun teleportToDimension(
        player: Player,
        world: PlayerWorld,
        environment: World.Environment
    ): CompletableFuture<Boolean> {
        debugLogger.debugMethodEntry("teleportToDimension",
            "playerName" to player.name,
            "playerUuid" to player.uniqueId,
            "worldName" to world.name,
            "worldId" to world.id,
            "environment" to environment
        )
        logger.info("[WorldManager] teleportToDimension: Teleporting player '${player.name}' to world '${world.name}' dimension $environment")

        // Get the target Bukkit world name
        val targetWorldName = getWorldName(world, environment)
        val currentWorldName = player.world.name
        debugLogger.debug("World name resolution",
            "targetWorldName" to targetWorldName,
            "currentWorldName" to currentWorldName
        )

        // Same-world check: if player is already in the target world, do nothing
        if (currentWorldName == targetWorldName) {
            logger.info("[WorldManager] teleportToDimension: Player '${player.name}' is already in world '$targetWorldName', skipping teleport")
            debugLogger.debug("Player already in target world, skipping", "worldName" to targetWorldName)
            debugLogger.debugMethodExit("teleportToDimension", true)
            return CompletableFuture.completedFuture(true)
        }

        val future = CompletableFuture<Boolean>()

        val bukkitWorld = getBukkitWorld(world, environment)
        debugLogger.debug("Bukkit world lookup", "found" to (bukkitWorld != null), "targetWorldName" to targetWorldName)
        if (bukkitWorld == null) {
            logger.warning("[WorldManager] teleportToDimension: Bukkit world not loaded for '${world.name}' dimension $environment")
            debugLogger.debug("Bukkit world not found/loaded", "worldName" to world.name, "environment" to environment)
            debugLogger.debugMethodExit("teleportToDimension", false)
            future.complete(false)
            return future
        }

        // STEP 1: Save FULL player state for current world BEFORE teleporting
        logger.info("[WorldManager] teleportToDimension: Saving full state for '${player.name}' in '$currentWorldName'")
        debugLogger.debug("Saving player state before dimension teleport", "currentWorld" to currentWorldName)
        worldStateManager.savePlayerState(player, currentWorldName)

        // STEP 2: Determine teleport location
        val savedState = worldStateManager.getSavedLocation(player.uniqueId, targetWorldName)
        debugLogger.debug("Checking for saved state in target world",
            "targetWorld" to targetWorldName,
            "hasSavedState" to (savedState != null),
            "hasCustomSpawn" to (world.spawnLocation != null)
        )
        val location = when {
            // Use saved location if player has been to this world before
            savedState != null -> {
                logger.info("[WorldManager] teleportToDimension: Using saved location for '${player.name}' in '$targetWorldName'")
                debugLogger.debug("Using saved location",
                    "x" to savedState.x, "y" to savedState.y, "z" to savedState.z,
                    "yaw" to savedState.yaw, "pitch" to savedState.pitch
                )
                Location(bukkitWorld, savedState.x, savedState.y, savedState.z, savedState.yaw, savedState.pitch)
            }
            // Use custom spawn location for overworld if first visit
            environment == World.Environment.NORMAL && world.spawnLocation != null -> {
                logger.info("[WorldManager] teleportToDimension: Using custom spawn for '${world.name}' (first visit)")
                val customSpawn = world.spawnLocation!!
                debugLogger.debug("Using custom spawn location (first visit)",
                    "x" to customSpawn.x, "y" to customSpawn.y, "z" to customSpawn.z
                )
                customSpawn.toBukkitLocation(bukkitWorld)
            }
            // Fall back to world spawn location
            else -> {
                logger.info("[WorldManager] teleportToDimension: Using world spawn for '$targetWorldName' (first visit)")
                val spawn = bukkitWorld.spawnLocation
                debugLogger.debug("Using world spawn location (first visit/default)",
                    "x" to spawn.x, "y" to spawn.y, "z" to spawn.z
                )
                spawn
            }
        }

        // Check if this is first time in target world
        val isFirstVisit = !worldStateManager.hasStateForWorld(player.uniqueId, targetWorldName)
        debugLogger.debug("First visit check for dimension teleport", "isFirstVisit" to isFirstVisit)

        // STEP 3: Teleport async
        debugLogger.debug("Initiating async dimension teleport",
            "destination" to targetWorldName,
            "x" to location.x, "y" to location.y, "z" to location.z
        )
        player.teleportAsync(location).thenAccept { success ->
            if (success) {
                logger.info("[WorldManager] teleportToDimension: Teleport successful for '${player.name}' to '$targetWorldName'")
                debugLogger.debug("Async dimension teleport successful",
                    "playerName" to player.name,
                    "destination" to targetWorldName
                )

                // STEP 4: Restore or clear state on entity scheduler (after teleport completes)
                debugLogger.debug("Scheduling state restoration on entity scheduler after dimension teleport")
                player.scheduler.run(plugin, { _ ->
                    if (isFirstVisit) {
                        // First time in this world - clear to fresh state
                        logger.info("[WorldManager] teleportToDimension: First visit to '$targetWorldName', clearing player state")
                        debugLogger.debug("First visit to dimension - clearing state", "worldName" to targetWorldName)
                        worldStateManager.clearPlayerState(player)
                        // Save the cleared state so future visits restore it (not treated as first visit again)
                        worldStateManager.savePlayerState(player, targetWorldName)
                        debugLogger.debug("Saved cleared state for future dimension visits")
                    } else {
                        // Returning to this world - restore saved state
                        logger.info("[WorldManager] teleportToDimension: Restoring state for '${player.name}' in '$targetWorldName'")
                        debugLogger.debug("Returning to dimension - restoring state", "worldName" to targetWorldName)
                        worldStateManager.restorePlayerState(player, targetWorldName)
                    }

                    // Set gamemode from world settings
                    debugLogger.debug("Setting gamemode from world settings", "gameMode" to world.defaultGameMode)
                    player.gameMode = world.defaultGameMode
                    debugLogger.debugMethodExit("teleportToDimension", true)
                }, null)
            } else {
                logger.warning("[WorldManager] teleportToDimension: Failed to teleport '${player.name}' to '$targetWorldName'")
                debugLogger.debug("Async dimension teleport failed", "playerName" to player.name)
                debugLogger.debugMethodExit("teleportToDimension", false)
            }
            future.complete(success)
        }

        return future
    }

    /**
     * Get the Bukkit World for a PlayerWorld's dimension.
     *
     * @param world The PlayerWorld
     * @param environment The dimension
     * @return The Bukkit World, or null if not loaded
     */
    fun getBukkitWorld(world: PlayerWorld, environment: World.Environment = World.Environment.NORMAL): World? {
        debugLogger.debugMethodEntry("getBukkitWorld",
            "worldName" to world.name,
            "worldId" to world.id,
            "environment" to environment
        )
        val worldName = getWorldName(world, environment)
        val bukkitWorld = Bukkit.getWorld(worldName)
        debugLogger.debug("Bukkit world lookup result",
            "searchName" to worldName,
            "found" to (bukkitWorld != null)
        )
        debugLogger.debugMethodExit("getBukkitWorld", bukkitWorld?.name)
        return bukkitWorld
    }

    /**
     * Get the internal world name for a dimension.
     *
     * @param world The PlayerWorld
     * @param environment The dimension
     * @return The world name string
     */
    fun getWorldName(world: PlayerWorld, environment: World.Environment = World.Environment.NORMAL): String {
        val baseName = "${world.ownerName}_${world.name}".lowercase().replace(" ", "_")
        return when (environment) {
            World.Environment.NORMAL -> baseName
            World.Environment.NETHER -> baseName + NETHER_SUFFIX
            World.Environment.THE_END -> baseName + END_SUFFIX
            else -> baseName
        }
    }

    /**
     * Check if player can create more worlds.
     *
     * @param player The player to check
     * @return True if player can create more worlds
     */
    fun canCreateWorld(player: Player): Boolean {
        debugLogger.debugMethodEntry("canCreateWorld",
            "playerName" to player.name,
            "playerUuid" to player.uniqueId
        )
        val currentCount = getWorldCount(player.uniqueId)
        val limit = dataManager.loadPlayerData(player.uniqueId)?.worldLimit ?: defaultWorldLimit
        val canCreate = currentCount < limit
        debugLogger.debug("World creation permission check",
            "currentCount" to currentCount,
            "limit" to limit,
            "canCreate" to canCreate
        )
        debugLogger.debugMethodExit("canCreateWorld", canCreate)
        return canCreate
    }

    /**
     * Get world count for a player.
     *
     * @param playerUuid The player's UUID
     * @return Number of worlds owned by player
     */
    fun getWorldCount(playerUuid: UUID): Int {
        debugLogger.debugMethodEntry("getWorldCount", "playerUuid" to playerUuid)
        val count = dataManager.getWorldsByOwner(playerUuid).size
        debugLogger.debugMethodExit("getWorldCount", count)
        return count
    }

    /**
     * Apply world settings (time lock, weather lock, gamemode).
     *
     * @param world The world to apply settings to
     */
    fun applyWorldSettings(world: PlayerWorld) {
        debugLogger.debugMethodEntry("applyWorldSettings",
            "worldName" to world.name,
            "worldId" to world.id,
            "timeLock" to world.timeLock,
            "weatherLock" to world.weatherLock,
            "defaultGameMode" to world.defaultGameMode
        )
        logger.info("[WorldManager] applyWorldSettings: Applying settings to world '${world.name}' (TimeLock: ${world.timeLock}, WeatherLock: ${world.weatherLock})")
        val bukkitWorld = getBukkitWorld(world) ?: run {
            logger.warning("[WorldManager] applyWorldSettings: Could not get Bukkit world for '${world.name}'")
            debugLogger.debug("Cannot apply settings - Bukkit world not found", "worldName" to world.name)
            debugLogger.debugMethodExit("applyWorldSettings", "failed - no bukkit world")
            return
        }
        debugLogger.debug("Found Bukkit world for settings", "bukkitWorldName" to bukkitWorld.name)

        // Apply time lock
        when (world.timeLock) {
            TimeLock.DAY -> {
                logger.info("[WorldManager] applyWorldSettings: Setting time to DAY (locked) for '${world.name}'")
                bukkitWorld.setGameRule(GameRules.ADVANCE_TIME, false)
                bukkitWorld.time = 6000 // Noon
            }
            TimeLock.NIGHT -> {
                logger.info("[WorldManager] applyWorldSettings: Setting time to NIGHT (locked) for '${world.name}'")
                bukkitWorld.setGameRule(GameRules.ADVANCE_TIME, false)
                bukkitWorld.time = 18000 // Midnight
            }
            TimeLock.CYCLE -> {
                logger.info("[WorldManager] applyWorldSettings: Enabling time CYCLE for '${world.name}'")
                bukkitWorld.setGameRule(GameRules.ADVANCE_TIME, true)
            }
        }

        // Apply weather lock
        when (world.weatherLock) {
            WeatherLock.CLEAR -> {
                logger.info("[WorldManager] applyWorldSettings: Setting weather to CLEAR (locked) for '${world.name}'")
                bukkitWorld.setGameRule(GameRules.ADVANCE_WEATHER, false)
                bukkitWorld.setStorm(false)
                bukkitWorld.isThundering = false
            }
            WeatherLock.RAIN -> {
                logger.info("[WorldManager] applyWorldSettings: Setting weather to RAIN (locked) for '${world.name}'")
                bukkitWorld.setGameRule(GameRules.ADVANCE_WEATHER, false)
                bukkitWorld.setStorm(true)
                bukkitWorld.isThundering = false
            }
            WeatherLock.CYCLE -> {
                logger.info("[WorldManager] applyWorldSettings: Enabling weather CYCLE for '${world.name}'")
                bukkitWorld.setGameRule(GameRules.ADVANCE_WEATHER, true)
            }
        }
        logger.info("[WorldManager] applyWorldSettings: Successfully applied settings to world '${world.name}'")
        debugLogger.debugMethodExit("applyWorldSettings", "success")
    }

    /**
     * Update spawn location for a world.
     *
     * @param world The world to update
     * @param location The new spawn location
     */
    fun setSpawnLocation(world: PlayerWorld, location: Location) {
        debugLogger.debugMethodEntry("setSpawnLocation",
            "worldName" to world.name,
            "worldId" to world.id,
            "x" to location.x,
            "y" to location.y,
            "z" to location.z,
            "yaw" to location.yaw,
            "pitch" to location.pitch
        )
        logger.info("[WorldManager] setSpawnLocation: Setting spawn location for world '${world.name}' to ${location.x}, ${location.y}, ${location.z}")
        world.spawnLocation = location.toSimpleLocation()
        debugLogger.debug("Saving world with new spawn location")
        dataManager.saveWorld(world)

        // Update Bukkit world spawn
        val bukkitWorld = getBukkitWorld(world)
        debugLogger.debug("Updating Bukkit world spawn", "bukkitWorldFound" to (bukkitWorld != null))
        bukkitWorld?.spawnLocation = location
        logger.info("[WorldManager] setSpawnLocation: Successfully updated spawn location for '${world.name}'")
        debugLogger.debugMethodExit("setSpawnLocation", "success")
    }

    /**
     * Find PlayerWorld from Bukkit World name.
     *
     * @param bukkitWorld The Bukkit world
     * @return The PlayerWorld, or null if not found
     */
    fun getPlayerWorldFromBukkitWorld(bukkitWorld: World): PlayerWorld? {
        debugLogger.debugMethodEntry("getPlayerWorldFromBukkitWorld", "bukkitWorldName" to bukkitWorld.name)
        val worldName = bukkitWorld.name

        // Try to extract owner name and world name from the Bukkit world name
        // Format: ownername_worldname or ownername_worldname_nether or ownername_worldname_the_end
        val baseName = worldName
            .removeSuffix(NETHER_SUFFIX)
            .removeSuffix(END_SUFFIX)
        debugLogger.debug("Extracted base name", "original" to worldName, "baseName" to baseName)

        // Find matching world
        val allWorlds = dataManager.getAllWorlds()
        debugLogger.debug("Searching for matching PlayerWorld", "totalWorlds" to allWorlds.size)
        val result = allWorlds.firstOrNull { world ->
            val expectedBaseName = "${world.ownerName}_${world.name}".lowercase().replace(" ", "_")
            baseName == expectedBaseName
        }
        debugLogger.debug("PlayerWorld lookup result",
            "found" to (result != null),
            "resultWorldName" to result?.name,
            "resultWorldId" to result?.id
        )
        debugLogger.debugMethodExit("getPlayerWorldFromBukkitWorld", result?.name)
        return result
    }

    /**
     * Check if a Bukkit world belongs to this plugin.
     *
     * @param world The world to check
     * @return True if this is a plugin-managed world
     */
    fun isPluginWorld(world: World): Boolean {
        debugLogger.debugMethodEntry("isPluginWorld", "worldName" to world.name)
        val isPlugin = getPlayerWorldFromBukkitWorld(world) != null
        debugLogger.debugMethodExit("isPluginWorld", isPlugin)
        return isPlugin
    }

    /**
     * Get all loaded plugin worlds.
     *
     * @return List of loaded PlayerWorlds
     */
    fun getLoadedWorlds(): List<PlayerWorld> {
        debugLogger.debugMethodEntry("getLoadedWorlds")
        val allWorlds = dataManager.getAllWorlds()
        val loadedWorlds = allWorlds.filter { world ->
            getBukkitWorld(world) != null
        }
        debugLogger.debug("Loaded worlds count",
            "totalWorlds" to allWorlds.size,
            "loadedWorlds" to loadedWorlds.size
        )
        debugLogger.debugMethodExit("getLoadedWorlds", loadedWorlds.size)
        return loadedWorlds
    }

    // ========================
    // Private Helper Methods
    // ========================

    /**
     * Validate world name.
     *
     * @param name The name to validate
     * @return Error message, or null if valid
     */
    private fun validateWorldName(name: String): String? {
        debugLogger.debugMethodEntry("validateWorldName", "name" to name, "nameLength" to name.length)
        val result = when {
            name.isBlank() -> "World name cannot be empty"
            name.length > maxNameLength -> "World name is too long (max $maxNameLength characters)"
            !namePattern.matches(name) -> "World name can only contain letters, numbers, and underscores"
            else -> null
        }
        debugLogger.debug("World name validation result",
            "name" to name,
            "isValid" to (result == null),
            "error" to result
        )
        debugLogger.debugMethodExit("validateWorldName", result)
        return result
    }

    /**
     * Create a dimension world using NMS classes for Folia compatibility.
     *
     * @param worldName The world name
     * @param environment The dimension type
     * @param worldType The generation type
     * @param seed The seed
     * @return The created World, or null on failure
     */
    private fun createDimension(
        worldName: String,
        environment: World.Environment,
        worldType: WorldType,
        seed: Long?
    ): World? {
        debugLogger.debugMethodEntry("createDimension",
            "worldName" to worldName,
            "environment" to environment,
            "worldType" to worldType,
            "seed" to seed
        )
        logger.info("[WorldManager] createDimension: Creating dimension '$worldName' (Environment: $environment, Type: $worldType, Seed: ${seed ?: "random"})")

        try {
            val craftServer = Bukkit.getServer() as CraftServer
            val console = craftServer.server as DedicatedServer

            // Check if world already exists in Bukkit
            val existingWorld = craftServer.getWorld(worldName)
            if (existingWorld != null) {
                // Check if this is a stale cached world from a deleted world (Folia can't unload worlds)
                if (deletedWorldNames.contains(worldName)) {
                    logger.info("[WorldManager] createDimension: Skipping cached deleted world '$worldName', will create fresh")
                    deletedWorldNames.remove(worldName)
                    // Continue to create fresh world - don't return the stale cached world
                } else {
                    logger.warning("[WorldManager] createDimension: World '$worldName' already exists")
                    return existingWorld
                }
            }

            // Determine the actual dimension key
            val actualDimension = when (environment) {
                World.Environment.NORMAL -> LevelStem.OVERWORLD
                World.Environment.NETHER -> LevelStem.NETHER
                World.Environment.THE_END -> LevelStem.END
                World.Environment.CUSTOM -> LevelStem.OVERWORLD // Custom uses overworld settings
            }

            val worldContainer = craftServer.worldContainer
            val worldFolder = File(worldContainer, worldName)

            // Validate world folder
            if (worldFolder.exists() && !worldFolder.isDirectory) {
                logger.severe("[WorldManager] createDimension: World folder '$worldName' exists but is not a directory")
                return null
            }

            // Create level storage access
            val levelStorageAccess: LevelStorageSource.LevelStorageAccess = try {
                LevelStorageSource.createDefault(worldContainer.toPath())
                    .validateAndCreateAccess(worldName, actualDimension)
            } catch (ex: IOException) {
                logger.severe("[WorldManager] createDimension: IOException creating storage access: ${ex.message}")
                ex.printStackTrace()
                return null
            } catch (ex: ContentValidationException) {
                logger.severe("[WorldManager] createDimension: Content validation error: ${ex.message}")
                ex.printStackTrace()
                return null
            }

            // Configure generator and biome provider
            var generator: ChunkGenerator? = null
            var biomeProvider: BiomeProvider? = null

            if (worldType == WorldType.VOID) {
                generator = VoidGenerator()
            }

            // Load or create world data
            val dataTag: Dynamic<*>?
            if (levelStorageAccess.hasWorldData()) {
                try {
                    dataTag = levelStorageAccess.dataTag
                    val summary = levelStorageAccess.getSummary(dataTag)

                    if (summary.requiresManualConversion()) {
                        logger.severe("[WorldManager] createDimension: World requires manual conversion")
                        levelStorageAccess.close()
                        return null
                    }

                    if (!summary.isCompatible) {
                        logger.severe("[WorldManager] createDimension: World was created by incompatible version")
                        levelStorageAccess.close()
                        return null
                    }
                } catch (ex: Exception) {
                    when (ex) {
                        is NbtException, is ReportedNbtException, is IOException -> {
                            logger.warning("[WorldManager] createDimension: Failed to load world data, attempting fallback")
                            try {
                                levelStorageAccess.dataTagFallback
                                levelStorageAccess.restoreLevelDataFromOld()
                            } catch (fallbackEx: Exception) {
                                logger.severe("[WorldManager] createDimension: Fallback failed: ${fallbackEx.message}")
                                levelStorageAccess.close()
                                return null
                            }
                        }
                        else -> throw ex
                    }
                }
            }

            // Get world loader context and registry access
            val context: WorldLoader.DataLoadContext = console.worldLoaderContext
            var registryAccess: RegistryAccess.Frozen = context.datapackDimensions()
            var contextLevelStemRegistry = registryAccess.lookupOrThrow(Registries.LEVEL_STEM)

            val primaryLevelData: PrimaryLevelData

            if (levelStorageAccess.hasWorldData()) {
                // Load existing world data
                val dataTag = levelStorageAccess.dataTag
                val levelDataAndDimensions = LevelStorageSource.getLevelDataAndDimensions(
                    dataTag,
                    context.dataConfiguration(),
                    contextLevelStemRegistry,
                    context.datapackWorldgen()
                )

                primaryLevelData = levelDataAndDimensions.worldData() as PrimaryLevelData
                registryAccess = levelDataAndDimensions.dimensions().dimensionsRegistryAccess()
            } else {
                // Create new world data
                val actualSeed = seed ?: System.currentTimeMillis()
                val worldOptions = WorldOptions(actualSeed, true, false)

                // Build generator settings JSON
                val generatorSettings = "{}"
                val bukkitWorldTypeName = when (worldType) {
                    WorldType.NORMAL -> "normal"
                    WorldType.FLAT -> "flat"
                    WorldType.AMPLIFIED -> "amplified"
                    WorldType.LARGE_BIOMES -> "large_biomes"
                    WorldType.VOID -> "flat" // Use flat with custom generator
                }

                val properties = DedicatedServerProperties.WorldDimensionData(
                    GsonHelper.parse(generatorSettings),
                    bukkitWorldTypeName
                )

                val levelSettings = LevelSettings(
                    worldName,
                    GameType.SURVIVAL,
                    false, // hardcore
                    Difficulty.EASY,
                    false, // allowCommands
                    net.minecraft.world.level.gamerules.GameRules(context.dataConfiguration().enabledFeatures()),
                    context.dataConfiguration()
                )

                val worldDimensions = properties.create(context.datapackWorldgen())
                val complete = worldDimensions.bake(contextLevelStemRegistry)
                val lifecycle = complete.lifecycle().add(context.datapackWorldgen().allRegistriesLifecycle())

                primaryLevelData = PrimaryLevelData(levelSettings, worldOptions, complete.specialWorldProperty(), lifecycle)
                registryAccess = complete.dimensionsRegistryAccess()
            }

            // Update registry references
            contextLevelStemRegistry = registryAccess.lookupOrThrow(Registries.LEVEL_STEM)
            primaryLevelData.customDimensions = contextLevelStemRegistry
            primaryLevelData.checkName(worldName)
            primaryLevelData.setModdedInfo(console.serverModName, console.moddedStatus.shouldReportAsModified())

            // Create custom spawners list
            val obfuscatedSeed = BiomeManager.obfuscateSeed(primaryLevelData.worldGenOptions().seed())
            val spawners: List<CustomSpawner> = if (environment == World.Environment.NORMAL) {
                ImmutableList.of(
                    PhantomSpawner(),
                    PatrolSpawner(),
                    CatSpawner(),
                    VillageSiege(),
                    WanderingTraderSpawner(primaryLevelData)
                )
            } else {
                ImmutableList.of()
            }

            // Get level stem
            val customStem = contextLevelStemRegistry.getValue(actualDimension)
                ?: throw IllegalStateException("Cannot find dimension $actualDimension in registry")

            // Create world info for generator/biome provider
            val worldInfo: WorldInfo = CraftWorldInfo(
                primaryLevelData,
                levelStorageAccess,
                environment,
                customStem.type().value(),
                customStem.generator(),
                console.registryAccess()
            )

            // Set up biome provider if generator exists
            if (generator != null) {
                biomeProvider = generator.getDefaultBiomeProvider(worldInfo)
            }

            // Create resource key for the dimension
            val worldKey = ResourceKey.create(
                Registries.DIMENSION,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("playerworldmanager", worldName.lowercase())
            )

            // Create the ServerLevel
            val serverLevel = ServerLevel(
                console,
                console.executor,
                levelStorageAccess,
                primaryLevelData,
                worldKey,
                customStem,
                primaryLevelData.isDebugWorld,
                obfuscatedSeed,
                spawners,
                true, // shouldTickTime
                console.overworld().randomSequences,
                environment,
                generator,
                biomeProvider
            )

            // Add the world to the server
            console.addLevel(serverLevel)
            console.initWorld(serverLevel, primaryLevelData, primaryLevelData.worldGenOptions())

            // Set spawn settings
            serverLevel.setSpawnSettings(true)

            // Prepare the level
            console.prepareLevel(serverLevel)

            val craftWorld = serverLevel.world as CraftWorld
            logger.info("[WorldManager] createDimension: Successfully created dimension '$worldName' using NMS")
            debugLogger.debug("Dimension created successfully via NMS",
                "worldName" to worldName,
                "bukkitWorldName" to craftWorld.name
            )
            debugLogger.debugMethodExit("createDimension", craftWorld.name)
            return craftWorld

        } catch (e: Exception) {
            logger.severe("[WorldManager] createDimension: Failed to create dimension $worldName: ${e.message}")
            debugLogger.debug("Dimension creation failed",
                "worldName" to worldName,
                "exceptionType" to e.javaClass.simpleName,
                "exceptionMessage" to e.message
            )
            debugLogger.debugMethodExit("createDimension", null)
            e.printStackTrace()
            return null
        }
    }

    /**
     * Create or load a dimension world (used during initialization).
     * This method now uses the same NMS approach as createDimension.
     *
     * @param worldName The world name
     * @param environment The dimension type
     * @param worldType The generation type
     * @param seed The seed
     * @return The World, or null on failure
     */
    private fun createOrLoadDimension(
        worldName: String,
        environment: World.Environment,
        worldType: WorldType,
        seed: Long?
    ): World? {
        debugLogger.debugMethodEntry("createOrLoadDimension",
            "worldName" to worldName,
            "environment" to environment,
            "worldType" to worldType,
            "seed" to seed
        )
        // Check if world folder exists
        val worldFolder = File(plugin.server.worldContainer, worldName)
        val folderExists = worldFolder.exists()
        debugLogger.debug("World folder check",
            "folderPath" to worldFolder.absolutePath,
            "exists" to folderExists
        )

        if (folderExists) {
            logger.info("[WorldManager] createOrLoadDimension: Loading existing world '$worldName'")
            debugLogger.debug("Loading existing world folder")
        } else {
            logger.info("[WorldManager] createOrLoadDimension: Creating new world '$worldName'")
            debugLogger.debug("Creating new world (folder does not exist)")
        }

        // Use the unified NMS approach for both loading and creating
        debugLogger.debug("Delegating to createDimension")
        val result = createDimension(worldName, environment, worldType, seed)
        debugLogger.debugMethodExit("createOrLoadDimension", result?.name)
        return result
    }

    /**
     * Recursively delete a directory.
     *
     * @param directory The directory to delete
     */
    private fun deleteDirectory(directory: File) {
        if (!directory.exists()) return

        if (directory.isDirectory) {
            directory.listFiles()?.forEach { file ->
                deleteDirectory(file)
            }
        }

        directory.delete()
    }

    /**
     * Add world names to the pending deletions file.
     * These worlds will be deleted on next server startup.
     *
     * @param worldNames List of world names to mark for deletion
     */
    private fun addPendingDeletions(worldNames: List<String>) {
        try {
            val existingDeletions = if (pendingDeletionsFile.exists()) {
                pendingDeletionsFile.readLines().toMutableSet()
            } else {
                mutableSetOf()
            }

            existingDeletions.addAll(worldNames)

            pendingDeletionsFile.writeText(existingDeletions.joinToString("\n"))
            logger.info("[WorldManager] addPendingDeletions: Added ${worldNames.size} worlds to pending deletions list")
        } catch (e: Exception) {
            logger.severe("[WorldManager] addPendingDeletions: Failed to write pending deletions: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Mark a world for cleanup (removal from database).
     * This is used when a world's folder doesn't exist on disk but the PlayerWorld entry exists.
     *
     * @param world The world to mark for cleanup
     */
    private fun markWorldForCleanup(world: PlayerWorld) {
        debugLogger.debugMethodEntry("markWorldForCleanup",
            "worldName" to world.name,
            "worldId" to world.id,
            "ownerName" to world.ownerName
        )
        worldsToCleanup.add(world)
        debugLogger.debug("World added to cleanup list",
            "cleanupListSize" to worldsToCleanup.size
        )
        logger.warning("[WorldManager] markWorldForCleanup: World '${world.name}' (owner: ${world.ownerName}) marked for cleanup - folder missing")
        debugLogger.debugMethodExit("markWorldForCleanup")
    }

    /**
     * Process the cleanup list and remove orphaned world entries from the database.
     * This should be called after all worlds have been loaded during initialization.
     */
    private fun processCleanupList() {
        debugLogger.debugMethodEntry("processCleanupList", "cleanupListSize" to worldsToCleanup.size)
        if (worldsToCleanup.isEmpty()) {
            logger.info("[WorldManager] processCleanupList: No orphaned worlds to clean up")
            debugLogger.debug("Cleanup list is empty, nothing to process")
            debugLogger.debugMethodExit("processCleanupList")
            return
        }

        logger.info("[WorldManager] processCleanupList: Processing ${worldsToCleanup.size} orphaned world(s)")
        debugLogger.debug("Processing orphaned worlds",
            "worldNames" to worldsToCleanup.map { it.name }
        )

        worldsToCleanup.forEach { world ->
            try {
                // Remove from data manager
                dataManager.deleteWorld(world.id)

                // Update player data
                val playerData = dataManager.loadPlayerData(world.ownerUuid)
                if (playerData != null) {
                    playerData.ownedWorlds.remove(world.id)
                    dataManager.savePlayerData(playerData)
                }

                logger.info("[WorldManager] processCleanupList: Removed orphaned world '${world.name}' (owner: ${world.ownerName}) from database")
            } catch (e: Exception) {
                logger.severe("[WorldManager] processCleanupList: Failed to clean up world '${world.name}': ${e.message}")
                e.printStackTrace()
            }
        }

        val cleanedCount = worldsToCleanup.size
        logger.info("[WorldManager] processCleanupList: Cleanup complete - removed $cleanedCount orphaned world(s)")
        debugLogger.debug("Cleanup complete", "cleanedCount" to cleanedCount)
        worldsToCleanup.clear()
        debugLogger.debugMethodExit("processCleanupList")
    }

    /**
     * Process pending world deletions from previous server runs.
     * This should be called during plugin initialization before worlds are loaded.
     */
    fun processPendingDeletions() {
        debugLogger.debugMethodEntry("processPendingDeletions",
            "pendingDeletionsFile" to pendingDeletionsFile.absolutePath
        )
        if (!pendingDeletionsFile.exists()) {
            logger.info("[WorldManager] processPendingDeletions: No pending deletions file found")
            debugLogger.debug("Pending deletions file does not exist")
            debugLogger.debugMethodExit("processPendingDeletions")
            return
        }
        debugLogger.debug("Pending deletions file exists")

        try {
            val worldNames = pendingDeletionsFile.readLines().filter { it.isNotBlank() }
            if (worldNames.isEmpty()) {
                logger.info("[WorldManager] processPendingDeletions: Pending deletions file is empty")
                pendingDeletionsFile.delete()
                return
            }

            logger.info("[WorldManager] processPendingDeletions: Found ${worldNames.size} worlds pending deletion")

            val serverFolder = plugin.server.worldContainer
            var deletedCount = 0

            worldNames.forEach { worldName ->
                val worldFolder = File(serverFolder, worldName)
                if (worldFolder.exists()) {
                    try {
                        deleteDirectory(worldFolder)
                        logger.info("[WorldManager] processPendingDeletions: Deleted world folder: $worldName")
                        deletedCount++
                    } catch (e: Exception) {
                        logger.warning("[WorldManager] processPendingDeletions: Failed to delete world folder $worldName: ${e.message}")
                    }
                } else {
                    logger.info("[WorldManager] processPendingDeletions: World folder $worldName already deleted")
                }
            }

            // Clear the pending deletions file
            pendingDeletionsFile.delete()
            debugLogger.debug("Cleared pending deletions file")
            logger.info("[WorldManager] processPendingDeletions: Completed - deleted $deletedCount of ${worldNames.size} world folders")
            debugLogger.debug("Pending deletions processing complete",
                "deletedCount" to deletedCount,
                "totalPending" to worldNames.size
            )
            debugLogger.debugMethodExit("processPendingDeletions")

        } catch (e: Exception) {
            logger.severe("[WorldManager] processPendingDeletions: Error processing pending deletions: ${e.message}")
            debugLogger.debug("Error processing pending deletions",
                "exceptionType" to e.javaClass.simpleName,
                "exceptionMessage" to e.message
            )
            debugLogger.debugMethodExit("processPendingDeletions", "error: ${e.message}")
            e.printStackTrace()
        }
    }
}
