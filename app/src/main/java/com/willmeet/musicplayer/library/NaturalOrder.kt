package com.willmeet.musicplayer.library

/**
 * 自然序字符串比较：把连续数字当成一个整体比大小，所以 `track2` 排在 `track10` 前面。
 */
object NaturalOrder : Comparator<String> {

    override fun compare(x: String?, y: String?): Int {
        if (x === y) return 0
        if (x == null) return -1
        if (y == null) return 1

        var i = 0
        var j = 0

        while (i < x.length && j < y.length) {
            if (x[i].isDigit() && y[j].isDigit()) {
                val xStart = i
                val yStart = j
                while (i < x.length && x[i].isDigit()) i++
                while (j < y.length && y[j].isDigit()) j++

                val result = compareNumbers(x.substring(xStart, i), y.substring(yStart, j))
                if (result != 0) return result
            } else {
                val result = x[i].lowercaseChar().compareTo(y[j].lowercaseChar())
                if (result != 0) return result
                i++
                j++
            }
        }

        // 前缀相同则短的在前
        val lengthCompare = (x.length - i).compareTo(y.length - j)
        if (lengthCompare != 0) return lengthCompare

        // 完全等价时用序数比较兜底，保证排序稳定
        return x.compareTo(y)
    }

    /**
     * 比较两段纯数字。不转成 Long —— 曲目号可能长到溢出，
     * 按「去掉前导零后的长度，再逐位比较」判断。
     */
    private fun compareNumbers(a: String, b: String): Int {
        val trimmedA = a.trimStart('0')
        val trimmedB = b.trimStart('0')

        if (trimmedA.length != trimmedB.length) return trimmedA.length.compareTo(trimmedB.length)
        return trimmedA.compareTo(trimmedB)
    }
}
