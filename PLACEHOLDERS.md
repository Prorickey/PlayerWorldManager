# PlayerWorldManager - PlaceholderAPI Integration

This plugin provides PlaceholderAPI placeholders for displaying world information, player stats, and permission info.

## Installation

1. Install PlaceholderAPI on your server
2. Install PlayerWorldManager
3. The placeholders will automatically register on server startup

## Available Placeholders

### Current World Information

| Placeholder | Description | Example Output |
|-------------|-------------|----------------|
| `%pwm_world%` | Current world name (full Bukkit name) | `prorickey_myworld` |
| `%pwm_world_display%` | World display name (without owner prefix) | `myworld` |
| `%pwm_world_owner%` | Current world owner name | `prorickey` |
| `%pwm_world_type%` | World generation type | `NORMAL`, `FLAT`, `VOID`, etc. |
| `%pwm_world_environment%` | Dimension type | `NORMAL`, `NETHER`, `THE_END` |

**Note:** Returns `N/A` if player is not in a plugin-managed world

### Player Counts

| Placeholder | Description | Example Output |
|-------------|-------------|----------------|
| `%pwm_world_players%` | Players in current world | `5` |
| `%pwm_world_<name>_players%` | Players in specific world (by full name) | `3` |
| `%pwm_online_players_<owner>_<world>%` | Players in owner's world | `2` |

**Examples:**
- `%pwm_world_prorickey_myworld_players%` - Players in prorickey's "myworld"
- `%pwm_online_players_prorickey_myworld%` - Same as above (alternative format)

### Permission Information

| Placeholder | Description | Example Output |
|-------------|-------------|----------------|
| `%pwm_is_owner%` | Is player owner of current world | `true` or `false` |
| `%pwm_can_build%` | Can player build in current world (owner or invited) | `true` or `false` |
| `%pwm_is_invited%` | Is player invited (not owner) to current world | `true` or `false` |

### Player Statistics

| Placeholder | Description | Example Output |
|-------------|-------------|----------------|
| `%pwm_owned_worlds%` | Number of worlds player owns | `3` |
| `%pwm_world_limit%` | Player's world creation limit | `5` |
| `%pwm_invited_to%` | Number of worlds player is invited to | `7` |

### Server Statistics

| Placeholder | Description | Example Output |
|-------------|-------------|----------------|
| `%pwm_total_worlds%` | Total worlds on server | `42` |
| `%pwm_total_players%` | Total players with worlds | `15` |

## Usage Examples

### In Chat Formats

```yaml
# With a chat plugin like EssentialsChat or ChatControlPro
format: '&7[%pwm_world_display%&7] &b%player_name%&7: &f%message%'
```

### In TAB (TabList)

```yaml
# Show world owner in tab list
tabname: '%player_name% &7(%pwm_world_owner%''s world)'
```

### In Scoreboards

```yaml
# Show player stats on a scoreboard
lines:
  - '&6&lYour Worlds'
  - '&7Owned: &e%pwm_owned_worlds%&7/&e%pwm_world_limit%'
  - '&7Invited to: &e%pwm_invited_to%'
  - ''
  - '&6&lCurrent World'
  - '&7Name: &e%pwm_world_display%'
  - '&7Owner: &e%pwm_world_owner%'
  - '&7Players: &e%pwm_world_players%'
```

### In Action Bars

```yaml
# Show current world info in action bar
actionbar: '&7World: &e%pwm_world_display% &7| &7Type: &e%pwm_world_type%'
```

### In Signs

Players can create signs with placeholders:
```
[World Info]
%pwm_world_display%
Owner: %pwm_world_owner%
Players: %pwm_world_players%
```

## Notes

- All placeholders return appropriate default values when not applicable (e.g., `N/A`, `false`, or `0`)
- Player-specific placeholders require the player to be online
- World name parameters are case-insensitive
- The full world name format is `<owner>_<worldname>` in lowercase
