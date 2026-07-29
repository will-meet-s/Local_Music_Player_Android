package com.willmeet.musicplayer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.willmeet.musicplayer.prefs.Preferences
import kotlin.math.roundToInt

/** 设置面板：音频处理 + 音量 + 外观。 */
@Composable
fun SettingsSheet(viewModel: PlayerViewModel) {

    val replayGain by viewModel.replayGainEnabled.collectAsStateWithLifecycle()
    val volume by viewModel.volume.collectAsStateWithLifecycle()
    val opacity by viewModel.backgroundOpacity.collectAsStateWithLifecycle()

    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {

        Text("音频", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("音量归一化", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "按文件里的 ReplayGain 标签补偿响度差异。没打标签的文件不受影响。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = replayGain, onCheckedChange = viewModel::setReplayGainEnabled)
        }

        Text(
            "音量",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 14.dp)
        )
        Slider(value = volume, onValueChange = viewModel::setVolume, valueRange = 0f..1f)

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "背景不透明度",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${(opacity * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Slider(
            value = opacity,
            onValueChange = viewModel::setBackgroundOpacity,
            valueRange = Preferences.MIN_BACKGROUND_OPACITY..1f
        )

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "最低 ${(Preferences.MIN_BACKGROUND_OPACITY * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { viewModel.setBackgroundOpacity(1f) }) {
                Text("恢复不透明", style = MaterialTheme.typography.labelMedium)
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        Text(
            "关掉界面后音乐继续在通知栏播放。通知栏与锁屏可以切歌、暂停，"
                + "蓝牙耳机和车机按键同样有效。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 20.dp)
        )
    }
}
