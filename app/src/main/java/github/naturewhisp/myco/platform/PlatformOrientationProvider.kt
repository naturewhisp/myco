package github.naturewhisp.myco.platform

import kotlinx.coroutines.flow.Flow

/**
 * Astrazione del sensore di orientamento e bussola del dispositivo.
 * Su Android sfrutta Sensor.TYPE_ROTATION_VECTOR.
 * Su macOS risponde con isSupported = false (o stima del corso di movimento).
 */
interface PlatformOrientationProvider {
    /**
     * Flusso reattivo continuo dell'azimut in gradi (0..360, 0 = Nord).
     */
    fun headingUpdates(): Flow<DeviceHeading>

    /**
     * Indica se la piattaforma o l'hardware attuale supporta la bussola.
     */
    fun isSupported(): Boolean
}
