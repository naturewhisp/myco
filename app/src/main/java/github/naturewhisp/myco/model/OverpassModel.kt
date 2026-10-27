package github.naturewhisp.myco.model

import com.google.gson.annotations.SerializedName
import github.naturewhisp.myco.core.GeoCoordinates

/**
 * Risposta grezza della query Overpass API per elementi OpenStreetMap nell'area circostante.
 *
 * @property elements Lista degli elementi spaziali OSM (nodi, vie, relazioni) corrispondenti alla query.
 */
data class OverpassResponse(
    @SerializedName("elements") val elements: List<OverpassElement>
) {
    private fun isPointValid(pt: OverpassCenter?): Boolean {
        return pt != null &&
            pt.lat.isFinite() &&
            pt.lon.isFinite() &&
            pt.lat in -90.0..90.0 &&
            pt.lon in -180.0..180.0
    }

    /**
     * Rileva se la risposta contiene relazioni ecologiche (boschi, prati, aree antropizzate)
     * prive di membri vettoriali e geometrie, oppure coordinate nulle/non valide nei poligoni.
     */
    fun hasDefectiveRelationGeometries(): Boolean {
        return elements.any { el ->
            val missingRelationGeometries = el.type == "relation" &&
                (el.isWoodOrForest || el.isMeadowOrGrass || el.isUrbanOrBuilt) &&
                el.members.isNullOrEmpty() &&
                el.geometry.isNullOrEmpty()
            val invalidElementGeometry = el.geometry?.any { pt -> !isPointValid(pt) } == true
            val invalidMemberGeometry = el.members?.any { m ->
                m.geometry?.any { pt -> !isPointValid(pt) } == true
            } == true
            missingRelationGeometries || invalidElementGeometry || invalidMemberGeometry
        }
    }

    /**
     * Ripulisce la risposta rimuovendo punti geometrici nulli o fuori dominio geografico
     * e scartando elementi spurii interamente fuori dall'area di ricerca (es. bug server Overpass).
     */
    fun sanitize(
        targetLat: Double,
        targetLon: Double,
        maxDistanceMeters: Double = 15_000.0,
    ): OverpassResponse {
        val target = GeoCoordinates(targetLat, targetLon)
        val sanitizedElements = elements.mapNotNull { el ->
            val cleanGeom: List<OverpassCenter>? = el.geometry?.mapNotNull { pt -> pt?.takeIf(::isPointValid) }
            val cleanMembers = el.members?.map { member ->
                member.copy(geometry = member.geometry?.mapNotNull { pt -> pt?.takeIf(::isPointValid) })
            }
            val allPoints = buildList<OverpassCenter> {
                el.coordinate?.let { (lat, lon) ->
                    val centerPt = OverpassCenter(lat, lon)
                    if (isPointValid(centerPt)) add(centerPt)
                }
                if (cleanGeom != null) addAll(cleanGeom)
                cleanMembers?.forEach { m ->
                    m.geometry?.filterNotNull()?.let { addAll(it) }
                }
            }
            if (allPoints.isNotEmpty() && allPoints.all { pt ->
                    target.distanceToMeters(GeoCoordinates(pt.lat, pt.lon)) > maxDistanceMeters
                }
            ) {
                null
            } else {
                el.copy(geometry = cleanGeom, members = cleanMembers)
            }
        }
        return OverpassResponse(elements = sanitizedElements)
    }
}

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
    @SerializedName("center") val center: OverpassCenter? = null,
    @SerializedName("tags") val tags: Map<String, String>? = null,
    @SerializedName("geometry") val geometry: List<OverpassCenter?>? = null,
    @SerializedName("members") val members: List<OverpassMember>? = null,
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

    /**
     * Genere botanico arboreo estratto dal tag `genus` o dal binomio `species`.
     */
    val genus: String?
        get() = tags?.get("genus") ?: tags?.get("species")?.split(" ")?.firstOrNull()

    /**
     * Tipologia fogliare prevalente estratta dal tag `leaf_type` (es. "broadleaved", "needleleaved").
     */
    val leafType: String?
        get() = tags?.get("leaf_type")

    /**
     * Indica se l'elemento rappresenta una copertura forestale o boschiva verificata.
     */
    val isWoodOrForest: Boolean
        get() {
            val natural = tags?.get("natural")
            val landuse = tags?.get("landuse")
            return natural == "wood" || landuse == "forest"
        }

    /**
     * Indica se l'elemento rappresenta un ambiente aperto a prato, pascolo o radura.
     */
    val isMeadowOrGrass: Boolean
        get() {
            val landuse = tags?.get("landuse")
            val natural = tags?.get("natural")
            return landuse in listOf("meadow", "grass", "pasture") || natural in listOf("grassland", "heath")
        }

    /**
     * Indica se l'elemento appartiene a un tessuto urbano, industriale o intensamente antropizzato.
     */
    val isUrbanOrBuilt: Boolean
        get() {
            val landuse = tags?.get("landuse")
            return landuse in listOf("residential", "commercial", "industrial", "retail", "construction") ||
                    tags?.containsKey("building") == true
        }
}

/**
 * Coordinate del centroide calcolato da Overpass per elementi poligonali o lineari ("out center;").
 */
data class OverpassCenter(
    @SerializedName("lat") val lat: Double,
    @SerializedName("lon") val lon: Double
)

data class OverpassMember(
    val role: String?,
    val geometry: List<OverpassCenter?>?,
)
