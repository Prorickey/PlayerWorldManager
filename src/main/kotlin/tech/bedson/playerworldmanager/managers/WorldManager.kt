package tech.bedson.playerworldmanager.managers

import org.bukkit.Bukkit
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.models.PlayerWorld
import tech.bedson.playerworldmanager.models.TimeLock
import tech.bedson.playerworldmanager.models.WeatherLock
import tech.bedson.playerworldmanager.models.WorldType
import tech.bedson.playerworldmanager.models.toBukkitLocation
import tech.bedson.playerworldmanager.models.toSimpleLocation
import tech.bedson.playerworldmanager.utils.VoidGenerator
import java.io.File
import java.util.UUID
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
    private val dataManager: DataManager
) {

    private val logger: Logger = plugin.logger

    // World name validation regex (alphanumeric + underscores)
    private val namePattern = Regex("^[a-zA-Z0-9_]+$")

    // Max world name length
    private val maxNameLength = 32

    // Default world limit per player
    private val defaultWorldLimit = 3

    companion object {
        // World name suffixes for dimensions
        private const val NETHER_SUFFIX = "_nether"
        private const val END_SUFFIX = "_the_end"
    }

    /**
     * Initialize the world manager - load all worlds on startup.
     */
    fun initialize() {
        logger.info("Initializing WorldManager...")

        // Load all worlds from data
        val worlds = dataManager.getAllWorlds()
        logger.info("Found ${worlds.size} worlds to load")

        // Load each world asynchronously
        val futures = worlds.map { world ->
            loadWorld(world).thenAccept { success ->
                if (success) {
                    logger.info("Loaded world: ${world.name} (owner: ${world.ownerName})")
                } else {
                    logger.warning("Failed to load world: ${world.name}")
                }
            }
        }

        // Wait for all loads to complete
        CompletableFuture.allOf(*futures.toTypedArray()).join()

        logger.info("WorldManager initialization complete")
    }

    /**
     * Shutdown the world manager - save and unload all worlds.
     */
    fun shutdown() {
        logger.info("Shutting down WorldManager...")

        val worlds = getLoadedWorlds()
        logger.info("Unloading ${worlds.size} worlds...")

        // Unload all worlds
        val futures = worlds.map { world ->
            unloadWorld(world)
        }

        // Wait for all unloads to complete
        CompletableFuture.allOf(*futures.toTypedArray()).join()

        logger.info("WorldManager shutdown complete")
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
        logger.info("[WorldManager] createWorld: Player '${owner.name}' attempting to create world '$name' (Type: $worldType, Seed: ${seed ?: "random"})")
        val future = CompletableFuture<Result<PlayerWorld>>()

        // Validate world name
        val validationError = validateWorldName(name)
        if (validationError != null) {
            logger.warning("[WorldManager] createWorld: Validation failed for world '$name': $validationError")
            future.complete(Result.failure(IllegalArgumentException(validationError)))
            return future
        }

        // Check if player can create more worlds
        if (!canCreateWorld(owner)) {
            val limit = dataManager.loadPlayerData(owner.uniqueId)?.worldLimit ?: defaultWorldLimit
            logger.warning("[WorldManager] createWorld: Player '${owner.name}' has reached world limit ($limit worlds)")
            future.complete(Result.failure(IllegalStateException("You have reached your world limit ($limit worlds)")))
            return future
        }

        // Check if world name already exists for this player
        val existingWorld = dataManager.getWorldsByOwner(owner.uniqueId)
            .firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (existingWorld != null) {
            logger.warning("[WorldManager] createWorld: Player '${owner.name}' already has a world named '$name'")
            future.complete(Result.failure(IllegalArgumentException("You already have a world named '$name'")))
            return future
        }

        // Create PlayerWorld object
        val playerWorld = PlayerWorld(
            id = UUID.randomUUID(),
            name = name,
            ownerUuid = owner.uniqueId,
            ownerName = owner.name,
            worldType = worldType,
            seed = seed,
            createdAt = System.currentTimeMillis()
        )
        logger.info("[WorldManager] createWorld: Created PlayerWorld object with ID: ${playerWorld.id}")

        // Create worlds on global region scheduler
        Bukkit.getGlobalRegionScheduler().run(plugin) { _ ->
            try {
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
                future.complete(Result.success(playerWorld))

            } catch (e: Exception) {
                logger.severe("[WorldManager] createWorld: Error creating world '${playerWorld.name}': ${e.message}")
                e.printStackTrace()
                future.complete(Result.failure(e))
            }
        }

        return future
    }

    /**
     * Delete a world and all its dimensions.
     *
     * @param world The world to delete
     * @return CompletableFuture with Result
     */
    fun deleteWorld(world: PlayerWorld): CompletableFuture<Result<Unit>> {
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
                            // Unload all dimensions
                            logger.info("[WorldManager] deleteWorld: Unloading all dimensions for world '${world.name}'")
                            for (env in World.Environment.entries) {
                                val worldName = getWorldName(world, env)
                                val bukkitWorld = Bukkit.getWorld(worldName)
                                if (bukkitWorld != null) {
                                    Bukkit.unloadWorld(bukkitWorld, true)
                                    logger.info("[WorldManager] deleteWorld: Unloaded world: $worldName")
                                } else {
                                    logger.info("[WorldManager] deleteWorld: World $worldName not loaded, skipping unload")
                                }
                            }

                            // Delete world folders from disk
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
                            future.complete(Result.success(Unit))

                        } catch (e: Exception) {
                            logger.severe("[WorldManager] deleteWorld: Error deleting world '${world.name}': ${e.message}")
                            e.printStackTrace()
                            future.complete(Result.failure(e))
                        }
                    }
                }

            } catch (e: Exception) {
                logger.severe("[WorldManager] deleteWorld: Error in initial deletion phase for world '${world.name}': ${e.message}")
                e.printStackTrace()
                future.complete(Result.failure(e))
            }
        }

        return future
    }

    /**
     * Load a world from disk.
     *
     * @param world The world to load
     * @return CompletableFuture with success status
     */
    fun loadWorld(world: PlayerWorld): CompletableFuture<Boolean> {
        logger.info("[WorldManager] loadWorld: Loading world '${world.name}' (ID: ${world.id}, Owner: ${world.ownerName})")
        val future = CompletableFuture<Boolean>()

        if (!world.isEnabled) {
            logger.info("[WorldManager] loadWorld: World '${world.name}' is disabled, skipping load")
            future.complete(false)
            return future
        }

        Bukkit.getGlobalRegionScheduler().run(plugin) { _ ->
            try {
                var success = true

                // Load overworld
                val overworldName = getWorldName(world, World.Environment.NORMAL)
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

                // Load nether
                val netherName = getWorldName(world, World.Environment.NETHER)
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

                // Load end
                val endName = getWorldName(world, World.Environment.THE_END)
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

                // Apply world settings
                if (success) {
                    logger.info("[WorldManager] loadWorld: Applying world settings for '${world.name}'")
                    applyWorldSettings(world)
                }

                logger.info("[WorldManager] loadWorld: Load complete for world '${world.name}' - Success: $success")
                future.complete(success)

            } catch (e: Exception) {
                logger.severe("[WorldManager] loadWorld: Error loading world ${world.name}: ${e.message}")
                e.printStackTrace()
                future.complete(false)
            }
        }

        return future
    }

    /**
     * Unload a world (save and remove from memory).
     *
     * @param world The world to unload
     * @return CompletableFuture with success status
     */
    fun unloadWorld(world: PlayerWorld): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()

        Bukkit.getGlobalRegionScheduler().run(plugin) { _ ->
            try {
                var success = true

                // Unload all dimensions
                for (env in World.Environment.entries) {
                    val worldName = getWorldName(world, env)
                    val bukkitWorld = Bukkit.getWorld(worldName)
                    if (bukkitWorld != null) {
                        // Kick players before unloading
                        val defaultWorld = Bukkit.getWorlds().firstOrNull()
                        if (defaultWorld != null && bukkitWorld.players.isNotEmpty()) {
                            val teleportFutures = bukkitWorld.players.map { player ->
                                player.teleportAsync(defaultWorld.spawnLocation)
                            }
                            CompletableFuture.allOf(*teleportFutures.toTypedArray()).join()
                        }

                        // Unload with save
                        if (!Bukkit.unloadWorld(bukkitWorld, true)) {
                            logger.warning("Failed to unload world: $worldName")
                            success = false
                        }
                    }
                }

                future.complete(success)

            } catch (e: Exception) {
                logger.severe("Error unloading world ${world.name}: ${e.message}")
                e.printStackTrace()
                future.complete(false)
            }
        }

        return future
    }

    /**
     * Teleport player to a world's spawn.
     *
     * @param player The player to teleport
     * @param world The world to teleport to
     * @return CompletableFuture with success status
     */
    fun teleportToWorld(player: Player, world: PlayerWorld): CompletableFuture<Boolean> {
        logger.info("[WorldManager] teleportToWorld: Teleporting player '${player.name}' to world '${world.name}'")
        return teleportToDimension(player, world, World.Environment.NORMAL)
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
        logger.info("[WorldManager] teleportToDimension: Teleporting player '${player.name}' to world '${world.name}' dimension $environment")
        val future = CompletableFuture<Boolean>()

        val bukkitWorld = getBukkitWorld(world, environment)
        if (bukkitWorld == null) {
            logger.warning("[WorldManager] teleportToDimension: Bukkit world not loaded for '${world.name}' dimension $environment")
            future.complete(false)
            return future
        }

        // Determine teleport location
        val location = when {
            environment == World.Environment.NORMAL && world.spawnLocation != null -> {
                logger.info("[WorldManager] teleportToDimension: Using custom spawn location for '${world.name}'")
                world.spawnLocation!!.toBukkitLocation(bukkitWorld)
            }
            else -> {
                logger.info("[WorldManager] teleportToDimension: Using default spawn location for '${world.name}'")
                bukkitWorld.spawnLocation
            }
        }

        // Use async teleportation
        player.teleportAsync(location).thenAccept { success ->
            if (success) {
                logger.info("[WorldManager] teleportToDimension: Successfully teleported player '${player.name}' to '${world.name}'")
                // Set player gamemode on entity scheduler
                player.scheduler.run(plugin, { _ ->
                    logger.info("[WorldManager] teleportToDimension: Setting gamemode ${world.defaultGameMode} for player '${player.name}'")
                    player.gameMode = world.defaultGameMode
                }, null)
            } else {
                logger.warning("[WorldManager] teleportToDimension: Failed to teleport player '${player.name}' to '${world.name}'")
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
        val worldName = getWorldName(world, environment)
        return Bukkit.getWorld(worldName)
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
        val currentCount = getWorldCount(player.uniqueId)
        val limit = dataManager.loadPlayerData(player.uniqueId)?.worldLimit ?: defaultWorldLimit
        return currentCount < limit
    }

    /**
     * Get world count for a player.
     *
     * @param playerUuid The player's UUID
     * @return Number of worlds owned by player
     */
    fun getWorldCount(playerUuid: UUID): Int {
        return dataManager.getWorldsByOwner(playerUuid).size
    }

    /**
     * Apply world settings (time lock, weather lock, gamemode).
     *
     * @param world The world to apply settings to
     */
    fun applyWorldSettings(world: PlayerWorld) {
        logger.info("[WorldManager] applyWorldSettings: Applying settings to world '${world.name}' (TimeLock: ${world.timeLock}, WeatherLock: ${world.weatherLock})")
        val bukkitWorld = getBukkitWorld(world) ?: run {
            logger.warning("[WorldManager] applyWorldSettings: Could not get Bukkit world for '${world.name}'")
            return
        }

        // Apply time lock
        when (world.timeLock) {
            TimeLock.DAY -> {
                logger.info("[WorldManager] applyWorldSettings: Setting time to DAY (locked) for '${world.name}'")
                bukkitWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
                bukkitWorld.time = 6000 // Noon
            }
            TimeLock.NIGHT -> {
                logger.info("[WorldManager] applyWorldSettings: Setting time to NIGHT (locked) for '${world.name}'")
                bukkitWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
                bukkitWorld.time = 18000 // Midnight
            }
            TimeLock.CYCLE -> {
                logger.info("[WorldManager] applyWorldSettings: Enabling time CYCLE for '${world.name}'")
                bukkitWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true)
            }
        }

        // Apply weather lock
        when (world.weatherLock) {
            WeatherLock.CLEAR -> {
                logger.info("[WorldManager] applyWorldSettings: Setting weather to CLEAR (locked) for '${world.name}'")
                bukkitWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false)
                bukkitWorld.setStorm(false)
                bukkitWorld.isThundering = false
            }
            WeatherLock.RAIN -> {
                logger.info("[WorldManager] applyWorldSettings: Setting weather to RAIN (locked) for '${world.name}'")
                bukkitWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false)
                bukkitWorld.setStorm(true)
                bukkitWorld.isThundering = false
            }
            WeatherLock.CYCLE -> {
                logger.info("[WorldManager] applyWorldSettings: Enabling weather CYCLE for '${world.name}'")
                bukkitWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, true)
            }
        }
        logger.info("[WorldManager] applyWorldSettings: Successfully applied settings to world '${world.name}'")
    }

    /**
     * Update spawn location for a world.
     *
     * @param world The world to update
     * @param location The new spawn location
     */
    fun setSpawnLocation(world: PlayerWorld, location: Location) {
        logger.info("[WorldManager] setSpawnLocation: Setting spawn location for world '${world.name}' to ${location.x}, ${location.y}, ${location.z}")
        world.spawnLocation = location.toSimpleLocation()
        dataManager.saveWorld(world)

        // Update Bukkit world spawn
        val bukkitWorld = getBukkitWorld(world)
        bukkitWorld?.setSpawnLocation(location)
        logger.info("[WorldManager] setSpawnLocation: Successfully updated spawn location for '${world.name}'")
    }

    /**
     * Find PlayerWorld from Bukkit World name.
     *
     * @param bukkitWorld The Bukkit world
     * @return The PlayerWorld, or null if not found
     */
    fun getPlayerWorldFromBukkitWorld(bukkitWorld: World): PlayerWorld? {
        val worldName = bukkitWorld.name

        // Try to extract owner name and world name from the Bukkit world name
        // Format: ownername_worldname or ownername_worldname_nether or ownername_worldname_the_end
        val baseName = worldName
            .removeSuffix(NETHER_SUFFIX)
            .removeSuffix(END_SUFFIX)

        // Find matching world
        return dataManager.getAllWorlds().firstOrNull { world ->
            val expectedBaseName = "${world.ownerName}_${world.name}".lowercase().replace(" ", "_")
            baseName == expectedBaseName
        }
    }

    /**
     * Check if a Bukkit world belongs to this plugin.
     *
     * @param world The world to check
     * @return True if this is a plugin-managed world
     */
    fun isPluginWorld(world: World): Boolean {
        return getPlayerWorldFromBukkitWorld(world) != null
    }

    /**
     * Get all loaded plugin worlds.
     *
     * @return List of loaded PlayerWorlds
     */
    fun getLoadedWorlds(): List<PlayerWorld> {
        return dataManager.getAllWorlds().filter { world ->
            getBukkitWorld(world) != null
        }
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
        return when {
            name.isBlank() -> "World name cannot be empty"
            name.length > maxNameLength -> "World name is too long (max $maxNameLength characters)"
            !namePattern.matches(name) -> "World name can only contain letters, numbers, and underscores"
            else -> null
        }
    }

    /**
     * Create a dimension world.
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
        logger.info("[WorldManager] createDimension: Creating dimension '$worldName' (Environment: $environment, Type: $worldType, Seed: ${seed ?: "random"})")
        return try {
            val creator = WorldCreator.name(worldName)
                .environment(environment)

            // Apply seed if provided
            if (seed != null) {
                creator.seed(seed)
            }

            // Apply world type
            when (worldType) {
                WorldType.NORMAL -> creator.type(org.bukkit.WorldType.NORMAL)
                WorldType.FLAT -> creator.type(org.bukkit.WorldType.FLAT)
                WorldType.AMPLIFIED -> creator.type(org.bukkit.WorldType.AMPLIFIED)
                WorldType.LARGE_BIOMES -> creator.type(org.bukkit.WorldType.LARGE_BIOMES)
                WorldType.VOID -> {
                    creator.type(org.bukkit.WorldType.FLAT)
                    creator.generator(VoidGenerator())
                }
            }

            val result = creator.createWorld()
            logger.info("[WorldManager] createDimension: Successfully created dimension '$worldName'")
            result

        } catch (e: Exception) {
            logger.severe("[WorldManager] createDimension: Failed to create dimension $worldName: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Create or load a dimension world (used during initialization).
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
        // Check if world folder exists
        val worldFolder = File(plugin.server.worldContainer, worldName)

        return if (worldFolder.exists()) {
            // Load existing world
            try {
                val creator = WorldCreator.name(worldName)
                    .environment(environment)

                // For void worlds, we need to specify the generator even when loading
                if (worldType == WorldType.VOID) {
                    creator.generator(VoidGenerator())
                }

                creator.createWorld()
            } catch (e: Exception) {
                logger.severe("Failed to load dimension $worldName: ${e.message}")
                e.printStackTrace()
                null
            }
        } else {
            // Create new world
            createDimension(worldName, environment, worldType, seed)
        }
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
}
