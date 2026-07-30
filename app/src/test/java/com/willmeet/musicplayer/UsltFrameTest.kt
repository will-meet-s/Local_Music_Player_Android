package com.willmeet.musicplayer

import com.willmeet.musicplayer.lyrics.UsltFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** USLT 帧体解析。构造字节的方式与 mp3 标签工具写出来的一致。 */
class UsltFrameTest {

    /** @param encoding 0=Latin-1, 1=UTF-16(带 BOM), 2=UTF-16BE, 3=UTF-8 */
    private fun frame(encoding: Int, descriptor: String, lyrics: String): ByteArray {
        val charset = when (encoding) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        val terminator = if (encoding == 1 || encoding == 2) byteArrayOf(0, 0) else byteArrayOf(0)

        return byteArrayOf(encoding.toByte()) +
            "eng".toByteArray(Charsets.ISO_8859_1) +
            descriptor.toByteArray(charset) +
            terminator +
            lyrics.toByteArray(charset)
    }

    @Test
    fun `utf-8 lyrics with empty descriptor`() {
        assertEquals("[00:01.00]第一行", UsltFrame.parse(frame(3, "", "[00:01.00]第一行")))
    }

    @Test
    fun `latin-1 lyrics after a descriptor`() {
        assertEquals("line one", UsltFrame.parse(frame(0, "desc", "line one")))
    }

    @Test
    fun `utf-16 with bom`() {
        assertEquals("中文歌词", UsltFrame.parse(frame(1, "", "中文歌词")))
    }

    @Test
    fun `utf-16be without bom`() {
        assertEquals("中文歌词", UsltFrame.parse(frame(2, "描述", "中文歌词")))
    }

    @Test
    fun `multi-line lyrics keep their breaks`() {
        assertEquals("一\n二", UsltFrame.parse(frame(3, "", "一\n二")))
    }

    @Test
    fun `trailing nul is dropped`() {
        // 有些标签工具会给正文也补一个终止符
        assertEquals("行", UsltFrame.parse(frame(3, "", "行") + byteArrayOf(0)))
    }

    @Test
    fun `descriptor only means no lyrics`() {
        assertNull(UsltFrame.parse(frame(3, "desc", "")))
    }

    @Test
    fun `truncated frame is rejected`() {
        assertNull(UsltFrame.parse(byteArrayOf(3, 'e'.code.toByte())))
    }

    @Test
    fun `unknown encoding is rejected`() {
        assertNull(UsltFrame.parse(byteArrayOf(9, 0, 0, 0, 0, 65)))
    }
}
