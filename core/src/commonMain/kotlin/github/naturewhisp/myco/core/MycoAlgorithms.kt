package github.naturewhisp.myco.core

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

object MycoAlgorithms {
    fun applyCanopyBuffering(
        day: ProcessedDay,
        canopyCover: Double = 0.80,
    ): ProcessedDay {
        val c = canopyCover.coerceIn(0.0, 1.0)
        if (c <= 0.001) return day

        val rawMax = day.maxTemp
        val rawMin = day.minTemp
        val rawAvg = day.avgTemp

        // 1. Cooling diurno sulle massime
        val excessMax = max(0.0, rawMax - 18.0)
        val deltaMax = c * min(4.0, 1.0 + 0.15 * excessMax)
        val subMaxTemp = rawMax - deltaMax

        // 2. Warming notturno sulle minime
        val coldDeficit = smoothstep(0.0, 10.0, 10.0 - rawMin)
        val deltaMin = c * (1.2 + 0.5 * coldDeficit)
        val subMinTemp = rawMin + deltaMin

        // 3. Attenuazione DTR
        val subDtr = max(0.5, subMaxTemp - subMinTemp)
        val rawDtr = max(0.5, rawMax - rawMin)
        val dtrRatio = (subDtr / rawDtr).coerceIn(0.4, 1.0)
        val subAvgTemp = rawMin + deltaMin + (rawAvg - rawMin) * dtrRatio

        // 4. Intercettazione chioma e Throughfall
        val precip = day.liquidPrecipMm
        val throughfall = if (precip > 0.0) {
            val lossFraction = c * (0.15 + 0.20 * kotlin.math.exp(-precip / 8.0))
            (precip * (1.0 - lossFraction)).coerceAtLeast(0.0)
        } else {
            0.0
        }

        // 5. Umidità relativa sub-canopy
        val rawHum = day.avgHumidityPercent
        val humBoost = c * 6.0 * (1.0 - rawHum / 100.0)
        val subHumidity = min(100.0, rawHum + humBoost)

        return day.copy(
            avgTemp = subAvgTemp,
            minTemp = subMinTemp,
            maxTemp = subMaxTemp,
            totalPrecipMm = throughfall,
            avgHumidityPercent = subHumidity,
        )
    }

    fun applyCanopyBuffering(
        days: List<ProcessedDay>,
        canopyCover: Double = 0.80,
    ): List<ProcessedDay> {
        if (canopyCover <= 0.001) return days
        return days.map { applyCanopyBuffering(it, canopyCover) }
    }

    fun phenologyKernel(
        tauDays: Double,
        tauPeak: Double,
        alpha: Double = 4.0,
    ): Double {
        if (tauDays <= 0.0 || tauPeak <= 0.0) return 0.0
        val ratio = tauDays / tauPeak
        return ratio.pow(alpha) * kotlin.math.exp(-alpha * (ratio - 1.0))
    }

    fun calculateEffectiveRainfall(
        dayIndex: Int,
        days: List<ProcessedDay>,
        species: MushroomSpecies,
    ): Double {
        if (dayIndex !in days.indices) return 0.0
        val maxMemoryDays = 26
        val chillingStart = max(0, dayIndex - 14)
        val chillingWindow = days.slice(chillingStart until dayIndex)
        val hasChilling = chillingWindow.any { it.minTemp < species.toleratedTempMin }
        val effectiveTauPeak = if (hasChilling) {
            species.phenologyLatencyPeakDays + 2.0
        } else {
            species.phenologyLatencyPeakDays
        }
        val memoryWindowStart = max(0, dayIndex - maxMemoryDays)
        var weightedRain = 0.0
        val pastDeepSoilList = mutableListOf<Double>()
        for (i in memoryWindowStart until dayIndex) {
            val tau = (dayIndex - i).toDouble()
            val precip = days[i].liquidPrecipMm
            if (precip > 0.0) {
                val weight = phenologyKernel(
                    tauDays = tau,
                    tauPeak = effectiveTauPeak,
                    alpha = species.phenologyShapeAlpha,
                )
                weightedRain += precip * weight
            }
            days[i].soilMoisture7To28?.let { pastDeepSoilList.add(it) }
        }
        val avgPastDeepSoil = if (pastDeepSoilList.isNotEmpty()) pastDeepSoilList.average() else null
        val comp = deepSoilMoistureCompensation(avgPastDeepSoil)
        return (weightedRain * comp).coerceAtLeast(0.0)
    }

