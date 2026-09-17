package github.naturewhisp.myco

import github.naturewhisp.myco.core.SpeciesCatalog
import github.naturewhisp.myco.model.SPECIES_CATALOG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class CanonicalSpeciesSafetyParityTest {
    @Test
    fun sharedCatalogMatchesAndroidScientificAndSafetyData() {
        assertEquals(SPECIES_CATALOG.map { it.id }, SpeciesCatalog.all.map { it.id })

        SPECIES_CATALOG.zip(SpeciesCatalog.all).forEach { (android, shared) ->
            assertEquals(android.id, shared.id)
            assertEquals(android.vernacularName, shared.vernacularName)
            assertEquals(android.binomialName, shared.binomialName)
            assertEquals(android.category.name, shared.category.name)
            assertEquals(android.minElevation, shared.minElevation)
            assertEquals(android.maxElevation, shared.maxElevation)
            assertEquals(android.idealElevationMin, shared.idealElevationMin)
            assertEquals(android.idealElevationMax, shared.idealElevationMax)
            assertEquals(android.idealTempMin.toDouble(), shared.idealTempMin, 0.0001)
            assertEquals(android.idealTempMax.toDouble(), shared.idealTempMax, 0.0001)
            assertEquals(android.toleratedTempMin.toDouble(), shared.toleratedTempMin, 0.0001)
            assertEquals(android.toleratedTempMax.toDouble(), shared.toleratedTempMax, 0.0001)
            assertEquals(android.minRainAccumulation.toDouble(), shared.minRainAccumulation, 0.0001)
            assertEquals(android.preferredCanopyTypes, shared.preferredCanopyTypes)
            assertEquals(android.fruitingPeriodDescription, shared.fruitingPeriodDescription)
            assertEquals(android.activeMonths, shared.activeMonths)
            assertEquals(android.toxicLookAlikes, shared.toxicLookAlikes)
            assertEquals(android.edibilityWarning, shared.edibilityWarning)
        }
    }

    @Test
    fun everyCatalogEntryCarriesExplicitSafetyMetadata() {
        SpeciesCatalog.all.forEach { species ->
            assertNotNull("${species.id} must carry an edibility warning", species.edibilityWarning)
            if (species.id != "general") {
                assertFalse("${species.id} must list toxic look-alikes", species.toxicLookAlikes.isEmpty())
            }
        }
    }
}
