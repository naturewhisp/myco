package github.naturewhisp.myco.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import github.naturewhisp.myco.ui.theme.MycoTheme
import github.naturewhisp.myco.ui.theme.NewsreaderFontFamily

/**
 * Modale informativa di sicurezza micologica e responsabilità legale.
 *
 * Istruisce l'utente sulla natura puramente probabilistica delle stime,
 * sui rischi di intossicazione da sosia velenosi e sull'obbligo di controllo
 * preventivo presso gli ispettorati micologici ufficiali prima del consumo.
 *
 * @param onConfirm Callback invocata quando l'utente accetta e conferma la lettura del disclaimer.
 * @param onDismiss Callback invocata in caso di chiusura della modale.
 */
@Composable
fun SafetyDisclaimerDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mycoColors = MycoTheme.colors

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, mycoColors.ruleSubtle)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Intestazione con badge di allerta
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = mycoColors.favorable.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = "Allerta Sicurezza",
                            tint = mycoColors.favorable,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AVVISO MICOLOGICO & SICUREZZA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = mycoColors.favorable
                        )
                        Text(
                            text = "Responsabilità della Raccolta",
                            fontFamily = NewsreaderFontFamily,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Contenuto informativo scorrevole
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DisclaimerPoint(
                        title = "Stime Probabilistiche",
                        description = "I punteggi e le mappe di calore rappresentano modelli matematici basati su meteo, suolo e orografia. Non costituiscono garanzia di fruttificazione né di presenza reale sul terreno."
                    )

                    DisclaimerPoint(
                        title = "Rischio Tossicità & Sosia Mortali",
                        description = "I funghi commestibili possono essere facilmente confusi con specie velenose o mortali (es. Amanita phalloides, Omphalotus olearius, Lepiota tossiche). Una determinazione errata può provocare avvelenamenti gravi o decessi."
                    )

                    DisclaimerPoint(
                        title = "Obbligo di Controllo ASL",
                        description = "Non consumare MAI funghi raccolti senza averli prima sottoposti all'ispezione gratuita dei micologi degli Ispettorati Micologici delle ASL locali."
                    )

                    DisclaimerPoint(
                        title = "Normativa & Rispetto Ambientale",
                        description = "Rispettare le leggi regionali sulla raccolta, i tesserini abilitativi, i limiti di peso e il divieto assoluto di raccolta di esemplari allo stadio di ovolo chiuso (DPR 376/1995)."
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Pulsante di conferma
                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = mycoColors.favorable,
                        contentColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Ho compreso e accetto",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun DisclaimerPoint(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "• $title",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = description,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
