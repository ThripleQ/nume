package com.thripleq.nume.ui.playerbar

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
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
import kotlinx.coroutines.flow.collect
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

/** 播放页「打开程度」：0 = 完全收起在迷你条胶囊，2 = 盖满全屏。
 *  两段式：p∈[0,1] 胶囊原位展开成悬浮卡（dock 保持可见）；
 *  p∈[1,2] 卡片放大盖满全屏（dock 淡出）。 */

/** spring 动画参数：打开时略带弹性，收起时干净无回弹。 */
private val SPRING_OPEN = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMedium,
)
private val SPRING_CLOSE = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium,
)

/** 播放页「档位」：三档——收起(dock 胶囊) / 卡片 / 全屏。 */
enum class PlayerSheet { Closed, Half, Full }

/**
 * 播放页状态：用官方 [AnchoredDraggableState] 管理「收起/卡片/全屏」三档之间的拖动与吸附。
 *
 * 锚点像素 = 胶囊展开进度（像素），几何是「胶囊 → 悬浮卡 → 全屏」两段 lerp：
 * - [PlayerSheet.Closed] = 0          → 壳收在迷你条胶囊原位
 * - [PlayerSheet.Half]   = travelPx   → 壳展开成悬浮卡（progress == 1）
 * - [PlayerSheet.Full]   = 2*travelPx → 壳盖满全屏（progress == 2）
 * [progress] = offset / travelPx ∈ [0,2]，在 draw 阶段读，不触发重组。
 *
 * - 手势由 `Modifier.anchoredDraggable(state)` 驱动（内部处理 slop 仲裁/松手吸附/甩动）。
 * - 组合与否只由 [open] 显式布尔决定，由 offset/settledValue 观察驱动。
 */
