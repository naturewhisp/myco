package github.naturewhisp.myco.network

import android.content.Context
import android.os.Build
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig
import com.google.ai.edge.aicore.DownloadConfig
import com.google.ai.edge.aicore.DownloadCallback
import com.google.ai.edge.aicore.GenerativeAIException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class LocalAiService(private val context: Context) {

    enum class Status {
        NOT_SUPPORTED,
        INITIALIZING,
        DOWNLOADING,
        DOWNLOAD_FAILED,
        READY
    }

    private val _status = MutableStateFlow(Status.INITIALIZING)
    val status: StateFlow<Status> = _status.asStateFlow()

    private var generativeModel: GenerativeModel? = null

    init {
        initModel()
    }

    private fun initModel() {
        // AICore is officially supported on Android 14 (U) and above
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            _status.value = Status.NOT_SUPPORTED
            return
        }

        try {
            val config = generationConfig {
                this.context = this@LocalAiService.context
                temperature = 0.2f // Bassa temperatura per risposte stabili e meno allucinazioni
                topK = 16
                maxOutputTokens = 512
            }

            val downloadCallback = object : DownloadCallback {
                override fun onDownloadStarted(bytesToDownload: Long) {
                    _status.value = Status.DOWNLOADING
                }

                override fun onDownloadProgress(totalBytesDownloaded: Long) {
                    _status.value = Status.DOWNLOADING
                }

                override fun onDownloadCompleted() {
                    _status.value = Status.READY
                }

                override fun onDownloadFailed(failureStatus: String, e: GenerativeAIException) {
                    _status.value = Status.DOWNLOAD_FAILED
                }

                override fun onDownloadDidNotStart(e: GenerativeAIException) {
                    _status.value = Status.DOWNLOAD_FAILED
                }

                override fun onDownloadPending() {
                    _status.value = Status.DOWNLOADING
                }
            }

            val downloadConfig = DownloadConfig(downloadCallback)

            generativeModel = GenerativeModel(
                generationConfig = config,
                downloadConfig = downloadConfig
            )

            // Di default consideriamo il modello pronto se l'istanziazione ha successo.
            // I callback verranno comunque chiamati se si avvia o riprende un download.
            _status.value = Status.READY

        } catch (e: NoClassDefFoundError) {
            _status.value = Status.NOT_SUPPORTED
        } catch (e: Exception) {
            _status.value = Status.NOT_SUPPORTED
        }
    }

    fun isAvailable(): Boolean {
        return _status.value == Status.READY && generativeModel != null
    }

    suspend fun generateAdvancedSummary(prompt: String): String? {
        if (!isAvailable()) return null
        return withContext(Dispatchers.Default) {
            try {
                val response = generativeModel?.generateContent(prompt)
                response?.text
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
