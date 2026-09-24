package github.naturewhisp.myco

import github.naturewhisp.myco.core.HeatmapEngine
import github.naturewhisp.myco.core.MycoAlgorithms
import github.naturewhisp.myco.core.SpeciesCatalog
import github.naturewhisp.myco.core.SpunParser
import github.naturewhisp.myco.model.EcologicalWeightsConfig
import github.naturewhisp.myco.model.HeatmapRenderConfig
import github.naturewhisp.myco.model.ProcessedDay
import github.naturewhisp.myco.model.SPECIES_CATALOG
import github.naturewhisp.myco.platform.AssetProvider
import github.naturewhisp.myco.repository.SpunDataManager
import github.naturewhisp.myco.utils.GrowthStage
import github.naturewhisp.myco.utils.HeatmapGenerator
import github.naturewhisp.myco.utils.MushroomAlgorithms
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.Locale

/**
 * Suite di test di non-regressione scientifica e invarianti per il Blocco 4:
 * Parità Cross-Platform Android/iOS/Core, Parità Heatmap/Scheda Puntuale,
 * Coerenza Modalità WEATHER_ONLY e Validazione Empirica Rigorosa su Calendario Reale.
 *
 * Risolve i seguenti rilievi della revisione scientifica:
 * - F11 (MYCO-SCI-08): Parità matematica Heatmap e Scheda Puntuale; a W = 0 la mappa non mostra
 *        alcuna classe favorevole; a input identici pixel e scheda coincidono prima del rendering;
 *        rimozione dell'incremento numerico artificiale (0.60 + ... * 0.60).
 * - F12 (MYCO-SCI-04): Parità completa tra il motore Android (:app) e il motore KMP (:core);
 *        condivisione delle soglie idrologiche continue, del modello Hurdle a due stadi e della
 *        formula asintotica di calibrazione.
 * - F13 (MYCO-SCI-04): Coerenza della modalità WEATHER_ONLY: applicazione uniforme di H=A=S=T=1.0
 *        alla scheda principale e a tutti i giorni dell'outlook previsionale.
 * - F19 (MYCO-SCI-09): Validità del calendario reale nei benchmark storici (Mindino dal 25 agosto
 *        al 26 settembre 2026, zero date fittizie), segregazione test sintetici vs storici, e
 *        rispetto dei limiti fisici di carpogenesi (Liebig growth gating).
 */
class ScientificRegressionBlock4Test {

    private val edulis = SPECIES_CATALOG.first { it.id == "boletus_edulis" }
    private val procera = SPECIES_CATALOG.first { it.id == "macrolepiota_procera" }
    private val caesarea = SPECIES_CATALOG.first { it.id == "amanita_caesarea" }

    private val binaryAssetFile = File("src/main/assets/spun/spun_italy.bin")

    private fun createSpunDataManager(lat: Double = 44.200, lon: Double = 7.300): SpunDataManager {
        assertTrue("L'asset spun_italy.bin deve esistere", binaryAssetFile.exists())
        val assetProvider = AssetProvider { path ->
            val file = if (path.startsWith("spun/")) {
                File("src/main/assets", path)
            } else {
                File("src/main/assets/spun", path)
            }
            file.inputStream()
        }
        return SpunDataManager(assetProvider).apply {
            runBlocking { ensureRegionLoadedFor(lat, lon) }
        }
    }

