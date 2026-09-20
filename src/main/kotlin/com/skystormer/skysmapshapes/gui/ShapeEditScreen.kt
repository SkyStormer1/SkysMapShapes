package com.skystormer.skysmapshapes.gui

import com.skystormer.skysmapshapes.Colours
import com.skystormer.skysmapshapes.Config
import com.skystormer.skysmapshapes.MapMenus
import com.skystormer.skysmapshapes.MiniHudShapes
import com.skystormer.skysmapshapes.Shape
import com.skystormer.skysmapshapes.ShapeShare
import com.skystormer.skysmapshapes.ShapeStore
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.MultiLineTextWidget
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import xaero.map.mods.SupportMods
import kotlin.math.floor

/**
 * Adding or editing one shape: its label, kind, colour, where it is (typed, from a waypoint or
 * from where you stand) and its size in blocks. Presets fill in a circle's radius and colour in
 * one click; "Add all presets" adds one circle per preset at once, for a despawn sphere's rings.
 */
class ShapeEditScreen private constructor(
    private val parent: Screen?,
    /** The shape being edited, or null for a new one. */
    private val existing: Shape?,
    private val dimension: String,
    x: Int,
    z: Int,
    label: String,
    /** A shape to copy the settings of into a new one, as a shared shape does. */
    template: Shape? = null,
    /** How a shared shape was made in MiniHUD, when it was: its kind and the height it sat at. */
    private val sharedMiniHudType: String? = null,
    private val sharedY: Int? = null,
) : Screen(Component.literal(if (existing == null) "Add a shape" else "Edit shape")) {

    /** Where the fields start from: the shape being edited, or one being copied. */
    private val from: Shape? = existing ?: template

    // What is being edited, kept across rebuildWidgets (which changing the kind of shape causes).
    private var label = label
    private var type = from?.type ?: Shape.Type.CIRCLE
    private var colour = from?.colour ?: Config.presets.firstOrNull()?.colour ?: Colours.RED.argb
    private var fill = from?.fill ?: false
    private var anchor = from?.anchor ?: Shape.Anchor.CENTRE
    private var lineWidth: Float = from?.lineWidth ?: Config.DEFAULT_LINE_WIDTH

    /** A new shape can be made in MiniHUD instead, when MiniHUD is installed. */
    private var inMiniHud = sharedMiniHudType != null && MiniHudShapes.installed && Config.showMiniHud &&
        Minecraft.getInstance().player?.level()?.dimension()?.identifier()?.toString() == dimension

    private var xText = x.toString()
    private var zText = z.toString()
    private var radiusText = from?.takeIf { it.type.sized == Shape.Sized.RADIUS }?.radius?.let(Shape::number)
        ?: Config.presets.firstOrNull()?.radius?.let(Shape::number) ?: "128"
    private var widthText = from?.takeIf { it.type.sized == Shape.Sized.WIDTH_LENGTH }?.width?.let(Shape::number) ?: "32"
    private var lengthText = from?.takeIf { it.type.sized == Shape.Sized.WIDTH_LENGTH }?.length?.let(Shape::number) ?: "32"
    private var message: Component =
        if (existing == null && template != null)
            Component.literal(
                "Shared with you, for the ${ShapeShare.dimensionName(template.dimension)}. " +
                    if (sharedMiniHudType != null && MiniHudShapes.installed) "It came from MiniHUD, so it can go in your world too."
                    else "Add keeps it on your map."
            )
        else Component.empty()

    private lateinit var labelBox: EditBox
    private lateinit var xBox: EditBox
    private lateinit var zBox: EditBox
    private var radiusBox: EditBox? = null
    private var widthBox: EditBox? = null
    private var lengthBox: EditBox? = null
    private lateinit var messageWidget: MultiLineTextWidget

    override fun init() {
        val left = width / 2 - WIDTH / 2
        val third = (WIDTH - GAP * 2) / 3
        var y = maxOf(4, (height - 296) / 2)

        addRenderableWidget(StringWidget(left, y, WIDTH, font.lineHeight, title, font))
        y += font.lineHeight + GAP * 2

        label(left, y, LABEL, "Label")
        labelBox = field(left + LABEL, y, WIDTH - LABEL, label, "optional, e.g. AFK spot", 64)
        y += ROW + GAP

        addRenderableWidget(
            CycleButton.builder<Shape.Type>({ Component.literal(it.title) }, type)
                .withValues(Shape.Type.entries)
                .create(left, y, third, ROW, Component.literal("Shape")) { _, value -> keepEdits(); type = value; rebuildWidgets() }
        )
        addRenderableWidget(colourButton(left + third + GAP, y, third))
        addRenderableWidget(
            CycleButton.onOffBuilder(fill)
                .create(left + (third + GAP) * 2, y, third, ROW, Component.literal("Fill")) { _, on -> fill = on }
                .also { it.setTooltip(Tooltip.create(Component.literal("Shade the inside as well as drawing the outline. How strongly is in the settings."))) }
        )
        y += ROW + GAP

        val positionName = if (type == Shape.Type.RECTANGLE && anchor == Shape.Anchor.CORNER) "Corner" else "Centre"
        val boxWidth = 60
        label(left, y, LABEL, "$positionName X")
        xBox = field(left + LABEL, y, boxWidth, xText, "X", 12)
        label(left + LABEL + boxWidth + 6, y, 12, "Z")
        zBox = field(left + LABEL + boxWidth + 18, y, boxWidth, zText, "Z", 12)
        val hereLeft = left + LABEL + boxWidth * 2 + 18 + GAP
        addRenderableWidget(
            Button.builder(Component.literal("Where I am")) { useMyPosition() }
                .bounds(hereLeft, y, left + WIDTH - hereLeft, ROW)
                .tooltip(Tooltip.create(Component.literal("Use the block you are standing on.")))
                .build()
        )
        y += ROW + GAP

        val waypoints = waypoints()
        if (waypoints.isNotEmpty()) {
            addRenderableWidget(
                CycleButton.builder<Int>({ i -> Component.literal(if (i < 0) "Pick a waypoint…" else waypoints[i].first) }, -1)
                    .withValues(listOf(-1) + waypoints.indices)
                    .displayOnlyValue()
                    .create(left, y, WIDTH, ROW, Component.literal("Waypoint")) { _, i -> if (i >= 0) useWaypoint(waypoints[i]) }
                    .also { it.setTooltip(Tooltip.create(Component.literal("Centre the shape on one of your waypoints on this map, including ones saved from BlueMap by Sky's Map Exposer."))) }
            )
            y += ROW + GAP
        }

        radiusBox = null
        widthBox = null
        lengthBox = null
        when (type.sized) {
            Shape.Sized.RADIUS -> {
                val (name, explanation) = when (type) {
                    Shape.Type.CIRCLE -> "Radius" to "Distance from the centre to the edge, in blocks."
                    Shape.Type.RHOMBUS -> "Centre to point" to "Distance from the centre to each of the four points (north, east, south, west), in blocks."
                    Shape.Type.OCTAGON -> "Centre to edge" to "Distance from the centre to each flat side, in blocks."
                    else -> "Centre to edge" to "Distance from the centre to each side, in blocks: a square twice this wide."
                }
                label(left, y, LABEL, name)
                radiusBox = field(left + LABEL, y, boxWidth, radiusText, "blocks", 10)
                label(left + LABEL + boxWidth + 6, y, 60, "blocks")
                radiusBox!!.setTooltip(Tooltip.create(Component.literal(explanation)))
            }
            Shape.Sized.WIDTH_LENGTH -> {
                label(left, y, LABEL, "Width (X)")
                widthBox = field(left + LABEL, y, boxWidth, widthText, "blocks", 10)
                label(left + LABEL + boxWidth + 6, y, 60, "Length (Z)")
                lengthBox = field(left + LABEL + boxWidth + 66, y, boxWidth, lengthText, "blocks", 10)
                if (type == Shape.Type.RECTANGLE) {
                y += ROW + GAP
                addRenderableWidget(
                    CycleButton.builder<Shape.Anchor>({ Component.literal(it.title) }, anchor)
                        .withValues(Shape.Anchor.entries)
                        .create(left, y, WIDTH, ROW, Component.literal("X and Z are the")) { _, value -> keepEdits(); anchor = value; rebuildWidgets() }
                        .also { it.setTooltip(Tooltip.create(Component.literal("Centre: the rectangle is centred on X, Z. North-west corner: it starts at X, Z and goes east and south, for lining up with block or chunk edges."))) }
                )
                }
            }
        }
        y += ROW + GAP

        addRenderableWidget(ThicknessSlider(left, y, WIDTH, ROW, lineWidth,
            "How thick this shape's outline is drawn, whatever the zoom. The Thickness scale in the settings makes every shape thicker or thinner at once.") { lineWidth = it })
        y += ROW + GAP

        // Presets are for shaping a new one; editing an existing shape keeps the screen shorter.
        val presets = Config.presets
        if (existing == null && presets.isNotEmpty()) {
            val each = (WIDTH - GAP * (presets.size - 1)) / presets.size
            presets.forEachIndexed { i, preset ->
                addRenderableWidget(
                    Button.builder(Component.literal("${preset.name} ${Shape.number(preset.radius)}")) { usePreset(preset) }
                        .bounds(left + (each + GAP) * i, y, each, ROW)
                        .tooltip(Tooltip.create(Component.literal("A ${Colours.name(preset.colour).lowercase()} circle of radius ${Shape.number(preset.radius)}. Change presets in the settings.")))
                        .build()
                )
            }
            y += ROW + GAP
        }

        // Where a new shape goes: here, or MiniHUD, which draws it in the world too.
        if (existing == null && MiniHudShapes.installed && Config.showMiniHud) {
            val inThisDimension = minecraft.player?.level()?.dimension()?.identifier()?.toString() == dimension
            addRenderableWidget(
                CycleButton.builder<Boolean>({ Component.literal(if (it) "Make it in MiniHUD" else "Make it in Sky's Map Shapes") }, inMiniHud)
                    .withValues(listOf(false, true))
                    .displayOnlyValue()
                    .create(left, y, WIDTH, ROW, Component.literal("Where")) { _, value -> inMiniHud = value }
                    .also {
                        it.active = inThisDimension
                        it.setTooltip(Tooltip.create(Component.literal(
                            if (inThisDimension)
                                "MiniHUD: it becomes an ordinary MiniHUD shape, shown in the world as well as on the map, and edited in MiniHUD. " +
                                    "A flat shape needs a height, so it is put at your feet: spheres centred there, prisms and boxes ${MiniHudShapes.PRISM_HEIGHT} blocks tall around it. Change it afterwards in MiniHUD's editor."
                            else "MiniHUD only holds shapes for the dimension you are in."
                        )))
                    }
            )
            y += ROW + GAP
        }

        messageWidget = MultiLineTextWidget(left, y + 2, message, font).setMaxWidth(WIDTH).setMaxRows(2)
        addRenderableWidget(messageWidget)
        y += font.lineHeight * 2 + GAP * 2

        // What can be done with a shape already kept: its own row, above the usual buttons.
        if (existing != null) {
            val toMiniHud = MiniHudShapes.installed && Config.showMiniHud
            val shareWidth = if (toMiniHud) (WIDTH - GAP) / 2 else WIDTH
            addRenderableWidget(
                Button.builder(Component.literal("Share in chat…")) { MapMenus.confirmShare(this, existing) }
                    .bounds(left, y, shareWidth, ROW)
                    .tooltip(Tooltip.create(Component.literal("Send this shape to everyone in chat. Anyone with this mod can click to add it to their own map.")))
                    .build()
            )
            if (toMiniHud) {
                addRenderableWidget(
                    // Back to the map or list, not here: the shape is gone from this mod once moved.
                    Button.builder(Component.literal("Move into MiniHUD…")) { MapMenus.confirmMoveToMiniHud(parent, existing) }
                        .bounds(left + shareWidth + GAP, y, WIDTH - shareWidth - GAP, ROW)
                        .tooltip(Tooltip.create(Component.literal("Turn this into a MiniHUD shape, shown in the world as well as on the map instead.")))
                        .build()
                )
            }
            y += ROW + GAP
        }

        // Keep, undo, and the one that throws it away, always in the same places.
        val keep = Button.builder(Component.literal(if (existing == null) "Add" else "Save")) { save() }
        val extra: Button.Builder? = when {
            existing != null -> Button.builder(Component.literal("Delete…")) { MapMenus.confirmDelete(parent, existing) }
            presets.size > 1 -> Button.builder(Component.literal("Add all presets")) { addAllPresets() }
                .tooltip(Tooltip.create(Component.literal(
                    "Add one circle per preset at this position: " +
                        presets.joinToString(", ") { "${it.name} ${Shape.number(it.radius)}" } + "."
                )))
            else -> null
        }
        if (extra == null) {
            val half = (WIDTH - GAP) / 2
            addRenderableWidget(keep.bounds(left, y, half, ROW).build())
            addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL) { onClose() }.bounds(left + half + GAP, y, WIDTH - half - GAP, ROW).build())
        } else {
            addRenderableWidget(keep.bounds(left, y, third, ROW).build())
            addRenderableWidget(extra.bounds(left + third + GAP, y, third, ROW).build())
            addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL) { onClose() }.bounds(left + (third + GAP) * 2, y, third, ROW).build())
        }
    }

    private fun colourButton(x: Int, y: Int, width: Int): CycleButton<Int> {
        // A colour set by hand in the file is kept as a choice of its own.
        val choices = Colours.entries.map { it.argb }.let { if (colour in it) it else listOf(colour) + it }
        return CycleButton.builder<Int>({ Component.literal(Colours.name(it)).withStyle { s -> s.withColor(it and 0xFFFFFF) } }, colour)
            .withValues(choices)
            .create(x, y, width, ROW, Component.literal("Colour")) { _, value -> colour = value }
    }

    /** The waypoints on Xaero's world map for this dimension, as names and block positions. */
    private fun waypoints(): List<Triple<String, Int, Int>> = try {
        if (!SupportMods.minimap() || MapMenus.mapDimension() != dimension) emptyList()
        else SupportMods.xaeroMinimap.waypointsSorted.orEmpty()
            .map { Triple(it.name, floor(it.renderX).toInt(), floor(it.renderZ).toInt()) }
    } catch (e: Throwable) {
        emptyList()
    }

    private fun useWaypoint(waypoint: Triple<String, Int, Int>) {
        xBox.value = waypoint.second.toString()
        zBox.value = waypoint.third.toString()
        if (labelBox.value.isBlank()) labelBox.value = waypoint.first
        show("Centred on ${waypoint.first}.")
    }

    private fun useMyPosition() {
        val player = minecraft.player ?: return
        if (player.level().dimension().identifier().toString() != dimension) {
            show("You are in another dimension from this map.")
            return
        }
        xBox.value = player.blockX.toString()
        zBox.value = player.blockZ.toString()
        show("Using where you stand: ${player.blockX}, ${player.blockZ}.")
    }

    private fun usePreset(preset: Config.Preset) {
        keepEdits()
        type = Shape.Type.CIRCLE
        radiusText = Shape.number(preset.radius)
        colour = preset.colour
        if (label.isBlank() || Config.presets.any { it.name == label }) label = preset.name
        message = Component.literal("${preset.name}: radius ${Shape.number(preset.radius)}.")
        rebuildWidgets()
    }

    /** Copies what is typed into the fields above, before the widgets are thrown away. */
    private fun keepEdits() {
        label = labelBox.value
        xText = xBox.value
        zText = zBox.value
        radiusBox?.let { radiusText = it.value }
        widthBox?.let { widthText = it.value }
        lengthBox?.let { lengthText = it.value }
    }

    private fun save() {
        keepEdits()
        // It may have been deleted or moved into MiniHUD while this screen stayed open.
        if (existing != null && ShapeStore.byId(existing.id) == null) {
            return show("${existing.name} is not on your map any more.")
        }
        val x = xText.toIntOrNull() ?: return show("Type a whole number for X.")
        val z = zText.toIntOrNull() ?: return show("Type a whole number for Z.")
        val shape = when (type.sized) {
            Shape.Sized.RADIUS -> {
                val radius = size(radiusText) ?: return show("Type a size in blocks, more than 0.")
                Shape(label = label.trim(), dimension = dimension, type = type, x = x, z = z, radius = radius, colour = colour, fill = fill, lineWidth = lineWidth)
            }
            Shape.Sized.WIDTH_LENGTH -> {
                val w = size(widthText) ?: return show("Type a width in blocks, more than 0.")
                val l = size(lengthText) ?: return show("Type a length in blocks, more than 0.")
                Shape(label = label.trim(), dimension = dimension, type = type, x = x, z = z, width = w, length = l, anchor = if (type == Shape.Type.RECTANGLE) anchor else Shape.Anchor.CENTRE, colour = colour, fill = fill, lineWidth = lineWidth)
            }
        }
        if (existing == null && inMiniHud) {
            if (!MiniHudShapes.create(shape, sharedY ?: playerY(), sharedMiniHudType)) {
                return show("MiniHUD would not take that shape; the log says why.")
            }
            MapMenus.say("Made ${shape.name} in MiniHUD")
        } else {
            ShapeStore.put(if (existing != null) shape.copy(id = existing.id, visible = existing.visible, y = existing.y) else shape)
        }
        minecraft.gui.setScreen(parent)
    }

    private fun addAllPresets() {
        keepEdits()
        val x = xText.toIntOrNull() ?: return show("Type a whole number for X.")
        val z = zText.toIntOrNull() ?: return show("Type a whole number for Z.")
        val base = label.trim().takeUnless { name -> name.isEmpty() || Config.presets.any { it.name == name } }
        for (preset in Config.presets) {
            val shape = Shape(
                label = if (base == null) preset.name else "$base: ${preset.name}",
                dimension = dimension, type = Shape.Type.CIRCLE, x = x, z = z,
                radius = preset.radius, colour = preset.colour, fill = fill, lineWidth = lineWidth,
            )
            if (inMiniHud) MiniHudShapes.create(shape, playerY()) else ShapeStore.put(shape)
        }
        MapMenus.say("Added ${Config.presets.size} ${if (inMiniHud) "spheres in MiniHUD" else "circles"} at $x, $z")
        minecraft.gui.setScreen(parent)
    }

    /** The height a shape made in MiniHUD is put at: where you stand, or the sea level if elsewhere. */
    private fun playerY(): Int {
        val player = minecraft.player ?: return 64
        return if (player.level().dimension().identifier().toString() == dimension) player.blockY else 64
    }

    private fun size(text: String): Double? = text.trim().toDoubleOrNull()?.takeIf { it > 0 && it <= MAX_SIZE }

    private fun show(text: String) {
        message = Component.literal(text)
        messageWidget.setMessage(message)
    }

    override fun onClose() {
        minecraft.gui.setScreen(parent)
    }

    private fun label(x: Int, y: Int, width: Int, text: String) {
        addRenderableWidget(StringWidget(x, y + 6, width, font.lineHeight, Component.literal(text), font))
    }

    private fun field(x: Int, y: Int, width: Int, value: String, hint: String, maxLength: Int): EditBox {
        val box = EditBox(font, x, y, width, ROW, Component.literal(hint))
        box.setMaxLength(maxLength)
        box.setValue(value)
        box.setHint(Component.literal(hint))
        return addRenderableWidget(box)
    }

    companion object {
        /** A shape someone shared in chat, to look at before it is kept. */
        fun forShared(parent: Screen?, shared: ShapeShare.Shared) = ShapeEditScreen(
            parent, null, shared.shape.dimension, shared.shape.x, shared.shape.z, shared.shape.label,
            shared.shape, shared.miniHudType, shared.y,
        )

        fun forNew(parent: Screen?, dimension: String, x: Int, z: Int, label: String) =
            ShapeEditScreen(parent, null, dimension, x, z, label)

        fun forExisting(parent: Screen?, shape: Shape) =
            ShapeEditScreen(parent, shape, shape.dimension, shape.x, shape.z, shape.label)

        private const val WIDTH = 300
        private const val ROW = 20
        private const val GAP = 2
        private const val LABEL = 72
        private const val MAX_SIZE = 1_000_000.0
    }
}
