package com.thripleq.nume.ui.playerbar

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.thripleq.nume.Home
import com.thripleq.nume.Profile
import com.thripleq.nume.Search
import com.thripleq.nume.core.playback.PlayerHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** The top-level tabs shown in the docked capsule. */
enum class BottomTab(val route: Any, val label: String, val icon: ImageVector) {
    ExploreTab(route = Home, label = "探索", icon = Icons.Filled.Explore),
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

/** 播放页「打开程度」：0 = 完全收起在屏下，1 = 盖满全屏。 */
private const val OPEN_THRESHOLD = 0.25f

/** 松手时向上甩的速度（px/s）超过它则视为想打开。 */
private const val FLING_UP_PPS = 500f

/** spring 动画参数：打开时略带弹性，收起时干净无回弹。 */
private val SPRING_OPEN = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMedium,
)
private val SPRING_CLOSE = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium,
)

/**
 * 播放页状态：dock 与全屏播放页**共用同一份**进度，但只供全屏面在
 * graphicsLayer 里读（几何）→ 不触发重组；组合与否只由 [open] 显式布尔决定。
 *
 * 手势写入口集中在 [beginDrag]/[dragTo]/[settle]（迷你条上滑 1:1 跟手），
 * 外部入口 [open]（点击迷你条/列表项整页弹开）、[close]（收起箭头/返回键）。
 */
class PlayerDockState internal constructor(private val scope: CoroutineScope) {
    /** 0..1，播放页升起程度。只应在 draw 阶段（graphicsLayer）读，勿在组合读。 */
    val progress = Animatable(0f)

    /** 全屏播放面是否在组合中：进入会话即 true，完全收起（尾帧落地）后才复位。 */
    var open by mutableStateOf(false)
        private set

    private var animJob: Job? = null

    /** 点击迷你条/列表项/我的：整页动画弹出。 */
    fun open() {
        animJob?.cancel()
        animJob = scope.launch {
            if (!open) {
                open = true
                progress.snapTo(0f)
            }
            progress.animateTo(1f, SPRING_OPEN)
        }
    }

    /** 收起箭头/返回键。 */
    fun close() {
        animJob?.cancel()
        animJob = scope.launch { runClose() }
    }

    /** 迷你条上滑过 slop：全屏面先组合（此时 p=0，整块在屏下不可见），等 [dragTo] 跟手。 */
    fun beginDrag() {
        if (!open) open = true
    }

    /** 1:1 跟手：把升起程度直接设到 [lift]（0..1）。目标是绝对位置，先后顺序无关，不会抖动。 */
    fun dragTo(lift: Float) {
        animJob?.cancel()
        scope.launch { progress.snapTo(lift.coerceIn(0f, 1f)) }
    }

    /** 松手吸附：到阈值/甩速则弹满，否则回落收起（尾帧落地后才卸载）。
     * 传入 [fullHeightPx] 将甩速换算为 progress/s 供 spring 续跑，手感更自然。 */
    fun settle(velUpPxPerSec: Float, fullHeightPx: Float = 1f) {
        animJob?.cancel()
        animJob = scope.launch {
            val velProgress = (velUpPxPerSec / fullHeightPx).coerceIn(-10f, 10f)
            if (progress.value >= OPEN_THRESHOLD || velUpPxPerSec >= FLING_UP_PPS) {
                progress.animateTo(1f, SPRING_OPEN, initialVelocity = velProgress)
            } else {
                progress.animateTo(0f, SPRING_CLOSE, initialVelocity = velProgress)
                withFrameNanos {}
                withFrameNanos {}
                open = false
            }
        }
    }

    private suspend fun runClose() {
        progress.animateTo(0f, SPRING_CLOSE)
        // animateTo 返回后再等两帧，确保尾帧真正落地才卸载，避免收起闪最后一帧。
        withFrameNanos {}
        withFrameNanos {}
        open = false
    }
}

/** 在 [PlayerDock] 外部持有同一状态（NumeApp 需要列表项/我的点歌弹开播放页）。 */
@Composable
fun rememberPlayerDockState(): PlayerDockState {
    val scope = rememberCoroutineScope()
    return remember { PlayerDockState(scope) }
}

