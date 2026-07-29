package com.willmeet.musicplayer

import com.willmeet.musicplayer.library.TrackFilter
import com.willmeet.musicplayer.model.Track
import com.willmeet.musicplayer.model.TrackSortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackFilterTest {

    private fun track(
        name: String,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        parent: String = "dir"
    ) = Track(
        uri = "content://tree/$parent/$name",
        fileName = name,
        parentId = parent,
        title = title ?: name.substringBeforeLast('.'),
        artist = artist,
        album = album
    )

    // 搜索

    @Test
    fun `empty search returns everything`() {
        val tracks = listOf(track("a.mp3"), track("b.mp3"))

        assertEquals(2, TrackFilter.filter(tracks, "").size)
        assertEquals(2, TrackFilter.filter(tracks, "   ").size)
    }

    @Test
    fun `search matches title`() {
        val tracks = listOf(track("1.mp3", "晴天"), track("2.mp3", "雨天"))
        assertEquals(listOf("晴天"), TrackFilter.filter(tracks, "晴").map { it.title })
    }

    @Test
    fun `search matches artist and album`() {
        val tracks = listOf(
            track("1.mp3", "A", artist = "周杰伦"),
            track("2.mp3", "B", album = "范特西"),
            track("3.mp3", "C")
        )

        assertEquals(listOf("A"), TrackFilter.filter(tracks, "周杰伦").map { it.title })
        assertEquals(listOf("B"), TrackFilter.filter(tracks, "范特西").map { it.title })
    }

    @Test
    fun `search is case insensitive`() {
        val tracks = listOf(track("1.mp3", "Hello World"))

        assertEquals(1, TrackFilter.filter(tracks, "hello").size)
        assertEquals(1, TrackFilter.filter(tracks, "WORLD").size)
    }

    @Test
    fun `search is diacritic insensitive`() {
        val tracks = listOf(track("1.mp3", "Café Bar"))
        assertEquals(1, TrackFilter.filter(tracks, "cafe").size)
    }

    @Test
    fun `search keyword is trimmed`() {
        assertEquals(1, TrackFilter.filter(listOf(track("1.mp3", "晴天")), "  晴天  ").size)
    }

    @Test
    fun `no match returns empty`() {
        assertTrue(TrackFilter.filter(listOf(track("1.mp3", "晴天")), "不存在").isEmpty())
    }

    // 排序

    @Test
    fun `file order uses natural sort`() {
        val tracks = listOf(track("track10.mp3"), track("track2.mp3"), track("track1.mp3"))

        val sorted = TrackFilter.sort(tracks, TrackSortOrder.FILE_ORDER, ascending = true)

        assertEquals(listOf("track1.mp3", "track2.mp3", "track10.mp3"), sorted.map { it.fileName })
    }

    @Test
    fun `title sort ascending and descending`() {
        val tracks = listOf(
            track("1.mp3", "Banana"),
            track("2.mp3", "apple"),
            track("3.mp3", "Cherry")
        )

        assertEquals(
            listOf("apple", "Banana", "Cherry"),
            TrackFilter.sort(tracks, TrackSortOrder.TITLE, true).map { it.title }
        )
        assertEquals(
            listOf("Cherry", "Banana", "apple"),
            TrackFilter.sort(tracks, TrackSortOrder.TITLE, false).map { it.title }
        )
    }

    @Test
    fun `title sort breaks ties by uri for stability`() {
        val tracks = listOf(track("z.mp3", "同名"), track("a.mp3", "同名"))

        listOf(true, false).forEach { ascending ->
            val sorted = TrackFilter.sort(tracks, TrackSortOrder.TITLE, ascending)
            assertEquals(
                "同名曲目应始终按 URI 定序（ascending=$ascending）",
                listOf("a.mp3", "z.mp3"),
                sorted.map { it.fileName }
            )
        }
    }

    @Test
    fun `artist sort`() {
        val tracks = listOf(
            track("1.mp3", "A", artist = "Beyond"),
            track("2.mp3", "B", artist = "Air"),
            track("3.mp3", "C", artist = "Coldplay")
        )

        assertEquals(
            listOf("Air", "Beyond", "Coldplay"),
            TrackFilter.sort(tracks, TrackSortOrder.ARTIST, true).map { it.artist }
        )
        assertEquals(
            listOf("Coldplay", "Beyond", "Air"),
            TrackFilter.sort(tracks, TrackSortOrder.ARTIST, false).map { it.artist }
        )
    }

    @Test
    fun `tracks without artist always sort last`() {
        val tracks = listOf(
            track("1.mp3", "无歌手"),
            track("2.mp3", "有歌手", artist = "Air"),
            track("3.mp3", "空白歌手", artist = "   ")
        )

        listOf(true, false).forEach { ascending ->
            val sorted = TrackFilter.sort(tracks, TrackSortOrder.ARTIST, ascending)

            assertEquals("Air", sorted.first().artist)
            assertTrue(
                "缺失歌手的应垫底（ascending=$ascending）",
                sorted.drop(1).all { it.artist.isNullOrBlank() }
            )
        }
    }

    @Test
    fun `same artist sorts by title`() {
        val tracks = listOf(
            track("1.mp3", "Beta", artist = "Same"),
            track("2.mp3", "Alpha", artist = "Same")
        )

        assertEquals(
            listOf("Alpha", "Beta"),
            TrackFilter.sort(tracks, TrackSortOrder.ARTIST, true).map { it.title }
        )
    }

    @Test
    fun `apply filters then sorts`() {
        // 标题用 ASCII —— 中文排序结果取决于系统 locale，断言具体顺序会让测试飘
        val tracks = listOf(
            track("1.mp3", "Zulu", artist = "周杰伦"),
            track("2.mp3", "Alpha", artist = "周杰伦"),
            track("3.mp3", "Mike", artist = "Beyond")
        )

        val result = TrackFilter.apply(tracks, "周杰伦", TrackSortOrder.TITLE, true)

        assertEquals(2, result.size)
        assertEquals(listOf("Alpha", "Zulu"), result.map { it.title })
    }

    @Test
    fun `apply on empty library`() {
        assertTrue(TrackFilter.apply(emptyList(), "x", TrackSortOrder.TITLE, true).isEmpty())
    }
}
