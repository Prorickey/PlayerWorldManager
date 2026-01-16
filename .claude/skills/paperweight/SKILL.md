# paperweight-userdev

Paper's official Gradle plugin for accessing Minecraft server internals (NMS) during development.

## Overview

paperweight-userdev provides access to `net.minecraft.server` code with proper deobfuscation and IDE support. It's the recommended way to work with Minecraft internals on Paper/Folia.

## Setup

### 1. settings.gradle.kts

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}
```

### 2. build.gradle.kts

```kotlin
plugins {
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
}

dependencies {
    // Replace compileOnly("io.papermc.paper:paper-api:...") with:
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
}

// Use Mojang mappings for Paper-only plugins (recommended)
paperweight.reobfArtifactConfiguration =
    io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

// Ensure reobfJar runs during build
tasks.assemble {
    dependsOn(tasks.reobfJar)
}
```

## Accessing NMS Classes

With paperweight-userdev, you can directly import and use Minecraft classes:

```kotlin
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.storage.LevelStorageSource
import org.bukkit.craftbukkit.CraftServer
import org.bukkit.craftbukkit.CraftWorld

// Get MinecraftServer instance
val craftServer = Bukkit.getServer() as CraftServer
val minecraftServer: MinecraftServer = craftServer.server

// Get ServerLevel from a Bukkit World
val world: World = // bukkit world
val serverLevel: ServerLevel = (world as CraftWorld).handle
```

## World Creation on Folia

Folia doesn't support `WorldCreator.createWorld()`. Use NMS directly:

```kotlin
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.dimension.LevelStem
import net.minecraft.world.level.storage.LevelStorageSource
import net.minecraft.world.level.storage.PrimaryLevelData
import org.bukkit.craftbukkit.CraftServer

fun createWorld(name: String) {
    val craftServer = Bukkit.getServer() as CraftServer
    val console: MinecraftServer = craftServer.server

    // Create storage access
    val storageSource = LevelStorageSource.createDefault(
        craftServer.worldContainer.toPath()
    )
    val storageAccess = storageSource.validateAndCreateAccess(name, dimensionKey)

    // Create and register the world
    val serverLevel = ServerLevel(
        console,
        console.executor,
        storageAccess,
        levelData,
        dimensionKey,
        levelStem,
        console.progressListenerFactory.create(11),
        false, // isDebug
        seed,
        listOf(), // spawners
        false, // tickTime
        console.randomSequences,
        // ... other params
    )

    console.addLevel(serverLevel)
    console.initWorld(serverLevel, levelData, levelData, levelData.worldGenOptions())
}
```

## Key NMS Classes for World Management

| Class | Purpose |
|-------|---------|
| `MinecraftServer` | Main server instance, manages levels |
| `ServerLevel` | Represents a loaded dimension/world |
| `LevelStorageSource` | Creates storage access for worlds |
| `PrimaryLevelData` | World data (spawn, game rules, etc.) |
| `LevelStem` | Dimension configuration (generator, type) |
| `ResourceKey<Level>` | World identifier |

## CraftBukkit Bridge Classes

| Class | Purpose |
|-------|---------|
| `CraftServer` | Bukkit Server -> MinecraftServer |
| `CraftWorld` | Bukkit World -> ServerLevel |
| `CraftPlayer` | Bukkit Player -> ServerPlayer |

## Gradle Tasks

```bash
./gradlew reobfJar      # Build production JAR (remapped)
./gradlew assemble      # Build all artifacts
```

## Resources

- [Official Docs](https://docs.papermc.io/paper/dev/userdev/)
- [Internals Guide](https://docs.papermc.io/paper/dev/internals/)
- [Example Plugin](https://github.com/PaperMC/paperweight-test-plugin)
