package com.skystormer.skysmapshapes.gui

import com.mojang.blaze3d.platform.InputConstants
import com.skystormer.skysmapshapes.Colours
import com.skystormer.skysmapshapes.Config
import com.skystormer.skysmapshapes.Dimensions
import com.skystormer.skysmapshapes.Log
import com.skystormer.skysmapshapes.MapMenus
import com.skystormer.skysmapshapes.MiniHudGeometry
import com.skystormer.skysmapshapes.MiniHudShapes
import com.skystormer.skysmapshapes.Shape
import com.skystormer.skysmapshapes.ShapeStore
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.network.chat.Component

/**
 * Adding or editing one of this mod's shapes on Xaero's world map itself, in a small window that is
 * a map panel like the others: docked under the Shapes panel's stack to begin with, moved, folded,
 * docked and undocked by its title, and sized with the stack. The shape is drawn live on the map as
 * it changes.
 *
 * - Type a name and press Enter: the size then follows the mouse until a click locks it, or a
 *   number is typed. Enter makes it (or saves it), Esc cancels.
 * - A rectangle or an ellipse can also be set by two corner blocks, typed, or by dragging a corner
 *   handle (both directions) or a side (one). The mouse can size it from the centre or from corner 1.
 * - Dragging inside the shape moves it; dragging its outline sizes it.
 * - A new shape can be made in MiniHUD instead, choosing how it stands up in the world.
 *
 * While open, clicks on the map go to the shape, not the map, except where nothing of the shape is
 * under the mouse, which still pans the map; keys typed into its boxes never reach Xaero's hotkeys.
 * The draft is a [Shape] like any other, so [draft] is drawn by the usual code.
 */
object AddShapeWindow {

    private const val ID = "skysmapshapes:add"
    private const val WIDTH = 150
    private const val PAD = 4
    private const val ROW = DockPanel.ROW
    /** Where the boxes start, under the title. */
    private const val CONTENT_TOP = ROW + 1
    /** The Shapes panel's own see-through black, so the window matches the other map panels. */
    private const val BACKGROUND = 0x70000000
    private const val BOX = 14
    private const val PILL = 12
    private const val GAP = 2
    private const val LINE = 10
    private const val MAX_SIZE = Shape.MAX_TYPED_SIZE
    private const val DRAFT_ID = "skysmapshapes:draft"

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val GREY = 0xFF9A9A9A.toInt()
    private const val LABEL = 0xFFD0D0D0.toInt()

    private enum class Drag { MOVE, SIZE, CORNER, SIDE_X, SIDE_Z }

    private var screen: Screen? = null
    private var existing: Shape? = null
    private var dimension = ""

    // What is being made. Radius shapes keep a centre block; width and length shapes keep their edges.
    private var name = ""
    private var type = Shape.Type.CIRCLE
    private var cx = 0
    private var cz = 0
    private var radius = 32.0
    private var minX = 0.0
    private var minZ = 0.0
    private var maxX = 1.0
    private var maxZ = 1.0
    private var colour = Colours.RED.argb
    private var fill = false
    private var lineWidth = Config.DEFAULT_LINE_WIDTH
    private var y: Int? = null

    private var fromCorner = false
    private var cornerX = 0
    private var cornerZ = 0
    private var tracking = false
    private var drag: Drag? = null
    private var dragFixedX = 0.0
    private var dragFixedZ = 0.0
    private var dragStartX = 0
    private var dragStartZ = 0
    private var dragFrom: DoubleArray = DoubleArray(6)

    private var inMiniHud = false
    private var form: MiniHudGeometry.Form? = null
    private var lagWarned: String? = null
    private var message: String? = null

    private var frame: Frame? = null
    /** The boxes' width, from the panel's width (a stack shares its widest panel's). */
    private var inner = WIDTH - PAD * 2
    private var contentHeight = 0
    private var hintTop = 0
    private var button: MouseButtonInfo? = null
    private var screenMouseX = 0
    private var screenMouseY = 0
    private val widgets = ArrayList<AbstractWidget>()
    private val boxes = LinkedHashMap<String, EditBox>()
    private var syncing = false

    /** The block under the mouse on the world map and how many blocks one screen unit is, from the map's hook each frame. */
    private var mouseX = 0
    private var mouseZ = 0
    private var blocksPerUnit = 1f

    val active get() = screen != null

    /** The form picked, or the first MiniHUD has for this kind of shape. */
    private fun chosenForm(): MiniHudGeometry.Form = form ?: MiniHudGeometry.forms(type).first()

    /** The shape as it stands, for drawing, or null when nothing is being made. */
    fun draft(): Shape? = if (screen == null) null else build()

    /** The id of the shape being edited, which the map leaves out while its draft is drawn instead. */
    fun editingId(): String? = if (screen == null) null else existing?.id

    @JvmStatic
    fun mouseOnMap(x: Int, z: Int, perUnit: Float) {
        mouseX = x
        mouseZ = z
        blocksPerUnit = perUnit
    }

    /** Where handles go, in blocks: the centre, and for a width and length shape its corners and the middles of its sides. */
    fun handles(): List<DoubleArray> {
        if (screen == null) return emptyList()
        val shape = build()
        val b = shape.bounds
        val centre = doubleArrayOf(b.centreX, b.centreZ)
        if (type.sized == Shape.Sized.RADIUS) return listOf(centre)
        val mx = (minX + maxX) / 2
        val mz = (minZ + maxZ) / 2
        return listOf(centre,
            doubleArrayOf(minX, minZ), doubleArrayOf(maxX, minZ), doubleArrayOf(minX, maxZ), doubleArrayOf(maxX, maxZ),
            doubleArrayOf(mx, minZ), doubleArrayOf(mx, maxZ), doubleArrayOf(minX, mz), doubleArrayOf(maxX, mz))
    }

