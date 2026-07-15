package com.sywd.usamocklocation.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF006A64),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9CF2E9),
    onPrimaryContainer = Color(0xFF00201E),
    secondary = Color(0xFF4A6360),
    background = Color(0xFFF5FAF8),
    surface = Color(0xFFF5FAF8),
    surfaceVariant = Color(0xFFDAE5E2),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF80D5CD),
    onPrimary = Color(0xFF003734),
    primaryContainer = Color(0xFF00504B),
    onPrimaryContainer = Color(0xFF9CF2E9),
    secondary = Color(0xFFB1CCC8),
    background = Color(0xFF0F1514),
    surface = Color(0xFF0F1514),
    surfaceVariant = Color(0xFF3F4947),
    error = Color(0xFFFFB4AB),
)

@Composable
fun UsaMockLocationTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
