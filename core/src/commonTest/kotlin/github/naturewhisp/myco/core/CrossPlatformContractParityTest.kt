package github.naturewhisp.myco.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Suite di parità contrattuale cross-platform: verifica su JVM e test runner comuni
 * che tutti i tipi, costruttori, proprietà e logiche esportati verso Swift/Objective-C
 * mantengano stabilità di firma, assenza di regressioni e semantica identica.
 */
class CrossPlatformContractParityTest {

    @Test
    fun testAnalysisInputsConstructorsAndBuilderParity() {
        val days = listOf(
            ProcessedDay("2026-09-15", 18.0, 0.0, 75.0, 1),
            ProcessedDay("2026-09-16", 17.5, 5.0, 80.0, 2),
        )
        val elevationSamples = listOf(450.0, 460.0, 440.0, 455.0, 445.0)

        // 1. Costruttore a 14 parametri (primario con calculationMode)
        val input14 = AnalysisInputs(
            days = days,
            todayIndex = 1,
            speciesId = "boletus_edulis",
            habitatScore = 0.90,
            habitatDescription = "Bosco misto",
            canopyTypes = listOf("Fagus", "Quercus"),
            elevationSamples = elevationSamples,
            monthIndex = 8,
            spunEcmRichness = 55.0,
            spunHyphalDensity = 3.2,
            missingSources = listOf("DEM"),
            canopyCover = 0.75,
            forestProximityIndex = 0.75,
            calculationMode = "ALL",
        )
        assertEquals("boletus_edulis", input14.speciesId)
        assertEquals(0.75, input14.canopyCover)
        assertEquals(0.75, input14.forestProximityIndex)
        assertEquals("ALL", input14.calculationMode)

        // 2. Costruttore a 13 parametri (per Swift, omette calculationMode)
        val input13 = AnalysisInputs(
            days = days,
            todayIndex = 1,
            speciesId = "boletus_edulis",
            habitatScore = 0.90,
            habitatDescription = "Bosco misto",
            canopyTypes = listOf("Fagus"),
            elevationSamples = elevationSamples,
            monthIndex = 8,
            spunEcmRichness = null,
            spunHyphalDensity = null,
            missingSources = emptyList(),
            canopyCover = 0.70,
            forestProximityIndex = 0.70,
        )
        assertEquals("ALL", input13.calculationMode)
        assertEquals(0.70, input13.canopyCover)

        // 3. Costruttore a 12 parametri (per Swift legacy)
        val input12 = AnalysisInputs(
            days = days,
            todayIndex = 1,
            speciesId = "boletus_edulis",
            habitatScore = 0.85,
            habitatDescription = "Bosco",
            canopyTypes = emptyList(),
            elevationSamples = elevationSamples,
            monthIndex = 8,
            spunEcmRichness = null,
            spunHyphalDensity = null,
            missingSources = emptyList(),
            canopyCover = 0.60,
        )
        assertEquals(0.60, input12.canopyCover)
        assertEquals(0.60, input12.forestProximityIndex)
        assertEquals("ALL", input12.calculationMode)

        // 4. Costruttore a 11 parametri (fallback minimale)
        val input11 = AnalysisInputs(
            days = days,
            todayIndex = 1,
            speciesId = "general",
            habitatScore = 0.50,
            habitatDescription = "Generale",
            canopyTypes = emptyList(),
            elevationSamples = elevationSamples,
            monthIndex = 8,
            spunEcmRichness = null,
            spunHyphalDensity = null,
            missingSources = emptyList(),
        )
        assertEquals(0.0, input11.canopyCover)
        assertEquals("ALL", input11.calculationMode)

        // 5. Pattern AnalysisInputsBuilder
        val built = AnalysisInputs.builder()
            .days(days)
            .todayIndex(1)
            .speciesId("macrolepiota_procera")
            .habitatScore(0.95)
            .habitatDescription("Prato")
            .canopyCover(0.20)
            .calculationMode("ALL")
            .build()

        assertEquals("macrolepiota_procera", built.speciesId)
        assertEquals(0.95, built.habitatScore)
        assertEquals(0.20, built.canopyCover)
        assertEquals(0.20, built.forestProximityIndex)
    }

