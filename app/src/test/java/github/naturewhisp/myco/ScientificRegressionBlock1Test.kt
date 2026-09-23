package github.naturewhisp.myco

import github.naturewhisp.myco.model.EcologicalWeightsConfig
import github.naturewhisp.myco.model.MushroomSpecies
import github.naturewhisp.myco.model.ProcessedDay
import github.naturewhisp.myco.model.SPECIES_CATALOG
import github.naturewhisp.myco.utils.GrowthStage
import github.naturewhisp.myco.utils.MushroomAlgorithms
import github.naturewhisp.myco.utils.RainTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

/**
 * Suite di test di regressione scientifica Blocco 1 (Release v1.3).
 *
 * Copre sistematicamente i requisiti architetturali e i criteri di accettazione:
 * - REG-01: Validità termica cardinale (Tmin < Topt < Tmax).
 * - REG-02: Assenza di singolarità per Boletus edulis a T = 10.667 °C.
 * - REG-03: Continuità della risposta alle precipitazioni attorno al reset 70%.
 * - REG-04: Conservazione della massa nel clustering piogge (30 + 15 = 45 mm).
 * - REG-05: Rilevamento continuo pioggia debole persistente (5.9 mm/giorno).
 * - REG-06: Continuità C1 ai raccordi di fase fenologica.
 * - REG-13: Validità delle date di calendario e rollover mese.
 * - REG-14: Finestra previsionale di analyzeFutureTrend coerente con todayIndex.
 * - REG-15: Tolleranza a serie meteorologiche corte o incomplete.
 * - REG-16: Distinzione tra neve e precipitazione liquida.
 * - REG-20: Rispetto rigoroso dei confini [0, 100] e assenza di NaN/Infiniti.
 */
class ScientificRegressionBlock1Test {

    private val edulis: MushroomSpecies = SPECIES_CATALOG.first { it.id == "boletus_edulis" }

    // =========================================================================
    // REG-01: Validità termica cardinale
    // =========================================================================
    @Test
    fun reg01_cardinalThermalValidityAcrossAllSpecies() {
        for (species in SPECIES_CATALOG) {
            val tMin = species.toleratedTempMin.toDouble()
            val tOpt = species.optimalTemp.toDouble()
            val tMax = species.toleratedTempMax.toDouble()

            assertTrue("tMin < tOpt per ${species.id}", tMin < tOpt)
            assertTrue("tOpt < tMax per ${species.id}", tOpt < tMax)

            // Valore al picco esattamente unitario
            val vOpt = MushroomAlgorithms.ctmi(tOpt, tMin, tOpt, tMax)
            assertEquals("ctmi(tOpt) deve valere 1.0 per ${species.id}", 1.0, vOpt, 1.0e-5)

            // Valori agli estremi nulli
            assertEquals("ctmi(tMin) == 0.0 per ${species.id}", 0.0, MushroomAlgorithms.ctmi(tMin, tMin, tOpt, tMax), 1.0e-5)
            assertEquals("ctmi(tMax) == 0.0 per ${species.id}", 0.0, MushroomAlgorithms.ctmi(tMax, tMin, tOpt, tMax), 1.0e-5)

            // Esterno al range deve essere nullo
            assertEquals(0.0, MushroomAlgorithms.ctmi(tMin - 5.0, tMin, tOpt, tMax), 1.0e-5)
            assertEquals(0.0, MushroomAlgorithms.ctmi(tMax + 5.0, tMin, tOpt, tMax), 1.0e-5)

            // Sweep continuo all'interno del range: valori in [0.0, 1.0] e derivata finita
            var prevV = 0.0
            var t = tMin
            while (t <= tMax) {
                val v = MushroomAlgorithms.ctmi(t, tMin, tOpt, tMax)
                assertTrue("ctmi per ${species.id} a T=$t deve essere in [0, 1] (attuale: $v)", v in 0.0..1.0)
                assertFalse("ctmi non deve essere NaN", v.isNaN())
                assertFalse("ctmi non deve essere infinito", v.isInfinite())

                if (t > tMin) {
                    val slope = abs(v - prevV) / 0.1
                    assertTrue("Derivata prima limitata (nessuna divergenza) a T=$t (slope: $slope)", slope < 5.0)
                }
                prevV = v
                t += 0.1
            }
        }
    }

