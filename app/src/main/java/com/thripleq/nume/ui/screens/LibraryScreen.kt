package com.thripleq.nume.ui.screens

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.thripleq.nume.core.repo.Chart
import com.thripleq.nume.core.repo.ChartRepository
import com.thripleq.nume.core.repo.Track

private sealed interface LibraryPhase {
    data object Loading : LibraryPhase
    data object Error : LibraryPhase
    data class Charts(val charts: List<Chart>) : LibraryPhase
}

/** 免登录首页：先列排行榜，点进榜单后列出曲目，点按播放。 */
@Composable
fun LibraryScreen(onPlayTrack: suspend (Track) -> Unit) {
    var phase by remember { mutableStateOf<LibraryPhase>(LibraryPhase.Loading) }
    var selected by remember { mutableStateOf<Chart?>(null) }
    var backStackCount by remember { mutableStateOf(0) }

    LaunchedEffect(backStackCount) {
        if (selected != null) return@LaunchedEffect
        phase = LibraryPhase.Loading
        val charts = ChartRepository.charts()
        phase = if (charts.isEmpty()) LibraryPhase.Error else LibraryPhase.Charts(charts)
    }

    val chart = selected
    when (phase) {
        is LibraryPhase.Loading -> CenteredBox { CircularProgressIndicator() }
        is LibraryPhase.Error -> CenteredBox { Text("加载失败，请检查网络") }
        is LibraryPhase.Charts -> {
            if (chart == null) {
                ChartList(
                    charts = (phase as LibraryPhase.Charts).charts,
                    onChart = { selected = it },
                )
            } else {
                TrackList(
                    chart = chart,
                    onBack = {
                        selected = null
                        backStackCount += 1
                    },
                    onPlay = onPlayTrack,
                )
            }
        }
    }
}

@Composable
private fun ChartList(charts: List<Chart>, onChart: (Chart) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("排行榜", style = androidx.compose.material3.MaterialTheme.typography.titleLarge) }
        items(charts, key = { it.id }) { c ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onChart(c) }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp)),
                ) {
                    c.coverUrl?.let { url ->
                        AsyncImage(
                            model = Uri.parse(url),
                            contentDescription = c.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = c.name,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TrackList(
    chart: Chart,
    onBack: () -> Unit,
    onPlay: suspend (Track) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "‹",
                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.clickable { onBack() }.padding(end = 12.dp),
                )
                Text(
                    text = chart.name,
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        items(chart.tracks, key = { it.id }) { track ->
            TrackRow(track) { onPlay(track) }
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
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = track.artist,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}