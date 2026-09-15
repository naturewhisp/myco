package github.naturewhisp.myco

import github.naturewhisp.myco.model.CanopyCategory
import github.naturewhisp.myco.model.DeepMaxentModelConfig
import github.naturewhisp.myco.model.DeepMaxentPrediction
import github.naturewhisp.myco.model.ElevationBracket
import github.naturewhisp.myco.model.MushroomSightingSubmission
import github.naturewhisp.myco.model.PhenologicalStage
import github.naturewhisp.myco.model.TargetGroupBackgroundSite
import github.naturewhisp.myco.model.VerificationTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CitizenScienceModelTest {

    @Test
    fun testElevationBracketMapping() {
        assertEquals(ElevationBracket.SUB_MONTANE_0_400, ElevationBracket.fromElevation(0.0))
        assertEquals(ElevationBracket.SUB_MONTANE_0_400, ElevationBracket.fromElevation(250.0))
        assertEquals(ElevationBracket.LOW_MONTANE_400_800, ElevationBracket.fromElevation(400.0))
        assertEquals(ElevationBracket.LOW_MONTANE_400_800, ElevationBracket.fromElevation(750.0))
        assertEquals(ElevationBracket.MID_MONTANE_800_1200, ElevationBracket.fromElevation(800.0))
        assertEquals(ElevationBracket.MID_MONTANE_800_1200, ElevationBracket.fromElevation(1150.0))
        assertEquals(ElevationBracket.HIGH_MONTANE_1200_1600, ElevationBracket.fromElevation(1200.0))
        assertEquals(ElevationBracket.HIGH_MONTANE_1200_1600, ElevationBracket.fromElevation(1422.0)) // Bric Mindino
        assertEquals(ElevationBracket.SUB_ALPINE_1600_2000, ElevationBracket.fromElevation(1600.0))
        assertEquals(ElevationBracket.SUB_ALPINE_1600_2000, ElevationBracket.fromElevation(1950.0))
        assertEquals(ElevationBracket.ALPINE_ABOVE_2000, ElevationBracket.fromElevation(2000.0))
        assertEquals(ElevationBracket.ALPINE_ABOVE_2000, ElevationBracket.fromElevation(3200.0))

        // Negative values clamped to lowest bracket
        assertEquals(ElevationBracket.SUB_MONTANE_0_400, ElevationBracket.fromElevation(-50.0))
    }

    @Test
    fun testPhenologicalStagePropertiesAndResolution() {
        assertTrue(PhenologicalStage.FRESH_BUTTON.weightMultiplier > PhenologicalStage.MATURE_EXPANDED.weightMultiplier)
        assertTrue(PhenologicalStage.MATURE_EXPANDED.weightMultiplier > PhenologicalStage.OVERRIPE_SENESCENT.weightMultiplier)
        assertEquals(1.20, PhenologicalStage.FRESH_BUTTON.weightMultiplier, 0.001)
        assertEquals(1.00, PhenologicalStage.MATURE_EXPANDED.weightMultiplier, 0.001)
        assertEquals(0.70, PhenologicalStage.OVERRIPE_SENESCENT.weightMultiplier, 0.001)

        assertEquals(PhenologicalStage.FRESH_BUTTON, PhenologicalStage.fromCode("FRESH_BUTTON"))
        assertEquals(PhenologicalStage.FRESH_BUTTON, PhenologicalStage.fromCode("fresh_button"))
        assertEquals(PhenologicalStage.OVERRIPE_SENESCENT, PhenologicalStage.fromCode("OVERRIPE_SENESCENT"))
        assertEquals(PhenologicalStage.MATURE_EXPANDED, PhenologicalStage.fromCode("UNKNOWN_CODE"))
        assertEquals(PhenologicalStage.MATURE_EXPANDED, PhenologicalStage.fromCode(null))
    }

    @Test
    fun testVerificationTierReliability() {
        assertTrue(VerificationTier.EXPERT_VERIFIED.reliabilityWeight > VerificationTier.ON_DEVICE_AI_CONFIRMED.reliabilityWeight)
        assertTrue(VerificationTier.ON_DEVICE_AI_CONFIRMED.reliabilityWeight > VerificationTier.MANUAL_UNCONFIRMED.reliabilityWeight)
        assertEquals(0.85, VerificationTier.ON_DEVICE_AI_CONFIRMED.reliabilityWeight, 0.001)
        assertEquals(0.40, VerificationTier.MANUAL_UNCONFIRMED.reliabilityWeight, 0.001)
        assertEquals(1.00, VerificationTier.EXPERT_VERIFIED.reliabilityWeight, 0.001)

        assertEquals(VerificationTier.ON_DEVICE_AI_CONFIRMED, VerificationTier.fromCode("ON_DEVICE_AI_CONFIRMED"))
        assertEquals(VerificationTier.MANUAL_UNCONFIRMED, VerificationTier.fromCode(null))
    }

    @Test
    fun testCanopyCategoryResolution() {
        assertEquals(CanopyCategory.DECIDUOUS_BEECH, CanopyCategory.fromName("DECIDUOUS_BEECH"))
        assertEquals(CanopyCategory.CONIFEROUS_PINE, CanopyCategory.fromName("CONIFEROUS_PINE"))
        assertEquals(CanopyCategory.MIXED_WOODLAND, CanopyCategory.fromName("UNKNOWN_CANOPY"))
        assertEquals(CanopyCategory.MIXED_WOODLAND, CanopyCategory.fromName(null))
    }

    @Test
    fun testMushroomSightingSubmissionIntegrity() {
        val submission = MushroomSightingSubmission(
            h3Index = "871f14488ffffff",
            speciesId = "boletus_edulis",
            phenologicalStage = PhenologicalStage.FRESH_BUTTON,
            elevationBracket = ElevationBracket.HIGH_MONTANE_1200_1600,
            canopyCategory = CanopyCategory.DECIDUOUS_BEECH,
            timestampEpochDay = 20710L,
            verificationTier = VerificationTier.ON_DEVICE_AI_CONFIRMED,
            entropyNonce = "e3b0c44298fc1c14"
        )

        assertEquals("871f14488ffffff", submission.h3Index)
        assertEquals("boletus_edulis", submission.speciesId)
        assertEquals(PhenologicalStage.FRESH_BUTTON, submission.phenologicalStage)
        assertEquals(ElevationBracket.HIGH_MONTANE_1200_1600, submission.elevationBracket)
        assertEquals(CanopyCategory.DECIDUOUS_BEECH, submission.canopyCategory)
        assertEquals(20710L, submission.timestampEpochDay)
        assertEquals(VerificationTier.ON_DEVICE_AI_CONFIRMED, submission.verificationTier)
        assertEquals("e3b0c44298fc1c14", submission.entropyNonce)
    }

    @Test
    fun testTargetGroupBackgroundSiteEqualityAndMethods() {
        val env1 = floatArrayOf(0.45f, 13.2f, 0.28f, 0.75f)
        val env2 = floatArrayOf(0.45f, 13.2f, 0.28f, 0.75f)
        val envDiff = floatArrayOf(0.10f, 8.0f, 0.15f, 0.20f)

        val site1 = TargetGroupBackgroundSite(
            h3Index = "871f14488ffffff",
            recordedSpeciesIds = setOf("boletus_edulis", "cantharellus_cibarius"),
            environmentalVector = env1
        )

        val site2 = TargetGroupBackgroundSite(
            h3Index = "871f14488ffffff",
            recordedSpeciesIds = setOf("boletus_edulis", "cantharellus_cibarius"),
            environmentalVector = env2
        )

        val siteDifferent = TargetGroupBackgroundSite(
            h3Index = "871f14488ffffff",
            recordedSpeciesIds = setOf("boletus_edulis"),
            environmentalVector = envDiff
        )

        val emptySite = TargetGroupBackgroundSite(
            h3Index = "871f14481ffffff",
            recordedSpeciesIds = emptySet(),
            environmentalVector = floatArrayOf()
        )

        assertEquals(site1, site2)
        assertEquals(site1.hashCode(), site2.hashCode())
        assertNotEquals(site1, siteDifferent)

        assertTrue(site1.hasGuildPresence())
        assertFalse(emptySite.hasGuildPresence())

        assertTrue(site1.containsSpecies("boletus_edulis"))
        assertTrue(site1.containsSpecies("cantharellus_cibarius"))
        assertFalse(site1.containsSpecies("morchella_esculenta"))
    }

    @Test
    fun testDeepMaxentPredictionAndConfig() {
        val prediction = DeepMaxentPrediction(
            h3Index = "871f14488ffffff",
            speciesId = "boletus_edulis",
            intensityScore = 2.45,
            calibratedProbability = 78.4,
            confidenceIntervalLower = 72.1,
            confidenceIntervalUpper = 84.6
        )

        assertEquals("871f14488ffffff", prediction.h3Index)
        assertEquals(2.45, prediction.intensityScore, 0.001)
        assertEquals(78.4, prediction.calibratedProbability, 0.001)
        assertTrue(prediction.confidenceIntervalLower <= prediction.calibratedProbability)
        assertTrue(prediction.calibratedProbability <= prediction.confidenceIntervalUpper)

        val config = DeepMaxentModelConfig()
        assertEquals(128, config.batchSize)
        assertEquals(3e-4, config.weightDecayL2, 1e-7)
        assertEquals(64, config.latentDimensions)
        assertEquals(10, config.spatialCvFolds)
        assertEquals(7, config.h3Resolution)
    }
}
