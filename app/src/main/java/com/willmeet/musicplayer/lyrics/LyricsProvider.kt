package com.willmeet.musicplayer.lyrics

import android.content.Context
import android.net.Uri
import com.willmeet.musicplayer.model.LyricLine
import com.willmeet.musicplayer.model.Track
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.nio.ByteBuffer

/**
 * 为曲目查找歌词。优先级：同目录同名 `.lrc` → 音频内嵌歌词 → 无。
 *
 * `.lrc` 的 URI 在扫描阶段就配好了（见 LibraryScanner）—— SAF 下没法由音频 URI
 * 推导出兄弟文件的 URI，只能扫描时按目录 + 文件名主干配对。
 */
object LyricsProvider {

    fun lyricsFor(context: Context, track: Track): List<LyricLine> {
        track.lrcUri?.let { uri ->
            readText(context, uri)?.let { text ->
                val lines = LrcParser.parse(text)
                if (lines.isNotEmpty()) return lines
            }
        }

        val embedded = track.embeddedLyrics
        if (!embedded.isNullOrBlank()) {
            val lines = LrcParser.parse(embedded)
            if (lines.isNotEmpty()) return lines

            // 内嵌歌词常常没有时间戳，此时逐行静态展示。
            // 时间设为 -1，UI 据此不做高亮滚动。
            return embedded.split('\n', '\r')
                .filter { it.isNotEmpty() }
                .mapIndexed { i, line -> LyricLine(i, -1.0, line) }
        }

        return emptyList()
    }

    private fun readText(context: Context, uri: String): String? = try {
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { decode(it.readBytes()) }
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        // 持久化授权可能已被系统回收
        null
    }

    /** UTF-8 失败时退 GB18030。中文歌词文件用 GBK 系编码的很常见。 */
    internal fun decode(data: ByteArray): String {
        strictDecode(data, Charsets.UTF_8)?.let { return it }

        runCatching { Charset.forName("GB18030") }
            .getOrNull()
            ?.let { strictDecode(data, it) }
            ?.let { return it }

        return String(data, Charsets.ISO_8859_1)
    }

    /**
     * 严格解码：遇到非法字节就失败，从而落到下一种编码。
     * 用宽容模式会把中文解成一串「」，看起来读成功了其实是乱码。
     */
    private fun strictDecode(data: ByteArray, charset: Charset): String? = try {
        val decoder: CharsetDecoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        decoder.decode(ByteBuffer.wrap(data)).toString()
    } catch (e: Exception) {
        null
    }
}
