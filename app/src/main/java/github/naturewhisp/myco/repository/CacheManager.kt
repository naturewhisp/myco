package github.naturewhisp.myco.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

class CacheManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("myco_cache", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_MAP_STYLE = "settings_map_style"
        private const val KEY_RADIUS = "settings_radius"
        private const val KEY_THRESHOLD = "settings_threshold"
        private const val KEY_CACHE_ENABLED = "settings_cache_enabled"
    }

    var mapStyle: String
        get() = prefs.getString(KEY_MAP_STYLE, "topo") ?: "topo"
        set(value) = prefs.edit().putString(KEY_MAP_STYLE, value).apply()

    var radius: Int
        get() = prefs.getInt(KEY_RADIUS, 1500)
        set(value) = prefs.edit().putInt(KEY_RADIUS, value).apply()

    var threshold: Int
        get() = prefs.getInt(KEY_THRESHOLD, 65)
        set(value) = prefs.edit().putInt(KEY_THRESHOLD, value).apply()

    var cacheEnabled: Boolean
        get() = prefs.getBoolean(KEY_CACHE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_CACHE_ENABLED, value).apply()

    fun <T> getCachedData(key: String, classType: Class<T>, expiryMs: Long): T? {
        if (!cacheEnabled) return null
        val cachedStr = prefs.getString(key, null) ?: return null
        return try {
            val wrapper = gson.fromJson(cachedStr, CacheWrapper::class.java) ?: return null
            if (System.currentTimeMillis() - wrapper.timestamp < expiryMs) {
                gson.fromJson(wrapper.dataJson, classType)
            } else {
                prefs.edit().remove(key).apply()
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
            prefs.edit().putString(key, wrapperJson).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getGeocodeCache(query: String): String? {
        if (!cacheEnabled) return null
        return prefs.getString("geo_query_${query.lowercase()}", null)
    }

    fun saveGeocodeCache(query: String, dataJson: String) {
        if (!cacheEnabled) return
        prefs.edit().putString("geo_query_${query.lowercase()}", dataJson).apply()
    }

    fun clearCache() {
        val mapStyleVal = mapStyle
        val radiusVal = radius
        val thresholdVal = threshold
        val cacheEnabledVal = cacheEnabled

        prefs.edit().clear().apply()

        mapStyle = mapStyleVal
        radius = radiusVal
        threshold = thresholdVal
        cacheEnabled = cacheEnabledVal
    }

    fun getCacheSizeString(): String {
        val allEntries = prefs.all
        var totalChars = 0
        var cacheItemCount = 0
        for ((key, value) in allEntries) {
            if (key != KEY_MAP_STYLE && key != KEY_RADIUS && key != KEY_THRESHOLD && key != KEY_CACHE_ENABLED) {
                totalChars += key.length
                if (value is String) {
                    totalChars += value.length
                }
                cacheItemCount++
            }
        }
        if (cacheItemCount == 0) return "Vuota"

        val bytes = totalChars * 2
        return if (bytes < 1024) {
            "$bytes B ($cacheItemCount elementi)"
        } else if (bytes < 1024 * 1024) {
            String.format("%.1f KB (%d elementi)", bytes / 1024.0, cacheItemCount)
        } else {
            String.format("%.1f MB (%d elementi)", bytes / (1024.0 * 1024.0), cacheItemCount)
        }
    }

    private data class CacheWrapper(
        val timestamp: Long,
        val dataJson: String
    )
}
