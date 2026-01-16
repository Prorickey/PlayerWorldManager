package tech.bedson.playerworldmanager.utils

import org.bukkit.generator.ChunkGenerator
import org.bukkit.generator.WorldInfo
import java.util.Random

/**
 * Chunk generator for void worlds.
 * Generates completely empty chunks with no blocks.
 */
class VoidGenerator : ChunkGenerator() {

    /**
     * Generate chunk data - returns empty data for void world.
     */
    override fun generateNoise(
        worldInfo: WorldInfo,
        random: Random,
        chunkX: Int,
        chunkZ: Int,
        chunkData: ChunkData
    ) {
        // Do nothing - leave chunk completely empty
    }

    /**
     * Don't generate any surface or bedrock.
     */
    override fun shouldGenerateSurface(): Boolean {
        return false
    }

    /**
     * Don't generate any bedrock floor.
     */
    override fun shouldGenerateBedrock(): Boolean {
        return false
    }

    /**
     * Don't generate caves.
     */
    override fun shouldGenerateCaves(): Boolean {
        return false
    }

    /**
     * Don't generate decorations.
     */
    override fun shouldGenerateDecorations(): Boolean {
        return false
    }

    /**
     * Don't generate mobs.
     */
    override fun shouldGenerateMobs(): Boolean {
        return false
    }

    /**
     * Don't generate structures.
     */
    override fun shouldGenerateStructures(): Boolean {
        return false
    }
}
