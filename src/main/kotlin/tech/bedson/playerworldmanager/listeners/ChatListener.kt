package tech.bedson.playerworldmanager.listeners

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.managers.ChatManager
import tech.bedson.playerworldmanager.managers.WorldManager
import tech.bedson.playerworldmanager.models.ChatMode
import tech.bedson.playerworldmanager.utils.DebugLogger

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

    private val debugLogger = DebugLogger(plugin, "ChatListener")

    /**
     * Intercept chat messages and route based on chat modes.
     *
     * This event is async-safe, so we can safely modify the viewers set.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onAsyncChat(event: AsyncChatEvent) {
        val sender = event.player
        val senderMode = chatManager.getChatMode(sender.uniqueId)

        debugLogger.debugMethodEntry("onAsyncChat",
            "sender" to sender.name,
            "senderUuid" to sender.uniqueId,
            "senderWorld" to sender.world.name,
            "senderMode" to senderMode,
            "isCancelled" to event.isCancelled
        )

        plugin.logger.info("[ChatListener] AsyncChat: Player '${sender.name}' sending message from world '${sender.world.name}' with mode '$senderMode'")

        // Get all online players
        val allPlayers = plugin.server.onlinePlayers.toSet()
        debugLogger.debug("Retrieved online players",
            "totalPlayers" to allPlayers.size,
            "playerNames" to allPlayers.joinToString(", ") { it.name }
        )
        plugin.logger.info("[ChatListener] AsyncChat: Total online players: ${allPlayers.size}")

        // Filter viewers based on chat mode logic
        val viewers = allPlayers.filter { receiver ->
            val shouldReceive = chatManager.shouldReceiveMessage(sender, receiver)
            val receiverMode = chatManager.getChatMode(receiver.uniqueId)
            debugLogger.debug("Checking receiver eligibility",
                "receiver" to receiver.name,
                "receiverUuid" to receiver.uniqueId,
                "receiverWorld" to receiver.world.name,
                "receiverMode" to receiverMode,
                "sameWorld" to (sender.world.name == receiver.world.name),
                "shouldReceive" to shouldReceive
            )
            plugin.logger.info("[ChatListener] AsyncChat: Check receiver '${receiver.name}' (world: '${receiver.world.name}', mode: '$receiverMode') - shouldReceive: $shouldReceive")
            shouldReceive
        }.toMutableSet()

        debugLogger.debug("Filtered viewers calculated",
            "originalCount" to allPlayers.size,
            "filteredCount" to viewers.size,
            "excludedCount" to (allPlayers.size - viewers.size)
        )
        plugin.logger.info("[ChatListener] AsyncChat: Message will be sent to ${viewers.size} players: ${viewers.joinToString(", ") { it.name }}")

        // Update the event's viewer set
        val originalViewersCount = event.viewers().size
        event.viewers().clear()
        event.viewers().addAll(viewers)
        debugLogger.debug("Updated event viewers set",
            "originalViewersCount" to originalViewersCount,
            "newViewersCount" to event.viewers().size
        )

        // Custom renderer with new chat format: [G|W] Username » message
        debugLogger.debug("Setting custom chat renderer",
            "senderMode" to senderMode,
            "prefixType" to when (senderMode) {
                ChatMode.GLOBAL -> "[G]"
                ChatMode.WORLD -> "[W]"
            }
        )
        event.renderer { source, sourceDisplayName, message, viewer ->
            val prefix = when (senderMode) {
                ChatMode.GLOBAL -> Component.text("[G] ", NamedTextColor.GRAY)
                ChatMode.WORLD -> Component.text("[W] ", NamedTextColor.GRAY)
            }

            val usernameColor = when (senderMode) {
                ChatMode.GLOBAL -> NamedTextColor.GREEN
                ChatMode.WORLD -> NamedTextColor.AQUA
            }

            prefix
                .append(Component.text(source.name, usernameColor))
                .append(Component.text(" \u00BB ", NamedTextColor.DARK_GRAY))
                .append(message.color(NamedTextColor.WHITE))
        }

        plugin.logger.info("[ChatListener] AsyncChat: Message processing complete for '${sender.name}'")
        debugLogger.debugMethodExit("onAsyncChat", "viewerCount" to viewers.size)
    }

    /**
     * Load chat settings when a player joins.
     */
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        debugLogger.debugMethodEntry("onPlayerJoin",
            "player" to player.name,
            "playerUuid" to player.uniqueId,
            "world" to player.world.name,
            "hasPlayedBefore" to player.hasPlayedBefore()
        )

        plugin.logger.info("[ChatListener] PlayerJoin: Player '${player.name}' (${player.uniqueId}) joined, loading chat mode")

        debugLogger.debug("Loading chat mode from storage",
            "player" to player.name,
            "playerUuid" to player.uniqueId
        )
        chatManager.loadPlayerChatMode(player)

        val loadedMode = chatManager.getChatMode(player.uniqueId)
        debugLogger.debug("Chat mode loaded",
            "player" to player.name,
            "loadedMode" to loadedMode
        )
        plugin.logger.info("[ChatListener] PlayerJoin: Chat mode loaded for '${player.name}': $loadedMode")

        debugLogger.debugMethodExit("onPlayerJoin", "loadedMode" to loadedMode)
    }

    /**
     * Save chat settings when a player quits.
     */
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val currentMode = chatManager.getChatMode(player.uniqueId)

        debugLogger.debugMethodEntry("onPlayerQuit",
            "player" to player.name,
            "playerUuid" to player.uniqueId,
            "world" to player.world.name,
            "currentMode" to currentMode
        )

        plugin.logger.info("[ChatListener] PlayerQuit: Player '${player.name}' (${player.uniqueId}) quit, saving chat mode: $currentMode")

        debugLogger.debug("Saving chat mode to storage",
            "player" to player.name,
            "playerUuid" to player.uniqueId,
            "mode" to currentMode
        )
        chatManager.savePlayerChatMode(player)

        debugLogger.debug("Clearing chat mode cache",
            "player" to player.name,
            "playerUuid" to player.uniqueId
        )
        chatManager.clearCache(player.uniqueId)

        plugin.logger.info("[ChatListener] PlayerQuit: Chat mode saved and cache cleared for '${player.name}'")

        debugLogger.debugMethodExit("onPlayerQuit", "savedMode" to currentMode)
    }
}
