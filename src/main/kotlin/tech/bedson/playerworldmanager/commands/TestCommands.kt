package tech.bedson.playerworldmanager.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.managers.DataManager
import tech.bedson.playerworldmanager.managers.InviteManager
import tech.bedson.playerworldmanager.managers.WorldManager
import tech.bedson.playerworldmanager.models.PlayerWorld
import tech.bedson.playerworldmanager.models.WorldType
import java.text.SimpleDateFormat
import java.util.*

/**
 * Console test commands for testing plugin functionality without a player.
 * All commands operate as the player "Prodeathmaster".
 * Note: Some commands require the test player to be online.
 */
@Suppress("UnstableApiUsage")
class TestCommands(
    private val plugin: JavaPlugin,
    private val worldManager: WorldManager,
    private val inviteManager: InviteManager,
    private val dataManager: DataManager
) {
    // Test player name
    private val testPlayerName = "Prodeathmaster"

    // Get the test player's UUID
    private val testPlayerId: UUID
        get() = Bukkit.getOfflinePlayer(testPlayerName).uniqueId

    fun build(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("pwmtest")
            .requires { it.sender.hasPermission("playerworldmanager.admin") }
            // /pwmtest create <worldname> [type]
            .then(Commands.literal("create")
                .then(Commands.argument("worldname", StringArgumentType.word())
                    .then(Commands.argument("type", StringArgumentType.word())
                        .executes(::handleCreateWithType)
                    )
                    .executes(::handleCreate)
                )
            )
            // /pwmtest list
            .then(Commands.literal("list")
                .executes(::handleList)
            )
            // /pwmtest delete <worldname>
            .then(Commands.literal("delete")
                .then(Commands.argument("worldname", StringArgumentType.word())
                    .executes(::handleDelete)
                )
            )
            // /pwmtest info <worldname>
            .then(Commands.literal("info")
                .then(Commands.argument("worldname", StringArgumentType.word())
                    .executes(::handleInfo)
                )
            )
            // /pwmtest status
            .then(Commands.literal("status")
                .executes(::handleStatus)
            )
            // /pwmtest cleanup - delete all test worlds
            .then(Commands.literal("cleanup")
                .executes(::handleCleanup)
            )
            .executes(::handleHelp)
            .build()
    }

    private fun handleHelp(ctx: CommandContext<CommandSourceStack>): Int {
        val sender = ctx.source.sender
        sender.sendMessage(Component.text("=== PWM Test Commands ===", NamedTextColor.GOLD))
        sender.sendMessage(Component.text("Test player: $testPlayerName", NamedTextColor.GRAY))
        sender.sendMessage(Component.text(""))
        sender.sendMessage(Component.text("/pwmtest create <name> [type]", NamedTextColor.YELLOW)
            .append(Component.text(" - Create a world (requires player online)", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/pwmtest list", NamedTextColor.YELLOW)
            .append(Component.text(" - List all worlds", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/pwmtest delete <name>", NamedTextColor.YELLOW)
            .append(Component.text(" - Delete a world", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/pwmtest info <name>", NamedTextColor.YELLOW)
            .append(Component.text(" - Show world info", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/pwmtest status", NamedTextColor.YELLOW)
            .append(Component.text(" - Show plugin status", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/pwmtest cleanup", NamedTextColor.YELLOW)
            .append(Component.text(" - Delete all test worlds", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text(""))
        sender.sendMessage(Component.text("World types: normal, flat, amplified, large_biomes, void", NamedTextColor.GRAY))
        return Command.SINGLE_SUCCESS
    }

    private fun handleCreate(ctx: CommandContext<CommandSourceStack>): Int {
        return handleCreateInternal(ctx, "normal")
    }

    private fun handleCreateWithType(ctx: CommandContext<CommandSourceStack>): Int {
        val typeStr = StringArgumentType.getString(ctx, "type")
        return handleCreateInternal(ctx, typeStr)
    }

    private fun handleCreateInternal(ctx: CommandContext<CommandSourceStack>, typeStr: String): Int {
        val sender = ctx.source.sender
        val worldName = StringArgumentType.getString(ctx, "worldname")

        // Check if test player is online
        val testPlayer = Bukkit.getPlayer(testPlayerName)
        if (testPlayer == null) {
            sender.sendMessage(Component.text("ERROR: Test player '$testPlayerName' must be online to create worlds", NamedTextColor.RED))
            sender.sendMessage(Component.text("Have them join the server first, or use /worldadmin commands", NamedTextColor.GRAY))
            return Command.SINGLE_SUCCESS
        }

        val worldType = try {
            WorldType.valueOf(typeStr.uppercase())
        } catch (e: IllegalArgumentException) {
            sender.sendMessage(Component.text("Invalid world type: $typeStr", NamedTextColor.RED))
            sender.sendMessage(Component.text("Valid types: normal, flat, amplified, large_biomes, void", NamedTextColor.GRAY))
            return Command.SINGLE_SUCCESS
        }

        sender.sendMessage(Component.text("Creating world '$worldName' (type: $worldType) for $testPlayerName...", NamedTextColor.YELLOW))

        // Call the createWorld API
        worldManager.createWorld(testPlayer, worldName, worldType).thenAccept { result ->
            result.fold(
                onSuccess = { playerWorld ->
                    sender.sendMessage(Component.text("SUCCESS: World '${playerWorld.name}' created!", NamedTextColor.GREEN))
                    sender.sendMessage(Component.text("World ID: ${playerWorld.id}", NamedTextColor.GRAY))
                    val bukkitName = worldManager.getWorldName(playerWorld, World.Environment.NORMAL)
                    sender.sendMessage(Component.text("Bukkit world: $bukkitName", NamedTextColor.GRAY))
                },
                onFailure = { error ->
                    sender.sendMessage(Component.text("FAILED: ${error.message}", NamedTextColor.RED))
                }
            )
        }

        return Command.SINGLE_SUCCESS
    }

    private fun handleList(ctx: CommandContext<CommandSourceStack>): Int {
        val sender = ctx.source.sender

        // Get all worlds
        val allWorlds = dataManager.getAllWorlds()
        val testPlayerWorlds = dataManager.getWorldsByOwner(testPlayerId)

        sender.sendMessage(Component.text("=== World List ===", NamedTextColor.GOLD))
        sender.sendMessage(Component.text("Total worlds: ${allWorlds.size}", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("$testPlayerName's worlds: ${testPlayerWorlds.size}", NamedTextColor.GRAY))
        sender.sendMessage(Component.text(""))

        if (allWorlds.isEmpty()) {
            sender.sendMessage(Component.text("No worlds found.", NamedTextColor.YELLOW))
        } else {
            allWorlds.forEach { world ->
                val loaded = worldManager.getBukkitWorld(world) != null
                val loadedStr = if (loaded) "[LOADED]" else "[UNLOADED]"
                val color = if (loaded) NamedTextColor.GREEN else NamedTextColor.GRAY
                val bukkitName = worldManager.getWorldName(world, World.Environment.NORMAL)

                sender.sendMessage(Component.text("- ${world.name} ", color)
                    .append(Component.text("($bukkitName) ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("by ${world.ownerName} ", NamedTextColor.AQUA))
                    .append(Component.text(loadedStr, color)))
            }
        }

        return Command.SINGLE_SUCCESS
    }

    private fun handleDelete(ctx: CommandContext<CommandSourceStack>): Int {
        val sender = ctx.source.sender
        val worldName = StringArgumentType.getString(ctx, "worldname")

        // Find the world (search test player's worlds first, then all)
        var playerWorld: PlayerWorld? = dataManager.getWorldsByOwner(testPlayerId).find {
            it.name.equals(worldName, ignoreCase = true)
        }

        // If not found in test player's worlds, search all worlds
        if (playerWorld == null) {
            playerWorld = dataManager.getAllWorlds().find {
                it.name.equals(worldName, ignoreCase = true) ||
                worldManager.getWorldName(it, World.Environment.NORMAL).equals(worldName, ignoreCase = true)
            }
        }

        if (playerWorld == null) {
            sender.sendMessage(Component.text("World '$worldName' not found", NamedTextColor.RED))
            return Command.SINGLE_SUCCESS
        }

        sender.sendMessage(Component.text("Deleting world '${playerWorld.name}' (owner: ${playerWorld.ownerName})...", NamedTextColor.YELLOW))

        worldManager.deleteWorld(playerWorld).thenAccept { result ->
            result.fold(
                onSuccess = {
                    sender.sendMessage(Component.text("SUCCESS: World deleted!", NamedTextColor.GREEN))
                },
                onFailure = { error ->
                    sender.sendMessage(Component.text("FAILED: ${error.message}", NamedTextColor.RED))
                }
            )
        }

        return Command.SINGLE_SUCCESS
    }

    private fun handleInfo(ctx: CommandContext<CommandSourceStack>): Int {
        val sender = ctx.source.sender
        val worldName = StringArgumentType.getString(ctx, "worldname")

        // Find the world (search all worlds)
        val playerWorld = dataManager.getAllWorlds().find {
            it.name.equals(worldName, ignoreCase = true) ||
            worldManager.getWorldName(it, World.Environment.NORMAL).equals(worldName, ignoreCase = true)
        }

        if (playerWorld == null) {
            sender.sendMessage(Component.text("World '$worldName' not found", NamedTextColor.RED))
            return Command.SINGLE_SUCCESS
        }

        val bukkitWorld = worldManager.getBukkitWorld(playerWorld)
        val loaded = bukkitWorld != null
        val bukkitName = worldManager.getWorldName(playerWorld, World.Environment.NORMAL)

        sender.sendMessage(Component.text("=== World Info: ${playerWorld.name} ===", NamedTextColor.GOLD))
        sender.sendMessage(Component.text("ID: ${playerWorld.id}", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("Bukkit Name: $bukkitName", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("Owner: ${playerWorld.ownerName} (${playerWorld.ownerUuid})", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("Type: ${playerWorld.worldType}", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("Seed: ${playerWorld.seed ?: "random"}", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("Enabled: ${playerWorld.isEnabled}", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("Loaded: $loaded", if (loaded) NamedTextColor.GREEN else NamedTextColor.RED))
        sender.sendMessage(Component.text("Created: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(playerWorld.createdAt))}", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("Invited players: ${playerWorld.invitedPlayers.size}", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("Time lock: ${playerWorld.timeLock}", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("Weather lock: ${playerWorld.weatherLock}", NamedTextColor.GRAY))

        if (bukkitWorld != null) {
            sender.sendMessage(Component.text("Players online: ${bukkitWorld.playerCount}", NamedTextColor.GRAY))
        }

        return Command.SINGLE_SUCCESS
    }

    private fun handleStatus(ctx: CommandContext<CommandSourceStack>): Int {
        val sender = ctx.source.sender

        val allWorlds = dataManager.getAllWorlds()
        val loadedWorlds = allWorlds.count { worldManager.getBukkitWorld(it) != null }
        val allPlayers = dataManager.getAllPlayerData()
        val bukkitWorlds = Bukkit.getWorlds()

        sender.sendMessage(Component.text("=== Plugin Status ===", NamedTextColor.GOLD))
        sender.sendMessage(Component.text("Plugin worlds: ${allWorlds.size} (${loadedWorlds} loaded)", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("Player data records: ${allPlayers.size}", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("Bukkit worlds: ${bukkitWorlds.size}", NamedTextColor.GRAY))

        sender.sendMessage(Component.text(""))
        sender.sendMessage(Component.text("Bukkit world list:", NamedTextColor.YELLOW))
        bukkitWorlds.forEach { world ->
            val isPlugin = worldManager.isPluginWorld(world)
            val marker = if (isPlugin) "[PWM]" else "[VANILLA]"
            val color = if (isPlugin) NamedTextColor.GREEN else NamedTextColor.AQUA
            sender.sendMessage(Component.text("  - ${world.name} $marker", color))
        }

        sender.sendMessage(Component.text(""))
        sender.sendMessage(Component.text("Test player: $testPlayerName", NamedTextColor.YELLOW))
        sender.sendMessage(Component.text("Test UUID: $testPlayerId", NamedTextColor.GRAY))

        val testPlayerOnline = Bukkit.getPlayer(testPlayerName) != null
        sender.sendMessage(Component.text("Online: $testPlayerOnline", if (testPlayerOnline) NamedTextColor.GREEN else NamedTextColor.RED))

        return Command.SINGLE_SUCCESS
    }

    private fun handleCleanup(ctx: CommandContext<CommandSourceStack>): Int {
        val sender = ctx.source.sender
        val testPlayerWorlds = dataManager.getWorldsByOwner(testPlayerId)

        if (testPlayerWorlds.isEmpty()) {
            sender.sendMessage(Component.text("No worlds to clean up for $testPlayerName", NamedTextColor.YELLOW))
            return Command.SINGLE_SUCCESS
        }

        sender.sendMessage(Component.text("Cleaning up ${testPlayerWorlds.size} worlds for $testPlayerName...", NamedTextColor.YELLOW))

        var pending = testPlayerWorlds.size
        var deleted = 0
        var failed = 0

        testPlayerWorlds.forEach { world ->
            worldManager.deleteWorld(world).thenAccept { result ->
                result.fold(
                    onSuccess = {
                        deleted++
                        sender.sendMessage(Component.text("  Deleted: ${world.name}", NamedTextColor.GREEN))
                    },
                    onFailure = { error ->
                        failed++
                        sender.sendMessage(Component.text("  Failed: ${world.name} - ${error.message}", NamedTextColor.RED))
                    }
                )
                pending--
                if (pending == 0) {
                    sender.sendMessage(Component.text("Cleanup complete: $deleted deleted, $failed failed", NamedTextColor.GOLD))
                }
            }
        }

        return Command.SINGLE_SUCCESS
    }
}
