package com.sus7898.lrrviewer.ui.reader

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import com.sus7898.lrrviewer.data.ColorFilterMode

/**
 * Applies the reader colour filter to everything drawn inside this node. Works on every supported API level:
 * the content is composited offscreen and a rectangle is blended over it (Saturation = greyscale, Multiply =
 * tint), so no RenderEffect (API 31+) is needed.
 */
fun Modifier.readerColorFilter(mode: ColorFilterMode): Modifier =
    if (mode == ColorFilterMode.NONE) this
    else this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            when (mode) {
                ColorFilterMode.NONE -> Unit
                ColorFilterMode.GRAYSCALE -> drawRect(Color.Gray, blendMode = BlendMode.Saturation)
                ColorFilterMode.SEPIA -> {
                    drawRect(Color.Gray, blendMode = BlendMode.Saturation)
                    drawRect(Color(0xFFD9B98C), blendMode = BlendMode.Multiply)
                }
                ColorFilterMode.WARM -> drawRect(Color(0xFFFFE2BE), blendMode = BlendMode.Multiply)
            }
        }
