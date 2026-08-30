package com.thripleq.nume.core.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.thripleq.nume.BuildConfig
import com.thripleq.nume.core.net.NetEaseGateway
import com.thripleq.nume.core.net.NeteaseOp
import com.thripleq.nume.core.repo.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns [Track]s into a playable [MediaItem] queue through libnetease and hands it
 * to the shared player. Downstream UI (列表点按、播放页) all funnel through here so
 * the byte-cache → ExoPlayer path stays in one place.
 */
@Singleton
class PlaybackLauncher @Inject constructor(
    private val gateway: NetEaseGateway,
) {
    // id -> 已解析 url（失败也缓存 null，避免同一首歌反复请求）。
    // 网易云音频 url 有时效性，缓存带 TTL：过期后重新解析，避免回退到旧链接。
    private data class CachedUrl(val url: String?, val at: Long)

    private val urlCache = ConcurrentHashMap<String, CachedUrl>()

    // 后台补队列只在主线程触碰 ExoPlayer（非线程安全），网络解析在 IO。
    private val enqueueScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var enqueueJob: Job? = null

    /** Plays a single track; ⏮/⏭ become no-ops since there is no queue. */
    suspend fun play(context: Context, track: Track) =
        play(context, listOf(track), 0)

    /**
     * Plays every track of [tracks] as a queue starting at [index], so ⏮/⏭ and
     * autoplay move through the whole list (e.g. an entire chart).
     *
     * UI 优先: 先把 [index] 首的元数据以占位 [MediaItem] 立即交给 player
     * （迷你条/播放页零等待显示歌名封面），再解析实际音频 url；解析成功后
     * 替换占位并开始出声。剩余曲目由 [enqueueRest] 在后台逐首解析追加，
     * 避免大列表（如"喜欢的音乐"几百首）一次全量解析把点击响应拖到几十秒。
     */
    suspend fun play(context: Context, tracks: List<Track>, index: Int) {
        if (tracks.isEmpty()) return
        enqueueJob?.cancel()
        val player = PlayerHolder.get(context)

        val idx = index.coerceIn(0, tracks.lastIndex)

        // 1) 占位即响: 立即让 player 持有当前曲目元数据，UI 即刻显示。
        val first = tracks[idx]
        player.setMediaItems(listOf(placeholderItem(first)), 0, 0L)

        // 2) 解析当前首的音频 url; 失败自动向后找最近可播的（VIP/无版权跳过）。
        val startPlayable = firstPlayable(tracks, idx) ?: run {
            player.clearMediaItems()
            return
        }
        val (playIdx, url) = startPlayable
        val playItem = tracks[playIdx]
        player.setMediaItems(listOf(buildMediaItem(playItem, url)), 0, 0L)
        player.prepare()
        player.play()

        // Start the background service AFTER playback has begun. Media3's
        // MediaSessionService calls startForeground() off the first player-state
        // change; kicking the foreground timer first when the audio fetch is slow
        // blows Android's 5s window and crashes (ForegroundServiceDidNotStart…).
        ContextCompat.startForegroundService(
            context,
            Intent(context, PlaybackService::class.java),
        )

        // 3) 后台补齐剩余队列（含 index 前的追尾），不阻塞点击响应。
        enqueueRest(context, tracks, playIdx)
    }

    /** Appends the rest of [tracks] (after [start]) onto the queue in the background. */
    private fun enqueueRest(context: Context, tracks: List<Track>, start: Int) {
        enqueueJob?.cancel()
        enqueueJob = enqueueScope.launch {
            val player = PlayerHolder.get(context)
            val n = tracks.size
            // 从 start 后一首开始，绕一圈到 start 前一首（追尾），保持整单顺序。
            var next = start + 1
            val end = start + n
            while (isActive) {
                val behind = player.mediaItemCount - player.currentMediaItemIndex - 1
                if (next < end && behind < PREFETCH_LOOKAHEAD) {
                    // 队列尾部快被播到，才解析下一首：请求随播放节奏稀疏发放，
                    // 不再整单一次性轰炸（JNI 加解密 CPU 密集 × 100+ 首会跟 UI 抢调度）。
                    val idx = next % n
                    next++
                    val url = songUrlCached(tracks[idx].id)
                    if (url != null) {
                        player.addMediaItem(buildMediaItem(tracks[idx], url))
                    }
                } else {
                    delay(PREFETCH_POLL_MS)
                }
            }
        }
    }

    /** First track from [fromIndex] (looping through the head) that resolves a url. */
    private suspend fun firstPlayable(tracks: List<Track>, fromIndex: Int): Pair<Int, String>? {
        val n = tracks.size
        val order = buildList {
            for (i in fromIndex until n) add(i)
            for (i in 0 until fromIndex) add(i)
        }
        for (i in order) {
            val url = songUrlCached(tracks[i].id)
            if (url != null) return i to url
        }
        return null
    }

    private suspend fun songUrlCached(id: String): String? {
        val now = System.currentTimeMillis()
        urlCache[id]?.let { hit ->
            if (now - hit.at < CACHE_TTL_MS) return hit.url
        }
        val url = songUrl(id)
        urlCache[id] = CachedUrl(url, now)
        return url
    }

    /** [MediaItem] used to make the UI respond before the real url resolves.
     *  Carries a fake uri that only needs to pass DefaultMediaSourceFactory's
     *  type inference (".mp3" → progressive). It is never prepared: the real
     *  item replaces it before [ExoPlayer.prepare] is called. */
    private fun placeholderItem(track: Track): MediaItem =
        MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(PLACEHOLDER_URI)
            .setMediaMetadata(mediaMetadata(track))
            .build()

    private fun buildMediaItem(track: Track, url: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(Uri.parse(url.toHttps()))
            .setMediaMetadata(mediaMetadata(track))
            .build()

    private fun mediaMetadata(track: Track): MediaMetadata =
        MediaMetadata.Builder()
            .setTitle(track.name)
            .setArtist(track.artist)
            .setArtworkUri(track.artworkUrl?.let { Uri.parse(it) })
            .build()

    /** Netease CDN audio URLs come back as http://; ExoPlayer blocks cleartext by
     *  default, and the CDN serves the same files over https. Upgrade instead of
     *  weakening the network security policy. */
    private fun String.toHttps(): String =
        if (startsWith("http://", ignoreCase = true)) {
            "https://" + substring(7)
        } else {
            this
        }

    private suspend fun songUrl(id: String): String? = withContext(Dispatchers.IO) {
        // exhigh is the flagship quality graded series; fall back to standard if
        // the account / track is not entitled to it (e.g. anon access).
        for (quality in listOf("exhigh", "standard")) {
            val r = gateway.call(NeteaseOp.SONG_URL_V1, id, quality)
            if (BuildConfig.DEBUG) {
                android.util.Log.e("ProfileDiag", "songUrl id=$id q=$quality code=${r.code} err=${r.err} body=${String(r.body, Charsets.UTF_8).take(300)}")
            }
            val url = parseUrl(r)
            if (url != null) return@withContext url
        }
        null
    }

    private fun parseUrl(result: com.thripleq.nume.core.net.ApiResult): String? {
        if (result.err != 0) return null
        return try {
            val root = JSONObject(String(result.body, Charsets.UTF_8))
            root.getJSONArray("data").getJSONObject(0).optString("url").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private val PLACEHOLDER_URI = Uri.parse("file:///tmp/nume_placeholder.mp3")
        // 音频 url 有效期数小时，缓存 6h 后重新解析。
        private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L
        // 队列中始终保留的"已就绪后续曲目"数量：播到不足就预解析下一首。
        private const val PREFETCH_LOOKAHEAD = 5
        // 按需预缓冲的轮询间隔：进度推进才补，无需高频。
        private const val PREFETCH_POLL_MS = 500L
    }
}