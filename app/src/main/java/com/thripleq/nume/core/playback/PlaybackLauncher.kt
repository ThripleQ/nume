package com.thripleq.nume.core.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.thripleq.nume.Gateway
import com.thripleq.nume.core.net.NeteaseOp
import com.thripleq.nume.core.repo.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Turns a [Track] into a playable [MediaItem] through libnetease and hands it to
 * the shared player. Downstream UI (列表点按、播放页) all funnel through here so
 * the byte-cache → ExoPlayer path stays in one place.
 */
object PlaybackLauncher {

    suspend fun play(context: Context, track: Track) {
        val url = songUrl(track.id) ?: return
        val item = MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(Uri.parse(url))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.name)
                    .setArtist(track.artist)
                    .setArtworkUri(track.artworkUrl?.let { Uri.parse(it) })
                    .build(),
            )
            .build()

        val player = PlayerHolder.get(context)
        ContextCompat.startForegroundService(
            context,
            Intent(context, PlaybackService::class.java),
        )
        player.setMediaItem(item)
        player.prepare()
        player.play()
    }

    private suspend fun songUrl(id: String): String? = withContext(Dispatchers.IO) {
        val gateway = Gateway.netease
        // exhigh is the flagship quality graded series; fall back to standard if
        // the account / track is not entitled to it (e.g. anon access).
        for (quality in listOf("exhigh", "standard")) {
            val r = gateway.call(NeteaseOp.SONG_URL_V1, id, quality)
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
}