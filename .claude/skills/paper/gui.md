# GUI Development

For comprehensive GUI documentation, see the **[triumph-gui](../triumph-gui/SKILL.md)** skill folder.

## Quick Reference

```kotlin
// Basic GUI
val gui = Gui.gui()
    .title(Component.text("Title", NamedTextColor.GOLD))
    .rows(3)
    .disableAllInteractions()
    .create()

// Paginated GUI
val gui = Gui.paginated()
    .title(Component.text("List", NamedTextColor.GOLD))
    .rows(6)
    .pageSize(45)
    .disableAllInteractions()
    .create()

// Item with click handler
ItemBuilder.from(Material.DIAMOND)
    .name(Component.text("Click Me"))
    .asGuiItem { event ->
        event.isCancelled = true
        val player = event.whoClicked as Player
        // Handle click
    }
```

## Documentation

- **[triumph-gui/SKILL.md](../triumph-gui/SKILL.md)** - Overview and installation
- **[triumph-gui/gui-types.md](../triumph-gui/gui-types.md)** - Basic, Paginated, Scrolling GUIs
- **[triumph-gui/item-builder.md](../triumph-gui/item-builder.md)** - ItemBuilder API
- **[triumph-gui/features.md](../triumph-gui/features.md)** - Filler, actions, events
