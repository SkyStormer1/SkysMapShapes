package com.skystormer.skysmapshapes.gui

import com.skystormer.skysmapshapes.Colours
import com.skystormer.skysmapshapes.Config
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

/** Widgets more than one screen builds the same way. */
internal object Widgets {

    private const val ROW = 20

    /** A text box holding [value], with [hint] shown greyed while it is empty. */
    fun field(font: Font, x: Int, y: Int, width: Int, value: String, hint: String, maxLength: Int): EditBox {
        val box = EditBox(font, x, y, width, ROW, Component.literal(hint))
        box.setMaxLength(maxLength)
        box.setValue(value)
        box.setHint(Component.literal(hint))
        return box
    }

    /**
     * A button that goes round the named colours, each written in its own colour, starting at
     * [colour]. A colour set by hand in a file is kept as a choice of its own. [label] goes before
     * the colour's name unless [nameOnly].
     */
    fun colourButton(x: Int, y: Int, width: Int, label: String, colour: Int, nameOnly: Boolean = false, onChange: (Int) -> Unit): CycleButton<Int> {
        val choices = Colours.entries.map { it.argb }.let { if (colour in it) it else listOf(colour) + it }
        val builder = CycleButton.builder<Int>({ Component.literal(Colours.name(it)).withStyle { s -> s.withColor(it and 0xFFFFFF) } }, colour)
            .withValues(choices)
        if (nameOnly) builder.displayOnlyValue()
        return builder.create(x, y, width, ROW, Component.literal(label)) { _, value -> onChange(value) }
    }
}

/** Outline thickness, from [Config.MIN_LINE_WIDTH] to [Config.MAX_LINE_WIDTH] pixels in half-pixel steps, on a [ConfigScreen.Slider]. */
internal object Thickness {

    private const val STEP = 0.5f

    private fun sizes(): Int = ((Config.MAX_LINE_WIDTH - Config.MIN_LINE_WIDTH) / STEP).roundToInt() + 1

    /** The slider's place for [width]. */
    fun toSlider(width: Float): Double =
        ((width - Config.MIN_LINE_WIDTH) / STEP).roundToInt().coerceIn(0, sizes() - 1).toDouble() / (sizes() - 1)

    /** The thickness at the slider's place [slider]. */
    fun of(slider: Double): Float = Config.MIN_LINE_WIDTH + (slider * (sizes() - 1)).roundToInt() * STEP

    fun format(width: Float): String = (if (width % 1f == 0f) width.toInt().toString() else width.toString()) + " px"
}
