package com.thripleq.nume.core.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer

/**
 * Process-scoped [ExoPlayer]. Built once with the byte-cache wired into its
 * media-source factory so every stream flows through [PlaybackCache].
 */
object PlayerHolder {

    @Volatile
    private var player: ExoPlayer? = null

    fun get(context: Context): ExoPlayer = player ?: synchronized(this) {
        player ?: build(context).also { player = it }
    }

    fun release() {
        synchronized(this) {
            player?.release()
            player = null
        }
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
            .build()
    }

    const val USER_AGENT = "nume/0.1 (Android)"
}