    fun calculateDtrPenalty(dtr: Double, usePhenologicalInertia: Boolean = true): Double {
        return if (usePhenologicalInertia) {
            1.0 - 0.20 * smoothstep(12.0, 18.0, dtr)
        } else {
            if (dtr > 15.0) 0.8 else 1.0
        }
    }

    fun evaluateGrowthPhase(
        processedData: List<ProcessedDay>,
        species: MushroomSpecies,
        dayIndex: Int,
    ): GrowthPhaseEvaluation {
        val effectiveToday = min(dayIndex, processedData.size - 1)
        if (effectiveToday < 0 || processedData.isEmpty()) {
            return GrowthPhaseEvaluation(
                phaseText = "Fase: Dati insufficienti per il calcolo fenologico.",
                multiplier = 0.25,
                stage = GrowthStage.WAITING_FOR_RAIN,
            )
        }

        val tauPeak = species.phenologyLatencyPeakDays
        val hydrationThreshold = max(2, (0.35 * tauPeak).roundToInt())
        val incubationThreshold = max(hydrationThreshold + 1, (0.75 * tauPeak).roundToInt())
        val fruitingThreshold = max(incubationThreshold + 1, (1.35 * tauPeak).roundToInt())
        val maxLookback = max(0, effectiveToday - (2.5 * tauPeak).roundToInt())

        val candidateEvents = extractCandidateRainEvents(processedData, effectiveToday, maxLookback)
        if (candidateEvents.isEmpty()) {
            return GrowthPhaseEvaluation(
                phaseText = "Fase: Crescita assente (in attesa di precipitazioni).",
                multiplier = 0.25,
                stage = GrowthStage.WAITING_FOR_RAIN,
            )
        }

        val distinctEvents = clusterRainEvents(candidateEvents)
        val recentTrigger = distinctEvents.first()
        val earlierCandidate = distinctEvents.drop(1).firstOrNull { earlier ->
            val daysSinceEarlier = effectiveToday - earlier.triggerIndex
            daysSinceEarlier in (hydrationThreshold + 1)..fruitingThreshold && earlier.rainAmount > 15.0
        }

        val evalRecent = evaluateStageFromTrigger(
            activeTrigger = recentTrigger,
            effectiveToday = effectiveToday,
            species = species,
            hydrationThreshold = hydrationThreshold,
            incubationThreshold = incubationThreshold,
            fruitingThreshold = fruitingThreshold,
            tauPeak = tauPeak,
        )

        val baseEval: GrowthPhaseEvaluation
        val activeTriggerForDrought: RainTrigger

        if (earlierCandidate == null) {
            baseEval = evalRecent
            activeTriggerForDrought = recentTrigger
        } else {
            val evalEarlier = evaluateStageFromTrigger(
                activeTrigger = earlierCandidate,
                effectiveToday = effectiveToday,
                species = species,
                hydrationThreshold = hydrationThreshold,
                incubationThreshold = incubationThreshold,
                fruitingThreshold = fruitingThreshold,
                tauPeak = tauPeak,
            )

            val saturationFactor = smoothstep(15.0, 25.0, earlierCandidate.rainAmount)
            val ratio = recentTrigger.rainAmount / earlierCandidate.rainAmount.coerceAtLeast(1.0)
            val transition = smoothstep(0.50, 0.90, ratio)
            val weightEarlier = (1.0 - transition) * saturationFactor
            val blendedMultiplier = weightEarlier * evalEarlier.multiplier + (1.0 - weightEarlier) * evalRecent.multiplier

            baseEval = if (weightEarlier >= 0.5) {
                evalEarlier.copy(multiplier = blendedMultiplier)
            } else {
                evalRecent.copy(multiplier = blendedMultiplier)
            }
            activeTriggerForDrought = if (weightEarlier >= 0.5) earlierCandidate else recentTrigger
        }

        // Gate di Disseccamento Idrologico Superficiale (REV2-03 / Mindino Gate)
        val postTriggerStart = activeTriggerForDrought.triggerIndex + 1
        if (postTriggerStart <= effectiveToday) {
            var nDry = 0
            for (idx in effectiveToday downTo postTriggerStart) {
                if (processedData[idx].liquidPrecipMm < 1.0) {
                    nDry++
                } else {
                    break
                }
            }

            val postTriggerDays = processedData.subList(postTriggerStart, effectiveToday + 1)
            val soilValues = postTriggerDays.mapNotNull { it.soilMoisture0To7 }
            if (nDry >= 5 && soilValues.isNotEmpty()) {
                val avgSoil0To7 = soilValues.average()
                val recentSoil = postTriggerDays.takeLast(min(5, postTriggerDays.size)).mapNotNull { it.soilMoisture0To7 }
                val currentSoil = postTriggerDays.lastOrNull()?.soilMoisture0To7
                val effectiveSoil = listOfNotNull(avgSoil0To7, if (recentSoil.isNotEmpty()) recentSoil.average() else null, currentSoil).minOrNull() ?: avgSoil0To7
                if (effectiveSoil <= 0.22) {
                    val phiDrought = smoothstep(0.14, 0.22, effectiveSoil).coerceIn(0.20, 1.0)
                    val decayedMultiplier = baseEval.multiplier * phiDrought
                    return if (phiDrought <= 0.50) {
                        baseEval.copy(
                            phaseText = "Fase: Stress idrico e disseccamento superficiale (primordi compromessi dalla siccità).",
                            multiplier = decayedMultiplier,
                            stage = GrowthStage.WANING,
                        )
                    } else {
                        baseEval.copy(multiplier = decayedMultiplier)
                    }
                }
            }
        }

        return baseEval
    }

