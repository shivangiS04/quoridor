package com.blockade.quoridor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * The game uses its own flat palette and does not follow the system theme or dynamic colours,
 * so the board and pieces always keep their contrast. Set [USE_LIGHT_THEME] for the light variant.
 */
@Composable
fun QuoridorTheme(
    palette: GamePalette = if (USE_LIGHT_THEME) LightPalette else DarkPalette,
    content: @Composable () -> Unit,
) {
    val scheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.onSurface,
            onPrimary = palette.background,
            secondary = palette.opponent,
            tertiary = palette.neutralWall,
            background = palette.background,
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
            primary = palette.onSurface,
            onPrimary = palette.background,
            secondary = palette.opponent,
            tertiary = palette.neutralWall,
            background = palette.background,
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
