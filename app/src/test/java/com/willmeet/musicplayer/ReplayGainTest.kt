package com.willmeet.musicplayer

import com.willmeet.musicplayer.playback.ReplayGain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayGainTest {

    @Test
    fun `parses gain with unit`() {
        assertEquals(-6.54, ReplayGain.parseGain("-6.54 dB")!!, 0.0001)
        assertEquals(2.10, ReplayGain.parseGain("+2.10dB")!!, 0.0001)
        assertEquals(-3.0, ReplayGain.parseGain("-3")!!, 0.0001)
        assertEquals(0.0, ReplayGain.parseGain("  0.00 DB  ")!!, 0.0001)
    }

    @Test
    fun `rejects garbage gain`() {
        listOf("", "不是数字", "dB", null).forEach { assertNull(ReplayGain.parseGain(it)) }
    }

    @Test
    fun `parses peak`() {
        assertEquals(0.988525, ReplayGain.parsePeak("0.988525")!!, 0.000001)
        assertEquals(1.0, ReplayGain.parsePeak(" 1.0 ")!!, 0.0001)
    }

    @Test
    fun `rejects out of range peak`() {
        listOf("0", "-0.5", "99", "abc").forEach { assertNull(ReplayGain.parsePeak(it)) }
    }

    @Test
    fun `key matching is case insensitive`() {
        assertTrue(ReplayGain.isTrackGainKey("REPLAYGAIN_TRACK_GAIN"))
        assertTrue(ReplayGain.isTrackGainKey("replaygain_track_gain"))
        assertTrue(ReplayGain.isTrackPeakKey("ReplayGain_Track_Peak"))

        // 专辑级增益只在整张连听时才正确，随机播放是常态，所以不采用
        assertFalse(ReplayGain.isTrackGainKey("REPLAYGAIN_ALBUM_GAIN"))
        assertFalse(ReplayGain.isTrackGainKey("TITLE"))
    }

    @Test
    fun `no gain means unchanged`() {
        assertEquals(1f, ReplayGain().linearGain(), 0.0001f)
        assertEquals(1f, ReplayGain(trackPeak = 0.9).linearGain(), 0.0001f)
    }

    @Test
    fun `negative gain attenuates`() {
        // -6.02 dB 约等于减半
        assertEquals(0.5f, ReplayGain(-6.0206).linearGain(), 0.001f)
    }

    @Test
    fun `positive gain boosts`() {
        // +6.02 dB 约等于翻倍，峰值未知时不设限
        assertEquals(2.0f, ReplayGain(6.0206).linearGain(), 0.001f)
    }

    @Test
    fun `peak prevents clipping`() {
        // 峰值 0.8 时最多只能放大到 1.25 倍，否则削波
        assertEquals(1.25f, ReplayGain(12.0, 0.8).linearGain(), 0.001f)
    }

    @Test
    fun `peak does not interfere when no clipping`() {
        // 衰减不可能削波，峰值不应改变结果
        assertEquals(0.5f, ReplayGain(-6.0206, 0.99).linearGain(), 0.001f)
    }

    @Test
    fun `preamp is applied`() {
        assertEquals(2.0f, ReplayGain(0.0).linearGain(preampDb = 6.0206), 0.001f)
    }

    @Test
    fun `factor is clamped to safe range`() {
        // 标签写错成极端值时不应炸耳朵，也不应彻底静音
        assertEquals(ReplayGain.MAX_FACTOR, ReplayGain(60.0).linearGain(), 0.001f)
        assertEquals(ReplayGain.MIN_FACTOR, ReplayGain(-60.0).linearGain(), 0.001f)
    }

    @Test
    fun `isEmpty reflects content`() {
        assertTrue(ReplayGain().isEmpty)
        assertFalse(ReplayGain(-3.0).isEmpty)
        assertFalse(ReplayGain(trackPeak = 0.9).isEmpty)
    }
}
