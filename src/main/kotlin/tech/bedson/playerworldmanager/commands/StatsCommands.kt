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
import tech.bedson.playerworldmanager.gui.WorldStatsGui
import tech.bedson.playerworldmanager.managers.DataManager
import tech.bedson.playerworldmanager.managers.StatsManager
import tech.bedson.playerworldmanager.managers.WorldManager
import tech.bedson.playerworldmanager.utils.DebugLogger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Brigadier command builder for /stats command.
 * Allows players to view statistics for their worlds.
 */
@Suppress("UnstableApiUsage")
class StatsCommands(
    private val plugin: JavaPlugin,
    private val statsManager: StatsManager,
    private val worldManager: WorldManager,
    private val dataManager: DataManager,
    private val worldStatsGui: WorldStatsGui
) {
    private val debugLogger = DebugLogger(plugin, "StatsCommands")

    fun build(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("stats")
            // /stats - Show stats for current world or open GUI
            .executes(::handleCurrentWorld)
            // /stats <worldname> - Show stats for a specific world
            .then(Commands.argument("world", StringArgumentType.word())
                .suggests(::suggestWorlds)
                .executes(::handleSpecificWorld)
            )
            // /stats gui [worldname] - Open stats GUI
            .then(Commands.literal("gui")
                .then(Commands.argument("world", StringArgumentType.word())
                    .suggests(::suggestWorlds)
                    .executes(::handleOpenGuiWithWorld)
                )
                .executes(::handleOpenGui)
            )
            .build()
    }

    /**
     * Handle /stats - show stats for current world.
     */
    private fun handleCurrentWorld(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleCurrentWorld", "sender" to ctx.source.sender.name)

        val player = ctx.source.sender as? Player
        if (player == null) {
            ctx.source.sender.sendMessage(
                Component.text("This command can only be used by players", NamedTextColor.RED)
            )
            return Command.SINGLE_SUCCESS
        }

        // Check if player is in a plugin world
        val worldStats = statsManager.getWorldStatsByBukkitWorld(player.world)
        if (worldStats == null) {
            player.sendMessage(
                Component.text("You are not in a player world. Use ", NamedTextColor.YELLOW)
                    .append(Component.text("/stats <worldname>", NamedTextColor.GOLD))
                    .append(Component.text(" to view specific world stats.", NamedTextColor.YELLOW))
            )
            return Command.SINGLE_SUCCESS
        }

        val playerWorld = worldManager.getPlayerWorldFromBukkitWorld(player.world)
        displayWorldStats(player, playerWorld?.name ?: "Unknown", worldStats)

        debugLogger.debugMethodExit("handleCurrentWorld", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    /**
     * Handle /stats <worldname> - show stats for a specific world.
     */
    private fun handleSpecificWorld(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleSpecificWorld", "sender" to ctx.source.sender.name)

        val player = ctx.source.sender as? Player
        if (player == null) {
            ctx.source.sender.sendMessage(
                Component.text("This command can only be used by players", NamedTextColor.RED)
            )
            return Command.SINGLE_SUCCESS
        }

        val worldName = StringArgumentType.getString(ctx, "world")
        debugLogger.debug("Looking up world", "worldName" to worldName)

        // Find the world owned by this player
        val ownedWorlds = dataManager.getWorldsByOwner(player.uniqueId)
        val playerWorld = ownedWorlds.firstOrNull { it.name.equals(worldName, ignoreCase = true) }

        if (playerWorld == null) {
            player.sendMessage(
                Component.text("World '$worldName' not found or you don't own it.", NamedTextColor.RED)
            )
            return Command.SINGLE_SUCCESS
        }

        val worldStats = statsManager.getWorldStats(playerWorld.id)
        displayWorldStats(player, playerWorld.name, worldStats)

        debugLogger.debugMethodExit("handleSpecificWorld", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    /**
     * Handle /stats gui - open the stats GUI for current world.
     */
    private fun handleOpenGui(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleOpenGui", "sender" to ctx.source.sender.name)

        val player = ctx.source.sender as? Player
        if (player == null) {
            ctx.source.sender.sendMessage(
                Component.text("This command can only be used by players", NamedTextColor.RED)
            )
            return Command.SINGLE_SUCCESS
        }

        // Check if player is in a plugin world
        val playerWorld = worldManager.getPlayerWorldFromBukkitWorld(player.world)
        if (playerWorld == null) {
            player.sendMessage(
                Component.text("You are not in a player world. Use ", NamedTextColor.YELLOW)
                    .append(Component.text("/stats gui <worldname>", NamedTextColor.GOLD))
                    .append(Component.text(" to view specific world stats.", NamedTextColor.YELLOW))
            )
            return Command.SINGLE_SUCCESS
        }

        worldStatsGui.open(player, playerWorld)

        debugLogger.debugMethodExit("handleOpenGui", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    /**
     * Handle /stats gui <worldname> - open the stats GUI for a specific world.
     */
    private fun handleOpenGuiWithWorld(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleOpenGuiWithWorld", "sender" to ctx.source.sender.name)

        val player = ctx.source.sender as? Player
        if (player == null) {
            ctx.source.sender.sendMessage(
                Component.text("This command can only be used by players", NamedTextColor.RED)
            )
            return Command.SINGLE_SUCCESS
        }

        val worldName = StringArgumentType.getString(ctx, "world")
        debugLogger.debug("Looking up world", "worldName" to worldName)

        // Find the world owned by this player
        val ownedWorlds = dataManager.getWorldsByOwner(player.uniqueId)
        val playerWorld = ownedWorlds.firstOrNull { it.name.equals(worldName, ignoreCase = true) }

        if (playerWorld == null) {
            player.sendMessage(
                Component.text("World '$worldName' not found or you don't own it.", NamedTextColor.RED)
            )
            return Command.SINGLE_SUCCESS
        }

        worldStatsGui.open(player, playerWorld)

        debugLogger.debugMethodExit("handleOpenGuiWithWorld", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    /**
     * Display world statistics in chat.
     */
    private fun displayWorldStats(player: Player, worldName: String, stats: tech.bedson.playerworldmanager.models.WorldStatistics) {
        player.sendMessage(Component.empty())
        player.sendMessage(
            Component.text("=== Statistics for ", NamedTextColor.GOLD)
                .append(Component.text(worldName, NamedTextColor.YELLOW))
                .append(Component.text(" ===", NamedTextColor.GOLD))
        )
        player.sendMessage(Component.empty())

        player.sendMessage(
            Component.text("  Blocks Placed: ", NamedTextColor.GRAY)
                .append(Component.text(formatNumber(stats.blocksPlaced), NamedTextColor.WHITE))
        )
        player.sendMessage(
            Component.text("  Blocks Broken: ", NamedTextColor.GRAY)
                .append(Component.text(formatNumber(stats.blocksBroken), NamedTextColor.WHITE))
        )
        player.sendMessage(
            Component.text("  Mobs Killed: ", NamedTextColor.GRAY)
                .append(Component.text(formatNumber(stats.mobsKilled), NamedTextColor.WHITE))
        )
        player.sendMessage(
            Component.text("  Animals Killed: ", NamedTextColor.GRAY)
                .append(Component.text(formatNumber(stats.animalsKilled), NamedTextColor.WHITE))
        )
        player.sendMessage(
            Component.text("  Player Kills: ", NamedTextColor.GRAY)
                .append(Component.text(formatNumber(stats.playerKills), NamedTextColor.WHITE))
        )
        player.sendMessage(
            Component.text("  Player Deaths: ", NamedTextColor.GRAY)
                .append(Component.text(formatNumber(stats.playerDeaths), NamedTextColor.WHITE))
        )
        player.sendMessage(
            Component.text("  Items Crafted: ", NamedTextColor.GRAY)
                .append(Component.text(formatNumber(stats.itemsCrafted), NamedTextColor.WHITE))
        )
        player.sendMessage(
            Component.text("  Time Played: ", NamedTextColor.GRAY)
                .append(Component.text(formatDuration(stats.timePlayed), NamedTextColor.WHITE))
        )

        player.sendMessage(Component.empty())
        player.sendMessage(
            Component.text("Use ", NamedTextColor.GRAY)
                .append(Component.text("/stats gui", NamedTextColor.GOLD))
                .append(Component.text(" for a detailed view", NamedTextColor.GRAY))
        )
    }

    /**
     * Suggest worlds owned by the player.
     */
    private fun suggestWorlds(
        ctx: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val player = ctx.source.sender as? Player ?: return builder.buildFuture()

        dataManager.getWorldsByOwner(player.uniqueId)
            .map { it.name.lowercase() }
            .filter { it.startsWith(builder.remainingLowerCase) }
            .forEach { builder.suggest(it) }

        return builder.buildFuture()
    }

    /**
     * Format a number with commas for readability.
     */
    private fun formatNumber(number: Long): String {
        return String.format("%,d", number)
    }

    /**
     * Format a duration in milliseconds to a human-readable string.
     */
    private fun formatDuration(millis: Long): String {
        if (millis == 0L) return "0 minutes"

        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60

        return when {
            hours > 0 -> "$hours hours, $minutes minutes"
            else -> "$minutes minutes"
        }
    }
}