    fun handleSize() = 3.0 * blocksPerUnit

    // Where the map is, to put the measurements beside their lines on the screen: the camera is the
    // middle of the screen, and blocks per map unit come from the map's matrix.
    private var cameraX = 0.0
    private var cameraZ = 0.0
    private var viewSet = false
    /**
     * Screen units per map unit, across and down, found from the mouse: Xaero works out the block
     * under the mouse from where it really is on the screen, while its matrix leaves out a scale
     * it applies afterwards. Signed, in case one way runs backwards. The same at every zoom.
     */
    private var screenPerUnitX = 0.0
    private var screenPerUnitZ = 0.0
    private var viewLogged = false

    @JvmStatic
    fun mapView(camX: Double, camZ: Double) {
        cameraX = camX
        cameraZ = camZ
        viewSet = true
    }

    /** Block ([bx], [bz]) on the screen, or null until the mouse has been far enough out on the map to tell. */
    private fun toScreen(s: Screen, bx: Double, bz: Double, mouseScreenX: Int, mouseScreenY: Int): DoubleArray? {
        if (!viewSet) return null
        val midX = s.width / 2.0
        val midY = s.height / 2.0
        // Learnt while the mouse is well away from the middle both ways, where being half a block out hardly matters.
        if (!inside(mouseScreenX.toDouble(), mouseScreenY.toDouble())) {
            val unitsX = (mouseX + 0.5 - cameraX) / blocksPerUnit
            val unitsZ = (mouseZ + 0.5 - cameraZ) / blocksPerUnit
            val dx = mouseScreenX - midX
            val dy = mouseScreenY - midY
            if (abs(dx) > 30 && abs(unitsX) > 30) screenPerUnitX = dx / unitsX
            if (abs(dy) > 30 && abs(unitsZ) > 30) screenPerUnitZ = dy / unitsZ
        }
        if (screenPerUnitX == 0.0 || screenPerUnitZ == 0.0) return null
        if (!viewLogged) {
            viewLogged = true
            Log.info("Map measurements: {} x {} screen units per map unit", screenPerUnitX, screenPerUnitZ)
        }
        return doubleArrayOf(midX + (bx - cameraX) / blocksPerUnit * screenPerUnitX, midY + (bz - cameraZ) / blocksPerUnit * screenPerUnitZ)
    }

    /** The main measuring lines, with what each says: the radius, or the width and the length. */
    private fun measures(): List<Pair<String, DoubleArray>> {
        if (type.sized == Shape.Sized.RADIUS) {
            val l = measureLines().firstOrNull() ?: return emptyList()
            return listOf("r ${Shape.number(radius)}" to l)
        }
        val mx = (minX + maxX) / 2
        val mz = (minZ + maxZ) / 2
        return listOf("w ${Shape.number(maxX - minX)}" to doubleArrayOf(minX, mz, maxX, mz),
            "l ${Shape.number(maxZ - minZ)}" to doubleArrayOf(mx, minZ, mx, maxZ))
    }

    /**
     * The measuring lines, in blocks, as segments (x1, z1, x2, z2): the radius from the centre (a
     * circle's and an octagon's toward the mouse, the others' east), or the width and the length
     * across the middle, each with a tick across both ends.
     */
    fun measureLines(): List<DoubleArray> {
        if (screen == null) return emptyList()
        val tick = 4.0 * blocksPerUnit
        val out = ArrayList<DoubleArray>()
        fun line(x1: Double, z1: Double, x2: Double, z2: Double) {
            val len = hypot(x2 - x1, z2 - z1)
            if (len <= 0) return
            val nx = -(z2 - z1) / len * tick
            val nz = (x2 - x1) / len * tick
            out += doubleArrayOf(x1, z1, x2, z2)
            out += doubleArrayOf(x1 + nx, z1 + nz, x1 - nx, z1 - nz)
            out += doubleArrayOf(x2 + nx, z2 + nz, x2 - nx, z2 - nz)
        }
        if (type.sized == Shape.Sized.RADIUS) {
            val ox = cx + 0.5
            val oz = cz + 0.5
            val toward = type == Shape.Type.CIRCLE || type == Shape.Type.OCTAGON
            val angle = if (toward && (mouseX != cx || mouseZ != cz)) Math.atan2(mouseZ + 0.5 - oz, mouseX + 0.5 - ox) else 0.0
            line(ox, oz, ox + Math.cos(angle) * radius, oz + Math.sin(angle) * radius)
        } else {
            val mx = (minX + maxX) / 2
            val mz = (minZ + maxZ) / 2
            line(minX, mz, maxX, mz)
            line(mx, minZ, mx, maxZ)
        }
        return out
    }

    fun lineWidth() = 0.5 * blocksPerUnit

    /**
     * Opens the window for a new shape centred on block ([x], [z]) of [dimension], at [height] when
     * it is known (a waypoint's), which a MiniHUD shape is centred on; otherwise the Y box starts empty.
     */
    fun openNew(map: Screen, dimension: String, x: Int, z: Int, label: String, height: Int? = null) {
        reset(map, null, dimension)
        name = label
        y = height
        cx = x
        cz = z
        val preset = Config.presets.firstOrNull()
        radius = preset?.radius ?: 32.0
        colour = preset?.colour ?: Colours.RED.argb
        edgesFromRadius()
        show(map)
        focus("name")
    }

