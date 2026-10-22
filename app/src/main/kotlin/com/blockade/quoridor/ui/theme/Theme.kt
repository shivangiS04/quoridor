package com.blockade.quoridor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * The game is designed as a dark "night board" and does not follow the system theme or dynamic colours,
 * so that the board, pawns and walls always keep their contrast. Set [USE_LIGHT_WOOD_THEME] for the light variant.
 */
@Composable
fun QuoridorTheme(
    palette: GamePalette = if (USE_LIGHT_WOOD_THEME) LightWoodPalette else NightPalette,
    content: @Composable () -> Unit,
) {
    val scheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.player,
            onPrimary = palette.groove,
            secondary = palette.opponent,
            tertiary = palette.neutralWall,
            background = palette.backgroundTop,
            onBackground = palette.onSurface,
            surface = palette.surface,
            onSurface = palette.onSurface,
            surfaceVariant = palette.surfaceHigh,
            onSurfaceVariant = palette.onSurfaceMuted,
            outline = palette.outline,
            error = palette.invalid,
        )
    } else {
        lightColorScheme(
            primary = palette.player,
            onPrimary = palette.surface,
            secondary = palette.opponent,
            tertiary = palette.neutralWall,
            background = palette.backgroundTop,
            onBackground = palette.onSurface,
            surface = palette.surface,
            onSurface = palette.onSurface,
            surfaceVariant = palette.surfaceHigh,
            onSurfaceVariant = palette.onSurfaceMuted,
            outline = palette.outline,
            error = palette.invalid,
        )
    }
    ProvideGamePalette(palette) {
        MaterialTheme(colorScheme = scheme, typography = GameTypography, content = content)
    }
}
