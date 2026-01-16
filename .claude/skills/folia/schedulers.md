# Folia Schedulers

Folia provides multiple scheduler types for different use cases. **Never use `Bukkit.getScheduler()`** - it will throw exceptions on Folia.

## Scheduler Types Overview

| Scheduler | Use Case | Thread |
|-----------|----------|--------|
| Entity Scheduler | Operations on a specific entity | Entity's region thread |
| Region Scheduler | Operations at a specific location | Location's region thread |
| Global Region Scheduler | Non-location-specific operations | Global region thread |
| Async Scheduler | I/O, database, HTTP requests | Async thread pool |

## Entity Scheduler

For operations on a specific entity (player, mob, etc.):

```kotlin
// Run immediately on entity's region thread
entity.scheduler.run(plugin, { task ->
    // Safe to modify this entity
    entity.health = 20.0
    entity.sendMessage(Component.text("Hello!"))
}, retiredCallback)

// Run with delay (in ticks)
entity.scheduler.runDelayed(plugin, { task ->
    // Runs after delay
}, retiredCallback, delayTicks)

// Run repeatedly
entity.scheduler.runAtFixedRate(plugin, { task ->
    // Runs every period ticks
    if (shouldStop) {
        task.cancel()
    }
}, retiredCallback, initialDelayTicks, periodTicks)
```

### Retired Callback

The `retiredCallback` (nullable `Runnable`) is called if the entity is removed before the task runs:

```kotlin
entity.scheduler.run(plugin, { task ->
    // Main logic
}, {
    // Entity was removed before task could run
    logger.info("Entity no longer exists")
})

// Or null if you don't care
entity.scheduler.run(plugin, { task -> /* logic */ }, null)
```

### Player-Specific Examples

```kotlin
// Send message safely
fun Player.sendMessageSafe(plugin: JavaPlugin, message: Component) {
    this.scheduler.run(plugin, { task ->
        this.sendMessage(message)
    }, null)
}

// Give item safely
fun Player.giveItemSafe(plugin: JavaPlugin, item: ItemStack) {
    this.scheduler.run(plugin, { task ->
        this.inventory.addItem(item)
    }, null)
}

// Apply effect safely
fun Player.applyEffectSafe(plugin: JavaPlugin, effect: PotionEffect) {
    this.scheduler.run(plugin, { task ->
        this.addPotionEffect(effect)
    }, null)
}
```

## Region Scheduler

For operations at a specific location (blocks, spawning entities):

```kotlin
// Run on the region that owns this location
Bukkit.getRegionScheduler().run(plugin, location) { task ->
    // Safe to modify blocks/spawn entities at this location
    location.block.type = Material.DIAMOND_BLOCK
    location.world?.spawn(location, Zombie::class.java)
}

// Alternative with World, chunk coordinates
Bukkit.getRegionScheduler().run(plugin, world, chunkX, chunkZ) { task ->
    // Operations in this chunk
}

// Fire-and-forget (no ScheduledTask reference)
Bukkit.getRegionScheduler().execute(plugin, location) {
    // Quick operation
}

// With delay
Bukkit.getRegionScheduler().runDelayed(plugin, location, { task ->
    // Runs after delay
}, delayTicks)

// Repeating
Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, { task ->
    // Runs every period
}, initialDelayTicks, periodTicks)
```

### Block Operations

```kotlin
fun setBlockSafe(plugin: JavaPlugin, location: Location, material: Material) {
    Bukkit.getRegionScheduler().execute(plugin, location) {
        location.block.type = material
    }
}

fun spawnEntitySafe(plugin: JavaPlugin, location: Location, type: EntityType): CompletableFuture<Entity?> {
    val future = CompletableFuture<Entity?>()

    Bukkit.getRegionScheduler().execute(plugin, location) {
        val entity = location.world?.spawnEntity(location, type)
        future.complete(entity)
    }

    return future
}
```

## Global Region Scheduler

For operations not tied to any specific location/entity:

```kotlin
// Run on the global region thread
Bukkit.getGlobalRegionScheduler().run(plugin) { task ->
    // Global operations
}

// With delay
Bukkit.getGlobalRegionScheduler().runDelayed(plugin, { task ->
    // Runs after delay
}, delayTicks)

// Repeating
Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, { task ->
    // Runs every period
}, initialDelayTicks, periodTicks)

// Fire-and-forget
Bukkit.getGlobalRegionScheduler().execute(plugin) {
    // Quick operation
}
```

### World Management

```kotlin
// Creating a world
fun createWorldSafe(plugin: JavaPlugin, name: String): CompletableFuture<World?> {
    val future = CompletableFuture<World?>()

    Bukkit.getGlobalRegionScheduler().run(plugin) { task ->
        val worldCreator = WorldCreator(name)
            .environment(World.Environment.NORMAL)
        val world = Bukkit.createWorld(worldCreator)
        future.complete(world)
    }

    return future
}

// Unloading a world
fun unloadWorldSafe(plugin: JavaPlugin, world: World, save: Boolean): CompletableFuture<Boolean> {
    val future = CompletableFuture<Boolean>()

    Bukkit.getGlobalRegionScheduler().run(plugin) { task ->
        val success = Bukkit.unloadWorld(world, save)
        future.complete(success)
    }

    return future
}
```

