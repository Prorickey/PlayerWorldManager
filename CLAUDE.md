# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build the plugin JAR (output: build/libs/PlayerWorldManager-<version>.jar)
./gradlew shadowJar

# Clean and rebuild
./gradlew clean shadowJar
```

## Folia Server Commands

All server management is done through Gradle tasks. These tasks are defined in `build.gradle.kts`.

### Core Tasks

| Task | Description |
|------|-------------|
| `downloadFolia` | Download the latest Folia server JAR to `run/` |
| `deployPlugin` | Build plugin and deploy to `run/plugins/` |
| `startServer` | Start server in foreground (Ctrl+C to stop) |
| `rcon -Pcmd="cmd"` | Send RCON command to running server |

### Cleaning Tasks

| Task | Description |
|------|-------------|
| `serverClean` | Remove worlds and plugin data (keeps JAR and plugins) |
| `cleanAll` | Delete everything in run/ (requires re-download of Folia) |

### Composite Tasks

| Task | Description |
|------|-------------|
| `fresh` | Clean worlds, rebuild plugin, and start fresh |
| `testFolia` | Download (if needed), build, deploy, and test loading |
| `runFolia` | Run server interactively (foreground) |

### Quick Reference

```bash
# First time setup
./gradlew downloadFolia      # Download Folia JAR
./gradlew startServer        # Start server (Ctrl+C to stop)

# Development cycle
./gradlew deployPlugin       # Rebuild plugin and deploy to run/plugins/
./gradlew startServer        # Start server with updated plugin

# Fresh start (clean worlds)
./gradlew fresh              # Clean worlds, rebuild, start

# Send commands to running server (in a separate terminal)
./gradlew rcon -Pcmd="list"
./gradlew rcon -Pcmd="pwmtest status"
./gradlew rcon -Pcmd="stop"

# Full reset
./gradlew cleanAll           # Delete everything
./gradlew downloadFolia      # Re-download Folia
```

### Server Configuration

The `startServer` task automatically configures:
- `eula.txt` - Auto-accepts EULA
- `server.properties` - Sets `online-mode=false`, enables RCON on port 25575 (password: `test`)
- `bukkit.yml` - Sets `world-container: worlds` (all worlds stored in `run/worlds/`)

## Architecture

This is a Minecraft Folia/Paper plugin written in Kotlin using Gradle Kotlin DSL. It targets Folia's regionized multithreading model.

### Core Components

- **PlayerWorldManager.kt** - Main plugin entry point, initializes managers and registers commands/listeners
- **managers/** - Business logic for world management, player permissions, and chat routing
- **commands/** - Command handlers for `/world` and `/chat` commands
- **listeners/** - Event handlers for player joins, chat events, and world changes
- **gui/** - Triumph GUI implementations for world management menus
- **models/** - Data classes for player worlds, invites, and chat settings

### Key Systems

1. **World Management**: Each player can own multiple worlds. Worlds are created via Bukkit's WorldCreator (on the global region scheduler) and tracked in plugin data files.

2. **Invite System**: World owners can invite/kick players. Non-invited players cannot enter private worlds.

3. **Chat Routing**: Players choose between global, world-only, or both chat modes. Chat events are intercepted and routed based on player settings.

### Folia Threading Model

**Critical**: This plugin uses Folia's region-based schedulers. Never use `Bukkit.getScheduler()`.

- **Entity operations**: Use `entity.scheduler.run(plugin, task, retired)`
- **Location operations**: Use `Bukkit.getRegionScheduler().run(plugin, location, task)`
- **Global operations**: Use `Bukkit.getGlobalRegionScheduler().run(plugin, task)`
- **Async I/O**: Use `Bukkit.getAsyncScheduler().runNow(plugin, task)`
- **Teleportation**: Always use `player.teleportAsync(location)` with callbacks

### Dependencies

- **Paper API 1.21** - Server API (compile-only, includes Folia scheduler methods)
- **Triumph GUI** - Inventory GUI library (shaded into JAR, relocated to avoid conflicts)

### Configuration

Plugin configuration is in `src/main/resources/config.yml`. The `paper` block in `build.gradle.kts` auto-generates `plugin.yml` with `folia-supported: true`.

## LLM Testing Guidelines

When testing the plugin interactively:

1. **Start the server in background mode**:
   ```bash
   ./gradlew startServer  # Use run_in_background: true in Bash tool
   ```
   This starts the server in the background. Wait ~30 seconds for the server to fully start before sending RCON commands.

2. **Use RCON for command testing** (from a separate terminal or tool call):
   ```bash
   ./gradlew rcon -Pcmd="pwmtest status"    # Check plugin status
   ./gradlew rcon -Pcmd="pwmtest list"      # List all worlds
   ./gradlew rcon -Pcmd="pwmtest create testworld normal"  # Create world
   ```

3. **Console test commands** (`/pwmtest`) are available for automated testing:
   - `pwmtest status` - Show plugin and server status
   - `pwmtest list` - List all plugin worlds
   - `pwmtest create <name> [type]` - Create world (requires test player "Prodeathmaster" online)
   - `pwmtest delete <name>` - Delete a world
   - `pwmtest info <name>` - Show world details
   - `pwmtest cleanup` - Delete all test player's worlds

4. **Stop the server** when done:
   ```bash
   ./gradlew rcon -Pcmd="stop"  # Or press Ctrl+C if attached
   ```

## Skills

See `.claude/skills/` for development patterns:

- **paper/** - Paper plugin development
  - `SKILL.md` - Overview
  - `commands.md` - Brigadier command registration
  - `events.md` - Event handling
  - `gui.md` - Quick reference (links to triumph-gui)
  - `scripts/` - Server download scripts

- **folia/** - Folia-specific patterns
  - `SKILL.md` - Overview and gradle tasks
  - `schedulers.md` - Region scheduler patterns
  - `threading.md` - Threading model
  - `scripts/` - Server download, run, and test scripts

- **triumph-gui/** - Inventory GUI library
  - `SKILL.md` - Overview and installation
  - `gui-types.md` - Basic, Paginated, Scrolling GUIs
  - `item-builder.md` - ItemBuilder API
  - `features.md` - Filler, actions, events

- **placeholderapi/** - PlaceholderAPI integration
  - `SKILL.md` - Placeholder registration guide
