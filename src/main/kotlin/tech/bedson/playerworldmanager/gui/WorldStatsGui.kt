package tech.bedson.playerworldmanager.gui

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.managers.DataManager
import tech.bedson.playerworldmanager.managers.InviteManager
import tech.bedson.playerworldmanager.managers.StatsManager
import tech.bedson.playerworldmanager.managers.WorldManager
import tech.bedson.playerworldmanager.models.PlayerWorld
import tech.bedson.playerworldmanager.models.WorldStatistics
import tech.bedson.playerworldmanager.utils.DebugLogger
import java.util.concurrent.TimeUnit

/**
 * GUI for displaying world statistics.
 */
class WorldStatsGui(
    private val plugin: JavaPlugin,
    private val statsManager: StatsManager,
    private val worldManager: WorldManager,
    private val inviteManager: InviteManager,
    private val dataManager: DataManager
) {
    private val debugLogger = DebugLogger(plugin, "WorldStatsGui")

    /**
     * Open the statistics GUI for a world.
     */
    fun open(player: Player, world: PlayerWorld) {
        debugLogger.debugMethodEntry("open", "player" to player.name, "worldName" to world.name)

        val stats = statsManager.getWorldStats(world.id)
        val playerStats = stats.getOrCreatePlayerStats(player.uniqueId)

        val gui = Gui.gui()
            .title(Component.text("Statistics: ${world.name}", NamedTextColor.GOLD))
            .rows(5)
            .disableAllInteractions()
            .create()

        // Row 1: World statistics header and overview
        gui.setItem(4, createWorldOverviewItem(world, stats))

        // Row 2: Block statistics
        gui.setItem(10, createBlocksPlacedItem(stats))
        gui.setItem(11, createBlocksBrokenItem(stats))
        gui.setItem(12, createItemsCraftedItem(stats))

        // Row 3: Combat statistics
        gui.setItem(19, createMobsKilledItem(stats))
        gui.setItem(20, createAnimalsKilledItem(stats))
        gui.setItem(21, createPlayerKillsItem(stats))
        gui.setItem(22, createPlayerDeathsItem(stats))

        // Row 4: Time statistics
        gui.setItem(28, createTimePlayedItem(stats))

        // Row 5: Player personal stats & navigation
        gui.setItem(31, createPersonalStatsItem(player, playerStats))
        gui.setItem(36, createBackItem(player, world))
        gui.setItem(44, createRefreshItem(player, world))

        // Fill empty slots with glass panes
        for (i in 0 until 45) {
            if (gui.getGuiItem(i) == null) {
                gui.setItem(i, ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                    .name(Component.empty())
                    .asGuiItem())
            }
        }

        debugLogger.debug("Opening GUI", "player" to player.name)
        gui.open(player)
        debugLogger.debugMethodExit("open")
    }

    private fun createWorldOverviewItem(world: PlayerWorld, stats: WorldStatistics): GuiItem {
        val lore = listOf(
            Component.empty(),
            Component.text("World: ", NamedTextColor.GRAY)
                .append(Component.text(world.name, NamedTextColor.WHITE)),
            Component.text("Owner: ", NamedTextColor.GRAY)
                .append(Component.text(world.ownerName, NamedTextColor.WHITE)),
            Component.text("Type: ", NamedTextColor.GRAY)
                .append(Component.text(world.worldType.name, NamedTextColor.WHITE)),
            Component.empty(),
            Component.text("Total Players Tracked: ", NamedTextColor.GRAY)
                .append(Component.text(stats.playerStats.size.toString(), NamedTextColor.YELLOW))
        )

        return ItemBuilder.from(Material.BOOK)
            .name(Component.text("World Statistics Overview", NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true))
            .lore(lore)
            .asGuiItem { it.isCancelled = true }
    }

    private fun createBlocksPlacedItem(stats: WorldStatistics): GuiItem {
        val lore = listOf(
            Component.empty(),
            Component.text("Total blocks placed in this world", NamedTextColor.GRAY),
            Component.empty(),
            Component.text(formatNumber(stats.blocksPlaced), NamedTextColor.GREEN)
                .decoration(TextDecoration.BOLD, true)
        )

        return ItemBuilder.from(Material.GRASS_BLOCK)
            .name(Component.text("Blocks Placed", NamedTextColor.GREEN))
            .lore(lore)
            .asGuiItem { it.isCancelled = true }
    }

    private fun createBlocksBrokenItem(stats: WorldStatistics): GuiItem {
        val lore = listOf(
            Component.empty(),
            Component.text("Total blocks broken in this world", NamedTextColor.GRAY),
            Component.empty(),
            Component.text(formatNumber(stats.blocksBroken), NamedTextColor.YELLOW)
                .decoration(TextDecoration.BOLD, true)
        )

        return ItemBuilder.from(Material.DIAMOND_PICKAXE)
            .name(Component.text("Blocks Broken", NamedTextColor.YELLOW))
            .lore(lore)
            .asGuiItem { it.isCancelled = true }
    }

    private fun createItemsCraftedItem(stats: WorldStatistics): GuiItem {
        val lore = listOf(
            Component.empty(),
            Component.text("Total items crafted in this world", NamedTextColor.GRAY),
            Component.empty(),
            Component.text(formatNumber(stats.itemsCrafted), NamedTextColor.AQUA)
                .decoration(TextDecoration.BOLD, true)
        )

        return ItemBuilder.from(Material.CRAFTING_TABLE)
            .name(Component.text("Items Crafted", NamedTextColor.AQUA))
            .lore(lore)
            .asGuiItem { it.isCancelled = true }
    }

    private fun createMobsKilledItem(stats: WorldStatistics): GuiItem {
        val lore = listOf(
            Component.empty(),
            Component.text("Total hostile mobs killed", NamedTextColor.GRAY),
            Component.empty(),
            Component.text(formatNumber(stats.mobsKilled), NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, true)
        )

        return ItemBuilder.from(Material.IRON_SWORD)
            .name(Component.text("Mobs Killed", NamedTextColor.RED))
            .lore(lore)
            .asGuiItem { it.isCancelled = true }
    }

    private fun createAnimalsKilledItem(stats: WorldStatistics): GuiItem {
        val lore = listOf(
            Component.empty(),
            Component.text("Total passive animals killed", NamedTextColor.GRAY),
            Component.empty(),
            Component.text(formatNumber(stats.animalsKilled), NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.BOLD, true)
        )

        return ItemBuilder.from(Material.COOKED_BEEF)
            .name(Component.text("Animals Killed", NamedTextColor.LIGHT_PURPLE))
            .lore(lore)
            .asGuiItem { it.isCancelled = true }
    }

    private fun createPlayerKillsItem(stats: WorldStatistics): GuiItem {
        val lore = listOf(
            Component.empty(),
            Component.text("Total PvP kills in this world", NamedTextColor.GRAY),
            Component.empty(),
            Component.text(formatNumber(stats.playerKills), NamedTextColor.DARK_RED)
                .decoration(TextDecoration.BOLD, true)
        )

        return ItemBuilder.from(Material.DIAMOND_SWORD)
            .name(Component.text("Player Kills", NamedTextColor.DARK_RED))
            .lore(lore)
            .asGuiItem { it.isCancelled = true }
    }

    private fun createPlayerDeathsItem(stats: WorldStatistics): GuiItem {
        val lore = listOf(
            Component.empty(),
            Component.text("Total player deaths in this world", NamedTextColor.GRAY),
            Component.empty(),
            Component.text(formatNumber(stats.playerDeaths), NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.BOLD, true)
        )

        return ItemBuilder.from(Material.SKELETON_SKULL)
            .name(Component.text("Player Deaths", NamedTextColor.DARK_GRAY))
            .lore(lore)
            .asGuiItem { it.isCancelled = true }
    }

    private fun createTimePlayedItem(stats: WorldStatistics): GuiItem {
        val lore = listOf(
            Component.empty(),
            Component.text("Total time played in this world", NamedTextColor.GRAY),
            Component.empty(),
            Component.text(formatDuration(stats.timePlayed), NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
        )

        return ItemBuilder.from(Material.CLOCK)
            .name(Component.text("Time Played", NamedTextColor.GOLD))
            .lore(lore)
            .asGuiItem { it.isCancelled = true }
    }

    private fun createPersonalStatsItem(player: Player, playerStats: tech.bedson.playerworldmanager.models.PlayerStatistics): GuiItem {
        val lore = listOf(
            Component.empty(),
            Component.text("Your personal statistics:", NamedTextColor.GRAY),
            Component.empty(),
            Component.text("Blocks Placed: ", NamedTextColor.GRAY)
                .append(Component.text(formatNumber(playerStats.blocksPlaced), NamedTextColor.GREEN)),
            Component.text("Blocks Broken: ", NamedTextColor.GRAY)
                .append(Component.text(formatNumber(playerStats.blocksBroken), NamedTextColor.YELLOW)),
            Component.text("Mobs Killed: ", NamedTextColor.GRAY)
                .append(Component.text(formatNumber(playerStats.mobsKilled), NamedTextColor.RED)),
            Component.text("Animals Killed: ", NamedTextColor.GRAY)
                .append(Component.text(formatNumber(playerStats.animalsKilled), NamedTextColor.LIGHT_PURPLE)),
            Component.text("Player Kills: ", NamedTextColor.GRAY)
                .append(Component.text(formatNumber(playerStats.playerKills), NamedTextColor.DARK_RED)),
            Component.text("Deaths: ", NamedTextColor.GRAY)
                .append(Component.text(formatNumber(playerStats.deaths), NamedTextColor.DARK_GRAY)),
            Component.text("Items Crafted: ", NamedTextColor.GRAY)
                .append(Component.text(formatNumber(playerStats.itemsCrafted), NamedTextColor.AQUA)),
            Component.text("Time Played: ", NamedTextColor.GRAY)
                .append(Component.text(formatDuration(playerStats.timePlayed), NamedTextColor.GOLD))
        )

        return ItemBuilder.from(Material.PLAYER_HEAD)
            .name(Component.text("Your Statistics", NamedTextColor.AQUA)
                .decoration(TextDecoration.BOLD, true))
            .lore(lore)
            .asGuiItem { it.isCancelled = true }
    }

    private fun createBackItem(player: Player, world: PlayerWorld): GuiItem {
        return ItemBuilder.from(Material.ARROW)
            .name(Component.text("Back to World Menu", NamedTextColor.YELLOW))
            .asGuiItem { event ->
                event.isCancelled = true
                player.closeInventory()
                player.scheduler.run(plugin, { _ ->
                    WorldManageGui(plugin, worldManager, inviteManager, dataManager).open(player, world)
                }, null)
            }
    }

    private fun createRefreshItem(player: Player, world: PlayerWorld): GuiItem {
        return ItemBuilder.from(Material.SUNFLOWER)
            .name(Component.text("Refresh Statistics", NamedTextColor.GREEN))
            .lore(listOf(
                Component.empty(),
                Component.text("Click to refresh statistics", NamedTextColor.GRAY)
            ))
            .asGuiItem { event ->
                event.isCancelled = true
                player.closeInventory()
                player.scheduler.run(plugin, { _ ->
                    open(player, world)
                }, null)
            }
    }

    private fun formatNumber(number: Long): String {
        return String.format("%,d", number)
    }

    private fun formatDuration(millis: Long): String {
        if (millis == 0L) return "0 minutes"

        val days = TimeUnit.MILLISECONDS.toDays(millis)
        val hours = TimeUnit.MILLISECONDS.toHours(millis) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60

        return when {
            days > 0 -> "$days days, $hours hours"
            hours > 0 -> "$hours hours, $minutes minutes"
            else -> "$minutes minutes"
        }
    }
}