    internal fun extractCandidateRainEvents(
        processedData: List<ProcessedDay>,
        effectiveToday: Int,
        maxLookback: Int,
    ): List<RainTrigger> {
        val candidateEvents = mutableListOf<RainTrigger>()
        var i = effectiveToday
        while (i >= maxLookback) {
            val precip = processedData[i].liquidPrecipMm
            if (precip >= 10.0) {
                candidateEvents.add(RainTrigger(i, precip))
                i--
            } else if (precip >= 0.5 && i >= 2) {
                val threeDayRain = processedData[i].liquidPrecipMm +
                    processedData[i - 1].liquidPrecipMm +
                    processedData[i - 2].liquidPrecipMm
                if (threeDayRain >= 17.5) {
                    candidateEvents.add(RainTrigger(i - 2, threeDayRain))
                    i -= 3
                } else if (i >= 4) {
                    val fiveDayRain = threeDayRain +
                        processedData[i - 3].liquidPrecipMm +
                        processedData[i - 4].liquidPrecipMm
                    if (fiveDayRain >= 24.0) {
                        candidateEvents.add(RainTrigger(i - 4, fiveDayRain))
                        i -= 5
                    } else {
                        i--
                    }
                } else {
                    i--
                }
            } else {
                i--
            }
        }
        return candidateEvents
    }

    internal fun clusterRainEvents(events: List<RainTrigger>): List<RainTrigger> {
        if (events.isEmpty()) return emptyList()
        val distinctEvents = mutableListOf<RainTrigger>()
        var currentClusterTriggerIndex = events[0].triggerIndex
        var currentClusterRain = events[0].rainAmount

        for (k in 1 until events.size) {
            val ev = events[k]
            if (kotlin.math.abs(ev.triggerIndex - currentClusterTriggerIndex) <= 2) {
                currentClusterRain += ev.rainAmount
                currentClusterTriggerIndex = min(currentClusterTriggerIndex, ev.triggerIndex)
            } else {
                distinctEvents.add(RainTrigger(currentClusterTriggerIndex, currentClusterRain))
                currentClusterTriggerIndex = ev.triggerIndex
                currentClusterRain = ev.rainAmount
            }
        }
        distinctEvents.add(RainTrigger(currentClusterTriggerIndex, currentClusterRain))
        return distinctEvents
    }

