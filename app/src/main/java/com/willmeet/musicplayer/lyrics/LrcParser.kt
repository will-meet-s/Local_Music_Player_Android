package com.willmeet.musicplayer.lyrics

import com.willmeet.musicplayer.model.LyricLine
import kotlin.math.pow

/**
 * LRC 歌词格式解析器。
 *
 * 支持：
 * - `[mm:ss]`、`[mm:ss.xx]`、`[mm:ss.xxx]`、`[mm:ss:xx]`
 * - 一行多个时间戳（`[00:12.00][01:30.00]同一句副歌`）
 * - 忽略 `[ti:]` `[ar:]` `[al:]` `[by:]` 等元信息标签
 * - `[offset:N]` 校准，输入乱序时按时间排序
 */
object LrcParser {

    fun parse(content: String?): List<LyricLine> {
        if (content.isNullOrEmpty()) return emptyList()

        val parsed = mutableListOf<Pair<Double, String>>()
        var offsetMs = 0.0

        for (raw in content.split('\n', '\r')) {
            parseOffset(raw)?.let {
                offsetMs = it
                continue
            }

            val (stamps, text) = splitTimestamps(raw)
            if (stamps.isEmpty()) continue

            val trimmed = text.trim()
            stamps.forEach { parsed += it to trimmed }
        }

        // offset 为正表示歌词需要提前显示（LRC 规范），故从时间上减去
        val shift = offsetMs / 1000.0

        return parsed
            .map { (time, text) -> maxOf(0.0, time - shift) to text }
            .sortedBy { it.first }
            .mapIndexed { index, (time, text) -> LyricLine(index, time, text) }
    }

    /**
     * 从行首连续切出所有 `[...]` 时间戳，返回时间列表与剩余文本。
     *
     * 遇到第一个非时间戳的 `[...]`（例如 `[ti:标题]`）即停止，该行被视为无时间戳。
     */
    private fun splitTimestamps(line: String): Pair<List<Double>, String> {
        val times = mutableListOf<Double>()
        var rest = line

        while (true) {
            val scan = rest.trimStart(' ', '\t')
            if (!scan.startsWith('[')) break

            val close = scan.indexOf(']')
            if (close < 0) break

            val time = parseTimestamp(scan.substring(1, close)) ?: break

            times += time
            rest = scan.substring(close + 1)
        }

        return times to rest
    }

    /** 解析 `mm:ss`、`mm:ss.xx`、`mm:ss:xx` 形式的时间戳。 */
    private fun parseTimestamp(s: String): Double? {
        // 必须以数字开头，用来把 [00:12.34] 与 [ti:标题] 区分开
        if (s.isEmpty() || !s[0].isDigit()) return null

        val parts = s.split(':', '.')
        if (parts.size !in 2..3) return null

        val minutes = parts[0].toDoubleOrNull() ?: return null
        val seconds = parts[1].toDoubleOrNull() ?: return null
        if (seconds >= 60) return null

        var total = minutes * 60 + seconds

        if (parts.size == 3) {
            val frac = parts[2].toIntOrNull() ?: return null
            // 两位是厘秒，三位是毫秒
            total += frac / 10.0.pow(parts[2].length)
        }

        return total
    }

    /** 识别 `[offset:+/-N]` 标签，返回毫秒值。 */
    private fun parseOffset(line: String): Double? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("[offset:", ignoreCase = true) || !trimmed.endsWith("]")) return null
        return trimmed.substring(8, trimmed.length - 1).trim().toDoubleOrNull()
    }

    /** 二分查找 [time] 时刻应高亮的行索引；早于第一行时返回 -1。 */
    fun indexAt(time: Double, lines: List<LyricLine>): Int {
        if (lines.isEmpty() || time < lines[0].time) return -1

        var low = 0
        var high = lines.size - 1
        var result = 0

        while (low <= high) {
            val mid = (low + high) / 2
            if (lines[mid].time <= time) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }
}
