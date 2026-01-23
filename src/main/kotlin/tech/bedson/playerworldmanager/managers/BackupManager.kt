package tech.bedson.playerworldmanager.managers

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.models.BackupSchedule
import tech.bedson.playerworldmanager.models.PlayerWorld
import tech.bedson.playerworldmanager.models.WorldBackup
import tech.bedson.playerworldmanager.utils.DebugLogger
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

/**
 * Manages world backups - creation, restoration, deletion, and scheduling.
 *
 * Backups are stored in:
 * - plugins/PlayerWorldManager/backups/ - Individual backup JSON metadata files
 * - plugins/PlayerWorldManager/backups/data/ - Actual backup world folders
 *
 * This manager is Folia-compatible and uses:
 * - AsyncScheduler for file I/O operations
 * - GlobalRegionScheduler for scheduled backup checks
 */
class BackupManager(
    private val plugin: JavaPlugin,
    private val dataManager: DataManager,
    private val worldManager: WorldManager
) {
    private val logger: Logger = plugin.logger
    private val debugLogger = DebugLogger(plugin, "BackupManager")

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private val backupsFolder = File(plugin.dataFolder, "backups")
    private val backupsDataFolder = File(backupsFolder, "data")
    private val schedulesFile = File(backupsFolder, "schedules.json")

    // In-memory caches
    private val backups = ConcurrentHashMap<UUID, WorldBackup>()
    private val schedules = ConcurrentHashMap<UUID, BackupSchedule>()

    // Scheduler task reference for cleanup
    private var schedulerTaskId: Long = -1

    init {
        backupsFolder.mkdirs()
        backupsDataFolder.mkdirs()
    }

    // ========================
    // Configuration
    // ========================

    /**
     * Get the maximum number of backups allowed per world from config.
     */
    fun getMaxBackupsPerWorld(): Int {
        return plugin.config.getInt("backups.max-backups-per-world", 5)
    }

    /**
     * Get the default backup interval in minutes from config.
     */
    fun getDefaultBackupIntervalMinutes(): Int {
        return plugin.config.getInt("backups.default-interval-minutes", 1440) // 24 hours
    }

    /**
     * Check if automatic backups are globally enabled.
     */
    fun isAutoBackupEnabled(): Boolean {
        return plugin.config.getBoolean("backups.auto-backup-enabled", true)
    }

    // ========================
    // Backup Operations
    // ========================

    /**
     * Create a backup of a world.
     *
     * @param world The PlayerWorld to backup
     * @param description Optional description for the backup
     * @param isAutomatic Whether this is an automatic backup
     * @return CompletableFuture with Result containing WorldBackup or error
     */
    fun createBackup(
        world: PlayerWorld,
        description: String? = null,
        isAutomatic: Boolean = false
    ): CompletableFuture<Result<WorldBackup>> {
        debugLogger.debugMethodEntry("createBackup",
            "worldName" to world.name,
            "worldId" to world.id,
            "description" to description,
            "isAutomatic" to isAutomatic
        )

        val future = CompletableFuture<Result<WorldBackup>>()

        // Run file operations asynchronously
        Bukkit.getAsyncScheduler().runNow(plugin) { _ ->
            try {
                // First, enforce backup limit by removing old backups if needed
                enforceBackupLimit(world.id)

                val backupId = UUID.randomUUID()
                val timestamp = System.currentTimeMillis()
                val backupFolderName = "${world.ownerName}_${world.name}_$timestamp"
                val backupPath = "data/$backupFolderName"

                debugLogger.debug("Creating backup",
                    "backupId" to backupId,
                    "backupPath" to backupPath
                )

                // Get the world folder (main overworld)
                val worldFolderName = "${world.ownerName}_${world.name}"
                val worldFolder = File(Bukkit.getServer().worldContainer, "worlds/$worldFolderName")

                if (!worldFolder.exists()) {
                    debugLogger.debug("World folder not found", "path" to worldFolder.absolutePath)
                    future.complete(Result.failure(IllegalStateException("World folder not found: ${worldFolder.absolutePath}")))
                    return@runNow
                }

                // Create backup destination folder
                val backupDestination = File(backupsDataFolder, backupFolderName)
                backupDestination.mkdirs()

                debugLogger.debug("Copying world folder",
                    "source" to worldFolder.absolutePath,
                    "destination" to backupDestination.absolutePath
                )

                // Copy world folder (overworld)
                copyDirectory(worldFolder, backupDestination)

                // Also backup nether and end dimensions if they exist
                val netherFolder = File(Bukkit.getServer().worldContainer, "worlds/${worldFolderName}_nether")
                if (netherFolder.exists()) {
                    val netherBackup = File(backupsDataFolder, "${backupFolderName}_nether")
                    copyDirectory(netherFolder, netherBackup)
                    debugLogger.debug("Copied nether dimension")
                }

                val endFolder = File(Bukkit.getServer().worldContainer, "worlds/${worldFolderName}_the_end")
                if (endFolder.exists()) {
                    val endBackup = File(backupsDataFolder, "${backupFolderName}_the_end")
                    copyDirectory(endFolder, endBackup)
                    debugLogger.debug("Copied end dimension")
                }

                // Calculate total size
                val totalSize = calculateDirectorySize(backupDestination)

                // Create backup metadata
                val backup = WorldBackup(
                    id = backupId,
                    worldId = world.id,
                    worldName = world.name,
                    ownerUuid = world.ownerUuid,
                    ownerName = world.ownerName,
                    createdAt = timestamp,
                    backupPath = backupPath,
                    sizeBytes = totalSize,
                    description = description,
                    isAutomatic = isAutomatic
                )

                // Save backup metadata
                saveBackup(backup)

                logger.info("[BackupManager] Created backup for world '${world.name}': ${backup.id}")
                debugLogger.debugMethodExit("createBackup", backup.toCompactDebugString())
                future.complete(Result.success(backup))

            } catch (e: Exception) {
                logger.severe("[BackupManager] Failed to create backup for world '${world.name}': ${e.message}")
                debugLogger.debug("Backup creation failed",
                    "worldName" to world.name,
                    "error" to e.message
                )
                future.complete(Result.failure(e))
            }
        }

        return future
    }

    /**
     * Restore a world from a backup.
     *
     * @param backup The backup to restore
     * @return CompletableFuture with Result indicating success or error
     */
    fun restoreBackup(backup: WorldBackup): CompletableFuture<Result<Unit>> {
        debugLogger.debugMethodEntry("restoreBackup",
            "backupId" to backup.id,
            "worldId" to backup.worldId,
            "worldName" to backup.worldName
        )

        val future = CompletableFuture<Result<Unit>>()

        // Get the current world
        val world = dataManager.loadWorld(backup.worldId)
        if (world == null) {
            debugLogger.debug("World not found for backup", "worldId" to backup.worldId)
            future.complete(Result.failure(IllegalStateException("World not found")))
            return future
        }

        // Run file operations asynchronously
        Bukkit.getAsyncScheduler().runNow(plugin) { _ ->
            try {
                val backupFolderName = backup.backupPath.removePrefix("data/")
                val backupSource = File(backupsDataFolder, backupFolderName)

                if (!backupSource.exists()) {
                    debugLogger.debug("Backup folder not found", "path" to backupSource.absolutePath)
                    future.complete(Result.failure(IllegalStateException("Backup folder not found")))
                    return@runNow
                }

                // Get world folders
                val worldFolderName = "${world.ownerName}_${world.name}"
                val worldFolder = File(Bukkit.getServer().worldContainer, "worlds/$worldFolderName")

                debugLogger.debug("Restoring backup",
                    "source" to backupSource.absolutePath,
                    "destination" to worldFolder.absolutePath
                )

                // Kick all players from the world before restoring
                Bukkit.getGlobalRegionScheduler().run(plugin) { _ ->
                    val bukkitWorld = Bukkit.getWorld(worldFolderName)
                    bukkitWorld?.players?.forEach { player ->
                        player.sendMessage(net.kyori.adventure.text.Component.text(
                            "World is being restored from backup. You have been moved to spawn.",
                            net.kyori.adventure.text.format.NamedTextColor.YELLOW
                        ))
                        worldManager.teleportToVanillaWorld(player)
                    }

                    // Continue restoration after players are moved
                    Bukkit.getAsyncScheduler().runDelayed(plugin, { _ ->
                        try {
                            // Delete existing world folder and restore from backup
                            if (worldFolder.exists()) {
                                deleteDirectory(worldFolder)
                            }
                            copyDirectory(backupSource, worldFolder)

                            // Restore nether if backup exists
                            val netherBackup = File(backupsDataFolder, "${backupFolderName}_nether")
                            if (netherBackup.exists()) {
                                val netherFolder = File(Bukkit.getServer().worldContainer, "worlds/${worldFolderName}_nether")
                                if (netherFolder.exists()) {
                                    deleteDirectory(netherFolder)
                                }
                                copyDirectory(netherBackup, netherFolder)
                                debugLogger.debug("Restored nether dimension")
                            }

                            // Restore end if backup exists
                            val endBackup = File(backupsDataFolder, "${backupFolderName}_the_end")
                            if (endBackup.exists()) {
                                val endFolder = File(Bukkit.getServer().worldContainer, "worlds/${worldFolderName}_the_end")
                                if (endFolder.exists()) {
                                    deleteDirectory(endFolder)
                                }
                                copyDirectory(endBackup, endFolder)
                                debugLogger.debug("Restored end dimension")
                            }

                            logger.info("[BackupManager] Restored world '${world.name}' from backup ${backup.id}")
                            debugLogger.debugMethodExit("restoreBackup", "success")
                            future.complete(Result.success(Unit))

                        } catch (e: Exception) {
                            logger.severe("[BackupManager] Failed to restore backup: ${e.message}")
                            future.complete(Result.failure(e))
                        }
                    }, 2, TimeUnit.SECONDS)
                }

            } catch (e: Exception) {
                logger.severe("[BackupManager] Failed to restore backup: ${e.message}")
                debugLogger.debug("Backup restoration failed", "error" to e.message)
                future.complete(Result.failure(e))
            }
        }

        return future
    }

    /**
     * Delete a backup.
     *
     * @param backup The backup to delete
     * @return CompletableFuture with Result indicating success or error
     */
    fun deleteBackup(backup: WorldBackup): CompletableFuture<Result<Unit>> {
        debugLogger.debugMethodEntry("deleteBackup", "backupId" to backup.id)

        val future = CompletableFuture<Result<Unit>>()

        Bukkit.getAsyncScheduler().runNow(plugin) { _ ->
            try {
                // Delete backup data folder
                val backupFolderName = backup.backupPath.removePrefix("data/")
                val backupFolder = File(backupsDataFolder, backupFolderName)
                if (backupFolder.exists()) {
                    deleteDirectory(backupFolder)
                }

                // Delete nether backup if exists
                val netherBackup = File(backupsDataFolder, "${backupFolderName}_nether")
                if (netherBackup.exists()) {
                    deleteDirectory(netherBackup)
                }

                // Delete end backup if exists
                val endBackup = File(backupsDataFolder, "${backupFolderName}_the_end")
                if (endBackup.exists()) {
                    deleteDirectory(endBackup)
                }

                // Remove from cache and delete metadata file
                backups.remove(backup.id)
                val metadataFile = File(backupsFolder, "${backup.id}.json")
                if (metadataFile.exists()) {
                    metadataFile.delete()
                }

                logger.info("[BackupManager] Deleted backup ${backup.id} for world '${backup.worldName}'")
                debugLogger.debugMethodExit("deleteBackup", "success")
                future.complete(Result.success(Unit))

            } catch (e: Exception) {
                logger.severe("[BackupManager] Failed to delete backup: ${e.message}")
                future.complete(Result.failure(e))
            }
        }

        return future
    }

    // ========================
    // Backup Queries
    // ========================

    /**
     * Get all backups for a specific world.
     */
    fun getBackupsForWorld(worldId: UUID): List<WorldBackup> {
        debugLogger.debugMethodEntry("getBackupsForWorld", "worldId" to worldId)
        val worldBackups = backups.values.filter { it.worldId == worldId }
            .sortedByDescending { it.createdAt }
        debugLogger.debugMethodExit("getBackupsForWorld", worldBackups.size)
        return worldBackups
    }

    /**
     * Get all backups for a specific player (all their worlds).
     */
    fun getBackupsForPlayer(playerUuid: UUID): List<WorldBackup> {
        debugLogger.debugMethodEntry("getBackupsForPlayer", "playerUuid" to playerUuid)
        val playerBackups = backups.values.filter { it.ownerUuid == playerUuid }
            .sortedByDescending { it.createdAt }
        debugLogger.debugMethodExit("getBackupsForPlayer", playerBackups.size)
        return playerBackups
    }

    /**
     * Get a specific backup by ID.
     */
    fun getBackup(backupId: UUID): WorldBackup? {
        return backups[backupId]
    }

    /**
     * Get all backups.
     */
    fun getAllBackups(): List<WorldBackup> {
        return backups.values.toList().sortedByDescending { it.createdAt }
    }

    // ========================
    // Schedule Management
    // ========================

    /**
     * Enable or update scheduled backups for a world.
     */
    fun setBackupSchedule(worldId: UUID, enabled: Boolean, intervalMinutes: Int? = null): BackupSchedule {
        debugLogger.debugMethodEntry("setBackupSchedule",
            "worldId" to worldId,
            "enabled" to enabled,
            "intervalMinutes" to intervalMinutes
        )

        val existing = schedules[worldId]
        val schedule = BackupSchedule(
            worldId = worldId,
            enabled = enabled,
            intervalMinutes = intervalMinutes ?: existing?.intervalMinutes ?: getDefaultBackupIntervalMinutes(),
            lastBackupTime = existing?.lastBackupTime ?: 0
        )

        schedules[worldId] = schedule
        saveSchedules()

        debugLogger.debugMethodExit("setBackupSchedule", schedule.toString())
        return schedule
    }

    /**
     * Get the backup schedule for a world.
     */
    fun getBackupSchedule(worldId: UUID): BackupSchedule? {
        return schedules[worldId]
    }

    /**
     * Disable scheduled backups for a world.
     */
    fun disableBackupSchedule(worldId: UUID) {
        debugLogger.debugMethodEntry("disableBackupSchedule", "worldId" to worldId)
        schedules[worldId]?.let { existing ->
            schedules[worldId] = existing.copy(enabled = false)
            saveSchedules()
        }
        debugLogger.debugMethodExit("disableBackupSchedule")
    }

    // ========================
    // Initialization & Scheduling
    // ========================

    /**
     * Initialize the backup manager - load data and start scheduler.
     */
    fun initialize() {
        debugLogger.debugMethodEntry("initialize")
        logger.info("Initializing BackupManager...")

        // Load all backup metadata
        loadAllBackups()

        // Load schedules
        loadSchedules()

        // Start the scheduler task for automatic backups
        startSchedulerTask()

        logger.info("BackupManager initialized with ${backups.size} backups and ${schedules.size} schedules")
        debugLogger.debugMethodExit("initialize")
    }

    /**
     * Shutdown the backup manager.
     */
    fun shutdown() {
        debugLogger.debugMethodEntry("shutdown")
        logger.info("Shutting down BackupManager...")

        // Save all data
        saveSchedules()

        logger.info("BackupManager shutdown complete")
        debugLogger.debugMethodExit("shutdown")
    }

    /**
     * Start the scheduler task that checks for automatic backups.
     */
    private fun startSchedulerTask() {
        debugLogger.debug("Starting backup scheduler task")

        // Check every minute for worlds that need backup
        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, { _ ->
            if (!isAutoBackupEnabled()) return@runAtFixedRate

            val currentTime = System.currentTimeMillis()

            schedules.values.filter { it.enabled }.forEach { schedule ->
                val timeSinceLastBackup = currentTime - schedule.lastBackupTime
                val intervalMs = schedule.intervalMinutes.toLong() * 60 * 1000

                if (timeSinceLastBackup >= intervalMs) {
                    // Time for a backup
                    val world = dataManager.loadWorld(schedule.worldId)
                    if (world != null) {
                        debugLogger.debug("Running scheduled backup",
                            "worldName" to world.name,
                            "worldId" to world.id
                        )

                        createBackup(world, "Automatic backup", isAutomatic = true).thenAccept { result ->
                            result.onSuccess {
                                // Update last backup time
                                schedules[schedule.worldId] = schedule.copy(lastBackupTime = currentTime)
                                saveSchedules()
                                logger.info("[BackupManager] Automatic backup completed for world '${world.name}'")
                            }.onFailure { error ->
                                logger.warning("[BackupManager] Automatic backup failed for world '${world.name}': ${error.message}")
                            }
                        }
                    }
                }
            }
        }, 1, 1, TimeUnit.MINUTES)
    }

    // ========================
    // Persistence
    // ========================

    /**
     * Save backup metadata to disk.
     */
    private fun saveBackup(backup: WorldBackup) {
        debugLogger.debugMethodEntry("saveBackup", "backupId" to backup.id)
        backups[backup.id] = backup
        val file = File(backupsFolder, "${backup.id}.json")
        try {
            file.writeText(gson.toJson(backup))
            debugLogger.debug("Backup metadata saved", "file" to file.name)
        } catch (e: Exception) {
            logger.severe("[BackupManager] Failed to save backup metadata: ${e.message}")
        }
        debugLogger.debugMethodExit("saveBackup")
    }

    /**
     * Load all backup metadata from disk.
     */
    private fun loadAllBackups() {
        debugLogger.debugMethodEntry("loadAllBackups")
        val files = backupsFolder.listFiles()?.filter { it.extension == "json" && it.name != "schedules.json" } ?: emptyList()
        debugLogger.debug("Found backup files", "count" to files.size)

        files.forEach { file ->
            try {
                val backup = gson.fromJson(file.readText(), WorldBackup::class.java)
                backups[backup.id] = backup
            } catch (e: Exception) {
                logger.warning("[BackupManager] Failed to load backup file ${file.name}: ${e.message}")
            }
        }

        debugLogger.debugMethodExit("loadAllBackups", backups.size)
    }

    /**
     * Save all schedules to disk.
     */
    private fun saveSchedules() {
        debugLogger.debugMethodEntry("saveSchedules")
        try {
            schedulesFile.writeText(gson.toJson(schedules.values.toList()))
            debugLogger.debug("Schedules saved", "count" to schedules.size)
        } catch (e: Exception) {
            logger.severe("[BackupManager] Failed to save schedules: ${e.message}")
        }
        debugLogger.debugMethodExit("saveSchedules")
    }

    /**
     * Load all schedules from disk.
     */
    private fun loadSchedules() {
        debugLogger.debugMethodEntry("loadSchedules")
        if (!schedulesFile.exists()) {
            debugLogger.debug("No schedules file found")
            debugLogger.debugMethodExit("loadSchedules", 0)
            return
        }

        try {
            val loadedSchedules = gson.fromJson(
                schedulesFile.readText(),
                Array<BackupSchedule>::class.java
            )
            loadedSchedules.forEach { schedule ->
                schedules[schedule.worldId] = schedule
            }
            debugLogger.debug("Schedules loaded", "count" to schedules.size)
        } catch (e: Exception) {
            logger.warning("[BackupManager] Failed to load schedules: ${e.message}")
        }
        debugLogger.debugMethodExit("loadSchedules", schedules.size)
    }

    // ========================
    // Helper Methods
    // ========================

    /**
     * Enforce backup limit by removing oldest backups if over limit.
     */
    private fun enforceBackupLimit(worldId: UUID) {
        val maxBackups = getMaxBackupsPerWorld()
        val worldBackups = getBackupsForWorld(worldId)

        if (worldBackups.size >= maxBackups) {
            // Remove oldest backups to make room
            val backupsToRemove = worldBackups.drop(maxBackups - 1)
            backupsToRemove.forEach { backup ->
                debugLogger.debug("Removing old backup to enforce limit",
                    "backupId" to backup.id,
                    "worldId" to worldId
                )
                deleteBackup(backup)
            }
        }
    }

    /**
     * Delete backups when a world is deleted.
     */
    fun deleteBackupsForWorld(worldId: UUID): CompletableFuture<Unit> {
        debugLogger.debugMethodEntry("deleteBackupsForWorld", "worldId" to worldId)
        val future = CompletableFuture<Unit>()

        val worldBackups = getBackupsForWorld(worldId)
        if (worldBackups.isEmpty()) {
            future.complete(Unit)
            return future
        }

        val deleteFutures = worldBackups.map { deleteBackup(it) }
        CompletableFuture.allOf(*deleteFutures.toTypedArray()).thenAccept {
            // Also remove schedule
            schedules.remove(worldId)
            saveSchedules()
            future.complete(Unit)
        }

        debugLogger.debugMethodExit("deleteBackupsForWorld")
        return future
    }

    /**
     * Copy a directory recursively.
     */
    private fun copyDirectory(source: File, destination: File) {
        if (source.isDirectory) {
            destination.mkdirs()
            source.listFiles()?.forEach { file ->
                // Skip session.lock file as it may be locked
                if (file.name != "session.lock") {
                    copyDirectory(file, File(destination, file.name))
                }
            }
        } else {
            source.copyTo(destination, overwrite = true)
        }
    }

    /**
     * Delete a directory recursively.
     */
    private fun deleteDirectory(directory: File) {
        if (directory.isDirectory) {
            directory.listFiles()?.forEach { deleteDirectory(it) }
        }
        directory.delete()
    }

    /**
     * Calculate the total size of a directory.
     */
    private fun calculateDirectorySize(directory: File): Long {
        var size = 0L
        if (directory.isDirectory) {
            directory.listFiles()?.forEach { file ->
                size += calculateDirectorySize(file)
            }
        } else {
            size = directory.length()
        }
        return size
    }
}
