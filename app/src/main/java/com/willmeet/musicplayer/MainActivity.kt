package com.willmeet.musicplayer

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import com.willmeet.musicplayer.ui.PlayerScreen
import com.willmeet.musicplayer.ui.PlayerViewModel
import com.willmeet.musicplayer.ui.theme.MusicPlayerTheme

class MainActivity : ComponentActivity() {

    /**
     * Android 13 起通知要用户点头，不给就看不到播放控制。
     * 不影响播放本身，所以拒绝了也不拦着。
     */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MusicPlayerTheme {
                val viewModel: PlayerViewModel = viewModel()
                PlayerScreen(viewModel, onQuit = { finish() })
            }
        }
    }
}
