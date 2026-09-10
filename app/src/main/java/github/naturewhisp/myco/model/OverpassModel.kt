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
 */
data class OverpassElement(
    @SerializedName("type") val type: String,
    @SerializedName("id") val id: Long
)
