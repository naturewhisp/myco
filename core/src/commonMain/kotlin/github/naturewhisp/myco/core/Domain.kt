package github.naturewhisp.myco.core

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Rappresentazione geodetica WGS84 immutabile e cross-platform di una coordinata geografica.
 * Fornisce validazione di dominio dei range angolari e calcolo delle distanze tramite formula Haversine.
 */
data class GeoCoordinates(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude in -90.0..90.0) { "Latitudine non valida: $latitude (intervallo ammesso [-90.0, 90.0])" }
        require(longitude in -180.0..180.0) { "Longitudine non valida: $longitude (intervallo ammesso [-180.0, 180.0])" }
    }

    /**
     * Calcola la distanza geodetica in metri rispetto a un'altra coordinata (formula di Haversine).
     */
    fun distanceToMeters(other: GeoCoordinates): Double {
        val r = 6371000.0
        val lat1Rad = latitude * (PI / 180.0)
        val lat2Rad = other.latitude * (PI / 180.0)
        val dLat = (other.latitude - latitude) * (PI / 180.0)
        val dLon = (other.longitude - longitude) * (PI / 180.0)

        val sinDLat2 = sin(dLat / 2.0)
        val sinDLon2 = sin(dLon / 2.0)
        val a = sinDLat2 * sinDLat2 + cos(lat1Rad) * cos(lat2Rad) * sinDLon2 * sinDLon2
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return r * c
    }

    /**
     * Calcola la distanza geodetica in chilometri rispetto a un'altra coordinata.
     */
    fun distanceToKm(other: GeoCoordinates): Double = distanceToMeters(other) / 1000.0
}

enum class ProbabilityTier(
    val tierIndex: Int,
    val minProbability: Int,
    val maxProbability: Int,
    val shortLabel: String,
    val descriptiveLabel: String,
) {
    VERY_LOW(0, 0, 19, "Molto bassa", "FAVOREVOLEZZA MOLTO BASSA • CONDIZIONI SFAVOREVOLI"),
    LOW(1, 20, 39, "Bassa", "FAVOREVOLEZZA BASSA • CONDIZIONI LIMITANTI"),
    MODERATE(2, 40, 59, "Media", "FAVOREVOLEZZA MEDIA • CONDIZIONI INTERMEDIE"),
    HIGH(3, 60, 74, "Alta", "FAVOREVOLEZZA ALTA • CONDIZIONI PROPIZIE"),
    VERY_HIGH(4, 75, 100, "Molto alta", "FAVOREVOLEZZA MOLTO ALTA • CONDIZIONI OTTIMALI");

    companion object {
        fun fromProbability(probability: Int): ProbabilityTier = when {
            probability < 20 -> VERY_LOW
            probability < 40 -> LOW
            probability < 60 -> MODERATE
            probability < 75 -> HIGH
            else -> VERY_HIGH
        }
    }
}

enum class FactorId {
    TEMPERATURE,
    PRECIPITATION,
    HUMIDITY,
    SOIL_MOISTURE,
    HABITAT,
    ALTITUDE,
    SEASONALITY,
    MYCELIAL_PHASE,
    LUNAR_PHASE,
    SLOPE,
    SPUN_ECM,
    SPUN_HYPHAL,
    FOREST_PROXIMITY,
}

enum class FactorLevel { FAVORABLE, NEUTRAL, ADVERSE, INFORMATIVE }

data class Factor(
    val id: FactorId,
    val label: String,
    val formattedValue: String,
    val level: FactorLevel,
    val detail: String,
)

enum class HabitatStatus {
    /** Presenza confermata di bosco, foresta, pascolo o radura biologicamente compatibile. */
    KNOWN_SUITABLE,

    /** Presenza confermata di contesto artificiale, urbano, industriale o specchio d'acqua. */
    KNOWN_UNSUITABLE,

    /** Nessun dato OSM o copertura geografica disponibile (offline, timeout, fuori copertura). */
    UNKNOWN,
}

data class HabitatEvidence(
    val status: HabitatStatus,
    val forestCoverFraction: Double,
    val meadowFraction: Double,
    val distanceToNearestForestMeters: Double,
    val confirmedHostGenera: Set<String>,
    val dominantLeafType: String? = null,
) {
    constructor(
        status: HabitatStatus,
        forestCoverFraction: Double,
        meadowFraction: Double,
        distanceToNearestForestMeters: Double,
        confirmedHostGenera: Set<String>,
    ) : this(
        status = status,
        forestCoverFraction = forestCoverFraction,
        meadowFraction = meadowFraction,
        distanceToNearestForestMeters = distanceToNearestForestMeters,
        confirmedHostGenera = confirmedHostGenera,
        dominantLeafType = null,
    )

    companion object {
        val UNKNOWN_HABITAT = HabitatEvidence(
            status = HabitatStatus.UNKNOWN,
            forestCoverFraction = 0.0,
            meadowFraction = 0.0,
            distanceToNearestForestMeters = 500.0,
            confirmedHostGenera = emptySet(),
            dominantLeafType = null,
        )
    }
}