/**
 * 常驻底部 dock + 全屏播放页（合体）。
 *
 * 一个组件、一份 [state]：收起时只露底部 dock（拉手+迷你播放条+操作行+导航），
 * 迷你条**上滑 1:1** 跟手把全屏播放面从底部拉出盖满屏；点击直接整页弹出。
 * 全屏面只在 draw（graphicsLayer）读 progress，绝不因动画数值重组/挂载（上次翻车的坑）。
 */
@Composable
fun PlayerDock(
    player: Player,
    state: PlayerDockState,
    selected: BottomTab,
    onSelectTab: (BottomTab) -> Unit,
    /** 列表详情页操作行是否顶替迷你条上方的空间。 */
    actionVisible: Boolean = false,
    onPlayAll: () -> Unit = {},
    onPlaceholderAction: () -> Unit = {},
    /** dock 总高（dp）实时上报，供上层内容避让/Profile 展开壳让位。 */
    onIslandHeightChange: (Float) -> Unit = {},
) {
    val playerState = rememberPlayerState(player)
    // 进度是高频状态：单独订阅，只有进度条随 250ms 轮询重组。
    val positionState = rememberPlayerPosition(player)
    val density = LocalDensity.current
    val shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    val barHeight = 68.dp
    val actionHeight = 57.dp

    // 全屏播放面需盖满整个窗口（含状态栏），供几何与手势换算共用同一分母。
    val fullHeightPx = with(density) {
        LocalConfiguration.current.screenHeightDp.dp.toPx()
    }.coerceAtLeast(1f)

    // 总高上报：实际测量 dock 高度（含底部手势条 inset）。
    var dockHeightPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(dockHeightPx) {
        if (dockHeightPx > 0) onIslandHeightChange(with(density) { dockHeightPx.toDp() }.value)
    }

    Box(Modifier.fillMaxSize()) {
        // ---- 底部常驻 dock（画在全屏面下层，被它盖住；progress 只用于淡出，draw 读取）----
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .shadow(2.dp, shape, clip = false)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .graphicsLayer { alpha = (1f - state.progress.value).coerceIn(0f, 1f) }
                .onSizeChanged { dockHeightPx = it.height },
        ) {
            // 迷你播放条：点击进播放页；上滑 1:1 拉出播放页；左右滑切歌。
            PlayerBar(
                state = state,
                playerState = playerState,
                positionState = positionState,
                player = player,
                fullHeightPx = fullHeightPx,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )

            // 列表详情页操作行（滚动把头部按钮顶出视口时显示）。
            AnimatedVisibility(visible = actionVisible) {
                Column(Modifier.fillMaxWidth().height(actionHeight)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
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

            // 分隔线 = 播放条与导航之间的分隔线（内缩与胶囊对齐）。
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
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
                        .height(64.dp),
                )
            }
        }

        // ---- 全屏播放面（最上层）：open 才组合；p=0 整块沉在屏下（不可见/不可点）----
        if (state.open) {
            PlayerPage(
                state = state,
                player = player,
                fullHeightPx = fullHeightPx,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Mini player bar: cover + metadata + tap/vertical-drag/horizontal-swipe.
 *
 * 手势仲裁（单 pointerInput，手动 awaitEachGesture）：
 * - 未过 touch slop 就抬手 → 点击（开全屏播放页）
 * - 竖向为主 → 1:1 拉起播放页，松手按进度/甩速吸附
 * - 横向为主 → 不消费，交给横滑切歌
 * - 子节点已消费（播放/暂停按钮）→ 立即退出，不当作点击
 */
@Composable
private fun PlayerBar(
    state: PlayerDockState,
    playerState: PlayerUiState,
    positionState: State<Long>,
    player: Player,
    fullHeightPx: Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val swipeThresholdPx = with(density) { SWIPE_THRESHOLD_DP.dp.toPx() }
    val capsule = RoundedCornerShape(22.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(3.dp, capsule, clip = false)
            .clip(capsule)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            // 横滑切歌：独立 detector；竖向/点击见下方手动手势。
            .pointerInput(player) {
                var accumulated = 0f
                detectHorizontalDragGestures(
                    onDragStart = { accumulated = 0f },
                    onDragEnd = {
                        when {
                            accumulated <= -swipeThresholdPx -> {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                PlayerHolder.skipNext(player)
                            }
                            accumulated >= swipeThresholdPx -> {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                PlayerHolder.skipPrevious(player)
                            }
                        }
                    },
                    onHorizontalDrag = { _, dragAmount -> accumulated += dragAmount },
                )
            }
            // 手动手势：点击 / 竖向拉起播放页（同一 pointerInput 仲裁，避免多 detector 抢 slop）。
            .pointerInput(player, fullHeightPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.isConsumed) return@awaitEachGesture // 播放/暂停等子按钮吃掉按下
                    val id = down.id
                    val slop = viewConfiguration.touchSlop
                    val tracker = VelocityTracker()
                    // 0 = 未定轴，1 = 竖向（拉起播放页），2 = 横向（交还切歌 detector）
                    var axis = 0
                    var vertCum = 0f
                    var horizCum = 0f

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == id } ?: break
                        // 已被子节点或横滑 detector 消费（播放按钮、切歌）→ 不当作点击/拉起
                        if (change.isConsumed) break

                        if (!change.pressed) {
                            // 抬手：没走出 slop = 点击；否则若在竖向会话里 → 吸附
                            if (axis == 0) {
                                state.open()
                            } else if (axis == 1) {
                                state.settle(-tracker.calculateVelocity().y, fullHeightPx)
                            }
                            break
                        }

                        tracker.addPosition(change.uptimeMillis, change.position)
                        val dx = change.position.x - change.previousPosition.x
                        val dy = change.position.y - change.previousPosition.y

                        if (axis == 0) {
                            vertCum += dy
                            horizCum += dx
                            if (abs(vertCum) >= slop || abs(horizCum) >= slop) {
                                axis = if (abs(vertCum) >= abs(horizCum)) 1 else 2
                                if (axis == 1) state.beginDrag()
                            }
                        }

                        when (axis) {
                            1 -> {
                                // 竖向：消费并让播放面跟手（手指上移 dy<0 → lift 增大）
                                change.consume()
                                state.dragTo(-vertCum / fullHeightPx)
                            }
                            2 -> break // 横向交给切歌 detector，本会话结束
                        }
                    }
                }
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
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                playerState.coverUrl?.let { uri ->
                    val model = remember(uri) {
                        ImageRequest.Builder(context)
                            .data(Uri.parse(uri))
                            .size(120)
                            .build()
                    }
                    AsyncImage(
                        model = model,
                        contentDescription = playerState.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = playerState.title.ifEmpty { "暂无播放" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (playerState.hasTrack) MaterialTheme.colorScheme.onSurface
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = playerState.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            if (playerState.hasTrack) {
                SpectrumPlaceholder()
                Spacer(Modifier.width(8.dp))
            }
            IconButton(onClick = { PlayerHolder.togglePlay(player) }) {
                Icon(
                    imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playerState.isPlaying) "暂停" else "播放",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // 进度条：唯一读 positionState 的组合，250ms 轮询只让它重组。
        if (playerState.hasTrack) {
            MiniProgressBar(
                state = playerState,
                positionState = positionState,
                modifier = Modifier.fillMaxWidth(),
            )
        }
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

/** The tab row: evenly split tabs, selected one on a theme-color pill.
 * M3 Expressive 导航栏：选中 = secondaryContainer pill + onSecondaryContainer 图标,
 * 未选 = onSurfaceVariant 灰图标, 图标+标签竖排。 */
@Composable
private fun NavRow(
    selected: BottomTab,
    onSelect: (BottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pill = RoundedCornerShape(50)
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(pill)
                    .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelect(tab)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

/** 列表操作行：与 [NavRow] 同构——均分岛宽、胶囊圆角弧与岛平行、图标居中。
 *  播放 = secondaryContainer 胶囊（对应导航"选中"pill）；收藏 / 评论 = 透明（对应"未选中"）。 */
@Composable
private fun ActionNavRow(
    onPlayAll: () -> Unit,
    onPlaceholderAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pill = RoundedCornerShape(50)
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPlayAll()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "播放",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
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

/** 全屏播放面：盖满窗口、最上层、沉在屏下时不可见。 */
@Composable
private fun PlayerPage(
    state: PlayerDockState,
    player: Player,
    fullHeightPx: Float,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { SWIPE_THRESHOLD_DP.dp.toPx() }

    // 进入沉浸：隐藏系统导航栏，收起时恢复。保留状态栏。
    val view = LocalView.current
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        if (controller != null) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.navigationBars())
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.navigationBars())
        }
    }
    // 返回键收起（仅全屏面在场时生效）。
    BackHandler { state.close() }

    fun doClose() {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        state.close()
    }

    // 拦截触摸：避免盖住下面的导航/内容还能点到（无 pointer 的 Box 会让触摸穿透）。
    Box(
        modifier = modifier
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
            ) { }
            .graphicsLayer { translationY = fullHeightPx * (1f - state.progress.value) },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // 顶部：拉手 + 收起箭头。
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .padding(top = 6.dp),
                contentAlignment = Alignment.TopCenter,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            ) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { doClose() }) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "收起",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // 内容区：支持横滑切歌（与迷你条一致）。
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(player) {
                        var accumulated = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { accumulated = 0f },
                            onDragEnd = {
                                when {
                                    accumulated <= -swipeThresholdPx -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        PlayerHolder.skipNext(player)
                                    }
                                    accumulated >= swipeThresholdPx -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        PlayerHolder.skipPrevious(player)
                                    }
                                }
                            },
                            onHorizontalDrag = { _, dragAmount -> accumulated += dragAmount },
                        )
                    },
            ) {
                PlayerPageContent(player)
            }
        }
    }
}

