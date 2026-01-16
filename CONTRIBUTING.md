# Contributing to PlayerWorldManager

Thank you for your interest in contributing to PlayerWorldManager! This document provides guidelines and instructions for contributing.

## Getting Started

1. Fork the repository
2. Clone your fork:
   ```bash
   git clone https://github.com/Prorickey/PlayerWorldManager.git
   cd PlayerWorldManager
   ```
3. Create a feature branch:
   ```bash
   git checkout -b feature/your-feature-name
   ```
4. Make your changes
5. Test your changes
6. Commit and push:
   ```bash
   git commit -m "Add your feature"
   git push origin feature/your-feature-name
   ```
7. Open a Pull Request

## Development Setup

### Requirements

- JDK 21+
- Gradle 8.5+ (wrapper included)

### Building

```bash
# Build the plugin JAR
./gradlew shadowJar
```

The compiled JAR will be at `build/libs/PlayerWorldManager-<version>.jar`

### Testing

#### Quick Test (Automated)

```bash
# Build, deploy, and test plugin loading on Folia
./gradlew testFolia
```

This will:
1. Download Folia server (if needed)
2. Build the plugin
3. Start the server
4. Wait for plugin to load
5. Shut down and report results

#### Interactive Testing

```bash
# Start server in background
./gradlew startServer

# Send commands via RCON
./gradlew rcon -Pcmd="world list"
./gradlew rcon -Pcmd="worldadmin list"

# Stop the server
./gradlew stopServer
```

#### Individual Tasks

```bash
./gradlew downloadFolia    # Download Folia server JAR
./gradlew deployPlugin     # Build and deploy to test server
./gradlew runFolia         # Run server interactively
./gradlew cleanTestServer  # Clean test files (keeps JAR)
```

## Project Structure

```
src/main/kotlin/tech/bedson/playerworldmanager/
├── PlayerWorldManager.kt    # Main plugin class
├── commands/                # Brigadier command handlers
│   ├── WorldCommands.kt
│   ├── WorldAdminCommands.kt
│   └── ChatCommands.kt
├── managers/                # Business logic
│   ├── DataManager.kt       # Persistence
│   ├── WorldManager.kt      # World operations
│   ├── InviteManager.kt     # Invite system
│   └── ChatManager.kt       # Chat routing
├── listeners/               # Event handlers
│   ├── AccessListener.kt
│   └── ChatListener.kt
├── gui/                     # Triumph GUI menus
├── models/                  # Data classes
└── utils/                   # Utilities
```

## Code Style

- **Language**: Kotlin
- **Formatting**: Follow existing code style
- **Naming**: Use descriptive, meaningful names
- **Comments**: Add comments for complex logic only
- **Folia**: Always use region-aware schedulers (never `Bukkit.getScheduler()`)

### Folia Threading Rules

```kotlin
// Entity operations
entity.scheduler.run(plugin, { task -> /* code */ }, null)

// Location operations
Bukkit.getRegionScheduler().run(plugin, location) { task -> /* code */ }

// Global operations
Bukkit.getGlobalRegionScheduler().run(plugin) { task -> /* code */ }

// Async I/O
Bukkit.getAsyncScheduler().runNow(plugin) { task -> /* code */ }

// Teleportation (always async)
player.teleportAsync(location).thenAccept { success -> /* callback */ }
```

## Pull Request Guidelines

- **Focus**: Keep PRs focused on a single feature or fix
- **Size**: Smaller PRs are easier to review
- **Tests**: Ensure the plugin loads without errors
- **Documentation**: Update README/docs if needed
- **Description**: Clearly describe your changes

### PR Checklist

- [ ] Code compiles without errors (`./gradlew shadowJar`)
- [ ] Plugin loads and disables cleanly (`./gradlew testFolia`)
- [ ] No new compiler warnings
- [ ] Documentation updated (if applicable)
- [ ] Commit messages are clear and descriptive

## Reporting Issues

When reporting issues, please include:

- **Server**: Folia or Paper version
- **Plugin Version**: From `/version PlayerWorldManager`
- **Java Version**: From `java -version`
- **Steps to Reproduce**: Detailed steps
- **Expected Behavior**: What should happen
- **Actual Behavior**: What actually happens
- **Logs**: Relevant error logs from `logs/latest.log`

### Issue Template

```markdown
**Server Version:** Folia 1.21.4
**Plugin Version:** 0.1.0
**Java Version:** 21

**Description:**
A clear description of the bug.

**Steps to Reproduce:**
1. Do this
2. Then this
3. Bug occurs

**Expected Behavior:**
What should happen.

**Actual Behavior:**
What actually happens.

**Logs:**
```
Relevant error messages
```
```

## Feature Requests

We welcome feature requests! Please:

1. Check existing issues to avoid duplicates
2. Clearly describe the feature
3. Explain the use case
4. Consider implementation complexity

## License

By contributing, you agree that your contributions will be licensed under the MIT License.

## Questions?

Feel free to open a Discussion or Issue if you have questions about contributing.
