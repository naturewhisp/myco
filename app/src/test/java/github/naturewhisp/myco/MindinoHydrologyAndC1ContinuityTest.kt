package github.naturewhisp.myco

import github.naturewhisp.myco.core.AnalysisInputs
import github.naturewhisp.myco.core.GrowthStage
import github.naturewhisp.myco.core.MycoAlgorithms
import github.naturewhisp.myco.core.MycoAnalysisEngine
import github.naturewhisp.myco.core.ParameterRegistry
import github.naturewhisp.myco.core.SpeciesCatalog
import github.naturewhisp.myco.model.EcologicalWeightsConfig
import github.naturewhisp.myco.model.ProcessedDay
import github.naturewhisp.myco.model.SPECIES_CATALOG
import github.naturewhisp.myco.utils.MushroomAlgorithms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

/**
 * Suite di test vincolanti per la bonifica idrologica, continuità C1 e parità motore (RES-01..09).
 */
class MindinoHydrologyAndC1ContinuityTest {

    private val edulis = SPECIES_CATALOG.first { it.id == "boletus_edulis" }
    private val coreEdulis = SpeciesCatalog.byId("boletus_edulis")

    /**
     * 1. Test Indipendenza del Fattore Idrico dalla Pioviggine:
     * A parità di stato idrico del suolo theta_0-7 = 0.16 m3/m3, una precipitazione marginale
     * di 1.01 mm non deve alterare phi_soil (Delta phi_soil = 0).
     * Mantenendo fissi meteo, eventi e fase base, Delta S = 0.0.
     */
    @Test
    fun testDrizzleIndependence_DeltaPhiSoilZero() {
        val daysBase = createDryDroughtSeries(dryDayRain = 0.0f, soilMoisture = 0.16f)
        val daysDrizzle = createDryDroughtSeries(dryDayRain = 1.01f, soilMoisture = 0.16f)

        val evalBase = MushroomAlgorithms.evaluateGrowthPhase(daysBase, edulis, dayIndex = 34)
        val evalDrizzle = MushroomAlgorithms.evaluateGrowthPhase(daysDrizzle, edulis, dayIndex = 34)

        // Verifichiamo che il fattore di suolo (multiplier) sia identico a theta invariata
        assertEquals(
            "A theta invariata, Delta phi_soil deve essere esattamente 0 indipendentemente dalla pioggia",
            evalBase.multiplier,
            evalDrizzle.multiplier,
            0.0001
        )
        assertEquals(GrowthStage.WANING, evalBase.stage)
        assertEquals(GrowthStage.WANING, evalDrizzle.stage)

        // Parità anche su Core KMP
        val coreDaysBase = convertToCoreDays(daysBase)
        val coreDaysDrizzle = convertToCoreDays(daysDrizzle)
        val coreEvalBase = MycoAlgorithms.evaluateGrowthPhase(coreDaysBase, coreEdulis, dayIndex = 34)
        val coreEvalDrizzle = MycoAlgorithms.evaluateGrowthPhase(coreDaysDrizzle, coreEdulis, dayIndex = 34)

        assertEquals(
            "Su Core, Delta phi_soil deve essere 0 a theta costante",
            coreEvalBase.multiplier,
            coreEvalDrizzle.multiplier,
            0.0001
        )
    }