    @Test
    fun testHabitatEvidenceAndStatusContracts() {
        val evidence = HabitatEvidence(
            status = HabitatStatus.KNOWN_SUITABLE,
            forestCoverFraction = 0.80,
            meadowFraction = 0.15,
            distanceToNearestForestMeters = 0.0,
            confirmedHostGenera = setOf("Castanea"),
            dominantLeafType = "broadleaved",
        )
        assertEquals(HabitatStatus.KNOWN_SUITABLE, evidence.status)
        assertEquals(0.80, evidence.forestCoverFraction)
        assertEquals("broadleaved", evidence.dominantLeafType)

        val fallback = HabitatEvidence.UNKNOWN_HABITAT
        assertEquals(HabitatStatus.UNKNOWN, fallback.status)
        assertEquals(0.0, fallback.forestCoverFraction)
        assertEquals(0.0, fallback.meadowFraction)
        assertEquals(500.0, fallback.distanceToNearestForestMeters)
        assertTrue(fallback.confirmedHostGenera.isEmpty())
        assertNull(fallback.dominantLeafType)

        val secondary = HabitatEvidence(
            status = HabitatStatus.KNOWN_UNSUITABLE,
            forestCoverFraction = 0.0,
            meadowFraction = 0.0,
            distanceToNearestForestMeters = 1000.0,
            confirmedHostGenera = emptySet(),
        )
        assertNull(secondary.dominantLeafType)
    }

    @Test
    fun testHabitatScoringUnifiedAcrossGuilds() {
        val edulis = SpeciesCatalog.byId("boletus_edulis")
        val procera = SpeciesCatalog.byId("macrolepiota_procera")
        val mellea = SpeciesCatalog.byId("armillaria_mellea")

        // 1. Saprotrofo praticolo in prato aperto
        val meadowEvidence = HabitatEvidence(
            status = HabitatStatus.KNOWN_SUITABLE,
            forestCoverFraction = 0.0,
            meadowFraction = 0.80,
            distanceToNearestForestMeters = 500.0,
            confirmedHostGenera = emptySet(),
        )
        val proceraEval = MycoAlgorithms.evaluateHabitat(meadowEvidence, procera)
        assertEquals(0.95, proceraEval.score, 0.01)
        assertTrue(proceraEval.baseText.contains("Praticolo"))

        // 2. Ectomicorrizico in prato aperto senza bosco -> score basso
        val edulisInMeadow = MycoAlgorithms.evaluateHabitat(meadowEvidence, edulis)
        assertTrue(edulisInMeadow.score <= 0.15)

        // 3. Ectomicorrizico in faggeta con essenza confermata -> score alto con bonus
        val forestEvidence = HabitatEvidence(
            status = HabitatStatus.KNOWN_SUITABLE,
            forestCoverFraction = 0.85,
            meadowFraction = 0.0,
            distanceToNearestForestMeters = 0.0,
            confirmedHostGenera = setOf("Fagus"),
        )
        val edulisEval = MycoAlgorithms.evaluateHabitat(forestEvidence, edulis, spunEcmRichness = 60.0)
        assertTrue(edulisEval.score >= 0.95)
        assertTrue(edulisEval.baseScore < edulisEval.score, "baseScore deve riflettere il valore prima del bonus ospite/SPUN")
        assertTrue(edulisEval.bonusText.contains("ospiti", ignoreCase = true))
        assertEquals(MycoAlgorithms.evaluateHabitat(forestEvidence, edulis, null).score, edulisEval.score, 1e-9)

        // 4. Parassita lignicolo con bosco fitto
        val melleaEval = MycoAlgorithms.evaluateHabitat(forestEvidence, mellea)
        assertTrue(melleaEval.score >= 0.85)
    }

