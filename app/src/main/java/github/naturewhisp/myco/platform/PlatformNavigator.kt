package github.naturewhisp.myco.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import java.util.Locale

/**
 * Astrazione per l'avvio della navigazione o apertura delle coordinate geografiche
 * tramite app esterne o provider di mappe di sistema.
 */
interface PlatformNavigator {
    fun navigateTo(latitude: Double, longitude: Double, label: String = "Punto Funghi")
}

/**
 * Implementazione Android di [PlatformNavigator] che apre il chooser di sistema con intent geo:
 * e fallback su Google Maps web.
 */
class AndroidPlatformNavigator(private val context: Context) : PlatformNavigator {
    override fun navigateTo(latitude: Double, longitude: Double, label: String) {
        val cleanLabel = label.ifBlank { "Punto Funghi" }
        val encodedLabel = Uri.encode(cleanLabel)

        val geoUri = String.format(
            Locale.US,
            "geo:%.5f,%.5f?q=%.5f,%.5f(%s)",
            latitude,
            longitude,
            latitude,
            longitude,
            encodedLabel
        ).toUri()

        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val chooser = Intent.createChooser(mapIntent, "Naviga verso il punto con:").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            context.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            try {
                val webUri = String.format(
                    Locale.US,
                    "https://www.google.com/maps/dir/?api=1&destination=%.5f,%.5f",
                    latitude,
                    longitude
                ).toUri()
                val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(webIntent)
            } catch (_: Exception) {
                Toast.makeText(context, "Nessuna app di navigazione trovata", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
