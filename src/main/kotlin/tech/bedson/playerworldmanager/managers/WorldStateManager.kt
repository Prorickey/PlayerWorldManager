package tech.bedson.playerworldmanager.managers

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import tech.bedson.playerworldmanager.models.LocationData
import tech.bedson.playerworldmanager.models.PlayerWorldState
import tech.bedson.playerworldmanager.models.SerializedPotionEffect
import tech.bedson.playerworldmanager.utils.DebugLogger
import java.util.*
import java.util.logging.Logger

/**
 * Manages player state (inventory, health, XP, effects) per world.
 * Uses Paper's NBT serialization for safe ItemStack storage.
 */
class WorldStateManager(
    private val plugin: JavaPlugin,
    private val dataManager: DataManager
) {
    private val logger: Logger = plugin.logger
    private val debugLogger = DebugLogger(plugin, "WorldStateManager")
    private val gson = Gson()

    /**
     * Save the player's current state for the specified world.
     *
     * @param player The player whose state to save
     * @param worldName The Bukkit world name to save state for
     */
    fun savePlayerState(player: Player, worldName: String) {
        debugLogger.debugMethodEntry("savePlayerState",
            "playerName" to player.name,
            "playerUuid" to player.uniqueId,
            "worldName" to worldName
        )
        logger.info("[WorldStateManager] Saving state for '${player.name}' in world '$worldName'")

        val playerData = dataManager.getOrCreatePlayerData(player.uniqueId, player.name)
        val location = player.location
        debugLogger.debug("Retrieved player data", "existingWorldStatesCount" to playerData.worldStates.size)

        debugLogger.debug("Serializing player state",
            "health" to player.health,
            "foodLevel" to player.foodLevel,
            "level" to player.level,
            "activePotionEffects" to player.activePotionEffects.size
        )

        val state = PlayerWorldState(
            worldName = worldName,

            // Inventory - serialize to Base64
            inventoryContents = serializeInventory(player.inventory.contents),
            armorContents = serializeInventory(player.inventory.armorContents),
            offHandItem = serializeItem(player.inventory.itemInOffHand),
            enderChestContents = serializeInventory(player.enderChest.contents),

            // Health and hunger
            health = player.health,
            maxHealth = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0,
            foodLevel = player.foodLevel,
            saturation = player.saturation,
            exhaustion = player.exhaustion,

            // Experience
            expLevel = player.level,
            expProgress = player.exp,

            // Potion effects
            potionEffects = serializePotionEffects(player.activePotionEffects),

            // Location
            location = LocationData(
                worldName = worldName,
                x = location.x,
                y = location.y,
                z = location.z,
                yaw = location.yaw,
                pitch = location.pitch
            )
        )

        debugLogger.debugState("playerWorldState",
            "worldName" to state.worldName,
            "health" to state.health,
            "foodLevel" to state.foodLevel,
            "expLevel" to state.expLevel,
            "hasInventory" to (state.inventoryContents != null),
            "hasArmor" to (state.armorContents != null),
            "hasPotionEffects" to (state.potionEffects != null)
        )

        playerData.worldStates[worldName] = state
        dataManager.savePlayerData(playerData)
        debugLogger.debug("Saved player data", "totalWorldStates" to playerData.worldStates.size)

        logger.info("[WorldStateManager] Saved state for '${player.name}' in '$worldName': " +
                "health=${state.health}, food=${state.foodLevel}, level=${state.expLevel}, " +
                "inventory=${if (state.inventoryContents != null) "saved" else "empty"}")
        debugLogger.debugMethodExit("savePlayerState", "success")
    }

    /**
     * Restore the player's state from the specified world.
     * Returns false if no saved state exists.
     *
     * @param player The player to restore state for
     * @param worldName The Bukkit world name to restore state from
     * @return True if state was restored, false if no saved state exists
     */
    fun restorePlayerState(player: Player, worldName: String): Boolean {
        debugLogger.debugMethodEntry("restorePlayerState",
            "playerName" to player.name,
            "playerUuid" to player.uniqueId,
            "worldName" to worldName
        )
        logger.info("[WorldStateManager] Restoring state for '${player.name}' from world '$worldName'")

        val playerData = dataManager.loadPlayerData(player.uniqueId)
        val state = playerData?.worldStates?.get(worldName)
        debugLogger.debug("Loaded player data",
            "playerDataFound" to (playerData != null),
            "stateFound" to (state != null),
            "availableWorldStates" to (playerData?.worldStates?.keys?.toList() ?: emptyList())
        )

        if (state == null) {
            logger.info("[WorldStateManager] No saved state for '${player.name}' in '$worldName'")
            debugLogger.debugMethodExit("restorePlayerState", false)
            return false
        }

        debugLogger.debug("Restoring state",
            "health" to state.health,
            "foodLevel" to state.foodLevel,
            "expLevel" to state.expLevel,
            "hasInventory" to (state.inventoryContents != null),
            "hasPotionEffects" to (state.potionEffects != null)
        )

        // Clear current state first
        debugLogger.debug("Clearing current potion effects")
        clearPotionEffects(player)

        // Restore inventory
        debugLogger.debug("Restoring inventory")
        player.inventory.contents = deserializeInventory(state.inventoryContents, 36)
        player.inventory.armorContents = deserializeArmor(state.armorContents)
        player.inventory.setItemInOffHand(deserializeItem(state.offHandItem))
        player.enderChest.contents = deserializeInventory(state.enderChestContents, 27)

        // Restore health (set max first to avoid clamping)
        debugLogger.debug("Restoring health", "maxHealth" to state.maxHealth, "health" to state.health)
        player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = state.maxHealth
        player.health = state.health.coerceIn(0.0, state.maxHealth)

        // Restore hunger
        debugLogger.debug("Restoring hunger", "foodLevel" to state.foodLevel, "saturation" to state.saturation)
        player.foodLevel = state.foodLevel
        player.saturation = state.saturation
        player.exhaustion = state.exhaustion

        // Restore experience
        debugLogger.debug("Restoring experience", "level" to state.expLevel, "progress" to state.expProgress)
        player.level = state.expLevel
        player.exp = state.expProgress.coerceIn(0.0f, 1.0f)

        // Restore potion effects
        debugLogger.debug("Restoring potion effects")
        restorePotionEffects(player, state.potionEffects)

        logger.info("[WorldStateManager] Restored state for '${player.name}' from '$worldName': " +
                "health=${state.health}, food=${state.foodLevel}, level=${state.expLevel}")
        debugLogger.debugMethodExit("restorePlayerState", true)

        return true
    }

    /**
     * Clear player to fresh state (for first entry into a world).
     * Sets empty inventory, full health, no effects.
     *
     * @param player The player to clear
     */
    fun clearPlayerState(player: Player) {
        debugLogger.debugMethodEntry("clearPlayerState",
            "playerName" to player.name,
            "playerUuid" to player.uniqueId
        )
        logger.info("[WorldStateManager] Clearing state for '${player.name}' (fresh start)")

        debugLogger.debug("Current player state before clear",
            "health" to player.health,
            "foodLevel" to player.foodLevel,
            "level" to player.level,
            "activePotionEffects" to player.activePotionEffects.size
        )

        // Clear potion effects first
        debugLogger.debug("Clearing potion effects")
        clearPotionEffects(player)

        // Clear inventory
        debugLogger.debug("Clearing inventory")
        player.inventory.clear()
        player.inventory.setArmorContents(arrayOfNulls(4))
        player.inventory.setItemInOffHand(null)
        player.enderChest.clear()

        // Reset health to full
        debugLogger.debug("Resetting health to full")
        player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = 20.0
        player.health = 20.0

        // Reset hunger to full
        debugLogger.debug("Resetting hunger to full")
        player.foodLevel = 20
        player.saturation = 5.0f
        player.exhaustion = 0.0f

        // Reset experience
        debugLogger.debug("Resetting experience")
        player.level = 0
        player.exp = 0.0f

        // Reset gamemode to survival
        debugLogger.debug("Resetting gamemode to SURVIVAL")
        player.gameMode = GameMode.SURVIVAL

        // Update inventory to client
        player.updateInventory()

        logger.info("[WorldStateManager] Cleared state for '${player.name}' - fresh start applied")
        debugLogger.debugMethodExit("clearPlayerState", "success")
    }

    /**
     * Check if player has saved state for a world.
     *
     * @param playerUuid The player's UUID
     * @param worldName The Bukkit world name
     * @return True if saved state exists
     */
    fun hasStateForWorld(playerUuid: UUID, worldName: String): Boolean {
        debugLogger.debugMethodEntry("hasStateForWorld", "playerUuid" to playerUuid, "worldName" to worldName)
        val playerData = dataManager.loadPlayerData(playerUuid)
        val hasState = playerData?.worldStates?.containsKey(worldName) == true
        debugLogger.debug("State check result",
            "playerDataFound" to (playerData != null),
            "hasState" to hasState,
            "availableStates" to (playerData?.worldStates?.keys?.toList() ?: emptyList())
        )
        debugLogger.debugMethodExit("hasStateForWorld", hasState)
        return hasState
    }

    /**
     * Get saved location for a world, if any.
     *
     * @param playerUuid The player's UUID
     * @param worldName The Bukkit world name
     * @return The saved location, or null if none
     */
    fun getSavedLocation(playerUuid: UUID, worldName: String): LocationData? {
        debugLogger.debugMethodEntry("getSavedLocation", "playerUuid" to playerUuid, "worldName" to worldName)
        val playerData = dataManager.loadPlayerData(playerUuid)
        val location = playerData?.worldStates?.get(worldName)?.location
        debugLogger.debug("Location lookup result",
            "found" to (location != null),
            "x" to location?.x,
            "y" to location?.y,
            "z" to location?.z
        )
        debugLogger.debugMethodExit("getSavedLocation", location != null)
        return location
    }

    /**
     * Clear all player world states for a specific world.
     * Called when a world is deleted to prevent stale data from persisting.
     *
     * @param worldName The Bukkit world name (e.g., "prodeathmaster_test")
     */
    fun clearWorldStatesForWorld(worldName: String) {
        debugLogger.debugMethodEntry("clearWorldStatesForWorld", "worldName" to worldName)
        logger.info("[WorldStateManager] Clearing all player states for world '$worldName'")

        var clearedCount = 0
        val allPlayerData = dataManager.getAllPlayerData()

        for (playerData in allPlayerData) {
            if (playerData.worldStates.containsKey(worldName)) {
                playerData.worldStates.remove(worldName)
                dataManager.savePlayerData(playerData)
                clearedCount++
                debugLogger.debug("Cleared world state",
                    "player" to playerData.username,
                    "worldName" to worldName
                )
            }
        }

        logger.info("[WorldStateManager] Cleared $clearedCount player state(s) for world '$worldName'")
        debugLogger.debugMethodExit("clearWorldStatesForWorld", "clearedCount" to clearedCount)
    }

    /**
     * Clear all player world states for a world and its dimensions (nether, end).
     *
     * @param baseWorldName The base world name (e.g., "prodeathmaster_test")
     */
    fun clearAllWorldStates(baseWorldName: String) {
        debugLogger.debugMethodEntry("clearAllWorldStates", "baseWorldName" to baseWorldName)

        // Clear overworld, nether, and end dimension states
        clearWorldStatesForWorld(baseWorldName)
        clearWorldStatesForWorld("${baseWorldName}_nether")
        clearWorldStatesForWorld("${baseWorldName}_the_end")

        debugLogger.debugMethodExit("clearAllWorldStates")
    }

    // ========================
    // Serialization Helpers
    // ========================

    /**
     * Serialize an inventory array to Base64.
     */
    private fun serializeInventory(items: Array<ItemStack?>?): String? {
        if (items == null || items.all { it == null || it.type.isAir }) {
            return null
        }

        return try {
            // Filter out null/air items and serialize using Paper API
            val nonNullItems = items.filterNotNull().filter { !it.type.isAir }
            if (nonNullItems.isEmpty()) {
                return null
            }

            val bytes = ItemStack.serializeItemsAsBytes(items)
            Base64.getEncoder().encodeToString(bytes)
        } catch (e: Exception) {
            logger.warning("[WorldStateManager] Failed to serialize inventory: ${e.message}")
            null
        }
    }

    /**
     * Deserialize an inventory array from Base64.
     */
    private fun deserializeInventory(base64: String?, expectedSize: Int = 36): Array<ItemStack?> {
        if (base64 == null) {
            return arrayOfNulls(expectedSize)
        }

        return try {
            val bytes = Base64.getDecoder().decode(base64)
            @Suppress("UNCHECKED_CAST")
            val items = ItemStack.deserializeItemsFromBytes(bytes) as Array<ItemStack?>
            // Ensure we return exactly the expected size
            when {
                items.size == expectedSize -> items
                items.size > expectedSize -> items.copyOfRange(0, expectedSize)
                else -> Array(expectedSize) { index -> items.getOrNull(index) }
            }
        } catch (e: Exception) {
            logger.warning("[WorldStateManager] Failed to deserialize inventory: ${e.message}")
            arrayOfNulls(expectedSize)
        }
    }

    /**
     * Deserialize armor contents from Base64.
     * Returns exactly 4 items (boots, leggings, chestplate, helmet).
     */
    private fun deserializeArmor(base64: String?): Array<ItemStack?> {
        if (base64 == null) {
            return arrayOfNulls(4)  // Default empty armor
        }

        return try {
            val bytes = Base64.getDecoder().decode(base64)
            @Suppress("UNCHECKED_CAST")
            val items = ItemStack.deserializeItemsFromBytes(bytes) as Array<ItemStack?>
            // Ensure we return exactly 4 items for armor
            if (items.size == 4) {
                items
            } else if (items.size > 4) {
                items.copyOfRange(0, 4)
            } else {
                // Pad with nulls if less than 4
                Array(4) { index -> items.getOrNull(index) }
            }
        } catch (e: Exception) {
            logger.warning("[WorldStateManager] Failed to deserialize armor: ${e.message}")
            arrayOfNulls(4)
        }
    }

    /**
     * Serialize a single item to Base64.
     */
    private fun serializeItem(item: ItemStack?): String? {
        if (item == null || item.type.isAir) {
            return null
        }

        return try {
            val bytes = item.serializeAsBytes()
            Base64.getEncoder().encodeToString(bytes)
        } catch (e: Exception) {
            logger.warning("[WorldStateManager] Failed to serialize item: ${e.message}")
            null
        }
    }

    /**
     * Deserialize a single item from Base64.
     */
    private fun deserializeItem(base64: String?): ItemStack? {
        if (base64 == null) {
            return null
        }

        return try {
            val bytes = Base64.getDecoder().decode(base64)
            ItemStack.deserializeBytes(bytes)
        } catch (e: Exception) {
            logger.warning("[WorldStateManager] Failed to deserialize item: ${e.message}")
            null
        }
    }

    /**
     * Serialize potion effects to JSON.
     */
    private fun serializePotionEffects(effects: Collection<PotionEffect>): String? {
        if (effects.isEmpty()) {
            return null
        }

        val serialized = effects.map { effect ->
            SerializedPotionEffect(
                type = effect.type.key.toString(),
                duration = effect.duration,
                amplifier = effect.amplifier,
                ambient = effect.isAmbient,
                particles = effect.hasParticles(),
                icon = effect.hasIcon()
            )
        }

        return gson.toJson(serialized)
    }

    /**
     * Restore potion effects from JSON.
     */
    private fun restorePotionEffects(player: Player, json: String?) {
        if (json == null) {
            return
        }

        try {
            val type = object : TypeToken<List<SerializedPotionEffect>>() {}.type
            val effects: List<SerializedPotionEffect> = gson.fromJson(json, type)

            effects.forEach { serialized ->
                val effectType = PotionEffectType.getByKey(
                    org.bukkit.NamespacedKey.fromString(serialized.type)
                )

                if (effectType != null) {
                    val effect = PotionEffect(
                        effectType,
                        serialized.duration,
                        serialized.amplifier,
                        serialized.ambient,
                        serialized.particles,
                        serialized.icon
                    )
                    player.addPotionEffect(effect)
                }
            }
        } catch (e: Exception) {
            logger.warning("[WorldStateManager] Failed to restore potion effects: ${e.message}")
        }
    }

    /**
     * Clear all potion effects from player.
     */
    private fun clearPotionEffects(player: Player) {
        player.activePotionEffects.forEach { effect ->
            player.removePotionEffect(effect.type)
        }
    }
}
