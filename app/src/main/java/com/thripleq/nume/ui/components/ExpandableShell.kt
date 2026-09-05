package com.thripleq.nume.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 通用「胶囊壳 → 全屏面板」伸展覆盖层。
 *
 * 从一个胶囊的 [fromRect]（窗口坐标 Rect）平滑伸展到全屏，再缩回原位。
 * 用于「点击小胶囊 → 展开成面板」的过渡，收起动画保证最后一帧精确复位。
 *
 * ## 对齐契约（务必遵守，否则收起尾帧会跳位）
 * 1. [header] 必须与「起点胶囊的头部」是**同一个 composable**（颜色/文字/图标相对位置天然一致）。
 *    胶囊未展开时的头部由调用方渲染，展开壳顶部的标题栏由本组件渲染同一份 [header]。
 * 2. 起点胶囊的测量：`onGloballyPositioned` 的 bounds 必须是**视觉胶囊本身**——
 *    胶囊自身的 padding 不能加在 Card/bounds modifier 上，要放内部，否则 bounds 含 padding、终点偏。
 * 3. 壳内部布局必须与胶囊内部布局**同构**（同样的垂直 padding），否则内容相对壳顶的位置对不上。
 *
 * ## 打开/收起
 * - 打开：竖向、横向、内容淡入三轴并发展开。
 * - 收起：内容淡出 → 横向收窄 → 竖向缩回；全部完成后等两帧（[withFrameNanos]）让
 *   「完全复位」的画面真正绘制落地再调 [onDismiss]，避免最后一帧被跳过。
 *
 * @param fromRect  起点胶囊的窗口坐标 Rect；null 时用兜底几何（左/顶各 16dp、宽 328dp）
 * @param fullTopPx 展开后壳顶的窗口 Y（px），通常 = 状态栏下沿
 * @param shapeCornerDp  壳圆角（px 动画不插值圆角，保持胶囊观感）
 * @param containerColor 壳背景色（应与胶囊 Card 颜色一致）
 * @param recessedBottom 内容区（窟窿）底部让位量：壳本身延伸到屏幕底，
 *                       内容区底部留出此高度露出底下导航岛，与岛同色融合
 * @param progress 外部受控展开进度（0..1）：非 null 时壳几何由它插值驱动（跟手），
 *                 自动开启动画被跳过；null 时用内部动画。关闭动画从当前进度续跑。
 * @param onDismiss 关闭动画完全结束、壳复位后才回调（调用方借此移除本组件）
 * @param header    壳顶部标题栏（必须与胶囊头部同源）；接收 [onClose]，收起按钮应调它触发关闭动画
 * @param content   壳内内容区（占剩余空间）
 */
