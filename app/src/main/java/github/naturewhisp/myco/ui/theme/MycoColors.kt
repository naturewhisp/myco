package github.naturewhisp.myco.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import github.naturewhisp.myco.model.ProbabilityTier

/**
 * Rappresentazione immutabile della tavolozza semantica botanica Herbarium.
 *
 * Fornisce i token cromatici per la scala tassonomica a 5 livelli di probabilità,
 * gli indicatori di stato (favorable, neutral, adverse, meteo), le linee di demarcazione
 * e il supporto per i temi Naturalist (chiaro) e Nocturne (scuro).
 *
 * @property scale0 Colore per il livello di probabilità 0 (Inattivo).
 * @property scale1 Colore per il livello di probabilità 1 (Innesco).
 * @property scale2 Colore per il livello di probabilità 2 (Discreto).
 * @property scale3 Colore per il livello di probabilità 3 (Propizio).
 * @property scale4 Colore per il livello di probabilità 4 (Culmine).
 * @property favorable Colore semantico positivo / ottimale.
 * @property neutral Colore semantico neutro.
 * @property adverse Colore semantico avverso / penalizzante.
 * @property meteo Colore semantico per dati climatici e idrologici.
 * @property ruleHairline Linea di demarcazione finissima (stile incisione).
 * @property ruleSubtle Linea di demarcazione tenue.
 * @property inkVague Inchiostro attenuato per testo disabilitato o secondario.
 * @property onMap Colore di contrasto per etichette cartografiche.
 * @property scrimAlpha Trasparenza della velatura di sfondo.
 * @property isDark Flag indicante la modalità scura (Nocturne).
 */
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
    val scrimAlpha: Float,
    val isDark: Boolean,
) {
    /**
     * Restituisce il colore corrispondente della scala tassonomica calcolato tramite [ProbabilityTier].
     *
     * @param probability Valore percentuale intero della probabilità (0..100).
     * @return [Color] associato al livello di probabilità.
     */
    fun scaleForProbability(probability: Int): Color =
        scaleForTier(ProbabilityTier.fromProbability(probability).tierIndex)

    /**
     * Restituisce il colore corrispondente a uno specifico [ProbabilityTier].
     *
     * @param tier Scaglione canonico [ProbabilityTier].
     * @return [Color] associato.
     */
    fun scaleForProbabilityTier(tier: ProbabilityTier): Color =
        scaleForTier(tier.tierIndex)

    /**
     * Restituisce il colore per indice ordinale 0..4.
     *
     * @param tier Indice ordinale (0..4).
     * @return [Color] corrispondente.
     */
    fun scaleForTier(tier: Int): Color = when (tier) {
        0 -> scale0
        1 -> scale1
        2 -> scale2
        3 -> scale3
        else -> scale4
    }
}

/**
 * Tavolozza Herbarium Naturalist per tema chiaro ispirata alla carta pergamena e inchiostri vegetali.
 */
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
    scrimAlpha = 0.50f,
    isDark = false,
)

/**
 * Tavolozza Herbarium Nocturne per tema scuro con contrasto calibrato per escursioni notturne.
 */
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
    scrimAlpha = 0.60f,
    isDark = true,
)

/**
 * CompositionLocal per la propagazione implicita dell'istanza [MycoColors] nell'albero Compose.
 */
val LocalMycoColors = staticCompositionLocalOf { NaturalistMycoColors }
