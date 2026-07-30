package com.willmeet.musicplayer

import com.willmeet.musicplayer.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * 元数据补全后，UI 必须能看到变化。
 *
 * StateFlow 与 Compose 都靠 `equals` 判断「值变了没有」——Track 只比 uri 的话，
 * 换成带封面 / 歌词的新实例会被判成同一个值而整批丢弃，界面永远停在扫描时的空元数据。
 */
class TrackUpdateTest {

    private fun track(name: String) = Track(uri = "content://tree/$name", fileName = name)

    @Test
    fun `artwork makes a track unequal`() {
        val bare = track("a.mp3")
        assertNotEquals(bare, bare.copy(artwork = byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `embedded lyrics make a track unequal`() {
        val bare = track("a.mp3")
        assertNotEquals(bare, bare.copy(embeddedLyrics = "[00:01.00]行"))
    }

    @Test
    fun `identical artwork bytes stay equal`() {
        // 重扫时同一首歌被重新读出来，内容一样就不该判成变化 —— 否则列表白白重组
        val withArt = track("a.mp3").copy(artwork = byteArrayOf(1, 2, 3))
        val sameArt = track("a.mp3").copy(artwork = byteArrayOf(1, 2, 3))

        assertEquals(withArt, sameArt)
        assertEquals(withArt.hashCode(), sameArt.hashCode())
    }

    @Test
    fun `replacing one entry in a list flow is observable`() {
        val flow = MutableStateFlow(listOf(track("a.mp3"), track("b.mp3")))
        val loaded = flow.value[0].copy(
            artist = "歌手",
            artwork = byteArrayOf(9),
            embeddedLyrics = "歌词",
            metadataLoaded = true
        )

        flow.value = flow.value.toMutableList().also { it[0] = loaded }

        assertNotNull(flow.value[0].artwork)
        assertEquals("歌词", flow.value[0].embeddedLyrics)
    }
}
