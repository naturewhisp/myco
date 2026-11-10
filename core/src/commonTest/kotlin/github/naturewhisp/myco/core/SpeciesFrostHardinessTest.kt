package github.naturewhisp.myco.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Scenari SINTETICI (non riscontri empirici) per la resistenza al gelo per specie e per il testo di attesa
 * con piogge deboli (revisione del 10 novembre 2026, §11).
 */
class SpeciesFrostHardinessTest {
    private val seriesLength = 62

    /** 62 giorni di calendario reale (15 agosto – 15 ottobre 2026); lag = giorni prima del target. */
    private fun dateAt(index: Int): String = when {
        index < 17 -> "2026-08-${(index + 15).toString().padStart(2, '0')}"
        index < 47 -> "2026-09-${(index - 16).toString().padStart(2, '0')}"
        else -> "2026-10-${(index - 46).toString().padStart(2, '0')}"
    }

    private fun series(
        rainByLag: Map<Int, Double> = emptyMap(),
        override: (Int, ProcessedDay) -> ProcessedDay = { _, day -> day },
    ): List<ProcessedDay> = (0 until seriesLength).map { index ->
        val lag = seriesLength - 1 - index
        val rain = rainByLag[lag] ?: 0.0
        override(
            lag,
            ProcessedDay(dateAt(index), 12.0, rain, 88.0, if (rain > 0.0) 61 else 1, 0.30, 0.25, 2.0, 8.0, 17.0),
        )
    }

