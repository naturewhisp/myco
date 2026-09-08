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
import github.naturewhisp.myco.ui.theme.MycoTheme

// Barra di probabilità tassonomica a 5 segmenti discreti separati da gap
@Composable
fun ProbabilityBar(
    probability: Int,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp
) {
    val mycoColors = MycoTheme.colors
    val activeTiers = when {
        probability < 20 -> 0
        probability < 40 -> 1
        probability < 60 -> 2
        probability < 75 -> 3
        else -> 4
    }

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
