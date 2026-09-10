package github.naturewhisp.myco.platform.android

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import github.naturewhisp.myco.platform.CacheStats
import github.naturewhisp.myco.platform.PlatformCacheStore

/**
 * Adapter di persistenza su database SQLite relazionale per la piattaforma Android.
 *
 * Implementa [PlatformCacheStore] memorizzando payload JSON di grandi dimensioni
 * (previsioni meteorologiche a 14gg, dati Overpass OSM, matrici di elevazione)
 * in una tabella SQLite indicizzata con rimozione automatica per TTL (Time-To-Live).
 *
 * @param context Contesto applicativo Android per l'inizializzazione del database.
 */
class AndroidSqliteCacheStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
), PlatformCacheStore {

    companion object {
        private const val DATABASE_NAME = "myco_cache.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_CACHE = "cache_entries"
        private const val COL_KEY = "cache_key"
        private const val COL_LAT = "latitude"
        private const val COL_LON = "longitude"
        private const val COL_DATA = "payload_json"
        private const val COL_TIMESTAMP = "cached_timestamp"
        private const val COL_TTL = "ttl_millis"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_CACHE (
                $COL_KEY TEXT PRIMARY KEY,
                $COL_LAT REAL,
                $COL_LON REAL,
                $COL_DATA TEXT NOT NULL,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_TTL INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cache_coords ON $TABLE_CACHE($COL_LAT, $COL_LON)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cache_timestamp ON $TABLE_CACHE($COL_TIMESTAMP)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CACHE")
        onCreate(db)
    }

    override fun get(key: String, expiryMs: Long): String? {
        return try {
            val db = readableDatabase
            val projection = arrayOf(COL_DATA, COL_TIMESTAMP, COL_TTL)
            val selection = "$COL_KEY = ?"
            val selectionArgs = arrayOf(key)

            db.query(
                TABLE_CACHE,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                null
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    val data = cursor.getString(0)
                    val timestamp = cursor.getLong(1)
                    val ttl = cursor.getLong(2)
                    val now = System.currentTimeMillis()
                    val effectiveExpiry = if (ttl > 0) ttl else expiryMs

                    if (now - timestamp < effectiveExpiry) {
                        data
                    } else {
                        // Elemento scaduto: rimozione immediata
                        remove(key)
                        null
                    }
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun put(key: String, dataJson: String, lat: Double?, lon: Double?, ttlMs: Long) {
        try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COL_KEY, key)
                put(COL_DATA, dataJson)
                put(COL_TIMESTAMP, System.currentTimeMillis())
                put(COL_TTL, ttlMs)
                if (lat != null) put(COL_LAT, lat) else putNull(COL_LAT)
                if (lon != null) put(COL_LON, lon) else putNull(COL_LON)
            }
            db.insertWithOnConflict(TABLE_CACHE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (_: Exception) {
            // Tolleranza guasti: mancata scrittura in cache non deve interrompere l'esperienza utente
        }
    }

    override fun remove(key: String) {
        try {
            val db = writableDatabase
            db.delete(TABLE_CACHE, "$COL_KEY = ?", arrayOf(key))
        } catch (_: Exception) {
            // Tolleranza guasti
        }
    }

    override fun clear() {
        try {
            val db = writableDatabase
            db.delete(TABLE_CACHE, null, null)
        } catch (_: Exception) {
            // Tolleranza guasti
        }
    }

    override fun getCacheAge(key: String): Long? {
        return try {
            val db = readableDatabase
            val projection = arrayOf(COL_TIMESTAMP, COL_TTL)
            val selection = "$COL_KEY = ?"
            val selectionArgs = arrayOf(key)

            db.query(
                TABLE_CACHE,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                null
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    val timestamp = cursor.getLong(0)
                    val ttl = cursor.getLong(1)
                    val age = System.currentTimeMillis() - timestamp
                    val maxAge = if (ttl > 0) ttl else 60 * 60 * 1000L
                    if (age < maxAge) age else null
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun getCacheStats(): CacheStats {
        return try {
            val db = readableDatabase
            db.rawQuery("SELECT COUNT(*), SUM(LENGTH($COL_KEY) + LENGTH($COL_DATA)) FROM $TABLE_CACHE", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    val count = cursor.getInt(0)
                    val bytes = if (!cursor.isNull(1)) cursor.getLong(1) * 2L else 0L
                    CacheStats(totalEntries = count, totalSizeBytes = bytes)
                } else {
                    CacheStats(0, 0L)
                }
            }
        } catch (_: Exception) {
            CacheStats(0, 0L)
        }
    }
}
