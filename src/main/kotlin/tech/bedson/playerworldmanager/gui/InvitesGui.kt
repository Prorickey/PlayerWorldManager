package tech.bedson.playerworldmanager.gui

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.managers.InviteManager
import tech.bedson.playerworldmanager.models.WorldInvite
import tech.bedson.playerworldmanager.utils.DebugLogger
import java.text.SimpleDateFormat
import java.util.Date

/**
 * GUI showing pending world invites.
 * Players can accept (left click) or deny (right click) invites.
 */
class InvitesGui(
    private val plugin: JavaPlugin,
    private val inviteManager: InviteManager
) {
    private val debugLogger = DebugLogger(plugin, "InvitesGui")
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm")

    fun open(player: Player) {
        debugLogger.debugMethodEntry("open", "player" to player.name, "playerUuid" to player.uniqueId)
        plugin.logger.info("[GUI] InvitesGui: Opening for player ${player.name}")
        val pendingInvites = inviteManager.getPendingInvites(player.uniqueId)

        plugin.logger.info("[GUI] InvitesGui: Player ${player.name} has ${pendingInvites.size} pending invites")
        debugLogger.debug("Loaded pending invites", "count" to pendingInvites.size, "playerUuid" to player.uniqueId)

        val gui = Gui.paginated()
            .title(Component.text("Pending Invites", NamedTextColor.GOLD))
            .rows(6)
            .disableAllInteractions()
            .create()

        // Add all pending invites
        debugLogger.debug("Adding pending invites to GUI", "count" to pendingInvites.size)
        pendingInvites.forEach { invite ->
            debugLogger.debug("Adding invite item", "worldName" to invite.worldName, "ownerName" to invite.ownerName)
            gui.addItem(createInviteItem(player, invite))
        }

        // Navigation buttons
        debugLogger.debug("Adding navigation buttons")
        gui.setItem(6, 3, ItemBuilder.from(Material.ARROW)
            .name(Component.text("Previous Page", NamedTextColor.YELLOW))
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] InvitesGui: Player ${player.name} navigated to previous page")
                debugLogger.debug("Previous page clicked", "player" to player.name, "currentPage" to gui.currentPageNum)
                gui.previous()
            })

        gui.setItem(6, 7, ItemBuilder.from(Material.ARROW)
            .name(Component.text("Next Page", NamedTextColor.YELLOW))
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] InvitesGui: Player ${player.name} navigated to next page")
                debugLogger.debug("Next page clicked", "player" to player.name, "currentPage" to gui.currentPageNum)
                gui.next()
            })

        // Back button
        gui.setItem(6, 5, ItemBuilder.from(Material.BARRIER)
            .name(Component.text("Back to Main Menu", NamedTextColor.RED))
            .asGuiItem { event ->
                event.isCancelled = true
                debugLogger.debug("Back button clicked", "player" to player.name)
                player.closeInventory()
                // Note: We need access to other managers to open MainMenuGui
                // For now, just close the GUI
                player.sendMessage(
                    Component.text("Use ", NamedTextColor.YELLOW)
                        .append(Component.text("/world menu", NamedTextColor.GOLD))
                        .append(Component.text(" to return to the main menu", NamedTextColor.YELLOW))
                )
            })

        // If no pending invites, show a message
        if (pendingInvites.isEmpty()) {
            debugLogger.debug("No pending invites found, showing empty state")
            gui.setItem(22, ItemBuilder.from(Material.BARRIER)
                .name(Component.text("No Pending Invites", NamedTextColor.RED))
                .lore(
                    listOf(
                        Component.text("You don't have any pending invites", NamedTextColor.GRAY)
                    )
                )
                .asGuiItem())
        }

        debugLogger.debug("Opening GUI for player", "player" to player.name)
        gui.open(player)
        debugLogger.debugMethodExit("open")
    }

    private fun createInviteItem(player: Player, invite: WorldInvite): GuiItem {
        debugLogger.debugMethodEntry("createInviteItem", "player" to player.name, "worldName" to invite.worldName, "ownerName" to invite.ownerName)
        val sentDate = dateFormat.format(Date(invite.sentAt))
        debugLogger.debugState("InviteItem",
            "worldName" to invite.worldName,
            "ownerName" to invite.ownerName,
            "sentDate" to sentDate,
            "worldId" to invite.worldId
        )

        val item = ItemBuilder.from(Material.PAPER)
            .name(Component.text(invite.worldName, NamedTextColor.GOLD))
            .lore(
                listOf(
                    Component.text("From: ${invite.ownerName}", NamedTextColor.GRAY),
                    Component.text("Sent: $sentDate", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Left Click: ", NamedTextColor.GREEN)
                        .append(Component.text("Accept", NamedTextColor.WHITE)),
                    Component.text("Right Click: ", NamedTextColor.RED)
                        .append(Component.text("Deny", NamedTextColor.WHITE))
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true

                when (event.click) {
                    ClickType.LEFT -> {
                        // Accept invite
                        plugin.logger.info("[GUI] InvitesGui: Player ${player.name} accepting invite to world ${invite.worldName}")
                        debugLogger.debug("Accept invite clicked", "player" to player.name, "worldName" to invite.worldName)
                        player.closeInventory()
                        player.scheduler.run(plugin, { _ ->
                            debugLogger.debug("Processing accept invite", "player" to player.name, "worldName" to invite.worldName)
                            val result = inviteManager.acceptInvite(invite, player)
                            result.onSuccess {
                                plugin.logger.info("[GUI] InvitesGui: Player ${player.name} successfully accepted invite to world ${invite.worldName}")
                                debugLogger.debug("Accept invite successful", "player" to player.name, "worldName" to invite.worldName)
                                player.sendMessage(
                                    Component.text("Accepted invite to ", NamedTextColor.GREEN)
                                        .append(Component.text(invite.worldName, NamedTextColor.GOLD))
                                )
                            }.onFailure { error ->
                                plugin.logger.warning("[GUI] InvitesGui: Player ${player.name} failed to accept invite to world ${invite.worldName}: ${error.message}")
                                debugLogger.debug("Accept invite failed", "player" to player.name, "worldName" to invite.worldName, "error" to error.message)
                                player.sendMessage(
                                    Component.text("Failed to accept invite: ${error.message}", NamedTextColor.RED)
                                )
                            }
                        }, null)
                    }

                    ClickType.RIGHT -> {
                        // Deny invite
                        plugin.logger.info("[GUI] InvitesGui: Player ${player.name} denying invite to world ${invite.worldName}")
                        debugLogger.debug("Deny invite clicked", "player" to player.name, "worldName" to invite.worldName)
                        player.closeInventory()
                        player.scheduler.run(plugin, { _ ->
                            debugLogger.debug("Processing deny invite", "player" to player.name, "worldName" to invite.worldName)
                            val result = inviteManager.denyInvite(invite, player)
                            result.onSuccess {
                                plugin.logger.info("[GUI] InvitesGui: Player ${player.name} successfully denied invite to world ${invite.worldName}")
                                debugLogger.debug("Deny invite successful", "player" to player.name, "worldName" to invite.worldName)
                                player.sendMessage(
                                    Component.text("Declined invite to ", NamedTextColor.YELLOW)
                                        .append(Component.text(invite.worldName, NamedTextColor.GOLD))
                                )
                            }.onFailure { error ->
                                plugin.logger.warning("[GUI] InvitesGui: Player ${player.name} failed to deny invite to world ${invite.worldName}: ${error.message}")
                                debugLogger.debug("Deny invite failed", "player" to player.name, "worldName" to invite.worldName, "error" to error.message)
                                player.sendMessage(
                                    Component.text("Failed to decline invite: ${error.message}", NamedTextColor.RED)
                                )
                            }
                        }, null)
                    }

                    else -> {
                        // Other click types - do nothing
                        debugLogger.debug("Other click type ignored", "player" to player.name, "clickType" to event.click)
                    }
                }
            }
        debugLogger.debugMethodExit("createInviteItem", "item created")
        return item
    }
}
