package com.thripleq.nume.ui.playerbar

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars

import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.launch

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
 * 落地常驻胶囊岛：全宽贴底、仅顶部圆角、surfaceContainerHighest 胶囊色。
 *
 * 结构（自顶向下）：拉手 → 播放状态栏 [PlayerBar] → 列表操作行 [actionVisible] →
 * 底部导航行（常驻）。
 *
 * ## 分段上拉（问题 2 修正）
 * 拉手垂直拖动是**分段**的，且全程跟手：
 * - 第一段（0 → [barHeight]）：拉出 / 收回播放状态栏。播放条**默认隐藏**、
 *   **无曲目也存在**（只是内容为空壳，点击仍可进播放页）。
 * - 第一段拉满后再继续上拉 → 拉出全屏播放页（[onPullUp]）。
 * - 下拉同理：播放页未开时先收播放条。
 *
 * 拖动中岛高由 [Animatable] `snapTo` 实时跟随手指（问题 3 修正）；松手后弹簧回弹到
 * 最近锚点。`playerBarVisible`（NumeApp 持有）是**松手后的定格态**，只在拖动结束
 * 时更新，避免拖到一半底部 inset 跳变。
 */
@Composable
fun PlayerCapsule(
    selected: BottomTab,
    player: Player,
    playerBarVisible: Boolean,
    onTogglePlayerBar: () -> Unit,
    onSelectTab: (BottomTab) -> Unit,
    onPullUp: () -> Unit,
    actionVisible: Boolean = false,
    onPlayAll: () -> Unit = {},
    onPlaceholderAction: () -> Unit = {},
    onIslandHeightChange: (Float) -> Unit = {},
    /** 播放页是否打开（定格全屏/跟手中）。收起时播放条淡回。 */
    sheetOpen: Boolean = false,
    /** 播放页跟手拉出进度（0..1）实时上报；松手后外部定格，不再上报。 */
    onSheetProgress: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val playerState = rememberPlayerState(player)
    // 进度是高频状态：单独订阅，只有进度条随 250ms 轮询重组，岛其余部分不动。
    val positionMs by rememberPlayerPosition(player)
    val density = LocalDensity.current
    // 落地形状：顶部圆角、底部贴地直角。
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    val handleHeight = 16.dp
    val barHeight = 60.dp
    val actionSectionHeight = 57.dp
    val navSectionHeight = 57.dp

    val basePx = with(density) { (handleHeight + navSectionHeight).toPx() }
    val actionPx = with(density) { actionSectionHeight.toPx() }
    val barPx = with(density) { barHeight.toPx() }
    // 手势导航条 inset：底部贴地后把它加进高度，补偿内部 Column 的 navigationBarsPadding，
    // 避免内容被压缩（尤其是展开播放条后）。
    val navInsetPx = with(density) {
        WindowInsets.navigationBars.getBottom(density).toFloat()
    }

    // 岛高度动画（px）：Animatable 支持跟手 snapTo；锚点 = base + action + bar(0/满)。
    val currentHeight = remember {
        Animatable(
            basePx + navInsetPx + (if (playerBarVisible) barPx else 0f),
        )
    }
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }

    // 播放条上移淡出进度（0..1）：跟手拉出播放页时 1:1 驱动；
    // 播放页定格/收起后由 NumeApp 触发回弹或淡回动画。
    val pullProgressAnim = remember { Animatable(0f) }
    val pullProgress by pullProgressAnim.asState()

    // 播放页收起（sheetOpen→false）时播放条淡回原位。
    LaunchedEffect(sheetOpen) {
        if (!sheetOpen) {
            pullProgressAnim.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
        }
    }

    // 锚点变化（playerBarVisible / actionVisible 由外部定格态驱动）时动画到位。
    LaunchedEffect(playerBarVisible, actionVisible) {
        if (!dragging) {
            val target = basePx + navInsetPx + (if (actionVisible) actionPx else 0f) +
                (if (playerBarVisible) barPx else 0f)
            currentHeight.animateTo(target, tween(320, easing = FastOutSlowInEasing))
        }
    }

    // 岛高实时上报（px→Dp）：让展开壳的底部让位跟随岛的实时高度
    // （拉播放条 / 操作行出现 / 缓冲拉伸时岛变高，壳底同步停在新岛上沿）。
    LaunchedEffect(Unit) {
        snapshotFlow { currentHeight.value }.collect { heightPx ->
            onIslandHeightChange(with(density) { heightPx.toDp() }.value)
        }
    }

    // 播放条当前露出高度（px），随拖动实时变化；clamp 到 [0, barPx]。
    // base 含手势条 inset（navInsetPx），只算到「导航行下沿」为止。
    val baseWithInset = basePx + navInsetPx
    val barShownPx = (currentHeight.value - baseWithInset - (if (actionVisible) actionPx else 0f))
        .coerceIn(0f, barPx)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(with(density) { currentHeight.value.toDp() })
            .shadow(elevation = 8.dp, shape = shape, clip = false),
    ) {
        // 背景层：圆角裁剪背景，独立于内容层——不裁剪浮出的播放条。
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            // 拉手：顶部居中的短横条；垂直拖动分段展开/收起播放条。
            // 播放页不再由拉手拉出（改由播放条上拉触发，见 PlayerBar）。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(handleHeight)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = {
                                dragging = false
                                val base = baseWithInset + (if (actionVisible) actionPx else 0f)
                                // 定格：松开时高度过半 → 播放条留下。
                                val settled = currentHeight.value >= base + barPx / 2
                                if (settled != playerBarVisible) onTogglePlayerBar()
                                scope.launch {
                                    currentHeight.animateTo(
                                        base + (if (settled) barPx else 0f),
                                        tween(260, easing = FastOutSlowInEasing),
                                    )
                                }
                            },
                            onVerticalDrag = { _, dragAmount ->
                                val base = baseWithInset + (if (actionVisible) actionPx else 0f)
                                val max = base + barPx
                                val raw = currentHeight.value - dragAmount
                                // 播放条已拉满后继续上拉不再处理（播放页由播放条手势接管）。
                                if (raw <= max) {
                                    val next = raw.coerceIn(base, max)
                                    scope.launch { currentHeight.snapTo(next) }
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        ),
                )
            }

            // 播放条占位：布局保持「拉手 → 播放条 → 操作行 → 导航行」顺序；
            // 实际播放条绘制在顶层（见下），可越界浮出不被裁剪。
            Spacer(Modifier.fillMaxWidth().height(with(density) { barShownPx.toDp() }))

            // 列表详情页的操作行：滚过头按钮时在导航行上方出现。
            if (actionVisible) {
                Column(Modifier.fillMaxWidth().height(actionSectionHeight)) {
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

            // 底部导航行：常驻。缓冲拉伸时上面的空白由 weight(1f) 吸收，
            // 导航行钉在底部、上段（拉手/播放条）被顶起，避免内容被压。
            Column(Modifier.fillMaxWidth().height(navSectionHeight)) {
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
            Spacer(Modifier.weight(1f))
        }

        // 播放状态栏（顶层，越界浮出不被岛 clip 裁剪）：
        // 高度随分段跟手变化；上拉播放页时 1:1 跟手上移淡出。
        // 按住播放条上拉 → 拉出播放页（跟手）。
        PlayerBar(
            state = playerState,
            positionMs = positionMs,
            player = player,
            onClick = onPullUp,
            pullProgress = pullProgress,
            onPullProgress = { p ->
                // 跟手 1:1：播放条上移淡出与播放页壳长高共用同一进度。
                scope.launch { pullProgressAnim.snapTo(p) }
                onSheetProgress(p)
            },
            onPullCommit = { p ->
                // 松手 settle：过半定格全屏，否则回弹（壳缩回 + 播放条淡回）。
                if (p >= 0.5f) {
                    onPullUp()
                } else {
                    onSheetProgress(0f)
                    scope.launch {
                        pullProgressAnim.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
                    }
                }
            },
            modifier = Modifier
                .offset(y = handleHeight)
                .fillMaxWidth()
                .height(with(density) { barShownPx.toDp() }),
        )
    }
}

