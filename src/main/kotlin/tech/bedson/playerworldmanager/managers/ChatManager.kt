package tech.bedson.playerworldmanager.managers

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.models.ChatMode
import tech.bedson.playerworldmanager.models.ChatSettings
import java.util.UUID
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
     * - GLOBAL sender: Visible to GLOBAL and BOTH receivers
     * - WORLD sender: Visible to WORLD and BOTH receivers in the same world
     * - BOTH sender: Visible to everyone (GLOBAL part) + same-world WORLD receivers
     */
    fun shouldReceiveMessage(sender: Player, receiver: Player): Boolean {
        val senderMode = getChatMode(sender.uniqueId)
        val receiverMode = getChatMode(receiver.uniqueId)
        val sameWorld = sender.world == receiver.world

        val result = when (senderMode) {
            ChatMode.GLOBAL -> {
                // GLOBAL messages go to GLOBAL and BOTH receivers
                receiverMode == ChatMode.GLOBAL || receiverMode == ChatMode.BOTH
            }
            ChatMode.WORLD -> {
                // WORLD messages only go to same-world WORLD and BOTH receivers
                sameWorld && (receiverMode == ChatMode.WORLD || receiverMode == ChatMode.BOTH)
            }
            ChatMode.BOTH -> {
                // BOTH sends to everyone in global mode + same-world in world mode
                when (receiverMode) {
                    ChatMode.GLOBAL -> true  // Global part
                    ChatMode.WORLD -> sameWorld  // World part
                    ChatMode.BOTH -> true  // Receives both global and world
                }
            }
        }

        plugin.logger.info("[ChatManager] shouldReceiveMessage: Sender '${sender.name}' ($senderMode) -> Receiver '${receiver.name}' ($receiverMode), Same world: $sameWorld, Result: $result")
        return result
    }

    /**
     * Get the chat prefix component for a specific mode.
     */
    fun getChatPrefix(mode: ChatMode): Component {
        return when (mode) {
            ChatMode.GLOBAL -> Component.text("[G] ", NamedTextColor.GRAY)
            ChatMode.WORLD -> Component.text("[W] ", NamedTextColor.GREEN)
            ChatMode.BOTH -> Component.text("[G+W] ", NamedTextColor.YELLOW)
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
