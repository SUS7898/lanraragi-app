package com.sus7898.lrrviewer.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sus7898.lrrviewer.data.api.Archive

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArchiveCard(
    archive: Archive,
    thumbnailUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    favorite: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    Card(modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "빠른 메뉴")) {
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

/** List-view row: cover, title, a few tags, pages/progress, rating, favourite and NEW markers. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArchiveListRow(
    archive: Archive,
    thumbnailUrl: String,
    onClick: () -> Unit,
    favorite: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val tagLine = remember(archive.tags) {
        archive.tagList
            .filter { it.namespace in setOf("artist", "group", "series", "parody") }
            .joinToString(" · ") { it.value }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "빠른 메뉴")
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = archive.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(64.dp).aspectRatio(0.7f).clip(MaterialTheme.shapes.small),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (archive.isnew) Badge { Text("NEW") }
                if (archive.isTankoubon) Badge(containerColor = MaterialTheme.colorScheme.tertiary) { Text("묶음") }
                if (favorite) Icon(Icons.Filled.Favorite, contentDescription = "즐겨찾기", tint = Color(0xFFE53935), modifier = Modifier.size(16.dp))
                archive.rating?.let { Text("★ $it", style = MaterialTheme.typography.labelMedium, color = Color(0xFFFFA000)) }
            }
            Text(archive.title.ifBlank { archive.filename }, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (tagLine.isNotBlank()) {
                Text(tagLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val pages = buildString {
                if (archive.pagecount > 0) append("${archive.pagecount}p")
                if (archive.progress > 0 && archive.pagecount > 0) append(" · ${archive.progress}/${archive.pagecount}")
            }
            if (pages.isNotEmpty()) Text(pages, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (archive.progress > 0 && archive.pagecount > 0) {
                LinearProgressIndicator(
                    progress = { (archive.progress.toFloat() / archive.pagecount).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                )
            }
        }
    }
}
