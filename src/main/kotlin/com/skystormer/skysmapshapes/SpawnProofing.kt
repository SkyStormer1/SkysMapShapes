package com.skystormer.skysmapshapes

import java.lang.reflect.Field
import java.lang.reflect.Method
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.BasePressurePlateBlock
import net.minecraft.world.level.block.BaseRailBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.level.block.CarpetBlock
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.TrapDoorBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.AttachFace
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.Half
import net.minecraft.world.level.block.state.properties.SlabType

/**
 * Spawn proofing with Litematica's Easy Place: every floor MiniHUD's light level overlay marks,
 * where you have said it is a problem, gets a schematic block on it (a bottom slab, a carpet, a
 * button: one of the [SpawnProofBlocks] you picked), so Easy Place puts it there in a click.
 *
 * Where it is a problem: inside the shapes you set to spawn-proof (right-click one on the map),
 * or everywhere the overlay shows with [Config.spawnProofEverywhere]. A marked spot counts when
 * its block light is no more than the dimension lets monsters spawn at: 0 in the Overworld and
 * the End, so a lit spot is fine; every light in the Nether, so there every marked floor counts,
 * and which floors are marked there at all follows the biome's mobs ([NetherSpawns]).
 *
 * It follows MiniHUD's overlay: it comes and goes with MiniHUD's light level renderer (and so its
 * hotkey), and reaches only as far as MiniHUD looks. [Config.spawnProofRange] keeps it nearer, to
 * go easy on the game.
 *
 * The spots are copied out as MiniHUD gathers them (`OverlayRendererLightLevelMixin` calls
 * [gather]); the placement is rebuilt on the client tick, at most twice a second and only when the
 * spots or the block change. MiniHUD and Litematica are both optional and reached only when
 * installed; nothing of either is copied here.
 */
object SpawnProofing {

    val installed: Boolean by lazy {
        MiniHudShapes.installed && FabricLoader.getInstance().isModLoaded("litematica")
    }

    /** Whether spawn proofing is switched on (it still waits for MiniHUD's overlay to be on). */
    val active: Boolean get() = Config.enabled && Config.spawnProofing && installed

    /** The spots to cover, packed block positions, as last gathered. Written on the render thread. */
    @Volatile
    private var gathered: LongArray = LongArray(0)

    /** What the placement holds now, to rebuild only on a change. */
    private var shownSpots: LongArray? = null
    private var shownState: BlockState? = null

    /** The block in use, kept while you hold something else so the schematic does not flicker. */
    private var current: Block? = null

    private var broken = false
    private var seen = false
    private var ticks = 0
    private var wasOn = false

    private var spotsField: Field? = null
    private var posField: Field? = null
    private var blockField: Field? = null
    private var overlayOn: Pair<Any, Method>? = null
    private var mainOn: Pair<Any, Method>? = null

    /**
     * Copies the spots worth covering out of MiniHUD's [overlay], just after it gathered them (and
     * after [LightLevels.filter] kept them to inside shapes, when it does).
     */
    @JvmStatic
    fun gather(overlay: Any) {
        if (broken || !active) return
        try {
            val minecraft = Minecraft.getInstance()
            val level = minecraft.level ?: return
            val player = minecraft.player ?: return
            val dimension = Dimensions.ofPlayer() ?: return
            val volumes = volumes(dimension)
            if (volumes.isEmpty() && !Config.spawnProofEverywhere) {
                gathered = LongArray(0)
                return
            }
            @Suppress("UNCHECKED_CAST")
            val spots = (spotsField ?: overlay.javaClass.getDeclaredField(SPOTS).also { it.isAccessible = true; spotsField = it })
                .get(overlay) as List<Any?>
            val limit = level.dimensionType().monsterSpawnBlockLightLimit()
            val range = Config.spawnProofRange
            val px = player.blockX
            val py = player.blockY
            val pz = player.blockZ
            val pos = BlockPos.MutableBlockPos()
            val out = ArrayList<Long>()
            for (spot in spots) {
                spot ?: continue
                val packed = (posField ?: spot.javaClass.getField(POS).also { posField = it }).getLong(spot)
                val light = (blockField ?: spot.javaClass.getField(BLOCK_LIGHT).also { blockField = it }).getByte(spot).toInt()
                if (light > limit) continue
                val x = BlockPos.getX(packed)
                val y = BlockPos.getY(packed)
                val z = BlockPos.getZ(packed)
                if (Math.abs(x - px) > range || Math.abs(y - py) > range || Math.abs(z - pz) > range) continue
                if (!Config.spawnProofEverywhere && volumes.none { it.contains(x, y, z) }) continue
                // Only where a block can go: not into water, not where something already stands.
                val state = level.getBlockState(pos.set(x, y, z))
                if (!state.canBeReplaced() || !state.fluidState.isEmpty) continue
                out += packed
            }
            out.sort()
            gathered = out.toLongArray()
            if (!seen) {
                seen = true
                Log.info("Spawn proofing: {} spot(s) to cover", out.size)
            }
        } catch (e: Throwable) {
            broken = true
            Log.error("Could not read MiniHUD's light levels for spawn proofing; it is off until the game restarts", e)
        }
    }

