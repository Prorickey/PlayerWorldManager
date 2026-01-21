package tech.bedson.playerworldmanager.listeners

import io.papermc.paper.event.player.AsyncChatEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.managers.ChatManager
import tech.bedson.playerworldmanager.managers.WorldManager

/**
 * Handles chat message routing based on player chat modes.
 *
 * Intercepts AsyncChatEvent and modifies the viewer set based on:
 * - Sender's chat mode
 * - Receiver's chat mode
 * - Whether sender and receiver are in the same world
 *
 * Also handles loading/saving chat settings on player join/quit.
 */
class ChatListener(
    private val plugin: JavaPlugin,
    private val chatManager: ChatManager,
    private val worldManager: WorldManager
) : Listener {

    /**
     * Intercept chat messages and route based on chat modes.
     *
     * This event is async-safe, so we can safely modify the viewers set.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onAsyncChat(event: AsyncChatEvent) {
        val sender = event.player
        val senderMode = chatManager.getChatMode(sender.uniqueId)

        plugin.logger.info("[ChatListener] AsyncChat: Player '${sender.name}' sending message from world '${sender.world.name}' with mode '$senderMode'")

        // Get all online players
        val allPlayers = plugin.server.onlinePlayers.toSet()
        plugin.logger.info("[ChatListener] AsyncChat: Total online players: ${allPlayers.size}")

        // Filter viewers based on chat mode logic
        val viewers = allPlayers.filter { receiver ->
            val shouldReceive = chatManager.shouldReceiveMessage(sender, receiver)
            val receiverMode = chatManager.getChatMode(receiver.uniqueId)
            plugin.logger.info("[ChatListener] AsyncChat: Check receiver '${receiver.name}' (world: '${receiver.world.name}', mode: '$receiverMode') - shouldReceive: $shouldReceive")
            shouldReceive
        }.toMutableSet()

        plugin.logger.info("[ChatListener] AsyncChat: Message will be sent to ${viewers.size} players: ${viewers.joinToString(", ") { it.name }}")

        // Update the event's viewer set
        event.viewers().clear()
        event.viewers().addAll(viewers)

        // Add prefix to the message based on sender's mode
        val prefix = chatManager.getChatPrefix(senderMode)

        // Modify the renderer to add the prefix
        val originalRenderer = event.renderer()
        event.renderer { source, sourceDisplayName, message, viewer ->
            // Call the original renderer to get the base message format
            val originalMessage = originalRenderer.render(source, sourceDisplayName, message, viewer)

            // Prepend the chat mode prefix
            prefix.append(originalMessage)
        }

        plugin.logger.info("[ChatListener] AsyncChat: Message processing complete for '${sender.name}'")
    }

    /**
     * Load chat settings when a player joins.
     */
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        plugin.logger.info("[ChatListener] PlayerJoin: Player '${player.name}' (${player.uniqueId}) joined, loading chat mode")
        chatManager.loadPlayerChatMode(player)
        val loadedMode = chatManager.getChatMode(player.uniqueId)
        plugin.logger.info("[ChatListener] PlayerJoin: Chat mode loaded for '${player.name}': $loadedMode")
    }

    /**
     * Save chat settings when a player quits.
     */
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val currentMode = chatManager.getChatMode(player.uniqueId)
        plugin.logger.info("[ChatListener] PlayerQuit: Player '${player.name}' (${player.uniqueId}) quit, saving chat mode: $currentMode")
        chatManager.savePlayerChatMode(player)
        chatManager.clearCache(player.uniqueId)
        plugin.logger.info("[ChatListener] PlayerQuit: Chat mode saved and cache cleared for '${player.name}'")
    }
}
