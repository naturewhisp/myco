package github.naturewhisp.myco.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Scenari SINTETICI (non riscontri empirici) per l'umidità del suolo normalizzata per cella (rilievo A3 della
 * revisione del 9 novembre 2026). Le serie usano date di calendario reali: target il 15 ottobre 2026, almeno
 * 60 giorni di storico (nessun "storico pioggia parziale") e sei giorni di prospettiva; lag = giorni prima del target.
 * La traccia di riferimento ha P90 pari a 0.355 m³/m³ su entrambi gli orizzonti (scala di cella ≈ 1.01).
 */
class SoilCellNormalizationTest {
    private val speciesId = "boletus_edulis"
    private val notNormalized = "suolo non normalizzato"
    // 125 mm nella finestra: oltre i 120 mm che sbloccano del tutto una scala < 1.
    private val defaultRain = mapOf(50 to 25.0, 38 to 25.0, 27 to 25.0, 16 to 25.0, 9 to 25.0)
    private val referenceP90 = 0.355

    private fun monthLength(year: Int, month: Int): Int = when (month) {
        2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    /** Data ISO [lag] giorni prima del 15 ottobre 2026 (lag negativo: giorni successivi). */
    private fun dateAtLag(lag: Int): String {
        var year = 2026
        var month = 10
        var day = 15
        var remaining = lag
        while (remaining > 0) {
            day -= 1
            if (day == 0) {
                month -= 1
                if (month == 0) {
                    month = 12
                    year -= 1
                }
                day = monthLength(year, month)
            }
            remaining -= 1
        }
        while (remaining < 0) {
            day += 1
            if (day > monthLength(year, month)) {
                day = 1
                month += 1
            }
            remaining += 1
        }
        return "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
    }

    /** Giorni dall'ultima pioggia (a quel giorno o prima); null se nessuna pioggia precede il giorno. */
    private fun daysSinceRain(lag: Int, rain: Map<Int, Double>): Int? =
        rain.keys.filter { it >= lag }.minOrNull()?.let { it - lag }

    private fun baseShallow(lag: Int, rain: Map<Int, Double>): Double =
        daysSinceRain(lag, rain)?.let { 0.24 + 0.12 * exp(-it / 4.0) } ?: 0.24

    private fun baseDeep(lag: Int, rain: Map<Int, Double>): Double =
        daysSinceRain(lag, rain)?.let { 0.26 + 0.08 * exp(-it / 8.0) } ?: 0.26

    /** Percentile con interpolazione lineare tra statistiche d'ordine, scritto qui in modo indipendente. */
    private fun p90(values: List<Double>): Double {
        val sorted = values.sorted()
        val h = (sorted.size - 1) * 0.90
        val lo = floor(h).toInt()
        val hi = minOf(lo + 1, sorted.lastIndex)
        return sorted[lo] + (h - lo) * (sorted[hi] - sorted[lo])
    }

    private fun shallowRef(lag: Int, rain: Map<Int, Double>): Double =
        baseShallow(lag, rain) * referenceP90 / p90((1..56).map { baseShallow(it, rain) })

    private fun deepRef(lag: Int, rain: Map<Int, Double>): Double =
        baseDeep(lag, rain) * referenceP90 / p90((1..56).map { baseDeep(it, rain) })

    /**
     * Serie di [pastDays] giorni passati, target e [futureDays] giorni di prospettiva. Il suolo della traccia di
     * riferimento è moltiplicato per [c] (tessitura della cella); [override] sostituisce i valori grezzi per lag.
     */
    private fun series(
        c: Double = 1.0,
        pastDays: Int = 60,
        futureDays: Int = 6,
        rain: Map<Int, Double> = defaultRain,
        soilFromLag: Int = Int.MAX_VALUE,
        override: (Int) -> Pair<Double, Double>? = { null },
    ): List<ProcessedDay> = (pastDays downTo -futureDays).map { lag ->
        val mm = rain[lag] ?: 0.0
        val soil = override(lag) ?: (c * shallowRef(lag, rain) to c * deepRef(lag, rain))
        val present = lag <= soilFromLag
        ProcessedDay(
            dateAtLag(lag), 15.0, mm, 88.0, if (mm > 0.0) 61 else 1,
            soil.first.takeIf { present }, soil.second.takeIf { present }, 2.0, 11.0, 19.0,
        )
    }

    private fun analyze(days: List<ProcessedDay>): AnalysisResult {
        val todayIndex = days.indexOfFirst { it.dateIso == "2026-10-15" }
        return MycoAnalysisEngine().analyze(
            AnalysisInputs(
                days = days, todayIndex = todayIndex, speciesId = speciesId, habitatScore = 1.0,
                habitatDescription = "", canopyTypes = emptyList(), elevationSamples = List(5) { 1200.0 },
                monthIndex = 9, spunEcmRichness = null, spunHyphalDensity = null, missingSources = emptyList(),
                canopyCover = 0.0, forestProximityIndex = 0.0, calculationMode = "WEATHER_ONLY",
            ),
        )
    }

    private fun soilFactor(result: AnalysisResult): Factor =
        assertNotNull(result.factors.firstOrNull { it.id == FactorId.SOIL_MOISTURE })

    private fun phiSoil(result: AnalysisResult): Double = assertNotNull(result.growthPhase).phiSoil

    // ---------------------------------------------------------------------------------------------
    // (h) Percentile e scala di cella: valori calcolati a mano
    // ---------------------------------------------------------------------------------------------

    @Test
    fun cellScaleUsesTheLinearlyInterpolatedNinetiethPercentile() {
        // 0.10, 0.11, ..., 0.39 (30 valori, ordine rimescolato): h = 29 × 0.9 = 26.1 → 0.36 + 0.1 × 0.01 = 0.361.
        val values = (0 until 30).map { 0.10 + 0.01 * ((it * 7) % 30) }
        assertEquals(0.361 / 0.35, assertNotNull(MycoAlgorithms.soilCellScale(values, 45.0)), 1e-12)
        // 40 valori costanti a 0.28: P90 = 0.28 → scala 0.80 (con almeno 120 mm la riduzione è piena).
        assertEquals(0.80, assertNotNull(MycoAlgorithms.soilCellScale(List(40) { 0.28 }, 120.0)), 1e-12)
        // 0.30 × 27 e 0.42 × 3: h = 26.1 → 0.30 + 0.1 × 0.12 = 0.312 → scala 0.8914285714.
        val step = List(27) { 0.30 } + List(3) { 0.42 }
        assertEquals(0.312 / 0.35, assertNotNull(MycoAlgorithms.soilCellScale(step, 130.0)), 1e-12)
    }

    @Test
    fun aScaleBelowOneNeedsAWetWindowWithASmoothTransition() {
        val sandy = List(40) { 0.28 }
        // Sotto 80 mm la riduzione è bloccata (scala 1.0), a 100 mm è a metà, da 120 mm è piena.
        assertEquals(1.0, assertNotNull(MycoAlgorithms.soilCellScale(sandy, 30.0)), 1e-12)
        assertEquals(1.0, assertNotNull(MycoAlgorithms.soilCellScale(sandy, 80.0)), 1e-12)
        assertEquals(0.90, assertNotNull(MycoAlgorithms.soilCellScale(sandy, 100.0)), 1e-12)
        assertEquals(0.80, assertNotNull(MycoAlgorithms.soilCellScale(sandy, 120.0)), 1e-12)
        // Raccordo continuo e monotono nella pioggia, senza gradino a 100 mm.
        var previous = assertNotNull(MycoAlgorithms.soilCellScale(sandy, 60.0))
        var rain = 60.1
        while (rain <= 140.0) {
            val current = assertNotNull(MycoAlgorithms.soilCellScale(sandy, rain))
            assertTrue(current <= previous + 1e-12 && previous - current < 0.003, "salto a $rain mm: $previous → $current")
            previous = current
            rain += 0.1
        }
        // Una scala > 1 non dipende dalla soglia di 100 mm: bastano 30 mm.
        assertEquals(0.45 / 0.35, assertNotNull(MycoAlgorithms.soilCellScale(List(40) { 0.45 }, 30.0)), 1e-12)
    }

    @Test
    fun cellScaleParametersAreRegisteredAsExpertPriors() {
        val parameters = listOf(
            ParameterRegistry.SOIL_REFERENCE_CAPACITY, ParameterRegistry.SOIL_SCALE_PERCENTILE,
            ParameterRegistry.SOIL_SCALE_WINDOW_DAYS, ParameterRegistry.SOIL_SCALE_MIN, ParameterRegistry.SOIL_SCALE_MAX,
            ParameterRegistry.SOIL_SCALE_BLEND_HALF_WIDTH, ParameterRegistry.SOIL_SCALE_MIN_DAYS,
            ParameterRegistry.SOIL_SCALE_MIN_RAIN_MM, ParameterRegistry.SOIL_SCALE_DOWN_MIN_RAIN_MM,
            ParameterRegistry.SOIL_SCALE_DOWN_RAIN_BLEND_MM,
        )
        for (parameter in parameters) {
            assertEquals(ParameterProvenance.EXPERT_PRIOR, parameter.provenance, parameter.key)
            assertTrue(parameter in ParameterRegistry.ALL, parameter.key)
        }
        assertEquals(0.35, ParameterRegistry.SOIL_REFERENCE_CAPACITY.value)
        assertEquals(0.90, ParameterRegistry.SOIL_SCALE_PERCENTILE.value)
        assertEquals(56, ParameterRegistry.SOIL_SCALE_WINDOW_DAYS.value)
        assertEquals(0.60, ParameterRegistry.SOIL_SCALE_MIN.value)
        assertEquals(1.50, ParameterRegistry.SOIL_SCALE_MAX.value)
        assertEquals(30, ParameterRegistry.SOIL_SCALE_MIN_DAYS.value)
        assertEquals(30.0, ParameterRegistry.SOIL_SCALE_MIN_RAIN_MM.value)
        assertEquals(100.0, ParameterRegistry.SOIL_SCALE_DOWN_MIN_RAIN_MM.value)
        assertEquals(20.0, ParameterRegistry.SOIL_SCALE_DOWN_RAIN_BLEND_MM.value)
    }

    @Test
    fun cellScaleIsNotComputableWithoutEnoughDaysOrRain() {
        assertNull(MycoAlgorithms.soilCellScale(List(29) { 0.30 }, 100.0))
        assertNull(MycoAlgorithms.soilCellScale(List(56) { 0.30 }, 29.9))
        assertNotNull(MycoAlgorithms.soilCellScale(List(30) { 0.30 }, 30.0))
        // I valori non finiti non contano come giorni disponibili.
        assertNull(MycoAlgorithms.soilCellScale(List(29) { 0.30 } + Double.NaN, 100.0))
    }

    // ---------------------------------------------------------------------------------------------
    // (e) Saturazione della scala in [0.60, 1.50]
    // ---------------------------------------------------------------------------------------------

    @Test
    fun cellScaleSaturatesSmoothlyAtItsLimits() {
        fun scaleFor(raw: Double): Double = assertNotNull(MycoAlgorithms.soilCellScale(List(40) { raw * 0.35 }, 150.0))
        assertEquals(0.60, scaleFor(0.40), 1e-12)
        assertEquals(0.60, scaleFor(0.20), 1e-12)
        assertEquals(1.50, scaleFor(1.60), 1e-12)
        assertEquals(1.50, scaleFor(2.00), 1e-12)
        for (raw in listOf(0.70, 0.80, 1.0, 1.2, 1.4)) assertEquals(raw, scaleFor(raw), 1e-12)
        // Continuità C1: pendenza fra 0 e 1, senza salti di pendenza fra passi adiacenti.
        val h = 0.0005
        var previousSlope: Double? = null
        var raw = 0.30
        while (raw < 1.80) {
            val a = scaleFor(raw)
            val b = scaleFor(raw + h)
            assertTrue(a in 0.60..1.50, "scala $a a $raw")
            val slope = (b - a) / h
            assertTrue(slope in -1e-9..(1.0 + 1e-6), "pendenza $slope a $raw")
            previousSlope?.let { assertTrue(abs(slope - it) < 0.02, "salto di pendenza a $raw: $it → $slope") }
            previousSlope = slope
            raw += h
        }
    }

    // ---------------------------------------------------------------------------------------------
    // (a) Invarianza rispetto alla tessitura della cella
    // ---------------------------------------------------------------------------------------------

    @Test
    fun indexIsInvariantToTheTextureOfTheCell() {
        val reference = analyze(series())
        assertFalse(notNormalized in reference.qualityReasons, reference.qualityReasons.toString())
        assertFalse("storico pioggia parziale" in reference.qualityReasons, reference.qualityReasons.toString())
        for (c in listOf(0.65, 0.8, 1.25, 1.4)) {
            val scaled = analyze(series(c))
            assertTrue(abs(scaled.probability - reference.probability) <= 1, "c=$c: ${scaled.probability} vs ${reference.probability}")
            assertEquals(phiSoil(reference), phiSoil(scaled), 1e-9, "c=$c")
            assertEquals(reference.weatherScore, scaled.weatherScore, "c=$c")
            assertEquals(soilFactor(reference).level, soilFactor(scaled).level, "c=$c")
            assertEquals(reference.dailyOutlooks.size, scaled.dailyOutlooks.size)
            reference.dailyOutlooks.zip(scaled.dailyOutlooks).forEach { (r, s) ->
                assertTrue(abs(r.probability - s.probability) <= 1, "c=$c ${s.dateIso}: ${s.probability} vs ${r.probability}")
            }
            assertEquals(reference.dataQuality, scaled.dataQuality)
            assertFalse(notNormalized in scaled.qualityReasons, "c=$c")
        }
    }

    // ---------------------------------------------------------------------------------------------
    // (b) Cella a tessitura grossolana ("tipo Abetone")
    // ---------------------------------------------------------------------------------------------

    @Test
    fun coarseCellInAWetPeriodIsNotPenalisedButARealDrySpellStillIs() {
        val wet = analyze(series(0.6))
        assertTrue(phiSoil(wet) >= 0.95, "phiSoil ${phiSoil(wet)}")
        // Ultimi tre giorni ben sotto il livello umido della cella stessa (P90 grezzo ≈ 0.21 m³/m³).
        val dry = analyze(series(0.6) { lag -> if (lag in 0..2) 0.10 to 0.12 else null })
        assertTrue(phiSoil(dry) <= 0.5, "phiSoil ${phiSoil(dry)}")
        assertTrue(dry.probability < wet.probability)
    }

    // ---------------------------------------------------------------------------------------------
    // (c) Cella a tessitura fine ("tipo pianura padana")
    // ---------------------------------------------------------------------------------------------

    @Test
    fun fineCellAtItsOwnWetLevelIsNotSaturatedButWellAboveItIs() {
        val cellP90 = 1.3 * referenceP90
        val normal = analyze(series(1.3) { lag -> if (lag in 0..2) cellP90 to cellP90 else null })
        assertEquals(FactorLevel.FAVORABLE, soilFactor(normal).level, soilFactor(normal).toString())
        val saturated = analyze(series(1.3) { lag -> if (lag in 0..2) 1.25 * cellP90 to 1.25 * cellP90 else null })
        assertNotEquals(FactorLevel.FAVORABLE, soilFactor(saturated).level, soilFactor(saturated).toString())
        assertTrue(saturated.weatherScore < normal.weatherScore, "${saturated.weatherScore} vs ${normal.weatherScore}")
    }

    // ---------------------------------------------------------------------------------------------
    // (d) Ripiego sulle soglie assolute
    // ---------------------------------------------------------------------------------------------

    private fun assertAbsoluteBehaviour(days: List<ProcessedDay>, result: AnalysisResult) {
        val species = SpeciesCatalog.byId(speciesId)
        val index = days.indexOfFirst { it.dateIso == "2026-10-15" }
        assertEquals(MycoAlgorithms.evaluateGrowthPhase(days, species, index).phiSoil, phiSoil(result), 1e-12)
        val weather = MycoAlgorithms.weatherScore(
            dayIndex = index, days = days, species = species, spunHyphalDensity = null,
            applySpunHyphalBonus = false, usePhenologicalInertia = true, canopyCover = 0.0,
        )
        assertEquals(weather, result.weatherScore)
    }

    @Test
    fun withTooFewSoilDaysTheThresholdsStayAbsolute() {
        val days = series(0.65, soilFromLag = 25)
        val result = analyze(days)
        assertTrue(notNormalized in result.qualityReasons, result.qualityReasons.toString())
        assertTrue(notNormalized in result.missingSources, result.missingSources.toString())
        assertAbsoluteBehaviour(days, result)
        assertEquals(analyze(series(0.65)).dataQuality, result.dataQuality)
    }

    @Test
    fun withTooLittleRainTheThresholdsStayAbsolute() {
        val rain = mapOf(50 to 10.0, 9 to 10.0, 60 to 40.0)
        val days = series(0.65, rain = rain)
        val result = analyze(days)
        assertTrue(notNormalized in result.qualityReasons, result.qualityReasons.toString())
        assertAbsoluteBehaviour(days, result)
        // Con 120 mm nella finestra la scala (< 1) torna attiva.
        val enough = analyze(series(0.65, rain = mapOf(50 to 60.0, 9 to 60.0, 60 to 40.0)))
        assertFalse(notNormalized in enough.qualityReasons, enough.qualityReasons.toString())
        assertFalse(notNormalized in enough.missingSources, enough.missingSources.toString())
        assertEquals(enough.dataQuality, result.dataQuality)
    }

    // ---------------------------------------------------------------------------------------------
    // Regola asimmetrica: una scala < 1 richiede una finestra piovosa
    // ---------------------------------------------------------------------------------------------

    /** Traccia «tipo Mindino»: suolo a 0.18, periodo umido 0.25–0.28 dopo la pioggia a 19 gg, poi 0.16 per 11 giorni. */
    private fun mindinoLike(rain: Map<Int, Double>): List<ProcessedDay> = series(rain = rain) { lag ->
        when {
            lag > 20 -> 0.18 to 0.22
            lag >= 11 -> (if (lag == 18 || lag == 11) 0.28 else 0.25) to 0.22
            else -> 0.16 to 0.22
        }
    }

    @Test
    fun aDrySummerWithAboutFiftyMillimetresKeepsAbsoluteThresholds() {
        val days = mindinoLike(mapOf(19 to 22.3, 12 to 25.3))
        val result = analyze(days)
        assertTrue(notNormalized in result.qualityReasons, result.qualityReasons.toString())
        assertTrue(phiSoil(result) <= 0.5, "phiSoil ${phiSoil(result)}")
        assertEquals("Orizzonti 0-7 e 7-28 cm", soilFactor(result).detail)
        assertAbsoluteBehaviour(days, result)
    }

    @Test
    fun theSameTraceWithAWetWindowLowersTheThresholds() {
        val dry = analyze(mindinoLike(mapOf(19 to 22.3, 12 to 25.3)))
        val wet = analyze(mindinoLike(mapOf(19 to 60.0, 12 to 65.0)))
        assertFalse(notNormalized in wet.qualityReasons, wet.qualityReasons.toString())
        assertTrue(soilFactor(wet).detail.contains("normalizzata sulla cella (×0.71"), soilFactor(wet).detail)
        assertTrue(phiSoil(wet) > phiSoil(dry), "${phiSoil(wet)} vs ${phiSoil(dry)}")
    }

    @Test
    fun aClayCellIsScaledUpWithFortyMillimetres() {
        val result = analyze(series(0.45 / referenceP90, rain = mapOf(30 to 20.0, 9 to 20.0)))
        assertFalse(notNormalized in result.qualityReasons, result.qualityReasons.toString())
        assertTrue(soilFactor(result).detail.endsWith("normalizzata sulla cella (×1.29)"), soilFactor(result).detail)
    }

    // ---------------------------------------------------------------------------------------------
    // (f) Invarianza rispetto alla lunghezza dell'archivio
    // ---------------------------------------------------------------------------------------------

    @Test
    fun theScaleIgnoresDaysBeforeItsFiftySixDayWindow() {
        val short = analyze(series(0.8))
        // 90 giorni, con suolo molto più umido e piogge prima della finestra di 56 giorni.
        val long = analyze(series(0.8, pastDays = 90, rain = defaultRain + mapOf(70 to 60.0, 80 to 30.0)) { lag ->
            if (lag > 56) 0.55 to 0.55 else null
        })
        assertEquals(short.probability, long.probability)
        assertEquals(short.weatherScore, long.weatherScore)
        assertEquals(phiSoil(short), phiSoil(long), 1e-12)
        assertEquals(short.factors, long.factors)
        assertEquals(short.qualityReasons, long.qualityReasons)
        assertEquals(short.dailyOutlooks, long.dailyOutlooks)
    }

    // ---------------------------------------------------------------------------------------------
    // (g) I valori mostrati restano grezzi
    // ---------------------------------------------------------------------------------------------

    private fun twoDecimals(value: Double): String = ((value * 100.0).roundToInt() / 100.0).toString()

    @Test
    fun displayedSoilValuesStayRaw() {
        val c = 0.65
        val days = series(c)
        val result = analyze(days)
        val rawShallow = days.filter { it.dateIso in setOf("2026-10-13", "2026-10-14", "2026-10-15") }
            .map { assertNotNull(it.soilMoisture0To7) }.average()
        val factor = soilFactor(result)
        assertEquals(twoDecimals(rawShallow) + " m³/m³", factor.formattedValue)
        assertTrue(assertNotNull(result.waterDiagnosis).contains(twoDecimals(rawShallow) + " m³/m³"), result.waterDiagnosis)
        assertTrue(factor.detail.startsWith("Orizzonti 0-7 e 7-28 cm • normalizzata sulla cella (×0.66"), factor.detail)
        // Con la traccia di riferimento la scala resta ≈ 1.01: la nota compare comunque, con il fattore reale.
        assertTrue(soilFactor(analyze(series())).detail.contains("(×1.01"), soilFactor(analyze(series())).detail)
        // Nel ripiego la nota non compare.
        assertEquals("Orizzonti 0-7 e 7-28 cm", soilFactor(analyze(series(c, soilFromLag = 25))).detail)
    }
}
