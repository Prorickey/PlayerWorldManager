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
    val invitedPlayers: MutableSet<UUID> = mutableSetOf(),  // Invited player UUIDs (legacy, kept for compatibility)
    val playerRoles: MutableMap<UUID, WorldRole> = mutableMapOf(),  // Player roles (MANAGER, MEMBER, or VISITOR)
    var spawnLocation: SimpleLocation? = null,  // Custom spawn point
    var defaultGameMode: GameMode = GameMode.SURVIVAL,
    var timeLock: TimeLock = TimeLock.CYCLE,    // DAY, NIGHT, CYCLE
    var weatherLock: WeatherLock = WeatherLock.CYCLE,  // CLEAR, RAIN, CYCLE
    var isPublic: Boolean = false,         // Whether anyone can join the world
    var publicJoinRole: WorldRole = WorldRole.VISITOR,  // Role given to players who join via public access
    private var _worldBorder: WorldBorderSettings? = null  // World border settings (nullable for Gson compatibility)
) {
    // Public accessor that ensures a non-null WorldBorderSettings
    var worldBorder: WorldBorderSettings
        get() = _worldBorder ?: WorldBorderSettings.default().also { _worldBorder = it }
        set(value) { _worldBorder = value }

    /**
     * Get the role of a player in this world.
     * Returns OWNER for the world owner, the stored role for other players,
     * the publicJoinRole if the world is public, or null if the player has no access.
     */
    fun getPlayerRole(playerUuid: UUID): WorldRole? {
        return when {
            playerUuid == ownerUuid -> WorldRole.OWNER
            playerRoles.containsKey(playerUuid) -> playerRoles[playerUuid]
            invitedPlayers.contains(playerUuid) -> WorldRole.MEMBER  // Legacy fallback
            isPublic -> publicJoinRole  // Public worlds allow anyone with the configured role
            else -> null
        }
    }

    /**
     * Get the explicit role of a player (ignores public access).
     * Use this to check if a player has been specifically assigned a role.
     */
    fun getExplicitPlayerRole(playerUuid: UUID): WorldRole? {
        return when {
            playerUuid == ownerUuid -> WorldRole.OWNER
            playerRoles.containsKey(playerUuid) -> playerRoles[playerUuid]
            invitedPlayers.contains(playerUuid) -> WorldRole.MEMBER  // Legacy fallback
            else -> null
        }
    }

    /**
     * Check if a player has access to this world.
     */
    fun hasAccess(playerUuid: UUID): Boolean {
        return getPlayerRole(playerUuid) != null
    }

    /**
     * Check if a player has explicit access (not via public).
     */
    fun hasExplicitAccess(playerUuid: UUID): Boolean {
        return getExplicitPlayerRole(playerUuid) != null
    }

    /**
     * Check if a player has at least the specified role.
     */
    fun hasRole(playerUuid: UUID, minRole: WorldRole): Boolean {
        val role = getPlayerRole(playerUuid) ?: return false
        return role.isAtLeast(minRole)
    }

    /**
     * Set a player's role. Cannot be used to set OWNER role.
     */
    fun setPlayerRole(playerUuid: UUID, role: WorldRole) {
        require(role != WorldRole.OWNER) { "Cannot set OWNER role directly. Use transfer ownership instead." }
        require(playerUuid != ownerUuid) { "Cannot change the owner's role." }
        playerRoles[playerUuid] = role
        // Ensure they're also in invitedPlayers for backward compatibility
        invitedPlayers.add(playerUuid)
    }

    /**
     * Remove a player's access to this world.
     */
    fun removePlayer(playerUuid: UUID) {
        require(playerUuid != ownerUuid) { "Cannot remove the owner from their own world." }
        playerRoles.remove(playerUuid)
        invitedPlayers.remove(playerUuid)
    }

    /**
     * Get all players with a specific role.
     */
    fun getPlayersWithRole(role: WorldRole): Set<UUID> {
        return when (role) {
            WorldRole.OWNER -> setOf(ownerUuid)
            else -> playerRoles.filterValues { it == role }.keys
        }
    }

    /**
     * Get all players with access to this world (including owner).
     */
    fun getAllPlayers(): Set<UUID> {
        return setOf(ownerUuid) + playerRoles.keys + invitedPlayers
    }

    /**
     * Migrate legacy invitedPlayers to playerRoles.
     * Call this after loading to ensure all invited players have roles.
     */
    fun migrateInvitedPlayersToRoles() {
        for (playerUuid in invitedPlayers) {
            if (playerUuid != ownerUuid && !playerRoles.containsKey(playerUuid)) {
                playerRoles[playerUuid] = WorldRole.MEMBER
            }
        }
    }

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
            playerRoles: MutableMap<UUID, WorldRole> = mutableMapOf(),
            spawnLocation: SimpleLocation? = null,
            defaultGameMode: GameMode = GameMode.SURVIVAL,
            timeLock: TimeLock = TimeLock.CYCLE,
            weatherLock: WeatherLock = WeatherLock.CYCLE,
            isPublic: Boolean = false,
            publicJoinRole: WorldRole = WorldRole.VISITOR,
            worldBorder: WorldBorderSettings = WorldBorderSettings.default()
        ): PlayerWorld {
            val debugLogger = DebugLogger(plugin, "PlayerWorld")
            debugLogger.debug("Creating PlayerWorld",
                "id" to id,
                "name" to name,
                "owner" to ownerName,
                "ownerUuid" to ownerUuid,
                "type" to worldType,
                "seed" to seed,
                "isPublic" to isPublic,
                "publicJoinRole" to publicJoinRole
            )
            return PlayerWorld(
                id, name, ownerUuid, ownerName, worldType, seed, createdAt,
                isEnabled, invitedPlayers, playerRoles, spawnLocation, defaultGameMode,
                timeLock, weatherLock, isPublic, publicJoinRole, _worldBorder = worldBorder
            )
        }
    }

    /**
     * Returns a debug-friendly string representation of this world.
     */
    fun toDebugString(): String {
        return "PlayerWorld(id=$id, name=$name, owner=$ownerName/$ownerUuid, " +
                "type=$worldType, seed=$seed, enabled=$isEnabled, " +
                "members=${playerRoles.size}, gameMode=$defaultGameMode, " +
                "timeLock=$timeLock, weatherLock=$weatherLock, " +
                "isPublic=$isPublic, publicJoinRole=$publicJoinRole, " +
                "border=${worldBorder.size})"
    }

    /**
     * Returns a compact debug string for logging.
     */
    fun toCompactDebugString(): String {
        return "PlayerWorld[$name, owner=$ownerName, type=$worldType, public=$isPublic]"
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
