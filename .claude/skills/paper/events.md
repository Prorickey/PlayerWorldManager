# Event Handling in Paper

Paper uses Bukkit's event system with some enhancements. Events allow plugins to react to game actions like player joins, chat messages, and block breaks.

## Basic Event Listener

```kotlin
class PlayerListener(private val plugin: JavaPlugin) : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        player.sendMessage(Component.text("Welcome to the server!", NamedTextColor.GREEN))
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        // Cleanup player data
    }
}
```

## Registering Listeners

```kotlin
class MyPlugin : JavaPlugin() {
    override fun onEnable() {
        server.pluginManager.registerEvents(PlayerListener(this), this)
        server.pluginManager.registerEvents(ChatListener(this), this)
    }
}
```

## Event Priority

Events can specify priority to control execution order:

```kotlin
@EventHandler(priority = EventPriority.HIGHEST)
fun onChat(event: AsyncChatEvent) {
    // Runs after LOWEST, LOW, NORMAL, HIGH handlers
}
```

| Priority | Order | Use Case |
|----------|-------|----------|
| `LOWEST` | First | Logging, monitoring |
| `LOW` | Second | Early modifications |
| `NORMAL` | Middle | Standard handlers (default) |
| `HIGH` | Fourth | Late modifications |
| `HIGHEST` | Fifth | Final modifications |
| `MONITOR` | Last | Read-only observation |

**Warning**: `MONITOR` priority should never modify events; it's for observation only.

## Cancellable Events

Many events can be cancelled to prevent the action:

```kotlin
@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
fun onBlockBreak(event: BlockBreakEvent) {
    if (!canBreakBlock(event.player, event.block)) {
        event.isCancelled = true
        event.player.sendMessage(Component.text("You cannot break this block!", NamedTextColor.RED))
    }
}
```

The `ignoreCancelled = true` parameter skips your handler if the event was already cancelled by another plugin.

## Async Events

Some events run asynchronously (not on the main thread). Handle them carefully:

```kotlin
@EventHandler
fun onAsyncChat(event: AsyncChatEvent) {
    // This runs on an async thread!
    // Don't access Bukkit API directly here

    val player = event.player
    val message = event.message()

    // For Folia compatibility, use scheduler
    player.scheduler.run(plugin, { task ->
        // Safe to access Bukkit API here
        player.world.strikeLightning(player.location)
    }, null)
}
```

### Checking Thread Safety

```kotlin
@EventHandler
fun onSomeEvent(event: SomeEvent) {
    if (Bukkit.isPrimaryThread()) {
        // Safe to use Bukkit API
    } else {
        // Must schedule to main/region thread
    }
}
```

## Common Events

### Player Events

```kotlin
// Player joins
@EventHandler
fun onJoin(event: PlayerJoinEvent) {
    event.joinMessage(Component.text("${event.player.name} joined!"))
}

// Player quits
@EventHandler
fun onQuit(event: PlayerQuitEvent) {
    event.quitMessage(null) // Hide quit message
}

// Player moves
@EventHandler
fun onMove(event: PlayerMoveEvent) {
    // Check if player actually moved blocks (not just head rotation)
    if (event.hasChangedBlock()) {
        // Handle movement
    }
}

// Player teleports
@EventHandler
fun onTeleport(event: PlayerTeleportEvent) {
    val cause = event.cause // COMMAND, PLUGIN, NETHER_PORTAL, etc.
}

// Player changes world
@EventHandler
fun onWorldChange(event: PlayerChangedWorldEvent) {
    val from = event.from
    val to = event.player.world
}

// Player respawns
@EventHandler
fun onRespawn(event: PlayerRespawnEvent) {
    event.respawnLocation = customSpawnLocation
}
```

### Chat Events

```kotlin
// Modern async chat (Paper)
@EventHandler
fun onChat(event: AsyncChatEvent) {
    val sender = event.player
    val message = event.message()

    // Modify viewers
    event.viewers().removeIf { it is Player && isIgnoring(it, sender) }

    // Modify message format
    event.renderer { source, displayName, msg, viewer ->
        Component.text()
            .append(Component.text("[Server] ", NamedTextColor.GRAY))
            .append(displayName)
            .append(Component.text(": "))
            .append(msg)
            .build()
    }
}
```

