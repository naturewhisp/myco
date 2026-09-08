package github.naturewhisp.myco.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

// Tavolozza Material 3 per il tema chiaro "Herbarium Naturalist"
val HerbariumLightColorScheme = lightColorScheme(
    primary = Forest,
    onPrimary = Parchment,
    primaryContainer = ParchmentVariant,
    onPrimaryContainer = Forest,
    secondary = Lichen,
    onSecondary = Parchment,
    secondaryContainer = ParchmentVariant,
    onSecondaryContainer = Forest,
    tertiary = Indigo,
    onTertiary = Parchment,
    background = Parchment,
    onBackground = InkPrimary,
    surface = Parchment,
    onSurface = InkPrimary,
    surfaceVariant = ParchmentVariant,
    onSurfaceVariant = InkSecondary,
    surfaceContainer = ParchmentVariant,
    surfaceContainerHigh = ParchmentVariant,
    surfaceContainerHighest = ParchmentVariant,
    surfaceContainerLow = Parchment,
    surfaceContainerLowest = ParchmentSurface,
    outline = RuleHairline,
    outlineVariant = RuleSubtle,
    error = Scale4,
    onError = Parchment
)

// Tavolozza Material 3 per il tema scuro "Herbarium Nocturne"
val HerbariumDarkColorScheme = darkColorScheme(
    primary = NightLichen,
    onPrimary = NightBase,
    primaryContainer = NightSurfaceRaised,
    onPrimaryContainer = NightLichen,
    secondary = NightScale2,
    onSecondary = NightBase,
    secondaryContainer = NightSurface,
    onSecondaryContainer = NightScale2,
    tertiary = NightIndigo,
    onTertiary = NightBase,
    background = NightBase,
    onBackground = NightInk,
    surface = NightSurface,
    onSurface = NightInk,
    surfaceVariant = NightSurfaceRaised,
    onSurfaceVariant = NightInkSoft,
    surfaceContainer = NightSurface,
    surfaceContainerHigh = NightSurfaceRaised,
    surfaceContainerHighest = NightSurfaceRaised,
    surfaceContainerLow = NightBase,
    surfaceContainerLowest = NightBase,
    outline = NightRule,
    outlineVariant = NightRuleSubtle,
    error = NightScale4,
    onError = NightBase
)

// Accesso rapido alle definizioni visive Herbarium
object MycoTheme {
    val colors: MycoColors
        @Composable
        get() = LocalMycoColors.current

    val typography: Typography
        @Composable
        get() = MaterialTheme.typography

    val shapes: Shapes
        @Composable
        get() = MaterialTheme.shapes
}

// Composable principale di tematizzazione Myco Herbarium
@Composable
fun MycoTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    darkTheme: Boolean = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    },
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) HerbariumDarkColorScheme else HerbariumLightColorScheme
    val mycoColors = if (darkTheme) NocturneMycoColors else NaturalistMycoColors

    CompositionLocalProvider(
        LocalMycoColors provides mycoColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = HerbariumTypography,
            shapes = HerbariumShapes,
            content = content
        )
    }
}
