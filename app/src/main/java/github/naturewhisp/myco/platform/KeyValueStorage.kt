package github.naturewhisp.myco.platform

/**
 * Astrazione di persistenza chiave-valore per disaccoppiare la logica applicativa
 * da `android.content.SharedPreferences` e consentire implementazioni native macOS
 * (es. NSUserDefaults o storage su file).
 */
interface KeyValueStorage {
    /**
     * Recupera una stringa memorizzata associata alla chiave.
     *
     * @param key Chiave identificativa.
     * @param defValue Valore predefinito se la chiave non esiste.
     * @return Stringa memorizzata o [defValue].
     */
    fun getString(key: String, defValue: String? = null): String?

    /**
     * Memorizza una stringa associata alla chiave. Se [value] è null, rimuove la chiave.
     *
     * @param key Chiave identificativa.
     * @param value Valore da memorizzare, oppure null per cancellare.
     */
    fun putString(key: String, value: String?)

    /**
     * Recupera un intero memorizzato.
     *
     * @param key Chiave identificativa.
     * @param defValue Valore predefinito se la chiave non esiste.
     * @return Intero memorizzato o [defValue].
     */
    fun getInt(key: String, defValue: Int): Int

    /**
     * Memorizza un intero associato alla chiave.
     *
     * @param key Chiave identificativa.
     * @param value Valore intero da persistere.
     */
    fun putInt(key: String, value: Int)

    /**
     * Recupera un valore booleano memorizzato.
     *
     * @param key Chiave identificativa.
     * @param defValue Valore predefinito se la chiave non esiste.
     * @return Booleano memorizzato o [defValue].
     */
    fun getBoolean(key: String, defValue: Boolean): Boolean

    /**
     * Memorizza un booleano associato alla chiave.
     *
     * @param key Chiave identificativa.
     * @param value Valore booleano da persistere.
     */
    fun putBoolean(key: String, value: Boolean)

    /**
     * Rimuove una voce di configurazione.
     *
     * @param key Chiave identificativa da eliminare.
     */
    fun remove(key: String)

    /**
     * Cancella integralmente tutte le chiavi memorizzate nello storage.
     */
    fun clear()

    /**
     * Restituisce una mappa immutabile con tutte le coppie chiave-valore correnti.
     *
     * @return Mappa di tutti gli elementi persistiti.
     */
    fun getAll(): Map<String, *>
}
