# Local Music Player（Android）

安卓本地音乐播放器。Kotlin + Jetpack Compose + Media3(ExoPlayer)，与
[macOS 版](https://github.com/will-meet-s/Local_Music_Player_MAC) 和
[Windows 版](https://github.com/will-meet-s/Local_Music_Player_WIN) 功能对齐。

## 功能

- 选择文件夹（SAF），递归扫描音乐（mp3 / m4a / aac / flac / wav / ogg / opus / aiff）
- 手动刷新曲库：同步新增 / 删除的文件，不打断播放
- 播放、暂停、停止、上一首、下一首、拖动进度、音量
- 四种播放顺序：顺序播放 → 列表循环 → 单曲循环 → 随机
- 歌词：同名 `.lrc` 优先，其次读音频内嵌歌词；逐行高亮自动滚动，点某行可跳播
- 搜索（歌名 / 歌手 / 专辑）与排序（文件顺序 / 歌曲名 / 歌手名，可升降序）
- 无缝切歌（gapless）、音量归一化（ReplayGain）
- 「正在播放」区三种展示模式：封面 + 歌词 / 只看封面 / 只看歌词
- 背景不透明度可调（20%–100%）
- **后台播放**：退到桌面继续放，通知栏与锁屏可切歌暂停，蓝牙耳机 / 车机按键同样有效
- 记住上次的文件夹、播放模式、音量、排序、布局

## 环境要求

- Android 8.0（API 26）及以上
- 构建：JDK 17 + Android SDK 34；用 Android Studio Koala 以上打开最省事

## 构建运行

```bash
./gradlew test              # 跑单元测试（纯 JVM，不需要设备）
./gradlew assembleDebug     # 产出 app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug      # 装到已连接的设备
```

仓库里**没有提交 gradle-wrapper.jar**（二进制不入库）。首次构建前先补一个：

```bash
gradle wrapper --gradle-version 8.7
```

或者直接用 Android Studio 打开项目，它会自动补齐 wrapper 并同步依赖。

## 为什么用 SAF 而不是 MediaStore

安卓 10 起限制直接路径访问，读本地音乐有两条路：

| | SAF（本项目） | MediaStore |
|---|---|---|
| 语义 | 用户指定一个文件夹，只扫它 | 全机已索引的音频 |
| 权限 | 不需要 | 需要 `READ_MEDIA_AUDIO` |
| 结果 | 与桌面版一致 | 会把微信语音、铃声、游戏音效全扫进来 |
| 重启后 | 持久化授权仍有效 | — |

选 SAF 是为了和桌面版「选择文件夹」的语义保持一致。

一个由此带来的实现细节：SAF 下**没法由音频 URI 推导出兄弟文件的 URI**，
所以同名 `.lrc` 是在扫描阶段按「目录 + 文件名主干」配对好的，不是播放时现找。

## 与桌面版的架构差异

**没有自己的播放队列。** macOS / Windows 版都有一个手写的 `PlaybackQueue`
负责四种播放顺序，Windows 版还得自己拼无缝管线。ExoPlayer 的播放列表原生支持
顺序 / 循环 / 单曲 / 随机，并且自带无缝衔接，再写一套等于和平台对着干。

映射关系：

| 播放模式 | ExoPlayer |
|---|---|
| 顺序播放 | `REPEAT_MODE_OFF` |
| 列表循环 | `REPEAT_MODE_ALL` |
| 单曲循环 | `REPEAT_MODE_ONE` |
| 随机播放 | `REPEAT_MODE_ALL` + `shuffleModeEnabled` |

代价是 `PlaybackQueue` 那套 park / peekNext 的单测不再适用，改由 ExoPlayer 保证。

**搜索或排序时不打断播放**：不能直接 `setMediaItems`，那会重新准备当前条目、
正在放的歌会卡一下并从头开始。做法是先把当前条目前后的都删掉（它落到下标 0），
再把新列表的前半段插到它前面、后半段接到它后面 —— 当前条目自始至终没被动过。

## 音频处理

### 无缝切歌（始终开启）

ExoPlayer 播放列表原生支持，无需额外工作。

### 音量归一化（默认开启）

读 `REPLAYGAIN_TRACK_GAIN` / `REPLAYGAIN_TRACK_PEAK` 标签补偿响度差异。

增益施加在自定义的 `GainAudioProcessor`（挂进 `DefaultAudioSink` 的处理链），
而不是 `player.volume` —— 后者是用户的音量旋钮，取值上限还是 1，
没法为偏轻的曲目**提升**音量。

- 只用**曲目级**增益，不用专辑级
- 已知峰值时保证补偿后不削波，增益系数钳制在 0.05–4 倍
- 没打标签的文件不受任何影响，处理器整个旁路

**一个已知的精度问题**：ExoPlayer 会提前缓冲，增益在 `onMediaItemTransition`
时切换，与实际听到的换曲点可能差几十到几百毫秒。相邻两首增益差很大时，
交界处会有一瞬间用错增益。

## 相对桌面版删掉的功能

| 桌面版 | 安卓 | 原因 |
|---|---|---|
| Windows 的桌面歌词 / 独占输出 | 无 | 悬浮窗按你的要求不做；独占输出安卓没有对应概念 |
| macOS 的磨砂背景 | 改为背景不透明度 | 系统级模糊在安卓上不通用 |
| 菜单栏 / 托盘常驻 | 前台服务 + 通知栏 | 安卓的对应做法，顺带白拿锁屏与蓝牙控制 |

## 代码结构

```
app/src/main/java/com/willmeet/musicplayer/
  model/          Track / LyricLine / PlayMode / NowPlayingLayout / TrackSortOrder
  library/        LibraryScanner（SAF 递归扫描）、MetadataLoader、
                  TrackFilter（搜索排序）、NaturalOrder
  lyrics/         LrcParser、LyricsProvider
  playback/       ReplayGain、GainAudioProcessor、PlaybackService（MediaSessionService）
  prefs/          Preferences（SharedPreferences）
  ui/             PlayerViewModel（唯一数据源）+ Compose 界面
app/src/test/     LrcParser / ReplayGain / TrackFilter / NaturalOrder 单测
```

`LrcParser`、`TrackFilter`、`ReplayGain`、`NaturalOrder` 都是纯 Kotlin，
`./gradlew test` 直接跑，不需要设备。`LibraryScanner`、`MetadataLoader`、
`PlaybackService` 依赖 Android 框架与真实音频，由手动验收覆盖。

## 已知限制

- 不做在线歌词下载、标签编辑、均衡器
- 内嵌歌词依赖 Media3 能解析出的 Vorbis Comment / ID3 TXXX 字段，覆盖不如
  桌面版的 TagLib# 全；`.lrc` 是更可靠的路径
- WAV 没有标准歌词标签，只能靠同名 `.lrc`
- 未做签名与上架，安装时系统会提示「来自未知来源」
