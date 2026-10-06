package github.naturewhisp.myco.platform

import github.naturewhisp.myco.core.GeoCoordinates
import kotlinx.coroutines.flow.Flow

/**
 * Coordinate geografiche WGS84 agnostiche dalla piattaforma.
 * Allineate con il Value Object di dominio condiviso [GeoCoordinates].
 */
typealias LocationCoordinates = GeoCoordinates

/**
 * Astrazione per l'acquisizione della posizione geografica corrente del dispositivo.
 * Permette di isolare Google Play Services Location su Android e CoreLocation su iOS.
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
