package com.gop.survey.corporatefarm.common

import com.esri.arcgisruntime.geometry.Geometry
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.Polygon
import com.esri.arcgisruntime.geometry.PolygonBuilder
import com.esri.arcgisruntime.geometry.PolylineBuilder
import com.esri.arcgisruntime.mapping.view.Graphic
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay
import com.esri.arcgisruntime.mapping.view.MapView

class ParcelDrawController(
    private val mapView: MapView,
    private val isInsideExistingParcel: (Point) -> Boolean,
    private val snapPoint: (Point) -> Point = { it }
) {

    companion object {
        const val MIN_POINTS = 3
    }

    /** Add this to the map view's overlays so the preview is visible. */
    val overlay = GraphicsOverlay()

    /** AOI boundary the surveyor must stay inside. Set it whenever the map reloads. */
    var boundary: Geometry? = null

    private val points = mutableListOf<Point>()
    val placedPoints: List<Point> get() = points

    var isActive: Boolean = false
        private set

    val pointCount: Int get() = points.size

    val hasBoundary: Boolean get() = boundary != null

    val canSave: Boolean get() = points.size >= MIN_POINTS

    // ==================================================================
    // MODE
    // ==================================================================

    /** Enters draw mode. Fails when no boundary is available. */
    fun start(): DrawResult {
        if (boundary == null) return DrawResult.NoBoundary

        isActive = true
        points.clear()
        overlay.graphics.clear()
        return DrawResult.Ok
    }

    /** Leaves draw mode and clears the preview. Safe to call at any time. */
    fun cancel() {
        isActive = false
        points.clear()
        overlay.graphics.clear()
    }

    // ==================================================================
    // VERTICES
    // ==================================================================

    /** Validates the tap and, if acceptable, adds it as a vertex. */
    fun addPoint(mapPoint: Point): DrawResult {
        val point = snapPoint(mapPoint)
        if (!GeometryUtils.contains(boundary, point)) {
            return DrawResult.OutsideBoundary
        }

        if (isInsideExistingParcel(point)) {
            return DrawResult.InsideExistingParcel
        }

        points.add(point)
        refreshPreview()
        return DrawResult.PointAdded(points.size)
    }

    /** Removes the most recent vertex. Returns false when there was nothing to undo. */
    fun undoLastPoint(): Boolean {
        if (points.isEmpty()) return false
        points.removeAt(points.lastIndex)
        refreshPreview()
        return true
    }

    // ==================================================================
    // RESULT
    // ==================================================================

    /**
     * Builds the traced polygon.
     * Returns null when there are too few points or the map is not ready —
     * use [validate] first if you need to tell the user why.
     */
    fun buildPolygon(): Polygon? {
        if (points.size < MIN_POINTS) return null
        val sr = mapView.spatialReference ?: return null

        return PolygonBuilder(sr).apply {
            points.forEach { addPoint(it) }
        }.toGeometry()
    }

    /** Checks whether [buildPolygon] would succeed, with a reason when it would not. */
    fun validate(): DrawResult = when {
        points.size < MIN_POINTS -> DrawResult.TooFewPoints(MIN_POINTS)
        mapView.spatialReference == null -> DrawResult.MapNotReady
        else -> DrawResult.Ok
    }

    // ==================================================================
    // PREVIEW
    // ==================================================================

    private fun refreshPreview() {
        overlay.graphics.clear()
        val sr = mapView.spatialReference ?: return

        // Vertices
        val vertexSymbol = MapSymbols.drawVertex()
        points.forEach { overlay.graphics.add(Graphic(it, vertexSymbol)) }

        // Connecting line
        if (points.size >= 2) {
            val line = PolylineBuilder(sr).apply {
                points.forEach { addPoint(it) }
            }.toGeometry()
            overlay.graphics.add(Graphic(line, MapSymbols.drawLine()))
        }

        // Filled preview once the shape closes
        if (points.size >= MIN_POINTS) {
            val preview = PolygonBuilder(sr).apply {
                points.forEach { addPoint(it) }
            }.toGeometry()
            overlay.graphics.add(Graphic(preview, MapSymbols.drawPreviewFill()))
        }
    }
}