    internal fun evaluateStageFromTrigger(
        activeTrigger: RainTrigger,
        effectiveToday: Int,
        species: MushroomSpecies,
        hydrationThreshold: Int,
        incubationThreshold: Int,
        fruitingThreshold: Int,
        tauPeak: Double,
    ): GrowthPhaseEvaluation {
        val daysSince = effectiveToday - activeTrigger.triggerIndex
        val multiplier: Double
        val phaseText: String
        val stage: GrowthStage

        when {
            daysSince <= hydrationThreshold -> {
                stage = GrowthStage.MYCELIAL_HYDRATION
                multiplier = 0.35 + 0.15 * (daysSince.toDouble() / hydrationThreshold.coerceAtLeast(1))
                phaseText = "Fase: Idratazione miceliare (${daysSince} gg dall'innesco)."
            }
            daysSince <= incubationThreshold -> {
                stage = GrowthStage.PRIMORDIA_INCUBATION
                val span = (incubationThreshold - hydrationThreshold).coerceAtLeast(1)
                multiplier = 0.50 + 0.35 * ((daysSince - hydrationThreshold).toDouble() / span)
                phaseText = "Fase: Incubazione primordi (${daysSince} gg dall'innesco)."
            }
            daysSince <= fruitingThreshold -> {
                stage = GrowthStage.ACTIVE_FRUITING
                multiplier = if (daysSince.toDouble() <= tauPeak) {
                    val span = (tauPeak - incubationThreshold).coerceAtLeast(1.0)
                    0.85 + 0.15 * ((daysSince - incubationThreshold).toDouble() / span)
                } else {
                    val span = (fruitingThreshold - tauPeak).coerceAtLeast(1.0)
                    1.00 - 0.15 * ((daysSince - tauPeak) / span)
                }
                phaseText = "Fase: Buttata attiva e culmine epigeo (${daysSince} gg dall'innesco)."
            }
            else -> {
                stage = GrowthStage.WANING
                val extraDays = (daysSince - fruitingThreshold).toDouble()
                multiplier = (0.70 - 0.05 * extraDays).coerceIn(0.30, 0.70)
                phaseText = "Fase: Flusso in esaurimento (${daysSince} gg dall'innesco)."
            }
        }

        return GrowthPhaseEvaluation(
            phaseText = phaseText,
            multiplier = multiplier.coerceIn(0.20, 1.0),
            daysSinceTrigger = daysSince,
            stage = stage,
        )
    }

