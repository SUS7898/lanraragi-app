package com.sus7898.lrrviewer.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sus7898.lrrviewer.data.api.Archive
import com.sus7898.lrrviewer.ui.common.RatingStars

/** Long-press menu of a library card: rate, favourite, NEW flag, open — without going through the detail screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveQuickSheet(
    archive: Archive,
    favorite: Boolean,
    favoritesSupported: Boolean,
    canEdit: Boolean,
    busy: Boolean,
    onRate: (Int?) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleNew: () -> Unit,
    onOpenDetail: () -> Unit,
    onReadFromStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(archive.title.ifBlank { archive.filename }, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!archive.isTankoubon) {
                RatingStars(rating = archive.rating, enabled = canEdit && !busy, onRate = onRate, size = 44.dp)
                if (!canEdit) {
                    Text(
                        "평점·즐겨찾기·NEW는 서버에 저장되므로 설정에서 API 키를 입력해야 바꿀 수 있습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (favoritesSupported) {
                    ListItem(
                        headlineContent = { Text(if (favorite) "즐겨찾기 해제" else "즐겨찾기에 추가") },
                        leadingContent = {
                            Icon(
                                if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = null,
                                tint = if (favorite) Color(0xFFE53935) else LocalContentColor.current,
                            )
                        },
                        modifier = Modifier.clickableIf(canEdit && !busy, onToggleFavorite),
                    )
                }
                ListItem(
                    headlineContent = { Text(if (archive.isnew) "NEW 표시 해제" else "NEW 표시") },
                    leadingContent = { Icon(Icons.Filled.FiberNew, contentDescription = null) },
                    modifier = Modifier.clickableIf(canEdit && !busy, onToggleNew),
                )
                ListItem(
                    headlineContent = { Text("처음부터 읽기") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                    modifier = Modifier.clickableIf(true, onReadFromStart),
                )
            }
            ListItem(
                headlineContent = { Text("상세 보기") },
                leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                modifier = Modifier.clickableIf(true, onOpenDetail),
            )
        }
    }
}

private fun Modifier.clickableIf(enabled: Boolean, onClick: () -> Unit): Modifier = this.clickable(enabled = enabled, onClick = onClick)
