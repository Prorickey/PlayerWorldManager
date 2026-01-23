package tech.bedson.playerworldmanager.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.DoubleArgumentType
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
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.gui.WorldBorderGui
import tech.bedson.playerworldmanager.managers.DataManager
import tech.bedson.playerworldmanager.managers.WorldManager
import tech.bedson.playerworldmanager.models.PlayerWorld
import tech.bedson.playerworldmanager.utils.DebugLogger
import java.util.concurrent.CompletableFuture

/**
 * Brigadier command builder for /worldborder command.
 * Provides world border management for world owners.
 * Mirrors Minecraft's vanilla /worldborder command structure.
 */
@Suppress("UnstableApiUsage")
class WorldBorderCommands(
    private val plugin: JavaPlugin,
    private val worldManager: WorldManager,
    private val dataManager: DataManager,
    private val worldBorderGui: WorldBorderGui
) {
    private val debugLogger = DebugLogger(plugin, "WorldBorderCommands")

    fun build(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("worldborder")
            // /worldborder - Opens GUI or shows help
            .executes(::handleDefault)

            // /worldborder get [world] - Get current border size
            .then(Commands.literal("get")
                .then(Commands.argument("world", StringArgumentType.word())
                    .suggests(::suggestOwnedWorlds)
                    .executes(::handleGetWithWorld)
                )
                .executes(::handleGet)
            )

            // /worldborder set <size> [time] [world] - Set border size
            .then(Commands.literal("set")
                .then(Commands.argument("size", DoubleArgumentType.doubleArg(1.0, 60000000.0))
                    .then(Commands.argument("time", IntegerArgumentType.integer(0))
                        .then(Commands.argument("world", StringArgumentType.word())
                            .suggests(::suggestOwnedWorlds)
                            .executes(::handleSetWithTimeAndWorld)
                        )
                        .executes(::handleSetWithTime)
                    )
                    .then(Commands.argument("world", StringArgumentType.word())
                        .suggests(::suggestOwnedWorlds)
                        .executes(::handleSetWithWorld)
                    )
                    .executes(::handleSet)
                )
            )

            // /worldborder add <size> [time] [world] - Add to border size
            .then(Commands.literal("add")
                .then(Commands.argument("size", DoubleArgumentType.doubleArg(-60000000.0, 60000000.0))
                    .then(Commands.argument("time", IntegerArgumentType.integer(0))
                        .then(Commands.argument("world", StringArgumentType.word())
                            .suggests(::suggestOwnedWorlds)
                            .executes(::handleAddWithTimeAndWorld)
                        )
                        .executes(::handleAddWithTime)
                    )
                    .then(Commands.argument("world", StringArgumentType.word())
                        .suggests(::suggestOwnedWorlds)
                        .executes(::handleAddWithWorld)
                    )
                    .executes(::handleAdd)
                )
            )

            // /worldborder center <x> <z> [world] - Set border center
            .then(Commands.literal("center")
                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                    .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("world", StringArgumentType.word())
                            .suggests(::suggestOwnedWorlds)
                            .executes(::handleCenterWithWorld)
                        )
                        .executes(::handleCenter)
                    )
                )
            )

            // /worldborder damage - Damage subcommands
            .then(Commands.literal("damage")
                // /worldborder damage amount <damage> [world]
                .then(Commands.literal("amount")
                    .then(Commands.argument("damage", DoubleArgumentType.doubleArg(0.0))
                        .then(Commands.argument("world", StringArgumentType.word())
                            .suggests(::suggestOwnedWorlds)
                            .executes(::handleDamageAmountWithWorld)
                        )
                        .executes(::handleDamageAmount)
                    )
                )
                // /worldborder damage buffer <distance> [world]
                .then(Commands.literal("buffer")
                    .then(Commands.argument("distance", DoubleArgumentType.doubleArg(0.0))
                        .then(Commands.argument("world", StringArgumentType.word())
                            .suggests(::suggestOwnedWorlds)
                            .executes(::handleDamageBufferWithWorld)
                        )
                        .executes(::handleDamageBuffer)
                    )
                )
            )

            // /worldborder warning - Warning subcommands
            .then(Commands.literal("warning")
                // /worldborder warning distance <distance> [world]
                .then(Commands.literal("distance")
                    .then(Commands.argument("distance", IntegerArgumentType.integer(0))
                        .then(Commands.argument("world", StringArgumentType.word())
                            .suggests(::suggestOwnedWorlds)
                            .executes(::handleWarningDistanceWithWorld)
                        )
                        .executes(::handleWarningDistance)
                    )
                )
                // /worldborder warning time <time> [world]
                .then(Commands.literal("time")
                    .then(Commands.argument("time", IntegerArgumentType.integer(0))
                        .then(Commands.argument("world", StringArgumentType.word())
                            .suggests(::suggestOwnedWorlds)
                            .executes(::handleWarningTimeWithWorld)
                        )
                        .executes(::handleWarningTime)
                    )
                )
            )

            // /worldborder menu [world] - Open GUI
            .then(Commands.literal("menu")
                .then(Commands.argument("world", StringArgumentType.word())
                    .suggests(::suggestOwnedWorlds)
                    .executes(::handleMenuWithWorld)
                )
                .executes(::handleMenu)
            )

            // /worldborder help
            .then(Commands.literal("help")
                .executes(::handleHelp)
            )

            .build()
    }

    // ========================
    // Helper Methods
    // ========================

    private fun sendPlayerOnlyError(ctx: CommandContext<CommandSourceStack>): Int {
        ctx.source.sender.sendMessage(
            Component.text("This command can only be run by players!", NamedTextColor.RED)
        )
        return Command.SINGLE_SUCCESS
    }

    private fun getPlayerWorld(player: Player, worldName: String?): PlayerWorld? {
        return if (worldName != null) {
            dataManager.getWorldsByOwner(player.uniqueId)
                .find { it.name.equals(worldName, ignoreCase = true) }
        } else {
            // Try to get the world the player is currently in
            val currentWorld = player.world
            dataManager.getAllWorlds()
                .find { world ->
                    val bukkitWorld = Bukkit.getWorld(world.name)
                    bukkitWorld == currentWorld && world.ownerUuid == player.uniqueId
                }
        }
    }

    private fun requireOwnedWorld(player: Player, worldName: String?): PlayerWorld? {
        val world = getPlayerWorld(player, worldName)
        if (world == null) {
            if (worldName != null) {
                player.sendMessage(
                    Component.text("You don't own a world named ", NamedTextColor.RED)
                        .append(Component.text(worldName, NamedTextColor.GOLD))
                )
            } else {
                player.sendMessage(
                    Component.text("You are not in one of your own worlds. ", NamedTextColor.RED)
                        .append(Component.text("Specify a world name or teleport to your world first.", NamedTextColor.GRAY))
                )
            }
        }
        return world
    }

    private fun applyWorldBorder(world: PlayerWorld) {
        val bukkitWorld = Bukkit.getWorld(world.name) ?: return
        val border = bukkitWorld.worldBorder
        val settings = world.worldBorder

        Bukkit.getGlobalRegionScheduler().run(plugin) { _ ->
            border.center = org.bukkit.Location(bukkitWorld, settings.centerX, 0.0, settings.centerZ)
            border.size = settings.size
            border.damageAmount = settings.damageAmount
            border.damageBuffer = settings.damageBuffer
            border.warningDistance = settings.warningDistance
            border.warningTime = settings.warningTime
        }
    }

    private fun applyWorldBorderWithTransition(world: PlayerWorld, targetSize: Double, timeSeconds: Int) {
        val bukkitWorld = Bukkit.getWorld(world.name) ?: return
        val border = bukkitWorld.worldBorder
        val settings = world.worldBorder

        Bukkit.getGlobalRegionScheduler().run(plugin) { _ ->
            border.center = org.bukkit.Location(bukkitWorld, settings.centerX, 0.0, settings.centerZ)
            border.damageAmount = settings.damageAmount
            border.damageBuffer = settings.damageBuffer
            border.warningDistance = settings.warningDistance
            border.warningTime = settings.warningTime

            if (timeSeconds > 0) {
                border.setSize(targetSize, timeSeconds.toLong())
            } else {
                border.size = targetSize
            }
        }
    }

    // ========================
    // Command Handlers
    // ========================

    private fun handleDefault(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val world = requireOwnedWorld(player, null)

        if (world != null) {
            player.scheduler.run(plugin, { _ ->
                worldBorderGui.open(player, world)
            }, null)
        } else {
            // Show help if not in own world
            return handleHelp(ctx)
        }
        return Command.SINGLE_SUCCESS
    }

    private fun handleGet(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val world = requireOwnedWorld(player, null) ?: return Command.SINGLE_SUCCESS

        val settings = world.worldBorder
        player.sendMessage(
            Component.text("World border for ", NamedTextColor.GREEN)
                .append(Component.text(world.name, NamedTextColor.GOLD))
                .append(Component.text(" is currently ", NamedTextColor.GREEN))
                .append(Component.text("${settings.size.toLong()}", NamedTextColor.YELLOW))
                .append(Component.text(" blocks wide", NamedTextColor.GREEN))
        )
        return Command.SINGLE_SUCCESS
    }

    private fun handleGetWithWorld(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val worldName = StringArgumentType.getString(ctx, "world")
        val world = requireOwnedWorld(player, worldName) ?: return Command.SINGLE_SUCCESS

        val settings = world.worldBorder
        player.sendMessage(
            Component.text("World border for ", NamedTextColor.GREEN)
                .append(Component.text(world.name, NamedTextColor.GOLD))
                .append(Component.text(" is currently ", NamedTextColor.GREEN))
                .append(Component.text("${settings.size.toLong()}", NamedTextColor.YELLOW))
                .append(Component.text(" blocks wide", NamedTextColor.GREEN))
        )
        return Command.SINGLE_SUCCESS
    }

    private fun handleSet(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val world = requireOwnedWorld(player, null) ?: return Command.SINGLE_SUCCESS
        val size = DoubleArgumentType.getDouble(ctx, "size")

        world.worldBorder.size = size
        dataManager.saveWorld(world)
        applyWorldBorder(world)

        player.sendMessage(
            Component.text("Set world border size to ", NamedTextColor.GREEN)
                .append(Component.text("${size.toLong()}", NamedTextColor.YELLOW))
                .append(Component.text(" blocks", NamedTextColor.GREEN))
        )
        return Command.SINGLE_SUCCESS
    }

    private fun handleSetWithWorld(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val worldName = StringArgumentType.getString(ctx, "world")
        val world = requireOwnedWorld(player, worldName) ?: return Command.SINGLE_SUCCESS
        val size = DoubleArgumentType.getDouble(ctx, "size")

        world.worldBorder.size = size
        dataManager.saveWorld(world)
        applyWorldBorder(world)

        player.sendMessage(
            Component.text("Set world border size for ", NamedTextColor.GREEN)
                .append(Component.text(world.name, NamedTextColor.GOLD))
                .append(Component.text(" to ", NamedTextColor.GREEN))
                .append(Component.text("${size.toLong()}", NamedTextColor.YELLOW))
                .append(Component.text(" blocks", NamedTextColor.GREEN))
        )
        return Command.SINGLE_SUCCESS
    }

    private fun handleSetWithTime(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val world = requireOwnedWorld(player, null) ?: return Command.SINGLE_SUCCESS
        val size = DoubleArgumentType.getDouble(ctx, "size")
        val time = IntegerArgumentType.getInteger(ctx, "time")

        world.worldBorder.size = size
        dataManager.saveWorld(world)
        applyWorldBorderWithTransition(world, size, time)

        if (time > 0) {
            player.sendMessage(
                Component.text("Setting world border size to ", NamedTextColor.GREEN)
                    .append(Component.text("${size.toLong()}", NamedTextColor.YELLOW))
                    .append(Component.text(" blocks over ", NamedTextColor.GREEN))
                    .append(Component.text("$time", NamedTextColor.YELLOW))
                    .append(Component.text(" seconds", NamedTextColor.GREEN))
            )
        } else {
            player.sendMessage(
                Component.text("Set world border size to ", NamedTextColor.GREEN)
                    .append(Component.text("${size.toLong()}", NamedTextColor.YELLOW))
                    .append(Component.text(" blocks", NamedTextColor.GREEN))
            )
        }
        return Command.SINGLE_SUCCESS
    }

    private fun handleSetWithTimeAndWorld(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val worldName = StringArgumentType.getString(ctx, "world")
        val world = requireOwnedWorld(player, worldName) ?: return Command.SINGLE_SUCCESS
        val size = DoubleArgumentType.getDouble(ctx, "size")
        val time = IntegerArgumentType.getInteger(ctx, "time")

        world.worldBorder.size = size
        dataManager.saveWorld(world)
        applyWorldBorderWithTransition(world, size, time)

        if (time > 0) {
            player.sendMessage(
                Component.text("Setting world border size for ", NamedTextColor.GREEN)
                    .append(Component.text(world.name, NamedTextColor.GOLD))
                    .append(Component.text(" to ", NamedTextColor.GREEN))
                    .append(Component.text("${size.toLong()}", NamedTextColor.YELLOW))
                    .append(Component.text(" blocks over ", NamedTextColor.GREEN))
                    .append(Component.text("$time", NamedTextColor.YELLOW))
                    .append(Component.text(" seconds", NamedTextColor.GREEN))
            )
        } else {
            player.sendMessage(
                Component.text("Set world border size for ", NamedTextColor.GREEN)
                    .append(Component.text(world.name, NamedTextColor.GOLD))
                    .append(Component.text(" to ", NamedTextColor.GREEN))
                    .append(Component.text("${size.toLong()}", NamedTextColor.YELLOW))
                    .append(Component.text(" blocks", NamedTextColor.GREEN))
            )
        }
        return Command.SINGLE_SUCCESS
    }

    private fun handleAdd(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val world = requireOwnedWorld(player, null) ?: return Command.SINGLE_SUCCESS
        val amount = DoubleArgumentType.getDouble(ctx, "size")

        val newSize = (world.worldBorder.size + amount).coerceIn(1.0, 60000000.0)
        world.worldBorder.size = newSize
        dataManager.saveWorld(world)
        applyWorldBorder(world)

        val verb = if (amount >= 0) "Increased" else "Decreased"
        player.sendMessage(
            Component.text("$verb world border to ", NamedTextColor.GREEN)
                .append(Component.text("${newSize.toLong()}", NamedTextColor.YELLOW))
                .append(Component.text(" blocks", NamedTextColor.GREEN))
        )
        return Command.SINGLE_SUCCESS
    }

    private fun handleAddWithWorld(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val worldName = StringArgumentType.getString(ctx, "world")
        val world = requireOwnedWorld(player, worldName) ?: return Command.SINGLE_SUCCESS
        val amount = DoubleArgumentType.getDouble(ctx, "size")

        val newSize = (world.worldBorder.size + amount).coerceIn(1.0, 60000000.0)
        world.worldBorder.size = newSize
        dataManager.saveWorld(world)
        applyWorldBorder(world)

        val verb = if (amount >= 0) "Increased" else "Decreased"
        player.sendMessage(
            Component.text("$verb world border for ", NamedTextColor.GREEN)
                .append(Component.text(world.name, NamedTextColor.GOLD))
                .append(Component.text(" to ", NamedTextColor.GREEN))
                .append(Component.text("${newSize.toLong()}", NamedTextColor.YELLOW))
                .append(Component.text(" blocks", NamedTextColor.GREEN))
        )
        return Command.SINGLE_SUCCESS
    }

    private fun handleAddWithTime(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val world = requireOwnedWorld(player, null) ?: return Command.SINGLE_SUCCESS
        val amount = DoubleArgumentType.getDouble(ctx, "size")
        val time = IntegerArgumentType.getInteger(ctx, "time")

        val newSize = (world.worldBorder.size + amount).coerceIn(1.0, 60000000.0)
        world.worldBorder.size = newSize
        dataManager.saveWorld(world)
        applyWorldBorderWithTransition(world, newSize, time)

        val verb = if (amount >= 0) "Increasing" else "Decreasing"
        if (time > 0) {
            player.sendMessage(
                Component.text("$verb world border to ", NamedTextColor.GREEN)
                    .append(Component.text("${newSize.toLong()}", NamedTextColor.YELLOW))
                    .append(Component.text(" blocks over ", NamedTextColor.GREEN))
                    .append(Component.text("$time", NamedTextColor.YELLOW))
                    .append(Component.text(" seconds", NamedTextColor.GREEN))
            )
        } else {
            val verbPast = if (amount >= 0) "Increased" else "Decreased"
            player.sendMessage(
                Component.text("$verbPast world border to ", NamedTextColor.GREEN)
                    .append(Component.text("${newSize.toLong()}", NamedTextColor.YELLOW))
                    .append(Component.text(" blocks", NamedTextColor.GREEN))
            )
        }
        return Command.SINGLE_SUCCESS
    }

    private fun handleAddWithTimeAndWorld(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val worldName = StringArgumentType.getString(ctx, "world")
        val world = requireOwnedWorld(player, worldName) ?: return Command.SINGLE_SUCCESS
        val amount = DoubleArgumentType.getDouble(ctx, "size")
        val time = IntegerArgumentType.getInteger(ctx, "time")

        val newSize = (world.worldBorder.size + amount).coerceIn(1.0, 60000000.0)
        world.worldBorder.size = newSize
        dataManager.saveWorld(world)
        applyWorldBorderWithTransition(world, newSize, time)

        val verb = if (amount >= 0) "Increasing" else "Decreasing"
        if (time > 0) {
            player.sendMessage(
                Component.text("$verb world border for ", NamedTextColor.GREEN)
                    .append(Component.text(world.name, NamedTextColor.GOLD))
                    .append(Component.text(" to ", NamedTextColor.GREEN))
                    .append(Component.text("${newSize.toLong()}", NamedTextColor.YELLOW))
                    .append(Component.text(" blocks over ", NamedTextColor.GREEN))
                    .append(Component.text("$time", NamedTextColor.YELLOW))
                    .append(Component.text(" seconds", NamedTextColor.GREEN))
            )
        } else {
            val verbPast = if (amount >= 0) "Increased" else "Decreased"
            player.sendMessage(
                Component.text("$verbPast world border for ", NamedTextColor.GREEN)
                    .append(Component.text(world.name, NamedTextColor.GOLD))
                    .append(Component.text(" to ", NamedTextColor.GREEN))
                    .append(Component.text("${newSize.toLong()}", NamedTextColor.YELLOW))
                    .append(Component.text(" blocks", NamedTextColor.GREEN))
            )
        }
        return Command.SINGLE_SUCCESS
    }

    private fun handleCenter(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val world = requireOwnedWorld(player, null) ?: return Command.SINGLE_SUCCESS
        val x = DoubleArgumentType.getDouble(ctx, "x")
        val z = DoubleArgumentType.getDouble(ctx, "z")

        world.worldBorder.centerX = x
        world.worldBorder.centerZ = z
        dataManager.saveWorld(world)
        applyWorldBorder(world)

        player.sendMessage(
            Component.text("Set world border center to ", NamedTextColor.GREEN)
                .append(Component.text("${x.toLong()}, ${z.toLong()}", NamedTextColor.YELLOW))
        )
        return Command.SINGLE_SUCCESS
    }

    private fun handleCenterWithWorld(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val worldName = StringArgumentType.getString(ctx, "world")
        val world = requireOwnedWorld(player, worldName) ?: return Command.SINGLE_SUCCESS
        val x = DoubleArgumentType.getDouble(ctx, "x")
        val z = DoubleArgumentType.getDouble(ctx, "z")

        world.worldBorder.centerX = x
        world.worldBorder.centerZ = z
        dataManager.saveWorld(world)
        applyWorldBorder(world)

        player.sendMessage(
            Component.text("Set world border center for ", NamedTextColor.GREEN)
                .append(Component.text(world.name, NamedTextColor.GOLD))
                .append(Component.text(" to ", NamedTextColor.GREEN))
                .append(Component.text("${x.toLong()}, ${z.toLong()}", NamedTextColor.YELLOW))
        )
        return Command.SINGLE_SUCCESS
    }

    private fun handleDamageAmount(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val world = requireOwnedWorld(player, null) ?: return Command.SINGLE_SUCCESS
        val damage = DoubleArgumentType.getDouble(ctx, "damage")

        world.worldBorder.damageAmount = damage
        dataManager.saveWorld(world)
        applyWorldBorder(world)

        player.sendMessage(
            Component.text("Set world border damage to ", NamedTextColor.GREEN)
                .append(Component.text("$damage", NamedTextColor.YELLOW))
                .append(Component.text(" per block", NamedTextColor.GREEN))
        )
        return Command.SINGLE_SUCCESS
    }

    private fun handleDamageAmountWithWorld(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val worldName = StringArgumentType.getString(ctx, "world")
        val world = requireOwnedWorld(player, worldName) ?: return Command.SINGLE_SUCCESS
        val damage = DoubleArgumentType.getDouble(ctx, "damage")

        world.worldBorder.damageAmount = damage
        dataManager.saveWorld(world)
        applyWorldBorder(world)

        player.sendMessage(
            Component.text("Set world border damage for ", NamedTextColor.GREEN)
                .append(Component.text(world.name, NamedTextColor.GOLD))
                .append(Component.text(" to ", NamedTextColor.GREEN))
                .append(Component.text("$damage", NamedTextColor.YELLOW))
                .append(Component.text(" per block", NamedTextColor.GREEN))
        )
        return Command.SINGLE_SUCCESS
    }

    private fun handleDamageBuffer(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val world = requireOwnedWorld(player, null) ?: return Command.SINGLE_SUCCESS
        val buffer = DoubleArgumentType.getDouble(ctx, "distance")

        world.worldBorder.damageBuffer = buffer
        dataManager.saveWorld(world)
        applyWorldBorder(world)

        player.sendMessage(
            Component.text("Set world border damage buffer to ", NamedTextColor.GREEN)
                .append(Component.text("$buffer", NamedTextColor.YELLOW))
                .append(Component.text(" blocks", NamedTextColor.GREEN))
        )
        return Command.SINGLE_SUCCESS
    }

    private fun handleDamageBufferWithWorld(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val worldName = StringArgumentType.getString(ctx, "world")
        val world = requireOwnedWorld(player, worldName) ?: return Command.SINGLE_SUCCESS
        val buffer = DoubleArgumentType.getDouble(ctx, "distance")

        world.worldBorder.damageBuffer = buffer
        dataManager.saveWorld(world)
        applyWorldBorder(world)

        player.sendMessage(
            Component.text("Set world border damage buffer for ", NamedTextColor.GREEN)
                .append(Component.text(world.name, NamedTextColor.GOLD))
                .append(Component.text(" to ", NamedTextColor.GREEN))
                .append(Component.text("$buffer", NamedTextColor.YELLOW))
                .append(Component.text(" blocks", NamedTextColor.GREEN))
        )
        return Command.SINGLE_SUCCESS
    }

    private fun handleWarningDistance(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val world = requireOwnedWorld(player, null) ?: return Command.SINGLE_SUCCESS
        val distance = IntegerArgumentType.getInteger(ctx, "distance")

        world.worldBorder.warningDistance = distance
        dataManager.saveWorld(world)
        applyWorldBorder(world)

        player.sendMessage(
            Component.text("Set world border warning distance to ", NamedTextColor.GREEN)
                .append(Component.text("$distance", NamedTextColor.YELLOW))
                .append(Component.text(" blocks", NamedTextColor.GREEN))
        )
        return Command.SINGLE_SUCCESS
    }

    private fun handleWarningDistanceWithWorld(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val worldName = StringArgumentType.getString(ctx, "world")
        val world = requireOwnedWorld(player, worldName) ?: return Command.SINGLE_SUCCESS
        val distance = IntegerArgumentType.getInteger(ctx, "distance")

        world.worldBorder.warningDistance = distance
        dataManager.saveWorld(world)
        applyWorldBorder(world)

        player.sendMessage(
            Component.text("Set world border warning distance for ", NamedTextColor.GREEN)
                .append(Component.text(world.name, NamedTextColor.GOLD))
                .append(Component.text(" to ", NamedTextColor.GREEN))
                .append(Component.text("$distance", NamedTextColor.YELLOW))
                .append(Component.text(" blocks", NamedTextColor.GREEN))
        )
        return Command.SINGLE_SUCCESS
    }

    private fun handleWarningTime(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val world = requireOwnedWorld(player, null) ?: return Command.SINGLE_SUCCESS
        val time = IntegerArgumentType.getInteger(ctx, "time")

        world.worldBorder.warningTime = time
        dataManager.saveWorld(world)
        applyWorldBorder(world)

        player.sendMessage(
            Component.text("Set world border warning time to ", NamedTextColor.GREEN)
                .append(Component.text("$time", NamedTextColor.YELLOW))
                .append(Component.text(" seconds", NamedTextColor.GREEN))
        )
        return Command.SINGLE_SUCCESS
    }

    private fun handleWarningTimeWithWorld(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val worldName = StringArgumentType.getString(ctx, "world")
        val world = requireOwnedWorld(player, worldName) ?: return Command.SINGLE_SUCCESS
        val time = IntegerArgumentType.getInteger(ctx, "time")

        world.worldBorder.warningTime = time
        dataManager.saveWorld(world)
        applyWorldBorder(world)

        player.sendMessage(
            Component.text("Set world border warning time for ", NamedTextColor.GREEN)
                .append(Component.text(world.name, NamedTextColor.GOLD))
                .append(Component.text(" to ", NamedTextColor.GREEN))
                .append(Component.text("$time", NamedTextColor.YELLOW))
                .append(Component.text(" seconds", NamedTextColor.GREEN))
        )
        return Command.SINGLE_SUCCESS
    }

    private fun handleMenu(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val world = requireOwnedWorld(player, null) ?: return Command.SINGLE_SUCCESS

        player.scheduler.run(plugin, { _ ->
            worldBorderGui.open(player, world)
        }, null)
        return Command.SINGLE_SUCCESS
    }

    private fun handleMenuWithWorld(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.sender as? Player ?: return sendPlayerOnlyError(ctx)
        val worldName = StringArgumentType.getString(ctx, "world")
        val world = requireOwnedWorld(player, worldName) ?: return Command.SINGLE_SUCCESS

        player.scheduler.run(plugin, { _ ->
            worldBorderGui.open(player, world)
        }, null)
        return Command.SINGLE_SUCCESS
    }

    private fun handleHelp(ctx: CommandContext<CommandSourceStack>): Int {
        val sender = ctx.source.sender
        sender.sendMessage(Component.text("=== World Border Commands ===", NamedTextColor.GOLD))
        sender.sendMessage(Component.text("/worldborder", NamedTextColor.YELLOW)
            .append(Component.text(" - Open border settings GUI", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/worldborder get [world]", NamedTextColor.YELLOW)
            .append(Component.text(" - Get current border size", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/worldborder set <size> [time] [world]", NamedTextColor.YELLOW)
            .append(Component.text(" - Set border size", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/worldborder add <amount> [time] [world]", NamedTextColor.YELLOW)
            .append(Component.text(" - Add/subtract from border size", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/worldborder center <x> <z> [world]", NamedTextColor.YELLOW)
            .append(Component.text(" - Set border center", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/worldborder damage amount <damage> [world]", NamedTextColor.YELLOW)
            .append(Component.text(" - Set damage per block", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/worldborder damage buffer <distance> [world]", NamedTextColor.YELLOW)
            .append(Component.text(" - Set damage buffer distance", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/worldborder warning distance <blocks> [world]", NamedTextColor.YELLOW)
            .append(Component.text(" - Set warning distance", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/worldborder warning time <seconds> [world]", NamedTextColor.YELLOW)
            .append(Component.text(" - Set warning time", NamedTextColor.GRAY)))
        sender.sendMessage(Component.text("/worldborder menu [world]", NamedTextColor.YELLOW)
            .append(Component.text(" - Open border settings GUI", NamedTextColor.GRAY)))
        return Command.SINGLE_SUCCESS
    }

    // ========================
    // Suggestion Providers
    // ========================

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
}
