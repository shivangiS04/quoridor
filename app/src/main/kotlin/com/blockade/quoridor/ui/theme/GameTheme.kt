package com.blockade.quoridor.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Flip to true to ship the light variant instead of the default dark theme. */
const val USE_LIGHT_THEME = false

/**
 * Every colour the game UI uses. Screens read these through [GameTheme.colors]; nothing hard-codes a colour.
 * The look is deliberately flat and neutral: colour is reserved for the two players, the active turn and warnings.
 */
data class GamePalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,        // cards, chips, sheets
    val surfaceHigh: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val outline: Color,
    // Board
    val tile: Color,
    val tileBorder: Color,
    // Identities
    val player: Color,         // you
    val opponent: Color,       // computer / remote opponent
    val neutralWall: Color,    // wall whose owner is unknown (e.g. a game resumed mid-way)
    // State
    val active: Color,         // whose turn it is
    val activeSurface: Color,
    val invalid: Color,        // illegal wall / error
)

val DarkPalette = GamePalette(
    isDark = true,
    background = Color(0xFF141414),
    surface = Color(0xFF1A1A1A),
    surfaceHigh = Color(0xFF242424),
    onSurface = Color(0xFFF2F2F2),
    onSurfaceMuted = Color(0xFF8E8E93),
    outline = Color(0xFF2F2F2F),
    tile = Color(0xFF2A2A2A),
    tileBorder = Color(0xFF3E3E3E),
    player = Color(0xFFEF4444),
    opponent = Color(0xFF3B82F6),
    neutralWall = Color(0xFFB8B8B8),
    active = Color(0xFF22C55E),
    activeSurface = Color(0xFF14271B),
    invalid = Color(0xFFF59E0B),
)

val LightPalette = GamePalette(
    isDark = false,
    background = Color(0xFFF4F4F5),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFEDEDEE),
    onSurface = Color(0xFF18181B),
    onSurfaceMuted = Color(0xFF71717A),
    outline = Color(0xFFE0E0E3),
    tile = Color(0xFFE4E4E7),
    tileBorder = Color(0xFFCFCFD4),
    player = Color(0xFFDC2626),
    opponent = Color(0xFF2563EB),
    neutralWall = Color(0xFF52525B),
    active = Color(0xFF16A34A),
    activeSurface = Color(0xFFE7F6EC),
    invalid = Color(0xFFD97706),
)

/** Corner radii, spacing and sizes. */
object Dimens {
    val tileRadius = 6.dp
    val boardCornerRadius = 14.dp
    val cardRadius = 12.dp
    val chipRadius = 10.dp
    val sheetRadius = 20.dp
    val screenPadding = 16.dp
    val gap = 12.dp
    val gapSmall = 8.dp
    /** Minimum touch target (accessibility). */
    val touchTarget = 48.dp
}

/** Animation timings (ms). Motion is kept short and quiet. */
object Motion {
    const val Quick = 120
    const val Standard = 180
    const val Pawn = 200
    const val Shake = 300
    val Ease = FastOutSlowInEasing
}

private val Sans = FontFamily.Default

val GameTypography = androidx.compose.material3.Typography(
    displayLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 46.sp),
    displayMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)

val LocalGamePalette = staticCompositionLocalOf { DarkPalette }

object GameTheme {
    val colors: GamePalette
        @Composable @ReadOnlyComposable get() = LocalGamePalette.current
}

@Composable
fun ProvideGamePalette(palette: GamePalette, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGamePalette provides palette, content = content)
}
