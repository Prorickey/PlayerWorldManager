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

    fun open(player: Player) {
        plugin.logger.info("[GUI] InvitedWorldsGui: Opening for player ${player.name}")
        val invitedWorlds = dataManager.getAllWorlds()
            .filter { inviteManager.isInvited(player.uniqueId, it) }

        plugin.logger.info("[GUI] InvitedWorldsGui: Player ${player.name} has ${invitedWorlds.size} invited worlds")

        val gui = Gui.paginated()
            .title(Component.text("Invited Worlds", NamedTextColor.GOLD))
            .rows(6)
            .disableAllInteractions()
            .create()

        // Add all invited worlds
        invitedWorlds.forEach { world ->
            gui.addItem(createWorldItem(player, world))
        }

        // Navigation buttons
        gui.setItem(6, 3, ItemBuilder.from(Material.ARROW)
            .name(Component.text("Previous Page", NamedTextColor.YELLOW))
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] InvitedWorldsGui: Player ${player.name} navigated to previous page")
                gui.previous()
            })

        gui.setItem(6, 7, ItemBuilder.from(Material.ARROW)
            .name(Component.text("Next Page", NamedTextColor.YELLOW))
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] InvitedWorldsGui: Player ${player.name} navigated to next page")
                gui.next()
            })

        // Back button
        gui.setItem(6, 5, ItemBuilder.from(Material.BARRIER)
            .name(Component.text("Back to Main Menu", NamedTextColor.RED))
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] InvitedWorldsGui: Player ${player.name} clicked Back to Main Menu")
                player.closeInventory()
                player.scheduler.run(plugin, { _ ->
                    MainMenuGui(plugin, worldManager, inviteManager, dataManager).open(player)
                }, null)
            })

        // If no invited worlds, show a message
        if (invitedWorlds.isEmpty()) {
            gui.setItem(22, ItemBuilder.from(Material.BARRIER)
                .name(Component.text("No Invited Worlds", NamedTextColor.RED))
                .lore(
                    listOf(
                        Component.text("You haven't been invited to any worlds yet", NamedTextColor.GRAY)
                    )
                )
                .asGuiItem())
        }

        gui.open(player)
    }

    private fun createWorldItem(player: Player, world: PlayerWorld): GuiItem {
        val bukkitWorld = worldManager.getBukkitWorld(world)
        val playersOnline = bukkitWorld?.players?.size ?: 0

        return ItemBuilder.from(Material.GRASS_BLOCK)
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
    }
}
