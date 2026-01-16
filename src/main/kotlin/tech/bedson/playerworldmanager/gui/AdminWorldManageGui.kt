package tech.bedson.playerworldmanager.gui

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.managers.DataManager
import tech.bedson.playerworldmanager.managers.InviteManager
import tech.bedson.playerworldmanager.managers.WorldManager
import tech.bedson.playerworldmanager.models.PlayerWorld
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Admin management GUI for any player world.
 * Provides full administrative control over world settings, players, and status.
 */
class AdminWorldManageGui(
    private val plugin: JavaPlugin,
    private val worldManager: WorldManager,
    private val inviteManager: InviteManager,
    private val dataManager: DataManager
) {

    private var deleteClickCount = 0
    private var lastDeleteClick = 0L

    fun open(player: Player, world: PlayerWorld) {
        plugin.logger.info("[GUI] AdminWorldManageGui: Opening for admin ${player.name}, world ${world.name} (owner: ${world.ownerName})")
        // Reset delete confirmation
        deleteClickCount = 0

        val gui = Gui.gui()
            .title(Component.text("Admin: ${world.name}", NamedTextColor.DARK_PURPLE))
            .rows(5)
            .disableAllInteractions()
            .create()

        // Row 1 - Info (display only)
        gui.setItem(0, createWorldInfoItem(world))
        gui.setItem(1, createInvitedPlayersInfoItem(world))

        // Row 2 - Teleport
        gui.setItem(9, createTeleportItem(player, world, World.Environment.NORMAL))
        gui.setItem(10, createTeleportItem(player, world, World.Environment.NETHER))
        gui.setItem(11, createTeleportItem(player, world, World.Environment.THE_END))

        // Row 3 - Management
        gui.setItem(18, createForceInviteItem(player, world))
        gui.setItem(19, createForceKickItem(player, world))
        gui.setItem(20, createForceTransferItem(player, world))

        // Row 4 - Status
        gui.setItem(27, createToggleEnableItem(player, world))
        gui.setItem(28, createSetLimitItem(player, world))

        // Row 5 - Navigation and deletion
        gui.setItem(36, createBackItem(player))
        gui.setItem(40, createForceDeleteItem(player, world))

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

    private fun createWorldInfoItem(world: PlayerWorld): GuiItem {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm")
        val createdDate = dateFormat.format(Date(world.createdAt))

        return ItemBuilder.from(Material.PAPER)
            .name(Component.text("World Information", NamedTextColor.GOLD))
            .lore(
                listOf(
                    Component.text("Name: ${world.name}", NamedTextColor.GRAY),
                    Component.text("Owner: ${world.ownerName}", NamedTextColor.GRAY),
                    Component.text("Type: ${world.worldType}", NamedTextColor.GRAY),
                    Component.text("Created: $createdDate", NamedTextColor.GRAY),
                    Component.text("Status: ${if (world.isEnabled) "Enabled" else "Disabled"}",
                        if (world.isEnabled) NamedTextColor.GREEN else NamedTextColor.RED)
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true
            }
    }

    private fun createInvitedPlayersInfoItem(world: PlayerWorld): GuiItem {
        val invitedCount = world.invitedPlayers.size
        val invitedNames = world.invitedPlayers.take(10).map { uuid ->
            Bukkit.getOfflinePlayer(uuid).name ?: uuid.toString()
        }

        val lore = buildList<Component> {
            add(Component.text("Invited Players: $invitedCount", NamedTextColor.GRAY))
            if (invitedNames.isNotEmpty()) {
                add(Component.empty())
                invitedNames.forEach { name ->
                    add(Component.text("  - $name", NamedTextColor.GRAY))
                }
                if (invitedCount > 10) {
                    add(Component.text("  ...and ${invitedCount - 10} more", NamedTextColor.GRAY))
                }
            }
        }

        return ItemBuilder.from(Material.BOOK)
            .name(Component.text("Invited Players", NamedTextColor.AQUA))
            .lore(lore)
            .asGuiItem { event ->
                event.isCancelled = true
            }
    }

    private fun createTeleportItem(player: Player, world: PlayerWorld, environment: World.Environment): GuiItem {
        val (material, name) = when (environment) {
            World.Environment.NORMAL -> Material.COMPASS to "TP to Overworld"
            World.Environment.NETHER -> Material.NETHERRACK to "TP to Nether"
            World.Environment.THE_END -> Material.END_STONE to "TP to End"
            else -> Material.COMPASS to "Teleport"
        }

        return ItemBuilder.from(material)
            .name(Component.text(name, NamedTextColor.GREEN))
            .lore(listOf(Component.text("Click to teleport", NamedTextColor.YELLOW)))
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] AdminWorldManageGui: Admin ${player.name} teleporting to ${environment.name} in world ${world.name}")
                player.closeInventory()
                player.scheduler.run(plugin, { _ ->
                    worldManager.teleportToDimension(player, world, environment).thenAccept { success ->
                        if (success) {
                            plugin.logger.info("[GUI] AdminWorldManageGui: Admin ${player.name} successfully teleported to ${environment.name} in world ${world.name}")
                            player.sendMessage(
                                Component.text("Teleported to ", NamedTextColor.GREEN)
                                    .append(Component.text(world.name, NamedTextColor.GOLD))
                            )
                        } else {
                            plugin.logger.warning("[GUI] AdminWorldManageGui: Admin ${player.name} failed to teleport to ${environment.name} in world ${world.name}")
                            player.sendMessage(
                                Component.text("Failed to teleport to world", NamedTextColor.RED)
                            )
                        }
                    }
                }, null)
            }
    }

    private fun createForceInviteItem(player: Player, world: PlayerWorld): GuiItem {
        return ItemBuilder.from(Material.EMERALD)
            .name(Component.text("Force Invite Player", NamedTextColor.GREEN))
            .lore(
                listOf(
                    Component.text("Invite a player to this world", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Use /worldadmin invite <world> <player>", NamedTextColor.YELLOW)
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true
                player.closeInventory()
                player.sendMessage(
                    Component.text("Use ", NamedTextColor.YELLOW)
                        .append(Component.text("/worldadmin invite ${world.name} <player>", NamedTextColor.GOLD))
                        .append(Component.text(" to force invite a player", NamedTextColor.YELLOW))
                )
            }
    }

    private fun createForceKickItem(player: Player, world: PlayerWorld): GuiItem {
        val invitedCount = world.invitedPlayers.size

        return ItemBuilder.from(Material.BARRIER)
            .name(Component.text("Force Kick Player", NamedTextColor.RED))
            .lore(
                listOf(
                    Component.text("Kick a player from this world", NamedTextColor.GRAY),
                    Component.text("Invited players: $invitedCount", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Use /worldadmin kick <world> <player>", NamedTextColor.YELLOW)
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true
                player.closeInventory()
                player.sendMessage(
                    Component.text("Use ", NamedTextColor.YELLOW)
                        .append(Component.text("/worldadmin kick ${world.name} <player>", NamedTextColor.GOLD))
                        .append(Component.text(" to force kick a player", NamedTextColor.YELLOW))
                )
            }
    }

    private fun createForceTransferItem(player: Player, world: PlayerWorld): GuiItem {
        return ItemBuilder.from(Material.GOLD_INGOT)
            .name(Component.text("Force Transfer Ownership", NamedTextColor.GOLD))
            .lore(
                listOf(
                    Component.text("Transfer this world to another player", NamedTextColor.GRAY),
                    Component.text("Current owner: ${world.ownerName}", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Use /worldadmin transfer <world> <player>", NamedTextColor.YELLOW)
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true
                player.closeInventory()
                player.sendMessage(
                    Component.text("Use ", NamedTextColor.YELLOW)
                        .append(Component.text("/worldadmin transfer ${world.name} <player>", NamedTextColor.GOLD))
                        .append(Component.text(" to force transfer ownership", NamedTextColor.YELLOW))
                )
            }
    }

    private fun createToggleEnableItem(player: Player, world: PlayerWorld): GuiItem {
        val isEnabled = world.isEnabled
        val material = if (isEnabled) Material.LIME_DYE else Material.GRAY_DYE
        val name = if (isEnabled) "Disable World" else "Enable World"
        val color = if (isEnabled) NamedTextColor.RED else NamedTextColor.GREEN
        val action = if (isEnabled) "disable" else "enable"

        return ItemBuilder.from(material)
            .name(Component.text(name, color))
            .lore(
                listOf(
                    Component.text("Current status: ${if (isEnabled) "Enabled" else "Disabled"}", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Click to $action", NamedTextColor.YELLOW)
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true
                val newStatus = !world.isEnabled
                plugin.logger.info("[GUI] AdminWorldManageGui: Admin ${player.name} toggling world ${world.name} from ${world.isEnabled} to $newStatus")
                world.isEnabled = newStatus
                dataManager.saveWorld(world)

                player.sendMessage(
                    Component.text("World ", NamedTextColor.YELLOW)
                        .append(Component.text(world.name, NamedTextColor.GOLD))
                        .append(Component.text(" is now ${if (world.isEnabled) "enabled" else "disabled"}", NamedTextColor.YELLOW))
                )

                // Refresh GUI
                open(player, world)
            }
    }

    private fun createSetLimitItem(player: Player, world: PlayerWorld): GuiItem {
        val playerData = dataManager.loadPlayerData(world.ownerUuid)
        val currentLimit = playerData?.worldLimit ?: 3

        return ItemBuilder.from(Material.PLAYER_HEAD)
            .name(Component.text("Set Player World Limit", NamedTextColor.AQUA))
            .lore(
                listOf(
                    Component.text("Owner: ${world.ownerName}", NamedTextColor.GRAY),
                    Component.text("Current limit: $currentLimit", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Use /worldadmin setlimit <player> <limit>", NamedTextColor.YELLOW)
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true
                player.closeInventory()
                player.sendMessage(
                    Component.text("Use ", NamedTextColor.YELLOW)
                        .append(Component.text("/worldadmin setlimit ${world.ownerName} <limit>", NamedTextColor.GOLD))
                        .append(Component.text(" to set the player's world limit", NamedTextColor.YELLOW))
                )
            }
    }

    private fun createBackItem(player: Player): GuiItem {
        return ItemBuilder.from(Material.ARROW)
            .name(Component.text("Back to Browser", NamedTextColor.YELLOW))
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] AdminWorldManageGui: Admin ${player.name} clicked Back to Browser")
                player.closeInventory()
                player.scheduler.run(plugin, { _ ->
                    AdminWorldBrowserGui(plugin, worldManager, inviteManager, dataManager).open(player)
                }, null)
            }
    }

    private fun createForceDeleteItem(player: Player, world: PlayerWorld): GuiItem {
        val currentTime = System.currentTimeMillis()

        // Reset counter if too much time has passed (5 seconds)
        if (currentTime - lastDeleteClick > 5000) {
            deleteClickCount = 0
        }

        val needsConfirmation = deleteClickCount == 0
        val material = if (needsConfirmation) Material.TNT else Material.BARRIER
        val name = if (needsConfirmation) "Force Delete World" else "Click again to confirm deletion"
        val color = if (needsConfirmation) NamedTextColor.RED else NamedTextColor.DARK_RED

        return ItemBuilder.from(material)
            .name(Component.text(name, color))
            .lore(
                listOf(
                    Component.text("This cannot be undone!", NamedTextColor.RED),
                    Component.text("Owner will lose the world!", NamedTextColor.RED),
                    Component.empty(),
                    if (needsConfirmation) {
                        Component.text("Click twice to delete", NamedTextColor.YELLOW)
                    } else {
                        Component.text("Click to DELETE PERMANENTLY", NamedTextColor.DARK_RED)
                    }
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true

                if (deleteClickCount == 0) {
                    deleteClickCount = 1
                    lastDeleteClick = currentTime
                    plugin.logger.info("[GUI] AdminWorldManageGui: Admin ${player.name} clicked force delete for world ${world.name} (first click)")
                    player.sendMessage(
                        Component.text("Click delete again to confirm deletion of ", NamedTextColor.YELLOW)
                            .append(Component.text(world.name, NamedTextColor.GOLD))
                    )
                    open(player, world) // Refresh GUI to show confirmation state
                } else {
                    // Delete the world
                    plugin.logger.info("[GUI] AdminWorldManageGui: Admin ${player.name} confirmed force deletion of world ${world.name} (owner: ${world.ownerName})")
                    player.closeInventory()
                    player.sendMessage(
                        Component.text("Force deleting world ", NamedTextColor.YELLOW)
                            .append(Component.text(world.name, NamedTextColor.GOLD))
                            .append(Component.text("...", NamedTextColor.YELLOW))
                    )

                    worldManager.deleteWorld(world).thenAccept { result ->
                        result.onSuccess {
                            plugin.logger.info("[GUI] AdminWorldManageGui: Successfully force deleted world ${world.name} for admin ${player.name}")
                            player.scheduler.run(plugin, { _ ->
                                player.sendMessage(
                                    Component.text("Successfully deleted world ", NamedTextColor.GREEN)
                                        .append(Component.text(world.name, NamedTextColor.GOLD))
                                )
                            }, null)
                        }.onFailure { error ->
                            plugin.logger.warning("[GUI] AdminWorldManageGui: Failed to force delete world ${world.name} for admin ${player.name}: ${error.message}")
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
