package com.willmeet.musicplayer.library

import com.willmeet.musicplayer.model.Track
import com.willmeet.musicplayer.model.TrackSortOrder
import java.text.Normalizer

/** 对曲目列表做搜索过滤 + 排序。纯函数，不碰任何状态，因此可完整单测。 */
object TrackFilter {

    fun apply(
        tracks: List<Track>,
        search: String,
        sort: TrackSortOrder,
        ascending: Boolean
    ): List<Track> = sort(filter(tracks, search), sort, ascending)

    /** 匹配标题 / 歌手 / 专辑，忽略大小写与音调符号。空白关键词表示不过滤。 */
    fun filter(tracks: List<Track>, search: String): List<Track> {
        val keyword = search.trim()
        if (keyword.isEmpty()) return tracks

        val needle = fold(keyword)
        return tracks.filter { track ->
            matches(track.title, needle) || matches(track.artist, needle) || matches(track.album, needle)
        }
    }

    private fun matches(text: String?, foldedKeyword: String): Boolean {
        if (text.isNullOrEmpty()) return false
        return fold(text).contains(foldedKeyword)
    }

    /**
     * 归一化：小写 + 去掉音调符号，让 `cafe` 能搜到 `Café`。
     *
     * NFD 会把 `é` 拆成 `e` + 组合重音符，再去掉 Mn 类字符即可。中文不受影响。
     */
    private fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }

    fun sort(tracks: List<Track>, order: TrackSortOrder, ascending: Boolean): List<Track> {
        val direction = if (ascending) 1 else -1

        val comparator = when (order) {
            TrackSortOrder.FILE_ORDER -> Comparator<Track> { a, b ->
                direction * NaturalOrder.compare("${a.parentId}/${a.fileName}", "${b.parentId}/${b.fileName}")
            }

            TrackSortOrder.TITLE -> Comparator { a, b ->
                val byTitle = NaturalOrder.compare(a.title, b.title)
                if (byTitle != 0) direction * byTitle
                // 同名歌曲按 URI 定序，保证结果稳定；方向切换也不变
                else NaturalOrder.compare(a.uri, b.uri)
            }

            TrackSortOrder.ARTIST -> Comparator { a, b ->
                val left = a.artist?.trim().orEmpty()
                val right = b.artist?.trim().orEmpty()

                // 没有歌手信息的始终垫底，正序倒序都一样 —— 否则倒序时
                // 一堆「未知歌手」会顶到最前面，没有意义
                when {
                    left.isEmpty() && right.isNotEmpty() -> 1
                    left.isNotEmpty() && right.isEmpty() -> -1
                    else -> {
                        val byArtist = NaturalOrder.compare(left, right)
                        if (byArtist != 0) direction * byArtist
                        else {
                            val byTitle = NaturalOrder.compare(a.title, b.title)
                            if (byTitle != 0) byTitle else NaturalOrder.compare(a.uri, b.uri)
                        }
                    }
                }
            }
        }

        return tracks.sortedWith(comparator)
    }
}
