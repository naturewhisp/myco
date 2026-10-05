package github.naturewhisp.myco

import github.naturewhisp.myco.core.AnalysisInputs
import github.naturewhisp.myco.core.MycoAlgorithms
import github.naturewhisp.myco.core.MycoAnalysisEngine
import github.naturewhisp.myco.core.ParameterRegistry
import github.naturewhisp.myco.core.ProcessedDay
import github.naturewhisp.myco.core.SpeciesCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Protocollo di validazione comparativa e analisi di sensitività (REV2-09).
 *
 * Valuta l'utilità euristica del Percorso A confrontandolo con baseline climatologiche
 * e certifica la stabilità matematica rispetto a variazioni parametriche.
 */
class ComparativeValidationTest {

    private val species = SpeciesCatalog.byId("boletus_edulis")

    @Test
    fun benchmarkClimatologicalBaselineComparison() {
        val baseDate = java.time.LocalDate.of(2026, 8, 25)
        val wetDays = List(35) { index ->
            ProcessedDay(
                dateIso = baseDate.plusDays(index.toLong()).toString(),
                avgTemp = 16.0,
                totalPrecipMm = if (index == 23) 35.0 else 0.0,
                avgHumidityPercent = 82.0,
                weatherCode = 1,
                soilMoisture0To7 = if (index >= 23) 0.30 else 0.20,
                soilMoisture7To28 = 0.25,
                evapotranspiration = 1.5,
                minTemp = 11.0,
                maxTemp = 21.0,
            )
        }

        val droughtDays = List(35) { index ->
            ProcessedDay(
                dateIso = "2026-09-${(index + 1).toString().padStart(2, '0')}",
                avgTemp = 16.0,
                totalPrecipMm = 0.0, // Nessuna pioggia negli ultimi 35 giorni
                avgHumidityPercent = 45.0,
                weatherCode = 0,
                soilMoisture0To7 = 0.12,
                soilMoisture7To28 = 0.15,
                evapotranspiration = 3.2,
                minTemp = 11.0,
                maxTemp = 21.0,
            )
        }

        val wetInput = AnalysisInputs(
            days = wetDays,
            todayIndex = 34, // tau = 11 gg post-pioggia
            speciesId = "boletus_edulis",
            habitatScore = 0.90,
            habitatDescription = "Faggeta",
            canopyTypes = listOf("fagus"),
            elevationSamples = listOf(950.0, 950.0, 950.0, 950.0),
            monthIndex = 8, // Settembre
            spunEcmRichness = null,
            spunHyphalDensity = null,
            missingSources = emptyList(),
            canopyCover = 0.80,
        )

        val droughtInput = AnalysisInputs(
            days = droughtDays,
            todayIndex = 34,
            speciesId = "boletus_edulis",
            habitatScore = 0.90,
            habitatDescription = "Faggeta",
            canopyTypes = listOf("fagus"),
            elevationSamples = listOf(950.0, 950.0, 950.0, 950.0),
            monthIndex = 8,
            spunEcmRichness = null,
            spunHyphalDensity = null,
            missingSources = emptyList(),
            canopyCover = 0.80,
        )

        val engine = MycoAnalysisEngine()
        val wetResult = engine.analyze(wetInput)
        val droughtResult = engine.analyze(droughtInput)

        // Baseline climatologica pura: la sola stagionalità S assegna 1.0 (100%) a entrambi gli scenari
        val climatologicalBaseline = MycoAlgorithms.seasonalityScore(8, species)
        assertEquals(1.0, climatologicalBaseline, 0.001)

        // Il modello A discrimina nettamente tra le due stazioni
        assertTrue("Wet station suitability must be high (>= 60), got ${wetResult.probability}", wetResult.probability >= 60)
        assertTrue("Drought station suitability must be low (<= 20), got ${droughtResult.probability}", droughtResult.probability <= 20)
        val informationGain = wetResult.probability - droughtResult.probability
        assertTrue("Model must achieve wide discrimination gap (>= 40 points), got $informationGain", informationGain >= 40)
    }