    /** Opens the window on one of this mod's shapes, drawn as its draft until saved or cancelled. */
    fun openExisting(map: Screen, shape: Shape) {
        reset(map, shape, shape.dimension)
        name = shape.label
        type = shape.type
        colour = shape.colour
        fill = shape.fill
        lineWidth = shape.lineWidth
        y = shape.y
        if (type.sized == Shape.Sized.RADIUS) {
            cx = shape.x
            cz = shape.z
            radius = shape.radius
            edgesFromRadius()
        } else {
            val b = shape.bounds
            minX = b.minX; minZ = b.minZ; maxX = b.maxX; maxZ = b.maxZ
            radiusFromEdges()
        }
        show(map)
        focus("name")
    }

    /** Lays out the boxes and puts the panel on the map, in front of the others. */
    private fun show(map: Screen) {
        layoutWidgets()
        val panel = Frame(map)
        frame = panel
        Screens.getWidgets(map).add(panel)
        panel.toFront()
    }

    private fun reset(map: Screen, shape: Shape?, dimension: String) {
        close()
        screen = map
        existing = shape
        this.dimension = dimension
        type = Shape.Type.CIRCLE
        fill = false
        lineWidth = Config.DEFAULT_LINE_WIDTH
        y = null
        fromCorner = false
        tracking = false
        drag = null
        inMiniHud = false
        form = null
        lagWarned = null
        message = null
    }

    /**
     * Called whenever the map screen is set up, including after a resize, which throws its widgets
     * away: puts the window back, and hooks the mouse and keys it needs.
     */
    fun install(map: Screen) {
        if (screen != null && screen !== map) close()
        if (screen === map) show(map)

        ScreenMouseEvents.allowMouseClick(map).register { s, event -> if (screen === s) click(s, event) else true }
        ScreenMouseEvents.allowMouseDrag(map).register { s, _, _, _ -> !(screen === s && drag != null) }
        ScreenMouseEvents.allowMouseRelease(map).register { s, _ ->
            if (screen === s && drag != null) {
                drag = null
                false
            } else true
        }
        ScreenMouseEvents.allowMouseScroll(map).register { s, mx, my, _, _ -> !(screen === s && inside(mx, my)) }
        ScreenKeyboardEvents.allowKeyPress(map).register { s, event -> if (screen === s) key(event) else true }
        ScreenEvents.afterExtract(map).register { s, graphics, mx, my, _ -> if (screen === s) frame(graphics, mx, my) }
        ScreenEvents.remove(map).register { s -> if (screen === s) close() }
    }

    fun close() {
        frame?.let { panel ->
            panel.leave()
            screen?.let { Screens.getWidgets(it).remove(panel) }
        }
        frame = null
        widgets.clear()
        boxes.clear()
        screen = null
        existing = null
        drag = null
        tracking = false
    }

    // ---------- the shape ----------

    private fun build(): Shape {
        val id = existing?.id ?: DRAFT_ID
        val label = name.trim()
        if (type.sized == Shape.Sized.RADIUS) {
            return Shape(id = id, label = label, dimension = dimension, type = type, x = cx, z = cz, radius = radius,
                colour = colour, fill = fill, lineWidth = lineWidth, y = y)
        }
        val w = maxX - minX
        val l = maxZ - minZ
        val centreX = (minX + maxX) / 2
        val centreZ = (minZ + maxZ) / 2
        // A rectangle starting on block edges keeps them exactly; otherwise it is centred on a block.
        return if (type == Shape.Type.RECTANGLE && minX == floor(minX) && minZ == floor(minZ))
            Shape(id = id, label = label, dimension = dimension, type = type, x = minX.toInt(), z = minZ.toInt(), width = w, length = l,
                anchor = Shape.Anchor.CORNER, colour = colour, fill = fill, lineWidth = lineWidth, y = y)
        else Shape(id = id, label = label, dimension = dimension, type = type, x = (centreX - 0.5).roundToInt(), z = (centreZ - 0.5).roundToInt(),
            width = w, length = l, colour = colour, fill = fill, lineWidth = lineWidth, y = y)
    }

    private fun edgesFromRadius() {
        minX = cx + 0.5 - radius
        maxX = cx + 0.5 + radius
        minZ = cz + 0.5 - radius
        maxZ = cz + 0.5 + radius
    }

    private fun radiusFromEdges() {
        cx = ((minX + maxX) / 2 - 0.5).roundToInt()
        cz = ((minZ + maxZ) / 2 - 0.5).roundToInt()
        radius = maxOf(1.0, (minOf(maxX - minX, maxZ - minZ) / 2).roundToInt().toDouble())
    }

    private fun setType(new: Shape.Type) {
        if (new == type) return
        if (type.sized == Shape.Sized.RADIUS && new.sized == Shape.Sized.WIDTH_LENGTH) edgesFromRadius()
        if (type.sized == Shape.Sized.WIDTH_LENGTH && new.sized == Shape.Sized.RADIUS) radiusFromEdges()
        type = new
        if (type.sized == Shape.Sized.RADIUS) fromCorner = false
        lagWarned = null
        rebuild()
        arm(true)
    }