    // =========================================================================
    // REG-02: Assenza di singolarità per Boletus edulis
    // =========================================================================
    @Test
    fun reg02_boletusEdulisSingularityAbsence() {
        val tMin = 9.0
        val tOpt = 14.0
        val tMax = 24.0

        // Nel vecchio modello Rosso et al., il denominatore si annullava a T = 10.6667 °C
        val singularT = 160.0 / 15.0 // 10.666666666666666 °C
        val vSingular = MushroomAlgorithms.ctmi(singularT, tMin, tOpt, tMax)

        assertFalse("Il valore a T=$singularT non deve essere NaN", vSingular.isNaN())
        assertFalse("Il valore a T=$singularT non deve essere infinito", vSingular.isInfinite())
        assertTrue("Il valore a T=$singularT deve essere compreso in (0.0, 1.0) (attuale: $vSingular)", vSingular in 0.1..0.9)

        // Continuità bilaterale attorno a 10.667 °C
        val delta = 0.001
        val vLeft = MushroomAlgorithms.ctmi(singularT - delta, tMin, tOpt, tMax)
        val vRight = MushroomAlgorithms.ctmi(singularT + delta, tMin, tOpt, tMax)
        assertEquals("Continuità bilaterale a T=$singularT", vLeft, vRight, 0.01)
    }

    // =========================================================================
    // REG-03: Continuità della risposta alle precipitazioni attorno al reset 70%
    // =========================================================================
    @Test
    fun reg03_rainfallContinuityAround70PercentReset() {
        // Evento primario consistente 11 giorni fa (25 mm)
        // Nuova pioggia 1 giorno fa: 17.49 mm (69.96%) vs 17.51 mm (70.04%)
        val seriesA = createSeriesWithTwoStorms(stormEarlier = 25.0f, stormRecent = 17.49f)
        val seriesB = createSeriesWithTwoStorms(stormEarlier = 25.0f, stormRecent = 17.51f)

        val evalA = MushroomAlgorithms.evaluateGrowthPhase(seriesA, edulis, dayIndex = 24)
        val evalB = MushroomAlgorithms.evaluateGrowthPhase(seriesB, edulis, dayIndex = 24)

        // Il moltiplicatore non deve saltare bruscamente (|ΔΦ| <= 0.05)
        val deltaMultiplier = abs(evalA.multiplier - evalB.multiplier)
        assertTrue("La variazione di moltiplicatore fenologico deve essere <= 0.05 (attuale: $deltaMultiplier)", deltaMultiplier <= 0.05)

        val weatherScoreA = MushroomAlgorithms.calculateWeatherScore(24, seriesA, species = edulis, config = EcologicalWeightsConfig.PHENOLOGICAL)
        val weatherScoreB = MushroomAlgorithms.calculateWeatherScore(24, seriesB, species = edulis, config = EcologicalWeightsConfig.PHENOLOGICAL)

        val scoreA = MushroomAlgorithms.calculateSuitabilityScore(
            weatherScore = weatherScoreA,
            habitatScore = 0.9,
            altitudeScore = 1.0,
            seasonalityScore = 1.0,
            terrainModifier = 1.0,
            species = edulis,
            growthPhaseMultiplier = evalA.multiplier
        )
        val scoreB = MushroomAlgorithms.calculateSuitabilityScore(
            weatherScore = weatherScoreB,
            habitatScore = 0.9,
            altitudeScore = 1.0,
            seasonalityScore = 1.0,
            terrainModifier = 1.0,
            species = edulis,
            growthPhaseMultiplier = evalB.multiplier
        )

        val deltaScore = abs(scoreA - scoreB)
        assertTrue("La differenza tra i punteggi di idoneità per 0.02 mm di pioggia deve essere <= 5.0 (attuale: $deltaScore)", deltaScore <= 5.0)
    }

