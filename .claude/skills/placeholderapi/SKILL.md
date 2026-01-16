# PlaceholderAPI Integration

PlaceholderAPI (PAPI) is a plugin that allows plugins to register placeholders that can be used across the server in chat, scoreboards, holograms, and more.

## Dependency Setup

### build.gradle.kts

```kotlin
repositories {
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("me.clip:placeholderapi:2.11.6")
}
```

### Plugin Configuration

For Paper plugins (paper-plugin.yml):
```yaml
dependencies:
  server:
    PlaceholderAPI:
      load: BEFORE
      required: false
      join-classpath: true
```

For Bukkit plugins (plugin.yml via gradle):
```kotlin
bukkit {
    softDepend = listOf("PlaceholderAPI")
}
```

## Creating a PlaceholderExpansion

```kotlin
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.java.JavaPlugin

class MyExpansion(private val plugin: MyPlugin) : PlaceholderExpansion() {

    // Return plugin instance for reloading support
    override fun getPlugin(): MyPlugin = plugin

    // Identifier used in placeholders: %myplugin_placeholder%
    override fun getIdentifier(): String = "myplugin"

    // Plugin author
    override fun getAuthor(): String = "AuthorName"

    // Expansion version (usually matches plugin version)
    override fun getVersion(): String = plugin.description.version

    // Keep expansion loaded through PAPI reloads
    override fun persist(): Boolean = true

    // Handle placeholder requests
    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        if (player == null) return null

        return when (params.lowercase()) {
            "example" -> "value"
            "player_data" -> getPlayerData(player)
            "world" -> player.player?.world?.name ?: "Unknown"
            else -> null // Unknown placeholder
        }
    }

    private fun getPlayerData(player: OfflinePlayer): String {
        // Your data retrieval logic
        return "data"
    }
}
```

## Registering the Expansion

Check if PlaceholderAPI is available before registering:

```kotlin
override fun onEnable() {
    // Check if PlaceholderAPI is installed
    if (server.pluginManager.getPlugin("PlaceholderAPI") != null) {
        val expansion = MyExpansion(this)
        if (expansion.register()) {
            logger.info("PlaceholderAPI expansion registered!")
        } else {
            logger.warning("Failed to register PlaceholderAPI expansion")
        }
    } else {
        logger.info("PlaceholderAPI not found - placeholders disabled")
    }
}

override fun onDisable() {
    // Unregister expansion
    if (server.pluginManager.getPlugin("PlaceholderAPI") != null) {
        PlaceholderAPI.unregisterExpansion(MyExpansion(this))
    }
}
```

## Placeholder Patterns

### Simple Placeholders

```kotlin
// %myplugin_world% -> "world_name"
"world" -> player.player?.world?.name ?: "Unknown"

// %myplugin_health% -> "20.0"
"health" -> player.player?.health?.toString() ?: "0"
```

### Parameterized Placeholders

```kotlin
// %myplugin_world_players_my_world% -> "5"
override fun onRequest(player: OfflinePlayer?, params: String): String? {
    if (player == null) return null

    // Parse: world_players_<worldname>
    if (params.startsWith("world_players_")) {
        val worldName = params.removePrefix("world_players_")
        val world = Bukkit.getWorld(worldName)
        return world?.players?.size?.toString() ?: "0"
    }

    return when (params.lowercase()) {
        "world" -> player.player?.world?.name
        else -> null
    }
}
```

### Multiple Parameter Parsing

```kotlin
// %myplugin_stat_kills_player_Steve% -> "42"
if (params.startsWith("stat_")) {
    val parts = params.removePrefix("stat_").split("_player_", limit = 2)
    if (parts.size == 2) {
        val statName = parts[0]
        val targetName = parts[1]
        return getPlayerStat(targetName, statName)
    }
}
```

## Relational Placeholders

For placeholders comparing two players (e.g., in TAB plugin):

```kotlin
override fun onPlaceholderRequest(
    one: Player?,
    two: Player?,
    params: String
): String? {
    if (one == null || two == null) return null

    return when (params) {
        "same_world" -> if (one.world == two.world) "Yes" else "No"
        "distance" -> one.location.distance(two.location).toInt().toString()
        else -> null
    }
}
```

## Common Placeholder Examples

| Placeholder | Description | Example Output |
|-------------|-------------|----------------|
| `%plugin_world%` | Current world name | `world_nether` |
| `%plugin_world_type%` | World environment | `NETHER` |
| `%plugin_world_players%` | Players in current world | `5` |
| `%plugin_world_owner%` | World owner name | `Steve` |
| `%plugin_is_owner%` | Is player the world owner | `true` |
| `%plugin_can_build%` | Can player build here | `true` |
| `%plugin_owned_worlds%` | Number of owned worlds | `3` |

