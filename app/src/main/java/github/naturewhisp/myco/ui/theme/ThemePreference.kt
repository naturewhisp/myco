package github.naturewhisp.myco.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Modalità tema selezionabile dall'utente
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

internal val Context.themeDataStore by preferencesDataStore(name = "theme_preferences")

// Gestore della persistenza delle preferenze sul tema grafico
class ThemePreference(private val context: Context) {
    private val themeModeKey = stringPreferencesKey("theme_mode")

    val themeMode: Flow<ThemeMode> = context.themeDataStore.data.map { preferences ->
        val modeName = preferences[themeModeKey] ?: ThemeMode.SYSTEM.name
        try {
            ThemeMode.valueOf(modeName)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.themeDataStore.edit { preferences ->
            preferences[themeModeKey] = mode.name
        }
    }
}
