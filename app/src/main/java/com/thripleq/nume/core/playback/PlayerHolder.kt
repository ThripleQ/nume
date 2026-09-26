package com.thripleq.nume.core.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Process-scoped [ExoPlayer]. Built once with the byte-cache wired into its
 * media-source factory so every stream flows through [PlaybackCache].
 */
object PlayerHolder {

    @Volatile
    private var player: ExoPlayer? = null

    // song id → 签名音频 URL。由 NumeApplication 在启动时安装（见 installUrlResolver）。
    // 在 ExoPlayer 加载线程上被调用，实现必须是阻塞且线程安全的。
    @Volatile
    private var urlResolver: ((String) -> String?)? = null

    /** Installs the lazy URL resolver used by the [ResolvingDataSource]. Call once at startup. */
    fun installUrlResolver(resolver: (String) -> String?) {
        urlResolver = resolver
    }

    // 错误恢复用的协程作用域。object 单例的普通属性在类初始化时就求值；
    // 用 lazy 推迟到首次真正需要时再取 Main dispatcher，避免在非 UI 线程
    // （如无 Looper 的工作线程）首次触碰对象导致 Main.immediate 初始化失败。
    private val recoveryScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
    private var recoveryJob: Job? = null

    fun get(context: Context): ExoPlayer = player ?: synchronized(this) {
        player ?: build(context).also {
            it.addErrorRecovery()
            player = it
        }
    }

    /** 播放失败（无权/VIP、盗链、404…）自动跳到下一首，别让队列卡死在 source
     *  error 上。后台补队列可能还没把下一首加进来，此时轮询等待（最多 ~10s）。 */
    private fun ExoPlayer.addErrorRecovery() {
        addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (mediaItemCount == 0) return
                if (nextMediaItemIndex != C.INDEX_UNSET) {
                    recover()
                    return
                }
                recoveryJob?.cancel()
                recoveryJob = recoveryScope.launch {
                    var attempts = 0
                    while (attempts < RECOVERY_WAIT_ATTEMPTS) {
                        delay(RECOVERY_POLL_MS)
                        attempts++
                        if (nextMediaItemIndex != C.INDEX_UNSET && mediaItemCount > 0) {
                            recover()
                            return@launch
                        }
                        if (playbackState != Player.STATE_IDLE) return@launch
                    }
                }
            }
        })
    }

    private fun ExoPlayer.recover() {
        seekToNextMediaItem()
        // error 状态 player 停在 STATE_IDLE：seekToNextMediaItem 只切换 index，
        // 不会自动开始缓冲；必须显式 prepare() 才会重新加载下一首的音频。
        prepare()
        play()
    }

    /** 统一播放/暂停：error 状态下 play() 需先 prepare 才会重新加载，否则无声。 */
    fun togglePlay(player: Player) {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playerError != null) player.prepare()
            player.play()
        }
    }

    /** 统一下一首：error 状态下 seekToNext 后必须 prepare+play 才会加载新曲目。 */
    fun skipNext(player: Player) {
        if (player.playerError != null) {
            player.seekToNextMediaItem()
            player.prepare()
            player.play()
        } else {
            player.seekToNextMediaItem()
        }
    }

    /** 统一上一首：同上，error 后需要显式 prepare 才能恢复加载。 */
    fun skipPrevious(player: Player) {
        if (player.playerError != null) {
            player.seekToPreviousMediaItem()
            player.prepare()
            player.play()
        } else {
            player.seekToPreviousMediaItem()
        }
    }

    /** 统一 seek：error 状态下拖动进度条同样需要先 prepare 恢复。 */
    fun seekTo(player: Player, positionMs: Long) {
        if (player.playerError != null) player.prepare()
        player.seekTo(positionMs)
    }

    /** 随机播放开关（直接映射 ExoPlayer.shuffleModeEnabled）。 */
    fun toggleShuffle(player: Player) {
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    /** 循环模式轮换：关 → 列表循环 → 单曲循环 → 关。 */
    fun cycleRepeat(player: Player) {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun build(context: Context): ExoPlayer {
        // Upstream HTTP (the audio CDN). Accept protocol redirects and keep a
        // UA so netease's CDN doesn't 4xx on us.
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)

        val base = DefaultDataSource.Factory(context, http)

        // 惰性解析：队列里放的是合成 URI `nume://song/<id>`，真正打开某首歌的字节流
        // 时才把 URI 换成签名 URL。放在 CacheDataSource 的**上游**，于是缓存键取合成
        // URI（稳定）：命中缓存根本不触发解析，签名 URL 轮换也不会让已缓存音频失效。
        val resolving = ResolvingDataSource.Factory(base) { dataSpec ->
            val id = PlaybackUrls.songId(dataSpec.uri)
            if (id == null) {
                dataSpec
            } else {
                val url = urlResolver?.invoke(id)
                    ?: throw IOException("no playable url for song $id")
                dataSpec.withUri(Uri.parse(url))
            }
        }

        // Byte-cache front: cache hit → local read; miss → resolve URL + range request.
        val cacheFactory = CacheDataSource.Factory()
            .setCache(PlaybackCache.get(context))
            .setUpstreamDataSourceFactory(resolving)

        val mediaFactory =
            androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
                .setDataSourceFactory(cacheFactory)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaFactory)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            // 流媒体用 NETWORK 唤醒锁（LOCAL 只持 CPU 锁，息屏后 WiFi 可能休眠，
            // 弱网/长缓冲时断流）。成熟播放器（Media3 示例 / ViMusic）均用 NETWORK。
            .setWakeMode(C.WAKE_MODE_NETWORK)
            // 拔耳机/蓝牙断开自动暂停：handleAudioFocus 只处理 AudioFocus，
            // 不覆盖 AUDIO_BECOMING_NOISY；不开会突然外放。
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    const val USER_AGENT = "nume/0.1 (Android)"

    // 错误恢复轮询：间隔与次数共同决定最长等待(3s)。后台补队列可能还没就绪，
    // 等一小段让它补上；超时则放弃这首，避免播放长时间卡死在错误态。
    private const val RECOVERY_POLL_MS = 250L
    private const val RECOVERY_WAIT_ATTEMPTS = 12
}