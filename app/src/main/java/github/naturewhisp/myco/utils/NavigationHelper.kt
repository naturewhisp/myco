package github.naturewhisp.myco.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import java.util.Locale

object NavigationHelper {

    /**
     * Avvia la navigazione verso le coordinate specificate aprendo il selettore
     * di sistema con tutte le app di navigazione/sentieri installate (Outdooractive, Komoot, Maps, ecc.).
     */
    fun navigateTo(
        context: Context,
        latitude: Double,
        longitude: Double,
        label: String = "Punto Funghi"
    ) {
        val cleanLabel = label.ifBlank { "Punto Funghi" }
        val encodedLabel = Uri.encode(cleanLabel)

        // URI standard geo compatibile con tutte le app di sentieri e navigatori (Outdooractive, Komoot, Google Maps, OsmAnd)
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

        // Il chooser di sistema forza la scelta permettendo all'utente di selezionare
        // tra app sentieristiche (es. Outdooractive, Komoot) o navigatori stradali (Google Maps, Waze)
        val chooser = Intent.createChooser(mapIntent, "Naviga verso il punto con:").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            context.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            // Fallback web nel caso remoto in cui nessuna app geografica sia registrata
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