    /**
     * REG-09 & REG-12 (F11): Parità tra Heatmap e Scheda Puntuale e azzeramento a W = 0.
     * Verifica che:
     * 1. A meteo nullo (baseWeatherScore = 0.0), la mappa cartografica non mostri alcuna classe
     *    favorevole (tutti i pixel risultano completamente trasparenti con ARGB = 0).
     * 2. A meteo favorevole (W > 0), con identici parametri ambientali (W, H, A, S, T=1, Phi=1),
     *    la formula raster produca esattamente lo stesso punteggio di idoneità della scheda.
     */
    @Test
    fun reg09_reg12_heatmapPunctualParityAndZeroWeatherSuppression() = runBlocking {
        val manager = createSpunDataManager()
        // Coordinate boschive delle Alpi Marittime (alta ricchezza EcM)
        val lat = 44.200
        val lon = 7.300

        // 1. A baseWeatherScore = 0.0 (siccità/gelo estremo), nessun pixel deve essere colorato
        val zeroWeatherRaster = HeatmapGenerator.generateHeatmapRaster(
            centerLat = lat,
            centerLon = lon,
            spunDataManager = manager,
            baseWeatherScore = 0.0,
            seasonalityScore = 1.0,
            altitudeScore = 1.0,
            isDark = false,
            config = HeatmapRenderConfig.DEFAULT,
            species = edulis
        )
        assertNotNull("Il raster deve essere generato", zeroWeatherRaster)
        val nonZeroPixels = zeroWeatherRaster!!.argbPixels.count { it != 0 }
        assertEquals(
            "A W=0 nessun pixel della mappa deve mostrare colore o favorevolezza (trovati: $nonZeroPixels)",
            0,
            nonZeroPixels
        )

        // 2. A meteo favorevole (W > 0), verificare direttamente i pixel calcolati su raster e scheda
        val wScore = 75
        val aScore = 0.90
        val sScore = 1.00

        val favorableRaster = HeatmapGenerator.generateHeatmapRaster(
            centerLat = lat,
            centerLon = lon,
            spunDataManager = manager,
            baseWeatherScore = wScore.toDouble(),
            seasonalityScore = sScore,
            altitudeScore = aScore,
            isDark = false,
            config = HeatmapRenderConfig.DEFAULT,
            species = edulis
        )
        assertNotNull("Il raster favorevole deve essere generato", favorableRaster)
        val nonZeroFavPixels = favorableRaster!!.argbPixels.count { it != 0 }
        assertTrue("A W=75 i pixel attivi della mappa devono essere presenti (trovati: $nonZeroFavPixels)", nonZeroFavPixels > 0)

        // Verifica puntuale del pixel centrale calcolato direttamente su W > 0
        val region = manager.getCurrentRegionData(lat, lon)!!
        val header = region.header
        val py = favorableRaster.height / 2
        val px = favorableRaster.width / 2
        val curLat = favorableRaster.north - (py.toDouble() / (favorableRaster.height - 1)) * (favorableRaster.north - favorableRaster.south)
        val curLon = favorableRaster.west + (px.toDouble() / (favorableRaster.width - 1)) * (favorableRaster.east - favorableRaster.west)
        val stepLon = (header.maxLon - header.minLon) / header.width
        val stepLat = (header.maxLat - header.minLat) / header.height
        val row = ((header.maxLat - curLat) / stepLat).toInt()
        val col = ((curLon - header.minLon) / stepLon).toInt()
        val idx = row * header.width + col
        val centerEcm = region.ecmData[idx].toInt() and 0xFF
        val centerEcmRatio = (centerEcm / 65.0f).coerceIn(0f, 1f)
        val centerBioPot = (centerEcmRatio * 100.0f).toDouble()
        val centerHabitatScore = (centerBioPot / 100.0).coerceIn(0.0, 1.0)

        val cardSuitability = MushroomAlgorithms.calculateSuitabilityScore(
            weatherScore = wScore,
            habitatScore = centerHabitatScore,
            altitudeScore = aScore,
            seasonalityScore = sScore,
            terrainModifier = 1.0,
            config = EcologicalWeightsConfig.PHENOLOGICAL,
            species = edulis,
            growthPhaseMultiplier = 1.0
        )
        val expectedCenterProb = cardSuitability.toInt().coerceIn(0, 100)
        val expectedCenterColor = HeatmapGenerator.getHeatmapColor(expectedCenterProb, false, HeatmapRenderConfig.DEFAULT)
        val actualCenterPixel = favorableRaster.argbPixels[py * favorableRaster.width + px]
        assertEquals("Il pixel centrale su W>0 deve corrispondere al calcolo della scheda", expectedCenterColor, actualCenterPixel)

        // 3. Verifica con il motore condiviso KMP HeatmapEngine (sia W=0 che W>0)
        val headerBytes = ByteArray(32).also { bytes ->
            "SPUN".encodeToByteArray().copyInto(bytes, 0)
            bytes.putShort(4, 1)
            "ALP\u0000".encodeToByteArray().copyInto(bytes, 6)
            bytes.putInt(10, 43.0f.toBits())
            bytes.putInt(14, 45.0f.toBits())
            bytes.putInt(18, 6.0f.toBits())
            bytes.putInt(22, 8.0f.toBits())
            bytes.putShort(26, 8)
            bytes.putShort(28, 8)
            bytes.putShort(30, 30)
        }
        val grid = checkNotNull(SpunParser.parseInflated(headerBytes, ByteArray(128) { 50.toByte() }))
        val sharedEngineZero = HeatmapEngine().generate(
            centerLatitude = 44.0,
            centerLongitude = 7.0,
            grid = grid,
            baseWeatherScore = 0.0,
            seasonalityScore = 1.0,
            altitudeScore = 1.0,
            speciesId = "boletus_edulis",
            isDark = false,
            gridSize = 8,
            radiusKm = 10.0
        )
        assertNotNull(sharedEngineZero)
        val sharedZeroCount = sharedEngineZero!!.argbPixels.count { it != 0 }
        assertEquals("Nel core KMP a W=0 nessun pixel deve essere colorato", 0, sharedZeroCount)

        val sharedEngineFavorable = HeatmapEngine().generate(
            centerLatitude = 44.0,
            centerLongitude = 7.0,
            grid = grid,
            baseWeatherScore = 75.0,
            seasonalityScore = 1.0,
            altitudeScore = 0.90,
            speciesId = "boletus_edulis",
            isDark = false,
            gridSize = 8,
            radiusKm = 10.0
        )
        assertNotNull(sharedEngineFavorable)
        val sharedFavCount = sharedEngineFavorable!!.argbPixels.count { it != 0 }
        assertTrue("Nel core KMP a W=75 devono essere presenti pixel colorati", sharedFavCount > 0)
        val ecmVal = 50
        val ecmRatioCore = (ecmVal / 65.0).coerceIn(0.0, 1.0)
        val cellHabCore = (ecmRatioCore * 100.0) / 100.0
        val coreSpecies = SpeciesCatalog.byId("boletus_edulis")
        val expectedCoreSuitability = MycoAlgorithms.calculateSuitabilityScore(
            weatherScore = 75,
            habitatScore = cellHabCore,
            altitudeScore = 0.90,
            seasonalityScore = 1.0,
            terrainModifier = 1.0,
            growthPhaseMultiplier = 1.0,
            species = coreSpecies,
            useHurdle = true
        )
        val expectedCoreProb = expectedCoreSuitability.toInt().coerceIn(0, 100)
        val expectedCoreColor = HeatmapEngine().color(expectedCoreProb, false)
        val centerKmpPixel = sharedEngineFavorable.argbPixels[3 * 8 + 3]
        assertEquals("Il pixel KMP interno calcolato su W>0 deve corrispondere alla formula con hurdle", expectedCoreColor, centerKmpPixel)
    }

