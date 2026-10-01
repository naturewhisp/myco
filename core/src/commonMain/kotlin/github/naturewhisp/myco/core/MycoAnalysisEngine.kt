package github.naturewhisp.myco.core

import kotlin.math.max
import kotlin.math.roundToInt

class MycoAnalysisEngine {
    fun analyze(input: AnalysisInputs): AnalysisResult {
        val species = SpeciesCatalog.byId(input.speciesId)
        val todayIndex = input.todayIndex.coerceIn(0, max(0, input.days.lastIndex))
        val siteCanopyCover = input.canopyCover.coerceIn(0.0, 1.0)
        val bufferedDays = if (siteCanopyCover > 0.001) {
            MycoAlgorithms.applyCanopyBuffering(input.days, siteCanopyCover)
        } else {
            input.days
        }
        val current = bufferedDays.getOrNull(todayIndex) ?: emptyDay()
        val terrainBase = MycoAlgorithms.terrain(input.elevationSamples)
        val terrainModifier = MycoAlgorithms.terrainModifier(terrainBase, input.monthIndex, current.avgTemp, species)
        val terrain = terrainBase.copy(modifier = terrainModifier)
        val altitude = MycoAlgorithms.altitudeScore(terrain.elevation, species)
        val seasonality = MycoAlgorithms.seasonalityScore(input.monthIndex, species)

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

        val growthPhaseEval = MycoAlgorithms.evaluateGrowthPhase(bufferedDays, species, todayIndex)

        // Single-pass microclimate buffering invariant (RES-04): bufferedDays è già trasformato sub-canopy
        val weather = MycoAlgorithms.weatherScore(
            dayIndex = todayIndex,
            days = bufferedDays,
            species = species,
            spunHyphalDensity = input.spunHyphalDensity,
            applySpunHyphalBonus = false,
            usePhenologicalInertia = true,
            canopyCover = 0.0,
        )

        val probability = MycoAlgorithms.growthProbability(
            weatherScore = weather,
            habitatScore = probabilityHabitatScore,
            altitudeScore = altitude,
            seasonalityScore = seasonality,
            terrainModifier = terrainModifier,
            growthPhaseMultiplier = growthPhaseEval.multiplier,
            species = species,
            useHurdle = true,
        )

        var dataQuality = DataQualityStatus.OPTIMAL
        for (day in input.days) {
            if (!day.avgTemp.isFinite() || day.avgTemp !in -40.0..50.0 ||
                !day.minTemp.isFinite() || day.minTemp !in -40.0..50.0 ||
                !day.maxTemp.isFinite() || day.maxTemp !in -40.0..50.0 ||
                !day.totalPrecipMm.isFinite() || day.totalPrecipMm < 0.0 ||
                !day.avgHumidityPercent.isFinite() || day.avgHumidityPercent !in 0.0..100.0) {
                dataQuality = DataQualityStatus.DEGRADED_OUT_OF_BOUNDS
                break
            }
            val s0 = day.soilMoisture0To7
            if (s0 != null && (!s0.isFinite() || s0 !in 0.0..0.60)) {
                dataQuality = DataQualityStatus.DEGRADED_OUT_OF_BOUNDS
                break
            }
            val s7 = day.soilMoisture7To28
            if (s7 != null && (!s7.isFinite() || s7 !in 0.0..0.60)) {
                dataQuality = DataQualityStatus.DEGRADED_OUT_OF_BOUNDS
                break
            }
            val et = day.evapotranspiration
            if (et != null && (!et.isFinite() || et < 0.0)) {
                dataQuality = DataQualityStatus.DEGRADED_OUT_OF_BOUNDS
                break
            }
        }

        val soilWindow = (max(0, todayIndex - 2)..todayIndex).mapNotNull { i ->
            bufferedDays.getOrNull(i)?.soilMoisture0To7
        }
        val waterDiagnosis: String
        if (soilWindow.size < 2) {
            if (dataQuality == DataQualityStatus.OPTIMAL) {
                dataQuality = DataQualityStatus.DEGRADED_MISSING_SOIL
            }
            waterDiagnosis = "Diagnosi idrica non determinabile per assenza di dati pedologici"
        } else {
            if (soilWindow.size == 2 && dataQuality == DataQualityStatus.OPTIMAL) {
                dataQuality = DataQualityStatus.DEGRADED_PARTIAL_SOIL
            }
            val avgSoil = soilWindow.average()
            waterDiagnosis = "Umidità orizzonte 0–7 cm: ${oneDecimal(avgSoil)} m³/m³ (media retrospettiva 3 gg)"
        }

        val windows = EnvironmentalWindows.derive(bufferedDays, todayIndex)
        val factors = factors(input, windows, species, displayHabitatFactorScore, altitude, seasonality, terrain, growthPhaseEval)
        val outlooks = bufferedDays.drop(todayIndex).take(7).mapIndexed { offset, day ->
            val index = todayIndex + offset
            val dayWeather = MycoAlgorithms.weatherScore(
                dayIndex = index,
                days = bufferedDays,
                species = species,
                spunHyphalDensity = input.spunHyphalDensity,
                applySpunHyphalBonus = false,
                usePhenologicalInertia = true,
                canopyCover = 0.0,
            )
            val dayMonth = try {
                val parts = day.dateIso.split("-")
                if (parts.size >= 2) parts[1].toInt() - 1 else input.monthIndex
            } catch (_: Exception) {
                input.monthIndex
            }
            val daySeasonality = MycoAlgorithms.seasonalityScore(dayMonth, species)
            val dayGrowthPhase = MycoAlgorithms.evaluateGrowthPhase(bufferedDays, species, index)
            val dayProbability = MycoAlgorithms.growthProbability(
                weatherScore = dayWeather,
                habitatScore = probabilityHabitatScore,
                altitudeScore = altitude,
                seasonalityScore = daySeasonality,
                terrainModifier = terrainModifier,
                growthPhaseMultiplier = dayGrowthPhase.multiplier,
                species = species,
                useHurdle = true,
            )
            DailyOutlook(
                dateIso = day.dateIso,
                weatherCode = day.weatherCode,
                avgTemp = day.avgTemp,
                totalPrecipMm = day.totalPrecipMm,
                avgHumidityPercent = day.avgHumidityPercent,
                probability = dayProbability,
                tier = ProbabilityTier.fromProbability(dayProbability),
            )
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
            growthPhase = growthPhaseEval,
            dataQuality = dataQuality,
            waterDiagnosis = waterDiagnosis,
        )
    }