    /** Sizes the shape so its edge (or, from a corner, its far corner) is on block ([bx], [bz]). */
    private fun sizeTo(bx: Int, bz: Int) {
        if (type.sized == Shape.Sized.RADIUS) {
            val dx = (bx - cx).toDouble()
            val dz = (bz - cz).toDouble()
            radius = clampSize(when (type) {
                Shape.Type.SQUARE -> maxOf(abs(dx), abs(dz))
                Shape.Type.RHOMBUS -> abs(dx) + abs(dz)
                else -> hypot(dx, dz)
            }.roundToInt().toDouble())
        } else if (fromCorner) {
            minX = minOf(cornerX, bx).toDouble(); maxX = maxOf(cornerX, bx) + 1.0
            minZ = minOf(cornerZ, bz).toDouble(); maxZ = maxOf(cornerZ, bz) + 1.0
        } else {
            val mx = (minX + maxX) / 2
            val mz = (minZ + maxZ) / 2
            val w = clampSize((abs(bx + 0.5 - mx) * 2).roundToInt().toDouble())
            val l = clampSize((abs(bz + 0.5 - mz) * 2).roundToInt().toDouble())
            minX = mx - w / 2; maxX = mx + w / 2
            minZ = mz - l / 2; maxZ = mz + l / 2
        }
    }

    private fun clampSize(v: Double) = v.coerceIn(1.0, MAX_SIZE)

    /** The edge a dragged corner or side lands on, for the mouse on block [b] with the other edge at [fixed]. */
    private fun edgeFor(b: Int, fixed: Double): Double = if (b + 0.5 >= fixed) maxOf(b + 1.0, fixed + 1) else minOf(b.toDouble(), fixed - 1)

    // ---------- the window ----------

    private fun font() = Minecraft.getInstance().font

    private fun rebuild() {
        if (screen != null) layoutWidgets()
    }

    /**
     * Lays out the boxes and buttons in the panel's own units (its corner at 0, 0, before it is
     * scaled), keeping whatever box had the keyboard.
     */
    private fun layoutWidgets() {
        val focused = boxes.entries.firstOrNull { it.value.isFocused }?.key
        widgets.clear()
        boxes.clear()
        val x0 = PAD
        var y = CONTENT_TOP + GAP

        box("name", x0, y, inner, "Name", 64)
        y += BOX + GAP

        val kinds = listOf(Shape.Type.CIRCLE, Shape.Type.SQUARE, Shape.Type.RECTANGLE, Shape.Type.RHOMBUS, Shape.Type.OCTAGON, Shape.Type.ELLIPSE)
        val kindWidth = (inner - GAP * 2) / 3
        kinds.forEachIndexed { i, kind ->
            val label = when (kind) { Shape.Type.RECTANGLE -> "Rect"; Shape.Type.RHOMBUS -> "Diamond"; else -> kind.title }
            widgets += Pill(x0 + (kindWidth + GAP) * (i % 3), y + (PILL + GAP) * (i / 3), kindWidth, PILL, { label }, { type == kind },
                { "${kind.title}. Click, then move the mouse to size it." }) { setType(kind) }
        }
        y += (PILL + GAP) * 2

        val half = (inner - GAP) / 2
        val cell = (inner - GAP * 2) / 3
        box("x", x0 + 9, y, cell - 9, "X", 12)
        box("z", x0 + cell + GAP + 9, y, cell - 9, "Z", 12)
        box("y", x0 + (cell + GAP) * 2 + 9, y, inner - (cell + GAP) * 2 - 9, if (here()) "feet" else "sea", 6)
        y += BOX + GAP

        if (type.sized == Shape.Sized.RADIUS) {
            box("r", x0 + 40, y, inner - 40, "blocks", 10)
        } else {
            box("w", x0 + 9, y, half - 9, "width", 10)
            box("l", x0 + half + GAP + 9, y, half - 9, "length", 10)
            y += BOX + GAP
            val third = (inner - 9) / 2
            box("x1", x0 + 9, y, third, "X", 12)
            box("z1", x0 + 9 + third + GAP, y, inner - 9 - third - GAP, "Z", 12)
            y += BOX + GAP
            box("x2", x0 + 9, y, third, "X", 12)
            box("z2", x0 + 9 + third + GAP, y, inner - 9 - third - GAP, "Z", 12)
            y += BOX + GAP
            widgets += Pill(x0, y, inner, PILL, { "Mouse: from " + if (fromCorner) "corner 1" else "centre" }, { fromCorner },
                { "From the centre, the shape grows evenly both ways. From corner 1, it stays pinned at corner 1 and the mouse sets the opposite corner." }) {
                fromCorner = !fromCorner
                if (fromCorner) { cornerX = floor(minX).toInt(); cornerZ = floor(minZ).toInt() }
                arm(true)
            }
        }
        y += (if (type.sized == Shape.Sized.RADIUS) BOX else PILL) + GAP

        widgets += Pill(x0, y, 30, PILL, { "Fill" }, { fill }, { "Shade the inside as well as drawing the outline." }) { fill = !fill }
        val swatch = minOf(14, (inner - 32) / Colours.entries.size)
        Colours.entries.forEachIndexed { i, c ->
            widgets += Pill(x0 + 32 + i * swatch, y + 1, swatch - 1, 10, { "" }, { colour == c.argb }, { c.title }, swatch = c.argb) { colour = c.argb }
        }
        y += PILL + GAP

        if (miniHudOffered()) {
            val forms = MiniHudGeometry.forms(type)
            if (form !in forms) form = forms.first()
            widgets += Pill(x0, y, if (inMiniHud) half else inner, PILL, { if (inMiniHud) "In: MiniHUD" else "Make it in: Map Shapes" }, { inMiniHud },
                { whereTip() }) { inMiniHud = !inMiniHud; lagWarned = null; rebuild() }
            if (inMiniHud) {
                widgets += Pill(x0 + half + GAP, y, inner - half - GAP, PILL, { "As: " + MiniHudGeometry.formName(type, chosenForm()) }, { false },
                    { "How it stands up in the world. " + MiniHudGeometry.formTip(chosenForm()) + if (forms.size == 1) " MiniHUD has only this for a ${type.title.lowercase()}." else "" },
                    enabled = forms.size > 1) {
                    form = forms[(forms.indexOf(form) + 1) % forms.size]
                    lagWarned = null
                }
            }
            y += PILL + GAP
        }

        // Room for three lines of hint or message.
        hintTop = y
        y += LINE * 3 + GAP
        val buttonWidth = (inner - GAP) / 2
        widgets += Button.builder(Component.literal(if (existing == null) "Create" else "Save")) { commit() }
            .bounds(x0, y, buttonWidth, 16).build()
        widgets += Button.builder(Component.literal("Cancel")) { cancel() }
            .bounds(x0 + buttonWidth + GAP, y, inner - buttonWidth - GAP, 16).build()
        y += 16 + GAP
        contentHeight = y - CONTENT_TOP
        sync(null)
        focused?.let { focus(it) }
    }

