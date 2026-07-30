package com.willmeet.musicplayer.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.MetadataRetriever
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.flac.PictureFrame
import androidx.media3.extractor.metadata.id3.ApicFrame
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.willmeet.musicplayer.lyrics.UsltFrame
import com.willmeet.musicplayer.model.Track
import com.willmeet.musicplayer.playback.ReplayGain
import java.util.concurrent.TimeUnit

/**
 * 读取音频元数据。
 *
 * 分两条路：
 * - [MediaMetadataRetriever]：系统自带，稳定拿到标题 / 歌手 / 专辑 / 时长 / 封面
 * - Media3 的 [MetadataRetriever]：解析 Vorbis Comment 与 ID3 TXXX，
 *   补上前者拿不到的**内嵌歌词**和 **ReplayGain**
 *
 * 任何一步失败都只是让对应字段留空 —— 扫描不该因为单个坏文件中断。
 */
object MetadataLoader {

    /**
     * 内嵌歌词的键名。
     *
     * 各容器叫法不一：FLAC / OGG 用 Vorbis Comment 的 `LYRICS`、mp3 用 TXXX 的自定义
     * 描述符或 USLT 帧、m4a 的 `©lyr` 被 Media3 折算成 id 为 `USLT` 的文本帧。
     */
    private val LYRICS_KEYS = setOf(
        "LYRICS", "UNSYNCEDLYRICS", "UNSYNCED LYRICS", "LYRIC", UsltFrame.FRAME_ID
    )

    fun load(context: Context, track: Track): Track {
        var result = loadBasic(context, track)
        result = loadExtended(context, result)
        return result.copy(metadataLoaded = true)
    }

    private fun loadBasic(context: Context, track: Track): Track {
        val retriever = MediaMetadataRetriever()

        return try {
            retriever.setDataSource(context, Uri.parse(track.uri))

            fun tag(key: Int) = retriever.extractMetadata(key)?.takeIf { it.isNotBlank() }

            track.copy(
                title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: track.title,
                artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                durationMs = tag(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                artwork = retriever.embeddedPicture
            )
        } catch (e: Exception) {
            // setDataSource 对损坏文件会抛 RuntimeException，标题已降级为文件名，够用
            track
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * 用 Media3 解析容器级元数据，取内嵌歌词与 ReplayGain。
     *
     * [MetadataRetriever.retrieveMetadata] 返回 Future，这里同步等待 —— 调用方本身
     * 就在后台协程里，不会卡 UI。超时是必须的：某些畸形文件会让它一直不返回。
     */
    @OptIn(UnstableApi::class)
    private fun loadExtended(context: Context, track: Track): Track {
        val groups = try {
            MetadataRetriever
                .retrieveMetadata(DefaultMediaSourceFactory(context), MediaItem.fromUri(track.uri))
                .get(8, TimeUnit.SECONDS)
        } catch (e: Exception) {
            return track
        }

        var lyrics: String? = null
        var gainDb: Double? = null
        var peak: Double? = null
        var picture: ByteArray? = null

        fun absorb(key: String, value: String?) {
            if (value.isNullOrBlank()) return
            val upper = key.uppercase()
            when {
                lyrics == null && upper in LYRICS_KEYS -> lyrics = value
                gainDb == null && ReplayGain.isTrackGainKey(upper) -> gainDb = ReplayGain.parseGain(value)
                peak == null && ReplayGain.isTrackPeakKey(upper) -> peak = ReplayGain.parsePeak(value)
            }
        }

        for (i in 0 until groups.length) {
            val format = groups.get(i).getFormat(0)
            val metadata = format.metadata ?: continue

            for (j in 0 until metadata.length()) {
                when (val entry = metadata.get(j)) {
                    // FLAC / OGG
                    is VorbisComment -> absorb(entry.key, entry.value)
                    // mp3 的 TXXX 自定义帧：description 才是真正的键名。
                    // m4a 的 ©lyr 也走这里，此时 description 为空、id 是 USLT。
                    is TextInformationFrame -> {
                        val key = entry.description ?: entry.id
                        absorb(key, entry.values.firstOrNull())
                    }
                    // mp3 的 USLT：Id3Decoder 不认识这个帧，只把帧体原样丢出来，自己解
                    is BinaryFrame -> if (entry.id == UsltFrame.FRAME_ID) {
                        absorb(UsltFrame.FRAME_ID, UsltFrame.parse(entry.data))
                    }
                    // 封面兜底，仅在 MediaMetadataRetriever 没拿到时用
                    is ApicFrame -> if (picture == null) picture = entry.pictureData
                    is PictureFrame -> if (picture == null) picture = entry.pictureData
                }
            }
        }

        val replayGain = ReplayGain(gainDb, peak).takeIf { !it.isEmpty }

        return track.copy(
            artwork = track.artwork ?: picture,
            embeddedLyrics = track.embeddedLyrics ?: lyrics,
            replayGain = track.replayGain ?: replayGain
        )
    }
}
