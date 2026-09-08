package github.naturewhisp.myco.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.naturewhisp.myco.model.Factor
import github.naturewhisp.myco.model.FactorLevel
import github.naturewhisp.myco.ui.theme.MycoTheme

// Riga tabellare senza cornice (card-less) per un fattore ecologico
@Composable
fun FactorRow(
    factor: Factor,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true
) {
    val mycoColors = MycoTheme.colors
    val pipColor = when (factor.level) {
        FactorLevel.FAVORABLE -> mycoColors.favorable
        FactorLevel.NEUTRAL -> mycoColors.scale2
        FactorLevel.ADVERSE -> mycoColors.adverse
        FactorLevel.INFORMATIVE -> mycoColors.meteo
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pip colorato indicatore del livello
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(pipColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Etichetta e dettaglio del fattore
            Column(modifier = Modifier.weight(1f, fill = true)) {
                Text(
                    text = factor.label,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                if (factor.detail.isNotEmpty()) {
                    Text(
                        text = factor.detail,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            // Valore formattato allineato a destra
            if (factor.formattedValue.isNotEmpty()) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = factor.formattedValue,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End
                )
            }
        }

        if (showDivider) {
            MycoDivider(subtle = true)
        }
    }
}
