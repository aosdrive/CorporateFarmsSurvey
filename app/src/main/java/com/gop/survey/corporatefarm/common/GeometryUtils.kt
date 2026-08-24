package com.gop.survey.corporatefarm.common

import android.util.Log
import com.esri.arcgisruntime.geometry.AreaUnit
import com.esri.arcgisruntime.geometry.AreaUnitId
import com.esri.arcgisruntime.geometry.GeodeticCurveType
import com.esri.arcgisruntime.geometry.Geometry
import com.esri.arcgisruntime.geometry.GeometryEngine
import com.esri.arcgisruntime.geometry.LinearUnit
import com.esri.arcgisruntime.geometry.LinearUnitId
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.Polygon
import com.esri.arcgisruntime.geometry.SpatialReference
import com.esri.arcgisruntime.geometry.SpatialReferences
import java.util.Locale
import kotlin.math.abs


object GeometryUtils {

    private const val TAG = "GeometryUtils"
    private const val SQ_FT_PER_ACRE = 43560.0

    val wgs84: SpatialReference by lazy { SpatialReferences.getWgs84() }
    val webMercator: SpatialReference by lazy { SpatialReferences.getWebMercator() }

    // ==================================================================
    // PARSING
    // ==================================================================

    fun parsePolygons(wkt: String?): List<Polygon> {
        if (wkt.isNullOrBlank()) return emptyList()
        return try {
            when {
                wkt.contains("MULTIPOLYGON") -> Utility.getMultiPolygonFromString(wkt, wgs84)
                wkt.contains("POLYGON ((") -> listOfNotNull(Utility.getPolygonFromString(wkt, wgs84))
                wkt.contains("POLYGON") -> listOfNotNull(Utility.getPolyFromString(wkt, wgs84))
                else -> emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "parsePolygons failed: ${e.message}")
            emptyList()
        }
    }

    fun parseAndSimplify(wkt: String?): List<Polygon> =
        parsePolygons(wkt).mapNotNull { poly ->
            try {
                Utility.simplifyPolygon(poly).takeIf { !it.isEmpty }
            } catch (e: Exception) {
                null
            }
        }

    fun unionOf(polygons: List<Polygon>): Geometry? {
        if (polygons.isEmpty()) return null
        return try {
            if (polygons.size == 1) polygons[0]
            else {
                var merged: Geometry = polygons[0]
                for (i in 1 until polygons.size) {
                    merged = GeometryEngine.union(merged, polygons[i])
                }
                merged
            }
        } catch (e: Exception) {
            Log.e(TAG, "unionOf failed: ${e.message}")
            null
        }
    }

    fun toWkt(polygon: Polygon): String {
        val sb = StringBuilder("POLYGON(")

        polygon.parts.forEachIndexed { partIndex, part ->
            if (partIndex > 0) sb.append(",")
            sb.append("(")

            for (i in 0 until part.pointCount) {
                val p = part.getPoint(i)
                if (i > 0) sb.append(",")
                sb.append("${p.x} ${p.y}")
            }

            // close the ring if it is not closed already
            val first = part.getPoint(0)
            val last = part.getPoint(part.pointCount - 1)
            if (first.x != last.x || first.y != last.y) {
                sb.append(",${first.x} ${first.y}")
            }

            sb.append(")")
        }

        sb.append(")")
        return sb.toString()
    }

    fun isValidWkt(wkt: String?): Boolean {
        if (wkt.isNullOrBlank()) return false
        return when {
            wkt.startsWith("POLYGON", true) && wkt.contains("((") -> true
            wkt.startsWith("MULTIPOLYGON", true) && wkt.contains("(((") -> true
            else -> false
        }
    }

    fun centroidWkt(polygon: Polygon): String {
        val c = polygon.extent.center
        return String.format(Locale.US, "POINT(%.8f %.8f)", c.x, c.y)
    }

    fun areaSqFt(polygon: Polygon): Double = try {
        abs(
            GeometryEngine.areaGeodetic(
                polygon,
                AreaUnit(AreaUnitId.SQUARE_FEET),
                GeodeticCurveType.NORMAL_SECTION
            )
        )
    } catch (e: Exception) {
        Log.e(TAG, "areaSqFt failed: ${e.message}")
        0.0
    }

    fun sqFtToAcres(sqFt: Double): Double = sqFt / SQ_FT_PER_ACRE

    fun formatAcres(sqFt: Double): String =
        String.format(Locale.US, "%.4f Acres", sqFtToAcres(sqFt))


    private fun align(geometry: Geometry, target: SpatialReference?): Geometry =
        if (target != null && geometry.spatialReference != target)
            GeometryEngine.project(geometry, target)
        else geometry

    fun clipToBoundary(boundary: Geometry?, polygon: Polygon): Polygon? {
        if (boundary == null) return polygon
        return try {
            val aligned = align(boundary, polygon.spatialReference)
            when (val clipped = GeometryEngine.intersection(polygon, aligned)) {
                null -> null
                is Polygon -> clipped.takeIf { !it.isEmpty }
                else -> {
                    Log.w(TAG, "Clip produced ${clipped.javaClass.simpleName}")
                    clipped as? Polygon
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "clipToBoundary failed: ${e.message}")
            polygon
        }
    }

    fun overlaps(a: Polygon, b: Polygon, toleranceSqFt: Double = 1.0): Boolean = try {
        val bAligned = align(b, a.spatialReference) as Polygon

        if (!GeometryEngine.intersects(a, bAligned)) false
        else {
            val intersection = GeometryEngine.intersection(a, bAligned)
            when {
                intersection == null || intersection.isEmpty -> false
                intersection is Polygon -> areaSqFt(intersection) > toleranceSqFt
                else -> false   // point or line contact only
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "overlaps failed: ${e.message}")
        false
    }

    fun contains(geometry: Geometry?, point: Point): Boolean {
        if (geometry == null) return false
        return try {
            val aligned = align(point, geometry.spatialReference) as Point
            GeometryEngine.contains(geometry, aligned)
        } catch (e: Exception) {
            Log.e(TAG, "contains failed: ${e.message}")
            false
        }
    }

    fun containsDeepInside(polygon: Polygon, point: Point, shrinkMeters: Double = -1.5): Boolean {
        return try {
            val aligned = align(polygon, point.spatialReference) as Polygon

            val shrunk = try {
                GeometryEngine.bufferGeodetic(
                    aligned, shrinkMeters,
                    LinearUnit(LinearUnitId.METERS),
                    Double.NaN, GeodeticCurveType.GEODESIC
                ) as? Polygon
            } catch (e: Exception) {
                null
            }

            val target = if (shrunk != null && !shrunk.isEmpty) shrunk else aligned
            GeometryEngine.contains(target, point)
        } catch (e: Exception) {
            Log.e(TAG, "containsDeepInside failed: ${e.message}")
            false
        }
    }


    fun toWgs84(polygon: Polygon): Polygon =
        if (polygon.spatialReference != wgs84)
            GeometryEngine.project(polygon, wgs84) as Polygon
        else polygon

    fun toLatLon(mapPoint: Point): Point {
        val projected = GeometryEngine.project(mapPoint, webMercator) as Point
        return GeometryEngine.project(projected, wgs84) as Point
    }
}