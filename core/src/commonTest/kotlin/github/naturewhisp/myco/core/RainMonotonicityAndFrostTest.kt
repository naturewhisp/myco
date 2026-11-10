package github.naturewhisp.myco.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Scenari SINTETICI (non riscontri empirici) per i rilievi A1 e A4 della revisione del 9 novembre 2026:
 * - A1: aggiungere pioggia non deve mai abbassare la fase fenologica né l'indice;
 * - A4: il gelo dopo l'innesco deve abbattere l'indice, non aumentarlo, e avere memoria sulla finestra di latenza.
 */
class RainMonotonicityAndFrostTest {
    private val seriesLength = 45

    /** Serie di 45 giorni di calendario reale (1 settembre – 15 ottobre 2026); lag = giorni prima del target. */
    private fun series(
        avgTemp: Double = 14.5,
        minTemp: Double = 10.0,
        maxTemp: Double = 19.0,
        rainByLag: Map<Int, Double> = emptyMap(),
        override: (Int, ProcessedDay) -> ProcessedDay = { _, day -> day },
    ): List<ProcessedDay> = (0 until seriesLength).map { index ->
        val lag = seriesLength - 1 - index
        val date = if (index < 30) {
            "2026-09-${(index + 1).toString().padStart(2, '0')}"
        } else {
            "2026-10-${(index - 29).toString().padStart(2, '0')}"
        }
        val rain = rainByLag[lag] ?: 0.0
        override(
            lag,
            ProcessedDay(date, avgTemp, rain, 88.0, if (rain > 0.0) 61 else 1, 0.30, 0.25, 2.0, minTemp, maxTemp),
        )
    }

    private fun weatherOnlyIndex(days: List<ProcessedDay>, speciesId: String, canopyCover: Double = 0.0): Int =
        MycoAnalysisEngine().analyze(
            AnalysisInputs(
                days = days, todayIndex = days.lastIndex, speciesId = speciesId, habitatScore = 1.0,
                habitatDescription = "", canopyTypes = emptyList(), elevationSamples = List(5) { 1000.0 },
                monthIndex = 9, spunEcmRichness = null, spunHyphalDensity = null, missingSources = emptyList(),
                canopyCover = canopyCover, forestProximityIndex = canopyCover, calculationMode = "WEATHER_ONLY",
            ),
        ).probability

    // ---------------------------------------------------------------------------------------------
    // A1 — monotonia rispetto alla pioggia
    // ---------------------------------------------------------------------------------------------

    @Test
    fun addingRainNeverLowersTheGrowthPhaseMultiplier() {
        for (species in SpeciesCatalog.all) {
            val triggerLag = species.phenologyLatencyPeakDays.toInt()
            val baseDays = series(rainByLag = mapOf(triggerLag to 40.0))
            val base = MycoAlgorithms.evaluateGrowthPhase(baseDays, species, baseDays.lastIndex).multiplier
            for (extraLag in 0..20) {
                if (extraLag == triggerLag) continue
                for (extraMm in listOf(2.0, 5.0, 10.0, 20.0, 40.0, 80.0)) {
                    val days = series(rainByLag = mapOf(triggerLag to 40.0, extraLag to extraMm))
                    val withExtra = MycoAlgorithms.evaluateGrowthPhase(days, species, days.lastIndex).multiplier
                    assertTrue(
                        withExtra >= base - 1e-9,
                        "${species.id}: +$extraMm mm a $extraLag gg abbassa la fase da $base a $withExtra",
                    )
                }
            }
        }
    }

    @Test
    fun addingRainNeverLowersTheSuitabilityIndex() {
        for (speciesId in listOf("boletus_edulis", "cantharellus_cibarius", "macrolepiota_procera")) {
            val triggerLag = SpeciesCatalog.byId(speciesId).phenologyLatencyPeakDays.toInt()
            val base = weatherOnlyIndex(series(rainByLag = mapOf(triggerLag to 40.0)), speciesId)
            for (extraLag in listOf(0, 1, 2, 3, 5)) {
                for (extraMm in listOf(10.0, 40.0)) {
                    val withExtra = weatherOnlyIndex(series(rainByLag = mapOf(triggerLag to 40.0, extraLag to extraMm)), speciesId)
                    assertTrue(withExtra >= base, "$speciesId: +$extraMm mm a $extraLag gg porta l'indice da $base a $withExtra")
                }
            }
        }
    }

    @Test
    fun steadyDailyRainReachesTheMaturePhase() {
        val species = SpeciesCatalog.byId("boletus_edulis")
        val days = series(rainByLag = (0 until seriesLength).associateWith { 8.0 })
        val phase = MycoAlgorithms.evaluateGrowthPhase(days, species, days.lastIndex)
        assertTrue(phase.multiplier >= 0.85, "Pioggia quotidiana regolare: fase ${phase.multiplier}")
        assertEquals(GrowthStage.ACTIVE_FRUITING, phase.stage)
    }

