package github.naturewhisp.myco.core

enum class ProbabilityTier(
    val tierIndex: Int,
    val minProbability: Int,
    val maxProbability: Int,
    val shortLabel: String,
    val descriptiveLabel: String,
) {
    VERY_LOW(0, 0, 19, "Inattivo", "INATTIVO • CONDIZIONI SFAVOREVOLI"),
    LOW(1, 20, 39, "Innesco", "EMERGENTE • INNESCO MICELIARE"),
    MODERATE(2, 40, 59, "Discreto", "MODERATO • POTENZIALE DISCRETO"),
    HIGH(3, 60, 74, "Propizio", "PROPIZIO • BUTTATA IN CORSO"),
    VERY_HIGH(4, 75, 100, "Culmine", "CULMINE • MASSIMA FAVOREVOLEZZA");

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
}

enum class FactorLevel { FAVORABLE, NEUTRAL, ADVERSE, INFORMATIVE }

data class Factor(
    val id: FactorId,
    val label: String,
    val formattedValue: String,
    val level: FactorLevel,
    val detail: String,
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

data class ProcessedDay(
    val dateIso: String,
    val avgTemp: Double,
    val totalPrecipMm: Double,
    val avgHumidityPercent: Double,
    val weatherCode: Int?,
    val soilMoisture0To7: Double?,
    val soilMoisture7To28: Double?,
    val evapotranspiration: Double?,
)

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
)

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
) {
    val suitabilityScore: Int get() = probability
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
)
