package com.thripleq.nume.ui.playerbar

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.thripleq.nume.Home
import com.thripleq.nume.Profile
import com.thripleq.nume.Search
import com.thripleq.nume.core.playback.PlayerHolder
import kotlinx.coroutines.delay

/** The top-level tabs shown in the docked capsule. */
enum class BottomTab(val route: Any, val label: String, val icon: ImageVector) {
    ExploreTab(route = Home, label = "探索", icon = Icons.Filled.Home),
    SearchTab(route = Search, label = "搜索", icon = Icons.Filled.Search),
    ProfileTab(route = Profile, label = "我的", icon = Icons.Filled.Person),
}

/** Live snapshot of the shared [Player] for the mini player bar. */
data class PlayerUiState(
    val title: String = "",
    val artist: String = "",
    val coverUrl: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasTrack: Boolean = false,
    val errorText: String? = null,
)

/**
 * Observes [player] (process-scoped singleton) with a metadata listener + 250ms poll.
 * 状态拆成快照：元数据/播放态（标题/封面/是否播放/时长/有无曲目）低频，
 * 仅在真实变化时才写，避免无谓重组。
 *
 * **进度条优化**：进度 positionMs 不进入返回值 —— [rememberPlayerState] 从不读它，
 * 调用方（迷你条/播放页）不会随 250ms 轮询重组。进度条这类需要逐帧更新的小部件
 * 单独订阅 [rememberPlayerPosition]，只有它随轮询重组。
 */
@Composable
fun rememberPlayerState(
    player: Player,
): PlayerUiState {
    var meta by remember { mutableStateOf(PlayerUiState()) }

    LaunchedEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                meta = meta.copy(
                    title = mediaMetadata.title?.toString() ?: "",
                    artist = mediaMetadata.artist?.toString() ?: "",
                    coverUrl = mediaMetadata.artworkUri?.toString(),
                    errorText = null,
                )
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                meta = meta.copy(isPlaying = playing)
            }

            override fun onPlaybackStateChanged(s: Int) {
                meta = meta.copy(isBuffering = s == Player.STATE_BUFFERING)
                if (s == Player.STATE_READY) {
                    meta = meta.copy(durationMs = player.duration.coerceAtLeast(0L))
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                meta = meta.copy(errorText = error.errorCodeName ?: error.message)
            }
        }
        player.addListener(listener)
        // Seed everything from the player's current state so the UI reflects
        // reality the moment it appears (e.g. already playing when the screen
        // opens) instead of defaulting to "not playing".
        val mediaMetadata = player.mediaMetadata
        meta = PlayerUiState(
            title = mediaMetadata.title?.toString() ?: "",
            artist = mediaMetadata.artist?.toString() ?: "",
            coverUrl = mediaMetadata.artworkUri?.toString(),
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            durationMs = player.duration.coerceAtLeast(0L),
            hasTrack = player.currentMediaItem != null,
        )
        try {
            while (true) {
                // hasTrack / duration 由 listener 与这里共同维护；只在真实变化时写 meta。
                val hasTrack = player.currentMediaItem != null
                val duration = player.duration.coerceAtLeast(0L)
                if (meta.hasTrack != hasTrack || meta.durationMs != duration) {
                    meta = meta.copy(hasTrack = hasTrack, durationMs = duration)
                }
                delay(250)
            }
        } finally {
            player.removeListener(listener)
        }
    }
    return meta
}

/**
 * 高频进度订阅：250ms 轮询写入 positionMs。只有读取返回 [State] 的组合
 * （迷你条进度条、播放页 Slider/时间）才随轮询重组；不读它的组合零开销。
 * [positionFrozen] 用于拖动进度时冻结位置，避免轮询跟手指打架。
 */
@Composable
fun rememberPlayerPosition(
    player: Player,
    positionFrozen: () -> Boolean = { false },
): State<Long> {
    val positionMs = remember { mutableLongStateOf(player.currentPosition) }
    LaunchedEffect(player) {
        try {
            while (true) {
                if (!positionFrozen()) positionMs.longValue = player.currentPosition
                delay(250)
            }
        } finally {
            // 协程取消（LaunchedEffect 离开组合）时自然退出，不吞 CancellationException。
        }
    }
    return positionMs
}

/**
 * 常驻底部 dock：拉手 + 迷你播放条 + (列表操作行) + 分隔线 + 底部导航行。
 *
 * 与全屏播放页**零耦合**：点迷你播放条只是触发 [onOpenPlayer]（外部置位打开开关），
 * 播放页作为独立最上层覆盖层由自己动画滑入滑出，本 dock 不做任何让位/联动动画。
 */
