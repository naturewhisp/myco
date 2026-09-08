package github.naturewhisp.myco.utils

import android.content.Context
import github.naturewhisp.myco.platform.AndroidPlatformNavigator
import github.naturewhisp.myco.platform.PlatformNavigator

object NavigationHelper {

    /**
     * Avvia la navigazione verso le coordinate specificate tramite [PlatformNavigator].
     */
    fun navigateTo(
        context: Context,
        latitude: Double,
        longitude: Double,
        label: String = "Punto Funghi"
    ) {
        val navigator: PlatformNavigator = AndroidPlatformNavigator(context)
        navigator.navigateTo(latitude, longitude, label)
    }

    /**
     * Esegue la navigazione direttamente tramite l'istanza agnostica di [PlatformNavigator].
     */
    fun navigateTo(
        navigator: PlatformNavigator,
        latitude: Double,
        longitude: Double,
        label: String = "Punto Funghi"
    ) {
        navigator.navigateTo(latitude, longitude, label)
    }
}
