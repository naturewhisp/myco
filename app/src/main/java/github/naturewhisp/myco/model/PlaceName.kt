package github.naturewhisp.myco.model

import java.util.Locale
import kotlin.math.abs

/**
 * Rappresentazione strutturata della toponomastica geografica secondo il canone editoriale Herbarium.
 *
 * Separa il toponimo principale (frazione, località o comune) dal contesto amministrativo
 * (provincia, regione, stato), formattando le coordinate geodetiche WGS84 e l'altitudine.
 *
 * @property primary Toponimo primario identificativo (es. "Abetone", "Passo della Cisa").
 * @property administrative Dettaglio amministrativo secondario (es. "Pistoia, Toscana").
 * @property coordinatesFormatted Coordinate decimali formattate con emisferi (es. "44.1432°N, 10.6621°E (WGS84)").
 * @property elevationFormatted Quota altimetrica formattata con unità di misura (es. "1388 m s.l.m.").
 */
data class PlaceName(
    val primary: String,
    val administrative: String,
    val coordinatesFormatted: String,
    val elevationFormatted: String
) {
    companion object {
        /**
         * Crea un'istanza [PlaceName] a partire dalla stringa descrittiva Nominatim o dalle coordinate WGS84 di fallback.
         *
         * @param rawName Stringa grezza restituita dal reverse geocoder.
         * @param latitude Latitudine in gradi decimali.
         * @param longitude Longitudine in gradi decimali.
         * @param elevationMeters Quota altimetrica opzionale in metri.
         * @return [PlaceName] strutturato e formattato.
         */
        fun fromNominatimOrCoordinates(
            rawName: String,
            latitude: Double,
            longitude: Double,
            elevationMeters: Float? = null
        ): PlaceName {
            val parts = rawName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val primary = parts.firstOrNull() ?: String.format(Locale.US, "Lat %.4f, Lon %.4f", latitude, longitude)
            val administrative = if (parts.size > 1) {
                parts.drop(1).take(2).joinToString(", ")
            } else {
                "Coordinate geografiche"
            }

            val latHem = if (latitude >= 0) "N" else "S"
            val lonHem = if (longitude >= 0) "E" else "W"
            val coords = String.format(
                Locale.US,
                "%.4f°%s, %.4f°%s (WGS84)",
                abs(latitude),
                latHem,
                abs(longitude),
                lonHem
            )
            val elevation = if (elevationMeters != null) {
                String.format(Locale.US, "%d m s.l.m.", elevationMeters.toInt())
            } else {
                "— m s.l.m."
            }

            return PlaceName(
                primary = primary,
                administrative = administrative,
                coordinatesFormatted = coords,
                elevationFormatted = elevation
            )
        }

        /**
         * Crea un'istanza [PlaceName] a partire dalla struttura tipizzata [GeocodeResult].
         *
         * @param geocodeResult DTO restituito da Nominatim [GeocodeResult].
         * @param elevationMeters Quota altimetrica in metri, se nota.
         * @return [PlaceName] con toponimo prioritario ricavato dalla gerarchia (village -> town -> city).
         */
        fun fromGeocodeResult(
            geocodeResult: GeocodeResult,
            elevationMeters: Float? = null
        ): PlaceName {
            val addr = geocodeResult.address
            val lat = geocodeResult.lat.toDoubleOrNull() ?: 0.0
            val lon = geocodeResult.lon.toDoubleOrNull() ?: 0.0
            val primary = addr?.village ?: addr?.town ?: addr?.city ?: addr?.municipality
                ?: geocodeResult.displayName.substringBefore(',').trim().ifEmpty {
                    String.format(Locale.US, "Lat %.4f, Lon %.4f", lat, lon)
                }
            val secondary = listOfNotNull(addr?.county ?: addr?.state, addr?.country)
                .filter { it.isNotBlank() }
                .joinToString(", ")
                .ifEmpty { "Coordinate geografiche" }

            val latHem = if (lat >= 0) "N" else "S"
            val lonHem = if (lon >= 0) "E" else "W"
            val coords = String.format(
                Locale.US,
                "%.4f°%s, %.4f°%s (WGS84)",
                abs(lat),
                latHem,
                abs(lon),
                lonHem
            )
            val elevation = if (elevationMeters != null) {
                String.format(Locale.US, "%d m s.l.m.", elevationMeters.toInt())
            } else {
                "— m s.l.m."
            }

            return PlaceName(
                primary = primary,
                administrative = secondary,
                coordinatesFormatted = coords,
                elevationFormatted = elevation
            )
        }
    }
}
