package github.naturewhisp.myco.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import github.naturewhisp.myco.model.SavedLocation
import github.naturewhisp.myco.platform.InMemoryCacheStore
import github.naturewhisp.myco.platform.KeyValueStorage
import github.naturewhisp.myco.platform.PlatformCacheStore
import java.util.Locale

/**
 * Gestore centralizzato della persistenza su disco e della cache multi-livello di Myco.
 *
 * Disaccoppiato dalle piattaforme native secondo l'architettura esagonale:
 * - Preferenze utente stabili, cronologia e preferiti sono memorizzati su [KeyValueStorage].
 * - Risposte di rete pesanti e geospaziali (meteo 14gg, Overpass OSM, DEM) su [PlatformCacheStore].
 *
 * @param storage Astrazione di persistenza chiave-valore [KeyValueStorage].
 * @param cacheStore Astrazione per lo storage strutturato e indicizzato della cache [PlatformCacheStore].
 */
class CacheManager(
    private val storage: KeyValueStorage,
    val cacheStore: PlatformCacheStore = InMemoryCacheStore()
) {

    private val gson = Gson()

    companion object {
        private const val KEY_MAP_STYLE = "settings_map_style"
        private const val KEY_RADIUS = "settings_radius"
        private const val KEY_THRESHOLD = "settings_threshold"
        private const val KEY_CACHE_ENABLED = "settings_cache_enabled"
        private const val KEY_USE_LOCAL_AI = "settings_use_local_ai"
        private const val KEY_SAFETY_DISCLAIMER_ACCEPTED = "safety_disclaimer_accepted"
        private const val KEY_RECENT_LOCATIONS = "recent_locations"
        private const val KEY_FAVORITE_LOCATIONS = "favorite_locations"
        private const val MAX_RECENT = 8
    }

    var mapStyle: String
        get() = storage.getString(KEY_MAP_STYLE, "standard") ?: "standard"
        set(value) = storage.putString(KEY_MAP_STYLE, value)

    var radius: Int
        get() = storage.getInt(KEY_RADIUS, 1500)
        set(value) = storage.putInt(KEY_RADIUS, value)

    var threshold: Int
        get() = storage.getInt(KEY_THRESHOLD, 65)
        set(value) = storage.putInt(KEY_THRESHOLD, value)

    var cacheEnabled: Boolean
        get() = storage.getBoolean(KEY_CACHE_ENABLED, true)
        set(value) = storage.putBoolean(KEY_CACHE_ENABLED, value)

    var useLocalAi: Boolean
        get() = storage.getBoolean(KEY_USE_LOCAL_AI, true)
        set(value) = storage.putBoolean(KEY_USE_LOCAL_AI, value)

    var isSafetyDisclaimerAccepted: Boolean
        get() = storage.getBoolean(KEY_SAFETY_DISCLAIMER_ACCEPTED, false)
        set(value) = storage.putBoolean(KEY_SAFETY_DISCLAIMER_ACCEPTED, value)

    fun <T> getCachedData(key: String, classType: Class<T>, expiryMs: Long): T? {
        if (!cacheEnabled) return null
        val cachedStr = cacheStore.get(key, expiryMs) ?: return null
        return try {
            gson.fromJson(cachedStr, classType)
        } catch (_: Exception) {
            null
        }
    }

    fun <T> saveCachedData(key: String, data: T, lat: Double? = null, lon: Double? = null, ttlMs: Long = 0) {
        if (!cacheEnabled) return
        try {
            val dataJson = gson.toJson(data)
            cacheStore.put(key, dataJson, lat, lon, ttlMs)
        } catch (_: Exception) {
            // Tolleranza guasti
        }
    }

    fun getGeocodeCache(query: String): String? {
        if (!cacheEnabled) return null
        return cacheStore.get("geo_query_${query.lowercase()}", 7 * 24 * 60 * 60 * 1000L)
    }

    fun saveGeocodeCache(query: String, dataJson: String) {
        if (!cacheEnabled) return
        cacheStore.put("geo_query_${query.lowercase()}", dataJson, ttlMs = 7 * 24 * 60 * 60 * 1000L)
    }

    // ── Recent locations ──────────────────────────────────────────────────────

    fun getRecentLocations(): List<SavedLocation> {
        val json = storage.getString(KEY_RECENT_LOCATIONS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<SavedLocation>>() {}.type
            val list = gson.fromJson<List<SavedLocation>>(json, type) ?: emptyList()
            val sanitized = list.filter { loc ->
                !SavedLocation.isPlaceholderName(loc.displayName) &&
                !SavedLocation.isPlaceholderName(loc.shortName)
            }
            if (sanitized.size != list.size) {
                storage.putString(KEY_RECENT_LOCATIONS, gson.toJson(sanitized))
            }
            sanitized
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveRecentLocation(loc: SavedLocation) {
        if (SavedLocation.isPlaceholderName(loc.displayName) || SavedLocation.isPlaceholderName(loc.shortName)) {
            return
        }
        val current = getRecentLocations().toMutableList()
        // Dedup: rimuovi entry con stesse coordinate (~111m, 3 decimali)
        val roundedLat = String.format(Locale.US, "%.3f", loc.lat)
        val roundedLon = String.format(Locale.US, "%.3f", loc.lon)
        current.removeAll { loc2 ->
            String.format(Locale.US, "%.3f", loc2.lat) == roundedLat &&
            String.format(Locale.US, "%.3f", loc2.lon) == roundedLon
        }
        current.add(0, loc.copy(savedAt = System.currentTimeMillis()))
        val trimmed = current.take(MAX_RECENT)
        storage.putString(KEY_RECENT_LOCATIONS, gson.toJson(trimmed))
    }

    fun removeRecentLocation(lat: Double, lon: Double) {
        val rLat = String.format(Locale.US, "%.3f", lat)
        val rLon = String.format(Locale.US, "%.3f", lon)
        val recents = getRecentLocations().toMutableList()
        recents.removeAll {
            String.format(Locale.US, "%.3f", it.lat) == rLat &&
            String.format(Locale.US, "%.3f", it.lon) == rLon
        }
        storage.putString(KEY_RECENT_LOCATIONS, gson.toJson(recents))
    }

    // ── Favorites ─────────────────────────────────────────────────────────────

    fun getFavoriteLocations(): List<SavedLocation> {
        val json = storage.getString(KEY_FAVORITE_LOCATIONS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<SavedLocation>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun isFavorite(lat: Double, lon: Double): Boolean {
        val rLat = String.format(Locale.US, "%.3f", lat)
        val rLon = String.format(Locale.US, "%.3f", lon)
        return getFavoriteLocations().any { fav ->
            String.format(Locale.US, "%.3f", fav.lat) == rLat &&
            String.format(Locale.US, "%.3f", fav.lon) == rLon
        }
    }

    fun addFavorite(loc: SavedLocation) {
        val favs = getFavoriteLocations().toMutableList()
        val rLat = String.format(Locale.US, "%.3f", loc.lat)
        val rLon = String.format(Locale.US, "%.3f", loc.lon)
        val idx = favs.indexOfFirst {
            String.format(Locale.US, "%.3f", it.lat) == rLat &&
            String.format(Locale.US, "%.3f", it.lon) == rLon
        }
        val existingCustomName = if (idx >= 0) favs[idx].customName else loc.customName
        val favoriteLoc = loc.copy(
            isFavorite = true,
            savedAt = System.currentTimeMillis(),
            customName = existingCustomName
        )
        if (idx >= 0) {
            favs[idx] = favoriteLoc
        } else {
            favs.add(0, favoriteLoc)
        }
        storage.putString(KEY_FAVORITE_LOCATIONS, gson.toJson(favs))
        // Aggiorna anche nella cronologia recenti
        saveRecentLocation(favoriteLoc)
    }

    /** Assegna o rimuove un nome personalizzato per un preferito */
    fun renameFavorite(lat: Double, lon: Double, customName: String?) {
        val rLat = String.format(Locale.US, "%.3f", lat)
        val rLon = String.format(Locale.US, "%.3f", lon)
        val trimmed = customName?.trim()?.takeIf { it.isNotEmpty() }

        val favs = getFavoriteLocations().toMutableList()
        val fIdx = favs.indexOfFirst {
            String.format(Locale.US, "%.3f", it.lat) == rLat &&
            String.format(Locale.US, "%.3f", it.lon) == rLon
        }
        if (fIdx >= 0) {
            favs[fIdx] = favs[fIdx].copy(customName = trimmed)
            storage.putString(KEY_FAVORITE_LOCATIONS, gson.toJson(favs))
        }

        val recents = getRecentLocations().toMutableList()
        val rIdx = recents.indexOfFirst {
            String.format(Locale.US, "%.3f", it.lat) == rLat &&
            String.format(Locale.US, "%.3f", it.lon) == rLon
        }
        if (rIdx >= 0) {
            recents[rIdx] = recents[rIdx].copy(customName = trimmed)
            storage.putString(KEY_RECENT_LOCATIONS, gson.toJson(recents))
        }
    }

    fun removeFavorite(lat: Double, lon: Double) {
        val rLat = String.format(Locale.US, "%.3f", lat)
        val rLon = String.format(Locale.US, "%.3f", lon)
        val favs = getFavoriteLocations().toMutableList()
        favs.removeAll {
            String.format(Locale.US, "%.3f", it.lat) == rLat &&
            String.format(Locale.US, "%.3f", it.lon) == rLon
        }
        storage.putString(KEY_FAVORITE_LOCATIONS, gson.toJson(favs))
        // Aggiorna anche nella cronologia recenti
        val recents = getRecentLocations().toMutableList()
        val idx = recents.indexOfFirst {
            String.format(Locale.US, "%.3f", it.lat) == rLat &&
            String.format(Locale.US, "%.3f", it.lon) == rLon
        }
        if (idx >= 0) {
            recents[idx] = recents[idx].copy(isFavorite = false)
            storage.putString(KEY_RECENT_LOCATIONS, gson.toJson(recents))
        }
    }

    // ── Cache age ─────────────────────────────────────────────────────────────

    /** Restituisce l'età in ms del dato meteo in cache, null se non presente o scaduto */
    fun getWeatherCacheAge(lat: Double, lon: Double): Long? {
        val roundedLat = String.format(Locale.US, "%.4f", lat)
        val roundedLon = String.format(Locale.US, "%.4f", lon)
        val key = "weather_${roundedLat}_${roundedLon}"
        return cacheStore.getCacheAge(key)
    }

    // ── Clear ─────────────────────────────────────────────────────────────────

    fun clearRecentLocations() {
        storage.remove(KEY_RECENT_LOCATIONS)
    }

    /**
     * Svuota completamente la cache effimera delle risposte di rete (meteo, Overpass, geocoding).
     *
     * Preserva integralmente le preferenze utente, le località salvate, i preferiti
     * e lo stato di accettazione del disclaimer di sicurezza memorizzati in [KeyValueStorage].
     */
    fun clearCache() {
        cacheStore.clear()
    }

    fun getCacheSizeString(): String {
        val stats = cacheStore.getCacheStats()
        if (stats.totalEntries == 0) return "Vuota"

        val bytes = stats.totalSizeBytes
        return if (bytes < 1024) {
            "$bytes B (${stats.totalEntries} elementi)"
        } else if (bytes < 1024 * 1024) {
            String.format(Locale.getDefault(), "%.1f KB (%d elementi)", bytes / 1024.0, stats.totalEntries)
        } else {
            String.format(Locale.getDefault(), "%.1f MB (%d elementi)", bytes / (1024.0 * 1024.0), stats.totalEntries)
        }
    }
}
