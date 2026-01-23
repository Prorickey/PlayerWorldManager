package tech.bedson.playerworldmanager.models

import java.util.UUID

/**
 * Data class representing statistics for a player world.
 * Tracks various metrics like blocks placed/broken, mob kills, player kills, and deaths.
 */
data class WorldStatistics(
    val worldId: UUID,                     // The world this statistics belongs to
    var blocksPlaced: Long = 0,            // Total blocks placed in this world
    var blocksBroken: Long = 0,            // Total blocks broken in this world
    var mobsKilled: Long = 0,              // Total mobs killed in this world
    var playerKills: Long = 0,             // Total player kills in this world
    var playerDeaths: Long = 0,            // Total player deaths in this world
    var animalsKilled: Long = 0,           // Total passive animals killed
    var itemsCrafted: Long = 0,            // Total items crafted
    var timePlayed: Long = 0,              // Total time played in this world (milliseconds)
    val playerStats: MutableMap<UUID, PlayerStatistics> = mutableMapOf()  // Per-player statistics
) {
    /**
     * Get or create player statistics for a specific player.
     */
    fun getOrCreatePlayerStats(playerUuid: UUID): PlayerStatistics {
        return playerStats.getOrPut(playerUuid) { PlayerStatistics(playerUuid) }
    }

    /**
     * Returns a debug-friendly string representation.
     */
    fun toDebugString(): String {
        return "WorldStatistics(worldId=$worldId, blocksPlaced=$blocksPlaced, blocksBroken=$blocksBroken, " +
                "mobsKilled=$mobsKilled, playerKills=$playerKills, playerDeaths=$playerDeaths, " +
                "animalsKilled=$animalsKilled, itemsCrafted=$itemsCrafted, timePlayed=$timePlayed, " +
                "playerStatsCount=${playerStats.size})"
    }
}

/**
 * Per-player statistics within a world.
 */
data class PlayerStatistics(
    val playerUuid: UUID,
    var blocksPlaced: Long = 0,
    var blocksBroken: Long = 0,
    var mobsKilled: Long = 0,
    var playerKills: Long = 0,
    var deaths: Long = 0,
    var animalsKilled: Long = 0,
    var itemsCrafted: Long = 0,
    var timePlayed: Long = 0,              // Time played in milliseconds
    var lastJoinTime: Long = 0             // Used to track session time
) {
    /**
     * Returns a debug-friendly string representation.
     */
    fun toDebugString(): String {
        return "PlayerStatistics(uuid=$playerUuid, blocksPlaced=$blocksPlaced, blocksBroken=$blocksBroken, " +
                "mobsKilled=$mobsKilled, playerKills=$playerKills, deaths=$deaths, animalsKilled=$animalsKilled, " +
                "itemsCrafted=$itemsCrafted, timePlayed=$timePlayed)"
    }
}
