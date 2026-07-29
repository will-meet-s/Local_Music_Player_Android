package com.willmeet.musicplayer.playback

import kotlin.math.pow

/**
 * ReplayGain 音量归一化信息。
 *
 * 不同来源的文件响度能差 10dB 以上，一首听着刚好、下一首震耳朵。ReplayGain 是事实标准：
 * 打标签的软件预先算好该曲相对参考响度的增益，播放器照着补偿即可。
 *
 * 只处理**曲目级**（TRACK）而不是专辑级（ALBUM）增益 —— 随机播放是常态，
 * 专辑级增益只在整张连听时才正确。
 */
data class ReplayGain(
    /** 相对参考响度的增益，单位 dB。负值表示这首偏响、需要衰减。 */
    val trackGainDb: Double? = null,
    /** 峰值采样电平（1.0 = 满刻度）。用来防止补偿后削波。 */
    val trackPeak: Double? = null
) {
    val isEmpty: Boolean get() = trackGainDb == null && trackPeak == null

    /**
     * 换算成线性增益系数（1 表示不做处理）。
     *
     * 已知峰值时会保证补偿后不削波：`peak × factor ≤ 1`。
     *
     * @param preampDb 额外的统一前置增益。ReplayGain 参考响度偏保守，多数人会加几 dB 补回来。
     */
    fun linearGain(preampDb: Double = 0.0): Float {
        val gain = trackGainDb ?: return 1f

        var factor = 10.0.pow((gain + preampDb) / 20.0)

        val peak = trackPeak
        if (peak != null && peak > 0 && peak * factor > 1) factor = 1 / peak

        return factor.toFloat().coerceIn(MIN_FACTOR, MAX_FACTOR)
    }

    companion object {
        /** 增益系数的安全上下限。标签写错时不至于把耳朵震坏或彻底静音。 */
        const val MIN_FACTOR = 0.05f
        const val MAX_FACTOR = 4.0f

        /** 解析增益字段，例如 `-6.54 dB`、`+2.10dB`、`-3`。 */
        fun parseGain(raw: String?): Double? {
            if (raw.isNullOrBlank()) return null
            return raw.replace("dB", "", ignoreCase = true).trim().toDoubleOrNull()
        }

        /** 解析峰值字段，例如 `0.988525`。超出合理范围的值视为无效。 */
        fun parsePeak(raw: String?): Double? {
            val value = raw?.trim()?.toDoubleOrNull() ?: return null
            return if (value > 0 && value <= 8) value else null
        }

        fun isTrackGainKey(key: String) = key.contains("REPLAYGAIN_TRACK_GAIN", ignoreCase = true)

        fun isTrackPeakKey(key: String) = key.contains("REPLAYGAIN_TRACK_PEAK", ignoreCase = true)
    }
}
