package github.naturewhisp.myco.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Scenari SINTETICI (non riscontri empirici) per i rilievi A2 e B1 della revisione del 9 novembre 2026:
 * - A2: le minime notturne non vanno giudicate con le soglie di temperatura media della specie;
 * - B1: la memoria pluviometrica deve estinguersi senza gradino al limite della memoria della specie (minimo 26 giorni).
 */
class NightThresholdsAndKernelTaperTest {
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
            ProcessedDay(date, avgTemp, rain, 88.0, if (rain > 0.0) 61 else 1, 0.30, null, 2.0, minTemp, maxTemp),
        )
    }

    private fun weatherOnlyIndex(days: List<ProcessedDay>, speciesId: String): Int =
        MycoAnalysisEngine().analyze(
            AnalysisInputs(
                days = days, todayIndex = days.lastIndex, speciesId = speciesId, habitatScore = 1.0,
                habitatDescription = "", canopyTypes = emptyList(), elevationSamples = List(5) { 1000.0 },
                monthIndex = 9, spunEcmRichness = null, spunHyphalDensity = null, missingSources = emptyList(),
                canopyCover = 0.0, forestProximityIndex = 0.0, calculationMode = "WEATHER_ONLY",
            ),
        ).probability

    // ---------------------------------------------------------------------------------------------
    // A2 — soglie notturne
    // ---------------------------------------------------------------------------------------------

    @Test
    fun aNightCompatibleWithAnIdealMeanAndAnUnpenalisedRangeIsNotInhibited() {
        // Media pari alla soglia ideale della specie ed escursione di 12 °C (limite oltre il quale inizia la
        // penalità DTR): la minima vale media - 6 °C. Il fattore notturno non può contraddire media ed escursione.
        for (species in SpeciesCatalog.all) {
            val nightMin = species.idealTempMin - 6.0
            assertEquals(1.0, MycoAlgorithms.nocturnalChillingInhibition(nightMin, species), 1e-12, species.id)
        }
    }

    @Test
    fun nocturnalInhibitionKeepsItsFloorAndGrowsMonotonically() {
        for (species in SpeciesCatalog.all) {
            // Sei gradi sotto la media tollerata: inibizione massima.
            assertEquals(0.3, MycoAlgorithms.nocturnalChillingInhibition(species.toleratedTempMin - 6.0, species), 1e-12, species.id)
            assertEquals(0.3, MycoAlgorithms.nocturnalChillingInhibition(-8.0, species), 1e-12, species.id)
            var previous = 0.0
            var t = -10.0
            while (t <= 25.0) {
                val value = MycoAlgorithms.nocturnalChillingInhibition(t, species)
                assertTrue(value >= previous - 1e-12, "${species.id}: non monotona a $t °C")
                assertTrue(value in 0.3..1.0)
                previous = value
                t += 0.25
            }
        }
    }

    @Test
    fun anOrdinaryAutumnNightDoesNotLowerTheIndexOfAnIdealDay() {
        // Stessa media ideale di 14 °C: notte a 8 °C (escursione 12) contro notte irrealisticamente calda a 13 °C.
        val rain = mapOf(11 to 40.0)
        for (speciesId in listOf("boletus_edulis", "general", "cantharellus_cibarius")) {
            val ordinary = weatherOnlyIndex(series(avgTemp = 14.0, minTemp = 8.0, maxTemp = 20.0, rainByLag = rain), speciesId)
            val warmNights = weatherOnlyIndex(series(avgTemp = 14.0, minTemp = 13.0, maxTemp = 15.0, rainByLag = rain), speciesId)
            assertTrue(ordinary >= warmNights - 1, "$speciesId: notte ordinaria $ordinary contro notte calda $warmNights")
        }
    }

    @Test
    fun aGenuinelyColdNightStillLowersTheIndex() {
        // Controllo: la stessa giornata con minima 6 °C sotto la media tollerata (3 °C per B. edulis) deve pesare.
        val rain = mapOf(11 to 40.0)
        val ordinary = weatherOnlyIndex(series(avgTemp = 14.0, minTemp = 8.0, maxTemp = 20.0, rainByLag = rain), "boletus_edulis")
        val coldNights = weatherOnlyIndex(series(avgTemp = 14.0, minTemp = 3.0, maxTemp = 20.0, rainByLag = rain), "boletus_edulis")
        assertTrue(coldNights <= ordinary - 10, "Notti a 3 °C: $coldNights contro $ordinary")
    }

    @Test
    fun aMildNightDoesNotStretchTheRainLatency() {
        // Una notte a 6 °C (3 °C sotto la media tollerata di B. edulis) non è un trauma da freddo.
        val species = SpeciesCatalog.byId("boletus_edulis")
        fun effectiveRain(nightMin: Double): Double {
            val days = series(rainByLag = mapOf(11 to 40.0)) { lag, day -> if (lag == 3) day.copy(minTemp = nightMin) else day }
            return MycoAlgorithms.calculateEffectiveRainfall(days.lastIndex, days, species)
        }
        assertEquals(effectiveRain(10.0), effectiveRain(6.0), 1e-9)
        // Controllo: una notte a 0 °C (9 sotto la tollerata) continua ad allungare la latenza.
        assertTrue(kotlin.math.abs(effectiveRain(10.0) - effectiveRain(0.0)) > 0.5)
    }

    // ---------------------------------------------------------------------------------------------
    // B1 — estinzione della memoria pluviometrica
    // ---------------------------------------------------------------------------------------------

    private fun effectiveRainOfSingleEvent(species: MushroomSpecies, lag: Int, mm: Double = 40.0): Double {
        // Notti a 16 °C: nessuna specie del catalogo subisce l'allungamento di latenza da freddo.
        val days = series(avgTemp = 18.0, minTemp = 16.0, maxTemp = 21.0, rainByLag = mapOf(lag to mm))
        return MycoAlgorithms.calculateEffectiveRainfall(days.lastIndex, days, species)
    }

    @Test
    fun rainMemoryFadesOutWithoutACliffAtTheMemoryLimit() {
        for (species in SpeciesCatalog.all) {
            // Memoria della specie (minimo 26 gg): all'ultimo giorno l'evento pesa meno del 5% del suo volume, poi zero.
            val memory = MycoAlgorithms.rainMemoryDays(species)
            assertTrue(effectiveRainOfSingleEvent(species, memory) <= 0.05 * 40.0, "${species.id}: ${effectiveRainOfSingleEvent(species, memory)} mm a $memory gg")
            assertEquals(0.0, effectiveRainOfSingleEvent(species, memory + 1), 1e-12, species.id)
            // Nessun calo giornaliero superiore al 20% del volume dell'evento lungo tutta la coda.
            for (lag in 12..(memory + 1)) {
                val drop = effectiveRainOfSingleEvent(species, lag) - effectiveRainOfSingleEvent(species, lag + 1)
                assertTrue(drop <= 0.20 * 40.0, "${species.id}: calo di $drop mm tra $lag e ${lag + 1} gg")
            }
        }
    }

    @Test
    fun rainMemoryIsUntouchedBeforeTheTaperStarts() {
        // Fino a memoria - 6 giorni il peso resta quello del kernel fenologico puro.
        for (species in SpeciesCatalog.all) {
            val memory = MycoAlgorithms.rainMemoryDays(species)
            for (lag in listOf(3, 8, 11, 14, 20, memory - 6)) {
                val expected = 40.0 * MycoAlgorithms.phenologyKernel(lag.toDouble(), species.phenologyLatencyPeakDays, species.phenologyShapeAlpha)
                assertEquals(expected, effectiveRainOfSingleEvent(species, lag), 1e-9, "${species.id} a $lag gg")
            }
        }
    }

    @Test
    fun theIndexDoesNotJumpWhenAnEventLeavesTheMemoryWindow() {
        for (speciesId in listOf("cantharellus_cibarius", "hydnum_repandum", "boletus_pinophilus")) {
            val memory = MycoAlgorithms.rainMemoryDays(SpeciesCatalog.byId(speciesId))
            val inside = weatherOnlyIndex(series(rainByLag = mapOf(memory to 60.0)), speciesId)
            val outside = weatherOnlyIndex(series(rainByLag = mapOf(memory + 1 to 60.0)), speciesId)
            assertTrue(inside - outside <= 3, "$speciesId: da $inside a $outside in un giorno")
        }
    }
}
