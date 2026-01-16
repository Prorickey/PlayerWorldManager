# GUI Features

Advanced features for filling, actions, events, and interaction control.

## Disabling Interactions

Prevent players from taking or placing items in the GUI.

### Disable All Interactions

```kotlin
val gui = Gui.gui()
    .title(Component.text("Safe GUI"))
    .rows(3)
    .disableAllInteractions()  // Recommended for most GUIs
    .create()
```

### Selective Disabling

```kotlin
val gui = Gui.gui()
    .title(Component.text("Custom"))
    .rows(3)
    .disableItemTake()   // Prevent taking items
    .disableItemPlace()  // Prevent placing items
    .disableItemSwap()   // Prevent swapping items
    .disableItemDrop()   // Prevent dropping items
    .create()
```

---

## GUI Filler

Fill empty slots with decorative items.

### Access Filler

```kotlin
val filler = gui.getFiller()
```

### Fill Methods

```kotlin
// Fill all empty slots
filler.fill(ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
    .name(Component.text(" "))
    .asGuiItem())

// Fill with alternating items
filler.fill(listOf(
    ItemBuilder.from(Material.BLACK_STAINED_GLASS_PANE).name(Component.text(" ")).asGuiItem(),
    ItemBuilder.from(Material.WHITE_STAINED_GLASS_PANE).name(Component.text(" ")).asGuiItem()
))
```

### Fill Specific Areas

```kotlin
// Fill border (outer edge)
filler.fillBorder(glassItem)

// Fill top row
filler.fillTop(glassItem)

// Fill bottom row
filler.fillBottom(glassItem)

// Fill rectangle between two points
filler.fillBetweenPoints(
    1, 1,  // Start: row 1, col 1
    3, 9,  // End: row 3, col 9
    glassItem
)

// With alternating items
filler.fillBetweenPoints(1, 1, 3, 9, listOf(item1, item2))
```

### Common Filler Pattern

```kotlin
// Create filler item once
val fillerItem = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
    .name(Component.text(" "))
    .asGuiItem()

// Fill border, then set content
gui.getFiller().fillBorder(fillerItem)
gui.setItem(2, 5, contentItem)
```

---

## Action Handlers

### Default Click Action

Applied to all clicks in the GUI:

```kotlin
gui.setDefaultClickAction { event ->
    event.isCancelled = true  // Prevent item theft
}
```

### Default Top Click Action

Only for clicks in the GUI inventory (not player inventory):

```kotlin
gui.setDefaultTopClickAction { event ->
    event.isCancelled = true
}
```

### Slot-Specific Action

Action for a specific slot without an item:

```kotlin
// By slot number
gui.addSlotAction(13) { event ->
    event.isCancelled = true
    // Handle click on slot 13
}

// By row and column
gui.addSlotAction(2, 5) { event ->
    event.isCancelled = true
    // Handle click on row 2, col 5
}
```

### Open Action

Triggered when GUI opens:

```kotlin
gui.setOpenGuiAction { event ->
    val player = event.player
    player.sendMessage(Component.text("GUI opened!"))
}
```

### Close Action

Triggered when GUI closes:

```kotlin
gui.setCloseGuiAction { event ->
    val player = event.player as Player
    player.sendMessage(Component.text("GUI closed!"))

    // Common pattern: cleanup or save state
    savePlayerData(player)
}
```

---

## Updating the GUI

### Update Entire GUI

Refreshes all items (does not update title):

```kotlin
gui.update()
```

### Update Title

```kotlin
gui.updateTitle(Component.text("New Title", NamedTextColor.GOLD))
```

### Update Single Item

Most efficient for single changes:

```kotlin
// By slot
gui.updateItem(13, newGuiItem)

// By row and column
gui.updateItem(2, 5, newGuiItem)
```

### Update Page Item (Paginated GUI)

```kotlin
paginatedGui.updatePageItem(slot, newGuiItem)
paginatedGui.updatePageItem(row, col, newGuiItem)
```

---

## Opening GUIs

### Basic Open

```kotlin
gui.open(player)
```

### Open with Folia Safety

```kotlin
// Close current, then open new on player's thread
player.closeInventory()
player.scheduler.run(plugin, { _ ->
    newGui.open(player)
}, null)
```

---

## Complete Example: Settings GUI

```kotlin
class SettingsGui(
    private val plugin: JavaPlugin,
    private val settings: PlayerSettings
) {
    private lateinit var gui: Gui

    fun open(player: Player) {
        gui = Gui.gui()
            .title(Component.text("Settings", NamedTextColor.GOLD))
            .rows(3)
            .disableAllInteractions()
            .create()

        // Cancel all clicks by default
        gui.setDefaultClickAction { it.isCancelled = true }

        // Log when GUI opens
        gui.setOpenGuiAction {
            plugin.logger.info("${player.name} opened settings")
        }

        // Save when GUI closes
        gui.setCloseGuiAction {
            saveSettings(player, settings)
        }

        // Fill border
        val filler = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
            .name(Component.text(" "))
            .asGuiItem()
        gui.getFiller().fillBorder(filler)

        // Settings items
        gui.setItem(2, 3, createToggleItem("Notifications", settings.notifications) {
            settings.notifications = !settings.notifications
            refreshGui()
        })

        gui.setItem(2, 5, createToggleItem("Public Profile", settings.publicProfile) {
            settings.publicProfile = !settings.publicProfile
            refreshGui()
        })

        gui.setItem(2, 7, createToggleItem("Chat Sounds", settings.chatSounds) {
            settings.chatSounds = !settings.chatSounds
            refreshGui()
        })

        gui.open(player)
    }

    private fun createToggleItem(
        name: String,
        enabled: Boolean,
        onClick: () -> Unit
    ): GuiItem {
        val material = if (enabled) Material.LIME_DYE else Material.GRAY_DYE
        val status = if (enabled) "ON" else "OFF"
        val color = if (enabled) NamedTextColor.GREEN else NamedTextColor.RED

        return ItemBuilder.from(material)
            .name(Component.text(name, NamedTextColor.WHITE))
            .lore(listOf(
                Component.text("Status: $status", color),
                Component.empty(),
                Component.text("Click to toggle", NamedTextColor.GRAY)
            ))
            .asGuiItem { event ->
                event.isCancelled = true
                onClick()
            }
    }

    private fun refreshGui() {
        // Re-create items and update
        gui.updateItem(2, 3, createToggleItem("Notifications", settings.notifications) {
            settings.notifications = !settings.notifications
            refreshGui()
        })
        gui.updateItem(2, 5, createToggleItem("Public Profile", settings.publicProfile) {
            settings.publicProfile = !settings.publicProfile
            refreshGui()
        })
        gui.updateItem(2, 7, createToggleItem("Chat Sounds", settings.chatSounds) {
            settings.chatSounds = !settings.chatSounds
            refreshGui()
        })
    }

    private fun saveSettings(player: Player, settings: PlayerSettings) {
        plugin.logger.info("Saving settings for ${player.name}")
        // Save to database/file
    }
}
```