### Block Events

```kotlin
@EventHandler
fun onBlockBreak(event: BlockBreakEvent) {
    val player = event.player
    val block = event.block

    if (block.type == Material.DIAMOND_ORE) {
        // Custom drop handling
        event.isDropItems = false
        block.world.dropItemNaturally(block.location, ItemStack(Material.DIAMOND, 2))
    }
}

@EventHandler
fun onBlockPlace(event: BlockPlaceEvent) {
    val block = event.block
    val against = event.blockAgainst
}
```

### Entity Events

```kotlin
@EventHandler
fun onEntityDamage(event: EntityDamageEvent) {
    if (event.entity is Player) {
        val player = event.entity as Player
        if (event.cause == EntityDamageEvent.DamageCause.FALL) {
            event.damage = event.damage * 0.5 // Half fall damage
        }
    }
}

@EventHandler
fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
    val damager = event.damager
    val victim = event.entity

    if (damager is Player && victim is Player) {
        // PvP logic
    }
}
```

### World Events

```kotlin
@EventHandler
fun onWorldLoad(event: WorldLoadEvent) {
    val world = event.world
    logger.info("World loaded: ${world.name}")
}

@EventHandler
fun onWorldUnload(event: WorldUnloadEvent) {
    // Cleanup world-specific data
}

@EventHandler
fun onChunkLoad(event: ChunkLoadEvent) {
    if (event.isNewChunk) {
        // Handle newly generated chunk
    }
}
```

### Inventory Events

```kotlin
@EventHandler
fun onInventoryClick(event: InventoryClickEvent) {
    if (event.view.title() == Component.text("My GUI")) {
        event.isCancelled = true // Prevent item movement

        val clickedSlot = event.slot
        val item = event.currentItem

        // Handle click
    }
}

@EventHandler
fun onInventoryClose(event: InventoryCloseEvent) {
    // Cleanup GUI state
}
```

## Custom Events

### Defining a Custom Event

```kotlin
class WorldCreatedEvent(
    val world: World,
    val creator: Player
) : Event(), Cancellable {

    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }

    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}
```

### Firing Custom Events

```kotlin
val event = WorldCreatedEvent(world, player)
Bukkit.getPluginManager().callEvent(event)

if (!event.isCancelled) {
    // Proceed with world creation
}
```

### Listening to Custom Events

```kotlin
@EventHandler
fun onWorldCreated(event: WorldCreatedEvent) {
    logger.info("${event.creator.name} created world ${event.world.name}")
}
```

## Folia Compatibility

When using Folia, events may fire on different region threads. Always use appropriate schedulers:

```kotlin
@EventHandler
fun onPlayerJoin(event: PlayerJoinEvent) {
    val player = event.player

    // Safe: Entity scheduler runs on the entity's region
    player.scheduler.run(plugin, { task ->
        player.sendMessage(Component.text("Welcome!"))
        player.teleportAsync(spawnLocation)
    }, null)
}

@EventHandler
fun onBlockBreak(event: BlockBreakEvent) {
    val location = event.block.location

    // Safe: Region scheduler for location operations
    Bukkit.getRegionScheduler().run(plugin, location) { task ->
        // Block operations here
    }
}
```

See the [Folia skill](../folia/SKILL.md) for detailed threading patterns.

## Best Practices

1. **Use appropriate priority** - Don't always use HIGHEST; use NORMAL unless you need to override other plugins

2. **Check cancelled state** - Use `ignoreCancelled = true` or check `event.isCancelled` manually

3. **Don't block event handlers** - Use async operations for I/O, then schedule back

4. **Unregister when needed** - For temporary listeners:
   ```kotlin
   HandlerList.unregisterAll(myListener)
   ```

5. **Handle exceptions** - Wrap risky code in try-catch to prevent breaking other plugins

6. **Cache expensive operations** - Don't do database lookups in frequently-called events like `PlayerMoveEvent`

## Resources

- [Paper Event Javadocs](https://jd.papermc.io/paper/1.21/org/bukkit/event/package-summary.html)
- [Adventure Components](https://docs.advntr.dev/)
