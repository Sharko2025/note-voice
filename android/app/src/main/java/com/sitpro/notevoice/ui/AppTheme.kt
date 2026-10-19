package com.sitpro.notevoice.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Fixed navy + orange palette from logo C (SPEC: no wallpaper colours), light and dark.
 * Values are the `--p-*` tokens of the approved mockup.
 */
@Immutable
data class AppColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val fg: Color,
    val muted: Color,
    val primary: Color,
    val onPrimary: Color,
    val fin: Color,
    val onFin: Color,
    val star: Color,
    val danger: Color,
    val line: Color,
    val new: Color,
    val wave: Color,
    val ideaBg: Color,
    val ideaFg: Color,
    val quoteBg: Color,
    val quoteFg: Color,
    val plainBg: Color,
    val plainFg: Color,
    val sun: Color,
    val ok: Color,
)

val LightColors = AppColors(
    bg = Color(0xFFF6F8FB), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFE9EFF6), fg = Color(0xFF0E1E33),
    muted = Color(0xFF5B6B7F), primary = Color(0xFF1F5A96), onPrimary = Color(0xFFFFFFFF), fin = Color(0xFFF28C28),
    onFin = Color(0xFF0B2545), star = Color(0xFFE07A12), danger = Color(0xFFC62828), line = Color(0xFFE1E7EF),
    new = Color(0xFFFFF1E0), wave = Color(0xFF3FA7D6),
    ideaBg = Color(0xFFFFE9D2), ideaFg = Color(0xFFA4520A), quoteBg = Color(0xFFDDEFFA), quoteFg = Color(0xFF1B6A93),
    plainBg = Color(0xFFE9EFF6), plainFg = Color(0xFF4D5E73), sun = Color(0xFFE9A23B), ok = Color(0xFF2E7D32),
)

val DarkColors = AppColors(
    bg = Color(0xFF0C1726), surface = Color(0xFF132238), surface2 = Color(0xFF1B2D46), fg = Color(0xFFE5ECF5),
    muted = Color(0xFF95A5BA), primary = Color(0xFF8FC3F0), onPrimary = Color(0xFF0B2545), fin = Color(0xFFF7A04B),
    onFin = Color(0xFF0B2545), star = Color(0xFFF7A04B), danger = Color(0xFFFF8A80), line = Color(0xFF22344D),
    new = Color(0xFF2A2416), wave = Color(0xFF5BB8E0),
    ideaBg = Color(0xFF3A2814), ideaFg = Color(0xFFF7B26B), quoteBg = Color(0xFF16334A), quoteFg = Color(0xFF8CCBEE),
    plainBg = Color(0xFF1B2D46), plainFg = Color(0xFFA9B8CB), sun = Color(0xFFE9A23B), ok = Color(0xFF81C784),
)

val LocalAppColors = staticCompositionLocalOf { LightColors }

/** Shortcut: `colors.fin` inside composables. */
val colors: AppColors @Composable get() = LocalAppColors.current

@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.primary, onPrimary = c.onPrimary, secondary = c.fin, onSecondary = c.onFin,
            background = c.bg, onBackground = c.fg, surface = c.surface, onSurface = c.fg,
            surfaceVariant = c.surface2, onSurfaceVariant = c.muted, surfaceContainer = c.surface,
            surfaceContainerLow = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surface2,
            outline = c.line, outlineVariant = c.line, error = c.danger,
            inverseSurface = c.fg, inverseOnSurface = c.bg, inversePrimary = c.fin,
        )
    } else {
        lightColorScheme(
            primary = c.primary, onPrimary = c.onPrimary, secondary = c.fin, onSecondary = c.onFin,
            background = c.bg, onBackground = c.fg, surface = c.surface, onSurface = c.fg,
            surfaceVariant = c.surface2, onSurfaceVariant = c.muted, surfaceContainer = c.surface,
            surfaceContainerLow = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surface2,
            outline = c.line, outlineVariant = c.line, error = c.danger,
            inverseSurface = c.fg, inverseOnSurface = c.bg, inversePrimary = c.fin,
        )
    }
    CompositionLocalProvider(LocalAppColors provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