    fun analyzeLegacy(
        input: AnalysisInputs,
        growthPhaseMultiplier: Double = 1.0,
        useHurdle: Boolean = false,
    ): AnalysisResult = analyze(input, growthPhaseMultiplier, useHurdle)

    fun analyze(
        input: AnalysisInputs,
        growthPhaseMultiplier: Double,
        useHurdle: Boolean,
    ): AnalysisResult {
        val species = SpeciesCatalog.byId(input.speciesId)
        val todayIndex = input.todayIndex.coerceIn(0, max(0, input.days.lastIndex))
        val current = input.days.getOrNull(todayIndex) ?: emptyDay()
        val terrainBase = MycoAlgorithms.terrain(input.elevationSamples)
        val terrainModifier = MycoAlgorithms.terrainModifier(terrainBase, input.monthIndex, current.avgTemp, species)
        val terrain = terrainBase.copy(modifier = terrainModifier)
        val altitude = MycoAlgorithms.altitudeScore(terrain.elevation, species)
        val seasonality = MycoAlgorithms.seasonalityScore(input.monthIndex, species)
        val weather = MycoAlgorithms.weatherScore(
            dayIndex = todayIndex,
            days = input.days,
            species = species,
            spunHyphalDensity = input.spunHyphalDensity,
            applySpunHyphalBonus = false,
            usePhenologicalInertia = false,
        )
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
        val probability = MycoAlgorithms.growthProbability(
            weatherScore = weather,
            habitatScore = probabilityHabitatScore,
            altitudeScore = altitude,
            seasonalityScore = seasonality,
            terrainModifier = terrainModifier,
            growthPhaseMultiplier = growthPhaseMultiplier,
            species = species,
            useHurdle = useHurdle,
        )
        val windows = EnvironmentalWindows.derive(input.days, todayIndex)
        val factors = factorsLegacy(input, windows, species, displayHabitatFactorScore, altitude, seasonality, terrain)
        val outlooks = input.days.drop(todayIndex).take(7).mapIndexed { offset, day ->
            val index = todayIndex + offset
            val dayWeather = MycoAlgorithms.weatherScore(
                dayIndex = index,
                days = input.days,
                species = species,
                spunHyphalDensity = input.spunHyphalDensity,
                applySpunHyphalBonus = false,
                usePhenologicalInertia = false,
            )
            val dayMonth = try {
                val parts = day.dateIso.split("-")
                if (parts.size >= 2) parts[1].toInt() - 1 else input.monthIndex
            } catch (_: Exception) {
                input.monthIndex
            }
            val daySeasonality = MycoAlgorithms.seasonalityScore(dayMonth, species)
            val dayProbability = MycoAlgorithms.growthProbability(
                weatherScore = dayWeather,
                habitatScore = probabilityHabitatScore,
                altitudeScore = altitude,
                seasonalityScore = daySeasonality,
                terrainModifier = terrainModifier,
                growthPhaseMultiplier = growthPhaseMultiplier,
                species = species,
                useHurdle = useHurdle,
            )
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
        growthPhase: GrowthPhaseEvaluation,
    ): List<Factor> {
        val soil = MycoAlgorithms.soilMoistureResponse(
            windows.averageSoil0To7,
            windows.averageSoil7To28,
            windows.averageEt0,
        )
        return buildList {
            add(factor(FactorId.TEMPERATURE, "Temperatura media", oneDecimal(windows.averageTempWindowC) + " °C", MycoAlgorithms.temperatureResponse(windows.averageTempWindowC, species), "Intervallo specifico della specie"))
            add(factor(FactorId.PRECIPITATION, "Apporto ponderato per latenza", windows.rainWindowTotalMm.roundToIntText() + " mm ponderati", MycoAlgorithms.rainResponse(windows.rainWindowTotalMm, species), "Indicatore fenologico temporale; non misura la riserva idrica residua nel suolo"))
            add(factor(FactorId.HUMIDITY, "Umidità relativa", windows.averageHumidityWindowPercent.roundToIntText() + "%", MycoAlgorithms.humidityResponse(windows.averageHumidityWindowPercent), "Aria prossima alla lettiera", favorable = 0.7))
            if (windows.averageSoil0To7 != null || windows.averageSoil7To28 != null) add(factor(FactorId.SOIL_MOISTURE, "Idratazione suolo", oneDecimal(windows.averageSoil0To7 ?: windows.averageSoil7To28 ?: 0.0) + " m³/m³", soil, "Orizzonti 0-7 e 7-28 cm", neutral = 0.45))
            add(factor(FactorId.HABITAT, if (species.category == EcologicalCategory.SAPROTROPHIC) "Idoneità suolo/margine" else "Copertura forestale", (habitat * 100).roundToIntText() + "%", habitat, input.habitatDescription + " • Indice di prossimità forestale (settori a 8 spicchi)", favorable = 0.85, neutral = 0.5))
            add(factor(FactorId.ALTITUDE, "Fascia altimetrica", terrain.elevation.roundToIntText() + " m", altitude, species.fruitingPeriodDescription, favorable = 0.85, neutral = 0.6))
            add(factor(FactorId.SEASONALITY, "Finestra fenologica", (seasonality * 100).roundToIntText() + "%", seasonality, species.fruitingPeriodDescription, favorable = 0.85, neutral = 0.5))
            add(
                Factor(
                    FactorId.MYCELIAL_PHASE,
                    "Fase fenologica",
                    when (growthPhase.stage) {
                        GrowthStage.ACTIVE_FRUITING -> "Buttata attiva"
                        GrowthStage.PRIMORDIA_INCUBATION -> "Incubazione"
                        GrowthStage.MYCELIAL_HYDRATION -> "Idratazione"
                        GrowthStage.WANING -> "Disseccamento"
                        GrowthStage.WAITING_FOR_RAIN -> "In attesa"
                    },
                    when (growthPhase.stage) {
                        GrowthStage.ACTIVE_FRUITING -> FactorLevel.FAVORABLE
                        GrowthStage.PRIMORDIA_INCUBATION, GrowthStage.MYCELIAL_HYDRATION -> FactorLevel.NEUTRAL
                        GrowthStage.WAITING_FOR_RAIN, GrowthStage.WANING -> FactorLevel.ADVERSE
                    },
                    growthPhase.phaseText,
                ),
            )
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

    private fun factorsLegacy(
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
            add(factor(FactorId.PRECIPITATION, "Apporto ponderato per latenza", windows.rainWindowTotalMm.roundToIntText() + " mm ponderati", MycoAlgorithms.rainResponse(windows.rainWindowTotalMm, species), "Indicatore fenologico temporale; non misura la riserva idrica residua nel suolo"))
            add(factor(FactorId.HUMIDITY, "Umidità relativa", windows.averageHumidityWindowPercent.roundToIntText() + "%", MycoAlgorithms.humidityResponse(windows.averageHumidityWindowPercent), "Aria prossima alla lettiera", favorable = 0.7))
            if (windows.averageSoil0To7 != null || windows.averageSoil7To28 != null) add(factor(FactorId.SOIL_MOISTURE, "Idratazione suolo", oneDecimal(windows.averageSoil0To7 ?: windows.averageSoil7To28 ?: 0.0) + " m³/m³", soil, "Orizzonti 0-7 e 7-28 cm", neutral = 0.45))
            add(factor(FactorId.HABITAT, if (species.category == EcologicalCategory.SAPROTROPHIC) "Idoneità suolo/margine" else "Copertura forestale", (habitat * 100).roundToIntText() + "%", habitat, input.habitatDescription + " • Indice di prossimità forestale (settori a 8 spicchi)", favorable = 0.85, neutral = 0.5))
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
        return "Indice di idoneità stimato $probability/100. Fattore più favorevole: $strongest; principale limite: $weakest. $coverage"
    }

    private fun emptyDay() = ProcessedDay("", 0.0, 0.0, 0.0, null, null, null, null)

    private fun oneDecimal(value: Double): String = ((value * 10.0).toInt() / 10.0).toString()

    private fun Double.roundToIntText(): String = roundToInt().toString()
}