    // =========================================================================
    // REG-04: Conservazione della massa nel clustering piogge
    // =========================================================================
    @Test
    fun reg04_massConservationInClustering() {
        val triggersAscending = listOf(
            RainTrigger(13, 30.0f),
            RainTrigger(14, 15.0f)
        )
        val clustersAscending = MushroomAlgorithms.clusterRainEvents(triggersAscending)
        assertEquals("Due eventi a 1 giorno di distanza devono formare 1 cluster", 1, clustersAscending.size)
        assertEquals("La massa totale del cluster deve essere 45.0 mm (30 + 15)", 45.0f, clustersAscending.first().rainAmount, 0.01f)
        assertEquals("L'indice del cluster deve essere l'ultimo giorno (14)", 14, clustersAscending.first().triggerIndex)

        // Indipendenza dall'ordine di inserimento
        val triggersDescending = listOf(
            RainTrigger(14, 15.0f),
            RainTrigger(13, 30.0f)
        )
        val clustersDescending = MushroomAlgorithms.clusterRainEvents(triggersDescending)
        assertEquals(1, clustersDescending.size)
        assertEquals(45.0f, clustersDescending.first().rainAmount, 0.01f)
        assertEquals(14, clustersDescending.first().triggerIndex)
    }

    // =========================================================================
    // REG-05: Pioggia debole persistente
    // =========================================================================
    @Test
    fun reg05_persistentLightRainDoesNotDropToWaiting() {
        // Pioggia costante di 5.9 mm/giorno per 29 giorni: totale 171.1 mm
        val days59 = (0 until 29).map { i ->
            ProcessedDay(
                date = "2026-09-${String.format(Locale.US, "%02d", i + 1)}",
                avgTemp = 16.0f,
                minTemp = 12.0f,
                maxTemp = 20.0f,
                totalPrecip = 5.9f,
                avgHumidity = 85.0f,
                weatherCode = 61
            )
        }

        val eval59 = MushroomAlgorithms.evaluateGrowthPhase(days59, edulis, dayIndex = 28)
        assertNotEquals("Con 171 mm di pioggia cumulata la fase NON deve essere WAITING_FOR_RAIN", GrowthStage.WAITING_FOR_RAIN, eval59.stage)
        assertTrue("Il moltiplicatore fenologico deve essere significativamente superiore a 0.25 (attuale: ${eval59.multiplier})", eval59.multiplier > 0.40)

        // Confronto di continuità con 6.0 mm/giorno
        val days60 = (0 until 29).map { i ->
            ProcessedDay(
                date = "2026-09-${String.format(Locale.US, "%02d", i + 1)}",
                avgTemp = 16.0f,
                minTemp = 12.0f,
                maxTemp = 20.0f,
                totalPrecip = 6.0f,
                avgHumidity = 85.0f,
                weatherCode = 61
            )
        }
        val eval60 = MushroomAlgorithms.evaluateGrowthPhase(days60, edulis, dayIndex = 28)
        val deltaMult = abs(eval59.multiplier - eval60.multiplier)
        assertTrue("La differenza di moltiplicatore tra 5.9 mm e 6.0 mm deve essere <= 0.15 (attuale: $deltaMult)", deltaMult <= 0.15)
    }

    // =========================================================================
    // REG-06: Continuità ai raccordi di fase fenologica
    // =========================================================================
    @Test
    fun reg06_phenologicalPhaseTransitionsSmoothness() {
        // Singola pioggia saturante a giorno 10 (30 mm)
        val days = (0 until 35).map { i ->
            ProcessedDay(
                date = LocalDate.of(2026, 9, 1).plusDays(i.toLong()).toString(),
                avgTemp = 16.0f,
                totalPrecip = if (i == 10) 30.0f else 0.0f,
                avgHumidity = 80.0f,
                weatherCode = if (i == 10) 61 else 1
            )
        }

        var prevMultiplier = MushroomAlgorithms.evaluateGrowthPhase(days, edulis, dayIndex = 10).multiplier
        for (day in 11..34) {
            val eval = MushroomAlgorithms.evaluateGrowthPhase(days, edulis, dayIndex = day)
            val currentMultiplier = eval.multiplier
            val delta = abs(currentMultiplier - prevMultiplier)
            assertTrue(
                "Passaggio da giorno ${day - 1} a $day: salto eccessivo nel moltiplicatore ($delta, da $prevMultiplier a $currentMultiplier)",
                delta <= 0.15
            )
            prevMultiplier = currentMultiplier
        }
    }

