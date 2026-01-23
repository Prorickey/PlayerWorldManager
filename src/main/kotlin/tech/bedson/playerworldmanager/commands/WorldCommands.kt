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
import tech.bedson.playerworldmanager.models.WorldRole
import tech.bedson.playerworldmanager.models.WorldType
import tech.bedson.playerworldmanager.utils.DebugLogger
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
    private val debugLogger = DebugLogger(plugin, "WorldCommands")

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
            // /world leave
            .then(Commands.literal("leave")
                .executes(::handleLeave)
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
            // /world role <player> <role> [world] - Set a player's role (owner only)
            .then(Commands.literal("role")
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests(::suggestWorldMembers)
                    .then(Commands.argument("role", StringArgumentType.word())
                        .suggests(::suggestRoles)
                        .then(Commands.argument("world", StringArgumentType.word())
                            .suggests(::suggestOwnedWorlds)
                            .executes(::handleRoleWithWorld)
                        )
                        .executes(::handleRole)
                    )
                )
            )
            // /world visibility [world] - Toggle public/private (owner only)
            .then(Commands.literal("visibility")
                .then(Commands.argument("world", StringArgumentType.word())
                    .suggests(::suggestOwnedWorlds)
                    .executes(::handleVisibilityWithWorld)
                )
                .executes(::handleVisibility)
            )
            // /world publicrole <role> [world] - Set public join role (owner only)
            .then(Commands.literal("publicrole")
                .then(Commands.argument("role", StringArgumentType.word())
                    .suggests(::suggestPublicRoles)
                    .then(Commands.argument("world", StringArgumentType.word())
                        .suggests(::suggestOwnedWorlds)
                        .executes(::handlePublicRoleWithWorld)
                    )
                    .executes(::handlePublicRole)
                )
            )
            // /world members [world] - View all player roles in a world
            .then(Commands.literal("members")
                .then(Commands.argument("world", StringArgumentType.word())
                    .suggests(::suggestOwnedWorlds)
                    .executes(::handleMembersWithWorld)
                )
                .executes(::handleMembers)
            )
            // /world browse - Browse public worlds
            .then(Commands.literal("browse")
                .executes(::handleBrowse)
            )
            // Default (no args) opens main menu GUI
            .executes(::handleMenu)
            .build()
    }

    // ========================
    // Command Handlers
    // ========================

    private fun handleCreate(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleCreate", "sender" to ctx.source.sender.name)
        plugin.logger.info("[WorldCommands] handleCreate: Executing world create command")

        val player = ctx.source.sender as? Player
        debugLogger.debug("Player check", "isPlayer" to (player != null), "playerName" to player?.name)
        if (player == null) {
            debugLogger.debugMethodExit("handleCreate", "player-only error")
            return sendPlayerOnlyError(ctx)
        }

        val name = StringArgumentType.getString(ctx, "name")
        debugLogger.debug("Parsed arguments", "name" to name, "type" to "NORMAL", "seed" to null)

        plugin.logger.info("[WorldCommands] handleCreate: Player ${player.name} creating world '$name' with type NORMAL")
        player.sendMessage(Component.text("Creating world...", NamedTextColor.YELLOW))

        debugLogger.debug("Calling worldManager.createWorld", "player" to player.name, "name" to name, "type" to "NORMAL")
        worldManager.createWorld(player, name, WorldType.NORMAL, null).thenAccept { result ->
            result.onSuccess { world ->
                debugLogger.debug("World creation succeeded", "worldName" to world.name, "worldId" to world.id)
                plugin.logger.info("[WorldCommands] handleCreate: World '${world.name}' created successfully for ${player.name}")
                player.scheduler.run(plugin, { _ ->
                    player.sendMessage(
                        Component.text("World '", NamedTextColor.GREEN)
                            .append(Component.text(world.name, NamedTextColor.GOLD))
                            .append(Component.text("' created! Use ", NamedTextColor.GREEN))
                            .append(Component.text("/world tp ${world.name}", NamedTextColor.YELLOW))
                            .append(Component.text(" to visit it.", NamedTextColor.GREEN))
                    )
                }, null)
            }.onFailure { error ->
                debugLogger.debug("World creation failed", "error" to error.message)
                plugin.logger.warning("[WorldCommands] handleCreate: Failed to create world '$name' for ${player.name}: ${error.message}")
                player.scheduler.run(plugin, { _ ->
                    player.sendMessage(
                        Component.text("Failed to create world: ", NamedTextColor.RED)
                            .append(Component.text(error.message ?: "Unknown error", NamedTextColor.GOLD))
                    )
                }, null)
            }
        }

        debugLogger.debugMethodExit("handleCreate", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleCreateWithType(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleCreateWithType", "sender" to ctx.source.sender.name)
        plugin.logger.info("[WorldCommands] handleCreateWithType: Executing world create command with type")

        val player = ctx.source.sender as? Player
        debugLogger.debug("Player check", "isPlayer" to (player != null), "playerName" to player?.name)
        if (player == null) {
            debugLogger.debugMethodExit("handleCreateWithType", "player-only error")
            return sendPlayerOnlyError(ctx)
        }

        val name = StringArgumentType.getString(ctx, "name")
        val typeString = StringArgumentType.getString(ctx, "type")
        debugLogger.debug("Parsed arguments", "name" to name, "typeString" to typeString)

        plugin.logger.info("[WorldCommands] handleCreateWithType: Player ${player.name} creating world '$name' with type '$typeString'")

        val worldType = parseWorldType(typeString)
        debugLogger.debug("Parsed world type", "typeString" to typeString, "worldType" to worldType)
        if (worldType == null) {
            plugin.logger.warning("[WorldCommands] handleCreateWithType: Invalid world type '$typeString' provided by ${player.name}")
            player.sendMessage(
                Component.text("Invalid world type. Valid types: ", NamedTextColor.RED)
                    .append(Component.text("normal, flat, amplified, large_biomes, void", NamedTextColor.GOLD))
            )
            debugLogger.debugMethodExit("handleCreateWithType", "invalid world type")
            return Command.SINGLE_SUCCESS
        }

        plugin.logger.info("[WorldCommands] handleCreateWithType: Starting async world creation for ${player.name}")
        player.sendMessage(Component.text("Creating world...", NamedTextColor.YELLOW))

        debugLogger.debug("Calling worldManager.createWorld", "player" to player.name, "name" to name, "type" to worldType)
        worldManager.createWorld(player, name, worldType, null).thenAccept { result ->
            result.onSuccess { world ->
                debugLogger.debug("World creation succeeded", "worldName" to world.name, "worldId" to world.id)
                plugin.logger.info("[WorldCommands] handleCreateWithType: World '${world.name}' created successfully for ${player.name}")
                player.scheduler.run(plugin, { _ ->
                    player.sendMessage(
                        Component.text("World '", NamedTextColor.GREEN)
                            .append(Component.text(world.name, NamedTextColor.GOLD))
                            .append(Component.text("' created! Use ", NamedTextColor.GREEN))
                            .append(Component.text("/world tp ${world.name}", NamedTextColor.YELLOW))
                            .append(Component.text(" to visit it.", NamedTextColor.GREEN))
                    )
                }, null)
            }.onFailure { error ->
                debugLogger.debug("World creation failed", "error" to error.message)
                plugin.logger.warning("[WorldCommands] handleCreateWithType: Failed to create world '$name' for ${player.name}: ${error.message}")
                player.scheduler.run(plugin, { _ ->
                    player.sendMessage(
                        Component.text("Failed to create world: ", NamedTextColor.RED)
                            .append(Component.text(error.message ?: "Unknown error", NamedTextColor.GOLD))
                    )
                }, null)
            }
        }

        debugLogger.debugMethodExit("handleCreateWithType", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleCreateWithSeed(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleCreateWithSeed", "sender" to ctx.source.sender.name)
        plugin.logger.info("[WorldCommands] handleCreateWithSeed: Executing world create command with seed")

        val player = ctx.source.sender as? Player
        debugLogger.debug("Player check", "isPlayer" to (player != null), "playerName" to player?.name)
        if (player == null) {
            debugLogger.debugMethodExit("handleCreateWithSeed", "player-only error")
            return sendPlayerOnlyError(ctx)
        }

        val name = StringArgumentType.getString(ctx, "name")
        val typeString = StringArgumentType.getString(ctx, "type")
        val seedString = StringArgumentType.getString(ctx, "seed")
        debugLogger.debug("Parsed arguments", "name" to name, "typeString" to typeString, "seedString" to seedString)

        plugin.logger.info("[WorldCommands] handleCreateWithSeed: Player ${player.name} creating world '$name' with type '$typeString' and seed '$seedString'")

        val worldType = parseWorldType(typeString)
        debugLogger.debug("Parsed world type", "typeString" to typeString, "worldType" to worldType)
        if (worldType == null) {
            plugin.logger.warning("[WorldCommands] handleCreateWithSeed: Invalid world type '$typeString' provided by ${player.name}")
            player.sendMessage(
                Component.text("Invalid world type. Valid types: ", NamedTextColor.RED)
                    .append(Component.text("normal, flat, amplified, large_biomes, void", NamedTextColor.GOLD))
            )
            debugLogger.debugMethodExit("handleCreateWithSeed", "invalid world type")
            return Command.SINGLE_SUCCESS
        }

        val seed = seedString.toLongOrNull()
        debugLogger.debug("Parsed seed", "seedString" to seedString, "seed" to seed)
        if (seed == null) {
            plugin.logger.warning("[WorldCommands] handleCreateWithSeed: Invalid seed '$seedString' provided by ${player.name}")
            player.sendMessage(Component.text("Invalid seed. Must be a number", NamedTextColor.RED))
            debugLogger.debugMethodExit("handleCreateWithSeed", "invalid seed")
            return Command.SINGLE_SUCCESS
        }

        plugin.logger.info("[WorldCommands] handleCreateWithSeed: Starting async world creation for ${player.name} with seed $seed")
        player.sendMessage(Component.text("Creating world...", NamedTextColor.YELLOW))

        debugLogger.debug("Calling worldManager.createWorld", "player" to player.name, "name" to name, "type" to worldType, "seed" to seed)
        worldManager.createWorld(player, name, worldType, seed).thenAccept { result ->
            result.onSuccess { world ->
                debugLogger.debug("World creation succeeded", "worldName" to world.name, "worldId" to world.id, "seed" to seed)
                plugin.logger.info("[WorldCommands] handleCreateWithSeed: World '${world.name}' created successfully for ${player.name}")
                player.scheduler.run(plugin, { _ ->
                    player.sendMessage(
                        Component.text("World '", NamedTextColor.GREEN)
                            .append(Component.text(world.name, NamedTextColor.GOLD))
                            .append(Component.text("' created! Use ", NamedTextColor.GREEN))
                            .append(Component.text("/world tp ${world.name}", NamedTextColor.YELLOW))
                            .append(Component.text(" to visit it.", NamedTextColor.GREEN))
                    )
                }, null)
            }.onFailure { error ->
                debugLogger.debug("World creation failed", "error" to error.message)
                plugin.logger.warning("[WorldCommands] handleCreateWithSeed: Failed to create world '$name' for ${player.name}: ${error.message}")
                player.scheduler.run(plugin, { _ ->
                    player.sendMessage(
                        Component.text("Failed to create world: ", NamedTextColor.RED)
                            .append(Component.text(error.message ?: "Unknown error", NamedTextColor.GOLD))
                    )
                }, null)
            }
        }

        debugLogger.debugMethodExit("handleCreateWithSeed", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleDelete(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleDelete", "sender" to ctx.source.sender.name)
        plugin.logger.info("[WorldCommands] handleDelete: Executing world delete command")

        val player = ctx.source.sender as? Player
        debugLogger.debug("Player check", "isPlayer" to (player != null), "playerName" to player?.name)
        if (player == null) {
            debugLogger.debugMethodExit("handleDelete", "player-only error")
            return sendPlayerOnlyError(ctx)
        }

        val name = StringArgumentType.getString(ctx, "name")
        debugLogger.debug("Parsed arguments", "name" to name)

        plugin.logger.info("[WorldCommands] handleDelete: Player ${player.name} attempting to delete world '$name'")

        val world = getPlayerWorld(player, name)
        debugLogger.debug("World lookup result", "worldName" to name, "found" to (world != null), "worldId" to world?.id)
        if (world == null) {
            plugin.logger.warning("[WorldCommands] handleDelete: Player ${player.name} does not own world '$name'")
            debugLogger.debugMethodExit("handleDelete", "world not found")
            return Command.SINGLE_SUCCESS
        }

        plugin.logger.info("[WorldCommands] handleDelete: Starting async world deletion for ${player.name}")
        player.sendMessage(Component.text("Deleting world...", NamedTextColor.YELLOW))

        debugLogger.debug("Calling worldManager.deleteWorld", "worldName" to world.name, "worldId" to world.id)
        worldManager.deleteWorld(world).thenAccept { result ->
            result.onSuccess {
                debugLogger.debug("World deletion succeeded", "worldName" to name)
                plugin.logger.info("[WorldCommands] handleDelete: World '$name' deleted successfully for ${player.name}")
                player.scheduler.run(plugin, { _ ->
                    player.sendMessage(
                        Component.text("World '", NamedTextColor.GREEN)
                            .append(Component.text(name, NamedTextColor.GOLD))
                            .append(Component.text("' deleted successfully!", NamedTextColor.GREEN))
                    )
                }, null)
            }.onFailure { error ->
                debugLogger.debug("World deletion failed", "worldName" to name, "error" to error.message)
                plugin.logger.warning("[WorldCommands] handleDelete: Failed to delete world '$name' for ${player.name}: ${error.message}")
                player.scheduler.run(plugin, { _ ->
                    player.sendMessage(
                        Component.text("Failed to delete world: ", NamedTextColor.RED)
                            .append(Component.text(error.message ?: "Unknown error", NamedTextColor.GOLD))
                    )
                }, null)
            }
        }

        debugLogger.debugMethodExit("handleDelete", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleList(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleList", "sender" to ctx.source.sender.name)
        plugin.logger.info("[WorldCommands] handleList: Executing world list command")

        val player = ctx.source.sender as? Player
        debugLogger.debug("Player check", "isPlayer" to (player != null), "playerName" to player?.name)
        if (player == null) {
            debugLogger.debugMethodExit("handleList", "player-only error")
            return sendPlayerOnlyError(ctx)
        }

        plugin.logger.info("[WorldCommands] handleList: Player ${player.name} listing their worlds")
        val worlds = dataManager.getWorldsByOwner(player.uniqueId)
        debugLogger.debug("Retrieved worlds", "playerUuid" to player.uniqueId, "worldCount" to worlds.size)

        if (worlds.isEmpty()) {
            plugin.logger.info("[WorldCommands] handleList: Player ${player.name} has no worlds")
            player.sendMessage(Component.text("You don't own any worlds", NamedTextColor.YELLOW))
            debugLogger.debugMethodExit("handleList", "no worlds")
            return Command.SINGLE_SUCCESS
        }

        plugin.logger.info("[WorldCommands] handleList: Player ${player.name} has ${worlds.size} world(s)")
        debugLogger.debug("Displaying worlds", "worldNames" to worlds.map { it.name })
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
        debugLogger.debugMethodExit("handleList", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleTeleport(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleTeleport", "sender" to ctx.source.sender.name)
        plugin.logger.info("[WorldCommands] handleTeleport: Executing world teleport command")

        val player = ctx.source.sender as? Player
        debugLogger.debug("Player check", "isPlayer" to (player != null), "playerName" to player?.name)
        if (player == null) {
            debugLogger.debugMethodExit("handleTeleport", "player-only error")
            return sendPlayerOnlyError(ctx)
        }

        val name = StringArgumentType.getString(ctx, "name")
        debugLogger.debug("Parsed arguments", "name" to name)

        plugin.logger.info("[WorldCommands] handleTeleport: Player ${player.name} attempting to teleport to world '$name'")

        // Handle "default" as special case for vanilla world
        val isDefaultWorld = name.equals("default", ignoreCase = true)
        debugLogger.debug("Default world check", "name" to name, "isDefault" to isDefaultWorld)
        if (isDefaultWorld) {
            plugin.logger.info("[WorldCommands] handleTeleport: Player ${player.name} teleporting to default (vanilla) world")
            player.sendMessage(Component.text("Teleporting to spawn...", NamedTextColor.YELLOW))

            debugLogger.debug("Calling worldManager.teleportToVanillaWorld", "player" to player.name)
            worldManager.teleportToVanillaWorld(player).thenAccept { success ->
                debugLogger.debug("Vanilla world teleport result", "success" to success)
                player.scheduler.run(plugin, { _ ->
                    if (success) {
                        plugin.logger.info("[WorldCommands] handleTeleport: Player ${player.name} teleported successfully to default world")
                        player.sendMessage(
                            Component.text("Teleported to spawn", NamedTextColor.GREEN)
                        )
                    } else {
                        plugin.logger.warning("[WorldCommands] handleTeleport: Failed to teleport ${player.name} to default world")
                        player.sendMessage(Component.text("Failed to teleport to spawn", NamedTextColor.RED))
                    }
                }, null)
            }

            debugLogger.debugMethodExit("handleTeleport", "default world teleport initiated")
            return Command.SINGLE_SUCCESS
        }

        val world = getPlayerWorld(player, name)
        debugLogger.debug("World lookup result", "worldName" to name, "found" to (world != null), "worldId" to world?.id)
        if (world == null) {
            plugin.logger.warning("[WorldCommands] handleTeleport: Player ${player.name} does not own world '$name'")
            debugLogger.debugMethodExit("handleTeleport", "world not found")
            return Command.SINGLE_SUCCESS
        }

        plugin.logger.info("[WorldCommands] handleTeleport: Starting async teleport for ${player.name} to world '$name'")
        player.sendMessage(Component.text("Teleporting...", NamedTextColor.YELLOW))

        debugLogger.debug("Calling worldManager.teleportToWorld", "player" to player.name, "worldName" to world.name, "worldId" to world.id)
        worldManager.teleportToWorld(player, world).thenAccept { success ->
            debugLogger.debug("Plugin world teleport result", "success" to success, "worldName" to world.name)
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

        debugLogger.debugMethodExit("handleTeleport", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleVisit(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleVisit", "sender" to ctx.source.sender.name)
        plugin.logger.info("[WorldCommands] handleVisit: Executing world visit command")

        val player = ctx.source.sender as? Player
        debugLogger.debug("Player check", "isPlayer" to (player != null), "playerName" to player?.name)
        if (player == null) {
            debugLogger.debugMethodExit("handleVisit", "player-only error")
            return sendPlayerOnlyError(ctx)
        }

        val ownerName = StringArgumentType.getString(ctx, "owner")
        val worldName = StringArgumentType.getString(ctx, "name")
        debugLogger.debug("Parsed arguments", "ownerName" to ownerName, "worldName" to worldName)

        plugin.logger.info("[WorldCommands] handleVisit: Player ${player.name} attempting to visit world '$worldName' owned by '$ownerName'")

        val owner = Bukkit.getOfflinePlayer(ownerName)
        val ownerExists = owner.hasPlayedBefore() || owner.isOnline
        debugLogger.debug("Owner lookup", "ownerName" to ownerName, "ownerUuid" to owner.uniqueId, "exists" to ownerExists)
        if (!ownerExists) {
            plugin.logger.warning("[WorldCommands] handleVisit: Owner '$ownerName' not found")
            player.sendMessage(
                Component.text("Player '", NamedTextColor.RED)
                    .append(Component.text(ownerName, NamedTextColor.GOLD))
                    .append(Component.text("' not found", NamedTextColor.RED))
            )
            debugLogger.debugMethodExit("handleVisit", "owner not found")
            return Command.SINGLE_SUCCESS
        }

        val world = dataManager.getWorldsByOwner(owner.uniqueId)
            .firstOrNull { it.name.equals(worldName, ignoreCase = true) }
        debugLogger.debug("World lookup", "ownerUuid" to owner.uniqueId, "worldName" to worldName, "found" to (world != null))

        if (world == null) {
            plugin.logger.warning("[WorldCommands] handleVisit: World '$worldName' not found for owner '$ownerName'")
            player.sendMessage(
                Component.text("$ownerName doesn't own a world named '", NamedTextColor.RED)
                    .append(Component.text(worldName, NamedTextColor.GOLD))
                    .append(Component.text("'", NamedTextColor.RED))
            )
            debugLogger.debugMethodExit("handleVisit", "world not found")
            return Command.SINGLE_SUCCESS
        }

        val hasAccess = inviteManager.hasAccess(player.uniqueId, world)
        debugLogger.debug("Access check", "playerUuid" to player.uniqueId, "worldId" to world.id, "hasAccess" to hasAccess)
        if (!hasAccess) {
            plugin.logger.warning("[WorldCommands] handleVisit: Player ${player.name} does not have access to world '$worldName'")
            player.sendMessage(Component.text("You don't have access to this world", NamedTextColor.RED))
            debugLogger.debugMethodExit("handleVisit", "no access")
            return Command.SINGLE_SUCCESS
        }

        plugin.logger.info("[WorldCommands] handleVisit: Starting async teleport for ${player.name} to '$worldName'")
        player.sendMessage(Component.text("Teleporting...", NamedTextColor.YELLOW))

        debugLogger.debug("Calling worldManager.teleportToWorld", "player" to player.name, "worldName" to world.name, "worldId" to world.id)
        worldManager.teleportToWorld(player, world).thenAccept { success ->
            debugLogger.debug("Visit teleport result", "success" to success, "worldName" to world.name)
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

        debugLogger.debugMethodExit("handleVisit", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleInvite(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleInvite", "sender" to ctx.source.sender.name)
        plugin.logger.info("[WorldCommands] handleInvite: Executing world invite command")

        val player = ctx.source.sender as? Player
        debugLogger.debug("Player check", "isPlayer" to (player != null), "playerName" to player?.name)
        if (player == null) {
            debugLogger.debugMethodExit("handleInvite", "player-only error")
            return sendPlayerOnlyError(ctx)
        }

        val targetName = StringArgumentType.getString(ctx, "player")
        debugLogger.debug("Parsed arguments", "targetName" to targetName)

        plugin.logger.info("[WorldCommands] handleInvite: Player ${player.name} attempting to invite '$targetName'")

        val target = Bukkit.getPlayer(targetName)
        debugLogger.debug("Target player lookup", "targetName" to targetName, "isOnline" to (target != null), "targetUuid" to target?.uniqueId)
        if (target == null) {
            plugin.logger.warning("[WorldCommands] handleInvite: Target player '$targetName' is not online")
            player.sendMessage(
                Component.text("Player '", NamedTextColor.RED)
                    .append(Component.text(targetName, NamedTextColor.GOLD))
                    .append(Component.text("' is not online", NamedTextColor.RED))
            )
            debugLogger.debugMethodExit("handleInvite", "target not online")
            return Command.SINGLE_SUCCESS
        }

        val world = getCurrentWorld(player)
        debugLogger.debug("Current world check", "playerWorld" to player.world.name, "ownedWorld" to world?.name, "worldId" to world?.id)
        if (world == null) {
            plugin.logger.warning("[WorldCommands] handleInvite: Player ${player.name} not in their own world")
            player.sendMessage(
                Component.text("You must specify a world name or be in one of your worlds", NamedTextColor.RED)
            )
            debugLogger.debugMethodExit("handleInvite", "not in own world")
            return Command.SINGLE_SUCCESS
        }

        plugin.logger.info("[WorldCommands] handleInvite: Sending invite for world '${world.name}' to ${target.name}")
        debugLogger.debug("Calling inviteManager.sendInvite", "worldName" to world.name, "worldId" to world.id, "targetName" to target.name)
        inviteManager.sendInvite(world, player, target).onFailure { error ->
            debugLogger.debug("Invite failed", "error" to error.message)
            plugin.logger.warning("[WorldCommands] handleInvite: Failed to send invite: ${error.message}")
            player.sendMessage(
                Component.text("Failed to send invite: ", NamedTextColor.RED)
                    .append(Component.text(error.message ?: "Unknown error", NamedTextColor.GOLD))
            )
        }

        plugin.logger.info("[WorldCommands] handleInvite: Command completed for ${player.name}")
        debugLogger.debugMethodExit("handleInvite", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleInviteWithWorld(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleInviteWithWorld", "sender" to ctx.source.sender.name)

        val player = ctx.source.sender as? Player
        debugLogger.debug("Player check", "isPlayer" to (player != null), "playerName" to player?.name)
        if (player == null) {
            debugLogger.debugMethodExit("handleInviteWithWorld", "player-only error")
            return sendPlayerOnlyError(ctx)
        }

        val targetName = StringArgumentType.getString(ctx, "player")
        val worldName = StringArgumentType.getString(ctx, "world")
        debugLogger.debug("Parsed arguments", "targetName" to targetName, "worldName" to worldName)

        val target = Bukkit.getPlayer(targetName)
        debugLogger.debug("Target player lookup", "targetName" to targetName, "isOnline" to (target != null))
        if (target == null) {
            player.sendMessage(
                Component.text("Player '", NamedTextColor.RED)
                    .append(Component.text(targetName, NamedTextColor.GOLD))
                    .append(Component.text("' is not online", NamedTextColor.RED))
            )
            debugLogger.debugMethodExit("handleInviteWithWorld", "target not online")
            return Command.SINGLE_SUCCESS
        }

        val world = getPlayerWorld(player, worldName)
        debugLogger.debug("World lookup", "worldName" to worldName, "found" to (world != null), "worldId" to world?.id)
        if (world == null) {
            debugLogger.debugMethodExit("handleInviteWithWorld", "world not found")
            return Command.SINGLE_SUCCESS
        }

        debugLogger.debug("Calling inviteManager.sendInvite", "worldName" to world.name, "targetName" to target.name)
        inviteManager.sendInvite(world, player, target).onFailure { error ->
            debugLogger.debug("Invite failed", "error" to error.message)
            player.sendMessage(
                Component.text("Failed to send invite: ", NamedTextColor.RED)
                    .append(Component.text(error.message ?: "Unknown error", NamedTextColor.GOLD))
            )
        }

        debugLogger.debugMethodExit("handleInviteWithWorld", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleKick(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleKick", "sender" to ctx.source.sender.name)

        val player = ctx.source.sender as? Player
        debugLogger.debug("Player check", "isPlayer" to (player != null), "playerName" to player?.name)
        if (player == null) {
            debugLogger.debugMethodExit("handleKick", "player-only error")
            return sendPlayerOnlyError(ctx)
        }

        val targetName = StringArgumentType.getString(ctx, "player")
        debugLogger.debug("Parsed arguments", "targetName" to targetName)

        val target = Bukkit.getOfflinePlayer(targetName)
        val targetExists = target.hasPlayedBefore() || target.isOnline
        debugLogger.debug("Target player lookup", "targetName" to targetName, "targetUuid" to target.uniqueId, "exists" to targetExists)
        if (!targetExists) {
            player.sendMessage(
                Component.text("Player '", NamedTextColor.RED)
                    .append(Component.text(targetName, NamedTextColor.GOLD))
                    .append(Component.text("' not found", NamedTextColor.RED))
            )
            debugLogger.debugMethodExit("handleKick", "target not found")
            return Command.SINGLE_SUCCESS
        }

        val world = getCurrentWorld(player)
        debugLogger.debug("Current world check", "playerWorld" to player.world.name, "ownedWorld" to world?.name, "worldId" to world?.id)
        if (world == null) {
            player.sendMessage(
                Component.text("You must specify a world name or be in one of your worlds", NamedTextColor.RED)
            )
            debugLogger.debugMethodExit("handleKick", "not in own world")
            return Command.SINGLE_SUCCESS
        }

        debugLogger.debug("Calling inviteManager.kickPlayer", "worldName" to world.name, "worldId" to world.id, "targetUuid" to target.uniqueId)
        inviteManager.kickPlayer(world, player, target.uniqueId).onFailure { error ->
            debugLogger.debug("Kick failed", "error" to error.message)
            player.sendMessage(
                Component.text("Failed to kick player: ", NamedTextColor.RED)
                    .append(Component.text(error.message ?: "Unknown error", NamedTextColor.GOLD))
            )
        }

        debugLogger.debugMethodExit("handleKick", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleKickWithWorld(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleKickWithWorld", "sender" to ctx.source.sender.name)

        val player = ctx.source.sender as? Player
        debugLogger.debug("Player check", "isPlayer" to (player != null), "playerName" to player?.name)
        if (player == null) {
            debugLogger.debugMethodExit("handleKickWithWorld", "player-only error")
            return sendPlayerOnlyError(ctx)
        }

        val targetName = StringArgumentType.getString(ctx, "player")
        val worldName = StringArgumentType.getString(ctx, "world")
        debugLogger.debug("Parsed arguments", "targetName" to targetName, "worldName" to worldName)

        val target = Bukkit.getOfflinePlayer(targetName)
        val targetExists = target.hasPlayedBefore() || target.isOnline
        debugLogger.debug("Target player lookup", "targetName" to targetName, "targetUuid" to target.uniqueId, "exists" to targetExists)
        if (!targetExists) {
            player.sendMessage(
                Component.text("Player '", NamedTextColor.RED)
                    .append(Component.text(targetName, NamedTextColor.GOLD))
                    .append(Component.text("' not found", NamedTextColor.RED))
            )
            debugLogger.debugMethodExit("handleKickWithWorld", "target not found")
            return Command.SINGLE_SUCCESS
        }

        val world = getPlayerWorld(player, worldName)
        debugLogger.debug("World lookup", "worldName" to worldName, "found" to (world != null), "worldId" to world?.id)
        if (world == null) {
            debugLogger.debugMethodExit("handleKickWithWorld", "world not found")
            return Command.SINGLE_SUCCESS
        }

        debugLogger.debug("Calling inviteManager.kickPlayer", "worldName" to world.name, "worldId" to world.id, "targetUuid" to target.uniqueId)
        inviteManager.kickPlayer(world, player, target.uniqueId).onFailure { error ->
            debugLogger.debug("Kick failed", "error" to error.message)
            player.sendMessage(
                Component.text("Failed to kick player: ", NamedTextColor.RED)
                    .append(Component.text(error.message ?: "Unknown error", NamedTextColor.GOLD))
            )
        }

        debugLogger.debugMethodExit("handleKickWithWorld", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleAccept(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleAccept", "sender" to ctx.source.sender.name)

        val player = ctx.source.sender as? Player
        debugLogger.debug("Player check", "isPlayer" to (player != null), "playerName" to player?.name)
        if (player == null) {
            debugLogger.debugMethodExit("handleAccept", "player-only error")
            return sendPlayerOnlyError(ctx)
        }

        val ownerName = StringArgumentType.getString(ctx, "owner")
        val worldName = StringArgumentType.getString(ctx, "world")
        debugLogger.debug("Parsed arguments", "ownerName" to ownerName, "worldName" to worldName)

        val owner = Bukkit.getOfflinePlayer(ownerName)
        val ownerExists = owner.hasPlayedBefore() || owner.isOnline
        debugLogger.debug("Owner lookup", "ownerName" to ownerName, "ownerUuid" to owner.uniqueId, "exists" to ownerExists)
        if (!ownerExists) {
            player.sendMessage(
                Component.text("Player '", NamedTextColor.RED)
                    .append(Component.text(ownerName, NamedTextColor.GOLD))
                    .append(Component.text("' not found", NamedTextColor.RED))
            )
            debugLogger.debugMethodExit("handleAccept", "owner not found")
            return Command.SINGLE_SUCCESS
        }

        val world = dataManager.getWorldsByOwner(owner.uniqueId)
            .firstOrNull { it.name.equals(worldName, ignoreCase = true) }
        debugLogger.debug("World lookup", "ownerUuid" to owner.uniqueId, "worldName" to worldName, "found" to (world != null))

        if (world == null) {
            player.sendMessage(
                Component.text("$ownerName doesn't own a world named '", NamedTextColor.RED)
                    .append(Component.text(worldName, NamedTextColor.GOLD))
                    .append(Component.text("'", NamedTextColor.RED))
            )
            debugLogger.debugMethodExit("handleAccept", "world not found")
            return Command.SINGLE_SUCCESS
        }

        val invite = dataManager.getInvite(world.id, player.uniqueId)
        debugLogger.debug("Invite lookup", "worldId" to world.id, "playerUuid" to player.uniqueId, "found" to (invite != null))
        if (invite == null) {
            player.sendMessage(Component.text("You don't have a pending invite to this world", NamedTextColor.RED))
            debugLogger.debugMethodExit("handleAccept", "no invite")
            return Command.SINGLE_SUCCESS
        }

        debugLogger.debug("Calling inviteManager.acceptInvite", "worldId" to invite.worldId, "inviteeUuid" to invite.inviteeUuid)
        inviteManager.acceptInvite(invite, player).onFailure { error ->
            debugLogger.debug("Accept invite failed", "error" to error.message)
            player.sendMessage(
                Component.text("Failed to accept invite: ", NamedTextColor.RED)
                    .append(Component.text(error.message ?: "Unknown error", NamedTextColor.GOLD))
            )
        }

        debugLogger.debugMethodExit("handleAccept", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleDeny(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleDeny", "sender" to ctx.source.sender.name)

        val player = ctx.source.sender as? Player
        debugLogger.debug("Player check", "isPlayer" to (player != null), "playerName" to player?.name)
        if (player == null) {
            debugLogger.debugMethodExit("handleDeny", "player-only error")
            return sendPlayerOnlyError(ctx)
        }

        val ownerName = StringArgumentType.getString(ctx, "owner")
        val worldName = StringArgumentType.getString(ctx, "world")
        debugLogger.debug("Parsed arguments", "ownerName" to ownerName, "worldName" to worldName)

        val owner = Bukkit.getOfflinePlayer(ownerName)
        val ownerExists = owner.hasPlayedBefore() || owner.isOnline
        debugLogger.debug("Owner lookup", "ownerName" to ownerName, "ownerUuid" to owner.uniqueId, "exists" to ownerExists)
        if (!ownerExists) {
            player.sendMessage(
                Component.text("Player '", NamedTextColor.RED)
                    .append(Component.text(ownerName, NamedTextColor.GOLD))
                    .append(Component.text("' not found", NamedTextColor.RED))
            )
            debugLogger.debugMethodExit("handleDeny", "owner not found")
            return Command.SINGLE_SUCCESS
        }

        val world = dataManager.getWorldsByOwner(owner.uniqueId)
            .firstOrNull { it.name.equals(worldName, ignoreCase = true) }
        debugLogger.debug("World lookup", "ownerUuid" to owner.uniqueId, "worldName" to worldName, "found" to (world != null))

        if (world == null) {
            player.sendMessage(
                Component.text("$ownerName doesn't own a world named '", NamedTextColor.RED)
                    .append(Component.text(worldName, NamedTextColor.GOLD))
                    .append(Component.text("'", NamedTextColor.RED))
            )
            debugLogger.debugMethodExit("handleDeny", "world not found")
            return Command.SINGLE_SUCCESS
        }

        val invite = dataManager.getInvite(world.id, player.uniqueId)
        debugLogger.debug("Invite lookup", "worldId" to world.id, "playerUuid" to player.uniqueId, "found" to (invite != null))
        if (invite == null) {
            player.sendMessage(Component.text("You don't have a pending invite to this world", NamedTextColor.RED))
            debugLogger.debugMethodExit("handleDeny", "no invite")
            return Command.SINGLE_SUCCESS
        }

        debugLogger.debug("Calling inviteManager.denyInvite", "worldId" to invite.worldId, "inviteeUuid" to invite.inviteeUuid)
        inviteManager.denyInvite(invite, player).onFailure { error ->
            debugLogger.debug("Deny invite failed", "error" to error.message)
            player.sendMessage(
                Component.text("Failed to deny invite: ", NamedTextColor.RED)
                    .append(Component.text(error.message ?: "Unknown error", NamedTextColor.GOLD))
            )
        }

        debugLogger.debugMethodExit("handleDeny", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleTransfer(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleTransfer", "sender" to ctx.source.sender.name)

        val player = ctx.source.sender as? Player
        debugLogger.debug("Player check", "isPlayer" to (player != null), "playerName" to player?.name)
        if (player == null) {
            debugLogger.debugMethodExit("handleTransfer", "player-only error")
            return sendPlayerOnlyError(ctx)
        }

        val targetName = StringArgumentType.getString(ctx, "player")
        val worldName = StringArgumentType.getString(ctx, "world")
        debugLogger.debug("Parsed arguments", "targetName" to targetName, "worldName" to worldName)

        val target = Bukkit.getOfflinePlayer(targetName)
        val targetExists = target.hasPlayedBefore() || target.isOnline
        debugLogger.debug("Target lookup", "targetName" to targetName, "targetUuid" to target.uniqueId, "exists" to targetExists)
        if (!targetExists) {
            player.sendMessage(
                Component.text("Player '", NamedTextColor.RED)
                    .append(Component.text(targetName, NamedTextColor.GOLD))
                    .append(Component.text("' not found", NamedTextColor.RED))
            )
            debugLogger.debugMethodExit("handleTransfer", "target not found")
            return Command.SINGLE_SUCCESS
        }

        val world = getPlayerWorld(player, worldName)
        debugLogger.debug("World lookup", "worldName" to worldName, "found" to (world != null), "worldId" to world?.id)
        if (world == null) {
            debugLogger.debugMethodExit("handleTransfer", "world not found")
            return Command.SINGLE_SUCCESS
        }

        debugLogger.debug("Calling inviteManager.transferOwnership", "worldId" to world.id, "worldName" to world.name, "newOwnerUuid" to target.uniqueId)
        inviteManager.transferOwnership(world, player, target.uniqueId).onFailure { error ->
            debugLogger.debug("Transfer failed", "error" to error.message)
            player.sendMessage(
                Component.text("Failed to transfer ownership: ", NamedTextColor.RED)
                    .append(Component.text(error.message ?: "Unknown error", NamedTextColor.GOLD))
            )
        }

        debugLogger.debugMethodExit("handleTransfer", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleInvites(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleInvites", "sender" to ctx.source.sender.name)

        val player = ctx.source.sender as? Player
        debugLogger.debug("Player check", "isPlayer" to (player != null), "playerName" to player?.name)
        if (player == null) {
            debugLogger.debugMethodExit("handleInvites", "player-only error")
            return sendPlayerOnlyError(ctx)
        }

        val invites = inviteManager.getPendingInvites(player.uniqueId)
        debugLogger.debug("Retrieved pending invites", "playerUuid" to player.uniqueId, "inviteCount" to invites.size)

        if (invites.isEmpty()) {
            player.sendMessage(Component.text("You have no pending invites", NamedTextColor.YELLOW))
            debugLogger.debugMethodExit("handleInvites", "no invites")
            return Command.SINGLE_SUCCESS
        }

        debugLogger.debug("Displaying invites", "inviteDetails" to invites.map { "${it.ownerName}/${it.worldName}" })
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

        debugLogger.debugMethodExit("handleInvites", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleLeave(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleLeave", "sender" to ctx.source.sender.name)
        plugin.logger.info("[WorldCommands] handleLeave: Executing world leave command")

        val player = ctx.source.sender as? Player
        debugLogger.debug("Player check", "isPlayer" to (player != null), "playerName" to player?.name)
        if (player == null) {
            debugLogger.debugMethodExit("handleLeave", "player-only error")
            return sendPlayerOnlyError(ctx)
        }

        plugin.logger.info("[WorldCommands] handleLeave: Player ${player.name} attempting to leave current world")

        // Check if player is currently in a plugin world
        val currentWorld = player.world
        val isPluginWorld = worldManager.isPluginWorld(currentWorld)
        debugLogger.debug("Current world check", "worldName" to currentWorld.name, "isPluginWorld" to isPluginWorld)
        if (!isPluginWorld) {
            plugin.logger.info("[WorldCommands] handleLeave: Player ${player.name} is already in a vanilla world")
            player.sendMessage(Component.text("You are already in a vanilla world", NamedTextColor.YELLOW))
            debugLogger.debugMethodExit("handleLeave", "already in vanilla world")
            return Command.SINGLE_SUCCESS
        }

        plugin.logger.info("[WorldCommands] handleLeave: Teleporting ${player.name} to vanilla world")
        player.sendMessage(Component.text("Returning to vanilla world...", NamedTextColor.YELLOW))

        debugLogger.debug("Calling worldManager.teleportToVanillaWorld", "player" to player.name)
        worldManager.teleportToVanillaWorld(player).thenAccept { success ->
            debugLogger.debug("Teleport to vanilla world result", "success" to success)
            player.scheduler.run(plugin, { _ ->
                if (success) {
                    plugin.logger.info("[WorldCommands] handleLeave: Player ${player.name} successfully left plugin world")
                    player.sendMessage(
                        Component.text("Returned to vanilla world", NamedTextColor.GREEN)
                    )
                } else {
                    plugin.logger.warning("[WorldCommands] handleLeave: Failed to teleport ${player.name} to vanilla world")
                    player.sendMessage(Component.text("Failed to return to vanilla world", NamedTextColor.RED))
                }
            }, null)
        }

        debugLogger.debugMethodExit("handleLeave", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleSpawn(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleSpawn", "sender" to ctx.source.sender.name)
        plugin.logger.info("[WorldCommands] handleSpawn: Executing world spawn command")

        val player = ctx.source.sender as? Player
        debugLogger.debug("Player check", "isPlayer" to (player != null), "playerName" to player?.name)
        if (player == null) {
            debugLogger.debugMethodExit("handleSpawn", "player-only error")
            return sendPlayerOnlyError(ctx)
        }

        plugin.logger.info("[WorldCommands] handleSpawn: Player ${player.name} attempting to teleport to default world spawn")
        debugLogger.debug("Current location", "world" to player.world.name, "location" to player.location.toString())

        player.sendMessage(Component.text("Teleporting to spawn...", NamedTextColor.YELLOW))

        // Use WorldManager to properly save/restore state
        debugLogger.debug("Calling worldManager.teleportToVanillaWorld", "player" to player.name)
        worldManager.teleportToVanillaWorld(player).thenAccept { success ->
            debugLogger.debug("Spawn teleport result", "success" to success)
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

        debugLogger.debugMethodExit("handleSpawn", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleMenu(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleMenu", "sender" to ctx.source.sender.name)
        plugin.logger.info("[WorldCommands] handleMenu: Executing world menu command")

        val player = ctx.source.sender as? Player
        debugLogger.debug("Player check", "isPlayer" to (player != null), "playerName" to player?.name)
        if (player == null) {
            debugLogger.debugMethodExit("handleMenu", "player-only error")
            return sendPlayerOnlyError(ctx)
        }

        plugin.logger.info("[WorldCommands] handleMenu: Opening main menu GUI for ${player.name}")
        debugLogger.debug("Opening main menu GUI", "player" to player.name, "playerUuid" to player.uniqueId)
        mainMenuGui.open(player)

        plugin.logger.info("[WorldCommands] handleMenu: Command completed for ${player.name}")
        debugLogger.debugMethodExit("handleMenu", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleHelp(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleHelp", "sender" to ctx.source.sender.name)
        val sender = ctx.source.sender

        debugLogger.debug("Displaying help", "senderName" to sender.name)
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
            Component.text("/world leave", NamedTextColor.GRAY)
                .append(Component.text(" - Return to vanilla world", NamedTextColor.YELLOW))
        )
        sender.sendMessage(
            Component.text("/world spawn", NamedTextColor.GRAY)
                .append(Component.text(" - Return to default spawn", NamedTextColor.YELLOW))
        )
        sender.sendMessage(
            Component.text("/world menu", NamedTextColor.GRAY)
                .append(Component.text(" - Open the GUI menu", NamedTextColor.YELLOW))
        )
        sender.sendMessage(
            Component.text("/world role <player> <role> [world]", NamedTextColor.GRAY)
                .append(Component.text(" - Set player role", NamedTextColor.YELLOW))
        )
        sender.sendMessage(
            Component.text("/world visibility [world]", NamedTextColor.GRAY)
                .append(Component.text(" - Toggle public/private", NamedTextColor.YELLOW))
        )
        sender.sendMessage(
            Component.text("/world publicrole <role> [world]", NamedTextColor.GRAY)
                .append(Component.text(" - Set public join role", NamedTextColor.YELLOW))
        )
        sender.sendMessage(
            Component.text("/world members [world]", NamedTextColor.GRAY)
                .append(Component.text(" - View world members", NamedTextColor.YELLOW))
        )
        sender.sendMessage(
            Component.text("/world browse", NamedTextColor.GRAY)
                .append(Component.text(" - Browse public worlds", NamedTextColor.YELLOW))
        )

        debugLogger.debugMethodExit("handleHelp", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleRole(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleRole", "sender" to ctx.source.sender.name)

        val player = ctx.source.sender as? Player
        if (player == null) {
            debugLogger.debugMethodExit("handleRole", "player-only error")
            return sendPlayerOnlyError(ctx)
        }

        val targetName = StringArgumentType.getString(ctx, "player")
        val roleString = StringArgumentType.getString(ctx, "role")
        debugLogger.debug("Parsed arguments", "targetName" to targetName, "roleString" to roleString)

        val target = Bukkit.getOfflinePlayer(targetName)
        val targetExists = target.hasPlayedBefore() || target.isOnline
        if (!targetExists) {
            player.sendMessage(
                Component.text("Player '", NamedTextColor.RED)
                    .append(Component.text(targetName, NamedTextColor.GOLD))
                    .append(Component.text("' not found", NamedTextColor.RED))
            )
            return Command.SINGLE_SUCCESS
        }

        val role = parseRole(roleString)
        if (role == null) {
            player.sendMessage(
                Component.text("Invalid role. Valid roles: ", NamedTextColor.RED)
                    .append(Component.text("manager, member, visitor", NamedTextColor.GOLD))
            )
            return Command.SINGLE_SUCCESS
        }

        val world = getCurrentOwnedWorld(player)
        if (world == null) {
            player.sendMessage(
                Component.text("You must specify a world name or be in one of your owned worlds", NamedTextColor.RED)
            )
            return Command.SINGLE_SUCCESS
        }

        inviteManager.setPlayerRole(world, player, target.uniqueId, role).onFailure { error ->
            player.sendMessage(
                Component.text("Failed to set role: ", NamedTextColor.RED)
                    .append(Component.text(error.message ?: "Unknown error", NamedTextColor.GOLD))
            )
        }

        debugLogger.debugMethodExit("handleRole", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleRoleWithWorld(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleRoleWithWorld", "sender" to ctx.source.sender.name)

        val player = ctx.source.sender as? Player
        if (player == null) {
            debugLogger.debugMethodExit("handleRoleWithWorld", "player-only error")
            return sendPlayerOnlyError(ctx)
        }

        val targetName = StringArgumentType.getString(ctx, "player")
        val roleString = StringArgumentType.getString(ctx, "role")
        val worldName = StringArgumentType.getString(ctx, "world")
        debugLogger.debug("Parsed arguments", "targetName" to targetName, "roleString" to roleString, "worldName" to worldName)

        val target = Bukkit.getOfflinePlayer(targetName)
        val targetExists = target.hasPlayedBefore() || target.isOnline
        if (!targetExists) {
            player.sendMessage(
                Component.text("Player '", NamedTextColor.RED)
                    .append(Component.text(targetName, NamedTextColor.GOLD))
                    .append(Component.text("' not found", NamedTextColor.RED))
            )
            return Command.SINGLE_SUCCESS
        }

        val role = parseRole(roleString)
        if (role == null) {
            player.sendMessage(
                Component.text("Invalid role. Valid roles: ", NamedTextColor.RED)
                    .append(Component.text("manager, member, visitor", NamedTextColor.GOLD))
            )
            return Command.SINGLE_SUCCESS
        }

        val world = getPlayerWorld(player, worldName)
        if (world == null) {
            return Command.SINGLE_SUCCESS
        }

        inviteManager.setPlayerRole(world, player, target.uniqueId, role).onFailure { error ->
            player.sendMessage(
                Component.text("Failed to set role: ", NamedTextColor.RED)
                    .append(Component.text(error.message ?: "Unknown error", NamedTextColor.GOLD))
            )
        }

        debugLogger.debugMethodExit("handleRoleWithWorld", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleVisibility(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleVisibility", "sender" to ctx.source.sender.name)

        val player = ctx.source.sender as? Player
        if (player == null) {
            debugLogger.debugMethodExit("handleVisibility", "player-only error")
            return sendPlayerOnlyError(ctx)
        }

        val world = getCurrentOwnedWorld(player)
        if (world == null) {
            player.sendMessage(
                Component.text("You must specify a world name or be in one of your owned worlds", NamedTextColor.RED)
            )
            return Command.SINGLE_SUCCESS
        }

        inviteManager.toggleWorldVisibility(world, player).onFailure { error ->
            player.sendMessage(
                Component.text("Failed to toggle visibility: ", NamedTextColor.RED)
                    .append(Component.text(error.message ?: "Unknown error", NamedTextColor.GOLD))
            )
        }

        debugLogger.debugMethodExit("handleVisibility", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleVisibilityWithWorld(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleVisibilityWithWorld", "sender" to ctx.source.sender.name)

        val player = ctx.source.sender as? Player
        if (player == null) {
            debugLogger.debugMethodExit("handleVisibilityWithWorld", "player-only error")
            return sendPlayerOnlyError(ctx)
        }

        val worldName = StringArgumentType.getString(ctx, "world")
        val world = getPlayerWorld(player, worldName)
        if (world == null) {
            return Command.SINGLE_SUCCESS
        }

        inviteManager.toggleWorldVisibility(world, player).onFailure { error ->
            player.sendMessage(
                Component.text("Failed to toggle visibility: ", NamedTextColor.RED)
                    .append(Component.text(error.message ?: "Unknown error", NamedTextColor.GOLD))
            )
        }

        debugLogger.debugMethodExit("handleVisibilityWithWorld", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handlePublicRole(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handlePublicRole", "sender" to ctx.source.sender.name)

        val player = ctx.source.sender as? Player
        if (player == null) {
            debugLogger.debugMethodExit("handlePublicRole", "player-only error")
            return sendPlayerOnlyError(ctx)
        }

        val roleString = StringArgumentType.getString(ctx, "role")
        val role = parsePublicRole(roleString)
        if (role == null) {
            player.sendMessage(
                Component.text("Invalid role. Valid public roles: ", NamedTextColor.RED)
                    .append(Component.text("member, visitor", NamedTextColor.GOLD))
            )
            return Command.SINGLE_SUCCESS
        }

        val world = getCurrentOwnedWorld(player)
        if (world == null) {
            player.sendMessage(
                Component.text("You must specify a world name or be in one of your owned worlds", NamedTextColor.RED)
            )
            return Command.SINGLE_SUCCESS
        }

        inviteManager.setPublicJoinRole(world, player, role).onFailure { error ->
            player.sendMessage(
                Component.text("Failed to set public role: ", NamedTextColor.RED)
                    .append(Component.text(error.message ?: "Unknown error", NamedTextColor.GOLD))
            )
        }

        debugLogger.debugMethodExit("handlePublicRole", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handlePublicRoleWithWorld(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handlePublicRoleWithWorld", "sender" to ctx.source.sender.name)

        val player = ctx.source.sender as? Player
        if (player == null) {
            debugLogger.debugMethodExit("handlePublicRoleWithWorld", "player-only error")
            return sendPlayerOnlyError(ctx)
        }

        val roleString = StringArgumentType.getString(ctx, "role")
        val worldName = StringArgumentType.getString(ctx, "world")

        val role = parsePublicRole(roleString)
        if (role == null) {
            player.sendMessage(
                Component.text("Invalid role. Valid public roles: ", NamedTextColor.RED)
                    .append(Component.text("member, visitor", NamedTextColor.GOLD))
            )
            return Command.SINGLE_SUCCESS
        }

        val world = getPlayerWorld(player, worldName)
        if (world == null) {
            return Command.SINGLE_SUCCESS
        }

        inviteManager.setPublicJoinRole(world, player, role).onFailure { error ->
            player.sendMessage(
                Component.text("Failed to set public role: ", NamedTextColor.RED)
                    .append(Component.text(error.message ?: "Unknown error", NamedTextColor.GOLD))
            )
        }

        debugLogger.debugMethodExit("handlePublicRoleWithWorld", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleMembers(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleMembers", "sender" to ctx.source.sender.name)

        val player = ctx.source.sender as? Player
        if (player == null) {
            debugLogger.debugMethodExit("handleMembers", "player-only error")
            return sendPlayerOnlyError(ctx)
        }

        val world = getCurrentOwnedWorld(player)
        if (world == null) {
            player.sendMessage(
                Component.text("You must specify a world name or be in one of your owned worlds", NamedTextColor.RED)
            )
            return Command.SINGLE_SUCCESS
        }

        displayWorldMembers(player, world)

        debugLogger.debugMethodExit("handleMembers", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleMembersWithWorld(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleMembersWithWorld", "sender" to ctx.source.sender.name)

        val player = ctx.source.sender as? Player
        if (player == null) {
            debugLogger.debugMethodExit("handleMembersWithWorld", "player-only error")
            return sendPlayerOnlyError(ctx)
        }

        val worldName = StringArgumentType.getString(ctx, "world")
        val world = getPlayerWorld(player, worldName)
        if (world == null) {
            return Command.SINGLE_SUCCESS
        }

        displayWorldMembers(player, world)

        debugLogger.debugMethodExit("handleMembersWithWorld", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun displayWorldMembers(player: Player, world: PlayerWorld) {
        player.sendMessage(
            Component.text("Members of ", NamedTextColor.GREEN)
                .append(Component.text(world.name, NamedTextColor.GOLD))
                .append(Component.text(":", NamedTextColor.GREEN))
        )

        // Show visibility status
        val visibilityText = if (world.isPublic) "Public" else "Private"
        val publicRoleText = if (world.isPublic) " (joins as ${world.publicJoinRole.name.lowercase()})" else ""
        player.sendMessage(
            Component.text("  Visibility: ", NamedTextColor.GRAY)
                .append(Component.text(visibilityText + publicRoleText, NamedTextColor.YELLOW))
        )

        // Show owner
        player.sendMessage(
            Component.text("  ", NamedTextColor.GRAY)
                .append(Component.text(world.ownerName, NamedTextColor.GOLD))
                .append(Component.text(" - ", NamedTextColor.GRAY))
                .append(Component.text("Owner", NamedTextColor.RED))
        )

        // Show all players with roles
        if (world.playerRoles.isEmpty()) {
            player.sendMessage(Component.text("  No other members", NamedTextColor.GRAY))
        } else {
            world.playerRoles.forEach { (uuid, role) ->
                val playerName = Bukkit.getOfflinePlayer(uuid).name ?: uuid.toString()
                val roleColor = when (role) {
                    WorldRole.MANAGER -> NamedTextColor.GOLD
                    WorldRole.MEMBER -> NamedTextColor.GREEN
                    WorldRole.VISITOR -> NamedTextColor.AQUA
                    else -> NamedTextColor.GRAY
                }
                player.sendMessage(
                    Component.text("  ", NamedTextColor.GRAY)
                        .append(Component.text(playerName, NamedTextColor.WHITE))
                        .append(Component.text(" - ", NamedTextColor.GRAY))
                        .append(Component.text(role.name.lowercase().replaceFirstChar { it.uppercase() }, roleColor))
                )
            }
        }
    }

    private fun handleBrowse(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleBrowse", "sender" to ctx.source.sender.name)

        val player = ctx.source.sender as? Player
        if (player == null) {
            debugLogger.debugMethodExit("handleBrowse", "player-only error")
            return sendPlayerOnlyError(ctx)
        }

        val publicWorlds = dataManager.getAllWorlds().filter { it.isPublic && it.isEnabled }

        if (publicWorlds.isEmpty()) {
            player.sendMessage(Component.text("No public worlds available", NamedTextColor.YELLOW))
            return Command.SINGLE_SUCCESS
        }

        player.sendMessage(Component.text("Public Worlds:", NamedTextColor.GREEN))
        publicWorlds.forEach { world ->
            val joinRoleText = when (world.publicJoinRole) {
                WorldRole.MEMBER -> "can play"
                WorldRole.VISITOR -> "spectator only"
                else -> ""
            }
            player.sendMessage(
                Component.text("  - ", NamedTextColor.GRAY)
                    .append(Component.text(world.name, NamedTextColor.GOLD))
                    .append(Component.text(" by ", NamedTextColor.GRAY))
                    .append(Component.text(world.ownerName, NamedTextColor.WHITE))
                    .append(Component.text(" ($joinRoleText)", NamedTextColor.DARK_GRAY))
            )
            player.sendMessage(
                Component.text("    ", NamedTextColor.GRAY)
                    .append(Component.text("/world visit ${world.ownerName} ${world.name}", NamedTextColor.YELLOW))
            )
        }

        debugLogger.debugMethodExit("handleBrowse", Command.SINGLE_SUCCESS)
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
            // Add "default" for the vanilla world
            if ("default".startsWith(builder.remainingLowerCase)) {
                builder.suggest("default")
            }
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

    private fun suggestWorldMembers(
        ctx: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val sender = ctx.source.sender
        if (sender is Player) {
            // Get current world's members
            val currentWorld = getCurrentOwnedWorld(sender)
            if (currentWorld != null) {
                currentWorld.playerRoles.keys
                    .mapNotNull { Bukkit.getOfflinePlayer(it).name }
                    .filter { it.lowercase().startsWith(builder.remainingLowerCase) }
                    .forEach { builder.suggest(it) }
            }
        }
        return builder.buildFuture()
    }

    private fun suggestRoles(
        ctx: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        listOf("manager", "member", "visitor")
            .filter { it.startsWith(builder.remainingLowerCase) }
            .forEach { builder.suggest(it) }
        return builder.buildFuture()
    }

    private fun suggestPublicRoles(
        ctx: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        listOf("member", "visitor")
            .filter { it.startsWith(builder.remainingLowerCase) }
            .forEach { builder.suggest(it) }
        return builder.buildFuture()
    }

    // ========================
    // Helper Methods
    // ========================

    private fun sendPlayerOnlyError(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debug("sendPlayerOnlyError called", "senderType" to ctx.source.sender.javaClass.simpleName)
        plugin.logger.warning("[WorldCommands] Non-player attempted to execute player-only command")
        ctx.source.sender.sendMessage(
            Component.text("This command can only be used by players", NamedTextColor.RED)
        )
        return Command.SINGLE_SUCCESS
    }

    private fun getPlayerWorld(player: Player, name: String): PlayerWorld? {
        debugLogger.debugMethodEntry("getPlayerWorld", "playerUuid" to player.uniqueId, "name" to name)
        val world = dataManager.getWorldsByOwner(player.uniqueId)
            .firstOrNull { it.name.equals(name, ignoreCase = true) }

        debugLogger.debug("World lookup result", "name" to name, "found" to (world != null), "worldId" to world?.id)
        if (world == null) {
            player.sendMessage(
                Component.text("You don't own a world named '", NamedTextColor.RED)
                    .append(Component.text(name, NamedTextColor.GOLD))
                    .append(Component.text("'", NamedTextColor.RED))
            )
        }

        debugLogger.debugMethodExit("getPlayerWorld", world?.name)
        return world
    }

    /**
     * Get the player's world they are currently in, if they have management permissions (owner or manager).
     * Returns null if not in a world where they have invite/kick permissions.
     */
    private fun getCurrentWorld(player: Player): PlayerWorld? {
        debugLogger.debugMethodEntry("getCurrentWorld", "player" to player.name, "bukkitWorld" to player.world.name)
        val currentWorld = worldManager.getPlayerWorldFromBukkitWorld(player.world)
        debugLogger.debug("Player world lookup", "bukkitWorld" to player.world.name, "found" to (currentWorld != null))

        if (currentWorld == null) {
            debugLogger.debugMethodExit("getCurrentWorld", null)
            return null
        }

        val playerRole = currentWorld.getPlayerRole(player.uniqueId)
        val canManage = playerRole?.canInvite() == true
        debugLogger.debug("Permission check", "worldOwner" to currentWorld.ownerUuid, "player" to player.uniqueId, "role" to playerRole, "canManage" to canManage)
        if (!canManage) {
            debugLogger.debugMethodExit("getCurrentWorld", "no permission")
            return null
        }

        debugLogger.debugMethodExit("getCurrentWorld", currentWorld.name)
        return currentWorld
    }

    private fun parseWorldType(type: String): WorldType? {
        debugLogger.debugMethodEntry("parseWorldType", "type" to type)
        val result = when (type.lowercase()) {
            "normal" -> WorldType.NORMAL
            "flat" -> WorldType.FLAT
            "amplified" -> WorldType.AMPLIFIED
            "large_biomes" -> WorldType.LARGE_BIOMES
            "void" -> WorldType.VOID
            else -> null
        }
        debugLogger.debugMethodExit("parseWorldType", result)
        return result
    }

    private fun parseRole(role: String): WorldRole? {
        debugLogger.debugMethodEntry("parseRole", "role" to role)
        val result = when (role.lowercase()) {
            "manager" -> WorldRole.MANAGER
            "member" -> WorldRole.MEMBER
            "visitor" -> WorldRole.VISITOR
            else -> null
        }
        debugLogger.debugMethodExit("parseRole", result)
        return result
    }

    private fun parsePublicRole(role: String): WorldRole? {
        debugLogger.debugMethodEntry("parsePublicRole", "role" to role)
        val result = when (role.lowercase()) {
            "member" -> WorldRole.MEMBER
            "visitor" -> WorldRole.VISITOR
            else -> null
        }
        debugLogger.debugMethodExit("parsePublicRole", result)
        return result
    }

    /**
     * Get the player's owned world they are currently in.
     * Returns null if not in a world they own.
     */
    private fun getCurrentOwnedWorld(player: Player): PlayerWorld? {
        debugLogger.debugMethodEntry("getCurrentOwnedWorld", "player" to player.name, "bukkitWorld" to player.world.name)
        val currentWorld = worldManager.getPlayerWorldFromBukkitWorld(player.world)
        debugLogger.debug("Player world lookup", "bukkitWorld" to player.world.name, "found" to (currentWorld != null))

        if (currentWorld == null) {
            debugLogger.debugMethodExit("getCurrentOwnedWorld", null)
            return null
        }

        val isOwner = currentWorld.ownerUuid == player.uniqueId
        debugLogger.debug("Ownership check", "worldOwner" to currentWorld.ownerUuid, "player" to player.uniqueId, "isOwner" to isOwner)
        if (!isOwner) {
            debugLogger.debugMethodExit("getCurrentOwnedWorld", "not owner")
            return null
        }

        debugLogger.debugMethodExit("getCurrentOwnedWorld", currentWorld.name)
        return currentWorld
    }

    /**
     * Get a world where the player has manager permission or higher.
     * Used for commands that managers can also execute.
     */
    private fun getWorldWithPermission(player: Player, name: String): PlayerWorld? {
        debugLogger.debugMethodEntry("getWorldWithPermission", "playerUuid" to player.uniqueId, "name" to name)

        // First try owned worlds
        val ownedWorld = dataManager.getWorldsByOwner(player.uniqueId)
            .firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (ownedWorld != null) {
            debugLogger.debugMethodExit("getWorldWithPermission", "owned: ${ownedWorld.name}")
            return ownedWorld
        }

        // Then check if they're a manager in any world with that name
        val allWorlds = dataManager.getAllWorlds()
        val managedWorld = allWorlds.firstOrNull { world ->
            world.name.equals(name, ignoreCase = true) &&
            world.getPlayerRole(player.uniqueId)?.canInvite() == true
        }

        if (managedWorld != null) {
            debugLogger.debugMethodExit("getWorldWithPermission", "managed: ${managedWorld.name}")
            return managedWorld
        }

        player.sendMessage(
            Component.text("You don't have permission for world '", NamedTextColor.RED)
                .append(Component.text(name, NamedTextColor.GOLD))
                .append(Component.text("'", NamedTextColor.RED))
        )

        debugLogger.debugMethodExit("getWorldWithPermission", null)
        return null
    }
}
