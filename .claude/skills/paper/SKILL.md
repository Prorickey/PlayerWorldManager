# Paper Plugin Development

This skill provides guidance for developing Minecraft plugins using Kotlin and Gradle, targeting Paper servers (1.21+).

## Overview

Paper is a high-performance fork of Spigot that provides additional APIs and optimizations. This project uses Paper's modern plugin system with `paper-plugin.yml`.

## Technology Stack

- **Language**: Kotlin 1.9+
- **Build System**: Gradle with Kotlin DSL
- **Server API**: Paper API 1.21+
- **GUI Library**: Triumph GUI (for inventory GUIs)
- **Configuration**: Paper's YAML configuration system

## Related Skills

- **[commands.md](./commands.md)** - Brigadier command system (Paper's modern command API)
- **[events.md](./events.md)** - Event handling patterns
- **[gui.md](./gui.md)** - Triumph GUI and inventory management
- **[downloads.md](./downloads.md)** - Server JAR download scripts

For Folia-specific patterns (regionized multithreading), see the **[folia](../folia/SKILL.md)** skill.

## Project Structure

```
src/main/
├── kotlin/
│   └── com/example/plugin/
│       ├── PluginMain.kt           # Main plugin class
│       ├── commands/               # Brigadier command handlers
│       ├── listeners/              # Event listeners
│       ├── gui/                    # GUI implementations
│       ├── managers/               # Business logic managers
│       ├── models/                 # Data classes
│       └── utils/                  # Utility functions
└── resources/
    ├── paper-plugin.yml            # Plugin metadata (auto-generated)
    └── config.yml                  # Default configuration
```

## Plugin Main Class

```kotlin
class PluginMain : JavaPlugin() {
    companion object {
        lateinit var instance: PluginMain
            private set
    }

    override fun onEnable() {
        instance = this
        saveDefaultConfig()

        // Register commands via LifecycleEventManager
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val registrar = event.registrar()
            // Register Brigadier commands here
        }

        // Register listeners
        server.pluginManager.registerEvents(MyListener(this), this)
    }

    override fun onDisable() {
        // Cleanup resources
    }
}
```

## Build Configuration

### build.gradle.kts

```kotlin
plugins {
    kotlin("jvm") version "1.9.22"
    id("io.github.goooler.shadow") version "8.1.8"
    id("xyz.jpenilla.run-paper") version "2.2.2"
    id("net.minecrell.plugin-yml.paper") version "0.6.0"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.triumphteam.dev/snapshots/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")
    implementation("dev.triumphteam:triumph-gui:3.1.7")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
}

tasks {
    shadowJar {
        relocate("dev.triumphteam.gui", "com.example.plugin.libs.gui")
        relocate("kotlin", "com.example.plugin.libs.kotlin")
    }

    runServer {
        minecraftVersion("1.21")
    }
}

paper {
    main = "com.example.plugin.PluginMain"
    apiVersion = "1.21"
    foliaSupported = true  // Enable if using Folia schedulers
    name = "MyPlugin"
    version = project.version.toString()
    description = "Plugin description"
    authors = listOf("Author")
}
```

## paper-plugin.yml Structure

The `net.minecrell.plugin-yml.paper` plugin generates `paper-plugin.yml` automatically. Key differences from `plugin.yml`:

- Commands are registered programmatically via Brigadier (not in YAML)
- Dependencies use `dependencies.server` and `dependencies.bootstrap` sections
- Supports `bootstrapper` and `loader` for advanced initialization

```yaml
name: MyPlugin
version: '1.0.0'
main: com.example.plugin.PluginMain
api-version: '1.21'
folia-supported: true
description: Plugin description
authors:
  - Author

dependencies:
  server:
    PlaceholderAPI:
      load: BEFORE
      required: false
      join-classpath: true
```

## Configuration Access

```kotlin
// In main class
saveDefaultConfig()
val value = config.getString("key", "default")

// Custom config files
val customConfig = YamlConfiguration.loadConfiguration(File(dataFolder, "custom.yml"))
```

## Anti-Patterns to Avoid

1. **Using plugin.yml commands section** - Use Brigadier command registration instead
2. **Blocking the main thread** - Use async operations for I/O
3. **Memory leaks with listeners** - Always unregister listeners on disable
4. **Hardcoded messages** - Use configuration or MiniMessage for user-facing text
5. **Direct world file manipulation** - Use Bukkit API for world operations

## Testing

```bash
# Build the plugin
./gradlew shadowJar

# Run a test Paper server with the plugin
./gradlew runServer

# The plugin JAR will be at build/libs/<name>.jar
```

## Resources

- [Paper Documentation](https://docs.papermc.io/)
- [Paper Plugins Guide](https://docs.papermc.io/paper/dev/getting-started/paper-plugins/)
- [Paper API Javadocs](https://jd.papermc.io/paper/1.21/)
- [Triumph GUI Wiki](https://triumphteam.dev/library/triumph-gui/introduction)
- [Adventure Documentation](https://docs.advntr.dev/)
