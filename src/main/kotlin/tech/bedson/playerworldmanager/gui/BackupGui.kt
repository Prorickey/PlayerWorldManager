package tech.bedson.playerworldmanager.gui

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import dev.triumphteam.gui.guis.PaginatedGui
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.PlayerWorldManager
import tech.bedson.playerworldmanager.managers.BackupManager
import tech.bedson.playerworldmanager.managers.DataManager
import tech.bedson.playerworldmanager.managers.WorldManager
import tech.bedson.playerworldmanager.models.PlayerWorld
import tech.bedson.playerworldmanager.models.WorldBackup
import tech.bedson.playerworldmanager.utils.DebugLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Backup management GUI for world owners.
 * Provides options to create, view, restore, and delete backups.
 */
class BackupGui(
    private val plugin: JavaPlugin,
    private val worldManager: WorldManager,
    private val backupManager: BackupManager,
    private val dataManager: DataManager
) {
    private val debugLogger = DebugLogger(plugin, "BackupGui")

    companion object {
        private val pendingRestores = ConcurrentHashMap<UUID, PendingRestore>()
        private val pendingDeletes = ConcurrentHashMap<UUID, PendingDelete>()

        data class PendingRestore(
            val backupId: UUID,
            val timestamp: Long
        )

        data class PendingDelete(
            val backupId: UUID,
            val timestamp: Long
        )
    }

    fun open(player: Player, world: PlayerWorld) {
        debugLogger.debugMethodEntry("open", "player" to player.name, "worldName" to world.name)

        val backups = backupManager.getBackupsForWorld(world.id)
        debugLogger.debug("Retrieved backups", "count" to backups.size)

        if (backups.size > 18) {
            openPaginatedGui(player, world, backups)
        } else {
            openSimpleGui(player, world, backups)
        }

        debugLogger.debugMethodExit("open")
    }

    private fun openSimpleGui(player: Player, world: PlayerWorld, backups: List<WorldBackup>) {
        debugLogger.debug("Opening simple GUI", "backupCount" to backups.size)

        val gui = Gui.gui()
            .title(Component.text("Backups: ${world.name}", NamedTextColor.GOLD))
            .rows(4)
            .disableAllInteractions()
            .create()

        // Row 1: Create backup and schedule buttons
        gui.setItem(0, createNewBackupItem(player, world))
        gui.setItem(1, createScheduleItem(player, world))
        gui.setItem(8, createBackItem(player, world))

        // Rows 2-3: Backup items
        backups.take(18).forEachIndexed { index, backup ->
            gui.setItem(9 + index, createBackupItem(player, world, backup))
        }

        // Fill empty slots
        for (i in 0 until 36) {
            if (gui.getGuiItem(i) == null) {
                gui.setItem(i, ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                    .name(Component.empty())
                    .asGuiItem())
            }
        }

        gui.open(player)
    }

    private fun openPaginatedGui(player: Player, world: PlayerWorld, backups: List<WorldBackup>) {
        debugLogger.debug("Opening paginated GUI", "backupCount" to backups.size)

        val gui = Gui.paginated()
            .title(Component.text("Backups: ${world.name}", NamedTextColor.GOLD))
            .rows(5)
            .pageSize(27)
            .disableAllInteractions()
            .create()

        // Navigation row (bottom)
        gui.setItem(36, createBackItem(player, world))
        gui.setItem(39, createPreviousPageItem(gui))
        gui.setItem(40, createNewBackupItem(player, world))
        gui.setItem(41, createScheduleItem(player, world))
        gui.setItem(42, createNextPageItem(gui))

        // Fill navigation row
        listOf(37, 38, 43, 44).forEach { slot ->
            gui.setItem(slot, ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .asGuiItem())
        }

        // Add backup items
        backups.forEach { backup ->
            gui.addItem(createBackupItem(player, world, backup))
        }

        gui.open(player)
    }

    private fun createBackupItem(player: Player, world: PlayerWorld, backup: WorldBackup): GuiItem {
        debugLogger.debug("Creating backup item", "backupId" to backup.id)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm")
        val date = dateFormat.format(Date(backup.createdAt))
        val currentTime = System.currentTimeMillis()

        // Check for pending restore/delete confirmations
        val pendingRestore = pendingRestores[player.uniqueId]
        val hasPendingRestore = pendingRestore != null &&
                pendingRestore.backupId == backup.id &&
                (currentTime - pendingRestore.timestamp) <= 5000

        val pendingDelete = pendingDeletes[player.uniqueId]
        val hasPendingDelete = pendingDelete != null &&
                pendingDelete.backupId == backup.id &&
                (currentTime - pendingDelete.timestamp) <= 5000

        val material = when {
            hasPendingRestore -> Material.LIME_STAINED_GLASS
            hasPendingDelete -> Material.RED_STAINED_GLASS
            backup.isAutomatic -> Material.CLOCK
            else -> Material.CHEST
        }

        val lore = buildList<Component> {
            add(Component.text("Created: $date", NamedTextColor.GRAY))
            add(Component.text("Size: ${backup.getHumanReadableSize()}", NamedTextColor.GRAY))
            if (backup.isAutomatic) {
                add(Component.text("Type: Automatic", NamedTextColor.YELLOW))
            } else {
                add(Component.text("Type: Manual", NamedTextColor.GREEN))
            }
            if (backup.description != null) {
                add(Component.empty())
                add(Component.text(backup.description, NamedTextColor.GRAY))
            }
            add(Component.empty())
            add(Component.text("ID: ${backup.id.toString().take(8)}", NamedTextColor.DARK_GRAY))
            add(Component.empty())
            when {
                hasPendingRestore -> {
                    add(Component.text("Click again to CONFIRM RESTORE", NamedTextColor.GREEN))
                }
                hasPendingDelete -> {
                    add(Component.text("Click again to CONFIRM DELETE", NamedTextColor.RED))
                }
                else -> {
                    add(Component.text("Left-click to restore", NamedTextColor.YELLOW))
                    add(Component.text("Right-click to delete", NamedTextColor.RED))
                }
            }
        }

        val name = when {
            hasPendingRestore -> "Confirm Restore?"
            hasPendingDelete -> "Confirm Delete?"
            else -> "Backup from $date"
        }

        return ItemBuilder.from(material)
            .name(Component.text(name, if (hasPendingDelete) NamedTextColor.RED else NamedTextColor.GOLD))
            .lore(lore)
            .asGuiItem { event ->
                event.isCancelled = true

                when {
                    hasPendingRestore -> {
                        // Confirm restore
                        pendingRestores.remove(player.uniqueId)
                        player.closeInventory()
                        player.sendMessage(
                            Component.text("Restoring backup...", NamedTextColor.YELLOW)
                        )
                        backupManager.restoreBackup(backup).thenAccept { result ->
                            result.onSuccess {
                                player.scheduler.run(plugin, { _ ->
                                    player.sendMessage(
                                        Component.text("World restored successfully!", NamedTextColor.GREEN)
                                    )
                                }, null)
                            }.onFailure { error ->
                                player.scheduler.run(plugin, { _ ->
                                    player.sendMessage(
                                        Component.text("Failed to restore: ${error.message}", NamedTextColor.RED)
                                    )
                                }, null)
                            }
                        }
                    }
                    hasPendingDelete -> {
                        // Confirm delete
                        pendingDeletes.remove(player.uniqueId)
                        player.sendMessage(
                            Component.text("Deleting backup...", NamedTextColor.YELLOW)
                        )
                        backupManager.deleteBackup(backup).thenAccept { result ->
                            result.onSuccess {
                                player.scheduler.run(plugin, { _ ->
                                    player.sendMessage(
                                        Component.text("Backup deleted!", NamedTextColor.GREEN)
                                    )
                                    open(player, world) // Refresh
                                }, null)
                            }.onFailure { error ->
                                player.scheduler.run(plugin, { _ ->
                                    player.sendMessage(
                                        Component.text("Failed to delete: ${error.message}", NamedTextColor.RED)
                                    )
                                }, null)
                            }
                        }
                    }
                    event.isLeftClick -> {
                        // First click for restore
                        pendingRestores[player.uniqueId] = PendingRestore(backup.id, currentTime)
                        pendingDeletes.remove(player.uniqueId)
                        player.sendMessage(
                            Component.text("Click again to confirm restore", NamedTextColor.YELLOW)
                        )
                        open(player, world) // Refresh to show confirmation
                    }
                    event.isRightClick -> {
                        // First click for delete
                        pendingDeletes[player.uniqueId] = PendingDelete(backup.id, currentTime)
                        pendingRestores.remove(player.uniqueId)
                        player.sendMessage(
                            Component.text("Click again to confirm deletion", NamedTextColor.YELLOW)
                        )
                        open(player, world) // Refresh to show confirmation
                    }
                }
            }
    }

    private fun createNewBackupItem(player: Player, world: PlayerWorld): GuiItem {
        val maxBackups = backupManager.getMaxBackupsPerWorld()
        val currentBackups = backupManager.getBackupsForWorld(world.id).size

        return ItemBuilder.from(Material.EMERALD)
            .name(Component.text("Create New Backup", NamedTextColor.GREEN))
            .lore(listOf(
                Component.text("Create a manual backup of this world", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Current backups: $currentBackups / $maxBackups", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Click to create backup", NamedTextColor.YELLOW)
            ))
            .asGuiItem { event ->
                event.isCancelled = true
                player.closeInventory()
                player.sendMessage(
                    Component.text("Creating backup for ", NamedTextColor.YELLOW)
                        .append(Component.text(world.name, NamedTextColor.GOLD))
                        .append(Component.text("...", NamedTextColor.YELLOW))
                )

                backupManager.createBackup(world, null, isAutomatic = false).thenAccept { result ->
                    result.onSuccess { backup ->
                        player.scheduler.run(plugin, { _ ->
                            player.sendMessage(
                                Component.text("Backup created! ", NamedTextColor.GREEN)
                                    .append(Component.text("Size: ${backup.getHumanReadableSize()}", NamedTextColor.AQUA))
                            )
                        }, null)
                    }.onFailure { error ->
                        player.scheduler.run(plugin, { _ ->
                            player.sendMessage(
                                Component.text("Failed to create backup: ${error.message}", NamedTextColor.RED)
                            )
                        }, null)
                    }
                }
            }
    }

    private fun createScheduleItem(player: Player, world: PlayerWorld): GuiItem {
        val schedule = backupManager.getBackupSchedule(world.id)
        val isEnabled = schedule?.enabled == true

        val material = if (isEnabled) Material.LIME_DYE else Material.GRAY_DYE
        val statusText = if (isEnabled) "Enabled" else "Disabled"
        val statusColor = if (isEnabled) NamedTextColor.GREEN else NamedTextColor.RED

        val lore = buildList<Component> {
            add(Component.text("Status: ", NamedTextColor.GRAY)
                .append(Component.text(statusText, statusColor)))
            if (isEnabled && schedule != null) {
                add(Component.text("Interval: ${schedule.intervalMinutes} minutes", NamedTextColor.GRAY))
                if (schedule.lastBackupTime > 0) {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm")
                    val lastBackup = dateFormat.format(Date(schedule.lastBackupTime))
                    add(Component.text("Last backup: $lastBackup", NamedTextColor.GRAY))
                }
            }
            add(Component.empty())
            add(Component.text("Click to ${if (isEnabled) "disable" else "enable"}", NamedTextColor.YELLOW))
        }

        return ItemBuilder.from(material)
            .name(Component.text("Automatic Backups", NamedTextColor.GOLD))
            .lore(lore)
            .asGuiItem { event ->
                event.isCancelled = true

                if (isEnabled) {
                    backupManager.disableBackupSchedule(world.id)
                    player.sendMessage(
                        Component.text("Automatic backups disabled", NamedTextColor.YELLOW)
                    )
                } else {
                    backupManager.setBackupSchedule(world.id, enabled = true)
                    player.sendMessage(
                        Component.text("Automatic backups enabled", NamedTextColor.GREEN)
                    )
                }
                open(player, world) // Refresh
            }
    }

    private fun createBackItem(player: Player, world: PlayerWorld): GuiItem {
        return ItemBuilder.from(Material.ARROW)
            .name(Component.text("Back to World Settings", NamedTextColor.YELLOW))
            .asGuiItem { event ->
                event.isCancelled = true
                // Clear pending confirmations
                pendingRestores.remove(player.uniqueId)
                pendingDeletes.remove(player.uniqueId)
                player.closeInventory()
                player.scheduler.run(plugin, { _ ->
                    val pwm = plugin as? PlayerWorldManager
                    val inviteManager = pwm?.getInviteManager()
                    val statsManager = pwm?.getStatsManager()
                    WorldManageGui(plugin, worldManager, inviteManager, dataManager, statsManager, backupManager)
                        .open(player, world)
                }, null)
            }
    }

    private fun createPreviousPageItem(gui: PaginatedGui): GuiItem {
        return ItemBuilder.from(Material.SPECTRAL_ARROW)
            .name(Component.text("Previous Page", NamedTextColor.YELLOW))
            .asGuiItem { event ->
                event.isCancelled = true
                gui.previous()
            }
    }

    private fun createNextPageItem(gui: PaginatedGui): GuiItem {
        return ItemBuilder.from(Material.SPECTRAL_ARROW)
            .name(Component.text("Next Page", NamedTextColor.YELLOW))
            .asGuiItem { event ->
                event.isCancelled = true
                gui.next()
            }
    }
}
