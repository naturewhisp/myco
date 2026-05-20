package github.naturewhisp.myco.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF34D399),       // Emerald 400
    onPrimary = Color(0xFF040810),
    primaryContainer = Color(0xFF059669), // Emerald 600
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF6EE7B7),
    onSecondary = Color(0xFF040810),
    background = Color(0xFF040810),
    onBackground = Color.White,
    surface = Color(0x990D1423),        // glass card bg (rgba(13, 20, 35, 0.6))
    onSurface = Color.White,
    surfaceVariant = Color(0xCC0D1423), // slightly more opaque glass card bg (rgba(13, 20, 35, 0.8))
    onSurfaceVariant = Color(0xFF94A3B8), // Slate 400 text
    outline = Color(0x14FFFFFF),         // border (rgba(255, 255, 255, 0.08))
    error = Color(0xFFF87171),
    onError = Color.White
)

val LightColorScheme = DarkColorScheme // The app is dark-themed by design

@Composable
fun MycoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
