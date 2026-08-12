package com.talwinter.bptracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Dynamic colour (Material You) is deliberately NOT used.
 *
 * In most apps wallpaper-derived theming is a nice touch. Here it is actively wrong:
 * colour carries clinical meaning — the category badge for "hypertension" has to read as
 * more serious than "elevated", every time, on every device. Letting the wallpaper pick
 * those hues means a green-tinted phone renders a high reading in something reassuring.
 *
 * So the palette from DesignSystem.kt is used as-is, in both light and dark.
 */
private val LightColors = lightColorScheme(
    primary = Palette.Calm,
    onPrimary = Color.White,
    primaryContainer = Palette.Calm.copy(alpha = 0.14f),
    onPrimaryContainer = Palette.Ink,
    secondary = Palette.Slate,
    onSecondary = Color.White,
    tertiary = Palette.Raised,
    background = Palette.Paper,
    onBackground = Palette.Ink,
    surface = Palette.Surface,
    onSurface = Palette.Ink,
    surfaceVariant = Color(0xFFEDEFEC),
    onSurfaceVariant = Palette.Slate,
    outline = Palette.Slate.copy(alpha = 0.5f),
    outlineVariant = Palette.Hairline,
    error = Palette.High,
    onError = Color.White,
    errorContainer = Palette.High.copy(alpha = 0.12f),
    onErrorContainer = Palette.High
)

private val DarkColors = darkColorScheme(
    primary = Palette.CalmDark,
    onPrimary = Palette.Ink,
    primaryContainer = Palette.CalmDark.copy(alpha = 0.20f),
    onPrimaryContainer = Color(0xFFE7ECEF),
    secondary = Palette.SlateDark,
    onSecondary = Palette.Ink,
    tertiary = Palette.RaisedDark,
    background = Palette.PaperDark,
    onBackground = Color(0xFFE7ECEF),
    surface = Palette.SurfaceDark,
    onSurface = Color(0xFFE7ECEF),
    surfaceVariant = Color(0xFF232830),
    onSurfaceVariant = Palette.SlateDark,
    outline = Palette.SlateDark.copy(alpha = 0.5f),
    outlineVariant = Palette.HairlineDark,
    error = Palette.HighDark,
    onError = Palette.Ink,
    errorContainer = Palette.HighDark.copy(alpha = 0.18f),
    onErrorContainer = Palette.HighDark
)

/** Material's defaults, retuned to the tighter tracking the design language uses. */
private val AppTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.merge(Type.Heading),
        titleMedium = base.titleMedium.merge(Type.Title),
        titleSmall = base.titleSmall.merge(Type.Title),
        bodyMedium = base.bodyMedium.merge(Type.Body),
        bodySmall = base.bodySmall.merge(Type.Small),
        labelSmall = base.labelSmall.merge(Type.Eyebrow)
    )
}

@Composable
fun BpTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography
    ) {
        // Explicit ground. Without it the window falls back to the XML theme's background,
        // which is how a light page ended up behind dark cards.
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}
