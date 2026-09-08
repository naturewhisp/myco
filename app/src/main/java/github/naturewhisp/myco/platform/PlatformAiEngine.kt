package github.naturewhisp.myco.platform

import kotlinx.coroutines.flow.StateFlow

/**
 * Stato del motore di inferenza AI locale su dispositivo.
 */
enum class AiEngineStatus {
    NOT_SUPPORTED,
    INITIALIZING,
    DOWNLOADING,
    DOWNLOAD_FAILED,
    READY
}

/**
 * Astrazione del motore di intelligenza artificiale locale su dispositivo.
 * Consente l'integrazione con Google AI Edge AICore su Android e con
 * CoreML / Apple Intelligence / MLX / Ollama su macOS.
 */
interface PlatformAiEngine {
    val status: StateFlow<AiEngineStatus>
    fun isAvailable(): Boolean
    suspend fun generateAdvancedSummary(prompt: String): String?
}
