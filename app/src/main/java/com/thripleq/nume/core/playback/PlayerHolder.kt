package com.thripleq.nume.core.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Process-scoped [ExoPlayer]. Built once with the byte-cache wired into its
 * media-source factory so every stream flows through [PlaybackCache].
 */
object PlayerHolder {

    @Volatile
    private var player: ExoPlayer? = null

    private val recoveryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
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
                    repeat(40) {
                        delay(250)
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

    private fun build(context: Context): ExoPlayer {
        // Upstream HTTP (the audio CDN). Accept protocol redirects and keep a
        // UA so netease's CDN doesn't 4xx on us.
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)

        val upstream = DefaultDataSource.Factory(context, http)

        // Byte-cache front: cache hit → local read; miss → range request upstream.
        val cacheFactory = CacheDataSource.Factory()
            .setCache(PlaybackCache.get(context))
            .setUpstreamDataSourceFactory(upstream)

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
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
    }

    const val USER_AGENT = "nume/0.1 (Android)"
}