    private fun miniHudOffered() = existing == null && MiniHudShapes.installed && Config.showMiniHud && MiniHudShapes.canReach(dimension)

    private fun here() = Dimensions.ofPlayer() == dimension

    private fun whereTip(): String = if (!inMiniHud) "Where it is made. MiniHUD draws it in the world too, as well as on the map."
    else "MiniHUD: an ordinary MiniHUD shape, in the world as well as on the map, edited in MiniHUD afterwards. " +
        (if (here()) "" else "You are not in the ${Dimensions.name(dimension)}, so it goes into MiniHUD's file for it, and MiniHUD has it when you go there. ") +
        "It is at the Y typed, or with none, ${if (here()) "at your feet" else "at height ${MapMenus.SEA_LEVEL}"}."

    private fun box(key: String, x: Int, y: Int, width: Int, hint: String, maxLength: Int) {
        val box = EditBox(font(), x, y, width, BOX, Component.literal(hint))
        box.setMaxLength(maxLength)
        box.setHint(Component.literal(hint))
        box.setResponder { if (!syncing) typed(key, it) }
        boxes[key] = box
        widgets += box
    }

    private fun focus(key: String) {
        val box = boxes[key] ?: return
        boxes.values.forEach { if (it !== box) it.isFocused = false }
        box.isFocused = true
        box.moveCursorToEnd(false)
        box.setHighlightPos(0)
    }

    private fun focusedKey(): String? = boxes.entries.firstOrNull { it.value.isFocused }?.key

    /** Puts the shape's numbers in the boxes, except the one being typed in. */
    private fun sync(skip: String?) {
        syncing = true
        try {
            fun set(key: String, value: String) {
                if (key != skip) boxes[key]?.let { if (it.value != value) it.value = value }
            }
            set("name", name)
            set("y", y?.toString() ?: "")
            if (type.sized == Shape.Sized.RADIUS) {
                set("x", cx.toString())
                set("z", cz.toString())
                set("r", Shape.number(radius))
            } else {
                set("x", Shape.number((minX + maxX) / 2))
                set("z", Shape.number((minZ + maxZ) / 2))
                set("w", Shape.number(maxX - minX))
                set("l", Shape.number(maxZ - minZ))
                set("x1", floor(minX).toInt().toString())
                set("z1", floor(minZ).toInt().toString())
                set("x2", (ceil(maxX).toInt() - 1).toString())
                set("z2", (ceil(maxZ).toInt() - 1).toString())
            }
        } finally {
            syncing = false
        }
    }

    /** What was typed into box [key], into the shape. Anything that is not a number yet is left until it is. */
    private fun typed(key: String, text: String) {
        message = null
        if (key == "name") {
            name = text
            return
        }
        if (key == "y") {
            // Empty is the default: your feet, or sea level for another dimension.
            if (text.isBlank()) y = null
            else text.trim().toIntOrNull()?.let { y = it.coerceIn(-Shape.MAX_HEIGHT, Shape.MAX_HEIGHT) }
            lagWarned = null
            return
        }
        val v = text.trim().toDoubleOrNull() ?: return
        when (key) {
            "x" -> if (type.sized == Shape.Sized.RADIUS) cx = v.roundToInt() else { val w = maxX - minX; minX = v - w / 2; maxX = v + w / 2 }
            "z" -> if (type.sized == Shape.Sized.RADIUS) cz = v.roundToInt() else { val l = maxZ - minZ; minZ = v - l / 2; maxZ = v + l / 2 }
            "r" -> { if (v <= 0 || v > MAX_SIZE) return; radius = v; tracking = false }
            "w" -> { if (v <= 0 || v > MAX_SIZE) return; val m = (minX + maxX) / 2; minX = m - v / 2; maxX = m + v / 2; tracking = false }
            "l" -> { if (v <= 0 || v > MAX_SIZE) return; val m = (minZ + maxZ) / 2; minZ = m - v / 2; maxZ = m + v / 2; tracking = false }
            "x1", "x2", "z1", "z2" -> {
                val b = v.roundToInt()
                val x1 = if (key == "x1") b else floor(minX).toInt()
                val x2 = if (key == "x2") b else ceil(maxX).toInt() - 1
                val z1 = if (key == "z1") b else floor(minZ).toInt()
                val z2 = if (key == "z2") b else ceil(maxZ).toInt() - 1
                minX = minOf(x1, x2).toDouble(); maxX = maxOf(x1, x2) + 1.0
                minZ = minOf(z1, z2).toDouble(); maxZ = maxOf(z1, z2) + 1.0
                tracking = false
            }
        }
        sync(key)
    }

    /** Arrow keys step a number box by one, or ten with Shift. */
    private fun step(key: String, up: Boolean, shift: Boolean) {
        val box = boxes[key] ?: return
        val v = box.value.trim().toDoubleOrNull() ?: return
        val stepped = v + (if (up) 1 else -1) * (if (shift) 10 else 1)
        box.value = Shape.number(stepped)
    }

