package com.thripleq.nume.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.thripleq.nume.core.playback.PlayerHolder
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Full-screen player: cover, metadata, seek bar and transport controls. */
@Composable
fun PlayerScreen() {
    val context = LocalContext.current.applicationContext
    val player = remember { PlayerHolder.get(context) }

    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var coverUrl by remember { mutableStateOf<String?>(null) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var seekPending by remember { mutableStateOf(false) }
    var dragMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaMetadataChanged(meta: MediaMetadata) {
                title = meta.title?.toString() ?: ""
                artist = meta.artist?.toString() ?: ""
                coverUrl = meta.artworkUri?.toString()
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    durationMs = player.duration.coerceAtLeast(0L)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                errorText = error.message
            }
        }
        player.addListener(listener)
        // Seed state if the player already holds a media item when we open the screen.
        val meta = player.mediaMetadata
        title = meta.title?.toString() ?: ""
        artist = meta.artist?.toString() ?: ""
        coverUrl = meta.artworkUri?.toString()
        try {
            while (isActive) {
                if (!seekPending) positionMs = player.currentPosition
                durationMs = player.duration.coerceAtLeast(0L)
                delay(250)
            }
        } finally {
            player.removeListener(listener)
        }
    }

    val rangeMax = durationMs.toFloat().coerceAtLeast(1f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.weight(1f))

        // Cover
        Box(
            modifier = Modifier
                .size(280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            coverUrl?.let { uri ->
                AsyncImage(
                    model = Uri.parse(uri),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(Modifier.height(40.dp))

        // Track / metadata
        Text(
            text = title.ifEmpty { "未设置歌曲" },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = artist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.weight(1f))

        // Seek bar + time labels
        Column(Modifier.fillMaxWidth()) {
            Slider(
                value = if (seekPending) dragMs.toFloat() else positionMs.toFloat(),
                onValueChange = { dragMs = it.toLong(); seekPending = true },
                onValueChangeFinished = {
                    player.seekTo(dragMs)
                    seekPending = false
                },
                valueRange = 0f..rangeMax,
                enabled = durationMs > 0,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatTime(positionMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatTime(durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Transport controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { player.seekToPreviousMediaItem() }) {
                TransportGlyph("⏮", 32.sp)
            }
            IconButton(onClick = {
                if (isPlaying) player.pause() else player.play()
            }) {
                val glyph = when {
                    isBuffering -> "⋯"
                    isPlaying -> "⏸"
                    else -> "▶"
                }
                TransportGlyph(glyph, 48.sp)
            }
            IconButton(onClick = { player.seekToNextMediaItem() }) {
                TransportGlyph("⏭", 32.sp)
            }
        }

        Spacer(Modifier.height(8.dp))
        errorText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun TransportGlyph(glyph: String, size: androidx.compose.ui.unit.TextUnit) {
    Text(
        text = glyph,
        fontSize = size,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

private fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1000
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}