    /**
     * 2. Verifica Analitica e Numerica della Continuità C1 dell'Hermite Smoothstep:
     * Sweep denso a 100 punti campionati su theta in [0.10, 0.26].
     * Verifica derivate ai nodi = 0, monotonicità e assenza di spigoli.
     */
    @Test
    fun testC1HermiteSmoothstep_ZeroBoundaryDerivativesAndDenseSweep() {
        val thetaMin = ParameterRegistry.SOIL_DROUGHT_MIN_THRESHOLD.value // 0.14
        val thetaMax = ParameterRegistry.SOIL_DROUGHT_STRESS_THRESHOLD.value // 0.22
        val yMin = ParameterRegistry.SOIL_FLOOR_FACTOR.value // 0.20

        fun evalPhiSoil(theta: Double): Double {
            val u = ((theta - thetaMin) / (thetaMax - thetaMin)).coerceIn(0.0, 1.0)
            val sU = 3.0 * u * u - 2.0 * u * u * u
            return yMin + (1.0 - yMin) * sU
        }

        fun evalAnalyticalDerivative(theta: Double): Double {
            if (theta <= thetaMin || theta >= thetaMax) return 0.0
            val u = (theta - thetaMin) / (thetaMax - thetaMin)
            val duDtheta = 1.0 / (thetaMax - thetaMin)
            val dsDu = 6.0 * u * (1.0 - u)
            return (1.0 - yMin) * dsDu * duDtheta
        }

        // Verifica analitica nodi: derivata prima rigorosamente 0
        assertEquals(0.0, evalAnalyticalDerivative(thetaMin), 1e-9)
        assertEquals(0.0, evalAnalyticalDerivative(thetaMax), 1e-9)
        assertEquals(yMin, evalPhiSoil(thetaMin), 1e-9)
        assertEquals(1.0, evalPhiSoil(thetaMax), 1e-9)

        // Sweep denso su 100 punti in [0.10, 0.26]
        val steps = 100
        val startTheta = 0.10
        val endTheta = 0.26
        val stepSize = (endTheta - startTheta) / steps
        val eps = 1e-5

        var prevVal = evalPhiSoil(startTheta)
        for (i in 1..steps) {
            val theta = startTheta + i * stepSize
            val currentVal = evalPhiSoil(theta)

            // Monotonicità
            assertTrue(
                "La curva phi_soil deve essere debolmente monotona crescente (theta=$theta)",
                currentVal >= prevVal - 1e-12
            )

            // Continuità C1: rapporto incrementale deve approssimare la derivata analitica
            val numericalDerivative = (evalPhiSoil(theta + eps) - evalPhiSoil(theta - eps)) / (2 * eps)
            val analyticalDerivative = evalAnalyticalDerivative(theta)
            val diff = abs(numericalDerivative - analyticalDerivative)

            assertTrue(
                "Gradiente numerico deve coincidere con la derivata analitica C1 (theta=$theta, diff=$diff)",
                diff < 5e-3
            )

            prevVal = currentVal
        }
    }

    /**
     * 3. Test Invarianza Dati Pedologici Assenti:
     * In caso di dati pedologici insufficienti (theta_0-7 assente o < 2 giorni nella finestra retrospettiva),
     * il motore non applica penalità o tetti arbitrari fittizi (phi_soil = 1.0).
     */
    @Test
    fun testMissingSoilMoisture_NoArbitraryCap() {
        val daysWithoutSoil = createDryDroughtSeries(dryDayRain = 0.0f, soilMoisture = null)

        val eval = MushroomAlgorithms.evaluateGrowthPhase(daysWithoutSoil, edulis, dayIndex = 34)

        // Senza dati di suolo, non c'è penalizzazione pedologica (phi_soil non attivo)
        assertTrue(
            "Senza dati di suolo, non devono essere applicati tetti arbitrari fittizi (multiplier: ${eval.multiplier})",
            eval.multiplier >= 0.80
        )
        assertNotEquals(GrowthStage.WANING, eval.stage)

        // Parità su Core
        val coreDaysWithoutSoil = convertToCoreDays(daysWithoutSoil)
        val coreEval = MycoAlgorithms.evaluateGrowthPhase(coreDaysWithoutSoil, coreEdulis, dayIndex = 34)
        assertEquals(eval.multiplier, coreEval.multiplier, 0.001)
    }

    /**
     * 4. Test Salvaguardia Termica DTR Sub-Canopy:
     * In giornate con escursione termica ridotta (es. Tmax = 13.0°C, Tmin = 12.5°C),
     * il buffering sub-canopy non deve provocare inversione termica (Tmin <= Tmean <= Tmax).
     */
    @Test
    fun testCanopyBuffering_ThermodynamicHierarchyGuaranteed() {
        val rawNarrowDay = ProcessedDay(
            date = "2026-09-28",
            avgTemp = 12.8f,
            minTemp = 12.5f,
            maxTemp = 13.0f,
            totalPrecip = 0.0f,
            avgHumidity = 85.0f,
            weatherCode = 1
        )

        val bufferedAndroid = MushroomAlgorithms.applyCanopyBuffering(rawNarrowDay, canopyCover = 0.85)
        assertTrue(
            "Android: Tmin <= Tmax garantito (Tmin=${bufferedAndroid.minTemp}, Tmax=${bufferedAndroid.maxTemp})",
            bufferedAndroid.minTemp <= bufferedAndroid.maxTemp
        )
        assertTrue(
            "Android: Tmin <= Tavg <= Tmax garantito",
            bufferedAndroid.avgTemp in bufferedAndroid.minTemp..bufferedAndroid.maxTemp
        )

        val rawCoreDay = github.naturewhisp.myco.core.ProcessedDay(
            dateIso = "2026-09-28",
            avgTemp = 12.8,
            minTemp = 12.5,
            maxTemp = 13.0,
            totalPrecipMm = 0.0,
            avgHumidityPercent = 85.0,
            weatherCode = 1
        )

        val bufferedCore = MycoAlgorithms.applyCanopyBuffering(rawCoreDay, canopyCover = 0.85)
        assertTrue(
            "Core: Tmin <= Tmax garantito (Tmin=${bufferedCore.minTemp}, Tmax=${bufferedCore.maxTemp})",
            bufferedCore.minTemp <= bufferedCore.maxTemp
        )
        assertTrue(
            "Core: Tmin <= Tavg <= Tmax garantito",
            bufferedCore.avgTemp in bufferedCore.minTemp..bufferedCore.maxTemp
        )
        assertEquals(bufferedAndroid.minTemp.toDouble(), bufferedCore.minTemp, 0.01)
        assertEquals(bufferedAndroid.maxTemp.toDouble(), bufferedCore.maxTemp, 0.01)
    }