class PlayerDockState internal constructor(
    val sheetState: AnchoredDraggableState<PlayerSheet>,
    private val scope: CoroutineScope,
) {
    /** 全屏播放面是否在组合中：进入会话即 true，完全收起（尾帧落地）后才复位。
     *  内部由 [AnchoredDraggableState] 的 offset/settledValue 观察驱动，外部只读。 */
    var open by mutableStateOf(false)
        internal set

    /** 手势行程（px）：从迷你条胶囊到「全屏」的展开总距（卡片是它的一半）。 */
    var travelPx by mutableFloatStateOf(1f)

    /** 胶囊展开进度 0..2（[0,1]=胶囊→卡片，[1,2]=卡片→全屏），由 [AnchoredDraggableState.offset] 归一化。
     *  只应在 draw 阶段（graphicsLayer）读，勿在组合读。 */
    val progress: Float
        get() = (sheetState.offset / travelPx).coerceIn(0f, 2f)

    /** 迷你条胶囊的窗口坐标 Rect（动画起点）。由 PlayerBar 上报；拖动/收起时定格使用。 */
    var capsuleRect by mutableStateOf<Rect?>(null)
        internal set

    private var animJob: Job? = null

    /** 点击迷你条/列表项/我的：整页动画弹出。
     *  [toFull] = false 时两段式先弹到卡片（Half），可继续上滑看全屏；
     *  true 时直接盖满全屏（列表项点歌/点击迷你条用）。 */
    fun open(toFull: Boolean = false) {
        animJob?.cancel()
        animJob = scope.launch {
            if (!open) {
                open = true
                sheetState.snapTo(PlayerSheet.Closed)
            }
            // 卡片用干净弹簧（无回弹过冲，卡到位即停）；全屏才用弹性。
            sheetState.animateTo(
                if (toFull) PlayerSheet.Full else PlayerSheet.Half,
                if (toFull) SPRING_OPEN else SPRING_CLOSE,
            )
        }
    }

    /** 收起箭头/返回键：无论当前在哪个档位，都缩回迷你条。 */
    fun close() {
        animJob?.cancel()
        animJob = scope.launch { runClose() }
    }

    private suspend fun runClose() {
        sheetState.animateTo(PlayerSheet.Closed, SPRING_CLOSE)
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
    // 官方 AnchoredDraggableState：三锚点（收起/半高/全屏），锚点像素值（offset）在
    // PlayerDock 布局后由 updateAnchors 填充（依赖 dock 高/屏高）。初始只有一个锚点。
    val sheetState = remember {
        AnchoredDraggableState(
            initialValue = PlayerSheet.Closed,
            anchors = DraggableAnchors {
                PlayerSheet.Closed at 0f
            },
            positionalThreshold = { distance -> distance * 0.5f },
            velocityThreshold = { 1200f },
            snapAnimationSpec = SPRING_CLOSE,
            decayAnimationSpec = exponentialDecay(),
            confirmValueChange = { true },
        )
    }
    return remember { PlayerDockState(sheetState, scope) }
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

    // 手势 1:1 行程：手指从迷你条顶拉到全屏顶的可见距离。
    // 整屏高（2400px）里 dock 占了 ~390px，若拿整屏高做分母，手指要走满一屏 progress 才到 1，
    // 实际只能走 ~2000px → 拉满手卡片才升一小截 → 感觉"反的/费劲"。
    // 锚点像素以此为准：Closed=0、Half=travelPx*0.5、Full=travelPx。
    val travelPx = (fullHeightPx - dockHeightPx).coerceAtLeast(1f)

    // 把行程同步进 state，并把三档锚点像素注册给 AnchoredDraggableState（拖动/吸附据此 1:1）。
    // 锚点 = 胶囊展开进度（px）：Closed=0、Half=travelPx（展开成卡片）、Full=2*travelPx（盖满全屏）。
    LaunchedEffect(travelPx) {
        state.travelPx = travelPx
        state.sheetState.updateAnchors(
            DraggableAnchors {
                PlayerSheet.Closed at 0f
                PlayerSheet.Half at travelPx
                PlayerSheet.Full at 2f * travelPx
            },
            newTarget = state.sheetState.currentValue,
        )
    }

    // 组合与否由锚点状态驱动：迷你条一拖动（offset>0）就组合播放面；
    // 完全落回 dock 锚点（settled）才卸载。替代手搓的 beginDrag。
    LaunchedEffect(state.sheetState) {
        snapshotFlow { state.sheetState.offset }.collect { offset ->
            if (offset > 1f) state.open = true
        }
    }
    LaunchedEffect(state.sheetState) {
        snapshotFlow { state.sheetState.settledValue }.collect { v ->
            if (v == PlayerSheet.Closed) state.open = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        // ---- 底部常驻 dock（画在全屏面下层，被它盖住；p≤半高时保持可见，>半高才淡出）----
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .shadow(2.dp, shape, clip = false)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .graphicsLayer {
                    // 胶囊展开：卡片档（p≤1）壳悬浮在 dock 顶之上，dock 完整可见；
                    // 只有继续展开盖满全屏（p>1）才淡出 dock。
                    val p = state.progress
                    alpha = if (p <= 1f) 1f
                    else (2f - p).coerceIn(0f, 1f)
                }
                .onSizeChanged { dockHeightPx = it.height },
        ) {
            // 迷你播放条：点击进播放页；上滑 1:1 拉出播放页；左右滑切歌。
            PlayerBar(
                state = state,
                playerState = playerState,
                positionState = positionState,
                player = player,
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
                dockHeightPx = dockHeightPx.toFloat(),
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
            // 上报迷你条胶囊的窗口坐标：胶囊展开动画的起点（从胶囊原位长成卡片/全屏）。
            // 必须在 padding 之前测，bounds 才是视觉胶囊本身。
            .onGloballyPositioned { coords ->
                state.capsuleRect = Rect(coords.localToWindow(Offset.Zero), coords.size.toSize())
            }
            // 官方 anchoredDraggable：竖向把播放面拉起来（内部处理 slop 仲裁 / 松手吸附 / 甩动）。
            // reverseDirection=true：上滑（y 减小）→ offset 增大 → 展开；下滑 → 收起。
            // 迷你条在 dock 里、dock 被播放面盖住时（全屏）不可点，天然不冲突。
            .anchoredDraggable(
                state.sheetState,
                reverseDirection = true,
                orientation = Orientation.Vertical,
            )
            // 点击 → 胶囊原位展开到全屏（经过卡片矩形，一气呵成）。
            // 若拖动被 anchoredDraggable 消费，点击不会触发。
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { state.open(toFull = true) }
            // 横滑切歌：独立 detector；与竖向 anchoredDraggable 方向正交，互不干扰。
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

/** 全屏播放面（两段式）：p=0 沉在屏下；p∈(0,0.5] 悬浮卡片从 dock 顶升起
 *  （dock 保持可见可点）；p∈(0.5,1] 卡片放大盖满全屏（dock 淡出）。
 *  所有几何/圆角/压暗都在 draw 阶段（graphicsLayer + drawWithContent）读 progress，
 *  零重组；组合与否只由 [state.open] 决定（铁律）。
 */
@Composable
private fun PlayerPage(
    state: PlayerDockState,
    player: Player,
    fullHeightPx: Float,
    dockHeightPx: Float,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { SWIPE_THRESHOLD_DP.dp.toPx() }
    val fullWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val edgePx = with(density) { 10.dp.toPx() }
    val capsuleCornerPx = with(density) { 22.dp.toPx() }
    val cardColor = MaterialTheme.colorScheme.surfaceContainer

    // 三档矩形（px）：
    //   capsule = 迷你条胶囊原位（窗口坐标，展开起点；无值时用兜底几何）；
    //   card    = 悬浮卡（底距 dock 顶一个 edge、四周 10dp 边距）；
    //   full    = 盖满全屏。
    // progress ∈ [0,1]：capsule→card 插值；∈ (1,2]：card→full 插值。两段在 p=1 处连续。
    fun lerpRect(a: androidx.compose.ui.geometry.Rect, b: androidx.compose.ui.geometry.Rect, t: Float) =
        androidx.compose.ui.geometry.Rect(
            a.left + (b.left - a.left) * t,
            a.top + (b.top - a.top) * t,
            a.right + (b.right - a.right) * t,
            a.bottom + (b.bottom - a.bottom) * t,
        )

    fun shellRect(p: Float): androidx.compose.ui.geometry.Rect {
        val card = androidx.compose.ui.geometry.Rect(
            edgePx, edgePx,
            fullWidthPx - edgePx, fullHeightPx - dockHeightPx - edgePx,
        )
        val full = androidx.compose.ui.geometry.Rect(0f, 0f, fullWidthPx, fullHeightPx)
        val capsule = state.capsuleRect ?: androidx.compose.ui.geometry.Rect(
            edgePx, fullHeightPx - dockHeightPx + edgePx,
            fullWidthPx - edgePx, fullHeightPx,
        )
        // 两段插值，p=1 处首尾相连（t0=1 时 card，t1=0 时也是 card）。
        val t0 = (p / 1f).coerceIn(0f, 1f)          // [0,1] capsule→card
        val t1 = ((p - 1f) / 1f).coerceIn(0f, 1f)   // [1,2] card→full
        return lerpRect(lerpRect(capsule, card, t0), full, t1)
    }

    // 沉浸：只在接近全屏时隐藏系统导航栏（半高时保持显示，dock 的 navigationBarsPadding
    // 布局稳定）；收起/回落到半高时恢复。用 snapshotFlow 轮询 progress，不引重组。
    val view = LocalView.current
    val activity = LocalActivity.current
    val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
    LaunchedEffect(controller) {
        if (controller != null) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            snapshotFlow { state.progress }.collect { p ->
                if (p >= 1.9f) {
                    controller.hide(WindowInsetsCompat.Type.navigationBars())
                } else {
                    controller.show(WindowInsetsCompat.Type.navigationBars())
                }
            }
        }
    }
    // 返回键收起（仅全屏面在场时生效）。
    BackHandler { state.close() }

    fun doClose() {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        state.close()
    }

    Box(
        modifier = modifier
            // 胶囊展开面：graphicsLayer 把全屏内容缩放+平移到壳矩形（胶囊→卡片→全屏），
            // drawWithContent 裁剪圆角（卡片段保持胶囊圆角，全屏段收小到 0）。都在 draw 阶段读 progress。
            // 壳是实心 surface（在 clip 内绘制），透不出底下内容，无需额外 scrim。
            .graphicsLayer {
                val r = shellRect(state.progress)
                val sx = (r.right - r.left) / fullWidthPx
                val sy = (r.bottom - r.top) / fullHeightPx
                scaleX = sx
                scaleY = sy
                translationX = r.left
                translationY = r.top
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .drawWithContent {
                val p = state.progress
                // 圆角：胶囊/卡片段（p∈[0,1]）保持胶囊观感 22dp；全屏段（p∈[1,2]）收小到 0。
                val t1 = ((p - 1f) / 1f).coerceIn(0f, 1f)
                val radius = capsuleCornerPx * (1f - t1)
                clipPath(
                    path = Path().apply {
                        addRoundRect(
                            RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)),
                        )
                    },
                ) {
                    // 实心卡片背景（在 clip 内绘制，圆角裁剪）：胶囊/卡片/全屏都铺满，透不出底下内容。
                    drawRect(color = cardColor)
                    this@drawWithContent.drawContent()
                }
            }
            // 拦截触摸：只拦卡片实际区域（graphicsLayer transform 会一并变换 hit-test），
            // 半高时 dock 区域不在此矩形内 → 仍可点。
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
            ) { }
            // 官方 anchoredDraggable：卡内上滑续开到全屏、下拉回 dock/收起。
            // 松手吸附/甩动由 AnchoredDraggableState 原生处理（位置阈值 + 速度阈值）。
            // reverseDirection=true：上滑（y 减小）→ offset 增大 → 展开。
            .anchoredDraggable(
                state.sheetState,
                reverseDirection = true,
                orientation = Orientation.Vertical,
            ),
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
