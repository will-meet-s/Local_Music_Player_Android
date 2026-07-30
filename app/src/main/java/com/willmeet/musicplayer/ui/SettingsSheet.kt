package com.willmeet.musicplayer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 设置面板：音频处理 + 音量 + 退出。
 *
 * @param onQuit 关闭界面。ViewModel 只管停播放与停服务，`finish()` 得由 Activity 做。
 */
@Composable
fun SettingsSheet(viewModel: PlayerViewModel, onQuit: () -> Unit) {

    val replayGain by viewModel.replayGainEnabled.collectAsStateWithLifecycle()
    val volume by viewModel.volume.collectAsStateWithLifecycle()

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

        Text(
            "关掉界面后音乐继续在通知栏播放。通知栏与锁屏可以切歌、暂停，"
                + "蓝牙耳机和车机按键同样有效。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        // 上面那段说了「关掉界面还在放」，所以这里必须给一个真正的出口
        Text("退出", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)

        Text(
            "停止播放并退出，通知栏一并消失。下次听歌要重新打开 App。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        // 淡红填充而不是纯文字按钮：有底色才不会被当成又一段说明文字。
        // 不用实心 error 红 —— 这个面板里全是 Switch / Slider，一片饱和红太抢眼。
        FilledTonalButton(
            onClick = {
                viewModel.quit()
                onQuit()
            },
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            modifier = Modifier
                .padding(top = 12.dp, bottom = 20.dp)
                .fillMaxWidth()
        ) {
            Icon(
                Icons.Default.PowerSettingsNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("关闭程序", fontWeight = FontWeight.SemiBold)
        }
    }
}