    fun weatherScore(
        dayIndex: Int,
        days: List<ProcessedDay>,
        species: MushroomSpecies,
        spunHyphalDensity: Double?,
        applySpunHyphalBonus: Boolean = false,
        usePhenologicalInertia: Boolean = true,
        canopyCover: Double = 0.0,
    ): Int {
        if (dayIndex !in days.indices) return 0

        val effectiveCanopy = canopyCover.coerceIn(0.0, 1.0)
        val effectiveData = if (effectiveCanopy > 0.001) applyCanopyBuffering(days, effectiveCanopy) else days

        val effectiveRain: Double
        if (usePhenologicalInertia) {
            effectiveRain = calculateEffectiveRainfall(dayIndex, effectiveData, species)
        } else {
            val rainStart = max(0, dayIndex - 10)
            val rainEnd = max(0, dayIndex - 2)
            val rainWindow = if (rainStart < rainEnd && rainEnd <= effectiveData.size) {
                effectiveData.slice(rainStart until rainEnd)
            } else {
                emptyList()
            }
            effectiveRain = rainWindow.sumOf { it.liquidPrecipMm }
        }

        val effectiveSpunBonus = applySpunHyphalBonus && (!usePhenologicalInertia || species.category == EcologicalCategory.ECTOMYCORRHIZAL)
        var rainScore = rainResponse(effectiveRain, species) * 40.0
        if (effectiveSpunBonus && spunHyphalDensity != null && spunHyphalDensity >= 5.0 && effectiveRain >= 12.0) {
            rainScore = min(40.0, rainScore + 6.0)
        } else if (effectiveSpunBonus && spunHyphalDensity != null && spunHyphalDensity < 2.5) {
            rainScore = max(0.0, rainScore - 4.0)
        }

        val tempStart = max(0, dayIndex - 5)
        val tempWindow = if (tempStart < dayIndex && dayIndex <= effectiveData.size) {
            effectiveData.slice(tempStart until dayIndex)
        } else {
            emptyList()
        }
        val avgTempLast5Days = if (tempWindow.isNotEmpty()) tempWindow.sumOf { it.avgTemp } / tempWindow.size else 0.0
        val minTempRecent = if (tempWindow.isNotEmpty()) tempWindow.minOf { it.minTemp } else avgTempLast5Days
        val nocturnalInhibition = nocturnalChillingInhibition(minTempRecent, species)

        val currentDay = if (dayIndex < effectiveData.size) effectiveData[dayIndex] else effectiveData.lastOrNull()
        val dtr = if (currentDay != null) currentDay.maxTemp - currentDay.minTemp else 0.0
        val dtrPenalty = calculateDtrPenalty(dtr, usePhenologicalInertia)

        val effectiveTempScore: Double
        if (usePhenologicalInertia && dayIndex >= 10) {
            val mediumStart = max(0, dayIndex - 20)
            val mediumEnd = max(0, dayIndex - 5)
            val mediumWindow = if (mediumStart < mediumEnd && mediumEnd <= effectiveData.size) {
                effectiveData.slice(mediumStart until mediumEnd)
            } else {
                emptyList()
            }
            val avgTempMedium = if (mediumWindow.isNotEmpty()) mediumWindow.sumOf { it.avgTemp } / mediumWindow.size else avgTempLast5Days
            val mediumScore = ctmi(
                temp = avgTempMedium,
                tMin = species.toleratedTempMin,
                tOpt = species.optimalTemp,
                tMax = species.toleratedTempMax,
            )
            val shortScore = temperatureResponse(avgTempLast5Days, species)
            effectiveTempScore = 0.75 * shortScore + 0.25 * mediumScore
        } else {
            effectiveTempScore = temperatureResponse(avgTempLast5Days, species)
        }

        val tempScore = effectiveTempScore * 30.0 * nocturnalInhibition * dtrPenalty

        val humStart = max(0, dayIndex - 3)
        val humEnd = min(effectiveData.size, dayIndex + 1)
        val humWindow = if (humStart < humEnd) effectiveData.slice(humStart until humEnd) else emptyList()
        val avgHum = if (humWindow.isNotEmpty()) humWindow.sumOf { it.avgHumidityPercent } / humWindow.size else 0.0

        val soil0To7Vals = humWindow.mapNotNull { it.soilMoisture0To7 }
        val soil7To28Vals = humWindow.mapNotNull { it.soilMoisture7To28 }
        val et0Vals = humWindow.mapNotNull { it.evapotranspiration }
        val hasSoilMoisture = soil0To7Vals.isNotEmpty() || soil7To28Vals.isNotEmpty()

        val humScore = if (hasSoilMoisture) {
            val avgSoil0To7 = if (soil0To7Vals.isNotEmpty()) soil0To7Vals.average() else null
            val avgSoil7To28 = if (soil7To28Vals.isNotEmpty()) soil7To28Vals.average() else null
            val avgET0 = if (et0Vals.isNotEmpty()) et0Vals.average() else null
            val soilNorm = soilMoistureResponse(avgSoil0To7, avgSoil7To28, avgET0)
            val airHumNorm = humidityResponse(avgHum)
            (0.40 * airHumNorm + 0.60 * soilNorm) * 15.0
        } else {
            humidityResponse(avgHum) * 15.0
        }

        var shockScore = 0.0
        if (usePhenologicalInertia) {
            if (dayIndex >= 2 && effectiveRain >= 12.0) {
                var bestShock = 0.0
                for (j in 2 until dayIndex) {
                    val tempBefore = effectiveData[max(0, j - 3)].avgTemp
                    val tempAfter = effectiveData[j].avgTemp
                    val drop = tempBefore - tempAfter
                    val minDrop = if (effectiveSpunBonus && spunHyphalDensity != null && spunHyphalDensity >= 5.0) 2.0 else 3.0
                    if (drop > minDrop) {
                        val tau = (dayIndex - j).toDouble()
                        val phenoWeight = phenologyKernel(
                            tauDays = tau,
                            tauPeak = species.phenologyLatencyPeakDays,
                            alpha = species.phenologyShapeAlpha,
                        )
                        val dropFactor = ((drop - minDrop) / 3.0).coerceIn(0.0, 1.0)
                        val rainFactor = (effectiveRain / 25.0).coerceIn(0.0, 1.0)
                        val candidateShock = 15.0 * dropFactor * rainFactor * phenoWeight
                        if (candidateShock > bestShock) {
                            bestShock = candidateShock
                        }
                    }
                }
                shockScore = bestShock
            }
        } else {
            if (dayIndex > 4 && effectiveRain >= 12.0) {
                val drop = effectiveData[dayIndex - 4].avgTemp - effectiveData[dayIndex - 1].avgTemp
                val minimumDrop = if (effectiveSpunBonus && spunHyphalDensity != null && spunHyphalDensity >= 5.0) 2.0 else 3.0
                if (drop > minimumDrop) {
                    shockScore = 15.0 * ((drop - minimumDrop) / 3.0).coerceIn(0.0, 1.0) *
                        (effectiveRain / 25.0).coerceIn(0.0, 1.0)
                }
            }
        }

        val rawWeather = (rainScore + tempScore + humScore + shockScore)
        val thermalViability = when {
            avgTempLast5Days < species.toleratedTempMin -> {
                smoothstep(species.toleratedTempMin - 3.0, species.toleratedTempMin, avgTempLast5Days)
            }
            avgTempLast5Days > species.toleratedTempMax -> {
                1.0 - smoothstep(species.toleratedTempMax, species.toleratedTempMax + 3.0, avgTempLast5Days)
            }
            else -> 1.0
        }

        return (rawWeather * thermalViability).coerceIn(0.0, 100.0).roundToInt()
    }

