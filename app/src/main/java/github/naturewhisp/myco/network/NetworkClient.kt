package github.naturewhisp.myco.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Client HTTP centralizzato con configurazione di pooling, timeout e header identificativi conformi alle policy OSM.
 *
 * Configura un'istanza riutilizzabile di [OkHttpClient] con User-Agent customizzato per rispettare
 * i termini di servizio di Nominatim e Overpass, gestendo il parsing JSON tramite [GsonConverterFactory].
 */
object NetworkClient {
    /** Header identificativo inviato con ogni richiesta HTTP conforme alle policy OSM. */
    var userAgent: String = "Myco/1.2 (github.naturewhisp.myco; Android)"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.NONE
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val retrofitBuilder = Retrofit.Builder()
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())

    /**
     * Crea un'implementazione del servizio Retrofit specificato collegato all'URL base fornito.
     *
     * @param T Tipo dell'interfaccia di servizio Retrofit.
     * @param serviceClass Classe Java dell'interfaccia.
     * @param baseUrl URL base dell'endpoint remoto (es. "https://api.open-meteo.com/").
     * @return Istanza proxy del servizio Retrofit pronta all'uso.
     */
    fun <T> createService(serviceClass: Class<T>, baseUrl: String): T {
        return retrofitBuilder.baseUrl(baseUrl).build().create(serviceClass)
    }
}