@Composable
fun ExpandableShell(
    fromRect: Rect?,
    fullTopPx: Float,
    shapeCornerDp: Dp,
    containerColor: androidx.compose.ui.graphics.Color,
    recessedBottom: Dp = 0.dp,
    progress: Float? = null,
    onDismiss: () -> Unit,
    header: @Composable (onClose: () -> Unit) -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    var viewWidth by remember { mutableStateOf(0) }
    var viewHeight by remember { mutableStateOf(0) }

    // 起点（胶囊）几何，兜底 16dp 边距 + 328dp 宽。
    val capsuleLeft = fromRect?.left ?: with(density) { 16.dp.toPx() }
    val capsuleTop = fromRect?.top ?: fullTopPx
    val capsuleWidthPx = fromRect?.width ?: with(density) { 328.dp.toPx() }
    val capsuleHeightPx = fromRect?.height ?: 0f
    // 终点（全屏）几何：左贴 0、宽满屏；顶贴 [fullTopPx]、高到屏幕底。
    // 壳本身延伸到屏幕最底部，让位只作用于内容区（窟窿）底部。
    val fullLeft = 0f
    val fullWidthPx = viewWidth.toFloat()
    val fullTop = fullTopPx
    val fullHeightPx = (viewHeight - fullTopPx).coerceAtLeast(0f)

    var closing by remember { mutableStateOf(false) }
    val vertical = remember { Animatable(0f) }
    val horizontal = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(0f) }

    // 外部受控进度：跟手时直接驱动壳几何（snap），不受内部动画干扰。
    // 关闭动画从当前进度续跑；打开动画仅在无外部进度时自动跑。
    val followProgress = progress != null
    var hasFollowed by remember { mutableStateOf(false) }
    LaunchedEffect(progress) {
        if (progress != null) {
            hasFollowed = true
            vertical.snapTo(progress)
            horizontal.snapTo(progress)
            contentAlpha.snapTo(progress)
        } else {
            // 从跟手态定格（progress→null）：从当前进度续跑打开动画到全屏。
            // 未经历跟手（点击直接打开）时忽略，走下方默认打开动画。
            if (hasFollowed && !closing && (vertical.value < 1f || horizontal.value < 1f)) {
                coroutineScope {
                    launch { vertical.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
                    launch { horizontal.animateTo(1f, tween(280, easing = FastOutSlowInEasing)) }
                    launch { contentAlpha.animateTo(1f, tween(240, delayMillis = 60)) }
                }
            }
        }
    }

    // 打开：仅无外部进度时三轴并发展开（点击胶囊进入全屏面板的默认动画）。
    if (!followProgress) {
        LaunchedEffect(Unit) {
            coroutineScope {
                launch { vertical.animateTo(1f, tween(380, easing = FastOutSlowInEasing)) }
                launch { horizontal.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
                launch { contentAlpha.animateTo(1f, tween(300, delayMillis = 80)) }
            }
        }
    }

    fun startClose() {
        if (closing) return
        closing = true
    }
    LaunchedEffect(closing) {
        if (closing) {
            // 关闭：横向收窄与竖向缩回并发展开，内容延后到收缩中后段再淡出——
            // 太早淡掉会让列表在壳还没缩小时就消失，观感"内容先走、壳再走"。
            coroutineScope {
                launch { horizontal.animateTo(0f, tween(240, easing = FastOutSlowInEasing)) }
                launch { vertical.animateTo(0f, tween(380, easing = FastOutSlowInEasing)) }
                launch { contentAlpha.animateTo(0f, tween(180, delayMillis = 140)) }
            }
            // animateTo 返回时值已到位，但该值的画面还要等重组+绘制才落地；
            // 立即 onDismiss 会把最后一帧跳过，壳停在 vertical≈0 处（偏上）。
            // 等两帧让「完全复位」的画面真正画出来再移除覆盖层。
            withFrameNanos { }
            withFrameNanos { }
            onDismiss()
        }
    }

    BackHandler { startClose() }

    val shellLeftPx = lerp(capsuleLeft, fullLeft, horizontal.value)
    val shellWidthPx = lerp(capsuleWidthPx, fullWidthPx, horizontal.value)
    val shellTopPx = lerp(capsuleTop, fullTop, vertical.value)
    val shellHeightPx = lerp(capsuleHeightPx, fullHeightPx, vertical.value)
    val contentAlphaValue = contentAlpha.value

    // 占位层：盖住底下页面、拦截触摸。
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { viewWidth = it.width; viewHeight = it.height },
    ) {
        // 壳：位置/宽高全由插值驱动（graphicsLayer 平移 + 显式宽高）。
        // 壳延伸到屏幕底，贴地直角（只顶圆角）；底部让位由内容区（窟窿）padding 实现。
        val shellShape = RoundedCornerShape(topStart = shapeCornerDp, topEnd = shapeCornerDp)
        Box(
            modifier = Modifier
                .width(with(density) { shellWidthPx.toDp() })
                .height(with(density) { shellHeightPx.toDp() })
                .graphicsLayer {
                    translationX = shellLeftPx
                    translationY = shellTopPx
                }
                .shadow(1.dp, shellShape, clip = false)
                .clip(shellShape)
                .background(containerColor),
        ) {
            Column(Modifier.fillMaxSize().padding(vertical = 4.dp)) {
                // 头部标题栏：与起点胶囊的头部同一份 composable，颜色/图文相对位置天然一致。
                // 把关闭动画触发器传给 slot：收起按钮调它走完整关闭动画，而不是直接移除壳。
                header(::startClose)
                // 内容区：占剩余空间，alpha 随动画淡入。
                // 底部让位 recessedBottom：内容区（窟窿）底部留出高度露出底下导航岛，与岛融合。
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = recessedBottom)
                        .graphicsLayer { alpha = contentAlphaValue },
                    content = { content() },
                )
            }
        }
    }
}
