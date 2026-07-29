package com.willmeet.musicplayer.ui

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.willmeet.musicplayer.library.LibraryScanner
import com.willmeet.musicplayer.library.MetadataLoader
import com.willmeet.musicplayer.library.TrackFilter
import com.willmeet.musicplayer.lyrics.LrcParser
import com.willmeet.musicplayer.lyrics.LyricsProvider
import com.willmeet.musicplayer.model.LyricLine
import com.willmeet.musicplayer.model.NowPlayingLayout
import com.willmeet.musicplayer.model.PlayMode
import com.willmeet.musicplayer.model.Track
import com.willmeet.musicplayer.model.TrackSortOrder
import com.willmeet.musicplayer.playback.GainProcessorHolder
import com.willmeet.musicplayer.playback.PlaybackService
import com.willmeet.musicplayer.prefs.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI 的唯一数据源。
 *
 * 曲库有两份：[library] 是扫描出来的全量（文件顺序，不动），[tracks] 是经过搜索过滤
 * 与排序后**实际展示和播放**的列表。播放列表跟着 [tracks] 走，所以排序或搜索一变，
 * 都要重建播放列表 —— 统一在 [rebuildDisplayed] 里做。
 *
 * 与桌面版的一处架构差异：**没有自己的 PlaybackQueue**。ExoPlayer 的播放列表
 * 原生支持顺序 / 循环 / 单曲 / 随机，还自带无缝衔接，再写一套等于和平台对着干。
 */
