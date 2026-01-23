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
import tech.bedson.playerworldmanager.PlayerWorldManager
import tech.bedson.playerworldmanager.managers.DataManager
import tech.bedson.playerworldmanager.managers.InviteManager
import tech.bedson.playerworldmanager.managers.WorldManager
import tech.bedson.playerworldmanager.models.PlayerWorld
import tech.bedson.playerworldmanager.models.WorldType
import tech.bedson.playerworldmanager.utils.DebugLogger
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
    private val debugLogger = DebugLogger(plugin, "TestCommands")

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
            // /pwmtest build - show build info
            .then(Commands.literal("build")
                .executes(::handleBuild)
            )
            .executes(::handleHelp)
            .build()
    }

    private fun handleHelp(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleHelp", "sender" to ctx.source.sender.name)
        val sender = ctx.source.sender
        debugLogger.debug("Displaying help", "senderName" to sender.name, "testPlayer" to testPlayerName)
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
        sender.sendMessage(Component.text("/pwmtest build", NamedTextColor.YELLOW)
            .append(Component.text(" - Show current build info", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text(""))
        sender.sendMessage(Component.text("World types: normal, flat, amplified, large_biomes, void", NamedTextColor.GRAY))
        debugLogger.debugMethodExit("handleHelp", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleCreate(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleCreate", "sender" to ctx.source.sender.name)
        val result = handleCreateInternal(ctx, "normal")
        debugLogger.debugMethodExit("handleCreate", result)
        return result
    }

    private fun handleCreateWithType(ctx: CommandContext<CommandSourceStack>): Int {
        val typeStr = StringArgumentType.getString(ctx, "type")
        debugLogger.debugMethodEntry("handleCreateWithType", "sender" to ctx.source.sender.name, "typeStr" to typeStr)
        val result = handleCreateInternal(ctx, typeStr)
        debugLogger.debugMethodExit("handleCreateWithType", result)
        return result
    }

    private fun handleCreateInternal(ctx: CommandContext<CommandSourceStack>, typeStr: String): Int {
        debugLogger.debugMethodEntry("handleCreateInternal", "sender" to ctx.source.sender.name, "typeStr" to typeStr)
        val sender = ctx.source.sender
        val worldName = StringArgumentType.getString(ctx, "worldname")
        debugLogger.debug("Parsed arguments", "worldName" to worldName, "typeStr" to typeStr)

        // Check if test player is online
        val testPlayer = Bukkit.getPlayer(testPlayerName)
        debugLogger.debug("Test player lookup", "testPlayerName" to testPlayerName, "isOnline" to (testPlayer != null))
        if (testPlayer == null) {
            sender.sendMessage(Component.text("ERROR: Test player '$testPlayerName' must be online to create worlds", NamedTextColor.RED))
            sender.sendMessage(Component.text("Have them join the server first, or use /worldadmin commands", NamedTextColor.GRAY))
            debugLogger.debugMethodExit("handleCreateInternal", "test player not online")
            return Command.SINGLE_SUCCESS
        }

        val worldType = try {
            WorldType.valueOf(typeStr.uppercase())
        } catch (e: IllegalArgumentException) {
            debugLogger.debug("Invalid world type", "typeStr" to typeStr)
            sender.sendMessage(Component.text("Invalid world type: $typeStr", NamedTextColor.RED))
            sender.sendMessage(Component.text("Valid types: normal, flat, amplified, large_biomes, void", NamedTextColor.GRAY))
            debugLogger.debugMethodExit("handleCreateInternal", "invalid world type")
            return Command.SINGLE_SUCCESS
        }

        debugLogger.debug("Parsed world type", "typeStr" to typeStr, "worldType" to worldType)
        sender.sendMessage(Component.text("Creating world '$worldName' (type: $worldType) for $testPlayerName...", NamedTextColor.YELLOW))

        // Call the createWorld API
        debugLogger.debug("Calling worldManager.createWorld", "testPlayer" to testPlayer.name, "worldName" to worldName, "worldType" to worldType)
        worldManager.createWorld(testPlayer, worldName, worldType).thenAccept { result ->
            result.fold(
                onSuccess = { playerWorld ->
                    debugLogger.debug("World creation succeeded", "worldName" to playerWorld.name, "worldId" to playerWorld.id)
                    sender.sendMessage(Component.text("SUCCESS: World '${playerWorld.name}' created!", NamedTextColor.GREEN))
                    sender.sendMessage(Component.text("World ID: ${playerWorld.id}", NamedTextColor.GRAY))
                    val bukkitName = worldManager.getWorldName(playerWorld, World.Environment.NORMAL)
                    sender.sendMessage(Component.text("Bukkit world: $bukkitName", NamedTextColor.GRAY))
                },
                onFailure = { error ->
                    debugLogger.debug("World creation failed", "error" to error.message)
                    sender.sendMessage(Component.text("FAILED: ${error.message}", NamedTextColor.RED))
                }
            )
        }

        debugLogger.debugMethodExit("handleCreateInternal", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleList(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleList", "sender" to ctx.source.sender.name)
        val sender = ctx.source.sender

        // Get all worlds
        val allWorlds = dataManager.getAllWorlds()
        val testPlayerWorlds = dataManager.getWorldsByOwner(testPlayerId)
        debugLogger.debug("Retrieved worlds", "totalWorlds" to allWorlds.size, "testPlayerWorlds" to testPlayerWorlds.size, "testPlayerId" to testPlayerId)

        sender.sendMessage(Component.text("=== World List ===", NamedTextColor.GOLD))
        sender.sendMessage(Component.text("Total worlds: ${allWorlds.size}", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("$testPlayerName's worlds: ${testPlayerWorlds.size}", NamedTextColor.GRAY))
        sender.sendMessage(Component.text(""))

        if (allWorlds.isEmpty()) {
            debugLogger.debug("No worlds found")
            sender.sendMessage(Component.text("No worlds found.", NamedTextColor.YELLOW))
        } else {
            debugLogger.debug("Displaying worlds", "worldNames" to allWorlds.map { it.name })
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

        debugLogger.debugMethodExit("handleList", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleDelete(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleDelete", "sender" to ctx.source.sender.name)
        val sender = ctx.source.sender
        val worldName = StringArgumentType.getString(ctx, "worldname")
        debugLogger.debug("Parsed arguments", "worldName" to worldName)

        // Find the world (search test player's worlds first, then all)
        var playerWorld: PlayerWorld? = dataManager.getWorldsByOwner(testPlayerId).find {
            it.name.equals(worldName, ignoreCase = true)
        }
        debugLogger.debug("Test player world lookup", "testPlayerId" to testPlayerId, "found" to (playerWorld != null))

        // If not found in test player's worlds, search all worlds
        if (playerWorld == null) {
            playerWorld = dataManager.getAllWorlds().find {
                it.name.equals(worldName, ignoreCase = true) ||
                worldManager.getWorldName(it, World.Environment.NORMAL).equals(worldName, ignoreCase = true)
            }
            debugLogger.debug("All worlds lookup", "found" to (playerWorld != null))
        }

        if (playerWorld == null) {
            debugLogger.debug("World not found", "worldName" to worldName)
            sender.sendMessage(Component.text("World '$worldName' not found", NamedTextColor.RED))
            debugLogger.debugMethodExit("handleDelete", "world not found")
            return Command.SINGLE_SUCCESS
        }

        debugLogger.debug("Found world to delete", "worldName" to playerWorld.name, "worldId" to playerWorld.id, "ownerName" to playerWorld.ownerName)
        sender.sendMessage(Component.text("Deleting world '${playerWorld.name}' (owner: ${playerWorld.ownerName})...", NamedTextColor.YELLOW))

        debugLogger.debug("Calling worldManager.deleteWorld", "worldName" to playerWorld.name, "worldId" to playerWorld.id)
        worldManager.deleteWorld(playerWorld).thenAccept { result ->
            result.fold(
                onSuccess = {
                    debugLogger.debug("World deletion succeeded", "worldName" to worldName)
                    sender.sendMessage(Component.text("SUCCESS: World deleted!", NamedTextColor.GREEN))
                },
                onFailure = { error ->
                    debugLogger.debug("World deletion failed", "worldName" to worldName, "error" to error.message)
                    sender.sendMessage(Component.text("FAILED: ${error.message}", NamedTextColor.RED))
                }
            )
        }

        debugLogger.debugMethodExit("handleDelete", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleInfo(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleInfo", "sender" to ctx.source.sender.name)
        val sender = ctx.source.sender
        val worldName = StringArgumentType.getString(ctx, "worldname")
        debugLogger.debug("Parsed arguments", "worldName" to worldName)

        // Find the world (search all worlds)
        val playerWorld = dataManager.getAllWorlds().find {
            it.name.equals(worldName, ignoreCase = true) ||
            worldManager.getWorldName(it, World.Environment.NORMAL).equals(worldName, ignoreCase = true)
        }
        debugLogger.debug("World lookup", "worldName" to worldName, "found" to (playerWorld != null))

        if (playerWorld == null) {
            sender.sendMessage(Component.text("World '$worldName' not found", NamedTextColor.RED))
            debugLogger.debugMethodExit("handleInfo", "world not found")
            return Command.SINGLE_SUCCESS
        }

        val bukkitWorld = worldManager.getBukkitWorld(playerWorld)
        val loaded = bukkitWorld != null
        val bukkitName = worldManager.getWorldName(playerWorld, World.Environment.NORMAL)
        debugLogger.debugState("PlayerWorld",
            "id" to playerWorld.id,
            "name" to playerWorld.name,
            "ownerName" to playerWorld.ownerName,
            "worldType" to playerWorld.worldType,
            "isEnabled" to playerWorld.isEnabled,
            "loaded" to loaded,
            "invitedPlayers" to playerWorld.invitedPlayers.size
        )

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

        debugLogger.debugMethodExit("handleInfo", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleStatus(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleStatus", "sender" to ctx.source.sender.name)
        val sender = ctx.source.sender

        val allWorlds = dataManager.getAllWorlds()
        val loadedWorlds = allWorlds.count { worldManager.getBukkitWorld(it) != null }
        val allPlayers = dataManager.getAllPlayerData()
        val bukkitWorlds = Bukkit.getWorlds()
        debugLogger.debug("Status data retrieved",
            "totalPluginWorlds" to allWorlds.size,
            "loadedWorlds" to loadedWorlds,
            "playerDataRecords" to allPlayers.size,
            "bukkitWorlds" to bukkitWorlds.size
        )

        sender.sendMessage(Component.text("=== Plugin Status ===", NamedTextColor.GOLD))
        sender.sendMessage(Component.text("Plugin worlds: ${allWorlds.size} (${loadedWorlds} loaded)", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("Player data records: ${allPlayers.size}", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("Bukkit worlds: ${bukkitWorlds.size}", NamedTextColor.GRAY))

        sender.sendMessage(Component.text(""))
        sender.sendMessage(Component.text("Bukkit world list:", NamedTextColor.YELLOW))
        debugLogger.debug("Bukkit world names", "worlds" to bukkitWorlds.map { it.name })
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
        debugLogger.debug("Test player status", "testPlayerName" to testPlayerName, "testPlayerId" to testPlayerId, "isOnline" to testPlayerOnline)
        sender.sendMessage(Component.text("Online: $testPlayerOnline", if (testPlayerOnline) NamedTextColor.GREEN else NamedTextColor.RED))

        debugLogger.debugMethodExit("handleStatus", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleCleanup(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleCleanup", "sender" to ctx.source.sender.name)
        val sender = ctx.source.sender
        val testPlayerWorlds = dataManager.getWorldsByOwner(testPlayerId)
        debugLogger.debug("Test player worlds for cleanup", "testPlayerId" to testPlayerId, "worldCount" to testPlayerWorlds.size)

        if (testPlayerWorlds.isEmpty()) {
            debugLogger.debug("No worlds to clean up")
            sender.sendMessage(Component.text("No worlds to clean up for $testPlayerName", NamedTextColor.YELLOW))
            debugLogger.debugMethodExit("handleCleanup", "no worlds")
            return Command.SINGLE_SUCCESS
        }

        debugLogger.debug("Starting cleanup", "worldNames" to testPlayerWorlds.map { it.name })
        sender.sendMessage(Component.text("Cleaning up ${testPlayerWorlds.size} worlds for $testPlayerName...", NamedTextColor.YELLOW))

        var pending = testPlayerWorlds.size
        var deleted = 0
        var failed = 0

        testPlayerWorlds.forEach { world ->
            debugLogger.debug("Deleting world in cleanup", "worldName" to world.name, "worldId" to world.id)
            worldManager.deleteWorld(world).thenAccept { result ->
                result.fold(
                    onSuccess = {
                        deleted++
                        debugLogger.debug("Cleanup world deleted", "worldName" to world.name, "deleted" to deleted, "failed" to failed)
                        sender.sendMessage(Component.text("  Deleted: ${world.name}", NamedTextColor.GREEN))
                    },
                    onFailure = { error ->
                        failed++
                        debugLogger.debug("Cleanup world failed", "worldName" to world.name, "error" to error.message, "deleted" to deleted, "failed" to failed)
                        sender.sendMessage(Component.text("  Failed: ${world.name} - ${error.message}", NamedTextColor.RED))
                    }
                )
                pending--
                if (pending == 0) {
                    debugLogger.debug("Cleanup complete", "deleted" to deleted, "failed" to failed)
                    sender.sendMessage(Component.text("Cleanup complete: $deleted deleted, $failed failed", NamedTextColor.GOLD))
                }
            }
        }

        debugLogger.debugMethodExit("handleCleanup", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }

    private fun handleBuild(ctx: CommandContext<CommandSourceStack>): Int {
        debugLogger.debugMethodEntry("handleBuild", "sender" to ctx.source.sender.name)
        val sender = ctx.source.sender

        debugLogger.debug("Build info",
            "buildId" to PlayerWorldManager.buildId,
            "buildTime" to PlayerWorldManager.buildTime,
            "buildVersion" to PlayerWorldManager.buildVersion
        )

        sender.sendMessage(Component.text("=== Build Info ===", NamedTextColor.GOLD))
        sender.sendMessage(Component.text("Build ID: ", NamedTextColor.GRAY)
            .append(Component.text(PlayerWorldManager.buildId, NamedTextColor.GREEN)))
        sender.sendMessage(Component.text("Build Time: ", NamedTextColor.GRAY)
            .append(Component.text(PlayerWorldManager.buildTime, NamedTextColor.AQUA)))
        sender.sendMessage(Component.text("Version: ", NamedTextColor.GRAY)
            .append(Component.text(PlayerWorldManager.buildVersion, NamedTextColor.YELLOW)))

        debugLogger.debugMethodExit("handleBuild", Command.SINGLE_SUCCESS)
        return Command.SINGLE_SUCCESS
    }
}
