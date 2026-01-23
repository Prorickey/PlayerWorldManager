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
import tech.bedson.playerworldmanager.utils.DebugLogger

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
    private val debugLogger = DebugLogger(plugin, "AdminMenuGui")

    fun open(player: Player) {
        debugLogger.debugMethodEntry("open", "player" to player.name, "playerUuid" to player.uniqueId)
        plugin.logger.info("[GUI] AdminMenuGui: Opening for admin player ${player.name}")

        val gui = Gui.gui()
            .title(Component.text("World Admin Menu", NamedTextColor.DARK_PURPLE))
            .rows(4)
            .disableAllInteractions()
            .create()

        // Row 1 - Overview
        debugLogger.debug("Setting up Row 1: Overview")
        gui.setItem(0, createStatisticsItem(player))
        gui.setItem(1, createBrowseAllWorldsItem(player))
        gui.setItem(2, createBrowseByPlayerItem(player))

        // Row 2 - Quick Actions
        debugLogger.debug("Setting up Row 2: Quick Actions")
        gui.setItem(9, createSearchWorldItem(player))
        gui.setItem(10, createDisabledWorldsItem(player))

        // Row 3 - Dangerous Actions
        debugLogger.debug("Setting up Row 3: Dangerous Actions")
        gui.setItem(18, createPurgeInactiveItem(player))
        gui.setItem(19, createReloadPluginItem(player))

        // Row 4 - Navigation and filler
        debugLogger.debug("Setting up Row 4: Navigation")
        gui.setItem(31, createCloseItem())

        // Fill empty slots with gray glass panes
        debugLogger.debug("Filling empty slots with glass panes")
        for (i in 0 until 36) {
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

    private fun createStatisticsItem(player: Player): GuiItem {
        debugLogger.debugMethodEntry("createStatisticsItem", "player" to player.name)
        val item = ItemBuilder.from(Material.BOOK)
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
                debugLogger.debug("Statistics button clicked", "player" to player.name)
                player.closeInventory()

                player.scheduler.run(plugin, { _ ->
                    debugLogger.debug("Loading statistics data")
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

                    debugLogger.debugState("ServerStatistics",
                        "totalWorlds" to allWorlds.size,
                        "enabledCount" to enabledCount,
                        "disabledCount" to disabledCount,
                        "playerCount" to playerCount,
                        "mostActiveName" to mostActiveName,
                        "mostActiveCount" to mostActiveCount
                    )
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
        debugLogger.debugMethodExit("createStatisticsItem")
        return item
    }

    private fun createBrowseAllWorldsItem(player: Player): GuiItem {
        debugLogger.debugMethodEntry("createBrowseAllWorldsItem", "player" to player.name)
        val item = ItemBuilder.from(Material.COMPASS)
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
                debugLogger.debug("Browse All Worlds button clicked", "player" to player.name)
                player.closeInventory()
                player.scheduler.run(plugin, { _ ->
                    debugLogger.debug("Opening AdminWorldBrowserGui", "player" to player.name)
                    AdminWorldBrowserGui(plugin, worldManager, inviteManager, dataManager).open(player)
                }, null)
            }
        debugLogger.debugMethodExit("createBrowseAllWorldsItem")
        return item
    }

    private fun createBrowseByPlayerItem(player: Player): GuiItem {
        debugLogger.debugMethodEntry("createBrowseByPlayerItem", "player" to player.name)
        val item = ItemBuilder.from(Material.PLAYER_HEAD)
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
                debugLogger.debug("Browse by Player button clicked", "player" to player.name)
                player.closeInventory()
                player.sendMessage(
                    Component.text("Use ", NamedTextColor.YELLOW)
                        .append(Component.text("/worldadmin player <name>", NamedTextColor.GOLD))
                        .append(Component.text(" to browse a player's worlds", NamedTextColor.YELLOW))
                )
            }
        debugLogger.debugMethodExit("createBrowseByPlayerItem")
        return item
    }

    private fun createSearchWorldItem(player: Player): GuiItem {
        debugLogger.debugMethodEntry("createSearchWorldItem", "player" to player.name)
        val item = ItemBuilder.from(Material.EMERALD)
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
                debugLogger.debug("Search World button clicked", "player" to player.name)
                player.closeInventory()
                player.sendMessage(
                    Component.text("Use ", NamedTextColor.YELLOW)
                        .append(Component.text("/worldadmin search <name>", NamedTextColor.GOLD))
                        .append(Component.text(" to search for a world", NamedTextColor.YELLOW))
                )
            }
        debugLogger.debugMethodExit("createSearchWorldItem")
        return item
    }

    private fun createDisabledWorldsItem(player: Player): GuiItem {
        debugLogger.debugMethodEntry("createDisabledWorldsItem", "player" to player.name)
        val disabledCount = dataManager.getAllWorlds().count { !it.isEnabled }
        debugLogger.debugState("DisabledWorldsItem", "disabledCount" to disabledCount)

        val item = ItemBuilder.from(Material.REDSTONE)
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
                debugLogger.debug("Disabled Worlds button clicked", "player" to player.name, "disabledCount" to disabledCount)
                player.closeInventory()
                player.scheduler.run(plugin, { _ ->
                    debugLogger.debug("Opening AdminWorldBrowserGui with disabled filter", "player" to player.name)
                    AdminWorldBrowserGui(plugin, worldManager, inviteManager, dataManager)
                        .open(player, disabledOnly = true)
                }, null)
            }
        debugLogger.debugMethodExit("createDisabledWorldsItem")
        return item
    }

    private fun createPurgeInactiveItem(player: Player): GuiItem {
        debugLogger.debugMethodEntry("createPurgeInactiveItem", "player" to player.name)
        val item = ItemBuilder.from(Material.TNT)
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
                debugLogger.debug("Purge Inactive Worlds button clicked", "player" to player.name)
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
        debugLogger.debugMethodExit("createPurgeInactiveItem")
        return item
    }

    private fun createReloadPluginItem(player: Player): GuiItem {
        debugLogger.debugMethodEntry("createReloadPluginItem", "player" to player.name)
        val item = ItemBuilder.from(Material.COMMAND_BLOCK)
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
                debugLogger.debug("Reload Plugin button clicked", "player" to player.name)
                player.closeInventory()
                player.sendMessage(
                    Component.text("Use ", NamedTextColor.YELLOW)
                        .append(Component.text("/worldadmin reload", NamedTextColor.GOLD))
                        .append(Component.text(" to reload the plugin configuration", NamedTextColor.YELLOW))
                )
            }
        debugLogger.debugMethodExit("createReloadPluginItem")
        return item
    }

    private fun createCloseItem(): GuiItem {
        debugLogger.debugMethodEntry("createCloseItem")
        val item = ItemBuilder.from(Material.BARRIER)
            .name(Component.text("Close", NamedTextColor.RED))
            .asGuiItem { event ->
                event.isCancelled = true
                val clickedPlayer = event.whoClicked as? Player
                plugin.logger.info("[GUI] AdminMenuGui: Admin ${clickedPlayer?.name} clicked Close")
                debugLogger.debug("Close button clicked", "player" to (clickedPlayer?.name ?: "unknown"))
                clickedPlayer?.closeInventory()
            }
        debugLogger.debugMethodExit("createCloseItem")
        return item
    }
}
