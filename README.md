# PlayerWorldManager

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21+-green.svg)](https://www.minecraft.net/)
[![Folia](https://img.shields.io/badge/Folia-Supported-blue.svg)](https://papermc.io/software/folia)
[![Paper](https://img.shields.io/badge/Paper-Supported-blue.svg)](https://papermc.io/software/paper)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A powerful Minecraft plugin that allows players to create and manage their own private worlds, invite friends, and control chat visibility. Built with **Folia's regionized multithreading** for optimal performance on large servers.

## Features

- **Personal Worlds** - Players can create multiple private worlds with different generation types (Normal, Flat, Amplified, Large Biomes, Void)
- **Multi-Dimension Support** - Each world includes Overworld, Nether, and End dimensions
- **Friend System** - Invite players to your world or kick them when needed
- **Access Control** - Only invited players can enter your world (with admin bypass)
- **Chat Modes** - Choose between Global chat, World-only chat, or Both
- **GUI Management** - Intuitive inventory-based menus for easy world management
- **World Settings** - Lock time (Day/Night), lock weather (Clear/Rain), set custom spawn points, and default gamemode
- **Admin Tools** - Browse all worlds, enable/disable worlds, force delete, set player limits
- **PlaceholderAPI** - Full integration for scoreboards, chat, and more

## Requirements

| Requirement | Version |
|-------------|---------|
| Minecraft | 1.21+ |
| Server | [Folia](https://papermc.io/software/folia) or [Paper](https://papermc.io/software/paper) |
| Java | 21+ |

### Optional Dependencies

- [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) - For placeholder support in other plugins

## Installation

1. Download the latest release from the [Releases](../../releases) page
2. Place the JAR file in your server's `plugins/` folder
3. Restart your server
4. Configure the plugin in `plugins/PlayerWorldManager/config.yml`

## Commands

### Player Commands

| Command | Description |
|---------|-------------|
| `/world` | Open the world management GUI |
| `/world create <name> [type]` | Create a new world (types: normal, flat, amplified, large_biomes, void) |
| `/world delete <name>` | Delete one of your worlds |
| `/world list` | List your worlds |
| `/world tp <name>` | Teleport to your world |
| `/world visit <owner> <name>` | Visit another player's world (if invited) |
| `/world invite <player> [world]` | Invite a player to your world |
| `/world kick <player> [world]` | Remove a player from your world |
| `/world accept <owner> <world>` | Accept a pending invite |
| `/world deny <owner> <world>` | Deny a pending invite |
| `/world setspawn` | Set the spawn point in your current world |
| `/world settings` | Configure world settings (time, weather, gamemode) |

**Aliases:** `/w`, `/worlds`

### Chat Commands

| Command | Description |
|---------|-------------|
| `/chat` | Show your current chat mode |
| `/chat <mode>` | Set chat mode (global, world, both) |

**Aliases:** `/chatmode`

### Admin Commands

| Command | Description |
|---------|-------------|
| `/worldadmin` | Open admin management GUI |
| `/worldadmin list [player]` | List all worlds or a player's worlds |
| `/worldadmin info <owner> <world>` | View detailed world information |
| `/worldadmin tp <owner> <world>` | Teleport to any world |
| `/worldadmin delete <owner> <world>` | Force delete a world |
| `/worldadmin enable <owner> <world>` | Enable a disabled world |
| `/worldadmin disable <owner> <world>` | Disable a world |
| `/worldadmin setlimit <player> <limit>` | Set a player's world creation limit |
| `/worldadmin purge <days>` | Delete worlds older than specified days |

**Aliases:** `/wa`, `/wadmin`

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `playerworldmanager.create` | Create and manage personal worlds | All Players |
| `playerworldmanager.admin` | Access admin commands and GUI | OP |
| `playerworldmanager.admin.bypass` | Bypass world access restrictions | OP |

## Configuration

```yaml
# plugins/PlayerWorldManager/config.yml

# The default world where players spawn
default-world: "world"

# Maximum number of worlds a player can create (-1 for unlimited)
max-worlds-per-player: 3

# Chat settings
chat:
  # Default chat mode for new players: global, world, or both
  default-mode: "global"

# World creation settings
worlds:
  # Default world type: NORMAL, FLAT, AMPLIFIED, LARGE_BIOMES, VOID
  default-type: "NORMAL"
```

## PlaceholderAPI

PlayerWorldManager integrates with PlaceholderAPI to provide placeholders for use in scoreboards, chat plugins, tab lists, and more.

**Example placeholders:**
- `%pwm_world_display%` - Current world name
- `%pwm_world_owner%` - World owner name
- `%pwm_owned_worlds%` - Number of worlds you own
- `%pwm_world_players%` - Players in current world

See [PLACEHOLDERS.md](PLACEHOLDERS.md) for the complete list of available placeholders and usage examples.

## Building from Source

```bash
# Clone the repository
git clone https://github.com/YOUR_USERNAME/PlayerWorldManager.git
cd PlayerWorldManager

# Build the plugin
./gradlew shadowJar
```

The compiled JAR will be at `build/libs/PlayerWorldManager-<version>.jar`

### Development

```bash
# Build and test on Folia server
./gradlew testFolia

# Start server for interactive testing
./gradlew startServer

# Send commands to running server
./gradlew rcon -Pcmd="world list"

# Stop the server
./gradlew stopServer
```

## Screenshots

*Coming soon*

## Support

- **Issues:** [GitHub Issues](../../issues)
- **Discussions:** [GitHub Discussions](../../discussions)

## Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on how to contribute.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

Made with Kotlin and Folia