data class OsmHabitatElement(
    val lat: Double?,
    val lon: Double?,
    val isWoodOrForest: Boolean,
    val isMeadowOrGrass: Boolean,
    val isUrbanOrBuilt: Boolean,
    val genus: String?,
    val leafType: String? = null,
)

data class SpeciesHabitatEvaluation(
    val score: Double,
    val baseText: String,
    val bonusText: String,
    val basalAreaM2Ha: Float,
    val standDensityScore: Double,
    val baseScore: Double = score,
)

enum class EcologicalCategory(val label: String, val description: String) {
    ECTOMYCORRHIZAL("Simbiotico EcM", "Legato a radici di alberi specifici"),
    SAPROTROPHIC("Saprofita umicolo", "Cresce su lettiera organica, prati e margini boschivi"),
    PARASITIC("Lignicolo / Parassita", "Sviluppo su tronchi vivi o ceppaie in decomposizione"),
}

data class MushroomSpecies(
    val id: String,
    val binomialName: String,
    val vernacularName: String,
    val category: EcologicalCategory,
    val minElevation: Int,
    val maxElevation: Int,
    val idealElevationMin: Int,
    val idealElevationMax: Int,
    val idealTempMin: Double,
    val idealTempMax: Double,
    val toleratedTempMin: Double,
    val toleratedTempMax: Double,
    val minRainAccumulation: Double,
    val preferredCanopyTypes: List<String>,
    val fruitingPeriodDescription: String,
    val activeMonths: List<Int>,
    val toxicLookAlikes: List<String> = emptyList(),
    val edibilityWarning: String? = null,
    val phenologyLatencyPeakDays: Double = 11.0,
    val phenologyShapeAlpha: Double = 4.0,
    val optimalTemp: Double = (idealTempMin + idealTempMax) / 2.0,
    val optimalBasalAreaM2Ha: Double = 32.0,
    val hurdleStrictness: Double = 1.0,
) {
    val isGeneralBaseline: Boolean
        get() = id == "general"
}

enum class GrowthStage {
    WAITING_FOR_RAIN,
    MYCELIAL_HYDRATION,
    PRIMORDIA_INCUBATION,
    ACTIVE_FRUITING,
    WANING,
}

data class GrowthPhaseEvaluation(
    val phaseText: String,
    val multiplier: Double,
    val daysSinceTrigger: Int? = null,
    val stage: GrowthStage = GrowthStage.WAITING_FOR_RAIN,
    val phiBase: Double = multiplier,
    val phiSoil: Double = 1.0,
)

data class SoilHydrologyEvaluation(
    val phiSoil: Double,
    val averageSoil0To7: Double?,
    val availableDaysCount: Int,
    val isTargetDayPresent: Boolean,
    val diagnosisText: String,
)

data class RainTrigger(
    val triggerIndex: Int,
    val rainAmount: Double,
)

data class ProcessedDay(
    val dateIso: String,
    val avgTemp: Double,
    val totalPrecipMm: Double,
    val avgHumidityPercent: Double,
    val weatherCode: Int?,
    val soilMoisture0To7: Double? = null,
    val soilMoisture7To28: Double? = null,
    val evapotranspiration: Double? = null,
    val minTemp: Double = avgTemp,
    val maxTemp: Double = avgTemp,
) {
    val liquidPrecipMm: Double
        get() = if (isSnowDay(weatherCode, avgTemp)) 0.0 else totalPrecipMm

    constructor(
        dateIso: String,
        avgTemp: Double,
        totalPrecipMm: Double,
        avgHumidityPercent: Double,
        weatherCode: Int?,
        soilMoisture0To7: Double?,
        soilMoisture7To28: Double?,
        evapotranspiration: Double?,
    ) : this(
        dateIso = dateIso,
        avgTemp = avgTemp,
        totalPrecipMm = totalPrecipMm,
        avgHumidityPercent = avgHumidityPercent,
        weatherCode = weatherCode,
        soilMoisture0To7 = soilMoisture0To7,
        soilMoisture7To28 = soilMoisture7To28,
        evapotranspiration = evapotranspiration,
        minTemp = avgTemp,
        maxTemp = avgTemp,
    )

    companion object {
        fun isSnowDay(weatherCode: Int?, avgTemp: Double): Boolean {
            val isSnowWmo = weatherCode in listOf(71, 73, 75, 77, 85, 86)
            return isSnowWmo || avgTemp <= 0.0
        }
    }
}


