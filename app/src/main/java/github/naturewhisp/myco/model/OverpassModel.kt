package github.naturewhisp.myco.model

import com.google.gson.annotations.SerializedName

/**
 * Risposta grezza della query Overpass API per elementi OpenStreetMap nell'area circostante.
 *
 * @property elements Lista degli elementi spaziali OSM (nodi, vie, relazioni) corrispondenti alla query.
 */
data class OverpassResponse(
    @SerializedName("elements") val elements: List<OverpassElement>
)

/**
 * Singolo elemento geografico vettoriale restituito da OpenStreetMap Overpass.
 *
 * @property type Tipologia dell'elemento OSM ("node", "way", "relation").
 * @property id Identificatore numerico univoco globale OSM dell'oggetto.
 * @property lat Latitudine per elementi di tipo "node".
 * @property lon Longitudine per elementi di tipo "node".
 * @property center Centro geometrico approssimato per elementi complessi ("way", "relation").
 */
data class OverpassElement(
    @SerializedName("type") val type: String,
    @SerializedName("id") val id: Long,
    @SerializedName("lat") val lat: Double? = null,
    @SerializedName("lon") val lon: Double? = null,
    @SerializedName("center") val center: OverpassCenter? = null
) {
    /**
     * Restituisce la coordinata (lat, lon) dell'elemento, estraendola da `(lat, lon)` o da `center`.
     */
    val coordinate: Pair<Double, Double>?
        get() = when {
            lat != null && lon != null -> Pair(lat, lon)
            center != null -> Pair(center.lat, center.lon)
            else -> null
        }
}

/**
 * Coordinate del centroide calcolato da Overpass per elementi poligonali o lineari ("out center;").
 */
data class OverpassCenter(
    @SerializedName("lat") val lat: Double,
    @SerializedName("lon") val lon: Double
)
