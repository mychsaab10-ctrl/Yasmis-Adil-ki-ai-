package com.yesmin.ai.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Cyan = Color(0xFF22D3EE)
val Purple = Color(0xFFB388FF)
val Pink = Color(0xFFFF7AC6)
val DeepBg = Color(0xFF0B0714)

private val DarkScheme = darkColorScheme(
    primary = Cyan, onPrimary = Color.Black,
    secondary = Purple, tertiary = Pink,
    background = DeepBg, onBackground = Color(0xFFEDE7FF),
    surface = Color(0xFF161026), onSurface = Color(0xFFEDE7FF),
    surfaceVariant = Color(0xFF221A38), onSurfaceVariant = Color(0xFFBDB1DA),
    outline = Color(0xFF4A3D70), error = Color(0xFFFF6B81)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0891B2), onPrimary = Color.White,
    secondary = Color(0xFF7C3AED), tertiary = Color(0xFFDB2777),
    background = Color(0xFFF7F4FF), onBackground = Color(0xFF1B1530),
    surface = Color.White, onSurface = Color(0xFF1B1530),
    surfaceVariant = Color(0xFFEDE7FF), onSurfaceVariant = Color(0xFF5B4F7A),
    outline = Color(0xFFB9ADD6), error = Color(0xFFD6334D)
)

/** mode: "dark" | "light" | "system" */
@Composable
fun YesminTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "light" -> false
        "system" -> isSystemInDarkTheme()
        else -> true
    }
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}