    private fun ByteArray.putShort(offset: Int, value: Int) {
        this[offset] = (value ushr 8).toByte()
        this[offset + 1] = value.toByte()
    }

    private fun ByteArray.putInt(offset: Int, value: Int) {
        this[offset] = (value ushr 24).toByte()
        this[offset + 1] = (value ushr 16).toByte()
        this[offset + 2] = (value ushr 8).toByte()
        this[offset + 3] = value.toByte()
    }

    /**
     * REG-10 & REG-14 (F13): Coerenza della modalità WEATHER_ONLY tra scheda principale e outlook.
     * Verifica che:
     * 1. In modalità WEATHER_ONLY, il primo giorno dell'outlook previsionale (giorno 0) coincida
     *    esattamente con la probabilità/idoneità calcolata per la scheda principale.
     * 2. La neutralizzazione dei fattori non meteo (H=A=S=T=1.0) sia uniforme e non reintroduca
     *    penalità altimetriche o stagionali nell'outlook.
     */
    @Test
    fun reg10_reg14_weatherOnlyModeCoherenceBetweenCardAndOutlook() {
        val days = (0 until 28).map { i ->
            ProcessedDay(
                date = "2026-09-${String.format(Locale.US, "%02d", i + 1)}",
                avgTemp = 16.0f,
                minTemp = 11.0f,
                maxTemp = 20.0f,
                totalPrecip = if (i == 20) 35.0f else 0.0f,
                avgHumidity = 80.0f,
                weatherCode = if (i == 20) 61 else 1
            )
        }

        val testSpeciesList = listOf(edulis, procera, caesarea)
        val outOfBoundsElevation = 2800f // Quota estrema: A = 0.40 se attivo
        val outOfSeasonMonth = 0 // Gennaio: S = 0.10 se attivo

        for (species in testSpeciesList) {
            val todayIndex = 23
            val weatherScore = MushroomAlgorithms.calculateWeatherScore(
                todayIndex,
                days,
                species = species,
                config = EcologicalWeightsConfig.PHENOLOGICAL
            )
            val growthPhaseEval = MushroomAlgorithms.evaluateGrowthPhase(days, species, todayIndex)

            // 1. Calcolo scheda principale in modalità WEATHER_ONLY (H=A=S=T=1.0)
            val mainCardSuitability = MushroomAlgorithms.calculateSuitabilityScore(
                weatherScore = weatherScore,
                habitatScore = 1.0,
                altitudeScore = 1.0,
                seasonalityScore = 1.0,
                terrainModifier = 1.0,
                config = EcologicalWeightsConfig.PHENOLOGICAL,
                species = species,
                growthPhaseMultiplier = growthPhaseEval.multiplier
            )
            val mainCardProb = mainCardSuitability.toInt().coerceIn(0, 100)

            // 2. Calcolo DailyOutlook con calculationMode = "WEATHER_ONLY"
            val outlooks = MushroomAlgorithms.calculateDailyOutlooks(
                processedDays = days,
                startIndex = todayIndex,
                species = species,
                habitatScore = 1.0,
                elevation = outOfBoundsElevation,
                month = outOfSeasonMonth,
                terrainModifier = 1.0,
                config = EcologicalWeightsConfig.PHENOLOGICAL,
                calculationMode = "WEATHER_ONLY"
            )

            assertTrue("Gli outlook devono contenere almeno un giorno", outlooks.isNotEmpty())
            val day0OutlookProb = outlooks[0].probability

            assertEquals(
                "Per specie ${species.id}, il giorno 0 dell'outlook ($day0OutlookProb) deve coincidere con la scheda ($mainCardProb) in WEATHER_ONLY",
                mainCardProb,
                day0OutlookProb
            )

            // 3. Verifica per contrasto: in modalità standard (ALL), i fattori A ed S deprimono fortemente l'outlook
            val standardOutlooks = MushroomAlgorithms.calculateDailyOutlooks(
                processedDays = days,
                startIndex = todayIndex,
                species = species,
                habitatScore = 1.0,
                elevation = outOfBoundsElevation,
                month = outOfSeasonMonth,
                terrainModifier = 1.0,
                config = EcologicalWeightsConfig.PHENOLOGICAL,
                calculationMode = "ALL"
            )
            val standardDay0Prob = standardOutlooks[0].probability
            assertTrue(
                "In modalità standard fuori stagione/quota la probabilità ($standardDay0Prob) deve essere inferiore a WEATHER_ONLY ($mainCardProb)",
                standardDay0Prob < mainCardProb
            )
        }
    }

