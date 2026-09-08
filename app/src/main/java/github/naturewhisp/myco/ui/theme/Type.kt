package github.naturewhisp.myco.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import github.naturewhisp.myco.R

private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val NewsreaderFont = GoogleFont("Newsreader")
private val SourceSans3Font = GoogleFont("Source Sans 3")
private val CourierPrimeFont = GoogleFont("Courier Prime")

val NewsreaderFontFamily = FontFamily(
    Font(googleFont = NewsreaderFont, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = NewsreaderFont, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = NewsreaderFont, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = NewsreaderFont, fontProvider = fontProvider, weight = FontWeight.Bold),
    Font(googleFont = NewsreaderFont, fontProvider = fontProvider, weight = FontWeight.Normal, style = FontStyle.Italic)
)

val SourceSans3FontFamily = FontFamily(
    Font(googleFont = SourceSans3Font, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = SourceSans3Font, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = SourceSans3Font, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = SourceSans3Font, fontProvider = fontProvider, weight = FontWeight.Bold)
)

val CourierPrimeFontFamily = FontFamily(
    Font(googleFont = CourierPrimeFont, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = CourierPrimeFont, fontProvider = fontProvider, weight = FontWeight.Bold)
)

val HerbariumTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = NewsreaderFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.02).sp
    ),
    displayMedium = TextStyle(
        fontFamily = NewsreaderFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 34.sp,
        lineHeight = 38.sp
    ),
    displaySmall = TextStyle(
        fontFamily = NewsreaderFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = NewsreaderFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 26.sp,
        lineHeight = 32.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = NewsreaderFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = NewsreaderFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleLarge = TextStyle(
        fontFamily = NewsreaderFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = NewsreaderFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontFamily = SourceSans3FontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = SourceSans3FontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = SourceSans3FontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodySmall = TextStyle(
        fontFamily = SourceSans3FontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = SourceSans3FontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = SourceSans3FontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.08.sp
    ),
    labelSmall = TextStyle(
        fontFamily = SourceSans3FontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

val EditorialItalic = TextStyle(
    fontFamily = NewsreaderFontFamily,
    fontStyle = FontStyle.Italic,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 24.sp
)

val CodeTech = TextStyle(
    fontFamily = CourierPrimeFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.sp
)
