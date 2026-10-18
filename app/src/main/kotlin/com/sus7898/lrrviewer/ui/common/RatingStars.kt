package com.sus7898.lrrviewer.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val RatingGold = Color(0xFFFFC107)

/** Five tappable stars; tapping the current rating clears it (null). */
@Composable
fun RatingStars(
    rating: Int?,
    enabled: Boolean,
    onRate: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    emptyTint: Color = LocalContentColor.current,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        (1..5).forEach { n ->
            val filled = rating != null && n <= rating
            IconButton(onClick = { onRate(if (rating == n) null else n) }, enabled = enabled, modifier = Modifier.size(size)) {
                Icon(
                    if (filled) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "${n}점",
                    tint = if (filled) RatingGold else emptyTint,
                )
            }
        }
    }
}
