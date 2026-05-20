package github.naturewhisp.myco.network

import github.naturewhisp.myco.model.GeocodeResult
import github.naturewhisp.myco.model.OverpassResponse
import github.naturewhisp.myco.model.WeatherResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface GeocodingService {
    @GET("search")
    suspend fun searchLocation(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 1
    ): List<GeocodeResult>
}

interface WeatherService {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("past_days") pastDays: Int = 14,
        @Query("forecast_days") forecastDays: Int = 11,
        @Query("hourly") hourly: String = "temperature_2m,relativehumidity_2m,precipitation",
        @Query("daily") daily: String = "weathercode",
        @Query("timezone") timezone: String = "auto"
    ): WeatherResponse
}

interface OverpassService {
    @GET("api/interpreter")
    suspend fun queryOverpass(
        @Query("data") query: String
    ): OverpassResponse
}
