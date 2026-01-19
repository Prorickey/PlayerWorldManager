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
import tech.bedson.playerworldmanager.managers.WorldManager
import tech.bedson.playerworldmanager.models.PlayerWorld
import tech.bedson.playerworldmanager.models.TimeLock
import tech.bedson.playerworldmanager.models.WeatherLock
import tech.bedson.playerworldmanager.models.toSimpleLocation
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
    private val dataManager: DataManager
) {

    companion object {
        private val pendingDeletes = ConcurrentHashMap<UUID, PendingDelete>()

        data class PendingDelete(
            val worldId: UUID,
            val timestamp: Long
        )
    }

    fun open(player: Player, world: PlayerWorld) {
        plugin.logger.info("[GUI] WorldManageGui: Opening for player ${player.name}, world ${world.name}")

        val gui = Gui.gui()
            .title(Component.text("Manage: ${world.name}", NamedTextColor.GOLD))
            .rows(5)
            .disableAllInteractions()
            .create()

        // Row 1: Teleportation
        gui.setItem(0, createTeleportItem(player, world, World.Environment.NORMAL))
        gui.setItem(1, createTeleportItem(player, world, World.Environment.NETHER))
        gui.setItem(2, createTeleportItem(player, world, World.Environment.THE_END))
        gui.setItem(4, createInvitedPlayersItem(world))

        // Row 2: Settings
        gui.setItem(9, createTimeLockItem(player, world))
        gui.setItem(10, createWeatherLockItem(player, world))
        gui.setItem(11, createGameModeItem(player, world))
        gui.setItem(12, createSetSpawnItem(player, world))

        // Row 3: Invite/Kick
        gui.setItem(18, createInvitePlayerItem(player, world))
        gui.setItem(19, createKickPlayerItem(player, world))

        // Row 4: Ownership
        gui.setItem(27, createTransferOwnershipItem(player, world))

        // Row 5: Navigation and deletion
        gui.setItem(36, createBackItem(player))
        gui.setItem(44, createDeleteItem(player, world))

        // Fill empty slots with gray glass panes
        for (i in 0 until 45) {
            if (gui.getGuiItem(i) == null) {
                gui.setItem(i, ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                    .name(Component.empty())
                    .asGuiItem())
            }
        }

        gui.open(player)
    }

    private fun createTeleportItem(player: Player, world: PlayerWorld, environment: World.Environment): GuiItem {
        val (material, name) = when (environment) {
            World.Environment.NORMAL -> Material.COMPASS to "Teleport to Overworld"
            World.Environment.NETHER -> Material.NETHERRACK to "Teleport to Nether"
            World.Environment.THE_END -> Material.END_STONE to "Teleport to End"
            else -> Material.COMPASS to "Teleport"
        }

        return ItemBuilder.from(material)
            .name(Component.text(name, NamedTextColor.GREEN))
            .lore(listOf(Component.text("Click to teleport", NamedTextColor.YELLOW)))
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] WorldManageGui: Player ${player.name} clicked teleport to ${environment.name} in world ${world.name}")
                player.closeInventory()
                player.scheduler.run(plugin, { _ ->
                    worldManager.teleportToDimension(player, world, environment).thenAccept { success ->
                        if (success) {
                            plugin.logger.info("[GUI] WorldManageGui: Player ${player.name} successfully teleported to ${environment.name} in world ${world.name}")
                            player.sendMessage(
                                Component.text("Teleported to ", NamedTextColor.GREEN)
                                    .append(Component.text(world.name, NamedTextColor.GOLD))
                            )
                        } else {
                            plugin.logger.warning("[GUI] WorldManageGui: Player ${player.name} failed to teleport to ${environment.name} in world ${world.name}")
                            player.sendMessage(
                                Component.text("Failed to teleport to world", NamedTextColor.RED)
                            )
                        }
                    }
                }, null)
            }
    }

    private fun createInvitedPlayersItem(world: PlayerWorld): GuiItem {
        val invitedCount = world.invitedPlayers.size
        val invitedNames = world.invitedPlayers.take(5).map { uuid ->
            Bukkit.getOfflinePlayer(uuid).name ?: uuid.toString()
        }

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

        return ItemBuilder.from(Material.PLAYER_HEAD)
            .name(Component.text("Invited Players", NamedTextColor.AQUA))
            .lore(lore)
            .asGuiItem { event ->
                event.isCancelled = true
            }
    }

    private fun createTimeLockItem(player: Player, world: PlayerWorld): GuiItem {
        val currentLock = world.timeLock
        val nextLock = when (currentLock) {
            TimeLock.CYCLE -> TimeLock.DAY
            TimeLock.DAY -> TimeLock.NIGHT
            TimeLock.NIGHT -> TimeLock.CYCLE
        }

        return ItemBuilder.from(Material.CLOCK)
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
                world.timeLock = nextLock
                dataManager.saveWorld(world)
                Bukkit.getGlobalRegionScheduler().run(plugin) { _ ->
                    worldManager.applyWorldSettings(world)
                }
                player.sendMessage(
                    Component.text("Time setting changed to ", NamedTextColor.GREEN)
                        .append(Component.text(nextLock.toString(), NamedTextColor.GOLD))
                )
                open(player, world) // Refresh GUI
            }
    }

    private fun createWeatherLockItem(player: Player, world: PlayerWorld): GuiItem {
        val currentLock = world.weatherLock
        val nextLock = when (currentLock) {
            WeatherLock.CYCLE -> WeatherLock.CLEAR
            WeatherLock.CLEAR -> WeatherLock.RAIN
            WeatherLock.RAIN -> WeatherLock.CYCLE
        }

        return ItemBuilder.from(Material.WATER_BUCKET)
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
                world.weatherLock = nextLock
                dataManager.saveWorld(world)
                Bukkit.getGlobalRegionScheduler().run(plugin) { _ ->
                    worldManager.applyWorldSettings(world)
                }
                player.sendMessage(
                    Component.text("Weather setting changed to ", NamedTextColor.GREEN)
                        .append(Component.text(nextLock.toString(), NamedTextColor.GOLD))
                )
                open(player, world) // Refresh GUI
            }
    }

    private fun createGameModeItem(player: Player, world: PlayerWorld): GuiItem {
        val currentMode = world.defaultGameMode
        val nextMode = when (currentMode) {
            GameMode.SURVIVAL -> GameMode.CREATIVE
            GameMode.CREATIVE -> GameMode.ADVENTURE
            GameMode.ADVENTURE -> GameMode.SPECTATOR
            GameMode.SPECTATOR -> GameMode.SURVIVAL
        }

        return ItemBuilder.from(Material.DIAMOND_SWORD)
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
                world.defaultGameMode = nextMode
                dataManager.saveWorld(world)
                player.sendMessage(
                    Component.text("Default gamemode changed to ", NamedTextColor.GREEN)
                        .append(Component.text(nextMode.toString(), NamedTextColor.GOLD))
                )
                open(player, world) // Refresh GUI
            }
    }

    private fun createSetSpawnItem(player: Player, world: PlayerWorld): GuiItem {
        return ItemBuilder.from(Material.RED_BED)
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
                player.closeInventory()
                player.scheduler.run(plugin, { _ ->
                    val location = player.location
                    worldManager.setSpawnLocation(world, location)
                    plugin.logger.info("[GUI] WorldManageGui: Spawn point set for world ${world.name} at ${location.blockX}, ${location.blockY}, ${location.blockZ}")
                    player.sendMessage(
                        Component.text("Spawn point set to your current location", NamedTextColor.GREEN)
                    )
                }, null)
            }
    }

    private fun createInvitePlayerItem(player: Player, world: PlayerWorld): GuiItem {
        return ItemBuilder.from(Material.NAME_TAG)
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
                player.closeInventory()
                player.sendMessage(
                    Component.text("Use ", NamedTextColor.YELLOW)
                        .append(Component.text("/world invite <player>", NamedTextColor.GOLD))
                        .append(Component.text(" to invite a player to ", NamedTextColor.YELLOW))
                        .append(Component.text(world.name, NamedTextColor.GOLD))
                )
            }
    }

    private fun createKickPlayerItem(player: Player, world: PlayerWorld): GuiItem {
        val invitedCount = world.invitedPlayers.size

        return ItemBuilder.from(Material.BARRIER)
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
                player.closeInventory()
                player.sendMessage(
                    Component.text("Use ", NamedTextColor.YELLOW)
                        .append(Component.text("/world kick <player>", NamedTextColor.GOLD))
                        .append(Component.text(" to kick a player from ", NamedTextColor.YELLOW))
                        .append(Component.text(world.name, NamedTextColor.GOLD))
                )
            }
    }

    private fun createTransferOwnershipItem(player: Player, world: PlayerWorld): GuiItem {
        return ItemBuilder.from(Material.GOLD_INGOT)
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
                player.closeInventory()
                player.sendMessage(
                    Component.text("Use ", NamedTextColor.YELLOW)
                        .append(Component.text("/world transfer <player>", NamedTextColor.GOLD))
                        .append(Component.text(" to transfer ownership of ", NamedTextColor.YELLOW))
                        .append(Component.text(world.name, NamedTextColor.GOLD))
                )
            }
    }

    private fun createBackItem(player: Player): GuiItem {
        return ItemBuilder.from(Material.ARROW)
            .name(Component.text("Back to Main Menu", NamedTextColor.YELLOW))
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] WorldManageGui: Player ${player.name} clicked Back to Main Menu")
                // Clear pending delete confirmation when navigating away
                pendingDeletes.remove(player.uniqueId)
                player.closeInventory()
                player.scheduler.run(plugin, { _ ->
                    MainMenuGui(plugin, worldManager, inviteManager, dataManager).open(player)
                }, null)
            }
    }

    private fun createDeleteItem(player: Player, world: PlayerWorld): GuiItem {
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

        return ItemBuilder.from(material)
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
                    player.sendMessage(
                        Component.text("Click delete again to confirm deletion of ", NamedTextColor.YELLOW)
                            .append(Component.text(world.name, NamedTextColor.GOLD))
                    )
                    open(player, world) // Refresh GUI to show confirmation state
                } else {
                    // Second click: execute delete and remove from pending
                    pendingDeletes.remove(player.uniqueId)
                    plugin.logger.info("[GUI] WorldManageGui: Player ${player.name} confirmed deletion of world ${world.name}")
                    player.closeInventory()
                    player.sendMessage(
                        Component.text("Deleting world ", NamedTextColor.YELLOW)
                            .append(Component.text(world.name, NamedTextColor.GOLD))
                            .append(Component.text("...", NamedTextColor.YELLOW))
                    )

                    worldManager.deleteWorld(world).thenAccept { result ->
                        result.onSuccess {
                            plugin.logger.info("[GUI] WorldManageGui: Successfully deleted world ${world.name} for player ${player.name}")
                            player.scheduler.run(plugin, { _ ->
                                player.sendMessage(
                                    Component.text("Successfully deleted world ", NamedTextColor.GREEN)
                                        .append(Component.text(world.name, NamedTextColor.GOLD))
                                )
                            }, null)
                        }.onFailure { error ->
                            plugin.logger.warning("[GUI] WorldManageGui: Failed to delete world ${world.name} for player ${player.name}: ${error.message}")
                            player.scheduler.run(plugin, { _ ->
                                player.sendMessage(
                                    Component.text("Failed to delete world: ${error.message}", NamedTextColor.RED)
                                )
                            }, null)
                        }
                    }
                }
            }
    }
}
