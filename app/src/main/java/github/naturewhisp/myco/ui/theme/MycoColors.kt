package github.naturewhisp.myco.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Rappresentazione immutabile della tavolozza semantica botanica Herbarium
@Immutable
data class MycoColors(
    val scale0: Color,
    val scale1: Color,
    val scale2: Color,
    val scale3: Color,
    val scale4: Color,
    val favorable: Color,
    val neutral: Color,
    val adverse: Color,
    val meteo: Color,
    val ruleHairline: Color,
    val ruleSubtle: Color,
    val inkVague: Color,
    val onMap: Color,
    val heatmapAlpha: Float,
    val scrimAlpha: Float,
    val isDark: Boolean,
) {
    // Restituisce il colore corrispondente della scala tassonomica a 5 livelli (0..4)
    fun scaleForProbability(probability: Int): Color = when {
        probability < 20 -> scale0
        probability < 40 -> scale1
        probability < 60 -> scale2
        probability < 75 -> scale3
        else -> scale4
    }

    // Restituisce il colore per livello ordinale 0..4
    fun scaleForTier(tier: Int): Color = when (tier) {
        0 -> scale0
        1 -> scale1
        2 -> scale2
        3 -> scale3
        else -> scale4
    }
}

val NaturalistMycoColors = MycoColors(
    scale0 = Scale0,
    scale1 = Scale1,
    scale2 = Scale2,
    scale3 = Scale3,
    scale4 = Scale4,
    favorable = Forest,
    neutral = InkSecondary,
    adverse = Scale4,
    meteo = Indigo,
    ruleHairline = RuleHairline,
    ruleSubtle = RuleSubtle,
    inkVague = InkDisabled,
    onMap = InkMap,
    heatmapAlpha = 0.50f,
    scrimAlpha = 0.50f,
    isDark = false,
)

val NocturneMycoColors = MycoColors(
    scale0 = NightScale0,
    scale1 = NightScale1,
    scale2 = NightScale2,
    scale3 = NightScale3,
    scale4 = NightScale4,
    favorable = NightLichen,
    neutral = NightInkSoft,
    adverse = NightScale4,
    meteo = NightIndigo,
    ruleHairline = NightRule,
    ruleSubtle = NightRuleSubtle,
    inkVague = NightInkVague,
    onMap = NightInk,
    heatmapAlpha = 0.59f,
    scrimAlpha = 0.60f,
    isDark = true,
)

val LocalMycoColors = staticCompositionLocalOf { NaturalistMycoColors }
