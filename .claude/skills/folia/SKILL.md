# Folia Development

Folia is Paper's fork that adds regionized multithreading. This fundamentally changes how plugins must handle scheduling, entity operations, and world interactions.

## Overview

Folia divides the world into independent regions that tick on separate threads. This provides massive performance improvements for large servers but requires plugins to use region-aware schedulers.

## Key Differences from Paper

| Paper | Folia |
|-------|-------|
| Single main thread | Multiple region threads |
| `Bukkit.getScheduler()` | Region-specific schedulers |
| Sync teleport | Async teleport with callbacks |
| Direct entity manipulation | Must run on entity's region thread |

## Related Documentation

- **[schedulers.md](./schedulers.md)** - Complete scheduler patterns and usage
- **[threading.md](./threading.md)** - Threading model and common patterns
- **[downloads.md](./downloads.md)** - Folia server download info

## Scripts

Located in `scripts/` folder:

- **download-server.sh** - Download Folia JAR from PaperMC API
- **run-server.sh** - Run Folia server interactively
- **test-plugin.sh** - LLM test: start server, wait for load, shutdown, analyze logs

### Gradle Tasks

```bash
# All-in-one: download (if needed), build, deploy, test (RECOMMENDED)
./gradlew testFolia

# Individual tasks:
./gradlew downloadFolia    # Download Folia server JAR to run/
./gradlew deployPlugin     # Build plugin and deploy to run/plugins/
./gradlew runFolia         # Run Folia server interactively
./gradlew testPlugin       # Test plugin loading (auto start/stop)
./gradlew cleanTestServer  # Clean up test files (keep JAR and plugins)

# RCON (Remote Console) - for interactive command testing:
./gradlew startServer              # Start server in background with RCON
./gradlew rcon -Pcmd="command"     # Send command to running server
./gradlew stopServer               # Stop the background server
```

## Quick Reference

### Critical Rules

1. **Never use `Bukkit.getScheduler()`** - It throws `UnsupportedOperationException`
2. **Always use `teleportAsync()`** - Sync teleport doesn't exist
3. **Use entity schedulers for entity operations** - Entities may be on different threads
4. **Use region schedulers for location operations** - Locations belong to regions

### Scheduler Quick Reference

```kotlin
// Entity operations (player, mob, etc.)
entity.scheduler.run(plugin, { task -> /* logic */ }, null)

// Location operations (blocks, spawning)
Bukkit.getRegionScheduler().run(plugin, location) { task -> /* logic */ }

// Global operations (world creation, plugin-wide)
Bukkit.getGlobalRegionScheduler().run(plugin) { task -> /* logic */ }

// Async I/O (database, HTTP, files)
Bukkit.getAsyncScheduler().runNow(plugin) { task -> /* logic */ }
```

### Teleportation

```kotlin
player.teleportAsync(location).thenAccept { success ->
    if (success) {
        player.scheduler.run(plugin, { task ->
            player.sendMessage(Component.text("Teleported!"))
        }, null)
    }
}
```

## Build Configuration

```kotlin
dependencies {
    // Paper API includes Folia scheduler methods
    compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")
}

paper {
    // Required for Folia servers to load the plugin
    foliaSupported = true
}
```

Or with bukkit plugin-yml:

```kotlin
bukkit {
    foliaSupported = true
}
```

## Detecting Folia vs Paper

```kotlin
object ServerUtil {
    val isFolia: Boolean by lazy {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }
}

// Usage
if (ServerUtil.isFolia) {
    player.scheduler.run(plugin, { /* Folia path */ }, null)
} else {
    Bukkit.getScheduler().runTask(plugin) { /* Paper path */ }
}
```

## Resources

- [Folia GitHub](https://github.com/PaperMC/Folia)
- [Folia Region Threading](https://docs.papermc.io/folia/reference/overview)
- [Folia Scheduler Javadocs](https://jd.papermc.io/folia/1.21/)
