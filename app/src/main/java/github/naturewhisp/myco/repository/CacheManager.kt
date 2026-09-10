package github.naturewhisp.myco.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import github.naturewhisp.myco.model.SavedLocation
import github.naturewhisp.myco.platform.KeyValueStorage
import java.util.Locale

/**
 * Gestore centralizzato della persistenza su disco e della cache multi-livello di Myco.
 *
 * Totalmente disaccoppiato da Android, opera esclusivamente tramite l'astrazione [KeyValueStorage],
 * gestendo preferenze utente, cronologia toponomastica, località preferite e cache con TTL di risposte API.
 *
 * @param storage Astrazione di persistenza chiave-valore [KeyValueStorage].
 */
class CacheManager(private val storage: KeyValueStorage) {

    private val gson = Gson()

    companion object {
        private const val KEY_MAP_STYLE = "settings_map_style"
        private const val KEY_RADIUS = "settings_radius"
        private const val KEY_THRESHOLD = "settings_threshold"
        private const val KEY_CACHE_ENABLED = "settings_cache_enabled"
        private const val KEY_USE_LOCAL_AI = "settings_use_local_ai"
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

    fun <T> getCachedData(key: String, classType: Class<T>, expiryMs: Long): T? {
        if (!cacheEnabled) return null
        val cachedStr = storage.getString(key, null) ?: return null
        return try {
            val wrapper = gson.fromJson(cachedStr, CacheWrapper::class.java) ?: return null
            if (System.currentTimeMillis() - wrapper.timestamp < expiryMs) {
                gson.fromJson(wrapper.dataJson, classType)
            } else {
                storage.remove(key)
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun <T> saveCachedData(key: String, data: T) {
        if (!cacheEnabled) return
        try {
            val dataJson = gson.toJson(data)
            val wrapper = CacheWrapper(timestamp = System.currentTimeMillis(), dataJson = dataJson)
            val wrapperJson = gson.toJson(wrapper)
            storage.putString(key, wrapperJson)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getGeocodeCache(query: String): String? {
        if (!cacheEnabled) return null
        return storage.getString("geo_query_${query.lowercase()}", null)
    }

    fun saveGeocodeCache(query: String, dataJson: String) {
        if (!cacheEnabled) return
        storage.putString("geo_query_${query.lowercase()}", dataJson)
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
        val json = storage.getString(key, null) ?: return null
        return try {
            val wrapper = gson.fromJson(json, CacheWrapper::class.java) ?: return null
            val age = System.currentTimeMillis() - wrapper.timestamp
            if (age < 60 * 60 * 1000L) age else null  // null se scaduto (>1h)
        } catch (_: Exception) {
            null
        }
    }

    // ── Clear ─────────────────────────────────────────────────────────────────

    fun clearRecentLocations() {
        storage.remove(KEY_RECENT_LOCATIONS)
    }

    fun clearCache() {
        val mapStyleVal = mapStyle
        val radiusVal = radius
        val thresholdVal = threshold
        val cacheEnabledVal = cacheEnabled
        val useLocalAiVal = useLocalAi
        val recentsJson = storage.getString(KEY_RECENT_LOCATIONS, null)
        val favsJson = storage.getString(KEY_FAVORITE_LOCATIONS, null)

        storage.clear()

        mapStyle = mapStyleVal
        radius = radiusVal
        threshold = thresholdVal
        cacheEnabled = cacheEnabledVal
        useLocalAi = useLocalAiVal
        recentsJson?.let { json -> storage.putString(KEY_RECENT_LOCATIONS, json) }
        favsJson?.let { json -> storage.putString(KEY_FAVORITE_LOCATIONS, json) }
    }

    fun getCacheSizeString(): String {
        val allEntries = storage.getAll()
        val settingsKeys = setOf(KEY_MAP_STYLE, KEY_RADIUS, KEY_THRESHOLD, KEY_CACHE_ENABLED,
            KEY_USE_LOCAL_AI, KEY_RECENT_LOCATIONS, KEY_FAVORITE_LOCATIONS)
        var totalChars = 0
        var cacheItemCount = 0
        for ((key, value) in allEntries) {
            if (key !in settingsKeys) {
                totalChars += key.length
                if (value is String) totalChars += value.length
                cacheItemCount++
            }
        }
        if (cacheItemCount == 0) return "Vuota"

        val bytes = totalChars * 2
        return if (bytes < 1024) {
            "$bytes B ($cacheItemCount elementi)"
        } else if (bytes < 1024 * 1024) {
            String.format(Locale.getDefault(), "%.1f KB (%d elementi)", bytes / 1024.0, cacheItemCount)
        } else {
            String.format(Locale.getDefault(), "%.1f MB (%d elementi)", bytes / (1024.0 * 1024.0), cacheItemCount)
        }
    }

    private data class CacheWrapper(
        val timestamp: Long,
        val dataJson: String
    )
}
