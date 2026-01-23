package tech.bedson.playerworldmanager.models

import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.utils.DebugLogger

/**
 * Stores a player's complete state for a specific world.
 * This includes inventory, health, hunger, XP, and potion effects.
 *
 * All ItemStack data is stored as Base64-encoded NBT bytes using Paper's
 * ItemStack.serializeAsBytes() for safe data migration.
 */
data class PlayerWorldState(
    val worldName: String,                      // Bukkit world name this state belongs to

    // Inventory (Base64 encoded NBT bytes)
    var inventoryContents: String? = null,      // Main inventory (36 slots)
    var armorContents: String? = null,          // Armor (4 slots)
    var offHandItem: String? = null,            // Offhand item
    var enderChestContents: String? = null,     // Ender chest (27 slots)

    // Health and hunger
    var health: Double = 20.0,
    var maxHealth: Double = 20.0,
    var foodLevel: Int = 20,
    var saturation: Float = 5.0f,
    var exhaustion: Float = 0.0f,

    // Experience
    var expLevel: Int = 0,
    var expProgress: Float = 0.0f,              // 0.0 to 1.0 progress to next level

    // Potion effects (JSON serialized list)
    var potionEffects: String? = null,          // Serialized potion effects

    // Location (for convenience, though we also store in worldLocations)
    var location: LocationData? = null
) {
    companion object {
        /**
         * Create a PlayerWorldState with debug logging.
         */
        fun createWithLogging(
            plugin: JavaPlugin,
            worldName: String,
            health: Double = 20.0,
            maxHealth: Double = 20.0,
            foodLevel: Int = 20,
            expLevel: Int = 0
        ): PlayerWorldState {
            val debugLogger = DebugLogger(plugin, "PlayerWorldState")
            debugLogger.debug("Creating PlayerWorldState",
                "worldName" to worldName,
                "health" to health,
                "maxHealth" to maxHealth,
                "foodLevel" to foodLevel,
                "expLevel" to expLevel
            )
            return PlayerWorldState(
                worldName = worldName,
                health = health,
                maxHealth = maxHealth,
                foodLevel = foodLevel,
                expLevel = expLevel
            )
        }
    }

    /**
     * Returns a debug-friendly string representation.
     */
    fun toDebugString(): String {
        return "PlayerWorldState(world=$worldName, health=$health/$maxHealth, " +
                "food=$foodLevel, saturation=$saturation, exp=Lv$expLevel+${(expProgress * 100).toInt()}%, " +
                "hasInventory=${inventoryContents != null}, hasArmor=${armorContents != null}, " +
                "hasOffhand=${offHandItem != null}, hasEnderChest=${enderChestContents != null}, " +
                "hasPotionEffects=${potionEffects != null}, hasLocation=${location != null})"
    }

    /**
     * Returns a compact debug string for logging.
     */
    fun toCompactDebugString(): String {
        return "PlayerWorldState[$worldName: HP=$health, Food=$foodLevel, Lv$expLevel]"
    }

    /**
     * Returns a summary of what data is present in this state.
     */
    fun toDataSummary(): String {
        val items = mutableListOf<String>()
        if (inventoryContents != null) items.add("inv")
        if (armorContents != null) items.add("armor")
        if (offHandItem != null) items.add("offhand")
        if (enderChestContents != null) items.add("ender")
        if (potionEffects != null) items.add("effects")
        if (location != null) items.add("loc")
        return if (items.isEmpty()) "empty" else items.joinToString(",")
    }
}

/**
 * Serialized potion effect data.
 */
data class SerializedPotionEffect(
    val type: String,           // PotionEffectType key (e.g., "minecraft:speed")
    val duration: Int,          // Duration in ticks
    val amplifier: Int,         // Effect level (0 = level 1)
    val ambient: Boolean,       // Whether particles are less visible
    val particles: Boolean,     // Whether to show particles
    val icon: Boolean           // Whether to show icon
) {
    /**
     * Returns a debug-friendly string representation.
     */
    fun toDebugString(): String {
        return "PotionEffect(type=$type, duration=$duration, amp=$amplifier, " +
                "ambient=$ambient, particles=$particles, icon=$icon)"
    }

    /**
     * Returns a compact debug string for logging.
     */
    fun toCompactDebugString(): String {
        val durationSecs = duration / 20
        return "$type Lv${amplifier + 1} (${durationSecs}s)"
    }
}
