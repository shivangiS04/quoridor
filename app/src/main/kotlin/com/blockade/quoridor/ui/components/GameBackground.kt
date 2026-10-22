package com.blockade.quoridor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.blockade.quoridor.ui.theme.GameTheme

/** Flat screen background; also makes the palette's text colour the default for everything inside. */
@Composable
fun GameBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = GameTheme.colors
    Box(modifier.fillMaxSize().background(c.background)) {
        CompositionLocalProvider(LocalContentColor provides c.onSurface) { content() }
    }
}
