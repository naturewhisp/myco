package github.naturewhisp.myco.utils

import github.naturewhisp.myco.model.HeatmapData
import github.naturewhisp.myco.model.HeatmapRaster
import github.naturewhisp.myco.model.toHeatmapData
import github.naturewhisp.myco.repository.SpunDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.max

/**
 * Genera in background una texture raster georeferenziata (HeatmapRaster / HeatmapData)
 * calcolando la probabilità distribuita nello spazio attorno al punto di interesse.
 *
 * La logica di calcolo dei campioni raster e la composizione colore ARGB sono completamente
 * pure e indipendenti dalla piattaforma, pronte per essere condivise con macOS.
 */
object HeatmapGenerator {

    private const val GRID_SIZE = 96 // 96x96 campioni (~9216 celle, risoluzione ~700m, calcolo in <2ms)
    private const val RADIUS_KM = 35.0 // Raggio di copertura esteso (~70x70 km, copre ampi zoom senza tagli netti)

    /**
     * Genera la superficie raster pura [HeatmapRaster] (100% Kotlin agnostico da Android/macOS).
     */
    suspend fun generateHeatmapRaster(
        centerLat: Double,
        centerLon: Double,
        spunDataManager: SpunDataManager,
        baseWeatherScore: Double,
        seasonalityScore: Double,
        altitudeScore: Double,
        isDark: Boolean = false
    ): HeatmapRaster? = withContext(Dispatchers.Default) {
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
                        val baseColor = getHeatmapColor(prob, isDark)

                        // Sfumatura radiale morbida (feathering) sull'ultimo 25% del bordo esterno
                        // Elimina qualsiasi taglio netto o bordo squadrato sulla mappa
                        if (distFromCenter > 0.75 && baseColor != 0) {
                            val edgeFade = ((1.0 - distFromCenter) / 0.25).toFloat().coerceIn(0f, 1f)
                            val baseAlpha = (baseColor ushr 24) and 0xFF
                            val alpha = (baseAlpha * edgeFade).toInt()
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

        HeatmapRaster(
            argbPixels = pixels,
            width = GRID_SIZE,
            height = GRID_SIZE,
            north = north,
            south = south,
            west = west,
            east = east
        )
    }

    /**
     * Genera in background una texture raster georeferenziata per Android [HeatmapData].
     */
    suspend fun generateHeatmap(
        centerLat: Double,
        centerLon: Double,
        spunDataManager: SpunDataManager,
        baseWeatherScore: Double,
        seasonalityScore: Double,
        altitudeScore: Double,
        isDark: Boolean = false
    ): HeatmapData? {
        val raster = generateHeatmapRaster(
            centerLat = centerLat,
            centerLon = centerLon,
            spunDataManager = spunDataManager,
            baseWeatherScore = baseWeatherScore,
            seasonalityScore = seasonalityScore,
            altitudeScore = altitudeScore,
            isDark = isDark
        ) ?: return null

        return raster.toHeatmapData()
    }

    /**
     * Mappa la probabilità (0..100) sui pigmenti minerali botanici della scala Herbarium
     * (Salvia/Lichene -> Ocra Dorata -> Terracotta Cinabro -> Ruggine Granato)
     * con opacità progressiva equilibrata (115..180) e transizioni zonali armoniose per il perfetto
     * compromesso tra estetica naturale e leggibilità cartografica.
     */
    fun getHeatmapColor(probability: Int, isDark: Boolean = false): Int {
        if (probability < 16) return 0 // Trasparente per assenza di attività miceliare significativa

        // Pigmenti minerali botanici calibrati per contrasto e armonia naturale
        val s1 = if (isDark) rgb(0x62, 0xB0, 0x58) else rgb(0x4E, 0x96, 0x48) // Salvia Viva / Lichene Luminoso
        val s2 = if (isDark) rgb(0xFA, 0xB2, 0x2A) else rgb(0xD4, 0x9B, 0x24) // Ocra Dorata Solare / Ambra
        val s3 = if (isDark) rgb(0xEB, 0x6E, 0x34) else rgb(0xC8, 0x64, 0x30) // Terracotta Cinabro
        val s4 = if (isDark) rgb(0xD8, 0x34, 0x3E) else rgb(0x9E, 0x26, 0x2C) // Ruggine Granato Hotspot

        // Feathering iniziale morbido tra 16% e 20% per evitare gradini netti al limite inferiore
        if (probability < 20) {
            val t = smoothStep((probability - 16f) / 4f)
            val initialAlpha = (t * (if (isDark) 125 else 115)).toInt()
            return (s1 and 0x00FFFFFF) or (initialAlpha shl 24)
        }

        // Opacità equilibrata: lascia respirare orografia, curve di livello e toponomastica (115..180)
        val alphaMin = if (isDark) 125 else 115
        val alphaMax = if (isDark) 195 else 180
        val alphaT = smoothStep(((probability - 20f) / 65f).coerceIn(0f, 1f))
        val alpha = (alphaMin + alphaT * (alphaMax - alphaMin)).toInt()

        // Delimitazione zonale morbida per rendere ben distinguibili le 4 classi
        // (Innesco 20-45%, Moderato 45-65%, Propizio 65-80%, Culmine >80%)
        val (c1, c2, rawFraction) = when {
            probability < 40 -> Triple(s1, s1, 0f)
            probability < 50 -> Triple(s1, s2, (probability - 40f) / 10f)
            probability < 60 -> Triple(s2, s2, 0f)
            probability < 70 -> Triple(s2, s3, (probability - 60f) / 10f)
            probability < 78 -> Triple(s3, s3, 0f)
            probability < 86 -> Triple(s3, s4, (probability - 78f) / 8f)
            else -> Triple(s4, s4, 1.0f)
        }

        val fraction = smoothStep(rawFraction)
        return interpolateColorWithAlpha(c1, c2, fraction, alpha)
    }

    private fun smoothStep(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private fun rgb(r: Int, g: Int, b: Int): Int {
        return (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    }

    private fun interpolateColorWithAlpha(c1: Int, c2: Int, fraction: Float, alpha: Int): Int {
        val f = fraction.coerceIn(0f, 1f)
        val r1 = (c1 ushr 16) and 0xFF
        val g1 = (c1 ushr 8) and 0xFF
        val b1 = c1 and 0xFF

        val r2 = (c2 ushr 16) and 0xFF
        val g2 = (c2 ushr 8) and 0xFF
        val b2 = c2 and 0xFF

        val r = (r1 + f * (r2 - r1)).toInt().coerceIn(0, 255)
        val g = (g1 + f * (g2 - g1)).toInt().coerceIn(0, 255)
        val b = (b1 + f * (b2 - b1)).toInt().coerceIn(0, 255)

        return ((alpha and 0xFF) shl 24) or (r shl 16) or (g shl 8) or b
    }
}
