package com.blockade.quoridor.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blockade.quoridor.R

/** Flip to true to ship the light "wooden" variant instead of the default night theme. */
const val USE_LIGHT_WOOD_THEME = false

/** Every colour the game UI uses. Screens read these through [GameTheme.colors]; nothing hard-codes a colour. */
data class GamePalette(
    val isDark: Boolean,
    // Screen
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val vignette: Color,
    val surface: Color,        // HUD cards, chips, sheets
    val surfaceHigh: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val outline: Color,
    // Board
    val boardFrame: Color,
    val boardFrameEdge: Color,
    val groove: Color,         // gutters between tiles — the "wall grooves"
    val tileTop: Color,
    val tileBottom: Color,
    // Identities
    val player: Color,         // you
    val playerDark: Color,
    val opponent: Color,       // computer / remote opponent
    val opponentDark: Color,
    val neutralWall: Color,    // wall whose owner is unknown (e.g. game resumed mid-way)
    // Feedback
    val invalid: Color,
    val success: Color,
)

val NightPalette = GamePalette(
    isDark = true,
    backgroundTop = Color(0xFF0F1226),
    backgroundBottom = Color(0xFF1E2350),
    vignette = Color(0xCC05060F),
    surface = Color(0xFF1B2046),
    surfaceHigh = Color(0xFF262C5C),
    onSurface = Color(0xFFF4F5FF),
    onSurfaceMuted = Color(0xFFA9AFD6),
    outline = Color(0xFF3A4180),
    boardFrame = Color(0xFF2A2140),
    boardFrameEdge = Color(0xFF4B3A66),
    groove = Color(0xFF0E1022),
    tileTop = Color(0xFFF3E9D2),
    tileBottom = Color(0xFFDCCBA6),
    player = Color(0xFF22D3EE),
    playerDark = Color(0xFF0E7490),
    opponent = Color(0xFFFB7185),
    opponentDark = Color(0xFFBE123C),
    neutralWall = Color(0xFFE8B04B),
    invalid = Color(0xFFFF4D5E),
    success = Color(0xFF34D399),
)

val LightWoodPalette = GamePalette(
    isDark = false,
    backgroundTop = Color(0xFFF6EBD6),
    backgroundBottom = Color(0xFFE3CFA9),
    vignette = Color(0x55402A10),
    surface = Color(0xFFFFF7E8),
    surfaceHigh = Color(0xFFF1E2C2),
    onSurface = Color(0xFF2B2013),
    onSurfaceMuted = Color(0xFF7A6446),
    outline = Color(0xFFCDB68A),
    boardFrame = Color(0xFF8A5A2B),
    boardFrameEdge = Color(0xFFB57C42),
    groove = Color(0xFF4A2E12),
    tileTop = Color(0xFFF7E8C8),
    tileBottom = Color(0xFFE6CFA0),
    player = Color(0xFF0891B2),
    playerDark = Color(0xFF155E75),
    opponent = Color(0xFFE11D48),
    opponentDark = Color(0xFF9F1239),
    neutralWall = Color(0xFFB45309),
    invalid = Color(0xFFDC2626),
    success = Color(0xFF059669),
)

/** Corner radii, spacing and sizes. */
object Dimens {
    val boardRadius = 20.dp
    val tileRadius = 5.dp
    val cardRadius = 18.dp
    val chipRadius = 14.dp
    val sheetRadius = 28.dp
    val screenPadding = 16.dp
    val gap = 12.dp
    val gapSmall = 8.dp
    /** Minimum touch target (accessibility). */
    val touchTarget = 48.dp
}

/** Animation timings (ms) and easings; everything stays inside 150–350 ms. */
object Motion {
    const val Quick = 150
    const val Standard = 250
    const val Pawn = 320
    const val Wall = 300
    const val Shake = 350
    const val Pulse = 1100
    val Ease = FastOutSlowInEasing
    /** Slight overshoot, used for the wall "thud". */
    val Settle = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.25f)
}

private val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_semibold, FontWeight.Bold),
)

/** Poppins everywhere; headings are SemiBold for a rounded, playful feel. */
val GameTypography = androidx.compose.material3.Typography(
    displayLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 44.sp, lineHeight = 50.sp),
    displayMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 42.sp),
    headlineLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)

val LocalGamePalette = staticCompositionLocalOf { NightPalette }

object GameTheme {
    val colors: GamePalette
        @Composable @ReadOnlyComposable get() = LocalGamePalette.current
}

@Composable
fun ProvideGamePalette(palette: GamePalette, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGamePalette provides palette, content = content)
}