    // =========================================================================
    // REG-13: Date valide e rollover mese
    // =========================================================================
    @Test
    fun reg13_validCalendarDatesAndMonthRollover() {
        val days = (0 until 30).map { i ->
            // Inizia il 20 Settembre 2026: deve passare naturalmente al 1 Ottobre senza '2026-09-31'
            val date = LocalDate.of(2026, 9, 20).plusDays(i.toLong()).toString()
            ProcessedDay(
                date = date,
                avgTemp = 15.0f,
                totalPrecip = 2.0f,
                avgHumidity = 75.0f,
                weatherCode = 1
            )
        }

        // Verifica che tutte le date siano valide per lo standard ISO-8601
        for (day in days) {
            val parsed = LocalDate.parse(day.date)
            assertTrue("La data deve essere valida", parsed.year == 2026)
            assertFalse("Nessuna data fittizia tipo 2026-09-31", day.date.contains("2026-09-31"))
        }

        assertEquals("2026-09-30", days[10].date)
        assertEquals("2026-10-01", days[11].date)

        val todayIndex = MushroomAlgorithms.deriveTodayIndex(days, "Europe/Rome")
        assertTrue("todayIndex deve essere un indice valido", todayIndex in days.indices)
    }

    // =========================================================================
    // REG-14: Finestra previsionale di analyzeFutureTrend coerente con todayIndex
    // =========================================================================
    @Test
    fun reg14_futureTrendAnalyzesOnlyFutureDays() {
        // Serie temporale di 35 giorni (28 giorni storici + 7 previsionali)
        // Pioggia solo al futuro (giorni 30 e 31, +2 e +3 rispetto a todayIndex = 28)
        val days = (0 until 35).map { i ->
            ProcessedDay(
                date = LocalDate.of(2026, 9, 1).plusDays(i.toLong()).toString(),
                avgTemp = 16.0f,
                totalPrecip = if (i in 30..31) 12.0f else 0.0f,
                avgHumidity = 75.0f,
                weatherCode = if (i in 30..31) 61 else 1
            )
        }

        val todayIndex = 28
        val trend = MushroomAlgorithms.analyzeFutureTrend(days, todayIndex = todayIndex)
        assertTrue(
            "Con 24 mm previsti nei giorni 30 e 31, il trend deve segnalare una nuova buttata promettente",
            trend.contains("nuova e promettente 'buttata'")
        )

        // Se invece la pioggia era passata (giorni 15..16) e il futuro (giorni 29..33) è asciutto
        val daysDryFuture = (0 until 35).map { i ->
            ProcessedDay(
                date = LocalDate.of(2026, 9, 1).plusDays(i.toLong()).toString(),
                avgTemp = 16.0f,
                totalPrecip = if (i in 15..16) 12.0f else 0.0f,
                avgHumidity = 75.0f,
                weatherCode = 1
            )
        }
        val trendDry = MushroomAlgorithms.analyzeFutureTrend(daysDryFuture, todayIndex = todayIndex)
        assertTrue(
            "Con futuro asciutto a todayIndex=28, il trend deve segnalare tempo stabile e asciutto",
            trendDry.contains("stabile e asciutto")
        )
    }

