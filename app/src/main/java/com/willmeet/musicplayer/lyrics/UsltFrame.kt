package com.willmeet.musicplayer.lyrics

import java.nio.charset.Charset

/**
 * 解析 ID3 的 USLT（Unsynchronised lyrics）帧体。
 *
 * 为什么要自己解：Media3 的 `Id3Decoder` 只认识 T\*\*\*、W\*\*\*、APIC、COMM、PRIV 等帧，
 * USLT 落到「未知帧」分支变成 `BinaryFrame`，原始字节原样交给调用方 —— 也就是说
 * **mp3 的内嵌歌词必须自己从字节里取**，否则永远读不到。
 *
 * 帧体布局（ID3v2.3 / v2.4 一致）：
 * ```
 * 文本编码   1 字节
 * 语言       3 字节（如 "eng"）
 * 内容描述符 以 NUL 结尾（UTF-16 时是两个 0 字节）
 * 歌词正文   剩余全部
 * ```
 */
object UsltFrame {

    const val FRAME_ID = "USLT"

    fun parse(data: ByteArray): String? {
        // 至少要够「编码 + 语言 + 空描述符」
        if (data.size < 5) return null

        val charset = charsetFor(data[0]) ?: return null

        // UTF-16 的字符串终止符是两个字节
        val step = if (charset == Charsets.UTF_16 || charset == Charsets.UTF_16BE) 2 else 1

        var pos = 4
        while (pos + step <= data.size) {
            val isTerminator = (0 until step).all { data[pos + it] == 0.toByte() }
            pos += step
            if (isTerminator) break
        }

        // 描述符没有终止符，或者终止符之后什么都没有
        if (pos >= data.size) return null

        // 有些标签工具会给正文补上终止符或 BOM，一并去掉
        return String(data, pos, data.size - pos, charset)
            .trim { it == '\u0000' || it == '\uFEFF' || it.isWhitespace() }
            .takeIf { it.isNotEmpty() }
    }

    private fun charsetFor(encoding: Byte): Charset? = when (encoding.toInt()) {
        0 -> Charsets.ISO_8859_1
        1 -> Charsets.UTF_16 // 带 BOM，交给解码器判字节序
        2 -> Charsets.UTF_16BE
        3 -> Charsets.UTF_8
        else -> null
    }
}