    /** The shapes in [dimension] set to spawn-proof, as MiniHUD's light levels count them. */
    private fun volumes(dimension: String): List<Volume> {
        val out = ArrayList<Volume>()
        for (shape in ShapeStore.inDimension(dimension)) {
            if (shape.visible && ShapeStore.spawnProof(shape.id)) out += Volume.Column(shape.geometry)
        }
        for (shape in MiniHudShapes.inDimension(dimension)) {
            if (shape.enabledInMiniHud && ShapeStore.spawnProof(shape.id)) shape.volume?.let { out += it }
        }
        return out
    }

    /** Called every client tick: brings the placement up to date with the spots and the block. */
    fun tick() {
        if (broken || !installed) return
        ticks = (ticks + 1) % CHECK_EVERY
        if (ticks != 0) return
        val on = try {
            active && Minecraft.getInstance().player != null && overlayShowing()
        } catch (e: Throwable) {
            broken = true
            Log.error("Could not tell whether MiniHUD's light levels are on; spawn proofing is off until the game restarts", e)
            false
        }
        if (!on) {
            wasOn = false
            clear()
            return
        }
        if (!wasOn) {
            // MiniHUD may not gather again until you move; ask it to now.
            wasOn = true
            LightLevels.refresh()
        }
        val spots = gathered
        val state = chooseBlock()?.let(SpawnProofBlocks::stateFor)
        if (state == null || spots.isEmpty()) {
            clear()
            return
        }
        if (state == shownState && spots.contentEquals(shownSpots)) return
        try {
            LitematicaPlacement.show(spots, state)
            shownSpots = spots
            shownState = state
        } catch (e: Throwable) {
            broken = true
            Log.error("Could not make Litematica's spawn proofing placement; spawn proofing is off until the game restarts", e)
            clear()
        }
    }

    /** Takes the placement away, as when the overlay is switched off or you leave. */
    fun clear() {
        if (shownSpots == null) return
        shownSpots = null
        shownState = null
        try {
            LitematicaPlacement.clear()
        } catch (e: Throwable) {
            Log.error("Could not remove Litematica's spawn proofing placement", e)
        }
    }

    /** Called when you leave a world: nothing gathered there is any use in the next. */
    fun reset() {
        clear()
        gathered = LongArray(0)
    }

    /** Asks for the spots again, as after a change to what counts. */
    fun refresh() {
        if (installed) LightLevels.refresh()
    }

    /**
     * The block to put down: the one in your hand when it is one you picked; else the one used
     * last, while you still have some; else the picked block you have most of; else the first picked.
     */
    private fun chooseBlock(): Block? {
        val picked = SpawnProofBlocks.picked()
        if (picked.isEmpty()) return null
        val player = Minecraft.getInstance().player ?: return null
        val held = (player.mainHandItem.item as? BlockItem)?.block
        if (held != null && held in picked) {
            current = held
            return held
        }
        val counts = HashMap<Block, Int>()
        val inventory = player.inventory
        for (slot in 0 until inventory.containerSize) {
            val stack = inventory.getItem(slot)
            val block = (stack.item as? BlockItem)?.block ?: continue
            if (block in picked) counts.merge(block, stack.count, Int::plus)
        }
        current?.let { if (it in picked && (counts[it] ?: 0) > 0) return it }
        val best = counts.maxByOrNull { it.value }?.key ?: SpawnProofBlocks.candidates().first { it in picked }
        current = best
        return best
    }