@Composable
fun PlayerCapsule(
    player: Player,
    selected: BottomTab,
    onSelectTab: (BottomTab) -> Unit,
    /** 列表详情页操作行是否顶替迷你条上方的空间。 */
    actionVisible: Boolean = false,
    onPlayAll: () -> Unit = {},
    onPlaceholderAction: () -> Unit = {},
    /** 点击迷你播放条 → 打开全屏播放页（唯一入口）。 */
    onOpenPlayer: () -> Unit = {},
    /** dock 总高（dp）实时上报，供上层内容避让/Profile 展开壳让位。 */
    onIslandHeightChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val playerState = rememberPlayerState(player)
    // 进度是高频状态：单独订阅，只有进度条随 250ms 轮询重组。
    val positionState = rememberPlayerPosition(player)
    val density = LocalDensity.current
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    val handleHeight = 14.dp
    val barHeight = 60.dp
    val actionHeight = 57.dp

    // 总高上报：实际测量高度（含底部手势条 inset）。
    var dockHeightPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(dockHeightPx) {
        if (dockHeightPx > 0) onIslandHeightChange(with(density) { dockHeightPx.toDp() }.value)
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .onSizeChanged { dockHeightPx = it.height },
    ) {
        // 拉手：顶部居中短横条。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(handleHeight),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
            )
        }

        // 迷你播放条：点击进播放页；左右滑切歌。
        PlayerBar(
            state = playerState,
            positionState = positionState,
            player = player,
            onClick = onOpenPlayer,
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight),
        )

        // 列表详情页操作行（滚动把头部按钮顶出视口时显示）。
        if (actionVisible) {
            Column(Modifier.fillMaxWidth().height(actionHeight)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                ActionNavRow(
                    onPlayAll = onPlayAll,
                    onPlaceholderAction = onPlaceholderAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }

        // 分隔线 = 播放条与导航之间的分隔线。
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )

        // 底部导航行：内垫手势条 inset，背景自然延伸到屏幕底。
        Box(Modifier.fillMaxWidth().navigationBarsPadding()) {
            NavRow(
                selected = selected,
                onSelect = onSelectTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(57.dp),
            )
        }
    }
}

/** Mini player bar: cover + metadata + swipe-to-skip + play/pause + thin progress.
 *
 * 常驻在 dock 顶部（拉手之下）。点击整条 → [onClick]（开全屏播放页）；左右滑切歌。
 */
@Composable
private fun PlayerBar(
    state: PlayerUiState,
    positionState: State<Long>,
    player: Player,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val swipeThresholdPx = with(density) { SWIPE_THRESHOLD_DP.dp.toPx() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .pointerInput(player) {
                var accumulated = 0f
                detectHorizontalDragGestures(
                    onDragStart = { accumulated = 0f },
                    onDragEnd = {
                        when {
                            accumulated <= -swipeThresholdPx -> PlayerHolder.skipNext(player)
                            accumulated >= swipeThresholdPx -> PlayerHolder.skipPrevious(player)
                        }
                    },
                    onHorizontalDrag = { _, dragAmount -> accumulated += dragAmount },
                )
            }
            .padding(horizontal = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                state.coverUrl?.let { uri ->
                    val model = remember(uri) {
                        ImageRequest.Builder(context)
                            .data(Uri.parse(uri))
                            .size(120)
                            .build()
                    }
                    AsyncImage(
                        model = model,
                        contentDescription = state.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.title.ifEmpty { "未设置歌曲" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = state.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            SpectrumPlaceholder()
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { PlayerHolder.togglePlay(player) }) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "暂停" else "播放",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // 进度条：唯一读 positionState 的组合，250ms 轮询只让它重组。
        MiniProgressBar(
            state = state,
            positionState = positionState,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 迷你进度条：从 [positionState] 读值，独立重组，不带动播放条/岛。 */
@Composable
private fun MiniProgressBar(
    state: PlayerUiState,
    positionState: State<Long>,
    modifier: Modifier = Modifier,
) {
    val positionMs = positionState.value
    val fraction =
        if (state.durationMs > 0) (positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    Box(
        modifier = modifier.height(3.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/** Reserved slot for the future live spectrum; static bars for now. */
@Composable
private fun SpectrumPlaceholder() {
    Box(
        modifier = Modifier.size(width = 36.dp, height = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            listOf(8.dp, 16.dp, 10.dp).forEach { h ->
                Box(
                    Modifier
                        .width(4.dp)
                        .height(h)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
    }
}

/** The tab row: evenly split tabs, selected one on a theme-color pill. */
@Composable
private fun NavRow(
    selected: BottomTab,
    onSelect: (BottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pill = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier.padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(pill)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.label,
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/** 列表操作行：与 [NavRow] 同构——均分岛宽、胶囊圆角弧与岛平行、图标居中。
 *  播放 = primary 胶囊（对应导航"选中"pill）；收藏 / 评论 = 透明（对应"未选中"）。 */
@Composable
private fun ActionNavRow(
    onPlayAll: () -> Unit,
    onPlaceholderAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pill = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier.padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(pill)
                .background(Color.Transparent)
                .clickable { onPlaceholderAction() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "收藏",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(pill)
                .background(MaterialTheme.colorScheme.primary)
                .clickable { onPlayAll() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "播放",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(24.dp),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(pill)
                .background(Color.Transparent)
                .clickable { onPlaceholderAction() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Chat,
                contentDescription = "评论",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

private const val SWIPE_THRESHOLD_DP = 120
