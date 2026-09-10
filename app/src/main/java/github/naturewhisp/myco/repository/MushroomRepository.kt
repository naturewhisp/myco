package github.naturewhisp.myco.repository

import com.google.gson.Gson
import github.naturewhisp.myco.model.EcologicalCategory
import github.naturewhisp.myco.model.GeocodeResult
import github.naturewhisp.myco.model.MushroomSpecies
import github.naturewhisp.myco.model.OverpassResponse
import github.naturewhisp.myco.model.SpunData
import github.naturewhisp.myco.model.TerrainAspectConfig
import github.naturewhisp.myco.model.TerrainAspectData
import github.naturewhisp.myco.model.WeatherResponse
import github.naturewhisp.myco.network.GeocodingService
import github.naturewhisp.myco.network.NetworkClient
import github.naturewhisp.myco.network.OverpassService
import github.naturewhisp.myco.network.WeatherService
import github.naturewhisp.myco.utils.MushroomAlgorithms
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max

/**
 * Repository centrale per l'aggregazione e il coordinamento delle sorgenti dati remote e locali.
 *
 * Gestisce l'interrogazione delle API REST (Nominatim, Open-Meteo, Overpass OSM), la lettura
 * degli asset biologici SPUN e applica strategie di caching multilivello con TTL differenziati
 * tramite [CacheManager]. Totalmente privo di import `android.*`.
 *
 * @param cacheManager Gestore della cache multilivello [CacheManager].
 * @property spunDataManager Gestore degli asset di biodiversità del consorzio SPUN [SpunDataManager].
 */
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
    private val overpassServices: List<OverpassService> = overpassEndpoints.map {
        NetworkClient.createService(OverpassService::class.java, it)
    }

    private val gson = Gson()

    /**
     * Esegue la geocodifica diretta per nome toponomastico o indirizzo testuale.
     *
     * @param query Testo digitato dall'utente per la ricerca della località.
     * @return [GeocodeResult] con le coordinate trovate, o null se nessun riscontro.
     */
    suspend fun searchLocation(query: String): GeocodeResult? {
        val cacheKey = query.lowercase().trim()
        val cached = cacheManager.getGeocodeCache(cacheKey)
        if (cached != null) {
            try {
                return gson.fromJson(cached, GeocodeResult::class.java)
            } catch (_: Exception) {
                // Procedi con la query di rete
            }
        }

        return try {
            val results = geocodingService.searchLocation(query)
            if (results.isNotEmpty()) {
                val first = results[0]
                cacheManager.saveGeocodeCache(cacheKey, gson.toJson(first))
                first
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Esegue il reverse geocoding per ottenere il toponimo dalle coordinate geografiche.
     *
     * @param latitude Latitudine in gradi decimali.
     * @param longitude Longitudine in gradi decimali.
     * @return [GeocodeResult] con l'indirizzo risolto, o null se non disponibile.
     */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): GeocodeResult? {
        val roundedLat = String.format(Locale.US, "%.4f", latitude)
        val roundedLon = String.format(Locale.US, "%.4f", longitude)
        val cacheKey = "rev_${roundedLat}_${roundedLon}"

        val cached = cacheManager.getCachedData(cacheKey, GeocodeResult::class.java, 7 * 24 * 60 * 60 * 1000) // 7 days
        if (cached != null) {
            return cached
        }

        return try {
            val result = geocodingService.reverseGeocode(latitude, longitude)
            cacheManager.saveCachedData(cacheKey, result)
            result
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Recupera le previsioni e lo storico meteorologico orario e giornaliero da Open-Meteo.
     *
     * @param latitude Latitudine in gradi decimali.
     * @param longitude Longitudine in gradi decimali.
     * @return [WeatherResponse] con temperature, precipitazioni, umidità relativa e parametri orari, con cache di 1 ora.
     */
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

    /**
     * Interroga le API Overpass OSM per stimare la copertura boschiva e forestale attorno al punto.
     *
     * @param latitude Latitudine in gradi decimali.
     * @param longitude Longitudine in gradi decimali.
     * @return [OverpassResponse] con gli elementi boschivi individuati, con cache di 24 ore.
     */
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

        for (service in overpassServices) {
            try {
                val response = service.queryOverpass(query)
                cacheManager.saveCachedData(cacheKey, response)
                return response
            } catch (_: Exception) {
                // Prova il mirror Overpass successivo
            }
        }
        return null
    }

    /**
     * Interroga Overpass per rilevare la presenza di generi arborei forestali specifici e simbionti
     * parametrati sulle preferenze ecologiche della specie micologica selezionata.
     *
     * @param latitude Latitudine in gradi decimali.
     * @param longitude Longitudine in gradi decimali.
     * @param species Specie micologica target per filtrare i generi arborei simbionti (OSM genus).
     * @return [OverpassResponse] con essenze arboree rilevate, con cache di 24 ore isolata per specie.
     */
    suspend fun fetchSpecificHabitatBonus(
        latitude: Double,
        longitude: Double,
        species: MushroomSpecies? = null
    ): OverpassResponse? {
        val roundedLat = String.format(Locale.US, "%.4f", latitude)
        val roundedLon = String.format(Locale.US, "%.4f", longitude)
        val speciesKey = species?.id ?: "general"
        val cacheKey = "habitat_bonus_${speciesKey}_${roundedLat}_${roundedLon}"

        val cached = cacheManager.getCachedData(cacheKey, OverpassResponse::class.java, 24 * 60 * 60 * 1000) // 24 hours
        if (cached != null) {
            return cached
        }

        val radius = cacheManager.radius
        val knownGeneraMap = mapOf(
            "fagus" to "Fagus",
            "quercus" to "Quercus",
            "castanea" to "Castanea",
            "pinus" to "Pinus",
            "picea" to "Picea",
            "abies" to "Abies",
            "betula" to "Betula",
            "larix" to "Larix",
            "populus" to "Populus",
            "salix" to "Salix",
            "ostrya" to "Ostrya",
            "carpinus" to "Carpinus",
            "corylus" to "Corylus"
        )
        val targetGenera = species?.preferredCanopyTypes?.mapNotNull { knownGeneraMap[it.lowercase()] } ?: emptyList()
        val genusRegex = if (targetGenera.isNotEmpty()) {
            targetGenera.distinct().joinToString("|")
        } else {
            "Fagus|Quercus|Castanea|Pinus|Picea|Abies"
        }

        val query = if (species?.category == EcologicalCategory.SAPROTROPHIC) {
            "[out:json];(nwr[\"landuse\"~\"meadow|grass|pasture\"](around:$radius,$latitude,$longitude);nwr[\"natural\"~\"grassland|heath\"](around:$radius,$latitude,$longitude);nwr[\"leaf_type\"~\"broadleaved|needleleaved\"](around:$radius,$latitude,$longitude););out body;"
        } else {
            "[out:json];(nwr[\"leaf_type\"~\"broadleaved|needleleaved\"](around:$radius,$latitude,$longitude);nwr[\"genus\"~\"$genusRegex\"](around:$radius,$latitude,$longitude););out body;"
        }

        for (service in overpassServices) {
            try {
                val response = service.queryOverpass(query)
                cacheManager.saveCachedData(cacheKey, response)
                return response
            } catch (_: Exception) {
                // Prova il mirror Overpass successivo
            }
        }
        return null
    }

    /**
     * Esegue l'interrogazione geospaziale degli asset binari SPUN tramite [SpunDataManager].
     *
     * @param latitude Latitudine in gradi decimali.
     * @param longitude Longitudine in gradi decimali.
     * @param radiusMeters Raggio di scansione in metri.
     * @return [SpunData] con ricchezza EcM e densità ifale, o null se fuori copertura.
     */
    suspend fun fetchSpunData(latitude: Double, longitude: Double, radiusMeters: Int): SpunData? {
        return spunDataManager.getSpunData(latitude, longitude, radiusMeters)
    }

    /**
     * Calcola pendenza ed esposizione del versante tramite campionamento DEM a 5 punti su Open-Meteo.
     *
     * @param latitude Latitudine centrale in gradi decimali.
     * @param longitude Longitudine centrale in gradi decimali.
     * @return [TerrainAspectData] con i dati orografici calcolati, con cache di 30 giorni.
     */
    suspend fun fetchTerrainAspect(latitude: Double, longitude: Double): TerrainAspectData? {
        val roundedLat = String.format(Locale.US, "%.4f", latitude)
        val roundedLon = String.format(Locale.US, "%.4f", longitude)
        val cacheKey = "terrain_${roundedLat}_${roundedLon}"

        val cached = cacheManager.getCachedData(cacheKey, TerrainAspectData::class.java, 30L * 24 * 60 * 60 * 1000L) // 30 giorni
        if (cached != null) {
            return cached
        }

        return try {
            val deltaMeters = TerrainAspectConfig.DEFAULT.deltaMeters
            val metersPerDegLat = 111139.0
            val latRad = Math.toRadians(latitude)
            val metersPerDegLon = 111139.0 * cos(latRad)
            val deltaLat = deltaMeters / metersPerDegLat
            val deltaLon = deltaMeters / max(1.0, metersPerDegLon)

            val latN = latitude + deltaLat
            val latS = latitude - deltaLat
            val lonE = longitude + deltaLon
            val lonW = longitude - deltaLon

            // Batch request per 5 punti: Centro, Nord, Sud, Est, Ovest
            val latStr = String.format(Locale.US, "%.6f,%.6f,%.6f,%.6f,%.6f", latitude, latN, latS, latitude, latitude)
            val lonStr = String.format(Locale.US, "%.6f,%.6f,%.6f,%.6f,%.6f", longitude, longitude, longitude, lonE, lonW)

            val response = weatherService.getElevation(latStr, lonStr)
            if (response.elevation.size >= 5) {
                val terrainData = MushroomAlgorithms.calculateTerrainAspect(response.elevation, deltaMeters)
                cacheManager.saveCachedData(cacheKey, terrainData)
                terrainData
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
