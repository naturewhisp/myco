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

        // Invocazione diretta della funzione pura di produzione
        fun evalProductionPhiSoil(theta: Double): Double {
            val series = listOf(
                github.naturewhisp.myco.core.ProcessedDay("2026-09-26", 15.0, 0.0, 70.0, 1, soilMoisture0To7 = theta),
                github.naturewhisp.myco.core.ProcessedDay("2026-09-27", 15.0, 0.0, 70.0, 1, soilMoisture0To7 = theta),
                github.naturewhisp.myco.core.ProcessedDay("2026-09-28", 15.0, 0.0, 70.0, 1, soilMoisture0To7 = theta)
            )
            return MycoAlgorithms.calculateSoilMoistureFactor(series, effectiveToday = 2).phiSoil
        }

        fun evalAnalyticalDerivative(theta: Double): Double {
            if (theta <= thetaMin || theta >= thetaMax) return 0.0
            val u = (theta - thetaMin) / (thetaMax - thetaMin)
            val duDtheta = 1.0 / (thetaMax - thetaMin)
            val dsDu = 6.0 * u * (1.0 - u)
            return (1.0 - yMin) * dsDu * duDtheta
        }

        // Verifica nodi di confine ed estremi biologici C06 (0.16 -> 0.325)
        assertEquals(0.0, evalAnalyticalDerivative(thetaMin), 1e-9)
        assertEquals(0.0, evalAnalyticalDerivative(thetaMax), 1e-9)
        assertEquals(yMin, evalProductionPhiSoil(thetaMin), 1e-9)
        assertEquals(1.0, evalProductionPhiSoil(thetaMax), 1e-9)
        assertEquals(0.20, evalProductionPhiSoil(0.10), 1e-9)
        assertEquals(0.325, evalProductionPhiSoil(0.16), 1e-4) // C06: 0.20 + 0.80 * smoothstep(0.14, 0.22, 0.16) = 0.325
        assertEquals(1.0, evalProductionPhiSoil(0.26), 1e-9)

        // Parità diretta con l'implementazione Android MushroomAlgorithms
        val androidSeries = listOf(
            ProcessedDay("2026-09-26", 15.0f, 0.0f, 70.0f, 1, avgSoilMoisture0To7cm = 0.16f),
            ProcessedDay("2026-09-27", 15.0f, 0.0f, 70.0f, 1, avgSoilMoisture0To7cm = 0.16f),
            ProcessedDay("2026-09-28", 15.0f, 0.0f, 70.0f, 1, avgSoilMoisture0To7cm = 0.16f)
        )
        val androidEval = MushroomAlgorithms.calculateSoilMoistureFactor(androidSeries, effectiveToday = 2)
        assertEquals(0.325, androidEval.phiSoil, 1e-4)
        assertEquals(evalProductionPhiSoil(0.16), androidEval.phiSoil, 1e-5)

        // Sweep denso su 100 punti in [0.10, 0.26] sulla funzione di produzione reale
        val steps = 100
        val startTheta = 0.10
        val endTheta = 0.26
        val stepSize = (endTheta - startTheta) / steps
        val eps = 1e-5

        var prevVal = evalProductionPhiSoil(startTheta)
        for (i in 1..steps) {
            val theta = startTheta + i * stepSize
            val currentVal = evalProductionPhiSoil(theta)

            // Monotonicità
            assertTrue(
                "La curva phi_soil deve essere debolmente monotona crescente (theta=$theta)",
                currentVal >= prevVal - 1e-12
            )

            // Continuità C1: rapporto incrementale deve approssimare la derivata analitica
            val numericalDerivative = (evalProductionPhiSoil(theta + eps) - evalProductionPhiSoil(theta - eps)) / (2 * eps)
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

    /**
     * 6. Test Confine Temporale Chilling Notturno (C02, C10):
     * La finestra di memoria chilling è rigorosamente governata da [ParameterRegistry.CHILLING_DURATION_DAYS] (5 giorni).
     * Una notte gelida (Tmin = 4.0°C < 6.0°C) avvenuta 8 giorni prima (fuori dalla finestra [t-5, t))
     * NON deve innescare l'espansione di latenza di +1.5 giorni (effectiveTauPeak resta 11.0).
     * Una notte gelida avvenuta 3 giorni prima (dentro la finestra) DEVE espandere la latenza a 12.5.
     */
    @Test
    fun testChillingWindow_ColdNightOutside5DaysDoesNotTriggerStasis() {
        val todayIndex = 20
        val baseDays = (0..25).map { i ->
            github.naturewhisp.myco.core.ProcessedDay(
                dateIso = "2026-09-${(i + 1).toString().padStart(2, '0')}",
                avgTemp = 16.0,
                minTemp = 12.0, // Tutte notti miti > 6°C
                maxTemp = 20.0,
                totalPrecipMm = if (i == 9) 30.0 else 0.0, // Pioggia a t-11 giorni
                avgHumidityPercent = 75.0,
                weatherCode = 1
            )
        }

        // Caso A: Notte fredda (4°C) a t-8 giorni (indice 12), notti in [t-5, t) calde (>= 12°C)
        val daysCold8DaysAgo = baseDays.mapIndexed { idx, day ->
            if (idx == 12) day.copy(minTemp = 4.0) else day
        }
        val rainRainNoChilling = MycoAlgorithms.calculateEffectiveRainfall(todayIndex, baseDays, coreEdulis)
        val rainCold8DaysAgo = MycoAlgorithms.calculateEffectiveRainfall(todayIndex, daysCold8DaysAgo, coreEdulis)

        assertEquals(
            "Una notte fredda a 8 giorni fa (fuori da 5 gg) non deve innescare chilling stasis",
            rainRainNoChilling,
            rainCold8DaysAgo,
            0.001
        )

        // Caso B: Notte fredda (4°C) a t-3 giorni (indice 17, dentro finestra 5 gg)
        val daysCold3DaysAgo = baseDays.mapIndexed { idx, day ->
            if (idx == 17) day.copy(minTemp = 4.0) else day
        }
        val rainCold3DaysAgo = MycoAlgorithms.calculateEffectiveRainfall(todayIndex, daysCold3DaysAgo, coreEdulis)

        assertNotEquals(
            "Una notte fredda a 3 giorni fa (dentro finestra 5 gg) deve innescare chilling stasis (espansione latenza)",
            rainRainNoChilling,
            rainCold3DaysAgo
        )

        // Parità con MushroomAlgorithms
        val androidBaseDays = baseDays.map { d ->
            ProcessedDay(d.dateIso, d.avgTemp.toFloat(), d.totalPrecipMm.toFloat(), d.avgHumidityPercent.toFloat(), d.weatherCode, minTemp = d.minTemp.toFloat(), maxTemp = d.maxTemp.toFloat())
        }
        val androidCold8Days = daysCold8DaysAgo.map { d ->
            ProcessedDay(d.dateIso, d.avgTemp.toFloat(), d.totalPrecipMm.toFloat(), d.avgHumidityPercent.toFloat(), d.weatherCode, minTemp = d.minTemp.toFloat(), maxTemp = d.maxTemp.toFloat())
        }
        val androidRainNormal = MushroomAlgorithms.calculateEffectiveRainfall(todayIndex, androidBaseDays, edulis)
        val androidRainCold8 = MushroomAlgorithms.calculateEffectiveRainfall(todayIndex, androidCold8Days, edulis)
        assertEquals(androidRainNormal, androidRainCold8, 0.001)
        assertEquals(rainCold8DaysAgo, androidRainCold8, 0.001)
    }

    /**
     * 7. Test Fattore PRECIPITATION: Mostra la Convoluzione Fenologica e non la Finestra Rettangolare (C03):
     * Con un apporto piovoso avvenuto 12 giorni fa (fuori da [t-10, t-2) dove il rettangolo è 0 mm),
     * il motore operativo deve presentare il fattore "Apporto ponderato per latenza" con valore > 0 mm ponderati.
     * Il motore legacy deve mostrare 0 mm con "Precipitazioni finestra recente (legacy)".
     */
    @Test
    fun testPrecipitationFactor_Matches26DayConvolutionNotRectangularSum() {
        val todayIndex = 20
        val days = (0..25).map { i ->
            github.naturewhisp.myco.core.ProcessedDay(
                dateIso = "2026-09-${(i + 1).toString().padStart(2, '0')}",
                avgTemp = 16.0,
                minTemp = 12.0,
                maxTemp = 20.0,
                totalPrecipMm = if (i == 8) 35.0 else 0.0, // Pioggia a 12 giorni fa (indice 8 rispetto a todayIndex 20)
                avgHumidityPercent = 75.0,
                weatherCode = 1,
                soilMoisture0To7 = 0.20,
                soilMoisture7To28 = 0.22
            )
        }

        val inputs = AnalysisInputs(
            days = days,
            todayIndex = todayIndex,
            speciesId = "boletus_edulis",
            habitatScore = 1.0,
            habitatDescription = "Bosco misto",
            canopyTypes = listOf("Fagus", "Castanea"),
            elevationSamples = listOf(900.0),
            monthIndex = 8,
            spunEcmRichness = 55.0,
            spunHyphalDensity = 3.5,
            missingSources = emptyList()
        )

        val engine = MycoAnalysisEngine()
        val result = engine.analyze(inputs)

        val precipFactor = result.factors.first { it.id == github.naturewhisp.myco.core.FactorId.PRECIPITATION }
        assertEquals("Apporto ponderato per latenza", precipFactor.label)
        assertTrue(
            "Il fattore fenologico a 12 giorni post-pioggia deve contenere 'mm ponderati' ed essere > 0 (attuale: ${precipFactor.formattedValue})",
            precipFactor.formattedValue.contains("mm ponderati") && !precipFactor.formattedValue.startsWith("0")
        )

        // Verifica modalità legacy: finestra rettangolare [t-10, t-2) è vuota (0 mm)
        val legacyResult = engine.analyzeLegacy(inputs)
        val legacyPrecip = legacyResult.factors.first { it.id == github.naturewhisp.myco.core.FactorId.PRECIPITATION }
        assertEquals("Precipitazioni finestra recente (legacy)", legacyPrecip.label)
        assertEquals("0 mm", legacyPrecip.formattedValue)
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
