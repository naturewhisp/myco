package github.naturewhisp.myco.core

import kotlin.math.cos
import kotlin.math.sqrt

/** Deterministic surface union quadrature; resolution is a geographic proxy, not canopy measurement. */
object HabitatGeometry {
    private data class Point(val x: Double, val y: Double)
    private data class SurfaceAssembly(val surfaces: List<OsmSurface>, val complete: Boolean)

    /** Joins ordered or reversed multipolygon member fragments; unresolved chains stay unavailable. */
    fun assembleSurfaces(surfaces: List<OsmSurface>): List<OsmSurface> {
        return assemble(surfaces).surfaces
    }

    private fun assemble(surfaces: List<OsmSurface>): SurfaceAssembly {
        val result = mutableListOf<OsmSurface>()
        var complete = surfaces.isNotEmpty() && surfaces.all { it.vertices.size >= 2 }
        for (inner in listOf(false, true)) {
            val pending = surfaces.filter { it.inner == inner && it.vertices.size >= 2 }.map { it.vertices }.toMutableList()
            while (pending.isNotEmpty()) {
                val chain = pending.removeAt(0).toMutableList()
                while (chain.first() != chain.last()) {
                    val index = pending.indexOfFirst { it.first() == chain.last() || it.last() == chain.last() }
                    if (index < 0) break
                    val fragment = pending.removeAt(index)
                    chain.addAll((if (fragment.first() == chain.last()) fragment else fragment.reversed()).drop(1))
                }
                if (chain.size >= 4 && chain.first() == chain.last()) result.add(OsmSurface(chain, inner))
                else complete = false
            }
        }
        return SurfaceAssembly(result, complete && result.any { !it.inner })
    }

    fun extract(elements: List<OsmHabitatElement>, target: GeoCoordinates, radius: Int): HabitatEvidence {
        if (elements.isEmpty() || radius <= 0) return HabitatEvidence.UNKNOWN_HABITAT
        val unique = elements.distinctBy { it.elementKey ?: it }
        val scale = 111_320.0 * cos(target.latitude * kotlin.math.PI / 180.0)
        fun project(coordinate: GeoCoordinates) = Point(
            (coordinate.longitude - target.longitude) * scale,
            (coordinate.latitude - target.latitude) * 111_320.0,
        )
        val assemblies = unique.associateWith { assemble(it.surfaces) }
        val rings = unique.associateWith { element ->
            assemblies.getValue(element).takeIf { it.complete }?.surfaces.orEmpty()
                .map { it.inner to it.vertices.map(::project) }
        }
        fun inside(point: Point, ring: List<Point>): Boolean {
            var result = false
            for (i in 0 until ring.lastIndex) {
                val a = ring[i]
                val b = ring[i + 1]
                if ((a.y > point.y) != (b.y > point.y) &&
                    point.x < (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x) result = !result
            }
            return result
        }
        fun contains(element: OsmHabitatElement, point: Point): Boolean {
            val surfaces = rings.getValue(element)
            return surfaces.any { !it.first && inside(point, it.second) } &&
                surfaces.none { it.first && inside(point, it.second) }
        }
        fun distance(element: OsmHabitatElement): Double? {
            if (element.surfaces.isNotEmpty() && !assemblies.getValue(element).complete) return null
            if (contains(element, Point(0.0, 0.0))) return 0.0
            val edges = rings.getValue(element).flatMap { (_, ring) -> ring.zipWithNext() }
            if (edges.isNotEmpty()) return edges.minOf { (a, b) ->
                val dx = b.x - a.x
                val dy = b.y - a.y
                val length2 = dx * dx + dy * dy
                val t = if (length2 == 0.0) 0.0 else ((-a.x * dx - a.y * dy) / length2).coerceIn(0.0, 1.0)
                sqrt((a.x + t * dx) * (a.x + t * dx) + (a.y + t * dy) * (a.y + t * dy))
            }
            return if (element.lat != null && element.lon != null && element.lat.isFinite() && element.lon.isFinite()) {
                MycoAlgorithms.haversineDistanceKm(target.latitude, target.longitude, element.lat, element.lon) * 1000.0
            } else null
        }
        var samples = 0
        var forest = 0
        var meadow = 0
        var urban = 0
        val steps = ParameterRegistry.HABITAT_SURFACE_GRID.value
        for (x in -steps..steps) for (y in -steps..steps) {
            val point = Point(x * radius.toDouble() / steps, y * radius.toDouble() / steps)
            if (point.x * point.x + point.y * point.y > radius.toDouble() * radius) continue
            samples++
            if (unique.any { it.isWoodOrForest && contains(it, point) }) forest++
            if (unique.any { it.isMeadowOrGrass && contains(it, point) }) meadow++
            if (unique.any { it.isUrbanOrBuilt && contains(it, point) }) urban++
        }
        val located = unique.filter { distance(it)?.let { d -> d <= radius } == true }
        val forestDistance = located.filter { it.isWoodOrForest }.mapNotNull(::distance).minOrNull()
        val complete = unique.filter { it.isWoodOrForest || it.isMeadowOrGrass || it.isUrbanOrBuilt }
            .all { assemblies.getValue(it).complete }
        return HabitatEvidence(
            status = when {
                urban > samples / 2 -> HabitatStatus.KNOWN_UNSUITABLE
                forest > 0 || meadow > 0 -> HabitatStatus.KNOWN_SUITABLE
                else -> HabitatStatus.UNKNOWN
            },
            forestCoverFraction = forest.toDouble() / samples,
            meadowFraction = meadow.toDouble() / samples,
            distanceToNearestForestMeters = forestDistance ?: radius.toDouble(),
            confirmedHostGenera = located.mapNotNull { it.genus?.lowercase() }.toSet(),
            dominantLeafType = located.mapNotNull { it.leafType }.distinct().singleOrNull(),
            forestProximityIndex = forestDistance?.let { 1.0 - MycoAlgorithms.smoothstep(0.0, radius.toDouble(), it) } ?: 0.0,
            geometryComplete = complete,
        )
    }
}
