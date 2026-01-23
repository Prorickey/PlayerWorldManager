package tech.bedson.playerworldmanager.listeners

import org.bukkit.entity.Animals
import org.bukkit.entity.Monster
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.managers.StatsManager
import tech.bedson.playerworldmanager.managers.WorldManager
import tech.bedson.playerworldmanager.utils.DebugLogger

/**
 * Listens for various game events and records them to world statistics.
 *
 * Tracks:
 * - Blocks placed and broken
 * - Mobs killed (hostile and passive)
 * - Player kills and deaths
 * - Items crafted
 * - Time played per world
 */
class StatsListener(
    private val plugin: JavaPlugin,
    private val statsManager: StatsManager,
    private val worldManager: WorldManager
) : Listener {

    private val debugLogger = DebugLogger(plugin, "StatsListener")

    /**
     * Track blocks placed.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player
        val worldId = statsManager.getWorldIdFromBukkitWorld(player.world) ?: return

        debugLogger.debug("Block placed",
            "player" to player.name,
            "worldId" to worldId,
            "blockType" to event.block.type
        )

        statsManager.recordBlockPlaced(worldId, player.uniqueId)
    }

    /**
     * Track blocks broken.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val worldId = statsManager.getWorldIdFromBukkitWorld(player.world) ?: return

        debugLogger.debug("Block broken",
            "player" to player.name,
            "worldId" to worldId,
            "blockType" to event.block.type
        )

        statsManager.recordBlockBroken(worldId, player.uniqueId)
    }

    /**
     * Track mob/animal kills.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        val entity = event.entity
        val killer = entity.killer ?: return
        val worldId = statsManager.getWorldIdFromBukkitWorld(entity.world) ?: return

        // Don't count player deaths here (handled by PlayerDeathEvent)
        if (entity is Player) return

        debugLogger.debug("Entity killed",
            "killer" to killer.name,
            "entity" to entity.type,
            "worldId" to worldId
        )

        when (entity) {
            is Monster -> statsManager.recordMobKilled(worldId, killer.uniqueId)
            is Animals -> statsManager.recordAnimalKilled(worldId, killer.uniqueId)
            else -> statsManager.recordMobKilled(worldId, killer.uniqueId)
        }
    }

    /**
     * Track player deaths and PvP kills.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val worldId = statsManager.getWorldIdFromBukkitWorld(player.world) ?: return

        debugLogger.debug("Player died",
            "player" to player.name,
            "worldId" to worldId,
            "killer" to player.killer?.name
        )

        // Record the death
        statsManager.recordPlayerDeath(worldId, player.uniqueId)

        // If killed by another player, record the kill
        val killer = player.killer
        if (killer != null && killer != player) {
            debugLogger.debug("PvP kill recorded",
                "killer" to killer.name,
                "victim" to player.name,
                "worldId" to worldId
            )
            statsManager.recordPlayerKill(worldId, killer.uniqueId)
        }
    }

    /**
     * Track items crafted.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCraftItem(event: CraftItemEvent) {
        val player = event.whoClicked as? Player ?: return
        val worldId = statsManager.getWorldIdFromBukkitWorld(player.world) ?: return

        // Calculate actual amount crafted (considering shift-click)
        val amount = if (event.isShiftClick) {
            // Estimate based on result amount
            event.recipe.result.amount
        } else {
            event.recipe.result.amount
        }

        debugLogger.debug("Item crafted",
            "player" to player.name,
            "worldId" to worldId,
            "item" to event.recipe.result.type,
            "amount" to amount
        )

        statsManager.recordItemCrafted(worldId, player.uniqueId, amount)
    }

    /**
     * Start session time tracking when a player joins.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val worldId = statsManager.getWorldIdFromBukkitWorld(player.world) ?: return

        debugLogger.debug("Player join - starting session tracking",
            "player" to player.name,
            "worldId" to worldId
        )

        statsManager.startPlayerSession(worldId, player.uniqueId)
    }

    /**
     * End session time tracking when a player quits.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val worldId = statsManager.getWorldIdFromBukkitWorld(player.world) ?: return

        debugLogger.debug("Player quit - ending session tracking",
            "player" to player.name,
            "worldId" to worldId
        )

        statsManager.endPlayerSession(worldId, player.uniqueId)

        // Save stats when player quits
        statsManager.saveWorldStats(worldId)
    }

    /**
     * Handle world changes for session time tracking.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        val player = event.player

        // End session in the previous world (if it's a plugin world)
        val previousWorldId = statsManager.getWorldIdFromBukkitWorld(event.from)
        if (previousWorldId != null) {
            debugLogger.debug("World change - ending session in previous world",
                "player" to player.name,
                "previousWorldId" to previousWorldId
            )
            statsManager.endPlayerSession(previousWorldId, player.uniqueId)
            statsManager.saveWorldStats(previousWorldId)
        }

        // Start session in the new world (if it's a plugin world)
        val newWorldId = statsManager.getWorldIdFromBukkitWorld(player.world)
        if (newWorldId != null) {
            debugLogger.debug("World change - starting session in new world",
                "player" to player.name,
                "newWorldId" to newWorldId
            )
            statsManager.startPlayerSession(newWorldId, player.uniqueId)
        }
    }
}
