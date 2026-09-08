package github.naturewhisp.myco.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.naturewhisp.myco.ui.theme.EditorialItalic
import github.naturewhisp.myco.ui.theme.MycoTheme

// Riquadro editoriale per note di campo con filetto laterale da 2px
@Composable
fun FieldNote(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = "NOTA DI CAMPO",
    isCaution: Boolean = false
) {
    val mycoColors = MycoTheme.colors
    val accentColor = if (isCaution) mycoColors.scale3 else mycoColors.favorable

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Filetto verticale da 2px
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(64.dp)
                .background(accentColor)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column {
            if (!title.isNullOrEmpty()) {
                Text(
                    text = title,
                    color = accentColor,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurface,
                style = EditorialItalic,
                fontSize = 14.sp,
                lineHeight = 22.sp
            )
        }
    }
}
