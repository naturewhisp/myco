package github.naturewhisp.myco.core

import kotlin.math.max
import kotlin.math.roundToInt

class MycoAnalysisEngine {
    fun analyze(rawInput: AnalysisInputs): AnalysisResult {
        val target = rawInput.days.getOrNull(rawInput.todayIndex)
            ?: return emptyResult(DataQualityStatus.DEGRADED_OUT_OF_BOUNDS)
        val targetEpoch = MycoAlgorithms.isoDateToEpochDay(target.dateIso)
            ?: return emptyResult(DataQualityStatus.DEGRADED_OUT_OF_BOUNDS)
        if (rawInput.targetDateIso != null && rawInput.targetDateIso != target.dateIso) return emptyResult(DataQualityStatus.DEGRADED_OUT_OF_BOUNDS)
        if (target.coverage?.weatherUsable == false) return emptyResult(DataQualityStatus.UNAVAILABLE_INCOMPLETE_WEATHER)
        val relevant = rawInput.days.filter {
            MycoAlgorithms.isoDateToEpochDay(it.dateIso)?.let { epoch -> epoch in (targetEpoch - 28)..targetEpoch } == true
        }
        if (relevant.map { it.dateIso }.distinct().size != relevant.size) return emptyResult(DataQualityStatus.DEGRADED_OUT_OF_BOUNDS)
        val usable = rawInput.days.filter {
            it.coverage?.weatherUsable != false && isValidIsoDate(it.dateIso) &&
                (MycoAlgorithms.isoDateToEpochDay(it.dateIso)!! <= targetEpoch || validWeatherDay(it))
        }.sortedBy { it.dateIso }
        if (rawInput.spunEcmRichness?.let { !it.isFinite() || it < 0.0 } == true) return emptyResult(DataQualityStatus.DEGRADED_OUT_OF_BOUNDS)
        val evidence = rawInput.habitatEvidence
        if (evidence != null && (!evidence.forestCoverFraction.isFinite() || evidence.forestCoverFraction !in 0.0..1.0 ||
            !evidence.meadowFraction.isFinite() || evidence.meadowFraction !in 0.0..1.0 ||
            !evidence.forestProximityIndex.isFinite() || evidence.forestProximityIndex !in 0.0..1.0 ||
            !evidence.distanceToNearestForestMeters.isFinite() || evidence.distanceToNearestForestMeters < 0.0)) {
            return emptyResult(DataQualityStatus.DEGRADED_OUT_OF_BOUNDS)
        }
        val habitatEvaluation = evidence?.let { MycoAlgorithms.evaluateHabitat(it, SpeciesCatalog.byId(rawInput.speciesId), rawInput.spunEcmRichness) }
        val input = rawInput.copy(
            days = usable, todayIndex = usable.indexOfFirst { it.dateIso == target.dateIso },
            habitatScore = habitatEvaluation?.baseScore ?: rawInput.habitatScore,
            habitatDescription = habitatEvaluation?.baseText ?: rawInput.habitatDescription,
            canopyTypes = evidence?.confirmedHostGenera?.toList() ?: rawInput.canopyTypes,
            canopyCover = evidence?.forestCoverFraction ?: rawInput.canopyCover,
            forestProximityIndex = evidence?.forestProximityIndex ?: rawInput.forestProximityIndex,
        )
        if (input.days.isEmpty() || input.todayIndex !in input.days.indices) {
            return emptyResult(DataQualityStatus.DEGRADED_OUT_OF_BOUNDS)
        }
        if (!input.habitatScore.isFinite() || input.habitatScore !in 0.0..1.0 ||
            !input.forestProximityIndex.isFinite() || input.forestProximityIndex !in 0.0..1.0 || input.monthIndex !in 0..11 ||
            !input.canopyCover.isFinite() || input.canopyCover !in 0.0..1.0 ||
            input.elevationSamples.any { !it.isFinite() || it !in -500.0..9000.0 } ||
            (input.spunEcmRichness != null && (!input.spunEcmRichness.isFinite() || input.spunEcmRichness < 0.0)) ||
            (input.spunHyphalDensity != null && (!input.spunHyphalDensity.isFinite() || input.spunHyphalDensity < 0.0))) {
            return emptyResult(DataQualityStatus.DEGRADED_OUT_OF_BOUNDS)
        }

        val species = SpeciesCatalog.byId(input.speciesId)
        val todayIndex = input.todayIndex

        // 1. Controllo preventivo di plausibilità e completezza dati (C05, D04, D05)
        var dataQuality = DataQualityStatus.OPTIMAL
        for (day in input.days.filter { MycoAlgorithms.isoDateToEpochDay(it.dateIso)?.let { epoch -> epoch in (targetEpoch - 28)..targetEpoch } == true }) {
            if (!validWeatherDay(day)) {
                dataQuality = DataQualityStatus.DEGRADED_OUT_OF_BOUNDS
                break
            }
        }

        if (dataQuality == DataQualityStatus.DEGRADED_OUT_OF_BOUNDS) {
            return emptyResult(DataQualityStatus.DEGRADED_OUT_OF_BOUNDS)
        }

        val siteCanopyCover = input.canopyCover.coerceIn(0.0, 1.0)
        val bufferedDays = if (siteCanopyCover > 0.0) {
            MycoAlgorithms.applyCanopyBuffering(input.days, siteCanopyCover)
        } else {
            input.days
        }
        val current = bufferedDays.getOrNull(todayIndex) ?: emptyDay()

        // 2. Diagnosi idrica pedologica condivisa C1 (C06) e copertura finestre temporali
        val soilEval = MycoAlgorithms.calculateSoilMoistureFactor(bufferedDays, todayIndex)
        val windows = EnvironmentalWindows.derive(bufferedDays, todayIndex)
        if (soilEval.availableDaysCount < 2) {
            if (dataQuality == DataQualityStatus.OPTIMAL) {
                dataQuality = DataQualityStatus.DEGRADED_MISSING_SOIL
            }
        } else if (soilEval.availableDaysCount == 2 && dataQuality == DataQualityStatus.OPTIMAL) {
            dataQuality = DataQualityStatus.DEGRADED_PARTIAL_SOIL
        }
        val qualityReasons = mutableListOf<String>()
        if (windows.evapotranspiration.any { it.coverage?.et0Usable == false }) qualityReasons.add("ET0 incompleta")
        if (soilEval.availableDaysCount < 2) qualityReasons.add("suolo")
        else if (soilEval.availableDaysCount < 3) qualityReasons.add("suolo parziale")
        val incompleteWeather = windows.temperatureAvailableDays < 5 || windows.rainAvailableDays < 8 ||
            windows.humidityAvailableDays < 4 || relevant.any { it.coverage?.weatherUsable == false }
        if (incompleteWeather) {
            qualityReasons.add("meteo lacunoso")
            if (dataQuality == DataQualityStatus.OPTIMAL) dataQuality = DataQualityStatus.DEGRADED_INCOMPLETE_WEATHER
        }
        val waterDiagnosis = soilEval.diagnosisText

        val terrainBase = MycoAlgorithms.terrain(input.elevationSamples)
        val terrainModifier = MycoAlgorithms.terrainModifier(terrainBase, input.monthIndex, current.avgTemp, species)
        val terrain = terrainBase.copy(modifier = terrainModifier)
        val altitude = MycoAlgorithms.altitudeScore(terrain.elevation, species)
        val seasonality = MycoAlgorithms.seasonalityScore(input.monthIndex, species)

        val isWeatherOnly = input.calculationMode == "WEATHER_ONLY"

        var probabilityHabitatScore = if (isWeatherOnly) 1.0 else input.habitatScore
        if (!isWeatherOnly) {
            probabilityHabitatScore = MycoAlgorithms.applyHabitatBonusPenalty(
                probabilityHabitatScore,
                MycoAlgorithms.habitatModifier(species, input.canopyTypes, input.spunEcmRichness),
            )
        }
        val displayHabitatFactorScore = if (isWeatherOnly) {
            1.0
        } else {
            probabilityHabitatScore
        }

        val effectiveAltitude = if (isWeatherOnly) 1.0 else altitude
        val effectiveSeasonality = if (isWeatherOnly) 1.0 else seasonality
        val effectiveTerrainModifier = if (isWeatherOnly) 1.0 else terrainModifier

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
            altitudeScore = effectiveAltitude,
            seasonalityScore = effectiveSeasonality,
            terrainModifier = effectiveTerrainModifier,
            growthPhaseMultiplier = growthPhaseEval.multiplier,
            species = species,
            useHurdle = !isWeatherOnly,
        )

