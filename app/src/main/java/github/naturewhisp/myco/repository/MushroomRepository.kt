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
    suspend fun fetchWeather(latitude: Double, longitude: Double): WeatherResponse {
        val roundedLat = String.format(Locale.US, "%.4f", latitude)
        val roundedLon = String.format(Locale.US, "%.4f", longitude)
        val cacheKey = "weather_${roundedLat}_${roundedLon}"

        val cached = cacheManager.getCachedData(cacheKey, WeatherResponse::class.java, 60 * 60 * 1000) // 1 hour
        if (cached != null) {
            return cached
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
    suspend fun fetchHabitat(latitude: Double, longitude: Double): OverpassResponse? {
        val roundedLat = String.format(Locale.US, "%.4f", latitude)
        val roundedLon = String.format(Locale.US, "%.4f", longitude)
        val radius = cacheManager.radius
        val cacheKey = "habitat_${radius}m_${roundedLat}_${roundedLon}"

        val cached = cacheManager.getCachedData(cacheKey, OverpassResponse::class.java, 24 * 60 * 60 * 1000) // 24 hours
        if (cached != null) {
            return cached
        }

        val query = "[out:json];(nwr[\"natural\"=\"wood\"](around:$radius,$latitude,$longitude);nwr[\"landuse\"=\"forest\"](around:$radius,$latitude,$longitude);nwr[\"landuse\"~\"meadow|grass|pasture\"](around:$radius,$latitude,$longitude);nwr[\"natural\"~\"grassland|heath\"](around:$radius,$latitude,$longitude);nwr[\"landuse\"~\"residential|commercial|industrial\"](around:$radius,$latitude,$longitude););out tags center;"

        for (service in overpassServices) {
            try {
                val response = service.queryOverpass(query)
                cacheManager.saveCachedData(cacheKey, response)
                return response
            } catch (_: Exception) {
                // Prova il mirror Overpass successivo
            }
        }
        return cacheManager.getCachedDataIgnoreExpiry(cacheKey, OverpassResponse::class.java)?.first
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
        val radius = cacheManager.radius
        val cacheKey = "habitat_bonus_${speciesKey}_${radius}m_${roundedLat}_${roundedLon}"

        val cached = cacheManager.getCachedData(cacheKey, OverpassResponse::class.java, 24 * 60 * 60 * 1000) // 24 hours
        if (cached != null) {
            return cached
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
            "[out:json];(nwr[\"landuse\"~\"meadow|grass|pasture\"](around:$radius,$latitude,$longitude);nwr[\"natural\"~\"grassland|heath\"](around:$radius,$latitude,$longitude););out tags center;"
        } else {
            "[out:json];nwr[\"genus\"~\"$genusRegex\"](around:$radius,$latitude,$longitude);out tags center;"
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
        return cacheManager.getCachedDataIgnoreExpiry(cacheKey, OverpassResponse::class.java)?.first
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
        val elements = response.elements
        if (elements.isEmpty()) {
            return HabitatEvidence(
                status = HabitatStatus.KNOWN_UNSUITABLE,
                forestCoverFraction = 0.0,
                meadowFraction = 0.0,
                distanceToNearestForestMeters = searchRadiusMeters.toDouble(),
                confirmedHostGenera = emptySet(),
                dominantLeafType = null
            )
        }

        val forestElements = elements.filter { it.isWoodOrForest }
        val meadowElements = elements.filter { it.isMeadowOrGrass }
        val urbanElements = elements.filter { it.isUrbanOrBuilt }

        val confirmedGenera = elements.mapNotNull { it.genus }.toSet()
        val leafTypes = elements.mapNotNull { it.leafType }
        val dominantLeafType = when {
            leafTypes.contains("mixed") || (leafTypes.contains("broadleaved") && leafTypes.contains("needleleaved")) -> "mixed"
            leafTypes.contains("broadleaved") -> "broadleaved"
            leafTypes.contains("needleleaved") -> "needleleaved"
            else -> null
        }

        val forestDistances = forestElements.mapNotNull { el ->
            el.coordinate?.let { (lat, lon) ->
                MushroomAlgorithms.haversineDistanceKm(targetLat, targetLon, lat, lon) * 1000.0
            }
        }
        val minForestDist = forestDistances.minOrNull() ?: searchRadiusMeters.toDouble()

        // Calcolo della copertura forestale con invarianza rispetto alla segmentazione poligonale (F08, REG-11)
        val forestSectors = BooleanArray(8)
        for (el in forestElements) {
            val coord = el.coordinate ?: continue
            val dist = MushroomAlgorithms.haversineDistanceKm(targetLat, targetLon, coord.first, coord.second) * 1000.0
            if (dist <= searchRadiusMeters) {
                val dLat = coord.first - targetLat
                val dLon = (coord.second - targetLon) * cos(Math.toRadians(targetLat))
                var angle = Math.toDegrees(kotlin.math.atan2(dLon, dLat))
                if (angle < 0) angle += 360.0
                val sector = (angle / 45.0).toInt().coerceIn(0, 7)
                forestSectors[sector] = true
            }
        }
        val coveredForestSectors = forestSectors.count { it }
        val baseForestCover = coveredForestSectors / 8.0

        val forestCoverFraction = when {
            minForestDist <= 50.0 -> max(baseForestCover, 0.75)
            minForestDist <= 150.0 -> max(baseForestCover, 0.50)
            else -> baseForestCover
        }.coerceIn(0.0, 1.0)

        val meadowSectors = BooleanArray(8)
        for (el in meadowElements) {
            val coord = el.coordinate ?: continue
            val dist = MushroomAlgorithms.haversineDistanceKm(targetLat, targetLon, coord.first, coord.second) * 1000.0
            if (dist <= searchRadiusMeters) {
                val dLat = coord.first - targetLat
                val dLon = (coord.second - targetLon) * cos(Math.toRadians(targetLat))
                var angle = Math.toDegrees(kotlin.math.atan2(dLon, dLat))
                if (angle < 0) angle += 360.0
                val sector = (angle / 45.0).toInt().coerceIn(0, 7)
                meadowSectors[sector] = true
            }
        }
        val meadowFraction = (meadowSectors.count { it } / 8.0).coerceIn(0.0, 1.0)

        val isUrbanDominant = urbanElements.size > (forestElements.size + meadowElements.size) && forestCoverFraction < 0.20
        val status = when {
            isUrbanDominant -> HabitatStatus.KNOWN_UNSUITABLE
            forestCoverFraction > 0.10 || meadowFraction > 0.10 -> HabitatStatus.KNOWN_SUITABLE
            else -> HabitatStatus.KNOWN_UNSUITABLE
        }

        return HabitatEvidence(
            status = status,
            forestCoverFraction = forestCoverFraction,
            meadowFraction = meadowFraction,
            distanceToNearestForestMeters = minForestDist,
            confirmedHostGenera = confirmedGenera,
            dominantLeafType = dominantLeafType
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
                cacheManager.getCachedDataIgnoreExpiry(cacheKey, TerrainAspectData::class.java)?.first
            }
        } catch (_: Exception) {
            cacheManager.getCachedDataIgnoreExpiry(cacheKey, TerrainAspectData::class.java)?.first
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
    suspend fun prefetchCompleteLocation(latitude: Double, longitude: Double, species: MushroomSpecies? = null) {
        try { fetchWeather(latitude, longitude) } catch (_: Exception) {}
        try { fetchTerrainAspect(latitude, longitude) } catch (_: Exception) {}
        try { fetchHabitat(latitude, longitude) } catch (_: Exception) {}
        try { fetchSpecificHabitatBonus(latitude, longitude, species) } catch (_: Exception) {}
        try { reverseGeocode(latitude, longitude) } catch (_: Exception) {}
    }
}
