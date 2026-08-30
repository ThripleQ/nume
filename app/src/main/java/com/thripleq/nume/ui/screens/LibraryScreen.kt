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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.thripleq.nume.core.repo.Chart
import com.thripleq.nume.ui.library.LibraryUiState
import com.thripleq.nume.ui.library.LibraryViewModel

/** 免登录首页：列出排行榜，点进榜单到统一列表页。 */
@Composable
fun LibraryScreen(onOpenChart: (String, String) -> Unit) {
    val vm: LibraryViewModel = hiltViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    when (val s = state) {
        is LibraryUiState.Loading -> CenteredBox { CircularProgressIndicator() }
        is LibraryUiState.Error -> CenteredBox {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("加载失败，请检查网络", color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.size(12.dp))
                Button(onClick = vm::load) { Text("重试") }
            }
        }
        is LibraryUiState.Charts -> ChartList(
            charts = s.charts,
            onChart = { c -> onOpenChart(c.id, c.name) },
        )
    }
}

@Composable
private fun ChartList(charts: List<Chart>, onChart: (Chart) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 128.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("排行榜", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface) }
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
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}