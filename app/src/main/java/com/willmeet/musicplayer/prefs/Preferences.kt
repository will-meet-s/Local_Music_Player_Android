package com.willmeet.musicplayer.prefs

import android.content.Context
import androidx.core.content.edit
import com.willmeet.musicplayer.model.NowPlayingLayout
import com.willmeet.musicplayer.model.PlayMode
import com.willmeet.musicplayer.model.TrackSortOrder

/** 用户偏好。项少且都是标量，SharedPreferences 足够，不必上 DataStore。 */
class Preferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("player_prefs", Context.MODE_PRIVATE)

    /** 上次授权的目录 tree URI。 */
    var treeUri: String?
        get() = prefs.getString(KEY_TREE_URI, null)
        set(value) = prefs.edit { putString(KEY_TREE_URI, value) }

    var playMode: PlayMode
        get() = enumOf(KEY_PLAY_MODE, PlayMode.SEQUENTIAL)
        set(value) = prefs.edit { putString(KEY_PLAY_MODE, value.name) }

    var volume: Float
        get() = prefs.getFloat(KEY_VOLUME, 1f).coerceIn(0f, 1f)
        set(value) = prefs.edit { putFloat(KEY_VOLUME, value.coerceIn(0f, 1f)) }

    var sortOrder: TrackSortOrder
        get() = enumOf(KEY_SORT_ORDER, TrackSortOrder.FILE_ORDER)
        set(value) = prefs.edit { putString(KEY_SORT_ORDER, value.name) }

    var sortAscending: Boolean
        get() = prefs.getBoolean(KEY_SORT_ASC, true)
        set(value) = prefs.edit { putBoolean(KEY_SORT_ASC, value) }

    var nowPlayingLayout: NowPlayingLayout
        get() = enumOf(KEY_LAYOUT, NowPlayingLayout.ARTWORK_AND_LYRICS)
        set(value) = prefs.edit { putString(KEY_LAYOUT, value.name) }

    var backgroundOpacity: Float
        get() = prefs.getFloat(KEY_BG_OPACITY, 1f).coerceIn(MIN_BACKGROUND_OPACITY, 1f)
        set(value) = prefs.edit {
            putFloat(KEY_BG_OPACITY, value.coerceIn(MIN_BACKGROUND_OPACITY, 1f))
        }

    /** 默认开启：有标签就用，没标签的文件本来也不受影响。 */
    var replayGainEnabled: Boolean
        get() = prefs.getBoolean(KEY_REPLAY_GAIN, true)
        set(value) = prefs.edit { putBoolean(KEY_REPLAY_GAIN, value) }

    private inline fun <reified T : Enum<T>> enumOf(key: String, fallback: T): T {
        val raw = prefs.getString(key, null) ?: return fallback
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
    }

    companion object {
        /** 背景不透明度下限。再低文字就浮在壁纸上没法看了。 */
        const val MIN_BACKGROUND_OPACITY = 0.2f

        private const val KEY_TREE_URI = "tree_uri"
        private const val KEY_PLAY_MODE = "play_mode"
        private const val KEY_VOLUME = "volume"
        private const val KEY_SORT_ORDER = "sort_order"
        private const val KEY_SORT_ASC = "sort_ascending"
        private const val KEY_LAYOUT = "now_playing_layout"
        private const val KEY_BG_OPACITY = "background_opacity"
        private const val KEY_REPLAY_GAIN = "replay_gain"
    }
}