    @Test
    fun testGregorianEpochDayCalculationsAndNonCompressingLatency() {
        // Verifica Howard Hinnant algorithm
        assertEquals(0L, MycoAlgorithms.isoDateToEpochDay("1970-01-01"))
        assertEquals(1L, MycoAlgorithms.isoDateToEpochDay("1970-01-02"))
        assertEquals(365L, MycoAlgorithms.isoDateToEpochDay("1971-01-01"))
        assertEquals(20731L, MycoAlgorithms.isoDateToEpochDay("2026-10-05"))
        // Supporto robusto per ISO8601 con componente oraria (es. 2026-10-05T12:00:00Z)
        assertEquals(20731L, MycoAlgorithms.isoDateToEpochDay("2026-10-05T12:00:00Z"))
        assertEquals(20731L, MycoAlgorithms.isoDateToEpochDay("2026-10-05 08:30:00"))

        // Anno bisestile 2024
        val feb28 = MycoAlgorithms.isoDateToEpochDay("2024-02-28")
        val feb29 = MycoAlgorithms.isoDateToEpochDay("2024-02-29")
        val mar01 = MycoAlgorithms.isoDateToEpochDay("2024-03-01")
        assertNotNull(feb28)
        assertNotNull(feb29)
        assertNotNull(mar01)
        assertEquals(1L, feb29 - feb28)
        assertEquals(1L, mar01 - feb29)

        // Formato invalido
        assertNull(MycoAlgorithms.isoDateToEpochDay("invalid"))
        assertNull(MycoAlgorithms.isoDateToEpochDay("2026-02-30"))

        // Differenza di giorni
        assertEquals(9L, MycoAlgorithms.daysBetween("2026-09-01", "2026-09-10"))
        assertEquals(-9L, MycoAlgorithms.daysBetween("2026-09-10", "2026-09-01"))

        // Verifica anti-compressione: serie con buchi temporali
        val species = SpeciesCatalog.byId("boletus_edulis")
        val daysWithGaps = listOf(
            ProcessedDay("2026-09-01", 18.0, 25.0, 80.0, 61), // Pioggia 14 giorni fa rispetto al 15
            ProcessedDay("2026-09-10", 18.0, 0.0, 75.0, 1),   // 5 giorni fa rispetto al 15
            ProcessedDay("2026-09-15", 18.0, 0.0, 75.0, 1),   // Target day (index 2)
        )

        val targetIndex = 2
        // Con gli indici posizionali la distanza sarebbe stata 2 - 0 = 2 gg!
        // Con le date gregoriane la latenza reale è 14 gg!
        val effectiveRain = MycoAlgorithms.calculateEffectiveRainfall(targetIndex, daysWithGaps, species)
        assertTrue(effectiveRain > 0.0, "La pioggia deve essere conteggiata con la corretta latenza di 14 gg")

        // Calcolo latenza giorni
        assertEquals(14, MycoAlgorithms.calculateDaysSince(daysWithGaps, 2, 0))
        assertEquals(5, MycoAlgorithms.calculateDaysSince(daysWithGaps, 2, 1))
    }

    @Test
    fun testHeatmapEngineGuildSupportAndDescriptionParity() {
        val engine = HeatmapEngine()
        val dummyGrid = SpunGrid(
            header = SpunRegionHeader(1, "ITA", 35.0, 48.0, 6.0, 19.0, 10, 10, 100),
            ecmData = ByteArray(100),
            hyphalData = ByteArray(100),
        )

        // Saprotrofo praticolo non supportato da EcM
        val saprotrophicRaster = engine.generate(
            centerLatitude = 45.0,
            centerLongitude = 10.0,
            grid = dummyGrid,
            baseWeatherScore = 80.0,
            seasonalityScore = 0.9,
            altitudeScore = 0.9,
            speciesId = "macrolepiota_procera",
            isDark = false,
        )
        assertNotNull(saprotrophicRaster)
        assertEquals(HeatmapLayerStatus.UNAVAILABLE_GUILD_NOT_SUPPORTED, saprotrophicRaster.layerStatus)
        assertEquals(
            "Layer non disponibile: macromiceti saprotrofi praticoli non mappati da SPUN EcM forestale",
            saprotrophicRaster.statusDescription,
        )

        // Parassita lignicolo non supportato da EcM
        val parasiticRaster = engine.generate(
            centerLatitude = 45.0,
            centerLongitude = 10.0,
            grid = dummyGrid,
            baseWeatherScore = 80.0,
            seasonalityScore = 0.9,
            altitudeScore = 0.9,
            speciesId = "armillaria_mellea",
            isDark = false,
        )
        assertNotNull(parasiticRaster)
        assertEquals(HeatmapLayerStatus.UNAVAILABLE_GUILD_NOT_SUPPORTED, parasiticRaster.layerStatus)
        assertEquals(
            "Layer non disponibile: macromiceti lignicoli/parassiti non tracciati da SPUN EcM",
            parasiticRaster.statusDescription,
        )
    }