    fun deepSoilMoistureCompensation(historicalDeepSoil: Double?): Double {
        if (historicalDeepSoil == null) return 1.0
        return when {
            historicalDeepSoil < 0.12 -> 0.70
            historicalDeepSoil < 0.20 -> 0.70 + 0.30 * smoothstep(0.12, 0.20, historicalDeepSoil)
            historicalDeepSoil <= 0.35 -> 1.0 + 0.10 * smoothstep(0.20, 0.28, historicalDeepSoil)
            historicalDeepSoil < 0.44 -> 1.10 - 0.10 * smoothstep(0.35, 0.44, historicalDeepSoil)
            else -> 1.0 - 0.15 * smoothstep(0.44, 0.52, historicalDeepSoil)
        }.coerceIn(0.70, 1.15)
    }

    fun canopyCoverToBasalArea(canopyCover: Double): Double {
        val c = canopyCover.coerceIn(0.0, 1.0)
        if (c <= 0.001) return 0.0
        return 50.0 * c.pow(1.15)
    }

    fun standDensityResponseUnimodal(canopyCover: Double, species: MushroomSpecies): Double {
        if (species.category == EcologicalCategory.SAPROTROPHIC) return 1.0
        val g = canopyCoverToBasalArea(canopyCover)
        val gOpt = species.optimalBasalAreaM2Ha
        if (g <= 0.5 || gOpt <= 0.5) return 0.65
        val u = sqrt(g / gOpt)
        val deltaPhi = 2.0 * (kotlin.math.ln(u) - u + 1.0)
        val gamma = 0.75
        val factor = 0.65 + 0.35 * kotlin.math.exp(gamma * deltaPhi)
        return factor.coerceIn(0.65, 1.0)
    }

    fun hurdleOccurrenceProbability(
        habitatScore: Double,
        altitudeScore: Double,
        species: MushroomSpecies,
    ): Double {
        val effectiveHab = if (species.category == EcologicalCategory.SAPROTROPHIC) {
            max(habitatScore, 0.85)
        } else {
            habitatScore
        }

        val stationSuitability = (effectiveHab * altitudeScore).coerceIn(0.0, 1.0)
        if (stationSuitability <= 0.001) return 0.0

        val sigma = (0.35 * species.hurdleStrictness).coerceIn(0.05, 0.50)
        val beta = 2.5
        val ratio = stationSuitability / sigma
        val exponent = -ratio.pow(beta)

        return (1.0 - kotlin.math.exp(exponent)).coerceIn(0.0, 1.0)
    }

    fun calculateSuitabilityScore(
        weatherScore: Int,
        habitatScore: Double,
        altitudeScore: Double,
        seasonalityScore: Double,
        terrainModifier: Double,
        growthPhaseMultiplier: Double = 1.0,
        species: MushroomSpecies? = null,
        useHurdle: Boolean = false,
    ): Double {
        val clampedWeather = weatherScore.coerceIn(0, 100)
        val clampedHabitat = habitatScore.coerceIn(0.0, 1.0)
        val clampedAltitude = altitudeScore.coerceIn(0.0, 1.0)
        val clampedSeasonality = seasonalityScore.coerceIn(0.0, 1.0)
        val clampedTerrain = terrainModifier.coerceIn(0.0, 2.0)
        val clampedPhase = growthPhaseMultiplier.coerceIn(0.0, 1.0)

        val pHurdle = if (useHurdle && species != null) {
            hurdleOccurrenceProbability(clampedHabitat, clampedAltitude, species)
        } else {
            1.0
        }

        val raw = 100.0 * (clampedWeather / 100.0).pow(1.2) * clampedHabitat * clampedAltitude *
            clampedSeasonality * clampedTerrain * clampedPhase * pHurdle
        val calibrated = if (raw > 70.0) {
            70.0 + 22.0 * kotlin.math.tanh((raw - 70.0) / 22.0)
        } else {
            raw
        }
        return calibrated.coerceIn(0.0, 100.0)
    }

    fun growthProbability(
        weatherScore: Int,
        habitatScore: Double,
        altitudeScore: Double,
        seasonalityScore: Double,
        terrainModifier: Double,
        growthPhaseMultiplier: Double = 1.0,
        species: MushroomSpecies? = null,
        useHurdle: Boolean = false,
    ): Int {
        return calculateSuitabilityScore(
            weatherScore = weatherScore,
            habitatScore = habitatScore,
            altitudeScore = altitudeScore,
            seasonalityScore = seasonalityScore,
            terrainModifier = terrainModifier,
            growthPhaseMultiplier = growthPhaseMultiplier,
            species = species,
            useHurdle = useHurdle,
        ).toInt().coerceIn(0, 100)
    }

