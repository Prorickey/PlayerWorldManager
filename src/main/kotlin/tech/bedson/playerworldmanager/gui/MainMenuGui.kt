package tech.bedson.playerworldmanager.gui

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import dev.triumphteam.gui.guis.PaginatedGui
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.managers.DataManager
import tech.bedson.playerworldmanager.managers.InviteManager
import tech.bedson.playerworldmanager.managers.WorldManager
import tech.bedson.playerworldmanager.models.PlayerWorld

/**
 * Main menu GUI for player worlds.
 * Shows owned worlds, create world button, invited worlds, and pending invites.
 */
class MainMenuGui(
    private val plugin: JavaPlugin,
    private val worldManager: WorldManager,
    private val inviteManager: InviteManager,
    private val dataManager: DataManager
) {

    fun open(player: Player) {
        plugin.logger.info("[GUI] MainMenuGui: Opening for player ${player.name}")
        val ownedWorlds = dataManager.getWorldsByOwner(player.uniqueId)
        val pendingInvites = inviteManager.getPendingInvites(player.uniqueId)
        val invitedWorlds = dataManager.getAllWorlds()
            .filter { inviteManager.isInvited(player.uniqueId, it) }

        plugin.logger.info("[GUI] MainMenuGui: Player ${player.name} has ${ownedWorlds.size} owned worlds, ${pendingInvites.size} pending invites, ${invitedWorlds.size} invited worlds")

        // Use paginated GUI if many worlds
        val gui = if (ownedWorlds.size > 18) {
            plugin.logger.info("[GUI] MainMenuGui: Using paginated GUI for player ${player.name}")
            createPaginatedGui(player, ownedWorlds, pendingInvites.size, invitedWorlds.size)
        } else {
            plugin.logger.info("[GUI] MainMenuGui: Using simple GUI for player ${player.name}")
            createSimpleGui(player, ownedWorlds, pendingInvites.size, invitedWorlds.size)
        }

        gui.open(player)
    }

    private fun createSimpleGui(
        player: Player,
        ownedWorlds: List<PlayerWorld>,
        pendingInviteCount: Int,
        invitedWorldsCount: Int
    ): Gui {
        val gui = Gui.gui()
            .title(Component.text("World Manager", NamedTextColor.GOLD))
            .rows(3)
            .disableAllInteractions()
            .create()

        // Add player's owned worlds
        ownedWorlds.forEachIndexed { index, world ->
            if (index < 18) {
                gui.setItem(index, createWorldItem(player, world))
            }
        }

        // Create New World button (if under limit)
        if (worldManager.canCreateWorld(player)) {
            gui.setItem(20, createNewWorldItem(player))
        }

        // Invited Worlds button
        gui.setItem(22, createInvitedWorldsItem(player, invitedWorldsCount))

        // Pending Invites button
        gui.setItem(24, createPendingInvitesItem(player, pendingInviteCount))

        return gui
    }

    private fun createPaginatedGui(
        player: Player,
        ownedWorlds: List<PlayerWorld>,
        pendingInviteCount: Int,
        invitedWorldsCount: Int
    ): PaginatedGui {
        val gui = Gui.paginated()
            .title(Component.text("World Manager", NamedTextColor.GOLD))
            .rows(6)
            .disableAllInteractions()
            .create()

        // Add all owned worlds
        ownedWorlds.forEach { world ->
            gui.addItem(createWorldItem(player, world))
        }

        // Navigation buttons
        gui.setItem(6, 3, ItemBuilder.from(Material.ARROW)
            .name(Component.text("Previous Page", NamedTextColor.YELLOW))
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] MainMenuGui: Player ${player.name} navigated to previous page")
                gui.previous()
            })

        gui.setItem(6, 7, ItemBuilder.from(Material.ARROW)
            .name(Component.text("Next Page", NamedTextColor.YELLOW))
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] MainMenuGui: Player ${player.name} navigated to next page")
                gui.next()
            })

        // Create New World button
        if (worldManager.canCreateWorld(player)) {
            gui.setItem(6, 1, createNewWorldItem(player))
        }

        // Invited Worlds button
        gui.setItem(6, 4, createInvitedWorldsItem(player, invitedWorldsCount))

        // Pending Invites button
        gui.setItem(6, 6, createPendingInvitesItem(player, pendingInviteCount))

        return gui
    }

    private fun createWorldItem(player: Player, world: PlayerWorld): GuiItem {
        val bukkitWorld = worldManager.getBukkitWorld(world)
        val playersOnline = bukkitWorld?.players?.size ?: 0
        val invitedCount = world.invitedPlayers.size

        return ItemBuilder.from(Material.GRASS_BLOCK)
            .name(Component.text(world.name, NamedTextColor.GOLD))
            .lore(
                listOf(
                    Component.text("World Type: ${world.worldType}", NamedTextColor.GRAY),
                    Component.text("Players Online: $playersOnline", NamedTextColor.GRAY),
                    Component.text("Invited Players: $invitedCount", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Click to manage", NamedTextColor.YELLOW)
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] MainMenuGui: Player ${player.name} clicked on world ${world.name}")
                player.closeInventory()
                // Open world management GUI on player's scheduler
                player.scheduler.run(plugin, { _ ->
                    WorldManageGui(plugin, worldManager, inviteManager, dataManager).open(player, world)
                }, null)
            }
    }

    private fun createNewWorldItem(player: Player): GuiItem {
        val playerData = dataManager.loadPlayerData(player.uniqueId)
        val currentCount = worldManager.getWorldCount(player.uniqueId)
        val limit = playerData?.worldLimit ?: 3

        return ItemBuilder.from(Material.EMERALD_BLOCK)
            .name(Component.text("Create New World", NamedTextColor.GREEN))
            .lore(
                listOf(
                    Component.text("Worlds: $currentCount/$limit", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Click to create", NamedTextColor.YELLOW)
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] MainMenuGui: Player ${player.name} clicked Create New World button")
                player.closeInventory()
                player.sendMessage(
                    Component.text("Use ", NamedTextColor.YELLOW)
                        .append(Component.text("/world create <name>", NamedTextColor.GOLD))
                        .append(Component.text(" to create a world", NamedTextColor.YELLOW))
                )
            }
    }

    private fun createInvitedWorldsItem(player: Player, count: Int): GuiItem {
        return ItemBuilder.from(Material.ENDER_PEARL)
            .name(Component.text("Invited Worlds", NamedTextColor.AQUA))
            .lore(
                listOf(
                    Component.text("You have access to $count world(s)", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Click to view", NamedTextColor.YELLOW)
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] MainMenuGui: Player ${player.name} clicked Invited Worlds button (count: $count)")
                player.closeInventory()
                // Open invited worlds GUI on player's scheduler
                player.scheduler.run(plugin, { _ ->
                    InvitedWorldsGui(plugin, worldManager, inviteManager, dataManager).open(player)
                }, null)
            }
    }

    private fun createPendingInvitesItem(player: Player, count: Int): GuiItem {
        val material = if (count > 0) Material.PAPER else Material.PAPER
        val color = if (count > 0) NamedTextColor.GREEN else NamedTextColor.GRAY

        return ItemBuilder.from(material)
            .name(Component.text("Pending Invites", color))
            .lore(
                listOf(
                    Component.text("You have $count pending invite(s)", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Click to view", NamedTextColor.YELLOW)
                )
            )
            .amount(maxOf(1, count))
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] MainMenuGui: Player ${player.name} clicked Pending Invites button (count: $count)")
                player.closeInventory()
                // Open invites GUI on player's scheduler
                player.scheduler.run(plugin, { _ ->
                    InvitesGui(plugin, inviteManager).open(player)
                }, null)
            }
    }
}
