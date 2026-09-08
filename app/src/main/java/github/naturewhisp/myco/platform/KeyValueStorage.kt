package github.naturewhisp.myco.platform

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Astrazione di persistenza chiave-valore per disaccoppiare la logica applicativa
 * da [android.content.SharedPreferences] e consentire implementazioni native macOS
 * (es. NSUserDefaults o storage su file).
 */
interface KeyValueStorage {
    fun getString(key: String, defValue: String? = null): String?
    fun putString(key: String, value: String?)
    fun getInt(key: String, defValue: Int): Int
    fun putInt(key: String, value: Int)
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun remove(key: String)
    fun clear()
    fun getAll(): Map<String, *>
}

/**
 * Implementazione Android di [KeyValueStorage] basata su [SharedPreferences].
 */
class AndroidSharedPreferencesStorage(
    private val prefs: SharedPreferences
) : KeyValueStorage {

    constructor(context: Context, name: String = "myco_cache") : this(
        context.getSharedPreferences(name, Context.MODE_PRIVATE)
    )

    override fun getString(key: String, defValue: String?): String? = prefs.getString(key, defValue)

    override fun putString(key: String, value: String?) {
        prefs.edit {
            if (value != null) putString(key, value) else remove(key)
        }
    }

    override fun getInt(key: String, defValue: Int): Int = prefs.getInt(key, defValue)

    override fun putInt(key: String, value: Int) {
        prefs.edit { putInt(key, value) }
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean = prefs.getBoolean(key, defValue)

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit { putBoolean(key, value) }
    }

    override fun remove(key: String) {
        prefs.edit { remove(key) }
    }

    override fun clear() {
        prefs.edit { clear() }
    }

    override fun getAll(): Map<String, *> = prefs.all
}
