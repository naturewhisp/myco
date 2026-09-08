package github.naturewhisp.myco.utils

import android.graphics.Color
import androidx.core.graphics.createBitmap
import github.naturewhisp.myco.model.HeatmapData
import github.naturewhisp.myco.repository.SpunDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * Genera in background una texture raster georeferenziata (HeatmapData)
 * calcolando la probabilità distribuita nello spazio attorno al punto di interesse.
 */
object HeatmapGenerator {

    private const val GRID_SIZE = 96 // 96x96 campioni (~9216 celle, risoluzione ~700m, calcolo in <2ms)
    private const val RADIUS_KM = 35.0 // Raggio di copertura esteso (~70x70 km, copre ampi zoom senza tagli netti)

    suspend fun generateHeatmap(
        centerLat: Double,
        centerLon: Double,
        spunDataManager: SpunDataManager,
        baseWeatherScore: Double,
        seasonalityScore: Double,
        altitudeScore: Double
    ): HeatmapData? = withContext(Dispatchers.Default) {
        val region = spunDataManager.getCurrentRegionData(centerLat, centerLon) ?: return@withContext null
        val header = region.header

        val metersPerDegLat = 111320.0
        val latRad = Math.toRadians(centerLat)
        val metersPerDegLon = 111320.0 * cos(latRad)

        val deltaLat = (RADIUS_KM * 1000.0) / metersPerDegLat
        val deltaLon = (RADIUS_KM * 1000.0) / max(1.0, metersPerDegLon)

        val north = centerLat + deltaLat
        val south = centerLat - deltaLat
        val west = centerLon - deltaLon
        val east = centerLon + deltaLon

        val stepLon = (header.maxLon - header.minLon) / header.width
        val stepLat = (header.maxLat - header.minLat) / header.height

        val pixels = IntArray(GRID_SIZE * GRID_SIZE)

        var hasValidData = false

        for (py in 0 until GRID_SIZE) {
            val curLat = north - (py.toDouble() / (GRID_SIZE - 1)) * (north - south)
            val row = ((header.maxLat - curLat) / stepLat).toInt()
            val rowValid = row in 0 until header.height
            val rowOffset = row * header.width

            // Coordinate normalizzate per sfumatura radiale morbida ai bordi (-1.0 .. 1.0)
            val normY = (py.toDouble() / (GRID_SIZE - 1) - 0.5) * 2.0

            for (px in 0 until GRID_SIZE) {
                val pixelIndex = py * GRID_SIZE + px
                val normX = (px.toDouble() / (GRID_SIZE - 1) - 0.5) * 2.0
                val distFromCenter = Math.hypot(normX, normY)

                if (distFromCenter > 1.0) {
                    pixels[pixelIndex] = 0 // Esterno al raggio circolare: trasparente
                    continue
                }

                val curLon = west + (px.toDouble() / (GRID_SIZE - 1)) * (east - west)
                val col = ((curLon - header.minLon) / stepLon).toInt()
                val colValid = col in 0 until header.width

                if (rowValid && colValid) {
                    val idx = rowOffset + col
                    val ecm = region.ecmData[idx].toInt() and 0xFF
                    val hypRaw = region.hyphalData[idx].toInt() and 0xFF

                    if (ecm > 0 || hypRaw > 0) {
                        hasValidData = true
                        val hyp = hypRaw.toFloat() / 20.0f

                        // Calcolo scientifico dell'indice di potenziale micologico (0..100)
                        // Combina la biodiversità simbionte EcM (50%) e la biomassa fungina ifale (50%)
                        val ecmRatio = (ecm / 65.0f).coerceIn(0f, 1f)
                        val hypRatio = (hyp / 7.0f).coerceIn(0f, 1f)
                        val bioPotential = (ecmRatio * 50.0f + hypRatio * 50.0f)

                        // Modulatore meteo/stagionale: fa da volano di attivazione fruttificazione
                        val weatherFactor = (baseWeatherScore / 100.0).coerceIn(0.2, 1.0)
                        val seasonFactor = seasonalityScore.coerceIn(0.3, 1.0)
                        val weatherMultiplier = (0.70 + (weatherFactor * seasonFactor) * 0.50).toFloat()

                        val prob = (bioPotential * weatherMultiplier).toInt().coerceIn(0, 100)
                        val baseColor = getHeatmapColor(prob)

                        // Sfumatura radiale morbida (feathering) sull'ultimo 25% del bordo esterno
                        // Elimina qualsiasi taglio netto o bordo squadrato sulla mappa
                        if (distFromCenter > 0.75 && baseColor != 0) {
                            val edgeFade = ((1.0 - distFromCenter) / 0.25).toFloat().coerceIn(0f, 1f)
                            val alpha = (Color.alpha(baseColor) * edgeFade).toInt()
                            pixels[pixelIndex] = (baseColor and 0x00FFFFFF) or (alpha shl 24)
                        } else {
                            pixels[pixelIndex] = baseColor
                        }
                    } else {
                        pixels[pixelIndex] = 0 // Trasparente (acqua/periurbano artificiale)
                    }
                } else {
                    pixels[pixelIndex] = 0
                }
            }
        }

        if (!hasValidData) return@withContext null

        val bitmap = createBitmap(GRID_SIZE, GRID_SIZE)
        bitmap.setPixels(pixels, 0, GRID_SIZE, 0, 0, GRID_SIZE, GRID_SIZE)

        HeatmapData(
            bitmap = bitmap,
            north = north,
            south = south,
            west = west,
            east = east
        )
    }

    /**
     * Mappa la probabilità (0..100) sulla scala tassonomica a 5 livelli Herbarium
     * (Salvia -> Ocra -> Terracotta -> Ruggine) con opacità costante per massima leggibilità toponomastica.
     */
    fun getHeatmapColor(probability: Int, isDark: Boolean = false): Int {
        if (probability < 20) return 0 // Trasparente per assenza di micelio significativo

        val baseAlpha = if (isDark) 150 else 128

        // Pigmenti minerali della scala tassonomica Herbarium
        val s1 = if (isDark) Color.rgb(0x6B, 0x7A, 0x55) else Color.rgb(0xBC, 0xC7, 0xA6) // Salvia
        val s2 = if (isDark) Color.rgb(0xDC, 0xB6, 0x5C) else Color.rgb(0xC8, 0x9B, 0x3C) // Ocra
        val s3 = if (isDark) Color.rgb(0xC6, 0x7C, 0x4B) else Color.rgb(0xB9, 0x6F, 0x42) // Terracotta
        val s4 = if (isDark) Color.rgb(0xB8, 0x5A, 0x45) else Color.rgb(0x9B, 0x4A, 0x38) // Ruggine

        val (c1, c2, fraction) = when {
            probability < 40 -> Triple(s1, s2, (probability - 20f) / 20f)
            probability < 60 -> Triple(s2, s3, (probability - 40f) / 20f)
            probability < 75 -> Triple(s3, s4, (probability - 60f) / 15f)
            else -> Triple(s4, s4, 1.0f)
        }

        return interpolateColorWithAlpha(c1, c2, fraction, baseAlpha)
    }

    private fun interpolateColorWithAlpha(c1: Int, c2: Int, fraction: Float, alpha: Int): Int {
        val f = fraction.coerceIn(0f, 1f)
        val r = (Color.red(c1) + f * (Color.red(c2) - Color.red(c1))).toInt()
        val g = (Color.green(c1) + f * (Color.green(c2) - Color.green(c1))).toInt()
        val b = (Color.blue(c1) + f * (Color.blue(c2) - Color.blue(c1))).toInt()
        return Color.argb(alpha, r, g, b)
    }
}