    @Test
    fun testAnalysisResultPropertiesAndInvariants() {
        val result = AnalysisResult(
            probability = 72,
            tier = ProbabilityTier.HIGH,
            weatherScore = 78,
            habitatScore = 0.90,
            altitudeScore = 0.95,
            seasonalityScore = 0.85,
            terrain = TerrainAspect(500.0, 10.0, 180.0, "Sud", 1.05),
            factors = emptyList(),
            dailyOutlooks = emptyList(),
            deterministicFieldNote = "Idoneità alta.",
            missingSources = emptyList(),
            growthPhase = null,
            dataQuality = DataQualityStatus.OPTIMAL,
            waterDiagnosis = "Ottimale",
            effectiveRainMm = 28.5,
        )

        assertEquals(72, result.suitabilityScore)
        assertTrue(result.isCalculable)
        assertEquals(28.5, result.effectiveRainMm)
    }

    @Test
    fun testApplyHabitatBonusPenaltyInvariants() {
        // Invariant 1: Penalty (bonusMult < 1.0) must never increase score
        val baseScore = 0.15
        val penaltyMult = 0.80
        val penalized = MycoAlgorithms.applyHabitatBonusPenalty(baseScore, penaltyMult)
        assertTrue(penalized <= baseScore, "Penalty must never increase base score: $penalized > $baseScore")
        assertEquals(0.12, penalized, 1e-4)

        // Invariant 2: Bonus (bonusMult > 1.0) must never decrease score
        val bonusMult = 1.15
        val bonused = MycoAlgorithms.applyHabitatBonusPenalty(baseScore, bonusMult)
        assertTrue(bonused >= baseScore, "Bonus must never decrease base score: $bonused < $baseScore")
        assertEquals(0.1725, bonused, 1e-4)

        // Invariant 3: Clamping at ceiling and floor
        val highBonus = MycoAlgorithms.applyHabitatBonusPenalty(0.95, 1.20)
        assertEquals(1.0, highBonus, 1e-4)

        val lowPenalty = MycoAlgorithms.applyHabitatBonusPenalty(0.08, 0.50)
        assertTrue(lowPenalty <= 0.08)
    }

    @Test
    fun testCalculateSoilMoistureFactorWithTemporalGapDegradesQuality() {
        val daysWithGap = listOf(
            ProcessedDay("2026-09-27", 18.0, 0.0, 75.0, 1, soilMoisture0To7 = 0.30),
            ProcessedDay("2026-09-28", 17.5, 5.0, 80.0, 2, soilMoisture0To7 = 0.30),
            ProcessedDay("2026-10-06", 16.0, 0.0, 70.0, 1, soilMoisture0To7 = 0.18),
        )
        val todayIndex = 2 // 2026-10-06
        val eval = MycoAlgorithms.calculateSoilMoistureFactor(daysWithGap, todayIndex)

        assertEquals(1, eval.availableDaysCount)
        assertEquals(1.0, eval.phiSoil)
        assertTrue(eval.diagnosisText.contains("Dati pedologici insufficienti"))

        val input = AnalysisInputs(
            days = daysWithGap,
            todayIndex = todayIndex,
            speciesId = "boletus_edulis",
            habitatScore = 0.8,
            habitatDescription = "Bosco",
            canopyTypes = emptyList(),
            elevationSamples = listOf(500.0),
            monthIndex = 9,
            spunEcmRichness = null,
            spunHyphalDensity = null,
            missingSources = emptyList(),
        )
        val result = MycoAnalysisEngine().analyze(input)
        assertEquals(DataQualityStatus.DEGRADED_MISSING_SOIL, result.dataQuality)
    }

