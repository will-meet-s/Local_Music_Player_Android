package com.willmeet.musicplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 歌词区：带时间戳时逐行高亮并自动滚动；无时间戳时静态展示全文。 */
@Composable
fun LyricsPane(viewModel: PlayerViewModel, modifier: Modifier = Modifier) {

    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentLyricIndex.collectAsStateWithLifecycle()
    val synced by viewModel.lyricsAreSynced.collectAsStateWithLifecycle()

    if (lyrics.isEmpty()) {
        Column(
            modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Notes,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp)
            )
            Text(
                "暂无歌词",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                "把同名 .lrc 文件放在音频旁边即可显示",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val listState = rememberLazyListState()

    // 当前行滚到视图中部。用 animateScrollToItem 而不是 scrollToItem，
    // 否则每换一行画面会硬跳。
    LaunchedEffect(currentIndex) {
        if (!synced || currentIndex < 0) return@LaunchedEffect

        val visible = listState.layoutInfo.visibleItemsInfo
        val offset = if (visible.isEmpty()) 0 else -(listState.layoutInfo.viewportSize.height / 3)

        runCatching { listState.animateScrollToItem(currentIndex, offset) }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        itemsIndexed(lyrics, key = { index, _ -> index }) { index, line ->
            val isCurrent = synced && index == currentIndex

            Text(
                text = line.text.ifEmpty { " " },
                textAlign = TextAlign.Center,
                fontSize = if (isCurrent) 16.sp else 14.sp,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = synced && line.time >= 0) {
                        // 点歌词跳播到该行
                        viewModel.seekTo((line.time * 1000).toLong())
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
