package tech.bedson.playerworldmanager.gui

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.managers.DataManager
import tech.bedson.playerworldmanager.managers.InviteManager
import tech.bedson.playerworldmanager.managers.WorldManager

/**
 * Main admin menu GUI for world administrators.
 * Provides access to world browsing, statistics, and administrative actions.
 */
class AdminMenuGui(
    private val plugin: JavaPlugin,
    private val worldManager: WorldManager,
    private val inviteManager: InviteManager,
    private val dataManager: DataManager
) {

    fun open(player: Player) {
        plugin.logger.info("[GUI] AdminMenuGui: Opening for admin player ${player.name}")
        val gui = Gui.gui()
            .title(Component.text("World Admin Menu", NamedTextColor.DARK_PURPLE))
            .rows(4)
            .disableAllInteractions()
            .create()

        // Row 1 - Overview
        gui.setItem(0, createStatisticsItem(player))
        gui.setItem(1, createBrowseAllWorldsItem(player))
        gui.setItem(2, createBrowseByPlayerItem(player))

        // Row 2 - Quick Actions
        gui.setItem(9, createSearchWorldItem(player))
        gui.setItem(10, createDisabledWorldsItem(player))

        // Row 3 - Dangerous Actions
        gui.setItem(18, createPurgeInactiveItem(player))
        gui.setItem(19, createReloadPluginItem(player))

        // Row 4 - Navigation and filler
        gui.setItem(31, createCloseItem())

        // Fill empty slots with gray glass panes
        for (i in 0 until 36) {
            if (gui.getGuiItem(i) == null) {
                gui.setItem(i, ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                    .name(Component.empty())
                    .asGuiItem())
            }
        }

        gui.open(player)
    }

    private fun createStatisticsItem(player: Player): GuiItem {
        return ItemBuilder.from(Material.BOOK)
            .name(Component.text("Server Statistics", NamedTextColor.GOLD))
            .lore(
                listOf(
                    Component.text("Click to view world statistics", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Click to view", NamedTextColor.YELLOW)
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] AdminMenuGui: Admin ${player.name} clicked Server Statistics")
                player.closeInventory()

                player.scheduler.run(plugin, { _ ->
                    val allWorlds = dataManager.getAllWorlds()
                    val enabledCount = allWorlds.count { it.isEnabled }
                    val disabledCount = allWorlds.count { !it.isEnabled }

                    // Get unique owners
                    val ownerCounts = allWorlds.groupBy { it.ownerUuid }
                    val playerCount = ownerCounts.size

                    // Find most active player
                    val mostActive = ownerCounts.maxByOrNull { it.value.size }
                    val mostActiveName = mostActive?.let {
                        mostActive.value.firstOrNull()?.ownerName ?: "Unknown"
                    } ?: "None"
                    val mostActiveCount = mostActive?.value?.size ?: 0

                    plugin.logger.info("[GUI] AdminMenuGui: Statistics - Total: ${allWorlds.size}, Enabled: $enabledCount, Disabled: $disabledCount, Players: $playerCount")
                    player.sendMessage(Component.text("=== Server World Statistics ===", NamedTextColor.DARK_PURPLE))
                    player.sendMessage(Component.text("Total Worlds: ${allWorlds.size}", NamedTextColor.GRAY))
                    player.sendMessage(
                        Component.text("Enabled: ", NamedTextColor.GRAY)
                            .append(Component.text(enabledCount, NamedTextColor.GREEN))
                            .append(Component.text(" | ", NamedTextColor.GRAY))
                            .append(Component.text("Disabled: ", NamedTextColor.GRAY))
                            .append(Component.text(disabledCount, NamedTextColor.RED))
                    )
                    player.sendMessage(Component.text("Total Players with Worlds: $playerCount", NamedTextColor.GRAY))
                    player.sendMessage(
                        Component.text("Most Active: ", NamedTextColor.GRAY)
                            .append(Component.text(mostActiveName, NamedTextColor.GOLD))
                            .append(Component.text(" ($mostActiveCount worlds)", NamedTextColor.GRAY))
                    )
                }, null)
            }
    }

    private fun createBrowseAllWorldsItem(player: Player): GuiItem {
        return ItemBuilder.from(Material.COMPASS)
            .name(Component.text("Browse All Worlds", NamedTextColor.AQUA))
            .lore(
                listOf(
                    Component.text("View and manage all player worlds", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Click to browse", NamedTextColor.YELLOW)
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] AdminMenuGui: Admin ${player.name} clicked Browse All Worlds")
                player.closeInventory()
                player.scheduler.run(plugin, { _ ->
                    AdminWorldBrowserGui(plugin, worldManager, inviteManager, dataManager).open(player)
                }, null)
            }
    }

    private fun createBrowseByPlayerItem(player: Player): GuiItem {
        return ItemBuilder.from(Material.PLAYER_HEAD)
            .name(Component.text("Browse by Player", NamedTextColor.GREEN))
            .lore(
                listOf(
                    Component.text("View worlds owned by a specific player", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Use /worldadmin player <name>", NamedTextColor.YELLOW)
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true
                player.closeInventory()
                player.sendMessage(
                    Component.text("Use ", NamedTextColor.YELLOW)
                        .append(Component.text("/worldadmin player <name>", NamedTextColor.GOLD))
                        .append(Component.text(" to browse a player's worlds", NamedTextColor.YELLOW))
                )
            }
    }

    private fun createSearchWorldItem(player: Player): GuiItem {
        return ItemBuilder.from(Material.EMERALD)
            .name(Component.text("Search World", NamedTextColor.GREEN))
            .lore(
                listOf(
                    Component.text("Search for a world by name", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Use /worldadmin search <name>", NamedTextColor.YELLOW)
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true
                player.closeInventory()
                player.sendMessage(
                    Component.text("Use ", NamedTextColor.YELLOW)
                        .append(Component.text("/worldadmin search <name>", NamedTextColor.GOLD))
                        .append(Component.text(" to search for a world", NamedTextColor.YELLOW))
                )
            }
    }

    private fun createDisabledWorldsItem(player: Player): GuiItem {
        val disabledCount = dataManager.getAllWorlds().count { !it.isEnabled }

        return ItemBuilder.from(Material.REDSTONE)
            .name(Component.text("Disabled Worlds", NamedTextColor.RED))
            .lore(
                listOf(
                    Component.text("View all disabled worlds", NamedTextColor.GRAY),
                    Component.text("Total disabled: $disabledCount", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Click to view", NamedTextColor.YELLOW)
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] AdminMenuGui: Admin ${player.name} clicked Disabled Worlds (count: $disabledCount)")
                player.closeInventory()
                player.scheduler.run(plugin, { _ ->
                    AdminWorldBrowserGui(plugin, worldManager, inviteManager, dataManager)
                        .open(player, disabledOnly = true)
                }, null)
            }
    }

    private fun createPurgeInactiveItem(player: Player): GuiItem {
        return ItemBuilder.from(Material.TNT)
            .name(Component.text("Purge Inactive Worlds", NamedTextColor.DARK_RED))
            .lore(
                listOf(
                    Component.text("Delete worlds from inactive players", NamedTextColor.GRAY),
                    Component.text("This action cannot be undone!", NamedTextColor.RED),
                    Component.empty(),
                    Component.text("Use /worldadmin purge <days>", NamedTextColor.YELLOW)
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true
                player.closeInventory()
                player.sendMessage(
                    Component.text("Use ", NamedTextColor.YELLOW)
                        .append(Component.text("/worldadmin purge <days>", NamedTextColor.GOLD))
                        .append(Component.text(" to purge worlds from players inactive for X days", NamedTextColor.YELLOW))
                )
                player.sendMessage(
                    Component.text("WARNING: This action cannot be undone!", NamedTextColor.RED)
                )
            }
    }

    private fun createReloadPluginItem(player: Player): GuiItem {
        return ItemBuilder.from(Material.COMMAND_BLOCK)
            .name(Component.text("Reload Plugin", NamedTextColor.GOLD))
            .lore(
                listOf(
                    Component.text("Reload plugin configuration", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Use /worldadmin reload", NamedTextColor.YELLOW)
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true
                player.closeInventory()
                player.sendMessage(
                    Component.text("Use ", NamedTextColor.YELLOW)
                        .append(Component.text("/worldadmin reload", NamedTextColor.GOLD))
                        .append(Component.text(" to reload the plugin configuration", NamedTextColor.YELLOW))
                )
            }
    }

    private fun createCloseItem(): GuiItem {
        return ItemBuilder.from(Material.BARRIER)
            .name(Component.text("Close", NamedTextColor.RED))
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] AdminMenuGui: Admin ${(event.whoClicked as? Player)?.name} clicked Close")
                (event.whoClicked as? Player)?.closeInventory()
            }
    }
}
