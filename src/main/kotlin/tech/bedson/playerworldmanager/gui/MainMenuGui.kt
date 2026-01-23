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
import tech.bedson.playerworldmanager.managers.StatsManager
import tech.bedson.playerworldmanager.managers.WorldManager
import tech.bedson.playerworldmanager.models.PlayerWorld
import tech.bedson.playerworldmanager.utils.DebugLogger

/**
 * Main menu GUI for player worlds.
 * Shows owned worlds, create world button, invited worlds, and pending invites.
 */
class MainMenuGui(
    private val plugin: JavaPlugin,
    private val worldManager: WorldManager,
    private val inviteManager: InviteManager,
    private val dataManager: DataManager,
    private val statsManager: StatsManager? = null
) {
    private val debugLogger = DebugLogger(plugin, "MainMenuGui")

    fun open(player: Player) {
        debugLogger.debugMethodEntry("open", "player" to player.name, "playerUuid" to player.uniqueId)
        plugin.logger.info("[GUI] MainMenuGui: Opening for player ${player.name}")
        val ownedWorlds = dataManager.getWorldsByOwner(player.uniqueId)
        val pendingInvites = inviteManager.getPendingInvites(player.uniqueId)
        val invitedWorlds = dataManager.getAllWorlds()
            .filter { inviteManager.isInvited(player.uniqueId, it) }

        plugin.logger.info("[GUI] MainMenuGui: Player ${player.name} has ${ownedWorlds.size} owned worlds, ${pendingInvites.size} pending invites, ${invitedWorlds.size} invited worlds")
        debugLogger.debug("Loaded player data",
            "ownedWorldCount" to ownedWorlds.size,
            "pendingInviteCount" to pendingInvites.size,
            "invitedWorldCount" to invitedWorlds.size,
            "playerUuid" to player.uniqueId
        )

        // Use paginated GUI if many worlds
        val gui = if (ownedWorlds.size > 18) {
            plugin.logger.info("[GUI] MainMenuGui: Using paginated GUI for player ${player.name}")
            debugLogger.debug("Creating paginated GUI (worlds > 18)", "worldCount" to ownedWorlds.size)
            createPaginatedGui(player, ownedWorlds, pendingInvites.size, invitedWorlds.size)
        } else {
            plugin.logger.info("[GUI] MainMenuGui: Using simple GUI for player ${player.name}")
            debugLogger.debug("Creating simple GUI (worlds <= 18)", "worldCount" to ownedWorlds.size)
            createSimpleGui(player, ownedWorlds, pendingInvites.size, invitedWorlds.size)
        }

        debugLogger.debug("Opening GUI for player", "guiType" to gui.javaClass.simpleName)
        gui.open(player)
        debugLogger.debugMethodExit("open")
    }

    private fun createSimpleGui(
        player: Player,
        ownedWorlds: List<PlayerWorld>,
        pendingInviteCount: Int,
        invitedWorldsCount: Int
    ): Gui {
        debugLogger.debugMethodEntry("createSimpleGui",
            "player" to player.name,
            "ownedWorldCount" to ownedWorlds.size,
            "pendingInviteCount" to pendingInviteCount,
            "invitedWorldsCount" to invitedWorldsCount
        )
        val gui = Gui.gui()
            .title(Component.text("World Manager", NamedTextColor.GOLD))
            .rows(3)
            .disableAllInteractions()
            .create()

        // Add player's owned worlds
        debugLogger.debug("Adding owned worlds to GUI", "count" to minOf(ownedWorlds.size, 18))
        ownedWorlds.forEachIndexed { index, world ->
            if (index < 18) {
                debugLogger.debug("Adding world item", "slot" to index, "worldName" to world.name, "worldId" to world.id)
                gui.setItem(index, createWorldItem(player, world))
            }
        }

        // Create New World button (if under limit)
        val canCreate = worldManager.canCreateWorld(player)
        debugLogger.debug("Checking create world permission", "canCreate" to canCreate)
        if (canCreate) {
            gui.setItem(20, createNewWorldItem(player))
        }

        // Invited Worlds button
        gui.setItem(22, createInvitedWorldsItem(player, invitedWorldsCount))

        // Pending Invites button
        gui.setItem(24, createPendingInvitesItem(player, pendingInviteCount))

        // Return to Spawn button
        gui.setItem(13, createReturnToSpawnItem(player))

        debugLogger.debugMethodExit("createSimpleGui", "gui created with 3 rows")
        return gui
    }

    private fun createPaginatedGui(
        player: Player,
        ownedWorlds: List<PlayerWorld>,
        pendingInviteCount: Int,
        invitedWorldsCount: Int
    ): PaginatedGui {
        debugLogger.debugMethodEntry("createPaginatedGui",
            "player" to player.name,
            "ownedWorldCount" to ownedWorlds.size,
            "pendingInviteCount" to pendingInviteCount,
            "invitedWorldsCount" to invitedWorldsCount
        )
        val gui = Gui.paginated()
            .title(Component.text("World Manager", NamedTextColor.GOLD))
            .rows(6)
            .disableAllInteractions()
            .create()

        // Add all owned worlds
        debugLogger.debug("Adding all owned worlds to paginated GUI", "count" to ownedWorlds.size)
        ownedWorlds.forEach { world ->
            debugLogger.debug("Adding world item to paginated GUI", "worldName" to world.name, "worldId" to world.id)
            gui.addItem(createWorldItem(player, world))
        }

        // Navigation buttons
        debugLogger.debug("Adding navigation buttons to paginated GUI")
        gui.setItem(6, 3, ItemBuilder.from(Material.ARROW)
            .name(Component.text("Previous Page", NamedTextColor.YELLOW))
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] MainMenuGui: Player ${player.name} navigated to previous page")
                debugLogger.debug("Previous page clicked", "player" to player.name, "currentPage" to gui.currentPageNum)
                gui.previous()
            })

        gui.setItem(6, 7, ItemBuilder.from(Material.ARROW)
            .name(Component.text("Next Page", NamedTextColor.YELLOW))
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] MainMenuGui: Player ${player.name} navigated to next page")
                debugLogger.debug("Next page clicked", "player" to player.name, "currentPage" to gui.currentPageNum)
                gui.next()
            })

        // Create New World button
        val canCreatePaginated = worldManager.canCreateWorld(player)
        debugLogger.debug("Checking create world permission for paginated GUI", "canCreate" to canCreatePaginated)
        if (canCreatePaginated) {
            gui.setItem(6, 1, createNewWorldItem(player))
        }

        // Invited Worlds button
        gui.setItem(6, 4, createInvitedWorldsItem(player, invitedWorldsCount))

        // Pending Invites button
        gui.setItem(6, 6, createPendingInvitesItem(player, pendingInviteCount))

        // Return to Spawn button
        gui.setItem(6, 5, createReturnToSpawnItem(player))

        debugLogger.debugMethodExit("createPaginatedGui", "gui created with 6 rows")
        return gui
    }

    private fun createWorldItem(player: Player, world: PlayerWorld): GuiItem {
        debugLogger.debugMethodEntry("createWorldItem", "player" to player.name, "worldName" to world.name, "worldId" to world.id)
        val bukkitWorld = worldManager.getBukkitWorld(world)
        val playersOnline = bukkitWorld?.players?.size ?: 0
        val invitedCount = world.invitedPlayers.size
        debugLogger.debugState("WorldItem",
            "worldName" to world.name,
            "worldType" to world.worldType,
            "playersOnline" to playersOnline,
            "invitedCount" to invitedCount,
            "worldLoaded" to (bukkitWorld != null)
        )

        val item = ItemBuilder.from(Material.GRASS_BLOCK)
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
                debugLogger.debug("World item clicked", "player" to player.name, "worldName" to world.name, "worldId" to world.id)
                player.closeInventory()
                // Open world management GUI on player's scheduler
                player.scheduler.run(plugin, { _ ->
                    debugLogger.debug("Opening WorldManageGui", "player" to player.name, "worldName" to world.name)
                    WorldManageGui(plugin, worldManager, inviteManager, dataManager, statsManager).open(player, world)
                }, null)
            }
        debugLogger.debugMethodExit("createWorldItem", "GuiItem for ${world.name}")
        return item
    }

    private fun createNewWorldItem(player: Player): GuiItem {
        debugLogger.debugMethodEntry("createNewWorldItem", "player" to player.name)
        val playerData = dataManager.loadPlayerData(player.uniqueId)
        val currentCount = worldManager.getWorldCount(player.uniqueId)
        val limit = playerData?.worldLimit ?: 3
        debugLogger.debugState("NewWorldItem",
            "currentCount" to currentCount,
            "limit" to limit,
            "hasPlayerData" to (playerData != null)
        )

        val item = ItemBuilder.from(Material.EMERALD_BLOCK)
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
                debugLogger.debug("Create New World button clicked", "player" to player.name)
                player.closeInventory()
                player.sendMessage(
                    Component.text("Use ", NamedTextColor.YELLOW)
                        .append(Component.text("/world create <name>", NamedTextColor.GOLD))
                        .append(Component.text(" to create a world", NamedTextColor.YELLOW))
                )
            }
        debugLogger.debugMethodExit("createNewWorldItem")
        return item
    }

    private fun createInvitedWorldsItem(player: Player, count: Int): GuiItem {
        debugLogger.debugMethodEntry("createInvitedWorldsItem", "player" to player.name, "count" to count)
        val item = ItemBuilder.from(Material.ENDER_PEARL)
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
                debugLogger.debug("Invited Worlds button clicked", "player" to player.name, "invitedCount" to count)
                player.closeInventory()
                // Open invited worlds GUI on player's scheduler
                player.scheduler.run(plugin, { _ ->
                    debugLogger.debug("Opening InvitedWorldsGui", "player" to player.name)
                    InvitedWorldsGui(plugin, worldManager, inviteManager, dataManager).open(player)
                }, null)
            }
        debugLogger.debugMethodExit("createInvitedWorldsItem")
        return item
    }

    private fun createPendingInvitesItem(player: Player, count: Int): GuiItem {
        debugLogger.debugMethodEntry("createPendingInvitesItem", "player" to player.name, "count" to count)
        val material = if (count > 0) Material.PAPER else Material.PAPER
        val color = if (count > 0) NamedTextColor.GREEN else NamedTextColor.GRAY
        debugLogger.debugState("PendingInvitesItem", "count" to count, "hasInvites" to (count > 0))

        val item = ItemBuilder.from(material)
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
                debugLogger.debug("Pending Invites button clicked", "player" to player.name, "pendingCount" to count)
                player.closeInventory()
                // Open invites GUI on player's scheduler
                player.scheduler.run(plugin, { _ ->
                    debugLogger.debug("Opening InvitesGui", "player" to player.name)
                    InvitesGui(plugin, inviteManager).open(player)
                }, null)
            }
        debugLogger.debugMethodExit("createPendingInvitesItem")
        return item
    }

    private fun createReturnToSpawnItem(player: Player): GuiItem {
        debugLogger.debugMethodEntry("createReturnToSpawnItem", "player" to player.name)
        val item = ItemBuilder.from(Material.COMPASS)
            .name(Component.text("Return to Spawn", NamedTextColor.AQUA))
            .lore(
                listOf(
                    Component.text("Teleport to the default world spawn", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Click to teleport", NamedTextColor.YELLOW)
                )
            )
            .asGuiItem { event ->
                event.isCancelled = true
                plugin.logger.info("[GUI] MainMenuGui: Player ${player.name} clicked Return to Spawn button")
                debugLogger.debug("Return to Spawn button clicked", "player" to player.name, "currentWorld" to player.world.name)
                player.closeInventory()

                player.sendMessage(Component.text("Teleporting to spawn...", NamedTextColor.YELLOW))
                debugLogger.debug("Initiating teleport to vanilla world", "player" to player.name)

                // Use WorldManager to properly save/restore state
                worldManager.teleportToVanillaWorld(player).thenAccept { success ->
                    player.scheduler.run(plugin, { _ ->
                        if (success) {
                            plugin.logger.info("[GUI] MainMenuGui: Player ${player.name} teleported successfully to spawn")
                            debugLogger.debug("Teleport to spawn successful", "player" to player.name)
                            player.sendMessage(
                                Component.text("Teleported to spawn", NamedTextColor.GREEN)
                            )
                        } else {
                            plugin.logger.warning("[GUI] MainMenuGui: Failed to teleport ${player.name} to spawn")
                            debugLogger.debug("Teleport to spawn failed", "player" to player.name)
                            player.sendMessage(Component.text("Failed to teleport to spawn", NamedTextColor.RED))
                        }
                    }, null)
                }
            }
        debugLogger.debugMethodExit("createReturnToSpawnItem")
        return item
    }
}
