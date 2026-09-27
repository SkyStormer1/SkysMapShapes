package com.skystormer.skysmapshapes

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import xaero.lib.client.graphics.XaeroBufferProvider
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import org.joml.Matrix4f
import org.joml.Vector3f
import xaero.lib.XaeroLib
import xaero.map.MapProcessor
import xaero.map.graphics.CustomRenderTypes
import kotlin.math.sqrt

/**
 * Draws the shapes for the dimension on screen, on Xaero's world map and minimap, as filled
 * insides and outlines of constant screen thickness. Labels are drawn separately, by
 * [ShapeLabels], through Xaero's element system so that they can be hovered and right-clicked.
 */
object ShapeDrawing {

    /** Whether Xaero's world map has called in at least once, so it is said once only. */
    private var worldMapHookRan = false

    @JvmStatic
    fun drawWorldMap(
        mapProcessor: MapProcessor, matrix: Matrix4f, originX: Int, originZ: Int, cameraX: Double, cameraZ: Double,
        mouseX: Int, mouseZ: Int, updateHover: Boolean,
    ) {
        if (!worldMapHookRan) {
            worldMapHookRan = true
            Log.info("World map hook working")
        }
        if (!Config.enabled || !Config.showOnWorldMap) return ShapeHover.clear()
        try {
            val shapes = shapesFor(mapProcessor) ?: return ShapeHover.clear()
            val blocksPerUnit = blocksPerUnit(matrix)
            if (updateHover) ShapeHover.update(shapes, mouseX, mouseZ, blocksPerUnit)
            // How much of the world is on screen. Xaero's units are at most window pixels, so
            // measuring with the window's size never sees too little.
            val window = Minecraft.getInstance().window
            val halfWidth = window.width * blocksPerUnit * 0.5 + 16
            val halfHeight = window.height * blocksPerUnit * 0.5 + 16
            val view = Geometry.Bounds(cameraX - halfWidth, cameraZ - halfHeight, cameraX + halfWidth, cameraZ + halfHeight)
            val buffer = XaeroLib.INSTANCE.client.bufferProvider.getBuffer(CustomRenderTypes.MAP_COLOR_OVERLAY)
            draw(buffer, matrix, shapes, originX, originZ, blocksPerUnit, view)
        } catch (e: Throwable) {
            failOnce("world map", e)
        }
    }

    private var minimapHookRan = false

    /** Set when the minimap's world-map path has already drawn this frame, so it is not drawn twice. */
    private var drawnFromWorldMap = false

    @JvmStatic
    fun drawMinimap(
        mapProcessor: MapProcessor, pose: PoseStack, originX: Int, originZ: Int,
        minViewX: Int, minViewZ: Int, maxViewX: Int, maxViewZ: Int, buffer: VertexConsumer,
    ) {
        if (!minimapHookRan) {
            minimapHookRan = true
            Log.info("Minimap hook working (from the world map's data)")
        }
        drawnFromWorldMap = true
        if (!Config.enabled || !Config.showOnMinimap) return
        try {
            val shapes = shapesFor(mapProcessor) ?: return
            val matrix = pose.last().pose()
            // The minimap's view bounds come in Xaero's 64-block units.
            val view = Geometry.Bounds(minViewX * 64.0, minViewZ * 64.0, (maxViewX + 1) * 64.0, (maxViewZ + 1) * 64.0)
            draw(buffer, matrix, shapes, originX, originZ, blocksPerUnit(matrix), view)
        } catch (e: Throwable) {
            failOnce("minimap", e)
        }
    }

    /**
     * The other way the minimap draws: from its own records rather than the world map's, which is
     * what it does underground in cave mode. Called where the two ways meet, so this draws only
     * when the world-map path has not already done it this frame.
     *
     * Nothing here says how much of the world is on screen, so everything within [MINIMAP_REACH]
     * of the middle is drawn and the minimap's own edges clip it.
     */
    @JvmStatic
    fun drawMinimapAnyMode(pose: PoseStack, renderPos: Vec3, mapDimension: ResourceKey<Level>?, buffers: XaeroBufferProvider) {
        if (!caveHookRan) {
            caveHookRan = true
            Log.info("Minimap hook working (from the minimap's own data, as underground and in the Nether)")
        }
        val alreadyDrawn = drawnFromWorldMap
        drawnFromWorldMap = false
        if (alreadyDrawn || !Config.enabled || !Config.showOnMinimap) return
        try {
            val dimension = mapDimension?.identifier()?.toString() ?: return
            val shapes = MapShapes.visibleIn(dimension).takeIf { it.isNotEmpty() } ?: return
            val originX = Math.floor(renderPos.x).toInt()
            val originZ = Math.floor(renderPos.z).toInt()
            val matrix = pose.last().pose()
            val view = Geometry.Bounds(
                originX - MINIMAP_REACH, originZ - MINIMAP_REACH,
                originX + MINIMAP_REACH, originZ + MINIMAP_REACH,
            )
            val buffer = buffers.getBuffer(xaero.common.graphics.CustomRenderTypes.MAP_CHUNK_OVERLAY)
            draw(buffer, matrix, shapes, originX, originZ, blocksPerUnit(matrix), view)
        } catch (e: Throwable) {
            failOnce("minimap in this mode", e)
        }
    }

