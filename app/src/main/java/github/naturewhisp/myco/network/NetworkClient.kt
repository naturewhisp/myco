package github.naturewhisp.myco.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.NONE // Can set to BODY for debugging
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "MycoPorciniAndroid/1.0 (github.naturewhisp.myco)")
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

    fun <T> createService(serviceClass: Class<T>, baseUrl: String): T {
        return retrofitBuilder.baseUrl(baseUrl).build().create(serviceClass)
    }
}
