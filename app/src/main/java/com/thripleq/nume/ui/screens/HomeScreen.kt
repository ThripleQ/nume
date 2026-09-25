package com.thripleq.nume.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.thripleq.nume.core.repo.Track
import com.thripleq.nume.ui.components.BigCoverVisual
import com.thripleq.nume.ui.components.ExpandableShell
import com.thripleq.nume.ui.components.LocalShellProgress
import com.thripleq.nume.ui.components.ShimmerImagePlaceholder
import com.thripleq.nume.ui.components.SkeletonBox
import com.thripleq.nume.ui.components.SkeletonLine
import com.valentinilk.shimmer.shimmer
import com.thripleq.nume.ui.home.HomeUiState
import com.thripleq.nume.ui.home.HomeViewModel

/** 探索 tab：每日推荐歌曲 / 推荐歌单 / 排行榜 / 最近播放。
 *  大封面 = 歌单/榜单（横滑卡片，点开走胶囊伸展壳进全屏列表）；
 *  小封面 = 单曲（内联行，点了直接播，无展开动效）。 */
@Composable
fun HomeScreen(
    onOpenPlayer: () -> Unit,
    onWebLogin: () -> Unit,
    islandHeight: Float = 0f,
    onShellOpenChange: (Boolean) -> Unit = {},
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.openPlayer.collect { onOpenPlayer() } }

    var expand by remember { mutableStateOf<ExpandTarget?>(null) }
    val islandClearance = with(LocalDensity.current) { islandHeight.dp }

    // 展开壳打开时通知上层收起底部导航（保留迷你播放条）；离开页面时复位。
    val shellOpen = expand != null
    LaunchedEffect(shellOpen) { onShellOpenChange(shellOpen) }
    DisposableEffect(Unit) { onDispose { onShellOpenChange(false) } }

    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            HomeUiState.Loading -> HomeSkeleton(bottomPadding = islandClearance + 16.dp)
            HomeUiState.Error -> Centered {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("加载失败，请检查网络", color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = vm::load) { Text("重试") }
                }
            }
            is HomeUiState.Ready -> HomeContent(
                data = s,
                bottomPadding = islandClearance + 16.dp,
                onPlay = vm::onPlayTrack,
                onExpand = { expand = it },
                onWebLogin = onWebLogin,
                onRefresh = vm::load,
            )
        }

        expand?.let { target ->
            HomeExpandShell(
                target = target,
                bottomPadding = islandClearance + 16.dp,
                onOpenPlayer = onOpenPlayer,
                onDismiss = { expand = null },
            )
        }
    }
}

private data class ExpandTarget(
    val source: String,
    val id: String,
    val title: String,
    val coverUrl: String?,
    val rect: Rect,
)

@Composable
private fun HomeContent(
    data: HomeUiState.Ready,
    bottomPadding: Dp,
    onPlay: (List<Track>, Int) -> Unit,
    onExpand: (ExpandTarget) -> Unit,
    onWebLogin: () -> Unit,
    onRefresh: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding),
    ) {
        item(key = "topbar") { HomeTopBar(onRefresh) }

        // 逐块渲染：null = 这块还没就绪，整个区块（含标题）不显示，避免未就绪
        // 时先闪出空标题/登录引导；就绪后再按是否有内容决定渲染。

        // 每日推荐歌曲（小封面单曲行）
        val daily = data.dailySongs
        if (daily != null) {
            item(key = "h_daily") { SectionHeader("每日推荐歌曲") }
            if (daily.isNotEmpty()) {
                itemsIndexed(
                    daily,
                    key = { i, t -> "daily_${t.id}_$i" },
                    contentType = { _, _ -> "track" },
                ) { i, t -> SmallTrackRow(t) { onPlay(daily, i) } }
            } else {
                item(key = "login_daily") { LoginPrompt(onWebLogin) }
            }
        }

        // 推荐歌单（大封面横滑卡片）
        val playlists = data.playlists
        if (playlists.isNullOrEmpty().not()) {
            item(key = "h_pl") { SectionHeader("推荐歌单") }
            item(key = "row_pl") {
                CarouselRow(
                    items = playlists,
                    keyOf = { it.id },
                    coverOf = { it.coverUrl },
                    nameOf = { it.name },
                ) { p, rect ->
                    onExpand(ExpandTarget("playlist", p.id, p.name, p.coverUrl, rect))
                }
            }
        }

        // 排行榜（大封面横滑卡片）
        val charts = data.charts
        if (charts.isNullOrEmpty().not()) {
            item(key = "h_chart") { SectionHeader("排行榜") }
            item(key = "row_chart") {
                CarouselRow(
                    items = charts,
                    keyOf = { it.id },
                    coverOf = { it.coverUrl },
                    nameOf = { it.name },
                ) { c, rect ->
                    onExpand(ExpandTarget("chart", c.id, c.name, c.coverUrl, rect))
                }
            }
        }

        // 最近播放（小封面单曲行）
        val recent = data.recentSongs
        if (recent != null) {
            item(key = "h_recent") { SectionHeader("最近播放") }
            if (recent.isNotEmpty()) {
                itemsIndexed(
                    recent,
                    key = { i, t -> "recent_${t.id}_$i" },
                    contentType = { _, _ -> "track" },
                ) { i, t -> SmallTrackRow(t) { onPlay(recent, i) } }
            } else {
                item(key = "login_recent") { LoginPrompt(onWebLogin) }
            }
        }
    }
}

