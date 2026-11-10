package github.naturewhisp.myco.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Scenari SINTETICI (non riscontri empirici) per la memoria pluviometrica dipendente dalla specie
 * (rilievo B1 e sezione 8 della revisione del 9 novembre 2026; storico esteso a 60 giorni).
 * Le serie usano date di calendario reali che terminano il 15 ottobre 2026; lag = giorni prima del target.
 */
class SpeciesRainMemoryTest {
    private val eventMm = 40.0

    private fun monthLength(year: Int, month: Int): Int = when (month) {
        2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    /** Date ISO consecutive che terminano il 15 ottobre 2026 (incluso), in ordine cronologico. */
    private fun datesEndingOn15Oct(count: Int): List<String> {
        var year = 2026
        var month = 10
        var day = 15
        val out = ArrayList<String>(count)
        repeat(count) {
            out.add("$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}")
            day -= 1
            if (day == 0) {
                month -= 1
                if (month == 0) {
                    month = 12
                    year -= 1
                }
                day = monthLength(year, month)
            }
        }
        return out.reversed()
    }

    /** Serie di [length] giorni con target sull'ultimo; notti a 16 °C: nessuna specie subisce chilling o gelo. */
    private fun series(
        length: Int,
        rainByLag: Map<Int, Double> = emptyMap(),
        deepSoilByLag: (Int) -> Double? = { null },
    ): List<ProcessedDay> = datesEndingOn15Oct(length).mapIndexed { index, date ->
        val lag = length - 1 - index
        val rain = rainByLag[lag] ?: 0.0
        ProcessedDay(date, 18.0, rain, 88.0, if (rain > 0.0) 61 else 1, 0.30, deepSoilByLag(lag), 2.0, 16.0, 21.0)
    }

    private fun effectiveRain(species: MushroomSpecies, lag: Int, length: Int = 60): Double {
        val days = series(length, mapOf(lag to eventMm))
        return MycoAlgorithms.calculateEffectiveRainfall(days.lastIndex, days, species)
    }

    @Test
    fun rainMemoryDaysFollowsTheSpeciesLatencyPeak() {
        val expected = mapOf(
            "cantharellus_cibarius" to 42, "hydnum_repandum" to 39, "boletus_pinophilus" to 36,
            "lactarius_deliciosus" to 36, "boletus_edulis" to 33, "general" to 33, "armillaria_mellea" to 30,
            "boletus_aereus" to 27, "boletus_reticulatus" to 26, "amanita_caesarea" to 26,
            "macrolepiota_procera" to 26, "morchella_esculenta" to 26,
        )
        assertEquals(SpeciesCatalog.all.map { it.id }.toSet(), expected.keys)
        for (species in SpeciesCatalog.all) {
            assertEquals(expected.getValue(species.id), MycoAlgorithms.rainMemoryDays(species), species.id)
        }
    }

    @Test
    fun rainMemoryParametersAreRegisteredAsExpertPriors() {
        for (parameter in listOf(ParameterRegistry.PHENOLOGY_MEMORY_PEAK_MULTIPLE, ParameterRegistry.PHENOLOGY_MEMORY_MAX_DAYS)) {
            assertEquals(ParameterProvenance.EXPERT_PRIOR, parameter.provenance, parameter.key)
            assertTrue(parameter in ParameterRegistry.ALL, parameter.key)
        }
        assertEquals(3.0, ParameterRegistry.PHENOLOGY_MEMORY_PEAK_MULTIPLE.value)
        assertEquals(56, ParameterRegistry.PHENOLOGY_MEMORY_MAX_DAYS.value)
        assertEquals(26, ParameterRegistry.PHENOLOGY_MEMORY_DAYS.value)
    }

    @Test
    fun aSingleEventKeepsThePureKernelUntilTheTaperAndFadesOutAtTheSpeciesMemory() {
        for (species in SpeciesCatalog.all) {
            val memory = MycoAlgorithms.rainMemoryDays(species)
            for (lag in 1..(memory - 6)) {
                val pure = eventMm * MycoAlgorithms.phenologyKernel(lag.toDouble(), species.phenologyLatencyPeakDays, species.phenologyShapeAlpha)
                assertEquals(pure, effectiveRain(species, lag), 1e-9, "${species.id} a $lag gg")
            }
            val lastDay = effectiveRain(species, memory)
            assertTrue(lastDay <= 0.05 * eventMm, "${species.id}: $lastDay mm all'ultimo giorno di memoria ($memory gg)")
            assertEquals(0.0, effectiveRain(species, memory + 1), 1e-12, "${species.id} a ${memory + 1} gg")
        }
    }

    /**
     * Nel raccordo di estinzione [memoria - 6, memoria + 1] nessun calo giornaliero supera il 12% del volume
     * (il vecchio raccordo fisso 20–27 gg arrivava al 16.5% per Cantharellus). Per le specie con memoria oltre
     * 26 gg il limite vale sull'intera curva. Nota: per tre specie a latenza breve con memoria minima (26 gg)
     * la discesa del kernel puro, non toccata dal raccordo, supera il 12% (Macrolepiota 14.9% a 9 gg,
     * Morchella 13.0% a 10 gg, Amanita caesarea 12.2% a 11 gg): è la forma del kernel, invariata da questa modifica.
     */
    @Test
    fun noSingleDayDropOnTheTailExceedsTwelvePercentOfTheEvent() {
        for (species in SpeciesCatalog.all) {
            val memory = MycoAlgorithms.rainMemoryDays(species)
            val firstLag = if (memory > ParameterRegistry.PHENOLOGY_MEMORY_DAYS.value) 1 else memory - 6
            for (lag in firstLag..(memory + 1)) {
                val drop = effectiveRain(species, lag) - effectiveRain(species, lag + 1)
                assertTrue(drop <= 0.12 * eventMm, "${species.id}: calo di $drop mm tra $lag e ${lag + 1} gg")
            }
        }
    }

    @Test
    fun cantharellusNoLongerLosesRainThatTheKernelStillWeights() {
        // Il vecchio raccordo fisso 20–27 gg tagliava a 22 gg un kernel ancora pari a circa 0.6.
        val species = SpeciesCatalog.byId("cantharellus_cibarius")
        val pure22 = eventMm * MycoAlgorithms.phenologyKernel(22.0, species.phenologyLatencyPeakDays, species.phenologyShapeAlpha)
        assertEquals(pure22, effectiveRain(species, 22), 1e-9)
        assertTrue(effectiveRain(species, 30) > 0.0)
    }

    @Test
    fun effectiveRainIsInvariantToArchiveLengthOnceTheMemoryIsCovered() {
        val rain = mapOf(3 to 6.0, 9 to 22.0, 17 to 35.0, 25 to 12.0, 31 to 18.0, 38 to 25.0, 43 to 30.0)
        val deepSoil: (Int) -> Double? = { lag -> 0.20 + 0.002 * lag }
        for (species in SpeciesCatalog.all) {
            val short = series(45, rain, deepSoil)
            val long = series(60, rain, deepSoil)
            assertEquals(short.last().dateIso, long.last().dateIso)
            val a = MycoAlgorithms.calculateEffectiveRainfall(short.lastIndex, short, species)
            val b = MycoAlgorithms.calculateEffectiveRainfall(long.lastIndex, long, species)
            assertEquals(a, b, 1e-12, species.id)
        }
    }

    @Test
    fun deepSoilCompensationWindowStaysAtTwentySixDays() {
        // La compensazione del suolo profondo (7–28 cm) resta mediata sugli ultimi 26 giorni.
        val species = SpeciesCatalog.byId("cantharellus_cibarius")
        fun withDeepSoil(dryLags: IntRange): Double {
            val days = series(60, mapOf(10 to eventMm)) { lag -> if (lag in dryLags) 0.05 else 0.30 }
            return MycoAlgorithms.calculateEffectiveRainfall(days.lastIndex, days, species)
        }
        val reference = withDeepSoil(IntRange.EMPTY)
        assertEquals(reference, withDeepSoil(27..59), 1e-12)
        assertTrue(abs(reference - withDeepSoil(1..26)) > 1e-6)
    }

    // ---------------------------------------------------------------------------------------------
    // Motore: motivo di qualità "storico pioggia parziale"
    // ---------------------------------------------------------------------------------------------

    /** [pastDays] giorni prima del target (15 ottobre 2026) più [futureDays] giorni di prospettiva. */
    private fun engineSeries(pastDays: Int, futureDays: Int = 0): Pair<List<ProcessedDay>, Int> {
        val total = pastDays + 1 + futureDays
        val dates = datesEndingOn15Oct(pastDays + 1)
        val future = listOf("2026-10-16", "2026-10-17", "2026-10-18", "2026-10-19", "2026-10-20", "2026-10-21").take(futureDays)
        val all = dates + future
        check(all.size == total)
        val days = all.mapIndexed { index, date ->
            val lag = pastDays - index
            val rain = if (lag == 14 || lag == 20) 25.0 else 0.0
            ProcessedDay(date, 15.0, rain, 88.0, if (rain > 0.0) 61 else 1, 0.30, 0.30, 2.0, 11.0, 19.0)
        }
        return days to pastDays
    }

    private fun analyze(pastDays: Int, futureDays: Int = 0, speciesId: String = "cantharellus_cibarius"): AnalysisResult {
        val (days, todayIndex) = engineSeries(pastDays, futureDays)
        return MycoAnalysisEngine().analyze(
            AnalysisInputs(
                days = days, todayIndex = todayIndex, speciesId = speciesId, habitatScore = 1.0,
                habitatDescription = "", canopyTypes = emptyList(), elevationSamples = List(5) { 800.0 },
                monthIndex = 9, spunEcmRichness = null, spunHyphalDensity = null, missingSources = emptyList(),
                canopyCover = 0.0, forestProximityIndex = 0.0, calculationMode = "WEATHER_ONLY",
            ),
        )
    }

    private val partialHistory = "storico pioggia parziale"

    @Test
    fun aShortRainHistoryIsFlaggedWithoutChangingTheDataQualityStatus() {
        val short = analyze(28, futureDays = 6)
        val long = analyze(60, futureDays = 6)
        assertTrue(partialHistory in short.qualityReasons, short.qualityReasons.toString())
        assertTrue(partialHistory in short.missingSources, short.missingSources.toString())
        assertFalse(partialHistory in long.qualityReasons, long.qualityReasons.toString())
        assertFalse(partialHistory in long.missingSources, long.missingSources.toString())
        assertEquals(long.dataQuality, short.dataQuality)
        assertTrue(short.dailyOutlooks.isNotEmpty())
        assertTrue(short.dailyOutlooks.all { partialHistory in it.qualityReasons }, short.dailyOutlooks.map { it.qualityReasons }.toString())
        assertTrue(long.dailyOutlooks.none { partialHistory in it.qualityReasons }, long.dailyOutlooks.map { it.qualityReasons }.toString())
    }

    @Test
    fun theHistoryReasonFollowsTheExactSpeciesMemory() {
        val memory = MycoAlgorithms.rainMemoryDays(SpeciesCatalog.byId("cantharellus_cibarius"))
        assertFalse(partialHistory in analyze(memory).qualityReasons)
        assertTrue(partialHistory in analyze(memory - 1).qualityReasons)
        // Per una specie con memoria minima (26 gg) bastano 28 giorni di storico.
        assertFalse(partialHistory in analyze(28, speciesId = "macrolepiota_procera").qualityReasons)
    }
}
