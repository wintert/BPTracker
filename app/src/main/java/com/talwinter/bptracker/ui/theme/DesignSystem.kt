package com.talwinter.bptracker.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talwinter.bptracker.clinical.BpCategory

/**
 * Design language: "measured instrument".
 *
 * The reference points are a good barometer or a Braun wall clock, not a hospital screen.
 * That matters for this app specifically: the person using it has genuinely high readings
 * most days. A traffic-light palette would shout at them every single morning, which is
 * both unpleasant and useless — alarm that fires constantly stops being information.
 *
 * So the category colours are deliberately desaturated. Severity is still legible at a
 * glance through hue and weight, but the app never panics. The one exception is a
 * hypertensive crisis, which gets the full-stop oxblood and a blocking dialog, because
 * that genuinely is an emergency and should look unlike everything else in the app.
 */
object Palette {
    // Neutrals — cool rather than cream. Warm off-white reads "lifestyle app"; this is
    // an instrument, and its ground should be quiet and slightly cold.
    val Ink = Color(0xFF14181F)
    val Paper = Color(0xFFF7F8F6)
    val PaperDark = Color(0xFF101317)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceDark = Color(0xFF191D23)
    val Slate = Color(0xFF5A6472)
    val SlateDark = Color(0xFF97A1B0)
    val Hairline = Color(0xFFDFE3E1)
    val HairlineDark = Color(0xFF2A3039)

    // Category signals, low chroma on purpose.
    val Calm = Color(0xFF3E7A5E)
    val Watch = Color(0xFFB08319)
    val Raised = Color(0xFFC2632C)
    val High = Color(0xFFA83E32)
    val Crisis = Color(0xFF7E1F17)

    val CalmDark = Color(0xFF6FB894)
    val WatchDark = Color(0xFFDCB250)
    val RaisedDark = Color(0xFFE79461)
    val HighDark = Color(0xFFE97A6C)
    val CrisisDark = Color(0xFFFF8D7C)

    fun signal(category: BpCategory, dark: Boolean): Color = when (category.severity) {
        0 -> if (dark) CalmDark else Calm
        1 -> if (dark) WatchDark else Watch
        2 -> if (dark) RaisedDark else Raised
        3 -> if (dark) HighDark else High
        else -> if (dark) CrisisDark else Crisis
    }

    /** A wash of the signal colour for badge backgrounds — never a solid fill. */
    fun signalWash(category: BpCategory, dark: Boolean): Color =
        signal(category, dark).copy(alpha = if (dark) 0.18f else 0.12f)
}

/** 8dp base rhythm. */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val gutter = 20.dp
}

/**
 * Numbers get a monospaced, tabular treatment throughout.
 *
 * This is not decoration. Digits in a fixed-width column mean the history list can be
 * scanned vertically — systolic values line up, so a rising trend is visible without
 * reading a single number. It also echoes the cuff's own readout without stooping to
 * faking a seven-segment LCD, which would be kitsch.
 */
object Type {
    val Reading = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 46.sp,
        letterSpacing = (-2).sp,
        textAlign = TextAlign.Start
    )
    val ReadingMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        letterSpacing = (-1).sp
    )
    val ReadingSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        letterSpacing = (-0.5).sp
    )
    val Heading = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        letterSpacing = (-0.4).sp
    )
    val Title = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        letterSpacing = (-0.1).sp
    )
    val Body = TextStyle(fontSize = 14.sp, lineHeight = 20.sp)
    val Small = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp)

    /** Section markers. Uppercase and widely tracked so they read as structure, not content. */
    val Eyebrow = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp
    )
    val Unit = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.5.sp
    )
}

/**
 * User-chosen text scale, multiplied on top of whatever the system is already doing.
 *
 * Someone who has set their phone to large text still gets that, and can then go further
 * here without changing every other app. Applied by overriding density's fontScale, so
 * every sp value in the app scales together and no layout is left behind.
 */
val LocalTextScale: ProvidableCompositionLocal<Float> = compositionLocalOf { 1f }

object TextScale {
    val options = listOf(0.85f, 1.0f, 1.15f, 1.3f, 1.5f)
    fun label(scale: Float) = when {
        scale < 0.95f -> "Compact"
        scale < 1.05f -> "Default"
        scale < 1.2f -> "Large"
        scale < 1.4f -> "Larger"
        else -> "Largest"
    }
}

@Composable
fun ScaledText(scale: Float, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * scale),
        LocalTextScale provides scale,
        content = content
    )
}
