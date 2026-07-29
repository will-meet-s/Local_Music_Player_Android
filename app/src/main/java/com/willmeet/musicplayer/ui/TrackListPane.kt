package com.willmeet.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.willmeet.musicplayer.model.Track
import com.willmeet.musicplayer.model.TrackSortOrder

/** 曲目列表 + 搜索框 + 排序控件。 */
@Composable
fun TrackListPane(viewModel: PlayerViewModel, modifier: Modifier = Modifier) {

    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val search by viewModel.searchText.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val ascending by viewModel.sortAscending.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentIndex.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val folderName by viewModel.folderName.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()

    // 搜索或改排序之后列表内容整个换了，滚动位置却还停在原处，
    // 看到的是列表中段。必须手动回顶。
    LaunchedEffect(search, sortOrder, ascending) {
        runCatching { listState.scrollToItem(0) }
    }

    Column(modifier) {
        ListToolbar(
            search = search,
            onSearchChange = viewModel::setSearchText,
            sortOrder = sortOrder,
            onSortChange = viewModel::setSortOrder,
            ascending = ascending,
            onToggleDirection = viewModel::toggleSortDirection,
            matchCount = if (search.isNotBlank()) tracks.size else null
        )

        if (tracks.isEmpty()) {
            EmptyState(
                isFiltering = search.isNotBlank(),
                keyword = search,
                hasFolder = folderName != null,
                onClearSearch = { viewModel.setSearchText("") }
            )
            return@Column
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(tracks, key = { _, track -> track.uri }) { index, track ->
                TrackRow(
                    track = track,
                    isCurrent = index == currentIndex,
                    isPlaying = isPlaying,
                    onClick = { viewModel.playAt(index) }
                )
            }
        }
    }
}

@Composable
private fun ListToolbar(
    search: String,
    onSearchChange: (String) -> Unit,
    sortOrder: TrackSortOrder,
    onSortChange: (TrackSortOrder) -> Unit,
    ascending: Boolean,
    onToggleDirection: () -> Unit,
    matchCount: Int?
) {
    var menuOpen by remember { mutableStateOf(false) }

    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        TextField(
            value = search,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索歌曲、歌手、专辑") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (search.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "清除搜索")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(10.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                TextButton(onClick = { menuOpen = true }) {
                    Text(sortOrder.displayName, style = MaterialTheme.typography.labelLarge)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    TrackSortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { Text(order.displayName) },
                            onClick = {
                                onSortChange(order)
                                menuOpen = false
                            }
                        )
                    }
                }
            }

            IconButton(onClick = onToggleDirection) {
                Icon(
                    if (ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = if (ascending) "升序" else "降序",
                    modifier = Modifier.size(18.dp)
                )
            }

            Box(Modifier.weight(1f))

            if (matchCount != null) {
                Text(
                    "匹配 $matchCount 首",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            if (isCurrent) {
                Icon(
                    if (isPlaying) Icons.Default.VolumeUp else Icons.Default.Pause,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (track.subtitle.isNotEmpty()) {
                Text(
                    track.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (track.durationMs > 0) {
            Text(
                formatTime(track.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyState(
    isFiltering: Boolean,
    keyword: String,
    hasFolder: Boolean,
    onClearSearch: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            if (isFiltering) Icons.Default.Search else Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(36.dp)
        )
        Text(
            text = when {
                isFiltering -> "没有匹配「$keyword」的歌曲"
                hasFolder -> "该文件夹里没有音频文件"
                else -> "还没有选择音乐文件夹"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp)
        )
        if (isFiltering) {
            TextButton(onClick = onClearSearch) { Text("清除搜索") }
        }
    }
}

/** 毫秒格式化为 m:ss 或 h:mm:ss。 */
internal fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"

    val total = ms / 1000
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60

    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
