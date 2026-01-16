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

```bash
# All-in-one: download (if needed), build, deploy, test (RECOMMENDED)
./gradlew testFolia

# Individual tasks:
./gradlew downloadFolia    # Download Folia server JAR to run/
./gradlew deployPlugin     # Build plugin and deploy to run/plugins/
./gradlew runFolia         # Run Folia server interactively
./gradlew testPlugin       # Test plugin loading (auto start/stop)
./gradlew cleanTestServer  # Clean up test files (keep JAR and plugins)

# RCON (Remote Console) - for interactive testing:
./gradlew startServer              # Start server in background with RCON
./gradlew rcon -Pcmd="list"        # Send command to running server
./gradlew rcon -Pcmd="help world"  # Get help for plugin commands
./gradlew stopServer               # Stop the background server
```

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

1. **ALWAYS start the server in background mode** using `run_in_background: true` parameter:
   ```
   ./gradlew startServer  # Use run_in_background: true in Bash tool
   ```
   This keeps your main thread free to send RCON commands and interact with the user.

2. **Use RCON for command testing**:
   ```bash
   ./gradlew rcon -Pcmd="pwmtest status"    # Check plugin status
   ./gradlew rcon -Pcmd="pwmtest list"      # List all worlds
   ./gradlew rcon -Pcmd="pwmtest create testworld normal"  # Create world (requires player online)
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
   ./gradlew stopServer
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
