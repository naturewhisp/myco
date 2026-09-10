package github.naturewhisp.myco.platform

import kotlinx.coroutines.flow.StateFlow

/**
 * Stato del motore di inferenza AI locale on-device.
 */
enum class AiEngineStatus {
    /** Hardware o versione del sistema operativo non supportata. */
    NOT_SUPPORTED,
    /** Inizializzazione dei binding di sistema in corso. */
    INITIALIZING,
    /** Modello di linguaggio in fase di download o aggiornamento in background. */
    DOWNLOADING,
    /** Download del modello non riuscito. */
    DOWNLOAD_FAILED,
    /** Modello on-device caricato in memoria e pronto per l'inferenza. */
    READY
}

/**
 * Astrazione agnostica del motore di intelligenza artificiale locale su dispositivo.
 *
 * Consente l'integrazione con Google AI Edge AICore su Android e con
 * CoreML / Apple Intelligence / MLX / Ollama su macOS.
 */
interface PlatformAiEngine {
    /**
     * Flusso di stato reattivo [StateFlow] del ciclo di vita del modello linguistico.
     */
    val status: StateFlow<AiEngineStatus>

    /**
     * Verifica la disponibilità operativa del modello per l'esecuzione immediata di inferenze.
     *
     * @return True se il modello è pronto ([AiEngineStatus.READY]), False altrimenti.
     */
    fun isAvailable(): Boolean

    /**
     * Esegue la generazione del riassunto testuale avanzato a partire da un prompt di contesto.
     *
     * @param prompt Testo contenente i dati ambientali ed ecologici da analizzare.
     * @return Stringa del responso generato dal modello, o null in caso di errore.
     */
    suspend fun generateAdvancedSummary(prompt: String): String?
}
