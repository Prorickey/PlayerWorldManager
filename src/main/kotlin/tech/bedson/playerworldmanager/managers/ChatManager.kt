package tech.bedson.playerworldmanager.managers

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.models.ChatMode
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
    // In-memory cache of player chat modes for fast access
    private val chatModes = ConcurrentHashMap<UUID, ChatMode>()

    /**
     * Get a player's current chat mode.
     * Defaults to GLOBAL if not set.
     */
    fun getChatMode(playerUuid: UUID): ChatMode {
        return chatModes.getOrDefault(playerUuid, ChatMode.GLOBAL)
    }

    /**
     * Set a player's chat mode and persist to disk.
     */
    fun setChatMode(playerUuid: UUID, mode: ChatMode) {
        plugin.logger.info("[ChatManager] setChatMode: Setting chat mode to $mode for player UUID: $playerUuid")
        chatModes[playerUuid] = mode

        // Update player data and save
        val playerData = dataManager.loadPlayerData(playerUuid)
        if (playerData != null) {
            playerData.chatSettings.chatMode = mode
            dataManager.savePlayerData(playerData)
            plugin.logger.info("[ChatManager] setChatMode: Successfully set chat mode to $mode for '${playerData.username}'")
        } else {
            plugin.logger.warning("[ChatManager] setChatMode: Could not load player data for UUID: $playerUuid")
        }
    }

    /**
     * Load a player's chat mode from disk into the cache.
     * Called when a player joins the server.
     */
    fun loadPlayerChatMode(player: Player) {
        val playerData = dataManager.getOrCreatePlayerData(player.uniqueId, player.name)
        chatModes[player.uniqueId] = playerData.chatSettings.chatMode
        plugin.logger.info("Loaded chat mode ${playerData.chatSettings.chatMode} for ${player.name}")
    }

    /**
     * Save a player's chat mode to disk.
     * Called when a player quits the server.
     */
    fun savePlayerChatMode(player: Player) {
        val mode = chatModes[player.uniqueId] ?: return

        val playerData = dataManager.loadPlayerData(player.uniqueId)
        if (playerData != null) {
            playerData.chatSettings.chatMode = mode
            dataManager.savePlayerData(playerData)
            plugin.logger.info("Saved chat mode $mode for ${player.name}")
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
        val senderMode = getChatMode(sender.uniqueId)
        val sameWorld = sender.world == receiver.world

        val result = when (senderMode) {
            ChatMode.GLOBAL -> {
                // GLOBAL messages go to all players
                true
            }
            ChatMode.WORLD -> {
                // WORLD messages only go to players in the same world
                sameWorld
            }
        }

        plugin.logger.info("[ChatManager] shouldReceiveMessage: Sender '${sender.name}' ($senderMode) -> Receiver '${receiver.name}', Same world: $sameWorld, Result: $result")
        return result
    }

    /**
     * Get the chat prefix component for a specific mode.
     */
    fun getChatPrefix(mode: ChatMode): Component {
        return when (mode) {
            ChatMode.GLOBAL -> Component.text("[G] ", NamedTextColor.GRAY)
            ChatMode.WORLD -> Component.text("[W] ", NamedTextColor.GREEN)
        }
    }

    /**
     * Clear cached chat mode for a player.
     * Useful for cleanup when a player leaves.
     */
    fun clearCache(playerUuid: UUID) {
        val removed = chatModes.remove(playerUuid)
        if (removed != null) {
            plugin.logger.info("[ChatManager] clearCache: Cleared chat mode cache for player UUID: $playerUuid (was $removed)")
        } else {
            plugin.logger.info("[ChatManager] clearCache: No cache entry found for player UUID: $playerUuid")
        }
    }
}
