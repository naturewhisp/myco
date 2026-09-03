package github.naturewhisp.myco.repository

import com.google.gson.Gson
import github.naturewhisp.myco.model.GeocodeResult
import github.naturewhisp.myco.model.OverpassResponse
import github.naturewhisp.myco.model.WeatherResponse
import github.naturewhisp.myco.model.SpunData
import github.naturewhisp.myco.network.GeocodingService
import github.naturewhisp.myco.network.NetworkClient
import github.naturewhisp.myco.network.OverpassService
import github.naturewhisp.myco.network.WeatherService
import java.util.Locale

class MushroomRepository(
    private val cacheManager: CacheManager,
    val spunDataManager: SpunDataManager
) {
    private val geocodingService = NetworkClient.createService(
        GeocodingService::class.java,
        "https://nominatim.openstreetmap.org/"
    )
    private val weatherService = NetworkClient.createService(
        WeatherService::class.java,
        "https://api.open-meteo.com/"
    )
    private val overpassEndpoints = listOf(
        "https://overpass-api.de/",
        "https://overpass.kumi.systems/",
        "https://overpass.openstreetmap.fr/"
    )

    private val gson = Gson()

    suspend fun searchLocation(query: String): GeocodeResult? {
        val cacheKey = query.lowercase().trim()
        val cached = cacheManager.getGeocodeCache(cacheKey)
        if (cached != null) {
            try {
                return gson.fromJson(cached, GeocodeResult::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val results = geocodingService.searchLocation(query)
        if (results.isNotEmpty()) {
            val result = results[0]
            cacheManager.saveGeocodeCache(cacheKey, gson.toJson(result))
            return result
        }
        return null
    }

    suspend fun reverseGeocode(latitude: Double, longitude: Double): GeocodeResult? {
        val roundedLat = String.format(Locale.US, "%.3f", latitude)
        val roundedLon = String.format(Locale.US, "%.3f", longitude)
        val cacheKey = "reverse_${roundedLat}_${roundedLon}"

        val cached = cacheManager.getCachedData(cacheKey, GeocodeResult::class.java, 7 * 24 * 60 * 60 * 1000L)
        if (cached != null) return cached

        return try {
            val result = geocodingService.reverseGeocode(latitude, longitude)
            cacheManager.saveCachedData(cacheKey, result)
            result
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun fetchWeather(latitude: Double, longitude: Double): WeatherResponse {
        val roundedLat = String.format(Locale.US, "%.4f", latitude)
        val roundedLon = String.format(Locale.US, "%.4f", longitude)
        val cacheKey = "weather_${roundedLat}_${roundedLon}"

        val cached = cacheManager.getCachedData(cacheKey, WeatherResponse::class.java, 60 * 60 * 1000) // 1 hour
        if (cached != null) {
            return cached
        }

        val response = weatherService.getForecast(latitude, longitude)
        cacheManager.saveCachedData(cacheKey, response)
        return response
    }

    suspend fun fetchHabitat(latitude: Double, longitude: Double): OverpassResponse? {
        val roundedLat = String.format(Locale.US, "%.4f", latitude)
        val roundedLon = String.format(Locale.US, "%.4f", longitude)
        val cacheKey = "habitat_${roundedLat}_${roundedLon}"

        val cached = cacheManager.getCachedData(cacheKey, OverpassResponse::class.java, 24 * 60 * 60 * 1000) // 24 hours
        if (cached != null) {
            return cached
        }

        val radius = cacheManager.radius
        val query = "[out:json];(nwr[\"natural\"=\"wood\"](around:$radius,$latitude,$longitude);nwr[\"landuse\"=\"forest\"](around:$radius,$latitude,$longitude););out body;"

        for (baseUrl in overpassEndpoints) {
            try {
                val service = NetworkClient.createService(OverpassService::class.java, baseUrl)
                val response = service.queryOverpass(query)
                cacheManager.saveCachedData(cacheKey, response)
                return response
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }

    suspend fun fetchSpecificHabitatBonus(latitude: Double, longitude: Double): OverpassResponse? {
        val roundedLat = String.format(Locale.US, "%.4f", latitude)
        val roundedLon = String.format(Locale.US, "%.4f", longitude)
        val cacheKey = "habitat_bonus_${roundedLat}_${roundedLon}"

        val cached = cacheManager.getCachedData(cacheKey, OverpassResponse::class.java, 24 * 60 * 60 * 1000) // 24 hours
        if (cached != null) {
            return cached
        }

        val radius = cacheManager.radius
        val query = "[out:json];(nwr[\"leaf_type\"~\"broadleaved|needleleaved\"](around:$radius,$latitude,$longitude);nwr[\"genus\"~\"Fagus|Quercus|Castanea|Pinus|Picea|Abies\"](around:$radius,$latitude,$longitude););out body;"

        for (baseUrl in overpassEndpoints) {
            try {
                val service = NetworkClient.createService(OverpassService::class.java, baseUrl)
                val response = service.queryOverpass(query)
                cacheManager.saveCachedData(cacheKey, response)
                return response
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }

    suspend fun fetchSpunData(latitude: Double, longitude: Double, radiusMeters: Int): SpunData? {
        return spunDataManager.getSpunData(latitude, longitude, radiusMeters)
    }
}
