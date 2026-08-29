package com.thripleq.nume.core.repo

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.thripleq.nume.Gateway
import com.thripleq.nume.core.net.ApiResult
import com.thripleq.nume.core.net.NeteaseOp
import com.thripleq.nume.core.net.NetEaseGateway
import com.thripleq.nume.core.playback.PlaybackService
import com.thripleq.nume.core.playback.PlayerHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Temporary stand-in until Home/搜索 lands: fabricates a playable [MediaItem]
 * for a hard-coded song from the real libnetease pipeline.
 *
 * Set [DEBUG_SONG_ID] to a working track id to validate playback end-to-end
 * (gateway → song URL → byte-cache → ExoPlayer) without any login yet.
 */
object DebugFeed {

    /** TODO: point this at a real netease track id to validate playback. */
    const val DEBUG_SONG_ID = ""

    suspend fun loadAndPlay(context: Context) {
        if (DEBUG_SONG_ID.isBlank()) return
        val gateway = Gateway.netease
        val url = songUrl(gateway, DEBUG_SONG_ID) ?: return
        val detail = songDetail(gateway, DEBUG_SONG_ID)
        val item = MediaItem.Builder()
            .setUri(Uri.parse(url))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(detail?.name)
                    .setArtist(detail?.artist)
                    .setArtworkUri(detail?.artworkUrl?.let { Uri.parse(it) })
                    .build(),
            )
            .build()
        val player = PlayerHolder.get(context)
        // Bring the media session/notification service up before playing.
        ContextCompat.startForegroundService(
            context,
            Intent(context, PlaybackService::class.java),
        )
        player.setMediaItem(item)
        player.prepare()
        player.play()
    }

    private suspend fun songUrl(gateway: NetEaseGateway, id: String): String? =
        withContext(Dispatchers.IO) {
            val r = gateway.call(NeteaseOp.SONG_URL_V1, id, "exhigh")
            parseOrNull(r) { root ->
                root.getJSONArray("data").getJSONObject(0).optString("url").takeIf { it.isNotBlank() }
            }
        }

    private suspend fun songDetail(
        gateway: NetEaseGateway,
        id: String,
    ): SongDetail? = withContext(Dispatchers.IO) {
        val r = gateway.call(NeteaseOp.SONG_DETAIL, id)
        parseOrNull(r) { root ->
            val s = root.getJSONArray("songs").getJSONObject(0)
            val name = s.getString("name")
            val artist = s.getJSONArray("ar").optJSONObject(0)?.optString("name")
            val artwork = s.getJSONObject("al").optString("picUrl").takeIf { it.isNotBlank() }
            SongDetail(name, artist ?: "", artwork)
        }
    }

    private inline fun <T> parseOrNull(
        result: ApiResult,
        block: (JSONObject) -> T?,
    ): T? = try {
        if (result.err != 0) null
        else block(JSONObject(String(result.body, Charsets.UTF_8)))
    } catch (_: Exception) {
        null
    }

    data class SongDetail(val name: String, val artist: String, val artworkUrl: String?)
}