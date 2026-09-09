package github.naturewhisp.myco.platform

import kotlinx.coroutines.flow.Flow

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
    /**
     * Lettura puntuale one-shot delle coordinate correnti.
     */
    suspend fun getCurrentLocation(): LocationCoordinates?

    /**
     * Flusso reattivo continuo di aggiornamenti della posizione live dell'utente.
     */
    fun locationUpdates(): Flow<UserLocation>
}
