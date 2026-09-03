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
     * Mappa la probabilità (0..100) in una rampa di colore termica/radar vivida e contrastata,
     * chiaramente distinguibile sopra mappe topografiche (verdi), scure o standard.
     */
    private fun getHeatmapColor(probability: Int): Int {
        if (probability < 20) return 0 // Trasparente per assenza di micelio significativo

        return when {
            probability < 40 -> {
                // 20..40: Azzurro / Ciano vivido (Alpha 130..155)
                val fraction = (probability - 20f) / 20f
                interpolateColor(
                    startColor = Color.argb(130, 0, 190, 255),
                    endColor = Color.argb(155, 0, 230, 200),
                    fraction = fraction
                )
            }
            probability < 60 -> {
                // 40..60: Da Ciano/Verde smeraldo brillante a Giallo lime (Alpha 155..175)
                val fraction = (probability - 40f) / 20f
                interpolateColor(
                    startColor = Color.argb(155, 16, 185, 129),
                    endColor = Color.argb(175, 234, 179, 8),
                    fraction = fraction
                )
            }
            probability < 75 -> {
                // 60..75: Da Giallo oro ad Arancio vivo (Alpha 175..195)
                val fraction = (probability - 60f) / 15f
                interpolateColor(
                    startColor = Color.argb(175, 245, 158, 11),
                    endColor = Color.argb(195, 249, 115, 22),
                    fraction = fraction
                )
            }
            else -> {
                // 75..100: Da Arancio vivo a Rosso fuoco / Magenta Hotspot (Alpha 195..220)
                val fraction = ((probability - 75f) / 25f).coerceIn(0f, 1f)
                interpolateColor(
                    startColor = Color.argb(195, 249, 115, 22),
                    endColor = Color.argb(220, 239, 68, 68),
                    fraction = fraction
                )
            }
        }
    }

    private fun interpolateColor(startColor: Int, endColor: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        val a = (Color.alpha(startColor) + f * (Color.alpha(endColor) - Color.alpha(startColor))).toInt()
        val r = (Color.red(startColor) + f * (Color.red(endColor) - Color.red(startColor))).toInt()
        val g = (Color.green(startColor) + f * (Color.green(endColor) - Color.green(startColor))).toInt()
        val b = (Color.blue(startColor) + f * (Color.blue(endColor) - Color.blue(startColor))).toInt()
        return Color.argb(a, r, g, b)
    }
}
