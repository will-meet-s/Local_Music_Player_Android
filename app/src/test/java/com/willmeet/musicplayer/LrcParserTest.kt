package com.willmeet.musicplayer

import com.willmeet.musicplayer.lyrics.LrcParser
import com.willmeet.musicplayer.model.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun `parses basic timestamps`() {
        val lines = LrcParser.parse("[00:12.34]第一行\n[01:05.00]第二行")

        assertEquals(2, lines.size)
        assertEquals(12.34, lines[0].time, 0.001)
        assertEquals("第一行", lines[0].text)
        assertEquals(65.0, lines[1].time, 0.001)
    }

    @Test
    fun `parses timestamp without fraction`() {
        val lines = LrcParser.parse("[02:03]文本")
        assertEquals(1, lines.size)
        assertEquals(123.0, lines[0].time, 0.001)
    }

    @Test
    fun `parses millisecond precision`() {
        assertEquals(1.5, LrcParser.parse("[00:01.500]文本")[0].time, 0.001)
    }

    @Test
    fun `multiple timestamps on one line`() {
        val lines = LrcParser.parse("[00:10.00][01:10.00]副歌")

        assertEquals(2, lines.size)
        assertEquals(listOf("副歌", "副歌"), lines.map { it.text })
        assertEquals(10.0, lines[0].time, 0.001)
        assertEquals(70.0, lines[1].time, 0.001)
    }

    @Test
    fun `ignores metadata tags`() {
        val lines = LrcParser.parse("[ti:歌名]\n[ar:歌手]\n[al:专辑]\n[by:某人]\n[00:01.00]正文")

        assertEquals(1, lines.size)
        assertEquals("正文", lines[0].text)
    }

    @Test
    fun `sorts out of order input`() {
        val lines = LrcParser.parse("[00:30.00]后\n[00:10.00]前")

        assertEquals(listOf("前", "后"), lines.map { it.text })
        assertEquals(listOf(0, 1), lines.map { it.index })
    }

    @Test
    fun `applies offset tag`() {
        // offset 为正表示歌词提前显示，时间应被减小
        assertEquals(9.5, LrcParser.parse("[offset:+500]\n[00:10.00]文本")[0].time, 0.001)
    }

    @Test
    fun `offset never produces negative time`() {
        assertEquals(0.0, LrcParser.parse("[offset:5000]\n[00:01.00]文本")[0].time, 0.001)
    }

    @Test
    fun `rejects input without valid timestamps`() {
        listOf(
            "",
            "这是一段没有时间戳的纯文本\n第二行",
            "[not-a-time]文本",
            "[00:99.00]文本"   // 秒数 >= 60 不是合法时间戳
        ).forEach { input ->
            assertTrue("应被拒绝：$input", LrcParser.parse(input).isEmpty())
        }
    }

    @Test
    fun `keeps empty lyric lines`() {
        // 间奏留白行应该保留，否则滚动位置会错
        val lines = LrcParser.parse("[00:01.00]\n[00:05.00]有词")

        assertEquals(2, lines.size)
        assertEquals("", lines[0].text)
    }

    @Test
    fun `index lookup`() {
        val lines = listOf(
            LyricLine(0, 0.0, "a"),
            LyricLine(1, 10.0, "b"),
            LyricLine(2, 20.0, "c")
        )

        assertEquals(0, LrcParser.indexAt(0.0, lines))
        assertEquals(0, LrcParser.indexAt(9.99, lines))
        assertEquals(1, LrcParser.indexAt(10.0, lines))
        assertEquals(1, LrcParser.indexAt(15.0, lines))
        assertEquals(2, LrcParser.indexAt(1000.0, lines))
    }

    @Test
    fun `index before first line is minus one`() {
        val lines = listOf(LyricLine(0, 5.0, "a"))

        assertEquals(-1, LrcParser.indexAt(0.0, lines))
        assertEquals(-1, LrcParser.indexAt(4.9, lines))
    }

    @Test
    fun `index on empty lyrics is minus one`() {
        assertEquals(-1, LrcParser.indexAt(10.0, emptyList()))
    }
}
