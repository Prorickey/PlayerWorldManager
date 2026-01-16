# ItemBuilder

Fluent API for creating GUI items with display names, lore, enchantments, and click handlers.

## Creating Items

### From Material

```kotlin
val item = ItemBuilder.from(Material.DIAMOND)
    .name(Component.text("Shiny Diamond", NamedTextColor.AQUA))
    .build()
```

### From Existing ItemStack

```kotlin
val item = ItemBuilder.from(existingItemStack)
    .name(Component.text("Modified Item"))
    .build()
```

## Available Methods

### Display Name

```kotlin
.name(Component.text("Item Name", NamedTextColor.GOLD))
```

### Lore

```kotlin
// Set lore lines
.lore(listOf(
    Component.text("Line 1", NamedTextColor.GRAY),
    Component.text("Line 2", NamedTextColor.GRAY)
))

// Or with consumer for dynamic lore
.lore { loreList ->
    loreList.add(Component.text("Dynamic line", NamedTextColor.GRAY))
}
```

### Amount

```kotlin
.amount(64)
```

### Enchantments

```kotlin
// Add enchantment
.enchant(Enchantment.SHARPNESS, 5)

// Add enchantment ignoring level restrictions
.enchant(Enchantment.SHARPNESS, 10, true)

// Remove enchantment
.disenchant(Enchantment.SHARPNESS)
```

### Glow Effect

Adds enchantment glow without visible enchantments:

```kotlin
.glow()  // Adds glow
.glow(true)  // Explicit enable
.glow(false)  // Disable
```

### Item Flags

Hide attributes, enchantments, etc:

```kotlin
.flags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS)
```

### Unbreakable

```kotlin
.unbreakable()
.unbreakable(true)
```

### Custom Model Data

```kotlin
.model(12345)
```

### Persistent Data Container

```kotlin
.pdc { container ->
    container.set(
        NamespacedKey(plugin, "my_key"),
        PersistentDataType.STRING,
        "my_value"
    )
}
```

### NBT Data

```kotlin
// Set NBT
.setNbt("key", "value")

// Remove NBT
.removeNbt("key")
```

---

## Converting to GuiItem

### Without Click Handler

```kotlin
val guiItem = ItemBuilder.from(Material.STONE)
    .name(Component.text("Display Only"))
    .asGuiItem()
```

### With Click Handler

```kotlin
val guiItem = ItemBuilder.from(Material.STONE)
    .name(Component.text("Clickable"))
    .asGuiItem { event ->
        event.isCancelled = true
        val player = event.whoClicked as Player
        player.sendMessage(Component.text("Clicked!"))
    }
```

### Build Raw ItemStack

```kotlin
val itemStack: ItemStack = ItemBuilder.from(Material.DIAMOND)
    .name(Component.text("Raw Item"))
    .build()
```

---

## Click Event Handling

The click handler receives an `InventoryClickEvent`:

```kotlin
.asGuiItem { event ->
    // Always cancel to prevent item theft
    event.isCancelled = true

    // Get the player
    val player = event.whoClicked as Player

    // Check click type
    when (event.click) {
        ClickType.LEFT -> { /* Left click */ }
        ClickType.RIGHT -> { /* Right click */ }
        ClickType.SHIFT_LEFT -> { /* Shift + Left click */ }
        ClickType.SHIFT_RIGHT -> { /* Shift + Right click */ }
        ClickType.MIDDLE -> { /* Middle click */ }
        else -> { /* Other click types */ }
    }
}
```

---

## Common Patterns

### Navigation Button

```kotlin
ItemBuilder.from(Material.ARROW)
    .name(Component.text("Back", NamedTextColor.YELLOW))
    .asGuiItem { event ->
        event.isCancelled = true
        val player = event.whoClicked as Player
        player.closeInventory()
        player.scheduler.run(plugin, { _ ->
            previousGui.open(player)
        }, null)
    }
```

### Confirmation Button

```kotlin
ItemBuilder.from(Material.TNT)
    .name(Component.text("Delete", NamedTextColor.RED))
    .lore(listOf(
        Component.text("Click to delete", NamedTextColor.GRAY),
        Component.text("This cannot be undone!", NamedTextColor.DARK_RED)
    ))
    .asGuiItem { event ->
        event.isCancelled = true
        // Show confirmation GUI
    }
```

### Status Indicator

```kotlin
fun createStatusItem(enabled: Boolean): GuiItem {
    val material = if (enabled) Material.LIME_DYE else Material.GRAY_DYE
    val status = if (enabled) "Enabled" else "Disabled"
    val color = if (enabled) NamedTextColor.GREEN else NamedTextColor.RED

    return ItemBuilder.from(material)
        .name(Component.text("Status: $status", color))
        .asGuiItem { event ->
            event.isCancelled = true
            // Toggle status
        }
}
```

### Player Head

```kotlin
ItemBuilder.skull()
    .owner(player)  // Set skull owner
    .name(Component.text(player.name, NamedTextColor.GOLD))
    .asGuiItem()
```

### Filler/Spacer Item

```kotlin
ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
    .name(Component.text(" "))  // Empty name
    .asGuiItem()  // No click handler needed
```

---

## Complete Example

```kotlin
fun createWorldItem(world: WorldInfo, plugin: JavaPlugin): GuiItem {
    return ItemBuilder.from(Material.GRASS_BLOCK)
        .name(Component.text(world.name, NamedTextColor.GREEN))
        .lore(listOf(
            Component.empty(),
            Component.text("Owner: ${world.ownerName}", NamedTextColor.GRAY),
            Component.text("Players: ${world.playerCount}", NamedTextColor.GRAY),
            Component.empty(),
            Component.text("Left-click to teleport", NamedTextColor.YELLOW),
            Component.text("Right-click for settings", NamedTextColor.YELLOW)
        ))
        .glow(world.isPublic)
        .asGuiItem { event ->
            event.isCancelled = true
            val player = event.whoClicked as Player
            player.closeInventory()

            when (event.click) {
                ClickType.LEFT -> {
                    player.scheduler.run(plugin, { _ ->
                        player.teleportAsync(world.spawnLocation)
                    }, null)
                }
                ClickType.RIGHT -> {
                    player.scheduler.run(plugin, { _ ->
                        WorldSettingsGui(plugin, world).open(player)
                    }, null)
                }
                else -> {}
            }
        }
}
```
