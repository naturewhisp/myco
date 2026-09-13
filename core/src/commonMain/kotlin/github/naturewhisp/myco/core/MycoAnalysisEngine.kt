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
        var probabilityHabitatScore = input.habitatScore
        if (input.canopyTypes.isNotEmpty()) probabilityHabitatScore = minOf(1.0, probabilityHabitatScore * 1.15)
        input.spunEcmRichness?.let { richness ->
            probabilityHabitatScore = when {
                richness >= 50.0 -> minOf(1.0, probabilityHabitatScore * 1.15)
                richness < 15.0 && input.habitatScore > 0.1 -> max(0.2, probabilityHabitatScore * 0.8)
                else -> probabilityHabitatScore
            }
        }
        val displayHabitatFactorScore = if (species.category == EcologicalCategory.SAPROTROPHIC) {
            max(probabilityHabitatScore, 0.85)
        } else {
            probabilityHabitatScore
        }
        val probability = MycoAlgorithms.growthProbability(weather, probabilityHabitatScore, altitude, seasonality, terrainModifier)
        val windows = EnvironmentalWindows.derive(input.days, todayIndex)
        val factors = factors(input, windows, species, displayHabitatFactorScore, altitude, seasonality, terrain)
        val outlooks = input.days.drop(todayIndex).take(7).mapIndexed { offset, day ->
            val index = todayIndex + offset
            val dayWeather = MycoAlgorithms.weatherScore(index, input.days, species, input.spunHyphalDensity)
            val dayProbability = MycoAlgorithms.growthProbability(dayWeather, probabilityHabitatScore, altitude, seasonality, terrainModifier)
            DailyOutlook(day.dateIso, day.weatherCode, day.avgTemp, day.totalPrecipMm, day.avgHumidityPercent, dayProbability, ProbabilityTier.fromProbability(dayProbability))
        }
        val missing = input.missingSources.distinct()
        return AnalysisResult(
            probability = probability,
            tier = ProbabilityTier.fromProbability(probability),
            weatherScore = weather,
            habitatScore = probabilityHabitatScore,
            altitudeScore = altitude,
            seasonalityScore = seasonality,
            terrain = terrain,
            factors = factors,
            dailyOutlooks = outlooks,
            deterministicFieldNote = deterministicNote(probability, weather, probabilityHabitatScore, altitude, seasonality, missing),
            missingSources = missing,
        )
    }

    private fun factors(
        input: AnalysisInputs,
        windows: EnvironmentalWindows,
        species: MushroomSpecies,
        habitat: Double,
        altitude: Double,
        seasonality: Double,
        terrain: TerrainAspect,
    ): List<Factor> {
        val soil = MycoAlgorithms.soilMoistureResponse(
            windows.averageSoil0To7,
            windows.averageSoil7To28,
            windows.averageEt0,
        )
        return buildList {
            add(factor(FactorId.TEMPERATURE, "Temperatura media", oneDecimal(windows.averageTempWindowC) + " °C", MycoAlgorithms.temperatureResponse(windows.averageTempWindowC, species), "Intervallo specifico della specie"))
            add(factor(FactorId.PRECIPITATION, "Precipitazioni cumulate", windows.rainWindowTotalMm.roundToIntText() + " mm", MycoAlgorithms.rainResponse(windows.rainWindowTotalMm, species), "Finestra idrica recente"))
            add(factor(FactorId.HUMIDITY, "Umidità relativa", windows.averageHumidityWindowPercent.roundToIntText() + "%", MycoAlgorithms.humidityResponse(windows.averageHumidityWindowPercent), "Aria prossima alla lettiera", favorable = 0.7))
            if (windows.averageSoil0To7 != null || windows.averageSoil7To28 != null) add(factor(FactorId.SOIL_MOISTURE, "Idratazione suolo", oneDecimal(windows.averageSoil0To7 ?: windows.averageSoil7To28 ?: 0.0) + " m³/m³", soil, "Orizzonti 0-7 e 7-28 cm", neutral = 0.45))
            add(factor(FactorId.HABITAT, if (species.category == EcologicalCategory.SAPROTROPHIC) "Idoneità suolo/margine" else "Copertura forestale", (habitat * 100).roundToIntText() + "%", habitat, input.habitatDescription, favorable = 0.85, neutral = 0.5))
            add(factor(FactorId.ALTITUDE, "Fascia altimetrica", terrain.elevation.roundToIntText() + " m", altitude, species.fruitingPeriodDescription, favorable = 0.85, neutral = 0.6))
            add(factor(FactorId.SEASONALITY, "Finestra fenologica", (seasonality * 100).roundToIntText() + "%", seasonality, species.fruitingPeriodDescription, favorable = 0.85, neutral = 0.5))
            add(
                Factor(
                    FactorId.SLOPE,
                    "Esposizione versante",
                    terrain.cardinalDirection,
                    when {
                        terrain.slopeDegrees < 3.0 -> FactorLevel.NEUTRAL
                        terrain.modifier > 1.0 -> FactorLevel.FAVORABLE
                        terrain.modifier < 1.0 -> FactorLevel.ADVERSE
                        else -> FactorLevel.NEUTRAL
                    },
                    oneDecimal(terrain.slopeDegrees) + "°",
                ),
            )
            input.spunEcmRichness?.let {
                add(Factor(FactorId.SPUN_ECM, "Simbiosi ectomicorrizica", it.roundToIntText() + " specie", FactorLevel.FAVORABLE, "Atlante SPUN"))
            }
            input.spunHyphalDensity?.let {
                add(Factor(FactorId.SPUN_HYPHAL, "Biomassa ifale", oneDecimal(it) + " m/cm³", FactorLevel.FAVORABLE, "Atlante SPUN"))
            }
        }
    }

    private fun factor(
        id: FactorId,
        label: String,
        value: String,
        score: Double,
        detail: String,
        favorable: Double = 0.8,
        neutral: Double = 0.4,
    ): Factor = Factor(
        id,
        label,
        value,
        when {
            score >= favorable -> FactorLevel.FAVORABLE
            score >= neutral -> FactorLevel.NEUTRAL
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
