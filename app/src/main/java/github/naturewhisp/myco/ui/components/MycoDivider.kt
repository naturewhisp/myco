package github.naturewhisp.myco.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.naturewhisp.myco.ui.theme.MycoTheme

// Filetto separatore orizzontale continuo da 1px nello stile archivistico Herbarium
@Composable
fun MycoDivider(
    modifier: Modifier = Modifier,
    subtle: Boolean = true
) {
    val color = if (subtle) MycoTheme.colors.ruleSubtle else MycoTheme.colors.ruleHairline
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}

// Interruzione botanica di sezione con glifo centrale e filetti laterali
@Composable
fun BotanicalBreak(
    modifier: Modifier = Modifier,
    glyph: String = "✳"
) {
    val ruleColor = MycoTheme.colors.ruleSubtle
    val glyphColor = MycoTheme.colors.favorable

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(ruleColor)
        )
        Text(
            text = glyph,
            color = glyphColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(ruleColor)
        )
    }
}