    // =========================================================================
    // REG-15: Tolleranza a serie meteorologiche corte o incomplete
    // =========================================================================
    @Test
    fun reg15_missingOrIncompleteDataHandling() {
        // Serie vuota
        val evalEmpty = MushroomAlgorithms.evaluateGrowthPhase(emptyList(), edulis, dayIndex = 0)
        assertEquals(GrowthStage.WAITING_FOR_RAIN, evalEmpty.stage)
        assertEquals(0.25, evalEmpty.multiplier, 1.0e-5)
        assertEquals("", MushroomAlgorithms.analyzeFutureTrend(emptyList(), 0))
        assertEquals(emptyList<Any>(), MushroomAlgorithms.calculateDailyOutlooks(emptyList()))

        // Serie molto corta (3 giorni)
        val shortDays = (0 until 3).map { i ->
            ProcessedDay("2026-09-${i + 1}", 16f, 5f, 80f, 1)
        }
        val evalShort = MushroomAlgorithms.evaluateGrowthPhase(shortDays, edulis, dayIndex = 2)
        assertEquals(0.25, evalShort.multiplier, 1.0e-5)
    }

    // =========================================================================
    // REG-16: Distinzione tra neve e precipitazione liquida
    // =========================================================================
    @Test
    fun reg16_snowfallDistinguishedFromLiquidPrecipitation() {
        // Giornata con 30 mm di precipitazione equivalente ma temperatura di -2 °C e WMO 71 (neve)
        val snowDay = ProcessedDay(
            date = "2026-01-15",
            avgTemp = -2.0f,
            totalPrecip = 30.0f,
            avgHumidity = 90.0f,
            weatherCode = 71
        )
        assertEquals("La precipitazione liquida deve essere zero per giornata di neve", 0.0f, snowDay.liquidPrecip, 0.001f)

        val seriesWithSnow = (0 until 28).map { i ->
            if (i == 20) snowDay else ProcessedDay("2026-01-${String.format(Locale.US, "%02d", i + 1)}", -1.0f, 0.0f, 75.0f, 0)
        }
        val eval = MushroomAlgorithms.evaluateGrowthPhase(seriesWithSnow, edulis, dayIndex = 24)
        assertEquals("Una nevicata invernale non deve innescare idratazione miceliare estiva/autunnale", GrowthStage.WAITING_FOR_RAIN, eval.stage)
    }

    // =========================================================================
    // REG-20: Rispetto rigoroso dei confini [0, 100] e assenza di NaN/Infiniti
    // =========================================================================
    @Test
    fun reg20_boundsAndFiniteValuesCheck() {
        val testScores = listOf(-50, 0, 10, 50, 70, 85, 100, 150)
        val testDoubles = listOf(-0.5, 0.0, 0.5, 1.0, 1.5)

        for (w in testScores) {
            for (h in testDoubles) {
                for (a in testDoubles) {
                    for (s in testDoubles) {
                        for (t in testDoubles) {
                            val score = MushroomAlgorithms.calculateSuitabilityScore(
                                weatherScore = w,
                                habitatScore = h,
                                altitudeScore = a,
                                seasonalityScore = s,
                                terrainModifier = t,
                                species = edulis
                            )
                            assertFalse("Il punteggio non deve essere NaN", score.isNaN())
                            assertFalse("Il punteggio non deve essere infinito", score.isInfinite())
                            assertTrue("Il punteggio di idoneità deve essere in [0.0, 100.0] (attuale: $score)", score in 0.0..100.0)

                            val prob = MushroomAlgorithms.dailyGrowthProbability(
                                weatherScore = w,
                                habitatScore = h,
                                altitudeScore = a,
                                seasonalityScore = s,
                                terrainModifier = t,
                                species = edulis
                            )
                            assertTrue("La probabilità intera deve essere in [0, 100] (attuale: $prob)", prob in 0..100)
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================
    private fun createSeriesWithTwoStorms(stormEarlier: Float, stormRecent: Float): List<ProcessedDay> {
        return (0 until 28).map { i ->
            val precip = when (i) {
                13 -> stormEarlier // 11 giorni fa rispetto a dayIndex = 24
                23 -> stormRecent  // 1 giorno fa rispetto a dayIndex = 24
                else -> 0.0f
            }
            ProcessedDay(
                date = "2026-09-${String.format(Locale.US, "%02d", i + 1)}",
                avgTemp = 16.0f,
                minTemp = 12.0f,
                maxTemp = 20.0f,
                totalPrecip = precip,
                avgHumidity = 78.0f,
                weatherCode = if (precip > 0f) 61 else 0
            )
        }
    }
}
