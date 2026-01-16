# Triumph-GUI

A simple library for creating inventory GUIs in Bukkit/Paper plugins.

## Overview

Triumph-GUI provides a fluent builder API for creating chest-based GUIs with support for pagination, scrolling, item builders, and click handling.

**Current Version:** 3.1.13
**License:** MIT
**MC Versions:** 1.14+

## Related Documentation

- **[gui-types.md](./gui-types.md)** - Basic, Paginated, and Scrolling GUIs
- **[item-builder.md](./item-builder.md)** - Creating items with ItemBuilder
- **[features.md](./features.md)** - Filler, actions, events, and interactions

## Installation

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("dev.triumphteam:triumph-gui:3.1.13")
}
```

### Shading (Required)

Triumph-GUI must be shaded into your plugin JAR and relocated to avoid conflicts:

```kotlin
plugins {
    id("io.github.goooler.shadow") version "8.1.8"
}

tasks.shadowJar {
    relocate("dev.triumphteam.gui", "your.plugin.package.libs.gui")
}
```

## Quick Start

### Basic GUI

```kotlin
val gui = Gui.gui()
    .title(Component.text("My GUI", NamedTextColor.GOLD))
    .rows(3)
    .disableAllInteractions()
    .create()

gui.setItem(2, 5, ItemBuilder.from(Material.DIAMOND)
    .name(Component.text("Click Me!", NamedTextColor.AQUA))
    .asGuiItem { event ->
        event.isCancelled = true
        val player = event.whoClicked as Player
        player.sendMessage(Component.text("You clicked!"))
    })

gui.open(player)
```

### Paginated GUI

```kotlin
val gui = Gui.paginated()
    .title(Component.text("Items", NamedTextColor.GOLD))
    .rows(6)
    .pageSize(45)
    .disableAllInteractions()
    .create()

// Add items to pages
items.forEach { item ->
    gui.addItem(ItemBuilder.from(item.material)
        .name(Component.text(item.name))
        .asGuiItem { event -> /* handle click */ })
}

// Navigation buttons
gui.setItem(6, 3, ItemBuilder.from(Material.ARROW)
    .name(Component.text("Previous"))
    .asGuiItem { gui.previous() })

gui.setItem(6, 7, ItemBuilder.from(Material.ARROW)
    .name(Component.text("Next"))
    .asGuiItem { gui.next() })

gui.open(player)
```

## Folia Compatibility

Triumph-GUI does not have native Folia support. Use manual scheduling in click handlers:

```kotlin
.asGuiItem { event ->
    event.isCancelled = true
    val player = event.whoClicked as Player
    player.closeInventory()

    // Schedule on player's region thread
    player.scheduler.run(plugin, { _ ->
        // Folia-safe operations here
        player.teleportAsync(destination)
    }, null)
}
```

## Common Patterns

### Prevent Item Theft

Always use `disableAllInteractions()` when building GUIs:

```kotlin
Gui.gui()
    .title(Component.text("Safe GUI"))
    .rows(3)
    .disableAllInteractions()  // Prevents taking/placing items
    .create()
```

### Fill Empty Slots

```kotlin
gui.getFiller().fill(
    ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
        .name(Component.text(" "))
        .asGuiItem()
)
```

### Cancel All Clicks by Default

```kotlin
gui.setDefaultClickAction { event ->
    event.isCancelled = true
}
```

## Resources

- [GitHub Repository](https://github.com/TriumphTeam/triumph-gui)
- [Documentation](https://triumphteam.dev/docs/triumph-gui/introduction)
- [Maven Central](https://mvnrepository.com/artifact/dev.triumphteam/triumph-gui)
- [JavaDoc](https://www.javadoc.io/doc/dev.triumphteam/triumph-gui)