## Async Scheduler

For truly async operations (I/O, database, HTTP):

```kotlin
// Run immediately on async thread
Bukkit.getAsyncScheduler().runNow(plugin) { task ->
    // Safe for blocking I/O
    val data = fetchFromDatabase()

    // Return to player's thread to send result
    player.scheduler.run(plugin, { _ ->
        player.sendMessage(Component.text("Data: $data"))
    }, null)
}

// With delay (uses real time, not ticks)
Bukkit.getAsyncScheduler().runDelayed(plugin, { task ->
    // Runs after delay
}, delay, TimeUnit.SECONDS)

// Repeating
Bukkit.getAsyncScheduler().runAtFixedRate(plugin, { task ->
    // Runs every period
}, initialDelay, period, TimeUnit.SECONDS)
```

### Database Pattern

```kotlin
fun loadPlayerData(plugin: JavaPlugin, player: Player, callback: (PlayerData?) -> Unit) {
    Bukkit.getAsyncScheduler().runNow(plugin) { task ->
        // Async: safe for database query
        val data = database.loadPlayer(player.uniqueId)

        // Back to player's region thread
        player.scheduler.run(plugin, { _ ->
            callback(data)
        }, {
            // Player left before data loaded
            callback(null)
        })
    }
}
```

## Teleportation

Teleportation in Folia is **always async** and requires callbacks:

```kotlin
// Basic teleport
player.teleportAsync(location).thenAccept { success ->
    if (success) {
        // Player is now at location, but we're on a different thread
        player.scheduler.run(plugin, { task ->
            player.sendMessage(Component.text("Teleported!"))
        }, null)
    } else {
        player.scheduler.run(plugin, { task ->
            player.sendMessage(Component.text("Teleport failed!"))
        }, null)
    }
}

// With cause
player.teleportAsync(location, TeleportCause.PLUGIN).thenAccept { success ->
    // Handle result
}
```

### Safe Teleport Wrapper

```kotlin
fun Player.teleportSafe(
    plugin: JavaPlugin,
    location: Location,
    onSuccess: () -> Unit = {},
    onFailure: () -> Unit = {}
) {
    this.teleportAsync(location).thenAccept { success ->
        if (success) {
            this.scheduler.run(plugin, { task ->
                onSuccess()
            }, null)
        } else {
            this.scheduler.run(plugin, { task ->
                onFailure()
            }, null)
        }
    }
}

// Usage
player.teleportSafe(plugin, spawnLocation,
    onSuccess = { player.sendMessage(Component.text("Welcome to spawn!")) },
    onFailure = { player.sendMessage(Component.text("Could not teleport")) }
)
```

## Cancelling Tasks

All scheduled tasks return a `ScheduledTask` that can be cancelled:

```kotlin
val task = entity.scheduler.runAtFixedRate(plugin, { scheduledTask ->
    // Task logic
    if (shouldStop) {
        scheduledTask.cancel()
    }
}, null, 0, 20)

// Or cancel from outside
task.cancel()

// Check if cancelled
if (task.isCancelled) {
    // Task was cancelled
}
```

## Running on All Players

```kotlin
fun forEachPlayerSafe(plugin: JavaPlugin, action: (Player) -> Unit) {
    Bukkit.getOnlinePlayers().forEach { player ->
        player.scheduler.run(plugin, { task ->
            action(player)
        }, null)
    }
}

// Usage
forEachPlayerSafe(plugin) { player ->
    player.sendMessage(Component.text("Server announcement!"))
}
```

## Common Patterns

### Event Handler with Scheduler

```kotlin
@EventHandler
fun onPlayerJoin(event: PlayerJoinEvent) {
    val player = event.player

    // Schedule welcome message after 1 second
    player.scheduler.runDelayed(plugin, { task ->
        player.sendMessage(Component.text("Welcome!"))
    }, null, 20L)  // 20 ticks = 1 second
}
```

### Command with Async Database

```kotlin
.executes { ctx ->
    val player = ctx.source.sender as? Player ?: return@executes Command.SINGLE_SUCCESS

    // Query database async
    Bukkit.getAsyncScheduler().runNow(plugin) { task ->
        val stats = database.getPlayerStats(player.uniqueId)

        // Display on player's thread
        player.scheduler.run(plugin, { _ ->
            player.sendMessage(Component.text("Your stats: $stats"))
        }, null)
    }

    Command.SINGLE_SUCCESS
}
```

### World Creation with Teleport

```kotlin
fun createAndTeleport(plugin: JavaPlugin, player: Player, worldName: String) {
    Bukkit.getGlobalRegionScheduler().run(plugin) { task ->
        val world = Bukkit.createWorld(WorldCreator(worldName))

        if (world != null) {
            player.teleportAsync(world.spawnLocation).thenAccept { success ->
                player.scheduler.run(plugin, { _ ->
                    if (success) {
                        player.sendMessage(Component.text("World created!"))
                    } else {
                        player.sendMessage(Component.text("Could not teleport"))
                    }
                }, null)
            }
        }
    }
}
```
