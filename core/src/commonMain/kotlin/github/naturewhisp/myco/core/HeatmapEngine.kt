package github.naturewhisp.myco.core

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max

class HeatmapEngine {
    fun generate(
        centerLatitude: Double,
        centerLongitude: Double,
        grid: SpunGrid,
        baseWeatherScore: Double,
        seasonalityScore: Double,
        altitudeScore: Double,
        speciesId: String,
        isDark: Boolean,
        gridSize: Int = 96,
        radiusKm: Double = 35.0,
        applySpunHyphalBonus: Boolean = false,
    ): HeatmapRaster? {
        require(gridSize >= 2)
        val species = SpeciesCatalog.byId(speciesId)
        val metersPerDegreeLatitude = 111_320.0
        val metersPerDegreeLongitude = metersPerDegreeLatitude * cos(centerLatitude * kotlin.math.PI / 180.0)
        val deltaLatitude = radiusKm * 1000.0 / metersPerDegreeLatitude
        val deltaLongitude = radiusKm * 1000.0 / max(1.0, metersPerDegreeLongitude)
        val north = centerLatitude + deltaLatitude
        val south = centerLatitude - deltaLatitude
        val west = centerLongitude - deltaLongitude
        val east = centerLongitude + deltaLongitude
        val header = grid.header
        val stepLongitude = (header.maxLon - header.minLon) / header.width
        val stepLatitude = (header.maxLat - header.minLat) / header.height
        val pixels = IntArray(gridSize * gridSize)
        var hasData = false

        for (y in 0 until gridSize) {
            val latitude = north - y.toDouble() / (gridSize - 1) * (north - south)
            val row = ((header.maxLat - latitude) / stepLatitude).toInt()
            val normalizedY = (y.toDouble() / (gridSize - 1) - 0.5) * 2.0
            for (x in 0 until gridSize) {
                val index = y * gridSize + x
                val normalizedX = (x.toDouble() / (gridSize - 1) - 0.5) * 2.0
                val distance = hypot(normalizedX, normalizedY)
                if (distance > 1.0 || row !in 0 until header.height) continue
                val longitude = west + x.toDouble() / (gridSize - 1) * (east - west)
                val column = ((longitude - header.minLon) / stepLongitude).toInt()
                if (column !in 0 until header.width) continue
                val sourceIndex = row * header.width + column
                val ecm = grid.ecmData[sourceIndex].toInt() and 0xFF
                val hyphal = (grid.hyphalData[sourceIndex].toInt() and 0xFF) / 20.0
                if (ecm == 0 && hyphal == 0.0) continue
                hasData = true
                val ecmRatio = (ecm / 65.0).coerceIn(0.0, 1.0)
                val hyphalRatio = (hyphal / 7.0).coerceIn(0.0, 1.0)
                val biologicalPotential = when (species.category) {
                    EcologicalCategory.SAPROTROPHIC -> hyphalRatio * 80.0 + 20.0
                    EcologicalCategory.PARASITIC -> hyphalRatio * 70.0 + ecmRatio * 30.0
                    EcologicalCategory.ECTOMYCORRHIZAL -> if (applySpunHyphalBonus) {
                        ecmRatio * 55.0 + hyphalRatio * 45.0
                    } else {
                        ecmRatio * 100.0
                    }
                }
                val cellHabitatScore = (biologicalPotential / 100.0).coerceIn(0.0, 1.0)
                val cellSuitability = MycoAlgorithms.calculateSuitabilityScore(
                    weatherScore = baseWeatherScore.toInt().coerceIn(0, 100),
                    habitatScore = cellHabitatScore,
                    altitudeScore = altitudeScore.coerceIn(0.0, 1.0),
                    seasonalityScore = seasonalityScore.coerceIn(0.0, 1.0),
                    terrainModifier = 1.0,
                    growthPhaseMultiplier = 1.0,
                    species = species,
                    useHurdle = true,
                )
                val probability = cellSuitability.toInt().coerceIn(0, 100)
                var color = color(probability, isDark)
                if (distance > 0.75 && color != 0) {
                    val alpha = (((color ushr 24) and 0xFF) * ((1.0 - distance) / 0.25).coerceIn(0.0, 1.0)).toInt()
                    color = (color and 0x00FFFFFF) or (alpha shl 24)
                }
                pixels[index] = color
            }
        }
        return if (hasData) HeatmapRaster(pixels, gridSize, gridSize, north, south, west, east) else null
    }

    fun color(probability: Int, isDark: Boolean): Int {
        if (probability < 16) return 0
        val colors = if (isDark) intArrayOf(0x62B058, 0xFAB22A, 0xEB6E34, 0xD8343E) else intArrayOf(0x4E9648, 0xD49B24, 0xC86430, 0x9E262C)
        if (probability < 20) {
            val alpha = (smooth((probability - 16) / 4.0) * if (isDark) 125 else 115).toInt()
            return colors[0] or (alpha shl 24)
        }
        val alphaMinimum = if (isDark) 125 else 115
        val alphaMaximum = if (isDark) 195 else 180
        val alpha = (alphaMinimum + smooth(((probability - 20) / 65.0).coerceIn(0.0, 1.0)) * (alphaMaximum - alphaMinimum)).toInt()
        val (first, second, fraction) = when {
            probability < 40 -> Triple(colors[0], colors[0], 0.0)
            probability < 50 -> Triple(colors[0], colors[1], (probability - 40) / 10.0)
            probability < 60 -> Triple(colors[1], colors[1], 0.0)
            probability < 70 -> Triple(colors[1], colors[2], (probability - 60) / 10.0)
            probability < 78 -> Triple(colors[2], colors[2], 0.0)
            probability < 86 -> Triple(colors[2], colors[3], (probability - 78) / 8.0)
            else -> Triple(colors[3], colors[3], 1.0)
        }
        val t = smooth(fraction)
        val red = (((first ushr 16) and 0xFF) + t * (((second ushr 16) and 0xFF) - ((first ushr 16) and 0xFF))).toInt()
        val green = (((first ushr 8) and 0xFF) + t * (((second ushr 8) and 0xFF) - ((first ushr 8) and 0xFF))).toInt()
        val blue = ((first and 0xFF) + t * ((second and 0xFF) - (first and 0xFF))).toInt()
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }

    private fun smooth(value: Double): Double {
        val x = value.coerceIn(0.0, 1.0)
        return x * x * (3.0 - 2.0 * x)
    }
}
