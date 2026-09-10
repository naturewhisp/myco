package github.naturewhisp.myco.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.naturewhisp.myco.model.SavedLocation
import github.naturewhisp.myco.ui.theme.MycoTheme

/**
 * Chip compatto per la selezione rapida di località salvate o recenti.
 *
 * Visualizza l'icona di stato (stella per i preferiti, pin per la posizione GPS live),
 * il toponimo formattato e l'eventuale pulsante circolare di rimozione.
 * Supporta click singolo (selezione) e click prolungato (es. ridenominazione).
 *
 * @param loc Entità geografica salvata [SavedLocation].
 * @param onClick Callback eseguita al tocco standard per caricare la località.
 * @param onLongClick Callback opzionale eseguita alla pressione prolungata (es. modifica toponimo).
 * @param onRemoveClick Callback opzionale per eliminare la località dalla lista recenti/preferiti.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocationChip(
    loc: SavedLocation,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onRemoveClick: (() -> Unit)? = null
) {
    val colors = MycoTheme.colors
    val accentColor = when {
        loc.isFavorite -> colors.scale2
        loc.isGpsLocation -> colors.meteo
        else -> colors.neutral
    }
    val icon = when {
        loc.isFavorite -> Icons.Default.Star
        loc.isGpsLocation -> Icons.Default.LocationOn
        else -> null
    }
    val shape = RoundedCornerShape(4.dp)

    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    } else {
        Modifier.clickable(onClick = onClick)
    }

    Row(
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .then(clickModifier)
            .padding(
                start = 10.dp,
                end = if (onRemoveClick != null) 6.dp else 10.dp,
                top = 6.dp,
                bottom = 6.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = loc.effectiveName,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontWeight = if (loc.isFavorite) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
        if (onRemoveClick != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onRemoveClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Rimuovi",
                    tint = accentColor,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}