    private var caveHookRan = false

    /** How far from the middle of the minimap shapes are drawn, in blocks. */
    private const val MINIMAP_REACH = 4096.0

    private fun shapesFor(mapProcessor: MapProcessor): List<MapShape>? {
        val dimension = mapProcessor.mapWorld?.currentDimension?.dimId?.identifier()?.toString() ?: return null
        return MapShapes.visibleIn(dimension).takeIf { it.isNotEmpty() }
    }

    private fun blocksPerUnit(matrix: Matrix4f): Float =
        Matrix4f(matrix).invert().transformDirection(Vector3f(1f, 0f, 0f)).length().coerceAtLeast(1e-4f)

    private fun draw(
        buffer: VertexConsumer, matrix: Matrix4f, shapes: List<MapShape>, originX: Int, originZ: Int,
        blocksPerUnit: Float, view: Geometry.Bounds,
    ) {
        for (shape in shapes) {
            val halfLine = shape.effectiveLineWidth * blocksPerUnit * 0.5
            val b = shape.bounds
            if (b.maxX + halfLine < view.minX || b.minX - halfLine > view.maxX || b.maxZ + halfLine < view.minZ || b.minZ - halfLine > view.maxZ) continue
            val geometry = shape.geometry
            val points = geometry.points(blocksPerUnit)
            val closed = geometry.closed
            val red = ((shape.colour shr 16) and 0xFF) / 255f
            val green = ((shape.colour shr 8) and 0xFF) / 255f
            val blue = (shape.colour and 0xFF) / 255f
            if (closed && shape.fill && Config.fillOpacity > 0f) {
                fill(buffer, matrix, points, b.centreX - originX, b.centreZ - originZ, originX, originZ, red, green, blue, Config.fillOpacity)
            }
            // A hovered outline is drawn twice as thick, over a dark edge so it stands out on any colour.
            if (ShapeHover.isHovered(shape)) {
                ring(buffer, matrix, points, closed, halfLine * 3, originX, originZ, 0f, 0f, 0f)
                ring(buffer, matrix, points, closed, halfLine * 2, originX, originZ, red, green, blue)
            } else {
                ring(buffer, matrix, points, closed, halfLine, originX, originZ, red, green, blue)
            }
        }
    }

    /**
     * The inside, as a fan of triangles from the centre. The buffer takes quads, so each triangle
     * is a quad with two corners at the centre.
     */
    private fun fill(
        buffer: VertexConsumer, matrix: Matrix4f, points: DoubleArray, centreX: Double, centreZ: Double,
        originX: Int, originZ: Int, red: Float, green: Float, blue: Float, alpha: Float,
    ) {
        val corners = points.size / 2
        val cx = centreX.toFloat()
        val cz = centreZ.toFloat()
        for (i in 0 until corners) {
            val j = (i + 1) % corners
            buffer.addVertex(matrix, cx, cz, 0f).setColor(red, green, blue, alpha)
            buffer.addVertex(matrix, (points[j * 2] - originX).toFloat(), (points[j * 2 + 1] - originZ).toFloat(), 0f).setColor(red, green, blue, alpha)
            buffer.addVertex(matrix, (points[i * 2] - originX).toFloat(), (points[i * 2 + 1] - originZ).toFloat(), 0f).setColor(red, green, blue, alpha)
            buffer.addVertex(matrix, cx, cz, 0f).setColor(red, green, blue, alpha)
        }
    }

    /**
     * The outline, one quad per side, widened sideways by [half] and lengthened by it at each end
     * so that corners meet.
     */
    private fun ring(
        buffer: VertexConsumer, matrix: Matrix4f, points: DoubleArray, closed: Boolean, half: Double,
        originX: Int, originZ: Int, red: Float, green: Float, blue: Float,
    ) {
        val corners = points.size / 2
        for (i in 0 until if (closed) corners else corners - 1) {
            val j = (i + 1) % corners
            val x1 = points[i * 2]
            val z1 = points[i * 2 + 1]
            val x2 = points[j * 2]
            val z2 = points[j * 2 + 1]
            val dx = x2 - x1
            val dz = z2 - z1
            val length = sqrt(dx * dx + dz * dz)
            if (length < 1e-6) continue
            val nx = (-dz / length * half).toFloat()
            val nz = (dx / length * half).toFloat()
            val ex = dx / length * half
            val ez = dz / length * half
            val ax = (x1 - ex - originX).toFloat()
            val az = (z1 - ez - originZ).toFloat()
            val bx = (x2 + ex - originX).toFloat()
            val bz = (z2 + ez - originZ).toFloat()
            buffer.addVertex(matrix, ax + nx, az + nz, 0f).setColor(red, green, blue, 1f)
            buffer.addVertex(matrix, bx + nx, bz + nz, 0f).setColor(red, green, blue, 1f)
            buffer.addVertex(matrix, bx - nx, bz - nz, 0f).setColor(red, green, blue, 1f)
            buffer.addVertex(matrix, ax - nx, az - nz, 0f).setColor(red, green, blue, 1f)
        }
    }

    private val failed = HashSet<String>()

    private fun failOnce(where: String, e: Throwable) {
        if (failed.add(where)) Log.error("Could not draw shapes on the $where", e)
    }
}