/** 播放页主体：封面 / 标题 / slider / 控制。 */
@Composable
private fun PlayerPageContent(player: Player) {
    val context = LocalContext.current.applicationContext

    var seekPending by remember { mutableStateOf(false) }
    var dragMs by remember { mutableLongStateOf(0L) }
    val state = rememberPlayerState(player)
    // 进度是高频状态：单独订阅（拖动时冻结，避免轮询跟手指打架）。
    val positionMs by rememberPlayerPosition(player) { seekPending }

    val rangeMax = state.durationMs.toFloat().coerceAtLeast(1f)

    Column(
        modifier = Modifier
            .fillMaxSize()
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
                .background(MaterialTheme.colorScheme.surface),
        ) {
            state.coverUrl?.let { uri ->
                val model = remember(uri) {
                    ImageRequest.Builder(context).data(uri).size(560).build()
                }
                AsyncImage(
                    model = model,
                    contentDescription = state.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(Modifier.height(40.dp))

        // Track / metadata
        Text(
            text = state.title.ifEmpty { "暂无播放" },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = state.artist,
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
                    PlayerHolder.seekTo(player, dragMs)
                    seekPending = false
                },
                valueRange = 0f..rangeMax,
                enabled = state.durationMs > 0,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    formatTime(positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatTime(state.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Transport controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { PlayerHolder.skipPrevious(player) }) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "上一首",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp),
                )
            }
            IconButton(onClick = { PlayerHolder.togglePlay(player) }) {
                Icon(
                    imageVector = when {
                        state.isBuffering -> Icons.Filled.MoreHoriz
                        state.isPlaying -> Icons.Filled.Pause
                        else -> Icons.Filled.PlayArrow
                    },
                    contentDescription = if (state.isPlaying) "暂停" else "播放",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(48.dp),
                )
            }
            IconButton(onClick = { PlayerHolder.skipNext(player) }) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "下一首",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        state.errorText?.let {
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

private fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1000
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}

private const val SWIPE_THRESHOLD_DP = 56
