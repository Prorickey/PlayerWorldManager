package tech.bedson.playerworldmanager.gui

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.GuiItem
import dev.triumphteam.gui.guis.PaginatedGui
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.managers.DataManager
import tech.bedson.playerworldmanager.managers.InviteManager
import tech.bedson.playerworldmanager.managers.WorldManager
import tech.bedson.playerworldmanager.models.PlayerWorld
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID

/**
 * Paginated browser for all player worlds (admin view).
 * Allows admins to view, teleport to, and manage any world.
 */
class AdminWorldBrowserGui(
    private val plugin: JavaPlugin,
    private val worldManager: WorldManager,
    private val inviteManager: InviteManager,
    private val dataManager: DataManager
) {

    private enum class FilterMode {
        ALL, ENABLED_ONLY, DISABLED_ONLY
    }

    private var currentFilter = FilterMode.ALL

    /**
     * Open browsing all worlds with optional filter.
     */
    fun open(player: Player, disabledOnly: Boolean = false) {
        plugin.logger.info("[GUI] AdminWorldBrowserGui: Opening for admin ${player.name}, disabledOnly=$disabledOnly")
        if (disabledOnly) {
            currentFilter = FilterMode.DISABLED_ONLY
        }
        openWithFilter(player, null, null)
    }

    /**
     * Open browsing specific player's worlds.
     */
    fun openForPlayer(player: Player, targetUuid: UUID, targetName: String) {
        plugin.logger.info("[GUI] AdminWorldBrowserGui: Opening for admin ${player.name}, browsing worlds of player $targetName")
        openWithFilter(player, targetUuid, targetName)
    }

    private fun openWithFilter(player: Player, targetUuid: UUID?, targetName: String?) {
        val gui = dev.triumphteam.gui.guis.Gui.paginated()
            .title(
                Component.text(
                    if (targetName != null) "$targetName's Worlds" else "Browse Worlds",
                    NamedTextColor.DARK_PURPLE
                )
            )
            .rows(6)
            .disableAllInteractions()
            .create()

        // Get worlds based on filter
        val allWorlds = if (targetUuid != null) {
            dataManager.getWorldsByOwner(targetUuid)
        } else {
            dataManager.getAllWorlds()
        }

        val filteredWorlds = when (currentFilter) {
            FilterMode.ALL -> allWorlds
            FilterMode.ENABLED_ONLY -> allWorlds.filter { it.isEnabled }
            FilterMode.DISABLED_ONLY -> allWorlds.filter { !it.isEnabled }
        }

        // Sort by creation date (newest first)
        val sortedWorlds = filteredWorlds.sortedByDescending { it.createdAt }

        plugin.logger.info("[GUI] AdminWorldBrowserGui: Displaying ${sortedWorlds.size} worlds to admin ${player.name} (filter: $currentFilter)")

        // Add world items
        sortedWorlds.forEach { world ->
            gui.addItem(createWorldItem(player, world))
        }

        // Row 6 - Navigation
        gui.setItem(6, 1, createBackItem(player))
        gui.setItem(6, 4, createPreviousPageItem(gui))
        gui.setItem(6, 5, createPageIndicatorItem(gui))
        gui.setItem(6, 6, createNextPageItem(gui))
        gui.setItem(6, 9, createFilterItem(player))

        // Fill remaining slots with glass panes
        for (i in listOf(0, 2, 3, 7, 8)) {
            gui.setItem(6, i + 1, ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .asGuiItem())
        }

        gui.open(player)
    }

    private fun createWorldItem(player: Player, world: PlayerWorld): GuiItem {
        val material = if (world.isEnabled) Material.GRASS_BLOCK else Material.GRAY_STAINED_GLASS
        val nameColor = if (world.isEnabled) NamedTextColor.GREEN else NamedTextColor.GRAY
        val statusText = if (world.isEnabled) "Enabled" else "Disabled"
        val statusColor = if (world.isEnabled) NamedTextColor.GREEN else NamedTextColor.RED

        val bukkitWorld = worldManager.getBukkitWorld(world)
        val playersOnline = bukkitWorld?.players?.size ?: 0
        val invitedCount = world.invitedPlayers.size

        val dateFormat = SimpleDateFormat("MMM dd, yyyy")
        val createdDate = dateFormat.format(Date(world.createdAt))

        return ItemBuilder.from(material)
            .name(Component.text("${world.ownerName}'s ${world.name}", nameColor))
            .lore(
                listOf(
                    Component.text("Type: ${world.worldType}", NamedTextColor.GRAY),
                    Component.text("Status: ", NamedTextColor.GRAY)
                        .append(Component.text(statusText, statusColor)),
                    Component.text("Players Online: $playersOnline", NamedTextColor.GRAY),
                    Component.text("Invited: $invitedCount players", NamedTextColor.GRAY),
                    Component.text("Created: $createdDate", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Left-click: Teleport", NamedTextColor.YELLOW),
                    Component.text("Right-click: Manage", NamedTextColor.YELLOW),
                    Component.text("Shift-click: Toggle Enable/Disable", NamedTextColor.GOLD)
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true

                when (event.click) {
                    ClickType.LEFT -> {
                        // Teleport to world
                        plugin.logger.info("[GUI] AdminWorldBrowserGui: Admin ${player.name} teleporting to world ${world.name}")
                        player.closeInventory()
                        player.scheduler.run(plugin, { _ ->
                            worldManager.teleportToWorld(player, world).thenAccept { success ->
                                if (success) {
                                    plugin.logger.info("[GUI] AdminWorldBrowserGui: Admin ${player.name} successfully teleported to world ${world.name}")
                                    player.sendMessage(
                                        Component.text("Teleported to ", NamedTextColor.GREEN)
                                            .append(Component.text(world.name, NamedTextColor.GOLD))
                                    )
                                } else {
                                    plugin.logger.warning("[GUI] AdminWorldBrowserGui: Admin ${player.name} failed to teleport to world ${world.name}")
                                    player.sendMessage(
                                        Component.text("Failed to teleport to world", NamedTextColor.RED)
                                    )
                                }
                            }
                        }, null)
                    }
                    ClickType.RIGHT -> {
                        // Open manage GUI
                        plugin.logger.info("[GUI] AdminWorldBrowserGui: Admin ${player.name} opening manage GUI for world ${world.name}")
                        player.closeInventory()
                        player.scheduler.run(plugin, { _ ->
                            AdminWorldManageGui(plugin, worldManager, inviteManager, dataManager)
                                .open(player, world)
                        }, null)
                    }
                    ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT -> {
                        // Toggle enable/disable
                        val newStatus = !world.isEnabled
                        plugin.logger.info("[GUI] AdminWorldBrowserGui: Admin ${player.name} toggling world ${world.name} from ${world.isEnabled} to $newStatus")
                        world.isEnabled = newStatus
                        dataManager.saveWorld(world)

                        val status = if (world.isEnabled) "enabled" else "disabled"
                        player.sendMessage(
                            Component.text("World ", NamedTextColor.YELLOW)
                                .append(Component.text(world.name, NamedTextColor.GOLD))
                                .append(Component.text(" is now $status", NamedTextColor.YELLOW))
                        )

                        // Refresh GUI
                        player.closeInventory()
                        player.scheduler.run(plugin, { _ ->
                            openWithFilter(player, null, null)
                        }, null)
                    }
                    else -> {}
                }
            }
    }

    private fun createBackItem(player: Player): GuiItem {
        return ItemBuilder.from(Material.ARROW)
            .name(Component.text("Back to Admin Menu", NamedTextColor.YELLOW))
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] AdminWorldBrowserGui: Admin ${player.name} clicked Back to Admin Menu")
                player.closeInventory()
                player.scheduler.run(plugin, { _ ->
                    AdminMenuGui(plugin, worldManager, inviteManager, dataManager).open(player)
                }, null)
            }
    }

    private fun createPreviousPageItem(gui: PaginatedGui): GuiItem {
        return ItemBuilder.from(Material.ARROW)
            .name(Component.text("Previous Page", NamedTextColor.YELLOW))
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] AdminWorldBrowserGui: Admin ${(event.whoClicked as? Player)?.name} navigated to previous page")
                gui.previous()
            }
    }

    private fun createPageIndicatorItem(gui: PaginatedGui): GuiItem {
        val currentPage = gui.currentPageNum + 1
        val totalPages = maxOf(1, gui.pagesNum)

        return ItemBuilder.from(Material.PAPER)
            .name(Component.text("Page $currentPage/$totalPages", NamedTextColor.GOLD))
            .asGuiItem { event ->
                event.isCancelled = true
            }
    }

    private fun createNextPageItem(gui: PaginatedGui): GuiItem {
        return ItemBuilder.from(Material.ARROW)
            .name(Component.text("Next Page", NamedTextColor.YELLOW))
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] AdminWorldBrowserGui: Admin ${(event.whoClicked as? Player)?.name} navigated to next page")
                gui.next()
            }
    }

    private fun createFilterItem(player: Player): GuiItem {
        val (filterName, nextFilter) = when (currentFilter) {
            FilterMode.ALL -> "All Worlds" to FilterMode.ENABLED_ONLY
            FilterMode.ENABLED_ONLY -> "Enabled Only" to FilterMode.DISABLED_ONLY
            FilterMode.DISABLED_ONLY -> "Disabled Only" to FilterMode.ALL
        }

        val nextFilterName = when (nextFilter) {
            FilterMode.ALL -> "All Worlds"
            FilterMode.ENABLED_ONLY -> "Enabled Only"
            FilterMode.DISABLED_ONLY -> "Disabled Only"
        }

        return ItemBuilder.from(Material.HOPPER)
            .name(Component.text("Filter: $filterName", NamedTextColor.AQUA))
            .lore(
                listOf(
                    Component.text("Click to change to: $nextFilterName", NamedTextColor.YELLOW)
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] AdminWorldBrowserGui: Admin ${player.name} changed filter from $currentFilter to $nextFilter")
                currentFilter = nextFilter

                // Refresh GUI
                player.closeInventory()
                player.scheduler.run(plugin, { _ ->
                    openWithFilter(player, null, null)
                }, null)
            }
    }
}
