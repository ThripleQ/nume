package com.thripleq.nume.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thripleq.nume.core.playback.PlaybackLauncher
import com.thripleq.nume.core.repo.ChartRepository
import com.thripleq.nume.core.repo.Track
import kotlinx.coroutines.launch

private enum class TrackPhase { Loading, Error, Ready }

/** 单个榜单的曲目列表。点一首歌以整榜为队列开始播放。 */
@Composable
fun ChartDetailScreen(
    chartId: String,
    name: String,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(TrackPhase.Loading) }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }

    LaunchedEffect(chartId) {
        phase = TrackPhase.Loading
        tracks = try {
            ChartRepository.chartTracks(chartId).also {
                phase = if (it.isEmpty()) TrackPhase.Error else TrackPhase.Ready
            }
        } catch (_: Exception) {
            phase = TrackPhase.Error
            emptyList()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "‹",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.clickable { onBack() }.padding(end = 12.dp),
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            when (phase) {
                TrackPhase.Loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("加载曲目中…", style = MaterialTheme.typography.bodySmall)
                }
                TrackPhase.Error -> Text(
                    "曲目加载失败",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                TrackPhase.Ready -> Unit
            }
        }
        items(tracks, key = { it.id }) { track ->
            TrackRow(track) {
                scope.launch {
                    PlaybackLauncher.play(context, tracks, tracks.indexOf(track))
                    onOpenPlayer()
                }
            }
        }
    }
}

@Composable
private fun TrackRow(track: Track, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = track.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}