package github.naturewhisp.myco.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.naturewhisp.myco.ui.theme.MycoTheme
import github.naturewhisp.myco.ui.theme.NewsreaderFontFamily

// Testata principale del responso di probabilità con gradazione tassonomica
@Composable
fun ProbabilityHeadline(
    probability: Int,
    modifier: Modifier = Modifier,
    speciesVernacular: String? = null
) {
    val mycoColors = MycoTheme.colors
    val tier = when {
        probability < 20 -> 0
        probability < 40 -> 1
        probability < 60 -> 2
        probability < 75 -> 3
        else -> 4
    }

    val tierLabel = when (tier) {
        0 -> "INATTIVO • CONDIZIONI SFAVOREVOLI"
        1 -> "EMERGENTE • INNESCO MICELIARE"
        2 -> "MODERATO • POTENZIALE DISCRETO"
        3 -> "PROPIZIO • BUTTATA IN CORSO"
        else -> "CULMINE • MASSIMA PROBABILITÀ"
    }

    val tierColor = if (tier == 0) MaterialTheme.colorScheme.onSurfaceVariant else mycoColors.scaleForTier(tier)

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "$probability%",
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = NewsreaderFontFamily,
                fontSize = 44.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 48.sp
            )

            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Text(
                    text = tierLabel,
                    color = tierColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )
                if (!speciesVernacular.isNullOrEmpty()) {
                    Text(
                        text = speciesVernacular,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontFamily = NewsreaderFontFamily,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        ProbabilityBar(probability = probability)
    }
}
