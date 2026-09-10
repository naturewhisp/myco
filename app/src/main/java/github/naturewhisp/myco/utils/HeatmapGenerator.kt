package github.naturewhisp.myco.utils

import github.naturewhisp.myco.model.HeatmapRaster
import github.naturewhisp.myco.model.HeatmapRenderConfig
import github.naturewhisp.myco.platform.android.HeatmapData
import github.naturewhisp.myco.platform.android.toHeatmapData
import github.naturewhisp.myco.repository.SpunDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.max

/**
 * Motore raster georeferenziato per la generazione della nuvola termica di probabilità micologica.
 *
 * Esegue il campionamento bidimensionale in background attorno alle coordinate centrali,
 * correlando i dati di biodiversità ectomicorrizica (EcM) e densità ifale sotterranea SPUN
 * con il volano climatico recente e la stagionalità fenologica.
 *
 * La logica di calcolo dei campioni raster e la composizione dei colori a 32-bit ARGB
 * sono al 100% pure e indipendenti dal sistema operativo, pronte per essere condivise
 * con la futura versione desktop macOS, conformemente ad AGENTS.md.
 */
object HeatmapGenerator {

    /**
     * Genera la superficie raster pura [HeatmapRaster] (100% Kotlin agnostico da Android/macOS).
     *
     * @param centerLat Latitudine WGS84 del centro di campionamento.
     * @param centerLon Longitudine WGS84 del centro di campionamento.
     * @param spunDataManager Repository dei dati biologici SPUN.
     * @param baseWeatherScore Punteggio meteorologico composito (0..100).
     * @param seasonalityScore Punteggio fenologico stagionale continuo (0..1).
     * @param altitudeScore Risposta altimetrica continua (0..1).
     * @param isDark Flag per commutare tra la palette chiara Naturalist e scura Nocturne.
     * @param config Configurazione cartografica e di risoluzione [HeatmapRenderConfig].
     * @return [HeatmapRaster] georeferenziato con i pixel ARGB generati, o null se non vi sono dati coperti.
     */
    suspend fun generateHeatmapRaster(
        centerLat: Double,
        centerLon: Double,
        spunDataManager: SpunDataManager,
        baseWeatherScore: Double,
        seasonalityScore: Double,
        altitudeScore: Double,
        isDark: Boolean = false,
        config: HeatmapRenderConfig = HeatmapRenderConfig.DEFAULT
    ): HeatmapRaster? = withContext(Dispatchers.Default) {
        val region = spunDataManager.getCurrentRegionData(centerLat, centerLon) ?: return@withContext null
        val header = region.header

        val metersPerDegLat = config.metersPerDegLat
        val latRad = Math.toRadians(centerLat)
        val metersPerDegLon = metersPerDegLat * cos(latRad)

        val deltaLat = (config.radiusKm * 1000.0) / metersPerDegLat
        val deltaLon = (config.radiusKm * 1000.0) / max(1.0, metersPerDegLon)

        val north = centerLat + deltaLat
        val south = centerLat - deltaLat
        val west = centerLon - deltaLon
        val east = centerLon + deltaLon

        val stepLon = (header.maxLon - header.minLon) / header.width
        val stepLat = (header.maxLat - header.minLat) / header.height

        val gridSize = config.gridSize
        val pixels = IntArray(gridSize * gridSize)

        var hasValidData = false

        for (py in 0 until gridSize) {
            val curLat = north - (py.toDouble() / (gridSize - 1)) * (north - south)
            val row = ((header.maxLat - curLat) / stepLat).toInt()
            val rowValid = row in 0 until header.height
            val rowOffset = row * header.width

            // Coordinate normalizzate per sfumatura radiale morbida ai bordi (-1.0 .. 1.0)
            val normY = (py.toDouble() / (gridSize - 1) - 0.5) * 2.0

            for (px in 0 until gridSize) {
                val pixelIndex = py * gridSize + px
                val normX = (px.toDouble() / (gridSize - 1) - 0.5) * 2.0
                val distFromCenter = Math.hypot(normX, normY)

                if (distFromCenter > 1.0) {
                    pixels[pixelIndex] = 0 // Esterno al raggio circolare: trasparente
                    continue
                }

                val curLon = west + (px.toDouble() / (gridSize - 1)) * (east - west)
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
                        val baseColor = getHeatmapColor(prob, isDark, config)

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
            width = gridSize,
            height = gridSize,
            north = north,
            south = south,
            west = west,
            east = east
        )
    }

    /**
     * Genera in background una texture raster georeferenziata per Android [HeatmapData].
     *
     * @param centerLat Latitudine centrale WGS84.
     * @param centerLon Longitudine centrale WGS84.
     * @param spunDataManager Gestore degli asset di biodiversità SPUN.
     * @param baseWeatherScore Punteggio meteorologico.
     * @param seasonalityScore Punteggio fenologico.
     * @param altitudeScore Risposta altimetrica.
     * @param isDark Flag modalità scura.
     * @param config Configurazione cartografica [HeatmapRenderConfig].
     * @return [HeatmapData] pronto per il binding con OsmDroid, o null se non disponibile.
     */
    suspend fun generateHeatmap(
        centerLat: Double,
        centerLon: Double,
        spunDataManager: SpunDataManager,
        baseWeatherScore: Double,
        seasonalityScore: Double,
        altitudeScore: Double,
        isDark: Boolean = false,
        config: HeatmapRenderConfig = HeatmapRenderConfig.DEFAULT
    ): HeatmapData? {
        val raster = generateHeatmapRaster(
            centerLat = centerLat,
            centerLon = centerLon,
            spunDataManager = spunDataManager,
            baseWeatherScore = baseWeatherScore,
            seasonalityScore = seasonalityScore,
            altitudeScore = altitudeScore,
            isDark = isDark,
            config = config
        ) ?: return null

        return raster.toHeatmapData()
    }

    /**
     * Mappa la probabilità (0..100) sui pigmenti minerali botanici della scala Herbarium
     * (Salvia/Lichene -> Ocra Dorata -> Terracotta Cinabro -> Ruggine Granato)
     * con opacità progressiva equilibrata (115..180) e transizioni zonali armoniose per il perfetto
     * compromesso tra estetica naturale e leggibilità cartografica.
     *
     * @param probability Valore percentuale di probabilità di fruttificazione (0..100).
     * @param isDark Flag per palette notturna.
     * @param config Configurazione dei colori e delle soglie di plateau [HeatmapRenderConfig].
     * @return Valore ARGB compatto a 32-bit (0 = completamente trasparente).
     */
    fun getHeatmapColor(
        probability: Int,
        isDark: Boolean = false,
        config: HeatmapRenderConfig = HeatmapRenderConfig.DEFAULT
    ): Int {
        if (probability < config.cutoffThreshold) return 0 // Trasparente per assenza di attività miceliare significativa

        // Pigmenti minerali botanici calibrati per contrasto e armonia naturale
        val colors = if (isDark) config.darkColors else config.lightColors
        val s1 = colors[0] // Salvia Viva / Lichene Luminoso
        val s2 = colors[1] // Ocra Dorata Solare / Ambra
        val s3 = colors[2] // Terracotta Cinabro
        val s4 = colors[3] // Ruggine Granato Hotspot

        // Feathering iniziale morbido tra cutoffThreshold e featherThreshold per evitare gradini netti al limite inferiore
        if (probability < config.featherThreshold) {
            val range = (config.featherThreshold - config.cutoffThreshold).toFloat()
            val t = smoothStep(if (range > 0f) (probability - config.cutoffThreshold.toFloat()) / range else 1f)
            val maxInitialAlpha = if (isDark) config.alphaMinDark else config.alphaMinLight
            val initialAlpha = (t * maxInitialAlpha).toInt()
            return (s1 and 0x00FFFFFF) or (initialAlpha shl 24)
        }

        // Opacità equilibrata: lascia respirare orografia, curve di livello e toponomastica (115..180)
        val alphaMin = if (isDark) config.alphaMinDark else config.alphaMinLight
        val alphaMax = if (isDark) config.alphaMaxDark else config.alphaMaxLight
        val alphaT = smoothStep(((probability - config.featherThreshold.toFloat()) / config.alphaSpanProb).coerceIn(0f, 1f))
        val alpha = (alphaMin + alphaT * (alphaMax - alphaMin)).toInt()

        // Delimitazione zonale morbida per rendere ben distinguibili le 4 classi
        // (Innesco 20-45%, Moderato 45-65%, Propizio 65-80%, Culmine >80%)
        val (c1, c2, rawFraction) = when {
            probability < config.plateauTier1 -> Triple(s1, s1, 0f)
            probability < 50 -> Triple(s1, s2, (probability - config.plateauTier1.toFloat()) / 10f)
            probability < config.plateauTier2 -> Triple(s2, s2, 0f)
            probability < 70 -> Triple(s2, s3, (probability - config.plateauTier2.toFloat()) / 10f)
            probability < config.plateauTier3 -> Triple(s3, s3, 0f)
            probability < config.plateauTier4 -> Triple(s3, s4, (probability - config.plateauTier3.toFloat()) / (config.plateauTier4 - config.plateauTier3).toFloat())
            else -> Triple(s4, s4, 1.0f)
        }

        val fraction = smoothStep(rawFraction)
        return interpolateColorWithAlpha(c1, c2, fraction, alpha)
    }

    private fun smoothStep(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
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
