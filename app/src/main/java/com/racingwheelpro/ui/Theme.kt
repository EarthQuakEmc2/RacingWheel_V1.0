package com.racingwheelpro.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Carbon = Color(0xFF0B0D10)
val Panel = Color(0xFF14181D)
val PanelRaised = Color(0xFF1C2229)
val Line = Color(0xFF2E363F)
val Ink = Color(0xFFE8E6E3)
val Muted = Color(0xFF8E969E)
val SignalRed = Color(0xFFE10600)
val SignalYellow = Color(0xFFF5C518)
val SignalGreen = Color(0xFF3DDC57)
val SignalBlue = Color(0xFF2F80ED)
val SignalMagenta = Color(0xFFE040FB)
val SignalCyan = Color(0xFF00E5FF)

private val Scheme = darkColorScheme(
    primary = SignalRed,
    onPrimary = Color.White,
    secondary = SignalYellow,
    onSecondary = Color.Black,
    background = Carbon,
    onBackground = Ink,
    surface = Panel,
    onSurface = Ink,
    surfaceVariant = PanelRaised,
    onSurfaceVariant = Muted,
    outline = Line,
)

@Composable
fun RacingTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}

fun hexColor(hex: String): Color {
    val clean = hex.removePrefix("#").trim()
    return try {
        val value = clean.toLong(16)
        if (clean.length <= 6) Color((0xFF000000L or value).toInt()) else Color(value.toInt())
    } catch (_: Exception) {
        Color.White
    }
}