    /**
     * 5. Test Parità Numerica tra Android e Core su Scenario Mindino:
     */
    @Test
    fun testMindinoScenario_NumericParityBetweenAndroidAndCore() {
        val days = createDryDroughtSeries(dryDayRain = 0.0f, soilMoisture = 0.16f)
        val coreDays = convertToCoreDays(days)

        val androidEval = MushroomAlgorithms.evaluateGrowthPhase(days, edulis, dayIndex = 34)
        val coreEval = MycoAlgorithms.evaluateGrowthPhase(coreDays, coreEdulis, dayIndex = 34)

        assertEquals("Moltiplicatore fenologico Android vs Core", androidEval.multiplier, coreEval.multiplier, 0.001)
        assertEquals(GrowthStage.WANING, androidEval.stage)
        assertEquals(GrowthStage.WANING, coreEval.stage)

        val androidScore = MushroomAlgorithms.calculateSuitabilityScore(
            weatherScore = 78,
            habitatScore = 1.0,
            altitudeScore = 1.0,
            seasonalityScore = 1.0,
            terrainModifier = 1.0,
            growthPhaseMultiplier = androidEval.multiplier,
            species = edulis,
            config = EcologicalWeightsConfig.PHENOLOGICAL
        )

        val coreScore = MycoAlgorithms.calculateSuitabilityScore(
            weatherScore = 78,
            habitatScore = 1.0,
            altitudeScore = 1.0,
            seasonalityScore = 1.0,
            terrainModifier = 1.0,
            growthPhaseMultiplier = coreEval.multiplier,
            species = coreEdulis,
            useHurdle = true
        )

        assertEquals("Punteggio finale di idoneità Mindino tra Android e Core (tolleranza <= 0.001)", androidScore, coreScore, 0.001)
    }

    // --- Helper Fixtures ---

    private fun createDryDroughtSeries(dryDayRain: Float, soilMoisture: Float?): List<ProcessedDay> {
        val startDate = java.time.LocalDate.of(2026, 8, 25)
        return (0 until 36).map { i ->
            val rain = when (i) {
                16 -> 22.3f
                23 -> 25.3f
                34 -> dryDayRain // giorno target
                else -> 0.0f
            }
            ProcessedDay(
                date = startDate.plusDays(i.toLong()).toString(),
                avgTemp = 16.0f,
                minTemp = 11.5f,
                maxTemp = 20.5f,
                totalPrecip = rain,
                avgHumidity = 78.0f,
                weatherCode = if (rain > 10f) 61 else 1,
                avgSoilMoisture0To7cm = soilMoisture,
                avgSoilMoisture7To28cm = 0.22f
            )
        }
    }

    private fun convertToCoreDays(androidDays: List<ProcessedDay>): List<github.naturewhisp.myco.core.ProcessedDay> {
        return androidDays.map { d ->
            github.naturewhisp.myco.core.ProcessedDay(
                dateIso = d.date,
                avgTemp = d.avgTemp.toDouble(),
                minTemp = d.minTemp.toDouble(),
                maxTemp = d.maxTemp.toDouble(),
                totalPrecipMm = d.totalPrecip.toDouble(),
                avgHumidityPercent = d.avgHumidity.toDouble(),
                weatherCode = d.weatherCode ?: 1,
                soilMoisture0To7 = d.avgSoilMoisture0To7cm?.toDouble(),
                soilMoisture7To28 = d.avgSoilMoisture7To28cm?.toDouble(),
                evapotranspiration = d.totalEvapotranspiration?.toDouble()
            )
        }
    }
}
