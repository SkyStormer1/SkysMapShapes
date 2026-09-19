package com.skystormer.skysmapshapes.gui

import com.skystormer.skysmapshapes.Config
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

/** Outline thickness, from [Config.MIN_LINE_WIDTH] to [Config.MAX_LINE_WIDTH] pixels in half-pixel steps. */
class ThicknessSlider(
    x: Int, y: Int, width: Int, height: Int, initial: Float,
    explanation: String,
    private val onChange: (Float) -> Unit,
) : AbstractSliderButton(x, y, width, height, Component.empty(), toSlider(initial)) {

    init {
        updateMessage()
        setTooltip(Tooltip.create(Component.literal(explanation)))
    }

    val thickness: Float
        get() = Config.MIN_LINE_WIDTH + (value * (sizes() - 1)).roundToInt() * STEP

    override fun updateMessage() {
        message = Component.literal("Thickness: ${format(thickness)}")
    }

    override fun applyValue() = onChange(thickness)

    companion object {
        private const val STEP = 0.5f

        private fun sizes(): Int = ((Config.MAX_LINE_WIDTH - Config.MIN_LINE_WIDTH) / STEP).roundToInt() + 1

        private fun toSlider(value: Float): Double =
            ((value - Config.MIN_LINE_WIDTH) / STEP).roundToInt().coerceIn(0, sizes() - 1).toDouble() / (sizes() - 1)

        fun format(width: Float): String = (if (width % 1f == 0f) width.toInt().toString() else width.toString()) + " px"
    }
}
