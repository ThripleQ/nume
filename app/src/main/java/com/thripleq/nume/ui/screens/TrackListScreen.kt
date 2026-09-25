package com.thripleq.nume.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.thripleq.nume.core.repo.Track
import com.thripleq.nume.core.repo.TrackCollection
import com.thripleq.nume.ui.components.BigCoverVisual
import com.thripleq.nume.ui.components.LocalShellProgress
import com.thripleq.nume.ui.components.SkeletonBox
import com.thripleq.nume.ui.components.SkeletonLine
import com.thripleq.nume.ui.playerbar.CollectionActions
import com.valentinilk.shimmer.shimmer
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
    /** 封面是否显示集合名；调用方已在顶栏/壳顶标题栏显示标题时可传 false 避免重复。 */
    showName: Boolean = true,
    bottomPadding: Dp = 16.dp,
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
            // 统一 banner 头：封面是列表第一项（左右 16dp 内缩、随滚动移出），元信息叠在封面里；
            // 列表行同样 16dp 内缩，与封面同宽。所有列表（榜单/歌单/专辑/喜欢/已购）共用此形态。
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 0.dp,
                    top = if (showTopBar) 8.dp else 0.dp,
                    end = 0.dp,
                    bottom = bottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item(key = "header") {
                    TrackListBannerHeader(collection, vm, showName) { actionsTop = it }
                }
                itemsIndexed(
                    collection.tracks,
                    key = { _, t -> t.id },
                    contentType = { _, _ -> "track" },
                ) { index, track ->
                    TrackRow(index, track, hPadding = 16.dp) {
                        vm.onTrackClick(collection, index)
                    }
                }
            }
        } else {
            when (state) {
                TrackListUiState.Empty -> CenteredHint("暂无曲目", MaterialTheme.colorScheme.onSurfaceVariant)
                TrackListUiState.Error -> CenteredHint("曲目加载失败", MaterialTheme.colorScheme.error)
                else -> TrackListSkeleton(showTopBar)
            }
        }
    }
}

@Composable
private fun CenteredHint(text: String, color: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

/**
 * 列表骨架：与 banner 头同构——方形封面（内缩量同真实头，随壳展开进度收起）+ 居中三按钮 + 曲目行。
 * 微光由根 Column 的 `shimmer()` 统一提供。
 */
@Composable
private fun TrackListSkeleton(showTopBar: Boolean) {
    val p = LocalShellProgress.current
    Column(
        Modifier
            .fillMaxSize()
            .shimmer()
            .padding(top = if (showTopBar) 8.dp else 0.dp),
    ) {
        SkeletonBox(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp * p)
                .aspectRatio(1f),
            RoundedCornerShape(16.dp),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            repeat(3) { SkeletonBox(Modifier.width(96.dp).height(40.dp), RoundedCornerShape(percent = 50)) }
        }
        Spacer(Modifier.height(12.dp))
        repeat(6) { SkeletonTrackRow() }
    }
}

@Composable
private fun SkeletonTrackRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBox(Modifier.size(48.dp), RoundedCornerShape(8.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonLine(widthFraction = 0.6f, height = 14.dp)
            SkeletonLine(widthFraction = 0.35f, height = 12.dp)
        }
        SkeletonBox(Modifier.size(24.dp), CircleShape)
    }
}

/**
 * Banner 头：封面作为列表第一项，随列表滚动移出。
 *
 * 封面与列表行的内容同宽（左右 16dp 内缩），方形、圆角；内缩量随壳展开进度从 0 收到 16dp，
 * 于是 p=0 时封面恰好满宽等于起点卡片（同源对齐契约），完全展开后与列表对齐。
 * 元信息叠在封面底部（名字下方，渐变遮罩保证可读）。
 */
@Composable
private fun TrackListBannerHeader(
    collection: TrackCollection,
    vm: TrackListViewModel,
    showName: Boolean = true,
    onActionsTop: (Float) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val p = LocalShellProgress.current
    val meta = listOfNotNull(
        collectionMetaLine(collection).takeIf { it.isNotBlank() },
        collection.updateFrequency.takeIf { it.isNotBlank() },
        collection.description.takeIf { it.isNotBlank() },
    ).joinToString("\n")

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp * p)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp)),
        ) {
            BigCoverVisual(
                coverUrl = collection.coverUrl,
                name = collection.name,
                modifier = Modifier.fillMaxSize(),
                meta = meta.ifBlank { null },
                showName = showName,
                scrimTop = 0.35f,
                scrimAlpha = 0.85f,
            )
        }
        Spacer(Modifier.height(12.dp))
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
private fun TrackRow(index: Int, track: Track, hPadding: Dp = 8.dp, onClick: () -> Unit) {
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
            .padding(horizontal = hPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            if (artwork != null) {
                Box(
                    Modifier
                        .matchParentSize()
                        .shimmer()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                AsyncImage(
                    model = artwork,
                    contentDescription = track.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
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
