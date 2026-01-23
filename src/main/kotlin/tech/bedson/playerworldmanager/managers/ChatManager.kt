package tech.bedson.playerworldmanager.managers

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.models.ChatMode
import tech.bedson.playerworldmanager.utils.DebugLogger
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages chat mode preferences for players.
 *
 * Handles:
 * - In-memory cache of player chat modes
 * - Loading and saving chat settings
 * - Message routing logic based on chat modes
 * - Chat prefix generation
 */
class ChatManager(
    private val plugin: JavaPlugin,
    private val dataManager: DataManager
) {
    private val debugLogger = DebugLogger(plugin, "ChatManager")

    // In-memory cache of player chat modes for fast access
    private val chatModes = ConcurrentHashMap<UUID, ChatMode>()

    /**
     * Get a player's current chat mode.
     * Defaults to GLOBAL if not set.
     */
    fun getChatMode(playerUuid: UUID): ChatMode {
        debugLogger.debugMethodEntry("getChatMode", "playerUuid" to playerUuid)
        val mode = chatModes.getOrDefault(playerUuid, ChatMode.GLOBAL)
        debugLogger.debug("Retrieved chat mode", "mode" to mode, "wasInCache" to chatModes.containsKey(playerUuid))
        debugLogger.debugMethodExit("getChatMode", mode)
        return mode
    }

    /**
     * Set a player's chat mode and persist to disk.
     */
    fun setChatMode(playerUuid: UUID, mode: ChatMode) {
        debugLogger.debugMethodEntry("setChatMode", "playerUuid" to playerUuid, "mode" to mode)
        plugin.logger.info("[ChatManager] setChatMode: Setting chat mode to $mode for player UUID: $playerUuid")

        val previousMode = chatModes[playerUuid]
        chatModes[playerUuid] = mode
        debugLogger.debug("Updated cache", "previousMode" to previousMode, "newMode" to mode)

        // Update player data and save
        val playerData = dataManager.loadPlayerData(playerUuid)
        if (playerData != null) {
            playerData.chatSettings.chatMode = mode
            dataManager.savePlayerData(playerData)
            plugin.logger.info("[ChatManager] setChatMode: Successfully set chat mode to $mode for '${playerData.username}'")
            debugLogger.debug("Persisted chat mode to disk", "username" to playerData.username)
            debugLogger.debugMethodExit("setChatMode", "success")
        } else {
            plugin.logger.warning("[ChatManager] setChatMode: Could not load player data for UUID: $playerUuid")
            debugLogger.debug("Failed to load player data for persistence")
            debugLogger.debugMethodExit("setChatMode", "partial - data not persisted")
        }
    }

    /**
     * Load a player's chat mode from disk into the cache.
     * Called when a player joins the server.
     */
    fun loadPlayerChatMode(player: Player) {
        debugLogger.debugMethodEntry("loadPlayerChatMode", "playerName" to player.name, "playerUuid" to player.uniqueId)
        val playerData = dataManager.getOrCreatePlayerData(player.uniqueId, player.name)
        chatModes[player.uniqueId] = playerData.chatSettings.chatMode
        plugin.logger.info("Loaded chat mode ${playerData.chatSettings.chatMode} for ${player.name}")
        debugLogger.debug("Loaded chat mode into cache", "mode" to playerData.chatSettings.chatMode, "cacheSize" to chatModes.size)
        debugLogger.debugMethodExit("loadPlayerChatMode", playerData.chatSettings.chatMode)
    }

    /**
     * Save a player's chat mode to disk.
     * Called when a player quits the server.
     */
    fun savePlayerChatMode(player: Player) {
        debugLogger.debugMethodEntry("savePlayerChatMode", "playerName" to player.name, "playerUuid" to player.uniqueId)
        val mode = chatModes[player.uniqueId]
        if (mode == null) {
            debugLogger.debug("No cached mode to save, skipping")
            debugLogger.debugMethodExit("savePlayerChatMode", "skipped - no cache")
            return
        }

        val playerData = dataManager.loadPlayerData(player.uniqueId)
        if (playerData != null) {
            playerData.chatSettings.chatMode = mode
            dataManager.savePlayerData(playerData)
            plugin.logger.info("Saved chat mode $mode for ${player.name}")
            debugLogger.debug("Saved chat mode to disk", "mode" to mode)
            debugLogger.debugMethodExit("savePlayerChatMode", "success")
        } else {
            debugLogger.debug("Failed to load player data for saving")
            debugLogger.debugMethodExit("savePlayerChatMode", "failed - no player data")
        }
    }

    /**
     * Determine if a receiver should see a message from a sender based on chat modes and worlds.
     *
     * Logic:
     * - GLOBAL sender: Message goes to ALL players (everyone can see global)
     * - WORLD sender: Message goes only to players in the same world
     */
    fun shouldReceiveMessage(sender: Player, receiver: Player): Boolean {
        debugLogger.debugMethodEntry("shouldReceiveMessage",
            "senderName" to sender.name,
            "receiverName" to receiver.name,
            "senderWorld" to sender.world.name,
            "receiverWorld" to receiver.world.name
        )
        val senderMode = getChatMode(sender.uniqueId)
        val sameWorld = sender.world == receiver.world
        debugLogger.debug("Evaluating message routing", "senderMode" to senderMode, "sameWorld" to sameWorld)

        val result = when (senderMode) {
            ChatMode.GLOBAL -> {
                // GLOBAL messages go to all players
                debugLogger.debug("GLOBAL mode - message goes to all")
                true
            }
            ChatMode.WORLD -> {
                // WORLD messages only go to players in the same world
                debugLogger.debug("WORLD mode - checking same world", "sameWorld" to sameWorld)
                sameWorld
            }
        }

        plugin.logger.info("[ChatManager] shouldReceiveMessage: Sender '${sender.name}' ($senderMode) -> Receiver '${receiver.name}', Same world: $sameWorld, Result: $result")
        debugLogger.debugMethodExit("shouldReceiveMessage", result)
        return result
    }

    /**
     * Get the chat prefix component for a specific mode.
     */
    fun getChatPrefix(mode: ChatMode): Component {
        debugLogger.debugMethodEntry("getChatPrefix", "mode" to mode)
        val prefix = when (mode) {
            ChatMode.GLOBAL -> Component.text("[G] ", NamedTextColor.GRAY)
            ChatMode.WORLD -> Component.text("[W] ", NamedTextColor.GREEN)
        }
        debugLogger.debugMethodExit("getChatPrefix", mode.name)
        return prefix
    }

    /**
     * Clear cached chat mode for a player.
     * Useful for cleanup when a player leaves.
     */
    fun clearCache(playerUuid: UUID) {
        debugLogger.debugMethodEntry("clearCache", "playerUuid" to playerUuid)
        val removed = chatModes.remove(playerUuid)
        if (removed != null) {
            plugin.logger.info("[ChatManager] clearCache: Cleared chat mode cache for player UUID: $playerUuid (was $removed)")
            debugLogger.debug("Removed cache entry", "previousMode" to removed, "newCacheSize" to chatModes.size)
        } else {
            plugin.logger.info("[ChatManager] clearCache: No cache entry found for player UUID: $playerUuid")
            debugLogger.debug("No cache entry to remove")
        }
        debugLogger.debugMethodExit("clearCache", removed)
    }
}