    @Test
    fun aSingleEventIsStillLimitedByItsOwnStage() {
        val species = SpeciesCatalog.byId("boletus_edulis")
        fun phase(lag: Int, mm: Double): GrowthPhaseEvaluation {
            val days = series(rainByLag = mapOf(lag to mm))
            return MycoAlgorithms.evaluateGrowthPhase(days, species, days.lastIndex)
        }
        // Un evento odierno, anche enorme, resta in idratazione: nessun falso positivo precoce.
        assertEquals(0.35, phase(0, 180.0).multiplier, 1e-9)
        assertEquals(GrowthStage.MYCELIAL_HYDRATION, phase(0, 180.0).stage)
        assertEquals(1.0, phase(11, 40.0).multiplier, 1e-9)
        assertEquals(11, phase(11, 40.0).daysSinceTrigger)
        // Pioggia debole isolata: sotto metà attivazione resta "in attesa".
        assertEquals(GrowthStage.WAITING_FOR_RAIN, phase(11, 3.0).stage)
    }

    // ---------------------------------------------------------------------------------------------
    // A4 — gelo dopo l'innesco
    // ---------------------------------------------------------------------------------------------

    @Test
    fun frostDamageRisesSmoothlyFromOnsetToFullDamage() {
        assertEquals(0.0, MycoAlgorithms.frostDamage(1.0), 1e-12)
        assertEquals(0.0, MycoAlgorithms.frostDamage(8.0), 1e-12)
        assertEquals(0.5, MycoAlgorithms.frostDamage(-0.5), 1e-12)
        assertEquals(1.0, MycoAlgorithms.frostDamage(-2.0), 1e-12)
        assertEquals(1.0, MycoAlgorithms.frostDamage(-9.0), 1e-12)
        val eps = 1e-6
        for (edge in listOf(1.0, -2.0)) {
            val left = (MycoAlgorithms.frostDamage(edge) - MycoAlgorithms.frostDamage(edge - eps)) / eps
            val right = (MycoAlgorithms.frostDamage(edge + eps) - MycoAlgorithms.frostDamage(edge)) / eps
            assertTrue(kotlin.math.abs(left - right) < 1e-4, "Derivata discontinua a $edge: $left vs $right")
        }
    }

    @Test
    fun aFrostNightAfterTheTriggerKeepsTheIndexAtOrBelowTwenty() {
        for (speciesId in listOf("boletus_edulis", "general", "hydnum_repandum")) {
            for (rainMm in listOf(40.0, 80.0, 180.0)) {
                for (frostLag in 1..10) {
                    fun index(nightMin: Double) = weatherOnlyIndex(
                        series(avgTemp = 12.0, minTemp = 8.0, maxTemp = 17.0, rainByLag = mapOf(11 to rainMm)) { lag, day ->
                            if (lag == frostLag) day.copy(minTemp = nightMin, avgTemp = 6.0, maxTemp = 14.0) else day
                        },
                        speciesId,
                    )
                    val frozen = index(-2.0)
                    assertTrue(frozen <= 20, "$speciesId, $rainMm mm, gelo a $frostLag gg: indice $frozen")
                    // Controllo: stessa giornata fredda ma senza gelo; l'indice deve restare chiaramente più alto.
                    val control = index(3.0)
                    assertTrue(control >= frozen + 15, "$speciesId, $rainMm mm, $frostLag gg: controllo $control vs gelo $frozen")
                }
            }
        }
    }

    @Test
    fun frostUnderCanopyIsJudgedOnTheSubCanopyMinimum() {
        // Minima in campo aperto -3.5 °C con copertura 0.8: la minima sottochioma resta sotto -2 °C.
        val days = series(avgTemp = 12.0, minTemp = 8.0, maxTemp = 17.0, rainByLag = mapOf(11 to 40.0)) { lag, day ->
            if (lag == 4) day.copy(minTemp = -3.5, avgTemp = 5.0, maxTemp = 14.0) else day
        }
        val subCanopyMin = MycoAlgorithms.applyCanopyBuffering(days, 0.8)[days.lastIndex - 4].minTemp
        assertTrue(subCanopyMin <= -2.0, "Minima sottochioma $subCanopyMin")
        assertTrue(weatherOnlyIndex(days, "boletus_edulis", canopyCover = 0.8) <= 20)
    }

