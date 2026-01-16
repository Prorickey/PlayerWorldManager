package tech.bedson.playerworldmanager.models

import org.bukkit.GameMode
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
    var weatherLock: WeatherLock = WeatherLock.CYCLE  // CLEAR, RAIN, CYCLE
)

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
