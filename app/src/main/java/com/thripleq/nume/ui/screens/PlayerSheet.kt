package com.thripleq.nume.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.thripleq.nume.core.playback.PlayerHolder
import com.thripleq.nume.ui.playerbar.rememberPlayerPosition
import com.thripleq.nume.ui.playerbar.rememberPlayerState
import kotlinx.coroutines.launch

/**
 * 全屏播放页（独立覆盖层，与底部 dock 零耦合）。
 *
 * 挂在 NumeApp 最上层：由外部开关决定组合，内部自管动画，与 dock 无任何共享状态。
 * - 打开：组合即播 [lift] 0→1，整块全屏壳从屏幕底部升起盖满全屏。
 * - 关闭：拉手/返回键 → [lift] 1→0 滑下，动画结束回调 [onDismiss]，外部卸载。
 */
@Composable
fun PlayerSheet(
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    // 全屏壳高度 = 整个窗口高（含状态栏区），盖满不留缝。
    val fullHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }.coerceAtLeast(1f)

    // 升起动画：0=整块在屏下，1=盖满全屏。
    val lift = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        lift.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
    }
    val scope = rememberCoroutineScope()
    fun close() {
        scope.launch {
            lift.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
            onDismiss()
        }
    }
    BackHandler { close() }

    // 全屏沉浸：进入隐藏系统导航栏，卸载（收起）时恢复。保留状态栏。
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

    // 顶层覆盖：拦截触摸、盖住底下 dock 与页面。
    Box(Modifier.fillMaxSize()) {
        // 全屏壳：translationY 从 fullHeightPx 降到 0，即从底部升起盖满。
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = fullHeightPx * (1f - lift.value)
                }
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
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
                    IconButton(onClick = { close() }) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = "收起",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    PlayerSheetContent()
                }
            }
        }
    }
}

/** 播放页主体：封面 / 标题 / slider / 控制。背景透明，由壳的胶囊色承接。 */
@Composable
private fun PlayerSheetContent() {
    val context = LocalContext.current.applicationContext
    val player = remember { PlayerHolder.get(context) }

    var seekPending by remember { mutableStateOf(false) }
    var dragMs by remember { mutableLongStateOf(0L) }
    // Shared player observation (same source the mini player bar uses): seeded
    // with live state, so the play/pause button reflects reality the moment this
    // screen opens instead of defaulting to "not playing".
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
            text = state.title.ifEmpty { "未设置歌曲" },
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

@Composable
private fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1000
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
