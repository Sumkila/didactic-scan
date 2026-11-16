package org.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = MutedInk,
    background = PageTint,
    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = Ink
)

private val DarkColors = darkColorScheme(
    primary = DarkBlue,
    onPrimary = androidx.compose.ui.graphics.Color(0xFF10213D),
    secondary = androidx.compose.ui.graphics.Color(0xFFB8C5D8),
    background = DarkPageTint,
    surface = androidx.compose.ui.graphics.Color(0xFF1A2430),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE7ECF5)
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
