package github.naturewhisp.myco.model

/**
 * Configurazione tipizzata per la generazione e il rendering raster della mappa termica ([HeatmapRaster]).
 *
 * Incapsula tutti i parametri cartografici, le costanti geodetiche e le opacità progressive bilanciate
 * (finestra 115..180 per tema chiaro, 125..195 per tema scuro) richieste da AGENTS.md per garantire
 * il perfetto equilibrio tra estetica naturale e leggibilità orografica/toponomastica.
 *
 * @property gridSize Risoluzione quadrata della matrice raster in celle (default 96 per 96x96 celle, ~9216 campioni).
 * @property radiusKm Raggio geografico di copertura in chilometri rispetto al centro (default 35.0 km, box ~70x70 km).
 * @property metersPerDegLat Costante geodetica WGS84 di metri per grado di latitudine (default 111320.0).
 * @property cutoffThreshold Soglia percentuale minima al di sotto della quale i pixel sono completamente trasparenti (default 16).
 * @property featherThreshold Soglia percentuale di fine transizione morbida iniziale (default 20).
 * @property alphaMinLight Opacità minima bilanciata per il tema chiaro (default 115, ~45%).
 * @property alphaMaxLight Opacità massima di saturazione per il tema chiaro (default 180, ~70%).
 * @property alphaMinDark Opacità minima bilanciata per il tema scuro (default 125, ~49%).
 * @property alphaMaxDark Opacità massima di saturazione per il tema scuro (default 195, ~76%).
 * @property alphaSpanProb Ampiezza di normalizzazione dell'escursione opaca (default 65f).
 * @property plateauTier1 Soglia superiore del primo plateau zonale (Salvia/Lichene, default 40).
 * @property plateauTier2 Soglia superiore del secondo plateau zonale (Ocra/Ambra, default 60).
 * @property plateauTier3 Soglia superiore del terzo plateau zonale (Terracotta Cinabro, default 78).
 * @property plateauTier4 Soglia iniziale del culmine Hotspot (Ruggine Granato, default 86).
 * @property lightColors Tavolozza di pigmenti minerali ARGB per tema chiaro.
 * @property darkColors Tavolozza di pigmenti minerali ARGB per tema scuro.
 */
data class HeatmapRenderConfig(
    val gridSize: Int = 96,
    val radiusKm: Double = 35.0,
    val metersPerDegLat: Double = 111320.0,
    val cutoffThreshold: Int = 16,
    val featherThreshold: Int = 20,
    val alphaMinLight: Int = 115,
    val alphaMaxLight: Int = 180,
    val alphaMinDark: Int = 125,
    val alphaMaxDark: Int = 195,
    val alphaSpanProb: Float = 65f,
    val plateauTier1: Int = 40,
    val plateauTier2: Int = 60,
    val plateauTier3: Int = 78,
    val plateauTier4: Int = 86,
    val lightColors: List<Int> = listOf(0x4E9648, 0xD49B24, 0xC86430, 0x9E262C),
    val darkColors: List<Int> = listOf(0x62B058, 0xFAB22A, 0xEB6E34, 0xD8343E)
) {
    companion object {
        /**
         * Istanza predefinita con i parametri cartografici canonici di Myco.
         */
        val DEFAULT = HeatmapRenderConfig()
    }
}