    /**
     * REG-18 (F12): Parità cross-platform tra il modulo Android (:app) e il modulo condiviso KMP (:core).
     * Verifica la convergenza numerica (delta <= 1e-6) di:
     * 1. soilMoistureScoreSmooth vs soilMoistureResponse su tutto l'intervallo [0.05, 0.55].
     * 2. hurdleOccurrenceProbability tra :app e :core per specie ectomicorriziche e saprotrofe.
     * 3. calculateSuitabilityScore tra :app e :core.
     */
    @Test
    fun reg18_crossPlatformParityBetweenAndroidAndCore() {
        val coreEdulis = SpeciesCatalog.byId("boletus_edulis")
        val coreProcera = SpeciesCatalog.byId("macrolepiota_procera")

        // 1. Parità risposta idrica del suolo
        var theta = 0.05
        while (theta <= 0.55) {
            val appScore = MushroomAlgorithms.soilMoistureScoreSmooth(m0To7 = theta, m7To28 = null)
            val coreScore = MycoAlgorithms.soilMoistureResponse(shallow = theta, deep = null, et0 = null)
            assertEquals("Soil moisture parity a theta=$theta", appScore, coreScore, 0.000_001)

            val appDeepScore = MushroomAlgorithms.soilMoistureScoreSmooth(m0To7 = null, m7To28 = theta)
            val coreDeepScore = MycoAlgorithms.soilMoistureResponse(shallow = null, deep = theta, et0 = null)
            assertEquals("Deep soil moisture parity a theta=$theta", appDeepScore, coreDeepScore, 0.000_001)

            theta += 0.02
        }

        // 2. Parità modello Hurdle a due stadi
        for (h in listOf(0.05, 0.20, 0.50, 0.85, 1.00)) {
            for (a in listOf(0.40, 0.70, 1.00)) {
                val appHurdleEdulis = MushroomAlgorithms.hurdleOccurrenceProbability(h, a, edulis)
                val coreHurdleEdulis = MycoAlgorithms.hurdleOccurrenceProbability(h, a, coreEdulis)
                assertEquals("Hurdle edulis parity a H=$h, A=$a", appHurdleEdulis, coreHurdleEdulis, 0.000_001)

                val appHurdleProcera = MushroomAlgorithms.hurdleOccurrenceProbability(h, a, procera)
                val coreHurdleProcera = MycoAlgorithms.hurdleOccurrenceProbability(h, a, coreProcera)
                assertEquals("Hurdle procera parity a H=$h, A=$a", appHurdleProcera, coreHurdleProcera, 0.000_001)
            }
        }

        // 3. Parità formula asintotica di calibrazione (calculateSuitabilityScore)
        for (w in listOf(0, 20, 50, 70, 85, 100)) {
            val appSuitability = MushroomAlgorithms.calculateSuitabilityScore(
                weatherScore = w,
                habitatScore = 0.90,
                altitudeScore = 0.85,
                seasonalityScore = 0.95,
                terrainModifier = 1.02,
                config = EcologicalWeightsConfig.DEFAULT,
                growthPhaseMultiplier = 1.0
            )
            val coreSuitability = MycoAlgorithms.calculateSuitabilityScore(
                weatherScore = w,
                habitatScore = 0.90,
                altitudeScore = 0.85,
                seasonalityScore = 0.95,
                terrainModifier = 1.02,
                growthPhaseMultiplier = 1.0
            )
            assertEquals("Suitability formula parity a W=$w", appSuitability, coreSuitability, 0.000_001)

            // 4. Parità formula di calibrazione con configurazione PHENOLOGICAL (con modello Hurdle attivo)
            for ((appSpecies, coreSpecies) in listOf(edulis to coreEdulis, procera to coreProcera)) {
                val appSuitabilityPheno = MushroomAlgorithms.calculateSuitabilityScore(
                    weatherScore = w,
                    habitatScore = 0.90,
                    altitudeScore = 0.85,
                    seasonalityScore = 0.95,
                    terrainModifier = 1.02,
                    config = EcologicalWeightsConfig.PHENOLOGICAL,
                    species = appSpecies,
                    growthPhaseMultiplier = 1.0
                )
                val coreSuitabilityPheno = MycoAlgorithms.calculateSuitabilityScore(
                    weatherScore = w,
                    habitatScore = 0.90,
                    altitudeScore = 0.85,
                    seasonalityScore = 0.95,
                    terrainModifier = 1.02,
                    growthPhaseMultiplier = 1.0,
                    species = coreSpecies,
                    useHurdle = true
                )
                assertEquals(
                    "Suitability formula PHENOLOGICAL parity for ${appSpecies.id} a W=$w",
                    appSuitabilityPheno,
                    coreSuitabilityPheno,
                    0.000_001
                )
            }
        }
    }

