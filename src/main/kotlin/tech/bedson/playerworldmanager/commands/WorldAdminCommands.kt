package tech.bedson.playerworldmanager.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.gui.AdminMenuGui
import tech.bedson.playerworldmanager.managers.DataManager
import tech.bedson.playerworldmanager.managers.InviteManager
import tech.bedson.playerworldmanager.managers.WorldManager
import tech.bedson.playerworldmanager.models.PlayerWorld
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * Brigadier command builder for /worldadmin command.
 */
class WorldAdminCommands(
    private val plugin: JavaPlugin,
    private val worldManager: WorldManager,
    private val inviteManager: InviteManager,
    private val dataManager: DataManager,
    private val adminMenuGui: AdminMenuGui
) {
    private val purgeConfirmations = ConcurrentHashMap<String, PurgeConfirmation>()

    private data class PurgeConfirmation(
        val days: Int,
        val worldCount: Int,
        val timestamp: Long
    )

    fun build(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("worldadmin")
            .requires { it.sender.hasPermission("playerworldmanager.admin") }
            // /worldadmin list [player] [page]
            .then(Commands.literal("list")
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests(::suggestPlayersWithWorlds)
                    .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(::handleListPlayerPage)
                    )
                    .executes(::handleListPlayer)
                )
                .executes(::handleList)
            )
            // /worldadmin info <owner> <world>
            .then(Commands.literal("info")
                .then(Commands.argument("owner", StringArgumentType.word())
                    .suggests(::suggestPlayersWithWorlds)
                    .then(Commands.argument("world", StringArgumentType.word())
                        .suggests(::suggestOwnerWorlds)
                        .executes(::handleInfo)
                    )
                )
            )
            // /worldadmin tp <owner> <world>
            .then(Commands.literal("tp")
                .then(Commands.argument("owner", StringArgumentType.word())
                    .suggests(::suggestPlayersWithWorlds)
                    .then(Commands.argument("world", StringArgumentType.word())
                        .suggests(::suggestOwnerWorlds)
                        .executes(::handleTeleport)
                    )
                )
            )
            // /worldadmin delete <owner> <world>
            .then(Commands.literal("delete")
                .then(Commands.argument("owner", StringArgumentType.word())
                    .suggests(::suggestPlayersWithWorlds)
                    .then(Commands.argument("world", StringArgumentType.word())
                        .suggests(::suggestOwnerWorlds)
                        .executes(::handleDelete)
                    )
                )
            )
            // /worldadmin disable <owner> <world>
            .then(Commands.literal("disable")
                .then(Commands.argument("owner", StringArgumentType.word())
                    .suggests(::suggestPlayersWithWorlds)
                    .then(Commands.argument("world", StringArgumentType.word())
                        .suggests(::suggestOwnerWorlds)
                        .executes(::handleDisable)
                    )
                )
            )
            // /worldadmin enable <owner> <world>
            .then(Commands.literal("enable")
                .then(Commands.argument("owner", StringArgumentType.word())
                    .suggests(::suggestPlayersWithWorlds)
                    .then(Commands.argument("world", StringArgumentType.word())
                        .suggests(::suggestOwnerWorlds)
                        .executes(::handleEnable)
                    )
                )
            )
            // /worldadmin setlimit <player> <amount>
            .then(Commands.literal("setlimit")
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests(::suggestPlayersWithWorlds)
                    .then(Commands.argument("limit", IntegerArgumentType.integer(-1))
                        .executes(::handleSetLimit)
                    )
                )
            )
            // /worldadmin purge <days>
            .then(Commands.literal("purge")
                .then(Commands.argument("days", IntegerArgumentType.integer(1))
                    .executes(::handlePurge)
                )
            )
            // /worldadmin stats
            .then(Commands.literal("stats")
                .executes(::handleStats)
            )
            // /worldadmin reload
            .then(Commands.literal("reload")
                .executes(::handleReload)
            )
            // /worldadmin menu
            .then(Commands.literal("menu")
                .executes(::handleMenu)
            )
            // /worldadmin help
            .then(Commands.literal("help")
                .executes(::handleHelp)
            )
            .executes(::handleHelp)
            .build()
    }

    // ========================
    // Command Handlers
    // ========================

    private fun handleList(ctx: CommandContext<CommandSourceStack>): Int {
        return handleListWithParams(ctx, null, 1)
    }

    private fun handleListPlayer(ctx: CommandContext<CommandSourceStack>): Int {
        val playerName = StringArgumentType.getString(ctx, "player")
        return handleListWithParams(ctx, playerName, 1)
    }

    private fun handleListPlayerPage(ctx: CommandContext<CommandSourceStack>): Int {
        val playerName = StringArgumentType.getString(ctx, "player")
        val page = IntegerArgumentType.getInteger(ctx, "page")
        return handleListWithParams(ctx, playerName, page)
    }

    private fun handleListWithParams(ctx: CommandContext<CommandSourceStack>, playerName: String?, page: Int): Int {
        plugin.logger.info("[WorldAdminCommands] handleListWithParams: Executing admin list command (player='$playerName', page=$page)")
        val sender = ctx.source.sender

        val worlds = if (playerName != null) {
            plugin.logger.info("[WorldAdminCommands] handleListWithParams: Listing worlds for specific player '$playerName'")
            val targetPlayer = Bukkit.getOfflinePlayer(playerName)
            if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline) {
                plugin.logger.warning("[WorldAdminCommands] handleListWithParams: Player '$playerName' not found")
                sender.sendMessage(
                    Component.text("Player '", NamedTextColor.RED)
                        .append(Component.text(playerName, NamedTextColor.GOLD))
                        .append(Component.text("' not found", NamedTextColor.RED))
                )
                return Command.SINGLE_SUCCESS
            }
            dataManager.getWorldsByOwner(targetPlayer.uniqueId)
        } else {
            plugin.logger.info("[WorldAdminCommands] handleListWithParams: Listing all worlds")
            dataManager.getAllWorlds()
        }

        if (worlds.isEmpty()) {
            plugin.logger.info("[WorldAdminCommands] handleListWithParams: No worlds found")
            sender.sendMessage(Component.text("No worlds found", NamedTextColor.YELLOW))
            return Command.SINGLE_SUCCESS
        }

        val pageSize = 10
        val totalPages = (worlds.size + pageSize - 1) / pageSize
        val actualPage = page.coerceIn(1, totalPages)
        val startIndex = (actualPage - 1) * pageSize
        val endIndex = minOf(startIndex + pageSize, worlds.size)

        plugin.logger.info("[WorldAdminCommands] handleListWithParams: Displaying ${worlds.size} world(s) (page $actualPage/$totalPages)")

        sender.sendMessage(
            Component.text("=== World List (Page $actualPage/$totalPages) ===", NamedTextColor.DARK_PURPLE)
        )

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm")
        worlds.subList(startIndex, endIndex).forEach { world ->
            val status = if (world.isEnabled) "Enabled" else "Disabled"
            val statusColor = if (world.isEnabled) NamedTextColor.GREEN else NamedTextColor.RED
            val createdDate = dateFormat.format(Date(world.createdAt))

            sender.sendMessage(
                Component.text("[${world.ownerName}] ", NamedTextColor.GRAY)
                    .append(Component.text(world.name, NamedTextColor.GOLD))
                    .append(Component.text(" - ", NamedTextColor.GRAY))
                    .append(Component.text(status, statusColor))
                    .append(Component.text(" - ", NamedTextColor.GRAY))
                    .append(Component.text(createdDate, NamedTextColor.YELLOW))
            )
        }

        plugin.logger.info("[WorldAdminCommands] handleListWithParams: Command completed successfully")
        return Command.SINGLE_SUCCESS
    }

    private fun handleInfo(ctx: CommandContext<CommandSourceStack>): Int {
        plugin.logger.info("[WorldAdminCommands] handleInfo: Executing admin info command")
        val sender = ctx.source.sender
        val world = findWorld(ctx) ?: return Command.SINGLE_SUCCESS

        plugin.logger.info("[WorldAdminCommands] handleInfo: Retrieving info for world '${world.name}' owned by ${world.ownerName}")

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val createdDate = dateFormat.format(Date(world.createdAt))

        val onlinePlayers = mutableListOf<String>()
        for (env in World.Environment.entries) {
            val bukkitWorld = worldManager.getBukkitWorld(world, env)
            if (bukkitWorld != null) {
                onlinePlayers.addAll(bukkitWorld.players.map { it.name })
            }
        }

        plugin.logger.info("[WorldAdminCommands] handleInfo: World '${world.name}' has ${onlinePlayers.size} online player(s), ${world.invitedPlayers.size} invited player(s)")

        sender.sendMessage(Component.text("=== World Info: ${world.name} ===", NamedTextColor.DARK_PURPLE))
        sender.sendMessage(
            Component.text("Owner: ", NamedTextColor.GRAY)
                .append(Component.text(world.ownerName, NamedTextColor.GOLD))
        )
        sender.sendMessage(
            Component.text("Type: ", NamedTextColor.GRAY)
                .append(Component.text(world.worldType.name, NamedTextColor.GOLD))
        )
        sender.sendMessage(
            Component.text("Created: ", NamedTextColor.GRAY)
                .append(Component.text(createdDate, NamedTextColor.GOLD))
        )
        sender.sendMessage(
            Component.text("Invited Players: ", NamedTextColor.GRAY)
                .append(Component.text(world.invitedPlayers.size.toString(), NamedTextColor.GOLD))
        )
        sender.sendMessage(
            Component.text("Online: ", NamedTextColor.GRAY)
                .append(Component.text(onlinePlayers.size.toString(), NamedTextColor.GOLD))
                .append(if (onlinePlayers.isNotEmpty())
                    Component.text(" (${onlinePlayers.joinToString(", ")})", NamedTextColor.YELLOW)
                else Component.empty())
        )
        sender.sendMessage(
            Component.text("Enabled: ", NamedTextColor.GRAY)
                .append(Component.text(
                    if (world.isEnabled) "Yes" else "No",
                    if (world.isEnabled) NamedTextColor.GREEN else NamedTextColor.RED
                ))
        )

        plugin.logger.info("[WorldAdminCommands] handleInfo: Command completed successfully")
        return Command.SINGLE_SUCCESS
    }

    private fun handleTeleport(ctx: CommandContext<CommandSourceStack>): Int {
        plugin.logger.info("[WorldAdminCommands] handleTeleport: Executing admin teleport command")
        val sender = ctx.source.sender
        if (sender !is Player) {
            plugin.logger.warning("[WorldAdminCommands] handleTeleport: Non-player attempted to execute command")
            sender.sendMessage(Component.text("This command can only be used by players", NamedTextColor.RED))
            return Command.SINGLE_SUCCESS
        }

        val world = findWorld(ctx) ?: return Command.SINGLE_SUCCESS

        plugin.logger.info("[WorldAdminCommands] handleTeleport: Admin ${sender.name} teleporting to world '${world.name}'")
        sender.sendMessage(Component.text("Teleporting...", NamedTextColor.YELLOW))

        worldManager.teleportToWorld(sender, world).thenAccept { success ->
            sender.scheduler.run(plugin, { _ ->
                if (success) {
                    plugin.logger.info("[WorldAdminCommands] handleTeleport: Admin ${sender.name} teleported successfully to '${world.name}'")
                    sender.sendMessage(
                        Component.text("Teleported to ", NamedTextColor.GREEN)
                            .append(Component.text(world.name, NamedTextColor.GOLD))
                    )
                } else {
                    plugin.logger.warning("[WorldAdminCommands] handleTeleport: Failed to teleport admin ${sender.name} to '${world.name}'")
                    sender.sendMessage(Component.text("Failed to teleport", NamedTextColor.RED))
                }
            }, null)
        }

        return Command.SINGLE_SUCCESS
    }

    private fun handleDelete(ctx: CommandContext<CommandSourceStack>): Int {
        plugin.logger.info("[WorldAdminCommands] handleDelete: Executing admin delete command")
        val sender = ctx.source.sender
        val world = findWorld(ctx) ?: return Command.SINGLE_SUCCESS

        plugin.logger.info("[WorldAdminCommands] handleDelete: Admin deleting world '${world.name}' owned by ${world.ownerName}")
        sender.sendMessage(Component.text("Deleting world...", NamedTextColor.YELLOW))

        worldManager.deleteWorld(world).thenAccept { result ->
            result.onSuccess {
                plugin.logger.info("[WorldAdminCommands] handleDelete: World '${world.name}' deleted successfully")
                sender.sendMessage(
                    Component.text("World '", NamedTextColor.GREEN)
                        .append(Component.text(world.name, NamedTextColor.GOLD))
                        .append(Component.text("' deleted", NamedTextColor.GREEN))
                )
            }.onFailure { error ->
                plugin.logger.warning("[WorldAdminCommands] handleDelete: Failed to delete world '${world.name}': ${error.message}")
                sender.sendMessage(
                    Component.text("Failed: ", NamedTextColor.RED)
                        .append(Component.text(error.message ?: "Unknown", NamedTextColor.GOLD))
                )
            }
        }

        return Command.SINGLE_SUCCESS
    }

    private fun handleDisable(ctx: CommandContext<CommandSourceStack>): Int {
        plugin.logger.info("[WorldAdminCommands] handleDisable: Executing admin disable command")
        val sender = ctx.source.sender
        val world = findWorld(ctx) ?: return Command.SINGLE_SUCCESS

        if (!world.isEnabled) {
            plugin.logger.info("[WorldAdminCommands] handleDisable: World '${world.name}' is already disabled")
            sender.sendMessage(Component.text("World is already disabled", NamedTextColor.YELLOW))
            return Command.SINGLE_SUCCESS
        }

        plugin.logger.info("[WorldAdminCommands] handleDisable: Disabling world '${world.name}' owned by ${world.ownerName}")
        world.isEnabled = false
        dataManager.saveWorld(world)

        sender.sendMessage(
            Component.text("World '", NamedTextColor.GREEN)
                .append(Component.text(world.name, NamedTextColor.GOLD))
                .append(Component.text("' disabled", NamedTextColor.GREEN))
        )

        // Kick players from world
        val defaultWorld = Bukkit.getWorlds().firstOrNull()
        if (defaultWorld != null) {
            plugin.logger.info("[WorldAdminCommands] handleDisable: Kicking players from disabled world '${world.name}'")
            for (env in World.Environment.entries) {
                worldManager.getBukkitWorld(world, env)?.players?.forEach { player ->
                    plugin.logger.info("[WorldAdminCommands] handleDisable: Teleporting ${player.name} out of disabled world")
                    player.teleportAsync(defaultWorld.spawnLocation).thenAccept {
                        player.sendMessage(Component.text("World disabled by admin", NamedTextColor.RED))
                    }
                }
            }
        }

        plugin.logger.info("[WorldAdminCommands] handleDisable: World '${world.name}' disabled successfully")
        return Command.SINGLE_SUCCESS
    }

    private fun handleEnable(ctx: CommandContext<CommandSourceStack>): Int {
        plugin.logger.info("[WorldAdminCommands] handleEnable: Executing admin enable command")
        val sender = ctx.source.sender
        val world = findWorld(ctx) ?: return Command.SINGLE_SUCCESS

        if (world.isEnabled) {
            plugin.logger.info("[WorldAdminCommands] handleEnable: World '${world.name}' is already enabled")
            sender.sendMessage(Component.text("World is already enabled", NamedTextColor.YELLOW))
            return Command.SINGLE_SUCCESS
        }

        plugin.logger.info("[WorldAdminCommands] handleEnable: Enabling world '${world.name}' owned by ${world.ownerName}")
        world.isEnabled = true
        dataManager.saveWorld(world)

        worldManager.loadWorld(world).thenAccept { success ->
            if (success) {
                plugin.logger.info("[WorldAdminCommands] handleEnable: World '${world.name}' enabled and loaded successfully")
            } else {
                plugin.logger.warning("[WorldAdminCommands] handleEnable: World '${world.name}' enabled but failed to load")
            }
            sender.sendMessage(
                if (success)
                    Component.text("World '", NamedTextColor.GREEN)
                        .append(Component.text(world.name, NamedTextColor.GOLD))
                        .append(Component.text("' enabled and loaded", NamedTextColor.GREEN))
                else
                    Component.text("World enabled but failed to load", NamedTextColor.YELLOW)
            )
        }

        return Command.SINGLE_SUCCESS
    }

    private fun handleSetLimit(ctx: CommandContext<CommandSourceStack>): Int {
        plugin.logger.info("[WorldAdminCommands] handleSetLimit: Executing admin setlimit command")
        val sender = ctx.source.sender
        val playerName = StringArgumentType.getString(ctx, "player")
        val limit = IntegerArgumentType.getInteger(ctx, "limit")

        plugin.logger.info("[WorldAdminCommands] handleSetLimit: Setting world limit for '$playerName' to $limit")

        val target = Bukkit.getOfflinePlayer(playerName)
        if (!target.hasPlayedBefore() && !target.isOnline) {
            plugin.logger.warning("[WorldAdminCommands] handleSetLimit: Player '$playerName' not found")
            sender.sendMessage(
                Component.text("Player '", NamedTextColor.RED)
                    .append(Component.text(playerName, NamedTextColor.GOLD))
                    .append(Component.text("' not found", NamedTextColor.RED))
            )
            return Command.SINGLE_SUCCESS
        }

        val playerData = dataManager.getOrCreatePlayerData(target.uniqueId, target.name ?: playerName)
        playerData.worldLimit = limit
        dataManager.savePlayerData(playerData)

        val limitText = if (limit == -1) "unlimited" else limit.toString()
        plugin.logger.info("[WorldAdminCommands] handleSetLimit: World limit set to $limitText for '${target.name ?: playerName}'")
        sender.sendMessage(
            Component.text("Set world limit for ", NamedTextColor.GREEN)
                .append(Component.text(target.name ?: playerName, NamedTextColor.GOLD))
                .append(Component.text(" to ", NamedTextColor.GREEN))
                .append(Component.text(limitText, NamedTextColor.GOLD))
        )

        return Command.SINGLE_SUCCESS
    }

    private fun handlePurge(ctx: CommandContext<CommandSourceStack>): Int {
        plugin.logger.info("[WorldAdminCommands] handlePurge: Executing admin purge command")
        val sender = ctx.source.sender
        val days = IntegerArgumentType.getInteger(ctx, "days")

        plugin.logger.info("[WorldAdminCommands] handlePurge: Purging worlds older than $days days")

        val cutoffTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        val worldsToDelete = dataManager.getAllWorlds().filter { it.createdAt < cutoffTime }

        val senderKey = if (sender is Player) sender.uniqueId.toString() else "console"
        val existingConfirmation = purgeConfirmations[senderKey]

        if (existingConfirmation == null ||
            existingConfirmation.days != days ||
            System.currentTimeMillis() - existingConfirmation.timestamp > 30000) {

            plugin.logger.info("[WorldAdminCommands] handlePurge: Requesting confirmation to delete ${worldsToDelete.size} world(s)")
            purgeConfirmations[senderKey] = PurgeConfirmation(days, worldsToDelete.size, System.currentTimeMillis())

            sender.sendMessage(
                Component.text("WARNING: ", NamedTextColor.RED)
                    .append(Component.text("This will delete ${worldsToDelete.size} worlds older than $days days", NamedTextColor.YELLOW))
            )
            sender.sendMessage(Component.text("Run again within 30 seconds to confirm", NamedTextColor.YELLOW))
            return Command.SINGLE_SUCCESS
        }

        purgeConfirmations.remove(senderKey)

        if (worldsToDelete.isEmpty()) {
            plugin.logger.info("[WorldAdminCommands] handlePurge: No worlds to purge")
            sender.sendMessage(Component.text("No worlds to purge", NamedTextColor.YELLOW))
            return Command.SINGLE_SUCCESS
        }

        plugin.logger.info("[WorldAdminCommands] handlePurge: Starting purge of ${worldsToDelete.size} world(s)")
        sender.sendMessage(Component.text("Purging ${worldsToDelete.size} worlds...", NamedTextColor.YELLOW))

        var deleted = 0
        var failed = 0
        worldsToDelete.forEach { world ->
            worldManager.deleteWorld(world).thenAccept { result ->
                if (result.isSuccess) deleted++ else failed++
                if (deleted + failed == worldsToDelete.size) {
                    plugin.logger.info("[WorldAdminCommands] handlePurge: Purge complete. Deleted: $deleted, Failed: $failed")
                    sender.sendMessage(
                        Component.text("Purge complete. Deleted: $deleted, Failed: $failed", NamedTextColor.GREEN)
                    )
                }
            }
        }

        return Command.SINGLE_SUCCESS
    }

    private fun handleStats(ctx: CommandContext<CommandSourceStack>): Int {
        plugin.logger.info("[WorldAdminCommands] handleStats: Executing admin stats command")
        val sender = ctx.source.sender
        val allWorlds = dataManager.getAllWorlds()
        val allPlayers = dataManager.getAllPlayerData()

        val enabledCount = allWorlds.count { it.isEnabled }
        val disabledCount = allWorlds.count { !it.isEnabled }

        plugin.logger.info("[WorldAdminCommands] handleStats: Total worlds: ${allWorlds.size}, Enabled: $enabledCount, Disabled: $disabledCount, Players: ${allPlayers.size}")

        sender.sendMessage(Component.text("=== PlayerWorldManager Statistics ===", NamedTextColor.DARK_PURPLE))
        sender.sendMessage(
            Component.text("Total Worlds: ", NamedTextColor.GRAY)
                .append(Component.text(allWorlds.size.toString(), NamedTextColor.GOLD))
        )
        sender.sendMessage(
            Component.text("Enabled: ", NamedTextColor.GRAY)
                .append(Component.text(enabledCount.toString(), NamedTextColor.GOLD))
        )
        sender.sendMessage(
            Component.text("Disabled: ", NamedTextColor.GRAY)
                .append(Component.text(disabledCount.toString(), NamedTextColor.GOLD))
        )
        sender.sendMessage(
            Component.text("Players with worlds: ", NamedTextColor.GRAY)
                .append(Component.text(allPlayers.size.toString(), NamedTextColor.GOLD))
        )

        plugin.logger.info("[WorldAdminCommands] handleStats: Command completed successfully")
        return Command.SINGLE_SUCCESS
    }

    private fun handleReload(ctx: CommandContext<CommandSourceStack>): Int {
        plugin.logger.info("[WorldAdminCommands] handleReload: Executing admin reload command")
        val sender = ctx.source.sender

        try {
            plugin.logger.info("[WorldAdminCommands] handleReload: Reloading plugin configuration")
            plugin.reloadConfig()
            dataManager.loadAll()
            plugin.logger.info("[WorldAdminCommands] handleReload: Configuration reloaded successfully")
            sender.sendMessage(Component.text("Configuration reloaded", NamedTextColor.GREEN))
        } catch (e: Exception) {
            plugin.logger.warning("[WorldAdminCommands] handleReload: Failed to reload configuration: ${e.message}")
            sender.sendMessage(
                Component.text("Failed to reload: ", NamedTextColor.RED)
                    .append(Component.text(e.message ?: "Unknown", NamedTextColor.GOLD))
            )
        }

        return Command.SINGLE_SUCCESS
    }

    private fun handleMenu(ctx: CommandContext<CommandSourceStack>): Int {
        plugin.logger.info("[WorldAdminCommands] handleMenu: Executing admin menu command")
        val sender = ctx.source.sender
        if (sender !is Player) {
            plugin.logger.warning("[WorldAdminCommands] handleMenu: Non-player attempted to execute command")
            sender.sendMessage(Component.text("This command can only be used by players", NamedTextColor.RED))
            return Command.SINGLE_SUCCESS
        }

        plugin.logger.info("[WorldAdminCommands] handleMenu: Opening admin menu GUI for ${sender.name}")
        adminMenuGui.open(sender)

        plugin.logger.info("[WorldAdminCommands] handleMenu: Command completed for ${sender.name}")
        return Command.SINGLE_SUCCESS
    }

    private fun handleHelp(ctx: CommandContext<CommandSourceStack>): Int {
        val sender = ctx.source.sender

        sender.sendMessage(Component.text("=== World Admin Commands ===", NamedTextColor.DARK_PURPLE))
        sender.sendMessage(Component.text("/worldadmin list [player] [page]", NamedTextColor.GRAY)
            .append(Component.text(" - List worlds", NamedTextColor.YELLOW)))
        sender.sendMessage(Component.text("/worldadmin info <owner> <world>", NamedTextColor.GRAY)
            .append(Component.text(" - World info", NamedTextColor.YELLOW)))
        sender.sendMessage(Component.text("/worldadmin tp <owner> <world>", NamedTextColor.GRAY)
            .append(Component.text(" - Teleport", NamedTextColor.YELLOW)))
        sender.sendMessage(Component.text("/worldadmin delete <owner> <world>", NamedTextColor.GRAY)
            .append(Component.text(" - Delete", NamedTextColor.YELLOW)))
        sender.sendMessage(Component.text("/worldadmin disable/enable <owner> <world>", NamedTextColor.GRAY)
            .append(Component.text(" - Toggle", NamedTextColor.YELLOW)))
        sender.sendMessage(Component.text("/worldadmin setlimit <player> <amount>", NamedTextColor.GRAY)
            .append(Component.text(" - Set limit", NamedTextColor.YELLOW)))
        sender.sendMessage(Component.text("/worldadmin purge <days>", NamedTextColor.GRAY)
            .append(Component.text(" - Delete old", NamedTextColor.YELLOW)))
        sender.sendMessage(Component.text("/worldadmin stats", NamedTextColor.GRAY)
            .append(Component.text(" - Statistics", NamedTextColor.YELLOW)))
        sender.sendMessage(Component.text("/worldadmin reload", NamedTextColor.GRAY)
            .append(Component.text(" - Reload config", NamedTextColor.YELLOW)))
        sender.sendMessage(Component.text("/worldadmin menu", NamedTextColor.GRAY)
            .append(Component.text(" - Open GUI", NamedTextColor.YELLOW)))

        return Command.SINGLE_SUCCESS
    }

    // ========================
    // Suggestions
    // ========================

    private fun suggestPlayersWithWorlds(
        ctx: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        dataManager.getAllPlayerData()
            .map { it.username }
            .filter { it.lowercase().startsWith(builder.remainingLowerCase) }
            .forEach { builder.suggest(it) }
        return builder.buildFuture()
    }

    private fun suggestOwnerWorlds(
        ctx: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val ownerName = StringArgumentType.getString(ctx, "owner")
        val owner = Bukkit.getOfflinePlayer(ownerName)
        dataManager.getWorldsByOwner(owner.uniqueId)
            .map { it.name }
            .filter { it.lowercase().startsWith(builder.remainingLowerCase) }
            .forEach { builder.suggest(it) }
        return builder.buildFuture()
    }

    // ========================
    // Helpers
    // ========================

    private fun findWorld(ctx: CommandContext<CommandSourceStack>): PlayerWorld? {
        val ownerName = StringArgumentType.getString(ctx, "owner")
        val worldName = StringArgumentType.getString(ctx, "world")
        val sender = ctx.source.sender

        plugin.logger.info("[WorldAdminCommands] findWorld: Looking up world '$worldName' owned by '$ownerName'")

        val owner = Bukkit.getOfflinePlayer(ownerName)
        if (!owner.hasPlayedBefore() && !owner.isOnline) {
            plugin.logger.warning("[WorldAdminCommands] findWorld: Owner '$ownerName' not found")
            sender.sendMessage(
                Component.text("Player '", NamedTextColor.RED)
                    .append(Component.text(ownerName, NamedTextColor.GOLD))
                    .append(Component.text("' not found", NamedTextColor.RED))
            )
            return null
        }

        val world = dataManager.getWorldsByOwner(owner.uniqueId)
            .firstOrNull { it.name.equals(worldName, ignoreCase = true) }

        if (world == null) {
            plugin.logger.warning("[WorldAdminCommands] findWorld: World '$worldName' not found for owner '$ownerName'")
            sender.sendMessage(
                Component.text("$ownerName doesn't own a world named '", NamedTextColor.RED)
                    .append(Component.text(worldName, NamedTextColor.GOLD))
                    .append(Component.text("'", NamedTextColor.RED))
            )
        } else {
            plugin.logger.info("[WorldAdminCommands] findWorld: Found world '${world.name}' (ID: ${world.id})")
        }

        return world
    }
}
