package com.sus7898.lrrviewer.ui.reader

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import com.sus7898.lrrviewer.data.ColorFilterMode

/** 4x5 colour matrix of each filter (row-major, as android.graphics.ColorMatrix). */
internal fun filterMatrix(mode: ColorFilterMode): ColorMatrix? = when (mode) {
    ColorFilterMode.NONE -> null
    ColorFilterMode.GRAYSCALE -> ColorMatrix().apply { setToSaturation(0f) }
    ColorFilterMode.SEPIA -> ColorMatrix(
        floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    ColorFilterMode.WARM -> ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 0.92f, 0f, 0f, 0f,
            0f, 0f, 0.74f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
}

/**
 * Applies the reader colour filter to everything drawn inside this node: the content is drawn into a layer whose
 * paint carries a ColorMatrix filter. Unlike blend modes (Saturation/Multiply need API 29+ and silently fall back to
 * an opaque fill on Android 9) and RenderEffect (API 31+), this works on every supported version.
 */
fun Modifier.readerColorFilter(mode: ColorFilterMode): Modifier {
    val matrix = filterMatrix(mode) ?: return this
    val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(matrix) }
    return this.drawWithContent {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), paint)
            drawContent()
            canvas.restore()
        }
    }
}