    /** 40 mm a 11 giorni dal target, una notte fredda (media 6 °C) al lag indicato. */
    private fun frostSeries(frostLag: Int?, nightMin: Double): List<ProcessedDay> =
        series(rainByLag = mapOf(11 to 40.0)) { lag, day ->
            if (lag == frostLag) day.copy(minTemp = nightMin, avgTemp = 6.0, maxTemp = 14.0) else day
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

    private fun effectiveRain(speciesId: String, frostLag: Int?, nightMin: Double): Double {
        val days = frostSeries(frostLag, nightMin)
        return MycoAlgorithms.calculateEffectiveRainfall(days.lastIndex, days, SpeciesCatalog.byId(speciesId))
    }

    // ---------------------------------------------------------------------------------------------
    // (a) danno da gelo per specie
    // ---------------------------------------------------------------------------------------------

    @Test
    fun catalogFrostHardinessMatchesTheExpertPriors() {
        val expected = mapOf(
            "hydnum_repandum" to 2.0, "morchella_esculenta" to 2.0, "armillaria_mellea" to 1.0,
            "lactarius_deliciosus" to 1.0, "boletus_pinophilus" to 1.0,
        )
        for (species in SpeciesCatalog.all) {
            assertEquals(expected[species.id] ?: 0.0, species.frostHardinessC, 0.0, species.id)
        }
        assertEquals(expected, ParameterRegistry.FROST_HARDINESS_BY_SPECIES.value)
        assertEquals(ParameterProvenance.EXPERT_PRIOR, ParameterRegistry.FROST_HARDINESS_BY_SPECIES.provenance)
        assertTrue(ParameterRegistry.FROST_HARDINESS_BY_SPECIES in ParameterRegistry.ALL)
    }

    @Test
    fun frostDamageShiftsWithTheSpeciesHardiness() {
        val hydnum = SpeciesCatalog.byId("hydnum_repandum")
        assertEquals(0.0, MycoAlgorithms.frostDamage(-1.0, hydnum), 1e-12)
        assertEquals(0.5, MycoAlgorithms.frostDamage(-2.5, hydnum), 1e-12)
        assertEquals(1.0, MycoAlgorithms.frostDamage(-4.0, hydnum), 1e-12)
        assertEquals(7.0 / 27.0, MycoAlgorithms.frostDamage(-2.0, hydnum), 1e-12)
        val edulis = SpeciesCatalog.byId("boletus_edulis")
        for (t in listOf(5.0, 1.0, 0.3, -0.5, -1.2, -2.0, -6.0, Double.NaN)) {
            assertEquals(MycoAlgorithms.frostDamage(t), MycoAlgorithms.frostDamage(t, edulis), 0.0, "t = $t")
        }
        // Raccordo C1 ai due estremi di ciascuna specie.
        val eps = 1e-6
        for (species in listOf(hydnum, edulis, SpeciesCatalog.byId("armillaria_mellea"))) {
            val h = species.frostHardinessC
            for (edge in listOf(1.0 - h, -2.0 - h)) {
                val left = (MycoAlgorithms.frostDamage(edge, species) - MycoAlgorithms.frostDamage(edge - eps, species)) / eps
                val right = (MycoAlgorithms.frostDamage(edge + eps, species) - MycoAlgorithms.frostDamage(edge, species)) / eps
                assertTrue(kotlin.math.abs(left) < 1e-4 && kotlin.math.abs(right) < 1e-4, "${species.id} a $edge: $left vs $right")
            }
        }
    }

    @Test
    fun frostHardinessIsClampedBetweenZeroAndFour() {
        val edulis = SpeciesCatalog.byId("boletus_edulis")
        val tooHardy = edulis.copy(frostHardinessC = 9.0)
        val negative = edulis.copy(frostHardinessC = -3.0)
        // h = 4: soglie -3 / -6 °C.
        assertEquals(0.0, MycoAlgorithms.frostDamage(-3.0, tooHardy), 1e-12)
        assertEquals(0.5, MycoAlgorithms.frostDamage(-4.5, tooHardy), 1e-12)
        assertEquals(1.0, MycoAlgorithms.frostDamage(-6.0, tooHardy), 1e-12)
        // h < 0 vale 0: nessuna specie è più sensibile delle soglie comuni.
        for (t in listOf(2.0, 0.0, -0.5, -2.0)) {
            assertEquals(MycoAlgorithms.frostDamage(t), MycoAlgorithms.frostDamage(t, negative), 0.0, "t = $t")
        }
    }

    // ---------------------------------------------------------------------------------------------
    // (c) Nocturnal Frost Gate alla minima di danno completo della specie
    // ---------------------------------------------------------------------------------------------

    @Test
    fun aFrostNightAtTheSpeciesOwnFullDamageMinimumKeepsTheIndexAtOrBelowTwenty() {
        val ids = listOf("boletus_edulis", "general", "hydnum_repandum", "boletus_pinophilus", "lactarius_deliciosus", "armillaria_mellea")
        for (speciesId in ids) {
            val species = SpeciesCatalog.byId(speciesId)
            val full = ParameterRegistry.FROST_DAMAGE_FULL_C.value - species.frostHardinessC
            val onset = ParameterRegistry.FROST_DAMAGE_ONSET_C.value - species.frostHardinessC
            for (rainMm in listOf(40.0, 80.0)) {
                for (frostLag in 1..10) {
                    fun index(nightMin: Double) = weatherOnlyIndex(
                        series(rainByLag = mapOf(11 to rainMm)) { lag, day ->
                            if (lag == frostLag) day.copy(minTemp = nightMin, avgTemp = 6.0, maxTemp = 14.0) else day
                        },
                        speciesId,
                    )
                    val frozen = index(full)
                    assertTrue(frozen <= 20, "$speciesId, $rainMm mm, gelo $full °C a $frostLag gg: indice $frozen")
                    val control = index(onset + 2.0)
                    assertTrue(control >= frozen + 15, "$speciesId, $rainMm mm, $frostLag gg: controllo $control vs gelo $frozen")
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // (b) stessa notte di gelo, specie diverse
    // ---------------------------------------------------------------------------------------------

    @Test
    fun theSameFrostNightErasesBoletusMemoryButLeavesMostOfHydnumMemory() {
        // Notte a 7 giorni: fuori dalla finestra di 5 giorni che allunga la latenza, quindi il confronto isola il gelo.
        // H. repandum (resistenza 2 °C): soglie -1 / -4 °C. A -2 °C u = (-2 + 4) / 3 = 2/3,
        // smoothstep = 3u² - 2u³ = 20/27, quindi sopravvive la frazione 20/27 (≈ 0.741) della memoria.
        val hydnumControl = effectiveRain("hydnum_repandum", 7, 3.0)
        assertTrue(hydnumControl > 25.0, "Memoria di controllo H. repandum: $hydnumControl")
        assertEquals(hydnumControl * 20.0 / 27.0, effectiveRain("hydnum_repandum", 7, -2.0), 1e-6)
        assertEquals(0.0, effectiveRain("hydnum_repandum", 7, -4.0), 1e-9)
        // B. edulis (resistenza 0): -2 °C è già danno completo.
        assertTrue(effectiveRain("boletus_edulis", 7, 3.0) > 25.0)
        assertEquals(0.0, effectiveRain("boletus_edulis", 7, -2.0), 1e-9)
    }

    // ---------------------------------------------------------------------------------------------
    // (e) testo coerente con piogge troppo deboli per attivare una fase
    // ---------------------------------------------------------------------------------------------

    private val weakRainText =
        "Fase temporale potenziale: Piogge deboli, innesco non consolidato (in attesa di precipitazioni più consistenti)."

    @Test
    fun steadyDrizzleWaitsWithACoherentTextAndNoDayCount() {
        // 2 mm al giorno: peso di evento 2 × smoothstep(0, 10, 2) ≈ 0.21 mm. Per le specie a latenza breve
        // (finestra di ricerca 2.5 × picco) il peso cumulato resta sotto metà attivazione (5 mm) e la fase è
        // "in attesa"; per quelle a latenza lunga (es. B. edulis, ~29 eventi) supera metà attivazione e la fase
        // segue l'evento rappresentativo, fuori dal perimetro di questo test.
        val days = series(rainByLag = (0 until seriesLength).associateWith { 2.0 })
        val shortLatency = listOf("macrolepiota_procera", "morchella_esculenta", "amanita_caesarea", "boletus_reticulatus", "boletus_aereus")
        for (speciesId in shortLatency) {
            val phase = MycoAlgorithms.evaluateGrowthPhase(days, SpeciesCatalog.byId(speciesId), days.lastIndex)
            assertEquals(GrowthStage.WAITING_FOR_RAIN, phase.stage, speciesId)
            assertEquals(weakRainText, phase.phaseText, speciesId)
            assertNull(phase.daysSinceTrigger, speciesId)
        }
        for (species in SpeciesCatalog.all) {
            val phase = MycoAlgorithms.evaluateGrowthPhase(days, species, days.lastIndex)
            if (phase.stage == GrowthStage.WAITING_FOR_RAIN) assertNull(phase.daysSinceTrigger, species.id)
        }
    }

    @Test
    fun anIsolatedWeakShowerWaitsWithACoherentTextAndNoDayCount() {
        val days = series(rainByLag = mapOf(11 to 3.0))
        val phase = MycoAlgorithms.evaluateGrowthPhase(days, SpeciesCatalog.byId("boletus_edulis"), days.lastIndex)
        assertEquals(GrowthStage.WAITING_FOR_RAIN, phase.stage)
        assertEquals(weakRainText, phase.phaseText)
        assertNull(phase.daysSinceTrigger)
        // Il moltiplicatore non cambia: resta quello dell'ensemble sotto metà attivazione (> livello di attesa).
        assertTrue(phase.multiplier > 0.25 && phase.multiplier < 0.35, "Moltiplicatore ${phase.multiplier}")
    }

    @Test
    fun aSingleStrongEventStillReportsItsPeakAndDayCount() {
        val days = series(rainByLag = mapOf(11 to 40.0))
        val phase = MycoAlgorithms.evaluateGrowthPhase(days, SpeciesCatalog.byId("boletus_edulis"), days.lastIndex)
        assertEquals(GrowthStage.ACTIVE_FRUITING, phase.stage)
        assertTrue(phase.phaseText.startsWith("Fase temporale potenziale: Culmine teorico"), phase.phaseText)
        assertEquals(11, phase.daysSinceTrigger)
    }

    @Test
    fun theFrostTextStillTakesPrecedence() {
        val phase = MycoAlgorithms.evaluateGrowthPhase(frostSeries(5, -2.0), SpeciesCatalog.byId("boletus_edulis"), seriesLength - 1)
        assertEquals(GrowthStage.WAITING_FOR_RAIN, phase.stage)
        assertEquals(
            "Fase temporale potenziale: Flusso interrotto dal gelo notturno (in attesa di un nuovo innesco).",
            phase.phaseText,
        )
    }

    @Test
    fun theMycelialPhaseFactorShowsTheWeakRainTextWithoutAStaleDayCount() {
        val days = series(rainByLag = mapOf(11 to 3.0))
        val factor = MycoAnalysisEngine().analyze(
            AnalysisInputs(
                days = days, todayIndex = days.lastIndex, speciesId = "boletus_edulis", habitatScore = 1.0,
                habitatDescription = "", canopyTypes = emptyList(), elevationSamples = List(5) { 1000.0 },
                monthIndex = 9, spunEcmRichness = null, spunHyphalDensity = null, missingSources = emptyList(),
                canopyCover = 0.0, forestProximityIndex = 0.0, calculationMode = "WEATHER_ONLY",
            ),
        ).factors.first { it.id == FactorId.MYCELIAL_PHASE }
        assertEquals("In attesa", factor.formattedValue)
        assertTrue("gg dall'innesco" !in factor.detail, factor.detail)
    }
}
