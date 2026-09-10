package github.naturewhisp.myco.platform.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import github.naturewhisp.myco.platform.PlatformNavigator
import java.util.Locale

/**
 * Implementazione Android di [PlatformNavigator] che apre il chooser di sistema con intent `geo:`
 * e fallback su Google Maps web.
 *
 * Utilizza la forma canonica standard `geo:lat,lon?q=lat,lon` puramente numerica (senza parentesi né etichette),
 * garantendo la massima compatibilità e prevenendo crash o anomalie nei parser di app escursionistiche
 * come Outdooractive, OsmAnd, Komoot, Waze e Locus Map.
 *
 * @param context Contesto Android dell'applicazione per avviare l'intent.
 */
class AndroidPlatformNavigator(private val context: Context) : PlatformNavigator {
    override fun navigateTo(latitude: Double, longitude: Double, label: String) {
        // La sintassi standard geo:lat,lon?q=lat,lon senza label tra parentesi garantisce che:
        // 1. Google Maps posizioni il pin rosso con tasti "Indicazioni" e "Avvia".
        // 2. Outdooractive apra il waypoint posizionato con tasti "Raggiungi punto" e "Parti qui",
        //    senza andare in eccezione di parsing sulle parentesi o fallback sull'Oceano Atlantico (0,0).
        // 3. OsmAnd, Komoot, Waze e Locus Map interpretino correttamente le coordinate.
        val geoUri = String.format(
            Locale.US,
            "geo:%.5f,%.5f?q=%.5f,%.5f",
            latitude,
            longitude,
            latitude,
            longitude
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