    @Test
    fun testSharedOsmHabitatEvidenceExtractionAndEvaluationParity() {
        val targetLat = 44.2149
        val targetLon = 7.9755

        data class RawOsmItem(
            val lat: Double,
            val lon: Double,
            val tags: Map<String, String>
        )

        val rawOsmResponse = listOf(
            RawOsmItem(44.2150, 7.9756, mapOf("natural" to "wood", "genus" to "Fagus")),
            RawOsmItem(44.2160, 7.9760, mapOf("landuse" to "forest", "genus" to "Quercus")),
            RawOsmItem(44.2140, 7.9740, mapOf("landuse" to "meadow")),
        )

        // Android adapter extraction path
        val androidOsmElements = rawOsmResponse.map { item ->
            val natural = item.tags["natural"]
            val landuse = item.tags["landuse"]
            OsmHabitatElement(
                lat = item.lat,
                lon = item.lon,
                isWoodOrForest = natural == "wood" || landuse == "forest",
                isMeadowOrGrass = landuse in listOf("meadow", "grass", "pasture"),
                isUrbanOrBuilt = landuse in listOf("residential", "commercial", "industrial"),
                genus = item.tags["genus"]?.lowercase(),
                leafType = item.tags["leaf_type"]
            )
        }
        val androidEvidence = MycoAlgorithms.extractHabitatEvidence(
            elements = androidOsmElements,
            targetLat = targetLat,
            targetLon = targetLon,
            searchRadiusMeters = 1500
        )

        // iOS adapter extraction path
        val iosOsmElements = rawOsmResponse.map { item ->
            val natural = item.tags["natural"]
            val landuse = item.tags["landuse"]
            OsmHabitatElement(
                lat = item.lat,
                lon = item.lon,
                isWoodOrForest = natural == "wood" || landuse == "forest",
                isMeadowOrGrass = landuse == "meadow" || landuse == "grass" || landuse == "pasture",
                isUrbanOrBuilt = landuse == "residential" || landuse == "commercial" || landuse == "industrial",
                genus = item.tags["genus"]?.lowercase(),
                leafType = item.tags["leaf_type"]
            )
        }
        val iosEvidence = MycoAlgorithms.extractHabitatEvidence(
            elements = iosOsmElements,
            targetLat = targetLat,
            targetLon = targetLon,
            searchRadiusMeters = 1500
        )

        // Both adapters MUST produce identical evidence
        assertEquals(androidEvidence.status, iosEvidence.status)
        assertEquals(androidEvidence.forestCoverFraction, iosEvidence.forestCoverFraction, 1e-4)
        assertEquals(androidEvidence.meadowFraction, iosEvidence.meadowFraction, 1e-4)
        assertEquals(androidEvidence.confirmedHostGenera, iosEvidence.confirmedHostGenera)

        // Evaluate habitat via single shared function for both ECTOMYCORRHIZAL and SAPROTROPHIC
        val edulis = SpeciesCatalog.byId("boletus_edulis")
        val edulisAndroidEval = MycoAlgorithms.evaluateHabitat(androidEvidence, edulis)
        val edulisIosEval = MycoAlgorithms.evaluateHabitat(iosEvidence, edulis)
        assertEquals(edulisAndroidEval.baseScore, edulisIosEval.baseScore, 1e-4)
        assertEquals(edulisAndroidEval.score, edulisIosEval.score, 1e-4)
        assertEquals(edulisAndroidEval.baseText, edulisIosEval.baseText)

        val procera = SpeciesCatalog.byId("macrolepiota_procera")
        val proceraAndroidEval = MycoAlgorithms.evaluateHabitat(androidEvidence, procera)
        val proceraIosEval = MycoAlgorithms.evaluateHabitat(iosEvidence, procera)
        assertEquals(proceraAndroidEval.baseScore, proceraIosEval.baseScore, 1e-4)
        assertEquals(proceraAndroidEval.score, proceraIosEval.score, 1e-4)
        assertEquals(proceraAndroidEval.baseText, proceraIosEval.baseText)
    }

