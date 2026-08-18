package org.newnewpipe.app.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Tema Compose unico dell'app (piano 022, S18 — Material You).
 *
 * - **Dynamic color** (Android 12+, API 31): palette generata dal sistema (wallpaper),
 *   coerente con la UI legacy a cui `ThemeHelper.setTheme` applica `DynamicColors`.
 * - **Fallback** (API < 31 o dynamic color non disponibile): palette del fork,
 *   indaco `#3D5AFE` (D-9).
 * - **Variante BLACK**: nero puro, coerente con il tema View `BlackTheme`
 *   (background/surface neri, testo bianco).
 *
 * Sostituisce i color scheme duplicati di `ComposeItemUiHelper` e
 * `SearchFilterDialog`, che ora delegano qui (S17: punto di migrazione (a)).
 */
@Composable
fun NewNewPipeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    blackTheme: Boolean = false,
    dynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = if (blackTheme) {
        // scelta esplicita dell'utente: il nero puro ha precedenza sul dynamic color
        IndacoBlackColorScheme
    } else if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
    } else if (darkTheme) {
        IndacoDarkColorScheme
    } else {
        IndacoLightColorScheme
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

/** Palette indaco (fallback statico) — variante light. */
val IndacoLightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF3D5AFE),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF5B5D72),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E1F9),
    onSecondaryContainer = Color(0xFF181A2C),
    tertiary = Color(0xFF77536D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8F1),
    onTertiaryContainer = Color(0xFF2D1228),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC7C5D0),
    scrim = Color.Black
)

/** Palette indaco (fallback statico) — variante dark. */
val IndacoDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFB9C1FF),
    onPrimary = Color(0xFF00208B),
    primaryContainer = Color(0xFF0033C0),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFC4C5DD),
    onSecondary = Color(0xFF2D2F42),
    secondaryContainer = Color(0xFF434559),
    onSecondaryContainer = Color(0xFFE0E1F9),
    tertiary = Color(0xFFE7BAD8),
    onTertiary = Color(0xFF46263F),
    tertiaryContainer = Color(0xFF5E3A56),
    onTertiaryContainer = Color(0xFFFFD8F1),
    background = Color(0xFF1B1B21),
    onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF1B1B21),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF46464F),
    scrim = Color.Black
)

/**
 * Palette indaco — variante BLACK: come la dark ma con nero puro,
 * coerente con il tema View `BlackTheme` (e con il vecchio scheme di SearchFilterDialog).
 */
val IndacoBlackColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFB9C1FF),
    onPrimary = Color(0xFF00208B),
    primaryContainer = Color(0xFF0033C0),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFC4C5DD),
    onSecondary = Color(0xFF2D2F42),
    secondaryContainer = Color(0xFF1F1F1F),
    onSecondaryContainer = Color(0xFFE0E1F9),
    tertiary = Color(0xFFE7BAD8),
    onTertiary = Color(0xFF46263F),
    tertiaryContainer = Color(0xFF5E3A56),
    onTertiaryContainer = Color(0xFFFFD8F1),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFC7C5D0),
    outline = Color(0xFF3A3A3A),
    outlineVariant = Color(0xFF2A2A2A),
    scrim = Color.Black
)