    /** Whether MiniHUD is drawing its light level overlay: its renderer on, and MiniHUD's rendering on. */
    private fun overlayShowing(): Boolean {
        val (toggle, overlay) = overlayOn ?: run {
            val toggles = Class.forName(RENDERER_TOGGLE)
            (toggles.getField(LIGHT_TOGGLE).get(null) to toggles.getMethod("getBooleanValue")).also { overlayOn = it }
        }
        val (main, mainValue) = mainOn ?: run {
            val generic = Class.forName(GENERIC)
            val field = generic.getField(MAIN_TOGGLE).get(null)
            (field to field.javaClass.getMethod("getBooleanValue")).also { mainOn = it }
        }
        return overlay.invoke(toggle) as Boolean && mainValue.invoke(main) as Boolean
    }

    private const val RENDERER_TOGGLE = "fi.dy.masa.minihud.config.RendererToggle"
    private const val LIGHT_TOGGLE = "OVERLAY_LIGHT_LEVEL"
    private const val GENERIC = "fi.dy.masa.minihud.config.Configs\$Generic"
    private const val MAIN_TOGGLE = "MAIN_RENDERING_TOGGLE"

    /** MiniHUD's list of spots, and each spot's packed block position and block light. */
    private const val SPOTS = "lightInfos"
    private const val POS = "pos"
    private const val BLOCK_LIGHT = "block"

    /** Ticks between updates: twice a second, as often as MiniHUD's own are read. */
    private const val CHECK_EVERY = 10
}

/**
 * The blocks spawn proofing can put down, in the order the settings list them, and which of them
 * you picked ([Config.spawnProofBlocks], block ids; until you pick, every slab).
 */
object SpawnProofBlocks {

    enum class Kind(val title: String) {
        SLABS("Slabs"),
        TRAPDOORS("Trapdoors"),
        BUTTONS("Buttons"),
        CARPETS("Carpets"),
        PRESSURE_PLATES("Pressure plates"),
        RAILS("Rails"),
    }

    fun kindOf(block: Block): Kind? = when (block) {
        is SlabBlock -> Kind.SLABS
        is TrapDoorBlock -> Kind.TRAPDOORS
        is ButtonBlock -> Kind.BUTTONS
        is CarpetBlock -> Kind.CARPETS
        is BasePressurePlateBlock -> Kind.PRESSURE_PLATES
        is BaseRailBlock -> Kind.RAILS
        else -> null
    }

    private var all: List<Block>? = null

    /** Every block that can be picked, by kind, then by name. */
    fun candidates(): List<Block> = all ?: BuiltInRegistries.BLOCK
        .filter { kindOf(it) != null && it.asItem() != Items.AIR }
        .sortedWith(compareBy<Block>({ kindOf(it)!!.ordinal }, { it.name.string }))
        .also { all = it }

    fun id(block: Block): String = BuiltInRegistries.BLOCK.getKey(block).toString()

    /** Every slab: what is picked until you choose. */
    fun defaults(): Set<String> = candidates().filter { kindOf(it) == Kind.SLABS }.mapTo(HashSet(), ::id)

    private var pickedFor: Set<String>? = null
    private var pickedBlocks: Set<Block> = emptySet()

    fun picked(): Set<Block> {
        val ids = Config.spawnProofBlocks ?: defaults()
        if (ids !== pickedFor) {
            pickedFor = ids
            pickedBlocks = candidates().filterTo(HashSet()) { id(it) in ids }
        }
        return pickedBlocks
    }

    /** How [block] goes down to spawn-proof: a bottom slab, a shut trapdoor at the bottom, a button on the floor. */
    fun stateFor(block: Block): BlockState {
        var state = block.defaultBlockState()
        with(BlockStateProperties.SLAB_TYPE) {
            if (state.hasProperty(this)) state = state.setValue(this, SlabType.BOTTOM)
        }
        with(BlockStateProperties.HALF) {
            if (state.hasProperty(this)) state = state.setValue(this, Half.BOTTOM)
        }
        with(BlockStateProperties.OPEN) {
            if (state.hasProperty(this)) state = state.setValue(this, false)
        }
        with(BlockStateProperties.ATTACH_FACE) {
            if (state.hasProperty(this)) state = state.setValue(this, AttachFace.FLOOR)
        }
        with(BlockStateProperties.WATERLOGGED) {
            if (state.hasProperty(this)) state = state.setValue(this, false)
        }
        return state
    }
}
