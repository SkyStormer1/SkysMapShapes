package com.skystormer.skysmapshapes

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState

/**
 * Which floor blocks MiniHUD's light level overlay marks in the Nether, by biome.
 *
 * MiniHUD asks whether a creeper could stand on a block, which suits the Overworld. In the Nether
 * the mobs are different and each biome has its own: a floor is marked here when any mob that
 * spawns in that biome could stand on it. So magma counts where fire-immune mobs spawn (zombified
 * piglins, magma cubes, ghasts, blazes, wither skeletons), and red nether wart blocks do not count
 * in a crimson forest, where every mob is a piglin, a zombified piglin or a hoglin, none of which
 * spawn on them.
 *
 * Fortresses: the server does not send where they are, but their mobs add nothing here. Blazes
 * and wither skeletons take every floor a zombified piglin or a magma cube does.
 *
 * Light is left to MiniHUD's colours. Of the Nether's mobs only wither skeletons, skeletons and
 * endermen need it dark (block light 7 or less); the rest spawn at any light.
 *
 * The lists follow vanilla's biome files and spawn rules. Biomes from other mods, and every other
 * dimension, keep MiniHUD's own answer.
 */
object NetherSpawns {

    private val wastes = listOf(EntityTypes.GHAST, EntityTypes.ZOMBIFIED_PIGLIN, EntityTypes.MAGMA_CUBE, EntityTypes.ENDERMAN, EntityTypes.PIGLIN)
    private val crimson = listOf(EntityTypes.ZOMBIFIED_PIGLIN, EntityTypes.HOGLIN, EntityTypes.PIGLIN)
    private val warped = listOf(EntityTypes.ENDERMAN)
    private val soulSand = listOf(EntityTypes.SKELETON, EntityTypes.GHAST, EntityTypes.ENDERMAN)
    private val deltas = listOf(EntityTypes.GHAST, EntityTypes.MAGMA_CUBE)

    private val biomes: Map<ResourceKey<Biome>, List<EntityType<*>>> = mapOf(
        Biomes.NETHER_WASTES to wastes,
        Biomes.CRIMSON_FOREST to crimson,
        Biomes.WARPED_FOREST to warped,
        Biomes.SOUL_SAND_VALLEY to soulSand,
        Biomes.BASALT_DELTAS to deltas,
    )

    /** Mobs whose own spawn rule refuses a red nether wart block under them. */
    private val notOnWart: Set<EntityType<*>> = setOf(EntityTypes.ZOMBIFIED_PIGLIN, EntityTypes.PIGLIN, EntityTypes.HOGLIN)

    val active: Boolean get() = Config.enabled && Config.netherBiomeSpawns

    private var seen = false

    /**
     * Whether MiniHUD should count [state] at [pos] as a floor mobs can spawn on. [asked] is the
     * mob MiniHUD asks about; outside the Nether, or with the setting off, it gets that answer.
     */
    @JvmStatic
    fun floor(state: BlockState, level: BlockGetter, pos: BlockPos, asked: EntityType<*>): Boolean {
        // MiniHUD asks this for every block near you, and looking up a biome is slow. Only magma and
        // red nether wart blocks are allowed for some of the Nether's mobs and not others; every
        // other floor all of them take or none do, the same as MiniHUD's creeper.
        if (!state.`is`(Blocks.MAGMA_BLOCK) && !state.`is`(Blocks.NETHER_WART_BLOCK)) return state.isValidSpawn(level, pos, asked)
        val mobs = mobsAt(pos) ?: return state.isValidSpawn(level, pos, asked)
        if (!seen) {
            seen = true
            Log.info("Marking MiniHUD's light levels by the Nether's biomes")
        }
        return mobs.any { mob ->
            state.isValidSpawn(level, pos, mob) && !(mob in notOnWart && state.`is`(Blocks.NETHER_WART_BLOCK))
        }
    }

    /** The mobs that could spawn at [pos], or null to leave it to MiniHUD. */
    private fun mobsAt(pos: BlockPos): List<EntityType<*>>? {
        if (!active) return null
        val world = Minecraft.getInstance().level ?: return null
        if (world.dimension() != Level.NETHER) return null
        val biome = world.getBiome(pos.above())
        return biomes.entries.firstOrNull { biome.`is`(it.key) }?.value
    }
}