    private fun arm(track: Boolean) {
        tracking = track
        focus(if (type.sized == Shape.Sized.RADIUS) "r" else "w")
    }

    private fun inside(mx: Double, my: Double) = frame?.let { mx >= it.x && mx < it.x + it.width && my >= it.y && my < it.y + it.height } ?: false

    private fun hint(): String = message ?: when {
        tracking -> "Move the mouse to size it, click to lock it. Or type a number. Enter ${if (existing == null) "creates" else "saves"} it."
        type.sized == Shape.Sized.WIDTH_LENGTH -> "Type corners, or drag a corner or a side on the map. Drag inside to move it. Enter ${if (existing == null) "creates" else "saves"} it."
        else -> "Type a size, or drag its outline on the map. Drag inside to move it. Enter ${if (existing == null) "creates" else "saves"} it."
    }

    // ---------- mouse and keys ----------

    private fun click(s: Screen, event: MouseButtonEvent): Boolean {
        val mx = event.x()
        val my = event.y()
        if (inside(mx, my)) {
            // The screen hands a left click to the panel (or to another panel in front of it),
            // which passes it on to its boxes in [clickInside]. Nothing else reaches the map.
            button = event.buttonInfo()
            return event.button() == 0
        }
        if (Screens.getWidgets(s).any { it.visible && it.isMouseOver(mx, my) }) return true
        if (event.button() != 0) return false
        if (tracking) {
            tracking = false
            return false
        }
        val hit = hitTest() ?: return true
        drag = hit
        dragStartX = mouseX
        dragStartZ = mouseZ
        dragFrom = doubleArrayOf(cx.toDouble(), cz.toDouble(), minX, minZ, maxX, maxZ)
        // A click into the map takes the keyboard away from the boxes, so Xaero's keys work again.
        boxes.values.forEach { it.isFocused = false }
        s.setFocused(null)
        return false
    }

    /** What a press on the map at the mouse would drag, or null to let the map pan. */
    private fun hitTest(): Drag? {
        val shape = build()
        val px = mouseX + 0.5
        val pz = mouseZ + 0.5
        val reach = maxOf(1.0, 6.0 * blocksPerUnit)
        if (type.sized == Shape.Sized.WIDTH_LENGTH) {
            val corners = listOf(doubleArrayOf(minX, minZ, maxX, maxZ), doubleArrayOf(maxX, minZ, minX, maxZ),
                doubleArrayOf(minX, maxZ, maxX, minZ), doubleArrayOf(maxX, maxZ, minX, minZ))
            corners.firstOrNull { hypot(it[0] - px, it[1] - pz) <= reach * 1.5 }?.let {
                dragFixedX = it[2]; dragFixedZ = it[3]
                return Drag.CORNER
            }
            val mx = (minX + maxX) / 2
            val mz = (minZ + maxZ) / 2
            val rect = type == Shape.Type.RECTANGLE
            val alongZ = pz > minZ - reach && pz < maxZ + reach
            val alongX = px > minX - reach && px < maxX + reach
            when {
                hypot(minX - px, mz - pz) <= reach * 1.5 || (rect && abs(px - minX) <= reach && alongZ) -> { dragFixedX = maxX; return Drag.SIDE_X }
                hypot(maxX - px, mz - pz) <= reach * 1.5 || (rect && abs(px - maxX) <= reach && alongZ) -> { dragFixedX = minX; return Drag.SIDE_X }
                hypot(mx - px, minZ - pz) <= reach * 1.5 || (rect && abs(pz - minZ) <= reach && alongX) -> { dragFixedZ = maxZ; return Drag.SIDE_Z }
                hypot(mx - px, maxZ - pz) <= reach * 1.5 || (rect && abs(pz - maxZ) <= reach && alongX) -> { dragFixedZ = minZ; return Drag.SIDE_Z }
            }
        }
        if (shape.distanceToOutline(px, pz) <= reach) return Drag.SIZE
        if (shape.contains(mouseX, mouseZ)) return Drag.MOVE
        return null
    }

    /** A click on the panel below its title, at [lx], [ly] in its own units. */
    private fun clickInside(lx: Double, ly: Double) {
        val info = button ?: return
        val hit = widgets.firstOrNull { it.isMouseOver(lx, ly) }
        boxes.values.forEach { if (it !== hit) it.isFocused = false }
        if (hit != null && hit.mouseClicked(MouseButtonEvent(lx, ly, info), false) && hit is EditBox) hit.isFocused = true
    }

    /** A letter typed on the map, for the box that has the keyboard: true when it took it. */
    @JvmStatic
    fun charTyped(event: CharacterEvent): Boolean {
        if (screen == null) return false
        val box = focusedKey()?.let { boxes[it] } ?: return false
        box.charTyped(event)
        return true
    }

    private fun key(event: KeyEvent): Boolean {
        val code = event.key()
        val focused = focusedKey()
        when (code) {
            InputConstants.KEY_ESCAPE -> { cancel(); return false }
            InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> { enter(focused); return false }
            InputConstants.KEY_UP, InputConstants.KEY_DOWN -> if (focused != null && focused != "name" && boxes[focused]?.value?.isNotBlank() == true) {
                step(focused, code == InputConstants.KEY_UP, event.hasShiftDown())
                return false
            }
        }
        // Typing goes to the box only, so letters never trigger Xaero's hotkeys.
        val box = focused?.let { boxes[it] } ?: return true
        box.keyPressed(event)
        return false
    }

