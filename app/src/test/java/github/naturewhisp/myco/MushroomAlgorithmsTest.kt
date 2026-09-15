package github.naturewhisp.myco

import github.naturewhisp.myco.model.DailyData
import github.naturewhisp.myco.model.EcologicalCategory
import github.naturewhisp.myco.model.EcologicalWeightsConfig
import github.naturewhisp.myco.model.FactorId
import github.naturewhisp.myco.model.FactorLevel
import github.naturewhisp.myco.model.HourlyData
import github.naturewhisp.myco.model.ProcessedDay
import github.naturewhisp.myco.model.SPECIES_CATALOG
import github.naturewhisp.myco.model.WeatherResponse
import github.naturewhisp.myco.utils.MushroomAlgorithms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class MushroomAlgorithmsTest {

    @Test
    fun testRainStatusThresholds() {
        assertEquals("Ottimale", MushroomAlgorithms.getRainStatus(41.0).label)
        assertEquals(40, MushroomAlgorithms.getRainStatus(40.0).score)
        assertEquals("Molto buona", MushroomAlgorithms.getRainStatus(26.0).label)
        assertEquals(35, MushroomAlgorithms.getRainStatus(25.0).score)
        assertEquals("Buona", MushroomAlgorithms.getRainStatus(16.0).label)
        assertEquals(25, MushroomAlgorithms.getRainStatus(15.0).score)
        assertEquals("Sufficiente", MushroomAlgorithms.getRainStatus(6.0).label)
        assertEquals(10, MushroomAlgorithms.getRainStatus(5.0).score)
        assertEquals("Scarsa", MushroomAlgorithms.getRainStatus(0.0).label)
        assertEquals(0, MushroomAlgorithms.getRainStatus(0.0).score)
    }

    @Test
    fun testTempStatusThresholds() {
        assertEquals("Ideale", MushroomAlgorithms.getTempStatus(18.0).label)
        assertEquals(30, MushroomAlgorithms.getTempStatus(14.0).score)
        assertEquals(30, MushroomAlgorithms.getTempStatus(22.0).score)

        assertEquals("Favorevole", MushroomAlgorithms.getTempStatus(13.0).label)
        assertEquals(15, MushroomAlgorithms.getTempStatus(10.0).score)
        assertEquals(15, MushroomAlgorithms.getTempStatus(23.0).score)
        assertEquals(15, MushroomAlgorithms.getTempStatus(24.9).score)

        assertEquals("Troppo freddo", MushroomAlgorithms.getTempStatus(9.9).label)
        assertEquals("Troppo caldo", MushroomAlgorithms.getTempStatus(25.1).label)
    }

    @Test
    fun testHumidityScoreThresholds() {
        assertEquals(15, MushroomAlgorithms.getHumidityScore(86.0))
        assertEquals(15, MushroomAlgorithms.getHumidityScore(85.0))
        assertEquals(10, MushroomAlgorithms.getHumidityScore(80.0))
        assertEquals(10, MushroomAlgorithms.getHumidityScore(75.0))
        assertEquals(0, MushroomAlgorithms.getHumidityScore(70.0))
    }

    @Test
    fun testAltitudeScore() {
        val low = MushroomAlgorithms.calculateAltitudeScore(150f)
        assertEquals(0.7, low.score, 0.001)

        val hill = MushroomAlgorithms.calculateAltitudeScore(350f)
        assertEquals(0.9, hill.score, 0.001)

        val ideal = MushroomAlgorithms.calculateAltitudeScore(1000f)
        assertEquals(1.0, ideal.score, 0.001)

        val mountain = MushroomAlgorithms.calculateAltitudeScore(1600f)
        assertEquals(0.9, mountain.score, 0.001)

        val high = MushroomAlgorithms.calculateAltitudeScore(2100f)
        assertEquals(0.6, high.score, 0.001)
    }

    @Test
    fun testSeasonalityScore() {
        // Month 8 = Settembre, 9 = Ottobre (Peak)
        val sept = MushroomAlgorithms.calculateSeasonalityScore(8)
        assertEquals(1.0, sept.score, 0.001)
        assertTrue(sept.text.contains("Settembre"))

        val oct = MushroomAlgorithms.calculateSeasonalityScore(9)
        assertEquals(1.0, oct.score, 0.001)

        // May = 4, June = 5 (Spring)
        val may = MushroomAlgorithms.calculateSeasonalityScore(4)
        assertEquals(0.9, may.score, 0.001)

        // July = 6, Aug = 7 (Summer)
        val july = MushroomAlgorithms.calculateSeasonalityScore(6)
        assertEquals(0.5, july.score, 0.001)

        // Off season: Jan = 0
        val jan = MushroomAlgorithms.calculateSeasonalityScore(0)
        assertEquals(0.1, jan.score, 0.001)
    }

    @Test
    fun testMoonPhaseCalculation() {
        val cal = Calendar.getInstance()
        cal.set(2024, Calendar.SEPTEMBER, 3, 12, 0, 0)
        val result = MushroomAlgorithms.getMoonPhase(cal.time)
        assertTrue(result.text.isNotEmpty())
        assertTrue(result.emoji.isNotEmpty())
    }

    @Test
    fun testSmoothScoringFunctions() {
        // Temp smooth: baseline 14-22 ideal, 10-25 tolerated
        assertEquals(1.0, MushroomAlgorithms.tempScoreSmooth(18.0), 0.001)
        assertEquals(0.0, MushroomAlgorithms.tempScoreSmooth(8.0), 0.001)
        assertEquals(0.0, MushroomAlgorithms.tempScoreSmooth(27.0), 0.001)
        assertTrue(MushroomAlgorithms.tempScoreSmooth(12.0) in 0.1..0.9)

        // Rain smooth
        assertEquals(1.0, MushroomAlgorithms.rainScoreSmooth(40.0), 0.001)
        assertEquals(0.0, MushroomAlgorithms.rainScoreSmooth(0.0), 0.001)
        assertTrue(MushroomAlgorithms.rainScoreSmooth(15.0) in 0.4..0.8)

        // Humidity smooth
        assertEquals(1.0, MushroomAlgorithms.humidityScoreSmooth(90.0), 0.001)
        assertEquals(0.0, MushroomAlgorithms.humidityScoreSmooth(40.0), 0.001)
        assertTrue(MushroomAlgorithms.humidityScoreSmooth(75.0) in 0.6..0.9)
    }

    @Test
    fun testSpeciesSpecificProfiles() {
        val pinophilus = github.naturewhisp.myco.model.SPECIES_CATALOG.first { it.id == "boletus_pinophilus" }
        // Pinophilus loves higher elevation (700-1700 ideal)
        val highAltScore = MushroomAlgorithms.calculateSpeciesAltitudeScore(1200f, pinophilus)
        assertEquals(1.0, highAltScore.score, 0.001)

        val lowAltScore = MushroomAlgorithms.calculateSpeciesAltitudeScore(100f, pinophilus)
        assertEquals(0.4, lowAltScore.score, 0.001)
    }

    @Test
    fun testDailyGrowthProbability() {
        val prob = MushroomAlgorithms.dailyGrowthProbability(
            weatherScore = 80,
            habitatScore = 1.0,
            altitudeScore = 1.0,
            seasonalityScore = 1.0
        )
        // 100 * (0.80)^1.2 ≈ 76.5
        // After cap: 70 + 22 * tanh((76.5 - 70)/22) = 76.28 -> 76
        assertEquals(76, prob)

        // 0 weather score produces 0
        assertEquals(0, MushroomAlgorithms.dailyGrowthProbability(0, 1.0, 1.0, 1.0))
        // 100 weather score produces max probability around 89
        val maxProb = MushroomAlgorithms.dailyGrowthProbability(100, 1.0, 1.0, 1.0)
        assertEquals(89, maxProb)
    }

    @Test
    fun testFactorsFormattedValuesAreCompact() {
        val moon = MushroomAlgorithms.getMoonPhase()
        val factors = MushroomAlgorithms.calculateFactors(
            avgTemp = 18.0,
            totalRain = 35.0,
            avgHumidity = 80.0,
            habitatScore = 0.95,
            habitatText = "Ideale (area boschiva)",
            elevation = 900f,
            month = 8,
            growthPhaseText = "Idratazione miceliare (Primordi in formazione)",
            moon = moon,
            slopeText = "Est (Versante riparato dai venti secchi)",
            spunEcmText = "63 specie (Ideale)",
            spunHyphalText = "5.9 m/cm³ (Attiva)"
        )

        // Assert all formattedValues are compact (< 25 chars) to prevent layout squashing
        for (f in factors) {
            assertTrue("Factor ${f.id} formattedValue '${f.formattedValue}' is too long", f.formattedValue.length <= 25)
        }
    }

    @Test
    fun testCalculateTerrainAspect_flat() {
        // [Center, North, South, East, West] all equal
        val elevations = listOf(500f, 500f, 500f, 500f, 500f)
        val result = MushroomAlgorithms.calculateTerrainAspect(elevations)
        assertTrue(result.isFlat)
        assertEquals("Pianeggiante", result.cardinalDirection)
        assertEquals("Pian", result.cardinalAbbreviation)
        assertEquals(0f, result.slopeDegrees, 0.01f)
    }

    @Test
    fun testCalculateTerrainAspect_cardinalDirections() {
        val delta = 75.0
        // 1. Downhill North (North is lower: 950 vs South 1050)
        val northSample = listOf(1000f, 950f, 1050f, 1000f, 1000f)
        val northAspect = MushroomAlgorithms.calculateTerrainAspect(northSample, delta)
        assertFalse(northAspect.isFlat)
        assertEquals("Nord", northAspect.cardinalDirection)
        assertEquals("N", northAspect.cardinalAbbreviation)
        assertTrue(northAspect.aspectDegrees < 22.5f || northAspect.aspectDegrees >= 337.5f)
        assertTrue(northAspect.slopeDegrees > 0f)

        // 2. Downhill South (South is lower: 950 vs North 1050)
        val southSample = listOf(1000f, 1050f, 950f, 1000f, 1000f)
        val southAspect = MushroomAlgorithms.calculateTerrainAspect(southSample, delta)
        assertFalse(southAspect.isFlat)
        assertEquals("Sud", southAspect.cardinalDirection)
        assertEquals("S", southAspect.cardinalAbbreviation)
        assertEquals(180f, southAspect.aspectDegrees, 1.0f)

        // 3. Downhill East (East is lower: 950 vs West 1050)
        val eastSample = listOf(1000f, 1000f, 1000f, 950f, 1050f)
        val eastAspect = MushroomAlgorithms.calculateTerrainAspect(eastSample, delta)
        assertFalse(eastAspect.isFlat)
        assertEquals("Est", eastAspect.cardinalDirection)
        assertEquals("E", eastAspect.cardinalAbbreviation)
        assertEquals(90f, eastAspect.aspectDegrees, 1.0f)

        // 4. Downhill West (West is lower: 950 vs East 1050)
        val westSample = listOf(1000f, 1000f, 1000f, 1050f, 950f)
        val westAspect = MushroomAlgorithms.calculateTerrainAspect(westSample, delta)
        assertFalse(westAspect.isFlat)
        assertEquals("Ovest", westAspect.cardinalDirection)
        assertEquals("O", westAspect.cardinalAbbreviation)
        assertEquals(270f, westAspect.aspectDegrees, 1.0f)
    }

    @Test
    fun testEvaluateTerrainAspect_seasonalLogic() {
        val generalSpecies = SPECIES_CATALOG.first { it.id == "general" }
        val delta = 75.0

        val northTerrain = MushroomAlgorithms.calculateTerrainAspect(listOf(1000f, 950f, 1050f, 1000f, 1000f), delta)
        val southTerrain = MushroomAlgorithms.calculateTerrainAspect(listOf(1000f, 1050f, 950f, 1000f, 1000f), delta)

        // Estate calda (Luglio = mese 6, temp 24°C)
        val summerNorth = MushroomAlgorithms.evaluateTerrainAspect(northTerrain, 6, 24.0, 0.7, generalSpecies)
        val summerSouth = MushroomAlgorithms.evaluateTerrainAspect(southTerrain, 6, 24.0, 0.7, generalSpecies)

        assertEquals(FactorLevel.FAVORABLE, summerNorth.level)
        assertTrue(summerNorth.detail.contains("bacìo"))
        assertEquals(FactorLevel.ADVERSE, summerSouth.level)
        assertTrue(summerSouth.detail.contains("Solatìo"))

        // Autunno freddo (Novembre = mese 10, temp 9°C)
        val autumnNorth = MushroomAlgorithms.evaluateTerrainAspect(northTerrain, 10, 9.0, 0.8, generalSpecies)
        val autumnSouth = MushroomAlgorithms.evaluateTerrainAspect(southTerrain, 10, 9.0, 0.8, generalSpecies)

        assertEquals(FactorLevel.ADVERSE, autumnNorth.level)
        assertEquals(FactorLevel.FAVORABLE, autumnSouth.level)
        assertTrue(autumnSouth.detail.contains("Solatìo"))
    }

    @Test
    fun testEvaluateTerrainAspect_thermophilicSpecies() {
        val aereus = SPECIES_CATALOG.first { it.id == "boletus_aereus" }
        val delta = 75.0
        val southTerrain = MushroomAlgorithms.calculateTerrainAspect(listOf(500f, 550f, 450f, 500f, 500f), delta)
        val northTerrain = MushroomAlgorithms.calculateTerrainAspect(listOf(500f, 450f, 550f, 500f, 500f), delta)

        // Boletus aereus ama il calore: versante sud è sempre favorito
        val southEval = MushroomAlgorithms.evaluateTerrainAspect(southTerrain, 8, 20.0, 1.0, aereus)
        assertEquals(FactorLevel.FAVORABLE, southEval.level)

        // Versante nord per aereus con temp mite (20°C) è avverso per carenza di insolazione
        val northEval = MushroomAlgorithms.evaluateTerrainAspect(northTerrain, 8, 20.0, 1.0, aereus)
        assertEquals(FactorLevel.ADVERSE, northEval.level)
    }

    @Test
    fun testFactorsWithTerrainAspectEvaluationFormattedValuesAreCompact() {
        val moon = MushroomAlgorithms.getMoonPhase()
        val delta = 75.0
        val northTerrain = MushroomAlgorithms.calculateTerrainAspect(listOf(1000f, 950f, 1050f, 1000f, 1000f), delta)
        val eval = MushroomAlgorithms.evaluateTerrainAspect(northTerrain, 6, 23.0, 0.8)

        val factors = MushroomAlgorithms.calculateFactors(
            avgTemp = 23.0,
            totalRain = 35.0,
            avgHumidity = 75.0,
            habitatScore = 0.95,
            habitatText = "Ideale (area boschiva)",
            elevation = 1000f,
            month = 6,
            growthPhaseText = "Idratazione miceliare",
            moon = moon,
            slopeText = "Nord (Versanti più freschi)",
            spunEcmText = "60 specie (Ideale)",
            spunHyphalText = "5.0 m/cm³ (Attiva)",
            terrainEvaluation = eval
        )

        val slopeFactor = factors.first { it.id == FactorId.SLOPE }
        assertEquals(FactorLevel.FAVORABLE, slopeFactor.level)
        assertTrue("Formatted value '${slopeFactor.formattedValue}' must be compact", slopeFactor.formattedValue.length <= 15)

        for (f in factors) {
            assertTrue("Factor ${f.id} formattedValue '${f.formattedValue}' is too long", f.formattedValue.length <= 25)
        }
    }

    @Test
    fun testHeatmapColorPaletteAndDynamicAlpha() {
        // Probabilità inferiore a 16: completamente trasparente
        assertEquals(0, github.naturewhisp.myco.utils.HeatmapGenerator.getHeatmapColor(10))
        assertEquals(0, github.naturewhisp.myco.utils.HeatmapGenerator.getHeatmapColor(15))

        // Feathering di ingresso morbido (16..19)
        val c18 = github.naturewhisp.myco.utils.HeatmapGenerator.getHeatmapColor(18)
        val alpha18 = (c18 ushr 24) and 0xFF
        assertTrue("Alpha a prob 18 deve essere > 0 per morbidezza bordo", alpha18 > 0)
        assertTrue("Alpha a prob 18 deve essere inferiore ad alpha base 150", alpha18 < 150)

        // Verifica opacità progressiva
        val c25 = github.naturewhisp.myco.utils.HeatmapGenerator.getHeatmapColor(25)
        val c50 = github.naturewhisp.myco.utils.HeatmapGenerator.getHeatmapColor(50)
        val c68 = github.naturewhisp.myco.utils.HeatmapGenerator.getHeatmapColor(68)
        val c85 = github.naturewhisp.myco.utils.HeatmapGenerator.getHeatmapColor(85)

        val a25 = (c25 ushr 24) and 0xFF
        val a50 = (c50 ushr 24) and 0xFF
        val a68 = (c68 ushr 24) and 0xFF
        val a85 = (c85 ushr 24) and 0xFF

        assertTrue("Alpha deve crescere con la probabilità (a25=$a25, a50=$a50)", a25 <= a50)
        assertTrue("Alpha deve crescere con la probabilità (a50=$a50, a68=$a68)", a50 <= a68)
        assertTrue("Alpha deve crescere con la probabilità (a68=$a68, a85=$a85)", a68 <= a85)
        assertTrue("Alpha a 85 deve raggiungere opacità equilibrata (>= 175)", a85 >= 175)

        // Distinzione cromatica netta tra le 4 classi (RGB non coincidenti)
        val r25 = (c25 ushr 16) and 0xFF
        val g25 = (c25 ushr 8) and 0xFF
        val b25 = c25 and 0xFF

        val r50 = (c50 ushr 16) and 0xFF
        val g50 = (c50 ushr 8) and 0xFF
        val b50 = c50 and 0xFF

        val r68 = (c68 ushr 16) and 0xFF
        val g68 = (c68 ushr 8) and 0xFF
        val b68 = c68 and 0xFF

        val r85 = (c85 ushr 16) and 0xFF
        val g85 = (c85 ushr 8) and 0xFF
        val b85 = c85 and 0xFF

        // Innesco (25) è verde: G dominante su R
        assertTrue("Innesco deve avere componente verde dominante", g25 > r25)
        // Moderato (50) è ambra/oro: R dominante, G sostenuto, B basso
        assertTrue("Moderato deve avere R > B", r50 > b50 && g50 > b50)
        // Propizio (68) è terracotta: R più alto di G
        assertTrue("Propizio deve avere R nettamente superiore a G", r68 > g68)
        // Culmine (85) è cremisi profondo: R dominante, G basso
        assertTrue("Culmine deve essere rosso granato con G basso", r85 > g85 && g85 < 50)
    }

    @Test
    fun testSoilMoistureScoreSmooth_NullInputs() {
        val score = MushroomAlgorithms.soilMoistureScoreSmooth(null, null, null)
        assertEquals(1.0, score, 0.001)
    }

    @Test
    fun testSoilMoistureScoreSmooth_OptimalConditions() {
        val score = MushroomAlgorithms.soilMoistureScoreSmooth(0.30, 0.28, 1.5)
        assertEquals(1.0, score, 0.001)
    }

    @Test
    fun testSoilMoistureScoreSmooth_DesiccatedConditions() {
        val score = MushroomAlgorithms.soilMoistureScoreSmooth(0.08, 0.10, 2.0)
        assertTrue("Suolo disidratato deve produrre punteggio basso", score <= 0.25)
    }

    @Test
    fun testSoilMoistureScoreSmooth_WaterloggedPenalty() {
        val score = MushroomAlgorithms.soilMoistureScoreSmooth(0.50, 0.46, 1.0)
        assertTrue("Suolo saturo/asfittico deve essere penalizzato", score < 0.70)
        assertTrue("Suolo in asfissia idrica deve scendere sotto 0.35", score <= 0.35)
    }

    @Test
    fun testSoilMoistureScoreSmooth_AcuteWaterloggingAnoxia() {
        val score = MushroomAlgorithms.soilMoistureScoreSmooth(0.55, 0.52, 0.5)
        assertTrue("Saturazione totale del suolo (>0.50 m³/m³) deve deprimere il punteggio sotto 0.20", score <= 0.20)
    }

    @Test
    fun testSoilMoistureScoreSmooth_EvapotranspirationImpact() {
        val scoreLowET0 = MushroomAlgorithms.soilMoistureScoreSmooth(0.20, 0.22, 1.0)
        val scoreHighET0 = MushroomAlgorithms.soilMoistureScoreSmooth(0.20, 0.22, 5.0)
        assertTrue("Forte evapotraspirazione deve ridurre l'idratazione efficace", scoreHighET0 < scoreLowET0)
    }

    @Test
    fun testProcessWeatherData_AggregatesSoilMoistureAndET0() {
        val hours = listOf("2026-09-10T00:00", "2026-09-10T12:00")
        val hourly = HourlyData(
            time = hours,
            temperature2m = listOf(16.0f, 20.0f),
            relativeHumidity2m = listOf(80.0f, 60.0f),
            precipitation = listOf(1.0f, 3.0f),
            soilMoisture0To7cm = listOf(0.28f, 0.32f),
            soilMoisture7To28cm = listOf(0.24f, 0.26f),
            evapotranspiration = listOf(0.8f, 1.4f)
        )
        val daily = DailyData(time = listOf("2026-09-10"), weatherCode = listOf(2))
        val response = WeatherResponse(elevation = 800f, timezone = "Europe/Rome", hourly = hourly, daily = daily)

        val processed = MushroomAlgorithms.processWeatherData(response)
        assertEquals(1, processed.size)
        val day = processed[0]
        assertEquals("2026-09-10", day.date)
        assertEquals(18.0f, day.avgTemp, 0.01f)
        assertEquals(4.0f, day.totalPrecip, 0.01f)
        assertEquals(70.0f, day.avgHumidity, 0.01f)
        assertNotNull(day.avgSoilMoisture0To7cm)
        assertEquals(0.30f, day.avgSoilMoisture0To7cm!!, 0.01f)
        assertNotNull(day.avgSoilMoisture7To28cm)
        assertEquals(0.25f, day.avgSoilMoisture7To28cm!!, 0.01f)
        assertNotNull(day.totalEvapotranspiration)
        assertEquals(2.2f, day.totalEvapotranspiration!!, 0.01f)
    }

    @Test
    fun testCalculateFactors_IncludesSoilMoistureFactor() {
        val moon = MushroomAlgorithms.getMoonPhase()
        val factors = MushroomAlgorithms.calculateFactors(
            avgTemp = 18.0,
            totalRain = 45.0,
            avgHumidity = 80.0,
            habitatScore = 1.0,
            habitatText = "Bosco misto",
            elevation = 900f,
            month = 9,
            growthPhaseText = "Buttata attiva",
            moon = moon,
            slopeText = "Solatìo",
            avgSoilMoisture0To7 = 0.32f,
            avgSoilMoisture7To28 = 0.28f,
            totalEvapotranspiration = 2.1f
        )

        val soilFactor = factors.firstOrNull { it.id == FactorId.SOIL_MOISTURE }
        assertNotNull("Deve includere il fattore SOIL_MOISTURE", soilFactor)
        assertEquals("Idratazione suolo", soilFactor!!.label)
        assertEquals("0,32 m³/m³", soilFactor.formattedValue)
        assertEquals(FactorLevel.FAVORABLE, soilFactor.level)
        assertTrue(soilFactor.detail.contains("Orizzonte primordi 0-7 cm"))
        assertTrue(soilFactor.detail.contains("Radici 0,28"))
        assertTrue(soilFactor.detail.contains("ET0 2,1 mm"))
    }

    @Test
    fun testHaversineDistanceKm_CalculatesAccurateDistance() {
        // Roma (41.8902, 12.4922) a Milano (45.4642, 9.1900) ~ 477 km
        val distanceRomeMilan = MushroomAlgorithms.haversineDistanceKm(41.8902, 12.4922, 45.4642, 9.1900)
        assertEquals(477.0, distanceRomeMilan, 10.0)

        // Distanza dal punto a se stesso = 0
        val distanceSame = MushroomAlgorithms.haversineDistanceKm(45.0, 7.0, 45.0, 7.0)
        assertEquals(0.0, distanceSame, 0.001)

        // Courmayeur (45.7969, 6.9697) a Chamonix (45.9237, 6.8694) ~ 16 km
        val distanceCourmCham = MushroomAlgorithms.haversineDistanceKm(45.7969, 6.9697, 45.9237, 6.8694)
        assertEquals(16.0, distanceCourmCham, 3.0)
    }

    @Test
    fun testApplyCanopyBuffering_SummerCoolingAndNightWarming() {
        val hotDay = ProcessedDay(
            date = "2026-08-15",
            avgTemp = 18.0f,
            totalPrecip = 0.0f,
            avgHumidity = 50.0f,
            weatherCode = 1,
            minTemp = 6.0f,
            maxTemp = 30.0f
        )
        val buffered = MushroomAlgorithms.applyCanopyBuffering(hotDay, canopyCover = 0.85)

        assertTrue("Massima estiva deve essere attenuata sotto la chioma", buffered.maxTemp < 28.0f)
        assertTrue("Minima notturna deve essere protetta dall'isolamento radiativo", buffered.minTemp > 7.0f)
        val openDtr = hotDay.maxTemp - hotDay.minTemp
        val bufferedDtr = buffered.maxTemp - buffered.minTemp
        assertTrue("Escursione termica nel sottobosco deve essere compressa", bufferedDtr < openDtr - 3.0f)
        assertTrue("Umidità relativa sub-canopy deve essere incrementata", buffered.avgHumidity > hotDay.avgHumidity)
    }

    @Test
    fun testApplyCanopyBuffering_ThroughfallInterception() {
        val drizzleDay = ProcessedDay(
            date = "2026-09-01",
            avgTemp = 16.0f,
            totalPrecip = 2.0f,
            avgHumidity = 80.0f,
            weatherCode = 51
        )
        val heavyRainDay = ProcessedDay(
            date = "2026-09-02",
            avgTemp = 15.0f,
            totalPrecip = 25.0f,
            avgHumidity = 95.0f,
            weatherCode = 63
        )

        val bufferedDrizzle = MushroomAlgorithms.applyCanopyBuffering(drizzleDay, canopyCover = 0.85)
        val bufferedHeavy = MushroomAlgorithms.applyCanopyBuffering(heavyRainDay, canopyCover = 0.85)

        // Pioggia lieve: forte intercettazione (> 25% persa nella chioma)
        assertTrue("Pioggia lieve deve subire sensibile intercettazione", bufferedDrizzle.totalPrecip < 1.6f)
        assertTrue("Throughfall deve rimanere positivo", bufferedDrizzle.totalPrecip > 1.2f)

        // Pioggia intensa: saturazione chioma e penetrazione prevalente (> 80% al suolo)
        assertTrue("Pioggia intensa deve penetrare al suolo oltre 21 mm", bufferedHeavy.totalPrecip > 21.0f)
    }

    @Test
    fun testApplyCanopyBuffering_OpenFieldZeroOffset() {
        val rawDay = ProcessedDay(
            date = "2026-09-05",
            avgTemp = 19.5f,
            totalPrecip = 12.0f,
            avgHumidity = 65.0f,
            weatherCode = 3,
            minTemp = 11.0f,
            maxTemp = 26.0f
        )
        val openField = MushroomAlgorithms.applyCanopyBuffering(rawDay, canopyCover = 0.0)

        assertEquals(rawDay.avgTemp, openField.avgTemp, 0.001f)
        assertEquals(rawDay.minTemp, openField.minTemp, 0.001f)
        assertEquals(rawDay.maxTemp, openField.maxTemp, 0.001f)
        assertEquals(rawDay.totalPrecip, openField.totalPrecip, 0.001f)
        assertEquals(rawDay.avgHumidity, openField.avgHumidity, 0.001f)
    }

    @Test
    fun testCalculateWeatherScore_CanopyBufferingDampensExtremeDtr() {
        // Genera serie 21 giorni con giorni stabili e giorno 14 con escursione aperta estrema (DTR = 16°C: 24°C max, 8°C min)
        val days = (0..20).map { i ->
            ProcessedDay(
                date = "2026-09-${String.format(java.util.Locale.US, "%02d", i + 1)}",
                avgTemp = 16.0f,
                totalPrecip = if (i == 4) 20.0f else 0.0f,
                avgHumidity = 75.0f,
                weatherCode = 1,
                minTemp = if (i == 14) 8.0f else 12.0f,
                maxTemp = if (i == 14) 24.0f else 20.0f
            )
        }

        // Punteggio campo aperto (nessuna copertura, DTR = 16°C > 15°C attiva penalità dtr 0.8)
        val openScore = MushroomAlgorithms.calculateWeatherScore(
            dayIndex = 14,
            allData = days,
            canopyCover = 0.0
        )

        // Punteggio sotto chioma forestale densa (C = 0.85 restringe DTR < 15°C, rimuovendo la penalità da cielo aperto)
        val forestScore = MushroomAlgorithms.calculateWeatherScore(
            dayIndex = 14,
            allData = days,
            canopyCover = 0.85
        )

        assertTrue("La chioma boschiva deve proteggere dal falso stress termico notturno di campo aperto", forestScore >= openScore)
    }

    @Test
    fun testCtmi_CardinalTemperaturesAndPeak() {
        val tMin = 9.0
        val tOpt = 14.0
        val tMax = 24.0

        // Condizioni ai limiti cardinali
        assertEquals(0.0, MushroomAlgorithms.ctmi(8.0, tMin, tOpt, tMax), 0.0001)
        assertEquals(0.0, MushroomAlgorithms.ctmi(9.0, tMin, tOpt, tMax), 0.0001)
        assertEquals(0.0, MushroomAlgorithms.ctmi(24.0, tMin, tOpt, tMax), 0.0001)
        assertEquals(0.0, MushroomAlgorithms.ctmi(26.0, tMin, tOpt, tMax), 0.0001)

        // Picco esatto alla temperatura cardinale ottimale
        assertEquals(1.0, MushroomAlgorithms.ctmi(tOpt, tMin, tOpt, tMax), 0.0001)

        // Risposta continua asimmetrica per temperature intermedie
        val coldScore = MushroomAlgorithms.ctmi(11.0, tMin, tOpt, tMax)
        val warmScore = MushroomAlgorithms.ctmi(20.0, tMin, tOpt, tMax)
        assertTrue("Cold score must be in (0, 1): was $coldScore", coldScore in 0.01..0.99)
        assertTrue("Warm score must be in (0, 1): was $warmScore", warmScore in 0.01..0.99)
        assertFalse("ctmi must not produce NaN", coldScore.isNaN())
    }

    @Test
    fun testTempScoreSmooth_PlateauAndColdWarmTransitions() {
        val edulis = SPECIES_CATALOG.first { it.id == "boletus_edulis" }
        // Ideal: 13..20, Tolerated: 9..24
        assertEquals(1.0, MushroomAlgorithms.tempScoreSmooth(13.0, edulis), 0.0001)
        assertEquals(1.0, MushroomAlgorithms.tempScoreSmooth(16.5, edulis), 0.0001)
        assertEquals(1.0, MushroomAlgorithms.tempScoreSmooth(20.0, edulis), 0.0001)

        assertEquals(0.0, MushroomAlgorithms.tempScoreSmooth(8.5, edulis), 0.0001)
        assertEquals(0.0, MushroomAlgorithms.tempScoreSmooth(25.0, edulis), 0.0001)

        val transCold = MushroomAlgorithms.tempScoreSmooth(11.0, edulis)
        val transWarm = MushroomAlgorithms.tempScoreSmooth(22.0, edulis)
        assertTrue("Ascending cold flank in (0, 1): was $transCold", transCold in 0.1..0.9)
        assertTrue("Descending warm flank in (0, 1): was $transWarm", transWarm in 0.1..0.9)
    }

    @Test
    fun testPhenologicalRainfall_ExtendedWindow26Days() {
        val edulis = SPECIES_CATALOG.first { it.id == "boletus_edulis" }
        // Genera 29 giorni di serie meteo (giorni 0..28, oggi = 28)
        // Pioggia intensa (45 mm) caduta 26 giorni prima (giorno 2: tau = 28 - 2 = 26 gg)
        val days = (0..28).map { i ->
            ProcessedDay(
                date = "2026-09-${String.format(java.util.Locale.US, "%02d", (i % 30) + 1)}",
                avgTemp = 15.0f,
                totalPrecip = if (i == 2) 45.0f else 0.0f,
                avgHumidity = 70.0f,
                weatherCode = 1,
                avgSoilMoisture7To28cm = 0.28f
            )
        }

        val effectiveRain = MushroomAlgorithms.calculateEffectiveRainfall(28, days, edulis)
        assertTrue("Pioggia di 26 giorni prima deve essere catturata dalla finestra estesa P_d-26", effectiveRain > 0.0)
    }

    @Test
    fun testMediumTermThermalConditioning_Td20() {
        val edulis = SPECIES_CATALOG.first { it.id == "boletus_edulis" }
        // Serie 29 giorni:
        // Entrambe le serie hanno identica pioggia (25 mm a tau = 11, giorno 17) e identica T recente ultimi 5 giorni (16°C)
        val optimalDays = (0..28).map { i ->
            ProcessedDay(
                date = "2026-09-${String.format(java.util.Locale.US, "%02d", (i % 30) + 1)}",
                avgTemp = if (i in 8..23) 14.0f else 16.0f, // 14°C ottimale Brejon Lamartinière nel medio termine
                totalPrecip = if (i == 17) 25.0f else 0.0f,
                avgHumidity = 75.0f,
                weatherCode = 1,
                minTemp = 12.0f,
                maxTemp = 18.0f
            )
        }

        val heatwaveDays = (0..28).map { i ->
            ProcessedDay(
                date = "2026-09-${String.format(java.util.Locale.US, "%02d", (i % 30) + 1)}",
                avgTemp = if (i in 8..23) 32.0f else 16.0f, // Caldo torrido estremo 32°C nelle settimane precedenti
                totalPrecip = if (i == 17) 25.0f else 0.0f,
                avgHumidity = 75.0f,
                weatherCode = 1,
                minTemp = 12.0f,
                maxTemp = 18.0f
            )
        }

        val scoreOptimal = MushroomAlgorithms.calculateWeatherScore(
            dayIndex = 28,
            allData = optimalDays,
            species = edulis,
            config = EcologicalWeightsConfig.PHENOLOGICAL
        )

        val scoreHeatwave = MushroomAlgorithms.calculateWeatherScore(
            dayIndex = 28,
            allData = heatwaveDays,
            species = edulis,
            config = EcologicalWeightsConfig.PHENOLOGICAL
        )

        assertTrue(
            "Il condizionamento termico T_d-20 ottimale (14°C) deve produrre un punteggio superiore a un pregresso torrido (32°C): $scoreOptimal vs $scoreHeatwave",
            scoreOptimal > scoreHeatwave
        )
    }

    @Test
    fun testHurdleOccurrenceProbability_GatingAndContinuity() {
        val edulis = SPECIES_CATALOG.first { it.id == "boletus_edulis" }
        val macrolepiota = SPECIES_CATALOG.first { it.id == "macrolepiota_procera" }

        // 1. Stazione completamente incompatibile (H = 0, A = 0) -> p_hurdle = 0.0
        assertEquals(0.0, MushroomAlgorithms.hurdleOccurrenceProbability(0.0, 0.0, edulis), 0.001)

        // 2. Specie ectomicorrizica (edulis): habitat scarso (es. campo aperto H = 0.10) -> penalità severa
        val pLowHabitat = MushroomAlgorithms.hurdleOccurrenceProbability(0.10, 1.0, edulis)
        assertTrue("Hurdle per edulis in campo aperto (H=0.10) deve essere basso (<= 0.15): $pLowHabitat", pLowHabitat <= 0.15)

        // 3. Specie ectomicorrizica (edulis): habitat eccellente (H = 0.85, A = 1.0) -> barriera superata (p >= 0.95)
        val pOptimal = MushroomAlgorithms.hurdleOccurrenceProbability(0.85, 1.0, edulis)
        assertTrue("Hurdle per edulis in bosco favorevole deve superare 0.95: $pOptimal", pOptimal >= 0.95)

        // 4. Continuità e monotonicità: al crescere dell'idoneità stazionale, p_hurdle cresce monotonicamente
        var prevP = 0.0
        for (step in 0..20) {
            val hab = step / 20.0
            val p = MushroomAlgorithms.hurdleOccurrenceProbability(hab, 1.0, edulis)
            assertTrue("Monotonicità violata a hab=$hab: $p < $prevP", p >= prevP - 1e-6)
            prevP = p
        }

        // 5. Specie saprofita praticola (macrolepiota): in prato (H = 0.10), supera agevolmente la barriera
        val pMacroMeadow = MushroomAlgorithms.hurdleOccurrenceProbability(0.10, 1.0, macrolepiota)
        assertTrue("Macrolepiota in prato (H=0.10) deve superare la barriera hurdle (>= 0.90): $pMacroMeadow", pMacroMeadow >= 0.90)
    }

    @Test
    fun testStandDensityResponseUnimodal_PeakAtOptimalG() {
        val edulis = SPECIES_CATALOG.first { it.id == "boletus_edulis" }
        val deliciosus = SPECIES_CATALOG.first { it.id == "lactarius_deliciosus" }
        val macrolepiota = SPECIES_CATALOG.first { it.id == "macrolepiota_procera" }

        // 1. Verifica conversione canopy -> Area Basimetrica G
        assertEquals(0.0, MushroomAlgorithms.canopyCoverToBasalArea(0.0), 0.01)
        val gOpen = MushroomAlgorithms.canopyCoverToBasalArea(0.45)
        assertTrue("Canopy 0.45 deve corrispondere a G compreso tra 18 e 22 m²/ha: $gOpen", gOpen in 18.0..22.0)
        val gModerate = MushroomAlgorithms.canopyCoverToBasalArea(0.70)
        assertTrue("Canopy 0.70 deve corrispondere a G compreso tra 31 e 35 m²/ha: $gModerate", gModerate in 31.0..35.0)

        // 2. Boletus edulis: il picco unimodale è intorno a canopyCover 0.68..0.72 (G ~ 32 m²/ha)
        val scoreEdulisPeak = MushroomAlgorithms.standDensityResponseUnimodal(0.70, edulis)
        val scoreEdulisDense = MushroomAlgorithms.standDensityResponseUnimodal(0.95, edulis)
        val scoreEdulisSparse = MushroomAlgorithms.standDensityResponseUnimodal(0.15, edulis)

        assertTrue("Picco edulis deve essere molto vicino a 1.0: $scoreEdulisPeak", scoreEdulisPeak >= 0.99)
        assertTrue("Popolamento sovraffollato/chiuso (0.95) deve avere resa inferiore al picco (0.70): $scoreEdulisDense < $scoreEdulisPeak", scoreEdulisDense < scoreEdulisPeak)
        assertTrue("Popolamento rado (0.15) deve avere resa inferiore al picco (0.70): $scoreEdulisSparse < $scoreEdulisPeak", scoreEdulisSparse < scoreEdulisPeak)

        // 3. Lactarius deliciosus: preferisce pinete più aperte e soleggiate (G_opt = 20 m²/ha, canopy ~ 0.45)
        val scoreDeliciosusPeak = MushroomAlgorithms.standDensityResponseUnimodal(0.45, deliciosus)
        val scoreDeliciosusDense = MushroomAlgorithms.standDensityResponseUnimodal(0.85, deliciosus)
        assertTrue("Lactarius deliciosus al picco (0.45) deve essere vicino a 1.0: $scoreDeliciosusPeak", scoreDeliciosusPeak >= 0.99)
        assertTrue("Lactarius deliciosus in bosco fitto (0.85) deve avere resa inferiore a pineta aperta (0.45): $scoreDeliciosusDense < $scoreDeliciosusPeak", scoreDeliciosusDense < scoreDeliciosusPeak)

        // 4. Specie saprofita (macrolepiota): stand density non vincolante (restituisce 1.0)
        assertEquals(1.0, MushroomAlgorithms.standDensityResponseUnimodal(0.10, macrolepiota), 0.001)
        assertEquals(1.0, MushroomAlgorithms.standDensityResponseUnimodal(0.85, macrolepiota), 0.001)
    }

    @Test
    fun testEvaluateSpeciesHabitat_SaprotrophicVsEctomycorrhizal() {
        val edulis = SPECIES_CATALOG.first { it.id == "boletus_edulis" }
        val macrolepiota = SPECIES_CATALOG.first { it.id == "macrolepiota_procera" }

        // 1. In assenza di alberi (forestCount = 0):
        val habEdulisNoForest = MushroomAlgorithms.evaluateSpeciesHabitat(forestCount = 0, species = edulis)
        val habMacroNoForest = MushroomAlgorithms.evaluateSpeciesHabitat(forestCount = 0, species = macrolepiota)

        assertTrue("Edulis senza bosco deve avere habitat basso (<= 0.15): ${habEdulisNoForest.score}", habEdulisNoForest.score <= 0.15)
        assertTrue("Macrolepiota senza bosco (prato) deve avere habitat favorevole (>= 0.85): ${habMacroNoForest.score}", habMacroNoForest.score >= 0.85)

        // 2. Con alberi ospiti specifici:
        val habEdulisWithHost = MushroomAlgorithms.evaluateSpeciesHabitat(
            forestCount = 10,
            specificElementsCount = 3,
            species = edulis
        )
        assertTrue("Presenza alberi specifici per edulis deve potenziare il punteggio: ${habEdulisWithHost.score}", habEdulisWithHost.score >= 0.95)
        assertTrue("La descrizione bonus deve menzionare alberi ospiti: ${habEdulisWithHost.bonusText}", habEdulisWithHost.bonusText.contains("ospiti") || habEdulisWithHost.bonusText.contains("ottimali"))
    }

    @Test
    fun testTwoStageHurdleModel_DailyGrowthProbabilityGating() {
        val edulis = SPECIES_CATALOG.first { it.id == "boletus_edulis" }

        // A parità di meteo eccellente (W = 90, S = 1.0, T = 1.0):
        // Con stazione inidonea (H = 0.0):
        val probNoHabitat = MushroomAlgorithms.dailyGrowthProbability(
            weatherScore = 90,
            habitatScore = 0.0,
            altitudeScore = 1.0,
            seasonalityScore = 1.0,
            terrainModifier = 1.0,
            config = EcologicalWeightsConfig.PHENOLOGICAL,
            species = edulis
        )
        assertEquals("Con habitat nullo, il modello Hurdle deve bloccare la probabilità a 0", 0, probNoHabitat)

        // Con habitat ottimale (H = 1.0, A = 1.0):
        val probOptimal = MushroomAlgorithms.dailyGrowthProbability(
            weatherScore = 90,
            habitatScore = 1.0,
            altitudeScore = 1.0,
            seasonalityScore = 1.0,
            terrainModifier = 1.0,
            config = EcologicalWeightsConfig.PHENOLOGICAL,
            species = edulis
        )
        assertTrue("Con habitat ottimale e meteo 90, la probabilità deve essere favorevole (>= 75%): $probOptimal", probOptimal >= 75)
    }

    @Test
    fun testSpeciesCatalog_NewSpeciesIntegrity() {
        val deliciosus = SPECIES_CATALOG.firstOrNull { it.id == "lactarius_deliciosus" }
        assertNotNull("Lactarius deliciosus deve essere presente nel catalogo", deliciosus)
        assertEquals("Sanguinello / Fungo del pino", deliciosus!!.vernacularName)
        assertEquals(EcologicalCategory.ECTOMYCORRHIZAL, deliciosus.category)
        assertTrue("Lactarius deliciosus deve essere associato a Pinus", deliciosus.preferredCanopyTypes.contains("pinus"))
        assertEquals(20.0f, deliciosus.optimalBasalAreaM2Ha, 0.01f)
        assertTrue("Deve avere avvertenze tossiche", deliciosus.toxicLookAlikes.isNotEmpty())

        val morchella = SPECIES_CATALOG.firstOrNull { it.id == "morchella_esculenta" }
        assertNotNull("Morchella esculenta deve essere presente nel catalogo", morchella)
        assertEquals("Spugnola comune", morchella!!.vernacularName)
        assertEquals(EcologicalCategory.SAPROTROPHIC, morchella.category)
        assertTrue("Morchella deve fruttificare in primavera", morchella.activeMonths.contains(3)) // Aprile (indice 3)
        assertNotNull("Morchella deve avere avvertenza di cottura prolungata", morchella.edibilityWarning)
    }
}
