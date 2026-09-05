package com.thripleq.nume.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DiscFull
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.thripleq.nume.core.repo.Track
import com.thripleq.nume.core.repo.TrackCollection
import com.thripleq.nume.ui.playerbar.CollectionActions
import com.thripleq.nume.ui.profile.TrackListSource
import com.thripleq.nume.ui.profile.TrackListUiState
import com.thripleq.nume.ui.profile.TrackListViewModel
import java.util.Locale

/**
 * 统一"壳子 + 列表"详情页：榜单 / 歌单 / 专辑 / 喜欢 / 已购都是同一个结构——
 * 头部集合信息（封面/标题/数据/描述/操作按钮）+ 曲目列表。
 */
@Composable
fun TrackListScreen(
    source: String,
    id: String,
    title: String,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    onActionsOffscreen: (Boolean) -> Unit = {},
    showTopBar: Boolean = true,
    compactHeader: Boolean = false,
) {
    val vm: TrackListViewModel = hiltViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val src = remember(source) { TrackListSource.from(source) }

    // 头部三按钮的滚动位置：滚到接近视口顶（即将看不见）时上报，触发底部操作浮岛。
    val listState = rememberLazyListState()
    var actionsTop by remember { mutableFloatStateOf(Float.POSITIVE_INFINITY) }
    val actionsThresholdPx = with(LocalDensity.current) { 90.dp.toPx() }
    val actionsOffscreen by remember { derivedStateOf { actionsTop < actionsThresholdPx } }
    LaunchedEffect(actionsOffscreen) { onActionsOffscreen(actionsOffscreen) }

    LaunchedEffect(source, id) { vm.load(src, id, title) }
    LaunchedEffect(Unit) { vm.openPlayer.collect { onOpenPlayer() } }

    // 数据到了直接显示列表（不预载封面：滚动到哪张就单张串行下载）。
    val collection = (state as? TrackListUiState.Ready)?.collection

    // 胶囊撑开：scale 0.92→1 + 圆角 28→0，内容像一颗胶囊被拉开成整屏。
    Column(Modifier.fillMaxSize()) {
        if (showTopBar) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp),
            ) {
                Text(
                    text = "‹",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clickable { onBack() }.padding(end = 12.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (collection != null) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = if (compactHeader) 8.dp else 16.dp,
                    top = if (compactHeader) 0.dp else 8.dp,
                    end = if (compactHeader) 8.dp else 16.dp,
                    bottom = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item(key = "header") {
                    TrackListHeader(
                        collection,
                        vm,
                        onActionsTop = { actionsTop = it },
                        compact = compactHeader,
                    )
                }
                itemsIndexed(
                    collection.tracks,
                    key = { _, t -> t.id },
                    contentType = { _, _ -> "track" },
                ) { index, track ->
                    TrackRow(index, track) { vm.onTrackClick(collection, index) }
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when (state) {
                    TrackListUiState.Loading -> LoadingHint("加载中…")
                    TrackListUiState.Empty -> Text(
                        "暂无曲目",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TrackListUiState.Error -> Text(
                        "曲目加载失败",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    is TrackListUiState.Ready -> LoadingHint("加载中…")
                }
            }
        }
    }
}

/** 全屏加载动画（数据/首屏封面预载统一用这一段，不再分两段）。 */
@Composable
private fun LoadingHint(text: String) {
    val rotation by rememberInfiniteTransition(label = "loadingSpin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "rotation",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.DiscFull,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer { rotationZ = rotation },
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/* ── 头部：壳的元数据 + 操作按钮 ─────────────────────── */

@Composable
private fun TrackListHeader(
    collection: TrackCollection,
    vm: TrackListViewModel,
    onActionsTop: (Float) -> Unit,
    compact: Boolean = false,
) {
    if (compact) {
        TrackListHeaderCompact(collection, vm, onActionsTop)
        return
    }
    val context = LocalContext.current.applicationContext

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(240.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            // model 整体 remember：AsyncImagePainter 以 model 为 key，每次重组新建
            // ImageRequest 会重走请求分发；按 480px（240dp 封面 @2x）尺寸构造并缓存。
            val context = LocalContext.current
            val cover = remember(collection.coverUrl) {
                collection.coverUrl?.let {
                    ImageRequest.Builder(context).data(it).size(480).build()
                }
            }
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = collection.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.List,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = collection.name,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        val line = collectionMetaLine(collection)
        if (line.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (collection.updateFrequency.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = collection.updateFrequency,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (collection.description.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = collection.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(16.dp))
        // onGloballyPositioned 上报三按钮顶部 y，供滚动浮岛判定"即将滑出视口"。
        Row(
            modifier = Modifier.onGloballyPositioned { onActionsTop(it.positionInWindow().y) },
        ) {
            CollectionActions(
                onPlayAll = { vm.onPlayAll(collection) },
                onPlaceholderAction = {
                    Toast.makeText(context, "开发中", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

/**
 * 紧凑头部：用于展开壳内的列表（胶囊拉成面板）。
 * 封面居中，下方一行元信息（播放量/收藏数等），再下方横排操作按钮。
 * 不再显示集合名称（壳顶标题栏已示"喜欢的音乐"，避免重复）。
 */
@Composable
private fun TrackListHeaderCompact(
    collection: TrackCollection,
    vm: TrackListViewModel,
    onActionsTop: (Float) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val context = LocalContext.current
            val cover = remember(collection.coverUrl) {
                collection.coverUrl?.let {
                    ImageRequest.Builder(context).data(it).size(240).build()
                }
            }
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = collection.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.List,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
        }
        val line = collectionMetaLine(collection)
        if (line.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(10.dp))
        // 按钮行：等宽横排、整行居中，onGloballyPositioned 上报三按钮顶部 y，供滚动浮岛判定。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { onActionsTop(it.positionInWindow().y) },
            horizontalArrangement = Arrangement.Center,
        ) {
            CollectionActions(
                onPlayAll = { vm.onPlayAll(collection) },
                onPlaceholderAction = {
                    Toast.makeText(context, "开发中", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

/** 数据行：按有值的字段拼接，如 "250.4亿次播放 · 3752.2万人收藏"。 */
private fun collectionMetaLine(c: TrackCollection): String {
    val parts = mutableListOf<String>()
    if (c.playCount > 0) parts += "${formatCount(c.playCount)}次播放"
    if (c.subscribedCount > 0) parts += "${formatCount(c.subscribedCount)}人收藏"
    if (c.trackCount > 0) parts += "${c.trackCount}首"
    if (c.creator.isNotBlank()) parts += c.creator
    return parts.joinToString(" · ")
}

/** 数字缩写：亿 / 万 / 原样。 */
private fun formatCount(n: Long): String = when {
    n >= 100_000_000 -> trimZero(String.format(Locale.US, "%.1f", n / 1.0e8)) + "亿"
    n >= 10_000 -> trimZero(String.format(Locale.US, "%.1f", n / 1.0e4)) + "万"
    else -> "$n"
}

private fun trimZero(s: String) = if (s.endsWith(".0")) s.dropLast(2) else s

/* ── 列表项：序号 + 封面 + 歌名/歌手 + 三点菜单 ───────── */

/** 行级不可变基础 modifier（fillMaxWidth + 圆角裁剪），避免每次重组重建 modifier 链。 */
private val trackRowBaseModifier = Modifier
    .fillMaxWidth()
    .clip(RoundedCornerShape(10.dp))

@Composable
private fun TrackRow(index: Int, track: Track, onClick: () -> Unit) {
    // model 整体 remember：AsyncImagePainter 以 model 为 key，每次重组新建 ImageRequest
    // 会重走请求分发；按 96px（48dp 封面 @2x）尺寸构造并缓存。
    val context = LocalContext.current
    val artwork = remember(track.artworkUrl) {
        track.artworkUrl?.let {
            ImageRequest.Builder(context).data(it).size(96).build()
        }
    }
    Row(
        modifier = trackRowBaseModifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (artwork != null) {
                AsyncImage(
                    model = artwork,
                    contentDescription = track.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            track.artist.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = { /* 三点菜单：暂无功能 */ }) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "更多",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