    fun ctmi(temp: Double, tMin: Double, tOpt: Double, tMax: Double): Double {
        if (tMin >= tOpt || tOpt >= tMax) return 0.0
        if (temp <= tMin || temp >= tMax) return 0.0

        val spanMin = tOpt - tMin
        val spanMax = tMax - tOpt
        val x = (temp - tMin) / spanMin
        val y = (tMax - temp) / spanMax

        return if (spanMin <= spanMax) {
            val alpha = spanMax / spanMin
            (x * y.pow(alpha)).coerceIn(0.0, 1.0)
        } else {
            val beta = spanMin / spanMax
            (x.pow(beta) * y).coerceIn(0.0, 1.0)
        }
    }

    fun nocturnalChillingInhibition(minTemp: Double, species: MushroomSpecies): Double {
        val idealMin = species.idealTempMin
        val toleratedMin = species.toleratedTempMin
        if (minTemp >= idealMin) return 1.0
        if (minTemp <= toleratedMin) return 0.3
        return 0.3 + 0.7 * smoothstep(toleratedMin, idealMin, minTemp)
    }

    fun temperatureResponse(temp: Double, species: MushroomSpecies): Double = when {
        temp < species.toleratedTempMin || temp > species.toleratedTempMax -> 0.0
        temp in species.idealTempMin..species.idealTempMax -> 1.0
        temp < species.idealTempMin -> (temp - species.toleratedTempMin) /
            (species.idealTempMin - species.toleratedTempMin)
        else -> (species.toleratedTempMax - temp) / (species.toleratedTempMax - species.idealTempMax)
    }.coerceIn(0.0, 1.0)

    fun rainResponse(rainMm: Double, species: MushroomSpecies): Double = when {
        rainMm <= 0.0 -> 0.0
        rainMm >= species.minRainAccumulation -> 1.0
        species.minRainAccumulation > 0.0 -> rainMm / species.minRainAccumulation
        else -> 1.0
    }.coerceIn(0.0, 1.0)

    fun humidityResponse(humidity: Double): Double = when {
        humidity < 50.0 -> 0.0
        humidity >= 85.0 -> 1.0
        else -> (humidity - 50.0) / 35.0
    }.coerceIn(0.0, 1.0)

    /**
     * Risposta ecologica empirica continua al contenuto idrico del suolo su due orizzonti (0-7 cm e 7-28 cm).
     *
     * NOTA SCIENTIFICA (F07 - Pedologia Idraulica):
     * Risposta euristica basata su curve smoothstep C1 del contenuto volumetrico (theta in m3/m3).
     * Non implementa equazioni differenziali di van Genuchten (1980), non disponendo dei parametri
     * di suzione matriciale h, conducibilita K(h) o coefficienti di ritenzione locali (theta_r, theta_s, alpha, n, m).
     */
    fun soilMoistureResponse(shallow: Double?, deep: Double?, et0: Double?): Double {
        if (shallow == null && deep == null) return 1.0
        val shallowScore = shallow?.let {
            when {
                it < 0.10 -> 0.10
                it in 0.10..0.22 -> 0.10 + 0.90 * smoothstep(0.10, 0.22, it)
                it in 0.22..0.38 -> 1.0
                it in 0.38..0.44 -> 1.0 - 0.50 * smoothstep(0.38, 0.44, it)
                it in 0.44..0.52 -> 0.50 - 0.35 * smoothstep(0.44, 0.52, it)
                else -> 0.15
            }
        }
        val deepScore = deep?.let {
            when {
                it < 0.12 -> 0.20
                it in 0.12..0.20 -> 0.20 + 0.80 * smoothstep(0.12, 0.20, it)
                it in 0.20..0.35 -> 1.0
                it in 0.35..0.42 -> 1.0 - 0.45 * smoothstep(0.35, 0.42, it)
                it in 0.42..0.50 -> 0.55 - 0.35 * smoothstep(0.42, 0.50, it)
                else -> 0.20
            }
        }
        val base = when {
            shallowScore != null && deepScore != null -> 0.55 * shallowScore + 0.45 * deepScore
            shallowScore != null -> shallowScore
            deepScore != null -> deepScore
            else -> 1.0
        }
        val etModifier = if (et0 != null && et0 > 3.0) {
            val excess = (et0 - 3.0).coerceIn(0.0, 3.0) / 3.0
            1.0 - (0.15 * excess)
        } else {
            1.0
        }
        return (base * etModifier).coerceIn(0.0, 1.0)
    }