    private fun enter(focused: String?) {
        when (focused) {
            "name" -> arm(true)
            "x" -> focus("z")
            "z" -> focus("y")
            "y" -> arm(false)
            "x1" -> focus("z1")
            "z1" -> focus("x2")
            "x2" -> focus("z2")
            "w" -> { tracking = false; focus("l") }
            else -> commit()
        }
    }

    /** Each frame: the size follows the mouse while tracking or dragging, and each measuring line says its length. */
    private fun frame(graphics: GuiGraphicsExtractor, mouseScreenX: Int, mouseScreenY: Int) {
        val d = drag
        val before = build()
        when {
            d == Drag.MOVE -> {
                val dx = mouseX - dragStartX
                val dz = mouseZ - dragStartZ
                cx = dragFrom[0].toInt() + dx; cz = dragFrom[1].toInt() + dz
                minX = dragFrom[2] + dx; minZ = dragFrom[3] + dz; maxX = dragFrom[4] + dx; maxZ = dragFrom[5] + dz
            }
            d == Drag.SIZE -> if (type.sized == Shape.Sized.RADIUS || !fromCorner) sizeTo(mouseX, mouseZ) else {
                val keep = fromCorner
                fromCorner = false
                sizeTo(mouseX, mouseZ)
                fromCorner = keep
            }
            d == Drag.CORNER -> {
                val ex = edgeFor(mouseX, dragFixedX)
                val ez = edgeFor(mouseZ, dragFixedZ)
                minX = minOf(dragFixedX, ex); maxX = maxOf(dragFixedX, ex)
                minZ = minOf(dragFixedZ, ez); maxZ = maxOf(dragFixedZ, ez)
            }
            d == Drag.SIDE_X -> { val ex = edgeFor(mouseX, dragFixedX); minX = minOf(dragFixedX, ex); maxX = maxOf(dragFixedX, ex) }
            d == Drag.SIDE_Z -> { val ez = edgeFor(mouseZ, dragFixedZ); minZ = minOf(dragFixedZ, ez); maxZ = maxOf(dragFixedZ, ez) }
            tracking && !inside(mouseScreenX.toDouble(), mouseScreenY.toDouble()) -> sizeTo(mouseX, mouseZ)
        }
        if (build() != before) {
            message = null
            sync(null)
        }
        // Each measurement just above the middle of its line (beside it, for a line running up and
        // down), whenever the window is open.
        val font = font()
        val s = screen ?: return
        for ((text, l) in measures()) {
            val a = toScreen(s, l[0], l[1], mouseScreenX, mouseScreenY) ?: return
            val b = toScreen(s, l[2], l[3], mouseScreenX, mouseScreenY) ?: return
            val midX = (a[0] + b[0]) / 2
            val midY = (a[1] + b[1]) / 2
            val w = font.width(text)
            // Centred on the middle of the line, just above it; beside it for a line running up and down.
            val steep = abs(b[0] - a[0]) < abs(b[1] - a[1]) * 0.5
            val tx = (if (steep) midX + 5 else midX - w / 2.0).roundToInt()
            val ty = (if (steep) midY - 4 else midY - 12).roundToInt()
            graphics.fill(tx - 2, ty - 2, tx + w + 2, ty + 10, 0xC0000000.toInt())
            graphics.text(font, text, tx, ty, WHITE, false)
        }
    }

    // ---------- making it ----------

    private fun commit() {
        val s = screen ?: return
        val shape = build()
        val existing = existing
        if (existing != null && ShapeStore.byId(existing.id) == null) return show("${existing.name} is not on your map any more.")
        if (!shape.isValid()) return show("That size or place is beyond any world.")
        if (existing == null && inMiniHud) {
            val made = shape.copy(id = UUID.randomUUID().toString())
            val form = chosenForm()
            MiniHudGeometry.whyTooBig(made, form)?.let { return show(it.substringBefore(" It stays")) }
            val warning = MiniHudGeometry.lagWarning(made, form)
            val asked = "$type ${made.radius} ${made.width} ${made.length} $form"
            if (warning != null && lagWarned != asked) {
                lagWarned = asked
                return show("$warning Press Create again to make it anyway.")
            }
            MiniHudShapes.create(made, y ?: MapMenus.miniHudY(made), null, form)?.let { return show(it.substringBefore(", so it stays")) }
            Log.info("Made {} in MiniHUD from the map", made.name)
        } else if (existing != null) {
            ShapeStore.put(shape.copy(id = existing.id, visible = existing.visible))
        } else {
            ShapeStore.put(shape.copy(id = UUID.randomUUID().toString()))
        }
        close()
        s.setFocused(null)
    }

    private fun cancel() {
        screen?.setFocused(null)
        close()
    }

    private fun show(text: String) {
        message = text
    }

    // ---------- widgets ----------

    /** The window as a map panel: its title, labels and hint, then its boxes and buttons. */
    private class Frame(map: Screen) : DockPanel(map, ID, "Add shape") {
        private var builtWidth = WIDTH
        private var offset = 0
        override var scrollOffset: Int
            get() = offset
            set(value) { offset = value }

