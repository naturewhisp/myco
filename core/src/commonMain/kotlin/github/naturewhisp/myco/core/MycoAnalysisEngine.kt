package github.naturewhisp.myco.core

import kotlin.math.max
import kotlin.math.roundToInt

class MycoAnalysisEngine {
    fun analyze(input: AnalysisInputs): AnalysisResult {
        val species = SpeciesCatalog.byId(input.speciesId)
        val todayIndex = input.todayIndex.coerceIn(0, max(0, input.days.lastIndex))
        val current = input.days.getOrNull(todayIndex) ?: emptyDay()
        val terrainBase = MycoAlgorithms.terrain(input.elevationSamples)
        val terrainModifier = MycoAlgorithms.terrainModifier(terrainBase, input.monthIndex, current.avgTemp, species)
        val terrain = terrainBase.copy(modifier = terrainModifier)
        val altitude = MycoAlgorithms.altitudeScore(terrain.elevation, species)
        val seasonality = MycoAlgorithms.seasonalityScore(input.monthIndex, species)
        val weather = MycoAlgorithms.weatherScore(todayIndex, input.days, species, input.spunHyphalDensity)
        var effectiveHabitat = if (species.category == EcologicalCategory.SAPROTROPHIC) {
            max(input.habitatScore, 0.85)
        } else {
            input.habitatScore
        }
        if (input.canopyTypes.isNotEmpty()) effectiveHabitat = minOf(1.0, effectiveHabitat * 1.15)
        input.spunEcmRichness?.let { richness ->
            effectiveHabitat = when {
                richness >= 50.0 -> minOf(1.0, effectiveHabitat * 1.15)
                richness < 15.0 && input.habitatScore > 0.1 -> max(0.2, effectiveHabitat * 0.8)
                else -> effectiveHabitat
            }
        }
        val probability = MycoAlgorithms.growthProbability(weather, effectiveHabitat, altitude, seasonality, terrainModifier)
        val factors = factors(input, current, species, effectiveHabitat, altitude, seasonality, terrain)
        val outlooks = input.days.drop(todayIndex).take(7).mapIndexed { offset, day ->
            val index = todayIndex + offset
            val dayWeather = MycoAlgorithms.weatherScore(index, input.days, species, input.spunHyphalDensity)
            val dayProbability = MycoAlgorithms.growthProbability(dayWeather, effectiveHabitat, altitude, seasonality, terrainModifier)
            DailyOutlook(day.dateIso, day.weatherCode, day.avgTemp, day.totalPrecipMm, day.avgHumidityPercent, dayProbability, ProbabilityTier.fromProbability(dayProbability))
        }
        val missing = input.missingSources.distinct()
        return AnalysisResult(
            probability = probability,
            tier = ProbabilityTier.fromProbability(probability),
            weatherScore = weather,
            habitatScore = effectiveHabitat,
            altitudeScore = altitude,
            seasonalityScore = seasonality,
            terrain = terrain,
            factors = factors,
            dailyOutlooks = outlooks,
            deterministicFieldNote = deterministicNote(probability, weather, effectiveHabitat, altitude, seasonality, missing),
            missingSources = missing,
        )
    }

    private fun factors(
        input: AnalysisInputs,
        day: ProcessedDay,
        species: MushroomSpecies,
        habitat: Double,
        altitude: Double,
        seasonality: Double,
        terrain: TerrainAspect,
    ): List<Factor> {
        val rain = input.days.take(input.todayIndex.coerceAtLeast(0)).takeLast(10).sumOf { it.totalPrecipMm }
        val soil = MycoAlgorithms.soilMoistureResponse(day.soilMoisture0To7, day.soilMoisture7To28, day.evapotranspiration)
        return buildList {
            add(factor(FactorId.TEMPERATURE, "Temperatura media", oneDecimal(day.avgTemp) + " °C", MycoAlgorithms.temperatureResponse(day.avgTemp, species), "Intervallo specifico della specie"))
            add(factor(FactorId.PRECIPITATION, "Precipitazioni cumulate", rain.roundToIntText() + " mm", MycoAlgorithms.rainResponse(rain, species), "Finestra idrica recente"))
            add(factor(FactorId.HUMIDITY, "Umidità relativa", day.avgHumidityPercent.roundToIntText() + "%", MycoAlgorithms.humidityResponse(day.avgHumidityPercent), "Aria prossima alla lettiera"))
            if (day.soilMoisture0To7 != null || day.soilMoisture7To28 != null) add(factor(FactorId.SOIL_MOISTURE, "Idratazione suolo", oneDecimal((day.soilMoisture0To7 ?: day.soilMoisture7To28 ?: 0.0) * 100) + "%", soil, "Orizzonti 0-7 e 7-28 cm"))
            add(factor(FactorId.HABITAT, "Habitat", (habitat * 100).roundToIntText() + "%", habitat, input.habitatDescription))
            add(factor(FactorId.ALTITUDE, "Fascia altimetrica", terrain.elevation.roundToIntText() + " m", altitude, species.fruitingPeriodDescription))
            add(factor(FactorId.SEASONALITY, "Finestra fenologica", (seasonality * 100).roundToIntText() + "%", seasonality, species.fruitingPeriodDescription))
            add(factor(FactorId.SLOPE, "Esposizione versante", terrain.cardinalDirection, terrain.modifier / 1.05, oneDecimal(terrain.slopeDegrees) + "°"))
            input.spunEcmRichness?.let { add(factor(FactorId.SPUN_ECM, "Simbiosi ectomicorrizica", it.roundToIntText() + " specie", (it / 55.0).coerceIn(0.0, 1.0), "Atlante SPUN")) }
            input.spunHyphalDensity?.let { add(factor(FactorId.SPUN_HYPHAL, "Biomassa ifale", oneDecimal(it) + " m/cm³", (it / 5.5).coerceIn(0.0, 1.0), "Atlante SPUN")) }
        }
    }

    private fun factor(id: FactorId, label: String, value: String, score: Double, detail: String): Factor = Factor(
        id,
        label,
        value,
        when {
            score >= 0.8 -> FactorLevel.FAVORABLE
            score >= 0.4 -> FactorLevel.NEUTRAL
            else -> FactorLevel.ADVERSE
        },
        detail,
    )

    private fun deterministicNote(probability: Int, weather: Int, habitat: Double, altitude: Double, seasonality: Double, missing: List<String>): String {
        val strongest = listOf("meteo" to weather / 100.0, "habitat" to habitat, "altitudine" to altitude, "stagione" to seasonality).maxBy { it.second }.first
        val weakest = listOf("meteo" to weather / 100.0, "habitat" to habitat, "altitudine" to altitude, "stagione" to seasonality).minBy { it.second }.first
        val coverage = if (missing.isEmpty()) "Tutte le fonti scientifiche sono disponibili." else "Fonti non disponibili: ${missing.joinToString()}. Il risultato è parziale."
        return "Probabilità stimata $probability%. Fattore più favorevole: $strongest; principale limite: $weakest. $coverage"
    }

    private fun emptyDay() = ProcessedDay("", 0.0, 0.0, 0.0, null, null, null, null)

    private fun oneDecimal(value: Double): String = ((value * 10.0).toInt() / 10.0).toString()

    private fun Double.roundToIntText(): String = roundToInt().toString()
}