    fun altitudeScore(elevation: Double, species: MushroomSpecies): Double = when {
        elevation < species.minElevation -> {
            val decay = smoothstep(species.minElevation - 100.0, species.minElevation.toDouble(), elevation)
            0.40 + 0.20 * decay
        }
        elevation > species.maxElevation -> {
            val decay = 1.0 - smoothstep(species.maxElevation.toDouble(), species.maxElevation + 100.0, elevation)
            0.40 + 0.20 * decay
        }
        elevation in species.idealElevationMin.toDouble()..species.idealElevationMax.toDouble() -> 1.0
        elevation < species.idealElevationMin -> {
            val span = species.idealElevationMin - species.minElevation
            if (span > 0) 0.60 + 0.40 * ((elevation - species.minElevation) / span) else 0.60
        }
        else -> {
            val span = species.maxElevation - species.idealElevationMax
            if (span > 0) 0.60 + 0.40 * ((species.maxElevation - elevation) / span) else 0.60
        }
    }.coerceIn(0.0, 1.0)

    fun seasonalityScore(monthIndex: Int, species: MushroomSpecies): Double {
        if (monthIndex in species.activeMonths) return 1.0
        return if (species.activeMonths.any { abs(it - monthIndex) == 1 || abs(it - monthIndex) == 11 }) 0.6 else 0.1
    }

    fun terrain(elevations: List<Double>, deltaMeters: Double = 75.0): TerrainAspect {
        val center = elevations.firstOrNull() ?: 0.0
        if (elevations.size < 5) return TerrainAspect(center, 0.0, 0.0, "Pianeggiante", 1.0)
        val dzdx = (elevations[3] - elevations[4]) / (2.0 * deltaMeters)
        val dzdy = (elevations[1] - elevations[2]) / (2.0 * deltaMeters)
        val slope = radiansToDegrees(atan(sqrt(dzdx * dzdx + dzdy * dzdy)))
        var aspect = radiansToDegrees(atan2(-dzdx, -dzdy))
        if (aspect < 0.0) aspect += 360.0
        val direction = when {
            slope < 3.0 -> "Pianeggiante"
            aspect >= 337.5 || aspect < 22.5 -> "Nord"
            aspect < 67.5 -> "Nord-Est"
            aspect < 112.5 -> "Est"
            aspect < 157.5 -> "Sud-Est"
            aspect < 202.5 -> "Sud"
            aspect < 247.5 -> "Sud-Ovest"
            aspect < 292.5 -> "Ovest"
            else -> "Nord-Ovest"
        }
        return TerrainAspect(center, slope, aspect, direction, 1.0)
    }

    fun terrainModifier(
        terrain: TerrainAspect,
        monthIndex: Int,
        avgTemp: Double,
        species: MushroomSpecies,
    ): Double {
        if (terrain.slopeDegrees < 3.0) return 1.0
        val isNorth = terrain.aspectDegrees >= 315.0 || terrain.aspectDegrees <= 45.0
        val isSouth = terrain.aspectDegrees in 135.0..225.0
        val isEast = terrain.aspectDegrees in 45.0..135.0
        val modifier = if (species.idealTempMin >= 17.0) {
            when {
                isSouth || (isEast && terrain.aspectDegrees > 90.0) -> 1.05
                isNorth && avgTemp < 24.0 -> 0.90
                else -> 1.0
            }
        } else if (monthIndex in 5..7 || avgTemp > 21.0) {
            when {
                isNorth -> 1.05
                isSouth -> 0.90
                else -> 1.0
            }
        } else if (monthIndex in listOf(3, 9, 10, 11) || (monthIndex in listOf(4, 8) && avgTemp < 15.0) || avgTemp < 13.0) {
            when {
                isSouth -> 1.05
                isNorth -> 0.90
                else -> 1.0
            }
        } else {
            when {
                isEast -> 1.03
                isSouth -> 1.02
                else -> 1.0
            }
        }
        return if (terrain.slopeDegrees > 38.0) min(modifier, 0.92) else modifier
    }

    internal fun smoothstep(edge0: Double, edge1: Double, value: Double): Double {
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }
    private fun radiansToDegrees(value: Double): Double = value * 180.0 / kotlin.math.PI
}
