package org.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = MutedInk,
    tertiary = Teal,
    background = PageTint,
    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = Ink,
    primaryContainer = LightBlue,
    onPrimaryContainer = Ink,
    secondaryContainer = SoftTeal,
    onSecondaryContainer = Color(0xFF174E48),
    tertiaryContainer = Color(0xFFFFE6C7),
    onTertiaryContainer = Color(0xFF5C3510)
)

private val DarkColors = darkColorScheme(
    primary = DarkBlue,
    onPrimary = androidx.compose.ui.graphics.Color(0xFF10213D),
    secondary = androidx.compose.ui.graphics.Color(0xFFB8C5D8),
    tertiary = Color(0xFF9AD4C9),
    background = DarkPageTint,
    surface = androidx.compose.ui.graphics.Color(0xFF1A2430),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE7ECF5),
    primaryContainer = Color(0xFF303A65),
    onPrimaryContainer = Color(0xFFE2E6FF),
    secondaryContainer = Color(0xFF25433F),
    onSecondaryContainer = Color(0xFFD0ECE6),
    tertiaryContainer = Color(0xFF59442C),
    onTertiaryContainer = Color(0xFFFFE6C7)
)

@Composable
fun DocScannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = DocScannerTypography,
        content = content
    )
}
