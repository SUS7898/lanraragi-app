package com.sus7898.lrrviewer.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sus7898.lrrviewer.data.api.Archive

@Composable
fun ArchiveCard(
    archive: Archive,
    thumbnailUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    favorite: Boolean = false,
) {
    Card(onClick = onClick, modifier = modifier) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f),
            ) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = archive.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (archive.isnew) Badge { Text("NEW") }
                    if (archive.isTankoubon) Badge(containerColor = MaterialTheme.colorScheme.tertiary) { Text("묶음") }
                }
                if (favorite) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = "즐겨찾기",
                        tint = Color(0xFFE53935),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(18.dp),
                    )
                }
                archive.rating?.let { r ->
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        contentColor = Color(0xFFFFC107),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp),
                    ) {
                        Text(
                            "★ $r",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }
                if (archive.pagecount > 0) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        contentColor = Color.White,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp),
                    ) {
                        Text(
                            "${archive.pagecount}p",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }
                if (archive.progress > 0 && archive.pagecount > 0) {
                    LinearProgressIndicator(
                        progress = { (archive.progress.toFloat() / archive.pagecount).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp),
                    )
                }
            }
            Text(
                archive.title.ifBlank { archive.filename },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
