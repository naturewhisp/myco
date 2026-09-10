package github.naturewhisp.myco

import github.naturewhisp.myco.model.SavedLocation
import github.naturewhisp.myco.platform.KeyValueStorage
import github.naturewhisp.myco.repository.CacheManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteLocationTest {

    private class InMemoryKeyValueStorage : KeyValueStorage {
        private val data = mutableMapOf<String, Any?>()

        override fun getString(key: String, defValue: String?): String? =
            data[key] as? String ?: defValue

        override fun putString(key: String, value: String?) {
            if (value != null) data[key] = value else data.remove(key)
        }

        override fun getInt(key: String, defValue: Int): Int =
            data[key] as? Int ?: defValue

        override fun putInt(key: String, value: Int) {
            data[key] = value
        }

        override fun getBoolean(key: String, defValue: Boolean): Boolean =
            data[key] as? Boolean ?: defValue

        override fun putBoolean(key: String, value: Boolean) {
            data[key] = value
        }

        override fun remove(key: String) {
            data.remove(key)
        }

        override fun clear() {
            data.clear()
        }

        override fun getAll(): Map<String, *> = data
    }

    @Test
    fun testSavedLocationEffectiveName() {
        val defaultLoc = SavedLocation(
            lat = 44.2149,
            lon = 7.9755,
            displayName = "Borgo, Garessio, Cuneo, Piemonte",
            shortName = "Borgo",
            savedAt = 1000L
        )
        // Senza customName, effectiveName deve restituire shortName
        assertEquals("Borgo", defaultLoc.effectiveName)

        val blankCustomLoc = defaultLoc.copy(customName = "   ")
        assertEquals("Borgo", blankCustomLoc.effectiveName)

        val renamedLoc = defaultLoc.copy(customName = "Il mio bosco di faggi")
        assertEquals("Il mio bosco di faggi", renamedLoc.effectiveName)
    }

    @Test
    fun testCacheManagerFavoriteRenamingLifecycle() {
        val storage = InMemoryKeyValueStorage()
        val cacheManager = CacheManager(storage)

        val loc = SavedLocation(
            lat = 44.2149,
            lon = 7.9755,
            displayName = "Borgo, Garessio, Cuneo",
            shortName = "Borgo",
            savedAt = 1000L,
            isFavorite = true
        )

        // 1. Aggiunta preferito
        cacheManager.addFavorite(loc)
        assertTrue(cacheManager.isFavorite(44.2149, 7.9755))
        var favs = cacheManager.getFavoriteLocations()
        assertEquals(1, favs.size)
        assertEquals("Borgo", favs[0].effectiveName)
        assertNull(favs[0].customName)

        // 2. Rinomina preferito con un nome elegante scelto dall'utente
        cacheManager.renameFavorite(44.2149, 7.9755, "Bosco dei Porcini")
        favs = cacheManager.getFavoriteLocations()
        assertEquals(1, favs.size)
        assertEquals("Bosco dei Porcini", favs[0].customName)
        assertEquals("Bosco dei Porcini", favs[0].effectiveName)

        // Verifica che anche la voce nei recenti sia sincronizzata
        val recents = cacheManager.getRecentLocations()
        val recentLoc = recents.firstOrNull { it.shortName == "Borgo" }
        assertEquals("Bosco dei Porcini", recentLoc?.customName)

        // 3. Ripristino del toponimo originale passando null o stringa vuota
        cacheManager.renameFavorite(44.2149, 7.9755, null)
        favs = cacheManager.getFavoriteLocations()
        assertNull(favs[0].customName)
        assertEquals("Borgo", favs[0].effectiveName)

        // 4. Rimozione dai preferiti
        cacheManager.removeFavorite(44.2149, 7.9755)
        assertFalse(cacheManager.isFavorite(44.2149, 7.9755))
        assertTrue(cacheManager.getFavoriteLocations().isEmpty())
    }

    @Test
    fun testPlaceholderFilteringAndSanitization() {
        val storage = InMemoryKeyValueStorage()
        val cacheManager = CacheManager(storage)

        // Verifica riconoscimento segnaposto
        assertTrue(SavedLocation.isPlaceholderName("Punto cartografico"))
        assertTrue(SavedLocation.isPlaceholderName("Punto selezionato"))
        assertTrue(SavedLocation.isPlaceholderName("Posizione GPS (44.215, 7.976)"))
        assertTrue(SavedLocation.isPlaceholderName("Localizzazione in corso..."))
        assertTrue(SavedLocation.isPlaceholderName(""))
        assertTrue(SavedLocation.isPlaceholderName(null))
        assertFalse(SavedLocation.isPlaceholderName("Garessio"))
        assertFalse(SavedLocation.isPlaceholderName("Val Veny, Courmayeur (AO)"))

        // Tentativo di salvare un punto cartografico anonimo nei recenti: deve essere ignorato
        val placeholderLoc = SavedLocation(
            lat = 44.1,
            lon = 7.9,
            displayName = "Punto cartografico",
            shortName = "Punto cartografico",
            savedAt = 2000L
        )
        cacheManager.saveRecentLocation(placeholderLoc)
        assertTrue(cacheManager.getRecentLocations().isEmpty())

        // Salvataggio di una località reale con toponimo risolto
        val realLoc = SavedLocation(
            lat = 44.2,
            lon = 7.95,
            displayName = "Garessio, Cuneo, Piemonte",
            shortName = "Garessio",
            savedAt = 3000L
        )
        cacheManager.saveRecentLocation(realLoc)
        val recents = cacheManager.getRecentLocations()
        assertEquals(1, recents.size)
        assertEquals("Garessio", recents[0].shortName)
    }

    @Test
    fun testSafetyDisclaimerPersistence() {
        val storage = InMemoryKeyValueStorage()
        val cacheManager = CacheManager(storage)

        assertFalse(cacheManager.isSafetyDisclaimerAccepted)
        cacheManager.isSafetyDisclaimerAccepted = true
        assertTrue(cacheManager.isSafetyDisclaimerAccepted)
    }
}