## Using Other Placeholders

To use placeholders from other plugins in your code:

```kotlin
import me.clip.placeholderapi.PlaceholderAPI

// Parse placeholders in a string
val message = "%player_name% has %vault_eco_balance% coins"
val parsed = PlaceholderAPI.setPlaceholders(player, message)
// Result: "Steve has 1000 coins"

// Check if PAPI is available first
fun parseMessage(player: Player, message: String): String {
    return if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
        PlaceholderAPI.setPlaceholders(player, message)
    } else {
        message
    }
}
```

## Folia Compatibility

PlaceholderAPI requests happen on whatever thread requests them. For Folia compatibility:

```kotlin
override fun onRequest(player: OfflinePlayer?, params: String): String? {
    // Avoid accessing player.player directly if they might be offline
    // Use cached data or async-safe methods

    // BAD - might cause issues on wrong thread
    // val world = player.player?.world

    // GOOD - use cached data from your plugin
    val worldName = playerDataCache[player?.uniqueId]?.currentWorld
    return worldName
}
```

### Caching Strategy

```kotlin
class MyExpansion(private val plugin: MyPlugin) : PlaceholderExpansion() {

    // Thread-safe cache
    private val cache = ConcurrentHashMap<UUID, CachedData>()

    fun updateCache(uuid: UUID, data: CachedData) {
        cache[uuid] = data
    }

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        val uuid = player?.uniqueId ?: return null
        val cached = cache[uuid] ?: return null

        return when (params) {
            "world" -> cached.worldName
            "balance" -> cached.balance.toString()
            else -> null
        }
    }
}
```

## Testing Placeholders

In-game commands:
```
/papi parse me %myplugin_world%
/papi ecloud download <expansion>
/papi reload
/papi list
```

## Complete Example

```kotlin
class PWMPlaceholderExpansion(
    private val plugin: JavaPlugin,
    private val worldManager: WorldManager,
    private val dataManager: DataManager
) : PlaceholderExpansion() {

    override fun getPlugin(): JavaPlugin = plugin
    override fun getIdentifier(): String = "pwm"
    override fun getAuthor(): String = "Author"
    override fun getVersion(): String = plugin.description.version
    override fun persist(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        if (player == null) return null

        return when {
            // Current world info
            params == "world" -> getCurrentWorldName(player)
            params == "world_owner" -> getCurrentWorldOwner(player)
            params == "world_players" -> getCurrentWorldPlayerCount(player)

            // Player info
            params == "owned_worlds" -> getOwnedWorldCount(player)
            params == "is_owner" -> isCurrentWorldOwner(player)

            // Parameterized: world_<name>_players
            params.startsWith("world_") && params.endsWith("_players") -> {
                val worldName = params.removePrefix("world_").removeSuffix("_players")
                getWorldPlayerCount(worldName)
            }

            else -> null
        }
    }

    private fun getCurrentWorldName(player: OfflinePlayer): String {
        return player.player?.world?.name ?: "Unknown"
    }

    private fun getCurrentWorldOwner(player: OfflinePlayer): String {
        val world = player.player?.world ?: return "Unknown"
        val playerWorld = worldManager.getPlayerWorldFromBukkitWorld(world)
        return playerWorld?.ownerName ?: "Server"
    }

    private fun getCurrentWorldPlayerCount(player: OfflinePlayer): String {
        return player.player?.world?.players?.size?.toString() ?: "0"
    }

    private fun getOwnedWorldCount(player: OfflinePlayer): String {
        return dataManager.getWorldsByOwner(player.uniqueId).size.toString()
    }

    private fun isCurrentWorldOwner(player: OfflinePlayer): String {
        val world = player.player?.world ?: return "false"
        val playerWorld = worldManager.getPlayerWorldFromBukkitWorld(world)
        return (playerWorld?.ownerUuid == player.uniqueId).toString()
    }

    private fun getWorldPlayerCount(worldName: String): String {
        return Bukkit.getWorld(worldName)?.players?.size?.toString() ?: "0"
    }
}
```

## Resources

- [PlaceholderAPI Wiki](https://wiki.placeholderapi.com/)
- [PlaceholderAPI JavaDocs](https://extendedclip.com/javadocs/placeholderapi/)
- [Expansion Template](https://github.com/PlaceholderAPI/PlaceholderAPI/wiki/PlaceholderExpansion)
