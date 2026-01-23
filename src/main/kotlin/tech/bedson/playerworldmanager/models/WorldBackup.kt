package tech.bedson.playerworldmanager.models

import java.util.UUID

/**
 * Data class representing a world backup.
 */
data class WorldBackup(
    val id: UUID,                          // Unique backup ID
    val worldId: UUID,                     // ID of the world that was backed up
    val worldName: String,                 // Name of the world at backup time
    val ownerUuid: UUID,                   // Owner's UUID at backup time
    val ownerName: String,                 // Owner's name at backup time
    val createdAt: Long,                   // Timestamp when backup was created
    val backupPath: String,                // Relative path to backup folder (from plugin data folder)
    val sizeBytes: Long = 0,               // Size of backup in bytes
    val description: String? = null,       // Optional description/label for backup
    val isAutomatic: Boolean = false       // Whether this was an automatic scheduled backup
) {
    /**
     * Returns a debug-friendly string representation of this backup.
     */
    fun toDebugString(): String {
        return "WorldBackup(id=$id, worldId=$worldId, worldName=$worldName, " +
                "owner=$ownerName, createdAt=$createdAt, path=$backupPath, " +
                "size=$sizeBytes, automatic=$isAutomatic)"
    }

    /**
     * Returns a compact debug string for logging.
     */
    fun toCompactDebugString(): String {
        return "WorldBackup[$worldName, id=${id.toString().take(8)}, created=$createdAt]"
    }

    /**
     * Returns a human-readable size string.
     */
    fun getHumanReadableSize(): String {
        return when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
            sizeBytes < 1024 * 1024 * 1024 -> "${sizeBytes / (1024 * 1024)} MB"
            else -> "${sizeBytes / (1024 * 1024 * 1024)} GB"
        }
    }
}

/**
 * Data class for tracking backup schedule configuration per world.
 */
data class BackupSchedule(
    val worldId: UUID,                     // ID of the world
    val enabled: Boolean = false,          // Whether scheduled backups are enabled
    val intervalMinutes: Int = 1440,       // Interval between backups (default: 24 hours = 1440 minutes)
    val lastBackupTime: Long = 0           // Timestamp of last automatic backup
)
