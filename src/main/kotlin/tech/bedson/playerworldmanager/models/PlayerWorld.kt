package tech.bedson.playerworldmanager.models

import org.bukkit.GameMode
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.utils.DebugLogger
import java.util.UUID

/**
 * Data class representing a player-owned world.
 */
data class PlayerWorld(
    val id: UUID,                          // Unique world ID
    val name: String,                      // World name (e.g., "myworld")
    val ownerUuid: UUID,                   // Owner's UUID
    val ownerName: String,                 // Owner's name (for display)
    val worldType: WorldType,              // NORMAL, FLAT, AMPLIFIED, LARGE_BIOMES, VOID
    val seed: Long?,                       // Custom seed or null for random
    val createdAt: Long,                   // Timestamp
    var isEnabled: Boolean = true,         // Can be disabled by admin
    val invitedPlayers: MutableSet<UUID> = mutableSetOf(),  // Invited player UUIDs
    var spawnLocation: SimpleLocation? = null,  // Custom spawn point
    var defaultGameMode: GameMode = GameMode.SURVIVAL,
    var timeLock: TimeLock = TimeLock.CYCLE,    // DAY, NIGHT, CYCLE
    var weatherLock: WeatherLock = WeatherLock.CYCLE,  // CLEAR, RAIN, CYCLE
    private var _worldBorder: WorldBorderSettings? = null  // World border settings (nullable for Gson compatibility)
) {
    // Public accessor that ensures a non-null WorldBorderSettings
    var worldBorder: WorldBorderSettings
        get() = _worldBorder ?: WorldBorderSettings.default().also { _worldBorder = it }
        set(value) { _worldBorder = value }

    companion object {
        /**
         * Create a PlayerWorld with debug logging.
         */
        fun createWithLogging(
            plugin: JavaPlugin,
            id: UUID,
            name: String,
            ownerUuid: UUID,
            ownerName: String,
            worldType: WorldType,
            seed: Long?,
            createdAt: Long,
            isEnabled: Boolean = true,
            invitedPlayers: MutableSet<UUID> = mutableSetOf(),
            spawnLocation: SimpleLocation? = null,
            defaultGameMode: GameMode = GameMode.SURVIVAL,
            timeLock: TimeLock = TimeLock.CYCLE,
            weatherLock: WeatherLock = WeatherLock.CYCLE,
            worldBorder: WorldBorderSettings = WorldBorderSettings.default()
        ): PlayerWorld {
            val debugLogger = DebugLogger(plugin, "PlayerWorld")
            debugLogger.debug("Creating PlayerWorld",
                "id" to id,
                "name" to name,
                "owner" to ownerName,
                "ownerUuid" to ownerUuid,
                "type" to worldType,
                "seed" to seed
            )
            return PlayerWorld(
                id, name, ownerUuid, ownerName, worldType, seed, createdAt,
                isEnabled, invitedPlayers, spawnLocation, defaultGameMode, timeLock, weatherLock, _worldBorder = worldBorder
            )
        }
    }

    /**
     * Returns a debug-friendly string representation of this world.
     */
    fun toDebugString(): String {
        return "PlayerWorld(id=$id, name=$name, owner=$ownerName/$ownerUuid, " +
                "type=$worldType, seed=$seed, enabled=$isEnabled, " +
                "invites=${invitedPlayers.size}, gameMode=$defaultGameMode, " +
                "timeLock=$timeLock, weatherLock=$weatherLock, " +
                "border=${worldBorder.size})"
    }

    /**
     * Returns a compact debug string for logging.
     */
    fun toCompactDebugString(): String {
        return "PlayerWorld[$name, owner=$ownerName, type=$worldType]"
    }
}

/**
 * Enum representing world generation types.
 */
enum class WorldType {
    NORMAL,
    FLAT,
    AMPLIFIED,
    LARGE_BIOMES,
    VOID
}

/**
 * Enum representing time lock states for a world.
 */
enum class TimeLock {
    DAY,
    NIGHT,
    CYCLE
}

/**
 * Enum representing weather lock states for a world.
 */
enum class WeatherLock {
    CLEAR,
    RAIN,
    CYCLE
}

/**
 * Data class representing world border settings.
 * Mirrors Minecraft's vanilla world border features.
 */
data class WorldBorderSettings(
    var size: Double = 60000000.0,           // Border diameter in blocks (default: Minecraft default)
    var centerX: Double = 0.0,               // Center X coordinate
    var centerZ: Double = 0.0,               // Center Z coordinate
    var damageAmount: Double = 0.2,          // Damage per block per second outside buffer
    var damageBuffer: Double = 5.0,          // Distance past border before damage starts
    var warningDistance: Int = 5,            // Warning distance in blocks
    var warningTime: Int = 15                // Warning time in seconds
) {
    companion object {
        /** Default world border settings */
        fun default() = WorldBorderSettings()
    }
}
