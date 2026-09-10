package github.naturewhisp.myco.platform

/**
 * Metriche aggregate relative allo stato di riempimento della cache applicativa.
 *
 * @property totalEntries Numero totale di record presenti nella cache.
 * @property totalSizeBytes Dimensione aggregata stimata in byte del payload memorizzato.
 */
data class CacheStats(
    val totalEntries: Int,
    val totalSizeBytes: Long
)

/**
 * Porta di persistenza per la cache geospaziale e strutturata di Myco (Pattern Architettura Esagonale).
 *
 * Disaccoppia la logica di business e i repository dalle implementazioni
 * concrete di database (SQLite relazionale su Android, SQLite nativo C-API / SQLDelight su iOS,
 * InMemory per test unitari JVM) senza alcuna dipendenza verso il runtime del sistema operativo.
 */
interface PlatformCacheStore {

    /**
     * Recupera il dato serializzato associato a [key] se presente e non scaduto.
     *
     * @param key Chiave univoca dell'elemento in cache.
     * @param expiryMs Finestra temporale massima di validità in millisecondi.
     * @return Stringa JSON o null se assente o scaduto.
     */
    fun get(key: String, expiryMs: Long): String?

    /**
     * Recupera il dato serializzato associato a [key] ignorando la scadenza del TTL.
     * Utilizzato per il fallback di resilienza sul campo in assenza di connessione di rete.
     *
     * @param key Chiave univoca dell'elemento in cache.
     * @return Stringa JSON o null se assente.
     */
    fun getIgnoreExpiry(key: String): String?

    /**
     * Memorizza o aggiorna un dato serializzato in formato JSON.
     *
     * @param key Chiave univoca dell'elemento in cache.
     * @param dataJson Payload serializzato in formato JSON.
     * @param lat Latitudine opzionale per indicizzazione geospaziale.
     * @param lon Longitudine opzionale per indicizzazione geospaziale.
     * @param ttlMs Durata di vita massima in millisecondi (0 per indicare default o persistenza).
     */
    fun put(key: String, dataJson: String, lat: Double? = null, lon: Double? = null, ttlMs: Long = 0)

    /**
     * Rimuove un elemento dalla cache in base alla chiave.
     *
     * @param key Chiave dell'elemento da eliminare.
     */
    fun remove(key: String)

    /**
     * Svuota integralmente tutti gli elementi della cache applicativa.
     */
    fun clear()

    /**
     * Restituisce l'età in millisecondi del record in cache, oppure null se inesistente.
     *
     * @param key Chiave dell'elemento.
     * @return Età del record in millisecondi rispetto al tempo corrente di sistema.
     */
    fun getCacheAge(key: String): Long?

    /**
     * Calcola le metriche aggregate della cache (conteggio elementi e occupazione in byte).
     *
     * @return [CacheStats] con conteggio record e dimensione stimata.
     */
    fun getCacheStats(): CacheStats
}
