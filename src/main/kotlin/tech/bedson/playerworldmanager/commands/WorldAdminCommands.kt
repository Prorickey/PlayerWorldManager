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
import tech.bedson.playerworldmanager.utils.DebugLogger
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
    private val debugLogger = DebugLogger(plugin, "WorldAdminCommands")
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
        debugLogger.debugMethodEntry("handleList", "sender" to ctx.source.sender.name)
        val result = handleListWithParams(ctx, null, 1)
        debugLogger.debugMethodExit("handleList", result)
        return result
    }

    private fun handleListPlayer(ctx: CommandContext<CommandSourceStack>): Int {
        val playerName = StringArgumentType.getString(ctx, "player")
        debugLogger.debugMethodEntry("handleListPlayer", "sender" to ctx.source.sender.name, "playerName" to playerName)
        val result = handleListWithParams(ctx, playerName, 1)
        debugLogger.debugMethodExit("handleListPlayer", result)
        return result
    }

    private fun handleListPlayerPage(ctx: CommandContext<CommandSourceStack>): Int {
        val playerName = StringArgumentType.getString(ctx, "player")
        val page = IntegerArgumentType.getInteger(ctx, "page")
        debugLogger.debugMethodEntry("handleListPlayerPage", "sender" to ctx.source.sender.name, "playerName" to playerName, "page" to page)
        val result = handleListWithParams(ctx, playerName, page)
        debugLogger.debugMethodExit("handleListPlayerPage", result)
        return result
    }

    private fun handleListWithParams(ctx: CommandContext<CommandSourceStack>, playerName: String?, page: Int): Int {
        debugLogger.debugMethodEntry("handleListWithParams", "sender" to ctx.source.sender.name, "playerName" to playerName, "page" to page)
        plugin.logger.info("[WorldAdminCommands] handleListWithParams: Executing admin list command (player='$playerName', page=$page)")
        val sender = ctx.source.sender

        val worlds = if (playerName != null) {
            plugin.logger.info("[WorldAdminCommands] handleListWithParams: Listing worlds for specific player '$playerName'")
            val targetPlayer = Bukkit.getOfflinePlayer(playerName)
            val targetExists = targetPlayer.hasPlayedBefore() || targetPlayer.isOnline
            debugLogger.debug("Target player lookup", "playerName" to playerName, "exists" to targetExists, "uuid" to targetPlayer.uniqueId)
            if (!targetExists) {
                plugin.logger.warning("[WorldAdminCommands] handleListWithParams: Player '$playerName' not found")
                sender.sendMessage(
                    Component.text("Player '", NamedTextColor.RED)
                        .append(Component.text(playerName, NamedTextColor.GOLD))
                        .append(Component.text("' not found", NamedTextColor.RED))
                )
                debugLogger.debugMethodExit("handleListWithParams", "player not found")
                return Command.SINGLE_SUCCESS
            }
            dataManager.getWorldsByOwner(targetPlayer.uniqueId)
        } else {
            plugin.logger.info("[WorldAdminCommands] handleListWithParams: Listing all worlds")
            debugLogger.debug("Listing all worlds")
            dataManager.getAllWorlds()
        }

        debugLogger.debug("Worlds retrieved", "count" to worlds.size)
        if (worlds.isEmpty()) {
            plugin.logger.info("[WorldAdminCommands] handleListWithParams: No worlds found")
            sender.sendMessage(Component.text("No worlds found", NamedTextColor.YELLOW))
            debugLogger.debugMethodExit("handleListWithParams", "no worlds")
            return Command.SINGLE_SUCCESS
        }

        val pageSize = 10
        val totalPages = (worlds.size + pageSize - 1) / pageSize
        val actualPage = page.coerceIn(1, totalPages)
        val startIndex = (actualPage - 1) * pageSize
        val endIndex = minOf(startIndex + pageSize, worlds.size)
        debugLogger.debug("Pagination", "totalWorlds" to worlds.size, "pageSize" to pageSize, "totalPages" to totalPages, "actualPage" to actualPage, "startIndex" to startIndex, "endIndex" to endIndex)

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
        debugLogger.debugMethodExit("handleListWithParams", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleInfo(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleInfo", "sender" to ctx.source.sender.name)
        plugin.logger.info("[WorldAdminCommands] handleInfo: Executing admin info command")
        val sender = ctx.source.sender
        val world = findWorld(ctx)
        if (world == null) {
            debugLogger.debugMethodExit("handleInfo", "world not found")
            return Command.SINGLE_SUCCESS
        }

        plugin.logger.info("[WorldAdminCommands] handleInfo: Retrieving info for world '${world.name}' owned by ${world.ownerName}")
        debugLogger.debugState("PlayerWorld", "id" to world.id, "name" to world.name, "ownerUuid" to world.ownerUuid, "isEnabled" to world.isEnabled)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val createdDate = dateFormat.format(Date(world.createdAt))

        val onlinePlayers = mutableListOf<String>()
        for (env in World.Environment.entries) {
            val bukkitWorld = worldManager.getBukkitWorld(world, env)
            if (bukkitWorld != null) {
                onlinePlayers.addAll(bukkitWorld.players.map { it.name })
            }
        }
        debugLogger.debug("Online players in world", "worldName" to world.name, "count" to onlinePlayers.size, "players" to onlinePlayers)

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
        debugLogger.debugMethodExit("handleInfo", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleTeleport(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleTeleport", "sender" to ctx.source.sender.name)
        plugin.logger.info("[WorldAdminCommands] handleTeleport: Executing admin teleport command")
        val sender = ctx.source.sender
        debugLogger.debug("Sender type check", "isPlayer" to (sender is Player), "senderType" to sender.javaClass.simpleName)
        if (sender !is Player) {
            plugin.logger.warning("[WorldAdminCommands] handleTeleport: Non-player attempted to execute command")
            sender.sendMessage(Component.text("This command can only be used by players", NamedTextColor.RED))
            debugLogger.debugMethodExit("handleTeleport", "not a player")
            return Command.SINGLE_SUCCESS
        }

        val world = findWorld(ctx)
        if (world == null) {
            debugLogger.debugMethodExit("handleTeleport", "world not found")
            return Command.SINGLE_SUCCESS
        }

        plugin.logger.info("[WorldAdminCommands] handleTeleport: Admin ${sender.name} teleporting to world '${world.name}'")
        debugLogger.debug("Teleporting admin", "adminName" to sender.name, "worldName" to world.name, "worldId" to world.id)
        sender.sendMessage(Component.text("Teleporting...", NamedTextColor.YELLOW))

        worldManager.teleportToWorld(sender, world).thenAccept { success ->
            debugLogger.debug("Admin teleport result", "success" to success, "worldName" to world.name)
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

        debugLogger.debugMethodExit("handleTeleport", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleDelete(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleDelete", "sender" to ctx.source.sender.name)
        plugin.logger.info("[WorldAdminCommands] handleDelete: Executing admin delete command")
        val sender = ctx.source.sender
        val world = findWorld(ctx)
        if (world == null) {
            debugLogger.debugMethodExit("handleDelete", "world not found")
            return Command.SINGLE_SUCCESS
        }

        plugin.logger.info("[WorldAdminCommands] handleDelete: Admin deleting world '${world.name}' owned by ${world.ownerName}")
        debugLogger.debug("Deleting world", "worldName" to world.name, "worldId" to world.id, "ownerName" to world.ownerName)
        sender.sendMessage(Component.text("Deleting world...", NamedTextColor.YELLOW))

        worldManager.deleteWorld(world).thenAccept { result ->
            result.onSuccess {
                debugLogger.debug("World deletion succeeded", "worldName" to world.name)
                plugin.logger.info("[WorldAdminCommands] handleDelete: World '${world.name}' deleted successfully")
                sender.sendMessage(
                    Component.text("World '", NamedTextColor.GREEN)
                        .append(Component.text(world.name, NamedTextColor.GOLD))
                        .append(Component.text("' deleted", NamedTextColor.GREEN))
                )
            }.onFailure { error ->
                debugLogger.debug("World deletion failed", "worldName" to world.name, "error" to error.message)
                plugin.logger.warning("[WorldAdminCommands] handleDelete: Failed to delete world '${world.name}': ${error.message}")
                sender.sendMessage(
                    Component.text("Failed: ", NamedTextColor.RED)
                        .append(Component.text(error.message ?: "Unknown", NamedTextColor.GOLD))
                )
            }
        }

        debugLogger.debugMethodExit("handleDelete", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleDisable(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleDisable", "sender" to ctx.source.sender.name)
        plugin.logger.info("[WorldAdminCommands] handleDisable: Executing admin disable command")
        val sender = ctx.source.sender
        val world = findWorld(ctx)
        if (world == null) {
            debugLogger.debugMethodExit("handleDisable", "world not found")
            return Command.SINGLE_SUCCESS
        }

        debugLogger.debug("World state", "worldName" to world.name, "isEnabled" to world.isEnabled)
        if (!world.isEnabled) {
            plugin.logger.info("[WorldAdminCommands] handleDisable: World '${world.name}' is already disabled")
            sender.sendMessage(Component.text("World is already disabled", NamedTextColor.YELLOW))
            debugLogger.debugMethodExit("handleDisable", "already disabled")
            return Command.SINGLE_SUCCESS
        }

        plugin.logger.info("[WorldAdminCommands] handleDisable: Disabling world '${world.name}' owned by ${world.ownerName}")
        debugLogger.debug("Disabling world", "worldName" to world.name, "worldId" to world.id)
        world.isEnabled = false
        dataManager.saveWorld(world)

        sender.sendMessage(
            Component.text("World '", NamedTextColor.GREEN)
                .append(Component.text(world.name, NamedTextColor.GOLD))
                .append(Component.text("' disabled", NamedTextColor.GREEN))
        )

        // Kick players from world
        val defaultWorld = Bukkit.getWorlds().firstOrNull()
        debugLogger.debug("Default world for kick", "defaultWorld" to defaultWorld?.name)
        if (defaultWorld != null) {
            plugin.logger.info("[WorldAdminCommands] handleDisable: Kicking players from disabled world '${world.name}'")
            for (env in World.Environment.entries) {
                val bukkitWorld = worldManager.getBukkitWorld(world, env)
                val players = bukkitWorld?.players ?: emptyList()
                debugLogger.debug("Players in dimension", "environment" to env, "playerCount" to players.size)
                players.forEach { player ->
                    debugLogger.debug("Kicking player from disabled world", "player" to player.name)
                    plugin.logger.info("[WorldAdminCommands] handleDisable: Teleporting ${player.name} out of disabled world")
                    player.teleportAsync(defaultWorld.spawnLocation).thenAccept {
                        player.sendMessage(Component.text("World disabled by admin", NamedTextColor.RED))
                    }
                }
            }
        }

        plugin.logger.info("[WorldAdminCommands] handleDisable: World '${world.name}' disabled successfully")
        debugLogger.debugMethodExit("handleDisable", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleEnable(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleEnable", "sender" to ctx.source.sender.name)
        plugin.logger.info("[WorldAdminCommands] handleEnable: Executing admin enable command")
        val sender = ctx.source.sender
        val world = findWorld(ctx)
        if (world == null) {
            debugLogger.debugMethodExit("handleEnable", "world not found")
            return Command.SINGLE_SUCCESS
        }

        debugLogger.debug("World state", "worldName" to world.name, "isEnabled" to world.isEnabled)
        if (world.isEnabled) {
            plugin.logger.info("[WorldAdminCommands] handleEnable: World '${world.name}' is already enabled")
            sender.sendMessage(Component.text("World is already enabled", NamedTextColor.YELLOW))
            debugLogger.debugMethodExit("handleEnable", "already enabled")
            return Command.SINGLE_SUCCESS
        }

        plugin.logger.info("[WorldAdminCommands] handleEnable: Enabling world '${world.name}' owned by ${world.ownerName}")
        debugLogger.debug("Enabling world", "worldName" to world.name, "worldId" to world.id)
        world.isEnabled = true
        dataManager.saveWorld(world)

        debugLogger.debug("Loading world after enable", "worldName" to world.name)
        worldManager.loadWorld(world).thenAccept { success ->
            debugLogger.debug("World load result", "worldName" to world.name, "success" to success)
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

        debugLogger.debugMethodExit("handleEnable", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleSetLimit(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleSetLimit", "sender" to ctx.source.sender.name)
        plugin.logger.info("[WorldAdminCommands] handleSetLimit: Executing admin setlimit command")
        val sender = ctx.source.sender
        val playerName = StringArgumentType.getString(ctx, "player")
        val limit = IntegerArgumentType.getInteger(ctx, "limit")
        debugLogger.debug("Parsed arguments", "playerName" to playerName, "limit" to limit)

        plugin.logger.info("[WorldAdminCommands] handleSetLimit: Setting world limit for '$playerName' to $limit")

        val target = Bukkit.getOfflinePlayer(playerName)
        val targetExists = target.hasPlayedBefore() || target.isOnline
        debugLogger.debug("Target player lookup", "playerName" to playerName, "uuid" to target.uniqueId, "exists" to targetExists)
        if (!targetExists) {
            plugin.logger.warning("[WorldAdminCommands] handleSetLimit: Player '$playerName' not found")
            sender.sendMessage(
                Component.text("Player '", NamedTextColor.RED)
                    .append(Component.text(playerName, NamedTextColor.GOLD))
                    .append(Component.text("' not found", NamedTextColor.RED))
            )
            debugLogger.debugMethodExit("handleSetLimit", "player not found")
            return Command.SINGLE_SUCCESS
        }

        val playerData = dataManager.getOrCreatePlayerData(target.uniqueId, target.name ?: playerName)
        debugLogger.debug("Player data before update", "uuid" to playerData.uuid, "currentLimit" to playerData.worldLimit)
        playerData.worldLimit = limit
        dataManager.savePlayerData(playerData)
        debugLogger.debug("Player data after update", "uuid" to playerData.uuid, "newLimit" to playerData.worldLimit)

        val limitText = if (limit == -1) "unlimited" else limit.toString()
        plugin.logger.info("[WorldAdminCommands] handleSetLimit: World limit set to $limitText for '${target.name ?: playerName}'")
        sender.sendMessage(
            Component.text("Set world limit for ", NamedTextColor.GREEN)
                .append(Component.text(target.name ?: playerName, NamedTextColor.GOLD))
                .append(Component.text(" to ", NamedTextColor.GREEN))
                .append(Component.text(limitText, NamedTextColor.GOLD))
        )

        debugLogger.debugMethodExit("handleSetLimit", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handlePurge(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handlePurge", "sender" to ctx.source.sender.name)
        plugin.logger.info("[WorldAdminCommands] handlePurge: Executing admin purge command")
        val sender = ctx.source.sender
        val days = IntegerArgumentType.getInteger(ctx, "days")
        debugLogger.debug("Parsed arguments", "days" to days)

        plugin.logger.info("[WorldAdminCommands] handlePurge: Purging worlds older than $days days")

        val cutoffTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        val worldsToDelete = dataManager.getAllWorlds().filter { it.createdAt < cutoffTime }
        debugLogger.debug("Worlds to purge", "cutoffTime" to cutoffTime, "worldCount" to worldsToDelete.size)

        val senderKey = if (sender is Player) sender.uniqueId.toString() else "console"
        val existingConfirmation = purgeConfirmations[senderKey]
        debugLogger.debug("Confirmation check", "senderKey" to senderKey, "hasExisting" to (existingConfirmation != null))

        val needsConfirmation = existingConfirmation == null ||
            existingConfirmation.days != days ||
            System.currentTimeMillis() - existingConfirmation.timestamp > 30000
        debugLogger.debug("Confirmation status", "needsConfirmation" to needsConfirmation)

        if (needsConfirmation) {
            plugin.logger.info("[WorldAdminCommands] handlePurge: Requesting confirmation to delete ${worldsToDelete.size} world(s)")
            purgeConfirmations[senderKey] = PurgeConfirmation(days, worldsToDelete.size, System.currentTimeMillis())

            sender.sendMessage(
                Component.text("WARNING: ", NamedTextColor.RED)
                    .append(Component.text("This will delete ${worldsToDelete.size} worlds older than $days days", NamedTextColor.YELLOW))
            )
            sender.sendMessage(Component.text("Run again within 30 seconds to confirm", NamedTextColor.YELLOW))
            debugLogger.debugMethodExit("handlePurge", "waiting for confirmation")
            return Command.SINGLE_SUCCESS
        }

        purgeConfirmations.remove(senderKey)
        debugLogger.debug("Confirmation accepted, proceeding with purge")

        if (worldsToDelete.isEmpty()) {
            plugin.logger.info("[WorldAdminCommands] handlePurge: No worlds to purge")
            sender.sendMessage(Component.text("No worlds to purge", NamedTextColor.YELLOW))
            debugLogger.debugMethodExit("handlePurge", "no worlds to purge")
            return Command.SINGLE_SUCCESS
        }

        plugin.logger.info("[WorldAdminCommands] handlePurge: Starting purge of ${worldsToDelete.size} world(s)")
        debugLogger.debug("Starting purge", "worldCount" to worldsToDelete.size, "worldNames" to worldsToDelete.map { it.name })
        sender.sendMessage(Component.text("Purging ${worldsToDelete.size} worlds...", NamedTextColor.YELLOW))

        var deleted = 0
        var failed = 0
        worldsToDelete.forEach { world ->
            worldManager.deleteWorld(world).thenAccept { result ->
                if (result.isSuccess) deleted++ else failed++
                debugLogger.debug("World purge result", "worldName" to world.name, "success" to result.isSuccess, "deleted" to deleted, "failed" to failed)
                if (deleted + failed == worldsToDelete.size) {
                    plugin.logger.info("[WorldAdminCommands] handlePurge: Purge complete. Deleted: $deleted, Failed: $failed")
                    debugLogger.debug("Purge complete", "deleted" to deleted, "failed" to failed)
                    sender.sendMessage(
                        Component.text("Purge complete. Deleted: $deleted, Failed: $failed", NamedTextColor.GREEN)
                    )
                }
            }
        }

        debugLogger.debugMethodExit("handlePurge", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleStats(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleStats", "sender" to ctx.source.sender.name)
        plugin.logger.info("[WorldAdminCommands] handleStats: Executing admin stats command")
        val sender = ctx.source.sender
        val allWorlds = dataManager.getAllWorlds()
        val allPlayers = dataManager.getAllPlayerData()

        val enabledCount = allWorlds.count { it.isEnabled }
        val disabledCount = allWorlds.count { !it.isEnabled }
        debugLogger.debug("Statistics", "totalWorlds" to allWorlds.size, "enabledCount" to enabledCount, "disabledCount" to disabledCount, "playerCount" to allPlayers.size)

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
        debugLogger.debugMethodExit("handleStats", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleReload(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleReload", "sender" to ctx.source.sender.name)
        plugin.logger.info("[WorldAdminCommands] handleReload: Executing admin reload command")
        val sender = ctx.source.sender

        try {
            plugin.logger.info("[WorldAdminCommands] handleReload: Reloading plugin configuration")
            debugLogger.debug("Reloading config")
            plugin.reloadConfig()
            debugLogger.debug("Reloading data manager")
            dataManager.loadAll()
            // Refresh debug state after config reload
            DebugLogger.refreshDebugState()
            debugLogger.debug("Config reload complete")
            plugin.logger.info("[WorldAdminCommands] handleReload: Configuration reloaded successfully")
            sender.sendMessage(Component.text("Configuration reloaded", NamedTextColor.GREEN))
        } catch (e: Exception) {
            debugLogger.debug("Config reload failed", "error" to e.message)
            plugin.logger.warning("[WorldAdminCommands] handleReload: Failed to reload configuration: ${e.message}")
            sender.sendMessage(
                Component.text("Failed to reload: ", NamedTextColor.RED)
                    .append(Component.text(e.message ?: "Unknown", NamedTextColor.GOLD))
            )
        }

        debugLogger.debugMethodExit("handleReload", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleMenu(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleMenu", "sender" to ctx.source.sender.name)
        plugin.logger.info("[WorldAdminCommands] handleMenu: Executing admin menu command")
        val sender = ctx.source.sender
        debugLogger.debug("Sender type check", "isPlayer" to (sender is Player), "senderType" to sender.javaClass.simpleName)
        if (sender !is Player) {
            plugin.logger.warning("[WorldAdminCommands] handleMenu: Non-player attempted to execute command")
            sender.sendMessage(Component.text("This command can only be used by players", NamedTextColor.RED))
            debugLogger.debugMethodExit("handleMenu", "not a player")
            return Command.SINGLE_SUCCESS
        }

        plugin.logger.info("[WorldAdminCommands] handleMenu: Opening admin menu GUI for ${sender.name}")
        debugLogger.debug("Opening admin menu GUI", "player" to sender.name, "playerUuid" to sender.uniqueId)
        adminMenuGui.open(sender)

        plugin.logger.info("[WorldAdminCommands] handleMenu: Command completed for ${sender.name}")
        debugLogger.debugMethodExit("handleMenu", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleHelp(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleHelp", "sender" to ctx.source.sender.name)
        val sender = ctx.source.sender

        debugLogger.debug("Displaying help", "senderName" to sender.name)
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

        debugLogger.debugMethodExit("handleHelp", Command.SINGLE_SUCCESS)
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
        debugLogger.debugMethodEntry("findWorld", "ownerName" to ownerName, "worldName" to worldName)

        plugin.logger.info("[WorldAdminCommands] findWorld: Looking up world '$worldName' owned by '$ownerName'")

        val owner = Bukkit.getOfflinePlayer(ownerName)
        val ownerExists = owner.hasPlayedBefore() || owner.isOnline
        debugLogger.debug("Owner lookup", "ownerName" to ownerName, "ownerUuid" to owner.uniqueId, "exists" to ownerExists)
        if (!ownerExists) {
            plugin.logger.warning("[WorldAdminCommands] findWorld: Owner '$ownerName' not found")
            sender.sendMessage(
                Component.text("Player '", NamedTextColor.RED)
                    .append(Component.text(ownerName, NamedTextColor.GOLD))
                    .append(Component.text("' not found", NamedTextColor.RED))
            )
            debugLogger.debugMethodExit("findWorld", null)
            return null
        }

        val world = dataManager.getWorldsByOwner(owner.uniqueId)
            .firstOrNull { it.name.equals(worldName, ignoreCase = true) }
        debugLogger.debug("World lookup", "ownerUuid" to owner.uniqueId, "worldName" to worldName, "found" to (world != null))

        if (world == null) {
            plugin.logger.warning("[WorldAdminCommands] findWorld: World '$worldName' not found for owner '$ownerName'")
            sender.sendMessage(
                Component.text("$ownerName doesn't own a world named '", NamedTextColor.RED)
                    .append(Component.text(worldName, NamedTextColor.GOLD))
                    .append(Component.text("'", NamedTextColor.RED))
            )
            debugLogger.debugMethodExit("findWorld", null)
        } else {
            plugin.logger.info("[WorldAdminCommands] findWorld: Found world '${world.name}' (ID: ${world.id})")
            debugLogger.debugMethodExit("findWorld", world.name)
        }

        return world
    }
}
