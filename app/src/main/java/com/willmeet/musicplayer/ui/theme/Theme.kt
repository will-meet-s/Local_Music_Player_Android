package com.willmeet.musicplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DB8FF),
    onPrimary = Color(0xFF00325B),
    background = Color(0xFF101014),
    onBackground = Color(0xFFF2F2F5),
    surface = Color(0xFF17171C),
    onSurface = Color(0xFFF2F2F5),
    onSurfaceVariant = Color(0xFFA8A8B4)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2C6FD1),
    background = Color(0xFFF7F7FA),
    surface = Color(0xFFFFFFFF)
)

@Composable
fun MusicPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
