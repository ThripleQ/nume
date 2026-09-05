package com.thripleq.nume.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets

import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.thripleq.nume.core.playback.PlayerHolder
import com.thripleq.nume.ui.playerbar.rememberPlayerPosition
import com.thripleq.nume.ui.playerbar.rememberPlayerState
import kotlinx.coroutines.launch

/**
 * 全屏播放页：从屏幕底部「长出来」（下边缘钉底、上边缘跟手）成沉浸全屏。
 *
 * ## 跟手契约（与 PlayerBar 共用同一进度算法，保证 1:1）
 * [progress] 由播放条上拉手势实时驱动（0..1，1=全屏顶到状态栏下沿）。
 * 壳下边缘钉在屏幕底，高度 = progress × 全屏高 —— 手指上移多少、壳顶就上移多少。
 * 播放条（在壳底下）同时跟手上移淡出，壳追上盖过它。
 *
 * - progress != null（跟手）：壳 snap 到手指位置（长到手指高度）。
 * - progress == null 且经历跟手（松手定格）：壳动画续长到全屏。
 * - progress == 0（回弹）：壳缩回屏下，完成后 [onDismiss]。
 * - 未经历跟手（点击播放条直接打开）：默认长满全屏动画。
 */
@Composable
fun PlayerSheet(
    onDismiss: () -> Unit,
    /** 外部受控展开进度（0..1）：跟手拉出时手势驱动；null 用内部动画。 */
    progress: Float? = null,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val configuration = LocalConfiguration.current
    // 沉浸：顶到状态栏下沿（保留状态栏时间区，内容从下沿开始）。
    val fullTopPx = with(density) { WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx() }
    val sheetFullHeightPx = with(density) {
        val windowBottom = configuration.screenHeightDp.dp.toPx()
        (windowBottom - fullTopPx).coerceAtLeast(1f)
    }

    // 壳高度动画（0..1 → 全屏高的比例）：跟手 snap；定格/回弹时动画。
    val lift = remember { Animatable(0f) }
    var hasFollowed by remember { mutableStateOf(false) }
    val displayProgress by lift.asState()
    val progressValue = progress ?: displayProgress
    val shellHeightPx = sheetFullHeightPx * progressValue

    // 跟手 snap。
    LaunchedEffect(progress) {
        if (progress != null) {
            hasFollowed = true
            lift.snapTo(progress)
        }
    }
    // settle：progress→null 定格全屏；progress→0 回弹缩回后卸载。
    LaunchedEffect(progress) {
        when {
            progress == null && hasFollowed ->
                lift.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
            progress == 0f && hasFollowed -> {
                lift.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
                onDismiss()
            }
        }
    }
    // 未跟手直接打开：默认长满动画。
    if (!hasFollowed) {
        LaunchedEffect(Unit) {
            lift.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        }
    }

    // 收起（拉手/返回）：壳缩回后卸载。
    val scope = rememberCoroutineScope()
    fun startClose() {
        scope.launch {
            lift.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
            onDismiss()
        }
    }

    BackHandler { startClose() }

    // 全屏占位：拦截触摸、盖住底下页面。
    Box(Modifier.fillMaxSize()) {
        // 壳：下边缘钉在屏幕底，高度随 progress 增长，上边缘跟手。
        Box(
            Modifier
                .fillMaxWidth()
                .height(with(density) { shellHeightPx.toDp() })
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    alpha = progressValue.coerceIn(0f, 1f)
                }
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Column(Modifier.fillMaxSize().padding(vertical = 4.dp)) {
                // 顶部拉手：与岛同款短横条，点击收起回岛。
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
                    IconButton(onClick = { startClose() }) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = "收起",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    PlayerSheetContent(onOpenPlayerClose = { startClose() })
                }
            }
        }
    }
}

/** 播放页主体：封面 / 标题 / slider / 控制。背景透明，由壳的胶囊色承接。 */
@Composable
private fun PlayerSheetContent(onOpenPlayerClose: () -> Unit) {
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

    BackHandler { onOpenPlayerClose() }

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
                AsyncImage(
                    model = Uri.parse(uri),
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
                Text(formatTime(positionMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatTime(state.durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
