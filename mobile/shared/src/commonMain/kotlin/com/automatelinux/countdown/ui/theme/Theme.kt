package com.automatelinux.countdown.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Dusk: warm paper by day, lamplight by night — the same family as tally, so the two
 * sit together on a home screen, with apricot in place of tally's honey.
 *
 * Counting down is a quiet daily habit, and the number shrinking is good news, so
 * nothing here may look like a warning: there is no red anywhere, and "counted today"
 * is a calm sage rather than a signal green (tally issue #2 — a screen in alarm colours
 * made an easy habit feel like a reckoning).
 */
@Immutable
data class CountdownColors(
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val line: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val brand: Color,
    val onBrand: Color,
    val brandSoft: Color,
    /** Brand colour for text and icons sitting on a surface, where `brand` is too light. */
    val brandInk: Color,
    val done: Color,
    val doneSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val isDark: Boolean,
)

private val DarkPalette = CountdownColors(
    bg = Color(0xFF16130F),
    surface = Color(0xFF211C17),
    surfaceAlt = Color(0xFF2C251F),
    line = Color(0xFF3A3129),
    text = Color(0xFFF2EAE0),
    textDim = Color(0xFFADA093),
    textFaint = Color(0xFF8A7E71),
    brand = Color(0xFFF0A07A),
    onBrand = Color(0xFF2B160C),
    brandSoft = Color(0xFF3D2619),
    brandInk = Color(0xFFF0A07A),
    done = Color(0xFF96C9A0),
    doneSoft = Color(0xFF22301F),
    // Delete is the one act worth a second look — clay, still not red.
    danger = Color(0xFFE39B7C),
    dangerSoft = Color(0xFF36251C),
    isDark = true,
)

private val LightPalette = CountdownColors(
    bg = Color(0xFFF8F4EE),
    surface = Color(0xFFFFFCF8),
    surfaceAlt = Color(0xFFEFE7DC),
    line = Color(0xFFE3D8C9),
    text = Color(0xFF2D2925),
    textDim = Color(0xFF6C635A),
    textFaint = Color(0xFF857A6E),
    brand = Color(0xFFE88E66),
    onBrand = Color(0xFF2E1A10),
    brandSoft = Color(0xFFFAE7DC),
    brandInk = Color(0xFFA9532F),
    done = Color(0xFF3F7A54),
    doneSoft = Color(0xFFE4EEE2),
    danger = Color(0xFFB35C3E),
    dangerSoft = Color(0xFFF9E7DE),
    isDark = false,
)

val LocalCountdownColors = staticCompositionLocalOf { DarkPalette }

/** Shorthand: `T.brand` anywhere inside the theme. */
val T: CountdownColors
    @Composable get() = LocalCountdownColors.current

private val Tight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

/** The number left carries this app, so numbers get their own scale — heavy and tight. */
object Num {
    val hero = TextStyle(fontSize = 88.sp, lineHeight = 88.sp, fontWeight = FontWeight.Bold, letterSpacing = (-3).sp, lineHeightStyle = Tight)
    val large = TextStyle(fontSize = 44.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.4).sp, lineHeightStyle = Tight)
    val medium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
    val small = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
}

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    // Section labels are sentence case, not shouted small-caps — same reason as the palette.
    labelSmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
)

/**
 * The six colours a countdown can wear. Each is light enough that dark ink reads on
 * it in both themes, so a countdown's button is simply its own colour.
 */
val AccentPalette = listOf(
    Color(0xFFF0A07A),
    Color(0xFF93B88B),
    Color(0xFF8FAAC8),
    Color(0xFFDC9C90),
    Color(0xFFCDB66C),
    Color(0xFFADA3CF),
)

/** Ink on an accent. */
val OnAccent = Color(0xFF1F1611)

fun accentAt(index: Int): Color = AccentPalette[((index % AccentPalette.size) + AccentPalette.size) % AccentPalette.size]

@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkPalette else LightPalette
    // Stock Material components (dialogs, menus, switches, text selection) read the
    // colorScheme, so it is mapped onto the same palette instead of left at defaults.
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.brand, onPrimary = c.onBrand,
            primaryContainer = c.brandSoft, onPrimaryContainer = c.brand,
            secondary = c.brand, onSecondary = c.onBrand,
            background = c.bg, onBackground = c.text,
            surface = c.surface, onSurface = c.text,
            surfaceVariant = c.surfaceAlt, onSurfaceVariant = c.textDim,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surfaceAlt,
            surfaceContainerHighest = c.surfaceAlt,
            outline = c.line, outlineVariant = c.line,
            error = c.danger, onError = Color(0xFF36251C),
            inverseSurface = c.surfaceAlt, inverseOnSurface = c.text,
        )
    } else {
        lightColorScheme(
            primary = c.brandInk, onPrimary = Color.White,
            primaryContainer = c.brandSoft, onPrimaryContainer = Color(0xFF4A2414),
            secondary = c.brandInk, onSecondary = Color.White,
            background = c.bg, onBackground = c.text,
            surface = c.surface, onSurface = c.text,
            surfaceVariant = c.surfaceAlt, onSurfaceVariant = c.textDim,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surfaceAlt,
            surfaceContainerHighest = c.surfaceAlt,
            outline = c.line, outlineVariant = c.line,
            error = c.danger, onError = Color.White,
            inverseSurface = Color(0xFF2D2925), inverseOnSurface = Color(0xFFF8F4EE),
        )
    }
    CompositionLocalProvider(LocalCountdownColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
