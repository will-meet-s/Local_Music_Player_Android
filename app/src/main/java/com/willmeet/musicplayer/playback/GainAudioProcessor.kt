package com.willmeet.musicplayer.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 在音频管线上施加 ReplayGain 增益。
 *
 * 为什么不直接用 `player.volume`：那是用户的音量旋钮，取值上限还是 1，
 * 没法为偏轻的曲目**提升**音量。放在处理器里则可以大于 1，而且与音量旋钮解耦。
 *
 * 增益值由 [gain] 随曲目切换更新。有一点必须清楚：ExoPlayer 会提前缓冲，
 * 无缝切歌时这里的切换点与实际听到的换曲点可能差几十到几百毫秒 ——
 * 相邻两首增益差很大时，交界处会有一瞬间用错增益。
 */
@OptIn(UnstableApi::class)
class GainAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var gain: Float = 1f
        set(value) {
            field = value.coerceIn(ReplayGain.MIN_FACTOR, ReplayGain.MAX_FACTOR)
        }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // 只处理这两种 PCM 编码；其余原样透传（isActive 会返回 false）
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    /** 增益为 1 时整个处理器旁路掉，省去无谓的逐样本乘法。 */
    override fun isActive(): Boolean = super.isActive() && gain != 1f

    override fun queueInput(inputBuffer: ByteBuffer) {
        val factor = gain
        val limit = inputBuffer.limit()
        val output = replaceOutputBuffer(limit - inputBuffer.position())

        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> {
                while (inputBuffer.hasRemaining()) {
                    val sample = inputBuffer.short.toFloat() * factor
                    // 必须夹在 16 位范围内，否则溢出会变成刺耳的爆音
                    output.putShort(sample.coerceIn(-32768f, 32767f).toInt().toShort())
                }
            }

            C.ENCODING_PCM_FLOAT -> {
                while (inputBuffer.hasRemaining()) {
                    output.putFloat((inputBuffer.float * factor).coerceIn(-1f, 1f))
                }
            }
        }

        inputBuffer.position(limit)
        output.flip()
    }

    init {
        // BaseAudioProcessor 的输出缓冲默认按输入字节序，显式声明避免平台差异
        ByteOrder.nativeOrder()
    }
}
