package com.skystormer.skysmapshapes

import java.lang.reflect.Field
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.world.item.BlockItem

/**
 * What is stopping [SpawnProofing], if anything: MiniHUD and Litematica each have switches it
 * needs, and with one of them off it just quietly does nothing. Each [Check] says what it needs
 * and why, and where it can, fixes it in a click.
 *
 * Told two ways: once in chat when spawn proofing is switched on and something is in the way
 * ([tick]), with a `[Fix it]` that runs `/skysmapshapes fix <check>` ([fixFromCommand]); and as the
 * Setup lines at the top of the light levels screen. Nothing in MiniHUD or Litematica is ever
 * changed except on a click.
 *
 * MiniHUD's options are read by reflection, as the rest of the mod does; Litematica's through
 * [LitematicaPlacement]. Each check is asked on its own: an option that is not there means
 * "cannot tell", never a crash.
 */
object SpawnProofSetup {

    /** One thing spawn proofing needs, in the order they are worth fixing. [id] is what `fix` takes. */
    enum class Check(val id: String, val title: String, val why: String) {
        MINIHUD_RENDERING("minihud_rendering", "MiniHUD's rendering on",
            "MiniHUD's main rendering switch. With it off MiniHUD shows nothing, its light level overlay included, and spawn proofing follows that overlay."),
        LIGHT_OVERLAY("light_overlay", "MiniHUD's light level overlay on",
            "Spawn proofing covers the floors MiniHUD's light level overlay marks, so it comes and goes with the overlay (and its hotkey)."),
        LIGHT_RANGE("light_range", "MiniHUD's light level range",
            "Spawn proofing reaches only as far as MiniHUD looks for light levels: MiniHUD's Light Level Range setting."),
        LITEMATICA_RENDERING("litematica_rendering", "Litematica's rendering on",
            "Litematica's main rendering and schematic rendering switches. With either off, the blocks to place are not drawn."),
        EASY_PLACE("easy_place", "Litematica's Easy Place on",
            "Easy Place is what puts the blocks down: aim at a spot and use the block."),
        LAYERS("layers", "Render layers take in the spots",
            "Litematica hides blocks outside its render layers, and Easy Place will not place them. This mod lets spawn proofing's spots through " +
                "whatever your layers are, so normally there is nothing to do; if that could not be set up on this Litematica, the layers have to take in the spots."),
        BLOCK("block", "An accepted block with you",
            "Spawn proofing puts down one of the accepted blocks (Blocks… in these settings), so you need some with you."),
        SHAPE("shape", "Inside a spawn-proofed shape",
            "Spawn proofing only covers floors inside shapes set to it (right-click one on the map), unless Everywhere is on."),
        ;

        companion object {
            fun byId(id: String): Check? = entries.firstOrNull { it.id == id }
        }
    }

    /** Whether [check] is met: true, false, or null when it cannot tell (an option missing on this version). */
    fun passes(check: Check): Boolean? {
        if (!SpawnProofing.installed) return null
        return try {
            when (check) {
                Check.MINIHUD_RENDERING -> bool(miniHud(GENERIC, MAIN_TOGGLE))
                Check.LIGHT_OVERLAY -> bool(miniHud(RENDERER_TOGGLE, LIGHT_TOGGLE))
                Check.LIGHT_RANGE -> lightRange()?.let { it >= MIN_LIGHT_RANGE }
                Check.LITEMATICA_RENDERING -> LitematicaPlacement.renderingOn()
                Check.EASY_PLACE -> LitematicaPlacement.easyPlaceOn()
                Check.LAYERS -> SpawnProofing.layerBypassWorking || SpawnProofing.shown()?.all(LitematicaPlacement::inLayers) ?: true
                Check.BLOCK -> carriesAccepted()
                Check.SHAPE -> Config.spawnProofEverywhere || insideShape() ?: return null
            }
        } catch (e: Throwable) {
            if (unreadable.add(check)) Log.warn("Spawn proofing setup: cannot tell \"{}\" on this version ({})", check.title, e.toString())
            null
        }
    }

    private val unreadable = HashSet<Check>()

    /** What [check]'s line says: its title, with MiniHUD's range in blocks when it can be read. */
    fun line(check: Check): String {
        if (check != Check.LIGHT_RANGE) return check.title
        val range = try { lightRange() } catch (e: Throwable) { null }
        return if (range == null) check.title else "${check.title}: $range blocks"
    }

