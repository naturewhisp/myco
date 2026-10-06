package github.naturewhisp.myco.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OperationalRepairTest {
    @Test
    fun unresolvedHoleInvalidatesItsEntireSurfaceWithoutDiscardingIndependentEvidence() {
        fun point(lat: Double, lon: Double) = GeoCoordinates(lat, lon)
        val outer = listOf(point(41.98, 11.98), point(42.02, 11.98), point(42.02, 12.02), point(41.98, 12.02), point(41.98, 11.98))
        val hole = listOf(point(41.998, 11.998), point(42.002, 11.998), point(42.002, 12.002))
        val broken = OsmHabitatElement(42.0, 12.0, true, false, false, null, surfaces = listOf(OsmSurface(outer, false), OsmSurface(hole, true)))
        val evidence = HabitatGeometry.extract(listOf(broken), point(42.0, 12.0), 1500)
        assertFalse(evidence.geometryComplete)
        assertEquals(HabitatStatus.UNKNOWN, evidence.status)
        assertEquals(0.0, evidence.forestCoverFraction)
        assertEquals(0.0, evidence.forestProximityIndex)
        assertEquals(1500.0, evidence.distanceToNearestForestMeters)
        val meadow = broken.copy(isWoodOrForest = false, isMeadowOrGrass = true, surfaces = listOf(OsmSurface(outer, false)))
        val mixed = HabitatGeometry.extract(listOf(broken, meadow), point(42.0, 12.0), 1500)
        assertFalse(mixed.geometryComplete)
        assertEquals(0.0, mixed.forestCoverFraction)
        assertEquals(1.0, mixed.meadowFraction)
        val closedHole = hole + point(41.998, 12.002) + hole.first()
        val fragments = listOf(OsmSurface(outer, false), OsmSurface(closedHole.take(3), true), OsmSurface(closedHole.drop(2).reversed(), true))
        val repaired = HabitatGeometry.extract(listOf(broken.copy(surfaces = fragments)), point(42.0, 12.0), 1500)
        assertTrue(repaired.geometryComplete)
        assertTrue(repaired.forestCoverFraction < 1.0)
        assertTrue(repaired.distanceToNearestForestMeters > 0.0)
    }
    @Test
    fun rainTriggerEnsembleHasNoResetAtFormerVolumeThresholds() {
        val species = SpeciesCatalog.byId("boletus_edulis")
        fun phase(rain: Double): GrowthPhaseEvaluation {
            val days = (1..20).map { d -> ProcessedDay("2026-10-${d.toString().padStart(2, '0')}", 18.0, when (d) { 8 -> 25.0; 18 -> rain; else -> 0.0 }, 80.0, 1, 0.25, 0.3, 0.1, 15.0, 21.0) }
            return MycoAlgorithms.evaluateGrowthPhase(days, species, 19)
        }
        for (threshold in listOf(1.0, 10.0, 17.5)) {
            val eps = 1e-5
            val a = phase(threshold - eps)
            val b = phase(threshold)
            val c = phase(threshold + eps)
            assertEquals(a.phiSoil, c.phiSoil)
            assertTrue(abs(a.multiplier - c.multiplier) < 1e-4)
            assertTrue(abs((b.multiplier - a.multiplier) / eps - (c.multiplier - b.multiplier) / eps) < 1e-4)
        }
    }
    @Test
    fun cardinalAndHabitatBoundaryDerivativesRemainContinuousForEverySpecies() {
        val eps = 1e-7
        for (species in SpeciesCatalog.all) {
            for (t in listOf(species.toleratedTempMin, species.optimalTemp, species.toleratedTempMax)) {
                fun f(x: Double) = MycoAlgorithms.ctmi(x, species.toleratedTempMin, species.optimalTemp, species.toleratedTempMax)
                val left = (f(t) - f(t - eps)) / eps
                val right = (f(t + eps) - f(t)) / eps
                assertTrue(abs(left - right) < 1e-4, "Cardinal derivative ${species.id} at $t")
            }
            for (cover in listOf(0.1, 0.2, 0.25, 0.35, 0.4, 0.6, 0.65)) {
                fun f(x: Double) = MycoAlgorithms.evaluateHabitat(HabitatEvidence(HabitatStatus.KNOWN_SUITABLE, x, 0.2, 0.0, emptySet()), species).score
                assertTrue(abs((f(cover) - f(cover - eps)) / eps - (f(cover + eps) - f(cover)) / eps) < 1e-4, "Habitat derivative ${species.id} at $cover")
            }
        }
    }
    private fun hours(date: String) = List(24) { h ->
        WeatherHour("${date}T${h.toString().padStart(2, '0')}:00", 18.0, 80.0, 1.0, 0.25, 0.30, 0.1)
    }

    @Test
    fun duplicateTransportDoesNotAddRainAndConflictingValuesInvalidateOnlyTheirVariable() {
        val source = hours("2026-10-06")
        fun map(rows: List<WeatherHour>) = WeatherAggregation.aggregate(rows, listOf(ExpectedDayHours("2026-10-06", 24)), emptyList(), "fixture").single()
        val exact = map(source + source.first())
        assertTrue(exact.coverage!!.weatherUsable)
        assertEquals(24.0, exact.totalPrecipMm)
        val conflict = map(source + source.first().copy(precipitation = 2.0))
        assertFalse(conflict.coverage!!.weatherUsable)
        assertTrue(conflict.totalPrecipMm.isNaN())
        assertEquals(18.0, conflict.avgTemp)
        assertEquals(listOf("precipitation"), conflict.coverage.conflictingVariables)
    }

    @Test
    fun hourlyMeansRequire75PercentAndTotalsRequireEveryHour() {
        val source = hours("2026-10-06")
        fun map(rows: List<WeatherHour>) = WeatherAggregation.aggregate(rows, listOf(ExpectedDayHours("2026-10-06", 24)), emptyList(), "fixture").single()
        assertFalse(map(source.take(1)).coverage!!.weatherUsable)
        val threshold = map(source.mapIndexed { i, h -> if (i < 6) h.copy(temperature = null, humidity = null) else h })
        assertTrue(threshold.coverage!!.weatherUsable)
        assertFalse(map(source.mapIndexed { i, h -> if (i < 7) h.copy(temperature = null) else h }).coverage!!.weatherUsable)
        val missingEt0 = map(source.mapIndexed { i, h -> if (i == 0) h.copy(et0 = null) else h })
        assertTrue(missingEt0.coverage!!.weatherUsable)
        assertEquals(null, missingEt0.evapotranspiration)
    }

    @Test
    fun independentQualityAndOutlooksNeverUseAnAdjacentDayAsTarget() {
        val days = (1..8).map { d -> ProcessedDay("2026-10-${d.toString().padStart(2, '0')}", 18.0, 2.0, 80.0, 1, null, null, 0.1, 15.0, 21.0) }
        val truncated = days.last().copy(coverage = WeatherCoverage(24, 1, 1, 1, 0, 0, 1, emptyList(), "fixture"))
        fun input(series: List<ProcessedDay>) = AnalysisInputs(series, 6, "boletus_edulis", 0.8, "fixture", emptyList(), emptyList(), 9, null, null, emptyList(), targetDateIso = "2026-10-07")
        val result = MycoAnalysisEngine().analyze(input(days.dropLast(1) + truncated))
        assertTrue(result.isCalculable)
        assertTrue("suolo" in result.qualityReasons)
        assertTrue("meteo lacunoso" in result.qualityReasons)
        assertFalse(result.dailyOutlooks.last().isCalculable)
        assertFalse(MycoAnalysisEngine().analyze(input(days).copy(targetDateIso = "2026-10-09")).isCalculable)
        val gap = days.filter { it.dateIso != "2026-10-05" }
        val window = EnvironmentalWindows.derive(gap, gap.indexOfFirst { it.dateIso == "2026-10-07" })
        assertEquals(2, window.soil.size)
        assertFalse(window.soil.any { it.dateIso == "2026-10-04" })
    }

    @Test
    fun habitatModifiersAreMonotoneBoundedNeutralAndC1AtBothJoins() {
        for (m in listOf(0.2, 0.8, 1.0, 1.15, 2.0)) {
            val values = (0..1000).map { MycoAlgorithms.applyHabitatBonusPenalty(it / 1000.0, m) }
            assertTrue(values.all { it in 0.0..1.0 })
            assertTrue(values.zipWithNext().all { (a, b) -> b >= a - 1e-12 })
        }
        for (base in listOf(0.0, 0.01, 0.1, 0.5, 0.9, 1.0)) assertEquals(base, MycoAlgorithms.applyHabitatBonusPenalty(base, 1.0), 1e-12)
        val base = 0.8
        for (m in listOf(1.0, 1.125, 1.375)) {
            val eps = 1e-7
            val center = MycoAlgorithms.applyHabitatBonusPenalty(base, m)
            val left = (center - MycoAlgorithms.applyHabitatBonusPenalty(base, m - eps)) / eps
            val right = (MycoAlgorithms.applyHabitatBonusPenalty(base, m + eps) - center) / eps
            assertTrue(abs(left - right) < 1e-4, "Derivative mismatch at $m")
        }
        val evidence = HabitatEvidence(HabitatStatus.KNOWN_SUITABLE, 0.6, 0.2, 0.0, setOf("fagus"))
        for (species in SpeciesCatalog.all) {
            val without = MycoAlgorithms.evaluateHabitat(evidence, species, null)
            val with = MycoAlgorithms.evaluateHabitat(evidence, species, 60.0)
            if (species.category != EcologicalCategory.ECTOMYCORRHIZAL) assertEquals(without, with)
        }
    }
}


