# PlayerWorldManager

Give your players the power to create and manage their own private worlds, invite friends, and enjoy isolated gameplay without interference.

## Overview

PlayerWorldManager enables per-player world management with full support for **Folia** and **Paper** servers. Players can create multiple worlds with different terrain types, invite friends, and have complete control over their personal space.

Perfect for:
- **SMP servers** where friend groups want private spaces
- **School/Community servers** where players need separation
- **Creative servers** with personal building worlds
- **Minigame servers** needing isolated player instances

## Key Features

### World Management
- Create unlimited private worlds (configurable per-player limits)
- Multiple terrain types: Normal, Flat, Amplified, Large Biomes, Void
- Each world includes Overworld, Nether, and End dimensions
- Per-world player state (inventory, health, effects persist per world)
- Automatic world backup system with scheduled backups
- Auto-unload empty worlds to save resources

### Social Features
- Invite friends to your world
- Kick unwanted players
- Accept/deny pending invites
- Role-based permissions per world

### World Settings
- Lock time (Day/Night/Normal)
- Lock weather (Clear/Rain/Normal)
- Set custom spawn points
- Configure default gamemode
- World border controls

### Chat System
- **Global** - Talk to entire server
- **World** - Talk only to players in your world
- **Both** - Messages appear in both

### Administration
- Browse all player worlds via GUI
- Enable/disable worlds
- Force delete worlds
- Set per-player world limits
- Purge old inactive worlds
- Admin bypass for world access

## Commands

| Command | Description |
|---------|-------------|
| `/world` | Open world management GUI |
| `/world create <name> [type]` | Create a new world |
| `/world tp <name>` | Teleport to your world |
| `/world invite <player>` | Invite a player |
| `/world kick <player>` | Remove a player |
| `/world settings` | Configure world settings |
| `/chat <mode>` | Set chat mode |
| `/worldadmin` | Admin management GUI |

**Aliases:** `/w`, `/worlds`, `/wa`

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `playerworldmanager.create` | Create and manage worlds | Everyone |
| `playerworldmanager.admin` | Admin commands | OP |
| `playerworldmanager.admin.bypass` | Bypass access restrictions | OP |

## Requirements

- **Minecraft:** 1.21+
- **Server:** Folia or Paper
- **Java:** 21+

### Optional
- PlaceholderAPI - For placeholders in scoreboards, chat, etc.

## Installation

1. Download the JAR file
2. Place in your `plugins/` folder
3. Restart the server
4. Configure in `plugins/PlayerWorldManager/config.yml`

## Configuration

```yaml
# Default spawn world
default-world: "world"

# Max worlds per player (-1 for unlimited)
max-worlds-per-player: 3

# Default chat mode: global, world, both
chat:
  default-mode: "global"

# Default world type
worlds:
  default-type: "NORMAL"
```

## PlaceholderAPI Support

Includes placeholders for scoreboards and chat:
- `%pwm_world_display%` - Current world name
- `%pwm_world_owner%` - World owner
- `%pwm_owned_worlds%` - Your world count
- `%pwm_world_players%` - Players in world

## Links

- [Source Code](https://github.com/Prorickey/PlayerWorldManager)
- [Issue Tracker](https://github.com/Prorickey/PlayerWorldManager/issues)
- [Documentation](https://github.com/Prorickey/PlayerWorldManager/blob/main/README.md)

## License

MIT License - Free for personal and commercial use.
