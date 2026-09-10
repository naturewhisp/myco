package github.naturewhisp.myco.platform

import java.util.concurrent.ConcurrentHashMap

/**
 * Implementazione pura in-memory di [PlatformCacheStore].
 *
 * Utilizzata per i test unitari ad alta velocità su JVM e come fallback leggero
 * per piattaforme desktop o configurazioni senza persistenza su disco.
 */
class InMemoryCacheStore : PlatformCacheStore {

    private data class Entry(
        val data: String,
        val timestamp: Long,
        val ttlMs: Long,
        val lat: Double?,
        val lon: Double?
    )

    private val entries = ConcurrentHashMap<String, Entry>()

    override fun get(key: String, expiryMs: Long): String? {
        val entry = entries[key] ?: return null
        val now = System.currentTimeMillis()
        val effectiveExpiry = if (entry.ttlMs > 0) entry.ttlMs else expiryMs
        return if (now - entry.timestamp < effectiveExpiry) {
            entry.data
        } else {
            entries.remove(key)
            null
        }
    }

    override fun getIgnoreExpiry(key: String): String? {
        return entries[key]?.data
    }

    override fun put(key: String, dataJson: String, lat: Double?, lon: Double?, ttlMs: Long) {
        entries[key] = Entry(
            data = dataJson,
            timestamp = System.currentTimeMillis(),
            ttlMs = ttlMs,
            lat = lat,
            lon = lon
        )
    }

    override fun remove(key: String) {
        entries.remove(key)
    }

    override fun clear() {
        entries.clear()
    }

    override fun getCacheAge(key: String): Long? {
        val entry = entries[key] ?: return null
        return System.currentTimeMillis() - entry.timestamp
    }

    override fun getCacheStats(): CacheStats {
        var bytes = 0L
        for ((k, v) in entries) {
            bytes += (k.length + v.data.length) * 2L
        }
        return CacheStats(
            totalEntries = entries.size,
            totalSizeBytes = bytes
        )
    }
}
