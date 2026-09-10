package github.naturewhisp.myco.model

/**
 * Risultato delle metriche micorriziche SPUN aggregate per una specifica coordinata geografica.
 *
 * @property ecmRichness Numero stimato di specie fungine ectomicorriziche presenti nel substrato.
 * @property hyphalDensity Densità media della rete ifale sotterranea espressa in metri per cm³ di suolo.
 * @property ecmScore Punteggio normalizzato della biodiversità EcM (0.0..1.0).
 * @property hyphalScore Punteggio normalizzato della biomassa ifale (0.0..1.0).
 * @property ecmText Stringa descrittiva formattata per la card fattore Herbarium.
 * @property hyphalText Stringa descrittiva della densità ifale con unità di misura.
 * @property regionCode Codice regionale della griglia binaria SPUN (es. "ALP").
 * @property regionName Nome geografico descrittivo della regione SPUN (es. "Italia e Arco Alpino").
 * @property ecmQualityLabel Giudizio qualitativo sintetico della ricchezza simbiotica.
 * @property hyphalVitalityLabel Giudizio sintetico della vitalità del micelio sotterraneo.
 */
data class SpunData(
    val ecmRichness: Float,
    val hyphalDensity: Float,
    val ecmScore: Double,
    val hyphalScore: Double,
    val ecmText: String,
    val hyphalText: String,
    val regionCode: String,
    val regionName: String,
    val ecmQualityLabel: String = "Ideale",
    val hyphalVitalityLabel: String = "Attiva"
)

/**
 * Metadati decodificati dall'header binario proprietario di una regione SPUN (32 byte).
 *
 * @property magic Sequenza identificativa del formato (es. "SPUN").
 * @property version Versione della struttura dati binaria.
 * @property regionCode Codice identificativo della macroregione geografica.
 * @property minLat Latitudine meridionale minima della griglia in gradi decimali.
 * @property maxLat Latitudine settentrionale massima della griglia in gradi decimali.
 * @property minLon Longitudine occidentale minima della griglia in gradi decimali.
 * @property maxLon Longitudine orientale massima della griglia in gradi decimali.
 * @property width Numero di celle orizzontali (colonne) della matrice.
 * @property height Numero di celle verticali (righe) della matrice.
 * @property stepArcSec Risoluzione angolare di ciascuna cella in secondi d'arco.
 */
data class SpunRegionHeader(
    val magic: String,
    val version: Int,
    val regionCode: String,
    val minLat: Float,
    val maxLat: Float,
    val minLon: Float,
    val maxLon: Float,
    val width: Int,
    val height: Int,
    val stepArcSec: Int
)

/**
 * Descrittore di catalogo per il caricamento on-demand degli asset binari regionali SPUN.
 *
 * @property regionCode Codice identificativo della regione (es. "ALP").
 * @property displayName Denominazione leggibile per l'utente.
 * @property assetFileName Percorso relativo dell'asset compresso all'interno dell'app.
 * @property minLat Latitudine minima coperta dalla regione.
 * @property maxLat Latitudine massima coperta dalla regione.
 * @property minLon Longitudine minima coperta dalla regione.
 * @property maxLon Longitudine massima coperta dalla regione.
 */
data class SpunRegionDescriptor(
    val regionCode: String,
    val displayName: String,
    val assetFileName: String,
    val minLat: Float,
    val maxLat: Float,
    val minLon: Float,
    val maxLon: Float
) {
    /**
     * Determina se la coordinata geografica WGS84 specificata ricade all'interno del bounding box regionale.
     *
     * @param lat Latitudine in gradi decimali.
     * @param lon Longitudine in gradi decimali.
     * @return True se il punto è all'interno della regione, False altrimenti.
     */
    fun contains(lat: Double, lon: Double): Boolean {
        return lat in minLat..maxLat && lon in minLon..maxLon
    }
}