    /** What is wrong when [check] fails, said as the end of "Spawn proofing is waiting: ...". */
    fun problem(check: Check): String = when (check) {
        Check.MINIHUD_RENDERING -> "MiniHUD's rendering is off"
        Check.LIGHT_OVERLAY -> "MiniHUD's light level overlay is off"
        Check.LIGHT_RANGE -> "MiniHUD's light level range is only ${try { lightRange() } catch (e: Throwable) { null } ?: "a few"} blocks"
        Check.LITEMATICA_RENDERING -> "Litematica's rendering is off"
        Check.EASY_PLACE -> "Litematica's Easy Place is off"
        Check.LAYERS -> "Litematica's render layers leave out some of the spots"
        Check.BLOCK -> "you have none of the accepted blocks with you"
        Check.SHAPE -> "you are not inside a spawn-proofed shape"
    }

    /** What to do yourself, for a check with no fix. */
    fun advice(check: Check): String? = when (check) {
        Check.LIGHT_RANGE -> "Raise Light Level Range in MiniHUD's Generic settings."
        Check.BLOCK -> "Carry a slab (or another accepted block)."
        Check.SHAPE -> "Right-click a shape on the map → Spawn proofing: on, or turn on Everywhere."
        else -> null
    }

    /** Whether [check] has a fix to click. */
    fun canFix(check: Check): Boolean = advice(check) == null

    /**
     * Applies [check]'s fix, and says what it changed; null when there is no fix or it did not
     * work. Only ever called on a click.
     */
    fun fix(check: Check): String? {
        if (!SpawnProofing.installed || !canFix(check)) return null
        val changed = try {
            when (check) {
                Check.MINIHUD_RENDERING -> setBool(miniHud(GENERIC, MAIN_TOGGLE), true).let { "Turned on MiniHUD's rendering." }
                Check.LIGHT_OVERLAY -> setBool(miniHud(RENDERER_TOGGLE, LIGHT_TOGGLE), true).let { "Turned on MiniHUD's light level overlay." }
                Check.LITEMATICA_RENDERING -> LitematicaPlacement.turnOnRendering().let { "Turned on Litematica's rendering." }
                Check.EASY_PLACE -> LitematicaPlacement.turnOnEasyPlace().let { "Turned on Litematica's Easy Place." }
                Check.LAYERS -> LitematicaPlacement.showAllLayers().let { "Set Litematica's render layers to All." }
                else -> null
            }
        } catch (e: Throwable) {
            Log.error("Spawn proofing setup: could not fix \"${check.title}\"", e)
            null
        }
        if (changed != null) SpawnProofing.refresh()
        return changed
    }

    /** The first check that fails, in [Check]'s order; null when none does (or none can tell). */
    fun firstFailing(): Check? = Check.entries.firstOrNull { passes(it) == false }

    /** Every failing check that has a fix, for Fix all. */
    fun fixable(): List<Check> = Check.entries.filter { canFix(it) && passes(it) == false }

    /** Run by `/skysmapshapes fix <check>`, the chat line's `[Fix it]`: fixes that one check and says so. */
    fun fixFromCommand(id: String) {
        val check = Check.byId(id) ?: return say(Component.literal("Spawn proofing: there is no setup check called $id."))
        val message = when {
            passes(check) == true -> "Spawn proofing: nothing to fix, ${check.title.replaceFirstChar { it.lowercase() }} already."
            !canFix(check) -> "Spawn proofing: ${advice(check)}"
            else -> fix(check)?.let { "Spawn proofing: $it" } ?: "Spawn proofing: that could not be changed; the log says why."
        }
        say(Component.literal(message))
    }

    // Telling you in chat.

    private val looks = Every(LOOK_EVERY)
    private var quiet = QUIET_TICKS
    private var wasWanted = false
    private var sayOnSwitchOn = false

    /** The failing check last seen, and the one seen a second before it settles. */
    private var settled: Check? = null
    private var seen: Check? = null
    private var lastSaid = 0L

    /**
     * Called every client tick; looks once a second. When spawn proofing is switched on (in the
     * settings, or by the first spawn-proofed shape here) and something is in the way, says the
     * first thing in one chat line. After that only when a different check starts failing, or one
     * you fixed breaks again; never more than once a minute. A check has to fail two looks in a
     * row, so something only changing for a moment says nothing.
     *
     * MiniHUD's rendering and light level overlay are only named on switching on: after that, the
     * overlay going off is MiniHUD's hotkey doing what it is for, switching spawn proofing off.
     */
    fun tick() {
        if (!SpawnProofing.installed) return
        if (quiet > 0) quiet--
        if (!looks.due()) return
        val wanted = wanted()
        if (wanted && !wasWanted && quiet == 0) sayOnSwitchOn = true
        wasWanted = wanted
        if (!wanted) {
            settled = null
            seen = null
            sayOnSwitchOn = false
            return
        }
        val failing = firstFailing()
        if (failing != seen) {
            seen = failing
            return
        }
        val changed = failing != settled
        settled = failing
        if (failing == null) {
            sayOnSwitchOn = false
            return
        }
        if (quiet > 0 || !(sayOnSwitchOn || changed)) return
        if (!sayOnSwitchOn && (failing == Check.MINIHUD_RENDERING || failing == Check.LIGHT_OVERLAY)) return
        val now = System.currentTimeMillis()
        if (now - lastSaid < MIN_GAP_MS) return
        lastSaid = now
        sayOnSwitchOn = false
        sayWaiting(failing)
    }

