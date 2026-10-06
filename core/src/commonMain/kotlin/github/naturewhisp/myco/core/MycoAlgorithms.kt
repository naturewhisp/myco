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
    private fun positiveHermite(value: Double): Double = when {
        value <= 0.0 -> 0.0
        value >= 1.0 -> value
        else -> value * value * (2.0 - value)
    }

    private fun cappedHermite(value: Double, ceiling: Double): Double {
        if (ceiling <= 0.0) return 0.0
        if (value <= 0.5 * ceiling) return value
        if (value >= 1.5 * ceiling) return ceiling
        val t = value / ceiling - 0.5
        return ceiling * (0.5 + t - 0.5 * t * t)
    }
    fun applyCanopyBuffering(
        day: ProcessedDay,
        canopyCover: Double = 0.80,
    ): ProcessedDay {
        val c = canopyCover.coerceIn(0.0, 1.0)
        if (c == 0.0) return day

        val rawMax = day.maxTemp
        val rawMin = day.minTemp
        val rawAvg = day.avgTemp

        // 1. Attenuazione diurna massime (De Frenne offset estivo continuo C1 senza scalini a 18°C)
        val baseCooling = 0.5 * positiveHermite((rawMax - 5.0) / 13.0)
        val hotDayExtra = smoothstep(12.0, 24.0, rawMax) * cappedHermite(0.18 * positiveHermite(rawMax - 12.0), 3.5)
        val maxOffset = c * cappedHermite(baseCooling + hotDayExtra, ParameterRegistry.CANOPY_COOLING_MAX.value)

        // 2. Isolamento radiativo notturno
        val coldDeficit = smoothstep(0.0, 10.0, 10.0 - rawMin)
        val rawMinOffset = c * (1.2 + 0.5 * coldDeficit)

        // 3. Rispetto naturale del gradiente termico diurno DTR senza inversione fisica
        val rawDtr = max(0.0, rawMax - rawMin)
        val maxAllowedOffset = rawDtr * 0.45
        val effectiveMaxOffset = cappedHermite(maxOffset, maxAllowedOffset)
        val effectiveMinOffset = cappedHermite(rawMinOffset, maxAllowedOffset)

        val subMaxTemp = rawMax - effectiveMaxOffset
        val subMinTemp = rawMin + effectiveMinOffset
        val meanPosition = if (rawDtr > 0.0) (rawAvg - rawMin) / rawDtr else 0.5
        val subAvgTemp = subMinTemp + meanPosition * (subMaxTemp - subMinTemp)

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
        if (canopyCover <= 0.0) return days
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

    /**
     * Calcola i giorni trascorsi dall'epoca gregoriana/Unix (1970-01-01) per una data ISO "YYYY-MM-DD"
     * in puro Kotlin matematico senza dipendenze JVM (Howard Hinnant civil calendar algorithm).
     * Restituisce null se la stringa non è nel formato valido "YYYY-MM-DD".
     */
    fun isoDateToEpochDay(dateIso: String): Long? {
        val datePart = if (dateIso.length > 10 && (dateIso[10] == 'T' || dateIso[10] == ' ')) {
            dateIso.substring(0, 10)
        } else {
            dateIso
        }
        if (datePart.length != 10 || datePart[4] != '-' || datePart[7] != '-') return null
        val year = datePart.substring(0, 4).toLongOrNull() ?: return null
        val month = datePart.substring(5, 7).toIntOrNull() ?: return null
        val day = datePart.substring(8, 10).toLongOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null
        val maxDays = when (month) {
            2 -> if ((year % 4 == 0L && year % 100L != 0L) || (year % 400 == 0L)) 29L else 28L
            4, 6, 9, 11 -> 30L
            else -> 31L
        }
        if (day > maxDays) return null

        val y = if (month <= 2) year - 1L else year
        val m = if (month <= 2) month + 9 else month - 3
        val era = (if (y >= 0L) y else y - 399L) / 400L
        val yoe = y - era * 400L
        val doy = (153 * m + 2) / 5 + day - 1L
        val doe = yoe * 365L + yoe / 4L - yoe / 100L + doy
        return era * 146097L + doe - 719468L
    }

    /**
     * Calcola la differenza in giorni di calendario gregoriano (toIso - fromIso).
     * Se una delle due date non è valida, restituisce null.
     */
    fun daysBetween(fromIso: String, toIso: String): Long? {
        val fromEpoch = isoDateToEpochDay(fromIso) ?: return null
        val toEpoch = isoDateToEpochDay(toIso) ?: return null
        return toEpoch - fromEpoch
    }

    /**
     * Calcola la latenza in giorni di calendario tra due indici della serie temporale.
     * Fallback sulla differenza posizionale se le date ISO non sono valide o assenti.
     */
    fun calculateDaysSince(
        processedData: List<ProcessedDay>,
        todayIndex: Int,
        pastIndex: Int,
    ): Int {
        if (todayIndex in processedData.indices && pastIndex in processedData.indices) {
            val delta = daysBetween(processedData[pastIndex].dateIso, processedData[todayIndex].dateIso)
            if (delta != null) return delta.toInt()
        }
        return todayIndex - pastIndex
    }

    fun calculateEffectiveRainfall(
        dayIndex: Int,
        days: List<ProcessedDay>,
        species: MushroomSpecies,
    ): Double {
        if (dayIndex !in days.indices) return 0.0
        val maxMemoryDays = ParameterRegistry.PHENOLOGY_MEMORY_DAYS.value
        val chillingDuration = ParameterRegistry.CHILLING_DURATION_DAYS.value
        val chillingExpansion = ParameterRegistry.CHILLING_LATENCY_EXPANSION_DAYS.value
        val targetDay = days[dayIndex]
        val targetEpoch = isoDateToEpochDay(targetDay.dateIso)

        var chillingSurvival = 1.0
        for (i in 0 until dayIndex) {
            val day = days[i]
            val pastEpoch = isoDateToEpochDay(day.dateIso)
            val tau = if (targetEpoch != null && pastEpoch != null) {
                (targetEpoch - pastEpoch).toDouble()
            } else {
                (dayIndex - i).toDouble()
            }
            if (tau in 1.0..chillingDuration.toDouble()) chillingSurvival *=
                1.0 - smoothstep(0.0, 3.0, species.toleratedTempMin - day.minTemp)
        }
        val effectiveTauPeak = species.phenologyLatencyPeakDays + (1.0 - chillingSurvival) * chillingExpansion

        var weightedRain = 0.0
        val pastDeepSoilList = mutableListOf<Double>()
        for (i in 0 until dayIndex) {
            val pastDay = days[i]
            val pastEpoch = isoDateToEpochDay(pastDay.dateIso)
            val tau = if (targetEpoch != null && pastEpoch != null) {
                (targetEpoch - pastEpoch).toDouble()
            } else {
                (dayIndex - i).toDouble()
            }
            if (tau <= 0.0 || tau > maxMemoryDays.toDouble()) continue

            val precip = pastDay.liquidPrecipMm
            if (precip > 0.0) {
                val weight = phenologyKernel(
                    tauDays = tau,
                    tauPeak = effectiveTauPeak,
                    alpha = species.phenologyShapeAlpha,
                )
                weightedRain += precip * weight
            }
            pastDay.soilMoisture7To28?.let { pastDeepSoilList.add(it) }
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

    fun calculateSoilMoistureFactor(
        processedData: List<ProcessedDay>,
        effectiveToday: Int,
    ): SoilHydrologyEvaluation {
        if (effectiveToday < 0 || processedData.isEmpty()) {
            return SoilHydrologyEvaluation(
                phiSoil = 1.0,
                averageSoil0To7 = null,
                availableDaysCount = 0,
                isTargetDayPresent = false,
                diagnosisText = "Dati pedologici non disponibili.",
            )
        }
        val targetDay = processedData.getOrNull(effectiveToday)
        val targetEpoch = targetDay?.let { isoDateToEpochDay(it.dateIso) }
        if (targetEpoch == null) return SoilHydrologyEvaluation(1.0, null, 0, false, "Dati pedologici non disponibili.")
        val dayByEpoch = processedData.mapNotNull { d -> isoDateToEpochDay(d.dateIso)?.let { it to d } }.toMap()
        val retrospectiveWindow = listOfNotNull(dayByEpoch[targetEpoch - 2L], dayByEpoch[targetEpoch - 1L], dayByEpoch[targetEpoch])
        val isTargetPresent = targetDay.soilMoisture0To7 != null
        val soilValues = retrospectiveWindow.mapNotNull { it.soilMoisture0To7 }

        if (soilValues.size < 2) {
            val text = if (soilValues.isEmpty()) {
                "Diagnosi idrica non determinabile per assenza di dati pedologici"
            } else {
                "Dati pedologici insufficienti (${soilValues.size}/3 giorni nell'orizzonte superficiale)"
            }
            return SoilHydrologyEvaluation(
                phiSoil = 1.0,
                averageSoil0To7 = soilValues.firstOrNull(),
                availableDaysCount = soilValues.size,
                isTargetDayPresent = isTargetPresent,
                diagnosisText = text,
            )
        }

        val avgSoil0To7 = soilValues.average()
        val thetaMin = ParameterRegistry.SOIL_DROUGHT_MIN_THRESHOLD.value
        val thetaMax = ParameterRegistry.SOIL_DROUGHT_STRESS_THRESHOLD.value
        val yMin = ParameterRegistry.SOIL_FLOOR_FACTOR.value
        val u = ((avgSoil0To7 - thetaMin) / (thetaMax - thetaMin)).coerceIn(0.0, 1.0)
        val sU = 3.0 * u * u - 2.0 * u * u * u
        val phiSoil = yMin + (1.0 - yMin) * sU

        val roundedAvg = (avgSoil0To7 * 100.0).roundToInt() / 100.0
        val targetNote = if (!isTargetPresent) " (giorno target mancante)" else ""
        val diagnosisText = "Umidità orizzonte 0–7 cm: ${roundedAvg} m³/m³ (media retrospettiva ${soilValues.size} gg$targetNote)"

        return SoilHydrologyEvaluation(
            phiSoil = phiSoil,
            averageSoil0To7 = avgSoil0To7,
            availableDaysCount = soilValues.size,
            isTargetDayPresent = isTargetPresent,
            diagnosisText = diagnosisText,
        )
    }

    fun evaluateGrowthPhase(
        processedData: List<ProcessedDay>,
        species: MushroomSpecies,
        dayIndex: Int,
    ): GrowthPhaseEvaluation {
        val effectiveToday = min(dayIndex, processedData.size - 1)
        if (effectiveToday < 0 || processedData.isEmpty() || dayIndex !in processedData.indices || processedData.any { isoDateToEpochDay(it.dateIso) == null }) {
            return GrowthPhaseEvaluation(
                phaseText = "Fase: Dati insufficienti per il calcolo fenologico.",
                multiplier = 0.25,
                stage = GrowthStage.WAITING_FOR_RAIN,
                phiBase = 0.25,
                phiSoil = 1.0,
            )
        }

        val soilEval = calculateSoilMoistureFactor(processedData, effectiveToday)
        val tauPeak = species.phenologyLatencyPeakDays
        val hydrationThreshold = max(2, (0.35 * tauPeak).roundToInt())
        val incubationThreshold = max(hydrationThreshold + 1, (0.75 * tauPeak).roundToInt())
        val fruitingThreshold = max(incubationThreshold + 1, (1.35 * tauPeak).roundToInt())
        val lookbackDays = (2.5 * tauPeak).roundToInt()
        val maxLookback = processedData.indices.firstOrNull { calculateDaysSince(processedData, effectiveToday, it) <= lookbackDays } ?: effectiveToday

        // Continuous event ensemble: all real rain is retained; no volume threshold resets the phase.
        val events = (maxLookback..effectiveToday).mapNotNull { index ->
            val rain = processedData[index].liquidPrecipMm
            if (!rain.isFinite() || rain <= 0.0) null else RainTrigger(index, rain)
        }
        val weights = events.map { event ->
            event.rainAmount * smoothstep(0.0, ParameterRegistry.PHASE_RAIN_ACTIVATION_MM.value, event.rainAmount)
        }
        val totalWeight = weights.sum()
        val evaluations = events.map { event ->
            evaluateStageFromTrigger(event, effectiveToday, species, hydrationThreshold, incubationThreshold, fruitingThreshold, tauPeak, processedData)
        }
        val activation = smoothstep(0.0, ParameterRegistry.PHASE_RAIN_ACTIVATION_MM.value, totalWeight)
        val baseEval = if (totalWeight == 0.0) {
            GrowthPhaseEvaluation("Fase temporale potenziale: Crescita assente (in attesa di precipitazioni).", 0.25,
                stage = GrowthStage.WAITING_FOR_RAIN, phiBase = 0.25, phiSoil = soilEval.phiSoil)
        } else {
            val weightedPhase = evaluations.indices.sumOf { evaluations[it].multiplier * weights[it] } / totalWeight
            val representative = weights.indices.maxBy { weights[it] }
            evaluations[representative].copy(
                multiplier = 0.25 + activation * (weightedPhase - 0.25),
                stage = if (activation < 0.5) GrowthStage.WAITING_FOR_RAIN else evaluations[representative].stage,
            )
        }

        val finalMultiplier = baseEval.multiplier * soilEval.phiSoil
        return if (soilEval.phiSoil <= 0.50) {
            baseEval.copy(
                phaseText = "Fase temporale potenziale: Stress idrico e disseccamento superficiale (sviluppo potenzialmente limitato).",
                multiplier = finalMultiplier,
                stage = GrowthStage.WANING,
                phiBase = baseEval.multiplier,
                phiSoil = soilEval.phiSoil,
            )
        } else if (soilEval.phiSoil < 0.85) {
            baseEval.copy(
                phaseText = "${baseEval.phaseText} • Rallentamento per deficit idrico superficiale.",
                multiplier = finalMultiplier,
                phiBase = baseEval.multiplier,
                phiSoil = soilEval.phiSoil,
            )
        } else {
            baseEval.copy(
                multiplier = finalMultiplier,
                phiBase = baseEval.multiplier,
                phiSoil = soilEval.phiSoil,
            )
        }
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
            } else if (precip >= 0.5 && i >= 2 && calculateDaysSince(processedData, i, i - 2) <= 2) {
                val threeDayRain = processedData[i].liquidPrecipMm +
                    processedData[i - 1].liquidPrecipMm +
                    processedData[i - 2].liquidPrecipMm
                if (threeDayRain >= 17.5) {
                    candidateEvents.add(RainTrigger(i - 2, threeDayRain))
                    i -= 3
                } else if (i >= 4 && calculateDaysSince(processedData, i, i - 4) <= 4) {
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

    internal fun clusterRainEvents(
        events: List<RainTrigger>,
        processedData: List<ProcessedDay>? = null,
    ): List<RainTrigger> {
        if (events.isEmpty()) return emptyList()
        val distinctEvents = mutableListOf<RainTrigger>()
        var currentClusterTriggerIndex = events[0].triggerIndex
        var currentClusterRain = events[0].rainAmount

        for (k in 1 until events.size) {
            val ev = events[k]
            val dayDist = if (processedData != null) {
                kotlin.math.abs(calculateDaysSince(processedData, ev.triggerIndex, currentClusterTriggerIndex))
            } else {
                kotlin.math.abs(ev.triggerIndex - currentClusterTriggerIndex)
            }
            if (dayDist <= 2) {
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
        processedData: List<ProcessedDay>? = null,
    ): GrowthPhaseEvaluation {
        val daysSince = if (processedData != null) {
            calculateDaysSince(processedData, effectiveToday, activeTrigger.triggerIndex)
        } else {
            effectiveToday - activeTrigger.triggerIndex
        }
        val multiplier: Double
        val phaseText: String
        val stage: GrowthStage

        when {
            daysSince <= hydrationThreshold -> {
                stage = GrowthStage.MYCELIAL_HYDRATION
                multiplier = 0.35 + 0.15 * smoothstep(0.0, hydrationThreshold.toDouble(), daysSince.toDouble())
                phaseText = "Fase temporale potenziale: Idratazione miceliare (${daysSince} gg dall'innesco)."
            }
            daysSince <= incubationThreshold -> {
                stage = GrowthStage.PRIMORDIA_INCUBATION
                val span = (incubationThreshold - hydrationThreshold).coerceAtLeast(1)
                multiplier = 0.50 + 0.35 * smoothstep(0.0, span.toDouble(), (daysSince - hydrationThreshold).toDouble())
                phaseText = "Fase temporale potenziale: Incubazione primordi (${daysSince} gg dall'innesco)."
            }
            daysSince <= fruitingThreshold -> {
                stage = GrowthStage.ACTIVE_FRUITING
                multiplier = if (daysSince.toDouble() <= tauPeak) {
                    val span = (tauPeak - incubationThreshold).coerceAtLeast(1.0)
                    0.85 + 0.15 * smoothstep(0.0, span, (daysSince - incubationThreshold).toDouble())
                } else {
                    val span = (fruitingThreshold - tauPeak).coerceAtLeast(1.0)
                    1.00 - 0.15 * smoothstep(0.0, span, daysSince - tauPeak)
                }
                phaseText = "Fase temporale potenziale: Culmine teorico della finestra (${daysSince} gg dall'innesco)."
            }
            else -> {
                stage = GrowthStage.WANING
                val extraDays = (daysSince - fruitingThreshold).toDouble()
                multiplier = 0.85 - 0.55 * smoothstep(0.0, ParameterRegistry.PHASE_WANING_DAYS.value, extraDays)
                phaseText = "Fase temporale potenziale: Flusso in esaurimento (${daysSince} gg dall'innesco)."
            }
        }

        val clampedMultiplier = multiplier.coerceIn(0.20, 1.0)
        return GrowthPhaseEvaluation(
            phaseText = phaseText,
            multiplier = clampedMultiplier,
            daysSinceTrigger = daysSince,
            stage = stage,
            phiBase = clampedMultiplier,
            phiSoil = 1.0,
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
        val effectiveData = if (effectiveCanopy > 0.0) applyCanopyBuffering(days, effectiveCanopy) else days

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

        val targetDay = effectiveData.getOrNull(dayIndex)
        val targetEpoch = targetDay?.let { isoDateToEpochDay(it.dateIso) }
        val dayByEpoch = if (targetEpoch != null) {
            effectiveData.mapNotNull { d -> isoDateToEpochDay(d.dateIso)?.let { it to d } }.toMap()
        } else null

        val tempWindow = if (dayByEpoch != null && targetEpoch != null) {
            (5 downTo 1).mapNotNull { dayByEpoch[targetEpoch - it] }
        } else {
            val tempStart = max(0, dayIndex - 5)
            if (tempStart < dayIndex && dayIndex <= effectiveData.size) {
                effectiveData.slice(tempStart until dayIndex)
            } else {
                emptyList()
            }
        }
        val avgTempLast5Days = if (tempWindow.isNotEmpty()) tempWindow.sumOf { it.avgTemp } / tempWindow.size else 0.0
        val minTempRecent = if (tempWindow.isNotEmpty()) tempWindow.minOf { it.minTemp } else avgTempLast5Days
        val nocturnalInhibition = nocturnalChillingInhibition(minTempRecent, species)

        val currentDay = if (dayIndex < effectiveData.size) effectiveData[dayIndex] else effectiveData.lastOrNull()
        val dtr = if (currentDay != null) currentDay.maxTemp - currentDay.minTemp else 0.0
        val dtrPenalty = calculateDtrPenalty(dtr, usePhenologicalInertia)

        val effectiveTempScore: Double
        if (usePhenologicalInertia && ((dayByEpoch != null && targetEpoch != null) || dayIndex >= 10)) {
            val mediumWindow = if (dayByEpoch != null && targetEpoch != null) {
                (20 downTo 5).mapNotNull { dayByEpoch[targetEpoch - it] }
            } else {
                val mediumStart = max(0, dayIndex - 20)
                val mediumEnd = max(0, dayIndex - 5)
                if (mediumStart < mediumEnd && mediumEnd <= effectiveData.size) {
                    effectiveData.slice(mediumStart until mediumEnd)
                } else {
                    emptyList()
                }
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

        val humWindow = if (dayByEpoch != null && targetEpoch != null) {
            (3 downTo 0).mapNotNull { dayByEpoch[targetEpoch - it] }
        } else {
            val humStart = max(0, dayIndex - 3)
            val humEnd = min(effectiveData.size, dayIndex + 1)
            if (humStart < humEnd) effectiveData.slice(humStart until humEnd) else emptyList()
        }
        val avgHum = if (humWindow.isNotEmpty()) humWindow.sumOf { it.avgHumidityPercent } / humWindow.size else 0.0

        val soilWindow = EnvironmentalWindows.derive(effectiveData, dayIndex).soil
        val soil0To7Vals = soilWindow.mapNotNull { it.soilMoisture0To7 }
        val soil7To28Vals = soilWindow.mapNotNull { it.soilMoisture7To28 }
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
            if (dayIndex >= 2) {
                var shockSurvival = 1.0
                for (j in 2 until dayIndex) {
                    val candidateDay = effectiveData[j]
                    val candidateEpoch = isoDateToEpochDay(candidateDay.dateIso)
                    val drop = if (dayByEpoch != null && candidateEpoch != null) {
                        val priorDay = dayByEpoch[candidateEpoch - 3L]
                        if (priorDay != null) priorDay.avgTemp - candidateDay.avgTemp else null
                    } else {
                        val priorDay = effectiveData[max(0, j - 3)]
                        priorDay.avgTemp - candidateDay.avgTemp
                    }
                    val minDrop = if (effectiveSpunBonus && spunHyphalDensity != null && spunHyphalDensity >= 5.0) 2.0 else 3.0
                    if (drop != null && drop > minDrop) {
                        val tau = calculateDaysSince(effectiveData, dayIndex, j).toDouble()
                        val phenoWeight = phenologyKernel(
                            tauDays = tau,
                            tauPeak = species.phenologyLatencyPeakDays,
                            alpha = species.phenologyShapeAlpha,
                        )
                        val dropFactor = smoothstep(minDrop, minDrop + 3.0, drop)
                        val rainFactor = smoothstep(0.0, 25.0, effectiveRain)
                        val candidateShock = 15.0 * dropFactor * rainFactor * phenoWeight
                        shockSurvival *= 1.0 - candidateShock / 15.0
                    }
                }
                shockScore = 15.0 * (1.0 - shockSurvival)
            }
        } else {
            if (dayIndex > 4 && effectiveRain >= 12.0) {
                val drop = if (dayByEpoch != null && targetEpoch != null) {
                    val d4 = dayByEpoch[targetEpoch - 4L]
                    val d1 = dayByEpoch[targetEpoch - 1L]
                    if (d4 != null && d1 != null) d4.avgTemp - d1.avgTemp else null
                } else {
                    effectiveData[dayIndex - 4].avgTemp - effectiveData[dayIndex - 1].avgTemp
                }
                val minimumDrop = if (effectiveSpunBonus && spunHyphalDensity != null && spunHyphalDensity >= 5.0) 2.0 else 3.0
                if (drop != null && drop > minimumDrop) {
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
        if (c <= 0.0) return 0.0
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
        return 0.65 + (factor - 0.65) * smoothstep(0.5, 1.5, g)
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

    /**
     * Valuta l'idoneità stazionale e la compatibilità ecologica sulla base di evidenze territoriali
     * strutturate [HabitatEvidence] per qualsiasi specie e categoria ecologica (D03).
     */
    fun evaluateHabitat(
        evidence: HabitatEvidence,
        species: MushroomSpecies,
        spunEcmRichness: Double? = null,
    ): SpeciesHabitatEvaluation {
        val forest = evidence.forestCoverFraction.coerceIn(0.0, 1.0)
        val meadow = evidence.meadowFraction.coerceIn(0.0, 1.0)
        val basalArea = canopyCoverToBasalArea(forest).toFloat()
        val stand = if (evidence.status == HabitatStatus.UNKNOWN || species.category == EcologicalCategory.SAPROTROPHIC) 1.0
            else standDensityResponseUnimodal(forest, species)
        val raw = when (evidence.status) {
            HabitatStatus.UNKNOWN -> if (species.category == EcologicalCategory.PARASITIC) 0.45 else 0.50
            HabitatStatus.KNOWN_UNSUITABLE -> if (species.category == EcologicalCategory.SAPROTROPHIC) 0.15 else 0.10
            HabitatStatus.KNOWN_SUITABLE -> when (species.category) {
                EcologicalCategory.SAPROTROPHIC -> {
                    val edge = smoothstep(0.0, 0.10, forest) * (1.0 - smoothstep(0.40, 0.60, forest))
                    val open = 0.85 + 0.05 * edge - 0.10 * smoothstep(0.40, 0.60, forest)
                    open + (0.95 - open) * smoothstep(0.0, 0.25, meadow)
                }
                EcologicalCategory.PARASITIC -> 0.30 + 0.55 * smoothstep(0.0, 0.20, forest) + 0.15 * smoothstep(0.20, 0.60, forest)
                EcologicalCategory.ECTOMYCORRHIZAL -> 0.15 + 0.50 * smoothstep(0.0, 0.10, forest) +
                    0.25 * smoothstep(0.10, 0.35, forest) + 0.10 * smoothstep(0.35, 0.65, forest)
            }
        }
        val base = raw * stand
        val modifier = habitatModifier(species, evidence.confirmedHostGenera.toList(), spunEcmRichness)
        val text = when {
            evidence.status == HabitatStatus.UNKNOWN -> "Habitat: Dati geografici non disponibili (stima neutrale)."
            evidence.status == HabitatStatus.KNOWN_UNSUITABLE -> "Habitat: Contesto artificiale mappato."
            species.category == EcologicalCategory.PARASITIC -> "Habitat: Formazioni arboree mappate; necromassa, ceppaie e substrato non verificati."
            species.category == EcologicalCategory.SAPROTROPHIC -> "Habitat: Praticolo; compatibilità modellata di prati, radure e margini boschivi."
            else -> "Habitat: Compatibilità modellata delle superfici forestali e degli ospiti mappati."
        }
        return SpeciesHabitatEvaluation(
            score = applyHabitatBonusPenalty(base, modifier), baseText = text,
            bonusText = when {
                species.category == EcologicalCategory.ECTOMYCORRHIZAL && species.preferredCanopyTypes.any { pref -> evidence.confirmedHostGenera.any { it.equals(pref, ignoreCase = true) } } -> "Bonus: Rilevati alberi ospiti mappati; modificatore euristico condiviso $modifier"
                modifier == 1.0 -> "Nessuna essenza specifica o dato vegetativo rilevato."
                else -> "Modificatore EcM condiviso (prior euristico): $modifier"
            },
            basalAreaM2Ha = basalArea, standDensityScore = stand, baseScore = base,
        )
    }

    /** One non-compounding prior for correlated host and atlas evidence. */
    fun habitatModifier(species: MushroomSpecies, canopyTypes: List<String>, richness: Double?): Double {
        if (species.category != EcologicalCategory.ECTOMYCORRHIZAL) return 1.0
        val host = species.preferredCanopyTypes.any { pref -> canopyTypes.any { it.equals(pref, ignoreCase = true) } }
        if (host) return ParameterRegistry.HABITAT_HOST_MODIFIER.value
        if (richness == null) return 1.0
        return 0.8 + 0.2 * smoothstep(5.0, 25.0, richness) + 0.15 * smoothstep(40.0, 60.0, richness)
    }

    /** C1 Hermite saturation, identity below half the available headroom. */
    fun applyHabitatBonusPenalty(baseScore: Double, bonusMult: Double, floor: Double = 0.10, ceiling: Double = 1.0): Double {
        require(baseScore.isFinite() && baseScore in 0.0..ceiling && ceiling in 0.0..1.0)
        require(bonusMult.isFinite() && bonusMult >= 0.0 && floor.isFinite() && floor >= 0.0)
        fun boundedDelta(delta: Double, headroom: Double): Double {
            if (headroom == 0.0) return 0.0
            if (delta <= headroom * 0.5) return delta
            if (delta >= headroom * 1.5) return headroom
            val t = (delta - headroom * 0.5) / headroom
            return headroom * (0.5 + t - 0.5 * t * t)
        }
        return if (bonusMult >= 1.0) {
            baseScore + boundedDelta(baseScore * (bonusMult - 1.0), ceiling - baseScore)
        } else {
            val lower = if (floor == 0.0) 0.0 else floor * baseScore / (floor + baseScore)
            baseScore - boundedDelta(baseScore * (1.0 - bonusMult), baseScore - lower)
        }
    }

    fun haversineDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = (lat2 - lat1) * (kotlin.math.PI / 180.0)
        val dLon = (lon2 - lon1) * (kotlin.math.PI / 180.0)
        val a = kotlin.math.sin(dLat / 2.0) * kotlin.math.sin(dLat / 2.0) +
            kotlin.math.cos(lat1 * (kotlin.math.PI / 180.0)) * kotlin.math.cos(lat2 * (kotlin.math.PI / 180.0)) *
            kotlin.math.sin(dLon / 2.0) * kotlin.math.sin(dLon / 2.0)
        val c = 2.0 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1.0 - a))
        return r * c
    }

    fun extractHabitatEvidence(
        elements: List<OsmHabitatElement>?,
        target: GeoCoordinates,
        searchRadiusMeters: Int = 1500,
    ): HabitatEvidence = extractHabitatEvidence(
        elements = elements,
        targetLat = target.latitude,
        targetLon = target.longitude,
        searchRadiusMeters = searchRadiusMeters,
    )

    fun extractHabitatEvidence(
        elements: List<OsmHabitatElement>?,
        targetLat: Double,
        targetLon: Double,
        searchRadiusMeters: Int = 1500,
    ): HabitatEvidence {
        return HabitatGeometry.extract(elements.orEmpty(), GeoCoordinates(targetLat, targetLon), searchRadiusMeters)
    }

    fun evaluateHabitat(
        evidence: HabitatEvidence,
        species: MushroomSpecies,
    ): SpeciesHabitatEvaluation = evaluateHabitat(evidence, species, null)

    fun evaluateHabitat(
        forestCount: Int,
        specificElementsCount: Int = 0,
        species: MushroomSpecies = SpeciesCatalog.all[0],
        spunEcmRichness: Double? = null,
        canopyCover: Double? = null,
    ): SpeciesHabitatEvaluation {
        val effectiveCanopy = canopyCover ?: when {
            forestCount >= 15 -> 0.85
            forestCount >= 5 -> 0.70
            forestCount >= 1 -> 0.45
            else -> 0.0
        }
        val evidence = HabitatEvidence(
            status = HabitatStatus.KNOWN_SUITABLE,
            forestCoverFraction = effectiveCanopy,
            meadowFraction = if (forestCount == 0) 0.80 else 0.10,
            distanceToNearestForestMeters = if (forestCount > 0) 0.0 else 500.0,
            confirmedHostGenera = if (specificElementsCount > 0) species.preferredCanopyTypes.toSet() else emptySet(),
        )
        return evaluateHabitat(evidence, species, spunEcmRichness)
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
            (x * y.pow(alpha)).pow(ParameterRegistry.CARDINAL_BOUNDARY_POWER.value).coerceIn(0.0, 1.0)
        } else {
            val beta = spanMin / spanMax
            (x.pow(beta) * y).pow(ParameterRegistry.CARDINAL_BOUNDARY_POWER.value).coerceIn(0.0, 1.0)
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
        val etModifier = if (et0 == null) 1.0 else 1.0 - 0.15 * smoothstep(3.0, 6.0, et0)
        return (base * etModifier).coerceIn(0.0, 1.0)
    }

    fun altitudeScore(elevation: Double, species: MushroomSpecies): Double = when {
        elevation < species.minElevation -> {
            val decay = smoothstep(species.minElevation - 100.0, species.minElevation.toDouble(), elevation)
            0.40 + (if (species.idealElevationMin == species.minElevation) 0.60 else 0.20) * decay
        }
        elevation > species.maxElevation -> {
            val decay = 1.0 - smoothstep(species.maxElevation.toDouble(), species.maxElevation + 100.0, elevation)
            0.40 + (if (species.idealElevationMax == species.maxElevation) 0.60 else 0.20) * decay
        }
        elevation in species.idealElevationMin.toDouble()..species.idealElevationMax.toDouble() -> 1.0
        elevation < species.idealElevationMin -> {
            val span = species.idealElevationMin - species.minElevation
            if (span > 0) 0.60 + 0.40 * smoothstep(species.minElevation.toDouble(), species.idealElevationMin.toDouble(), elevation) else 1.0
        }
        else -> {
            val span = species.maxElevation - species.idealElevationMax
            if (span > 0) 0.60 + 0.40 * (1.0 - smoothstep(species.idealElevationMax.toDouble(), species.maxElevation.toDouble(), elevation)) else 1.0
        }
    }.coerceIn(0.0, 1.0)

    fun seasonalityScore(monthIndex: Int, species: MushroomSpecies): Double {
        if (monthIndex in species.activeMonths) return 1.0
        return if (species.activeMonths.any { abs(it - monthIndex) == 1 || abs(it - monthIndex) == 11 }) 0.6 else 0.1
    }

    fun terrain(elevations: List<Double>, deltaMeters: Double = 75.0): TerrainAspect {
        val center = elevations.firstOrNull() ?: 0.0
        if (elevations.size < 5) return TerrainAspect(center, 0.0, 0.0, "Non disponibile", 1.0)
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
        if (terrain.slopeDegrees < 3.0 || terrain.cardinalDirection == "Non disponibile") return 1.0
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
