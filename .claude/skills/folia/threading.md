# Folia Threading Model

Folia uses regionized multithreading, meaning different parts of the world tick on different threads. Understanding this model is crucial for writing correct plugins.

## Region Concept

Folia divides the world into regions based on chunks. Each region:

- Contains a group of nearby loaded chunks
- Ticks on its own dedicated thread
- Is independent from other regions

Entities and blocks within a region are only safe to access from that region's thread.

## Thread Safety Rules

### Safe Operations

| Operation | Safe From | Scheduler to Use |
|-----------|-----------|------------------|
| Modify player inventory | Player's region thread | `player.scheduler` |
| Send player message | Player's region thread | `player.scheduler` |
| Teleport player | Any thread (async) | `player.teleportAsync()` |
| Modify block | Block's region thread | `Bukkit.getRegionScheduler()` |
| Spawn entity | Location's region thread | `Bukkit.getRegionScheduler()` |
| Create world | Global region thread | `Bukkit.getGlobalRegionScheduler()` |
| Database query | Async thread | `Bukkit.getAsyncScheduler()` |
| Read config | Any thread | Direct access OK |

### Unsafe Operations

These will cause issues or exceptions:

```kotlin
// BAD: Direct entity manipulation from wrong thread
player.inventory.addItem(item)  // May be wrong thread!

// BAD: Using old scheduler
Bukkit.getScheduler().runTask(plugin) { }  // Throws exception!

// BAD: Blocking the region thread
Thread.sleep(1000)  // Freezes the region!

// BAD: Accessing entity from event that might fire on wrong thread
event.entity.remove()  // May not be on entity's thread!
```

## Event Threading

Events may fire on different threads depending on the event type and context:

### Player Events

Most player events fire on the player's region thread:

```kotlin
@EventHandler
fun onPlayerMove(event: PlayerMoveEvent) {
    // Safe: we're on the player's region thread
    val player = event.player
    player.sendMessage(Component.text("You moved!"))  // Safe
}
```

### Async Events

Some events explicitly run async:

```kotlin
@EventHandler
fun onAsyncChat(event: AsyncChatEvent) {
    // NOT safe: we're on an async thread
    val player = event.player

    // Must schedule to player's thread
    player.scheduler.run(plugin, { task ->
        player.sendMessage(Component.text("Message received"))
    }, null)
}
```

### Cross-Region Events

Some events might involve entities from different regions:

```kotlin
@EventHandler
fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
    val damager = event.damager
    val victim = event.entity

    // damager and victim might be in different regions!
    // Only safe to modify the victim (event fires on victim's thread)

    // To modify damager, schedule to its thread
    damager.scheduler.run(plugin, { task ->
        // Safe to modify damager here
    }, null)
}
```

## Common Anti-Patterns

### 1. Using Bukkit.getScheduler()

```kotlin
// BAD
Bukkit.getScheduler().runTask(plugin) {
    player.sendMessage(Component.text("Hello"))
}

// GOOD
player.scheduler.run(plugin, { task ->
    player.sendMessage(Component.text("Hello"))
}, null)
```

### 2. Sync Teleportation

```kotlin
// BAD - doesn't exist in Folia
player.teleport(location)

// GOOD
player.teleportAsync(location).thenAccept { success ->
    player.scheduler.run(plugin, { task ->
        player.sendMessage(Component.text("Teleported!"))
    }, null)
}
```

### 3. Direct Entity Manipulation from Events

```kotlin
// BAD - might be wrong thread
@EventHandler
fun onInventoryClick(event: InventoryClickEvent) {
    val player = event.whoClicked as Player
    player.closeInventory()  // May cause issues
}

// GOOD
@EventHandler
fun onInventoryClick(event: InventoryClickEvent) {
    val player = event.whoClicked as Player
    player.scheduler.run(plugin, { task ->
        player.closeInventory()
    }, null)
}
```

### 4. Storing Entity References

```kotlin
// BAD - entity may move to different region
val savedPlayer: Player = player  // Dangerous reference

// Later...
savedPlayer.sendMessage(...)  // May be wrong thread!

// GOOD - store UUID, look up when needed
val playerUuid: UUID = player.uniqueId

// Later...
Bukkit.getPlayer(playerUuid)?.let { player ->
    player.scheduler.run(plugin, { task ->
        player.sendMessage(...)
    }, null)
}
```

### 5. Blocking Operations

```kotlin
// BAD - blocks region thread
player.scheduler.run(plugin, { task ->
    Thread.sleep(5000)  // Freezes region for 5 seconds!
    val data = httpClient.get(url)  // Blocking I/O!
}, null)

// GOOD - use async scheduler
Bukkit.getAsyncScheduler().runNow(plugin) { task ->
    Thread.sleep(5000)  // OK on async thread
    val data = httpClient.get(url)  // OK on async thread

    player.scheduler.run(plugin, { _ ->
        player.sendMessage(Component.text("Data: $data"))
    }, null)
}
```

## Thread Checking

### Checking Current Thread

```kotlin
// Check if on main thread (always false in Folia for gameplay)
if (Bukkit.isPrimaryThread()) {
    // On main thread
}

// Check if on a region thread
// (No direct API, but you can track in your scheduler callbacks)
```

### Assertions for Debugging

```kotlin
fun requireRegionThread(entity: Entity) {
    // In debug mode, verify we're on the right thread
    // Note: No direct API, but you can use stack traces or thread names
}
```

## Data Synchronization

When sharing data between regions, use thread-safe structures:

```kotlin
import java.util.concurrent.ConcurrentHashMap

// Thread-safe player data cache
private val playerData = ConcurrentHashMap<UUID, PlayerData>()

// Safe to read/write from any thread
fun getPlayerData(uuid: UUID): PlayerData? = playerData[uuid]
fun setPlayerData(uuid: UUID, data: PlayerData) { playerData[uuid] = data }
```

### Atomic Operations

```kotlin
import java.util.concurrent.atomic.AtomicInteger

// Thread-safe counter
private val onlineCount = AtomicInteger(0)

@EventHandler
fun onJoin(event: PlayerJoinEvent) {
    onlineCount.incrementAndGet()
}

@EventHandler
fun onQuit(event: PlayerQuitEvent) {
    onlineCount.decrementAndGet()
}
```

## Testing Considerations

### Local Testing

The `./gradlew runServer` task starts Paper, not Folia. For Folia testing:

1. Download Folia JAR (see [downloads.md](./downloads.md))
2. Set up a Folia test server manually
3. Copy your plugin JAR to the `plugins/` folder
4. Test all features with multiple players in different world regions

### Common Issues to Test

- Player teleportation across regions
- Entity interactions between regions
- Async data loading/saving
- Event handlers with entity access
- Scheduled tasks after player disconnect

## Resources

- [Folia GitHub](https://github.com/PaperMC/Folia)
- [Folia Region Threading](https://docs.papermc.io/folia/reference/overview)
- [Folia Scheduler Javadocs](https://jd.papermc.io/folia/1.21/)
