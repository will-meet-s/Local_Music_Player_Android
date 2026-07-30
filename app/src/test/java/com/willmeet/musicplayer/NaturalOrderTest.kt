package com.willmeet.musicplayer

import com.willmeet.musicplayer.library.NaturalOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalOrderTest {

    private fun sign(a: String?, b: String?) = NaturalOrder.compare(a, b).let {
        when {
            it < 0 -> -1
            it > 0 -> 1
            else -> 0
        }
    }

    @Test
    fun `compares numbers by value not lexically`() {
        assertEquals(-1, sign("a1", "a2"))
        assertEquals(-1, sign("a2", "a10"))
        assertEquals(1, sign("a10", "a2"))
    }

    @Test
    fun `leading zeros do not change value`() {
        // 前导零不影响数值大小
        assertEquals(-1, sign("a01", "a2"))
        assertEquals(1, sign("a010", "a9"))

        // 数值相等时不返回 0，而是由实现末尾的序数兜底定序 ——
        // 这样比较器保持全序，不同字符串永不相等，排序结果才确定
        assertEquals(-1, sign("a01", "a1"))
        assertEquals(-1, sign("track007", "track7"))
    }

    @Test
    fun `shorter prefix sorts first`() {
        assertEquals(-1, sign("a", "ab"))
        assertEquals(0, sign("abc", "abc"))
    }

    @Test
    fun `ignores case`() {
        // 大小写不参与排序判断：Track2 排在 track10 前面靠的是数值比较
        assertEquals(-1, sign("Track2", "track10"))
        assertEquals(1, sign("TRACK10", "track2"))

        // 仅大小写不同的两串在自然序上等价，同样由序数兜底定序（大写在前）
        assertEquals(-1, sign("Track1", "track1"))
    }

    @Test
    fun `handles very long numbers without overflow`() {
        // 直接 toLong 会溢出，所以实现是按位比较的
        assertTrue(
            NaturalOrder.compare(
                "track99999999999999999999.mp3",
                "track99999999999999999998.mp3"
            ) > 0
        )
    }

    @Test
    fun `nulls sort first`() {
        assertEquals(-1, sign(null, "a"))
        assertEquals(1, sign("a", null))
        assertEquals(0, sign(null, null))
    }

    @Test
    fun `sorts a realistic track listing`() {
        val input = listOf(
            "Album/10 - Ten.mp3",
            "Album/2 - Two.mp3",
            "Album/1 - One.mp3",
            "Album/20 - Twenty.mp3"
        )

        assertEquals(
            listOf(
                "Album/1 - One.mp3",
                "Album/2 - Two.mp3",
                "Album/10 - Ten.mp3",
                "Album/20 - Twenty.mp3"
            ),
            input.sortedWith(NaturalOrder)
        )
    }
}
