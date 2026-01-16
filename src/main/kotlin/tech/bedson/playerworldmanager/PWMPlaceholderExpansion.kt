package tech.bedson.playerworldmanager

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.World
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.managers.DataManager
import tech.bedson.playerworldmanager.managers.InviteManager
import tech.bedson.playerworldmanager.managers.WorldManager
import tech.bedson.playerworldmanager.models.PlayerWorld

class PWMPlaceholderExpansion(
    private val plugin: JavaPlugin,
    private val worldManager: WorldManager,
    private val inviteManager: InviteManager,
    private val dataManager: DataManager
) : PlaceholderExpansion() {

    override fun getIdentifier(): String = "pwm"
    override fun getAuthor(): String = "prorickey"
    override fun getVersion(): String = plugin.pluginMeta.version
    override fun persist(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        // Handle placeholders that don't require a player
        when (params) {
            "total_worlds" -> {
                return dataManager.getAllWorlds().size.toString()
            }
            "total_players" -> {
                return dataManager.getAllPlayerData().size.toString()
            }
        }

        // For player-specific placeholders, check if player is online
        if (player == null || !player.isOnline) {
            return when {
                params.startsWith("world_") && params.endsWith("_players") -> {
                    // Handle %pwm_world_<name>_players% for offline/null players
                    val worldName = params.removePrefix("world_").removeSuffix("_players")
                    val world = findWorldByFullName(worldName)
                    if (world != null) {
                        val bukkitWorld = worldManager.getBukkitWorld(world)
                        bukkitWorld?.players?.size?.toString() ?: "0"
                    } else {
                        "0"
                    }
                }
                params.startsWith("online_players_") -> {
                    // Handle %pwm_online_players_<owner>_<world>%
                    val parts = params.removePrefix("online_players_").split("_", limit = 2)
                    if (parts.size == 2) {
                        val ownerName = parts[0]
                        val worldName = parts[1]
                        val world = findWorldByOwnerAndName(ownerName, worldName)
                        if (world != null) {
                            val bukkitWorld = worldManager.getBukkitWorld(world)
                            bukkitWorld?.players?.size?.toString() ?: "0"
                        } else {
                            "0"
                        }
                    } else {
                        "0"
                    }
                }
                else -> "N/A"
            }
        }

        val onlinePlayer = player.player ?: return "N/A"
        val currentWorld = onlinePlayer.world
        val playerWorld = worldManager.getPlayerWorldFromBukkitWorld(currentWorld)

        return when (params) {
            // Current world info
            "world" -> {
                playerWorld?.let { "${it.ownerName}_${it.name}".lowercase() } ?: "N/A"
            }
            "world_display" -> {
                playerWorld?.name ?: "N/A"
            }
            "world_owner" -> {
                playerWorld?.ownerName ?: "N/A"
            }
            "world_type" -> {
                playerWorld?.worldType?.name ?: "N/A"
            }
            "world_environment" -> {
                if (playerWorld != null) {
                    currentWorld.environment.name
                } else {
                    "N/A"
                }
            }

            // Player counts
            "world_players" -> {
                if (playerWorld != null) {
                    currentWorld.players.size.toString()
                } else {
                    "0"
                }
            }

            // Permission info
            "is_owner" -> {
                if (playerWorld != null) {
                    (playerWorld.ownerUuid == onlinePlayer.uniqueId).toString()
                } else {
                    "false"
                }
            }
            "can_build" -> {
                if (playerWorld != null) {
                    inviteManager.hasAccess(onlinePlayer.uniqueId, playerWorld).toString()
                } else {
                    "false"
                }
            }
            "is_invited" -> {
                if (playerWorld != null) {
                    // Is invited but NOT the owner
                    (inviteManager.isInvited(onlinePlayer.uniqueId, playerWorld) &&
                     playerWorld.ownerUuid != onlinePlayer.uniqueId).toString()
                } else {
                    "false"
                }
            }

            // Player stats
            "owned_worlds" -> {
                dataManager.getWorldsByOwner(onlinePlayer.uniqueId).size.toString()
            }
            "world_limit" -> {
                val playerData = dataManager.loadPlayerData(onlinePlayer.uniqueId)
                (playerData?.worldLimit ?: 3).toString()
            }
            "invited_to" -> {
                // Count how many worlds this player is invited to
                val invitedCount = dataManager.getAllWorlds().count { world ->
                    inviteManager.isInvited(onlinePlayer.uniqueId, world)
                }
                invitedCount.toString()
            }

            else -> {
                // Handle parameterized placeholders
                when {
                    params.startsWith("world_") && params.endsWith("_players") -> {
                        // %pwm_world_<name>_players%
                        val worldName = params.removePrefix("world_").removeSuffix("_players")
                        val world = findWorldByFullName(worldName)
                        if (world != null) {
                            val bukkitWorld = worldManager.getBukkitWorld(world)
                            bukkitWorld?.players?.size?.toString() ?: "0"
                        } else {
                            "0"
                        }
                    }
                    params.startsWith("online_players_") -> {
                        // %pwm_online_players_<owner>_<world>%
                        val parts = params.removePrefix("online_players_").split("_", limit = 2)
                        if (parts.size == 2) {
                            val ownerName = parts[0]
                            val worldName = parts[1]
                            val world = findWorldByOwnerAndName(ownerName, worldName)
                            if (world != null) {
                                val bukkitWorld = worldManager.getBukkitWorld(world)
                                bukkitWorld?.players?.size?.toString() ?: "0"
                            } else {
                                "0"
                            }
                        } else {
                            "0"
                        }
                    }
                    else -> null
                }
            }
        }
    }

    /**
     * Find a PlayerWorld by its full Bukkit world name (e.g., "prorickey_myworld").
     */
    private fun findWorldByFullName(fullName: String): PlayerWorld? {
        val allWorlds = dataManager.getAllWorlds()
        return allWorlds.firstOrNull { world ->
            val expectedName = "${world.ownerName}_${world.name}".lowercase().replace(" ", "_")
            expectedName == fullName.lowercase()
        }
    }

    /**
     * Find a PlayerWorld by owner name and world name.
     */
    private fun findWorldByOwnerAndName(ownerName: String, worldName: String): PlayerWorld? {
        val allWorlds = dataManager.getAllWorlds()
        return allWorlds.firstOrNull { world ->
            world.ownerName.equals(ownerName, ignoreCase = true) &&
            world.name.equals(worldName, ignoreCase = true)
        }
    }
}
