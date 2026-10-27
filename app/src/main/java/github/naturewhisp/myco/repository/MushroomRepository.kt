package github.naturewhisp.myco.repository

import com.google.gson.Gson
import github.naturewhisp.myco.model.EcologicalCategory
import github.naturewhisp.myco.model.GeocodeResult
import github.naturewhisp.myco.model.HabitatEvidence
import github.naturewhisp.myco.model.HabitatStatus
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
    val spunDataManager: SpunDataManager,
    private val geocodingService: GeocodingService = NetworkClient.createService(
        GeocodingService::class.java,
        "https://nominatim.openstreetmap.org/"
    ),
    private val weatherService: WeatherService = NetworkClient.createService(
        WeatherService::class.java,
        "https://api.open-meteo.com/"
    ),
    private val overpassServices: List<OverpassService> = listOf(
        "https://overpass-api.de/",
        "https://overpass.kumi.systems/",
        "https://overpass.openstreetmap.fr/"
    ).map {
        NetworkClient.createService(OverpassService::class.java, it)
    }
) {
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
    suspend fun reverseGeocode(latitude: Double, longitude: Double): GeocodeResult? =
        reverseGeocode(latitude, longitude, forceRefresh = false)

    suspend fun reverseGeocode(
        latitude: Double,
        longitude: Double,
        forceRefresh: Boolean,
    ): GeocodeResult? {
        val roundedLat = String.format(Locale.US, "%.4f", latitude)
        val roundedLon = String.format(Locale.US, "%.4f", longitude)
        val cacheKey = "rev_${roundedLat}_${roundedLon}"

        if (!forceRefresh) {
            val cached = cacheManager.getCachedData(cacheKey, GeocodeResult::class.java, 7 * 24 * 60 * 60 * 1000) // 7 days
            if (cached != null) {
                return cached
            }
        }

        return try {
            val result = geocodingService.reverseGeocode(latitude, longitude)
            cacheManager.saveCachedData(cacheKey, result)
            result
        } catch (_: Exception) {
            cacheManager.getCachedDataIgnoreExpiry(cacheKey, GeocodeResult::class.java)?.first
        }
    }

    /**
     * Recupera le previsioni e lo storico meteorologico orario e giornaliero da Open-Meteo.
     * In caso di indisponibilità della connessione sul campo, degrada sul dato precedentemente archiviato in locale.
     *
     * @param latitude Latitudine in gradi decimali.
     * @param longitude Longitudine in gradi decimali.
     * @return [WeatherResponse] con temperature, precipitazioni, umidità relativa e parametri orari.
     */
    suspend fun fetchWeather(latitude: Double, longitude: Double): WeatherResponse =
        fetchWeather(latitude, longitude, forceRefresh = false)

    suspend fun fetchWeather(
        latitude: Double,
        longitude: Double,
        forceRefresh: Boolean,
    ): WeatherResponse {
        val roundedLat = String.format(Locale.US, "%.4f", latitude)
        val roundedLon = String.format(Locale.US, "%.4f", longitude)
        val cacheKey = "weather_${roundedLat}_${roundedLon}"

        if (!forceRefresh) {
            val cached = cacheManager.getCachedData(cacheKey, WeatherResponse::class.java, 60 * 60 * 1000) // 1 hour
            if (cached != null) {
                return cached
            }
        }

        return try {
            val response = weatherService.getForecast(latitude, longitude)
            cacheManager.saveCachedData(cacheKey, response)
            response
        } catch (e: Exception) {
            val fallback = cacheManager.getCachedDataIgnoreExpiry(cacheKey, WeatherResponse::class.java)
            fallback?.first ?: throw e
        }
    }

    /**
     * Interroga le API Overpass OSM per stimare la copertura boschiva e forestale attorno al punto.
     *
     * @param latitude Latitudine in gradi decimali.
     * @param longitude Longitudine in gradi decimali.
     * @return [OverpassResponse] con gli elementi boschivi individuati, con cache di 24 ore.
     */
    suspend fun fetchHabitat(latitude: Double, longitude: Double): OverpassResponse? =
        fetchHabitat(latitude, longitude, forceRefresh = false)

    suspend fun fetchHabitat(
        latitude: Double,
        longitude: Double,
        forceRefresh: Boolean,
        searchRadiusMeters: Int = -1,
    ): OverpassResponse? {
        val roundedLat = String.format(Locale.US, "%.4f", latitude)
        val roundedLon = String.format(Locale.US, "%.4f", longitude)
        val radius = if (searchRadiusMeters > 0) searchRadiusMeters else cacheManager.radius
        val maxDistanceMeters = maxOf(radius * 10.0, 15_000.0)
        val cacheKey = "habitat_geom_v2_${radius}m_${roundedLat}_${roundedLon}"

        if (!forceRefresh) {
            val cached = cacheManager.getCachedData(cacheKey, OverpassResponse::class.java, 24 * 60 * 60 * 1000) // 24 hours
            if (cached != null) {
                if (cached.hasDefectiveRelationGeometries()) {
                    cacheManager.cacheStore.remove(cacheKey)
                } else {
                    return cached.sanitize(latitude, longitude, maxDistanceMeters)
                }
            }
        }

        val query = "[out:json][timeout:15];(nwr[\"natural\"~\"wood|grassland|heath\"](around:$radius,$latitude,$longitude);nwr[\"landuse\"~\"forest|meadow|grass|pasture|residential|commercial|industrial\"](around:$radius,$latitude,$longitude);nwr[\"genus\"](around:$radius,$latitude,$longitude););out body geom;"

        var bestSanitizedFallback: OverpassResponse? = null
        for (service in overpassServices) {
            try {
                val response = service.queryOverpass(query)
                val sanitized = response.sanitize(latitude, longitude, maxDistanceMeters)
                if (!response.hasDefectiveRelationGeometries()) {
                    cacheManager.saveCachedData(cacheKey, sanitized)
                    return sanitized
                }
                if (!sanitized.hasDefectiveRelationGeometries() && bestSanitizedFallback == null) {
                    bestSanitizedFallback = sanitized
                }
            } catch (_: Exception) {
                // Prova il mirror Overpass successivo
            }
        }
        if (bestSanitizedFallback != null) {
            cacheManager.saveCachedData(cacheKey, bestSanitizedFallback)
            return bestSanitizedFallback
        }
        val fallback = cacheManager.getCachedDataIgnoreExpiry(cacheKey, OverpassResponse::class.java)?.first
        if (fallback != null) {
            val sanitizedFallback = fallback.sanitize(latitude, longitude, maxDistanceMeters)
            if (sanitizedFallback.hasDefectiveRelationGeometries()) {
                cacheManager.cacheStore.remove(cacheKey)
                return null
            }
            return sanitizedFallback
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
    ): OverpassResponse? = fetchSpecificHabitatBonus(latitude, longitude, species, forceRefresh = false)

    suspend fun fetchSpecificHabitatBonus(
        latitude: Double,
        longitude: Double,
        species: MushroomSpecies? = null,
        forceRefresh: Boolean,
        searchRadiusMeters: Int = -1,
    ): OverpassResponse? {
        val roundedLat = String.format(Locale.US, "%.4f", latitude)
        val roundedLon = String.format(Locale.US, "%.4f", longitude)
        val speciesKey = species?.id ?: "general"
        val radius = if (searchRadiusMeters > 0) searchRadiusMeters else cacheManager.radius
        val maxDistanceMeters = maxOf(radius * 10.0, 15_000.0)
        val cacheKey = "habitat_bonus_${speciesKey}_${radius}m_${roundedLat}_${roundedLon}"

        if (!forceRefresh) {
            val cached = cacheManager.getCachedData(cacheKey, OverpassResponse::class.java, 24 * 60 * 60 * 1000) // 24 hours
            if (cached != null) {
                if (cached.hasDefectiveRelationGeometries()) {
                    cacheManager.cacheStore.remove(cacheKey)
                } else {
                    return cached.sanitize(latitude, longitude, maxDistanceMeters)
                }
            }
        }

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
            "[out:json][timeout:15];(nwr[\"landuse\"~\"meadow|grass|pasture\"](around:$radius,$latitude,$longitude);nwr[\"natural\"~\"grassland|heath\"](around:$radius,$latitude,$longitude););out body geom;"
        } else {
            "[out:json][timeout:15];nwr[\"genus\"~\"$genusRegex\"](around:$radius,$latitude,$longitude);out body geom;"
        }

        var bestSanitizedFallback: OverpassResponse? = null
        for (service in overpassServices) {
            try {
                val response = service.queryOverpass(query)
                val sanitized = response.sanitize(latitude, longitude, maxDistanceMeters)
                if (!response.hasDefectiveRelationGeometries()) {
                    cacheManager.saveCachedData(cacheKey, sanitized)
                    return sanitized
                }
                if (!sanitized.hasDefectiveRelationGeometries() && bestSanitizedFallback == null) {
                    bestSanitizedFallback = sanitized
                }
            } catch (_: Exception) {
                // Prova il mirror Overpass successivo
            }
        }
        if (bestSanitizedFallback != null) {
            cacheManager.saveCachedData(cacheKey, bestSanitizedFallback)
            return bestSanitizedFallback
        }
        val fallback = cacheManager.getCachedDataIgnoreExpiry(cacheKey, OverpassResponse::class.java)?.first
        if (fallback != null) {
            val sanitizedFallback = fallback.sanitize(latitude, longitude, maxDistanceMeters)
            if (sanitizedFallback.hasDefectiveRelationGeometries()) {
                cacheManager.cacheStore.remove(cacheKey)
                return null
            }
            return sanitizedFallback
        }
        return null
    }

    /**
     * Estrae le evidenze vegetazionali, di copertura e di habitat geometrico da una risposta Overpass.
     *
     * Implementa l'invarianza rispetto alla segmentazione poligonale (F08) dividendo l'area di
     * scansione in ottanti spaziali: 1 poligono esteso o 20 sub-poligoni occupano gli stessi settori
     * e generano la medesima stima di copertura.
     *
     * @param response Risposta Overpass deserializzata, o null se non disponibile.
     * @param targetLat Latitudine del punto target.
     * @param targetLon Longitudine del punto target.
     * @param searchRadiusMeters Raggio di scansione in metri.
     * @return [HabitatEvidence] con stato tipizzato, coperture e generi confermati.
     */
    fun extractHabitatEvidence(
        response: OverpassResponse?,
        targetLat: Double,
        targetLon: Double,
        searchRadiusMeters: Int = cacheManager.radius
    ): HabitatEvidence {
        if (response == null) {
            return HabitatEvidence.UNKNOWN_HABITAT
        }
        val maxDistanceMeters = maxOf(searchRadiusMeters * 10.0, 15_000.0)
        val sanitized = response.sanitize(targetLat, targetLon, maxDistanceMeters)
        val rawElements = sanitized.elements.distinctBy { it.type to it.id }.map { el ->
            github.naturewhisp.myco.core.OsmHabitatElement(
                lat = el.coordinate?.first,
                lon = el.coordinate?.second,
                isWoodOrForest = el.isWoodOrForest,
                isMeadowOrGrass = el.isMeadowOrGrass,
                isUrbanOrBuilt = el.isUrbanOrBuilt,
                genus = el.genus,
                leafType = el.leafType,
                elementKey = "${el.type}/${el.id}",
                surfaces = buildList {
                    fun ring(points: List<github.naturewhisp.myco.model.OverpassCenter?>?, inner: Boolean) {
                        val validPoints = points?.filterNotNull()?.filter {
                            it.lat.isFinite() && it.lon.isFinite() && it.lat in -90.0..90.0 && it.lon in -180.0..180.0
                        } ?: return
                        if (validPoints.size >= 2) {
                            add(
                                github.naturewhisp.myco.core.OsmSurface(
                                    validPoints.map { github.naturewhisp.myco.core.GeoCoordinates(it.lat, it.lon) },
                                    inner
                                )
                            )
                        }
                    }
                    ring(el.geometry, false)
                    el.members.orEmpty().forEach { ring(it.geometry, it.role == "inner") }
                },
            )
        }
        return github.naturewhisp.myco.core.MycoAlgorithms.extractHabitatEvidence(
            elements = rawElements,
            targetLat = targetLat,
            targetLon = targetLon,
            searchRadiusMeters = searchRadiusMeters,
        )
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
    suspend fun fetchTerrainAspect(latitude: Double, longitude: Double): TerrainAspectData? =
        fetchTerrainAspect(latitude, longitude, forceRefresh = false)

    suspend fun fetchTerrainAspect(
        latitude: Double,
        longitude: Double,
        forceRefresh: Boolean,
    ): TerrainAspectData? {
        val roundedLat = String.format(Locale.US, "%.4f", latitude)
        val roundedLon = String.format(Locale.US, "%.4f", longitude)
        val cacheKey = "terrain_${roundedLat}_${roundedLon}"

        if (!forceRefresh) {
            val cached = cacheManager.getCachedData(cacheKey, TerrainAspectData::class.java, 30L * 24 * 60 * 60 * 1000L) // 30 giorni
            if (cached != null) {
                return if (cached.rawElevations.isNullOrEmpty() || cached.rawElevations.size < 5) {
                    val synthesized = cached.getOrSynthesizeRawElevations().map { it.toFloat() }
                    val upgraded = cached.copy(rawElevations = synthesized)
                    cacheManager.saveCachedData(cacheKey, upgraded)
                    upgraded
                } else {
                    cached
                }
            }
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
                val fallback = cacheManager.getCachedDataIgnoreExpiry(cacheKey, TerrainAspectData::class.java)?.first
                if (fallback != null && (fallback.rawElevations.isNullOrEmpty() || fallback.rawElevations.size < 5)) {
                    val synthesized = fallback.getOrSynthesizeRawElevations().map { it.toFloat() }
                    val upgraded = fallback.copy(rawElevations = synthesized)
                    cacheManager.saveCachedData(cacheKey, upgraded)
                    upgraded
                } else {
                    fallback
                }
            }
        } catch (_: Exception) {
            val fallback = cacheManager.getCachedDataIgnoreExpiry(cacheKey, TerrainAspectData::class.java)?.first
            if (fallback != null && (fallback.rawElevations.isNullOrEmpty() || fallback.rawElevations.size < 5)) {
                val synthesized = fallback.getOrSynthesizeRawElevations().map { it.toFloat() }
                val upgraded = fallback.copy(rawElevations = synthesized)
                cacheManager.saveCachedData(cacheKey, upgraded)
                upgraded
            } else {
                fallback
            }
        }
    }

    /**
     * Interroga Overpass per individuare il poligono o nodo forestale reale più vicino alle coordinate fornite.
     * Risolve il debito tecnico TD-01 sostituendo lo spostamento empirico statico con uno snap geospaziale autentico.
     *
     * @param latitude Latitudine del punto corrente in gradi decimali.
     * @param longitude Longitudine del punto corrente in gradi decimali.
     * @return Coppia (latitudine, longitudine) del baricentro del bosco più vicino, o null se non trovato o offline.
     */
    suspend fun findNearestForest(latitude: Double, longitude: Double): Pair<Double, Double>? {
        val query = "[out:json][timeout:10];(nwr[\"natural\"=\"wood\"](around:5000,$latitude,$longitude);nwr[\"landuse\"=\"forest\"](around:5000,$latitude,$longitude););out center 20;"
        for (service in overpassServices) {
            try {
                val response = service.queryOverpass(query)
                val candidates = response.elements.mapNotNull { it.coordinate }
                if (candidates.isNotEmpty()) {
                    var closest = candidates[0]
                    var minDistance = Double.MAX_VALUE
                    for (coord in candidates) {
                        val dist = MushroomAlgorithms.haversineDistanceKm(latitude, longitude, coord.first, coord.second)
                        if (dist in 0.03..minDistance) {
                            minDistance = dist
                            closest = coord
                        }
                    }
                    if (minDistance < 10.0) {
                        return closest
                    }
                }
            } catch (_: Exception) {
                // Prova il mirror Overpass successivo
            }
        }
        return null
    }

    /**
     * Esegue il precaricamento sincrono e la persistenza offline in SQLite di tutti i layer ambientali
     * (meteo, elevazione orografica DEM, habitat boschivo e alberi simbionti guida) per una località.
     */
    suspend fun prefetchCompleteLocation(latitude: Double, longitude: Double, species: MushroomSpecies? = null) =
        prefetchCompleteLocation(latitude, longitude, species, forceRefresh = false)

    suspend fun prefetchCompleteLocation(
        latitude: Double,
        longitude: Double,
        species: MushroomSpecies? = null,
        forceRefresh: Boolean,
    ) {
        try { fetchWeather(latitude, longitude, forceRefresh = forceRefresh) } catch (_: Exception) {}
        try { fetchTerrainAspect(latitude, longitude, forceRefresh = forceRefresh) } catch (_: Exception) {}
        try { fetchHabitat(latitude, longitude, forceRefresh = forceRefresh) } catch (_: Exception) {}
        try { fetchSpecificHabitatBonus(latitude, longitude, species, forceRefresh = forceRefresh) } catch (_: Exception) {}
        try { reverseGeocode(latitude, longitude, forceRefresh = forceRefresh) } catch (_: Exception) {}
    }
}
