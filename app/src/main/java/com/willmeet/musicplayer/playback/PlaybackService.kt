package com.willmeet.musicplayer.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.willmeet.musicplayer.MainActivity

/**
 * 后台播放服务。
 *
 * 这是安卓上「关掉界面继续放歌」的唯一正确做法 —— 对应 macOS 的菜单栏常驻、
 * Windows 的托盘常驻。顺带白拿通知栏控制、锁屏封面、蓝牙耳机按键、
 * 车机与手表控制，这些都由 [MediaSessionService] 统一提供。
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessorChain(
                    DefaultAudioSink.DefaultAudioProcessorChain(GainProcessorHolder.processor)
                )
                .build()
        }

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                // 处理音频焦点：来电话、别的 App 放声音时自动让位
                /* handleAudioFocus = */ true
            )
            // 拔耳机自动暂停，避免外放尴尬
            .setHandleAudioBecomingNoisy(true)
            .build()

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * 用户从最近任务里划掉 App 时：还在放就继续（通知还在，符合音乐 App 的预期），
     * 已经暂停了就顺手停掉服务，不占后台。
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}

/**
 * 增益处理器必须在渲染器构建时就位，而那发生在服务内部；
 * 界面侧又要在切歌时改增益值。用一个进程内单例把两边接起来。
 */
@OptIn(UnstableApi::class)
object GainProcessorHolder {
    val processor: GainAudioProcessor by lazy { GainAudioProcessor() }
}
