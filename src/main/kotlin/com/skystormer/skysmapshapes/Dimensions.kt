package com.skystormer.skysmapshapes

import net.minecraft.client.Minecraft
import xaero.map.WorldMapSession

/**
 * Which dimension a thing belongs to, and what to call it.
 *
 * Shapes are kept per dimension by its id (`minecraft:the_nether`), and read out to people by its
 * name ("Nether"), including in the lines shared in chat, which is why both directions live here.
 */
object Dimensions {

    const val OVERWORLD = "minecraft:overworld"
    const val NETHER = "minecraft:the_nether"
    const val END = "minecraft:the_end"

    /** "Nether" for `minecraft:the_nether`; anything else keeps its own id, without `minecraft:`. */
    fun name(id: String): String = when (id) {
        OVERWORLD -> "Overworld"
        NETHER -> "Nether"
        END -> "End"
        else -> id.removePrefix("minecraft:")
    }

    /** The id behind a name from [name], for reading a shared line back. */
    fun id(name: String): String = when (name) {
        "Overworld" -> OVERWORLD
        "Nether" -> NETHER
        "End" -> END
        else -> if (':' in name) name else "minecraft:$name"
    }

    /** The dimension you are standing in, which is the only one MiniHUD holds shapes for. */
    fun ofPlayer(): String? =
        Minecraft.getInstance().player?.level()?.dimension()?.identifier()?.toString()

    /** The dimension Xaero's world map is showing, which is not always the one you are in. */
    fun ofMap(): String? =
        WorldMapSession.getCurrentSession()?.mapProcessor?.mapWorld?.currentDimension?.dimId?.identifier()?.toString()
}
