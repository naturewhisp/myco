package github.naturewhisp.myco.model

import com.google.gson.annotations.SerializedName

/**
 * Risposta dall'API di elevazione batch di Open-Meteo.
 */
data class ElevationResponse(
    @SerializedName("elevation") val elevation: List<Float>
)

/**
 * Dati orografici calcolati dal gradiente altimetrico del terreno.
 */
data class TerrainAspectData(
    val centerElevation: Float,
    val slopeDegrees: Float,
    val slopePercent: Float,
    val aspectDegrees: Float,
    val cardinalDirection: String,
    val cardinalAbbreviation: String,
    val isFlat: Boolean
)

/**
 * Valutazione ecologica dell'esposizione e pendenza rispetto a specie e stagione.
 */
data class TerrainAspectEvaluation(
    val terrain: TerrainAspectData?,
    val level: FactorLevel,
    val formattedValue: String,
    val detail: String,
    val modifier: Double = 1.0
)
