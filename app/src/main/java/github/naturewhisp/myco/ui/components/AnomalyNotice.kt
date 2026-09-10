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
import androidx.compose.ui.res.stringResource
import github.naturewhisp.myco.R
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
                    text = stringResource(R.string.notice_habitat_tag),
                    color = mycoColors.scale3,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.notice_habitat_title),
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = NewsreaderFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.notice_habitat_description),
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
                        text = stringResource(R.string.notice_habitat_action),
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
                text = stringResource(R.string.notice_coverage_tag),
                color = mycoColors.meteo,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.notice_coverage_title),
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = NewsreaderFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.notice_coverage_description),
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
                    val label = if (distanceKm != null) {
                        stringResource(R.string.notice_coverage_action_with_distance, closestLocationName, distanceKm)
                    } else {
                        stringResource(R.string.notice_coverage_action, closestLocationName)
                    }
                    Text(
                        text = label,
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
    modifier: Modifier = Modifier,
    isFieldOffline: Boolean = false
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
                .background(if (isFieldOffline) mycoColors.meteo else mycoColors.scale2)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isFieldOffline) {
                stringResource(R.string.notice_offline_field_mode, cacheAgeText)
            } else {
                stringResource(R.string.notice_offline_local_archive, cacheAgeText)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
