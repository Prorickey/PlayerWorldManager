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
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.gui.MainMenuGui
import tech.bedson.playerworldmanager.managers.DataManager
import tech.bedson.playerworldmanager.managers.InviteManager
import tech.bedson.playerworldmanager.managers.WorldManager
import tech.bedson.playerworldmanager.models.PlayerWorld
import tech.bedson.playerworldmanager.models.WorldType
import java.util.concurrent.CompletableFuture

/**
 * Brigadier command builder for /world command.
 */
@Suppress("UnstableApiUsage")
class WorldCommands(
    private val plugin: JavaPlugin,
    private val worldManager: WorldManager,
    private val inviteManager: InviteManager,
    private val dataManager: DataManager,
    private val mainMenuGui: MainMenuGui
) {
    fun build(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("world")
            // /world create <name> [type] [seed]
            .then(Commands.literal("create")
                .requires { it.sender.hasPermission("playerworldmanager.create") }
                .then(Commands.argument("name", StringArgumentType.word())
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests(::suggestWorldTypes)
                        .then(Commands.argument("seed", StringArgumentType.word())
                            .executes(::handleCreateWithSeed)
                        )
                        .executes(::handleCreateWithType)
                    )
                    .executes(::handleCreate)
                )
            )
            // /world delete <name>
            .then(Commands.literal("delete")
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests(::suggestOwnedWorlds)
                    .executes(::handleDelete)
                )
            )
            // /world list
            .then(Commands.literal("list")
                .executes(::handleList)
            )
            // /world tp <name>
            .then(Commands.literal("tp")
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests(::suggestOwnedWorlds)
                    .executes(::handleTeleport)
                )
            )
            // /world visit <owner> <name>
            .then(Commands.literal("visit")
                .then(Commands.argument("owner", StringArgumentType.word())
                    .suggests(::suggestPlayersWithWorlds)
                    .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(::suggestOwnerWorlds)
                        .executes(::handleVisit)
                    )
                )
            )
            // /world invite <player> [world]
            .then(Commands.literal("invite")
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests(::suggestOnlinePlayers)
                    .then(Commands.argument("world", StringArgumentType.word())
                        .suggests(::suggestOwnedWorlds)
                        .executes(::handleInviteWithWorld)
                    )
                    .executes(::handleInvite)
                )
            )
            // /world kick <player> [world]
            .then(Commands.literal("kick")
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests(::suggestOnlinePlayers)
                    .then(Commands.argument("world", StringArgumentType.word())
                        .suggests(::suggestOwnedWorlds)
                        .executes(::handleKickWithWorld)
                    )
                    .executes(::handleKick)
                )
            )
            // /world accept <owner> <world>
            .then(Commands.literal("accept")
                .then(Commands.argument("owner", StringArgumentType.word())
                    .suggests(::suggestInviteOwners)
                    .then(Commands.argument("world", StringArgumentType.word())
                        .suggests(::suggestInviteWorlds)
                        .executes(::handleAccept)
                    )
                )
            )
            // /world deny <owner> <world>
            .then(Commands.literal("deny")
                .then(Commands.argument("owner", StringArgumentType.word())
                    .suggests(::suggestInviteOwners)
                    .then(Commands.argument("world", StringArgumentType.word())
                        .suggests(::suggestInviteWorlds)
                        .executes(::handleDeny)
                    )
                )
            )
            // /world transfer <player> <world>
            .then(Commands.literal("transfer")
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests(::suggestOnlinePlayers)
                    .then(Commands.argument("world", StringArgumentType.word())
                        .suggests(::suggestOwnedWorlds)
                        .executes(::handleTransfer)
                    )
                )
            )
            // /world invites
            .then(Commands.literal("invites")
                .executes(::handleInvites)
            )
            // /world spawn
            .then(Commands.literal("spawn")
                .executes(::handleSpawn)
            )
            // /world menu
            .then(Commands.literal("menu")
                .executes(::handleMenu)
            )
            // /world help
            .then(Commands.literal("help")
                .executes(::handleHelp)
            )
            // Default (no args) opens main menu GUI
            .executes(::handleMenu)
            .build()
    }

    // ========================
    // Command Handlers
    // ========================

    private fun handleCreate(ctx: CommandContext<CommandSourceStack>): Int {
        plugin.logger.info("[WorldCommands] handleCreate: Executing world create command")

        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val name = StringArgumentType.getString(ctx, "name")

        plugin.logger.info("[WorldCommands] handleCreate: Player ${player.name} creating world '$name' with type NORMAL")
        player.sendMessage(Component.text("Creating world...", NamedTextColor.YELLOW))

        worldManager.createWorld(player, name, WorldType.NORMAL, null).thenAccept { result ->
            result.onSuccess { world ->
                plugin.logger.info("[WorldCommands] handleCreate: World '${world.name}' created successfully for ${player.name}")
                player.scheduler.run(plugin, { _ ->
                    player.sendMessage(
                        Component.text("World '", NamedTextColor.GREEN)
                            .append(Component.text(world.name, NamedTextColor.GOLD))
                            .append(Component.text("' created successfully!", NamedTextColor.GREEN))
                    )
                }, null)
            }.onFailure { error ->
                plugin.logger.warning("[WorldCommands] handleCreate: Failed to create world '$name' for ${player.name}: ${error.message}")
                player.scheduler.run(plugin, { _ ->
                    player.sendMessage(
                        Component.text("Failed to create world: ", NamedTextColor.RED)
                            .append(Component.text(error.message ?: "Unknown error", NamedTextColor.GOLD))
                    )
                }, null)
            }
        }

        return Command.SINGLE_SUCCESS
    }

    private fun handleCreateWithType(ctx: CommandContext<CommandSourceStack>): Int {
        plugin.logger.info("[WorldCommands] handleCreateWithType: Executing world create command with type")

        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val name = StringArgumentType.getString(ctx, "name")
        val typeString = StringArgumentType.getString(ctx, "type")

        plugin.logger.info("[WorldCommands] handleCreateWithType: Player ${player.name} creating world '$name' with type '$typeString'")

        val worldType = parseWorldType(typeString) ?: run {
            plugin.logger.warning("[WorldCommands] handleCreateWithType: Invalid world type '$typeString' provided by ${player.name}")
            player.sendMessage(
                Component.text("Invalid world type. Valid types: ", NamedTextColor.RED)
                    .append(Component.text("normal, flat, amplified, large_biomes, void", NamedTextColor.GOLD))
            )
            return Command.SINGLE_SUCCESS
        }

        plugin.logger.info("[WorldCommands] handleCreateWithType: Starting async world creation for ${player.name}")
        player.sendMessage(Component.text("Creating world...", NamedTextColor.YELLOW))

        worldManager.createWorld(player, name, worldType, null).thenAccept { result ->
            result.onSuccess { world ->
                plugin.logger.info("[WorldCommands] handleCreateWithType: World '${world.name}' created successfully for ${player.name}")
                player.scheduler.run(plugin, { _ ->
                    player.sendMessage(
                        Component.text("World '", NamedTextColor.GREEN)
                            .append(Component.text(world.name, NamedTextColor.GOLD))
                            .append(Component.text("' created successfully!", NamedTextColor.GREEN))
                    )
                }, null)
            }.onFailure { error ->
                plugin.logger.warning("[WorldCommands] handleCreateWithType: Failed to create world '$name' for ${player.name}: ${error.message}")
                player.scheduler.run(plugin, { _ ->
                    player.sendMessage(
                        Component.text("Failed to create world: ", NamedTextColor.RED)
                            .append(Component.text(error.message ?: "Unknown error", NamedTextColor.GOLD))
                    )
                }, null)
            }
        }

        return Command.SINGLE_SUCCESS
    }

    private fun handleCreateWithSeed(ctx: CommandContext<CommandSourceStack>): Int {
        plugin.logger.info("[WorldCommands] handleCreateWithSeed: Executing world create command with seed")

        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val name = StringArgumentType.getString(ctx, "name")
        val typeString = StringArgumentType.getString(ctx, "type")
        val seedString = StringArgumentType.getString(ctx, "seed")

        plugin.logger.info("[WorldCommands] handleCreateWithSeed: Player ${player.name} creating world '$name' with type '$typeString' and seed '$seedString'")

        val worldType = parseWorldType(typeString) ?: run {
            plugin.logger.warning("[WorldCommands] handleCreateWithSeed: Invalid world type '$typeString' provided by ${player.name}")
            player.sendMessage(
                Component.text("Invalid world type. Valid types: ", NamedTextColor.RED)
                    .append(Component.text("normal, flat, amplified, large_biomes, void", NamedTextColor.GOLD))
            )
            return Command.SINGLE_SUCCESS
        }

        val seed = seedString.toLongOrNull() ?: run {
            plugin.logger.warning("[WorldCommands] handleCreateWithSeed: Invalid seed '$seedString' provided by ${player.name}")
            player.sendMessage(Component.text("Invalid seed. Must be a number", NamedTextColor.RED))
            return Command.SINGLE_SUCCESS
        }

        plugin.logger.info("[WorldCommands] handleCreateWithSeed: Starting async world creation for ${player.name} with seed $seed")
        player.sendMessage(Component.text("Creating world...", NamedTextColor.YELLOW))

        worldManager.createWorld(player, name, worldType, seed).thenAccept { result ->
            result.onSuccess { world ->
                plugin.logger.info("[WorldCommands] handleCreateWithSeed: World '${world.name}' created successfully for ${player.name}")
                player.scheduler.run(plugin, { _ ->
                    player.sendMessage(
                        Component.text("World '", NamedTextColor.GREEN)
                            .append(Component.text(world.name, NamedTextColor.GOLD))
                            .append(Component.text("' created successfully!", NamedTextColor.GREEN))
                    )
                }, null)
            }.onFailure { error ->
                plugin.logger.warning("[WorldCommands] handleCreateWithSeed: Failed to create world '$name' for ${player.name}: ${error.message}")
                player.scheduler.run(plugin, { _ ->
                    player.sendMessage(
                        Component.text("Failed to create world: ", NamedTextColor.RED)
                            .append(Component.text(error.message ?: "Unknown error", NamedTextColor.GOLD))
                    )
                }, null)
            }
        }

        return Command.SINGLE_SUCCESS
    }

    private fun handleDelete(ctx: CommandContext<CommandSourceStack>): Int {
        plugin.logger.info("[WorldCommands] handleDelete: Executing world delete command")

        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val name = StringArgumentType.getString(ctx, "name")

        plugin.logger.info("[WorldCommands] handleDelete: Player ${player.name} attempting to delete world '$name'")

        val world = getPlayerWorld(player, name) ?: run {
            plugin.logger.warning("[WorldCommands] handleDelete: Player ${player.name} does not own world '$name'")
            return Command.SINGLE_SUCCESS
        }

        plugin.logger.info("[WorldCommands] handleDelete: Starting async world deletion for ${player.name}")
        player.sendMessage(Component.text("Deleting world...", NamedTextColor.YELLOW))

        worldManager.deleteWorld(world).thenAccept { result ->
            result.onSuccess {
                plugin.logger.info("[WorldCommands] handleDelete: World '$name' deleted successfully for ${player.name}")
                player.scheduler.run(plugin, { _ ->
                    player.sendMessage(
                        Component.text("World '", NamedTextColor.GREEN)
                            .append(Component.text(name, NamedTextColor.GOLD))
                            .append(Component.text("' deleted successfully!", NamedTextColor.GREEN))
                    )
                }, null)
            }.onFailure { error ->
                plugin.logger.warning("[WorldCommands] handleDelete: Failed to delete world '$name' for ${player.name}: ${error.message}")
                player.scheduler.run(plugin, { _ ->
                    player.sendMessage(
                        Component.text("Failed to delete world: ", NamedTextColor.RED)
                            .append(Component.text(error.message ?: "Unknown error", NamedTextColor.GOLD))
                    )
                }, null)
            }
        }

        return Command.SINGLE_SUCCESS
    }

    private fun handleList(ctx: CommandContext<CommandSourceStack>): Int {
        plugin.logger.info("[WorldCommands] handleList: Executing world list command")

        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)

        plugin.logger.info("[WorldCommands] handleList: Player ${player.name} listing their worlds")
        val worlds = dataManager.getWorldsByOwner(player.uniqueId)

        if (worlds.isEmpty()) {
            plugin.logger.info("[WorldCommands] handleList: Player ${player.name} has no worlds")
            player.sendMessage(Component.text("You don't own any worlds", NamedTextColor.YELLOW))
            return Command.SINGLE_SUCCESS
        }

        plugin.logger.info("[WorldCommands] handleList: Player ${player.name} has ${worlds.size} world(s)")
        player.sendMessage(Component.text("Your worlds:", NamedTextColor.GREEN))
        worlds.forEach { world ->
            player.sendMessage(
                Component.text("  - ", NamedTextColor.GRAY)
                    .append(Component.text(world.name, NamedTextColor.GOLD))
                    .append(Component.text(" (", NamedTextColor.GRAY))
                    .append(Component.text(world.worldType.name.lowercase(), NamedTextColor.YELLOW))
                    .append(Component.text(")", NamedTextColor.GRAY))
            )
        }

        plugin.logger.info("[WorldCommands] handleList: Command completed successfully for ${player.name}")
        return Command.SINGLE_SUCCESS
    }

    private fun handleTeleport(ctx: CommandContext<CommandSourceStack>): Int {
        plugin.logger.info("[WorldCommands] handleTeleport: Executing world teleport command")

        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val name = StringArgumentType.getString(ctx, "name")

        plugin.logger.info("[WorldCommands] handleTeleport: Player ${player.name} attempting to teleport to world '$name'")

        val world = getPlayerWorld(player, name) ?: run {
            plugin.logger.warning("[WorldCommands] handleTeleport: Player ${player.name} does not own world '$name'")
            return Command.SINGLE_SUCCESS
        }

        plugin.logger.info("[WorldCommands] handleTeleport: Starting async teleport for ${player.name} to world '$name'")
        player.sendMessage(Component.text("Teleporting...", NamedTextColor.YELLOW))

        worldManager.teleportToWorld(player, world).thenAccept { success ->
            player.scheduler.run(plugin, { _ ->
                if (success) {
                    plugin.logger.info("[WorldCommands] handleTeleport: Player ${player.name} teleported successfully to '$name'")
                    player.sendMessage(
                        Component.text("Teleported to ", NamedTextColor.GREEN)
                            .append(Component.text(world.name, NamedTextColor.GOLD))
                    )
                } else {
                    plugin.logger.warning("[WorldCommands] handleTeleport: Failed to teleport ${player.name} to '$name'")
                    player.sendMessage(Component.text("Failed to teleport to world", NamedTextColor.RED))
                }
            }, null)
        }

        return Command.SINGLE_SUCCESS
    }

    private fun handleVisit(ctx: CommandContext<CommandSourceStack>): Int {
        plugin.logger.info("[WorldCommands] handleVisit: Executing world visit command")

        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val ownerName = StringArgumentType.getString(ctx, "owner")
        val worldName = StringArgumentType.getString(ctx, "name")

        plugin.logger.info("[WorldCommands] handleVisit: Player ${player.name} attempting to visit world '$worldName' owned by '$ownerName'")

        val owner = Bukkit.getOfflinePlayer(ownerName)
        if (!owner.hasPlayedBefore() && !owner.isOnline) {
            plugin.logger.warning("[WorldCommands] handleVisit: Owner '$ownerName' not found")
            player.sendMessage(
                Component.text("Player '", NamedTextColor.RED)
                    .append(Component.text(ownerName, NamedTextColor.GOLD))
                    .append(Component.text("' not found", NamedTextColor.RED))
            )
            return Command.SINGLE_SUCCESS
        }

        val world = dataManager.getWorldsByOwner(owner.uniqueId)
            .firstOrNull { it.name.equals(worldName, ignoreCase = true) }

        if (world == null) {
            plugin.logger.warning("[WorldCommands] handleVisit: World '$worldName' not found for owner '$ownerName'")
            player.sendMessage(
                Component.text("$ownerName doesn't own a world named '", NamedTextColor.RED)
                    .append(Component.text(worldName, NamedTextColor.GOLD))
                    .append(Component.text("'", NamedTextColor.RED))
            )
            return Command.SINGLE_SUCCESS
        }

        if (!inviteManager.hasAccess(player.uniqueId, world)) {
            plugin.logger.warning("[WorldCommands] handleVisit: Player ${player.name} does not have access to world '$worldName'")
            player.sendMessage(Component.text("You don't have access to this world", NamedTextColor.RED))
            return Command.SINGLE_SUCCESS
        }

        plugin.logger.info("[WorldCommands] handleVisit: Starting async teleport for ${player.name} to '$worldName'")
        player.sendMessage(Component.text("Teleporting...", NamedTextColor.YELLOW))

        worldManager.teleportToWorld(player, world).thenAccept { success ->
            player.scheduler.run(plugin, { _ ->
                if (success) {
                    plugin.logger.info("[WorldCommands] handleVisit: Player ${player.name} teleported successfully to '$worldName'")
                    player.sendMessage(
                        Component.text("Teleported to ", NamedTextColor.GREEN)
                            .append(Component.text(world.name, NamedTextColor.GOLD))
                    )
                } else {
                    plugin.logger.warning("[WorldCommands] handleVisit: Failed to teleport ${player.name} to '$worldName'")
                    player.sendMessage(Component.text("Failed to teleport to world", NamedTextColor.RED))
                }
            }, null)
        }

        return Command.SINGLE_SUCCESS
    }

    private fun handleInvite(ctx: CommandContext<CommandSourceStack>): Int {
        plugin.logger.info("[WorldCommands] handleInvite: Executing world invite command")

        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val targetName = StringArgumentType.getString(ctx, "player")

        plugin.logger.info("[WorldCommands] handleInvite: Player ${player.name} attempting to invite '$targetName'")

        val target = Bukkit.getPlayer(targetName)
        if (target == null) {
            plugin.logger.warning("[WorldCommands] handleInvite: Target player '$targetName' is not online")
            player.sendMessage(
                Component.text("Player '", NamedTextColor.RED)
                    .append(Component.text(targetName, NamedTextColor.GOLD))
                    .append(Component.text("' is not online", NamedTextColor.RED))
            )
            return Command.SINGLE_SUCCESS
        }

        val world = getCurrentWorld(player) ?: run {
            plugin.logger.warning("[WorldCommands] handleInvite: Player ${player.name} not in their own world")
            player.sendMessage(
                Component.text("You must specify a world name or be in one of your worlds", NamedTextColor.RED)
            )
            return Command.SINGLE_SUCCESS
        }

        plugin.logger.info("[WorldCommands] handleInvite: Sending invite for world '${world.name}' to ${target.name}")
        inviteManager.sendInvite(world, player, target).onFailure { error ->
            plugin.logger.warning("[WorldCommands] handleInvite: Failed to send invite: ${error.message}")
            player.sendMessage(
                Component.text("Failed to send invite: ", NamedTextColor.RED)
                    .append(Component.text(error.message ?: "Unknown error", NamedTextColor.GOLD))
            )
        }

        plugin.logger.info("[WorldCommands] handleInvite: Command completed for ${player.name}")
        return Command.SINGLE_SUCCESS
    }

    private fun handleInviteWithWorld(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val targetName = StringArgumentType.getString(ctx, "player")
        val worldName = StringArgumentType.getString(ctx, "world")

        val target = Bukkit.getPlayer(targetName)
        if (target == null) {
            player.sendMessage(
                Component.text("Player '", NamedTextColor.RED)
                    .append(Component.text(targetName, NamedTextColor.GOLD))
                    .append(Component.text("' is not online", NamedTextColor.RED))
            )
            return Command.SINGLE_SUCCESS
        }

        val world = getPlayerWorld(player, worldName) ?: return Command.SINGLE_SUCCESS

        inviteManager.sendInvite(world, player, target).onFailure { error ->
            player.sendMessage(
                Component.text("Failed to send invite: ", NamedTextColor.RED)
                    .append(Component.text(error.message ?: "Unknown error", NamedTextColor.GOLD))
            )
        }

        return Command.SINGLE_SUCCESS
    }

    private fun handleKick(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val targetName = StringArgumentType.getString(ctx, "player")

        val target = Bukkit.getOfflinePlayer(targetName)
        if (!target.hasPlayedBefore() && !target.isOnline) {
            player.sendMessage(
                Component.text("Player '", NamedTextColor.RED)
                    .append(Component.text(targetName, NamedTextColor.GOLD))
                    .append(Component.text("' not found", NamedTextColor.RED))
            )
            return Command.SINGLE_SUCCESS
        }

        val world = getCurrentWorld(player) ?: run {
            player.sendMessage(
                Component.text("You must specify a world name or be in one of your worlds", NamedTextColor.RED)
            )
            return Command.SINGLE_SUCCESS
        }

        inviteManager.kickPlayer(world, player, target.uniqueId).onFailure { error ->
            player.sendMessage(
                Component.text("Failed to kick player: ", NamedTextColor.RED)
                    .append(Component.text(error.message ?: "Unknown error", NamedTextColor.GOLD))
            )
        }

        return Command.SINGLE_SUCCESS
    }

    private fun handleKickWithWorld(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val targetName = StringArgumentType.getString(ctx, "player")
        val worldName = StringArgumentType.getString(ctx, "world")

        val target = Bukkit.getOfflinePlayer(targetName)
        if (!target.hasPlayedBefore() && !target.isOnline) {
            player.sendMessage(
                Component.text("Player '", NamedTextColor.RED)
                    .append(Component.text(targetName, NamedTextColor.GOLD))
                    .append(Component.text("' not found", NamedTextColor.RED))
            )
            return Command.SINGLE_SUCCESS
        }

        val world = getPlayerWorld(player, worldName) ?: return Command.SINGLE_SUCCESS

        inviteManager.kickPlayer(world, player, target.uniqueId).onFailure { error ->
            player.sendMessage(
                Component.text("Failed to kick player: ", NamedTextColor.RED)
                    .append(Component.text(error.message ?: "Unknown error", NamedTextColor.GOLD))
            )
        }

        return Command.SINGLE_SUCCESS
    }

    private fun handleAccept(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val ownerName = StringArgumentType.getString(ctx, "owner")
        val worldName = StringArgumentType.getString(ctx, "world")

        val owner = Bukkit.getOfflinePlayer(ownerName)
        if (!owner.hasPlayedBefore() && !owner.isOnline) {
            player.sendMessage(
                Component.text("Player '", NamedTextColor.RED)
                    .append(Component.text(ownerName, NamedTextColor.GOLD))
                    .append(Component.text("' not found", NamedTextColor.RED))
            )
            return Command.SINGLE_SUCCESS
        }

        val world = dataManager.getWorldsByOwner(owner.uniqueId)
            .firstOrNull { it.name.equals(worldName, ignoreCase = true) }

        if (world == null) {
            player.sendMessage(
                Component.text("$ownerName doesn't own a world named '", NamedTextColor.RED)
                    .append(Component.text(worldName, NamedTextColor.GOLD))
                    .append(Component.text("'", NamedTextColor.RED))
            )
            return Command.SINGLE_SUCCESS
        }

        val invite = dataManager.getInvite(world.id, player.uniqueId)
        if (invite == null) {
            player.sendMessage(Component.text("You don't have a pending invite to this world", NamedTextColor.RED))
            return Command.SINGLE_SUCCESS
        }

        inviteManager.acceptInvite(invite, player).onFailure { error ->
            player.sendMessage(
                Component.text("Failed to accept invite: ", NamedTextColor.RED)
                    .append(Component.text(error.message ?: "Unknown error", NamedTextColor.GOLD))
            )
        }

        return Command.SINGLE_SUCCESS
    }

    private fun handleDeny(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val ownerName = StringArgumentType.getString(ctx, "owner")
        val worldName = StringArgumentType.getString(ctx, "world")

        val owner = Bukkit.getOfflinePlayer(ownerName)
        if (!owner.hasPlayedBefore() && !owner.isOnline) {
            player.sendMessage(
                Component.text("Player '", NamedTextColor.RED)
                    .append(Component.text(ownerName, NamedTextColor.GOLD))
                    .append(Component.text("' not found", NamedTextColor.RED))
            )
            return Command.SINGLE_SUCCESS
        }

        val world = dataManager.getWorldsByOwner(owner.uniqueId)
            .firstOrNull { it.name.equals(worldName, ignoreCase = true) }

        if (world == null) {
            player.sendMessage(
                Component.text("$ownerName doesn't own a world named '", NamedTextColor.RED)
                    .append(Component.text(worldName, NamedTextColor.GOLD))
                    .append(Component.text("'", NamedTextColor.RED))
            )
            return Command.SINGLE_SUCCESS
        }

        val invite = dataManager.getInvite(world.id, player.uniqueId)
        if (invite == null) {
            player.sendMessage(Component.text("You don't have a pending invite to this world", NamedTextColor.RED))
            return Command.SINGLE_SUCCESS
        }

        inviteManager.denyInvite(invite, player).onFailure { error ->
            player.sendMessage(
                Component.text("Failed to deny invite: ", NamedTextColor.RED)
                    .append(Component.text(error.message ?: "Unknown error", NamedTextColor.GOLD))
            )
        }

        return Command.SINGLE_SUCCESS
    }

    private fun handleTransfer(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val targetName = StringArgumentType.getString(ctx, "player")
        val worldName = StringArgumentType.getString(ctx, "world")

        val target = Bukkit.getOfflinePlayer(targetName)
        if (!target.hasPlayedBefore() && !target.isOnline) {
            player.sendMessage(
                Component.text("Player '", NamedTextColor.RED)
                    .append(Component.text(targetName, NamedTextColor.GOLD))
                    .append(Component.text("' not found", NamedTextColor.RED))
            )
            return Command.SINGLE_SUCCESS
        }

        val world = getPlayerWorld(player, worldName) ?: return Command.SINGLE_SUCCESS

        inviteManager.transferOwnership(world, player, target.uniqueId).onFailure { error ->
            player.sendMessage(
                Component.text("Failed to transfer ownership: ", NamedTextColor.RED)
                    .append(Component.text(error.message ?: "Unknown error", NamedTextColor.GOLD))
            )
        }

        return Command.SINGLE_SUCCESS
    }

    private fun handleInvites(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)

        val invites = inviteManager.getPendingInvites(player.uniqueId)

        if (invites.isEmpty()) {
            player.sendMessage(Component.text("You have no pending invites", NamedTextColor.YELLOW))
            return Command.SINGLE_SUCCESS
        }

        player.sendMessage(Component.text("Pending invites:", NamedTextColor.GREEN))
        invites.forEach { invite ->
            player.sendMessage(
                Component.text("  - ", NamedTextColor.GRAY)
                    .append(Component.text(invite.worldName, NamedTextColor.GOLD))
                    .append(Component.text(" from ", NamedTextColor.GRAY))
                    .append(Component.text(invite.ownerName, NamedTextColor.GOLD))
            )
            player.sendMessage(
                Component.text("    Use ", NamedTextColor.GRAY)
                    .append(Component.text("/world accept ${invite.ownerName} ${invite.worldName}", NamedTextColor.YELLOW))
            )
        }

        return Command.SINGLE_SUCCESS
    }

    private fun handleSpawn(ctx: CommandContext<CommandSourceStack>): Int {
        plugin.logger.info("[WorldCommands] handleSpawn: Executing world spawn command")

        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)

        plugin.logger.info("[WorldCommands] handleSpawn: Player ${player.name} attempting to teleport to default world spawn")

        val defaultWorld = Bukkit.getWorlds().firstOrNull()
        if (defaultWorld == null) {
            plugin.logger.warning("[WorldCommands] handleSpawn: No default world found")
            player.sendMessage(Component.text("Default world not found", NamedTextColor.RED))
            return Command.SINGLE_SUCCESS
        }

        player.sendMessage(Component.text("Teleporting to spawn...", NamedTextColor.YELLOW))

        player.teleportAsync(defaultWorld.spawnLocation).thenAccept { success ->
            player.scheduler.run(plugin, { _ ->
                if (success) {
                    plugin.logger.info("[WorldCommands] handleSpawn: Player ${player.name} teleported successfully to spawn")
                    player.sendMessage(
                        Component.text("Teleported to spawn", NamedTextColor.GREEN)
                    )
                } else {
                    plugin.logger.warning("[WorldCommands] handleSpawn: Failed to teleport ${player.name} to spawn")
                    player.sendMessage(Component.text("Failed to teleport to spawn", NamedTextColor.RED))
                }
            }, null)
        }

        return Command.SINGLE_SUCCESS
    }

    private fun handleMenu(ctx: CommandContext<CommandSourceStack>): Int {
        plugin.logger.info("[WorldCommands] handleMenu: Executing world menu command")

        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)

        plugin.logger.info("[WorldCommands] handleMenu: Opening main menu GUI for ${player.name}")
        mainMenuGui.open(player)

        plugin.logger.info("[WorldCommands] handleMenu: Command completed for ${player.name}")
        return Command.SINGLE_SUCCESS
    }

    private fun handleHelp(ctx: CommandContext<CommandSourceStack>): Int {
        val sender = ctx.source.sender

        sender.sendMessage(Component.text("World Management Commands", NamedTextColor.GOLD))
        sender.sendMessage(
            Component.text("/world create <name> [type] [seed]", NamedTextColor.GRAY)
                .append(Component.text(" - Create a world", NamedTextColor.YELLOW))
        )
        sender.sendMessage(
            Component.text("/world delete <name>", NamedTextColor.GRAY)
                .append(Component.text(" - Delete your world", NamedTextColor.YELLOW))
        )
        sender.sendMessage(
            Component.text("/world list", NamedTextColor.GRAY)
                .append(Component.text(" - List your worlds", NamedTextColor.YELLOW))
        )
        sender.sendMessage(
            Component.text("/world tp <name>", NamedTextColor.GRAY)
                .append(Component.text(" - Teleport to your world", NamedTextColor.YELLOW))
        )
        sender.sendMessage(
            Component.text("/world visit <owner> <name>", NamedTextColor.GRAY)
                .append(Component.text(" - Visit a world", NamedTextColor.YELLOW))
        )
        sender.sendMessage(
            Component.text("/world invite <player> [world]", NamedTextColor.GRAY)
                .append(Component.text(" - Invite a player", NamedTextColor.YELLOW))
        )
        sender.sendMessage(
            Component.text("/world kick <player> [world]", NamedTextColor.GRAY)
                .append(Component.text(" - Kick a player", NamedTextColor.YELLOW))
        )
        sender.sendMessage(
            Component.text("/world accept <owner> <world>", NamedTextColor.GRAY)
                .append(Component.text(" - Accept an invite", NamedTextColor.YELLOW))
        )
        sender.sendMessage(
            Component.text("/world deny <owner> <world>", NamedTextColor.GRAY)
                .append(Component.text(" - Deny an invite", NamedTextColor.YELLOW))
        )
        sender.sendMessage(
            Component.text("/world transfer <player> <world>", NamedTextColor.GRAY)
                .append(Component.text(" - Transfer ownership", NamedTextColor.YELLOW))
        )
        sender.sendMessage(
            Component.text("/world invites", NamedTextColor.GRAY)
                .append(Component.text(" - List pending invites", NamedTextColor.YELLOW))
        )
        sender.sendMessage(
            Component.text("/world spawn", NamedTextColor.GRAY)
                .append(Component.text(" - Return to default spawn", NamedTextColor.YELLOW))
        )
        sender.sendMessage(
            Component.text("/world menu", NamedTextColor.GRAY)
                .append(Component.text(" - Open the GUI menu", NamedTextColor.YELLOW))
        )

        return Command.SINGLE_SUCCESS
    }

    // ========================
    // Suggestion Providers
    // ========================

    private fun suggestWorldTypes(
        ctx: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        listOf("normal", "flat", "amplified", "large_biomes", "void")
            .filter { it.startsWith(builder.remainingLowerCase) }
            .forEach { builder.suggest(it) }
        return builder.buildFuture()
    }

    private fun suggestOwnedWorlds(
        ctx: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val sender = ctx.source.sender
        if (sender is Player) {
            dataManager.getWorldsByOwner(sender.uniqueId)
                .map { it.name }
                .filter { it.lowercase().startsWith(builder.remainingLowerCase) }
                .forEach { builder.suggest(it) }
        }
        return builder.buildFuture()
    }

    private fun suggestPlayersWithWorlds(
        ctx: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        Bukkit.getOnlinePlayers()
            .filter { dataManager.getWorldsByOwner(it.uniqueId).isNotEmpty() }
            .map { it.name }
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

    private fun suggestOnlinePlayers(
        ctx: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        Bukkit.getOnlinePlayers()
            .map { it.name }
            .filter { it.lowercase().startsWith(builder.remainingLowerCase) }
            .forEach { builder.suggest(it) }
        return builder.buildFuture()
    }

    private fun suggestInviteOwners(
        ctx: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val sender = ctx.source.sender
        if (sender is Player) {
            inviteManager.getPendingInvites(sender.uniqueId)
                .map { it.ownerName }
                .distinct()
                .filter { it.lowercase().startsWith(builder.remainingLowerCase) }
                .forEach { builder.suggest(it) }
        }
        return builder.buildFuture()
    }

    private fun suggestInviteWorlds(
        ctx: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val sender = ctx.source.sender
        if (sender is Player) {
            val ownerName = StringArgumentType.getString(ctx, "owner")
            inviteManager.getPendingInvites(sender.uniqueId)
                .filter { it.ownerName.equals(ownerName, ignoreCase = true) }
                .map { it.worldName }
                .filter { it.lowercase().startsWith(builder.remainingLowerCase) }
                .forEach { builder.suggest(it) }
        }
        return builder.buildFuture()
    }

    // ========================
    // Helper Methods
    // ========================

    private fun sendPlayerOnlyError(ctx: CommandContext<CommandSourceStack>): Int {
        plugin.logger.warning("[WorldCommands] Non-player attempted to execute player-only command")
        ctx.source.sender.sendMessage(
            Component.text("This command can only be used by players", NamedTextColor.RED)
        )
        return Command.SINGLE_SUCCESS
    }

    private fun getPlayerWorld(player: Player, name: String): PlayerWorld? {
        val world = dataManager.getWorldsByOwner(player.uniqueId)
            .firstOrNull { it.name.equals(name, ignoreCase = true) }

        if (world == null) {
            player.sendMessage(
                Component.text("You don't own a world named '", NamedTextColor.RED)
                    .append(Component.text(name, NamedTextColor.GOLD))
                    .append(Component.text("'", NamedTextColor.RED))
            )
        }

        return world
    }

    private fun getCurrentWorld(player: Player): PlayerWorld? {
        val currentWorld = worldManager.getPlayerWorldFromBukkitWorld(player.world) ?: return null
        if (currentWorld.ownerUuid != player.uniqueId) {
            return null
        }
        return currentWorld
    }

    private fun parseWorldType(type: String): WorldType? {
        return when (type.lowercase()) {
            "normal" -> WorldType.NORMAL
            "flat" -> WorldType.FLAT
            "amplified" -> WorldType.AMPLIFIED
            "large_biomes" -> WorldType.LARGE_BIOMES
            "void" -> WorldType.VOID
            else -> null
        }
    }
}