    /** Called when you leave a world: the next one starts quiet, as things load. */
    fun reset() {
        quiet = QUIET_TICKS
        wasWanted = false
        sayOnSwitchOn = false
        settled = null
        seen = null
    }

    /** Whether spawn proofing is meant to be working here: switched on, with Everywhere or a spawn-proofed shape in this dimension. */
    private fun wanted(): Boolean {
        if (!SpawnProofing.active || Minecraft.getInstance().player == null) return false
        if (Config.spawnProofEverywhere) return true
        val dimension = Dimensions.ofPlayer() ?: return false
        return SpawnProofing.volumes(dimension).isNotEmpty()
    }

    private fun sayWaiting(check: Check) {
        val line = Component.literal("Spawn proofing is waiting: ${problem(check)}.")
        if (canFix(check)) {
            line.append(" ").append(
                Component.literal("[Fix it]").withStyle {
                    it.withColor(FIX_COLOUR).withBold(true)
                        .withClickEvent(ClickEvent.RunCommand("/${ShapeShare.COMMAND} $FIX ${check.id}"))
                        .withHoverEvent(HoverEvent.ShowText(Component.literal("${check.why}\nClick to fix it.")))
                }
            )
        } else {
            line.append(" ${advice(check)}")
        }
        say(line)
    }

    private fun say(message: Component) {
        Minecraft.getInstance().gui.chatListener().handleSystemMessage(message, false)
    }

    // Reading the game and the other mods.

    private fun carriesAccepted(): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        val accepted = SpawnProofBlocks.accepted()
        val inventory = player.inventory
        return (0 until inventory.containerSize).any { slot ->
            val stack = inventory.getItem(slot)
            !stack.isEmpty && (stack.item as? BlockItem)?.block in accepted
        }
    }

    /** Whether you stand inside (or on the floor of) a spawn-proofed shape here; null when there is no telling. */
    private fun insideShape(): Boolean? {
        val player = Minecraft.getInstance().player ?: return null
        val dimension = Dimensions.ofPlayer() ?: return null
        val x = player.blockX
        val y = player.blockY
        val z = player.blockZ
        return SpawnProofing.volumes(dimension).any { it.contains(x, y, z) || it.contains(x, y - 1, z) }
    }

    private fun lightRange(): Int? {
        val option = miniHud(GENERIC, LIGHT_RANGE)
        return option.type.getMethod("getIntegerValue").invoke(option.get(null)) as? Int
    }

    /**
     * A MiniHUD option: a static field of [className]. Its methods are looked up on the field's
     * type, which is public, not on the value's class (an enum constant can have its own).
     */
    private fun miniHud(className: String, field: String): Field = Class.forName(className).getField(field)

    private fun bool(option: Field): Boolean = option.type.getMethod("getBooleanValue").invoke(option.get(null)) as Boolean

    private fun setBool(option: Field, on: Boolean) {
        option.type.getMethod("setBooleanValue", Boolean::class.javaPrimitiveType).invoke(option.get(null), on)
    }

    /** The subcommand of `/skysmapshapes` that runs a fix. */
    const val FIX = "fix"

    private const val RENDERER_TOGGLE = "fi.dy.masa.minihud.config.RendererToggle"
    private const val LIGHT_TOGGLE = "OVERLAY_LIGHT_LEVEL"
    private const val GENERIC = "fi.dy.masa.minihud.config.Configs\$Generic"
    private const val MAIN_TOGGLE = "MAIN_RENDERING_TOGGLE"
    private const val LIGHT_RANGE = "LIGHT_LEVEL_RANGE"

    /** A light level range under this many blocks is too small to be worth spawn proofing with. */
    private const val MIN_LIGHT_RANGE = 8

    private const val LOOK_EVERY = 20
    private const val MIN_GAP_MS = 60_000L

    /** Five seconds after joining, while the inventory and the other mods' settings come in. */
    private const val QUIET_TICKS = 100

    private const val FIX_COLOUR = 0x55FF55
}
