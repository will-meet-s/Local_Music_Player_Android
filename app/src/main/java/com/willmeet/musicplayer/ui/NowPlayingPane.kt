package com.willmeet.musicplayer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.willmeet.musicplayer.model.NowPlayingLayout

/** 「正在播放」区：封面 + 曲目信息 + 歌词，三种模式由右上角缩略图切换。 */
@Composable
fun NowPlayingPane(viewModel: PlayerViewModel, modifier: Modifier = Modifier) {

    val track by viewModel.playingTrack.collectAsStateWithLifecycle()
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val missing by viewModel.playingTrackMissing.collectAsStateWithLifecycle()

    Box(modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {

        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (layout.showsArtwork) {
                Artwork(
                    data = track?.artwork,
                    modifier = if (layout == NowPlayingLayout.ARTWORK_ONLY) {
                        Modifier.weight(1f).aspectRatio(1f)
                    } else {
                        Modifier.size(150.dp)
                    }
                )
            }

            Text(
                text = track?.title ?: "未在播放",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp)
            )

            val subtitle = track?.subtitle.orEmpty()
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (missing) {
                Row(
                    Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFFA000),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        "文件已不在曲库中，本曲仍可播完",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (layout.showsLyrics) {
                LyricsPane(
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 8.dp)
                )
            }
        }

        // 浮在右上角，不占布局空间，也不抢歌词行的点击
        LayoutThumbnail(
            layout = layout,
            onClick = viewModel::cycleLayout,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

@Composable
private fun Artwork(data: ByteArray?, modifier: Modifier = Modifier) {
    // 按字节数组内容缓存解码结果，避免每次重组都解一遍图
    val bitmap = remember(data) {
        data?.let {
            runCatching {
                android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)
            }.getOrNull()
        }
    }

    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "专辑封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

/** 用色块画出当前布局的示意图，点一下换下一种模式。 */
@Composable
private fun LayoutThumbnail(
    layout: NowPlayingLayout,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val block = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    val line = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)

    Box(
        modifier
            .size(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            when (layout) {
                NowPlayingLayout.ARTWORK_AND_LYRICS -> {
                    Box(Modifier.size(11.dp).clip(RoundedCornerShape(2.dp)).background(block))
                    Bar(width = 22, color = line)
                    Bar(width = 16, color = line)
                }

                NowPlayingLayout.ARTWORK_ONLY -> {
                    Box(Modifier.size(22.dp).clip(RoundedCornerShape(3.dp)).background(block))
                }

                NowPlayingLayout.LYRICS_ONLY -> {
                    Bar(width = 22, color = line)
                    Bar(width = 17, color = line)
                    Bar(width = 20, color = line)
                    Bar(width = 13, color = line)
                }
            }
        }
    }
}

@Composable
private fun Bar(width: Int, color: Color) {
    Box(
        Modifier
            .width(width.dp)
            .size(width = width.dp, height = 2.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(color)
    )
}
