package com.skystormer.skysmapshapes

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import xaero.lib.client.graphics.XaeroBufferProvider
import xaero.lib.client.gui.widget.Tooltip
import xaero.map.WorldMapSession
import xaero.map.element.MapElementGraphics
import xaero.map.element.render.ElementReader
import xaero.map.element.render.ElementRenderInfo
import xaero.map.element.render.ElementRenderLocation
import xaero.map.element.render.ElementRenderProvider
import xaero.map.element.render.ElementRenderer
import xaero.map.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider
import xaero.map.gui.IRightClickableElement
import xaero.map.gui.dropdown.rightclick.RightClickOption

/**
 * Each shape's label on Xaero's world map, just above the middle of its north edge, through
 * Xaero's own element system (the one its waypoints use) so it can be hovered for the shape's
 * details and right-clicked to edit or delete the shape. Shapes without a label get a small
 * handle there instead, so every shape can be right-clicked.
 */
object ShapeLabels {

    class Context {
        var shapes: List<MapShape> = emptyList()
        var next = 0
        /** How many labels up each shape's is drawn, so labels at the same spot stack instead of overlapping. */
        var stack: Map<String, Int> = emptyMap()
    }

    /** 0 for the first label at a spot, 1 for the next one there, and so on. */
    private fun stackOf(shapes: List<MapShape>): Map<String, Int> {
        val used = HashMap<Pair<Long, Long>, Int>()
        return shapes.associate { shape ->
            val b = shape.bounds
            val spot = Math.round(b.centreX) to Math.round(b.minZ)
            val index = used.getOrDefault(spot, 0)
            used[spot] = index + 1
            shape.id to index
        }
    }

    private fun shapesOnWorldMap(): List<MapShape> {
        if (!Config.enabled || !Config.showOnWorldMap || !Config.showLabels) return emptyList()
        val dimension = WorldMapSession.getCurrentSession()?.mapProcessor?.mapWorld?.currentDimension?.dimId
            ?.identifier()?.toString() ?: return emptyList()
        return MapShapes.visibleIn(dimension)
    }

    class Provider : ElementRenderProvider<MapShape, Context>() {
        override fun begin(location: ElementRenderLocation, context: Context) {
            context.shapes = if (location == ElementRenderLocation.WORLD_MAP) shapesOnWorldMap() else emptyList()
            context.stack = stackOf(context.shapes)
            context.next = 0
        }

        override fun hasNext(location: ElementRenderLocation, context: Context): Boolean = context.next < context.shapes.size

        override fun getNext(location: ElementRenderLocation, context: Context): MapShape = context.shapes[context.next++]

        override fun end(location: ElementRenderLocation, context: Context) {
            context.shapes = emptyList()
            context.stack = emptyMap()
        }
    }

    class Reader : ElementReader<MapShape, Context, Renderer>() {
        override fun isHidden(shape: MapShape, context: Context): Boolean = false
        override fun getRenderX(shape: MapShape, context: Context, partialTicks: Float): Double = shape.bounds.centreX
        override fun getRenderZ(shape: MapShape, context: Context, partialTicks: Float): Double = shape.bounds.minZ
        override fun getRenderY(shape: MapShape, context: Context, partialTicks: Float): Double = 64.0
        override fun hasYCoordinate(): Boolean = false
        // Xaero only hovers and right-clicks elements that say they can be; the default is no.
        override fun isInteractable(location: ElementRenderLocation, shape: MapShape): Boolean = location == ElementRenderLocation.WORLD_MAP
        override fun getInteractionBoxLeft(shape: MapShape, context: Context, partialTicks: Float): Int = -halfWidth(shape)
        override fun getInteractionBoxRight(shape: MapShape, context: Context, partialTicks: Float): Int = halfWidth(shape)
        override fun getInteractionBoxTop(shape: MapShape, context: Context, partialTicks: Float): Int = -HEIGHT - GAP - lift(shape, context)
        override fun getInteractionBoxBottom(shape: MapShape, context: Context, partialTicks: Float): Int = -GAP - lift(shape, context)
        override fun getRenderBoxLeft(shape: MapShape, context: Context, partialTicks: Float): Int = -halfWidth(shape) - 1
        override fun getRenderBoxRight(shape: MapShape, context: Context, partialTicks: Float): Int = halfWidth(shape) + 1
        override fun getRenderBoxTop(shape: MapShape, context: Context, partialTicks: Float): Int = -HEIGHT - GAP - 1 - lift(shape, context)
        override fun getRenderBoxBottom(shape: MapShape, context: Context, partialTicks: Float): Int = 1
        override fun getLeftSideLength(shape: MapShape, minecraft: Minecraft): Int = minecraft.font.width(nameOf(shape)) + 9
        override fun getMenuName(shape: MapShape): String = nameOf(shape)
        override fun getFilterName(shape: MapShape): String = nameOf(shape)
        override fun getMenuTextFillLeftPadding(shape: MapShape): Int = 0
        override fun getRightClickTitleBackgroundColor(shape: MapShape): Int = shape.colour
        override fun shouldScaleBoxWithOptionalScale(): Boolean = true
        override fun isRightClickValid(shape: MapShape): Boolean = true

