package tech.bedson.playerworldmanager.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.managers.ChatManager
import tech.bedson.playerworldmanager.models.ChatMode
import java.util.concurrent.CompletableFuture

/**
 * Brigadier command builder for /chat command.
 */
@Suppress("UnstableApiUsage")
class ChatCommands(
    private val plugin: JavaPlugin,
    private val chatManager: ChatManager
) {
    fun build(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("chat")
            .then(Commands.argument("mode", StringArgumentType.word())
                .suggests(::suggestChatModes)
                .executes(::handleSetMode)
            )
            .executes(::handleShowMode)
            .build()
    }

    private fun handleShowMode(ctx: CommandContext<CommandSourceStack>): Int {
        plugin.logger.info("[ChatCommands] handleShowMode: Executing chat show mode command")

        val player = ctx.source.sender as? Player ?: run {
            plugin.logger.warning("[ChatCommands] handleShowMode: Non-player attempted to execute command")
            ctx.source.sender.sendMessage(
                Component.text("This command can only be used by players", NamedTextColor.RED)
            )
            return Command.SINGLE_SUCCESS
        }

        plugin.logger.info("[ChatCommands] handleShowMode: Player ${player.name} checking current chat mode")

        val currentMode = chatManager.getChatMode(player.uniqueId)
        plugin.logger.info("[ChatCommands] handleShowMode: Player ${player.name} current mode is $currentMode")

        player.sendMessage(
            Component.text("Your current chat mode is: ", NamedTextColor.YELLOW)
                .append(Component.text(currentMode.name, NamedTextColor.GOLD))
        )
        player.sendMessage(
            Component.text("Use ", NamedTextColor.GRAY)
                .append(Component.text("/chat <global|world>", NamedTextColor.GOLD))
                .append(Component.text(" to change modes", NamedTextColor.GRAY))
        )

        plugin.logger.info("[ChatCommands] handleShowMode: Command completed successfully for ${player.name}")
        return Command.SINGLE_SUCCESS
    }

    private fun handleSetMode(ctx: CommandContext<CommandSourceStack>): Int {
        plugin.logger.info("[ChatCommands] handleSetMode: Executing chat set mode command")

        val player = ctx.source.sender as? Player ?: run {
            plugin.logger.warning("[ChatCommands] handleSetMode: Non-player attempted to execute command")
            ctx.source.sender.sendMessage(
                Component.text("This command can only be used by players", NamedTextColor.RED)
            )
            return Command.SINGLE_SUCCESS
        }

        val modeString = StringArgumentType.getString(ctx, "mode").uppercase()
        plugin.logger.info("[ChatCommands] handleSetMode: Player ${player.name} attempting to set mode to '$modeString'")

        val mode = try {
            ChatMode.valueOf(modeString)
        } catch (_: IllegalArgumentException) {
            plugin.logger.warning("[ChatCommands] handleSetMode: Player ${player.name} provided invalid mode '$modeString'")
            player.sendMessage(
                Component.text("Invalid chat mode. Valid modes: ", NamedTextColor.RED)
                    .append(Component.text("global, world", NamedTextColor.GOLD))
            )
            return Command.SINGLE_SUCCESS
        }

        plugin.logger.info("[ChatCommands] handleSetMode: Setting chat mode for ${player.name} to $mode")
        chatManager.setChatMode(player.uniqueId, mode)

        player.sendMessage(
            Component.text("Chat mode set to ", NamedTextColor.GREEN)
                .append(Component.text(mode.name, NamedTextColor.GOLD))
        )

        when (mode) {
            ChatMode.GLOBAL -> {
                plugin.logger.info("[ChatCommands] handleSetMode: Player ${player.name} set to GLOBAL mode")
                player.sendMessage(
                    Component.text("Your messages will be visible to all players in global chat", NamedTextColor.GRAY)
                )
            }
            ChatMode.WORLD -> {
                plugin.logger.info("[ChatCommands] handleSetMode: Player ${player.name} set to WORLD mode")
                player.sendMessage(
                    Component.text("Your messages will only be visible to players in your current world", NamedTextColor.GRAY)
                )
            }
        }

        plugin.logger.info("[ChatCommands] handleSetMode: Command completed successfully for ${player.name}")
        return Command.SINGLE_SUCCESS
    }

    private fun suggestChatModes(
        ctx: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        listOf("global", "world")
            .filter { it.startsWith(builder.remainingLowerCase) }
            .forEach { builder.suggest(it) }
        return builder.buildFuture()
    }
}