    @Test
    fun testIncompleteWeatherSeriesDegradesDataQuality() {
        val gappedDays = listOf(
            ProcessedDay("2026-10-04", 15.0, 0.0, 70.0, 1, soilMoisture0To7 = 0.25),
            ProcessedDay("2026-10-05", 15.0, 0.0, 70.0, 1, soilMoisture0To7 = 0.25),
            ProcessedDay("2026-10-06", 14.0, 0.0, 68.0, 1, soilMoisture0To7 = 0.24),
        )
        val input = AnalysisInputs(
            days = gappedDays,
            todayIndex = 2,
            speciesId = "boletus_edulis",
            habitatScore = 0.8,
            habitatDescription = "Bosco",
            canopyTypes = emptyList(),
            elevationSamples = listOf(500.0),
            monthIndex = 9,
            spunEcmRichness = null,
            spunHyphalDensity = null,
            missingSources = emptyList(),
        )
        val result = MycoAnalysisEngine().analyze(input)
        assertEquals(DataQualityStatus.DEGRADED_INCOMPLETE_WEATHER, result.dataQuality)
        assertTrue(result.deterministicFieldNote.contains("Serie meteorologica lacunosa o incompleta."))
        assertTrue(result.deterministicFieldNote.contains("meteo lacunoso"))
    }

    @Test
    fun testGeoCoordinatesDomainValidationAndHaversineDistance() {
        val roma = GeoCoordinates(41.8902, 12.4922)
        val milano = GeoCoordinates(45.4642, 9.1900)

        // Validazione coordinate
        assertEquals(41.8902, roma.latitude)
        assertEquals(12.4922, roma.longitude)

        // Eccezioni su coordinate invalide
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            GeoCoordinates(91.0, 0.0)
        }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            GeoCoordinates(-90.1, 0.0)
        }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            GeoCoordinates(0.0, 180.1)
        }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            GeoCoordinates(0.0, -180.1)
        }

        // Distanza verso se stesso
        assertEquals(0.0, roma.distanceToMeters(roma), 1e-6)
        assertEquals(0.0, roma.distanceToKm(roma), 1e-6)

        // Distanza Roma-Milano (~477 km)
        val distKm = roma.distanceToKm(milano)
        assertTrue(distKm in 470.0..485.0, "Distanza attesa ~477 km, ottenuta $distKm")
        assertEquals(distKm * 1000.0, roma.distanceToMeters(milano), 1.0)

        // Simmetria
        assertEquals(roma.distanceToMeters(milano), milano.distanceToMeters(roma), 1e-3)

        // Overload extractHabitatEvidence con GeoCoordinates target
        val elements = listOf(
            OsmHabitatElement(lat = 41.8910, lon = 12.4930, isWoodOrForest = true, isMeadowOrGrass = false, isUrbanOrBuilt = false, genus = "Quercus"),
        )
        val evidenceFromCoords = MycoAlgorithms.extractHabitatEvidence(elements, roma, 1000)
        val evidenceFromDoubles = MycoAlgorithms.extractHabitatEvidence(elements, roma.latitude, roma.longitude, 1000)
        assertEquals(evidenceFromDoubles.forestCoverFraction, evidenceFromCoords.forestCoverFraction)
        assertEquals(evidenceFromDoubles.confirmedHostGenera, evidenceFromCoords.confirmedHostGenera)
        assertEquals(evidenceFromDoubles.status, evidenceFromCoords.status)
    }
}