        override fun getTooltip(shape: MapShape, context: Context, overMenu: Boolean): Tooltip = Tooltip(describe(shape))

        override fun getRightClickOptions(shape: MapShape, target: IRightClickableElement): ArrayList<RightClickOption> {
            val options = ArrayList<RightClickOption>()
            options.add(object : RightClickOption(nameOf(shape), 0, target) {
                override fun onAction(screen: Screen) {}
            })
            MapMenus.addShapeOptions(options, target, shape)
            return options
        }
    }

    class Renderer(context: Context, provider: Provider, reader: Reader) :
        ElementRenderer<MapShape, Context, Renderer>(context, provider, reader) {

        // Xaero draws element layers in ascending order, later ones on top; its claims are 150 and
        // waypoints 200. Below all of them, so labels never cover a waypoint.
        override fun getOrder(): Int = ORDER

        // Xaero renders elements in two passes, shadows first ("pre"); both go through here.
        override fun shouldRender(location: ElementRenderLocation, pre: Boolean): Boolean =
            location == ElementRenderLocation.WORLD_MAP && Config.enabled && Config.showOnWorldMap && Config.showLabels

        override fun preRender(info: ElementRenderInfo, buffers: XaeroBufferProvider, renderers: MultiTextureRenderTypeRendererProvider, pre: Boolean) {
        }

        override fun postRender(info: ElementRenderInfo, buffers: XaeroBufferProvider, renderers: MultiTextureRenderTypeRendererProvider, pre: Boolean) {
            buffers.endBatch()
        }

        override fun renderElementShadow(
            shape: MapShape, hovered: Boolean, scale: Float, partialX: Double, partialY: Double,
            info: ElementRenderInfo, graphics: MapElementGraphics, buffers: XaeroBufferProvider, renderers: MultiTextureRenderTypeRendererProvider,
        ) {
        }

        override fun renderElement(
            shape: MapShape, hovered: Boolean, depth: Double, scale: Float, partialX: Double, partialY: Double,
            info: ElementRenderInfo, graphics: MapElementGraphics, buffers: XaeroBufferProvider, renderers: MultiTextureRenderTypeRendererProvider,
        ): Boolean {
            val pose = graphics.pose()
            pose.pushPose()
            pose.translate(partialX, partialY, 0.0)
            pose.scale(scale, scale, 1f)
            pose.translate(0.0, -lift(shape, context).toDouble(), 0.0)
            val half = halfWidth(shape)
            val background = if (hovered || ShapeHover.isHovered(shape)) 0xDD000000.toInt() else 0x99000000.toInt()
            graphics.fill(-half, -HEIGHT - GAP, half, -GAP, background)
            if (shape.label.isBlank()) {
                graphics.fill(-3, -HEIGHT - GAP + 2, 3, -GAP - 2, shape.colour)
            } else {
                graphics.drawCenteredString(Minecraft.getInstance().font, shape.label, 0, -HEIGHT - GAP + 2, shape.colour)
            }
            pose.popPose()
            return true
        }
    }

    /** Registers with Xaero's world map, once it has started. */
    fun register(): Boolean {
        val handler = xaero.map.WorldMap.mapElementRenderHandler ?: return false
        handler.add(Renderer(Context(), Provider(), Reader()))
        return true
    }

    fun nameOf(shape: MapShape): String = shape.name

    fun describe(shape: MapShape): Component = Component.literal(nameOf(shape)).append(
        Component.literal("\n${shape.describeSize()}\n${shape.describePosition()}\n" + if (shape.fromMiniHud) "From MiniHUD · right-click to edit or hide" else "Right-click to edit or delete")
            .withStyle { it.withColor(0xDDDDDD) }
    )

    private fun lift(shape: MapShape, context: Context): Int = (context.stack[shape.id] ?: 0) * (HEIGHT + 1)

    private fun halfWidth(shape: MapShape): Int =
        if (shape.label.isBlank()) 5 else Minecraft.getInstance().font.width(shape.label) / 2 + 3

    private const val ORDER = -100
    private const val HEIGHT = 11
    private const val GAP = 2
}
