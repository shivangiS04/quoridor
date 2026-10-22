package com.blockade.quoridor.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.blockade.quoridor.ui.theme.GameTheme
import kotlin.random.Random

/** Deep gradient with a soft vignette and a faint grain, used behind every screen. */
@Composable
fun GameBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = GameTheme.colors
    Box(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val gradient = Brush.verticalGradient(listOf(c.backgroundTop, c.backgroundBottom))
                val vignette = Brush.radialGradient(
                    colors = listOf(Color.Transparent, c.vignette),
                    center = Offset(size.width / 2, size.height * 0.45f),
                    radius = maxOf(size.width, size.height) * 0.85f,
                )
                // Deterministic grain so it doesn't shimmer between frames.
                val rng = Random(7)
                val grain = List(260) { Offset(rng.nextFloat() * size.width, rng.nextFloat() * size.height) }
                val grainColor = (if (c.isDark) Color.White else Color.Black).copy(alpha = 0.035f)
                onDrawBehind {
                    drawRect(gradient)
                    grain.forEach { drawCircle(grainColor, 1.4f, it) }
                    drawRect(vignette)
                }
            },
    ) {
        // Everything on the background defaults to the palette's text colour (Text would otherwise be black).
        CompositionLocalProvider(LocalContentColor provides c.onSurface) { content() }
    }
}
