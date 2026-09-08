package github.naturewhisp.myco.platform

/**
 * Coordinate geografiche WGS84 agnostiche dalla piattaforma.
 */
data class LocationCoordinates(
    val latitude: Double,
    val longitude: Double
)

/**
 * Astrazione per l'acquisizione della posizione geografica corrente del dispositivo.
 * Permette di isolare Google Play Services Location su Android e CoreLocation su macOS.
 */
interface PlatformLocationProvider {
    suspend fun getCurrentLocation(): LocationCoordinates?
}
