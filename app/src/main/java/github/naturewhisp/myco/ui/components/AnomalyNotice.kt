package github.naturewhisp.myco.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.naturewhisp.myco.ui.theme.MycoTheme
import github.naturewhisp.myco.ui.theme.NewsreaderFontFamily

// Banner di avviso per condizioni di anomalia geografica o ambientale
@Composable
fun HabitatAnomalyNotice(
    modifier: Modifier = Modifier,
    onMoveToForestClick: (() -> Unit)? = null
) {
    val mycoColors = MycoTheme.colors
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, mycoColors.scale3, shape)
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "AVVISO HABITAT",
                    color = mycoColors.scale3,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Area a prevalente insediamento urbano o agricolo",
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = NewsreaderFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "La copertura forestale rilevata è inferiore al 10%. La probabilità miceliare richiede la vicinanza a specie arboree ospiti (faggio, castagno, quercia o conifere).",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            if (onMoveToForestClick != null) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onMoveToForestClick,
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(
                        text = "Sposta cursore verso il bosco vicino",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// Banner per coordinate fuori dai confini nazionali dell'atlante SPUN Italia
@Composable
fun OutsideCoverageNotice(
    distanceKm: Int? = null,
    closestLocationName: String? = null,
    modifier: Modifier = Modifier,
    onSnapClick: (() -> Unit)? = null
) {
    val mycoColors = MycoTheme.colors
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, mycoColors.meteo, shape)
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = "FUORI COPERTURA ATLANTE SPUN",
                color = mycoColors.meteo,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Dati meteorologici attivi • Atlante micorrizico non disponibile",
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = NewsreaderFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Le coordinate selezionate si trovano al di fuori dei confini coperti dall'atlante SPUN Italia. Il calcolo climatico resta pienamente operativo.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            if (onSnapClick != null && !closestLocationName.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onSnapClick,
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = mycoColors.favorable,
                        contentColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(
                        text = "Centra su $closestLocationName ${if (distanceKm != null) "($distanceKm km)" else ""}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// Indicatore dell'archivio offline attivo con orario di memorizzazione
@Composable
fun OfflineCacheNotice(
    cacheAgeText: String,
    modifier: Modifier = Modifier
) {
    val mycoColors = MycoTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(mycoColors.scale2)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Archivio locale attivo • Rilevamento salvato $cacheAgeText",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
