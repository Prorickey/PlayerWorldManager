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
import tech.bedson.playerworldmanager.managers.WorldManager
import tech.bedson.playerworldmanager.models.PlayerWorld
import tech.bedson.playerworldmanager.utils.DebugLogger

/**
 * World border settings GUI for world owners.
 * Provides a visual interface to manage all world border settings.
 */
class WorldBorderGui(
    private val plugin: JavaPlugin,
    private val worldManager: WorldManager,
    private val inviteManager: InviteManager,
    private val dataManager: DataManager
) {
    private val debugLogger = DebugLogger(plugin, "WorldBorderGui")

    fun open(player: Player, world: PlayerWorld) {
        debugLogger.debugMethodEntry("open", "player" to player.name, "worldName" to world.name)

        val gui = Gui.gui()
            .title(Component.text("World Border: ${world.name}", NamedTextColor.AQUA))
            .rows(5)
            .disableAllInteractions()
            .create()

        val settings = world.worldBorder

        // Row 1: Size controls
        gui.setItem(10, createSizeDecreaseItem(player, world, 1000.0))
        gui.setItem(11, createSizeDecreaseItem(player, world, 100.0))
        gui.setItem(12, createSizeDecreaseItem(player, world, 10.0))
        gui.setItem(13, createSizeDisplayItem(world))
        gui.setItem(14, createSizeIncreaseItem(player, world, 10.0))
        gui.setItem(15, createSizeIncreaseItem(player, world, 100.0))
        gui.setItem(16, createSizeIncreaseItem(player, world, 1000.0))

        // Row 2: Center and common presets
        gui.setItem(19, createCenterHereItem(player, world))
        gui.setItem(20, createCenterResetItem(player, world))
        gui.setItem(22, createPresetItem(player, world, 100.0, "Small"))
        gui.setItem(23, createPresetItem(player, world, 500.0, "Medium"))
        gui.setItem(24, createPresetItem(player, world, 2000.0, "Large"))
        gui.setItem(25, createPresetItem(player, world, 59999968.0, "Unlimited"))

        // Row 3: Damage and warning settings
        gui.setItem(28, createDamageAmountItem(player, world))
        gui.setItem(29, createDamageBufferItem(player, world))
        gui.setItem(31, createWarningDistanceItem(player, world))
        gui.setItem(32, createWarningTimeItem(player, world))
        gui.setItem(34, createInfoItem(world))

        // Row 4: Navigation
        gui.setItem(36, createBackItem(player, world))

        // Fill empty slots with glass panes
        for (i in 0 until 45) {
            if (gui.getGuiItem(i) == null) {
                gui.setItem(i, ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                    .name(Component.empty())
                    .asGuiItem())
            }
        }

        gui.open(player)
        debugLogger.debugMethodExit("open")
    }

    private fun applyWorldBorder(world: PlayerWorld) {
        val bukkitWorld = worldManager.getBukkitWorld(world) ?: return
        val border = bukkitWorld.worldBorder
        val settings = world.worldBorder

        Bukkit.getGlobalRegionScheduler().run(plugin) { _ ->
            border.center = org.bukkit.Location(bukkitWorld, settings.centerX, 0.0, settings.centerZ)
            border.size = settings.size
            border.damageAmount = settings.damageAmount
            border.damageBuffer = settings.damageBuffer
            border.warningDistance = settings.warningDistance
            border.warningTime = settings.warningTime
        }
    }

    private fun createSizeDisplayItem(world: PlayerWorld): GuiItem {
        val settings = world.worldBorder
        return ItemBuilder.from(Material.STRUCTURE_VOID)
            .name(Component.text("Border Size", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false))
            .lore(listOf(
                Component.text("Current: ${settings.size.toLong()} blocks", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("This is the diameter of the border", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
            ))
            .asGuiItem { event -> event.isCancelled = true }
    }

    private fun createSizeIncreaseItem(player: Player, world: PlayerWorld, amount: Double): GuiItem {
        val material = when {
            amount >= 1000 -> Material.LIME_STAINED_GLASS_PANE
            amount >= 100 -> Material.GREEN_STAINED_GLASS_PANE
            else -> Material.GREEN_CONCRETE
        }

        return ItemBuilder.from(material)
            .name(Component.text("+${amount.toLong()}", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false))
            .lore(listOf(
                Component.text("Click to increase border", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("by ${amount.toLong()} blocks", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
            ))
            .asGuiItem { event ->
                event.isCancelled = true
                val newSize = (world.worldBorder.size + amount).coerceIn(1.0, 59999968.0)
                world.worldBorder.size = newSize
                dataManager.saveWorld(world)
                applyWorldBorder(world)
                player.sendMessage(
                    Component.text("Border size set to ", NamedTextColor.GREEN)
                        .append(Component.text("${newSize.toLong()}", NamedTextColor.YELLOW))
                        .append(Component.text(" blocks", NamedTextColor.GREEN))
                )
                open(player, world)
            }
    }

    private fun createSizeDecreaseItem(player: Player, world: PlayerWorld, amount: Double): GuiItem {
        val material = when {
            amount >= 1000 -> Material.RED_STAINED_GLASS_PANE
            amount >= 100 -> Material.ORANGE_STAINED_GLASS_PANE
            else -> Material.RED_CONCRETE
        }

        return ItemBuilder.from(material)
            .name(Component.text("-${amount.toLong()}", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false))
            .lore(listOf(
                Component.text("Click to decrease border", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("by ${amount.toLong()} blocks", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
            ))
            .asGuiItem { event ->
                event.isCancelled = true
                val newSize = (world.worldBorder.size - amount).coerceIn(1.0, 59999968.0)
                world.worldBorder.size = newSize
                dataManager.saveWorld(world)
                applyWorldBorder(world)
                player.sendMessage(
                    Component.text("Border size set to ", NamedTextColor.GREEN)
                        .append(Component.text("${newSize.toLong()}", NamedTextColor.YELLOW))
                        .append(Component.text(" blocks", NamedTextColor.GREEN))
                )
                open(player, world)
            }
    }

    private fun createCenterHereItem(player: Player, world: PlayerWorld): GuiItem {
        return ItemBuilder.from(Material.COMPASS)
            .name(Component.text("Center at Your Location", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false))
            .lore(listOf(
                Component.text("Click to set border center", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("to your current position", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
            ))
            .asGuiItem { event ->
                event.isCancelled = true
                val loc = player.location
                world.worldBorder.centerX = loc.x
                world.worldBorder.centerZ = loc.z
                dataManager.saveWorld(world)
                applyWorldBorder(world)
                player.sendMessage(
                    Component.text("Border center set to ", NamedTextColor.GREEN)
                        .append(Component.text("${loc.blockX}, ${loc.blockZ}", NamedTextColor.YELLOW))
                )
                open(player, world)
            }
    }

    private fun createCenterResetItem(player: Player, world: PlayerWorld): GuiItem {
        return ItemBuilder.from(Material.RECOVERY_COMPASS)
            .name(Component.text("Reset Center to 0, 0", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))
            .lore(listOf(
                Component.text("Click to reset border center", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("to coordinates 0, 0", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
            ))
            .asGuiItem { event ->
                event.isCancelled = true
                world.worldBorder.centerX = 0.0
                world.worldBorder.centerZ = 0.0
                dataManager.saveWorld(world)
                applyWorldBorder(world)
                player.sendMessage(
                    Component.text("Border center reset to ", NamedTextColor.GREEN)
                        .append(Component.text("0, 0", NamedTextColor.YELLOW))
                )
                open(player, world)
            }
    }

    private fun createPresetItem(player: Player, world: PlayerWorld, size: Double, name: String): GuiItem {
        val material = when (name) {
            "Small" -> Material.LEATHER_CHESTPLATE
            "Medium" -> Material.IRON_CHESTPLATE
            "Large" -> Material.DIAMOND_CHESTPLATE
            "Unlimited" -> Material.NETHERITE_CHESTPLATE
            else -> Material.CHAINMAIL_CHESTPLATE
        }

        val sizeDisplay = if (size >= 59999968) "Unlimited" else "${size.toLong()}"

        return ItemBuilder.from(material)
            .name(Component.text("$name Preset", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false))
            .lore(listOf(
                Component.text("Size: $sizeDisplay blocks", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Click to apply this preset", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
            ))
            .asGuiItem { event ->
                event.isCancelled = true
                world.worldBorder.size = size
                dataManager.saveWorld(world)
                applyWorldBorder(world)
                player.sendMessage(
                    Component.text("Applied $name preset: ", NamedTextColor.GREEN)
                        .append(Component.text("$sizeDisplay blocks", NamedTextColor.YELLOW))
                )
                open(player, world)
            }
    }

    private fun createDamageAmountItem(player: Player, world: PlayerWorld): GuiItem {
        val settings = world.worldBorder
        return ItemBuilder.from(Material.IRON_SWORD)
            .name(Component.text("Damage Amount", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false))
            .lore(listOf(
                Component.text("Current: ${settings.damageAmount} per block", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Left-click: +0.1", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click: -0.1", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.text("Shift-click: Reset to 0.2", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
            ))
            .asGuiItem { event ->
                event.isCancelled = true
                val newValue = when {
                    event.isShiftClick -> 0.2
                    event.isLeftClick -> (settings.damageAmount + 0.1).coerceIn(0.0, 10.0)
                    event.isRightClick -> (settings.damageAmount - 0.1).coerceIn(0.0, 10.0)
                    else -> settings.damageAmount
                }
                world.worldBorder.damageAmount = newValue
                dataManager.saveWorld(world)
                applyWorldBorder(world)
                player.sendMessage(
                    Component.text("Damage amount set to ", NamedTextColor.GREEN)
                        .append(Component.text("${"%.1f".format(newValue)}", NamedTextColor.YELLOW))
                        .append(Component.text(" per block", NamedTextColor.GREEN))
                )
                open(player, world)
            }
    }

    private fun createDamageBufferItem(player: Player, world: PlayerWorld): GuiItem {
        val settings = world.worldBorder
        return ItemBuilder.from(Material.SHIELD)
            .name(Component.text("Damage Buffer", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false))
            .lore(listOf(
                Component.text("Current: ${settings.damageBuffer} blocks", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Distance past border before", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("damage starts", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Left-click: +1", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click: -1", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.text("Shift-click: Reset to 5", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
            ))
            .asGuiItem { event ->
                event.isCancelled = true
                val newValue = when {
                    event.isShiftClick -> 5.0
                    event.isLeftClick -> (settings.damageBuffer + 1).coerceIn(0.0, 100.0)
                    event.isRightClick -> (settings.damageBuffer - 1).coerceIn(0.0, 100.0)
                    else -> settings.damageBuffer
                }
                world.worldBorder.damageBuffer = newValue
                dataManager.saveWorld(world)
                applyWorldBorder(world)
                player.sendMessage(
                    Component.text("Damage buffer set to ", NamedTextColor.GREEN)
                        .append(Component.text("${newValue.toLong()}", NamedTextColor.YELLOW))
                        .append(Component.text(" blocks", NamedTextColor.GREEN))
                )
                open(player, world)
            }
    }

    private fun createWarningDistanceItem(player: Player, world: PlayerWorld): GuiItem {
        val settings = world.worldBorder
        return ItemBuilder.from(Material.BELL)
            .name(Component.text("Warning Distance", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))
            .lore(listOf(
                Component.text("Current: ${settings.warningDistance} blocks", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Distance from border when", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("screen turns red", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Left-click: +1", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click: -1", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.text("Shift-click: Reset to 5", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
            ))
            .asGuiItem { event ->
                event.isCancelled = true
                val newValue = when {
                    event.isShiftClick -> 5
                    event.isLeftClick -> (settings.warningDistance + 1).coerceIn(0, 100)
                    event.isRightClick -> (settings.warningDistance - 1).coerceIn(0, 100)
                    else -> settings.warningDistance
                }
                world.worldBorder.warningDistance = newValue
                dataManager.saveWorld(world)
                applyWorldBorder(world)
                player.sendMessage(
                    Component.text("Warning distance set to ", NamedTextColor.GREEN)
                        .append(Component.text("$newValue", NamedTextColor.YELLOW))
                        .append(Component.text(" blocks", NamedTextColor.GREEN))
                )
                open(player, world)
            }
    }

    private fun createWarningTimeItem(player: Player, world: PlayerWorld): GuiItem {
        val settings = world.worldBorder
        return ItemBuilder.from(Material.CLOCK)
            .name(Component.text("Warning Time", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false))
            .lore(listOf(
                Component.text("Current: ${settings.warningTime} seconds", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Time before border shrink", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("warning appears", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Left-click: +5", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click: -5", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.text("Shift-click: Reset to 15", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
            ))
            .asGuiItem { event ->
                event.isCancelled = true
                val newValue = when {
                    event.isShiftClick -> 15
                    event.isLeftClick -> (settings.warningTime + 5).coerceIn(0, 300)
                    event.isRightClick -> (settings.warningTime - 5).coerceIn(0, 300)
                    else -> settings.warningTime
                }
                world.worldBorder.warningTime = newValue
                dataManager.saveWorld(world)
                applyWorldBorder(world)
                player.sendMessage(
                    Component.text("Warning time set to ", NamedTextColor.GREEN)
                        .append(Component.text("$newValue", NamedTextColor.YELLOW))
                        .append(Component.text(" seconds", NamedTextColor.GREEN))
                )
                open(player, world)
            }
    }

    private fun createInfoItem(world: PlayerWorld): GuiItem {
        val settings = world.worldBorder
        return ItemBuilder.from(Material.BOOK)
            .name(Component.text("Current Settings", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
            .lore(listOf(
                Component.empty(),
                Component.text("Size: ${settings.size.toLong()} blocks", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                Component.text("Center: ${settings.centerX.toLong()}, ${settings.centerZ.toLong()}", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Damage: ${settings.damageAmount}/block", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.text("Buffer: ${settings.damageBuffer.toLong()} blocks", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Warning Distance: ${settings.warningDistance} blocks", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text("Warning Time: ${settings.warningTime} seconds", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false)
            ))
            .asGuiItem { event -> event.isCancelled = true }
    }

    private fun createBackItem(player: Player, world: PlayerWorld): GuiItem {
        return ItemBuilder.from(Material.ARROW)
            .name(Component.text("Back to World Settings", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))
            .asGuiItem { event ->
                event.isCancelled = true
                player.closeInventory()
                player.scheduler.run(plugin, { _ ->
                    WorldManageGui(plugin, worldManager, inviteManager, dataManager).open(player, world)
                }, null)
            }
    }
}
