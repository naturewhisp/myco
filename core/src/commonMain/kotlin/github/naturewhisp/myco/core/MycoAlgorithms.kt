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
    fun weatherScore(
        dayIndex: Int,
        days: List<ProcessedDay>,
        species: MushroomSpecies,
        spunHyphalDensity: Double?,
    ): Int {
        if (dayIndex !in days.indices) return 0

        val windows = EnvironmentalWindows.derive(days, dayIndex)
        val totalRain = windows.rainWindowTotalMm
        var rainScore = rainResponse(totalRain, species) * 40.0
        if (spunHyphalDensity != null && spunHyphalDensity >= 5.0 && totalRain >= 12.0) {
            rainScore = min(40.0, rainScore + 6.0)
        } else if (spunHyphalDensity != null && spunHyphalDensity < 2.5) {
            rainScore = max(0.0, rainScore - 4.0)
        }

        val minTempRecent = if (windows.temperature.isNotEmpty()) {
            windows.temperature.minOf { it.avgTemp }
        } else {
            windows.averageTempWindowC
        }
        val nocturnalInhibition = nocturnalChillingInhibition(minTempRecent, species)

        val tempScore = temperatureResponse(windows.averageTempWindowC, species) * 30.0 * nocturnalInhibition

        val humidityScore = if (windows.averageSoil0To7 != null || windows.averageSoil7To28 != null) {
            val soil = soilMoistureResponse(windows.averageSoil0To7, windows.averageSoil7To28, windows.averageEt0)
            (0.40 * humidityResponse(windows.averageHumidityWindowPercent) + 0.60 * soil) * 15.0
        } else {
            humidityResponse(windows.averageHumidityWindowPercent) * 15.0
        }

        var shockScore = 0.0
        if (dayIndex > 4 && totalRain >= 12.0) {
            val drop = windows.temperatureDropC ?: 0.0
            val minimumDrop = if (spunHyphalDensity != null && spunHyphalDensity >= 5.0) 2.0 else 3.0
            if (drop > minimumDrop) {
                shockScore = 15.0 * ((drop - minimumDrop) / 3.0).coerceIn(0.0, 1.0) *
                    (totalRain / 25.0).coerceIn(0.0, 1.0)
            }
        }

        return (rainScore + tempScore + humidityScore + shockScore).coerceIn(0.0, 100.0).roundToInt()
    }

    fun calculateSuitabilityScore(
        weatherScore: Int,
        habitatScore: Double,
        altitudeScore: Double,
        seasonalityScore: Double,
        terrainModifier: Double,
        growthPhaseMultiplier: Double = 1.0,
    ): Double {
        val clampedWeather = weatherScore.coerceIn(0, 100)
        val clampedHabitat = habitatScore.coerceIn(0.0, 1.0)
        val clampedAltitude = altitudeScore.coerceIn(0.0, 1.0)
        val clampedSeasonality = seasonalityScore.coerceIn(0.0, 1.0)
        val clampedTerrain = terrainModifier.coerceIn(0.0, 2.0)
        val clampedPhase = growthPhaseMultiplier.coerceIn(0.0, 1.0)

        val raw = 100.0 * (clampedWeather / 100.0).pow(1.2) * clampedHabitat * clampedAltitude *
            clampedSeasonality * clampedTerrain * clampedPhase
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
    ): Int {
        return calculateSuitabilityScore(
            weatherScore = weatherScore,
            habitatScore = habitatScore,
            altitudeScore = altitudeScore,
            seasonalityScore = seasonalityScore,
            terrainModifier = terrainModifier,
            growthPhaseMultiplier = growthPhaseMultiplier,
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

    fun soilMoistureResponse(shallow: Double?, deep: Double?, et0: Double?): Double {
        if (shallow == null && deep == null) return 1.0
        val shallowScore = shallow?.let {
            when {
                it < 0.10 -> 0.10
                it <= 0.22 -> 0.10 + 0.90 * smoothstep(0.10, 0.22, it)
                it <= 0.38 -> 1.0
                it <= 0.48 -> 1.0 - 0.50 * smoothstep(0.38, 0.48, it)
                else -> 0.50
            }
        }
        val deepScore = deep?.let {
            when {
                it < 0.12 -> 0.20
                it <= 0.20 -> 0.20 + 0.80 * smoothstep(0.12, 0.20, it)
                it <= 0.35 -> 1.0
                it <= 0.45 -> 1.0 - 0.40 * smoothstep(0.35, 0.45, it)
                else -> 0.60
            }
        }
        val base = when {
            shallowScore != null && deepScore != null -> 0.55 * shallowScore + 0.45 * deepScore
            shallowScore != null -> shallowScore
            deepScore != null -> deepScore
            else -> 1.0
        }
        val etModifier = if (et0 != null && et0 > 3.0) {
            1.0 - 0.15 * ((et0 - 3.0).coerceIn(0.0, 3.0) / 3.0)
        } else {
            1.0
        }
        return (base * etModifier).coerceIn(0.0, 1.0)
    }

    fun altitudeScore(elevation: Double, species: MushroomSpecies): Double = when {
        elevation < species.minElevation || elevation > species.maxElevation -> 0.4
        elevation in species.idealElevationMin.toDouble()..species.idealElevationMax.toDouble() -> 1.0
        elevation < species.idealElevationMin -> 0.6 + 0.4 *
            ((elevation - species.minElevation) / (species.idealElevationMin - species.minElevation))
        else -> 0.6 + 0.4 *
            ((species.maxElevation - elevation) / (species.maxElevation - species.idealElevationMax))
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

    private fun smoothstep(edge0: Double, edge1: Double, value: Double): Double {
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }
    private fun radiansToDegrees(value: Double): Double = value * 180.0 / kotlin.math.PI
}