    @Test
    fun frostNeverRaisesTheIndex() {
        for (frostLags in listOf(listOf(2), listOf(1, 2, 3), listOf(7, 8, 9), listOf(4, 5, 6, 7))) {
            for (nightMin in listOf(0.0, -1.0, -2.0, -5.0)) {
                fun index(min: Double) = weatherOnlyIndex(
                    series(avgTemp = 12.0, minTemp = 8.0, maxTemp = 17.0, rainByLag = mapOf(11 to 40.0)) { lag, day ->
                        if (lag in frostLags) day.copy(minTemp = min, avgTemp = 6.0, maxTemp = 14.0) else day
                    },
                    "boletus_edulis",
                )
                val mild = weatherOnlyIndex(series(avgTemp = 12.0, minTemp = 8.0, maxTemp = 17.0, rainByLag = mapOf(11 to 40.0)), "boletus_edulis")
                val frozen = index(nightMin)
                assertTrue(frozen <= mild, "Gelo $nightMin °C ai giorni $frostLags: $frozen > $mild senza gelo")
                assertTrue(frozen <= index(3.0), "Gelo $nightMin °C ai giorni $frostLags: $frozen > ${index(3.0)} con minima +3")
            }
        }
    }

    @Test
    fun aColdSnapBelowTheToleratedMeanEarnsNoThermalShockBonus() {
        // Tre giorni a media 6 °C (tollerata minima di B. edulis: 9 °C), minima +3 °C: nessun gelo.
        // Verificato in campo aperto e sottochioma (dove la media dei giorni freddi sale a circa 6.9 °C).
        for (canopy in listOf(0.0, 0.5, 0.8, 1.0)) {
            for (coldMean in listOf(5.0, 6.0, 7.5, 8.5)) {
                val mild = weatherOnlyIndex(
                    series(avgTemp = 12.0, minTemp = 8.0, maxTemp = 17.0, rainByLag = mapOf(11 to 40.0)),
                    "boletus_edulis",
                    canopy,
                )
                val coldSnap = weatherOnlyIndex(
                    series(avgTemp = 12.0, minTemp = 8.0, maxTemp = 17.0, rainByLag = mapOf(11 to 40.0)) { lag, day ->
                        if (lag in 7..9) day.copy(minTemp = 3.0, avgTemp = coldMean, maxTemp = 14.0) else day
                    },
                    "boletus_edulis",
                    canopy,
                )
                assertTrue(coldSnap <= mild, "Ondata a $coldMean °C, copertura $canopy: $coldSnap > $mild senza ondata")
            }
        }
    }

    @Test
    fun aTemperatureDropWithinToleranceStillEarnsTheThermalShockBonus() {
        // Da 19 °C a 14.5 °C di media due giorni dopo la pioggia: il calo resta entro l'intervallo tollerato.
        val flat = weatherOnlyIndex(series(rainByLag = mapOf(11 to 60.0)), "boletus_edulis")
        val drop = weatherOnlyIndex(
            series(rainByLag = mapOf(11 to 60.0)) { lag, day ->
                if (lag >= 10) day.copy(minTemp = 15.0, avgTemp = 19.0, maxTemp = 23.0) else day
            },
            "boletus_edulis",
        )
        assertTrue(drop >= flat + 5, "Calo termico entro tolleranza: $drop contro $flat senza calo")
    }

    @Test
    fun frostErasesTheRainMemoryThatPrecededItButNotLaterRain() {
        val species = SpeciesCatalog.byId("boletus_edulis")
        fun effectiveRain(frostLag: Int?, nightMin: Double): Double {
            val days = series(avgTemp = 12.0, minTemp = 8.0, maxTemp = 17.0, rainByLag = mapOf(11 to 40.0)) { lag, day ->
                if (lag == frostLag) day.copy(minTemp = nightMin, avgTemp = 6.0, maxTemp = 14.0) else day
            }
            return MycoAlgorithms.calculateEffectiveRainfall(days.lastIndex, days, species)
        }
        val untouched = effectiveRain(null, 8.0)
        assertTrue(untouched > 30.0)
        assertEquals(0.0, effectiveRain(5, -2.0), 1e-9)
        // Notte fredda a 7 giorni: fuori dalla finestra di 5 giorni che allunga la latenza, quindi il confronto
        // isola il solo danno da gelo. Controllo a +3 °C senza gelo; a -0.5 °C il danno è 0.5.
        val coldNoFrost = effectiveRain(7, 3.0)
        assertTrue(coldNoFrost > 25.0)
        assertEquals(coldNoFrost * 0.5, effectiveRain(7, -0.5), 1e-6)
        assertEquals(0.0, effectiveRain(7, -2.0), 1e-9)
        // Gelo PRIMA della pioggia: la memoria dell'evento successivo resta intatta.
        assertEquals(untouched, effectiveRain(13, -4.0), 1e-9)
        val laterDays = series(avgTemp = 12.0, minTemp = 8.0, maxTemp = 17.0, rainByLag = mapOf(11 to 40.0)) { lag, day ->
            if (lag == 13) day.copy(minTemp = -4.0, avgTemp = 6.0, maxTemp = 14.0) else day
        }
        assertEquals(1.0, MycoAlgorithms.evaluateGrowthPhase(laterDays, species, laterDays.lastIndex).multiplier, 1e-9)
    }
}