@OptIn(UnstableApi::class)
class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Preferences(app)
    private var controller: MediaController? = null
    private var progressJob: Job? = null
    private var metadataJob: Job? = null

    // 曲库
    private val _library = MutableStateFlow<List<Track>>(emptyList())
    val library: StateFlow<List<Track>> = _library.asStateFlow()

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _folderName = MutableStateFlow<String?>(null)
    val folderName: StateFlow<String?> = _folderName.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // 搜索与排序
    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    private val _sortOrder = MutableStateFlow(prefs.sortOrder)
    val sortOrder: StateFlow<TrackSortOrder> = _sortOrder.asStateFlow()

    private val _sortAscending = MutableStateFlow(prefs.sortAscending)
    val sortAscending: StateFlow<Boolean> = _sortAscending.asStateFlow()

    // 播放状态
    private val _playingTrack = MutableStateFlow<Track?>(null)
    val playingTrack: StateFlow<Track?> = _playingTrack.asStateFlow()

    /** 当前曲目在 [tracks] 里的下标；被搜索过滤掉时为 -1。 */
    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    /** 正在播的文件已不在曲库中（被删除或移走）。歌还能放完，但列表里没有它了。 */
    private val _playingTrackMissing = MutableStateFlow(false)
    val playingTrackMissing: StateFlow<Boolean> = _playingTrackMissing.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    // 歌词
    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyrics: StateFlow<List<LyricLine>> = _lyrics.asStateFlow()

    private val _currentLyricIndex = MutableStateFlow(-1)
    val currentLyricIndex: StateFlow<Int> = _currentLyricIndex.asStateFlow()

    /** 歌词是否带时间戳。无时间戳时只静态展示，不高亮滚动。 */
    private val _lyricsAreSynced = MutableStateFlow(false)
    val lyricsAreSynced: StateFlow<Boolean> = _lyricsAreSynced.asStateFlow()

    // 偏好
    private val _playMode = MutableStateFlow(prefs.playMode)
    val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    private val _volume = MutableStateFlow(prefs.volume)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _layout = MutableStateFlow(prefs.nowPlayingLayout)
    val layout: StateFlow<NowPlayingLayout> = _layout.asStateFlow()

    private val _backgroundOpacity = MutableStateFlow(prefs.backgroundOpacity)
    val backgroundOpacity: StateFlow<Float> = _backgroundOpacity.asStateFlow()

    private val _replayGainEnabled = MutableStateFlow(prefs.replayGainEnabled)
    val replayGainEnabled: StateFlow<Boolean> = _replayGainEnabled.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val isFiltering: Boolean get() = _searchText.value.isNotBlank()

    // MARK: - 生命周期

    /** Activity 启动时调用：连上后台播放服务，并恢复上次的曲库。 */
    fun connect() {
        if (controller != null) return

        val context = getApplication<Application>()
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))

        // 服务未启动时先拉起来，否则 MediaController 连不上
        context.startService(Intent(context, PlaybackService::class.java))

        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            controller = runCatching { future.get() }.getOrNull()
            controller?.let { attach(it) }
        }, MoreExecutors.directExecutor())
    }

    private fun attach(controller: MediaController) {
        controller.volume = _volume.value
        applyPlayModeToPlayer(_playMode.value)

        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) startProgressTicker() else stopProgressTicker()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                syncFromPlayer()
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    _durationMs.value = controller.duration.coerceAtLeast(0)
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // 坏文件不该卡住整张列表。ExoPlayer 默认会跳过出错的条目继续，
                // 这里只负责把原因说出来。
                _errorMessage.value = "无法播放该文件：${error.errorCodeName}"
            }
        })

        restoreLastSession()
    }

    override fun onCleared() {
        stopProgressTicker()
        controller?.release()
        controller = null
        super.onCleared()
    }

    // MARK: - 曲库

    private fun restoreLastSession() {
        val saved = prefs.treeUri ?: return
        val uri = Uri.parse(saved)

        // 授权可能已被系统或用户撤销，逐个核对
        val stillGranted = getApplication<Application>().contentResolver
            .persistedUriPermissions
            .any { it.uri == uri && it.isReadPermission }

        if (!stillGranted) {
            prefs.treeUri = null
            return
        }

        openFolder(uri, persist = false)
    }

    /** 用户从系统目录选择器选定了一个文件夹。 */
    fun openFolder(treeUri: Uri, persist: Boolean = true) {
        val context = getApplication<Application>()

        if (persist) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            prefs.treeUri = treeUri.toString()
        }

        _folderName.value = folderLabel(treeUri)
        _searchText.value = ""
        stopAndClear()

        scan(treeUri, reportEmpty = true)
    }

    /**
     * 重新扫描当前文件夹，把新增 / 删除的文件同步进来。
     * 与 [openFolder] 的区别：**不打断播放**，也不动搜索词和排序。
     */
    fun refreshLibrary() {
        val saved = prefs.treeUri ?: return
        if (_isScanning.value) return
        scan(Uri.parse(saved), reportEmpty = false)
    }

    private fun scan(treeUri: Uri, reportEmpty: Boolean) {
        metadataJob?.cancel()
        _isScanning.value = true

        viewModelScope.launch {
            val context = getApplication<Application>()
            val found = withContext(Dispatchers.IO) { LibraryScanner.scan(context, treeUri) }

            // 复用已有条目，避免重扫时把整库的元数据全部重读一遍
            val known = _library.value.associateBy { it.uri }
            _library.value = found.map { known[it.uri]?.copy(lrcUri = it.lrcUri) ?: it }

            rebuildDisplayed()
            _isScanning.value = false

            if (_library.value.isEmpty() && reportEmpty) {
                _errorMessage.value = "该文件夹下没有找到受支持的音频文件"
            }

            loadMetadata()
        }
    }

    /**
     * 逐个补全元数据。加载过程中只就地更新条目、不重排 —— 否则用户正在看的
     * 列表会随着元数据到位不断跳动。全部加载完再统一重排一次。
     */
    private fun loadMetadata() {
        metadataJob = viewModelScope.launch {
            val context = getApplication<Application>()
            val snapshot = _library.value

            for (track in snapshot) {
                if (track.metadataLoaded) continue

                val loaded = withContext(Dispatchers.IO) { MetadataLoader.load(context, track) }

                // 列表可能已被重新扫描，按 URI 校验后再写回
                val index = _library.value.indexOfFirst { it.uri == loaded.uri }
                if (index < 0) continue

                _library.value = _library.value.toMutableList().also { it[index] = loaded }
                patchDisplayed(loaded)
            }

            // 标题 / 歌手到位后，按这两个维度排序的结果才是对的
            if (_sortOrder.value != TrackSortOrder.FILE_ORDER) rebuildDisplayed()
        }
    }

    /** 元数据到位后就地替换展示列表里的同一条，不改变顺序。 */
    private fun patchDisplayed(loaded: Track) {
        val index = _tracks.value.indexOfFirst { it.uri == loaded.uri }
        if (index >= 0) {
            _tracks.value = _tracks.value.toMutableList().also { it[index] = loaded }
        }

        if (_playingTrack.value?.uri == loaded.uri) {
            _playingTrack.value = loaded
            refreshLyrics(loaded)
            if (_durationMs.value == 0L) _durationMs.value = loaded.durationMs
        }
    }

    // MARK: - 搜索与排序

    fun setSearchText(value: String) {
        if (_searchText.value == value) return
        _searchText.value = value
        rebuildDisplayed()
    }

    fun setSortOrder(value: TrackSortOrder) {
        if (_sortOrder.value == value) return
        _sortOrder.value = value
        prefs.sortOrder = value
        rebuildDisplayed()
    }

    fun toggleSortDirection() {
        _sortAscending.value = !_sortAscending.value
        prefs.sortAscending = _sortAscending.value
        rebuildDisplayed()
    }

    /**
     * 重建展示列表，并让播放列表跟上。
     *
     * 正在播的那首会被原地保留，不会因为重排而重新加载、从头播 ——
     * 这靠 [syncPlaylist] 的「先删两边、再补两边」实现。
     */
    private fun rebuildDisplayed() {
        _tracks.value = TrackFilter.apply(
            _library.value, _searchText.value, _sortOrder.value, _sortAscending.value
        )

        syncPlaylist()
        syncFromPlayer()
        updatePlayingTrackMissing()
    }

    /**
     * 正在播的曲目是否已经不在曲库里。
     *
     * 判据是**曲库**而不是展示列表 —— 被搜索过滤掉不等于文件没了，
     * 只有重扫后曲库里都找不到，才说明文件真的被删除或移走了。
     */
    private fun updatePlayingTrackMissing() {
        val uri = _playingTrack.value?.uri
        _playingTrackMissing.value = uri != null && _library.value.none { it.uri == uri }
    }

    // MARK: - 播放列表同步

    /**
     * 把 ExoPlayer 的播放列表换成当前展示列表。
     *
     * 不能直接 `setMediaItems` —— 那会重新准备当前条目，正在放的歌会卡一下、从头开始。
     * 做法是：先把当前条目前后的都删掉（此时它落到下标 0），再把新列表的前半段
     * 插到它前面、后半段接到它后面。当前条目自始至终没被动过，播放完全不受影响。
     */
    private fun syncPlaylist() {
        val player = controller ?: return
        val newList = _tracks.value
        val currentUri = currentPlayerUri()

        if (currentUri == null || player.mediaItemCount == 0) {
            player.setMediaItems(newList.map { it.toMediaItem() })
            player.prepare()
            return
        }

        val currentPos = player.currentMediaItemIndex

        // 先删后面再删前面：先删前面的话后面那段下标会整体前移
        if (currentPos + 1 < player.mediaItemCount) {
            player.removeMediaItems(currentPos + 1, player.mediaItemCount)
        }
        if (currentPos > 0) {
            player.removeMediaItems(0, currentPos)
        }

        val index = newList.indexOfFirst { it.uri == currentUri }

        if (index >= 0) {
            if (index > 0) player.addMediaItems(0, newList.take(index).map { it.toMediaItem() })
            player.addMediaItems(newList.drop(index + 1).map { it.toMediaItem() })
        } else {
            // 当前曲目已不在新列表里（被删或被搜索过滤）：让它留在最前面播完，
            // 后面接上新列表 —— 与桌面版「歌继续放，播完从列表继续」一致
            player.addMediaItems(newList.map { it.toMediaItem() })
        }
    }

    private fun currentPlayerUri(): String? = controller?.currentMediaItem?.mediaId

    /** 从播放器读回当前状态，刷新界面。 */
    private fun syncFromPlayer() {
        val player = controller ?: return
        val uri = currentPlayerUri()

        if (uri == null) {
            _playingTrack.value = null
            _currentIndex.value = -1
            _lyrics.value = emptyList()
            return
        }

        val track = _tracks.value.firstOrNull { it.uri == uri }
            ?: _library.value.firstOrNull { it.uri == uri }

        _currentIndex.value = _tracks.value.indexOfFirst { it.uri == uri }
        _durationMs.value = player.duration.coerceAtLeast(0)
        _positionMs.value = player.currentPosition.coerceAtLeast(0)

        if (track != null && _playingTrack.value?.uri != track.uri) {
            _playingTrack.value = track
            refreshLyrics(track)
            applyReplayGain(track)
        } else if (track != null) {
            _playingTrack.value = track
        }

        updatePlayingTrackMissing()
    }

    // MARK: - 播放控制

    fun playAt(index: Int) {
        val player = controller ?: return
        if (index !in _tracks.value.indices) return

        // 播放列表可能还没同步过（首次播放），补一次
        if (player.mediaItemCount != _tracks.value.size) {
            player.setMediaItems(_tracks.value.map { it.toMediaItem() })
        }

        player.seekTo(index, 0)
        player.prepare()
        player.play()
    }

    fun togglePlayPause() {
        val player = controller ?: return

        if (player.mediaItemCount == 0) {
            if (_tracks.value.isEmpty()) return
            playAt(0)
            return
        }

        if (player.isPlaying) player.pause() else {
            player.prepare()
            player.play()
        }
    }

    /** 停止：暂停并回到曲目开头（与桌面版一致，不是彻底卸载）。 */
    fun stop() {
        val player = controller ?: return
        player.pause()
        player.seekTo(0)
        _positionMs.value = 0
        _currentLyricIndex.value = -1
    }

    fun next() {
        val player = controller ?: return
        // seekToNextMediaItem 在单曲循环下不会前进，这里要的是「用户主动跳过」
        if (player.hasNextMediaItem()) player.seekToNextMediaItem()
        else if (_playMode.value != PlayMode.SEQUENTIAL) player.seekTo(0, 0)
    }

    fun previous() {
        val player = controller ?: return
        // 播放超过 3 秒时先回到本曲开头，符合常见播放器习惯
        if (player.currentPosition > 3_000) {
            player.seekTo(0)
            return
        }
        if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
        else player.seekTo(0)
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms.coerceAtLeast(0))
        _positionMs.value = ms.coerceAtLeast(0)
        updateLyricHighlight(ms / 1000.0)
    }

    fun setVolume(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        _volume.value = clamped
        prefs.volume = clamped
        controller?.volume = clamped
    }

    fun cyclePlayMode() {
        val mode = _playMode.value.next()
        _playMode.value = mode
        prefs.playMode = mode
        applyPlayModeToPlayer(mode)
    }

    /** 四种模式直接映射到 ExoPlayer 自带的重复 + 随机开关。 */
    private fun applyPlayModeToPlayer(mode: PlayMode) {
        val player = controller ?: return

        player.repeatMode = when (mode) {
            PlayMode.SEQUENTIAL -> Player.REPEAT_MODE_OFF
            PlayMode.REPEAT_ALL, PlayMode.SHUFFLE -> Player.REPEAT_MODE_ALL
            PlayMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
        }
        player.shuffleModeEnabled = mode == PlayMode.SHUFFLE
    }

    fun cycleLayout() {
        val next = _layout.value.next()
        _layout.value = next
        prefs.nowPlayingLayout = next
    }

    fun setBackgroundOpacity(value: Float) {
        val clamped = value.coerceIn(Preferences.MIN_BACKGROUND_OPACITY, 1f)
        _backgroundOpacity.value = clamped
        prefs.backgroundOpacity = clamped
    }

    fun setReplayGainEnabled(enabled: Boolean) {
        _replayGainEnabled.value = enabled
        prefs.replayGainEnabled = enabled
        _playingTrack.value?.let { applyReplayGain(it) }
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    // MARK: - 内部

    private fun applyReplayGain(track: Track) {
        GainProcessorHolder.processor.gain =
            if (_replayGainEnabled.value) track.replayGain?.linearGain() ?: 1f else 1f
    }

    private fun refreshLyrics(track: Track) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val lines = withContext(Dispatchers.IO) { LyricsProvider.lyricsFor(context, track) }

            // 期间可能又切歌了，丢弃过期结果
            if (_playingTrack.value?.uri != track.uri) return@launch

            _lyrics.value = lines
            _lyricsAreSynced.value = lines.any { it.time >= 0 }
            _currentLyricIndex.value = -1
        }
    }

    private fun startProgressTicker() {
        if (progressJob?.isActive == true) return

        progressJob = viewModelScope.launch {
            while (true) {
                val player = controller ?: break
                val position = player.currentPosition.coerceAtLeast(0)
                _positionMs.value = position
                updateLyricHighlight(position / 1000.0)
                delay(200)
            }
        }
    }

    private fun stopProgressTicker() {
        progressJob?.cancel()
        progressJob = null
    }

    /** 只在高亮行真正变化时写状态，避免每次 tick 都触发重组。 */
    private fun updateLyricHighlight(seconds: Double) {
        if (!_lyricsAreSynced.value || _lyrics.value.isEmpty()) return

        val index = LrcParser.indexAt(seconds, _lyrics.value)
        if (index != _currentLyricIndex.value) _currentLyricIndex.value = index
    }

    private fun stopAndClear() {
        controller?.run {
            stop()
            clearMediaItems()
        }
        _playingTrack.value = null
        _currentIndex.value = -1
        _playingTrackMissing.value = false
        _positionMs.value = 0
        _durationMs.value = 0
        _lyrics.value = emptyList()
        _currentLyricIndex.value = -1
    }

    private fun folderLabel(treeUri: Uri): String {
        // tree URI 的最后一段形如 "primary:Music/Rock"，取冒号后的部分给人看
        val raw = treeUri.lastPathSegment ?: return treeUri.toString()
        return raw.substringAfter(':', raw)
    }

    private fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
        .setUri(uri)
        .setMediaId(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .apply {
                    artwork?.let { setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
                }
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build()
        )
        .build()
}
