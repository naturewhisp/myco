package github.naturewhisp.myco.model

import com.google.gson.annotations.SerializedName

/**
 * Dettagli dell'indirizzo e gerarchia amministrativa restituiti dal servizio Nominatim OSM.
 *
 * @property village Nome del villaggio o frazione rurale.
 * @property town Nome del comune o centro abitato minore.
 * @property city Nome della città principale.
 * @property municipality Nome della municipalità amministrativa.
 * @property county Nome della provincia o contea.
 * @property state Nome della regione o stato federale.
 * @property country Nome della nazione.
 */
data class AddressDetails(
    @SerializedName("village") val village: String? = null,
    @SerializedName("town") val town: String? = null,
    @SerializedName("city") val city: String? = null,
    @SerializedName("municipality") val municipality: String? = null,
    @SerializedName("county") val county: String? = null,
    @SerializedName("state") val state: String? = null,
    @SerializedName("country") val country: String? = null
)

/**
 * Risultato di geocodifica (diretta o inversa) fornito dall'API OpenStreetMap Nominatim.
 *
 * @property lat Latitudine geografica in formato stringa decimale WGS84.
 * @property lon Longitudine geografica in formato stringa decimale WGS84.
 * @property displayName Toponimo completo comprensivo di gerarchia amministrativa.
 * @property address Dettagli strutturati dell'indirizzo [AddressDetails].
 */
data class GeocodeResult(
    @SerializedName("lat") val lat: String,
    @SerializedName("lon") val lon: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("address") val address: AddressDetails? = null
)