/** Mini player bar: cover + metadata + swipe-to-skip + play/pause + thin progress.
 *
 *  上移淡出：拉出播放页时（[pullProgress] 0→1）播放条向上平移并淡出，
 *  播放页壳从底部 1:1 跟手升起盖过它。竖向拖动 1:1 映射为拉出进度。
 */
@Composable
private fun PlayerBar(
    state: PlayerUiState,
    positionMs: Long,
    player: Player,
    onClick: () -> Unit,
    pullProgress: Float,
    onPullProgress: (Float) -> Unit,
    onPullCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { SWIPE_THRESHOLD_DP.dp.toPx() }
    // 播放页壳从底部升到全屏（状态栏下沿）所需位移；与 PlayerSheet 用同一算法，
    // 保证跟手 1:1（进度 = 手指位移 / 此距离）。
    val configuration = LocalConfiguration.current
    val sheetFullHeightPx = with(density) {
        val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
        val windowBottom = configuration.screenHeightDp.dp.toPx()
        (windowBottom - statusTop).coerceAtLeast(1f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                // 播放条跟手上移淡出：上移量 = 手指累计位移（1:1 跟手），
                // 淡出与播放页进度同步 —— 播放页追上盖过它时已淡出。
                translationY = -pullProgress * sheetFullHeightPx
                alpha = 1f - pullProgress
            }
            .clickable(onClick = onClick)
            .pointerInput(player) {
                // 上拉拉出播放页：累计上移距离 → 进度（1:1）。
                var pullAccum = 0f
                detectVerticalDragGestures(
                    onDragStart = { pullAccum = 0f },
                    onDragEnd = {
                        onPullCommit((pullAccum / sheetFullHeightPx).coerceIn(0f, 1f))
                    },
                    onVerticalDrag = { _, dragAmount ->
                        pullAccum += -dragAmount
                        onPullProgress((pullAccum / sheetFullHeightPx).coerceIn(0f, 1f))
                    },
                )
            }
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
            if (state.durationMs > 0) (positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
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
) {    val pill = RoundedCornerShape(10.dp)
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