        val effectiveRain = MycoAlgorithms.calculateEffectiveRainfall(todayIndex, bufferedDays, species)
        val factors = factors(
            input = input,
            windows = windows,
            species = species,
            habitat = displayHabitatFactorScore,
            altitude = effectiveAltitude,
            seasonality = effectiveSeasonality,
            terrain = terrain,
            growthPhase = growthPhaseEval,
            effectiveRainMm = effectiveRain,
        )

        val outlooks = rawInput.days.filter { day ->
            MycoAlgorithms.isoDateToEpochDay(day.dateIso)?.let { it in targetEpoch..(targetEpoch + 6) } == true
        }.distinctBy { it.dateIso }.sortedBy { it.dateIso }.map { rawDay ->
            val index = bufferedDays.indexOfFirst { it.dateIso == rawDay.dateIso }
            if (index < 0 || !validWeatherDay(rawDay) || rawInput.days.count { it.dateIso == rawDay.dateIso } != 1) {
                return@map DailyOutlook(rawDay.dateIso, rawDay.weatherCode, 0.0, 0.0, 0.0, 0,
                    ProbabilityTier.VERY_LOW, false, listOf("Copertura meteorologica insufficiente o dati non validi"))
            }
            val day = bufferedDays[index]
            val dayWindows = EnvironmentalWindows.derive(bufferedDays, index)
            val daySoil = MycoAlgorithms.calculateSoilMoistureFactor(bufferedDays, index)
            val dayQuality = buildList {
                if (dayWindows.temperatureAvailableDays < 5 || dayWindows.rainAvailableDays < 8 || dayWindows.humidityAvailableDays < 4) add("meteo lacunoso")
                if (daySoil.availableDaysCount < 2) add("suolo") else if (daySoil.availableDaysCount < 3) add("suolo parziale")
            }
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
            val daySeasonality = if (isWeatherOnly) 1.0 else MycoAlgorithms.seasonalityScore(dayMonth, species)
            val dayGrowthPhase = MycoAlgorithms.evaluateGrowthPhase(bufferedDays, species, index)
            val dayProbability = MycoAlgorithms.growthProbability(
                weatherScore = dayWeather,
                habitatScore = probabilityHabitatScore,
                altitudeScore = effectiveAltitude,
                seasonalityScore = daySeasonality,
                terrainModifier = effectiveTerrainModifier,
                growthPhaseMultiplier = dayGrowthPhase.multiplier,
                species = species,
                useHurdle = !isWeatherOnly,
            )
            DailyOutlook(
                dateIso = day.dateIso,
                weatherCode = day.weatherCode,
                avgTemp = day.avgTemp,
                totalPrecipMm = day.totalPrecipMm,
                avgHumidityPercent = day.avgHumidityPercent,
                probability = dayProbability,
                tier = ProbabilityTier.fromProbability(dayProbability),
                qualityReasons = dayQuality,
            )
        }
        val missing = (input.missingSources + qualityReasons).distinct()
        return AnalysisResult(
            probability = probability,
            tier = ProbabilityTier.fromProbability(probability),
            weatherScore = weather,
            habitatScore = probabilityHabitatScore,
            altitudeScore = effectiveAltitude,
            seasonalityScore = effectiveSeasonality,
            terrain = terrain,
            factors = factors,
            dailyOutlooks = outlooks,
            deterministicFieldNote = deterministicNote(
                probability = probability,
                weather = weather,
                habitat = probabilityHabitatScore,
                altitude = effectiveAltitude,
                seasonality = effectiveSeasonality,
                growthPhase = growthPhaseEval,
                dataQuality = dataQuality,
                missing = missing,
            ),
            missingSources = missing,
            growthPhase = growthPhaseEval,
            dataQuality = dataQuality,
            waterDiagnosis = waterDiagnosis,
            effectiveRainMm = effectiveRain,
            qualityReasons = qualityReasons,
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
        if (input.canopyTypes.isNotEmpty()) {
            probabilityHabitatScore = minOf(1.0, probabilityHabitatScore * 1.15)
        }
        input.spunEcmRichness?.let { richness ->
            probabilityHabitatScore = when {
                richness >= 50.0 -> minOf(1.0, probabilityHabitatScore * 1.15)
                richness < 15.0 && input.habitatScore > 0.1 -> MycoAlgorithms.applyHabitatBonusPenalty(probabilityHabitatScore, 0.8)
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
            deterministicFieldNote = deterministicNote(
                probability = probability,
                weather = weather,
                habitat = probabilityHabitatScore,
                altitude = altitude,
                seasonality = seasonality,
                growthPhase = null,
                dataQuality = DataQualityStatus.OPTIMAL,
                missing = missing,
            ),
            missingSources = missing,
            effectiveRainMm = windows.rainWindowTotalMm,
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
        effectiveRainMm: Double,
    ): List<Factor> {
        val soil = MycoAlgorithms.soilMoistureResponse(
            windows.averageSoil0To7,
            windows.averageSoil7To28,
            windows.averageEt0,
        )
        return buildList {
            add(factor(FactorId.TEMPERATURE, "Temperatura media", oneDecimal(windows.averageTempWindowC) + " °C", MycoAlgorithms.temperatureResponse(windows.averageTempWindowC, species), "Ottimale ${species.idealTempMin.toInt()}–${species.idealTempMax.toInt()} °C (tollerata ${species.toleratedTempMin.toInt()}–${species.toleratedTempMax.toInt()} °C)"))
            // C03: Usare la reale convoluzione per latenza fenologica f(tau) over 26 gg
            add(factor(FactorId.PRECIPITATION, "Apporto ponderato per latenza", effectiveRainMm.roundToIntText() + " mm ponderati", MycoAlgorithms.rainResponse(effectiveRainMm, species), "Indicatore fenologico temporale; non misura la riserva idrica residua nel suolo"))
            add(factor(FactorId.HUMIDITY, "Umidità relativa", windows.averageHumidityWindowPercent.roundToIntText() + "%", MycoAlgorithms.humidityResponse(windows.averageHumidityWindowPercent), "Aria prossima alla lettiera", favorable = 0.7))
            if (windows.averageSoil0To7 != null || windows.averageSoil7To28 != null) {
                val soilVal = windows.averageSoil0To7 ?: windows.averageSoil7To28 ?: 0.0
                add(factor(FactorId.SOIL_MOISTURE, "Idratazione suolo", twoDecimals(soilVal) + " m³/m³", soil, "Orizzonti 0-7 e 7-28 cm", neutral = 0.45))
            }
            // Mostrare separatamente prossimità forestale e idoneità ecologica (D03 / Issue 3)
            val habLabel = if (species.category == EcologicalCategory.SAPROTROPHIC) "Idoneità suolo/margine" else "Idoneità ecologica habitat"
            val habValue = (habitat * 100).roundToIntText() + "/100"
            val rawHabDesc = input.habitatDescription.removePrefix("Habitat: ").trim()
            val finalHabDesc = if (rawHabDesc.isEmpty() || rawHabDesc == "KNOWN_SUITABLE" || rawHabDesc == "UNKNOWN" || rawHabDesc == "KNOWN_UNSUITABLE") {
                when {
                    habitat >= 0.85 -> "Condizioni forestali e vegetazionali favorevoli."
                    habitat >= 0.50 -> "Copertura forestale mista o moderata."
                    else -> "Copertura vegetazionale scarsa o non ideale."
                }
            } else {
                rawHabDesc
            }
            add(factor(FactorId.HABITAT, habLabel, habValue, habitat, finalHabDesc, favorable = 0.85, neutral = 0.5))
            val proxValue = (input.forestProximityIndex * 100).roundToIntText() + "/100"
            add(factor(FactorId.FOREST_PROXIMITY, "Indice di prossimità forestale", proxValue, input.forestProximityIndex, "Prossimità alle superfici OSM; proxy geografico, non misura della chiusura della chioma", favorable = 0.70, neutral = 0.40))
            val altDesc = when {
                altitude >= 0.85 -> "Fascia ottimale (${species.idealElevationMin}–${species.idealElevationMax} m s.l.m.)"
                altitude >= 0.50 -> "Intervallo compatibile (${species.idealElevationMin}–${species.idealElevationMax} m s.l.m.)"
                else -> "Fuori dalla fascia ideale (${species.idealElevationMin}–${species.idealElevationMax} m s.l.m.)"
            }
            add(factor(FactorId.ALTITUDE, "Fascia altimetrica", terrain.elevation.roundToIntText() + " m", altitude, altDesc, favorable = 0.85, neutral = 0.6))
            add(factor(FactorId.SEASONALITY, "Finestra fenologica", (seasonality * 100).roundToIntText() + "%", seasonality, species.fruitingPeriodDescription, favorable = 0.85, neutral = 0.5))
            add(
                Factor(
                    FactorId.MYCELIAL_PHASE,
                    "Fase fenologica",
                    when (growthPhase.stage) {
                        GrowthStage.ACTIVE_FRUITING -> "Finestra teorica di maturazione"
                        GrowthStage.PRIMORDIA_INCUBATION -> "Incubazione"
                        GrowthStage.MYCELIAL_HYDRATION -> "Idratazione"
                        GrowthStage.WANING -> if (growthPhase.phiSoil <= 0.50) "Disseccamento" else "Finestra in esaurimento"
                        GrowthStage.WAITING_FOR_RAIN -> "In attesa"
                    },
                    when (growthPhase.stage) {
                        GrowthStage.ACTIVE_FRUITING -> FactorLevel.FAVORABLE
                        GrowthStage.PRIMORDIA_INCUBATION, GrowthStage.MYCELIAL_HYDRATION -> FactorLevel.NEUTRAL
                        GrowthStage.WAITING_FOR_RAIN -> FactorLevel.ADVERSE
                        GrowthStage.WANING -> if (growthPhase.phiSoil <= 0.50) FactorLevel.ADVERSE else FactorLevel.NEUTRAL
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
                        terrain.cardinalDirection == "Non disponibile" -> FactorLevel.NEUTRAL
                        terrain.slopeDegrees < 3.0 -> FactorLevel.NEUTRAL
                        terrain.modifier > 1.0 -> FactorLevel.FAVORABLE
                        terrain.modifier < 1.0 -> FactorLevel.ADVERSE
                        else -> FactorLevel.NEUTRAL
                    },
                    if (terrain.cardinalDirection == "Non disponibile") "Dati DEM non disponibili" else oneDecimal(terrain.slopeDegrees) + "°",
                ),
            )
            // C08: Luna puramente astronomica e informativa
            add(
                Factor(
                    FactorId.LUNAR_PHASE,
                    "Fase lunare (astronomica)",
                    "Informativa",
                    FactorLevel.INFORMATIVE,
                    "Riferimento astronomico e tradizione popolare; nessuna correlazione causale dimostrata con la fruttificazione.",
                )
            )
            input.spunEcmRichness?.let {
                add(Factor(FactorId.SPUN_ECM, "Simbiosi ectomicorrizica", it.roundToIntText() + " specie", FactorLevel.FAVORABLE, "Atlante SPUN"))
            }
            // C08: SPUN_HYPHAL informativo
            input.spunHyphalDensity?.let {
                add(Factor(FactorId.SPUN_HYPHAL, "Rete ifale del suolo (SPUN)", oneDecimal(it) + " m/cm³", FactorLevel.INFORMATIVE, "Biomassa ifale complessiva del suolo (prevalenza arbuscolare); non misura il micelio della specie target."))
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
            add(factor(FactorId.TEMPERATURE, "Temperatura media", oneDecimal(windows.averageTempWindowC) + " °C", MycoAlgorithms.temperatureResponse(windows.averageTempWindowC, species), "Ottimale ${species.idealTempMin.toInt()}–${species.idealTempMax.toInt()} °C (tollerata ${species.toleratedTempMin.toInt()}–${species.toleratedTempMax.toInt()} °C)"))
            // C03: In legacy il cumulato rettangolare è esplicitamente indicato
            add(factor(FactorId.PRECIPITATION, "Precipitazioni finestra recente (legacy)", windows.rainWindowTotalMm.roundToIntText() + " mm", MycoAlgorithms.rainResponse(windows.rainWindowTotalMm, species), "Finestra rettangolare [t-10, t-2)"))
            add(factor(FactorId.HUMIDITY, "Umidità relativa", windows.averageHumidityWindowPercent.roundToIntText() + "%", MycoAlgorithms.humidityResponse(windows.averageHumidityWindowPercent), "Aria prossima alla lettiera", favorable = 0.7))
            if (windows.averageSoil0To7 != null || windows.averageSoil7To28 != null) add(factor(FactorId.SOIL_MOISTURE, "Idratazione suolo", oneDecimal(windows.averageSoil0To7 ?: windows.averageSoil7To28 ?: 0.0) + " m³/m³", soil, "Orizzonti 0-7 e 7-28 cm", neutral = 0.45))
            val habLabel = if (species.category == EcologicalCategory.SAPROTROPHIC) "Idoneità suolo/margine" else "Copertura forestale"
            val rawHabDesc = input.habitatDescription.removePrefix("Habitat: ").trim()
            val finalHabDesc = if (rawHabDesc.isEmpty() || rawHabDesc == "KNOWN_SUITABLE" || rawHabDesc == "UNKNOWN" || rawHabDesc == "KNOWN_UNSUITABLE") {
                when {
                    habitat >= 0.85 -> "Condizioni forestali e vegetazionali favorevoli."
                    habitat >= 0.50 -> "Copertura forestale mista o moderata."
                    else -> "Copertura vegetazionale scarsa o non ideale."
                }
            } else {
                rawHabDesc
            }
            add(factor(FactorId.HABITAT, habLabel, (habitat * 100).roundToIntText() + "%", habitat, finalHabDesc, favorable = 0.85, neutral = 0.5))
            val altDesc = when {
                altitude >= 0.85 -> "Fascia ottimale (${species.idealElevationMin}–${species.idealElevationMax} m s.l.m.)"
                altitude >= 0.50 -> "Intervallo compatibile (${species.idealElevationMin}–${species.idealElevationMax} m s.l.m.)"
                else -> "Fuori dalla fascia ideale (${species.idealElevationMin}–${species.idealElevationMax} m s.l.m.)"
            }
            add(factor(FactorId.ALTITUDE, "Fascia altimetrica", terrain.elevation.roundToIntText() + " m", altitude, altDesc, favorable = 0.85, neutral = 0.6))
            add(factor(FactorId.SEASONALITY, "Finestra fenologica", (seasonality * 100).roundToIntText() + "%", seasonality, species.fruitingPeriodDescription, favorable = 0.85, neutral = 0.5))
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

    private fun deterministicNote(
        probability: Int,
        weather: Int,
        habitat: Double,
        altitude: Double,
        seasonality: Double,
        growthPhase: GrowthPhaseEvaluation?,
        dataQuality: DataQualityStatus,
        missing: List<String>,
    ): String {
        val components = mutableListOf(
            "meteo" to weather / 100.0,
            "habitat" to habitat,
            "altitudine" to altitude,
            "stagione" to seasonality,
        )
        growthPhase?.let {
            components.add("idrologia e suolo" to it.phiSoil)
        }
        val strongest = components.maxBy { it.second }.first
        val weakest = components.minBy { it.second }.first

        val stressWarning = if ((growthPhase?.phiSoil ?: 1.0) <= 0.50) {
            "Attenzione: deficit idrico superficiale modellato (sviluppo potenzialmente limitato). "
        } else if (growthPhase?.stage == GrowthStage.WANING) {
            "Finestra temporale fenologica in esaurimento. "
        } else ""

        val qualityWarning = when (dataQuality) {
            DataQualityStatus.DEGRADED_OUT_OF_BOUNDS -> "Dati ambientali anomali o fuori scala rilevati. "
            DataQualityStatus.UNAVAILABLE_INCOMPLETE_WEATHER -> "Copertura meteorologica insufficiente. "
            DataQualityStatus.DEGRADED_MISSING_SOIL -> "Dati pedologici superficiali non disponibili. "
            DataQualityStatus.DEGRADED_PARTIAL_SOIL -> "Copertura pedologica parziale. "
            DataQualityStatus.DEGRADED_INCOMPLETE_WEATHER -> "Serie meteorologica lacunosa o incompleta. "
            DataQualityStatus.OPTIMAL -> ""
        }

        val allMissing = missing.toMutableList()
        if (dataQuality == DataQualityStatus.DEGRADED_MISSING_SOIL && !allMissing.contains("suolo")) {
            allMissing.add("suolo")
        }
        if (dataQuality == DataQualityStatus.DEGRADED_INCOMPLETE_WEATHER && !allMissing.contains("meteo lacunoso")) {
            allMissing.add("meteo lacunoso")
        }
        val supplementary = if ("meteo lacunoso" in missing && dataQuality != DataQualityStatus.DEGRADED_INCOMPLETE_WEATHER) "Serie meteorologica lacunosa o incompleta. " else ""
        val coverage = if (allMissing.isEmpty()) {
            "Tutte le fonti ambientali sono disponibili."
        } else {
            "Fonti non disponibili: ${allMissing.joinToString()}. Il risultato è parziale."
        }
        return "${stressWarning}${qualityWarning}${supplementary}Indice di idoneità stimato $probability/100. Fattore più favorevole: $strongest; principale limite: $weakest. $coverage"
    }

    private fun emptyDay() = ProcessedDay("", 0.0, 0.0, 0.0, null, null, null, null)

    private fun emptyResult(dataQuality: DataQualityStatus): AnalysisResult {
        return AnalysisResult(
            probability = 0,
            tier = ProbabilityTier.VERY_LOW,
            weatherScore = 0,
            habitatScore = 0.0,
            altitudeScore = 0.0,
            seasonalityScore = 0.0,
            terrain = TerrainAspect(0.0, 0.0, 0.0, "Non disponibile", 1.0),
            factors = emptyList(),
            dailyOutlooks = emptyList(),
            deterministicFieldNote = if (dataQuality == DataQualityStatus.UNAVAILABLE_INCOMPLETE_WEATHER) "Indice non calcolabile: copertura oraria meteo insufficiente." else "Analisi non calcolabile: dati ambientali non disponibili o non validi.",
            missingSources = listOf("dati meteo"),
            growthPhase = null,
            dataQuality = dataQuality,
            waterDiagnosis = "Serie assente o non valida",
            effectiveRainMm = 0.0,
        )
    }

    private fun validWeatherDay(day: ProcessedDay): Boolean = isValidIsoDate(day.dateIso) &&
        day.avgTemp.isFinite() && day.avgTemp in -40.0..50.0 && day.minTemp.isFinite() && day.minTemp in -40.0..50.0 &&
        day.maxTemp.isFinite() && day.maxTemp in -40.0..50.0 && day.minTemp <= day.avgTemp && day.avgTemp <= day.maxTemp &&
        day.totalPrecipMm.isFinite() && day.totalPrecipMm in 0.0..500.0 &&
        day.avgHumidityPercent.isFinite() && day.avgHumidityPercent in 0.0..100.0 &&
        (day.soilMoisture0To7?.let { it.isFinite() && it in 0.0..0.60 } != false) &&
        (day.soilMoisture7To28?.let { it.isFinite() && it in 0.0..0.60 } != false) &&
        (day.evapotranspiration?.let { it.isFinite() && it >= 0.0 } != false)

    private fun isValidIsoDate(date: String): Boolean {
        if (date.length != 10 || date[4] != '-' || date[7] != '-') return false
        val year = date.substring(0, 4).toIntOrNull() ?: return false
        val month = date.substring(5, 7).toIntOrNull() ?: return false
        val day = date.substring(8, 10).toIntOrNull() ?: return false
        if (year !in 1900..2100 || month !in 1..12 || day !in 1..31) return false
        val maxDays = when (month) {
            2 -> if ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
        return day <= maxDays
    }

    private fun oneDecimal(value: Double): String = ((value * 10.0).roundToInt() / 10.0).toString()

    private fun twoDecimals(value: Double): String = ((value * 100.0).roundToInt() / 100.0).toString()

    private fun Double.roundToIntText(): String = roundToInt().toString()
}