        override fun savedLeft(width: Int) = screen.width - Config.addRight - width
        override fun saveLeft(left: Int, width: Int) { Config.addRight = screen.width - left - width }
        override var savedWidth: Int
            get() = Config.addWidth
            set(value) { Config.addWidth = value }
        override var savedTop: Int
            get() = Config.addTop
            set(value) { Config.addTop = value }
        override var savedRows: Int
            get() = itemCount()
            set(_) {}
        override var savedOpen: Boolean
            get() = Config.addOpen
            set(value) { Config.addOpen = value }
        // Docking where it was last docked: the size and width are the stack's, so it opens matching it.
        private val dockOnto: Map<String, Any>? by lazy { bottomOf(Config.addUnder) }
        override var savedScale: Float
            get() = (dockOnto?.get("scale") as? Number)?.toFloat() ?: Config.addScale
            set(value) { Config.addScale = value }
        override var savedExtra: Int
            get() = (dockOnto?.get("extra") as? Number)?.toInt() ?: Config.addExtra
            set(value) { Config.addExtra = value }
        override var savedUnder: String
            get() = (dockOnto?.get("id") as? String) ?: ""
            set(value) { Config.addUnder = value }
        override val maxScale get() = Config.panelMaxScale
        override fun save() = Config.save()

        override val background = BACKGROUND
        override val listTop = CONTENT_TOP
        override val fixedRows get() = true
        override fun naturalWidth() = WIDTH
        override fun itemCount() = maxOf(1, ceil(contentHeight / ROW.toDouble()).toInt())

        /**
         * The bottom panel of the stack the panel called [id] is in, on this screen, to dock under:
         * the one named, unless another panel has been docked under it since.
         */
        private fun bottomOf(id: String): Map<String, Any>? {
            if (id.isEmpty()) return null
            @Suppress("UNCHECKED_CAST")
            val share = FabricLoader.getInstance().objectShare.get("skysmaps:panels") as? Map<String, Any> ?: return null
            val key = System.identityHashCode(screen)
            val records = share.entries.filter { it.key.startsWith("panel:") && it.key != "panel:$ID" }
                .mapNotNull { @Suppress("UNCHECKED_CAST") (it.value as? Map<String, Any>) }
                .filter { it["screen"] == key }
            var at = records.firstOrNull { it["id"] == id } ?: return null
            val seen = hashSetOf(id)
            while (true) {
                val below = records.firstOrNull { it["under"] == at["id"] } ?: return at
                if (!seen.add(below["id"] as? String ?: return at)) return at
                at = below
            }
        }

        override fun afterLayout() {
            // A stack is as wide as its widest panel: the boxes widen with it.
            if (baseWidth != builtWidth) {
                builtWidth = baseWidth
                inner = baseWidth - PAD * 2
                layoutWidgets()
            }
        }

        override fun drawLocal(graphics: GuiGraphicsExtractor, lx: Int, ly: Int, mouseX: Int, mouseY: Int, idle: Boolean, partialTick: Float) {
            val font = font()
            screenMouseX = mouseX
            screenMouseY = mouseY
            if (ly in 0 until ROW && lx in 0 until baseWidth) graphics.fill(0, 0, baseWidth, ROW, 0x30FFFFFF)
            val title = (if (open) "- " else "+ ") + (if (existing == null) "Add shape" else "Edit shape")
            graphics.text(font, title, 3, 2, WHITE, false)
            val esc = "Esc cancels"
            graphics.text(font, esc, baseWidth - 3 - font.width(esc), 2, GREY, false)
            if (!open) return
            fun label(key: String, text: String) {
                boxes[key]?.let { graphics.text(font, text, it.x - font.width(text) - 2, it.y + 3, LABEL, false) }
            }
            label("x", "X"); label("z", "Z"); label("y", "Y")
            if (type.sized == Shape.Sized.RADIUS) {
                label("r", when (type) { Shape.Type.CIRCLE -> "Radius"; Shape.Type.RHOMBUS -> "To point"; else -> "To edge" })
            } else {
                label("w", "W"); label("l", "L"); label("x1", "1"); label("x2", "2")
            }
            val lines = font.split(Component.literal(hint()), inner)
            lines.take(3).forEachIndexed { i, line ->
                graphics.text(font, line, PAD, hintTop + i * LINE, if (AddShapeWindow.message != null) 0xFFFFD27A.toInt() else LABEL, false)
            }
            for (widget in widgets) widget.extractRenderState(graphics, lx, ly, partialTick)
        }

        override fun clickLocal(lx: Double, ly: Double): Click {
            if (ly < ROW) return Click.MOVE
            clickInside(lx, ly)
            return Click.DONE
        }
    }

    /** A small switch in the style of the map panels: lit when on, or a colour swatch. */
    private class Pill(
        x: Int, y: Int, width: Int, height: Int,
        private val text: () -> String,
        private val lit: () -> Boolean,
        private val tip: () -> String?,
        private val swatch: Int? = null,
        enabled: Boolean = true,
        private val action: () -> Unit,
    ) : AbstractWidget(x, y, width, height, Component.empty()) {
        init {
            active = enabled
        }

        override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
            val font = font()
            val hovered = isMouseOver(mouseX.toDouble(), mouseY.toDouble())
            val on = lit()
            if (swatch != null) {
                if (on) graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, WHITE)
                graphics.fill(x, y, x + width, y + height, swatch or 0xFF000000.toInt())
            } else {
                graphics.fill(x, y, x + width, y + height, if (on) 0x60FFFFFF else if (hovered && active) 0x30FFFFFF else 0x20FFFFFF)
                val label = text()
                val shown = if (font.width(label) > width - 2) font.plainSubstrByWidth(label, width - 4) else label
                graphics.text(font, shown, x + (width - font.width(shown)) / 2, y + (height - 8) / 2, if (!active) 0xFF606060.toInt() else if (on) WHITE else LABEL, false)
            }
            if (hovered) tip()?.let { DockPanel.tooltip(graphics, it, screenMouseX, screenMouseY, 180) }
        }

        override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
            if (!active || !visible || event.button() != 0 || !isMouseOver(event.x(), event.y())) return false
            action()
            return true
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) {
            defaultButtonNarrationText(output)
        }
    }
}
