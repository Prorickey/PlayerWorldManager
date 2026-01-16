# GUI Types

Triumph-GUI supports three main GUI types: Basic, Paginated, and Scrolling.

## Basic GUI

Standard chest inventory with fixed slots.

### Creation

```kotlin
val gui = Gui.gui()
    .title(Component.text("Title", NamedTextColor.GOLD))
    .rows(3)  // 1-6 rows (9-54 slots)
    .disableAllInteractions()
    .create()
```

### Inventory Types

Default is `CHEST`. Other types available:

```kotlin
val gui = Gui.gui()
    .title(Component.text("Dispenser GUI"))
    .type(GuiType.DISPENSER)  // 9 slots (3x3)
    .create()
```

| Type | Slots | Layout |
|------|-------|--------|
| `CHEST` | 9-54 | 1-6 rows of 9 |
| `WORKBENCH` | 10 | Crafting table |
| `HOPPER` | 5 | Single row of 5 |
| `DISPENSER` | 9 | 3x3 grid |
| `BREWING` | 5 | Brewing stand |

### Adding Items

```kotlin
// By slot number (0-53)
gui.setItem(0, guiItem)

// By row and column (1-indexed)
gui.setItem(2, 5, guiItem)  // Row 2, Column 5

// Auto-placement (fills next empty slot)
gui.addItem(guiItem)
```

---

## Paginated GUI

For displaying lists that span multiple pages.

### Creation

```kotlin
val gui = Gui.paginated()
    .title(Component.text("Browse Items", NamedTextColor.GOLD))
    .rows(6)
    .pageSize(45)  // Optional: slots for page content
    .disableAllInteractions()
    .create()
```

If `pageSize` is not set, it's calculated automatically when opened.

### Adding Page Items

```kotlin
// Items added with addItem() are paginated
items.forEach { item ->
    gui.addItem(ItemBuilder.from(item.material)
        .name(Component.text(item.name))
        .asGuiItem { /* click handler */ })
}

// Items added with setItem() are static (appear on all pages)
gui.setItem(6, 5, navigationButton)
```

### Navigation

```kotlin
// Previous page
gui.previous()

// Next page
gui.next()

// Check page info
val currentPage = gui.currentPageNum  // 0-indexed
val totalPages = gui.pagesNum
```

### Navigation Button Example

```kotlin
// Previous button (row 6, column 3)
gui.setItem(6, 3, ItemBuilder.from(Material.ARROW)
    .name(Component.text("Previous Page", NamedTextColor.YELLOW))
    .asGuiItem { event ->
        event.isCancelled = true
        gui.previous()
    })

// Next button (row 6, column 7)
gui.setItem(6, 7, ItemBuilder.from(Material.ARROW)
    .name(Component.text("Next Page", NamedTextColor.YELLOW))
    .asGuiItem { event ->
        event.isCancelled = true
        gui.next()
    })

// Page indicator (row 6, column 5)
gui.setItem(6, 5, ItemBuilder.from(Material.PAPER)
    .name(Component.text("Page ${gui.currentPageNum + 1}/${gui.pagesNum}", NamedTextColor.GOLD))
    .asGuiItem())
```

### Updating Page Items

```kotlin
// Update specific slot on current page
gui.updatePageItem(slot, newGuiItem)

// By row/column
gui.updatePageItem(row, col, newGuiItem)

// Get current page items
val items: Map<Int, GuiItem> = gui.currentPageItems
```

---

## Scrolling GUI

For content that scrolls in one direction.

### Creation

```kotlin
val gui = Gui.scrolling()
    .title(Component.text("Scroll List", NamedTextColor.GOLD))
    .rows(6)
    .pageSize(45)
    .scrollType(ScrollType.VERTICAL)  // Default
    .disableAllInteractions()
    .create()
```

### Scroll Types

```kotlin
// Vertical scrolling (up/down)
.scrollType(ScrollType.VERTICAL)

// Horizontal scrolling (left/right)
.scrollType(ScrollType.HORIZONTAL)
```

### Adding Items

Same as PaginatedGui:

```kotlin
// Scrollable items
gui.addItem(guiItem)

// Static items
gui.setItem(row, col, guiItem)
```

### Navigation

```kotlin
// Scroll up/left
gui.previous()

// Scroll down/right
gui.next()
```

---

## Complete Example: Paginated World List

```kotlin
class WorldListGui(
    private val plugin: JavaPlugin,
    private val worlds: List<WorldInfo>
) {
    fun open(player: Player) {
        val gui = Gui.paginated()
            .title(Component.text("Your Worlds", NamedTextColor.GOLD))
            .rows(6)
            .pageSize(45)
            .disableAllInteractions()
            .create()

        // Add world items
        worlds.forEach { world ->
            gui.addItem(ItemBuilder.from(Material.GRASS_BLOCK)
                .name(Component.text(world.name, NamedTextColor.GREEN))
                .lore(listOf(
                    Component.text("Click to teleport", NamedTextColor.GRAY)
                ))
                .asGuiItem { event ->
                    event.isCancelled = true
                    val p = event.whoClicked as Player
                    p.closeInventory()
                    p.scheduler.run(plugin, { _ ->
                        p.teleportAsync(world.spawnLocation)
                    }, null)
                })
        }

        // Fill empty slots in navigation row
        gui.getFiller().fillBetweenPoints(6, 1, 6, 9,
            ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.text(" "))
                .asGuiItem())

        // Navigation
        gui.setItem(6, 3, ItemBuilder.from(Material.ARROW)
            .name(Component.text("Previous", NamedTextColor.YELLOW))
            .asGuiItem { gui.previous() })

        gui.setItem(6, 7, ItemBuilder.from(Material.ARROW)
            .name(Component.text("Next", NamedTextColor.YELLOW))
            .asGuiItem { gui.next() })

        gui.setItem(6, 5, ItemBuilder.from(Material.PAPER)
            .name(Component.text("Page ${gui.currentPageNum + 1}/${gui.pagesNum}"))
            .asGuiItem())

        gui.open(player)
    }
}
```