data class DailyOutlook(
    val dateIso: String,
    val weatherCode: Int?,
    val avgTemp: Double,
    val totalPrecipMm: Double,
    val avgHumidityPercent: Double,
    val probability: Int,
    val tier: ProbabilityTier,
) {
    val suitabilityScore: Int get() = probability
}

data class TerrainAspect(
    val elevation: Double,
    val slopeDegrees: Double,
    val aspectDegrees: Double,
    val cardinalDirection: String,
    val modifier: Double,
)

data class AnalysisInputs(
    val days: List<ProcessedDay>,
    val todayIndex: Int,
    val speciesId: String,
    val habitatScore: Double,
    val habitatDescription: String,
    val canopyTypes: List<String>,
    val elevationSamples: List<Double>,
    val monthIndex: Int,
    val spunEcmRichness: Double?,
    val spunHyphalDensity: Double?,
    val missingSources: List<String>,
    val canopyCover: Double = 0.0,
    val forestProximityIndex: Double = canopyCover,
    val calculationMode: String = "ALL",
) {
    constructor(
        days: List<ProcessedDay>,
        todayIndex: Int,
        speciesId: String,
        habitatScore: Double,
        habitatDescription: String,
        canopyTypes: List<String>,
        elevationSamples: List<Double>,
        monthIndex: Int,
        spunEcmRichness: Double?,
        spunHyphalDensity: Double?,
        missingSources: List<String>,
        canopyCover: Double,
        forestProximityIndex: Double,
    ) : this(
        days = days,
        todayIndex = todayIndex,
        speciesId = speciesId,
        habitatScore = habitatScore,
        habitatDescription = habitatDescription,
        canopyTypes = canopyTypes,
        elevationSamples = elevationSamples,
        monthIndex = monthIndex,
        spunEcmRichness = spunEcmRichness,
        spunHyphalDensity = spunHyphalDensity,
        missingSources = missingSources,
        canopyCover = canopyCover,
        forestProximityIndex = forestProximityIndex,
        calculationMode = "ALL",
    )

    constructor(
        days: List<ProcessedDay>,
        todayIndex: Int,
        speciesId: String,
        habitatScore: Double,
        habitatDescription: String,
        canopyTypes: List<String>,
        elevationSamples: List<Double>,
        monthIndex: Int,
        spunEcmRichness: Double?,
        spunHyphalDensity: Double?,
        missingSources: List<String>,
        canopyCover: Double,
    ) : this(
        days = days,
        todayIndex = todayIndex,
        speciesId = speciesId,
        habitatScore = habitatScore,
        habitatDescription = habitatDescription,
        canopyTypes = canopyTypes,
        elevationSamples = elevationSamples,
        monthIndex = monthIndex,
        spunEcmRichness = spunEcmRichness,
        spunHyphalDensity = spunHyphalDensity,
        missingSources = missingSources,
        canopyCover = canopyCover,
        forestProximityIndex = canopyCover,
        calculationMode = "ALL",
    )

    constructor(
        days: List<ProcessedDay>,
        todayIndex: Int,
        speciesId: String,
        habitatScore: Double,
        habitatDescription: String,
        canopyTypes: List<String>,
        elevationSamples: List<Double>,
        monthIndex: Int,
        spunEcmRichness: Double?,
        spunHyphalDensity: Double?,
        missingSources: List<String>,
    ) : this(
        days = days,
        todayIndex = todayIndex,
        speciesId = speciesId,
        habitatScore = habitatScore,
        habitatDescription = habitatDescription,
        canopyTypes = canopyTypes,
        elevationSamples = elevationSamples,
        monthIndex = monthIndex,
        spunEcmRichness = spunEcmRichness,
        spunHyphalDensity = spunHyphalDensity,
        missingSources = missingSources,
        canopyCover = 0.0,
        forestProximityIndex = 0.0,
        calculationMode = "ALL",
    )

    companion object {
        fun builder(): AnalysisInputsBuilder = AnalysisInputsBuilder()
    }
}

