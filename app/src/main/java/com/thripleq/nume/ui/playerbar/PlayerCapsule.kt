package com.thripleq.nume.ui.playerbar

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.thripleq.nume.Home
import com.thripleq.nume.Profile
import com.thripleq.nume.Search
import com.thripleq.nume.core.playback.PlayerHolder
import kotlinx.coroutines.delay

/** The top-level tabs shown in the floating capsule. */
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
 * 状态拆成两个 snapshot：元数据/播放态（低频，标题/封面/是否播放/时长/有无曲目）
 * 和进度 positionMs（高频）。轮询只写 positionMs；meta 仅在真实变化时才写，
 * 避免每 250ms 复制整个状态对象触发无谓重组。
 * [positionFrozen] lets callers (full-screen player during a seek drag) pin the
 * reported position so the poll doesn't fight the thumb.
 */
@Composable
fun rememberPlayerState(
    player: Player,
    positionFrozen: () -> Boolean = { false },
): PlayerUiState {
    var meta by remember { mutableStateOf(PlayerUiState()) }
    var positionMs by remember { mutableLongStateOf(0L) }

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
        positionMs = player.currentPosition
        try {
            while (true) {
                if (!positionFrozen()) positionMs = player.currentPosition
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
    return meta.copy(positionMs = positionMs)
}

/**
 * 轻量订阅"播放器当前是否持有曲目"（用于岛显隐），独立于高频进度轮询：
 * 调用方（NumeApp）只订阅这里，不会随 250ms 进度刷新而重组。
 */
@Composable
fun rememberHasTrack(player: Player): Boolean {
    var hasTrack by remember { mutableStateOf(player.currentMediaItem != null) }
    LaunchedEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                hasTrack = player.currentMediaItem != null
            }
        }
        player.addListener(listener)
        hasTrack = player.currentMediaItem != null
        try {
            while (true) {
                // 兜底：占位 MediaItem 的 transition 在部分流程不触发，定期核对。
                if (hasTrack != (player.currentMediaItem != null)) {
                    hasTrack = player.currentMediaItem != null
                }
                delay(500)
            }
        } finally {
            player.removeListener(listener)
        }
    }
    return hasTrack
}

/**
 * Floating capsule island: mini player bar (top) + navigation tabs (bottom),
 * merged into one rounded-rectangle island. When [navVisible] flips to false
 * (detail pages) the whole island shrinks and the player bar slides down into
 * the nav slot; the caller hides the island entirely on the full-screen player.
 */
@Composable
fun PlayerCapsule(
    navVisible: Boolean,
    selected: BottomTab,
    player: Player,
    onSelectTab: (BottomTab) -> Unit,
    onOpenPlayer: () -> Unit,
    actionVisible: Boolean = false,
    onPlayAll: () -> Unit = {},
    onPlaceholderAction: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val playerState = rememberPlayerState(player)
    val shape = RoundedCornerShape(28.dp)
    val barHeight = 60.dp
    val navSectionHeight = 57.dp
    val actionSectionHeight = 57.dp
    val navOffsetPx = with(LocalDensity.current) { navSectionHeight.toPx() }
    val actionOffsetPx = with(LocalDensity.current) { actionSectionHeight.toPx() }

    val showBar = playerState.hasTrack
    // 操作行只出现在详情页（navVisible=false 时）：播放条先上移让位，操作行从下滑入。
    val showAction = actionVisible && !navVisible
    val targetHeight = (if (showBar) barHeight else 0.dp) +
        (if (navVisible) navSectionHeight else 0.dp) +
        (if (showAction) actionSectionHeight else 0.dp)
    val height by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "islandHeight",
    )
    // 播放条底对齐 + 负向上移：先顶到导航上方（tab 页），再被操作行挤上去（详情页滚动）。
    val barOffsetPx by animateFloatAsState(
        targetValue = (if (navVisible) -navOffsetPx else 0f) +
            (if (showAction) -actionOffsetPx else 0f),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 520f),
        label = "barOffset",
    )

    val islandWidth = LocalConfiguration.current.screenWidthDp.dp * 7f / 8f

    Box(
        modifier = modifier
            .width(islandWidth)
            .height(height)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        if (showBar) {
            PlayerBar(
                state = playerState,
                player = player,
                onClick = onOpenPlayer,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(barHeight)
                    .graphicsLayer { translationY = barOffsetPx },
            )
        }

        // 导航部分：抽屉向下滑出 / 从下方滑入。
        AnimatedVisibility(
            visible = navVisible,
            enter = fadeIn(tween(200)) + slideInVertically(tween(240), initialOffsetY = { it }),
            exit = fadeOut(tween(180)) + slideOutVertically(tween(240), targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(navSectionHeight),
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                NavRow(
                    selected = selected,
                    onSelect = onSelectTab,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }

        // 列表详情页的操作浮岛：滚过头部三按钮时，播放条先上移、操作行从下方滑入。
        AnimatedVisibility(
            visible = showAction,
            enter = fadeIn(tween(200)) + slideInVertically(tween(240), initialOffsetY = { it }),
            exit = fadeOut(tween(180)) + slideOutVertically(tween(240), targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(actionSectionHeight),
        ) {
            Column(Modifier.fillMaxSize()) {
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
    }
}

/** Mini player bar: cover + metadata + swipe-to-skip + play/pause + thin progress. */
@Composable
private fun PlayerBar(
    state: PlayerUiState,
    player: Player,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val swipeThresholdPx = with(LocalDensity.current) { SWIPE_THRESHOLD_DP.dp.toPx() }

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
                    AsyncImage(
                        model = Uri.parse(uri),
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

        val fraction =
            if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
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
) {    val pill = RoundedCornerShape(percent = 50)
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
                        MaterialTheme.colorScheme.onSurfaceVariant
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
    val pill = RoundedCornerShape(percent = 50)
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
