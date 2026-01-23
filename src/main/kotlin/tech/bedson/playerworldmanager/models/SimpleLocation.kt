package tech.bedson.playerworldmanager.models

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World

/**
 * Serializable location class (Bukkit Location is not serializable).
 */
data class SimpleLocation(
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float = 0f,
    val pitch: Float = 0f
) {
    /**
     * Returns a debug-friendly string representation.
     */
    fun toDebugString(): String {
        return "SimpleLocation(x=%.2f, y=%.2f, z=%.2f, yaw=%.1f, pitch=%.1f)".format(x, y, z, yaw, pitch)
    }

    /**
     * Returns a compact coordinate string for logging.
     */
    fun toCompactString(): String {
        return "(%.1f, %.1f, %.1f)".format(x, y, z)
    }
}

/**
 * Convert SimpleLocation to Bukkit Location.
 * @param world The world to create the location in
 * @return Bukkit Location object
 */
fun SimpleLocation.toBukkitLocation(world: World): Location {
    return Location(world, x, y, z, yaw, pitch)
}

/**
 * Convert SimpleLocation to Bukkit Location using world name.
 * @param worldName The name of the world
 * @return Bukkit Location object, or null if world doesn't exist
 */
fun SimpleLocation.toBukkitLocation(worldName: String): Location? {
    val world = Bukkit.getWorld(worldName) ?: return null
    return Location(world, x, y, z, yaw, pitch)
}

/**
 * Convert Bukkit Location to SimpleLocation.
 * @return SimpleLocation object
 */
fun Location.toSimpleLocation(): SimpleLocation {
    return SimpleLocation(x, y, z, yaw, pitch)
}
