package tech.bedson.playerworldmanager.listeners

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.managers.WorldManager
import tech.bedson.playerworldmanager.managers.WorldUnloadManager
import tech.bedson.playerworldmanager.utils.DebugLogger

/**
 * Listens for player world changes and notifies the WorldUnloadManager
 * to schedule or cancel world unloads appropriately.
 */
class WorldChangeListener(
    private val plugin: JavaPlugin,
    private val worldManager: WorldManager,
    private val worldUnloadManager: WorldUnloadManager
) : Listener {

    private val debugLogger = DebugLogger(plugin, "WorldChangeListener")

    /**
     * Handle player changing worlds - notify unload manager about both worlds.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        val player = event.player
        val fromWorld = event.from
        val toWorld = player.world

        debugLogger.debugMethodEntry("onPlayerChangedWorld",
            "player" to player.name,
            "fromWorld" to fromWorld.name,
            "toWorld" to toWorld.name
        )

        // Check if the 'from' world is a plugin world and notify about player leaving
        val fromPlayerWorld = worldManager.getPlayerWorldFromBukkitWorld(fromWorld)
        if (fromPlayerWorld != null) {
            debugLogger.debug("Player left plugin world",
                "player" to player.name,
                "worldName" to fromPlayerWorld.name,
                "worldId" to fromPlayerWorld.id
            )
            worldUnloadManager.onPlayerLeaveWorld(fromPlayerWorld)
        }

        // Check if the 'to' world is a plugin world and notify about player entering
        val toPlayerWorld = worldManager.getPlayerWorldFromBukkitWorld(toWorld)
        if (toPlayerWorld != null) {
            debugLogger.debug("Player entered plugin world",
                "player" to player.name,
                "worldName" to toPlayerWorld.name,
                "worldId" to toPlayerWorld.id
            )
            worldUnloadManager.onPlayerEnterWorld(toPlayerWorld)
        }

        debugLogger.debugMethodExit("onPlayerChangedWorld")
    }

    /**
     * Handle player joining - if they join directly into a plugin world,
     * cancel any scheduled unload.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val world = player.world

        debugLogger.debugMethodEntry("onPlayerJoin",
            "player" to player.name,
            "world" to world.name
        )

        // Check if the world is a plugin world
        val playerWorld = worldManager.getPlayerWorldFromBukkitWorld(world)
        if (playerWorld != null) {
            debugLogger.debug("Player joined in plugin world",
                "player" to player.name,
                "worldName" to playerWorld.name,
                "worldId" to playerWorld.id
            )
            worldUnloadManager.onPlayerEnterWorld(playerWorld)
        }

        debugLogger.debugMethodExit("onPlayerJoin")
    }

    /**
     * Handle player quitting - check if they were the last player in a plugin world.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val world = player.world

        debugLogger.debugMethodEntry("onPlayerQuit",
            "player" to player.name,
            "world" to world.name
        )

        // Check if the world is a plugin world
        val playerWorld = worldManager.getPlayerWorldFromBukkitWorld(world)
        if (playerWorld != null) {
            debugLogger.debug("Player quit from plugin world",
                "player" to player.name,
                "worldName" to playerWorld.name,
                "worldId" to playerWorld.id
            )
            // Pass the leaving player so they're excluded from the count
            // (they're still counted as in the world at this point)
            worldUnloadManager.onPlayerLeaveWorld(playerWorld, player)
        }

        debugLogger.debugMethodExit("onPlayerQuit")
    }
}