    @Test
    fun rankingConcordanceAcrossStations() {
        val baseDays = List(35) { index ->
            val dateStr = if (index < 6) {
                "2026-08-${(26 + index).toString().padStart(2, '0')}"
            } else {
                "2026-09-${(index - 5).toString().padStart(2, '0')}"
            }
            ProcessedDay(
                dateIso = dateStr,
                avgTemp = 16.0,
                totalPrecipMm = if (index == 23) 35.0 else 0.0,
                avgHumidityPercent = 80.0,
                weatherCode = 1,
                soilMoisture0To7 = if (index >= 23) 0.30 else 0.20,
                soilMoisture7To28 = 0.25,
                evapotranspiration = 1.5,
                minTemp = 11.0,
                maxTemp = 21.0,
            )
        }

        val engine = MycoAnalysisEngine()

        // Stazione 1: Faggeta matura ideale a 1000m
        val s1 = engine.analyze(
            AnalysisInputs(
                days = baseDays,
                todayIndex = 34,
                speciesId = "boletus_edulis",
                habitatScore = 0.95,
                habitatDescription = "Faggeta matura",
                canopyTypes = listOf("fagus"),
                elevationSamples = listOf(1000.0, 1000.0, 1000.0, 1000.0),
                monthIndex = 8,
                spunEcmRichness = 50.0,
                spunHyphalDensity = 4.0,
                missingSources = emptyList(),
                canopyCover = 0.85,
            )
        ).probability

        // Stazione 2: Bosco misto a quota inferiore (400m)
        val s2 = engine.analyze(
            AnalysisInputs(
                days = baseDays,
                todayIndex = 34,
                speciesId = "boletus_edulis",
                habitatScore = 0.70,
                habitatDescription = "Bosco misto",
                canopyTypes = listOf("fagus"),
                elevationSamples = listOf(400.0, 400.0, 400.0, 400.0),
                monthIndex = 8,
                spunEcmRichness = null,
                spunHyphalDensity = null,
                missingSources = emptyList(),
                canopyCover = 0.60,
            )
        ).probability

        // Stazione 3: Mindino in siccità superficiale
        val s3 = engine.analyze(
            AnalysisInputs(
                days = baseDays.mapIndexed { idx, day ->
                    day.copy(soilMoisture0To7 = if (idx in 24..34) 0.16 else day.soilMoisture0To7)
                },
                todayIndex = 34,
                speciesId = "boletus_edulis",
                habitatScore = 0.85,
                habitatDescription = "Bosco",
                canopyTypes = listOf("fagus"),
                elevationSamples = listOf(900.0, 900.0, 900.0, 900.0),
                monthIndex = 8,
                spunEcmRichness = null,
                spunHyphalDensity = null,
                missingSources = emptyList(),
                canopyCover = 0.75,
            )
        ).probability

        // Stazione 4: Pascolo aperto senza piante ospiti (Hurdle bloccante)
        val s4 = engine.analyze(
            AnalysisInputs(
                days = baseDays,
                todayIndex = 34,
                speciesId = "boletus_edulis",
                habitatScore = 0.10,
                habitatDescription = "Prato",
                canopyTypes = emptyList(),
                elevationSamples = listOf(900.0, 900.0, 900.0, 900.0),
                monthIndex = 8,
                spunEcmRichness = null,
                spunHyphalDensity = null,
                missingSources = emptyList(),
                canopyCover = 0.0,
            )
        ).probability

        // Stazione 5: Vetta alpina fredda (2200m, Tavg = 1°C, Tmin = -3°C)
        val s5 = engine.analyze(
            AnalysisInputs(
                days = baseDays.map { it.copy(avgTemp = 1.0, minTemp = -3.0, maxTemp = 4.0) },
                todayIndex = 34,
                speciesId = "boletus_edulis",
                habitatScore = 0.10,
                habitatDescription = "Ghiaione alpino",
                canopyTypes = emptyList(),
                elevationSamples = listOf(2200.0, 2200.0, 2200.0, 2200.0),
                monthIndex = 8,
                spunEcmRichness = null,
                spunHyphalDensity = null,
                missingSources = emptyList(),
                canopyCover = 0.0,
            )
        ).probability

        // Verifica concordanza gerarchica stretta: S1 > S2 > S3 > S4 >= S5
        assertTrue("Station 1 ($s1) must outrank Station 2 ($s2)", s1 > s2)
        assertTrue("Station 2 ($s2) must outrank Station 3 ($s3)", s2 > s3)
        assertTrue("Station 3 ($s3) must outrank Station 4 ($s4)", s3 > s4)
        assertTrue("Station 4 ($s4) must be equal or greater than Station 5 ($s5)", s4 >= s5)
    }

    @Test
    fun parameterSensitivitySweeps_boundedPerturbations() {
        val baseWeather = 75
        val baseHabitat = 0.85
        val baseAlt = 1.0
        val baseSeason = 1.0
        val baseTerrain = 1.0
        val basePhase = 1.0

        val baseScore = MycoAlgorithms.calculateSuitabilityScore(
            weatherScore = baseWeather,
            habitatScore = baseHabitat,
            altitudeScore = baseAlt,
            seasonalityScore = baseSeason,
            terrainModifier = baseTerrain,
            growthPhaseMultiplier = basePhase,
            species = species,
            useHurdle = true,
        )

        // Test perturbazione su input continuo (+/- 5%)
        val perturbedHigh = MycoAlgorithms.calculateSuitabilityScore(
            weatherScore = (baseWeather * 1.05).toInt(),
            habitatScore = baseHabitat * 1.05,
            altitudeScore = baseAlt,
            seasonalityScore = baseSeason,
            terrainModifier = baseTerrain,
            growthPhaseMultiplier = basePhase,
            species = species,
            useHurdle = true,
        )

        val perturbedLow = MycoAlgorithms.calculateSuitabilityScore(
            weatherScore = (baseWeather * 0.95).toInt(),
            habitatScore = baseHabitat * 0.95,
            altitudeScore = baseAlt,
            seasonalityScore = baseSeason,
            terrainModifier = baseTerrain,
            growthPhaseMultiplier = basePhase,
            species = species,
            useHurdle = true,
        )

        // Lipschitz-continuità: delta di idoneità per variazione del 5% non deve superare 10 punti
        val deltaHigh = abs(perturbedHigh - baseScore)
        val deltaLow = abs(perturbedLow - baseScore)
        assertTrue("Delta high must be <= 10.0, got $deltaHigh", deltaHigh <= 10.0)
        assertTrue("Delta low must be <= 10.0, got $deltaLow", deltaLow <= 10.0)

        // Monotonia locale
        assertTrue("Perturbed high must be >= base score", perturbedHigh >= baseScore)
        assertTrue("Perturbed low must be <= base score", perturbedLow <= baseScore)
    }

    @Test
    fun parameterRegistryCompleteness() {
        assertTrue(ParameterRegistry.ALL.isNotEmpty())
        assertEquals(15, ParameterRegistry.ALL.size)

        // Verifica che ogni parametro abbia metadati completi e non vuoti
        ParameterRegistry.ALL.forEach { param ->
            assertTrue(param.key.isNotBlank())
            assertTrue(param.name.isNotBlank())
            assertTrue(param.unit.isNotBlank())
            assertTrue(param.reference.isNotBlank())
            assertTrue(param.domain.isNotBlank())
            assertTrue(param.description.isNotBlank())
        }
    }
}
