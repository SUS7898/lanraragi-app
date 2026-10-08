package com.sus7898.lrrviewer.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.sus7898.lrrviewer.AppGraph
import com.sus7898.lrrviewer.data.api.Archive
import com.sus7898.lrrviewer.data.api.Tag
import com.sus7898.lrrviewer.data.api.TankoubonFull
import com.sus7898.lrrviewer.ui.common.ErrorView
import com.sus7898.lrrviewer.ui.common.LoadingView
import com.sus7898.lrrviewer.ui.common.formatBytes
import com.sus7898.lrrviewer.ui.common.formatDate
import com.sus7898.lrrviewer.ui.common.formatDateTime
import com.sus7898.lrrviewer.ui.common.openUrl
import com.sus7898.lrrviewer.ui.library.ArchiveCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveDetailScreen(
    graph: AppGraph,
    id: String,
    onBack: () -> Unit,
    onOpenReader: (page: Int) -> Unit,
    onOpenArchive: (String) -> Unit,
    onSearchTag: (String) -> Unit,
) {
    val vm: ArchiveDetailViewModel = viewModel(key = "detail_$id") {
        ArchiveDetailViewModel(graph.api, graph.progress, graph.favorites, graph.pageInfo, graph.settingsState, id)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.archive?.title?.ifBlank { null } ?: state.tank?.name ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
                },
                actions = {
                    if (state.archive != null && state.favoritesSupported) {
                        IconButton(onClick = vm::toggleFavorite, enabled = !state.busy) {
                            Icon(
                                if (state.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = if (state.favorite) "즐겨찾기 해제" else "즐겨찾기에 추가",
                                tint = if (state.favorite) Color(0xFFE53935) else LocalContentColor.current,
                            )
                        }
                    }
                    IconButton(onClick = { context.openUrl(graph.api.webReaderUrl(id)) }) {
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = "웹에서 열기")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val archive = state.archive
        val tank = state.tank
        when {
            state.loading -> LoadingView(modifier = Modifier.padding(padding))
            state.error != null -> ErrorView(state.error!!, modifier = Modifier.padding(padding), onRetry = { vm.load() })
            tank != null -> TankoubonContent(
                tank = tank,
                archives = state.tankArchives,
                favoriteIds = graph.favorites.state.collectAsStateWithLifecycle().value.ids,
                graph = graph,
                padding = padding,
                onOpenArchive = onOpenArchive,
                onSearchTag = onSearchTag,
            )
            archive != null -> ArchiveContent(
                archive = archive,
                thumbnailUrl = graph.api.thumbnailUrl(id),
                resumePage = vm.resumePage(),
                busy = state.busy,
                canEdit = state.canEdit,
                padding = padding,
                onOpenReader = onOpenReader,
                onToggleNew = vm::toggleNew,
                onReextract = vm::forceReextract,
                onRate = vm::setRating,
                onSearchTag = onSearchTag,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArchiveContent(
    archive: Archive,
    thumbnailUrl: String,
    resumePage: Int?,
    busy: Boolean,
    canEdit: Boolean,
    padding: PaddingValues,
    onOpenReader: (Int) -> Unit,
    onToggleNew: () -> Unit,
    onReextract: () -> Unit,
    onRate: (Int?) -> Unit,
    onSearchTag: (String) -> Unit,
) {
    val tagGroups = remember(archive.tags) {
        archive.tagList
            .filter { it.namespace != "date_added" && it.namespace != Archive.RATING_NAMESPACE }
            .groupBy { it.namespace }
            .toSortedMap(compareBy<String> { it.isEmpty() }.thenBy { it })
    }
    val toc = remember(archive.toc) { archive.tocEntries }

    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(140.dp)
                        .aspectRatio(0.7f)
                        .clip(MaterialTheme.shapes.medium),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(archive.title.ifBlank { archive.filename }, style = MaterialTheme.typography.titleMedium)
                    InfoLine("페이지", if (archive.pagecount > 0) "${archive.pagecount}" else "미확인 (첫 열람 시 집계)")
                    InfoLine("크기", formatBytes(archive.size))
                    archive.extension?.takeIf { it.isNotBlank() }?.let { InfoLine("형식", it.uppercase()) }
                    archive.dateAdded?.let { InfoLine("추가일", formatDate(it)) }
                    if (archive.lastreadtime > 0) InfoLine("마지막 읽음", formatDateTime(archive.lastreadtime))
                    if (archive.progress > 0 && archive.pagecount > 0) {
                        InfoLine("진행률", "${archive.progress} / ${archive.pagecount}" + if (archive.isCompleted) " (완독)" else "")
                        LinearProgressIndicator(
                            progress = { (archive.progress.toFloat() / archive.pagecount).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (archive.isnew) Badge { Text("NEW") }
                }
            }
        }

        item {
            RatingRow(rating = archive.rating, enabled = canEdit && !busy, onRate = onRate)
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onOpenReader(resumePage ?: 0) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            resumePage != null -> "이어 읽기 (${resumePage + 1}p)"
                            archive.isCompleted -> "다시 읽기"
                            else -> "읽기"
                        },
                    )
                }
                if (resumePage != null) {
                    OutlinedButton(onClick = { onOpenReader(0) }) { Text("처음부터") }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onToggleNew, enabled = !busy) { Text(if (archive.isnew) "NEW 해제" else "NEW 표시") }
                OutlinedButton(onClick = onReextract, enabled = !busy) { Text("서버 재추출") }
            }
        }

        if (!archive.summary.isNullOrBlank()) {
            item {
                Column {
                    Text("요약", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(archive.summary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (toc.isNotEmpty()) {
            item { Text("목차", style = MaterialTheme.typography.titleSmall) }
            items(toc, key = { "toc_${it.page}_${it.name}" }) { entry ->
                ListItem(
                    headlineContent = { Text(entry.name) },
                    trailingContent = { Text("${entry.page}p") },
                    modifier = Modifier.clickable { onOpenReader((entry.page - 1).coerceAtLeast(0)) },
                )
            }
        }

        if (tagGroups.isNotEmpty()) {
            item { Text("태그 (탭하면 검색)", style = MaterialTheme.typography.titleSmall) }
            items(tagGroups.entries.toList(), key = { "ns_${it.key}" }) { (ns, tags) ->
                TagGroup(namespace = ns, tags = tags, onSearchTag = onSearchTag)
            }
        }
    }
}

/** Five tappable stars; tapping the current rating clears it. Stored on the server as a `rating:N` tag. */
@Composable
private fun RatingRow(rating: Int?, enabled: Boolean, onRate: (Int?) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("평점", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(48.dp))
            (1..5).forEach { n ->
                IconButton(onClick = { onRate(if (rating == n) null else n) }, enabled = enabled, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (rating != null && n <= rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = "${n}점",
                        tint = if (rating != null && n <= rating) Color(0xFFFFC107) else LocalContentColor.current,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                rating?.let { "$it / 5" } ?: "없음",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!enabled) {
            Text(
                "평점·즐겨찾기는 서버 태그/카테고리에 저장됩니다. 설정에서 API 키를 입력하면 바꿀 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagGroup(namespace: String, tags: List<Tag>, onSearchTag: (String) -> Unit) {
    Column {
        Text(
            namespace.ifEmpty { "기타" },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tags.forEach { tag ->
                SuggestionChip(
                    onClick = { onSearchTag(tag.raw + "$") },
                    label = { Text(tag.value, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TankoubonContent(
    tank: TankoubonFull,
    archives: List<Archive>,
    favoriteIds: Set<String>,
    graph: AppGraph,
    padding: PaddingValues,
    onOpenArchive: (String) -> Unit,
    onSearchTag: (String) -> Unit,
) {
    val tagGroups = remember(tank.tags) {
        Archive.parseTags(tank.tags).filter { it.namespace != "date_added" }.groupBy { it.namespace }
            .toSortedMap(compareBy<String> { it.isEmpty() }.thenBy { it })
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tank.name, style = MaterialTheme.typography.titleMedium)
                Text("탄코본(묶음) · ${archives.size}권", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!tank.summary.isNullOrBlank()) Text(tank.summary, style = MaterialTheme.typography.bodyMedium)
                tagGroups.forEach { (ns, tags) -> TagGroup(namespace = ns, tags = tags, onSearchTag = onSearchTag) }
                Text("수록 아카이브", style = MaterialTheme.typography.titleSmall)
            }
        }
        items(archives, key = { it.arcid }) { a ->
            ArchiveCard(
                archive = a,
                thumbnailUrl = graph.api.thumbnailUrl(a.arcid),
                favorite = a.arcid in favoriteIds,
                onClick = { onOpenArchive(a.arcid) },
            )
        }
        if (archives.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text("수록된 아카이브가 없습니다") }
            }
        }
    }
}