class AnalysisInputsBuilder {
    var days: List<ProcessedDay> = emptyList()
    var todayIndex: Int = -1
    var speciesId: String = "general"
    var habitatScore: Double = 0.5
    var habitatDescription: String = ""
    var canopyTypes: List<String> = emptyList()
    var elevationSamples: List<Double> = emptyList()
    var monthIndex: Int = 0
    var spunEcmRichness: Double? = null
    var spunHyphalDensity: Double? = null
    var missingSources: List<String> = emptyList()
    var canopyCover: Double = 0.0
    var forestProximityIndex: Double = 0.0
    var calculationMode: String = "ALL"

    fun days(days: List<ProcessedDay>) = apply { this.days = days }
    fun todayIndex(todayIndex: Int) = apply { this.todayIndex = todayIndex }
    fun speciesId(speciesId: String) = apply { this.speciesId = speciesId }
    fun habitatScore(habitatScore: Double) = apply { this.habitatScore = habitatScore }
    fun habitatDescription(habitatDescription: String) = apply { this.habitatDescription = habitatDescription }
    fun canopyTypes(canopyTypes: List<String>) = apply { this.canopyTypes = canopyTypes }
    fun elevationSamples(elevationSamples: List<Double>) = apply { this.elevationSamples = elevationSamples }
    fun monthIndex(monthIndex: Int) = apply { this.monthIndex = monthIndex }
    fun spunEcmRichness(spunEcmRichness: Double?) = apply { this.spunEcmRichness = spunEcmRichness }
    fun spunHyphalDensity(spunHyphalDensity: Double?) = apply { this.spunHyphalDensity = spunHyphalDensity }
    fun missingSources(missingSources: List<String>) = apply { this.missingSources = missingSources }
    fun canopyCover(canopyCover: Double) = apply {
        this.canopyCover = canopyCover
        if (this.forestProximityIndex == 0.0) this.forestProximityIndex = canopyCover
    }
    fun forestProximityIndex(forestProximityIndex: Double) = apply { this.forestProximityIndex = forestProximityIndex }
    fun calculationMode(calculationMode: String) = apply { this.calculationMode = calculationMode }

    fun build(): AnalysisInputs = AnalysisInputs(
        days = days,
        todayIndex = todayIndex,
        speciesId = speciesId,
        habitatScore = habitatScore,
        habitatDescription = habitatDescription,
        canopyTypes = canopyTypes,
        elevationSamples = elevationSamples,
        monthIndex = monthIndex,
        spunEcmRichness = spunEcmRichness,
        spunHyphalDensity = spunHyphalDensity,
        missingSources = missingSources,
        canopyCover = canopyCover,
        forestProximityIndex = forestProximityIndex,
        calculationMode = calculationMode,
    )
}

enum class DataQualityStatus {
    OPTIMAL,
    DEGRADED_PARTIAL_SOIL,
    DEGRADED_MISSING_SOIL,
    DEGRADED_INCOMPLETE_WEATHER,
    DEGRADED_OUT_OF_BOUNDS,
}

enum class HeatmapLayerStatus {
    AVAILABLE,
    UNAVAILABLE_GUILD_NOT_SUPPORTED,
    UNAVAILABLE_OUT_OF_COVERAGE,
}

data class AnalysisResult(
    val probability: Int,
    val tier: ProbabilityTier,
    val weatherScore: Int,
    val habitatScore: Double,
    val altitudeScore: Double,
    val seasonalityScore: Double,
    val terrain: TerrainAspect,
    val factors: List<Factor>,
    val dailyOutlooks: List<DailyOutlook>,
    val deterministicFieldNote: String,
    val missingSources: List<String>,
    val growthPhase: GrowthPhaseEvaluation? = null,
    val dataQuality: DataQualityStatus = DataQualityStatus.OPTIMAL,
    val waterDiagnosis: String? = null,
    val effectiveRainMm: Double = 0.0,
) {
    val suitabilityScore: Int get() = probability
    val isCalculable: Boolean get() = dataQuality != DataQualityStatus.DEGRADED_OUT_OF_BOUNDS
}

data class SpunRegionHeader(
    val version: Int,
    val regionCode: String,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
    val width: Int,
    val height: Int,
    val stepArcSec: Int,
)

data class SpunGrid(
    val header: SpunRegionHeader,
    val ecmData: ByteArray,
    val hyphalData: ByteArray,
)

data class SpunSample(
    val ecmRichness: Double,
    val hyphalDensity: Double,
    val ecmScore: Double,
    val hyphalScore: Double,
    val regionCode: String,
)

data class HeatmapRaster(
    val argbPixels: IntArray,
    val width: Int,
    val height: Int,
    val north: Double,
    val south: Double,
    val west: Double,
    val east: Double,
    val layerStatus: HeatmapLayerStatus = HeatmapLayerStatus.AVAILABLE,
    val statusDescription: String? = null,
)