    /**
     * REG-19 (F19): Validità del calendario reale e segregazione benchmark empirici (Mindino).
     * Verifica che:
     * 1. Tutte le date storiche generate appartengano al calendario gregoriano reale (LocalDate.parse valido).
     * 2. La serie di Mindino inizi il 25 Agosto 2026 e collochi i benchmark storici sui giorni corretti:
     *    - Giorno 16: 10 Settembre 2026 (prima pioggia 22.3 mm)
     *    - Giorno 23: 17 Settembre 2026 (pioggia primaria 25.3 mm)
     *    - Giorno 24: 18 Settembre 2026 (visita sul campo con zero carpofori)
     *    - Giorno 27: 21 Settembre 2026 (quarto giorno post-pioggia)
     *    - Giorno 32: 26 Settembre 2026 (proiezione weekend di picco)
     * 3. Non vi sia alcuna data inesistente (es. 31, 32 settembre).
     * 4. Al giorno della visita (18 set), la Legge del Minimo di Liebig (growthPhaseMultiplier <= 0.45)
     *    impedisca falsi positivi nonostante l'ottima pioggia del giorno precedente.
     */
    @Test
    fun reg19_mindinoRealCalendarDatesAndLiebigGating() {
        val startDate = LocalDate.of(2026, 8, 25)
        val series = (0 until 28).map { i ->
            val dateStr = startDate.plusDays(i.toLong()).toString()
            // Validazione del parser gregoriano
            val parsed = LocalDate.parse(dateStr)
            assertEquals(startDate.plusDays(i.toLong()), parsed)
            ProcessedDay(
                date = dateStr,
                avgTemp = 16.0f,
                minTemp = 11.5f,
                maxTemp = 20.5f,
                totalPrecip = when (i) {
                    16 -> 22.3f
                    23 -> 25.3f
                    else -> 0.0f
                },
                avgHumidity = 78.0f,
                weatherCode = if (i == 16 || i == 23) 61 else 1
            )
        }

        // Verifica date storiche esatte
        assertEquals("2026-08-25", series[0].date)
        assertEquals("2026-09-10", series[16].date) // 10 settembre
        assertEquals("2026-09-17", series[23].date) // 17 settembre
        assertEquals("2026-09-18", series[24].date) // 18 settembre (uscita reale)
        assertEquals("2026-09-21", series[27].date) // 21 settembre

        // Verifica prolungamento weekend
        val weekendDate = startDate.plusDays(32L).toString()
        assertEquals("2026-09-26", weekendDate) // 26 settembre (weekend)
        assertTrue("Nessun giorno di settembre deve avere giorno > 30", LocalDate.parse(weekendDate).dayOfMonth <= 30)

        // Verifica Liebig growth phase gating sul giorno della visita (18 settembre, dayIndex = 24)
        val evalVisit = MushroomAlgorithms.evaluateGrowthPhase(series, edulis, dayIndex = 24)
        assertEquals(GrowthStage.MYCELIAL_HYDRATION, evalVisit.stage)
        assertEquals(1, evalVisit.daysSinceTrigger)
        assertTrue(
            "Il moltiplicatore fenologico il 18 set deve essere <= 0.45 (attuale: ${evalVisit.multiplier})",
            evalVisit.multiplier <= 0.45
        )

        // Calcolo della probabilità al 18 settembre: deve riflettere la stasi fisiologica (<= 45%)
        val wVisit = MushroomAlgorithms.calculateWeatherScore(
            24,
            series,
            species = edulis,
            config = EcologicalWeightsConfig.PHENOLOGICAL,
            canopyCover = 0.70
        )
        val pVisit = MushroomAlgorithms.dailyGrowthProbability(
            weatherScore = wVisit,
            habitatScore = 0.80,
            altitudeScore = 0.85,
            seasonalityScore = 1.0,
            terrainModifier = 1.00,
            config = EcologicalWeightsConfig.PHENOLOGICAL,
            growthPhaseMultiplier = evalVisit.multiplier,
            species = edulis
        )
        assertTrue(
            "Al 18 settembre (uscita reale con 0 funghi) la probabilità deve essere <= 45% per via di Liebig (attuale: $pVisit%)",
            pVisit <= 45
        )
    }
}
