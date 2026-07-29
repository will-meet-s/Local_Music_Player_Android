package com.willmeet.musicplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.willmeet.musicplayer.model.PlayMode

/** 底部传输控制条：进度 + 上一首 / 播放暂停 / 下一首 / 停止 / 播放顺序。 */
@Composable
fun ControlsBar(viewModel: PlayerViewModel) {

    val position by viewModel.positionMs.collectAsStateWithLifecycle()
    val duration by viewModel.durationMs.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playMode by viewModel.playMode.collectAsStateWithLifecycle()

    // 拖动期间用本地值，避免播放进度回调把滑块拽回去
    var isSeeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }

    val shown = if (isSeeking) seekValue else position.toFloat()
    val max = duration.coerceAtLeast(1L).toFloat()

    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatTime(shown.toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(42.dp)
            )

            Slider(
                value = shown.coerceIn(0f, max),
                onValueChange = {
                    isSeeking = true
                    seekValue = it
                },
                onValueChangeFinished = {
                    viewModel.seekTo(seekValue.toLong())
                    isSeeking = false
                },
                valueRange = 0f..max,
                enabled = duration > 0,
                modifier = Modifier.weight(1f)
            )

            Text(
                formatTime(duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .width(42.dp)
                    .padding(start = 4.dp)
            )
        }

        // 顺序：上一首 → 播放/暂停 → 下一首 → 停止 → 播放顺序
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = viewModel::previous) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "上一首")
            }

            IconButton(onClick = viewModel::togglePlayPause) {
                Icon(
                    if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(44.dp)
                )
            }

            IconButton(onClick = viewModel::next) {
                Icon(Icons.Default.SkipNext, contentDescription = "下一首")
            }

            IconButton(onClick = viewModel::stop) {
                Icon(Icons.Default.Stop, contentDescription = "停止")
            }

            IconButton(onClick = viewModel::cyclePlayMode) {
                Icon(
                    imageVector = when (playMode) {
                        PlayMode.SEQUENTIAL -> Icons.AutoMirrored.Filled.ArrowForward
                        PlayMode.REPEAT_ALL -> Icons.Default.Repeat
                        PlayMode.REPEAT_ONE -> Icons.Default.RepeatOne
                        PlayMode.SHUFFLE -> Icons.Default.Shuffle
                    },
                    contentDescription = playMode.displayName,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
