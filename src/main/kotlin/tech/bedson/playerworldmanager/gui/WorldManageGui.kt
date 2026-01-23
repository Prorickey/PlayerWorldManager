package tech.bedson.playerworldmanager.gui

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.managers.DataManager
import tech.bedson.playerworldmanager.managers.InviteManager
import tech.bedson.playerworldmanager.managers.StatsManager
import tech.bedson.playerworldmanager.managers.WorldManager
import tech.bedson.playerworldmanager.models.PlayerWorld
import tech.bedson.playerworldmanager.models.TimeLock
import tech.bedson.playerworldmanager.models.WeatherLock
import tech.bedson.playerworldmanager.utils.DebugLogger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * World management GUI for world owners.
 * Provides options to manage world settings, invite/kick players, and delete the world.
 */
class WorldManageGui(
    private val plugin: JavaPlugin,
    private val worldManager: WorldManager,
    private val inviteManager: InviteManager,
    private val dataManager: DataManager,
    private val statsManager: StatsManager? = null
) {
    private val debugLogger = DebugLogger(plugin, "WorldManageGui")

    companion object {
        private val pendingDeletes = ConcurrentHashMap<UUID, PendingDelete>()

        data class PendingDelete(
            val worldId: UUID,
            val timestamp: Long
        )
    }

    fun open(player: Player, world: PlayerWorld) {
        debugLogger.debugMethodEntry("open", "player" to player.name, "worldName" to world.name, "worldId" to world.id)
        plugin.logger.info("[GUI] WorldManageGui: Opening for player ${player.name}, world ${world.name}")
        debugLogger.debugState("WorldManageGui",
            "worldName" to world.name,
            "ownerUuid" to world.ownerUuid,
            "timeLock" to world.timeLock,
            "weatherLock" to world.weatherLock,
            "defaultGameMode" to world.defaultGameMode,
            "invitedPlayers" to world.invitedPlayers.size
        )

        val gui = Gui.gui()
            .title(Component.text("Manage: ${world.name}", NamedTextColor.GOLD))
            .rows(5)
            .disableAllInteractions()
            .create()

        // Row 1: Teleportation
        debugLogger.debug("Setting up Row 1: Teleportation")
        gui.setItem(0, createTeleportItem(player, world, World.Environment.NORMAL))
        gui.setItem(1, createTeleportItem(player, world, World.Environment.NETHER))
        gui.setItem(2, createTeleportItem(player, world, World.Environment.THE_END))
        gui.setItem(4, createInvitedPlayersItem(world))

        // Row 2: Settings
        debugLogger.debug("Setting up Row 2: Settings")
        gui.setItem(9, createTimeLockItem(player, world))
        gui.setItem(10, createWeatherLockItem(player, world))
        gui.setItem(11, createGameModeItem(player, world))
        gui.setItem(12, createSetSpawnItem(player, world))
        gui.setItem(13, createWorldBorderItem(player, world))

        // Row 3: Invite/Kick/Stats
        debugLogger.debug("Setting up Row 3: Invite/Kick/Stats")
        gui.setItem(18, createInvitePlayerItem(player, world))
        gui.setItem(19, createKickPlayerItem(player, world))
        gui.setItem(20, createStatsItem(player, world))

        // Row 4: Ownership
        debugLogger.debug("Setting up Row 4: Ownership")
        gui.setItem(27, createTransferOwnershipItem(player, world))

        // Row 5: Navigation and deletion
        debugLogger.debug("Setting up Row 5: Navigation and deletion")
        gui.setItem(36, createBackItem(player))
        gui.setItem(44, createDeleteItem(player, world))

        // Fill empty slots with gray glass panes
        debugLogger.debug("Filling empty slots with glass panes")
        for (i in 0 until 45) {
            if (gui.getGuiItem(i) == null) {
                gui.setItem(i, ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                    .name(Component.empty())
                    .asGuiItem())
            }
        }

        debugLogger.debug("Opening GUI for player", "player" to player.name)
        gui.open(player)
        debugLogger.debugMethodExit("open")
    }

    private fun createTeleportItem(player: Player, world: PlayerWorld, environment: World.Environment): GuiItem {
        debugLogger.debugMethodEntry("createTeleportItem", "player" to player.name, "worldName" to world.name, "environment" to environment)
        val (material, name) = when (environment) {
            World.Environment.NORMAL -> Material.COMPASS to "Teleport to Overworld"
            World.Environment.NETHER -> Material.NETHERRACK to "Teleport to Nether"
            World.Environment.THE_END -> Material.END_STONE to "Teleport to End"
            else -> Material.COMPASS to "Teleport"
        }
        debugLogger.debug("Creating teleport item", "material" to material, "name" to name)

        val item = ItemBuilder.from(material)
            .name(Component.text(name, NamedTextColor.GREEN))
            .lore(listOf(Component.text("Click to teleport", NamedTextColor.YELLOW)))
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] WorldManageGui: Player ${player.name} clicked teleport to ${environment.name} in world ${world.name}")
                debugLogger.debug("Teleport button clicked", "player" to player.name, "environment" to environment, "worldName" to world.name)
                player.closeInventory()
                player.scheduler.run(plugin, { _ ->
                    debugLogger.debug("Initiating teleport to dimension", "player" to player.name, "environment" to environment)
                    worldManager.teleportToDimension(player, world, environment).thenAccept { success ->
                        if (success) {
                            plugin.logger.info("[GUI] WorldManageGui: Player ${player.name} successfully teleported to ${environment.name} in world ${world.name}")
                            debugLogger.debug("Teleport successful", "player" to player.name, "environment" to environment)
                            player.sendMessage(
                                Component.text("Teleported to ", NamedTextColor.GREEN)
                                    .append(Component.text(world.name, NamedTextColor.GOLD))
                            )
                        } else {
                            plugin.logger.warning("[GUI] WorldManageGui: Player ${player.name} failed to teleport to ${environment.name} in world ${world.name}")
                            debugLogger.debug("Teleport failed", "player" to player.name, "environment" to environment)
                            player.sendMessage(
                                Component.text("Failed to teleport to world", NamedTextColor.RED)
                            )
                        }
                    }
                }, null)
            }
        debugLogger.debugMethodExit("createTeleportItem")
        return item
    }

    private fun createInvitedPlayersItem(world: PlayerWorld): GuiItem {
        debugLogger.debugMethodEntry("createInvitedPlayersItem", "worldName" to world.name)
        val invitedCount = world.invitedPlayers.size
        val invitedNames = world.invitedPlayers.take(5).map { uuid ->
            Bukkit.getOfflinePlayer(uuid).name ?: uuid.toString()
        }
        debugLogger.debugState("InvitedPlayersItem", "invitedCount" to invitedCount, "displayedNames" to invitedNames.size)

        val lore = buildList<Component> {
            add(Component.text("Invited Players: $invitedCount", NamedTextColor.GRAY))
            if (invitedNames.isNotEmpty()) {
                add(Component.empty())
                invitedNames.forEach { name ->
                    add(Component.text("  - $name", NamedTextColor.GRAY))
                }
                if (invitedCount > 5) {
                    add(Component.text("  ...and ${invitedCount - 5} more", NamedTextColor.GRAY))
                }
            }
        }

        val item = ItemBuilder.from(Material.PLAYER_HEAD)
            .name(Component.text("Invited Players", NamedTextColor.AQUA))
            .lore(lore)
            .asGuiItem { event ->
                event.isCancelled = true
                debugLogger.debug("Invited Players item clicked (display only)", "worldName" to world.name)
            }
        debugLogger.debugMethodExit("createInvitedPlayersItem", "invitedCount=$invitedCount")
        return item
    }

    private fun createTimeLockItem(player: Player, world: PlayerWorld): GuiItem {
        debugLogger.debugMethodEntry("createTimeLockItem", "player" to player.name, "worldName" to world.name)
        val currentLock = world.timeLock
        val nextLock = when (currentLock) {
            TimeLock.CYCLE -> TimeLock.DAY
            TimeLock.DAY -> TimeLock.NIGHT
            TimeLock.NIGHT -> TimeLock.CYCLE
        }
        debugLogger.debugState("TimeLockItem", "currentLock" to currentLock, "nextLock" to nextLock)

        val item = ItemBuilder.from(Material.CLOCK)
            .name(Component.text("Time Setting", NamedTextColor.YELLOW))
            .lore(
                listOf(
                    Component.text("Current: $currentLock", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Click to change to $nextLock", NamedTextColor.YELLOW)
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] WorldManageGui: Player ${player.name} changing time lock from $currentLock to $nextLock in world ${world.name}")
                debugLogger.debug("Time lock button clicked", "player" to player.name, "currentLock" to currentLock, "nextLock" to nextLock)
                world.timeLock = nextLock
                dataManager.saveWorld(world)
                debugLogger.debug("Time lock saved, applying world settings", "worldName" to world.name)
                Bukkit.getGlobalRegionScheduler().run(plugin) { _ ->
                    worldManager.applyWorldSettings(world)
                }
                player.sendMessage(
                    Component.text("Time setting changed to ", NamedTextColor.GREEN)
                        .append(Component.text(nextLock.toString(), NamedTextColor.GOLD))
                )
                open(player, world) // Refresh GUI
            }
        debugLogger.debugMethodExit("createTimeLockItem")
        return item
    }

    private fun createWeatherLockItem(player: Player, world: PlayerWorld): GuiItem {
        debugLogger.debugMethodEntry("createWeatherLockItem", "player" to player.name, "worldName" to world.name)
        val currentLock = world.weatherLock
        val nextLock = when (currentLock) {
            WeatherLock.CYCLE -> WeatherLock.CLEAR
            WeatherLock.CLEAR -> WeatherLock.RAIN
            WeatherLock.RAIN -> WeatherLock.CYCLE
        }
        debugLogger.debugState("WeatherLockItem", "currentLock" to currentLock, "nextLock" to nextLock)

        val item = ItemBuilder.from(Material.WATER_BUCKET)
            .name(Component.text("Weather Setting", NamedTextColor.AQUA))
            .lore(
                listOf(
                    Component.text("Current: $currentLock", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Click to change to $nextLock", NamedTextColor.YELLOW)
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] WorldManageGui: Player ${player.name} changing weather lock from $currentLock to $nextLock in world ${world.name}")
                debugLogger.debug("Weather lock button clicked", "player" to player.name, "currentLock" to currentLock, "nextLock" to nextLock)
                world.weatherLock = nextLock
                dataManager.saveWorld(world)
                debugLogger.debug("Weather lock saved, applying world settings", "worldName" to world.name)
                Bukkit.getGlobalRegionScheduler().run(plugin) { _ ->
                    worldManager.applyWorldSettings(world)
                }
                player.sendMessage(
                    Component.text("Weather setting changed to ", NamedTextColor.GREEN)
                        .append(Component.text(nextLock.toString(), NamedTextColor.GOLD))
                )
                open(player, world) // Refresh GUI
            }
        debugLogger.debugMethodExit("createWeatherLockItem")
        return item
    }

    private fun createGameModeItem(player: Player, world: PlayerWorld): GuiItem {
        debugLogger.debugMethodEntry("createGameModeItem", "player" to player.name, "worldName" to world.name)
        val currentMode = world.defaultGameMode
        val nextMode = when (currentMode) {
            GameMode.SURVIVAL -> GameMode.CREATIVE
            GameMode.CREATIVE -> GameMode.ADVENTURE
            GameMode.ADVENTURE -> GameMode.SPECTATOR
            GameMode.SPECTATOR -> GameMode.SURVIVAL
        }
        debugLogger.debugState("GameModeItem", "currentMode" to currentMode, "nextMode" to nextMode)

        val item = ItemBuilder.from(Material.DIAMOND_SWORD)
            .name(Component.text("Default Gamemode", NamedTextColor.GOLD))
            .lore(
                listOf(
                    Component.text("Current: $currentMode", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Click to change to $nextMode", NamedTextColor.YELLOW)
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] WorldManageGui: Player ${player.name} changing default gamemode from $currentMode to $nextMode in world ${world.name}")
                debugLogger.debug("GameMode button clicked", "player" to player.name, "currentMode" to currentMode, "nextMode" to nextMode)
                world.defaultGameMode = nextMode
                dataManager.saveWorld(world)
                debugLogger.debug("GameMode saved", "worldName" to world.name, "newGameMode" to nextMode)
                player.sendMessage(
                    Component.text("Default gamemode changed to ", NamedTextColor.GREEN)
                        .append(Component.text(nextMode.toString(), NamedTextColor.GOLD))
                )
                open(player, world) // Refresh GUI
            }
        debugLogger.debugMethodExit("createGameModeItem")
        return item
    }

    private fun createSetSpawnItem(player: Player, world: PlayerWorld): GuiItem {
        debugLogger.debugMethodEntry("createSetSpawnItem", "player" to player.name, "worldName" to world.name)
        val item = ItemBuilder.from(Material.RED_BED)
            .name(Component.text("Set Spawn Point", NamedTextColor.RED))
            .lore(
                listOf(
                    Component.text("Sets spawn to your current location", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Click to set", NamedTextColor.YELLOW)
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] WorldManageGui: Player ${player.name} setting spawn point in world ${world.name}")
                debugLogger.debug("Set Spawn button clicked", "player" to player.name, "worldName" to world.name)
                player.closeInventory()
                player.scheduler.run(plugin, { _ ->
                    val location = player.location
                    debugLogger.debug("Setting spawn location", "worldName" to world.name, "x" to location.blockX, "y" to location.blockY, "z" to location.blockZ)
                    worldManager.setSpawnLocation(world, location)
                    plugin.logger.info("[GUI] WorldManageGui: Spawn point set for world ${world.name} at ${location.blockX}, ${location.blockY}, ${location.blockZ}")
                    debugLogger.debug("Spawn point set successfully", "worldName" to world.name)
                    player.sendMessage(
                        Component.text("Spawn point set to your current location", NamedTextColor.GREEN)
                    )
                }, null)
            }
        debugLogger.debugMethodExit("createSetSpawnItem")
        return item
    }

    private fun createWorldBorderItem(player: Player, world: PlayerWorld): GuiItem {
        debugLogger.debugMethodEntry("createWorldBorderItem", "player" to player.name, "worldName" to world.name)
        val settings = world.worldBorder
        val sizeDisplay = if (settings.size >= 60000000) "Unlimited" else "${settings.size.toLong()}"

        val item = ItemBuilder.from(Material.STRUCTURE_VOID)
            .name(Component.text("World Border", NamedTextColor.AQUA))
            .lore(
                listOf(
                    Component.text("Current size: $sizeDisplay blocks", NamedTextColor.GRAY),
                    Component.text("Center: ${settings.centerX.toLong()}, ${settings.centerZ.toLong()}", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Click to open border settings", NamedTextColor.YELLOW)
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] WorldManageGui: Player ${player.name} opening world border settings for world ${world.name}")
                debugLogger.debug("World Border button clicked", "player" to player.name, "worldName" to world.name)
                player.closeInventory()
                player.scheduler.run(plugin, { _ ->
                    debugLogger.debug("Opening WorldBorderGui", "player" to player.name, "worldName" to world.name)
                    WorldBorderGui(plugin, worldManager, inviteManager, dataManager).open(player, world)
                }, null)
            }
        debugLogger.debugMethodExit("createWorldBorderItem")
        return item
    }

    private fun createInvitePlayerItem(player: Player, world: PlayerWorld): GuiItem {
        debugLogger.debugMethodEntry("createInvitePlayerItem", "player" to player.name, "worldName" to world.name)
        val item = ItemBuilder.from(Material.NAME_TAG)
            .name(Component.text("Invite Player", NamedTextColor.GREEN))
            .lore(
                listOf(
                    Component.text("Click to invite a player", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Use /world invite <player>", NamedTextColor.YELLOW)
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true
                debugLogger.debug("Invite Player button clicked", "player" to player.name, "worldName" to world.name)
                player.closeInventory()
                player.sendMessage(
                    Component.text("Use ", NamedTextColor.YELLOW)
                        .append(Component.text("/world invite <player>", NamedTextColor.GOLD))
                        .append(Component.text(" to invite a player to ", NamedTextColor.YELLOW))
                        .append(Component.text(world.name, NamedTextColor.GOLD))
                )
            }
        debugLogger.debugMethodExit("createInvitePlayerItem")
        return item
    }

    private fun createKickPlayerItem(player: Player, world: PlayerWorld): GuiItem {
        debugLogger.debugMethodEntry("createKickPlayerItem", "player" to player.name, "worldName" to world.name)
        val invitedCount = world.invitedPlayers.size
        debugLogger.debugState("KickPlayerItem", "invitedCount" to invitedCount)

        val item = ItemBuilder.from(Material.BARRIER)
            .name(Component.text("Kick Player", NamedTextColor.RED))
            .lore(
                listOf(
                    Component.text("Invited players: $invitedCount", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Use /world kick <player>", NamedTextColor.YELLOW)
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true
                debugLogger.debug("Kick Player button clicked", "player" to player.name, "worldName" to world.name)
                player.closeInventory()
                player.sendMessage(
                    Component.text("Use ", NamedTextColor.YELLOW)
                        .append(Component.text("/world kick <player>", NamedTextColor.GOLD))
                        .append(Component.text(" to kick a player from ", NamedTextColor.YELLOW))
                        .append(Component.text(world.name, NamedTextColor.GOLD))
                )
            }
        debugLogger.debugMethodExit("createKickPlayerItem")
        return item
    }

    private fun createStatsItem(player: Player, world: PlayerWorld): GuiItem {
        debugLogger.debugMethodEntry("createStatsItem", "player" to player.name, "worldName" to world.name)

        val stats = statsManager?.getWorldStats(world.id)
        val blocksPlaced = stats?.blocksPlaced ?: 0L
        val blocksBroken = stats?.blocksBroken ?: 0L
        val mobsKilled = stats?.mobsKilled ?: 0L

        val item = ItemBuilder.from(Material.PAPER)
            .name(Component.text("World Statistics", NamedTextColor.AQUA))
            .lore(
                listOf(
                    Component.text("View statistics for this world", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Quick Stats:", NamedTextColor.YELLOW),
                    Component.text("  Blocks Placed: ${formatNumber(blocksPlaced)}", NamedTextColor.GRAY),
                    Component.text("  Blocks Broken: ${formatNumber(blocksBroken)}", NamedTextColor.GRAY),
                    Component.text("  Mobs Killed: ${formatNumber(mobsKilled)}", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Click to view detailed stats", NamedTextColor.YELLOW)
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true
                debugLogger.debug("Stats button clicked", "player" to player.name, "worldName" to world.name)
                player.closeInventory()
                if (statsManager != null) {
                    player.scheduler.run(plugin, { _ ->
                        WorldStatsGui(plugin, statsManager, worldManager, inviteManager, dataManager).open(player, world)
                    }, null)
                } else {
                    player.sendMessage(
                        Component.text("Statistics are not available.", NamedTextColor.RED)
                    )
                }
            }
        debugLogger.debugMethodExit("createStatsItem")
        return item
    }

    private fun formatNumber(number: Long): String {
        return String.format("%,d", number)
    }

    private fun createTransferOwnershipItem(player: Player, world: PlayerWorld): GuiItem {
        debugLogger.debugMethodEntry("createTransferOwnershipItem", "player" to player.name, "worldName" to world.name)
        val item = ItemBuilder.from(Material.GOLD_INGOT)
            .name(Component.text("Transfer Ownership", NamedTextColor.GOLD))
            .lore(
                listOf(
                    Component.text("Transfer this world to another player", NamedTextColor.GRAY),
                    Component.text("They must be invited first", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Use /world transfer <player>", NamedTextColor.YELLOW)
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true
                debugLogger.debug("Transfer Ownership button clicked", "player" to player.name, "worldName" to world.name)
                player.closeInventory()
                player.sendMessage(
                    Component.text("Use ", NamedTextColor.YELLOW)
                        .append(Component.text("/world transfer <player>", NamedTextColor.GOLD))
                        .append(Component.text(" to transfer ownership of ", NamedTextColor.YELLOW))
                        .append(Component.text(world.name, NamedTextColor.GOLD))
                )
            }
        debugLogger.debugMethodExit("createTransferOwnershipItem")
        return item
    }

    private fun createBackItem(player: Player): GuiItem {
        debugLogger.debugMethodEntry("createBackItem", "player" to player.name)
        val item = ItemBuilder.from(Material.ARROW)
            .name(Component.text("Back to Main Menu", NamedTextColor.YELLOW))
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] WorldManageGui: Player ${player.name} clicked Back to Main Menu")
                debugLogger.debug("Back button clicked", "player" to player.name)
                // Clear pending delete confirmation when navigating away
                pendingDeletes.remove(player.uniqueId)
                debugLogger.debug("Cleared pending delete confirmation", "player" to player.name)
                player.closeInventory()
                player.scheduler.run(plugin, { _ ->
                    debugLogger.debug("Opening MainMenuGui", "player" to player.name)
                    MainMenuGui(plugin, worldManager, inviteManager, dataManager).open(player)
                }, null)
            }
        debugLogger.debugMethodExit("createBackItem")
        return item
    }

    private fun createDeleteItem(player: Player, world: PlayerWorld): GuiItem {
        debugLogger.debugMethodEntry("createDeleteItem", "player" to player.name, "worldName" to world.name)
        val currentTime = System.currentTimeMillis()
        val pendingDelete = pendingDeletes[player.uniqueId]

        // Check if there's a pending delete for this player AND this world AND within 5 seconds
        val hasPendingDelete = pendingDelete != null &&
                pendingDelete.worldId == world.id &&
                (currentTime - pendingDelete.timestamp) <= 5000

        val needsConfirmation = !hasPendingDelete
        val material = if (needsConfirmation) Material.TNT else Material.BARRIER
        val name = if (needsConfirmation) "Delete World" else "Click again to confirm deletion"
        val color = if (needsConfirmation) NamedTextColor.RED else NamedTextColor.DARK_RED
        debugLogger.debugState("DeleteItem",
            "needsConfirmation" to needsConfirmation,
            "hasPendingDelete" to hasPendingDelete,
            "worldName" to world.name,
            "timeSinceLastClick" to if (pendingDelete != null) (currentTime - pendingDelete.timestamp) else "N/A"
        )

        val item = ItemBuilder.from(material)
            .name(Component.text(name, color))
            .lore(
                listOf(
                    Component.text("This cannot be undone!", NamedTextColor.RED),
                    Component.empty(),
                    if (needsConfirmation) {
                        Component.text("Click twice to delete", NamedTextColor.YELLOW)
                    } else {
                        Component.text("Click to DELETE PERMANENTLY", NamedTextColor.RED)
                    }
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true

                if (needsConfirmation) {
                    // First click: add to pending deletes
                    pendingDeletes[player.uniqueId] = PendingDelete(world.id, currentTime)
                    plugin.logger.info("[GUI] WorldManageGui: Player ${player.name} clicked delete for world ${world.name} (first click)")
                    debugLogger.debug("Delete button clicked (first click)", "player" to player.name, "worldName" to world.name)
                    player.sendMessage(
                        Component.text("Click delete again to confirm deletion of ", NamedTextColor.YELLOW)
                            .append(Component.text(world.name, NamedTextColor.GOLD))
                    )
                    open(player, world) // Refresh GUI to show confirmation state
                } else {
                    // Second click: execute delete and remove from pending
                    pendingDeletes.remove(player.uniqueId)
                    plugin.logger.info("[GUI] WorldManageGui: Player ${player.name} confirmed deletion of world ${world.name}")
                    debugLogger.debug("Delete button clicked (confirmation)", "player" to player.name, "worldName" to world.name, "worldId" to world.id)
                    player.closeInventory()
                    player.sendMessage(
                        Component.text("Deleting world ", NamedTextColor.YELLOW)
                            .append(Component.text(world.name, NamedTextColor.GOLD))
                            .append(Component.text("...", NamedTextColor.YELLOW))
                    )

                    debugLogger.debug("Initiating world deletion", "worldName" to world.name)
                    worldManager.deleteWorld(world).thenAccept { result ->
                        result.onSuccess {
                            plugin.logger.info("[GUI] WorldManageGui: Successfully deleted world ${world.name} for player ${player.name}")
                            debugLogger.debug("World deletion successful", "worldName" to world.name)
                            player.scheduler.run(plugin, { _ ->
                                player.sendMessage(
                                    Component.text("Successfully deleted world ", NamedTextColor.GREEN)
                                        .append(Component.text(world.name, NamedTextColor.GOLD))
                                )
                            }, null)
                        }.onFailure { error ->
                            plugin.logger.warning("[GUI] WorldManageGui: Failed to delete world ${world.name} for player ${player.name}: ${error.message}")
                            debugLogger.debug("World deletion failed", "worldName" to world.name, "error" to error.message)
                            player.scheduler.run(plugin, { _ ->
                                player.sendMessage(
                                    Component.text("Failed to delete world: ${error.message}", NamedTextColor.RED)
                                )
                            }, null)
                        }
                    }
                }
            }
        debugLogger.debugMethodExit("createDeleteItem")
        return item
    }
}
