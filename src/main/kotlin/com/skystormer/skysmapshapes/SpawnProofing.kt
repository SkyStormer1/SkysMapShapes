package com.skystormer.skysmapshapes

import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.longs.LongSet
import java.lang.reflect.Method
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.core.Direction
import kotlin.math.roundToInt
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput
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
import net.minecraft.world.phys.Vec3

/**
 * Spawn proofing with Litematica's Easy Place: every floor MiniHUD's light level overlay marks,
 * where you have said it is a problem, gets a schematic block on it (a bottom slab, a carpet, a
 * button: one of the [SpawnProofBlocks] you accepted), so Easy Place puts it there in a click.
 *
 * Where it is a problem: inside the shapes you set to spawn-proof (right-click one on the map),
 * or everywhere the overlay shows with [Config.spawnProofEverywhere]. A marked spot counts when
 * its block light is no more than the dimension lets monsters spawn at: 0 in the Overworld and
 * the End, so a lit spot is fine; every light in the Nether, so there every marked floor counts,
 * and which floors are marked there at all follows the biome's mobs ([NetherSpawns]).
 *
 * It follows MiniHUD's overlay: it comes and goes with MiniHUD's light level renderer (and so its
 * hotkey), and reaches as far as MiniHUD looks (its light level range, 24 blocks unless changed,
 * at most 64). [Config.spawnProofRange] can keep it nearer, to go easy on the game. With
 * [Config.spawnProofRenderThrough], each patch of spots also gets one outline drawn through blocks
 * ([drawOutlines], [SpawnOutlines]).
 *
 * Going to Easy Place a spot with a block that is not accepted swaps an accepted one into your
 * hand ([beforeUse]).
 *
 * The spots are copied out as MiniHUD gathers them (`OverlayRendererLightLevelMixin` calls
 * [gather]); the placement is brought up to date on the client tick, at most twice a second and
 * only when the spots or the block change ([LitematicaPlacement]). MiniHUD and Litematica are both optional and reached only when
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

    /** The dimension [gathered] is in: spots from the one you just left are no use in the next. */
    @Volatile
    private var gatheredIn: String? = null

    /**
     * What the placement holds now, to rebuild only on a change. Sorted. Also read by Litematica's
     * chunk builder threads, through [coversLitematicaOverlay].
     */
    @Volatile
    private var shownSpots: LongArray? = null

    /**
     * The sections [shownSpots] fall in ([SpotSections]), so Litematica's whole-box layer checks can
     * be answered in a few lookups. Replaced, never changed, so the builder threads can read it.
     */
    @Volatile
    private var shownSections: LongSet? = null

    private var shownState: BlockState? = null

    /** The block in use, kept while you hold something else so the schematic does not flicker. */
    private var current: Block? = null

    private var broken = false
    private var seen = false
    private var ticks = 0
    private var wasOn = false

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
                gatheredIn = dimension
                return
            }
            val spots = MiniHudLight.spots(overlay)
            val limit = level.dimensionType().monsterSpawnBlockLightLimit()
            // At the slider's top it reaches as far as MiniHUD looks.
            val range = if (Config.spawnProofRange >= Config.MAX_SPAWN_PROOF_RANGE) Int.MAX_VALUE else Config.spawnProofRange
            val px = player.blockX
            val py = player.blockY
            val pz = player.blockZ
            val pos = BlockPos.MutableBlockPos()
            val out = LongArrayList()
            for (spot in spots) {
                spot ?: continue
                val packed = MiniHudLight.pos(spot)
                val light = MiniHudLight.blockLight(spot)
                if (light > limit) continue
                val x = BlockPos.getX(packed)
                val y = BlockPos.getY(packed)
                val z = BlockPos.getZ(packed)
                if (Math.abs(x - px) > range || Math.abs(y - py) > range || Math.abs(z - pz) > range) continue
                if (!Config.spawnProofEverywhere && volumes.none { it.contains(x, y, z) }) continue
                // Only where a block can go: not into water, not where something already stands.
                val state = level.getBlockState(pos.set(x, y, z))
                if (!state.canBeReplaced() || !state.fluidState.isEmpty) continue
                out.add(packed)
            }
            gathered = out.toLongArray().also { it.sort() }
            gatheredIn = dimension
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
    fun volumes(dimension: String): List<Volume> {
        return Volumes.inDimension(dimension) { ShapeStore.spawnProof(it) }.map { it.second }
    }

    /** Called every client tick: brings the placement up to date with the spots and the block. */
    fun tick() {
        if (broken || !installed) return
        ticks = (ticks + 1) % CHECK_EVERY
        if (ticks == 0) update()
        val wanted = Config.spawnProofRenderThrough
        if (wanted != outlineWanted) {
            outlineWanted = wanted
            outlineStale = true
        }
        if (outlineStale) updateOutlines()
    }

    /** The through-blocks outline's edges: worked out only when something changed, drawn every frame. */
    @Volatile
    private var outline: List<SpawnOutlines.Edge> = emptyList()

    /** The layer drawn on the spots still to cover, in place of Litematica's overlay there: its tops... */
    @Volatile
    private var fill: List<SpawnOutlines.Rect> = emptyList()

    /** ...and its sides, only around the outside of each patch, so touching tops show no seams. */
    @Volatile
    private var fillSides: List<SpawnOutlines.Edge> = emptyList()

    /** Whether [outline] needs working out again, and whether it was wanted last time. */
    private var outlineStale = false
    private var outlineWanted = false

    /**
     * Called by `ClientLevelMixin` for every block that changes. Only a change on one of the spots
     * matters: then the outline is worked out again on the next tick, so a spot you cover drops
     * out straight away, without waiting for MiniHUD to look again.
     */
    @JvmStatic
    fun blockChanged(pos: BlockPos) {
        val spots = shownSpots ?: return
        if (!outlineStale && holds(spots, pos.asLong())) outlineStale = true
    }

    /**
     * Called by `ChunkRendererSchematicVboMixin`, on Litematica's chunk builder threads, for each
     * block Litematica is about to draw its overlay box on: true for spawn proofing's own spots,
     * which get this mod's [fill] instead, in the colour, opacity and height you set. Your other
     * placements keep Litematica's overlay.
     */
    @JvmStatic
    fun coversLitematicaOverlay(pos: BlockPos): Boolean = holds(shownSpots ?: return false, pos.asLong())

    /** [coversLitematicaOverlay] for a block given as x, y, z. */
    @JvmStatic
    fun coversLitematicaOverlay(x: Int, y: Int, z: Int): Boolean = holds(shownSpots ?: return false, BlockPos.asLong(x, y, z))

    /**
     * Called by the render layer bypass hooks, on Litematica's chunk builder threads too: whether
     * the box from the min corner to the max one (both included) holds one of the spots. Litematica
     * then builds, shows or walks that box whatever your render layers say, and only the spots in
     * it are let through ([coversLitematicaOverlay]); everything else still follows your layers.
     */
    @JvmStatic
    fun litematicaBoxHasSpot(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean =
        SpotSections.anyIn(shownSections ?: return false, minX, minY, minZ, maxX, maxY, maxZ)

    /**
     * Whether a render layer bypass hook has let one of the spots through. Until then, render layers
     * that leave spots out are something [SpawnProofSetup] has to ask you to change.
     */
    @Volatile
    var layerBypassWorking = false
        private set

    private val bypassesLogged = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** Called by a render layer bypass hook each time it lets a spot through that your layers leave out. */
    @JvmStatic
    fun layerBypassUsed(what: String) {
        layerBypassWorking = true
        if (bypassesLogged.add(what)) Log.info("Render layer bypass working: {}", what)
    }

    /** The spots the placement holds now, sorted; null when there is none. */
    fun shown(): LongArray? = shownSpots

    /** Whether the sorted [spots] hold [packed]. */
    private fun holds(spots: LongArray, packed: Long): Boolean = java.util.Arrays.binarySearch(spots, packed) >= 0

    /**
     * Called by `ChunkRendererSchematicVboMixin` for each block Litematica is about to draw: true
     * to leave out its ghost block, when the setting hides Litematica's blocks on spawn proofing's
     * spots. Easy Place still sees them; only the drawing is skipped.
     */
    @JvmStatic
    fun hidesLitematicaBlock(pos: BlockPos): Boolean = !Config.spawnProofGhostBlocks && coversLitematicaOverlay(pos)

    /**
     * Called by `EasyPlaceUtilsMixin`: true for spawn proofing's own placement, which Easy Place then
     * leaves out when it decides whether you are aiming near a schematic. Its empty blocks (around
     * and between the spots) would otherwise stop you placing anything there but the slabs.
     */
    @JvmStatic
    fun isOwnPlacement(placement: Any?): Boolean =
        placement is fi.dy.masa.litematica.schematic.placement.SchematicPlacement && LitematicaPlacement.owns(placement)

    /** Draws spawn proofing's part of Litematica again, after a setting changed how it is drawn. */
    fun redrawLitematica() {
        if (!installed || broken) return
        try {
            LitematicaPlacement.redraw()
        } catch (e: Throwable) {
            Log.error("Could not ask Litematica to draw spawn proofing again", e)
        }
    }

    /**
     * Whether Litematica's overlay at [pos], where the schematic holds [schematicAir], belongs to
     * spawn proofing: one of its spots, or any block of yours inside its box where the placement
     * holds air (a spot already covered, or the ground around the spots), which Litematica would
     * mark as extra.
     */
    @JvmStatic
    fun ownsLitematicaOverlay(pos: BlockPos, schematicAir: Boolean): Boolean {
        if (coversLitematicaOverlay(pos)) return true
        return schematicAir && LitematicaPlacement.inBox(pos.x, pos.y, pos.z)
    }

    /**
     * Works out [fill] and [outline]: one layer over, and one line around, each patch of spots
     * still to cover. Done when the spots change (you moved, came into or went out of range, or
     * the overlay or block changed), when a block on a spot changes, or when a setting is
     * switched; never on a timer.
     */
    private fun updateOutlines() {
        outlineStale = false
        val spots = shownSpots
        val level = Minecraft.getInstance().level
        if (spots == null || level == null) {
            outline = emptyList()
            fill = emptyList()
            fillSides = emptyList()
            return
        }
        val pos = BlockPos.MutableBlockPos()
        val kept = LongArrayList(spots.size)
        for (spot in spots) if (level.getBlockState(pos.set(spot)).canBeReplaced()) kept.add(spot)
        val left = kept.toLongArray()
        val filled = left.isNotEmpty() && Config.spawnProofOpacity > 0f
        val edges = if (left.isNotEmpty() && (filled || Config.spawnProofRenderThrough)) SpawnOutlines.edges(left) else emptyList()
        outline = if (Config.spawnProofRenderThrough) edges else emptyList()
        fillSides = if (filled) edges else emptyList()
        fill = if (filled) SpawnOutlines.rectangles(left) else emptyList()
    }

    /**
     * Called as each frame is gathered: the [fill] on the spots still to cover, in the colour,
     * opacity and height set, and with [Config.spawnProofRenderThrough] the [outline] drawn
     * through blocks on the floor where the blocks go, so spots behind walls show. This mod's own
     * drawing; Litematica's settings and other placements are left alone.
     */
    fun drawOutlines() {
        if (broken) return
        val rects = fill
        val edges = outline
        if (rects.isEmpty() && edges.isEmpty()) return
        try {
            val rgb = Config.spawnProofColour and 0xFFFFFF
            if (rects.isNotEmpty()) {
                val style = GizmoStyle.fill(((Config.spawnProofOpacity * 255).roundToInt().coerceIn(0, 255) shl 24) or rgb)
                val height = Config.spawnProofHeight / 16.0
                for (rect in rects) {
                    val top = rect.y + height
                    Gizmos.rect(Vec3(rect.x1.toDouble(), top, rect.z1.toDouble()), Vec3(rect.x2.toDouble(), top, rect.z2.toDouble()), Direction.UP, style)
                }
                for (side in fillSides) {
                    val bottom = side.y.toDouble()
                    val top = side.y + height
                    Gizmos.rect(Vec3(side.x1.toDouble(), bottom, side.z1.toDouble()), Vec3(side.x2.toDouble(), bottom, side.z2.toDouble()),
                        Vec3(side.x2.toDouble(), top, side.z2.toDouble()), Vec3(side.x1.toDouble(), top, side.z1.toDouble()), style)
                }
            }
            val outlineColour = 0xFF000000.toInt() or rgb
            for (edge in edges) {
                val y = edge.y + LIFT
                Gizmos.line(Vec3(edge.x1.toDouble(), y, edge.z1.toDouble()), Vec3(edge.x2.toDouble(), y, edge.z2.toDouble()), outlineColour, OUTLINE_WIDTH)
                    .setAlwaysOnTop()
            }
        } catch (e: Throwable) {
            broken = true
            Log.error("Could not draw spawn proofing's outlines; spawn proofing is off until the game restarts", e)
        }
    }

    private fun update() {
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
        val spots = if (gatheredIn == Dimensions.ofPlayer()) gathered else LongArray(0)
        val state = chooseBlock()?.let(SpawnProofBlocks::stateFor)
        if (state == null || spots.isEmpty()) {
            clear()
            return
        }
        if (state == shownState && spots.contentEquals(shownSpots)) return
        try {
            // Known before Litematica draws them, so its builder threads see the new spots.
            val sections = SpotSections.of(spots)
            val newSections = sections != shownSections
            shownSpots = spots
            shownSections = sections
            shownState = state
            LitematicaPlacement.show(spots, state)
            // Litematica only works out again which chunks to draw when asked; with X or Z layers it matters.
            if (newSections) LitematicaPlacement.recheckVisibleChunks()
            outlineStale = true
        } catch (e: Throwable) {
            broken = true
            Log.error("Could not make Litematica's spawn proofing placement; spawn proofing is off until the game restarts", e)
            clear()
        }
    }

    /**
     * Called at the start of every client tick, before the game handles the use key: when you go
     * to Easy Place one of the spots holding a block that is not accepted (or nothing), the
     * accepted block the placement shows is put in your hand instead, from the hotbar or the rest
     * of your inventory. Anything else in your hand (a tool, food) is left alone.
     */
    fun beforeUse(minecraft: Minecraft) {
        if (broken) return
        val state = shownState ?: return
        val spots = shownSpots ?: return
        if (!minecraft.options.keyUse.isDown || minecraft.gui.screen() != null) return
        if (swapCooldown > 0) {
            swapCooldown--
            return
        }
        val player = minecraft.player ?: return
        val held = player.mainHandItem
        val heldBlock = (held.item as? BlockItem)?.block
        if (!held.isEmpty && heldBlock == null) return
        if (heldBlock != null && heldBlock in SpawnProofBlocks.accepted()) return
        try {
            if (!LitematicaPlacement.easyPlaceOn()) return
            if (!lookingAtSpot(minecraft, spots)) return
            swapCooldown = SWAP_COOLDOWN
            val inventory = player.inventory
            fun slotOf(wanted: (Block) -> Boolean) = (0 until Inventory.INVENTORY_SIZE).firstOrNull { i ->
                val stack = inventory.getItem(i)
                !stack.isEmpty && (stack.item as? BlockItem)?.block?.let(wanted) == true
            }
            // The block the placement shows; else any accepted one, and the placement follows it next tick.
            val slot = slotOf { it == state.block } ?: slotOf { it in SpawnProofBlocks.accepted() }?.also { ticks = CHECK_EVERY - 1 }
            when {
                slot == null -> player.sendOverlayMessage(Component.literal("No accepted blocks to spawn-proof with"))
                Inventory.isHotbarSlot(slot) -> inventory.setSelectedSlot(slot)
                else -> minecraft.gameMode?.handleContainerInput(player.inventoryMenu.containerId, slot, inventory.selectedSlot, ContainerInput.SWAP, player)
            }
        } catch (e: Throwable) {
            broken = true
            Log.error("Could not swap to a spawn proofing block; spawn proofing is off until the game restarts", e)
        }
    }

    private var swapCooldown = 0

    /** Whether you are looking at one of [spots] within reach, before your view hits a solid block. */
    private fun lookingAtSpot(minecraft: Minecraft, spots: LongArray): Boolean {
        val player = minecraft.player ?: return false
        val level = minecraft.level ?: return false
        val eye = player.getEyePosition(1f)
        val look = player.getViewVector(1f)
        val reach = player.blockInteractionRange()
        val pos = BlockPos.MutableBlockPos()
        var t = 0.0
        while (t <= reach) {
            pos.set(eye.x + look.x * t, eye.y + look.y * t, eye.z + look.z * t)
            if (holds(spots, pos.asLong())) return true
            if (!level.getBlockState(pos).canBeReplaced()) return false
            t += STEP
        }
        return false
    }

    /** Takes the placement away, as when the overlay is switched off or you leave. */
    fun clear() {
        if (shownSpots == null) return
        shownSpots = null
        shownSections = null
        shownState = null
        outlineStale = true
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
        gatheredIn = null
    }

    /** Asks for the spots again, as after a change to what counts. */
    fun refresh() {
        if (installed) LightLevels.refresh()
    }

    /**
     * The block to put down: the one in your hand when it is accepted; else the one used last,
     * while you still have some; else the accepted block you have most of; else the first accepted.
     */
    private fun chooseBlock(): Block? {
        val accepted = SpawnProofBlocks.accepted()
        if (accepted.isEmpty()) return null
        val player = Minecraft.getInstance().player ?: return null
        val held = (player.mainHandItem.item as? BlockItem)?.block
        if (held != null && held in accepted) {
            current = held
            return held
        }
        val counts = HashMap<Block, Int>()
        val inventory = player.inventory
        for (slot in 0 until inventory.containerSize) {
            val stack = inventory.getItem(slot)
            val block = (stack.item as? BlockItem)?.block ?: continue
            if (block in accepted) counts.merge(block, stack.count, Int::plus)
        }
        current?.let { if (it in accepted && (counts[it] ?: 0) > 0) return it }
        val best = counts.maxByOrNull { it.value }?.key ?: SpawnProofBlocks.candidates().first { it in accepted }
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

    /** Ticks between updates: twice a second, as often as MiniHUD's own are read. */
    private const val CHECK_EVERY = 10

    /** Ticks to wait after a swap, so holding the use key does not swap again before the server catches up. */
    private const val SWAP_COOLDOWN = 4

    /** The through-blocks outline: blue lines just above the floor where the blocks go. */
    private const val OUTLINE_WIDTH = 2f
    private const val LIFT = 0.02

    /** How finely the view is stepped along to find the spot you look at. */
    private const val STEP = 0.05
}

/**
 * The blocks spawn proofing can put down, in the order the settings list them, and which of them
 * you accepted ([Config.spawnProofBlocks], block ids; until you choose, every slab).
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

    /** Every block that can be accepted, by kind, then by name. */
    fun candidates(): List<Block> = all ?: BuiltInRegistries.BLOCK
        .filter { kindOf(it) != null && it.asItem() != Items.AIR }
        .sortedWith(compareBy<Block>({ kindOf(it)!!.ordinal }, { it.name.string }))
        .also { all = it }

    fun id(block: Block): String = BuiltInRegistries.BLOCK.getKey(block).toString()

    private var defaultIds: Set<String>? = null

    /** Every slab: what is accepted until you choose. The same set each time, so [accepted] can tell it has not changed. */
    fun defaults(): Set<String> = defaultIds
        ?: candidates().filter { kindOf(it) == Kind.SLABS }.mapTo(HashSet(), ::id).let { java.util.Collections.unmodifiableSet(it) }
            .also { defaultIds = it }

    private var acceptedFor: Set<String>? = null
    private var acceptedBlocks: Set<Block> = emptySet()

    /** The accepted blocks, worked out again only when the setting is changed. */
    fun accepted(): Set<Block> {
        val ids = Config.spawnProofBlocks ?: defaults()
        if (ids !== acceptedFor) {
            acceptedFor = ids
            acceptedBlocks = candidates().filterTo(HashSet()) { id(it) in ids }
        }
        return acceptedBlocks
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

/**
 * The 16-block sections a set of spots falls in, as packed keys, so "does this box hold a spot?"
 * takes one lookup per section rather than a pass over every spot. Litematica asks that about
 * whole chunks while it decides what to build, on its builder threads.
 */
object SpotSections {

    /** The sections [spots] (packed block positions) fall in. */
    fun of(spots: LongArray): LongOpenHashSet {
        val out = LongOpenHashSet()
        for (spot in spots) out.add(key(BlockPos.getX(spot) shr 4, BlockPos.getY(spot) shr 4, BlockPos.getZ(spot) shr 4))
        return out
    }

    /** Whether any of [sections] lies at least partly inside the box from min to max, in blocks, both included. */
    fun anyIn(sections: LongSet, minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean {
        if (sections.isEmpty() || maxX < minX || maxY < minY || maxZ < minZ) return false
        val x0 = minX shr 4; val y0 = minY shr 4; val z0 = minZ shr 4
        val x1 = maxX shr 4; val y1 = maxY shr 4; val z1 = maxZ shr 4
        val count = (x1 - x0 + 1).toLong() * (y1 - y0 + 1) * (z1 - z0 + 1)
        if (count > sections.size) {
            // A big box and few sections: go through the sections instead.
            val each = sections.iterator()
            while (each.hasNext()) {
                val key = each.nextLong()
                val x = xOf(key); val y = yOf(key); val z = zOf(key)
                if (x in x0..x1 && y in y0..y1 && z in z0..z1) return true
            }
            return false
        }
        for (x in x0..x1) for (z in z0..z1) for (y in y0..y1) if (sections.contains(key(x, y, z))) return true
        return false
    }

    /** x in the top 22 bits, z in the next 22, y in the low 20: every section of a world fits. */
    fun key(x: Int, y: Int, z: Int): Long =
        ((x.toLong() and 0x3FFFFF) shl 42) or ((z.toLong() and 0x3FFFFF) shl 20) or (y.toLong() and 0xFFFFF)

    private fun xOf(key: Long): Int = (key shr 42).toInt()
    private fun zOf(key: Long): Int = ((key shl 22) shr 42).toInt()
    private fun yOf(key: Long): Int = ((key shl 44) shr 44).toInt()
}
