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
import tech.bedson.playerworldmanager.models.PlayerWorld
import tech.bedson.playerworldmanager.utils.DebugLogger

/**
 * GUI showing worlds the player has been invited to.
 * Allows quick teleportation to invited worlds.
 */
class InvitedWorldsGui(
    private val plugin: JavaPlugin,
    private val worldManager: WorldManager,
    private val inviteManager: InviteManager,
    private val dataManager: DataManager
) {
    private val debugLogger = DebugLogger(plugin, "InvitedWorldsGui")

    fun open(player: Player) {
        debugLogger.debugMethodEntry("open", "player" to player.name, "playerUuid" to player.uniqueId)
        plugin.logger.info("[GUI] InvitedWorldsGui: Opening for player ${player.name}")
        val invitedWorlds = dataManager.getAllWorlds()
            .filter { inviteManager.isInvited(player.uniqueId, it) }

        plugin.logger.info("[GUI] InvitedWorldsGui: Player ${player.name} has ${invitedWorlds.size} invited worlds")
        debugLogger.debug("Loaded invited worlds", "count" to invitedWorlds.size, "playerUuid" to player.uniqueId)

        val gui = Gui.paginated()
            .title(Component.text("Invited Worlds", NamedTextColor.GOLD))
            .rows(6)
            .disableAllInteractions()
            .create()

        // Add all invited worlds
        debugLogger.debug("Adding invited worlds to GUI", "count" to invitedWorlds.size)
        invitedWorlds.forEach { world ->
            debugLogger.debug("Adding world item", "worldName" to world.name, "ownerName" to world.ownerName)
            gui.addItem(createWorldItem(player, world))
        }

        // Navigation buttons
        debugLogger.debug("Adding navigation buttons")
        gui.setItem(6, 3, ItemBuilder.from(Material.ARROW)
            .name(Component.text("Previous Page", NamedTextColor.YELLOW))
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] InvitedWorldsGui: Player ${player.name} navigated to previous page")
                debugLogger.debug("Previous page clicked", "player" to player.name, "currentPage" to gui.currentPageNum)
                gui.previous()
            })

        gui.setItem(6, 7, ItemBuilder.from(Material.ARROW)
            .name(Component.text("Next Page", NamedTextColor.YELLOW))
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] InvitedWorldsGui: Player ${player.name} navigated to next page")
                debugLogger.debug("Next page clicked", "player" to player.name, "currentPage" to gui.currentPageNum)
                gui.next()
            })

        // Back button
        gui.setItem(6, 5, ItemBuilder.from(Material.BARRIER)
            .name(Component.text("Back to Main Menu", NamedTextColor.RED))
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] InvitedWorldsGui: Player ${player.name} clicked Back to Main Menu")
                debugLogger.debug("Back button clicked", "player" to player.name)
                player.closeInventory()
                player.scheduler.run(plugin, { _ ->
                    debugLogger.debug("Opening MainMenuGui", "player" to player.name)
                    MainMenuGui(plugin, worldManager, inviteManager, dataManager).open(player)
                }, null)
            })

        // If no invited worlds, show a message
        if (invitedWorlds.isEmpty()) {
            debugLogger.debug("No invited worlds found, showing empty state")
            gui.setItem(22, ItemBuilder.from(Material.BARRIER)
                .name(Component.text("No Invited Worlds", NamedTextColor.RED))
                .lore(
                    listOf(
                        Component.text("You haven't been invited to any worlds yet", NamedTextColor.GRAY)
                    )
                )
                .asGuiItem())
        }

        debugLogger.debug("Opening GUI for player", "player" to player.name)
        gui.open(player)
        debugLogger.debugMethodExit("open")
    }

    private fun createWorldItem(player: Player, world: PlayerWorld): GuiItem {
        debugLogger.debugMethodEntry("createWorldItem", "player" to player.name, "worldName" to world.name)
        val bukkitWorld = worldManager.getBukkitWorld(world)
        val playersOnline = bukkitWorld?.players?.size ?: 0
        debugLogger.debugState("WorldItem",
            "worldName" to world.name,
            "ownerName" to world.ownerName,
            "worldType" to world.worldType,
            "playersOnline" to playersOnline,
            "worldLoaded" to (bukkitWorld != null)
        )

        val item = ItemBuilder.from(Material.GRASS_BLOCK)
            .name(Component.text(world.name, NamedTextColor.GOLD))
            .lore(
                listOf(
                    Component.text("Owner: ${world.ownerName}", NamedTextColor.GRAY),
                    Component.text("World Type: ${world.worldType}", NamedTextColor.GRAY),
                    Component.text("Players Online: $playersOnline", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Click to visit", NamedTextColor.GREEN)
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] InvitedWorldsGui: Player ${player.name} clicked to teleport to world ${world.name}")
                player.closeInventory()
                player.scheduler.run(plugin, { _ ->
                    worldManager.teleportToWorld(player, world).thenAccept { success ->
                        if (success) {
                            plugin.logger.info("[GUI] InvitedWorldsGui: Player ${player.name} successfully teleported to world ${world.name}")
                            player.sendMessage(
                                Component.text("Teleported to ", NamedTextColor.GREEN)
                                    .append(Component.text(world.name, NamedTextColor.GOLD))
                            )
                        } else {
                            plugin.logger.warning("[GUI] InvitedWorldsGui: Player ${player.name} failed to teleport to world ${world.name}")
                            player.sendMessage(
                                Component.text("Failed to teleport to world", NamedTextColor.RED)
                            )
                        }
                    }
                }, null)
            }
        debugLogger.debugMethodExit("createWorldItem", "item created")
        return item
    }
}