@Composable
private fun HomeTopBar(onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "探索",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRefresh) {
            Icon(Icons.Filled.Refresh, contentDescription = "刷新", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** 大封面横滑卡片行（歌单 / 榜单）。 */
@Composable
private fun <T> CarouselRow(
    items: List<T>,
    keyOf: (T) -> Any,
    coverOf: (T) -> String?,
    nameOf: (T) -> String,
    onClick: (T, Rect) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { keyOf(it) }) { item ->
            BigCoverCard(
                coverUrl = coverOf(item),
                name = nameOf(item),
            ) { rect -> onClick(item, rect) }
        }
    }
}

private val BigCoverSize = 116.dp

@Composable
private fun BigCoverCard(
    coverUrl: String?,
    name: String,
    onClick: (Rect) -> Unit,
) {
    var rect by remember { mutableStateOf<Rect?>(null) }
    Box(
        Modifier
            .size(BigCoverSize)
            .onGloballyPositioned { coords ->
                rect = Rect(coords.localToWindow(Offset.Zero), coords.size.toSize())
            }
            .clip(RoundedCornerShape(16.dp))
            .clickable { rect?.let(onClick) },
    ) {
        BigCoverVisual(coverUrl, name, Modifier.fillMaxSize())
    }
}

/** 小封面单曲行：点了直接播（无展开动效）。 */
@Composable
private fun SmallTrackRow(track: Track, onClick: () -> Unit) {
    val context = LocalContext.current
    val model = remember(track.artworkUrl) {
        track.artworkUrl?.let { ImageRequest.Builder(context).data(it).size(96).build() }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            if (model != null) {
                val painter = rememberAsyncImagePainter(model)
                ShimmerImagePlaceholder(painter, Modifier.matchParentSize())
                Image(
                    painter = painter,
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
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (track.artist.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LoginPrompt(onLogin: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onLogin)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "登录后解锁",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "去登录 ›",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** 大封面卡 → 全屏列表：从卡片位置伸展；封面是列表第一项（banner 头），随列表滚动移出。
 *  contentFromStart：内容从头可见，p=0 时方形封面恰好等于卡片、随壳生长，收起精确缩回。 */
@Composable
private fun HomeExpandShell(
    target: ExpandTarget,
    bottomPadding: Dp,
    onOpenPlayer: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val statusBarTopPx = with(density) { WindowInsets.statusBars.getTop(this).toFloat() }
    ExpandableShell(
        fromRect = target.rect,
        fullTopPx = statusBarTopPx,
        shapeCornerDp = 16.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        contentFromStart = true,
        onDismiss = onDismiss,
        header = {},
        content = {
            Box(Modifier.fillMaxSize()) {
                TrackListScreen(
                    source = target.source,
                    id = target.id,
                    title = target.title,
                    onBack = onDismiss,
                    onOpenPlayer = onOpenPlayer,
                    showTopBar = false,
                    bottomPadding = bottomPadding,
                )
                // 关闭按钮：浮在左上、不随列表滚，随展开进度淡入（p=0 不可见、不响应点击）。
                val p = LocalShellProgress.current.value
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .size(36.dp)
                        .graphicsLayer { alpha = p }
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.38f))
                        .clickable(enabled = p > 0.5f, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "收起",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        },
    )
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/* ── 加载骨架 ─────────────────────────────────────────── */

/** 探索页骨架：与 [HomeContent] 同构——顶栏 + 区块标题 + 横滑大封面卡 + 单曲行。 */
@Composable
private fun HomeSkeleton(bottomPadding: Dp) {
    Column(
        Modifier
            .fillMaxSize()
            .shimmer()
            .padding(bottom = bottomPadding),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonLine(widthFraction = 0.24f, height = 28.dp, shape = RoundedCornerShape(8.dp))
            Spacer(Modifier.weight(1f))
            SkeletonBox(Modifier.size(28.dp), CircleShape)
        }

        SkeletonSectionHeader()
        repeat(3) { SkeletonTrackRow(artSize = 52.dp) }

        SkeletonSectionHeader()
        SkeletonCarousel()

        SkeletonSectionHeader()
        SkeletonCarousel()

        SkeletonSectionHeader()
        repeat(3) { SkeletonTrackRow(artSize = 52.dp) }
    }
}

@Composable
private fun SkeletonSectionHeader() {
    SkeletonLine(
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
        widthFraction = 0.3f,
        height = 22.dp,
        shape = RoundedCornerShape(7.dp),
    )
}

@Composable
private fun SkeletonCarousel() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(3) { SkeletonBox(Modifier.size(BigCoverSize), RoundedCornerShape(16.dp)) }
    }
}

@Composable
private fun SkeletonTrackRow(artSize: Dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBox(Modifier.size(artSize), RoundedCornerShape(8.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonLine(widthFraction = 0.55f, height = 14.dp)
            SkeletonLine(widthFraction = 0.3f, height = 12.dp)
        }
    }
}
