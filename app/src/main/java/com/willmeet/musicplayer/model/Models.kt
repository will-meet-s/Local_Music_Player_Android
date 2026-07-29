package com.willmeet.musicplayer.model

import com.willmeet.musicplayer.playback.ReplayGain

/**
 * 一首本地音乐曲目。
 *
 * 用 SAF 的 document URI 作唯一标识 —— 安卓从 10 起限制直接路径访问，
 * URI 才是稳定可用的句柄，重启后凭持久化授权仍然有效。
 */
data class Track(
    val uri: String,
    /** 文件名（含扩展名），元数据缺失时降级为标题。 */
    val fileName: String,
    /** 同目录同名 `.lrc` 的 URI，扫描时一并解析出来。 */
    val lrcUri: String? = null,
    /** 所属目录的 document id，用于配对歌词文件与自然序排序。 */
    val parentId: String = "",
    val title: String = fileName.substringBeforeLast('.'),
    val artist: String? = null,
    val album: String? = null,
    /** 毫秒。未知时为 0。 */
    val durationMs: Long = 0,
    val artwork: ByteArray? = null,
    /** 音频文件内嵌的歌词文本（未解析）。 */
    val embeddedLyrics: String? = null,
    /** 音量归一化信息。文件没打标签时为 null。 */
    val replayGain: ReplayGain? = null,
    /** 元数据是否已异步加载完成。重扫时用它跳过已读条目。 */
    val metadataLoaded: Boolean = false
) {
    /** 副标题：「艺术家 — 专辑」，缺失部分自动省略。 */
    val subtitle: String
        get() = listOfNotNull(artist, album).filter { it.isNotBlank() }.joinToString(" — ")

    val durationSeconds: Double get() = durationMs / 1000.0

    // data class 默认会对 ByteArray 比引用，导致同一首歌被判成两首。
    // uri 就是身份，其余字段都是可变的元数据。
    override fun equals(other: Any?) = other is Track && other.uri == uri
    override fun hashCode() = uri.hashCode()
}

/**
 * 一行带时间戳的歌词。
 *
 * @param time 该行开始时间，单位秒。负值表示这份歌词没有时间戳。
 */
data class LyricLine(val index: Int, val time: Double, val text: String)

/** 播放顺序模式。 */
enum class PlayMode(val displayName: String) {
    /** 顺序播放：播到列表末尾自动停止。 */
    SEQUENTIAL("顺序播放"),

    /** 列表循环：播到末尾回到开头。 */
    REPEAT_ALL("列表循环"),

    /** 单曲循环。 */
    REPEAT_ONE("单曲循环"),

    /** 随机播放。 */
    SHUFFLE("随机播放");

    fun next(): PlayMode = entries[(ordinal + 1) % entries.size]
}

/** 「正在播放」区的展示模式。 */
enum class NowPlayingLayout(val displayName: String) {
    ARTWORK_AND_LYRICS("封面 + 歌词"),
    ARTWORK_ONLY("只看封面"),
    LYRICS_ONLY("只看歌词");

    fun next(): NowPlayingLayout = entries[(ordinal + 1) % entries.size]

    val showsArtwork: Boolean get() = this != LYRICS_ONLY
    val showsLyrics: Boolean get() = this != ARTWORK_ONLY
}

/** 曲目列表的排序维度。 */
enum class TrackSortOrder(val displayName: String) {
    /** 目录 + 文件名自然序 —— 扫描出来的原始顺序，专辑目录结构在此顺序下最直观。 */
    FILE_ORDER("文件顺序"),
    TITLE("歌曲名"),
    ARTIST("歌手名")
}
