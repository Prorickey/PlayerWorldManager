package tech.bedson.playerworldmanager.gui

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.GuiItem
import dev.triumphteam.gui.guis.PaginatedGui
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.managers.DataManager
import tech.bedson.playerworldmanager.managers.InviteManager
import tech.bedson.playerworldmanager.managers.WorldManager
import tech.bedson.playerworldmanager.models.PlayerWorld
import tech.bedson.playerworldmanager.utils.DebugLogger
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
    private val debugLogger = DebugLogger(plugin, "AdminWorldBrowserGui")

    private enum class FilterMode {
        ALL, ENABLED_ONLY, DISABLED_ONLY
    }

    private var currentFilter = FilterMode.ALL

    /**
     * Open browsing all worlds with optional filter.
     */
    fun open(player: Player, disabledOnly: Boolean = false) {
        debugLogger.debugMethodEntry("open", "player" to player.name, "disabledOnly" to disabledOnly)
        plugin.logger.info("[GUI] AdminWorldBrowserGui: Opening for admin ${player.name}, disabledOnly=$disabledOnly")
        if (disabledOnly) {
            currentFilter = FilterMode.DISABLED_ONLY
            debugLogger.debug("Filter set to DISABLED_ONLY")
        }
        openWithFilter(player, null, null)
        debugLogger.debugMethodExit("open")
    }

    /**
     * Open browsing specific player's worlds.
     */
    fun openForPlayer(player: Player, targetUuid: UUID, targetName: String) {
        debugLogger.debugMethodEntry("openForPlayer", "player" to player.name, "targetUuid" to targetUuid, "targetName" to targetName)
        plugin.logger.info("[GUI] AdminWorldBrowserGui: Opening for admin ${player.name}, browsing worlds of player $targetName")
        openWithFilter(player, targetUuid, targetName)
        debugLogger.debugMethodExit("openForPlayer")
    }

    private fun openWithFilter(player: Player, targetUuid: UUID?, targetName: String?) {
        debugLogger.debugMethodEntry("openWithFilter", "player" to player.name, "targetUuid" to targetUuid, "targetName" to targetName)
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
        debugLogger.debug("Loading worlds", "targetUuid" to targetUuid, "filter" to currentFilter)
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
        debugLogger.debug("Worlds loaded and filtered",
            "totalWorlds" to allWorlds.size,
            "filteredWorlds" to filteredWorlds.size,
            "filter" to currentFilter
        )

        // Add world items
        debugLogger.debug("Adding world items to GUI", "count" to sortedWorlds.size)
        sortedWorlds.forEach { world ->
            debugLogger.debug("Adding world item", "worldName" to world.name, "ownerName" to world.ownerName, "isEnabled" to world.isEnabled)
            gui.addItem(createWorldItem(player, world))
        }

        // Row 6 - Navigation
        debugLogger.debug("Adding navigation buttons")
        gui.setItem(6, 1, createBackItem(player))
        gui.setItem(6, 4, createPreviousPageItem(gui))
        gui.setItem(6, 5, createPageIndicatorItem(gui))
        gui.setItem(6, 6, createNextPageItem(gui))
        gui.setItem(6, 9, createFilterItem(player))

        // Fill remaining slots with glass panes
        debugLogger.debug("Filling empty slots with glass panes")
        for (i in listOf(0, 2, 3, 7, 8)) {
            gui.setItem(6, i + 1, ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .asGuiItem())
        }

        debugLogger.debug("Opening GUI for player", "player" to player.name)
        gui.open(player)
        debugLogger.debugMethodExit("openWithFilter")
    }

    private fun createWorldItem(player: Player, world: PlayerWorld): GuiItem {
        debugLogger.debugMethodEntry("createWorldItem", "player" to player.name, "worldName" to world.name, "ownerName" to world.ownerName)
        val material = if (world.isEnabled) Material.GRASS_BLOCK else Material.GRAY_STAINED_GLASS
        val nameColor = if (world.isEnabled) NamedTextColor.GREEN else NamedTextColor.GRAY
        val statusText = if (world.isEnabled) "Enabled" else "Disabled"
        val statusColor = if (world.isEnabled) NamedTextColor.GREEN else NamedTextColor.RED

        val bukkitWorld = worldManager.getBukkitWorld(world)
        val playersOnline = bukkitWorld?.players?.size ?: 0
        val invitedCount = world.invitedPlayers.size

        val dateFormat = SimpleDateFormat("MMM dd, yyyy")
        val createdDate = dateFormat.format(Date(world.createdAt))

        debugLogger.debugState("WorldItem",
            "worldName" to world.name,
            "ownerName" to world.ownerName,
            "isEnabled" to world.isEnabled,
            "playersOnline" to playersOnline,
            "invitedCount" to invitedCount,
            "worldType" to world.worldType,
            "worldLoaded" to (bukkitWorld != null)
        )

        val item = ItemBuilder.from(material)
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
                        debugLogger.debug("Teleport (LEFT click)", "player" to player.name, "worldName" to world.name)
                        player.closeInventory()
                        player.scheduler.run(plugin, { _ ->
                            debugLogger.debug("Initiating teleport", "player" to player.name, "worldName" to world.name)
                            worldManager.teleportToWorld(player, world).thenAccept { success ->
                                if (success) {
                                    plugin.logger.info("[GUI] AdminWorldBrowserGui: Admin ${player.name} successfully teleported to world ${world.name}")
                                    debugLogger.debug("Teleport successful", "player" to player.name, "worldName" to world.name)
                                    player.sendMessage(
                                        Component.text("Teleported to ", NamedTextColor.GREEN)
                                            .append(Component.text(world.name, NamedTextColor.GOLD))
                                    )
                                } else {
                                    plugin.logger.warning("[GUI] AdminWorldBrowserGui: Admin ${player.name} failed to teleport to world ${world.name}")
                                    debugLogger.debug("Teleport failed", "player" to player.name, "worldName" to world.name)
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
                        debugLogger.debug("Manage (RIGHT click)", "player" to player.name, "worldName" to world.name)
                        player.closeInventory()
                        player.scheduler.run(plugin, { _ ->
                            debugLogger.debug("Opening AdminWorldManageGui", "player" to player.name, "worldName" to world.name)
                            AdminWorldManageGui(plugin, worldManager, inviteManager, dataManager)
                                .open(player, world)
                        }, null)
                    }
                    ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT -> {
                        // Toggle enable/disable
                        val newStatus = !world.isEnabled
                        plugin.logger.info("[GUI] AdminWorldBrowserGui: Admin ${player.name} toggling world ${world.name} from ${world.isEnabled} to $newStatus")
                        debugLogger.debug("Toggle enable/disable (SHIFT click)", "player" to player.name, "worldName" to world.name, "oldStatus" to world.isEnabled, "newStatus" to newStatus)
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
                            debugLogger.debug("Refreshing GUI after toggle", "player" to player.name)
                            openWithFilter(player, null, null)
                        }, null)
                    }
                    else -> {
                        debugLogger.debug("Other click type ignored", "clickType" to event.click)
                    }
                }
            }
        debugLogger.debugMethodExit("createWorldItem", "item created")
        return item
    }

    private fun createBackItem(player: Player): GuiItem {
        debugLogger.debugMethodEntry("createBackItem", "player" to player.name)
        val item = ItemBuilder.from(Material.ARROW)
            .name(Component.text("Back to Admin Menu", NamedTextColor.YELLOW))
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] AdminWorldBrowserGui: Admin ${player.name} clicked Back to Admin Menu")
                debugLogger.debug("Back button clicked", "player" to player.name)
                player.closeInventory()
                player.scheduler.run(plugin, { _ ->
                    debugLogger.debug("Opening AdminMenuGui", "player" to player.name)
                    AdminMenuGui(plugin, worldManager, inviteManager, dataManager).open(player)
                }, null)
            }
        debugLogger.debugMethodExit("createBackItem")
        return item
    }

    private fun createPreviousPageItem(gui: PaginatedGui): GuiItem {
        debugLogger.debugMethodEntry("createPreviousPageItem")
        val item = ItemBuilder.from(Material.ARROW)
            .name(Component.text("Previous Page", NamedTextColor.YELLOW))
            .asGuiItem { event ->
                event.isCancelled = true
                val clickedPlayer = event.whoClicked as? Player
                plugin.logger.info("[GUI] AdminWorldBrowserGui: Admin ${clickedPlayer?.name} navigated to previous page")
                debugLogger.debug("Previous page clicked", "player" to (clickedPlayer?.name ?: "unknown"), "currentPage" to gui.currentPageNum)
                gui.previous()
            }
        debugLogger.debugMethodExit("createPreviousPageItem")
        return item
    }

    private fun createPageIndicatorItem(gui: PaginatedGui): GuiItem {
        debugLogger.debugMethodEntry("createPageIndicatorItem")
        val currentPage = gui.currentPageNum + 1
        val totalPages = maxOf(1, gui.pagesNum)
        debugLogger.debugState("PageIndicator", "currentPage" to currentPage, "totalPages" to totalPages)

        val item = ItemBuilder.from(Material.PAPER)
            .name(Component.text("Page $currentPage/$totalPages", NamedTextColor.GOLD))
            .asGuiItem { event ->
                event.isCancelled = true
            }
        debugLogger.debugMethodExit("createPageIndicatorItem")
        return item
    }

    private fun createNextPageItem(gui: PaginatedGui): GuiItem {
        debugLogger.debugMethodEntry("createNextPageItem")
        val item = ItemBuilder.from(Material.ARROW)
            .name(Component.text("Next Page", NamedTextColor.YELLOW))
            .asGuiItem { event ->
                event.isCancelled = true
                val clickedPlayer = event.whoClicked as? Player
                plugin.logger.info("[GUI] AdminWorldBrowserGui: Admin ${clickedPlayer?.name} navigated to next page")
                debugLogger.debug("Next page clicked", "player" to (clickedPlayer?.name ?: "unknown"), "currentPage" to gui.currentPageNum)
                gui.next()
            }
        debugLogger.debugMethodExit("createNextPageItem")
        return item
    }

    private fun createFilterItem(player: Player): GuiItem {
        debugLogger.debugMethodEntry("createFilterItem", "player" to player.name)
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

        debugLogger.debugState("FilterItem", "currentFilter" to filterName, "nextFilter" to nextFilterName)

        val item = ItemBuilder.from(Material.HOPPER)
            .name(Component.text("Filter: $filterName", NamedTextColor.AQUA))
            .lore(
                listOf(
                    Component.text("Click to change to: $nextFilterName", NamedTextColor.YELLOW)
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] AdminWorldBrowserGui: Admin ${player.name} changed filter from $currentFilter to $nextFilter")
                debugLogger.debug("Filter button clicked", "player" to player.name, "oldFilter" to currentFilter, "newFilter" to nextFilter)
                currentFilter = nextFilter

                // Refresh GUI
                player.closeInventory()
                player.scheduler.run(plugin, { _ ->
                    debugLogger.debug("Refreshing GUI with new filter", "player" to player.name, "filter" to currentFilter)
                    openWithFilter(player, null, null)
                }, null)
            }
        debugLogger.debugMethodExit("createFilterItem")
        return item
    }
}
