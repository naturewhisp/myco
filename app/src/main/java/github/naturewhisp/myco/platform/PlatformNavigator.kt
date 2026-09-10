package github.naturewhisp.myco.platform

/**
 * Astrazione per l'avvio della navigazione o apertura delle coordinate geografiche
 * tramite app esterne o provider di mappe di sistema.
 *
 * Questa porta esagonale è al 100% pura Kotlin e disaccoppia il dominio applicativo
 * dalle API geografiche della piattaforma (Intent Android o URL scheme su macOS).
 */
interface PlatformNavigator {
    /**
     * Avvia la navigazione o visualizzazione delle coordinate geografiche WGS84 specificate.
     *
     * @param latitude Latitudine in gradi decimali WGS84.
     * @param longitude Longitudine in gradi decimali WGS84.
     * @param label Etichetta descrittiva del punto geografico (default "Punto Funghi").
     */
    fun navigateTo(latitude: Double, longitude: Double, label: String = "Punto Funghi")
}
