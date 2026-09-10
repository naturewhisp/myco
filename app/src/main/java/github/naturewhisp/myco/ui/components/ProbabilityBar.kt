package github.naturewhisp.myco.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import github.naturewhisp.myco.model.ProbabilityTier
import github.naturewhisp.myco.ui.theme.MycoTheme

/**
 * Barra di probabilità a 5 segmenti discreti separati da gap millimetrici (stile botanico Herbarium).
 *
 * Mappa la probabilità percentuale calcolata sui 5 livelli tassonomici di [ProbabilityTier],
 * evidenziando i segmenti attivi con il corrispondente pigmento cromatico minerale.
 *
 * @param probability Valore percentuale intero della probabilità di crescita (0..100).
 * @param modifier Modificatore Compose per personalizzazione del layout.
 * @param height Altezza verticale dei singoli segmenti (default 6.dp).
 */
@Composable
fun ProbabilityBar(
    probability: Int,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp
) {
    val mycoColors = MycoTheme.colors
    val tier = ProbabilityTier.fromProbability(probability)
    val activeTiers = tier.tierIndex

    val activeColor = mycoColors.scaleForTier(activeTiers)
    val inactiveColor = mycoColors.scale0
    val segmentShape = RoundedCornerShape(3.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (i in 0..4) {
            val isFilled = i <= activeTiers && (probability > 0 || i == 0)
            val color = if (isFilled) activeColor else inactiveColor

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(height)
                    .clip(segmentShape)
                    .background(color)
            )
        }
    }
}
